package server.view;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import server.auth.AccountService;

/** Registration and login view. All JDBC and password hashing work stays off the FX thread. */
public final class RegistrationPage extends BorderPane implements AutoCloseable {
    private final AccountService accounts;
    private final Consumer<String> onAuthenticated;
    private final ProtectionBackdrop backdrop = new ProtectionBackdrop();
    private final ExecutorService databaseWorker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("account-database").factory());
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final TextField username = new TextField();
    private final TextField email = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirmation = new PasswordField();
    private final Label feedback = new Label();
    private final Label databaseState = new Label("Đang kết nối PostgreSQL…");
    private final Label subtitle = new Label();
    private final Text titleAction = new Text();
    private final Text titleAccent = new Text(" tài khoản");
    private final Button submit = new Button();
    private final Hyperlink switchMode = new Hyperlink();
    private final Label existingAccount = new Label();
    private final Label socialPrompt = new Label();
    private final HBox emailRow;
    private final HBox confirmationRow;
    private boolean loginMode;
    private double dragX;
    private double dragY;

    public RegistrationPage(AccountService accounts, Consumer<String> onAuthenticated) {
        this.accounts = accounts;
        this.onAuthenticated = onAuthenticated;
        getStyleClass().add("auth-root");
        getStylesheets().add(RegistrationPage.class.getResource("/server/view/auth.css").toExternalForm());
        setTop(windowBar());

        StackPane artwork = createArtwork();
        HBox.setHgrow(artwork, Priority.ALWAYS);
        // The source PNG is 1536x1536. An explicit zero minimum prevents its
        // intrinsic size from consuming the whole HBox and pushing the form
        // outside the window.
        artwork.setMinSize(0, 0);
        artwork.setPrefSize(620, 700);
        artwork.setMaxWidth(760);

        Node shield = createEmblem();
        titleAction.getStyleClass().add("auth-heading-white");
        titleAccent.getStyleClass().add("auth-heading-accent");
        TextFlow heading = new TextFlow(titleAction, titleAccent);
        subtitle.getStyleClass().add("auth-subtitle");
        subtitle.setWrapText(true);
        VBox headingText = new VBox(8, heading, subtitle);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        HBox headingRow = new HBox(16, shield, headingText);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        headingRow.getStyleClass().add("auth-card-header");

        Region accent = new Region();
        accent.getStyleClass().add("auth-divider");
        username.setId("account-username");
        email.setId("account-email");
        password.setId("account-password");
        confirmation.setId("account-confirmation");
        username.setPromptText("Tên đăng nhập");
        email.setPromptText("Email của bạn");
        password.setPromptText("Mật khẩu");
        confirmation.setPromptText("Nhập lại mật khẩu");
        username.setOnAction(event -> submit.fire());
        email.setOnAction(event -> submit.fire());
        password.setOnAction(event -> submit.fire());
        confirmation.setOnAction(event -> submit.fire());
        HBox usernameRow = field("fas-user", username);
        emailRow = field("fas-envelope", email);
        HBox passwordRow = passwordRow(password);
        confirmationRow = passwordRow(confirmation);
        VBox fields = new VBox(13, usernameRow, emailRow, passwordRow, confirmationRow);
        fields.disableProperty().bind(busy);

        submit.setId("account-submit");
        submit.getStyleClass().add("auth-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDefaultButton(true);
        submit.disableProperty().bind(busy);
        submit.setOnAction(event -> submit());

        feedback.setId("account-feedback");
        feedback.getStyleClass().add("auth-feedback");
        feedback.setWrapText(true);
        feedback.setMinHeight(28);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setMaxSize(18, 18);
        progress.visibleProperty().bind(busy);
        progress.managedProperty().bind(busy);
        HBox progressLine = new HBox(8, progress, feedback);
        progressLine.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(feedback, Priority.ALWAYS);

        Region line1 = new Region(), line2 = new Region();
        line1.getStyleClass().add("auth-social-line");
        line2.getStyleClass().add("auth-social-line");
        HBox.setHgrow(line1, Priority.ALWAYS);
        HBox.setHgrow(line2, Priority.ALWAYS);
        socialPrompt.getStyleClass().add("auth-secondary-text");
        HBox separator = new HBox(14, line1, socialPrompt, line2);
        separator.setAlignment(Pos.CENTER);
        HBox social = new HBox(22,
                socialButton("Google", circularAssetImage("gg.png", 27,
                        "auth-social-image")),
                socialButton("GitHub", UiIcons.icon("fab-github", "auth-social-icon")));
        social.setAlignment(Pos.CENTER);
        switchMode.getStyleClass().add("auth-mode-link");
        switchMode.setId("account-switch-mode");
        switchMode.setOnAction(event -> {
            setLoginMode(!loginMode);
            showMessage("", false);
        });
        switchMode.disableProperty().bind(busy);
        existingAccount.getStyleClass().add("auth-secondary-text");
        HBox accountLink = new HBox(4, existingAccount, switchMode);
        accountLink.setAlignment(Pos.CENTER);
        databaseState.getStyleClass().add("auth-db-state");
        databaseState.setWrapText(true);
        databaseState.setAlignment(Pos.CENTER);
        databaseState.setMaxWidth(Double.MAX_VALUE);

        VBox socialSection = new VBox(13, separator, social);
        VBox form = new VBox(19, headingRow, accent, fields, submit,
                socialSection, accountLink, progressLine);
        form.setPadding(new Insets(34, 42, 24, 42));
        form.getStyleClass().add("auth-form");
        form.setMaxWidth(700);
        form.setMinWidth(440);
        form.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane formHost = new StackPane(form);
        formHost.getStyleClass().add("auth-form-host");
        formHost.setPadding(new Insets(26, 12, 26, 12));
        ScrollPane scroll = new ScrollPane(formHost);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("auth-scroll");
        scroll.setMinWidth(0);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        HBox layout = new HBox(20, artwork, scroll);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(8, 38, 18, 32));
        layout.widthProperty().addListener((observable, previous, value) -> {
            double available = value.doubleValue() - layout.getPadding().getLeft()
                    - layout.getPadding().getRight();
            boolean showArtwork = available >= 950;
            scroll.setPrefWidth(showArtwork ? available * .51 : available);
            artwork.setPrefWidth(showArtwork ? available * .49 - layout.getSpacing() : 0);
            artwork.setVisible(showArtwork);
            artwork.setManaged(showArtwork);
        });
        heightProperty().addListener((observable, previous, value) -> {
            getStyleClass().remove("auth-compact");
            if (value.doubleValue() < 850) getStyleClass().add("auth-compact");
        });
        setCenter(new StackPane(backdrop, layout));
        HBox databaseBar = new HBox(databaseState);
        databaseBar.setAlignment(Pos.CENTER);
        databaseBar.setPadding(new Insets(0, 14, 10, 14));
        databaseBar.getStyleClass().add("auth-database-bar");
        setBottom(databaseBar);
        setLoginMode(true);
        // Paint the login screen before starting PostgreSQL discovery.
        Platform.runLater(this::checkDatabase);
    }

    private HBox windowBar() {
        Label brand = UiIcons.graphic(new Label("PROTECTION LAB  /  ACCOUNT ACCESS"),
                "fas-shield-alt", "auth-brand-icon");
        brand.getStyleClass().add("auth-brand");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minimize = windowButton("fas-minus", "Thu nhỏ");
        minimize.setOnAction(event -> stage().setIconified(true));
        Button maximize = windowButton("far-window-maximize", "Phóng to / khôi phục");
        maximize.setOnAction(event -> stage().setMaximized(!stage().isMaximized()));
        Button close = windowButton("fas-times", "Đóng");
        close.getStyleClass().add("auth-window-close");
        close.setOnAction(event -> stage().close());
        HBox bar = new HBox(6, brand, spacer, minimize, maximize, close);
        bar.getStyleClass().add("auth-window-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setOnMousePressed(event -> { dragX = event.getSceneX(); dragY = event.getSceneY(); });
        bar.setOnMouseDragged(event -> {
            if (!stage().isMaximized()) {
                stage().setX(event.getScreenX() - dragX);
                stage().setY(event.getScreenY() - dragY);
            }
        });
        return bar;
    }

    private Node createEmblem() {
        ImageView logo = assetImage("header-normal-logo-v2.png", 94, 94,
                "auth-emblem-image");
        StackPane badge = new StackPane(logo);
        badge.getStyleClass().add("auth-emblem");
        badge.setMinSize(96, 100);
        badge.setPrefSize(96, 100);
        badge.setMaxSize(96, 100);
        return badge;
    }

    private StackPane createArtwork() {
        ImageView map = assetImage("blue-network-map.png", 0, 0,
                "auth-network-map");
        ImageView hacker = assetImage("hacker-quick-actions.png", 0, 0,
                "auth-artwork-image");
        map.setMouseTransparent(true);
        hacker.setMouseTransparent(true);
        StackPane visual = new StackPane(map, hacker);
        visual.setMinSize(0, 0);
        StackPane caption = createArtworkCaption();
        VBox composition = new VBox(6, visual, caption);
        composition.setAlignment(Pos.CENTER);
        composition.setFillWidth(true);
        composition.setMinSize(0, 0);
        composition.setMaxHeight(Region.USE_PREF_SIZE);
        VBox.setMargin(caption, new Insets(0, 18, 8, 18));

        StackPane host = new StackPane(composition);
        host.getStyleClass().add("auth-artwork-image-host");
        host.setMinSize(0, 0);
        visual.prefHeightProperty().bind(host.widthProperty().multiply(.55));
        visual.maxHeightProperty().bind(host.widthProperty().multiply(.55));
        map.fitWidthProperty().bind(visual.widthProperty().multiply(.98));
        map.fitHeightProperty().bind(visual.heightProperty().multiply(.96));
        hacker.fitWidthProperty().bind(visual.widthProperty().multiply(.72));
        hacker.fitHeightProperty().bind(visual.heightProperty().multiply(.88));
        caption.prefWidthProperty().bind(host.widthProperty().multiply(.86));
        caption.maxWidthProperty().bind(host.widthProperty().multiply(.86));
        return host;
    }

    private StackPane createArtworkCaption() {
        Label title = new Label("DoS/DDoS Protection Server");
        title.getStyleClass().add("auth-artwork-caption-title");
        Label subtitle = new Label("Bảo vệ hệ thống · Ngăn chặn tấn công · Đảm bảo an ninh mạng");
        subtitle.getStyleClass().add("auth-artwork-caption-subtitle");
        VBox text = new VBox(2, title, subtitle);
        text.setAlignment(Pos.CENTER);
        StackPane caption = new StackPane(text);
        caption.getStyleClass().add("auth-artwork-caption");
        caption.setMinSize(0, 62);
        caption.setPrefHeight(68);
        caption.setMaxHeight(68);
        caption.setMouseTransparent(true);
        return caption;
    }

    private static ImageView circularAssetImage(String filename, double size,
                                                String styleClass) {
        ImageView image = assetImage(filename, size, size, styleClass);
        image.setClip(new Circle(size / 2, size / 2, size / 2));
        return image;
    }

    private static ImageView assetImage(String filename, double width, double height,
                                        String styleClass) {
        var resource = RegistrationPage.class.getResource("/server/view/assets/" + filename);
        if (resource == null) {
            throw new IllegalStateException("Missing authentication asset: " + filename);
        }
        ImageView image = new ImageView(new Image(resource.toExternalForm()));
        image.setSmooth(true);
        image.setPreserveRatio(true);
        if (width > 0) image.setFitWidth(width);
        if (height > 0) image.setFitHeight(height);
        image.getStyleClass().add(styleClass);
        return image;
    }

    private Button windowButton(String icon, String name) {
        Button button = UiIcons.graphic(new Button(), icon, "auth-window-icon");
        button.getStyleClass().add("auth-window-button");
        button.setAccessibleText(name);
        button.setTooltip(new Tooltip(name));
        return button;
    }

    private HBox field(String icon, TextField text) {
        text.getStyleClass().add("auth-input");
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(14, UiIcons.icon(icon, "auth-input-icon"), text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("auth-input-row");
        return row;
    }

    private HBox passwordRow(PasswordField hidden) {
        TextField visible = new TextField();
        visible.setPromptText(hidden.getPromptText());
        visible.textProperty().bindBidirectional(hidden.textProperty());
        hidden.getStyleClass().add("auth-input");
        visible.getStyleClass().add("auth-input");
        hidden.setMinWidth(0);
        visible.setMinWidth(0);
        visible.setVisible(false);
        visible.setManaged(false);
        StackPane inputs = new StackPane(hidden, visible);
        inputs.setMinWidth(0);
        HBox.setHgrow(inputs, Priority.ALWAYS);
        Button reveal = UiIcons.graphic(new Button(), "fas-eye-slash", "auth-input-icon");
        reveal.getStyleClass().add("auth-reveal");
        reveal.setAccessibleText("Hiện hoặc ẩn mật khẩu");
        reveal.setOnAction(event -> {
            boolean show = !visible.isVisible();
            visible.setVisible(show);
            visible.setManaged(show);
            hidden.setVisible(!show);
            hidden.setManaged(!show);
            reveal.setGraphic(UiIcons.icon(show ? "fas-eye" : "fas-eye-slash", "auth-input-icon"));
        });
        HBox row = new HBox(14, UiIcons.icon("fas-lock", "auth-input-icon"), inputs, reveal);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("auth-input-row");
        return row;
    }

    private Button socialButton(String text, Node graphic) {
        Button button = new Button(text, graphic);
        button.getStyleClass().add("auth-social-button");
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
        button.setDisable(true);
        button.setTooltip(new Tooltip("Cần cấu hình OAuth " + text + " để sử dụng."));
        return button;
    }

    private void setLoginMode(boolean login) {
        loginMode = login;
        titleAction.setText(login ? "ĐĂNG NHẬP" : "ĐĂNG KÝ");
        titleAccent.setText(login ? " HỆ THỐNG" : " TÀI KHOẢN");
        subtitle.setText(login ? "Chào mừng trở lại hệ thống bảo vệ mạng"
                : "Tham gia hệ thống và bắt đầu trải nghiệm");
        username.setPromptText(login ? "Tên đăng nhập hoặc email" : "Tên đăng nhập");
        emailRow.setVisible(!login);
        emailRow.setManaged(!login);
        confirmationRow.setVisible(!login);
        confirmationRow.setManaged(!login);
        submit.setText(login ? "Đăng nhập" : "Đăng ký");
        submit.setGraphic(UiIcons.icon(login ? "fas-sign-in-alt" : "fas-user-plus", "auth-submit-icon"));
        existingAccount.setText(login ? "Chưa có tài khoản?" : "Đã có tài khoản?");
        switchMode.setText(login ? "Đăng ký ngay" : "Đăng nhập ngay");
        socialPrompt.setText(login ? "Hoặc đăng nhập với" : "Hoặc đăng ký với");
        password.clear();
        confirmation.clear();
    }

    private void submit() {
        if (busy.get()) return;
        boolean login = loginMode;
        String name = username.getText();
        String address = email.getText();
        char[] secret = password.getText().toCharArray();
        char[] repeated = confirmation.getText().toCharArray();
        password.clear();
        confirmation.clear();
        busy.set(true);
        showMessage(login ? "Đang đăng nhập…" : "Đang tạo tài khoản…", false);
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception {
                try {
                    return login ? accounts.login(name, secret) : accounts.register(name, address, secret, repeated);
                } finally {
                    Arrays.fill(secret, '\0');
                    Arrays.fill(repeated, '\0');
                }
            }
        };
        task.setOnSucceeded(event -> {
            busy.set(false);
            if (login) onAuthenticated.accept(task.getValue());
            else {
                username.setText(task.getValue());
                setLoginMode(true);
                showMessage("Đăng ký thành công! Hãy đăng nhập để vào Dashboard.", false);
                password.requestFocus();
            }
        });
        task.setOnFailed(event -> {
            busy.set(false);
            Throwable failure = task.getException();
            showMessage(failure instanceof SQLException sql ? AccountService.connectionError(sql)
                    : failure instanceof IllegalArgumentException ? failure.getMessage()
                    : "Không thể xử lý tài khoản. Vui lòng thử lại.", true);
        });
        databaseWorker.execute(task);
    }

    private void checkDatabase() {
        databaseWorker.execute(() -> {
            try {
                accounts.initialize();
                Platform.runLater(() -> databaseState.setText("● PostgreSQL đã kết nối · Tài khoản được bảo vệ"));
            } catch (SQLException exception) {
                Platform.runLater(() -> databaseState.setText(AccountService.connectionError(exception)));
            }
        });
    }

    private void showMessage(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().remove("auth-error");
        if (error) feedback.getStyleClass().add("auth-error");
    }

    private Stage stage() { return (Stage) getScene().getWindow(); }

    @Override public void close() {
        backdrop.close();
        password.clear();
        confirmation.clear();
        databaseWorker.shutdownNow();
    }
}
