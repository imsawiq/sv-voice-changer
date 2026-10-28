package org.sawiq.svvoicechanger.client.ai.engine;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.audio.FrameSplitter;

/**
 * A live converter for engines that take audio a chunk at a time: collects
 * frames into chunks on the capture side, sends each chunk from its own
 * thread and cuts the answers back into frames.
 *
 * <p>Both local engines work this way. They keep the previous chunk as
 * context on their side, so consecutive chunks join without a seam.</p>
 */
public abstract class ChunkedLiveConverter implements LiveConverter {
    /** About two seconds. Past that the engine is not keeping up and old audio is worth less than new. */
    private static final int MAX_QUEUED_INPUT_FRAMES = 100;
    private static final int MAX_QUEUED_OUTPUT_FRAMES = 150;
    /** A few failures in a row means the engine is gone, not having a slow moment. */
    private static final int FAILURES_BEFORE_GIVING_UP = 3;

    private final ArrayBlockingQueue<short[]> input = new ArrayBlockingQueue<>(MAX_QUEUED_INPUT_FRAMES);
    private final ArrayBlockingQueue<short[]> output = new ArrayBlockingQueue<>(MAX_QUEUED_OUTPUT_FRAMES);
    private final int chunkFrames;
    private final Thread worker;

    private volatile boolean closed;
    private volatile boolean gaveUp;
    private volatile boolean converting;
    private volatile String failure;
    private volatile long lastRoundTripMillis;

    protected ChunkedLiveConverter(String threadName, int chunkMillis) {
        this.chunkFrames = AudioFrames.framesFor(chunkMillis);
        this.worker = new Thread(this::run, threadName);
        this.worker.setDaemon(true);
    }

    /** Starts the worker. Called once the subclass is fully constructed. */
    protected final void start() {
        this.worker.start();
    }

    /**
     * Converts one chunk. Blocking; the answer should be as long as the chunk,
     * and anything else is still played, only out of step by the difference.
     */
    protected abstract short[] convertChunk(short[] chunk) throws IOException, InterruptedException;

    @Override
    public void push(short[] frame) {
        short[] copy = frame.clone();
        if (!this.input.offer(copy)) {
            this.input.poll();
            this.input.offer(copy);
        }
    }

    @Override
    public short[] poll() {
        return this.output.poll();
    }

    @Override
    public boolean isIdle() {
        return this.input.isEmpty() && this.output.isEmpty() && !this.converting;
    }

    @Override
    public int chunkFrames() {
        return this.chunkFrames;
    }

    @Override
    public String failure() {
        return this.failure;
    }

    @Override
    public boolean hasGivenUp() {
        return this.gaveUp;
    }

    @Override
    public long lastRoundTripMillis() {
        return this.lastRoundTripMillis;
    }

    @Override
    public void close() {
        this.closed = true;
        this.worker.interrupt();
    }

    private void run() {
        short[] chunk = new short[this.chunkFrames * AudioFrames.FRAME_SAMPLES];
        int filled = 0;
        int failuresInARow = 0;
        FrameSplitter splitter = new FrameSplitter(this::offerOutput);

        while (!this.closed) {
            short[] frame;
            try {
                frame = this.input.poll(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                break;
            }
            if (frame == null) {
                continue;
            }

            System.arraycopy(frame, 0, chunk, filled * AudioFrames.FRAME_SAMPLES, AudioFrames.FRAME_SAMPLES);
            if (++filled < this.chunkFrames) {
                continue;
            }
            filled = 0;

            this.converting = true;
            try {
                long started = System.nanoTime();
                short[] converted = convertChunk(chunk.clone());
                this.lastRoundTripMillis = (System.nanoTime() - started) / 1_000_000L;
                splitter.accept(converted);
                failuresInARow = 0;
                this.failure = null;
            } catch (InterruptedException exception) {
                break;
            } catch (IOException | RuntimeException exception) {
                failuresInARow++;
                this.failure = describe(exception);
                if (failuresInARow == 1) {
                    SvVoiceChanger.LOGGER.warn("AI voice engine request failed: {}", this.failure);
                }
                if (failuresInARow >= FAILURES_BEFORE_GIVING_UP) {
                    this.gaveUp = true;
                    this.closed = true;
                }
            } finally {
                this.converting = false;
            }
        }
    }

    private void offerOutput(short[] frame) {
        if (!this.output.offer(frame)) {
            this.output.poll();
            this.output.offer(frame);
        }
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
