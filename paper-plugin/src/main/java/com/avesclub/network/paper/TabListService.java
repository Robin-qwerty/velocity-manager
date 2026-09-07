package com.avesclub.network.paper;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Paper no longer manages network tab entries or chat completions (Velocity handles those).
 * This class only clears optional header/footer when a server is unlinked.
 */
public final class TabListService {

    private final RobinPaperBridgePlugin plugin;

    public TabListService(RobinPaperBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void clearAll() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
        });
    }
}
