package org.sawiq.svvoicechanger.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.ai.AiVoiceController;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettings;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettingsStore;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.runtime.AiVoiceRuntime;
import org.sawiq.svvoicechanger.client.ai.runtime.AiVoiceStream;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.api.VoiceChangerApi;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.client.api.internal.VoiceChangerApiImpl;
import org.sawiq.svvoicechanger.client.api.internal.VoiceChangerApiRegistry;
import org.sawiq.svvoicechanger.client.api.internal.VoiceChangerApiSupport;
import org.sawiq.svvoicechanger.client.audio.SelfListenBus;
import org.sawiq.svvoicechanger.client.audio.SelfListenMonitor;
import org.sawiq.svvoicechanger.client.audio.SimpleVoiceChatAudioProcessor;
import org.sawiq.svvoicechanger.client.audio.VoiceDiagnostics;
import org.sawiq.svvoicechanger.client.model.ActiveVoice;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceChangerState;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;
import org.sawiq.svvoicechanger.client.preset.VoiceChangerPresetStore;
import org.sawiq.svvoicechanger.client.server.ServerVoiceSession;
import org.sawiq.svvoicechanger.client.ui.AiModelPickerScreen;
import org.sawiq.svvoicechanger.client.ui.AiTextScreen;
import org.sawiq.svvoicechanger.client.ui.AiTrainScreen;
import org.sawiq.svvoicechanger.client.ui.AiVoiceScreen;
import org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen;
import org.sawiq.svvoicechanger.protocol.PolicyReason;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * Client-side state of the voice changer: the current profile, the saved preset
 * library, the microphone processor and the studio's self-listen monitor.
 */
public final class VoiceChangerController {
    public static final VoiceChangerController INSTANCE = new VoiceChangerController();

    private final VoiceChangerApiSupport api = new VoiceChangerApiSupport();
    private final ServerVoiceSession serverSession = new ServerVoiceSession(this);
    private final SelfListenBus selfListenBus = new SelfListenBus();
    private final SimpleVoiceChatAudioProcessor audioProcessor =
            new SimpleVoiceChatAudioProcessor(this.selfListenBus);
    private final AiVoiceRuntime aiRuntime = new AiVoiceRuntime();
    /** Microphone hook calls, for {@link #micHookCalls()}. */
    private final AtomicLong micHookCalls = new AtomicLong();
    private final SelfListenMonitor selfListenMonitor = new SelfListenMonitor(
            this.selfListenBus,
            this::getActiveVoice,
            this::reportInputLevel,
            failure -> setSelfListenEnabled(false),
            new SelfListenMonitor.AiPreview() {
                @Override
                public boolean isActive() {
                    AiVoiceController ai = VoiceChangerController.this.aiVoice;
                    return ai != null && ai.isPreviewing();
                }

                @Override
                public short[] convert(short[] mono) {
                    return VoiceChangerController.this.aiVoice.previewFrame(mono);
                }
            }
    );
    private AiVoiceController aiVoice;
    private AiVoiceStream.Activity lastAiActivity = AiVoiceStream.Activity.IDLE;
    private volatile boolean aiAllowedByServer = true;

    private volatile boolean initialized;
    private volatile boolean effectEnabled;
    private volatile boolean selfListenEnabled;
    private volatile VoiceChangerPreset selectedPreset = VoiceChangerPreset.MAN;
    private volatile int strength = 100;
    private volatile VoiceProfile playerProfile = VoiceChangerPreset.MAN.profile();
    private volatile ActiveVoice activeVoice = ActiveVoice.INACTIVE;
    private volatile boolean allowedByServer = true;
    private volatile PolicyReason serverDenialReason = PolicyReason.ALLOWED;
    private volatile VoiceSourceRule voiceRule = VoiceSourceRule.ALL;
    private volatile String serverDenialMessage = "";
    private volatile double inputLevel;

