package server.view;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import server.controller.DashboardController;

/** Right rail for status summary, security events and quick actions. */
public final class SecurityPanel extends VBox {
    private final DashboardController controller;

    public SecurityPanel(DashboardController controller) {
        this.controller = controller;
        setSpacing(10);
        getChildren().addAll(createSummary(), createLogPanel(), createActions(), createServerInfo());
    }

    private Node createSummary() {
        Label protection = new Label();
        protection.textProperty().bind(Bindings.when(controller.protectionEnabledProperty())
                .then("ACTIVE").otherwise("DISABLED"));
        Label attack = new Label();
        attack.textProperty().bind(Bindings.createStringBinding(
                () -> controller.attackStatusProperty().get().name().replace('_', ' '),
                controller.attackStatusProperty()));
        Label uptime = new Label();
        uptime.textProperty().bind(controller.uptimeProperty());
        HBox summary = new HBox(0,
                summaryItem("PROTECTION", "fas-shield-alt", protection),
                summaryItem("ATTACK STATUS", "fas-bullseye", attack),
                summaryItem("UPTIME", "far-clock", uptime));
        summary.getStyleClass().add("panel");
        summary.setPadding(new Insets(14, 8, 14, 8));
        return summary;
    }

    private Node createLogPanel() {
        Label title = UiIcons.graphic(new Label("SECURITY LOG"), "fas-file-alt", "section-icon");
        title.getStyleClass().add("section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = UiIcons.graphic(new Button("Clear"), "fas-trash-alt", "button-icon");
        clear.getStyleClass().addAll("small-button", "action-button", "outline-button");
        clear.setOnAction(event -> controller.clearLogs());
        Button export = UiIcons.graphic(new Button("Export"), "fas-download", "button-icon");
        export.getStyleClass().addAll("small-button", "action-button", "outline-button");
        export.setTooltip(new Tooltip("File export will be provided by SecurityLogger module"));
        export.setOnAction(event -> controller.appendLog("INFO", "SERVER", "EXPORT",
                "SecurityLogger module will own file export"));
        HBox header = new HBox(8, title, spacer, clear, export);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox filters = new HBox(6,
                filter("ALL"), filter("INFO"), filter("TRAFFIC"),
                filter("WARNING"), filter("ERROR"), filter("DEFENSE"));

        ListView<String> logView = new ListView<>(controller.getLogs());
        logView.getStyleClass().add("log-list");
        logView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : colouredLogLine(item));
            }
        });
        controller.getLogs().addListener((javafx.collections.ListChangeListener<String>) change -> {
            if (!controller.getLogs().isEmpty()) {
                logView.scrollTo(controller.getLogs().size() - 1);
            }
        });
        VBox.setVgrow(logView, Priority.ALWAYS);

        VBox panel = new VBox(8, header, filters, logView);
        panel.setPadding(new Insets(10));
        panel.setPrefHeight(385);
        panel.setMinHeight(300);
        panel.setMaxHeight(405);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createActions() {
        Label title = UiIcons.graphic(new Label("QUICK ACTIONS"), "fas-bolt", "section-icon");
        title.getStyleClass().add("section-title");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        Button lifecycle = actionButton("Stop Server", "success-button", "fas-play");
        lifecycle.textProperty().bind(Bindings.when(controller.serverRunningProperty())
                .then("Stop Server").otherwise("Start Server"));
        updateLifecycleButton(lifecycle, controller.serverRunningProperty().get());
        controller.serverRunningProperty().addListener((observable, oldValue, running) ->
                updateLifecycleButton(lifecycle, running));
        lifecycle.setOnAction(event -> {
            if (controller.serverRunningProperty().get()) controller.stopServer();
            else controller.startServer(controller.getConfig().getServerPort());
        });
        Button reset = actionButton("Reset Statistics", "outline-button", "fas-sync-alt");
        reset.setOnAction(event -> controller.resetStatistics());
        Button enable = actionButton("Enable Protection", "cyan-button", "fas-shield-alt");
        enable.setOnAction(event -> controller.setProtectionEnabled(true));
        Button disable = actionButton("Disable Protection", "outline-button", "fas-shield-alt");
        disable.setOnAction(event -> controller.setProtectionEnabled(false));
        grid.add(lifecycle, 0, 0);
        grid.add(reset, 1, 0);
        grid.add(enable, 0, 1);
        grid.add(disable, 1, 1);
        GridPane.setHgrow(lifecycle, Priority.ALWAYS);
        GridPane.setHgrow(reset, Priority.ALWAYS);
        GridPane.setHgrow(enable, Priority.ALWAYS);
        GridPane.setHgrow(disable, Priority.ALWAYS);

        VBox panel = new VBox(10, title, grid);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Node createServerInfo() {
        Node icon = UiIcons.icon("fas-server", "server-icon-small");
        Label title = new Label("Server Information");
        title.getStyleClass().add("detail-label");
        Label keys = new Label("IP:\nPort:\nURL:");
        keys.getStyleClass().add("info-key");
        Label values = new Label();
        values.textProperty().bind(Bindings.concat(controller.serverIpProperty(), "\n",
                controller.serverPortProperty(), "\nhttp://", controller.serverIpProperty(), ":",
                controller.serverPortProperty(), "/"));
        values.getStyleClass().add("info-value");
        HBox details = new HBox(22, keys, values);
        VBox text = new VBox(5, title, details);
        HBox box = new HBox(20, icon, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(13, 24, 13, 24));
        box.getStyleClass().add("panel");
        return box;
    }

    private VBox summaryItem(String title, String icon, Label value) {
        Label heading = new Label(title);
        heading.getStyleClass().add("summary-title");
        value.setGraphic(UiIcons.icon(icon, "summary-icon"));
        value.setGraphicTextGap(8);
        value.getStyleClass().add("summary-value");
        VBox box = new VBox(7, heading, value);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("summary-item");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private Label filter(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("log-filter", "filter-" + text.toLowerCase());
        return label;
    }

    /** Renders each log field separately so its semantic colour remains readable at a glance. */
    private static TextFlow colouredLogLine(String entry) {
        String[] fields = entry.split("\\s*\\|\\s*", 5);
        String timestamp = field(fields, 0);
        String level = field(fields, 1);
        String ip = field(fields, 2);
        String event = field(fields, 3);
        String description = field(fields, 4);
        String levelStyle = levelStyle(level);

        TextFlow line = new TextFlow(
                logText("[" + timestamp + "]  ", "log-time"),
                logText("[" + level + "]  ", levelStyle),
                logText(ip + "  ", "log-ip"),
                logText("|  ", "log-separator"),
                logText(event + "  ", levelStyle),
                logText("|  ", "log-separator"),
                logText(description, "log-description"));
        line.getStyleClass().add("log-flow");
        return line;
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "-";
    }

    private static String levelStyle(String level) {
        return switch (level) {
            case "WARNING" -> "log-level-warning";
            case "ALERT", "ERROR" -> "log-level-alert";
            case "DEFENSE" -> "log-level-defense";
            case "TRAFFIC" -> "log-level-traffic";
            default -> "log-level-info";
        };
    }

    private static Text logText(String value, String styleClass) {
        Text text = new Text(value);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private Button actionButton(String text, String styleClass, String icon) {
        Button button = UiIcons.graphic(new Button(text), icon, "button-icon");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setPrefHeight(42);
        button.getStyleClass().addAll("action-button", styleClass);
        return button;
    }

    private static void updateLifecycleButton(Button button, boolean running) {
        button.getStyleClass().removeAll("success-button", "danger-button");
        button.getStyleClass().add(running ? "danger-button" : "success-button");
        button.setGraphic(UiIcons.icon(running ? "fas-stop" : "fas-play", "button-icon"));
    }
}
