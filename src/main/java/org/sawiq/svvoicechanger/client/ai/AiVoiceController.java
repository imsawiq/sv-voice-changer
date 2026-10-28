package org.sawiq.svvoicechanger.client.ai;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ai.audio.PreviewPlayer;
import org.sawiq.svvoicechanger.client.ai.engine.AiVoiceEngine;
import org.sawiq.svvoicechanger.client.ai.engine.AiVoiceEngine.EngineSetup;
import org.sawiq.svvoicechanger.client.ai.engine.EngineAdvisor;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus;
import org.sawiq.svvoicechanger.client.ai.engine.elevenlabs.ElevenLabsEngine;
import org.sawiq.svvoicechanger.client.ai.engine.local.DatasetReport;
import org.sawiq.svvoicechanger.client.ai.engine.local.TrainingStatus;
import org.sawiq.svvoicechanger.client.ai.engine.local.VoiceEngine;
import org.sawiq.svvoicechanger.client.ai.engine.wokada.WOkadaEngine;
import org.sawiq.svvoicechanger.client.ai.install.EngineInstaller;
import org.sawiq.svvoicechanger.client.ai.install.EngineProcess;
import org.sawiq.svvoicechanger.client.ai.runtime.AiVoiceRuntime;
import org.sawiq.svvoicechanger.client.ai.runtime.AiVoiceStream;

/**
 * Owns the AI voice on the client: the settings, what each engine last said
 * about itself, which engine is in use, and the stream the capture thread
 * talks to.
 *
 * <p>Called from the client thread. Anything that touches the network runs on
 * a background executor, and every result comes back through volatile
 * snapshots, so the studio can redraw every frame without waiting on
 * anything.</p>
 */
public final class AiVoiceController {
    /** Slider drags fire a change per pixel; the stream is rebuilt once they settle. */
    private static final long RESTART_DELAY_MILLIS = 350L;
    /** Local engines answer in milliseconds, so asking this often costs nothing. */
    private static final long IDLE_PROBE_INTERVAL_MILLIS = 8_000L;
    private static final long GAVE_UP_COOLDOWN_MILLIS = 30_000L;
    /**
     * How long the installed engine stays up after the last thing that needed
     * it: loading it onto the GPU takes a while, and a player who leaves a
     * server or closes the studio often comes straight back.
     */
    private static final long ENGINE_IDLE_STOP_MILLIS = 3 * 60_000L;
    /** A run shorter than this did not really start, so starting it again at once would only loop. */
    private static final long ENGINE_QUICK_EXIT_MILLIS = 60_000L;
    private static final long ENGINE_RETRY_BASE_MILLIS = 10_000L;
    private static final int ENGINE_MAX_QUICK_EXITS = 3;
    private static final long TRAINING_POLL_MILLIS = 1_000L;

    private final AiVoiceSettingsStore store;
    private final AiVoiceRuntime runtime;
    private final ElevenLabsEngine elevenLabs = new ElevenLabsEngine();
    private final VoiceEngine voiceEngine = new VoiceEngine();
    private final Map<EngineKind, AiVoiceEngine> engines = new EnumMap<>(Map.of(
            EngineKind.VOICE_ENGINE, this.voiceEngine,
            EngineKind.W_OKADA, new WOkadaEngine(),
            EngineKind.ELEVENLABS, this.elevenLabs));

    /** Probes run side by side, so a slow ElevenLabs answer does not hold up the local engines. */
    private final ExecutorService probes = Executors.newFixedThreadPool(EngineKind.values().length,
            daemonThreads("Voice changer AI probe"));
    /** Opening and closing streams happens strictly in order. */
    private final ScheduledExecutorService lifecycle = Executors.newSingleThreadScheduledExecutor(
            daemonThreads("Voice changer AI"));
    private final AtomicInteger restartGeneration = new AtomicInteger();
    private final Map<EngineKind, Long> gaveUpAt = new ConcurrentHashMap<>();

