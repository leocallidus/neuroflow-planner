package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import java.util.List;

/**
 * Inline time tracker view.
 */
public class TimeTrackerDialog implements InlineView {

    private Task selectedTask;
    private Timeline timer;
    private int sessionSeconds = 0;
    private final Label timerLabel = new Label("00:00:00");
    private final Label trackedLabel = new Label("Всего: 0ч 0мин");
    private final Button startBtn = new Button();
    private final FontIcon startIcon = FontIcon.of(MaterialDesignP.PLAY, 24);
    private final FontIcon pauseIcon = FontIcon.of(MaterialDesignP.PAUSE, 24);
    private final ComboBox<Task> taskCombo = new ComboBox<>();
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private TimeTrackerDialog(List<Task> tasks) {
        root = new VBox(20);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));
        root.getStyleClass().add("tracker-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("tracker-header");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("tracker-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignT.TIMER, 22);
        icon.getStyleClass().add("tracker-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Трекинг Времени");
        title.getStyleClass().add("tracker-title");
        Label subtitle = new Label("Отслеживайте прогресс по задачам");
        subtitle.getStyleClass().add("tracker-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);

        // --- Task Selection ---
        VBox selectionBox = new VBox(8);
        selectionBox.getStyleClass().add("tracker-selection-box");
        Label selectLabel = new Label("ЗАДАЧА");
        selectLabel.getStyleClass().add("tracker-section-label");
        
        taskCombo.setPromptText("Выберите задачу для трекинга...");
        taskCombo.setMaxWidth(Double.MAX_VALUE);
        taskCombo.getStyleClass().add("tracker-combo");
        
        for (Task t : tasks) {
            if (!t.isArchived()) {
                taskCombo.getItems().add(t);
                for (Task sub : t.getSubtasks()) {
                    if (!sub.isArchived()) taskCombo.getItems().add(sub);
                }
            }
        }
        taskCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : (t.isSubtask() ? "  └ " : "") + t.getTitle());
            }
        });
        taskCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Task t, boolean empty) {
                super.updateItem(t, empty);
                setText(empty || t == null ? null : t.getTitle());
            }
        });
        taskCombo.setOnAction(e -> {
            selectedTask = taskCombo.getValue();
            updateTrackedDisplay();
        });

        selectionBox.getChildren().addAll(selectLabel, taskCombo);

        // --- Timer Display ---
        VBox timerBox = new VBox(5);
        timerBox.setAlignment(Pos.CENTER);
        timerBox.getStyleClass().add("tracker-timer-box");
        
        timerLabel.getStyleClass().add("tracker-timer-display");
        trackedLabel.getStyleClass().add("tracker-total-label");
        
        timerBox.getChildren().addAll(timerLabel, trackedLabel);

        // --- Controls ---
        HBox controls = new HBox(20);
        controls.setAlignment(Pos.CENTER);
        controls.getStyleClass().add("tracker-controls");

        startBtn.setGraphic(startIcon);
        startBtn.getStyleClass().add("tracker-btn-primary");
        startBtn.setTooltip(new Tooltip("Старт/Пауза"));
        startBtn.setOnAction(e -> toggleTimer());

        Button stopBtn = new Button();
        stopBtn.setGraphic(FontIcon.of(MaterialDesignS.STOP, 24));
        stopBtn.getStyleClass().add("tracker-btn-danger");
        stopBtn.setTooltip(new Tooltip("Стоп и Сохранить"));
        stopBtn.setOnAction(e -> stopAndSave());

        controls.getChildren().addAll(startBtn, stopBtn);

        // --- Manual Entry ---
        VBox manualBox = new VBox(10);
        manualBox.getStyleClass().add("tracker-manual-box");
        
        Label manualLabel = new Label("ДОБАВИТЬ ВРУЧНУЮ");
        manualLabel.getStyleClass().add("tracker-section-label");

        HBox manualInputBox = new HBox(10);
        manualInputBox.setAlignment(Pos.CENTER_LEFT);
        
        Spinner<Integer> manualMins = new Spinner<>(1, 480, 30);
        manualMins.setPrefWidth(100);
        manualMins.setEditable(true);
        manualMins.getStyleClass().add("tracker-spinner");
        
        Button addBtn = new Button("Добавить");
        addBtn.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 16));
        addBtn.getStyleClass().add("tracker-btn-secondary");
        addBtn.setOnAction(e -> {
            if (selectedTask != null) {
                selectedTask.addTrackedMinutes(manualMins.getValue());
                db.saveTask(selectedTask);
                updateTrackedDisplay();
            } else {
                warnNoTask();
            }
        });

        manualInputBox.getChildren().addAll(manualMins, new Label("мин"), addBtn);
        manualBox.getChildren().addAll(manualLabel, manualInputBox);

        root.getChildren().addAll(
            header, 
            selectionBox,
            timerBox,
            controls,
            manualBox
        );

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline(List<Task> tasks) {
        return new TimeTrackerDialog(tasks);
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return () -> {
            if (timer != null) stopAndSave();
        };
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Трекинг времени";
    }

    private void toggleTimer() {
        if (selectedTask == null) {
            warnNoTask();
            return;
        }
        if (timer == null) {
            timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
            timer.setCycleCount(Timeline.INDEFINITE);
            timer.play();
            startBtn.setGraphic(pauseIcon);
            startBtn.getStyleClass().add("running");
        } else {
            timer.stop();
            timer = null;
            startBtn.setGraphic(startIcon);
            startBtn.getStyleClass().remove("running");
        }
    }

    private void tick() {
        sessionSeconds++;
        int h = sessionSeconds / 3600;
        int m = (sessionSeconds % 3600) / 60;
        int s = sessionSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d:%02d", h, m, s));
    }

    private void stopAndSave() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        startBtn.setGraphic(startIcon);
        startBtn.getStyleClass().remove("running");

        if (selectedTask != null && sessionSeconds > 0) {
            long mins = Math.max(sessionSeconds / 60, 1);
            selectedTask.addTrackedMinutes(mins);
            db.saveTask(selectedTask);
            updateTrackedDisplay();
        }
        sessionSeconds = 0;
        timerLabel.setText("00:00:00");
    }

    private void updateTrackedDisplay() {
        if (selectedTask != null) {
            long total = selectedTask.getTrackedMinutes();
            long h = total / 60;
            long m = total % 60;
            trackedLabel.setText(String.format("Всего учтено: %d ч %d мин", h, m));
        } else {
            trackedLabel.setText("Всего: 0ч 0мин");
        }
    }

    private void warnNoTask() {
        // Simple visual feedback could be added here instead of a popup
         taskCombo.requestFocus();
         taskCombo.show();
    }
}
