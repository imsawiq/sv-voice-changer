package org.sawiq.svvoicechanger.protocol;

import java.util.Locale;

/**
 * How much freedom a server gives players over their voice.
 *
 * <p>The reason this exists is not safety but load: a server full of people
 * with the reverb and distortion sliders at maximum is unpleasant to be in, and
 * an operator asked for a way to say "pick one of the ready-made voices" without
 * having to switch the whole feature off.</p>
 *
 * <p>Ready-made means somebody chose the numbers deliberately: this mod's own
 * voices, a voice another mod contributed, or a voice the server shared. A
 * player's own slider work and their saved files are what gets restricted,
 * because those are the ones nobody vetted.</p>
 *
 * <p>Codes are part of the wire format and must not be renumbered. An
 * unrecognised one reads as {@link #ALL}, which is the same as an older server
 * that never sends this at all.</p>
 */
public enum VoiceSourceRule {
    /** No restriction. */
    ALL(0, "all"),
    /** Ready-made voices only: no hand tuning and no personal preset files. */
    READY_MADE(1, "presets"),
    /** Only the voices this server offers. */
    SERVER_ONLY(2, "server-only");

    /**
     * What {@code getSelectedVoiceId()} answers when the player tuned the voice
     * themselves or loaded one of their own files. Named here because this is
     * the class that has to tell those apart from a ready-made voice.
     */
    public static final String CUSTOM_VOICE_ID = "custom";

    /** Namespace the server's own shared voices are registered under. */
    public static final String SERVER_NAMESPACE = "server";

    private final int code;
    private final String configValue;

    VoiceSourceRule(int code, String configValue) {
        this.code = code;
        this.configValue = configValue;
    }

    public int code() {
        return this.code;
    }

    /** How an operator writes this in the config file. */
    public String configValue() {
        return this.configValue;
    }

    /** Translation key for the line shown to a player who is restricted. */
    public String translationKey() {
        return "svvoicechanger.server.voices." + this.configValue.replace('-', '_');
    }

    /** @param voiceId as {@code getSelectedVoiceId()} reports it */
    public boolean allows(String voiceId) {
        return switch (this) {
            case ALL -> true;
            case READY_MADE -> !CUSTOM_VOICE_ID.equals(voiceId);
            case SERVER_ONLY -> voiceId != null && voiceId.startsWith(SERVER_NAMESPACE + ":");
        };
    }

    /** Whether the player may move the parameter sliders at all. */
    public boolean allowsHandTuning() {
        return this == ALL;
    }

    /** Whether the player may load voices from their own preset folder. */
    public boolean allowsPersonalPresets() {
        return this == ALL;
    }

    public static VoiceSourceRule byCode(int code) {
        for (VoiceSourceRule rule : values()) {
            if (rule.code == code) {
                return rule;
            }
        }
        return ALL;
    }

    /** Falls back to {@link #ALL} rather than refusing to start on a typo. */
    public static VoiceSourceRule byConfigValue(String value, VoiceSourceRule fallback) {
        if (value == null) {
            return fallback;
        }

        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        for (VoiceSourceRule rule : values()) {
            if (rule.configValue.equals(trimmed)) {
                return rule;
            }
        }
        return fallback;
    }
}
