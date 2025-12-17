package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Inline project progress view.
 */
public class ProjectProgressDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private ProjectProgressDialog(List<Task> tasks) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("projects-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("projects-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignF.FOLDER_OPEN, 22);
        icon.getStyleClass().add("projects-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Прогресс Проектов");
        title.getStyleClass().add("projects-title");
        Label subtitle = new Label("Отслеживание задач с подзадачами");
        subtitle.getStyleClass().add("projects-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        LocalDate today = LocalDate.now();
        List<Task> projects = tasks.stream().filter(Task::hasSubtasks).toList();

        if (projects.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(40));
            FontIcon emptyIcon = FontIcon.of(MaterialDesignF.FOLDER_OUTLINE, 48);
            emptyIcon.getStyleClass().add("projects-empty-icon");
            Label emptyLbl = new Label("Нет активных проектов\nСоздайте задачу и добавьте к ней подзадачи.");
            emptyLbl.getStyleClass().add("projects-empty-text");
            emptyBox.getChildren().addAll(emptyIcon, emptyLbl);
            content.getChildren().add(emptyBox);
        } else {
            for (Task project : projects) {
                content.getChildren().add(createProjectCard(project, today));
            }
        }

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(350, 350);
        root.getStyleClass().add("projects-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new ProjectProgressDialog(tasks);
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
        return "Прогресс проектов";
    }

    private VBox createProjectCard(Task project, LocalDate today) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("project-card");

        int total = project.getSubtasks().size();
        int done = (int) project.getSubtasks().stream().filter(Task::isCompleted).count();
        double pct = total > 0 ? (done * 100.0 / total) : 0;

        // Card Header
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        
        Label name = new Label(project.getTitle());
        name.getStyleClass().add("project-card-name");
        name.setWrapText(true);
        HBox.setHgrow(name, Priority.ALWAYS);
        
        Label statusPill = new Label(String.format("%.0f%%", pct));
        statusPill.getStyleClass().addAll("project-status-pill", getStatusClass(pct));
        
        topRow.getChildren().addAll(name, statusPill);

        // Progress Bar
        VBox progressBox = new VBox(6);
        StackPane track = new StackPane();
        track.getStyleClass().add("project-progress-track");
        
        Region fill = new Region();
        fill.getStyleClass().addAll("project-progress-fill", getStatusClass(pct));
        // Bind width manually or use simple calculation for snapshot
        fill.prefWidthProperty().bind(track.widthProperty().multiply(pct / 100.0));
        fill.setMaxWidth(Double.MAX_VALUE); // Allow binding to work
        
        // Align fill to left
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        track.getChildren().add(fill);
        
        HBox statsRow = new HBox(10);
        Label doneLbl = new Label("Выполнено: " + done + "/" + total);
        doneLbl.getStyleClass().add("project-stats-text");
        progressBox.getChildren().addAll(track, doneLbl);

        // Details Chips
        HBox details = new HBox(10);
        details.getStyleClass().add("project-details");
        
        long daysLeft = ChronoUnit.DAYS.between(today, project.getDeadline());
        String daysText = daysLeft < 0 ? "Просрочен: " + (-daysLeft) + " дн." :
                          daysLeft == 0 ? "Дедлайн сегодня" : "Осталось: " + daysLeft + " дн.";
        
        details.getChildren().addAll(
            chip(project.getDeadline().toString(), MaterialDesignC.CALENDAR),
            chip(daysText, MaterialDesignT.TIMER_SAND)
        );

        // Subtasks List
        VBox subtasksList = new VBox(6);
        subtasksList.getStyleClass().add("project-subtasks");
        
        for (Task sub : project.getSubtasks()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("project-subtask-row");
            
            FontIcon check = FontIcon.of(sub.isCompleted() ? MaterialDesignC.CHECK_CIRCLE : MaterialDesignC.CIRCLE_OUTLINE, 16);
            check.getStyleClass().add(sub.isCompleted() ? "project-subtask-done-icon" : "project-subtask-open-icon");
            
            Label subName = new Label(sub.getTitle());
            subName.getStyleClass().add(sub.isCompleted() ? "project-subtask-done" : "project-subtask-title");
            
            row.getChildren().addAll(check, subName);
            subtasksList.getChildren().add(row);
        }

        card.getChildren().addAll(topRow, progressBox, details, subtasksList);
        return card;
    }

    private String getStatusClass(double pct) {
        if (pct >= 100) return "project-status-done";
        if (pct >= 50) return "project-status-good";
        if (pct >= 25) return "project-status-mid";
        return "project-status-risk";
    }

    private HBox chip(String text, Enum<?> iconEnum) {
        HBox chip = new HBox(6);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("project-detail-chip");
        FontIcon icon = FontIcon.of((Ikon) iconEnum, 14);
        icon.getStyleClass().add("project-detail-icon");
        Label label = new Label(text);
        label.getStyleClass().add("project-detail-text");
        chip.getChildren().addAll(icon, label);
        return chip;
    }
}
