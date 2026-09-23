package server.view;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import server.controller.DashboardController;

/** Root view for the Machine 2 monitoring dashboard. */
public final class Dashboard extends BorderPane {
    private final DashboardController controller;
    private final StackPane contentHost = new StackPane();
    private final List<Node> menuItems = new ArrayList<>();
    private final Node dashboardPage;
    private final Node serverInfoPage;
    private final Node trafficMonitorPage;
    private final Node detectionPage;
    private final Node protectionPage;
    private final Node logsPage;
    private final Node settingsPage;
    private double dragOffsetX;
    private double dragOffsetY;

    public Dashboard(DashboardController controller) {
        this.controller = controller;
        this.dashboardPage = createContent();
        this.serverInfoPage = new ServerInfoPage(controller);
        this.trafficMonitorPage = new TrafficMonitorPage(controller);
        this.detectionPage = new DetectionPage(controller);
        this.protectionPage = new ProtectionPage(controller);
        this.logsPage = new LogsPage(controller);
        this.settingsPage = new SettingsPage(controller);
        getStyleClass().add("dashboard-root");
        setTop(createHeader());
        setLeft(createSidebar());
        contentHost.getChildren().setAll(dashboardPage);
        setCenter(contentHost);
    }

    private Node createHeader() {
        Node logo = UiIcons.icon("fas-shield-alt", "brand-logo");
        Label title = new Label("DoS/DDoS Protection Server");
        title.getStyleClass().add("brand-title");
        HBox brand = new HBox(12, logo, title);
        brand.setAlignment(Pos.CENTER_LEFT);

        HBox navigation = new HBox(0,
                navItem("Web Server"), navSeparator(), navItem("Traffic Monitor"),
                navSeparator(), navItem("DoS/DDoS Detector"), navSeparator(),
                navItem("Anti-DoS"), navSeparator(), navItem("Security Logger"));
        navigation.setAlignment(Pos.CENTER_LEFT);

        Label statusDot = new Label("●");
        statusDot.getStyleClass().add("online-dot");
        Label status = new Label();
        status.textProperty().bind(Bindings.when(controller.serverRunningProperty())
                .then("SERVER RUNNING").otherwise("SERVER STOPPED"));
        status.getStyleClass().add("header-status");

        Label clock = new Label();
        clock.textProperty().bind(Bindings.concat(
                LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE), "   ", controller.clockProperty()));
        clock.getStyleClass().add("header-clock");

        Button minimize = windowButton("fas-minus", "Minimize");
        minimize.setOnAction(event -> currentStage().setIconified(true));
        Button maximize = windowButton("far-window-maximize", "Maximize / Restore");
        maximize.setOnAction(event -> currentStage().setMaximized(!currentStage().isMaximized()));
        Button close = windowButton("fas-times", "Close");
        close.getStyleClass().add("close-window-button");
        close.setOnAction(event -> currentStage().close());
        HBox windowControls = new HBox(2, minimize, maximize, close);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox centerBar = new HBox(10, navigation, headerSpacer, statusDot, status, divider(), clock);
        centerBar.setAlignment(Pos.CENTER_LEFT);

        BorderPane headerContent = new BorderPane();
        headerContent.setLeft(brand);
        headerContent.setCenter(centerBar);
        BorderPane.setAlignment(brand, Pos.CENTER_LEFT);
        BorderPane.setAlignment(centerBar, Pos.CENTER_LEFT);
        BorderPane.setMargin(centerBar, new Insets(0, 8, 0, 24));
        headerContent.setPadding(new Insets(5, 132, 5, 14));

