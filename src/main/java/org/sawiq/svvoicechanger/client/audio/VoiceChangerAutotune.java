package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.audio.dsp.SmbPitchShifter;
import org.sawiq.svvoicechanger.client.audio.dsp.YinDetector;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;

/**
 * Real-time autotune driven by an in-tree implementation of two well-known
 * algorithms:
 *
 * <ul>
 *   <li>{@link YinDetector} &mdash; YIN F0 estimator
 *       (de Cheveign&eacute; &amp; Kawahara, 2002).</li>
 *   <li>{@link SmbPitchShifter} &mdash; STFT phase vocoder
 *       (Bernsee's smbPitchShift, &quot;Wide Open License&quot;, re-implemented
 *       independently from the algorithm description on dspdimension.com).</li>
 * </ul>
 *
 * <p>Both algorithm families are public-domain mathematical recipes that have
 * been published in academic and industry references; this class merely wires
 * the streaming buffers, applies a musical-scale snap, smooths the ratio to
 * give the user a glide-vs-snap control, and mixes the wet/dry signal.</p>
 */
final class VoiceChangerAutotune {
    static final float SAMPLE_RATE = 48_000f;
    static final int FFT_SIZE = 1024;
    static final int OVERLAP = 768;
    static final int STEP_SIZE = FFT_SIZE - OVERLAP; // 256
    static final int DRY_DELAY = OVERLAP;            // matches phase-vocoder latency
    static final int OUT_QUEUE = FFT_SIZE * 2;
    /**
     * Run the YIN detector once every {@value} STFT cycles (i.e. once per
     * {@code DETECT_EVERY_N_CYCLES * STEP_SIZE} samples). At 48 kHz with the
     * default constants this is ~47 detections per second, well above the
     * rate at which a sung F0 actually changes, while keeping the cost of
     * the O(N²/4) YIN difference function out of the hot path.
     */
    static final int DETECT_EVERY_N_CYCLES = 4;

    private static final double MIN_RATIO = 0.50D;
    private static final double MAX_RATIO = 2.00D;
    private static final double SILENCE_RMS = 0.0025D;

