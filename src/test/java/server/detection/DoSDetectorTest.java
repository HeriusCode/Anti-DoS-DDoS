package server.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import server.config.ServerConfig;
import server.model.ClientInfo;
import server.monitor.TrafficStatistics;

class DoSDetectorTest {
    @Test
    void oneBusySourceIsClassifiedAsDos() {
        DoSDetector detector = new DoSDetector(config());
        TrafficStatistics traffic = statistics(120);
        List<ClientInfo> clients = List.of(client("10.0.0.1", 120));

        DoSDetector.Result result = detector.evaluate(traffic, clients);

        assertEquals(AttackStatus.ATTACK_DETECTED, result.status());
        assertEquals(AttackType.DOS, result.attackType());
    }

    @Test
    void aggregateTrafficFromSeveralSourcesIsClassifiedAsDdos() {
        DoSDetector detector = new DoSDetector(config());
        TrafficStatistics traffic = statistics(120);
        List<ClientInfo> clients = List.of(
                client("10.0.0.1", 40),
                client("10.0.0.2", 40),
                client("10.0.0.3", 40));

        DoSDetector.Result result = detector.evaluate(traffic, clients);

        assertEquals(AttackStatus.ATTACK_DETECTED, result.status());
        assertEquals(AttackType.DDOS, result.attackType());
    }

    private static ServerConfig config() {
        return new ServerConfig(8080, 100, 100, 1_000, 100,
                50, Duration.ofSeconds(30), true);
    }

    private static TrafficStatistics statistics(double requestsPerSecond) {
        return new TrafficStatistics(1_000, 1_000, 0, 0, 0,
                10, requestsPerSecond, 10);
    }

    private static ClientInfo client(String ip, int requestsPerSecond) {
        return new ClientInfo(ip, 100, requestsPerSecond, 1,
                AttackStatus.NORMAL, false, 0);
    }
}
