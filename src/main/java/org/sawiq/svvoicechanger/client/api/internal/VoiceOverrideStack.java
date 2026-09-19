package org.sawiq.svvoicechanger.client.api.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.sawiq.svvoicechanger.client.api.VoiceOverride;

/**
 * The overrides mods are currently holding, and which of them wins.
 *
 * <p>The winner is recomputed on every change and published to a volatile
 * field, because the audio thread reads it once per block and must never
 * block or allocate to do so. Mutations happen on whatever thread a mod calls
 * from, which is why the list itself is only ever touched under the lock.</p>
 */
public final class VoiceOverrideStack {
    private final Object lock = new Object();
    private final List<Entry> entries = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();

    private volatile Entry winner;

    /** Notified whenever the winning override changes, including to none. */
    private volatile Runnable changeListener = () -> {
    };

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    public VoiceOverride push(VoiceOverride.Request request) {
        // Checked here rather than left to fail later: an entry with no request
        // breaks every recomputation that follows, which would surface as a
        // repeating failure in our own tick rather than in the caller.
        Objects.requireNonNull(request, "request");

        Entry entry = new Entry(request, this.sequence.incrementAndGet(), this);
        synchronized (this.lock) {
            this.entries.add(entry);
            recomputeWinner();
        }
        this.changeListener.run();
        return entry;
    }

    public void releaseOwner(String ownerId) {
        boolean removedAny;
        synchronized (this.lock) {
            removedAny = this.entries.removeIf(entry -> entry.request.ownerId().equals(ownerId));
            if (removedAny) {
                recomputeWinner();
            }
        }
        if (removedAny) {
            this.changeListener.run();
        }
    }

    /** Drops everything. Used when the mod shuts down. */
    public void clear() {
        synchronized (this.lock) {
            this.entries.clear();
            this.winner = null;
        }
        this.changeListener.run();
    }

    /** The override currently shaping the voice, if any. Safe on the audio thread. */
    public Optional<VoiceOverride> winning() {
        return Optional.ofNullable(this.winner);
    }

    /** The request currently shaping the voice, if any. Safe on the audio thread. */
    public Optional<VoiceOverride.Request> winningRequest() {
        Entry current = this.winner;
        return current == null ? Optional.empty() : Optional.of(current.request);
    }

    private void release(Entry entry) {
        boolean removed;
        synchronized (this.lock) {
            removed = this.entries.remove(entry);
            if (removed) {
                recomputeWinner();
            }
        }
        if (removed) {
            this.changeListener.run();
        }
    }

    /**
     * Highest priority wins; equal priorities are broken by whoever pushed
     * last, so a mod layering a second effect over its own gets the newer one.
     */
    private void recomputeWinner() {
        Entry best = null;
        for (Entry entry : this.entries) {
            if (best == null
                    || entry.request.priority() > best.request.priority()
                    || (entry.request.priority() == best.request.priority() && entry.order > best.order)) {
                best = entry;
            }
        }
        this.winner = best;
    }

    private static final class Entry implements VoiceOverride {
        private final VoiceOverride.Request request;
        private final long order;
        private final VoiceOverrideStack stack;
        private volatile boolean released;

        private Entry(VoiceOverride.Request request, long order, VoiceOverrideStack stack) {
            this.request = request;
            this.order = order;
            this.stack = stack;
        }

        @Override
        public String ownerId() {
            return this.request.ownerId();
        }

        @Override
        public int priority() {
            return this.request.priority();
        }

        @Override
        public boolean isActive() {
            return !this.released;
        }

        @Override
        public boolean isWinning() {
            return this.stack.winner == this;
        }

        @Override
        public void release() {
            if (this.released) {
                return;
            }

            this.released = true;
            this.stack.release(this);
        }
    }
}
