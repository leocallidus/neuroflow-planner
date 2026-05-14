package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.UiNavigationSurfacePolicyService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public final class CommandPaletteView extends VBox {
    private static final int DEFAULT_LIMIT = 18;

    private final CommandPaletteController controller;
    private final int baseLimit;
    private int effectiveLimit;

    private final TextField queryField = new TextField();
    private final ListView<CommandPaletteItem> resultsView = new ListView<>();
    private final Label statusLabel = new Label("Введите команду, задачу или заметку...");
    private final HBox toolbar = new HBox(8);
    private final Button closeButton = new Button();
    private final HBox hintBar = new HBox(6);
    private final Label modeHintChip = new Label("GUIDED");
    private final Label keyboardHintChip = new Label("Enter • Alt+S • Esc");
    private final Label policyHintChip = new Label("Palette");
    private final VBox emptyStateBox = new VBox(8);
    private final Label emptyStateTitleLabel = new Label("Что можно сделать");
    private final Label emptyStateBodyLabel = new Label("Палитра ищет команды, задачи и заметки.");
    private final FlowPane exampleQueriesPane = new FlowPane();
    private final HBox emptyActionsBar = new HBox(6);
    private final Button emptyActionFocusTaskBtn = new Button("Добавить задачу");
    private final Button emptyActionOpenSettingsBtn = new Button("Настройки");
    private final Region emptyActionsSpacer = new Region();
    private final Button dismissEmptyHelpBtn = new Button("Скрыть подсказку");

    private Runnable closeAction;
    private Function<String, Boolean> sidebarRevealHandler;
    private Consumer<CommandPaletteViewMode> paletteViewModeListener;
    private Consumer<String> helperHintDismissListener;
    private String initialQuery = "";
    private CommandPaletteDisplayPolicy displayPolicy;
    private CommandPaletteViewModel lastViewModel = new CommandPaletteViewModel("", List.of(), List.of(), List.of(), false, "", "");
    private boolean lastRefreshFailed;
    private final Map<String, CommandPaletteResultGroup> itemGroups = new HashMap<>();
    private final Set<String> sectionHeaderStarts = new HashSet<>();

    public CommandPaletteView(CommandPaletteController controller) {
        this(controller, DEFAULT_LIMIT);
    }

    public CommandPaletteView(CommandPaletteController controller, int limit) {
        this.controller = controller;
        this.baseLimit = Math.max(1, limit);
        this.effectiveLimit = this.baseLimit;

        getStyleClass().add("command-palette-root");
        setSpacing(10);
        setPadding(new Insets(14));
        setFillWidth(true);
        setPrefSize(720, 460);
        setMinHeight(0);

        buildToolbar();
        buildHintBar();
        buildEmptyState();
        buildResultsList();
        buildStatus();
        installFocusCycleHandlers();
        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Node target = event != null && event.getTarget() instanceof Node node ? node : null;
            handleFocusCycle(event, target);
        });

        getChildren().addAll(toolbar, hintBar, emptyStateBox, resultsView, statusLabel);
    }

    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    public void setSidebarRevealHandler(Function<String, Boolean> sidebarRevealHandler) {
        this.sidebarRevealHandler = sidebarRevealHandler;
    }

    public void setPaletteViewModeListener(Consumer<CommandPaletteViewMode> paletteViewModeListener) {
        this.paletteViewModeListener = paletteViewModeListener;
    }

    public void setHelperHintDismissListener(Consumer<String> helperHintDismissListener) {
        this.helperHintDismissListener = helperHintDismissListener;
    }

    public void setInitialQuery(String initialQuery) {
        this.initialQuery = initialQuery == null ? "" : initialQuery.trim();
    }

    public void activate() {
        if (!initialQuery.isBlank()) {
            queryField.setText(initialQuery);
            queryField.positionCaret(queryField.getText().length());
        }
        refreshResults();
        Platform.runLater(() -> {
            queryField.requestFocus();
            if (initialQuery.isBlank()) {
                queryField.selectAll();
            }
        });
        initialQuery = "";
    }

    public void applyAdaptiveMode(UiLayoutBreakpoint breakpoint, UiLayoutMode densityMode) {
        getStyleClass().removeAll(
            "layout-breakpoint-compact",
            "layout-breakpoint-normal",
            "layout-breakpoint-wide",
            "layout-density-compact",
            "layout-density-comfortable"
        );
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        UiLayoutMode safeDensity = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        getStyleClass().add(switch (safeBreakpoint) {
            case COMPACT -> "layout-breakpoint-compact";
            case NORMAL -> "layout-breakpoint-normal";
            case WIDE -> "layout-breakpoint-wide";
        });
        getStyleClass().add(safeDensity == UiLayoutMode.COMPACT
            ? "layout-density-compact"
            : "layout-density-comfortable");
    }

    public void applyDisplayPolicy(CommandPaletteDisplayPolicy policy) {
        displayPolicy = policy;
        if (policy == null) {
            effectiveLimit = baseLimit;
            setStyleClassPresent(this, "layout-height-low", false);
            setStyleClassPresent(this, "layout-height-very-low", false);
            setStyleClassPresent(this, "command-palette-guided", true);
            setStyleClassPresent(this, "command-palette-compact-rows", false);
            setStyleClassPresent(this, "command-palette-hide-descriptions", false);
            refreshResults();
            return;
        }
        effectiveLimit = Math.max(5, Math.min(baseLimit, policy.maxResults()));
        applyAdaptiveMode(policy.breakpoint(), policy.densityMode());
        setStyleClassPresent(this, "layout-height-low", policy.heightBand().isLowHeight() && !policy.heightBand().isVeryLowHeight());
        setStyleClassPresent(this, "layout-height-very-low", policy.heightBand().isVeryLowHeight());
        setStyleClassPresent(this, "command-palette-guided", policy.guidedLauncher());
        setStyleClassPresent(this, "command-palette-compact-rows", policy.compactRows());
        setStyleClassPresent(this, "command-palette-hide-descriptions", !policy.showDescriptions());
        refreshResults();
    }

    private void buildToolbar() {
        queryField.getStyleClass().add("command-palette-query");
        queryField.setPromptText("Например: добавить задачу, настройки, ИИ, архив");
        queryField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults());
        queryField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleQueryKeys);
        HBox.setHgrow(queryField, Priority.ALWAYS);

        closeButton.getStyleClass().add("command-palette-close-btn");
        closeButton.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 14));
        closeButton.setTooltip(new Tooltip("Закрыть (Esc, Ctrl/Cmd+K)"));
        closeButton.setOnAction(event -> close());
        closeButton.setFocusTraversable(true);

        toolbar.getStyleClass().addAll("command-palette-toolbar", "adaptive-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getChildren().addAll(queryField, closeButton);
    }

    private void buildHintBar() {
        hintBar.getStyleClass().add("command-palette-hintbar");
        hintBar.setAlignment(Pos.CENTER_LEFT);
        modeHintChip.getStyleClass().addAll("command-palette-hint-chip", "command-palette-hint-chip-mode");
        keyboardHintChip.getStyleClass().addAll("command-palette-hint-chip", "command-palette-hint-chip-kbd");
        policyHintChip.getStyleClass().addAll("command-palette-hint-chip", "command-palette-hint-chip-policy");
        hintBar.getChildren().addAll(modeHintChip, keyboardHintChip, policyHintChip);
    }

    private void buildEmptyState() {
        emptyStateBox.getStyleClass().add("command-palette-empty-state");
        emptyStateBox.setFillWidth(true);
        emptyStateBox.setPadding(new Insets(10));
        emptyStateBox.setManaged(false);
        emptyStateBox.setVisible(false);

        emptyStateTitleLabel.getStyleClass().add("command-palette-empty-title");
        emptyStateBodyLabel.getStyleClass().add("command-palette-empty-body");
        emptyStateBodyLabel.setWrapText(true);

        exampleQueriesPane.getStyleClass().add("command-palette-example-queries");
        exampleQueriesPane.setHgap(6);
        exampleQueriesPane.setVgap(6);

        emptyActionFocusTaskBtn.getStyleClass().addAll("command-palette-empty-action-btn", "primary");
        emptyActionFocusTaskBtn.setOnAction(e -> queryField.setText("добавить задачу"));
        emptyActionOpenSettingsBtn.getStyleClass().addAll("command-palette-empty-action-btn", "secondary");
        emptyActionOpenSettingsBtn.setOnAction(e -> queryField.setText("настройки"));
        HBox.setHgrow(emptyActionsSpacer, Priority.ALWAYS);

        dismissEmptyHelpBtn.getStyleClass().addAll("command-palette-empty-action-btn", "dismiss");
        dismissEmptyHelpBtn.setOnAction(e -> {
            if (helperHintDismissListener != null) {
                helperHintDismissListener.accept(UiNavigationSurfacePolicyService.HELPER_HINT_PALETTE_EMPTY_STATE);
            }
            // Keep current list visible; hide guided box immediately.
            emptyStateBox.setManaged(false);
            emptyStateBox.setVisible(false);
        });

        emptyActionsBar.getStyleClass().add("command-palette-empty-actions");
        emptyActionsBar.setAlignment(Pos.CENTER_LEFT);
        emptyActionsBar.getChildren().addAll(
            emptyActionFocusTaskBtn,
            emptyActionOpenSettingsBtn,
            emptyActionsSpacer,
            dismissEmptyHelpBtn
        );

        emptyStateBox.getChildren().addAll(
            emptyStateTitleLabel,
            emptyStateBodyLabel,
            exampleQueriesPane,
            emptyActionsBar
        );
    }

    private void buildResultsList() {
        resultsView.getStyleClass().add("command-palette-results");
        resultsView.setCellFactory(listView -> new CommandPaletteCell());
        resultsView.setOnMouseClicked(event -> {
            if (event.getClickCount() >= 2) {
                executeSelected();
            }
        });
        resultsView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleResultsViewKeys);
        resultsView.setMinHeight(0);
        VBox.setVgrow(resultsView, Priority.ALWAYS);
    }

    private void buildStatus() {
        statusLabel.getStyleClass().add("command-palette-status");
        statusLabel.setWrapText(true);
    }

    private void refreshResults() {
        CommandPaletteViewModel viewModel;
        try {
            viewModel = controller.buildViewModel(queryField.getText(), displayPolicy);
            lastRefreshFailed = false;
        } catch (RuntimeException ex) {
            lastRefreshFailed = true;
            viewModel = buildErrorFallbackViewModel(ex);
        }
        lastViewModel = viewModel;
        applyGroupingMetadata(viewModel);
        resultsView.getItems().setAll(viewModel.flatItems());
        if (!viewModel.flatItems().isEmpty()) {
            resultsView.getSelectionModel().select(0);
        } else {
            resultsView.getSelectionModel().clearSelection();
        }
        refreshEmptyState(viewModel);
        refreshHintBar(viewModel);
        refreshStatus(viewModel);
        notifyModeMemory(viewModel);
        setStyleClassPresent(statusLabel, "command-palette-status-error", lastRefreshFailed);
        setStyleClassPresent(emptyStateBox, "error", lastRefreshFailed);
    }

    private CommandPaletteViewModel buildErrorFallbackViewModel(RuntimeException ex) {
        String message = ex == null ? "Не удалось обновить результаты." : ex.getClass().getSimpleName();
        return new CommandPaletteViewModel(
            queryField.getText(),
            List.of(),
            List.of(),
            List.of("настройки", "добавить задачу", "архив"),
            true,
            "Палитра временно недоступна",
            "Ошибка загрузки результатов: " + message + ". Попробуйте изменить запрос или открыть действие через sidebar."
        );
    }

    private void applyGroupingMetadata(CommandPaletteViewModel viewModel) {
        itemGroups.clear();
        sectionHeaderStarts.clear();
        if (viewModel == null || viewModel.sections() == null) {
            return;
        }
        for (CommandPaletteResultSection section : viewModel.sections()) {
            if (section == null || section.items() == null || section.items().isEmpty()) {
                continue;
            }
            for (int i = 0; i < section.items().size(); i++) {
                CommandPaletteItem item = section.items().get(i);
                if (item == null || item.key() == null) {
                    continue;
                }
                itemGroups.put(item.key(), section.group());
                if (i == 0) {
                    sectionHeaderStarts.add(item.key());
                }
            }
        }
    }

    private void refreshEmptyState(CommandPaletteViewModel viewModel) {
        boolean showEmpty = viewModel != null && !viewModel.hasResults() && viewModel.showGuidedEmptyState();
        emptyStateTitleLabel.setText(viewModel == null ? "" : viewModel.emptyTitle());
        emptyStateBodyLabel.setText(viewModel == null ? "" : viewModel.emptyBody());
        exampleQueriesPane.getChildren().clear();
        if (viewModel != null) {
            for (String example : viewModel.exampleQueries()) {
                if (example == null || example.isBlank()) {
                    continue;
                }
                exampleQueriesPane.getChildren().add(createExampleQueryChip(example));
            }
        }
        emptyStateBox.setManaged(showEmpty);
        emptyStateBox.setVisible(showEmpty);
        if (displayPolicy != null) {
            setStyleClassPresent(emptyStateBox, "compact", displayPolicy.compactRows());
            setStyleClassPresent(emptyStateBox, "very-low", displayPolicy.heightBand().isVeryLowHeight());
        }
        boolean canDismiss = displayPolicy == null || displayPolicy.showGuidedEmptyState();
        dismissEmptyHelpBtn.setManaged(canDismiss);
        dismissEmptyHelpBtn.setVisible(canDismiss);
    }

    private Node createExampleQueryChip(String example) {
        Button chip = new Button(example);
        chip.getStyleClass().add("command-palette-example-chip");
        chip.setFocusTraversable(false);
        chip.setOnAction(e -> {
            queryField.setText(example);
            queryField.positionCaret(example.length());
        });
        return chip;
    }

    private void refreshHintBar(CommandPaletteViewModel viewModel) {
        boolean blankQuery = viewModel == null || viewModel.query().isBlank();
        String modeText;
        if (blankQuery) {
            modeText = "GUIDED";
        } else if (viewModel != null && viewModel.sections().stream().anyMatch(s -> s.group() == CommandPaletteResultGroup.RECENT)) {
            modeText = "SEARCH + RECENT";
        } else {
            modeText = "SEARCH";
        }
        modeHintChip.setText(modeText);
        keyboardHintChip.setText(sidebarRevealHandler == null
            ? "Enter • Esc"
            : "Enter • Alt+S • Esc");

        if (displayPolicy == null) {
            policyHintChip.setText("Adaptive • default");
        } else {
            String height = switch (displayPolicy.heightBand()) {
                case TALL -> "Tall";
                case LOW_HEIGHT -> "Low";
                case VERY_LOW_HEIGHT -> "Very low";
            };
            policyHintChip.setText(height + " • " + displayPolicy.maxResults() + " results");
        }
    }

    private void refreshStatus(CommandPaletteViewModel viewModel) {
        if (viewModel == null) {
            statusLabel.setText("Введите команду, задачу или заметку...");
            return;
        }
        if (lastRefreshFailed) {
            statusLabel.setText("Ошибка обновления палитры. Можно продолжить через sidebar или повторить запрос.");
            return;
        }
        if (!viewModel.hasResults()) {
            statusLabel.setText(viewModel.showGuidedEmptyState()
                ? "Пустое состояние показывает примеры запросов и основные сценарии."
                : "Ничего не найдено. Попробуйте короче: например, \"архив\" или \"настройки\".");
            return;
        }
        int total = viewModel.flatItems().size();
        int actionCount = 0;
        int entityCount = 0;
        for (CommandPaletteItem item : viewModel.flatItems()) {
            if (item.type() == CommandPaletteItemType.ACTION) {
                actionCount++;
            } else {
                entityCount++;
            }
        }
        String bridgeHint = sidebarRevealHandler == null ? "" : " • Alt+S показать в sidebar";
        statusLabel.setText(
            "Результатов: " + total + " • Actions: " + actionCount + " • Entities: " + entityCount
                + " • Enter выполнить" + bridgeHint
        );
    }

    private void notifyModeMemory(CommandPaletteViewModel viewModel) {
        if (paletteViewModeListener == null || viewModel == null) {
            return;
        }
        CommandPaletteViewMode mode;
        if (viewModel.query().isBlank()) {
            boolean hasRecent = viewModel.sections().stream().anyMatch(section -> section.group() == CommandPaletteResultGroup.RECENT);
            boolean hasSuggested = viewModel.sections().stream().anyMatch(section -> section.group() == CommandPaletteResultGroup.SUGGESTED);
            if (hasRecent) {
                mode = CommandPaletteViewMode.RECENT;
            } else if (hasSuggested) {
                mode = CommandPaletteViewMode.CONTEXT;
            } else {
                mode = CommandPaletteViewMode.GUIDED;
            }
        } else {
            mode = CommandPaletteViewMode.SEARCH;
        }
        paletteViewModeListener.accept(mode);
    }

    private void handleQueryKeys(KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        if (handleFocusCycle(event, queryField)) {
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            executeSelected();
            return;
        }
        if (event.getCode() == KeyCode.S && event.isAltDown()) {
            event.consume();
            revealSelectedInSidebar();
            return;
        }
        if (event.getCode() == KeyCode.DOWN) {
            event.consume();
            resultsView.requestFocus();
            if (resultsView.getItems().isEmpty()) {
                return;
            }
            int current = Math.max(0, resultsView.getSelectionModel().getSelectedIndex());
            int next = Math.min(resultsView.getItems().size() - 1, current + 1);
            resultsView.getSelectionModel().select(next);
            resultsView.scrollTo(next);
        }
    }

    private void handleResultsViewKeys(KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        if (handleFocusCycle(event, resultsView)) {
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            executeSelected();
            return;
        }
        if (event.getCode() == KeyCode.S && event.isAltDown()) {
            event.consume();
            revealSelectedInSidebar();
        }
    }

    private void installFocusCycleHandlers() {
        closeButton.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleFocusCycle(event, closeButton));
        emptyActionFocusTaskBtn.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleFocusCycle(event, emptyActionFocusTaskBtn));
        emptyActionOpenSettingsBtn.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleFocusCycle(event, emptyActionOpenSettingsBtn));
        dismissEmptyHelpBtn.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleFocusCycle(event, dismissEmptyHelpBtn));
    }

    private boolean handleFocusCycle(KeyEvent event, Node source) {
        if (event == null || event.isConsumed() || event.getCode() != KeyCode.TAB) {
            return false;
        }
        List<Node> targets = buildFocusCycleTargets();
        if (targets.size() < 2) {
            return false;
        }
        int index = findFocusTargetIndex(targets, source);
        if (index < 0 && getScene() != null) {
            index = findFocusTargetIndex(targets, getScene().getFocusOwner());
        }
        if (index < 0) {
            index = 0;
        }
        int nextIndex = event.isShiftDown()
            ? (index - 1 + targets.size()) % targets.size()
            : (index + 1) % targets.size();
        Node next = targets.get(nextIndex);
        if (next != null) {
            event.consume();
            Platform.runLater(next::requestFocus);
            return true;
        }
        return false;
    }

    private int findFocusTargetIndex(List<Node> targets, Node source) {
        if (targets == null || targets.isEmpty() || source == null) {
            return -1;
        }
        Node cursor = source;
        while (cursor != null) {
            int index = targets.indexOf(cursor);
            if (index >= 0) {
                return index;
            }
            cursor = cursor.getParent();
        }
        return -1;
    }

    private List<Node> buildFocusCycleTargets() {
        List<Node> out = new ArrayList<>(6);
        addFocusTarget(out, queryField);
        if (!resultsView.getItems().isEmpty()) {
            addFocusTarget(out, resultsView);
        }
        addFocusTarget(out, emptyActionFocusTaskBtn);
        addFocusTarget(out, emptyActionOpenSettingsBtn);
        addFocusTarget(out, dismissEmptyHelpBtn);
        if (resultsView.getItems().isEmpty()) {
            addFocusTarget(out, resultsView);
        }
        addFocusTarget(out, closeButton);
        return out;
    }

    private void addFocusTarget(List<Node> out, Node node) {
        if (node == null || out.contains(node)) {
            return;
        }
        if (!node.isVisible() || !node.isManaged() || node.isDisable()) {
            return;
        }
        out.add(node);
    }

    private void executeSelected() {
        CommandPaletteItem selected = resultsView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите команду из списка.");
            return;
        }
        int rank = Math.max(1, resultsView.getSelectionModel().getSelectedIndex() + 1);
        CommandPaletteController.ExecutionResult result = controller.execute(selected, queryField.getText(), rank);
        if (result.successful()) {
            close();
            return;
        }
        String message = result.message() == null || result.message().isBlank()
            ? "Не удалось выполнить команду."
            : result.message();
        statusLabel.setText(message);
    }

    private void revealSelectedInSidebar() {
        CommandPaletteItem selected = resultsView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите команду из списка.");
            return;
        }
        if (selected.type() != CommandPaletteItemType.ACTION) {
            statusLabel.setText("Показ в панели доступен только для команд.");
            return;
        }
        CommandPaletteController.ExecutionResult result = controller.revealInSidebar(selected);
        if (result.successful()) {
            close();
            return;
        }
        statusLabel.setText(result.message());
    }

    private void close() {
        if (closeAction != null) {
            closeAction.run();
        }
    }

    private boolean setStyleClassPresent(Node node, String styleClass, boolean present) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return false;
        }
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
                return true;
            }
            return false;
        }
        return node.getStyleClass().remove(styleClass);
    }

    private final class CommandPaletteCell extends ListCell<CommandPaletteItem> {
        private final Label sectionHeaderLabel = new Label();
        private final Label badgeLabel = new Label();
        private final Label titleLabel = new Label();
        private final Label metaLabel = new Label();
        private final VBox textBox = new VBox(2, titleLabel, metaLabel);
        private final Region spacer = new Region();
        private final Label shortcutLabel = new Label();
        private final HBox rowRoot = new HBox(8, badgeLabel, textBox, spacer, shortcutLabel);
        private final VBox cellRoot = new VBox(4, sectionHeaderLabel, rowRoot);
        private final MenuItem revealInSidebarItem = new MenuItem("Показать в панели (Alt+S)");
        private final ContextMenu contextMenu = new ContextMenu(revealInSidebarItem);
        private Tooltip rowTooltip;

        private CommandPaletteCell() {
            cellRoot.getStyleClass().add("command-palette-cell");
            cellRoot.setMinWidth(0);
            sectionHeaderLabel.getStyleClass().add("command-palette-section-header");
            rowRoot.getStyleClass().add("command-palette-row");
            rowRoot.setMinWidth(0);
            rowRoot.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textBox, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            textBox.setMinWidth(0);

            badgeLabel.getStyleClass().add("command-palette-badge");
            titleLabel.getStyleClass().add("command-palette-title");
            titleLabel.setMinWidth(0);
            titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            metaLabel.getStyleClass().add("command-palette-meta");
            metaLabel.setMinWidth(0);
            metaLabel.setWrapText(false);
            metaLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
            shortcutLabel.getStyleClass().add("command-palette-shortcut");
            shortcutLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

            revealInSidebarItem.setOnAction(event -> {
                CommandPaletteItem item = getItem();
                if (item == null) {
                    return;
                }
                resultsView.getSelectionModel().select(item);
                revealSelectedInSidebar();
            });
        }

        @Override
        protected void updateItem(CommandPaletteItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }

            refreshSectionHeader(item);
            refreshBadge(item);
            titleLabel.setText(item.displayTitle());

            boolean showDescriptions = displayPolicy == null || displayPolicy.showDescriptions();
            String meta = buildRowMeta(item);
            metaLabel.setText(meta);
            metaLabel.setManaged(showDescriptions && !meta.isBlank());
            metaLabel.setVisible(showDescriptions && !meta.isBlank());

            shortcutLabel.setText(item.shortcutHint());
            boolean showShortcut = item.type() == CommandPaletteItemType.ACTION && !item.shortcutHint().isBlank();
            shortcutLabel.setManaged(showShortcut);
            shortcutLabel.setVisible(showShortcut);
            boolean canRevealInSidebar = item.type() == CommandPaletteItemType.ACTION && sidebarRevealHandler != null;
            String revealTargetHint = canRevealInSidebar
                ? controller.sidebarRevealTargetHint(item.commandId())
                : "rail/context sidebar";
            revealInSidebarItem.setText("Показать в " + revealTargetHint + " (Alt+S)");
            revealInSidebarItem.setDisable(!canRevealInSidebar);
            setContextMenu(canRevealInSidebar ? contextMenu : null);
            applyRowTooltip(item, canRevealInSidebar, revealTargetHint);

            setStyleClassPresent(rowRoot, "recent", item.recent());
            setStyleClassPresent(rowRoot, "unavailable", !item.available());
            setGraphic(cellRoot);
        }

        private void refreshSectionHeader(CommandPaletteItem item) {
            CommandPaletteResultGroup group = itemGroups.get(item.key());
            boolean showHeader = group != null && sectionHeaderStarts.contains(item.key());
            sectionHeaderLabel.setManaged(showHeader);
            sectionHeaderLabel.setVisible(showHeader);
            if (!showHeader) {
                sectionHeaderLabel.setText("");
                return;
            }
            sectionHeaderLabel.setText(group.title());
            setStyleClassPresent(sectionHeaderLabel, "group-recent", group == CommandPaletteResultGroup.RECENT);
            setStyleClassPresent(sectionHeaderLabel, "group-suggested", group == CommandPaletteResultGroup.SUGGESTED);
            setStyleClassPresent(sectionHeaderLabel, "group-actions", group == CommandPaletteResultGroup.ACTIONS);
            setStyleClassPresent(sectionHeaderLabel, "group-entities", group == CommandPaletteResultGroup.ENTITIES);
        }

        private void refreshBadge(CommandPaletteItem item) {
            badgeLabel.getStyleClass().removeAll(
                "command-palette-badge-action",
                "command-palette-badge-task",
                "command-palette-badge-note"
            );
            String badgeText = switch (item.type()) {
                case ACTION -> "CMD";
                case TASK -> "TASK";
                case NOTE -> "NOTE";
            };
            switch (item.type()) {
                case ACTION -> badgeLabel.getStyleClass().add("command-palette-badge-action");
                case TASK -> badgeLabel.getStyleClass().add("command-palette-badge-task");
                case NOTE -> badgeLabel.getStyleClass().add("command-palette-badge-note");
            }
            if (item.recent()) {
                badgeText = "REC";
            }
            badgeLabel.setText(badgeText);
        }

        private String buildRowMeta(CommandPaletteItem item) {
            String meta = item.subtitle();
            if (!item.available() && !item.unavailableReason().isBlank()) {
                meta = (meta == null || meta.isBlank())
                    ? item.unavailableReason()
                    : meta + " • " + item.unavailableReason();
            }
            return meta == null ? "" : meta;
        }

        private void applyRowTooltip(CommandPaletteItem item, boolean canRevealInSidebar, String revealTargetHint) {
            if (item == null) {
                if (rowTooltip != null) {
                    Tooltip.uninstall(rowRoot, rowTooltip);
                    Tooltip.uninstall(cellRoot, rowTooltip);
                }
                return;
            }
            StringBuilder text = new StringBuilder();
            if (!item.displayTitle().isBlank()) {
                text.append(item.displayTitle());
            }
            String meta = buildRowMeta(item);
            if (!meta.isBlank()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(meta);
            }
            if (item.type() == CommandPaletteItemType.ACTION && !item.shortcutHint().isBlank()) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("Shortcut: ").append(item.shortcutHint());
            }
            if (item.type() == CommandPaletteItemType.ACTION) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append("Статус: ").append(item.available() ? "доступно" : "недоступно");
                if (!item.available() && !item.unavailableReason().isBlank()) {
                    text.append(" (").append(item.unavailableReason()).append(')');
                }
                if (canRevealInSidebar) {
                    text.append('\n').append("Bridge: Alt+S показать в ")
                        .append(revealTargetHint == null || revealTargetHint.isBlank() ? "rail/context sidebar" : revealTargetHint);
                }
            }
            if (text.length() == 0) {
                if (rowTooltip != null) {
                    Tooltip.uninstall(rowRoot, rowTooltip);
                    Tooltip.uninstall(cellRoot, rowTooltip);
                }
                return;
            }
            if (rowTooltip == null) {
                rowTooltip = new Tooltip(text.toString());
                Tooltip.install(rowRoot, rowTooltip);
                Tooltip.install(cellRoot, rowTooltip);
            } else {
                rowTooltip.setText(text.toString());
            }
        }
    }
}
