package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.MoodEntry;
import com.example.neuroflowplanner.service.MoodAnalysisService;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignE;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

public class MoodAnalysisDialog implements InlineView {

    private final VBox root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private Runnable closeAction;
    private final MoodAnalysisService service = new MoodAnalysisService();
    private final DatabaseManager db = DatabaseManager.getInstance();
    
    private final TextArea noteArea = new TextArea();
    private final Slider moodSlider = new Slider(1, 10, 5);
    private final Label resultLabel = new Label();
    private final Label scoreValueLabel = new Label("5 - Нормально");
    private final LineChart<String, Number> chart;

    private MoodAnalysisDialog() {
        root = new VBox(0);
        root.getStyleClass().add("mood-root");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().add("mood-header-panel");
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("mood-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignE.EMOTICON, 22);
        icon.getStyleClass().add("mood-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Анализ настроения");
        title.getStyleClass().add("mood-title");
        Label subtitle = new Label("Дневник эмоций и анализ состояния");
        subtitle.getStyleClass().add("mood-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(iconPane, titleBox, spacer);
        root.getChildren().add(header);

        // --- Content ---
        VBox content = new VBox(20);
        content.setPadding(new Insets(0, 25, 25, 25));
        content.getStyleClass().add("mood-content");

        // --- Input Section ---
        VBox inputCard = new VBox(15);
        inputCard.getStyleClass().add("mood-input-card");

        // Slider
        VBox sliderBox = new VBox(8);
        Label sliderLabel = new Label("Как вы себя чувствуете?");
        sliderLabel.getStyleClass().add("mood-label");
        
        HBox sliderHeader = new HBox(10, sliderLabel, scoreValueLabel);
        sliderHeader.setAlignment(Pos.CENTER_LEFT);
        scoreValueLabel.getStyleClass().add("mood-score-val");

        moodSlider.getStyleClass().add("mood-slider");
        moodSlider.setBlockIncrement(1);
        moodSlider.setMajorTickUnit(1);
        moodSlider.setMinorTickCount(0);
        moodSlider.setSnapToTicks(true);
        moodSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            scoreValueLabel.setText(val + " - " + getMoodDescription(val));
        });

        sliderBox.getChildren().addAll(sliderHeader, moodSlider);

        // Note Area
        VBox noteBox = new VBox(8);
        Label noteLabel = new Label("Заметки / Дневник:");
        noteLabel.getStyleClass().add("mood-label");
        
        noteArea.setPromptText("Опишите ваше состояние, мысли или события дня...");
        noteArea.setPrefRowCount(3);
        noteArea.setWrapText(true);
        noteArea.getStyleClass().add("mood-text-area");

        noteBox.getChildren().addAll(noteLabel, noteArea);

        Button analyzeBtn = new Button("Сохранить запись");
        analyzeBtn.getStyleClass().add("mood-analyze-btn");
        analyzeBtn.setMaxWidth(Double.MAX_VALUE);
        analyzeBtn.setOnAction(e -> handleAnalyze());

        // Result Box (Initially hidden or empty)
        HBox resultBox = new HBox(10);
        resultBox.getStyleClass().add("mood-result-box");
        resultBox.setAlignment(Pos.CENTER_LEFT);
        resultLabel.getStyleClass().add("mood-result-text");
        resultBox.getChildren().add(resultLabel);
        resultBox.managedProperty().bind(resultLabel.textProperty().isNotEmpty());
        resultBox.visibleProperty().bind(resultLabel.textProperty().isNotEmpty());

        inputCard.getChildren().addAll(sliderBox, noteBox, analyzeBtn, resultBox);
        content.getChildren().add(inputCard);

        // --- Chart Section ---
        VBox chartBox = new VBox(10);
        chartBox.getStyleClass().add("mood-chart-container");
        
        Label chartTitle = new Label("История настроения");
        chartTitle.getStyleClass().add("mood-section-title");
        
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(1, 10, 1);
        yAxis.setLabel("Оценка");

        chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("mood-chart");
        chart.setLegendVisible(false);
        chart.setPrefHeight(250);
        chart.setCreateSymbols(true); // Dots on lines
        
        chartBox.getChildren().addAll(chartTitle, chart);
        
        updateChart();

        content.getChildren().add(chartBox);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        // scrollPane.setPrefSize(600, 680); // Removed hard size on scroll pane, handled by root
        scrollPane.getStyleClass().add("mood-scroll-pane"); // Use specific style if needed or generic
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;"); // Ensure transparency
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        root.getChildren().add(scrollPane);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(350, 400);
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private String getMoodDescription(int score) {
        if (score >= 9) return "Отлично 🤩";
        if (score >= 7) return "Хорошо 🙂";
        if (score >= 5) return "Нормально 😐";
        if (score >= 3) return "Так себе 😕";
        return "Плохо 😞";
    }

    private void handleAnalyze() {
        String text = noteArea.getText();
        int score = (int) moodSlider.getValue();
        
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(text, score);
        
        MoodEntry entry = new MoodEntry(
            java.util.UUID.randomUUID().toString(),
            LocalDateTime.now(),
            result.adjustedScore,
            text,
            result.label
        );
        
        db.saveMoodEntry(entry);
        
        resultLabel.setText("Анализ: " + result.label + " (Скорр. оценка: " + result.adjustedScore + ")");
        noteArea.clear();
        updateChart();
    }

    private void updateChart() {
        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Настроение");
        
        List<MoodEntry> history = db.loadMoodHistory();
        Collections.reverse(history); 
        
        int start = Math.max(0, history.size() - 14); // Show last 2 weeks
        List<MoodEntry> recent = history.subList(start, history.size());
        
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM");
        
        for (MoodEntry e : recent) {
            series.getData().add(new XYChart.Data<>(e.getTimestamp().format(dtf), e.getScore()));
        }
        
        chart.getData().add(series);
    }

    public static InlineView inline() {
        return new MoodAnalysisDialog();
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
        return "Анализ настроения";
    }
}
