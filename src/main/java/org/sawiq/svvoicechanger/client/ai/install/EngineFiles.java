package org.sawiq.svvoicechanger.client.ai.install;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Where the installed Voice Changer Engine lives, and what the mod downloads
 * to put it there.
 *
 * <p>One install per computer, outside every Minecraft profile: the engine
 * and its packages take a few gigabytes, and a player with five modpacks
 * should not have them five times. Both the Plasmo Voice and the Simple Voice
 * Chat build use the same folder.</p>
 */
public final class EngineFiles {
    /** The engine release the mod installs, checked against this hash. */
    static final String ENGINE_VERSION = "1.0.0";
    static final String ENGINE_URL = "https://github.com/imsawiq/voice-changer-engine/releases/download/v"
            + ENGINE_VERSION + "/voice-changer-engine-" + ENGINE_VERSION + ".zip";
    static final String ENGINE_SHA256 = "6d243451c96b79779a122ac291676bb5502023285f53846a79c5c66cd44c9028";

    /** uv installs Python and the engine's packages without the player installing anything. */
    static final String UV_URL = "https://github.com/astral-sh/uv/releases/download/0.12.24/uv-x86_64-pc-windows-msvc.zip";
    static final String UV_SHA256 = "7c38608c8a18ee137d748a1773053b07ec8f3a30fab49aebaa6f4e4efeceb019";

    private EngineFiles() {
    }

    /** Automatic install is Windows on x64 only: the engine's GPU builds exist for nothing else yet. */
    public static boolean isSupported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return os.contains("win") && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    public static Path root() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData)
                : Path.of(System.getProperty("user.home"), "AppData", "Local");
        return base.resolve("VoiceChangerEngine");
    }

    static Path uvExecutable() {
        return root().resolve("uv").resolve("uv.exe");
    }

    static Path engineDirectory() {
        return root().resolve("engine");
    }

    /** The windowless Python, so starting the engine does not pop up a console. */
    static Path python() {
        return engineDirectory().resolve(".venv").resolve("Scripts").resolve("pythonw.exe");
    }

    /** Where players put the recordings a new voice is trained from, one folder per voice. */
    public static Path datasetsDirectory() {
        return playerDirectory().resolve("datasets");
    }

    /**
     * Where the player's voices live. The engine is started with this folder,
     * so each instance has its own voices while the engine itself is
     * installed once.
     */
    public static Path modelsDirectory() {
        return playerDirectory().resolve("voices");
    }

    /**
     * The player's own files sit in the game folder, next to the rest of the
     * instance, where players look for files they added, and removing the
     * engine leaves them alone.
     */
    private static Path playerDirectory() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().resolve("voice-changer");
    }

    /** The engine's own models folder, from its archive; kept across updates. */
    static Path bundledModelsDirectory() {
        return engineDirectory().resolve("models");
    }

    static Path marker() {
        return root().resolve("installed.properties");
    }

    /** Present while an install the player agreed to has not finished. */
    static Path pendingInstallMarker() {
        return root().resolve("install-pending");
    }

    static Path logDirectory() {
        return root().resolve("logs");
    }
}
