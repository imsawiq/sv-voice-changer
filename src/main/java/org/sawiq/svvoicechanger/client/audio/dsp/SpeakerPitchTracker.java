package org.sawiq.svvoicechanger.client.audio.dsp;

import java.util.Arrays;

/**
 * Running estimate of how high the person at the microphone actually speaks.
 *
 * <p>The built-in voices are written as ratios against a roughly 120 Hz male
 * voice. Applied literally, a woman selecting the female voice gets her pitch
 * multiplied again and lands near 290 Hz, far above any real speaking voice.
 * Knowing the speaker's own pitch lets a preset describe the voice it is aiming
 * for rather than a blind multiplication.</p>
 *
 * <p>Robustness matters more here than speed. The calibration divides by this
 * number, so an octave error does not merely mistune the result, it inverts it:
 * hearing a 200 Hz voice as 100 Hz turns the male preset into a ratio of almost
 * exactly 1, leaving the voice untouched while every other stage still colours
 * it. Three things guard against that: a window long enough to hold many
 * periods, a median rather than an average, and an explicit rejection of
 * candidates that sit near half or double the established estimate.</p>
 */
public final class SpeakerPitchTracker {
    /**
     * Analysis window at the capture rate: 85 ms, long enough to hold well over
     * a dozen periods of a low voice, which is what keeps the detector off the
     * subharmonic.
     */
    private static final int WINDOW = 4_096;
    /**
     * Pitch detection runs at a quarter of the capture rate. The detector's
     * cost grows with the square of the window, and speech pitch lives far
     * below 6 kHz, so working at 12 kHz is sixteen times cheaper for the same
     * span of audio and the same accuracy. Low-passing well below the new
     * Nyquist also strips the upper harmonics that tempt the detector into
     * answering an octave out.
     */
    private static final int DECIMATION = 4;
    private static final int DETECTION_WINDOW = WINDOW / DECIMATION;
    private static final double DETECTION_LOW_PASS_HZ = 1_200.0;
    /** Plausible speaking range; anything outside is a detector error. */
    private static final double MIN_SPEAKING_HZ = 70.0;
    private static final double MAX_SPEAKING_HZ = 350.0;
    private static final double SILENCE_RMS = 0.004D;
    /** Frames kept for the median: several seconds of speech. */
    private static final int HISTORY = 24;
    /** Voiced frames needed before the estimate is offered at all. */
    private static final int MINIMUM_FRAMES = 6;
    /** How close to an exact octave counts as an octave confusion. */
    private static final double OCTAVE_TOLERANCE = 0.12D;

    private final YinDetector detector;
    private final double lowPassCoefficient;
    private final float[] ring = new float[WINDOW];
    private final float[] window = new float[WINDOW];
    private final float[] detectionWindow = new float[DETECTION_WINDOW];
    private final double[] history = new double[HISTORY];
    private final double[] sorted = new double[HISTORY];

    private int writeIndex;
    private long samplesSeen;
    private int count;
    private int historyIndex;

    public SpeakerPitchTracker(float sampleRate) {
        this.detector = new YinDetector(sampleRate / DECIMATION, DETECTION_WINDOW);
        this.lowPassCoefficient = 1.0D - Math.exp(-2.0D * Math.PI * DETECTION_LOW_PASS_HZ / sampleRate);
    }

    /** Feeds one input sample. Cheap enough for the per-sample path. */
    public void push(float sample) {
        this.ring[this.writeIndex] = sample;
        this.writeIndex = this.writeIndex + 1 >= WINDOW ? 0 : this.writeIndex + 1;
        this.samplesSeen++;
    }

    /**
     * Runs the detector over the samples collected so far. Expensive, so the
     * caller decides how often; the speaker's pitch changes over seconds, not
     * milliseconds.
     */
    public void analyse() {
        if (this.samplesSeen < WINDOW) {
            return;
        }

        flatten();
        if (rootMeanSquare(this.window) < SILENCE_RMS) {
            return;
        }

        decimate();
        float detected = this.detector.getPitch(this.detectionWindow);
        if (detected < MIN_SPEAKING_HZ || detected > MAX_SPEAKING_HZ) {
            return;
        }

        double candidate = correctOctave(detected);
        this.history[this.historyIndex] = candidate;
        this.historyIndex = this.historyIndex + 1 >= HISTORY ? 0 : this.historyIndex + 1;
        if (this.count < HISTORY) {
            this.count++;
        }
    }

    /**
     * @return the speaker's typical pitch in Hz, or 0 while too little voiced
     *         audio has been heard to say
     */
    public double speakingPitchHz() {
        if (this.count < MINIMUM_FRAMES) {
            return 0.0D;
        }

        System.arraycopy(this.history, 0, this.sorted, 0, this.count);
        Arrays.sort(this.sorted, 0, this.count);
        return this.sorted[this.count / 2];
    }

    public void reset() {
        this.count = 0;
        this.historyIndex = 0;
        this.writeIndex = 0;
        this.samplesSeen = 0L;
        Arrays.fill(this.ring, 0f);
    }

    /**
     * Pulls a candidate back into the octave the established estimate already
     * sits in. Pitch detectors slip by exactly a factor of two far more often
     * than they are wrong by a small amount, and a single such slip would
     * otherwise drag the median with it.
     */
    private double correctOctave(double candidate) {
        double established = speakingPitchHz();
        if (established <= 0.0D) {
            return candidate;
        }

        if (isNear(candidate * 2.0D, established)) {
            return candidate * 2.0D;
        }
        if (isNear(candidate / 2.0D, established)) {
            return candidate / 2.0D;
        }
        return candidate;
    }

    private static boolean isNear(double value, double reference) {
        return Math.abs(value - reference) / reference < OCTAVE_TOLERANCE;
    }

    /** Low-passes the window and keeps every fourth sample. */
    private void decimate() {
        double state = 0.0D;
        for (int i = 0; i < WINDOW; i++) {
            state += this.lowPassCoefficient * (this.window[i] - state);
            if (i % DECIMATION == 0) {
                this.detectionWindow[i / DECIMATION] = (float) state;
            }
        }
    }

    /** Copies the ring into a flat oldest-to-newest window. */
    private void flatten() {
        int firstChunk = WINDOW - this.writeIndex;
        System.arraycopy(this.ring, this.writeIndex, this.window, 0, firstChunk);
        if (firstChunk < WINDOW) {
            System.arraycopy(this.ring, 0, this.window, firstChunk, WINDOW - firstChunk);
        }
    }

    private static double rootMeanSquare(float[] samples) {
        double sum = 0.0D;
        for (float sample : samples) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }
}
