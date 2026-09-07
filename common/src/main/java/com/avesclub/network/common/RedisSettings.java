package com.avesclub.network.common;

import java.util.Locale;
import java.util.Map;

public record RedisSettings(
        boolean enabled,
        String host,
        int port,
        String password,
        String channelPrefix
) {
    public static RedisSettings defaults() {
        return new RedisSettings(false, "localhost", 6379, "", "robin-network");
    }

    public static RedisSettings fromYamlMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return defaults();
        }
        boolean enabled = parseBoolean(map.get("enabled"), false);
        String host = stringValue(map.get("host"), "localhost");
        int port = parseInt(map.get("port"), 6379);
        String password = stringValue(map.get("password"), "");
        String prefix = stringValue(map.get("channel-prefix"), "robin-network");
        return new RedisSettings(enabled, host, port, password, prefix);
    }

    public String chatChannel() {
        return channelPrefix + ":chat";
    }

    public String presenceChannel() {
        return channelPrefix + ":presence";
    }

    public String controlChannel() {
        return channelPrefix + ":control";
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private static int parseInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.toString().trim().toLowerCase(Locale.ROOT));
    }
}
