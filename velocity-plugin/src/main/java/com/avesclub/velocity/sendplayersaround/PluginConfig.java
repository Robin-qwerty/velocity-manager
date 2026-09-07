package com.avesclub.velocity.sendplayersaround;

import com.avesclub.network.common.RedisSettings;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code config.yml} for the Velocity plugin.
 */
public final class PluginConfig {

    private static final String DEFAULT_DISCORD_MESSAGE = "Join our Discord server!";
    private static final String DEFAULT_DISCORD_LINK_TEXT = "Click here";
    private static final String DEFAULT_DISCORD_URL = "https://discord.gg/avesclub";

    private final String lobbyServer;
    private final Map<String, String> domainServers;
    private final String discordMessage;
    private final String discordLinkText;
    private final String discordUrl;
    private final RedisSettings redis;
    private final boolean publishPresence;
    private final List<String> linkedServers;
    private final boolean tabListEnabled;
    private final boolean chatCompletionsEnabled;
    private final String tabPlayerLine;

    public PluginConfig(
            String lobbyServer,
            Map<String, String> domainServers,
            String discordMessage,
            String discordLinkText,
            String discordUrl,
            RedisSettings redis,
            boolean publishPresence,
            List<String> linkedServers,
            boolean tabListEnabled,
            boolean chatCompletionsEnabled,
            String tabPlayerLine
    ) {
        this.lobbyServer = lobbyServer == null || lobbyServer.isBlank() ? "lobby" : lobbyServer.trim();
        this.domainServers = domainServers == null ? Map.of() : Map.copyOf(domainServers);
        this.discordMessage = discordMessage == null || discordMessage.isBlank()
                ? DEFAULT_DISCORD_MESSAGE : discordMessage.trim();
        this.discordLinkText = discordLinkText == null || discordLinkText.isBlank()
                ? DEFAULT_DISCORD_LINK_TEXT : discordLinkText.trim();
        this.discordUrl = discordUrl == null || discordUrl.isBlank()
                ? DEFAULT_DISCORD_URL : discordUrl.trim();
        this.redis = redis == null ? RedisSettings.defaults() : redis;
        this.publishPresence = publishPresence;
        this.linkedServers = linkedServers == null ? List.of() : List.copyOf(linkedServers);
        this.tabListEnabled = tabListEnabled;
        this.chatCompletionsEnabled = chatCompletionsEnabled;
        this.tabPlayerLine = tabPlayerLine == null || tabPlayerLine.isBlank()
                ? "&a{player} &7({server})" : tabPlayerLine;
    }

    public String lobbyServer() {
        return lobbyServer;
    }

    public Map<String, String> domainServers() {
        return domainServers;
    }

    public String discordMessage() {
        return discordMessage;
    }

    public String discordLinkText() {
        return discordLinkText;
    }

    public String discordUrl() {
        return discordUrl;
    }

    public RedisSettings redis() {
        return redis;
    }

    public boolean publishPresence() {
        return publishPresence;
    }

    /**
     * Velocity server names that participate in network sync. Empty list = all servers.
     */
    public List<String> linkedServers() {
        return linkedServers;
    }

    public boolean tabListEnabled() {
        return tabListEnabled;
    }

    public boolean chatCompletionsEnabled() {
        return chatCompletionsEnabled;
    }

    public String tabPlayerLine() {
        return tabPlayerLine;
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

    public static PluginConfig load(Path dataDirectory) throws IOException {
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            return defaults();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(Files.newBufferedReader(configPath));
        if (root == null) {
            return defaults();
        }
        Object lobbyObj = root.get("lobby-server");
        String lobby = lobbyObj == null ? "lobby" : lobbyObj.toString().trim();
        if (lobby.isEmpty()) {
            lobby = "lobby";
        }
        Map<String, String> domains = new LinkedHashMap<>();
        Object rawDomains = root.get("domain-servers");
        if (rawDomains instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String key = e.getKey().toString().trim().toLowerCase(Locale.ROOT);
                String value = e.getValue().toString().trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    domains.put(key, value);
                }
            }
        }
        String discordMessage = DEFAULT_DISCORD_MESSAGE;
        String discordLinkText = DEFAULT_DISCORD_LINK_TEXT;
        String discordUrl = DEFAULT_DISCORD_URL;
        Object rawDiscord = root.get("discord");
        if (rawDiscord instanceof Map<?, ?> discord) {
            Object messageObj = discord.get("message");
            if (messageObj != null) {
                discordMessage = messageObj.toString();
            }
            Object linkTextObj = discord.get("link-text");
            if (linkTextObj != null) {
                discordLinkText = linkTextObj.toString();
            }
            Object urlObj = discord.get("url");
            if (urlObj != null) {
                discordUrl = urlObj.toString();
            }
        }
        RedisSettings redis = RedisSettings.fromYamlMap(root.get("redis"));
        boolean publishPresence = true;
        List<String> linkedServers = List.of();
        boolean tabListEnabled = true;
        boolean chatCompletionsEnabled = true;
        String tabPlayerLine = "&a{player} &7({server})";
        Object rawNetworkSync = root.get("network-sync");
        if (rawNetworkSync instanceof Map<?, ?> networkSync) {
            Object publishObj = networkSync.get("publish-presence");
            if (publishObj instanceof Boolean bool) {
                publishPresence = bool;
            } else if (publishObj != null) {
                publishPresence = Boolean.parseBoolean(publishObj.toString());
            }
            linkedServers = parseLinkedServers(networkSync.get("linked-servers"));
            Object rawTab = networkSync.get("tablist");
            if (rawTab instanceof Map<?, ?> tab) {
                Object enabledObj = tab.get("enabled");
                if (enabledObj instanceof Boolean bool) {
                    tabListEnabled = bool;
                } else if (enabledObj != null) {
                    tabListEnabled = Boolean.parseBoolean(enabledObj.toString());
                }
                Object lineObj = tab.get("player-line");
                if (lineObj != null) {
                    tabPlayerLine = lineObj.toString();
                }
            }
            Object completionsObj = networkSync.get("chat-completions");
            if (completionsObj instanceof Boolean bool) {
                chatCompletionsEnabled = bool;
            } else if (completionsObj != null) {
                chatCompletionsEnabled = Boolean.parseBoolean(completionsObj.toString());
            }
        }
        return new PluginConfig(
                lobby,
                Collections.unmodifiableMap(domains),
                discordMessage,
                discordLinkText,
                discordUrl,
                redis,
                publishPresence,
                linkedServers,
                tabListEnabled,
                chatCompletionsEnabled,
                tabPlayerLine
        );
    }

    private static List<String> parseLinkedServers(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> servers = new ArrayList<>();
        for (Object entry : list) {
            if (entry == null) {
                continue;
            }
            String name = entry.toString().trim();
            if (!name.isEmpty()) {
                servers.add(name);
            }
        }
        return List.copyOf(servers);
    }

    private static PluginConfig defaults() {
        return new PluginConfig(
                "lobby", Map.of(), null, null, null, RedisSettings.defaults(), true, List.of(),
                true, true, "&a{player} &7({server})"
        );
    }
}
