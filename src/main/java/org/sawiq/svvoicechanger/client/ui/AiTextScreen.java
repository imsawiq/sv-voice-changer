package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.compat.ClientScreens;
import org.sawiq.svvoicechanger.client.ui.compat.CompatScreen;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * A page of text with one action under it: an engine's setup guide, or what
 * installing the engine involves before the player agrees to it.
 *
 * <p>Sending players to a web page to find out what to do next lost most of
 * them, and the page may not open at all. The text comes from the language
 * files, one paragraph per line break, a "# " line being a heading, and is
 * wrapped to the window here.</p>
 */
public final class AiTextScreen extends CompatScreen {
    /** Below a header of just the title, like the studio's own pages have with their tabs. */
    private static final int HEADER_HEIGHT = 36;
    private static final int TEXT_TOP = HEADER_HEIGHT + 6;
    private static final int FOOTER_HEIGHT = StudioFrame.FOOTER_HEIGHT + 4;
    private static final int MAX_TEXT_WIDTH = 420;
    private static final int LINE_HEIGHT = 11;
    private static final int PARAGRAPH_GAP = 4;
    private static final int WIDGET_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 140;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;

    private final Screen parent;
    private final Component text;
    private final Component actionLabel;
    private final Runnable action;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int contentHeight;

    /**
     * @param action run when the player presses the action button; this screen
     *               returns to {@code parent} afterwards
     */
    public AiTextScreen(Screen parent, Component title, Component text, Component actionLabel, Runnable action) {
        super(title);
        this.parent = parent;
        this.text = text;
        this.actionLabel = actionLabel;
        this.action = action;
    }

    @Override
    protected void init() {
        clearWidgets();
        wrapText();

        int gap = 8;
        int left = this.width / 2 - BUTTON_WIDTH - gap / 2;
        addRenderableWidget(Button.builder(this.actionLabel, button -> {
                    this.action.run();
                    onClose();
                })
                .bounds(left, StudioFrame.footerY(this.height), BUTTON_WIDTH, WIDGET_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), button -> onClose())
                .bounds(left + BUTTON_WIDTH + gap, StudioFrame.footerY(this.height), BUTTON_WIDTH, WIDGET_HEIGHT)
                .build());
    }

    @Override
    public void onClose() {
        ClientScreens.open(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollBy(-(int) Math.round(verticalAmount * LINE_HEIGHT * 3));
        return true;
    }

    @Override
    protected boolean onKeyPressed(int keyCode) {
        int page = viewportHeight() - LINE_HEIGHT;
        int step = switch (keyCode) {
            case KEY_UP -> -LINE_HEIGHT;
            case KEY_DOWN -> LINE_HEIGHT;
            case KEY_PAGE_UP -> -page;
            case KEY_PAGE_DOWN -> page;
            default -> 0;
        };
        if (step == 0) {
            return false;
        }
        scrollBy(step);
        return true;
    }

    @Override
    protected void paintBehindWidgets(StudioGraphics graphics) {
        StudioFrame.paintBackground(graphics, this.width, this.height, HEADER_HEIGHT);
    }

    @Override
    protected void paintAboveWidgets(StudioGraphics graphics, int mouseX, int mouseY) {
        StudioFrame.paintChrome(graphics, this.width, this.height, this.title, HEADER_HEIGHT);

        int left = textLeft();
        int bottom = this.height - FOOTER_HEIGHT;
        for (Line line : this.lines) {
            int y = TEXT_TOP + line.y() - this.scroll;
            if (y >= TEXT_TOP && y + LINE_HEIGHT <= bottom) {
                graphics.text(Component.literal(line.text()), left, y, line.heading() ? 0xFFFFCC55 : 0xFFE0E0E0);
            }
        }

        if (this.contentHeight > viewportHeight()) {
            int track = viewportHeight();
            int thumb = Math.max(18, track * track / this.contentHeight);
            int travel = Math.max(1, track - thumb);
            int thumbY = TEXT_TOP + this.scroll * travel / Math.max(1, this.contentHeight - track);
            graphics.fill(this.width - 7, TEXT_TOP, this.width - 4, bottom, 0x60000000);
            graphics.fill(this.width - 7, thumbY, this.width - 4, thumbY + thumb, 0xFFE0E0E0);
        }
    }

    /** Splits the text into lines that fit the window. */
    private void wrapText() {
        this.lines.clear();
        int maxWidth = textWidth();
        int y = 0;

        for (String paragraph : this.text.getString().split("\n")) {
            boolean heading = paragraph.startsWith("# ");
            String body = heading ? paragraph.substring(2) : paragraph;
            if (heading && y > 0) {
                y += PARAGRAPH_GAP * 2;
            }

            StringBuilder current = new StringBuilder();
            for (String word : body.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && this.font.width(candidate) > maxWidth) {
                    this.lines.add(new Line(current.toString(), y, heading));
                    y += LINE_HEIGHT;
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            this.lines.add(new Line(current.toString(), y, heading));
            y += LINE_HEIGHT + PARAGRAPH_GAP;
        }

        this.contentHeight = y;
        scrollBy(0);
    }

    private void scrollBy(int pixels) {
        int maxScroll = Math.max(0, this.contentHeight - viewportHeight());
        this.scroll = Math.max(0, Math.min(maxScroll, this.scroll + pixels));
    }

    private int viewportHeight() {
        return Math.max(LINE_HEIGHT, this.height - FOOTER_HEIGHT - TEXT_TOP);
    }

    private int textWidth() {
        return Math.min(MAX_TEXT_WIDTH, this.width - 40);
    }

    private int textLeft() {
        return (this.width - textWidth()) / 2;
    }

    private record Line(String text, int y, boolean heading) {
    }
}
