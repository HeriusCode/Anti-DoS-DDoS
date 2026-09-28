package server.view;

import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import server.controller.DashboardController;
import server.detection.AttackStatus;
import server.model.ClientInfo;

/** Anti-DoS controls, protection telemetry and configuration. */
public final class ProtectionPage extends VBox {
    private final DashboardController controller;
    private final ObservableList<ProtectionEventRow> events = FXCollections.observableArrayList();
    private final ObservableList<RuleRow> ruleRows = FXCollections.observableArrayList();
    private final XYChart.Series<Number, Number> totalSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> allowedSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> limitedSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> blockedSeries = new XYChart.Series<>();
    private final TextField rateLimit = new TextField();
    private final TextField connectionLimit = new TextField();
    private final TextField blockDuration = new TextField();

    public ProtectionPage(DashboardController controller) {
        this.controller = controller;
        getStyleClass().add("protection-page");
        setMinWidth(0);
        setSpacing(10);
        setPadding(new Insets(10, 12, 10, 12));

        HBox overview = new HBox(10, createRateCard(), createConnectionCard(),
                createBlockingCard(), createOverviewCard());
        overview.setPrefHeight(170);
        overview.setMinHeight(150);
        overview.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox chartRow = new HBox(10, createChart(), createEvents());
        chartRow.setPrefHeight(275);
        chartRow.setMinHeight(230);
        chartRow.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        VBox actionsAndSettings = new VBox(10, createQuickActions(), createSettings());
        actionsAndSettings.getChildren().forEach(node -> VBox.setVgrow(node, Priority.ALWAYS));
        HBox bottom = new HBox(10, createClients(), createRules(), actionsAndSettings);
        bottom.setPrefHeight(255);
        bottom.setMinHeight(215);
        bottom.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox.setVgrow(bottom, Priority.ALWAYS);

        getChildren().addAll(createHero(), overview, chartRow, bottom);
        refreshChart();
        refreshEvents();
        refreshRules();
        controller.getChartPoints().addListener(
                (ListChangeListener<DashboardController.ChartPoint>) change -> {
                    refreshChart();
                    refreshRules();
                });
        controller.getLogs().addListener((ListChangeListener<String>) change -> refreshEvents());
    }

    private Node createHero() {
        Node icon = UiIcons.icon("fas-shield-alt", "protection-hero-icon");
        Label title = new Label("ANTI-DOS / PROTECTION");
        title.getStyleClass().add("protection-hero-title");
        Label subtitle = new Label("Rate limiting, connection limiting and temporary blocking");
        subtitle.getStyleClass().add("protection-hero-subtitle");
        VBox heading = new VBox(3, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(7,
                protectionButton("Disable", false), protectionButton("Enable", true));
        VBox controlBox = new VBox(4, heroCaption("PROTECTION CONTROL"), controls);
        controlBox.getStyleClass().add("protection-hero-section");

        HBox hero = new HBox(18, icon, heading, spacer,
                heroStatus("fas-shield-alt", "PROTECTION STATUS",
                        Bindings.when(controller.protectionEnabledProperty()).then("ACTIVE").otherwise("DISABLED")),
                heroAutoDefense(),
                heroStatus("fas-server", "SERVER",
                        Bindings.concat(controller.serverIpProperty(), ":", controller.serverPortProperty())),
                controlBox);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(10, 16, 10, 16));
        hero.getStyleClass().addAll("panel", "protection-hero");
        return hero;
    }

    private Node heroStatus(String icon, String caption, ObservableValue<String> value) {
        Node symbol = UiIcons.icon(icon, "protection-hero-status-icon");
        Label state = new Label();
        state.textProperty().bind(value);
        state.getStyleClass().add("protection-hero-value");
        HBox box = new HBox(9, symbol, new VBox(2, heroCaption(caption), state));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("protection-hero-section");
        return box;
    }

    private Node heroAutoDefense() {
        ToggleSwitch toggle = toggle(controller.autoDefenseProperty(), controller::setAutoDefense);
        toggle.setTooltip(new Tooltip("Automatically block a client after repeated rate-limit violations "
                + "when Temporary Blocking is enabled."));
        HBox value = new HBox(6, new Label("ON"), toggle);
        value.setAlignment(Pos.CENTER_LEFT);
        value.getStyleClass().add("protection-auto-value");
        VBox box = new VBox(3, heroCaption("AUTO DEFENSE"), value);
        box.getStyleClass().add("protection-hero-section");
        return box;
    }

