package org.sawiq.svvoicechanger.client.ui.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.MinecraftTextAccess;

/**
 * The drawing operations the AI voice screens need, with text going through
 * {@link MinecraftTextAccess} so the screens do not have to know which
 * Minecraft rendering API they are on.
 */
public final class StudioGraphics {
    private final GuiGraphics delegate;
    private final Font font;

    public StudioGraphics(GuiGraphics delegate, Font font) {
        this.delegate = delegate;
        this.font = font;
    }

    /** Fills a rectangle with a packed ARGB colour. */
    public void fill(int left, int top, int right, int bottom, int argb) {
        this.delegate.fill(left, top, right, bottom, argb);
    }

    public void text(Component text, int x, int y, int argb) {
        MinecraftTextAccess.draw(this.delegate, this.font, text, x, y, argb);
    }

    public void centeredText(Component text, int centerX, int y, int argb) {
        MinecraftTextAccess.drawCentered(this.delegate, this.font, text, centerX, y, argb);
    }

    public int textWidth(Component text) {
        return this.font.width(text);
    }

    public int lineHeight() {
        return this.font.lineHeight;
    }
}
