package server.view;

import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import server.controller.DashboardController;

/** Header card containing endpoint information and server lifecycle controls. */
public final class ServerPanel extends HBox {
    public ServerPanel(DashboardController controller) {
        getStyleClass().addAll("panel", "server-panel");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(14, 20, 14, 20));
        setSpacing(18);

        Node icon = UiIcons.icon("fas-server", "server-icon");

        Label title = new Label("WEB SERVER");
        title.getStyleClass().add("section-title-large");

        Label endpoint = new Label();
        endpoint.textProperty().bind(Bindings.concat("IP: ", controller.serverIpProperty(), "     Port: ",
                controller.serverPortProperty(), "   (HTTP)"));
        endpoint.getStyleClass().add("server-detail");

        Label url = new Label();
        url.textProperty().bind(Bindings.concat("◎  http://", controller.serverIpProperty(), ":",
                controller.serverPortProperty(), "/"));
        url.getStyleClass().add("server-url");
        VBox info = new VBox(4, title, endpoint, url);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button lifecycle = new Button();
        lifecycle.textProperty().bind(Bindings.when(controller.serverRunningProperty())
                .then("Stop Server").otherwise("Start Server"));
        lifecycle.getStyleClass().add("action-button");
        updateLifecycleButton(lifecycle, controller.serverRunningProperty().get());
        controller.serverRunningProperty().addListener((observable, oldValue, running) ->
                updateLifecycleButton(lifecycle, running));
        lifecycle.setOnAction(event -> {
            if (controller.serverRunningProperty().get()) {
                controller.stopServer();
            } else {
                controller.startServer(controller.getConfig().getServerPort());
            }
        });

        Button test = UiIcons.graphic(new Button("Test Connection"),
                "fas-external-link-alt", "button-icon");
        test.getStyleClass().addAll("action-button", "outline-button");
        test.setOnAction(event -> {
            test.setDisable(true);
            test.setText("Testing...");
            controller.testConnection().thenAccept(connected -> Platform.runLater(() -> {
                test.setText(connected ? "Connected" : "Test Failed");
                test.setDisable(false);
                javafx.animation.PauseTransition pause =
                        new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
                pause.setOnFinished(ignored -> test.setText("Test Connection"));
                pause.play();
            }));
        });

        HBox controls = new HBox(10, lifecycle, test);
        controls.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(icon, info, spacer, controls);
    }

    private static void updateLifecycleButton(Button button, boolean running) {
        button.getStyleClass().removeAll("success-button", "danger-button");
        button.getStyleClass().add(running ? "danger-button" : "success-button");
        button.setGraphic(UiIcons.icon(running ? "fas-stop" : "fas-play", "button-icon"));
    }
}
