package org.sawiq.svvoicechanger.client.audio;

import java.util.Arrays;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;

final class VoiceChangerAudioEngine {
    private static final double SAMPLE_RATE = 48_000.0D;
    private static final double TWO_PI = Math.PI * 2.0D;
    private static final int PITCH_WINDOW = 960;
    private static final int PITCH_DELAY = 1440;
    private static final int PITCH_BUFFER_SIZE = 4096;

    private VoiceChangerAudioEngine() {
    }

    public static void process(short[] samples, int channels, VoiceChangerProfile profile, int strength, VoiceChangerState state) {
        if (samples == null || samples.length == 0) {
            return;
        }

        int safeChannels = Math.max(1, channels);
        double amount = clamp01(strength / 100.0D);
        VoiceChangerProfile scaled = scale(profile, amount);
        state.prepare(safeChannels);

        if (profile.autotuneMix() > 0.005D) {
            VoiceChangerAutotune.process(samples, safeChannels, profile, amount, state.autotuneState);
        }

        short[] voicingSource;
        if (Math.abs(scaled.pitch() - 1.0D) < 0.015D) {
            voicingSource = samples;
        } else {
            short[] scratch = state.ensurePitchScratch(samples.length);
            applyPitchShifter(samples, scratch, safeChannels, scaled.pitch(), state);
            voicingSource = scratch;
        }
        processVoicing(samples, voicingSource, safeChannels, scaled, state);
    }

    private static VoiceChangerProfile scale(VoiceChangerProfile profile, double amount) {
        double shaped = amount * amount;
        double wet = 0.18D + shaped * 0.82D;
        return new VoiceChangerProfile(
                mix(0.0D, profile.mix(), wet),
                mix(1.0D, profile.gain(), wet),
                mix(1.0D, profile.pitch(), wet),
                mix(1.0D, profile.formant(), wet),
                profile.distortion() * wet,
                profile.robotMix() * wet,
                profile.robotFrequency(),
                profile.echoMix() * wet,
                profile.echoDelayMs(),
                profile.echoFeedback() * wet,
                profile.tremoloDepth() * wet,
                mix(0.0D, profile.tremoloRate(), wet),
                profile.lowEq() * wet,
                profile.midEq() * wet,
                profile.highEq() * wet,
                profile.noise() * wet,
                profile.autotuneMix() * wet,
                profile.autotuneStrength(),
                profile.autotuneKey(),
                profile.autotuneScale(),
                mix(VoiceChangerProfile.BIT_DEPTH_CLEAN, profile.bitDepth(), wet)
        );
    }

    private static void applyPitchShifter(short[] samples, short[] output, int channels, double pitchRatio, VoiceChangerState state) {
        double ratio = clamp(pitchRatio, 0.60D, 1.70D);
        double phaseStep = (1.0D - ratio) / PITCH_WINDOW;

        for (int i = 0; i < samples.length; i++) {
            int channel = i % channels;
            double sample = normalizeSample(samples[i]);
            float[] buffer = state.pitchBuffer[channel];
            int write = state.pitchWriteCursor[channel];
            buffer[write] = (float) sample;

            double phaseA = wrap01(state.pitchPhase[channel]);
            double phaseB = wrap01(phaseA + 0.5D);
            double delayA = PITCH_DELAY + phaseA * PITCH_WINDOW;
            double delayB = PITCH_DELAY + phaseB * PITCH_WINDOW;

            double sampleA = readDelayed(buffer, write, delayA);
            double sampleB = readDelayed(buffer, write, delayB);
            double envA = triangleWindow(phaseA);
            double envB = triangleWindow(phaseB);
            double env = Math.max(0.0001D, envA + envB);
            double shifted = (sampleA * envA + sampleB * envB) / env;

            output[i] = denormalizeSample(clamp(shifted, -1.0D, 1.0D));

            state.pitchWriteCursor[channel] = (write + 1) % buffer.length;
            state.pitchPhase[channel] = wrap01(phaseA + phaseStep);
        }
    }

