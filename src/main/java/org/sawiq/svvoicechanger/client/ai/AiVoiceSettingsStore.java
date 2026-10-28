package org.sawiq.svvoicechanger.client.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import org.sawiq.svvoicechanger.client.ai.engine.EngineChoice;

/**
 * Reads and writes the AI voice settings and the ElevenLabs key.
 *
 * <p>Both live in a folder of their own next to the preset library rather than
 * inside it: the preset folder is the one players open and share, and a key
 * that ends up in a shared zip is a key somebody else spends credits on.</p>
 */
public final class AiVoiceSettingsStore {
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String KEY_FILE = "elevenlabs.key";
    /** ElevenLabs keys are a few dozen characters; anything far longer is not one. */
    private static final int MAX_KEY_LENGTH = 256;

    private final Path directory;

    public AiVoiceSettingsStore(Path directory) {
        this.directory = directory;
    }

    public Path directory() {
        return this.directory;
    }

    public AiVoiceSettings load() throws IOException {
        Path file = this.directory.resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file)) {
            return AiVoiceSettings.defaults();
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return read(properties);
    }

    public void save(AiVoiceSettings settings) throws IOException {
        Properties properties = new Properties();
        write(properties, settings);

        Files.createDirectories(this.directory);
        Path target = this.directory.resolve(SETTINGS_FILE);
        Path temporary = this.directory.resolve(SETTINGS_FILE + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Voice changer AI voice settings");
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @return the stored key, or an empty string when there is none */
    public String loadKey() throws IOException {
        Path file = this.directory.resolve(KEY_FILE);
        if (!Files.isRegularFile(file) || Files.size(file) > MAX_KEY_LENGTH * 4L) {
            return "";
        }
        return sanitizeKey(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** An empty key deletes the file. */
    public void saveKey(String key) throws IOException {
        Path file = this.directory.resolve(KEY_FILE);
        String sanitized = sanitizeKey(key);
        if (sanitized.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }

        Files.createDirectories(this.directory);
        Files.writeString(file, sanitized, StandardCharsets.UTF_8);
    }

    /**
     * Keeps what can be part of a key. A clipboard paste often carries a line
     * break or the quotes it was copied between, and either would make every
     * request fail with an authentication error that looks like a wrong key.
     */
    public static String sanitizeKey(String raw) {
        if (raw == null) {
            return "";
        }

        StringBuilder key = new StringBuilder();
        for (char character : raw.trim().toCharArray()) {
            if (key.length() >= MAX_KEY_LENGTH) {
                break;
            }
            if (Character.isLetterOrDigit(character) || character == '_' || character == '-') {
                key.append(character);
            }
        }
        return key.toString();
    }

    // --- Mapping -----------------------------------------------------------

    private static AiVoiceSettings read(Properties properties) {
        AiVoiceSettings defaults = AiVoiceSettings.defaults();
        AiVoiceSettings.ElevenLabs elevenDefaults = defaults.elevenLabs();

        AiVoiceSettings.ElevenLabs elevenLabs = new AiVoiceSettings.ElevenLabs(
                properties.getProperty("elevenlabs.voiceId", elevenDefaults.voiceId()),
                properties.getProperty("elevenlabs.voiceName", elevenDefaults.voiceName()),
                properties.getProperty("elevenlabs.modelId", elevenDefaults.modelId()),
                readInt(properties, "elevenlabs.stability", elevenDefaults.stability()),
                readInt(properties, "elevenlabs.similarity", elevenDefaults.similarity()),
                readInt(properties, "elevenlabs.style", elevenDefaults.style()),
                readBoolean(properties, "elevenlabs.speakerBoost", elevenDefaults.speakerBoost()),
                readBoolean(properties, "elevenlabs.removeNoise", elevenDefaults.removeNoise()));

        return new AiVoiceSettings(
                readBoolean(properties, "useAi", defaults.useAi()),
                EngineChoice.byId(properties.getProperty("engine", defaults.engine().id())),
                elevenLabs,
                readLocal(properties, "voiceEngine", defaults.voiceEngine()),
                readLocal(properties, "wokada", defaults.wOkada()));
    }

    private static void write(Properties properties, AiVoiceSettings settings) {
        properties.setProperty("useAi", Boolean.toString(settings.useAi()));
        properties.setProperty("engine", settings.engine().id());

        AiVoiceSettings.ElevenLabs elevenLabs = settings.elevenLabs();
        properties.setProperty("elevenlabs.voiceId", elevenLabs.voiceId());
        properties.setProperty("elevenlabs.voiceName", elevenLabs.voiceName());
        properties.setProperty("elevenlabs.modelId", elevenLabs.modelId());
        properties.setProperty("elevenlabs.stability", Integer.toString(elevenLabs.stability()));
        properties.setProperty("elevenlabs.similarity", Integer.toString(elevenLabs.similarity()));
        properties.setProperty("elevenlabs.style", Integer.toString(elevenLabs.style()));
        properties.setProperty("elevenlabs.speakerBoost", Boolean.toString(elevenLabs.speakerBoost()));
        properties.setProperty("elevenlabs.removeNoise", Boolean.toString(elevenLabs.removeNoise()));

        writeLocal(properties, "voiceEngine", settings.voiceEngine());
        writeLocal(properties, "wokada", settings.wOkada());
    }

    private static AiVoiceSettings.LocalEngine readLocal(Properties properties, String prefix,
                                                         AiVoiceSettings.LocalEngine defaults) {
        return new AiVoiceSettings.LocalEngine(
                properties.getProperty(prefix + ".address", defaults.address()),
                properties.getProperty(prefix + ".model", defaults.modelId()),
                readInt(properties, prefix + ".pitch", defaults.pitch()),
                readInt(properties, prefix + ".chunkMillis", defaults.chunkMillis()));
    }

    private static void writeLocal(Properties properties, String prefix, AiVoiceSettings.LocalEngine engine) {
        properties.setProperty(prefix + ".address", engine.address());
        properties.setProperty(prefix + ".model", engine.modelId());
        properties.setProperty(prefix + ".pitch", Integer.toString(engine.pitch()));
        properties.setProperty(prefix + ".chunkMillis", Integer.toString(engine.chunkMillis()));
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }
}
