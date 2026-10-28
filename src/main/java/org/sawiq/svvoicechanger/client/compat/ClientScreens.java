package org.sawiq.svvoicechanger.client.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.MinecraftScreenAccess;

/**
 * The client calls the AI voice screens need, with the version differences
 * resolved here so those screens read the same as in the Plasmo Voice build.
 */
public final class ClientScreens {
    private ClientScreens() {
    }

    /** The screen currently displayed, or {@code null} when in-game. */
    public static Screen current() {
        return MinecraftScreenAccess.current(Minecraft.getInstance());
    }

    public static void open(Screen screen) {
        MinecraftScreenAccess.show(Minecraft.getInstance(), screen);
    }

    /** Writes a line into the chat log. No-op when no player is loaded. */
    public static void sendChatMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        //? if >=26.1 {
        /*client.player.sendSystemMessage(message);
        *///?} else {
        client.player.displayClientMessage(message, false);
        //?}
    }

    /** Writes a line above the hotbar. No-op when no player is loaded. */
    public static void sendActionBarMessage(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        //? if >=26.1 {
        /*client.player.sendOverlayMessage(message);
        *///?} else {
        client.player.displayClientMessage(message, true);
        //?}
    }

    /** What the system clipboard holds as text, empty when it holds none. */
    public static String clipboardText() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }
}
