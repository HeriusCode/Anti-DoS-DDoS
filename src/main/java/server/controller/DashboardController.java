package server.controller;

import java.awt.Desktop;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import server.config.ServerConfig;
import server.detection.AttackStatus;
import server.model.ClientInfo;
import server.model.ProtectionStatus;
import server.monitor.TrafficMonitor;
import server.monitor.TrafficStatistics;
import server.web.RequestGuard;
import server.web.WebServer;

/** Connects the JavaFX dashboard to the real TCP/HTTP server and traffic monitor. */
public final class DashboardController implements AutoCloseable {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_CHART_POINTS = 60;
    private static final int MAX_LOG_LINES = 250;

    private final ServerConfig config;
    private final TrafficMonitor trafficMonitor = new TrafficMonitor();
    private final RequestGuard requestGuard;
    private final WebServer webServer;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicLong lastRequestLogNanos = new AtomicLong();
    private final AtomicLong lastProtectionLogNanos = new AtomicLong();
    private final Timeline timeline;

    private final BooleanProperty serverRunning = new SimpleBooleanProperty(false);
    private final BooleanProperty protectionEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty autoDefense = new SimpleBooleanProperty(true);
    private final BooleanProperty rateLimitActive = new SimpleBooleanProperty(true);
    private final BooleanProperty connectionLimitActive = new SimpleBooleanProperty(true);
    private final BooleanProperty blockingActive = new SimpleBooleanProperty(true);
    private final ObjectProperty<AttackStatus> attackStatus =
            new SimpleObjectProperty<>(AttackStatus.NORMAL);

    private final LongProperty totalRequests = new SimpleLongProperty();
    private final LongProperty successfulRequests = new SimpleLongProperty();
    private final LongProperty failedRequests = new SimpleLongProperty();
    private final LongProperty limitedRequests = new SimpleLongProperty();
    private final LongProperty droppedRequests = new SimpleLongProperty();
    private final IntegerProperty serverPort;
    private final IntegerProperty requestsPerSecond = new SimpleIntegerProperty();
    private final IntegerProperty activeConnections = new SimpleIntegerProperty();
    private final DoubleProperty averageResponseTime = new SimpleDoubleProperty();

    private final StringProperty clock = new SimpleStringProperty(LocalTime.now().format(CLOCK_FORMAT));
    private final StringProperty uptime = new SimpleStringProperty("00:00:00");
    private final StringProperty serverIp = new SimpleStringProperty(findPrivateIpv4());
    private final StringProperty detectionReason = new SimpleStringProperty("Server is stopped");
    private final StringProperty detectedAt = new SimpleStringProperty("--:--:--");

    private final ObservableList<ClientInfo> clients = FXCollections.observableArrayList();
    private final ObservableList<String> logs = FXCollections.observableArrayList();
    private final ObservableList<ChartPoint> chartPoints = FXCollections.observableArrayList();

    private Instant startedAt;
    private long chartIndex;
    private long previousChartTotal;
    private long previousChartLimited;
    private long previousChartDropped;
    private AttackStatus previousStatus = AttackStatus.NORMAL;

