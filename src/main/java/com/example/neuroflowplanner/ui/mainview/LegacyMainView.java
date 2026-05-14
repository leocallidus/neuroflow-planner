package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.*;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.CriticalPathTaskMetrics;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewContentFormatter;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewResult;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockContentFormatter;
import com.example.neuroflowplanner.service.focusblocks.FocusBlockRecommendationResult;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityContentFormatter;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityResult;
import com.example.neuroflowplanner.service.task.DefaultTaskImportService;
import com.example.neuroflowplanner.service.task.TaskImportService;
import com.example.neuroflowplanner.sync.SyncClientFacade;
import com.example.neuroflowplanner.sync.SyncUiSnapshot;
import com.example.neuroflowplanner.sync.SyncUiStatus;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteController;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteDialog;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteHistory;
import com.example.neuroflowplanner.ui.commandpalette.OverlayDialogManager;
import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.ui.layout.AdaptiveLayoutService;
import com.example.neuroflowplanner.ui.layout.MainLayoutCoordinator;
import com.example.neuroflowplanner.ui.layout.MainLayoutShell;
import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.UiLayoutState;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.ContextSidebarDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelSidebarMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavigationRailSection;
import com.example.neuroflowplanner.ui.layout.leftpanel.TwoTierSidebarDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorState;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorTab;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelLayoutService;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelSectionPriority;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTabContentPolicy;
import com.example.neuroflowplanner.ui.navigation.SidebarNavItem;
import com.example.neuroflowplanner.ui.navigation.SidebarNavSection;
import com.example.neuroflowplanner.ui.navigation.SidebarNavState;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;
import com.example.neuroflowplanner.ui.navigation.SidebarNavigationService;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.LinkParser;
import com.example.neuroflowplanner.util.TaskScheduleFormatter;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.TextAlignment;
import javafx.application.Platform;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.io.File;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.stage.FileChooser;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import org.kordamp.ikonli.Ikon;

public class LegacyMainView extends BorderPane {
    private enum TaskListViewMode {
        ALL,
        SCHEDULED,
        ARCHIVED,
        URGENT,
        TAG_FILTER
    }

    private static final String MAIN_ACTION_OPEN_PALETTE = "main.system.commandPalette";
    private static final String MAIN_ACTION_FOCUS_GLOBAL_SEARCH = "main.system.globalSearchFocus";
    private static final String MAIN_ACTION_TOGGLE_INLINE_OVERLAY = "main.system.inlineOverlayToggle";
    private static final String MAIN_ACTION_UNDO = "main.history.undo";
    private static final String MAIN_ACTION_REDO = "main.history.redo";
    private static final String MAIN_ACTION_SHORTCUTS_HELP = "main.system.shortcutsHelp";
    private static final int SIDEBAR_FILTER_MAX_RESULTS = 24;
    private static final String SIDEBAR_FAVORITES_TITLE = "ИЗБРАННОЕ";
    private static final String SIDEBAR_RECENT_TITLE = "НЕДАВНИЕ";
    private static final String SIDEBAR_GUIDED_HINT_NOVICE_ID = "sidebar.guided.novice";
    private static final String SIDEBAR_GROUP_WORK = "group.workflows";
    private static final String SIDEBAR_GROUP_INSIGHTS = "group.insights";
    private static final String SIDEBAR_GROUP_SYSTEM = "group.system";
    private static final String SIDEBAR_BUTTON_ACTION_ID = "sidebar.actionId";
    private static final String SIDEBAR_BUTTON_BASE_LABEL = "sidebar.baseLabel";
    private static final double SIDEBAR_ICON_RAIL_WIDTH = 56.0;
    private static final double TWO_TIER_CONTEXT_MIN_WIDTH = 220.0;
    private static final double RIGHT_PANEL_COLLAPSED_WIDTH = 56.0;
    private static final String SHELL_CLASS_ADAPTIVE = "adaptive-shell-root";
    private static final String SHELL_CLASS_BREAKPOINT_COMPACT = "layout-breakpoint-compact";
    private static final String SHELL_CLASS_BREAKPOINT_NORMAL = "layout-breakpoint-normal";
    private static final String SHELL_CLASS_BREAKPOINT_WIDE = "layout-breakpoint-wide";
    private static final String SHELL_CLASS_DENSITY_COMPACT = "layout-density-compact";
    private static final String SHELL_CLASS_DENSITY_COMFORTABLE = "layout-density-comfortable";
    private static final String SHELL_ZONE_COLLAPSED = "shell-zone-collapsed";
    private static final String SHELL_CLASS_LEFT_PANEL_PINNED = "left-panel-mode-pinned";
    private static final String SHELL_CLASS_LEFT_PANEL_COLLAPSIBLE = "left-panel-mode-collapsible";
    private static final String SHELL_CLASS_LEFT_PANEL_OVERLAY = "left-panel-mode-overlay";
    private static final String SHELL_CLASS_COMMAND_PALETTE_OPEN = "command-palette-overlay-open";
    private static final String SHELL_CLASS_RIGHT_PANEL_PINNED = "right-panel-mode-pinned";
    private static final String SHELL_CLASS_RIGHT_PANEL_COLLAPSIBLE = "right-panel-mode-collapsible";
    private static final String SHELL_CLASS_RIGHT_PANEL_OVERLAY = "right-panel-mode-overlay";
    private static final String SHELL_CLASS_RIGHT_PANEL_OVERLAY_OPEN = "right-panel-overlay-open";
    private static final String TASK_TABLE_CLASS_COMPACT = "task-table-compact";
    private static final String TASK_TABLE_CLASS_NARROW = "task-table-narrow";
    private static final double TASK_TABLE_NARROW_WIDTH_THRESHOLD = 860.0;
    private static final double RIGHT_PANEL_OVERLAY_MIN_WIDTH = 260.0;
    private static final double RIGHT_PANEL_COLLAPSIBLE_MIN_WIDTH = 240.0;
    private static final double RIGHT_PANEL_PINNED_MIN_WIDTH = 260.0;
    private static final double INLINE_OVERLAY_WIDTH_COMPACT_THRESHOLD = 1380.0;
    private static final double INLINE_OVERLAY_WIDTH_VERY_COMPACT_THRESHOLD = 1320.0;
    private static final double INLINE_OVERLAY_HEIGHT_LOW_THRESHOLD = 800.0;
    private static final double INLINE_OVERLAY_HEIGHT_VERY_LOW_THRESHOLD = 700.0;
    private static final double INLINE_TAB_MAX_WIDTH_DEFAULT = 220.0;
    private static final double INLINE_TAB_MAX_WIDTH_COMPACT = 176.0;
    private static final double INLINE_TAB_MAX_WIDTH_VERY_COMPACT = 148.0;
    private static final List<String> INSPECTOR_TAB_PROPERTIES_SECTION_IDS = List.of(
        RightPanelLayoutService.SECTION_DETAILS
    );
    private static final List<String> INSPECTOR_TAB_DESCRIPTION_SECTION_IDS = List.of(
        RightPanelLayoutService.SECTION_DESCRIPTION
    );
    private static final List<String> INSPECTOR_TAB_ANALYTICS_SECTION_IDS = List.of(
        RightPanelLayoutService.SECTION_AI,
        RightPanelLayoutService.SECTION_PATH
    );

    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final TreeTableView<Task> taskTable = new TreeTableView<>();
    private final TreeItem<Task> rootItem = new TreeItem<>();
    private TaskListViewMode currentTaskListViewMode = TaskListViewMode.ALL;
    private String currentTaskTagFilter = "";
    private TreeTableColumn<Task, String> taskTitleColumn;
    private TreeTableColumn<Task, String> taskTagsColumn;
    private TreeTableColumn<Task, LocalDateTime> taskDeadlineColumn;
    private TreeTableColumn<Task, Number> taskComplexityColumn;
    private TreeTableColumn<Task, Number> taskPriorityColumn;
    private TreeTableColumn<Task, Void> taskActionsColumn;
    private boolean taskTableSecondaryColumnsCollapsed;
    private boolean taskTableCompactModeApplied;
    private WebView aiInsightWebView;
    private String currentInsightText = "";
    private final Label aiInsightSummaryLabel = new Label("Выберите задачу для краткого summary...");
    private Button aiInsightCompactExpandBtn;
    private VBox aiInsightSummaryBox;
    private VBox aiInsightFullContentBox;
    private boolean aiInsightCompactExpanded;
    private boolean aiInsightErrorState;
    private final Label detailTitle = new Label("Выберите задачу");
    private final Label detailQuickFactsLabel = new Label("Дедлайн: —  |  Приоритет: —  |  Сложность: —");
    private final Label detailStatusSummaryLabel = new Label("Выберите задачу для просмотра контекста");
    private final Label descriptionSummaryLabel = new Label("Нет описания");
    private Button descriptionCompactExpandBtn;
    private VBox descriptionSummaryBox;
    private VBox descriptionFullContentBox;
    private boolean descriptionCompactExpanded;
    private WebView descriptionWebView;
    private final Label detailDeadline = new Label("-");
    private final Label detailComplexity = new Label("-");
    private final Label detailPriority = new Label("-");
    private final Label detailTags = new Label("-");
    private final Label detailRecurrence = new Label("-");
    private final Label detailDependsOn = new Label("-");
    private final Label detailDependents = new Label("-");
    private final Label detailStartDate = new Label("-");
    private Label taskTableEmptyTitleLabel;
    private Label taskTableEmptyDescriptionLabel;
    private Button taskTableEmptyActionButton;
    private Button taskPanelAddTaskButton;
    private final FlowPane detailLinkedNotes = new FlowPane();
    private final Label undoRedoStateLabel = new Label("Undo/Redo: недоступно");
    private Button undoActionButton;
    private Button redoActionButton;
    private final Label criticalPathScopeLabel = new Label("-");
    private final Label criticalPathSummaryLabel = new Label("-");
    private final Label criticalPathCompactSummaryLabel = new Label("Критический путь: нет данных");
    private final AutoCloseable syncStatusListenerRegistration;
    private final Label criticalPathSelectedTaskLabel = new Label("Выберите задачу для просмотра метрик");
    private final FlowPane criticalPathChainPane = new FlowPane();
    private VBox criticalPathExtendedMetricsBox;
    private Button criticalPathDetailsToggleBtn;
    private boolean criticalPathCompactDetailsExpanded;
    private boolean criticalPathExtendedMetricsDirty = true;
    private final Map<String, Task> taskIndexById = new HashMap<>();
    private final Map<String, List<String>> blockersByTaskId = new HashMap<>();
    private final Map<String, List<String>> dependentsByTaskId = new HashMap<>();
    private final Map<String, CriticalPathTaskMetrics> criticalMetricsByTaskId = new HashMap<>();
    private final Set<String> criticalTaskIds = new LinkedHashSet<>();
    private final Set<String> criticalChainTaskIds = new LinkedHashSet<>();
    private CriticalPathResult criticalPathResult = CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null);
    private final NotesService notesService = NotesService.getInstance();
    private MainViewPresenter presenter;
    private final UiActionRegistry commandActionRegistry = UiActionRegistry.withConfigDefaults();
    private final ShortcutRegistry shortcutRegistry = ShortcutRegistry.withConfigDefaults();
    private final CommandPaletteController commandPaletteController = new CommandPaletteController(
        "mainview",
        commandActionRegistry,
        (query, limit) -> presenter == null ? List.of() : presenter.searchGlobal(query, limit),
        result -> presenter != null && presenter.openGlobalSearchResult(result),
        this::showActionInSidebar,
        this::resolveSidebarShortcutHint,
        new CommandPaletteHistory()
    );
    private final CommandPaletteDialog commandPaletteDialog = new CommandPaletteDialog(
        "Командная палитра",
        commandPaletteController
    );
    private final OverlayDialogManager overlayDialogManager = new OverlayDialogManager("mainview");
    private final SidebarNavigationService sidebarNavigationService = new SidebarNavigationService();
    private final AdaptiveLayoutService adaptiveLayoutService = new AdaptiveLayoutService();
    private final RightPanelLayoutService rightPanelLayoutService = new RightPanelLayoutService();
    private final MainLayoutCoordinator mainLayoutCoordinator =
        new MainLayoutCoordinator(adaptiveLayoutService, rightPanelLayoutService);
    private final MainLayoutShell mainLayoutShell = new MainLayoutShell();
    private boolean commandPaletteActionsRegistered;
    private TaskImportService taskImportService;
    private final StackPane overlayHost = new StackPane();
    private final StackPane overlayContentHolder = new StackPane();
    private final StackPane overlayScrim = new StackPane();
    private final VBox overlayContainer = new VBox();
    private final Label overlayTitle = new Label();
    private final ScrollPane overlayTabStripScroll = new ScrollPane();
    private final HBox overlayTabStrip = new HBox(6);
    private final MenuButton overlayTabMenuButton = new MenuButton();
    private final HBox inlineTaskDockTabStrip = new HBox(6);
    private final ScrollPane inlineTaskDockTabStripScroll = new ScrollPane();
    private Button overlayCloseButton;
    private Button inlineTaskDockToggleButton;
    private final LinkedHashMap<String, InlineOverlayTab> inlineOverlayTabs = new LinkedHashMap<>();
    private final LinkedHashSet<String> pendingInlineOverlayRestoreTabIds = new LinkedHashSet<>();
    private String activeInlineTabId;
    private String persistedInlineOverlayActiveTabId = "";
    private Node previousFocusOwner;
    private EventHandler<KeyEvent> overlayEscapeHandler;
    private boolean inlineOverlayRestoreInitialized;
    private boolean inlineOverlayRestoreCompleted;
    private boolean inlineOverlayRestoreDataReady;
    private boolean shortcutsRegistered;
    private UiLayoutState adaptiveLayoutState = UiLayoutState.defaults();
    private Scene adaptiveObservedScene;
    private boolean adaptiveHeightRefreshQueued;
    private final ChangeListener<Number> adaptiveSceneWidthListener =
        (obs, oldWidth, newWidth) -> applyAdaptiveLayoutForWidth(newWidth == null ? 0.0 : newWidth.doubleValue());
    private final ChangeListener<Number> adaptiveSceneHeightListener =
        (obs, oldHeight, newHeight) -> scheduleAdaptiveHeightRefresh();

    private String currentDescriptionText = "";
    private boolean autoPriorityInProgress = false;

    public LegacyMainView() {
        syncLayoutStateFromCoordinator();
        isSidebarCollapsed = adaptiveLayoutState.leftPanelCollapsed();
        isRightPanelCollapsed = adaptiveLayoutState.rightPanelCollapsed();

        registerCommandPaletteActions();
        mainLayoutShell.setLeftRail(createSidebar());
        mainLayoutShell.setCenterWorkspace(createCenterPanel());
        mainLayoutShell.setRightContextDrawer(createRightPanel());
        setCenter(mainLayoutShell.root());
        getStyleClass().add(SHELL_CLASS_ADAPTIVE);
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcutKeyPressed);
        commandActionRegistry.addExecutionListener(this::handleUiActionExecuted);
        registerShortcutBindings();
        commandPaletteDialog.setSidebarRevealHandler(this::showActionInSidebar);
        commandPaletteController.setSidebarRevealTargetHintResolver(this::resolveTwoTierSidebarRevealTargetHint);
        commandPaletteDialog.setCloseOnFocusLoss(true);
        commandPaletteDialog.setPaletteViewModeListener(mode -> {
            if (mode == null) {
                return;
            }
            mainLayoutCoordinator.setCommandPaletteViewMode(mode);
        });
        commandPaletteDialog.setHelperHintDismissListener(hintId -> {
            if (hintId == null || hintId.isBlank()) {
                return;
            }
            mainLayoutCoordinator.setNavHelperHintDismissed(hintId, true);
            if (Platform.isFxApplicationThread()) {
                applyAdaptiveLayoutStateToShell(true);
            } else {
                Platform.runLater(() -> applyAdaptiveLayoutStateToShell(true));
            }
        });
        commandPaletteDialog.setOpenStateListener(open -> {
            mainLayoutCoordinator.setCommandPaletteOverlayOpen(open);
            if (Platform.isFxApplicationThread()) {
                applyAdaptiveLayoutStateToShell(false);
            } else {
                Platform.runLater(() -> applyAdaptiveLayoutStateToShell(false));
            }
        });
        registerOverlayDialogs();
        refreshSidebarButtonsPresentation();
        initializeAdaptiveShell();
        syncStatusListenerRegistration = SyncClientFacade.getInstance().addListener(this::handleSyncSnapshotChanged);
        
        // Регистрируем callback для обновления WebView при смене темы
        SettingsDialog.setThemeChangeCallback(this::refreshWebViewsTheme);
        
        // Show welcome dialog on first launch
        Platform.runLater(WelcomeDialog::showIfFirstLaunch);
    }

    public void setPresenter(MainViewPresenter presenter) {
        this.presenter = presenter;
        this.taskImportService = presenter == null
            ? null
            : new DefaultTaskImportService(
                presenter.getServices().taskApplicationService(),
                presenter.getServices().taskAnalysisService()
            );
        refreshSidebarButtonsPresentation();
        restoreInlineOverlayTabsFromConfigIfReady();
    }

    public void applyState(MainViewState state) {
        if (state == null) {
            return;
        }
        tasks.setAll(state.tasks());
        if (state.criticalPathResult() != null) {
            applyCriticalPathResult(state.criticalPathResult());
        }
        updateUndoRedoControls(state);
        refreshSidebarButtonsPresentation();
        refreshTree();
        inlineOverlayRestoreDataReady = true;
        restoreInlineOverlayTabsFromConfigIfReady();
    }

    private void updateUndoRedoControls(MainViewState state) {
        if (state == null) {
            return;
        }
        String undoLabel = (state.nextUndoLabel() == null || state.nextUndoLabel().isBlank())
            ? "—"
            : state.nextUndoLabel();
        String redoLabel = (state.nextRedoLabel() == null || state.nextRedoLabel().isBlank())
            ? "—"
            : state.nextRedoLabel();

        if (undoActionButton != null) {
            undoActionButton.setDisable(!state.undoAvailable());
            undoActionButton.setTooltip(new Tooltip("Отменить: " + undoLabel));
        }
        if (redoActionButton != null) {
            redoActionButton.setDisable(!state.redoAvailable());
            redoActionButton.setTooltip(new Tooltip("Повторить: " + redoLabel));
        }
        undoRedoStateLabel.setText("Undo: " + undoLabel + "\nRedo: " + redoLabel);
    }

    private void handleUndoAction() {
        if (presenter == null) {
            return;
        }
        try {
            UndoRedoManager.CommandResult result = presenter.undoLastAction();
            if (!result.successful()) {
                showAlert("Undo недоступен: " + result.message());
            }
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка undo",
                ex,
                ErrorCode.DB_QUERY_FAILED,
                "Не удалось отменить действие.",
                false,
                "operation", "undoAction"
            );
        }
    }

    private void handleRedoAction() {
        if (presenter == null) {
            return;
        }
        try {
            UndoRedoManager.CommandResult result = presenter.redoLastAction();
            if (!result.successful()) {
                showAlert("Redo недоступен: " + result.message());
            }
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка redo",
                ex,
                ErrorCode.DB_QUERY_FAILED,
                "Не удалось повторить действие.",
                false,
                "operation", "redoAction"
            );
        }
    }

    private void handleGlobalShortcutKeyPressed(KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        if (!ConfigManager.isUxShortcutsEnabled()) {
            return;
        }
        String shortcutToken = ShortcutRegistry.toShortcutToken(event);
        if (shortcutToken == null || shortcutToken.isBlank()) {
            return;
        }
        ShortcutRegistry.ShortcutContext[] contexts = isTextInputEvent(event)
            ? new ShortcutRegistry.ShortcutContext[] {
                ShortcutRegistry.ShortcutContext.GLOBAL,
                ShortcutRegistry.ShortcutContext.FOCUSED_PANE,
                ShortcutRegistry.ShortcutContext.CONTROL_DEFAULT
            }
            : new ShortcutRegistry.ShortcutContext[] {
                ShortcutRegistry.ShortcutContext.GLOBAL,
                ShortcutRegistry.ShortcutContext.FOCUSED_PANE
            };
        Set<ShortcutRegistry.ShortcutContext> activeContexts = Set.of(contexts);
        shortcutRegistry.resolve(shortcutToken, activeContexts).ifPresent(binding -> {
            UndoRedoManager.CommandResult result = commandActionRegistry.execute(binding.actionId());
            if (result != null && result.successful()) {
                syncPaletteHistoryFromExternalAction(binding.actionId());
            }
            event.consume();
        });
    }

    private boolean isTextInputEvent(KeyEvent event) {
        Object target = event.getTarget();
        return target instanceof TextInputControl;
    }

    private void focusGlobalSearch() {
        if (!ConfigManager.isUxGlobalSearchEnabled()) {
            return;
        }
        openCommandPaletteWithQuery("", false);
    }

    private void showShortcutsHelp() {
        overlayDialogManager.open(
            OverlayDialogManager.OverlayId.SHORTCUTS_HELP,
            currentWindow(),
            OverlayDialogManager.OverlayRequest.empty()
        );
    }

    private void openCommandPalette() {
        if (!ConfigManager.isUxCommandPaletteEnabled()) {
            return;
        }
        openCommandPaletteWithQuery("", true);
    }

    private void openCommandPaletteWithQuery(String initialQuery, boolean toggle) {
        OverlayDialogManager.OverlayRequest request = new OverlayDialogManager.OverlayRequest(initialQuery);
        if (toggle) {
            overlayDialogManager.toggle(
                OverlayDialogManager.OverlayId.COMMAND_PALETTE,
                currentWindow(),
                request
            );
            applyAdaptiveLayoutStateToShell(false);
            return;
        }
        overlayDialogManager.open(
            OverlayDialogManager.OverlayId.COMMAND_PALETTE,
            currentWindow(),
            request
        );
        applyAdaptiveLayoutStateToShell(false);
    }

    private javafx.stage.Window currentWindow() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private void registerOverlayDialogs() {
        overlayDialogManager.register(
            OverlayDialogManager.OverlayId.COMMAND_PALETTE,
            new OverlayDialogManager.OverlayHandle() {
                @Override
                public void open(javafx.stage.Window owner, OverlayDialogManager.OverlayRequest request) {
                    commandPaletteDialog.open(owner, request == null ? "" : request.initialQuery());
                }

                @Override
                public boolean isOpen() {
                    return commandPaletteDialog.isOpen();
                }

                @Override
                public void close() {
                    commandPaletteDialog.close();
                }
            }
        );
        overlayDialogManager.register(
            OverlayDialogManager.OverlayId.SHORTCUTS_HELP,
            (owner, request) -> ShortcutsHelpDialog.show(
                owner,
                "Горячие клавиши: задачи",
                ShortcutsHelpDialog.defaultMainEntries()
            )
        );
    }

    private void registerCommandPaletteActions() {
        if (commandPaletteActionsRegistered) {
            return;
        }
        commandPaletteActionsRegistered = true;

        registerPaletteAction(
            "main.inbox.addTask",
            "Inbox: добавить задачу",
            "inbox",
            "Ctrl/Cmd+N",
            () -> handleAddTask(null),
            this::isPresenterReady,
            () -> "Презентер не инициализирован",
            false
        );
        registerPaletteAction(
            "main.task.edit.selected",
            "Изменить выбранную задачу",
            "tasks",
            "",
            () -> handleEditTask(getSelectedTask()),
            this::hasSelectedTask,
            () -> "Выберите задачу",
            false
        );
        registerPaletteAction(
            "main.task.bulk.archive",
            "Архивировать выбранные задачи",
            "bulk",
            "",
            this::bulkArchive,
            this::hasTaskSelection,
            () -> "Выберите задачи (Ctrl+клик)",
            false
        );
        registerPaletteAction(
            "main.task.bulk.delete",
            "Удалить выбранные задачи",
            "bulk",
            "",
            this::bulkDelete,
            this::hasTaskSelection,
            () -> "Выберите задачи (Ctrl+клик)",
            true
        );
        registerPaletteAction(
            "main.task.bulk.tag",
            "Добавить тег выбранным задачам",
            "bulk",
            "",
            this::bulkAddTag,
            this::hasTaskSelection,
            () -> "Выберите задачи (Ctrl+клик)",
            false
        );
        registerPaletteAction(
            "main.ai.analyze.selected",
            "ИИ: анализ выбранной задачи",
            "ai",
            "",
            this::runAIAnalysisForSelected,
            this::hasSelectedTask,
            () -> "Выберите задачу",
            false
        );
        registerPaletteAction(
            "main.ai.autoPrioritize",
            "ИИ: авто-приоритет",
            "ai",
            "",
            this::handleAutoPrioritization,
            this::hasTasksLoaded,
            () -> "Нет задач для приоритизации",
            false
        );
        registerPaletteAction(
            "main.ai.autoSchedule",
            "ИИ: авто-планирование",
            "ai",
            "",
            this::handleAutoSchedule,
            this::hasTasksLoaded,
            () -> "Нет задач для планирования",
            false
        );
        registerPaletteAction(
            "main.tools.notes.open",
            "Открыть умные заметки",
            "tools",
            "",
            this::openSmartNotesPanel,
            () -> true,
            () -> "",
            false
        );
        registerPaletteAction(
            "main.system.export",
            "Открыть экспорт",
            "system",
            "",
            this::openExportPanel,
            () -> true,
            () -> "",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_OPEN_PALETTE,
            "Открыть командную палитру",
            "system",
            "Ctrl/Cmd+K",
            this::openCommandPalette,
            () -> ConfigManager.isUxCommandPaletteEnabled(),
            () -> "Командная палитра отключена",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_FOCUS_GLOBAL_SEARCH,
            "Фокус глобального поиска",
            "system",
            "Ctrl/Cmd+F",
            this::focusGlobalSearch,
            () -> ConfigManager.isUxGlobalSearchEnabled(),
            () -> "Глобальный поиск отключен",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_TOGGLE_INLINE_OVERLAY,
            "Показать/скрыть inline-вкладки",
            "system",
            "Ctrl/Cmd+Shift+L",
            this::toggleInlineOverlayVisibilityFromTaskPanel,
            this::hasInlineTabsOpen,
            () -> "Нет открытых inline-вкладок",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_UNDO,
            "Undo: отменить последнее действие",
            "history",
            "Ctrl/Cmd+Z",
            this::handleUndoAction,
            this::isPresenterReady,
            () -> "Undo недоступен",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_REDO,
            "Redo: повторить отмененное действие",
            "history",
            "Ctrl/Cmd+Shift+Z",
            this::handleRedoAction,
            this::isPresenterReady,
            () -> "Redo недоступен",
            false
        );
        registerPaletteAction(
            MAIN_ACTION_SHORTCUTS_HELP,
            "Показать горячие клавиши",
            "system",
            "",
            this::showShortcutsHelp,
            () -> true,
            () -> "",
            false
        );
        registerSidebarBridgeActions();
    }

    private void registerSidebarBridgeActions() {
        for (SidebarNavItem navItem : sidebarNavigationService.buildItems()) {
            if (navItem == null || navItem.actionId() == null || navItem.actionId().isBlank()) {
                continue;
            }
            String actionId = navItem.actionId();
            if (commandActionRegistry.isRegistered(actionId)) {
                continue;
            }
            Runnable handler = resolveSidebarAction(actionId);
            if (handler == null) {
                registerPaletteAction(
                    actionId,
                    navItem.label(),
                    navItem.sectionId(),
                    "",
                    () -> { },
                    () -> false,
                    () -> "Действие пока не реализовано",
                    false
                );
                continue;
            }
            registerPaletteAction(
                actionId,
                navItem.label(),
                navItem.sectionId(),
                "",
                handler,
                availabilityForSidebarAction(actionId),
                unavailableReasonForSidebarAction(actionId),
                false
            );
        }
    }

    private BooleanSupplier availabilityForSidebarAction(String actionId) {
        return switch (actionId) {
            case "main.task.addSubtask",
                "main.task.archive.selected",
                "main.task.dependency.link",
                "main.task.dependency.unlink",
                "main.task.dependency.details" -> this::hasSelectedTask;
            case "main.task.bulk.archive",
                "main.task.bulk.delete",
                "main.task.bulk.tag" -> this::hasTaskSelection;
            case "main.ai.autoPrioritize",
                "main.ai.autoSchedule",
                "main.ai.categorization",
                "main.task.filter.urgent",
                "main.task.filter.tag",
                "main.task.listAll",
                "main.task.filter.scheduled" -> this::hasTasksLoaded;
            default -> () -> true;
        };
    }

    private Supplier<String> unavailableReasonForSidebarAction(String actionId) {
        return switch (actionId) {
            case "main.task.addSubtask",
                "main.task.archive.selected",
                "main.task.dependency.link",
                "main.task.dependency.unlink",
                "main.task.dependency.details" -> () -> "Выберите задачу";
            case "main.task.bulk.archive",
                "main.task.bulk.delete",
                "main.task.bulk.tag" -> () -> "Выберите задачи (Ctrl+клик)";
            case "main.ai.autoPrioritize",
                "main.ai.autoSchedule",
                "main.ai.categorization" -> () -> "Нет задач для выполнения операции";
            case "main.task.filter.urgent",
                "main.task.filter.tag",
                "main.task.listAll",
                "main.task.filter.scheduled" -> () -> "Список задач пуст";
            default -> () -> "";
        };
    }

    private void registerPaletteAction(
        String actionId,
        String label,
        String category,
        String shortcutHint,
        Runnable handler,
        BooleanSupplier availability,
        Supplier<String> unavailableReason,
        boolean safetyCritical
    ) {
        commandActionRegistry.register(new UiActionRegistry.RegisteredAction(
            actionId,
            label,
            category,
            shortcutHint,
            () -> nonUndoableCommand(actionId, label, category, handler, availability),
            availability,
            unavailableReason,
            safetyCritical
        ));
    }

    private void registerShortcutBindings() {
        if (shortcutsRegistered) {
            return;
        }
        shortcutsRegistered = true;

        registerShortcutBinding("CTRL+K", MAIN_ACTION_OPEN_PALETTE, false, false);
        registerShortcutBinding("CTRL+F", MAIN_ACTION_FOCUS_GLOBAL_SEARCH, false, false);
        registerShortcutBinding("CTRL+SHIFT+L", MAIN_ACTION_TOGGLE_INLINE_OVERLAY, false, false);
        registerShortcutBinding("CTRL+Z", MAIN_ACTION_UNDO, false, false);
        registerShortcutBinding("CTRL+SHIFT+Z", MAIN_ACTION_REDO, false, false);
        registerShortcutBinding("CTRL+N", "main.inbox.addTask", false, false);

        shortcutRegistry.runStartupConflictCheck("mainview");
        refreshSidebarButtonsPresentation();
    }

    private void registerShortcutBinding(
        String shortcut,
        String actionId,
        boolean overrideSafe,
        boolean safetyCritical
    ) {
        shortcutRegistry.register(new ShortcutRegistry.ShortcutBinding(
            shortcut,
            ShortcutRegistry.ShortcutContext.GLOBAL,
            actionId,
            overrideSafe,
            safetyCritical
        ));
    }

    private UserActionCommand nonUndoableCommand(
        String actionId,
        String label,
        String category,
        Runnable handler,
        BooleanSupplier availability
    ) {
        return new UserActionCommand() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public boolean canExecute() {
                return availability == null || availability.getAsBoolean();
            }

            @Override
            public boolean canUndo() {
                return false;
            }

            @Override
            public void execute() {
                if (handler != null) {
                    handler.run();
                }
            }
        };
    }

    private boolean isPresenterReady() {
        return presenter != null;
    }

    private boolean hasTasksLoaded() {
        return !tasks.isEmpty();
    }

    private boolean hasInlineTabsOpen() {
        return !inlineOverlayTabs.isEmpty();
    }

    private boolean hasSelectedTask() {
        return getSelectedTask() != null;
    }

    private boolean hasTaskSelection() {
        return !selectedTaskIds().isEmpty();
    }

    private Task getSelectedTask() {
        TreeItem<Task> selectedItem = taskTable.getSelectionModel().getSelectedItem();
        return selectedItem == null ? null : selectedItem.getValue();
    }

    private List<String> selectedTaskIds() {
        List<String> taskIds = new ArrayList<>();
        for (TreeItem<Task> selectedItem : taskTable.getSelectionModel().getSelectedItems()) {
            if (selectedItem == null || selectedItem.getValue() == null || selectedItem.getValue().getId() == null) {
                continue;
            }
            taskIds.add(selectedItem.getValue().getId());
        }
        return taskIds;
    }

    /** Обновляет тему WebView при смене темы приложения */
    private void refreshWebViewsTheme() {
        // Перезагружаем контент WebView с новыми цветами темы
        if (aiInsightWebView != null) {
            String html = convertMarkdownToHtml(currentInsightText);
            String fullHtml = getHtmlTemplate(html);
            aiInsightWebView.getEngine().loadContent(fullHtml);
        }
        if (descriptionWebView != null) {
            String html = convertMarkdownToHtml(currentDescriptionText);
            String fullHtml = getDescriptionHtmlTemplate(html);
            descriptionWebView.getEngine().loadContent(fullHtml);
        }
    }

    private boolean isSidebarCollapsed = false;
    private final List<Node> sidebarSectionLabels = new ArrayList<>();
    private final List<Button> sidebarButtons = new ArrayList<>();
    private ScrollPane sidebarScrollPane;
    private Region sidebarContainer;
    private VBox sidebarShellBox;
    private HBox sidebarTwoTierRoot;
    private VBox navigationRailBox;
    private ToggleGroup navigationRailToggleGroup;
    private final Map<SidebarRailDomain, ToggleButton> navigationRailButtons = new LinkedHashMap<>();
    private VBox contextSidebarDomainHeaderBox;
    private Label contextSidebarDomainHeaderLabel;
    private Label contextSidebarDomainHeaderMetaLabel;
    private VBox contextSidebarDomainListBox;
    private final List<Button> contextSidebarDomainButtons = new ArrayList<>();
    private HBox contextSidebarFooterBox;
    private Label contextSidebarFooterStatusLabel;
    private Label contextSidebarFooterVersionLabel;
    private SidebarRailDomain renderedContextSidebarDomain;
    private VBox sidebarPinnedQuickZone;
    private Label sidebarQuickTitleLabel;
    private VBox sidebarHeaderContent;
    private Button sidebarToggleBtn;
    private Node sidebarPreviousFocusOwner;
    private Label leftPanelModeLabel;
    private Label leftPanelStateLabel;
    private Label commandPaletteOverlayStateLabel;
    private TextField sidebarFilterField;
    private Button sidebarFilterClearButton;
    private VBox sidebarQuickActionsBox;
    private final List<Button> sidebarQuickActionButtons = new ArrayList<>();
    private Separator sidebarQuickZoneDivider;
    private Label sidebarFavoritesLabel;
    private VBox sidebarFavoritesBox;
    private Label sidebarRecentLabel;
    private VBox sidebarRecentBox;
    private VBox sidebarFilterResultsBox;
    private Label sidebarFilterResultsLabel;
    private final List<Button> sidebarFilterResultButtons = new ArrayList<>();
    private int sidebarFilterSelectionIndex = -1;
    private List<SidebarNavItem> sidebarNavItems = List.of();
    private List<SidebarNavSection> sidebarNavSections = List.of();
    private SidebarNavState sidebarNavState = SidebarNavState.empty();
    private Set<String> sidebarTrackableActionIds = Set.of();
    private final Map<SidebarNavZone, List<Node>> sidebarZoneContentNodes = new LinkedHashMap<>();
    private final Map<SidebarNavZone, Node> sidebarZoneAnchorNodes = new LinkedHashMap<>();
    private final Map<String, VBox> sidebarSurfaceGroupContentNodes = new LinkedHashMap<>();
    private final Map<String, Label> sidebarSurfaceGroupSummaryLabels = new LinkedHashMap<>();
    private final Map<String, FontIcon> sidebarSurfaceGroupChevronIcons = new LinkedHashMap<>();
    private final Map<String, VBox> sidebarSurfaceGroupCards = new LinkedHashMap<>();
    private final Map<String, Boolean> sidebarSurfaceGroupExpandedState = new LinkedHashMap<>();
    private VBox sidebarGuidedHintCard;
    private Label sidebarGuidedHintBodyLabel;
    private Button sidebarGuidedHintPrimaryBtn;
    private Button sidebarGuidedHintPaletteBtn;
    private Button sidebarGuidedHintDismissBtn;
    private boolean isRightPanelCollapsed = false;
    private BorderPane rightPanelWrapper;
    private HBox rightPanelHeader;
    private Button rightPanelToggleBtn;
    private HBox rightPanelInspectorTabStrip;
    private StackPane rightPanelInspectorContentHost;
    private VBox rightPanelBody;
    private Node rightPanelContent;
    private Label rightPanelModeLabel;
    private Label rightPanelStateLabel;
    private Label rightPanelInspectorFooterLabel;
    private RightPanelDisplayPolicy rightPanelDisplayPolicy;
    private RightPanelInspectorDisplayPolicy rightPanelInspectorDisplayPolicy;
    private final Map<String, Node> rightPanelSectionNodes = new LinkedHashMap<>();
    private final Map<RightPanelInspectorTab, List<String>> rightPanelInspectorTabSectionIds = new LinkedHashMap<>();
    private final Map<RightPanelInspectorTab, Button> rightPanelInspectorTabButtons = new LinkedHashMap<>();
    private final Map<RightPanelInspectorTab, VBox> rightPanelInspectorTabStacks = new LinkedHashMap<>();
    private final Map<RightPanelInspectorTab, ScrollPane> rightPanelInspectorTabScrolls = new LinkedHashMap<>();
    private Button rightPanelQuickToggleBtn;
    private TitledPane detailSecondaryFieldsPane;
    private VBox criticalPathPanelBody;
    private final StackPane rightPanelOverlayHost = new StackPane();
    private final StackPane rightPanelOverlayScrim = new StackPane();
    private Node rightPanelPreviousFocusOwner;
    private UiRightContextMode rightPanelDisplayMode = UiRightContextMode.COLLAPSIBLE;
    private LeftPanelDisplayPolicy leftPanelDisplayPolicy;
    private TwoTierSidebarDisplayPolicy twoTierSidebarDisplayPolicy;
    private StackPane centerShellStack;

    private Region createSidebar() {
        resetSidebarRenderState();
        return createSidebarFromNavigationModel();
    }

    private void resetSidebarRenderState() {
        sidebarSectionLabels.clear();
        sidebarButtons.clear();
        sidebarFilterResultButtons.clear();
        sidebarQuickActionButtons.clear();
        sidebarZoneContentNodes.clear();
        sidebarZoneAnchorNodes.clear();
        sidebarSurfaceGroupContentNodes.clear();
        sidebarSurfaceGroupSummaryLabels.clear();
        sidebarSurfaceGroupChevronIcons.clear();
        sidebarSurfaceGroupCards.clear();
        sidebarSurfaceGroupExpandedState.clear();
        sidebarFilterSelectionIndex = -1;
        undoActionButton = null;
        redoActionButton = null;
        sidebarContainer = null;
        sidebarShellBox = null;
        sidebarTwoTierRoot = null;
        navigationRailBox = null;
        navigationRailToggleGroup = null;
        navigationRailButtons.clear();
        contextSidebarDomainHeaderBox = null;
        contextSidebarDomainHeaderLabel = null;
        contextSidebarDomainHeaderMetaLabel = null;
        contextSidebarDomainListBox = null;
        contextSidebarDomainButtons.clear();
        contextSidebarFooterBox = null;
        contextSidebarFooterStatusLabel = null;
        contextSidebarFooterVersionLabel = null;
        renderedContextSidebarDomain = null;
        sidebarScrollPane = null;
        sidebarPinnedQuickZone = null;
        sidebarQuickTitleLabel = null;
        sidebarPreviousFocusOwner = null;
        leftPanelModeLabel = null;
        leftPanelStateLabel = null;
        commandPaletteOverlayStateLabel = null;
        sidebarFilterField = null;
        sidebarFilterClearButton = null;
        sidebarQuickActionsBox = null;
        sidebarQuickZoneDivider = null;
        sidebarFavoritesLabel = null;
        sidebarFavoritesBox = null;
        sidebarRecentLabel = null;
        sidebarRecentBox = null;
        sidebarGuidedHintCard = null;
        sidebarGuidedHintBodyLabel = null;
        sidebarGuidedHintPrimaryBtn = null;
        sidebarGuidedHintPaletteBtn = null;
        sidebarGuidedHintDismissBtn = null;
        sidebarFilterResultsBox = null;
        sidebarFilterResultsLabel = null;
        sidebarNavItems = List.of();
        sidebarNavSections = List.of();
        sidebarNavState = SidebarNavState.empty();
        sidebarTrackableActionIds = Set.of();
    }

    private Region createSidebarFromNavigationModel() {
        List<SidebarNavSection> sections = sidebarNavigationService.buildSections();
        List<SidebarNavItem> items = sidebarNavigationService.buildItems();
        sidebarNavSections = sections;
        sidebarNavItems = items;
        sidebarNavState = sidebarNavigationService.loadState();
        sidebarTrackableActionIds = collectSidebarActionIds(items);

        VBox quickZone = createPinnedQuickZone(
            sidebarNavigationService.buildQuickAccessItems(sidebarNavigationService.maxQuickItems())
        );
        Node appHeader = createSidebarHeader();
        Node domainHeader = createContextSidebarDomainHeader();
        VBox domainList = createContextSidebarDomainList();
        ScrollPane scrollPane = createSidebarScrollPane(domainList);
        Node footer = createContextSidebarFooter();
        VBox shell = new VBox(0);
        shell.getStyleClass().addAll("sidebar-shell", "sidebar-context-shell");
        shell.getChildren().addAll(appHeader, quickZone, domainHeader, scrollPane, footer);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        VBox rail = createNavigationRail();
        HBox twoTierRoot = new HBox(0);
        twoTierRoot.getStyleClass().addAll("sidebar-two-tier-root", "shell-zone-left");
        twoTierRoot.getChildren().addAll(rail, shell);
        HBox.setHgrow(shell, Priority.ALWAYS);
        sidebarShellBox = shell;
        sidebarTwoTierRoot = twoTierRoot;
        sidebarContainer = twoTierRoot;
        applySidebarCollapsedVisualState();
        applyContextSidebarDomainContentPolicy(twoTierSidebarDisplayPolicy);
        applySidebarFilter("");
        applyNavigationRailPolicy(twoTierSidebarDisplayPolicy);
        return twoTierRoot;
    }

    private VBox createContextSidebarDomainHeader() {
        VBox headerBox = new VBox(2);
        headerBox.getStyleClass().add("context-sidebar-domain-header");
        headerBox.setPadding(new Insets(8, 12, 8, 12));
        contextSidebarDomainHeaderBox = headerBox;

        Label title = new Label("Рабочее");
        title.getStyleClass().add("context-sidebar-domain-title");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        contextSidebarDomainHeaderLabel = title;
        sidebarSectionLabels.add(title);

        Label meta = new Label("Действия выбранного домена");
        meta.getStyleClass().add("context-sidebar-domain-meta");
        meta.setWrapText(false);
        meta.setTextOverrun(OverrunStyle.ELLIPSIS);
        contextSidebarDomainHeaderMetaLabel = meta;
        sidebarSectionLabels.add(meta);

        headerBox.getChildren().addAll(title, meta);
        sidebarSectionLabels.add(headerBox);
        return headerBox;
    }

    private VBox createContextSidebarDomainList() {
        VBox listBox = new VBox(2);
        listBox.getStyleClass().addAll("sidebar-content", "context-sidebar-domain-list");
        listBox.setPadding(new Insets(8, 8, 12, 8));
        listBox.setMinHeight(0);
        contextSidebarDomainListBox = listBox;
        return listBox;
    }

    private HBox createContextSidebarFooter() {
        HBox footerBox = new HBox(8);
        footerBox.getStyleClass().add("context-sidebar-footer");
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.setPadding(new Insets(6, 10, 8, 10));
        contextSidebarFooterBox = footerBox;

        Label status = new Label("Доменные действия: 0");
        status.getStyleClass().add("context-sidebar-footer-status");
        status.setMaxWidth(Double.MAX_VALUE);
        status.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(status, Priority.ALWAYS);
        contextSidebarFooterStatusLabel = status;
        sidebarSectionLabels.add(status);

        Label version = new Label("2.0");
        version.getStyleClass().addAll("sidebar-version", "context-sidebar-footer-version");
        contextSidebarFooterVersionLabel = version;
        sidebarSectionLabels.add(version);

        footerBox.getChildren().addAll(status, version);
        sidebarSectionLabels.add(footerBox);
        return footerBox;
    }

    private VBox createNavigationRail() {
        VBox rail = new VBox(4);
        rail.getStyleClass().add("navigation-rail");
        rail.setPadding(new Insets(8, 6, 8, 6));
        rail.setAlignment(Pos.TOP_CENTER);
        rail.setFillWidth(true);
        rail.setFocusTraversable(false);
        applyRegionWidth(rail, SIDEBAR_ICON_RAIL_WIDTH);

        ToggleGroup toggleGroup = new ToggleGroup();
        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
        navigationRailToggleGroup = toggleGroup;

        List<NavigationRailSection> sections = twoTierSidebarDisplayPolicy == null
            ? List.of()
            : twoTierSidebarDisplayPolicy.visibleRailSections();
        if (sections.isEmpty()) {
            for (SidebarRailDomain domain : sidebarNavigationService.buildRailDomains()) {
                rail.getChildren().add(createNavigationRailToggle(
                    new NavigationRailSection(
                        domain,
                        domain.ordinal(),
                        domain.label(),
                        domain.railTooltipLabel(),
                        domain.contextHeaderLabel(),
                        domain.icon()
                    )
                ));
            }
        } else {
            for (NavigationRailSection section : sections) {
                rail.getChildren().add(createNavigationRailToggle(section));
            }
        }
        navigationRailBox = rail;
        refreshNavigationRailSelection(twoTierSidebarDisplayPolicy);
        return rail;
    }

    private ToggleButton createNavigationRailToggle(NavigationRailSection section) {
        NavigationRailSection safeSection = section == null
            ? new NavigationRailSection(SidebarRailDomain.WORK, 0, "Рабочее", "Рабочее", "Рабочее", "mdi:briefcase-outline")
            : section;
        SidebarRailDomain domain = safeSection.domain();
        ToggleButton button = new ToggleButton();
        button.getStyleClass().add("navigation-rail-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinHeight(40);
        button.setFocusTraversable(true);
        button.setToggleGroup(navigationRailToggleGroup);
        button.getProperties().put("railDomain", domain);
        button.setAccessibleText(safeSection.railLabel());
        button.setGraphic(createNavigationRailIcon(safeSection));
        Tooltip tooltip = new Tooltip(safeSection.railTooltipLabel());
        tooltip.getStyleClass().add("sidebar-tooltip");
        button.setTooltip(tooltip);
        button.setOnAction(event -> {
            if (!button.isSelected()) {
                button.setSelected(true);
            }
            selectNavigationRailDomain(domain, true);
        });
        button.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        button.addEventHandler(KeyEvent.KEY_PRESSED, this::handleNavigationRailKeyPressed);
        navigationRailButtons.put(domain, button);
        return button;
    }

    private Node createNavigationRailIcon(NavigationRailSection section) {
        try {
            FontIcon icon = FontIcon.of(resolveNavigationRailIconCode(section), 18);
            icon.setIconSize(18);
            icon.getStyleClass().add("navigation-rail-icon");
            return icon;
        } catch (RuntimeException ex) {
            FontIcon fallback = FontIcon.of(MaterialDesignM.MENU, 18);
            fallback.getStyleClass().add("navigation-rail-icon");
            return fallback;
        }
    }

    private Ikon resolveNavigationRailIconCode(NavigationRailSection section) {
        SidebarRailDomain domain = section == null || section.domain() == null
            ? SidebarRailDomain.WORK
            : section.domain();
        return switch (domain) {
            case WORK -> MaterialDesignH.HOME;
            case RECENT -> MaterialDesignC.CLOCK_OUTLINE;
            case TOOLS -> MaterialDesignT.TOOLS;
            case ANALYTICS -> MaterialDesignC.CHART_LINE;
            case SYSTEM -> MaterialDesignC.COG_OUTLINE;
        };
    }

    private void handleNavigationRailKeyPressed(KeyEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getCode()) {
            case UP -> {
                moveNavigationRailSelection(-1);
                event.consume();
            }
            case DOWN -> {
                moveNavigationRailSelection(1);
                event.consume();
            }
            case HOME -> {
                focusNavigationRailBoundary(true);
                event.consume();
            }
            case END -> {
                focusNavigationRailBoundary(false);
                event.consume();
            }
            case ENTER, SPACE -> {
                ToggleButton focused = findNavigationRailButton(event.getTarget());
                if (focused != null) {
                    focused.fire();
                    event.consume();
                }
            }
            default -> {
                // No-op; Tab navigation remains default platform behavior.
            }
        }
    }

    private ToggleButton findNavigationRailButton(Object eventTarget) {
        if (!(eventTarget instanceof Node node)) {
            return null;
        }
        Node cursor = node;
        while (cursor != null) {
            if (cursor instanceof ToggleButton toggle && toggle.getProperties().get("railDomain") instanceof SidebarRailDomain) {
                return toggle;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private void moveNavigationRailSelection(int delta) {
        if (navigationRailButtons.isEmpty()) {
            return;
        }
        List<ToggleButton> visibleButtons = new ArrayList<>();
        for (SidebarRailDomain domain : SidebarRailDomain.values()) {
            ToggleButton button = navigationRailButtons.get(domain);
            if (button == null || !button.isManaged() || !button.isVisible()) {
                continue;
            }
            visibleButtons.add(button);
        }
        if (visibleButtons.isEmpty()) {
            return;
        }
        ToggleButton current = null;
        for (ToggleButton button : visibleButtons) {
            if (button.isFocused()) {
                current = button;
                break;
            }
        }
        if (current == null) {
            Toggle selected = navigationRailToggleGroup == null ? null : navigationRailToggleGroup.getSelectedToggle();
            if (selected instanceof ToggleButton selectedButton && visibleButtons.contains(selectedButton)) {
                current = selectedButton;
            }
        }
        int currentIndex = current == null ? 0 : visibleButtons.indexOf(current);
        int nextIndex = Math.floorMod(currentIndex + delta, visibleButtons.size());
        ToggleButton next = visibleButtons.get(nextIndex);
        next.requestFocus();
        if (!next.isSelected()) {
            next.fire();
        }
    }

    private void focusNavigationRailBoundary(boolean first) {
        if (navigationRailButtons.isEmpty()) {
            return;
        }
        List<ToggleButton> visibleButtons = new ArrayList<>();
        for (SidebarRailDomain domain : SidebarRailDomain.values()) {
            ToggleButton button = navigationRailButtons.get(domain);
            if (button != null && button.isVisible() && button.isManaged()) {
                visibleButtons.add(button);
            }
        }
        if (visibleButtons.isEmpty()) {
            return;
        }
        ToggleButton target = first ? visibleButtons.get(0) : visibleButtons.get(visibleButtons.size() - 1);
        target.requestFocus();
        if (!target.isSelected()) {
            target.fire();
        }
    }

    private void selectNavigationRailDomain(SidebarRailDomain domain, boolean userInitiated) {
        SidebarRailDomain safeDomain = domain == null ? SidebarRailDomain.WORK : domain;
        mainLayoutCoordinator.selectNavigationRailDomain(safeDomain);
        twoTierSidebarDisplayPolicy = mainLayoutCoordinator.twoTierSidebarDisplayPolicy();
        boolean openedOnDemand = userInitiated && maybeOpenContextSidebarForRailSelection(twoTierSidebarDisplayPolicy);
        refreshNavigationRailSelection(twoTierSidebarDisplayPolicy);
        if (userInitiated) {
            applyAdaptiveLayoutStateToShell(true, !openedOnDemand);
        }
    }

    private boolean maybeOpenContextSidebarForRailSelection(TwoTierSidebarDisplayPolicy policy) {
        TwoTierSidebarDisplayPolicy safePolicy = policy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : policy;
        if (safePolicy == null) {
            return false;
        }
        ContextSidebarDisplayPolicy contextPolicy = safePolicy.contextSidebarPolicy();
        if (contextPolicy == null) {
            return false;
        }
        if (!contextPolicy.collapsed()) {
            return false;
        }
        mainLayoutCoordinator.setContextSidebarCollapsed(false);
        twoTierSidebarDisplayPolicy = mainLayoutCoordinator.twoTierSidebarDisplayPolicy();
        return true;
    }

    private void refreshNavigationRailSelection(TwoTierSidebarDisplayPolicy policy) {
        TwoTierSidebarDisplayPolicy safePolicy = policy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : policy;
        if (safePolicy == null || navigationRailButtons.isEmpty()) {
            return;
        }
        SidebarRailDomain activeDomain = safePolicy.activeRailDomain();
        for (Map.Entry<SidebarRailDomain, ToggleButton> entry : navigationRailButtons.entrySet()) {
            SidebarRailDomain domain = entry.getKey();
            ToggleButton button = entry.getValue();
            if (button == null) {
                continue;
            }
            boolean visible = safePolicy.railContains(domain);
            button.setVisible(visible);
            button.setManaged(visible);
            button.setSelected(domain == activeDomain);
        }
        ToggleButton activeButton = navigationRailButtons.get(activeDomain);
        if (activeButton != null && activeButton.isVisible() && navigationRailToggleGroup != null) {
            navigationRailToggleGroup.selectToggle(activeButton);
        }
    }

    private void applyNavigationRailPolicy(TwoTierSidebarDisplayPolicy policy) {
        if (navigationRailBox == null) {
            return;
        }
        TwoTierSidebarDisplayPolicy safePolicy = policy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : policy;
        if (safePolicy == null) {
            return;
        }
        ContextSidebarDisplayPolicy contextPolicy = safePolicy.contextSidebarPolicy();
        refreshNavigationRailSelection(safePolicy);
        setStyleClassPresent(navigationRailBox, "compact", safePolicy.breakpoint() == UiLayoutBreakpoint.COMPACT);
        setStyleClassPresent(navigationRailBox, "height-compact", safePolicy.heightCompactionApplied());
        setStyleClassPresent(navigationRailBox, "height-aggressive", safePolicy.aggressiveCompaction());
        setStyleClassPresent(navigationRailBox, "overlay-semantics", contextPolicy != null && contextPolicy.overlayOnDemand());
        setStyleClassPresent(navigationRailBox, "context-collapsed", contextPolicy != null && contextPolicy.collapsed());
    }

    private void applyContextSidebarDomainContentPolicy(TwoTierSidebarDisplayPolicy policy) {
        TwoTierSidebarDisplayPolicy safePolicy = policy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : policy;
        if (safePolicy == null) {
            return;
        }
        ContextSidebarDisplayPolicy contextPolicy = safePolicy.contextSidebarPolicy();
        SidebarRailDomain activeDomain = safePolicy.activeRailDomain();
        renderContextSidebarDomainList(activeDomain);
        refreshContextSidebarDomainHeader(activeDomain, contextPolicy);
        refreshContextSidebarFooterStatus(activeDomain, contextPolicy);
        boolean filterActive = sidebarFilterField != null
            && sidebarFilterField.getText() != null
            && !sidebarFilterField.getText().isBlank();
        if (sidebarScrollPane != null) {
            boolean showScroll = !filterActive && !(contextPolicy != null && contextPolicy.collapsed());
            setNodeVisibility(sidebarScrollPane, showScroll);
        }
        if (contextSidebarDomainHeaderBox != null) {
            boolean showHeader = !filterActive && !(contextPolicy != null && contextPolicy.collapsed());
            setNodeVisibility(contextSidebarDomainHeaderBox, showHeader && !isSidebarCollapsed);
        }
        if (contextSidebarFooterBox != null) {
            boolean showFooter = !filterActive && !(contextPolicy != null && contextPolicy.collapsed());
            setNodeVisibility(contextSidebarFooterBox, showFooter && !isSidebarCollapsed);
        }
        if (sidebarShellBox != null) {
            setStyleClassPresent(sidebarShellBox, "context-sidebar-height-compact",
                contextPolicy != null && contextPolicy.heightCompactionApplied());
            setStyleClassPresent(sidebarShellBox, "context-sidebar-height-aggressive",
                contextPolicy != null && contextPolicy.aggressiveCompaction());
        }
        if (sidebarTwoTierRoot != null) {
            setStyleClassPresent(sidebarTwoTierRoot, "context-sidebar-height-compact",
                contextPolicy != null && contextPolicy.heightCompactionApplied());
            setStyleClassPresent(sidebarTwoTierRoot, "context-sidebar-height-aggressive",
                contextPolicy != null && contextPolicy.aggressiveCompaction());
        }
    }

    private void renderContextSidebarDomainList(SidebarRailDomain domain) {
        if (contextSidebarDomainListBox == null) {
            return;
        }
        SidebarRailDomain safeDomain = domain == null ? SidebarRailDomain.WORK : domain;
        if (renderedContextSidebarDomain == safeDomain && !contextSidebarDomainListBox.getChildren().isEmpty()) {
            return;
        }
        clearContextSidebarDomainButtons();
        contextSidebarDomainListBox.getChildren().clear();
        undoActionButton = null;
        redoActionButton = null;

        List<SidebarNavItem> domainItems;
        try {
            if (safeDomain == SidebarRailDomain.RECENT) {
                domainItems = sidebarNavigationService.buildRecentItems(
                    sidebarNavState,
                    sidebarNavigationService.maxRecentItems()
                );
            } else {
                domainItems = sidebarNavigationService.buildContextSidebarDomainItems(safeDomain);
            }
        } catch (RuntimeException ex) {
            addContextSidebarDomainFallback(
                "Не удалось загрузить действия раздела. Попробуйте снова или используйте Ctrl/Cmd+K.",
                true
            );
            renderedContextSidebarDomain = safeDomain;
            return;
        }
        for (SidebarNavItem item : domainItems) {
            Button button = createSidebarButtonFromModelItem(item);
            if (button == null) {
                continue;
            }
            if (!button.getStyleClass().contains("context-sidebar-domain-btn")) {
                button.getStyleClass().add("context-sidebar-domain-btn");
            }
            contextSidebarDomainListBox.getChildren().add(button);
            contextSidebarDomainButtons.add(button);
        }
        if (domainItems.isEmpty()) {
            addContextSidebarDomainFallback(
                safeDomain == SidebarRailDomain.RECENT
                    ? "Недавних действий пока нет. Выполните действие в sidebar, palette или через shortcut."
                    : "Нет доступных действий в этом разделе. Редкие команды ищите через Ctrl/Cmd+K.",
                false
            );
        }
        renderedContextSidebarDomain = safeDomain;
    }

    private void addContextSidebarDomainFallback(String text, boolean errorState) {
        if (contextSidebarDomainListBox == null) {
            return;
        }
        Label fallback = new Label(text == null || text.isBlank() ? "Нет доступных действий." : text);
        fallback.getStyleClass().add("context-sidebar-domain-empty");
        if (errorState) {
            fallback.getStyleClass().add("context-sidebar-domain-empty-error");
        }
        fallback.setWrapText(true);
        fallback.setMaxWidth(Double.MAX_VALUE);
        contextSidebarDomainListBox.getChildren().add(fallback);
    }

    private void clearContextSidebarDomainButtons() {
        for (Button button : new ArrayList<>(contextSidebarDomainButtons)) {
            sidebarButtons.remove(button);
        }
        contextSidebarDomainButtons.clear();
    }

    private void refreshContextSidebarDomainHeader(SidebarRailDomain domain, ContextSidebarDisplayPolicy contextPolicy) {
        SidebarRailDomain safeDomain = domain == null ? SidebarRailDomain.WORK : domain;
        boolean compact = contextPolicy != null && contextPolicy.heightCompactionApplied();
        boolean aggressive = contextPolicy != null && contextPolicy.aggressiveCompaction();
        if (contextSidebarDomainHeaderLabel != null) {
            contextSidebarDomainHeaderLabel.setText(safeDomain.contextHeaderLabel());
        }
        if (contextSidebarDomainHeaderMetaLabel != null) {
            int count = contextSidebarDomainButtons.size();
            String mode = contextPolicy == null ? "N/A" : contextPolicy.sidebarMode().name();
            String metaText = aggressive
                ? count + " • " + mode
                : compact
                    ? count + " действий • " + mode
                    : "Действия раздела: " + count + " • " + mode;
            contextSidebarDomainHeaderMetaLabel.setText(metaText);
            setNodeVisibility(contextSidebarDomainHeaderMetaLabel, !aggressive);
        }
        if (contextSidebarDomainHeaderBox != null) {
            setStyleClassPresent(contextSidebarDomainHeaderBox, "compact", compact);
            setStyleClassPresent(contextSidebarDomainHeaderBox, "aggressive", aggressive);
        }
    }

    private void refreshContextSidebarFooterStatus(SidebarRailDomain domain, ContextSidebarDisplayPolicy contextPolicy) {
        if (contextSidebarFooterStatusLabel == null) {
            return;
        }
        SidebarRailDomain safeDomain = domain == null ? SidebarRailDomain.WORK : domain;
        boolean compact = contextPolicy != null && contextPolicy.heightCompactionApplied();
        boolean aggressive = contextPolicy != null && contextPolicy.aggressiveCompaction();
        String suffix = "";
        if (contextPolicy != null && contextPolicy.overlayOnDemand()) {
            suffix = contextPolicy.collapsed()
                ? (aggressive ? " • demand" : " • on demand")
                : " • overlay";
        } else if (contextPolicy != null && contextPolicy.collapsed()) {
            suffix = aggressive ? " • clpsd" : " • collapsed";
        }
        String statusText = aggressive
            ? safeDomain.label() + " • " + contextSidebarDomainButtons.size() + suffix
            : compact
                ? safeDomain.label() + " • " + contextSidebarDomainButtons.size() + " д." + suffix
                : safeDomain.label() + " • " + contextSidebarDomainButtons.size() + " действий" + suffix;
        statusText += buildSidebarSyncSuffix(aggressive, compact);
        contextSidebarFooterStatusLabel.setText(statusText);
        if (contextSidebarFooterVersionLabel != null) {
            setNodeVisibility(contextSidebarFooterVersionLabel, !aggressive);
        }
        if (contextSidebarFooterBox != null) {
            setStyleClassPresent(contextSidebarFooterBox, "compact", compact);
            setStyleClassPresent(contextSidebarFooterBox, "aggressive", aggressive);
        }
    }

    private void handleSyncSnapshotChanged(SyncUiSnapshot snapshot) {
        Runnable refresh = () -> applyContextSidebarDomainContentPolicy(twoTierSidebarDisplayPolicy);
        if (Platform.isFxApplicationThread()) {
            refresh.run();
        } else {
            Platform.runLater(refresh);
        }
    }

    private String buildSidebarSyncSuffix(boolean aggressive, boolean compact) {
        SyncUiSnapshot snapshot = SyncClientFacade.getInstance().snapshot();
        if (snapshot == null) {
            return "";
        }
        String state = switch (snapshot.status()) {
            case SIGNED_OUT -> aggressive ? " • out" : " • cloud: sign-out";
            case SYNCING -> aggressive ? " • sync" : compact ? " • cloud: sync" : " • cloud: синхронизация";
            case SYNCED -> aggressive ? " • ok" : " • cloud: synced";
            case CONFLICT -> aggressive ? " • warn" : " • cloud: conflict";
            case OFFLINE -> aggressive ? " • off" : " • cloud: offline";
        };
        if (snapshot.status() == SyncUiStatus.CONFLICT && snapshot.remotePreviewChangeCount() > 0 && !aggressive) {
            return state + " (" + snapshot.remotePreviewChangeCount() + ")";
        }
        return state;
    }

    private VBox createPinnedQuickZone(List<SidebarNavItem> quickItems) {
        VBox quickZone = new VBox(2);
        quickZone.getStyleClass().add("sidebar-quick-zone");
        quickZone.setPadding(new Insets(6, 12, 6, 12));
        sidebarPinnedQuickZone = quickZone;

        Label quickTitle = new Label("БЫСТРЫЙ ДОСТУП");
        quickTitle.getStyleClass().addAll("sidebar-section-label", "sidebar-quick-title");
        sidebarQuickTitleLabel = quickTitle;
        sidebarSectionLabels.add(quickTitle);
        quickZone.getChildren().add(quickTitle);

        if (sidebarNavigationService.isFilterEnabled()) {
            HBox filterBox = createSidebarFilterBar();
            sidebarSectionLabels.add(filterBox);
            quickZone.getChildren().add(filterBox);
        }

        sidebarQuickActionsBox = new VBox(2);
        sidebarQuickActionsBox.getStyleClass().add("sidebar-quick-actions");
        for (SidebarNavItem item : quickItems) {
            Button button = createSidebarButtonFromModelItem(item);
            if (button == null) {
                continue;
            }
            if (!button.getStyleClass().contains("sidebar-btn-quick")) {
                button.getStyleClass().add("sidebar-btn-quick");
            }
            sidebarQuickActionsBox.getChildren().add(button);
            sidebarQuickActionButtons.add(button);
        }

        Separator divider = new Separator();
        divider.getStyleClass().add("sidebar-zone-divider");
        sidebarQuickZoneDivider = divider;
        sidebarSectionLabels.add(divider);
        sidebarQuickActionsBox.getChildren().add(divider);
        quickZone.getChildren().add(sidebarQuickActionsBox);
        quickZone.getChildren().add(createSidebarGuidedHintCard());

        if (sidebarNavigationService.isFavoritesEnabled()) {
            sidebarFavoritesLabel = createSidebarPersonalizationLabel(SIDEBAR_FAVORITES_TITLE);
            sidebarSectionLabels.add(sidebarFavoritesLabel);
            quickZone.getChildren().add(sidebarFavoritesLabel);

            sidebarFavoritesBox = new VBox(2);
            sidebarFavoritesBox.getStyleClass().add("sidebar-personalization-box");
            quickZone.getChildren().add(sidebarFavoritesBox);
        }

        sidebarFilterResultsBox = new VBox(2);
        sidebarFilterResultsBox.getStyleClass().add("sidebar-filter-results");
        sidebarFilterResultsBox.setVisible(false);
        sidebarFilterResultsBox.setManaged(false);

        sidebarFilterResultsLabel = new Label();
        sidebarFilterResultsLabel.getStyleClass().add("sidebar-filter-results-label");
        sidebarSectionLabels.add(sidebarFilterResultsLabel);
        sidebarFilterResultsBox.getChildren().add(sidebarFilterResultsLabel);

        sidebarSectionLabels.add(sidebarFilterResultsBox);
        quickZone.getChildren().add(sidebarFilterResultsBox);
        refreshSidebarPersonalizationZones();
        return quickZone;
    }

    private Label createSidebarPersonalizationLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().addAll("sidebar-section-label", "sidebar-personalization-title");
        return label;
    }

    private void refreshSidebarPersonalizationZones() {
        if (sidebarFavoritesBox != null) {
            List<SidebarNavItem> favoriteItems = sidebarNavigationService.buildFavoriteItems(
                sidebarNavState,
                sidebarNavigationService.maxFavoriteItems()
            );
            renderSidebarPersonalizationItems(
                sidebarFavoritesBox,
                favoriteItems,
                "Закрепите действия через ПКМ • редкие команды ищите в палитре (Ctrl/Cmd+K)",
                "sidebar-btn-favorite-entry",
                "sidebar-personalization-empty"
            );
        }
        if (sidebarRecentBox != null) {
            List<SidebarNavItem> recentItems = sidebarNavigationService.buildRecentItems(
                sidebarNavState,
                sidebarNavigationService.maxRecentItems()
            );
            renderSidebarPersonalizationItems(
                sidebarRecentBox,
                recentItems,
                "Пока пусто • выполните действие в sidebar, palette или через shortcut",
                "sidebar-btn-recent-entry",
                "sidebar-personalization-empty"
            );
        }
    }

    private void renderSidebarPersonalizationItems(
        VBox targetBox,
        List<SidebarNavItem> items,
        String emptyText,
        String buttonStyleClass,
        String emptyLabelStyleClass
    ) {
        if (targetBox == null) {
            return;
        }
        for (Node node : new ArrayList<>(targetBox.getChildren())) {
            if (node instanceof Button button) {
                sidebarButtons.remove(button);
            }
        }
        targetBox.getChildren().clear();

        if (items == null || items.isEmpty()) {
            if (isSidebarCollapsed) {
                return;
            }
            Label placeholder = new Label(emptyText);
            placeholder.getStyleClass().add(emptyLabelStyleClass);
            targetBox.getChildren().add(placeholder);
            return;
        }

        for (SidebarNavItem item : items) {
            Button button = createSidebarButtonFromModelItem(item, true, false);
            if (button == null) {
                continue;
            }
            if (!button.getStyleClass().contains(buttonStyleClass)) {
                button.getStyleClass().add(buttonStyleClass);
            }
            targetBox.getChildren().add(button);
        }
    }

    private void attachSidebarFavoriteContextMenu(Button button, SidebarNavItem item) {
        if (button == null || item == null) {
            return;
        }
        String actionId = canonicalActionId(item.actionId());
        String stateActionId = normalizeActionIdForState(actionId);
        if (!isSidebarTrackableAction(stateActionId)) {
            return;
        }

        MenuItem openViaPaletteItem = new MenuItem("Открыть через палитру (Ctrl/Cmd+K)");
        openViaPaletteItem.setDisable(!ConfigManager.isUxCommandPaletteEnabled());
        openViaPaletteItem.setOnAction(event -> openCommandPaletteForAction(actionId));

        MenuItem showInSidebarItem = new MenuItem("Показать в rail/context sidebar");
        showInSidebarItem.setOnAction(event -> showActionInSidebar(actionId));

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.getItems().addAll(openViaPaletteItem, showInSidebarItem);

        if (!sidebarNavigationService.isFavoritesEnabled()) {
            button.setContextMenu(contextMenu);
            return;
        }

        MenuItem toggleFavoriteItem = new MenuItem();
        contextMenu.getItems().add(new SeparatorMenuItem());
        contextMenu.getItems().add(toggleFavoriteItem);
        contextMenu.setOnShowing(event -> {
            String revealTargetHint = resolveTwoTierSidebarRevealTargetHint(actionId);
            showInSidebarItem.setText("Показать в " + revealTargetHint);
            boolean currentlyFavorite = sidebarNavState.isFavoriteAction(stateActionId);
            toggleFavoriteItem.setText(currentlyFavorite ? "Убрать из избранного" : "Добавить в избранное");
        });
        toggleFavoriteItem.setOnAction(event -> {
            boolean currentlyFavorite = sidebarNavState.isFavoriteAction(stateActionId);
            sidebarNavState = sidebarNavigationService.updateFavoriteAction(
                sidebarNavState,
                stateActionId,
                !currentlyFavorite
            );
            refreshSidebarPersonalizationZones();
        });
        button.setContextMenu(contextMenu);
    }

    private void handleUiActionExecuted(String actionId, UndoRedoManager.CommandResult result) {
        if (result == null || !result.successful()) {
            return;
        }
        if (Platform.isFxApplicationThread()) {
            recordSidebarRecentAction(actionId);
            return;
        }
        Platform.runLater(() -> recordSidebarRecentAction(actionId));
    }

    private void recordSidebarRecentAction(String actionId) {
        if (!sidebarNavigationService.isRecentEnabled()) {
            return;
        }
        String normalized = normalizeActionIdForState(actionId);
        if (!isSidebarTrackableAction(normalized)) {
            return;
        }
        SidebarNavState updated = sidebarNavigationService.recordRecentAction(sidebarNavState, normalized);
        if (updated.recentActionIds().equals(sidebarNavState.recentActionIds())) {
            return;
        }
        sidebarNavState = updated;
        refreshSidebarPersonalizationZones();
    }

    private void syncPaletteHistoryFromExternalAction(String actionId) {
        if (!ConfigManager.isUxCommandPaletteEnabled()) {
            return;
        }
        commandPaletteController.recordExternalActionExecution(actionId);
    }

    private Set<String> collectSidebarActionIds(List<SidebarNavItem> items) {
        if (items == null || items.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> actionIds = new LinkedHashSet<>();
        for (SidebarNavItem item : items) {
            if (item == null) {
                continue;
            }
            String normalized = normalizeActionIdForState(item.actionId());
            if (normalized != null) {
                actionIds.add(normalized);
            }
        }
        return Set.copyOf(actionIds);
    }

    private boolean isSidebarTrackableAction(String actionId) {
        String normalized = normalizeActionIdForState(actionId);
        return normalized != null && sidebarTrackableActionIds.contains(normalized);
    }

    private String normalizeActionIdForState(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        return actionId.trim().toLowerCase(Locale.ROOT);
    }

    private String canonicalActionId(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        return actionId.trim();
    }

    private void setSidebarNodeVisibility(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void setNodeVisibility(Node node, boolean visible) {
        setSidebarNodeVisibility(node, visible);
    }

    private void handleLeftNavControlKeyPressed(KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        if (event.getCode() == KeyCode.TAB) {
            if (cycleLeftNavFocus(event)) {
                event.consume();
            }
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE && handleLeftNavEscape()) {
            event.consume();
        }
    }

    private boolean cycleLeftNavFocus(KeyEvent event) {
        List<Node> targets = buildLeftNavFocusCycleTargets();
        if (targets.size() < 2) {
            return false;
        }
        Node sourceNode = event.getTarget() instanceof Node node ? node : null;
        int index = findLeftNavFocusTargetIndex(targets, sourceNode);
        if (index < 0 && getScene() != null) {
            index = findLeftNavFocusTargetIndex(targets, getScene().getFocusOwner());
        }
        if (index < 0) {
            index = 0;
        }
        int nextIndex = event.isShiftDown()
            ? Math.floorMod(index - 1, targets.size())
            : Math.floorMod(index + 1, targets.size());
        Node next = targets.get(nextIndex);
        if (next == null) {
            return false;
        }
        Platform.runLater(next::requestFocus);
        return true;
    }

    private int findLeftNavFocusTargetIndex(List<Node> targets, Node source) {
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

    private List<Node> buildLeftNavFocusCycleTargets() {
        List<Node> targets = new ArrayList<>(32);
        addLeftNavFocusTarget(targets, sidebarToggleBtn);
        for (SidebarRailDomain domain : SidebarRailDomain.values()) {
            addLeftNavFocusTarget(targets, navigationRailButtons.get(domain));
        }
        addLeftNavFocusTarget(targets, sidebarFilterField);
        addLeftNavFocusTarget(targets, sidebarFilterClearButton);
        for (Button button : sidebarQuickActionButtons) {
            addLeftNavFocusTarget(targets, button);
        }
        addLeftNavFocusTarget(targets, sidebarGuidedHintPrimaryBtn);
        addLeftNavFocusTarget(targets, sidebarGuidedHintPaletteBtn);
        addLeftNavFocusTarget(targets, sidebarGuidedHintDismissBtn);
        addButtonsAsLeftNavFocusTargets(targets, sidebarFavoritesBox);
        addButtonsAsLeftNavFocusTargets(targets, sidebarRecentBox);
        for (Button button : sidebarFilterResultButtons) {
            addLeftNavFocusTarget(targets, button);
        }
        for (Button button : contextSidebarDomainButtons) {
            addLeftNavFocusTarget(targets, button);
        }
        return targets;
    }

    private void addButtonsAsLeftNavFocusTargets(List<Node> targets, VBox container) {
        if (container == null) {
            return;
        }
        for (Node child : container.getChildren()) {
            if (child instanceof Button button) {
                addLeftNavFocusTarget(targets, button);
            }
        }
    }

    private void addLeftNavFocusTarget(List<Node> targets, Node node) {
        if (targets == null || node == null || targets.contains(node)) {
            return;
        }
        if (!isFocusableNode(node)) {
            return;
        }
        targets.add(node);
    }

    private boolean handleLeftNavEscape() {
        if (overlayDialogManager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE)) {
            return false;
        }
        if (sidebarFilterField != null
            && sidebarFilterField.isVisible()
            && sidebarFilterField.isManaged()
            && sidebarFilterField.getText() != null
            && !sidebarFilterField.getText().isBlank()) {
            sidebarFilterField.clear();
            sidebarFilterField.requestFocus();
            return true;
        }
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy == null
            ? mainLayoutCoordinator.leftPanelDisplayPolicy()
            : leftPanelDisplayPolicy;
        if (policy == null || policy.sidebarMode() == LeftPanelSidebarMode.PINNED || isSidebarCollapsed) {
            return false;
        }
        captureSidebarFocusOwnerForRestore();
        mainLayoutCoordinator.toggleLeftPanelCollapsed();
        applyAdaptiveLayoutStateToShell(false);
        return true;
    }

    private void refreshSidebarButtonsPresentation() {
        for (Button button : new ArrayList<>(sidebarButtons)) {
            refreshSidebarButtonPresentation(button);
        }
        for (Button button : new ArrayList<>(sidebarFilterResultButtons)) {
            refreshSidebarButtonPresentation(button);
        }
    }

    private void refreshSidebarButtonPresentation(Button button) {
        if (button == null) {
            return;
        }
        Object actionIdRaw = button.getProperties().get(SIDEBAR_BUTTON_ACTION_ID);
        if (!(actionIdRaw instanceof String actionId) || actionId.isBlank()) {
            return;
        }

        String canonicalActionId = canonicalActionId(actionId);
        String baseLabel = resolveSidebarButtonBaseLabel(button, canonicalActionId);
        String shortcutHint = resolveSidebarShortcutHint(canonicalActionId);
        UiActionRegistry.ActionAvailability actionAvailability = commandActionRegistry.actionAvailability(canonicalActionId);
        boolean available = actionAvailability.registered() && actionAvailability.available();
        boolean hardDisabled = !actionAvailability.registered();
        String unavailableReason = available ? "" : actionAvailability.unavailableReason();

        if (!shortcutHint.isBlank()) {
            button.setText(baseLabel + "  " + shortcutHint);
        } else {
            button.setText(baseLabel);
        }

        button.setDisable(hardDisabled);
        if (available) {
            button.getStyleClass().remove("sidebar-btn-unavailable");
        } else if (!button.getStyleClass().contains("sidebar-btn-unavailable")) {
            button.getStyleClass().add("sidebar-btn-unavailable");
        }

        String tooltipText = buildSidebarTooltipText(
            canonicalActionId,
            shortcutHint,
            available,
            unavailableReason
        );
        if (!tooltipText.isBlank()) {
            Tooltip tooltip = button.getTooltip();
            if (tooltip == null) {
                tooltip = new Tooltip(tooltipText);
                tooltip.getStyleClass().add("sidebar-tooltip");
                tooltip.setShowDelay(javafx.util.Duration.millis(300));
                button.setTooltip(tooltip);
            } else {
                tooltip.setText(tooltipText);
            }
        }
    }

    private String resolveSidebarButtonBaseLabel(Button button, String actionId) {
        Object baseLabelRaw = button.getProperties().get(SIDEBAR_BUTTON_BASE_LABEL);
        if (baseLabelRaw instanceof String baseLabel && !baseLabel.isBlank()) {
            return baseLabel;
        }
        SidebarNavItem item = findSidebarNavItemByActionId(actionId);
        if (item != null) {
            button.getProperties().put(SIDEBAR_BUTTON_BASE_LABEL, item.label());
            return item.label();
        }
        String text = button.getText();
        if (text == null || text.isBlank()) {
            return actionId;
        }
        return text;
    }

    private String resolveSidebarShortcutHint(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return "";
        }
        return shortcutRegistry.findBindingByActionId(actionId)
            .map(ShortcutRegistry.ShortcutBinding::shortcut)
            .map(this::formatShortcutHint)
            .orElseGet(() -> commandActionRegistry.find(actionId)
                .map(UiActionRegistry.RegisteredAction::defaultShortcut)
                .map(this::formatShortcutHint)
                .orElse(""));
    }

    private String formatShortcutHint(String rawHint) {
        return ShortcutRegistry.toDisplayShortcutHint(rawHint);
    }

    private String buildSidebarTooltipText(
        String actionId,
        String shortcutHint,
        boolean available,
        String unavailableReason
    ) {
        String base = resolveSidebarTooltip(actionId);
        StringBuilder out = new StringBuilder();
        if (base != null && !base.isBlank()) {
            out.append(base.trim());
        }
//        SidebarNavItem navItem = findSidebarNavItemByActionId(actionId);
//        if (navItem != null && navItem.shortDescription() != null && !navItem.shortDescription().isBlank()) {
//            String shortDescription = navItem.shortDescription().trim();
//            SidebarRailDomain railDomain = resolveSidebarRailDomain(navItem);
//            if (!shortDescription.equalsIgnoreCase(navItem.label())) {
//                if (out.length() > 0) {
//                    out.append('\n');
//                }
//                out.append("Что делает: ").append(shortDescription);
//            }
//            if (out.length() > 0) {
//                out.append('\n');
//            }
//            out.append("Surface: ")
//                .append(formatSidebarSurfaceHintLabel(navItem.surfaceHint()))
//                .append(" • rail: ")
//                .append(railDomain == null ? "n/a" : railDomain.contextHeaderLabel())
//                .append(" • ПКМ: открыть через палитру/показать в context sidebar");
//        }
        if (shortcutHint != null && !shortcutHint.isBlank()) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("Shortcut: ").append(shortcutHint);
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append("Статус: ").append(available ? "доступно" : "недоступно");
        if (!available && unavailableReason != null && !unavailableReason.isBlank()) {
            out.append(" (").append(unavailableReason.trim()).append(')');
        }
        return out.toString();
    }

    private String formatSidebarSurfaceHintLabel(com.example.neuroflowplanner.ui.navigation.SidebarSurfaceHint surfaceHint) {
        if (surfaceHint == null) {
            return "sidebar/palette";
        }
        return switch (surfaceHint) {
            case SIDEBAR -> "sidebar";
            case PALETTE -> "palette";
            case BOTH -> "sidebar + palette";
        };
    }

    private SidebarNavItem findSidebarNavItemByActionId(String actionId) {
        String normalized = normalizeActionIdForState(actionId);
        if (normalized == null) {
            return null;
        }
        for (SidebarNavItem item : sidebarNavItems) {
            if (item == null) {
                continue;
            }
            if (normalized.equals(normalizeActionIdForState(item.actionId()))) {
                return item;
            }
        }
        return null;
    }

    private SidebarRailDomain resolveSidebarRailDomain(SidebarNavItem item) {
        if (item == null) {
            return null;
        }
        try {
            return sidebarNavigationService.resolveRailActionMapping(item).railDomain();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String resolveTwoTierSidebarRevealTargetHint(String actionId) {
        SidebarNavItem item = findSidebarNavItemByActionId(actionId);
        SidebarRailDomain railDomain = resolveSidebarRailDomain(item);
        if (railDomain == null) {
            return "rail/context sidebar";
        }
        return "домене \"" + railDomain.contextHeaderLabel() + "\"";
    }

    private boolean showActionInSidebar(String actionId) {
        SidebarNavItem item = findSidebarNavItemByActionId(actionId);
        if (item == null) {
            return false;
        }
        SidebarRailDomain targetDomain = sidebarNavigationService.resolveRailActionMapping(item).railDomain();
        mainLayoutCoordinator.selectNavigationRailDomain(targetDomain);
        renderedContextSidebarDomain = null;
        applyAdaptiveLayoutStateToShell(false);
        if (isSidebarCollapsed) {
            toggleSidebar();
        }

        if (sidebarNavigationService.isFilterEnabled() && sidebarFilterField != null) {
            sidebarFilterField.setText(item.actionId());
            sidebarFilterField.requestFocus();
            return true;
        }

        sidebarNavState = sidebarNavigationService.updateSectionExpanded(sidebarNavState, item.sectionId(), true);
        Button button = findSidebarButtonByActionId(actionId);
        if (button != null) {
            button.requestFocus();
            return true;
        }
        return false;
    }

    private Button findSidebarButtonByActionId(String actionId) {
        String normalized = normalizeActionIdForState(actionId);
        if (normalized == null) {
            return null;
        }
        for (Button button : sidebarButtons) {
            if (button == null) {
                continue;
            }
            Object actionIdRaw = button.getProperties().get(SIDEBAR_BUTTON_ACTION_ID);
            if (actionIdRaw instanceof String buttonActionId
                && normalized.equals(normalizeActionIdForState(buttonActionId))) {
                return button;
            }
        }
        return null;
    }

    private void openCommandPaletteForAction(String actionId) {
        if (!ConfigManager.isUxCommandPaletteEnabled()) {
            return;
        }
        String query = actionId == null ? "" : actionId.trim();
        openCommandPaletteWithQuery(query, false);
    }

    private HBox createSidebarFilterBar() {
        HBox filterBox = new HBox(6);
        filterBox.getStyleClass().add("sidebar-filter-box");
        filterBox.setAlignment(Pos.CENTER_LEFT);

        sidebarFilterField = new TextField();
        sidebarFilterField.getStyleClass().add("sidebar-filter-field");
        sidebarFilterField.setPromptText("Найти действие...");
        sidebarFilterField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        HBox.setHgrow(sidebarFilterField, Priority.ALWAYS);

        sidebarFilterClearButton = new Button();
        sidebarFilterClearButton.getStyleClass().add("sidebar-filter-clear-btn");
        sidebarFilterClearButton.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 14));
        sidebarFilterClearButton.setTooltip(new Tooltip("Очистить фильтр (Esc)"));
        sidebarFilterClearButton.setVisible(false);
        sidebarFilterClearButton.setManaged(false);
        sidebarFilterClearButton.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        sidebarFilterClearButton.setOnAction(e -> {
            sidebarFilterField.clear();
            sidebarFilterField.requestFocus();
        });

        sidebarFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
            boolean hasQuery = newValue != null && !newValue.isBlank();
            sidebarFilterClearButton.setVisible(hasQuery);
            sidebarFilterClearButton.setManaged(hasQuery);
            applySidebarFilter(newValue);
        });
        sidebarFilterField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSidebarFilterKeyPressed);

        filterBox.getChildren().addAll(sidebarFilterField, sidebarFilterClearButton);
        return filterBox;
    }

    private void handleSidebarFilterKeyPressed(KeyEvent event) {
        if (event == null || sidebarFilterField == null) {
            return;
        }
        if (!sidebarFilterResultsBox.isVisible() || sidebarFilterResultButtons.isEmpty()) {
            if (event.getCode() == KeyCode.ESCAPE && !sidebarFilterField.getText().isBlank()) {
                sidebarFilterField.clear();
                event.consume();
            }
            return;
        }
        switch (event.getCode()) {
            case DOWN -> {
                moveSidebarFilterSelection(1);
                event.consume();
            }
            case UP -> {
                moveSidebarFilterSelection(-1);
                event.consume();
            }
            case ENTER -> {
                int index = sidebarFilterSelectionIndex < 0 ? 0 : sidebarFilterSelectionIndex;
                if (index < sidebarFilterResultButtons.size()) {
                    sidebarFilterResultButtons.get(index).fire();
                }
                event.consume();
            }
            case ESCAPE -> {
                sidebarFilterField.clear();
                event.consume();
            }
            default -> {
                // No-op.
            }
        }
    }

    private void applySidebarFilter(String rawQuery) {
        if (!sidebarNavigationService.isFilterEnabled() || sidebarFilterResultsBox == null) {
            return;
        }
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            sidebarFilterResultsBox.setVisible(false);
            sidebarFilterResultsBox.setManaged(false);
            sidebarFilterSelectionIndex = -1;
            clearSidebarFilterResultButtons();
            setSidebarNodeVisibility(sidebarQuickActionsBox, true);
            setSidebarNodeVisibility(sidebarFavoritesLabel, sidebarNavigationService.isFavoritesEnabled());
            setSidebarNodeVisibility(sidebarFavoritesBox, sidebarNavigationService.isFavoritesEnabled());
            setSidebarNodeVisibility(sidebarRecentLabel, sidebarNavigationService.isRecentEnabled());
            setSidebarNodeVisibility(sidebarRecentBox, sidebarNavigationService.isRecentEnabled());
            setSidebarNodeVisibility(sidebarScrollPane, true);
            setSidebarNodeVisibility(contextSidebarDomainHeaderBox, !isSidebarCollapsed);
            setSidebarNodeVisibility(contextSidebarFooterBox, !isSidebarCollapsed);
            refreshSidebarPersonalizationZones();
            return;
        }

        List<SidebarNavItem> matched = sidebarNavigationService.filterItems(sidebarNavItems, sidebarNavSections, query);
        setSidebarNodeVisibility(sidebarQuickActionsBox, false);
        setSidebarNodeVisibility(sidebarFavoritesLabel, false);
        setSidebarNodeVisibility(sidebarFavoritesBox, false);
        setSidebarNodeVisibility(sidebarRecentLabel, false);
        setSidebarNodeVisibility(sidebarRecentBox, false);
        setSidebarNodeVisibility(sidebarScrollPane, false);
        setSidebarNodeVisibility(contextSidebarDomainHeaderBox, false);
        setSidebarNodeVisibility(contextSidebarFooterBox, false);
        sidebarFilterResultsBox.setVisible(true);
        sidebarFilterResultsBox.setManaged(true);
        clearSidebarFilterResultButtons();

        if (matched.isEmpty()) {
            sidebarFilterResultsLabel.setText("Совпадений не найдено");
            sidebarFilterSelectionIndex = -1;
            return;
        }

        int resultCount = Math.min(SIDEBAR_FILTER_MAX_RESULTS, matched.size());
        sidebarFilterResultsLabel.setText("Найдено: " + matched.size() + " (показано " + resultCount + ")");
        for (int i = 0; i < resultCount; i++) {
            SidebarNavItem item = matched.get(i);
            Button resultButton = createSidebarFilterResultButton(item);
            sidebarFilterResultButtons.add(resultButton);
            sidebarFilterResultsBox.getChildren().add(resultButton);
        }
        setSidebarFilterSelectionIndex(0);
    }

    private void clearSidebarFilterResultButtons() {
        if (sidebarFilterResultsBox == null) {
            return;
        }
        sidebarFilterResultsBox.getChildren().retainAll(sidebarFilterResultsLabel);
        sidebarFilterResultButtons.clear();
    }

    private void moveSidebarFilterSelection(int delta) {
        if (sidebarFilterResultButtons.isEmpty()) {
            sidebarFilterSelectionIndex = -1;
            return;
        }
        int size = sidebarFilterResultButtons.size();
        int current = sidebarFilterSelectionIndex < 0 ? 0 : sidebarFilterSelectionIndex;
        int next = (current + delta) % size;
        if (next < 0) {
            next += size;
        }
        setSidebarFilterSelectionIndex(next);
    }

    private void setSidebarFilterSelectionIndex(int index) {
        if (sidebarFilterResultButtons.isEmpty()) {
            sidebarFilterSelectionIndex = -1;
            return;
        }
        int safeIndex = Math.max(0, Math.min(index, sidebarFilterResultButtons.size() - 1));
        sidebarFilterSelectionIndex = safeIndex;
        for (int i = 0; i < sidebarFilterResultButtons.size(); i++) {
            Button button = sidebarFilterResultButtons.get(i);
            if (i == safeIndex) {
                if (!button.getStyleClass().contains("sidebar-btn-filter-selected")) {
                    button.getStyleClass().add("sidebar-btn-filter-selected");
                }
            } else {
                button.getStyleClass().remove("sidebar-btn-filter-selected");
            }
        }
    }

    private void renderUnifiedSidebarSurfaceGroups(
        VBox sectionContent,
        List<SidebarNavSection> sections,
        Map<String, List<SidebarNavItem>> groupedItems
    ) {
        if (sectionContent == null || sections == null || sections.isEmpty()) {
            return;
        }
        Map<String, SidebarNavSection> sectionById = new LinkedHashMap<>();
        for (SidebarNavSection section : sections) {
            if (section == null) {
                continue;
            }
            sectionById.put(section.id(), section);
        }

        VBox workGroup = createSidebarSurfaceGroupCard(
            SIDEBAR_GROUP_WORK,
            "РАБОЧИЕ СЦЕНАРИИ",
            "Основные действия для ежедневной работы",
            SidebarNavZone.CORE,
            List.of("main", "tools"),
            sectionById,
            groupedItems
        );
        if (workGroup != null) {
            sectionContent.getChildren().add(workGroup);
        }

        VBox insightsGroup = createSidebarSurfaceGroupCard(
            SIDEBAR_GROUP_INSIGHTS,
            "АНАЛИТИКА / AI",
            "Обзор, аналитика и интеллектуальные сценарии",
            SidebarNavZone.ADVANCED,
            List.of("analysis", "ai"),
            sectionById,
            groupedItems
        );
        if (insightsGroup != null) {
            sectionContent.getChildren().add(insightsGroup);
        }

        VBox systemGroup = createSidebarSurfaceGroupCard(
            SIDEBAR_GROUP_SYSTEM,
            "СИСТЕМА И РЕДКИЕ",
            "Администрирование, массовые и редкие операции",
            SidebarNavZone.ADVANCED,
            List.of("manage", "bulk", "system"),
            sectionById,
            groupedItems
        );
        if (systemGroup != null) {
            sectionContent.getChildren().add(systemGroup);
        }
    }

    private VBox createSidebarSurfaceGroupCard(
        String groupId,
        String title,
        String subtitle,
        SidebarNavZone zone,
        List<String> sectionIds,
        Map<String, SidebarNavSection> sectionById,
        Map<String, List<SidebarNavItem>> groupedItems
    ) {
        if (groupId == null || sectionIds == null || sectionIds.isEmpty()) {
            return null;
        }
        VBox content = new VBox(4);
        content.getStyleClass().add("sidebar-surface-group-content");
        int totalItems = 0;
        List<SidebarNavItem> summaryItems = new ArrayList<>();
        for (String sectionId : sectionIds) {
            SidebarNavSection section = sectionById.get(sectionId);
            if (section == null) {
                continue;
            }
            List<SidebarNavItem> sectionItems = groupedItems.getOrDefault(section.id(), List.of());
            if (sectionItems.isEmpty()) {
                continue;
            }
            totalItems += sectionItems.size();
            for (SidebarNavItem item : sectionItems) {
                if (summaryItems.size() >= 3) {
                    break;
                }
                summaryItems.add(item);
            }
            VBox sectionBox = createCollapsibleSidebarSection(section, sectionItems, true);
            content.getChildren().add(sectionBox);
        }
        if (content.getChildren().isEmpty()) {
            return null;
        }

        VBox card = new VBox(6);
        card.getStyleClass().add("sidebar-surface-group");
        if (zone == SidebarNavZone.ADVANCED) {
            card.getStyleClass().add("sidebar-surface-group-secondary");
        }
        card.setPadding(new Insets(8, 8, 8, 8));

        Label titleLabel = new Label(title == null ? "СЦЕНАРИИ" : title);
        titleLabel.getStyleClass().add("sidebar-surface-group-title");

        Label subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        subtitleLabel.getStyleClass().add("sidebar-surface-group-subtitle");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMinWidth(0);

        VBox titleBox = new VBox(2, titleLabel, subtitleLabel);
        titleBox.getStyleClass().add("sidebar-surface-group-titlebox");
        titleBox.setMinWidth(0);

        Label summaryLabel = new Label(buildSidebarSurfaceGroupSummary(totalItems, summaryItems));
        summaryLabel.getStyleClass().add("sidebar-surface-group-summary");
        summaryLabel.setWrapText(false);
        summaryLabel.setMinWidth(0);
        summaryLabel.setMaxWidth(220);
        summaryLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        FontIcon chevronIcon = FontIcon.of(MaterialDesignC.CHEVRON_DOWN, 14);
        Button toggleBtn = new Button();
        toggleBtn.getStyleClass().add("sidebar-surface-group-toggle");
        toggleBtn.setGraphic(chevronIcon);
        toggleBtn.setFocusTraversable(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(8, titleBox, spacer, summaryLabel, toggleBtn);
        header.getStyleClass().add("sidebar-surface-group-header");
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        sidebarSectionLabels.add(header);

        boolean expanded = sidebarSurfaceGroupExpandedState.getOrDefault(groupId, defaultSidebarSurfaceGroupExpanded(groupId));
        applySidebarSurfaceGroupExpandedVisualState(groupId, content, chevronIcon, summaryLabel, expanded);

        Runnable toggleAction = () -> {
            boolean next = !content.isManaged();
            sidebarSurfaceGroupExpandedState.put(groupId, next);
            applySidebarSurfaceGroupExpandedVisualState(groupId, content, chevronIcon, summaryLabel, next);
        };
        toggleBtn.setOnAction(e -> toggleAction.run());
        titleBox.setOnMouseClicked(e -> toggleAction.run());
        titleLabel.setOnMouseClicked(e -> toggleAction.run());
        subtitleLabel.setOnMouseClicked(e -> toggleAction.run());
        summaryLabel.setOnMouseClicked(e -> toggleAction.run());
        spacer.setOnMouseClicked(e -> toggleAction.run());

        card.getChildren().addAll(header, content);
        sidebarSurfaceGroupContentNodes.put(groupId, content);
        sidebarSurfaceGroupSummaryLabels.put(groupId, summaryLabel);
        sidebarSurfaceGroupChevronIcons.put(groupId, chevronIcon);
        sidebarSurfaceGroupCards.put(groupId, card);
        if (zone != null) {
            sidebarZoneContentNodes.computeIfAbsent(zone, ignored -> new ArrayList<>()).add(card);
        }
        return card;
    }

    private String buildSidebarSurfaceGroupSummary(int totalItems, List<SidebarNavItem> summaryItems) {
        List<String> labels = new ArrayList<>();
        if (summaryItems != null) {
            for (SidebarNavItem item : summaryItems) {
                if (item == null || item.label() == null || item.label().isBlank()) {
                    continue;
                }
                labels.add(item.label());
            }
        }
        String prefix = totalItems > 0 ? (totalItems + " действий") : "Сценарии";
        if (labels.isEmpty()) {
            return prefix;
        }
        return prefix + " • " + String.join(" • ", labels);
    }

    private boolean defaultSidebarSurfaceGroupExpanded(String groupId) {
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy == null ? mainLayoutCoordinator.leftPanelDisplayPolicy() : leftPanelDisplayPolicy;
        boolean compact = policy != null && policy.heightCompactionApplied();
        boolean aggressive = policy != null && policy.aggressiveCompaction();
        return switch (groupId) {
            case SIDEBAR_GROUP_WORK -> true;
            case SIDEBAR_GROUP_INSIGHTS -> !aggressive;
            case SIDEBAR_GROUP_SYSTEM -> !compact && !aggressive && adaptiveLayoutState.breakpoint() == UiLayoutBreakpoint.WIDE;
            default -> !compact;
        };
    }

    private void applySidebarSurfaceGroupExpandedVisualState(
        String groupId,
        VBox content,
        FontIcon chevronIcon,
        Label summaryLabel,
        boolean expanded
    ) {
        if (content != null) {
            content.setManaged(expanded);
            content.setVisible(expanded);
        }
        if (chevronIcon != null) {
            chevronIcon.setIconCode(expanded ? MaterialDesignC.CHEVRON_DOWN : MaterialDesignC.CHEVRON_RIGHT);
        }
        if (summaryLabel != null) {
            boolean keepSummaryVisible = !expanded || SIDEBAR_GROUP_SYSTEM.equals(groupId);
            summaryLabel.setManaged(true);
            summaryLabel.setVisible(keepSummaryVisible);
        }
    }

    private VBox createSidebarGuidedHintCard() {
        VBox card = new VBox(4);
        card.getStyleClass().add("sidebar-guided-hint-card");
        card.setPadding(new Insets(5, 8, 5, 8));
        sidebarGuidedHintCard = card;

        Label title = new Label("С чего начать");
        title.getStyleClass().add("sidebar-guided-hint-title");

        sidebarGuidedHintBodyLabel = new Label(
            "Добавьте задачу или используйте Ctrl/Cmd+K для редких команд."
        );
        sidebarGuidedHintBodyLabel.getStyleClass().add("sidebar-guided-hint-body");
        sidebarGuidedHintBodyLabel.setWrapText(false);
        sidebarGuidedHintBodyLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        sidebarGuidedHintPrimaryBtn = new Button("Добавить задачу");
        sidebarGuidedHintPrimaryBtn.getStyleClass().addAll("sidebar-guided-hint-btn", "sidebar-guided-hint-btn-primary");
        sidebarGuidedHintPrimaryBtn.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        sidebarGuidedHintPrimaryBtn.setOnAction(e -> executeSidebarGuidedHintAction("main.inbox.addTask"));

        sidebarGuidedHintPaletteBtn = new Button("Редкие команды");
        sidebarGuidedHintPaletteBtn.getStyleClass().addAll("sidebar-guided-hint-btn", "sidebar-guided-hint-btn-secondary");
        sidebarGuidedHintPaletteBtn.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        sidebarGuidedHintPaletteBtn.setTooltip(new Tooltip("Открыть командную палитру (Ctrl/Cmd+K)"));
        sidebarGuidedHintPaletteBtn.setOnAction(e -> openCommandPaletteWithQuery("", false));

        Region actionsSpacer = new Region();
        HBox.setHgrow(actionsSpacer, Priority.ALWAYS);

        sidebarGuidedHintDismissBtn = new Button("Скрыть");
        sidebarGuidedHintDismissBtn.getStyleClass().addAll("sidebar-guided-hint-btn", "sidebar-guided-hint-btn-dismiss");
        sidebarGuidedHintDismissBtn.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        sidebarGuidedHintDismissBtn.setOnAction(e -> {
            mainLayoutCoordinator.setNavHelperHintDismissed(SIDEBAR_GUIDED_HINT_NOVICE_ID, true);
            applyAdaptiveLayoutStateToShell(true);
        });

        HBox actions = new HBox(4, sidebarGuidedHintPrimaryBtn, sidebarGuidedHintPaletteBtn, actionsSpacer, sidebarGuidedHintDismissBtn);
        actions.getStyleClass().add("sidebar-guided-hint-actions");
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, sidebarGuidedHintBodyLabel, actions);
        return card;
    }

    private void executeSidebarGuidedHintAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return;
        }
        if (!commandActionRegistry.isRegistered(actionId)) {
            showAlert("Действие не зарегистрировано");
            return;
        }
        UndoRedoManager.CommandResult result = commandActionRegistry.execute(actionId);
        if (!result.successful() && result.message() != null && !result.message().isBlank()) {
            showAlert(result.message());
        }
    }

    private Label createZoneAnchorLabel(SidebarNavZone zone) {
        Label anchor = new Label(
            switch (zone) {
                case CORE -> "ОСНОВНЫЕ СЦЕНАРИИ";
                case ADVANCED -> "РАСШИРЕННЫЕ СЦЕНАРИИ";
                case QUICK -> "БЫСТРЫЙ ДОСТУП";
            }
        );
        anchor.getStyleClass().add("sidebar-zone-anchor");
        return anchor;
    }

    private VBox createCollapsibleSidebarSection(
        SidebarNavSection section,
        List<SidebarNavItem> sectionItems
    ) {
        return createCollapsibleSidebarSection(section, sectionItems, false);
    }

    private VBox createCollapsibleSidebarSection(
        SidebarNavSection section,
        List<SidebarNavItem> sectionItems,
        boolean nestedInGroup
    ) {
        VBox sectionBox = new VBox(4);
        sectionBox.getStyleClass().add("sidebar-nav-section");
        if (nestedInGroup) {
            sectionBox.getStyleClass().add("sidebar-nav-section-nested");
        }
        sectionBox.setPadding(new Insets(8, 0, 6, 0));

        Label titleLabel = new Label(section.label().toUpperCase(java.util.Locale.ROOT));
        titleLabel.getStyleClass().add("sidebar-section-label");

        Label summaryLabel = new Label(buildSidebarSectionSummary(sectionItems));
        summaryLabel.getStyleClass().add("sidebar-section-summary");
        summaryLabel.setWrapText(false);
        summaryLabel.setMinWidth(0);
        summaryLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        FontIcon chevronIcon = FontIcon.of(MaterialDesignC.CHEVRON_DOWN, 14);
        Button toggleButton = new Button();
        toggleButton.getStyleClass().add("sidebar-section-toggle");
        toggleButton.setGraphic(chevronIcon);
        toggleButton.setFocusTraversable(false);
        toggleButton.setDisable(!section.collapsible());
        toggleButton.setManaged(section.collapsible());
        toggleButton.setVisible(section.collapsible());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox header = new HBox(6);
        header.getStyleClass().add("sidebar-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(titleLabel, summaryLabel, spacer, toggleButton);
        HBox.setHgrow(summaryLabel, Priority.ALWAYS);
        sidebarSectionLabels.add(header);

        VBox itemsBox = new VBox(4);
        itemsBox.getStyleClass().add("sidebar-section-content");
        for (SidebarNavItem item : sectionItems) {
            Button button = createSidebarButtonFromModelItem(item);
            if (button != null) {
                itemsBox.getChildren().add(button);
            }
        }
        if ("history".equals(section.id())) {
            if (!undoRedoStateLabel.getStyleClass().contains("sidebar-subtitle")) {
                undoRedoStateLabel.getStyleClass().add("sidebar-subtitle");
            }
            undoRedoStateLabel.setWrapText(true);
            itemsBox.getChildren().add(undoRedoStateLabel);
        }

        boolean expanded = !section.collapsible() || sidebarNavState.isSectionExpanded(section.id());
        applySectionExpandedVisualState(itemsBox, chevronIcon, summaryLabel, expanded);

        if (section.collapsible()) {
            Runnable toggleHandler = () -> {
                boolean nextExpanded = !itemsBox.isManaged();
                applySectionExpandedVisualState(itemsBox, chevronIcon, summaryLabel, nextExpanded);
                sidebarNavState = sidebarNavigationService.updateSectionExpanded(
                    sidebarNavState,
                    section.id(),
                    nextExpanded
                );
            };
            toggleButton.setOnAction(e -> toggleHandler.run());
            titleLabel.setOnMouseClicked(e -> toggleHandler.run());
            spacer.setOnMouseClicked(e -> toggleHandler.run());
        }

        sectionBox.getChildren().addAll(header, itemsBox);
        return sectionBox;
    }

    private String buildSidebarSectionSummary(List<SidebarNavItem> sectionItems) {
        if (sectionItems == null || sectionItems.isEmpty()) {
            return "";
        }
        List<String> labels = new ArrayList<>();
        for (SidebarNavItem item : sectionItems) {
            if (item == null || item.label() == null || item.label().isBlank()) {
                continue;
            }
            labels.add(item.label());
            if (labels.size() >= 2) {
                break;
            }
        }
        if (labels.isEmpty()) {
            return sectionItems.size() + " действий";
        }
        if (sectionItems.size() <= labels.size()) {
            return String.join(" • ", labels);
        }
        return String.join(" • ", labels) + " +" + (sectionItems.size() - labels.size());
    }

    private void applySectionExpandedVisualState(VBox sectionContent, FontIcon chevronIcon, boolean expanded) {
        applySectionExpandedVisualState(sectionContent, chevronIcon, null, expanded);
    }

    private void applySectionExpandedVisualState(
        VBox sectionContent,
        FontIcon chevronIcon,
        Label summaryLabel,
        boolean expanded
    ) {
        sectionContent.setManaged(expanded);
        sectionContent.setVisible(expanded);
        chevronIcon.setIconCode(expanded ? MaterialDesignC.CHEVRON_DOWN : MaterialDesignC.CHEVRON_RIGHT);
        if (summaryLabel != null) {
            boolean showSummary = !expanded;
            summaryLabel.setManaged(showSummary);
            summaryLabel.setVisible(showSummary);
        }
    }

    private VBox createSidebarHeader() {
        VBox headerBox = new VBox(10);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setPadding(new Insets(0, 0, 20, 0));

        sidebarToggleBtn = new Button();
        sidebarToggleBtn.setGraphic(FontIcon.of(MaterialDesignM.MENU, 20));
        sidebarToggleBtn.getStyleClass().add("sidebar-toggle-btn");
        sidebarToggleBtn.setTooltip(new Tooltip("Свернуть/Развернуть меню"));
        sidebarToggleBtn.setOnAction(e -> toggleSidebar());
        sidebarToggleBtn.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        StackPane.setAlignment(sidebarToggleBtn, Pos.TOP_LEFT);

        sidebarHeaderContent = new VBox(10);
        sidebarHeaderContent.setAlignment(Pos.CENTER);
        sidebarHeaderContent.getStyleClass().add("sidebar-brand-box");
        sidebarHeaderContent.setFocusTraversable(true);

//        try {
//            String logoPath = ConfigManager.isDarkTheme()
//                ? "/com/example/neuroflowplanner/images/logo_mocha.png"
//                : "/com/example/neuroflowplanner/images/logo_latte.png";
//            ImageView logoImg = new ImageView(new Image(getClass().getResourceAsStream(logoPath)));
//            logoImg.setFitHeight(100);
//            logoImg.setPreserveRatio(true);
//            sidebarHeaderContent.getChildren().add(logoImg);
//        } catch (Exception ignored) {
//            // Ignore logo load failures and keep textual header.
//        }

        Label appName = new Label("НейроПоток");
        appName.getStyleClass().add("logo");
        Label subtitle = new Label("ИИ-Планировщик");
        subtitle.getStyleClass().add("subtitle");

        Runnable openTaskPanel = () -> {
            final String actionId = "main.task.panel";
            if (commandActionRegistry.isRegistered(actionId)) {
                UndoRedoManager.CommandResult result = commandActionRegistry.execute(actionId);
                if (!result.successful() && result.message() != null && !result.message().isBlank()) {
                    showAlert(result.message());
                }
                return;
            }
            showTaskPanelWithoutClosingInlineTabs();
            refreshTree();
        };
        Tooltip.install(sidebarHeaderContent, new Tooltip("Перейти на панель задач"));
        sidebarHeaderContent.setOnMouseClicked(event -> openTaskPanel.run());
        sidebarHeaderContent.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                openTaskPanel.run();
                event.consume();
            }
        });

//        leftPanelModeLabel = new Label("LEFT • COLLAPSIBLE");
//        leftPanelModeLabel.getStyleClass().add("subtitle");
//        leftPanelStateLabel = new Label("COLLAPSED");
//        leftPanelStateLabel.getStyleClass().add("subtitle");
//        commandPaletteOverlayStateLabel = new Label("PALETTE • CLOSED");
//        commandPaletteOverlayStateLabel.getStyleClass().add("subtitle");

        sidebarHeaderContent.getChildren().addAll(
            appName,
            subtitle
//            leftPanelModeLabel,
//            leftPanelStateLabel,
//            commandPaletteOverlayStateLabel
        );

        headerBox.getChildren().addAll(sidebarToggleBtn, sidebarHeaderContent);
        return headerBox;
    }

    private ScrollPane createSidebarScrollPane(VBox sidebarContent) {
        sidebarScrollPane = new ScrollPane(sidebarContent);
        sidebarScrollPane.setFitToWidth(true);
        sidebarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sidebarScrollPane.getStyleClass().add("sidebar-scroll");
        return sidebarScrollPane;
    }

    private Button createSidebarButtonFromModelItem(SidebarNavItem item) {
        return createSidebarButtonFromModelItem(item, true, true);
    }

    private Button createSidebarFilterResultButton(SidebarNavItem item) {
        Button button = createSidebarButtonFromModelItem(item, false, false);
        if (button != null) {
            if (!button.getStyleClass().contains("sidebar-btn-filter-match")) {
                button.getStyleClass().add("sidebar-btn-filter-match");
            }
            EventHandler<javafx.event.ActionEvent> delegate = button.getOnAction();
            button.setOnAction(e -> {
                if (delegate != null) {
                    delegate.handle(e);
                }
                if (sidebarFilterField != null) {
                    sidebarFilterField.clear();
                }
            });
        }
        return button;
    }

    private Button createSidebarButtonFromModelItem(
        SidebarNavItem item,
        boolean trackForSidebarCollapse,
        boolean registerUndoRedoButtons
    ) {
        if (item == null) {
            return null;
        }
        String actionId = item.actionId();
        Button button = createSidebarButton(
            item.label(),
            resolveSidebarIcon(actionId),
            resolveSidebarButtonStyle(actionId),
            resolveSidebarTooltip(actionId),
            trackForSidebarCollapse
        );
        button.getProperties().put(SIDEBAR_BUTTON_ACTION_ID, canonicalActionId(actionId));
        button.getProperties().put(SIDEBAR_BUTTON_BASE_LABEL, item.label());
        button.setOnAction(e -> {
            if (!commandActionRegistry.isRegistered(actionId)) {
                showAlert("Действие не зарегистрировано");
                return;
            }
            UndoRedoManager.CommandResult result = commandActionRegistry.execute(actionId);
            if (result.successful()) {
                syncPaletteHistoryFromExternalAction(actionId);
            }
            if (!result.successful() && result.message() != null && !result.message().isBlank()) {
                showAlert(result.message());
            }
        });
        button.setOnMouseEntered(e -> refreshSidebarButtonPresentation(button));
        refreshSidebarButtonPresentation(button);
        button.addEventFilter(KeyEvent.KEY_PRESSED, this::handleLeftNavControlKeyPressed);
        attachSidebarFavoriteContextMenu(button, item);

        if (registerUndoRedoButtons) {
            if (MAIN_ACTION_UNDO.equals(actionId)) {
                undoActionButton = button;
            } else if (MAIN_ACTION_REDO.equals(actionId)) {
                redoActionButton = button;
            }
        }
        return button;
    }

    private String resolveSidebarButtonStyle(String actionId) {
        return switch (actionId) {
            case "main.task.panel", "main.ai.chat" -> "sidebar-btn-primary";
            case "main.inbox.addTask" -> "sidebar-btn-success";
            case "main.task.filter.urgent", "main.task.bulk.delete" -> "sidebar-btn-danger";
            default -> "sidebar-btn";
        };
    }

    private String resolveSidebarTooltip(String actionId) {
        return switch (actionId) {
            case MAIN_ACTION_UNDO -> "Отменить последнее действие (Ctrl/Cmd+Z)";
            case MAIN_ACTION_REDO -> "Повторить отменённое действие (Ctrl/Cmd+Shift+Z)";
            case MAIN_ACTION_OPEN_PALETTE -> "Командная палитра (Ctrl/Cmd+K)";
            case MAIN_ACTION_FOCUS_GLOBAL_SEARCH -> "Глобальный поиск (Ctrl/Cmd+F)";
            case MAIN_ACTION_SHORTCUTS_HELP -> "Показать все горячие клавиши";

            case "main.task.panel" -> "Вернуться к списку задач";
            case "main.inbox.addTask" -> "Создание новой задачи";
            case "main.task.addSubtask" -> "Создание подзадачи для выбранной";
            case "main.task.listAll" -> "Полный список всех задач";
            case "main.task.filter.scheduled" -> "Задачи с будущей датой старта";
            case "main.view.calendar" -> "Календарный вид задач";
            case "main.view.kanban" -> "Доска с колонками по статусам";
            case "main.view.gantt" -> "Временная шкала задач";

            case "main.tools.notes.open" -> "Заметки с ИИ-помощником";
            case "main.tools.pomodoro" -> "Таймер для фокусировки";
            case "main.tools.timeTracker" -> "Учет времени работы над задачами";
            case "main.tools.workHours" -> "Настройка рабочего времени";
            case "main.tools.template.create" -> "Создание задачи из шаблона";
            case "main.tools.template.save" -> "Сохранить задачу как шаблон";
            case "main.tools.importTasks" -> "Импорт задач из JSON или CSV";

            case "main.analytics.dashboard" -> "Обзор ключевых показателей";
            case "main.analytics.dailyReview" -> "Сводка дня: риски, дедлайны и свободные окна";
            case "main.analytics.focusBlocks" -> "Рекомендации лучших окон для глубокой работы";
            case "main.analytics.planningQuality" -> "Точность оценки времени, переносы и стабильность ритма";
            case "main.analytics.statistics" -> "Общая статистика продуктивности";
            case "main.analytics.personalInsights" -> "Личный ритм и рекомендации";
            case "main.analytics.goals" -> "Недельные и месячные цели";
            case "main.analytics.timeStats" -> "Анализ затрат времени";
            case "main.analytics.workload" -> "Прогноз нагрузки на месяц";
            case "main.analytics.heatmap" -> "История активности по дням";
            case "main.analytics.projectProgress" -> "Статус задач с подзадачами";

            case "main.ai.chat" -> "Чат с умным помощником";
            case "main.ai.analyzeCenter" -> "ИИ-центр анализа задач";
            case "main.ai.reminders" -> "Умные уведомления о дедлайнах";
            case "main.ai.autoPrioritize" -> "Автоматическая расстановка приоритетов";
            case "main.ai.autoSchedule" -> "ИИ составляет расписание";
            case "main.ai.categorization" -> "Автоматическое распределение по категориям";

            case "main.task.filter.urgent" -> "Фильтр задач с высоким приоритетом";
            case "main.task.filter.tag" -> "Фильтр задач по тегу";
            case "main.task.archive.selected" -> "Перемещение задачи в архив";
            case "main.task.archive.show" -> "Просмотр архивированных задач";
            case "main.task.dependency.link" -> "Связать выбранную задачу с блокирующей";
            case "main.task.dependency.unlink" -> "Удалить связь выбранной задачи";
            case "main.task.dependency.details" -> "Показать блокирующие и зависимые задачи";

            case "main.task.bulk.archive" -> "Массовая архивация";
            case "main.task.bulk.delete" -> "Массовое удаление";
            case "main.task.bulk.tag" -> "Массовое добавление тега";

            case "main.system.export" -> "Сохранение данных в файл";
            case "main.system.settings" -> "Параметры приложения";
            case "main.system.help" -> "Руководство пользователя";
            default -> "";
        };
    }

    private Runnable resolveSidebarAction(String actionId) {
        return switch (actionId) {
            case MAIN_ACTION_UNDO -> this::handleUndoAction;
            case MAIN_ACTION_REDO -> this::handleRedoAction;
            case MAIN_ACTION_OPEN_PALETTE -> this::openCommandPalette;
            case MAIN_ACTION_FOCUS_GLOBAL_SEARCH -> this::focusGlobalSearch;
            case MAIN_ACTION_SHORTCUTS_HELP -> this::showShortcutsHelp;

            case "main.task.panel", "main.task.listAll" -> () -> {
                showTaskPanelWithoutClosingInlineTabs();
                showAllTasks();
            };
            case "main.inbox.addTask" -> () -> handleAddTask(null);
            case "main.task.addSubtask" -> this::handleAddSubtask;
            case "main.task.filter.scheduled" -> this::filterScheduled;
            case "main.view.calendar" -> () -> openInlineView("main.view.calendar", () -> CalendarDialog.inline(tasks));
            case "main.view.kanban" -> () -> openInlineView("main.view.kanban", () -> KanbanDialog.inline(tasks));
            case "main.view.gantt" -> () -> openInlineView("main.view.gantt", () -> GanttChartDialog.inline(tasks));

            case "main.analytics.dashboard" -> () -> openInlineView("main.analytics.dashboard", () -> DashboardDialog.inline(tasks));
            case "main.analytics.dailyReview" -> () -> openInlineView("main.analytics.dailyReview", this::buildDailyReviewDialog);
            case "main.analytics.focusBlocks" -> () -> openInlineView("main.analytics.focusBlocks", this::buildFocusBlockRecommendationDialog);
            case "main.analytics.planningQuality" -> () -> openInlineView("main.analytics.planningQuality", this::buildPlanningQualityDashboardDialog);
            case "main.analytics.statistics" -> () -> openInlineView("main.analytics.statistics", () -> StatisticsDialog.inline(tasks));
            case "main.analytics.personalInsights" -> () -> openInlineView("main.analytics.personalInsights", () -> PersonalInsightsDialog.inline(tasks));
            case "main.analytics.goals" -> () -> openInlineView("main.analytics.goals", GoalsDialog::inline);
            case "main.analytics.timeStats" -> () -> openInlineView("main.analytics.timeStats", () -> TimeStatsDialog.inline(tasks));
            case "main.analytics.workload" -> () -> openInlineView("main.analytics.workload", () -> WorkloadDialog.inline(tasks));
            case "main.analytics.heatmap" -> () -> openInlineView("main.analytics.heatmap", () -> HeatmapDialog.inline(tasks));
            case "main.analytics.projectProgress" -> () -> openInlineView("main.analytics.projectProgress", () -> ProjectProgressDialog.inline(tasks));

            case "main.tools.notes.open" -> this::openSmartNotesPanel;
            case "main.tools.pomodoro" -> () -> openInlineView("main.tools.pomodoro", PomodoroDialog::inline);
            case "main.tools.timeTracker" -> () -> openInlineView("main.tools.timeTracker", () -> TimeTrackerDialog.inline(tasks));
            case "main.tools.workHours" -> () -> openInlineView("main.tools.workHours", WorkHoursDialog::inline);
            case "main.tools.template.create" -> this::handleCreateFromTemplate;
            case "main.tools.template.save" -> this::handleSaveAsTemplate;
            case "main.tools.importTasks" -> this::handleImportTasks;

            case "main.ai.chat" -> () -> openInlineView("main.ai.chat", ChatBotDialog::inline);
            case "main.ai.analyzeCenter" -> () -> openInlineView("main.ai.analyzeCenter", () -> AIAnalysisDialog.inline(tasks));
            case "main.ai.reminders" -> () -> openInlineView("main.ai.reminders", () -> SmartRemindersDialog.inline(tasks));
            case "main.ai.autoPrioritize" -> this::handleAutoPrioritization;
            case "main.ai.autoSchedule" -> this::handleAutoSchedule;
            case "main.ai.categorization" -> () -> openInlineView("main.ai.categorization", () -> SmartCategorizationDialog.inline(tasks));

            case "main.task.filter.urgent" -> this::filterUrgent;
            case "main.task.filter.tag" -> this::filterByTag;
            case "main.task.archive.selected" -> this::handleArchiveTask;
            case "main.task.archive.show" -> this::showArchivedTasks;
            case "main.task.dependency.link" -> this::handleLinkDependency;
            case "main.task.dependency.unlink" -> this::handleUnlinkDependency;
            case "main.task.dependency.details" -> this::handleShowDependencyDetails;

            case "main.task.bulk.archive" -> this::bulkArchive;
            case "main.task.bulk.delete" -> this::bulkDelete;
            case "main.task.bulk.tag" -> this::bulkAddTag;

            case "main.system.export" -> this::openExportPanel;
            case "main.system.settings" -> () -> openInlineView("main.system.settings", SettingsDialog::inline);
            case "main.system.help" -> () -> openInlineView("main.system.help", HelpDialog::inline);
            default -> null;
        };
    }

    private void openInlineView(Supplier<InlineView> viewFactory) {
        openInlineView(InlineTabMetadata.global("inline.legacy.unknown"), viewFactory);
    }

    private void openInlineView(String actionId, Supplier<InlineView> viewFactory) {
        openInlineView(InlineTabMetadata.global(actionId), viewFactory);
    }

    private void openInlineViewContext(
        String actionId,
        String entityType,
        String entityId,
        Supplier<InlineView> viewFactory
    ) {
        openInlineView(InlineTabMetadata.context(actionId, entityType, entityId), viewFactory);
    }

    private InlineView openInlineView(InlineTabMetadata metadata, Supplier<InlineView> viewFactory) {
        InlineTabMetadata safeMetadata = metadata == null
            ? InlineTabMetadata.global("inline.legacy.unknown")
            : metadata;
        String precomputedTabId = buildInlineTabId(safeMetadata, null);
        InlineOverlayTab existing = inlineOverlayTabs.get(precomputedTabId);
        if (existing != null) {
            activateTab(precomputedTabId);
            if (existing.inlineView() instanceof ChatBotDialog chatBotDialog) {
                chatBotDialog.applyPendingLaunchRequestIfPresent();
            }
            return existing.inlineView();
        }

        if (viewFactory == null) {
            return null;
        }
        InlineView view = viewFactory.get();
        if (view == null) {
            return null;
        }
        return openInlineView(safeMetadata, view);
    }

    private InlineView openInlineView(InlineTabMetadata metadata, InlineView view) {
        if (view == null) {
            return null;
        }
        InlineTabMetadata safeMetadata = metadata == null
            ? InlineTabMetadata.global("inline.legacy.unknown")
            : metadata;
        String tabId = buildInlineTabId(safeMetadata, view);
        String title = resolveInlineTabTitle(view.getTitle(), view.getClass().getSimpleName());
        openOrActivateTab(tabId, view, title);
        return view;
    }

    private InlineView buildDailyReviewDialog() {
        return DailyReviewDialog.inline(this::openDailyReviewInChat, this::openFocusBlocksFromDailyReview);
    }

    private InlineView buildFocusBlockRecommendationDialog() {
        return FocusBlockRecommendationDialog.inline(
            this::openFocusBlocksInChat,
            this::openFocusBlocksDailyReview,
            this::openFocusBlocksAssistant
        );
    }

    private InlineView buildPlanningQualityDashboardDialog() {
        return PlanningQualityDashboardDialog.inline(this::openPlanningQualityInChat);
    }

    private void openDailyReviewInChat(DailyReviewResult result) {
        if (result == null) {
            return;
        }
        String conversationTitle = DailyReviewContentFormatter.buildExportTitle(result);
        String initialPrompt = DailyReviewContentFormatter.toChatSeedPrompt(result);
        ChatBotDialog.queueLaunchRequest(conversationTitle, initialPrompt);
        openInlineView("main.ai.chat", ChatBotDialog::inline);
    }

    private void openFocusBlocksFromDailyReview() {
        openInlineView("main.analytics.focusBlocks", this::buildFocusBlockRecommendationDialog);
    }

    private void openFocusBlocksDailyReview() {
        openInlineView("main.analytics.dailyReview", this::buildDailyReviewDialog);
    }

    private void openFocusBlocksAssistant() {
        openInlineView("main.ai.chat", ChatBotDialog::inline);
    }

    private void openFocusBlocksInChat(FocusBlockRecommendationResult result) {
        if (result == null) {
            return;
        }
        String conversationTitle = FocusBlockContentFormatter.buildExportTitle(result);
        String initialPrompt = FocusBlockContentFormatter.toChatSeedPrompt(result);
        ChatBotDialog.queueLaunchRequest(conversationTitle, initialPrompt);
        openInlineView("main.ai.chat", ChatBotDialog::inline);
    }

    private void openPlanningQualityInChat(PlanningQualityResult result) {
        if (result == null) {
            return;
        }
        String conversationTitle = PlanningQualityContentFormatter.buildExportTitle(result);
        String initialPrompt = PlanningQualityContentFormatter.toChatSeedPrompt(result);
        ChatBotDialog.queueLaunchRequest(conversationTitle, initialPrompt);
        openInlineView("main.ai.chat", ChatBotDialog::inline);
    }

    private Ikon resolveSidebarIcon(String actionId) {
        return switch (actionId) {
            case MAIN_ACTION_UNDO -> MaterialDesignU.UNDO_VARIANT;
            case MAIN_ACTION_REDO -> MaterialDesignR.REDO_VARIANT;
            case MAIN_ACTION_OPEN_PALETTE, MAIN_ACTION_FOCUS_GLOBAL_SEARCH -> MaterialDesignM.MAGNIFY;
            case MAIN_ACTION_SHORTCUTS_HELP -> MaterialDesignH.HELP_CIRCLE_OUTLINE;

            case "main.task.panel" -> MaterialDesignH.HOME;
            case "main.inbox.addTask" -> MaterialDesignP.PLUS_CIRCLE_OUTLINE;
            case "main.task.addSubtask" -> MaterialDesignP.PLUS_BOX_OUTLINE;
            case "main.task.listAll", "main.task.dependency.details" -> MaterialDesignV.VIEW_LIST_OUTLINE;
            case "main.task.filter.scheduled" -> MaterialDesignC.CALENDAR_CLOCK;
            case "main.view.calendar" -> MaterialDesignC.CALENDAR_MONTH;
            case "main.view.kanban" -> MaterialDesignV.VIEW_COLUMN;
            case "main.view.gantt" -> MaterialDesignC.CHART_GANTT;

            case "main.tools.notes.open" -> MaterialDesignN.NOTE_TEXT_OUTLINE;
            case "main.tools.pomodoro" -> MaterialDesignT.TIMER_OUTLINE;
            case "main.tools.timeTracker" -> MaterialDesignT.TIMER_SAND;
            case "main.tools.workHours" -> MaterialDesignC.CLOCK_TIME_EIGHT_OUTLINE;
            case "main.tools.template.create" -> MaterialDesignF.FILE_DOCUMENT_OUTLINE;
            case "main.tools.template.save" -> MaterialDesignC.CONTENT_SAVE_OUTLINE;
            case "main.tools.importTasks" -> MaterialDesignF.FILE_DOCUMENT_OUTLINE;

            case "main.analytics.dashboard" -> MaterialDesignV.VIEW_DASHBOARD;
            case "main.analytics.dailyReview" -> MaterialDesignW.WEATHER_SUNNY;
            case "main.analytics.focusBlocks" -> MaterialDesignT.TIMELINE_TEXT_OUTLINE;
            case "main.analytics.planningQuality" -> MaterialDesignG.GAUGE;
            case "main.analytics.statistics" -> MaterialDesignC.CHART_BAR;
            case "main.analytics.personalInsights" -> MaterialDesignL.LIGHTBULB_ON;
            case "main.analytics.goals" -> MaterialDesignT.TARGET;
            case "main.analytics.timeStats" -> MaterialDesignC.CLOCK_OUTLINE;
            case "main.analytics.workload" -> MaterialDesignC.CHART_LINE;
            case "main.analytics.heatmap" -> MaterialDesignG.GRID;
            case "main.analytics.projectProgress" -> MaterialDesignP.PROGRESS_CHECK;

            case "main.ai.chat" -> MaterialDesignC.CHAT;
            case "main.ai.analyzeCenter" -> MaterialDesignB.BRAIN;
            case "main.ai.reminders" -> MaterialDesignB.BELL_RING_OUTLINE;
            case "main.ai.autoPrioritize" -> MaterialDesignP.PRIORITY_HIGH;
            case "main.ai.autoSchedule" -> MaterialDesignC.CALENDAR_SYNC;
            case "main.ai.categorization" -> MaterialDesignS.SHAPE_OUTLINE;

            case "main.task.filter.urgent" -> MaterialDesignA.ALERT_CIRCLE_OUTLINE;
            case "main.task.filter.tag" -> MaterialDesignT.TAG_OUTLINE;
            case "main.task.archive.selected" -> MaterialDesignA.ARCHIVE_OUTLINE;
            case "main.task.archive.show" -> MaterialDesignA.ARCHIVE;
            case "main.task.dependency.link" -> MaterialDesignL.LINK_VARIANT;
            case "main.task.dependency.unlink" -> MaterialDesignD.DELETE_OUTLINE;

            case "main.task.bulk.archive" -> MaterialDesignA.ARCHIVE_ARROW_DOWN;
            case "main.task.bulk.delete" -> MaterialDesignD.DELETE_SWEEP;
            case "main.task.bulk.tag" -> MaterialDesignT.TAG_PLUS;

            case "main.system.export" -> MaterialDesignF.FILE_EXPORT_OUTLINE;
            case "main.system.settings" -> MaterialDesignC.COG_OUTLINE;
            case "main.system.help" -> MaterialDesignH.HELP_CIRCLE_OUTLINE;
            default -> MaterialDesignM.MENU;
        };
    }

    private void syncLayoutStateFromCoordinator() {
        adaptiveLayoutState = mainLayoutCoordinator.state();
        leftPanelDisplayPolicy = mainLayoutCoordinator.leftPanelDisplayPolicy();
        twoTierSidebarDisplayPolicy = mainLayoutCoordinator.twoTierSidebarDisplayPolicy();
        rightPanelDisplayMode = mainLayoutCoordinator.snapshot().rightContextMode();
        rightPanelDisplayPolicy = mainLayoutCoordinator.rightPanelDisplayPolicy();
        rightPanelInspectorDisplayPolicy = mainLayoutCoordinator.rightPanelInspectorDisplayPolicy();
    }

    private void toggleSidebar() {
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy == null
            ? mainLayoutCoordinator.leftPanelDisplayPolicy()
            : leftPanelDisplayPolicy;
        if (policy != null && policy.sidebarMode() == LeftPanelSidebarMode.OVERLAY) {
            openCommandPaletteWithQuery("", true);
            return;
        }
        boolean opening = isSidebarCollapsed;
        if (!opening) {
            captureSidebarFocusOwnerForRestore();
        }
        mainLayoutCoordinator.toggleLeftPanelCollapsed();
        applyAdaptiveLayoutStateToShell(false);
        if (opening) {
            restoreSidebarFocusAfterExpand();
        }
    }

    private void captureSidebarFocusOwnerForRestore() {
        if (getScene() == null) {
            return;
        }
        Node focusOwner = getScene().getFocusOwner();
        if (isNodeInside(focusOwner, sidebarContainer)) {
            sidebarPreviousFocusOwner = focusOwner;
        }
    }

    private void restoreSidebarFocusAfterExpand() {
        Node candidate = isFocusableNode(sidebarPreviousFocusOwner)
            ? sidebarPreviousFocusOwner
            : resolvePreferredLeftNavFocusTarget();
        if (candidate == null) {
            return;
        }
        Platform.runLater(candidate::requestFocus);
    }

    private Node resolvePreferredLeftNavFocusTarget() {
        if (isFocusableNode(sidebarFilterField)) {
            return sidebarFilterField;
        }
        TwoTierSidebarDisplayPolicy safePolicy = twoTierSidebarDisplayPolicy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : twoTierSidebarDisplayPolicy;
        if (safePolicy != null) {
            ToggleButton activeRailButton = navigationRailButtons.get(safePolicy.activeRailDomain());
            if (isFocusableNode(activeRailButton)) {
                return activeRailButton;
            }
        }
        for (Button button : contextSidebarDomainButtons) {
            if (isFocusableNode(button)) {
                return button;
            }
        }
        for (Button button : sidebarQuickActionButtons) {
            if (isFocusableNode(button)) {
                return button;
            }
        }
        return isFocusableNode(sidebarToggleBtn) ? sidebarToggleBtn : null;
    }

    private boolean isFocusableNode(Node node) {
        return node != null
            && node.isVisible()
            && node.isManaged()
            && !node.isDisable()
            && node.isFocusTraversable();
    }

    private boolean isNodeInside(Node node, Node container) {
        if (node == null || container == null) {
            return false;
        }
        Node cursor = node;
        while (cursor != null) {
            if (cursor == container) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private void toggleRightPanel() {
        rightPanelDisplayMode = resolveRightPanelDisplayMode();
        if (rightPanelDisplayMode == UiRightContextMode.PINNED) {
            refreshRightPanelControls();
            return;
        }
        if (rightPanelDisplayMode == UiRightContextMode.OVERLAY) {
            if (isRightPanelCollapsed) {
                openRightPanelOverlay();
            } else {
                closeRightPanelOverlayIfOpen();
            }
            return;
        }
        mainLayoutCoordinator.toggleRightPanelCollapsed();
        applyAdaptiveLayoutStateToShell(false);
    }

    private void showInInspectorTab(RightPanelInspectorTab tab, boolean requestFocus) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        UiRightContextMode mode = resolveRightPanelDisplayMode();
        if (mode == UiRightContextMode.OVERLAY && isRightPanelCollapsed) {
            rememberRightPanelFocusBeforeOverlayOpen();
            mainLayoutCoordinator.toggleRightPanelCollapsed();
        }
        mainLayoutCoordinator.selectRightInspectorTab(safeTab);
        applyAdaptiveLayoutStateToShell(false);
        if (requestFocus) {
            focusRightInspectorTabButton(safeTab);
        }
    }

    private void rememberRightPanelFocusBeforeOverlayOpen() {
        if (getScene() == null) {
            return;
        }
        Node focusOwner = getScene().getFocusOwner();
        if (focusOwner == null || isNodeInside(focusOwner, rightPanelOverlayHost)) {
            return;
        }
        rightPanelPreviousFocusOwner = focusOwner;
    }

    private void restoreRightPanelFocusAfterOverlayClose() {
        Node preferred = rightPanelPreviousFocusOwner;
        rightPanelPreviousFocusOwner = null;
        Platform.runLater(() -> {
            Node fallback = isFocusableNode(preferred) ? preferred : rightPanelQuickToggleBtn;
            if (isFocusableNode(fallback)) {
                fallback.requestFocus();
            }
        });
    }

    private void initializeAdaptiveShell() {
        applyAdaptiveLayoutStateToShell(false);
        sceneProperty().addListener((obs, oldScene, newScene) -> attachAdaptiveScene(newScene));
        attachAdaptiveScene(getScene());
    }

    private void attachAdaptiveScene(Scene scene) {
        if (adaptiveObservedScene == scene) {
            if (scene != null) {
                applyAdaptiveLayoutForWidth(scene.getWidth());
                applyAdaptiveLayoutStateToShell(false);
            }
            return;
        }
        if (adaptiveObservedScene != null) {
            adaptiveObservedScene.widthProperty().removeListener(adaptiveSceneWidthListener);
            adaptiveObservedScene.heightProperty().removeListener(adaptiveSceneHeightListener);
        }
        adaptiveObservedScene = scene;
        if (adaptiveObservedScene == null) {
            applyAdaptiveLayoutStateToShell(false);
            return;
        }
        adaptiveObservedScene.widthProperty().addListener(adaptiveSceneWidthListener);
        adaptiveObservedScene.heightProperty().addListener(adaptiveSceneHeightListener);
        double width = adaptiveObservedScene.getWidth() > 0.0 ? adaptiveObservedScene.getWidth() : getWidth();
        applyAdaptiveLayoutForWidth(width);
        applyAdaptiveLayoutStateToShell(false);
    }

    private void applyAdaptiveLayoutForWidth(double windowWidthPx) {
        boolean breakpointChanged = mainLayoutCoordinator.applyWindowWidthPolicy(windowWidthPx);
        adaptiveLayoutState = mainLayoutCoordinator.state();
        if (breakpointChanged) {
            renderedContextSidebarDomain = null;
            refreshSidebarButtonsPresentation();
        }
        applyAdaptiveLayoutStateToShell(false);
        if (breakpointChanged) {
            ensureContextSidebarContentRestored();
        }
    }

    private void applyAdaptiveLayoutStateToShell(boolean persistState) {
        applyAdaptiveLayoutStateToShell(persistState, true);
    }

    private void applyAdaptiveLayoutStateToShell(boolean persistState, boolean refreshHeightPolicy) {
        if (refreshHeightPolicy) {
            mainLayoutCoordinator.applyWindowHeightPolicy(resolveLeftNavSurfaceAvailableHeight());
        }
        mainLayoutCoordinator.applyRightPanelInspectorHeightPolicy(resolveRightPanelAvailableHeight());
        mainLayoutCoordinator.setCommandPaletteOverlayOpen(
            overlayDialogManager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE)
        );
        adaptiveLayoutState = mainLayoutCoordinator.state();
        isSidebarCollapsed = adaptiveLayoutState.leftPanelCollapsed();
        isRightPanelCollapsed = adaptiveLayoutState.rightPanelCollapsed();
        leftPanelDisplayPolicy = mainLayoutCoordinator.leftPanelDisplayPolicy();
        twoTierSidebarDisplayPolicy = mainLayoutCoordinator.twoTierSidebarDisplayPolicy();
        rightPanelDisplayPolicy = mainLayoutCoordinator.rightPanelDisplayPolicy();
        rightPanelInspectorDisplayPolicy = mainLayoutCoordinator.rightPanelInspectorDisplayPolicy();
        rightPanelDisplayMode = rightPanelDisplayPolicy == null
            ? resolveRightPanelDisplayMode()
            : rightPanelDisplayPolicy.mode();
        applySidebarCollapsedVisualState();
        applyLeftNavSurfaceLayoutPolicy();
        applyRightPanelCollapsedVisualState();
        applyAdaptiveShellStyleClasses();
        applyInlineOverlayAdaptiveStyleClasses();
        applyAdaptiveTaskTableLayout();
        if (persistState) {
            mainLayoutCoordinator.saveState();
        }
    }

    private void ensureContextSidebarContentRestored() {
        if (isSidebarCollapsed || contextSidebarDomainListBox == null) {
            return;
        }
        boolean filterActive = sidebarFilterField != null
            && sidebarFilterField.getText() != null
            && !sidebarFilterField.getText().isBlank();
        if (filterActive) {
            return;
        }
        TwoTierSidebarDisplayPolicy safePolicy = twoTierSidebarDisplayPolicy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : twoTierSidebarDisplayPolicy;
        if (safePolicy == null) {
            return;
        }
        ContextSidebarDisplayPolicy contextPolicy = safePolicy.contextSidebarPolicy();
        if (contextPolicy != null && contextPolicy.collapsed()) {
            return;
        }
        SidebarRailDomain activeDomain = safePolicy.activeRailDomain();
        renderedContextSidebarDomain = null;
        renderContextSidebarDomainList(activeDomain);
        refreshContextSidebarDomainHeader(activeDomain, contextPolicy);
        refreshContextSidebarFooterStatus(activeDomain, contextPolicy);
        setSidebarNodeVisibility(sidebarScrollPane, true);
        setSidebarNodeVisibility(contextSidebarDomainHeaderBox, true);
        setSidebarNodeVisibility(contextSidebarFooterBox, true);
        refreshSidebarButtonsPresentation();
    }

    private void applySidebarCollapsedVisualState() {
        double totalWidth = isSidebarCollapsed
            ? SIDEBAR_ICON_RAIL_WIDTH
            : Math.max(sidebarExpandedWidth(), SIDEBAR_ICON_RAIL_WIDTH + TWO_TIER_CONTEXT_MIN_WIDTH);
        applyRegionWidth(sidebarContainer, totalWidth);
        if (navigationRailBox != null) {
            applyRegionWidth(navigationRailBox, SIDEBAR_ICON_RAIL_WIDTH);
        }
        double contextWidth = Math.max(0.0, totalWidth - SIDEBAR_ICON_RAIL_WIDTH);
        if (sidebarShellBox != null) {
            applyRegionWidth(sidebarShellBox, contextWidth);
            setSidebarNodeVisibility(sidebarShellBox, !isSidebarCollapsed);
        }
        if (sidebarScrollPane != null) {
            applyRegionWidth(sidebarScrollPane, contextWidth);
        }

        if (sidebarHeaderContent != null) {
            sidebarHeaderContent.setVisible(!isSidebarCollapsed);
            sidebarHeaderContent.setManaged(!isSidebarCollapsed);
        }

        if (sidebarToggleBtn != null) {
            StackPane.setAlignment(sidebarToggleBtn, isSidebarCollapsed ? Pos.CENTER : Pos.TOP_LEFT);
        }

        for (Node sectionLabel : sidebarSectionLabels) {
            sectionLabel.setVisible(!isSidebarCollapsed);
            sectionLabel.setManaged(!isSidebarCollapsed);
        }

        for (Button btn : sidebarButtons) {
            if (isSidebarCollapsed) {
                btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                btn.setAlignment(Pos.CENTER);
            } else {
                btn.setContentDisplay(ContentDisplay.LEFT);
                btn.setAlignment(Pos.CENTER_LEFT);
            }
        }
    }

    private void applyLeftNavSurfaceLayoutPolicy() {
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy == null
            ? mainLayoutCoordinator.leftPanelDisplayPolicy()
            : leftPanelDisplayPolicy;
        if (policy == null) {
            return;
        }
        applySidebarVerticalBounds();
        applyContextSidebarDomainContentPolicy(twoTierSidebarDisplayPolicy);
        applySidebarZoneVisibilityPolicy(policy);
        applySidebarQuickZoneCompactionPolicy(policy);
        applySidebarSurfaceGroupCompactionPolicy(policy);
        applySidebarGuidedHintPolicy(policy);
        applyNavigationRailPolicy(twoTierSidebarDisplayPolicy);
        applyCommandPaletteOverlayLayoutPolicy(policy.palettePolicy());
        refreshLeftNavSurfaceIndicators(policy);
    }

    private void applySidebarZoneVisibilityPolicy(LeftPanelDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        boolean filterActive = sidebarFilterField != null
            && sidebarFilterField.getText() != null
            && !sidebarFilterField.getText().isBlank();
        if (filterActive) {
            return;
        }
        for (SidebarNavZone zone : SidebarNavZone.values()) {
            boolean visible = policy.isZoneVisible(zone);
            Node anchor = sidebarZoneAnchorNodes.get(zone);
            if (anchor != null) {
                setNodeVisibility(anchor, !isSidebarCollapsed && visible);
            }
            List<Node> sectionNodes = sidebarZoneContentNodes.getOrDefault(zone, List.of());
            for (Node node : sectionNodes) {
                setNodeVisibility(node, !isSidebarCollapsed && visible);
            }
        }
    }

    private void applySidebarQuickZoneCompactionPolicy(LeftPanelDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        ContextSidebarDisplayPolicy contextPolicy = twoTierSidebarDisplayPolicy == null
            ? mainLayoutCoordinator.contextSidebarDisplayPolicy()
            : twoTierSidebarDisplayPolicy.contextSidebarPolicy();
        int quickLimit = Math.max(1, policy.quickActionLimit());
        if (contextPolicy != null) {
            quickLimit = Math.max(1, contextPolicy.quickActionLimit());
        }
        boolean compact = contextPolicy == null ? policy.heightCompactionApplied() : contextPolicy.heightCompactionApplied();
        boolean aggressive = contextPolicy == null ? policy.aggressiveCompaction() : contextPolicy.aggressiveCompaction();
        for (int i = 0; i < sidebarQuickActionButtons.size(); i++) {
            Button button = sidebarQuickActionButtons.get(i);
            boolean visible = i < quickLimit;
            setNodeVisibility(button, visible);
            if (!visible) {
                continue;
            }
            // Keep quick-action labels visible when the context sidebar is open.
            button.setContentDisplay(ContentDisplay.LEFT);
            button.setAlignment(Pos.CENTER_LEFT);
            setStyleClassPresent(button, "sidebar-btn-quick-compact", compact);
            setStyleClassPresent(button, "sidebar-btn-quick-aggressive", aggressive);
        }

        boolean showFavorites = contextPolicy == null ? !policy.aggressiveCompaction() : contextPolicy.showFavorites();
        boolean showRecent = contextPolicy == null ? !policy.heightCompactionApplied() : contextPolicy.showRecent();
        if (!sidebarNavigationService.isFavoritesEnabled()) {
            showFavorites = false;
        }
        if (!sidebarNavigationService.isRecentEnabled()) {
            showRecent = false;
        }
        if (sidebarRecentBox == null) {
            showRecent = false;
        }
        setSidebarNodeVisibility(sidebarFavoritesLabel, showFavorites && !isSidebarCollapsed);
        setSidebarNodeVisibility(sidebarFavoritesBox, showFavorites && !isSidebarCollapsed);
        setSidebarNodeVisibility(sidebarRecentLabel, showRecent && !isSidebarCollapsed);
        setSidebarNodeVisibility(sidebarRecentBox, showRecent && !isSidebarCollapsed);

        if (sidebarQuickZoneDivider != null) {
            boolean dividerVisible = !isSidebarCollapsed
                && (showFavorites || showRecent)
                && !sidebarQuickActionButtons.isEmpty()
                && quickLimit > 0;
            setNodeVisibility(sidebarQuickZoneDivider, dividerVisible);
        }
        if (sidebarPinnedQuickZone != null) {
            setStyleClassPresent(sidebarPinnedQuickZone, "nav-surface-height-compact", policy.heightCompactionApplied());
            setStyleClassPresent(sidebarPinnedQuickZone, "nav-surface-height-aggressive", policy.aggressiveCompaction());
        }
        if (sidebarQuickTitleLabel != null) {
            sidebarQuickTitleLabel.setText("БЫСТРЫЙ ДОСТУП");
        }
        if (sidebarFavoritesLabel != null) {
            sidebarFavoritesLabel.setText(aggressive ? "ИЗБР." : compact ? "ИЗБРАННОЕ" : SIDEBAR_FAVORITES_TITLE);
        }
        if (sidebarRecentLabel != null) {
            sidebarRecentLabel.setText(aggressive ? "НЕДАВН." : compact ? "НЕДАВНИЕ" : SIDEBAR_RECENT_TITLE);
        }
        if (sidebarFilterField != null) {
            sidebarFilterField.setPromptText(aggressive ? "Поиск..." : compact ? "Найти..." : "Найти действие...");
        }
    }

    private void applySidebarSurfaceGroupCompactionPolicy(LeftPanelDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        for (Map.Entry<String, VBox> entry : sidebarSurfaceGroupCards.entrySet()) {
            String groupId = entry.getKey();
            VBox card = entry.getValue();
            if (card == null) {
                continue;
            }
            boolean zoneVisible = switch (groupId) {
                case SIDEBAR_GROUP_WORK -> policy.isZoneVisible(SidebarNavZone.CORE);
                case SIDEBAR_GROUP_INSIGHTS, SIDEBAR_GROUP_SYSTEM -> policy.isZoneVisible(SidebarNavZone.ADVANCED);
                default -> true;
            };
            boolean showCard = !isSidebarCollapsed && zoneVisible;
            setNodeVisibility(card, showCard);
            if (!showCard) {
                continue;
            }

            boolean preferredExpanded = sidebarSurfaceGroupExpandedState.getOrDefault(groupId, defaultSidebarSurfaceGroupExpanded(groupId));
            boolean forcedCollapse = false;
            if (SIDEBAR_GROUP_SYSTEM.equals(groupId) && policy.heightCompactionApplied()) {
                forcedCollapse = true;
            }
            if (SIDEBAR_GROUP_INSIGHTS.equals(groupId) && policy.aggressiveCompaction()) {
                forcedCollapse = true;
            }
            if (SIDEBAR_GROUP_WORK.equals(groupId)) {
                preferredExpanded = true;
            }
            boolean expanded = forcedCollapse ? false : preferredExpanded;
            applySidebarSurfaceGroupExpandedVisualState(
                groupId,
                sidebarSurfaceGroupContentNodes.get(groupId),
                sidebarSurfaceGroupChevronIcons.get(groupId),
                sidebarSurfaceGroupSummaryLabels.get(groupId),
                expanded
            );

            setStyleClassPresent(card, "height-compact", policy.heightCompactionApplied());
            setStyleClassPresent(card, "height-aggressive", policy.aggressiveCompaction());
            setStyleClassPresent(card, "forced-collapsed", forcedCollapse);
        }
    }

    private void applySidebarGuidedHintPolicy(LeftPanelDisplayPolicy policy) {
        if (policy == null || sidebarGuidedHintCard == null) {
            return;
        }
        boolean dismissed = mainLayoutCoordinator.leftPanelLayoutState().isHelperHintDismissed(SIDEBAR_GUIDED_HINT_NOVICE_ID);
        ContextSidebarDisplayPolicy contextPolicy = twoTierSidebarDisplayPolicy == null
            ? mainLayoutCoordinator.contextSidebarDisplayPolicy()
            : twoTierSidebarDisplayPolicy.contextSidebarPolicy();
        boolean showInline = contextPolicy == null ? policy.showInlineNoviceGuidance() : contextPolicy.showInlineHelperHints();
        boolean show = showInline && !dismissed && !isSidebarCollapsed;
        setNodeVisibility(sidebarGuidedHintCard, show);
        if (!show) {
            return;
        }

        boolean compactHint = policy.heightCompactionApplied() || policy.isZoneCompacted(SidebarNavZone.CORE);
        boolean aggressive = policy.aggressiveCompaction();
        if (sidebarGuidedHintBodyLabel != null) {
            sidebarGuidedHintBodyLabel.setText(compactHint
                ? "Частые действия сверху. Редкие команды быстрее через Ctrl/Cmd+K."
                : "Сначала добавьте задачу или выберите рабочий сценарий. Редкие команды ищите через Ctrl/Cmd+K.");
        }
        if (sidebarGuidedHintPrimaryBtn != null) {
            sidebarGuidedHintPrimaryBtn.setText(aggressive ? "Добавить" : "Добавить задачу");
        }
        if (sidebarGuidedHintPaletteBtn != null) {
            sidebarGuidedHintPaletteBtn.setText(aggressive ? "Палитра" : "Редкие команды");
        }
        if (sidebarGuidedHintDismissBtn != null) {
            setNodeVisibility(sidebarGuidedHintDismissBtn, !aggressive);
        }
        setStyleClassPresent(sidebarGuidedHintCard, "compact", compactHint);
        setStyleClassPresent(sidebarGuidedHintCard, "aggressive", aggressive);
    }

    private void applyCommandPaletteOverlayLayoutPolicy(CommandPaletteDisplayPolicy palettePolicy) {
        if (palettePolicy == null) {
            return;
        }
        commandPaletteDialog.applyDisplayPolicy(palettePolicy, currentWindow());
    }

    private void refreshLeftNavSurfaceIndicators(LeftPanelDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        TwoTierSidebarDisplayPolicy twoTierPolicy = twoTierSidebarDisplayPolicy == null
            ? mainLayoutCoordinator.twoTierSidebarDisplayPolicy()
            : twoTierSidebarDisplayPolicy;
        ContextSidebarDisplayPolicy contextPolicy = twoTierPolicy == null ? null : twoTierPolicy.contextSidebarPolicy();
        SidebarRailDomain activeRailDomain = twoTierPolicy == null ? null : twoTierPolicy.activeRailDomain();
        String modeText = switch (policy.sidebarMode()) {
            case PINNED -> "WIDE • PINNED";
            case COLLAPSIBLE -> "ADAPTIVE • COLLAPSIBLE";
            case OVERLAY -> "ADAPTIVE • OVERLAY";
        };
        String shellStateText = switch (policy.sidebarMode()) {
            case PINNED -> (isSidebarCollapsed ? "PINNED • FORCED COMPACT" : "PINNED • OPEN");
            case COLLAPSIBLE -> (isSidebarCollapsed ? "COLLAPSED" : "OPEN");
            case OVERLAY -> "ON DEMAND • PALETTE";
        };
        String contextModeText = contextPolicy == null
            ? "CTX • N/A"
            : switch (contextPolicy.sidebarMode()) {
                case PINNED -> "CTX • PINNED";
                case COLLAPSIBLE -> "CTX • COLLAPSIBLE";
                case OVERLAY -> "CTX • OVERLAY";
            };
        String railText = activeRailDomain == null
            ? "RAIL • N/A"
            : "RAIL • " + activeRailDomain.label().toUpperCase(Locale.ROOT);
        String contextStateText;
        if (contextPolicy == null) {
            contextStateText = "CTX • UNKNOWN";
        } else if (contextPolicy.overlayOnDemand()) {
            contextStateText = contextPolicy.collapsed() ? "CTX • ON DEMAND" : "CTX • OVERLAY OPEN";
        } else {
            contextStateText = contextPolicy.collapsed() ? "CTX • COLLAPSED" : "CTX • OPEN";
        }
        boolean paletteOpen = mainLayoutCoordinator.isCommandPaletteOverlayOpen();
        String paletteText = paletteOpen ? "PALETTE • OPEN" : "PALETTE • CLOSED";
        if (leftPanelModeLabel != null) {
            leftPanelModeLabel.setText(modeText + " • " + contextModeText);
            leftPanelModeLabel.setTooltip(new Tooltip(
                "Режим левой navigation surface: " + modeText
                    + " | two-tier context: " + contextModeText
            ));
        }
        if (leftPanelStateLabel != null) {
            leftPanelStateLabel.setText(railText + " • " + contextStateText + " • " + policy.heightBand().name());
            leftPanelStateLabel.setTooltip(new Tooltip(
                "Высотная политика: " + policy.heightBand().name()
                    + ", quickLimit=" + policy.quickActionLimit()
                    + ", compactedZones=" + policy.compactedZones().size()
                    + (contextPolicy == null ? "" : ", twoTierQuickLimit=" + contextPolicy.quickActionLimit())
                    + (contextPolicy == null ? "" : ", twoTierAggressive=" + contextPolicy.aggressiveCompaction())
                    + " | shellState=" + shellStateText
            ));
        }
        if (commandPaletteOverlayStateLabel != null) {
            commandPaletteOverlayStateLabel.setText(paletteText);
            CommandPaletteDisplayPolicy palettePolicy = policy.palettePolicy();
            commandPaletteOverlayStateLabel.setTooltip(new Tooltip(
                "Command palette overlay: " + paletteText
                    + " • mode=" + (palettePolicy == null ? "n/a" : palettePolicy.preferredViewMode().name())
                    + " • height=" + (palettePolicy == null ? "n/a" : palettePolicy.heightBand().name())
                    + " • rail=" + (activeRailDomain == null ? "n/a" : activeRailDomain.name())
            ));
        }
        if (sidebarToggleBtn != null) {
            String tooltip = switch (policy.sidebarMode()) {
                case OVERLAY -> "Открыть командную палитру (overlay-on-demand)";
                case COLLAPSIBLE -> "Свернуть/Развернуть меню";
                case PINNED -> "Левая панель закреплена; можно свернуть вручную";
            };
            sidebarToggleBtn.setTooltip(new Tooltip(tooltip));
        }
    }

    private void applySidebarVerticalBounds() {
        if (sidebarShellBox == null) {
            return;
        }
        if (sidebarTwoTierRoot != null) {
            sidebarTwoTierRoot.setMinHeight(0);
            sidebarTwoTierRoot.setMaxHeight(Double.MAX_VALUE);
            installRegionClip(sidebarTwoTierRoot, 0.0);
        }
        sidebarShellBox.setMinHeight(0);
        sidebarShellBox.setMaxHeight(Double.MAX_VALUE);
        installRegionClip(sidebarShellBox, 0.0);
        if (navigationRailBox != null) {
            navigationRailBox.setMinHeight(0);
            navigationRailBox.setMaxHeight(Double.MAX_VALUE);
            installRegionClip(navigationRailBox, 0.0);
        }
        if (contextSidebarDomainListBox != null) {
            contextSidebarDomainListBox.setMinHeight(0);
            contextSidebarDomainListBox.setMaxHeight(Double.MAX_VALUE);
        }
        if (sidebarScrollPane != null) {
            sidebarScrollPane.setMinHeight(0);
            sidebarScrollPane.setMaxHeight(Double.MAX_VALUE);
            sidebarScrollPane.setMinViewportHeight(0);
            sidebarScrollPane.setFitToWidth(true);
            sidebarScrollPane.setFitToHeight(false);
            sidebarScrollPane.setPannable(true);
        }

        double availableHeight = resolveLeftNavSurfaceAvailableHeight();
        if (!Double.isFinite(availableHeight) || availableHeight <= 0.0) {
            if (sidebarTwoTierRoot != null) {
                sidebarTwoTierRoot.setPrefHeight(Region.USE_COMPUTED_SIZE);
                sidebarTwoTierRoot.setMaxHeight(Double.MAX_VALUE);
            }
            sidebarShellBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
            sidebarShellBox.setMaxHeight(Double.MAX_VALUE);
            return;
        }
        double targetHeight = Math.max(220.0, availableHeight);
        if (sidebarTwoTierRoot != null) {
            applyBoundedRegionHeight(sidebarTwoTierRoot, targetHeight, 0.0, targetHeight);
        }
        applyBoundedRegionHeight(sidebarShellBox, targetHeight, 0.0, targetHeight);
        if (navigationRailBox != null) {
            applyBoundedRegionHeight(navigationRailBox, targetHeight, 0.0, targetHeight);
        }
    }

    private void applyRightPanelCollapsedVisualState() {
        if (rightPanelWrapper == null) {
            return;
        }
        mainLayoutCoordinator.applyRightPanelInspectorHeightPolicy(resolveRightPanelAvailableHeight());
        rightPanelDisplayPolicy = mainLayoutCoordinator.rightPanelDisplayPolicy();
        rightPanelInspectorDisplayPolicy = mainLayoutCoordinator.rightPanelInspectorDisplayPolicy();
        rightPanelDisplayMode = rightPanelDisplayPolicy == null
            ? resolveRightPanelDisplayMode()
            : rightPanelDisplayPolicy.mode();
        applyRightPanelLayoutPolicy();
        boolean collapsed = rightPanelDisplayMode == UiRightContextMode.PINNED ? false : isRightPanelCollapsed;
        switch (rightPanelDisplayMode) {
            case PINNED -> applyPinnedRightPanelMode();
            case COLLAPSIBLE -> applyCollapsibleRightPanelMode(collapsed);
            case OVERLAY -> applyOverlayRightPanelMode(collapsed);
            default -> applyCollapsibleRightPanelMode(collapsed);
        }
        refreshRightPanelControls();
    }

    private void applyAdaptiveShellStyleClasses() {
        getStyleClass().removeAll(
            SHELL_CLASS_BREAKPOINT_COMPACT,
            SHELL_CLASS_BREAKPOINT_NORMAL,
            SHELL_CLASS_BREAKPOINT_WIDE,
            SHELL_CLASS_DENSITY_COMPACT,
            SHELL_CLASS_DENSITY_COMFORTABLE,
            SHELL_CLASS_LEFT_PANEL_PINNED,
            SHELL_CLASS_LEFT_PANEL_COLLAPSIBLE,
            SHELL_CLASS_LEFT_PANEL_OVERLAY,
            SHELL_CLASS_COMMAND_PALETTE_OPEN,
            SHELL_CLASS_RIGHT_PANEL_PINNED,
            SHELL_CLASS_RIGHT_PANEL_COLLAPSIBLE,
            SHELL_CLASS_RIGHT_PANEL_OVERLAY,
            SHELL_CLASS_RIGHT_PANEL_OVERLAY_OPEN
        );
        getStyleClass().add(switch (adaptiveLayoutState.breakpoint()) {
            case COMPACT -> SHELL_CLASS_BREAKPOINT_COMPACT;
            case NORMAL -> SHELL_CLASS_BREAKPOINT_NORMAL;
            case WIDE -> SHELL_CLASS_BREAKPOINT_WIDE;
        });
        getStyleClass().add(adaptiveLayoutState.densityMode() == UiLayoutMode.COMPACT
            ? SHELL_CLASS_DENSITY_COMPACT
            : SHELL_CLASS_DENSITY_COMFORTABLE);
        LeftPanelDisplayPolicy safeLeftPolicy = leftPanelDisplayPolicy == null
            ? mainLayoutCoordinator.leftPanelDisplayPolicy()
            : leftPanelDisplayPolicy;
        if (safeLeftPolicy != null) {
            getStyleClass().add(switch (safeLeftPolicy.sidebarMode()) {
                case PINNED -> SHELL_CLASS_LEFT_PANEL_PINNED;
                case COLLAPSIBLE -> SHELL_CLASS_LEFT_PANEL_COLLAPSIBLE;
                case OVERLAY -> SHELL_CLASS_LEFT_PANEL_OVERLAY;
            });
        }
        if (mainLayoutCoordinator.isCommandPaletteOverlayOpen()) {
            getStyleClass().add(SHELL_CLASS_COMMAND_PALETTE_OPEN);
        }
        getStyleClass().add(switch (rightPanelDisplayMode) {
            case PINNED -> SHELL_CLASS_RIGHT_PANEL_PINNED;
            case COLLAPSIBLE -> SHELL_CLASS_RIGHT_PANEL_COLLAPSIBLE;
            case OVERLAY -> SHELL_CLASS_RIGHT_PANEL_OVERLAY;
        });
        if (rightPanelDisplayMode == UiRightContextMode.OVERLAY && !isRightPanelCollapsed) {
            getStyleClass().add(SHELL_CLASS_RIGHT_PANEL_OVERLAY_OPEN);
        }
        applyCollapsedClass(sidebarContainer, isSidebarCollapsed);
        applyCollapsedClass(rightPanelWrapper, rightPanelDisplayMode != UiRightContextMode.PINNED && isRightPanelCollapsed);
    }

    private UiRightContextMode resolveRightPanelDisplayMode() {
        return mainLayoutCoordinator.snapshot().rightContextMode();
    }

    private void applyRightPanelLayoutPolicy() {
        if (rightPanelInspectorContentHost == null) {
            return;
        }
        RightPanelInspectorDisplayPolicy policy = rightPanelInspectorDisplayPolicy == null
            ? mainLayoutCoordinator.rightPanelInspectorDisplayPolicy()
            : rightPanelInspectorDisplayPolicy;
        if (policy == null) {
            return;
        }

        updateRightPanelInspectorTabStrip(policy);
        refreshRightPanelInspectorContentHost(policy);
        applyRightPanelHeightBandStyles(policy);
        syncAnalyticsHeavyContentState(policy);
        applyRightPanelHeavySectionSizing(policy);
        applyRightPanelDetailsCompaction(policy);
        applyDescriptionContentCompaction(policy);
        applyAiInsightContentCompaction(policy);
        applyCriticalPathContentCompaction(policy);
        updateRightPanelInspectorFooter(policy);
    }

    private void updateRightPanelInspectorTabStrip(RightPanelInspectorDisplayPolicy policy) {
        if (rightPanelInspectorTabStrip == null) {
            return;
        }
        List<RightPanelInspectorTab> visibleTabs = policy == null ? List.of() : policy.tabs();
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(policy);
        boolean compactLabels = policy != null && policy.breakpoint() == UiLayoutBreakpoint.COMPACT;
        setNodeVisibility(rightPanelInspectorTabStrip, !visibleTabs.isEmpty());
        setStyleClassPresent(rightPanelInspectorTabStrip, "compact", compactLabels);

        for (Map.Entry<RightPanelInspectorTab, Button> entry : rightPanelInspectorTabButtons.entrySet()) {
            RightPanelInspectorTab tab = entry.getKey();
            Button button = entry.getValue();
            if (button == null) {
                continue;
            }
            boolean visible = visibleTabs.contains(tab);
            setNodeVisibility(button, visible);
            if (!visible) {
                continue;
            }
            RightPanelTabContentPolicy contentPolicy = policy.contentPolicyFor(tab);
            String label = contentPolicy == null
                ? resolveRightInspectorTabLabel(tab)
                : (compactLabels ? contentPolicy.compactLabel() : contentPolicy.label());
            button.setText(label);
            button.setTooltip(new Tooltip(label));
            boolean active = tab == activeTab;
            setStyleClassPresent(button, "right-panel-tab-btn-active", active);
            setStyleClassPresent(button, "right-panel-tab-btn-inactive", !active);
        }
    }

    private void refreshRightPanelInspectorContentHost(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null || rightPanelInspectorContentHost == null) {
            return;
        }
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(policy);
        RightPanelTabContentPolicy activeContentPolicy = policy.contentPolicyFor(activeTab);

        for (RightPanelInspectorTab tab : RightPanelInspectorTab.baselineOrder()) {
            ScrollPane scroll = rightPanelInspectorTabScrolls.get(tab);
            RightPanelTabContentPolicy contentPolicy = policy.contentPolicyFor(tab);
            if (scroll == null || contentPolicy == null || !policy.tabs().contains(tab)) {
                if (scroll != null) {
                    setNodeVisibility(scroll, false);
                }
                continue;
            }
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setPannable(!contentPolicy.localScrollOnly());
            setStyleClassPresent(scroll, "right-panel-tab-scroll-local-only", contentPolicy.localScrollOnly());

            boolean active = tab == activeTab;
            setNodeVisibility(scroll, active);
            setStyleClassPresent(scroll, "right-panel-tab-scroll-active", active);
            setStyleClassPresent(scroll, "right-panel-tab-scroll-inactive", !active);
            if (active) {
                scroll.toFront();
            }
        }
        applyActiveInspectorSectionPriorityStyles(activeContentPolicy);
    }

    private void applyRightPanelHeightBandStyles(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        boolean lowHeight = policy.heightBand() != null && policy.heightBand().isLowHeight();
        boolean veryLowHeight = policy.heightBand() != null && policy.heightBand().isVeryLowHeight();
        if (rightPanelBody != null) {
            setStyleClassPresent(rightPanelBody, "height-low", lowHeight);
            setStyleClassPresent(rightPanelBody, "height-very-low", veryLowHeight);
        }
        if (rightPanelInspectorTabStrip != null) {
            setStyleClassPresent(rightPanelInspectorTabStrip, "height-low", lowHeight);
            setStyleClassPresent(rightPanelInspectorTabStrip, "height-very-low", veryLowHeight);
        }
        for (VBox stack : rightPanelInspectorTabStacks.values()) {
            if (stack == null) {
                continue;
            }
            setStyleClassPresent(stack, "height-low", lowHeight);
            setStyleClassPresent(stack, "height-very-low", veryLowHeight);
        }
    }

    private void updateRightPanelInspectorFooter(RightPanelInspectorDisplayPolicy policy) {
        if (rightPanelInspectorFooterLabel == null || policy == null) {
            return;
        }
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(policy);
        String label = resolveRightInspectorTabLabel(activeTab);
        String state = resolveInspectorTabStateKind(activeTab);
        String stateHint = resolveInspectorTabStateHint(activeTab);
        rightPanelInspectorFooterLabel.setText(
            "Вкладка: " + label + "  •  " + stateHint + "  •  ←/→, Enter/Space, Tab, Ctrl+Tab"
        );
        setStyleClassPresent(rightPanelInspectorFooterLabel, "state-empty", "empty".equals(state));
        setStyleClassPresent(rightPanelInspectorFooterLabel, "state-error", "error".equals(state));
    }

    private String resolveInspectorTabStateKind(RightPanelInspectorTab tab) {
        Task selectedTask = getSelectedTask();
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        return switch (safeTab) {
            case PROPERTIES -> selectedTask == null ? "empty" : "ready";
            case DESCRIPTION -> selectedTask == null || isEffectivelyBlankMarkdown(currentDescriptionText) ? "empty" : "ready";
            case ANALYTICS -> {
                if (selectedTask == null) {
                    yield "empty";
                }
                if (aiInsightErrorState) {
                    yield "error";
                }
                yield isEffectivelyBlankMarkdown(currentInsightText) ? "empty" : "ready";
            }
        };
    }

    private String resolveInspectorTabStateHint(RightPanelInspectorTab tab) {
        Task selectedTask = getSelectedTask();
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        return switch (safeTab) {
            case PROPERTIES -> selectedTask == null
                ? "Нет выбранной задачи: выберите строку в таблице"
                : "Свойства задачи доступны для просмотра";
            case DESCRIPTION -> {
                if (selectedTask == null) {
                    yield "Нет выбранной задачи: описание не привязано";
                }
                if (isEffectivelyBlankMarkdown(currentDescriptionText)) {
                    yield "Описание пустое: добавьте контекст задачи";
                }
                yield "Описание задачи доступно";
            }
            case ANALYTICS -> {
                if (selectedTask == null) {
                    yield "Нет выбранной задачи: аналитика недоступна";
                }
                if (aiInsightErrorState) {
                    yield "Ошибка ИИ-анализа: повторите запуск";
                }
                if (isEffectivelyBlankMarkdown(currentInsightText)) {
                    yield "ИИ-анализ не запущен для выбранной задачи";
                }
                if (criticalPathResult.taskCount() <= 0) {
                    yield "Граф зависимостей пуст: добавьте связи между задачами";
                }
                yield "ИИ-анализ и критический путь доступны";
            }
        };
    }

    private boolean isEffectivelyBlankMarkdown(String rawText) {
        String normalized = rawText == null ? "" : rawText.trim();
        if (normalized.isBlank()) {
            return true;
        }
        String plain = normalized
            .replaceAll("(?m)^#+\\s*", "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("*", "")
            .replace("_", "")
            .replaceAll("\\[(.+?)\\]\\((.+?)\\)", "$1")
            .replaceAll("\\s+", " ")
            .trim();
        if (plain.isBlank()) {
            return true;
        }
        String lowered = plain.toLowerCase(Locale.ROOT);
        return lowered.equals("нет описания")
            || lowered.contains("выберите задачу")
            || lowered.contains("нажмите 'ии-анализ'")
            || lowered.contains("ошибка анализа");
    }

    private void applyActiveInspectorSectionPriorityStyles(RightPanelTabContentPolicy activeContentPolicy) {
        for (Map.Entry<String, Node> entry : rightPanelSectionNodes.entrySet()) {
            String sectionId = entry.getKey();
            Node sectionNode = entry.getValue();
            if (sectionNode == null) {
                continue;
            }
            boolean inActiveTab = hasInspectorSection(activeContentPolicy, sectionId);
            RightPanelSectionPriority priority = resolveInspectorSectionPriority(activeContentPolicy, sectionId);
            boolean demoted = inActiveTab && (priority.isTertiary()
                || (priority == RightPanelSectionPriority.SECONDARY
                && activeContentPolicy != null
                && activeContentPolicy.heightCompactionApplied()));
            setStyleClassPresent(sectionNode, "right-panel-section-demoted", demoted);
        }
    }

    private void syncAnalyticsHeavyContentState(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        RightPanelTabContentPolicy analyticsPolicy = policy.contentPolicyFor(RightPanelInspectorTab.ANALYTICS);
        if (analyticsPolicy == null) {
            return;
        }
        boolean aiExpanded = aiInsightCompactExpanded
            || analyticsPolicy.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL);
        boolean pathExpanded = criticalPathCompactDetailsExpanded
            || analyticsPolicy.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL);
        if (aiExpanded && pathExpanded) {
            pathExpanded = false;
        }
        aiInsightCompactExpanded = aiExpanded;
        criticalPathCompactDetailsExpanded = pathExpanded;
    }

    private void applyRightPanelHeavySectionSizing(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null) {
            return;
        }
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(policy);
        RightPanelTabContentPolicy activeContentPolicy = policy.contentPolicyFor(activeTab);
        boolean descriptionVisible = hasInspectorSection(activeContentPolicy, RightPanelLayoutService.SECTION_DESCRIPTION);
        boolean aiVisible = hasInspectorSection(activeContentPolicy, RightPanelLayoutService.SECTION_AI);
        boolean pathVisible = hasInspectorSection(activeContentPolicy, RightPanelLayoutService.SECTION_PATH);
        boolean descriptionDemoted = resolveInspectorSectionPriority(
            activeContentPolicy,
            RightPanelLayoutService.SECTION_DESCRIPTION
        ) == RightPanelSectionPriority.SECONDARY;
        boolean aiDemoted = resolveInspectorSectionPriority(activeContentPolicy, RightPanelLayoutService.SECTION_AI)
            == RightPanelSectionPriority.SECONDARY;
        boolean pathDemoted = resolveInspectorSectionPriority(activeContentPolicy, RightPanelLayoutService.SECTION_PATH)
            == RightPanelSectionPriority.SECONDARY;

        boolean compact = policy.breakpoint() == UiLayoutBreakpoint.COMPACT;
        boolean normal = policy.breakpoint() == UiLayoutBreakpoint.NORMAL;
        boolean heightConstrained = isRightPanelHeightConstrained();
        boolean heightSeverelyConstrained = isRightPanelHeightSeverelyConstrained();

        if (descriptionWebView != null) {
            double height = compact ? 170.0 : (normal ? 210.0 : 300.0);
            if (descriptionDemoted || !descriptionVisible) {
                height = compact ? 150.0 : (normal ? 180.0 : 240.0);
            }
            if (heightConstrained) {
                height = Math.min(height, heightSeverelyConstrained ? 140.0 : 170.0);
            }
            descriptionWebView.setMinHeight(Math.min(height, heightSeverelyConstrained ? 110.0 : 130.0));
            descriptionWebView.setPrefHeight(height);
            descriptionWebView.setMaxHeight(height);
            setStyleClassPresent(descriptionWebView, "right-panel-heavy-content", descriptionVisible);
        }

        if (aiInsightWebView != null) {
            double height = compact ? 180.0 : (normal ? 230.0 : 320.0);
            if (aiDemoted || !aiVisible) {
                height = compact ? 160.0 : (normal ? 190.0 : 250.0);
            }
            if (heightConstrained) {
                height = Math.min(height, heightSeverelyConstrained ? 150.0 : 185.0);
            }
            aiInsightWebView.setMinHeight(Math.min(height, heightSeverelyConstrained ? 120.0 : 140.0));
            aiInsightWebView.setPrefHeight(height);
            aiInsightWebView.setMaxHeight(height);
            setStyleClassPresent(aiInsightWebView, "right-panel-heavy-content", aiVisible);
        }

        if (criticalPathPanelBody != null) {
            double scrollHeight = compact ? 150.0 : (normal ? 190.0 : 250.0);
            if (pathDemoted || !pathVisible) {
                scrollHeight = compact ? 130.0 : (normal ? 160.0 : 210.0);
            }
            if (heightConstrained) {
                scrollHeight = Math.min(scrollHeight, heightSeverelyConstrained ? 120.0 : 150.0);
            }
            criticalPathPanelBody.setMinHeight(Math.min(scrollHeight, heightSeverelyConstrained ? 90.0 : 110.0));
            criticalPathPanelBody.setPrefHeight(scrollHeight);
            criticalPathPanelBody.setMaxHeight(scrollHeight + 8.0);
            setStyleClassPresent(criticalPathPanelBody, "right-panel-heavy-content-scroll", pathVisible);
        }
    }

    private void applyRightPanelDetailsCompaction(RightPanelInspectorDisplayPolicy policy) {
        if (detailSecondaryFieldsPane == null || policy == null) {
            return;
        }
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(policy);
        RightPanelTabContentPolicy activeContentPolicy = policy.contentPolicyFor(activeTab);
        boolean shouldExpandSecondary = policy.breakpoint() == UiLayoutBreakpoint.WIDE
            && activeTab == RightPanelInspectorTab.PROPERTIES
            && (activeContentPolicy == null || !activeContentPolicy.heightCompactionApplied())
            && !isRightPanelHeightConstrained()
            && hasInspectorSection(activeContentPolicy, RightPanelLayoutService.SECTION_DETAILS);
        if (detailSecondaryFieldsPane.isExpanded() != shouldExpandSecondary) {
            detailSecondaryFieldsPane.setExpanded(shouldExpandSecondary);
        }
        setStyleClassPresent(detailSecondaryFieldsPane, "right-panel-secondary-demoted", !shouldExpandSecondary);
    }

    private void applyDescriptionContentCompaction(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null || descriptionSummaryBox == null || descriptionFullContentBox == null) {
            return;
        }
        RightPanelTabContentPolicy descriptionPolicy = policy.contentPolicyFor(RightPanelInspectorTab.DESCRIPTION);
        if (descriptionPolicy == null || !hasInspectorSection(descriptionPolicy, RightPanelLayoutService.SECTION_DESCRIPTION)) {
            setNodeVisibility(descriptionSummaryBox, false);
            setNodeVisibility(descriptionFullContentBox, true);
            return;
        }
        boolean summaryFirstMode = descriptionPolicy.summaryFirst();
        boolean showSummary = summaryFirstMode;
        boolean showFull = !summaryFirstMode || descriptionCompactExpanded;

        setNodeVisibility(descriptionSummaryBox, showSummary);
        setNodeVisibility(descriptionFullContentBox, showFull);
        if (descriptionCompactExpandBtn != null) {
            setNodeVisibility(descriptionCompactExpandBtn, showSummary);
            descriptionCompactExpandBtn.setText(showFull ? "Скрыть полный" : "Полное описание");
            descriptionCompactExpandBtn.setTooltip(new Tooltip(
                showFull ? "Оставить только краткий summary описания" : "Показать полное описание задачи"
            ));
        }
        setStyleClassPresent(descriptionSummaryBox, "expanded", showFull && showSummary);
        setStyleClassPresent(descriptionFullContentBox, "compact-hidden", !showFull && showSummary);
    }

    private void applyAiInsightContentCompaction(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null || aiInsightSummaryBox == null || aiInsightFullContentBox == null) {
            return;
        }
        RightPanelTabContentPolicy analyticsPolicy = policy.contentPolicyFor(RightPanelInspectorTab.ANALYTICS);
        if (analyticsPolicy == null || !hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_AI)) {
            setNodeVisibility(aiInsightSummaryBox, false);
            setNodeVisibility(aiInsightFullContentBox, true);
            return;
        }
        boolean pathTabPresent = hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_PATH);
        boolean summaryFirstMode = analyticsPolicy.summaryFirst() || pathTabPresent;
        boolean showSummary = summaryFirstMode;
        boolean showFull = (!summaryFirstMode || aiInsightCompactExpanded) && !criticalPathCompactDetailsExpanded;

        setNodeVisibility(aiInsightSummaryBox, showSummary);
        setNodeVisibility(aiInsightFullContentBox, showFull);
        if (aiInsightCompactExpandBtn != null) {
            setNodeVisibility(aiInsightCompactExpandBtn, showSummary);
            aiInsightCompactExpandBtn.setText(showFull ? "Скрыть полный" : "Полный текст");
            aiInsightCompactExpandBtn.setTooltip(new Tooltip(
                showFull ? "Оставить только краткий AI summary" : "Показать полный AI-анализ"
            ));
        }
        setStyleClassPresent(aiInsightSummaryBox, "expanded", showFull && showSummary);
        setStyleClassPresent(aiInsightFullContentBox, "compact-hidden", !showFull && showSummary);
    }

    private void applyCriticalPathContentCompaction(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null || criticalPathExtendedMetricsBox == null) {
            return;
        }
        RightPanelTabContentPolicy analyticsPolicy = policy.contentPolicyFor(RightPanelInspectorTab.ANALYTICS);
        if (analyticsPolicy == null || !hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_PATH)) {
            setNodeVisibility(criticalPathExtendedMetricsBox, true);
            return;
        }
        boolean aiTabPresent = hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_AI);
        boolean summaryFirstMode = analyticsPolicy.summaryFirst() || aiTabPresent;
        boolean expanded = (!summaryFirstMode || criticalPathCompactDetailsExpanded) && !aiInsightCompactExpanded;

        setNodeVisibility(criticalPathCompactSummaryLabel, true);
        setNodeVisibility(criticalPathExtendedMetricsBox, expanded);
        if (criticalPathDetailsToggleBtn != null) {
            setNodeVisibility(criticalPathDetailsToggleBtn, summaryFirstMode);
            criticalPathDetailsToggleBtn.setText(expanded ? "Скрыть" : "Подробнее");
            criticalPathDetailsToggleBtn.setTooltip(new Tooltip(
                expanded ? "Скрыть расширенные метрики критического пути" : "Показать расширенные метрики критического пути"
            ));
        }
        if (expanded && criticalPathExtendedMetricsDirty) {
            renderCriticalChain();
            criticalPathExtendedMetricsDirty = false;
        }
    }

    private RightPanelInspectorTab resolveActiveInspectorTab(RightPanelInspectorDisplayPolicy policy) {
        if (policy == null) {
            return RightPanelInspectorTab.PROPERTIES;
        }
        RightPanelInspectorTab active = policy.activeTab();
        if (active != null && policy.tabs().contains(active)) {
            return active;
        }
        if (!policy.tabs().isEmpty()) {
            return policy.tabs().getFirst();
        }
        return RightPanelInspectorTab.PROPERTIES;
    }

    private boolean hasInspectorSection(RightPanelTabContentPolicy policy, String sectionId) {
        if (policy == null || sectionId == null || sectionId.isBlank()) {
            return false;
        }
        for (var mapping : policy.sectionMappings()) {
            if (sectionId.equals(mapping.sectionId())) {
                return true;
            }
        }
        return false;
    }

    private RightPanelSectionPriority resolveInspectorSectionPriority(
        RightPanelTabContentPolicy policy,
        String sectionId
    ) {
        if (policy == null || sectionId == null || sectionId.isBlank()) {
            return RightPanelSectionPriority.TERTIARY;
        }
        for (var mapping : policy.sectionMappings()) {
            if (sectionId.equals(mapping.sectionId())) {
                return mapping.contentPriority();
            }
        }
        return RightPanelSectionPriority.TERTIARY;
    }

    private boolean isRightPanelHeightConstrained() {
        double availableHeight = resolveRightPanelAvailableHeight();
        return Double.isFinite(availableHeight) && availableHeight > 0.0 && availableHeight <= 860.0;
    }

    private boolean isRightPanelHeightSeverelyConstrained() {
        double availableHeight = resolveRightPanelAvailableHeight();
        return Double.isFinite(availableHeight) && availableHeight > 0.0 && availableHeight <= 790.0;
    }

    private void applyPinnedRightPanelMode() {
        dockRightPanel();
        double preferredWidth = resolveDockedRightPanelWidth(UiRightContextMode.PINNED);
        applyBoundedRegionWidth(
            rightPanelWrapper,
            preferredWidth,
            RIGHT_PANEL_PINNED_MIN_WIDTH,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
        );
        setNodeVisibility(rightPanelWrapper, true);
        setNodeVisibility(rightPanelContent, true);
        setNodeVisibility(rightPanelOverlayHost, false);
        setNodeVisibility(rightPanelOverlayScrim, false);
        if (rightPanelHeader != null) {
            rightPanelHeader.setAlignment(Pos.CENTER_RIGHT);
            rightPanelHeader.setPadding(new Insets(6, 8, 4, 8));
        }
        applyRightPanelVerticalBounds();
        isRightPanelCollapsed = false;
    }

    private void applyCollapsibleRightPanelMode(boolean collapsed) {
        dockRightPanel();
        if (collapsed) {
            applyRegionWidth(rightPanelWrapper, RIGHT_PANEL_COLLAPSED_WIDTH);
        } else {
            double preferredWidth = resolveDockedRightPanelWidth(UiRightContextMode.COLLAPSIBLE);
            applyBoundedRegionWidth(
                rightPanelWrapper,
                preferredWidth,
                RIGHT_PANEL_COLLAPSIBLE_MIN_WIDTH,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
            );
        }
        setNodeVisibility(rightPanelWrapper, true);
        setNodeVisibility(rightPanelContent, !collapsed);
        setNodeVisibility(rightPanelOverlayHost, false);
        setNodeVisibility(rightPanelOverlayScrim, false);
        if (rightPanelHeader != null) {
            rightPanelHeader.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
            rightPanelHeader.setPadding(collapsed ? new Insets(6, 0, 6, 0) : new Insets(6, 8, 4, 8));
        }
        applyRightPanelVerticalBounds();
        isRightPanelCollapsed = collapsed;
    }

    private void applyOverlayRightPanelMode(boolean collapsed) {
        attachRightPanelOverlay();
        double width = resolveOverlayRightPanelWidth();
        applyBoundedRegionWidth(rightPanelWrapper, width, RIGHT_PANEL_OVERLAY_MIN_WIDTH, width);
        boolean open = !collapsed;
        setNodeVisibility(rightPanelOverlayHost, open);
        rightPanelOverlayHost.setMouseTransparent(!open);
        rightPanelOverlayHost.setPickOnBounds(open);
        setNodeVisibility(rightPanelOverlayScrim, open);
        setNodeVisibility(rightPanelWrapper, open);
        setNodeVisibility(rightPanelContent, open);
        if (rightPanelHeader != null) {
            rightPanelHeader.setAlignment(Pos.CENTER_RIGHT);
            rightPanelHeader.setPadding(new Insets(6, 8, 4, 8));
        }
        if (open && rightPanelWrapper != null) {
            rightPanelWrapper.toFront();
        }
        applyRightPanelVerticalBounds();
        isRightPanelCollapsed = collapsed;
        if (!open) {
            restoreRightPanelFocusAfterOverlayClose();
        }
    }

    private void openRightPanelOverlay() {
        rightPanelDisplayMode = resolveRightPanelDisplayMode();
        if (rightPanelDisplayMode != UiRightContextMode.OVERLAY || !isRightPanelCollapsed) {
            return;
        }
        rememberRightPanelFocusBeforeOverlayOpen();
        mainLayoutCoordinator.toggleRightPanelCollapsed();
        applyAdaptiveLayoutStateToShell(false);
        focusRightInspectorTabButton(resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy));
    }

    private void closeRightPanelOverlayIfOpen() {
        rightPanelDisplayMode = resolveRightPanelDisplayMode();
        if (rightPanelDisplayMode != UiRightContextMode.OVERLAY || isRightPanelCollapsed) {
            return;
        }
        mainLayoutCoordinator.toggleRightPanelCollapsed();
        applyAdaptiveLayoutStateToShell(false);
    }

    private void dockRightPanel() {
        if (rightPanelOverlayHost != null && rightPanelWrapper != null) {
            rightPanelOverlayHost.getChildren().remove(rightPanelWrapper);
        }
        if (mainLayoutShell.rightContextDrawer() != rightPanelWrapper && rightPanelWrapper != null) {
            mainLayoutShell.setRightContextDrawer(rightPanelWrapper);
        }
    }

    private void attachRightPanelOverlay() {
        if (mainLayoutShell.rightContextDrawer() == rightPanelWrapper) {
            mainLayoutShell.setRightContextDrawer(null);
        }
        if (rightPanelOverlayHost == null || rightPanelWrapper == null) {
            return;
        }
        if (!rightPanelOverlayHost.getChildren().contains(rightPanelWrapper)) {
            rightPanelOverlayHost.getChildren().add(rightPanelWrapper);
        }
        StackPane.setAlignment(rightPanelWrapper, Pos.CENTER_RIGHT);
        StackPane.setMargin(rightPanelWrapper, new Insets(8, 8, 8, 8));
    }

    private double resolveOverlayRightPanelWidth() {
        double preferred = clamp(
            rightPanelExpandedWidth(),
            RIGHT_PANEL_OVERLAY_MIN_WIDTH,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
        );
        if (centerShellStack == null) {
            return preferred;
        }
        double available = centerShellStack.getWidth();
        if (!Double.isFinite(available) || available <= 0.0) {
            return preferred;
        }
        return clamp(
            preferred,
            RIGHT_PANEL_OVERLAY_MIN_WIDTH,
            Math.min(UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX, available * 0.76)
        );
    }

    private double resolveDockedRightPanelWidth(UiRightContextMode mode) {
        double preferred = clamp(
            rightPanelExpandedWidth(),
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MIN,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
        );
        double windowWidth = resolveWindowWidth();
        if (!Double.isFinite(windowWidth) || windowWidth <= 0.0) {
            return preferred;
        }

        double minWidth = mode == UiRightContextMode.PINNED
            ? RIGHT_PANEL_PINNED_MIN_WIDTH
            : RIGHT_PANEL_COLLAPSIBLE_MIN_WIDTH;
        double maxFraction = mode == UiRightContextMode.PINNED ? 0.34 : 0.31;
        double boundedMax = Math.min(
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX,
            Math.max(minWidth, windowWidth * maxFraction)
        );
        return clamp(preferred, minWidth, boundedMax);
    }

    private double resolveWindowWidth() {
        if (adaptiveObservedScene != null && adaptiveObservedScene.getWidth() > 0.0) {
            return adaptiveObservedScene.getWidth();
        }
        if (getScene() != null && getScene().getWidth() > 0.0) {
            return getScene().getWidth();
        }
        if (getWidth() > 0.0) {
            return getWidth();
        }
        return UxConfigDefaults.UX_LAYOUT_BREAKPOINT_NORMAL_MIN_WIDTH;
    }

    private void refreshRightPanelControls() {
        RightPanelInspectorTab activeInspectorTab = mainLayoutCoordinator.snapshot().rightInspectorActiveTab();
        String activeTabLabel = resolveRightInspectorTabLabel(activeInspectorTab);
        String activeTabHint = "Активная вкладка инспектора: " + activeTabLabel;
        String modeText;
        String modeTooltip;
        String stateText;
        String stateTooltip;
        String tooltipText;
        switch (rightPanelDisplayMode) {
            case PINNED -> {
                modeText = "WIDE • PINNED";
                modeTooltip = "Режим wide: правая панель всегда закреплена";
                stateText = "LOCKED OPEN • " + activeTabLabel;
                stateTooltip = "В wide-режиме панель закреплена и не сворачивается. " + activeTabHint;
                tooltipText = "Правая панель закреплена в широком режиме";
            }
            case COLLAPSIBLE -> {
                modeText = "NORMAL • COLLAPSIBLE";
                modeTooltip = "Режим normal: docked панель можно свернуть";
                stateText = (isRightPanelCollapsed ? "COLLAPSED" : "OPEN") + " • " + activeTabLabel;
                stateTooltip = isRightPanelCollapsed
                    ? "Правая панель свернута, рабочая область приоритетна. " + activeTabHint
                    : "Правая панель docked и открыта. " + activeTabHint;
                tooltipText = isRightPanelCollapsed
                    ? "Показать правую панель"
                    : "Свернуть правую панель";
            }
            case OVERLAY -> {
                modeText = "COMPACT • OVERLAY";
                modeTooltip = "Режим compact: панель открывается как overlay-on-demand";
                stateText = (isRightPanelCollapsed ? "ON DEMAND" : "OPEN") + " • " + activeTabLabel;
                stateTooltip = isRightPanelCollapsed
                    ? "Контекстная панель скрыта до запроса пользователя. " + activeTabHint
                    : "Overlay открыт, закрытие по кнопке или scrim. " + activeTabHint;
                tooltipText = isRightPanelCollapsed
                    ? "Открыть контекстный drawer"
                    : "Закрыть контекстный drawer";
            }
            default -> {
                modeText = "PANEL";
                modeTooltip = "Режим правой панели";
                stateText = "UNKNOWN • " + activeTabLabel;
                stateTooltip = "Состояние правой панели. " + activeTabHint;
                tooltipText = "Переключить правую панель";
            }
        }

//        if (rightPanelModeLabel != null) {
//            rightPanelModeLabel.setText(modeText);
//            rightPanelModeLabel.setTooltip(new Tooltip(modeTooltip));
//            setStyleClassPresent(rightPanelModeLabel, "mode-pinned", rightPanelDisplayMode == UiRightContextMode.PINNED);
//            setStyleClassPresent(rightPanelModeLabel, "mode-collapsible", rightPanelDisplayMode == UiRightContextMode.COLLAPSIBLE);
//            setStyleClassPresent(rightPanelModeLabel, "mode-overlay", rightPanelDisplayMode == UiRightContextMode.OVERLAY);
//        }
//        if (rightPanelStateLabel != null) {
//            rightPanelStateLabel.setText(stateText);
//            rightPanelStateLabel.setTooltip(new Tooltip(stateTooltip));
//            setStyleClassPresent(rightPanelStateLabel, "state-locked", rightPanelDisplayMode == UiRightContextMode.PINNED);
//            setStyleClassPresent(rightPanelStateLabel, "state-open", !isRightPanelCollapsed || rightPanelDisplayMode == UiRightContextMode.PINNED);
//            setStyleClassPresent(rightPanelStateLabel, "state-closed", rightPanelDisplayMode != UiRightContextMode.PINNED && isRightPanelCollapsed);
//            setStyleClassPresent(rightPanelStateLabel, "state-tab-properties", activeInspectorTab == RightPanelInspectorTab.PROPERTIES);
//            setStyleClassPresent(rightPanelStateLabel, "state-tab-description", activeInspectorTab == RightPanelInspectorTab.DESCRIPTION);
//            setStyleClassPresent(rightPanelStateLabel, "state-tab-analytics", activeInspectorTab == RightPanelInspectorTab.ANALYTICS);
//        }
        if (rightPanelToggleBtn != null) {
            rightPanelToggleBtn.setDisable(rightPanelDisplayMode == UiRightContextMode.PINNED);
            rightPanelToggleBtn.setTooltip(new Tooltip(tooltipText));
            rightPanelToggleBtn.setGraphic(FontIcon.of(
                isRightPanelCollapsed ? MaterialDesignM.MENU_OPEN : MaterialDesignM.MENU,
                18
            ));
            setStyleClassPresent(rightPanelToggleBtn, "overlay-toggle-open", rightPanelDisplayMode == UiRightContextMode.OVERLAY && !isRightPanelCollapsed);
        }
        if (rightPanelQuickToggleBtn != null) {
            boolean quickVisible = rightPanelDisplayMode == UiRightContextMode.OVERLAY && isRightPanelCollapsed;
            setNodeVisibility(rightPanelQuickToggleBtn, quickVisible);
            rightPanelQuickToggleBtn.setTooltip(new Tooltip("Открыть контекстный overlay (compact mode)"));
            rightPanelQuickToggleBtn.setGraphic(FontIcon.of(MaterialDesignM.MENU_OPEN, 16));
        }
    }

    private String resolveRightInspectorTabLabel(RightPanelInspectorTab tab) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        if (rightPanelInspectorDisplayPolicy != null) {
            RightPanelTabContentPolicy policy = rightPanelInspectorDisplayPolicy.contentPolicyFor(safeTab);
            if (policy != null && policy.label() != null && !policy.label().isBlank()) {
                return policy.label();
            }
        }
        return switch (safeTab) {
            case PROPERTIES -> "Свойства";
            case DESCRIPTION -> "Описание";
            case ANALYTICS -> "ИИ-Анализ & График";
        };
    }

    private void applyAdaptiveTaskTableLayout() {
        if (taskTable == null || taskTitleColumn == null || adaptiveLayoutState == null) {
            return;
        }
        UiLayoutBreakpoint breakpoint = adaptiveLayoutState.breakpoint();
        boolean narrowByTableWidth = taskTable.getWidth() > 0.0 && taskTable.getWidth() < TASK_TABLE_NARROW_WIDTH_THRESHOLD;
        boolean collapseSecondaryColumns = breakpoint == UiLayoutBreakpoint.COMPACT || narrowByTableWidth;
        boolean compactMode = adaptiveLayoutState.densityMode() == UiLayoutMode.COMPACT || breakpoint == UiLayoutBreakpoint.COMPACT;

        boolean changed = false;
        changed |= setStyleClassPresent(taskTable, TASK_TABLE_CLASS_COMPACT, compactMode);
        changed |= setStyleClassPresent(taskTable, TASK_TABLE_CLASS_NARROW, collapseSecondaryColumns);
        changed |= setColumnVisible(taskTagsColumn, !collapseSecondaryColumns);
        changed |= setColumnVisible(taskComplexityColumn, !collapseSecondaryColumns);
        changed |= setColumnVisible(taskPriorityColumn, !collapseSecondaryColumns);
        changed |= applyTaskTableColumnWeights(collapseSecondaryColumns, breakpoint);

        if (taskTableSecondaryColumnsCollapsed != collapseSecondaryColumns) {
            taskTableSecondaryColumnsCollapsed = collapseSecondaryColumns;
            changed = true;
        }
        if (taskTableCompactModeApplied != compactMode) {
            taskTableCompactModeApplied = compactMode;
            changed = true;
        }
        if (changed) {
            taskTable.refresh();
        }
    }

    private boolean applyTaskTableColumnWeights(boolean collapseSecondaryColumns, UiLayoutBreakpoint breakpoint) {
        double availableWidth = resolveTaskTableAvailableWidth();
        double actionsWidth = collapseSecondaryColumns ? 118.0 : 126.0;
        boolean changed = false;
        changed |= setColumnPrefWidth(taskActionsColumn, actionsWidth);
        changed |= setColumnMinWidth(taskActionsColumn, collapseSecondaryColumns ? 108.0 : 112.0);
        changed |= setColumnMaxWidth(taskActionsColumn, collapseSecondaryColumns ? 132.0 : 150.0);

        double contentWidth = Math.max(380.0, availableWidth - actionsWidth);
        if (collapseSecondaryColumns) {
            double[] widths = distributeColumnWidths(
                contentWidth,
                new double[] {220.0, 124.0},
                new double[] {0.78, 0.22}
            );
            changed |= setColumnPrefWidth(taskTitleColumn, widths[0]);
            changed |= setColumnPrefWidth(taskDeadlineColumn, widths[1]);
            changed |= setColumnPrefWidth(taskTagsColumn, 120.0);
            changed |= setColumnPrefWidth(taskComplexityColumn, 96.0);
            changed |= setColumnPrefWidth(taskPriorityColumn, 102.0);
            return changed;
        }

        double[] minWidths;
        double[] flexWeights;
        if (breakpoint == UiLayoutBreakpoint.WIDE) {
            minWidths = new double[] {250.0, 160.0, 120.0, 92.0, 98.0};
            flexWeights = new double[] {0.37, 0.24, 0.16, 0.11, 0.12};
        } else {
            minWidths = new double[] {230.0, 145.0, 116.0, 90.0, 94.0};
            flexWeights = new double[] {0.40, 0.22, 0.15, 0.11, 0.12};
        }
        double[] widths = distributeColumnWidths(contentWidth, minWidths, flexWeights);
        changed |= setColumnPrefWidth(taskTitleColumn, widths[0]);
        changed |= setColumnPrefWidth(taskTagsColumn, widths[1]);
        changed |= setColumnPrefWidth(taskDeadlineColumn, widths[2]);
        changed |= setColumnPrefWidth(taskComplexityColumn, widths[3]);
        changed |= setColumnPrefWidth(taskPriorityColumn, widths[4]);
        return changed;
    }

    private double resolveTaskTableAvailableWidth() {
        double tableWidth = taskTable == null ? 0.0 : taskTable.getWidth();
        if (!Double.isFinite(tableWidth) || tableWidth <= 0.0) {
            return 980.0;
        }
        return Math.max(460.0, tableWidth - 18.0);
    }

    private double[] distributeColumnWidths(double availableWidth, double[] minWidths, double[] flexWeights) {
        if (minWidths == null || flexWeights == null || minWidths.length != flexWeights.length || minWidths.length == 0) {
            return new double[0];
        }
        double[] resolved = new double[minWidths.length];
        double minSum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < minWidths.length; i++) {
            minSum += minWidths[i];
            weightSum += Math.max(0.0, flexWeights[i]);
        }

        if (availableWidth <= minSum || weightSum <= 0.0) {
            System.arraycopy(minWidths, 0, resolved, 0, minWidths.length);
            return resolved;
        }

        double extra = availableWidth - minSum;
        for (int i = 0; i < minWidths.length; i++) {
            double ratio = Math.max(0.0, flexWeights[i]) / weightSum;
            resolved[i] = minWidths[i] + extra * ratio;
        }
        return resolved;
    }

    private boolean setColumnPrefWidth(TreeTableColumn<?, ?> column, double width) {
        if (column == null) {
            return false;
        }
        if (Math.abs(column.getPrefWidth() - width) < 0.5) {
            return false;
        }
        column.setPrefWidth(width);
        return true;
    }

    private boolean setColumnMinWidth(TreeTableColumn<?, ?> column, double width) {
        if (column == null) {
            return false;
        }
        if (Math.abs(column.getMinWidth() - width) < 0.5) {
            return false;
        }
        column.setMinWidth(width);
        return true;
    }

    private boolean setColumnMaxWidth(TreeTableColumn<?, ?> column, double width) {
        if (column == null) {
            return false;
        }
        if (Math.abs(column.getMaxWidth() - width) < 0.5) {
            return false;
        }
        column.setMaxWidth(width);
        return true;
    }

    private boolean setColumnVisible(TreeTableColumn<?, ?> column, boolean visible) {
        if (column == null || column.isVisible() == visible) {
            return false;
        }
        column.setVisible(visible);
        return true;
    }

    private boolean setStyleClassPresent(Region region, String styleClass, boolean present) {
        if (region == null || styleClass == null || styleClass.isBlank()) {
            return false;
        }
        List<String> styleClasses = region.getStyleClass();
        boolean contains = styleClasses.contains(styleClass);
        if (present && !contains) {
            styleClasses.add(styleClass);
            return true;
        }
        if (!present && contains) {
            styleClasses.remove(styleClass);
            return true;
        }
        return false;
    }

    private boolean setStyleClassPresent(Node node, String styleClass, boolean present) {
        if (!(node instanceof Region region)) {
            return false;
        }
        return setStyleClassPresent(region, styleClass, present);
    }

    private void applyCollapsedClass(Node node, boolean collapsed) {
        if (!(node instanceof Region region)) {
            return;
        }
        if (collapsed) {
            if (!region.getStyleClass().contains(SHELL_ZONE_COLLAPSED)) {
                region.getStyleClass().add(SHELL_ZONE_COLLAPSED);
            }
        } else {
            region.getStyleClass().remove(SHELL_ZONE_COLLAPSED);
        }
    }

    private void applyRegionWidth(Region region, double width) {
        if (region == null) {
            return;
        }
        region.setMinWidth(width);
        region.setPrefWidth(width);
        region.setMaxWidth(width);
    }

    private void applyBoundedRegionWidth(Region region, double preferredWidth, double minWidth, double maxWidth) {
        if (region == null) {
            return;
        }
        double safeMax = Math.max(minWidth, maxWidth);
        double safePreferred = clamp(preferredWidth, minWidth, safeMax);
        region.setMinWidth(minWidth);
        region.setPrefWidth(safePreferred);
        region.setMaxWidth(safeMax);
    }

    private void applyBoundedRegionHeight(Region region, double preferredHeight, double minHeight, double maxHeight) {
        if (region == null) {
            return;
        }
        double safeMax = Math.max(minHeight, maxHeight);
        double safePreferred = clamp(preferredHeight, minHeight, safeMax);
        region.setMinHeight(minHeight);
        region.setPrefHeight(safePreferred);
        region.setMaxHeight(safeMax);
    }

    private void installRegionClip(Region region, double arcSize) {
        if (region == null || region.getClip() != null) {
            return;
        }
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());
        if (arcSize > 0.0) {
            clip.setArcWidth(arcSize);
            clip.setArcHeight(arcSize);
        }
        region.setClip(clip);
    }

    private void applyRightPanelVerticalBounds() {
        if (rightPanelWrapper == null) {
            return;
        }
        rightPanelWrapper.setMinHeight(0);
        if (rightPanelBody != null) {
            rightPanelBody.setMinHeight(0);
            rightPanelBody.setMaxHeight(Double.MAX_VALUE);
        }
        if (rightPanelInspectorContentHost != null) {
            rightPanelInspectorContentHost.setMinHeight(0);
            rightPanelInspectorContentHost.setMaxHeight(Double.MAX_VALUE);
        }
        for (VBox stack : rightPanelInspectorTabStacks.values()) {
            if (stack == null) {
                continue;
            }
            stack.setMinHeight(0);
            stack.setMaxHeight(Double.MAX_VALUE);
        }
        for (ScrollPane tabScroll : rightPanelInspectorTabScrolls.values()) {
            if (tabScroll == null) {
                continue;
            }
            tabScroll.setMinHeight(0);
            tabScroll.setMaxHeight(Double.MAX_VALUE);
            tabScroll.setFitToWidth(true);
            tabScroll.setFitToHeight(false);
            tabScroll.setPannable(false);
            tabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            tabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tabScroll.setMinViewportHeight(0);
        }

        double availableHeight = resolveRightPanelAvailableHeight();
        boolean policyChanged = mainLayoutCoordinator.applyRightPanelInspectorHeightPolicy(availableHeight);
        rightPanelInspectorDisplayPolicy = mainLayoutCoordinator.rightPanelInspectorDisplayPolicy();
        if (policyChanged) {
            applyRightPanelLayoutPolicy();
        }
        if (!Double.isFinite(availableHeight) || availableHeight <= 0.0) {
            rightPanelWrapper.setPrefHeight(Region.USE_COMPUTED_SIZE);
            rightPanelWrapper.setMaxHeight(Double.MAX_VALUE);
            return;
        }

        double overlayInsets = rightPanelDisplayMode == UiRightContextMode.OVERLAY ? 16.0 : 0.0;
        double targetHeight = Math.max(180.0, availableHeight - overlayInsets);
        if (rightPanelDisplayMode == UiRightContextMode.OVERLAY) {
            applyBoundedRegionHeight(rightPanelWrapper, targetHeight, Math.min(160.0, targetHeight), targetHeight);
        } else {
            applyBoundedRegionHeight(rightPanelWrapper, targetHeight, 0.0, targetHeight);
        }
    }

    private double resolveRightPanelAvailableHeight() {
        double[] candidates = new double[] {
            adaptiveObservedScene == null ? Double.NaN : adaptiveObservedScene.getHeight(),
            centerShellStack == null ? Double.NaN : centerShellStack.getHeight(),
            mainLayoutShell == null || mainLayoutShell.root() == null ? Double.NaN : mainLayoutShell.root().getHeight(),
            getHeight()
        };
        double resolved = Double.NaN;
        for (double candidate : candidates) {
            if (!Double.isFinite(candidate) || candidate <= 0.0) {
                continue;
            }
            resolved = Double.isFinite(resolved) ? Math.min(resolved, candidate) : candidate;
        }
        return resolved;
    }

    private double resolveLeftNavSurfaceAvailableHeight() {
        double[] candidates = new double[] {
            adaptiveObservedScene == null ? Double.NaN : adaptiveObservedScene.getHeight(),
            centerShellStack == null ? Double.NaN : centerShellStack.getHeight(),
            mainLayoutShell == null || mainLayoutShell.root() == null ? Double.NaN : mainLayoutShell.root().getHeight(),
            getHeight()
        };
        double resolved = Double.NaN;
        for (double candidate : candidates) {
            if (!Double.isFinite(candidate) || candidate <= 0.0) {
                continue;
            }
            resolved = Double.isFinite(resolved) ? Math.min(resolved, candidate) : candidate;
        }
        return resolved;
    }

    private void scheduleAdaptiveHeightRefresh() {
        if (adaptiveHeightRefreshQueued) {
            return;
        }
        adaptiveHeightRefreshQueued = true;
        Platform.runLater(() -> {
            adaptiveHeightRefreshQueued = false;
            applyAdaptiveLayoutStateToShell(false);
        });
    }

    private double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private double sidebarExpandedWidth() {
        if (adaptiveLayoutState == null) {
            return UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT;
        }
        return adaptiveLayoutState.leftPanelWidth();
    }

    private double rightPanelExpandedWidth() {
        if (adaptiveLayoutState == null) {
            return UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT;
        }
        return adaptiveLayoutState.rightPanelWidth();
    }

    private Button createSidebarButton(String text, Ikon iconCode, String styleClass, String tooltipText) {
        return createSidebarButton(text, iconCode, styleClass, tooltipText, true);
    }

    private Button createSidebarButton(
        String text,
        Ikon iconCode,
        String styleClass,
        String tooltipText,
        boolean trackForSidebarCollapse
    ) {
        Button btn = new Button(text);
        FontIcon icon = FontIcon.of(iconCode, 18);
        btn.setGraphic(icon);
        btn.getStyleClass().addAll("sidebar-btn", styleClass);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setWrapText(false);
        btn.setTextOverrun(OverrunStyle.ELLIPSIS);
        
        if (tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.getStyleClass().add("sidebar-tooltip");
            tooltip.setShowDelay(javafx.util.Duration.millis(300));
            btn.setTooltip(tooltip);
        }
        if (trackForSidebarCollapse) {
            sidebarButtons.add(btn);
        }
        if (isSidebarCollapsed) {
            btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            btn.setAlignment(Pos.CENTER);
        }
        return btn;
    }

    private StackPane createCenterPanel() {
        VBox centerContent = new VBox(15);
        centerContent.getStyleClass().add("center-panel");

        Label header = new Label("📋 Панель задач");
        header.getStyleClass().add("panel-header");

        taskPanelAddTaskButton = new Button("Добавить задачу");
        taskPanelAddTaskButton.getStyleClass().add("task-panel-add-btn");
        taskPanelAddTaskButton.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 14));
        taskPanelAddTaskButton.setOnAction(e -> handleAddTask(null));
        taskPanelAddTaskButton.visibleProperty().bind(overlayHost.visibleProperty().not());
        taskPanelAddTaskButton.managedProperty().bind(taskPanelAddTaskButton.visibleProperty());

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        inlineTaskDockToggleButton = new Button();
        inlineTaskDockToggleButton.getStyleClass().add("inline-task-dock-toggle-btn");
        inlineTaskDockToggleButton.setOnAction(e -> toggleInlineOverlayVisibilityFromTaskPanel());
        HBox headerRow = new HBox(10, header, headerSpacer, taskPanelAddTaskButton);
        headerRow.getStyleClass().add("panel-header-row");
        headerRow.setAlignment(Pos.CENTER_LEFT);

        HBox inlineTaskDock = createInlineTaskDock();

        setupTaskTable();
        VBox.setVgrow(taskTable, Priority.ALWAYS);

        centerContent.getChildren().addAll(headerRow, inlineTaskDock, taskTable);

        rightPanelQuickToggleBtn = new Button();
        rightPanelQuickToggleBtn.getStyleClass().add("right-panel-quick-toggle-btn");
        rightPanelQuickToggleBtn.setGraphic(FontIcon.of(MaterialDesignM.MENU_OPEN, 16));
        rightPanelQuickToggleBtn.setTooltip(new Tooltip("Открыть контекстную панель"));
        rightPanelQuickToggleBtn.setOnAction(e -> toggleRightPanel());

        StackPane centerStack = new StackPane(
            centerContent,
            createRightPanelOverlayHost(),
            rightPanelQuickToggleBtn,
            createOverlayHost()
        );
        centerStack.getStyleClass().add("shell-zone-center");
        centerStack.setAlignment(Pos.CENTER);
        StackPane.setAlignment(rightPanelQuickToggleBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(rightPanelQuickToggleBtn, new Insets(12, 12, 0, 0));
        centerShellStack = centerStack;
        centerShellStack.widthProperty().addListener((obs, oldVal, newVal) -> applyRightPanelVerticalBounds());
        centerShellStack.heightProperty().addListener((obs, oldVal, newVal) -> scheduleAdaptiveHeightRefresh());
        return centerStack;
    }

    private HBox createInlineTaskDock() {
        HBox dock = new HBox(8);
        dock.getStyleClass().add("inline-task-dock");
        dock.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("INLINE");
        title.getStyleClass().add("inline-task-dock-title");
        title.setMinWidth(Region.USE_PREF_SIZE);

        inlineTaskDockTabStrip.getStyleClass().add("inline-task-dock-tabs");
        inlineTaskDockTabStrip.setAlignment(Pos.CENTER_LEFT);
        inlineTaskDockTabStrip.setMinWidth(Region.USE_PREF_SIZE);
        inlineTaskDockTabStrip.setFocusTraversable(false);

        inlineTaskDockTabStripScroll.getStyleClass().add("inline-task-dock-scroll");
        inlineTaskDockTabStripScroll.setContent(inlineTaskDockTabStrip);
        inlineTaskDockTabStripScroll.setFitToHeight(true);
        inlineTaskDockTabStripScroll.setFitToWidth(false);
        inlineTaskDockTabStripScroll.setPannable(true);
        inlineTaskDockTabStripScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        inlineTaskDockTabStripScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        inlineTaskDockTabStripScroll.setFocusTraversable(false);
        inlineTaskDockTabStripScroll.setMinHeight(30);
        inlineTaskDockTabStripScroll.setPrefHeight(30);
        inlineTaskDockTabStripScroll.setMaxHeight(30);
        HBox.setHgrow(inlineTaskDockTabStripScroll, Priority.ALWAYS);

        dock.getChildren().addAll(title, inlineTaskDockTabStripScroll);
        if (inlineTaskDockToggleButton != null) {
            dock.getChildren().add(inlineTaskDockToggleButton);
        }
        refreshInlineTaskDock();
        return dock;
    }

    @SuppressWarnings("unchecked")
    private void setupTaskTable() {
        taskTitleColumn = new TreeTableColumn<>("Название");
        taskTitleColumn.setCellValueFactory(c -> c.getValue().getValue().titleProperty());
        taskTitleColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        taskTitleColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    Task task = getTreeTableRow().getItem();
                    if (task != null) {
                        setText(item);
                        setAlignment(Pos.CENTER_LEFT);
                        setGraphicTextGap(6);
                        if (task.isCompleted()) {
                            FontIcon checkIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 14);
                            checkIcon.getStyleClass().add("task-icon-completed");
                            setGraphic(checkIcon);
                            setStyle("-fx-opacity: 0.7; -fx-text-fill: #40a02b;");
                            String tooltip = "Выполнено";
                            if (task.getCompletedDate() != null) {
                                tooltip += " " + task.getCompletedDate();
                            }
                            setTooltip(new Tooltip(tooltip));
                        } else if (!task.isStarted()) {
                            FontIcon clockIcon = FontIcon.of(MaterialDesignC.CLOCK_OUTLINE, 14);
                            clockIcon.getStyleClass().add("task-icon-scheduled");
                            setGraphic(clockIcon);
                            setStyle("-fx-opacity: 0.6; -fx-text-fill: #7f8c8d;");
                            setTooltip(new Tooltip("Запланировано на " + TaskScheduleFormatter.formatStart(task)));
                        } else {
                            setGraphic(null);
                            setStyle("");
                            setTooltip(null);
                        }
                    } else {
                        setText(item);
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });

        taskDeadlineColumn = new TreeTableColumn<>("Дедлайн");
        taskDeadlineColumn.setCellValueFactory(c ->
            new javafx.beans.property.ReadOnlyObjectWrapper<>(c.getValue().getValue().getDeadlineDateTime()));
        taskDeadlineColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTreeTableRow() == null || getTreeTableRow().getItem() == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                Task task = getTreeTableRow().getItem();
                String formattedDeadline = TaskScheduleFormatter.formatDeadline(task);
                setText(formattedDeadline);
                setTooltip(new Tooltip(formattedDeadline));
            }
        });

        taskComplexityColumn = new TreeTableColumn<>("Сложность");
        taskComplexityColumn.setCellValueFactory(c -> c.getValue().getValue().complexityProperty());
        taskComplexityColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label lbl = new Label(item + "/10");
                    lbl.setStyle("-fx-text-fill: #7f8c8d;");
                    setGraphic(lbl);
                }
            }
        });

        taskPriorityColumn = new TreeTableColumn<>("ИИ-Приоритет");
        taskPriorityColumn.setCellValueFactory(c -> c.getValue().getValue().smartPriorityProperty());
        taskPriorityColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(String.format("%.1f", item.doubleValue()));
                    double val = item.doubleValue();
                    if (val >= 7) badge.getStyleClass().add("priority-high");
                    else if (val >= 4) badge.getStyleClass().add("priority-medium");
                    else badge.getStyleClass().add("priority-low");
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Колонка тегов/категорий
        taskTagsColumn = new TreeTableColumn<>("Теги");
        taskTagsColumn.setCellValueFactory(c -> c.getValue().getValue().tagsProperty());
        taskTagsColumn.setCellFactory(col -> new TreeTableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    setTooltip(null);
                } else {
                    HBox tagsBox = new HBox(4);
                    tagsBox.setAlignment(Pos.CENTER_LEFT);
                    String[] tagArray = item.split(",");
                    Tooltip tagsTooltip = new Tooltip(buildTaskTagsTooltipText(tagArray));
                    Tooltip.install(tagsBox, tagsTooltip);
                    int shown = 0;
                    for (String tag : tagArray) {
                        String trimmed = tag.trim();
                        if (trimmed.isEmpty()) continue;
                        if (shown >= 2) {
                            Label more = new Label("+" + (tagArray.length - shown));
                            more.getStyleClass().add("tag-badge-more");
                            Tooltip.install(more, tagsTooltip);
                            tagsBox.getChildren().add(more);
                            break;
                        }
                        Label tagLabel = new Label(trimmed);
                        tagLabel.getStyleClass().add("tag-badge");
                        Tooltip.install(tagLabel, tagsTooltip);
                        // Цвет по категории
                        String lower = trimmed.toLowerCase();
                        if (lower.contains("работа") || lower.contains("проект")) {
                            tagLabel.getStyleClass().add("tag-work");
                        } else if (lower.contains("личн") || lower.contains("дом")) {
                            tagLabel.getStyleClass().add("tag-personal");
                        } else if (lower.contains("срочн") || lower.contains("важн")) {
                            tagLabel.getStyleClass().add("tag-urgent");
                        } else if (lower.contains("учёб") || lower.contains("учеб")) {
                            tagLabel.getStyleClass().add("tag-study");
                        } else if (lower.contains("финанс") || lower.contains("деньг")) {
                            tagLabel.getStyleClass().add("tag-finance");
                        } else if (lower.contains("идея") || lower.contains("план")) {
                            tagLabel.getStyleClass().add("tag-idea");
                        }
                        tagsBox.getChildren().add(tagLabel);
                        shown++;
                    }
                    setGraphic(tagsBox);
                    setText(null);
                    setTooltip(tagsTooltip);
                }
            }
        });

        taskActionsColumn = new TreeTableColumn<>("");
        taskActionsColumn.setMinWidth(116);
        taskActionsColumn.setMaxWidth(144);
        taskActionsColumn.setResizable(false);
        taskActionsColumn.setSortable(false);
        taskActionsColumn.setCellFactory(col -> new TreeTableCell<>() {
            private final HBox actionsBox = new HBox(4);
            private final Button completeBtn = new Button();
            private final Button editBtn = new Button();
            private final Button deleteBtn = new Button();
            {
                // Complete button
                completeBtn.getStyleClass().add("complete-btn");
                completeBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                completeBtn.setMinSize(32, 32);
                completeBtn.setMaxSize(36, 36);
                completeBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        handleToggleComplete(item.getValue());
                    }
                });
                
                // Edit button
                FontIcon editIcon = FontIcon.of(MaterialDesignP.PENCIL_OUTLINE, 16);
                editIcon.setIconColor(javafx.scene.paint.Color.web("#3498db"));
                editBtn.setGraphic(editIcon);
                editBtn.getStyleClass().add("edit-btn");
                editBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                editBtn.setMinSize(32, 32);
                editBtn.setMaxSize(36, 36);
                editBtn.setTooltip(new Tooltip("Редактировать"));
                editBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        handleEditTask(item.getValue());
                    }
                });
                
                // Delete button
                FontIcon icon = FontIcon.of(MaterialDesignD.DELETE_OUTLINE, 16);
                icon.setIconColor(javafx.scene.paint.Color.web("#e74c3c"));
                deleteBtn.setGraphic(icon);
                deleteBtn.getStyleClass().add("delete-btn");
                deleteBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                deleteBtn.setMinSize(32, 32);
                deleteBtn.setMaxSize(36, 36);
                deleteBtn.setTooltip(new Tooltip("Удалить"));
                deleteBtn.setOnAction(e -> {
                    TreeItem<Task> item = getTreeTableRow().getTreeItem();
                    if (item != null && item.getValue() != null) {
                        Task task = item.getValue();
                        showDeleteConfirmDialog(task);
                    }
                });
                
                actionsBox.setAlignment(Pos.CENTER);
                actionsBox.getChildren().addAll(completeBtn, editBtn, deleteBtn);
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                TreeItem<Task> treeItem = getTreeTableRow().getTreeItem();
                if (empty || treeItem == null || treeItem.getValue() == null) {
                    setGraphic(null);
                } else {
                    Task task = treeItem.getValue();
                    // Update complete button icon based on task state
                    FontIcon completeIcon;
                    if (task.isCompleted()) {
                        completeIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 16);
                        completeIcon.setIconColor(javafx.scene.paint.Color.web("#40a02b"));
                        completeBtn.setTooltip(new Tooltip("Отменить выполнение"));
                    } else {
                        completeIcon = FontIcon.of(MaterialDesignC.CHECKBOX_BLANK_CIRCLE_OUTLINE, 16);
                        completeIcon.setIconColor(javafx.scene.paint.Color.web("#7f8c8d"));
                        completeBtn.setTooltip(new Tooltip("Отметить выполненной"));
                    }
                    completeBtn.setGraphic(completeIcon);
                    setGraphic(actionsBox);
                }
                setAlignment(Pos.CENTER);
            }
        });

        taskTable.getColumns().setAll(
            taskTitleColumn,
            taskTagsColumn,
            taskDeadlineColumn,
            taskComplexityColumn,
            taskPriorityColumn,
            taskActionsColumn
        );
        rootItem.setExpanded(true);
        taskTable.setRoot(rootItem);
        taskTable.setShowRoot(false);
        taskTable.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        taskTable.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        taskTable.setPlaceholder(createTaskTableEmptyState());
        taskTable.setRowFactory(table -> new TreeTableRow<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("critical-path-row", "critical-path-chain-row");
                if (empty || item == null) {
                    setTooltip(null);
                    return;
                }
                CriticalPathTaskMetrics metrics = criticalMetricsByTaskId.get(item.getId());
                if (metrics != null && criticalChainTaskIds.contains(item.getId())) {
                    getStyleClass().add("critical-path-chain-row");
                } else if (metrics != null && metrics.critical()) {
                    getStyleClass().add("critical-path-row");
                }
                setTooltip(buildTaskRowTooltip(item, metrics));
            }
        });
        taskTable.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && item.getValue() != null) {
                updateDetailPanel(item.getValue());
            } else {
                updateCriticalPathPanel(null);
            }
        });
        taskTable.widthProperty().addListener((obs, oldWidth, newWidth) -> applyAdaptiveTaskTableLayout());
        applyAdaptiveTaskTableLayout();
    }

    private Node createTaskTableEmptyState() {
        VBox emptyState = new VBox(10);
        emptyState.getStyleClass().add("task-table-empty-state");
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setMaxWidth(420);

        taskTableEmptyTitleLabel = new Label();
        taskTableEmptyTitleLabel.getStyleClass().add("task-table-empty-title");

        taskTableEmptyDescriptionLabel = new Label();
        taskTableEmptyDescriptionLabel.getStyleClass().add("task-table-empty-description");
        taskTableEmptyDescriptionLabel.setWrapText(true);
        taskTableEmptyDescriptionLabel.setTextAlignment(TextAlignment.CENTER);
        taskTableEmptyDescriptionLabel.setAlignment(Pos.CENTER);

        taskTableEmptyActionButton = new Button();
        taskTableEmptyActionButton.getStyleClass().add("task-table-empty-btn");

        emptyState.getChildren().addAll(taskTableEmptyTitleLabel, taskTableEmptyDescriptionLabel, taskTableEmptyActionButton);
        updateTaskTableEmptyState();
        return emptyState;
    }

    private void updateTaskTableEmptyState() {
        if (taskTableEmptyTitleLabel == null || taskTableEmptyDescriptionLabel == null || taskTableEmptyActionButton == null) {
            return;
        }
        String title;
        String description;
        String buttonText;
        Ikon buttonIcon;
        Runnable buttonAction;
        switch (currentTaskListViewMode) {
            case SCHEDULED -> {
                title = "В планах пока пусто";
                description = "Задачи с будущей датой начала появятся здесь.";
                buttonText = "Ко всем задачам";
                buttonIcon = MaterialDesignV.VIEW_LIST;
                buttonAction = this::showAllTasks;
            }
            case ARCHIVED -> {
                title = "Архив пуст";
                description = "Здесь будут задачи, которые вы отправили в архив.";
                buttonText = "Ко всем задачам";
                buttonIcon = MaterialDesignV.VIEW_LIST;
                buttonAction = this::showAllTasks;
            }
            case URGENT -> {
                title = "Срочных задач нет";
                description = "Сейчас нет задач с высоким приоритетом.";
                buttonText = "Ко всем задачам";
                buttonIcon = MaterialDesignV.VIEW_LIST;
                buttonAction = this::showAllTasks;
            }
            case TAG_FILTER -> {
                String tag = currentTaskTagFilter == null ? "" : currentTaskTagFilter.trim();
                title = tag.isEmpty() ? "По этому тегу ничего нет" : "Нет задач с тегом \"" + tag + "\"";
                description = "Попробуйте другой тег или вернитесь ко всем задачам.";
                buttonText = "Ко всем задачам";
                buttonIcon = MaterialDesignV.VIEW_LIST;
                buttonAction = this::showAllTasks;
            }
            case ALL -> {
                title = "Пока нет задач";
                description = "Создайте первую задачу, чтобы начать планирование.";
                buttonText = "Создать задачу";
                buttonIcon = MaterialDesignP.PLUS;
                buttonAction = () -> handleAddTask(null);
            }
            default -> {
                title = "Список пуст";
                description = "В этом разделе пока нет задач.";
                buttonText = "Ко всем задачам";
                buttonIcon = MaterialDesignV.VIEW_LIST;
                buttonAction = this::showAllTasks;
            }
        }
        taskTableEmptyTitleLabel.setText(title);
        taskTableEmptyDescriptionLabel.setText(description);
        taskTableEmptyActionButton.setText(buttonText);
        taskTableEmptyActionButton.setGraphic(FontIcon.of(buttonIcon, 14));
        taskTableEmptyActionButton.setOnAction(event -> buttonAction.run());
    }

    private String buildTaskTagsTooltipText(String[] tagArray) {
        if (tagArray == null || tagArray.length == 0) {
            return "";
        }
        List<String> normalizedTags = new ArrayList<>();
        for (String tag : tagArray) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty() && !normalizedTags.contains(trimmed)) {
                normalizedTags.add(trimmed);
            }
        }
        if (normalizedTags.isEmpty()) {
            return "";
        }
        return "Все теги: " + String.join(", ", normalizedTags);
    }

    private void filterScheduled() {
        showTasks(presenter.filterScheduled(tasks), TaskListViewMode.SCHEDULED, "");
    }

    private List<String> collectTaskIds(List<Task> selectedTasks) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (selectedTasks == null) {
            return List.of();
        }
        for (Task task : selectedTasks) {
            if (task == null || task.getId() == null || task.getId().isBlank()) {
                continue;
            }
            ids.add(task.getId());
        }
        return new ArrayList<>(ids);
    }
    
    private void showDeleteConfirmDialog(Task task) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Удаление задачи");
        dialog.setHeaderText(null);
        
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(420);
        
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        
        // Warning icon
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(64, 64);
        iconBox.setMaxSize(64, 64);
        iconBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.15)" : "rgba(210,15,57,0.1)") + "; -fx-background-radius: 50%;");
        FontIcon warningIcon = FontIcon.of(MaterialDesignD.DELETE_ALERT, 32);
        warningIcon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f38ba8" : "#d20f39"));
        iconBox.getChildren().add(warningIcon);
        
        // Title
        Label titleLbl = new Label("Удалить задачу?");
        titleLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        
        // Task name
        Label taskNameLbl = new Label("\"" + task.getTitle() + "\"");
        taskNameLbl.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#f38ba8" : "#d20f39") + ";");
        taskNameLbl.setWrapText(true);
        taskNameLbl.setMaxWidth(350);
        taskNameLbl.setAlignment(Pos.CENTER);
        
        // Warning message
        VBox warningBox = new VBox(6);
        warningBox.setAlignment(Pos.CENTER);
        warningBox.setPadding(new Insets(12));
        warningBox.setStyle("-fx-background-color: " + (isDark ? "rgba(243,139,168,0.1)" : "rgba(210,15,57,0.05)") + "; -fx-background-radius: 10;");
        
        Label warningLbl = new Label("⚠️ Это действие нельзя отменить");
        warningLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: " + (isDark ? "#f9e2af" : "#df8e1d") + ";");
        
        int subtaskCount = task.getSubtasks().size();
        if (subtaskCount > 0) {
            Label subtaskWarning = new Label("Также будут удалены " + subtaskCount + " подзадач");
            subtaskWarning.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isDark ? "#a6adc8" : "#6c6f85") + ";");
            warningBox.getChildren().addAll(warningLbl, subtaskWarning);
        } else {
            warningBox.getChildren().add(warningLbl);
        }
        
        content.getChildren().addAll(iconBox, titleLbl, taskNameLbl, warningBox);
        dialogPane.setContent(content);
        
        // Buttons
        ButtonType deleteBtn = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteBtn, cancelBtn);
        
        // Style delete button
        Button deleteButton = (Button) dialogPane.lookupButton(deleteBtn);
        deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f38ba8" : "#d20f39") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;");
        deleteButton.setOnMouseEntered(e -> deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f5a0b5" : "#e8304a") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;"));
        deleteButton.setOnMouseExited(e -> deleteButton.setStyle("-fx-background-color: " + (isDark ? "#f38ba8" : "#d20f39") + "; " +
                             "-fx-text-fill: " + (isDark ? "#11111b" : "white") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;"));
        
        // Style cancel button
        Button cancelButton = (Button) dialogPane.lookupButton(cancelBtn);
        cancelButton.setStyle("-fx-background-color: " + (isDark ? "#45475a" : "#ccd0da") + "; " +
                             "-fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + "; " +
                             "-fx-font-weight: bold; -fx-background-radius: 10; -fx-padding: 10 24;");
        
        dialog.showAndWait().ifPresent(result -> {
            if (result == deleteBtn) {
                UndoRedoManager.CommandResult commandResult = presenter.deleteTaskUndoable(task.getId());
                if (!commandResult.successful()) {
                    showAlert("Не удалось удалить задачу: " + commandResult.message());
                }
            }
        });
    }

    private void refreshTree() {
        switch (currentTaskListViewMode) {
            case SCHEDULED -> filterScheduled();
            case ARCHIVED -> showArchivedTasks();
            case URGENT -> filterUrgent();
            case TAG_FILTER -> {
                if (currentTaskTagFilter == null || currentTaskTagFilter.isBlank()) {
                    showAllTasks();
                } else {
                    showTasks(presenter.filterByTag(tasks, currentTaskTagFilter), TaskListViewMode.TAG_FILTER, currentTaskTagFilter);
                }
            }
            case ALL -> showAllTasks();
        }
    }

    private void showTasks(List<Task> toShow) {
        showTasks(toShow, TaskListViewMode.ALL, "");
    }

    private void showTasks(List<Task> toShow, TaskListViewMode mode, String tagFilter) {
        currentTaskListViewMode = mode == null ? TaskListViewMode.ALL : mode;
        currentTaskTagFilter = tagFilter == null ? "" : tagFilter;
        updateTaskTableEmptyState();
        rootItem.getChildren().clear();
        if (toShow == null) {
            refreshDependencyAndCriticalPathData();
            taskTable.refresh();
            return;
        }
        for (Task task : toShow) {
            if (task.isArchived()) {
                continue;
            }
            TreeItem<Task> item = new TreeItem<>(task);
            item.setExpanded(true);
            for (Task sub : task.getSubtasks()) {
                if (!sub.isArchived()) {
                    item.getChildren().add(new TreeItem<>(sub));
                }
            }
            rootItem.getChildren().add(item);
        }
        refreshDependencyAndCriticalPathData();
        taskTable.refresh();
    }

    private void showAllTasks() {
        showTasks(tasks, TaskListViewMode.ALL, "");
    }

    private Node createRightPanel() {
        rightPanelSectionNodes.clear();
        rightPanelInspectorTabSectionIds.clear();
        rightPanelInspectorTabButtons.clear();
        rightPanelInspectorTabStacks.clear();
        rightPanelInspectorTabScrolls.clear();
        Node detailsSection = createRightPanelDetailsSection();
        Node descriptionSection = createRightPanelDescriptionSection();
        Node insightSection = createRightPanelInsightSection();
        Node criticalPathSection = createCriticalPathPanel();
        rightPanelSectionNodes.put(RightPanelLayoutService.SECTION_DETAILS, detailsSection);
        rightPanelSectionNodes.put(RightPanelLayoutService.SECTION_DESCRIPTION, descriptionSection);
        rightPanelSectionNodes.put(RightPanelLayoutService.SECTION_AI, insightSection);
        rightPanelSectionNodes.put(RightPanelLayoutService.SECTION_PATH, criticalPathSection);
        initializeRightPanelInspectorTabSections();

        rightPanelHeader = new HBox();
        rightPanelHeader.getStyleClass().add("right-panel-header");
        rightPanelHeader.setAlignment(Pos.CENTER_RIGHT);
        rightPanelHeader.setPadding(new Insets(6, 8, 4, 8));
        rightPanelHeader.setSpacing(8);

//        rightPanelModeLabel = new Label();
//        rightPanelModeLabel.getStyleClass().add("right-panel-mode-indicator");
//
//        rightPanelStateLabel = new Label();
//        rightPanelStateLabel.getStyleClass().add("right-panel-state-indicator");

        rightPanelToggleBtn = new Button();
        rightPanelToggleBtn.setGraphic(FontIcon.of(MaterialDesignM.MENU_OPEN, 18));
        rightPanelToggleBtn.getStyleClass().add("sidebar-toggle-btn");
        rightPanelToggleBtn.setTooltip(new Tooltip("Свернуть/Развернуть панель"));
        rightPanelToggleBtn.setOnAction(e -> toggleRightPanel());

        rightPanelHeader.getChildren().addAll(rightPanelToggleBtn);

        rightPanelInspectorTabStrip = createRightPanelInspectorTabStrip();
        rightPanelInspectorContentHost = createRightPanelInspectorContentHost();
//        rightPanelInspectorFooterLabel = new Label();
//        rightPanelInspectorFooterLabel.getStyleClass().add("right-panel-inspector-footer");

        rightPanelBody = new VBox(8);
        rightPanelBody.getStyleClass().add("right-panel-content");
        rightPanelBody.getStyleClass().add("right-panel-body");
        rightPanelBody.setMinHeight(0);
        rightPanelBody.setMaxHeight(Double.MAX_VALUE);
        rightPanelBody.getChildren().addAll(
            rightPanelInspectorTabStrip,
            rightPanelInspectorContentHost
//            rightPanelInspectorFooterLabel
        );
        VBox.setVgrow(rightPanelInspectorContentHost, Priority.ALWAYS);
        VBox.setVgrow(rightPanelBody, Priority.ALWAYS);

        rightPanelContent = rightPanelBody;

        // Используем BorderPane для правильного растягивания
        rightPanelWrapper = new BorderPane();
        rightPanelWrapper.setTop(rightPanelHeader);
        rightPanelWrapper.setCenter(rightPanelBody);
        rightPanelWrapper.setMinHeight(0);
        rightPanelWrapper.getStyleClass().addAll("right-panel-scroll", "shell-zone-right");
        rightPanelWrapper.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);
        installRegionClip(rightPanelWrapper, 14.0);
        rightPanelDisplayPolicy = mainLayoutCoordinator.rightPanelDisplayPolicy();
        applyRightPanelLayoutPolicy();
        if (isRightPanelCollapsed) {
            applyRegionWidth(rightPanelWrapper, RIGHT_PANEL_COLLAPSED_WIDTH);
        } else {
            applyBoundedRegionWidth(
                rightPanelWrapper,
                rightPanelExpandedWidth(),
                RIGHT_PANEL_COLLAPSIBLE_MIN_WIDTH,
                UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
            );
        }
        rightPanelContent.setVisible(!isRightPanelCollapsed);
        rightPanelContent.setManaged(!isRightPanelCollapsed);
        rightPanelHeader.setAlignment(isRightPanelCollapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
        rightPanelHeader.setPadding(isRightPanelCollapsed ? new Insets(6, 0, 6, 0) : new Insets(6, 8, 4, 8));
        applyRightPanelVerticalBounds();

        return rightPanelWrapper;
    }

    private void initializeRightPanelInspectorTabSections() {
        rightPanelInspectorTabSectionIds.put(RightPanelInspectorTab.PROPERTIES, INSPECTOR_TAB_PROPERTIES_SECTION_IDS);
        rightPanelInspectorTabSectionIds.put(RightPanelInspectorTab.DESCRIPTION, INSPECTOR_TAB_DESCRIPTION_SECTION_IDS);
        rightPanelInspectorTabSectionIds.put(RightPanelInspectorTab.ANALYTICS, INSPECTOR_TAB_ANALYTICS_SECTION_IDS);
    }

    private HBox createRightPanelInspectorTabStrip() {
        HBox strip = new HBox(6);
        strip.getStyleClass().add("right-panel-tabbed-strip");
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setPadding(new Insets(4, 8, 0, 8));
        strip.setFocusTraversable(true);
        strip.setAccessibleText("Вкладки инспектора: используйте стрелки, Enter или пробел");
        strip.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);

        for (RightPanelInspectorTab tab : RightPanelInspectorTab.baselineOrder()) {
            Button button = createRightPanelInspectorTabButton(tab);
            rightPanelInspectorTabButtons.put(tab, button);
            strip.getChildren().add(button);
        }
        return strip;
    }

    private Button createRightPanelInspectorTabButton(RightPanelInspectorTab tab) {
        Button button = new Button(resolveRightInspectorTabLabel(tab));
        button.getStyleClass().add("right-panel-tab-btn");
        button.setFocusTraversable(true);
        button.getProperties().put("inspectorTab", tab);
        button.focusedProperty().addListener((obs, oldFocused, focused) ->
            setStyleClassPresent(button, "right-panel-tab-btn-focused", focused)
        );
        button.setOnAction(event -> {
            showInInspectorTab(tab, false);
        });
        button.setAccessibleText("Вкладка: " + resolveRightInspectorTabLabel(tab));
        button.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);
        return button;
    }

    private StackPane createRightPanelInspectorContentHost() {
        StackPane host = new StackPane();
        host.getStyleClass().add("right-panel-tab-content-host");
        host.setMinHeight(0);
        host.setMaxHeight(Double.MAX_VALUE);
        host.setFocusTraversable(true);
        host.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);

        for (RightPanelInspectorTab tab : RightPanelInspectorTab.baselineOrder()) {
            VBox stack = new VBox(8);
            stack.getStyleClass().add("right-panel-section-stack");
            stack.getStyleClass().add("right-panel-tab-stack-" + tab.id());
            stack.setFillWidth(true);
            stack.setMinHeight(0);
            populateRightPanelInspectorTabStack(tab, stack);
            rightPanelInspectorTabStacks.put(tab, stack);

            ScrollPane scroll = new ScrollPane(stack);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(false);
            scroll.setPannable(false);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setMinHeight(0);
            scroll.setMinViewportHeight(0);
            scroll.setFocusTraversable(true);
            scroll.getStyleClass().addAll("properties-scroll", "right-panel-tab-scroll", "right-panel-tab-scroll-" + tab.id());
            scroll.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);
            rightPanelInspectorTabScrolls.put(tab, scroll);
            host.getChildren().add(scroll);
        }
        return host;
    }

    private void populateRightPanelInspectorTabStack(RightPanelInspectorTab tab, VBox stack) {
        if (tab == null || stack == null) {
            return;
        }
        List<String> sectionIds = rightPanelInspectorTabSectionIds.get(tab);
        if (sectionIds == null || sectionIds.isEmpty()) {
            stack.getChildren().clear();
            return;
        }
        List<Node> sections = new ArrayList<>();
        for (String sectionId : sectionIds) {
            Node section = rightPanelSectionNodes.get(sectionId);
            if (section != null) {
                setNodeVisibility(section, true);
                sections.add(section);
            }
        }
        stack.getChildren().setAll(sections);
    }

    private void handleRightInspectorTabKeyPressed(KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        if (rightPanelInspectorDisplayPolicy == null || rightPanelInspectorDisplayPolicy.tabs().isEmpty()) {
            return;
        }
        Node sourceNode = event.getTarget() instanceof Node node ? node : null;
        boolean tabStripFocused = isNodeInside(sourceNode, rightPanelInspectorTabStrip);
        boolean tabContentFocused = isNodeInside(sourceNode, rightPanelInspectorContentHost);
        Button focusedTabButton = findRightInspectorTabButton(event.getTarget());
        boolean handled = false;
        if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
            cycleRightInspectorTab(event.isShiftDown() ? -1 : 1);
            handled = true;
        } else if (!event.isControlDown() && event.getCode() == KeyCode.TAB) {
            if (tabStripFocused && !event.isShiftDown()) {
                handled = focusActiveInspectorTabContent();
            } else if (tabContentFocused && event.isShiftDown()) {
                handled = focusRightInspectorTabButton(resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy));
            }
        } else if (tabStripFocused && event.getCode() == KeyCode.RIGHT) {
            cycleRightInspectorTab(1);
            handled = true;
        } else if (tabStripFocused && event.getCode() == KeyCode.LEFT) {
            cycleRightInspectorTab(-1);
            handled = true;
        } else if (tabStripFocused && event.getCode() == KeyCode.HOME) {
            selectRightInspectorTabByIndex(0);
            handled = true;
        } else if (tabStripFocused && event.getCode() == KeyCode.END) {
            List<RightPanelInspectorTab> tabs = rightPanelInspectorDisplayPolicy.tabs();
            selectRightInspectorTabByIndex(Math.max(0, tabs.size() - 1));
            handled = true;
        } else if (tabStripFocused && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
            RightPanelInspectorTab focusedTab = resolveRightInspectorTabFromButton(focusedTabButton);
            showInInspectorTab(focusedTab, true);
            handled = true;
        } else if (event.getCode() == KeyCode.ESCAPE
            && rightPanelDisplayMode == UiRightContextMode.OVERLAY
            && !isRightPanelCollapsed) {
            closeRightPanelOverlayIfOpen();
            handled = true;
        }
        if (handled) {
            event.consume();
        }
    }

    private void cycleRightInspectorTab(int step) {
        if (step == 0 || rightPanelInspectorDisplayPolicy == null) {
            return;
        }
        List<RightPanelInspectorTab> tabs = rightPanelInspectorDisplayPolicy.tabs();
        if (tabs.isEmpty()) {
            return;
        }
        RightPanelInspectorTab active = resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy);
        int index = tabs.indexOf(active);
        if (index < 0) {
            index = 0;
        }
        int nextIndex = Math.floorMod(index + step, tabs.size());
        selectRightInspectorTabByIndex(nextIndex);
    }

    private void selectRightInspectorTabByIndex(int tabIndex) {
        if (rightPanelInspectorDisplayPolicy == null) {
            return;
        }
        List<RightPanelInspectorTab> tabs = rightPanelInspectorDisplayPolicy.tabs();
        if (tabs.isEmpty()) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(tabIndex, tabs.size() - 1));
        RightPanelInspectorTab nextTab = tabs.get(safeIndex);
        showInInspectorTab(nextTab, true);
    }

    private Button findRightInspectorTabButton(Object eventTarget) {
        if (!(eventTarget instanceof Node node)) {
            return null;
        }
        Node cursor = node;
        while (cursor != null) {
            if (cursor instanceof Button button && button.getProperties().get("inspectorTab") instanceof RightPanelInspectorTab) {
                return button;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private RightPanelInspectorTab resolveRightInspectorTabFromButton(Button button) {
        if (button == null) {
            return resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy);
        }
        Object tab = button.getProperties().get("inspectorTab");
        return tab instanceof RightPanelInspectorTab inspectorTab
            ? inspectorTab
            : resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy);
    }

    private boolean focusActiveInspectorTabContent() {
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(rightPanelInspectorDisplayPolicy);
        ScrollPane activeScroll = rightPanelInspectorTabScrolls.get(activeTab);
        if (!isFocusableNode(activeScroll)) {
            return false;
        }
        Platform.runLater(activeScroll::requestFocus);
        return true;
    }

    private boolean focusRightInspectorTabButton(RightPanelInspectorTab tab) {
        Button button = rightPanelInspectorTabButtons.get(tab);
        if (!isFocusableNode(button)) {
            return false;
        }
        Platform.runLater(button::requestFocus);
        return true;
    }

    private Node createRightPanelDetailsSection() {
        VBox detailsSection = new VBox(10);
        detailsSection.getStyleClass().add("details-section");
        detailsSection.setPadding(new Insets(15));
        detailsSection.setFillWidth(true);

        HBox detailsHeader = new HBox(8);
        detailsHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon infoIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 18);
        infoIcon.getStyleClass().add("panel-header-icon");
        Label detailsTitle = new Label("Детали задачи");
        detailsTitle.getStyleClass().add("section-title-main");
        detailsHeader.getChildren().addAll(infoIcon, detailsTitle);

        detailTitle.getStyleClass().add("detail-title-large");
        detailTitle.setWrapText(true);
        detailQuickFactsLabel.getStyleClass().add("detail-quick-facts");
        detailQuickFactsLabel.setWrapText(true);
        detailStatusSummaryLabel.getStyleClass().add("detail-status-summary");
        detailStatusSummaryLabel.setWrapText(true);

        detailLinkedNotes.getStyleClass().add("backlinks-pane");
        detailLinkedNotes.setHgap(6);
        detailLinkedNotes.setVgap(6);

        VBox summaryBlock = new VBox(6, detailQuickFactsLabel, detailStatusSummaryLabel);
        summaryBlock.getStyleClass().add("details-summary-block");

        VBox primaryPropertiesContent = new VBox(8);
        primaryPropertiesContent.getStyleClass().add("right-panel-primary-properties");
        primaryPropertiesContent.getChildren().addAll(
            createDetailRow(MaterialDesignC.CALENDAR_CLOCK, "Дедлайн", detailDeadline),
            createDetailRow(MaterialDesignT.TIMER_SAND, "Сложность", detailComplexity),
            createDetailRow(MaterialDesignT.TARGET, "Приоритет", detailPriority)
        );

        VBox secondaryPropertiesContent = new VBox(8);
        secondaryPropertiesContent.getStyleClass().add("right-panel-secondary-properties");
        secondaryPropertiesContent.getChildren().addAll(
            createDetailRow(MaterialDesignT.TAG_OUTLINE, "Теги", detailTags),
            createDetailRow(MaterialDesignR.REPEAT, "Повтор", detailRecurrence),
            createDetailRow(MaterialDesignL.LINK_VARIANT, "Зависит от", detailDependsOn),
            createDetailRow(MaterialDesignL.LINK_VARIANT, "Блокирует задачи", detailDependents),
            createDetailRow(MaterialDesignP.PLAY_CIRCLE_OUTLINE, "Старт", detailStartDate),
            createDetailRowNode(MaterialDesignN.NOTE_TEXT_OUTLINE, "Связанные заметки", detailLinkedNotes)
        );

        detailSecondaryFieldsPane = new TitledPane("Дополнительные поля", secondaryPropertiesContent);
        detailSecondaryFieldsPane.getStyleClass().add("right-panel-secondary-fields-pane");
        detailSecondaryFieldsPane.setExpanded(false);
        detailSecondaryFieldsPane.setCollapsible(true);
        detailSecondaryFieldsPane.setAnimated(false);
        detailSecondaryFieldsPane.setTooltip(new Tooltip("Второстепенные поля вынесены в collapsible-блок для низких разрешений"));

        detailsSection.getChildren().addAll(
            detailsHeader,
            detailTitle,
            summaryBlock,
            primaryPropertiesContent,
            detailSecondaryFieldsPane
        );
        return detailsSection;
    }

    private Node createRightPanelDescriptionSection() {
        VBox descriptionSection = new VBox(10);
        descriptionSection.getStyleClass().add("description-section");
        descriptionSection.setPadding(new Insets(10));
        descriptionSection.setFillWidth(true);

        HBox descHeader = new HBox(8);
        descHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon descIcon = FontIcon.of(MaterialDesignT.TEXT_SUBJECT, 16);
        descIcon.getStyleClass().add("section-icon");
        Label descTitle = new Label("Описание");
        descTitle.getStyleClass().add("section-title");
        descHeader.getChildren().addAll(descIcon, descTitle);

        descriptionWebView = new WebView();
        descriptionWebView.getStyleClass().add("description-webview");
        descriptionWebView.setMaxWidth(Double.MAX_VALUE);
        descriptionWebView.setPrefHeight(220);
        VBox.setVgrow(descriptionWebView, Priority.NEVER);
        setDescriptionContent("Нет описания");
        descriptionWebView.getEngine().locationProperty().addListener((obs, old, loc) -> {
            if (loc == null || loc.isBlank()) {
                return;
            }
            if (loc.startsWith("nfp-note:")) {
                String target = decodeLinkTarget(loc.substring("nfp-note:".length()));
                openNoteInSmartNotes(target);
            } else if (loc.startsWith("nfp-task:")) {
                String target = decodeLinkTarget(loc.substring("nfp-task:".length()));
                openTaskFromNoteLink(target);
            }
        });

        descriptionSummaryLabel.getStyleClass().add("description-summary-label");
        descriptionSummaryLabel.setWrapText(true);
        descriptionSummaryLabel.setTooltip(createTooltip(descriptionSummaryLabel.getText()));

        descriptionCompactExpandBtn = new Button("Полное описание");
        descriptionCompactExpandBtn.getStyleClass().add("description-compact-expand-btn");
        descriptionCompactExpandBtn.setOnAction(e -> {
            descriptionCompactExpanded = !descriptionCompactExpanded;
            applyAdaptiveLayoutStateToShell(false);
        });

        descriptionSummaryBox = new VBox(6, descriptionSummaryLabel, descriptionCompactExpandBtn);
        descriptionSummaryBox.getStyleClass().add("description-summary-box");

        descriptionFullContentBox = new VBox(descriptionWebView);
        descriptionFullContentBox.getStyleClass().add("description-full-content");
        VBox.setVgrow(descriptionFullContentBox, Priority.ALWAYS);
        descriptionCompactExpanded = false;

        descriptionSection.getChildren().addAll(descHeader, descriptionSummaryBox, descriptionFullContentBox);
        return descriptionSection;
    }

    private Node createRightPanelInsightSection() {
        VBox insightCard = new VBox(10);
        insightCard.getStyleClass().add("insight-card");
        insightCard.setPadding(new Insets(10));
        insightCard.setFillWidth(true);

        HBox insightHeader = new HBox(8);
        insightHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon aiIcon = FontIcon.of(MaterialDesignB.BRAIN, 18);
        aiIcon.getStyleClass().add("insight-icon");
        Label insightTitle = new Label("ИИ-Анализ");
        insightTitle.getStyleClass().add("insight-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button analyzeBtn = new Button();
        analyzeBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
        analyzeBtn.getStyleClass().add("insight-action-btn");
        analyzeBtn.setTooltip(new Tooltip("Запустить ИИ-анализ"));
        analyzeBtn.setOnAction(e -> runAIAnalysisForSelected(analyzeBtn));

        Button copyBtn = new Button();
        copyBtn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_COPY, 14));
        copyBtn.getStyleClass().add("insight-action-btn");
        copyBtn.setTooltip(new Tooltip("Копировать"));
        copyBtn.setOnAction(e -> copyInsightToClipboard(copyBtn));

        Button exportBtn = new Button();
        exportBtn.setGraphic(FontIcon.of(MaterialDesignE.EXPORT_VARIANT, 14));
        exportBtn.getStyleClass().add("insight-action-btn");
        exportBtn.setTooltip(new Tooltip("Экспорт"));
        exportBtn.setOnAction(e -> exportInsight());

        aiInsightCompactExpandBtn = new Button("Полный текст");
        aiInsightCompactExpandBtn.getStyleClass().add("ai-insight-compact-expand-btn");
        aiInsightCompactExpandBtn.setOnAction(e -> {
            aiInsightCompactExpanded = !aiInsightCompactExpanded;
            if (aiInsightCompactExpanded) {
                criticalPathCompactDetailsExpanded = false;
            }
            applyAdaptiveLayoutStateToShell(false);
        });

        insightHeader.getChildren().addAll(aiIcon, insightTitle, spacer, analyzeBtn, copyBtn, exportBtn);

        aiInsightSummaryLabel.getStyleClass().add("ai-insight-summary-label");
        aiInsightSummaryLabel.setWrapText(true);
        aiInsightSummaryLabel.setTooltip(createTooltip(aiInsightSummaryLabel.getText()));
        aiInsightSummaryBox = new VBox(6, aiInsightSummaryLabel, aiInsightCompactExpandBtn);
        aiInsightSummaryBox.getStyleClass().add("ai-insight-summary-box");

        aiInsightWebView = new WebView();
        aiInsightWebView.getStyleClass().add("insight-webview");
        aiInsightWebView.setMaxWidth(Double.MAX_VALUE);
        aiInsightWebView.setPrefHeight(240);
        VBox.setVgrow(aiInsightWebView, Priority.ALWAYS);
        setInsightContent("Выберите задачу для получения рекомендаций...");
        aiInsightWebView.getEngine().locationProperty().addListener((obs, old, loc) -> {
            if (loc == null || loc.isBlank()) {
                return;
            }
            if (loc.startsWith("nfp-note:")) {
                String target = decodeLinkTarget(loc.substring("nfp-note:".length()));
                openNoteInSmartNotes(target);
            } else if (loc.startsWith("nfp-task:")) {
                String target = decodeLinkTarget(loc.substring("nfp-task:".length()));
                openTaskFromNoteLink(target);
            }
        });

        aiInsightFullContentBox = new VBox(aiInsightWebView);
        aiInsightFullContentBox.getStyleClass().add("ai-insight-full-content");
        VBox.setVgrow(aiInsightFullContentBox, Priority.ALWAYS);
        aiInsightCompactExpanded = false;

        insightCard.getChildren().addAll(insightHeader, aiInsightSummaryBox, aiInsightFullContentBox);
        return insightCard;
    }

    private HBox createDetailRow(org.kordamp.ikonli.Ikon iconCode, String labelText, Label valueLabel) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("detail-row");

        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("detail-icon");

        VBox text = new VBox(2);
        Label label = new Label(labelText);
        label.getStyleClass().add("detail-label-small");
        
        valueLabel.getStyleClass().add("detail-value-text");
        valueLabel.setWrapText(true);
        
        text.getChildren().addAll(label, valueLabel);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(icon, text);
        return row;
    }

    private HBox createDetailRowNode(org.kordamp.ikonli.Ikon iconCode, String labelText, Node valueNode) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("detail-row");

        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("detail-icon");

        VBox text = new VBox(2);
        Label label = new Label(labelText);
        label.getStyleClass().add("detail-label-small");
        valueNode.getStyleClass().add("detail-value-node");
        text.getChildren().addAll(label, valueNode);
        HBox.setHgrow(text, Priority.ALWAYS);

        row.getChildren().addAll(icon, text);
        return row;
    }

    private VBox createCriticalPathPanel() {
        VBox panel = new VBox(8);
        panel.getStyleClass().add("critical-path-panel");
        panel.setPadding(new Insets(10, 10, 10, 10));
        panel.setFillWidth(true);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of(MaterialDesignC.CHART_LINE, 16);
        icon.getStyleClass().add("critical-path-icon");
        Label title = new Label("Критический путь");
        title.getStyleClass().add("critical-path-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button();
        refreshBtn.setGraphic(FontIcon.of(MaterialDesignC.CALENDAR_SYNC, 14));
        refreshBtn.getStyleClass().add("critical-path-refresh-btn");
        refreshBtn.setTooltip(new Tooltip("Пересчитать критический путь"));
        refreshBtn.setOnAction(e -> {
            refreshDependencyAndCriticalPathData();
            TreeItem<Task> selectedItem = taskTable.getSelectionModel().getSelectedItem();
            updateCriticalPathPanel(selectedItem == null ? null : selectedItem.getValue());
        });

        criticalPathDetailsToggleBtn = new Button("Подробнее");
        criticalPathDetailsToggleBtn.getStyleClass().add("critical-path-details-toggle-btn");
        criticalPathDetailsToggleBtn.setTooltip(new Tooltip("Показать расширенные метрики критического пути"));
        criticalPathDetailsToggleBtn.setOnAction(e -> {
            criticalPathCompactDetailsExpanded = !criticalPathCompactDetailsExpanded;
            if (criticalPathCompactDetailsExpanded) {
                aiInsightCompactExpanded = false;
            }
            if (criticalPathCompactDetailsExpanded && criticalPathExtendedMetricsDirty) {
                renderCriticalChain();
                criticalPathExtendedMetricsDirty = false;
            }
            applyAdaptiveLayoutStateToShell(false);
        });

        header.getChildren().addAll(icon, title, spacer, criticalPathDetailsToggleBtn, refreshBtn);

        criticalPathScopeLabel.getStyleClass().add("critical-path-scope");
        criticalPathSummaryLabel.getStyleClass().add("critical-path-summary");
        criticalPathCompactSummaryLabel.getStyleClass().add("critical-path-compact-summary");
        criticalPathSelectedTaskLabel.getStyleClass().add("critical-path-selected-task");
        criticalPathScopeLabel.setWrapText(true);
        criticalPathScopeLabel.setTextOverrun(OverrunStyle.CLIP);
        criticalPathScopeLabel.setMaxWidth(Double.MAX_VALUE);
        criticalPathScopeLabel.setMinWidth(0);
        criticalPathSummaryLabel.setWrapText(true);
        criticalPathSummaryLabel.setTextOverrun(OverrunStyle.CLIP);
        criticalPathSummaryLabel.setMaxWidth(Double.MAX_VALUE);
        criticalPathSummaryLabel.setMinWidth(0);
        criticalPathCompactSummaryLabel.setWrapText(true);
        criticalPathCompactSummaryLabel.setTextOverrun(OverrunStyle.CLIP);
        criticalPathCompactSummaryLabel.setMaxWidth(Double.MAX_VALUE);
        criticalPathCompactSummaryLabel.setMinWidth(0);
        criticalPathSelectedTaskLabel.setWrapText(true);
        criticalPathSelectedTaskLabel.setTextOverrun(OverrunStyle.CLIP);
        criticalPathSelectedTaskLabel.setMaxWidth(Double.MAX_VALUE);
        criticalPathSelectedTaskLabel.setMinWidth(0);

        criticalPathChainPane.getStyleClass().add("critical-path-chain");
        criticalPathChainPane.setHgap(6);
        criticalPathChainPane.setVgap(6);

        criticalPathExtendedMetricsBox = new VBox(6, criticalPathSelectedTaskLabel, criticalPathChainPane);
        criticalPathExtendedMetricsBox.getStyleClass().add("critical-path-extended-metrics");

        criticalPathPanelBody = new VBox(6);
        criticalPathPanelBody.getStyleClass().add("critical-path-panel-body");
        criticalPathPanelBody.setFillWidth(true);
        criticalPathPanelBody.getChildren().addAll(
            criticalPathCompactSummaryLabel,
            criticalPathScopeLabel,
            criticalPathSummaryLabel,
            criticalPathExtendedMetricsBox
        );
        VBox.setVgrow(criticalPathPanelBody, Priority.NEVER);

        panel.getChildren().addAll(header, criticalPathPanelBody);
        criticalPathCompactDetailsExpanded = false;
        return panel;
    }

    private VBox createCollapsibleSection(String titleText, org.kordamp.ikonli.Ikon iconCode, Node content, boolean expandedByDefault) {
        VBox section = new VBox(0);
        section.getStyleClass().add("collapsible-section");
        
        // Header (clickable)
        HBox headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("collapsible-header");
        headerBox.setPadding(new Insets(12, 15, 12, 15));
        headerBox.setCursor(javafx.scene.Cursor.HAND);
        
        FontIcon chevron = FontIcon.of(expandedByDefault ? MaterialDesignC.CHEVRON_DOWN : MaterialDesignC.CHEVRON_RIGHT, 16);
        chevron.getStyleClass().add("collapsible-chevron");
        
        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("collapsible-icon");
        
        Label titleLabel = new Label(titleText);
        titleLabel.getStyleClass().add("collapsible-title");
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        headerBox.getChildren().addAll(chevron, icon, titleLabel, spacer);
        
        // Content wrapper
        VBox contentWrapper = new VBox(content);
        contentWrapper.getStyleClass().add("collapsible-content");
        contentWrapper.setPadding(new Insets(0, 15, 15, 15));
        contentWrapper.setVisible(expandedByDefault);
        contentWrapper.setManaged(expandedByDefault);
        
        // Toggle on click
        headerBox.setOnMouseClicked(e -> {
            boolean isExpanded = contentWrapper.isVisible();
            contentWrapper.setVisible(!isExpanded);
            contentWrapper.setManaged(!isExpanded);
            chevron.setIconCode(isExpanded ? MaterialDesignC.CHEVRON_RIGHT : MaterialDesignC.CHEVRON_DOWN);
        });
        
        section.getChildren().addAll(headerBox, contentWrapper);
        return section;
    }

    private void updateDetailPanel(Task task) {
        String titleSuffix = task.isSubtask() ? " (подзадача)" : "";
        if (task.isCompleted()) {
            titleSuffix += " ✓";
        }
        detailTitle.setText(task.getTitle() + titleSuffix);
        String desc = task.getDescription();
        setDescriptionContent(desc != null && !desc.isEmpty() ? desc : "Нет описания");
        
        // Дедлайн с информацией о выполнении
        if (task.isCompleted() && task.getCompletedDate() != null) {
            long daysBeforeDeadline = java.time.temporal.ChronoUnit.DAYS.between(task.getCompletedDate(), task.getDeadline());
            String completionInfo = daysBeforeDeadline >= 0 
                ? " (выполнено за " + daysBeforeDeadline + " дн. до срока)"
                : " (просрочено на " + Math.abs(daysBeforeDeadline) + " дн.)";
            detailDeadline.setText(TaskScheduleFormatter.formatDeadline(task) + completionInfo);
        } else {
            detailDeadline.setText(TaskScheduleFormatter.formatDeadline(task));
        }
        
        detailComplexity.setText(task.getComplexity() + "/10");
        detailPriority.setText(String.format("%.1f", task.getSmartPriority()));
        detailTags.setText(task.getTags().isEmpty() ? "-" : task.getTags());
        detailRecurrence.setText(formatRecurrenceLabel(task.getRecurrence()));
        String taskId = normalizeTaskId(task.getId());
        List<String> blockers = taskId == null ? List.of() : blockersByTaskId.getOrDefault(taskId, List.of());
        List<String> dependents = taskId == null ? List.of() : dependentsByTaskId.getOrDefault(taskId, List.of());
        detailDependsOn.setText(formatTaskNames(blockers));
        detailDependents.setText(formatTaskNames(dependents));
        detailStartDate.setText(task.hasStartDate() ?
            (task.isStarted()
                ? TaskScheduleFormatter.formatStart(task) + " ✓"
                : TaskScheduleFormatter.formatStart(task) + " (ожидает)")
            : "Сразу");
        detailQuickFactsLabel.setText(String.format(
            "Дедлайн: %s  |  P: %.1f  |  C: %d/10",
            TaskScheduleFormatter.formatDeadline(task),
            task.getSmartPriority(),
            task.getComplexity()
        ));
        detailStatusSummaryLabel.setText(buildDetailStatusSummary(task, blockers, dependents));
        setInsightContent(task.getAiInsight() != null ? task.getAiInsight() : "Нажмите 'ИИ-Анализ'");
        updateCriticalPathPanel(task);
        refreshLinkedNotes(task);
        refreshRightPanelDetailTooltips();
    }

    private String buildDetailStatusSummary(Task task, List<String> blockers, List<String> dependents) {
        if (task == null) {
            return "Выберите задачу";
        }
        StringBuilder out = new StringBuilder();
        out.append(task.isCompleted() ? "Статус: выполнена" : "Статус: в работе");
        out.append(" • ");
        out.append(task.hasStartDate() ? "Старт: " + detailStartDate.getText() : "Старт: сразу");
        out.append(" • ");
        out.append("Связи: ").append(blockers == null ? 0 : blockers.size()).append("← / ")
            .append(dependents == null ? 0 : dependents.size()).append("→");
        return out.toString();
    }

    private void refreshRightPanelDetailTooltips() {
        setTooltipIfLong(detailTitle, 48);
        setTooltipIfLong(detailQuickFactsLabel, 64);
        setTooltipIfLong(detailStatusSummaryLabel, 64);
        setTooltipIfLong(detailDeadline, 42);
        setTooltipIfLong(detailTags, 42);
        setTooltipIfLong(detailDependsOn, 42);
        setTooltipIfLong(detailDependents, 42);
        setTooltipIfLong(detailStartDate, 36);
    }

    private void setTooltipIfLong(Labeled labeled, int threshold) {
        if (labeled == null) {
            return;
        }
        String text = labeled.getText();
        if (text == null || text.isBlank() || text.length() <= Math.max(8, threshold)) {
            labeled.setTooltip(null);
            return;
        }
        labeled.setTooltip(createTooltip(text));
    }

    private void refreshLinkedNotes(Task task) {
        detailLinkedNotes.getChildren().clear();
        if (task == null) {
            addEmptyLinkedNotes();
            return;
        }
        List<String> linkedNotes = findNotesLinkingToTask(task);
        if (linkedNotes.isEmpty()) {
            addEmptyLinkedNotes();
            return;
        }
        for (String title : linkedNotes) {
            Button link = new Button(title);
            link.getStyleClass().add("backlink-chip");
            link.setOnAction(e -> openNoteInSmartNotes(title));
            detailLinkedNotes.getChildren().add(link);
        }
    }

    private void addEmptyLinkedNotes() {
        Label empty = new Label("—");
        empty.getStyleClass().add("backlinks-empty");
        detailLinkedNotes.getChildren().add(empty);
    }

    private List<String> findNotesLinkingToTask(Task task) {
        List<String> result = new ArrayList<>();
        if (task == null) {
            return result;
        }
        for (String title : notesService.getAllNoteTitles()) {
            String content = notesService.loadNoteContent(title);
            if (containsTaskLink(content, task)) {
                result.add(title);
            }
        }
        return result;
    }

    private boolean containsTaskLink(String content, Task task) {
        for (LinkParser.LinkTarget link : LinkParser.extractLinks(content)) {
            if (link.getType() == LinkParser.LinkType.TASK && LinkParser.matchesTaskTarget(link.getTarget(), task)) {
                return true;
            }
        }
        return false;
    }

    private SmartNotesDialog buildSmartNotesDialog() {
        SmartNotesDialog view = (SmartNotesDialog) SmartNotesDialog.inline();
        view.setTaskProvider(this::getAllTasksFlat);
        view.setTaskResolver(this::resolveTaskByLink);
        view.setTaskNavigator(this::openTaskFromNoteLink);
        return view;
    }

    private void openSmartNotesPanel() {
        SmartNotesDialog view = buildSmartNotesDialog();
        openInlineView(InlineTabMetadata.global("main.tools.notes.open"), view);
    }

    private void openExportPanel() {
        openInlineView("main.system.export", () -> ExportDialog.inline(tasks));
    }

    private void openNoteInSmartNotes(String title) {
        SmartNotesDialog view = buildSmartNotesDialog();
        openInlineView(InlineTabMetadata.global("main.tools.notes.open"), view);
        view.openNoteByTitle(title);
    }

    private void openTaskFromNoteLink(String token) {
        if (!openTaskById(token)) {
            showAlert("Задача не найдена: " + token);
        }
    }

    public boolean openTaskById(String taskId) {
        Task task = resolveTaskByLink(taskId);
        if (task == null) {
            return false;
        }
        showTaskPanelWithoutClosingInlineTabs();
        selectTaskInTable(task);
        showInInspectorTab(RightPanelInspectorTab.PROPERTIES, true);
        return true;
    }

    private void showTaskPanelWithoutClosingInlineTabs() {
        hideInlineOverlayWithoutClosingTabs();
        if (taskTable != null) {
            Platform.runLater(taskTable::requestFocus);
        }
    }

    public boolean openNoteByTitle(String title) {
        String noteTitle = title == null ? "" : title.trim();
        if (noteTitle.isBlank()) {
            return false;
        }
        openNoteInSmartNotes(noteTitle);
        return true;
    }

    private Task resolveTaskByLink(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String normalized = LinkParser.normalize(token);
        for (Task task : getAllTasksFlat()) {
            if (LinkParser.normalize(task.getId()).equals(normalized) || LinkParser.normalize(task.getTitle()).equals(normalized)) {
                return task;
            }
        }
        return null;
    }

    private List<Task> getAllTasksFlat() {
        List<Task> all = new ArrayList<>();
        for (Task task : tasks) {
            collectTasks(task, all);
        }
        return all;
    }

    private void collectTasks(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task sub : task.getSubtasks()) {
            collectTasks(sub, sink);
        }
    }

    private void selectTaskInTable(Task task) {
        TreeItem<Task> item = findTreeItemById(rootItem, task.getId());
        if (item == null) {
            return;
        }
        taskTable.getSelectionModel().select(item);
        int row = taskTable.getRow(item);
        if (row >= 0) {
            taskTable.scrollTo(row);
        }
    }

    private TreeItem<Task> findTreeItemById(TreeItem<Task> root, String id) {
        if (root == null || id == null) {
            return null;
        }
        Task value = root.getValue();
        if (value != null && id.equals(value.getId())) {
            return root;
        }
        for (TreeItem<Task> child : root.getChildren()) {
            TreeItem<Task> found = findTreeItemById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void refreshDependencyAndCriticalPathData() {
        rebuildTaskIndex();
        if (presenter == null) {
            blockersByTaskId.clear();
            dependentsByTaskId.clear();
            applyCriticalPathResult(CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null));
            updateCriticalPathPanel(null);
            return;
        }
        reloadDependencyGraph();
        syncDependencyMirror();
        reloadCriticalPath();
        TreeItem<Task> selectedItem = taskTable.getSelectionModel().getSelectedItem();
        updateCriticalPathPanel(selectedItem == null ? null : selectedItem.getValue());
    }

    private void rebuildTaskIndex() {
        taskIndexById.clear();
        for (Task task : getAllTasksFlat()) {
            String taskId = normalizeTaskId(task.getId());
            if (taskId != null) {
                taskIndexById.put(taskId, task);
            }
        }
    }

    private void reloadDependencyGraph() {
        blockersByTaskId.clear();
        dependentsByTaskId.clear();
        Map<String, LinkedHashSet<String>> blockers = new HashMap<>();
        Map<String, LinkedHashSet<String>> dependents = new HashMap<>();
        for (TaskDependencyEdge edge : presenter.loadAllDependencyEdges()) {
            if (edge == null) {
                continue;
            }
            String dependentId = normalizeTaskId(edge.dependentTaskId());
            String blockerId = normalizeTaskId(edge.blockerTaskId());
            if (dependentId == null || blockerId == null) {
                continue;
            }
            blockers.computeIfAbsent(dependentId, key -> new LinkedHashSet<>()).add(blockerId);
            dependents.computeIfAbsent(blockerId, key -> new LinkedHashSet<>()).add(dependentId);
        }

        for (Map.Entry<String, LinkedHashSet<String>> entry : blockers.entrySet()) {
            blockersByTaskId.put(entry.getKey(), orderTaskIds(entry.getValue()));
        }
        for (Map.Entry<String, LinkedHashSet<String>> entry : dependents.entrySet()) {
            dependentsByTaskId.put(entry.getKey(), orderTaskIds(entry.getValue()));
        }
    }

    private void syncDependencyMirror() {
        for (Task task : getAllTasksFlat()) {
            String taskId = normalizeTaskId(task.getId());
            if (taskId == null) {
                continue;
            }
            task.setDependsOn(String.join(",", blockersByTaskId.getOrDefault(taskId, List.of())));
        }
    }

    private void reloadCriticalPath() {
        try {
            applyCriticalPathResult(presenter.computeCriticalPathFullGraph());
        } catch (RuntimeException ex) {
            applyCriticalPathResult(CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null));
        }
    }

    private void applyCriticalPathResult(CriticalPathResult result) {
        criticalPathResult = result == null
            ? CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null)
            : result;

        criticalMetricsByTaskId.clear();
        criticalTaskIds.clear();
        criticalChainTaskIds.clear();

        for (CriticalPathTaskMetrics metrics : criticalPathResult.taskMetrics()) {
            if (metrics == null) {
                continue;
            }
            String taskId = normalizeTaskId(metrics.taskId());
            if (taskId == null) {
                continue;
            }
            criticalMetricsByTaskId.put(taskId, metrics);
            if (metrics.critical()) {
                criticalTaskIds.add(taskId);
            }
        }
        for (String taskId : criticalPathResult.criticalChainTaskIds()) {
            String normalized = normalizeTaskId(taskId);
            if (normalized != null) {
                criticalChainTaskIds.add(normalized);
            }
        }
    }

    private void updateCriticalPathPanel(Task selectedTask) {
        String scopeText = criticalPathResult.scopeMode() == CriticalPathScopeMode.ROOT_TASK
            ? "Scope: root " + resolveTaskName(criticalPathResult.scopeRootTaskId())
            : "Scope: full graph";
        criticalPathScopeLabel.setText(scopeText);
        criticalPathSummaryLabel.setText(String.format(
            "Длина: %d, критических задач: %d/%d, критических ребер: %d/%d",
            criticalPathResult.projectDuration(),
            criticalPathResult.criticalTaskCount(),
            criticalPathResult.taskCount(),
            criticalPathResult.criticalEdgeCount(),
            criticalPathResult.edgeCount()
        ));
        criticalPathCompactSummaryLabel.setText(buildCriticalPathCompactSummaryText(selectedTask));

        criticalPathSelectedTaskLabel.setText(resolveSelectedTaskCriticalText(selectedTask));
        criticalPathExtendedMetricsDirty = true;
        if (shouldRenderCriticalPathExtendedMetricsNow()) {
            renderCriticalChain();
            criticalPathExtendedMetricsDirty = false;
        }
        if (rightPanelInspectorDisplayPolicy != null) {
            applyCriticalPathContentCompaction(rightPanelInspectorDisplayPolicy);
        }
    }

    private void renderCriticalChain() {
        if (!shouldRenderCriticalPathExtendedMetricsNow()) {
            criticalPathExtendedMetricsDirty = true;
            return;
        }
        criticalPathChainPane.getChildren().clear();
        if (criticalPathResult.criticalChainTaskIds().isEmpty()) {
            Label empty = new Label("Цепочка не определена");
            empty.getStyleClass().add("critical-path-empty");
            criticalPathChainPane.getChildren().add(empty);
            return;
        }
        for (String taskId : criticalPathResult.criticalChainTaskIds()) {
            String normalized = normalizeTaskId(taskId);
            if (normalized == null) {
                continue;
            }
            Label chip = new Label(resolveTaskName(normalized));
            chip.getStyleClass().add("critical-path-chip");
            if (criticalChainTaskIds.contains(normalized)) {
                chip.getStyleClass().add("critical-path-chip-critical");
            }
            CriticalPathTaskMetrics metrics = criticalMetricsByTaskId.get(normalized);
            if (metrics != null) {
                chip.setTooltip(buildCriticalPathTooltip(taskIndexById.get(normalized), metrics));
            }
            criticalPathChainPane.getChildren().add(chip);
        }
    }

    private boolean shouldRenderCriticalPathExtendedMetricsNow() {
        if (rightPanelInspectorDisplayPolicy == null) {
            return true;
        }
        RightPanelTabContentPolicy analyticsPolicy =
            rightPanelInspectorDisplayPolicy.contentPolicyFor(RightPanelInspectorTab.ANALYTICS);
        if (analyticsPolicy == null || !hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_PATH)) {
            return true;
        }
        boolean aiTabPresent = hasInspectorSection(analyticsPolicy, RightPanelLayoutService.SECTION_AI);
        boolean summaryFirstMode = analyticsPolicy.summaryFirst() || aiTabPresent;
        return (!summaryFirstMode || criticalPathCompactDetailsExpanded) && !aiInsightCompactExpanded;
    }

    private String buildCriticalPathCompactSummaryText(Task selectedTask) {
        String selectedName = selectedTask == null ? "нет выбранной задачи" : selectedTask.getTitle();
        return String.format(
            "Длительность %d • Критич. задач %d/%d • Выбор: %s",
            criticalPathResult.projectDuration(),
            criticalPathResult.criticalTaskCount(),
            criticalPathResult.taskCount(),
            selectedName
        );
    }

    private String resolveSelectedTaskCriticalText(Task selectedTask) {
        if (selectedTask == null) {
            return "Выберите задачу для просмотра slack/веса";
        }
        CriticalPathTaskMetrics metrics = criticalMetricsByTaskId.get(normalizeTaskId(selectedTask.getId()));
        if (metrics == null) {
            return "Выбранная задача вне текущего критического графа";
        }
        String status = metrics.critical() ? "критическая" : "некритическая";
        return String.format(
            "%s: %s, вес=%d, slack=%d, ES/EF=%d/%d",
            selectedTask.getTitle(),
            status,
            metrics.duration(),
            metrics.totalSlack(),
            metrics.earliestStart(),
            metrics.earliestFinish()
        );
    }

    private Tooltip buildTaskRowTooltip(Task task, CriticalPathTaskMetrics metrics) {
        String collapsedColumnsText = buildCollapsedColumnsTooltip(task);
        if (metrics == null) {
            return collapsedColumnsText.isBlank() ? null : createTooltip(collapsedColumnsText);
        }
        Tooltip tooltip = buildCriticalPathTooltip(task, metrics);
        if (!collapsedColumnsText.isBlank()) {
            tooltip.setText(tooltip.getText() + "\n\n" + collapsedColumnsText);
        }
        return tooltip;
    }

    private String buildCollapsedColumnsTooltip(Task task) {
        if (!taskTableSecondaryColumnsCollapsed || task == null) {
            return "";
        }
        String tags = task.getTags() == null || task.getTags().isBlank() ? "—" : task.getTags();
        return String.format(
            "Скрытые поля таблицы%nТеги: %s%nСложность: %d/10%nИИ-приоритет: %.1f%nПовтор: %s",
            tags,
            task.getComplexity(),
            task.getSmartPriority(),
            formatRecurrenceLabel(task.getRecurrence())
        );
    }

    private String formatRecurrenceLabel(String recurrence) {
        if (recurrence == null || recurrence.isBlank()) {
            return "-";
        }
        return switch (recurrence.trim().toLowerCase(Locale.ROOT)) {
            case "daily" -> "Ежедневно";
            case "weekly" -> "Еженедельно";
            case "monthly" -> "Ежемесячно";
            case "yearly" -> "Ежегодно";
            default -> recurrence.trim();
        };
    }

    private Tooltip createTooltip(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(javafx.util.Duration.millis(120));
        return tooltip;
    }

    private Tooltip buildCriticalPathTooltip(Task task, CriticalPathTaskMetrics metrics) {
        String title = task == null ? resolveTaskName(metrics.taskId()) : task.getTitle();
        String text = String.format(
            "%s%nВес: %d%nSlack: %d%nES/EF: %d/%d%nLS/LF: %d/%d%nКритическая: %s",
            title,
            metrics.duration(),
            metrics.totalSlack(),
            metrics.earliestStart(),
            metrics.earliestFinish(),
            metrics.latestStart(),
            metrics.latestFinish(),
            metrics.critical() ? "да" : "нет"
        );
        return createTooltip(text);
    }

    private List<String> orderTaskIds(Set<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>(taskIds);
        ordered.sort(Comparator.comparing(this::resolveTaskName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(ordered);
    }

    private String formatTaskNames(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return "-";
        }
        List<String> names = new ArrayList<>(taskIds.size());
        for (String taskId : taskIds) {
            names.add(resolveTaskName(taskId));
        }
        return names.isEmpty() ? "-" : String.join(", ", names);
    }

    private String resolveTaskName(String taskId) {
        String normalized = normalizeTaskId(taskId);
        if (normalized == null) {
            return "—";
        }
        Task task = taskIndexById.get(normalized);
        if (task != null && task.getTitle() != null && !task.getTitle().isBlank()) {
            return task.getTitle();
        }
        return normalized;
    }

    private String normalizeTaskId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void handleDuplicateTask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для дублирования");
            return;
        }
        Task original = selected.getValue();
        Task copy = new Task(
            java.util.UUID.randomUUID().toString(),
            original.getTitle() + " (копия)",
            original.getDescription(),
            original.getDeadline(),
            original.getComplexity(),
            original.getParentId(),
            original.getTags(),
            original.getRecurrence()
        );
        copy.setStartDate(original.getStartDate());
        copy.setStartTime(original.getStartTime());
        copy.setDeadlineTime(original.getDeadlineTime());
        presenter.calculatePriority(copy);
        UndoRedoManager.CommandResult result = presenter.addTaskUndoable(copy);
        if (!result.successful()) {
            showAlert("Не удалось дублировать задачу: " + result.message());
            return;
        }
        showAlert("Задача дублирована: " + copy.getTitle());
    }

    private void handleAddTask(String parentId) {
        String actionId = parentId == null ? "main.inbox.addTask" : "main.task.addSubtask";
        openInlineViewContext(actionId, "parentTask", parentId, () -> createAddTaskInlineView(parentId));
    }

    private void handleAddSubtask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите родительскую задачу");
            return;
        }
        Task parent = selected.getValue();
        if (parent.isSubtask()) {
            showAlert("Нельзя создать подзадачу для подзадачи");
            return;
        }
        handleAddTask(parent.getId());
    }
    
    private void handleEditTask(Task task) {
        if (task == null) {
            return;
        }
        openInlineViewContext("main.task.edit", "task", task.getId(), () -> createEditTaskInlineView(task));
    }

    private InlineView createAddTaskInlineView(String parentId) {
        return AddTaskDialog.inline(parentId, task -> {
            presenter.calculatePriority(task);
            UndoRedoManager.CommandResult result = presenter.addTaskUndoable(task);
            if (!result.successful()) {
                showAlert("Не удалось добавить задачу: " + result.message());
            }
        }, null);
    }

    private InlineView createEditTaskInlineView(Task task) {
        return EditTaskDialog.inline(task, updatedTask -> {
            if (updatedTask.getSmartPriority() <= 0) {
                presenter.calculatePriority(updatedTask);
            }
            UndoRedoManager.CommandResult result = presenter.editTaskUndoable(updatedTask);
            if (!result.successful()) {
                showAlert("Не удалось сохранить изменения: " + result.message());
                return;
            }
            Task selectedTask = taskTable.getSelectionModel().getSelectedItem() == null
                ? null
                : taskTable.getSelectionModel().getSelectedItem().getValue();
            if (selectedTask != null) {
                updateDetailPanel(selectedTask);
            }
        }, null);
    }

    private void showAlert(String msg) {
        javafx.stage.Window owner = taskTable != null && taskTable.getScene() != null
            ? taskTable.getScene().getWindow()
            : (getScene() != null ? getScene().getWindow() : null);
        UiErrorNotifier.showInfo(owner, ConfigManager.isDarkTheme(), "Информация", msg);
    }

    private void styleAlert(Alert alert) {
        styleDialog(alert);
    }

    private void styleDialog(javafx.scene.control.Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (ConfigManager.isDarkTheme()) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        dialog.initOwner(taskTable.getScene().getWindow());
    }

    private void runAIAnalysisForSelected() {
        runAIAnalysisForSelected(null);
    }

    private void runAIAnalysisForSelected(Button triggerBtn) {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для анализа");
            return;
        }
        showInInspectorTab(RightPanelInspectorTab.ANALYTICS, true);

        Task task = selected.getValue();
        
        if (triggerBtn != null) {
            // Меняем состояние кнопки
            triggerBtn.setDisable(true);
            triggerBtn.getStyleClass().add("insight-action-btn-loading");
            FontIcon loadingIcon = FontIcon.of(MaterialDesignL.LOADING, 14);
            triggerBtn.setGraphic(loadingIcon);
        }
        
        setInsightContent("⏳ Анализирую задачу...");

        CompletableFuture<String> analysisFuture = presenter.analyzeTaskObserved(
            task,
            getScene() != null ? getScene().getWindow() : null,
            ConfigManager.isDarkTheme()
        );

        // Запускаем анализ
        analysisFuture.thenAccept(insight -> {
            Platform.runLater(() -> {
                setInsightContent(insight);
                if (triggerBtn != null) {
                    // Успех
                    triggerBtn.getStyleClass().remove("insight-action-btn-loading");
                    triggerBtn.getStyleClass().add("insight-action-btn-success");
                    triggerBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK, 14));
                    triggerBtn.setDisable(false);

                    // Через 1.5 сек возвращаем нормальное состояние
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        Platform.runLater(() -> {
                            triggerBtn.getStyleClass().remove("insight-action-btn-success");
                            triggerBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
                        });
                    }).start();
                }
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                setInsightContent("Ошибка анализа. Попробуйте позже.", true);
                if (triggerBtn != null) {
                    triggerBtn.getStyleClass().remove("insight-action-btn-loading");
                    triggerBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 14));
                    triggerBtn.setDisable(false);
                }
            });
            return null;
        });
    }

    /** Установить контент в WebView с рендерингом Markdown */
    private void setInsightContent(String markdown) {
        setInsightContent(markdown, false);
    }

    private void setInsightContent(String markdown, boolean errorState) {
        currentInsightText = markdown != null ? markdown : "";
        aiInsightErrorState = errorState;
        aiInsightCompactExpanded = false;
        String html = convertMarkdownToHtml(currentInsightText);
        String fullHtml = getHtmlTemplate(html);
        if (aiInsightWebView != null) {
            aiInsightWebView.getEngine().loadContent(fullHtml);
        }
        updateAiInsightSummaryPresentation();
        if (rightPanelInspectorDisplayPolicy != null) {
            applyAiInsightContentCompaction(rightPanelInspectorDisplayPolicy);
            updateRightPanelInspectorFooter(rightPanelInspectorDisplayPolicy);
        }
    }
    
    /** Установить описание задачи в WebView с рендерингом Markdown */
    private void setDescriptionContent(String markdown) {
        currentDescriptionText = markdown != null ? markdown : "";
        descriptionCompactExpanded = false;
        String html = convertMarkdownToHtml(currentDescriptionText);
        String fullHtml = getDescriptionHtmlTemplate(html);
        if (descriptionWebView != null) {
            descriptionWebView.getEngine().loadContent(fullHtml);
        }
        updateDescriptionSummaryPresentation();
        if (rightPanelInspectorDisplayPolicy != null) {
            applyDescriptionContentCompaction(rightPanelInspectorDisplayPolicy);
            updateRightPanelInspectorFooter(rightPanelInspectorDisplayPolicy);
        }
    }

    private void updateAiInsightSummaryPresentation() {
        if (aiInsightSummaryLabel == null) {
            return;
        }
        String summary = buildAiInsightSummary(currentInsightText);
        aiInsightSummaryLabel.setText(summary);
        aiInsightSummaryLabel.setTooltip(createTooltip(summary));
    }

    private void updateDescriptionSummaryPresentation() {
        if (descriptionSummaryLabel == null) {
            return;
        }
        String summary = buildDescriptionSummary(currentDescriptionText);
        descriptionSummaryLabel.setText(summary);
        descriptionSummaryLabel.setTooltip(createTooltip(summary));
    }

    private String buildAiInsightSummary(String rawText) {
        return buildMarkdownSummary(
            rawText,
            "Нет AI-анализа. Выберите задачу и запустите анализ.",
            "AI-анализ готов. Откройте полный текст."
        );
    }

    private String buildDescriptionSummary(String rawText) {
        return buildMarkdownSummary(
            rawText,
            "Нет описания. Добавьте контекст задачи.",
            "Описание готово. Откройте полный текст."
        );
    }

    private String buildMarkdownSummary(String rawText, String emptyFallback, String noTextFallback) {
        String normalized = rawText == null ? "" : rawText.trim();
        if (normalized.isEmpty()) {
            return emptyFallback;
        }
        String plain = normalized
            .replaceAll("(?m)^#+\\s*", "")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("*", "")
            .replace("_", "")
            .replace("\r", " ");
        plain = plain.replaceAll("\\[(.+?)\\]\\((.+?)\\)", "$1");
        plain = plain.replaceAll("\\s+", " ").trim();
        if (plain.isBlank()) {
            return noTextFallback;
        }
        if (plain.length() > 220) {
            return plain.substring(0, 217).trim() + "...";
        }
        return plain;
    }
    
    /** HTML шаблон для описания (компактный) */
    private String getDescriptionHtmlTemplate(String content) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        String bgColor = isDark ? "#313244" : "#ccd0da";
        String textColor = isDark ? "#cdd6f4" : "#4c4f69";
        String headingColor = isDark ? "#89b4fa" : "#1e66f5";
        String codeColor = isDark ? "#a6e3a1" : "#40a02b";
        String codeBg = isDark ? "#1e1e2e" : "#e6e9ef";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    font-size: 12px;
                    line-height: 1.5;
                    color: %s;
                    background-color: %s;
                    padding: 8px 10px;
                    border-radius: 8px;
                }
                p { margin-bottom: 6px; }
                h1, h2, h3 { color: %s; margin: 8px 0 4px 0; }
                h1 { font-size: 14px; }
                h2 { font-size: 13px; }
                h3 { font-size: 12px; }
                strong { font-weight: 600; }
                code {
                    background: %s;
                    color: %s;
                    padding: 1px 4px;
                    border-radius: 3px;
                    font-family: 'JetBrains Mono', monospace;
                    font-size: 11px;
                }
                a.notes-wiki-link {
                    color: %s;
                    text-decoration: none;
                    font-weight: 600;
                }
                a.notes-wiki-link:hover {
                    text-decoration: underline;
                }
                ul, ol { margin: 4px 0 4px 16px; }
                li { margin: 2px 0; }
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(textColor, bgColor, headingColor, codeBg, codeColor, headingColor, content);
    }
    
    /** Конвертация Markdown в HTML */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        
        Map<String, String> wikiLinks = new HashMap<>();
        String html = replaceWikiLinksWithPlaceholders(markdown, wikiLinks);
        
        // Экранируем HTML
        html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        
        // Заголовки
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");
        
        // Жирный текст
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        
        // Курсив
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("_(.+?)_", "<em>$1</em>");
        
        // Код inline
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");
        
        // Списки
        html = html.replaceAll("(?m)^- (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\* (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");
        html = html.replaceAll("(<li>.*</li>\\n?)+", "<ul>$0</ul>");
        
        // Переносы строк
        html = html.replace("\n\n", "</p><p>");
        html = html.replace("\n", "<br>");
        html = "<p>" + html + "</p>";
        
        // Убираем пустые параграфы
        html = html.replace("<p></p>", "");
        html = html.replace("<p><br></p>", "");
        html = replacePlaceholders(html, wikiLinks);
        
        return html;
    }

    private String replaceWikiLinksWithPlaceholders(String markdown, Map<String, String> placeholders) {
        Matcher matcher = LinkParser.WIKI_LINK_PATTERN.matcher(markdown);
        StringBuffer buffer = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String raw = matcher.group(1);
            LinkParser.LinkTarget target = LinkParser.parse(raw);
            String placeholder = "NFPLINKTOKEN" + index++ + "X";
            placeholders.put(placeholder, buildWikiAnchor(target));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(placeholder));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String replacePlaceholders(String html, Map<String, String> placeholders) {
        String output = html;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String buildWikiAnchor(LinkParser.LinkTarget target) {
        if (target == null) {
            return "";
        }
        String label = escapeHtml(target.getRaw());
        String linkTarget = target.getTarget();
        String encoded = URLEncoder.encode(linkTarget == null ? "" : linkTarget, StandardCharsets.UTF_8);
        String href = target.getType() == LinkParser.LinkType.TASK ? "nfp-task:" + encoded : "nfp-note:" + encoded;
        return "<a href=\"" + href + "\" class=\"notes-wiki-link\">" + label + "</a>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String decodeLinkTarget(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
    
    /** HTML шаблон с CSS стилями */
    private String getHtmlTemplate(String content) {
        boolean isDark = ConfigManager.isDarkTheme();
        
        String bgColor = isDark ? "#1e1e2e" : "#eff1f5";
        String textColor = isDark ? "#cdd6f4" : "#4c4f69";
        String headingColor = isDark ? "#cba6f7" : "#8839ef";
        String codeColor = isDark ? "#a6e3a1" : "#40a02b";
        String codeBg = isDark ? "#313244" : "#e6e9ef";
        String linkColor = isDark ? "#89b4fa" : "#1e66f5";
        String listColor = isDark ? "#f9e2af" : "#df8e1d";
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: 'Segoe UI', system-ui, sans-serif;
                    font-size: 13px;
                    line-height: 1.6;
                    color: %s;
                    background-color: %s;
                    margin: 0;
                    padding: 12px;
                }
                h1, h2, h3 {
                    color: %s;
                    margin: 12px 0 8px 0;
                    font-weight: 600;
                }
                h1 { font-size: 18px; }
                h2 { font-size: 16px; }
                h3 { font-size: 14px; }
                p { margin: 8px 0; }
                strong { font-weight: 600; }
                em { font-style: italic; }
                code {
                    background-color: %s;
                    color: %s;
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: 'JetBrains Mono', 'Consolas', monospace;
                    font-size: 12px;
                }
                ul, ol {
                    margin: 8px 0;
                    padding-left: 20px;
                }
                li {
                    margin: 4px 0;
                }
                li::marker {
                    color: %s;
                }
                a {
                    color: %s;
                    text-decoration: none;
                }
                a:hover {
                    text-decoration: underline;
                }
            </style>
            </head>
            <body>%s</body>
            </html>
            """.formatted(textColor, bgColor, headingColor, codeBg, codeColor, listColor, linkColor, content);
    }

    private void copyInsightToClipboard(Button btn) {
        String content = currentInsightText;
        if (content == null || content.isEmpty()) {
            showAlert("Нет данных для копирования");
            return;
        }
        
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent clipboardContent = new javafx.scene.input.ClipboardContent();
        clipboardContent.putString(content);
        clipboard.setContent(clipboardContent);
        
        // Визуальная обратная связь
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CHECK, 14);
        btn.setGraphic(checkIcon);
        btn.getStyleClass().add("insight-action-btn-success");
        
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            javafx.application.Platform.runLater(() -> {
                btn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_COPY, 14));
                btn.getStyleClass().remove("insight-action-btn-success");
            });
        }).start();
    }

    private void exportInsight() {
        String content = currentInsightText;
        if (content == null || content.isEmpty()) {
            showAlert("Нет данных для экспорта");
            return;
        }
        
        FileChooser fc = new FileChooser();
        fc.setTitle("Экспорт ИИ-рекомендаций");
        fc.setInitialFileName("ai_insight_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")));
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PDF", "*.pdf"),
            new FileChooser.ExtensionFilter("Markdown", "*.md"),
            new FileChooser.ExtensionFilter("Word", "*.docx")
        );
        
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        FileChooser.ExtensionFilter selected = fc.getSelectedExtensionFilter();
        String ext = selected == null
            ? ".pdf"
            : selected.getExtensions().get(0).replace("*", "");
        ErrorCode exportCode = resolveExportCode(ext);
        
        try {
            String path = file.getAbsolutePath();
            
            if (!path.endsWith(ext)) {
                file = new File(path + ext);
            }
            
            presenter.exportInsight(file, ext, content);
            showAlert("Экспортировано: " + file.getName());
        } catch (Exception e) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка экспорта",
                e,
                exportCode,
                "Не удалось выполнить экспорт.",
                false,
                "operation", "exportInsight",
                "format", ext,
                "fileName", file.getName()
            );
        }
    }

    private ErrorCode resolveExportCode(String extension) {
        if (".pdf".equals(extension)) {
            return ErrorCode.EXPORT_PDF_FAILED;
        }
        if (".md".equals(extension)) {
            return ErrorCode.EXPORT_MARKDOWN_FAILED;
        }
        if (".docx".equals(extension)) {
            return ErrorCode.EXPORT_DOCX_FAILED;
        }
        return ErrorCode.UNEXPECTED_ERROR;
    }

    private void handleSmartSort() {
        tasks.sort(Comparator.comparingDouble(Task::getSmartPriority).reversed());
        refreshTree();
    }

    private void handleAnalyzeAll() {
        if (tasks.isEmpty()) return;
        
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        Task toAnalyze = (selected != null && selected.getValue() != null) ? selected.getValue() : tasks.get(0);
        
        setInsightContent("⏳ Анализирую задачи с помощью ИИ...");
        
        tasks.forEach(task -> {
            presenter.calculatePriority(task);
            presenter.saveTask(task);
            task.getSubtasks().forEach(sub -> {
                presenter.calculatePriority(sub);
                presenter.saveTask(sub);
            });
        });
        refreshTree();
        
        presenter.analyzeTask(toAnalyze).thenAccept(insight -> 
            javafx.application.Platform.runLater(() -> {
                toAnalyze.setAiInsight(insight);
                presenter.saveTask(toAnalyze);
                updateDetailPanel(toAnalyze);
            })
        );
    }

    private void handleAutoPrioritization() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для приоритизации");
            return;
        }
        if (autoPriorityInProgress) {
            return;
        }
        autoPriorityInProgress = true;
        setInsightContent("ИИ определяет приоритеты задач...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        TreeItem<Task> selectedItem = taskTable.getSelectionModel().getSelectedItem();
        final Task selectedTask = selectedItem != null ? selectedItem.getValue() : null;
        
        presenter.prioritizeWithAi(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> {
                autoPriorityInProgress = false;
                presenter.saveTasks(allTasks);
                refreshTree();
                if (selectedTask != null) {
                    updateDetailPanel(selectedTask);
                }
                setInsightContent(result);
            })
        );
    }

    private void handleAutoSchedule() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для планирования");
            return;
        }
        
        setInsightContent("📅 ИИ составляет оптимальное расписание...");
        // Выполняем пересчёт на FX-потоке, чтобы не трогать ObservableList из фонового потока
        javafx.application.Platform.runLater(() -> {
            String result = presenter.autoSchedule(tasks, 15); // 15 complexity points per day
            presenter.saveTasks(tasks);
            refreshTree();
            setInsightContent(result);
        });
    }

    private void handlePredictTime() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для оценки времени");
            return;
        }
        Task task = selected.getValue();
        setInsightContent("⏳ ИИ оценивает время выполнения...");
        
        presenter.predictTime(task).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void handleRecommendations() {
        if (tasks.isEmpty()) {
            showAlert("Нет задач для анализа");
            return;
        }
        setInsightContent("💡 ИИ анализирует задачи...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        
        presenter.recommendations(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void handleProductivityAnalysis() {
        if (tasks.isEmpty()) {
            showAlert("Нет данных для анализа");
            return;
        }
        setInsightContent("📈 ИИ анализирует паттерны работы...");
        
        List<Task> allTasks = new ArrayList<>(tasks);
        tasks.forEach(t -> allTasks.addAll(t.getSubtasks()));
        
        presenter.productivityAnalysis(allTasks).thenAccept(result ->
            javafx.application.Platform.runLater(() -> setInsightContent(result))
        );
    }

    private void filterUrgent() {
        showTasks(presenter.filterUrgent(tasks, 6), TaskListViewMode.URGENT, "");
    }

    private void filterByTag() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Фильтр по тегу");
        dialog.setHeaderText(null);
        dialog.setContentText("Введите тег:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(tag -> {
            String searchTag = tag.trim();
            if (searchTag.isEmpty()) {
                showAllTasks();
                return;
            }
            showTasks(presenter.filterByTag(tasks, searchTag), TaskListViewMode.TAG_FILTER, searchTag);
        });
    }

    private void handleToggleComplete(Task task) {
        if (task == null) return;
        boolean newState = !task.isCompleted();
        task.setCompleted(newState);
        if (newState) {
            task.setCompletedDate(LocalDate.now());
        } else {
            task.setCompletedDate(null);
        }
        presenter.saveTask(task);
        taskTable.refresh();
        updateDetailPanel(task);
    }

    private void handleArchiveTask() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для архивирования");
            return;
        }
        Task task = selected.getValue();
        try {
            UndoRedoManager.CommandResult result = presenter.archiveTasksUndoable(List.of(task.getId()), true);
            if (!result.successful()) {
                showAlert("Не удалось архивировать задачу: " + result.message());
                return;
            }
            showAlert("Задача перемещена в архив: " + task.getTitle());
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка архивирования",
                ex,
                ErrorCode.DB_QUERY_FAILED,
                "Не удалось архивировать задачу.",
                false,
                "operation", "archiveTask",
                "taskId", task.getId(),
                "taskTitle", task.getTitle()
            );
        }
    }

    private void showArchivedTasks() {
        currentTaskListViewMode = TaskListViewMode.ARCHIVED;
        currentTaskTagFilter = "";
        updateTaskTableEmptyState();
        rootItem.getChildren().clear();
        for (Task task : tasks) {
            if (!task.isArchived()) continue;
            TreeItem<Task> item = new TreeItem<>(task);
            item.setExpanded(true);
            for (Task sub : task.getSubtasks()) {
                if (sub.isArchived()) item.getChildren().add(new TreeItem<>(sub));
            }
            rootItem.getChildren().add(item);
        }
        taskTable.refresh();
    }

    private List<Task> getSelectedTasks() {
        List<Task> selected = new ArrayList<>();
        for (TreeItem<Task> item : taskTable.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null) selected.add(item.getValue());
        }
        return selected;
    }

    private void bulkArchive() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        List<String> taskIds = collectTaskIds(selected);
        if (taskIds.isEmpty()) {
            showAlert("Выберите задачи (Ctrl+клик)");
            return;
        }
        try {
            UndoRedoManager.CommandResult result = presenter.archiveTasksUndoable(taskIds, true);
            if (!result.successful()) {
                showAlert("Не удалось архивировать задачи: " + result.message());
                return;
            }
            showAlert("Архивировано задач: " + selected.size());
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка массового архивирования",
                ex,
                ErrorCode.DB_QUERY_FAILED,
                "Не удалось архивировать выбранные задачи.",
                false,
                "operation", "bulkArchive",
                "selectedCount", selected.size()
            );
        }
    }

    private void bulkDelete() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        List<String> taskIds = collectTaskIds(selected);
        if (taskIds.isEmpty()) {
            showAlert("Выберите задачи (Ctrl+клик)");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Удалить " + selected.size() + " задач?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        styleAlert(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
            try {
                UndoRedoManager.CommandResult result = presenter.deleteTasksUndoable(taskIds);
                if (!result.successful()) {
                    showAlert("Не удалось удалить задачи: " + result.message());
                    return;
                }
            } catch (RuntimeException ex) {
                UiErrorNotifier.showMappedError(
                    getScene() != null ? getScene().getWindow() : null,
                    ConfigManager.isDarkTheme(),
                    "Ошибка массового удаления",
                    ex,
                    ErrorCode.DB_QUERY_FAILED,
                    "Не удалось удалить выбранные задачи.",
                    false,
                    "operation", "bulkDelete",
                    "selectedCount", selected.size()
                );
            }
        }
    }

    private void bulkAddTag() {
        List<Task> selected = getSelectedTasks();
        if (selected.isEmpty()) { showAlert("Выберите задачи (Ctrl+клик)"); return; }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Добавить тег");
        dialog.setHeaderText(null);
        dialog.setContentText("Тег для " + selected.size() + " задач:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(tag -> {
            String normalizedTag = tag == null ? "" : tag.trim();
            if (!normalizedTag.isEmpty()) {
                Map<String, String> tagsByTaskId = new LinkedHashMap<>();
                for (Task task : selected) {
                    if (task == null || task.getId() == null || task.getId().isBlank()) {
                        continue;
                    }
                    String current = task.getTags() == null ? "" : task.getTags();
                    String updatedTags = current.isEmpty() ? normalizedTag : current + ", " + normalizedTag;
                    tagsByTaskId.put(task.getId(), updatedTags);
                }
                if (tagsByTaskId.isEmpty()) {
                    showAlert("Не удалось подготовить обновление тегов");
                    return;
                }
                try {
                    UndoRedoManager.CommandResult result = presenter.updateTaskTagsUndoable(tagsByTaskId);
                    if (!result.successful()) {
                        showAlert("Не удалось обновить теги: " + result.message());
                        return;
                    }
                    showAlert("Тег добавлен к " + selected.size() + " задачам");
                } catch (RuntimeException ex) {
                    UiErrorNotifier.showMappedError(
                        getScene() != null ? getScene().getWindow() : null,
                        ConfigManager.isDarkTheme(),
                        "Ошибка массового обновления тегов",
                        ex,
                        ErrorCode.DB_QUERY_FAILED,
                        "Не удалось добавить тег к выбранным задачам.",
                        false,
                        "operation", "bulkAddTag",
                        "selectedCount", selected.size(),
                        "tag", normalizedTag
                    );
                }
            }
        });
    }

    private void handleImportTasks() {
        if (taskImportService == null) {
            showAlert("Сервис импорта недоступен");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт задач");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Файлы задач (*.json, *.csv)", "*.json", "*.csv"),
            new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"),
            new FileChooser.ExtensionFilter("CSV (*.csv)", "*.csv")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }

        TaskImportService.ImportFormat format;
        try {
            format = resolveImportFormat(file);
        } catch (IllegalArgumentException ex) {
            showAlert("Поддерживаются только файлы JSON и CSV");
            return;
        }

        String payload;
        try {
            payload = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка чтения файла импорта",
                ex,
                ErrorCode.IO_READ_FAILED,
                "Не удалось прочитать файл импорта.",
                false,
                "operation", "taskImportReadFile",
                "fileName", file.getName()
            );
            return;
        }

        TaskImportService.ImportPreview preview;
        try {
            preview = taskImportService.dryRun(payload, format, TaskImportService.ImportOptions.defaults());
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка предпросмотра импорта",
                ex,
                ErrorCode.VALIDATION_FAILED,
                "Не удалось разобрать файл импорта.",
                false,
                "operation", "taskImportDryRun",
                "fileName", file.getName(),
                "format", format.name()
            );
            return;
        }

        if (!preview.hasChanges()) {
            showAlert("Нет валидных задач для импорта. Проверьте формат файла.");
            return;
        }

        Alert confirm = new Alert(
            Alert.AlertType.CONFIRMATION,
            buildImportPreviewSummary(preview),
            ButtonType.YES,
            ButtonType.NO
        );
        confirm.setTitle("Подтверждение импорта");
        confirm.setHeaderText("Импорт задач из " + file.getName());
        styleAlert(confirm);
        if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            TaskImportService.ImportResult result = taskImportService.apply(preview);
            tasks.setAll(presenter.loadTasks());
            refreshTree();
            showAlert(
                "Импорт завершён: создано " + preview.toCreateCount()
                    + ", обновлено " + preview.toUpdateCount()
                    + ", записей в батче " + result.bulkResult().processedCount()
            );
        } catch (RuntimeException ex) {
            UiErrorNotifier.showMappedError(
                getScene() != null ? getScene().getWindow() : null,
                ConfigManager.isDarkTheme(),
                "Ошибка применения импорта",
                ex,
                ErrorCode.DB_QUERY_FAILED,
                "Не удалось применить импорт задач.",
                false,
                "operation", "taskImportApply",
                "fileName", file.getName()
            );
        }
    }

    private TaskImportService.ImportFormat resolveImportFormat(File file) {
        if (file == null || file.getName() == null) {
            throw new IllegalArgumentException("File is required");
        }
        String fileName = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (fileName.endsWith(".json")) {
            return TaskImportService.ImportFormat.JSON;
        }
        if (fileName.endsWith(".csv")) {
            return TaskImportService.ImportFormat.CSV;
        }
        throw new IllegalArgumentException("Unsupported file type: " + fileName);
    }

    private String buildImportPreviewSummary(TaskImportService.ImportPreview preview) {
        StringBuilder summary = new StringBuilder();
        summary.append("Найдено записей: ").append(preview.sourceCount()).append('\n');
        summary.append("Будет импортировано: ").append(preview.acceptedCount()).append('\n');
        summary.append("Создано: ").append(preview.toCreateCount()).append('\n');
        summary.append("Обновлено: ").append(preview.toUpdateCount()).append('\n');
        summary.append("Дубликатов id: ").append(preview.duplicateIdCount()).append('\n');
        summary.append("Дубликатов title: ").append(preview.duplicateTitleCount()).append('\n');
        summary.append("Невалидных записей: ").append(preview.invalidCount());
        if (!preview.warnings().isEmpty()) {
            summary.append("\n\nПредупреждения:");
            int limit = Math.min(3, preview.warnings().size());
            for (int i = 0; i < limit; i++) {
                summary.append("\n- ").append(preview.warnings().get(i));
            }
            if (preview.warnings().size() > limit) {
                summary.append("\n- ... и ещё ").append(preview.warnings().size() - limit);
            }
        }
        summary.append("\n\nПродолжить импорт?");
        return summary.toString();
    }

    private void handleCreateFromTemplate() {
        List<TaskTemplate> templates = presenter.loadAllTemplates();
        if (templates.isEmpty()) {
            showAlert("Нет сохранённых шаблонов");
            return;
        }
        ChoiceDialog<TaskTemplate> dialog = new ChoiceDialog<>(templates.get(0), templates);
        dialog.setTitle("Создать из шаблона");
        dialog.setHeaderText(null);
        dialog.setContentText("Выберите шаблон:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(template -> {
            Task task = template.createTask();
            presenter.calculatePriority(task);
            UndoRedoManager.CommandResult result = presenter.addTaskUndoable(task);
            if (!result.successful()) {
                showAlert("Не удалось создать задачу из шаблона: " + result.message());
            }
        });
    }

    private void handleSaveAsTemplate() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для сохранения как шаблон");
            return;
        }
        Task task = selected.getValue();
        TextInputDialog dialog = new TextInputDialog(task.getTitle());
        dialog.setTitle("Сохранить шаблон");
        dialog.setHeaderText(null);
        dialog.setContentText("Название шаблона:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
                TaskTemplate template = new TaskTemplate(name.trim(), task.getTitle(), task.getDescription(), 
                    task.getComplexity(), Math.max(1, days), task.getTags());
                presenter.saveTemplate(template);
                showAlert("Шаблон сохранён: " + name);
            }
        });
    }

    private void handleLinkDependency() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу, которая будет зависеть от другой");
            return;
        }
        Task dependentTask = selected.getValue();

        String dependentId = normalizeTaskId(dependentTask.getId());
        if (dependentId == null) {
            showAlert("Не удалось определить идентификатор выбранной задачи");
            return;
        }

        Set<String> alreadyLinked = new LinkedHashSet<>(blockersByTaskId.getOrDefault(dependentId, List.of()));
        List<Task> availableTasks = new ArrayList<>();
        for (Task task : getAllTasksFlat()) {
            String taskId = normalizeTaskId(task.getId());
            if (taskId == null || taskId.equals(dependentId) || alreadyLinked.contains(taskId)) {
                continue;
            }
            availableTasks.add(task);
        }
        availableTasks.sort(Comparator.comparing(task -> task.getTitle() == null ? "" : task.getTitle(), String.CASE_INSENSITIVE_ORDER));

        if (availableTasks.isEmpty()) {
            showAlert("Нет доступных задач для связывания");
            return;
        }

        ChoiceDialog<Task> dialog = new ChoiceDialog<>(availableTasks.get(0), availableTasks);
        dialog.setTitle("Связать задачи");
        dialog.setHeaderText("Задача \"" + dependentTask.getTitle() + "\" будет зависеть от:");
        dialog.setContentText("Выберите задачу:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(blockerTask -> {
            try {
                UndoRedoManager.CommandResult result = presenter.linkDependencyUndoable(
                    dependentTask.getId(),
                    blockerTask.getId()
                );
                if (!result.successful()) {
                    showAlert("Не удалось добавить зависимость: " + result.message());
                    return;
                }
                showAlert("Зависимость добавлена: " + dependentTask.getTitle() + " → " + blockerTask.getTitle());
            } catch (RuntimeException ex) {
                UiErrorNotifier.showMappedError(
                    getScene() != null ? getScene().getWindow() : null,
                    ConfigManager.isDarkTheme(),
                    "Ошибка связывания задач",
                    ex,
                    ErrorCode.VALIDATION_FAILED,
                    "Не удалось добавить зависимость между задачами.",
                    false,
                    "operation", "linkDependency",
                    "dependentTaskId", dependentTask.getId(),
                    "blockerTaskId", blockerTask.getId()
                );
            }
        });
    }

    private void handleUnlinkDependency() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу, у которой нужно удалить зависимость");
            return;
        }
        Task dependentTask = selected.getValue();
        String dependentId = normalizeTaskId(dependentTask.getId());
        if (dependentId == null) {
            showAlert("Не удалось определить идентификатор выбранной задачи");
            return;
        }

        List<String> blockers = blockersByTaskId.getOrDefault(dependentId, List.of());
        if (blockers.isEmpty()) {
            showAlert("У выбранной задачи нет зависимостей для удаления");
            return;
        }

        Map<String, String> blockerByOption = new HashMap<>();
        List<String> options = new ArrayList<>(blockers.size());
        for (String blockerId : blockers) {
            String option = resolveTaskName(blockerId) + " [" + blockerId + "]";
            options.add(option);
            blockerByOption.put(option, blockerId);
        }

        ChoiceDialog<String> dialog = new ChoiceDialog<>(options.get(0), options);
        dialog.setTitle("Удалить зависимость");
        dialog.setHeaderText("Удалить блокирующую задачу для \"" + dependentTask.getTitle() + "\"");
        dialog.setContentText("Выберите зависимость:");
        styleDialog(dialog);
        dialog.showAndWait().ifPresent(option -> {
            String blockerId = blockerByOption.get(option);
            if (blockerId == null) {
                return;
            }
            try {
                UndoRedoManager.CommandResult result = presenter.unlinkDependencyUndoable(
                    dependentTask.getId(),
                    blockerId
                );
                if (!result.successful()) {
                    showAlert("Не удалось удалить зависимость: " + result.message());
                    return;
                }
                showAlert("Зависимость удалена: " + dependentTask.getTitle() + " <-/-> " + resolveTaskName(blockerId));
            } catch (RuntimeException ex) {
                UiErrorNotifier.showMappedError(
                    getScene() != null ? getScene().getWindow() : null,
                    ConfigManager.isDarkTheme(),
                    "Ошибка удаления зависимости",
                    ex,
                    ErrorCode.VALIDATION_FAILED,
                    "Не удалось удалить зависимость между задачами.",
                    false,
                    "operation", "unlinkDependency",
                    "dependentTaskId", dependentTask.getId(),
                    "blockerTaskId", blockerId
                );
            }
        });
    }

    private void handleShowDependencyDetails() {
        TreeItem<Task> selected = taskTable.getSelectionModel().getSelectedItem();
        if (selected == null || selected.getValue() == null) {
            showAlert("Выберите задачу для просмотра связей");
            return;
        }
        showInInspectorTab(RightPanelInspectorTab.ANALYTICS, false);
        Task task = selected.getValue();
        String taskId = normalizeTaskId(task.getId());
        if (taskId == null) {
            showAlert("Не удалось определить идентификатор выбранной задачи");
            return;
        }
        List<String> blockers = blockersByTaskId.getOrDefault(taskId, List.of());
        List<String> dependents = dependentsByTaskId.getOrDefault(taskId, List.of());

        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Связи задачи");
        dialog.setHeaderText(task.getTitle());
        dialog.setContentText(
            "Блокируется задачами:\n" + formatDependencySection(blockers)
                + "\n\nБлокирует задачи:\n" + formatDependencySection(dependents)
        );
        styleAlert(dialog);
        dialog.showAndWait();
    }

    private String formatDependencySection(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return "—";
        }
        StringBuilder builder = new StringBuilder();
        for (String taskId : taskIds) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("- ").append(resolveTaskName(taskId));
        }
        return builder.toString();
    }

    private StackPane createRightPanelOverlayHost() {
        rightPanelOverlayHost.getStyleClass().add("right-panel-overlay-host");
        rightPanelOverlayHost.setVisible(false);
        rightPanelOverlayHost.setManaged(false);
        rightPanelOverlayHost.setMouseTransparent(true);
        rightPanelOverlayHost.setPickOnBounds(false);
        rightPanelOverlayHost.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rightPanelOverlayHost.addEventFilter(KeyEvent.KEY_PRESSED, this::handleRightInspectorTabKeyPressed);

        rightPanelOverlayScrim.getStyleClass().add("right-panel-overlay-scrim");
        rightPanelOverlayScrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rightPanelOverlayScrim.setVisible(false);
        rightPanelOverlayScrim.setManaged(false);
        rightPanelOverlayScrim.setMouseTransparent(true);
        rightPanelOverlayScrim.setOnMouseClicked(e -> {
            closeRightPanelOverlayIfOpen();
            e.consume();
        });
        StackPane.setAlignment(rightPanelOverlayScrim, Pos.CENTER);

        if (!rightPanelOverlayHost.getChildren().contains(rightPanelOverlayScrim)) {
            rightPanelOverlayHost.getChildren().add(rightPanelOverlayScrim);
        }
        return rightPanelOverlayHost;
    }

    private StackPane createOverlayHost() {
        overlayHost.getStyleClass().add("overlay-host");
        overlayHost.setVisible(false);
        overlayHost.setMouseTransparent(true);
        overlayHost.setPickOnBounds(false);
        overlayHost.managedProperty().bind(overlayHost.visibleProperty());

        overlayScrim.getStyleClass().add("overlay-scrim");
        overlayScrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlayScrim.visibleProperty().bind(overlayHost.visibleProperty());
        overlayScrim.managedProperty().bind(overlayHost.visibleProperty());
        overlayScrim.setOnMouseClicked(e -> {
            hideInlineOverlayWithoutClosingTabs();
            e.consume();
        });
        StackPane.setAlignment(overlayScrim, Pos.CENTER);

        overlayTitle.getStyleClass().add("overlay-title");
        overlayTitle.setMinWidth(Region.USE_PREF_SIZE);

        overlayTabStrip.getStyleClass().add("overlay-tab-strip");
        overlayTabStrip.getStyleClass().add("inline-tabs-strip");
        overlayTabStrip.setAlignment(Pos.CENTER_LEFT);
        overlayTabStrip.setMinWidth(Region.USE_PREF_SIZE);
        overlayTabStrip.setFocusTraversable(true);
        overlayTabStrip.setAccessibleText(
            "Вкладки inline-диалогов: Ctrl+Tab, Ctrl+Shift+Tab для переключения; Ctrl+W или Esc для закрытия активной вкладки"
        );

        overlayTabStripScroll.getStyleClass().add("overlay-tab-strip-scroll");
        overlayTabStripScroll.getStyleClass().add("inline-tabs-strip-scroll");
        overlayTabStripScroll.setContent(overlayTabStrip);
        overlayTabStripScroll.setFitToHeight(true);
        overlayTabStripScroll.setFitToWidth(false);
        overlayTabStripScroll.setPannable(true);
        overlayTabStripScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        overlayTabStripScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        overlayTabStripScroll.setMinHeight(34);
        overlayTabStripScroll.setPrefHeight(34);
        overlayTabStripScroll.setMaxHeight(34);
        overlayTabStripScroll.setFocusTraversable(true);
        overlayTabStripScroll.setAccessibleText("Полоса вкладок inline-диалогов");
        HBox.setHgrow(overlayTabStripScroll, Priority.ALWAYS);

        overlayTabMenuButton.setGraphic(FontIcon.of(MaterialDesignV.VIEW_LIST_OUTLINE, 16));
        overlayTabMenuButton.getStyleClass().add("overlay-tab-menu-btn");
        overlayTabMenuButton.setFocusTraversable(false);
        overlayTabMenuButton.setAccessibleText("Список открытых inline-вкладок");
        updateTooltipText(overlayTabMenuButton, "Все открытые inline-вкладки");
        overlayTabMenuButton.setVisible(false);
        overlayTabMenuButton.setManaged(false);

        overlayCloseButton = new Button();
        overlayCloseButton.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 16));
        overlayCloseButton.getStyleClass().add("overlay-close-btn");
        overlayCloseButton.setOnAction(e -> closeActiveTab());
        overlayCloseButton.setAccessibleText("Закрыть активную вкладку");
        updateTooltipText(overlayCloseButton, "Закрыть активную вкладку (Ctrl+W / Esc)");

        HBox header = new HBox(8, overlayTitle, overlayTabStripScroll, overlayTabMenuButton, overlayCloseButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("overlay-header");
        header.setPadding(new Insets(0, 0, 4, 0));

        overlayContentHolder.getStyleClass().add("overlay-content");
        overlayContentHolder.setMinSize(0, 0);
        overlayContentHolder.setMaxWidth(Double.MAX_VALUE);
        overlayContentHolder.setFocusTraversable(true);
        overlayContentHolder.setAccessibleText("Контент активной inline-вкладки");
        VBox.setVgrow(overlayContentHolder, Priority.ALWAYS); // Ensure content takes available space

        // Clip content to prevent overflow
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(overlayContentHolder.widthProperty());
        clip.heightProperty().bind(overlayContentHolder.heightProperty());
        // Adjust arc size to match container radius (approx 12 from CSS)
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        overlayContentHolder.setClip(clip);

        overlayContainer.getStyleClass().add("overlay-container");
        overlayContainer.setSpacing(6);
        overlayContainer.setPadding(new Insets(10, 12, 12, 12));
        overlayContainer.setMaxWidth(Double.MAX_VALUE);
        overlayContainer.setFocusTraversable(true);
        // Адаптивные размеры для низких разрешений: используем больший процент экрана
        overlayContainer.prefWidthProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                double hostWidth = overlayHost.getWidth();
                // На узких экранах (<1400px) используем 95%, иначе 90%
                return hostWidth < 1100 ? hostWidth * 0.98 : hostWidth * 0.92;
            }, overlayHost.widthProperty())
        );
        overlayContainer.maxWidthProperty().bind(overlayHost.widthProperty().multiply(0.98));
        // Адаптивная высота: на низких разрешениях используем больше пространства
        overlayContainer.maxHeightProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(() -> {
                double hostHeight = overlayHost.getHeight();
                // На низких экранах (<800px) используем 95%, иначе 90%
                return hostHeight < 750 ? hostHeight * 0.95 : hostHeight * 0.90;
            }, overlayHost.heightProperty())
        );
        overlayContainer.visibleProperty().bind(overlayHost.visibleProperty());
        overlayContainer.managedProperty().bind(overlayHost.visibleProperty());
        overlayContainer.getChildren().addAll(header, overlayContentHolder);
        overlayHost.widthProperty().addListener((obs, oldWidth, newWidth) -> applyInlineOverlayAdaptiveStyleClasses());
        overlayHost.heightProperty().addListener((obs, oldHeight, newHeight) -> applyInlineOverlayAdaptiveStyleClasses());
        applyInlineOverlayAdaptiveStyleClasses();
        refreshInlineTabStrip();

        // Центрируем диалог по центру экрана
        StackPane.setAlignment(overlayContainer, Pos.CENTER);
        
        overlayHost.getChildren().addAll(overlayScrim, overlayContainer);
        return overlayHost;
    }

    public void showInline(InlineView view, String title) {
        if (view == null) {
            return;
        }
        String tabId = deriveDefaultInlineTabId(view);
        String headerTitle = resolveInlineTabTitle(title, view.getTitle());
        openOrActivateTab(tabId, view, headerTitle);
    }

    public void showInline(Node content, Runnable onClose, String title) {
        if (content == null) {
            return;
        }
        String fallbackId = "inline:node:" + Integer.toHexString(System.identityHashCode(content));
        openOrActivateTab(fallbackId, content, onClose, title, false);
    }

    public void openOrActivateTab(String tabId, InlineView view, String title) {
        if (view == null) {
            return;
        }
        Node content = view.getContent();
        if (content == null) {
            return;
        }

        String normalizedTabId = normalizeInlineTabId(tabId, deriveDefaultInlineTabId(view));
        String resolvedTitle = resolveInlineTabTitle(title, view.getTitle());

        InlineOverlayTab existing = inlineOverlayTabs.get(normalizedTabId);
        if (existing != null) {
            if (existing.inlineView() != view) {
                view.onDispose();
            }
            activateTab(normalizedTabId);
            return;
        }

        view.setCloseAction(() -> closeTab(normalizedTabId));
        InlineOverlayTab tab = new InlineOverlayTab(
            normalizedTabId,
            resolvedTitle,
            view,
            content,
            view.getOnClose(),
            view.isBlocking()
        );
        inlineOverlayTabs.put(normalizedTabId, tab);
        persistInlineOverlayState();
        activateTab(normalizedTabId);
    }

    public void openOrActivateTab(
        String tabId,
        Node content,
        Runnable onClose,
        String title,
        boolean blocking
    ) {
        if (content == null) {
            return;
        }

        String fallbackId = "inline:node:" + Integer.toHexString(System.identityHashCode(content));
        String normalizedTabId = normalizeInlineTabId(tabId, fallbackId);
        InlineOverlayTab existing = inlineOverlayTabs.get(normalizedTabId);
        if (existing != null) {
            activateTab(normalizedTabId);
            return;
        }
        String resolvedTitle = resolveInlineTabTitle(title, "");
        InlineOverlayTab tab = new InlineOverlayTab(
            normalizedTabId,
            resolvedTitle,
            null,
            content,
            onClose,
            blocking
        );
        inlineOverlayTabs.put(normalizedTabId, tab);
        refreshInlineTabStrip();
        persistInlineOverlayState();
        activateTab(normalizedTabId);
    }

    public boolean activateTab(String tabId) {
        String normalizedTabId = normalizeInlineTabId(tabId, null);
        if (normalizedTabId == null) {
            return false;
        }
        InlineOverlayTab tab = inlineOverlayTabs.get(normalizedTabId);
        if (tab == null) {
            return false;
        }

        if (!overlayHost.isVisible()) {
            previousFocusOwner = getScene() != null ? getScene().getFocusOwner() : null;
        }

        activeInlineTabId = normalizedTabId;
        overlayTitle.setText(tab.title());
        overlayContentHolder.getChildren().setAll(tab.contentNode());
        refreshInlineTabStrip();

        overlayHost.setVisible(true);
        overlayHost.setPickOnBounds(true);
        overlayHost.setMouseTransparent(false);

        registerEscapeHandler();
        persistInlineOverlayState();

        Platform.runLater(() -> {
            Node target = tab.contentNode();
            if (target != null) {
                target.requestFocus();
            } else if (overlayContainer != null) {
                overlayContainer.requestFocus();
            }
        });
        return true;
    }

    public boolean closeActiveTab() {
        if (activeInlineTabId == null || activeInlineTabId.isBlank()) {
            return false;
        }
        return closeTab(activeInlineTabId);
    }

    public boolean closeTab(String tabId) {
        return closeTabInternal(tabId, false);
    }

    public boolean canCloseAllTabs() {
        if (inlineOverlayTabs.isEmpty()) {
            return true;
        }
        List<String> reviewOrder = new ArrayList<>();
        if (activeInlineTabId != null && inlineOverlayTabs.containsKey(activeInlineTabId)) {
            reviewOrder.add(activeInlineTabId);
        }
        List<String> keys = new ArrayList<>(inlineOverlayTabs.keySet());
        for (int i = keys.size() - 1; i >= 0; i--) {
            String key = keys.get(i);
            if (!reviewOrder.contains(key)) {
                reviewOrder.add(key);
            }
        }

        for (String key : reviewOrder) {
            InlineOverlayTab tab = inlineOverlayTabs.get(key);
            if (tab == null || tab.inlineView() == null) {
                continue;
            }
            if (!tab.inlineView().canClose()) {
                activateTab(key);
                return false;
            }
        }
        return true;
    }

    public void closeInline() {
        if (inlineOverlayTabs.isEmpty()) {
            hideOverlayHost();
            persistInlineOverlayState();
            return;
        }
        if (activeInlineTabId == null || !inlineOverlayTabs.containsKey(activeInlineTabId)) {
            activeInlineTabId = inlineOverlayTabs.keySet().stream().findFirst().orElse(null);
        }
        if (activeInlineTabId != null) {
            closeActiveTab();
            return;
        }
        hideOverlayHost();
        refreshInlineTabStrip();
        persistInlineOverlayState();
    }
    
    /**
     * Check if application can be closed. Shows confirmation dialog if there are unsaved changes.
     * @return true if close is allowed, false to prevent closing
     */
    public boolean canCloseApplication() {
        return canCloseAllTabs();
    }

    private boolean closeTabInternal(String tabId, boolean skipCanCloseCheck) {
        String normalizedTabId = normalizeInlineTabId(tabId, null);
        if (normalizedTabId == null) {
            return false;
        }
        InlineOverlayTab tab = inlineOverlayTabs.get(normalizedTabId);
        if (tab == null) {
            return false;
        }
        if (!skipCanCloseCheck && tab.inlineView() != null && !tab.inlineView().canClose()) {
            activateTab(normalizedTabId);
            return false;
        }

        String nextTabId = resolveNextTabIdForClosedTab(normalizedTabId);
        boolean removedActive = normalizedTabId.equals(activeInlineTabId);

        inlineOverlayTabs.remove(normalizedTabId);
        disposeInlineOverlayTab(tab);

        if (inlineOverlayTabs.isEmpty()) {
            activeInlineTabId = null;
            overlayContentHolder.getChildren().clear();
            overlayTitle.setText("");
            refreshInlineTabStrip();
            hideOverlayHost();
            persistInlineOverlayState();
            return true;
        }

        if (removedActive) {
            if (nextTabId == null || !activateTab(nextTabId)) {
                String fallbackTabId = inlineOverlayTabs.keySet().stream().findFirst().orElse(null);
                if (fallbackTabId != null) {
                    activateTab(fallbackTabId);
                }
            }
        } else {
            refreshInlineTabStrip();
            persistInlineOverlayState();
        }
        return true;
    }

    private String resolveNextTabIdForClosedTab(String closedTabId) {
        List<String> order = new ArrayList<>(inlineOverlayTabs.keySet());
        int index = order.indexOf(closedTabId);
        if (index < 0) {
            return activeInlineTabId;
        }
        order.remove(index);
        if (order.isEmpty()) {
            return null;
        }
        if (index < order.size()) {
            return order.get(index);
        }
        return order.get(order.size() - 1);
    }

    private void disposeInlineOverlayTab(InlineOverlayTab tab) {
        if (tab == null) {
            return;
        }
        Runnable onClose = tab.onClose();
        if (onClose != null) {
            onClose.run();
        }
        InlineView inlineView = tab.inlineView();
        if (inlineView != null) {
            inlineView.onDispose();
        }
    }

    private void hideOverlayHost() {
        overlayHost.setVisible(false);
        overlayHost.setPickOnBounds(false);
        overlayHost.setMouseTransparent(true);

        if (getScene() != null && overlayEscapeHandler != null) {
            getScene().removeEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
        }
        overlayEscapeHandler = null;

        if (previousFocusOwner != null) {
            previousFocusOwner.requestFocus();
            previousFocusOwner = null;
        }
    }

    private void hideInlineOverlayWithoutClosingTabs() {
        if (!overlayHost.isVisible()) {
            refreshInlineTaskDock();
            return;
        }
        hideOverlayHost();
        refreshInlineTabStrip();
        persistInlineOverlayState();
    }

    private void toggleInlineOverlayVisibilityFromTaskPanel() {
        if (inlineOverlayTabs.isEmpty()) {
            return;
        }
        if (overlayHost.isVisible()) {
            hideInlineOverlayWithoutClosingTabs();
            return;
        }
        String tabId = activeInlineTabId;
        if (tabId == null || !inlineOverlayTabs.containsKey(tabId)) {
            tabId = inlineOverlayTabs.keySet().stream().findFirst().orElse(null);
        }
        if (tabId != null) {
            activateTab(tabId);
        }
    }

    private void restoreInlineOverlayTabsFromConfigIfReady() {
        if (inlineOverlayRestoreCompleted || !inlineOverlayRestoreDataReady || presenter == null) {
            return;
        }
        initializeInlineOverlayRestoreStateIfNeeded();
        if (pendingInlineOverlayRestoreTabIds.isEmpty()) {
            inlineOverlayRestoreCompleted = true;
            persistInlineOverlayState();
            refreshInlineTabStrip();
            return;
        }

        boolean restoredAnyInPass = false;
        List<String> restoreOrder = new ArrayList<>(pendingInlineOverlayRestoreTabIds);
        for (String tabId : restoreOrder) {
            InlineOverlayRestoreOutcome outcome = restoreInlineOverlayTabFromId(tabId);
            if (outcome == InlineOverlayRestoreOutcome.RESTORED) {
                restoredAnyInPass = true;
            }
            pendingInlineOverlayRestoreTabIds.remove(tabId);
        }

        if (restoredAnyInPass && !inlineOverlayTabs.isEmpty()) {
            String activeTabId = resolveRestoredInlineOverlayActiveTabId();
            if (activeTabId != null) {
                activateTab(activeTabId);
            }
            hideInlineOverlayWithoutClosingTabs();
        }

        inlineOverlayRestoreCompleted = true;
        if (inlineOverlayTabs.isEmpty()) {
            activeInlineTabId = null;
            overlayTitle.setText("");
            overlayContentHolder.getChildren().clear();
            hideOverlayHost();
            persistInlineOverlayState();
            refreshInlineTabStrip();
            return;
        }

        String activeTabId = resolveRestoredInlineOverlayActiveTabId();
        if (activeTabId != null) {
            activateTab(activeTabId);
        }
        hideInlineOverlayWithoutClosingTabs();
        persistInlineOverlayState();
    }

    private void initializeInlineOverlayRestoreStateIfNeeded() {
        if (inlineOverlayRestoreInitialized) {
            return;
        }
        inlineOverlayRestoreInitialized = true;
        persistedInlineOverlayActiveTabId = normalizeInlineTabId(ConfigManager.getUxInlineOverlayActiveTabId(), null);
        for (String rawTabId : ConfigManager.getUxInlineOverlayTabOrder()) {
            String tabId = normalizeInlineTabId(rawTabId, null);
            if (tabId != null) {
                pendingInlineOverlayRestoreTabIds.add(tabId);
            }
        }
    }

    private String resolveRestoredInlineOverlayActiveTabId() {
        if (persistedInlineOverlayActiveTabId != null
            && inlineOverlayTabs.containsKey(persistedInlineOverlayActiveTabId)) {
            return persistedInlineOverlayActiveTabId;
        }
        if (activeInlineTabId != null && inlineOverlayTabs.containsKey(activeInlineTabId)) {
            return activeInlineTabId;
        }
        return inlineOverlayTabs.keySet().stream().findFirst().orElse(null);
    }

    private InlineOverlayRestoreOutcome restoreInlineOverlayTabFromId(String tabId) {
        String normalizedTabId = normalizeInlineTabId(tabId, null);
        if (normalizedTabId == null || normalizedTabId.isBlank()) {
            return InlineOverlayRestoreOutcome.SKIP;
        }
        if (inlineOverlayTabs.containsKey(normalizedTabId)) {
            return InlineOverlayRestoreOutcome.RESTORED;
        }
        if (normalizedTabId.startsWith("global:")) {
            return restoreGlobalInlineOverlayTab(normalizedTabId);
        }
        if (normalizedTabId.startsWith("context:")) {
            return restoreContextInlineOverlayTab(normalizedTabId);
        }
        return InlineOverlayRestoreOutcome.SKIP;
    }

    private InlineOverlayRestoreOutcome restoreGlobalInlineOverlayTab(String tabId) {
        String actionId = normalizeInlineTabId(tabId.substring("global:".length()), null);
        if (actionId == null) {
            return InlineOverlayRestoreOutcome.SKIP;
        }
        Supplier<InlineView> viewFactory = resolveRestorableGlobalInlineViewFactory(actionId);
        if (viewFactory == null) {
            return InlineOverlayRestoreOutcome.SKIP;
        }
        InlineView restored = openInlineView(InlineTabMetadata.global(actionId), viewFactory);
        return restored == null ? InlineOverlayRestoreOutcome.SKIP : InlineOverlayRestoreOutcome.RESTORED;
    }

    private InlineOverlayRestoreOutcome restoreContextInlineOverlayTab(String tabId) {
        String[] parts = tabId.split(":", 4);
        if (parts.length < 4) {
            return InlineOverlayRestoreOutcome.SKIP;
        }
        String actionId = normalizeInlineTabId(parts[1], null);
        String entityType = normalizeInlineTabId(parts[2], null);
        String entityId = normalizeInlineTabId(parts[3], null);
        if (actionId == null || entityType == null) {
            return InlineOverlayRestoreOutcome.SKIP;
        }

        if ("main.task.edit".equals(actionId) && "task".equals(entityType)) {
            String taskId = normalizeTaskId(entityId);
            if (taskId == null) {
                return InlineOverlayRestoreOutcome.SKIP;
            }
            Task task = taskIndexById.get(taskId);
            if (task == null) {
                return InlineOverlayRestoreOutcome.SKIP;
            }
            InlineView restored = openInlineView(
                InlineTabMetadata.context(actionId, entityType, taskId),
                () -> createEditTaskInlineView(task)
            );
            return restored == null ? InlineOverlayRestoreOutcome.SKIP : InlineOverlayRestoreOutcome.RESTORED;
        }

        if (("main.inbox.addTask".equals(actionId) || "main.task.addSubtask".equals(actionId))
            && "parentTask".equals(entityType)) {
            String parentId = decodeParentTaskContextEntityId(entityId);
            if ("main.task.addSubtask".equals(actionId)) {
                if (parentId == null) {
                    return InlineOverlayRestoreOutcome.SKIP;
                }
                if (!taskIndexById.containsKey(parentId)) {
                    return InlineOverlayRestoreOutcome.SKIP;
                }
            }
            InlineView restored = openInlineView(
                InlineTabMetadata.context(actionId, entityType, parentId),
                () -> createAddTaskInlineView(parentId)
            );
            return restored == null ? InlineOverlayRestoreOutcome.SKIP : InlineOverlayRestoreOutcome.RESTORED;
        }

        return InlineOverlayRestoreOutcome.SKIP;
    }

    private String decodeParentTaskContextEntityId(String entityId) {
        String normalized = normalizeTaskId(entityId);
        if (normalized == null || "root".equalsIgnoreCase(normalized)) {
            return null;
        }
        return normalized;
    }

    private Supplier<InlineView> resolveRestorableGlobalInlineViewFactory(String actionId) {
        return switch (actionId) {
            case "main.view.calendar" -> () -> CalendarDialog.inline(tasks);
            case "main.view.kanban" -> () -> KanbanDialog.inline(tasks);
            case "main.view.gantt" -> () -> GanttChartDialog.inline(tasks);
            case "main.analytics.dashboard" -> () -> DashboardDialog.inline(tasks);
            case "main.analytics.dailyReview" -> this::buildDailyReviewDialog;
            case "main.analytics.focusBlocks" -> this::buildFocusBlockRecommendationDialog;
            case "main.analytics.planningQuality" -> this::buildPlanningQualityDashboardDialog;
            case "main.analytics.statistics" -> () -> StatisticsDialog.inline(tasks);
            case "main.analytics.personalInsights" -> () -> PersonalInsightsDialog.inline(tasks);
            case "main.analytics.goals" -> GoalsDialog::inline;
            case "main.analytics.timeStats" -> () -> TimeStatsDialog.inline(tasks);
            case "main.analytics.workload" -> () -> WorkloadDialog.inline(tasks);
            case "main.analytics.heatmap" -> () -> HeatmapDialog.inline(tasks);
            case "main.analytics.projectProgress" -> () -> ProjectProgressDialog.inline(tasks);
            case "main.tools.notes.open" -> this::buildSmartNotesDialog;
            case "main.tools.pomodoro" -> PomodoroDialog::inline;
            case "main.tools.timeTracker" -> () -> TimeTrackerDialog.inline(tasks);
            case "main.tools.workHours" -> WorkHoursDialog::inline;
            case "main.ai.chat" -> ChatBotDialog::inline;
            case "main.ai.analyzeCenter" -> () -> AIAnalysisDialog.inline(tasks);
            case "main.ai.reminders" -> () -> SmartRemindersDialog.inline(tasks);
            case "main.ai.categorization" -> () -> SmartCategorizationDialog.inline(tasks);
            case "main.system.export" -> () -> ExportDialog.inline(tasks);
            case "main.system.settings" -> SettingsDialog::inline;
            case "main.system.help" -> HelpDialog::inline;
            default -> null;
        };
    }

    private void refreshInlineTabStrip() {
        overlayTabStrip.getChildren().clear();
        for (InlineOverlayTab tab : inlineOverlayTabs.values()) {
            overlayTabStrip.getChildren().add(buildInlineTabChip(tab));
        }
        boolean hasTabs = !inlineOverlayTabs.isEmpty();
        overlayTabStripScroll.setVisible(hasTabs);
        overlayTabStripScroll.setManaged(hasTabs);
        refreshInlineTabOverflowMenu();
        refreshOverlayCloseButtonState();
        refreshInlineTaskDock();
        if (hasTabs && activeInlineTabId != null && !activeInlineTabId.isBlank()) {
            Platform.runLater(() -> ensureInlineTabVisible(activeInlineTabId));
        }
    }

    private void refreshInlineTabOverflowMenu() {
        overlayTabMenuButton.getItems().clear();

        boolean hasTabs = !inlineOverlayTabs.isEmpty();
        overlayTabMenuButton.setVisible(hasTabs);
        overlayTabMenuButton.setManaged(hasTabs);
        overlayTabMenuButton.setDisable(!hasTabs);
        overlayTabMenuButton.setText(hasTabs ? String.valueOf(inlineOverlayTabs.size()) : "");

        if (!hasTabs) {
            return;
        }

        for (InlineOverlayTab tab : inlineOverlayTabs.values()) {
            if (tab == null || tab.tabId() == null || tab.tabId().isBlank()) {
                continue;
            }
            String title = resolveInlineTabTitle(tab.title(), "Inline");
            boolean active = tab.tabId().equals(activeInlineTabId);

            MenuItem item = new MenuItem((active ? "● " : "") + title);
            item.getStyleClass().add(active ? "overlay-tab-menu-item-active" : "overlay-tab-menu-item");
            item.setOnAction(event -> activateTab(tab.tabId()));
            overlayTabMenuButton.getItems().add(item);
        }
    }

    private HBox buildInlineTabChip(InlineOverlayTab tab) {
        String title = tab == null ? "Inline" : resolveInlineTabTitle(tab.title(), "Inline");
        String tabId = tab == null ? "" : tab.tabId();
        boolean active = tabId != null && tabId.equals(activeInlineTabId);
        boolean closeAllowed = canCloseInlineTab(tab);

        Button tabButton = new Button(title);
        tabButton.getStyleClass().add("overlay-tab-btn");
        tabButton.getStyleClass().add("inline-tab-btn");
        tabButton.getStyleClass().add(active ? "overlay-tab-btn-active" : "overlay-tab-btn-inactive");
        tabButton.getStyleClass().add(active ? "inline-tab-btn-active" : "inline-tab-btn-inactive");
        tabButton.setTextOverrun(OverrunStyle.ELLIPSIS);
        tabButton.setWrapText(false);
        tabButton.maxWidthProperty().bind(
            javafx.beans.binding.Bindings.createDoubleBinding(
                this::resolveInlineTabButtonMaxWidth,
                overlayHost.widthProperty(),
                overlayContainer.widthProperty()
            )
        );
        tabButton.getProperties().put("inlineOverlayTabId", tabId);
        tabButton.getProperties().put("inlineOverlayTabRole", "switch");
        updateTooltipText(
            tabButton,
            active ? ("Активная вкладка: " + title) : ("Переключиться на вкладку: " + title)
        );
        tabButton.setOnAction(e -> {
            if (tabId != null && !tabId.isBlank()) {
                activateTab(tabId);
            }
        });
        tabButton.setAccessibleText((active ? "Активная " : "") + "вкладка: " + title);

        Button closeTabBtn = new Button();
        closeTabBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 12));
        closeTabBtn.getStyleClass().add("overlay-tab-close-btn");
        closeTabBtn.getStyleClass().add("inline-tab-close-btn");
        closeTabBtn.setFocusTraversable(false);
        closeTabBtn.getProperties().put("inlineOverlayTabId", tabId);
        closeTabBtn.getProperties().put("inlineOverlayTabRole", "close");
        closeTabBtn.setDisable(!closeAllowed);
        closeTabBtn.setAccessibleText(
            closeAllowed
                ? "Закрыть вкладку: " + title
                : "Закрытие вкладки недоступно: " + title
        );
        updateTooltipText(
            closeTabBtn,
            closeAllowed
                ? "Закрыть вкладку (Ctrl+W / Esc)"
                : "Закрытие недоступно: есть несохраненные изменения"
        );
        closeTabBtn.setOnAction(e -> {
            e.consume();
            if (tabId != null && !tabId.isBlank()) {
                closeTab(tabId);
            }
        });

        HBox chip = new HBox(4, tabButton, closeTabBtn);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("overlay-tab-chip");
        chip.getStyleClass().add(active ? "overlay-tab-chip-active" : "overlay-tab-chip-inactive");
        chip.setMinHeight(28);
        chip.setMaxHeight(28);
        chip.getProperties().put("inlineOverlayTabId", tabId);
        return chip;
    }

    private void ensureInlineTabVisible(String tabId) {
        if (tabId == null || tabId.isBlank()) {
            return;
        }
        Node target = findInlineTabChipById(overlayTabStrip, tabId);
        if (target == null) {
            return;
        }
        scrollNodeIntoViewHorizontally(overlayTabStripScroll, target);
    }

    private Node findInlineTabChipById(Parent root, String tabId) {
        if (root == null || tabId == null || tabId.isBlank()) {
            return null;
        }
        for (Node child : root.getChildrenUnmodifiable()) {
            Object childTabId = child.getProperties().get("inlineOverlayTabId");
            if (tabId.equals(childTabId)) {
                return child;
            }
            if (child instanceof Parent parent) {
                Node nested = findInlineTabChipById(parent, tabId);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private void scrollNodeIntoViewHorizontally(ScrollPane scrollPane, Node node) {
        if (scrollPane == null || node == null) {
            return;
        }
        Node content = scrollPane.getContent();
        if (content == null) {
            return;
        }

        Bounds viewportBounds = scrollPane.getViewportBounds();
        double viewportWidth = viewportBounds == null ? 0.0 : viewportBounds.getWidth();
        double contentWidth = content.getLayoutBounds().getWidth();
        if (viewportWidth <= 0.0 || contentWidth <= viewportWidth) {
            scrollPane.setHvalue(0.0);
            return;
        }

        Bounds nodeBounds = node.getBoundsInParent();
        double left = nodeBounds.getMinX();
        double right = nodeBounds.getMaxX();
        double scrollableWidth = contentWidth - viewportWidth;
        double currentLeft = scrollPane.getHvalue() * scrollableWidth;
        double currentRight = currentLeft + viewportWidth;

        double targetLeft = currentLeft;
        if (left < currentLeft) {
            targetLeft = left;
        } else if (right > currentRight) {
            targetLeft = right - viewportWidth;
        }

        targetLeft = Math.max(0.0, Math.min(targetLeft, scrollableWidth));
        double targetHValue = scrollableWidth <= 0.0 ? 0.0 : targetLeft / scrollableWidth;
        scrollPane.setHvalue(targetHValue);
    }

    private void refreshInlineTaskDock() {
        inlineTaskDockTabStrip.getChildren().clear();
        if (inlineOverlayTabs.isEmpty()) {
            Label emptyLabel = new Label("Нет открытых inline-вкладок");
            emptyLabel.getStyleClass().add("inline-task-dock-empty");
            inlineTaskDockTabStrip.getChildren().add(emptyLabel);
        } else {
            for (InlineOverlayTab tab : inlineOverlayTabs.values()) {
                inlineTaskDockTabStrip.getChildren().add(buildInlineTaskDockChip(tab));
            }
        }
        boolean hasTabs = !inlineOverlayTabs.isEmpty();
        inlineTaskDockTabStripScroll.setVisible(true);
        inlineTaskDockTabStripScroll.setManaged(true);
        inlineTaskDockTabStripScroll.setMouseTransparent(!hasTabs && overlayHost.isVisible());
        refreshInlineTaskDockToggleButtonState();
    }

    private HBox buildInlineTaskDockChip(InlineOverlayTab tab) {
        String title = tab == null ? "Inline" : resolveInlineTabTitle(tab.title(), "Inline");
        String tabId = tab == null ? "" : tab.tabId();
        boolean active = tabId != null && tabId.equals(activeInlineTabId);
        boolean closeAllowed = canCloseInlineTab(tab);

        Button tabButton = new Button(title);
        tabButton.getStyleClass().add("inline-task-dock-btn");
        tabButton.getStyleClass().add("inline-tab-btn");
        tabButton.getStyleClass().add(active ? "inline-task-dock-btn-active" : "inline-task-dock-btn-inactive");
        tabButton.setTextOverrun(OverrunStyle.ELLIPSIS);
        tabButton.setWrapText(false);
        tabButton.setMaxWidth(190);
        tabButton.getProperties().put("inlineOverlayTabId", tabId);
        tabButton.getProperties().put("inlineOverlayTabRole", "task-dock-switch");
        updateTooltipText(tabButton, "Открыть inline-вкладку: " + title);
        tabButton.setOnAction(e -> {
            if (tabId != null && !tabId.isBlank()) {
                activateTab(tabId);
            }
        });

        Button closeTabBtn = new Button();
        closeTabBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 11));
        closeTabBtn.getStyleClass().add("inline-task-dock-close-btn");
        closeTabBtn.getStyleClass().add("inline-tab-close-btn");
        closeTabBtn.setFocusTraversable(false);
        closeTabBtn.setDisable(!closeAllowed);
        closeTabBtn.getProperties().put("inlineOverlayTabId", tabId);
        closeTabBtn.getProperties().put("inlineOverlayTabRole", "task-dock-close");
        updateTooltipText(
            closeTabBtn,
            closeAllowed
                ? "Закрыть inline-вкладку"
                : "Закрытие недоступно: есть несохраненные изменения"
        );
        closeTabBtn.setOnAction(e -> {
            e.consume();
            if (tabId != null && !tabId.isBlank()) {
                closeTab(tabId);
            }
        });

        HBox chip = new HBox(3, tabButton, closeTabBtn);
        chip.getStyleClass().add("inline-task-dock-chip");
        chip.getStyleClass().add(active ? "inline-task-dock-chip-active" : "inline-task-dock-chip-inactive");
        chip.setAlignment(Pos.CENTER_LEFT);
        return chip;
    }

    private void refreshInlineTaskDockToggleButtonState() {
        if (inlineTaskDockToggleButton == null) {
            return;
        }
        int count = inlineOverlayTabs.size();
        boolean hasTabs = count > 0;
        inlineTaskDockToggleButton.setDisable(!hasTabs);
        inlineTaskDockToggleButton.setText(overlayHost.isVisible() ? ("К задачам (" + count + ")") : ("Inline (" + count + ")"));
        if (!hasTabs) {
            updateTooltipText(inlineTaskDockToggleButton, "Откройте inline-диалог, чтобы он появился в панели задач");
            inlineTaskDockToggleButton.setAccessibleText("Inline-вкладок нет");
            return;
        }
        if (overlayHost.isVisible()) {
            updateTooltipText(inlineTaskDockToggleButton, "Скрыть inline-overlay и вернуться к панели задач");
            inlineTaskDockToggleButton.setAccessibleText("Вернуться к панели задач, сохранив inline-вкладки");
        } else {
            updateTooltipText(inlineTaskDockToggleButton, "Открыть активную inline-вкладку");
            inlineTaskDockToggleButton.setAccessibleText("Открыть inline-вкладку из панели задач");
        }
    }

    private void persistInlineOverlayState() {
        ConfigManager.setUxInlineOverlayTabOrder(new ArrayList<>(inlineOverlayTabs.keySet()));
        ConfigManager.setUxInlineOverlayActiveTabId(activeInlineTabId);
    }

    private void refreshOverlayCloseButtonState() {
        if (overlayCloseButton == null) {
            return;
        }
        InlineOverlayTab activeTab = activeInlineTabId == null ? null : inlineOverlayTabs.get(activeInlineTabId);
        boolean hasActiveTab = activeTab != null;
        boolean closeAllowed = canCloseInlineTab(activeTab);
        overlayCloseButton.setDisable(!hasActiveTab || !closeAllowed);
        if (!hasActiveTab) {
            updateTooltipText(overlayCloseButton, "Нет активной вкладки для закрытия");
            overlayCloseButton.setAccessibleText("Закрыть активную вкладку");
            return;
        }
        String activeTitle = resolveInlineTabTitle(activeTab.title(), "Inline");
        if (closeAllowed) {
            updateTooltipText(overlayCloseButton, "Закрыть активную вкладку (Ctrl+W / Esc)");
            overlayCloseButton.setAccessibleText("Закрыть активную вкладку: " + activeTitle);
        } else {
            updateTooltipText(overlayCloseButton, "Закрытие недоступно: есть несохраненные изменения");
            overlayCloseButton.setAccessibleText("Закрытие активной вкладки недоступно: " + activeTitle);
        }
    }

    private boolean canCloseInlineTab(InlineOverlayTab tab) {
        return tab == null || tab.inlineView() == null || tab.inlineView().canClose();
    }

    private void updateTooltipText(Control control, String text) {
        if (control == null) {
            return;
        }
        String safeText = text == null || text.isBlank() ? "" : text;
        Tooltip tooltip = control.getTooltip();
        if (tooltip == null) {
            control.setTooltip(new Tooltip(safeText));
            return;
        }
        tooltip.setText(safeText);
    }

    private void handleInlineOverlayKeyPressed(KeyEvent event) {
        if (event == null || event.isConsumed() || !overlayHost.isVisible()) {
            return;
        }
        Node sourceNode = event.getTarget() instanceof Node node ? node : null;
        if (sourceNode != null && !isNodeInside(sourceNode, overlayHost)) {
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.TAB) {
            cycleInlineOverlayTab(event.isShiftDown() ? -1 : 1);
            event.consume();
            return;
        }
        if (event.isControlDown() && event.getCode() == KeyCode.W) {
            if (!inlineOverlayTabs.isEmpty()) {
                closeActiveTab();
                event.consume();
            }
            return;
        }
        if (event.getCode() == KeyCode.ESCAPE) {
            if (!inlineOverlayTabs.isEmpty()) {
                closeActiveTab();
                event.consume();
            }
            return;
        }
        if (event.isControlDown() || event.getCode() != KeyCode.TAB) {
            return;
        }

        boolean tabStripFocused = isNodeInside(sourceNode, overlayTabStrip) || isNodeInside(sourceNode, overlayTabStripScroll);
        boolean contentFocused = isNodeInside(sourceNode, overlayContentHolder);
        if (tabStripFocused && !event.isShiftDown()) {
            if (focusActiveInlineTabContent()) {
                event.consume();
            }
            return;
        }
        if (contentFocused && event.isShiftDown() && focusActiveInlineTabButton()) {
            event.consume();
        }
    }

    private void cycleInlineOverlayTab(int step) {
        if (step == 0 || inlineOverlayTabs.size() < 2) {
            return;
        }
        List<String> tabOrder = new ArrayList<>(inlineOverlayTabs.keySet());
        int activeIndex = tabOrder.indexOf(activeInlineTabId);
        if (activeIndex < 0) {
            activeIndex = 0;
        }
        int nextIndex = Math.floorMod(activeIndex + step, tabOrder.size());
        activateTab(tabOrder.get(nextIndex));
    }

    private boolean focusActiveInlineTabContent() {
        if (activeInlineTabId == null || activeInlineTabId.isBlank()) {
            return false;
        }
        InlineOverlayTab activeTab = inlineOverlayTabs.get(activeInlineTabId);
        if (activeTab == null) {
            return false;
        }
        Node contentNode = activeTab.contentNode();
        Node focusTarget = findFirstFocusableDescendant(contentNode);
        if (focusTarget == null && isFocusableNode(overlayContentHolder)) {
            focusTarget = overlayContentHolder;
        }
        if (focusTarget == null && isFocusableNode(overlayContainer)) {
            focusTarget = overlayContainer;
        }
        if (!isFocusableNode(focusTarget)) {
            return false;
        }
        Node target = focusTarget;
        Platform.runLater(target::requestFocus);
        return true;
    }

    private boolean focusActiveInlineTabButton() {
        if (activeInlineTabId == null || activeInlineTabId.isBlank()) {
            return false;
        }
        Button tabButton = findInlineOverlayTabButton(overlayTabStrip, activeInlineTabId, "switch");
        if (!isFocusableNode(tabButton)) {
            return false;
        }
        Platform.runLater(tabButton::requestFocus);
        return true;
    }

    private Button findInlineOverlayTabButton(Node root, String tabId, String role) {
        if (root == null || tabId == null || tabId.isBlank()) {
            return null;
        }
        if (root instanceof Button button) {
            Object buttonTabId = button.getProperties().get("inlineOverlayTabId");
            Object buttonRole = button.getProperties().get("inlineOverlayTabRole");
            if (tabId.equals(buttonTabId) && (role == null || role.equals(buttonRole))) {
                return button;
            }
            return null;
        }
        if (!(root instanceof Parent parent)) {
            return null;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            Button found = findInlineOverlayTabButton(child, tabId, role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Node findFirstFocusableDescendant(Node root) {
        if (isFocusableNode(root)) {
            return root;
        }
        if (!(root instanceof Parent parent)) {
            return null;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            Node found = findFirstFocusableDescendant(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void applyInlineOverlayAdaptiveStyleClasses() {
        double width = resolveInlineOverlayHostWidth();
        double height = resolveInlineOverlayHostHeight();

        boolean compactWidth = width > 0.0 && width < INLINE_OVERLAY_WIDTH_COMPACT_THRESHOLD;
        boolean veryCompactWidth = width > 0.0 && width < INLINE_OVERLAY_WIDTH_VERY_COMPACT_THRESHOLD;
        boolean lowHeight = height > 0.0 && height < INLINE_OVERLAY_HEIGHT_LOW_THRESHOLD;
        boolean veryLowHeight = height > 0.0 && height < INLINE_OVERLAY_HEIGHT_VERY_LOW_THRESHOLD;

        setStyleClassPresent(overlayContainer, "inline-overlay-width-compact", compactWidth);
        setStyleClassPresent(overlayContainer, "inline-overlay-width-very-compact", veryCompactWidth);
        setStyleClassPresent(overlayContainer, "inline-overlay-height-low", lowHeight);
        setStyleClassPresent(overlayContainer, "inline-overlay-height-very-low", veryLowHeight);
        setStyleClassPresent(overlayTabStrip, "inline-tabs-strip-compact", compactWidth || lowHeight);
        setStyleClassPresent(overlayTabStrip, "inline-tabs-strip-very-compact", veryCompactWidth || veryLowHeight);
        setStyleClassPresent(overlayTabStripScroll, "inline-tabs-strip-scroll-compact", compactWidth || lowHeight);
        setStyleClassPresent(overlayTabStripScroll, "inline-tabs-strip-scroll-very-compact", veryCompactWidth || veryLowHeight);
    }

    private double resolveInlineOverlayHostWidth() {
        double width = overlayHost.getWidth();
        if (width > 0.0) {
            return width;
        }
        if (getScene() != null && getScene().getWidth() > 0.0) {
            return getScene().getWidth();
        }
        return getWidth();
    }

    private double resolveInlineOverlayHostHeight() {
        double height = overlayHost.getHeight();
        if (height > 0.0) {
            return height;
        }
        if (getScene() != null && getScene().getHeight() > 0.0) {
            return getScene().getHeight();
        }
        return getHeight();
    }

    private double resolveInlineTabButtonMaxWidth() {
        double width = resolveInlineOverlayHostWidth();
        if (width > 0.0 && width < INLINE_OVERLAY_WIDTH_VERY_COMPACT_THRESHOLD) {
            return INLINE_TAB_MAX_WIDTH_VERY_COMPACT;
        }
        if (width > 0.0 && width < INLINE_OVERLAY_WIDTH_COMPACT_THRESHOLD) {
            return INLINE_TAB_MAX_WIDTH_COMPACT;
        }
        return INLINE_TAB_MAX_WIDTH_DEFAULT;
    }

    private String resolveInlineTabTitle(String preferredTitle, String fallbackTitle) {
        if (preferredTitle != null && !preferredTitle.isBlank()) {
            return preferredTitle.trim();
        }
        if (fallbackTitle != null && !fallbackTitle.isBlank()) {
            return fallbackTitle.trim();
        }
        return "Inline";
    }

    private String buildInlineTabId(InlineTabMetadata metadata, InlineView view) {
        if (metadata == null) {
            return deriveDefaultInlineTabId(view);
        }
        String actionId = normalizeInlineTabSegment(metadata.actionId(), "inline.legacy.unknown");
        InlineTabScope scope = metadata.scope() == null ? InlineTabScope.GLOBAL : metadata.scope();
        if (scope == InlineTabScope.CONTEXT) {
            String entityType = normalizeInlineTabSegment(metadata.entityType(), "entity");
            String entityId = normalizeInlineTabSegment(metadata.entityId(), "root");
            return "context:" + actionId + ":" + entityType + ":" + entityId;
        }
        return "global:" + actionId;
    }

    private String normalizeInlineTabSegment(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return fallback;
        }
        return value.replace(',', '_');
    }

    private String deriveDefaultInlineTabId(InlineView view) {
        if (view == null) {
            return "inline:unknown";
        }
        return "inline:view:" + view.getClass().getName();
    }

    private String normalizeInlineTabId(String tabId, String fallbackTabId) {
        String candidate = (tabId != null && !tabId.isBlank()) ? tabId.trim() : null;
        if (candidate == null || candidate.isBlank()) {
            candidate = fallbackTabId;
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        return candidate;
    }

    private void registerEscapeHandler() {
        if (getScene() == null) return;
        if (overlayEscapeHandler != null) {
            getScene().removeEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
        }
        overlayEscapeHandler = this::handleInlineOverlayKeyPressed;
        getScene().addEventFilter(KeyEvent.KEY_PRESSED, overlayEscapeHandler);
    }

    private enum InlineOverlayRestoreOutcome {
        RESTORED,
        SKIP
    }

    private enum InlineTabScope {
        GLOBAL,
        CONTEXT
    }

    private record InlineTabMetadata(
        String actionId,
        InlineTabScope scope,
        String entityType,
        String entityId
    ) {
        private static InlineTabMetadata global(String actionId) {
            return new InlineTabMetadata(actionId, InlineTabScope.GLOBAL, "", "");
        }

        private static InlineTabMetadata context(String actionId, String entityType, String entityId) {
            return new InlineTabMetadata(actionId, InlineTabScope.CONTEXT, entityType, entityId);
        }
    }

    private record InlineOverlayTab(
        String tabId,
        String title,
        InlineView inlineView,
        Node contentNode,
        Runnable onClose,
        boolean blocking
    ) {
    }

}
