package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

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

    public void renderScrollbar(StudioGraphics graphics, int rightEdge) {
        if (this.maxScroll <= 0) {
            return;
        }

        int trackHeight = viewportHeight();
        int thumbHeight = Math.max(MINIMUM_THUMB_HEIGHT, trackHeight * trackHeight / (trackHeight + this.maxScroll));
        int thumbTravel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = this.top + this.scrollOffset * thumbTravel / this.maxScroll;

        graphics.fill(rightEdge, this.top, rightEdge + SCROLLBAR_WIDTH, this.bottom, TRACK_COLOR);
        graphics.fill(rightEdge, thumbY, rightEdge + SCROLLBAR_WIDTH, thumbY + thumbHeight, THUMB_COLOR);
    }

    /**
     * Hides what scrolled out of view and brings back only what this panel
     * hid itself: a widget the screen hid or greyed out on purpose, such as a
     * control the server locked, stays that way when it scrolls back in.
     */
    private void apply() {
        for (Entry entry : this.entries) {
            AbstractWidget widget = entry.widget;
            int y = entry.baseY - this.scrollOffset;
            widget.setY(y);

            // Only whole widgets: a half-scrolled one would hang over the header or footer,
            // and cutting it off takes drawing that differs between Minecraft versions.
            boolean inView = y >= this.top && y + widget.getHeight() <= this.bottom;
            if (!inView && !entry.hiddenByScroll) {
                entry.hiddenByScroll = true;
                entry.wasVisible = widget.visible;
                entry.wasActive = widget.active;
                widget.visible = false;
                widget.active = false;
            } else if (inView && entry.hiddenByScroll) {
                entry.hiddenByScroll = false;
                widget.visible = entry.wasVisible;
                widget.active = entry.wasActive;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Entry {
        final AbstractWidget widget;
        final int baseY;
        boolean hiddenByScroll;
        boolean wasVisible;
        boolean wasActive;

        Entry(AbstractWidget widget, int baseY) {
            this.widget = widget;
            this.baseY = baseY;
        }
    }
}
