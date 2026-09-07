package com.avesclub.velocity.sendplayersaround;

import com.avesclub.network.common.LinkedServerFilter;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network-wide tab list entries and chat tab-complete, managed on Velocity.
 */
public final class VelocityNetworkUiService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Map<UUID, Set<UUID>> managedRemoteEntries = new ConcurrentHashMap<>();

    private LinkedServerFilter linkedServers = LinkedServerFilter.all();
    private boolean tabListEnabled = true;
    private boolean chatCompletionsEnabled = true;
    private String tabPlayerLine = "&a{player} &7({server})";

    public VelocityNetworkUiService(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    public void applyConfig(PluginConfig config) {
        this.linkedServers = LinkedServerFilter.of(config.linkedServers());
        this.tabListEnabled = config.tabListEnabled();
        this.chatCompletionsEnabled = config.chatCompletionsEnabled();
        this.tabPlayerLine = config.tabPlayerLine();
        refreshAll();
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        refreshAll();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        managedRemoteEntries.remove(event.getPlayer().getUniqueId());
        refreshAll();
    }

    public void refreshAll() {
        for (Player viewer : proxyServer.getAllPlayers()) {
            refreshFor(viewer);
        }
    }

    private void refreshFor(Player viewer) {
        String viewerServer = viewer.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
        if (viewerServer == null || !linkedServers.isLinked(viewerServer)) {
            viewer.setCustomChatCompletions(List.of());
            clearManagedEntries(viewer);
            return;
        }

        if (chatCompletionsEnabled) {
            syncChatCompletions(viewer);
        } else {
            viewer.setCustomChatCompletions(List.of());
        }

        if (tabListEnabled) {
            syncRemoteTabEntries(viewer, viewerServer);
        } else {
            clearManagedEntries(viewer);
        }
    }

    private void syncChatCompletions(Player viewer) {
        List<String> names = new ArrayList<>();
        for (Player other : proxyServer.getAllPlayers()) {
            if (other.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            String otherServer = currentServerName(other);
            if (otherServer != null && linkedServers.isLinked(otherServer)) {
                names.add(other.getUsername());
            }
        }
        viewer.setCustomChatCompletions(names);
    }

    private void syncRemoteTabEntries(Player viewer, String viewerServer) {
        TabList tabList = viewer.getTabList();
        Set<UUID> desired = new HashSet<>();
        Set<UUID> managed = managedRemoteEntries.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());

        for (Player target : proxyServer.getAllPlayers()) {
            if (target.getUniqueId().equals(viewer.getUniqueId())) {
                continue;
            }
            String targetServer = currentServerName(target);
            if (targetServer == null || !linkedServers.isLinked(targetServer)) {
                continue;
            }
            if (targetServer.equalsIgnoreCase(viewerServer)) {
                continue;
            }

            UUID targetId = target.getUniqueId();
            desired.add(targetId);
            Component displayName = formatTabName(target.getUsername(), targetServer);

            if (tabList.getEntry(targetId).isPresent()) {
                tabList.getEntry(targetId).ifPresent(entry -> {
                    entry.setDisplayName(displayName);
                    entry.setLatency((int) target.getPing());
                });
                managed.add(targetId);
                continue;
            }

            GameProfile profile = target.getGameProfile();
            TabListEntry entry = TabListEntry.builder()
                    .tabList(tabList)
                    .profile(profile)
                    .displayName(displayName)
                    .latency((int) target.getPing())
                    .gameMode(0)
                    .listed(true)
                    .build();
            try {
                tabList.addEntry(entry);
                managed.add(targetId);
            } catch (IllegalArgumentException ex) {
                logger.debug("Could not add {} to {}'s tab list: {}", target.getUsername(), viewer.getUsername(), ex.getMessage());
            }
        }

        Set<UUID> toRemove = new HashSet<>(managed);
        toRemove.removeAll(desired);
        for (UUID remoteId : toRemove) {
            tabList.removeEntry(remoteId);
            managed.remove(remoteId);
        }
    }

    private void clearManagedEntries(Player viewer) {
        TabList tabList = viewer.getTabList();
        Set<UUID> managed = managedRemoteEntries.remove(viewer.getUniqueId());
        if (managed == null) {
            return;
        }
        for (UUID remoteId : managed) {
            tabList.removeEntry(remoteId);
        }
    }

    private Component formatTabName(String player, String server) {
        String rendered = tabPlayerLine
                .replace("{player}", player)
                .replace("{server}", server);
        return LEGACY.deserialize(rendered);
    }

    private static String currentServerName(Player player) {
        return player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse(null);
    }
}