    private Label heroCaption(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("protection-hero-caption");
        return label;
    }

    private Button protectionButton(String text, boolean enable) {
        Button button = UiIcons.graphic(new Button(text), "fas-shield-alt", "button-icon");
        button.getStyleClass().addAll("small-button", "action-button", enable ? "success-button" : "danger-button");
        button.setOnAction(event -> controller.setProtectionEnabled(enable));
        return button;
    }

    private Node createRateCard() {
        Label limit = new Label();
        limit.textProperty().bind(Bindings.createStringBinding(
                () -> controller.getConfig().getRateLimit() + " req/sec", controller.clockProperty()));
        VBox clients = new VBox(4);
        Runnable refresh = () -> {
            clients.getChildren().clear();
            controller.getClients().stream().limit(3).forEach(client -> clients.getChildren().add(
                    valueLine(client.ipAddress(), client.requestsPerSecond() + "/" + controller.getConfig().getRateLimit())));
            if (clients.getChildren().isEmpty()) clients.getChildren().add(emptyLabel("No active limits"));
        };
        refresh.run();
        controller.getClients().addListener((ListChangeListener<ClientInfo>) change -> refresh.run());
        return protectionCard("fas-tachometer-alt", "RATE LIMITING", controller.rateLimitActiveProperty(),
                valueLine("Per-client limit", limit), new Label("Current limits:"), clients);
    }

    private Node createConnectionCard() {
        Label active = new Label();
        active.textProperty().bind(controller.activeConnectionsProperty().asString());
        Label maximum = new Label();
        maximum.textProperty().bind(Bindings.createStringBinding(
                () -> String.valueOf(controller.getConfig().getMaxActiveConnections()),
                controller.clockProperty()));
        Label rejected = new Label();
        rejected.textProperty().bind(controller.rejectedConnectionsProperty().asString());
        return protectionCard("fas-link", "CONNECTION LIMITING", controller.connectionLimitActiveProperty(),
                valueLine("Max connections", maximum),
                valueLine("Current connections", active), valueLine("Rejected connections", rejected));
    }

    private Node createBlockingCard() {
        VBox blocked = new VBox(4);
        Label blockedCount = new Label("0");
        Runnable refresh = () -> {
            blocked.getChildren().clear();
            long count = controller.getClients().stream().filter(ClientInfo::blocked).count();
            blockedCount.setText(String.valueOf(count));
            controller.getClients().stream().filter(ClientInfo::blocked).limit(3).forEach(client ->
                    blocked.getChildren().add(valueLine(client.ipAddress(), client.blockRemainingSeconds() + "s")));
            if (blocked.getChildren().isEmpty()) blocked.getChildren().add(emptyLabel("No blocked clients"));
        };
        refresh.run();
        controller.getClients().addListener((ListChangeListener<ClientInfo>) change -> refresh.run());
        return protectionCard("fas-ban", "TEMPORARY BLOCKING", controller.blockingActiveProperty(),
                valueLine("Blocked clients", blockedCount), blocked);
    }

