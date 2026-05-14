package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.ai.*;
import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportConflictPolicy;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportOptions;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportPreview;
import com.example.neuroflowplanner.model.chatio.ChatArchiveImportResult;
import com.example.neuroflowplanner.service.chatio.ChatArchiveExportService;
import com.example.neuroflowplanner.service.chatio.ChatArchiveFormat;
import com.example.neuroflowplanner.service.chatio.ChatArchiveImportService;
import com.example.neuroflowplanner.service.chatio.ChatArchiveImportValidationException;
import com.example.neuroflowplanner.service.chatio.DefaultChatArchiveExportService;
import com.example.neuroflowplanner.service.chatio.DefaultChatArchiveImportService;
import com.example.neuroflowplanner.service.imagecapability.ImageConfigResolution;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapability;
import com.example.neuroflowplanner.service.imagecapability.ImageValidatedOptions;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteController;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteDialog;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.ui.layout.AdaptiveLayoutService;
import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.ImageGenConfigDefaults;
import com.example.neuroflowplanner.util.SensitiveDataRedactor;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Inline settings view with unified AI mode section.
 */
public class SettingsDialog implements InlineView {
    private static final ExecutorService CHAT_ARCHIVE_EXECUTOR =
        Executors.newSingleThreadExecutor(AsyncContext.namedThreadFactory("settings-chat-archive", true));

    private static final String SETTINGS_CLASS_ADAPTIVE_ROOT = "adaptive-shell-root";
    private static final String LAYOUT_CLASS_BREAKPOINT_COMPACT = "layout-breakpoint-compact";
    private static final String LAYOUT_CLASS_BREAKPOINT_NORMAL = "layout-breakpoint-normal";
    private static final String LAYOUT_CLASS_BREAKPOINT_WIDE = "layout-breakpoint-wide";
    private static final String LAYOUT_CLASS_DENSITY_COMPACT = "layout-density-compact";
    private static final String LAYOUT_CLASS_DENSITY_COMFORTABLE = "layout-density-comfortable";
    private static final String SETTINGS_ACTION_PALETTE_PREFIX = "settings.section.";

    private static boolean isDarkTheme = ConfigManager.isDarkTheme();
    private static Scene mainScene = null;
    private static Runnable themeChangeCallback = null;

