package org.sawiq.svvoicechanger.client.ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.ai.AiVoiceController;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettings;
import org.sawiq.svvoicechanger.client.ai.LocalAddress;
import org.sawiq.svvoicechanger.client.ai.engine.EngineChoice;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.ai.install.EngineFiles;
import org.sawiq.svvoicechanger.client.ai.install.EngineInstaller;
import org.sawiq.svvoicechanger.client.ai.install.PackageSet;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.ui.compat.CompatScreen;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;
import org.sawiq.svvoicechanger.client.ui.widget.RangeSlider;

/**
 * The studio's AI voice tab, as a settings list in Plasmo Voice's style.
 *
 * <p>The simple view holds what most players need: what they sound like
 * right now, the switch, the voice and its one setting, and the way to a voice
 * of their own. The advanced view adds every engine's own settings, one engine
 * at a time. Engines are picked automatically by default; the settings of
 * every engine stay reachable so a player can set up the one they want before
 * it is running.</p>
 */
public final class AiVoiceScreen extends CompatScreen {
    private static final int WIDGET_HEIGHT = SettingsList.WIDGET_HEIGHT;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;

    private static final String GUIDE_URL = "https://github.com/imsawiq/sv-voice-changer/blob/main/docs/AI_VOICE.md";
    private static final String ELEVENLABS_KEYS_URL = "https://elevenlabs.io/app/settings/api-keys";
    private static final String ELEVENLABS_LIBRARY_URL = "https://elevenlabs.io/app/voice-library";

    private final Screen parent;
    private final VoiceChangerController addon;
    private final List<Runnable> labelRefreshers = new ArrayList<>();

    private SettingsList list;
    private EngineKind shownEngine;
    /** Kept across openings, like the studio's own mode: whoever went advanced wants it again. */
    private static boolean advancedMode;

    public AiVoiceScreen(Screen parent, VoiceChangerController addon) {
        super(Component.translatable("svvoicechanger.studio.title"));
        this.parent = parent;
        this.addon = addon;
    }

    private AiVoiceController ai() {
        return this.addon.aiVoice();
    }

    // --- Screen lifecycle --------------------------------------------------

