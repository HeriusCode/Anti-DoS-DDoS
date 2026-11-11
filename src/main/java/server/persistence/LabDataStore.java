package server.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import server.config.DatabaseConfig;
import server.config.ServerConfig;
import server.detection.AttackStatus;
import server.detection.AttackType;
import server.model.ClientInfo;
import server.model.ProtectionStatus;
import server.monitor.TrafficStatistics;

/**
 * Asynchronous PostgreSQL writer. HTTP and JavaFX threads only enqueue small
 * immutable commands; JDBC work is performed by one daemon thread.
 */
public final class LabDataStore implements AutoCloseable {
    private static final int QUEUE_CAPACITY = 4_096;
    private static final int MAX_BATCH_SIZE = 256;

    private final DatabaseConfig databaseConfig;
    private final BlockingQueue<Command> commands = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final Thread writerThread;

    public LabDataStore(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
        writerThread = Thread.ofPlatform()
                .daemon(true)
                .name("lab-postgresql-writer")
                .start(this::writerLoop);
    }

    public void startSession(String username, String serverIp, int serverPort,
                             ServerConfig config, ProtectionStatus protection) {
        enqueue(new StartSession(Instant.now(), safe(username, "local-user"),
                safe(serverIp, "127.0.0.1"), serverPort,
                config.getRequestRateThreshold(), config.getPerClientRateThreshold(),
                config.getMaxActiveConnections(), config.getResponseTimeThresholdMillis(),
                config.getRateLimit(), config.getBlockDuration().toSeconds(),
                config.isAutoDefense(), protection.protectionEnabled()));
    }

    public void stopSession(String reason) {
        enqueue(new StopSession(Instant.now(), safe(reason, "SERVER_STOPPED")));
    }

    public void saveSecurityEvent(Instant timestamp, String level, String source,
                                  String eventType, String description) {
        enqueue(new SecurityEvent(timestamp == null ? Instant.now() : timestamp,
                safe(level, "INFO"), safe(source, "SERVER"),
                safe(eventType, "UNKNOWN"), safe(description, "No details")));
    }

    public void saveTrafficSnapshot(Instant timestamp, TrafficStatistics statistics,
                                    AttackStatus attackStatus, ProtectionStatus protection) {
        if (statistics == null) return;
        enqueue(new TrafficSnapshot(timestamp == null ? Instant.now() : timestamp,
                statistics, attackStatus == null ? AttackStatus.NORMAL : attackStatus,
                protection != null && protection.protectionEnabled()));
    }

    public void saveClientTrafficSnapshots(Instant timestamp, List<ClientInfo> clients) {
        if (clients == null || clients.isEmpty()) return;
        enqueue(new ClientTrafficSnapshot(timestamp == null ? Instant.now() : timestamp,
                List.copyOf(clients)));
    }

    public void saveDetectionEvent(Instant timestamp, String clientIp, AttackStatus status,
                                   AttackType attackType, String reason,
                                   double currentValue, double threshold) {
        enqueue(new DetectionEvent(timestamp == null ? Instant.now() : timestamp,
                nullable(clientIp), status == null ? AttackStatus.NORMAL : status,
                attackType == null ? AttackType.NONE : attackType,
                safe(reason, "No details"), Math.max(0, currentValue), Math.max(0, threshold)));
    }

    public void saveProtectionAction(Instant timestamp, String clientIp, String actionType,
                                     String actionState, String description) {
        enqueue(new ProtectionAction(timestamp == null ? Instant.now() : timestamp,
                nullable(clientIp), safe(actionType, "UNKNOWN"),
                safe(actionState, "APPLIED"), safe(description, "No details")));
    }

    public void saveServerSettings(String source, String username, ServerConfig config,
                                   ProtectionStatus protection) {
        enqueue(new ServerSettings(Instant.now(), safe(username, "local-user"),
                safe(source, "DASHBOARD"), config.getServerPort(),
                config.getRequestRateThreshold(), config.getPerClientRateThreshold(),
                config.getMaxActiveConnections(), config.getResponseTimeThresholdMillis(),
                config.getRateLimit(), config.getBlockDuration().toSeconds(),
                config.isAutoDefense(), protection.protectionEnabled(),
                protection.rateLimitActive(), protection.connectionLimitActive(),
                protection.blockingActive()));
    }

