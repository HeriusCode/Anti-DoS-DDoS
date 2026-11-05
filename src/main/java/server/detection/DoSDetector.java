package server.detection;

import java.util.Comparator;
import java.util.List;
import server.config.ServerConfig;
import server.model.ClientInfo;
import server.monitor.TrafficStatistics;

/** Rule-based detector combining global traffic and per-client LAN traffic. */
public final class DoSDetector {
    private static final double SUSPICIOUS_RATIO = 0.70;

    private final ServerConfig config;

    public DoSDetector(ServerConfig config) {
        this.config = config;
    }

    public Result evaluate(TrafficStatistics traffic, List<ClientInfo> clients) {
        int globalRate = (int) Math.ceil(traffic.requestsPerSecond());
        ClientInfo busiest = clients.stream()
                .max(Comparator.comparingInt(ClientInfo::requestsPerSecond))
                .orElse(null);
        int clientRate = busiest == null ? 0 : busiest.requestsPerSecond();
        String clientIp = busiest == null ? null : busiest.ipAddress();

        if (clientRate >= config.getPerClientRateThreshold()) {
            return attack(clientIp,
                    "Client " + clientIp + " exceeded the per-client rate threshold ("
                            + clientRate + "/" + config.getPerClientRateThreshold() + " req/s)",
                    clientRate, config.getPerClientRateThreshold());
        }
        if (globalRate >= config.getRequestRateThreshold()) {
            return attack(null,
                    "Global request rate exceeded the threshold (" + globalRate + "/"
                            + config.getRequestRateThreshold() + " req/s)",
                    globalRate, config.getRequestRateThreshold());
        }
        if (traffic.activeConnections() >= config.getMaxActiveConnections()) {
            return attack(clientIp,
                    "Active connections reached the configured limit ("
                            + traffic.activeConnections() + "/" + config.getMaxActiveConnections() + ")",
                    traffic.activeConnections(), config.getMaxActiveConnections());
        }

        boolean elevatedGlobal = globalRate >= suspiciousLevel(config.getRequestRateThreshold());
        boolean elevatedClient = clientRate >= suspiciousLevel(config.getPerClientRateThreshold());
        boolean elevatedConnections = traffic.activeConnections()
                >= suspiciousLevel(config.getMaxActiveConnections());
        boolean slowResponses = traffic.averageResponseTimeMillis()
                >= config.getResponseTimeThresholdMillis();

        int warningSignals = count(elevatedGlobal) + count(elevatedClient)
                + count(elevatedConnections) + count(slowResponses);
        if (warningSignals >= 2) {
            return attack(elevatedClient ? clientIp : null,
                    "Multiple abnormal traffic conditions were detected",
                    globalRate, config.getRequestRateThreshold());
        }
        if (elevatedClient) {
            return suspicious(clientIp,
                    "High request rate from client " + clientIp + " (" + clientRate + " req/s)",
                    clientRate, config.getPerClientRateThreshold());
        }
        if (elevatedGlobal) {
            return suspicious(null, "Global request rate is approaching the threshold ("
                    + globalRate + " req/s)", globalRate, config.getRequestRateThreshold());
        }
        if (elevatedConnections) {
            return suspicious(clientIp, "Active connections are approaching the limit",
                    traffic.activeConnections(), config.getMaxActiveConnections());
        }
        if (slowResponses) {
            return suspicious(clientIp, "Average response time exceeded the threshold",
                    traffic.averageResponseTimeMillis(), config.getResponseTimeThresholdMillis());
        }
        return new Result(AttackStatus.NORMAL, null,
                "Traffic is within configured limits", globalRate, config.getRequestRateThreshold());
    }

    private static Result attack(String clientIp, String reason, double current, double threshold) {
        return new Result(AttackStatus.ATTACK_DETECTED, clientIp, reason, current, threshold);
    }

    private static Result suspicious(String clientIp, String reason, double current, double threshold) {
        return new Result(AttackStatus.SUSPICIOUS, clientIp, reason, current, threshold);
    }

    private static int suspiciousLevel(int threshold) {
        return Math.max(1, (int) Math.ceil(threshold * SUSPICIOUS_RATIO));
    }

    private static int count(boolean value) {
        return value ? 1 : 0;
    }

    public record Result(AttackStatus status, String clientIp, String reason,
                         double currentValue, double threshold) {
    }
}
