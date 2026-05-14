package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.TaskScheduleFormatter;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignV;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline analytics dashboard.
 */
public class DashboardDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private DashboardDialog(List<Task> tasks) {
        List<Task> all = new ArrayList<>();
        for (Task t : tasks) { all.add(t); all.addAll(t.getSubtasks()); }

        LocalDate today = LocalDate.now();
        int total = all.size();
        int done = (int) all.stream().filter(Task::isArchived).count();
        int active = total - done;
        int overdue = (int) all.stream().filter(t -> !t.isArchived() && t.getDeadline().isBefore(today)).count();
        int dueToday = (int) all.stream().filter(t -> !t.isArchived() && t.getDeadline().equals(today)).count();
        int dueWeek = (int) all.stream().filter(t -> !t.isArchived() && !t.getDeadline().isBefore(today) && t.getDeadline().isBefore(today.plusDays(7))).count();

        VBox content = new VBox(25);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("dashboard-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("dashboard-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignV.VIEW_DASHBOARD, 22);
        icon.getStyleClass().add("dashboard-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Аналитика");
        title.getStyleClass().add("dashboard-title");
        Label subtitle = new Label("Ключевые показатели эффективности");
        subtitle.getStyleClass().add("dashboard-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Metrics Grid ---
        FlowPane metricsGrid = new FlowPane();
        metricsGrid.setHgap(15);
        metricsGrid.setVgap(15);
        metricsGrid.setAlignment(Pos.TOP_LEFT);
        
        metricsGrid.getChildren().addAll(
            createMetricCard("Активные", active, MaterialDesignP.PLAY_CIRCLE_OUTLINE, "metric-active"),
            createMetricCard("Завершено", done, MaterialDesignC.CHECK_CIRCLE_OUTLINE, "metric-done"),
            createMetricCard("Просрочены", overdue, MaterialDesignC.CLOCK_ALERT_OUTLINE, "metric-overdue"),
            createMetricCard("На неделе", dueWeek, MaterialDesignC.CALENDAR_WEEK, "metric-week"),
            createMetricCard("Сегодня", dueToday, MaterialDesignC.CALENDAR_TODAY, "metric-today")
        );
        content.getChildren().add(metricsGrid);

        // --- Gauge Section ---
        HBox gaugesRow = new HBox(30);
        gaugesRow.setAlignment(Pos.CENTER);
        gaugesRow.getStyleClass().add("dashboard-section-box");
        gaugesRow.setPadding(new Insets(20));
        
        gaugesRow.getChildren().addAll(
            createGauge("Прогресс", done, total, "gauge-done"),
            createGauge("Активность", active, total, "gauge-active"),
            createGauge("Риски", overdue, total, "gauge-risk")
        );
        content.getChildren().add(gaugesRow);

        // --- Timeline Section ---
        VBox timelineBox = new VBox(15);
        timelineBox.getStyleClass().add("dashboard-section-box");
        
        HBox timelineHeader = new HBox(8);
        timelineHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon timeIcon = FontIcon.of(MaterialDesignT.TIMELINE_CLOCK, 18);
        timeIcon.getStyleClass().add("dashboard-section-icon");
        Label timelineTitle = new Label("Ближайшие Дедлайны (7 дней)");
        timelineTitle.getStyleClass().add("dashboard-section-title");
        timelineHeader.getChildren().addAll(timeIcon, timelineTitle);
        
        timelineBox.getChildren().add(timelineHeader);

        LocalDate inWeek = today.plusDays(7);
        long upcomingCount = all.stream()
            .filter(t -> !t.isArchived() && t.getDeadline() != null && !t.getDeadline().isAfter(inWeek))
            .count();

        if (upcomingCount == 0) {
            Label emptyLbl = new Label("Нет ближайших дедлайнов. Отличная работа!");
            emptyLbl.getStyleClass().add("dashboard-empty-text");
            timelineBox.getChildren().add(emptyLbl);
        } else {
            all.stream()
                .filter(t -> !t.isArchived() && t.getDeadline() != null && !t.getDeadline().isAfter(inWeek))
                .sorted((a, b) -> a.getDeadline().compareTo(b.getDeadline()))
                .limit(6)
                .forEach(t -> timelineBox.getChildren().add(createTimelineItem(t)));
        }
        
        content.getChildren().add(timelineBox);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        InlineLayoutSupport.makeShrinkable(root, content);
        root.getStyleClass().add("dashboard-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new DashboardDialog(tasks);
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
        return "Дашборд";
    }

    private VBox createMetricCard(String label, int value, org.kordamp.ikonli.Ikon iconCode, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.setPrefWidth(160);
        card.getStyleClass().addAll("dashboard-metric-card", styleClass);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = FontIcon.of(iconCode, 20);
        icon.getStyleClass().add("dashboard-metric-icon");
        
        Label valLbl = new Label(String.valueOf(value));
        valLbl.getStyleClass().add("dashboard-metric-value");
        
        top.getChildren().addAll(icon, valLbl);
        
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("dashboard-metric-label");
        
        card.getChildren().addAll(top, labelLbl);
        return card;
    }

    private VBox createGauge(String label, int part, int total, String styleClass) {
        double pct = total == 0 ? 0 : ((double) part / total);
        double size = 120;
        double strokeWidth = 10;
        double radius = (size / 2) - (strokeWidth / 2);

        // Arcs centered at 0,0 to rely on StackPane centering the Group
        Arc bg = new Arc(0, 0, radius, radius, 90, 360);
        bg.setType(ArcType.OPEN);
        bg.setStrokeWidth(strokeWidth);
        bg.setStrokeLineCap(StrokeLineCap.ROUND);
        bg.setFill(Color.TRANSPARENT);
        bg.getStyleClass().add("gauge-bg");

        Arc progress = new Arc(0, 0, radius, radius, 90, -pct * 360);
        progress.setType(ArcType.OPEN);
        progress.setStrokeWidth(strokeWidth);
        progress.setStrokeLineCap(StrokeLineCap.ROUND);
        progress.setFill(Color.TRANSPARENT);
        progress.getStyleClass().addAll("gauge-progress", styleClass);

        Label pctLbl = new Label(Math.round(pct * 100) + "%");
        pctLbl.getStyleClass().addAll("gauge-value", styleClass);

        // Group takes exact bounds of children, ensuring 0,0 is center of the circle
        javafx.scene.Group gaugeGroup = new javafx.scene.Group(bg, progress);
        
        StackPane stack = new StackPane(gaugeGroup, pctLbl);
        stack.setMinSize(size, size);
        stack.setPrefSize(size, size);
        stack.setMaxSize(size, size);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("gauge-label");

        VBox box = new VBox(8, stack, lbl);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private HBox createTimelineItem(Task task) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.getStyleClass().add("timeline-item");

        StackPane dot = new StackPane();
        dot.setPrefSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.getStyleClass().add(task.isArchived() ? "timeline-dot-done" : "timeline-dot-active");

        VBox info = new VBox(2);
        Label title = new Label(task.getTitle());
        title.getStyleClass().add("timeline-title");
        
        Label date = new Label(TaskScheduleFormatter.formatDeadline(task));
        date.getStyleClass().add("timeline-date");
        info.getChildren().addAll(title, date);

        Label daysLeft = new Label();
        if (task.getDeadline() != null) {
            long diff = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
            daysLeft.setText(diff == 0 ? "Сегодня" : (diff == 1 ? "Завтра" : "Через " + diff + " д."));
            daysLeft.getStyleClass().add(diff <= 1 ? "timeline-urgent" : "timeline-normal");
        }
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(dot, info, spacer, daysLeft);
        return row;
    }
}
