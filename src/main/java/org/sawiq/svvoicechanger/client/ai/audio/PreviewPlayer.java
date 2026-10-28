package org.sawiq.svvoicechanger.client.ai.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays a voice sample from the gallery to the player alone.
 *
 * <p>One sample at a time: pressing play on another voice stops the one that
 * is playing, so clicking down the list does not stack voices on top of each
 * other.</p>
 */
public final class PreviewPlayer {
    private static final AudioFormat FORMAT = new AudioFormat(AudioFrames.SAMPLE_RATE, 16, 1, true, false);

    private static Thread playing;

    private PreviewPlayer() {
    }

    public static synchronized void play(short[] samples) {
        stop();
        Thread thread = new Thread(() -> write(samples), "Voice changer AI preview");
        thread.setDaemon(true);
        playing = thread;
        thread.start();
    }

    public static synchronized void stop() {
        if (playing != null) {
            playing.interrupt();
            playing = null;
        }
    }

    private static void write(short[] samples) {
        byte[] bytes = AudioFrames.toLittleEndian(samples);
        try {
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, FORMAT));
            line.open(FORMAT);
            line.start();
            int chunk = AudioFrames.FRAME_SAMPLES * 2 * 5;
            for (int offset = 0; offset < bytes.length && !Thread.currentThread().isInterrupted(); offset += chunk) {
                line.write(bytes, offset, Math.min(chunk, bytes.length - offset));
            }
            if (!Thread.currentThread().isInterrupted()) {
                line.drain();
            }
            line.stop();
            line.close();
        } catch (LineUnavailableException | IllegalArgumentException ignored) {
            // No output line: the gallery simply stays silent.
        }
    }
}
