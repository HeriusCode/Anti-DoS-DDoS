package server.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import server.config.DatabaseConfig;

/** Creates the small, version-one PostgreSQL schema used by the lab. */
public final class DatabaseSchema {
    private DatabaseSchema() {
    }

    public static void initialize(DatabaseConfig config) throws SQLException {
        try (Connection connection = config.connect(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS lab_accounts (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        username VARCHAR(32) NOT NULL,
                        email VARCHAR(254) NOT NULL,
                        password_hash VARCHAR(255) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS lab_accounts_username_unique "
                    + "ON lab_accounts (LOWER(username))");
            statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS lab_accounts_email_unique "
                    + "ON lab_accounts (LOWER(email))");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS server_sessions (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        account_id BIGINT REFERENCES lab_accounts(id) ON DELETE SET NULL,
                        username VARCHAR(32) NOT NULL,
                        server_ip VARCHAR(45) NOT NULL,
                        server_port INTEGER NOT NULL CHECK (server_port BETWEEN 1 AND 65535),
                        started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        stopped_at TIMESTAMPTZ,
                        status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
                        stop_reason VARCHAR(64),
                        request_rate_threshold INTEGER NOT NULL,
                        per_client_rate_threshold INTEGER NOT NULL,
                        max_active_connections INTEGER NOT NULL,
                        response_time_threshold_ms BIGINT NOT NULL,
                        rate_limit INTEGER NOT NULL,
                        block_duration_seconds BIGINT NOT NULL,
                        auto_defense BOOLEAN NOT NULL,
                        protection_enabled BOOLEAN NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS server_sessions_started_at_idx "
                    + "ON server_sessions (started_at DESC)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS security_events (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT REFERENCES server_sessions(id) ON DELETE SET NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        level VARCHAR(16) NOT NULL,
                        source VARCHAR(64) NOT NULL,
                        event_type VARCHAR(64) NOT NULL,
                        description TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS security_events_time_idx "
                    + "ON security_events (occurred_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS security_events_session_time_idx "
                    + "ON security_events (session_id, occurred_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS security_events_source_idx "
                    + "ON security_events (source)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS traffic_snapshots (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT NOT NULL REFERENCES server_sessions(id) ON DELETE CASCADE,
                        captured_at TIMESTAMPTZ NOT NULL,
                        total_requests BIGINT NOT NULL CHECK (total_requests >= 0),
                        successful_requests BIGINT NOT NULL CHECK (successful_requests >= 0),
                        failed_requests BIGINT NOT NULL CHECK (failed_requests >= 0),
                        limited_requests BIGINT NOT NULL CHECK (limited_requests >= 0),
                        dropped_requests BIGINT NOT NULL CHECK (dropped_requests >= 0),
                        active_connections INTEGER NOT NULL CHECK (active_connections >= 0),
                        requests_per_second DOUBLE PRECISION NOT NULL CHECK (requests_per_second >= 0),
                        average_response_time_ms DOUBLE PRECISION NOT NULL
                            CHECK (average_response_time_ms >= 0),
                        attack_status VARCHAR(32) NOT NULL,
                        protection_enabled BOOLEAN NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS traffic_snapshots_session_time_idx "
                    + "ON traffic_snapshots (session_id, captured_at DESC)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS client_traffic_snapshots (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT NOT NULL REFERENCES server_sessions(id) ON DELETE CASCADE,
                        captured_at TIMESTAMPTZ NOT NULL,
                        client_ip VARCHAR(45) NOT NULL,
                        total_requests BIGINT NOT NULL CHECK (total_requests >= 0),
                        requests_per_second INTEGER NOT NULL CHECK (requests_per_second >= 0),
                        active_connections INTEGER NOT NULL CHECK (active_connections >= 0),
                        attack_status VARCHAR(32) NOT NULL,
                        blocked BOOLEAN NOT NULL,
                        manually_blocked BOOLEAN NOT NULL,
                        blocked_until TIMESTAMPTZ
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS client_traffic_session_time_idx "
                    + "ON client_traffic_snapshots (session_id, captured_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS client_traffic_ip_time_idx "
                    + "ON client_traffic_snapshots (client_ip, captured_at DESC)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS detection_events (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT REFERENCES server_sessions(id) ON DELETE SET NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        client_ip VARCHAR(45),
                        attack_status VARCHAR(32) NOT NULL,
                        attack_type VARCHAR(16) NOT NULL,
                        reason TEXT NOT NULL,
                        current_value DOUBLE PRECISION NOT NULL,
                        threshold DOUBLE PRECISION NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS detection_events_session_time_idx "
                    + "ON detection_events (session_id, occurred_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS detection_events_status_time_idx "
                    + "ON detection_events (attack_status, occurred_at DESC)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS protection_actions (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT REFERENCES server_sessions(id) ON DELETE SET NULL,
                        occurred_at TIMESTAMPTZ NOT NULL,
                        client_ip VARCHAR(64),
                        action_type VARCHAR(64) NOT NULL,
                        action_state VARCHAR(24) NOT NULL,
                        description TEXT NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS protection_actions_session_time_idx "
                    + "ON protection_actions (session_id, occurred_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS protection_actions_client_time_idx "
                    + "ON protection_actions (client_ip, occurred_at DESC)");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS server_settings (
                        id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        session_id BIGINT REFERENCES server_sessions(id) ON DELETE SET NULL,
                        username VARCHAR(32) NOT NULL,
                        saved_at TIMESTAMPTZ NOT NULL,
                        save_source VARCHAR(64) NOT NULL,
                        server_port INTEGER NOT NULL CHECK (server_port BETWEEN 1 AND 65535),
                        request_rate_threshold INTEGER NOT NULL,
                        per_client_rate_threshold INTEGER NOT NULL,
                        max_active_connections INTEGER NOT NULL,
                        response_time_threshold_ms BIGINT NOT NULL,
                        rate_limit INTEGER NOT NULL,
                        block_duration_seconds BIGINT NOT NULL,
                        auto_defense BOOLEAN NOT NULL,
                        protection_enabled BOOLEAN NOT NULL,
                        rate_limit_active BOOLEAN NOT NULL,
                        connection_limit_active BOOLEAN NOT NULL,
                        blocking_active BOOLEAN NOT NULL
                    )
                    """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS server_settings_session_time_idx "
                    + "ON server_settings (session_id, saved_at DESC)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS server_settings_user_time_idx "
                    + "ON server_settings (username, saved_at DESC)");
        }
    }
}
