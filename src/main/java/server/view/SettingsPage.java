package server.view;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.controlsfx.control.ToggleSwitch;
import server.config.ServerConfig;
import server.controller.DashboardController;

/** System configuration page opened from the Settings navigation item. */
public final class SettingsPage extends VBox {
    private final DashboardController controller;

    private final Spinner<Integer> port;
    private final Spinner<Integer> threadPool;
    private final Spinner<Integer> requestThreshold;
    private final Spinner<Integer> clientThreshold;
    private final Spinner<Integer> maxConnections;
    private final Spinner<Integer> responseThreshold;
    private final Spinner<Integer> multipleConnections = spinner(1, 10_000, 5);
    private final Spinner<Integer> slidingWindow = spinner(1, 300, 5);
    private final Spinner<Integer> rateLimit;
    private final Spinner<Integer> protectionConnectionLimit;
    private final Spinner<Integer> queueSize = spinner(1, 10_000, 50);
    private final Spinner<Integer> blockDuration;
    private final Spinner<Integer> temporaryBlockDuration;
    private final Spinner<Integer> headerSize = spinner(512, 1_048_576, 8_192);
    private final Spinner<Integer> requestSize = spinner(1_024, 100_000_000, 1_048_576);
    private final Spinner<Integer> timeout = spinner(1, 3_600, 60);
    private final Spinner<Integer> maxLogSize = spinner(1, 10_000, 10);

    private final ToggleSwitch protectionToggle;
    private final ToggleSwitch autoDefenseToggle;
    private final ToggleSwitch keepAliveToggle = toggle(true);
    private final ToggleSwitch autoRotateToggle = toggle(true);
    private final ComboBox<String> httpVersion = combo("HTTP/1.1", "HTTP/1.0");
    private final ComboBox<String> logLevel = combo("INFO", "WARNING", "ERROR", "DEBUG");
    private final TextField logFile = new TextField("logs/security.log");
    private final Label feedback = new Label("Settings are ready");

