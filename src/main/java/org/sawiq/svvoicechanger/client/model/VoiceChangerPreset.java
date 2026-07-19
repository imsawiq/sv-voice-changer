package org.sawiq.svvoicechanger.client.model;

public enum VoiceChangerPreset {
    CUSTOM("custom"),
    MAN("man"),
    WOMAN("woman"),
    TITAN("titan"),
    KID("kid"),
    DEMON("demon"),
    RADIO("radio");

    private final String key;

    VoiceChangerPreset(String key) {
        this.key = key;
    }

    public String getTranslationKey() {
        return "svvoicechanger.preset." + this.key;
    }

    public static VoiceChangerPreset fromIndex(int index) {
        VoiceChangerPreset[] presets = values();

        if (index < 0 || index >= presets.length) {
            return CUSTOM;
        }

        return presets[index];
    }
}

