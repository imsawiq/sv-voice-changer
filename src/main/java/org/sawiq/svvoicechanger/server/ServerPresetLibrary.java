package org.sawiq.svvoicechanger.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.sawiq.svvoicechanger.protocol.SharedPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The voices an operator dropped into the server's presets folder.
 *
 * <p>The file format is the one the client already saves presets in, so an
 * operator tunes a voice in the studio, copies the file over, and it is
 * offered to everybody. No conversion step, and nothing for them to hand-write.
 * </p>
 *
 * <p>What leaves this class is only ever numbers and two short strings, both
 * derived here rather than taken from the file: the id comes from the
 * filename, and every parameter is clamped to its own range. A server cannot
 * use this to send a client anything but a voice.</p>
 */
public final class ServerPresetLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Voice Changer");

    /**
     * Deliberately not "presets": on a client that opens a world to LAN the
     * player's own preset folder sits beside this one, and sharing it would
     * hand every private voice to whoever joins.
     */
    private static final String DIRECTORY_NAME = "shared-voices";
    private static final String EXTENSION = ".properties";
    private static final String KEY_NAME = "name";

    /** Matches the codec's cap, so nothing is loaded that could not be sent. */
    private static final int MAX_PRESETS = 64;

    private static final String README = """
            Put voice files in this folder to offer them to players on this
            server. They appear in the player's studio next to the built-in
            voices, and are not saved to the player's own preset library.

            The format is the one the client writes. To make one: tune a voice
            in the studio, press Save, then use the studio's "Open folder"
            button to find the file and copy it in here.

            The filename becomes the voice's id; add a line

                name=Dispatch Radio

            to control the label players see. Without it the filename is used.

            Only numbers are read from these files. Nothing in them can make a
            client load or run anything.""";

    private final Path directory;
    private volatile List<SharedPreset> presets = List.of();

    public ServerPresetLibrary(Path parentDirectory) {
        this.directory = parentDirectory.resolve(DIRECTORY_NAME);
    }

    /** Everything currently offered, in a stable order. */
    public List<SharedPreset> presets() {
        return this.presets;
    }

    public Path directory() {
        return this.directory;
    }

    /**
     * Rereads the folder, creating it with a README the first time.
     *
     * @return the number of voices now offered
     * @throws IOException if the folder cannot be created or listed
     */
    public int reload() throws IOException {
        ensureDirectory();

        List<Path> files;
        try (Stream<Path> found = Files.list(this.directory)) {
            files = found
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        List<SharedPreset> loaded = new ArrayList<>();
        for (Path file : files) {
            if (loaded.size() >= MAX_PRESETS) {
                break;
            }

            SharedPreset preset = read(file);
            if (preset != null && loaded.stream().noneMatch(existing -> existing.id().equals(preset.id()))) {
                loaded.add(preset);
            }
        }

        this.presets = List.copyOf(loaded);
        return this.presets.size();
    }

    private void ensureDirectory() throws IOException {
        boolean created = !Files.exists(this.directory);
        Files.createDirectories(this.directory);

        if (created) {
            Files.writeString(this.directory.resolve("README.txt"), README);
        }
    }

    /** @return the voice in that file, or null if it holds no usable values */
    private static SharedPreset read(Path file) {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException exception) {
            LOGGER.warn("Skipping unreadable shared voice {}", file.getFileName(), exception);
            return null;
        }

        String id = idFromFilename(file.getFileName().toString());
        if (!SharedPreset.isValidId(id)) {
            return null;
        }

        Map<String, Double> raw = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            try {
                raw.put(key, Double.parseDouble(properties.getProperty(key).trim()));
            } catch (NumberFormatException ignored) {
                // Not a number, so not a parameter. The name line lands here.
            }
        }

        Map<String, Double> values = SharedPreset.sanitizeValues(raw);
        if (values.isEmpty()) {
            return null;
        }

        return new SharedPreset(id, displayName(properties, id), values);
    }

    private static String displayName(Properties properties, String fallback) {
        String configured = properties.getProperty(KEY_NAME, "").trim();
        String name = configured.isEmpty() ? fallback : configured;
        return name.length() <= SharedPreset.MAX_NAME_LENGTH
                ? name
                : name.substring(0, SharedPreset.MAX_NAME_LENGTH);
    }

    /**
     * The filename, lowercased, with anything outside the allowed set folded to
     * an underscore. Built here rather than read from the file so that a preset
     * can never carry an id the operator did not see in their own folder.
     */
    private static String idFromFilename(String filename) {
        String base = filename.substring(0, filename.length() - EXTENSION.length())
                .toLowerCase(Locale.ROOT);

        StringBuilder id = new StringBuilder(base.length());
        for (int i = 0; i < base.length() && id.length() < SharedPreset.MAX_ID_LENGTH; i++) {
            char character = base.charAt(i);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-';
            id.append(allowed ? character : '_');
        }
        return id.toString();
    }
}
