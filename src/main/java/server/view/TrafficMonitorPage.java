package server.view;

import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
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

/** Detailed realtime traffic page opened from the Traffic Monitor navigation item. */
public final class TrafficMonitorPage extends VBox {
    private final DashboardController controller;
    private final XYChart.Series<Number, Number> totalSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> successfulSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> thresholdSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> miniSeries = new XYChart.Series<>();

    public TrafficMonitorPage(DashboardController controller) {
        this.controller = controller;
        getStyleClass().add("traffic-monitor-page");
        setMinWidth(0);
        setSpacing(10);
        setPadding(new Insets(10, 12, 10, 12));

        HBox middle = new HBox(10, createMainChart(), createRealtimeLog());
        middle.setPrefHeight(300);
        middle.setMinHeight(260);
        HBox.setHgrow(middle.getChildren().get(0), Priority.ALWAYS);

        HBox bottom = new HBox(10, createClientTraffic(), createDetectionProtection(), createTrafficSummary());
        bottom.setPrefHeight(260);
        bottom.setMinHeight(225);
        bottom.getChildren().forEach(node -> HBox.setHgrow(node, Priority.ALWAYS));
        VBox.setVgrow(bottom, Priority.ALWAYS);

        getChildren().addAll(createHero(), createMetricStrip(), middle, bottom);
        refreshCharts();
        controller.getChartPoints().addListener(
                (ListChangeListener<DashboardController.ChartPoint>) change -> refreshCharts());
    }

    private Node createHero() {
        Node icon = UiIcons.icon("fas-chart-bar", "traffic-hero-icon");
        Label title = new Label("TRAFFIC MONITOR");
        title.getStyleClass().add("traffic-hero-title");
        Label subtitle = new Label("Real-time traffic analysis and monitoring");
        subtitle.getStyleClass().add("traffic-hero-subtitle");
        VBox heading = new VBox(3, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hero = new HBox(18, icon, heading, spacer,
                heroStatus("fas-bullseye", "SERVER STATUS",
                        Bindings.when(controller.serverRunningProperty()).then("RUNNING").otherwise("STOPPED")),
                heroStatus("fas-shield-alt", "PROTECTION",
                        Bindings.when(controller.protectionEnabledProperty()).then("ACTIVE").otherwise("DISABLED")),
                heroStatus("fas-exclamation-triangle", "ATTACK STATUS",
                        Bindings.createStringBinding(() -> controller.attackStatusProperty().get().name().replace('_', ' '),
                                controller.attackStatusProperty())),
                heroStatus("far-clock", "UPTIME", controller.uptimeProperty()));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(10, 18, 10, 18));
        hero.getStyleClass().addAll("panel", "traffic-hero");
        return hero;
    }

