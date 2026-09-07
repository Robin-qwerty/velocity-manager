package com.avesclub.velocity.sendplayersaround;

import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.Optional;

/**
 * Normalizes the hostname a client connected with, shared by domain routing and the MOTD listener
 * so both agree on what "the domain" is for a given connection.
 */
final class HostUtil {

    private HostUtil() {
    }

    static String hostFromPlayer(Player player) {
        Optional<String> raw = player.getRawVirtualHost();
        if (raw.isPresent()) {
            return stripHostPort(raw.get().trim().toLowerCase(Locale.ROOT));
        }
        return player.getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .orElse("");
    }

    static String hostFromConnection(InboundConnection connection) {
        Optional<String> raw = connection.getRawVirtualHost();
        if (raw.isPresent()) {
            return stripHostPort(raw.get().trim().toLowerCase(Locale.ROOT));
        }
        return connection.getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .map(h -> h.toLowerCase(Locale.ROOT))
                .orElse("");
    }

    static String stripHostPort(String hostWithPossiblePort) {
        if (hostWithPossiblePort.isEmpty()) {
            return hostWithPossiblePort;
        }
        if (hostWithPossiblePort.startsWith("[")) {
            int close = hostWithPossiblePort.indexOf(']');
            if (close != -1 && hostWithPossiblePort.length() > close + 1
                    && hostWithPossiblePort.charAt(close + 1) == ':') {
                return hostWithPossiblePort.substring(1, close).toLowerCase(Locale.ROOT);
            }
            return hostWithPossiblePort;
        }
        int colon = hostWithPossiblePort.lastIndexOf(':');
        if (colon > 0) {
            String tail = hostWithPossiblePort.substring(colon + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                return hostWithPossiblePort.substring(0, colon);
            }
        }
        return hostWithPossiblePort;
    }
}
