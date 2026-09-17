package server.view;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
import server.controller.DashboardController;
import server.detection.AttackStatus;
import server.model.ClientInfo;

/** Full Server Info page opened from the left navigation. */
public final class ServerInfoPage extends VBox {
    private final DashboardController controller;
    private final Spinner<Integer> portSpinner;

    public ServerInfoPage(DashboardController controller) {
        this.controller = controller;
        this.portSpinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                1, 65_535, controller.getConfig().getServerPort()));
        getStyleClass().add("server-info-page");
        setSpacing(10);
        setPadding(new Insets(12));

        HBox top = new HBox(10, createConfiguration(), createStatus(), createPresets());
        top.setMinHeight(300);
        HBox.setHgrow(top.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        HBox.setHgrow(top.getChildren().get(2), Priority.ALWAYS);

        VBox right = new VBox(10, createStatistics(), createClients());
        right.setPrefWidth(530);
        right.setMinWidth(440);
        VBox.setVgrow(right.getChildren().get(1), Priority.ALWAYS);
        HBox lower = new HBox(10, createServerLog(), right);
        HBox.setHgrow(lower.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(lower, Priority.ALWAYS);

        getChildren().addAll(createHero(), top, lower);
    }

    private Node createHero() {
        Node icon = UiIcons.icon("fas-server", "server-info-hero-icon");
        Label title = new Label("SERVER INFORMATION");
        title.getStyleClass().add("server-info-hero-title");
        Label subtitle = new Label("Web server configuration and status");
        subtitle.getStyleClass().add("server-info-subtitle");
        VBox heading = new VBox(3, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox hero = new HBox(18, icon, heading, spacer,
                heroState("fas-circle", "RUNNING", "Web server is active", controller.serverRunningProperty()),
                heroItem("fas-globe", "HTTP", "Protocol"),
                heroItem("far-clock", "Uptime", null));
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(12, 22, 12, 22));
        hero.getStyleClass().addAll("panel", "server-info-hero");
        return hero;
    }

    private Node heroState(String icon, String onText, String subtitle,
                           ObservableBooleanValue state) {
        Label value = UiIcons.graphic(new Label(), icon, "hero-state-icon");
        value.textProperty().bind(Bindings.when(state).then(onText).otherwise("STOPPED"));
        value.getStyleClass().add("hero-value");
        Label detail = new Label();
        detail.textProperty().bind(Bindings.when(state)
                .then(subtitle).otherwise("Web server is inactive"));
        detail.getStyleClass().add("hero-detail");
        return heroBox(value, detail);
    }

    private Node heroItem(String icon, String title, String detail) {
        Label value = UiIcons.graphic(new Label(title), icon, "hero-state-icon");
        value.getStyleClass().add("hero-value");
        Label detailLabel = new Label(detail == null ? "" : detail);
        if (detail == null) {
            detailLabel.textProperty().bind(Bindings.concat(controller.uptimeProperty(), "  (since start)"));
        }
        detailLabel.getStyleClass().add("hero-detail");
        return heroBox(value, detailLabel);
    }

    private Node heroBox(Label value, Label detail) {
        VBox box = new VBox(3, value, detail);
        box.setPadding(new Insets(5, 24, 5, 24));
        box.getStyleClass().add("hero-item");
        return box;
    }

    private Node createConfiguration() {
        TextField ip = new TextField();
        ip.textProperty().bind(controller.serverIpProperty());
        ip.setEditable(false);
        ip.getStyleClass().add("server-config-field");

        portSpinner.setEditable(true);
        portSpinner.getStyleClass().add("server-config-spinner");
        ComboBox<String> protocol = new ComboBox<>();
        protocol.getItems().add("HTTP");
        protocol.getSelectionModel().selectFirst();
        protocol.setMaxWidth(Double.MAX_VALUE);
        protocol.getStyleClass().add("server-config-field");
        int workers = Math.max(8, Runtime.getRuntime().availableProcessors() * 4);
        Spinner<Integer> threads = new Spinner<>(1, 256, workers);
        threads.setEditable(true);
        threads.getStyleClass().add("server-config-spinner");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        addConfigRow(form, 0, "fas-network-wired", "Server IP", ip);
        addConfigRow(form, 1, "fas-plug", "Port", portSpinner);
        addConfigRow(form, 2, "fas-globe", "Protocol", protocol);
        addConfigRow(form, 3, "fas-microchip", "Thread Pool Size", threads);

        Button start = action("START SERVER", "fas-play", "success-button");
        Button stop = action("STOP SERVER", "fas-stop", "danger-button");
        Button restart = action("RESTART", "fas-sync-alt", "outline-button");
        start.disableProperty().bind(controller.serverRunningProperty());
        stop.disableProperty().bind(controller.serverRunningProperty().not());
        restart.disableProperty().bind(controller.serverRunningProperty().not());
        start.setOnAction(event -> startFromForm());
        stop.setOnAction(event -> controller.stopServer());
        restart.setOnAction(event -> {
            controller.stopServer();
            startFromForm();
        });
        HBox buttons = new HBox(10, start, stop, restart);
        buttons.getChildren().forEach(button -> HBox.setHgrow(button, Priority.ALWAYS));

        VBox panel = titledPanel("fas-cog", "SERVER CONFIGURATION", form, buttons);
        panel.setPrefWidth(455);
        panel.setMinWidth(350);
        return panel;
    }

    private void addConfigRow(GridPane form, int row, String icon, String name, Node field) {
        Label label = UiIcons.graphic(new Label(name), icon, "config-row-icon");
        label.getStyleClass().add("config-row-label");
        Label colon = new Label(":");
        colon.getStyleClass().add("config-row-label");
        form.add(label, 0, row);
        form.add(colon, 1, row);
        form.add(field, 2, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private Node createStatus() {
        GridPane values = new GridPane();
        values.setHgap(16);
        values.setVgap(8);
        Label state = new Label();
        state.textProperty().bind(Bindings.when(controller.serverRunningProperty()).then("●  RUNNING").otherwise("●  STOPPED"));
        state.getStyleClass().add("server-running-value");
        addStatusRow(values, 0, "Status", state);
        addStatusRow(values, 1, "IP Address", boundLabel(controller.serverIpProperty()));
        addStatusRow(values, 2, "Port", boundLabel(controller.serverPortProperty().asString()));
        addStatusRow(values, 3, "Protocol", new Label("HTTP"));
        addStatusRow(values, 4, "Uptime", boundLabel(controller.uptimeProperty()));
        addStatusRow(values, 5, "Active Connections", boundLabel(Bindings.concat(
                controller.activeConnectionsProperty(), " / ", controller.getConfig().getMaxActiveConnections())));
        addStatusRow(values, 6, "Thread Pool", new Label(Math.max(8,
                Runtime.getRuntime().availableProcessors() * 4) + " threads"));
        addStatusRow(values, 7, "Queue Size", new Label("0"));
        addStatusRow(values, 8, "Response Time (avg)", boundLabel(Bindings.format("%.0f ms",
                controller.averageResponseTimeProperty())));
        VBox panel = titledPanel("fas-server", "SERVER STATUS", values);
        panel.setPrefWidth(285);
        panel.setMinWidth(245);
        return panel;
    }

    private void addStatusRow(GridPane grid, int row, String key, Label value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("server-status-key");
        value.getStyleClass().add("server-status-value");
        grid.add(keyLabel, 0, row);
        grid.add(value, 1, row);
    }

    private Node createPresets() {
        Button local = preset("fas-network-wired", "Local Test", "127.0.0.1:8080");
        Button lab = preset("fas-file-medical-alt", "Lab Server",
                controller.serverIpProperty().get() + ":" + controller.serverPortProperty().get());
        Button custom = preset("fas-file-alt", "Custom", "Enter IP:Port");
        local.setOnAction(event -> portSpinner.getValueFactory().setValue(8080));
        lab.setOnAction(event -> portSpinner.getValueFactory().setValue(controller.getConfig().getServerPort()));
        custom.setOnAction(event -> portSpinner.requestFocus());
        VBox list = new VBox(12, local, lab, custom);
        VBox panel = titledPanel("fas-bolt", "QUICK PRESETS", list);
        panel.setPrefWidth(285);
        panel.setMinWidth(240);
        return panel;
    }

    private Button preset(String icon, String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("preset-title");
        Label detail = new Label(subtitle);
        detail.getStyleClass().add("preset-detail");
        VBox text = new VBox(2, titleLabel, detail);
        Node leading = UiIcons.icon(icon, "preset-icon");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node arrow = UiIcons.icon("fas-chevron-right", "preset-arrow");
        HBox graphic = new HBox(14, leading, text, spacer, arrow);
        graphic.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(graphic);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("preset-button");
        return button;
    }

    private Node createServerLog() {
        ComboBox<String> filter = new ComboBox<>();
        filter.getItems().addAll("ALL", "INFO", "WARNING", "ALERT", "DEFENSE");
        filter.getSelectionModel().selectFirst();
        filter.setPrefWidth(105);
        Button clear = UiIcons.graphic(new Button("CLEAR"), "fas-trash-alt", "button-icon");
        clear.getStyleClass().addAll("small-button", "action-button", "outline-button");
        clear.setOnAction(event -> controller.clearLogs());
        Button export = UiIcons.graphic(new Button("EXPORT"), "fas-download", "button-icon");
        export.getStyleClass().addAll("small-button", "action-button", "outline-button");
        export.setOnAction(event -> controller.appendLog("INFO", "SERVER", "EXPORT", "Export requested"));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, new Label("Filter:"), filter, clear, export);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = sectionHeader("fas-file-alt", "SERVER LOG", spacer, actions);

        ListView<String> list = new ListView<>(controller.getLogs());
        list.getStyleClass().add("server-log-list");
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
        panel.setPrefWidth(515);
        panel.setMinWidth(420);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createStatistics() {
        GridPane grid = new GridPane();
        grid.setHgap(9);
        grid.setVgap(8);
        grid.add(statCard("fas-server", "Total Requests", controller.totalRequestsProperty().asString("%,d"), "blue"), 0, 0);
        grid.add(statCard("fas-bolt", "Requests/sec", controller.requestsPerSecondProperty().asString(), "cyan"), 1, 0);
        grid.add(statCard("fas-check-circle", "Successful", controller.successfulRequestsProperty().asString("%,d"), "green"), 2, 0);
        grid.add(statCard("fas-times-circle", "Failed", controller.failedRequestsProperty().asString("%,d"), "red"), 0, 1);
        grid.add(statCard("fas-ban", "Dropped", controller.droppedRequestsProperty().asString("%,d"), "purple"), 1, 1);
        grid.add(statCard("fas-exclamation-triangle", "Limited", controller.limitedRequestsProperty().asString("%,d"), "yellow"), 2, 1);
        grid.add(statCard("fas-users", "Active Connections", controller.activeConnectionsProperty().asString(), "blue"), 0, 2);
        grid.add(statCard("far-clock", "Avg. Response Time", Bindings.format("%.0f ms", controller.averageResponseTimeProperty()), "cyan"), 1, 2);
        grid.add(statCard("fas-stopwatch", "Response Threshold", new javafx.beans.property.SimpleStringProperty(
                controller.getConfig().getResponseTimeThresholdMillis() + " ms"), "purple"), 2, 2);
        grid.getChildren().forEach(node -> GridPane.setHgrow(node, Priority.ALWAYS));
        return titledPanel("fas-chart-bar", "SERVER STATISTICS", grid);
    }

    private Node statCard(String icon, String title, ObservableValue<String> value, String color) {
        Node symbol = UiIcons.icon(icon, "server-stat-icon");
        symbol.getStyleClass().add(color);
        Label caption = new Label(title);
        caption.getStyleClass().add("server-stat-title");
        Label number = new Label();
        number.textProperty().bind(value);
        number.getStyleClass().addAll("server-stat-value", color);
        HBox card = new HBox(10, symbol, new VBox(1, caption, number));
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("server-stat-card");
        GridPane.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Node createClients() {
        Button viewAll = new Button("View All");
        viewAll.getStyleClass().add("view-all-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = sectionHeader("fas-users", "CLIENT CONNECTIONS", spacer, viewAll);
        TableView<ClientInfo> table = new TableView<>(controller.getClients());
        table.setPlaceholder(new Label("No client connections"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ClientInfo, String> ip = new TableColumn<>("IP Address");
        ip.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().ipAddress()));
        TableColumn<ClientInfo, Number> rate = new TableColumn<>("Requests/sec");
        rate.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue().requestsPerSecond()));
        TableColumn<ClientInfo, Number> total = new TableColumn<>("Total Requests");
        total.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue().totalRequests()));
        TableColumn<ClientInfo, AttackStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(cell -> new javafx.beans.property.ReadOnlyObjectWrapper<>(cell.getValue().status()));
        status.setCellFactory(column -> statusCell());
        TableColumn<ClientInfo, String> seen = new TableColumn<>("Last Seen");
        seen.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(controller.clockProperty().get()));
        table.getColumns().addAll(ip, rate, total, status, seen);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox panel = new VBox(7, header, table);
        panel.setPadding(new Insets(9));
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

    private VBox titledPanel(String icon, String title, Node... content) {
        HBox header = sectionHeader(icon, title);
        VBox panel = new VBox(10);
        panel.getChildren().add(header);
        panel.getChildren().addAll(content);
        panel.setPadding(new Insets(11, 13, 11, 13));
        panel.getStyleClass().add("panel");
        return panel;
    }

    private HBox sectionHeader(String icon, String title, Node... trailing) {
        Label label = UiIcons.graphic(new Label(title), icon, "section-icon");
        label.getStyleClass().add("server-info-section-title");
        HBox header = new HBox(8, label);
        header.getChildren().addAll(trailing);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private Button action(String text, String icon, String style) {
        Button button = UiIcons.graphic(new Button(text), icon, "button-icon");
        button.getStyleClass().addAll("server-info-action", "action-button", style);
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private Label boundLabel(ObservableValue<String> value) {
        Label label = new Label();
        label.textProperty().bind(value);
        return label;
    }

    private void startFromForm() {
        Integer port = portSpinner.getValue();
        if (port != null) controller.startServer(port);
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
                text(field(fields, 2) + "  ", "log-ip"),
                text("|  ", "log-separator"),
                text(field(fields, 3) + "  ", levelStyle),
                text("|  ", "log-separator"),
                text(field(fields, 4), "log-description"));
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
}
