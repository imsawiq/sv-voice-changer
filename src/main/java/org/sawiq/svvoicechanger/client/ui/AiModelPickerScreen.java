package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.ui.compat.CompatScreen;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * A searchable list to pick one voice or model from.
 *
 * <p>An ElevenLabs account can hold a hundred voices and cycling through them
 * one click at a time is no way to find one, so the AI tab opens this
 * instead. Typing filters by name and by the description, which for
 * ElevenLabs carries gender, accent and age.</p>
 */
public final class AiModelPickerScreen extends CompatScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int WIDGET_HEIGHT = 20;
    private static final int LIST_TOP = 58;
    private static final int MAX_WIDTH = 300;

    private final Screen parent;
    private List<EngineModel> models;
    private String selectedId;
    private final Consumer<EngineModel> onPick;
    private final ScrollPanel scrollPanel = new ScrollPanel();
    private final List<Button> listButtons = new ArrayList<>();

    private final List<ExtraButton> extraButtons = new ArrayList<>();
    private Consumer<EngineModel> preview;
    private Supplier<List<EngineModel>> liveModels;
    private Supplier<String> liveSelectedId;
    private Component emptyText = Component.translatable("svvoicechanger.ai.picker.empty");

    private EditBox searchField;
    private String query = "";

    private static final int PLAY_WIDTH = 24;

    private record ExtraButton(Component label, Runnable action) {
    }

    public AiModelPickerScreen(Screen parent, Component title, List<EngineModel> models, String selectedId,
                               Consumer<EngineModel> onPick) {
        super(title);
        this.parent = parent;
        this.models = List.copyOf(models);
        this.selectedId = selectedId;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        this.listButtons.clear();

        int width = listWidth();
        this.searchField = new EditBox(this.font, (this.width - width) / 2, 30, width, WIDGET_HEIGHT,
                Component.translatable("svvoicechanger.ai.picker.search"));
        this.searchField.setMaxLength(64);
        this.searchField.setValue(this.query);
        this.searchField.setResponder(text -> {
            this.query = text;
            rebuildList();
        });
        addRenderableWidget(this.searchField);
        setInitialFocus(this.searchField);

        buildFooter();
        rebuildList();
    }

    /** The extra actions, then Back, in one row under the list. */
    private void buildFooter() {
        int count = this.extraButtons.size() + 1;
        int gap = 6;
        int buttonWidth = Math.min(130, (this.width - 20 - gap * (count - 1)) / count);
        int x = (this.width - (buttonWidth * count + gap * (count - 1))) / 2;
        for (ExtraButton extra : this.extraButtons) {
            addRenderableWidget(Button.builder(extra.label(), button -> extra.action().run())
                    .bounds(x, StudioFrame.footerY(this.height), buttonWidth, WIDGET_HEIGHT)
                    .build());
            x += buttonWidth + gap;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(x, StudioFrame.footerY(this.height), buttonWidth, WIDGET_HEIGHT)
                .build());
    }

    /** Adds a play button to every row, for engines that can play a sample of a voice. */
    public AiModelPickerScreen withPreview(Consumer<EngineModel> preview) {
        this.preview = preview;
        return this;
    }

    /**
     * Keeps the list in step with the engine while the screen is open, so a
     * voice dropped into the folder shows up without leaving and coming back.
     */
    public AiModelPickerScreen withLiveModels(Supplier<List<EngineModel>> models, Supplier<String> selectedId) {
        this.liveModels = models;
        this.liveSelectedId = selectedId;
        return this;
    }

    /** What to say while there is nothing to pick, such as where voices come from. */
    public AiModelPickerScreen withEmptyText(Component text) {
        this.emptyText = text;
        return this;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.liveModels == null) {
            return;
        }
        List<EngineModel> current = this.liveModels.get();
        String selected = this.liveSelectedId.get();
        if (!current.equals(this.models) || !selected.equals(this.selectedId)) {
            this.models = List.copyOf(current);
            this.selectedId = selected;
            rebuildList();
        }
    }

    /** Adds an action under the list, such as opening the folder new voices go into. */
    public AiModelPickerScreen withExtraButton(Component label, Runnable action) {
        this.extraButtons.add(new ExtraButton(label, action));
        return this;
    }

    /** Swaps only the list, so the search field keeps its focus and cursor while typing. */
    private void rebuildList() {
        for (Button button : this.listButtons) {
            removeWidget(button);
        }
        this.listButtons.clear();
        this.scrollPanel.clear();

        int width = listWidth();
        int left = (this.width - width) / 2;
        List<EngineModel> matches = filtered();
        for (int i = 0; i < matches.size(); i++) {
            EngineModel model = matches.get(i);
            Component label = model.id().equals(this.selectedId)
                    ? Component.translatable("svvoicechanger.studio.preset_selected", model.name())
                    : Component.literal(model.name());
            int y = LIST_TOP + i * ROW_HEIGHT;
            int pickWidth = this.preview != null ? width - PLAY_WIDTH - 4 : width;
            Button button = Button.builder(label, ignored -> pick(model))
                    .bounds(left, y, pickWidth, WIDGET_HEIGHT)
                    .build();
            if (!model.description().isBlank()) {
                button.setTooltip(Tooltip.create(Component.literal(model.description())));
            }
            this.scrollPanel.add(button);
            this.listButtons.add(addRenderableWidget(button));

            if (this.preview != null) {
                Button play = Button.builder(Component.literal("▶"), ignored -> this.preview.accept(model))
                        .bounds(left + pickWidth + 4, y, PLAY_WIDTH, WIDGET_HEIGHT)
                        .build();
                play.setTooltip(Tooltip.create(Component.translatable("svvoicechanger.ai.picker.play")));
                this.scrollPanel.add(play);
                this.listButtons.add(addRenderableWidget(play));
            }
        }

        this.scrollPanel.layout(LIST_TOP, StudioFrame.listBottom(this.height));
    }

    private int listWidth() {
        return Math.min(MAX_WIDTH, this.width - 40);
    }

    @Override
    public void onClose() {
        ClientScreens.open(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollPanel.scrollBy(-(int) Math.round(verticalAmount * ROW_HEIGHT * 2))) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        StudioFrame.paintBackground(graphics, this.width, this.height, LIST_TOP - 4);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        StudioFrame.paintChrome(graphics, this.width, this.height, this.title, LIST_TOP - 4);
        this.scrollPanel.renderScrollbar(graphics, this.width - 7);

        if (this.models.isEmpty()) {
            int width = listWidth();
            StudioPaint.paragraph(graphics, this.emptyText, (this.width - width) / 2, LIST_TOP + 6, width, 4,
                    StudioPaint.GREY);
        } else if (filtered().isEmpty()) {
            graphics.centeredText(Component.translatable("svvoicechanger.ai.picker.no_match"),
                    this.width / 2, LIST_TOP + 6, StudioPaint.GREY);
        }
    }

    private List<EngineModel> filtered() {
        String needle = this.query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return this.models;
        }

        List<EngineModel> matches = new ArrayList<>();
        for (EngineModel model : this.models) {
            if (model.name().toLowerCase(Locale.ROOT).contains(needle)
                    || model.description().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(model);
            }
        }
        return matches;
    }

    private void pick(EngineModel model) {
        this.onPick.accept(model);
        onClose();
    }
}
