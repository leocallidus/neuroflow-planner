package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.MoodEntry;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.service.AIAnalysisCentralService;
import com.example.neuroflowplanner.service.MoodAnalysisService;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unified AI Control Center Dialog.
 * Consolidates analysis, insights, and predictions into a single interactive view.
 */
public class AIAnalysisDialog implements InlineView {

    private final BorderPane root;
    private final List<Task> tasks;
    private final AIAnalysisCentralService aiService;
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final MoodAnalysisService moodService;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

        private AIAnalysisDialog(List<Task> tasks, int initialTab) {
            this.tasks = tasks;
            this.aiService = new AIAnalysisCentralService();
            this.moodService = new MoodAnalysisService();
            this.root = new BorderPane();
            this.root.setMinSize(0, 0);
            this.root.getStyleClass().add("ai-dialog-root");
    
            // Refresh priorities upfront so charts and матрица use актуальные значения
            aiService.recalculateAllPriorities(tasks);
    
            // --- Header ---
            root.setTop(createHeader());
    
            // --- Main Content (Tabs) ---
            TabPane tabPane = new TabPane();
            tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabPane.getStyleClass().add("ai-tab-pane");
            InlineLayoutSupport.makeShrinkable(tabPane);
    
            Tab overviewTab = new Tab("Обзор", createOverviewContent());
            overviewTab.setGraphic(FontIcon.of(MaterialDesignV.VIEW_DASHBOARD, 16));
    
            Tab inspectorTab = new Tab("Детальный анализ", createInspectorContent());
            inspectorTab.setGraphic(FontIcon.of(MaterialDesignM.MAGNIFY, 16));
            
            Tab moodTab = new Tab("Психоэмоциональный фон", createMoodContent());
            moodTab.setGraphic(FontIcon.of(MaterialDesignE.EMOTICON, 16));
    
            Tab predictionsTab = new Tab("Прогнозы", createPredictionsContent());
            predictionsTab.setGraphic(FontIcon.of(MaterialDesignT.TRENDING_UP, 16));
    
            Tab recommendationsTab = new Tab("Рекомендации", createRecommendationsContent());
            recommendationsTab.setGraphic(FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 16));
    
            tabPane.getTabs().addAll(overviewTab, inspectorTab, moodTab, predictionsTab, recommendationsTab);
            
            if (initialTab >= 0 && initialTab < tabPane.getTabs().size()) {
                tabPane.getSelectionModel().select(initialTab);
            }
            
            root.setCenter(tabPane);
    
