package org.sawiq.svvoicechanger.client.audio;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;

public final class VoiceChangerSelfListenPreview {
    private static final AudioFormat MONO_PCM_FORMAT = new AudioFormat(48_000.0F, 16, 1, true, false);

    private final BooleanSupplier enabledSupplier;
    private final BooleanSupplier previewActiveSupplier;
    private final BooleanSupplier effectEnabledSupplier;
    private final Supplier<VoiceChangerProfile> profileSupplier;
    private final IntSupplier strengthSupplier;
    private final Runnable disableCallback;

    private SourceDataLine outputLine;
    private TargetDataLine inputLine;
    private Thread previewThread;
    private volatile boolean running;

    public VoiceChangerSelfListenPreview(
            BooleanSupplier enabledSupplier,
            BooleanSupplier previewActiveSupplier,
            BooleanSupplier effectEnabledSupplier,
            Supplier<VoiceChangerProfile> profileSupplier,
            IntSupplier strengthSupplier,
            Runnable disableCallback
    ) {
        this.enabledSupplier = enabledSupplier;
        this.previewActiveSupplier = previewActiveSupplier;
        this.effectEnabledSupplier = effectEnabledSupplier;
        this.profileSupplier = profileSupplier;
        this.strengthSupplier = strengthSupplier;
        this.disableCallback = disableCallback;
    }

    public void start() {
        if (!this.enabledSupplier.getAsBoolean() || !this.previewActiveSupplier.getAsBoolean() || this.running) {
            return;
        }

        try {
            this.inputLine = openInputLine();
            this.outputLine = openOutputLine();
            this.running = true;
            this.previewThread = new Thread(this::runPreviewLoop, "sv-voice-changer-preview");
            this.previewThread.setDaemon(true);
            this.previewThread.start();
        } catch (IllegalArgumentException | LineUnavailableException exception) {
            SvVoiceChanger.LOGGER.warn("Unable to start the self-listen preview", exception);
            this.disableCallback.run();
            stop();
        }
    }

    public void stop() {
        this.running = false;

        if (this.inputLine != null) {
            this.inputLine.stop();
            this.inputLine.flush();
            this.inputLine.close();
            this.inputLine = null;
        }

        if (this.previewThread != null && this.previewThread != Thread.currentThread()) {
            this.previewThread.interrupt();
        }
        this.previewThread = null;

        if (this.outputLine != null) {
            this.outputLine.stop();
            this.outputLine.flush();
            this.outputLine.close();
            this.outputLine = null;
        }
    }

    private void runPreviewLoop() {
        VoiceChangerAudioEngine.VoiceChangerState previewState = new VoiceChangerAudioEngine.VoiceChangerState();
        byte[] pcm = new byte[1920];
        short[] samples = new short[960];
        byte[] output = new byte[1920];

        try {
            while (this.running && this.enabledSupplier.getAsBoolean() && this.previewActiveSupplier.getAsBoolean()) {
                int read = this.inputLine.read(pcm, 0, pcm.length);
                if (read <= 0) {
                    continue;
                }

                int sampleCount = read / 2;
                if (samples.length != sampleCount) {
                    samples = new short[sampleCount];
                }
                int outputLength = sampleCount * 2;
                if (output.length < outputLength) {
                    output = new byte[outputLength];
                }

                for (int i = 0; i < sampleCount; i++) {
                    int lo = pcm[i * 2] & 0xFF;
                    int hi = pcm[i * 2 + 1] << 8;
                    samples[i] = (short) (hi | lo);
                }

                if (this.effectEnabledSupplier.getAsBoolean()) {
                    VoiceChangerAudioEngine.process(samples, 1, this.profileSupplier.get(), this.strengthSupplier.getAsInt(), previewState);
                }

                shortsToBytes(samples, output);
                this.outputLine.write(output, 0, outputLength);
            }
        } catch (Exception exception) {
            if (this.running) {
                SvVoiceChanger.LOGGER.warn("Self-listen preview stopped unexpectedly", exception);
            }
            this.disableCallback.run();
        } finally {
            this.running = false;
            stop();
        }
    }

    private static TargetDataLine openInputLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, MONO_PCM_FORMAT);
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(MONO_PCM_FORMAT, 4096);
        line.start();
        return line;
    }

    private static SourceDataLine openOutputLine() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, MONO_PCM_FORMAT);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(MONO_PCM_FORMAT, 4096);
        line.start();
        return line;
    }

    private static void shortsToBytes(short[] samples, byte[] bytes) {
        for (int i = 0; i < samples.length; i++) {
            short sample = samples[i];
            bytes[i * 2] = (byte) (sample & 0xFF);
            bytes[i * 2 + 1] = (byte) ((sample >>> 8) & 0xFF);
        }
    }
}

