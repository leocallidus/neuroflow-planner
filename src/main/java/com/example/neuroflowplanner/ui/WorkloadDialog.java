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
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inline workload chart view.
 */
public class WorkloadDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private WorkloadDialog(List<Task> tasks) {
        Map<LocalDate, Integer> workload = new HashMap<>();
        Map<LocalDate, Integer> taskCount = new HashMap<>();
        for (Task t : tasks) {
            if (!t.isArchived() && t.getDeadline() != null) {
                workload.merge(t.getDeadline(), t.getComplexity(), Integer::sum);
                taskCount.merge(t.getDeadline(), 1, Integer::sum);
                for (Task sub : t.getSubtasks()) {
                    if (!sub.isArchived() && sub.getDeadline() != null) {
                        workload.merge(sub.getDeadline(), sub.getComplexity(), Integer::sum);
                        taskCount.merge(sub.getDeadline(), 1, Integer::sum);
                    }
                }
            }
        }

        int maxWorkload = workload.values().stream().mapToInt(i -> i).max().orElse(1);
        int totalLoad = workload.values().stream().mapToInt(i -> i).sum();
        int totalTasks = taskCount.values().stream().mapToInt(i -> i).sum();
        double avgDaily = totalLoad / 30.0;

        VBox content = new VBox(25);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("workload-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("workload-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignW.WEIGHT_LIFTER, 22);
        icon.getStyleClass().add("workload-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Прогноз Загруженности");
        title.getStyleClass().add("workload-title");
        Label subtitle = new Label("Анализ нагрузки на следующие 30 дней");
        subtitle.getStyleClass().add("workload-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Metrics Grid ---
        FlowPane summaryGrid = new FlowPane();
        summaryGrid.setHgap(15);
        summaryGrid.setVgap(15);
        summaryGrid.setAlignment(Pos.TOP_LEFT);
        
        summaryGrid.getChildren().addAll(
            createMetricCard("Общая нагрузка", String.valueOf(totalLoad), MaterialDesignW.WEIGHT, "metric-load"),
            createMetricCard("Всего задач", String.valueOf(totalTasks), MaterialDesignC.CLIPBOARD_CHECK, "metric-tasks"),
            createMetricCard("Ср. в день", String.format("%.1f", avgDaily), MaterialDesignC.CHART_LINE, "metric-avg"),
            createMetricCard("Пик нагрузки", String.valueOf(maxWorkload), MaterialDesignC.CHART_BELL_CURVE, "metric-peak")
        );
        content.getChildren().add(summaryGrid);

        // --- Legend ---
        HBox legend = new HBox(15);
        legend.setAlignment(Pos.CENTER_LEFT);
        legend.getStyleClass().add("workload-legend");
        Label legendTitle = new Label("Уровни:");
        legendTitle.getStyleClass().add("workload-legend-title");
        legend.getChildren().addAll(
            legendTitle,
            createLegendItem("Низкая (≤10)", "workload-low"),
            createLegendItem("Средняя (11-20)", "workload-medium"),
            createLegendItem("Высокая (>20)", "workload-high")
        );
        content.getChildren().add(legend);

        // --- Chart ---
        VBox chartBox = new VBox(8);
        chartBox.getStyleClass().add("workload-chart-box");
        
        LocalDate today = LocalDate.now();

        for (int i = 0; i < 30; i++) {
            LocalDate date = today.plusDays(i);
            int load = workload.getOrDefault(date, 0);
            int count = taskCount.getOrDefault(date, 0);

            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 12, 8, 12));
            row.getStyleClass().add("workload-row");
            if (i == 0) row.getStyleClass().add("workload-row-today");

            // Date Box
            VBox dateBox = new VBox(2);
            dateBox.setAlignment(Pos.CENTER_LEFT);
            dateBox.setPrefWidth(70);
            
            Label dateLbl = new Label(date.getDayOfMonth() + " " +
                date.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ru")));
            dateLbl.getStyleClass().add("workload-date");
            
            Label dayLbl = new Label(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, new Locale("ru")));
            dayLbl.getStyleClass().add("workload-day");
            
            dateBox.getChildren().addAll(dateLbl, dayLbl);

            // Progress Bar
            StackPane barContainer = new StackPane();
            HBox.setHgrow(barContainer, Priority.ALWAYS);
            barContainer.setAlignment(Pos.CENTER_LEFT);
            
            ProgressBar pBar = new ProgressBar();
            pBar.setProgress(maxWorkload > 0 ? (double) load / maxWorkload : 0);
            pBar.setMaxWidth(Double.MAX_VALUE);
            pBar.getStyleClass().addAll("workload-bar", getLoadClass(load));
            
            barContainer.getChildren().add(pBar);

            // Stats
            Label statsLbl = new Label(load > 0 ? load + " ед. (" + count + " зад.)" : "-");
            statsLbl.getStyleClass().add("workload-stats");
            statsLbl.setPrefWidth(100);
            statsLbl.setAlignment(Pos.CENTER_RIGHT);

            row.getChildren().addAll(dateBox, barContainer, statsLbl);
            chartBox.getChildren().add(row);
        }
        
        content.getChildren().add(chartBox);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        InlineLayoutSupport.makeShrinkable(root, content);
        root.getStyleClass().add("workload-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new WorkloadDialog(tasks);
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
        return "Загруженность";
    }

    private VBox createMetricCard(String label, String value, org.kordamp.ikonli.Ikon iconCode, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        card.setPrefWidth(150);
        card.getStyleClass().addAll("workload-metric-card", styleClass);
        
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("workload-metric-icon");
        Label valLbl = new Label(value);
        valLbl.getStyleClass().add("workload-metric-value");
        top.getChildren().addAll(icon, valLbl);
        
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("workload-metric-label");
        
        card.getChildren().addAll(top, labelLbl);
        return card;
    }

    private HBox createLegendItem(String text, String styleClass) {
        HBox item = new HBox(6);
        item.setAlignment(Pos.CENTER_LEFT);
        
        Region dot = new Region();
        dot.setPrefSize(10, 10);
        dot.getStyleClass().addAll("workload-legend-dot", styleClass);
        
        Label lbl = new Label(text);
        lbl.getStyleClass().add("workload-legend-text");
        
        item.getChildren().addAll(dot, lbl);
        return item;
    }

    private String getLoadClass(int load) {
        if (load == 0) return "workload-bar-empty";
        if (load <= 10) return "workload-bar-low";
        if (load <= 20) return "workload-bar-medium";
        return "workload-bar-high";
    }
}
