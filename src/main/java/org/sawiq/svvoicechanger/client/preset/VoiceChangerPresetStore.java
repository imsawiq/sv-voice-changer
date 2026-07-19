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
import org.sawiq.svvoicechanger.client.model.VoiceChangerProfile;
import org.sawiq.svvoicechanger.client.model.VoiceChangerState;

public final class VoiceChangerPresetStore {
    private static final String EXTENSION = ".properties";

    private final Path directory;
    private final Path autosaveFile;

    public VoiceChangerPresetStore(Path directory) {
        this.directory = directory;
        this.autosaveFile = directory.resolve("autosave" + EXTENSION);
    }

    public Path getDirectory() {
        return this.directory;
    }

    public void ensureDirectory() throws IOException {
        Files.createDirectories(this.directory);
    }

    public List<String> listPresetNames() throws IOException {
        ensureDirectory();

        try (Stream<Path> stream = Files.list(this.directory)) {
            return stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(EXTENSION))
                    .map(path -> stripExtension(path.getFileName().toString()))
                    .filter(name -> !"autosave".equalsIgnoreCase(name))
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
    }

    public void savePreset(String name, VoiceChangerProfile profile) throws IOException {
        save(pathForName(name), profile);
    }

    public VoiceChangerProfile loadPreset(String name) throws IOException {
        return load(pathForName(name));
    }

    public boolean deletePreset(String name) throws IOException {
        return Files.deleteIfExists(pathForName(name));
    }

    public void saveAutosave(VoiceChangerProfile profile) throws IOException {
        saveAutosave(new VoiceChangerState(false, false, VoiceChangerPreset.CUSTOM, 100, null, profile));
    }

    public VoiceChangerState loadAutosaveState() throws IOException {
        if (!Files.exists(this.autosaveFile)) {
            return VoiceChangerState.defaults();
        }

        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(this.autosaveFile)) {
            properties.load(input);
        }

        VoiceChangerPreset preset = parsePreset(properties.getProperty("preset"));
        int strength = parseInt(properties, "strength", 100);
        boolean enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
        boolean selfListen = Boolean.parseBoolean(properties.getProperty("selfListen", "false"));
        String savedPresetName = emptyToNull(properties.getProperty("savedPresetName"));

