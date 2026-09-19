package org.sawiq.svvoicechanger.client.model;

import java.util.Optional;

import static org.sawiq.svvoicechanger.client.model.VoiceParameter.BIT_DEPTH;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.DE_ESS;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.DISTORTION;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.FORMANT;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.GAIN;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.GATE;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.GROWL;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.HIGH_EQ;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.LOW_EQ;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.MID_EQ;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.MIX;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.VOICE_MATCH;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.NOISE;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.PITCH;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.REVERB_DECAY;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.REVERB_MIX;
import static org.sawiq.svvoicechanger.client.model.VoiceParameter.REVERB_SIZE;

/**
 * Built-in voices.
 *
 * <p>The numbers below assume the pitch and formant controls are genuinely
 * independent, which they are since the chain shifts the harmonic content and
 * the spectral envelope separately. That is what lets, say, {@link #WOMAN}
 * raise the pitch by a musical fourth while moving the vocal tract only ~20%,
 * the ratio that actually separates adult female from adult male speech. Tying
 * the two together is what produces the chipmunk sound.</p>
 *
 * <p>The pitch figures are written against a roughly 120 Hz male voice, and
 * every character voice also sets {@link VoiceParameter#VOICE_MATCH}, which
 * makes the chain treat that partly as a target rather than purely as a
 * multiplier. Without any adaptation the presets only work for speakers who
 * already sound like the person they were tuned on; with full adaptation a
 * speaker who happens to sit on a preset's target hears nothing happen at all,
 * which reads as the mod being broken. Six tenths keeps the extremes away
 * without ever silencing the preset. Radio leaves it off entirely: it is a
 * transmission effect, not a different person, so it must keep your pitch.</p>
 *
 * <p>Reverb and hiss are used sparingly. This voice is going through a speech
 * codec at a low bitrate, which spends its bits on whatever is present: a
 * reverberant or noisy signal leaves fewer for the speech itself, so what the
 * listener receives is worse than what was sent. Measured against a speech-like
 * signal, reverb costs more waveform regularity than any other stage here.</p>
 *
 * <p>Enum constant names are persisted in preset files and must not change.</p>
 */
