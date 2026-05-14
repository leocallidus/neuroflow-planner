package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inline Gantt chart view.
 */
public class GanttChartDialog implements InlineView {

    private static final Locale RU_LOCALE = new Locale("ru");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("dd", RU_LOCALE);
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMM", RU_LOCALE);
    private static final DateTimeFormatter RANGE_FORMATTER = DateTimeFormatter.ofPattern("d MMM", RU_LOCALE);
    private static final double INLINE_OVERLAY_WIDTH_COMPACT_THRESHOLD = 1380.0;
    private static final double INLINE_OVERLAY_WIDTH_VERY_COMPACT_THRESHOLD = 1320.0;
    private static final double INLINE_OVERLAY_HEIGHT_LOW_THRESHOLD = 800.0;
    private static final double INLINE_OVERLAY_HEIGHT_VERY_LOW_THRESHOLD = 700.0;
    private static final Duration GANTT_TOOLTIP_SHOW_DELAY = Duration.millis(240);
    private static final Duration GANTT_TOOLTIP_HIDE_DELAY = Duration.millis(120);
    private static final Duration GANTT_TOOLTIP_SHOW_DURATION = Duration.seconds(18);

    private final VBox root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final List<Task> tasks;
    private final List<Node> focusableRowNodes = new ArrayList<>();
    private final List<Node> focusableBarNodes = new ArrayList<>();
    private final Map<Integer, Node> rowNodesByIndex = new HashMap<>();
    private final Map<Integer, Node> barNodesByRowIndex = new HashMap<>();
    private Runnable closeAction;
    private LayoutSignature appliedLayout;
    private boolean refreshScheduled;
    private double preservedHorizontalScroll;
    private double preservedVerticalScroll;
    private Integer preservedFocusedRowIndex;
    private Integer preservedFocusedBarIndex;
    private ScrollPane activeTimelineHeader;
    private ScrollPane activeLabelsScrollPane;
    private ScrollPane activeChartScrollPane;

    private GanttChartDialog(List<Task> tasks) {
        this.tasks = List.copyOf(tasks);

        root = new VBox(0);
        root.setMinSize(0, 0);
        root.setFillWidth(true);
        root.getStyleClass().add("gantt-root");
        root.setAccessibleText("Диаграмма Ганта. Используйте стрелки вверх и вниз для перемещения по строкам, вправо для перехода к полосе задачи.");

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        installAdaptiveObservers();
        refreshLayout();
    }

    public static InlineView inline(List<Task> tasks) {
        return new GanttChartDialog(tasks);
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
        return "Диаграмма Ганта";
    }

    private void installAdaptiveObservers() {
        root.widthProperty().addListener((obs, oldValue, newValue) -> scheduleRefresh());
        root.heightProperty().addListener((obs, oldValue, newValue) -> scheduleRefresh());
        root.parentProperty().addListener((obs, oldValue, newValue) -> scheduleRefresh());
        root.sceneProperty().addListener((obs, oldValue, newValue) -> scheduleRefresh());
    }

    private void scheduleRefresh() {
        if (refreshScheduled) {
            return;
        }
        refreshScheduled = true;
        Platform.runLater(() -> {
            refreshScheduled = false;
            refreshLayout();
        });
    }

    private void refreshLayout() {
        captureInteractiveState();
        GanttDateRange dateRange = resolveDateRange(tasks);
        List<GanttRowData> rows = collectRows(tasks);
        GanttSummary summary = buildSummary(rows, dateRange);
        AdaptiveContext adaptiveContext = resolveAdaptiveContext();
        GanttEmptyState emptyState = resolveEmptyState(rows);
        DensityConfig density = resolveDensityConfig(adaptiveContext, dateRange, rows.size());
        LayoutSignature nextLayout = new LayoutSignature(density, adaptiveContext);
        if (nextLayout.equals(appliedLayout)) {
            return;
        }

        appliedLayout = nextLayout;
        applyAdaptiveStyleClasses(density, adaptiveContext);
        resetInteractiveCollections();

        List<Node> sections = new ArrayList<>();
        sections.add(buildHeaderSection(summary, dateRange, density));

        if (emptyState != null) {
            sections.add(buildEmptyStateSection(emptyState, density));
        } else {
            Node metaSection = buildMetaSection(summary, dateRange, density);
            if (metaSection != null) {
                sections.add(metaSection);
            }

            Node legendSection = buildLegendSection(density);
            if (legendSection != null) {
                sections.add(legendSection);
            }

            Node fallbackBanner = buildFallbackBanner(dateRange, adaptiveContext, density);
            if (fallbackBanner != null) {
                sections.add(fallbackBanner);
            }

            sections.add(buildChartSection(rows, dateRange, density));
        }
        root.getChildren().setAll(sections);
    }

    private void applyAdaptiveStyleClasses(DensityConfig density, AdaptiveContext adaptiveContext) {
        root.getStyleClass().removeAll(
                "gantt-density-comfortable",
                "gantt-density-compact",
                "gantt-density-very-compact",
                "gantt-host-width-compact",
                "gantt-host-width-very-compact",
                "gantt-host-height-low",
                "gantt-host-height-very-low"
        );
        root.getStyleClass().add("gantt-density-" + density.mode().cssToken());
        if (adaptiveContext.compactWidth()) {
            root.getStyleClass().add("gantt-host-width-compact");
        }
        if (adaptiveContext.veryCompactWidth()) {
            root.getStyleClass().add("gantt-host-width-very-compact");
        }
        if (adaptiveContext.lowHeight()) {
            root.getStyleClass().add("gantt-host-height-low");
        }
        if (adaptiveContext.veryLowHeight()) {
            root.getStyleClass().add("gantt-host-height-very-low");
        }
    }

