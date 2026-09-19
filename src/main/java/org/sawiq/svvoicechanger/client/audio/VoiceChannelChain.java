package org.sawiq.svvoicechanger.client.audio;

import java.util.Arrays;
import org.sawiq.svvoicechanger.client.audio.dsp.Biquad;
import org.sawiq.svvoicechanger.client.audio.dsp.DeEsser;
import org.sawiq.svvoicechanger.client.audio.dsp.NoiseGate;
import org.sawiq.svvoicechanger.client.audio.dsp.RoomReverb;
import org.sawiq.svvoicechanger.client.audio.dsp.SmoothedValue;
import org.sawiq.svvoicechanger.client.audio.dsp.SoftLimiter;
import org.sawiq.svvoicechanger.client.audio.dsp.SpeakerPitchTracker;
import org.sawiq.svvoicechanger.client.audio.dsp.ToneStack;
import org.sawiq.svvoicechanger.client.audio.dsp.VocalPitchShifter;
import org.sawiq.svvoicechanger.client.model.AutotuneScale;

/**
 * The complete effect chain for one audio channel.
 *
 * <p>Signal order, and why:</p>
 * <ol>
 *   <li><b>Gate, then rumble filter.</b> Noise and low-frequency thumps are
 *       removed before the pitch shifter, which would otherwise turn them
 *       into warble and spend its frequency resolution on them.</li>
 *   <li><b>One pitch shifter.</b> Slider pitch, and autotune correction, are
 *       multiplied into a single ratio. Running two shifters in series, as the
 *       previous engine did whenever autotune and pitch were both active, cost
 *       twice the latency and smeared the voice.</li>
 *   <li><b>De-esser, tone, character, reverb, gain.</b> Colouring after the
 *       identity change, so the EQ shapes the voice the listener actually
 *       hears rather than the one being fed to the shifter.</li>
 *   <li><b>Blend against a delay-matched dry signal.</b> The dry path is held
 *       back by exactly the shifter's latency. Mixing an undelayed dry signal
 *       with a delayed wet one, which is what happened before, comb-filters
 *       the result and is the hollow sound users heard at partial blend.</li>
 *   <li><b>Limiter last</b>, so nothing downstream can undo it.</li>
 * </ol>
 */
public final class VoiceChannelChain {
    static final int FFT_SIZE = 1_024;
    /**
     * Eight overlapping frames per window. Four is the usual minimum for a
     * phase vocoder and it leaves an audible roughness on speech, which is the
     * artefact people describe as a robotic edge. Doubling the overlap is the
     * standard remedy and costs FFTs, not latency: the delay is set by the
     * window length, not the hop.
     */
    static final int HOP_SIZE = 128;

    /**
     * Delay from chain input to chain output, in samples. Derived rather than
     * guessed: a hop emits the oldest overlap-add slot, which corresponds to
     * the sample {@code FFT_SIZE - 1} positions back. Roughly 21 ms at 48 kHz.
     */
    static final int LATENCY_SAMPLES = FFT_SIZE - 1;

    private static final double RUMBLE_HZ = 75.0D;
    /**
     * The speaking pitch the built-in voices were written against. A preset
     * asking for pitch 1.45 means "land where a 120 Hz voice would land after
     * that multiplication", and the calibration is what turns that into the
     * right ratio for whoever is actually talking.
     */
    private static final double REFERENCE_SPEAKER_HZ = 120.0D;
    /** Bounds on the calibration, so an odd pitch reading cannot run away. */
    private static final double MIN_CALIBRATION = 0.45D;
    private static final double MAX_CALIBRATION = 2.20D;
    /** Hops between speaker-pitch observations; it changes over minutes, not frames. */
    private static final int SPEAKER_OBSERVE_HOPS = 32;
    /**
     * How strongly the vocal tract is assumed to differ when the speaking pitch
     * does. Across adult speakers pitch varies far more than tract length: a
     * voice roughly 65% higher in pitch typically has formants only about 15%
     * higher, which is this exponent. It is a population average rather than a
     * measurement of the person talking, but it is a far better assumption than
     * the alternative of pretending everyone has the vocal tract the presets
     * were written on.
     */
    private static final double FORMANT_CALIBRATION_EXPONENT = 0.28D;
    private static final double PARAMETER_RAMP_SECONDS = 0.03D;
    private static final int OUTPUT_QUEUE_SIZE = FFT_SIZE * 2;
    /** How long the chain takes to fade the shifter in or out, in seconds. */
    private static final double SHIFTER_FADE_SECONDS = 0.02D;
    /** Ratio distance from 1 below which a shift is not worth performing. */
    private static final double SHIFT_THRESHOLD = 0.002D;

