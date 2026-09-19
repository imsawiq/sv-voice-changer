package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.audio.dsp.YinDetector;
import org.sawiq.svvoicechanger.client.model.AutotuneScale;

/**
 * Turns the detected fundamental into a correction ratio for the pitch
 * shifter.
 *
 * <p>Autotune used to be a second, independent phase vocoder whose output was
 * crossfaded with the dry signal. Two vocoders in series smeared the voice,
 * and crossfading two uncorrelated signals combed the spectrum. Here the
 * tracker only produces a number: the chain multiplies it into the single
 * shifter's pitch ratio, so the amount control interpolates the correction
 * itself rather than mixing two versions of the voice together.</p>
 *
 * <p>Pitch is estimated with {@link YinDetector} (de Cheveigne and Kawahara,
 * JASA 2002). The detector is the expensive part of the chain, so it runs once
 * every {@value #DETECT_EVERY_N_HOPS} hops and the ratio is held in between.</p>
 */
public final class AutotuneTracker {
    private static final int DETECT_EVERY_N_HOPS = 4;
    private static final double MIN_RATIO = 0.5D;
    private static final double MAX_RATIO = 2.0D;
    private static final double SILENCE_RMS = 0.0025D;
    /** Glide time at amount 0: a slow, human-sounding slide into the note. */
    private static final double SLOWEST_GLIDE_SECONDS = 0.250D;
    /** Glide time at amount 1: an instant jump, the classic hard-tuned effect. */
    private static final double FASTEST_GLIDE_SECONDS = 0.0001D;

    private final YinDetector detector;
    private final double sampleRate;
    private final int hopSamples;

    private int hopCounter;
    private double targetRatio = 1.0D;
    private double currentRatio = 1.0D;

    public AutotuneTracker(float sampleRate, int windowSize, int hopSamples) {
        this.detector = new YinDetector(sampleRate, windowSize);
        this.sampleRate = sampleRate;
        this.hopSamples = hopSamples;
    }

    /**
     * Advances the tracker by one hop.
     *
     * @param window   the same analysis window the shifter is about to transform
     * @param settings scale, root note, amount and speed for this frame
     * @return the multiplier to fold into the shifter's pitch ratio; 1 means no correction
     */
    public double nextRatio(float[] window, Settings settings) {
        if (settings.amount() < 0.005D) {
            this.targetRatio = 1.0D;
            this.currentRatio = 1.0D;
            return 1.0D;
        }

        if (this.hopCounter++ % DETECT_EVERY_N_HOPS == 0) {
            this.targetRatio = detectRatio(window, settings);
        }

        double glideSeconds = FASTEST_GLIDE_SECONDS
                + SLOWEST_GLIDE_SECONDS * Math.pow(1.0D - settings.speed(), 3.0D);
        double glideCoefficient = 1.0D - Math.exp(-this.hopSamples / (this.sampleRate * glideSeconds));
        this.currentRatio += glideCoefficient * (this.targetRatio - this.currentRatio);

        double clamped = clamp(this.currentRatio, MIN_RATIO, MAX_RATIO);
        // Partial amounts pull the note part-way toward the scale instead of
        // blending two audio signals, which keeps the voice free of combing.
        return 1.0D + (clamped - 1.0D) * settings.amount();
    }

    public void reset() {
        this.hopCounter = 0;
        this.targetRatio = 1.0D;
        this.currentRatio = 1.0D;
    }

    private double detectRatio(float[] window, Settings settings) {
        if (rootMeanSquare(window) < SILENCE_RMS) {
            return 1.0D;
        }

        float detectedHz = this.detector.getPitch(window);
        if (detectedHz <= 0f) {
            return 1.0D;
        }

        double snappedHz = snapToScale(detectedHz, settings.rootPitchClass(), settings.scale().semitones());
        return clamp(snappedHz / detectedHz, MIN_RATIO, MAX_RATIO);
    }

    private static double rootMeanSquare(float[] window) {
        double sum = 0.0D;
        for (float sample : window) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / window.length);
    }

    /** Nearest allowed note, searched across the neighbouring octaves. */
    private static double snapToScale(double frequencyHz, int rootPitchClass, int[] semitones) {
        double midi = 69.0D + 12.0D * (Math.log(frequencyHz / 440.0D) / Math.log(2.0D));
        int baseMidi = (int) Math.floor(midi);

        int bestMidi = baseMidi;
        double bestDistance = Double.MAX_VALUE;
        for (int octaveOffset = -1; octaveOffset <= 1; octaveOffset++) {
            for (int degree : semitones) {
                int candidate = nearestMidiWithPitchClass(baseMidi + octaveOffset * 12, (rootPitchClass + degree) % 12);
                double distance = Math.abs(midi - candidate);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestMidi = candidate;
                }
            }
        }

        return 440.0D * Math.pow(2.0D, (bestMidi - 69) / 12.0D);
    }

    private static int nearestMidiWithPitchClass(int aroundMidi, int pitchClass) {
        int aroundPitchClass = ((aroundMidi % 12) + 12) % 12;
        int difference = pitchClass - aroundPitchClass;
        if (difference > 6) {
            difference -= 12;
        } else if (difference < -6) {
            difference += 12;
        }
        return aroundMidi + difference;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @param amount         how far the pitch is pulled toward the scale, 0 disables autotune
     * @param speed          0 glides into the note, 1 snaps to it
     * @param rootPitchClass 0 for C through 11 for B
     */
    public record Settings(double amount, double speed, int rootPitchClass, AutotuneScale scale) {
    }
}
