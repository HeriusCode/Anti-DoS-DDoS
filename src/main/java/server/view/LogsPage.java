package server.view;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Locale;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import server.controller.DashboardController;

/** Full searchable and filterable security log page. */
public final class LogsPage extends HBox {
    private static final String ALL_LEVELS = "All Levels";
    private static final String ALL_EVENTS = "All Events";

    private final DashboardController controller;
    private final ObservableList<LogRow> rows = FXCollections.observableArrayList();
    private final ObservableList<LogRow> recentRows = FXCollections.observableArrayList();
    private final FilteredList<LogRow> filteredRows = new FilteredList<>(rows);
    private final ComboBox<String> levelFilter = new ComboBox<>();
    private final ComboBox<String> eventFilter = new ComboBox<>();
    private final TextField search = new TextField();
    private final Label countLabel = new Label();
    private final Label infoCount = new Label("0");
    private final Label warningCount = new Label("0");
    private final Label alertCount = new Label("0");
    private final Label defenseCount = new Label("0");
    private final Label errorCount = new Label("0");

    public LogsPage(DashboardController controller) {
        this.controller = controller;
        getStyleClass().add("logs-page");
        setSpacing(12);
        setPadding(new Insets(14));

        VBox main = new VBox(12, createHeader(), createTablePanel());
        main.setMinWidth(0);
        HBox.setHgrow(main, Priority.ALWAYS);
        VBox right = new VBox(12, createSummary(), createRecentEvents(), createQuickActions());
        right.setPrefWidth(250);
        right.setMinWidth(220);
        getChildren().addAll(main, right);

        configureFilters();
        refreshRows();
        controller.getLogs().addListener((ListChangeListener<String>) change -> refreshRows());
    }