    private final NoiseGate gate;
    private final Biquad rumbleFilter = new Biquad();
    private final VocalPitchShifter shifter;
    private final AutotuneTracker autotune;
    private final SpeakerPitchTracker speakerPitch;
    private final DeEsser deEsser;
    private final ToneStack tone;
    private final CharacterStage character;
    private final RoomReverb reverb;
    private final SoftLimiter limiter;

    private final SmoothedValue blend;
    private final SmoothedValue outputGain;
    private final SmoothedValue reverbMix;
    private final SmoothedValue shifterMix;

    private final float[] inputRing = new float[FFT_SIZE];
    private final float[] analysisWindow = new float[FFT_SIZE];
    private final float[] hopOutput = new float[HOP_SIZE];
    private final float[] outputQueue = new float[OUTPUT_QUEUE_SIZE];
    private final float[] dryDelay = new float[LATENCY_SAMPLES];

    private int inputWriteIndex;
    private long samplesSeen;
    private int samplesSinceHop;
    private int queueReadIndex;
    private int queueWriteIndex;
    private int queuedSamples;
    private int dryDelayIndex;

    private double pitchRatio = 1.0D;
    private double formantRatio = 1.0D;
    private AutotuneTracker.Settings autotuneSettings =
            new AutotuneTracker.Settings(0.0D, 0.6D, 0, AutotuneScale.CHROMATIC);
    private double peakLevel;
    private boolean reverbRunning;
    private double voiceMatch;
    private long hopCounter;
    private boolean shifterRunning;
    private double appliedPitchRatio = 1.0D;

    public VoiceChannelChain(double sampleRate) {
        this.gate = new NoiseGate(sampleRate);
        this.shifter = new VocalPitchShifter((float) sampleRate, FFT_SIZE, HOP_SIZE);
        this.autotune = new AutotuneTracker((float) sampleRate, FFT_SIZE, HOP_SIZE);
        this.speakerPitch = new SpeakerPitchTracker((float) sampleRate);
        this.deEsser = new DeEsser(sampleRate);
        this.tone = new ToneStack(sampleRate);
        this.character = new CharacterStage(sampleRate);
        this.reverb = new RoomReverb(sampleRate);
        this.limiter = new SoftLimiter(sampleRate);

        this.blend = new SmoothedValue(sampleRate, PARAMETER_RAMP_SECONDS, 0.0D);
        this.outputGain = new SmoothedValue(sampleRate, PARAMETER_RAMP_SECONDS, 1.0D);
        this.reverbMix = new SmoothedValue(sampleRate, PARAMETER_RAMP_SECONDS, 0.0D);
        this.shifterMix = new SmoothedValue(sampleRate, SHIFTER_FADE_SECONDS, 0.0D);

        this.rumbleFilter.setHighPass(sampleRate, RUMBLE_HZ, 0.707D);
    }

    /** Applies a settings snapshot. Cheap enough to call once per audio block. */
    public void configure(VoiceSettings settings) {
        this.gate.setAmount(settings.gate());
        this.deEsser.setAmount(settings.deEss());
        this.tone.setEqualiser(settings.lowEqDb(), settings.midEqDb(), settings.highEqDb());
        this.tone.setRadioAmount(settings.radio());
        this.character.setSettings(settings.character());
        this.reverb.setRoom(settings.reverbSize(), settings.reverbDecay());

        this.blend.setTarget(settings.blend());
        this.outputGain.setTarget(settings.gain());
        this.reverbMix.setTarget(settings.reverbMix());

        this.pitchRatio = settings.pitch();
        this.voiceMatch = settings.voiceMatch();
        this.formantRatio = settings.formant();
        this.autotuneSettings = settings.autotune();
    }

    /** Processes one block of mono samples in place. */
    public void process(float[] block, int length) {
        for (int i = 0; i < length; i++) {
            block[i] = (float) processSample(block[i]);
        }
    }

    /** The speaker's measured pitch in Hz, or 0 while it is still unknown. */
    public double speakerPitchHz() {
        return this.speakerPitch.speakingPitchHz();
    }

    /** The pitch ratio last handed to the shifter, after calibration and autotune. */
    public double appliedPitchRatio() {
        return this.appliedPitchRatio;
    }