    public SettingsPage(DashboardController controller) {
        this.controller = controller;
        ServerConfig config = controller.getConfig();
        port = spinner(1, 65_535, config.getServerPort());
        threadPool = spinner(1, 256, Math.max(8, Runtime.getRuntime().availableProcessors() * 4));
        requestThreshold = spinner(1, 100_000, config.getRequestRateThreshold());
        clientThreshold = spinner(1, 100_000, config.getPerClientRateThreshold());
        maxConnections = spinner(1, 100_000, config.getMaxActiveConnections());
        responseThreshold = spinner(1, 120_000, (int) config.getResponseTimeThresholdMillis());
        rateLimit = spinner(1, 100_000, config.getRateLimit());
        protectionConnectionLimit = spinner(1, 100_000, config.getMaxActiveConnections());
        blockDuration = spinner(1, 86_400, (int) config.getBlockDuration().toSeconds());
        temporaryBlockDuration = spinner(1, 86_400, (int) config.getBlockDuration().toSeconds());
        maxConnections.getEditor().textProperty()
                .bindBidirectional(protectionConnectionLimit.getEditor().textProperty());
        blockDuration.getEditor().textProperty()
                .bindBidirectional(temporaryBlockDuration.getEditor().textProperty());
        protectionToggle = controllerToggle(controller.protectionEnabledProperty().get(),
                controller::setProtectionEnabled);
        autoDefenseToggle = controllerToggle(controller.autoDefenseProperty().get(),
                controller::setAutoDefense);

        getStyleClass().add("settings-page");
        setSpacing(9);
        setPadding(new Insets(12));

        GridPane cards = createCards();
        ScrollPane scroll = new ScrollPane(cards);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("settings-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(createHero(), scroll, createFooter());
    }

    private Node createHero() {
        Node icon = UiIcons.icon("fas-cog", "settings-hero-icon");
        Label title = new Label("Settings");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("System configuration and security rules");
        subtitle.getStyleClass().add("settings-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button save = actionButton("Save Settings", "fas-save", "settings-save-button");
        save.setOnAction(event -> saveSettings());
        Button reset = actionButton("Reset to Default", "fas-sync-alt", "settings-outline-button");
        reset.setOnAction(event -> resetDefaults());
        HBox hero = new HBox(15, icon, new VBox(2, title, subtitle), spacer, save, reset);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(12, 16, 12, 16));
        hero.getStyleClass().addAll("panel", "settings-hero");
        return hero;
    }

    private GridPane createCards() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        for (int index = 0; index < 3; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3.0);
            column.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < 2; index++) {
            RowConstraints row = new RowConstraints();
            row.setPercentHeight(50);
            row.setVgrow(Priority.ALWAYS);
            grid.getRowConstraints().add(row);
        }
        grid.add(createServerConfiguration(), 0, 0);
        grid.add(createDetectionThresholds(), 1, 0);
        grid.add(createProtectionSettings(), 2, 0);
        grid.add(createNetworkSettings(), 0, 1);
        grid.add(createLoggingSettings(), 1, 1);
        grid.add(createQuickActions(), 2, 1);
        grid.getChildren().forEach(node -> {
            GridPane.setHgrow(node, Priority.ALWAYS);
            GridPane.setVgrow(node, Priority.ALWAYS);
        });
        return grid;
    }

    private Node createServerConfiguration() {
        TextField ip = new TextField();
        ip.textProperty().bind(controller.serverIpProperty());
        ip.setEditable(false);
        ip.getStyleClass().add("settings-field");
        Button copy = UiIcons.graphic(new Button(), "far-copy", "settings-field-icon");
        copy.getStyleClass().add("settings-copy-button");
        copy.setOnAction(event -> {
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(controller.serverIpProperty().get());
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            setFeedback("Server IP copied", false);
        });
        HBox ipBox = new HBox(0, ip, copy);
        HBox.setHgrow(ip, Priority.ALWAYS);

        GridPane form = form();
        addRow(form, 0, "Server IP", ipBox, null);
        addRow(form, 1, "Port", port, null);
        addRow(form, 2, "Thread Pool Size", threadPool, null);

        Label state = new Label();
        state.textProperty().bind(Bindings.when(controller.serverRunningProperty())
                .then("●  RUNNING").otherwise("●  STOPPED"));
        state.getStyleClass().add("settings-running");
        Label uptime = new Label();
        uptime.textProperty().bind(Bindings.concat(controller.uptimeProperty(), " (since start)"));
        uptime.getStyleClass().add("settings-value");
        addRow(form, 3, "Server Status", state, null);
        addRow(form, 4, "Uptime", uptime, null);

        Button test = actionButton("Test Connection", "fas-link", "settings-outline-button");
        test.disableProperty().bind(controller.serverRunningProperty().not());
        test.setOnAction(event -> {
            test.setDisable(true);
            controller.testConnection().thenAccept(ok -> Platform.runLater(() -> {
                test.setDisable(false);
                setFeedback(ok ? "Connection test succeeded (HTTP 200)" : "Connection test failed", !ok);
            }));
        });
        HBox actions = new HBox(test);
        actions.setAlignment(Pos.CENTER_RIGHT);
        VBox content = new VBox(10, form, divider(), actions);
        return card("fas-server", "Server Configuration", "Web server and network settings", content);
    }

    private Node createDetectionThresholds() {
        GridPane form = form();
        addRow(form, 0, "Request Rate Threshold", requestThreshold, "req/sec");
        addRow(form, 1, "Per-Client Rate Threshold", clientThreshold, "req/sec");
        addRow(form, 2, "Max Active Connections", maxConnections, "connections");
        addRow(form, 3, "Response Time Threshold", responseThreshold, "ms");
        addRow(form, 4, "Multiple Connection Threshold", multipleConnections, "connections");
        addRow(form, 5, "Time Window (Sliding)", slidingWindow, "seconds");
        return card("fas-shield-alt", "Detection Thresholds", "Configure detection rules", form);
    }

    private Node createProtectionSettings() {
        GridPane form = form();
        addRow(form, 0, "Enable Protection", protectionToggle, null);
        addRow(form, 1, "Auto Defense", autoDefenseToggle, null);
        Label rateHeading = subsection("Rate Limiting");
        addRow(form, 3, "Per-Client Limit", rateLimit, "req/sec");
        addRow(form, 4, "Block Duration", blockDuration, "seconds");
        Label connectionHeading = subsection("Connection Limiting");
        addRow(form, 6, "Max Connections", protectionConnectionLimit, null);
        addRow(form, 7, "Queue Size", queueSize, null);
        Label blockingHeading = subsection("Temporary Blocking");
        addRow(form, 9, "Block Duration", temporaryBlockDuration, "seconds");
        form.add(rateHeading, 0, 2, 3, 1);
        form.add(connectionHeading, 0, 5, 3, 1);
        form.add(blockingHeading, 0, 8, 3, 1);
        return card("fas-shield-virus", "Protection Settings", "Anti-DoS configuration", form);
    }

    private Node createNetworkSettings() {
        GridPane form = form();
        addRow(form, 0, "HTTP Version", httpVersion, null);
        addRow(form, 1, "Max Header Size", headerSize, "bytes");
        addRow(form, 2, "Max Request Size", requestSize, "bytes");
        addRow(form, 3, "Keep Alive", keepAliveToggle, null);
        addRow(form, 4, "Timeout", timeout, "seconds");
        return card("fas-globe", "Network & HTTP Settings", "HTTP server configuration", form);
    }

    private Node createLoggingSettings() {
        logFile.getStyleClass().add("settings-field");
        Button browse = new Button("...");
        browse.getStyleClass().add("settings-copy-button");
        browse.setOnAction(event -> chooseLogFile());
        HBox file = new HBox(4, logFile, browse);
        HBox.setHgrow(logFile, Priority.ALWAYS);

        GridPane form = form();
        addRow(form, 0, "Log File", file, null);
        addRow(form, 1, "Log Level", logLevel, null);
        addRow(form, 2, "Max Log Size", maxLogSize, "MB");
        addRow(form, 3, "Auto Rotate", autoRotateToggle, null);

        VBox eventTypes = new VBox(3, checked("All Events"), checked("Security Events"),
                checked("Traffic Events"), checked("Protection Events"));
        addRow(form, 4, "Log Events", eventTypes, null);
        return card("fas-file-alt", "Logging Settings", "Security log configuration", form);
    }

    private Node createQuickActions() {
        Button clear = fullAction("Clear Statistics", "fas-trash-alt");
        clear.setOnAction(event -> {
            controller.resetStatistics();
            setFeedback("Traffic statistics cleared", false);
        });
        Button export = fullAction("Export Logs", "fas-download");
        export.setOnAction(event -> exportLogs());
        Button importButton = fullAction("Import Settings", "fas-upload");
        importButton.setOnAction(event -> importSettings());
        Button view = fullAction("View Log File", "fas-file-alt");
        view.setOnAction(event -> viewLogFile());
        VBox buttons = new VBox(10, clear, export, importButton, view);
        return card("fas-bolt", "Quick Actions", "Maintenance and utility", buttons);
    }

    private Node createFooter() {
        Label ready = UiIcons.graphic(new Label(), "fas-circle", "settings-ready-icon");
        ready.textProperty().bind(Bindings.when(controller.serverRunningProperty())
                .then("System Ready").otherwise("Server Stopped"));
        Label rps = new Label();
        rps.textProperty().bind(Bindings.concat("Requests/sec: ", controller.requestsPerSecondProperty()));
        Label total = new Label();
        total.textProperty().bind(Bindings.concat("Total Requests: ", controller.totalRequestsProperty()));
        Label connections = new Label();
        connections.textProperty().bind(Bindings.concat("Active Connections: ", controller.activeConnectionsProperty()));
        feedback.getStyleClass().add("settings-feedback");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label version = new Label("DoS/DDoS Protection Server   |   v1.0.0   |   Java 23");
        HBox footer = new HBox(14, ready, separator(), rps, separator(), total, separator(), connections,
                spacer, feedback, separator(), version);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 14, 8, 14));
        footer.getStyleClass().add("settings-footer");
        return footer;
    }

    private VBox card(String icon, String title, String subtitle, Node content) {
        Node symbol = UiIcons.icon(icon, "settings-card-icon");
        Label heading = new Label(title);
        heading.getStyleClass().add("settings-card-title");
        Label detail = new Label(subtitle);
        detail.getStyleClass().add("settings-card-subtitle");
        HBox header = new HBox(12, symbol, new VBox(1, heading, detail));
        header.setAlignment(Pos.CENTER_LEFT);
        VBox panel = new VBox(12, header, content);
        panel.setPadding(new Insets(13, 14, 13, 14));
        panel.getStyleClass().addAll("panel", "settings-card");
        return panel;
    }

    private GridPane form() {
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setPercentWidth(37);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setPercentWidth(45);
        fields.setHgrow(Priority.ALWAYS);
        ColumnConstraints units = new ColumnConstraints();
        units.setPercentWidth(18);
        form.getColumnConstraints().addAll(labels, fields, units);
        return form;
    }

    private void addRow(GridPane form, int row, String name, Node control, String unit) {
        Label label = new Label(name);
        label.getStyleClass().add("settings-row-label");
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
        form.add(label, 0, row);
        form.add(control, 1, row);
        if (unit != null) {
            Label unitLabel = new Label(unit);
            unitLabel.getStyleClass().add("settings-unit");
            form.add(unitLabel, 2, row);
        }
        GridPane.setHgrow(control, Priority.ALWAYS);
        GridPane.setHalignment(control, control instanceof ToggleSwitch ? javafx.geometry.HPos.RIGHT : javafx.geometry.HPos.LEFT);
    }

    private void saveSettings() {
        try {
            int configuredPort = spinnerValue(port);
            ServerConfig config = controller.getConfig();
            config.setServerPort(configuredPort);
            config.setRequestRateThreshold(spinnerValue(requestThreshold));
            config.setPerClientRateThreshold(spinnerValue(clientThreshold));
            config.setMaxActiveConnections(spinnerValue(protectionConnectionLimit));
            config.setResponseTimeThresholdMillis(spinnerValue(responseThreshold));
            config.setRateLimit(spinnerValue(rateLimit));
            config.setBlockDuration(Duration.ofSeconds(spinnerValue(temporaryBlockDuration)));
            config.setAutoDefense(autoDefenseToggle.isSelected());
            controller.setProtectionEnabled(protectionToggle.isSelected());
            controller.setAutoDefense(autoDefenseToggle.isSelected());
            controller.appendLog("INFO", "SERVER", "SETTINGS_SAVED",
                    "Detection and protection configuration updated");
            String suffix = controller.serverRunningProperty().get()
                    && configuredPort != controller.serverPortProperty().get()
                    ? "; new port applies after restart" : "";
            setFeedback("Settings saved" + suffix, false);
        } catch (IllegalArgumentException exception) {
            controller.appendLog("ERROR", "SERVER", "INVALID_SETTINGS", exception.getMessage());
            setFeedback("Invalid settings: " + exception.getMessage(), true);
        }
    }

    private void resetDefaults() {
        ServerConfig defaults = ServerConfig.defaults();
        setSpinner(port, defaults.getServerPort());
        setSpinner(threadPool, Math.max(8, Runtime.getRuntime().availableProcessors() * 4));
        setSpinner(requestThreshold, defaults.getRequestRateThreshold());
        setSpinner(clientThreshold, defaults.getPerClientRateThreshold());
        setSpinner(maxConnections, defaults.getMaxActiveConnections());
        setSpinner(protectionConnectionLimit, defaults.getMaxActiveConnections());
        setSpinner(responseThreshold, (int) defaults.getResponseTimeThresholdMillis());
        setSpinner(multipleConnections, 5);
        setSpinner(slidingWindow, 5);
        setSpinner(rateLimit, defaults.getRateLimit());
        setSpinner(queueSize, 50);
        setSpinner(blockDuration, (int) defaults.getBlockDuration().toSeconds());
        setSpinner(temporaryBlockDuration, (int) defaults.getBlockDuration().toSeconds());
        setSpinner(headerSize, 8_192);
        setSpinner(requestSize, 1_048_576);
        setSpinner(timeout, 60);
        setSpinner(maxLogSize, 10);
        protectionToggle.setSelected(true);
        autoDefenseToggle.setSelected(true);
        keepAliveToggle.setSelected(true);
        autoRotateToggle.setSelected(true);
        httpVersion.setValue("HTTP/1.1");
        logLevel.setValue("INFO");
        logFile.setText("logs/security.log");
        saveSettings();
        setFeedback("Default settings restored", false);
    }

    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Security Logs");
        chooser.setInitialFileName("security.log");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log files", "*.log", "*.txt"));
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        try {
            Files.write(file.toPath(), controller.getLogs(), StandardCharsets.UTF_8);
            setFeedback("Logs exported to " + file.getName(), false);
        } catch (IOException exception) {
            setFeedback("Cannot export logs: " + exception.getMessage(), true);
        }
    }

    private void chooseLogFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Security Log File");
        chooser.setInitialFileName("security.log");
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file != null) logFile.setText(file.getPath());
    }

    private void viewLogFile() {
        try {
            Path path = Path.of(logFile.getText().trim()).toAbsolutePath().normalize();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Files.write(path, controller.getLogs(), StandardCharsets.UTF_8);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile());
            setFeedback("Log file opened: " + path.getFileName(), false);
        } catch (IOException | RuntimeException exception) {
            setFeedback("Cannot open log file: " + exception.getMessage(), true);
        }
    }

    private void importSettings() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Settings");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Properties files", "*.properties"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            values.load(reader);
            applyProperty(values, "server.port", port);
            applyProperty(values, "server.threads", threadPool);
            applyProperty(values, "detection.requestRate", requestThreshold);
            applyProperty(values, "detection.perClientRate", clientThreshold);
            applyProperty(values, "detection.maxConnections", maxConnections);
            applyProperty(values, "detection.responseTime", responseThreshold);
            applyProperty(values, "protection.rateLimit", rateLimit);
            applyProperty(values, "protection.connectionLimit", protectionConnectionLimit);
            applyProperty(values, "protection.blockDuration", blockDuration);
            applyProperty(values, "protection.temporaryBlockDuration", temporaryBlockDuration);
            saveSettings();
            setFeedback("Settings imported from " + file.getName(), false);
        } catch (IOException | IllegalArgumentException exception) {
            setFeedback("Cannot import settings: " + exception.getMessage(), true);
        }
    }

    private void applyProperty(Properties values, String name, Spinner<Integer> target) {
        String value = values.getProperty(name);
        if (value != null) setSpinner(target, Integer.parseInt(value.trim()));
    }

    private void setFeedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().removeAll("error");
        if (error) feedback.getStyleClass().add("error");
    }

    private Button fullAction(String text, String icon) {
        Button button = actionButton(text, icon, "settings-quick-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private Button actionButton(String text, String icon, String style) {
        Button button = UiIcons.graphic(new Button(text), icon, "button-icon");
        button.getStyleClass().add(style);
        return button;
    }

    private ToggleSwitch controllerToggle(boolean selected, java.util.function.Consumer<Boolean> setter) {
        ToggleSwitch control = toggle(selected);
        control.selectedProperty().addListener((observable, oldValue, value) -> setter.accept(value));
        return control;
    }

    private static ToggleSwitch toggle(boolean selected) {
        ToggleSwitch control = new ToggleSwitch();
        control.setSelected(selected);
        control.getStyleClass().add("protection-toggle-switch");
        return control;
    }

    private static Spinner<Integer> spinner(int min, int max, int value) {
        Spinner<Integer> control = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, value));
        control.setEditable(true);
        control.getStyleClass().add("settings-spinner");
        control.setMaxWidth(Double.MAX_VALUE);
        return control;
    }

    private static int spinnerValue(Spinner<Integer> spinner) {
        String text = spinner.getEditor().getText().trim().replace(",", "");
        int value = Integer.parseInt(text);
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        if (value < factory.getMin() || value > factory.getMax()) {
            throw new IllegalArgumentException("Value " + value + " is outside the allowed range");
        }
        factory.setValue(value);
        return value;
    }

    private static void setSpinner(Spinner<Integer> spinner, int value) {
        spinner.getValueFactory().setValue(value);
        spinner.getEditor().setText(String.valueOf(value));
    }

    private static ComboBox<String> combo(String... values) {
        ComboBox<String> control = new ComboBox<>();
        control.getItems().addAll(values);
        control.getSelectionModel().selectFirst();
        control.getStyleClass().add("settings-combo");
        control.setMaxWidth(Double.MAX_VALUE);
        return control;
    }

    private CheckBox checked(String text) {
        CheckBox box = new CheckBox(text);
        box.setSelected(true);
        box.getStyleClass().add("settings-check");
        return box;
    }

    private Label subsection(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-subsection");
        return label;
    }

    private Region divider() {
        Region line = new Region();
        line.getStyleClass().add("settings-divider");
        return line;
    }

    private Label separator() {
        Label label = new Label("|");
        label.getStyleClass().add("settings-footer-separator");
        return label;
    }
}