    private VoiceChangerPresetStore presetStore;
    private List<String> savedPresetNames = List.of();
    private String selectedSavedPresetName;
    /**
     * Volatile because {@code getSelectedVoiceId()} is part of the published
     * API, which promises any thread may call it, while this is written on the
     * client thread whenever the player picks a voice.
     */
    private volatile String selectedContributedVoiceId;
    private boolean autosaveFailureLogged;
    private boolean isApplyingState;

    private VoiceChangerController() {
        this.api.onOverrideChanged(this::onOverrideChanged);
    }

    public synchronized void initialize(Path configDirectory) {
        if (this.initialized) {
            return;
        }

        Path presetDirectory = configDirectory
                .resolve(SvVoiceChanger.MOD_ID)
                .resolve("presets");
        this.presetStore = new VoiceChangerPresetStore(presetDirectory);
        this.aiVoice = new AiVoiceController(
                new AiVoiceSettingsStore(configDirectory.resolve(SvVoiceChanger.MOD_ID).resolve("ai")),
                this.aiRuntime);
        this.aiVoice.load();
        this.initialized = true;
        reloadSavedPresetNames();
        loadAutosaveOrDefault();
        this.aiVoice.refresh();
        // Published last: a mod calling in from its own thread the instant the
        // API appears must find a controller that is already fully wired up.
        VoiceChangerApiRegistry.publish(new VoiceChangerApiImpl(this));
    }

    public synchronized void shutdown() {
        if (!this.initialized) {
            return;
        }

        VoiceChangerApiRegistry.withdraw();
        this.api.clear();
        this.selfListenMonitor.stop();
        this.aiVoice.shutdown();
        persistAutosave();
        this.initialized = false;
        refreshActiveVoice();
    }

    public void tick() {
        if (!this.initialized) {
            return;
        }

        this.serverSession.tick();
        if (!isStudioPreviewActive() && this.selfListenEnabled) {
            setSelfListenEnabled(false);
        }
        this.aiVoice.tick();
        this.aiVoice.setPreviewWanted(this.selfListenEnabled || isVoiceModMenuOpen());
        this.aiVoice.setStudioOpen(isAiScreenOpen());
        reportAiVoiceEvents();
    }

    /**
     * Tells the player, above the hotbar, what the AI voice is doing when it
     * is not obvious: a phrase engine is silent while it converts, and a
     * failing engine sends nothing at all.
     */
    private void reportAiVoiceEvents() {
        String failure = this.aiVoice.takeFailure();
        if (failure != null) {
            // The engine's own words are for the log; the player only needs
            // to know the voice hiccuped and that it is being dealt with.
            if (!failure.equals(AiVoiceStream.FAILURE_BUSY)) {
                SvVoiceChanger.LOGGER.info("AI voice: {}", failure);
            }
            ClientScreens.sendActionBarMessage(Component.translatable("svvoicechanger.ai.actionbar.failed",
                    Component.translatable(failure.equals(AiVoiceStream.FAILURE_BUSY)
                            ? failure
                            : "svvoicechanger.ai.failure.generic")));
        }

        String trained = this.aiVoice.takeFinishedTraining();
        if (trained != null) {
            ClientScreens.sendChatMessage(Component.translatable("svvoicechanger.ai.train.finished_chat", trained));
        }

        AiVoiceStream.Activity activity = this.aiVoice.activity();
        if (activity == AiVoiceStream.Activity.CONVERTING && this.lastAiActivity != activity) {
            ClientScreens.sendActionBarMessage(Component.translatable("svvoicechanger.ai.actionbar.converting"));
        }
        this.lastAiActivity = activity;
    }

    // --- AI voice -----------------------------------------------------------

    /** Engines, settings and status of the AI voice, for the studio's AI tab. */
    public AiVoiceController aiVoice() {
        return this.aiVoice;
    }

