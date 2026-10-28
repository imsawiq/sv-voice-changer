package org.sawiq.svvoicechanger.client.ai.engine;

/**
 * Which engine the player asked for: one in particular, or whichever is ready.
 */
public enum EngineChoice {
    AUTO("auto", null),
    VOICE_ENGINE("voice_engine", EngineKind.VOICE_ENGINE),
    W_OKADA("w_okada", EngineKind.W_OKADA),
    ELEVENLABS("elevenlabs", EngineKind.ELEVENLABS);

    private final String id;
    private final EngineKind kind;

    EngineChoice(String id, EngineKind kind) {
        this.id = id;
        this.kind = kind;
    }

    public String id() {
        return this.id;
    }

    /** The engine pinned by this choice, or {@code null} for {@link #AUTO}. */
    public EngineKind kind() {
        return this.kind;
    }

    public String translationKey() {
        return "svvoicechanger.ai.choice." + this.id;
    }

    public EngineChoice next() {
        EngineChoice[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static EngineChoice byId(String id) {
        for (EngineChoice choice : values()) {
            if (choice.id.equals(id)) {
                return choice;
            }
        }
        return AUTO;
    }
}
