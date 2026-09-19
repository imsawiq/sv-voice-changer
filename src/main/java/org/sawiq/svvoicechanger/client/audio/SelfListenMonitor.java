package org.sawiq.svvoicechanger.client.audio;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.sawiq.svvoicechanger.client.model.ActiveVoice;

/**
 * Plays your own voice back to you while the studio is open.
 *
 * <p>Simple Voice Chat hands the mod audio through its sound event, which only
 * fires while you are actually transmitting. Whenever that is happening the
 * monitor plays back what the processor produced, which is exactly what
 * listeners receive and must not be processed a second time. The rest of the
 * time it opens its own capture line and runs the chain itself, because a
 * preview that only works while you hold the talk key is not a preview.</p>
 *
 * <p>Unlike the previous version it respects the on/off switch: with the effect
 * disabled you hear your plain microphone rather than a processed voice.</p>
 */
public final class SelfListenMonitor {
    private static final AudioFormat MONO_48K = new AudioFormat(48_000f, 16, 1, true, false);
    private static final int LINE_BUFFER_BYTES = 8_192;
    private static final int BLOCK_SAMPLES = 960;
    private static final long IDLE_SLEEP_MILLIS = 2L;
    /** How long the shared stream may stay silent before the monitor captures itself. */
    private static final long SHARED_STREAM_TIMEOUT_MILLIS = 400L;

    /** Where the monitored audio is coming from. */
    public enum Mode {
        STOPPED,
        /** Playing back what the voice chat processor produced. */
        SHARED_STREAM,
        /** Capturing separately, because the voice chat is not transmitting. */
        OWN_CAPTURE,
        /** No capture could be opened at all. */
        UNAVAILABLE
    }

    private final SelfListenBus bus;
    private final Supplier<ActiveVoice> activeVoice;
    private final Consumer<Double> levelReporter;
    private final Consumer<Exception> failureHandler;
    private final VoiceProcessor processor = new VoiceProcessor();

    private volatile int lastBlockFrames;
    private volatile int blocksPerSecond;
    private int blocksThisSecond;
    private long secondStartedAt = System.currentTimeMillis();

    private Thread playbackThread;
    private volatile boolean running;
    private volatile Mode mode = Mode.STOPPED;

    public SelfListenMonitor(
            SelfListenBus bus,
            Supplier<ActiveVoice> activeVoice,
            Consumer<Double> levelReporter,
            Consumer<Exception> failureHandler
    ) {
        this.bus = bus;
        this.activeVoice = activeVoice;
        this.levelReporter = levelReporter;
        this.failureHandler = failureHandler;
    }

    public Mode mode() {
        return this.mode;
    }

    /** What the preview chain is doing, for the studio to display. */
    public VoiceDiagnostics diagnostics() {
        if (!this.running) {
            return VoiceDiagnostics.IDLE;
        }

        return new VoiceDiagnostics(
                this.processor.speakerPitchHz(),
                this.processor.appliedPitchRatio(),
                this.lastBlockFrames,
                1,
                this.blocksPerSecond);
    }

    public synchronized void start() {
        if (this.running) {
            return;
        }

        this.running = true;
        this.bus.open();
        this.playbackThread = new Thread(this::runPlayback, "sv-voice-changer-self-listen");
        this.playbackThread.setDaemon(true);
        this.playbackThread.start();
    }

    public synchronized void stop() {
        this.running = false;
        this.bus.close();
        this.mode = Mode.STOPPED;
        this.blocksPerSecond = 0;
        this.playbackThread = null;
    }

    /**
     * Owns every line it opens and releases them on the way out, so a failure
     * anywhere cannot leak an audio device. Only this thread touches them.
     */
    private void runPlayback() {
        SourceDataLine output = null;
        TargetDataLine capture = null;

        try {
            output = openPlayback();
            short[] samples = new short[BLOCK_SAMPLES];
            byte[] captureBytes = new byte[BLOCK_SAMPLES * 2];
            byte[] playbackBytes = new byte[BLOCK_SAMPLES * 2];
            long lastSharedAudioAt = System.currentTimeMillis();

            while (this.running) {
                int fromShared = this.bus.read(samples);
                if (fromShared > 0) {
                    // Already processed by the voice chat path; play it as it is.
                    lastSharedAudioAt = System.currentTimeMillis();
                    this.mode = Mode.SHARED_STREAM;
                    capture = closeQuietly(capture);
                    countBlock(fromShared);
                    writeSamples(output, samples, fromShared, playbackBytes);
                    continue;
                }

                if (System.currentTimeMillis() - lastSharedAudioAt < SHARED_STREAM_TIMEOUT_MILLIS) {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                    continue;
                }

                if (capture == null) {
                    capture = openCapture();
                    this.mode = capture != null ? Mode.OWN_CAPTURE : Mode.UNAVAILABLE;
                    this.processor.reset();
                    if (capture == null) {
                        Thread.sleep(IDLE_SLEEP_MILLIS * 10L);
                        continue;
                    }
                }

                int captured = readCapture(capture, samples, captureBytes);
                if (captured <= 0) {
                    Thread.sleep(IDLE_SLEEP_MILLIS);
                    continue;
                }

                this.levelReporter.accept(peakOf(samples, captured));
                ActiveVoice voice = this.activeVoice.get();
                if (voice.active()) {
                    this.processor.process(samples, captured, 1, voice.profile(), voice.strength());
                }
                countBlock(captured);
                writeSamples(output, samples, captured, playbackBytes);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            this.mode = Mode.UNAVAILABLE;
            this.failureHandler.accept(exception);
        } finally {
            closeQuietly(capture);
            closeQuietly(output);
            this.running = false;
            this.mode = Mode.STOPPED;
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

    private static int readCapture(TargetDataLine line, short[] samples, byte[] bytes) {
        int available = line.available();
        if (available < bytes.length) {
            return 0;
        }

        int read = line.read(bytes, 0, bytes.length);
        if (read <= 0) {
            return 0;
        }

        int count = read / 2;
        for (int i = 0; i < count; i++) {
            samples[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
        }
        return count;
    }

    private static double peakOf(short[] samples, int count) {
        int peak = 0;
        for (int i = 0; i < count; i++) {
            int magnitude = Math.abs(samples[i]);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        return peak / 32_768.0D;
    }

    private static void writeSamples(SourceDataLine output, short[] samples, int count, byte[] scratch) {
        for (int i = 0; i < count; i++) {
            scratch[i * 2] = (byte) (samples[i] & 0xFF);
            scratch[i * 2 + 1] = (byte) ((samples[i] >>> 8) & 0xFF);
        }
        output.write(scratch, 0, count * 2);
    }

    private static SourceDataLine openPlayback() throws LineUnavailableException {
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(
                new DataLine.Info(SourceDataLine.class, MONO_48K));
        line.open(MONO_48K, LINE_BUFFER_BYTES);
        line.start();
        return line;
    }

    private static TargetDataLine openCapture() {
        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(
                    new DataLine.Info(TargetDataLine.class, MONO_48K));
            line.open(MONO_48K, LINE_BUFFER_BYTES);
            line.start();
            return line;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TargetDataLine closeQuietly(TargetDataLine line) {
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
        return null;
    }

    private static void closeQuietly(SourceDataLine line) {
        if (line == null) {
            return;
        }
        line.stop();
        line.flush();
        line.close();
    }
}
