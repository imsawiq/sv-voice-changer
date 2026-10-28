package org.sawiq.svvoicechanger.client.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.client.audio.SelfListenMonitor;
import org.sawiq.svvoicechanger.client.audio.VoiceDiagnostics;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.model.AutotuneScale;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;
import org.sawiq.svvoicechanger.client.ui.compat.CompatScreen;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;
import org.sawiq.svvoicechanger.client.ui.widget.ParameterSlider;
import org.sawiq.svvoicechanger.client.ui.widget.StrengthSlider;
import org.sawiq.svvoicechanger.protocol.PolicyReason;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * The studio.
 *
 * <p>Two modes, because the audience is split. <b>Simple</b> shows the voices
 * as a grid of buttons, one strength control and a live input meter, and is
 * what opens by default. <b>Advanced</b> exposes every parameter, laid out
 * automatically from {@link VoiceParameter} rather than from a hand-maintained
 * list of ranges.</p>
 */
public final class VoiceChangerStudioScreen extends CompatScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_HEIGHT = 20;
    private static final int PRESET_COLUMNS = 4;

    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;

    private static final String CURRENT_PRESET_OPTION = "__current__";
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    /** Parameters shown in the advanced view's left column, in order. */
    private static final VoiceParameter[] LEFT_COLUMN = {
            VoiceParameter.MIX,
            VoiceParameter.GAIN,
            VoiceParameter.PITCH,
            VoiceParameter.FORMANT,
            VoiceParameter.VOICE_MATCH,
            VoiceParameter.GROWL,
            VoiceParameter.LOW_EQ,
            VoiceParameter.MID_EQ,
            VoiceParameter.HIGH_EQ,
            VoiceParameter.GATE,
            VoiceParameter.DE_ESS
    };

    /** Parameters shown in the advanced view's right column, in order. */
    private static final VoiceParameter[] RIGHT_COLUMN = {
            VoiceParameter.RADIO,
            VoiceParameter.DISTORTION,
            VoiceParameter.BIT_DEPTH,
            VoiceParameter.NOISE,
            VoiceParameter.ROBOT_MIX,
            VoiceParameter.ROBOT_FREQUENCY,
            VoiceParameter.TREMOLO_DEPTH,
            VoiceParameter.TREMOLO_RATE,
            VoiceParameter.REVERB_MIX,
            VoiceParameter.REVERB_SIZE,
            VoiceParameter.REVERB_DECAY,
            VoiceParameter.AUTOTUNE_MIX,
            VoiceParameter.AUTOTUNE_SPEED
    };

    private enum Mode { SIMPLE, ADVANCED }

    private final Screen parent;
    private final VoiceChangerController controller;
    private SettingsList list;
    private final List<ParameterSlider> parameterSliders = new ArrayList<>();
    private final List<VoiceButton> voiceButtons = new ArrayList<>();
    /**
     * The contributed voices the current layout was built from. The registry
     * hands out an immutable snapshot per change, so an identity comparison is
     * enough to notice that a mod added or removed one while this was open.
     */
    private List<VoicePreset> layoutContributedVoices = List.of();

    private Mode mode = Mode.SIMPLE;
    private String selectedSavedPreset = CURRENT_PRESET_OPTION;

    private EditBox presetNameField;
    private Button enabledButton;
    private Button selfListenButton;
    private Button savedPresetButton;
    private Button autotuneKeyButton;
    private Button autotuneScaleButton;
    private StrengthSlider strengthSlider;
    private double meterLevel;

    public VoiceChangerStudioScreen(Screen parent, VoiceChangerController controller) {
        super(Component.translatable("svvoicechanger.studio.title"));
        this.parent = parent;
        this.controller = controller;
    }

    // --- Screen lifecycle --------------------------------------------------

    @Override
    protected void init() {
        clearWidgets();
        this.parameterSliders.clear();
        this.voiceButtons.clear();
        this.enabledButton = null;
        this.selfListenButton = null;
        this.savedPresetButton = null;
        this.autotuneKeyButton = null;
        this.autotuneScaleButton = null;
        this.strengthSlider = null;
        this.presetNameField = null;

        this.layoutContributedVoices = this.controller.api().contributedPresets().all();
        StudioFrame.addTabs(StudioTabs.of(StudioTabs.Tab.EFFECTS, this::openTab), this.font::width,
                this::addRenderableWidget);

        this.list = new SettingsList(this.width, this::addRenderableWidget);
        addServerNotice();
        if (this.mode == Mode.SIMPLE) {
            buildSimpleMode();
        } else {
            buildAdvancedMode();
        }
        this.list.finish(this.height);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 75, StudioFrame.footerY(this.height), 150, WIDGET_HEIGHT)
                .build());

        refreshFromController(false);
    }

    @Override
    public void onClose() {
        this.controller.setSelfListenEnabled(false);
        ClientScreens.open(this.parent);
    }

    /** Self-listen stays on across the switch: the AI tab is the same studio. */
    private void openTab(StudioTabs.Tab tab) {
        if (tab == StudioTabs.Tab.AI_VOICE) {
            ClientScreens.open(new AiVoiceScreen(this.parent, this.controller));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.list.scrollBy(-(int) Math.round(verticalAmount * ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected boolean onKeyPressed(int keyCode) {
        if (!this.list.isScrollable()) {
            return false;
        }

        int step = switch (keyCode) {
            case KEY_UP -> -ROW_HEIGHT;
            case KEY_DOWN -> ROW_HEIGHT;
            case KEY_PAGE_UP -> -this.list.viewportHeight();
            case KEY_PAGE_DOWN -> this.list.viewportHeight();
            default -> 0;
        };

        return step != 0 && this.list.scrollBy(step);
    }

    // --- Rendering ---------------------------------------------------------

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        StudioFrame.paintBackground(graphics, this.width, this.height);
    }

    /**
     * Drawn after the widgets rather than behind them: up to 1.21.5 the vanilla
     * screen blurs the menu background as part of drawing its widgets, and
     * would blur anything painted before that along with it.
     */
    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        StudioFrame.paintChrome(graphics, this.width, this.height, this.title);
        this.list.paint(graphics);
    }

    /**
     * Above everything else in both modes: somebody whose voice changer has
     * stopped working opens this screen first, and the answer should be the
     * first thing here rather than something to hunt for.
     */
    private void addServerNotice() {
        if (!this.controller.isAllowedByServer()) {
            this.list.text(this::serverBlockedText, () -> StudioPaint.RED, 2);
        } else if (this.controller.getVoiceRule() != VoiceSourceRule.ALL) {
            this.list.text(this::voicesRestrictedText,
                    () -> this.controller.isSelectedVoiceAllowed() ? 0xFFFFAA55 : StudioPaint.RED, 2);
        }
    }

    /**
     * What the server allows, and — when the player's own voice is not among it
     * — that they have to pick something else before anything is heard.
     */
    private Component voicesRestrictedText() {
        Component allowed = Component.translatable(this.controller.getVoiceRule().translationKey());
        return this.controller.isSelectedVoiceAllowed()
                ? Component.translatable("svvoicechanger.studio.voices_restricted", allowed)
                : Component.translatable("svvoicechanger.studio.voices_restricted_pick", allowed);
    }

    /** The server's own wording when it sent one, otherwise ours, translated. */
    private Component serverBlockedText() {
        String message = this.controller.getServerDenialMessage();
        Component reason = message.isBlank()
                ? Component.translatable(this.controller.getServerDenialReason().translationKey())
                : Component.literal(message);
        return Component.translatable("svvoicechanger.studio.server_blocked", reason);
    }

    /**
     * The microphone meter as an option row: the label on the left, the bar
     * where a control would be. Peak level, smoothed on the way down so the bar
     * falls at a readable speed instead of flickering at the audio block rate.
     */
    private void addLevelMeter() {
        this.list.custom(SettingsList.ROW_HEIGHT, (graphics, x, y, width) -> {
            double peak = Math.min(1.0D, this.controller.getInputLevel());
            this.meterLevel = peak > this.meterLevel ? peak : this.meterLevel * 0.90D;

            // The monitor never silently switches to another microphone, so the
            // only thing worth reporting is that it could not start at all.
            boolean unavailable = this.controller.getSelfListenMode() == SelfListenMonitor.Mode.UNAVAILABLE;
            StudioPaint.line(graphics, Component.translatable(unavailable
                            ? "svvoicechanger.studio.self_listen_unavailable"
                            : "svvoicechanger.studio.level"),
                    x, y + 8, width - SettingsList.CONTROL_WIDTH - 4, unavailable ? 0xFFFFAA55 : StudioPaint.WHITE);
            int barX = x + width - SettingsList.CONTROL_WIDTH;
            StudioPaint.bar(graphics, barX, y + 9, SettingsList.CONTROL_WIDTH, 6, this.meterLevel,
                    this.meterLevel > 0.92D ? 0xFFFF5555 : 0xFF55DD55);
        });
    }

    /**
     * One line saying what the chain is doing to the voice right now: the pitch
     * it measured, where the preset is taking it, and whether microphone blocks
     * are arriving at all. Without it, a preset that is running but inaudible
     * looks exactly like one that never ran. Only in the advanced view: to
     * everybody else it is noise.
     */
    private void addDiagnostics() {
        this.list.custom(SettingsList.ROW_HEIGHT, (graphics, x, y, width) -> {
            VoiceDiagnostics diagnostics = this.controller.getDiagnostics();
            String heard = diagnostics.speakerPitchHz() > 0.0D
                    ? Math.round(diagnostics.speakerPitchHz()) + " Hz"
                    : "?";
            String target = diagnostics.speakerPitchHz() > 0.0D
                    ? Math.round(diagnostics.targetPitchHz()) + " Hz"
                    : "?";
            String line = String.format("%s -> %s   x%.2f   %d ch  %d fr  %d/s",
                    heard, target, diagnostics.appliedPitchRatio(),
                    diagnostics.channels(), diagnostics.blockFrames(), diagnostics.blocksPerSecond());
            graphics.centeredText(Component.literal(line), x + width / 2, y + 8,
                    diagnostics.isRunning() ? 0xFF88CCFF : StudioPaint.RED);
        });
    }

    /**
     * Rebuilds when a mod or the server contributes or withdraws a voice, and
     * otherwise keeps the labels current: a server can turn the voice changer
     * off while this is open, and the switch must not go on reading "on".
     */
    @Override
    public void tick() {
        super.tick();
        if (this.controller.api().contributedPresets().all() != this.layoutContributedVoices) {
            init();
            return;
        }
        refreshButtonLabels();
    }

    // --- Simple mode -------------------------------------------------------

    private void buildSimpleMode() {
        addSwitches();
        addLevelMeter();

        this.list.category(Component.translatable("svvoicechanger.studio.section.voice"));
        buildPresetGrid();
        this.strengthSlider = this.list.option(Component.translatable("svvoicechanger.studio.row.strength"),
                new StrengthSlider(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT,
                        this.controller::getStrength, this.controller::setStrength).valueOnly());

        this.list.category(Component.translatable("svvoicechanger.studio.section.presets"));
        this.savedPresetButton = this.list.option(Component.translatable("svvoicechanger.studio.row.saved"),
                libraryWidget(withTooltip(button(savedPresetButtonText(), this::cycleSavedPreset),
                        "svvoicechanger.studio.saved_value.desc")));
        this.list.option(Component.translatable("svvoicechanger.studio.row.folder"),
                withTooltip(button(Component.translatable("svvoicechanger.studio.open_folder"), this::openPresetFolder),
                        "svvoicechanger.studio.open_folder.desc"));

        this.list.gap(6);
        this.list.row(withTooltip(button(Component.translatable("svvoicechanger.studio.advanced"),
                () -> switchMode(Mode.ADVANCED)), "svvoicechanger.studio.advanced.desc"));
    }

    /** The two switches every view starts with. */
    private void addSwitches() {
        this.list.category(Component.translatable("svvoicechanger.studio.section.main"));
        this.enabledButton = this.list.option(Component.translatable("svvoicechanger.studio.row.enabled"),
                button(enabledButtonText(), this::toggleEnabled));
        this.selfListenButton = this.list.option(Component.translatable("svvoicechanger.studio.row.self_listen"),
                withTooltip(button(selfListenButtonText(), this::toggleSelfListen),
                        "svvoicechanger.studio.self_listen.desc"));
    }

    /** Lays the voices out in rows of four. */
    private void buildPresetGrid() {
        List<VoiceChoice> choices = voiceChoices();
        for (int first = 0; first < choices.size(); first += PRESET_COLUMNS) {
            List<Button> row = new ArrayList<>();
            for (int index = first; index < first + PRESET_COLUMNS; index++) {
                if (index >= choices.size()) {
                    // An empty cell keeps the last row's buttons the same width as the others.
                    row.add(null);
                    continue;
                }
                VoiceChoice choice = choices.get(index);
                Button button = button(voiceButtonText(choice), () -> selectVoice(choice));
                if (choice.description() != null) {
                    button.setTooltip(Tooltip.create(choice.description()));
                }
                this.voiceButtons.add(new VoiceButton(choice, button));
                row.add(button);
            }
            this.list.row(row.toArray(new Button[0]));
        }
    }

    /**
     * The built-in voices first, then whatever other mods contributed, in the
     * order they registered. Built-ins keep their places so a mod appearing or
     * disappearing never moves the buttons the player already knows.
     */
    private List<VoiceChoice> voiceChoices() {
        VoiceSourceRule rule = this.controller.getVoiceRule();
        List<VoiceChoice> choices = new ArrayList<>();

        for (VoiceChangerPreset preset : VoiceChangerPreset.selectable()) {
            if (!rule.allows(preset.id())) {
                continue;
            }
            choices.add(new VoiceChoice(
                    preset.id(),
                    Component.translatable(preset.getTranslationKey()),
                    null,
                    () -> this.controller.applyBuiltInPreset(preset)));
        }
        for (VoicePreset preset : this.controller.api().contributedPresets().all()) {
            if (!rule.allows(preset.id())) {
                continue;
            }
            choices.add(new VoiceChoice(
                    preset.id(),
                    preset.displayName(),
                    preset.description(),
                    () -> this.controller.applyContributedPreset(preset)));
        }
        return choices;
    }

    // --- Advanced mode -----------------------------------------------------

    private void buildAdvancedMode() {
        addSwitches();
        addDiagnostics();

        this.list.category(Component.translatable("svvoicechanger.studio.section.presets"));
        this.presetNameField = new EditBox(this.font, 0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT,
                Component.translatable("svvoicechanger.studio.preset_name"));
        this.presetNameField.setMaxLength(48);
        this.presetNameField.setValue("MyPreset");
        this.list.row(libraryWidget(this.presetNameField),
                libraryWidget(button(Component.translatable("svvoicechanger.studio.save"), this::saveCurrentPreset)));
        this.savedPresetButton = this.list.option(Component.translatable("svvoicechanger.studio.row.saved"),
                libraryWidget(withTooltip(button(savedPresetButtonText(), this::cycleSavedPreset),
                        "svvoicechanger.studio.saved_value.desc")));
        this.list.row(
                tuningWidget(withTooltip(button(Component.translatable("svvoicechanger.studio.reset"),
                        this::resetToNeutral), "svvoicechanger.studio.reset.desc")),
                libraryWidget(withTooltip(button(Component.translatable("svvoicechanger.studio.delete_saved"),
                        this::deleteSelectedPreset), "svvoicechanger.studio.delete_saved.desc")));
        this.list.option(Component.translatable("svvoicechanger.studio.row.folder"),
                withTooltip(button(Component.translatable("svvoicechanger.studio.open_folder"), this::openPresetFolder),
                        "svvoicechanger.studio.open_folder.desc"));

        this.list.category(Component.translatable("svvoicechanger.studio.section.parameters"));
        this.strengthSlider = new StrengthSlider(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT,
                this.controller::getStrength, this.controller::setStrength);
        int rows = Math.max(LEFT_COLUMN.length + 1, RIGHT_COLUMN.length);
        for (int i = 0; i < rows; i++) {
            // The left column starts with strength, then its parameters; the right column has its own.
            AbstractWidget left = i == 0 ? this.strengthSlider
                    : i - 1 < LEFT_COLUMN.length ? parameterSlider(LEFT_COLUMN[i - 1]) : null;
            AbstractWidget right = i < RIGHT_COLUMN.length ? parameterSlider(RIGHT_COLUMN[i]) : null;
            this.list.row(left, right);
        }
        this.autotuneKeyButton = tuningWidget(withTooltip(button(autotuneKeyButtonText(), this::cycleAutotuneKey),
                VoiceParameter.AUTOTUNE_KEY.descriptionKey()));
        this.autotuneScaleButton = tuningWidget(withTooltip(button(autotuneScaleButtonText(), this::cycleAutotuneScale),
                VoiceParameter.AUTOTUNE_SCALE.descriptionKey()));
        this.list.row(this.autotuneKeyButton, this.autotuneScaleButton);

        this.list.gap(6);
        this.list.row(withTooltip(button(Component.translatable("svvoicechanger.studio.simple"),
                () -> switchMode(Mode.SIMPLE)), "svvoicechanger.studio.simple.desc"));
    }

    /**
     * Greys out everything that would tune the voice by hand.
     *
     * <p>The controller refuses those edits anyway; this is so the player can
     * see that rather than watching a slider move and nothing happen.</p>
     */
    private <T extends AbstractWidget> T tuningWidget(T widget) {
        if (!this.controller.getVoiceRule().allowsHandTuning()) {
            widget.active = false;
            widget.setTooltip(Tooltip.create(
                    Component.translatable("svvoicechanger.studio.tuning_locked")));
        }
        return widget;
    }

    /** Greys out the personal preset library, which a server may also withhold. */
    private <T extends AbstractWidget> T libraryWidget(T widget) {
        if (!this.controller.getVoiceRule().allowsPersonalPresets()) {
            widget.active = false;
            widget.setTooltip(Tooltip.create(
                    Component.translatable("svvoicechanger.studio.library_locked")));
        }
        return widget;
    }

    private ParameterSlider parameterSlider(VoiceParameter parameter) {
        ParameterSlider slider = new ParameterSlider(
                0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT, parameter,
                this.controller::getPlayerProfile, this::applyCustomProfile);
        this.parameterSliders.add(slider);
        return tuningWidget(slider);
    }

    private static Button button(Component label, Runnable action) {
        return Button.builder(label, ignored -> action.run())
                .bounds(0, 0, SettingsList.CONTROL_WIDTH, WIDGET_HEIGHT)
                .build();
    }

    // --- Actions -------------------------------------------------------------

    private void switchMode(Mode target) {
        this.mode = target;
        init();
    }

    private void toggleEnabled() {
        this.controller.setEnabled(!this.controller.isEffectEnabled());
        refreshButtonLabels();
    }

    private void toggleSelfListen() {
        this.controller.setSelfListenEnabled(!this.controller.isSelfListenEnabled());
        refreshButtonLabels();
    }

    private void selectVoice(VoiceChoice choice) {
        choice.apply().run();
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshFromController(false);
    }

    private void applyCustomProfile(VoiceProfile profile) {
        this.controller.applyCustomProfile(profile);
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshButtonLabels();
    }

    private void resetToNeutral() {
        this.controller.applyCustomProfile(VoiceProfile.neutral());
        this.selectedSavedPreset = CURRENT_PRESET_OPTION;
        refreshFromController(false);
    }

    private void saveCurrentPreset() {
        if (this.presetNameField == null) {
            return;
        }

        try {
            String savedName = this.controller.saveCurrentPreset(this.presetNameField.getValue());
            this.presetNameField.setValue(savedName);
            this.selectedSavedPreset = savedName;
            refreshFromController(true);
            notifyUser(Component.translatable("svvoicechanger.message.saved", savedName));
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.save_failed", describe(exception)));
        }
    }

    private void deleteSelectedPreset() {
        if (CURRENT_PRESET_OPTION.equals(this.selectedSavedPreset)) {
            notifyUser(Component.translatable("svvoicechanger.message.select_saved_first"));
            return;
        }

        String target = this.selectedSavedPreset;
        try {
            this.controller.deleteSavedPreset(target);
            this.selectedSavedPreset = CURRENT_PRESET_OPTION;
            refreshFromController(true);
            notifyUser(Component.translatable("svvoicechanger.message.deleted", target));
        } catch (IOException | IllegalStateException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.delete_failed", describe(exception)));
        }
    }

    private void cycleSavedPreset() {
        List<String> options = new ArrayList<>();
        options.add(CURRENT_PRESET_OPTION);
        options.addAll(this.controller.getSavedPresetNamesCached());

        int currentIndex = Math.max(0, options.indexOf(this.selectedSavedPreset));
        String next = options.get((currentIndex + 1) % options.size());
        this.selectedSavedPreset = next;

        if (!CURRENT_PRESET_OPTION.equals(next)) {
            try {
                this.controller.loadSavedPreset(next);
            } catch (IOException | IllegalStateException exception) {
                notifyUser(Component.translatable("svvoicechanger.message.load_failed", describe(exception)));
            }
        }

        refreshFromController(false);
    }

    private void openPresetFolder() {
        try {
            this.controller.ensurePresetDirectory();
            LinkOpener.open(this.controller.getPresetDirectory().toString());
        } catch (IOException | UnsupportedOperationException | IllegalStateException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.open_folder_failed", describe(exception)));
        }
    }

    private void cycleAutotuneKey() {
        VoiceProfile profile = this.controller.getPlayerProfile();
        int next = (profile.getInt(VoiceParameter.AUTOTUNE_KEY) + 1) % NOTE_NAMES.length;
        applyCustomProfile(profile.with(VoiceParameter.AUTOTUNE_KEY, next));
        refreshButtonLabels();
    }

    private void cycleAutotuneScale() {
        VoiceProfile profile = this.controller.getPlayerProfile();
        int next = (profile.getInt(VoiceParameter.AUTOTUNE_SCALE) + 1) % AutotuneScale.count();
        applyCustomProfile(profile.with(VoiceParameter.AUTOTUNE_SCALE, next));
        refreshButtonLabels();
    }

    // --- Refreshing ----------------------------------------------------------

    private void refreshFromController(boolean reloadPresetList) {
        if (reloadPresetList) {
            this.controller.reloadSavedPresetNames();
        }

        String savedName = this.controller.getSelectedSavedPresetName();
        this.selectedSavedPreset = savedName == null ? CURRENT_PRESET_OPTION : savedName;

        VoiceProfile profile = this.controller.getPlayerProfile();
        for (ParameterSlider slider : this.parameterSliders) {
            slider.refresh(profile);
        }
        if (this.strengthSlider != null) {
            this.strengthSlider.refresh();
        }

        refreshButtonLabels();
    }

    private void refreshButtonLabels() {
        setMessageIfPresent(this.enabledButton, enabledButtonText());
        applyServerBlock(this.enabledButton);
        setMessageIfPresent(this.selfListenButton, selfListenButtonText());
        setMessageIfPresent(this.savedPresetButton, savedPresetButtonText());
        setMessageIfPresent(this.autotuneKeyButton, autotuneKeyButtonText());
        setMessageIfPresent(this.autotuneScaleButton, autotuneScaleButtonText());

        for (VoiceButton entry : this.voiceButtons) {
            entry.button().setMessage(voiceButtonText(entry.choice()));
        }
    }

    // --- Labels --------------------------------------------------------------

    /**
     * Reads off whenever nothing is actually being sent, which includes a
     * server refusing. The player's own preference is untouched underneath and
     * comes back by itself; a button still reading "on" while the server has
     * them muted is the thing that makes a mute impossible to diagnose.
     */
    private Component enabledButtonText() {
        return Component.translatable(this.controller.isEffectEnabled() && this.controller.isAllowedByServer()
                ? "svvoicechanger.ui.on"
                : "svvoicechanger.ui.off");
    }

    private Component selfListenButtonText() {
        return Component.translatable(this.controller.isSelfListenEnabled()
                ? "svvoicechanger.ui.on"
                : "svvoicechanger.ui.off");
    }

    private Component savedPresetButtonText() {
        return CURRENT_PRESET_OPTION.equals(this.selectedSavedPreset)
                ? Component.translatable("svvoicechanger.saved.current")
                : Component.literal(this.selectedSavedPreset);
    }

    /** The active voice is marked in the label, since widget skins are version-specific. */
    private Component voiceButtonText(VoiceChoice choice) {
        boolean selected = choice.id().equals(this.controller.getSelectedVoiceId())
                && this.controller.getSelectedSavedPresetName() == null;
        return selected
                ? Component.translatable("svvoicechanger.studio.preset_selected", choice.name())
                : choice.name();
    }

    private Component autotuneKeyButtonText() {
        int key = this.controller.getPlayerProfile().getInt(VoiceParameter.AUTOTUNE_KEY);
        return Component.translatable("svvoicechanger.studio.autotune_key_value",
                NOTE_NAMES[Math.floorMod(key, NOTE_NAMES.length)]);
    }

    private Component autotuneScaleButtonText() {
        AutotuneScale scale = AutotuneScale.byIndex(
                this.controller.getPlayerProfile().getInt(VoiceParameter.AUTOTUNE_SCALE));
        return Component.translatable("svvoicechanger.studio.autotune_scale_value",
                Component.translatable(scale.translationKey()));
    }

    // --- Helpers -------------------------------------------------------------

    /**
     * Greys the on/off switch while the server refuses, and says why on hover.
     *
     * <p>Only this one control: everything else still only changes what
     * happens on this machine, so somebody waiting to be unmuted can carry on
     * setting up a voice for when they are.</p>
     */
    private void applyServerBlock(Button button) {
        if (button == null) {
            return;
        }

        boolean allowed = this.controller.isAllowedByServer();
        button.active = allowed;
        button.setTooltip(Tooltip.create(allowed
                ? Component.translatable("svvoicechanger.tab.enable.desc")
                : serverBlockedText()));
    }

    private static Button withTooltip(Button button, String descriptionKey) {
        button.setTooltip(Tooltip.create(Component.translatable(descriptionKey)));
        return button;
    }

    private static void setMessageIfPresent(Button button, Component message) {
        if (button != null) {
            button.setMessage(message);
        }
    }

    private void notifyUser(Component message) {
        ClientScreens.sendChatMessage(message);
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message;
    }

    /**
     * One entry in the voice grid, whether it came from this mod, another one,
     * or the server. The grid does not care which, so it is not told.
     *
     * @param description tooltip text, or null for none
     */
    private record VoiceChoice(String id, Component name, Component description, Runnable apply) {
    }

    private record VoiceButton(VoiceChoice choice, Button button) {
    }
}
