package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.dailyreview.DailyReviewFocusRecommendation;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFreeWindow;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewOverdueItem;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewContentFormatter;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewResult;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewService;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSnapshot;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSummary;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewUpcomingItem;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesExportService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
import com.example.neuroflowplanner.error.ErrorCode;
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
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignW;

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

public class DailyReviewDialog implements InlineView {

    private static final DailyReviewService DEFAULT_DAILY_REVIEW_SERVICE = new DailyReviewService();
    private static final SmartNotesExportService EXPORT_SERVICE = new DefaultSmartNotesExportService();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final boolean isDark = ConfigManager.isDarkTheme();
    private final VBox root;
    private final VBox content;
    private final VBox loadingState;
    private final VBox errorState;
    private final VBox emptyState;
    private final StackPane stateHost;
    private final Label updatedLabel;
    private final Label statusLabel;
    private final Button refreshButton;
    private final Button openInChatButton;
    private final Button openFocusBlocksButton;
    private final Button exportButton;
    private final ContextMenu exportContextMenu;
    private final Consumer<DailyReviewResult> openInChatAction;
    private final Runnable openFocusBlocksAction;
    private final DailyReviewService dailyReviewService;
    private final AtomicInteger loadSequence = new AtomicInteger();
    private Runnable closeAction;
    private DailyReviewResult currentResult;

