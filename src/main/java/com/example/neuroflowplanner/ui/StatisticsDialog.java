package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline statistics view.
 */
public class StatisticsDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private StatisticsDialog(List<Task> tasks) {
        List<Task> allTasks = new ArrayList<>();
        for (Task t : tasks) {
            allTasks.add(t);
            allTasks.addAll(t.getSubtasks());
        }

        int total = allTasks.size();
        int completed = (int) allTasks.stream().filter(Task::isCompleted).count();
        int archived = (int) allTasks.stream().filter(Task::isArchived).count();
        int active = (int) allTasks.stream().filter(t -> !t.isArchived() && !t.isCompleted()).count();
        int urgent = (int) allTasks.stream().filter(t -> !t.isArchived() && !t.isCompleted() && t.getSmartPriority() >= 7).count();
        int overdue = (int) allTasks.stream().filter(t -> !t.isArchived() && !t.isCompleted() && t.getDeadline().isBefore(LocalDate.now())).count();
        
        // Статистика по выполнению в срок
        int completedOnTime = (int) allTasks.stream()
            .filter(t -> t.isCompleted() && t.getCompletedDate() != null)
            .filter(t -> !t.getCompletedDate().isAfter(t.getDeadline()))
            .count();
        int completedLate = completed - completedOnTime;

