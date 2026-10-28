package org.sawiq.svvoicechanger.client.ai.engine.local;

/**
 * Where the engine's one training job stands, as {@code GET /v1/train/status}
 * reports it.
 *
 * @param state   "idle", "running", "done", "failed" or "cancelled"
 * @param stage   "download", "preprocess", "features", "training", "export" or "index"
 * @param percent the whole job, all stages weighted, 0 to 100
 * @param etaSeconds negative while the engine cannot tell yet
 * @param lastLog the engine's newest log line, in English, for the curious
 * @param error   why it failed, or empty
 */
public record TrainingStatus(String state, String name, String stage, int stageIndex, int stageCount,
                             double percent, int epoch, int epochs, int etaSeconds,
                             String lastLog, String error) {
    public static final TrainingStatus IDLE = new TrainingStatus("idle", "", "", 0, 0, 0.0D, 0, 0, -1, "", "");

    public boolean isRunning() {
        return "running".equals(this.state);
    }

    public boolean isDone() {
        return "done".equals(this.state);
    }

    /** A job that stopped before the end, which can be picked up again from its last save. */
    public boolean canResume() {
        return "failed".equals(this.state) || "cancelled".equals(this.state);
    }
}
