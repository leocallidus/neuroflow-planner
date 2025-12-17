package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

/**
 * Inline Pomodoro timer view.
 */
public class PomodoroDialog implements InlineView {

    private int timeLeft = 25 * 60;
    private int totalTime = 25 * 60;
    private boolean isWork = true;
    private int pomodorosCompleted = 0;
    private Timeline timer;
    private final Label timeLabel = new Label("25:00");
    private final Label statusLabel = new Label("ФОКУС");
    private final Label countLabel = new Label("0");
    private final Arc progressArc = new Arc(0, 0, 110, 110, 90, 0);
    private final Arc bgArc = new Arc(0, 0, 110, 110, 0, 360);
    private final Button startBtn = new Button();
    private final FontIcon startIcon = FontIcon.of(MaterialDesignP.PLAY, 24);
    private final FontIcon pauseIcon = FontIcon.of(MaterialDesignP.PAUSE, 24);
    private Spinner<Integer> workSpinner;
    private Spinner<Integer> breakSpinner;
    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private PomodoroDialog() {
        root = new VBox(20);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(30));
        root.getStyleClass().add("pomodoro-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("pomodoro-header");
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("pomodoro-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignT.TIMER_SAND, 22);
        icon.getStyleClass().add("pomodoro-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Таймер Фокуса");
        title.getStyleClass().add("pomodoro-title");
        Label subtitle = new Label("Будь в потоке, делай перерывы.");
        subtitle.getStyleClass().add("pomodoro-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        
        StackPane spacer = new StackPane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Session Counter
        HBox counterBox = new HBox(6);
        counterBox.setAlignment(Pos.CENTER);
        counterBox.getStyleClass().add("pomodoro-counter-box");
        FontIcon tomatoIcon = FontIcon.of(MaterialDesignR.RADIOACTIVE, 16); // Or a leaf/fire icon
        tomatoIcon.getStyleClass().add("pomodoro-counter-icon");
        countLabel.getStyleClass().add("pomodoro-counter-val");
        counterBox.getChildren().addAll(tomatoIcon, countLabel);

        header.getChildren().addAll(iconPane, titleBox, spacer, counterBox);

        // --- Timer Circle ---
        StackPane timerCircle = new StackPane();
        timerCircle.getStyleClass().add("pomodoro-circle-container");

        bgArc.setType(ArcType.OPEN);
        bgArc.setStrokeWidth(12);
        bgArc.setFill(Color.TRANSPARENT);
        bgArc.getStyleClass().add("pomodoro-arc-bg");
        bgArc.setStrokeLineCap(StrokeLineCap.ROUND);

        progressArc.setType(ArcType.OPEN);
        progressArc.setStrokeWidth(12);
        progressArc.setFill(Color.TRANSPARENT);
        progressArc.getStyleClass().add("pomodoro-arc-progress");
        progressArc.setStrokeLineCap(StrokeLineCap.ROUND);

        VBox centerInfo = new VBox(5);
        centerInfo.setAlignment(Pos.CENTER);
        timeLabel.getStyleClass().add("pomodoro-time");
        statusLabel.getStyleClass().add("pomodoro-status");
        centerInfo.getChildren().addAll(statusLabel, timeLabel);

        timerCircle.getChildren().addAll(bgArc, progressArc, centerInfo);

        // --- Controls ---
        HBox controls = new HBox(20);
        controls.setAlignment(Pos.CENTER);
        controls.getStyleClass().add("pomodoro-controls");

        Button resetBtn = createControlBtn(MaterialDesignR.REFRESH, "Сбросить таймер", e -> resetTimer());
        resetBtn.getStyleClass().add("pomodoro-btn-secondary");

        startBtn.setGraphic(startIcon);
        startBtn.getStyleClass().add("pomodoro-btn-primary"); // Big FAB style
        startBtn.setTooltip(new Tooltip("Старт/Пауза"));
        startBtn.setOnAction(e -> toggleTimer());

        Button skipBtn = createControlBtn(MaterialDesignS.SKIP_NEXT, "Пропустить фазу", e -> skipPhase());
        skipBtn.getStyleClass().add("pomodoro-btn-secondary");

        controls.getChildren().addAll(resetBtn, startBtn, skipBtn);

        // --- Settings Footer ---
        HBox settings = new HBox(25);
        settings.setAlignment(Pos.CENTER);
        settings.getStyleClass().add("pomodoro-settings");

        workSpinner = new Spinner<>(1, 60, 25);
        workSpinner.setPrefWidth(70);
        workSpinner.getStyleClass().add("pomodoro-spinner");
        workSpinner.valueProperty().addListener((o, old, val) -> {
            if (isWork && timer == null) {
                totalTime = val * 60;
                timeLeft = totalTime;
                updateDisplay();
            }
        });

        breakSpinner = new Spinner<>(1, 30, 5);
        breakSpinner.setPrefWidth(70);
        breakSpinner.getStyleClass().add("pomodoro-spinner");

        settings.getChildren().addAll(
            labeledSpinner("Фокус", workSpinner),
            labeledSpinner("Перерыв", breakSpinner)
        );

        root.getChildren().addAll(header, timerCircle, controls, settings);

        // CSS
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        updateDisplay(); // Init state
        statusLabel.getStyleClass().add("pomodoro-status-work");
    }

    private Button createControlBtn(org.kordamp.ikonli.Ikon iconCode, String tip, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button();
        FontIcon i = FontIcon.of(iconCode, 20);
        b.setGraphic(i);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(action);
        return b;
    }

    private HBox labeledSpinner(String label, Spinner<Integer> spinner) {
        VBox box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        Label lbl = new Label(label);
        lbl.getStyleClass().add("pomodoro-setting-label");
        box.getChildren().addAll(lbl, spinner);
        
        HBox wrapper = new HBox(box);
        wrapper.setAlignment(Pos.CENTER);
        return wrapper;
    }

    public static InlineView inline() {
        return new PomodoroDialog();
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return () -> { if (timer != null) timer.stop(); };
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Таймер Помодоро";
    }

    private void toggleTimer() {
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
        timeLeft--;
        updateDisplay();
        if (timeLeft <= 0) {
            timer.stop();
            timer = null;
            startBtn.setGraphic(startIcon);
            startBtn.getStyleClass().remove("running");
            onPhaseComplete();
        }
    }

    private void updateDisplay() {
        int mins = timeLeft / 60;
        int secs = timeLeft % 60;
        timeLabel.setText(String.format("%02d:%02d", mins, secs));
        
        double angle = -360.0 * (totalTime - timeLeft) / totalTime;
        // Invert angle for clockwise countdown or similar
        // Let's make it fill up or empty down. 
        // Standard pomodoro: circle reduces.
        // angle is negative, so it reduces from 90 degrees backwards.
        progressArc.setLength(angle);
    }

    private void onPhaseComplete() {
        if (isWork) {
            pomodorosCompleted++;
            countLabel.setText(String.valueOf(pomodorosCompleted));
            switchToBreak();
        } else {
            switchToWork();
        }
    }

    private void switchToWork() {
        isWork = true;
        totalTime = workSpinner.getValue() * 60;
        timeLeft = totalTime;
        statusLabel.setText("ФОКУС");
        statusLabel.getStyleClass().remove("pomodoro-status-break");
        statusLabel.getStyleClass().add("pomodoro-status-work");
        progressArc.getStyleClass().remove("pomodoro-arc-break");
        progressArc.getStyleClass().add("pomodoro-arc-work");
        updateDisplay();
    }

    private void switchToBreak() {
        isWork = false;
        int breakMins = (pomodorosCompleted % 4 == 0 && pomodorosCompleted > 0) ? 15 : breakSpinner.getValue();
        totalTime = breakMins * 60;
        timeLeft = totalTime;
        statusLabel.setText(pomodorosCompleted % 4 == 0 && pomodorosCompleted > 0 ? "ДЛИННЫЙ ПЕРЕРЫВ" : "ПЕРЕРЫВ");
        statusLabel.getStyleClass().remove("pomodoro-status-work");
        statusLabel.getStyleClass().add("pomodoro-status-break");
        progressArc.getStyleClass().remove("pomodoro-arc-work");
        progressArc.getStyleClass().add("pomodoro-arc-break");
        updateDisplay();
    }

    private void resetTimer() {
        if (timer != null) { 
            timer.stop(); 
            timer = null; 
        }
        startBtn.setGraphic(startIcon);
        startBtn.getStyleClass().remove("running");
        if (isWork) switchToWork(); else switchToBreak();
    }

    private void skipPhase() {
        resetTimer();
        if (isWork) {
            pomodorosCompleted++;
            countLabel.setText(String.valueOf(pomodorosCompleted));
            switchToBreak();
        } else {
            switchToWork();
        }
    }
}
