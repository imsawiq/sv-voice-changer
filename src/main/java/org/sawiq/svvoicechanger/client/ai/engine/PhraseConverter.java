package org.sawiq.svvoicechanger.client.ai.engine;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Converts one finished phrase at a time, for engines that cannot start
 * answering before the phrase is complete.
 */
public interface PhraseConverter extends AutoCloseable {
    /**
     * Converts a phrase and hands the result over in 20 ms frames as it
     * arrives, so playback can begin before the whole answer is in.
     *
     * <p>Blocks for the whole round trip; called on the phrase worker thread.</p>
     *
     * @param phrase mono 48 kHz samples
     */
    void convert(short[] phrase, Consumer<short[]> frames) throws IOException, InterruptedException;

    @Override
    void close();
}
