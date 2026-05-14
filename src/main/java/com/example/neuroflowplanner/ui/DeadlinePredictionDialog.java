package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.DeadlinePredictionService;
import com.example.neuroflowplanner.util.TaskScheduleFormatter;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeadlinePredictionDialog implements InlineView {

    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final DeadlinePredictionService service = new DeadlinePredictionService();

    private DeadlinePredictionDialog(List<Task> tasks) {
        root = new VBox(0);
        root.setMinSize(0, 0);
        root.getStyleClass().add("deadline-root");

        // Header
        HBox header = createHeader();
        root.getChildren().add(header);

        // Content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("deadline-scroll");
        InlineLayoutSupport.makeShrinkable(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 25, 25, 25));
        content.getStyleClass().add("deadline-content");
        InlineLayoutSupport.makeShrinkable(content);

        // Analyze risks
        List<DeadlinePredictionService.TaskRiskAnalysis> risks = service.analyzeRisks(tasks);
        Map<DeadlinePredictionService.RiskLevel, Long> counts = risks.stream()
            .collect(Collectors.groupingBy(DeadlinePredictionService.TaskRiskAnalysis::level, Collectors.counting()));

        // Summary cards
        HBox summaryCards = createSummaryCards(risks, counts);
        content.getChildren().add(summaryCards);

        // Risk distribution
        VBox riskDistribution = createRiskDistribution(counts, risks.size());
        content.getChildren().add(riskDistribution);

        // Table
        VBox tableSection = createTableSection(risks);
        content.getChildren().add(tableSection);

        scrollPane.setContent(content);
        root.getChildren().add(scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 15, 25));
        header.getStyleClass().add("deadline-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("deadline-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_OCTAGON_OUTLINE, 24);
        icon.getStyleClass().add("deadline-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Прогноз дедлайнов");
        title.getStyleClass().add("deadline-title");
        Label subtitle = new Label("Анализ рисков срыва сроков выполнения задач");
        subtitle.getStyleClass().add("deadline-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        return header;
    }

    private HBox createSummaryCards(List<DeadlinePredictionService.TaskRiskAnalysis> risks, 
                                     Map<DeadlinePredictionService.RiskLevel, Long> counts) {
        HBox cards = new HBox(15);
        cards.setAlignment(Pos.CENTER);

        long critical = counts.getOrDefault(DeadlinePredictionService.RiskLevel.CRITICAL, 0L);
        long high = counts.getOrDefault(DeadlinePredictionService.RiskLevel.HIGH, 0L);
        long medium = counts.getOrDefault(DeadlinePredictionService.RiskLevel.MEDIUM, 0L);
        long low = counts.getOrDefault(DeadlinePredictionService.RiskLevel.LOW, 0L);

        cards.getChildren().addAll(
            createMetricCard("Критический", String.valueOf(critical), MaterialDesignA.ALERT_CIRCLE, "deadline-card-critical"),
            createMetricCard("Высокий", String.valueOf(high), MaterialDesignA.ALERT, "deadline-card-high"),
            createMetricCard("Средний", String.valueOf(medium), MaterialDesignC.CLOCK_ALERT_OUTLINE, "deadline-card-medium"),
            createMetricCard("Низкий", String.valueOf(low), MaterialDesignC.CHECK_CIRCLE_OUTLINE, "deadline-card-low")
        );

        return cards;
    }

    private VBox createMetricCard(String label, String value, Ikon iconType, String styleClass) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));
        card.setPrefWidth(180);
        card.getStyleClass().addAll("deadline-metric-card", styleClass);

        FontIcon icon = FontIcon.of(iconType, 28);
        icon.getStyleClass().add("deadline-card-icon");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("deadline-card-value");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("deadline-card-label");

        card.getChildren().addAll(icon, valueLabel, nameLabel);
        return card;
    }

    private VBox createRiskDistribution(Map<DeadlinePredictionService.RiskLevel, Long> counts, int total) {
        VBox section = new VBox(12);
        section.getStyleClass().add("deadline-section");
        section.setPadding(new Insets(15));

        Label sectionTitle = new Label("Распределение рисков");
        sectionTitle.getStyleClass().add("deadline-section-title");

        VBox bars = new VBox(10);
        bars.setPadding(new Insets(10, 0, 0, 0));

        for (DeadlinePredictionService.RiskLevel level : DeadlinePredictionService.RiskLevel.values()) {
            long count = counts.getOrDefault(level, 0L);
            double percent = total > 0 ? (count * 100.0 / total) : 0;
            bars.getChildren().add(createRiskBar(level, count, percent));
        }

        section.getChildren().addAll(sectionTitle, bars);
        return section;
    }

    private HBox createRiskBar(DeadlinePredictionService.RiskLevel level, long count, double percent) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(level.label);
        nameLabel.setMinWidth(100);
        nameLabel.getStyleClass().add("deadline-bar-label");

        StackPane barContainer = new StackPane();
        barContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(barContainer, Priority.ALWAYS);

        Region track = new Region();
        track.getStyleClass().add("deadline-bar-track");
        track.setMaxWidth(Double.MAX_VALUE);

        Region fill = new Region();
        fill.getStyleClass().addAll("deadline-bar-fill", "deadline-bar-" + level.name().toLowerCase());
        fill.setMaxWidth(percent > 0 ? percent * 4 : 0); // Scale for visibility
        fill.setMinHeight(8);

        barContainer.getChildren().addAll(track, fill);

        Label countLabel = new Label(count + " (" + String.format("%.0f%%", percent) + ")");
        countLabel.setMinWidth(80);
        countLabel.getStyleClass().add("deadline-bar-count");

        row.getChildren().addAll(nameLabel, barContainer, countLabel);
        return row;
    }

    private VBox createTableSection(List<DeadlinePredictionService.TaskRiskAnalysis> risks) {
        VBox section = new VBox(12);
        section.getStyleClass().add("deadline-section");
        section.setPadding(new Insets(15));
        VBox.setVgrow(section, Priority.ALWAYS);

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label sectionTitle = new Label("Детальный анализ задач");
        sectionTitle.getStyleClass().add("deadline-section-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        long highRiskCount = risks.stream().filter(r -> r.level().severity >= 3).count();
        Label alertLabel = new Label();
        if (highRiskCount > 0) {
            alertLabel.setText("⚠ " + highRiskCount + " задач в зоне риска");
            alertLabel.getStyleClass().add("deadline-alert-danger");
        } else {
            alertLabel.setText("✓ Все задачи в безопасности");
            alertLabel.getStyleClass().add("deadline-alert-success");
        }

        headerRow.getChildren().addAll(sectionTitle, spacer, alertLabel);

        TableView<DeadlinePredictionService.TaskRiskAnalysis> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(350);
        table.getStyleClass().add("deadline-table");
        table.setMinHeight(0);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<DeadlinePredictionService.TaskRiskAnalysis, String> taskCol = new TableColumn<>("Задача");
        taskCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().task().getTitle()));
        taskCol.setPrefWidth(200);

        TableColumn<DeadlinePredictionService.TaskRiskAnalysis, String> dateCol = new TableColumn<>("Дедлайн");
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(TaskScheduleFormatter.formatDeadline(data.getValue().task())));
        dateCol.setPrefWidth(100);

        TableColumn<DeadlinePredictionService.TaskRiskAnalysis, String> riskCol = new TableColumn<>("Уровень риска");
        riskCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().level().label));
        riskCol.setPrefWidth(120);
        riskCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("risk-cell-low", "risk-cell-medium", "risk-cell-high", "risk-cell-critical", "risk-cell-overdue");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    DeadlinePredictionService.TaskRiskAnalysis row = getTableView().getItems().get(getIndex());
                    
                    HBox badge = new HBox(5);
                    badge.setAlignment(Pos.CENTER);
                    badge.getStyleClass().addAll("deadline-risk-badge", "risk-badge-" + row.level().name().toLowerCase());
                    
                    Label text = new Label(item);
                    text.getStyleClass().add("deadline-risk-badge-text");
                    badge.getChildren().add(text);
                    
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        TableColumn<DeadlinePredictionService.TaskRiskAnalysis, String> reasonCol = new TableColumn<>("Причина");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().reason()));
        reasonCol.setPrefWidth(250);

        table.getColumns().addAll(taskCol, dateCol, riskCol, reasonCol);
        table.getItems().addAll(risks);

        section.getChildren().addAll(headerRow, table);
        return section;
    }

    public static InlineView inline(List<Task> tasks) {
        return new DeadlinePredictionDialog(tasks);
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
        return "Прогноз дедлайнов";
    }
}