    private final VBox root;
    private final HBox themeToggle;
    private final StackPane toggleSwitch;
    private final Label themeLabel;
    private final Label themeDesc;
    private final FontIcon themeIcon;
    private final AdaptiveLayoutService adaptiveLayoutService = new AdaptiveLayoutService();
    private UiLayoutBreakpoint adaptiveBreakpoint = UiLayoutBreakpoint.NORMAL;
    private UiLayoutMode adaptiveDensityMode = UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode());
    private Scene adaptiveObservedScene;
    private final javafx.beans.value.ChangeListener<Number> adaptiveSceneWidthListener =
        (obs, oldWidth, newWidth) -> applyAdaptiveLayoutForWidth(newWidth == null ? 0.0 : newWidth.doubleValue());
    private Runnable closeAction;
    private final UiActionRegistry settingsActionRegistry = UiActionRegistry.withConfigDefaults();
    private CommandPaletteController settingsCommandPaletteController;
    private CommandPaletteDialog settingsCommandPaletteDialog;
    private final Map<String, Node> sectionContentById = new LinkedHashMap<>();
    private final List<String> sectionOrder = new ArrayList<>();
    private ListView<SettingsNavEntry> settingsNavList;
    private VBox settingsNavigationPane;
    private HBox settingsBody;

    // AI Mode settings
    private ComboBox<String> aiModeCombo;
    private VBox offlineModeContent;
    private VBox localModeContent;
    private VBox externalModeContent;
    private StackPane modeContentContainer;

    // Local Ollama fields
    private TextField localUrlField;
    private ComboBox<String> localModelCombo;
    private Label localStatusLabel;
    private FontIcon localStatusIcon;

    // External API fields
    private TextField externalUrlField;
    private PasswordField externalKeyField;
    private TextField externalModelField;
    private Label externalStatusLabel;
    private FontIcon externalStatusIcon;
    private String externalMaskedKeyValue = "";
    private final List<String> externalCustomModels = new ArrayList<>();
    private final List<String> externalDiscoveredModels = new ArrayList<>();
    private final List<String> externalMultimodalModels = new ArrayList<>();
    private final List<String> externalAudioInputModels = new ArrayList<>();
    private final List<String> externalFileInputModels = new ArrayList<>();
    private final List<AiDiscoveredModelInfo> externalModelCatalog = new ArrayList<>();

    // Assistant style settings
    private ComboBox<String> assistantDetailCombo;
    private ComboBox<String> assistantToneCombo;
    private ComboBox<String> assistantReasoningCombo;
    private ComboBox<String> assistantReasoningSummaryCombo;
    private TextField assistantReasoningMaxTokensField;
    private CheckBox assistantReasoningExcludeCheck;
    private CheckBox assistantPromptCachingCheck;
    private VBox assistantTextParametersSection;
    private Label assistantTextParametersHintLabel;
    private Slider assistantTextMaxTokensSlider;
    private Label assistantTextMaxTokensValueLabel;
    private Slider assistantTextTemperatureSlider;
    private Label assistantTextTemperatureValueLabel;
    private Slider assistantTextTopPSlider;
    private Label assistantTextTopPValueLabel;
    private Slider assistantTextFrequencyPenaltySlider;
    private Label assistantTextFrequencyPenaltyValueLabel;
    private Slider assistantTextPresencePenaltySlider;
    private Label assistantTextPresencePenaltyValueLabel;
    private VBox assistantPluginsSection;
    private CheckBox pluginWebEnabledCheck;
    private ComboBox<String> pluginWebEngineCombo;
    private TextField pluginWebMaxResultsField;
    private TextField pluginWebSearchPromptField;
    private CheckBox pluginFileParserEnabledCheck;
    private ComboBox<String> pluginFileParserPdfEngineCombo;
    private CheckBox pluginResponseHealingEnabledCheck;
    private Label pluginCompatibilityHintLabel;
    private MenuButton assistantExportChatsMenuButton;
    private Button assistantImportChatsButton;
    private boolean assistantChatArchiveBusy = false;
    private boolean suppressAssistantTextParameterSync = false;

    // Image generation settings (only for external mode)
    private VBox imageGenSection;
    private TextField imageModelField;
    private ComboBox<String> imageRatioCombo;
    private ComboBox<String> imageResolutionCombo;
    private ComboBox<String> imageQualityCombo;
    private ComboBox<String> imageOutputFormatCombo;
    private TextField imageStrengthField;
    private TextField imageGuidanceScaleField;
    private Label imageRatioLabel;
    private Label imageResolutionLabel;
    private Label imageQualityLabel;
    private Label imageOutputFormatLabel;
    private Label imageStrengthLabel;
    private Label imageGuidanceScaleLabel;
    private FlowPane imageCapabilityFlow;
    private Label imageCapabilityHintLabel;
    private Label imageGenStatusLabel;
    private FontIcon imageGenStatusIcon;
    private final List<String> imageCustomModels = new ArrayList<>();
    private final List<String> imageDiscoveredModels = new ArrayList<>();

    private final ChatArchiveExportService chatArchiveExportService = new DefaultChatArchiveExportService();
    private final ChatArchiveImportService chatArchiveImportService = new DefaultChatArchiveImportService();
    private final CloudSyncSettingsSection cloudSyncSettingsSection;

    private record SettingsNavEntry(String id, String title, String subtitle, String actionId) {
    }

    private SettingsDialog() {
        root = new VBox(0);
        root.setMinSize(320, 300);
        root.getStyleClass().addAll("settings-root", SETTINGS_CLASS_ADAPTIVE_ROOT);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().addAll("settings-header-panel", "adaptive-toolbar");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("settings-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignC.COG, 22);
        icon.getStyleClass().add("settings-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Настройки");
        title.getStyleClass().add("settings-title");
        Label subtitle = new Label("Параметры приложения");
        subtitle.getStyleClass().add("settings-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button paletteBtn = new Button();
        FontIcon paletteIcon = FontIcon.of(MaterialDesignM.MAGNIFY, 18);
        paletteBtn.setGraphic(paletteIcon);
        paletteBtn.getStyleClass().add("settings-palette-btn");
        paletteBtn.setTooltip(new Tooltip("Открыть через палитру (Ctrl/Cmd+K)"));
        paletteBtn.setOnAction(e -> openSettingsCommandPalette());

        Button closeBtn = new Button();
        FontIcon closeIcon = FontIcon.of(MaterialDesignC.CLOSE, 20);
        closeBtn.setGraphic(closeIcon);
        closeBtn.getStyleClass().add("settings-close-btn");
        closeBtn.setOnAction(e -> {
            if (closeAction != null) closeAction.run();
        });

        header.getChildren().addAll(iconPane, titleBox, spacer, paletteBtn, closeBtn);

        themeToggle = new HBox(15);
        toggleSwitch = new StackPane();
        themeLabel = new Label();
        themeDesc = new Label();
        themeIcon = new FontIcon();
        externalCustomModels.addAll(ConfigManager.getExternalApiCustomModels());
        externalDiscoveredModels.addAll(ConfigManager.getExternalApiDiscoveredModels());
        externalMultimodalModels.addAll(ConfigManager.getExternalApiMultimodalModels());
        externalAudioInputModels.addAll(ConfigManager.getExternalApiAudioInputModels());
        externalFileInputModels.addAll(ConfigManager.getExternalApiFileInputModels());
        externalModelCatalog.addAll(ConfigManager.getExternalApiModelCatalog());
        imageCustomModels.addAll(ConfigManager.getExternalImageCustomModels());
        imageDiscoveredModels.addAll(ConfigManager.getExternalImageDiscoveredModels());

        VBox themeSection = createThemeSection();
        VBox aiModeSection = createAiModeSection();
        VBox assistantStyleSection = createAssistantStyleSection();
        imageGenSection = createImageGenSection();
        cloudSyncSettingsSection = new CloudSyncSettingsSection(
            () -> root.getScene() != null ? root.getScene().getWindow() : null,
            () -> isDarkTheme
        );
        
        // Only show image gen section in external mode
        updateImageGenSectionVisibility();

        StackPane contentPane = createSettingsContentPane(
            themeSection,
            aiModeSection,
            assistantStyleSection,
            imageGenSection,
            cloudSyncSettingsSection.getContent()
        );
        VBox navigationPane = createSettingsNavigationPane();

        settingsBody = new HBox(16, navigationPane, contentPane);
        settingsBody.getStyleClass().add("settings-body");
        settingsBody.setPadding(new Insets(8, 16, 16, 16));
        HBox.setHgrow(contentPane, Priority.ALWAYS);
        VBox.setVgrow(settingsBody, Priority.ALWAYS);

        root.getChildren().addAll(header, settingsBody);

        initializeSettingsCommandPalette();
        initializeAdaptiveLayout();
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleSettingsShortcuts);

        applyLocalStyles();
        updateThemeUI();
    }

    public static InlineView inline() {
        return new SettingsDialog();
    }

    public static void setMainScene(Scene scene) {
        mainScene = scene;
    }

    public static void setDarkThemeState(boolean isDark) {
        isDarkTheme = isDark;
    }

    public static boolean isDarkTheme() {
        return isDarkTheme;
    }

    public static void setThemeChangeCallback(Runnable callback) {
        themeChangeCallback = callback;
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
        this.closeAction = () -> {
            cloudSyncSettingsSection.close();
            if (closeAction != null) {
                closeAction.run();
            }
        };
    }

    @Override
    public String getTitle() {
        return "Настройки";
    }

    private StackPane createSettingsContentPane(
        VBox themeSection,
        VBox aiModeSection,
        VBox assistantStyleSection,
        VBox imageSection,
        VBox cloudSyncSection
    ) {
        sectionContentById.clear();
        sectionOrder.clear();

        registerSection("appearance", wrapSettingsSection(themeSection));
        registerSection("ai-mode", wrapSettingsSection(aiModeSection));
        registerSection("assistant", wrapSettingsSection(assistantStyleSection));
        registerSection("image", wrapSettingsSection(imageSection));
        registerSection("cloud-sync", wrapSettingsSection(cloudSyncSection));

        StackPane contentPane = new StackPane();
        contentPane.getStyleClass().add("settings-content-pane");
        contentPane.getChildren().addAll(sectionContentById.values());
        if (!sectionOrder.isEmpty()) {
            showSection(sectionOrder.get(0));
        }
        return contentPane;
    }

    private Node wrapSettingsSection(VBox sectionContent) {
        if (sectionContent != null && !sectionContent.getStyleClass().contains("settings-section-content")) {
            sectionContent.getStyleClass().add("settings-section-content");
        }
        ScrollPane sectionScroll = new ScrollPane(sectionContent);
        sectionScroll.setFitToWidth(true);
        sectionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sectionScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sectionScroll.setPannable(true);
        sectionScroll.getStyleClass().addAll("settings-scroll", "settings-section-scroll");
        return sectionScroll;
    }

    private void registerSection(String sectionId, Node contentNode) {
        String normalized = normalizeSectionId(sectionId);
        if (normalized == null || contentNode == null) {
            return;
        }
        sectionContentById.put(normalized, contentNode);
        sectionOrder.add(normalized);
    }

    private VBox createSettingsNavigationPane() {
        settingsNavigationPane = new VBox(10);
        settingsNavigationPane.getStyleClass().add("settings-nav-panel");

        Label navTitle = new Label("Разделы");
        navTitle.getStyleClass().add("settings-nav-title");

        settingsNavList = new ListView<>();
        settingsNavList.getStyleClass().add("settings-nav-list");
        settingsNavList.getItems().setAll(buildSettingsNavEntries());
        settingsNavList.setCellFactory(listView -> new ListCell<>() {
            private final Label title = new Label();
            private final Label subtitle = new Label();
            private final VBox textBox = new VBox(2, title, subtitle);
            private final MenuItem openViaPaletteItem = new MenuItem("Открыть через палитру");
            private final MenuItem showInPanelItem = new MenuItem("Показать в панели");
            private final ContextMenu contextMenu = new ContextMenu(openViaPaletteItem, showInPanelItem);

            {
                textBox.getStyleClass().add("settings-nav-row");
                title.getStyleClass().add("settings-nav-row-title");
                subtitle.getStyleClass().add("settings-nav-row-subtitle");
                openViaPaletteItem.setOnAction(e -> {
                    SettingsNavEntry entry = getItem();
                    if (entry != null) {
                        openSettingsCommandPaletteForAction(entry.actionId());
                    }
                });
                showInPanelItem.setOnAction(e -> {
                    SettingsNavEntry entry = getItem();
                    if (entry != null) {
                        showSectionInPanelByAction(entry.actionId());
                    }
                });
            }

            @Override
            protected void updateItem(SettingsNavEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }
                title.setText(item.title());
                subtitle.setText(item.subtitle());
                openViaPaletteItem.setDisable(false);
                setGraphic(textBox);
                setContextMenu(contextMenu);
            }
        });
        settingsNavList.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> {
            if (newEntry != null) {
                showSection(newEntry.id());
            }
        });
        if (!settingsNavList.getItems().isEmpty()) {
            settingsNavList.getSelectionModel().select(0);
        }
        VBox.setVgrow(settingsNavList, Priority.ALWAYS);

        settingsNavigationPane.getChildren().addAll(navTitle, settingsNavList);
        return settingsNavigationPane;
    }

    private List<SettingsNavEntry> buildSettingsNavEntries() {
        List<SettingsNavEntry> entries = new ArrayList<>();
        for (String sectionId : sectionOrder) {
            String title = switch (sectionId) {
                case "appearance" -> "Оформление";
                case "ai-mode" -> "ИИ и провайдер";
                case "assistant" -> "Ассистент";
                case "image" -> "Изображения";
                case "cloud-sync" -> "Облачная синхронизация";
                default -> sectionId;
            };
            String subtitle = switch (sectionId) {
                case "appearance" -> "Тема и плотность интерфейса";
                case "ai-mode" -> "Offline, Ollama, external API";
                case "assistant" -> "Тон, подробность, reasoning и архив переписок";
                case "image" -> "Модель, ratio, resolution";
                case "cloud-sync" -> "Аккаунт, первое связывание, статус и ручная синхронизация";
                default -> "";
            };
            entries.add(new SettingsNavEntry(
                sectionId,
                title,
                subtitle,
                SETTINGS_ACTION_PALETTE_PREFIX + sectionId
            ));
        }
        return entries;
    }

    private void showSection(String sectionId) {
        String normalized = normalizeSectionId(sectionId);
        if (normalized == null || !sectionContentById.containsKey(normalized)) {
            return;
        }
        sectionContentById.forEach((id, node) -> {
            boolean selected = id.equals(normalized);
            node.setVisible(selected);
            node.setManaged(selected);
        });
    }

    private boolean showSectionInPanelByAction(String actionId) {
        String sectionId = sectionIdFromAction(actionId);
        if (sectionId == null || !sectionContentById.containsKey(sectionId)) {
            return false;
        }
        showSection(sectionId);
        if (settingsNavList != null) {
            for (int i = 0; i < settingsNavList.getItems().size(); i++) {
                SettingsNavEntry entry = settingsNavList.getItems().get(i);
                if (sectionId.equals(entry.id())) {
                    settingsNavList.getSelectionModel().select(i);
                    settingsNavList.scrollTo(i);
                    settingsNavList.requestFocus();
                    break;
                }
            }
        }
        return true;
    }

    private void openSettingsCommandPalette() {
        if (settingsCommandPaletteDialog == null) {
            return;
        }
        settingsCommandPaletteDialog.toggle(root.getScene() != null ? root.getScene().getWindow() : null);
    }

    private void openSettingsCommandPaletteForAction(String actionId) {
        if (settingsCommandPaletteDialog == null) {
            return;
        }
        String query = actionId == null ? "" : actionId.trim();
        settingsCommandPaletteDialog.open(root.getScene() != null ? root.getScene().getWindow() : null, query);
    }

    private void initializeSettingsCommandPalette() {
        settingsCommandPaletteController = new CommandPaletteController(
            "settings",
            settingsActionRegistry,
            (query, limit) -> List.of(),
            result -> false,
            this::showSectionInPanelByAction
        );
        settingsCommandPaletteDialog = new CommandPaletteDialog(
            "Командная палитра: настройки",
            settingsCommandPaletteController
        );
        settingsCommandPaletteDialog.setSidebarRevealHandler(this::showSectionInPanelByAction);
        registerSettingsPaletteActions();
    }

    private void registerSettingsPaletteActions() {
        for (SettingsNavEntry entry : buildSettingsNavEntries()) {
            String actionId = entry.actionId();
            settingsActionRegistry.register(new UiActionRegistry.RegisteredAction(
                actionId,
                "Настройки: " + entry.title(),
                "settings",
                "",
                () -> nonUndoableSettingsAction(actionId, () -> showSectionInPanelByAction(actionId)),
                () -> true,
                () -> "",
                false
            ));
        }
    }

    private UserActionCommand nonUndoableSettingsAction(String actionId, Runnable handler) {
        return new UserActionCommand() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String label() {
                return actionId;
            }

            @Override
            public String category() {
                return "settings";
            }

            @Override
            public boolean canExecute() {
                return true;
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

    private void handleSettingsShortcuts(javafx.scene.input.KeyEvent event) {
        if (event == null || event.isConsumed()) {
            return;
        }
        boolean commandModifier = event.isControlDown() || event.isMetaDown();
        if (!commandModifier) {
            return;
        }
        switch (event.getCode()) {
            case K -> {
                openSettingsCommandPalette();
                event.consume();
            }
            case W -> {
                if (closeAction != null) {
                    closeAction.run();
                    event.consume();
                }
            }
            default -> {
                // No-op.
            }
        }
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
        if (settingsNavigationPane == null || settingsBody == null) {
            return;
        }
        double navWidth = switch (adaptiveBreakpoint) {
            case COMPACT -> 176.0;
            case NORMAL -> 220.0;
            case WIDE -> 248.0;
        };
        settingsNavigationPane.setMinWidth(navWidth);
        settingsNavigationPane.setPrefWidth(navWidth);
        settingsNavigationPane.setMaxWidth(navWidth);
    }

    private String sectionIdFromAction(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return null;
        }
        String normalized = actionId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(SETTINGS_ACTION_PALETTE_PREFIX)) {
            return null;
        }
        String sectionId = normalized.substring(SETTINGS_ACTION_PALETTE_PREFIX.length());
        return normalizeSectionId(sectionId);
    }

    private String normalizeSectionId(String sectionId) {
        if (sectionId == null || sectionId.isBlank()) {
            return null;
        }
        return sectionId.trim().toLowerCase(Locale.ROOT);
    }

    private VBox createThemeSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Внешний вид");
        sectionTitle.getStyleClass().add("settings-section-title");

        themeToggle.setAlignment(Pos.CENTER_LEFT);
        themeToggle.getStyleClass().add("settings-toggle-container");

        themeIcon.setIconCode(isDarkTheme ? MaterialDesignW.WEATHER_NIGHT : MaterialDesignW.WEATHER_SUNNY);
        themeIcon.setIconSize(24);
        themeIcon.getStyleClass().add("settings-theme-icon");

        VBox textBox = new VBox(4);
        themeLabel.setText(isDarkTheme ? "Темная тема" : "Светлая тема");
        themeLabel.getStyleClass().add("settings-toggle-label");

        themeDesc.setText("Переключение между светлой и темной темой");
        themeDesc.getStyleClass().add("settings-toggle-desc");
        textBox.getChildren().addAll(themeLabel, themeDesc);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        setupToggleSwitch();

        themeToggle.getChildren().addAll(themeIcon, textBox, spacer, toggleSwitch);

        section.getChildren().addAll(sectionTitle, themeToggle);
        return section;
    }

    private VBox createAiModeSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Режим ИИ");
        sectionTitle.getStyleClass().add("settings-section-title");

        // Mode selector
        Label modeLabel = new Label("Активный режим:");
        modeLabel.getStyleClass().add("settings-field-label");

        aiModeCombo = new ComboBox<>();
        for (AiMode mode : AiMode.values()) {
            aiModeCombo.getItems().add(mode.getDisplayName());
        }
        aiModeCombo.getStyleClass().add("ai-combo-box");
        aiModeCombo.setMaxWidth(Double.MAX_VALUE);

        // Set current mode
        AiMode currentMode = AiClientFactory.getInstance().getCurrentMode();
        aiModeCombo.setValue(currentMode.getDisplayName());

        // Create mode-specific content panels
        createModeContentPanels();

        modeContentContainer = new StackPane();
        modeContentContainer.getChildren().addAll(offlineModeContent, localModeContent, externalModeContent);
        showModeContent(currentMode);

        aiModeCombo.setOnAction(e -> {
            AiMode selectedMode = getModeFromDisplayName(aiModeCombo.getValue());
            showModeContent(selectedMode);
            updateImageGenSectionVisibility();
        });

        section.getChildren().addAll(sectionTitle, modeLabel, aiModeCombo, modeContentContainer);
        return section;
    }

    private void createModeContentPanels() {
        // Offline content
        offlineModeContent = new VBox(8);
        offlineModeContent.getStyleClass().add("settings-mode-content");
        offlineModeContent.setPadding(new Insets(10, 0, 0, 0));

        Label offlineInfo = new Label("ИИ-функции отключены. Используются заглушки для демонстрации.");
        offlineInfo.setWrapText(true);
        offlineInfo.getStyleClass().add("settings-muted-text");

        Button applyOfflineBtn = new Button("Применить");
        applyOfflineBtn.getStyleClass().add("settings-save-btn");
        applyOfflineBtn.setOnAction(e -> applyMode(AiMode.OFFLINE));

        offlineModeContent.getChildren().addAll(offlineInfo, applyOfflineBtn);

        // Local Ollama content
        localModeContent = createLocalModeContent();

        // External API content
        externalModeContent = createExternalModeContent();
    }

    private VBox createLocalModeContent() {
        VBox content = new VBox(10);
        content.getStyleClass().add("settings-mode-content");
        content.setPadding(new Insets(10, 0, 0, 0));

        Label urlLabel = new Label("Адрес сервера Ollama:");
        urlLabel.getStyleClass().add("settings-field-label");

        localUrlField = new TextField();
        localUrlField.setPromptText("http://localhost:11434");
        localUrlField.getStyleClass().add("settings-text-field");

        String currentUrl = ConfigManager.getProperty(LocalOllamaClient.CONFIG_BASE_URL);
        localUrlField.setText(currentUrl != null ? currentUrl : LocalOllamaClient.DEFAULT_BASE_URL);

        Label modelLabel = new Label("Модель:");
        modelLabel.getStyleClass().add("settings-field-label");

        localModelCombo = new ComboBox<>();
        localModelCombo.setEditable(true);
        localModelCombo.setPromptText("Выберите или введите модель");
        localModelCombo.getStyleClass().add("ai-combo-box");
        localModelCombo.setMaxWidth(Double.MAX_VALUE);

        String currentModel = ConfigManager.getProperty(LocalOllamaClient.CONFIG_MODEL);
        if (currentModel != null && !currentModel.isBlank()) {
            localModelCombo.getItems().add(currentModel);
            localModelCombo.setValue(currentModel);
        }

        // Status
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        localStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        localStatusIcon.getStyleClass().add("settings-status-icon-neutral");

        localStatusLabel = new Label("Проверьте соединение перед применением");
        localStatusLabel.getStyleClass().add("settings-status-text");

        statusBox.getChildren().addAll(localStatusIcon, localStatusLabel);

        // Buttons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button checkBtn = new Button("Проверить");
        checkBtn.getStyleClass().add("settings-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 14);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);

        Button applyBtn = new Button("Применить");
        applyBtn.getStyleClass().add("settings-save-btn");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        btnBox.getChildren().addAll(checkBtn, applyBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = localUrlField.getText().trim();
            if (url.isEmpty()) {
                updateLocalStatus("error", "Введите адрес сервера");
                return;
            }

            checkBtn.setDisable(true);
            applyBtn.setDisable(true);
            progress.setVisible(true);
            updateLocalStatus("checking", "Проверка соединения...");

            LocalOllamaClient testClient = AiClientFactory.getInstance()
                    .createTestOllamaClient(url, null);

            testClient.testConnection().thenAccept(result -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                applyBtn.setDisable(false);
                progress.setVisible(false);

                if (result.success()) {
                    updateLocalStatus("success", result.message());
                    if (result.hasModels()) {
                        String currentValue = localModelCombo.getValue();
                        localModelCombo.getItems().clear();
                        localModelCombo.getItems().addAll(result.availableModels());
                        if (currentValue != null && result.availableModels().contains(currentValue)) {
                            localModelCombo.setValue(currentValue);
                        } else if (!result.availableModels().isEmpty()) {
                            localModelCombo.setValue(result.availableModels().get(0));
                        }
                    }
                } else {
                    updateLocalStatus("error", result.message());
                }
            }));
        });

        applyBtn.setOnAction(e -> {
            String url = localUrlField.getText().trim();
            String model = getComboValue(localModelCombo);

            if (url.isEmpty()) {
                updateLocalStatus("error", "Введите адрес сервера");
                return;
            }

            ConfigManager.setProperty(LocalOllamaClient.CONFIG_BASE_URL, url);
            if (model != null && !model.isBlank()) {
                ConfigManager.setProperty(LocalOllamaClient.CONFIG_MODEL, model);
            }
            applyMode(AiMode.LOCAL_OLLAMA);
            updateLocalStatus("success", "Применено!");
        });

        content.getChildren().addAll(urlLabel, localUrlField, modelLabel, localModelCombo, btnBox, statusBox);
        return content;
    }

    private VBox createExternalModeContent() {
        VBox content = new VBox(10);
        content.getStyleClass().add("settings-mode-content");
        content.setPadding(new Insets(10, 0, 0, 0));

        Label apiNote = new Label("Работает только с OpenAI-совместимыми API");
        apiNote.getStyleClass().add("settings-muted-text");
        apiNote.setStyle("-fx-font-style: italic;");

        Label urlLabel = new Label("Базовый URL API:");
        urlLabel.getStyleClass().add("settings-field-label");

        externalUrlField = new TextField();
        externalUrlField.setPromptText("https://api.openai.com/v1");
        externalUrlField.getStyleClass().add("settings-text-field");
        externalUrlField.textProperty().addListener((obs, oldValue, newValue) -> updatePluginCompatibilityHint());

        String currentUrl = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_BASE_URL);
        externalUrlField.setText(currentUrl != null ? currentUrl : ExternalOpenAiClient.DEFAULT_BASE_URL);

        Label keyLabel = new Label("API ключ:");
        keyLabel.getStyleClass().add("settings-field-label");

        externalKeyField = new PasswordField();
        externalKeyField.setPromptText("sk-...");
        externalKeyField.getStyleClass().add("settings-text-field");

        String currentKey = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_API_KEY);
        setExternalKeyMask(currentKey);

        Label modelLabel = new Label("ID модели:");
        modelLabel.getStyleClass().add("settings-field-label");

        externalModelField = new TextField();
        externalModelField.setPromptText("Например: openai/gpt-5.4");
        externalModelField.getStyleClass().add("settings-text-field");
        externalModelField.textProperty().addListener((obs, oldValue, newValue) -> refreshAssistantTextParameterControlsState());
        String currentModel = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_MODEL);
        if (currentModel != null && !currentModel.isBlank()) {
            externalModelField.setText(currentModel);
        }

        Button manageModelsBtn = new Button("Каталог моделей");
        manageModelsBtn.getStyleClass().add("settings-action-btn");
        manageModelsBtn.setGraphic(FontIcon.of(MaterialDesignC.CUBE_SCAN, 16));
        manageModelsBtn.setMaxWidth(Double.MAX_VALUE);
        manageModelsBtn.setOnAction(e -> openExternalModelManagementDialog());

        // Status
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        externalStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        externalStatusIcon.getStyleClass().add("settings-status-icon-neutral");

        externalStatusLabel = new Label("Проверьте соединение перед применением");
        externalStatusLabel.getStyleClass().add("settings-status-text");

        statusBox.getChildren().addAll(externalStatusIcon, externalStatusLabel);

        // Buttons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button checkBtn = new Button("Проверить");
        checkBtn.getStyleClass().add("settings-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 14);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);

        Button applyBtn = new Button("Применить");
        applyBtn.getStyleClass().add("settings-save-btn");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        btnBox.getChildren().addAll(checkBtn, applyBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = externalUrlField.getText().trim();
            String rawKeyInput = externalKeyField.getText().trim();
            String key = resolveExternalKeyInput(rawKeyInput);

            if (url.isEmpty()) {
                updateExternalStatus("error", "Введите URL API");
                return;
            }
            if (key.isEmpty()) {
                updateExternalStatus("error", "Введите API ключ");
                return;
            }

            checkBtn.setDisable(true);
            applyBtn.setDisable(true);
            progress.setVisible(true);
            updateExternalStatus("checking", "Проверка соединения...");

            ExternalOpenAiClient testClient = AiClientFactory.getInstance()
                    .createTestExternalClient(url, key, null);

            testClient.testConnection().thenAccept(result -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                applyBtn.setDisable(false);
                progress.setVisible(false);

                if (result.success()) {
                    updateExternalStatus("success", result.message());
                    updateExternalDiscoveredModels(
                            result.hasModels() ? result.availableModels() : List.of(),
                            result.hasMultimodalModels() ? result.multimodalModels() : List.of(),
                            result.hasAudioInputModels() ? result.audioInputModels() : List.of(),
                            result.hasFileInputModels() ? result.fileInputModels() : List.of(),
                            result.hasModelCatalog() ? result.modelCatalog() : List.of());
                } else {
                    updateExternalStatus("error", result.message());
                }
            }));
        });

        applyBtn.setOnAction(e -> {
            String url = externalUrlField.getText().trim();
            String rawKeyInput = externalKeyField.getText().trim();
            String key = resolveExternalKeyInput(rawKeyInput);
            String model = normalizeExternalModelFieldValue();

            if (url.isEmpty()) {
                updateExternalStatus("error", "Введите URL API");
                return;
            }
            if (key.isEmpty()) {
                updateExternalStatus("error", "Введите API ключ");
                return;
            }
            if (model.isBlank()) {
                updateExternalStatus("error", "Введите ID модели");
                return;
            }

            ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_BASE_URL, url);
            if (!isMaskedExternalKeyInput(rawKeyInput)) {
                ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_API_KEY, key);
                setExternalKeyMask(key);
            }
            rememberExternalCustomModel(model);
            ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_MODEL, model);
            ConfigManager.setExternalApiCustomModels(externalCustomModels);
            ConfigManager.setExternalApiDiscoveredModels(externalDiscoveredModels);
            ConfigManager.setExternalApiMultimodalModels(externalMultimodalModels);
            ConfigManager.setExternalApiAudioInputModels(externalAudioInputModels);
            ConfigManager.setExternalApiFileInputModels(externalFileInputModels);
            ConfigManager.setExternalApiModelCatalog(externalModelCatalog);
            applyMode(AiMode.EXTERNAL_OPENAI);
            updateExternalStatus("success", "Применено!");
        });

        content.getChildren().addAll(apiNote, urlLabel, externalUrlField, keyLabel, externalKeyField,
                modelLabel, externalModelField, manageModelsBtn, btnBox, statusBox);
        return content;
    }

    private void showModeContent(AiMode mode) {
        offlineModeContent.setVisible(mode == AiMode.OFFLINE);
        offlineModeContent.setManaged(mode == AiMode.OFFLINE);

        localModeContent.setVisible(mode == AiMode.LOCAL_OLLAMA);
        localModeContent.setManaged(mode == AiMode.LOCAL_OLLAMA);

        externalModeContent.setVisible(mode == AiMode.EXTERNAL_OPENAI);
        externalModeContent.setManaged(mode == AiMode.EXTERNAL_OPENAI);
    }

    private void applyMode(AiMode mode) {
        AiClientFactory.getInstance().switchToMode(mode);
        updateImageGenSectionVisibility();
    }

    private void updateImageGenSectionVisibility() {
        AiMode selectedMode = getModeFromDisplayName(aiModeCombo.getValue());
        boolean visible = selectedMode == AiMode.EXTERNAL_OPENAI;
        imageGenSection.setVisible(visible);
        imageGenSection.setManaged(visible);
        updateAssistantPluginSectionVisibility();
        refreshAssistantTextParameterControlsState();
    }

    private void updateAssistantPluginSectionVisibility() {
        if (assistantPluginsSection == null || aiModeCombo == null) {
            return;
        }
        boolean visible = getModeFromDisplayName(aiModeCombo.getValue()) == AiMode.EXTERNAL_OPENAI;
        assistantPluginsSection.setVisible(visible);
        assistantPluginsSection.setManaged(visible);
        updatePluginCompatibilityHint();
    }

    private void updatePluginCompatibilityHint() {
        if (pluginCompatibilityHintLabel == null) {
            return;
        }
        String url = externalUrlField == null ? "" : externalUrlField.getText();
        String normalizedUrl = url == null ? "" : url.trim().toLowerCase(Locale.ROOT);
        boolean looksLikePolza = normalizedUrl.contains("polza.ai");
        String text = looksLikePolza
            ? "Плагины будут отправляться только в запросы /v1/chat/completions текущего Polza API."
            : "Плагины Polza отправляются только в /v1/chat/completions. На текущем external API они могут быть проигнорированы.";
        pluginCompatibilityHintLabel.setText(text);
    }

    private AiMode getModeFromDisplayName(String displayName) {
        for (AiMode mode : AiMode.values()) {
            if (mode.getDisplayName().equals(displayName)) {
                return mode;
            }
        }
        return AiMode.OFFLINE;
    }

    private VBox createAssistantStyleSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Стиль ИИ-ассистента");
        sectionTitle.getStyleClass().add("settings-section-title");

        VBox fieldBox = new VBox(8);

        Label detailLabel = new Label("Подробность ответов:");
        detailLabel.getStyleClass().add("settings-field-label");

        assistantDetailCombo = new ComboBox<>();
        assistantDetailCombo.getItems().addAll("Кратко", "Подробно");
        assistantDetailCombo.getStyleClass().add("ai-combo-box");
        assistantDetailCombo.setMaxWidth(Double.MAX_VALUE);
        assistantDetailCombo.setValue(detailValueToLabel(resolveDetailValue()));
        assistantDetailCombo.setOnAction(e -> ConfigManager.setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_DETAIL,
                detailLabelToValue(assistantDetailCombo.getValue())
        ));

        Label toneLabel = new Label("Тон общения:");
        toneLabel.getStyleClass().add("settings-field-label");

        assistantToneCombo = new ComboBox<>();
        assistantToneCombo.getItems().addAll("Формально", "Дружелюбно");
        assistantToneCombo.getStyleClass().add("ai-combo-box");
        assistantToneCombo.setMaxWidth(Double.MAX_VALUE);
        assistantToneCombo.setValue(toneValueToLabel(resolveToneValue()));
        assistantToneCombo.setOnAction(e -> ConfigManager.setProperty(
                AiConfigDefaults.CONFIG_ASSISTANT_TONE,
                toneLabelToValue(assistantToneCombo.getValue())
        ));

        Label reasoningLabel = new Label("Режим размышления:");
        reasoningLabel.getStyleClass().add("settings-field-label");

        assistantReasoningCombo = new ComboBox<>();
        assistantReasoningCombo.getItems().addAll("Отключено", "Минимум", "Низкий", "Средний", "Высокий", "Максимум");
        assistantReasoningCombo.getStyleClass().add("ai-combo-box");
        assistantReasoningCombo.setMaxWidth(Double.MAX_VALUE);
        assistantReasoningCombo.setValue(reasoningValueToLabel(ConfigManager.getAssistantReasoningEffort()));
        assistantReasoningCombo.setOnAction(e -> {
            ConfigManager.setAssistantReasoningEffort(reasoningLabelToValue(assistantReasoningCombo.getValue()));
            refreshAssistantReasoningControlsState();
        });

        Label reasoningHint = new Label(
            "Для Polza reasoning-моделей отправляется полноценный reasoning-объект. " +
                "Для остальных совместимых моделей остаётся fallback через reasoning_effort."
        );
        reasoningHint.getStyleClass().add("settings-muted-text");
        reasoningHint.setWrapText(true);

        Label reasoningMaxTokensLabel = new Label("Лимит reasoning tokens:");
        reasoningMaxTokensLabel.getStyleClass().add("settings-field-label");

        assistantReasoningMaxTokensField = new TextField();
        assistantReasoningMaxTokensField.getStyleClass().add("settings-text-field");
        assistantReasoningMaxTokensField.setPromptText("Например: 1200");
        Integer currentReasoningMaxTokens = ConfigManager.getAssistantReasoningMaxTokens();
        assistantReasoningMaxTokensField.setText(currentReasoningMaxTokens == null ? "" : String.valueOf(currentReasoningMaxTokens));
        assistantReasoningMaxTokensField.textProperty().addListener((obs, oldValue, newValue) ->
            ConfigManager.setAssistantReasoningMaxTokens(parsePositiveInteger(newValue))
        );

        Label reasoningSummaryLabel = new Label("Резюме reasoning:");
        reasoningSummaryLabel.getStyleClass().add("settings-field-label");

        assistantReasoningSummaryCombo = new ComboBox<>();
        assistantReasoningSummaryCombo.getItems().addAll("Авто", "Краткое", "Подробное");
        assistantReasoningSummaryCombo.getStyleClass().add("ai-combo-box");
        assistantReasoningSummaryCombo.setMaxWidth(Double.MAX_VALUE);
        assistantReasoningSummaryCombo.setValue(reasoningSummaryValueToLabel(ConfigManager.getAssistantReasoningSummary()));
        assistantReasoningSummaryCombo.setOnAction(e -> ConfigManager.setAssistantReasoningSummary(
            reasoningSummaryLabelToValue(assistantReasoningSummaryCombo.getValue())
        ));

        assistantReasoningExcludeCheck = new CheckBox("Скрывать reasoning из ответа");
        assistantReasoningExcludeCheck.getStyleClass().add("settings-checkbox");
        assistantReasoningExcludeCheck.setSelected(ConfigManager.isAssistantReasoningExcluded());
        assistantReasoningExcludeCheck.setWrapText(true);
        assistantReasoningExcludeCheck.setOnAction(e -> ConfigManager.setAssistantReasoningExcluded(
            assistantReasoningExcludeCheck.isSelected()
        ));

        assistantPromptCachingCheck = new CheckBox("Включить prompt caching для Claude");
        assistantPromptCachingCheck.getStyleClass().add("settings-checkbox");
        assistantPromptCachingCheck.setSelected(ConfigManager.isAiPromptCachingEnabled());
        assistantPromptCachingCheck.setWrapText(true);
        assistantPromptCachingCheck.setOnAction(e -> ConfigManager.setAiPromptCachingEnabled(
            assistantPromptCachingCheck.isSelected()
        ));

        Label cachingHint = new Label(
            "Для Claude на внешнем API системный промпт будет отправляться с cache_control=ephemeral. " +
                "Для OpenAI, Gemini, Grok и DeepSeek Polza использует автоматическое кеширование без дополнительных полей."
        );
        cachingHint.getStyleClass().add("settings-muted-text");
        cachingHint.setWrapText(true);

        assistantTextParametersSection = createAssistantTextParametersSection();
        assistantPluginsSection = createAssistantPluginsSection();

        Label portabilityLabel = new Label("Архив переписок:");
        portabilityLabel.getStyleClass().add("settings-field-label");

        Label portabilityHint = new Label(
            "Экспортируйте все переписки в PDF, Markdown или JSON. JSON-архив можно импортировать обратно в приложение."
        );
        portabilityHint.getStyleClass().add("settings-muted-text");
        portabilityHint.setWrapText(true);

        assistantExportChatsMenuButton = createAssistantExportChatsMenuButton();
        assistantImportChatsButton = new Button("Импортировать переписки (JSON)");
        assistantImportChatsButton.setGraphic(FontIcon.of(MaterialDesignF.FILE_IMPORT_OUTLINE, 16));
        assistantImportChatsButton.getStyleClass().add("settings-action-btn");
        assistantImportChatsButton.setMaxWidth(Double.MAX_VALUE);
        assistantImportChatsButton.setOnAction(e -> requestImportChatsFromJson());

        fieldBox.getChildren().addAll(
            detailLabel,
            assistantDetailCombo,
            toneLabel,
            assistantToneCombo,
            reasoningLabel,
            assistantReasoningCombo,
            reasoningHint,
            reasoningMaxTokensLabel,
            assistantReasoningMaxTokensField,
            reasoningSummaryLabel,
            assistantReasoningSummaryCombo,
            assistantReasoningExcludeCheck,
            assistantPromptCachingCheck,
            cachingHint,
            assistantTextParametersSection,
            assistantPluginsSection,
            portabilityLabel,
            portabilityHint,
            assistantExportChatsMenuButton,
            assistantImportChatsButton
        );
        refreshAssistantReasoningControlsState();
        refreshAssistantTextParameterControlsState();
        updateAssistantPluginSectionVisibility();
        section.getChildren().addAll(sectionTitle, fieldBox);
        return section;
    }

    private VBox createAssistantTextParametersSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-plugin-box");

        Label title = new Label("Параметры text-модели API");
        title.getStyleClass().add("settings-field-label");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button resetButton = new Button("Сбросить по умолчанию");
        resetButton.getStyleClass().add("settings-action-btn");
        resetButton.setOnAction(e -> resetAssistantTextParametersToDefaults());

        HBox titleRow = new HBox(12, title, titleSpacer, resetButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        assistantTextParametersHintLabel = new Label();
        assistantTextParametersHintLabel.getStyleClass().add("settings-muted-text");
        assistantTextParametersHintLabel.setWrapText(true);

        Integer currentMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        assistantTextMaxTokensValueLabel = createAssistantParameterValueLabel();
        assistantTextMaxTokensSlider = createAssistantParameterSlider(
                256,
                Math.max(4096, currentMaxTokens == null ? 4096 : currentMaxTokens),
                currentMaxTokens == null ? 4096 : currentMaxTokens,
                256,
                true);
        assistantTextMaxTokensSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            int normalized = normalizeSliderIntValue(newValue.doubleValue(), 256);
            updateAssistantIntegerSliderLabel(assistantTextMaxTokensValueLabel, normalized);
            if (suppressAssistantTextParameterSync) {
                return;
            }
            ConfigManager.setAssistantTextMaxTokens(normalized);
        });
        updateAssistantIntegerSliderLabel(
                assistantTextMaxTokensValueLabel,
                normalizeSliderIntValue(assistantTextMaxTokensSlider.getValue(), 256));

        assistantTextTemperatureValueLabel = createAssistantParameterValueLabel();
        assistantTextTemperatureSlider = createAssistantParameterSlider(
                0.0,
                2.0,
                ConfigManager.getAssistantTextTemperature() == null ? 1.0 : ConfigManager.getAssistantTextTemperature(),
                0.1,
                false);
        assistantTextTemperatureSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            double normalized = normalizeSliderDoubleValue(newValue.doubleValue(), 0.1);
            updateAssistantDoubleSliderLabel(assistantTextTemperatureValueLabel, normalized);
            if (suppressAssistantTextParameterSync) {
                return;
            }
            ConfigManager.setAssistantTextTemperature(normalized);
        });
        updateAssistantDoubleSliderLabel(assistantTextTemperatureValueLabel, assistantTextTemperatureSlider.getValue());

        assistantTextTopPValueLabel = createAssistantParameterValueLabel();
        assistantTextTopPSlider = createAssistantParameterSlider(
                0.0,
                1.0,
                ConfigManager.getAssistantTextTopP() == null ? 1.0 : ConfigManager.getAssistantTextTopP(),
                0.05,
                false);
        assistantTextTopPSlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            double normalized = normalizeSliderDoubleValue(newValue.doubleValue(), 0.05);
            updateAssistantDoubleSliderLabel(assistantTextTopPValueLabel, normalized);
            if (suppressAssistantTextParameterSync) {
                return;
            }
            ConfigManager.setAssistantTextTopP(normalized);
        });
        updateAssistantDoubleSliderLabel(assistantTextTopPValueLabel, assistantTextTopPSlider.getValue());

        assistantTextFrequencyPenaltyValueLabel = createAssistantParameterValueLabel();
        assistantTextFrequencyPenaltySlider = createAssistantParameterSlider(
                -2.0,
                2.0,
                ConfigManager.getAssistantTextFrequencyPenalty() == null ? 0.0 : ConfigManager.getAssistantTextFrequencyPenalty(),
                0.1,
                false);
        assistantTextFrequencyPenaltySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            double normalized = normalizeSliderDoubleValue(newValue.doubleValue(), 0.1);
            updateAssistantDoubleSliderLabel(assistantTextFrequencyPenaltyValueLabel, normalized);
            if (suppressAssistantTextParameterSync) {
                return;
            }
            ConfigManager.setAssistantTextFrequencyPenalty(normalized);
        });
        updateAssistantDoubleSliderLabel(
                assistantTextFrequencyPenaltyValueLabel,
                assistantTextFrequencyPenaltySlider.getValue());

        assistantTextPresencePenaltyValueLabel = createAssistantParameterValueLabel();
        assistantTextPresencePenaltySlider = createAssistantParameterSlider(
                -2.0,
                2.0,
                ConfigManager.getAssistantTextPresencePenalty() == null ? 0.0 : ConfigManager.getAssistantTextPresencePenalty(),
                0.1,
                false);
        assistantTextPresencePenaltySlider.valueProperty().addListener((obs, oldValue, newValue) -> {
            double normalized = normalizeSliderDoubleValue(newValue.doubleValue(), 0.1);
            updateAssistantDoubleSliderLabel(assistantTextPresencePenaltyValueLabel, normalized);
            if (suppressAssistantTextParameterSync) {
                return;
            }
            ConfigManager.setAssistantTextPresencePenalty(normalized);
        });
        updateAssistantDoubleSliderLabel(
                assistantTextPresencePenaltyValueLabel,
                assistantTextPresencePenaltySlider.getValue());

        section.getChildren().addAll(
                titleRow,
                assistantTextParametersHintLabel,
                createAssistantSliderSetting("Макс. токены", assistantTextMaxTokensValueLabel, assistantTextMaxTokensSlider),
                createAssistantSliderSetting("Temperature", assistantTextTemperatureValueLabel, assistantTextTemperatureSlider),
                createAssistantSliderSetting("Top P", assistantTextTopPValueLabel, assistantTextTopPSlider),
                createAssistantSliderSetting("Наказание за повтор", assistantTextFrequencyPenaltyValueLabel, assistantTextFrequencyPenaltySlider),
                createAssistantSliderSetting("Штраф за присутствие", assistantTextPresencePenaltyValueLabel, assistantTextPresencePenaltySlider)
        );
        return section;
    }

    private VBox createAssistantSliderSetting(String titleText, Label valueLabel, Slider slider) {
        Label title = new Label(titleText);
        title.getStyleClass().add("settings-field-label");

        HBox header = new HBox(12, title, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(6, header, slider);
        return box;
    }

    private Label createAssistantParameterValueLabel() {
        Label label = new Label();
        label.getStyleClass().add("settings-muted-text");
        return label;
    }

    private Slider createAssistantParameterSlider(double min, double max, double value, double step, boolean integerValues) {
        Slider slider = new Slider(min, max, value);
        slider.getStyleClass().add("settings-parameter-slider");
        slider.setMaxWidth(Double.MAX_VALUE);
        slider.setShowTickMarks(false);
        slider.setShowTickLabels(false);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(step);
        slider.setMajorTickUnit(step);
        slider.setMinorTickCount(0);
        if (integerValues) {
            slider.setLabelFormatter(null);
        }
        HBox.setHgrow(slider, Priority.ALWAYS);
        return slider;
    }

    private VBox createAssistantPluginsSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("settings-plugin-box");

        Label title = new Label("Плагины Polza");
        title.getStyleClass().add("settings-field-label");

        Label hint = new Label("Плагины отправляются только в /v1/chat/completions и работают для Polza-compatible external API.");
        hint.getStyleClass().add("settings-muted-text");
        hint.setWrapText(true);

        pluginCompatibilityHintLabel = new Label();
        pluginCompatibilityHintLabel.getStyleClass().add("settings-plugin-hint");
        pluginCompatibilityHintLabel.setWrapText(true);

        pluginWebEnabledCheck = new CheckBox("web: поиск в интернете");
        pluginWebEnabledCheck.getStyleClass().add("settings-checkbox");
        pluginWebEnabledCheck.setSelected(ConfigManager.isAiPluginWebEnabled());
        pluginWebEnabledCheck.setWrapText(true);
        pluginWebEnabledCheck.setOnAction(e -> {
            ConfigManager.setAiPluginWebEnabled(pluginWebEnabledCheck.isSelected());
            refreshPluginControlsState();
        });

        Label webEngineLabel = new Label("web.engine:");
        webEngineLabel.getStyleClass().add("settings-field-label");
        pluginWebEngineCombo = new ComboBox<>();
        pluginWebEngineCombo.getItems().addAll("auto", "native", "exa");
        pluginWebEngineCombo.getStyleClass().add("ai-combo-box");
        pluginWebEngineCombo.setMaxWidth(Double.MAX_VALUE);
        pluginWebEngineCombo.setValue(ConfigManager.getAiPluginWebEngine());
        pluginWebEngineCombo.setOnAction(e -> ConfigManager.setAiPluginWebEngine(pluginWebEngineCombo.getValue()));

        Label webMaxResultsLabel = new Label("web.max_results:");
        webMaxResultsLabel.getStyleClass().add("settings-field-label");
        pluginWebMaxResultsField = new TextField(String.valueOf(ConfigManager.getAiPluginWebMaxResults()));
        pluginWebMaxResultsField.getStyleClass().add("settings-text-field");
        pluginWebMaxResultsField.setPromptText("1-20");
        pluginWebMaxResultsField.textProperty().addListener((obs, oldValue, newValue) ->
            ConfigManager.setAiPluginWebMaxResults(parsePositiveInteger(newValue))
        );

        Label webSearchPromptLabel = new Label("web.search_prompt:");
        webSearchPromptLabel.getStyleClass().add("settings-field-label");
        pluginWebSearchPromptField = new TextField(ConfigManager.getAiPluginWebSearchPrompt());
        pluginWebSearchPromptField.getStyleClass().add("settings-text-field");
        pluginWebSearchPromptField.setPromptText("Например: Найти актуальную информацию:");
        pluginWebSearchPromptField.textProperty().addListener((obs, oldValue, newValue) ->
            ConfigManager.setAiPluginWebSearchPrompt(newValue)
        );

        pluginFileParserEnabledCheck = new CheckBox("file-parser: обработка PDF");
        pluginFileParserEnabledCheck.getStyleClass().add("settings-checkbox");
        pluginFileParserEnabledCheck.setSelected(ConfigManager.isAiPluginFileParserEnabled());
        pluginFileParserEnabledCheck.setWrapText(true);
        pluginFileParserEnabledCheck.setOnAction(e -> {
            ConfigManager.setAiPluginFileParserEnabled(pluginFileParserEnabledCheck.isSelected());
            refreshPluginControlsState();
        });

        Label fileParserPdfEngineLabel = new Label("file-parser.pdf.engine:");
        fileParserPdfEngineLabel.getStyleClass().add("settings-field-label");
        pluginFileParserPdfEngineCombo = new ComboBox<>();
        pluginFileParserPdfEngineCombo.getItems().addAll("pdf-text", "mistral-ocr", "native");
        pluginFileParserPdfEngineCombo.getStyleClass().add("ai-combo-box");
        pluginFileParserPdfEngineCombo.setMaxWidth(Double.MAX_VALUE);
        pluginFileParserPdfEngineCombo.setValue(ConfigManager.getAiPluginFileParserPdfEngine());
        pluginFileParserPdfEngineCombo.setOnAction(e ->
            ConfigManager.setAiPluginFileParserPdfEngine(pluginFileParserPdfEngineCombo.getValue())
        );

        pluginResponseHealingEnabledCheck = new CheckBox("response-healing: исправление JSON-ответов");
        pluginResponseHealingEnabledCheck.getStyleClass().add("settings-checkbox");
        pluginResponseHealingEnabledCheck.setSelected(ConfigManager.isAiPluginResponseHealingEnabled());
        pluginResponseHealingEnabledCheck.setWrapText(true);
        pluginResponseHealingEnabledCheck.setOnAction(e ->
            ConfigManager.setAiPluginResponseHealingEnabled(pluginResponseHealingEnabledCheck.isSelected())
        );

        section.getChildren().addAll(
            title,
            hint,
            pluginCompatibilityHintLabel,
            pluginWebEnabledCheck,
            webEngineLabel,
            pluginWebEngineCombo,
            webMaxResultsLabel,
            pluginWebMaxResultsField,
            webSearchPromptLabel,
            pluginWebSearchPromptField,
            pluginFileParserEnabledCheck,
            fileParserPdfEngineLabel,
            pluginFileParserPdfEngineCombo,
            pluginResponseHealingEnabledCheck
        );
        refreshPluginControlsState();
        updatePluginCompatibilityHint();
        return section;
    }

    private void refreshPluginControlsState() {
        boolean webEnabled = pluginWebEnabledCheck != null && pluginWebEnabledCheck.isSelected();
        if (pluginWebEngineCombo != null) {
            pluginWebEngineCombo.setDisable(!webEnabled);
            pluginWebEngineCombo.setOpacity(webEnabled ? 1.0 : 0.6);
        }
        if (pluginWebMaxResultsField != null) {
            pluginWebMaxResultsField.setDisable(!webEnabled);
            pluginWebMaxResultsField.setOpacity(webEnabled ? 1.0 : 0.6);
        }
        if (pluginWebSearchPromptField != null) {
            pluginWebSearchPromptField.setDisable(!webEnabled);
            pluginWebSearchPromptField.setOpacity(webEnabled ? 1.0 : 0.6);
        }

        boolean fileParserEnabled = pluginFileParserEnabledCheck != null && pluginFileParserEnabledCheck.isSelected();
        if (pluginFileParserPdfEngineCombo != null) {
            pluginFileParserPdfEngineCombo.setDisable(!fileParserEnabled);
            pluginFileParserPdfEngineCombo.setOpacity(fileParserEnabled ? 1.0 : 0.6);
        }
    }

    private MenuButton createAssistantExportChatsMenuButton() {
        MenuButton menuButton = new MenuButton("Экспортировать все переписки");
        menuButton.setGraphic(FontIcon.of(MaterialDesignF.FILE_EXPORT_OUTLINE, 16));
        menuButton.getStyleClass().add("settings-action-btn");
        menuButton.setMaxWidth(Double.MAX_VALUE);

        MenuItem pdfItem = new MenuItem("PDF (.pdf)");
        pdfItem.getStyleClass().add("chat-archive-menu-item");
        pdfItem.setOnAction(e -> exportAllChats(ChatArchiveFormat.PDF));
        MenuItem markdownItem = new MenuItem("Markdown (.md)");
        markdownItem.getStyleClass().add("chat-archive-menu-item");
        markdownItem.setOnAction(e -> exportAllChats(ChatArchiveFormat.MARKDOWN));
        MenuItem jsonItem = new MenuItem("JSON (.json)");
        jsonItem.getStyleClass().add("chat-archive-menu-item");
        jsonItem.setOnAction(e -> exportAllChats(ChatArchiveFormat.JSON));

        menuButton.getItems().setAll(pdfItem, markdownItem, jsonItem);
        return menuButton;
    }

    private void exportAllChats(ChatArchiveFormat format) {
        if (assistantChatArchiveBusy) {
            showSettingsAlert(Alert.AlertType.INFORMATION, "Архив переписок", "Операция уже выполняется.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт всех переписок в " + chatArchiveFormatDisplayName(format));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            chatArchiveFormatDisplayName(format) + " (*" + format.defaultExtension() + ")",
            "*" + format.defaultExtension()
        ));
        chooser.setInitialFileName(buildAllChatsExportFileName(format));
        File file = chooser.showSaveDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        updateAssistantChatArchiveBusy(true, "Экспорт...");
        AsyncContext.runAsync(() -> {
            try {
                chatArchiveExportService.exportAllConversations(file, format);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, CHAT_ARCHIVE_EXECUTOR).thenRun(() -> Platform.runLater(() -> {
            updateAssistantChatArchiveBusy(false, null);
            showSettingsAlert(
                Alert.AlertType.INFORMATION,
                "Экспорт завершён",
                chatArchiveFormatSuccessLabel(format) + " сохранён: " + file.getName()
            );
        })).exceptionally(error -> {
            Throwable actual = AsyncContext.unwrap(error);
            Platform.runLater(() -> {
                updateAssistantChatArchiveBusy(false, null);
                UiErrorNotifier.showMappedError(
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    isDarkTheme,
                    "Ошибка экспорта " + chatArchiveFormatDisplayName(format),
                    actual,
                    chatArchiveExportErrorCode(format),
                    "Не удалось экспортировать переписки в " + chatArchiveFormatDisplayName(format) + ".",
                    false,
                    "operation", exportAllChatsOperationName(format),
                    "fileName", file.getName(),
                    "format", format.name()
                );
            });
            return null;
        });
    }

    private void requestImportChatsFromJson() {
        if (assistantChatArchiveBusy) {
            showSettingsAlert(Alert.AlertType.INFORMATION, "Импорт переписок", "Операция уже выполняется.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Импорт переписок из JSON");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"));
        File file = chooser.showOpenDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (file == null) {
            return;
        }

        updateAssistantChatArchiveBusy(true, "Читаю архив...");
        AsyncContext.supplyAsync(() -> {
            try {
                return Files.readString(file.toPath(), StandardCharsets.UTF_8);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, CHAT_ARCHIVE_EXECUTOR).thenAccept(payload -> Platform.runLater(() -> {
            updateAssistantChatArchiveBusy(false, null);
            showChatArchiveImportPreviewDialog(file, payload);
        })).exceptionally(error -> {
            Throwable actual = AsyncContext.unwrap(error);
            Platform.runLater(() -> {
                updateAssistantChatArchiveBusy(false, null);
                UiErrorNotifier.showMappedError(
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    isDarkTheme,
                    "Ошибка чтения архива",
                    actual,
                    ErrorCode.IO_READ_FAILED,
                    "Не удалось прочитать JSON-архив переписок.",
                    false,
                    "operation", "importChatsPreviewRead",
                    "fileName", file.getName()
                );
            });
            return null;
        });
    }

    private void showChatArchiveImportPreviewDialog(File file, String payload) {
        Dialog<ChatArchiveImportPreview> dialog = new Dialog<>();
        dialog.setTitle("Импорт переписок");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDarkTheme) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("styled-alert");
        dialogPane.setPrefWidth(500);

        VBox content = new VBox(12);
        content.setPadding(new Insets(18));

        Label intro = new Label("Проверьте архив перед импортом. Политика конфликта применяется к совпадающим conversationId.");
        intro.setWrapText(true);

        Label fileLabel = new Label("Файл: " + file.getName());
        fileLabel.getStyleClass().add("settings-muted-text");

        Label policyLabel = new Label("Политика конфликта:");
        policyLabel.getStyleClass().add("settings-field-label");

        ComboBox<String> policyCombo = new ComboBox<>();
        policyCombo.getItems().addAll(
            conflictPolicyLabel(ChatArchiveImportConflictPolicy.KEEP_BOTH),
            conflictPolicyLabel(ChatArchiveImportConflictPolicy.REPLACE_EXISTING),
            conflictPolicyLabel(ChatArchiveImportConflictPolicy.SKIP_EXISTING)
        );
        policyCombo.setValue(conflictPolicyLabel(ChatArchiveImportConflictPolicy.KEEP_BOTH));
        policyCombo.setMaxWidth(Double.MAX_VALUE);

        VBox summaryBox = new VBox(6);
        summaryBox.getStyleClass().add("settings-note-box");
        Label foundLabel = new Label();
        Label newLabel = new Label();
        Label conflictsLabel = new Label();
        Label messagesLabel = new Label();
        Label importableLabel = new Label();
        Label skippedLabel = new Label();
        summaryBox.getChildren().addAll(foundLabel, newLabel, conflictsLabel, messagesLabel, importableLabel, skippedLabel);

        ButtonType importButtonType = new ButtonType("Импортировать", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(importButtonType, cancelButtonType);
        Button importButton = (Button) dialogPane.lookupButton(importButtonType);

        final ChatArchiveImportPreview[] currentPreview = new ChatArchiveImportPreview[1];
        Runnable refreshSummary = () -> {
            try {
                ChatArchiveImportPreview preview = chatArchiveImportService.previewJson(
                    payload,
                    new ChatArchiveImportOptions(conflictPolicyFromLabel(policyCombo.getValue()))
                );
                currentPreview[0] = preview;
                foundLabel.setText("Найдено переписок: " + preview.sourceCount());
                newLabel.setText("Новых переписок: " + preview.newConversationCount());
                conflictsLabel.setText("Конфликтов по ID: " + preview.conflictingConversationCount());
                messagesLabel.setText("Сообщений в архиве: " + preview.messageCount());
                importableLabel.setText("Будет импортировано переписок: " + preview.importableConversationCount());
                skippedLabel.setText("Будет пропущено переписок: " + preview.skippedConversationCount());
                importButton.setDisable(preview.importableConversationCount() == 0);
            } catch (Exception ex) {
                importButton.setDisable(true);
                foundLabel.setText("Архив не удалось разобрать.");
                newLabel.setText("");
                conflictsLabel.setText("");
                messagesLabel.setText("");
                importableLabel.setText("");
                skippedLabel.setText("");
                currentPreview[0] = null;
            }
        };
        refreshSummary.run();
        policyCombo.setOnAction(e -> refreshSummary.run());

        content.getChildren().addAll(intro, fileLabel, policyLabel, policyCombo, summaryBox);
        dialogPane.setContent(content);
        dialog.setResultConverter(button -> button == importButtonType ? currentPreview[0] : null);

        dialog.showAndWait().ifPresent(preview -> applyImportedChatArchive(file, preview));
    }

    private void applyImportedChatArchive(File file, ChatArchiveImportPreview preview) {
        updateAssistantChatArchiveBusy(true, "Импорт...");
        AsyncContext.supplyAsync(() -> {
            try {
                return chatArchiveImportService.apply(preview);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, CHAT_ARCHIVE_EXECUTOR).thenAccept(result -> Platform.runLater(() -> {
            updateAssistantChatArchiveBusy(false, null);
            showSettingsAlert(
                Alert.AlertType.INFORMATION,
                "Импорт завершён",
                buildChatArchiveImportSuccessMessage(result, file.getName())
            );
        })).exceptionally(error -> {
            Throwable actual = AsyncContext.unwrap(error);
            Platform.runLater(() -> {
                updateAssistantChatArchiveBusy(false, null);
                UiErrorNotifier.showMappedError(
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    isDarkTheme,
                    "Ошибка импорта переписок",
                    actual,
                    resolveChatArchiveImportErrorCode(actual),
                    "Не удалось импортировать JSON-архив переписок.",
                    false,
                    "operation", "applyChatArchiveImport",
                    "fileName", file.getName(),
                    "policy", preview.options().conflictPolicy().name()
                );
            });
            return null;
        });
    }

    private VBox createImageGenSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("Генерация изображений");
        sectionTitle.getStyleClass().add("settings-section-title");

        Label availabilityNote = new Label("Доступно только во внешнем API режиме");
        availabilityNote.getStyleClass().add("settings-muted-text");
        availabilityNote.setStyle("-fx-font-style: italic;");

        VBox fieldBox = new VBox(8);

        Label modelLabel = new Label("ID image-модели:");
        modelLabel.getStyleClass().add("settings-field-label");

        imageModelField = new TextField();
        imageModelField.getStyleClass().add("settings-text-field");
        imageModelField.setPromptText("Например: gpt-5-image");

        String currentModel = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_MODEL);
        imageModelField.setText(ImageGenConfigDefaults.normalizeImageModel(currentModel));

        HBox modelActionBox = new HBox(10);
        modelActionBox.setAlignment(Pos.CENTER_LEFT);

        Button manageModelsBtn = new Button("Каталог моделей");
        manageModelsBtn.getStyleClass().add("settings-action-btn");
        manageModelsBtn.setGraphic(FontIcon.of(MaterialDesignC.CUBE_OUTLINE, 16));
        manageModelsBtn.setOnAction(e -> openImageModelManagementDialog());

        Button refreshModelsBtn = new Button("Загрузить из API");
        refreshModelsBtn.getStyleClass().add("settings-check-btn");
        refreshModelsBtn.setGraphic(FontIcon.of(MaterialDesignR.REFRESH, 14));

        ProgressIndicator modelsProgress = new ProgressIndicator();
        modelsProgress.setPrefSize(18, 18);
        modelsProgress.setVisible(false);

        refreshModelsBtn.setOnAction(e -> refreshImageModelsFromApi(refreshModelsBtn, modelsProgress));
        modelActionBox.getChildren().addAll(manageModelsBtn, refreshModelsBtn, modelsProgress);

        imageRatioLabel = new Label("Параметр модели:");
        imageRatioLabel.getStyleClass().add("settings-field-label");

        imageRatioCombo = new ComboBox<>();
        imageRatioCombo.getStyleClass().add("ai-combo-box");
        imageRatioCombo.setMaxWidth(Double.MAX_VALUE);
        imageRatioCombo.setEditable(false);

        imageResolutionLabel = new Label("Разрешение:");
        imageResolutionLabel.getStyleClass().add("settings-field-label");

        imageResolutionCombo = new ComboBox<>();
        imageResolutionCombo.getStyleClass().add("ai-combo-box");
        imageResolutionCombo.setMaxWidth(Double.MAX_VALUE);
        imageResolutionCombo.setEditable(false);

        imageQualityLabel = new Label("Качество:");
        imageQualityLabel.getStyleClass().add("settings-field-label");

        imageQualityCombo = new ComboBox<>();
        imageQualityCombo.getStyleClass().add("ai-combo-box");
        imageQualityCombo.setMaxWidth(Double.MAX_VALUE);
        imageQualityCombo.setEditable(false);

        imageOutputFormatLabel = new Label("Формат вывода:");
        imageOutputFormatLabel.getStyleClass().add("settings-field-label");

        imageOutputFormatCombo = new ComboBox<>();
        imageOutputFormatCombo.getStyleClass().add("ai-combo-box");
        imageOutputFormatCombo.setMaxWidth(Double.MAX_VALUE);
        imageOutputFormatCombo.setEditable(false);

        imageStrengthLabel = new Label("Сила референса:");
        imageStrengthLabel.getStyleClass().add("settings-field-label");

        imageStrengthField = new TextField();
        imageStrengthField.getStyleClass().add("settings-text-field");
        imageStrengthField.setPromptText("Например: 0.8");

        imageGuidanceScaleLabel = new Label("Следование промпту:");
        imageGuidanceScaleLabel.getStyleClass().add("settings-field-label");

        imageGuidanceScaleField = new TextField();
        imageGuidanceScaleField.getStyleClass().add("settings-text-field");
        imageGuidanceScaleField.setPromptText("Например: 2.5");

        Label capabilityLabel = new Label("Доступные параметры модели:");
        capabilityLabel.getStyleClass().add("settings-field-label");

        imageCapabilityFlow = new FlowPane();
        imageCapabilityFlow.setHgap(8);
        imageCapabilityFlow.setVgap(8);
        imageCapabilityFlow.getStyleClass().add("settings-image-capability-flow");

        imageCapabilityHintLabel = new Label();
        imageCapabilityHintLabel.getStyleClass().addAll("settings-muted-text", "settings-image-capability-hint");
        imageCapabilityHintLabel.setWrapText(true);

        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        imageGenStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        imageGenStatusIcon.getStyleClass().add("settings-status-icon-neutral");
        imageGenStatusLabel = new Label("Настройте и сохраните");
        imageGenStatusLabel.getStyleClass().add("settings-status-text");
        statusBox.getChildren().addAll(imageGenStatusIcon, imageGenStatusLabel);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("Сохранить");
        saveBtn.getStyleClass().add("settings-save-btn");
        FontIcon saveIcon = FontIcon.of(MaterialDesignC.CONTENT_SAVE, 14);
        saveIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        saveBtn.setGraphic(saveIcon);

        saveBtn.setOnAction(e -> {
            String model = normalizeImageModelFieldValue();
            String primaryValue = imageRatioCombo.getValue();
            String size = ImageGenConfigDefaults.supportsSizeField(model) ? primaryValue : "";
            String aspectRatio = ImageGenConfigDefaults.supportsAspectRatioField(model) ? primaryValue : "";
            String resolution = imageResolutionCombo.getValue();
            String quality = imageQualityCombo.getValue();
            String outputFormat = getComboValue(imageOutputFormatCombo);
            String strength = imageStrengthField.getText();
            String guidanceScale = imageGuidanceScaleField.getText();

            try {
                ImageValidatedOptions validated = ImageGenConfigDefaults.validateImageOptions(
                    model,
                    size,
                    aspectRatio,
                    resolution,
                    quality,
                    outputFormat,
                    strength,
                    guidanceScale
                );
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_MODEL, validated.model());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_SIZE, validated.size());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_ASPECT_RATIO, validated.aspectRatio());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_RESOLUTION, validated.resolution());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_QUALITY, validated.quality());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_OUTPUT_FORMAT, validated.outputFormat());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_STRENGTH, validated.strength());
                ConfigManager.setProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_GUIDANCE_SCALE, validated.guidanceScale());
                rememberImageCustomModel(validated.model());
                ConfigManager.setExternalImageCustomModels(imageCustomModels);
                ConfigManager.setExternalImageDiscoveredModels(imageDiscoveredModels);
                refreshImageGenUiFromConfig(false);
                updateImageGenStatus("success", "Сохранено!");
            } catch (IllegalArgumentException ex) {
                updateImageGenStatus("error", ex.getMessage());
            }
        });

        btnBox.getChildren().add(saveBtn);

        imageModelField.textProperty().addListener((obs, oldValue, newValue) -> refreshImageGenUiFromConfig(true));

        fieldBox.getChildren().addAll(
                modelLabel, imageModelField, modelActionBox,
                imageRatioLabel, imageRatioCombo,
                imageResolutionLabel, imageResolutionCombo,
                imageQualityLabel, imageQualityCombo,
                imageOutputFormatLabel, imageOutputFormatCombo,
                imageStrengthLabel, imageStrengthField,
                imageGuidanceScaleLabel, imageGuidanceScaleField,
                capabilityLabel, imageCapabilityFlow, imageCapabilityHintLabel,
                btnBox,
                statusBox
        );

        section.getChildren().addAll(sectionTitle, availabilityNote, fieldBox);

        refreshImageGenUiFromConfig(false);
        return section;
    }

    private void refreshImageGenUiFromConfig(boolean preserveSelections) {
        String configModel = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_MODEL);
        String configSize = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_SIZE);
        String configAspectRatio = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_ASPECT_RATIO);
        String configResolution = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_RESOLUTION);
        String configQuality = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_QUALITY);
        String configOutputFormat = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_OUTPUT_FORMAT);
        String configStrength = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_STRENGTH);
        String configGuidanceScale = ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_GUIDANCE_SCALE);

        String selectedModel = preserveSelections ? currentImageModelFieldValue() : ImageGenConfigDefaults.normalizeImageModel(configModel);
        String resolvedModel = ImageGenConfigDefaults.normalizeImageModel(selectedModel);
        ImageModelCapability capability = ImageGenConfigDefaults.getCapability(resolvedModel);

        String primaryCandidate = preserveSelections ? imageRatioCombo.getValue() : null;
        String resolutionCandidate = preserveSelections ? imageResolutionCombo.getValue() : null;
        String qualityCandidate = preserveSelections ? imageQualityCombo.getValue() : null;
        String outputFormatCandidate = preserveSelections ? getComboValue(imageOutputFormatCombo) : null;
        String strengthCandidate = preserveSelections ? imageStrengthField.getText() : null;
        String guidanceScaleCandidate = preserveSelections ? imageGuidanceScaleField.getText() : null;

        ImageConfigResolution resolved = ImageGenConfigDefaults.resolveConfiguredOptions(
            selectedModel,
            capability.supports(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.SIZE)
                ? (preserveSelections ? primaryCandidate : configSize)
                : configSize,
            capability.supports(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.ASPECT_RATIO)
                ? (preserveSelections ? primaryCandidate : configAspectRatio)
                : configAspectRatio,
            preserveSelections ? resolutionCandidate : configResolution,
            preserveSelections ? qualityCandidate : configQuality,
            preserveSelections ? outputFormatCandidate : configOutputFormat,
            preserveSelections ? strengthCandidate : configStrength,
            preserveSelections ? guidanceScaleCandidate : configGuidanceScale
        );

        ImageValidatedOptions options = resolved.options();
        ImageModelCapability resolvedCapability = ImageGenConfigDefaults.getCapability(options.model());
        imageModelField.setText(options.model());
        updateImageCapabilityInfo(selectedModel, resolvedCapability);

        imageRatioLabel.setText(resolvedCapability.primaryFieldLabel() + ":");
        imageRatioCombo.getItems().setAll(resolvedCapability.primaryFieldOptions());
        boolean primaryEnabled = !resolvedCapability.primaryFieldOptions().isEmpty();
        imageRatioCombo.setDisable(!primaryEnabled);
        imageRatioCombo.setOpacity(primaryEnabled ? 1.0 : 0.6);
        if (resolvedCapability.supports(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.ASPECT_RATIO)) {
            imageRatioCombo.setValue(options.aspectRatio().isBlank() ? null : options.aspectRatio());
        } else if (resolvedCapability.supports(com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.SIZE)) {
            imageRatioCombo.setValue(options.size().isBlank() ? null : options.size());
        } else {
            imageRatioCombo.setValue(null);
        }

        imageResolutionCombo.getItems().setAll(resolvedCapability.supportedValues(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.RESOLUTION
        ));
        boolean resolutionEnabled = resolvedCapability.supports(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.RESOLUTION
        );
        imageResolutionCombo.setDisable(!resolutionEnabled);
        imageResolutionCombo.setOpacity(resolutionEnabled ? 1.0 : 0.6);
        imageResolutionLabel.setOpacity(resolutionEnabled ? 1.0 : 0.6);
        imageResolutionCombo.setValue(resolutionEnabled && !options.resolution().isBlank() ? options.resolution() : null);

        imageQualityCombo.getItems().setAll(resolvedCapability.supportedValues(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.QUALITY
        ));
        boolean qualityEnabled = resolvedCapability.supports(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.QUALITY
        );
        imageQualityCombo.setDisable(!qualityEnabled);
        imageQualityCombo.setOpacity(qualityEnabled ? 1.0 : 0.6);
        imageQualityLabel.setOpacity(qualityEnabled ? 1.0 : 0.6);
        imageQualityCombo.setValue(qualityEnabled && !options.quality().isBlank() ? options.quality() : null);

        imageOutputFormatCombo.getItems().setAll(resolvedCapability.supportedValues(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.OUTPUT_FORMAT
        ));
        boolean outputFormatEnabled = resolvedCapability.supports(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.OUTPUT_FORMAT
        );
        boolean outputFormatTextual = outputFormatEnabled && resolvedCapability.supportedValues(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.OUTPUT_FORMAT
        ).isEmpty();
        imageOutputFormatCombo.setEditable(outputFormatTextual);
        imageOutputFormatCombo.setDisable(!outputFormatEnabled);
        imageOutputFormatCombo.setOpacity(outputFormatEnabled ? 1.0 : 0.6);
        imageOutputFormatLabel.setOpacity(outputFormatEnabled ? 1.0 : 0.6);
        if (outputFormatEnabled && !options.outputFormat().isBlank()) {
            if (!imageOutputFormatCombo.getItems().contains(options.outputFormat())) {
                imageOutputFormatCombo.getEditor().setText(options.outputFormat());
            }
            imageOutputFormatCombo.setValue(options.outputFormat());
        } else {
            imageOutputFormatCombo.setValue(null);
            imageOutputFormatCombo.getEditor().clear();
        }

        boolean strengthEnabled = resolvedCapability.supports(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.STRENGTH
        );
        imageStrengthField.setDisable(!strengthEnabled);
        imageStrengthField.setOpacity(strengthEnabled ? 1.0 : 0.6);
        imageStrengthLabel.setOpacity(strengthEnabled ? 1.0 : 0.6);
        imageStrengthField.setText(strengthEnabled ? options.strength() : "");

        boolean guidanceScaleEnabled = resolvedCapability.supports(
            com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField.GUIDANCE_SCALE
        );
        imageGuidanceScaleField.setDisable(!guidanceScaleEnabled);
        imageGuidanceScaleField.setOpacity(guidanceScaleEnabled ? 1.0 : 0.6);
        imageGuidanceScaleLabel.setOpacity(guidanceScaleEnabled ? 1.0 : 0.6);
        imageGuidanceScaleField.setText(guidanceScaleEnabled ? options.guidanceScale() : "");

        if (resolved.hasIssues()) {
            updateImageGenStatus("error", resolved.summary());
        } else {
            updateImageGenStatus("neutral", "Настройте и сохраните");
        }
    }

    private void updateImageCapabilityInfo(String selectedModel, ImageModelCapability capability) {
        if (imageCapabilityFlow == null || imageCapabilityHintLabel == null || capability == null) {
            return;
        }
        imageCapabilityFlow.getChildren().clear();
        List<String> parameters = capability.documentedParameters();
        for (String parameter : parameters) {
            Label badge = new Label(parameter);
            badge.getStyleClass().add("settings-image-capability-badge");
            imageCapabilityFlow.getChildren().add(badge);
        }
        boolean knownModel = ImageGenConfigDefaults.isSupportedImageModel(selectedModel);
        if (!knownModel && selectedModel != null && !selectedModel.isBlank()) {
            imageCapabilityHintLabel.setText("Для пользовательской модели список параметров неизвестен. Приложение гарантированно отправит только model и prompt.");
            if (parameters.isEmpty()) {
                Label fallbackBadge = new Label("prompt");
                fallbackBadge.getStyleClass().add("settings-image-capability-badge");
                imageCapabilityFlow.getChildren().add(fallbackBadge);
            }
            return;
        }
        imageCapabilityHintLabel.setText("Список собран по документации Polza. Параметры prompt и images задаются в самом запросе, остальные поддерживаемые параметры можно сохранить здесь как defaults.");
    }

    private void updateLocalStatus(String status, String text) {
        updateStatusIcon(localStatusIcon, localStatusLabel, status, text);
    }

    private void updateExternalStatus(String status, String text) {
        updateStatusIcon(externalStatusIcon, externalStatusLabel, status, text);
    }

    private void updateImageGenStatus(String type, String message) {
        updateStatusIcon(imageGenStatusIcon, imageGenStatusLabel, type, message);
    }

    private void updateAssistantChatArchiveBusy(boolean busy, String busyText) {
        assistantChatArchiveBusy = busy;
        if (assistantExportChatsMenuButton != null) {
            assistantExportChatsMenuButton.setDisable(busy);
            assistantExportChatsMenuButton.setText(busy ? (busyText == null ? "Экспорт..." : busyText) : "Экспортировать все переписки");
        }
        if (assistantImportChatsButton != null) {
            assistantImportChatsButton.setDisable(busy);
            assistantImportChatsButton.setText(busy ? (busyText == null ? "Импорт..." : busyText) : "Импортировать переписки (JSON)");
        }
    }

    private String buildAllChatsExportFileName(ChatArchiveFormat format) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return "neuroflow_chat_archive_all_" + date + format.defaultExtension();
    }

    private String buildChatArchiveImportSuccessMessage(ChatArchiveImportResult result, String fileName) {
        return "Архив " + fileName
            + ": импортировано переписок " + result.importedConversationCount()
            + ", сообщений " + result.importedMessageCount()
            + ", пропущено " + result.skippedConversationCount() + ".";
    }

    private String conflictPolicyLabel(ChatArchiveImportConflictPolicy policy) {
        return switch (policy) {
            case KEEP_BOTH -> "Сохранить обе версии";
            case REPLACE_EXISTING -> "Заменить существующие";
            case SKIP_EXISTING -> "Пропустить существующие";
        };
    }

    private ChatArchiveImportConflictPolicy conflictPolicyFromLabel(String label) {
        if (label == null) {
            return ChatArchiveImportConflictPolicy.KEEP_BOTH;
        }
        return switch (label) {
            case "Заменить существующие" -> ChatArchiveImportConflictPolicy.REPLACE_EXISTING;
            case "Пропустить существующие" -> ChatArchiveImportConflictPolicy.SKIP_EXISTING;
            default -> ChatArchiveImportConflictPolicy.KEEP_BOTH;
        };
    }

    private String chatArchiveFormatDisplayName(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "PDF";
            case MARKDOWN -> "Markdown";
            case JSON -> "JSON";
        };
    }

    private String chatArchiveFormatSuccessLabel(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "PDF";
            case MARKDOWN -> "Markdown";
            case JSON -> "JSON-архив";
        };
    }

    private ErrorCode chatArchiveExportErrorCode(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> ErrorCode.EXPORT_PDF_FAILED;
            case MARKDOWN -> ErrorCode.EXPORT_MARKDOWN_FAILED;
            case JSON -> ErrorCode.EXPORT_JSON_FAILED;
        };
    }

    private String exportAllChatsOperationName(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "exportAllChatsPdf";
            case MARKDOWN -> "exportAllChatsMarkdown";
            case JSON -> "exportAllChatsJson";
        };
    }

    private ErrorCode resolveChatArchiveImportErrorCode(Throwable error) {
        return error instanceof ChatArchiveImportValidationException
            ? ErrorCode.VALIDATION_FAILED
            : ErrorCode.IO_READ_FAILED;
    }

    private void updateStatusIcon(FontIcon icon, Label label, String status, String text) {
        icon.getStyleClass().removeAll(
                "settings-status-icon-neutral",
                "settings-status-icon-success",
                "settings-status-icon-error",
                "settings-status-icon-checking"
        );
        switch (status) {
            case "success" -> {
                icon.setIconCode(MaterialDesignC.CHECK_CIRCLE);
                icon.getStyleClass().add("settings-status-icon-success");
            }
            case "error" -> {
                icon.setIconCode(MaterialDesignA.ALERT_CIRCLE);
                icon.getStyleClass().add("settings-status-icon-error");
            }
            case "checking" -> {
                icon.setIconCode(MaterialDesignS.SYNC);
                icon.getStyleClass().add("settings-status-icon-checking");
            }
            default -> {
                icon.setIconCode(MaterialDesignC.CIRCLE_OUTLINE);
                icon.getStyleClass().add("settings-status-icon-neutral");
            }
        }
        label.setText(text);
    }

    private void showSettingsAlert(Alert.AlertType type, String title, String message) {
        javafx.stage.Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        if (type == Alert.AlertType.ERROR) {
            UiErrorNotifier.showMappedError(
                owner,
                isDarkTheme,
                title,
                null,
                ErrorCode.UNEXPECTED_ERROR,
                message,
                false,
                "operation", "settings.showAlert"
            );
            return;
        }
        if (type == Alert.AlertType.WARNING) {
            UiErrorNotifier.showWarning(owner, isDarkTheme, title, message);
            return;
        }
        UiErrorNotifier.showInfo(owner, isDarkTheme, title, message);
    }

    private String resolveDetailValue() {
        String value = ConfigManager.getProperty(AiConfigDefaults.CONFIG_ASSISTANT_DETAIL);
        if (AiConfigDefaults.ASSISTANT_DETAIL_DETAILED.equalsIgnoreCase(value)) {
            return AiConfigDefaults.ASSISTANT_DETAIL_DETAILED;
        }
        return AiConfigDefaults.DEFAULT_ASSISTANT_DETAIL;
    }

    private String resolveToneValue() {
        String value = ConfigManager.getProperty(AiConfigDefaults.CONFIG_ASSISTANT_TONE);
        if (AiConfigDefaults.ASSISTANT_TONE_FORMAL.equalsIgnoreCase(value)) {
            return AiConfigDefaults.ASSISTANT_TONE_FORMAL;
        }
        return AiConfigDefaults.DEFAULT_ASSISTANT_TONE;
    }

    private String reasoningValueToLabel(String value) {
        return switch (AiConfigDefaults.normalizeAssistantReasoningEffort(value)) {
            case AiConfigDefaults.ASSISTANT_REASONING_NONE -> "Отключено";
            case AiConfigDefaults.ASSISTANT_REASONING_MINIMAL -> "Минимум";
            case AiConfigDefaults.ASSISTANT_REASONING_LOW -> "Низкий";
            case AiConfigDefaults.ASSISTANT_REASONING_HIGH -> "Высокий";
            case AiConfigDefaults.ASSISTANT_REASONING_XHIGH -> "Максимум";
            default -> "Средний";
        };
    }

    private String reasoningLabelToValue(String label) {
        if (label == null) {
            return AiConfigDefaults.DEFAULT_ASSISTANT_REASONING_EFFORT;
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "отключено" -> AiConfigDefaults.ASSISTANT_REASONING_NONE;
            case "минимум" -> AiConfigDefaults.ASSISTANT_REASONING_MINIMAL;
            case "низкий" -> AiConfigDefaults.ASSISTANT_REASONING_LOW;
            case "высокий" -> AiConfigDefaults.ASSISTANT_REASONING_HIGH;
            case "максимум" -> AiConfigDefaults.ASSISTANT_REASONING_XHIGH;
            default -> AiConfigDefaults.ASSISTANT_REASONING_MEDIUM;
        };
    }

    private String reasoningSummaryValueToLabel(String value) {
        return switch (AiConfigDefaults.normalizeAssistantReasoningSummary(value)) {
            case AiConfigDefaults.ASSISTANT_REASONING_SUMMARY_CONCISE -> "Краткое";
            case AiConfigDefaults.ASSISTANT_REASONING_SUMMARY_DETAILED -> "Подробное";
            default -> "Авто";
        };
    }

    private String reasoningSummaryLabelToValue(String label) {
        if (label == null) {
            return AiConfigDefaults.DEFAULT_ASSISTANT_REASONING_SUMMARY;
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "краткое" -> AiConfigDefaults.ASSISTANT_REASONING_SUMMARY_CONCISE;
            case "подробное" -> AiConfigDefaults.ASSISTANT_REASONING_SUMMARY_DETAILED;
            default -> AiConfigDefaults.ASSISTANT_REASONING_SUMMARY_AUTO;
        };
    }

    private Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String formatNullableDouble(Double value) {
        if (value == null) {
            return "";
        }
        if (Math.rint(value) == value) {
            return String.format(Locale.US, "%.0f", value);
        }
        return value.toString();
    }

    private String formatSliderDouble(double value) {
        if (Math.rint(value) == value) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private int normalizeSliderIntValue(double value, int step) {
        return Math.max(step, (int) Math.round(value / step) * step);
    }

    private double normalizeSliderDoubleValue(double value, double step) {
        return Math.round(value / step) * step;
    }

    private void updateAssistantIntegerSliderLabel(Label label, int value) {
        if (label != null) {
            label.setText(String.valueOf(value));
        }
    }

    private void updateAssistantDoubleSliderLabel(Label label, double value) {
        if (label != null) {
            label.setText(formatSliderDouble(value));
        }
    }

    private int getAssistantTextMaxTokensDefault(AiTextModelParameterMetadata metadata) {
        if (metadata != null && metadata.maxCompletionTokens() != null && metadata.maxCompletionTokens() > 0) {
            return Math.min(metadata.maxCompletionTokens(), 4096);
        }
        return 4096;
    }

    private double getAssistantTextTemperatureDefault(AiTextModelParameterMetadata metadata) {
        return metadata != null && metadata.defaultTemperature() != null ? metadata.defaultTemperature() : 1.0;
    }

    private double getAssistantTextTopPDefault(AiTextModelParameterMetadata metadata) {
        return metadata != null && metadata.defaultTopP() != null ? metadata.defaultTopP() : 1.0;
    }

    private double getAssistantTextFrequencyPenaltyDefault(AiTextModelParameterMetadata metadata) {
        return metadata != null && metadata.defaultFrequencyPenalty() != null ? metadata.defaultFrequencyPenalty() : 0.0;
    }

    private double getAssistantTextPresencePenaltyDefault(AiTextModelParameterMetadata metadata) {
        return metadata != null && metadata.defaultPresencePenalty() != null ? metadata.defaultPresencePenalty() : 0.0;
    }

    private void applyAssistantTextParameterSliderValues(AiTextModelParameterMetadata metadata) {
        suppressAssistantTextParameterSync = true;
        try {
            if (assistantTextMaxTokensSlider != null) {
                int configured = ConfigManager.getAssistantTextMaxTokens() != null
                        ? ConfigManager.getAssistantTextMaxTokens()
                        : getAssistantTextMaxTokensDefault(metadata);
                int sliderMax = metadata != null && metadata.maxCompletionTokens() != null
                        ? Math.max(256, Math.max(metadata.maxCompletionTokens(), configured))
                        : Math.max(4096, configured);
                assistantTextMaxTokensSlider.setMin(256);
                assistantTextMaxTokensSlider.setMax(sliderMax);
                assistantTextMaxTokensSlider.setMajorTickUnit(256);
                assistantTextMaxTokensSlider.setBlockIncrement(256);
                assistantTextMaxTokensSlider.setValue(Math.min(sliderMax, configured));
                updateAssistantIntegerSliderLabel(
                        assistantTextMaxTokensValueLabel,
                        normalizeSliderIntValue(assistantTextMaxTokensSlider.getValue(), 256));
            }
            if (assistantTextTemperatureSlider != null) {
                double value = ConfigManager.getAssistantTextTemperature() != null
                        ? ConfigManager.getAssistantTextTemperature()
                        : getAssistantTextTemperatureDefault(metadata);
                assistantTextTemperatureSlider.setValue(value);
                updateAssistantDoubleSliderLabel(assistantTextTemperatureValueLabel, value);
            }
            if (assistantTextTopPSlider != null) {
                double value = ConfigManager.getAssistantTextTopP() != null
                        ? ConfigManager.getAssistantTextTopP()
                        : getAssistantTextTopPDefault(metadata);
                assistantTextTopPSlider.setValue(value);
                updateAssistantDoubleSliderLabel(assistantTextTopPValueLabel, value);
            }
            if (assistantTextFrequencyPenaltySlider != null) {
                double value = ConfigManager.getAssistantTextFrequencyPenalty() != null
                        ? ConfigManager.getAssistantTextFrequencyPenalty()
                        : getAssistantTextFrequencyPenaltyDefault(metadata);
                assistantTextFrequencyPenaltySlider.setValue(value);
                updateAssistantDoubleSliderLabel(assistantTextFrequencyPenaltyValueLabel, value);
            }
            if (assistantTextPresencePenaltySlider != null) {
                double value = ConfigManager.getAssistantTextPresencePenalty() != null
                        ? ConfigManager.getAssistantTextPresencePenalty()
                        : getAssistantTextPresencePenaltyDefault(metadata);
                assistantTextPresencePenaltySlider.setValue(value);
                updateAssistantDoubleSliderLabel(assistantTextPresencePenaltyValueLabel, value);
            }
        } finally {
            suppressAssistantTextParameterSync = false;
        }
    }

    private void resetAssistantTextParametersToDefaults() {
        AiTextModelParameterMetadata metadata =
                AiTextModelParameterResolver.resolveForModel(normalizeExternalModelFieldValue());
        ConfigManager.setAssistantTextMaxTokens(null);
        ConfigManager.setAssistantTextTemperature(null);
        ConfigManager.setAssistantTextTopP(null);
        ConfigManager.setAssistantTextFrequencyPenalty(null);
        ConfigManager.setAssistantTextPresencePenalty(null);
        applyAssistantTextParameterSliderValues(metadata);
    }

    private void refreshAssistantReasoningControlsState() {
        boolean disabled = AiConfigDefaults.ASSISTANT_REASONING_NONE.equals(
            reasoningLabelToValue(assistantReasoningCombo == null ? null : assistantReasoningCombo.getValue())
        );
        if (assistantReasoningMaxTokensField != null) {
            assistantReasoningMaxTokensField.setDisable(disabled);
            assistantReasoningMaxTokensField.setOpacity(disabled ? 0.6 : 1.0);
        }
        if (assistantReasoningSummaryCombo != null) {
            assistantReasoningSummaryCombo.setDisable(disabled);
            assistantReasoningSummaryCombo.setOpacity(disabled ? 0.6 : 1.0);
        }
        if (assistantReasoningExcludeCheck != null) {
            assistantReasoningExcludeCheck.setDisable(disabled);
            assistantReasoningExcludeCheck.setOpacity(disabled ? 0.6 : 1.0);
        }
    }

    private void refreshAssistantTextParameterControlsState() {
        if (assistantTextParametersSection == null) {
            return;
        }
        boolean externalMode = aiModeCombo != null
                && getModeFromDisplayName(aiModeCombo.getValue()) == AiMode.EXTERNAL_OPENAI;
        if (!externalMode) {
            assistantTextParametersSection.setVisible(false);
            assistantTextParametersSection.setManaged(false);
            return;
        }
        assistantTextParametersSection.setVisible(true);
        assistantTextParametersSection.setManaged(true);

        String modelId = normalizeExternalModelFieldValue();
        AiTextModelContextMetadata contextMetadata = AiTextModelContextResolver.resolveForModel(modelId);
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(modelId);

        if (assistantTextParametersHintLabel != null) {
            if (modelId.isBlank()) {
                assistantTextParametersHintLabel.setText("Выберите ID модели во внешнем API, чтобы настроить её text-параметры.");
            } else if (metadata == null && contextMetadata == null) {
                assistantTextParametersHintLabel.setText(
                        "Для модели " + modelId + " metadata параметров пока не найдены. " +
                                "Нажмите «Проверить» в разделе внешнего API, чтобы обновить каталог."
                );
            } else {
                StringBuilder hint = new StringBuilder("Параметры модели ").append(modelId).append(".");
                if (contextMetadata != null
                        && contextMetadata.contextWindowLabel() != null
                        && !contextMetadata.contextWindowLabel().isBlank()) {
                    hint.append(" Контекстное окно: ").append(contextMetadata.contextWindowLabel()).append(".");
                }
                if (metadata.maxCompletionTokens() != null && metadata.maxCompletionTokens() > 0) {
                    hint.append(" Лимит completion tokens до ").append(metadata.maxCompletionTokens()).append(".");
                }
                assistantTextParametersHintLabel.setText(hint.toString());
            }
        }

        setControlState(assistantTextMaxTokensSlider, !modelId.isBlank());
        setControlState(assistantTextTemperatureSlider, metadata != null && metadata.supportsTemperature());
        setControlState(assistantTextTopPSlider, metadata != null && metadata.supportsTopP());
        setControlState(assistantTextFrequencyPenaltySlider, metadata != null && metadata.supportsFrequencyPenalty());
        setControlState(assistantTextPresencePenaltySlider, metadata != null && metadata.supportsPresencePenalty());

        applyAssistantTextParameterSliderValues(metadata);
    }

    private void setControlState(Control control, boolean enabled) {
        if (control == null) {
            return;
        }
        control.setDisable(!enabled);
        control.setOpacity(enabled ? 1.0 : 0.6);
    }

    private String detailValueToLabel(String value) {
        if (AiConfigDefaults.ASSISTANT_DETAIL_DETAILED.equalsIgnoreCase(value)) {
            return "Подробно";
        }
        return "Кратко";
    }

    private String detailLabelToValue(String label) {
        if (label == null) {
            return AiConfigDefaults.ASSISTANT_DETAIL_BRIEF;
        }
        if ("Подробно".equalsIgnoreCase(label)) {
            return AiConfigDefaults.ASSISTANT_DETAIL_DETAILED;
        }
        return AiConfigDefaults.ASSISTANT_DETAIL_BRIEF;
    }

    private String toneValueToLabel(String value) {
        if (AiConfigDefaults.ASSISTANT_TONE_FORMAL.equalsIgnoreCase(value)) {
            return "Формально";
        }
        return "Дружелюбно";
    }

    private String toneLabelToValue(String label) {
        if (label == null) {
            return AiConfigDefaults.ASSISTANT_TONE_FRIENDLY;
        }
        if ("Формально".equalsIgnoreCase(label)) {
            return AiConfigDefaults.ASSISTANT_TONE_FORMAL;
        }
        return AiConfigDefaults.ASSISTANT_TONE_FRIENDLY;
    }

    private String getComboValue(ComboBox<String> combo) {
        if (combo == null) return null;
        String value = combo.getValue();
        if (value != null && !value.isBlank()) return value;
        String editorValue = combo.getEditor().getText();
        return editorValue != null ? editorValue.trim() : null;
    }

    private String normalizeExternalModelFieldValue() {
        if (externalModelField == null) {
            return "";
        }
        String normalized = AiConfigDefaults.normalizeExternalModelId(externalModelField.getText());
        externalModelField.setText(normalized);
        return normalized;
    }

    private void rememberExternalCustomModel(String model) {
        String normalized = AiConfigDefaults.normalizeExternalModelId(model);
        if (normalized.isBlank()) {
            return;
        }
        if (!AiConfigDefaults.MODEL_OPTIONS.contains(normalized)
                && !externalDiscoveredModels.contains(normalized)
                && !externalCustomModels.contains(normalized)) {
            externalCustomModels.add(0, normalized);
        }
    }

    private void updateExternalDiscoveredModels(
            List<String> models,
            List<String> multimodalModels,
            List<String> audioInputModels,
            List<String> fileInputModels,
            List<AiDiscoveredModelInfo> modelCatalog) {
        externalDiscoveredModels.clear();
        if (models != null) {
            for (String model : models) {
                String normalized = AiConfigDefaults.normalizeExternalModelId(model);
                if (!normalized.isBlank() && !externalDiscoveredModels.contains(normalized)) {
                    externalDiscoveredModels.add(normalized);
                }
            }
        }
        externalMultimodalModels.clear();
        if (multimodalModels != null) {
            for (String model : multimodalModels) {
                String normalized = AiConfigDefaults.normalizeExternalModelId(model);
                if (!normalized.isBlank()
                        && externalDiscoveredModels.contains(normalized)
                        && !externalMultimodalModels.contains(normalized)) {
                    externalMultimodalModels.add(normalized);
                }
            }
        }
        externalAudioInputModels.clear();
        if (audioInputModels != null) {
            for (String model : audioInputModels) {
                String normalized = AiConfigDefaults.normalizeExternalModelId(model);
                if (!normalized.isBlank()
                        && externalDiscoveredModels.contains(normalized)
                        && !externalAudioInputModels.contains(normalized)) {
                    externalAudioInputModels.add(normalized);
                }
            }
        }
        externalFileInputModels.clear();
        if (fileInputModels != null) {
            for (String model : fileInputModels) {
                String normalized = AiConfigDefaults.normalizeExternalModelId(model);
                if (!normalized.isBlank()
                        && externalDiscoveredModels.contains(normalized)
                        && !externalFileInputModels.contains(normalized)) {
                    externalFileInputModels.add(normalized);
                }
            }
        }
        externalModelCatalog.clear();
        if (modelCatalog != null) {
            for (AiDiscoveredModelInfo modelInfo : modelCatalog) {
                if (modelInfo == null) {
                    continue;
                }
                String normalized = AiConfigDefaults.normalizeExternalModelId(modelInfo.id());
                if (!normalized.isBlank()) {
                    externalModelCatalog.add(new AiDiscoveredModelInfo(
                            normalized,
                            modelInfo.type(),
                            modelInfo.multimodal(),
                            modelInfo.supportsImageInput(),
                            modelInfo.supportsAudioInput(),
                            modelInfo.supportsFileInput(),
                            modelInfo.textContextMetadata(),
                            modelInfo.textParameterMetadata()));
                }
            }
        }
        ConfigManager.setExternalApiDiscoveredModels(externalDiscoveredModels);
        ConfigManager.setExternalApiMultimodalModels(externalMultimodalModels);
        ConfigManager.setExternalApiAudioInputModels(externalAudioInputModels);
        ConfigManager.setExternalApiFileInputModels(externalFileInputModels);
        ConfigManager.setExternalApiModelCatalog(externalModelCatalog);
        String currentModel = normalizeExternalModelFieldValue();
        if (currentModel.isBlank() && !externalDiscoveredModels.isEmpty()) {
            externalModelField.setText(externalDiscoveredModels.get(0));
        }
        refreshAssistantTextParameterControlsState();
    }

    private void openExternalModelManagementDialog() {
        ModelManagementDialog.Result result = ModelManagementDialog.showExternalApi(
                List.copyOf(externalDiscoveredModels),
                List.copyOf(externalMultimodalModels),
                List.copyOf(externalAudioInputModels),
                List.copyOf(externalFileInputModels),
                List.copyOf(externalCustomModels),
                normalizeExternalModelFieldValue()
        );
        if (result == null) {
            return;
        }
        externalCustomModels.clear();
        externalCustomModels.addAll(result.customModels());
        if (externalModelField != null) {
            externalModelField.setText(AiConfigDefaults.normalizeExternalModelId(result.selectedModel()));
        }
    }

    private String normalizeImageModelFieldValue() {
        if (imageModelField == null) {
            return "";
        }
        String normalized = currentImageModelFieldValue();
        imageModelField.setText(normalized);
        return normalized;
    }

    private String currentImageModelFieldValue() {
        if (imageModelField == null) {
            return "";
        }
        return ImageGenConfigDefaults.normalizeImageModel(imageModelField.getText());
    }

    private void rememberImageCustomModel(String model) {
        String normalized = ImageGenConfigDefaults.normalizeImageModel(model);
        if (normalized.isBlank()) {
            return;
        }
        if (!ImageGenConfigDefaults.IMAGE_MODEL_OPTIONS.contains(normalized)
                && !imageDiscoveredModels.contains(normalized)
                && !imageCustomModels.contains(normalized)) {
            imageCustomModels.add(0, normalized);
        }
    }

    private void updateImageDiscoveredModels(List<String> models) {
        imageDiscoveredModels.clear();
        if (models != null) {
            for (String model : models) {
                String normalized = ImageGenConfigDefaults.normalizeImageModel(model);
                if (!normalized.isBlank() && !imageDiscoveredModels.contains(normalized)) {
                    imageDiscoveredModels.add(normalized);
                }
            }
        }
        ConfigManager.setExternalImageDiscoveredModels(imageDiscoveredModels);
        String currentModel = currentImageModelFieldValue();
        if (currentModel.isBlank() && !imageDiscoveredModels.isEmpty()) {
            imageModelField.setText(imageDiscoveredModels.get(0));
        }
    }

    private void openImageModelManagementDialog() {
        ModelManagementDialog.Result result = ModelManagementDialog.showImageModels(
                List.copyOf(imageDiscoveredModels),
                List.copyOf(imageCustomModels),
                normalizeImageModelFieldValue()
        );
        if (result == null) {
            return;
        }
        imageCustomModels.clear();
        imageCustomModels.addAll(result.customModels());
        imageModelField.setText(ImageGenConfigDefaults.normalizeImageModel(result.selectedModel()));
        refreshImageGenUiFromConfig(true);
    }

    private void refreshImageModelsFromApi(Button refreshButton, ProgressIndicator progressIndicator) {
        String url = externalUrlField == null ? "" : externalUrlField.getText().trim();
        String rawKeyInput = externalKeyField == null ? "" : externalKeyField.getText().trim();
        String key = resolveExternalKeyInput(rawKeyInput);

        if (url.isEmpty()) {
            updateImageGenStatus("error", "Сначала укажите URL внешнего API.");
            return;
        }
        if (key.isEmpty()) {
            updateImageGenStatus("error", "Сначала укажите API ключ внешнего режима.");
            return;
        }

        refreshButton.setDisable(true);
        progressIndicator.setVisible(true);
        updateImageGenStatus("checking", "Загружаю image-модели из API...");

        ExternalOpenAiClient testClient = AiClientFactory.getInstance().createTestExternalClient(url, key, null);
        testClient.fetchImageModels().thenAccept(models -> Platform.runLater(() -> {
            refreshButton.setDisable(false);
            progressIndicator.setVisible(false);

            if (models == null || models.isEmpty()) {
                updateImageDiscoveredModels(List.of());
                updateImageGenStatus("error", "Провайдер не вернул image-модели.");
                return;
            }

            updateImageDiscoveredModels(models);
            refreshImageGenUiFromConfig(true);
            updateImageGenStatus("success", "Image-модели обновлены.");
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                refreshButton.setDisable(false);
                progressIndicator.setVisible(false);
                Throwable actual = AsyncContext.unwrap(error);
                updateImageGenStatus("error", actual.getMessage() == null ? "Не удалось загрузить image-модели." : actual.getMessage());
            });
            return null;
        });
    }

    private void setExternalKeyMask(String actualKey) {
        if (actualKey != null && !actualKey.isBlank()) {
            externalMaskedKeyValue = SensitiveDataRedactor.maskSecret(actualKey);
            externalKeyField.setText(externalMaskedKeyValue);
        } else {
            externalMaskedKeyValue = "";
            externalKeyField.clear();
        }
    }

    private boolean isMaskedExternalKeyInput(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return !externalMaskedKeyValue.isBlank() && externalMaskedKeyValue.equals(input.trim());
    }

    private String resolveExternalKeyInput(String input) {
        if (isMaskedExternalKeyInput(input)) {
            String current = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_API_KEY);
            return current == null ? "" : current.trim();
        }
        return input == null ? "" : input.trim();
    }

    private void setupToggleSwitch() {
        toggleSwitch.setPrefSize(50, 26);
        toggleSwitch.getStyleClass().add("settings-switch");
        toggleSwitch.getStyleClass().add(isDarkTheme ? "settings-switch-on" : "settings-switch-off");

        Region thumb = new Region();
        thumb.setPrefSize(20, 20);
        thumb.getStyleClass().add("settings-switch-thumb");
        thumb.setTranslateX(isDarkTheme ? 12 : -12);

        toggleSwitch.getChildren().add(thumb);
        StackPane.setAlignment(thumb, Pos.CENTER_LEFT);

        toggleSwitch.setOnMouseClicked(e -> {
            isDarkTheme = !isDarkTheme;
            ConfigManager.setDarkTheme(isDarkTheme);
            animateThemeChange(thumb);
        });
    }

    private void animateThemeChange(Region thumb) {
        TranslateTransition thumbAnim = new TranslateTransition(Duration.millis(200), thumb);
        thumbAnim.setToX(isDarkTheme ? 12 : -12);

        FadeTransition fade = new FadeTransition(Duration.millis(300), root);
        fade.setFromValue(1.0);
        fade.setToValue(0.8);
        fade.setCycleCount(2);
        fade.setAutoReverse(true);

        fade.setOnFinished(e -> {
            updateThemeUI();
            applyThemeToApp();
        });

        thumbAnim.play();
        fade.play();
    }

    private void updateThemeUI() {
        toggleSwitch.getStyleClass().removeAll("settings-switch-on", "settings-switch-off");
        toggleSwitch.getStyleClass().add(isDarkTheme ? "settings-switch-on" : "settings-switch-off");

        themeIcon.setIconCode(isDarkTheme ? MaterialDesignW.WEATHER_NIGHT : MaterialDesignW.WEATHER_SUNNY);
        themeLabel.setText(isDarkTheme ? "Темная тема" : "Светлая тема");
    }

    private void applyThemeToApp() {
        applyLocalStyles();
        if (mainScene != null) {
            Platform.runLater(() -> {
                mainScene.getStylesheets().clear();
                mainScene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
                if (isDarkTheme) {
                    mainScene.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
                }
                if (themeChangeCallback != null) {
                    themeChangeCallback.run();
                }
            });
        }
    }

    private void applyLocalStyles() {
        root.getStylesheets().clear();
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDarkTheme) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }
}
