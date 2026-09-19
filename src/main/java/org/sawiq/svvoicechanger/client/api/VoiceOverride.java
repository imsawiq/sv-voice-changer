package org.sawiq.svvoicechanger.client.api;

import java.util.Objects;
import java.util.Optional;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * A voice applied on top of the player's own for as long as the holder keeps
 * it.
 *
 * <p>Releasing is the whole point: the player's own settings were never
 * touched, so there is nothing to restore and nothing to get wrong if a mod
 * crashes halfway through. Overrides are also safe to leak in the sense that
 * they only last as long as the game session, though a mod that forgets to
 * release one leaves the player stuck with a voice they did not pick, so
 * release from a {@code finally} or on the event that ends the effect.</p>
 */
public interface VoiceOverride {
    /** Who asked for this, in {@code namespace:id} form. */
    String ownerId();

    /** Higher wins. Ties go to whoever pushed most recently. */
    int priority();

    /** Whether this override is still held and has not been released. */
    boolean isActive();

    /** Whether this override is the one currently shaping the voice. */
    boolean isWinning();

    /**
     * Gives the voice back. Safe to call more than once, and safe to call after
     * the mod has already been shut down.
     */
    void release();

    /** Starts describing an override. */
    static Builder request(String ownerId, int priority) {
        return new Builder(ownerId, priority);
    }

    /**
     * What a mod is asking for.
     *
     * @param ownerId      who is asking, in {@code namespace:id} form
     * @param priority     higher wins over other overrides
     * @param profile      the tuning to apply
     * @param strength     0 to 100, or empty to keep the player's own strength
     * @param forceEnabled applies the voice even while the player has the
     *                     effect switched off, for effects that are part of a
     *                     mod's mechanics rather than a preference
     */
    record Request(
            String ownerId,
            int priority,
            VoiceProfile profile,
            Optional<Integer> strength,
            boolean forceEnabled
    ) {
        public Request {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(profile, "profile");
            Objects.requireNonNull(strength, "strength");

            if (ownerId.isBlank()) {
                throw new IllegalArgumentException("ownerId must not be blank");
            }
            strength.ifPresent(value -> {
                if (value < 0 || value > 100) {
                    throw new IllegalArgumentException("strength must be between 0 and 100");
                }
            });
        }
    }

    /** Builds a {@link Request}. */
    final class Builder {
        private final String ownerId;
        private final int priority;
        private VoiceProfile profile;
        private Integer strength;
        private boolean forceEnabled;

        private Builder(String ownerId, int priority) {
            this.ownerId = ownerId;
            this.priority = priority;
        }

        /** The tuning to apply. Required. */
        public Builder profile(VoiceProfile profile) {
            this.profile = profile;
            return this;
        }

        /** Overrides the player's strength setting for as long as this is held. */
        public Builder strength(int strength) {
            this.strength = strength;
            return this;
        }

        /**
         * Applies even when the player has the voice changer switched off.
         *
         * <p>For mechanics the player cannot opt out of, such as speaking
         * through a machine. Do not use it to ignore a preference.</p>
         */
        public Builder forceEnabled() {
            this.forceEnabled = true;
            return this;
        }

        public Request build() {
            return new Request(this.ownerId, this.priority, this.profile,
                    Optional.ofNullable(this.strength), this.forceEnabled);
        }
    }
}