    private Node heroStatus(String icon, String title, ObservableValue<String> value) {
        Node symbol = UiIcons.icon(icon, "traffic-hero-status-icon");
        Label caption = new Label(title);
        caption.getStyleClass().add("traffic-hero-status-title");
        Label state = new Label();
        state.textProperty().bind(value);
        state.getStyleClass().add("traffic-hero-status-value");
        VBox text = new VBox(2, caption, state);
        HBox box = new HBox(10, symbol, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("traffic-hero-status");
        return box;
    }

    private Node createMetricStrip() {
        HBox metrics = new HBox(10,
                metric("fas-server", "INCOMING REQUESTS", controller.totalRequestsProperty().asString("%,d"), "blue"),
                metric("fas-bolt", "INCOMING/SEC", controller.requestsPerSecondProperty().asString(), "cyan"),
                metric("fas-check-circle", "SUCCESSFUL", controller.successfulRequestsProperty().asString("%,d"), "green"),
                metric("fas-times-circle", "FAILED", controller.failedRequestsProperty().asString("%,d"), "red"),
                metric("fas-exclamation-triangle", "LIMITED", controller.limitedRequestsProperty().asString("%,d"), "yellow"),
                metric("fas-ban", "DROPPED", controller.droppedRequestsProperty().asString("%,d"), "purple"));
        metrics.getChildren().forEach(card -> HBox.setHgrow(card, Priority.ALWAYS));
        return metrics;
    }

    private Node metric(String icon, String title, ObservableValue<String> value, String color) {
        Node symbol = UiIcons.icon(icon, "traffic-metric-icon");
        symbol.getStyleClass().add(color);
        Label caption = new Label(title);
        caption.getStyleClass().add("traffic-metric-title");
        Label number = new Label();
        number.textProperty().bind(value);
        number.getStyleClass().addAll("traffic-metric-value", color);
        HBox card = new HBox(12, symbol, new VBox(3, caption, number));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("panel", "traffic-metric-card", color + "-border");
        card.setMinWidth(0);
        card.setPrefWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node createMainChart() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setMinorTickVisible(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(true);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.getStyleClass().addAll("traffic-chart", "traffic-main-chart");
        chart.getData().addAll(totalSeries, successfulSeries, thresholdSeries);
        VBox.setVgrow(chart, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox legend = new HBox(14, spacer,
                legend("Total RPS", "traffic-total-line"),
                legend("Successful RPS", "traffic-success-line"),
                legend("Threshold", "traffic-threshold-line"));
        legend.setAlignment(Pos.CENTER_RIGHT);
        HBox header = sectionHeader("fas-chart-bar", "REQUEST RATE  (req/sec)");
        VBox panel = new VBox(6, header, legend, chart);
        panel.setPadding(new Insets(10));
        panel.setMinWidth(0);
        panel.setPrefWidth(0);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createRealtimeLog() {
        Button clear = UiIcons.graphic(new Button("Clear"), "fas-trash-alt", "button-icon");
        clear.getStyleClass().addAll("small-button", "action-button", "outline-button");
        clear.setOnAction(event -> controller.clearLogs());
        Button export = UiIcons.graphic(new Button("Export"), "fas-download", "button-icon");
        export.getStyleClass().addAll("small-button", "action-button", "outline-button");
        export.setOnAction(event -> controller.appendLog("INFO", "SERVER", "EXPORT", "Traffic log export requested"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-file-alt", "REAL-TIME LOG", spacer, clear, export);

        ListView<String> list = new ListView<>(controller.getLogs());
        list.getStyleClass().addAll("server-log-list", "traffic-log-list");
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
        VBox panel = new VBox(8, header, list);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(330);
        panel.setMinWidth(270);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createClientTraffic() {
        Button all = new Button("View All");
        all.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-users", "CLIENT TRAFFIC", spacer, all);

        TableView<ClientInfo> table = new TableView<>(controller.getClients());
        table.setPlaceholder(new Label("No client traffic"));
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
        table.getColumns().addAll(ip, rate, total, status);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = new VBox(7, header, table);
        panel.setPadding(new Insets(9));
        panel.setPrefWidth(0);
        panel.setMinWidth(0);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createDetectionProtection() {
        Label detectionTitle = new Label("DETECTION STATUS");
        detectionTitle.getStyleClass().add("traffic-detail-title");
        Label attack = new Label();
        attack.textProperty().bind(Bindings.createStringBinding(
                () -> controller.attackStatusProperty().get().name().replace('_', ' '),
                controller.attackStatusProperty()));
        attack.getStyleClass().add("traffic-attack-value");
        Label reason = new Label();
        reason.textProperty().bind(controller.detectionReasonProperty());
        reason.setWrapText(true);
        reason.getStyleClass().add("detail-value");
        Label threshold = new Label("Threshold: " + controller.getConfig().getRequestRateThreshold() + " req/sec");
        threshold.getStyleClass().add("detail-value");
        VBox detection = new VBox(6, detectionTitle, attack, reason, threshold,
                rule("Request rate (global)"), rule("Per-client rate"), rule("Active connections"));
        detection.getStyleClass().add("traffic-subcard");

        Label protectionTitle = new Label("PROTECTION STATUS");
        protectionTitle.getStyleClass().add("traffic-detail-title");
        Label active = new Label();
        active.textProperty().bind(Bindings.when(controller.protectionEnabledProperty())
                .then("●  ACTIVE").otherwise("●  DISABLED"));
        active.getStyleClass().add("traffic-protection-value");
        VBox protection = new VBox(6, protectionTitle, active,
                switchRow("Rate Limiting", controller.rateLimitActiveProperty(), controller::setRateLimitActive),
                switchRow("Connection Limiting", controller.connectionLimitActiveProperty(), controller::setConnectionLimitActive),
                switchRow("Temporary Blocking", controller.blockingActiveProperty(), controller::setBlockingActive),
                switchRow("Auto Defense", controller.autoDefenseProperty(), controller::setAutoDefense));
        protection.getStyleClass().add("traffic-subcard");

        HBox cards = new HBox(8, detection, protection);
        cards.getChildren().forEach(card -> HBox.setHgrow(card, Priority.ALWAYS));
        VBox panel = new VBox(7, sectionHeader("fas-shield-alt", "DETECTION & PROTECTION"), cards);
        panel.setPadding(new Insets(9));
        panel.setPrefWidth(0);
        panel.setMinWidth(0);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createTrafficSummary() {
        Button oneMinute = rangeButton("1m", true);
        Button fiveMinutes = rangeButton("5m", false);
        Button fifteenMinutes = rangeButton("15m", false);
        Button hour = rangeButton("1h", false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-chart-bar", "TRAFFIC STATISTICS", spacer,
                oneMinute, fiveMinutes, fifteenMinutes, hour);

        Label current = new Label();
        current.textProperty().bind(Bindings.concat("Current: ", controller.requestsPerSecondProperty(), " req/sec"));
        current.getStyleClass().add("traffic-summary-current");
        NumberAxis x = new NumberAxis();
        x.setTickLabelsVisible(false);
        x.setMinorTickVisible(false);
        NumberAxis y = new NumberAxis();
        y.setAutoRanging(true);
        y.setForceZeroInRange(true);
        LineChart<Number, Number> chart = new LineChart<>(x, y);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        chart.getData().add(miniSeries);
        chart.getStyleClass().add("traffic-mini-chart");
        VBox.setVgrow(chart, Priority.ALWAYS);
        VBox panel = new VBox(7, header, current, chart);
        panel.setPadding(new Insets(9));
        panel.setPrefWidth(0);
        panel.setMinWidth(0);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private void refreshCharts() {
        totalSeries.getData().clear();
        successfulSeries.getData().clear();
        thresholdSeries.getData().clear();
        miniSeries.getData().clear();
        for (DashboardController.ChartPoint point : controller.getChartPoints()) {
            totalSeries.getData().add(new XYChart.Data<>(point.index(), point.requestsPerSecond()));
            successfulSeries.getData().add(new XYChart.Data<>(point.index(),
                    point.successfulRequestsPerSecond()));
            thresholdSeries.getData().add(new XYChart.Data<>(point.index(),
                    controller.getConfig().getRequestRateThreshold()));
            miniSeries.getData().add(new XYChart.Data<>(point.index(), point.requestsPerSecond()));
        }
    }

    private HBox switchRow(String text, ObservableBooleanValue value, Consumer<Boolean> setter) {
        Label label = new Label(text);
        label.getStyleClass().add("toggle-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ToggleSwitch toggle = new ToggleSwitch();
        toggle.setSelected(value.get());
        toggle.getStyleClass().add("protection-toggle-switch");
        toggle.selectedProperty().addListener((observable, oldValue, enabled) -> setter.accept(enabled));
        value.addListener((observable, oldValue, enabled) -> toggle.setSelected(enabled));
        HBox row = new HBox(5, label, spacer, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Label rule(String text) {
        Label label = new Label("●  " + text);
        label.getStyleClass().add("rule-ok");
        return label;
    }

    private HBox legend(String text, String style) {
        Region line = new Region();
        line.getStyleClass().addAll("legend-line", style);
        Label label = new Label(text);
        label.getStyleClass().add("legend-label");
        HBox item = new HBox(6, line, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    private Button rangeButton(String text, boolean selected) {
        Button button = new Button(text);
        button.getStyleClass().add("traffic-range-button");
        if (selected) button.getStyleClass().add("selected");
        return button;
    }

    private HBox sectionHeader(String icon, String title, Node... trailing) {
        Label label = UiIcons.graphic(new Label(title), icon, "section-icon");
        label.getStyleClass().add("traffic-section-title");
        HBox header = new HBox(8, label);
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private TableCell<ClientInfo, AttackStatus> statusCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(AttackStatus status, boolean empty) {
                super.updateItem(status, empty);
                setText(empty || status == null ? null : status.name().replace('_', ' '));
                getStyleClass().removeAll("normal-cell", "warning-cell", "attack-cell");
                if (!empty && status != null) {
                    getStyleClass().add(switch (status) {
                        case NORMAL -> "normal-cell";
                        case SUSPICIOUS -> "warning-cell";
                        case ATTACK_DETECTED -> "attack-cell";
                    });
                }
            }
        };
    }

    private static TextFlow logLine(String entry) {
        String[] fields = entry.split("\\s*\\|\\s*", 5);
        String level = field(fields, 1);
        String levelStyle = switch (level) {
            case "WARNING" -> "log-level-warning";
            case "ALERT", "ERROR" -> "log-level-alert";
            case "DEFENSE" -> "log-level-defense";
            default -> "log-level-info";
        };
        TextFlow flow = new TextFlow(
                text("[" + field(fields, 0) + "]  ", "log-time"),
                text("[" + level + "]  ", levelStyle),
                text(field(fields, 4), "log-description"));
        flow.getStyleClass().add("log-flow");
        return flow;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "-";
    }

    private static Text text(String value, String styleClass) {
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        return text;
    }
}