    /**
     * Highest absolute input level seen since the last call, then cleared.
     * Drives the studio's level meter.
     */
    public double takePeakLevel() {
        double peak = this.peakLevel;
        this.peakLevel = 0.0D;
        return peak;
    }

    /** Clears every filter, delay line and phase. Call when a stream starts. */
    public void reset() {
        this.gate.reset();
        this.rumbleFilter.reset();
        this.shifter.reset();
        this.autotune.reset();
        this.speakerPitch.reset();
        this.deEsser.reset();
        this.tone.reset();
        this.character.reset();
        this.reverb.reset();
        this.limiter.reset();

        Arrays.fill(this.inputRing, 0f);
        Arrays.fill(this.outputQueue, 0f);
        Arrays.fill(this.dryDelay, 0f);
        this.inputWriteIndex = 0;
        this.samplesSeen = 0L;
        this.samplesSinceHop = 0;
        this.queueReadIndex = 0;
        this.queueWriteIndex = 0;
        this.queuedSamples = 0;
        this.dryDelayIndex = 0;
        this.peakLevel = 0.0D;
        this.reverbRunning = false;
        this.shifterRunning = false;
        this.shifterMix.reset(0.0D);
        this.hopCounter = 0L;
    }

    private double processSample(double input) {
        double magnitude = Math.abs(input);
        if (magnitude > this.peakLevel) {
            this.peakLevel = magnitude;
        }

        double cleaned = this.rumbleFilter.process(this.gate.process(input));
        this.speakerPitch.push((float) cleaned);

        double dry = pushDry(cleaned);
        double wet = advanceShifter(cleaned, dry);

        wet = this.deEsser.process(wet);
        wet = this.tone.process(wet);
        wet = this.character.process(wet);

        wet = applyReverb(wet);
        wet *= this.outputGain.next();

        double blended = dry + (wet - dry) * this.blend.next();
        return this.limiter.process(blended);
    }

    /**
     * Crossfades the room tail against the direct sound rather than adding it
     * on top. Adding it means every turn of the reverb knob also turns up the
     * volume, which made the roomy presets measurably louder than the rest.
     */
    private double applyReverb(double input) {
        double amount = this.reverbMix.next();

        if (amount <= 0.001D) {
            // The tail only decays while it is being processed. Clearing it on
            // the way down stops a re-enabled reverb from replaying whatever
            // was frozen in the delay lines.
            if (this.reverbRunning) {
                this.reverb.reset();
                this.reverbRunning = false;
            }
            return input;
        }

        this.reverbRunning = true;
        double tail = this.reverb.process(input);
        return input * (1.0D - amount) + tail * amount;
    }

    /** Writes into the dry delay line and returns the sample leaving it. */
    private double pushDry(double sample) {
        double delayed = this.dryDelay[this.dryDelayIndex];
        this.dryDelay[this.dryDelayIndex] = (float) sample;
        this.dryDelayIndex = this.dryDelayIndex + 1 >= this.dryDelay.length ? 0 : this.dryDelayIndex + 1;
        return delayed;
    }

    /**
     * Feeds one sample into the STFT pipeline and returns one shifted sample.
     * Until the first full window has arrived there is nothing to return, so
     * the delayed dry signal stands in and the output stays continuous.
     */
    private double advanceShifter(double input, double delayedDry) {
        // The window keeps filling even while the shifter is idle, so it is
        // already warm the moment a preset asks for a shift.
        this.inputRing[this.inputWriteIndex] = (float) input;
        this.inputWriteIndex = this.inputWriteIndex + 1 >= FFT_SIZE ? 0 : this.inputWriteIndex + 1;
        this.samplesSeen++;

        this.shifterMix.setTarget(isShiftRequired() ? 1.0D : 0.0D);
        double wetAmount = this.shifterMix.next();

        if (wetAmount <= 0.0001D) {
            stopShifter();
            return delayedDry;
        }

        this.shifterRunning = true;
        if (this.samplesSeen == FFT_SIZE) {
            runHop();
            this.samplesSinceHop = 0;
        } else if (this.samplesSeen > FFT_SIZE && ++this.samplesSinceHop >= HOP_SIZE) {
            this.samplesSinceHop = 0;
            runHop();
        }

        if (this.queuedSamples == 0) {
            return delayedDry;
        }

        float wet = this.outputQueue[this.queueReadIndex];
        this.queueReadIndex = this.queueReadIndex + 1 >= OUTPUT_QUEUE_SIZE ? 0 : this.queueReadIndex + 1;
        this.queuedSamples--;

        // Crossfading against the delay-matched dry path means engaging and
        // releasing the shifter is inaudible, which is what allows it to be
        // switched off at all.
        return delayedDry + (wet - delayedDry) * wetAmount;
    }

