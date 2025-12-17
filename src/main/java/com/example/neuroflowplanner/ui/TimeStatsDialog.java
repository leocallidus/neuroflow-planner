package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Inline time statistics view.
 */
public class TimeStatsDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private TimeStatsDialog(List<Task> tasks) {
        List<Task> all = new ArrayList<>();
        for (Task t : tasks) { all.add(t); all.addAll(t.getSubtasks()); }
        List<Task> active = all.stream().filter(t -> !t.isArchived()).toList();

        int totalHours = active.stream().mapToInt(t -> t.getComplexity() * 2).sum();
        int doneHours = all.stream().filter(Task::isArchived).mapToInt(t -> t.getComplexity() * 2).sum();
        int totalScope = totalHours + doneHours;

        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("timestats-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("timestats-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignT.TIMER_SAND, 22);
        icon.getStyleClass().add("timestats-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Оценка Времени");
        title.getStyleClass().add("timestats-title");
        Label subtitle = new Label("Анализ трудозатрат (сложность × 2 часа)");
        subtitle.getStyleClass().add("timestats-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Metric Cards ---
        HBox cards = new HBox(15);
        cards.getStyleClass().add("timestats-cards");
        cards.getChildren().addAll(
            createCard("Осталось", totalHours + " ч", MaterialDesignT.TIMER_OUTLINE, "timestats-accent-blue"),
            createCard("Выполнено", doneHours + " ч", MaterialDesignC.CHECK_CIRCLE_OUTLINE, "timestats-accent-green"),
            createCard("Всего", totalScope + " ч", MaterialDesignS.SCALE_BALANCE, "timestats-accent-purple")
        );
        content.getChildren().add(cards);

        // --- Daily Distribution ---
        VBox dailyBox = new VBox(15);
        dailyBox.getStyleClass().add("timestats-section-box");
        
        Label weekLabel = new Label("Распределение по дням недели");
        weekLabel.getStyleClass().add("timestats-section-title");
        dailyBox.getChildren().add(weekLabel);

        Map<DayOfWeek, Integer> byDay = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) byDay.put(d, 0);
        for (Task t : active) {
            if (t.getDeadline() != null) {
                byDay.merge(t.getDeadline().getDayOfWeek(), t.getComplexity() * 2, Integer::sum);
            }
        }
        int maxDay = byDay.values().stream().mapToInt(i -> i).max().orElse(1);

        HBox weekChart = new HBox(15);
        weekChart.setAlignment(Pos.BOTTOM_CENTER);
        weekChart.getStyleClass().add("timestats-bars");
        String[] dayNames = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        for (int i = 0; i < 7; i++) {
            DayOfWeek day = DayOfWeek.of(i + 1);
            int hours = byDay.get(day);
            boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            String accent = weekend ? "timestats-accent-gray" : "timestats-accent-blue";
            VBox bar = createBarColumn(dayNames[i], hours, maxDay, accent);
            HBox.setHgrow(bar, Priority.ALWAYS);
            weekChart.getChildren().add(bar);
        }
        dailyBox.getChildren().add(weekChart);
        content.getChildren().add(dailyBox);

        // --- Weekly Distribution ---
        VBox weeklyBox = new VBox(15);
        weeklyBox.getStyleClass().add("timestats-section-box");
        
        Label weeksLabel = new Label("Нагрузка по неделям (ближайшие 4)");
        weeksLabel.getStyleClass().add("timestats-section-title");
        weeklyBox.getChildren().add(weeksLabel);

        LocalDate today = LocalDate.now();
        int[] weekHours = new int[4];
        for (Task t : active) {
            if (t.getDeadline() != null) {
                long days = ChronoUnit.DAYS.between(today, t.getDeadline());
                if (days >= 0 && days < 28) {
                    weekHours[(int)(days / 7)] += t.getComplexity() * 2;
                }
            }
        }
        int maxWeek = Arrays.stream(weekHours).max().orElse(1);

        HBox weeksChart = new HBox(20);
        weeksChart.setAlignment(Pos.BOTTOM_CENTER);
        weeksChart.getStyleClass().add("timestats-bars");
        String[] weekNames = {"Текущая", "+1 нед.", "+2 нед.", "+3 нед."};
        String[] weekAccents = {"timestats-accent-red", "timestats-accent-orange", "timestats-accent-blue", "timestats-accent-green"};
        for (int i = 0; i < 4; i++) {
            VBox bar = createBarColumn(weekNames[i], weekHours[i], Math.max(maxWeek, 1), weekAccents[i]);
            HBox.setHgrow(bar, Priority.ALWAYS);
            weeksChart.getChildren().add(bar);
        }
        weeklyBox.getChildren().add(weeksChart);
        content.getChildren().add(weeklyBox);

        // --- Top Tasks ---
        VBox topBox = new VBox(10);
        topBox.getStyleClass().add("timestats-section-box");
        
        Label topLabel = new Label("Топ-5 трудоёмких задач");
        topLabel.getStyleClass().add("timestats-section-title");
        topBox.getChildren().add(topLabel);

        VBox topTasks = new VBox(8);
        topTasks.getStyleClass().add("timestats-top-list");
        active.stream()
            .sorted((a, b) -> Integer.compare(b.getComplexity(), a.getComplexity()))
            .limit(5)
            .forEach(t -> topTasks.getChildren().add(createTopRow(t.getTitle(), t.getComplexity() * 2)));

        if (topTasks.getChildren().isEmpty()) {
            Label empty = new Label("Нет активных задач для оценки.");
            empty.getStyleClass().add("timestats-empty");
            topTasks.getChildren().add(empty);
        }
        topBox.getChildren().add(topTasks);
        content.getChildren().add(topBox);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(350, 400);
        root.getStyleClass().add("timestats-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new TimeStatsDialog(tasks);
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
        return "Временная статистика";
    }

    private VBox createCard(String label, String value, Ikon iconCode, String accentClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));
        card.getStyleClass().addAll("timestats-card", accentClass);
        HBox.setHgrow(card, Priority.ALWAYS);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon icon = FontIcon.of(iconCode, 20);
        icon.getStyleClass().add("timestats-card-icon");

        Label val = new Label(value);
        val.getStyleClass().add("timestats-card-value");
        
        top.getChildren().addAll(icon, val);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("timestats-card-label");

        card.getChildren().addAll(top, lbl);
        return card;
    }

    private VBox createBarColumn(String label, int value, int max, String accentClass) {
        VBox bar = new VBox(6);
        bar.setAlignment(Pos.BOTTOM_CENTER);
        bar.getStyleClass().add("timestats-bar-column");
        
        Label val = new Label(value > 0 ? value + " ч" : "");
        val.getStyleClass().add("timestats-bar-value");

        double height = Math.max((value * 100.0 / Math.max(max, 1)), 4);
        Region rect = new Region();
        rect.setMaxWidth(40);
        rect.setPrefHeight(height);
        rect.getStyleClass().addAll("timestats-bar", accentClass);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("timestats-bar-label");
        
        bar.getChildren().addAll(val, rect, lbl);
        return bar;
    }

    private HBox createTopRow(String title, int hours) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("timestats-top-row");
        
        Label hoursPill = new Label(hours + " ч");
        hoursPill.getStyleClass().add("timestats-top-hours");
        
        Label name = new Label(title);
        name.getStyleClass().add("timestats-top-title");
        name.setWrapText(true);
        
        row.getChildren().addAll(hoursPill, name);
        return row;
    }
}
