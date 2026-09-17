package server.model;

import java.time.Instant;
import server.detection.AttackStatus;

public record DetectionEvent(
        Instant timestamp,
        String clientIp,
        AttackStatus status,
        String reason,
        double requestsPerSecond,
        double threshold) {
}
