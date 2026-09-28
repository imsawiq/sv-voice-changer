package org.sawiq.svvoicechanger.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
     * hand back the end x, and now returns nothing. The call looks identical
     * in source, but the return type is part of the signature the JVM matches
     * on, so a build compiled against either half of 1.21 fails on the other
     * with a NoSuchMethodError. One build covers all of it, so the method is
     * looked up by name and argument types, which finds it either way.
     *
     * <p>Resolved once when the class loads; drawing a handful of labels a
     * frame through a cached Method costs nothing that shows up.</p>
     */
    private static final Method DRAW_STRING = resolveDrawString();

    private static Method resolveDrawString() {
        try {
            return GuiGraphics.class.getMethod(
                    "drawString", Font.class, Component.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("GuiGraphics has no drawString(Font, Component, int, int, int)", exception);
        }
    }

    private static void drawString(GuiGraphics context, Font font, Component text, int x, int y, int argb) {
        try {
            DRAW_STRING.invoke(context, font, text, x, y, argb);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not draw text", exception);
        }
    }
    //?}
}