    /** Number of writes discarded only when PostgreSQL cannot keep up for a long time. */
    public int pendingWrites() {
        return commands.size();
    }

    private void enqueue(Command command) {
        if (!accepting.get()) return;
        if (!commands.offer(command)) {
            System.err.println("PostgreSQL writer queue is full; one telemetry record was discarded.");
        }
    }

    private void writerLoop() {
        Connection connection = null;
        long currentSessionId = 0;
        try {
            while (accepting.get() || !commands.isEmpty()) {
                Command first;
                try {
                    first = commands.poll(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    if (!accepting.get()) continue;
                    Thread.currentThread().interrupt();
                    break;
                }
                if (first == null) continue;

                List<Command> batch = new ArrayList<>(MAX_BATCH_SIZE);
                batch.add(first);
                commands.drainTo(batch, MAX_BATCH_SIZE - 1);

                try {
                    if (connection == null || connection.isClosed()) {
                        connection = databaseConfig.connect();
                    }
                    connection.setAutoCommit(false);
                    currentSessionId = persistBatch(connection, batch, currentSessionId);
                    connection.commit();
                } catch (SQLException exception) {
                    rollbackQuietly(connection);
                    closeQuietly(connection);
                    connection = null;
                    System.err.println("Could not persist PostgreSQL telemetry: " + exception.getMessage());
                }
            }
        } finally {
            closeQuietly(connection);
        }
    }

    private long persistBatch(Connection connection, List<Command> batch,
                              long currentSessionId) throws SQLException {
        long sessionId = currentSessionId;
        for (Command command : batch) {
            if (command instanceof StartSession start) {
                if (sessionId != 0) finishSession(connection, sessionId, start.startedAt(), "RESTARTED");
                sessionId = insertSession(connection, start);
            } else if (command instanceof StopSession stop) {
                if (sessionId != 0) finishSession(connection, sessionId, stop.stoppedAt(), stop.reason());
                sessionId = 0;
            } else if (command instanceof SecurityEvent event) {
                insertSecurityEvent(connection, sessionId, event);
            } else if (command instanceof TrafficSnapshot snapshot && sessionId != 0) {
                insertTrafficSnapshot(connection, sessionId, snapshot);
            } else if (command instanceof ClientTrafficSnapshot snapshot && sessionId != 0) {
                insertClientTrafficSnapshots(connection, sessionId, snapshot);
            } else if (command instanceof DetectionEvent event) {
                insertDetectionEvent(connection, sessionId, event);
            } else if (command instanceof ProtectionAction action) {
                insertProtectionAction(connection, sessionId, action);
            } else if (command instanceof ServerSettings settings) {
                insertServerSettings(connection, sessionId, settings);
            }
        }
        return sessionId;
    }

    private static long insertSession(Connection connection, StartSession session) throws SQLException {
        String sql = """
                INSERT INTO server_sessions (
                    account_id, username, server_ip, server_port, started_at, status,
                    request_rate_threshold, per_client_rate_threshold, max_active_connections,
                    response_time_threshold_ms, rate_limit, block_duration_seconds,
                    auto_defense, protection_enabled
                ) VALUES (
                    (SELECT id FROM lab_accounts WHERE LOWER(username) = LOWER(?) LIMIT 1),
                    ?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?, ?, ?, ?, ?
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, session.username());
            statement.setString(2, session.username());
            statement.setString(3, session.serverIp());
            statement.setInt(4, session.serverPort());
            statement.setObject(5, utc(session.startedAt()));
            statement.setInt(6, session.requestRateThreshold());
            statement.setInt(7, session.perClientRateThreshold());
            statement.setInt(8, session.maxActiveConnections());
            statement.setLong(9, session.responseTimeThresholdMillis());
            statement.setInt(10, session.rateLimit());
            statement.setLong(11, session.blockDurationSeconds());
            statement.setBoolean(12, session.autoDefense());
            statement.setBoolean(13, session.protectionEnabled());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("PostgreSQL did not return a server session id");
                return keys.getLong(1);
            }
        }
    }

    private static void finishSession(Connection connection, long sessionId,
                                      Instant stoppedAt, String reason) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE server_sessions
                SET stopped_at = ?, status = 'STOPPED', stop_reason = ?
                WHERE id = ?
                """)) {
            statement.setObject(1, utc(stoppedAt));
            statement.setString(2, reason);
            statement.setLong(3, sessionId);
            statement.executeUpdate();
        }
    }

    private static void insertSecurityEvent(Connection connection, long sessionId,
                                            SecurityEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO security_events
                    (session_id, occurred_at, level, source, event_type, description)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            if (sessionId == 0) statement.setNull(1, Types.BIGINT);
            else statement.setLong(1, sessionId);
            statement.setObject(2, utc(event.timestamp()));
            statement.setString(3, event.level());
            statement.setString(4, event.source());
            statement.setString(5, event.eventType());
            statement.setString(6, event.description());
            statement.executeUpdate();
        }
    }

    private static void insertTrafficSnapshot(Connection connection, long sessionId,
                                              TrafficSnapshot snapshot) throws SQLException {
        TrafficStatistics statistics = snapshot.statistics();
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO traffic_snapshots (
                    session_id, captured_at, total_requests, successful_requests,
                    failed_requests, limited_requests, dropped_requests, active_connections,
                    requests_per_second, average_response_time_ms, attack_status,
                    protection_enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setLong(1, sessionId);
            statement.setObject(2, utc(snapshot.timestamp()));
            statement.setLong(3, statistics.totalRequests());
            statement.setLong(4, statistics.successfulRequests());
            statement.setLong(5, statistics.failedRequests());
            statement.setLong(6, statistics.limitedRequests());
            statement.setLong(7, statistics.droppedRequests());
            statement.setInt(8, statistics.activeConnections());
            statement.setDouble(9, statistics.requestsPerSecond());
            statement.setDouble(10, statistics.averageResponseTimeMillis());
            statement.setString(11, snapshot.attackStatus().name());
            statement.setBoolean(12, snapshot.protectionEnabled());
            statement.executeUpdate();
        }
    }

    private static void insertClientTrafficSnapshots(Connection connection, long sessionId,
                                                     ClientTrafficSnapshot snapshot) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO client_traffic_snapshots (
                    session_id, captured_at, client_ip, total_requests, requests_per_second,
                    active_connections, attack_status, blocked, manually_blocked, blocked_until
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            for (ClientInfo client : snapshot.clients()) {
                statement.setLong(1, sessionId);
                statement.setObject(2, utc(snapshot.timestamp()));
                statement.setString(3, client.ipAddress());
                statement.setLong(4, client.totalRequests());
                statement.setInt(5, client.requestsPerSecond());
                statement.setInt(6, client.activeConnections());
                statement.setString(7, client.status().name());
                statement.setBoolean(8, client.blocked());
                statement.setBoolean(9, client.blockRemainingSeconds() < 0);
                if (client.blockRemainingSeconds() > 0) {
                    statement.setObject(10, utc(snapshot.timestamp().plusSeconds(client.blockRemainingSeconds())));
                } else {
                    statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void insertDetectionEvent(Connection connection, long sessionId,
                                             DetectionEvent event) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO detection_events (
                    session_id, occurred_at, client_ip, attack_status, attack_type,
                    reason, current_value, threshold
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            setNullableSession(statement, 1, sessionId);
            statement.setObject(2, utc(event.timestamp()));
            setNullableString(statement, 3, event.clientIp());
            statement.setString(4, event.status().name());
            statement.setString(5, event.attackType().name());
            statement.setString(6, event.reason());
            statement.setDouble(7, event.currentValue());
            statement.setDouble(8, event.threshold());
            statement.executeUpdate();
        }
    }

    private static void insertProtectionAction(Connection connection, long sessionId,
                                               ProtectionAction action) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO protection_actions (
                    session_id, occurred_at, client_ip, action_type, action_state, description
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            setNullableSession(statement, 1, sessionId);
            statement.setObject(2, utc(action.timestamp()));
            setNullableString(statement, 3, action.clientIp());
            statement.setString(4, action.actionType());
            statement.setString(5, action.actionState());
            statement.setString(6, action.description());
            statement.executeUpdate();
        }
    }

    private static void insertServerSettings(Connection connection, long sessionId,
                                             ServerSettings settings) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO server_settings (
                    session_id, username, saved_at, save_source, server_port,
                    request_rate_threshold, per_client_rate_threshold, max_active_connections,
                    response_time_threshold_ms, rate_limit, block_duration_seconds,
                    auto_defense, protection_enabled, rate_limit_active,
                    connection_limit_active, blocking_active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            setNullableSession(statement, 1, sessionId);
            statement.setString(2, settings.username());
            statement.setObject(3, utc(settings.savedAt()));
            statement.setString(4, settings.source());
            statement.setInt(5, settings.serverPort());
            statement.setInt(6, settings.requestRateThreshold());
            statement.setInt(7, settings.perClientRateThreshold());
            statement.setInt(8, settings.maxActiveConnections());
            statement.setLong(9, settings.responseTimeThresholdMillis());
            statement.setInt(10, settings.rateLimit());
            statement.setLong(11, settings.blockDurationSeconds());
            statement.setBoolean(12, settings.autoDefense());
            statement.setBoolean(13, settings.protectionEnabled());
            statement.setBoolean(14, settings.rateLimitActive());
            statement.setBoolean(15, settings.connectionLimitActive());
            statement.setBoolean(16, settings.blockingActive());
            statement.executeUpdate();
        }
    }

    private static void setNullableSession(PreparedStatement statement, int index,
                                           long sessionId) throws SQLException {
        if (sessionId == 0) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, sessionId);
    }

    private static void setNullableString(PreparedStatement statement, int index,
                                          String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void rollbackQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // The original database error is more useful than a rollback failure.
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Nothing else can be done during shutdown/reconnect.
        }
    }

    @Override
    public void close() {
        if (!accepting.compareAndSet(true, false)) return;
        writerThread.interrupt();
        try {
            writerThread.join(3_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private interface Command {
    }

    private record StartSession(Instant startedAt, String username, String serverIp, int serverPort,
                                int requestRateThreshold, int perClientRateThreshold,
                                int maxActiveConnections, long responseTimeThresholdMillis,
                                int rateLimit, long blockDurationSeconds, boolean autoDefense,
                                boolean protectionEnabled) implements Command {
    }

    private record StopSession(Instant stoppedAt, String reason) implements Command {
    }

    private record SecurityEvent(Instant timestamp, String level, String source,
                                 String eventType, String description) implements Command {
    }

    private record TrafficSnapshot(Instant timestamp, TrafficStatistics statistics,
                                   AttackStatus attackStatus,
                                   boolean protectionEnabled) implements Command {
    }

    private record ClientTrafficSnapshot(Instant timestamp,
                                         List<ClientInfo> clients) implements Command {
    }

    private record DetectionEvent(Instant timestamp, String clientIp, AttackStatus status,
                                  AttackType attackType, String reason, double currentValue,
                                  double threshold) implements Command {
    }

    private record ProtectionAction(Instant timestamp, String clientIp, String actionType,
                                    String actionState, String description) implements Command {
    }

    private record ServerSettings(Instant savedAt, String username, String source, int serverPort,
                                  int requestRateThreshold, int perClientRateThreshold,
                                  int maxActiveConnections, long responseTimeThresholdMillis,
                                  int rateLimit, long blockDurationSeconds, boolean autoDefense,
                                  boolean protectionEnabled, boolean rateLimitActive,
                                  boolean connectionLimitActive,
                                  boolean blockingActive) implements Command {
    }
}