    private Node buildHeaderSection(GanttSummary summary, GanttDateRange dateRange, DensityConfig density) {
        VBox header = new VBox(density.sectionGap());
        header.setPadding(new Insets(density.headerPadding(), density.headerPadding() + density.headerInsetX(),
                density.headerPadding(), density.headerPadding() + density.headerInsetX()));
        header.getStyleClass().add("gantt-header-panel");

        HBox headerTop = new HBox(density.headerGap());
        headerTop.setAlignment(Pos.CENTER_LEFT);
        headerTop.getStyleClass().add("gantt-header-top");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("gantt-icon-container");
        iconPane.setPadding(new Insets(density.iconPadding()));

        FontIcon icon = FontIcon.of(MaterialDesignC.CHART_TIMELINE, (int) Math.round(density.iconSize()));
        icon.getStyleClass().add("gantt-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(density.titleGap());
        titleBox.getStyleClass().add("gantt-header-copy");

        Label kicker = new Label("ПЛАНИРОВАНИЕ");
        kicker.getStyleClass().add("gantt-header-kicker");

        Label title = new Label("Диаграмма Ганта");
        title.getStyleClass().add("gantt-title");
        titleBox.getChildren().addAll(kicker, title);

        if (density.showSubtitle()) {
            Label subtitle = new Label("Визуализация времени и зависимостей");
            subtitle.getStyleClass().add("gantt-subtitle");
            titleBox.getChildren().add(subtitle);
        }

        FlowPane headerBadges = new FlowPane();
        headerBadges.setHgap(density.legendItemGap());
        headerBadges.setVgap(density.legendItemGap());
        headerBadges.getStyleClass().add("gantt-header-badges");
        headerBadges.getChildren().add(createContextChip("Период " + formatDateRange(dateRange), "gantt-context-chip-period"));
        headerBadges.getChildren().add(createContextChip("Сегодня " + dateRange.today().format(RANGE_FORMATTER), "gantt-context-chip-today"));
        titleBox.getChildren().add(headerBadges);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        FlowPane summaryStrip = buildHeaderSummaryStrip(summary, dateRange, density);
        headerTop.getChildren().addAll(iconPane, titleBox, spacer, summaryStrip);
        header.getChildren().add(headerTop);
        return header;
    }

    private Node buildMetaSection(GanttSummary summary, GanttDateRange dateRange, DensityConfig density) {
        if (!density.showMetaRow()) {
            return null;
        }

        FlowPane metaRow = new FlowPane();
        metaRow.setHgap(density.metaGap());
        metaRow.setVgap(density.legendItemGap());
        metaRow.setPadding(new Insets(0, density.headerPadding() + density.headerInsetX(),
                density.sectionGap(), density.headerPadding() + density.headerInsetX()));
        metaRow.getStyleClass().add("gantt-meta-row");

        metaRow.getChildren().add(createMetaChip("Период", formatDateRange(dateRange), density, "gantt-meta-chip-period"));
        metaRow.getChildren().add(createMetaChip("Горизонт", (dateRange.totalDays() + 1) + " дн.", density, "gantt-meta-chip-horizon"));
        metaRow.getChildren().add(createMetaChip("Режим", resolveDensityLabel(density.mode()), density, "gantt-meta-chip-density"));
        if (!density.compactMetaRow()) {
            metaRow.getChildren().add(createMetaChip("Parent/Subtask",
                    summary.parentTasks() + " / " + summary.subtasks(), density, "gantt-meta-chip-hierarchy"));
        }
        metaRow.getChildren().add(createMetaChip("Фокус", summary.overdueTasks() > 0
                ? "Просрочено: " + summary.overdueTasks()
                : "Просроченных нет", density, "gantt-meta-chip-focus"));
        return metaRow;
    }

    private Node createMetaChip(String label, String value, DensityConfig density, String... extraStyleClasses) {
        VBox chip = new VBox(density.titleGap());
        chip.getStyleClass().add("gantt-meta-chip");
        chip.getStyleClass().addAll(extraStyleClasses);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("gantt-meta-label");

        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("gantt-meta-value");

        chip.getChildren().addAll(labelNode, valueNode);
        return chip;
    }

    private Node buildLegendSection(DensityConfig density) {
        if (!density.showLegend()) {
            return null;
        }

        FlowPane legend = new FlowPane();
        legend.setHgap(density.metaGap());
        legend.setVgap(density.metaGap());
        legend.setPadding(new Insets(0, density.headerPadding() + density.headerInsetX(),
                density.sectionGap(), density.headerPadding() + density.headerInsetX()));
        legend.getStyleClass().add("gantt-legend-row");

        legend.getChildren().add(createLegendItem("gantt-legend-marker-high", "Высокий приоритет", density));
        legend.getChildren().add(createLegendItem("gantt-legend-marker-medium", "Средний приоритет", density));
        if (!density.compactLegend()) {
            legend.getChildren().add(createLegendItem("gantt-legend-marker-low", "Низкий приоритет", density));
        }
        legend.getChildren().add(createLegendItem("gantt-legend-marker-overdue", "Просрочено", density));
        legend.getChildren().add(createLegendItem("gantt-legend-marker-today", "Линия сегодня", density));
        legend.getChildren().add(createLegendItem("gantt-legend-marker-parent", "Родительская задача", density));
        legend.getChildren().add(createLegendItem("gantt-legend-marker-subtask", "Подзадача", density));
        return legend;
    }

    private Node createLegendItem(String markerStyleClass, String text, DensityConfig density) {
        HBox item = new HBox(density.legendItemGap());
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("gantt-legend-item");

        Region marker = new Region();
        marker.getStyleClass().addAll("gantt-legend-marker", markerStyleClass);

        Label label = new Label(text);
        label.getStyleClass().add("gantt-legend-label");

        item.getChildren().addAll(marker, label);
        return item;
    }

    private Node buildChartSection(List<GanttRowData> rows, GanttDateRange dateRange, DensityConfig density) {
        VBox chartSection = new VBox(0);
        chartSection.setMinHeight(0);
        chartSection.getStyleClass().add("gantt-layout-section");
        chartSection.setPadding(new Insets(0, density.headerPadding() + density.headerInsetX(),
                density.headerPadding(), density.headerPadding() + density.headerInsetX()));

        double chartWidth = density.chartWidth(dateRange.totalDays());
        double chartHeight = density.chartHeight(rows.size());

        HBox timelineRow = new HBox(0);
        timelineRow.getStyleClass().add("gantt-timeline-row");

        StackPane labelsHeader = buildLabelsHeader(density);
        ScrollPane timelineHeader = buildTimelineHeader(dateRange, density, chartWidth);
        HBox.setHgrow(timelineHeader, Priority.ALWAYS);
        timelineRow.getChildren().addAll(labelsHeader, timelineHeader);

        HBox bodyRow = new HBox(0);
        bodyRow.setMinHeight(0);
        bodyRow.getStyleClass().add("gantt-body-row");
        VBox.setVgrow(bodyRow, Priority.ALWAYS);

        ScrollPane labelsScrollPane = buildLabelsColumn(rows, density, dateRange);
        ScrollPane chartScrollPane = buildChartArea(rows, dateRange, density, chartWidth, chartHeight);

        labelsScrollPane.vvalueProperty().bindBidirectional(chartScrollPane.vvalueProperty());
        timelineHeader.hvalueProperty().bindBidirectional(chartScrollPane.hvalueProperty());

        HBox.setHgrow(chartScrollPane, Priority.ALWAYS);
        bodyRow.getChildren().addAll(labelsScrollPane, chartScrollPane);

        chartSection.getChildren().addAll(timelineRow, bodyRow);
        VBox.setVgrow(chartSection, Priority.ALWAYS);
        bindInteractiveState(timelineHeader, labelsScrollPane, chartScrollPane);
        return chartSection;
    }

    private StackPane buildLabelsHeader(DensityConfig density) {
        StackPane labelsHeader = new StackPane();
        labelsHeader.setPrefWidth(density.taskColumnWidth());
        labelsHeader.setMinWidth(density.taskColumnWidth());
        labelsHeader.setMaxWidth(density.taskColumnWidth());
        labelsHeader.getStyleClass().addAll("gantt-timeline-header", "gantt-labels-header");

        Label labelsTitle = new Label("Задачи");
        labelsTitle.getStyleClass().add("gantt-labels-header-title");
        labelsHeader.getChildren().add(labelsTitle);
        StackPane.setAlignment(labelsTitle, Pos.CENTER_LEFT);
        StackPane.setMargin(labelsTitle, new Insets(0, density.iconPadding(), 0, density.labelBasePadding()));
        return labelsHeader;
    }

    private ScrollPane buildTimelineHeader(GanttDateRange dateRange, DensityConfig density, double chartWidth) {
        HBox timelineHeader = new HBox(0);
        timelineHeader.setMinWidth(chartWidth);
        timelineHeader.setPrefWidth(chartWidth);
        timelineHeader.setMaxWidth(chartWidth);
        timelineHeader.getStyleClass().add("gantt-timeline-header");

        for (int i = 0; i <= dateRange.totalDays(); i++) {
            LocalDate date = dateRange.minDate().plusDays(i);
            VBox dayCell = new VBox(2);
            dayCell.setPrefWidth(density.dayWidth());
            dayCell.setMinWidth(density.dayWidth());
            dayCell.setMaxWidth(density.dayWidth());
            dayCell.setAlignment(Pos.CENTER);
            dayCell.getStyleClass().add("gantt-header-cell");

            Label dayLbl = new Label(resolveDayLabel(date, dateRange, density, i));
            dayLbl.getStyleClass().add("gantt-header-day");

            Label monthLbl = new Label(resolveMonthLabel(date, dateRange, density, i));
            monthLbl.getStyleClass().add("gantt-header-month");

            if (date.equals(dateRange.today())) {
                dayCell.getStyleClass().add("gantt-header-today");
            }

            dayCell.getChildren().addAll(dayLbl, monthLbl);
            timelineHeader.getChildren().add(dayCell);
        }

        ScrollPane timelineScrollPane = new ScrollPane(timelineHeader);
        timelineScrollPane.setFitToHeight(true);
        timelineScrollPane.setFitToWidth(false);
        timelineScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timelineScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        timelineScrollPane.setPannable(false);
        timelineScrollPane.setMinHeight(0);
        timelineScrollPane.getStyleClass().addAll("gantt-scroll-pane", "gantt-timeline-scroll-pane");
        return timelineScrollPane;
    }

    private ScrollPane buildLabelsColumn(List<GanttRowData> rows, DensityConfig density, GanttDateRange dateRange) {
        VBox labelsColumn = new VBox(0);
        labelsColumn.setFillWidth(true);
        labelsColumn.setPrefWidth(density.taskColumnWidth());
        labelsColumn.setMinWidth(density.taskColumnWidth());
        labelsColumn.setMaxWidth(density.taskColumnWidth());
        labelsColumn.getStyleClass().add("gantt-labels-column");

        for (GanttRowData row : rows) {
            labelsColumn.getChildren().add(createLabelRow(row, density, dateRange));
        }

        ScrollPane labelsScrollPane = new ScrollPane(labelsColumn);
        labelsScrollPane.setFitToWidth(true);
        labelsScrollPane.setFitToHeight(true);
        labelsScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        labelsScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        labelsScrollPane.setMinWidth(density.taskColumnWidth());
        labelsScrollPane.setPrefWidth(density.taskColumnWidth());
        labelsScrollPane.setMaxWidth(density.taskColumnWidth());
        labelsScrollPane.setMinHeight(0);
        labelsScrollPane.getStyleClass().addAll("gantt-scroll-pane", "gantt-labels-scroll-pane");
        return labelsScrollPane;
    }

    private Node createLabelRow(GanttRowData row, DensityConfig density, GanttDateRange dateRange) {
        HBox rowBox = new HBox(density.legendItemGap());
        rowBox.setPrefHeight(density.rowHeight());
        rowBox.setMinHeight(density.rowHeight());
        rowBox.setMaxHeight(density.rowHeight());
        rowBox.setAlignment(Pos.CENTER_LEFT);
        rowBox.setFocusTraversable(true);
        rowBox.getStyleClass().add("gantt-focusable-row");
        applyRowStyleClasses(rowBox, row, dateRange);
        rowBox.setAccessibleText(buildRowAccessibleText(row, dateRange));
        rowBox.addEventHandler(KeyEvent.KEY_PRESSED, event -> handleRowKeyNavigation(event, row.rowIndex()));
        rowBox.setOnMouseClicked(event -> rowBox.requestFocus());

        Node hierarchyMarker = buildRowHierarchyMarker(row, density);

        Label nameLabel = new Label(row.displayTitle());
        nameLabel.setWrapText(false);
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        nameLabel.setEllipsisString("...");
        nameLabel.setPadding(new Insets(0, density.iconPadding(), 0, 0));
        nameLabel.getStyleClass().add("gantt-task-name");
        if (row.indent() > 0) {
            nameLabel.getStyleClass().add("gantt-task-name-subtask");
        }
        Tooltip.install(nameLabel, createTaskTooltip(row.task(), resolveTaskTimelineWindowOrNull(row.task(), dateRange), dateRange));

        HBox statusBadges = buildRowStatusBadges(row, density, dateRange);
        statusBadges.setMinWidth(Region.USE_PREF_SIZE);
        statusBadges.setMaxWidth(Region.USE_PREF_SIZE);

        HBox.setHgrow(nameLabel, Priority.ALWAYS);
        rowBox.getChildren().addAll(hierarchyMarker, nameLabel, statusBadges);
        registerRowFocusNode(row.rowIndex(), rowBox);
        return rowBox;
    }

    private ScrollPane buildChartArea(List<GanttRowData> rows,
                                      GanttDateRange dateRange,
                                      DensityConfig density,
                                      double chartWidth,
                                      double chartHeight) {
        VBox rowBackground = new VBox(0);
        rowBackground.setPrefWidth(chartWidth);
        rowBackground.setMinWidth(chartWidth);
        rowBackground.setMaxWidth(chartWidth);

        Pane barLayer = new Pane();
        barLayer.setPrefSize(chartWidth, chartHeight);
        barLayer.setMinSize(chartWidth, chartHeight);
        barLayer.setMaxSize(chartWidth, chartHeight);
        barLayer.getStyleClass().add("gantt-chart-area");

        Pane gridLayer = new Pane();
        gridLayer.setMouseTransparent(true);
        gridLayer.setPrefSize(chartWidth, chartHeight);
        gridLayer.setMinSize(chartWidth, chartHeight);
        gridLayer.setMaxSize(chartWidth, chartHeight);

        for (GanttRowData row : rows) {
            rowBackground.getChildren().add(createChartBackgroundRow(row, density, chartWidth));
            addTaskBar(barLayer, row, dateRange, density);
        }
        addGridLines(gridLayer, dateRange, density, chartHeight);

        StackPane chartCanvas = new StackPane(rowBackground, gridLayer, barLayer);
        chartCanvas.setAlignment(Pos.TOP_LEFT);
        chartCanvas.setMinSize(chartWidth, chartHeight);
        chartCanvas.setPrefSize(chartWidth, chartHeight);
        chartCanvas.getStyleClass().add("gantt-chart-canvas");

        ScrollPane chartScrollPane = new ScrollPane(chartCanvas);
        chartScrollPane.setFitToHeight(true);
        chartScrollPane.setFitToWidth(false);
        chartScrollPane.setPannable(true);
        chartScrollPane.setMinHeight(0);
        chartScrollPane.getStyleClass().addAll("gantt-scroll-pane", "gantt-chart-scroll-pane");
        VBox.setVgrow(chartScrollPane, Priority.ALWAYS);
        return chartScrollPane;
    }

    private Node createChartBackgroundRow(GanttRowData row, DensityConfig density, double chartWidth) {
        Region rowBackground = new Region();
        rowBackground.setPrefHeight(density.rowHeight());
        rowBackground.setMinHeight(density.rowHeight());
        rowBackground.setMaxHeight(density.rowHeight());
        rowBackground.setPrefWidth(chartWidth);
        rowBackground.setMinWidth(chartWidth);
        rowBackground.setMaxWidth(chartWidth);
        applyRowStyleClasses(rowBackground, row, null);
        return rowBackground;
    }

    private void addGridLines(Pane gridLayer, GanttDateRange dateRange, DensityConfig density, double chartHeight) {
        for (int i = 0; i <= dateRange.totalDays(); i++) {
            Line gridLine = new Line(i * density.dayWidth(), 0, i * density.dayWidth(), chartHeight);
            gridLine.getStyleClass().add("gantt-grid-line");
            if (dateRange.minDate().plusDays(i).equals(dateRange.today())) {
                gridLine.getStyleClass().add("gantt-grid-line-today");
            }
            gridLayer.getChildren().add(gridLine);
        }
    }

    private void addTaskBar(Pane chartContainer, GanttRowData row, GanttDateRange dateRange, DensityConfig density) {
        Task task = row.task();
        if (task.getDeadline() == null) {
            return;
        }

        TaskTimelineWindow window = resolveTaskTimelineWindow(task, dateRange);
        LocalDate startDate = window.startDate();
        LocalDate endDate = window.endDate();
        long startOffset = ChronoUnit.DAYS.between(dateRange.minDate(), startDate);
        long endOffset = ChronoUnit.DAYS.between(dateRange.minDate(), endDate);
        if (endOffset < 0) {
            return;
        }

        double rawX = startOffset * density.dayWidth() + (density.barHorizontalMargin() / 2.0);
        double logicalWidth = Math.max(((endOffset - startOffset + 1) * density.dayWidth()) - density.barHorizontalMargin(),
                density.minBarWidth());
        double visualWidth = Math.max(logicalWidth, Math.min(Math.max(density.dayWidth() * 0.74, density.minBarWidth()), 18.0));
        double contentInsetX = Math.max(0.0, (visualWidth - logicalWidth) / 2.0);
        double layoutX = logicalWidth < visualWidth
                ? clamp(0.0, rawX - ((visualWidth - logicalWidth) / 2.0), Math.max(0.0, density.chartWidth(dateRange.totalDays()) - visualWidth))
                : rawX;
        double y = (row.rowIndex() * density.rowHeight()) + density.barVerticalInset();
        double height = density.rowHeight() - (density.barVerticalInset() * 2);

        Pane bar = new Pane();
        bar.setManaged(false);
        bar.setFocusTraversable(true);
        bar.setLayoutX(layoutX);
        bar.setLayoutY(y);
        bar.setPrefSize(visualWidth, height);
        bar.setMinSize(visualWidth, height);
        bar.setMaxSize(visualWidth, height);

        String priorityClass = task.getSmartPriority() >= 7 ? "gantt-bar-high"
                : task.getSmartPriority() >= 4 ? "gantt-bar-medium" : "gantt-bar-low";
        boolean overdue = endDate.isBefore(dateRange.today()) && !task.isCompleted();
        if (overdue) {
            priorityClass = "gantt-bar-overdue";
        }

        bar.getStyleClass().addAll("gantt-bar", priorityClass);
        if (row.indent() > 0) {
            bar.getStyleClass().add("gantt-bar-subtask");
        } else {
            bar.getStyleClass().add("gantt-bar-parent");
        }
        if (task.isCompleted()) {
            bar.getStyleClass().add("gantt-bar-completed");
        }
        if (visualWidth <= density.dayWidth() * 0.85) {
            bar.getStyleClass().add("gantt-bar-short");
        }
        if (includesToday(startDate, endDate, dateRange.today())) {
            bar.getStyleClass().add("gantt-bar-has-today");
        }
        bar.getStyleClass().add("gantt-focusable-bar");
        bar.setAccessibleText(buildBarAccessibleText(task, window, dateRange, row.indent() > 0));
        bar.addEventHandler(KeyEvent.KEY_PRESSED, event -> handleBarKeyNavigation(event, row.rowIndex()));
        bar.setOnMouseClicked(event -> bar.requestFocus());
        decorateTaskBar(bar, row, window, dateRange, density, visualWidth, height, logicalWidth, contentInsetX, overdue);
        Tooltip.install(bar, createTaskTooltip(task, window, dateRange));
        registerBarFocusNode(row.rowIndex(), bar);
        chartContainer.getChildren().add(bar);
    }

    private Tooltip createTaskTooltip(Task task, TaskTimelineWindow window, GanttDateRange dateRange) {
        Tooltip tooltip = new Tooltip(buildTaskTooltipText(task, window, dateRange));
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(320);
        tooltip.setShowDelay(GANTT_TOOLTIP_SHOW_DELAY);
        tooltip.setHideDelay(GANTT_TOOLTIP_HIDE_DELAY);
        tooltip.setShowDuration(GANTT_TOOLTIP_SHOW_DURATION);
        tooltip.getStyleClass().add("gantt-tooltip");
        return tooltip;
    }

    private String buildTaskTooltipText(Task task, TaskTimelineWindow window, GanttDateRange dateRange) {
        LocalDate startDate = window != null ? window.startDate() : task.getStartDate();
        LocalDate endDate = task.getDeadline();
        int estimatedDuration = window != null ? window.durationDays() : Math.max(1, task.getComplexity() / 2);

        String timingLine;
        if (endDate == null) {
            timingLine = "Смещение: дедлайн не задан";
        } else if (task.isCompleted()) {
            timingLine = "Смещение: задача завершена";
        } else if (endDate.isBefore(dateRange.today())) {
            long overdueDays = ChronoUnit.DAYS.between(endDate, dateRange.today());
            timingLine = "Смещение: просрочено на " + overdueDays + " дн.";
        } else if (endDate.isEqual(dateRange.today())) {
            timingLine = "Смещение: дедлайн сегодня";
        } else {
            long daysLeft = ChronoUnit.DAYS.between(dateRange.today(), endDate);
            timingLine = "Смещение: до дедлайна " + daysLeft + " дн.";
        }

        return task.getTitle()
                + "\nСтарт: " + formatOptionalDate(startDate)
                + "\nДедлайн: " + formatOptionalDate(endDate)
                + "\nОценка: " + estimatedDuration + " дн."
                + "\n" + timingLine
                + "\nПриоритет: " + String.format("%.1f", task.getSmartPriority());
    }

    private String buildRowAccessibleText(GanttRowData row, GanttDateRange dateRange) {
        Task task = row.task();
        String hierarchy = row.indent() > 0 ? "Подзадача" : "Задача";
        String deadline = task.getDeadline() == null ? "без дедлайна" : "дедлайн " + formatOptionalDate(task.getDeadline());
        String state = task.isCompleted()
                ? "завершена"
                : task.getDeadline() != null && task.getDeadline().isBefore(dateRange.today())
                ? "просрочена"
                : task.getDeadline() != null && task.getDeadline().isEqual(dateRange.today())
                ? "дедлайн сегодня"
                : "активна";
        return hierarchy + ". " + task.getTitle() + ". " + deadline + ". " + state + ". Приоритет "
                + String.format("%.1f", task.getSmartPriority()) + ".";
    }

    private String buildBarAccessibleText(Task task, TaskTimelineWindow window, GanttDateRange dateRange, boolean subtask) {
        String hierarchy = subtask ? "Подзадача" : "Задача";
        String state;
        if (task.isCompleted()) {
            state = "завершена";
        } else if (task.getDeadline() != null && task.getDeadline().isBefore(dateRange.today())) {
            state = "просрочена";
        } else if (task.getDeadline() != null && task.getDeadline().isEqual(dateRange.today())) {
            state = "дедлайн сегодня";
        } else {
            state = "активна";
        }
        return hierarchy + ". " + task.getTitle()
                + ". Старт " + formatOptionalDate(window.startDate())
                + ". Дедлайн " + formatOptionalDate(window.endDate())
                + ". Длительность " + window.durationDays() + " дней"
                + ". " + state + ".";
    }

    private FlowPane buildHeaderSummaryStrip(GanttSummary summary, GanttDateRange dateRange, DensityConfig density) {
        FlowPane strip = new FlowPane();
        strip.setHgap(density.metaGap());
        strip.setVgap(density.legendItemGap());
        strip.setAlignment(Pos.CENTER_RIGHT);
        strip.getStyleClass().add("gantt-summary-strip");

        strip.getChildren().add(createSummaryCard("Задач", Integer.toString(summary.totalTasks()),
                "gantt-summary-card-neutral", density));
        strip.getChildren().add(createSummaryCard("Просрочено", Integer.toString(summary.overdueTasks()),
                summary.overdueTasks() > 0 ? "gantt-summary-card-alert" : "gantt-summary-card-calm", density));

        if (density.mode() != DensityMode.VERY_COMPACT) {
            strip.getChildren().add(createSummaryCard("Высокий приоритет", Integer.toString(summary.highPriorityTasks()),
                    "gantt-summary-card-priority", density));
        }
        if (density.mode() == DensityMode.COMFORTABLE) {
            strip.getChildren().add(createSummaryCard("Горизонт", (dateRange.totalDays() + 1) + " дн.",
                    "gantt-summary-card-neutral", density));
        }
        return strip;
    }

    private Node createSummaryCard(String label, String value, String toneClass, DensityConfig density) {
        VBox card = new VBox(density.titleGap());
        card.getStyleClass().addAll("gantt-summary-card", toneClass);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("gantt-summary-value");

        Label labelLabel = new Label(label);
        labelLabel.getStyleClass().add("gantt-summary-label");

        card.getChildren().addAll(valueLabel, labelLabel);
        return card;
    }

    private Node createContextChip(String text, String... extraStyleClasses) {
        Label chip = new Label(text);
        chip.getStyleClass().add("gantt-context-chip");
        chip.getStyleClass().addAll(extraStyleClasses);
        return chip;
    }

    private Node buildEmptyStateSection(GanttEmptyState emptyState, DensityConfig density) {
        VBox emptyStateBox = new VBox(density.sectionGap());
        emptyStateBox.setPadding(new Insets(0, density.headerPadding() + density.headerInsetX(),
                density.headerPadding(), density.headerPadding() + density.headerInsetX()));
        emptyStateBox.setAlignment(Pos.CENTER_LEFT);
        emptyStateBox.getStyleClass().add("gantt-empty-state");

        Label title = new Label(switch (emptyState) {
            case NO_TASKS -> "Пока нет задач для диаграммы";
            case NO_DEADLINES -> "Для диаграммы нужны дедлайны";
            case ARCHIVED_ONLY -> "Активных задач для диаграммы нет";
        });
        title.getStyleClass().add("gantt-empty-title");

        Label text = new Label(switch (emptyState) {
            case NO_TASKS -> "Добавьте задачи с дедлайном, и диаграмма сразу покажет временной горизонт и приоритеты.";
            case NO_DEADLINES -> "Задачи есть, но ни у одной нет дедлайна. Добавьте сроки, чтобы увидеть bars и timeline.";
            case ARCHIVED_ONLY -> "Сейчас в списке только архивные задачи. Верните хотя бы одну активную задачу, чтобы построить диаграмму.";
        });
        text.setWrapText(true);
        text.getStyleClass().add("gantt-empty-text");

        emptyStateBox.getChildren().addAll(title, text);
        return emptyStateBox;
    }

    private Node buildFallbackBanner(GanttDateRange dateRange, AdaptiveContext adaptiveContext, DensityConfig density) {
        boolean narrow = adaptiveContext.compactWidth() || adaptiveContext.veryCompactWidth();
        boolean low = adaptiveContext.lowHeight() || adaptiveContext.veryLowHeight();
        long visibleDays = dateRange.totalDays() + 1;
        if (visibleDays < 60 || (!narrow && !low)) {
            return null;
        }

        VBox banner = new VBox(4);
        banner.setPadding(new Insets(0, density.headerPadding() + density.headerInsetX(),
                density.sectionGap(), density.headerPadding() + density.headerInsetX()));
        banner.getStyleClass().add("gantt-fallback-banner");

        Label title = new Label("Длинный горизонт, уплотнённый режим");
        title.getStyleClass().add("gantt-fallback-title");

        Label text = new Label("Период " + visibleDays
                + " дн. Подписи и bars уплотнены, короткие задачи усилены маркерами, подробности доступны в tooltip.");
        text.setWrapText(true);
        text.getStyleClass().add("gantt-fallback-text");

        banner.getChildren().addAll(title, text);
        return banner;
    }

    private Node buildRowHierarchyMarker(GanttRowData row, DensityConfig density) {
        HBox markerBox = new HBox(4);
        markerBox.setAlignment(Pos.CENTER_LEFT);
        markerBox.setMinWidth(density.labelBasePadding() + (row.indent() * density.labelIndentStep()) + 16);
        markerBox.setPrefWidth(density.labelBasePadding() + (row.indent() * density.labelIndentStep()) + 16);
        markerBox.setMaxWidth(density.labelBasePadding() + (row.indent() * density.labelIndentStep()) + 16);
        markerBox.getStyleClass().add("gantt-row-hierarchy-marker");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (row.indent() > 0) {
            Region connector = new Region();
            connector.getStyleClass().add("gantt-row-hierarchy-connector");
            markerBox.getChildren().addAll(spacer, connector);
        } else {
            markerBox.getChildren().add(spacer);
        }

        Region bullet = new Region();
        bullet.getStyleClass().addAll("gantt-row-hierarchy-bullet",
                row.indent() > 0 ? "gantt-row-hierarchy-bullet-subtask" : "gantt-row-hierarchy-bullet-parent");
        markerBox.getChildren().add(bullet);
        return markerBox;
    }

    private void registerRowFocusNode(int rowIndex, Node node) {
        focusableRowNodes.add(node);
        rowNodesByIndex.put(rowIndex, node);
    }

    private void registerBarFocusNode(int rowIndex, Node node) {
        focusableBarNodes.add(node);
        barNodesByRowIndex.put(rowIndex, node);
    }

    private void bindInteractiveState(ScrollPane timelineHeader, ScrollPane labelsScrollPane, ScrollPane chartScrollPane) {
        activeTimelineHeader = timelineHeader;
        activeLabelsScrollPane = labelsScrollPane;
        activeChartScrollPane = chartScrollPane;

        chartScrollPane.hvalueProperty().addListener((obs, oldValue, newValue) -> preservedHorizontalScroll = newValue.doubleValue());
        chartScrollPane.vvalueProperty().addListener((obs, oldValue, newValue) -> preservedVerticalScroll = newValue.doubleValue());
        labelsScrollPane.vvalueProperty().addListener((obs, oldValue, newValue) -> preservedVerticalScroll = newValue.doubleValue());

        Platform.runLater(this::restoreInteractiveState);
    }

    private void restoreInteractiveState() {
        if (activeChartScrollPane != null) {
            activeChartScrollPane.setHvalue(preservedHorizontalScroll);
            activeChartScrollPane.setVvalue(preservedVerticalScroll);
        }
        if (activeTimelineHeader != null) {
            activeTimelineHeader.setHvalue(preservedHorizontalScroll);
        }
        if (activeLabelsScrollPane != null) {
            activeLabelsScrollPane.setVvalue(preservedVerticalScroll);
        }
        if (preservedFocusedBarIndex != null) {
            Node node = barNodesByRowIndex.get(preservedFocusedBarIndex);
            if (node != null) {
                node.requestFocus();
                return;
            }
        }
        if (preservedFocusedRowIndex != null) {
            Node node = rowNodesByIndex.get(preservedFocusedRowIndex);
            if (node != null) {
                node.requestFocus();
            }
        }
    }

    private void captureInteractiveState() {
        if (activeChartScrollPane != null) {
            preservedHorizontalScroll = activeChartScrollPane.getHvalue();
            preservedVerticalScroll = activeChartScrollPane.getVvalue();
        } else if (activeLabelsScrollPane != null) {
            preservedVerticalScroll = activeLabelsScrollPane.getVvalue();
        }

        Node focusOwner = root.getScene() != null ? root.getScene().getFocusOwner() : null;
        preservedFocusedRowIndex = resolveFocusedNodeIndex(focusOwner, rowNodesByIndex);
        preservedFocusedBarIndex = resolveFocusedNodeIndex(focusOwner, barNodesByRowIndex);
    }

    private Integer resolveFocusedNodeIndex(Node focusOwner, Map<Integer, Node> indexedNodes) {
        if (focusOwner == null) {
            return null;
        }
        for (Map.Entry<Integer, Node> entry : indexedNodes.entrySet()) {
            Node candidate = entry.getValue();
            if (candidate == focusOwner || isAncestorOf(candidate, focusOwner)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isAncestorOf(Node ancestor, Node candidate) {
        Node current = candidate;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private void resetInteractiveCollections() {
        focusableRowNodes.clear();
        focusableBarNodes.clear();
        rowNodesByIndex.clear();
        barNodesByRowIndex.clear();
        activeTimelineHeader = null;
        activeLabelsScrollPane = null;
        activeChartScrollPane = null;
    }

    private void handleRowKeyNavigation(KeyEvent event, int rowIndex) {
        if (event.getCode() == KeyCode.DOWN) {
            focusRow(rowIndex + 1);
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            focusRow(rowIndex - 1);
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT || event.getCode() == KeyCode.ENTER) {
            focusBar(rowIndex);
            event.consume();
        }
    }

    private void handleBarKeyNavigation(KeyEvent event, int rowIndex) {
        if (event.getCode() == KeyCode.LEFT) {
            focusRow(rowIndex);
            event.consume();
        } else if (event.getCode() == KeyCode.RIGHT) {
            focusNextBar(rowIndex, 1);
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            focusPreviousAvailableBar(rowIndex);
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            focusNextAvailableBar(rowIndex);
            event.consume();
        }
    }

    private void focusRow(int rowIndex) {
        Node node = rowNodesByIndex.get(rowIndex);
        if (node != null) {
            node.requestFocus();
        }
    }

    private void focusBar(int rowIndex) {
        Node node = barNodesByRowIndex.get(rowIndex);
        if (node != null) {
            node.requestFocus();
        }
    }

    private void focusNextBar(int currentRowIndex, int direction) {
        int nextIndex = currentRowIndex + direction;
        Node node = barNodesByRowIndex.get(nextIndex);
        if (node != null) {
            node.requestFocus();
        }
    }

    private void focusPreviousAvailableBar(int currentRowIndex) {
        for (int index = currentRowIndex - 1; index >= 0; index--) {
            Node node = barNodesByRowIndex.get(index);
            if (node != null) {
                node.requestFocus();
                return;
            }
        }
        focusRow(currentRowIndex - 1);
    }

    private void focusNextAvailableBar(int currentRowIndex) {
        for (int index = currentRowIndex + 1; index <= rowNodesByIndex.size(); index++) {
            Node node = barNodesByRowIndex.get(index);
            if (node != null) {
                node.requestFocus();
                return;
            }
        }
        focusRow(currentRowIndex + 1);
    }

    private HBox buildRowStatusBadges(GanttRowData row, DensityConfig density, GanttDateRange dateRange) {
        HBox badges = new HBox(4);
        badges.setAlignment(Pos.CENTER_RIGHT);
        badges.getStyleClass().add("gantt-row-badges");

        Task task = row.task();
        if (task.isCompleted()) {
            badges.getChildren().add(createRowBadge("Готово", "gantt-row-badge-completed"));
        } else if (task.getDeadline() != null && task.getDeadline().isBefore(dateRange.today())) {
            badges.getChildren().add(createRowBadge("Просрочено", "gantt-row-badge-overdue"));
        } else if (task.getDeadline() != null && task.getDeadline().isEqual(dateRange.today())) {
            badges.getChildren().add(createRowBadge("Сегодня", "gantt-row-badge-today"));
        }

        boolean hasCriticalStateBadge = !badges.getChildren().isEmpty();
        if (task.getSmartPriority() >= 7.0 && density.mode() == DensityMode.COMFORTABLE) {
            badges.getChildren().add(createRowBadge("P1", "gantt-row-badge-priority-high"));
        } else if (task.getSmartPriority() >= 7.0 && density.mode() == DensityMode.COMPACT && !hasCriticalStateBadge) {
            badges.getChildren().add(createRowBadge("P1", "gantt-row-badge-priority-high"));
        } else if (task.getSmartPriority() >= 4.0 && density.mode() == DensityMode.COMFORTABLE) {
            badges.getChildren().add(createRowBadge("P2", "gantt-row-badge-priority-medium"));
        }

        if (row.indent() == 0 && task.hasSubtasks() && density.mode() == DensityMode.COMFORTABLE) {
            badges.getChildren().add(createRowBadge(task.getSubtasks().size() + " подз.", "gantt-row-badge-hierarchy"));
        }
        if (task.isRecurring() && density.mode() == DensityMode.COMFORTABLE) {
            badges.getChildren().add(createRowBadge("Повтор", "gantt-row-badge-recurring"));
        }
        return badges;
    }

    private Node createRowBadge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().addAll("gantt-row-badge", styleClass);
        return badge;
    }

    private void decorateTaskBar(Pane bar,
                                 GanttRowData row,
                                 TaskTimelineWindow window,
                                 GanttDateRange dateRange,
                                 DensityConfig density,
                                 double barWidth,
                                 double barHeight,
                                 double logicalWidth,
                                 double contentInsetX,
                                 boolean overdue) {
        boolean shortBar = bar.getStyleClass().contains("gantt-bar-short");
        if (shortBar) {
            decorateShortTaskBar(bar, row, window, dateRange, barWidth, barHeight, overdue);
            return;
        }

        double capWidth = Math.min(4.0, Math.max(2.0, Math.floor(density.dayWidth() * 0.16)));
        double capHeight = Math.max(8.0, barHeight - 6.0);
        double capY = Math.max(1.0, (barHeight - capHeight) / 2.0);

        Region startCap = createBarCue("gantt-bar-cap", "gantt-bar-cap-start");
        startCap.resizeRelocate(0, capY, capWidth, capHeight);
        bar.getChildren().add(startCap);

        Region endCap = createBarCue("gantt-bar-cap", "gantt-bar-cap-end");
        endCap.resizeRelocate(Math.max(0.0, barWidth - capWidth), capY, capWidth, capHeight);
        bar.getChildren().add(endCap);

        if (overdue) {
            Region overdueAccent = createBarCue("gantt-bar-overdue-accent");
            overdueAccent.resizeRelocate(Math.max(0.0, barWidth - 4.0), 0, 4.0, barHeight);
            bar.getChildren().add(overdueAccent);

            Region overdueMarker = createBarCue("gantt-bar-overdue-marker");
            overdueMarker.resizeRelocate(2.0, Math.max(2.0, (barHeight - 6.0) / 2.0), 6.0, 6.0);
            bar.getChildren().add(overdueMarker);
        }

        if (includesToday(window.startDate(), window.endDate(), dateRange.today())) {
            long todayOffset = ChronoUnit.DAYS.between(window.startDate(), dateRange.today());
            double logicalTodayX = contentInsetX + ((todayOffset + 0.5) * density.dayWidth()) - (density.barHorizontalMargin() / 2.0);
            double todayX = clamp(1.0, logicalTodayX, Math.max(1.0, Math.min(barWidth - 3.0, contentInsetX + logicalWidth)));
            Region todayMarker = createBarCue("gantt-bar-today-intersection");
            todayMarker.resizeRelocate(todayX, 1.0, 3.0, Math.max(6.0, barHeight - 2.0));
            bar.getChildren().add(todayMarker);
        }

        if (row.indent() > 0) {
            Region hierarchyAccent = createBarCue("gantt-bar-hierarchy-accent");
            hierarchyAccent.resizeRelocate(1.0, Math.max(1.0, barHeight - 3.0), Math.max(8.0, barWidth * 0.28), 2.0);
            bar.getChildren().add(hierarchyAccent);
        }
    }

    private void decorateShortTaskBar(Pane bar,
                                      GanttRowData row,
                                      TaskTimelineWindow window,
                                      GanttDateRange dateRange,
                                      double barWidth,
                                      double barHeight,
                                      boolean overdue) {
        double glyphSize = Math.max(6.0, Math.min(barHeight - 2.0, barWidth - 2.0));
        double glyphX = Math.max(1.0, (barWidth - glyphSize) / 2.0);
        double glyphY = Math.max(1.0, (barHeight - glyphSize) / 2.0);

        Region glyph;
        if (overdue) {
            glyph = createBarCue("gantt-bar-overdue-marker");
        } else if (includesToday(window.startDate(), window.endDate(), dateRange.today())) {
            glyph = createBarCue("gantt-bar-today-intersection");
        } else {
            glyph = createBarCue("gantt-bar-short-indicator");
        }
        glyph.resizeRelocate(glyphX, glyphY, glyphSize, glyphSize);
        bar.getChildren().add(glyph);

        if (row.indent() > 0 && barWidth >= glyphSize + 4.0) {
            Region hierarchyAccent = createBarCue("gantt-bar-hierarchy-accent");
            hierarchyAccent.resizeRelocate(1.0, Math.max(1.0, barHeight - 3.0), Math.max(4.0, barWidth - 2.0), 2.0);
            bar.getChildren().add(hierarchyAccent);
        }
    }

    private Region createBarCue(String... styleClasses) {
        Region cue = new Region();
        cue.setManaged(false);
        cue.setMouseTransparent(true);
        cue.getStyleClass().addAll(styleClasses);
        return cue;
    }

    private List<GanttRowData> collectRows(List<Task> tasks) {
        List<GanttRowData> rows = new ArrayList<>();
        int rowIndex = 0;
        for (Task task : tasks) {
            if (task.isArchived()) {
                continue;
            }
            rows.add(new GanttRowData(task, rowIndex++, 0));
            for (Task subtask : task.getSubtasks()) {
                if (!subtask.isArchived()) {
                    rows.add(new GanttRowData(subtask, rowIndex++, 1));
                }
            }
        }
        return rows;
    }

    private GanttDateRange resolveDateRange(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(3);
        LocalDate maxDate = today.plusDays(14);

        for (Task task : tasks) {
            if (!task.isArchived() && task.getDeadline() != null) {
                if (task.getDeadline().isAfter(maxDate)) {
                    maxDate = task.getDeadline();
                }
                LocalDate startEstimate = task.getDeadline().minusDays(Math.max(1, task.getComplexity()));
                if (startEstimate.isBefore(minDate)) {
                    minDate = startEstimate;
                }
            }
        }

        minDate = minDate.minusDays(1);
        maxDate = maxDate.plusDays(3);
        long totalDays = ChronoUnit.DAYS.between(minDate, maxDate);
        return new GanttDateRange(today, minDate, maxDate, totalDays);
    }

    private AdaptiveContext resolveAdaptiveContext() {
        Node overlayContainer = findAncestorWithStyleClass(root, "overlay-container");
        double hostWidth = resolveHostWidth(overlayContainer);
        double hostHeight = resolveHostHeight(overlayContainer);

        boolean compactWidth = hasStyleClass(overlayContainer, "inline-overlay-width-compact")
                || (hostWidth > 0.0 && hostWidth < INLINE_OVERLAY_WIDTH_COMPACT_THRESHOLD);
        boolean veryCompactWidth = hasStyleClass(overlayContainer, "inline-overlay-width-very-compact")
                || (hostWidth > 0.0 && hostWidth < INLINE_OVERLAY_WIDTH_VERY_COMPACT_THRESHOLD);
        boolean lowHeight = hasStyleClass(overlayContainer, "inline-overlay-height-low")
                || (hostHeight > 0.0 && hostHeight < INLINE_OVERLAY_HEIGHT_LOW_THRESHOLD);
        boolean veryLowHeight = hasStyleClass(overlayContainer, "inline-overlay-height-very-low")
                || (hostHeight > 0.0 && hostHeight < INLINE_OVERLAY_HEIGHT_VERY_LOW_THRESHOLD);

        return new AdaptiveContext(hostWidth, hostHeight, compactWidth, veryCompactWidth, lowHeight, veryLowHeight);
    }

    private double resolveHostWidth(Node overlayContainer) {
        if (overlayContainer instanceof Region region && region.getWidth() > 0.0) {
            return region.getWidth();
        }
        if (root.getWidth() > 0.0) {
            return root.getWidth();
        }
        return root.getScene() != null ? root.getScene().getWidth() : 0.0;
    }

    private double resolveHostHeight(Node overlayContainer) {
        if (overlayContainer instanceof Region region && region.getHeight() > 0.0) {
            return region.getHeight();
        }
        if (root.getHeight() > 0.0) {
            return root.getHeight();
        }
        return root.getScene() != null ? root.getScene().getHeight() : 0.0;
    }

    private Node findAncestorWithStyleClass(Node start, String styleClass) {
        Parent current = start.getParent();
        while (current != null) {
            if (current.getStyleClass().contains(styleClass)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean hasStyleClass(Node node, String styleClass) {
        return node != null && node.getStyleClass().contains(styleClass);
    }

    private DensityConfig resolveDensityConfig(AdaptiveContext adaptiveContext, GanttDateRange dateRange, int rowCount) {
        DensityMode mode = adaptiveContext.veryCompactWidth() || adaptiveContext.veryLowHeight()
                ? DensityMode.VERY_COMPACT
                : adaptiveContext.compactWidth() || adaptiveContext.lowHeight()
                ? DensityMode.COMPACT
                : DensityMode.COMFORTABLE;

        double hostWidth = adaptiveContext.hostWidth() > 0.0 ? adaptiveContext.hostWidth() : 1280.0;
        double hostHeight = adaptiveContext.hostHeight() > 0.0 ? adaptiveContext.hostHeight() : 820.0;

        double headerPadding = switch (mode) {
            case COMFORTABLE -> clamp(18.0, Math.min(hostWidth, hostHeight) * 0.018, 24.0);
            case COMPACT -> clamp(14.0, Math.min(hostWidth, hostHeight) * 0.015, 18.0);
            case VERY_COMPACT -> clamp(10.0, Math.min(hostWidth, hostHeight) * 0.012, 14.0);
        };
        double headerGap = switch (mode) {
            case COMFORTABLE -> 12.0;
            case COMPACT -> 10.0;
            case VERY_COMPACT -> 8.0;
        };
        double titleGap = switch (mode) {
            case COMFORTABLE -> 3.0;
            case COMPACT -> 2.0;
            case VERY_COMPACT -> 1.0;
        };
        double metaGap = switch (mode) {
            case COMFORTABLE -> 10.0;
            case COMPACT -> 8.0;
            case VERY_COMPACT -> 6.0;
        };
        double labelBasePadding = switch (mode) {
            case COMFORTABLE -> 14.0;
            case COMPACT -> 12.0;
            case VERY_COMPACT -> 10.0;
        };
        double labelIndentStep = switch (mode) {
            case COMFORTABLE -> 15.0;
            case COMPACT -> 13.0;
            case VERY_COMPACT -> 11.0;
        };
        double legendItemGap = switch (mode) {
            case COMFORTABLE -> 6.0;
            case COMPACT -> 5.0;
            case VERY_COMPACT -> 4.0;
        };
        double sectionGap = switch (mode) {
            case COMFORTABLE -> 10.0;
            case COMPACT -> 8.0;
            case VERY_COMPACT -> 6.0;
        };
        double barHorizontalMargin = switch (mode) {
            case COMFORTABLE -> 6.0;
            case COMPACT -> 5.0;
            case VERY_COMPACT -> 4.0;
        };
        double barVerticalInset = switch (mode) {
            case COMFORTABLE -> 8.0;
            case COMPACT -> 7.0;
            case VERY_COMPACT -> 6.0;
        };
        double minBarWidth = switch (mode) {
            case COMFORTABLE -> 10.0;
            case COMPACT -> 9.0;
            case VERY_COMPACT -> 8.0;
        };
        double iconSize = switch (mode) {
            case COMFORTABLE -> 22.0;
            case COMPACT -> 20.0;
            case VERY_COMPACT -> 18.0;
        };
        double iconPadding = switch (mode) {
            case COMFORTABLE -> 8.0;
            case COMPACT -> 7.0;
            case VERY_COMPACT -> 6.0;
        };
        double headerInsetX = switch (mode) {
            case COMFORTABLE -> 4.0;
            case COMPACT -> 2.0;
            case VERY_COMPACT -> 0.0;
        };

        double estimatedChromeHeight = switch (mode) {
            case COMFORTABLE -> densityChromeEstimate(headerPadding, true, true);
            case COMPACT -> densityChromeEstimate(headerPadding, true, true);
            case VERY_COMPACT -> densityChromeEstimate(headerPadding, false, true);
        };
        double availableContentHeight = Math.max(hostHeight - estimatedChromeHeight, 220.0);
        double targetVisibleRows = switch (mode) {
            case COMFORTABLE -> 10.0;
            case COMPACT -> 12.0;
            case VERY_COMPACT -> 14.0;
        };
        double rowHeight = clamp(
                mode == DensityMode.VERY_COMPACT ? 24.0 : mode == DensityMode.COMPACT ? 27.0 : 30.0,
                availableContentHeight / Math.max(1.0, Math.min(Math.max(rowCount, 1), targetVisibleRows)),
                mode == DensityMode.VERY_COMPACT ? 28.0 : mode == DensityMode.COMPACT ? 32.0 : 36.0
        );

        double estimatedChromeWidth = (headerPadding + headerInsetX) * 2.0;
        double availableWidth = Math.max(hostWidth - estimatedChromeWidth, 480.0);
        double taskColumnWidth = switch (mode) {
            case COMFORTABLE -> clamp(160.0, availableWidth * 0.24, 220.0);
            case COMPACT -> clamp(152.0, availableWidth * 0.24, 204.0);
            case VERY_COMPACT -> clamp(168.0, availableWidth * 0.27, 232.0);
        };
        double chartViewportWidth = Math.max(availableWidth - taskColumnWidth, 260.0);
        double targetVisibleDays = switch (mode) {
            case COMFORTABLE -> 18.0;
            case COMPACT -> 22.0;
            case VERY_COMPACT -> 28.0;
        };
        double dayWidth = clamp(
                mode == DensityMode.VERY_COMPACT ? 18.0 : mode == DensityMode.COMPACT ? 22.0 : 28.0,
                chartViewportWidth / Math.max(1.0, Math.min(dateRange.totalDays() + 1.0, targetVisibleDays)),
                mode == DensityMode.VERY_COMPACT ? 24.0 : mode == DensityMode.COMPACT ? 28.0 : 34.0
        );

        return new DensityConfig(
                mode,
                dayWidth,
                rowHeight,
                taskColumnWidth,
                headerPadding,
                headerGap,
                titleGap,
                metaGap,
                labelBasePadding,
                labelIndentStep,
                legendItemGap,
                sectionGap,
                barHorizontalMargin,
                barVerticalInset,
                minBarWidth,
                iconSize,
                iconPadding,
                headerInsetX,
                mode != DensityMode.VERY_COMPACT,
                true,
                mode != DensityMode.VERY_COMPACT,
                mode != DensityMode.COMFORTABLE,
                mode == DensityMode.VERY_COMPACT,
                mode == DensityMode.VERY_COMPACT
        );
    }

    private double densityChromeEstimate(double headerPadding, boolean metaVisible, boolean legendVisible) {
        double height = (headerPadding * 2.0) + 56.0 + 44.0;
        if (metaVisible) {
            height += 56.0;
        }
        if (legendVisible) {
            height += 48.0;
        }
        return height;
    }

    private double clamp(double min, double value, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String resolveDayLabel(LocalDate date, GanttDateRange dateRange, DensityConfig density, int index) {
        if (date.equals(dateRange.today())) {
            return date.format(DAY_FORMATTER);
        }
        return switch (density.mode()) {
            case COMFORTABLE -> date.format(DAY_FORMATTER);
            case COMPACT -> date.format(DAY_FORMATTER);
            case VERY_COMPACT -> index % 2 == 0 ? date.format(DAY_FORMATTER) : "";
        };
    }

    private String resolveMonthLabel(LocalDate date, GanttDateRange dateRange, DensityConfig density, int index) {
        boolean monthBoundary = date.getDayOfMonth() == 1 || index == 0;
        if (date.equals(dateRange.today()) || monthBoundary) {
            return date.format(MONTH_FORMATTER);
        }
        return switch (density.mode()) {
            case COMFORTABLE -> date.format(MONTH_FORMATTER);
            case COMPACT -> index % 7 == 0 ? date.format(MONTH_FORMATTER) : "";
            case VERY_COMPACT -> "";
        };
    }

    private String formatDateRange(GanttDateRange dateRange) {
        return dateRange.minDate().format(RANGE_FORMATTER) + " - " + dateRange.maxDate().format(RANGE_FORMATTER);
    }

    private String formatOptionalDate(LocalDate date) {
        return date == null ? "не задан" : date.format(RANGE_FORMATTER);
    }

    private void applyRowStyleClasses(Region region, GanttRowData row, GanttDateRange dateRange) {
        region.getStyleClass().add("gantt-row");
        if (row.rowIndex() % 2 != 0) {
            region.getStyleClass().add("gantt-row-odd");
        }
        if (row.indent() > 0) {
            region.getStyleClass().add("gantt-subtask-row");
        } else {
            region.getStyleClass().add("gantt-parent-row");
        }
        if (row.task().isCompleted()) {
            region.getStyleClass().add("gantt-row-completed");
        }
        if (dateRange != null && row.task().getDeadline() != null) {
            if (row.task().getDeadline().isBefore(dateRange.today()) && !row.task().isCompleted()) {
                region.getStyleClass().add("gantt-row-overdue");
            } else if (row.task().getDeadline().isEqual(dateRange.today())) {
                region.getStyleClass().add("gantt-row-due-today");
            }
        }
    }

    private boolean includesToday(LocalDate startDate, LocalDate endDate, LocalDate today) {
        return !today.isBefore(startDate) && !today.isAfter(endDate);
    }

    private String resolveDensityLabel(DensityMode mode) {
        return switch (mode) {
            case COMFORTABLE -> "Комфортный";
            case COMPACT -> "Компактный";
            case VERY_COMPACT -> "Очень плотный";
        };
    }

    private GanttSummary buildSummary(List<GanttRowData> rows, GanttDateRange dateRange) {
        int totalTasks = rows.size();
        int parentTasks = (int) rows.stream().filter(row -> row.indent() == 0).count();
        int subtasks = Math.max(totalTasks - parentTasks, 0);
        int overdueTasks = (int) rows.stream()
                .map(GanttRowData::task)
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(dateRange.today()))
                .count();
        int highPriorityTasks = (int) rows.stream()
                .map(GanttRowData::task)
                .filter(task -> task.getSmartPriority() >= 7.0)
                .count();
        return new GanttSummary(totalTasks, parentTasks, subtasks, overdueTasks, highPriorityTasks);
    }

    private GanttEmptyState resolveEmptyState(List<GanttRowData> rows) {
        if (tasks.isEmpty()) {
            return GanttEmptyState.NO_TASKS;
        }
        if (rows.isEmpty()) {
            return GanttEmptyState.ARCHIVED_ONLY;
        }
        boolean hasDeadlines = rows.stream().anyMatch(row -> row.task().getDeadline() != null);
        if (!hasDeadlines) {
            return GanttEmptyState.NO_DEADLINES;
        }
        return null;
    }

    private TaskTimelineWindow resolveTaskTimelineWindow(Task task, GanttDateRange dateRange) {
        LocalDate endDate = task.getDeadline();
        int durationDays = Math.max(1, task.getComplexity() / 2);
        LocalDate startDate = task.hasStartDate() ? task.getStartDate() : endDate.minusDays(durationDays);
        if (startDate == null) {
            startDate = endDate.minusDays(durationDays);
        }
        if (startDate.isBefore(dateRange.minDate())) {
            startDate = dateRange.minDate();
        }
        return new TaskTimelineWindow(startDate, endDate, durationDays);
    }

    private TaskTimelineWindow resolveTaskTimelineWindowOrNull(Task task, GanttDateRange dateRange) {
        if (task.getDeadline() == null) {
            return null;
        }
        return resolveTaskTimelineWindow(task, dateRange);
    }

    private enum DensityMode {
        COMFORTABLE("comfortable"),
        COMPACT("compact"),
        VERY_COMPACT("very-compact");

        private final String cssToken;

        DensityMode(String cssToken) {
            this.cssToken = cssToken;
        }

        public String cssToken() {
            return cssToken;
        }
    }

    private record AdaptiveContext(double hostWidth,
                                   double hostHeight,
                                   boolean compactWidth,
                                   boolean veryCompactWidth,
                                   boolean lowHeight,
                                   boolean veryLowHeight) {
    }

    private record DensityConfig(DensityMode mode,
                                 double dayWidth,
                                 double rowHeight,
                                 double taskColumnWidth,
                                 double headerPadding,
                                 double headerGap,
                                 double titleGap,
                                 double metaGap,
                                 double labelBasePadding,
                                 double labelIndentStep,
                                 double legendItemGap,
                                 double sectionGap,
                                 double barHorizontalMargin,
                                 double barVerticalInset,
                                 double minBarWidth,
                                 double iconSize,
                                 double iconPadding,
                                 double headerInsetX,
                                 boolean showSubtitle,
                                 boolean showMetaRow,
                                 boolean showLegend,
                                 boolean compactMetaRow,
                                 boolean compactLegend,
                                 boolean reducedTimelineLabels) {

        private double chartWidth(long totalDays) {
            return (totalDays + 1) * dayWidth;
        }

        private double chartHeight(int rows) {
            return Math.max(rows, 1) * rowHeight;
        }
    }

    private record GanttDateRange(LocalDate today, LocalDate minDate, LocalDate maxDate, long totalDays) {
    }

    private record GanttSummary(int totalTasks,
                                int parentTasks,
                                int subtasks,
                                int overdueTasks,
                                int highPriorityTasks) {
    }

    private enum GanttEmptyState {
        NO_TASKS,
        NO_DEADLINES,
        ARCHIVED_ONLY
    }

    private record TaskTimelineWindow(LocalDate startDate, LocalDate endDate, int durationDays) {
    }

    private record GanttRowData(Task task, int rowIndex, int indent) {

        private String displayTitle() {
            return task.getTitle();
        }
    }

    private record LayoutSignature(DensityConfig density, AdaptiveContext adaptiveContext) {
    }
}
