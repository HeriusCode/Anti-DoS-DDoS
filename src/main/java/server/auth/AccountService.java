package server.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Locale;
import server.config.DatabaseConfig;

/** PostgreSQL account persistence and authentication, independent from JavaFX. */
public final class AccountService {
    private final DatabaseConfig config;
    private volatile boolean initialized;

    public AccountService(DatabaseConfig config) { this.config = config; }

    public synchronized void initialize() throws SQLException {
        if (initialized) return;
        try (Connection ignored = config.connect()) {
            // The normal path needs no administrative connection.
        } catch (SQLException exception) {
            if (!"3D000".equals(exception.getSQLState())) throw exception;
            try (Connection connection = config.connectAdministration();
                 Statement statement = connection.createStatement()) {
                // Identifier is validated by DatabaseConfig before interpolation.
                statement.executeUpdate("CREATE DATABASE \"" + config.databaseName() + "\"");
            } catch (SQLException createError) {
                if (!"42P04".equals(createError.getSQLState())) throw createError;
            }
        }
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
        }
        initialized = true;
    }

    public String register(String username, String email, char[] password, char[] confirmation) throws SQLException {
        try {
            String cleanName = username == null ? "" : username.trim();
            String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
            if (!cleanName.matches("[a-zA-Z0-9_]{3,32}")) {
                throw new IllegalArgumentException("Tên đăng nhập gồm 3–32 chữ cái, chữ số hoặc dấu gạch dưới.");
            }
            if (cleanEmail.length() > 254 || !cleanEmail.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
                throw new IllegalArgumentException("Vui lòng nhập email hợp lệ.");
            }
            if (password == null || password.length < 8 || password.length > 128) {
                throw new IllegalArgumentException("Mật khẩu cần từ 8 đến 128 ký tự.");
            }
            if (!Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Mật khẩu nhập lại không khớp.");
            }
            initialize();
            String hash = PasswordHasher.hash(password);
            try (Connection connection = config.connect(); PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO lab_accounts (username, email, password_hash) VALUES (?, ?, ?)")) {
                statement.setString(1, cleanName);
                statement.setString(2, cleanEmail);
                statement.setString(3, hash);
                statement.executeUpdate();
            } catch (SQLException exception) {
                if ("23505".equals(exception.getSQLState())) {
                    throw new IllegalArgumentException("Tên đăng nhập hoặc email đã được sử dụng.");
                }
                throw exception;
            }
            return cleanName;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    public String login(String identifier, char[] password) throws SQLException {
        try {
            if (identifier == null || identifier.isBlank() || password == null || password.length == 0
                    || password.length > 128 || identifier.length() > 254) {
                throw new IllegalArgumentException("Vui lòng nhập tên đăng nhập/email và mật khẩu.");
            }
            initialize();
            try (Connection connection = config.connect(); PreparedStatement statement = connection.prepareStatement(
                    "SELECT username, password_hash FROM lab_accounts WHERE LOWER(username) = LOWER(?) "
                            + "OR LOWER(email) = LOWER(?)")) {
                statement.setString(1, identifier.trim());
                statement.setString(2, identifier.trim());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && PasswordHasher.verify(password, result.getString("password_hash"))) {
                        return result.getString("username");
                    }
                }
            }
            throw new IllegalArgumentException("Tài khoản hoặc mật khẩu không chính xác.");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    public static String connectionError(SQLException exception) {
        String state = exception.getSQLState();
        if ("28P01".equals(state) || "28000".equals(state)) {
            return "Sai tài khoản PostgreSQL. Kiểm tra config/database.properties.";
        }
        if ("42501".equals(state)) return "Tài khoản PostgreSQL chưa có quyền tạo database/bảng.";
        return "Không kết nối được PostgreSQL. Kiểm tra dịch vụ, cổng và config/database.properties.";
    }
}