    @Override
    protected void init() {
        // First layout only: someone opening this tab has usually just started
        // or set up an engine, and should not have to press Refresh to see it.
        if (this.shownEngine == null) {
            ai().refresh();
            EngineKind target = ai().targetEngine();
            this.shownEngine = target != null ? target : EngineKind.VOICE_ENGINE;
        }

        clearWidgets();
        this.labelRefreshers.clear();
        StudioFrame.addTabs(StudioTabs.of(StudioTabs.Tab.AI_VOICE, this::openTab), this.font::width,
                this::addRenderableWidget);

        this.list = new SettingsList(this.width, this::addRenderableWidget);
        if (advancedMode) {
            buildAdvanced();
        } else {
            buildSimple();
        }
        this.list.finish(this.height);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 75, StudioFrame.footerY(this.height), 150, WIDGET_HEIGHT)
                .build());
        refreshLabels();
    }

    @Override
    public void onClose() {
        this.addon.setSelfListenEnabled(false);
        ClientScreens.open(this.parent);
    }

    @Override
    public void tick() {
        super.tick();
        refreshLabels();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.list.scrollBy(-(int) Math.round(verticalAmount * SettingsList.ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean onKeyPressed(int keyCode) {
        int step = switch (keyCode) {
            case KEY_PAGE_UP -> -this.list.viewportHeight();
            case KEY_PAGE_DOWN -> this.list.viewportHeight();
            default -> 0;
        };
        return step != 0 && this.list.scrollBy(step);
    }

    private void openTab(StudioTabs.Tab tab) {
        if (tab == StudioTabs.Tab.EFFECTS) {
            ClientScreens.open(new VoiceChangerStudioScreen(this.parent, this.addon));
        }
    }

    // --- Simple view -----------------------------------------------------------

    /**
     * What others hear and the next step first, then the switches, then the
     * voice. Until an engine is set up, the voice section gives way to the
     * three ways of getting one.
     */
    private void buildSimple() {
        addStatus();

        this.list.category(Component.translatable("svvoicechanger.ai.section.main"));
        addOption("svvoicechanger.ai.row.enabled", "svvoicechanger.ai.simple.switch.desc", this::toggleAiVoice,
                () -> onOff(ai().isSelected() && this.addon.isEffectEnabled()));
        addOption("svvoicechanger.studio.row.self_listen", "svvoicechanger.ai.self_listen.desc",
                () -> this.addon.setSelfListenEnabled(!this.addon.isSelfListenEnabled()),
                () -> onOff(this.addon.isSelfListenEnabled()));

        EngineKind target = ai().targetEngine();
        if (target == null) {
            this.list.category(Component.translatable("svvoicechanger.ai.card.start"));
            this.list.row(button("svvoicechanger.ai.choose.engine", "svvoicechanger.ai.choose.engine.desc",
                    this::chooseEngine));
            this.list.row(button("svvoicechanger.ai.choose.elevenlabs", "svvoicechanger.ai.choose.elevenlabs.desc",
                    () -> chooseAdvanced(EngineKind.ELEVENLABS)));
            this.list.row(button("svvoicechanger.ai.choose.wokada", "svvoicechanger.ai.choose.wokada.desc",
                    () -> chooseAdvanced(EngineKind.W_OKADA)));
        } else {
            this.list.category(Component.translatable("svvoicechanger.ai.card.voice"));
            addVoiceRows(target);
        }

        addInstallProgress();
        this.list.gap(6);
        this.list.row(button("svvoicechanger.ai.simple.advanced", "svvoicechanger.ai.simple.advanced.desc",
                () -> switchMode(true)));
    }

    /** The voice, its one setting, and the ways to more voices. */
    private void addVoiceRows(EngineKind engine) {
        addOption("svvoicechanger.ai.row.voice", "svvoicechanger.ai.simple.voice.desc", () -> openGallery(engine),
                () -> Component.literal(currentVoiceName(engine) + " ▸"));
        if (engine == EngineKind.ELEVENLABS) {
            this.list.option(Component.translatable("svvoicechanger.ai.elevenlabs.stability"),
                    percentSlider("svvoicechanger.ai.elevenlabs.stability",
                            () -> elevenLabs().stability(), value -> updateElevenLabs(e -> e.withStability(value))));
        } else {
            this.list.option(Component.translatable("svvoicechanger.ai.local.pitch"), pitchSlider(engine));
        }
        // Training needs the mod's own engine.
        if (engine == EngineKind.VOICE_ENGINE) {
            this.list.option(Component.translatable("svvoicechanger.ai.voices.own"),
                    button("svvoicechanger.ai.button.train", "svvoicechanger.ai.voices.own.desc", this::openTraining));
        }
        this.list.option(Component.translatable("svvoicechanger.ai.voices.where"),
                button("svvoicechanger.ai.button.open", "svvoicechanger.ai.voices.where.desc",
                        () -> openWhereToGetVoices(engine)));
    }

    /**
     * What other players hear, and the one thing in the way of the AI voice,
     * as one block so a short status leaves no gap above the switches.
     */
    private void addStatus() {
        this.list.text(() -> {
            Component hint = AiStatusText.hintLine(this.addon);
            Component now = Component.literal("● ").append(AiStatusText.nowLine(this.addon));
            return hint.getString().isEmpty() ? now : now.copy().append("\n").append(hint);
        }, () -> AiStatusText.nowColor(this.addon), 3);
    }

    /** What the installer is doing, while it is doing something or has just failed. */
    private void addInstallProgress() {
        this.list.text(() -> {
            Component line = installProgressLine();
            return line != null ? line : Component.empty();
        }, () -> StudioPaint.YELLOW, 1);
    }

    private void toggleAiVoice() {
        boolean on = ai().isSelected() && this.addon.isEffectEnabled();
        if (!on && !this.addon.isEffectEnabled()) {
            this.addon.setEnabled(true);
        }
        this.addon.updateAiSettings(settings -> settings.withUseAi(!on));
    }

    /** The free engine: install it if it is not there yet, otherwise it is simply picked. */
    private void chooseEngine() {
        this.addon.updateAiSettings(settings -> settings.withEngine(EngineChoice.AUTO).withUseAi(true));
        if (!ai().installer().isInstalled() && ai().installer().progress().state() != EngineInstaller.State.UNSUPPORTED) {
            this.shownEngine = EngineKind.VOICE_ENGINE;
            onInstallButton();
            return;
        }
        chooseAdvanced(EngineKind.VOICE_ENGINE);
    }

    private void chooseAdvanced(EngineKind engine) {
        this.addon.updateAiSettings(settings -> settings.withUseAi(true));
        this.shownEngine = engine;
        switchMode(true);
    }

    private void switchMode(boolean advanced) {
        advancedMode = advanced;
        init();
    }

    private String currentVoiceName(EngineKind engine) {
        if (engine == EngineKind.ELEVENLABS) {
            return elevenLabs().hasVoice()
                    ? (elevenLabs().voiceName().isBlank() ? elevenLabs().voiceId() : elevenLabs().voiceName())
                    : Component.translatable("svvoicechanger.ai.voice.none").getString();
        }
        return localModelName(engine);
    }

    // --- Voices --------------------------------------------------------------

    private void openGallery(EngineKind engine) {
        List<EngineModel> voices = ai().status(engine).models();
        String selected = engine == EngineKind.ELEVENLABS ? elevenLabs().voiceId() : localSettings(engine).modelId();
        AiModelPickerScreen gallery = new AiModelPickerScreen(this,
                Component.translatable("svvoicechanger.ai.picker.voice"), voices, selected,
                voice -> selectVoice(engine, voice));
        if (ai().canPreview(engine)) {
            gallery.withPreview(voice -> ai().previewVoice(engine, voice.id()));
        }
        gallery.withExtraButton(Component.translatable(engine == EngineKind.ELEVENLABS
                        ? "svvoicechanger.ai.elevenlabs.library"
                        : "svvoicechanger.ai.voices.add"),
                () -> addVoices(engine));
        if (engine == EngineKind.VOICE_ENGINE) {
            gallery.withExtraButton(Component.translatable("svvoicechanger.ai.voices.own"), this::openTraining);
        }
        ClientScreens.open(gallery);
    }

    private void selectVoice(EngineKind engine, EngineModel voice) {
        if (engine == EngineKind.ELEVENLABS) {
            updateElevenLabs(e -> e.withVoice(voice.id(), voice.name()));
        } else {
            updateLocal(engine, settings -> settings.withModel(voice.id()));
        }
    }

    /** Where new voices go: the voices folder, w-okada's own page, or the ElevenLabs library. */
    private void addVoices(EngineKind engine) {
        switch (engine) {
            case VOICE_ENGINE -> openModelsFolder();
            case W_OKADA -> LocalAddress.parse(ai().settings().wOkada().address())
                    .ifPresent(address -> openLink(address.uri("/").toString()));
            case ELEVENLABS -> openLink(ELEVENLABS_LIBRARY_URL);
        }
    }

    private void openTraining() {
        ClientScreens.open(new AiTrainScreen(this, this.addon));
    }

    private void openWhereToGetVoices(EngineKind engine) {
        ClientScreens.open(new AiTextScreen(this,
                Component.translatable("svvoicechanger.ai.voices.where"),
                Component.translatable(engine == EngineKind.ELEVENLABS
                        ? "svvoicechanger.ai.where.elevenlabs"
                        : "svvoicechanger.ai.where.rvc"),
                Component.translatable("svvoicechanger.ai.voices.add"),
                () -> addVoices(engine)));
    }

    // --- Advanced view -------------------------------------------------------

    /**
     * Every engine's state, the switches that decide whether the AI voice is
     * heard and which engine speaks, then one engine's own settings, picked
     * with a row of engine buttons.
     */
    private void buildAdvanced() {
        for (EngineKind kind : EngineKind.values()) {
            this.list.text(() -> AiStatusText.engineLine(ai(), kind),
                    () -> AiStatusText.engineColor(ai().status(kind)), 1);
        }
        addStatus();

        this.list.category(Component.translatable("svvoicechanger.ai.section.main"));
        addOption("svvoicechanger.studio.row.enabled", "svvoicechanger.tab.enable.desc", this.addon::toggleEnabled,
                () -> onOff(this.addon.isEffectEnabled()));
        addOption("svvoicechanger.ai.row.source", "svvoicechanger.ai.source.desc",
                () -> this.addon.updateAiSettings(settings -> settings.withUseAi(!settings.useAi())),
                () -> Component.translatable(ai().isSelected()
                        ? "svvoicechanger.ai.value.ai"
                        : "svvoicechanger.ai.value.effects"));
        addOption("svvoicechanger.ai.row.engine", "svvoicechanger.ai.choice.desc",
                () -> this.addon.updateAiSettings(settings -> settings.withEngine(settings.engine().next())),
                () -> Component.translatable(ai().settings().engine().translationKey()));
        addOption("svvoicechanger.studio.row.self_listen", "svvoicechanger.ai.self_listen.desc",
                () -> this.addon.setSelfListenEnabled(!this.addon.isSelfListenEnabled()),
                () -> onOff(this.addon.isSelfListenEnabled()));

        this.list.category(Component.translatable("svvoicechanger.ai.section.engines"));
        addEngineButtons();
        switch (this.shownEngine) {
            case ELEVENLABS -> buildElevenLabs();
            case VOICE_ENGINE, W_OKADA -> buildLocalEngine(this.shownEngine);
        }

        addInstallProgress();
        this.list.gap(6);
        this.list.row(button("svvoicechanger.ai.simple.back", "svvoicechanger.ai.simple.back.desc",
                () -> switchMode(false)));
    }

    /**
     * Which engine's settings are shown below, independent of which one is
     * used, and a button to ask every engine again. The shown one is greyed
     * out, the way Plasmo Voice marks its open tab.
     */
    private void addEngineButtons() {
        List<Button> buttons = new ArrayList<>();
        for (EngineKind kind : EngineKind.values()) {
            Button button = withTooltip(Button.builder(Component.translatable(kind.shortTranslationKey()), pressed -> {
                this.shownEngine = kind;
                init();
            }).bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT).build(), kind.translationKey() + ".desc");
            button.active = kind != this.shownEngine;
            buttons.add(button);
        }
        buttons.add(button("svvoicechanger.ai.refresh", "svvoicechanger.ai.refresh.desc", ai()::retryEngine));
        this.list.row(buttons.toArray(new Button[0]));
    }

    private void buildElevenLabs() {
        this.list.option(Component.translatable("svvoicechanger.ai.row.key"),
                button("svvoicechanger.ai.button.paste", "svvoicechanger.ai.elevenlabs.paste_key.desc", this::pasteKey));
        Button clearKey = addOption("svvoicechanger.ai.row.saved_key", "svvoicechanger.ai.elevenlabs.clear_key.desc",
                () -> ai().clearElevenLabsKey(),
                () -> ai().hasElevenLabsKey()
                        ? Component.literal(ai().maskedElevenLabsKey() + " ✕")
                        : Component.translatable("svvoicechanger.ai.value.none"));
        this.labelRefreshers.add(() -> clearKey.active = ai().hasElevenLabsKey() && clearKey.visible);
        addOption("svvoicechanger.ai.row.voice", "svvoicechanger.ai.elevenlabs.voice.desc", this::pickElevenLabsVoice,
                () -> Component.literal(currentVoiceName(EngineKind.ELEVENLABS) + " ▸"));
        addOption("svvoicechanger.ai.row.model", "svvoicechanger.ai.elevenlabs.model.desc", this::cycleElevenLabsModel,
                () -> Component.literal(elevenLabsModelName()));
        this.list.option(Component.translatable("svvoicechanger.ai.elevenlabs.stability"),
                percentSlider("svvoicechanger.ai.elevenlabs.stability",
                        () -> elevenLabs().stability(), value -> updateElevenLabs(e -> e.withStability(value))));
        this.list.option(Component.translatable("svvoicechanger.ai.elevenlabs.similarity"),
                percentSlider("svvoicechanger.ai.elevenlabs.similarity",
                        () -> elevenLabs().similarity(), value -> updateElevenLabs(e -> e.withSimilarity(value))));
        this.list.option(Component.translatable("svvoicechanger.ai.elevenlabs.style"),
                percentSlider("svvoicechanger.ai.elevenlabs.style",
                        () -> elevenLabs().style(), value -> updateElevenLabs(e -> e.withStyle(value))));
        addOption("svvoicechanger.ai.row.speaker_boost", "svvoicechanger.ai.elevenlabs.speaker_boost.desc",
                () -> updateElevenLabs(e -> e.withSpeakerBoost(!e.speakerBoost())),
                () -> onOff(elevenLabs().speakerBoost()));
        addOption("svvoicechanger.ai.row.remove_noise", "svvoicechanger.ai.elevenlabs.remove_noise.desc",
                () -> updateElevenLabs(e -> e.withRemoveNoise(!e.removeNoise())),
                () -> onOff(elevenLabs().removeNoise()));
        this.list.row(
                button("svvoicechanger.ai.elevenlabs.get_key", "svvoicechanger.ai.elevenlabs.get_key.desc",
                        () -> openLink(ELEVENLABS_KEYS_URL)),
                button("svvoicechanger.ai.elevenlabs.library", "svvoicechanger.ai.elevenlabs.library.desc",
                        () -> openLink(ELEVENLABS_LIBRARY_URL)));
        this.list.row(button("svvoicechanger.ai.guide", "svvoicechanger.ai.guide.desc",
                () -> openHelp(EngineKind.ELEVENLABS, "#elevenlabs")));
    }

    private void buildLocalEngine(EngineKind kind) {
        if (kind == EngineKind.VOICE_ENGINE) {
            addInstallRows();
        } else {
            addAddressRow(kind);
        }
        addOption("svvoicechanger.ai.row.voice", "svvoicechanger.ai.local.model.desc", () -> pickLocalModel(kind),
                () -> Component.literal(localModelName(kind) + " ▸"));
        this.list.option(Component.translatable("svvoicechanger.ai.local.pitch"), pitchSlider(kind));
        this.list.option(Component.translatable("svvoicechanger.ai.local.chunk"),
                new RangeSlider(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT, "svvoicechanger.ai.local.chunk",
                        AiVoiceSettings.MIN_CHUNK_MILLIS, AiVoiceSettings.MAX_CHUNK_MILLIS,
                        value -> (value / 20 * 20) + " ms",
                        () -> localSettings(kind).chunkMillis(),
                        value -> updateLocal(kind, engine -> engine.withChunkMillis(value / 20 * 20))).valueOnly());
        if (kind == EngineKind.VOICE_ENGINE) {
            this.list.option(Component.translatable("svvoicechanger.ai.voices.own"),
                    button("svvoicechanger.ai.button.train", "svvoicechanger.ai.voices.own.desc", this::openTraining));
        }
        String anchor = kind == EngineKind.W_OKADA ? "#w-okada" : "#voice-changer-engine";
        this.list.option(Component.translatable("svvoicechanger.ai.guide"),
                button("svvoicechanger.ai.button.open", "svvoicechanger.ai.guide.desc", () -> openHelp(kind, anchor)));
    }

    /**
     * Where w-okada listens. The mod's own engine has none: the mod starts it
     * on its own port, and a field the player could break it with is noise.
     */
    private void addAddressRow(EngineKind kind) {
        EditBox address = new EditBox(this.font, 0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT,
                Component.translatable("svvoicechanger.ai.address"));
        address.setMaxLength(64);
        address.setValue(localSettings(kind).address());
        address.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.ai.address.desc")));
        address.setResponder(text -> {
            // Saved only once it reads as a local address; half-typed text
            // would otherwise send a probe to every intermediate spelling.
            if (LocalAddress.parse(text).isPresent()) {
                updateLocal(kind, engine -> engine.withAddress(text));
            }
        });
        this.list.option(Component.translatable("svvoicechanger.ai.address"), address);
    }

    // --- Installed engine ---------------------------------------------------

    /**
     * Install, cancel or remove the engine, and its voices folder. The labels
     * and what a click does follow the installer's state, which changes while
     * the screen is open.
     */
    private void addInstallRows() {
        Button main = addOption("svvoicechanger.ai.row.install", "svvoicechanger.ai.install.button.desc",
                this::onInstallButton,
                () -> Component.translatable(switch (ai().installer().progress().state()) {
                    case UNSUPPORTED -> "svvoicechanger.ai.value.windows_only";
                    case NOT_INSTALLED, FAILED -> "svvoicechanger.ai.value.install";
                    case INSTALLING -> "svvoicechanger.ai.value.cancel";
                    case INSTALLED -> "svvoicechanger.ai.value.remove";
                }));
        this.labelRefreshers.add(() -> main.active = main.visible
                && ai().installer().progress().state() != EngineInstaller.State.UNSUPPORTED);
        this.list.option(Component.translatable("svvoicechanger.ai.install.models"),
                button("svvoicechanger.ai.button.open", "svvoicechanger.ai.install.models.desc", this::openModelsFolder));
    }

    private void onInstallButton() {
        EngineInstaller installer = ai().installer();
        switch (installer.progress().state()) {
            case NOT_INSTALLED, FAILED -> ClientScreens.open(new AiTextScreen(this,
                    Component.translatable("svvoicechanger.ai.install.title"),
                    installConsent(installer.packageSet()),
                    Component.translatable("svvoicechanger.ai.install.agree"),
                    installer::install));
            case INSTALLING -> installer.cancel();
            case INSTALLED -> ClientScreens.open(new AiTextScreen(this,
                    Component.translatable("svvoicechanger.ai.install.remove_title"),
                    Component.translatable("svvoicechanger.ai.install.remove_consent", EngineFiles.root().toString()),
                    Component.translatable("svvoicechanger.ai.install.remove"),
                    () -> ai().uninstallEngine()));
            case UNSUPPORTED -> {
            }
        }
    }

    /**
     * What installing involves, with the download for this player's card. A
     * card not found out yet reads as the largest set, so nobody is promised
     * less than they get.
     */
    private static Component installConsent(PackageSet packages) {
        PackageSet shown = packages != null ? packages : PackageSet.CUDA_12;
        return Component.translatable("svvoicechanger.ai.install.consent",
                EngineFiles.root().toString(),
                Component.translatable("svvoicechanger.ai.install.packages." + shown.name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%.1f", shown.downloadMegabytes() / 1000.0D));
    }

    /** What the installer is doing, while it is doing something or has just failed; otherwise null. */
    private Component installProgressLine() {
        EngineInstaller.Progress progress = ai().installer().progress();
        return switch (progress.state()) {
            case INSTALLING -> Component.translatable("svvoicechanger.ai.install.progress",
                    Component.translatable("svvoicechanger.ai.install.step." + progress.step()),
                    progress.percent() >= 0 ? progress.percent() + "%" : "",
                    progress.detail());
            case FAILED -> Component.translatable("svvoicechanger.ai.install.failed", progress.detail());
            default -> null;
        };
    }

    private void openModelsFolder() {
        try {
            Files.createDirectories(EngineFiles.modelsDirectory());
            LinkOpener.open(EngineFiles.modelsDirectory().toString());
        } catch (Exception exception) {
            notifyUser(Component.translatable("svvoicechanger.ai.open_link_failed", EngineFiles.modelsDirectory().toString()));
        }
    }

    // --- Rendering ---------------------------------------------------------

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        StudioFrame.paintBackground(graphics, this.width, this.height);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        StudioFrame.paintChrome(graphics, this.width, this.height, this.title);
        this.list.paint(graphics);
    }

    // --- Actions -----------------------------------------------------------

    private void pasteKey() {
        if (ai().setElevenLabsKey(ClientScreens.clipboardText())) {
            notifyUser(Component.translatable("svvoicechanger.ai.elevenlabs.key_saved"));
        } else {
            notifyUser(Component.translatable("svvoicechanger.ai.elevenlabs.key_empty"));
        }
    }

    private void pickElevenLabsVoice() {
        List<EngineModel> voices = ai().status(EngineKind.ELEVENLABS).models();
        ClientScreens.open(new AiModelPickerScreen(this, Component.translatable("svvoicechanger.ai.picker.voice"),
                voices, elevenLabs().voiceId(),
                voice -> updateElevenLabs(e -> e.withVoice(voice.id(), voice.name()))));
    }

    private void cycleElevenLabsModel() {
        List<EngineModel> models = ai().elevenLabsModels();
        if (models.isEmpty()) {
            return;
        }
        int index = indexOf(models, elevenLabs().modelId());
        EngineModel next = models.get((index + 1) % models.size());
        updateElevenLabs(e -> e.withModel(next.id()));
    }

    private void pickLocalModel(EngineKind kind) {
        List<EngineModel> models = ai().status(kind).models();
        ClientScreens.open(new AiModelPickerScreen(this, Component.translatable("svvoicechanger.ai.picker.model"),
                models, localSettings(kind).modelId(),
                model -> updateLocal(kind, engine -> engine.withModel(model.id()))));
    }

    private void updateElevenLabs(UnaryOperator<AiVoiceSettings.ElevenLabs> change) {
        this.addon.updateAiSettings(settings -> settings.withElevenLabs(change.apply(settings.elevenLabs())));
    }

    private void updateLocal(EngineKind kind, UnaryOperator<AiVoiceSettings.LocalEngine> change) {
        this.addon.updateAiSettings(settings -> kind == EngineKind.W_OKADA
                ? settings.withWOkada(change.apply(settings.wOkada()))
                : settings.withVoiceEngine(change.apply(settings.voiceEngine())));
    }

    /** The guide for one engine, in the game, with the GitHub copy one click away. */
    private void openHelp(EngineKind kind, String anchor) {
        String url = GUIDE_URL + anchor;
        ClientScreens.open(new AiTextScreen(this,
                Component.translatable(kind.translationKey()),
                Component.translatable("svvoicechanger.ai.help." + kind.id()),
                Component.translatable("svvoicechanger.ai.help.web"),
                () -> openLink(url)));
    }

    private void openLink(String url) {
        try {
            LinkOpener.open(url);
        } catch (Exception exception) {
            // Some launchers run without a desktop to hand links to; the
            // address in chat can still be copied from there.
            notifyUser(Component.translatable("svvoicechanger.ai.open_link_failed", url));
        }
    }

    // --- Rows and helpers ----------------------------------------------------

    /** An option row whose button label is recomputed every tick, since the AI state changes underneath it. */
    private Button addOption(String labelKey, String descriptionKey, Runnable action, Supplier<Component> value) {
        Button button = this.list.option(Component.translatable(labelKey),
                withTooltip(Button.builder(value.get(), pressed -> action.run())
                        .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT).build(), descriptionKey));
        this.labelRefreshers.add(() -> button.setMessage(value.get()));
        return button;
    }

    private static Button button(String labelKey, String descriptionKey, Runnable action) {
        return withTooltip(Button.builder(Component.translatable(labelKey), pressed -> action.run())
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT).build(), descriptionKey);
    }

    private RangeSlider pitchSlider(EngineKind engine) {
        return new RangeSlider(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT, "svvoicechanger.ai.local.pitch",
                AiVoiceSettings.MIN_PITCH, AiVoiceSettings.MAX_PITCH,
                value -> (value > 0 ? "+" : "") + value,
                () -> localSettings(engine).pitch(),
                value -> updateLocal(engine, settings -> settings.withPitch(value))).valueOnly();
    }

    private static RangeSlider percentSlider(String labelKey, IntSupplier value, IntConsumer consumer) {
        return new RangeSlider(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT, labelKey, 0, 100,
                percent -> percent + "%", value, consumer).valueOnly();
    }

    private AiVoiceSettings.ElevenLabs elevenLabs() {
        return ai().settings().elevenLabs();
    }

    private AiVoiceSettings.LocalEngine localSettings(EngineKind kind) {
        return kind == EngineKind.W_OKADA ? ai().settings().wOkada() : ai().settings().voiceEngine();
    }

    private String elevenLabsModelName() {
        String id = elevenLabs().modelId();
        for (EngineModel model : ai().elevenLabsModels()) {
            if (model.id().equals(id)) {
                return model.name();
            }
        }
        return id;
    }

    /** The model's name once the engine has listed it, its id until then, "loaded" when none is pinned. */
    private String localModelName(EngineKind kind) {
        String id = localSettings(kind).modelId();
        if (id.isBlank()) {
            return Component.translatable("svvoicechanger.ai.model.engine_default").getString();
        }
        for (EngineModel model : ai().status(kind).models()) {
            if (model.id().equals(id)) {
                return model.name();
            }
        }
        return id;
    }

    private static int indexOf(List<EngineModel> models, String id) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "svvoicechanger.ui.on" : "svvoicechanger.ui.off");
    }

    private void refreshLabels() {
        for (Runnable refresher : this.labelRefreshers) {
            refresher.run();
        }
    }

    private static Button withTooltip(Button button, String descriptionKey) {
        button.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
        return button;
    }

    private void notifyUser(Component message) {
        ClientScreens.sendChatMessage(message);
    }
}