    private static final int[] SCALE_CHROMATIC = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
    private static final int[] SCALE_MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] SCALE_MINOR = {0, 2, 3, 5, 7, 8, 10};

    private VoiceChangerAutotune() {
    }

    static void process(short[] samples, int channels, VoiceChangerProfile profile, double amount, AutotuneState state) {
        double mix = clamp01(profile.autotuneMix() * amount);
        if (mix <= 0.005D || samples.length == 0) {
            return;
        }

        state.prepare(channels);
        int[] scale = scaleFor(profile.autotuneScale());
        int rootPc = ((profile.autotuneKey() % 12) + 12) % 12;
        double strength = clamp01(profile.autotuneStrength());
        // Ratio time-constant: 250 ms slow glide -> 0.1 ms instant lock.
        // Applied once per STEP_SIZE samples (the rate at which a new ratio
        // is set on the pitch shifter). The cubic curve gives most of the
        // useful range to the user-facing strength slider in [0.3, 1.0].
        double tc = 0.0001D + 0.250D * Math.pow(1.0D - strength, 3.0D);
        double snapAlphaPerStep = 1.0D - Math.exp(-STEP_SIZE / (SAMPLE_RATE * tc));

        // Equal-power crossfade gains. The phase-vocoder output is largely
        // uncorrelated with the dry signal (random phase relationship), so a
        // linear (mix, 1-mix) law produces an audible -3 dB dip around
        // mix = 0.5. The constant-power cos/sin law keeps perceived loudness
        // flat across the whole mix range. We pre-compute the gain pair once
        // per process() call since the mix amount is constant for the block.
        double dryGainOn = Math.cos(mix * Math.PI / 2.0);
        double wetGainOn = Math.sin(mix * Math.PI / 2.0);

        for (int i = 0; i < samples.length; i++) {
            int ch = i % channels;
            float dryIn = samples[i] / 32768f;

            // 1) Dry delay line aligned with the pitch-shifter latency.
            float dry = state.dryDelay[ch][state.dryDelayPos[ch]];
            state.dryDelay[ch][state.dryDelayPos[ch]] = dryIn;
            state.dryDelayPos[ch] = (state.dryDelayPos[ch] + 1) % DRY_DELAY;

            // 2) Slide the input ring forward by one sample.
            state.inputRing[ch][state.inputWritePos[ch]] = dryIn;
            state.inputWritePos[ch] = (state.inputWritePos[ch] + 1) % FFT_SIZE;
            state.pendingFill[ch]++;
            state.samplesSeen[ch]++;

            // 3) Update RMS used for the silence gate.
            double r = state.rms[ch] * 0.995D + dryIn * dryIn * 0.005D;
            state.rms[ch] = r;

            // 4) Run an STFT cycle once we have STEP_SIZE new samples and at
            //    least one full FFT_SIZE window of history.
            if (state.pendingFill[ch] >= STEP_SIZE && state.samplesSeen[ch] >= FFT_SIZE) {
                state.pendingFill[ch] -= STEP_SIZE;
                boolean runDetector = (state.cycleCounter[ch]++ % DETECT_EVERY_N_CYCLES) == 0;
                runStftCycle(state, ch, rootPc, scale, snapAlphaPerStep, runDetector);
            }

            // 5) Pop one wet sample from the queue (or fall back to dry while
            //    the pipeline is still warming up).
            float wet;
            if (state.outQueueCount[ch] > 0) {
                wet = state.outQueue[ch][state.outQueueRead[ch]];
                state.outQueueRead[ch] = (state.outQueueRead[ch] + 1) % OUT_QUEUE;
                state.outQueueCount[ch]--;
            } else {
                wet = dry;
            }

            // Apply the equal-power gains only when the input is above the
            // silence floor; below it we pass dry through to keep room tone
            // free of pitch-shifter artefacts.
            double blended;
            if (Math.sqrt(r) < SILENCE_RMS) {
                blended = dry;
            } else {
                blended = dry * dryGainOn + wet * wetGainOn;
            }
            samples[i] = (short) Math.round(clamp(blended, -1.0D, 1.0D) * 32767.0D);
        }
    }

    private static void runStftCycle(AutotuneState state, int ch, int rootPc, int[] scale, double snapAlpha, boolean runDetector) {
        // Snapshot the input ring into a flat window for YIN + FFT.
        float[] ring = state.inputRing[ch];
        float[] flat = state.flatWindow[ch];
        int start = state.inputWritePos[ch];
        int firstChunk = FFT_SIZE - start;
        System.arraycopy(ring, start, flat, 0, firstChunk);
        if (firstChunk < FFT_SIZE) {
            System.arraycopy(ring, 0, flat, firstChunk, FFT_SIZE - firstChunk);
        }

        // Detect F0 (Hz). YIN returns -1 for unvoiced/quiet frames. We re-run
        // it only every DETECT_EVERY_N_CYCLES cycles to keep the O(N²/4)
        // difference function out of the hot path; the most recent target
        // ratio is held between detections.
        if (runDetector) {
            float pitchHz = state.detectors[ch].getPitch(flat);
            if (pitchHz > 0f && Math.sqrt(state.rms[ch]) >= SILENCE_RMS) {
                double targetHz = snapToScale(pitchHz, rootPc, scale);
                state.targetRatio[ch] = clamp(targetHz / pitchHz, MIN_RATIO, MAX_RATIO);
            } else {
                state.targetRatio[ch] = 1.0D;
            }
        }
        state.currentRatio[ch] += snapAlpha * (state.targetRatio[ch] - state.currentRatio[ch]);
        double ratio = clamp(state.currentRatio[ch], MIN_RATIO, MAX_RATIO);

        // Phase vocoder cycle.
        SmbPitchShifter shifter = state.shifters[ch];
        shifter.setPitchRatio((float) ratio);
        shifter.process(flat, state.outBlock[ch]);

        // Push stepSize fresh samples to the FIFO.
        float[] outBlock = state.outBlock[ch];
        float[] queue = state.outQueue[ch];
        int writePos = state.outQueueWrite[ch];
        for (int k = 0; k < STEP_SIZE; k++) {
            queue[writePos] = outBlock[k];
            writePos = (writePos + 1) % OUT_QUEUE;
        }
        state.outQueueWrite[ch] = writePos;
        state.outQueueCount[ch] = Math.min(OUT_QUEUE, state.outQueueCount[ch] + STEP_SIZE);
    }

    // ---------------------------------------------------------------------
    // Scale snapping
    // ---------------------------------------------------------------------

    private static int[] scaleFor(int scaleId) {
        return switch (scaleId) {
            case VoiceChangerProfile.SCALE_MAJOR -> SCALE_MAJOR;
            case VoiceChangerProfile.SCALE_MINOR -> SCALE_MINOR;
            default -> SCALE_CHROMATIC;
        };
    }

    private static double snapToScale(double freqHz, int rootPc, int[] scale) {
        if (freqHz <= 0.0D) {
            return freqHz;
        }
        double midi = 69.0D + 12.0D * (Math.log(freqHz / 440.0D) / Math.log(2.0D));
        int baseMidi = (int) Math.floor(midi);
        double bestDelta = Double.MAX_VALUE;
        int bestMidi = baseMidi;
        for (int octaveOffset = -1; octaveOffset <= 1; octaveOffset++) {
            for (int degree : scale) {
                int candidatePc = (rootPc + degree) % 12;
                int candidate = nearestMidiWithPc(baseMidi + octaveOffset * 12, candidatePc);
                double delta = Math.abs(midi - candidate);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    bestMidi = candidate;
                }
            }
        }
        return 440.0D * Math.pow(2.0D, (bestMidi - 69) / 12.0D);
    }

    private static int nearestMidiWithPc(int aroundMidi, int pc) {
        int aroundPc = ((aroundMidi % 12) + 12) % 12;
        int diff = pc - aroundPc;
        if (diff > 6) {
            diff -= 12;
        } else if (diff < -6) {
            diff += 12;
        }
        return aroundMidi + diff;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0D, 1.0D);
    }

    // ---------------------------------------------------------------------
    // Per-instance state (one slot per channel).
    // ---------------------------------------------------------------------

    static final class AutotuneState {
        float[][] inputRing;
        int[] inputWritePos;
        int[] pendingFill;
        long[] samplesSeen;
        float[][] flatWindow;
        float[][] dryDelay;
        int[] dryDelayPos;
        float[][] outQueue;
        int[] outQueueRead;
        int[] outQueueWrite;
        int[] outQueueCount;
        float[][] outBlock;
        double[] rms;
        double[] targetRatio;
        double[] currentRatio;
        long[] cycleCounter;
        SmbPitchShifter[] shifters;
        YinDetector[] detectors;

        AutotuneState() {
            allocate(1);
        }

        void prepare(int channels) {
            if (this.inputRing != null && this.inputRing.length == channels) {
                return;
            }
            allocate(channels);
        }

        private void allocate(int channels) {
            this.inputRing = new float[channels][FFT_SIZE];
            this.inputWritePos = new int[channels];
            this.pendingFill = new int[channels];
            this.samplesSeen = new long[channels];
            this.flatWindow = new float[channels][FFT_SIZE];
            this.dryDelay = new float[channels][DRY_DELAY];
            this.dryDelayPos = new int[channels];
            this.outQueue = new float[channels][OUT_QUEUE];
            this.outQueueRead = new int[channels];
            this.outQueueWrite = new int[channels];
            this.outQueueCount = new int[channels];
            this.outBlock = new float[channels][STEP_SIZE];
            this.rms = new double[channels];
            this.targetRatio = new double[channels];
            this.currentRatio = new double[channels];
            this.cycleCounter = new long[channels];
            this.shifters = new SmbPitchShifter[channels];
            this.detectors = new YinDetector[channels];
            for (int c = 0; c < channels; c++) {
                this.targetRatio[c] = 1.0D;
                this.currentRatio[c] = 1.0D;
                this.shifters[c] = new SmbPitchShifter(SAMPLE_RATE, FFT_SIZE, OVERLAP);
                this.detectors[c] = new YinDetector(SAMPLE_RATE, FFT_SIZE);
            }
        }
    }
}