    private static double readDelayed(float[] buffer, int write, double delaySamples) {
        double readIndex = write - delaySamples;
        while (readIndex < 0.0D) {
            readIndex += buffer.length;
        }

        int index0 = floorMod((int) Math.floor(readIndex), buffer.length);
        int index1 = floorMod(index0 + 1, buffer.length);
        double delta = readIndex - Math.floor(readIndex);
        return buffer[index0] + (buffer[index1] - buffer[index0]) * delta;
    }

    private static double triangleWindow(double phase) {
        return 1.0D - Math.abs(phase * 2.0D - 1.0D);
    }

    private static double wrap01(double value) {
        double wrapped = value;
        while (wrapped >= 1.0D) {
            wrapped -= 1.0D;
        }
        while (wrapped < 0.0D) {
            wrapped += 1.0D;
        }
        return wrapped;
    }

    private static int floorMod(int value, int mod) {
        int result = value % mod;
        return result < 0 ? result + mod : result;
    }

    private static void processVoicing(short[] original, short[] pitched, int channels, VoiceChangerProfile profile, VoiceChangerState state) {
        int echoFrames = Math.max(1, (int) Math.round(SAMPLE_RATE * profile.echoDelayMs() / 1000.0D));
        state.ensureEchoDelay(channels, echoFrames);

        for (int i = 0; i < original.length; i++) {
            int channel = i % channels;
            double dry = normalizeSample(original[i]);
            double wet = normalizeSample(pitched[i]);

            wet = applyFormant(channel, wet, profile.formant(), state);
            wet = applyAntiAlias(channel, wet, profile, state);
            wet = applyEq(channel, wet, profile, state);
            wet = applyRadioTone(channel, wet, profile, state);
            wet = applyPresence(channel, wet, profile.highEq(), state);
            wet = applySubharmonic(channel, wet, profile, state);
            wet = applyRobot(channel, wet, profile.robotMix(), profile.robotFrequency(), state);
            wet = applyNoise(channel, wet, profile.noise(), state);
            wet = applyDistortion(wet, profile.distortion());
            wet = applyBitCrush(channel, wet, profile.bitDepth(), state);
            wet = applyTremolo(channel, wet, profile.tremoloDepth(), profile.tremoloRate(), state);
            wet = applySmoothing(channel, wet, profile, state);

            double delayed = state.readEcho(channel, echoFrames);
            double echoed = wet + delayed * profile.echoFeedback();
            state.writeEcho(channel, clamp(echoed, -1.0D, 1.0D));
            wet = mix(wet, echoed, profile.echoMix());

            double shaped = softLimit(wet * profile.gain());
            original[i] = denormalizeSample(mix(dry, shaped, profile.mix()));
        }
    }

    private static double applyFormant(int channel, double sample, double formant, VoiceChangerState state) {
        state.formantLow[channel] += 0.10D * (sample - state.formantLow[channel]);
        double low = state.formantLow[channel];
        double high = sample - low;
        double lowGain = clamp(2.0D - formant, 0.20D, 2.20D);
        double highGain = clamp(formant * 1.45D, 0.25D, 2.40D);
        return clamp(low * lowGain + high * highGain, -1.0D, 1.0D);
    }

    private static double applyEq(int channel, double sample, VoiceChangerProfile profile, VoiceChangerState state) {
        state.eqLow[channel] += 0.04D * (sample - state.eqLow[channel]);
        state.eqHigh[channel] += 0.18D * (sample - state.eqHigh[channel]);

        double low = state.eqLow[channel];
        double high = sample - state.eqHigh[channel];
        double mid = sample - low - high;

        double lowGain = dbToLinear(profile.lowEq());
        double midGain = dbToLinear(profile.midEq());
        double highGain = dbToLinear(profile.highEq());
        return clamp(low * lowGain + mid * midGain + high * highGain, -1.0D, 1.0D);
    }

    private static double applyPresence(int channel, double sample, double highEq, VoiceChangerState state) {
        double presence = clamp01(Math.max(0.0D, highEq) / 8.0D);
        if (presence <= 0.01D) {
            return sample;
        }

        state.presence[channel] += 0.18D * (sample - state.presence[channel]);
        double airy = sample - state.presence[channel];
        return clamp(sample + airy * presence * 0.14D, -1.0D, 1.0D);
    }

