package org.sawiq.svvoicechanger.client.ui;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Opens a web page in the browser, or a folder in Explorer.
 *
 * <p>Java's own desktop integration does not work inside Minecraft: the game
 * runs with AWT in headless mode, so link buttons did nothing and folder
 * buttons crashed the game. The operating system's own opener is used
 * instead. Only the mod's own addresses and folders are ever passed in.</p>
 */
public final class LinkOpener {
    private LinkOpener() {
    }

    public static void open(String url) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command;
        if (os.contains("win")) {
            command = List.of("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            command = List.of("open", url);
        } else {
            command = List.of("xdg-open", url);
        }
        new ProcessBuilder(command).start();
    }
}
