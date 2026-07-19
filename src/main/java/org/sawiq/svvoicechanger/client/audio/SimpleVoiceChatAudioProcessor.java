package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;

public final class SimpleVoiceChatAudioProcessor {
    private static final long ACTIVE_STREAM_WINDOW_NANOS = 250_000_000L;

    private VoiceChangerAudioEngine.VoiceChangerState state =
            new VoiceChangerAudioEngine.VoiceChangerState();
    private boolean wasEnabled;
    private long lastProcessedAt;

    public ProcessingResult process(short[] samples, VoiceChangerController controller) {
        if (samples == null || samples.length == 0) {
            return ProcessingResult.PASSTHROUGH;
        }

        if (!controller.isEffectEnabled()) {
            boolean shouldStopActiveStream = this.wasEnabled
                    && System.nanoTime() - this.lastProcessedAt <= ACTIVE_STREAM_WINDOW_NANOS;
            if (this.wasEnabled) {
                reset();
            }
            return shouldStopActiveStream
                    ? ProcessingResult.STOP_ACTIVE_STREAM
                    : ProcessingResult.PASSTHROUGH;
        }

        if (!this.wasEnabled) {
            reset();
            this.wasEnabled = true;
        }

        VoiceChangerProfile profile = controller.getCurrentProfileSnapshot();
        int strength = controller.getStrength();
        VoiceChangerAudioEngine.process(samples, 1, profile, strength, this.state);
        this.lastProcessedAt = System.nanoTime();
        return ProcessingResult.PROCESSED;
    }

    public void reset() {
        this.state = new VoiceChangerAudioEngine.VoiceChangerState();
        this.wasEnabled = false;
        this.lastProcessedAt = 0L;
    }

    public enum ProcessingResult {
        PASSTHROUGH,
        PROCESSED,
        STOP_ACTIVE_STREAM
    }
}
