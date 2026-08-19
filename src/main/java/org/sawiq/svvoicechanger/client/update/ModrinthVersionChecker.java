package org.sawiq.svvoicechanger.client.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
//? if fabric {
import net.fabricmc.loader.api.FabricLoader;
//?}
//? if neoforge {
/*import net.neoforged.fml.ModList;
*///?}
import org.sawiq.svvoicechanger.SvVoiceChanger;

public final class ModrinthVersionChecker {
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/simple-voice-voice-changer/version";
    private static final String MODRINTH_PAGE = "https://modrinth.com/mod/simple-voice-voice-changer";
    private static final String CURSEFORGE_PAGE = "https://www.curseforge.com/minecraft/mc-mods/simple-voice-voice-changer";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final String currentVersion;
    private final String currentMinecraftVersion;
    private final String currentLoader;
    private boolean checked;

    public ModrinthVersionChecker() {
        this.currentVersion = resolveCurrentVersion();
        this.currentMinecraftVersion = resolveCurrentMinecraftVersion();
        this.currentLoader = resolveCurrentLoader();
    }

    public String currentVersion() {
        return this.currentVersion;
    }

    public CompletableFuture<Result> checkAsync() {
        if (this.checked) {
            return CompletableFuture.completedFuture(null);
        }
        this.checked = true;

        return CompletableFuture.supplyAsync(() -> {
            HttpURLConnection connection = null;
            try {
                URL endpoint = URI.create(MODRINTH_API).toURL();
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(READ_TIMEOUT_MS);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "sv-voice-changer/" + this.currentVersion);

                if (connection.getResponseCode() != 200) {
                    return null;
                }

                String json;
                try (InputStream input = connection.getInputStream()) {
                    json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }

                JsonElement root = JsonParser.parseString(json);
                if (!root.isJsonArray()) {
                    return null;
                }

                JsonArray versions = root.getAsJsonArray();
                JsonObject latest = null;
                String latestDate = "";

                for (JsonElement element : versions) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject version = element.getAsJsonObject();
                    if (!isCompatibleRelease(version)) {
                        continue;
                    }
                    String date = version.has("date_published") ? version.get("date_published").getAsString() : "";
                    if (latest == null || date.compareTo(latestDate) > 0) {
                        latest = version;
                        latestDate = date;
                    }
                }

                if (latest == null) {
                    return null;
                }

                String latestVersion = latest.has("version_number")
                        ? latest.get("version_number").getAsString()
                        : "unknown";
                if (isNewer(latestVersion, this.currentVersion)) {
                    return new Result(latestVersion, MODRINTH_PAGE, CURSEFORGE_PAGE);
                }

                return null;
            } catch (Exception exception) {
                // Network/API failures must never break the client.
                SvVoiceChanger.LOGGER.debug("Modrinth update check failed", exception);
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String resolveCurrentVersion() {
        //? if fabric {
        return FabricLoader.getInstance()
                .getModContainer(SvVoiceChanger.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        //?}
        //? if neoforge {
        /*return ModList.get()
                .getModContainerById(SvVoiceChanger.NEOFORGE_MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        *///?}
    }

    private static String resolveCurrentMinecraftVersion() {
        //? if fabric {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        //?}
        //? if neoforge {
        /*return ModList.get()
                .getModContainerById("minecraft")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        *///?}
    }

    private static String resolveCurrentLoader() {
        //? if fabric {
        return "fabric";
        //?}
        //? if neoforge {
        /*return "neoforge";
        *///?}
    }

    private boolean isCompatibleRelease(JsonObject version) {
        if (!hasStringValue(version, "version_type", "release")) {
            return false;
        }
        return hasArrayValue(version, "loaders", this.currentLoader)
                && hasArrayValue(version, "game_versions", this.currentMinecraftVersion);
    }

    private static boolean hasStringValue(JsonObject object, String memberName, String expected) {
        JsonElement value = object.get(memberName);
        return value != null && value.isJsonPrimitive() && expected.equals(value.getAsString());
    }

    private static boolean hasArrayValue(JsonObject object, String memberName, String expected) {
        JsonElement values = object.get(memberName);
        if (values == null || !values.isJsonArray()) {
            return false;
        }
        for (JsonElement value : values.getAsJsonArray()) {
            if (value.isJsonPrimitive() && expected.equals(value.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNewer(String remote, String local) {
        return compareVersions(normalizeVersion(remote), normalizeVersion(local)) > 0;
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "0";
        }

        String cleaned = version.trim();
        int plus = cleaned.indexOf('+');
        if (plus >= 0) {
            cleaned = cleaned.substring(0, plus);
        }
        return cleaned;
    }

    private static int compareVersions(String a, String b) {
        String[] partsA = a.split("[.-]");
        String[] partsB = b.split("[.-]");
        int max = Math.max(partsA.length, partsB.length);

        for (int i = 0; i < max; i++) {
            int numA = i < partsA.length ? parseIntSafe(partsA[i]) : 0;
            int numB = i < partsB.length ? parseIntSafe(partsB[i]) : 0;
            if (numA != numB) {
                return Integer.compare(numA, numB);
            }
        }

        return 0;
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public record Result(String version, String url, String curseForgeUrl) {
    }
}
