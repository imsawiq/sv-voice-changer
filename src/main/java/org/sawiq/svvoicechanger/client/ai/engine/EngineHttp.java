package org.sawiq.svvoicechanger.client.ai.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The two HTTP clients the engines share, and the JSON handling they all need.
 *
 * <p>Local engines get HTTP/1.1 and a one second connect timeout: they are on
 * this machine or the next one, a refused connection simply means "not
 * running", and keeping one connection alive is what makes a request every
 * 120 ms cheap. The cloud client allows HTTP/2 and waits longer.</p>
 */
public final class EngineHttp {
    /**
     * Never through a proxy: a VPN or a launcher that sets one would otherwise
     * send requests for this computer's own engine off to a proxy that cannot
     * reach it, and the engine would read as "not running" while it runs.
     */
    private static final HttpClient LOCAL = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(1))
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();
    /** Through the system proxy a VPN sets up, see {@link SystemProxy}. */
    private static final HttpClient CLOUD = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(SystemProxy.selector())
            .build();

    /** Bodies above this are refused rather than parsed; no engine answer here comes close. */
    public static final int MAX_JSON_BYTES = 4 * 1024 * 1024;

    private EngineHttp() {
    }

    public static HttpClient local() {
        return LOCAL;
    }

    public static HttpClient cloud() {
        return CLOUD;
    }

    public static boolean isSuccess(HttpResponse<?> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    /** Parses a response body that must be a JSON object. */
    public static JsonObject parseObject(byte[] body) throws IOException {
        if (body.length > MAX_JSON_BYTES) {
            throw new IOException("Response of " + body.length + " bytes is too large");
        }
        try {
            JsonElement root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new IOException("Expected a JSON object");
            }
            return root.getAsJsonObject();
        } catch (JsonParseException exception) {
            throw new IOException("Not valid JSON", exception);
        }
    }

    public static String string(JsonObject object, String member, String fallback) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    public static int integer(JsonObject object, String member, int fallback) {
        JsonElement value = object.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return value.getAsInt();
    }

    public static boolean bool(JsonObject object, String member, boolean fallback) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }
}
