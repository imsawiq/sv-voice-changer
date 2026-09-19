package org.sawiq.svvoicechanger.protocol;

/**
 * Why the voice changer is not available.
 *
 * <p>Sent as a code rather than as text so the client can say it in the
 * player's own language. A server that wants to word it differently sends its
 * own message alongside, and that wins.</p>
 *
 * <p>Codes are part of the wire format and must not be renumbered. An
 * unrecognised one is read as {@link #UNKNOWN}, so an older client meeting a
 * newer server still says something true rather than nothing.</p>
 */
public enum PolicyReason {
    /** Nothing is wrong; the player may use the voice changer. */
    ALLOWED(0),
    /** Switched off for everybody on this server. */
    SERVER_DISABLED(1),
    /** This player specifically was muted by a moderator. */
    PLAYER_MUTED(2),
    /** This player lacks the permission node. */
    NO_PERMISSION(3),
    /** A reason this build does not know about. */
    UNKNOWN(255);

    private final int code;

    PolicyReason(int code) {
        this.code = code;
    }

    public int code() {
        return this.code;
    }

    /** Translation key for the message shown when a server sends no wording of its own. */
    public String translationKey() {
        return "svvoicechanger.server." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static PolicyReason byCode(int code) {
        for (PolicyReason reason : values()) {
            if (reason.code == code) {
                return reason;
            }
        }
        return UNKNOWN;
    }
}
