package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

import java.util.prefs.Preferences;

/**
 * Inline work hours configuration view.
 */
public class WorkHoursDialog implements InlineView {

    private static final Preferences prefs = Preferences.userNodeForPackage(WorkHoursDialog.class);
    private static final String[] DAYS = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
    private static final String[] DAY_KEYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private WorkHoursDialog() {
        root = new VBox(0);
        root.setMinSize(0, 0);
        root.getStyleClass().add("work-hours-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().add("work-hours-header");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("work-hours-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignC.CLOCK_TIME_EIGHT_OUTLINE, 22);
        icon.getStyleClass().add("work-hours-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Рабочее Расписание");
        title.getStyleClass().add("work-hours-title");
        Label subtitle = new Label("Настройте свои продуктивные часы");
        subtitle.getStyleClass().add("work-hours-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);

        // --- Schedule Rows ---
        VBox scheduleBox = new VBox(8);
        scheduleBox.setPadding(new Insets(10, 25, 20, 25));
        scheduleBox.getStyleClass().add("work-hours-list");

        CheckBox[] workDays = new CheckBox[7];
        Spinner<Integer>[] startHours = new Spinner[7];
        Spinner<Integer>[] endHours = new Spinner[7];

        // Column headers
        HBox listHeader = new HBox(10);
        listHeader.setPadding(new Insets(0, 10, 5, 10));
        Label dayH = new Label("ДЕНЬ"); dayH.setPrefWidth(120); dayH.getStyleClass().add("work-hours-col-header");
        Label activeH = new Label("АКТИВЕН"); activeH.setPrefWidth(80); activeH.setAlignment(Pos.CENTER); activeH.getStyleClass().add("work-hours-col-header");
        Label timeH = new Label("ВРЕМЯ (ЧЧ:00)"); timeH.getStyleClass().add("work-hours-col-header");
        listHeader.getChildren().addAll(dayH, activeH, timeH);
        scheduleBox.getChildren().add(listHeader);

        for (int i = 0; i < 7; i++) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 10, 8, 10));
            row.getStyleClass().add("work-hours-row");

            Label dayLabel = new Label(DAYS[i]);
            dayLabel.setPrefWidth(120);
            dayLabel.getStyleClass().add("work-hours-day-label");

            workDays[i] = new CheckBox();
            workDays[i].setSelected(prefs.getBoolean(DAY_KEYS[i] + "_work", i < 5));
            workDays[i].getStyleClass().add("work-hours-checkbox");
            
            StackPane checkWrapper = new StackPane(workDays[i]);
            checkWrapper.setPrefWidth(80);
            checkWrapper.setAlignment(Pos.CENTER);

            HBox timeBox = new HBox(8);
            timeBox.setAlignment(Pos.CENTER_LEFT);

            startHours[i] = new Spinner<>(0, 23, prefs.getInt(DAY_KEYS[i] + "_start", 9));
            startHours[i].setPrefWidth(70);
            startHours[i].getStyleClass().add("work-hours-spinner");

            Label sep = new Label("—");
            sep.getStyleClass().add("work-hours-time-sep");

            endHours[i] = new Spinner<>(0, 23, prefs.getInt(DAY_KEYS[i] + "_end", 18));
            endHours[i].setPrefWidth(70);
            endHours[i].getStyleClass().add("work-hours-spinner");

            timeBox.getChildren().addAll(startHours[i], sep, endHours[i]);

            int idx = i;
            workDays[i].selectedProperty().addListener((o, old, val) -> {
                updateRowState(row, val);
                startHours[idx].setDisable(!val);
                endHours[idx].setDisable(!val);
            });
            
            updateRowState(row, workDays[i].isSelected());
            startHours[i].setDisable(!workDays[i].isSelected());
            endHours[i].setDisable(!workDays[i].isSelected());

