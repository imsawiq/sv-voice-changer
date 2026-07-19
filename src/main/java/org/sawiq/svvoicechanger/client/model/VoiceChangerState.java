package org.sawiq.svvoicechanger.client.model;

public record VoiceChangerState(
        boolean enabled,
        boolean selfListen,
        VoiceChangerPreset preset,
        int strength,
        String savedPresetName,
        VoiceChangerProfile profile
) {
    public static VoiceChangerState defaults() {
        return new VoiceChangerState(
                false,
                false,
                VoiceChangerPreset.MAN,
                100,
                null,
                VoiceChangerProfile.defaultsFor(VoiceChangerPreset.MAN)
        );
    }
}

