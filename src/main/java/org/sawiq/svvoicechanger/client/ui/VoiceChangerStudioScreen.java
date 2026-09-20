package org.sawiq.svvoicechanger.client.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;
import org.sawiq.svvoicechanger.client.MinecraftTextAccess;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;
import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.audio.SelfListenMonitor;
import org.sawiq.svvoicechanger.client.audio.VoiceDiagnostics;
import org.sawiq.svvoicechanger.client.model.AutotuneScale;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;
import org.sawiq.svvoicechanger.client.ui.widget.ParameterSlider;
import org.sawiq.svvoicechanger.client.ui.widget.StrengthSlider;

/**
 * The studio.
 *
 * <p>Two modes, because the audience is split. <b>Simple</b> shows the voices
 * as a grid of buttons, one strength control and a live input meter, and is
 * what opens by default. <b>Advanced</b> exposes every parameter, laid out
 * automatically from {@link VoiceParameter} rather than from a hand-maintained
 * list of ranges.</p>
 */
public final class VoiceChangerStudioScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_HEIGHT = 20;
    private static final int CONTENT_TOP = 34;
    private static final int FOOTER_HEIGHT = 34;
    private static final int COLUMN_WIDTH = 206;
    private static final int COLUMN_GAP = 8;
    private static final int PRESET_COLUMNS = 4;

    /**
     * Vertical space between the top row and the voice grid, reserved for the
     * level meter, its caption and the diagnostics line. The space is reserved
     * unconditionally so the grid does not shift under the pointer.
     */
    private static final int METER_BLOCK_HEIGHT = 58;
    private static final int METER_BAR_OFFSET = 6;
    private static final int METER_BAR_HEIGHT = 6;
    private static final int METER_CAPTION_OFFSET = 16;
    private static final int METER_NOTE_OFFSET = 28;
    private static final int METER_DIAGNOSTIC_OFFSET = 39;

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
    private final ScrollPanel scrollPanel = new ScrollPanel();
    private final List<ParameterSlider> parameterSliders = new ArrayList<>();
    private final List<VoiceButton> voiceButtons = new ArrayList<>();
    /**
     * The contributed voices the current layout was built from. The registry
     * hands out an immutable snapshot per change, so an identity comparison is
     * enough to notice that a mod or a server added or removed one while this
     * was open.
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

    // --- Screen lifecycle ----------------------------------------------------

    @Override
    protected void init() {
        clearWidgets();
        this.scrollPanel.clear();
        this.parameterSliders.clear();
        this.voiceButtons.clear();
        this.layoutContributedVoices = this.controller.api().contributedPresets().all();

        if (this.mode == Mode.SIMPLE) {
            buildSimpleMode(CONTENT_TOP);
        } else {
            buildAdvancedMode(CONTENT_TOP);
        }

        this.scrollPanel.layout(CONTENT_TOP, contentBottom());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 60, this.height - 28, 120, WIDGET_HEIGHT)
                .build());

        refreshFromController(false);
    }

    @Override
    public void onClose() {
        this.controller.setSelfListenEnabled(false);
        if (this.minecraft != null) {
            MinecraftScreenAccess.show(this.minecraft, this.parent);
        }
    }

    @Override
    //? if >=26.1 {
    /*public void extractRenderState(GuiGraphics context, int mouseX, int mouseY, float delta) {
    *///?} else {
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
    //?}
        context.fill(0, 0, this.width, this.height, 0xA0000000);
        MinecraftTextAccess.drawCentered(context, this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        // Under the title in both modes: somebody whose voice changer has
        // stopped working opens this screen first, and the answer should be the
        // first thing here rather than something to hunt for.
        if (!this.controller.isAllowedByServer()) {
            MinecraftTextAccess.drawCentered(context, this.font, serverBlockedText(),
                    this.width / 2, 23, 0xFFFF7777);
        } else if (this.controller.getVoiceRule() != VoiceSourceRule.ALL) {
            MinecraftTextAccess.drawCentered(context, this.font, voicesRestrictedText(),
                    this.width / 2, 23,
                    this.controller.isSelectedVoiceAllowed() ? 0xFFFFAA55 : 0xFFFF7777);
        }

        //? if >=26.1 {
        /*super.extractRenderState(context, mouseX, mouseY, delta);
        *///?} else {
        super.render(context, mouseX, mouseY, delta);
        //?}

        this.scrollPanel.renderScrollbar(context, this.width - 7);
        if (this.mode == Mode.SIMPLE) {
            renderLevelMeter(context);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollPanel.scrollBy(-(int) Math.round(verticalAmount * ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        if (handleScrollKey(event.key())) {
            return true;
        }

        return super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleScrollKey(keyCode)) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    private boolean handleScrollKey(int keyCode) {
        if (!this.scrollPanel.isScrollable()) {
            return false;
        }

        int step = switch (keyCode) {
            case KEY_UP -> -ROW_HEIGHT;
            case KEY_DOWN -> ROW_HEIGHT;
            case KEY_PAGE_UP -> -this.scrollPanel.viewportHeight();
            case KEY_PAGE_DOWN -> this.scrollPanel.viewportHeight();
            default -> 0;
        };

        return step != 0 && this.scrollPanel.scrollBy(step);
    }

    /** Rebuilds when a mod or the server contributes or withdraws a voice. */
    @Override
    public void tick() {
        super.tick();
        if (this.controller.api().contributedPresets().all() != this.layoutContributedVoices) {
            init();
        }
    }

    // --- Rendering -----------------------------------------------------------

    /**
     * Peak input level, smoothed on the way down so the bar falls at a readable
     * speed instead of flickering at the audio block rate.
     */
    private void renderLevelMeter(GuiGraphics context) {
        double peak = Math.min(1.0D, this.controller.getInputLevel());
        this.meterLevel = peak > this.meterLevel ? peak : this.meterLevel * 0.90D;

        int left = centeredLeft(COLUMN_WIDTH * 2 + COLUMN_GAP);
        int right = left + COLUMN_WIDTH * 2 + COLUMN_GAP;
        int rowBottom = CONTENT_TOP + WIDGET_HEIGHT - this.scrollPanel.scrollOffset();

        int barTop = rowBottom + METER_BAR_OFFSET;
        context.fill(left, barTop, right, barTop + METER_BAR_HEIGHT, 0xFF202020);
        int filled = left + (int) ((right - left) * this.meterLevel);
        context.fill(left, barTop, filled, barTop + METER_BAR_HEIGHT,
                this.meterLevel > 0.92D ? 0xFFFF5555 : 0xFF55DD55);
        MinecraftTextAccess.draw(context, this.font,
                Component.translatable("svvoicechanger.studio.level"),
                left, rowBottom + METER_CAPTION_OFFSET, 0xFFAAAAAA);

        drawDiagnostics(context, left, rowBottom + METER_DIAGNOSTIC_OFFSET);

        if (this.controller.getSelfListenMode() == SelfListenMonitor.Mode.UNAVAILABLE) {
            MinecraftTextAccess.draw(context, this.font,
                    Component.translatable("svvoicechanger.studio.self_listen_unavailable"),
                    left, rowBottom + METER_NOTE_OFFSET, 0xFFFFAA55);
        }
    }

    /**
     * One line saying what the chain is doing to the voice right now: the pitch
     * it measured, where the preset is taking it, and whether microphone blocks
     * are arriving at all. Without it, a preset that is running but inaudible
     * looks exactly like one that never ran.
     */
    private void drawDiagnostics(GuiGraphics context, int left, int y) {
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

        MinecraftTextAccess.draw(context, this.font, Component.literal(line), left, y,
                diagnostics.isRunning() ? 0xFF88CCFF : 0xFFFF7777);
    }

    // --- Simple mode ---------------------------------------------------------

    private void buildSimpleMode(int top) {
        int fullWidth = COLUMN_WIDTH * 2 + COLUMN_GAP;
        int left = centeredLeft(fullWidth);
        int half = (fullWidth - COLUMN_GAP) / 2;

        this.enabledButton = addScrollable(Button.builder(enabledButtonText(), button -> toggleEnabled())
                .bounds(left, top, half, WIDGET_HEIGHT).build());
        this.selfListenButton = addScrollable(withTooltip(Button.builder(selfListenButtonText(), button -> toggleSelfListen())
                .bounds(left + half + COLUMN_GAP, top, half, WIDGET_HEIGHT).build(), "svvoicechanger.studio.self_listen.desc"));

        int presetsTop = top + WIDGET_HEIGHT + METER_BLOCK_HEIGHT;
        int presetRow = buildPresetGrid(left, presetsTop, fullWidth);

        this.strengthSlider = addScrollable(new StrengthSlider(
                left, presetRow, fullWidth, WIDGET_HEIGHT,
                this.controller::getStrength, this.controller::setStrength));

        int libraryRow = presetRow + ROW_HEIGHT + 4;
        this.savedPresetButton = addScrollable(libraryWidget(withTooltip(
                Button.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                        .bounds(left, libraryRow, half, WIDGET_HEIGHT).build(),
                "svvoicechanger.studio.saved_value.desc")));
        addScrollable(withTooltip(Button.builder(Component.translatable("svvoicechanger.studio.open_folder"), button -> openPresetFolder())
                .bounds(left + half + COLUMN_GAP, libraryRow, half, WIDGET_HEIGHT).build(), "svvoicechanger.studio.open_folder.desc"));

        addScrollable(withTooltip(Button.builder(Component.translatable("svvoicechanger.studio.advanced"), button -> switchMode(Mode.ADVANCED))
                .bounds(left, libraryRow + ROW_HEIGHT + 4, fullWidth, WIDGET_HEIGHT).build(), "svvoicechanger.studio.advanced.desc"));
    }

    /** Lays the voices out in a grid. @return the y of the row below it */
    private int buildPresetGrid(int left, int top, int fullWidth) {
        List<VoiceChoice> choices = voiceChoices();
        int cellWidth = (fullWidth - COLUMN_GAP * (PRESET_COLUMNS - 1)) / PRESET_COLUMNS;

        int row = 0;
        for (int index = 0; index < choices.size(); index++) {
            VoiceChoice choice = choices.get(index);
            row = index / PRESET_COLUMNS;
            int column = index % PRESET_COLUMNS;
            int x = left + column * (cellWidth + COLUMN_GAP);
            int y = top + row * ROW_HEIGHT;

            Button button = addScrollable(Button.builder(voiceButtonText(choice), ignored -> selectVoice(choice))
                    .bounds(x, y, cellWidth, WIDGET_HEIGHT).build());
            if (choice.description() != null) {
                button.setTooltip(Tooltip.create(choice.description()));
            }
            this.voiceButtons.add(new VoiceButton(choice, button));
        }

        return top + (row + 1) * ROW_HEIGHT + 6;
    }

    /**
     * The built-in voices first, then whatever mods and the server contributed,
     * in the order they registered. Built-ins keep their places so a mod
     * appearing or disappearing never moves the buttons the player already
     * knows.
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

    // --- Advanced mode -------------------------------------------------------

    private void buildAdvancedMode(int top) {
        int left = centeredLeft(COLUMN_WIDTH * 2 + COLUMN_GAP);
        int right = left + COLUMN_WIDTH + COLUMN_GAP;
        int half = (COLUMN_WIDTH - 4) / 2;

        this.presetNameField = addScrollable(new EditBox(this.font, left, top, COLUMN_WIDTH - 70, WIDGET_HEIGHT,
                Component.translatable("svvoicechanger.studio.preset_name")));
        this.presetNameField.setMaxLength(48);
        this.presetNameField.setValue("MyPreset");
        addScrollable(libraryWidget(
                Button.builder(Component.translatable("svvoicechanger.studio.save"), button -> saveCurrentPreset())
                        .bounds(left + COLUMN_WIDTH - 64, top, 64, WIDGET_HEIGHT).build()));

        this.savedPresetButton = addScrollable(libraryWidget(withTooltip(
                Button.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                        .bounds(right, top, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "svvoicechanger.studio.saved_value.desc")));

        int secondRow = top + ROW_HEIGHT + 4;
        this.enabledButton = addScrollable(Button.builder(enabledButtonText(), button -> toggleEnabled())
                .bounds(left, secondRow, COLUMN_WIDTH, WIDGET_HEIGHT).build());
        this.selfListenButton = addScrollable(withTooltip(Button.builder(selfListenButtonText(), button -> toggleSelfListen())
                .bounds(right, secondRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "svvoicechanger.studio.self_listen.desc"));

        int thirdRow = secondRow + ROW_HEIGHT + 4;
        addScrollable(tuningWidget(withTooltip(
                Button.builder(Component.translatable("svvoicechanger.studio.reset"), button -> resetToNeutral())
                        .bounds(left, thirdRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "svvoicechanger.studio.reset.desc")));
        addScrollable(libraryWidget(withTooltip(
                Button.builder(Component.translatable("svvoicechanger.studio.delete_saved"), button -> deleteSelectedPreset())
                        .bounds(right, thirdRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(),
                "svvoicechanger.studio.delete_saved.desc")));

        int fourthRow = thirdRow + ROW_HEIGHT + 4;
        addScrollable(withTooltip(Button.builder(Component.translatable("svvoicechanger.studio.simple"), button -> switchMode(Mode.SIMPLE))
                .bounds(left, fourthRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "svvoicechanger.studio.simple.desc"));
        addScrollable(withTooltip(Button.builder(Component.translatable("svvoicechanger.studio.open_folder"), button -> openPresetFolder())
                .bounds(right, fourthRow, COLUMN_WIDTH, WIDGET_HEIGHT).build(), "svvoicechanger.studio.open_folder.desc"));

        int slidersTop = fourthRow + ROW_HEIGHT + 8;
        this.strengthSlider = addScrollable(new StrengthSlider(
                left, slidersTop, COLUMN_WIDTH, WIDGET_HEIGHT,
                this.controller::getStrength, this.controller::setStrength));

        for (int i = 0; i < LEFT_COLUMN.length; i++) {
            addParameterSlider(left, slidersTop + ROW_HEIGHT * (i + 1), LEFT_COLUMN[i]);
        }
        for (int i = 0; i < RIGHT_COLUMN.length; i++) {
            addParameterSlider(right, slidersTop + ROW_HEIGHT * i, RIGHT_COLUMN[i]);
        }

        int autotuneRow = slidersTop + ROW_HEIGHT * RIGHT_COLUMN.length;
        this.autotuneKeyButton = addScrollable(tuningWidget(withTooltip(
                Button.builder(autotuneKeyButtonText(), button -> cycleAutotuneKey())
                        .bounds(right, autotuneRow, half, WIDGET_HEIGHT).build(),
                VoiceParameter.AUTOTUNE_KEY.descriptionKey())));
        this.autotuneScaleButton = addScrollable(tuningWidget(withTooltip(
                Button.builder(autotuneScaleButtonText(), button -> cycleAutotuneScale())
                        .bounds(right + half + 4, autotuneRow, COLUMN_WIDTH - half - 4, WIDGET_HEIGHT).build(),
                VoiceParameter.AUTOTUNE_SCALE.descriptionKey())));
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

    private void addParameterSlider(int x, int y, VoiceParameter parameter) {
        ParameterSlider slider = new ParameterSlider(
                x, y, COLUMN_WIDTH, WIDGET_HEIGHT, parameter,
                this.controller::getPlayerProfile, this::applyCustomProfile);
        this.parameterSliders.add(slider);
        addScrollable(tuningWidget(slider));
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
            java.awt.Desktop.getDesktop().open(this.controller.getPresetDirectory().toFile());
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
                ? "svvoicechanger.studio.enabled_on"
                : "svvoicechanger.studio.enabled_off");
    }

    private Component selfListenButtonText() {
        return Component.translatable(this.controller.isSelfListenEnabled()
                ? "svvoicechanger.studio.self_listen_on"
                : "svvoicechanger.studio.self_listen_off");
    }

    private Component savedPresetButtonText() {
        Component value = CURRENT_PRESET_OPTION.equals(this.selectedSavedPreset)
                ? Component.translatable("svvoicechanger.saved.current")
                : Component.literal(this.selectedSavedPreset);
        return Component.translatable("svvoicechanger.studio.saved_value", value);
    }

    /** The active voice is marked in the label, since widget skins are version-specific. */
    private Component voiceButtonText(VoiceChoice choice) {
        boolean selected = choice.id().equals(this.controller.getSelectedVoiceId())
                && this.controller.getSelectedSavedPresetName() == null;
        return selected
                ? Component.translatable("svvoicechanger.studio.preset_selected", choice.name())
                : choice.name();
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

    private <T extends AbstractWidget> T addScrollable(T widget) {
        this.scrollPanel.add(widget);
        return addRenderableWidget(widget);
    }

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

    private int centeredLeft(int contentWidth) {
        return Math.max(8, (this.width - contentWidth) / 2);
    }

    private int contentBottom() {
        return Math.max(CONTENT_TOP + 24, this.height - FOOTER_HEIGHT);
    }

    private void notifyUser(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            //? if >=26.1 {
            /*client.player.sendSystemMessage(message);
            *///?} else {
            client.player.displayClientMessage(message, false);
            //?}
        }
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
