package org.sawiq.svvoicechanger.client.ai.engine;

/**
 * The places an AI voice can come from.
 *
 * <p>Ordered by preference: the auto pick takes the first one that is ready.
 * The local engines go first because they are free and live; ElevenLabs is
 * paid and can only answer once a phrase is finished.</p>
 */
public enum EngineKind {
    /** The companion program this mod ships a guide for, running on this computer. */
    VOICE_ENGINE("voice_engine", Delivery.LIVE),
    /** w-okada's Voice Changer, which many players already run for RVC voices. */
    W_OKADA("w_okada", Delivery.LIVE),
    /** ElevenLabs' cloud voice changer, with the player's own API key. */
    ELEVENLABS("elevenlabs", Delivery.PHRASE);

    /**
     * How converted audio reaches the chat. A live engine answers within a
     * fraction of a second while the player is still talking; a phrase engine
     * needs the whole phrase first and speaks it after the key is let go.
     */
    public enum Delivery {
        LIVE,
        PHRASE
    }

    private final String id;
    private final Delivery delivery;

    EngineKind(String id, Delivery delivery) {
        this.id = id;
        this.delivery = delivery;
    }

    /** Stable identifier used in settings files. */
    public String id() {
        return this.id;
    }

    public Delivery delivery() {
        return this.delivery;
    }

    public String translationKey() {
        return "svvoicechanger.ai.engine." + this.id;
    }

    /** A name short enough for a quarter-width button. */
    public String shortTranslationKey() {
        return translationKey() + ".short";
    }

    public static EngineKind byId(String id) {
        for (EngineKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
