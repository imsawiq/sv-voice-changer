package org.sawiq.svvoicechanger.client.ai.engine.local;

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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
 * The companion Voice Changer Engine, which runs voice conversion models on
 * this computer's GPU or CPU.
 *
 * <p>The protocol is the mod's own and deliberately small, documented in
 * {@code docs/VOICE_ENGINE.md}:</p>
 * <ul>
 *     <li>{@code GET /v1/status} - device, models, the selected one</li>
 *     <li>{@code POST /v1/configure} - JSON: session, model, pitch</li>
 *     <li>{@code POST /v1/convert?session=...} - raw 48 kHz mono s16le in,
 *     the same length converted back</li>
 * </ul>
 * <p>The session id lets the engine keep each client's context apart, so the
 * Plasmo Voice and Simple Voice Chat builds could share one engine.</p>
 */
public final class VoiceEngine implements AiVoiceEngine.Live {
    /** What the engine said it can do beyond converting, as of the last status. */
    private volatile Set<String> features = Set.of();
    public static final int PROTOCOL_VERSION = 1;
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration CONVERT_TIMEOUT = Duration.ofSeconds(5);

    @Override
    public EngineKind kind() {
        return EngineKind.VOICE_ENGINE;
    }

    @Override
    public EngineStatus probe(EngineSetup setup) {
        String configured = setup.settings().voiceEngine().address();
        Optional<LocalAddress> address = LocalAddress.parse(configured);
        if (address.isEmpty()) {
            return EngineStatus.of(kind(), Availability.FAILED, configured);
        }

        JsonObject status;
        try {
            HttpRequest request = HttpRequest.newBuilder(address.get().uri("/v1/status"))
                    .timeout(PROBE_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (!EngineHttp.isSuccess(response)) {
                return notReady(response);
            }
            status = EngineHttp.parseObject(response.body());
        } catch (ConnectException | HttpConnectTimeoutException exception) {
            return EngineStatus.of(kind(), Availability.NOT_RUNNING, address.get().toString());
        } catch (IOException exception) {
            return EngineStatus.of(kind(), Availability.FAILED, exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return EngineStatus.checking(kind());
        }

        if (EngineHttp.integer(status, "protocol", 0) != PROTOCOL_VERSION) {
            return EngineStatus.of(kind(), Availability.FAILED,
                    "protocol " + EngineHttp.integer(status, "protocol", 0));
        }

        this.features = readFeatures(status);
        List<EngineModel> models = readModels(status);
        String device = EngineHttp.string(status, "device", "");
        Availability availability = models.isEmpty() ? Availability.NO_MODEL : Availability.READY;
        return new EngineStatus(kind(), availability, device, models);
    }

    /**
     * An engine that answers but is not ready. On its first start it spends
     * minutes downloading speech models and says so with {"starting": true}
     * and the stage it is at; that is not a failure, and the player should
     * see the stage rather than an error code.
     */
    private EngineStatus notReady(HttpResponse<byte[]> response) {
        try {
            JsonObject body = EngineHttp.parseObject(response.body());
            if (EngineHttp.bool(body, "starting", false)) {
                String stage = EngineHttp.string(body, "error", "");
                int colon = stage.indexOf(':');
                return EngineStatus.of(kind(), Availability.STARTING,
                        colon >= 0 ? stage.substring(colon + 1).strip() : stage);
            }
        } catch (IOException notJson) {
            // Not the engine's own answer; the status code is all there is.
        }
        return EngineStatus.of(kind(), Availability.FAILED, "HTTP " + response.statusCode());
    }

    @Override
    public LiveConverter openLive(EngineSetup setup) throws IOException {
        AiVoiceSettings.LocalEngine settings = setup.settings().voiceEngine();
        LocalAddress address = LocalAddress.parse(settings.address())
                .orElseThrow(() -> new IOException("Not a local address: " + settings.address()));

        String session = UUID.randomUUID().toString();
        JsonObject configuration = new JsonObject();
        configuration.addProperty("session", session);
        configuration.addProperty("model", settings.modelId());
        configuration.addProperty("pitch", settings.pitch());

        HttpRequest request = HttpRequest.newBuilder(address.uri("/v1/configure"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(configuration.toString()))
                .build();
        try {
            // Long timeout: picking a model the engine has not loaded yet
            // means reading it from disk onto the GPU first.
            HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (!EngineHttp.isSuccess(response)) {
                throw new IOException("Voice engine refused the model: " + errorOf(response.body()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while configuring the voice engine", exception);
        }

        return new Converter(address.uri("/v1/convert?session=" + session), settings.chunkMillis());
    }

    /** Whether the engine said it can do this, such as "preview" or "dataset_check". */
    public boolean supports(String feature) {
        return this.features.contains(feature);
    }

    /**
     * A few seconds of a fixed phrase in the given voice, for the voice
     * gallery's play button. Blocking.
     *
     * @return mono 48 kHz samples
     */
    public short[] preview(EngineSetup setup, String model, int pitch) throws IOException, InterruptedException {
        LocalAddress address = address(setup);
        String query = "?model=" + URLEncoder.encode(model, StandardCharsets.UTF_8) + "&pitch=" + pitch;
        HttpRequest request = HttpRequest.newBuilder(address.uri("/v1/preview" + query))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException("Voice engine: " + errorOf(response.body()));
        }
        return AudioFrames.fromLittleEndian(response.body(), 0, response.body().length);
    }

    /** Asks the engine whether a folder of recordings is good enough to train on. Blocking. */
    public DatasetReport checkDataset(EngineSetup setup, Path folder) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("folder", folder.toAbsolutePath().toString());
        HttpRequest request = HttpRequest.newBuilder(address(setup).uri("/v1/dataset/check"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException("Voice engine: " + errorOf(response.body()));
        }

        JsonObject report = EngineHttp.parseObject(response.body());
        List<String> tips = new ArrayList<>();
        JsonElement tipList = report.get("tips");
        if (tipList != null && tipList.isJsonArray()) {
            for (JsonElement tip : tipList.getAsJsonArray()) {
                if (tip.isJsonPrimitive()) {
                    tips.add(tip.getAsString());
                }
            }
        }
        double minutes = report.has("minutes") && report.get("minutes").isJsonPrimitive()
                ? report.get("minutes").getAsDouble() : 0.0D;
        return new DatasetReport(
                EngineHttp.integer(report, "files", 0), minutes,
                EngineHttp.integer(report, "tooQuiet", 0),
                EngineHttp.integer(report, "clipped", 0),
                EngineHttp.integer(report, "noisy", 0),
                EngineHttp.string(report, "verdict", ""), tips);
    }

    /**
     * Starts training a new voice from a folder of recordings. Blocking, but
     * only until the engine has accepted the job; the job itself runs in the
     * engine and is followed with {@link #trainingStatus}.
     *
     * @param resume pick up a cancelled or failed job of the same name from its last save
     * @throws IOException with the engine's reason when it refuses, such as a
     *                     name already taken or another job running
     */
    public void startTraining(EngineSetup setup, String name, Path folder, int epochs, boolean resume)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.addProperty("folder", folder.toAbsolutePath().toString());
        body.addProperty("epochs", epochs);
        body.addProperty("resume", resume);
        post(setup, "/v1/train", body, Duration.ofSeconds(30));
    }

    public TrainingStatus trainingStatus(EngineSetup setup) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address(setup).uri("/v1/train/status"))
                .timeout(PROBE_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException("Voice engine: " + errorOf(response.body()));
        }

        JsonObject status = EngineHttp.parseObject(response.body());
        return new TrainingStatus(
                EngineHttp.string(status, "state", "idle"),
                EngineHttp.string(status, "name", ""),
                EngineHttp.string(status, "stage", ""),
                EngineHttp.integer(status, "stageIndex", 0),
                EngineHttp.integer(status, "stageCount", 0),
                number(status, "percent"),
                EngineHttp.integer(status, "epoch", 0),
                EngineHttp.integer(status, "epochs", 0),
                EngineHttp.integer(status, "etaSeconds", -1),
                EngineHttp.string(status, "lastLog", ""),
                EngineHttp.string(status, "error", ""));
    }

    /** Stops the job after its current step; what it learned so far is kept for a resume. */
    public void cancelTraining(EngineSetup setup) throws IOException, InterruptedException {
        post(setup, "/v1/train/cancel", new JsonObject(), PROBE_TIMEOUT);
    }

    private static void post(EngineSetup setup, String path, JsonObject body, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(address(setup).uri(path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<byte[]> response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (!EngineHttp.isSuccess(response)) {
            throw new IOException(errorOf(response.body()));
        }
    }

    private static double number(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()
                ? value.getAsDouble()
                : 0.0D;
    }

    private static LocalAddress address(EngineSetup setup) throws IOException {
        String configured = setup.settings().voiceEngine().address();
        return LocalAddress.parse(configured).orElseThrow(() -> new IOException("Not a local address: " + configured));
    }

    private static Set<String> readFeatures(JsonObject status) {
        Set<String> features = new HashSet<>();
        JsonElement list = status.get("features");
        if (list != null && list.isJsonArray()) {
            for (JsonElement element : list.getAsJsonArray()) {
                if (element.isJsonPrimitive()) {
                    features.add(element.getAsString());
                }
            }
        }
        return Set.copyOf(features);
    }

    private static List<EngineModel> readModels(JsonObject status) {
        List<EngineModel> models = new ArrayList<>();
        JsonElement list = status.get("models");
        if (list == null || !list.isJsonArray()) {
            return models;
        }

        for (JsonElement element : list.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject model = element.getAsJsonObject();
            String id = EngineHttp.string(model, "id", "");
            if (!id.isBlank()) {
                models.add(new EngineModel(id, EngineHttp.string(model, "name", id),
                        EngineHttp.string(model, "kind", "")));
            }
        }
        return models;
    }

    private static String errorOf(byte[] body) {
        try {
            return EngineHttp.string(EngineHttp.parseObject(body), "error", "unknown error");
        } catch (IOException exception) {
            return "unknown error";
        }
    }

    private static final class Converter extends ChunkedLiveConverter {
        private final URI endpoint;

        Converter(URI endpoint, int chunkMillis) {
            super("Voice changer engine", chunkMillis);
            this.endpoint = endpoint;
            start();
        }

        @Override
        protected short[] convertChunk(short[] chunk) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(this.endpoint)
                    .timeout(CONVERT_TIMEOUT)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(AudioFrames.toLittleEndian(chunk)))
                    .build();
            HttpResponse<byte[]> response;
            try {
                response = EngineHttp.local().send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (HttpTimeoutException exception) {
                throw new IOException("The voice engine did not answer in time", exception);
            }
            if (!EngineHttp.isSuccess(response)) {
                throw new IOException("Voice engine: " + errorOf(response.body()));
            }
            return AudioFrames.fromLittleEndian(response.body(), 0, response.body().length);
        }
    }
}
