package com.avesclub.velocity.sendplayersaround;

import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import redis.clients.jedis.ClientSetInfoConfig;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Network-wide online count shared between proxies through Redis. Each proxy writes its own
 * player count to {@code key-prefix + proxy-id} with a TTL; the ping handler adds the other
 * proxies' keys to this proxy's live count.
 *
 * <p>All Redis I/O runs on one dedicated thread. {@link #onlineCount()} only reads the last
 * snapshot from memory, so a slow or dead Redis never stalls a server-list ping.
 */
final class NetworkPlayerCounter {

    private static final long READ_CACHE_MILLIS = 1000L;
    private static final int REDIS_TIMEOUT_MILLIS = 2000;
    private static final int SCAN_COUNT = 100;

    private final ProxyServer proxyServer;
    private final Logger logger;
    private volatile Session session;

    NetworkPlayerCounter(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    void applyConfig(PluginConfig config) {
        PluginConfig.NetworkPlayers settings = config == null ? PluginConfig.NetworkPlayers.disabled() : config.networkPlayers();
        Session previous = this.session;
        this.session = null;
        if (previous != null) {
            previous.stop();
        }
        if (settings.enabled()) {
            Session next = new Session(settings);
            next.start();
            this.session = next;
        }
    }

    void shutdown() {
        Session previous = this.session;
        this.session = null;
        if (previous != null) {
            previous.stop();
        }
    }

    boolean enabled() {
        return session != null;
    }

    /**
     * This proxy's live player count plus every other proxy's published count. Falls back to the
     * local count when Redis is unreachable, the last read is too old, or the last read did not
     * contain this proxy's own key.
     */
    int onlineCount() {
        int local = proxyServer.getPlayerCount();
        Session current = this.session;
        return current == null ? local : current.onlineCount(local);
    }

    /** Result of one Redis read: the sum of the other proxies' keys. */
    private record Snapshot(int otherProxies, boolean ownKeyPresent, long readAtMillis) {
    }

    private final class Session {
        private final PluginConfig.NetworkPlayers settings;
        private final String scanPattern;
        private final ScheduledExecutorService executor;
        private final AtomicBoolean refreshQueued = new AtomicBoolean();
        private volatile Snapshot snapshot;
        // Only touched on the executor thread.
        private Jedis jedis;
        // Written only on the executor thread. null until the first attempt finishes.
        private volatile Boolean healthy;

        Session(PluginConfig.NetworkPlayers settings) {
            this.settings = settings;
            this.scanPattern = escapeGlob(settings.keyPrefix()) + "*";
            this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "robin-velocity-manager-network-players");
                thread.setDaemon(true);
                return thread;
            });
        }

        void start() {
            executor.scheduleWithFixedDelay(this::publishAndRead, 0L, settings.publishIntervalSeconds(), TimeUnit.SECONDS);
            logger.info("Network player count enabled: proxy-id '{}', Redis {}:{}",
                    settings.proxyId(), settings.host(), settings.port());
        }

        void stop() {
            try {
                executor.execute(this::closeConnection);
            } catch (RejectedExecutionException ignored) {
                // Already stopped.
            }
            executor.shutdown();
        }

        int onlineCount(int local) {
            Snapshot current = this.snapshot;
            long now = System.currentTimeMillis();
            if (current == null || now - current.readAtMillis() >= READ_CACHE_MILLIS) {
                requestRefresh();
            }
            if (current == null || !current.ownKeyPresent()
                    || now - current.readAtMillis() > settings.keyTtlSeconds() * 1000L) {
                return local;
            }
            return local + current.otherProxies();
        }

        private void requestRefresh() {
            // While Redis is down, only the publish task retries, so pings do not cause reconnect storms.
            if (Boolean.FALSE.equals(healthy) || !refreshQueued.compareAndSet(false, true)) {
                return;
            }
            try {
                executor.execute(() -> {
                    try {
                        runRedis(false);
                    } finally {
                        refreshQueued.set(false);
                    }
                });
            } catch (RejectedExecutionException ex) {
                refreshQueued.set(false);
            }
        }

        private void publishAndRead() {
            runRedis(true);
        }

        private void runRedis(boolean publish) {
            try {
                Jedis connection = connection();
                if (publish) {
                    connection.set(settings.ownKey(), Integer.toString(proxyServer.getPlayerCount()),
                            SetParams.setParams().ex(settings.keyTtlSeconds()));
                }
                this.snapshot = read(connection);
                markHealthy();
            } catch (Exception ex) {
                markFailed(ex);
            }
        }

        private Snapshot read(Jedis connection) {
            String ownKey = settings.ownKey();
            boolean ownKeyPresent = false;
            int others = 0;
            ScanParams params = new ScanParams().match(scanPattern).count(SCAN_COUNT);
            String cursor = ScanParams.SCAN_POINTER_START;
            do {
                ScanResult<String> page = connection.scan(cursor, params);
                for (String key : page.getResult()) {
                    if (key.equals(ownKey)) {
                        // Our own value is replaced by the live count at ping time.
                        ownKeyPresent = true;
                        continue;
                    }
                    others += parseCount(connection.get(key));
                }
                cursor = page.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            return new Snapshot(others, ownKeyPresent, System.currentTimeMillis());
        }

        private Jedis connection() {
            if (jedis == null) {
                DefaultJedisClientConfig.Builder config = DefaultJedisClientConfig.builder()
                        .connectionTimeoutMillis(REDIS_TIMEOUT_MILLIS)
                        .socketTimeoutMillis(REDIS_TIMEOUT_MILLIS)
                        .database(settings.database())
                        .clientSetInfoConfig(ClientSetInfoConfig.DISABLED);
                if (!settings.username().isEmpty()) {
                    config.user(settings.username());
                }
                if (!settings.password().isEmpty()) {
                    config.password(settings.password());
                }
                jedis = new Jedis(new HostAndPort(settings.host(), settings.port()), config.build());
            }
            return jedis;
        }

        private void closeConnection() {
            if (jedis != null) {
                try {
                    jedis.close();
                } catch (Exception ignored) {
                    // Nothing useful to do with a failed close.
                }
                jedis = null;
            }
        }

        private void markHealthy() {
            if (Boolean.TRUE.equals(healthy)) {
                return;
            }
            if (healthy == null) {
                logger.info("Network player count: connected to Redis at {}:{}", settings.host(), settings.port());
            } else {
                logger.info("Network player count: Redis connection recovered; showing the network total again");
            }
            healthy = Boolean.TRUE;
        }

        private void markFailed(Exception ex) {
            this.snapshot = null;
            closeConnection();
            if (Boolean.FALSE.equals(healthy)) {
                logger.debug("Network player count: Redis still unavailable: {}", ex.toString());
                return;
            }
            healthy = Boolean.FALSE;
            logger.warn("Network player count: Redis at {}:{} is unavailable ({}); showing this proxy's own count until it recovers",
                    settings.host(), settings.port(), ex.toString());
        }
    }

    private static int parseCount(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String escapeGlob(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }
}
