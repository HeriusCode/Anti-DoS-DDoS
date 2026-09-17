package server.model;

import server.detection.AttackStatus;

public record ClientInfo(
        String ipAddress,
        long totalRequests,
        int requestsPerSecond,
        int activeConnections,
        AttackStatus status,
        boolean blocked,
        long blockRemainingSeconds) {
}
