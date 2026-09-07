package com.avesclub.network.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;

public final class MessageSyncListener implements Listener {

    private final NetworkMessageService messages;

    public MessageSyncListener(NetworkMessageService messages) {
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!messages.isParticipating()) {
            return;
        }
        Component rendered = ChatCapture.capture(event);

        Set<UUID> viewers = NetworkMessageService.playerViewers(event.viewers());
        int onlineCount = Bukkit.getOnlinePlayers().size();
        if (viewers.isEmpty() || viewers.size() >= onlineCount) {
            messages.publishChat(event.getPlayer(), rendered);
            return;
        }

        event.setCancelled(true);
        messages.publishToPlayers(viewers, rendered);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        if (messages.isParticipating()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        if (messages.isParticipating()) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!messages.isParticipating()) {
            return;
        }
        Component deathMessage = event.deathMessage();
        if (deathMessage == null) {
            return;
        }
        event.deathMessage(null);
        messages.publishPublic(deathMessage);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!messages.isParticipating()) {
            return;
        }
        Component advancementMessage = event.message();
        if (advancementMessage == null) {
            return;
        }
        event.message(null);
        messages.publishPublic(advancementMessage);
    }
}