    private static double applyRadioTone(int channel, double sample, VoiceChangerProfile profile, VoiceChangerState state) {
        double lowCut = clamp01(Math.max(0.0D, -profile.lowEq()) / 10.0D);
        double highCut = clamp01(Math.max(0.0D, -profile.highEq()) / 10.0D);
        double midBoost = clamp01(Math.max(0.0D, profile.midEq()) / 10.0D);
        double radioAmount = clamp01(lowCut * 0.45D + highCut * 0.45D + midBoost * 0.35D + profile.noise() * 1.2D);
        if (radioAmount <= 0.12D) {
            return sample;
        }

        state.radioLow[channel] += 0.045D * (sample - state.radioLow[channel]);
        double highPassed = sample - state.radioLow[channel];
        state.radioBand[channel] += 0.22D * (highPassed - state.radioBand[channel]);
        double band = state.radioBand[channel];

        double bite = clamp01(radioAmount * 0.75D);
        double step = 1.0D / (42.0D + radioAmount * 90.0D);
        double crushed = Math.round(band / step) * step;
        double shaped = mix(band, crushed, bite * 0.30D);
        return clamp(mix(sample, shaped, radioAmount * 0.78D), -1.0D, 1.0D);
    }

    private static double applyAntiAlias(int channel, double sample, VoiceChangerProfile profile, VoiceChangerState state) {
        double aliasAmount = clamp01(Math.abs(profile.pitch() - 1.0D) * 0.85D + Math.abs(profile.formant() - 1.0D) * 0.55D);
        if (aliasAmount <= 0.01D) {
            return sample;
        }

        double alpha = 0.05D + aliasAmount * 0.18D;
        state.antiAlias[channel] += alpha * (sample - state.antiAlias[channel]);
        return mix(sample, state.antiAlias[channel], aliasAmount * 0.58D);
    }

    private static double applySubharmonic(int channel, double sample, VoiceChangerProfile profile, VoiceChangerState state) {
        double subAmount = clamp01((1.0D - profile.pitch()) * 0.55D + Math.max(0.0D, profile.lowEq()) / 10.0D * 0.22D);
        if (subAmount <= 0.01D) {
            state.previousSign[channel] = Math.signum(sample);
            return sample;
        }

        double sign = Math.signum(sample);
        if (sign != 0.0D && sign != state.previousSign[channel]) {
            state.subPhase[channel] = advancePhase(state.subPhase[channel], 0.5D);
            state.previousSign[channel] = sign;
        }

        double sub = Math.sin(state.subPhase[channel]) * Math.abs(sample);
        return clamp(sample + sub * subAmount * 0.24D, -1.0D, 1.0D);
    }

    private static double applyRobot(int channel, double sample, double robotMix, int robotFrequency, VoiceChangerState state) {
        if (robotMix <= 0.001D) {
            return sample;
        }

        double carrier = Math.signum(Math.sin(state.robotPhase[channel]));
        if (carrier == 0.0D) {
            carrier = 1.0D;
        }

        double metallic = sample * carrier;
        state.robotPhase[channel] = advancePhase(state.robotPhase[channel], robotFrequency);
        return mix(sample, metallic, clamp01(robotMix));
    }

    private static double applyNoise(int channel, double sample, double noiseAmount, VoiceChangerState state) {
        if (noiseAmount <= 0.001D) {
            return sample;
        }

        state.noiseSeed[channel] = state.noiseSeed[channel] * 1664525 + 1013904223;
        double white = (((state.noiseSeed[channel] >>> 8) & 0xFFFF) / 32767.5D) - 1.0D;
        state.noiseBand[channel] += 0.18D * (white - state.noiseBand[channel]);
        double hiss = white - state.noiseBand[channel];
        return clamp(sample + hiss * noiseAmount * 0.12D, -1.0D, 1.0D);
    }

    private static double applyDistortion(double sample, double distortion) {
        if (distortion <= 0.001D) {
            return sample;
        }

        double drive = 1.0D + distortion * 10.0D;
        double clipped = Math.tanh(sample * drive);
        double crushMix = clamp01((distortion - 0.28D) * 1.6D);
        if (crushMix <= 0.01D) {
            return clipped;
        }

        double step = 1.0D / (20.0D + distortion * 36.0D);
        double crushed = Math.round(clipped / step) * step;
        return mix(clipped, crushed, crushMix);
    }

