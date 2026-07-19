package org.sawiq.svvoicechanger.client.model;

public record VoiceChangerProfile(
        double mix,
        double gain,
        double pitch,
        double formant,
        double distortion,
        double robotMix,
        int robotFrequency,
        double echoMix,
        int echoDelayMs,
        double echoFeedback,
        double tremoloDepth,
        double tremoloRate,
        double lowEq,
        double midEq,
        double highEq,
        double noise,
        double autotuneMix,
        double autotuneStrength,
        int autotuneKey,
        int autotuneScale,
        double bitDepth
) {
    public static final int SCALE_CHROMATIC = 0;
    public static final int SCALE_MAJOR = 1;
    public static final int SCALE_MINOR = 2;

    /**
     * Highest bit depth: above this value the bit crusher is bypassed, so the
     * voice keeps its full resolution. Lowering {@link #bitDepth()} toward
     * {@link #BIT_DEPTH_MIN} crushes the signal into a chunky lo-fi tone.
     */
    public static final double BIT_DEPTH_CLEAN = 16.0D;
    public static final double BIT_DEPTH_MIN = 1.0D;

    /**
     * Neutral passthrough profile: all effect amounts at zero and the
     * wet/dry mix at {@code 0.0}, so {@link org.sawiq.svvoicechanger.client.audio.VoiceChangerAudioEngine}
     * outputs the raw microphone signal unchanged. Used by the &quot;reset to
     * original voice&quot; button in the studio UI.
     */
    public static VoiceChangerProfile passthrough() {
        return new VoiceChangerProfile(
                0.00D, 1.00D, 1.00D, 1.00D,
                0.00D, 0.00D, 60,
                0.00D, 120, 0.00D,
                0.00D, 2.00D,
                0.0D, 0.0D, 0.0D,
                0.00D,
                0.00D, 0.60D, 0, SCALE_CHROMATIC,
                BIT_DEPTH_CLEAN
        );
    }

    public static VoiceChangerProfile defaultsFor(VoiceChangerPreset preset) {
        return switch (preset) {
            case MAN -> new VoiceChangerProfile(0.80D, 1.08D, 0.92D, 0.92D, 0.00D, 0.00D, 40, 0.04D, 90, 0.08D, 0.00D, 2.20D, 2.2D, 0.0D, -1.2D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
            case WOMAN -> new VoiceChangerProfile(0.78D, 1.00D, 1.12D, 1.18D, 0.00D, 0.00D, 65, 0.03D, 84, 0.05D, 0.00D, 2.00D, -2.4D, 1.2D, 2.8D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
            case TITAN -> new VoiceChangerProfile(0.86D, 1.18D, 0.72D, 0.78D, 0.10D, 0.00D, 30, 0.28D, 170, 0.36D, 0.00D, 1.20D, 5.6D, -2.2D, -3.8D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
            case KID -> new VoiceChangerProfile(0.80D, 1.00D, 1.32D, 1.42D, 0.00D, 0.00D, 82, 0.00D, 70, 0.00D, 0.00D, 2.20D, -4.4D, 1.9D, 4.2D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
            case DEMON -> new VoiceChangerProfile(0.86D, 1.14D, 0.84D, 0.72D, 0.14D, 0.08D, 46, 0.24D, 155, 0.26D, 0.03D, 1.40D, 4.2D, -1.6D, -4.4D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
            case RADIO -> new VoiceChangerProfile(0.96D, 0.94D, 1.00D, 1.00D, 0.06D, 0.00D, 98, 0.00D, 70, 0.00D, 0.03D, 2.10D, -9.5D, 5.4D, -8.4D, 0.03D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, 9.0D);
            case CUSTOM -> new VoiceChangerProfile(0.90D, 1.00D, 1.00D, 1.00D, 0.00D, 0.00D, 60, 0.00D, 120, 0.10D, 0.00D, 2.50D, 0.0D, 0.0D, 0.0D, 0.00D, 0.00D, 0.60D, 0, SCALE_CHROMATIC, BIT_DEPTH_CLEAN);
        };
    }

    public VoiceChangerProfile withMix(double v) { return new VoiceChangerProfile(v, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withGain(double v) { return new VoiceChangerProfile(mix, v, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withPitch(double v) { return new VoiceChangerProfile(mix, gain, v, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withFormant(double v) { return new VoiceChangerProfile(mix, gain, pitch, v, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withDistortion(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, v, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withRobotMix(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, v, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withRobotFrequency(int v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, v, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withEchoMix(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, v, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withEchoDelayMs(int v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, v, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withEchoFeedback(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, v, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withTremoloDepth(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, v, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withTremoloRate(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, v, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withLowEq(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, v, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withMidEq(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, v, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withHighEq(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, v, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withNoise(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, v, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withAutotuneMix(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, v, autotuneStrength, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withAutotuneStrength(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, v, autotuneKey, autotuneScale, bitDepth); }
    public VoiceChangerProfile withAutotuneKey(int v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, v, autotuneScale, bitDepth); }
    public VoiceChangerProfile withAutotuneScale(int v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, v, bitDepth); }
    public VoiceChangerProfile withBitDepth(double v) { return new VoiceChangerProfile(mix, gain, pitch, formant, distortion, robotMix, robotFrequency, echoMix, echoDelayMs, echoFeedback, tremoloDepth, tremoloRate, lowEq, midEq, highEq, noise, autotuneMix, autotuneStrength, autotuneKey, autotuneScale, v); }
}

