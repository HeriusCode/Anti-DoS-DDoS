package server.model;

public record ProtectionStatus(
        boolean protectionEnabled,
        boolean autoDefense,
        boolean rateLimitActive,
        boolean connectionLimitActive,
        boolean blockingActive) {
}
