package org.sawiq.svvoicechanger.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * What the server operator decided, on disk.
 *
 * <p>Written back in full whenever it is loaded, so a fresh install gets a
 * commented file with every option in it rather than an empty one the operator
 * has to guess at. Anything unreadable falls back to the defaults and is
 * logged: a broken config should not stop the server from starting, and
 * silently denying everybody the voice changer would be the worse failure.</p>
 */
public final class ServerVoiceChangerConfig {
    private static final String FILE_NAME = "server.properties";

    private static final String KEY_ALLOWED = "allow-voice-changer";
    private static final String KEY_DENIED_MESSAGE = "denied-message";
    private static final String KEY_SHARE_PRESETS = "share-presets";
    private static final String KEY_ALLOWED_VOICES = "allowed-voices";

    private static final String COMMENT = """
            Simple Voice Changer, server settings.

            allow-voice-changer  Whether players may change their voice here.
                                 Toggled at runtime with /voicechanger on|off.
            denied-message       Shown to a player whose voice changer is off
                                 because of this server. Leave empty to use the
                                 message built into the client, which is
                                 already translated.
            share-presets        Whether the voices in the shared-voices
                                 folder next to this file are offered to
                                 players.
            allowed-voices       How much freedom players have over their voice.

                                 all         anything, including the sliders
                                 presets     ready-made voices only: this mod's
                                             own, ones other mods added, and the
                                             ones this server shares. No hand
                                             tuning and no personal preset files.
                                 server-only only the voices this server shares.

                                 This is about keeping a server listenable, not
                                 about safety. A mod holding a temporary voice,
                                 such as a radio, is not affected.

            The voice is changed on the player's own machine before it is sent,
            so these settings are a policy an unmodified client obeys. They are
            not, and cannot be, an enforcement mechanism.""";

    private final Path file;

    private volatile boolean allowed = true;
    private volatile String deniedMessage = "";
    private volatile boolean sharePresets = true;
    private volatile VoiceSourceRule allowedVoices = VoiceSourceRule.ALL;

    public ServerVoiceChangerConfig(Path directory) {
        this.file = directory.resolve(FILE_NAME);
    }

    public boolean isAllowed() {
        return this.allowed;
    }

    public String getDeniedMessage() {
        return this.deniedMessage;
    }

    public boolean isSharePresets() {
        return this.sharePresets;
    }

    public VoiceSourceRule getAllowedVoices() {
        return this.allowedVoices;
    }

    /** @throws IOException if the file exists but cannot be read */
    public void load() throws IOException {
        if (Files.exists(this.file)) {
            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(this.file)) {
                properties.load(input);
            }

            this.allowed = parseBoolean(properties.getProperty(KEY_ALLOWED), true);
            this.deniedMessage = properties.getProperty(KEY_DENIED_MESSAGE, "").trim();
            this.sharePresets = parseBoolean(properties.getProperty(KEY_SHARE_PRESETS), true);
            this.allowedVoices = VoiceSourceRule.byConfigValue(
                    properties.getProperty(KEY_ALLOWED_VOICES), VoiceSourceRule.ALL);
        }

        save();
    }

    public void setAllowed(boolean allowed) throws IOException {
        this.allowed = allowed;
        save();
    }

    public void setAllowedVoices(VoiceSourceRule allowedVoices) throws IOException {
        this.allowedVoices = allowedVoices;
        save();
    }

    private void save() throws IOException {
        Files.createDirectories(this.file.getParent());

        Properties properties = new Properties();
        properties.setProperty(KEY_ALLOWED, Boolean.toString(this.allowed));
        properties.setProperty(KEY_DENIED_MESSAGE, this.deniedMessage);
        properties.setProperty(KEY_SHARE_PRESETS, Boolean.toString(this.sharePresets));
        properties.setProperty(KEY_ALLOWED_VOICES, this.allowedVoices.configValue());

        try (OutputStream output = Files.newOutputStream(this.file)) {
            properties.store(output, COMMENT);
        }
    }

    /**
     * Unlike {@link Boolean#parseBoolean}, a value that is neither true nor
     * false keeps the default rather than quietly becoming false.
     */
    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }

        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }
}
