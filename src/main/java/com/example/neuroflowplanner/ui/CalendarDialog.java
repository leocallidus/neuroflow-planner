package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * Inline calendar view.
 */
public class CalendarDialog implements InlineView {

    private final List<Task> tasks;
    private YearMonth currentMonth;
    private final GridPane calendarGrid = new GridPane();
    private final Label monthLabel = new Label();
    private final ScrollPane scrollPane;
    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private CalendarDialog(List<Task> tasks) {
        this.tasks = tasks;
        this.currentMonth = YearMonth.now();

        root = new VBox(0);
        root.setMinSize(0, 0);
        root.getStyleClass().add("calendar-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().add("calendar-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("calendar-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignC.CALENDAR_MONTH, 22);
        icon.getStyleClass().add("calendar-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Календарь Задач");
        title.getStyleClass().add("calendar-title");
        Label subtitle = new Label("Планируйте свой месяц");
        subtitle.getStyleClass().add("calendar-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // --- Navigation ---
        HBox nav = new HBox(8);
        nav.setAlignment(Pos.CENTER);
        nav.getStyleClass().add("calendar-nav");

        Button prev = new Button();
        prev.setGraphic(FontIcon.of(MaterialDesignC.CHEVRON_LEFT, 20));
        prev.getStyleClass().add("calendar-nav-btn");
        prev.setOnAction(e -> changeMonth(-1));

        monthLabel.getStyleClass().add("calendar-month-label");
        monthLabel.setMinWidth(140);
        monthLabel.setAlignment(Pos.CENTER);
        updateMonthLabel();

        Button next = new Button();
        next.setGraphic(FontIcon.of(MaterialDesignC.CHEVRON_RIGHT, 20));
        next.getStyleClass().add("calendar-nav-btn");
        next.setOnAction(e -> changeMonth(1));

        nav.getChildren().addAll(prev, monthLabel, next);

        header.getChildren().addAll(iconPane, titleBox, spacer, nav);
        root.getChildren().add(header);

        // --- Calendar Grid ---
        calendarGrid.setHgap(8);
        calendarGrid.setVgap(8);
        calendarGrid.setPadding(new Insets(20));
        calendarGrid.getStyleClass().add("calendar-grid");

        for (int i = 0; i < 7; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(100.0 / 7.0);
            calendarGrid.getColumnConstraints().add(col);
        }

        renderCalendar();

        scrollPane = new ScrollPane(calendarGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("calendar-scroll-pane");
        InlineLayoutSupport.makeShrinkable(scrollPane, calendarGrid);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        root.getChildren().add(scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new CalendarDialog(tasks);
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
        return "Календарь задач";
    }

    private void changeMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        updateMonthLabel();
        renderCalendar();
    }
    
    private void updateMonthLabel() {
        String month = currentMonth.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, new Locale("ru"));
        month = month.substring(0, 1).toUpperCase() + month.substring(1);
        monthLabel.setText(month + " " + currentMonth.getYear());
    }

    private void renderCalendar() {
        calendarGrid.getChildren().clear();

        String[] weekDays = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
        for (int i = 0; i < weekDays.length; i++) {
            Label lbl = new Label(weekDays[i]);
            lbl.setMaxWidth(Double.MAX_VALUE);
            lbl.setAlignment(Pos.CENTER);
            lbl.getStyleClass().add("calendar-weekday");
            calendarGrid.add(lbl, i, 0);
        }

        LocalDate firstDay = currentMonth.atDay(1);
        int startCol = (firstDay.getDayOfWeek().getValue() + 6) % 7; // Monday first (0=Mon, 6=Sun)
        int daysInMonth = currentMonth.lengthOfMonth();

        int row = 1;
        int col = startCol;
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            VBox cell = createDayCell(date);
            GridPane.setVgrow(cell, Priority.ALWAYS);
            calendarGrid.add(cell, col, row);
            col++;
            if (col > 6) {
                col = 0;
                row++;
            }
        }
        
        // Fill remaining empty cells in the last row if needed for aesthetics (optional)
    }

    private VBox createDayCell(LocalDate date) {
        VBox cell = new VBox(6);
        cell.getStyleClass().add("calendar-cell");
        if (date.equals(LocalDate.now())) {
            cell.getStyleClass().add("calendar-cell-today");
        }

        Label dateLabel = new Label(String.valueOf(date.getDayOfMonth()));
        dateLabel.getStyleClass().add("calendar-date");
        if (date.equals(LocalDate.now())) {
            dateLabel.getStyleClass().add("calendar-date-today");
        }
        
        // Align date to top-right or top-left
        HBox dateHeader = new HBox(dateLabel);
        dateHeader.setAlignment(Pos.TOP_LEFT);
        
        cell.getChildren().add(dateHeader);

        VBox tasksBox = new VBox(4);
        tasksBox.setFillWidth(true);

        for (Task t : tasks) {
            addTaskLabel(tasksBox, t, date);
            for (Task sub : t.getSubtasks()) {
                addTaskLabel(tasksBox, sub, date);
            }
        }

        cell.getChildren().add(tasksBox);
        InlineLayoutSupport.makeShrinkable(tasksBox);
        VBox.setVgrow(tasksBox, Priority.ALWAYS);
        return cell;
    }

    private void addTaskLabel(VBox box, Task task, LocalDate date) {
        if (task.getDeadline() == null || !task.getDeadline().equals(date)) return;
        
        Label lbl = new Label(task.getTitle());
        lbl.setMaxWidth(Double.MAX_VALUE);
        
        String styleClass = "calendar-task-normal";
        if (task.isArchived()) styleClass = "calendar-task-done";
        else if (task.getSmartPriority() >= 7) styleClass = "calendar-task-high";
        
        lbl.getStyleClass().add(styleClass);
        lbl.setTooltip(new Tooltip(task.getTitle() + "\nПриоритет: " + task.getSmartPriority()));
        
        box.getChildren().add(lbl);
    }
}