    /**
     * Bit crusher in the spirit of the Grallion "bit" knob: as {@code bitDepth}
     * drops from {@link VoiceChangerProfile#BIT_DEPTH_CLEAN} toward
     * {@link VoiceChangerProfile#BIT_DEPTH_MIN}, the signal is quantized to fewer
     * and fewer levels (16, 8, 3 bit, ...) and progressively sample-rate reduced
     * via a sample-and-hold, giving the chunky retro tone. At full bit depth the
     * effect is bypassed so the voice stays clean.
     */
    private static double applyBitCrush(int channel, double sample, double bitDepth, VoiceChangerState state) {
        double bits = clamp(bitDepth, VoiceChangerProfile.BIT_DEPTH_MIN, VoiceChangerProfile.BIT_DEPTH_CLEAN);
        if (bits >= VoiceChangerProfile.BIT_DEPTH_CLEAN - 0.05D) {
            state.crushHold[channel] = sample;
            state.crushPhase[channel] = 0.0D;
            return sample;
        }

        // How aggressive the crush is, 0 (clean) .. 1 (1 bit).
        double crush = clamp01((VoiceChangerProfile.BIT_DEPTH_CLEAN - bits) / (VoiceChangerProfile.BIT_DEPTH_CLEAN - VoiceChangerProfile.BIT_DEPTH_MIN));

        // Sample-rate reduction: hold the previous quantized value for a few
        // frames. Stronger crush -> longer hold -> grittier, more "8-bit" sound.
        double holdStep = 1.0D / (1.0D + crush * 11.0D);
        state.crushPhase[channel] += holdStep;
        boolean refresh = state.crushPhase[channel] >= 1.0D;
        if (refresh) {
            state.crushPhase[channel] -= 1.0D;
            double levels = Math.pow(2.0D, bits);
            double step = 2.0D / levels;
            state.crushHold[channel] = clamp(Math.floor(sample / step + 0.5D) * step, -1.0D, 1.0D);
        }

        return state.crushHold[channel];
    }

    private static double applyTremolo(int channel, double sample, double depth, double rate, VoiceChangerState state) {
        if (depth <= 0.001D) {
            return sample;
        }

        double modulation = 1.0D - depth + ((Math.sin(state.tremoloPhase[channel]) + 1.0D) * 0.5D) * depth;
        state.tremoloPhase[channel] = advancePhase(state.tremoloPhase[channel], rate);
        return sample * modulation;
    }

    private static double applySmoothing(int channel, double sample, VoiceChangerProfile profile, VoiceChangerState state) {
        double smoothing = clamp01(Math.abs(profile.pitch() - 1.0D) * 0.45D + Math.abs(profile.formant() - 1.0D) * 0.25D + profile.distortion() * 0.90D + profile.noise() * 1.10D);
        if (smoothing <= 0.01D) {
            return sample;
        }

        double alpha = 0.14D + smoothing * 0.24D;
        state.smooth[channel] += alpha * (sample - state.smooth[channel]);
        return mix(sample, state.smooth[channel], smoothing * 0.60D);
    }

    private static double advancePhase(double phase, double frequency) {
        double advanced = phase + TWO_PI * frequency / SAMPLE_RATE;
        while (advanced > TWO_PI) {
            advanced -= TWO_PI;
        }
        return advanced;
    }

    private static double dbToLinear(double db) {
        return Math.pow(10.0D, db / 20.0D);
    }

    private static double softLimit(double sample) {
        return Math.tanh(sample * 0.95D);
    }

    private static double normalizeSample(short sample) {
        return sample / 32768.0D;
    }

    private static short denormalizeSample(double sample) {
        return (short) Math.round(clamp(sample, -1.0D, 1.0D) * 32767.0D);
    }

