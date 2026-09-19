package org.sawiq.svvoicechanger.client.model;

/**
 * What the audio thread should be doing right now, resolved once so that the
 * whole decision is read atomically.
 *
 * <p>The alternative is asking three separate questions — is it on, with which
 * tuning, at which strength — and an override arriving between two of them
 * would apply one voice's profile at another's strength for a block. Resolving
 * them together removes that window entirely.</p>
 *
 * @param active   whether the effect should be applied at all
 * @param profile  the tuning to apply
 * @param strength 0 to 100
 */
public record ActiveVoice(boolean active, VoiceProfile profile, int strength) {
    public static final ActiveVoice INACTIVE = new ActiveVoice(false, VoiceProfile.neutral(), 0);
}
