package org.sawiq.svvoicechanger.client.audio;

import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.model.ActiveVoice;

/**
 * Applies the voice changer to the microphone frames Simple Voice Chat is about
 * to send, and publishes the result for the studio's self-listen preview.
 *
 * <p>Frames arrive only while the microphone is transmitting, so the chain sees
 * a stream with gaps in it. Filter memory, delay lines and vocoder phase all
 * describe one continuous stream, and carrying them across a gap plays stale
 * audio back as a click, which is why the processor is reset whenever the
 * effect is switched on or the stream restarts.</p>
 */
public final class SimpleVoiceChatAudioProcessor {
    private static final long ACTIVE_STREAM_WINDOW_NANOS = 250_000_000L;
    /** Simple Voice Chat captures mono at 48 kHz, which is what the chain assumes. */
    private static final int CHANNELS = 1;

    private final SelfListenBus selfListenBus;
    private final VoiceProcessor processor = new VoiceProcessor();

    private boolean wasEnabled;
    private long lastProcessedAt;

    private volatile int lastBlockFrames;
    private volatile int blocksPerSecond;
    private int blocksThisSecond;
    private long secondStartedAt = System.currentTimeMillis();

    public SimpleVoiceChatAudioProcessor(SelfListenBus selfListenBus) {
        this.selfListenBus = selfListenBus;
    }

    /**
     * A processor for the microphone test, which is a separate stream with its
     * own filter state and is never monitored: the studio preview follows the
     * voice you are actually sending, not the settings screen's test tone.
     */
    public static SimpleVoiceChatAudioProcessor forMicrophoneTest() {
        return new SimpleVoiceChatAudioProcessor(null);
    }

    public ProcessingResult process(short[] samples, VoiceChangerController controller) {
        if (samples == null || samples.length == 0) {
            return ProcessingResult.PASSTHROUGH;
        }

        countBlock(samples.length);
        controller.reportInputLevel(peakOf(samples));

        ActiveVoice voice = controller.getActiveVoice();
        if (!voice.active()) {
            boolean shouldStopActiveStream = this.wasEnabled
                    && System.nanoTime() - this.lastProcessedAt <= ACTIVE_STREAM_WINDOW_NANOS;
            if (this.wasEnabled) {
                reset();
            }

            // Still published, so turning the effect off while previewing keeps
            // playing the microphone rather than falling silent.
            publishForMonitoring(samples);
            return shouldStopActiveStream
                    ? ProcessingResult.STOP_ACTIVE_STREAM
                    : ProcessingResult.PASSTHROUGH;
        }

        if (!this.wasEnabled) {
            this.processor.reset();
            this.wasEnabled = true;
        }

        this.processor.process(samples, CHANNELS, voice.profile(), voice.strength());
        this.lastProcessedAt = System.nanoTime();
        publishForMonitoring(samples);
        return ProcessingResult.PROCESSED;
    }

    /** What the chain is currently seeing and doing, for the studio to display. */
    public VoiceDiagnostics diagnostics() {
        return new VoiceDiagnostics(
                this.processor.speakerPitchHz(),
                this.processor.appliedPitchRatio(),
                this.lastBlockFrames,
                CHANNELS,
                this.blocksPerSecond);
    }

    public void reset() {
        this.processor.reset();
        this.wasEnabled = false;
        this.lastProcessedAt = 0L;
    }

    private void publishForMonitoring(short[] samples) {
        if (this.selfListenBus != null && this.selfListenBus.isActive()) {
            this.selfListenBus.write(samples, CHANNELS);
        }
    }

    private void countBlock(int frames) {
        this.lastBlockFrames = frames;
        this.blocksThisSecond++;

        long now = System.currentTimeMillis();
        if (now - this.secondStartedAt >= 1_000L) {
            this.blocksPerSecond = this.blocksThisSecond;
            this.blocksThisSecond = 0;
            this.secondStartedAt = now;
        }
    }

    private static double peakOf(short[] samples) {
        int peak = 0;
        for (short sample : samples) {
            int magnitude = Math.abs(sample);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        return peak / 32_768.0D;
    }

    public enum ProcessingResult {
        PASSTHROUGH,
        PROCESSED,
        STOP_ACTIVE_STREAM
    }
}
