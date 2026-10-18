package com.avesclub.velocity.sendplayersaround;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Optional;

/**
 * Sends a join or quit message when a player enters or leaves a configured server group.
 * Players already inside that group are the only recipients. A switch that stays inside one
 * group is silent, and servers that are not included never trigger or receive a message.
 */
final class JoinLeaveListener {

    private final ProxyServer proxyServer;
    private volatile PluginConfig.JoinLeaveMessages settings = PluginConfig.JoinLeaveMessages.disabled();

    JoinLeaveListener(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    void applyConfig(PluginConfig config) {
        this.settings = config == null || config.joinLeaveMessages() == null
                ? PluginConfig.JoinLeaveMessages.disabled()
                : config.joinLeaveMessages();
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        PluginConfig.JoinLeaveMessages cfg = settings;
        if (!cfg.enabled()) {
            return;
        }

        Optional<String> newScope = cfg.scope(event.getServer().getServerInfo().getName());
        Optional<String> oldScope = event.getPreviousServer()
                .flatMap(server -> cfg.scope(server.getServerInfo().getName()));

        if (newScope.isPresent() && oldScope.isPresent() && newScope.get().equals(oldScope.get())) {
            return;
        }

        String username = event.getPlayer().getUsername();
        if (oldScope.isPresent()) {
            broadcast(cfg, cfg.quitMessage(), username, oldScope.get(), event.getPlayer());
        }
        if (newScope.isPresent()) {
            broadcast(cfg, cfg.joinMessage(), username, newScope.get(), null);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        PluginConfig.JoinLeaveMessages cfg = settings;
        if (!cfg.enabled()) {
            return;
        }
        Optional<String> scope = event.getPlayer().getCurrentServer()
                .flatMap(connection -> cfg.scope(connection.getServerInfo().getName()));
        if (scope.isEmpty()) {
            return;
        }
        broadcast(cfg, cfg.quitMessage(), event.getPlayer().getUsername(), scope.get(), event.getPlayer());
    }

    private void broadcast(
            PluginConfig.JoinLeaveMessages cfg,
            String template,
            String username,
            String scope,
            Player exclude
    ) {
        if (template == null || template.isBlank()) {
            return;
        }
        Component message = render(template, username);
        for (Player online : proxyServer.getAllPlayers()) {
            if (exclude != null && online.getUniqueId().equals(exclude.getUniqueId())) {
                continue;
            }
            Optional<String> playerScope = online.getCurrentServer()
                    .flatMap(connection -> cfg.scope(connection.getServerInfo().getName()));
            if (playerScope.isPresent() && playerScope.get().equals(scope)) {
                online.sendMessage(message);
            }
        }
    }

    private static Component render(String template, String username) {
        String mini = MiniMessageBridge.legacyToMiniMessage(template);
        String safeName = MiniMessage.miniMessage().escapeTags(username == null ? "" : username);
        return MiniMessage.miniMessage().deserialize(mini.replace("%player%", safeName));
    }
}
