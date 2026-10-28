package org.sawiq.svvoicechanger.client.ai.engine;

/**
 * A running conversion that turns microphone frames into AI voice frames while
 * the player is still talking.
 *
 * <p>{@link #push} and {@link #poll} are called from the capture thread every
 * 20 ms and must never block; the network round trip happens on the
 * converter's own thread.</p>
 */
public interface LiveConverter extends AutoCloseable {
    /** Queues one 20 ms frame. Drops the oldest queued frame when the engine falls behind. */
    void push(short[] frame);

    /** One converted 20 ms frame, or {@code null} when none is ready. */
    short[] poll();

    /** Whether nothing is waiting to be converted or taken. */
    boolean isIdle();

    /** Audio held back inside the engine, in frames, which is the delay players hear. */
    int chunkFrames();

    /** Why the converter stopped working, or {@code null} while it works. */
    String failure();

    /** Whether it failed often enough in a row that it stopped trying. */
    boolean hasGivenUp();

    /** The last request's round trip, for the studio's latency readout. */
    long lastRoundTripMillis();

    @Override
    void close();
}
