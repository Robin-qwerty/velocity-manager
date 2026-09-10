package com.avesclub.velocity.sendplayersaround;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Plugin(
        id = "robin-velocity-manager",
        name = "robin-velocity-manager",
        version = "1.0.0",
        description = "Moves players around and provides utility commands.",
        authors = {"Robinaves"}
)
public final class SendPlayersAroundPlugin {
    private static final String CHANNEL_NAME = "myplugin:network";
    private static final String FIND_PERMISSION = "robinvelocitymanager.find";
    private static final String RELOAD_PERMISSION = "robinvelocitymanager.reload";
    private static final String JOIN_SUBCOMMAND = "__join";

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;
    private final MotdListener motdListener;
    private MinecraftChannelIdentifier channelIdentifier;
    private PluginConfig config;
    private MotdConfig motdConfig;

    @Inject
    public SendPlayersAroundPlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.motdListener = new MotdListener(proxyServer, logger, dataDirectory);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        proxyServer.getEventManager().register(this, motdListener);

        channelIdentifier = MinecraftChannelIdentifier.from(CHANNEL_NAME);
        proxyServer.getChannelRegistrar().register(channelIdentifier);
        proxyServer.getCommandManager().register(
                proxyServer.getCommandManager().metaBuilder("find").build(),
                new FindCommand()
        );
        var commandManager = proxyServer.getCommandManager();
        LobbyHubCommand lobbyHubCommand = new LobbyHubCommand();
        commandManager.register(commandManager.metaBuilder("lobby").build(), lobbyHubCommand);
        commandManager.register(commandManager.metaBuilder("hub").build(), lobbyHubCommand);
        commandManager.register(
                commandManager.metaBuilder("velocitymanager").build(),
                new VelocityManagerCommand()
        );
        commandManager.register(commandManager.metaBuilder("discord").build(), new DiscordCommand());
        logger.info("Registered plugin messaging channel {}", CHANNEL_NAME);
    }

    private void loadConfig() {
        try {
            PluginConfig.ensureDefaultFile(dataDirectory, SendPlayersAroundPlugin.class);
            this.config = PluginConfig.load(dataDirectory);
            logger.info("Loaded config: lobby '{}', {} domain route(s)",
                    config.lobbyServer(),
                    config.domainServers().size());
        } catch (IOException e) {
            logger.error("Failed to load config.yml; using defaults", e);
            this.config = new PluginConfig(
                    "lobby",
                    Map.of(),
                    null,
                    null,
                    null,
                    true,
                    true,
                    true
            );
        }

        try {
            MotdConfig.ensureDefaultFile(dataDirectory, SendPlayersAroundPlugin.class);
            this.motdConfig = MotdConfig.load(dataDirectory);
            logger.info("Loaded motd.yml: {} ({} domain profile(s))",
                    motdConfig.enabled() ? "enabled" : "disabled",
                    motdConfig.domainProfiles().size());
        } catch (IOException e) {
            logger.error("Failed to load motd.yml; MOTD customization disabled", e);
            this.motdConfig = MotdConfig.defaults();
        }
        motdListener.applyConfig(motdConfig);
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        if (config == null || !config.domainRoutingEnabled() || config.domainServers().isEmpty()) {
            return;
        }
        String host = HostUtil.hostFromPlayer(event.getPlayer());
        if (host.isEmpty()) {
            return;
        }
        String targetName = config.domainServers().get(host);
        if (targetName == null) {
            return;
        }
        Optional<RegisteredServer> target = proxyServer.getServer(targetName);
        if (target.isEmpty()) {
            logger.warn("domain-servers maps host '{}' to unknown server '{}'", host, targetName);
            return;
        }
        event.setInitialServer(target.get());
        logger.debug("Routing {} from host '{}' to server '{}'", event.getPlayer().getUsername(), host, targetName);
    }

    private void reloadConfig(CommandSource feedback) {
        try {
            PluginConfig.ensureDefaultFile(dataDirectory, SendPlayersAroundPlugin.class);
            this.config = PluginConfig.load(dataDirectory);
        } catch (IOException e) {
            logger.error("Config reload failed", e);
            feedback.sendMessage(Component.text("Config reload failed; check the proxy log."));
            return;
        }

        try {
            MotdConfig.ensureDefaultFile(dataDirectory, SendPlayersAroundPlugin.class);
            this.motdConfig = MotdConfig.load(dataDirectory);
        } catch (IOException e) {
            logger.error("motd.yml reload failed; MOTD customization disabled", e);
            this.motdConfig = MotdConfig.defaults();
        }
        motdListener.applyConfig(motdConfig);

        feedback.sendMessage(Component.text(
                "Reloaded config: lobby '" + config.lobbyServer() + "', "
                        + config.domainServers().size() + " domain route(s)"
                        + ", motd " + (motdConfig.enabled()
                                ? "enabled (" + motdConfig.domainProfiles().size() + " domain profile(s))"
                                : "disabled")
                        + "."));
        String who = feedback instanceof Player p ? p.getUsername() : "console";
        logger.info("{} reloaded robin-velocity-manager config", who);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (channelIdentifier == null || !event.getIdentifier().equals(channelIdentifier)) {
            return;
        }

        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        if (!(event.getTarget() instanceof Player player)) {
            return;
        }

        ServerConnection sourceServerConnection = (ServerConnection) event.getSource();
        String sourceServerName = sourceServerConnection.getServerInfo().getName();
        String playerCurrentServer = player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("unknown");

        ByteArrayDataInput input = ByteStreams.newDataInput(event.getData());
        String subcommand;
        String targetServerName;
        try {
            subcommand = input.readUTF();
            targetServerName = input.readUTF();
        } catch (IllegalArgumentException ex) {
            logger.warn("Invalid plugin message payload from {}", event.getSource(), ex);
            return;
        }

        logger.debug("Received plugin message on channel {}: server '{}' requested to move player '{}' from '{}' to '{}'",
                CHANNEL_NAME, sourceServerName, player.getUsername(), playerCurrentServer, targetServerName);

        if (!"CONNECT".equalsIgnoreCase(subcommand)) {
            return;
        }
        Optional<RegisteredServer> targetServer = proxyServer.getServer(targetServerName);
        if (targetServer.isEmpty()) {
            logger.debug("Target server '{}' does not exist, cannot move player '{}'", targetServerName, player.getUsername());
            player.sendMessage(Component.text("Unknown server: " + targetServerName));
            return;
        }

        player.createConnectionRequest(targetServer.get()).connect().whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.warn("Failed to connect {} to {}", player.getUsername(), targetServerName, throwable);
            } else if (!result.isSuccessful()) {
                logger.info("Connection request for {} to {} was not successful", player.getUsername(), targetServerName);
            } else {
                logger.debug("Successfully moved player '{}' from '{}' to '{}' (requested by server '{}')",
                        player.getUsername(), playerCurrentServer, targetServerName, sourceServerName);
            }
        });

        event.setResult(PluginMessageEvent.ForwardResult.handled());
    }

    private final class FindCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!source.hasPermission(FIND_PERMISSION)) {
                source.sendMessage(Component.text("You don't have permission to use this command."));
                return;
            }

            String[] arguments = invocation.arguments();
            if (arguments.length == 0) {
                source.sendMessage(Component.text("Usage: /find <player>"));
                return;
            }

            if (JOIN_SUBCOMMAND.equalsIgnoreCase(arguments[0])) {
                handleJoinSubcommand(source, arguments);
                return;
            }

            String playerName = arguments[0];
            Optional<Player> targetPlayer = proxyServer.getPlayer(playerName);
            if (targetPlayer.isEmpty()) {
                source.sendMessage(Component.text("Player '" + playerName + "' is not online."));
                return;
            }

            Optional<RegisteredServer> currentServer = targetPlayer.get().getCurrentServer()
                    .map(connection -> proxyServer.getServer(connection.getServerInfo().getName()))
                    .flatMap(server -> server);

            if (currentServer.isEmpty()) {
                source.sendMessage(Component.text("Could not determine server for " + targetPlayer.get().getUsername() + "."));
                return;
            }

            String serverName = currentServer.get().getServerInfo().getName();
            Component serverComponent = Component.text(serverName)
                    .clickEvent(ClickEvent.runCommand("/find " + JOIN_SUBCOMMAND + " " + serverName))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to connect to " + serverName)));

            source.sendMessage(
                    Component.text(targetPlayer.get().getUsername() + " is currently on ")
                            .append(serverComponent)
                            .append(Component.text("."))
            );
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!source.hasPermission(FIND_PERMISSION)) {
                return Collections.emptyList();
            }

            String[] arguments = invocation.arguments();
            if (arguments.length == 0) {
                return proxyServer.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }

            if (arguments.length == 1) {
                String input = arguments[0].toLowerCase();
                return proxyServer.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        }

        private void handleJoinSubcommand(CommandSource source, String[] arguments) {
            if (!(source instanceof Player requestingPlayer)) {
                source.sendMessage(Component.text("Only players can connect to a server."));
                return;
            }

            if (arguments.length < 2) {
                source.sendMessage(Component.text("Missing server name."));
                return;
            }

            String targetServerName = arguments[1];
            Optional<RegisteredServer> targetServer = proxyServer.getServer(targetServerName);
            if (targetServer.isEmpty()) {
                source.sendMessage(Component.text("Unknown server: " + targetServerName));
                return;
            }

            requestingPlayer.createConnectionRequest(targetServer.get()).connect().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.warn("Failed to connect {} to {}", requestingPlayer.getUsername(), targetServerName, throwable);
                    requestingPlayer.sendMessage(Component.text("Could not connect to " + targetServerName + "."));
                } else if (!result.isSuccessful()) {
                    requestingPlayer.sendMessage(Component.text("Could not connect to " + targetServerName + "."));
                }
            });
        }
    }

    private final class DiscordCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            PluginConfig cfg = SendPlayersAroundPlugin.this.config;
            if (cfg == null) {
                source.sendMessage(Component.text("Plugin configuration is not loaded."));
                return;
            }
            if (!cfg.discordCommandEnabled()) {
                source.sendMessage(Component.text("The Discord command is currently disabled."));
                return;
            }
            Component link = Component.text(cfg.discordLinkText())
                    .clickEvent(ClickEvent.openUrl(cfg.discordUrl()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to open Discord")));
            source.sendMessage(Component.text(cfg.discordMessage() + " ").append(link));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return Collections.emptyList();
        }
    }

    private final class VelocityManagerCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!source.hasPermission(RELOAD_PERMISSION)) {
                source.sendMessage(Component.text("You don't have permission to use this command."));
                return;
            }
            String[] args = invocation.arguments();
            if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
                reloadConfig(source);
                return;
            }
            source.sendMessage(Component.text("Usage: /velocitymanager reload"));
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            if (!invocation.source().hasPermission(RELOAD_PERMISSION)) {
                return Collections.emptyList();
            }
            String[] args = invocation.arguments();
            if (args.length == 0) {
                return List.of("reload");
            }
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                if ("reload".startsWith(prefix)) {
                    return List.of("reload");
                }
            }
            return Collections.emptyList();
        }
    }

    private final class LobbyHubCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!(source instanceof Player player)) {
                source.sendMessage(Component.text("Only players can use this command."));
                return;
            }
            PluginConfig cfg = SendPlayersAroundPlugin.this.config;
            if (cfg == null) {
                player.sendMessage(Component.text("Plugin configuration is not loaded."));
                return;
            }
            if (!cfg.lobbyHubCommandsEnabled()) {
                player.sendMessage(Component.text("This command is currently disabled."));
                return;
            }
            String lobbyName = cfg.lobbyServer();
            Optional<RegisteredServer> lobby = proxyServer.getServer(lobbyName);
            if (lobby.isEmpty()) {
                player.sendMessage(Component.text("Lobby server '" + lobbyName + "' is not defined in Velocity."));
                return;
            }
            player.createConnectionRequest(lobby.get()).connect().whenComplete((result, throwable) -> {
                if (throwable != null) {
                    logger.warn("Failed to connect {} to lobby {}", player.getUsername(), lobbyName, throwable);
                    player.sendMessage(Component.text("Could not connect to the lobby."));
                } else if (!result.isSuccessful()) {
                    player.sendMessage(Component.text("Could not connect to the lobby."));
                }
            });
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            return Collections.emptyList();
        }
    }
}
