package server.view;

import java.util.Locale;
import java.util.function.Consumer;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.ToggleSwitch;
import server.controller.DashboardController;
import server.detection.AttackStatus;
import server.model.ClientInfo;

/** Realtime KPI cards, request-rate chart, client table and detection details. */
public final class MonitorPanel extends VBox {
    private final DashboardController controller;
    private final XYChart.Series<Number, Number> requestSeries = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> thresholdSeries = new XYChart.Series<>();

    public MonitorPanel(DashboardController controller) {
        this.controller = controller;
        setSpacing(10);
        getChildren().addAll(createStatisticsPanel(), createChartPanel(), createBottomPanels());
        VBox.setVgrow(getChildren().getLast(), Priority.ALWAYS);
    }

    private Node createStatisticsPanel() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(metricCard("fas-server", "Total Requests", controller.totalRequestsProperty().asString("%,d"), "Received", "blue"), 0, 0);
        grid.add(metricCard("fas-bolt", "Requests/sec", controller.requestsPerSecondProperty().asString("%d"), "Rolling window", "cyan"), 1, 0);
        grid.add(metricCard("fas-check-circle", "Successful Requests", controller.successfulRequestsProperty().asString("%,d"), "HTTP 2xx", "green"), 2, 0);
        grid.add(metricCard("fas-times-circle", "Failed Requests", controller.failedRequestsProperty().asString("%,d"), "Parse / server", "red"), 3, 0);
        grid.add(metricCard("fas-exclamation-triangle", "Limited Requests", controller.limitedRequestsProperty().asString("%,d"), "HTTP 429", "yellow"), 0, 1);
        grid.add(metricCard("fas-ban", "Dropped Requests", controller.droppedRequestsProperty().asString("%,d"), "Protection drop", "purple"), 1, 1);
        grid.add(metricCard("fas-users", "Active Connections", controller.activeConnectionsProperty().asString("%d"), "TCP sessions", "cyan"), 2, 1);
        grid.add(metricCard("far-clock", "Avg. Response Time", Bindings.format(Locale.US, "%.0f ms", controller.averageResponseTimeProperty()), "Rolling average", "purple"), 3, 1);
        for (int column = 0; column < 4; column++) {
            GridPane.setHgrow(grid.getChildren().get(column), Priority.ALWAYS);
            GridPane.setHgrow(grid.getChildren().get(column + 4), Priority.ALWAYS);
        }