    private DailyReviewDialog(
            DailyReviewService dailyReviewService,
            Consumer<DailyReviewResult> openInChatAction,
            Runnable openFocusBlocksAction) {
        this.dailyReviewService = dailyReviewService == null ? DEFAULT_DAILY_REVIEW_SERVICE : dailyReviewService;
        this.openInChatAction = openInChatAction;
        this.openFocusBlocksAction = openFocusBlocksAction;
        root = new VBox(0);
        root.getStyleClass().add("daily-review-root");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(18, 22, 14, 22));
        header.getStyleClass().add("daily-review-header");

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("daily-review-header-icon-wrap");
        FontIcon headerIcon = FontIcon.of(MaterialDesignW.WEATHER_SUNNY, 22);
        headerIcon.getStyleClass().add("daily-review-header-icon");
        iconWrap.getChildren().add(headerIcon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Ежедневный обзор");
        title.getStyleClass().add("daily-review-title");
        Label subtitle = new Label(LocalDate.now().format(DATE_FORMAT));
        subtitle.getStyleClass().add("daily-review-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox metaBox = new VBox(3);
        metaBox.setAlignment(Pos.CENTER_RIGHT);
        updatedLabel = new Label("Требует обновления");
        updatedLabel.getStyleClass().add("daily-review-updated");
        statusLabel = new Label("Подготовка обзора");
        statusLabel.getStyleClass().add("daily-review-status");
        metaBox.getChildren().addAll(updatedLabel, statusLabel);

        openInChatButton = new Button("Открыть в чате");
        openInChatButton.getStyleClass().addAll("daily-review-open-chat-btn", "sidebar-btn-primary");
        openInChatButton.setGraphic(new FontIcon(MaterialDesignC.CHAT));
        openInChatButton.setTooltip(new Tooltip("Открыть обзор как стартовый контекст в ИИ-Ассистенте"));
        openInChatButton.setOnAction(event -> handleOpenInChat());

        openFocusBlocksButton = new Button("Фокус-блоки");
        openFocusBlocksButton.getStyleClass().addAll("daily-review-refresh-btn", "sidebar-btn-primary");
        openFocusBlocksButton.setGraphic(new FontIcon(MaterialDesignT.TIMELINE_TEXT_OUTLINE));
        openFocusBlocksButton.setTooltip(new Tooltip("Открыть рекомендации фокус-блоков"));
        openFocusBlocksButton.setOnAction(event -> handleOpenFocusBlocks());

        exportButton = createExportButton();
        exportContextMenu = createExportContextMenu();

        refreshButton = new Button("Обновить обзор");
        refreshButton.getStyleClass().addAll("daily-review-refresh-btn", "sidebar-btn-primary");
        refreshButton.setGraphic(new FontIcon(MaterialDesignR.REFRESH));
        refreshButton.setOnAction(event -> loadReview(true));

        HBox actionsRow = new HBox(8, openInChatButton, openFocusBlocksButton, exportButton, refreshButton);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.getStyleClass().add("daily-review-actions-row");

        VBox actionsBox = new VBox(8, metaBox, actionsRow);
        actionsBox.setAlignment(Pos.CENTER_RIGHT);
        actionsBox.getStyleClass().add("daily-review-actions-box");

        header.getChildren().addAll(iconWrap, titleBox, spacer, actionsBox);

        content = new VBox(16);
        content.setPadding(new Insets(0, 22, 22, 22));
        content.getStyleClass().add("daily-review-content");
        InlineLayoutSupport.makeShrinkable(content);

        loadingState = createLoadingState();
        errorState = createErrorState();
        emptyState = createEmptyState();

        stateHost = new StackPane(content, loadingState, errorState, emptyState);
        stateHost.getStyleClass().add("daily-review-state-host");
        InlineLayoutSupport.makeShrinkable(stateHost);

        ScrollPane scrollPane = InlineLayoutSupport.createContentScroll(stateHost, "daily-review-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(header, scrollPane);
        root.setMinSize(420, 360);
        InlineLayoutSupport.makeShrinkable(root, scrollPane);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        updateActionAvailability();
        showLoadingState("Собираем обзор дня…");
        loadReview(false);
    }

    public static InlineView inline() {
        return new DailyReviewDialog(DEFAULT_DAILY_REVIEW_SERVICE, null, null);
    }

    public static InlineView inline(Consumer<DailyReviewResult> openInChatAction) {
        return new DailyReviewDialog(DEFAULT_DAILY_REVIEW_SERVICE, openInChatAction, null);
    }

    public static InlineView inline(Consumer<DailyReviewResult> openInChatAction, Runnable openFocusBlocksAction) {
        return new DailyReviewDialog(DEFAULT_DAILY_REVIEW_SERVICE, openInChatAction, openFocusBlocksAction);
    }

    static DailyReviewDialog testingInstance(
            DailyReviewService service,
            Consumer<DailyReviewResult> openInChatAction,
            Runnable openFocusBlocksAction) {
        return new DailyReviewDialog(service, openInChatAction, openFocusBlocksAction);
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
        return "Ежедневный обзор";
    }

    private void loadReview(boolean forceRefresh) {
        int requestId = loadSequence.incrementAndGet();
        currentResult = null;
        refreshButton.setDisable(true);
        updateActionAvailability();
        showLoadingState(forceRefresh ? "Обновляем обзор…" : "Собираем обзор дня…");
        dailyReviewService.getReview(LocalDate.now(), forceRefresh)
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

    private void renderResult(DailyReviewResult result) {
        DailyReviewResult safeResult = result == null
                ? new DailyReviewResult(null, Instant.now(), "", false, false)
                : result;
        currentResult = safeResult;
        updateActionAvailability();
        DailyReviewSnapshot snapshot = safeResult.snapshot();

        updatedLabel.setText(formatUpdatedLabel(safeResult.generatedAt(), safeResult.fromCache()));
        statusLabel.setText(safeResult.aiUsed() ? "AI-сводка актуальна" : "Показан локальный обзор");

        if (isEffectivelyEmpty(snapshot)) {
            showOnly(emptyState);
            return;
        }

        content.getChildren().setAll(
                createSummaryCard(safeResult),
                createSection("Просрочки", MaterialDesignA.ALERT_CIRCLE_OUTLINE, buildOverdueNodes(snapshot.overdueItems())),
                createSection("Ближайшие дедлайны", MaterialDesignC.CALENDAR_CLOCK, buildUpcomingNodes(snapshot.upcomingItems())),
                createSection("Свободные окна", MaterialDesignT.TIMELINE_CLOCK, buildFreeWindowNodes(snapshot.freeWindows(), snapshot.approximateFreeWindows())),
                createFocusCard(snapshot.focusRecommendation())
        );
        showOnly(content);
    }

    private VBox createSummaryCard(DailyReviewResult result) {
        DailyReviewSnapshot snapshot = result.snapshot();
        DailyReviewSummary summary = snapshot.summary();

        VBox card = new VBox(12);
        card.getStyleClass().add("daily-review-summary-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(summary.headline().isBlank() ? "Картина дня" : summary.headline());
        title.getStyleClass().add("daily-review-summary-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label sourceChip = new Label(result.aiUsed() ? "AI" : "Fallback");
        sourceChip.getStyleClass().addAll("daily-review-chip", result.aiUsed() ? "daily-review-chip-ai" : "daily-review-chip-fallback");
        header.getChildren().addAll(title, spacer, sourceChip);

        VBox bulletsBox = new VBox(8);
        if (summary.bullets().isEmpty()) {
            bulletsBox.getChildren().add(createEmptyRow("Сводка дня пока недоступна."));
        } else {
            for (String bullet : summary.bullets()) {
                bulletsBox.getChildren().add(createBulletRow(bullet));
            }
        }

        HBox factsRow = new HBox(10);
        factsRow.getStyleClass().add("daily-review-facts-row");
        factsRow.getChildren().addAll(
                createFactCard("Активных", String.valueOf(snapshot.activeTaskCount()), "daily-review-fact-active"),
                createFactCard("Просрочки", String.valueOf(snapshot.overdueTaskCount()), "daily-review-fact-overdue"),
                createFactCard("Сегодня", String.valueOf(snapshot.tasksDueTodayCount()), "daily-review-fact-today"),
                createFactCard("Окон", String.valueOf(snapshot.freeWindows().size()), "daily-review-fact-windows")
        );

        card.getChildren().addAll(header, bulletsBox, factsRow);
        if (!summary.riskNote().isBlank()) {
            HBox riskRow = new HBox();
            riskRow.getStyleClass().add("daily-review-risk-row");
            Label risk = new Label(summary.riskNote());
            risk.getStyleClass().add("daily-review-bullet-text");
            risk.setWrapText(true);
            riskRow.getChildren().add(risk);
            card.getChildren().add(riskRow);
        }
        return card;
    }

    private VBox createSection(String title, Ikon iconCode, List<Node> bodyNodes) {
        VBox section = new VBox(12);
        section.getStyleClass().add("daily-review-section");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.getStyleClass().add("daily-review-section-icon");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("daily-review-section-title");
        header.getChildren().addAll(icon, titleLabel);

        VBox body = new VBox(8);
        body.getChildren().addAll(bodyNodes);
        section.getChildren().addAll(header, body);
        return section;
    }

    private VBox createFocusCard(DailyReviewFocusRecommendation focus) {
        VBox card = new VBox(10);
        card.getStyleClass().add("daily-review-focus-card");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(MaterialDesignL.LIGHTBULB_ON, 18);
        icon.getStyleClass().add("daily-review-focus-icon");
        Label title = new Label("Рекомендация фокуса");
        title.getStyleClass().add("daily-review-section-title");
        header.getChildren().addAll(icon, title);

        Label focusTitle = new Label(focus.title().isBlank() ? "Фокус дня пока не определён" : focus.title());
        focusTitle.getStyleClass().add("daily-review-focus-title");
        focusTitle.setWrapText(true);

        Label rationale = new Label(focus.rationale().isBlank() ? "Ориентир собран из текущих сигналов дня." : focus.rationale());
        rationale.getStyleClass().add("daily-review-focus-rationale");
        rationale.setWrapText(true);

        Label step = new Label(focus.suggestedNextStep().isBlank()
                ? "Обзор пока не дал конкретной рекомендации."
                : focus.suggestedNextStep());
        step.getStyleClass().add("daily-review-focus-step");
        step.setWrapText(true);

        card.getChildren().addAll(header, focusTitle, rationale, new Separator(), step);
        return card;
    }

    private List<Node> buildOverdueNodes(List<DailyReviewOverdueItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of(createEmptyRow("Просроченных задач нет."));
        }
        return items.stream()
                .map(item -> createInfoItem(
                        item.title(),
                        item.deadlineDateTime() != null
                                ? "Просрочка " + item.overdueDays() + " дн. • " + item.deadlineDateTime().format(DATE_TIME_FORMAT)
                                : "Просрочка " + item.overdueDays() + " дн.",
                        "daily-review-item-overdue"))
                .map(Node.class::cast)
                .toList();
    }

    private List<Node> buildUpcomingNodes(List<DailyReviewUpcomingItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of(createEmptyRow("Ближайших дедлайнов не найдено."));
        }
        return items.stream()
                .map(item -> {
                    String meta = item.dueToday() ? "Сегодня" : "Через " + item.daysUntilDue() + " дн.";
                    if (item.deadlineDateTime() != null) {
                        meta += " • " + item.deadlineDateTime().format(DATE_TIME_FORMAT);
                    }
                    if (item.urgent()) {
                        meta += " • срочно";
                    }
                    return createInfoItem(item.title(), meta, item.urgent() ? "daily-review-item-urgent" : "daily-review-item-upcoming");
                })
                .map(Node.class::cast)
                .toList();
    }

    private List<Node> buildFreeWindowNodes(List<DailyReviewFreeWindow> windows, boolean approximate) {
        if (windows == null || windows.isEmpty()) {
            return List.of(createEmptyRow(approximate
                    ? "Нет надёжных данных для точного расчёта свободных окон."
                    : "Свободные окна не найдены."));
        }
        return windows.stream()
                .map(window -> {
                    String meta = window.durationMinutes() + " мин • "
                            + window.suitability().name().replace('_', ' ').toLowerCase();
                    if (window.approximate()) {
                        meta += " • приблизительно";
                    }
                    return createInfoItem(window.label(), meta, "daily-review-item-window");
                })
                .map(Node.class::cast)
                .toList();
    }

    private HBox createInfoItem(String title, String meta, String styleClass) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("daily-review-item", styleClass);

        VBox textBox = new VBox(3);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("daily-review-item-title");
        titleLabel.setWrapText(true);
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("daily-review-item-meta");
        metaLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, metaLabel);
        row.getChildren().add(textBox);

        Tooltip.install(row, new Tooltip(title + (meta == null || meta.isBlank() ? "" : "\n" + meta)));
        return row;
    }

    private HBox createBulletRow(String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.TOP_LEFT);
        Label bullet = new Label("•");
        bullet.getStyleClass().add("daily-review-bullet-mark");
        Label label = new Label(text);
        label.getStyleClass().add("daily-review-bullet-text");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        row.getChildren().addAll(bullet, label);
        return row;
    }