    private static double mix(double dry, double wet, double mix) {
        return dry * (1.0D - mix) + wet * mix;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class VoiceChangerState {
        private double[] robotPhase = new double[1];
        private double[] tremoloPhase = new double[1];
        private double[] subPhase = new double[1];
        private double[] previousSign = new double[1];
        private double[] formantLow = new double[1];
        private double[] antiAlias = new double[1];
        private double[] eqLow = new double[1];
        private double[] eqHigh = new double[1];
        private double[] radioLow = new double[1];
        private double[] radioBand = new double[1];
        private double[] presence = new double[1];
        private double[] noiseBand = new double[1];
        private double[] smooth = new double[1];
        private double[] crushHold = new double[1];
        private double[] crushPhase = new double[1];
        private int[] noiseSeed = new int[1];
        private float[][] echoDelay = new float[1][1];
        private int[] echoCursor = new int[1];
        private float[][] pitchBuffer = new float[1][PITCH_BUFFER_SIZE];
        private int[] pitchWriteCursor = new int[1];
        private double[] pitchPhase = new double[1];
        private short[] pitchScratch = new short[0];
        final VoiceChangerAutotune.AutotuneState autotuneState = new VoiceChangerAutotune.AutotuneState();

        short[] ensurePitchScratch(int length) {
            if (this.pitchScratch.length < length) {
                this.pitchScratch = new short[length];
            }
            return this.pitchScratch;
        }

        void prepare(int channels) {
            this.robotPhase = ensureSize(this.robotPhase, channels);
            this.tremoloPhase = ensureSize(this.tremoloPhase, channels);
            this.subPhase = ensureSize(this.subPhase, channels);
            this.previousSign = ensureSize(this.previousSign, channels);
            this.formantLow = ensureSize(this.formantLow, channels);
            this.antiAlias = ensureSize(this.antiAlias, channels);
            this.eqLow = ensureSize(this.eqLow, channels);
            this.eqHigh = ensureSize(this.eqHigh, channels);
            this.radioLow = ensureSize(this.radioLow, channels);
            this.radioBand = ensureSize(this.radioBand, channels);
            this.presence = ensureSize(this.presence, channels);
            this.noiseBand = ensureSize(this.noiseBand, channels);
            this.smooth = ensureSize(this.smooth, channels);
            this.crushHold = ensureSize(this.crushHold, channels);
            this.crushPhase = ensureSize(this.crushPhase, channels);
            this.noiseSeed = ensureSize(this.noiseSeed, channels);
            this.echoCursor = ensureSize(this.echoCursor, channels);
            this.pitchWriteCursor = ensureSize(this.pitchWriteCursor, channels);
            this.pitchPhase = ensureSize(this.pitchPhase, channels);

            if (this.echoDelay.length != channels) {
                float[][] resized = new float[channels][];
                for (int i = 0; i < channels; i++) {
                    resized[i] = i < this.echoDelay.length ? this.echoDelay[i] : new float[1];
                }
                this.echoDelay = resized;
            }

            if (this.pitchBuffer.length != channels) {
                float[][] resized = new float[channels][];
                for (int i = 0; i < channels; i++) {
                    resized[i] = i < this.pitchBuffer.length ? this.pitchBuffer[i] : new float[PITCH_BUFFER_SIZE];
                    if (resized[i] == null || resized[i].length != PITCH_BUFFER_SIZE) {
                        resized[i] = new float[PITCH_BUFFER_SIZE];
                    }
                }
                this.pitchBuffer = resized;
            }
        }

        void ensureEchoDelay(int channels, int delayFrames) {
            prepare(channels);
            for (int i = 0; i < channels; i++) {
                if (this.echoDelay[i] == null || this.echoDelay[i].length != delayFrames) {
                    this.echoDelay[i] = new float[delayFrames];
                    this.echoCursor[i] = 0;
                }
            }
        }

        double readEcho(int channel, int delayFrames) {
            int cursor = this.echoCursor[channel];
            int index = (cursor + 1) % delayFrames;
            return this.echoDelay[channel][index];
        }

        void writeEcho(int channel, double value) {
            float[] channelDelay = this.echoDelay[channel];
            int cursor = this.echoCursor[channel];
            channelDelay[cursor] = (float) value;
            this.echoCursor[channel] = (cursor + 1) % channelDelay.length;
        }

        private static double[] ensureSize(double[] values, int size) {
            return values.length == size ? values : Arrays.copyOf(values, size);
        }

        private static int[] ensureSize(int[] values, int size) {
            return values.length == size ? values : Arrays.copyOf(values, size);
        }
    }
}

