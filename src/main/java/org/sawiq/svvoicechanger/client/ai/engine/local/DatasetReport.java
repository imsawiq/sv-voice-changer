package org.sawiq.svvoicechanger.client.ai.engine.local;

import java.util.List;

/**
 * What the engine thinks of a folder of recordings meant for training a voice.
 *
 * @param verdict "ok", "too_short" or "bad_quality"
 * @param tips    the engine's own advice, in English, shown as it is
 */
public record DatasetReport(int files, double minutes, int tooQuiet, int clipped, int noisy,
                            String verdict, List<String> tips) {
    public DatasetReport {
        verdict = verdict == null ? "" : verdict;
        tips = List.copyOf(tips);
    }
}