    private VBox createFactCard(String label, String value, String styleClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("daily-review-fact-card", styleClass);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("daily-review-fact-value");
        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("daily-review-fact-label");
        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private HBox createEmptyRow(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("daily-review-empty-row");
        Label label = new Label(text);
        label.getStyleClass().add("daily-review-muted");
        label.setWrapText(true);
        row.getChildren().add(label);
        return row;
    }

    private VBox createLoadingState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("daily-review-placeholder");
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(42, 42);
        Label label = new Label("Собираем обзор дня…");
        label.getStyleClass().add("daily-review-placeholder-title");
        box.getChildren().addAll(indicator, label);
        return box;
    }

    private VBox createErrorState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("daily-review-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE_OUTLINE, 28);
        icon.getStyleClass().add("daily-review-error-icon");
        Label title = new Label("Не удалось загрузить обзор");
        title.getStyleClass().add("daily-review-placeholder-title");
        Label body = new Label("Попробуйте обновить обзор ещё раз.");
        body.getStyleClass().add("daily-review-muted");
        body.setWrapText(true);
        body.setMaxWidth(320);
        box.getChildren().addAll(icon, title, body);
        return box;
    }

    private VBox createEmptyState() {
        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("daily-review-placeholder");
        FontIcon icon = FontIcon.of(MaterialDesignC.CALENDAR_TODAY, 28);
        icon.getStyleClass().add("daily-review-empty-icon");
        Label title = new Label("На сегодня обзор почти пуст");
        title.getStyleClass().add("daily-review-placeholder-title");
        Label body = new Label("Нет активных задач, просрочек и ближайших дедлайнов. Можно начать день спокойно.");
        body.getStyleClass().add("daily-review-muted");
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
                ? "Попробуйте обновить обзор ещё раз."
                : details.trim());
        updatedLabel.setText("Ошибка обновления");
        statusLabel.setText("Обзор недоступен");
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

