package server.web;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import server.config.ServerConfig;

/** Admission control before a connection enters the HTTP worker pool. */
public final class RequestGuard {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int BLOCK_AFTER_EXCESS = 3;
    private static final String LAB_SIMULATOR_USER_AGENT = "Academic-Lab-Simulator/1.0";

    public enum Decision {
        ALLOW,
        LIMITED,
        GLOBAL_LIMITED,
        BLOCKED,
        MANUALLY_BLOCKED,
        CONNECTION_LIMITED
    }

    private final ServerConfig config;
    private final Set<String> localAddresses;
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final ConcurrentHashMap<String, ClientWindow> clients = new ConcurrentHashMap<>();
    private final Set<String> manuallyBlockedIps = ConcurrentHashMap.newKeySet();
    /**
     * One shared admission window for all remote/simulator sources. Without
     * this window, many DDoS nodes can each stay below the per-client limit
     * while their aggregate traffic still overwhelms the worker pool.
     */
    private final ClientWindow globalRequests = new ClientWindow();
    private volatile boolean protectionEnabled = true;
    private volatile boolean rateLimitActive = true;
    private volatile boolean connectionLimitActive = true;
    private volatile boolean blockingActive = true;

    public RequestGuard(ServerConfig config) {
        this.config = config;
        this.localAddresses = findLocalAddresses();
    }

    public Decision enter(String ip) {
        if (isManuallyBlocked(ip)) return Decision.MANUALLY_BLOCKED;
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

    /** Apply the per-client limit after the HTTP headers identify local lab traffic. */
    public Decision checkRequest(String ip, String userAgent) {
        if (isManuallyBlocked(ip)) return Decision.MANUALLY_BLOCKED;
        boolean trustedLocalBrowser = isLocalAddress(ip) && !isLabSimulator(userAgent);
        return checkKey(clientKey(ip, userAgent), !trustedLocalBrowser);
    }

    /** Remote LAN clients can be limited before they occupy the HTTP worker queue. */
    public boolean canCheckBeforeHeaders(String ip) {
        return !isLocalAddress(ip);
    }

    public Decision checkRequestByIp(String ip) {
        if (isManuallyBlocked(ip)) return Decision.MANUALLY_BLOCKED;
        return checkKey(ip, true);
    }

    private Decision checkKey(String key, boolean applyGlobalLimit) {
        if (!protectionEnabled || !rateLimitActive) return Decision.ALLOW;
        long now = System.nanoTime();
        Decision clientDecision = clients.computeIfAbsent(key, ignored -> new ClientWindow())
                .check(now, config.getRateLimit(),
                        blockingActive && config.isAutoDefense(), config.getBlockDuration().toNanos());
        if (clientDecision != Decision.ALLOW || !applyGlobalLimit) {
            return clientDecision;
        }

        Decision globalDecision = globalRequests.check(now, config.getRequestRateThreshold(), false, 0);
        return globalDecision == Decision.ALLOW ? Decision.ALLOW : Decision.GLOBAL_LIMITED;
    }

    public void leave() {
        activeConnections.updateAndGet(active -> Math.max(0, active - 1));
    }

    public long blockRemainingSeconds(String ip) {
        if (isManuallyBlocked(ip)) return -1;
        long now = System.nanoTime();
        long remaining = blockRemainingSeconds(ip, now);
        if (isLocalAddress(ip)) {
            remaining = Math.max(remaining, blockRemainingSeconds(ip + "|lab-simulator", now));
            remaining = Math.max(remaining, blockRemainingSeconds(ip + "|browser", now));
        }
        return remaining;
    }

    /** Immediately blocks one detected source IP for the configured temporary duration. */
    public boolean blockClient(String ip) {
        if (!protectionEnabled || !blockingActive || ip == null || ip.isBlank()) return false;
        long now = System.nanoTime();
        long duration = config.getBlockDuration().toNanos();
        if (isLocalAddress(ip)) {
            // Keep the local browser usable while blocking the lab simulator quota.
            return clients.computeIfAbsent(ip + "|lab-simulator", ignored -> new ClientWindow())
                    .forceBlock(now, duration);
        }
        return clients.computeIfAbsent(ip, ignored -> new ClientWindow()).forceBlock(now, duration);
    }

    /** Blocks an address until an explicit manual unblock, independently of protection switches. */
    public boolean blockClientManually(String ip) {
        return ip != null && !ip.isBlank() && manuallyBlockedIps.add(ip);
    }

    public boolean unblockClientManually(String ip) {
        return ip != null && manuallyBlockedIps.remove(ip);
    }

    public boolean isManuallyBlocked(String ip) {
        return ip != null && manuallyBlockedIps.contains(ip);
    }

    private long blockRemainingSeconds(String key, long now) {
        ClientWindow client = clients.get(key);
        return client == null ? 0 : client.blockRemainingSeconds(now);
    }

    private String clientKey(String ip, String userAgent) {
        // A one-machine lab may use either 127.0.0.1 or this machine's LAN IP.
        // Separate the simulator and browser only for addresses owned by this
        // server; remote clients still share one quota per real source IP.
        if (!isLocalAddress(ip)) return ip;
        return ip + (isLabSimulator(userAgent)
                ? "|lab-simulator" : "|browser");
    }

    private static boolean isLabSimulator(String userAgent) {
        return LAB_SIMULATOR_USER_AGENT.equals(userAgent);
    }

    private boolean isLocalAddress(String ip) {
        return ip.startsWith("127.") || localAddresses.contains(ip);
    }

    private static Set<String> findLocalAddresses() {
        Set<String> addresses = new HashSet<>();
        addresses.add("127.0.0.1");
        addresses.add("::1");
        addresses.add("0:0:0:0:0:0:0:1");
        try {
            NetworkInterface.networkInterfaces()
                    .flatMap(NetworkInterface::inetAddresses)
                    .map(address -> address.getHostAddress())
                    .forEach(addresses::add);
        } catch (SocketException ignored) {
            // Loopback separation still works if interfaces cannot be listed.
        }
        return Set.copyOf(addresses);
    }

    public void setProtectionEnabled(boolean enabled) {
        protectionEnabled = enabled;
        if (!enabled) resetRateWindows();
    }

    public void setRateLimitActive(boolean enabled) {
        rateLimitActive = enabled;
        if (!enabled) resetRateWindows();
    }
    public void setConnectionLimitActive(boolean enabled) { connectionLimitActive = enabled; }
    public void setBlockingActive(boolean enabled) {
        blockingActive = enabled;
        if (!enabled) clearBlocks();
    }
    public void clearBlocks() { clients.values().forEach(ClientWindow::clearBlock); }
    public void reset() { resetRateWindows(); }
    void resetConnections() { activeConnections.set(0); }

    private void resetRateWindows() {
        clients.clear();
        globalRequests.reset();
    }

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

        synchronized boolean forceBlock(long now, long blockNanos) {
            if (blockedUntilNanos != 0 && now - blockedUntilNanos < 0) return false;
            blockedUntilNanos = now + blockNanos;
            return true;
        }

        synchronized void clearBlock() {
            blockedUntilNanos = 0;
            excess = 0;
        }

        synchronized void reset() {
            windowStartNanos = 0;
            admitted = 0;
            excess = 0;
            blockedUntilNanos = 0;
        }
    }
}