    /** Changes the AI voice settings; the voice source switch also changes what is active. */
    public void updateAiSettings(UnaryOperator<AiVoiceSettings> change) {
        this.aiVoice.update(change);
        refreshActiveVoice();
    }

    /** Whether this server lets players speak with an AI voice. */
    public boolean isAiAllowedByServer() {
        return this.aiAllowedByServer && this.voiceRule != VoiceSourceRule.SERVER_ONLY;
    }

    /** Whether the AI voice decides what is sent, rather than the effects. */
    public boolean isAiVoiceActive() {
        return this.aiRuntime.isActive();
    }

    /**
     * Called by Simple Voice Chat's microphone thread every tick, right before
     * it decides what to send. While the AI voice is off the frame passes
     * through untouched; while it is on, what comes back replaces it, and
     * {@code null} means send nothing this tick.
     *
     * @param raw the microphone frame while the player is talking, {@code null}
     *            while they are not
     */
    public short[] onMicFrame(short[] raw) {
        this.micHookCalls.incrementAndGet();
        if (!this.aiRuntime.isActive() || (raw != null && raw.length != AudioFrames.FRAME_SAMPLES)) {
            return raw;
        }
        if (raw != null) {
            reportInputLevel(AudioFrames.peakOf(raw));
        }
        return this.aiRuntime.onFrame(raw);
    }

    /**
     * How often the microphone hook has run. Its mixin is optional, so a
     * Simple Voice Chat version that renamed {@code sendAudio} would leave it
     * silently unattached; the runtime check reads this to catch that.
     */
    public long micHookCalls() {
        return this.micHookCalls.get();
    }

    /** The AI frame actually sent, for the self-listen monitor. */
    public void publishAiFrame(short[] frame) {
        if (this.selfListenBus.isActive()) {
            this.selfListenBus.write(frame, 1);
        }
    }

    /**
     * The AI voice runs when the voice changer is on, the player picked the AI
     * voice as the source, and the server allows both. A server that allows
     * only its own shared voices is taken to mean no AI voices either.
     */
    private boolean isAiWanted() {
        return this.initialized
                && this.effectEnabled
                && this.allowedByServer
                && isAiAllowedByServer()
                && this.aiVoice.isSelected();
    }

    /** What the current server has said about the voice changer. */
    public ServerVoiceSession serverSession() {
        return this.serverSession;
    }

    public boolean isInitialized() {
        return this.initialized && this.presetStore != null;
    }

    /** The processor Simple Voice Chat hands its microphone frames to. */
    public SimpleVoiceChatAudioProcessor getAudioProcessor() {
        return this.audioProcessor;
    }

    // --- Live audio surface -------------------------------------------------

    /**
     * What the audio thread should apply right now, resolved in one read so an
     * override cannot land between two separate questions.
     */
    public ActiveVoice getActiveVoice() {
        return this.activeVoice;
    }

    /** Whether the voice is being changed at all, override, AI voice and server policy included. */
    public boolean isEffectActive() {
        return this.activeVoice.active() || this.aiRuntime.isActive();
    }

    /** Whether the player has the effect switched on, ignoring anything on top of it. */
    public boolean isEffectEnabled() {
        return this.effectEnabled;
    }

    /** The tuning the player chose, ignoring any override on top of it. */
    public VoiceProfile getPlayerProfile() {
        return this.playerProfile;
    }

    // --- Public API surface -------------------------------------------------

    /** The overrides, contributed voices and listeners behind {@link VoiceChangerApi}. */
    public VoiceChangerApiSupport api() {
        return this.api;
    }

    /** Whether the server permits the voice changer here. */
    public boolean isAllowedByServer() {
        return this.allowedByServer;
    }

    /** How much freedom this server gives the player over their voice. */
    public VoiceSourceRule getVoiceRule() {
        return this.voiceRule;
    }

    /** Whether the voice the player currently has selected is permitted here. */
    public boolean isSelectedVoiceAllowed() {
        return this.voiceRule.allows(getSelectedVoiceId());
    }

