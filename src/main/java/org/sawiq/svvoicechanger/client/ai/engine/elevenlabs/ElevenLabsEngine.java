package org.sawiq.svvoicechanger.client.ai.engine.elevenlabs;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettings;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.audio.FrameSplitter;
import org.sawiq.svvoicechanger.client.ai.audio.Resampler;
import org.sawiq.svvoicechanger.client.ai.engine.AiVoiceEngine;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus.Availability;
import org.sawiq.svvoicechanger.client.ai.engine.PhraseConverter;

/**
 * ElevenLabs' voice changer, with the player's own key.
 *
 * <p>There is no live voice changer in their API: it takes a finished audio
 * file and streams the converted version back. So this is a phrase engine,
 * and the player is heard about a second after letting go of the key.</p>
 */
public final class ElevenLabsEngine implements AiVoiceEngine.Phrase {
    /** Shown until the model list has been fetched once. */
    private static final List<EngineModel> KNOWN_MODELS = List.of(
            new EngineModel(AiVoiceSettings.ElevenLabs.DEFAULT_MODEL, "Multilingual STS v2", ""),
            new EngineModel("eleven_english_sts_v2", "English STS v2", ""));

    private final URI base;
    private volatile List<EngineModel> voiceChangerModels = KNOWN_MODELS;
    private volatile String accountSummary = "";

    public ElevenLabsEngine() {
        this(ElevenLabsApi.BASE);
    }

    /** For a test against a stand-in server; players always get ElevenLabs itself. */
    ElevenLabsEngine(URI base) {
        this.base = base;
    }

    @Override
    public EngineKind kind() {
        return EngineKind.ELEVENLABS;
    }

    /** The models that can change a voice, as last fetched. */
    public List<EngineModel> voiceChangerModels() {
        return this.voiceChangerModels;
    }

    @Override
    public EngineStatus probe(EngineSetup setup) {
        if (setup.elevenLabsKey().isBlank()) {
            return EngineStatus.of(kind(), Availability.NO_KEY, "");
        }

        ElevenLabsApi api = new ElevenLabsApi(setup.elevenLabsKey(), this.base);
        try {
            String credits = describeCredits(api);
            if (credits == null) {
                return EngineStatus.of(kind(), Availability.NO_CREDITS, this.accountSummary);
            }

            List<EngineModel> voices = fetchVoices(api);
            refreshModels(api);

            Availability availability = setup.settings().elevenLabs().hasVoice()
                    ? Availability.READY
                    : Availability.NO_MODEL;
            return new EngineStatus(kind(), availability, credits, voices);
        } catch (ElevenLabsException exception) {
            return EngineStatus.of(kind(), exception.availability(), exception.getMessage());
        } catch (IOException exception) {
            return EngineStatus.of(kind(), Availability.FAILED, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EngineStatus.checking(kind());
        }
    }

    @Override
    public PhraseConverter openPhrase(EngineSetup setup) throws IOException {
        if (setup.elevenLabsKey().isBlank()) {
            throw new IOException("No ElevenLabs key");
        }
        if (!setup.settings().elevenLabs().hasVoice()) {
            throw new IOException("No ElevenLabs voice selected");
        }
        return new Converter(new ElevenLabsApi(setup.elevenLabsKey(), this.base), setup.settings().elevenLabs());
    }

    /**
     * @return "used / limit", an empty string when the key may not read the
     *         account, or {@code null} when the account has nothing left
     */
    private String describeCredits(ElevenLabsApi api) throws IOException, InterruptedException {
        ElevenLabsApi.Account account;
        try {
            account = api.account();
        } catch (ElevenLabsException exception) {
            // A key restricted to the voice changer cannot read the account,
            // and is still a perfectly good key.
            if (exception.isMissingPermission()) {
                return "";
            }
            throw exception;
        }

        this.accountSummary = account.tier();
        if (account.creditsLimit() > 0 && account.creditsLeft() == 0) {
            return null;
        }
        return account.creditsLeft() + " / " + account.creditsLimit();
    }

    private static List<EngineModel> fetchVoices(ElevenLabsApi api) throws IOException, InterruptedException {
        try {
            return api.voices();
        } catch (ElevenLabsException exception) {
            if (exception.isMissingPermission()) {
                return List.of();
            }
            throw exception;
        }
    }

    private void refreshModels(ElevenLabsApi api) throws InterruptedException {
        try {
            List<EngineModel> models = api.voiceChangerModels();
            if (!models.isEmpty()) {
                this.voiceChangerModels = List.copyOf(models);
            }
        } catch (IOException ignored) {
            // The built-in list still works; the fetched one only adds newer models.
        }
    }

    private static final class Converter implements PhraseConverter {
        private final ElevenLabsApi api;
        private final AiVoiceSettings.ElevenLabs settings;

        Converter(ElevenLabsApi api, AiVoiceSettings.ElevenLabs settings) {
            this.api = api;
            this.settings = settings;
        }

        @Override
        public void convert(short[] phrase, Consumer<short[]> frames) throws IOException, InterruptedException {
            byte[] upload = AudioFrames.toLittleEndian(Resampler.convertAll(Resampler.from48kTo16k(), phrase));

            Resampler upsampler = Resampler.from24kTo48k();
            FrameSplitter splitter = new FrameSplitter(frames);
            ByteJoiner joiner = new ByteJoiner();

            this.api.convert(upload, this.settings, chunk -> {
                short[] samples24k = joiner.join(chunk);
                splitter.accept(upsampler.process(samples24k, 0, samples24k.length));
            });
            splitter.flush();
        }

        @Override
        public void close() {
            // Stateless between phrases: nothing to release.
        }
    }

    /**
     * Turns a byte stream into samples when the network splits it at an odd
     * byte, which would otherwise shift every following sample by half.
     */
    private static final class ByteJoiner {
        private int carriedByte = -1;

        short[] join(byte[] chunk) {
            byte[] bytes = chunk;
            if (this.carriedByte >= 0) {
                bytes = new byte[chunk.length + 1];
                bytes[0] = (byte) this.carriedByte;
                System.arraycopy(chunk, 0, bytes, 1, chunk.length);
            }

            int even = bytes.length & ~1;
            this.carriedByte = even < bytes.length ? bytes[bytes.length - 1] & 0xFF : -1;
            return AudioFrames.fromLittleEndian(bytes, 0, even);
        }
    }
}
