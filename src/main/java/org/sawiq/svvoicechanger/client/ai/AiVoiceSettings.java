package org.sawiq.svvoicechanger.client.ai;

import org.sawiq.svvoicechanger.client.ai.engine.EngineChoice;

/**
 * Everything the player set up for the AI voice, apart from the ElevenLabs
 * key, which is kept in a file of its own so it never travels with a settings
 * file somebody shares.
 *
 * @param useAi whether the voice changer speaks with the AI voice instead of
 *              the effect presets; the studio's on/off switch still decides
 *              whether anything is changed at all
 */
public record AiVoiceSettings(
        boolean useAi,
        EngineChoice engine,
        ElevenLabs elevenLabs,
        LocalEngine voiceEngine,
        LocalEngine wOkada
) {
    public static final int MIN_PITCH = -24;
    public static final int MAX_PITCH = 24;
    public static final int MIN_CHUNK_MILLIS = 60;
    public static final int MAX_CHUNK_MILLIS = 500;

    public static AiVoiceSettings defaults() {
        return new AiVoiceSettings(
                false,
                EngineChoice.AUTO,
                ElevenLabs.defaults(),
                new LocalEngine("127.0.0.1:18889", "", 0, 120),
                new LocalEngine("127.0.0.1:18888", "", 0, 120));
    }

    public AiVoiceSettings withUseAi(boolean value) {
        return new AiVoiceSettings(value, this.engine, this.elevenLabs, this.voiceEngine, this.wOkada);
    }

    public AiVoiceSettings withEngine(EngineChoice value) {
        return new AiVoiceSettings(this.useAi, value, this.elevenLabs, this.voiceEngine, this.wOkada);
    }

    public AiVoiceSettings withElevenLabs(ElevenLabs value) {
        return new AiVoiceSettings(this.useAi, this.engine, value, this.voiceEngine, this.wOkada);
    }

    public AiVoiceSettings withVoiceEngine(LocalEngine value) {
        return new AiVoiceSettings(this.useAi, this.engine, this.elevenLabs, value, this.wOkada);
    }

    public AiVoiceSettings withWOkada(LocalEngine value) {
        return new AiVoiceSettings(this.useAi, this.engine, this.elevenLabs, this.voiceEngine, value);
    }

    /**
     * ElevenLabs voice changer settings. The three sliders are percentages and
     * are sent as 0..1, which is how ElevenLabs takes them.
     *
     * @param voiceName kept so the studio can name the voice before the voice
     *                  list has been fetched
     */
    public record ElevenLabs(
            String voiceId,
            String voiceName,
            String modelId,
            int stability,
            int similarity,
            int style,
            boolean speakerBoost,
            boolean removeNoise
    ) {
        public static final String DEFAULT_MODEL = "eleven_multilingual_sts_v2";

        public ElevenLabs {
            voiceId = voiceId == null ? "" : voiceId;
            voiceName = voiceName == null ? "" : voiceName;
            modelId = modelId == null || modelId.isBlank() ? DEFAULT_MODEL : modelId;
            stability = clampPercent(stability);
            similarity = clampPercent(similarity);
            style = clampPercent(style);
        }

        public static ElevenLabs defaults() {
            return new ElevenLabs("", "", DEFAULT_MODEL, 50, 75, 0, true, true);
        }

        public ElevenLabs withVoice(String id, String name) {
            return new ElevenLabs(id, name, this.modelId, this.stability, this.similarity, this.style,
                    this.speakerBoost, this.removeNoise);
        }

        public ElevenLabs withModel(String id) {
            return new ElevenLabs(this.voiceId, this.voiceName, id, this.stability, this.similarity, this.style,
                    this.speakerBoost, this.removeNoise);
        }

        public ElevenLabs withStability(int value) {
            return new ElevenLabs(this.voiceId, this.voiceName, this.modelId, value, this.similarity, this.style,
                    this.speakerBoost, this.removeNoise);
        }

        public ElevenLabs withSimilarity(int value) {
            return new ElevenLabs(this.voiceId, this.voiceName, this.modelId, this.stability, value, this.style,
                    this.speakerBoost, this.removeNoise);
        }

        public ElevenLabs withStyle(int value) {
            return new ElevenLabs(this.voiceId, this.voiceName, this.modelId, this.stability, this.similarity, value,
                    this.speakerBoost, this.removeNoise);
        }

        public ElevenLabs withSpeakerBoost(boolean value) {
            return new ElevenLabs(this.voiceId, this.voiceName, this.modelId, this.stability, this.similarity,
                    this.style, value, this.removeNoise);
        }

        public ElevenLabs withRemoveNoise(boolean value) {
            return new ElevenLabs(this.voiceId, this.voiceName, this.modelId, this.stability, this.similarity,
                    this.style, this.speakerBoost, value);
        }

        public boolean hasVoice() {
            return !this.voiceId.isBlank();
        }
    }

    /**
     * A converter running on this computer.
     *
     * @param address     host:port it listens on; only loopback addresses are
     *                    accepted, see {@link LocalAddress}
     * @param modelId     the model to speak with, empty for whatever the
     *                    engine has loaded
     * @param pitch       semitones to move the voice, for a model trained on a
     *                    voice higher or lower than the player's
     * @param chunkMillis how much audio is sent per request: shorter answers
     *                    sooner, longer sounds smoother on a slow GPU
     */
    public record LocalEngine(String address, String modelId, int pitch, int chunkMillis) {
        public LocalEngine {
            address = address == null ? "" : address.trim();
            modelId = modelId == null ? "" : modelId;
            pitch = Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
            chunkMillis = Math.max(MIN_CHUNK_MILLIS, Math.min(MAX_CHUNK_MILLIS, chunkMillis));
        }

        public LocalEngine withAddress(String value) {
            return new LocalEngine(value, this.modelId, this.pitch, this.chunkMillis);
        }

        public LocalEngine withModel(String value) {
            return new LocalEngine(this.address, value, this.pitch, this.chunkMillis);
        }

        public LocalEngine withPitch(int value) {
            return new LocalEngine(this.address, this.modelId, value, this.chunkMillis);
        }

        public LocalEngine withChunkMillis(int value) {
            return new LocalEngine(this.address, this.modelId, this.pitch, value);
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
