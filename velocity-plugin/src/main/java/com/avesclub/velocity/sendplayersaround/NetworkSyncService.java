package com.avesclub.velocity.sendplayersaround;

import com.avesclub.network.common.LinkedServerFilter;
import com.avesclub.network.common.NetworkMessages;
import com.avesclub.network.common.NetworkPlayer;
import com.avesclub.network.common.RedisBus;
import com.avesclub.network.common.RedisSettings;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes network-wide player presence to Redis for Paper tab list sync.
 */
public final class NetworkSyncService {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Map<UUID, String> lastServer = new ConcurrentHashMap<>();
    private RedisBus redisBus;
    private RedisSettings settings;
    private boolean publishPresence;
    private LinkedServerFilter linkedServers = LinkedServerFilter.all();

    public NetworkSyncService(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    public void start(RedisSettings settings, boolean publishPresence, List<String> linkedServerNames) {
        stop();
        this.settings = settings;
        this.publishPresence = publishPresence;
        this.linkedServers = LinkedServerFilter.of(linkedServerNames);
        if (!settings.enabled() || !publishPresence) {
            logger.info("Network presence publishing is disabled.");
            return;
        }
        validateLinkedServers();
        redisBus = new RedisBus(settings);
        redisBus.connect();
        redisBus.subscribe(settings.controlChannel(), this::handleControlMessage);
        publishConfig();
        if (linkedServers.linksAll()) {
            logger.info("Redis presence sync enabled on {}:{} (all servers linked)",
                    settings.host(), settings.port());
        } else {
            logger.info("Redis presence sync enabled on {}:{} (linked servers: {})",
                    settings.host(), settings.port(), linkedServers.configuredNames());
        }
    }

    public void stop() {
        lastServer.clear();
        if (redisBus != null) {
            redisBus.close();
            redisBus = null;
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (!isActive()) {
            return;
        }
        Player player = event.getPlayer();
        String server = event.getServer().getServerInfo().getName();
        String previous = lastServer.put(player.getUniqueId(), server);

        if (previous == null) {
            if (linkedServers.isLinked(server)) {
                publish(NetworkMessages.presence(
                        NetworkMessages.ACTION_JOIN,
                        player.getUniqueId(),
                        player.getUsername(),
                        server,
                        null
                ));
            }
            return;
        }
        if (previous.equals(server)) {
            return;
        }
        boolean wasLinked = linkedServers.isLinked(previous);
        boolean isLinked = linkedServers.isLinked(server);
        if (wasLinked && !isLinked) {
            publish(NetworkMessages.presence(
                    NetworkMessages.ACTION_QUIT,
                    player.getUniqueId(),
                    player.getUsername(),
                    null,
                    null
            ));
        } else if (!wasLinked && isLinked) {
            publish(NetworkMessages.presence(
                    NetworkMessages.ACTION_JOIN,
                    player.getUniqueId(),
                    player.getUsername(),
                    server,
                    null
            ));
        } else if (wasLinked && isLinked) {
            publish(NetworkMessages.presence(
                    NetworkMessages.ACTION_SWITCH,
                    player.getUniqueId(),
                    player.getUsername(),
                    server,
                    previous
            ));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (!isActive()) {
            return;
        }
        Player player = event.getPlayer();
        String previous = lastServer.remove(player.getUniqueId());
        if (previous != null && linkedServers.isLinked(previous)) {
            publish(NetworkMessages.presence(
                    NetworkMessages.ACTION_QUIT,
                    player.getUniqueId(),
                    player.getUsername(),
                    null,
                    null
            ));
        }
    }

    private void handleControlMessage(String raw) {
        try {
            NetworkMessages.ParsedMessage message = NetworkMessages.parse(raw);
            if (message instanceof NetworkMessages.Resync) {
                publishConfig();
                publishSnapshot();
            }
        } catch (RuntimeException ex) {
            logger.warn("Invalid Redis control message: {}", raw, ex);
        }
    }

    private void publishSnapshot() {
        if (!isActive()) {
            return;
        }
        List<NetworkPlayer> players = new ArrayList<>();
        for (Player player : proxyServer.getAllPlayers()) {
            String server = player.getCurrentServer()
                    .map(connection -> connection.getServerInfo().getName())
                    .orElse("unknown");
            if (!linkedServers.isLinked(server)) {
                continue;
            }
            players.add(new NetworkPlayer(player.getUniqueId(), player.getUsername(), server));
        }
        publish(NetworkMessages.snapshot(players));
    }

    private void publishConfig() {
        if (redisBus == null || settings == null) {
            return;
        }
        redisBus.publish(settings.controlChannel(), NetworkMessages.config(linkedServers.configuredNames()));
    }

    private void publish(String payload) {
        if (redisBus == null || settings == null) {
            return;
        }
        redisBus.publish(settings.presenceChannel(), payload);
    }

    private void validateLinkedServers() {
        if (linkedServers.linksAll()) {
            return;
        }
        for (String serverName : linkedServers.configuredNames()) {
            if (proxyServer.getServer(serverName).isEmpty()) {
                logger.warn("linked-servers contains '{}' but no such server exists in velocity.toml", serverName);
            }
        }
    }

    private boolean isActive() {
        return redisBus != null && publishPresence;
    }
}
