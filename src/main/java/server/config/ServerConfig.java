package server.config;

import java.time.Duration;

public final class ServerConfig {
    private volatile int serverPort;
    private volatile int requestRateThreshold;
    private volatile int perClientRateThreshold;
    private volatile int maxActiveConnections;
    private volatile long responseTimeThresholdMillis;
    private volatile int rateLimit;
    private volatile Duration blockDuration;
    private volatile boolean autoDefense;

    public ServerConfig(int serverPort,
                        int requestRateThreshold,
                        int perClientRateThreshold,
                        int maxActiveConnections,
                        long responseTimeThresholdMillis,
                        int rateLimit,
                        Duration blockDuration,
                        boolean autoDefense) {
        setServerPort(serverPort);
        setRequestRateThreshold(requestRateThreshold);
        setPerClientRateThreshold(perClientRateThreshold);
        setMaxActiveConnections(maxActiveConnections);
        setResponseTimeThresholdMillis(responseTimeThresholdMillis);
        setRateLimit(rateLimit);
        setBlockDuration(blockDuration);
        this.autoDefense = autoDefense;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(8080, 100, 50, 100, 1_000,
                50, Duration.ofSeconds(30), true);
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        if (serverPort < 1 || serverPort > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        this.serverPort = serverPort;
    }

    public int getRequestRateThreshold() {
        return requestRateThreshold;
    }

    public void setRequestRateThreshold(int value) {
        requestRateThreshold = requirePositive(value, "Request-rate threshold");
    }

    public int getPerClientRateThreshold() {
        return perClientRateThreshold;
    }

    public void setPerClientRateThreshold(int value) {
        perClientRateThreshold = requirePositive(value, "Per-client threshold");
    }

    public int getMaxActiveConnections() {
        return maxActiveConnections;
    }

    public void setMaxActiveConnections(int value) {
        maxActiveConnections = requirePositive(value, "Maximum connections");
    }

    public long getResponseTimeThresholdMillis() {
        return responseTimeThresholdMillis;
    }

    public void setResponseTimeThresholdMillis(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Response-time threshold must be positive");
        }
        responseTimeThresholdMillis = value;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(int value) {
        rateLimit = requirePositive(value, "Rate limit");
    }

    public Duration getBlockDuration() {
        return blockDuration;
    }

    public void setBlockDuration(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalArgumentException("Block duration must be positive");
        }
        blockDuration = value;
    }

    public boolean isAutoDefense() {
        return autoDefense;
    }

    public void setAutoDefense(boolean autoDefense) {
        this.autoDefense = autoDefense;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