    public DashboardController(ServerConfig config) {
        this.config = config;
        this.serverPort = new SimpleIntegerProperty(config.getServerPort());
        this.autoDefense.set(config.isAutoDefense());
        this.requestGuard = new RequestGuard(config);
        this.webServer = new WebServer(new ServerObserver(), requestGuard);
        appendLog("INFO", "SERVER", "APPLICATION_STARTED", "Dashboard initialized; server is stopped");
        timeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> updateDashboard()));
        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void startUiUpdates() {
        timeline.play();
    }

    /** Starts the real server and opens its home page in the default browser. */
    public boolean startServer(int port) {
        if (serverRunning.get()) {
            return true;
        }
        try {
            config.setServerPort(port);
            webServer.start(port);
            serverPort.set(port);
            startedAt = Instant.now();
            serverRunning.set(true);
            detectionReason.set("Traffic is within configured limits");
            appendLog("INFO", "SERVER", "SERVER_STARTED", "Listening on 0.0.0.0:" + port);
            openServerPage();
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            appendLog("ERROR", "SERVER", "START_FAILED", exception.getMessage());
            serverRunning.set(false);
            return false;
        }
    }

    public void stopServer() {
        if (!serverRunning.get()) {
            return;
        }
        webServer.stop();
        serverRunning.set(false);
        startedAt = null;
        requestsPerSecond.set(0);
        activeConnections.set(0);
        attackStatus.set(AttackStatus.NORMAL);
        previousStatus = AttackStatus.NORMAL;
        detectionReason.set("Server is stopped");
        appendLog("INFO", "SERVER", "SERVER_STOPPED", "TCP listener and worker pool stopped");
    }

    /** Sends a background HTTP health request. It never opens a browser window. */
    public CompletableFuture<Boolean> testConnection() {
        if (!serverRunning.get()) {
            appendLog("ERROR", "SERVER", "TEST_FAILED", "Start the server before testing the connection");
            return CompletableFuture.completedFuture(false);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(localServerUrl() + "health"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    boolean connected = error == null && response.statusCode() == 200;
                    appendLog(connected ? "INFO" : "ERROR", "SERVER",
                            connected ? "TEST_OK" : "TEST_FAILED",
                            connected ? "HTTP health check returned 200 OK"
                                    : error == null ? "HTTP status " + response.statusCode() : error.getMessage());
                    return connected;
                });
    }

    public void resetStatistics() {
        trafficMonitor.reset();
        requestGuard.reset();
        previousChartTotal = 0;
        previousChartLimited = 0;
        previousChartDropped = 0;
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        limitedRequests.set(0);
        droppedRequests.set(0);
        requestsPerSecond.set(0);
        averageResponseTime.set(0);
        chartPoints.clear();
        clients.clear();
        attackStatus.set(AttackStatus.NORMAL);
        previousStatus = AttackStatus.NORMAL;
        detectionReason.set(serverRunning.get() ? "Statistics have been reset" : "Server is stopped");
        appendLog("INFO", "SERVER", "RESET", "Traffic statistics cleared");
    }

    public void setProtectionEnabled(boolean enabled) {
        requestGuard.setProtectionEnabled(enabled);
        protectionEnabled.set(enabled);
        setRateLimitActive(enabled);
        setConnectionLimitActive(enabled);
        setBlockingActive(enabled);
        appendLog(enabled ? "DEFENSE" : "WARNING", "SERVER",
                enabled ? "PROTECTION_ENABLED" : "PROTECTION_DISABLED",
                enabled ? "Protection mechanisms enabled" : "Protection mechanisms disabled");
    }

    public void setRateLimitActive(boolean enabled) {
        requestGuard.setRateLimitActive(enabled);
        updateProtectionSwitch(rateLimitActive, enabled, "RATE_LIMIT", "Rate Limiting");
    }

    public void setConnectionLimitActive(boolean enabled) {
        requestGuard.setConnectionLimitActive(enabled);
        updateProtectionSwitch(connectionLimitActive, enabled, "CONNECTION_LIMIT", "Connection Limiting");
    }

    public void setBlockingActive(boolean enabled) {
        requestGuard.setBlockingActive(enabled);
        updateProtectionSwitch(blockingActive, enabled, "TEMP_BLOCK", "Temporary Blocking");
    }

    public void setAutoDefense(boolean enabled) {
        if (autoDefense.get() == enabled) {
            return;
        }
        autoDefense.set(enabled);
        config.setAutoDefense(enabled);
        if (!enabled) requestGuard.clearBlocks();
        appendLog("INFO", "SERVER", "AUTO_DEFENSE", enabled ? "Auto Defense ON" : "Auto Defense OFF");
    }

    private void updateProtectionSwitch(BooleanProperty property, boolean enabled,
                                        String event, String displayName) {
        if (property.get() == enabled) {
            return;
        }
        property.set(enabled);
        appendLog("DEFENSE", "SERVER", event, displayName + (enabled ? " ON" : " OFF"));
    }

    public void clearLogs() {
        logs.clear();
    }

    public void appendLog(String level, String ip, String event, String description) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendLog(level, ip, event, description));
            return;
        }
        String safeDescription = description == null || description.isBlank() ? "No details" : description;
        String entry = "%s | %-9s | %-15s | %-20s | %s".formatted(
                LocalTime.now().format(CLOCK_FORMAT), level, ip, event, safeDescription);
        logs.add(entry);
        if (logs.size() > MAX_LOG_LINES) {
            logs.remove(0, logs.size() - MAX_LOG_LINES);
        }
    }

    private void updateDashboard() {
        clock.set(LocalTime.now().format(CLOCK_FORMAT));
        updateUptime();
        if (!serverRunning.get()) {
            return;
        }

        TrafficStatistics statistics = trafficMonitor.snapshot();
        totalRequests.set(statistics.totalRequests());
        successfulRequests.set(statistics.successfulRequests());
        failedRequests.set(statistics.failedRequests());
        limitedRequests.set(statistics.limitedRequests());
        droppedRequests.set(statistics.droppedRequests());
        activeConnections.set(statistics.activeConnections());
        averageResponseTime.set(statistics.averageResponseTimeMillis());

        int rps = (int) Math.ceil(statistics.requestsPerSecond());
        requestsPerSecond.set(rps);
        clients.setAll(trafficMonitor.clientSnapshots(config.getPerClientRateThreshold()).stream()
                .map(client -> {
                    long remaining = requestGuard.blockRemainingSeconds(client.ipAddress());
                    return new ClientInfo(client.ipAddress(), client.totalRequests(), client.requestsPerSecond(),
                            client.activeConnections(), client.status(), remaining > 0, remaining);
                }).toList());

        AttackStatus next = classify(rps);
        attackStatus.set(next);
        updateDetectionText(next, rps);
        if (next != previousStatus) {
            logTransition(next, rps);
            previousStatus = next;
        }

        long totalNow = statistics.totalRequests();
        long limitedNow = statistics.limitedRequests();
        long droppedNow = statistics.droppedRequests();
        int totalDuringSecond = (int) Math.min(Integer.MAX_VALUE, Math.max(0, totalNow - previousChartTotal));
        int limitedDuringSecond = (int) Math.min(Integer.MAX_VALUE, Math.max(0, limitedNow - previousChartLimited));
        int droppedDuringSecond = (int) Math.min(Integer.MAX_VALUE, Math.max(0, droppedNow - previousChartDropped));
        previousChartTotal = totalNow;
        previousChartLimited = limitedNow;
        previousChartDropped = droppedNow;
        chartPoints.add(new ChartPoint(++chartIndex, totalDuringSecond,
                Math.max(0, totalDuringSecond - limitedDuringSecond - droppedDuringSecond),
                limitedDuringSecond, droppedDuringSecond, clock.get()));
        if (chartPoints.size() > MAX_CHART_POINTS) {
            chartPoints.removeFirst();
        }
    }

    private AttackStatus classify(int rps) {
        if (rps >= config.getRequestRateThreshold()) {
            return AttackStatus.ATTACK_DETECTED;
        }
        if (rps >= Math.round(config.getRequestRateThreshold() * 0.7)) {
            return AttackStatus.SUSPICIOUS;
        }
        return AttackStatus.NORMAL;
    }

    private void updateDetectionText(AttackStatus status, int rps) {
        switch (status) {
            case NORMAL -> detectionReason.set("Traffic is within configured limits");
            case SUSPICIOUS -> detectionReason.set("Request rate is approaching the threshold (" + rps + " req/s)");
            case ATTACK_DETECTED -> detectionReason.set("High request rate detected (" + rps + " req/s)");
        }
    }

    private void logTransition(AttackStatus status, int rps) {
        switch (status) {
            case NORMAL -> appendLog("INFO", "SERVER", "NORMAL", "Traffic returned to normal");
            case SUSPICIOUS -> appendLog("WARNING", topClientIp(), "SUSPICIOUS", "High traffic: " + rps + " req/s");
            case ATTACK_DETECTED -> {
                detectedAt.set(clock.get());
                appendLog("ALERT", topClientIp(), "ATTACK_DETECTED", "Request-rate threshold exceeded");
                if (autoDefense.get() && protectionEnabled.get() && rateLimitActive.get()) {
                    appendLog("DEFENSE", topClientIp(), "RATE_LIMIT", "Rate limiting is active");
                }
            }
        }
    }

    private void updateUptime() {
        Duration duration = serverRunning.get() && startedAt != null
                ? Duration.between(startedAt, Instant.now()) : Duration.ZERO;
        long seconds = Math.max(0, duration.toSeconds());
        uptime.set("%02d:%02d:%02d".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60));
    }

    private void openServerPage() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(localServerUrl()));
                appendLog("INFO", "SERVER", "BROWSER_OPENED", "Opened " + localServerUrl());
            } else {
                appendLog("WARNING", "SERVER", "BROWSER_UNAVAILABLE", "Open " + getServerUrl() + " manually");
            }
        } catch (IOException | RuntimeException exception) {
            appendLog("WARNING", "SERVER", "BROWSER_FAILED", exception.getMessage());
        }
    }

    private String localServerUrl() {
        return "http://127.0.0.1:" + serverPort.get() + "/";
    }

    private String topClientIp() {
        return clients.isEmpty() ? "UNKNOWN" : clients.getFirst().ipAddress();
    }

    private static String findPrivateIpv4() {
        try {
            return NetworkInterface.networkInterfaces()
                    .filter(network -> {
                        try {
                            return network.isUp() && !network.isLoopback() && !network.isVirtual();
                        } catch (SocketException exception) {
                            return false;
                        }
                    })
                    .flatMap(NetworkInterface::inetAddresses)
                    .filter(address -> address instanceof Inet4Address)
                    .map(address -> address.getHostAddress())
                    .filter(DashboardController::isPrivateAddress)
                    .findFirst()
                    .orElse("127.0.0.1");
        } catch (SocketException exception) {
            return "127.0.0.1";
        }
    }

    private static boolean isPrivateAddress(String address) {
        return address.startsWith("10.") || address.startsWith("192.168.")
                || address.matches("172\\.(1[6-9]|2\\d|3[01])\\..*");
    }

    public ServerConfig getConfig() { return config; }
    public String getServerUrl() { return "http://" + serverIp.get() + ":" + serverPort.get() + "/"; }
    public ReadOnlyBooleanProperty serverRunningProperty() { return serverRunning; }
    public ReadOnlyBooleanProperty protectionEnabledProperty() { return protectionEnabled; }
    public ReadOnlyBooleanProperty autoDefenseProperty() { return autoDefense; }
    public ReadOnlyBooleanProperty rateLimitActiveProperty() { return rateLimitActive; }
    public ReadOnlyBooleanProperty connectionLimitActiveProperty() { return connectionLimitActive; }
    public ReadOnlyBooleanProperty blockingActiveProperty() { return blockingActive; }
    public ReadOnlyObjectProperty<AttackStatus> attackStatusProperty() { return attackStatus; }
    public ReadOnlyLongProperty totalRequestsProperty() { return totalRequests; }
    public ReadOnlyLongProperty successfulRequestsProperty() { return successfulRequests; }
    public ReadOnlyLongProperty failedRequestsProperty() { return failedRequests; }
    public ReadOnlyLongProperty limitedRequestsProperty() { return limitedRequests; }
    public ReadOnlyLongProperty droppedRequestsProperty() { return droppedRequests; }
    public ReadOnlyIntegerProperty serverPortProperty() { return serverPort; }
    public ReadOnlyIntegerProperty requestsPerSecondProperty() { return requestsPerSecond; }
    public ReadOnlyIntegerProperty activeConnectionsProperty() { return activeConnections; }
    public ReadOnlyDoubleProperty averageResponseTimeProperty() { return averageResponseTime; }
    public ReadOnlyStringProperty clockProperty() { return clock; }
    public ReadOnlyStringProperty uptimeProperty() { return uptime; }
    public ReadOnlyStringProperty serverIpProperty() { return serverIp; }
    public ReadOnlyStringProperty detectionReasonProperty() { return detectionReason; }
    public ReadOnlyStringProperty detectedAtProperty() { return detectedAt; }
    public ObservableList<ClientInfo> getClients() { return clients; }
    public ObservableList<String> getLogs() { return logs; }
    public ObservableList<ChartPoint> getChartPoints() { return chartPoints; }

    public ProtectionStatus protectionSnapshot() {
        return new ProtectionStatus(protectionEnabled.get(), autoDefense.get(),
                rateLimitActive.get(), connectionLimitActive.get(), blockingActive.get());
    }

    @Override
    public void close() {
        timeline.stop();
        webServer.close();
    }

    private final class ServerObserver implements WebServer.Observer {
        @Override
        public void onConnectionOpened(String ip) {
            trafficMonitor.connectionOpened(ip);
        }

        @Override
        public void onConnectionClosed(String ip) {
            trafficMonitor.connectionClosed(ip);
        }

        @Override
        public void onRequestCompleted(String ip, String method, String target,
                                       int statusCode, long responseNanos) {
            trafficMonitor.requestCompleted(ip, statusCode, responseNanos);
            long now = System.nanoTime();
            long previous = lastRequestLogNanos.get();
            if (now - previous >= 1_000_000_000L && lastRequestLogNanos.compareAndSet(previous, now)) {
                appendLog(statusCode < 400 ? "INFO" : "WARNING", ip, "HTTP_REQUEST",
                        method + " " + target + " - " + statusCode + " ("
                                + Math.round(responseNanos / 1_000_000.0) + " ms)");
            }
        }

        @Override
        public void onRequestRejected(String ip, RequestGuard.Decision decision) {
            if (decision == RequestGuard.Decision.LIMITED) trafficMonitor.requestLimited();
            else trafficMonitor.requestDropped();
            long now = System.nanoTime();
            long previous = lastProtectionLogNanos.get();
            if (now - previous >= 1_000_000_000L && lastProtectionLogNanos.compareAndSet(previous, now)) {
                String event = switch (decision) {
                    case LIMITED -> "RATE_LIMIT";
                    case BLOCKED -> "TEMP_BLOCK";
                    case CONNECTION_LIMITED -> "CONNECTION_LIMIT";
                    case ALLOW -> "ALLOW";
                };
                appendLog("DEFENSE", ip, event, "Rejected excess lab traffic");
            }
        }

        @Override
        public void onServerError(Exception exception) {
            appendLog("ERROR", "SERVER", "SERVER_ERROR", exception.getMessage());
        }
    }

    public record ChartPoint(long index, int requestsPerSecond, int allowedRequestsPerSecond,
                             int limitedRequestsPerSecond, int droppedRequestsPerSecond,
                             String timeLabel) {
    }
}