        StackPane header = new StackPane(headerContent, windowControls);
        StackPane.setAlignment(windowControls, Pos.CENTER_RIGHT);
        StackPane.setMargin(windowControls, new Insets(0, 5, 0, 0));
        header.getStyleClass().add("top-header");
        installWindowDragging(header);
        return header;
    }

    private Node createSidebar() {
        Node dashboard = menuItem("fas-home", "Dashboard", "Overview", true);
        Node serverInfo = menuItem("fas-server", "Server Info", "Web Server", false);
        Node traffic = menuItem("fas-chart-line", "Traffic Monitor", "Real-time traffic", false);
        Node detection = menuItem("fas-shield-alt", "Detection", "DoS/DDoS Detector", false);
        Node protection = menuItem("fas-shield-virus", "Protection", "Anti-DoS / Firewall", false);
        Node logs = menuItem("fas-file-alt", "Logs", "Security logs", false);
        Node settings = menuItem("fas-cog", "Settings", "Configuration", false);
        dashboard.setOnMouseClicked(event -> showPage(dashboardPage, dashboard));
        serverInfo.setOnMouseClicked(event -> showPage(serverInfoPage, serverInfo));
        traffic.setOnMouseClicked(event -> showPage(trafficMonitorPage, traffic));
        detection.setOnMouseClicked(event -> showPage(detectionPage, detection));
        protection.setOnMouseClicked(event -> showPage(protectionPage, protection));
        logs.setOnMouseClicked(event -> showPage(logsPage, logs));
        settings.setOnMouseClicked(event -> showPage(settingsPage, settings));
        VBox menu = new VBox(2, dashboard, serverInfo, traffic, detection, protection, logs, settings);

        Node shield = UiIcons.icon("fas-shield-alt", "sidebar-shield");
        Label secure = new Label("SECURE SERVER");
        secure.getStyleClass().add("sidebar-secure");
        Label motto = new Label("MONITOR  •  DETECT  •  PROTECT");
        motto.getStyleClass().add("sidebar-motto");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox sidebar = new VBox(12, menu, spacer, shield, secure, motto);
        sidebar.setAlignment(Pos.TOP_CENTER);
        sidebar.setPadding(new Insets(18, 12, 24, 12));
        sidebar.setPrefWidth(218);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private Node createContent() {
        VBox mainColumn = new VBox(10,
                new ServerPanel(controller),
                new MonitorPanel(controller));
        mainColumn.setId("main-column");
        HBox.setHgrow(mainColumn, Priority.ALWAYS);
        mainColumn.setMinWidth(780);

        SecurityPanel securityPanel = new SecurityPanel(controller);
        securityPanel.setId("security-column");
        securityPanel.setPrefWidth(400);
        securityPanel.setMinWidth(360);

        HBox layout = new HBox(12, mainColumn, securityPanel);
        layout.setId("dashboard-layout");
        layout.setPadding(new Insets(14));
        layout.getStyleClass().add("dashboard-content");

        ScrollPane scroll = new ScrollPane(layout);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("content-scroll");
        scroll.viewportBoundsProperty().addListener((observable, oldBounds, bounds) ->
                layout.setPrefWidth(bounds.getWidth()));
        return scroll;
    }

    private Label navItem(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("nav-item");
        return label;
    }

    private Label navSeparator() {
        Label label = new Label("|");
        label.getStyleClass().add("nav-separator");
        return label;
    }

    private Label divider() {
        Label label = new Label("│");
        label.getStyleClass().add("header-divider");
        return label;
    }

    private Node menuItem(String icon, String title, String subtitle, boolean selected) {
        Node iconLabel = UiIcons.icon(icon, "menu-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("menu-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("menu-subtitle");
        VBox text = new VBox(2, titleLabel, subtitleLabel);
        HBox item = new HBox(14, iconLabel, text);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(13, 10, 13, 14));
        item.getStyleClass().add("menu-item");
        if (selected) {
            item.getStyleClass().add("selected");
        }
        menuItems.add(item);
        return item;
    }

    private void showPage(Node page, Node selectedMenuItem) {
        menuItems.forEach(item -> item.getStyleClass().remove("selected"));
        selectedMenuItem.getStyleClass().add("selected");
        contentHost.getChildren().setAll(page);
    }

    private Button windowButton(String icon, String tooltip) {
        Button button = UiIcons.graphic(new Button(), icon, "window-icon");
        button.setAccessibleText(tooltip);
        button.getStyleClass().add("window-button");
        return button;
    }

    private Stage currentStage() {
        return (Stage) getScene().getWindow();
    }

    private void installWindowDragging(Node header) {
        header.setOnMousePressed(event -> {
            Stage stage = currentStage();
            if (!stage.isMaximized()) {
                dragOffsetX = event.getSceneX();
                dragOffsetY = event.getSceneY();
            }
        });
        header.setOnMouseDragged(event -> {
            Stage stage = currentStage();
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - dragOffsetX);
                stage.setY(event.getScreenY() - dragOffsetY);
            }
        });
        header.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Stage stage = currentStage();
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }
}
