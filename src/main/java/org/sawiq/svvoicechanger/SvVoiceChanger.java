package org.sawiq.svvoicechanger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SvVoiceChanger {
    public static final String MOD_ID = "sv-voice-changer";
    public static final String NEOFORGE_MOD_ID = "sv_voice_changer";
    public static final String MOD_NAME = "Simple Voice Voice Changer";
    /** Sent to the server in the greeting, purely so operators can read it in a log. */
    public static final String MOD_VERSION = "1.2.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private SvVoiceChanger() {
    }
}
