package org.sawiq.svvoicechanger.client.ai.engine;

import java.io.IOException;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettings;

/**
 * One source of AI voices: knows how to ask whether it is usable and how to
 * open a conversion with it.
 *
 * <p>{@link #probe} and the {@code open} methods talk to the network and block,
 * so they are only ever called from the controller's background executor.</p>
 */
public sealed interface AiVoiceEngine permits AiVoiceEngine.Live, AiVoiceEngine.Phrase {
    EngineKind kind();

    EngineStatus probe(EngineSetup setup);

    /** An engine that converts while the player talks. */
    non-sealed interface Live extends AiVoiceEngine {
        LiveConverter openLive(EngineSetup setup) throws IOException;
    }

    /** An engine that converts finished phrases. */
    non-sealed interface Phrase extends AiVoiceEngine {
        PhraseConverter openPhrase(EngineSetup setup) throws IOException;
    }

    /**
     * What an engine needs to know about the player's setup.
     *
     * @param elevenLabsKey empty when the player has not given one
     */
    record EngineSetup(AiVoiceSettings settings, String elevenLabsKey) {
    }
}
