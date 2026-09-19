package org.sawiq.svvoicechanger.client.preset;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.sawiq.svvoicechanger.client.model.VoiceChangerPreset;
import org.sawiq.svvoicechanger.client.model.VoiceChangerState;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Reads and writes preset files.
 *
 * <p>The parameter list is walked from {@link VoiceParameter}, so a new knob
 * needs no change here. Files written by version 1.6 and earlier are migrated
 * on read; see {@link LegacyPresetMigration}.</p>
 */
public final class VoiceChangerPresetStore {
    private static final String EXTENSION = ".properties";
    private static final String AUTOSAVE_NAME = "autosave";

    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SELF_LISTEN = "selfListen";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_STRENGTH = "strength";
    private static final String KEY_SAVED_PRESET_NAME = "savedPresetName";
    private static final String KEY_CONTRIBUTED_VOICE_ID = "contributedVoiceId";

    private final Path directory;
    private final Path autosaveFile;

    public VoiceChangerPresetStore(Path directory) {
        this.directory = directory;
        this.autosaveFile = directory.resolve(AUTOSAVE_NAME + EXTENSION);
    }

    public Path getDirectory() {
        return this.directory;
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(this.directory);
    }

    public List<String> listPresetNames() throws IOException {
        ensureDirectory();

        try (Stream<Path> files = Files.list(this.directory)) {
            return files
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .map(path -> stripExtension(path.getFileName().toString()))
                    .filter(name -> !AUTOSAVE_NAME.equalsIgnoreCase(name))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public void savePreset(String name, VoiceProfile profile) throws IOException {
        ensureDirectory();

        Properties properties = new Properties();
        writeProfile(properties, profile);
        store(pathForName(name), properties);
    }

    public VoiceProfile loadPreset(String name) throws IOException {
        return readProfile(load(pathForName(name)));
    }

    public boolean deletePreset(String name) throws IOException {
        return Files.deleteIfExists(pathForName(name));
    }

    public void saveAutosave(VoiceChangerState state) throws IOException {
        ensureDirectory();

        Properties properties = new Properties();
        writeProfile(properties, state.profile());
        properties.setProperty(KEY_ENABLED, Boolean.toString(state.enabled()));
        properties.setProperty(KEY_SELF_LISTEN, Boolean.toString(state.selfListen()));
        properties.setProperty(KEY_PRESET, state.preset().name());
        properties.setProperty(KEY_STRENGTH, Integer.toString(clampStrength(state.strength())));
        properties.setProperty(KEY_SAVED_PRESET_NAME, state.savedPresetName() == null ? "" : state.savedPresetName());
        properties.setProperty(KEY_CONTRIBUTED_VOICE_ID,
                state.contributedVoiceId() == null ? "" : state.contributedVoiceId());
        store(this.autosaveFile, properties);
    }

    public VoiceChangerState loadAutosaveState() throws IOException {
        if (!Files.exists(this.autosaveFile)) {
            return VoiceChangerState.defaults();
        }

        Properties properties = load(this.autosaveFile);
        String savedName = properties.getProperty(KEY_SAVED_PRESET_NAME);
        String contributedVoiceId = properties.getProperty(KEY_CONTRIBUTED_VOICE_ID);

        return new VoiceChangerState(
                Boolean.parseBoolean(properties.getProperty(KEY_ENABLED, "false")),
                Boolean.parseBoolean(properties.getProperty(KEY_SELF_LISTEN, "false")),
                VoiceChangerPreset.byName(properties.getProperty(KEY_PRESET)),
                clampStrength(parseInt(properties, KEY_STRENGTH, 100)),
                savedName == null || savedName.isBlank() ? null : savedName,
                contributedVoiceId == null || contributedVoiceId.isBlank() ? null : contributedVoiceId,
                readProfile(properties)
        );
    }

    /**
     * @throws IllegalArgumentException if the name is empty or reserved
     */
    public static String sanitizeName(String rawName) {
        String sanitized = rawName.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Preset name cannot be empty");
        }
        if (AUTOSAVE_NAME.equalsIgnoreCase(sanitized)) {
            throw new IllegalArgumentException("The name 'autosave' is reserved");
        }

        return sanitized;
    }

    private static void writeProfile(Properties properties, VoiceProfile profile) {
        for (VoiceParameter parameter : VoiceParameter.values()) {
            properties.setProperty(parameter.id(), Double.toString(profile.get(parameter)));
        }
    }

    private static VoiceProfile readProfile(Properties properties) {
        VoiceProfile.Builder builder = VoiceProfile.builder();
        for (VoiceParameter parameter : VoiceParameter.values()) {
            String raw = properties.getProperty(parameter.id());
            if (raw != null) {
                builder.set(parameter, parseDouble(raw, parameter.defaultValue()));
            }
        }

        LegacyPresetMigration.apply(properties, builder);
        return builder.build();
    }

    private Path pathForName(String rawName) {
        return this.directory.resolve(sanitizeName(rawName) + EXTENSION);
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void store(Path path, Properties properties) throws IOException {
        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "Simple Voice Changer preset");
        }
    }

    private static String stripExtension(String filename) {
        return filename.substring(0, filename.length() - EXTENSION.length());
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseInt(Properties properties, String key, int fallback) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clampStrength(int strength) {
        return Math.max(0, Math.min(100, strength));
    }
}
