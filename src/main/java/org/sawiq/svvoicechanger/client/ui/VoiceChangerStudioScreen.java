package org.sawiq.svvoicechanger.client.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import org.sawiq.svvoicechanger.client.VoiceChangerController;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;
import org.sawiq.svvoicechanger.client.ui.widget.StudioProfileSlider;
import org.sawiq.svvoicechanger.client.ui.widget.StudioStrengthSlider;

public final class VoiceChangerStudioScreen extends Screen {
    private static final int SLIDER_WIDTH = 206;
    private static final int ROW_HEIGHT = 22;
    private static final int CONTENT_TOP = 22;
    private static final int CONTENT_BOTTOM_PADDING = 34;
    private static final String CURRENT_OPTION = "__current__";

    private final Screen parent;
    private final VoiceChangerController controller;
    private final List<StudioProfileSlider> sliders = new ArrayList<>();
    private final List<PositionedWidget> scrollWidgets = new ArrayList<>();
    private String selectedSavedPreset = CURRENT_OPTION;

    private EditBox presetNameField;
    private Button enabledButton;
    private Button selfListenButton;
    private Button builtInPresetButton;
    private Button savedPresetButton;
    private Button autotuneKeyButton;
    private Button autotuneScaleButton;
    private StudioStrengthSlider strengthSlider;
    private Component hoveredHint;
    private int scrollOffset;
    private int maxScroll;

    private static final String[] KEY_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public VoiceChangerStudioScreen(Screen parent, VoiceChangerController controller) {
        super(Component.translatable("svvoicechanger.studio.title"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.sliders.clear();
        this.scrollWidgets.clear();

        StudioLayout layout = new StudioLayout(this.width, this.height);
        initTopControls(layout);
        initSliders(layout);
        updateScrollBounds();
        applyScrollOffset();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose()).bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());

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
        this.hoveredHint = null;
        context.fill(0, 0, this.width, this.height, 0xA0000000);
        renderHeader(context);
        //? if >=26.1 {
        /*super.extractRenderState(context, mouseX, mouseY, delta);
        *///?} else {
        super.render(context, mouseX, mouseY, delta);
        //?}
        renderScrollbar(context);
        updateHoveredHint(mouseX, mouseY);

    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.maxScroll > 0) {
            this.scrollOffset = clamp(this.scrollOffset - (int) Math.round(verticalAmount * ROW_HEIGHT * 2), 0, this.maxScroll);
            applyScrollOffset();
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
        if (this.maxScroll > 0) {
            int step;
            switch (keyCode) {
                case 265 -> step = -ROW_HEIGHT;
                case 264 -> step = ROW_HEIGHT;
                case 266 -> step = -(contentBottom() - CONTENT_TOP);
                case 267 -> step = contentBottom() - CONTENT_TOP;
                default -> step = 0;
            }

            if (step != 0) {
                this.scrollOffset = clamp(this.scrollOffset + step, 0, this.maxScroll);
                applyScrollOffset();
                return true;
            }
        }

        return false;
    }

