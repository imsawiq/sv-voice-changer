package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.model.AutotuneScale;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * A profile resolved into the numbers the audio chain actually wants.
 *
 * <p>Keeping this separate from {@link VoiceProfile} means the chain never
 * reaches into the UI's data model, and the translation from user-facing
 * ranges to DSP units happens in exactly one place.</p>
 */
public record VoiceSettings(
        double blend,
        double gain,
        double pitch,
        double formant,
        double voiceMatch,
        double gate,
        double deEss,
        double lowEqDb,
        double midEqDb,
        double highEqDb,
        double radio,
        double reverbMix,
        double reverbSize,
        double reverbDecay,
        CharacterStage.Settings character,
        AutotuneTracker.Settings autotune
) {
    /** @param profile a profile already scaled by the strength control */
    public static VoiceSettings from(VoiceProfile profile) {
        return new VoiceSettings(
                profile.get(VoiceParameter.MIX),
                profile.get(VoiceParameter.GAIN),
                profile.get(VoiceParameter.PITCH),
                profile.get(VoiceParameter.FORMANT),
                profile.get(VoiceParameter.VOICE_MATCH),
                profile.get(VoiceParameter.GATE),
                profile.get(VoiceParameter.DE_ESS),
                profile.get(VoiceParameter.LOW_EQ),
                profile.get(VoiceParameter.MID_EQ),
                profile.get(VoiceParameter.HIGH_EQ),
                profile.get(VoiceParameter.RADIO),
                profile.get(VoiceParameter.REVERB_MIX),
                profile.get(VoiceParameter.REVERB_SIZE),
                profile.get(VoiceParameter.REVERB_DECAY),
                new CharacterStage.Settings(
                        profile.get(VoiceParameter.GROWL),
                        profile.get(VoiceParameter.ROBOT_MIX),
                        profile.get(VoiceParameter.ROBOT_FREQUENCY),
                        profile.get(VoiceParameter.DISTORTION),
                        profile.get(VoiceParameter.BIT_DEPTH),
                        profile.get(VoiceParameter.NOISE),
                        profile.get(VoiceParameter.TREMOLO_DEPTH),
                        profile.get(VoiceParameter.TREMOLO_RATE)
                ),
                new AutotuneTracker.Settings(
                        profile.get(VoiceParameter.AUTOTUNE_MIX),
                        profile.get(VoiceParameter.AUTOTUNE_SPEED),
                        profile.getInt(VoiceParameter.AUTOTUNE_KEY),
                        AutotuneScale.byIndex(profile.getInt(VoiceParameter.AUTOTUNE_SCALE))
                )
        );
    }
}
