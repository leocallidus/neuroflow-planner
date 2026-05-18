package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesExportService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityContentFormatter;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRecommendation;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityResult;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRisk;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRiskSeverity;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityService;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySnapshot;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySummary;
import com.example.neuroflowplanner.service.planningquality.RescheduleRateMetric;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityBand;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityMetric;
import com.example.neuroflowplanner.service.planningquality.TimeEstimateAccuracyMetric;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignG;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PlanningQualityDashboardDialog implements InlineView {

    private static final PlanningQualityService DEFAULT_SERVICE = new PlanningQualityService();
    private static final SmartNotesExportService EXPORT_SERVICE = new DefaultSmartNotesExportService();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final boolean isDark = ConfigManager.isDarkTheme();
    private final VBox root;
    private final VBox content;
    private final VBox loadingState;
    private final VBox errorState;
    private final VBox emptyState;
    private final Label updatedLabel;
    private final Label statusLabel;
    private final Button openInChatButton;
    private final Button exportButton;
    private final ContextMenu exportContextMenu;
    private final Button refreshButton;
    private final PlanningQualityService service;
    private final Consumer<PlanningQualityResult> openInChatAction;
    private final AtomicInteger loadSequence = new AtomicInteger();
    private PlanningQualityResult currentResult;

    private PlanningQualityDashboardDialog(
            PlanningQualityService service,
            Consumer<PlanningQualityResult> openInChatAction) {
        this.service = service == null ? DEFAULT_SERVICE : service;
        this.openInChatAction = openInChatAction;

        root = new VBox(0);
        root.getStyleClass().add("planning-quality-root");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.getStyleClass().add("planning-quality-header");

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("planning-quality-header-icon-wrap");
        FontIcon headerIcon = FontIcon.of(MaterialDesignG.GAUGE, 22);
        headerIcon.getStyleClass().add("planning-quality-header-icon");
        iconWrap.getChildren().add(headerIcon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Дашборд качества планирования");
        title.getStyleClass().add("planning-quality-title");
        Label subtitle = new Label("Последние 14 дней");
        subtitle.getStyleClass().add("planning-quality-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox metaBox = new VBox(3);
        metaBox.setAlignment(Pos.CENTER_RIGHT);
        updatedLabel = new Label("Требует расчёта");
        updatedLabel.getStyleClass().add("planning-quality-updated");
        statusLabel = new Label("Подготовка quality dashboard");
        statusLabel.getStyleClass().add("planning-quality-status");
        metaBox.getChildren().addAll(updatedLabel, statusLabel);

        openInChatButton = new Button("Открыть в чате");
        openInChatButton.getStyleClass().add("planning-quality-refresh-btn");
        openInChatButton.setGraphic(new FontIcon(MaterialDesignC.CHAT));
        openInChatButton.setTooltip(new Tooltip("Открыть dashboard как стартовый контекст в ИИ-Ассистенте"));
        openInChatButton.setOnAction(event -> handleOpenInChat());

        exportButton = createExportButton();
        exportContextMenu = createExportContextMenu();

        refreshButton = new Button("Пересчитать");
        refreshButton.getStyleClass().add("planning-quality-refresh-btn");
        refreshButton.setGraphic(new FontIcon(MaterialDesignR.REFRESH));
        refreshButton.setTooltip(new Tooltip("Пересчитать метрики качества планирования"));
        refreshButton.setOnAction(event -> loadDashboard(true));

        HBox actionsRow = new HBox(8, openInChatButton, exportButton, refreshButton);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        VBox actionsBox = new VBox(8, metaBox, actionsRow);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.getStyleClass().add("planning-quality-actions-box");

        header.getChildren().addAll(iconWrap, titleBox, spacer, actionsBox);

        content = new VBox(16);
        content.setPadding(new Insets(0, 22, 22, 22));
        content.getStyleClass().add("planning-quality-content");
        InlineLayoutSupport.makeShrinkable(content);

        loadingState = createLoadingState();
        errorState = createErrorState();
        emptyState = createEmptyState();

        StackPane stateHost = new StackPane(content, loadingState, errorState, emptyState);
        stateHost.getStyleClass().add("planning-quality-state-host");
        InlineLayoutSupport.makeShrinkable(stateHost);

        ScrollPane scrollPane = InlineLayoutSupport.createContentScroll(stateHost, "planning-quality-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(header, scrollPane);
        root.setMinSize(420, 360);
        InlineLayoutSupport.makeShrinkable(root, scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        updateActionAvailability();
        showLoadingState("Собираем planning quality dashboard…");
        loadDashboard(false);
    }

    public static InlineView inline() {
        return new PlanningQualityDashboardDialog(DEFAULT_SERVICE, null);
    }

    public static InlineView inline(Consumer<PlanningQualityResult> openInChatAction) {
        return new PlanningQualityDashboardDialog(DEFAULT_SERVICE, openInChatAction);
    }

    static PlanningQualityDashboardDialog testingInstance(PlanningQualityService service) {
        return new PlanningQualityDashboardDialog(service, null);
    }

    static PlanningQualityDashboardDialog testingInstance(
            PlanningQualityService service,
            Consumer<PlanningQualityResult> openInChatAction) {
        return new PlanningQualityDashboardDialog(service, openInChatAction);
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public String getTitle() {
        return "Качество планирования";
    }

    private void loadDashboard(boolean forceRefresh) {
        int requestId = loadSequence.incrementAndGet();
        currentResult = null;
        refreshButton.setDisable(true);
        updateActionAvailability();
        showLoadingState(forceRefresh ? "Пересчитываем dashboard…" : "Собираем planning quality dashboard…");
        CompletableFuture<PlanningQualityResult> future = forceRefresh
                ? service.refreshRecentDashboard()
                : service.getRecentDashboard();
        future
                .whenComplete((result, throwable) -> Platform.runLater(() -> {
                    if (requestId != loadSequence.get()) {
                        return;
                    }
                    refreshButton.setDisable(false);
                    if (throwable != null) {
                        showErrorState(throwable.getMessage());
                        return;
                    }
                    renderResult(result);
                }));
    }

    private void renderResult(PlanningQualityResult result) {
        PlanningQualityResult safeResult = result == null
                ? new PlanningQualityResult(null, Instant.now(), "", false, false)
                : result;
        currentResult = safeResult;
        updateActionAvailability();
        PlanningQualitySnapshot snapshot = safeResult.snapshot();

        updatedLabel.setText(formatUpdatedLabel(safeResult.generatedAt(), safeResult.fromCache()));
        statusLabel.setText(safeResult.aiUsed() ? "AI-сводка актуальна" : "Показан локальный дашборд качества");

        if (isEffectivelyEmpty(snapshot)) {
            showOnly(emptyState);
            return;
        }

        content.getChildren().setAll(
                createSummaryCard(safeResult),
                createMetricsRow(snapshot),
                createSection("Проблемные паттерны", MaterialDesignA.ALERT_CIRCLE_OUTLINE, buildRiskNodes(snapshot.risks())),
                createSection("Что улучшить", MaterialDesignS.STAR_CIRCLE_OUTLINE, buildRecommendationNodes(snapshot.recommendations()))
        );
        showOnly(content);
    }

    private VBox createSummaryCard(PlanningQualityResult result) {
        PlanningQualitySnapshot snapshot = result.snapshot();
        PlanningQualitySummary summary = snapshot.summary();

        VBox card = new VBox(12);
        card.getStyleClass().add("planning-quality-summary-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(summary.headline().isBlank() ? "Картина качества планирования" : summary.headline());
        title.getStyleClass().add("planning-quality-summary-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label sourceChip = new Label(result.aiUsed() ? "AI" : "Базовый");
        sourceChip.getStyleClass().addAll("planning-quality-chip", result.aiUsed() ? "planning-quality-chip-ai" : "planning-quality-chip-fallback");
        header.getChildren().addAll(title, spacer, sourceChip);

        Label summaryLabel = new Label(summary.summary().isBlank()
                ? "Дашборд качества собран, но выраженного доминирующего паттерна пока нет."
                : summary.summary());
        summaryLabel.getStyleClass().add("planning-quality-body-text");
        summaryLabel.setWrapText(true);

        HBox factsRow = new HBox(10);
        factsRow.getStyleClass().add("planning-quality-facts-row");
        factsRow.getChildren().addAll(
                createFactCard("Активных", String.valueOf(snapshot.activeTaskCount()), "planning-quality-fact-active"),
                createFactCard("Завершено", String.valueOf(snapshot.completedTaskCount()), "planning-quality-fact-completed"),
                createFactCard("Трекинг", String.valueOf(snapshot.trackedTaskCount()), "planning-quality-fact-tracked"),
                createFactCard("Сессий", String.valueOf(snapshot.trackedSessionCount()), "planning-quality-fact-sessions")
        );

        card.getChildren().addAll(header, summaryLabel, factsRow);
        if (!summary.nextAction().isBlank()) {
            Label nextAction = new Label(summary.nextAction());
            nextAction.getStyleClass().add("planning-quality-next-action");
            nextAction.setWrapText(true);
            card.getChildren().add(nextAction);
        }
        if (!summary.limitations().isBlank()) {
            Label limitations = new Label(summary.limitations());
            limitations.getStyleClass().add("planning-quality-muted");
            limitations.setWrapText(true);
            card.getChildren().add(limitations);
        }
        return card;
    }

    private HBox createMetricsRow(PlanningQualitySnapshot snapshot) {
        HBox row = new HBox(12);
        row.getStyleClass().add("planning-quality-metrics-row");
        VBox accuracyCard = createAccuracyCard(snapshot.accuracyMetric());
        VBox rescheduleCard = createRescheduleCard(snapshot.rescheduleMetric());
        VBox rhythmCard = createRhythmCard(snapshot.rhythmMetric());
        HBox.setHgrow(accuracyCard, Priority.ALWAYS);
        HBox.setHgrow(rescheduleCard, Priority.ALWAYS);
        HBox.setHgrow(rhythmCard, Priority.ALWAYS);
        row.getChildren().addAll(accuracyCard, rescheduleCard, rhythmCard);
        return row;
    }

    private VBox createAccuracyCard(TimeEstimateAccuracyMetric metric) {
        VBox card = createMetricCardBase("Точность оценки времени", MaterialDesignT.TIMELINE_CLOCK, "planning-quality-metric-accuracy");
        if (metric == null || !metric.available()) {
            card.getChildren().add(createMetricEmptyLabel("Недостаточно данных для расчёта accuracy."));
            return card;
        }

        card.getChildren().addAll(
                createMetricPrimaryValue(formatPercent(metric.hitRate()), "Попадание в диапазон"),
                createMetricDetail("Средняя ошибка", formatPercent(metric.averageErrorRatio())),
                createMetricDetail("Недооценка", formatPercent(metric.underestimationBias())),
                createMetricDetail("Переоценка", formatPercent(metric.overestimationBias())),
                createMetricDetail("Охват", metric.comparableTaskCount() + " из " + metric.estimatedTaskCount() + " задач"),
                createMetricApproximation(metric.approximate())
        );
        return card;
    }

    private VBox createRescheduleCard(RescheduleRateMetric metric) {
        VBox card = createMetricCardBase("Доля переносов", MaterialDesignC.CALENDAR_SYNC_OUTLINE, "planning-quality-metric-reschedule");
        if (metric == null || !metric.available()) {
            card.getChildren().add(createMetricEmptyLabel("Недостаточно данных для расчёта переносов."));
            return card;
        }

        card.getChildren().addAll(
                createMetricPrimaryValue(formatPercent(metric.rescheduleRate()), "Задач с переносами"),
                createMetricDetail("Без переносов", String.valueOf(metric.untouchedTaskCount())),
                createMetricDetail("Множественные", String.valueOf(metric.multipleRescheduleCount())),
                createMetricDetail("Поздние", String.valueOf(metric.lateRescheduleCount())),
                createMetricDetail("Проанализировано", String.valueOf(metric.analyzedTaskCount())),
                createMetricApproximation(metric.approximate())
        );
        return card;
    }

    private VBox createRhythmCard(RhythmStabilityMetric metric) {
        VBox card = createMetricCardBase("Стабильность ритма", MaterialDesignT.TIMELINE_TEXT_OUTLINE, "planning-quality-metric-rhythm");
        if (metric == null || !metric.available()) {
            card.getChildren().add(createMetricEmptyLabel("Недостаточно данных для расчёта ритма."));
            return card;
        }

        card.getChildren().addAll(
                createMetricPrimaryValue(localizeRhythmBand(metric.band()), "Общий уровень"),
                createMetricDetail("Оценка", formatPercent(metric.score())),
                createMetricDetail("Продуктивных дней", metric.productiveDayCount() + " из " + metric.analyzedDayCount()),
                createMetricDetail("Разброс старта", metric.startTimeVariabilityMinutes() + " мин"),
                createMetricDetail("Разброс фокуса", formatPercent(metric.focusMinutesVariability())),
                createMetricApproximation(metric.approximate())
        );
        return card;
    }

    private VBox createMetricCardBase(String titleText, Ikon iconCode, String styleClass) {
        VBox card = new VBox(10);
        card.getStyleClass().addAll("planning-quality-metric-card", styleClass);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("planning-quality-section-icon");
        Label title = new Label(titleText);
        title.getStyleClass().add("planning-quality-metric-title");
        header.getChildren().addAll(icon, title);

        card.getChildren().add(header);
        return card;
    }

    private VBox createMetricPrimaryValue(String value, String label) {
        VBox box = new VBox(2);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("planning-quality-metric-value");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("planning-quality-metric-label");
        box.getChildren().addAll(valueLabel, labelNode);
        return box;
    }

    private HBox createMetricDetail(String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("planning-quality-metric-detail-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("planning-quality-metric-detail-value");
        row.getChildren().addAll(labelNode, spacer, valueNode);
        return row;
    }

    private Label createMetricApproximation(boolean approximate) {
        Label label = new Label(approximate ? "Approximate" : "Надёжный сигнал");
        label.getStyleClass().addAll("planning-quality-chip", approximate ? "planning-quality-chip-fallback" : "planning-quality-chip-ai");
        return label;
    }

    private Label createMetricEmptyLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("planning-quality-muted");
        label.setWrapText(true);
        return label;
    }

    private VBox createSection(String titleText, Ikon iconCode, List<Node> bodyNodes) {
        VBox section = new VBox(12);
        section.getStyleClass().add("planning-quality-section");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("planning-quality-section-icon");
        Label title = new Label(titleText);
        title.getStyleClass().add("planning-quality-section-title");
        header.getChildren().addAll(icon, title);

        VBox body = new VBox(8);
        body.getChildren().addAll(bodyNodes);

        section.getChildren().addAll(header, body);
        return section;
    }

    private List<Node> buildRiskNodes(List<PlanningQualityRisk> risks) {
        if (risks == null || risks.isEmpty()) {
            return List.of(createEmptyRow("Явных проблемных паттернов пока не найдено."));
        }
        return risks.stream()
                .map(risk -> createItemRow(
                        risk.title(),
                        risk.detail(),
                        switch (risk.severity()) {
                            case CRITICAL -> "planning-quality-item-risk-critical";
                            case WARNING -> "planning-quality-item-risk-warning";
                            case INFO -> "planning-quality-item-risk-info";
                        }))
                .map(node -> (Node) node)
                .toList();
    }

    private List<Node> buildRecommendationNodes(List<PlanningQualityRecommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return List.of(createEmptyRow("Конкретных улучшений пока нет."));
        }
        return recommendations.stream()
                .filter(PlanningQualityRecommendation::available)
                .map(recommendation -> createItemRow(
                        recommendation.title(),
                        recommendation.detail() + (recommendation.action().isBlank() ? "" : "\n" + recommendation.action()),
                        "planning-quality-item-recommendation"))
                .map(node -> (Node) node)
                .toList();
    }

    private HBox createItemRow(String title, String meta, String styleClass) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().addAll("planning-quality-item", styleClass);

        VBox textBox = new VBox(4);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("planning-quality-item-title");
        titleLabel.setWrapText(true);
        Label metaLabel = new Label(meta == null ? "" : meta);
        metaLabel.getStyleClass().add("planning-quality-item-meta");
        metaLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, metaLabel);
        row.getChildren().add(textBox);
        Tooltip.install(row, new Tooltip((title == null ? "" : title) + ((meta == null || meta.isBlank()) ? "" : "\n" + meta)));
        return row;
    }

    private HBox createEmptyRow(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(text);
        label.getStyleClass().add("planning-quality-muted");
        label.setWrapText(true);
        row.getChildren().add(label);
        return row;
    }

    private VBox createFactCard(String label, String value, String styleClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("planning-quality-fact-card", styleClass);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("planning-quality-fact-value");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("planning-quality-fact-label");
        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private VBox createLoadingState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("planning-quality-placeholder");
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(42, 42);
        Label label = new Label("Собираем planning quality dashboard…");
        label.getStyleClass().add("planning-quality-placeholder-title");
        box.getChildren().addAll(indicator, label);
        return box;
    }

    private VBox createErrorState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("planning-quality-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE_OUTLINE, 28);
        icon.getStyleClass().add("planning-quality-error-icon");
        Label title = new Label("Не удалось собрать dashboard");
        title.getStyleClass().add("planning-quality-placeholder-title");
        Label body = new Label("Попробуйте пересчитать метрики ещё раз.");
        body.getStyleClass().add("planning-quality-muted");
        body.setWrapText(true);
        body.setMaxWidth(320);
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private VBox createEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("planning-quality-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE_OUTLINE, 28);
        icon.getStyleClass().add("planning-quality-empty-icon");
        Label title = new Label("Пока мало данных для dashboard");
        title.getStyleClass().add("planning-quality-placeholder-title");
        Label body = new Label("Для quality-метрик нужны задачи с плановыми окнами и хотя бы базовая история трекинга.");
        body.getStyleClass().add("planning-quality-muted");
        body.setWrapText(true);
        body.setMaxWidth(340);
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private void showLoadingState(String message) {
        ((Label) loadingState.getChildren().get(1)).setText(message);
        updatedLabel.setText("Обновление…");
        statusLabel.setText(message);
        showOnly(loadingState);
    }

    private void showErrorState(String details) {
        Label body = (Label) errorState.getChildren().get(2);
        body.setText(details == null || details.isBlank()
                ? "Попробуйте пересчитать метрики ещё раз."
                : details.trim());
        updatedLabel.setText("Ошибка обновления");
        statusLabel.setText("Dashboard недоступен");
        currentResult = null;
        updateActionAvailability();
        showOnly(errorState);
    }

    private void showOnly(Node visibleNode) {
        content.setManaged(visibleNode == content);
        content.setVisible(visibleNode == content);
        loadingState.setManaged(visibleNode == loadingState);
        loadingState.setVisible(visibleNode == loadingState);
        errorState.setManaged(visibleNode == errorState);
        errorState.setVisible(visibleNode == errorState);
        emptyState.setManaged(visibleNode == emptyState);
        emptyState.setVisible(visibleNode == emptyState);
    }

    private boolean isEffectivelyEmpty(PlanningQualitySnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        return !snapshot.hasDeterministicMetrics()
                && !snapshot.hasRisks()
                && !snapshot.hasRecommendations();
    }

    private String formatUpdatedLabel(Instant generatedAt, boolean fromCache) {
        Instant safeInstant = generatedAt == null ? Instant.now() : generatedAt;
        String label = "Обновлено " + TIME_FORMAT.format(safeInstant.atZone(ZoneId.systemDefault()).toLocalTime());
        return fromCache ? label + " • cache" : label;
    }

    private String formatPercent(double value) {
        return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) + "%";
    }

    private Button createExportButton() {
        Button button = new Button("Экспорт");
        button.getStyleClass().add("planning-quality-refresh-btn");
        button.setGraphic(new FontIcon(MaterialDesignF.FILE_EXPORT_OUTLINE));
        button.setTooltip(new Tooltip("Экспортировать dashboard в Markdown или PDF"));
        MenuItem markdownItem = new MenuItem("Markdown (.md)");
        markdownItem.setOnAction(event -> exportCurrentDashboard(".md"));
        MenuItem pdfItem = new MenuItem("PDF (.pdf)");
        pdfItem.setOnAction(event -> exportCurrentDashboard(".pdf"));
        button.setOnAction(event -> {
            if (button.isDisabled()) {
                return;
            }
            if (exportContextMenu.isShowing()) {
                exportContextMenu.hide();
            } else {
                exportContextMenu.show(button, Side.BOTTOM, 0, 6);
            }
        });
        button.getProperties().put("planningQualityMarkdownExportItem", markdownItem);
        button.getProperties().put("planningQualityPdfExportItem", pdfItem);
        return button;
    }

    private ContextMenu createExportContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem markdownItem = (MenuItem) exportButton.getProperties().get("planningQualityMarkdownExportItem");
        MenuItem pdfItem = (MenuItem) exportButton.getProperties().get("planningQualityPdfExportItem");
        menu.getItems().setAll(markdownItem, pdfItem);
        return menu;
    }

    private void handleOpenInChat() {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Качество планирования", "Сначала дождитесь расчёта dashboard.");
            return;
        }
        if (openInChatAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Качество планирования", "Открытие dashboard в чате сейчас недоступно.");
            return;
        }
        openInChatAction.accept(currentResult);
    }

    private void exportCurrentDashboard(String extension) {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Качество планирования", "Сначала дождитесь расчёта dashboard.");
            return;
        }
        File file = chooseExportFile(extension);
        if (file == null) {
            return;
        }
        String title = PlanningQualityContentFormatter.buildExportTitle(currentResult);
        String markdown = PlanningQualityContentFormatter.toMarkdown(currentResult);
        try {
            if (".pdf".equals(extension)) {
                EXPORT_SERVICE.exportNoteToPdf(file, title, markdown);
            } else {
                EXPORT_SERVICE.exportNoteToMarkdown(file, title, markdown);
            }
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Экспорт завершён", "Dashboard сохранён: " + file.getName());
        } catch (Exception ex) {
            UiErrorNotifier.showMappedError(
                    ownerWindow(),
                    isDark,
                    "Ошибка экспорта dashboard",
                    ex,
                    ".pdf".equals(extension) ? ErrorCode.EXPORT_PDF_FAILED : ErrorCode.EXPORT_MARKDOWN_FAILED,
                    "Не удалось экспортировать planning quality dashboard.",
                    false,
                    "operation", ".pdf".equals(extension) ? "exportPlanningQualityPdf" : "exportPlanningQualityMarkdown",
                    "periodStart", currentResult.periodStart().toString(),
                    "periodEnd", currentResult.periodEnd().toString()
            );
        }
    }

    private File chooseExportFile(String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(".pdf".equals(extension)
                ? "Экспорт dashboard качества планирования в PDF"
                : "Экспорт dashboard качества планирования в Markdown");
        String baseName = EXPORT_SERVICE.sanitizeFileName(
                "planning-quality-" + currentResult.periodStart() + "-" + currentResult.periodEnd(),
                "planning-quality");
        chooser.setInitialFileName(baseName + extension);
        if (".pdf".equals(extension)) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        } else {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        }
        return chooser.showSaveDialog(ownerWindow());
    }

    private void updateActionAvailability() {
        boolean ready = currentResult != null;
        openInChatButton.setDisable(!ready);
        exportButton.setDisable(!ready);
    }

    private Window ownerWindow() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
    }

    private String localizeRhythmBand(RhythmStabilityBand band) {
        if (band == null) {
            return "Нет данных";
        }
        return switch (band) {
            case STABLE -> "Стабильный";
            case MODERATE -> "Умеренный";
            case CHAOTIC -> "Хаотичный";
            case UNAVAILABLE -> "Нет данных";
        };
    }
}
