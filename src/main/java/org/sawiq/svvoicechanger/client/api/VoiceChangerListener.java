package org.sawiq.svvoicechanger.client.api;

/**
 * Notified when something about the voice changes.
 *
 * <p>Every method has a default, so an implementation only overrides what it
 * cares about and gains nothing to fix when the interface grows.</p>
 *
 * <p>A listener is notified while the change it describes is being applied, so
 * it must not push or release an override of its own: that would start the
 * notification again from inside itself and run until the stack gives out. Do
 * the work on the next tick instead.</p>
 *
 */
public interface VoiceChangerListener {
    /** The player turned the effect on or off. */
    default void onEnabledChanged(boolean enabled) {
    }

    /** The player picked a different voice, or tuned one by hand. */
    default void onVoiceChanged(String voiceId) {
    }

    /** The player moved the strength control. */
    default void onStrengthChanged(int strength) {
    }

    /**
     * The winning override changed, including to nothing.
     *
     * @param ownerId owner of the override now in force, or null if none
     */
    default void onOverrideChanged(String ownerId) {
    }

    /**
     * The server changed whether the voice changer may be used here.
     *
     * @param reason a message to show the player, or null when allowed again
     */
    default void onAllowedChanged(boolean allowed, String reason) {
    }
}
