package server;

import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import server.auth.AccountService;
import server.config.DatabaseConfig;
import server.config.ServerConfig;
import server.controller.DashboardController;
import server.persistence.LabDataStore;
import server.view.Dashboard;
import server.view.RegistrationPage;

public final class Main extends Application {
    private DashboardController controller;
    private RegistrationPage registration;
    private AccountService accounts;
    private DatabaseConfig databaseConfig;

    @Override
    public void start(Stage stage) throws Exception {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double width = Math.min(1480, screen.getWidth() - 24);
        double height = Math.min(900, screen.getHeight() - 24);
        databaseConfig = DatabaseConfig.load();
        accounts = new AccountService(databaseConfig);
        registration = new RegistrationPage(accounts, username -> openDashboard(stage, username));
        Scene scene = new Scene(registration, width, height);
        scene.getStylesheets().add(
                Main.class.getResource("/server/view/dashboard.css").toExternalForm());

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("DoS/DDoS Protection Server - Machine 2");
        stage.setMinWidth(Math.min(960, screen.getWidth()));
        stage.setMinHeight(Math.min(600, screen.getHeight()));
        stage.setScene(scene);
        stage.setX(screen.getMinX() + (screen.getWidth() - width) / 2);
        stage.setY(screen.getMinY() + (screen.getHeight() - height) / 2);
        stage.setOnCloseRequest(event -> closeResources());
        stage.show();
    }

    private void openDashboard(Stage stage, String username) {
        registration.close();
        registration = null;
        controller = new DashboardController(ServerConfig.defaults(),
                new LabDataStore(databaseConfig), username);
        stage.getScene().setRoot(new Dashboard(controller, () -> showLogin(stage)));
        stage.setTitle("DoS/DDoS Protection Server - " + username);
        controller.appendLog("INFO", "SERVER", "LOGIN", "Dashboard session started for " + username);
        controller.startUiUpdates();
    }

    private void showLogin(Stage stage) {
        if (controller != null) {
            controller.close();
            controller = null;
        }
        if (registration != null) registration.close();
        registration = new RegistrationPage(accounts, username -> openDashboard(stage, username));
        stage.getScene().setRoot(registration);
        stage.setTitle("DoS/DDoS Protection Server - Login");
    }

    private void closeResources() {
        if (registration != null) registration.close();
        if (controller != null) controller.close();
    }

    @Override
    public void stop() {
        closeResources();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
