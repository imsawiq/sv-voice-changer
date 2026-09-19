package org.sawiq.svvoicechanger.client.model;

import java.util.Locale;

/**
 * Every tunable knob of a voice profile, described once.
 *
 * <p>This enum is the single source of truth for a parameter's identity, its
 * allowed range, the value a fresh profile starts at, the value that means
 * "this effect is off", and the unit it reads in. The studio builds its
 * sliders from it, {@link org.sawiq.svvoicechanger.client.preset.VoiceChangerPresetStore}
 * reads and writes files from it, and {@link VoiceProfile} validates against
 * it. Adding a knob means adding one line here.</p>
 */
public enum VoiceParameter {
    // --- Output stage ------------------------------------------------------
    /**
     * How much of the processed voice replaces the raw microphone signal.
     *
     * <p>Its neutral value is 1, not 0, so the strength control never reduces
     * it. Blending a pitch-shifted copy back over the original is a chorus, and
     * at a small pitch difference that is exactly the doubled-voice effect: two
     * near-identical voices a few percent apart. Strength has to weaken the
     * shift itself, not fade a detuned copy in against the dry signal. Nothing
     * is lost by holding the blend open, because with every other parameter at
     * neutral the processed path already is the original voice.</p>
     */
    MIX("mix", Unit.PERCENT, 0.0D, 1.0D, 1.0D, 1.0D),
    /** Output level trim applied after the whole chain. */
    GAIN("gain", Unit.PERCENT, 0.40D, 2.50D, 1.0D, 1.0D),

    // --- Voice shape -------------------------------------------------------
    /** Fundamental frequency multiplier: how high or low the voice sits. */
    PITCH("pitch", Unit.SEMITONES, 0.50D, 2.00D, 1.0D, 1.0D),
    /**
     * Vocal-tract length multiplier. Below 100% the speaker sounds physically
     * bigger, above it smaller. Independent of {@link #PITCH}, which is what
     * keeps a raised voice from turning into a chipmunk.
     */
    FORMANT("formant", Unit.PERCENT, 0.50D, 2.00D, 1.0D, 1.0D),
    /** Sub-octave chest layer: weight and menace without extra distortion. */
    GROWL("growl", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    /**
     * How far the preset aims at an absolute target voice instead of simply
     * multiplying whatever pitch it is given. At 0 the pitch control is a plain
     * ratio. At 100% the preset lands on the same voice no matter who is
     * speaking, which is what stops the female preset from turning an already
     * high voice into a chipmunk.
     */
    VOICE_MATCH("voiceMatch", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),

    // --- Tone --------------------------------------------------------------
    LOW_EQ("lowEq", Unit.DECIBELS, -12.0D, 12.0D, 0.0D, 0.0D),
    MID_EQ("midEq", Unit.DECIBELS, -12.0D, 12.0D, 0.0D, 0.0D),
    HIGH_EQ("highEq", Unit.DECIBELS, -12.0D, 12.0D, 0.0D, 0.0D),
    /** Telephone and walkie-talkie band limiting. */
    RADIO("radio", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),

    // --- Cleanup -----------------------------------------------------------
    /** Noise gate aggressiveness. 0 leaves the gate open at all times. */
    GATE("gate", Unit.PERCENT, 0.0D, 1.0D, 0.30D, 0.0D),
    /** Sibilance tamer, mostly needed once the pitch is shifted upwards. */
    DE_ESS("deEss", Unit.PERCENT, 0.0D, 1.0D, 0.30D, 0.0D),

    // --- Character ---------------------------------------------------------
    DISTORTION("distortion", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    BIT_DEPTH("bitDepth", Unit.BITS, 1.0D, 16.0D, 16.0D, 16.0D, true),
    NOISE("noise", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    ROBOT_MIX("robotMix", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    ROBOT_FREQUENCY("robotFrequency", Unit.HERTZ, 20.0D, 300.0D, 60.0D, 60.0D, true),
    TREMOLO_DEPTH("tremoloDepth", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    TREMOLO_RATE("tremoloRate", Unit.HERTZ, 0.20D, 12.0D, 2.50D, 2.50D),

    // --- Space -------------------------------------------------------------
    REVERB_MIX("reverbMix", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    REVERB_SIZE("reverbSize", Unit.PERCENT, 0.0D, 1.0D, 0.40D, 0.40D),
    REVERB_DECAY("reverbDecay", Unit.PERCENT, 0.0D, 1.0D, 0.50D, 0.50D),

    // --- Autotune ----------------------------------------------------------
    AUTOTUNE_MIX("autotuneMix", Unit.PERCENT, 0.0D, 1.0D, 0.0D, 0.0D),
    AUTOTUNE_SPEED("autotuneStrength", Unit.PERCENT, 0.0D, 1.0D, 0.60D, 0.60D),
    AUTOTUNE_KEY("autotuneKey", Unit.PLAIN, 0.0D, 11.0D, 0.0D, 0.0D, true),
    AUTOTUNE_SCALE("autotuneScale", Unit.PLAIN, 0.0D, 2.0D, 0.0D, 0.0D, true);

    /** How a value is shown to the user. */
    public enum Unit {
        PLAIN,
        /** Shown as a percentage of 1.0, so a gain of 1.25 reads as 125%. */
        PERCENT,
        DECIBELS,
        /** A frequency multiplier shown as a musical interval. */
        SEMITONES,
        HERTZ,
        BITS;

        public String format(double value) {
            return switch (this) {
                case PERCENT -> Math.round(value * 100.0D) + "%";
                case DECIBELS -> String.format(Locale.ROOT, "%+.1f dB", value);
                case SEMITONES -> String.format(Locale.ROOT, "%+.1f st", 12.0D * (Math.log(value) / Math.log(2.0D)));
                case HERTZ -> String.format(Locale.ROOT, "%.1f Hz", value);
                case BITS -> Math.round(value) + " bit";
                case PLAIN -> String.format(Locale.ROOT, "%.2f", value);
            };
        }
    }

    private final String id;
    private final Unit unit;
    private final double min;
    private final double max;
    private final double defaultValue;
    private final double neutralValue;
    private final boolean integral;

    VoiceParameter(String id, Unit unit, double min, double max, double defaultValue, double neutralValue) {
        this(id, unit, min, max, defaultValue, neutralValue, false);
    }

    VoiceParameter(String id, Unit unit, double min, double max, double defaultValue, double neutralValue, boolean integral) {
        this.id = id;
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.neutralValue = neutralValue;
        this.integral = integral;
    }

    /** Stable key used in preset files. Never rename: it breaks saved presets. */
    public String id() {
        return this.id;
    }

    public Unit unit() {
        return this.unit;
    }

    public double min() {
        return this.min;
    }

    public double max() {
        return this.max;
    }

    public double defaultValue() {
        return this.defaultValue;
    }

    /**
     * The value at which this parameter has no audible effect. The strength
     * control interpolates between this and the profile's value, so a preset
     * at 0% strength is inaudible no matter which knobs it sets.
     */
    public double neutralValue() {
        return this.neutralValue;
    }

    public boolean isIntegral() {
        return this.integral;
    }

    /** Clamps to range, rounds if integral, and replaces NaN or infinity with the default. */
    public double sanitize(double value) {
        if (!Double.isFinite(value)) {
            return this.defaultValue;
        }

        double clamped = Math.max(this.min, Math.min(this.max, value));
        return this.integral ? Math.rint(clamped) : clamped;
    }

    public String formatValue(double value) {
        return this.unit.format(value);
    }

    public String translationKey() {
        return "svvoicechanger.param." + this.id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }
}
