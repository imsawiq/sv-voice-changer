package org.sawiq.svvoicechanger.client.ai.runtime;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.engine.PhraseConverter;

/**
 * The stream for engines that convert finished phrases.
 *
 * <p>While the player talks nothing is sent: the phrase is recorded. When they
 * stop, it goes to the engine, and the converted phrase is played into the
 * chat as it streams back, after a short cushion against network hiccups.
 * Phrases are converted one at a time and played in the order they were
 * spoken; talking over one's own playback records the next phrase meanwhile.</p>
 *
 * <p>The real voice is never sent in this mode, not even when the engine
 * fails. Someone who switched to an AI voice to keep their own out of the
 * chat would not want a network error to give it away.</p>
 */
final class PhraseStream implements AiVoiceStream {
    /** Shorter than this is a tap on the key or a cough, not worth credits. */
    private static final int MIN_PHRASE_FRAMES = 15;
    /** A phrase longer than this is cut and sent, so a monologue does not wait until the end. */
    private static final int MAX_PHRASE_FRAMES = 1_000;
    private static final int MAX_PENDING_PHRASES = 3;
    private static final int CUSHION_FRAMES = 10;
    /** Bridging a stalled download with silence for longer than this sounds broken anyway. */
    private static final int MAX_BRIDGED_FRAMES = 100;
    /** Bounds memory if the capture side stops taking frames. */
    private static final int MAX_QUEUED_FRAMES = 15_000;

    /** Marks where one phrase ends in the playback queue. */
    private static final short[] END_OF_PHRASE = new short[0];

    private final PhraseConverter converter;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Voice changer AI phrases");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<short[]> playback = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedFrames = new AtomicInteger();
    private final AtomicInteger pendingPhrases = new AtomicInteger();

    private short[] recording = new short[AudioFrames.FRAME_SAMPLES * 50];
    private int recordedFrames;
    private boolean speaking;
    private int bridgedFrames;
    private volatile Activity activity = Activity.IDLE;
    private volatile String pendingFailure;

    PhraseStream(PhraseConverter converter) {
        this.converter = converter;
    }

    @Override
    public short[] onFrame(short[] raw) {
        if (raw != null) {
            record(raw);
        } else if (this.recordedFrames > 0) {
            submitPhrase();
        }

        short[] frame = nextPlaybackFrame();
        this.activity = frame != null ? Activity.SPEAKING
                : raw != null ? Activity.RECORDING
                : this.pendingPhrases.get() > 0 ? Activity.CONVERTING
                : Activity.IDLE;
        return frame;
    }

    private void record(short[] raw) {
        int needed = (this.recordedFrames + 1) * AudioFrames.FRAME_SAMPLES;
        if (needed > this.recording.length) {
            short[] grown = new short[Math.min(this.recording.length * 2, MAX_PHRASE_FRAMES * AudioFrames.FRAME_SAMPLES)];
            System.arraycopy(this.recording, 0, grown, 0, this.recordedFrames * AudioFrames.FRAME_SAMPLES);
            this.recording = grown;
        }

        System.arraycopy(raw, 0, this.recording, this.recordedFrames * AudioFrames.FRAME_SAMPLES, AudioFrames.FRAME_SAMPLES);
        if (++this.recordedFrames >= MAX_PHRASE_FRAMES) {
            submitPhrase();
        }
    }

    private void submitPhrase() {
        int frames = this.recordedFrames;
        this.recordedFrames = 0;
        if (frames < MIN_PHRASE_FRAMES) {
            return;
        }
        if (this.pendingPhrases.get() >= MAX_PENDING_PHRASES) {
            this.pendingFailure = FAILURE_BUSY;
            return;
        }

        short[] phrase = new short[frames * AudioFrames.FRAME_SAMPLES];
        System.arraycopy(this.recording, 0, phrase, 0, phrase.length);

        this.pendingPhrases.incrementAndGet();
        try {
            this.worker.execute(() -> convert(phrase));
        } catch (RejectedExecutionException exception) {
            // Closed while the player was mid-phrase; nothing will play it.
            this.pendingPhrases.decrementAndGet();
        }
    }

    /** Runs on the worker thread. */
    private void convert(short[] phrase) {
        try {
            this.converter.convert(phrase, this::enqueue);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            this.pendingFailure = message;
            SvVoiceChanger.LOGGER.warn("AI voice phrase failed: {}", message);
        } finally {
            this.playback.add(END_OF_PHRASE);
            this.pendingPhrases.decrementAndGet();
        }
    }

    private void enqueue(short[] frame) {
        if (this.queuedFrames.get() < MAX_QUEUED_FRAMES) {
            this.playback.add(frame);
            this.queuedFrames.incrementAndGet();
        }
    }

    private short[] nextPlaybackFrame() {
        if (!this.speaking) {
            skipEmptyPhrases();
            if (!hasCushion()) {
                return null;
            }
            this.speaking = true;
            this.bridgedFrames = 0;
        }

        short[] frame = this.playback.peek();
        if (frame == null) {
            // The phrase is still downloading slower than it plays.
            if (this.bridgedFrames++ < MAX_BRIDGED_FRAMES) {
                return AudioFrames.silence();
            }
            this.speaking = false;
            return null;
        }

        this.playback.poll();
        if (frame == END_OF_PHRASE) {
            this.speaking = false;
            return null;
        }
        this.queuedFrames.decrementAndGet();
        this.bridgedFrames = 0;
        return frame;
    }

    /** A phrase that failed or came back empty leaves only its end marker. */
    private void skipEmptyPhrases() {
        while (this.playback.peek() == END_OF_PHRASE) {
            this.playback.poll();
        }
    }

    /** Enough has arrived to start, or the whole phrase has and it is short. */
    private boolean hasCushion() {
        int seen = 0;
        for (short[] frame : this.playback) {
            if (frame == END_OF_PHRASE || ++seen >= CUSHION_FRAMES) {
                return seen > 0;
            }
        }
        return false;
    }

    @Override
    public Activity activity() {
        return this.activity;
    }

    @Override
    public String takeFailure() {
        String failure = this.pendingFailure;
        this.pendingFailure = null;
        return failure;
    }

    @Override
    public void close() {
        this.worker.shutdownNow();
        this.converter.close();
        this.playback.clear();
    }
}
