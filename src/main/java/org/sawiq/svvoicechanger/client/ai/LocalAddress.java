package org.sawiq.svvoicechanger.client.ai;

import java.net.URI;
import java.util.Optional;

/**
 * The host:port of a local engine, checked before any microphone audio is sent
 * to it.
 *
 * <p>Only this computer and the private ranges of a home network are
 * accepted, written as an IPv4 literal or {@code localhost}. w-okada can run
 * on a second machine with the GPU, which is why the private ranges are
 * allowed at all; anything that would leave the local network is refused, so
 * a settings file cannot quietly turn the "local" engine into an upload. No
 * name is resolved, which also keeps the check from blocking on DNS.</p>
 */
public record LocalAddress(String host, int port) {
    public static Optional<LocalAddress> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }

        String trimmed = text.trim();
        int colon = trimmed.lastIndexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) {
            return Optional.empty();
        }

        String host = trimmed.substring(0, colon);
        int port;
        try {
            port = Integer.parseInt(trimmed.substring(colon + 1));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        if (port < 1 || port > 65_535 || !isLocal(host)) {
            return Optional.empty();
        }
        return Optional.of(new LocalAddress(host, port));
    }

    /**
     * "localhost" is sent as 127.0.0.1: Windows can resolve it to the IPv6
     * ::1 first, where an engine listening on IPv4 only never answers.
     */
    public URI uri(String path) {
        String target = "localhost".equalsIgnoreCase(this.host) ? "127.0.0.1" : this.host;
        return URI.create("http://" + target + ":" + this.port + path);
    }

    @Override
    public String toString() {
        return this.host + ":" + this.port;
    }

    private static boolean isLocal(String host) {
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }

        int[] octets = parseIpv4(host);
        if (octets == null) {
            return false;
        }
        return octets[0] == 127
                || octets[0] == 10
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
    }

    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }

        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3 || !parts[i].chars().allMatch(Character::isDigit)) {
                return null;
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }
}