        VBox content = new VBox(25);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("stats-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("stats-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignC.CHART_BAR, 22);
        icon.getStyleClass().add("stats-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Статистика");
        title.getStyleClass().add("stats-title");
        Label subtitle = new Label("Обзор вашей продуктивности");
        subtitle.getStyleClass().add("stats-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Key Metrics Grid ---
        FlowPane summaryGrid = new FlowPane();
        summaryGrid.setHgap(15);
        summaryGrid.setVgap(15);
        summaryGrid.setAlignment(Pos.TOP_LEFT);
        
        summaryGrid.getChildren().addAll(
            createStatCard("Всего задач", total, MaterialDesignC.CLIPBOARD_LIST, "stat-card-total"),
            createStatCard("В работе", active, MaterialDesignP.PLAY_CIRCLE_OUTLINE, "stat-card-active"),
            createStatCard("Выполнено", completed, MaterialDesignC.CHECK_CIRCLE, "stat-card-done"),
            createStatCard("В срок", completedOnTime, MaterialDesignC.CHECK_ALL, "stat-card-ontime"),
            createStatCard("С опозданием", completedLate, MaterialDesignC.CLOCK_ALERT_OUTLINE, "stat-card-late"),
            createStatCard("Срочные", urgent, MaterialDesignP.PRIORITY_HIGH, "stat-card-urgent"),
            createStatCard("Просрочены", overdue, MaterialDesignC.CLOCK_ALERT, "stat-card-overdue"),
            createStatCard("В архиве", archived, MaterialDesignA.ARCHIVE, "stat-card-archived")
        );
        content.getChildren().add(summaryGrid);

        // --- Priority Distribution ---
        VBox distributionBox = new VBox(15);
        distributionBox.getStyleClass().add("stats-section-box");
        
        Label distTitle = new Label("Распределение по приоритетам");
        distTitle.getStyleClass().add("stats-section-title");
        distributionBox.getChildren().add(distTitle);

        int low = (int) allTasks.stream().filter(t -> t.getSmartPriority() < 4).count();
        int medium = (int) allTasks.stream().filter(t -> t.getSmartPriority() >= 4 && t.getSmartPriority() < 7).count();
        int high = (int) allTasks.stream().filter(t -> t.getSmartPriority() >= 7).count();
        int maxVal = Math.max(1, Math.max(low, Math.max(medium, high)));

        distributionBox.getChildren().add(createBarChartRow("Низкий", low, maxVal, "chart-bar-low"));
        distributionBox.getChildren().add(createBarChartRow("Средний", medium, maxVal, "chart-bar-medium"));
        distributionBox.getChildren().add(createBarChartRow("Высокий", high, maxVal, "chart-bar-high"));

        content.getChildren().add(distributionBox);

        // --- Completion Rate ---
        VBox statusBox = new VBox(15);
        statusBox.getStyleClass().add("stats-section-box");
        
        Label statusTitle = new Label("Прогресс выполнения");
        statusTitle.getStyleClass().add("stats-section-title");
        
        HBox statusContent = new HBox(20);
        statusContent.setAlignment(Pos.CENTER_LEFT);
        
        double progress = total == 0 ? 0 : (double) completed / total;
        double onTimeRate = completed == 0 ? 0 : (double) completedOnTime / completed;
        
        // Прогресс выполнения
        VBox progressBox = new VBox(8);
        ProgressBar pBar = new ProgressBar(progress);
        pBar.setPrefWidth(200);
        pBar.getStyleClass().add("stats-progress-bar-large");
        
        Label progressLbl = new Label(String.format("%.0f%%", progress * 100));
        progressLbl.getStyleClass().add("stats-progress-value");
        
        VBox textInfo = new VBox(4);
        textInfo.setAlignment(Pos.CENTER_LEFT);
        Label completedLabel = new Label("Выполнено задач");
        completedLabel.getStyleClass().add("stats-label-small");
        textInfo.getChildren().addAll(progressLbl, completedLabel);
        
        HBox progressRow = new HBox(15);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.getChildren().addAll(pBar, textInfo);
        progressBox.getChildren().add(progressRow);
        
        // Процент выполнения в срок
        VBox onTimeBox = new VBox(8);
        ProgressBar onTimeBar = new ProgressBar(onTimeRate);
        onTimeBar.setPrefWidth(200);
        onTimeBar.getStyleClass().add("stats-progress-bar-ontime");
        
        Label onTimeLbl = new Label(String.format("%.0f%%", onTimeRate * 100));
        onTimeLbl.getStyleClass().add("stats-progress-value-green");
        
        VBox onTimeInfo = new VBox(4);
        onTimeInfo.setAlignment(Pos.CENTER_LEFT);
        Label onTimeLabel = new Label("Выполнено в срок");
        onTimeLabel.getStyleClass().add("stats-label-small");
        onTimeInfo.getChildren().addAll(onTimeLbl, onTimeLabel);
        
        HBox onTimeRow = new HBox(15);
        onTimeRow.setAlignment(Pos.CENTER_LEFT);
        onTimeRow.getChildren().addAll(onTimeBar, onTimeInfo);
        onTimeBox.getChildren().add(onTimeRow);

        statusContent.getChildren().addAll(progressBox, onTimeBox);
        statusBox.getChildren().addAll(statusTitle, statusContent);
        
        content.getChildren().add(statusBox);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(400, 350);
        root.getStyleClass().add("stats-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new StatisticsDialog(tasks);
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
        return "Статистика";
    }

    private VBox createStatCard(String label, int value, org.kordamp.ikonli.Ikon iconCode, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setPrefWidth(160);
        card.getStyleClass().addAll("stat-card", styleClass);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = FontIcon.of(iconCode, 20);
        icon.getStyleClass().add("stat-card-icon");
        
        Label valueLbl = new Label(String.valueOf(value));
        valueLbl.getStyleClass().add("stat-value");
        
        top.getChildren().addAll(icon, valueLbl);
        
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("stat-label");

        card.getChildren().addAll(top, labelLbl);
        return card;
    }

    private HBox createBarChartRow(String label, int value, int max, String barClass) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label lbl = new Label(label);
        lbl.setPrefWidth(80);
        lbl.getStyleClass().add("chart-label");

        StackPane barContainer = new StackPane();
        barContainer.getStyleClass().add("chart-track");
        HBox.setHgrow(barContainer, Priority.ALWAYS);
        barContainer.setAlignment(Pos.CENTER_LEFT);
        
        Pane bar = new Pane();
        bar.getStyleClass().addAll("chart-bar", barClass);
        // Calculate width percentage
        double pct = (double) value / max;
        bar.prefWidthProperty().bind(barContainer.widthProperty().multiply(pct));
        bar.setPrefHeight(16);
        
        barContainer.getChildren().add(bar);
        
        Label valLbl = new Label(String.valueOf(value));
        valLbl.getStyleClass().add("chart-value-label");
        valLbl.setPrefWidth(30);
        valLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lbl, barContainer, valLbl);
        return row;
    }
}
