package org.sawiq.svvoicechanger.client.ai.runtime;

/**
 * What sits between the microphone and the chat while the AI voice is on.
 *
 * <p>The voice mod calls {@link #onFrame} once per 20 ms capture tick, whether
 * the player is talking or not, and sends exactly what comes back. That one
 * call carries everything that makes an AI voice different from an effect:
 * the answer can lag behind the microphone, it can keep coming after the key
 * is let go, and when the player is talking it may be nothing at all yet.</p>
 */
public interface AiVoiceStream extends AutoCloseable {
    /**
     * Failures are engine messages shown as they are, except this one, which
     * is the mod's own and is a translation key: phrases are coming in faster
     * than the engine converts them and the newest was dropped.
     */
    String FAILURE_BUSY = "svvoicechanger.ai.failure.busy";

    /** What the stream is doing, for the studio and the action bar. */
    enum Activity {
        IDLE,
        /** Live engine: converting while the player talks. */
        LISTENING,
        /** Phrase engine: holding the phrase until the player stops. */
        RECORDING,
        /** Phrase engine: waiting for the converted phrase. */
        CONVERTING,
        /** Sending converted audio to the chat. */
        SPEAKING
    }

    /**
     * Called on the capture thread; never blocks.
     *
     * @param raw this tick's mono 48 kHz frame while the player is talking,
     *            {@code null} while they are not
     * @return the frame to send, or {@code null} to send nothing this tick
     */
    short[] onFrame(short[] raw);

    Activity activity();

    /** The most recent failure the player should be told about, cleared by reading it. */
    String takeFailure();

    /**
     * Whether the engine stopped answering for good. Phrase engines never do:
     * each phrase is a fresh request, and one that fails says so on its own.
     */
    default boolean hasGivenUp() {
        return false;
    }

    @Override
    void close();
}
