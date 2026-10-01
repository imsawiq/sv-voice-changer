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
        drawString(context, font, text, x, y, argb);
        //?}
    }

    public static void drawCentered(GuiGraphics context, Font font, Component text, int centerX, int y, int argb) {
        //? if >=26.1 {
        /*context.centeredText(font, text, centerX, y, argb);
        *///?} else {
        context.drawCenteredString(font, text, centerX, y, argb);
        //?}
    }

    //? if <26.1 {
    /**
     * Minecraft 1.21.6 changed what {@code drawString} returns - it used to
     * hand back the end x, and now returns nothing. The return type is part of
     * the signature the JVM links against, so a direct call compiled against
     * either half of 1.21 fails on the other, and the 1.21.8 build covers both.
     *
     * <p>{@code drawCenteredString} has returned nothing in every 1.21 release,
     * and all it does is shift x left by half the text width before drawing.
     * Shifting right by the same half first lands on exactly the requested x.
     * A reflective lookup by name is not an option: Fabric runs the game under
     * intermediary names, so "drawString" only exists in the dev environment.</p>
     */
    private static void drawString(GuiGraphics context, Font font, Component text, int x, int y, int argb) {
        int halfWidth = font.width(text.getVisualOrderText()) / 2;
        context.drawCenteredString(font, text, x + halfWidth, y, argb);
    }
    //?}
}
