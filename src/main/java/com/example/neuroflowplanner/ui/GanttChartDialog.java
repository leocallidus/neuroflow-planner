package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * Inline Gantt chart view.
 */
public class GanttChartDialog implements InlineView {

    private final VBox root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;
    // Адаптивные размеры для низких разрешений
    private static final double DAY_WIDTH = 32;
    private static final double ROW_HEIGHT = 32;
    private static final double TASK_COL_WIDTH = 160;

    private GanttChartDialog(List<Task> tasks) {
        root = new VBox(0);
        root.getStyleClass().add("gantt-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20));
        header.getStyleClass().add("gantt-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("gantt-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignC.CHART_TIMELINE, 22);
        icon.getStyleClass().add("gantt-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Диаграмма Ганта");
        title.getStyleClass().add("gantt-title");
        Label subtitle = new Label("Визуализация времени и зависимостей");
        subtitle.getStyleClass().add("gantt-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        root.getChildren().add(header);

        // --- Logic for Dates ---
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(3);
        LocalDate maxDate = today.plusDays(14); // Default range

        for (Task task : tasks) {
            if (!task.isArchived() && task.getDeadline() != null) {
                if (task.getDeadline().isAfter(maxDate)) maxDate = task.getDeadline();
                LocalDate startEstimate = task.getDeadline().minusDays(Math.max(1, task.getComplexity()));
                if (startEstimate.isBefore(minDate)) minDate = startEstimate;
            }
        }
        minDate = minDate.minusDays(1);
        maxDate = maxDate.plusDays(3);

        long totalDays = ChronoUnit.DAYS.between(minDate, maxDate);
        
        // --- Timeline Header ---
        HBox timelineHeader = new HBox(0);
        timelineHeader.setPadding(new Insets(0, 0, 0, TASK_COL_WIDTH));
        timelineHeader.getStyleClass().add("gantt-timeline-header");
        
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd", new Locale("ru"));
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM", new Locale("ru"));

        for (int i = 0; i <= totalDays; i++) {
            LocalDate date = minDate.plusDays(i);
            VBox dayCell = new VBox(2);
            dayCell.setPrefWidth(DAY_WIDTH);
            dayCell.setAlignment(Pos.CENTER);
            dayCell.getStyleClass().add("gantt-header-cell");
            
            Label dayLbl = new Label(date.format(dayFormatter));
            dayLbl.getStyleClass().add("gantt-header-day");
            
            Label monthLbl = new Label(date.format(monthFormatter));
            monthLbl.getStyleClass().add("gantt-header-month");
            
            if (date.equals(today)) {
                dayCell.getStyleClass().add("gantt-header-today");
            }
            
            dayCell.getChildren().addAll(dayLbl, monthLbl);
            timelineHeader.getChildren().add(dayCell);
        }

        // --- Chart Content ---
        Pane chartContainer = new Pane(); // Use Pane for absolute positioning of bars
        chartContainer.getStyleClass().add("gantt-chart-area");
        
        VBox rowsContainer = new VBox(0); // Holds the background rows/grid
        
        int rowIdx = 0;
        for (Task task : tasks) {
            if (task.isArchived()) continue;
            createRow(rowsContainer, chartContainer, task, minDate, totalDays, today, rowIdx++, 0);
            for (Task sub : task.getSubtasks()) {
                if (!sub.isArchived()) {
                    createRow(rowsContainer, chartContainer, sub, minDate, totalDays, today, rowIdx++, 1);
                }
            }
        }
        
        // Combine rows background and bars
        StackPane chartStack = new StackPane(rowsContainer, chartContainer);
        chartStack.setAlignment(Pos.TOP_LEFT);
        
        // Add vertical grid lines
        Pane gridOverlay = new Pane();
        gridOverlay.setMouseTransparent(true);
        for (int i = 0; i <= totalDays; i++) {
            Line gridLine = new Line(TASK_COL_WIDTH + (i * DAY_WIDTH), 0, TASK_COL_WIDTH + (i * DAY_WIDTH), rowIdx * ROW_HEIGHT);
            gridLine.getStyleClass().add("gantt-grid-line");
            if (minDate.plusDays(i).equals(today)) {
                gridLine.getStyleClass().add("gantt-grid-line-today");
            }
            gridOverlay.getChildren().add(gridLine);
        }
        chartStack.getChildren().add(gridOverlay);

        // Wrapping in scroll pane
        VBox scrollContent = new VBox(timelineHeader, chartStack);
        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true); // Don't fit width, let it scroll horizontally
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("gantt-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        root.getChildren().add(scrollPane);

        // Styling
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new GanttChartDialog(tasks);
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
        return "Диаграмма Ганта";
    }

    private void createRow(VBox rowsContainer, Pane chartContainer, Task task, LocalDate minDate, long totalDays, LocalDate today, int rowIndex, int indent) {
        // 1. Background Row (Task Name + Empty space)
        HBox rowBox = new HBox(0);
        rowBox.setPrefHeight(ROW_HEIGHT);
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.getStyleClass().add("gantt-row");
        if (rowIndex % 2 != 0) rowBox.getStyleClass().add("gantt-row-odd");

        Label nameLabel = new Label((indent > 0 ? "  └ " : "") + task.getTitle());
        nameLabel.setPrefWidth(TASK_COL_WIDTH);
        nameLabel.setMinWidth(TASK_COL_WIDTH);
        nameLabel.setMaxWidth(TASK_COL_WIDTH);
        nameLabel.setPadding(new Insets(0, 10, 0, 15));
        nameLabel.getStyleClass().add("gantt-task-name");
        
        rowBox.getChildren().add(nameLabel);
        rowsContainer.getChildren().add(rowBox);

        // 2. Bar (positioned absolutely on chartContainer)
        if (task.getDeadline() != null) {
            LocalDate endDate = task.getDeadline();
            // Estimate start date based on complexity or default to 1 day
            int durationDays = Math.max(1, task.getComplexity() / 2); 
            LocalDate startDate = endDate.minusDays(durationDays);
            
            // Clip to view
            if (startDate.isBefore(minDate)) startDate = minDate;
            
            long startOffset = ChronoUnit.DAYS.between(minDate, startDate);
            long endOffset = ChronoUnit.DAYS.between(minDate, endDate);
            
            if (endOffset >= 0) {
                double x = TASK_COL_WIDTH + (startOffset * DAY_WIDTH);
                double width = Math.max((endOffset - startOffset + 1) * DAY_WIDTH - 6, 10); // -6 for margins
                double y = (rowIndex * ROW_HEIGHT) + 8;
                double height = ROW_HEIGHT - 16;

                StackPane bar = new StackPane();
                bar.setLayoutX(x + 3);
                bar.setLayoutY(y);
                bar.setPrefSize(width, height);
                
                String priorityClass = task.getSmartPriority() >= 7 ? "gantt-bar-high" :
                                       task.getSmartPriority() >= 4 ? "gantt-bar-medium" : "gantt-bar-low";
                if (endDate.isBefore(today)) priorityClass = "gantt-bar-overdue";
                
                bar.getStyleClass().addAll("gantt-bar", priorityClass);

                Tooltip t = new Tooltip(task.getTitle() + "\nДедлайн: " + endDate + "\nПриоритет: " + String.format("%.1f", task.getSmartPriority()));
                Tooltip.install(bar, t);

                chartContainer.getChildren().add(bar);
            }
        }
    }
}