        VBox panel = section("fas-project-diagram", "REAL-TIME STATISTICS", grid);
        panel.getStyleClass().add("statistics-panel");
        return panel;
    }

    private Node createChartPanel() {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setForceZeroInRange(false);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setAutoRanging(true);
        yAxis.setForceZeroInRange(true);
        yAxis.setLabel("Requests/sec");

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setLegendVisible(false);
        chart.setHorizontalGridLinesVisible(true);
        chart.setVerticalGridLinesVisible(true);
        chart.setMinHeight(0);
        chart.getStyleClass().add("traffic-chart");
        requestSeries.setName("Current RPS");
        thresholdSeries.setName("Target RPS");
        chart.getData().addAll(requestSeries, thresholdSeries);
        refreshChart();
        controller.getChartPoints().addListener((ListChangeListener<DashboardController.ChartPoint>) change -> refreshChart());

        HBox legend = new HBox(16,
                legendItem("Current RPS", "current-rps-line"),
                legendItem("Target RPS", "target-rps-line"));
        legend.setAlignment(Pos.CENTER_RIGHT);
        VBox panel = section("fas-chart-bar", "REQUEST RATE  (req/sec)", chart, legend);
        VBox.setVgrow(chart, Priority.ALWAYS);
        return panel;
    }

    private Node createBottomPanels() {
        Button clientViewAll = viewAllButton();
        clientViewAll.setOnAction(event -> controller.appendLog(
                "INFO", "DASHBOARD", "VIEW_CLIENTS", "Requested complete client list"));
        VBox clientPanel = section("fas-users", "CLIENT TRAFFIC", createClientTable(), clientViewAll);
        clientPanel.setMinSize(0, 0);
        HBox.setHgrow(clientPanel, Priority.ALWAYS);

        HBox detectionContent = new HBox(10, createDetectionCard(), createProtectionCard());
        detectionContent.setMinWidth(0);
        HBox.setHgrow(detectionContent.getChildren().getFirst(), Priority.ALWAYS);
        HBox.setHgrow(detectionContent.getChildren().getLast(), Priority.ALWAYS);

        ScrollPane detectionScroll = new ScrollPane(detectionContent);
        detectionScroll.setFitToWidth(true);
        detectionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detectionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        detectionScroll.setMinSize(0, 0);
        detectionScroll.getStyleClass().add("component-scroll");

        VBox detectionPanel = section("fas-shield-alt", "DETECTION & PROTECTION", detectionScroll);
        detectionPanel.setMinSize(0, 0);
        HBox.setHgrow(detectionPanel, Priority.ALWAYS);

        HBox bottom = new HBox(10, clientPanel, detectionPanel);
        bottom.setMinHeight(0);
        return bottom;
    }

    private TableView<ClientInfo> createClientTable() {
        TableView<ClientInfo> table = new TableView<>(controller.getClients());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No client traffic"));

        TableColumn<ClientInfo, String> ip = new TableColumn<>("IP Address");
        ip.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().ipAddress()));
        TableColumn<ClientInfo, Number> rate = new TableColumn<>("Requests/sec");
        rate.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().requestsPerSecond()));
        TableColumn<ClientInfo, Number> total = new TableColumn<>("Total Requests");
        total.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().totalRequests()));
        TableColumn<ClientInfo, AttackStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(AttackStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name().replace('_', ' '));
                getStyleClass().removeAll("normal-cell", "warning-cell", "attack-cell");
                if (!empty && item != null) {
                    getStyleClass().add(switch (item) {
                        case NORMAL -> "normal-cell";
                        case SUSPICIOUS -> "warning-cell";
                        case ATTACK_DETECTED -> "attack-cell";
                    });
                }
            }
        });
        table.getColumns().addAll(ip, rate, total, status);
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    private Node createDetectionCard() {
        Label label = new Label("Detection Status");
        label.getStyleClass().add("detail-label");
        Label status = new Label();
        status.textProperty().bind(Bindings.createStringBinding(
                () -> controller.attackStatusProperty().get().name().replace('_', ' '),
                controller.attackStatusProperty()));
        status.getStyleClass().add("detection-status");
        controller.attackStatusProperty().addListener((observable, oldValue, newValue) -> {
            status.getStyleClass().removeAll("normal-text", "warning-text", "danger-text");
            status.getStyleClass().add(switch (newValue) {
                case NORMAL -> "normal-text";
                case SUSPICIOUS -> "warning-text";
                case ATTACK_DETECTED -> "danger-text";
            });
        });
        status.getStyleClass().add("normal-text");
        Label reason = new Label();
        reason.textProperty().bind(controller.detectionReasonProperty());
        reason.setWrapText(true);
        reason.getStyleClass().add("detail-value");
        Label threshold = new Label("Threshold: " + controller.getConfig().getRequestRateThreshold() + " req/sec");
        threshold.getStyleClass().add("detail-value");
        Label time = new Label();
        time.textProperty().bind(Bindings.concat("Detected at: ", controller.detectedAtProperty()));
        time.getStyleClass().add("detail-value");

        VBox card = new VBox(7, label, status, reason, threshold, time, separator());
        card.getStyleClass().add("inner-card");
        card.setPadding(new Insets(8));
        card.setMinSize(0, 0);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Node createProtectionCard() {
        Label label = new Label("Protection Status");
        label.getStyleClass().add("detail-label");
        Label status = UiIcons.graphic(new Label(), "fas-check-circle", "status-icon");
        status.textProperty().bind(Bindings.when(controller.protectionEnabledProperty())
                .then("ACTIVE").otherwise("DISABLED"));
        status.getStyleClass().addAll("detection-status", "normal-text");

        Node rateSwitch = switchControl(controller.rateLimitActiveProperty(), controller::setRateLimitActive);
        Node connectionSwitch = switchControl(controller.connectionLimitActiveProperty(), controller::setConnectionLimitActive);
        Node blockingSwitch = switchControl(controller.blockingActiveProperty(), controller::setBlockingActive);
        Node autoSwitch = switchControl(controller.autoDefenseProperty(), controller::setAutoDefense);

        HBox rate = toggleRow("Rate Limiting", rateSwitch);
        HBox connections = toggleRow("Connection Limiting", connectionSwitch);
        HBox blocking = toggleRow("Temporary Blocking", blockingSwitch);
        HBox auto = toggleRow("Auto Defense", autoSwitch);


        VBox card = new VBox(6, label, status, separator(), rate, connections, blocking, auto);
        card.getStyleClass().add("inner-card");
        card.setPadding(new Insets(10));
        card.setMinSize(0, 0);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void refreshChart() {
        requestSeries.getData().clear();
        thresholdSeries.getData().clear();
        for (DashboardController.ChartPoint point : controller.getChartPoints()) {
            requestSeries.getData().add(new XYChart.Data<>(point.index(), point.requestsPerSecond()));
            thresholdSeries.getData().add(new XYChart.Data<>(point.index(), controller.getConfig().getRequestRateThreshold()));
        }
    }

    private HBox metricCard(String icon, String title, javafx.beans.value.ObservableValue<String> value,
                            String note, String color) {
        Node iconLabel = UiIcons.icon(icon, "metric-icon");
        iconLabel.getStyleClass().add(color);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        Label valueLabel = new Label();
        valueLabel.textProperty().bind(value);
        valueLabel.getStyleClass().addAll("metric-value", color);
        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add("metric-note");
        VBox text = new VBox(2, titleLabel, valueLabel, noteLabel);
        HBox card = new HBox(12, iconLabel, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10));
        card.setMinSize(0, 0);
        card.getStyleClass().addAll("metric-card", color + "-border");
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox section(String icon, String title, Node content) {
        return section(icon, title, content, null);
    }

    private VBox section(String icon, String title, Node content, Node headerAction) {
        Label heading = UiIcons.graphic(new Label(title), icon, "section-icon");
        heading.getStyleClass().addAll("section-title", "section-title-inline");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, heading, spacer);
        if (headerAction != null) {
            header.getChildren().add(headerAction);
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("section-header");
        VBox box = new VBox(8, header, content);
        box.setPadding(new Insets(8, 10, 9, 10));
        box.getStyleClass().add("panel");
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private Label rule(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("rule-ok");
        return label;
    }

    private HBox legendItem(String text, String lineClass) {
        Region line = new Region();
        line.getStyleClass().addAll("legend-line", lineClass);
        line.setMaxHeight(2);
        Label label = new Label(text);
        label.getStyleClass().add("legend-label");
        HBox item = new HBox(6, line, label);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setFillHeight(false);
        return item;
    }

    private Button viewAllButton() {
        Button button = new Button("View All");
        button.getStyleClass().add("view-all-button");
        return button;
    }

    /**
     * A dedicated slider control, deliberately not a Button: click or drag the thumb to change state.
     */
    private ToggleSwitch switchControl(ObservableBooleanValue value, Consumer<Boolean> update) {
        ToggleSwitch slider = new ToggleSwitch();
        slider.setSelected(value.get());
        slider.getStyleClass().add("protection-toggle-switch");
        slider.selectedProperty().addListener((observable, oldValue, enabled) -> update.accept(enabled));
        value.addListener((observable, oldValue, enabled) -> slider.setSelected(enabled));
        return slider;
    }

    private HBox toggleRow(String text, Node toggle) {
        Label label = new Label(text);
        label.getStyleClass().add("toggle-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(6, label, spacer, toggle);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region separator() {
        Region region = new Region();
        region.getStyleClass().add("separator-line");
        region.setPrefHeight(1);
        return region;
    }
}
