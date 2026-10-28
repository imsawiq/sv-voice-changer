package org.sawiq.svvoicechanger.client.ai.engine;

import java.util.Map;

/**
 * Decides which engine "auto" means right now.
 *
 * <p>The first ready engine in {@link EngineKind}'s order wins: free and live
 * before paid and delayed. When none is ready the advice is the engine that is
 * closest to working, so the studio can say what one step would get the
 * player going - start w-okada, pick a voice, paste a key.</p>
 */
public final class EngineAdvisor {
    private EngineAdvisor() {
    }

    /** @return the engine to use, or {@code null} when none is ready */
    public static EngineKind pickReady(Map<EngineKind, EngineStatus> statuses) {
        for (EngineKind kind : EngineKind.values()) {
            EngineStatus status = statuses.get(kind);
            if (status != null && status.isReady()) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The engine nearest to ready: one that is running but needs a model or
     * voice picked beats one that is installed nowhere.
     */
    public static EngineKind closestToReady(Map<EngineKind, EngineStatus> statuses) {
        EngineKind ready = pickReady(statuses);
        if (ready != null) {
            return ready;
        }

        for (EngineKind kind : EngineKind.values()) {
            EngineStatus status = statuses.get(kind);
            if (status != null && status.availability() == EngineStatus.Availability.NO_MODEL) {
                return kind;
            }
        }
        return null;
    }
}
