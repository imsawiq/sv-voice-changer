package org.sawiq.svvoicechanger.client.ai.engine.wokada;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.sawiq.svvoicechanger.client.ai.AiVoiceSettings;
import org.sawiq.svvoicechanger.client.ai.LocalAddress;
import org.sawiq.svvoicechanger.client.ai.audio.AudioFrames;
import org.sawiq.svvoicechanger.client.ai.engine.AiVoiceEngine;
import org.sawiq.svvoicechanger.client.ai.engine.ChunkedLiveConverter;
import org.sawiq.svvoicechanger.client.ai.engine.EngineHttp;
import org.sawiq.svvoicechanger.client.ai.engine.EngineKind;
import org.sawiq.svvoicechanger.client.ai.engine.EngineModel;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus;
import org.sawiq.svvoicechanger.client.ai.engine.EngineStatus.Availability;
import org.sawiq.svvoicechanger.client.ai.engine.LiveConverter;

/**
 * w-okada's Voice Changer (MMVCServerSIO), driven over the REST interface its
 * own browser client uses.
 *
 * <p>{@code GET /info} lists the model slots and the GPU, {@code POST
 * /update_settings} picks a slot and the pitch, and {@code POST /test} takes
 * base64 PCM at 48 kHz and answers with the converted audio of the same
 * length. The server keeps the previous chunk as context, so chunks join up.
 * These are internal endpoints rather than a published API, so a status that
 * reads "not running" next to a running w-okada most likely means its
 * interface moved.</p>
 */
public final class WOkadaEngine implements AiVoiceEngine.Live {
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CONVERT_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public EngineKind kind() {
        return EngineKind.W_OKADA;
    }

    @Override
    public EngineStatus probe(EngineSetup setup) {
        Optional<LocalAddress> address = LocalAddress.parse(setup.settings().wOkada().address());
        if (address.isEmpty()) {
            return EngineStatus.of(kind(), Availability.FAILED, setup.settings().wOkada().address());
        }

        JsonObject info;
        try {
            info = fetchInfo(address.get());
        } catch (ConnectException | HttpConnectTimeoutException exception) {
            return EngineStatus.of(kind(), Availability.NOT_RUNNING, address.get().toString());
        } catch (IOException exception) {
            return EngineStatus.of(kind(), Availability.FAILED, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EngineStatus.checking(kind());
        }

        List<EngineModel> models = readModels(info);
        String gpu = describeGpu(info);
        if (models.isEmpty()) {
            return new EngineStatus(kind(), Availability.NO_MODEL, gpu, models);
        }
        return new EngineStatus(kind(), Availability.READY, gpu, models);
    }

    @Override
    public LiveConverter openLive(EngineSetup setup) throws IOException {
        AiVoiceSettings.LocalEngine settings = setup.settings().wOkada();
        LocalAddress address = LocalAddress.parse(settings.address())
                .orElseThrow(() -> new IOException("Not a local address: " + settings.address()));

        try {
            if (!settings.modelId().isBlank()) {
                updateSetting(address, "modelSlotIndex", settings.modelId());
            }
            updateSetting(address, "tran", Integer.toString(settings.pitch()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while configuring w-okada", exception);
        }

        return new Converter(address, settings.chunkMillis());
    }

    private static JsonObject fetchInfo(LocalAddress address) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address.uri("/info"))
                .timeout(PROBE_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException("w-okada answered " + response.statusCode());
        }
        return EngineHttp.parseObject(response.body());
    }

    private static void updateSetting(LocalAddress address, String key, String value)
            throws IOException, InterruptedException {
        String form = "key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&val=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(address.uri("/update_settings"))
                .timeout(PROBE_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<Void> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.discarding());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException("w-okada refused " + key + ": " + response.statusCode());
        }
    }

    /** Empty slots come back with no voice changer type and are skipped. */
    private static List<EngineModel> readModels(JsonObject info) {
        List<EngineModel> models = new ArrayList<>();
        JsonElement slots = info.get("modelSlots");
        if (slots == null || !slots.isJsonArray()) {
            return models;
        }

        for (JsonElement element : (JsonArray) slots) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject slot = element.getAsJsonObject();
            String type = EngineHttp.string(slot, "voiceChangerType", "");
            int index = EngineHttp.integer(slot, "slotIndex", -1);
            if (type.isBlank() || index < 0) {
                continue;
            }
            String name = EngineHttp.string(slot, "name", "");
            models.add(new EngineModel(Integer.toString(index), name.isBlank() ? "#" + index : name, type));
        }
        return models;
    }

    private static String describeGpu(JsonObject info) {
        JsonElement gpus = info.get("gpus");
        if (gpus == null || !gpus.isJsonArray() || gpus.getAsJsonArray().isEmpty()) {
            return "";
        }
        JsonElement first = gpus.getAsJsonArray().get(0);
        return first.isJsonObject() ? EngineHttp.string(first.getAsJsonObject(), "name", "") : "";
    }

    private static final class Converter extends ChunkedLiveConverter {
        private final URI endpoint;

        Converter(LocalAddress address, int chunkMillis) {
            super("Voice changer w-okada", chunkMillis);
            this.endpoint = address.uri("/test");
            start();
        }

        @Override
        protected short[] convertChunk(short[] chunk) throws IOException, InterruptedException {
            JsonObject body = new JsonObject();
            body.addProperty("timestamp", System.currentTimeMillis());
            body.addProperty("buffer", Base64.getEncoder().encodeToString(AudioFrames.toLittleEndian(chunk)));

            HttpRequest request = HttpRequest.newBuilder(this.endpoint)
                    .timeout(CONVERT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<byte[]> response;
            try {
                response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (HttpTimeoutException exception) {
                throw new IOException("w-okada did not answer in time", exception);
            }
            if (!EngineHttp.isSuccess(response)) {
                throw new IOException("w-okada answered " + response.statusCode());
            }

            // On a conversion error the server still answers 200, with the
            // error text as a bare JSON string instead of an object.
            JsonObject answer = EngineHttp.parseObject(response.body());
            String converted = EngineHttp.string(answer, "changedVoiceBase64", null);
            if (converted == null) {
                throw new IOException("w-okada sent no audio; is a model loaded?");
            }
            byte[] pcm = Base64.getDecoder().decode(converted);
            return AudioFrames.fromLittleEndian(pcm, 0, pcm.length);
        }
    }
}
