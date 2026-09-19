package org.sawiq.svvoicechanger.client.api.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.sawiq.svvoicechanger.client.api.VoicePreset;
import org.sawiq.svvoicechanger.protocol.VoiceSourceRule;

/**
 * The voices other mods have contributed.
 *
 * <p>Registration order is the menu order, so a mod's voices stay where the
 * player last saw them rather than jumping around between launches. Reads go
 * through an immutable snapshot: the studio redraws this list every frame and
 * must not be locking against a mod's initialiser to do it. That snapshot is
 * replaced rather than mutated on every change, which is also how the studio
 * notices it has to rebuild — an identity comparison, no listener needed.</p>
 */
public final class VoicePresetRegistry {
    /** Voices a server shares live here; a mod may not register into it. */
    private static final String RESERVED_NAMESPACE = VoiceSourceRule.SERVER_NAMESPACE;

    private final Object lock = new Object();
    private final Map<String, VoicePreset> presetsById = new LinkedHashMap<>();

    private volatile List<VoicePreset> snapshot = List.of();

    /**
     * Replaces any voice with the same id, keeping its place in the menu.
     *
     * @throws IllegalArgumentException if the id uses the namespace a server's
     *         own shared voices live under, which would let a mod's voice be
     *         removed when the player leaves that server
     */
    public void register(VoicePreset preset) {
        Objects.requireNonNull(preset, "preset");
        if (preset.id().startsWith(RESERVED_NAMESPACE + ":")) {
            throw new IllegalArgumentException(
                    "The " + RESERVED_NAMESPACE + " namespace is reserved for voices a server shares: "
                            + preset.id());
        }

        put(preset);
    }

    /**
     * Adds a voice the connected server shared, under the namespace {@link
     * #register} refuses. Only the connection's own session may call this, and
     * it is what removes these voices again on disconnect.
     */
    public void registerShared(VoicePreset preset) {
        Objects.requireNonNull(preset, "preset");
        put(preset);
    }

    private void put(VoicePreset preset) {
        synchronized (this.lock) {
            this.presetsById.put(preset.id(), preset);
            this.snapshot = List.copyOf(this.presetsById.values());
        }
    }

    public void unregister(String presetId) {
        synchronized (this.lock) {
            if (this.presetsById.remove(presetId) != null) {
                this.snapshot = List.copyOf(this.presetsById.values());
            }
        }
    }

    /** Every contributed voice, in registration order. Safe to iterate while drawing. */
    public List<VoicePreset> all() {
        return this.snapshot;
    }

    public Optional<VoicePreset> byId(String presetId) {
        if (presetId == null) {
            return Optional.empty();
        }

        for (VoicePreset preset : this.snapshot) {
            if (preset.id().equals(presetId)) {
                return Optional.of(preset);
            }
        }
        return Optional.empty();
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (VoicePreset preset : this.snapshot) {
            ids.add(preset.id());
        }
        return List.copyOf(ids);
    }
}
