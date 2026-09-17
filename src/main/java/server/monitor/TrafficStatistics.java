package server.monitor;

/** Immutable statistics snapshot safe to move from server threads to the UI. */
public record TrafficStatistics(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long limitedRequests,
        long droppedRequests,
        int activeConnections,
        double requestsPerSecond,
        double averageResponseTimeMillis) {

    public static TrafficStatistics empty() {
        return new TrafficStatistics(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
