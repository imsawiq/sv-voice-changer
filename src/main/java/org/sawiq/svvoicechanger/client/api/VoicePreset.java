package org.sawiq.svvoicechanger.client.api;

import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * A voice contributed by another mod, shown in the studio beside the built-in
 * ones and selectable by the player like any of them.
 *
 * <p>The id is what gets written into the player's saved settings, so it has to
 * stay stable across releases of the contributing mod. If the mod is later
 * removed the id stops resolving; the player keeps the tuning they had and the
 * studio simply shows it as a custom voice rather than losing it.</p>
 *
 * @param id          {@code namespace:id}, stable across versions
 * @param displayName what the player sees on the button
 * @param description one line for the tooltip, or null for none
 * @param profile     the tuning
 */
public record VoicePreset(
        String id,
        Component displayName,
        Component description,
        VoiceProfile profile
) {
    private static final int MAX_ID_LENGTH = 64;

    public VoicePreset {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(profile, "profile");

        if (!isWellFormed(id)) {
            throw new IllegalArgumentException(
                    "id must look like namespace:path, using a-z 0-9 _ - . only: " + id);
        }
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Ids go into save files and onto buttons, so they are kept to a shape that
     * cannot surprise either: no separators, no spaces, no case games.
     */
    private static boolean isWellFormed(String id) {
        if (id.length() > MAX_ID_LENGTH) {
            return false;
        }

        int separator = id.indexOf(':');
        if (separator <= 0 || separator == id.length() - 1) {
            return false;
        }

        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            boolean allowed = character == ':'
                    || (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-' || character == '.';
            if (!allowed) {
                return false;
            }
        }
        return id.indexOf(':', separator + 1) < 0;
    }

    public static final class Builder {
        private final String id;
        private Component displayName;
        private Component description;
        private VoiceProfile profile;

        private Builder(String id) {
            this.id = id;
        }

        public Builder displayName(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(Component description) {
            this.description = description;
            return this;
        }

        public Builder profile(VoiceProfile profile) {
            this.profile = profile;
            return this;
        }

        public VoicePreset build() {
            return new VoicePreset(this.id, this.displayName, this.description, this.profile);
        }
    }
}