    private volatile AiVoiceSettings settings = AiVoiceSettings.defaults();
    private volatile String elevenLabsKey = "";
    private volatile Map<EngineKind, EngineStatus> statuses = checkingAll();
    private volatile boolean wanted;
    private volatile EngineKind runningKind;
    /** What the running stream was built from; only touched on the lifecycle executor. */
    private volatile EngineSetup runningSetup;
    private final AiVoiceRuntime preview = new AiVoiceRuntime();
    private final EngineInstaller installer = new EngineInstaller();
    private final EngineProcess engineProcess = new EngineProcess();
    private boolean engineStartFailureLogged;
    private long engineNeededAt;
    private int engineQuickExits;
    private long engineRetryAt;
    /** Why the installed engine keeps exiting, once it has given up retrying; otherwise null. */
    private volatile String engineCrash;
    /** Set while one of the AI screens is open: the gallery, previews and training need the engine. */
    private volatile boolean studioOpen;
    private volatile TrainingStatus training = TrainingStatus.IDLE;
    private volatile String finishedTraining;
    private long trainingPolledAt;
    private volatile boolean previewWanted;
    private EngineSetup previewSetup;
    private volatile String startFailure;
    private volatile long lastProbeAt;
    private boolean saveFailureLogged;

    /**
     * @param runtime created before this, because the capture thread holds on
     *                to it from the moment the voice mod starts capturing
     */
    public AiVoiceController(AiVoiceSettingsStore store, AiVoiceRuntime runtime) {
        this.store = store;
        this.runtime = runtime;
    }

    public void load() {
        try {
            this.settings = this.store.load();
            this.elevenLabsKey = this.store.loadKey();
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not read the AI voice settings; starting from defaults", exception);
        }
    }

    public void shutdown() {
        this.wanted = false;
        this.runtime.stop();
        this.preview.stop();
        this.installer.cancel();
        this.engineProcess.stop();
        this.probes.shutdownNow();
        this.lifecycle.shutdownNow();
    }

    /**
     * Called every client tick. Keeps the AI voice going without the player
     * having to press Refresh: an engine that stopped answering is dropped
     * and the engines are asked again, and while the AI voice is wanted but
     * nothing is running they are asked every few seconds, so starting
     * w-okada after the game is enough.
     */
    public void tick() {
        manageInstalledEngine();
        pollTraining();
        if (this.runtime.hasGivenUp()) {
            SvVoiceChanger.LOGGER.warn("The AI voice engine stopped answering; looking for another");
            EngineKind failed = this.runningKind;
            if (failed != null) {
                this.gaveUpAt.put(failed, System.currentTimeMillis());
            }
            // Stopped here and now, so the next tick does not see it again;
            // the rest of the bookkeeping belongs to the lifecycle thread.
            this.runtime.stop();
            submitLifecycle(this::stopStream);
            submitLifecycle(this::syncPreview);
            refresh();
            return;
        }

        // Also while an AI screen is open on an engine that is still starting:
        // the training page and the status line should move on by themselves.
        boolean waitingForEngine = this.wanted && this.settings.useAi() && !this.runtime.isActive()
                || this.studioOpen && status(EngineKind.VOICE_ENGINE).availability() == EngineStatus.Availability.STARTING;
        if (waitingForEngine && System.currentTimeMillis() - this.lastProbeAt > IDLE_PROBE_INTERVAL_MILLIS) {
            refresh();
        }
    }

    private void submitLifecycle(Runnable task) {
        try {
            this.lifecycle.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Shutting down.
        }
    }

    // --- Whether the AI voice should be running ----------------------------

    /**
     * Set by the voice changer whenever its own switches, the server's policy
     * or the player's choice of voice source change.
     */
    public void setWanted(boolean wanted) {
        if (this.wanted == wanted) {
            return;
        }
        this.wanted = wanted;
        scheduleRestart(0L);
    }

    /** Whether the player chose the AI voice over the effect presets. */
    public boolean isSelected() {
        return this.settings.useAi();
    }

    /** The engine whose voice goes to the chat right now, or {@code null}. */
    public EngineKind runningEngine() {
        return this.runtime.isActive() ? this.runningKind : null;
    }

    /** What "auto" or the pinned choice resolves to, whether or not it is ready. */
    public EngineKind targetEngine() {
        EngineKind pinned = this.settings.engine().kind();
        return pinned != null ? pinned : EngineAdvisor.closestToReady(this.statuses);
    }

    /** Why the last attempt to start a stream failed, or {@code null}. */
    public String startFailure() {
        return this.startFailure;
    }

    public AiVoiceStream.Activity activity() {
        return this.runtime.activity();
    }

    public AiVoiceRuntime.LiveTiming liveTiming() {
        return this.runtime.liveTiming();
    }

    /** The newest stream failure for the action bar, or {@code null}. */
    public String takeFailure() {
        return this.runtime.takeFailure();
    }

    // --- Settings ----------------------------------------------------------

    public AiVoiceSettings settings() {
        return this.settings;
    }

