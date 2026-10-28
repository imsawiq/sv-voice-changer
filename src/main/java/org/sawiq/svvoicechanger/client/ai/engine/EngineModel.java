package org.sawiq.svvoicechanger.client.ai.engine;

/**
 * One voice an engine can speak in: an ElevenLabs voice, an RVC model in a
 * w-okada slot, or a model the companion engine loaded.
 *
 * @param id          what the engine calls it, sent back when selecting it
 * @param name        what the player sees
 * @param description extra detail for a tooltip, possibly empty
 */
public record EngineModel(String id, String name, String description) {
    public EngineModel {
        name = name == null || name.isBlank() ? id : name;
        description = description == null ? "" : description;
    }
}
