package org.sawiq.svvoicechanger.client.api.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.sawiq.svvoicechanger.client.api.VoiceChangerListener;

/**
 * Fans events out to the mods that asked for them.
 *
 * <p>A listener that throws is dropped rather than allowed to break the
 * notification for everyone after it: one misbehaving mod should not be able
 * to stop the rest of the game from hearing that the voice changed.</p>
 */
public final class VoiceChangerListeners {
    private final List<VoiceChangerListener> listeners = new CopyOnWriteArrayList<>();
    private final Consumer<Exception> failureReporter;

    public VoiceChangerListeners(Consumer<Exception> failureReporter) {
        this.failureReporter = failureReporter;
    }

    public void add(VoiceChangerListener listener) {
        if (listener != null && !this.listeners.contains(listener)) {
            this.listeners.add(listener);
        }
    }

    public void remove(VoiceChangerListener listener) {
        this.listeners.remove(listener);
    }

    public void clear() {
        this.listeners.clear();
    }

    public void notifyEach(Consumer<VoiceChangerListener> event) {
        for (VoiceChangerListener listener : this.listeners) {
            try {
                event.accept(listener);
            } catch (Exception exception) {
                this.listeners.remove(listener);
                this.failureReporter.accept(exception);
            }
        }
    }
}