        return new VoiceChangerState(
                enabled,
                selfListen,
                preset,
                clampInt(strength, 0, 100),
                savedPresetName,
                parseProfile(properties)
        );
    }

    public void saveAutosave(VoiceChangerState state) throws IOException {
        save(this.autosaveFile, state);
    }

    public VoiceChangerProfile loadAutosave() throws IOException {
        return loadAutosaveState().profile();
    }

    public static String sanitizeName(String rawName) {
        String sanitized = rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ");

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Preset name cannot be empty");
        }

        if ("autosave".equalsIgnoreCase(sanitized)) {
            throw new IllegalArgumentException("The name 'autosave' is reserved");
        }

        return sanitized;
    }

    private void save(Path path, VoiceChangerProfile profile) throws IOException {
        save(path, new VoiceChangerState(false, false, VoiceChangerPreset.CUSTOM, 100, null, profile));
    }

    private void save(Path path, VoiceChangerState state) throws IOException {
        ensureDirectory();

        Properties properties = new Properties();
        VoiceChangerProfile profile = sanitizeProfile(state.profile());

        properties.setProperty("enabled", Boolean.toString(state.enabled()));
        properties.setProperty("selfListen", Boolean.toString(state.selfListen()));
        properties.setProperty("preset", state.preset().name());
        properties.setProperty("strength", Integer.toString(clampInt(state.strength(), 0, 100)));
        properties.setProperty("savedPresetName", state.savedPresetName() == null ? "" : state.savedPresetName());
        properties.setProperty("mix", Double.toString(profile.mix()));
        properties.setProperty("gain", Double.toString(profile.gain()));
        properties.setProperty("pitch", Double.toString(profile.pitch()));
        properties.setProperty("formant", Double.toString(profile.formant()));
        properties.setProperty("distortion", Double.toString(profile.distortion()));
        properties.setProperty("robotMix", Double.toString(profile.robotMix()));
        properties.setProperty("robotFrequency", Integer.toString(profile.robotFrequency()));
        properties.setProperty("echoMix", Double.toString(profile.echoMix()));
        properties.setProperty("echoDelayMs", Integer.toString(profile.echoDelayMs()));
        properties.setProperty("echoFeedback", Double.toString(profile.echoFeedback()));
        properties.setProperty("tremoloDepth", Double.toString(profile.tremoloDepth()));
        properties.setProperty("tremoloRate", Double.toString(profile.tremoloRate()));
        properties.setProperty("lowEq", Double.toString(profile.lowEq()));
        properties.setProperty("midEq", Double.toString(profile.midEq()));
        properties.setProperty("highEq", Double.toString(profile.highEq()));
        properties.setProperty("noise", Double.toString(profile.noise()));
        properties.setProperty("autotuneMix", Double.toString(profile.autotuneMix()));
        properties.setProperty("autotuneStrength", Double.toString(profile.autotuneStrength()));
        properties.setProperty("autotuneKey", Integer.toString(profile.autotuneKey()));
        properties.setProperty("autotuneScale", Integer.toString(profile.autotuneScale()));
        properties.setProperty("bitDepth", Double.toString(profile.bitDepth()));

        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, "Simple Voice Voice Changer preset");
        }
    }

    private VoiceChangerProfile load(Path path) throws IOException {
        Properties properties = new Properties();

        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }

        return parseProfile(properties);
    }

    private Path pathForName(String rawName) {
        return this.directory.resolve(sanitizeName(rawName) + EXTENSION);
    }

    private static double parseDouble(Properties properties, String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int parseInt(Properties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static VoiceChangerProfile parseProfile(Properties properties) {
        return new VoiceChangerProfile(
                safeDouble(properties, "mix", 0.90D, 0.0D, 1.0D),
                safeDouble(properties, "gain", 1.00D, 0.40D, 2.50D),
                safeDouble(properties, "pitch", 1.00D, 0.55D, 2.20D),
                safeDouble(properties, "formant", 1.00D, 0.45D, 2.00D),
                safeDouble(properties, "distortion", 0.00D, 0.0D, 0.75D),
                safeDouble(properties, "robotMix", 0.00D, 0.0D, 0.85D),
                clampInt(parseInt(properties, "robotFrequency", 60), 20, 140),
                safeDouble(properties, "echoMix", 0.00D, 0.0D, 0.65D),
                clampInt(parseInt(properties, "echoDelayMs", 120), 40, 260),
                safeDouble(properties, "echoFeedback", 0.10D, 0.0D, 0.80D),
                safeDouble(properties, "tremoloDepth", 0.00D, 0.0D, 0.75D),
                safeDouble(properties, "tremoloRate", 2.50D, 0.20D, 9.00D),
                safeDouble(properties, "lowEq", 0.00D, -10.0D, 10.0D),
                safeDouble(properties, "midEq", 0.00D, -10.0D, 10.0D),
                safeDouble(properties, "highEq", 0.00D, -10.0D, 10.0D),
                safeDouble(properties, "noise", 0.00D, 0.0D, 0.60D),
                safeDouble(properties, "autotuneMix", 0.00D, 0.0D, 1.0D),
                safeDouble(properties, "autotuneStrength", 0.60D, 0.0D, 1.0D),
                clampInt(parseInt(properties, "autotuneKey", 0), 0, 11),
                clampInt(parseInt(properties, "autotuneScale", 0), 0, 2),
                safeDouble(properties, "bitDepth", VoiceChangerProfile.BIT_DEPTH_CLEAN, VoiceChangerProfile.BIT_DEPTH_MIN, VoiceChangerProfile.BIT_DEPTH_CLEAN)
        );
    }

    private static VoiceChangerPreset parsePreset(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return VoiceChangerPreset.CUSTOM;
        }

        try {
            return VoiceChangerPreset.valueOf(rawValue);
        } catch (IllegalArgumentException ignored) {
            return VoiceChangerPreset.CUSTOM;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static VoiceChangerProfile sanitizeProfile(VoiceChangerProfile profile) {
        return new VoiceChangerProfile(
                safeDouble(profile.mix(), 0.90D, 0.0D, 1.0D),
                safeDouble(profile.gain(), 1.00D, 0.40D, 2.50D),
                safeDouble(profile.pitch(), 1.00D, 0.55D, 2.20D),
                safeDouble(profile.formant(), 1.00D, 0.45D, 2.00D),
                safeDouble(profile.distortion(), 0.00D, 0.0D, 0.75D),
                safeDouble(profile.robotMix(), 0.00D, 0.0D, 0.85D),
                clampInt(profile.robotFrequency(), 20, 140),
                safeDouble(profile.echoMix(), 0.00D, 0.0D, 0.65D),
                clampInt(profile.echoDelayMs(), 40, 260),
                safeDouble(profile.echoFeedback(), 0.10D, 0.0D, 0.80D),
                safeDouble(profile.tremoloDepth(), 0.00D, 0.0D, 0.75D),
                safeDouble(profile.tremoloRate(), 2.50D, 0.20D, 9.00D),
                safeDouble(profile.lowEq(), 0.00D, -10.0D, 10.0D),
                safeDouble(profile.midEq(), 0.00D, -10.0D, 10.0D),
                safeDouble(profile.highEq(), 0.00D, -10.0D, 10.0D),
                safeDouble(profile.noise(), 0.00D, 0.0D, 0.60D),
                safeDouble(profile.autotuneMix(), 0.00D, 0.0D, 1.0D),
                safeDouble(profile.autotuneStrength(), 0.60D, 0.0D, 1.0D),
                clampInt(profile.autotuneKey(), 0, 11),
                clampInt(profile.autotuneScale(), 0, 2),
                safeDouble(profile.bitDepth(), VoiceChangerProfile.BIT_DEPTH_CLEAN, VoiceChangerProfile.BIT_DEPTH_MIN, VoiceChangerProfile.BIT_DEPTH_CLEAN)
        );
    }

    private static double safeDouble(Properties properties, String key, double defaultValue, double min, double max) {
        return safeDouble(parseDouble(properties, key, defaultValue), defaultValue, min, max);
    }

    private static double safeDouble(double value, double defaultValue, double min, double max) {
        if (!Double.isFinite(value)) {
            return defaultValue;
        }

        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String stripExtension(String filename) {
        return filename.substring(0, filename.length() - EXTENSION.length());
    }
}

