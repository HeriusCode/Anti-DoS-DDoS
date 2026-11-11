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
        long activeSources = clients.stream()
                .filter(client -> client.requestsPerSecond() > 0)
                .count();
        ClientInfo busiest = clients.stream()
                .max(Comparator.comparingInt(ClientInfo::requestsPerSecond))
                .orElse(null);
        int clientRate = busiest == null ? 0 : busiest.requestsPerSecond();
        String clientIp = busiest == null ? null : busiest.ipAddress();

        // Several active sources exceeding the aggregate threshold are a DDoS.
        // This check must run before the per-client rule so a distributed attack
        // is not mislabeled as a single-source DoS because one node is busiest.
        if (activeSources >= 2 && globalRate >= config.getRequestRateThreshold()) {
            return attack(AttackType.DDOS, null,
                    "Distributed request rate exceeded the global threshold ("
                            + globalRate + "/" + config.getRequestRateThreshold()
                            + " req/s from " + activeSources + " sources)",
                    globalRate, config.getRequestRateThreshold());
        }
        if (clientRate >= config.getPerClientRateThreshold()) {
            return attack(AttackType.DOS, clientIp,
                    "Client " + clientIp + " exceeded the per-client rate threshold ("
                            + clientRate + "/" + config.getPerClientRateThreshold() + " req/s)",
                    clientRate, config.getPerClientRateThreshold());
        }
        if (globalRate >= config.getRequestRateThreshold()) {
            return attack(AttackType.DOS, clientIp,
                    "Global request rate exceeded the threshold (" + globalRate + "/"
                            + config.getRequestRateThreshold() + " req/s)",
                    globalRate, config.getRequestRateThreshold());
        }
        if (traffic.activeConnections() >= config.getMaxActiveConnections()) {
            return attack(activeSources >= 2 ? AttackType.DDOS : AttackType.DOS, clientIp,
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
            return attack(activeSources >= 2 ? AttackType.DDOS : AttackType.DOS,
                    elevatedClient ? clientIp : null,
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
        return new Result(AttackStatus.NORMAL, AttackType.NONE, null,
                "Traffic is within configured limits", globalRate, config.getRequestRateThreshold());
    }

    private static Result attack(AttackType attackType, String clientIp, String reason,
                                 double current, double threshold) {
        return new Result(AttackStatus.ATTACK_DETECTED, attackType, clientIp, reason, current, threshold);
    }

    private static Result suspicious(String clientIp, String reason, double current, double threshold) {
        return new Result(AttackStatus.SUSPICIOUS, AttackType.NONE, clientIp, reason, current, threshold);
    }

    private static int suspiciousLevel(int threshold) {
        return Math.max(1, (int) Math.ceil(threshold * SUSPICIOUS_RATIO));
    }

    private static int count(boolean value) {
        return value ? 1 : 0;
    }

    public record Result(AttackStatus status, AttackType attackType, String clientIp, String reason,
                         double currentValue, double threshold) {
    }
}
