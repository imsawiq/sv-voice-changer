package org.sawiq.svvoicechanger.client.ai.engine;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.sawiq.svvoicechanger.SvVoiceChanger;

/**
 * The proxy the player's VPN set up for the whole system, for the requests
 * that leave the computer.
 *
 * <p>Many VPN clients do not route all traffic themselves but set a system
 * proxy (Windows' "Internet Settings"). Java ignores that proxy, so without
 * this the game would talk to ElevenLabs directly, which from some countries
 * is simply refused. Local engines never go through it: a proxy cannot reach
 * this computer's own 127.0.0.1.</p>
 *
 * <p>Only plain HTTP proxies are used, because that is all Java's HTTP client
 * speaks; a SOCKS-only setup falls back to a direct connection.</p>
 */
public final class SystemProxy {
    private static final String WINDOWS_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";

    private SystemProxy() {
    }

    public static ProxySelector selector() {
        Optional<InetSocketAddress> proxy = address();
        proxy.ifPresent(address -> SvVoiceChanger.LOGGER.info(
                "AI voice: cloud requests go through the system proxy {}", address));
        return proxy.map(ProxySelector::of).orElseGet(SystemProxy::javaDefault);
    }

    /** The system proxy's address, for handing to programs that read HTTPS_PROXY. */
    public static Optional<InetSocketAddress> address() {
        return windowsProxy().or(SystemProxy::environmentProxy);
    }

    /** Read once at startup with reg.exe, which every Windows has. */
    private static Optional<InetSocketAddress> windowsProxy() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return Optional.empty();
        }
        String settings = run("reg", "query", WINDOWS_KEY);
        if (!enabled(settings)) {
            return Optional.empty();
        }
        return valueOf(settings, "ProxyServer").flatMap(SystemProxy::parseWindowsServer);
    }

    private static Optional<InetSocketAddress> environmentProxy() {
        for (String name : new String[]{"HTTPS_PROXY", "https_proxy", "HTTP_PROXY", "http_proxy"}) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                try {
                    URI uri = URI.create(value.contains("://") ? value : "http://" + value);
                    if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getPort() > 0) {
                        return Optional.of(InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
                    }
                } catch (IllegalArgumentException ignored) {
                    // Not an address; try the next variable.
                }
            }
        }
        return Optional.empty();
    }

    private static boolean enabled(String settings) {
        return valueOf(settings, "ProxyEnable").map(value -> value.endsWith("1")).orElse(false);
    }

    /** {@code reg query} prints "    Name    REG_TYPE    value" per line. */
    private static Optional<String> valueOf(String settings, String name) {
        for (String line : settings.split("\\R")) {
            String[] parts = line.trim().split("\\s+", 3);
            if (parts.length == 3 && parts[0].equalsIgnoreCase(name)) {
                return Optional.of(parts[2].trim());
            }
        }
        return Optional.empty();
    }

    /**
     * Either one "host:port" for everything, or "http=h:p;https=h:p;socks=h:p".
     * The https entry wins, then http.
     */
    static Optional<InetSocketAddress> parseWindowsServer(String server) {
        String chosen = null;
        if (!server.contains("=")) {
            chosen = server;
        } else {
            for (String scheme : new String[]{"https", "http"}) {
                for (String entry : server.split(";")) {
                    String[] pair = entry.split("=", 2);
                    if (chosen == null && pair.length == 2 && pair[0].trim().equalsIgnoreCase(scheme)) {
                        chosen = pair[1].trim();
                    }
                }
            }
        }
        if (chosen == null) {
            return Optional.empty();
        }

        chosen = chosen.replaceFirst("^[a-zA-Z]+://", "");
        int colon = chosen.lastIndexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        try {
            int port = Integer.parseInt(chosen.substring(colon + 1).replace("/", ""));
            return Optional.of(InetSocketAddress.createUnresolved(chosen.substring(0, colon), port));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (InputStream output = process.getInputStream()) {
                byte[] bytes = output.readAllBytes();
                process.waitFor(3, TimeUnit.SECONDS);
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            return "";
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /** Java's own choice when the system names no proxy: usually a direct connection. */
    private static ProxySelector javaDefault() {
        ProxySelector selector = ProxySelector.getDefault();
        return selector != null ? selector : ProxySelector.of(null);
    }
}