    private boolean isEffectivelyEmpty(DailyReviewSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }
        return snapshot.activeTaskCount() == 0
                && snapshot.overdueItems().isEmpty()
                && snapshot.upcomingItems().isEmpty()
                && snapshot.freeWindows().isEmpty();
    }

    private String formatUpdatedLabel(Instant generatedAt, boolean fromCache) {
        Instant safeInstant = generatedAt == null ? Instant.now() : generatedAt;
        String label = "Обновлено " + TIME_FORMAT.format(safeInstant.atZone(ZoneId.systemDefault()).toLocalTime());
        return fromCache ? label + " • cache" : label;
    }

    private Button createExportButton() {
        Button button = new Button("Экспорт");
        button.getStyleClass().add("daily-review-export-btn");
        FontIcon exportIcon = FontIcon.of(MaterialDesignF.FILE_EXPORT_OUTLINE, 16);
        exportIcon.getStyleClass().add("daily-review-export-icon");
        FontIcon caretIcon = FontIcon.of(MaterialDesignC.CHEVRON_DOWN, 14);
        caretIcon.getStyleClass().add("daily-review-export-caret");
        HBox graphic = new HBox(5, exportIcon, caretIcon);
        graphic.setAlignment(Pos.CENTER);
        button.setGraphic(graphic);
        button.setTooltip(new Tooltip("Экспортировать обзор в Markdown или PDF"));
        installExportButtonVisualState(button);
        MenuItem markdownItem = new MenuItem("Markdown (.md)");
        markdownItem.setOnAction(event -> exportCurrentReview(".md"));
        MenuItem pdfItem = new MenuItem("PDF (.pdf)");
        pdfItem.setOnAction(event -> exportCurrentReview(".pdf"));
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
        button.getProperties().put("dailyReviewMarkdownExportItem", markdownItem);
        button.getProperties().put("dailyReviewPdfExportItem", pdfItem);
        return button;
    }

    private void installExportButtonVisualState(Button button) {
        String baseStyle = isDark
                ? "-fx-background-color: linear-gradient(to bottom, rgba(49, 50, 68, 0.98), rgba(39, 40, 55, 0.98));"
                + "-fx-border-color: rgba(137, 180, 250, 0.24);"
                + "-fx-text-fill: #cdd6f4;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;"
                : "-fx-background-color: linear-gradient(to bottom, #c7dcff, #a9c8ff);"
                + "-fx-border-color: rgba(29, 78, 216, 0.36);"
                + "-fx-text-fill: #102a56;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;";
        String hoverStyle = isDark
                ? "-fx-background-color: linear-gradient(to bottom, rgba(56, 58, 79, 1), rgba(45, 47, 64, 1));"
                + "-fx-border-color: rgba(116, 199, 236, 0.38);"
                + "-fx-text-fill: #cdd6f4;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;"
                : "-fx-background-color: linear-gradient(to bottom, #b8d2ff, #96bbff);"
                + "-fx-border-color: rgba(29, 78, 216, 0.46);"
                + "-fx-text-fill: #102a56;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;";
        String pressedStyle = isDark
                ? "-fx-background-color: linear-gradient(to bottom, rgba(63, 65, 87, 1), rgba(49, 50, 68, 1));"
                + "-fx-border-color: rgba(137, 180, 250, 0.42);"
                + "-fx-text-fill: #cdd6f4;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;"
                : "-fx-background-color: linear-gradient(to bottom, #a9c8ff, #86b3ff);"
                + "-fx-border-color: rgba(29, 78, 216, 0.52);"
                + "-fx-text-fill: #102a56;"
                + "-fx-border-width: 1;"
                + "-fx-background-radius: 999px;"
                + "-fx-border-radius: 999px;";
        button.setStyle(baseStyle);
        button.setOnMouseEntered(event -> button.setStyle(hoverStyle));
        button.setOnMouseExited(event -> button.setStyle(baseStyle));
        button.setOnMousePressed(event -> button.setStyle(pressedStyle));
        button.setOnMouseReleased(event -> button.setStyle(button.isHover() ? hoverStyle : baseStyle));
    }

    private ContextMenu createExportContextMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("daily-review-export-context-menu");
        MenuItem markdownItem = (MenuItem) exportButton.getProperties().get("dailyReviewMarkdownExportItem");
        MenuItem pdfItem = (MenuItem) exportButton.getProperties().get("dailyReviewPdfExportItem");
        if (markdownItem != null) {
            markdownItem.getStyleClass().add("daily-review-export-menu-item");
        }
        if (pdfItem != null) {
            pdfItem.getStyleClass().add("daily-review-export-menu-item");
        }
        menu.getItems().setAll(markdownItem, pdfItem);
        return menu;
    }

    private void handleOpenInChat() {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Ежедневный обзор", "Сначала дождитесь загрузки обзора.");
            return;
        }
        if (openInChatAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Ежедневный обзор", "Открытие обзора в чате сейчас недоступно.");
            return;
        }
        openInChatAction.accept(currentResult);
    }

    private void handleOpenFocusBlocks() {
        if (openFocusBlocksAction == null) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, "Ежедневный обзор", "Открытие фокус-блоков сейчас недоступно.");
            return;
        }
        openFocusBlocksAction.run();
    }

    private void exportCurrentReview(String extension) {
        if (currentResult == null) {
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Ежедневный обзор", "Сначала дождитесь загрузки обзора.");
            return;
        }
        File file = chooseExportFile(extension);
        if (file == null) {
            return;
        }
        String title = DailyReviewContentFormatter.buildExportTitle(currentResult);
        String markdown = DailyReviewContentFormatter.toMarkdown(currentResult);
        try {
            if (".pdf".equals(extension)) {
                EXPORT_SERVICE.exportNoteToPdf(file, title, markdown);
            } else {
                EXPORT_SERVICE.exportNoteToMarkdown(file, title, markdown);
            }
            UiErrorNotifier.showInfo(ownerWindow(), isDark, "Экспорт завершён", "Обзор сохранён: " + file.getName());
        } catch (Exception ex) {
            UiErrorNotifier.showMappedError(
                    ownerWindow(),
                    isDark,
                    "Ошибка экспорта обзора",
                    ex,
                    ".pdf".equals(extension) ? ErrorCode.EXPORT_PDF_FAILED : ErrorCode.EXPORT_MARKDOWN_FAILED,
                    "Не удалось экспортировать ежедневный обзор.",
                    false,
                    "operation", ".pdf".equals(extension) ? "exportDailyReviewPdf" : "exportDailyReviewMarkdown",
                    "reviewDate", currentResult.reviewDate().toString()
            );
        }
    }

    private File chooseExportFile(String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(".pdf".equals(extension) ? "Экспорт обзора в PDF" : "Экспорт обзора в Markdown");
        String baseName = EXPORT_SERVICE.sanitizeFileName(
                "daily-review-" + currentResult.reviewDate(),
                "daily-review");
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
        openFocusBlocksButton.setDisable(false);
    }

    private Window ownerWindow() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
    }
}
