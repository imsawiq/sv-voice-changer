package org.sawiq.svvoicechanger.client.ai.runtime;

import java.util.ArrayDeque;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.engine.LiveConverter;

/**
 * The stream for engines that convert while the player talks.
 *
 * <p>The engine answers a chunk at a time, so converted frames arrive in
 * bursts and roughly one chunk late. Sending starts once a small cushion has
 * built up, a gap in the answers is bridged with silence rather than ending
 * the stream, and when the player stops the engine is fed silence for one
 * more chunk so the end of their last word comes out instead of staying stuck
 * in a half-filled chunk.</p>
 *
 * <p>Driven by the capture thread only, apart from what the converter does on
 * its own thread.</p>
 */
final class LiveStream implements AiVoiceStream {
    /** Two frames of cushion soak up round-trip jitter without being heard as delay. */
    private static final int CUSHION_FRAMES = 2;
    /** Waiting this long past the flush for the last answer, then the stream is over. */
    private static final int TAIL_GRACE_FRAMES = 15;
    /** Half a second of bridged silence; past that the engine is stuck, not slow. */
    private static final int MAX_BRIDGED_FRAMES = 25;

    private final LiveConverter converter;
    private final ArrayDeque<short[]> ready = new ArrayDeque<>();

    private boolean sending;
    private int cushion;
    private int bridgedFrames;
    private int flushFramesLeft;
    private int tailFramesLeft;
    private boolean failureReported;
    private volatile Activity activity = Activity.IDLE;
    private volatile String pendingFailure;

    LiveStream(LiveConverter converter) {
        this.converter = converter;
    }

    @Override
    public short[] onFrame(short[] raw) {
        feed(raw);
        collect();
        reportFailureOnce();

        short[] frame = next(raw != null);
        this.activity = frame != null ? Activity.SPEAKING : raw != null ? Activity.LISTENING : Activity.IDLE;
        return frame;
    }

    private void feed(short[] raw) {
        if (raw != null) {
            this.converter.push(raw);
            this.flushFramesLeft = this.converter.chunkFrames();
            this.tailFramesLeft = this.converter.chunkFrames() + TAIL_GRACE_FRAMES;
            return;
        }

        if (this.flushFramesLeft > 0) {
            this.converter.push(AudioFrames.silence());
            this.flushFramesLeft--;
        }
        if (this.tailFramesLeft > 0) {
            this.tailFramesLeft--;
        }
    }

    /**
     * Takes everything the engine has finished. Should the engine run ahead
     * of real time for a while, the oldest frames are dropped, so the delay
     * players hear cannot creep upward over a long conversation.
     */
    private void collect() {
        short[] frame;
        while ((frame = this.converter.poll()) != null) {
            this.ready.addLast(frame);
        }

        int limit = Math.max(4, this.converter.chunkFrames() * 3);
        while (this.ready.size() > limit) {
            this.ready.pollFirst();
        }
    }

    private short[] next(boolean talking) {
        if (!this.sending) {
            // What the engine still hands back once a stream has ended is the
            // silence fed in to flush it. Starting a stream for that would
            // light up the speaking icon for nothing.
            if (!talking && this.tailFramesLeft == 0) {
                this.ready.clear();
                this.cushion = 0;
                return null;
            }
            if (this.ready.isEmpty() || ++this.cushion < CUSHION_FRAMES) {
                return null;
            }
            this.sending = true;
            this.cushion = 0;
            this.bridgedFrames = 0;
        }

        short[] frame = this.ready.pollFirst();
        if (frame != null) {
            this.bridgedFrames = 0;
            return frame;
        }

        boolean moreExpected = talking || this.tailFramesLeft > 0 || !this.converter.isIdle();
        if (moreExpected && this.converter.failure() == null && this.bridgedFrames++ < MAX_BRIDGED_FRAMES) {
            return AudioFrames.silence();
        }

        this.sending = false;
        return null;
    }

    private void reportFailureOnce() {
        String failure = this.converter.failure();
        if (failure == null) {
            this.failureReported = false;
        } else if (!this.failureReported) {
            this.failureReported = true;
            this.pendingFailure = failure;
        }
    }

    @Override
    public Activity activity() {
        return this.activity;
    }

    @Override
    public String takeFailure() {
        String failure = this.pendingFailure;
        this.pendingFailure = null;
        return failure;
    }

    @Override
    public boolean hasGivenUp() {
        return this.converter.hasGivenUp();
    }

    /** Last round trip to the engine, for the studio. */
    long lastRoundTripMillis() {
        return this.converter.lastRoundTripMillis();
    }

    int chunkFrames() {
        return this.converter.chunkFrames();
    }

    @Override
    public void close() {
        this.converter.close();
    }
}
