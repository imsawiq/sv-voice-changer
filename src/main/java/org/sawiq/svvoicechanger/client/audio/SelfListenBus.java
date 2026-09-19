package org.sawiq.svvoicechanger.client.audio;

/**
 * Hand-off buffer between the Plasmo Voice capture thread and the self-listen
 * playback thread.
 *
 * <p>Exists so that monitoring plays back the very same audio the microphone
 * filter just produced, instead of opening a second capture line on whatever
 * the operating system considers the default device. Those are frequently not
 * the same microphone, which made the preview misleading.</p>
 *
 * <p>One producer (the capture thread) and one consumer (the playback thread).
 * The critical sections are a handful of array copies, so plain
 * synchronisation is cheaper here than the machinery a lock-free ring would
 * need, and it keeps the ordering obvious.</p>
 */
public final class SelfListenBus {
    /** Half a second at 48 kHz mono: enough to ride out a scheduling hiccup. */
    private static final int CAPACITY = 24_000;
    /**
     * If the consumer falls this far behind, playback has stalled and the
     * backlog is dropped rather than played late.
     */
    private static final int MAX_BACKLOG = 12_000;

    private final short[] buffer = new short[CAPACITY];
    private int writeIndex;
    private int readIndex;
    private int available;
    private volatile boolean active;
    private volatile long lastWriteNanos;

    /** Opens the bus. Until this is called, {@link #write} is a no-op. */
    public synchronized void open() {
        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;
        this.lastWriteNanos = System.nanoTime();
        this.active = true;
    }

    public synchronized void close() {
        this.active = false;
        this.available = 0;
    }

    public boolean isActive() {
        return this.active;
    }

    /** Nanoseconds since the capture thread last delivered audio. */
    public long nanosSinceLastWrite() {
        return System.nanoTime() - this.lastWriteNanos;
    }

    /**
     * Publishes one processed block. Called from the capture thread, so it
     * must never block for long and must never allocate.
     *
     * @param channels channel count of {@code samples}; only the first channel
     *                 is monitored, since the preview line is mono
     */
    public synchronized void write(short[] samples, int channels) {
        if (!this.active) {
            return;
        }

        this.lastWriteNanos = System.nanoTime();
        if (this.available > MAX_BACKLOG) {
            this.readIndex = this.writeIndex;
            this.available = 0;
        }

        int step = Math.max(1, channels);
        for (int i = 0; i < samples.length; i += step) {
            this.buffer[this.writeIndex] = samples[i];
            this.writeIndex = this.writeIndex + 1 >= CAPACITY ? 0 : this.writeIndex + 1;
            if (this.available < CAPACITY) {
                this.available++;
            } else {
                this.readIndex = this.readIndex + 1 >= CAPACITY ? 0 : this.readIndex + 1;
            }
        }
    }

    /**
     * Drains up to {@code destination.length} samples.
     *
     * @return how many samples were written into {@code destination}
     */
    public synchronized int read(short[] destination) {
        int count = Math.min(destination.length, this.available);
        for (int i = 0; i < count; i++) {
            destination[i] = this.buffer[this.readIndex];
            this.readIndex = this.readIndex + 1 >= CAPACITY ? 0 : this.readIndex + 1;
        }
        this.available -= count;
        return count;
    }
}
