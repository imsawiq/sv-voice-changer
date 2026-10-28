package org.sawiq.svvoicechanger.client.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * The window every studio screen sits in, laid out like Plasmo Voice's own
 * settings so the voice changer feels like part of it: the title in the top
 * left corner, a row of tab buttons under it with the open one greyed out, a
 * separator, the scrolling list, another separator, and the footer buttons.
 *
 * <p>Plasmo Voice draws vanilla's menu textures for the list and the
 * separators. Texture drawing changes between Minecraft versions, so the same
 * look is made of plain fills here, which every version draws alike.</p>
 */
final class StudioFrame {
    static final int HEADER_HEIGHT = 62;
    static final int FOOTER_HEIGHT = 33;
    static final int MARGIN = 14;
    static final int WIDGET_HEIGHT = 20;

    private static final int TITLE_Y = 15;
    private static final int TABS_Y = 36;
    private static final int TAB_PADDING = 24;
    private static final int TAB_GAP = 4;
    private static final int SCREEN_DIM = 0x90000000;
    private static final int LIST_DIM = 0x50000000;
    private static final int SEPARATOR_LIGHT = 0x40FFFFFF;
    private static final int SEPARATOR_DARK = 0xC0000000;

    /**
     * One tab in the header row.
     *
     * @param enabled whether it can be opened from here; the open one is never
     */
    record Tab(Component name, String descriptionKey, boolean current, boolean enabled, Runnable open) {
    }

    private StudioFrame() {
    }

    /** The list area, between the header and the footer. */
    static int listTop() {
        return HEADER_HEIGHT;
    }

    static int listBottom(int screenHeight) {
        return Math.max(HEADER_HEIGHT + 24, screenHeight - FOOTER_HEIGHT);
    }

    /** Where the footer's buttons sit. */
    static int footerY(int screenHeight) {
        return screenHeight - FOOTER_HEIGHT + (FOOTER_HEIGHT - WIDGET_HEIGHT) / 2;
    }

    /**
     * Adds the tab buttons, each as wide as its name, the way Plasmo Voice
     * sizes its own. The open tab is a disabled button, as there.
     */
    static void addTabs(List<Tab> tabs, ToIntFunction<Component> textWidth, Consumer<Button> addWidget) {
        int x = MARGIN;
        for (Tab tab : tabs) {
            int width = textWidth.applyAsInt(tab.name()) + TAB_PADDING;
            Button button = Button.builder(tab.name(), ignored -> tab.open().run())
                    .bounds(x, TABS_Y, width, WIDGET_HEIGHT)
                    .build();
            button.active = !tab.current() && tab.enabled();
            if (tab.descriptionKey() != null) {
                button.setTooltip(Tooltip.create(Component.translatable(tab.descriptionKey())));
            }
            addWidget.accept(button);
            x += width + TAB_GAP;
        }
    }

    /**
     * The darkened screen and the list's own shade. Plain fills only: up to
     * 1.21.5 vanilla blurs whatever is drawn before the widgets, which leaves
     * a fill looking the same and would smear a thin line.
     */
    static void paintBackground(StudioGraphics graphics, int width, int height) {
        paintBackground(graphics, width, height, listTop());
    }

    /** @param top where the list starts; a page without tabs has a shorter header */
    static void paintBackground(StudioGraphics graphics, int width, int height, int top) {
        graphics.fill(0, 0, width, height, SCREEN_DIM);
        graphics.fill(0, top, width, listBottom(height), LIST_DIM);
    }

    /** The title and the two separators, drawn over the widgets so nothing blurs them. */
    static void paintChrome(StudioGraphics graphics, int width, int height, Component title) {
        paintChrome(graphics, width, height, title, listTop());
    }

    static void paintChrome(StudioGraphics graphics, int width, int height, Component title, int top) {
        graphics.text(title, MARGIN, TITLE_Y, 0xFFFFFFFF);
        graphics.fill(0, top - 2, width, top - 1, SEPARATOR_DARK);
        graphics.fill(0, top - 1, width, top, SEPARATOR_LIGHT);
        int bottom = listBottom(height);
        graphics.fill(0, bottom, width, bottom + 1, SEPARATOR_LIGHT);
        graphics.fill(0, bottom + 1, width, bottom + 2, SEPARATOR_DARK);
    }
}
