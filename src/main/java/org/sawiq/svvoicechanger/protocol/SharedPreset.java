package org.sawiq.svvoicechanger.protocol;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * A voice a server offers to the players on it.
 *
 * <p>Only ever numbers and two short strings. A parameter the client does not
 * know is dropped and one it does know is clamped to that parameter's own
 * range, so the worst a server can do with this is offer a voice that sounds
 * bad — not reach past the audio settings into anything else.</p>
 *
 * @param id     unique within the server's own list
 * @param name   what the player sees on the button
 * @param values parameter id to value, as written in the server's file
 */
public record SharedPreset(String id, String name, Map<String, Double> values) {
    /** Long enough for a descriptive name, short enough not to break the grid. */
    public static final int MAX_ID_LENGTH = 48;
    public static final int MAX_NAME_LENGTH = 32;

    public SharedPreset {
        values = Map.copyOf(values);
    }

    /**
     * @return whether the id is safe to use as an identifier and as part of a
     *         translation-free button label. Deliberately narrow: it is never
     *         used as a path, but a name that cannot contain a separator or a
     *         control character cannot start being one later either.
     */
    public static boolean isValidId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_ID_LENGTH) {
            return false;
        }

        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_' || character == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** Builds the tuning, ignoring anything this version does not recognise. */
    public VoiceProfile toProfile() {
        VoiceProfile.Builder builder = VoiceProfile.builder();
        for (VoiceParameter parameter : VoiceParameter.values()) {
            Double value = this.values.get(parameter.id());
            if (value != null) {
                builder.set(parameter, value);
            }
        }
        return builder.build();
    }

    /** Keeps only parameters this version knows, each clamped to its own range. */
    public static Map<String, Double> sanitizeValues(Map<String, Double> raw) {
        Map<String, Double> sanitized = new LinkedHashMap<>();
        for (VoiceParameter parameter : VoiceParameter.values()) {
            Double value = raw.get(parameter.id());
            if (value != null) {
                sanitized.put(parameter.id(), parameter.sanitize(value));
            }
        }
        return sanitized;
    }
}
