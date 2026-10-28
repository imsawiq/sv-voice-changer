package org.sawiq.svvoicechanger.client.ai.engine;

import java.util.List;
import java.util.Locale;

/**
 * What an engine said the last time it was asked whether it can be used.
 *
 * @param detail free text shown after the state, such as the GPU a local
 *               engine runs on or the credits left on an ElevenLabs account;
 *               never translated, possibly empty
 * @param models the voices it offers, empty when it could not be asked
 */
public record EngineStatus(EngineKind kind, Availability availability, String detail, List<EngineModel> models) {
    public enum Availability {
        /** Not asked yet, or the answer is still on its way. */
        CHECKING,
        /** The mod started the installed engine and it is still loading. */
        STARTING,
        READY,
        /** Running, but nothing to speak with until a model or voice is picked. */
        NO_MODEL,
        NOT_RUNNING,
        NO_KEY,
        BAD_KEY,
        NO_CREDITS,
        FAILED;

        public String translationKey() {
            return "svvoicechanger.ai.status." + name().toLowerCase(Locale.ROOT);
        }
    }

    public EngineStatus {
        detail = detail == null ? "" : detail;
        models = List.copyOf(models);
    }

    public static EngineStatus checking(EngineKind kind) {
        return new EngineStatus(kind, Availability.CHECKING, "", List.of());
    }

    public static EngineStatus of(EngineKind kind, Availability availability, String detail) {
        return new EngineStatus(kind, availability, detail, List.of());
    }

    public boolean isReady() {
        return this.availability == Availability.READY;
    }
}
