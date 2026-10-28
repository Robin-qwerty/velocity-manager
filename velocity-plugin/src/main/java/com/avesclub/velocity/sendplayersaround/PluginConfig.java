package com.avesclub.velocity.sendplayersaround;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final JoinLeaveMessages joinLeaveMessages;
    private final NetworkPlayers networkPlayers;

    public PluginConfig(
            String lobbyServer,
            Map<String, String> domainServers,
            String discordMessage,
            String discordLinkText,
            String discordUrl,
            boolean domainRoutingEnabled,
            boolean discordCommandEnabled,
            boolean lobbyHubCommandsEnabled,
            JoinLeaveMessages joinLeaveMessages,
            NetworkPlayers networkPlayers
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
        this.joinLeaveMessages = joinLeaveMessages == null ? JoinLeaveMessages.disabled() : joinLeaveMessages;
        this.networkPlayers = networkPlayers == null ? NetworkPlayers.disabled() : networkPlayers;
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

    public JoinLeaveMessages joinLeaveMessages() {
        return joinLeaveMessages;
    }

    public NetworkPlayers networkPlayers() {
        return networkPlayers;
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

    public static PluginConfig load(Path dataDirectory, Logger logger) throws IOException {
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
                lobbyHubCommandsEnabled,
                parseJoinLeave(root.get("join-leave-messages"), logger),
                parseNetworkPlayers(root.get("network-players"), logger)
        );
    }

    private static JoinLeaveMessages parseJoinLeave(Object raw, Logger logger) {
        if (!(raw instanceof Map<?, ?> section)) {
            return JoinLeaveMessages.disabled();
        }
        boolean enabled = boolOrDefault(section.get("enabled"), false);
        String joinMessage = messageOrDefault(section.get("join-message"), JoinLeaveMessages.DEFAULT_JOIN_MESSAGE);
        String quitMessage = messageOrDefault(section.get("quit-message"), JoinLeaveMessages.DEFAULT_QUIT_MESSAGE);

        Set<String> included = new LinkedHashSet<>();
        for (String server : stringList(section.get("included-servers"))) {
            included.add(server.toLowerCase(Locale.ROOT));
        }

        Map<String, String> serverToGroup = new LinkedHashMap<>();
        Object rawGroups = section.get("groups");
        if (rawGroups instanceof Map<?, ?> groups) {
            for (Map.Entry<?, ?> entry : groups.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String groupId = entry.getKey().toString().trim().toLowerCase(Locale.ROOT);
                if (groupId.isEmpty()) {
                    continue;
                }
                for (String server : stringList(entry.getValue())) {
                    String key = server.toLowerCase(Locale.ROOT);
                    if (!included.contains(key)) {
                        if (logger != null) {
                            logger.warn(
                                    "join-leave-messages group '{}' lists server '{}' which is not in included-servers; ignoring",
                                    groupId, server);
                        }
                        continue;
                    }
                    String existing = serverToGroup.putIfAbsent(key, groupId);
                    if (existing != null && !existing.equals(groupId) && logger != null) {
                        logger.warn(
                                "join-leave-messages server '{}' is listed in groups '{}' and '{}'; keeping '{}'",
                                server, existing, groupId, existing);
                    }
                }
            }
        } else if (rawGroups != null && logger != null) {
            logger.warn("join-leave-messages.groups must be a map of group name to server list; ignoring");
        }

        return new JoinLeaveMessages(enabled, joinMessage, quitMessage, Set.copyOf(included), Map.copyOf(serverToGroup));
    }

    private static NetworkPlayers parseNetworkPlayers(Object raw, Logger logger) {
        if (!(raw instanceof Map<?, ?> section)) {
            return NetworkPlayers.disabled();
        }
        boolean enabled = boolOrDefault(section.get("enabled"), false);
        Map<?, ?> redis = section.get("redis") instanceof Map<?, ?> r ? r : Map.of();
        String host = stringOrDefault(redis.get("host"), NetworkPlayers.DEFAULT_HOST);
        int port = intOrDefault(redis.get("port"), NetworkPlayers.DEFAULT_PORT);
        String username = stringOrDefault(redis.get("username"), "");
        String password = stringOrDefault(redis.get("password"), "");
        int database = Math.max(0, intOrDefault(redis.get("database"), 0));
        String proxyId = stringOrDefault(section.get("proxy-id"), "");
        int publishInterval = Math.max(1, intOrDefault(section.get("publish-interval-seconds"),
                NetworkPlayers.DEFAULT_PUBLISH_INTERVAL_SECONDS));
        int keyTtl = intOrDefault(section.get("key-ttl-seconds"), NetworkPlayers.DEFAULT_KEY_TTL_SECONDS);
        String keyPrefix = stringOrDefault(section.get("key-prefix"), NetworkPlayers.DEFAULT_KEY_PREFIX);

        if (enabled && proxyId.isEmpty()) {
            if (logger != null) {
                logger.warn("network-players.proxy-id is empty; network player count disabled");
            }
            enabled = false;
        }
        if (keyTtl <= publishInterval) {
            int fixed = publishInterval * 3;
            if (enabled && logger != null) {
                logger.warn("network-players.key-ttl-seconds ({}) must be greater than publish-interval-seconds ({}); using {}",
                        keyTtl, publishInterval, fixed);
            }
            keyTtl = fixed;
        }
        return new NetworkPlayers(enabled, host, port, username, password, database, proxyId,
                publishInterval, keyTtl, keyPrefix);
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return value.toString().trim();
    }

    private static int intOrDefault(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String messageOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? "" : text;
    }

    private static List<String> stringList(Object raw) {
        if (raw instanceof String single) {
            String text = single.trim();
            return text.isEmpty() ? List.of() : List.of(text);
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String text = item.toString().trim();
            if (!text.isEmpty()) {
                out.add(text);
            }
        }
        return out;
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
                "lobby", Map.of(), null, null, null, true, true, true, JoinLeaveMessages.disabled(),
                NetworkPlayers.disabled()
        );
    }

    /**
     * Join and quit chat sent by the proxy. A server participates only when it is in
     * {@code included-servers}. Servers in the same named group share one scope; an included
     * server that is not in any group is its own scope.
     */
    public static final class JoinLeaveMessages {
        static final String DEFAULT_JOIN_MESSAGE = "&8[&a+&8] &a%player%";
        static final String DEFAULT_QUIT_MESSAGE = "&8[&c-&8] &c%player%";
        private static final String SINGLE_SERVER_SCOPE_PREFIX = "server:";

        private final boolean enabled;
        private final String joinMessage;
        private final String quitMessage;
        private final Set<String> includedServers;
        private final Map<String, String> serverToGroup;

        JoinLeaveMessages(
                boolean enabled,
                String joinMessage,
                String quitMessage,
                Set<String> includedServers,
                Map<String, String> serverToGroup
        ) {
            this.enabled = enabled;
            this.joinMessage = joinMessage == null ? DEFAULT_JOIN_MESSAGE : joinMessage;
            this.quitMessage = quitMessage == null ? DEFAULT_QUIT_MESSAGE : quitMessage;
            this.includedServers = includedServers == null ? Set.of() : Set.copyOf(includedServers);
            this.serverToGroup = serverToGroup == null ? Map.of() : Map.copyOf(serverToGroup);
        }

        public static JoinLeaveMessages disabled() {
            return new JoinLeaveMessages(false, DEFAULT_JOIN_MESSAGE, DEFAULT_QUIT_MESSAGE, Set.of(), Map.of());
        }

        public boolean enabled() {
            return enabled;
        }

        public String joinMessage() {
            return joinMessage;
        }

        public String quitMessage() {
            return quitMessage;
        }

        /**
         * Scope used to decide whether a server change is a join, a quit, or a silent switch.
         * Empty when the server is not included.
         */
        public Optional<String> scope(String serverName) {
            if (serverName == null || serverName.isBlank()) {
                return Optional.empty();
            }
            String key = serverName.trim().toLowerCase(Locale.ROOT);
            if (!includedServers.contains(key)) {
                return Optional.empty();
            }
            String group = serverToGroup.get(key);
            if (group != null) {
                return Optional.of(group);
            }
            return Optional.of(SINGLE_SERVER_SCOPE_PREFIX + key);
        }
    }

    /**
     * Shared online count across proxies. Each proxy writes its own count to Redis under
     * {@code key-prefix + proxy-id} with a TTL, and the server-list ping shows the sum of all keys.
     */
    public static final class NetworkPlayers {
        static final String DEFAULT_HOST = "127.0.0.1";
        static final int DEFAULT_PORT = 6379;
        static final int DEFAULT_PUBLISH_INTERVAL_SECONDS = 3;
        static final int DEFAULT_KEY_TTL_SECONDS = 10;
        static final String DEFAULT_KEY_PREFIX = "avesclub:players:";

        private final boolean enabled;
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        private final int database;
        private final String proxyId;
        private final int publishIntervalSeconds;
        private final int keyTtlSeconds;
        private final String keyPrefix;

        NetworkPlayers(
                boolean enabled,
                String host,
                int port,
                String username,
                String password,
                int database,
                String proxyId,
                int publishIntervalSeconds,
                int keyTtlSeconds,
                String keyPrefix
        ) {
            this.enabled = enabled;
            this.host = host == null || host.isBlank() ? DEFAULT_HOST : host;
            this.port = port <= 0 ? DEFAULT_PORT : port;
            this.username = username == null ? "" : username;
            this.password = password == null ? "" : password;
            this.database = database;
            this.proxyId = proxyId == null ? "" : proxyId;
            this.publishIntervalSeconds = publishIntervalSeconds;
            this.keyTtlSeconds = keyTtlSeconds;
            this.keyPrefix = keyPrefix == null || keyPrefix.isEmpty() ? DEFAULT_KEY_PREFIX : keyPrefix;
        }

        public static NetworkPlayers disabled() {
            return new NetworkPlayers(false, DEFAULT_HOST, DEFAULT_PORT, "", "", 0, "",
                    DEFAULT_PUBLISH_INTERVAL_SECONDS, DEFAULT_KEY_TTL_SECONDS, DEFAULT_KEY_PREFIX);
        }

        public boolean enabled() {
            return enabled;
        }

        public String host() {
            return host;
        }

        public int port() {
            return port;
        }

        public String username() {
            return username;
        }

        public String password() {
            return password;
        }

        public int database() {
            return database;
        }

        public String proxyId() {
            return proxyId;
        }

        public int publishIntervalSeconds() {
            return publishIntervalSeconds;
        }

        public int keyTtlSeconds() {
            return keyTtlSeconds;
        }

        public String keyPrefix() {
            return keyPrefix;
        }

        public String ownKey() {
            return keyPrefix + proxyId;
        }
    }
}
