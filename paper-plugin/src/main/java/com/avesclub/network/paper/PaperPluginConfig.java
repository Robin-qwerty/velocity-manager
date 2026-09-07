package com.avesclub.network.paper;

import com.avesclub.network.common.RedisSettings;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PaperPluginConfig {

    private static final String DEFAULT_TAB_HEADER = "&6AvesClub Network\n&7Online: {network_count}";
    private static final String DEFAULT_TAB_FOOTER = "&eNetwork players:\n{network_players}";
    private static final String DEFAULT_PLAYER_LINE = "&a{player} &7({server})";

    private final String serverName;
    private final RedisSettings redis;
    private final boolean chatEnabled;
    private final boolean publicMessagesEnabled;
    private final boolean playerMessagesEnabled;
    private final boolean tabListEnabled;
    private final String tabHeader;
    private final String tabFooter;
    private final String playerLineFormat;

    public PaperPluginConfig(
            String serverName,
            RedisSettings redis,
            boolean chatEnabled,
            boolean publicMessagesEnabled,
            boolean playerMessagesEnabled,
            boolean tabListEnabled,
            String tabHeader,
            String tabFooter,
            String playerLineFormat
    ) {
        this.serverName = serverName == null || serverName.isBlank() ? "lobby" : serverName.trim();
        this.redis = redis == null ? RedisSettings.defaults() : redis;
        this.chatEnabled = chatEnabled;
        this.publicMessagesEnabled = publicMessagesEnabled;
        this.playerMessagesEnabled = playerMessagesEnabled;
        this.tabListEnabled = tabListEnabled;
        this.tabHeader = tabHeader == null || tabHeader.isBlank() ? DEFAULT_TAB_HEADER : tabHeader;
        this.tabFooter = tabFooter == null || tabFooter.isBlank() ? DEFAULT_TAB_FOOTER : tabFooter;
        this.playerLineFormat = playerLineFormat == null || playerLineFormat.isBlank()
                ? DEFAULT_PLAYER_LINE : playerLineFormat;
    }

    public String serverName() {
        return serverName;
    }

    public RedisSettings redis() {
        return redis;
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public boolean publicMessagesEnabled() {
        return publicMessagesEnabled;
    }

    public boolean playerMessagesEnabled() {
        return playerMessagesEnabled;
    }

    public boolean tabListEnabled() {
        return tabListEnabled;
    }

    public String tabHeader() {
        return tabHeader;
    }

    public String tabFooter() {
        return tabFooter;
    }

    public String playerLineFormat() {
        return playerLineFormat;
    }

    public static void ensureDefaultFile(Path dataDirectory, Class<?> resourceAnchor) throws IOException {
        if (Files.notExists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }
        Path configPath = dataDirectory.resolve("config.yml");
        if (Files.exists(configPath)) {
            return;
        }
        try (InputStream in = resourceAnchor.getResourceAsStream("/config.yml")) {
            if (in == null) {
                throw new IOException("Missing bundled /config.yml");
            }
            Files.copy(in, configPath);
        }
    }

    public static PaperPluginConfig load(Path dataDirectory) throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            return defaults();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(Files.newBufferedReader(configPath));
        if (root == null) {
            return defaults();
        }
        Object serverObj = root.get("server-name");
        String serverName = serverObj == null ? "lobby" : serverObj.toString().trim();
        if (serverName.isEmpty()) {
            serverName = "lobby";
        }
        RedisSettings redis = RedisSettings.fromYamlMap(root.get("redis"));

        boolean chatEnabled = true;
        boolean publicMessagesEnabled = true;
        boolean playerMessagesEnabled = true;
        boolean tabListEnabled = true;
        String tabHeader = DEFAULT_TAB_HEADER;
        String tabFooter = DEFAULT_TAB_FOOTER;
        String playerLine = DEFAULT_PLAYER_LINE;

        Object rawNetworkSync = root.get("network-sync");
        if (rawNetworkSync instanceof Map<?, ?> networkSync) {
            Object rawChat = networkSync.get("chat");
            if (rawChat instanceof Map<?, ?> chat) {
                chatEnabled = parseBoolean(chat.get("enabled"), true);
            } else if (rawChat != null) {
                chatEnabled = parseBoolean(rawChat, true);
            }
            Object rawMessages = networkSync.get("messages");
            if (rawMessages instanceof Map<?, ?> messages) {
                publicMessagesEnabled = parseBoolean(messages.get("public"), true);
                playerMessagesEnabled = parseBoolean(messages.get("player-specific"), true);
            }
            Object rawTab = networkSync.get("tablist");
            if (rawTab instanceof Map<?, ?> tab) {
                tabListEnabled = parseBoolean(tab.get("enabled"), true);
                Object headerObj = tab.get("header");
                if (headerObj != null) {
                    tabHeader = headerObj.toString();
                }
                Object footerObj = tab.get("footer");
                if (footerObj != null) {
                    tabFooter = footerObj.toString();
                }
                Object lineObj = tab.get("player-line");
                if (lineObj != null) {
                    playerLine = lineObj.toString();
                }
            }
        }

        return new PaperPluginConfig(
                serverName,
                redis,
                chatEnabled,
                publicMessagesEnabled,
                playerMessagesEnabled,
                tabListEnabled,
                tabHeader,
                tabFooter,
                playerLine
        );
    }

    public static PaperPluginConfig defaults() {
        return new PaperPluginConfig(
                "lobby",
                RedisSettings.defaults(),
                true,
                true,
                true,
                true,
                DEFAULT_TAB_HEADER,
                DEFAULT_TAB_FOOTER,
                DEFAULT_PLAYER_LINE
        );
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
