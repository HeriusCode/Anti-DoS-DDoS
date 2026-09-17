package server.monitor;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import server.detection.AttackStatus;
import server.model.ClientInfo;

/** Thread-safe counters and a five-second rolling request window. */
public final class TrafficMonitor {
    private static final long WINDOW_MILLIS = 5_000;

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LongAdder limitedRequests = new LongAdder();
    private final LongAdder droppedRequests = new LongAdder();
    private final LongAdder completedRequests = new LongAdder();
    private final LongAdder totalResponseNanos = new LongAdder();
    private final AtomicInteger activeConnections = new AtomicInteger();
    private final ConcurrentLinkedDeque<Long> requestTimes = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<String, ClientCounters> clients = new ConcurrentHashMap<>();

    public void connectionOpened(String ip) {
        activeConnections.incrementAndGet();
        clients.computeIfAbsent(ip, ignored -> new ClientCounters()).activeConnections.incrementAndGet();
    }

    public void connectionClosed(String ip) {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        ClientCounters counters = clients.get(ip);
        if (counters != null) {
            counters.activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        }
    }

    public void requestCompleted(String ip, int statusCode, long responseNanos) {
        long now = System.currentTimeMillis();
        totalRequests.increment();
        completedRequests.increment();
        totalResponseNanos.add(Math.max(0, responseNanos));
        requestTimes.addLast(now);
        if (statusCode >= 200 && statusCode < 400) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }

        ClientCounters counters = clients.computeIfAbsent(ip, ignored -> new ClientCounters());
        counters.totalRequests.increment();
        counters.requestTimes.addLast(now);
    }

    public void requestLimited() {
        limitedRequests.increment();
    }

    public void requestDropped() {
        droppedRequests.increment();
    }

    public TrafficStatistics snapshot() {
        long now = System.currentTimeMillis();
        prune(requestTimes, now - WINDOW_MILLIS);
        long completed = completedRequests.sum();
        double averageMillis = completed == 0
                ? 0
                : (totalResponseNanos.sum() / (double) completed) / 1_000_000.0;
        return new TrafficStatistics(
                totalRequests.sum(), successfulRequests.sum(), failedRequests.sum(),
                limitedRequests.sum(), droppedRequests.sum(), activeConnections.get(),
                requestTimes.size() / (WINDOW_MILLIS / 1_000.0), averageMillis);
    }

    public List<ClientInfo> clientSnapshots(int perClientRateThreshold) {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        return clients.entrySet().stream().map(entry -> {
            ClientCounters counters = entry.getValue();
            prune(counters.requestTimes, cutoff);
            int rate = (int) Math.ceil(counters.requestTimes.size() / (WINDOW_MILLIS / 1_000.0));
            AttackStatus status = rate >= perClientRateThreshold
                    ? AttackStatus.SUSPICIOUS : AttackStatus.NORMAL;
            return new ClientInfo(entry.getKey(), counters.totalRequests.sum(), rate,
                    counters.activeConnections.get(), status, false, 0);
        }).sorted(Comparator.comparingInt(ClientInfo::requestsPerSecond).reversed()
                .thenComparing(ClientInfo::ipAddress)).toList();
    }

    public void reset() {
        totalRequests.reset();
        successfulRequests.reset();
        failedRequests.reset();
        limitedRequests.reset();
        droppedRequests.reset();
        completedRequests.reset();
        totalResponseNanos.reset();
        requestTimes.clear();
        clients.clear();
    }

    private static void prune(ConcurrentLinkedDeque<Long> timestamps, long cutoff) {
        Long oldest;
        while ((oldest = timestamps.peekFirst()) != null && oldest < cutoff) {
            timestamps.pollFirst();
        }
    }

    private static final class ClientCounters {
        private final LongAdder totalRequests = new LongAdder();
        private final AtomicInteger activeConnections = new AtomicInteger();
        private final ConcurrentLinkedDeque<Long> requestTimes = new ConcurrentLinkedDeque<>();
    }
}
