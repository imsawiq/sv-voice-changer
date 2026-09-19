package org.sawiq.svvoicechanger.client.model;

/**
 * Everything the mod restores when the game restarts.
 *
 * @param contributedVoiceId id of the voice another mod contributed, when that
 *                           is what the player picked. It is remembered even
 *                           while that mod is missing, so uninstalling it for
 *                           one session does not silently reset the choice.
 */
public record VoiceChangerState(
        boolean enabled,
        boolean selfListen,
        VoiceChangerPreset preset,
        int strength,
        String savedPresetName,
        String contributedVoiceId,
        VoiceProfile profile
) {
    public static VoiceChangerState defaults() {
        return new VoiceChangerState(
                false,
                false,
                VoiceChangerPreset.MAN,
                100,
                null,
                null,
                VoiceChangerPreset.MAN.profile()
        );
    }
}