    /**
     * Applies a change, saves it and rebuilds the stream once the player stops
     * fiddling. A change to which engine is used re-probes, since "auto" may
     * now resolve differently.
     */
    public void update(UnaryOperator<AiVoiceSettings> change) {
        AiVoiceSettings previous = this.settings;
        AiVoiceSettings next = change.apply(previous);
        if (next.equals(previous)) {
            return;
        }

        this.settings = next;
        save(next);
        scheduleRestart(RESTART_DELAY_MILLIS);

        // What each engine reports depends on these: where to reach it, and
        // for ElevenLabs whether there is a voice to speak with yet.
        if (!next.voiceEngine().address().equals(previous.voiceEngine().address())
                || !next.wOkada().address().equals(previous.wOkada().address())
                || !next.elevenLabs().voiceId().equals(previous.elevenLabs().voiceId())) {
            refresh();
        }
    }

    public boolean hasElevenLabsKey() {
        return !this.elevenLabsKey.isBlank();
    }

    /** The last four characters, enough to tell two keys apart and useless to anyone else. */
    public String maskedElevenLabsKey() {
        String key = this.elevenLabsKey;
        return key.length() <= 4 ? "****" : "****" + key.substring(key.length() - 4);
    }

    /** @return false when the text holds nothing that could be a key */
    public boolean setElevenLabsKey(String raw) {
        String key = AiVoiceSettingsStore.sanitizeKey(raw);
        if (key.isEmpty()) {
            return false;
        }
        storeKey(key);
        return true;
    }

    public void clearElevenLabsKey() {
        storeKey("");
    }

    private void storeKey(String key) {
        this.elevenLabsKey = key;
        try {
            this.store.saveKey(key);
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Could not save the ElevenLabs key", exception);
        }
        refresh();
        scheduleRestart(0L);
    }

    // --- Engine status -----------------------------------------------------

    /**
     * An engine the mod started itself reads "starting" until it answers,
     * rather than "not running": the first start downloads its support models
     * and can take minutes.
     */
    public EngineStatus status(EngineKind kind) {
        EngineStatus status = this.statuses.getOrDefault(kind, EngineStatus.checking(kind));
        if (kind != EngineKind.VOICE_ENGINE || status.availability() != EngineStatus.Availability.NOT_RUNNING) {
            return status;
        }
        if (this.engineProcess.isRunning()) {
            return EngineStatus.of(kind, EngineStatus.Availability.STARTING, "");
        }
        String crash = this.engineCrash;
        return crash != null ? EngineStatus.of(kind, EngineStatus.Availability.FAILED, crash) : status;
    }

    // --- Voice gallery and recordings -----------------------------------------

    /** Whether the gallery can play a sample of this engine's voices. */
    public boolean canPreview(EngineKind kind) {
        return kind == EngineKind.VOICE_ENGINE && status(kind).isReady() && this.voiceEngine.supports("preview");
    }

