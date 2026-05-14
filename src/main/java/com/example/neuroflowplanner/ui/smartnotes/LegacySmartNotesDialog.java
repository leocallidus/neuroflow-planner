package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.ui.InlineView;
import com.example.neuroflowplanner.ui.ShortcutsHelpDialog;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteController;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteDialog;
import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.ui.layout.AdaptiveLayoutService;
import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.LinkParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class LegacySmartNotesDialog implements InlineView {
    private static final String NOTES_CLASS_ADAPTIVE_ROOT = "adaptive-shell-root";
    private static final String LAYOUT_CLASS_BREAKPOINT_COMPACT = "layout-breakpoint-compact";
    private static final String LAYOUT_CLASS_BREAKPOINT_NORMAL = "layout-breakpoint-normal";
    private static final String LAYOUT_CLASS_BREAKPOINT_WIDE = "layout-breakpoint-wide";
    private static final String LAYOUT_CLASS_DENSITY_COMPACT = "layout-density-compact";
    private static final String LAYOUT_CLASS_DENSITY_COMFORTABLE = "layout-density-comfortable";

    private static LegacySmartNotesDialog instance;
    private static final String NOTES_ACTION_OPEN_PALETTE = "notes.system.commandPalette";
    private static final String NOTES_ACTION_FOCUS_GLOBAL_SEARCH = "notes.system.globalSearchFocus";
    private static final String NOTES_ACTION_SHORTCUTS_HELP = "notes.system.shortcutsHelp";

    private final HBox root;
    private final AdaptiveLayoutService adaptiveLayoutService = new AdaptiveLayoutService();
    private UiLayoutBreakpoint adaptiveBreakpoint = UiLayoutBreakpoint.NORMAL;
    private UiLayoutMode adaptiveDensityMode = UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode());
    private Scene adaptiveObservedScene;
    private final javafx.beans.value.ChangeListener<Number> adaptiveSceneWidthListener =
        (obs, oldWidth, newWidth) -> applyAdaptiveLayoutForWidth(newWidth == null ? 0.0 : newWidth.doubleValue());
    private Runnable closeAction;

    private final ListView<String> notesList = new ListView<>();
    private final TextField searchField = new TextField();
    private final TextField titleField = new TextField();
    private final TextArea contentArea = new TextArea();
    private final javafx.scene.text.TextFlow linkHighlightFlow = new javafx.scene.text.TextFlow();
    private final Pane linkHighlightLayer = new Pane();
    private final Pane linkHighlightRects = new Pane();
    private final StackPane editorStack = new StackPane();
    private final TabPane contentTabs = new TabPane();
    private final WebView markdownPreview = new WebView();
    private final Label statusLabel = new Label("Готово");
    private final Label undoRedoLabel = new Label("Undo: — | Redo: —");
    private final FlowPane outgoingLinksPane = new FlowPane();
    private final FlowPane incomingLinksPane = new FlowPane();
    private final Label outgoingLinksEmpty = new Label("Нет ссылок");
    private final Label incomingLinksEmpty = new Label("Нет обратных ссылок");
    private SmartNotesPresenter presenter;

    private final PauseTransition autoSaveTimer;
    private final PauseTransition previewTimer = new PauseTransition(Duration.millis(200));
    private final PauseTransition linkRefreshTimer = new PauseTransition(Duration.millis(300));
    private final UiActionRegistry commandActionRegistry = UiActionRegistry.withConfigDefaults();
    private final ShortcutRegistry shortcutRegistry = ShortcutRegistry.withConfigDefaults();
    private final CommandPaletteController commandPaletteController = new CommandPaletteController(
        "smartnotes",
        commandActionRegistry,
        (query, limit) -> presenter == null ? List.of() : presenter.searchGlobal(query, limit),
        result -> presenter != null && presenter.openGlobalSearchResult(result)
    );
    private final CommandPaletteDialog commandPaletteDialog = new CommandPaletteDialog(
        "Командная палитра: Заметки",
        commandPaletteController
    );
    private boolean commandPaletteActionsRegistered;
    private String currentNoteTitle;
    private Tab previewTab;
    private String lastPreviewText;
    private String lastPreviewQuery;
    private String searchQuery = "";
    private String lastOverlayText;
    private double lastOverlayWidth = -1;
    private Region contentRegion;
    private boolean contentTextOffsetValid;
    private double contentTextOffsetY;
    private final List<javafx.scene.text.Text> linkHighlightNodes = new ArrayList<>();
    private boolean applyingState;
    private Button saveNoteBtn;
    private Button undoActionBtn;
    private Button redoActionBtn;
    private VBox sidebarBox;
    private VBox editorAreaBox;
    private FlowPane actionsToolbar;
    private boolean shortcutsRegistered;

    public static synchronized InlineView inline() {
        if (instance == null) {
            instance = new LegacySmartNotesDialog();
        }
        instance.applyTheme();
        return instance;
    }

    private LegacySmartNotesDialog() {
        root = new HBox();
        root.getStyleClass().addAll("notes-root", NOTES_CLASS_ADAPTIVE_ROOT);
        root.setPrefSize(800, 600);
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcutKeyPressed);
        registerCommandPaletteActions();
        registerShortcutBindings();
        commandPaletteDialog.setSidebarRevealHandler(this::showActionInNotesPanel);

        sidebarBox = new VBox(10);
        sidebarBox.setPadding(new Insets(15));
        sidebarBox.getStyleClass().add("notes-sidebar");

        VBox sidebarHeader = new VBox(6);
        sidebarHeader.setAlignment(Pos.CENTER_LEFT);
        Label sidebarTitle = new Label("Заметки");
        sidebarTitle.getStyleClass().add("notes-sidebar-title");

        Button addBtn = new Button();
        addBtn.setGraphic(FontIcon.of(MaterialDesignP.PLUS, 16));
        addBtn.getStyleClass().add("notes-add-btn");
        addBtn.setTooltip(new Tooltip("Новая заметка (Ctrl/Cmd+N)"));
        addBtn.setOnAction(e -> createNewNote());

        MenuButton templateBtn = new MenuButton("Шаблоны");
        templateBtn.setGraphic(FontIcon.of(MaterialDesignL.LAYERS, 14));
        templateBtn.getStyleClass().add("notes-template-btn");
        MenuItem diaryTemplate = new MenuItem("Дневник");
        diaryTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.DIARY));
        MenuItem retroTemplate = new MenuItem("Ретроспектива");
        retroTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.RETROSPECTIVE));
        MenuItem plansTemplate = new MenuItem("Планы");
        plansTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.PLANS));
        MenuItem meetingTemplate = new MenuItem("Встреча");
        meetingTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.MEETING));
        MenuItem oneOnOneTemplate = new MenuItem("1-on-1");
        oneOnOneTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.ONE_ON_ONE));
        MenuItem statusTemplate = new MenuItem("Отчет о статусе");
        statusTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.STATUS_REPORT));
        MenuItem postmortemTemplate = new MenuItem("Постмортем");
        postmortemTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.POSTMORTEM));
        MenuItem specTemplate = new MenuItem("Требования");
        specTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.SPEC));
        MenuItem ideasTemplate = new MenuItem("Идеи");
        ideasTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.IDEAS));
        MenuItem learningTemplate = new MenuItem("Учебный план");
        learningTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.LEARNING_PLAN));
        MenuItem reflectionTemplate = new MenuItem("Рефлексия недели");
        reflectionTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.WEEKLY_REFLECTION));
        MenuItem projectTemplate = new MenuItem("План проекта");
        projectTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.PROJECT_PLAN));
        MenuItem shoppingTemplate = new MenuItem("Покупки/поручения");
        shoppingTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.SHOPPING));
        MenuItem researchTemplate = new MenuItem("Исследование");
        researchTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.RESEARCH));
        MenuItem habitsTemplate = new MenuItem("Трекер привычек");
        habitsTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.HABITS));
        MenuItem reviewTemplate = new MenuItem("Ревью книги/курса");
        reviewTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.REVIEW));
        MenuItem travelTemplate = new MenuItem("План путешествия");
        travelTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.TRAVEL));
        MenuItem okrTemplate = new MenuItem("OKR");
        okrTemplate.setOnAction(e -> createNoteFromTemplate(NoteTemplate.OKR));
        templateBtn.getItems().addAll(
            diaryTemplate,
            retroTemplate,
            plansTemplate,
            new SeparatorMenuItem(),
            meetingTemplate,
            oneOnOneTemplate,
            statusTemplate,
            postmortemTemplate,
            specTemplate,
            new SeparatorMenuItem(),
            ideasTemplate,
            learningTemplate,
            reflectionTemplate,
            projectTemplate,
            shoppingTemplate,
            researchTemplate,
            new SeparatorMenuItem(),
            habitsTemplate,
            reviewTemplate,
            travelTemplate,
            okrTemplate
        );

        HBox sidebarTitleRow = new HBox(sidebarTitle);
        sidebarTitleRow.setAlignment(Pos.CENTER_LEFT);
        HBox sidebarActionsRow = new HBox(8, templateBtn, addBtn);
        sidebarActionsRow.setAlignment(Pos.CENTER_LEFT);
        sidebarHeader.getChildren().addAll(sidebarTitleRow, sidebarActionsRow);

        HBox searchRow = new HBox(8);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getStyleClass().add("notes-search-row");
        searchField.setPromptText("Поиск... (Ctrl/Cmd+F)");
        searchField.getStyleClass().add("notes-search-field");
        searchField.setContextMenu(createRussianContextMenu(searchField));
        searchField.textProperty().addListener((obs, old, val) -> updateSearchQuery(val));

        Button clearSearchBtn = new Button();
        clearSearchBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 14));
        clearSearchBtn.getStyleClass().add("notes-search-clear");
        clearSearchBtn.setOnAction(e -> searchField.clear());
        clearSearchBtn.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        clearSearchBtn.managedProperty().bind(clearSearchBtn.visibleProperty());
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchRow.getChildren().addAll(searchField, clearSearchBtn);

        notesList.getStyleClass().add("notes-list");
        VBox.setVgrow(notesList, Priority.ALWAYS);
        notesList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                if (searchQuery == null || searchQuery.isBlank()) {
                    setText(item);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(buildHighlightedText(item, searchQuery));
            }
        });
        notesList.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (applyingState) {
                return;
            }
            if (value != null) {
                loadNote(value);
            }
        });

        ContextMenu listMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(e -> {
            String selected = notesList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteNote(selected);
            }
        });
        MenuItem openViaPaletteItem = new MenuItem("Открыть через палитру");
        openViaPaletteItem.setOnAction(e -> {
            String selected = notesList.getSelectionModel().getSelectedItem();
            openCommandPaletteWithQuery(selected);
        });
        MenuItem showInPanelItem = new MenuItem("Показать в панели");
        showInPanelItem.setOnAction(e -> {
            String selected = notesList.getSelectionModel().getSelectedItem();
            showCurrentNoteInPanel(selected);
        });
        listMenu.getItems().addAll(openViaPaletteItem, showInPanelItem, new SeparatorMenuItem(), deleteItem);
        listMenu.setOnShowing(e -> {
            String selected = notesList.getSelectionModel().getSelectedItem();
            boolean hasSelection = selected != null && !selected.isBlank();
            openViaPaletteItem.setDisable(!ConfigManager.isUxCommandPaletteEnabled() || !hasSelection);
            showInPanelItem.setDisable(!hasSelection);
        });
        notesList.setContextMenu(listMenu);
        sidebarBox.getChildren().addAll(sidebarHeader, searchRow, notesList);

        editorAreaBox = new VBox(15);
        editorAreaBox.setPadding(new Insets(20, 25, 20, 25));
        editorAreaBox.getStyleClass().add("notes-editor-area");
        HBox.setHgrow(editorAreaBox, Priority.ALWAYS);

        HBox editorHeader = new HBox(15);
        editorHeader.setAlignment(Pos.CENTER_LEFT);

        titleField.setPromptText("Название заметки...");
        titleField.getStyleClass().add("notes-title-field");
        titleField.setContextMenu(createRussianContextMenu(titleField));
        HBox.setHgrow(titleField, Priority.ALWAYS);

        autoSaveTimer = new PauseTransition(Duration.seconds(1.5));
        autoSaveTimer.setOnFinished(e -> saveCurrentNote(false));

        titleField.textProperty().addListener((obs, old, val) -> {
            if (applyingState) {
                return;
            }
            if (currentNoteTitle != null) {
                statusLabel.setText("Изменено...");
                autoSaveTimer.playFromStart();
            }
            linkRefreshTimer.playFromStart();
        });

        Button aiActionBtn = new Button("Спросить ИИ");
        aiActionBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 16));
        aiActionBtn.getStyleClass().add("notes-ai-btn");
        aiActionBtn.setOnAction(e -> showAiPrompt());

        saveNoteBtn = new Button("Сохранить");
        saveNoteBtn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_SAVE, 16));
        saveNoteBtn.getStyleClass().add("notes-export-btn");
        saveNoteBtn.setTooltip(new Tooltip("Сохранить и добавить в историю undo/redo"));
        saveNoteBtn.setOnAction(e -> saveCurrentNote(true));

        undoActionBtn = new Button();
        undoActionBtn.setGraphic(FontIcon.of(MaterialDesignU.UNDO_VARIANT, 16));
        undoActionBtn.getStyleClass().add("notes-export-btn");
        undoActionBtn.setTooltip(new Tooltip("Отменить последнее действие с заметкой (Ctrl/Cmd+Z)"));
        undoActionBtn.setOnAction(e -> undoLastNoteAction());

        redoActionBtn = new Button();
        redoActionBtn.setGraphic(FontIcon.of(MaterialDesignR.REDO_VARIANT, 16));
        redoActionBtn.getStyleClass().add("notes-export-btn");
        redoActionBtn.setTooltip(new Tooltip("Повторить отменённое действие (Ctrl/Cmd+Shift+Z)"));
        redoActionBtn.setOnAction(e -> redoLastNoteAction());

        Button exportPdfBtn = new Button("PDF");
        exportPdfBtn.setGraphic(FontIcon.of(MaterialDesignF.FILE_PDF, 16));
        exportPdfBtn.getStyleClass().add("notes-export-btn");
        exportPdfBtn.setTooltip(new Tooltip("Экспорт заметки в PDF"));
        exportPdfBtn.setOnAction(e -> exportCurrentNoteToPdf());

        Button exportAllPdfBtn = new Button("PDF все");
        exportAllPdfBtn.setGraphic(FontIcon.of(MaterialDesignF.FILE_PDF, 16));
        exportAllPdfBtn.getStyleClass().add("notes-export-btn");
        exportAllPdfBtn.setTooltip(new Tooltip("Экспорт всех заметок в PDF"));
        exportAllPdfBtn.setOnAction(e -> exportAllNotesToPdf());

        Button paletteBtn = new Button();
        paletteBtn.setGraphic(FontIcon.of(MaterialDesignM.MAGNIFY, 16));
        paletteBtn.getStyleClass().add("notes-export-btn");
        paletteBtn.setTooltip(new Tooltip("Командная палитра (Ctrl/Cmd+K)"));
        paletteBtn.setOnAction(e -> openCommandPalette());
        
        Button shortcutsHelpBtn = new Button();
        shortcutsHelpBtn.setGraphic(FontIcon.of(MaterialDesignH.HELP_CIRCLE_OUTLINE, 16));
        shortcutsHelpBtn.getStyleClass().add("notes-export-btn");
        shortcutsHelpBtn.setTooltip(new Tooltip("Горячие клавиши"));
        shortcutsHelpBtn.setOnAction(e -> showShortcutsHelp());

        actionsToolbar = new FlowPane(
            8,
            6,
            saveNoteBtn,
            undoActionBtn,
            redoActionBtn,
            paletteBtn,
            shortcutsHelpBtn,
            aiActionBtn,
            exportPdfBtn,
            exportAllPdfBtn
        );
        actionsToolbar.getStyleClass().addAll("notes-actions-toolbar", "adaptive-toolbar");
        actionsToolbar.setAlignment(Pos.CENTER_RIGHT);
        actionsToolbar.setPrefWrapLength(340);
        editorHeader.getChildren().addAll(titleField, actionsToolbar);

        contentArea.setPromptText("Начните писать здесь... (Markdown поддерживается)");
        contentArea.getStyleClass().add("notes-content-area");
        contentArea.setWrapText(true);
        contentArea.setContextMenu(createRussianContextMenu(contentArea));
        contentArea.skinProperty().addListener((obs, old, skin) -> Platform.runLater(this::bindOverlayToContent));
        contentArea.widthProperty().addListener((obs, old, val) -> {
            lastOverlayText = null;
            updateOverlayLayout();
        });
        contentArea.textProperty().addListener((obs, old, val) -> {
            if (applyingState) {
                return;
            }
            if (currentNoteTitle != null) {
                statusLabel.setText("Изменено...");
                autoSaveTimer.playFromStart();
            }
            if (contentTabs.getSelectionModel().getSelectedItem() == previewTab) {
                previewTimer.playFromStart();
            }
            linkRefreshTimer.playFromStart();
        });

        linkHighlightFlow.getStyleClass().add("notes-link-overlay");
        linkHighlightFlow.setMouseTransparent(true);
        linkHighlightFlow.setManaged(false);

        linkHighlightRects.setMouseTransparent(true);
        linkHighlightRects.setManaged(false);

        linkHighlightLayer.getChildren().addAll(linkHighlightRects, linkHighlightFlow);
        linkHighlightLayer.setMouseTransparent(true);
        linkHighlightLayer.setManaged(false);
        StackPane.setAlignment(linkHighlightLayer, Pos.TOP_LEFT);
        contentArea.scrollTopProperty().addListener((obs, old, val) -> updateOverlayLayout());
        contentArea.scrollLeftProperty().addListener((obs, old, val) -> updateOverlayLayout());

        editorStack.getChildren().addAll(contentArea, linkHighlightLayer);
        Platform.runLater(this::bindOverlayToContent);

        Tab editorTab = new Tab("Редактор");
        editorTab.setClosable(false);
        editorTab.setContent(editorStack);

        previewTab = new Tab("Просмотр");
        previewTab.setClosable(false);
        markdownPreview.getStyleClass().add("notes-markdown-preview");
        StackPane previewPane = new StackPane(markdownPreview);
        previewPane.getStyleClass().add("notes-markdown-pane");
        previewTab.setContent(previewPane);

        contentTabs.getStyleClass().add("notes-tab-pane");
        contentTabs.getTabs().addAll(editorTab, previewTab);
        contentTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(contentTabs, Priority.ALWAYS);

        previewTimer.setOnFinished(e -> refreshMarkdownPreview());
        linkRefreshTimer.setOnFinished(e -> {
            refreshLinkPanels();
            updateLinkHighlightOverlay();
        });

        markdownPreview.getEngine().locationProperty().addListener((obs, old, loc) -> {
            if (loc == null || loc.isBlank()) {
                return;
            }
            if (loc.startsWith("nfp-note:")) {
                String target = decodeLinkTarget(loc.substring("nfp-note:".length()));
                handleNoteLinkClick(target);
                Platform.runLater(this::refreshMarkdownPreview);
            } else if (loc.startsWith("nfp-task:")) {
                String target = decodeLinkTarget(loc.substring("nfp-task:".length()));
                handleTaskLinkClick(target);
                Platform.runLater(this::refreshMarkdownPreview);
            }
        });

        contentTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value == previewTab) {
                refreshMarkdownPreview();
            }
        });

        VBox outgoingBox = new VBox(6);
        Label outgoingLabel = new Label("Ссылки");
        outgoingLabel.getStyleClass().add("notes-links-title");
        outgoingLinksPane.getStyleClass().add("notes-links-pane");
        outgoingLinksPane.setHgap(6);
        outgoingLinksPane.setVgap(6);
        outgoingLinksEmpty.getStyleClass().add("notes-links-empty");
        outgoingBox.getChildren().addAll(outgoingLabel, outgoingLinksPane);

        VBox incomingBox = new VBox(6);
        Label incomingLabel = new Label("Обратные ссылки");
        incomingLabel.getStyleClass().add("notes-links-title");
        incomingLinksPane.getStyleClass().add("notes-links-pane");
        incomingLinksPane.setHgap(6);
        incomingLinksPane.setVgap(6);
        incomingLinksEmpty.getStyleClass().add("notes-links-empty");
        incomingBox.getChildren().addAll(incomingLabel, incomingLinksPane);

        HBox linksRow = new HBox(16, outgoingBox, incomingBox);
        linksRow.getStyleClass().add("notes-links-row");
        HBox.setHgrow(outgoingBox, Priority.ALWAYS);
        HBox.setHgrow(incomingBox, Priority.ALWAYS);

        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_RIGHT);
        undoRedoLabel.getStyleClass().add("notes-status-label");
        statusLabel.getStyleClass().add("notes-status-label");
        statusBar.getChildren().addAll(undoRedoLabel, statusLabel);

        editorAreaBox.getChildren().addAll(editorHeader, contentTabs, linksRow, statusBar);
        root.getChildren().addAll(sidebarBox, editorAreaBox);

        applyTheme();
        showEmptyLinks();
        initializeAdaptiveLayout();
    }

    void setPresenter(SmartNotesPresenter presenter) {
        this.presenter = presenter;
    }

    void applyState(SmartNotesState state) {
        if (state == null) {
            return;
        }

        autoSaveTimer.stop();
        applyingState = true;
        try {
            searchQuery = state.searchQuery();
            if (!Objects.equals(searchField.getText(), searchQuery)) {
                searchField.setText(searchQuery);
            }

            notesList.getItems().setAll(state.noteTitles());
            notesList.refresh();

            String selected = state.selectedNoteTitle().isBlank() ? null : state.selectedNoteTitle();
            if (selected != null && notesList.getItems().contains(selected)) {
                notesList.getSelectionModel().select(selected);
            } else {
                notesList.getSelectionModel().clearSelection();
            }

            currentNoteTitle = selected;
            titleField.setText(selected == null ? "" : selected);
            contentArea.setText(state.selectedNoteContent());
            statusLabel.setText(state.statusMessage());
            updateUndoRedoControls(state);
        } finally {
            applyingState = false;
        }

        refreshLinkPanels();
        updateLinkHighlightOverlay();
        if (contentTabs.getSelectionModel().getSelectedItem() == previewTab) {
            refreshMarkdownPreview();
        }
    }

    private void updateUndoRedoControls(SmartNotesState state) {
        String undoLabel = state.nextUndoLabel() == null || state.nextUndoLabel().isBlank()
            ? "—"
            : state.nextUndoLabel();
        String redoLabel = state.nextRedoLabel() == null || state.nextRedoLabel().isBlank()
            ? "—"
            : state.nextRedoLabel();

        if (undoActionBtn != null) {
            undoActionBtn.setDisable(!state.undoAvailable());
            undoActionBtn.setTooltip(new Tooltip("Отменить: " + undoLabel + " (Ctrl/Cmd+Z)"));
        }
        if (redoActionBtn != null) {
            redoActionBtn.setDisable(!state.redoAvailable());
            redoActionBtn.setTooltip(new Tooltip("Повторить: " + redoLabel + " (Ctrl/Cmd+Shift+Z)"));
        }
        undoRedoLabel.setText("Undo: " + undoLabel + " | Redo: " + redoLabel);
    }

    private void createNewNote() {
        if (presenter == null) {
            return;
        }
        saveCurrentNote(false);
        presenter.createNewNote();
        Platform.runLater(titleField::requestFocus);
    }

    private void createNoteFromTemplate(NoteTemplate template) {
        if (presenter == null) {
            return;
        }
        saveCurrentNote(false);
        presenter.createNoteFromTemplate(template.name());
        Platform.runLater(titleField::requestFocus);
    }

    private void deleteNote(String title) {
        if (presenter == null) {
            return;
        }
        saveCurrentNote(false);
        presenter.deleteNote(title);
    }

    private void loadNote(String title) {
        if (presenter == null) {
            return;
        }
        presenter.selectNote(title, currentNoteTitle, titleField.getText(), contentArea.getText());
    }

    private void saveCurrentNote() {
        saveCurrentNote(false);
    }

    private void saveCurrentNote(boolean trackInHistory) {
        if (presenter == null) {
            return;
        }
        if (trackInHistory) {
            presenter.saveCurrentNote(currentNoteTitle, titleField.getText(), contentArea.getText());
            return;
        }
        presenter.autoSaveCurrentNote(currentNoteTitle, titleField.getText(), contentArea.getText());
    }

    private void undoLastNoteAction() {
        if (presenter == null) {
            return;
        }
        autoSaveTimer.stop();
        UndoRedoManager.CommandResult result = presenter.undoLastAction();
        if (!result.successful()) {
            statusLabel.setText("Undo недоступен: " + result.message());
        }
    }

    private void redoLastNoteAction() {
        if (presenter == null) {
            return;
        }
        autoSaveTimer.stop();
        UndoRedoManager.CommandResult result = presenter.redoLastAction();
        if (!result.successful()) {
            statusLabel.setText("Redo недоступен: " + result.message());
        }
    }

    private void openCommandPalette() {
        if (!ConfigManager.isUxCommandPaletteEnabled()) {
            return;
        }
        commandPaletteDialog.toggle(root.getScene() != null ? root.getScene().getWindow() : null);
    }

    private void openCommandPaletteWithQuery(String query) {
        if (!ConfigManager.isUxCommandPaletteEnabled()) {
            return;
        }
        String normalized = query == null ? "" : query.trim();
        commandPaletteDialog.open(
            root.getScene() != null ? root.getScene().getWindow() : null,
            normalized
        );
    }

    private void focusGlobalSearch() {
        if (!ConfigManager.isUxGlobalSearchEnabled()) {
            return;
        }
        openCommandPalette();
    }

    private void showShortcutsHelp() {
        ShortcutsHelpDialog.show(
            root.getScene() != null ? root.getScene().getWindow() : null,
            "Горячие клавиши: заметки",
            ShortcutsHelpDialog.defaultNotesEntries()
        );
    }

    private boolean showCurrentNoteInPanel(String noteTitle) {
        if (noteTitle == null || noteTitle.isBlank()) {
            return false;
        }
        if (!notesList.getItems().contains(noteTitle)) {
            return false;
        }
        notesList.getSelectionModel().select(noteTitle);
        notesList.scrollTo(noteTitle);
        notesList.requestFocus();
        return true;
    }

    private boolean showActionInNotesPanel(String actionId) {
        String normalized = actionId == null ? "" : actionId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        return switch (normalized) {
            case "notes.create" -> {
                searchField.requestFocus();
                yield true;
            }
            case "notes.save.current", "notes.delete.current", "notes.ai.prompt", "notes.export.current.pdf" -> {
                if (showCurrentNoteInPanel(currentNoteTitle)) {
                    yield true;
                }
                titleField.requestFocus();
                yield true;
            }
            case "notes.export.all.pdf", "notes.system.commandpalette", "notes.system.globalsearchfocus" -> {
                searchField.requestFocus();
                yield true;
            }
            case "notes.undo" -> {
                if (undoActionBtn != null) {
                    undoActionBtn.requestFocus();
                    yield true;
                }
                yield false;
            }
            case "notes.redo" -> {
                if (redoActionBtn != null) {
                    redoActionBtn.requestFocus();
                    yield true;
                }
                yield false;
            }
            case "notes.system.shortcutshelp" -> {
                root.requestFocus();
                yield true;
            }
            default -> false;
        };
    }

    private void initializeAdaptiveLayout() {
        applyAdaptiveLayoutForWidth(root.getWidth());
        root.sceneProperty().addListener((obs, oldScene, newScene) -> attachAdaptiveScene(newScene));
        attachAdaptiveScene(root.getScene());
    }

    private void attachAdaptiveScene(Scene scene) {
        if (adaptiveObservedScene == scene) {
            if (scene != null) {
                applyAdaptiveLayoutForWidth(scene.getWidth());
            }
            return;
        }
        if (adaptiveObservedScene != null) {
            adaptiveObservedScene.widthProperty().removeListener(adaptiveSceneWidthListener);
        }
        adaptiveObservedScene = scene;
        if (adaptiveObservedScene == null) {
            applyAdaptiveLayoutForWidth(root.getWidth());
            return;
        }
        adaptiveObservedScene.widthProperty().addListener(adaptiveSceneWidthListener);
        double width = adaptiveObservedScene.getWidth() > 0.0 ? adaptiveObservedScene.getWidth() : root.getWidth();
        applyAdaptiveLayoutForWidth(width);
    }

    private void applyAdaptiveLayoutForWidth(double width) {
        adaptiveBreakpoint = adaptiveLayoutService.resolveBreakpoint(width);
        adaptiveDensityMode = UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode());
        applyAdaptiveStyleClasses();
        applyAdaptiveSizing();
    }

    private void applyAdaptiveStyleClasses() {
        root.getStyleClass().removeAll(
            LAYOUT_CLASS_BREAKPOINT_COMPACT,
            LAYOUT_CLASS_BREAKPOINT_NORMAL,
            LAYOUT_CLASS_BREAKPOINT_WIDE,
            LAYOUT_CLASS_DENSITY_COMPACT,
            LAYOUT_CLASS_DENSITY_COMFORTABLE
        );
        root.getStyleClass().add(switch (adaptiveBreakpoint) {
            case COMPACT -> LAYOUT_CLASS_BREAKPOINT_COMPACT;
            case NORMAL -> LAYOUT_CLASS_BREAKPOINT_NORMAL;
            case WIDE -> LAYOUT_CLASS_BREAKPOINT_WIDE;
        });
        root.getStyleClass().add(adaptiveDensityMode == UiLayoutMode.COMPACT
            ? LAYOUT_CLASS_DENSITY_COMPACT
            : LAYOUT_CLASS_DENSITY_COMFORTABLE);
    }

    private void applyAdaptiveSizing() {
        if (sidebarBox == null || editorAreaBox == null || actionsToolbar == null) {
            return;
        }
        boolean compact = adaptiveBreakpoint == UiLayoutBreakpoint.COMPACT;
        double sidebarWidth = compact ? 74.0 : 250.0;
        sidebarBox.setMinWidth(sidebarWidth);
        sidebarBox.setPrefWidth(sidebarWidth);
        sidebarBox.setMaxWidth(compact ? 84.0 : 300.0);
        sidebarBox.setPadding(compact ? new Insets(10, 8, 10, 8) : new Insets(15));

        if (compact) {
            actionsToolbar.setHgap(6.0);
            actionsToolbar.setVgap(6.0);
            actionsToolbar.setPrefWrapLength(260.0);
        } else {
            actionsToolbar.setHgap(8.0);
            actionsToolbar.setVgap(6.0);
            actionsToolbar.setPrefWrapLength(360.0);
        }
    }

    private void registerCommandPaletteActions() {
        if (commandPaletteActionsRegistered) {
            return;
        }
        commandPaletteActionsRegistered = true;

        registerPaletteAction(
            "notes.create",
            "Создать заметку",
            "notes",
            "Ctrl/Cmd+N",
            this::createNewNote,
            this::isPresenterReady,
            () -> "Презентер не инициализирован",
            false
        );
        registerPaletteAction(
            "notes.save.current",
            "Сохранить заметку",
            "notes",
            "Ctrl/Cmd+S",
            () -> saveCurrentNote(true),
            this::hasCurrentSelection,
            () -> "Выберите заметку",
            false
        );
        registerPaletteAction(
            "notes.delete.current",
            "Удалить текущую заметку",
            "notes",
            "",
            () -> deleteNote(currentNoteTitle),
            this::hasCurrentSelection,
            () -> "Выберите заметку",
            true
        );
        registerPaletteAction(
            "notes.ai.prompt",
            "ИИ: спросить по текущей заметке",
            "ai",
            "",
            this::showAiPrompt,
            this::hasCurrentSelection,
            () -> "Выберите заметку",
            false
        );
        registerPaletteAction(
            "notes.export.current.pdf",
            "Экспорт текущей заметки в PDF",
            "export",
            "",
            this::exportCurrentNoteToPdf,
            this::hasCurrentSelection,
            () -> "Выберите заметку",
            false
        );
        registerPaletteAction(
            "notes.export.all.pdf",
            "Экспорт всех заметок в PDF",
            "export",
            "",
            this::exportAllNotesToPdf,
            this::isPresenterReady,
            () -> "Презентер не инициализирован",
            false
        );
        registerPaletteAction(
            "notes.undo",
            "Undo: отменить последнее действие",
            "history",
            "Ctrl/Cmd+Z",
            this::undoLastNoteAction,
            this::isPresenterReady,
            () -> "Undo недоступен",
            false
        );
        registerPaletteAction(
            "notes.redo",
            "Redo: повторить отмененное действие",
            "history",
            "Ctrl/Cmd+Shift+Z",
            this::redoLastNoteAction,
            this::isPresenterReady,
            () -> "Redo недоступен",
            false
        );
        registerPaletteAction(
            NOTES_ACTION_OPEN_PALETTE,
            "Открыть командную палитру",
            "system",
            "Ctrl/Cmd+K",
            this::openCommandPalette,
            () -> ConfigManager.isUxCommandPaletteEnabled(),
            () -> "Командная палитра отключена",
            false
        );
        registerPaletteAction(
            NOTES_ACTION_FOCUS_GLOBAL_SEARCH,
            "Фокус глобального поиска",
            "system",
            "Ctrl/Cmd+F",
            this::focusGlobalSearch,
            () -> ConfigManager.isUxGlobalSearchEnabled(),
            () -> "Глобальный поиск отключен",
            false
        );
        registerPaletteAction(
            NOTES_ACTION_SHORTCUTS_HELP,
            "Показать горячие клавиши",
            "system",
            "",
            this::showShortcutsHelp,
            () -> true,
            () -> "",
            false
        );
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

        registerShortcutBinding("CTRL+K", NOTES_ACTION_OPEN_PALETTE, false, false);
        registerShortcutBinding("CTRL+F", NOTES_ACTION_FOCUS_GLOBAL_SEARCH, false, false);
        registerShortcutBinding("CTRL+Z", "notes.undo", false, false);
        registerShortcutBinding("CTRL+SHIFT+Z", "notes.redo", false, false);
        registerShortcutBinding("CTRL+N", "notes.create", false, false);

        shortcutRegistry.runStartupConflictCheck("smartnotes");
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

    private boolean hasCurrentSelection() {
        return presenter != null && currentNoteTitle != null && !currentNoteTitle.isBlank();
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
            commandActionRegistry.execute(binding.actionId());
            event.consume();
        });
    }

    private boolean isTextInputEvent(KeyEvent event) {
        Object target = event.getTarget();
        return target instanceof TextInputControl;
    }

    private void updateSearchQuery(String query) {
        if (applyingState) {
            return;
        }
        String normalized = query == null ? "" : query.trim();
        if (Objects.equals(searchQuery, normalized)) {
            return;
        }
        searchQuery = normalized;
        if (presenter != null) {
            presenter.onSearchQueryChanged(searchQuery);
        }
        notesList.refresh();
        refreshMarkdownPreview();
    }

    private String normalizeSearchQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private javafx.scene.text.TextFlow buildHighlightedText(String text, String query) {
        javafx.scene.text.TextFlow flow = new javafx.scene.text.TextFlow();
        flow.getStyleClass().add("notes-search-flow");
        if (text == null || text.isEmpty()) {
            return flow;
        }

        String normalizedQuery = normalizeSearchQuery(query);
        if (normalizedQuery.isEmpty()) {
            javafx.scene.text.Text plain = new javafx.scene.text.Text(text);
            plain.getStyleClass().add("notes-search-text");
            flow.getChildren().add(plain);
            return flow;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int index = 0;
        int match;
        while ((match = lower.indexOf(normalizedQuery, index)) >= 0) {
            if (match > index) {
                javafx.scene.text.Text plain = new javafx.scene.text.Text(text.substring(index, match));
                plain.getStyleClass().add("notes-search-text");
                flow.getChildren().add(plain);
            }
            javafx.scene.text.Text hit = new javafx.scene.text.Text(text.substring(match, match + normalizedQuery.length()));
            hit.getStyleClass().add("notes-search-hit");
            flow.getChildren().add(hit);
            index = match + normalizedQuery.length();
        }
        if (index < text.length()) {
            javafx.scene.text.Text plain = new javafx.scene.text.Text(text.substring(index));
            plain.getStyleClass().add("notes-search-text");
            flow.getChildren().add(plain);
        }
        return flow;
    }

    public void openNoteByTitle(String title) {
        if (presenter == null) {
            return;
        }
        clearSearchFilter();
        if (presenter.openNoteByTitle(title)) {
            return;
        }
        if (promptCreateMissingNote(title)) {
            return;
        }
        showInfoAlert("Ссылка на заметку", "Заметка не найдена: " + title);
    }

    private void refreshLinkPanels() {
        outgoingLinksPane.getChildren().clear();
        incomingLinksPane.getChildren().clear();

        if (presenter == null || currentNoteTitle == null || currentNoteTitle.isBlank()) {
            showEmptyLinks();
            return;
        }

        List<SmartNotesPresenter.LinkChipViewModel> outgoingChips = presenter.buildOutgoingLinkChips(contentArea.getText());
        List<SmartNotesPresenter.LinkChipViewModel> incomingChips = presenter.buildIncomingLinkChips(currentNoteTitle);

        if (outgoingChips.isEmpty()) {
            outgoingLinksPane.getChildren().add(outgoingLinksEmpty);
        } else {
            for (SmartNotesPresenter.LinkChipViewModel chip : outgoingChips) {
                outgoingLinksPane.getChildren().add(buildLinkChip(chip));
            }
        }

        if (incomingChips.isEmpty()) {
            incomingLinksPane.getChildren().add(incomingLinksEmpty);
        } else {
            for (SmartNotesPresenter.LinkChipViewModel chip : incomingChips) {
                incomingLinksPane.getChildren().add(buildLinkChip(chip));
            }
        }
    }

    private void updateLinkHighlightOverlay() {
        String text = contentArea.getText();
        if (text == null || text.isEmpty()) {
            linkHighlightFlow.getChildren().clear();
            linkHighlightRects.getChildren().clear();
            linkHighlightNodes.clear();
            lastOverlayText = "";
            return;
        }
        if (!text.contains("[[")) {
            linkHighlightFlow.getChildren().clear();
            linkHighlightRects.getChildren().clear();
            linkHighlightNodes.clear();
            lastOverlayText = text;
            return;
        }
        if (text.equals(lastOverlayText)) {
            return;
        }

        lastOverlayText = text;
        linkHighlightFlow.getChildren().clear();
        linkHighlightRects.getChildren().clear();
        linkHighlightNodes.clear();

        Matcher matcher = LinkParser.WIKI_LINK_PATTERN.matcher(text);
        int lastIndex = 0;
        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                javafx.scene.text.Text plain = new javafx.scene.text.Text(text.substring(lastIndex, matcher.start()));
                plain.getStyleClass().add("notes-overlay-ghost");
                linkHighlightFlow.getChildren().add(plain);
            }
            String match = text.substring(matcher.start(), matcher.end());
            javafx.scene.text.Text highlight = new javafx.scene.text.Text(match);
            highlight.getStyleClass().add("notes-overlay-linktext");
            linkHighlightNodes.add(highlight);
            linkHighlightFlow.getChildren().add(highlight);
            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            javafx.scene.text.Text plain = new javafx.scene.text.Text(text.substring(lastIndex));
            plain.getStyleClass().add("notes-overlay-ghost");
            linkHighlightFlow.getChildren().add(plain);
        }

        Platform.runLater(this::refreshLinkHighlightRects);
    }

    private void bindOverlayToContent() {
        Node contentNode = contentArea.lookup(".content");
        if (contentNode instanceof Region region) {
            if (contentRegion == region) {
                updateOverlayLayout();
                return;
            }
            contentRegion = region;
            contentTextOffsetValid = false;
            contentRegion.layoutBoundsProperty().addListener((obs, old, val) -> updateOverlayLayout());
            contentRegion.boundsInParentProperty().addListener((obs, old, val) -> updateOverlayLayout());
        }
        Platform.runLater(this::updateContentTextOffset);
        updateOverlayLayout();
    }

    private void updateOverlayLayout() {
        double offsetX = 12;
        double offsetY = 10;
        double width = Math.max(0, contentArea.getWidth() - 24);
        if (contentRegion != null) {
            Bounds bounds = contentRegion.getBoundsInParent();
            Insets padding = contentRegion.getPadding();
            offsetX = bounds.getMinX() + padding.getLeft();
            offsetY = bounds.getMinY() + padding.getTop();
            width = Math.max(0, bounds.getWidth() - padding.getLeft() - padding.getRight());
            if (!contentTextOffsetValid) {
                updateContentTextOffset();
            }
        }
        offsetY += contentTextOffsetY;

        double scrollLeft = contentArea.getScrollLeft();
        double scrollTop = contentArea.getScrollTop();
        linkHighlightLayer.setTranslateX(offsetX - scrollLeft);
        linkHighlightLayer.setTranslateY(offsetY - scrollTop);
        linkHighlightLayer.setPrefWidth(width);
        linkHighlightLayer.setMaxWidth(width);
        linkHighlightFlow.setPrefWidth(width);
        linkHighlightFlow.setMaxWidth(width);

        if (Math.abs(width - lastOverlayWidth) > 0.5) {
            lastOverlayWidth = width;
            lastOverlayText = null;
            updateLinkHighlightOverlay();
        }
    }

    private void updateContentTextOffset() {
        if (contentRegion == null) {
            contentTextOffsetY = 0;
            contentTextOffsetValid = false;
            return;
        }

        Insets padding = contentRegion.getPadding();
        Bounds contentBounds = contentRegion.getBoundsInParent();
        double baseY = contentBounds.getMinY() + padding.getTop();
        double minY = Double.POSITIVE_INFINITY;

        for (Node node : contentArea.lookupAll(".text")) {
            if (!(node instanceof javafx.scene.text.Text textNode)) {
                continue;
            }
            Bounds bounds = textNode.getBoundsInParent();
            if (bounds.getHeight() <= 0) {
                continue;
            }
            minY = Math.min(minY, bounds.getMinY());
        }

        if (minY == Double.POSITIVE_INFINITY) {
            contentTextOffsetY = 0;
            contentTextOffsetValid = false;
            return;
        }

        contentTextOffsetY = minY - baseY;
        contentTextOffsetValid = true;
    }

    private void refreshLinkHighlightRects() {
        linkHighlightRects.getChildren().clear();
        if (linkHighlightNodes.isEmpty()) {
            return;
        }

        double ascent = getOverlayAscent();
        linkHighlightFlow.applyCss();
        linkHighlightFlow.layout();

        for (javafx.scene.text.Text highlight : linkHighlightNodes) {
            Bounds bounds = highlight.getBoundsInParent();
            if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
                continue;
            }
            Region rect = new Region();
            rect.getStyleClass().add("notes-overlay-highlight");
            rect.setManaged(false);
            rect.setMouseTransparent(true);
            rect.resizeRelocate(bounds.getMinX(), bounds.getMinY() + ascent, bounds.getWidth() + 3, bounds.getHeight());
            linkHighlightRects.getChildren().add(rect);
        }
    }

    private double getOverlayAscent() {
        javafx.scene.text.Font font = contentArea.getFont();
        if (font == null) {
            return 0;
        }
        javafx.scene.text.Text probe = new javafx.scene.text.Text("Ag");
        probe.setFont(font);
        return probe.getBaselineOffset();
    }

    private void showEmptyLinks() {
        outgoingLinksPane.getChildren().setAll(outgoingLinksEmpty);
        incomingLinksPane.getChildren().setAll(incomingLinksEmpty);
    }

    private Button buildLinkChip(SmartNotesPresenter.LinkChipViewModel chip) {
        Button button = new Button(chip.label());
        button.getStyleClass().add("notes-link-chip");
        if (chip.type() == SmartNotesApplicationService.LinkType.NOTE) {
            button.getStyleClass().add("notes-link-note");
            button.setOnAction(e -> handleNoteLinkClick(chip.target()));
        } else {
            button.getStyleClass().add("notes-link-task");
            button.setOnAction(e -> handleTaskLinkClick(chip.target()));
        }
        if (!chip.exists()) {
            button.getStyleClass().add("notes-link-missing");
        }
        return button;
    }

    private void handleNoteLinkClick(String target) {
        if (presenter == null) {
            return;
        }
        clearSearchFilter();
        String resolved = presenter.resolveExistingNoteTitle(target);
        if (resolved != null) {
            presenter.openNoteByTitle(resolved);
            return;
        }

        if (promptCreateMissingNote(target)) {
            return;
        }
        showInfoAlert("Ссылка на заметку", "Заметка не найдена: " + target);
    }

    private boolean promptCreateMissingNote(String title) {
        if (presenter == null) {
            return false;
        }

        String displayTitle = title == null ? "" : title.trim();
        Alert alert = new Alert(
            Alert.AlertType.CONFIRMATION,
            "Создать заметку: \"" + displayTitle + "\"?",
            ButtonType.YES,
            ButtonType.NO
        );
        alert.setTitle("Создание заметки");
        alert.setHeaderText(null);

        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (ConfigManager.isDarkTheme()) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");

        return alert.showAndWait().filter(ButtonType.YES::equals).map(result -> {
            clearSearchFilter();
            presenter.createAndOpenNote(displayTitle);
            Platform.runLater(titleField::requestFocus);
            return true;
        }).orElse(false);
    }

    private void handleTaskLinkClick(String target) {
        if (presenter == null) {
            return;
        }
        presenter.handleTaskLinkClick(target, root.getScene() != null ? root.getScene().getWindow() : null, ConfigManager.isDarkTheme());
    }

    private void clearSearchFilter() {
        if (!searchField.getText().isEmpty()) {
            searchField.clear();
        }
    }

    private String decodeLinkTarget(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value;
        }
    }

    private void showAiPrompt() {
        if (presenter == null) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Спросить ИИ");
        dialog.setHeaderText("Что сделать с этой заметкой?");
        dialog.setContentText("Запрос (например: 'составь список', 'улучши текст'):");

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (ConfigManager.isDarkTheme()) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");

        dialog.showAndWait().ifPresent(prompt -> {
            String normalized = prompt == null ? "" : prompt.trim();
            if (normalized.isBlank()) {
                return;
            }
            presenter.requestAiForCurrentNote(
                normalized,
                root.getScene() != null ? root.getScene().getWindow() : null,
                ConfigManager.isDarkTheme()
            );
        });
    }

    private void exportCurrentNoteToPdf() {
        if (presenter == null) {
            return;
        }

        String noteTitle = titleField.getText() == null ? "" : titleField.getText().trim();
        if (noteTitle.isEmpty()) {
            noteTitle = currentNoteTitle == null ? "note" : currentNoteTitle;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт заметки в PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(presenter.sanitizeExportFileName(noteTitle, "note") + ".pdf");
        File file = chooser.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) {
            return;
        }

        presenter.exportCurrentNoteToPdf(
            file,
            currentNoteTitle,
            titleField.getText(),
            contentArea.getText(),
            root.getScene() != null ? root.getScene().getWindow() : null,
            ConfigManager.isDarkTheme()
        );
    }

    private void exportAllNotesToPdf() {
        if (presenter == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт всех заметок в PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("notes_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf");
        File file = chooser.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) {
            return;
        }

        presenter.exportAllNotesToPdf(
            file,
            currentNoteTitle,
            titleField.getText(),
            contentArea.getText(),
            root.getScene() != null ? root.getScene().getWindow() : null,
            ConfigManager.isDarkTheme()
        );
    }

    private void refreshMarkdownPreview() {
        if (presenter == null) {
            return;
        }

        String markdown = contentArea.getText();
        if (markdown == null) {
            markdown = "";
        }
        if (markdown.equals(lastPreviewText) && Objects.equals(lastPreviewQuery, searchQuery)) {
            return;
        }

        lastPreviewText = markdown;
        lastPreviewQuery = searchQuery;
        String html = presenter.renderPreviewHtml(markdown, searchQuery, ConfigManager.isDarkTheme());
        markdownPreview.getEngine().loadContent(html);
    }

    private void applyTheme() {
        lastPreviewText = null;
        lastPreviewQuery = null;
        lastOverlayText = null;
        refreshMarkdownPreview();
        updateLinkHighlightOverlay();
        applyAdaptiveLayoutForWidth(root.getScene() != null ? root.getScene().getWidth() : root.getWidth());
    }

    public void refreshTheme() {
        applyTheme();
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return () -> {
            saveCurrentNote();
            if (closeAction != null) {
                closeAction.run();
            }
        };
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Умные заметки";
    }

    private ContextMenu createRussianContextMenu(TextInputControl field) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("russian-context-menu");

        MenuItem undoItem = new MenuItem("Отменить");
        undoItem.setOnAction(e -> field.undo());
        undoItem.getStyleClass().add("context-menu-item");

        MenuItem redoItem = new MenuItem("Повторить");
        redoItem.setOnAction(e -> field.redo());
        redoItem.getStyleClass().add("context-menu-item");

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem cutItem = new MenuItem("Вырезать");
        cutItem.setOnAction(e -> field.cut());
        cutItem.getStyleClass().add("context-menu-item");

        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(e -> field.copy());
        copyItem.getStyleClass().add("context-menu-item");

        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(e -> field.paste());
        pasteItem.getStyleClass().add("context-menu-item");

        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(e -> field.replaceSelection(""));
        deleteItem.getStyleClass().add("context-menu-item");

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        MenuItem selectAllItem = new MenuItem("Выделить всё");
        selectAllItem.setOnAction(e -> field.selectAll());
        selectAllItem.getStyleClass().add("context-menu-item");

        menu.getItems().addAll(undoItem, redoItem, sep1, cutItem, copyItem, pasteItem, deleteItem, sep2, selectAllItem);

        menu.setOnShowing(e -> {
            boolean hasSelection = field.getSelection().getLength() > 0;
            boolean hasText = !field.getText().isEmpty();
            boolean canPaste = Clipboard.getSystemClipboard().hasContent(DataFormat.PLAIN_TEXT);

            undoItem.setDisable(!field.isUndoable());
            redoItem.setDisable(!field.isRedoable());
            cutItem.setDisable(!hasSelection);
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!canPaste);
            deleteItem.setDisable(!hasSelection);
            selectAllItem.setDisable(!hasText);
        });

        return menu;
    }

    private void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (ConfigManager.isDarkTheme()) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        if (root.getScene() != null) {
            alert.initOwner(root.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private enum NoteTemplate {
        DIARY,
        RETROSPECTIVE,
        PLANS,
        MEETING,
        ONE_ON_ONE,
        STATUS_REPORT,
        POSTMORTEM,
        SPEC,
        IDEAS,
        LEARNING_PLAN,
        WEEKLY_REFLECTION,
        PROJECT_PLAN,
        SHOPPING,
        RESEARCH,
        HABITS,
        REVIEW,
        TRAVEL,
        OKR
    }
}
