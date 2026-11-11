package server.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Local PostgreSQL settings; credentials are never shipped in application resources. */
public final class DatabaseConfig {
    private final String host;
    private final int port;
    private final String database;
    private final Properties connectionProperties = new Properties();

    private DatabaseConfig(Properties settings) {
        host = value(settings, "db.host", "LAB_DB_HOST", "localhost");
        port = Integer.parseInt(value(settings, "db.port", "LAB_DB_PORT", "5432"));
        database = value(settings, "db.name", "LAB_DB_NAME", "dos_protection_lab");
        if (!database.matches("[a-zA-Z_][a-zA-Z0-9_]{0,62}") || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Tên database hoặc cổng PostgreSQL không hợp lệ.");
        }
        connectionProperties.setProperty("user", value(settings, "db.user", "LAB_DB_USER", "postgres"));
        connectionProperties.setProperty("password", value(settings, "db.password", "LAB_DB_PASSWORD", ""));
        connectionProperties.setProperty("connectTimeout", "5");
        connectionProperties.setProperty("socketTimeout", "10");
        connectionProperties.setProperty("ApplicationName", "DoS Protection Lab Server");
    }

    public static DatabaseConfig load() throws IOException {
        Properties properties = new Properties();
        Path path = Path.of("config", "database.properties");
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return new DatabaseConfig(properties);
    }

    public Connection connect() throws SQLException {
        return connect(database);
    }

    public Connection connectAdministration() throws SQLException {
        return connect("postgres");
    }

    private Connection connect(String name) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + name,
                connectionProperties);
    }

    public String databaseName() { return database; }

    private static String value(Properties settings, String key, String environment, String fallback) {
        String override = System.getenv(environment);
        return override == null ? settings.getProperty(key, fallback) : override;
    }
}