    /** Why the server refused, or {@link PolicyReason#ALLOWED} when it has not. */
    public PolicyReason getServerDenialReason() {
        return this.serverDenialReason;
    }

    /**
     * The server operator's own wording for the refusal, or empty when they
     * gave none and {@link #getServerDenialReason()} should be shown instead.
     */
    public String getServerDenialMessage() {
        return this.serverDenialMessage;
    }

    /**
     * Identifier of the voice the player selected: a built-in id such as
     * {@code man}, a contributed {@code namespace:id}, or {@code custom}.
     */
    public String getSelectedVoiceId() {
        if (this.selectedPreset != VoiceChangerPreset.CUSTOM) {
            return this.selectedPreset.id();
        }
        return this.selectedContributedVoiceId != null ? this.selectedContributedVoiceId : "custom";
    }

    public String getSelectedContributedVoiceId() {
        return this.selectedContributedVoiceId;
    }

    /**
     * Applies what the server says about the voice changer here.
     *
     * <p>Advisory by nature: the voice is changed on this machine before the
     * audio is encoded, so a server can ask an honest client to stop and has no
     * way to make a modified one comply.</p>
     */
    public void applyServerPolicy(
            boolean allowed, PolicyReason reason, String message, VoiceSourceRule voices, boolean aiAllowed) {
        this.serverDenialReason = allowed ? PolicyReason.ALLOWED : reason;
        this.serverDenialMessage = allowed || message == null ? "" : message;

        boolean ruleChanged = this.voiceRule != voices || this.aiAllowedByServer != aiAllowed;
        this.voiceRule = voices;
        this.aiAllowedByServer = aiAllowed;
        if (ruleChanged) {
            refreshActiveVoice();
        }

        if (this.allowedByServer == allowed) {
            return;
        }

        this.allowedByServer = allowed;
        refreshActiveVoice();

        // Listeners get something a mod can branch on, preferring the
        // operator's wording only when there is one to show a player.
        String described = allowed
                ? null
                : (this.serverDenialMessage.isBlank() ? reason.name() : this.serverDenialMessage);
        this.api.notifyEach(listener -> listener.onAllowedChanged(allowed, described));
    }

    /** Recomputes the audio thread's snapshot. Cheap, so it is done eagerly. */
    private void refreshActiveVoice() {
        this.activeVoice = this.initialized
                ? this.api.resolve(this.effectEnabled, this.allowedByServer,
                        isSelectedVoiceAllowed(), this.playerProfile, this.strength)
                : ActiveVoice.INACTIVE;
        if (this.aiVoice != null) {
            this.aiVoice.setWanted(isAiWanted());
        }
    }

    /** A mod pushed an override or released one. */
    private void onOverrideChanged() {
        refreshActiveVoice();
        String owner = this.api.winningOverrideOwner();
        this.api.notifyEach(listener -> listener.onOverrideChanged(owner));
    }

    public int getStrength() {
        return this.strength;
    }

    /** Reported by whichever chain is running, so the studio can draw a meter. */
    public void reportInputLevel(double peak) {
        this.inputLevel = peak;
    }

    public double getInputLevel() {
        return this.inputLevel;
    }

    public SelfListenMonitor.Mode getSelfListenMode() {
        return this.selfListenMonitor.mode();
    }

    /**
     * What the chain the user is currently hearing is doing. While the studio
     * previews, that is the monitor's own chain; otherwise it is the microphone
     * processor that feeds other players.
     */
    public VoiceDiagnostics getDiagnostics() {
        VoiceDiagnostics preview = this.selfListenMonitor.diagnostics();
        return preview.isRunning() ? preview : this.audioProcessor.diagnostics();
    }

    // --- State --------------------------------------------------------------

    public boolean isSelfListenEnabled() {
        return this.selfListenEnabled;
    }

    public VoiceChangerPreset getSelectedPreset() {
        return this.selectedPreset;
    }

