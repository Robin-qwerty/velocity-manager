package com.avesclub.velocity.sendplayersaround;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final boolean domainRoutingEnabled;
    private final boolean discordCommandEnabled;
    private final boolean lobbyHubCommandsEnabled;

    public PluginConfig(
            String lobbyServer,
            Map<String, String> domainServers,
            String discordMessage,
            String discordLinkText,
            String discordUrl,
            boolean domainRoutingEnabled,
            boolean discordCommandEnabled,
            boolean lobbyHubCommandsEnabled
    ) {
        this.lobbyServer = lobbyServer == null || lobbyServer.isBlank() ? "lobby" : lobbyServer.trim();
        this.domainServers = domainServers == null ? Map.of() : Map.copyOf(domainServers);
        this.discordMessage = discordMessage == null || discordMessage.isBlank()
                ? DEFAULT_DISCORD_MESSAGE : discordMessage.trim();
        this.discordLinkText = discordLinkText == null || discordLinkText.isBlank()
                ? DEFAULT_DISCORD_LINK_TEXT : discordLinkText.trim();
        this.discordUrl = discordUrl == null || discordUrl.isBlank()
                ? DEFAULT_DISCORD_URL : discordUrl.trim();
        this.domainRoutingEnabled = domainRoutingEnabled;
        this.discordCommandEnabled = discordCommandEnabled;
        this.lobbyHubCommandsEnabled = lobbyHubCommandsEnabled;
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

    public boolean domainRoutingEnabled() {
        return domainRoutingEnabled;
    }

    public boolean discordCommandEnabled() {
        return discordCommandEnabled;
    }

    public boolean lobbyHubCommandsEnabled() {
        return lobbyHubCommandsEnabled;
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
        boolean domainRoutingEnabled = true;
        boolean discordCommandEnabled = true;
        boolean lobbyHubCommandsEnabled = true;
        Object rawFeatures = root.get("features");
        if (rawFeatures instanceof Map<?, ?> features) {
            domainRoutingEnabled = boolOrDefault(features.get("domain-routing"), true);
            discordCommandEnabled = boolOrDefault(features.get("discord-command"), true);
            lobbyHubCommandsEnabled = boolOrDefault(features.get("lobby-hub-commands"), true);
        }
        return new PluginConfig(
                lobby,
                Collections.unmodifiableMap(domains),
                discordMessage,
                discordLinkText,
                discordUrl,
                domainRoutingEnabled,
                discordCommandEnabled,
                lobbyHubCommandsEnabled
        );
    }

    private static boolean boolOrDefault(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.toString().trim());
    }

    private static PluginConfig defaults() {
        return new PluginConfig(
                "lobby", Map.of(), null, null, null, true, true, true
        );
    }
}
