package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Vertical scrolling for a list of absolutely positioned widgets.
 *
 * <p>Widgets keep the position they were laid out at; the panel offsets them
 * and hides the ones that fall outside the viewport. Hidden widgets are also
 * deactivated, otherwise a click near the edge of the screen would land on a
 * slider scrolled out of view.</p>
 */
public final class ScrollPanel {
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int MINIMUM_THUMB_HEIGHT = 18;
    private static final int TRACK_COLOR = 0x60000000;
    private static final int THUMB_COLOR = 0xFFE0E0E0;

    private final List<Entry> entries = new ArrayList<>();
    private int top;
    private int bottom;
    private int scrollOffset;
    private int maxScroll;

    public void clear() {
        this.entries.clear();
        this.maxScroll = 0;
    }

    /** Registers a widget at the position it was constructed with. */
    public <T extends AbstractWidget> T add(T widget) {
        this.entries.add(new Entry(widget, widget.getY()));
        return widget;
    }

    /** Sets the viewport and recomputes how far the content can scroll. */
    public void layout(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;

        int contentEnd = top;
        for (Entry entry : this.entries) {
            contentEnd = Math.max(contentEnd, entry.baseY + entry.widget.getHeight());
        }

        this.maxScroll = Math.max(0, contentEnd - bottom);
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll);
        apply();
    }

    public boolean isScrollable() {
        return this.maxScroll > 0;
    }

    /** @return true when the panel consumed the scroll */
    public boolean scrollBy(int pixels) {
        if (this.maxScroll <= 0) {
            return false;
        }

        int updated = clamp(this.scrollOffset + pixels, 0, this.maxScroll);
        if (updated == this.scrollOffset) {
            return true;
        }

        this.scrollOffset = updated;
        apply();
        return true;
    }

    /** Pixels the content is currently scrolled by; painted overlays must match. */
    public int scrollOffset() {
        return this.scrollOffset;
    }

    public int viewportHeight() {
        return Math.max(1, this.bottom - this.top);
    }

    public void renderScrollbar(GuiGraphics context, int rightEdge) {
        if (this.maxScroll <= 0) {
            return;
        }

        int trackHeight = viewportHeight();
        int thumbHeight = Math.max(MINIMUM_THUMB_HEIGHT, trackHeight * trackHeight / (trackHeight + this.maxScroll));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = this.top + this.scrollOffset * thumbTravel / this.maxScroll;

        context.fill(rightEdge, this.top, rightEdge + SCROLLBAR_WIDTH, this.bottom, TRACK_COLOR);
        context.fill(rightEdge, thumbY, rightEdge + SCROLLBAR_WIDTH, thumbY + thumbHeight, THUMB_COLOR);
    }

    private void apply() {
        for (Entry entry : this.entries) {
            AbstractWidget widget = entry.widget;
            int y = entry.baseY - this.scrollOffset;
            widget.setY(y);

            boolean visible = y + widget.getHeight() >= this.top && y <= this.bottom;
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Entry(AbstractWidget widget, int baseY) {
    }
}
