package com.avesclub.network.paper;

import com.avesclub.network.common.NetworkMessages;
import com.avesclub.network.common.RedisBus;
import com.avesclub.network.common.RedisSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;

public final class RobinPaperBridgePlugin extends JavaPlugin {

    private static RobinPaperBridgePlugin instance;

    private PaperPluginConfig config;
    private RedisBus redisBus;
    private TabListService tabListService;
    private NetworkMessageService messageService;

    public NetworkMessageService getMessageService() {
        return messageService;
    }

    public static RobinPaperBridgePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        Path dataDirectory = getDataFolder().toPath();
        try {
            PaperPluginConfig.ensureDefaultFile(dataDirectory, RobinPaperBridgePlugin.class);
            this.config = PaperPluginConfig.load(dataDirectory);
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to load config.yml", ex);
            this.config = PaperPluginConfig.defaults();
        }

        this.tabListService = new TabListService(this);
        this.messageService = new NetworkMessageService(this, config);
        getServer().getPluginManager().registerEvents(new MessageSyncListener(messageService), this);

        if (!config.redis().enabled()) {
            getLogger().warning("Redis is disabled in config.yml; network sync will not run.");
            return;
        }

        try {
            startRedis();
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Failed to connect to Redis", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        stopRedis();
        instance = null;
    }

    public void sendToNetworkPlayer(UUID targetUuid, Component message) {
        if (messageService == null) {
            return;
        }
        messageService.publishToPlayers(java.util.Set.of(targetUuid), message);
    }

    private void startRedis() {
        stopRedis();
        RedisSettings settings = config.redis();
        redisBus = new RedisBus(settings);
        redisBus.connect();
        messageService.attachRedis(redisBus);
        redisBus.subscribe(settings.controlChannel(), this::handleControlMessage);
        redisBus.subscribe(settings.chatChannel(), messageService::handleRedisPayload);
        redisBus.publish(settings.controlChannel(), NetworkMessages.resync(config.serverName()));
        getLogger().info("Connected to Redis at " + settings.host() + ":" + settings.port()
                + " as server '" + config.serverName() + "'");
    }

    private void stopRedis() {
        if (redisBus != null) {
            redisBus.close();
            redisBus = null;
        }
    }

    private void handleControlMessage(String raw) {
        try {
            NetworkMessages.ParsedMessage parsed = NetworkMessages.parse(raw);
            if (parsed instanceof NetworkMessages.Config configMessage) {
                messageService.applyLinkedServers(configMessage.linkedServers());
                if (!messageService.isParticipating()) {
                    tabListService.clearAll();
                }
            }
        } catch (RuntimeException ex) {
            getLogger().log(Level.WARNING, "Invalid Redis control message: " + raw, ex);
        }
    }
}
