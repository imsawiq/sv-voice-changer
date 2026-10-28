package org.sawiq.svvoicechanger.client.ai.engine.elevenlabs;

import java.io.IOException;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus.Availability;

/**
 * An error answer from ElevenLabs, with what it means for the player: a key
 * that is wrong, an account that is out of credits, or anything else.
 */
public final class ElevenLabsException extends IOException {
    private final int statusCode;
    private final String reason;

    public ElevenLabsException(int statusCode, String reason, String message) {
        super(message);
        this.statusCode = statusCode;
        this.reason = reason == null ? "" : reason;
    }

    public int statusCode() {
        return this.statusCode;
    }

    /** ElevenLabs' own machine-readable reason, such as {@code quota_exceeded}. */
    public String reason() {
        return this.reason;
    }

    public boolean isMissingPermission() {
        return "missing_permissions".equals(this.reason);
    }

    public Availability availability() {
        if (this.statusCode == 401 && !isMissingPermission()) {
            return Availability.BAD_KEY;
        }
        if ("quota_exceeded".equals(this.reason) || this.statusCode == 402) {
            return Availability.NO_CREDITS;
        }
        return Availability.FAILED;
    }
}