    public String getSelectedSavedPresetName() {
        return this.selectedSavedPresetName;
    }

    public Path getPresetDirectory() {
        requireInitialized();
        return this.presetStore.getDirectory();
    }

    public List<String> listSavedPresetNames() throws IOException {
        requireInitialized();
        this.savedPresetNames = this.presetStore.listPresetNames();
        return this.savedPresetNames;
    }

    public List<String> getSavedPresetNamesCached() {
        return this.savedPresetNames;
    }

    // --- Commands from the UI ------------------------------------------------

    public void setEnabled(boolean enabled) {
        this.effectEnabled = enabled;
        refreshActiveVoice();
        persistAutosave();
        showToggleStatus();
        this.api.notifyEach(listener -> listener.onEnabledChanged(enabled));
    }

    public void toggleEnabled() {
        setEnabled(!this.effectEnabled);
    }

    public void setStrength(int strength) {
        this.strength = clampInt(strength, 0, 100);
        refreshActiveVoice();
        persistAutosave();
        this.api.notifyEach(listener -> listener.onStrengthChanged(this.strength));
    }

    public void setSelfListenEnabled(boolean enabled) {
        this.selfListenEnabled = enabled && isStudioPreviewActive();
        if (this.selfListenEnabled) {
            this.selfListenMonitor.start();
        } else {
            this.selfListenMonitor.stop();
        }
        persistAutosave();
    }

