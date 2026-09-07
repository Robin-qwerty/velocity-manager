package com.avesclub.network.paper;

import com.avesclub.network.common.LinkedServerFilter;
import com.avesclub.network.common.NetworkMessages;
import com.avesclub.network.common.RedisBus;
import com.avesclub.network.common.RedisSettings;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class NetworkMessageService {

    private final JavaPlugin plugin;
    private final PaperPluginConfig config;
    private RedisBus redisBus;
    private LinkedServerFilter linkedServers = LinkedServerFilter.all();
    private boolean participating;

    public NetworkMessageService(JavaPlugin plugin, PaperPluginConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.participating = false;
    }

    public void attachRedis(RedisBus redisBus) {
        this.redisBus = redisBus;
    }

    public void applyLinkedServers(java.util.List<String> linkedServerNames) {
        LinkedServerFilter previous = linkedServers;
        linkedServers = LinkedServerFilter.of(linkedServerNames);
        participating = linkedServers.isLinked(config.serverName());
        if (participating && !previous.isLinked(config.serverName())) {
            plugin.getLogger().info("Server '" + config.serverName() + "' is linked for network sync.");
        } else if (!participating) {
            plugin.getLogger().info("Server '" + config.serverName() + "' is not in Velocity linked-servers; network sync disabled.");
        }
    }

    public boolean isParticipating() {
        return participating;
    }

    public LinkedServerFilter linkedServers() {
        return linkedServers;
    }

    public void publishChat(Player player, Component renderedMessage) {
        if (!canPublish()) {
            return;
        }
        redisBus.publish(
                config.redis().chatChannel(),
                NetworkMessages.chat(
                        config.serverName(),
                        player.getUniqueId(),
                        player.getName(),
                        ComponentCodec.serialize(renderedMessage)
                )
        );
    }

    public void publishPublic(Component message) {
        if (!canPublish() || !config.publicMessagesEnabled()) {
            return;
        }
        redisBus.publish(
                config.redis().chatChannel(),
                NetworkMessages.publicMessage(config.serverName(), ComponentCodec.serialize(message))
        );
    }

    public void publishToPlayers(Set<UUID> targets, Component message) {
        if (!canPublish() || !config.playerMessagesEnabled() || targets.isEmpty()) {
            return;
        }
        String payload = ComponentCodec.serialize(message);
        for (UUID target : targets) {
            redisBus.publish(
                    config.redis().chatChannel(),
                    NetworkMessages.playerMessage(config.serverName(), target, payload)
            );
        }
    }

    public void handleRedisPayload(String raw) {
        try {
            NetworkMessages.ParsedMessage parsed = NetworkMessages.parse(raw);
            if (parsed instanceof NetworkMessages.Chat chat) {
                handleChat(chat);
            } else if (parsed instanceof NetworkMessages.Public publicMessage) {
                handlePublic(publicMessage);
            } else if (parsed instanceof NetworkMessages.Player playerMessage) {
                handlePlayer(playerMessage);
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Invalid Redis message payload: " + raw, ex);
        }
    }

    private void handleChat(NetworkMessages.Chat chat) {
        if (!participating || !config.chatEnabled() || !linkedServers.isLinked(chat.server())) {
            return;
        }
        // The originating backend already showed this through normal chat handling.
        if (chat.server().equalsIgnoreCase(config.serverName())) {
            return;
        }
        deliverToAll(ComponentCodec.deserialize(chat.componentJson()));
    }

    private void handlePublic(NetworkMessages.Public publicMessage) {
        if (!participating || !config.publicMessagesEnabled() || !linkedServers.isLinked(publicMessage.server())) {
            return;
        }
        deliverToAll(ComponentCodec.deserialize(publicMessage.componentJson()));
    }

    private void handlePlayer(NetworkMessages.Player playerMessage) {
        if (!participating || !config.playerMessagesEnabled() || !linkedServers.isLinked(playerMessage.server())) {
            return;
        }
        Player target = Bukkit.getPlayer(playerMessage.targetUuid());
        if (target == null) {
            return;
        }
        deliverToPlayer(target, ComponentCodec.deserialize(playerMessage.componentJson()));
    }

    public void deliverToAll(Component component) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(component);
            }
        });
    }

    public void deliverToPlayer(Player player, Component component) {
        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(component));
    }

    public static Set<UUID> playerViewers(Set<Audience> viewers) {
        Set<UUID> targets = new LinkedHashSet<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player) {
                targets.add(player.getUniqueId());
            }
        }
        return targets;
    }

    private boolean canPublish() {
        return participating && redisBus != null && config.redis().enabled();
    }
}