    private void initTopControls(StudioLayout layout) {
        this.presetNameField = addScrollableWidget(new EditBox(this.font, layout.left(), layout.top(), layout.fieldWidth(), 20, Component.translatable("svvoicechanger.studio.preset_name")), layout.top());
        this.presetNameField.setMaxLength(48);
        this.presetNameField.setValue("MyPreset");

        addScrollableWidget(Button.builder(Component.translatable("svvoicechanger.studio.save"), button -> savePreset()).bounds(layout.left() + layout.fieldWidth() + 6, layout.top(), layout.buttonWidth(), 20).build(), layout.top());

        this.enabledButton = addScrollableWidget(Button.builder(enabledButtonText(), button -> {
            this.controller.setEnabled(!this.controller.isEffectEnabled());
            refreshBooleanButtons();
        }).bounds(layout.left(), layout.top() + 26, layout.topWidth(), 20).build(), layout.top() + 26);
        this.selfListenButton = addScrollableWidget(Button.builder(selfListenButtonText(), button -> {
            this.controller.setSelfListenEnabled(!this.controller.isSelfListenEnabled());
            refreshBooleanButtons();
        }).bounds(layout.right(), layout.top() + 26, SLIDER_WIDTH, 20).build(), layout.top() + 26);

        this.builtInPresetButton = addScrollableWidget(Button.builder(builtInPresetButtonText(), button -> cycleBuiltInPreset())
                .bounds(layout.left(), layout.top() + 52, layout.topWidth(), 20).build(), layout.top() + 52);

        this.savedPresetButton = addScrollableWidget(Button.builder(savedPresetButtonText(), button -> cycleSavedPreset())
                .bounds(layout.right(), layout.top() + 52, SLIDER_WIDTH, 20).build(), layout.top() + 52);

        addScrollableWidget(Button.builder(Component.translatable("svvoicechanger.studio.open_folder"), button -> openFolder()).bounds(layout.left(), layout.top() + 78, layout.topWidth(), 20).build(), layout.top() + 78);
        addScrollableWidget(Button.builder(Component.translatable("svvoicechanger.studio.reload_list"), button -> refreshFromController(true)).bounds(layout.right(), layout.top() + 78, SLIDER_WIDTH, 20).build(), layout.top() + 78);

        Button deleteButton = addScrollableWidget(Button.builder(Component.translatable("svvoicechanger.studio.delete_saved"), button -> deleteSelectedPreset()).bounds(layout.right(), layout.top() + 104, SLIDER_WIDTH, 20).build(), layout.top() + 104);
        deleteButton.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.studio.delete_saved.desc")));

        Button resetButton = addScrollableWidget(Button.builder(Component.translatable("svvoicechanger.studio.reset"), button -> resetToPassthrough()).bounds(layout.left(), layout.top() + 104, layout.topWidth(), 20).build(), layout.top() + 104);
        resetButton.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.studio.reset.desc")));
    }