    public void applyBuiltInPreset(VoiceChangerPreset preset) {
        if (preset == VoiceChangerPreset.CUSTOM || !this.voiceRule.allows(preset.id())) {
            return;
        }

        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                preset,
                this.strength,
                null,
                null,
                preset.profile()
        ));
    }

    /** Selects a voice another mod contributed, remembering it by id. */
    public void applyContributedPreset(VoicePreset preset) {
        if (!this.voiceRule.allows(preset.id())) {
            return;
        }

        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                VoiceChangerPreset.CUSTOM,
                this.strength,
                null,
                preset.id(),
                preset.profile()
        ));
    }

    /**
     * Applies a tuning the player made themselves. Refused outright while the
     * server allows only ready-made voices, so the studio's sliders cannot be
     * the way around that policy even if a widget is left enabled by mistake.
     */
    public void applyCustomProfile(VoiceProfile profile) {
        if (!this.voiceRule.allowsHandTuning()) {
            return;
        }

        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                VoiceChangerPreset.CUSTOM,
                this.strength,
                null,
                null,
                profile
        ));
    }

    public void loadSavedPreset(String rawName) throws IOException {
        if (!this.voiceRule.allowsPersonalPresets()) {
            return;
        }

        requireInitialized();
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        VoiceProfile profile = this.presetStore.loadPreset(sanitized);
        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                VoiceChangerPreset.CUSTOM,
                this.strength,
                sanitized,
                null,
                profile
        ));
    }

    public void ensurePresetDirectory() throws IOException {
        requireInitialized();
        this.presetStore.ensureDirectory();
    }

    public String saveCurrentPreset(String rawName) throws IOException {
        requireInitialized();
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        this.presetStore.savePreset(sanitized, this.playerProfile);
        reloadSavedPresetNames();
        this.selectedSavedPresetName = sanitized;
        persistAutosave();
        return sanitized;
    }

    public boolean deleteSavedPreset(String rawName) throws IOException {
        requireInitialized();
        boolean deleted = this.presetStore.deletePreset(rawName);
        reloadSavedPresetNames();

        if (deleted && rawName.equalsIgnoreCase(String.valueOf(this.selectedSavedPresetName))) {
            this.selectedSavedPresetName = null;
        }

        persistAutosave();
        return deleted;
    }

    public void reloadSavedPresetNames() {
        if (!isInitialized()) {
            return;
        }

        try {
            this.savedPresetNames = this.presetStore.listPresetNames();
        } catch (IOException exception) {
            this.savedPresetNames = List.of();
            SvVoiceChanger.LOGGER.warn("Could not read the saved preset folder", exception);
        }
    }

    // --- Internals -----------------------------------------------------------

    private void loadAutosaveOrDefault() {
        VoiceChangerState state;
        try {
            state = this.presetStore.loadAutosaveState();
        } catch (IOException | RuntimeException exception) {
            SvVoiceChanger.LOGGER.warn("Could not read the saved settings; starting from defaults", exception);
            state = VoiceChangerState.defaults();
        }

        applyState(state);
    }

    private void applyState(VoiceChangerState state) {
        this.isApplyingState = true;
        try {
            this.playerProfile = state.profile();
            this.selectedSavedPresetName = normalizeSavedPresetName(state.savedPresetName());
            this.selectedContributedVoiceId = state.contributedVoiceId();
            this.effectEnabled = state.enabled();
            this.selfListenEnabled = state.selfListen() && isStudioPreviewActive();
            this.selectedPreset = state.preset();
            this.strength = clampInt(state.strength(), 0, 100);
        } finally {
            this.isApplyingState = false;
        }

        refreshActiveVoice();
        if (this.selfListenEnabled) {
            this.selfListenMonitor.start();
        } else {
            this.selfListenMonitor.stop();
        }
        persistAutosave();
        this.api.notifyEach(listener -> listener.onVoiceChanged(getSelectedVoiceId()));
    }

    private String normalizeSavedPresetName(String savedPresetName) {
        if (savedPresetName == null || savedPresetName.isBlank()) {
            return null;
        }

        return this.savedPresetNames.contains(savedPresetName) ? savedPresetName : null;
    }

    private void persistAutosave() {
        if (!isInitialized() || this.isApplyingState) {
            return;
        }

        try {
            this.presetStore.saveAutosave(new VoiceChangerState(
                    this.effectEnabled,
                    this.selfListenEnabled,
                    this.selectedPreset,
                    this.strength,
                    this.selectedSavedPresetName,
                    this.selectedContributedVoiceId,
                    this.playerProfile
            ));
            this.autosaveFailureLogged = false;
        } catch (IOException exception) {
            // Runs on every change a player makes, so a broken disk would
            // otherwise fill the log with the same line.
            if (!this.autosaveFailureLogged) {
                this.autosaveFailureLogged = true;
                SvVoiceChanger.LOGGER.warn("Could not save the voice changer settings", exception);
            }
        }
    }

    /**
     * Simple Voice Chat's own settings, where its microphone test plays the
     * processed microphone back; with the AI voice on, that is the AI
     * preview. Its screens are recognised by package.
     */
    private static boolean isVoiceModMenuOpen() {
        Object screen = MinecraftScreenAccess.current(Minecraft.getInstance());
        return screen != null && screen.getClass().getName().startsWith("de.maxhenkel.voicechat");
    }

    /** The AI tab and the screens it opens, which want the engine running for previews and training. */
    private static boolean isAiScreenOpen() {
        Object screen = MinecraftScreenAccess.current(Minecraft.getInstance());
        return screen instanceof AiVoiceScreen || screen instanceof AiTrainScreen
                || screen instanceof AiModelPickerScreen || screen instanceof AiTextScreen;
    }

    private boolean isStudioPreviewActive() {
        Object screen = MinecraftScreenAccess.current(Minecraft.getInstance());
        return screen instanceof VoiceChangerStudioScreen || screen instanceof AiVoiceScreen;
    }

    private void showToggleStatus() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        Component status = this.effectEnabled
                ? Component.translatable("svvoicechanger.actionbar.enabled")
                : Component.translatable("svvoicechanger.actionbar.disabled");
        //? if >=26.1 {
        /*client.player.sendOverlayMessage(status);
        *///?} else {
        client.player.displayClientMessage(status, true);
        //?}
    }

    private void requireInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("Voice changer is not initialized");
        }
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