public enum VoiceChangerPreset {
    CUSTOM("custom") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.neutral();
        }
    },

    /** Deeper, broader male voice: chest weight without turning into a monster. */
    MAN("man") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.16D)
                    .set(PITCH, 0.84D).set(VOICE_MATCH, 0.60D).set(FORMANT, 0.87D).set(GROWL, 0.16D)
                    .set(LOW_EQ, 3.0D).set(MID_EQ, -0.5D).set(HIGH_EQ, -2.0D)
                    .set(GATE, 0.30D).set(DE_ESS, 0.25D)
                    .build();
        }
    },

    /**
     * Adult female voice. Pitch up a fourth, vocal tract only slightly
     * shorter, low end trimmed to remove the male chest resonance, and a
     * firmer de-esser because raising the pitch also raises sibilance.
     */
    WOMAN("woman") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.76D)
                    .set(PITCH, 1.45D).set(VOICE_MATCH, 0.60D).set(FORMANT, 1.20D)
                    .set(LOW_EQ, -3.5D).set(MID_EQ, 1.0D).set(HIGH_EQ, 2.0D)
                    .set(GATE, 0.30D).set(DE_ESS, 0.55D)
                    .build();
        }
    },

    /** Enormous, slow, hall-sized. Formant far below pitch to sell the scale. */
    TITAN("titan") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.05D)
                    .set(PITCH, 0.72D).set(VOICE_MATCH, 0.60D).set(FORMANT, 0.62D).set(GROWL, 0.45D)
                    .set(LOW_EQ, 6.0D).set(MID_EQ, -1.5D).set(HIGH_EQ, -4.0D)
                    .set(DISTORTION, 0.06D)
                    .set(REVERB_MIX, 0.16D).set(REVERB_SIZE, 0.85D).set(REVERB_DECAY, 0.70D)
                    .set(GATE, 0.30D).set(DE_ESS, 0.20D)
                    .build();
        }
    },

    /** Child: pitch and vocal tract both up, but the tract further than the pitch. */
    KID("kid") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.11D)
                    .set(PITCH, 1.34D).set(VOICE_MATCH, 0.60D).set(FORMANT, 1.46D)
                    .set(LOW_EQ, -5.0D).set(MID_EQ, 1.5D).set(HIGH_EQ, 2.5D)
                    .set(GATE, 0.30D).set(DE_ESS, 0.55D)
                    .build();
        }
    },

    /** Possessed: deep, grinding, with a cathedral behind it. */
    DEMON("demon") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.12D)
                    .set(PITCH, 0.68D).set(VOICE_MATCH, 0.60D).set(FORMANT, 0.76D).set(GROWL, 0.50D)
                    .set(LOW_EQ, 2.0D).set(MID_EQ, 0.5D).set(HIGH_EQ, -3.0D)
                    .set(DISTORTION, 0.26D)
                    .set(REVERB_MIX, 0.18D).set(REVERB_SIZE, 0.62D).set(REVERB_DECAY, 0.68D)
                    .set(GATE, 0.32D).set(DE_ESS, 0.20D)
                    .build();
        }
    },

    /**
     * The Dark Knight's interrogation voice: dropped an octave-ish, chest
     * resonance from the sub layer, mids pushed for the rasp, highs pulled so
     * it stays a growl rather than a shout, and a tight room so it sounds like
     * a cowl rather than a cathedral.
     */
    BATMAN("batman") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 1.14D)
                    .set(PITCH, 0.80D).set(VOICE_MATCH, 0.60D).set(FORMANT, 0.80D).set(GROWL, 0.55D)
                    .set(LOW_EQ, 5.0D).set(MID_EQ, 1.5D).set(HIGH_EQ, -3.5D)
                    .set(DISTORTION, 0.18D)
                    .set(REVERB_MIX, 0.10D).set(REVERB_SIZE, 0.28D).set(REVERB_DECAY, 0.40D)
                    .set(GATE, 0.42D).set(DE_ESS, 0.35D)
                    .build();
        }
    },

    /** Handheld radio: band-limited, driven, with carrier hiss. */
    RADIO("radio") {
        @Override
        VoiceProfile buildProfile() {
            return VoiceProfile.builder()
                    .set(MIX, 1.00D).set(GAIN, 0.93D)
                    .set(PITCH, 1.00D).set(FORMANT, 1.00D)
                    .set(VoiceParameter.RADIO, 0.90D)
                    .set(MID_EQ, 2.5D)
                    .set(DISTORTION, 0.16D).set(NOISE, 0.08D).set(BIT_DEPTH, 12.0D)
                    .set(GATE, 0.38D).set(DE_ESS, 0.30D)
                    .build();
        }
    };

    private final String key;
    private VoiceProfile profile;

    VoiceChangerPreset(String key) {
        this.key = key;
    }

    abstract VoiceProfile buildProfile();

    /** The preset's tuning. Built lazily once, then shared: profiles are immutable. */
    public VoiceProfile profile() {
        VoiceProfile cached = this.profile;
        if (cached == null) {
            cached = buildProfile();
            this.profile = cached;
        }
        return cached;
    }

    /** Stable public id, as other mods refer to this voice through the API. */
    public String id() {
        return this.key;
    }

    public String getTranslationKey() {
        return "svvoicechanger.preset." + this.key;
    }

    /** Built-in voices in menu order, excluding the free-form custom slot. */
    public static VoiceChangerPreset[] selectable() {
        return new VoiceChangerPreset[] {MAN, WOMAN, KID, TITAN, DEMON, BATMAN, RADIO};
    }

    /** Looks a built-in voice up by its public id, such as {@code "radio"}. */
    public static Optional<VoiceChangerPreset> byKey(String id) {
        if (id == null) {
            return Optional.empty();
        }

        for (VoiceChangerPreset preset : values()) {
            if (preset.key.equals(id)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    public static VoiceChangerPreset byName(String name) {
        if (name == null || name.isBlank()) {
            return CUSTOM;
        }

        try {
            return valueOf(name);
        } catch (IllegalArgumentException ignored) {
            return CUSTOM;
        }
    }

}
