package org.sawiq.svvoicechanger.protocol;

/**
 * The plugin channel the server and the client talk over.
 *
 * <p>One channel carries every message, each tagged with a type byte, because
 * a channel the client has not registered is invisible to the server: with one
 * channel the server can tell from {@code getRegisteredChannels()} whether the
 * player has the mod at all, and does not have to keep several answers in
 * step.</p>
 *
 * <p>The messages themselves are plain data — numbers and short strings that
 * are range-checked on arrival. Nothing here names a class, a file or anything
 * else the other side would go and look up, which is what keeps a server from
 * being able to make a client run something.</p>
 */
public final class VoiceChangerChannel {
    /** Namespaced channel id, in the form Minecraft expects. */
    public static final String CHANNEL = "sv-voice-changer:main";

    /**
     * Bumped only when an old client could misread a new message. Both sides
     * check it and simply stop talking on a mismatch, which is better than a
     * half-understood policy.
     */
    public static final int PROTOCOL_VERSION = 2;

    /** Serverbound: the client says it is here and which protocol it speaks. */
    public static final byte TYPE_HELLO = 0;

    /** Clientbound: whether the voice changer may be used, and why not. */
    public static final byte TYPE_POLICY = 1;

    /** Clientbound: the voices the server offers. */
    public static final byte TYPE_PRESETS = 2;

    /**
     * The largest message either side will accept. Comfortably above a full
     * preset list and far below anything that could be used to exhaust memory.
     */
    public static final int MAX_MESSAGE_BYTES = 64 * 1024;

    private VoiceChangerChannel() {
    }
}