    private Node createOverviewCard() {
        Label enabled = new Label();
        enabled.textProperty().bind(Bindings.when(controller.protectionEnabledProperty())
                .then("ACTIVE").otherwise("DISABLED"));
        enabled.getStyleClass().add("overview-active");
        Label action = new Label();
        action.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!controller.protectionEnabledProperty().get()) return "None";
            StringBuilder enabledActions = new StringBuilder();
            if (controller.rateLimitActiveProperty().get()) enabledActions.append("Rate limit");
            if (controller.connectionLimitActiveProperty().get()) {
                if (!enabledActions.isEmpty()) enabledActions.append(" + ");
                enabledActions.append("Connection limit");
            }
            if (controller.blockingActiveProperty().get() && controller.autoDefenseProperty().get()) {
                if (!enabledActions.isEmpty()) enabledActions.append(" + ");
                enabledActions.append("Temp block");
            }
            return enabledActions.isEmpty() ? "Monitoring" : enabledActions.toString();
        }, controller.protectionEnabledProperty(), controller.rateLimitActiveProperty(),
                controller.connectionLimitActiveProperty(), controller.blockingActiveProperty(),
                controller.autoDefenseProperty()));
        VBox content = new VBox(7, valueLine("Protection status", enabled),
                valueLine("Last triggered", controller.detectedAtProperty()),
                valueLine("Reason", controller.detectionReasonProperty()), valueLine("Action", action));
        VBox panel = panel(7, sectionHeader("fas-shield-alt", "PROTECTION OVERVIEW"), content);
        panel.setPrefWidth(285);
        return panel;
    }

    private VBox protectionCard(String icon, String title, ObservableBooleanValue active, Node... content) {
        Label badge = new Label();
        badge.textProperty().bind(Bindings.when(active).then("●  ACTIVE").otherwise("○  OFF"));
        badge.getStyleClass().add("protection-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader(icon, title, spacer, badge);
        VBox panel = panel(7, header);
        panel.getChildren().addAll(content);
        panel.setPrefWidth(290);
        return panel;
    }

    private Node createChart() {
        NumberAxis x = new NumberAxis();
        x.setTickLabelsVisible(false);
        x.setMinorTickVisible(false);
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(true);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.getData().addAll(totalSeries, allowedSeries, limitedSeries, blockedSeries);
        chart.getStyleClass().add("protection-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox legend = new HBox(12, spacer, legend("Total Requests", "protection-total-line"),
                legend("Completed 2xx", "protection-allowed-line"), legend("Limited", "protection-limited-line"),
                legend("Blocked / Dropped", "protection-blocked-line"));
        VBox panel = panel(5, sectionHeader("fas-stopwatch", "TRAFFIC & PROTECTION STATUS"), legend, chart);
        panel.setPrefWidth(700);
        panel.setMinWidth(520);
        return panel;
    }

    private Node createEvents() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TableView<ProtectionEventRow> table = new TableView<>(events);
        table.setPlaceholder(new Label("No protection events"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(textColumn("Time", ProtectionEventRow::time),
                textColumn("Level", ProtectionEventRow::level), textColumn("Event", ProtectionEventRow::event),
                textColumn("Details", ProtectionEventRow::details));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = panel(6, sectionHeader("fas-shield-alt", "PROTECTION EVENTS", spacer, all), table);
        panel.setPrefWidth(485);
        panel.setMinWidth(380);
        return panel;
    }

    private Node createClients() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TableView<ClientInfo> table = new TableView<>(controller.getClients());
        table.setPlaceholder(new Label("No protected clients"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ClientInfo, String> ip = new TableColumn<>("IP Address");
        ip.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().ipAddress()));
        TableColumn<ClientInfo, Number> rate = new TableColumn<>("Requests/sec");
        rate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().requestsPerSecond()));
        TableColumn<ClientInfo, AttackStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> statusCell());
        TableColumn<ClientInfo, String> action = new TableColumn<>("Action");
        action.setCellValueFactory(cell -> new SimpleStringProperty(clientAction(cell.getValue())));
        table.getColumns().addAll(ip, rate, status, action);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = panel(6, sectionHeader("fas-users", "PROTECTED CLIENTS", spacer, all), table);
        panel.setPrefWidth(345);
        panel.setMinWidth(285);
        return panel;
    }

    private Node createRules() {
        TableView<RuleRow> table = new TableView<>(ruleRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(textColumnRule("Parameter", RuleRow::parameter),
                textColumnRule("Threshold", RuleRow::threshold), textColumnRule("Current", RuleRow::current),
                textColumnRule("Status", RuleRow::status));
        VBox.setVgrow(table, Priority.ALWAYS);
        Label attack = new Label();
        attack.textProperty().bind(Bindings.createStringBinding(
                () -> controller.attackStatusProperty().get().name().replace('_', ' '), controller.attackStatusProperty()));
        attack.getStyleClass().add("protection-attack-banner");
        VBox panel = panel(6, sectionHeader("fas-cog", "DETECTION RULES"), table, attack);
        panel.setPrefWidth(390);
        panel.setMinWidth(320);
        return panel;
    }

    private Node createQuickActions() {
        Button disable = quickAction("Disable Protection", "fas-shield-alt", "danger-button");
        disable.setOnAction(event -> controller.setProtectionEnabled(false));
        Button enable = quickAction("Enable Protection", "fas-shield-alt", "outline-button");
        enable.setOnAction(event -> controller.setProtectionEnabled(true));
        Button reset = quickAction("Reset Statistics", "fas-sync-alt", "success-button");
        reset.setOnAction(event -> controller.resetStatistics());
        Button logs = quickAction("View Logs", "fas-file-alt", "outline-button");
        logs.setOnAction(event -> controller.appendLog("INFO", "DASHBOARD", "VIEW_LOGS", "View Logs requested"));
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(disable, 0, 0);
        grid.add(enable, 1, 0);
        grid.add(reset, 0, 1);
        grid.add(logs, 1, 1);
        grid.getChildren().forEach(node -> GridPane.setHgrow(node, Priority.ALWAYS));
        VBox panel = panel(7, sectionHeader("fas-bolt", "QUICK ACTIONS"), grid);
        return panel;
    }

    private Node createSettings() {
        rateLimit.setText(String.valueOf(controller.getConfig().getRateLimit()));
        connectionLimit.setText(String.valueOf(controller.getConfig().getMaxActiveConnections()));
        blockDuration.setText(String.valueOf(controller.getConfig().getBlockDuration().toSeconds()));
        GridPane fields = new GridPane();
        fields.setHgap(7);
        fields.setVgap(5);
        addSetting(fields, 0, "Rate Limit (req/sec)", rateLimit);
        addSetting(fields, 1, "Connection Limit", connectionLimit);
        addSetting(fields, 2, "Block Duration (seconds)", blockDuration);
        Button save = UiIcons.graphic(new Button("Save Settings"), "fas-save", "button-icon");
        save.getStyleClass().addAll("action-button", "outline-button");
        save.setOnAction(event -> saveSettings());
        HBox row = new HBox(10, fields, save);
        HBox.setHgrow(fields, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox panel = panel(6, sectionHeader("fas-cog", "PROTECTION SETTINGS"), row);
        return panel;
    }

    private void addSetting(GridPane grid, int row, String title, TextField field) {
        Label label = new Label(title);
        label.getStyleClass().add("protection-setting-label");
        field.getStyleClass().add("protection-setting-field");
        grid.add(label, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private void saveSettings() {
        try {
            int rate = Integer.parseInt(rateLimit.getText().trim());
            int connections = Integer.parseInt(connectionLimit.getText().trim());
            long seconds = Long.parseLong(blockDuration.getText().trim());
            if (rate <= 0 || connections <= 0 || seconds <= 0) throw new IllegalArgumentException("Values must be positive");
            controller.getConfig().setRateLimit(rate);
            controller.getConfig().setMaxActiveConnections(connections);
            controller.getConfig().setBlockDuration(java.time.Duration.ofSeconds(seconds));
            controller.appendLog("INFO", "SERVER", "PROTECTION_SETTINGS", "Protection settings updated");
        } catch (IllegalArgumentException exception) {
            controller.appendLog("ERROR", "SERVER", "INVALID_SETTINGS", exception.getMessage());
        }
    }

    private void refreshChart() {
        totalSeries.getData().clear();
        allowedSeries.getData().clear();
        limitedSeries.getData().clear();
        blockedSeries.getData().clear();
        for (DashboardController.ChartPoint point : controller.getChartPoints()) {
            totalSeries.getData().add(new XYChart.Data<>(point.index(), point.requestsPerSecond()));
            allowedSeries.getData().add(new XYChart.Data<>(point.index(), point.successfulRequestsPerSecond()));
            limitedSeries.getData().add(new XYChart.Data<>(point.index(), point.limitedRequestsPerSecond()));
            blockedSeries.getData().add(new XYChart.Data<>(point.index(), point.droppedRequestsPerSecond()));
        }
    }

    private void refreshEvents() {
        events.clear();
        controller.getLogs().stream().map(ProtectionPage::parseLog)
                .filter(row -> row.level().equals("DEFENSE") || row.level().equals("ALERT")
                        || row.event().contains("PROTECTION") || row.event().contains("LIMIT")
                        || row.event().contains("BLOCK"))
                .forEach(events::add);
    }

    private void refreshRules() {
        ruleRows.setAll(
                ruleRow("Request rate (global)", controller.getConfig().getRequestRateThreshold(),
                        controller.requestsPerSecondProperty().get()),
                ruleRow("Per-client rate", controller.getConfig().getPerClientRateThreshold(), maxClientRate()),
                ruleRow("Active connections", controller.getConfig().getMaxActiveConnections(),
                        controller.activeConnectionsProperty().get()),
                ruleRow("Response time", controller.getConfig().getResponseTimeThresholdMillis(),
                        Math.round(controller.averageResponseTimeProperty().get())),
                new RuleRow("Limited requests", "20", String.valueOf(controller.limitedRequestsProperty().get()),
                        controller.limitedRequestsProperty().get() > 20 ? "Violation" : "OK"));
    }

    private HBox valueLine(String key, String value) {
        return valueLine(key, new Label(value));
    }

    private HBox valueLine(String key, ObservableValue<String> value) {
        Label label = new Label();
        label.textProperty().bind(value);
        return valueLine(key, label);
    }

    private HBox valueLine(String key, Label value) {
        Label keyLabel = new Label(key + ":");
        keyLabel.getStyleClass().add("protection-card-key");
        value.getStyleClass().add("protection-card-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox line = new HBox(6, keyLabel, spacer, value);
        line.setAlignment(Pos.CENTER_LEFT);
        return line;
    }

    private Label emptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("protection-empty");
        return label;
    }

    private ToggleSwitch toggle(ObservableBooleanValue value, Consumer<Boolean> setter) {
        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(value.get());
        toggle.getStyleClass().add("protection-toggle-switch");
        toggle.selectedProperty().addListener((observable, oldValue, enabled) -> setter.accept(enabled));
        value.addListener((observable, oldValue, enabled) -> toggle.setSelected(enabled));
        return toggle;
    }

    private Button quickAction(String text, String icon, String style) {
        Button button = UiIcons.graphic(new Button(text), icon, "button-icon");
        button.getStyleClass().addAll("action-button", style);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private HBox legend(String text, String style) {
        Region line = new Region();
        line.getStyleClass().addAll("legend-line", style);
        Label label = new Label(text);
        label.getStyleClass().add("legend-label");
        HBox item = new HBox(5, line, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private HBox sectionHeader(String icon, String title, Node... trailing) {
        Label label = UiIcons.graphic(new Label(title), icon, "section-icon");
        label.getStyleClass().add("protection-section-title");
        HBox header = new HBox(8, label);
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox panel(double spacing, Node... content) {
        VBox panel = new VBox(spacing, content);
        panel.setMinWidth(0);
        panel.setPadding(new Insets(9, 11, 9, 11));
        panel.getStyleClass().add("panel");
        return panel;
    }

    private TableCell<ClientInfo, AttackStatus> statusCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(AttackStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? null : status.name().replace('_', ' '));
                getStyleClass().removeAll("normal-cell", "warning-cell", "attack-cell");
                if (!empty && status != null) getStyleClass().add(switch (status) {
                    case NORMAL -> "normal-cell";
                    case SUSPICIOUS -> "warning-cell";
                    case ATTACK_DETECTED -> "attack-cell";
                });
            }
        };
    }

    private String clientAction(ClientInfo client) {
        if (client.blocked()) return "Blocked " + client.blockRemainingSeconds() + "s";
        if (client.status() != AttackStatus.NORMAL) return "Rate limited";
        return "-";
    }

    private RuleRow ruleRow(String parameter, long threshold, long current) {
        return new RuleRow(parameter, String.valueOf(threshold), String.valueOf(current),
                current > threshold ? "Violation" : "OK");
    }

    private int maxClientRate() {
        return controller.getClients().stream().mapToInt(ClientInfo::requestsPerSecond).max().orElse(0);
    }

    private TableColumn<ProtectionEventRow, String> textColumn(
            String title, java.util.function.Function<ProtectionEventRow, String> getter) {
        TableColumn<ProtectionEventRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private TableColumn<RuleRow, String> textColumnRule(
            String title, java.util.function.Function<RuleRow, String> getter) {
        TableColumn<RuleRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private static ProtectionEventRow parseLog(String entry) {
        String[] fields = entry.split("\\s*\\|\\s*", 5);
        return new ProtectionEventRow(field(fields, 0), field(fields, 1), field(fields, 3), field(fields, 4));
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "-";
    }

    private record ProtectionEventRow(String time, String level, String event, String details) {
    }

    private record RuleRow(String parameter, String threshold, String current, String status) {
    }
}
