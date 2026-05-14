package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockExplanation;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockContentFormatter;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendation;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationResult;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationService;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationSnapshot;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRisk;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesExportService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
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
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class FocusBlockRecommendationDialog implements InlineView {

    private static final FocusBlockRecommendationService DEFAULT_SERVICE = new FocusBlockRecommendationService();
    private static final SmartNotesExportService EXPORT_SERVICE = new DefaultSmartNotesExportService();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
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
    private final Button openDailyReviewButton;
    private final Button openAssistantButton;
    private final Button exportButton;
    private final ContextMenu exportContextMenu;
    private final Button refreshButton;
    private final AtomicInteger loadSequence = new AtomicInteger();
    private final FocusBlockRecommendationService service;
    private final Consumer<FocusBlockRecommendationResult> openInChatAction;
    private final Runnable openDailyReviewAction;
    private final Runnable openAssistantAction;
    private FocusBlockRecommendationResult currentResult;

    private FocusBlockRecommendationDialog(
            FocusBlockRecommendationService service,
            Consumer<FocusBlockRecommendationResult> openInChatAction,
            Runnable openDailyReviewAction,
            Runnable openAssistantAction) {
        this.service = service == null ? DEFAULT_SERVICE : service;
        this.openInChatAction = openInChatAction;
        this.openDailyReviewAction = openDailyReviewAction;
        this.openAssistantAction = openAssistantAction;

        root = new VBox(0);
        root.getStyleClass().add("focus-blocks-root");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.getStyleClass().add("focus-blocks-header");

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("focus-blocks-header-icon-wrap");
        FontIcon headerIcon = FontIcon.of(MaterialDesignT.TIMELINE_TEXT_OUTLINE, 22);
        headerIcon.getStyleClass().add("focus-blocks-header-icon");
        iconWrap.getChildren().add(headerIcon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Рекомендатор фокус-блоков");
        title.getStyleClass().add("focus-blocks-title");
        Label subtitle = new Label(LocalDate.now().format(DATE_FORMAT));
        subtitle.getStyleClass().add("focus-blocks-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox metaBox = new VBox(3);
        metaBox.setAlignment(Pos.CENTER_RIGHT);
        updatedLabel = new Label("Требует расчёта");
        updatedLabel.getStyleClass().add("focus-blocks-updated");
        statusLabel = new Label("Подготовка рекомендаций");
        statusLabel.getStyleClass().add("focus-blocks-status");
        metaBox.getChildren().addAll(updatedLabel, statusLabel);

        openInChatButton = new Button("Открыть в чате");
        openInChatButton.getStyleClass().add("focus-blocks-refresh-btn");
        openInChatButton.setGraphic(new FontIcon(MaterialDesignC.CHAT));
        openInChatButton.setTooltip(new Tooltip("Открыть рекомендации как стартовый контекст в ИИ-Ассистенте"));
        openInChatButton.setOnAction(event -> handleOpenInChat());

        openDailyReviewButton = new Button("Ежедневный обзор");
        openDailyReviewButton.getStyleClass().add("focus-blocks-refresh-btn");
        openDailyReviewButton.setGraphic(new FontIcon(MaterialDesignC.CALENDAR_TODAY));
        openDailyReviewButton.setTooltip(new Tooltip("Открыть соседний сценарий Ежедневного обзора"));
        openDailyReviewButton.setOnAction(event -> handleOpenDailyReview());

        openAssistantButton = new Button("ИИ-Ассистент");
        openAssistantButton.getStyleClass().add("focus-blocks-refresh-btn");
        openAssistantButton.setGraphic(new FontIcon(MaterialDesignC.CHAT));
        openAssistantButton.setTooltip(new Tooltip("Открыть ИИ-Ассистента"));
        openAssistantButton.setOnAction(event -> handleOpenAssistant());

        exportButton = createExportButton();
        exportContextMenu = createExportContextMenu();

        refreshButton = new Button("Обновить");
        refreshButton.getStyleClass().add("focus-blocks-refresh-btn");
        refreshButton.setGraphic(new FontIcon(MaterialDesignR.REFRESH));
        refreshButton.setTooltip(new Tooltip("Пересчитать рекомендации фокус-блоков"));
        refreshButton.setOnAction(event -> loadRecommendations(true));

        HBox actionsRow = new HBox(8, openInChatButton, openDailyReviewButton, openAssistantButton, exportButton, refreshButton);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        VBox actionsBox = new VBox(8, metaBox, actionsRow);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.getStyleClass().add("focus-blocks-actions-box");

        header.getChildren().addAll(iconWrap, titleBox, spacer, actionsBox);

        content = new VBox(16);
        content.setPadding(new Insets(0, 22, 22, 22));
        content.getStyleClass().add("focus-blocks-content");
        InlineLayoutSupport.makeShrinkable(content);

        loadingState = createLoadingState();
        errorState = createErrorState();
        emptyState = createEmptyState();

        StackPane stateHost = new StackPane(content, loadingState, errorState, emptyState);
        stateHost.getStyleClass().add("focus-blocks-state-host");
        InlineLayoutSupport.makeShrinkable(stateHost);

        ScrollPane scrollPane = InlineLayoutSupport.createContentScroll(stateHost, "focus-blocks-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(header, scrollPane);
        root.setMinSize(420, 360);
        InlineLayoutSupport.makeShrinkable(root, scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        showLoadingState("Собираем рекомендации…");
        loadRecommendations(false);
    }

    public static InlineView inline() {
        return new FocusBlockRecommendationDialog(DEFAULT_SERVICE, null, null, null);
    }

    public static InlineView inline(Runnable openDailyReviewAction, Runnable openAssistantAction) {
        return new FocusBlockRecommendationDialog(DEFAULT_SERVICE, null, openDailyReviewAction, openAssistantAction);
    }

    public static InlineView inline(
            Consumer<FocusBlockRecommendationResult> openInChatAction,
            Runnable openDailyReviewAction,
            Runnable openAssistantAction) {
        return new FocusBlockRecommendationDialog(DEFAULT_SERVICE, openInChatAction, openDailyReviewAction, openAssistantAction);
    }

    static FocusBlockRecommendationDialog testingInstance(FocusBlockRecommendationService service) {
        return new FocusBlockRecommendationDialog(service, null, null, null);
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public String getTitle() {
        return "Фокус-блоки";
    }

    private void loadRecommendations(boolean forceRefresh) {
        int requestId = loadSequence.incrementAndGet();
        currentResult = null;
        updateActionAvailability();
        refreshButton.setDisable(true);
        showLoadingState(forceRefresh ? "Обновляем рекомендации…" : "Собираем рекомендации…");
        service.getRecommendations(LocalDate.now(), forceRefresh)
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

    private void renderResult(FocusBlockRecommendationResult result) {
        FocusBlockRecommendationResult safeResult = result == null
                ? new FocusBlockRecommendationResult(null, Instant.now(), "", false, false)
                : result;
        currentResult = safeResult;
        updateActionAvailability();
        FocusBlockRecommendationSnapshot snapshot = safeResult.snapshot();

        updatedLabel.setText(formatUpdatedLabel(safeResult.generatedAt(), safeResult.fromCache()));
        statusLabel.setText(safeResult.aiUsed()
                ? "AI-объяснение актуально"
                : "Показаны локальные рекомендации");

        if (isEffectivelyEmpty(snapshot)) {
            showOnly(emptyState);
            return;
        }

        content.getChildren().setAll(
                createNextBlockCard(snapshot),
                createExplanationCard(safeResult),
                createSection("Лучшие фокус-окна", MaterialDesignB.BRAIN, buildRecommendationNodes(snapshot.focusWindows(), "focus")),
                createSection("Короткие окна", MaterialDesignT.TIMELINE_CLOCK, buildRecommendationNodes(snapshot.shortWindows(), "short")),
                createSection("Риски", MaterialDesignA.ALERT_CIRCLE_OUTLINE, buildRiskNodes(snapshot.risks()))
        );
        showOnly(content);
    }

    private VBox createNextBlockCard(FocusBlockRecommendationSnapshot snapshot) {
        VBox card = new VBox(12);
        card.getStyleClass().add("focus-blocks-next-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 18);
        icon.getStyleClass().add("focus-blocks-section-icon");
        Label title = new Label("Следующий рекомендуемый блок");
        title.getStyleClass().add("focus-blocks-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label confidenceChip = new Label("Уверенность " + formatPercent(snapshot.nextRecommendedBlock().confidence()));
        confidenceChip.getStyleClass().add("focus-blocks-chip");
        header.getChildren().addAll(icon, title, spacer, confidenceChip);

        FocusBlockRecommendation next = snapshot.nextRecommendedBlock();
        Label blockTitle = new Label(next.available()
                ? next.title()
                : "Подходящий фокус-блок пока не найден");
        blockTitle.getStyleClass().add("focus-blocks-next-title");
        blockTitle.setWrapText(true);

        Label meta = new Label(next.available()
                ? next.durationMinutes() + " мин • " + next.type().name().replace('_', ' ').toLowerCase()
                : "Попробуйте накопить больше истории трекинга или пересчитать позже.");
        meta.getStyleClass().add("focus-blocks-muted");
        meta.setWrapText(true);

        HBox factsRow = new HBox(10);
        factsRow.getStyleClass().add("focus-blocks-facts-row");
        factsRow.getChildren().addAll(
                createFactCard("Стабильный фокус", snapshot.productivityProfile().stableFocusMinutes() + " мин", "focus-blocks-fact-strong"),
                createFactCard("Средняя сессия", snapshot.productivityProfile().averageFocusMinutes() + " мин", "focus-blocks-fact-neutral"),
                createFactCard("История", snapshot.limitedHistory() ? "ограничена" : "достаточна", snapshot.limitedHistory() ? "focus-blocks-fact-warn" : "focus-blocks-fact-neutral")
        );

        card.getChildren().addAll(header, blockTitle, meta, factsRow);
        if (!next.rationale().isBlank()) {
            Label rationale = new Label(next.rationale());
            rationale.getStyleClass().add("focus-blocks-body-text");
            rationale.setWrapText(true);
            card.getChildren().add(rationale);
        }
        return card;
    }

    private VBox createExplanationCard(FocusBlockRecommendationResult result) {
        FocusBlockExplanation explanation = result.explanation();

        VBox card = new VBox(10);
        card.getStyleClass().add("focus-blocks-explanation-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(MaterialDesignC.CHAT_PROCESSING_OUTLINE, 18);
        icon.getStyleClass().add("focus-blocks-section-icon");
        Label title = new Label("Почему именно этот блок");
        title.getStyleClass().add("focus-blocks-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label sourceChip = new Label(result.aiUsed() ? "AI" : "Fallback");
        sourceChip.getStyleClass().addAll("focus-blocks-chip", result.aiUsed() ? "focus-blocks-chip-ai" : "focus-blocks-chip-fallback");
        header.getChildren().addAll(icon, title, spacer, sourceChip);

        Label headline = new Label(explanation.headline().isBlank()
                ? "Объяснение ещё не готово"
                : explanation.headline());
        headline.getStyleClass().add("focus-blocks-explanation-title");
        headline.setWrapText(true);

        Label summary = new Label(explanation.summary().isBlank()
                ? "Рекомендация построена по текущему профилю продуктивности и доступным окнам."
                : explanation.summary());
        summary.getStyleClass().add("focus-blocks-body-text");
        summary.setWrapText(true);

        Label nextAction = new Label(explanation.nextAction().isBlank()
                ? "Если окно не подходит, пересчитайте рекомендации позже."
                : explanation.nextAction());
        nextAction.getStyleClass().add("focus-blocks-next-step");
        nextAction.setWrapText(true);

        card.getChildren().addAll(header, headline, summary, new Separator(), nextAction);
        if (!explanation.limitations().isBlank()) {
            Label limitations = new Label(explanation.limitations());
            limitations.getStyleClass().add("focus-blocks-muted");
            limitations.setWrapText(true);
            card.getChildren().add(limitations);
        }
        return card;
    }

    private VBox createSection(String title, Ikon iconCode, List<Node> bodyNodes) {
        VBox section = new VBox(12);
        section.getStyleClass().add("focus-blocks-section");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("focus-blocks-section-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("focus-blocks-section-title");
        header.getChildren().addAll(icon, titleLabel);

        VBox body = new VBox(8);
        body.getChildren().addAll(bodyNodes);
        section.getChildren().addAll(header, body);
        return section;
    }

    private List<Node> buildRecommendationNodes(List<FocusBlockRecommendation> items, String mode) {
        if (items == null || items.isEmpty()) {
            return List.of(createEmptyRow(mode.equals("focus")
                    ? "Сильных длинных окон пока не найдено."
                    : "Коротких рабочих окон сейчас нет."));
        }
        return items.stream()
                .map(item -> {
                    String meta = item.durationMinutes() + " мин • "
                            + item.type().name().replace('_', ' ').toLowerCase()
                            + " • уверенность " + formatPercent(item.confidence());
                    return createInfoItem(
                            item.title(),
                            meta,
                            mode.equals("focus") ? "focus-blocks-item-strong" : "focus-blocks-item-short"
                    );
                })
                .map(Node.class::cast)
                .toList();
    }

    private List<Node> buildRiskNodes(List<FocusBlockRisk> risks) {
        if (risks == null || risks.isEmpty()) {
            return List.of(createEmptyRow("Явных рисков для фокусной работы не найдено."));
        }
        return risks.stream()
                .map(risk -> createInfoItem(
                        risk.title().isBlank() ? "Риск дня" : risk.title(),
                        risk.detail().isBlank() ? risk.level().name() : risk.detail(),
                        switch (risk.level()) {
                            case CRITICAL -> "focus-blocks-item-risk-high";
                            case WARNING -> "focus-blocks-item-risk-medium";
                            case INFO -> "focus-blocks-item-risk-low";
                        }
                ))
                .map(Node.class::cast)
                .toList();
    }

    private HBox createInfoItem(String title, String meta, String styleClass) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("focus-blocks-item", styleClass);

        VBox textBox = new VBox(3);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("focus-blocks-item-title");
        titleLabel.setWrapText(true);
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("focus-blocks-item-meta");
        metaLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, metaLabel);
        row.getChildren().add(textBox);

        Tooltip.install(row, new Tooltip(title + (meta == null || meta.isBlank() ? "" : "\n" + meta)));
        return row;
    }

    private VBox createFactCard(String label, String value, String styleClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("focus-blocks-fact-card", styleClass);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("focus-blocks-fact-value");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("focus-blocks-fact-label");
        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private HBox createEmptyRow(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("focus-blocks-empty-row");
        Label label = new Label(text);
        label.getStyleClass().add("focus-blocks-muted");
        label.setWrapText(true);
        row.getChildren().add(label);
        return row;
    }

    private VBox createLoadingState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("focus-blocks-placeholder");
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(42, 42);
        Label label = new Label("Собираем рекомендации…");
        label.getStyleClass().add("focus-blocks-placeholder-title");
        box.getChildren().addAll(indicator, label);
        return box;
    }

    private VBox createErrorState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("focus-blocks-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE_OUTLINE, 28);
        icon.getStyleClass().add("focus-blocks-error-icon");
        Label title = new Label("Не удалось собрать рекомендации");
        title.getStyleClass().add("focus-blocks-placeholder-title");
        Label body = new Label("Попробуйте обновить рекомендации ещё раз.");
        body.getStyleClass().add("focus-blocks-muted");
        body.setWrapText(true);
        body.setMaxWidth(320);
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private VBox createEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("focus-blocks-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignF.FLARE, 28);
        icon.getStyleClass().add("focus-blocks-empty-icon");
        Label title = new Label("Подходящих фокус-окон пока нет");
        title.getStyleClass().add("focus-blocks-placeholder-title");
        Label body = new Label("Для уверенных рекомендаций нужно больше истории трекинга или свободных слотов в дне.");
        body.getStyleClass().add("focus-blocks-muted");
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
                ? "Попробуйте обновить рекомендации ещё раз."
                : details.trim());
        updatedLabel.setText("Ошибка обновления");
        statusLabel.setText("Рекомендации недоступны");
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

    private boolean isEffectivelyEmpty(FocusBlockRecommendationSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        return !snapshot.nextRecommendedBlock().available()
                && snapshot.focusWindows().isEmpty()
                && snapshot.shortWindows().isEmpty()
                && snapshot.risks().isEmpty();
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
        button.getStyleClass().add("focus-blocks-refresh-btn");
        button.setGraphic(new FontIcon(MaterialDesignF.FILE_EXPORT_OUTLINE));
        button.setTooltip(new Tooltip("Экспортировать рекомендации в Markdown или PDF"));
        MenuItem markdownItem = new MenuItem("Markdown (.md)");
        markdownItem.setOnAction(event -> exportCurrentRecommendations(".md"));
        MenuItem pdfItem = new MenuItem("PDF (.pdf)");
        pdfItem.setOnAction(event -> exportCurrentRecommendations(".pdf"));
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
        button.getProperties().put("focusBlocksMarkdownExportItem", markdownItem);
        button.getProperties().put("focusBlocksPdfExportItem", pdfItem);
        return button;
    }

    private ContextMenu createExportContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem markdownItem = (MenuItem) exportButton.getProperties().get("focusBlocksMarkdownExportItem");
        MenuItem pdfItem = (MenuItem) exportButton.getProperties().get("focusBlocksPdfExportItem");
        menu.getItems().setAll(markdownItem, pdfItem);
        return menu;
    }

    private void handleOpenInChat() {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Фокус-блоки", "Сначала дождитесь расчёта рекомендаций.");
            return;
        }
        if (openInChatAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Фокус-блоки", "Открытие рекомендаций в чате сейчас недоступно.");
            return;
        }
        openInChatAction.accept(currentResult);
    }

    private void handleOpenDailyReview() {
        if (openDailyReviewAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Фокус-блоки", "Открытие ежедневного обзора сейчас недоступно.");
            return;
        }
        openDailyReviewAction.run();
    }

    private void handleOpenAssistant() {
        if (openAssistantAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Фокус-блоки", "Открытие ИИ-Ассистента сейчас недоступно.");
            return;
        }
        openAssistantAction.run();
    }

    private void exportCurrentRecommendations(String extension) {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Фокус-блоки", "Сначала дождитесь расчёта рекомендаций.");
            return;
        }
        File file = chooseExportFile(extension);
        if (file == null) {
            return;
        }
        String title = FocusBlockContentFormatter.buildExportTitle(currentResult);
        String markdown = FocusBlockContentFormatter.toMarkdown(currentResult);
        try {
            if (".pdf".equals(extension)) {
                EXPORT_SERVICE.exportNoteToPdf(file, title, markdown);
            } else {
                EXPORT_SERVICE.exportNoteToMarkdown(file, title, markdown);
            }
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Экспорт завершён", "Рекомендации сохранены: " + file.getName());
        } catch (Exception ex) {
            UiErrorNotifier.showMappedError(
                    ownerWindow(),
                    isDark,
                    "Ошибка экспорта рекомендаций",
                    ex,
                    ".pdf".equals(extension) ? ErrorCode.EXPORT_PDF_FAILED : ErrorCode.EXPORT_MARKDOWN_FAILED,
                    "Не удалось экспортировать рекомендации фокус-блоков.",
                    false,
                    "operation", ".pdf".equals(extension) ? "exportFocusBlocksPdf" : "exportFocusBlocksMarkdown",
                    "reviewDate", currentResult.reviewDate().toString()
            );
        }
    }

    private File chooseExportFile(String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(".pdf".equals(extension)
                ? "Экспорт рекомендаций фокус-блоков в PDF"
                : "Экспорт рекомендаций фокус-блоков в Markdown");
        String baseName = EXPORT_SERVICE.sanitizeFileName(
                "focus-blocks-" + currentResult.reviewDate(),
                "focus-blocks");
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
        openDailyReviewButton.setDisable(false);
        openAssistantButton.setDisable(false);
    }

    private Window ownerWindow() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
    }
}
