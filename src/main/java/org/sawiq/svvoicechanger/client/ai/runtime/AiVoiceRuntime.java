package org.sawiq.svvoicechanger.client.ai.runtime;

import org.sawiq.svvoicechanger.client.ai.engine.LiveConverter;
import org.sawiq.svvoicechanger.client.ai.engine.PhraseConverter;

/**
 * The capture thread's way into the AI voice: one call per 20 ms tick.
 *
 * <p>The stream is swapped as a whole whenever the player changes engine or
 * settings, through one volatile field, so the capture thread always sees
 * either the old stream or the new one and never a half-built mixture.
 * Closing the old one happens off that thread.</p>
 */
public final class AiVoiceRuntime {
    private volatile AiVoiceStream stream;
    private final VoiceGate previewGate = new VoiceGate();

    /** Whether the AI voice is what goes to the chat right now. */
    public boolean isActive() {
        return this.stream != null;
    }

    /**
     * @param raw this tick's mono 48 kHz frame while the player is talking,
     *            {@code null} while they are not
     * @return the frame to send, {@code null} for nothing; when the AI voice
     *         is off, {@code raw} unchanged
     */
    public short[] onFrame(short[] raw) {
        AiVoiceStream current = this.stream;
        return current == null ? raw : current.onFrame(raw);
    }

    /**
     * For the studio's preview: the frame goes in whenever it sounds like
     * speech, since there is no key to tell, and what comes back is played to
     * the player only.
     *
     * @return the converted frame, or {@code null} for silence this tick
     */
    public short[] onPreviewFrame(short[] frame) {
        AiVoiceStream current = this.stream;
        if (current == null) {
            return null;
        }
        return current.onFrame(this.previewGate.isTalking(frame) ? frame : null);
    }

    public AiVoiceStream.Activity activity() {
        AiVoiceStream current = this.stream;
        return current == null ? AiVoiceStream.Activity.IDLE : current.activity();
    }

    /** Whether the engine behind the stream stopped answering for good. */
    public boolean hasGivenUp() {
        AiVoiceStream current = this.stream;
        return current != null && current.hasGivenUp();
    }

    /** The newest failure to tell the player about, or {@code null}. */
    public String takeFailure() {
        AiVoiceStream current = this.stream;
        return current == null ? null : current.takeFailure();
    }

    /** Round trip and chunk length of a live engine, or {@code null} for a phrase engine or none. */
    public LiveTiming liveTiming() {
        return this.stream instanceof LiveStream live
                ? new LiveTiming(live.lastRoundTripMillis(), live.chunkFrames())
                : null;
    }

    public void startLive(LiveConverter converter) {
        replace(new LiveStream(converter));
    }

    public void startPhrases(PhraseConverter converter) {
        replace(new PhraseStream(converter));
    }

    public void stop() {
        replace(null);
    }

    /** Synchronized because the client thread can drop a stream the lifecycle thread is replacing. */
    private synchronized void replace(AiVoiceStream next) {
        AiVoiceStream previous = this.stream;
        this.stream = next;
        if (previous != null) {
            previous.close();
        }
    }

    /** What the player hears as delay on a live engine, roughly the chunk plus the round trip. */
    public record LiveTiming(long roundTripMillis, int chunkFrames) {
    }
}
