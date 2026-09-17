package server.model;

import java.time.Duration;

public record ServerStatus(boolean running, int port, Duration uptime) {
}
