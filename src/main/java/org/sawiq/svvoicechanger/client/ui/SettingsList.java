package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * The rows of a studio screen, laid out like Plasmo Voice's settings list:
 * section titles centred in a row of their own, then options with the label
 * on the left and the control on the right, all 24 pixels tall and 303 wide.
 *
 * <p>Rows are added top to bottom while the screen builds its widgets; the
 * list places each widget, scrolls them together, and draws the labels and
 * text that go with them.</p>
 */
final class SettingsList {
    static final int ROW_HEIGHT = 24;
    static final int CONTROL_WIDTH = 124;
    static final int WIDGET_HEIGHT = 20;

    private static final int MAX_WIDTH = 303;
    private static final int GAP = 4;
    private static final int TOP_PADDING = 4;
    private static final int LABEL_Y = (ROW_HEIGHT - 8) / 2;

    private final ScrollPanel scroll = new ScrollPanel();
    private final List<Painted> painted = new ArrayList<>();
    private final Consumer<AbstractWidget> addWidget;
    private final int left;
    private final int width;
    private int y = StudioFrame.listTop() + TOP_PADDING;
    private int bottom;

    /** Something drawn in a row: a label, a title, text, or a custom picture such as a bar. */
    @FunctionalInterface
    interface Painter {
        void paint(StudioGraphics graphics, int x, int y, int width);
    }

    private record Painted(int y, int height, Painter painter) {
    }

    /** @param addWidget adds a widget to the screen, so it is drawn and clicked */
    SettingsList(int screenWidth, Consumer<AbstractWidget> addWidget) {
        this.addWidget = addWidget;
        this.width = Math.min(MAX_WIDTH, screenWidth - 2 * StudioFrame.MARGIN);
        this.left = (screenWidth - this.width) / 2;
    }

    int left() {
        return this.left;
    }

    int width() {
        return this.width;
    }

    /** A section title, centred. */
    void category(Component title) {
        int rowY = this.y;
        this.painted.add(new Painted(rowY, ROW_HEIGHT, (graphics, x, top, width) ->
                graphics.centeredText(title, x + width / 2, top + LABEL_Y, StudioPaint.WHITE)));
        this.y += ROW_HEIGHT;
    }

    /** A label on the left and a control on the right, the control sized to the list's control column. */
    <T extends AbstractWidget> T option(Component label, T control) {
        return option(() -> label, control);
    }

    <T extends AbstractWidget> T option(Supplier<Component> label, T control) {
        int labelWidth = this.width - CONTROL_WIDTH - GAP;
        this.painted.add(new Painted(this.y, ROW_HEIGHT, (graphics, x, top, width) ->
                StudioPaint.line(graphics, label.get(), x, top + LABEL_Y, labelWidth, StudioPaint.WHITE)));
        place(control, this.left + this.width - CONTROL_WIDTH, CONTROL_WIDTH);
        this.y += ROW_HEIGHT;
        return control;
    }

    /**
     * Widgets side by side across the whole row, sharing its width equally. A
     * null leaves its cell empty, so a short last row of a grid keeps the
     * same cell width as the rows above it.
     */
    void row(AbstractWidget... widgets) {
        int each = (this.width - GAP * (widgets.length - 1)) / widgets.length;
        for (int i = 0; i < widgets.length; i++) {
            if (widgets[i] != null) {
                place(widgets[i], this.left + i * (each + GAP), each);
            }
        }
        this.y += ROW_HEIGHT;
    }

    /**
     * Text across the row, wrapped, in a block of a fixed number of lines so
     * the rows under it do not move when the text changes.
     */
    void text(Supplier<Component> text, IntSupplier color, int lines) {
        int height = lines * StudioPaint.LINE_HEIGHT + 4;
        this.painted.add(new Painted(this.y, height, (graphics, x, top, width) ->
                StudioPaint.paragraph(graphics, text.get(), x, top + 2, width, lines, color.getAsInt())));
        this.y += height;
    }

    /** A row of the given height drawn by the caller, such as a meter or a progress bar. */
    void custom(int height, Painter painter) {
        this.painted.add(new Painted(this.y, height, painter));
        this.y += height;
    }

    void gap(int pixels) {
        this.y += pixels;
    }

    /** Ends the layout; called once every row is in. */
    void finish(int screenHeight) {
        this.bottom = StudioFrame.listBottom(screenHeight);
        this.scroll.layout(StudioFrame.listTop(), this.bottom);
    }

    boolean scrollBy(int pixels) {
        return this.scroll.scrollBy(pixels);
    }

    boolean isScrollable() {
        return this.scroll.isScrollable();
    }

    int viewportHeight() {
        return this.scroll.viewportHeight();
    }

    /** Labels, titles and text of the rows in view, then the scrollbar beside the list. */
    void paint(StudioGraphics graphics) {
        int offset = this.scroll.scrollOffset();
        for (Painted row : this.painted) {
            int top = row.y() - offset;
            if (top >= StudioFrame.listTop() && top + row.height() <= this.bottom + 2) {
                row.painter().paint(graphics, this.left, top, this.width);
            }
        }
        this.scroll.renderScrollbar(graphics, this.left + this.width + 6);
    }

    private void place(AbstractWidget widget, int x, int widgetWidth) {
        widget.setX(x);
        widget.setY(this.y + (ROW_HEIGHT - WIDGET_HEIGHT) / 2);
        widget.setWidth(widgetWidth);
        this.scroll.add(widget);
        this.addWidget.accept(widget);
    }
}
