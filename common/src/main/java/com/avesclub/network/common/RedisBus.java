package com.avesclub.network.common;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RedisBus implements AutoCloseable {

    private final RedisSettings settings;
    private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();
    private RedisClient client;
    private StatefulRedisConnection<String, String> publishConnection;
    private StatefulRedisPubSubConnection<String, String> subscribeConnection;

    public RedisBus(RedisSettings settings) {
        this.settings = settings;
    }

    public void connect() {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(settings.host())
                .withPort(settings.port());
        if (settings.password() != null && !settings.password().isBlank()) {
            builder.withPassword(settings.password().toCharArray());
        }
        client = RedisClient.create(builder.build());
        publishConnection = client.connect();

        subscribeConnection = client.connectPubSub();
        subscribeConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                List<Consumer<String>> channelListeners = listeners.get(channel);
                if (channelListeners == null) {
                    return;
                }
                for (Consumer<String> listener : channelListeners) {
                    try {
                        listener.accept(message);
                    } catch (RuntimeException ignored) {
                        // Individual listener failures must not break the bus.
                    }
                }
            }
        });
    }

    public void subscribe(String channel, Consumer<String> handler) {
        listeners.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(handler);
        if (subscribedChannels.add(channel)) {
            subscribeConnection.sync().subscribe(channel);
        }
    }

    public void publish(String channel, String message) {
        RedisCommands<String, String> commands = publishConnection.sync();
        commands.publish(channel, message);
    }

    public RedisSettings settings() {
        return settings;
    }

    @Override
    public void close() {
        if (subscribeConnection != null) {
            subscribeConnection.close();
        }
        if (publishConnection != null) {
            publishConnection.close();
        }
        if (client != null) {
            client.shutdown();
        }
        listeners.clear();
        subscribedChannels.clear();
    }
}