            row.getChildren().addAll(dayLabel, checkWrapper, timeBox);
            scheduleBox.getChildren().add(row);
        }
        // --- Footer & Summary ---
        VBox footer = new VBox(15);
        footer.setPadding(new Insets(0, 25, 25, 25));
        
        HBox summaryCard = new HBox(12);
        summaryCard.setAlignment(Pos.CENTER_LEFT);
        summaryCard.setPadding(new Insets(12));
        summaryCard.getStyleClass().add("work-hours-summary-card");
        
        FontIcon sumIcon = FontIcon.of(MaterialDesignC.CALENDAR_WEEK, 24);
        sumIcon.getStyleClass().add("work-hours-summary-icon");
        
        VBox sumText = new VBox(2);
        Label sumTitle = new Label("Итого за неделю");
        sumTitle.getStyleClass().add("work-hours-summary-title");
        Label sumValue = new Label();
        sumValue.getStyleClass().add("work-hours-summary-value");
        sumText.getChildren().addAll(sumTitle, sumValue);
        
        summaryCard.getChildren().addAll(sumIcon, sumText);
        
        updateSummary(sumValue, workDays, startHours, endHours);

        for (int i = 0; i < 7; i++) {
            workDays[i].selectedProperty().addListener((o, old, val) -> updateSummary(sumValue, workDays, startHours, endHours));
            startHours[i].valueProperty().addListener((o, old, val) -> updateSummary(sumValue, workDays, startHours, endHours));
            endHours[i].valueProperty().addListener((o, old, val) -> updateSummary(sumValue, workDays, startHours, endHours));
        }

        Button saveBtn = new Button("Сохранить изменения");
        saveBtn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_SAVE, 16));
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.getStyleClass().add("work-hours-save-btn");
        saveBtn.setOnAction(e -> {
            for (int i = 0; i < 7; i++) {
                prefs.putBoolean(DAY_KEYS[i] + "_work", workDays[i].isSelected());
                prefs.putInt(DAY_KEYS[i] + "_start", startHours[i].getValue());
                prefs.putInt(DAY_KEYS[i] + "_end", endHours[i].getValue());
            }
            if (closeAction != null) closeAction.run();
        });

        footer.getChildren().addAll(summaryCard, saveBtn);

        VBox body = new VBox(0);
        body.getChildren().addAll(scheduleBox, footer);

        ScrollPane bodyScroll = InlineLayoutSupport.createContentScroll(body, "work-hours-body-scroll");
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);

        root.getChildren().addAll(header, bodyScroll);
        InlineLayoutSupport.makeShrinkable(root, body);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private void updateRowState(HBox row, boolean active) {
        if (active) {
            row.getStyleClass().remove("work-hours-row-disabled");
            row.getStyleClass().add("work-hours-row-active");
        } else {
            row.getStyleClass().remove("work-hours-row-active");
            row.getStyleClass().add("work-hours-row-disabled");
        }
    }

    public static InlineView inline() {
        return new WorkHoursDialog();
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
        return "Рабочие часы";
    }

    private void updateSummary(Label label, CheckBox[] days, Spinner<Integer>[] starts, Spinner<Integer>[] ends) {
        int totalHours = 0;
        int workDays = 0;
        for (int i = 0; i < 7; i++) {
            if (days[i].isSelected()) {
                workDays++;
                totalHours += Math.max(0, ends[i].getValue() - starts[i].getValue());
            }
        }
        label.setText(String.format("%d рабочих дней • %d часов", workDays, totalHours));
    }

    public static int getWeeklyHours() {
        int total = 0;
        for (int i = 0; i < 7; i++) {
            if (prefs.getBoolean(DAY_KEYS[i] + "_work", i < 5)) {
                total += prefs.getInt(DAY_KEYS[i] + "_end", 18) - prefs.getInt(DAY_KEYS[i] + "_start", 9);
            }
        }
        return total;
    }

    public static boolean isWorkDay(int dayOfWeek) {
        return prefs.getBoolean(DAY_KEYS[dayOfWeek - 1] + "_work", dayOfWeek <= 5);
    }

    public static int getStartHour(int dayOfWeek) {
        return prefs.getInt(DAY_KEYS[dayOfWeek - 1] + "_start", 9);
    }

    public static int getEndHour(int dayOfWeek) {
        return prefs.getInt(DAY_KEYS[dayOfWeek - 1] + "_end", 18);
    }
}
