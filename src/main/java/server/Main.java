package server;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import server.config.ServerConfig;
import server.controller.DashboardController;
import server.view.Dashboard;

public final class Main extends Application {
    private DashboardController controller;

    @Override
    public void start(Stage stage) {
        ServerConfig config = ServerConfig.defaults();
        controller = new DashboardController(config);
        Dashboard dashboard = new Dashboard(controller);

        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1480, screen.getWidth() - 24);
        double height = Math.min(900, screen.getHeight() - 24);
        Scene scene = new Scene(dashboard, width, height);
        scene.getStylesheets().add(
                Main.class.getResource("/server/view/dashboard.css").toExternalForm());

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("DoS/DDoS Protection Server - Machine 2");
        stage.setMinWidth(Math.min(1180, screen.getWidth()));
        stage.setMinHeight(Math.min(700, screen.getHeight()));
        stage.setScene(scene);
        stage.setX(screen.getMinX() + (screen.getWidth() - width) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - height) / 2);
        stage.setOnCloseRequest(event -> controller.close());
        stage.show();

        controller.startUiUpdates();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
