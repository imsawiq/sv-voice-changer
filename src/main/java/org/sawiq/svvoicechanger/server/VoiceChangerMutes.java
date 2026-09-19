package org.sawiq.svvoicechanger.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The players whose voice changer is switched off by a moderator.
 *
 * <p>Keyed by UUID so a name change does not lift a mute, and stored with the
 * last known name alongside it purely so the mute list is readable to whoever
 * has to review it.</p>
 *
 * <p>Written out on every change: a mute applied just before a crash that came
 * back lifted would be a moderation decision quietly undone.</p>
 */
public final class VoiceChangerMutes {
    private static final String FILE_NAME = "mutes.properties";
    private static final String COMMENT =
            "Players whose voice changer is muted, as uuid=last known name.";

    private final Path file;
    private final Map<UUID, String> mutedByUuid = new ConcurrentHashMap<>();

    public VoiceChangerMutes(Path directory) {
        this.file = directory.resolve(FILE_NAME);
    }

    public boolean isMuted(UUID playerId) {
        return this.mutedByUuid.containsKey(playerId);
    }

    /** @return false if that player was already muted */
    public boolean mute(UUID playerId, String playerName) throws IOException {
        if (this.mutedByUuid.put(playerId, playerName) != null) {
            return false;
        }

        save();
        return true;
    }

    /** @return false if that player was not muted */
    public boolean unmute(UUID playerId) throws IOException {
        if (this.mutedByUuid.remove(playerId) == null) {
            return false;
        }

        save();
        return true;
    }

    /** Names of everyone currently muted, for the moderator's list. */
    public List<String> mutedNames() {
        return this.mutedByUuid.values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public int size() {
        return this.mutedByUuid.size();
    }

    /** Entries that are not a readable UUID are dropped rather than kept unusable. */
    public void load() throws IOException {
        this.mutedByUuid.clear();
        if (!Files.exists(this.file)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(this.file)) {
            properties.load(input);
        }

        for (String key : properties.stringPropertyNames()) {
            try {
                this.mutedByUuid.put(UUID.fromString(key), properties.getProperty(key, key));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID, so there is no player it could apply to.
            }
        }
    }

    private void save() throws IOException {
        Files.createDirectories(this.file.getParent());

        // Sorted so the file does not reshuffle itself on every write, which
        // makes it awkward to keep in version control or to diff after an
        // incident.
        Map<String, String> sorted = new LinkedHashMap<>();
        this.mutedByUuid.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> sorted.put(entry.getKey().toString(), entry.getValue()));

        Properties properties = new Properties();
        properties.putAll(sorted);

        try (OutputStream output = Files.newOutputStream(this.file)) {
            properties.store(output, COMMENT);
        }
    }
}
