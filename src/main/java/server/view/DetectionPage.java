package server.view;

import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableBooleanValue;
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
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.controlsfx.control.ToggleSwitch;
import server.controller.DashboardController;
import server.detection.AttackStatus;
import server.model.ClientInfo;

/** Rule-based detection and protection screen. */
public final class DetectionPage extends VBox {
    private final DashboardController controller;
    private final ObservableList<DetectionLogRow> detectionEvents = FXCollections.observableArrayList();
    private final XYChart.Series<Number, Number> requestSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> thresholdSeries = new XYChart.Series<>();
    private final TextField requestThreshold = new TextField();
    private final TextField clientThreshold = new TextField();
    private final TextField connectionThreshold = new TextField();
    private final TextField responseThreshold = new TextField();

    public DetectionPage(DashboardController controller) {
        this.controller = controller;
        getStyleClass().add("detection-page");
        setMinWidth(0);
        setSpacing(10);
        setPadding(new Insets(10, 12, 10, 12));

        HBox settingsRow = new HBox(10, createThresholds(), createRules(), createAttackCard());
        settingsRow.setPrefHeight(185);
        settingsRow.setMinHeight(165);
        settingsRow.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox analysisRow = new HBox(10, createTrafficAnalysis(), createEvents());
        analysisRow.setPrefHeight(275);
        analysisRow.setMinHeight(235);
        analysisRow.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));

        HBox bottomRow = new HBox(10, createClientMonitoring(), createProtectionActions(), createRecentLogs());
        bottomRow.setPrefHeight(235);
        bottomRow.setMinHeight(205);
        bottomRow.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox.setVgrow(bottomRow, Priority.ALWAYS);

        getChildren().addAll(createHero(), settingsRow, analysisRow, bottomRow);
        refreshChart();
        refreshDetectionEvents();
        controller.getChartPoints().addListener(
                (ListChangeListener<DashboardController.ChartPoint>) change -> refreshChart());
        controller.getLogs().addListener((ListChangeListener<String>) change -> refreshDetectionEvents());
    }

    private Node createHero() {
        Node icon = UiIcons.icon("fas-shield-alt", "detection-hero-icon");
        Label title = new Label("DETECTION & PROTECTION");
        title.getStyleClass().add("detection-hero-title");
        Label subtitle = new Label("Monitor traffic, detect attacks and activate protection");
        subtitle.getStyleClass().add("detection-hero-subtitle");
        VBox heading = new VBox(3, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hero = new HBox(18, icon, heading, spacer,
                heroStatus("fas-exclamation-triangle", "DETECTION STATUS",
                        Bindings.createStringBinding(() -> displayStatus(controller.attackStatusProperty().get()),
                                controller.attackStatusProperty()), "danger"),
                heroStatus("fas-shield-alt", "PROTECTION STATUS",
                        Bindings.when(controller.protectionEnabledProperty()).then("ACTIVE").otherwise("DISABLED"), "active"),
                heroToggle(),
                heroStatus("fas-server", "SERVER",
                        Bindings.concat(controller.serverIpProperty(), ":", controller.serverPortProperty()), "server"));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(10, 18, 10, 18));
        hero.getStyleClass().addAll("panel", "detection-hero");
        return hero;
    }

    private Node heroStatus(String icon, String title, javafx.beans.value.ObservableValue<String> value,
                            String style) {
        Node symbol = UiIcons.icon(icon, "detection-hero-status-icon");
        Label caption = new Label(title);
        caption.getStyleClass().add("detection-hero-caption");
        Label state = new Label();
        state.textProperty().bind(value);
        state.getStyleClass().addAll("detection-hero-value", style);
        HBox box = new HBox(9, symbol, new VBox(2, caption, state));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("detection-hero-status");
        return box;
    }

    private Node heroToggle() {
        Label caption = new Label("AUTO DEFENSE");
        caption.getStyleClass().add("detection-hero-caption");
        ToggleSwitch toggle = protectionSwitch(controller.autoDefenseProperty(), controller::setAutoDefense);
        HBox value = new HBox(6, new Label("ON"), toggle);
        value.setAlignment(Pos.CENTER_LEFT);
        value.getStyleClass().add("detection-auto-defense");
        VBox box = new VBox(3, caption, value);
        box.getStyleClass().add("detection-hero-status");
        return box;
    }

    private Node createThresholds() {
        requestThreshold.setText(String.valueOf(controller.getConfig().getRequestRateThreshold()));
        clientThreshold.setText(String.valueOf(controller.getConfig().getPerClientRateThreshold()));
        connectionThreshold.setText(String.valueOf(controller.getConfig().getMaxActiveConnections()));
        responseThreshold.setText(String.valueOf(controller.getConfig().getResponseTimeThresholdMillis()));
        setThresholdEditing(false);

        Button edit = UiIcons.graphic(new Button("Edit Settings"), "fas-cog", "button-icon");
        edit.getStyleClass().addAll("small-button", "action-button", "outline-button");
        edit.setOnAction(event -> {
            boolean editing = !requestThreshold.isEditable();
            if (editing) {
                setThresholdEditing(true);
                edit.setText("Save Settings");
            } else if (saveThresholds()) {
                setThresholdEditing(false);
                edit.setText("Edit Settings");
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-cog", "DETECTION THRESHOLDS", spacer, edit);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        addThreshold(grid, 0, 0, "Request Rate (req/sec)", requestThreshold);
        addThreshold(grid, 1, 0, "Per Client Rate (req/sec)", clientThreshold);
        addThreshold(grid, 0, 1, "Max Active Connections", connectionThreshold);
        addThreshold(grid, 1, 1, "Response Time (ms)", responseThreshold);
        VBox panel = panel(8, header, grid);
        panel.setPrefWidth(565);
        panel.setMinWidth(420);
        return panel;
    }

    private void addThreshold(GridPane grid, int column, int row, String title, TextField field) {
        Label label = new Label(title);
        label.getStyleClass().add("threshold-label");
        field.getStyleClass().add("threshold-field");
        field.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, label, field);
        grid.add(box, column, row);
        GridPane.setHgrow(box, Priority.ALWAYS);
    }

    private Node createRules() {
        VBox rules = new VBox(10,
                rule("Request rate threshold"), rule("Per-client rate threshold"),
                rule("Active connections threshold"), rule("Response time threshold"),
                rule("Multiple condition analysis"));
        VBox panel = panel(9, sectionHeader("fas-list-alt", "DETECTION RULES"), rules);
        panel.setPrefWidth(275);
        panel.setMinWidth(230);
        return panel;
    }

    private Node createAttackCard() {
        Label state = new Label();
        state.textProperty().bind(Bindings.createStringBinding(
                () -> displayStatus(controller.attackStatusProperty().get()), controller.attackStatusProperty()));
        state.getStyleClass().add("attack-state-banner");
        Label reason = valueLabel(controller.detectionReasonProperty());
        Label current = valueLabel(Bindings.concat(controller.requestsPerSecondProperty(), " req/sec"));
        Label threshold = new Label(controller.getConfig().getRequestRateThreshold() + " req/sec");
        threshold.getStyleClass().add("attack-detail-value");
        Label time = valueLabel(controller.detectedAtProperty());
        GridPane details = new GridPane();
        details.setHgap(12);
        details.setVgap(6);
        attackRow(details, 0, "Reason", reason);
        attackRow(details, 1, "Current", current);
        attackRow(details, 2, "Threshold", threshold);
        attackRow(details, 3, "Detected at", time);
        VBox panel = panel(7, sectionHeader("fas-exclamation-triangle", "ATTACK STATUS"), state, details);
        panel.getStyleClass().add("attack-card");
        panel.setPrefWidth(355);
        panel.setMinWidth(285);
        return panel;
    }

    private void attackRow(GridPane grid, int row, String key, Label value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("attack-detail-key");
        grid.add(keyLabel, 0, row);
        grid.add(new Label(":"), 1, row);
        grid.add(value, 2, row);
    }

    private Node createTrafficAnalysis() {
        NumberAxis x = new NumberAxis();
        x.setTickLabelsVisible(false);
        x.setMinorTickVisible(false);
        NumberAxis y = new NumberAxis();
        y.setAutoRanging(true);
        y.setForceZeroInRange(true);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.getData().addAll(requestSeries, thresholdSeries);
        chart.getStyleClass().add("detection-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox legend = new HBox(12, spacer, legend("Request Rate", "current-rps-line"),
                legend("Threshold", "detection-threshold-line"));
        VBox panel = panel(5, sectionHeader("fas-chart-line", "TRAFFIC ANALYSIS"), legend, chart);
        panel.setPrefWidth(660);
        panel.setMinWidth(480);
        return panel;
    }

    private Node createEvents() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-file-alt", "DETECTION EVENTS", spacer, all);
        TableView<DetectionLogRow> table = new TableView<>(detectionEvents);
        table.setPlaceholder(new Label("No detection events"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getColumns().addAll(textColumn("Time", DetectionLogRow::time),
                textColumn("Level", DetectionLogRow::level), textColumn("Client IP", DetectionLogRow::ip),
                textColumn("Event", DetectionLogRow::event), textColumn("Details", DetectionLogRow::details));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = panel(6, header, table);
        panel.setPrefWidth(520);
        panel.setMinWidth(400);
        return panel;
    }

    private Node createClientMonitoring() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        TableView<ClientInfo> table = new TableView<>(controller.getClients());
        table.setPlaceholder(new Label("No monitored clients"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ClientInfo, String> ip = new TableColumn<>("IP Address");
        ip.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().ipAddress()));
        TableColumn<ClientInfo, Number> rate = new TableColumn<>("Requests/sec");
        rate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().requestsPerSecond()));
        TableColumn<ClientInfo, Number> total = new TableColumn<>("Total Requests");
        total.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().totalRequests()));
        TableColumn<ClientInfo, AttackStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> statusCell());
        TableColumn<ClientInfo, ClientInfo> action = new TableColumn<>("Actions");
        action.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        action.setCellFactory(column -> blockCell());
        table.getColumns().addAll(ip, rate, total, status, action);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = panel(6, sectionHeader("fas-users", "CLIENT IP MONITORING", spacer, all), table);
        panel.setPrefWidth(430);
        panel.setMinWidth(330);
        return panel;
    }

    private Node createProtectionActions() {
        VBox switches = new VBox(7,
                switchRow("Rate Limiting", controller.rateLimitActiveProperty(), controller::setRateLimitActive),
                switchRow("Connection Limiting", controller.connectionLimitActiveProperty(), controller::setConnectionLimitActive),
                switchRow("Temporary Blocking", controller.blockingActiveProperty(), controller::setBlockingActive),
                switchRow("Auto Defense", controller.autoDefenseProperty(), controller::setAutoDefense));
        Button disable = UiIcons.graphic(new Button(), "fas-shield-alt", "button-icon");
        disable.textProperty().bind(Bindings.when(controller.protectionEnabledProperty())
                .then("Disable Protection").otherwise("Enable Protection"));
        disable.getStyleClass().addAll("action-button", "danger-button");
        disable.setOnAction(event -> controller.setProtectionEnabled(!controller.protectionEnabledProperty().get()));
        Button logs = UiIcons.graphic(new Button("View Logs"), "fas-eye", "button-icon");
        logs.getStyleClass().addAll("action-button", "outline-button");
        HBox actions = new HBox(8, disable, logs);
        actions.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox panel = panel(8, sectionHeader("fas-shield-alt", "PROTECTION ACTIONS"), switches, actions);
        panel.setPrefWidth(320);
        panel.setMinWidth(270);
        return panel;
    }

    private Node createRecentLogs() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ListView<String> list = new ListView<>(controller.getLogs());
        list.getStyleClass().addAll("server-log-list", "detection-log-list");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : logLine(item));
            }
        });
        controller.getLogs().addListener((ListChangeListener<String>) change -> {
            if (!controller.getLogs().isEmpty()) list.scrollTo(controller.getLogs().size() - 1);
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox panel = panel(6, sectionHeader("fas-file-alt", "RECENT LOGS", spacer, all), list);
        panel.setPrefWidth(420);
        panel.setMinWidth(320);
        return panel;
    }

    private void refreshChart() {
        requestSeries.getData().clear();
        thresholdSeries.getData().clear();
        for (DashboardController.ChartPoint point : controller.getChartPoints()) {
            requestSeries.getData().add(new XYChart.Data<>(point.index(), point.requestsPerSecond()));
            thresholdSeries.getData().add(new XYChart.Data<>(point.index(),
                    controller.getConfig().getRequestRateThreshold()));
        }
    }

    private void refreshDetectionEvents() {
        detectionEvents.clear();
        controller.getLogs().stream().map(DetectionPage::parseLog).filter(row ->
                !"HTTP_REQUEST".equals(row.event()) && !"APPLICATION_STARTED".equals(row.event()))
                .forEach(detectionEvents::add);
    }

    private boolean saveThresholds() {
        try {
            int requestRate = Integer.parseInt(requestThreshold.getText().trim());
            int clientRate = Integer.parseInt(clientThreshold.getText().trim());
            int connections = Integer.parseInt(connectionThreshold.getText().trim());
            long responseTime = Long.parseLong(responseThreshold.getText().trim());
            if (requestRate <= 0 || clientRate <= 0 || connections <= 0 || responseTime <= 0) {
                throw new IllegalArgumentException("Threshold values must be positive");
            }
            controller.getConfig().setRequestRateThreshold(requestRate);
            controller.getConfig().setPerClientRateThreshold(clientRate);
            controller.getConfig().setMaxActiveConnections(connections);
            controller.getConfig().setResponseTimeThresholdMillis(responseTime);
            requestThreshold.getParent().getStyleClass().remove("threshold-error");
            controller.appendLog("INFO", "SERVER", "THRESHOLDS_UPDATED", "Detection thresholds updated");
            refreshChart();
            return true;
        } catch (IllegalArgumentException exception) {
            requestThreshold.getParent().getStyleClass().add("threshold-error");
            controller.appendLog("ERROR", "SERVER", "INVALID_THRESHOLD", exception.getMessage());
            return false;
        }
    }

    private void setThresholdEditing(boolean editable) {
        for (TextField field : new TextField[]{requestThreshold, clientThreshold, connectionThreshold, responseThreshold}) {
            field.setEditable(editable);
            field.setFocusTraversable(editable);
        }
    }

    private HBox switchRow(String text, ObservableBooleanValue value, Consumer<Boolean> setter) {
        Label label = new Label(text);
        label.getStyleClass().add("protection-action-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label active = new Label();
        active.textProperty().bind(Bindings.when(value).then("ACTIVE").otherwise("OFF"));
        active.getStyleClass().add("protection-action-state");
        ToggleSwitch toggle = protectionSwitch(value, setter);
        HBox row = new HBox(6, UiIcons.icon("fas-check-circle", "rule-icon"), label, spacer, active, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private ToggleSwitch protectionSwitch(ObservableBooleanValue value, Consumer<Boolean> setter) {
        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(value.get());
        toggle.getStyleClass().add("protection-toggle-switch");
        toggle.selectedProperty().addListener((observable, oldValue, enabled) -> setter.accept(enabled));
        value.addListener((observable, oldValue, enabled) -> toggle.setSelected(enabled));
        return toggle;
    }

    private Label rule(String text) {
        Label label = UiIcons.graphic(new Label(text), "fas-check-circle", "rule-icon");
        label.getStyleClass().add("detection-rule");
        return label;
    }

    private HBox legend(String title, String style) {
        Region line = new Region();
        line.getStyleClass().addAll("legend-line", style);
        Label label = new Label(title);
        label.getStyleClass().add("legend-label");
        HBox item = new HBox(5, line, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private HBox sectionHeader(String icon, String title, Node... trailing) {
        Label label = UiIcons.graphic(new Label(title), icon, "section-icon");
        label.getStyleClass().add("detection-section-title");
        HBox header = new HBox(8, label);
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private VBox panel(double spacing, Node... nodes) {
        VBox panel = new VBox(spacing, nodes);
        panel.setMinWidth(0);
        panel.setPadding(new Insets(9, 11, 9, 11));
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Label valueLabel(javafx.beans.value.ObservableValue<String> value) {
        Label label = new Label();
        label.textProperty().bind(value);
        label.getStyleClass().add("attack-detail-value");
        return label;
    }

    private TableColumn<DetectionLogRow, String> textColumn(
            String name, java.util.function.Function<DetectionLogRow, String> getter) {
        TableColumn<DetectionLogRow, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private TableCell<ClientInfo, AttackStatus> statusCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(AttackStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? null : displayStatus(status));
                getStyleClass().removeAll("normal-cell", "warning-cell", "attack-cell");
                if (!empty && status != null) getStyleClass().add(switch (status) {
                    case NORMAL -> "normal-cell";
                    case SUSPICIOUS -> "warning-cell";
                    case ATTACK_DETECTED -> "attack-cell";
                });
            }
        };
    }

    private TableCell<ClientInfo, ClientInfo> blockCell() {
        return new TableCell<>() {
            private final Button block = UiIcons.graphic(new Button("Block"), "fas-ban", "block-action-icon");
            {
                block.getStyleClass().add("block-action-button");
                block.setOnAction(event -> {
                    ClientInfo client = getItem();
                    if (client != null) controller.appendLog("DEFENSE", client.ipAddress(),
                            "MANUAL_BLOCK", "Manual block requested from Detection page");
                });
            }

            @Override
            protected void updateItem(ClientInfo client, boolean empty) {
                super.updateItem(client, empty);
                setGraphic(empty || client == null ? null : block);
            }
        };
    }

    private static DetectionLogRow parseLog(String entry) {
        String[] fields = entry.split("\\s*\\|\\s*", 5);
        return new DetectionLogRow(field(fields, 0), field(fields, 1), field(fields, 2),
                field(fields, 3), field(fields, 4));
    }

    private static TextFlow logLine(String entry) {
        DetectionLogRow row = parseLog(entry);
        String style = switch (row.level()) {
            case "WARNING" -> "log-level-warning";
            case "ALERT", "ERROR" -> "log-level-alert";
            case "DEFENSE" -> "log-level-defense";
            default -> "log-level-info";
        };
        TextFlow flow = new TextFlow(text("[" + row.time() + "]  ", "log-time"),
                text("[" + row.level() + "]  ", style), text(row.details(), "log-description"));
        flow.getStyleClass().add("log-flow");
        return flow;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "-";
    }

    private static Text text(String value, String style) {
        Text text = new Text(value);
        text.getStyleClass().add(style);
        return text;
    }

    private static String displayStatus(AttackStatus status) {
        return status.name().replace('_', ' ');
    }

    private record DetectionLogRow(String time, String level, String ip, String event, String details) {
    }
}
