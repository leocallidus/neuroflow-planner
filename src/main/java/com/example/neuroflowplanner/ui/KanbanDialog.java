package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.List;

/**
 * Inline Kanban board view.
 */
public class KanbanDialog implements InlineView {

    private final List<Task> tasks;
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final VBox todoColumn = new VBox(12);
    private final VBox inProgressColumn = new VBox(12);
    private final VBox doneColumn = new VBox(12);
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final ScrollPane scrollPane;
    private final VBox root;
    private Runnable closeAction;

    private KanbanDialog(List<Task> tasks) {
        this.tasks = tasks;
        
        root = new VBox(0);
        root.getStyleClass().add("kanban-root");

        // --- Header (компактный для низких разрешений) ---
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 16, 8, 16));
        header.getStyleClass().add("kanban-header-panel");

        FontIcon icon = FontIcon.of(MaterialDesignC.CLIPBOARD_LIST, 18);
        icon.getStyleClass().add("kanban-icon");

        Label title = new Label("Канбан-доска");
        title.getStyleClass().add("kanban-title");

        header.getChildren().addAll(icon, title);
        root.getChildren().add(header);

        // --- Board Columns ---
        HBox board = new HBox(12);
        board.setPadding(new Insets(12));
        board.setFillHeight(true);
        board.getStyleClass().add("kanban-board");
        
        board.getChildren().addAll(
            createColumn("К Выполнению", todoColumn, "kanban-header-todo"),
            createColumn("В Работе", inProgressColumn, "kanban-header-progress"),
            createColumn("Готово", doneColumn, "kanban-header-done")
        );

        refreshColumns();

        scrollPane = new ScrollPane(board);
        scrollPane.setFitToHeight(true);
        // Не fitToWidth - позволяем горизонтальный скролл на узких экранах
        scrollPane.setFitToWidth(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("kanban-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        root.getChildren().add(scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new KanbanDialog(tasks);
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return null;
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Канбан-доска";
    }

    private VBox createColumn(String title, VBox content, String headerStyleClass) {
        VBox column = new VBox(0);
        // Фиксированная ширина колонок для стабильного отображения
        column.setPrefWidth(220);
        column.setMinWidth(180);
        column.setMaxWidth(280);
        column.getStyleClass().add("kanban-column");
        // Убираем HGrow - колонки фиксированной ширины

        HBox header = new HBox(6);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.getStyleClass().addAll("kanban-column-header", headerStyleClass);
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kanban-column-title");
        
        header.getChildren().addAll(titleLabel);

        content.setPadding(new Insets(8));
        content.setMinHeight(80);

        ScrollPane columnScroll = new ScrollPane(content);
        columnScroll.setFitToWidth(true);
        columnScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        columnScroll.getStyleClass().add("kanban-column-scroll");
        VBox.setVgrow(columnScroll, Priority.ALWAYS);

        column.getChildren().addAll(header, columnScroll);
        return column;
    }

    private void refreshColumns() {
        todoColumn.getChildren().clear();
        inProgressColumn.getChildren().clear();
        doneColumn.getChildren().clear();

        for (Task task : tasks) {
            if (task.isArchived()) {
                doneColumn.getChildren().add(createCard(task));
            } else if (task.getSmartPriority() >= 5) {
                inProgressColumn.getChildren().add(createCard(task));
            } else {
                todoColumn.getChildren().add(createCard(task));
            }
        }
    }

    private VBox createCard(Task task) {
        VBox card = new VBox(4);
        card.getStyleClass().add("kanban-card");

        // Top bar: Priority indicator + Title
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        
        String priorityClass = task.getSmartPriority() >= 7 ? "kanban-priority-high" :
                               task.getSmartPriority() >= 4 ? "kanban-priority-medium" : "kanban-priority-low";
        
        Label priorityDot = new Label();
        priorityDot.getStyleClass().addAll("kanban-priority-dot", priorityClass);
        
        Label titleLabel = new Label(task.getTitle());
        titleLabel.getStyleClass().add("kanban-card-title");
        titleLabel.setWrapText(true);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        
        top.getChildren().addAll(priorityDot, titleLabel);

        // Meta info: Date + Actions в одной строке
        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon calIcon = FontIcon.of(MaterialDesignC.CALENDAR, 10);
        calIcon.getStyleClass().add("kanban-meta-icon");
        
        Label deadlineLabel = new Label(task.getDeadline().toString());
        deadlineLabel.getStyleClass().add("kanban-card-meta");
        
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button leftBtn = new Button();
        FontIcon leftIcon = FontIcon.of(MaterialDesignA.ARROW_LEFT, 12);
        leftBtn.setGraphic(leftIcon);
        leftBtn.getStyleClass().add("kanban-action-btn");
        leftBtn.setTooltip(new Tooltip("Назад"));
        leftBtn.setOnAction(e -> moveTask(task, -1));

        Button rightBtn = new Button();
        FontIcon rightIcon = FontIcon.of(MaterialDesignA.ARROW_RIGHT, 12);
        rightBtn.setGraphic(rightIcon);
        rightBtn.getStyleClass().add("kanban-action-btn");
        rightBtn.setTooltip(new Tooltip("Вперед"));
        rightBtn.setOnAction(e -> moveTask(task, 1));
        
        // Logic to hide buttons based on column
        boolean inTodo = !task.isArchived() && task.getSmartPriority() < 5;
        boolean inDone = task.isArchived();

        bottomRow.getChildren().addAll(calIcon, deadlineLabel, spacer);
        if (!inTodo) bottomRow.getChildren().add(leftBtn);
        if (!inDone) bottomRow.getChildren().add(rightBtn);

        card.getChildren().addAll(top, bottomRow);
        return card;
    }

    private void moveTask(Task task, int direction) {
        boolean inTodo = !task.isArchived() && task.getSmartPriority() < 5;
        boolean inProgress = !task.isArchived() && task.getSmartPriority() >= 5;
        boolean inDone = task.isArchived();

        if (direction < 0) {
            if (inDone) {
                task.setArchived(false);
                task.setSmartPriority(6);
            } else if (inProgress) {
                task.setSmartPriority(2);
            }
        } else {
            if (inTodo) {
                task.setSmartPriority(6);
            } else if (inProgress) {
                task.setArchived(true);
            }
        }
        db.saveTask(task);
        refreshColumns();
    }
}
