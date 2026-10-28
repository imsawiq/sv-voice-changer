package org.sawiq.svvoicechanger.client.ai.audio;

import java.util.function.Consumer;

/**
 * Cuts audio that arrives in arbitrary lengths into whole 20 ms frames.
 *
 * <p>Engines answer in their own chunk sizes and a streamed HTTP response in
 * whatever the network delivers, while the voice mods take exactly one frame
 * per capture tick. Whatever does not fill a frame yet is kept for the next
 * call; {@link #flush()} pads the remainder with silence at the very end.</p>
 *
 * <p>Not thread safe: one splitter belongs to one producer.</p>
 */
public final class FrameSplitter {
    private final Consumer<short[]> sink;
    private short[] pending = new short[AudioFrames.FRAME_SAMPLES];
    private int filled;

    public FrameSplitter(Consumer<short[]> sink) {
        this.sink = sink;
    }

    public void accept(short[] samples) {
        accept(samples, 0, samples.length);
    }

    public void accept(short[] samples, int offset, int length) {
        int position = offset;
        int end = offset + length;
        while (position < end) {
            int count = Math.min(end - position, AudioFrames.FRAME_SAMPLES - this.filled);
            System.arraycopy(samples, position, this.pending, this.filled, count);
            this.filled += count;
            position += count;

            if (this.filled == AudioFrames.FRAME_SAMPLES) {
                this.sink.accept(this.pending);
                this.pending = new short[AudioFrames.FRAME_SAMPLES];
                this.filled = 0;
            }
        }
    }

    /** Emits the partly filled frame, padded with silence, if there is one. */
    public void flush() {
        if (this.filled > 0) {
            this.sink.accept(this.pending);
            this.pending = new short[AudioFrames.FRAME_SAMPLES];
            this.filled = 0;
        }
    }
}
