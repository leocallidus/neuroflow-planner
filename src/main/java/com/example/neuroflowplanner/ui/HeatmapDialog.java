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
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inline heatmap view.
 */
public class HeatmapDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;

    private HeatmapDialog(List<Task> tasks) {
        Map<LocalDate, Integer> activity = new HashMap<>();
        for (Task t : tasks) {
            if (t.getDeadline() != null) {
                activity.merge(t.getDeadline(), 1, Integer::sum);
                for (Task sub : t.getSubtasks()) {
                    if (sub.getDeadline() != null) {
                        activity.merge(sub.getDeadline(), 1, Integer::sum);
                    }
                }
            }
        }

        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("heatmap-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("heatmap-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignG.GRID, 22);
        icon.getStyleClass().add("heatmap-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Карта Активности");
        title.getStyleClass().add("heatmap-title");
        Label subtitle = new Label("Интенсивность задач за последние полгода");
        subtitle.getStyleClass().add("heatmap-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Heatmap Grid ---
        GridPane grid = new GridPane();
        grid.setHgap(4);
        grid.setVgap(4);
        grid.setAlignment(Pos.CENTER);
        grid.getStyleClass().add("heatmap-grid");

        LocalDate today = LocalDate.now();
        // Start from a Monday about 6 months ago to align weeks cleanly
        LocalDate start = today.minusDays(180);
        while (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            start = start.minusDays(1);
        }

        // Weekday labels (Mon, Wed, Fri)
        String[] weekdays = {"Пн", "", "Ср", "", "Пт", "", ""};
        for (int i = 0; i < weekdays.length; i++) {
            if (!weekdays[i].isEmpty()) {
                Label w = new Label(weekdays[i]);
                w.getStyleClass().add("heatmap-weekday-label");
                grid.add(w, 0, i + 1);
            }
        }

        // Cells generation
        int col = 1;
        LocalDate current = start;
        
        // Month labels row
        HBox monthRow = new HBox(0); // Placeholder, managing months in grid is tricky with variable widths
        // Simplified approach: Add month label when month changes in the first row
        
        while (!current.isAfter(today)) {
            // Month label check
            if (current.getDayOfMonth() <= 7 && current.getDayOfWeek() == DayOfWeek.MONDAY) {
                 // Place label above the column
                 Label mLabel = new Label(current.getMonth().getDisplayName(TextStyle.SHORT, new Locale("ru")));
                 mLabel.getStyleClass().add("heatmap-month-label");
                 grid.add(mLabel, col, 0);
            }

            for (int row = 0; row < 7; row++) {
                if (current.isAfter(today)) break;
                
                int count = activity.getOrDefault(current, 0);
                StackPane cell = new StackPane();
                cell.setPrefSize(14, 14);
                cell.getStyleClass().addAll("heatmap-cell", getIntensityClass(count));
                
                Tooltip t = new Tooltip(current + ": " + count + " задач");
                Tooltip.install(cell, t);
                
                grid.add(cell, col, row + 1);
                current = current.plusDays(1);
            }
            col++;
        }
        
        ScrollPane gridScroll = new ScrollPane(grid);
        gridScroll.setFitToHeight(true);
        gridScroll.getStyleClass().add("heatmap-grid-scroll");
        gridScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        
        content.getChildren().add(gridScroll);

        // --- Legend ---
        HBox legend = new HBox(8);
        legend.setAlignment(Pos.CENTER_RIGHT);
        legend.setPadding(new Insets(10, 0, 0, 0));
        
        Label lessLbl = new Label("Меньше");
        lessLbl.getStyleClass().add("heatmap-legend-text");
        legend.getChildren().add(lessLbl);

        int[] sampleCounts = {0, 2, 4, 7, 10};
        for (int c : sampleCounts) {
            StackPane box = new StackPane();
            box.setPrefSize(12, 12);
            box.getStyleClass().addAll("heatmap-cell", "heatmap-legend-cell", getIntensityClass(c));
            legend.getChildren().add(box);
        }

        Label moreLbl = new Label("Больше");
        moreLbl.getStyleClass().add("heatmap-legend-text");
        legend.getChildren().add(moreLbl);
        
        content.getChildren().add(legend);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        root.setFitToHeight(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(400, 300);
        root.getStyleClass().add("heatmap-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private String getIntensityClass(int count) {
        if (count == 0) return "heatmap-level-0";
        if (count <= 2) return "heatmap-level-1";
        if (count <= 4) return "heatmap-level-2";
        if (count <= 7) return "heatmap-level-3";
        return "heatmap-level-4";
    }

    public static InlineView inline(List<Task> tasks) {
        return new HeatmapDialog(tasks);
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
        return "Тепловая карта";
    }
}
