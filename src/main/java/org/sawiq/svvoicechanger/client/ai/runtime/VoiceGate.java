package org.sawiq.svvoicechanger.client.ai.runtime;

import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;

/**
 * Decides from the level alone whether someone is talking, for the studio's
 * preview, which has no push-to-talk key to go by.
 *
 * <p>Holds open for a moment after the voice drops, so the pauses inside a
 * sentence do not cut a phrase engine's phrase into pieces.</p>
 */
final class VoiceGate {
    /** About -34 dBFS: above a quiet room, below normal speech. */
    private static final double OPEN_LEVEL = 0.02D;
    private static final int HOLD_FRAMES = 25;

    private int holdLeft;

    boolean isTalking(short[] frame) {
        if (AudioFrames.peakOf(frame) >= OPEN_LEVEL) {
            this.holdLeft = HOLD_FRAMES;
            return true;
        }
        if (this.holdLeft > 0) {
            this.holdLeft--;
            return true;
        }
        return false;
    }
}
