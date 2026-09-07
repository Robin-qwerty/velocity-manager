package com.avesclub.network.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Captures the chat line exactly as it would appear on the originating backend.
 */
final class ChatCapture {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ChatCapture() {
    }

    static Component capture(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Component displayName = player.displayName();
        Component message = event.message();

        Audience viewer = Bukkit.getOnlinePlayers().stream()
                .findFirst()
                .map(Audience.class::cast)
                .orElse(player);

        Component rendered = event.renderer().render(player, displayName, message, viewer);
        if (containsDisplayIdentity(rendered, displayName, player.getName())) {
            return rendered;
        }

        // Some renderers only return the message body; rebuild the visible chat line.
        return displayName.append(Component.text(": ")).append(message);
    }

    private static boolean containsDisplayIdentity(Component rendered, Component displayName, String username) {
        String renderedPlain = PLAIN.serialize(rendered).trim();
        if (renderedPlain.isEmpty()) {
            return false;
        }
        String displayPlain = PLAIN.serialize(displayName).trim();
        if (!displayPlain.isEmpty() && renderedPlain.contains(displayPlain)) {
            return true;
        }
        return renderedPlain.regionMatches(true, 0, username, 0, username.length());
    }
}
