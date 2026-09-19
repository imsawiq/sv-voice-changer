package org.sawiq.svvoicechanger.client.preset;

import java.util.Properties;
import org.sawiq.svvoicechanger.client.model.VoiceParameter;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Brings presets written by version 1.6 and earlier forward.
 *
 * <p>Most parameter keys survived the rewrite unchanged, so only the space
 * section needs translating: the old single delay tap described itself with a
 * delay time in milliseconds and a feedback amount, while the reverb that
 * replaced it is described by room size and decay. The conversion maps the old
 * ranges onto the new normalised ones, which keeps a user's saved preset
 * roughly as roomy as it was.</p>
 *
 * <p>Parameters that did not exist before, such as the gate and the de-esser,
 * are deliberately left at their defaults: an old preset gains the cleanup
 * stages, which is an improvement rather than a change of character.</p>
 */
final class LegacyPresetMigration {
    private static final String LEGACY_ECHO_MIX = "echoMix";
    private static final String LEGACY_ECHO_DELAY_MS = "echoDelayMs";
    private static final String LEGACY_ECHO_FEEDBACK = "echoFeedback";

    private static final double LEGACY_MAX_ECHO_MIX = 0.65D;
    private static final double LEGACY_MIN_DELAY_MS = 40.0D;
    private static final double LEGACY_MAX_DELAY_MS = 260.0D;
    private static final double LEGACY_MAX_FEEDBACK = 0.80D;

    private LegacyPresetMigration() {
    }

    /**
     * Fills in the reverb parameters from legacy echo keys, but only for keys
     * the file does not already define in the current format.
     */
    static void apply(Properties properties, VoiceProfile.Builder builder) {
        if (properties.getProperty(VoiceParameter.REVERB_MIX.id()) != null) {
            return;
        }

        Double echoMix = readDouble(properties, LEGACY_ECHO_MIX);
        if (echoMix == null) {
            return;
        }

        builder.set(VoiceParameter.REVERB_MIX, normalise(echoMix, 0.0D, LEGACY_MAX_ECHO_MIX));

        Double delayMs = readDouble(properties, LEGACY_ECHO_DELAY_MS);
        if (delayMs != null) {
            builder.set(VoiceParameter.REVERB_SIZE, normalise(delayMs, LEGACY_MIN_DELAY_MS, LEGACY_MAX_DELAY_MS));
        }

        Double feedback = readDouble(properties, LEGACY_ECHO_FEEDBACK);
        if (feedback != null) {
            builder.set(VoiceParameter.REVERB_DECAY, normalise(feedback, 0.0D, LEGACY_MAX_FEEDBACK));
        }
    }

    private static double normalise(double value, double min, double max) {
        double fraction = (value - min) / (max - min);
        return Math.max(0.0D, Math.min(1.0D, fraction));
    }

    private static Double readDouble(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            return null;
        }

        try {
            double parsed = Double.parseDouble(raw.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