    /** Plays a few seconds of a voice to the player; quietly does nothing if the engine cannot. */
    public void previewVoice(EngineKind kind, String modelId) {
        if (!canPreview(kind)) {
            return;
        }
        EngineSetup setup = setup();
        int pitch = this.settings.voiceEngine().pitch();
        submitProbe(() -> {
            try {
                PreviewPlayer.play(this.voiceEngine.preview(setup, modelId, pitch));
            } catch (IOException exception) {
                SvVoiceChanger.LOGGER.info("Voice sample not available: {}", exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public boolean canCheckRecordings() {
        return status(EngineKind.VOICE_ENGINE).isReady() && this.voiceEngine.supports("dataset_check");
    }

    /**
     * Has the engine judge a folder of recordings. The answer arrives on a
     * background thread; {@code null} means the engine could not be asked.
     */
    public void checkRecordings(Path folder, Consumer<DatasetReport> answer) {
        EngineSetup setup = setup();
        submitProbe(() -> {
            try {
                answer.accept(this.voiceEngine.checkDataset(setup, folder));
            } catch (IOException exception) {
                SvVoiceChanger.LOGGER.info("Recordings check failed: {}", exception.getMessage());
                answer.accept(null);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void submitProbe(Runnable task) {
        try {
            this.probes.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Shutting down.
        }
    }

    // --- The installed engine ------------------------------------------------

    public EngineInstaller installer() {
        return this.installer;
    }

    /** Stops the engine and deletes everything that was installed. */
    public void uninstallEngine() {
        submitLifecycle(() -> {
            this.engineProcess.stop();
            try {
                this.installer.uninstall();
            } catch (IOException exception) {
                SvVoiceChanger.LOGGER.warn("Could not remove the voice changer engine", exception);
            }
            refresh();
        });
    }

    /**
     * Runs the installed engine in the background while something could use
     * it: the AI voice, an open AI screen, or a voice being trained. The
     * player never starts or stops it by hand.
     *
     * <ul>
     *     <li>It stays up a few minutes after it was last needed, so leaving
     *     a server or closing the studio does not mean another long load.</li>
     *     <li>It is not started when an engine already answers on its port:
     *     another game instance or the player's own copy is serving it.</li>
     *     <li>One that exits within a minute of starting is retried a few
     *     times, further and further apart, and then left alone with its last
     *     words shown as the reason, until the player asks again.</li>
     * </ul>
     */
    private void manageInstalledEngine() {
        EngineProcess.Exit exit = this.engineProcess.takeUnexpectedExit();
        if (exit != null) {
            onEngineExit(exit);
        }

        Optional<Integer> port = managedEnginePort();
        long now = System.currentTimeMillis();
        boolean needed = port.isPresent()
                && (this.wanted && this.settings.useAi() || this.studioOpen || this.training.isRunning());
        if (needed) {
            this.engineNeededAt = now;
        }

        if (needed && !this.engineProcess.isRunning() && now >= this.engineRetryAt && nobodyServesEngine()) {
            startEngine(port.get());
        } else if (this.engineProcess.isRunning()
                && (port.isEmpty() || now - this.engineNeededAt > ENGINE_IDLE_STOP_MILLIS)) {
            this.engineProcess.stop();
        }
    }

    /** The port to run the installed engine on, when there is one and the settings point at it. */
    private Optional<Integer> managedEnginePort() {
        AiVoiceSettings current = this.settings;
        EngineKind pinned = current.engine().kind();
        if (!this.installer.isInstalled() || pinned != null && pinned != EngineKind.VOICE_ENGINE) {
            return Optional.empty();
        }
        return LocalAddress.parse(current.voiceEngine().address())
                .filter(address -> address.host().equals("127.0.0.1") || address.host().equalsIgnoreCase("localhost"))
                .map(LocalAddress::port);
    }

    /** Only a definite "nothing there" counts: while the first probe is out, an engine may yet answer. */
    private boolean nobodyServesEngine() {
        EngineStatus raw = this.statuses.get(EngineKind.VOICE_ENGINE);
        return raw != null && raw.availability() == EngineStatus.Availability.NOT_RUNNING;
    }

    private void startEngine(int port) {
        try {
            this.engineProcess.start(port);
            this.lastProbeAt = 0L;
            this.engineStartFailureLogged = false;
        } catch (IOException exception) {
            if (!this.engineStartFailureLogged) {
                this.engineStartFailureLogged = true;
                SvVoiceChanger.LOGGER.warn("Could not start the installed voice changer engine", exception);
            }
            this.engineRetryAt = System.currentTimeMillis() + ENGINE_RETRY_BASE_MILLIS;
        }
    }

    private void onEngineExit(EngineProcess.Exit exit) {
        SvVoiceChanger.LOGGER.warn("The voice changer engine exited with code {} after {} s: {}",
                exit.exitCode(), exit.uptimeMillis() / 1000L, exit.lastLogLine());
        long now = System.currentTimeMillis();
        if (exit.uptimeMillis() >= ENGINE_QUICK_EXIT_MILLIS) {
            // It ran for a while and then died: start it again straight away.
            this.engineQuickExits = 0;
            this.engineRetryAt = now;
        } else if (++this.engineQuickExits >= ENGINE_MAX_QUICK_EXITS) {
            this.engineRetryAt = Long.MAX_VALUE;
            this.engineCrash = exit.lastLogLine().isBlank() ? "exit code " + exit.exitCode() : exit.lastLogLine();
        } else {
            this.engineRetryAt = now + ENGINE_RETRY_BASE_MILLIS * (1L << (this.engineQuickExits - 1) * 2);
        }
        refresh();
    }

    /** Lets an engine that gave up on starting try again, when the player asks. */
    public void retryEngine() {
        this.engineQuickExits = 0;
        this.engineRetryAt = 0L;
        this.engineCrash = null;
        refresh();
    }

    /** Set while one of the AI screens is open, so the engine is there for previews and training. */
    public void setStudioOpen(boolean open) {
        this.studioOpen = open;
    }

    // --- Training a voice ----------------------------------------------------

    /** Whether the engine can train voices: running, and on a graphics card with enough memory. */
    public boolean canTrain() {
        EngineStatus.Availability availability = status(EngineKind.VOICE_ENGINE).availability();
        return (availability == EngineStatus.Availability.READY || availability == EngineStatus.Availability.NO_MODEL)
                && this.voiceEngine.supports("train");
    }

    /** The engine's training job as last asked; idle when there is none or the engine is not there. */
    public TrainingStatus training() {
        return this.training;
    }

    /** The name of a voice that has just finished training, once; otherwise null. */
    public String takeFinishedTraining() {
        String name = this.finishedTraining;
        this.finishedTraining = null;
        return name;
    }

    /**
     * Starts training. The answer arrives on a background thread: null when
     * the engine took the job, otherwise its reason for refusing.
     */
    public void startTraining(String name, Path folder, int epochs, boolean resume, Consumer<String> answer) {
        EngineSetup setup = setup();
        submitProbe(() -> {
            try {
                this.voiceEngine.startTraining(setup, name, folder, epochs, resume);
                this.training = this.voiceEngine.trainingStatus(setup);
                answer.accept(null);
            } catch (IOException exception) {
                answer.accept(exception.getMessage() == null ? "" : exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void cancelTraining() {
        EngineSetup setup = setup();
        submitProbe(() -> {
            try {
                this.voiceEngine.cancelTraining(setup);
            } catch (IOException exception) {
                SvVoiceChanger.LOGGER.info("Could not cancel training: {}", exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Follows the job once a second while it runs or a screen shows it. A
     * job that finishes brings its new voice into the gallery at once.
     */
    private void pollTraining() {
        long now = System.currentTimeMillis();
        boolean watched = this.training.isRunning() || this.studioOpen && canTrain();
        if (!watched || now - this.trainingPolledAt < TRAINING_POLL_MILLIS) {
            return;
        }
        this.trainingPolledAt = now;
        EngineSetup setup = setup();
        submitProbe(() -> {
            TrainingStatus previous = this.training;
            TrainingStatus next;
            try {
                next = this.voiceEngine.trainingStatus(setup);
            } catch (IOException exception) {
                next = previous.isRunning() ? previous : TrainingStatus.IDLE;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            this.training = next;
            if (previous.isRunning() && next.isDone()) {
                this.finishedTraining = next.name();
                refresh();
            }
        });
    }

    /** Voice-changing models ElevenLabs offers, for the model switch. */
    public List<EngineModel> elevenLabsModels() {
        return this.elevenLabs.voiceChangerModels();
    }

    /**
     * Asks every engine again. Results arrive one by one as each answers.
     *
     * <p>Each engine keeps its previous status until its new answer is in.
     * Resetting them all to "checking" first would let "auto" see only
     * whichever engine answers fastest and jump to it for a moment, cutting
     * off the one the player was speaking through.</p>
     */
    public void refresh() {
        this.lastProbeAt = System.currentTimeMillis();
        EngineSetup setup = setup();
        for (AiVoiceEngine engine : this.engines.values()) {
            try {
                this.probes.execute(() -> {
                    putStatus(engine.probe(setup));
                    // A newly ready engine may be what "auto" was waiting for.
                    scheduleRestart(0L);
                });
            } catch (RejectedExecutionException ignored) {
                return;
            }
        }
    }

    private synchronized void putStatus(EngineStatus status) {
        Map<EngineKind, EngineStatus> next = new EnumMap<>(this.statuses);
        next.put(status.kind(), status);
        this.statuses = next;
    }

    // --- Stream lifecycle --------------------------------------------------

    /**
     * Rebuilds the stream after a delay, dropping any rebuild scheduled
     * earlier: only the newest settings matter.
     */
    private void scheduleRestart(long delayMillis) {
        int generation = this.restartGeneration.incrementAndGet();
        try {
            this.lifecycle.schedule(() -> {
                if (generation == this.restartGeneration.get()) {
                    restartNow();
                    syncPreview();
                }
            }, delayMillis, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            // Shutting down.
        }
    }

    /**
     * Runs on the lifecycle executor. Leaves a running stream alone when
     * nothing it was built from has changed: probes finish all the time, and
     * rebuilding on each would cut the player off mid-word.
     */
    private void restartNow() {
        AiVoiceSettings current = this.settings;
        EngineKind kind = current.engine().kind() != null
                ? current.engine().kind()
                : EngineAdvisor.pickReady(autoCandidates());

        // A pinned engine that is not ready is not tried: its status line
        // already says what is missing, and a start that is bound to fail
        // only fills the log with the same reason.
        if (!this.wanted || !current.useAi() || kind == null || !status(kind).isReady()) {
            stopStream();
            return;
        }

        EngineSetup setup = new EngineSetup(current, this.elevenLabsKey);
        if (this.runtime.isActive() && kind == this.runningKind && setup.equals(this.runningSetup)) {
            return;
        }

        AiVoiceEngine engine = this.engines.get(kind);
        try {
            if (engine instanceof AiVoiceEngine.Live live) {
                this.runtime.startLive(live.openLive(setup));
            } else if (engine instanceof AiVoiceEngine.Phrase phrase) {
                this.runtime.startPhrases(phrase.openPhrase(setup));
            }
            this.runningKind = kind;
            this.runningSetup = setup;
            this.startFailure = null;
        } catch (IOException | RuntimeException exception) {
            stopStream();
            this.startFailure = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            SvVoiceChanger.LOGGER.warn("Could not start the AI voice with {}: {}", kind.id(), this.startFailure);
        }
    }

    private void stopStream() {
        this.runtime.stop();
        this.runningKind = null;
        this.runningSetup = null;
    }

    /**
     * Keeps the preview stream next to the real one: same engine, same
     * settings, its own session. Runs on the lifecycle executor.
     */
    private void syncPreview() {
        EngineKind kind = this.runningKind;
        EngineSetup setup = this.runningSetup;
        if (!this.previewWanted || kind == null || setup == null) {
            this.preview.stop();
            this.previewSetup = null;
            return;
        }
        if (this.preview.isActive() && setup.equals(this.previewSetup)) {
            return;
        }

        try {
            AiVoiceEngine engine = this.engines.get(kind);
            if (engine instanceof AiVoiceEngine.Live live) {
                this.preview.startLive(live.openLive(setup));
            } else if (engine instanceof AiVoiceEngine.Phrase phrase) {
                this.preview.startPhrases(phrase.openPhrase(setup));
            }
            this.previewSetup = setup;
        } catch (IOException | RuntimeException exception) {
            this.preview.stop();
            this.previewSetup = null;
        }
    }

    // --- Studio preview ----------------------------------------------------

    /**
     * Self-listen in the studio. The real stream only hears the microphone
     * while the voice mod is sending, and in a menu nothing is sent, so the
     * preview runs a second stream of its own from the studio's microphone.
     */
    public void setPreviewWanted(boolean wanted) {
        if (this.previewWanted == wanted) {
            return;
        }
        this.previewWanted = wanted;
        submitLifecycle(this::syncPreview);
    }

    /** Whether the preview should play the AI voice rather than the effects. */
    public boolean isPreviewing() {
        return this.preview.isActive();
    }

    /**
     * One 20 ms microphone frame from the studio's own capture.
     *
     * @return the converted frame to play, or {@code null} for silence
     */
    public short[] previewFrame(short[] mono) {
        return this.preview.onPreviewFrame(mono);
    }

    /**
     * The engines "auto" may pick from. One that gave up a moment ago sits
     * out for a while: it may still answer its status while failing to
     * convert, and picking it again at once would loop.
     */
    private Map<EngineKind, EngineStatus> autoCandidates() {
        long now = System.currentTimeMillis();
        Map<EngineKind, EngineStatus> candidates = new EnumMap<>(this.statuses);
        this.gaveUpAt.forEach((kind, at) -> {
            if (now - at < GAVE_UP_COOLDOWN_MILLIS) {
                candidates.remove(kind);
            }
        });
        return candidates;
    }

    private EngineSetup setup() {
        return new EngineSetup(this.settings, this.elevenLabsKey);
    }

    private void save(AiVoiceSettings value) {
        try {
            this.store.save(value);
            this.saveFailureLogged = false;
        } catch (IOException exception) {
            if (!this.saveFailureLogged) {
                this.saveFailureLogged = true;
                SvVoiceChanger.LOGGER.warn("Could not save the AI voice settings", exception);
            }
        }
    }

    private static Map<EngineKind, EngineStatus> checkingAll() {
        Map<EngineKind, EngineStatus> all = new EnumMap<>(EngineKind.class);
        for (EngineKind kind : EngineKind.values()) {
            all.put(kind, EngineStatus.checking(kind));
        }
        return all;
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
