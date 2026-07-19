package org.sawiq.svvoicechanger.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.audio.VoiceChangerSelfListenPreview;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;
import org.sawiq.svvoicechanger.client.model.VoiceChangerState;
import org.sawiq.svvoicechanger.client.preset.VoiceChangerPresetStore;
import org.sawiq.svvoicechanger.client.ui.VoiceChangerStudioScreen;

public final class VoiceChangerController {
    public static final VoiceChangerController INSTANCE = new VoiceChangerController();

    private final VoiceChangerSelfListenPreview selfListenPreview = new VoiceChangerSelfListenPreview(
            this::isSelfListenEnabled,
            this::isStudioPreviewActive,
            this::isEffectEnabled,
            this::getCurrentProfileSnapshot,
            this::getStrength,
            () -> setSelfListenSilently(false)
    );

    private volatile boolean initialized;
    private volatile boolean effectEnabled;
    private volatile boolean selfListenEnabled;
    private volatile VoiceChangerPreset selectedPreset = VoiceChangerPreset.MAN;
    private volatile int strength = 100;
    private volatile VoiceChangerProfile currentProfile =
            VoiceChangerProfile.defaultsFor(VoiceChangerPreset.MAN);

    private VoiceChangerPresetStore presetStore;
    private List<String> savedPresetNames = List.of();
    private String selectedSavedPresetName;
    private boolean isApplyingState;

    private VoiceChangerController() {
    }

    public synchronized void initialize(Path configDirectory) {
        if (this.initialized) {
            return;
        }

        Path presetDirectory = configDirectory
                .resolve(SvVoiceChanger.MOD_ID)
                .resolve("presets");
        this.presetStore = new VoiceChangerPresetStore(presetDirectory);
        this.initialized = true;
        reloadSavedPresetNames();
        loadAutosaveOrDefault();
    }

    public synchronized void shutdown() {
        if (!this.initialized) {
            return;
        }

        this.selfListenPreview.stop();
        persistAutosave();
        this.initialized = false;
    }

    public void tick() {
        if (this.initialized && !isStudioPreviewActive() && this.selfListenEnabled) {
            setSelfListenEnabled(false);
        }
    }

    public boolean isInitialized() {
        return this.initialized && this.presetStore != null;
    }

    public boolean isEffectEnabled() {
        return this.effectEnabled;
    }

    public boolean isSelfListenEnabled() {
        return this.selfListenEnabled;
    }

    public int getStrength() {
        return this.strength;
    }

    public VoiceChangerPreset getSelectedPreset() {
        return this.selectedPreset;
    }