            // Apply styles
            root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            if (isDark) {
                root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
            }
        }
    
        private Node createRecommendationsContent() {
            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
            InlineLayoutSupport.makeShrinkable(scroll);
            
            FlowPane container = new FlowPane();
            container.setPadding(new Insets(25));
            container.setHgap(20);
            container.setVgap(20);
            
            List<AIAnalysisCentralService.Recommendation> recommendations = aiService.getRecommendations(tasks);
            
            if (recommendations.isEmpty()) {
                VBox emptyBox = new VBox(15);
                emptyBox.setAlignment(Pos.CENTER);
                emptyBox.setPadding(new Insets(60));
                emptyBox.getStyleClass().add("ai-empty-state");
                emptyBox.setMaxWidth(400);
                
                StackPane iconBox = new StackPane();
                iconBox.setMinSize(64, 64);
                iconBox.setMaxSize(64, 64);
                iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(166,227,161,0.15)" : "rgba(64,160,43,0.1)") + "; -fx-background-radius: 50%;");
                FontIcon icon = FontIcon.of(MaterialDesignC.CHECK_ALL, 32);
                icon.setIconColor(Color.web(isDark ? "#a6e3a1" : "#40a02b"));
                iconBox.getChildren().add(icon);
                
                Label lbl = new Label("Всё отлично!");
                lbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
                
                Label sublbl = new Label("Рекомендаций нет. Продолжайте в том же духе!");
                sublbl.getStyleClass().add("ai-empty-text");
                
                emptyBox.getChildren().addAll(iconBox, lbl, sublbl);
                
                StackPane wrapper = new StackPane(emptyBox);
                wrapper.setPadding(new Insets(40));
                scroll.setContent(wrapper);
                return scroll;
            }
    
            for (AIAnalysisCentralService.Recommendation rec : recommendations) {
                container.getChildren().add(createActionCard(rec));
            }
            
            scroll.setContent(container);
            return scroll;
        }
    
            private VBox createActionCard(AIAnalysisCentralService.Recommendation rec) {
                VBox card = new VBox(12);
                card.setPrefWidth(320);
                card.setPadding(new Insets(18));
                card.getStyleClass().add("ai-card");
                
                HBox header = new HBox(12);
                header.setAlignment(Pos.CENTER_LEFT);
                
                StackPane iconBox = new StackPane();
                iconBox.setMinSize(40, 40);
                iconBox.setMaxSize(40, 40);
                
                FontIcon icon;
                String iconBgColor, iconFgColor;
                switch (rec.type()) {
                    case SPLIT -> { 
                        icon = FontIcon.of(MaterialDesignC.CALL_SPLIT, 22);
                        iconBgColor = isDark ? "rgba(250,179,135,0.15)" : "rgba(254,100,11,0.1)";
                        iconFgColor = isDark ? "#fab387" : "#fe640b";
                    }
                    case RESCHEDULE -> { 
                        icon = FontIcon.of(MaterialDesignC.CALENDAR_CLOCK, 22);
                        iconBgColor = isDark ? "rgba(243,139,168,0.15)" : "rgba(210,15,57,0.1)";
                        iconFgColor = isDark ? "#f38ba8" : "#d20f39";
                    }
                    case PRIORITIZE -> { 
                        icon = FontIcon.of(MaterialDesignA.ARROW_UP_BOLD, 22);
                        iconBgColor = isDark ? "rgba(137,180,250,0.15)" : "rgba(30,102,245,0.1)";
                        iconFgColor = isDark ? "#89b4fa" : "#1e66f5";
                    }
                    default -> { 
                        icon = FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 22);
                        iconBgColor = isDark ? "rgba(249,226,175,0.15)" : "rgba(223,142,29,0.1)";
                        iconFgColor = isDark ? "#f9e2af" : "#df8e1d";
                    }
                }
                
                iconBox.setStyle("-fx-background-color: " + iconBgColor + "; -fx-background-radius: 12;");
                icon.setIconColor(Color.web(iconFgColor));
                iconBox.getChildren().add(icon);

                Label titleLbl = new Label(rec.title());
                titleLbl.getStyleClass().add("ai-card-title");
                
                header.getChildren().addAll(iconBox, titleLbl);
                
                Label descLbl = new Label(rec.description());
                descLbl.setWrapText(true);
                descLbl.setPrefHeight(50);
                descLbl.getStyleClass().add("ai-card-desc");
                
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: " + (isDark ? "#45475a" : "#ccd0da") + ";");
                
                Button actionBtn = new Button(rec.actionLabel());
                actionBtn.setMaxWidth(Double.MAX_VALUE);
                actionBtn.getStyleClass().add("action-button");
                actionBtn.setOnAction(e -> handleRecommendationAction(rec, card));
        
                card.getChildren().addAll(header, descLbl, sep, actionBtn);
                return card;
            }    
        public static InlineView inline(List<Task> tasks) {
            return new AIAnalysisDialog(tasks, 0);
        }
        
        public static InlineView inline(List<Task> tasks, int tabIndex) {
            return new AIAnalysisDialog(tasks, tabIndex);
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
        return "ИИ-Центр Управления";
    }

    private Node createMoodContent() {
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(25));
        
        // Left Side: Input Card
        VBox inputCard = new VBox(20);
        inputCard.setPadding(new Insets(22));
        inputCard.setPrefWidth(400);
        inputCard.getStyleClass().addAll("ai-card", "ai-mood-input");
        
        // Header with icon
        HBox inputHeader = new HBox(12);
        inputHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane moodIconBox = new StackPane();
        moodIconBox.setMinSize(40, 40);
        moodIconBox.setMaxSize(40, 40);
        moodIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(249,226,175,0.15)" : "rgba(223,142,29,0.1)") + "; -fx-background-radius: 12;");
        FontIcon moodIcon = FontIcon.of(MaterialDesignE.EMOTICON_HAPPY, 22);
        moodIcon.setIconColor(Color.web(isDark ? "#f9e2af" : "#df8e1d"));
        moodIconBox.getChildren().add(moodIcon);
        Label title = new Label("Как вы себя чувствуете?");
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        inputHeader.getChildren().addAll(moodIconBox, title);
        
        // Score display section
        VBox scoreSection = new VBox(12);
        scoreSection.setPadding(new Insets(16));
        scoreSection.setStyle("-fx-background-color: " + (isDark ? "rgba(49,50,68,0.6)" : "rgba(204,208,218,0.5)") + "; -fx-background-radius: 12;");
        
        HBox scoreRow = new HBox(12);
        scoreRow.setAlignment(Pos.CENTER_LEFT);
        FontIcon scoreIcon = FontIcon.of(MaterialDesignS.STAR, 16);
        scoreIcon.setIconColor(Color.web(isDark ? "#f9e2af" : "#df8e1d"));
        Label scoreLbl = new Label("Текущая оценка:");
        scoreLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        Label scoreVal = new Label("5 - Нормально");
        scoreVal.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f9e2af" : "#df8e1d") + ";");
        scoreRow.getChildren().addAll(scoreIcon, scoreLbl, scoreVal);
        
        Slider slider = new Slider(1, 10, 5);
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.getStyleClass().add("ai-mood-slider");
        slider.valueProperty().addListener((o, oldV, newV) -> {
             int v = newV.intValue();
             String text, color;
             org.kordamp.ikonli.Ikon icon;
             if (v >= 9) { text = "Отлично"; color = isDark ? "#a6e3a1" : "#40a02b"; icon = MaterialDesignE.EMOTICON_EXCITED; }
             else if (v >= 7) { text = "Хорошо"; color = isDark ? "#94e2d5" : "#179299"; icon = MaterialDesignE.EMOTICON_HAPPY; }
             else if (v >= 5) { text = "Нормально"; color = isDark ? "#f9e2af" : "#df8e1d"; icon = MaterialDesignE.EMOTICON_NEUTRAL; }
             else if (v >= 3) { text = "Так себе"; color = isDark ? "#fab387" : "#fe640b"; icon = MaterialDesignE.EMOTICON_SAD; }
             else { text = "Плохо"; color = isDark ? "#f38ba8" : "#d20f39"; icon = MaterialDesignE.EMOTICON_CRY; }
             scoreVal.setText(v + " - " + text);
             scoreVal.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
             moodIcon.setIconCode(icon);
             moodIcon.setIconColor(Color.web(color));
             moodIconBox.setStyle("-fx-background-color: " + hexToRgba(color, 0.15) + "; -fx-background-radius: 12;");
        });
        
        // Scale labels
        HBox scaleLabels = new HBox();
        scaleLabels.setAlignment(Pos.CENTER);
        Label minLabel = new Label("😢 1");
        minLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        Region scaleSpacer = new Region();
        HBox.setHgrow(scaleSpacer, Priority.ALWAYS);
        Label maxLabel = new Label("10 😊");
        maxLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        scaleLabels.getChildren().addAll(minLabel, scaleSpacer, maxLabel);
        
        scoreSection.getChildren().addAll(scoreRow, slider, scaleLabels);
        
        // Notes section
        VBox notesSection = new VBox(10);
        HBox notesHeader = new HBox(10);
        notesHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon notesIcon = FontIcon.of(MaterialDesignN.NOTE_TEXT, 16);
        notesIcon.setIconColor(Color.web(isDark ? "#89b4fa" : "#1e66f5"));
        Label noteLbl = new Label("Заметка о состоянии:");
        noteLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        notesHeader.getChildren().addAll(notesIcon, noteLbl);
        
        TextArea notes = new TextArea();
        notes.setPromptText("Опишите своё состояние, мысли, события дня...");
        notes.setPrefRowCount(4);
        notes.setWrapText(true);
        notes.getStyleClass().add("ai-text-area");
        notesSection.getChildren().addAll(notesHeader, notes);
        
        // Result label
        HBox resultBox = new HBox(10);
        resultBox.setAlignment(Pos.CENTER_LEFT);
        resultBox.setMinHeight(24);
        Label resultLbl = new Label();
        resultLbl.setStyle("-fx-text-fill: " + (isDark ? "#a6e3a1" : "#40a02b") + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        resultBox.getChildren().add(resultLbl);
        
        Button saveBtn = new Button("Сохранить и Анализировать");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.getStyleClass().add("action-button");
        saveBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 16, Color.WHITE));
        
        inputCard.getChildren().addAll(inputHeader, scoreSection, notesSection, saveBtn, resultBox);
        
        // Right Side: Chart Card
        VBox chartCard = new VBox(18);
        chartCard.getStyleClass().add("ai-card");
        chartCard.setPadding(new Insets(22));
        
        // Chart header
        HBox chartHeader = new HBox(12);
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane chartIconBox = new StackPane();
        chartIconBox.setMinSize(40, 40);
        chartIconBox.setMaxSize(40, 40);
        chartIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(137,180,250,0.15)" : "rgba(30,102,245,0.1)") + "; -fx-background-radius: 12;");
        FontIcon chartIcon = FontIcon.of(MaterialDesignC.CHART_LINE, 22);
        chartIcon.setIconColor(Color.web(isDark ? "#89b4fa" : "#1e66f5"));
        chartIconBox.getChildren().add(chartIcon);
        Label chartTitle = new Label("История настроения");
        chartTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Label chartSubtitle = new Label("Последние 14 дней");
        chartSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        VBox chartTitleBox = new VBox(2);
        chartTitleBox.getChildren().addAll(chartTitle, chartSubtitle);
        chartHeader.getChildren().addAll(chartIconBox, chartTitleBox);
        
        // Chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis(0, 10, 1);
        yAxis.setLabel("Оценка");
        
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle(null);
        chart.getStyleClass().addAll("ai-chart", "ai-mood-chart");
        chart.setCreateSymbols(true);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        VBox.setVgrow(chart, Priority.ALWAYS);
        
        Runnable refreshChart = () -> {
            chart.getData().clear();
            XYChart.Series<String, Number> moodSeries = new XYChart.Series<>();
            moodSeries.setName("Настроение");
            
            List<MoodEntry> history = db.loadMoodHistory();
            if (history != null && !history.isEmpty()) {
                 List<MoodEntry> chron = new java.util.ArrayList<>(history);
                 Collections.reverse(chron);
                 
                 int start = Math.max(0, chron.size() - 14);
                 List<MoodEntry> recent = chron.subList(start, chron.size());
                 DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM");
                 
                 for (MoodEntry e : recent) {
                     moodSeries.getData().add(new XYChart.Data<>(e.getTimestamp().format(dtf), e.getScore()));
                 }
                 chart.getData().add(moodSeries);
            }
        };
        
        saveBtn.setOnAction(e -> {
            String txt = notes.getText();
            int score = (int) slider.getValue();
            var res = moodService.analyze(txt, score);
            
            MoodEntry entry = new MoodEntry(
                java.util.UUID.randomUUID().toString(),
                LocalDateTime.now(),
                res.adjustedScore,
                txt,
                res.label
            );
            db.saveMoodEntry(entry);
            resultLbl.setText("✓ Сохранено: " + res.label);
            notes.clear();
            refreshChart.run();
        });
        
        chartCard.getChildren().addAll(chartHeader, chart);
        
        content.setLeft(inputCard);
        content.setCenter(chartCard);
        BorderPane.setMargin(chartCard, new Insets(0, 0, 0, 25));
        
        refreshChart.run();

        return InlineLayoutSupport.createContentScroll(content, "ai-inline-scroll");
    }

    private Node createPredictionsContent() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        InlineLayoutSupport.makeShrinkable(scroll);
        
        VBox content = new VBox(25);
        content.setPadding(new Insets(25));
        
        // === TOP ROW: Summary Cards ===
        HBox summaryRow = new HBox(20);
        summaryRow.setAlignment(Pos.CENTER_LEFT);
        
        // Calculate predictions
        long totalTasks = tasks.stream().filter(t -> !t.isArchived()).count();
        long tasksWithDeadline = tasks.stream().filter(t -> !t.isArchived() && t.getDeadline() != null).count();
        long overdueTasks = tasks.stream().filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now())).count();
        long upcomingTasks = tasks.stream().filter(t -> !t.isArchived() && t.getDeadline() != null && 
            !t.getDeadline().isBefore(LocalDate.now()) && t.getDeadline().isBefore(LocalDate.now().plusDays(7))).count();
        
        double avgComplexity = tasks.stream().filter(t -> !t.isArchived()).mapToInt(Task::getComplexity).average().orElse(0);
        int estimatedHoursWeek = (int) (upcomingTasks * (avgComplexity * 0.5 + 1));
        
        // Summary card 1: Workload
        VBox workloadCard = createPredictionSummaryCard(
            "Нагрузка на неделю",
            estimatedHoursWeek + " ч",
            upcomingTasks + " задач",
            MaterialDesignC.CLOCK_OUTLINE,
            isDark ? "#89b4fa" : "#1e66f5",
            estimatedHoursWeek > 40 ? "high" : estimatedHoursWeek > 20 ? "medium" : "low"
        );
        
        // Summary card 2: Risk
        int riskPercent = totalTasks > 0 ? (int) ((overdueTasks * 100) / totalTasks) : 0;
        VBox riskCard = createPredictionSummaryCard(
            "Риск срыва",
            riskPercent + "%",
            overdueTasks + " просрочено",
            MaterialDesignA.ALERT_CIRCLE_OUTLINE,
            riskPercent > 30 ? (isDark ? "#f38ba8" : "#d20f39") : riskPercent > 10 ? (isDark ? "#f9e2af" : "#df8e1d") : (isDark ? "#a6e3a1" : "#40a02b"),
            riskPercent > 30 ? "high" : riskPercent > 10 ? "medium" : "low"
        );
        
        // Summary card 3: Completion forecast
        int completionDays = upcomingTasks > 0 ? (int) Math.ceil(upcomingTasks * avgComplexity / 3.0) : 0;
        VBox completionCard = createPredictionSummaryCard(
            "Прогноз завершения",
            completionDays + " дн",
            "при текущем темпе",
            MaterialDesignC.CALENDAR_CHECK,
            isDark ? "#a6e3a1" : "#40a02b",
            "low"
        );
        
        HBox.setHgrow(workloadCard, Priority.ALWAYS);
        HBox.setHgrow(riskCard, Priority.ALWAYS);
        HBox.setHgrow(completionCard, Priority.ALWAYS);
        summaryRow.getChildren().addAll(workloadCard, riskCard, completionCard);
        
        // === DEADLINE TIMELINE ===
        VBox timelineSection = new VBox(18);
        timelineSection.getStyleClass().add("ai-card");
        timelineSection.setPadding(new Insets(22));
        
        HBox timelineHeader = new HBox(12);
        timelineHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane timelineIconBox = new StackPane();
        timelineIconBox.setMinSize(40, 40);
        timelineIconBox.setMaxSize(40, 40);
        timelineIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(203,166,247,0.15)" : "rgba(136,57,239,0.1)") + "; -fx-background-radius: 12;");
        FontIcon timelineIcon = FontIcon.of(MaterialDesignT.TIMELINE_CLOCK, 22);
        timelineIcon.setIconColor(Color.web(isDark ? "#cba6f7" : "#8839ef"));
        timelineIconBox.getChildren().add(timelineIcon);
        
        VBox timelineTitleBox = new VBox(2);
        Label timelineTitle = new Label("Прогноз дедлайнов");
        timelineTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Label timelineSubtitle = new Label("Ближайшие 14 дней");
        timelineSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        timelineTitleBox.getChildren().addAll(timelineTitle, timelineSubtitle);
        timelineHeader.getChildren().addAll(timelineIconBox, timelineTitleBox);
        
        VBox timelineContent = new VBox(12);
        timelineContent.setPadding(new Insets(15, 0, 0, 0));
        
        // Group tasks by deadline
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            LocalDate date = today.plusDays(i);
            List<Task> dayTasks = tasks.stream()
                .filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().equals(date))
                .collect(Collectors.toList());
            
            if (!dayTasks.isEmpty()) {
                timelineContent.getChildren().add(createTimelineDay(date, dayTasks, i == 0));
            }
        }
        
        if (timelineContent.getChildren().isEmpty()) {
            Label emptyLabel = new Label("Нет задач с дедлайнами на ближайшие 2 недели");
            emptyLabel.setStyle("-fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + "; -fx-font-size: 13px;");
            emptyLabel.setPadding(new Insets(20));
            timelineContent.getChildren().add(emptyLabel);
        }
        
        timelineSection.getChildren().addAll(timelineHeader, timelineContent);
        
        // === AI PREDICTIONS SECTION ===
        VBox aiSection = new VBox(18);
        aiSection.getStyleClass().add("ai-card");
        aiSection.setPadding(new Insets(22));
        
        HBox aiHeader = new HBox(12);
        aiHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane aiIconBox = new StackPane();
        aiIconBox.setMinSize(40, 40);
        aiIconBox.setMaxSize(40, 40);
        aiIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(137,180,250,0.15)" : "rgba(30,102,245,0.1)") + "; -fx-background-radius: 12;");
        FontIcon aiIcon = FontIcon.of(MaterialDesignB.BRAIN, 22);
        aiIcon.setIconColor(Color.web(isDark ? "#89b4fa" : "#1e66f5"));
        aiIconBox.getChildren().add(aiIcon);
        
        VBox aiTitleBox = new VBox(2);
        Label aiTitle = new Label("ИИ-Прогнозы");
        aiTitle.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Label aiSubtitle = new Label("Анализ на основе ваших данных");
        aiSubtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        aiTitleBox.getChildren().addAll(aiTitle, aiSubtitle);
        
        Region aiSpacer = new Region();
        HBox.setHgrow(aiSpacer, Priority.ALWAYS);
        
        Button refreshBtn = new Button("Обновить");
        refreshBtn.setGraphic(FontIcon.of(MaterialDesignR.REFRESH, 14, Color.WHITE));
        refreshBtn.getStyleClass().add("action-button");
        
        aiHeader.getChildren().addAll(aiIconBox, aiTitleBox, aiSpacer, refreshBtn);
        
        VBox aiPredictions = new VBox(14);
        aiPredictions.setPadding(new Insets(15, 0, 0, 0));
        
        // Generate AI predictions
        TextArea aiResultArea = new TextArea();
        aiResultArea.setEditable(false);
        aiResultArea.setWrapText(true);
        aiResultArea.setPrefHeight(220);
        aiResultArea.setMinHeight(180);
        aiResultArea.setMaxWidth(Double.MAX_VALUE);
        aiResultArea.getStyleClass().add("ai-text-area");
        aiResultArea.setText(generateAIPredictions());
        VBox.setVgrow(aiResultArea, Priority.ALWAYS);
        
        refreshBtn.setOnAction(e -> {
            aiResultArea.setText("⏳ Анализирую данные...");
            AsyncContext.supplyAsync(() -> generateAIPredictions())
                .thenAccept(result -> javafx.application.Platform.runLater(() -> aiResultArea.setText(result)));
        });
        
        aiPredictions.getChildren().add(aiResultArea);
        aiSection.getChildren().addAll(aiHeader, aiPredictions);
        
        content.getChildren().addAll(summaryRow, timelineSection, aiSection);
        scroll.setContent(content);
        return scroll;
    }
    
    private VBox createPredictionSummaryCard(String title, String value, String subtitle, org.kordamp.ikonli.Ikon iconCode, String color, String level) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("ai-card");
        
        String bgColor = level.equals("high") ? (isDark ? "rgba(243,139,168,0.1)" : "rgba(210,15,57,0.08)") :
                         level.equals("medium") ? (isDark ? "rgba(249,226,175,0.1)" : "rgba(223,142,29,0.08)") :
                         (isDark ? "rgba(166,227,161,0.1)" : "rgba(64,160,43,0.08)");
        card.setStyle("-fx-background-color: " + bgColor + ";");
        
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(48, 48);
        iconBox.setMaxSize(48, 48);
        String iconBgColor = hexToRgba(color, 0.2);
        iconBox.setStyle("-fx-background-color: " + iconBgColor + "; -fx-background-radius: 14;");
        FontIcon icon = FontIcon.of(iconCode, 24);
        icon.setIconColor(Color.web(color));
        iconBox.getChildren().add(icon);
        
        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        
        card.getChildren().addAll(iconBox, valueLabel, titleLabel, subtitleLabel);
        return card;
    }
    
    private HBox createTimelineDay(LocalDate date, List<Task> dayTasks, boolean isToday) {
        HBox row = new HBox(15);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        
        String bgColor = isToday ? (isDark ? "rgba(137,180,250,0.15)" : "rgba(30,102,245,0.1)") :
                         (isDark ? "rgba(49,50,68,0.5)" : "rgba(204,208,218,0.4)");
        row.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 12;");
        
        // Date column
        VBox dateBox = new VBox(2);
        dateBox.setAlignment(Pos.CENTER);
        dateBox.setMinWidth(60);
        
        String dayName = isToday ? "Сегодня" : getDayAbbrev(date.getDayOfWeek());
        Label dayLabel = new Label(dayName);
        dayLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isToday ? (isDark ? "#89b4fa" : "#1e66f5") : (isDark ? "#a6adc8" : "#6c6f85")) + "; -fx-font-weight: bold;");
        
        Label dateLabel = new Label(date.getDayOfMonth() + "." + date.getMonthValue());
        dateLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        dateBox.getChildren().addAll(dayLabel, dateLabel);
        
        // Separator
        javafx.scene.shape.Line separator = new javafx.scene.shape.Line(0, 0, 0, 30);
        separator.setStroke(Color.web(isDark ? "#45475a" : "#ccd0da"));
        separator.setStrokeWidth(2);
        
        // Tasks
        VBox tasksBox = new VBox(6);
        HBox.setHgrow(tasksBox, Priority.ALWAYS);
        
        for (Task task : dayTasks) {
            HBox taskRow = new HBox(8);
            taskRow.setAlignment(Pos.CENTER_LEFT);
            
            Circle dot = new Circle(4);
            double priority = task.getSmartPriority();
            String dotColor = priority >= 7 ? (isDark ? "#f38ba8" : "#d20f39") :
                              priority >= 4 ? (isDark ? "#f9e2af" : "#df8e1d") :
                              (isDark ? "#a6e3a1" : "#40a02b");
            dot.setFill(Color.web(dotColor));
            
            Label taskLabel = new Label(task.getTitle());
            taskLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
            
            Label complexityLabel = new Label("⚡" + task.getComplexity());
            complexityLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
            
            taskRow.getChildren().addAll(dot, taskLabel, complexityLabel);
            tasksBox.getChildren().add(taskRow);
        }
        
        // Count badge
        Label countBadge = new Label(String.valueOf(dayTasks.size()));
        countBadge.setStyle("-fx-background-color: " + (isDark ? "#cba6f7" : "#8839ef") + "; " +
                          "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                          "-fx-font-size: 11px; -fx-font-weight: bold; " +
                          "-fx-padding: 4 10; -fx-background-radius: 12;");
        
        row.getChildren().addAll(dateBox, separator, tasksBox, countBadge);
        return row;
    }

    private String getDayAbbrev(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "ПН";
            case TUESDAY -> "ВТ";
            case WEDNESDAY -> "СР";
            case THURSDAY -> "ЧТ";
            case FRIDAY -> "ПТ";
            case SATURDAY -> "СБ";
            case SUNDAY -> "ВС";
        };
    }
    
    private String generateAIPredictions() {
        StringBuilder sb = new StringBuilder();
        
        long totalTasks = tasks.stream().filter(t -> !t.isArchived()).count();
        long completedRecently = tasks.stream().filter(Task::isArchived).count();
        double avgComplexity = tasks.stream().filter(t -> !t.isArchived()).mapToInt(Task::getComplexity).average().orElse(0);
        long highPriorityTasks = tasks.stream().filter(t -> !t.isArchived() && t.getSmartPriority() >= 7).count();
        long overdueTasks = tasks.stream().filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now())).count();
        
        sb.append("🔮 ПРОГНОЗ ПРОДУКТИВНОСТИ\n\n");
        
        // Workload analysis
        if (avgComplexity > 7) {
            sb.append("⚠️ Высокая сложность задач (").append(String.format("%.1f", avgComplexity)).append("/10)\n");
            sb.append("   Рекомендуется разбить крупные задачи на подзадачи.\n\n");
        } else if (avgComplexity > 4) {
            sb.append("📊 Средняя сложность задач (").append(String.format("%.1f", avgComplexity)).append("/10)\n");
            sb.append("   Нагрузка в пределах нормы.\n\n");
        } else {
            sb.append("✅ Низкая сложность задач (").append(String.format("%.1f", avgComplexity)).append("/10)\n");
            sb.append("   Можно взять дополнительные задачи.\n\n");
        }
        
        // Priority analysis
        if (highPriorityTasks > 5) {
            sb.append("🔴 Много срочных задач: ").append(highPriorityTasks).append("\n");
            sb.append("   Риск выгорания. Пересмотрите приоритеты.\n\n");
        } else if (highPriorityTasks > 0) {
            sb.append("🟡 Срочных задач: ").append(highPriorityTasks).append("\n");
            sb.append("   Сфокусируйтесь на них в первую очередь.\n\n");
        }
        
        // Overdue analysis
        if (overdueTasks > 0) {
            sb.append("❌ Просроченных задач: ").append(overdueTasks).append("\n");
            sb.append("   Необходимо срочно обработать или перенести.\n\n");
        }
        
        // Forecast
        sb.append("📈 ПРОГНОЗ НА НЕДЕЛЮ\n");
        int estimatedCompletion = (int) Math.min(totalTasks, 7 / Math.max(avgComplexity * 0.3, 1));
        sb.append("   При текущем темпе вы завершите ~").append(estimatedCompletion).append(" задач.\n");
        
        if (totalTasks > 0) {
            int daysToComplete = (int) Math.ceil(totalTasks * avgComplexity / 5.0);
            sb.append("   Все задачи будут выполнены за ~").append(daysToComplete).append(" дней.\n");
        }
        
        return sb.toString();
    }
    
    private String hexToRgba(String hex, double alpha) {
        String cleanHex = hex.replace("#", "");
        int r = Integer.parseInt(cleanHex.substring(0, 2), 16);
        int g = Integer.parseInt(cleanHex.substring(2, 4), 16);
        int b = Integer.parseInt(cleanHex.substring(4, 6), 16);
        return "rgba(" + r + "," + g + "," + b + "," + alpha + ")";
    }
    
    private void handleRecommendationAction(AIAnalysisCentralService.Recommendation rec, VBox card) {
        Task task = rec.relatedTask();
        
        switch (rec.type()) {
            case SPLIT -> {
                if (task == null) return;
                showSplitTaskDialog(task, card);
            }
            case RESCHEDULE -> {
                showRescheduleDialog(task, card);
            }
            case PRIORITIZE -> {
                if (task != null) {
                    task.setSmartPriority(Math.min(10.0, task.getSmartPriority() + 1.0));
                    showSuccessMessage(card, "Приоритет повышен!");
                }
            }
        }
    }
    
    private void showSplitTaskDialog(Task task, VBox card) {
        Dialog<List<String>> dialog = new Dialog<>();
        dialog.setTitle("Разбить задачу");
        dialog.setHeaderText(null);
        
        // Create styled dialog pane
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(500);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        
        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(44, 44);
        iconBox.setMaxSize(44, 44);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(250,179,135,0.15)" : "rgba(254,100,11,0.1)") + "; -fx-background-radius: 12;");
        FontIcon icon = FontIcon.of(MaterialDesignC.CALL_SPLIT, 24);
        icon.setIconColor(Color.web(isDark ? "#fab387" : "#fe640b"));
        iconBox.getChildren().add(icon);
        
        VBox titleBox = new VBox(2);
        Label titleLbl = new Label("Разбить на подзадачи");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Label subtitleLbl = new Label("Задача: " + task.getTitle());
        subtitleLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        titleBox.getChildren().addAll(titleLbl, subtitleLbl);
        header.getChildren().addAll(iconBox, titleBox);
        
        // Info
        Label infoLbl = new Label("Введите названия подзадач (каждая с новой строки):");
        infoLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        // TextArea for subtasks
        TextArea subtasksArea = new TextArea();
        subtasksArea.setPromptText("Подзадача 1\nПодзадача 2\nПодзадача 3");
        subtasksArea.setPrefRowCount(6);
        subtasksArea.setWrapText(true);
        subtasksArea.getStyleClass().add("ai-text-area");
        
        // Suggestion + AI helper
        Label suggestionLbl = new Label("💡 Совет: разбейте сложную задачу на 3-5 простых шагов");
        suggestionLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");

        boolean aiAvailable = isAiConfigured();
        Button aiSplitBtn = new Button("Разбить задачу с помощью ИИ");
        aiSplitBtn.setMaxWidth(Double.MAX_VALUE);
        aiSplitBtn.getStyleClass().add("action-button");
        aiSplitBtn.setDisable(!aiAvailable);
        aiSplitBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 16, Color.WHITE));
        aiSplitBtn.setOnAction(e -> {
            aiSplitBtn.setDisable(true);
            String loadingText = "⏳ Генерация подзадач...";
            subtasksArea.setText(loadingText);
            AsyncContext.supplyAsync(() -> generateAiSubtasks(task))
                .exceptionally(ex -> List.of("Подзадача 1: " + task.getTitle(),
                                             "Подзадача 2: уточнить требования",
                                             "Подзадача 3: проверить результат"))
                .thenAccept(list -> javafx.application.Platform.runLater(() -> {
                    subtasksArea.setText(String.join("\n", list));
                    aiSplitBtn.setDisable(false);
                }));
        });
        Label aiNote = new Label(aiAvailable ? "ИИ использует краткие подсказки по задаче" : "ИИ недоступен (проверьте api.url / api.model)");
        aiNote.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");

        VBox aiBox = new VBox(6, aiSplitBtn, aiNote);
        aiBox.setMaxWidth(Double.MAX_VALUE);

        // Complexity & Priority controls for subtasks
        Label complexityLbl = new Label("Сложность подзадач");
        complexityLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Slider complexitySlider = new Slider(1, 10, Math.max(1, task.getComplexity() / 2.0));
        complexitySlider.setMajorTickUnit(1);
        complexitySlider.setMinorTickCount(0);
        complexitySlider.setShowTickMarks(true);
        complexitySlider.setShowTickLabels(true);
        complexitySlider.setSnapToTicks(true);
        Label complexityVal = new Label(String.format("%.0f/10", complexitySlider.getValue()));
        complexityVal.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        complexitySlider.valueProperty().addListener((obs, o, v) -> complexityVal.setText(String.format("%.0f/10", v.doubleValue())));

        Label priorityLbl = new Label("ИИ-приоритет подзадач");
        priorityLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Slider prioritySlider = new Slider(0, 10, task.getSmartPriority() > 0 ? Math.min(10, task.getSmartPriority()) : 5);
        prioritySlider.setMajorTickUnit(1);
        prioritySlider.setMinorTickCount(0);
        prioritySlider.setShowTickMarks(true);
        prioritySlider.setShowTickLabels(true);
        prioritySlider.setSnapToTicks(true);
        Label priorityVal = new Label(String.format("%.1f/10", prioritySlider.getValue()));
        priorityVal.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        prioritySlider.valueProperty().addListener((obs, o, v) -> priorityVal.setText(String.format("%.1f/10", v.doubleValue())));

        Button aiTuneBtn = new Button("Подобрать сложность и приоритет (ИИ)");
        aiTuneBtn.setMaxWidth(Double.MAX_VALUE);
        aiTuneBtn.getStyleClass().add("action-button");
        aiTuneBtn.setDisable(!aiAvailable);
        aiTuneBtn.setGraphic(FontIcon.of(MaterialDesignA.AUTORENEW, 16, Color.WHITE));
        aiTuneBtn.setOnAction(e -> {
            aiTuneBtn.setDisable(true);
            aiTuneBtn.setText("⏳ Подбираем значения...");
            AsyncContext.supplyAsync(() -> generateAiSplitEstimate(task))
                .exceptionally(ex -> new SplitEstimate(Math.max(1, task.getComplexity() / 2), task.getSmartPriority() > 0 ? task.getSmartPriority() : 5))
                .thenAccept(estimate -> javafx.application.Platform.runLater(() -> {
                    complexitySlider.setValue(estimate.complexity());
                    prioritySlider.setValue(estimate.priority());
                    aiTuneBtn.setText("Подобрать сложность и приоритет (ИИ)");
                    aiTuneBtn.setDisable(false);
                }));
        });

        VBox complexityBox = new VBox(6, complexityLbl, complexitySlider, complexityVal);
        VBox priorityBox = new VBox(6, priorityLbl, prioritySlider, priorityVal);
        VBox slidersBox = new VBox(10, complexityBox, priorityBox, aiTuneBtn);
        slidersBox.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(header, infoLbl, subtasksArea, aiBox, slidersBox, suggestionLbl);
        dialogPane.setContent(content);
        
        // Buttons
        ButtonType createBtn = new ButtonType("Создать подзадачи", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(createBtn, cancelBtn);
        
        // Style buttons
        Button okButton = (Button) dialogPane.lookupButton(createBtn);
        okButton.getStyleClass().add("action-button");
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == createBtn) {
                String text = subtasksArea.getText().trim();
                if (!text.isEmpty()) {
                    return java.util.Arrays.asList(text.split("\\n"));
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(subtaskNames -> {
            if (!subtaskNames.isEmpty()) {
                int created = 0;
                for (String name : subtaskNames) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        Task subtask = new Task(trimmed, "", task.getDeadline(), (int) Math.round(complexitySlider.getValue()));
                        subtask.setParentId(task.getId());
                        subtask.setDeadlineTime(task.getDeadlineTime());
                        subtask.setSmartPriority(Math.round(prioritySlider.getValue() * 10.0) / 10.0);
                        task.getSubtasks().add(subtask);
                        db.saveTask(subtask);
                        created++;
                    }
                }
                if (created > 0) {
                    showSuccessMessage(card, "Создано " + created + " подзадач!");
                }
            }
        });
    }
    
    private void showRescheduleDialog(Task task, VBox card) {
        if (task == null) {
            showSuccessMessage(card, "Перенесите задачи вручную в календаре");
            return;
        }
        
        Dialog<LocalDate> dialog = new Dialog<>();
        dialog.setTitle("Перенести задачу");
        dialog.setHeaderText(null);
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(400);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        
        // Header
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(44, 44);
        iconBox.setMaxSize(44, 44);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.15)" : "rgba(210,15,57,0.1)") + "; -fx-background-radius: 12;");
        FontIcon icon = FontIcon.of(MaterialDesignC.CALENDAR_CLOCK, 24);
        icon.setIconColor(Color.web(isDark ? "#f38ba8" : "#d20f39"));
        iconBox.getChildren().add(icon);
        
        VBox titleBox = new VBox(2);
        Label titleLbl = new Label("Перенести дедлайн");
        titleLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Label subtitleLbl = new Label("Задача: " + task.getTitle());
        subtitleLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        titleBox.getChildren().addAll(titleLbl, subtitleLbl);
        header.getChildren().addAll(iconBox, titleBox);
        
        Label infoLbl = new Label("Выберите новую дату:");
        infoLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(3));
        datePicker.setMaxWidth(Double.MAX_VALUE);
        
        content.getChildren().addAll(header, infoLbl, datePicker);
        dialogPane.setContent(content);
        
        ButtonType saveBtn = new ButtonType("Перенести", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(saveBtn, cancelBtn);
        
        Button okButton = (Button) dialogPane.lookupButton(saveBtn);
        okButton.getStyleClass().add("action-button");
        
        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveBtn) {
                return datePicker.getValue();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(newDate -> {
            task.deadlineProperty().set(newDate);
            showSuccessMessage(card, "Дедлайн перенесён на " + newDate);
        });
    }
    
    private void showSuccessMessage(VBox card, String message) {
        Label successLbl = new Label("✅ " + message);
        successLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#a6e3a1" : "#40a02b") + ";");
        successLbl.setPadding(new Insets(8, 0, 0, 0));
        
        // Remove old success messages
        card.getChildren().removeIf(node -> node instanceof Label && ((Label) node).getText().startsWith("✅"));
        card.getChildren().add(successLbl);
        
        // Fade out after 3 seconds
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(e -> card.getChildren().remove(successLbl));
        pause.play();
    }

    private Node createInspectorContent() {
        BorderPane content = new BorderPane();
        content.setPadding(new Insets(25));

        // 1. Task Selection & Matrix (Left side)
        VBox leftPane = new VBox(20);
        leftPane.setPrefWidth(520);
        leftPane.getStyleClass().add("ai-card");
        leftPane.setPadding(new Insets(22));
        
        // Header for left panel
        HBox leftHeader = new HBox(12);
        leftHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane selectIconBox = new StackPane();
        selectIconBox.setMinSize(36, 36);
        selectIconBox.setMaxSize(36, 36);
        selectIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(137,180,250,0.15)" : "rgba(30,102,245,0.1)") + "; -fx-background-radius: 10;");
        FontIcon selectIcon = FontIcon.of(MaterialDesignM.MAGNIFY, 18);
        selectIcon.setIconColor(Color.web(isDark ? "#89b4fa" : "#1e66f5"));
        selectIconBox.getChildren().add(selectIcon);
        Label leftTitle = new Label("Выбор и анализ задачи");
        leftTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        leftHeader.getChildren().addAll(selectIconBox, leftTitle);
        
        Label selectLbl = new Label("Выберите задачу для анализа:");
        selectLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        
        ComboBox<Task> taskCombo = new ComboBox<>(FXCollections.observableArrayList(tasks));
        taskCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getTitle());
            }
        });
        taskCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item.getTitle());
            }
        });
        taskCombo.setMaxWidth(Double.MAX_VALUE);
        taskCombo.getStyleClass().add("ai-combo-box");
        taskCombo.setPromptText("Выберите задачу...");
        
        // Scatter Chart: Complexity vs Importance (Smart Priority)
        NumberAxis xAxis = new NumberAxis(0, 10, 1);
        xAxis.setAutoRanging(false);
        xAxis.setLabel("Сложность");
        NumberAxis yAxis = new NumberAxis(0, 10, 1);
        yAxis.setAutoRanging(false);
        yAxis.setLabel("Важность (ИИ, 0-10)");
        
        ScatterChart<Number, Number> scatter = new ScatterChart<>(xAxis, yAxis);
        scatter.setTitle("Матрица Ценности");
        scatter.setAnimated(false);
        scatter.setLegendVisible(false);
        scatter.getStyleClass().addAll("ai-chart", "ai-scatter-chart");
        scatter.setPrefHeight(280);
        scatter.setMinHeight(260);
        scatter.setMaxWidth(Double.MAX_VALUE);
        Pane scatterOverlay = new Pane();
        scatterOverlay.setPickOnBounds(false);
        scatterOverlay.setMouseTransparent(false);
        scatterOverlay.setManaged(false);
        
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Задачи");
        java.util.Map<XYChart.Data<Number, Number>, Task> dataTaskMap = new java.util.HashMap<>();
        
        for (Task t : tasks) {
            if (t.isArchived()) continue;
            double priorityScore = t.getSmartPriority() > 0
                ? t.getSmartPriority()
                : (t.getComplexity() * 0.8 + Math.random() * 2);
            double yValue = Double.isNaN(priorityScore) ? 0 : Math.max(0, Math.min(10, priorityScore));
            int complexity = Math.max(0, Math.min(10, t.getComplexity()));
            XYChart.Data<Number, Number> data = new XYChart.Data<>(complexity, yValue);
            series.getData().add(data);
            dataTaskMap.put(data, t);
        }
        scatter.getData().add(series);
        Label emptyMatrixLbl = new Label("Нет активных задач для матрицы");
        emptyMatrixLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        emptyMatrixLbl.setMouseTransparent(true);
        emptyMatrixLbl.visibleProperty().bind(javafx.beans.binding.Bindings.isEmpty(series.getData()));
        emptyMatrixLbl.managedProperty().bind(emptyMatrixLbl.visibleProperty());
        
        StackPane scatterContainer = new StackPane(scatter, emptyMatrixLbl);
        scatterContainer.setAlignment(Pos.CENTER);
        VBox.setVgrow(scatterContainer, Priority.ALWAYS);
        
        styleScatterPoints(series, dataTaskMap, taskCombo);
        attachOverlay(scatter, scatterOverlay, xAxis, yAxis, series, dataTaskMap, taskCombo);

        javafx.beans.property.IntegerProperty matrixCount = new javafx.beans.property.SimpleIntegerProperty(series.getData().size());
        series.getData().addListener((javafx.collections.ListChangeListener<XYChart.Data<Number, Number>>) change -> 
            matrixCount.set(series.getData().size()));
        Label matrixCountLbl = new Label();
        matrixCountLbl.textProperty().bind(javafx.beans.binding.Bindings.format("На матрице: %d задач", matrixCount));
        matrixCountLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");

        leftPane.getChildren().addAll(leftHeader, selectLbl, taskCombo, scatterContainer, matrixCountLbl);

        // 2. Detail Analysis Panel (Right side)
        VBox rightPane = new VBox(22);
        rightPane.setPadding(new Insets(22));
        rightPane.setPrefWidth(420);
        rightPane.getStyleClass().add("ai-card");
        
        // Header for right panel
        HBox rightHeader = new HBox(12);
        rightHeader.setAlignment(Pos.CENTER_LEFT);
        StackPane analyzeIconBox = new StackPane();
        analyzeIconBox.setMinSize(36, 36);
        analyzeIconBox.setMaxSize(36, 36);
        analyzeIconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(203,166,247,0.15)" : "rgba(136,57,239,0.1)") + "; -fx-background-radius: 10;");
        FontIcon analyzeIcon = FontIcon.of(MaterialDesignB.BRAIN, 18);
        analyzeIcon.setIconColor(Color.web(isDark ? "#cba6f7" : "#8839ef"));
        analyzeIconBox.getChildren().add(analyzeIcon);
        Label detailTitle = new Label("Результаты анализа");
        detailTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        rightHeader.getChildren().addAll(analyzeIconBox, detailTitle);
        
        Button analyzeBtn = new Button("Запустить анализ ИИ");
        analyzeBtn.setGraphic(FontIcon.of(MaterialDesignP.PLAY, 16, Color.WHITE));
        analyzeBtn.getStyleClass().add("action-button");
        analyzeBtn.setMaxWidth(Double.MAX_VALUE);
        analyzeBtn.setDisable(true);
        
        // SMART Check section
        VBox smartBox = new VBox(10);
        smartBox.getStyleClass().add("ai-result-box");
        smartBox.setPadding(new Insets(16));
        smartBox.setStyle("-fx-background-color: " + (isDark ? "rgba(49,50,68,0.6)" : "rgba(204,208,218,0.5)") + "; -fx-background-radius: 12;");
        
        HBox smartHeader = new HBox(10);
        smartHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon smartIcon = FontIcon.of(MaterialDesignC.CHECK_DECAGRAM, 16);
        smartIcon.setIconColor(Color.web(isDark ? "#a6e3a1" : "#40a02b"));
        Label smartLbl = new Label("SMART-Проверка");
        smartLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        smartHeader.getChildren().addAll(smartIcon, smartLbl);
        
        TextArea smartReport = new TextArea();
        smartReport.setEditable(false);
        smartReport.setWrapText(true);
        smartReport.setPrefHeight(130);
        smartReport.setPromptText("Выберите задачу и нажмите 'Запустить анализ'...");
        smartReport.getStyleClass().add("ai-text-area");
        smartBox.getChildren().addAll(smartHeader, smartReport);
        
        // Time prediction section
        VBox timeBox = new VBox(12);
        timeBox.getStyleClass().add("ai-result-box");
        timeBox.setPadding(new Insets(16));
        timeBox.setMinHeight(120);
        timeBox.setStyle("-fx-background-color: " + (isDark ? "rgba(49,50,68,0.6)" : "rgba(204,208,218,0.5)") + "; -fx-background-radius: 12;");
        
        HBox timeHeader = new HBox(10);
        timeHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon timeIcon = FontIcon.of(MaterialDesignC.CLOCK_OUTLINE, 16);
        timeIcon.setIconColor(Color.web(isDark ? "#89b4fa" : "#1e66f5"));
        Label timeLbl = new Label("Прогноз времени выполнения");
        timeLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        timeHeader.getChildren().addAll(timeIcon, timeLbl);
        
        Label timeResult = new Label("-");
        timeResult.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#89b4fa" : "#1e66f5") + ";");
        timeResult.setWrapText(true);
        
        Label timeHint = new Label("На основе сложности и исторических данных");
        timeHint.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
        timeHint.setWrapText(true);
        
        timeBox.getChildren().addAll(timeHeader, timeResult, timeHint);
        
        // Wire up selection
        taskCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            analyzeBtn.setDisable(newVal == null);
            if (newVal != null) {
                String savedInsight = newVal.getAiInsight();
                if (savedInsight != null && !savedInsight.isBlank()) {
                    smartReport.setText(savedInsight);
                } else {
                    smartReport.setText("Нажмите 'Запустить анализ'...");
                }
                timeResult.setText("-");
            }
        });
        
        analyzeBtn.setOnAction(e -> {
            Task t = taskCombo.getValue();
            if (t == null) return;
            
            smartReport.setText("⏳ Анализирую...");
            timeResult.setText("⏳");
            
            aiService.analyzeSmartCriteria(t).thenAccept(report ->
                javafx.application.Platform.runLater(() -> {
                    smartReport.setText(report);
                    t.setAiInsight(report);
                    db.saveTask(t);
                })
            );
            
            aiService.predictTaskTime(t).thenAccept(prediction -> 
                javafx.application.Platform.runLater(() -> timeResult.setText(prediction))
            );
        });
        
        rightPane.getChildren().addAll(rightHeader, analyzeBtn, smartBox, timeBox);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        content.setLeft(leftPane);
        content.setCenter(rightPane);
        BorderPane.setMargin(rightPane, new Insets(0, 0, 0, 25));

        return InlineLayoutSupport.createContentScroll(content, "ai-inline-scroll");
    }

    private void styleScatterPoints(XYChart.Series<Number, Number> series,
                                    java.util.Map<XYChart.Data<Number, Number>, Task> dataTaskMap,
                                    ComboBox<Task> taskCombo) {
        ListChangeListener<XYChart.Data<Number, Number>> listener = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (XYChart.Data<Number, Number> data : change.getAddedSubList()) {
                        attachPointNode(dataTaskMap, taskCombo, data);
                    }
                }
            }
        };
        series.getData().addListener(listener);
        series.getData().forEach(data -> attachPointNode(dataTaskMap, taskCombo, data));
    }

    private void attachPointNode(java.util.Map<XYChart.Data<Number, Number>, Task> dataTaskMap,
                                 ComboBox<Task> taskCombo,
                                 XYChart.Data<Number, Number> data) {
        Task task = dataTaskMap.get(data);
        if (task == null) return;

        double priority = data.getYValue().doubleValue();
        String color = priority >= 7 ? (isDark ? "#f38ba8" : "#d20f39") :
                       priority >= 4 ? (isDark ? "#f9e2af" : "#df8e1d") :
                       (isDark ? "#a6e3a1" : "#40a02b");

        Circle circle = new Circle(7);
        circle.setFill(Color.web(color));
        circle.setStroke(Color.web(isDark ? "#313244" : "#ffffff"));
        circle.setStrokeWidth(2);
        circle.setCursor(javafx.scene.Cursor.HAND);

        Tooltip tooltip = new Tooltip(task.getTitle() + "\nСложность: " + task.getComplexity() +
            "\nПриоритет: " + String.format("%.1f/10", priority));
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(circle, tooltip);

        circle.setOnMouseClicked(e -> taskCombo.setValue(task));

        data.setNode(circle);
    }

    private boolean isAiConfigured() {
        String url = ConfigManager.getProperty("api.url");
        String model = ConfigManager.getProperty("api.model");
        return url != null && !url.isBlank() && model != null && !model.isBlank();
    }

    private List<String> generateAiSubtasks(Task task) {
        String title = task.getTitle() != null ? task.getTitle() : "Задача";
        int complexity = task.getComplexity();
        String base = title.length() > 50 ? title.substring(0, 50) + "…" : title;

        List<String> subtasks = new java.util.ArrayList<>();
        subtasks.add("Подзадача 1: уточнить критерии успеха для \"" + base + "\"");
        subtasks.add("Подзадача 2: выполнить основной шаг (сложность " + complexity + "/10)");
        subtasks.add("Подзадача 3: проверить результат и зафиксировать итоги");
        return subtasks;
    }

    private SplitEstimate generateAiSplitEstimate(Task task) {
        int baseComplexity = Math.max(1, Math.min(10, task.getComplexity() - 1));
        double basePriority = task.getSmartPriority() > 0 ? Math.min(10, task.getSmartPriority()) : Math.min(10, task.getComplexity() * 0.6 + 2);
        return new SplitEstimate(baseComplexity, basePriority);
    }

    private record SplitEstimate(int complexity, double priority) {}

    private void attachOverlay(ScatterChart<Number, Number> scatter,
                               Pane overlay,
                               NumberAxis xAxis,
                               NumberAxis yAxis,
                               XYChart.Series<Number, Number> series,
                               java.util.Map<XYChart.Data<Number, Number>, Task> dataTaskMap,
                               ComboBox<Task> taskCombo) {
        javafx.application.Platform.runLater(() -> {
            Node plotArea = scatter.lookup(".chart-plot-background");
            if (plotArea == null || !(plotArea.getParent() instanceof Pane parent)) return;

            if (!parent.getChildren().contains(overlay)) {
                parent.getChildren().add(overlay);
                overlay.toFront();
                overlay.layoutXProperty().bind(plotArea.layoutXProperty());
                overlay.layoutYProperty().bind(plotArea.layoutYProperty());
                overlay.prefWidthProperty().bind(((Region) plotArea).widthProperty());
                overlay.prefHeightProperty().bind(((Region) plotArea).heightProperty());
            }

            Runnable rerender = () -> renderOverlay(overlay, xAxis, yAxis, series, dataTaskMap, taskCombo);
            plotArea.layoutBoundsProperty().addListener((obs, ov, nv) -> rerender.run());
            xAxis.lowerBoundProperty().addListener((obs, ov, nv) -> rerender.run());
            xAxis.upperBoundProperty().addListener((obs, ov, nv) -> rerender.run());
            yAxis.lowerBoundProperty().addListener((obs, ov, nv) -> rerender.run());
            yAxis.upperBoundProperty().addListener((obs, ov, nv) -> rerender.run());
            rerender.run();
        });
    }

    private void renderOverlay(Pane overlay,
                               NumberAxis xAxis,
                               NumberAxis yAxis,
                               XYChart.Series<Number, Number> series,
                               java.util.Map<XYChart.Data<Number, Number>, Task> dataTaskMap,
                               ComboBox<Task> taskCombo) {
        overlay.getChildren().clear();
        for (XYChart.Data<Number, Number> data : series.getData()) {
            Task task = dataTaskMap.get(data);
            if (task == null) continue;

            double xPos = xAxis.getDisplayPosition(data.getXValue().doubleValue());
            double yPos = yAxis.getDisplayPosition(data.getYValue().doubleValue());

            Shape dot = createOverlayDot(task, data.getYValue().doubleValue(), taskCombo);
            dot.setLayoutX(xPos);
            dot.setLayoutY(yPos);
            overlay.getChildren().add(dot);
        }
    }

    private Shape createOverlayDot(Task task, double priority, ComboBox<Task> taskCombo) {
        String color = priority >= 7 ? (isDark ? "#f38ba8" : "#d20f39") :
                       priority >= 4 ? (isDark ? "#f9e2af" : "#df8e1d") :
                       (isDark ? "#a6e3a1" : "#40a02b");

        Circle circle = new Circle(7);
        circle.setFill(Color.web(color));
        circle.setStroke(Color.web(isDark ? "#313244" : "#ffffff"));
        circle.setStrokeWidth(2);
        circle.setCursor(javafx.scene.Cursor.HAND);

        Tooltip tooltip = new Tooltip(task.getTitle() + "\nСложность: " + task.getComplexity() +
            "\nПриоритет: " + String.format("%.1f/10", priority));
        tooltip.setStyle("-fx-font-size: 12px;");
        Tooltip.install(circle, tooltip);

        circle.setOnMouseClicked(e -> taskCombo.setValue(task));
        return circle;
    }

    private Node createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 20, 25));
        header.getStyleClass().add("ai-header");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("ai-header-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignB.BRAIN, 22);
        icon.getStyleClass().add("ai-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("ИИ-Центр Анализа");
        title.getStyleClass().add("ai-header-title");
        Label subtitle = new Label("Комплексная оценка проекта и продуктивности");
        subtitle.getStyleClass().add("ai-header-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        return header;
    }

    private Node createOverviewContent() {
        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        InlineLayoutSupport.makeShrinkable(scroll);
        
        VBox content = new VBox(25);
        content.setPadding(new Insets(25));
        content.setAlignment(Pos.TOP_LEFT);

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("ai-overview-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignV.VIEW_DASHBOARD, 22);
        icon.getStyleClass().add("ai-overview-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Обзор Проекта");
        title.getStyleClass().add("ai-overview-title");
        Label subtitle = new Label("Ключевые метрики и инсайты");
        subtitle.getStyleClass().add("ai-overview-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Metrics Grid ---
        FlowPane metricsGrid = new FlowPane();
        metricsGrid.setHgap(15);
        metricsGrid.setVgap(15);
        metricsGrid.setAlignment(Pos.TOP_LEFT);
        
        // Calculate real metrics
        long totalTasks = tasks.stream().filter(t -> !t.isArchived()).count();
        long completedTasks = tasks.stream().filter(Task::isArchived).count();
        int productivity = totalTasks > 0 ? (int) ((completedTasks * 100) / (totalTasks + completedTasks)) : 0;
        long highPriority = tasks.stream().filter(t -> !t.isArchived() && t.getSmartPriority() >= 7).count();
        long overdue = tasks.stream().filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now())).count();
        
        String riskLevel = overdue > 3 ? "Высокий" : overdue > 0 ? "Средний" : "Низкий";
        String riskStyle = overdue > 3 ? "metric-high" : overdue > 0 ? "metric-medium" : "metric-low";
        
        metricsGrid.getChildren().addAll(
            createOverviewMetricCard("Продуктивность", productivity + "%", MaterialDesignC.CHART_ARC, "metric-productivity"),
            createOverviewMetricCard("Активных задач", String.valueOf(totalTasks), MaterialDesignC.CLIPBOARD_LIST, "metric-tasks"),
            createOverviewMetricCard("Срочных", String.valueOf(highPriority), MaterialDesignA.ALERT, "metric-urgent"),
            createOverviewMetricCard("Уровень риска", riskLevel, MaterialDesignS.SHIELD_CHECK, riskStyle)
        );
        content.getChildren().add(metricsGrid);

        // --- Risk Indicator ---
        HBox riskSection = new HBox(15);
        riskSection.setAlignment(Pos.CENTER_LEFT);
        riskSection.getStyleClass().add("ai-overview-risk-section");
        riskSection.setPadding(new Insets(16));
        
        Label riskTitle = new Label("Статус рисков:");
        riskTitle.getStyleClass().add("ai-overview-risk-title");
        
        String greenColor = isDark ? "#a6e3a1" : "#40a02b";
        String yellowColor = isDark ? "#f9e2af" : "#df8e1d";
        String redColor = isDark ? "#f38ba8" : "#d20f39";
        
        HBox lights = new HBox(12);
        lights.setAlignment(Pos.CENTER_LEFT);
        
        Circle redLight = new Circle(10);
        redLight.setFill(Color.web(redColor, overdue > 3 ? 1.0 : 0.2));
        
        Circle yellowLight = new Circle(10);
        yellowLight.setFill(Color.web(yellowColor, overdue > 0 && overdue <= 3 ? 1.0 : 0.2));
        
        Circle greenLight = new Circle(10);
        greenLight.setFill(Color.web(greenColor, overdue == 0 ? 1.0 : 0.2));
        
        lights.getChildren().addAll(redLight, yellowLight, greenLight);
        
        Label riskStatus = new Label(overdue == 0 ? "Всё под контролем" : overdue + " просроченных задач");
        riskStatus.getStyleClass().add("ai-overview-risk-status");
        
        riskSection.getChildren().addAll(riskTitle, lights, riskStatus);
        content.getChildren().add(riskSection);

        // --- Insights Section ---
        VBox insightsSection = new VBox(12);
        insightsSection.getStyleClass().add("ai-overview-insights-box");
        insightsSection.setPadding(new Insets(18));
        
        HBox insightsHeader = new HBox(10);
        insightsHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon insightIcon = FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 18);
        insightIcon.getStyleClass().add("ai-overview-insight-icon");
        Label insightsTitle = new Label("Ключевые Инсайты");
        insightsTitle.getStyleClass().add("ai-overview-insights-title");
        insightsHeader.getChildren().addAll(insightIcon, insightsTitle);
        
        VBox insightsList = new VBox(10);
        insightsList.getChildren().addAll(
            createOverviewInsightRow("Высокая нагрузка", "На этой неделе запланировано больше задач", MaterialDesignS.SPEEDOMETER, "insight-warning"),
            createOverviewInsightRow("Фокус внимания", "Вы продуктивны в утренние часы (09:00 - 11:00)", MaterialDesignW.WHITE_BALANCE_SUNNY, "insight-info"),
            createOverviewInsightRow("Приоритеты", highPriority + " задач требуют срочного внимания", MaterialDesignA.ALERT_CIRCLE_OUTLINE, "insight-alert")
        );
        
        insightsSection.getChildren().addAll(insightsHeader, insightsList);
        content.getChildren().add(insightsSection);
        
        scroll.setContent(content);
        return scroll;
    }
    
    private VBox createOverviewMetricCard(String label, String value, org.kordamp.ikonli.Ikon iconCode, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14));
        card.setPrefWidth(170);
        card.setMinWidth(160);
        card.getStyleClass().addAll("ai-overview-metric-card", styleClass);
        
        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("ai-overview-metric-icon");
        Label valLbl = new Label(value);
        valLbl.getStyleClass().add("ai-overview-metric-value");
        top.getChildren().addAll(icon, valLbl);
        
        Label labelLbl = new Label(label);
        labelLbl.getStyleClass().add("ai-overview-metric-label");
        
        card.getChildren().addAll(top, labelLbl);
        return card;
    }
    
    private HBox createOverviewInsightRow(String title, String desc, org.kordamp.ikonli.Ikon iconCode, String styleClass) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.getStyleClass().addAll("ai-overview-insight-row", styleClass);
        
        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("ai-overview-insight-row-icon");
        
        VBox textBox = new VBox(2);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("ai-overview-insight-row-title");
        Label descLbl = new Label(desc);
        descLbl.getStyleClass().add("ai-overview-insight-row-desc");
        textBox.getChildren().addAll(titleLbl, descLbl);
        
        row.getChildren().addAll(icon, textBox);
        return row;
    }

}
