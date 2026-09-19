package org.sawiq.svvoicechanger.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Text drawing across the supported Minecraft versions.
 *
 * <p>The graphics class itself is handled by the Stonecutter replacement that
 * renames it for 26.x, but its text methods were also renamed, and a rename
 * rule cannot express that {@code drawString} and {@code drawCenteredString}
 * became {@code text} and {@code centeredText}. Keeping the switch here rather
 * than adding more global replacements avoids substituting those very common
 * words everywhere in the source.</p>
 */
public final class MinecraftTextAccess {
    private MinecraftTextAccess() {
    }

    public static void draw(GuiGraphics context, Font font, Component text, int x, int y, int argb) {
        //? if >=26.1 {
        /*context.text(font, text, x, y, argb);
        *///?} else {
        context.drawString(font, text, x, y, argb);
        //?}
    }

    public static void drawCentered(GuiGraphics context, Font font, Component text, int centerX, int y, int argb) {
        //? if >=26.1 {
        /*context.centeredText(font, text, centerX, y, argb);
        *///?} else {
        context.drawCenteredString(font, text, centerX, y, argb);
        //?}
    }
}