    public VoiceChangerProfile getCurrentProfileSnapshot() {
        return this.currentProfile;
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

    public void setEnabled(boolean enabled) {
        this.effectEnabled = enabled;
        persistAutosave();
        showToggleStatus();
    }

    public void toggleEnabled() {
        setEnabled(!this.effectEnabled);
    }

    public void setStrength(int strength) {
        this.strength = clampInt(strength, 0, 100);
        persistAutosave();
    }

    public void setSelfListenEnabled(boolean enabled) {
        setSelfListenSilently(enabled && isStudioPreviewActive());
        if (this.selfListenEnabled) {
            this.selfListenPreview.start();
        } else {
            this.selfListenPreview.stop();
        }
        persistAutosave();
    }

    public void applyBuiltInPreset(VoiceChangerPreset preset) {
        if (preset == VoiceChangerPreset.CUSTOM) {
            return;
        }

        applyState(new VoiceChangerState(
                this.effectEnabled,
                false,
                preset,
                this.strength,
                null,
                VoiceChangerProfile.defaultsFor(preset)
        ));
    }

    public void applyCustomProfile(VoiceChangerProfile profile) {
        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                VoiceChangerPreset.CUSTOM,
                this.strength,
                null,
                profile
        ));
    }

    public void loadSavedPreset(String rawName) throws IOException {
        requireInitialized();
        String sanitized = VoiceChangerPresetStore.sanitizeName(rawName);
        VoiceChangerProfile profile = this.presetStore.loadPreset(sanitized);
        applyState(new VoiceChangerState(
                this.effectEnabled,
                this.selfListenEnabled,
                VoiceChangerPreset.CUSTOM,
                this.strength,
                sanitized,
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
        this.presetStore.savePreset(sanitized, this.currentProfile);
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
            SvVoiceChanger.LOGGER.warn("Failed to load saved voice changer presets", exception);
        }
    }

    private void loadAutosaveOrDefault() {
        VoiceChangerState state;
        try {
            state = this.presetStore.loadAutosaveState();
        } catch (IOException | RuntimeException exception) {
            SvVoiceChanger.LOGGER.warn("Failed to load the voice changer autosave; using defaults", exception);
            state = VoiceChangerState.defaults();
        }

        applyState(state);
    }

    private void applyState(VoiceChangerState state) {
        this.isApplyingState = true;
        try {
            this.currentProfile = sanitizeProfile(state.profile());
            this.selectedSavedPresetName = normalizeSavedPresetName(state.savedPresetName());
            this.effectEnabled = state.enabled();
            this.selfListenEnabled = state.selfListen() && isStudioPreviewActive();
            this.selectedPreset = state.preset();
            this.strength = clampInt(state.strength(), 0, 100);
        } finally {
            this.isApplyingState = false;
        }

        if (this.selfListenEnabled) {
            this.selfListenPreview.start();
        } else {
            this.selfListenPreview.stop();
        }
        persistAutosave();
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
                    this.currentProfile
            ));
        } catch (IOException exception) {
            SvVoiceChanger.LOGGER.warn("Failed to save voice changer settings", exception);
        }
    }

    private void setSelfListenSilently(boolean enabled) {
        this.selfListenEnabled = enabled;
    }

    private boolean isStudioPreviewActive() {
        return MinecraftScreenAccess.current(Minecraft.getInstance()) instanceof VoiceChangerStudioScreen;
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

    private static VoiceChangerProfile sanitizeProfile(VoiceChangerProfile profile) {
        return new VoiceChangerProfile(
                sanitize(profile.mix(), 0.90D, 0.0D, 1.0D),
                sanitize(profile.gain(), 1.00D, 0.40D, 2.50D),
                sanitize(profile.pitch(), 1.00D, 0.55D, 2.20D),
                sanitize(profile.formant(), 1.00D, 0.45D, 2.00D),
                sanitize(profile.distortion(), 0.00D, 0.0D, 0.75D),
                sanitize(profile.robotMix(), 0.00D, 0.0D, 0.85D),
                clampInt(profile.robotFrequency(), 20, 140),
                sanitize(profile.echoMix(), 0.00D, 0.0D, 0.65D),
                clampInt(profile.echoDelayMs(), 40, 260),
                sanitize(profile.echoFeedback(), 0.10D, 0.0D, 0.80D),
                sanitize(profile.tremoloDepth(), 0.00D, 0.0D, 0.75D),
                sanitize(profile.tremoloRate(), 2.50D, 0.20D, 9.00D),
                sanitize(profile.lowEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.midEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.highEq(), 0.00D, -10.0D, 10.0D),
                sanitize(profile.noise(), 0.00D, 0.0D, 0.60D),
                sanitize(profile.autotuneMix(), 0.00D, 0.0D, 1.0D),
                sanitize(profile.autotuneStrength(), 0.60D, 0.0D, 1.0D),
                clampInt(profile.autotuneKey(), 0, 11),
                clampInt(profile.autotuneScale(), 0, 2),
                sanitize(
                        profile.bitDepth(),
                        VoiceChangerProfile.BIT_DEPTH_CLEAN,
                        VoiceChangerProfile.BIT_DEPTH_MIN,
                        VoiceChangerProfile.BIT_DEPTH_CLEAN
                )
        );
    }

    private static double sanitize(double value, double fallback, double min, double max) {
        if (!Double.isFinite(value)) {
            return fallback;
        }

        return Math.max(min, Math.min(max, value));
    }
}
