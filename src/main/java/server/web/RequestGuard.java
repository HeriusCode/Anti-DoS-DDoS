package server.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import server.config.ServerConfig;

/** Admission control before a connection enters the HTTP worker pool. */
public final class RequestGuard {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int BLOCK_AFTER_EXCESS = 3;

    public enum Decision { ALLOW, LIMITED, BLOCKED, CONNECTION_LIMITED }

    private final ServerConfig config;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final ConcurrentHashMap<String, ClientWindow> clients = new ConcurrentHashMap<>();
    private volatile boolean protectionEnabled = true;
    private volatile boolean rateLimitActive = true;
    private volatile boolean connectionLimitActive = true;
    private volatile boolean blockingActive = true;

    public RequestGuard(ServerConfig config) {
        this.config = config;
    }

    public Decision enter(String ip) {
        if (protectionEnabled && rateLimitActive) {
            Decision rateDecision = clients.computeIfAbsent(ip, ignored -> new ClientWindow())
                    .check(System.nanoTime(), config.getRateLimit(),
                            blockingActive && config.isAutoDefense(), config.getBlockDuration().toNanos());
            if (rateDecision != Decision.ALLOW) return rateDecision;
        }

        if (protectionEnabled && connectionLimitActive) {
            while (true) {
                int active = activeConnections.get();
                if (active >= config.getMaxActiveConnections()) return Decision.CONNECTION_LIMITED;
                if (activeConnections.compareAndSet(active, active + 1)) break;
            }
        } else {
            activeConnections.incrementAndGet();
        }
        return Decision.ALLOW;
    }

    public void leave() {
        activeConnections.updateAndGet(active -> Math.max(0, active - 1));
    }

    public long blockRemainingSeconds(String ip) {
        ClientWindow client = clients.get(ip);
        return client == null ? 0 : client.blockRemainingSeconds(System.nanoTime());
    }

    public void setProtectionEnabled(boolean enabled) {
        protectionEnabled = enabled;
        if (!enabled) clients.clear();
    }

    public void setRateLimitActive(boolean enabled) {
        rateLimitActive = enabled;
        if (!enabled) clients.clear();
    }
    public void setConnectionLimitActive(boolean enabled) { connectionLimitActive = enabled; }
    public void setBlockingActive(boolean enabled) {
        blockingActive = enabled;
        if (!enabled) clearBlocks();
    }
    public void clearBlocks() { clients.values().forEach(ClientWindow::clearBlock); }
    public void reset() { clients.clear(); }
    void resetConnections() { activeConnections.set(0); }

    private static final class ClientWindow {
        private long windowStartNanos;
        private int admitted;
        private int excess;
        private long blockedUntilNanos;

        synchronized Decision check(long now, int limit, boolean blockEnabled, long blockNanos) {
            if (blockedUntilNanos != 0 && now - blockedUntilNanos < 0) return Decision.BLOCKED;
            if (windowStartNanos == 0 || now - windowStartNanos >= WINDOW_NANOS) {
                windowStartNanos = now;
                admitted = 0;
                excess = 0;
            }
            if (admitted >= limit) {
                if (blockEnabled && ++excess >= BLOCK_AFTER_EXCESS) {
                    blockedUntilNanos = now + blockNanos;
                    return Decision.BLOCKED;
                }
                return Decision.LIMITED;
            }
            admitted++;
            return Decision.ALLOW;
        }

        synchronized long blockRemainingSeconds(long now) {
            if (blockedUntilNanos == 0) return 0;
            long remaining = blockedUntilNanos - now;
            return remaining <= 0 ? 0 : (remaining + WINDOW_NANOS - 1) / WINDOW_NANOS;
        }

        synchronized void clearBlock() {
            blockedUntilNanos = 0;
            excess = 0;
        }
    }
}
