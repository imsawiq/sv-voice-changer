package org.sawiq.svvoicechanger.client.api.internal;

import java.util.Optional;
import java.util.function.Consumer;
import org.sawiq.svvoicechanger.SvVoiceChanger;
import org.sawiq.svvoicechanger.client.api.VoiceChangerListener;
import org.sawiq.svvoicechanger.client.api.VoiceOverride;
import org.sawiq.svvoicechanger.client.model.ActiveVoice;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * Everything the public API adds to the mod, in one place: the overrides other
 * mods hold, the voices they contribute, and the listeners they register.
 *
 * <p>It also answers the one question the audio thread asks — what should be
 * applied right now — because that answer is where the player's own settings
 * and a mod's override meet.</p>
 */
public final class VoiceChangerApiSupport {
    private final VoiceOverrideStack overrides = new VoiceOverrideStack();
    private final VoicePresetRegistry contributedPresets = new VoicePresetRegistry();
    private final VoiceChangerListeners listeners = new VoiceChangerListeners(
            exception -> SvVoiceChanger.LOGGER.warn(
                    "A voice changer API listener threw and was removed", exception));

    public VoiceOverrideStack overrides() {
        return this.overrides;
    }

    public VoicePresetRegistry contributedPresets() {
        return this.contributedPresets;
    }

    public VoiceChangerListeners listeners() {
        return this.listeners;
    }

    /**
     * Called whenever the winning override changes, including to none.
     *
     * <p>Contributed voices deliberately have no callback: they do not change
     * what is being applied, and the studio notices them by comparing the
     * registry's snapshot against the one it drew.</p>
     */
    public void onOverrideChanged(Runnable callback) {
        this.overrides.setChangeListener(callback);
    }

    /**
     * Works out what the audio thread should apply.
     *
     * <p>An override replaces the tuning, and may replace the strength, but it
     * cannot get past a server that has switched the voice changer off: a
     * policy the player has to obey is not one another mod may waive.</p>
     *
     * @param playerVoiceAllowed whether the voice the player picked is one this
     *                           server permits; ignored while an override holds
     *                           the voice
     */
    public ActiveVoice resolve(
            boolean playerEnabled,
            boolean allowedByServer,
            boolean playerVoiceAllowed,
            VoiceProfile playerProfile,
            int playerStrength
    ) {
        Optional<VoiceOverride.Request> override = this.overrides.winningRequest();
        boolean active = allowedByServer
                && (playerEnabled || override.map(VoiceOverride.Request::forceEnabled).orElse(false));

        // A server that restricts where voices may come from is restricting the
        // player, not the mods: a radio held by another mod is a mechanic the
        // operator installed, so an override applies whatever the player's own
        // selection would have been allowed to be.
        if (override.isEmpty() && !playerVoiceAllowed) {
            return ActiveVoice.INACTIVE;
        }

        if (!active) {
            return ActiveVoice.INACTIVE;
        }

        return new ActiveVoice(
                true,
                override.map(VoiceOverride.Request::profile).orElse(playerProfile),
                override.flatMap(VoiceOverride.Request::strength).orElse(playerStrength));
    }

    /** The owner of the override currently in force, or null if there is none. */
    public String winningOverrideOwner() {
        return this.overrides.winningRequest().map(VoiceOverride.Request::ownerId).orElse(null);
    }

    public void notifyEach(Consumer<VoiceChangerListener> event) {
        this.listeners.notifyEach(event);
    }

    /** Drops every mod-supplied thing, so a restarted addon starts clean. */
    public void clear() {
        this.overrides.clear();
        this.listeners.clear();
    }
}
