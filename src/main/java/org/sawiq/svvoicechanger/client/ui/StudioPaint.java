package org.sawiq.svvoicechanger.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.ui.compat.StudioGraphics;

/**
 * What the studio screens draw besides widgets: progress bars, text wrapped
 * to a width, and lines cut to a width. Everything goes through
 * {@link StudioGraphics}, the one class each Minecraft version adapts.
 */
final class StudioPaint {
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = 0xFFE0E0E0;
    static final int GREY = 0xFFA0A0A0;
    static final int GREEN = 0xFF66DD66;
    static final int YELLOW = 0xFFFFCC55;
    static final int RED = 0xFFFF7777;
    static final int BLUE = 0xFF5599FF;
    static final int LINE_HEIGHT = 10;

    private static final int BAR_TRACK = 0xFF202020;

    private StudioPaint() {
    }

    /** @param fraction 0 to 1; values outside are clamped */
    static void bar(StudioGraphics graphics, int x, int y, int width, int height, double fraction, int color) {
        double clamped = Math.max(0.0D, Math.min(1.0D, fraction));
        graphics.fill(x, y, x + width, y + height, BAR_TRACK);
        graphics.fill(x, y, x + (int) Math.round(width * clamped), y + height, color);
    }

    /** A block sliding across the track, for work whose total is not known; one pass every 1.6 seconds. */
    static void sweep(StudioGraphics graphics, int x, int y, int width, int height, long nowMillis, int color) {
        int block = Math.max(8, width / 4);
        int travel = width + block;
        int start = x - block + (int) (nowMillis % 1600L * travel / 1600L);
        graphics.fill(x, y, x + width, y + height, BAR_TRACK);
        graphics.fill(Math.max(x, start), y, Math.min(x + width, start + block), y + height, color);
    }

    /** Draws at most {@code maxLines} lines of text wrapped to a width; the last one ends in "..." if cut. */
    static void paragraph(StudioGraphics graphics, Component text, int x, int y, int width, int maxLines, int color) {
        List<String> lines = wrap(graphics, text.getString(), width);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) {
                line(graphics, Component.literal(line + " " + lines.get(i + 1)), x, y, width, color);
            } else {
                graphics.text(Component.literal(line), x, y, color);
            }
            y += LINE_HEIGHT;
        }
    }

    /** One line cut to the width with an ellipsis, for paths and names that may be any length. */
    static void line(StudioGraphics graphics, Component text, int x, int y, int width, int color) {
        if (graphics.textWidth(text) <= width) {
            graphics.text(text, x, y, color);
            return;
        }
        String plain = text.getString();
        while (!plain.isEmpty() && graphics.textWidth(Component.literal(plain + "...")) > width) {
            plain = plain.substring(0, plain.length() - 1);
        }
        graphics.text(Component.literal(plain + "..."), x, y, color);
    }

    private static List<String> wrap(StudioGraphics graphics, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (!current.isEmpty() && graphics.textWidth(Component.literal(candidate)) > width) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }
}
