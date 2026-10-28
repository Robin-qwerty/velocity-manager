package com.avesclub.velocity.sendplayersaround;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.util.Favicon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies per-domain {@code motd.yml} profiles to the server-list ping (description, hover box,
 * player count, fake version, favicon), resolved from the hostname the client connected with.
 */
final class MotdListener {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final NetworkPlayerCounter networkPlayers;
    private final Map<String, Favicon> faviconCache = new ConcurrentHashMap<>();
    private volatile MotdConfig config = MotdConfig.defaults();

    MotdListener(ProxyServer proxyServer, Logger logger, Path dataDirectory, NetworkPlayerCounter networkPlayers) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.networkPlayers = networkPlayers;
    }

    void applyConfig(MotdConfig newConfig) {
        this.config = newConfig;
        refreshFaviconCache(newConfig);
    }

    private void refreshFaviconCache(MotdConfig cfg) {
        faviconCache.clear();
        if (!cfg.enabled()) {
            return;
        }
        Set<String> filenames = new LinkedHashSet<>(cfg.defaultProfile().favicon().files());
        for (MotdProfile profile : cfg.domainProfiles().values()) {
            filenames.addAll(profile.favicon().files());
        }
        if (filenames.isEmpty()) {
            return;
        }
        Path faviconDir = dataDirectory.resolve("favicons");
        for (String filename : filenames) {
            Path file = faviconDir.resolve(filename);
            if (!Files.isRegularFile(file)) {
                logger.warn("motd.yml references favicon '{}' but {} does not exist", filename, file);
                continue;
            }
            try {
                faviconCache.put(filename, Favicon.create(file));
            } catch (IOException ex) {
                logger.warn("Failed to load favicon '{}': {}", filename, ex.getMessage());
            }
        }
        if (!faviconCache.isEmpty()) {
            logger.info("Loaded {} favicon(s) for MOTD profiles", faviconCache.size());
        }
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        MotdConfig cfg = this.config;
        boolean motdEnabled = cfg != null && cfg.enabled();
        boolean networkEnabled = networkPlayers.enabled();
        if (!motdEnabled && !networkEnabled) {
            return;
        }

        ServerPing.Builder builder = event.getPing().asBuilder();
        if (networkEnabled) {
            // Becomes the ping's online count and the <online> placeholder below.
            builder.onlinePlayers(networkPlayers.onlineCount());
        }
        if (!motdEnabled) {
            event.setPing(builder.build());
            return;
        }

        String host = HostUtil.hostFromConnection(event.getConnection());
        MotdProfile profile = cfg.resolve(host);

        int online = profile.playerCount().onlineOverride().enabled()
                ? profile.playerCount().onlineOverride().value() : builder.getOnlinePlayers();
        int max = profile.playerCount().maxOverride().enabled()
                ? profile.playerCount().maxOverride().value() : builder.getMaximumPlayers();

        applyPlayerCount(builder, profile.playerCount());
        applyHover(builder, profile.hover(), online, max);
        applyDescription(builder, profile.description(), online, max);
        applyVersion(builder, profile.versionName(), profile.versionProtocol(), online, max);
        applyFavicon(builder, profile.favicon());

        event.setPing(builder.build());
    }

    private void applyPlayerCount(ServerPing.Builder builder, MotdProfile.PlayerCountSettings settings) {
        if (settings.hide()) {
            builder.nullPlayers();
            return;
        }
        if (settings.onlineOverride().enabled()) {
            builder.onlinePlayers(settings.onlineOverride().value());
        }
        if (settings.maxOverride().enabled()) {
            builder.maximumPlayers(settings.maxOverride().value());
        }
    }

    private void applyHover(ServerPing.Builder builder, MotdProfile.HoverSettings hover, int online, int max) {
        if (!hover.enabled() || hover.vanilla()) {
            return;
        }
        List<ServerPing.SamplePlayer> samples = new ArrayList<>();
        for (String mini : renderLinesToMiniMessage(hover.lines(), online, max)) {
            Component component = MiniMessageBridge.render(mini);
            samples.add(new ServerPing.SamplePlayer(LEGACY_SECTION.serialize(component), UUID.randomUUID()));
        }
        builder.clearSamplePlayers();
        builder.samplePlayers(samples);
    }

    private void applyDescription(ServerPing.Builder builder, MotdProfile.DescriptionSettings description, int online, int max) {
        if (!description.enabled() || description.lines().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        for (String mini : renderLinesToMiniMessage(description.lines(), online, max)) {
            lines.add(MiniMessageBridge.render(mini));
        }
        builder.description(Component.join(JoinConfiguration.newlines(), lines));
    }

    private void applyVersion(
            ServerPing.Builder builder,
            MotdProfile.VersionNameSettings name,
            MotdProfile.VersionProtocolSettings protocol,
            int online,
            int max
    ) {
        if (!name.enabled() && !protocol.enabled()) {
            return;
        }
        int newProtocol = protocol.enabled() ? protocol.value() : builder.getVersion().getProtocol();
        String newName = builder.getVersion().getName();
        if (name.enabled()) {
            String substituted = substitutePlaceholders(name.text(), online, max);
            newName = LEGACY_SECTION.serialize(MiniMessageBridge.render(substituted));
        }
        builder.version(new ServerPing.Version(newProtocol, newName));
    }

    private void applyFavicon(ServerPing.Builder builder, MotdProfile.FaviconSettings favicon) {
        if (!favicon.enabled() || favicon.files().isEmpty()) {
            return;
        }
        Favicon chosen;
        if (favicon.mode() == MotdProfile.FaviconSettings.Mode.SINGLE) {
            chosen = faviconCache.get(favicon.files().get(0));
        } else {
            List<String> valid = favicon.files().stream().filter(faviconCache::containsKey).toList();
            chosen = valid.isEmpty() ? null : faviconCache.get(valid.get(ThreadLocalRandom.current().nextInt(valid.size())));
        }
        if (chosen != null) {
            builder.favicon(chosen);
        }
    }

    /**
     * Shared by hover and description rendering: placeholder substitution, then {@code <newline>}
     * splitting, then {@code <center>} padding (on plain text, before colors are parsed), then the
     * legacy-to-MiniMessage bridge — leaving a list of MiniMessage strings ready to deserialize.
     */
    private List<String> renderLinesToMiniMessage(List<String> configuredLines, int online, int max) {
        List<String> out = new ArrayList<>();
        for (String configured : configuredLines) {
            String substituted = substitutePlaceholders(configured, online, max);
            for (String physical : substituted.split("<newline>", -1)) {
                String centered = MotdCenterUtil.applyCenterIfPresent(physical);
                out.add(MiniMessageBridge.legacyToMiniMessage(centered));
            }
        }
        return out;
    }

    private String substitutePlaceholders(String raw, int online, int max) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String result = raw.replace("<online>", Integer.toString(online)).replace("<max>", Integer.toString(max));
        for (RegisteredServer server : proxyServer.getAllServers()) {
            result = result.replace(
                    "<online_" + server.getServerInfo().getName() + ">",
                    Integer.toString(server.getPlayersConnected().size())
            );
        }
        return result;
    }
}
