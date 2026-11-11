package server.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import server.config.ServerConfig;

class RequestGuardTest {
    @Test
    void distributedClientsShareTheGlobalRequestBudget() {
        RequestGuard guard = new RequestGuard(config(5, 10));

        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.1"));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.2"));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.3"));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.1"));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.2"));
        assertEquals(RequestGuard.Decision.GLOBAL_LIMITED,
                guard.checkRequestByIp("10.0.0.3"));
    }

    @Test
    void perClientLimitStillProtectsAgainstOneBusySource() {
        RequestGuard guard = new RequestGuard(config(100, 2));

        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.1"));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp("10.0.0.1"));
        assertEquals(RequestGuard.Decision.LIMITED, guard.checkRequestByIp("10.0.0.1"));
    }

    @Test
    void disablingProtectionRemovesBothLimits() {
        RequestGuard guard = new RequestGuard(config(1, 1));
        guard.setProtectionEnabled(false);

        for (int index = 0; index < 20; index++) {
            assertEquals(RequestGuard.Decision.ALLOW,
                    guard.checkRequestByIp("10.0.0." + (index + 1)));
        }
    }

    @Test
    void manualBlockPersistsUntilExplicitlyUnblocked() {
        RequestGuard guard = new RequestGuard(config(100, 100));
        String ip = "10.0.0.9";

        assertEquals(true, guard.blockClientManually(ip));
        assertEquals(-1, guard.blockRemainingSeconds(ip));
        assertEquals(RequestGuard.Decision.MANUALLY_BLOCKED, guard.enter(ip));

        guard.setProtectionEnabled(false);
        guard.reset();
        assertEquals(RequestGuard.Decision.MANUALLY_BLOCKED, guard.checkRequestByIp(ip));

        assertEquals(true, guard.unblockClientManually(ip));
        assertEquals(RequestGuard.Decision.ALLOW, guard.checkRequestByIp(ip));
    }

    private static ServerConfig config(int globalLimit, int perClientLimit) {
        return new ServerConfig(8080, globalLimit, 50, 100, 1_000,
                perClientLimit, Duration.ofSeconds(30), true);
    }
}