    /**
     * Whether anything actually wants the pitch or the vocal tract moved.
     *
     * <p>Resynthesising a voice through an STFT is never free: even at unity
     * ratio the noisy parts of speech come back smeared, which is heard as a
     * faint robotic edge on every preset. Measured against a speech-like
     * signal, a chain with nothing to do was returning barely 13 dB of signal
     * against its own resynthesis error. Skipping the transform outright when
     * no shift is asked for costs nothing and removes that entirely.</p>
     */
    private boolean isShiftRequired() {
        return Math.abs(this.pitchRatio - 1.0D) > SHIFT_THRESHOLD
                || Math.abs(this.formantRatio - 1.0D) > SHIFT_THRESHOLD
                || this.voiceMatch > 0.005D
                || this.autotuneSettings.amount() > 0.005D;
    }

    /** Releases the shifter and clears anything still queued from it. */
    private void stopShifter() {
        if (!this.shifterRunning) {
            return;
        }

        this.shifter.reset();
        this.autotune.reset();
        this.queueReadIndex = 0;
        this.queueWriteIndex = 0;
        this.queuedSamples = 0;
        this.samplesSinceHop = 0;
        this.appliedPitchRatio = 1.0D;
        this.shifterRunning = false;
    }

    private void runHop() {
        flattenInputRing();

        if (this.voiceMatch > 0.005D && this.hopCounter % SPEAKER_OBSERVE_HOPS == 0) {
            this.speakerPitch.analyse();
        }
        this.hopCounter++;

        double correction = this.autotune.nextRatio(this.analysisWindow, this.autotuneSettings);
        double calibration = speakerCalibration();
        this.appliedPitchRatio = this.pitchRatio * calibration * correction;
        this.shifter.setPitchRatio((float) this.appliedPitchRatio);
        this.shifter.setFormantRatio((float) (this.formantRatio * formantCalibration(calibration)));
        this.shifter.process(this.analysisWindow, this.hopOutput);

        for (int i = 0; i < HOP_SIZE; i++) {
            this.outputQueue[this.queueWriteIndex] = this.hopOutput[i];
            this.queueWriteIndex = this.queueWriteIndex + 1 >= OUTPUT_QUEUE_SIZE ? 0 : this.queueWriteIndex + 1;
        }
        this.queuedSamples = Math.min(OUTPUT_QUEUE_SIZE, this.queuedSamples + HOP_SIZE);
    }

    /**
     * Factor that turns the preset's pitch ratio into one aimed at this
     * speaker. Returns 1 while the speaker's pitch is still unknown, so the
     * voice never depends on a guess made before anyone has spoken.
     */
    private double speakerCalibration() {
        if (this.voiceMatch <= 0.005D) {
            return 1.0D;
        }

        double speakerHz = this.speakerPitch.speakingPitchHz();
        if (speakerHz <= 0.0D) {
            return 1.0D;
        }

        double calibration = Math.max(MIN_CALIBRATION,
                Math.min(MAX_CALIBRATION, REFERENCE_SPEAKER_HZ / speakerHz));
        return 1.0D + (calibration - 1.0D) * this.voiceMatch;
    }

    /**
     * The share of the pitch calibration that should also apply to the vocal
     * tract. Presets describe a tract relative to the voice they were tuned on,
     * so a speaker whose own tract already differs needs less of the shift.
     */
    private static double formantCalibration(double calibration) {
        if (Math.abs(calibration - 1.0D) < 1.0e-6D) {
            return 1.0D;
        }
        return Math.pow(calibration, FORMANT_CALIBRATION_EXPONENT);
    }

    /** Copies the ring buffer into a flat oldest-to-newest window. */
    private void flattenInputRing() {
        int firstChunk = FFT_SIZE - this.inputWriteIndex;
        System.arraycopy(this.inputRing, this.inputWriteIndex, this.analysisWindow, 0, firstChunk);
        if (firstChunk < FFT_SIZE) {
            System.arraycopy(this.inputRing, 0, this.analysisWindow, firstChunk, FFT_SIZE - firstChunk);
        }
    }
}
