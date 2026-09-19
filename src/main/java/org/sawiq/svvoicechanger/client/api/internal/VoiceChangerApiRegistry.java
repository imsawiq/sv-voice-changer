package org.sawiq.svvoicechanger.client.api.internal;

import java.util.Optional;
import org.sawiq.svvoicechanger.client.api.VoiceChangerApi;

/**
 * Holds the live API instance for {@link VoiceChangerApi#get()}.
 *
 * <p>The field is volatile because a mod may look the API up from its own
 * initialiser, on a different thread from the one that published it.</p>
 */
public final class VoiceChangerApiRegistry {
    private static volatile VoiceChangerApi current;

    private VoiceChangerApiRegistry() {
    }

    public static Optional<VoiceChangerApi> current() {
        return Optional.ofNullable(current);
    }

    public static void publish(VoiceChangerApi api) {
        current = api;
    }

    public static void withdraw() {
        current = null;
    }
}
