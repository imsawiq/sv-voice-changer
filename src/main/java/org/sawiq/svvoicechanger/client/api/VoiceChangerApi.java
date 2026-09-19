package org.sawiq.svvoicechanger.client.api;

import java.util.Optional;
import org.sawiq.svvoicechanger.client.api.internal.VoiceChangerApiRegistry;
import org.sawiq.svvoicechanger.client.model.VoiceProfile;

/**
 * The public entry point other mods build against.
 *
 * <p>Everything in this package is a stable contract. The rest of the mod is
 * not: treat any class outside {@code api} plus {@link VoiceProfile} and
 * {@link org.sawiq.svvoicechanger.client.model.VoiceParameter} as internal and subject to
 * change without notice.</p>
 *
 * <h2>Applying a voice while something is happening</h2>
 *
 * <p>This is what a radio, a mask, a possession effect or a machine wants, and
 * it is the reason the API exists. Push an override, keep the handle, release
 * it when the effect ends:</p>
 *
 * <pre>{@code
 * VoiceChangerApi api = VoiceChangerApi.get().orElseThrow();
 *
 * VoiceOverride radio = api.pushOverride(
 *         VoiceOverride.request("radiomod:handset", 100)
 *                 .profile(api.builtInProfile("radio").orElseThrow())
 *                 .build());
 *
 * // ... later, when the player puts the handset down
 * radio.release();
 * }</pre>
 *
 * <p>An override never touches what the player chose. Their own voice is still
 * there underneath and comes back by itself the moment the override is
 * released, which is what removes the need to save and restore their settings
 * by hand. Several mods may hold overrides at once; the highest priority wins,
 * and ties go to whoever pushed last.</p>
 *
 * <h2>Contributing a voice</h2>
 *
 * <p>A registered voice appears in the studio next to the built-in ones and can
 * be picked by the player like any other:</p>
 *
 * <pre>{@code
 * VoiceChangerApi.get().ifPresent(api -> api.registerPreset(VoicePreset.builder("radiomod:dispatch")
 *         .displayName(Component.translatable("radiomod.voice.dispatch"))
 *         .profile(profile)
 *         .build()));
 * }</pre>
 *
 * <h2>What this API deliberately does not do</h2>
 *
 * <p>There is no way to change the player's own saved choice, their strength or
 * whether the effect is on. A mod that wants a different voice asks for it with
 * an override, which is visible, temporary and reversible. Silently rewriting
 * somebody's settings is not something another mod should be able to do.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Every method is safe to call from any thread. Listeners are notified on
 * the thread that caused the change, which for player actions is the client
 * thread.</p>
 */
public interface VoiceChangerApi {
    /** Version of this contract. Incremented only for breaking changes. */
    int API_VERSION = 1;

    /**
     * @return the API once the mod has started, or empty when the voice changer
     *         is absent or not yet initialised. Never throws, so a soft
     *         dependency can call it unconditionally.
     */
    static Optional<VoiceChangerApi> get() {
        return VoiceChangerApiRegistry.current();
    }

    // --- State ---------------------------------------------------------------

    /** Whether the player has the effect switched on. */
    boolean isEffectEnabled();

    /**
     * Whether the effect is allowed here at all. A server may forbid it, in
     * which case overrides are accepted but nothing is applied to the voice.
     */
    boolean isAllowed();

    /** The player's strength setting, 0 to 100. */
    int getStrength();

    /**
     * Identifier of the voice the player selected: a built-in id such as
     * {@code "man"}, a registered {@code "namespace:id"}, or {@code "custom"}
     * when they tuned it by hand.
     */
    String getSelectedVoiceId();

    /**
     * The tuning actually being applied right now, including any override, or
     * empty when nothing is being applied.
     */
    Optional<VoiceProfile> getActiveProfile();

    /** The tuning the player chose, ignoring any override on top of it. */
    VoiceProfile getPlayerProfile();

    // --- Overrides -----------------------------------------------------------

    /**
     * Applies a voice on top of the player's own until the handle is released.
     *
     * @return a handle; releasing it restores whatever was underneath
     */
    VoiceOverride pushOverride(VoiceOverride.Request request);

    /** Releases every override a given owner is holding. */
    void releaseOverrides(String ownerId);

    /**
     * The override currently winning, if any.
     *
     * <p>Handed out for inspection. Releasing one you did not push takes a
     * voice away from whichever mod is using it, so call {@code release()} only
     * on a handle {@link #pushOverride} gave you.</p>
     */
    Optional<VoiceOverride> getActiveOverride();

    // --- Voices --------------------------------------------------------------

    /**
     * Adds a voice to the studio. Registering the same id twice replaces the
     * earlier one, so a mod can update its voice without restarting.
     */
    void registerPreset(VoicePreset preset);

    void unregisterPreset(String presetId);

    /** The tuning of a built-in voice, by its id, for use as a starting point. */
    Optional<VoiceProfile> builtInProfile(String builtInId);

    // --- Events --------------------------------------------------------------

    void addListener(VoiceChangerListener listener);

    void removeListener(VoiceChangerListener listener);
}