    private void initSliders(StudioLayout layout) {
        int sliderTop = layout.top() + 132;
        this.strengthSlider = addScrollableWidget(new StudioStrengthSlider(layout.left(), sliderTop, SLIDER_WIDTH, this.controller::getStrength, this.controller::setStrength), sliderTop);

        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT, "svvoicechanger.slider.mix", 0.0D, 1.0D, VoiceChangerProfile::mix, VoiceChangerProfile::withMix);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 2, "svvoicechanger.slider.gain", 0.40D, 2.50D, VoiceChangerProfile::gain, VoiceChangerProfile::withGain);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 3, "svvoicechanger.slider.pitch", 0.60D, 1.70D, VoiceChangerProfile::pitch, VoiceChangerProfile::withPitch);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 4, "svvoicechanger.slider.formant", 0.45D, 2.00D, VoiceChangerProfile::formant, VoiceChangerProfile::withFormant);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 5, "svvoicechanger.slider.low_eq", -10.0D, 10.0D, VoiceChangerProfile::lowEq, VoiceChangerProfile::withLowEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 6, "svvoicechanger.slider.mid_eq", -10.0D, 10.0D, VoiceChangerProfile::midEq, VoiceChangerProfile::withMidEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 7, "svvoicechanger.slider.high_eq", -10.0D, 10.0D, VoiceChangerProfile::highEq, VoiceChangerProfile::withHighEq);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 8, "svvoicechanger.slider.autotune_mix", 0.0D, 1.0D, VoiceChangerProfile::autotuneMix, VoiceChangerProfile::withAutotuneMix);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 9, "svvoicechanger.slider.autotune_strength", 0.0D, 1.0D, VoiceChangerProfile::autotuneStrength, VoiceChangerProfile::withAutotuneStrength);
        addProfileSlider(layout.left(), sliderTop + ROW_HEIGHT * 10, "svvoicechanger.slider.bit_crush", VoiceChangerProfile.BIT_DEPTH_CLEAN, VoiceChangerProfile.BIT_DEPTH_MIN, VoiceChangerProfile::bitDepth, (profile, value) -> profile.withBitDepth((double) Math.round(value)));

        addProfileSlider(layout.right(), sliderTop, "svvoicechanger.slider.distortion", 0.0D, 0.75D, VoiceChangerProfile::distortion, VoiceChangerProfile::withDistortion);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT, "svvoicechanger.slider.noise", 0.0D, 0.60D, VoiceChangerProfile::noise, VoiceChangerProfile::withNoise);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 2, "svvoicechanger.slider.robot_mix", 0.0D, 0.85D, VoiceChangerProfile::robotMix, VoiceChangerProfile::withRobotMix);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 3, "svvoicechanger.slider.robot_frequency", 20.0D, 140.0D, VoiceChangerProfile::robotFrequency, (profile, value) -> profile.withRobotFrequency((int) Math.round(value)));
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 4, "svvoicechanger.slider.echo_mix", 0.0D, 0.65D, VoiceChangerProfile::echoMix, VoiceChangerProfile::withEchoMix);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 5, "svvoicechanger.slider.echo_delay", 40.0D, 260.0D, VoiceChangerProfile::echoDelayMs, (profile, value) -> profile.withEchoDelayMs((int) Math.round(value)));
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 6, "svvoicechanger.slider.echo_feedback", 0.0D, 0.80D, VoiceChangerProfile::echoFeedback, VoiceChangerProfile::withEchoFeedback);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 7, "svvoicechanger.slider.tremolo_depth", 0.0D, 0.75D, VoiceChangerProfile::tremoloDepth, VoiceChangerProfile::withTremoloDepth);
        addProfileSlider(layout.right(), sliderTop + ROW_HEIGHT * 8, "svvoicechanger.slider.tremolo_rate", 0.20D, 9.00D, VoiceChangerProfile::tremoloRate, VoiceChangerProfile::withTremoloRate);

        int autotuneRowY = sliderTop + ROW_HEIGHT * 9;
        int half = (SLIDER_WIDTH - 4) / 2;
        this.autotuneKeyButton = addScrollableWidget(Button.builder(autotuneKeyButtonText(), button -> cycleAutotuneKey())
                .bounds(layout.right(), autotuneRowY, half, 20).build(), autotuneRowY);
        this.autotuneKeyButton.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.slider.autotune_key.desc")));
        this.autotuneScaleButton = addScrollableWidget(Button.builder(autotuneScaleButtonText(), button -> cycleAutotuneScale())
                .bounds(layout.right() + half + 4, autotuneRowY, SLIDER_WIDTH - half - 4, 20).build(), autotuneRowY);
        this.autotuneScaleButton.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.slider.autotune_scale.desc")));
    }

    private void addProfileSlider(int x, int y, String label, double min, double max, Function<VoiceChangerProfile, Number> getter, BiFunction<VoiceChangerProfile, Double, VoiceChangerProfile> setter) {
        StudioProfileSlider slider = new StudioProfileSlider(
                x,
                y,
                SLIDER_WIDTH,
                label,
                label + ".desc",
                min,
                max,
                this.controller::getCurrentProfileSnapshot,
                this::applyCustomProfile,
                getter,
                setter
        );
        this.sliders.add(slider);
        addScrollableWidget(slider, y);
    }

    private <T extends AbstractWidget> T addScrollableWidget(T widget, int baseY) {
        this.scrollWidgets.add(new PositionedWidget(widget, baseY));
        return addRenderableWidget(widget);
    }

    private void updateScrollBounds() {
        int contentEnd = CONTENT_TOP;
        for (PositionedWidget positioned : this.scrollWidgets) {
            contentEnd = Math.max(contentEnd, positioned.baseY() + positioned.widget().getHeight());
        }

        this.maxScroll = Math.max(0, contentEnd - contentBottom());
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void applyScrollOffset() {
        int bottom = contentBottom();
        for (PositionedWidget positioned : this.scrollWidgets) {
            AbstractWidget widget = positioned.widget();
            int y = positioned.baseY() - this.scrollOffset;
            widget.setY(y);

            boolean visible = y + widget.getHeight() >= CONTENT_TOP && y <= bottom;
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private void renderScrollbar(GuiGraphics context) {
        if (this.maxScroll <= 0) {
            return;
        }

        int top = CONTENT_TOP;
        int bottom = contentBottom();
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + this.maxScroll));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + this.scrollOffset * thumbTravel / this.maxScroll;
        int x = this.width - 7;

        context.fill(x, top, x + 3, bottom, 0x60000000);
        context.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFFE0E0E0);
    }

    private int contentBottom() {
        return Math.max(CONTENT_TOP + 24, this.height - CONTENT_BOTTOM_PADDING);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void applyCustomProfile(VoiceChangerProfile profile) {
        this.controller.applyCustomProfile(profile);
        this.selectedSavedPreset = CURRENT_OPTION;
    }

    private void renderHeader(GuiGraphics context) {
    }

    private void updateHoveredHint(int mouseX, int mouseY) {
        if (this.strengthSlider != null && this.strengthSlider.isMouseOver(mouseX, mouseY)) {
            this.hoveredHint = Component.translatable("svvoicechanger.slider.strength.desc");
            return;
        }

        for (StudioProfileSlider slider : this.sliders) {
            if (slider.isMouseOver(mouseX, mouseY)) {
                this.hoveredHint = slider.descriptionText();
                return;
            }
        }
    }

    private void refreshFromController(boolean reloadList) {
        if (reloadList) {
            try {
                this.controller.listSavedPresetNames();
            } catch (IOException exception) {
                notifyUser(Component.translatable("svvoicechanger.message.load_failed", exception.getMessage()));
            }
        }

        VoiceChangerProfile profile = this.controller.getCurrentProfileSnapshot();
        this.selectedSavedPreset = this.controller.getSelectedSavedPresetName() == null ? CURRENT_OPTION : this.controller.getSelectedSavedPresetName();
        refreshBooleanButtons();
        refreshPresetButtons();
        this.strengthSlider.refresh();

        for (StudioProfileSlider slider : this.sliders) {
            slider.refresh(profile);
        }
    }

    private void savePreset() {
        try {
            String savedName = this.controller.saveCurrentPreset(this.presetNameField.getValue());
            this.presetNameField.setValue(savedName);
            this.selectedSavedPreset = savedName;
            refreshFromController(true);
            notifyUser(Component.translatable("svvoicechanger.message.saved", savedName));
        } catch (IOException | IllegalArgumentException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.save_failed", exception.getMessage()));
        }
    }

    private void deleteSelectedPreset() {
        String selected = currentSavedSelection();
        if (CURRENT_OPTION.equals(selected)) {
            notifyUser(Component.translatable("svvoicechanger.message.select_saved_first"));
            return;
        }

        try {
            this.controller.deleteSavedPreset(selected);
            this.selectedSavedPreset = CURRENT_OPTION;
            refreshFromController(true);
            notifyUser(Component.translatable("svvoicechanger.message.deleted", selected));
        } catch (IOException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.delete_failed", exception.getMessage()));
        }
    }

    private void openFolder() {
        try {
            this.controller.ensurePresetDirectory();
            java.awt.Desktop.getDesktop().open(this.controller.getPresetDirectory().toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            notifyUser(Component.translatable("svvoicechanger.message.open_folder_failed", exception.getMessage()));
        }
    }

    private List<String> savedPresetOptions() {
        List<String> options = new ArrayList<>();
        options.add(CURRENT_OPTION);
        options.addAll(this.controller.getSavedPresetNamesCached());
        return options;
    }

    private String currentSavedSelection() {
        return this.selectedSavedPreset == null ? CURRENT_OPTION : this.selectedSavedPreset;
    }

    private Component enabledButtonText() {
        return this.controller.isEffectEnabled()
                ? Component.translatable("svvoicechanger.studio.enabled_on")
                : Component.translatable("svvoicechanger.studio.enabled_off");
    }

    private Component selfListenButtonText() {
        return this.controller.isSelfListenEnabled()
                ? Component.translatable("svvoicechanger.studio.self_listen_on")
                : Component.translatable("svvoicechanger.studio.self_listen_off");
    }

    private void refreshBooleanButtons() {
        if (this.enabledButton != null) {
            this.enabledButton.setMessage(enabledButtonText());
        }
        if (this.selfListenButton != null) {
            this.selfListenButton.setMessage(selfListenButtonText());
        }
    }

    private Component builtInPresetButtonText() {
        return Component.translatable("svvoicechanger.studio.built_in_value", Component.translatable(this.controller.getSelectedPreset().getTranslationKey()));
    }

    private Component savedPresetButtonText() {
        String value = currentSavedSelection();
        Component current = CURRENT_OPTION.equals(value) ? Component.translatable("svvoicechanger.saved.current") : Component.literal(value);
        return Component.translatable("svvoicechanger.studio.saved_value", current);
    }

    private void refreshPresetButtons() {
        if (this.builtInPresetButton != null) {
            this.builtInPresetButton.setMessage(builtInPresetButtonText());
        }
        if (this.savedPresetButton != null) {
            this.savedPresetButton.setMessage(savedPresetButtonText());
        }
        refreshAutotuneButtons();
    }

    private Component autotuneKeyButtonText() {
        int key = ((this.controller.getCurrentProfileSnapshot().autotuneKey() % 12) + 12) % 12;
        return Component.translatable("svvoicechanger.studio.autotune_key_value", KEY_NAMES[key]);
    }

    private Component autotuneScaleButtonText() {
        int scale = this.controller.getCurrentProfileSnapshot().autotuneScale();
        String key = switch (scale) {
            case VoiceChangerProfile.SCALE_MAJOR -> "svvoicechanger.studio.autotune_scale.major";
            case VoiceChangerProfile.SCALE_MINOR -> "svvoicechanger.studio.autotune_scale.minor";
            default -> "svvoicechanger.studio.autotune_scale.chromatic";
        };
        return Component.translatable("svvoicechanger.studio.autotune_scale_value", Component.translatable(key));
    }

    private void refreshAutotuneButtons() {
        if (this.autotuneKeyButton != null) {
            this.autotuneKeyButton.setMessage(autotuneKeyButtonText());
        }
        if (this.autotuneScaleButton != null) {
            this.autotuneScaleButton.setMessage(autotuneScaleButtonText());
        }
    }

    private void cycleAutotuneKey() {
        VoiceChangerProfile profile = this.controller.getCurrentProfileSnapshot();
        applyCustomProfile(profile.withAutotuneKey((profile.autotuneKey() + 1) % 12));
        refreshAutotuneButtons();
    }

    private void cycleAutotuneScale() {
        VoiceChangerProfile profile = this.controller.getCurrentProfileSnapshot();
        int next = (profile.autotuneScale() + 1) % 3;
        applyCustomProfile(profile.withAutotuneScale(next));
        refreshAutotuneButtons();
    }

    private void cycleBuiltInPreset() {
        VoiceChangerPreset[] presets = VoiceChangerPreset.values();
        VoiceChangerPreset current = this.controller.getSelectedPreset();
        int currentIndex = 0;

        for (int i = 0; i < presets.length; i++) {
            if (presets[i] == current) {
                currentIndex = i;
                break;
            }
        }

        for (int step = 1; step <= presets.length; step++) {
            VoiceChangerPreset candidate = presets[(currentIndex + step) % presets.length];
            if (candidate != VoiceChangerPreset.CUSTOM) {
                this.controller.applyBuiltInPreset(candidate);
                this.selectedSavedPreset = CURRENT_OPTION;
                refreshFromController(false);
                return;
            }
        }
    }

    private void cycleSavedPreset() {
        List<String> options = savedPresetOptions();
        if (options.isEmpty()) {
            return;
        }

        String current = currentSavedSelection();
        int currentIndex = Math.max(0, options.indexOf(current));
        String next = options.get((currentIndex + 1) % options.size());
        this.selectedSavedPreset = next;

        if (!CURRENT_OPTION.equals(next)) {
            try {
                this.controller.loadSavedPreset(next);
            } catch (IOException exception) {
                notifyUser(Component.translatable("svvoicechanger.message.load_failed", exception.getMessage()));
            }
        }

        refreshFromController(false);
    }

    private void resetToPassthrough() {
        // Neutral starting point: every effect at zero but a working wet/dry mix,
        // so the voice sounds essentially untouched yet any slider takes effect
        // immediately. For a fully raw mic signal, disable the effect instead.
        this.controller.applyCustomProfile(VoiceChangerProfile.passthrough().withMix(0.90D));
        this.selectedSavedPreset = CURRENT_OPTION;
        refreshFromController(false);
    }

    private void notifyUser(Component message) {
        if (this.minecraft != null && this.minecraft.player != null) {
            //? if >=26.1 {
            /*this.minecraft.player.sendSystemMessage(message);
            *///?} else {
            this.minecraft.player.displayClientMessage(message, false);
            //?}
        }
    }

    private record PositionedWidget(AbstractWidget widget, int baseY) {
    }

    private record StudioLayout(int width, int height) {
        private int top() {
            return 22;
        }

        private int left() {
            return Math.max(16, this.width / 2 - 216);
        }

        private int right() {
            return this.width / 2 + 10;
        }

        private int topWidth() {
            return 202;
        }

        private int fieldWidth() {
            return 132;
        }

        private int buttonWidth() {
            return 64;
        }
    }
}