    private Node createHeader() {
        Node icon = UiIcons.icon("fas-file-alt", "logs-hero-icon");
        Label title = new Label("Security Logs");
        title.getStyleClass().add("logs-title");
        Label subtitle = new Label("System events and security logs");
        subtitle.getStyleClass().add("logs-subtitle");
        VBox heading = new VBox(2, title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        levelFilter.setPrefWidth(125);
        eventFilter.setPrefWidth(135);
        Label dateRange = UiIcons.graphic(new Label(
                LocalDate.now() + " 00:00  ~  " + LocalDate.now() + " 23:59"),
                "far-calendar-alt", "logs-filter-icon");
        dateRange.getStyleClass().add("logs-date-range");
        search.setPromptText("Search...");
        search.setPrefWidth(205);
        search.getStyleClass().add("logs-search");
        HBox filters = new HBox(9, levelFilter, eventFilter, dateRange, search);
        filters.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(16, icon, heading, spacer, filters);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 6, 4, 6));
        return header;
    }

    private Node createTablePanel() {
        TableView<LogRow> table = new TableView<>(filteredRows);
        table.setPlaceholder(new Label("No logs match the selected filters"));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("security-log-table");

        TableColumn<LogRow, Number> number = new TableColumn<>("#");
        number.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().number()));
        number.setPrefWidth(42);
        TableColumn<LogRow, String> timestamp = column("Timestamp", LogRow::timestamp);
        timestamp.setPrefWidth(100);
        TableColumn<LogRow, String> level = column("Level", LogRow::level);
        level.setPrefWidth(85);
        level.setCellFactory(column -> levelCell());
        TableColumn<LogRow, String> ip = column("IP Address", LogRow::ip);
        ip.setPrefWidth(115);
        TableColumn<LogRow, String> event = column("Event", LogRow::event);
        event.setPrefWidth(135);
        event.setCellFactory(column -> eventCell());
        TableColumn<LogRow, String> description = column("Description", LogRow::description);
        table.getColumns().addAll(number, timestamp, level, ip, event, description);
        VBox.setVgrow(table, Priority.ALWAYS);

        countLabel.getStyleClass().add("logs-count-label");
        Button previous = new Button("‹");
        Button page = new Button("1");
        Button next = new Button("›");
        previous.getStyleClass().add("logs-page-button");
        page.getStyleClass().addAll("logs-page-button", "selected");
        next.getStyleClass().add("logs-page-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(6, countLabel, spacer, previous, page, next);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("logs-table-footer");

        VBox panel = new VBox(0, table, footer);
        panel.getStyleClass().add("logs-table-panel");
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Node createSummary() {
        VBox entries = new VBox(0,
                summaryRow("Info", "info", infoCount), summaryRow("Warning", "warning", warningCount),
                summaryRow("Alert", "alert", alertCount), summaryRow("Defense", "defense", defenseCount),
                summaryRow("Error", "error", errorCount));
        VBox panel = sidePanel("fas-file-alt", "Log Summary", entries);
        panel.setPrefHeight(235);
        return panel;
    }

    private Node summaryRow(String text, String style, Label count) {
        Label dot = new Label("●");
        dot.getStyleClass().addAll("log-summary-dot", style);
        Label label = new Label(text);
        label.getStyleClass().add("log-summary-name");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        count.getStyleClass().add("log-summary-count");
        HBox row = new HBox(9, dot, label, spacer, count);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("log-summary-row");
        return row;
    }

    private Node createRecentEvents() {
        ListView<LogRow> list = new ListView<>(recentRows);
        list.getStyleClass().add("recent-events-list");
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(LogRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                    return;
                }
                Label time = new Label(row.timestamp());
                time.getStyleClass().add("recent-event-time");
                Label event = new Label(row.event());
                event.getStyleClass().addAll("recent-event-name", levelStyle(row.level()));
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox line = new HBox(6, time, spacer, event);
                line.setAlignment(Pos.CENTER_LEFT);
                setGraphic(line);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox panel = sidePanel("far-clock", "Recent Events", list);
        panel.setPrefHeight(225);
        return panel;
    }

    private Node createQuickActions() {
        Button clear = action("Clear Logs", "fas-trash-alt");
        clear.setOnAction(event -> controller.clearLogs());
        Button export = action("Export Logs", "fas-download");
        export.setOnAction(event -> exportLogs());
        Button refresh = action("Refresh", "fas-sync-alt");
        refresh.setOnAction(event -> refreshRows());
        VBox actions = new VBox(10, clear, export, refresh);
        VBox panel = sidePanel("fas-cog", "Quick Actions", actions);
        VBox.setVgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private void configureFilters() {
        levelFilter.getItems().addAll(ALL_LEVELS, "INFO", "WARNING", "ALERT", "DEFENSE", "ERROR");
        levelFilter.getSelectionModel().selectFirst();
        eventFilter.getItems().add(ALL_EVENTS);
        eventFilter.getSelectionModel().selectFirst();
        levelFilter.valueProperty().addListener((observable, oldValue, value) -> applyFilters());
        eventFilter.valueProperty().addListener((observable, oldValue, value) -> applyFilters());
        search.textProperty().addListener((observable, oldValue, value) -> applyFilters());
    }

    private void refreshRows() {
        String selectedEvent = eventFilter.getValue();
        rows.clear();
        int index = 1;
        for (String entry : controller.getLogs()) rows.add(parse(index++, entry));

        ObservableList<String> eventNames = FXCollections.observableArrayList(ALL_EVENTS);
        rows.stream().map(LogRow::event).distinct().sorted().forEach(eventNames::add);
        eventFilter.getItems().setAll(eventNames);
        eventFilter.getSelectionModel().select(eventNames.contains(selectedEvent) ? selectedEvent : ALL_EVENTS);
        updateSummary();
        updateRecentEvents();
        applyFilters();
    }

    private void applyFilters() {
        String level = levelFilter.getValue();
        String event = eventFilter.getValue();
        String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        filteredRows.setPredicate(row -> (level == null || ALL_LEVELS.equals(level) || level.equals(row.level()))
                && (event == null || ALL_EVENTS.equals(event) || event.equals(row.event()))
                && (query.isEmpty() || row.searchableText().contains(query)));
        int size = filteredRows.size();
        countLabel.setText(size == 0 ? "Showing 0 logs" : "Showing 1 - " + size + " of " + size + " logs");
    }

    private void updateSummary() {
        infoCount.setText(String.valueOf(countLevel("INFO")));
        warningCount.setText(String.valueOf(countLevel("WARNING")));
        alertCount.setText(String.valueOf(countLevel("ALERT")));
        defenseCount.setText(String.valueOf(countLevel("DEFENSE")));
        errorCount.setText(String.valueOf(countLevel("ERROR")));
    }

    private long countLevel(String level) {
        return rows.stream().filter(row -> level.equals(row.level())).count();
    }

    private void updateRecentEvents() {
        recentRows.setAll(rows.stream()
                .filter(row -> !"HTTP_REQUEST".equals(row.event()))
                .sorted(Comparator.comparingInt(LogRow::number).reversed()).limit(6).toList());
    }

    private void exportLogs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Security Logs");
        chooser.setInitialFileName("security.log");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Log files", "*.log"),
                new FileChooser.ExtensionFilter("Text files", "*.txt"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        try {
            Path path = file.toPath();
            Files.write(path, controller.getLogs(), StandardCharsets.UTF_8);
            controller.appendLog("INFO", "SERVER", "LOG_EXPORTED", "Logs exported to " + path.getFileName());
        } catch (IOException exception) {
            controller.appendLog("ERROR", "SERVER", "EXPORT_FAILED", exception.getMessage());
        }
    }

    private VBox sidePanel(String icon, String title, Node content) {
        Label heading = UiIcons.graphic(new Label(title), icon, "logs-side-icon");
        heading.getStyleClass().add("logs-side-title");
        VBox panel = new VBox(10, heading, content);
        panel.setPadding(new Insets(12));
        panel.getStyleClass().add("logs-side-panel");
        return panel;
    }

    private Button action(String text, String icon) {
        Button button = UiIcons.graphic(new Button(text), icon, "button-icon");
        button.getStyleClass().add("logs-action-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    private TableColumn<LogRow, String> column(String title,
            java.util.function.Function<LogRow, String> getter) {
        TableColumn<LogRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private TableCell<LogRow, String> levelCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String level, boolean empty) {
                super.updateItem(level, empty);
                setText(null);
                if (empty || level == null) {
                    setGraphic(null);
                    return;
                }
                Label badge = new Label(level);
                badge.getStyleClass().addAll("log-level-badge", "log-pill-" + levelStyle(level));
                setGraphic(badge);
                setAlignment(Pos.CENTER_LEFT);
            }
        };
    }

    private TableCell<LogRow, String> eventCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String event, boolean empty) {
                super.updateItem(event, empty);
                setText(empty ? null : event);
                getStyleClass().removeAll("event-alert", "event-defense", "event-warning", "event-info");
                if (!empty && getTableRow().getItem() != null) {
                    getStyleClass().add("event-" + levelStyle(getTableRow().getItem().level()));
                }
            }
        };
    }

    private static String levelStyle(String level) {
        return switch (level) {
            case "WARNING" -> "warning";
            case "ALERT" -> "alert";
            case "DEFENSE" -> "defense";
            case "ERROR" -> "error";
            default -> "info";
        };
    }

    private static LogRow parse(int number, String entry) {
        String[] fields = entry.split("\\s*\\|\\s*", 5);
        return new LogRow(number, field(fields, 0), field(fields, 1), field(fields, 2),
                field(fields, 3), field(fields, 4));
    }

    private static String field(String[] fields, int index) {
        return index < fields.length ? fields[index].trim() : "-";
    }

    private record LogRow(int number, String timestamp, String level, String ip,
                          String event, String description) {
        String searchableText() {
            return (timestamp + " " + level + " " + ip + " " + event + " " + description)
                    .toLowerCase(Locale.ROOT);
        }
    }
}
