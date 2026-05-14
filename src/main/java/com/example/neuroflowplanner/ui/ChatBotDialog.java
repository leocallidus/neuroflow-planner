package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.ai.AiTextModelContextResolver;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.example.neuroflowplanner.service.ChatBotService;
import com.example.neuroflowplanner.service.ImageGenerationService;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.media.AiMediaTypeDescriptor;
import com.example.neuroflowplanner.ai.media.AiMediaTypeRegistry;
import com.example.neuroflowplanner.ai.media.AiModelMediaCapabilityPolicy;
import com.example.neuroflowplanner.service.chatio.ChatArchiveExportService;
import com.example.neuroflowplanner.service.chatio.ChatArchiveFormat;
import com.example.neuroflowplanner.service.chatflow.ChatRequestEvent;
import com.example.neuroflowplanner.service.chatflow.ChatRequestState;
import com.example.neuroflowplanner.service.chatflow.ChatRequestSubscription;
import com.example.neuroflowplanner.service.chatflow.ChatResponseChunk;
import com.example.neuroflowplanner.service.chatio.DefaultChatArchiveExportService;
import com.example.neuroflowplanner.service.context.ChatContextBuildResult;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationState;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationStatus;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;
import com.example.neuroflowplanner.service.imageflow.ImageRequestEvent;
import com.example.neuroflowplanner.service.imageflow.ImageRequestState;
import com.example.neuroflowplanner.service.imageflow.ImageRequestSubscription;
import com.example.neuroflowplanner.service.imagejob.ImageJobSnapshot;
import com.example.neuroflowplanner.service.imagejob.ImageJobState;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Inline chat bot view.
 */
public class ChatBotDialog implements InlineView {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ChatBotDialog.class);
    private static final String DEFAULT_CONVERSATION_TITLE = "Новая переписка";
    private static final double CHAT_HEIGHT_COMPACT_THRESHOLD = 720.0;
    private static final double CHAT_HEIGHT_VERY_COMPACT_THRESHOLD = 560.0;
    private static final String IMAGE_MESSAGE_PREFIX = "NEUROFLOW_IMAGE:";
    private static final String MEDIA_MESSAGE_PREFIX = "NEUROFLOW_MEDIA:";
    private static final String IMAGE_RESULT_MESSAGE_PREFIX = "NEUROFLOW_IMAGE_RESULT:";
    private static final String IMAGE_ERROR_MESSAGE_PREFIX = "NEUROFLOW_IMAGE_ERROR:";
    private static final String MODEL_MESSAGE_PREFIX = "NEUROFLOW_MODEL:";
    private static final ObjectMapper CHAT_MESSAGE_MAPPER = AiObjectMapperFactory.createMapper(false);
    private static final ExecutorService IMAGE_OPEN_EXECUTOR =
        Executors.newSingleThreadExecutor(AsyncContext.namedThreadFactory("chat-image-open", true));
    private static final ExecutorService CHAT_EXPORT_EXECUTOR =
        Executors.newSingleThreadExecutor(AsyncContext.namedThreadFactory("chat-export", true));
    private static final AtomicReference<ChatLaunchRequest> PENDING_LAUNCH_REQUEST = new AtomicReference<>();

    private final VBox messagesBox = new VBox(16);
    private final TextField inputField = new TextField();
    private final ChatBotService chatService = new ChatBotService();
    private final ScrollPane scrollPane;
    private final VBox root;
    private VBox inputArea;
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final ComboBox<ChatConversation> conversationCombo = new ComboBox<>();
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private ChatConversation currentConversation;
    private int nextMessageSeq = 0;
    private boolean suppressConversationChange = false;
    private final ChatArchiveExportService chatArchiveExportService = new DefaultChatArchiveExportService();
    private final CheckBox generateImageCheckBox = new CheckBox("Изображение");
    private final ImageGenerationService imageGenerationService = ImageGenerationService.getInstance();
    private final ComboBox<ChatContextMode> contextModeCombo = new ComboBox<>();
    private final Button contextPanelToggleButton = new Button("Контекст");
    private final Label contextUsageLabel = new Label("Контекст: —");
    private final Label contextModelValueLabel = new Label("—");
    private final Label contextWindowValueLabel = new Label("Размер контекста неизвестен");
    private final Label contextWindowHintLabel = new Label("estimate-only");
    private final ProgressBar contextBudgetProgressBar = new ProgressBar(0.0);
    private final Label contextSummaryStatusLabel = new Label("Статус: —");
    private final Tooltip contextSummaryStatusTooltip = new Tooltip();
    private final Button contextRebuildButton = new Button("Сжать контекст");
    private final Button contextClearButton = new Button("Очистить");
    private final Button contextPinButton = new Button("Закрепить факт");
    private final Tooltip contextUsageTooltip = new Tooltip();
    private VBox contextControlBar;
    private final HBox summarizeLockBanner = new HBox(10);
    private final Label summarizeLockBannerLabel = new Label("Сжимаем контекст. Новые сообщения временно заблокированы.");
    private boolean suppressContextModeChange = false;
    private boolean contextPanelExpanded = false;

    // Media upload (as input) support - External API mode only
    private final Button attachMediaButton = new Button();
    private final VBox attachedMediaBox = new VBox(6);
    private final Button clearAttachedMediaButton = new Button();
    private final List<Path> pendingMediaAttachments = new ArrayList<>();
    private final Map<String, UserMediaAttachmentPayload> runtimeMediaPayloadCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> runtimeMediaPayloadKeysByConversation = new ConcurrentHashMap<>();
    
    // Cancel request support
    private CompletableFuture<?> pendingRequest = null;
    private HBox currentTypingIndicator = null;
    private Button cancelButton;
    private Button sendButton;
    private ChatRequestSubscription requestSubscription;
    private ChatRequestSubscription responseChunkSubscription;
    private ImageRequestSubscription imageRequestSubscription;
    private String activeChatRequestId;
    private String activeImageJobId;
    private String activeImageRequestId;
    private boolean cancellationNoticeShown = false;
    private boolean summarizationInteractionLocked = false;
    private ChatRequestEvent latestRequestEvent;
    private ImageRequestEvent latestImageRequestEvent;
    private ImageJobSnapshot latestImageJobSnapshot;
    private Label currentImageGenerationLabel;
    private String currentImageIndicatorJobId;
    private final HBox lifecycleStatusBar = new HBox(10);
    private final Label lifecycleStatusLabel = new Label("Готово");
    private final Label lifecycleElapsedLabel = new Label("00:00");
    private final Label lifecycleAttemptLabel = new Label("Попытка 1/1");
    private final Label lifecycleDetailLabel = new Label("");
    private final ProgressIndicator lifecycleSpinner = new ProgressIndicator();
    private final ProgressBar lifecycleProgressBar = new ProgressBar(0.0);
    private final HBox lifecycleStepper = new HBox(5);
    private final List<Region> lifecycleStepDots = new ArrayList<>();
    private final Tooltip lifecycleTooltip = new Tooltip();
    private final Timeline lifecycleElapsedTimeline = new Timeline();
    private final PauseTransition lifecycleHideDelay = new PauseTransition(Duration.seconds(1.6));
    private final Set<String> lifecycleAnnouncements = new HashSet<>();
    private long lifecycleStartEpochMs = 0L;
    private String lifecyclePrimaryModel = "";
    private String lifecycleActiveModel = "";
    private int lifecycleLastAttempt = 1;
    private final HBox imageLifecycleStatusBar = new HBox(10);
    private final Label imageLifecycleTitleLabel = new Label("Изображение");
    private final Label imageLifecycleStatusLabel = new Label("Готово");
    private final Label imageLifecycleElapsedLabel = new Label("00:00");
    private final Label imageLifecycleAttemptLabel = new Label("Попытка 1/1");
    private final Label imageLifecycleDetailLabel = new Label("");
    private final ProgressIndicator imageLifecycleSpinner = new ProgressIndicator();
    private final ProgressBar imageLifecycleProgressBar = new ProgressBar(0.0);
    private final HBox imageLifecycleStepper = new HBox(5);
    private final List<Region> imageLifecycleStepDots = new ArrayList<>();
    private final Tooltip imageLifecycleTooltip = new Tooltip();
    private final Timeline imageLifecycleElapsedTimeline = new Timeline();
    private final PauseTransition imageLifecycleHideDelay = new PauseTransition(Duration.seconds(1.8));
    private final Set<String> imageLifecycleAnnouncements = new HashSet<>();
    private final Button imageLifecyclePrimaryActionButton = new Button();
    private final Button imageLifecycleSecondaryActionButton = new Button();
    private long imageLifecycleStartEpochMs = 0L;
    private String imageLifecyclePrimaryModel = "";
    private String imageLifecycleActiveModel = "";
    private int imageLifecycleLastAttempt = 1;
    private HBox streamingBotContainer;
    private Label streamingBotModelLabel;
    private WebView streamingBotWebView;
    private String streamingRequestId;
    private String streamingAccumulatedText = "";
    private String streamingModelName = "";
    private boolean streamingActive = false;
    private boolean streamingRenderDirty = false;
    private MenuButton exportMenuButton;
    private boolean chatExportInProgress = false;
    private final PauseTransition streamingRenderDebounce = new PauseTransition(Duration.millis(45));
    private final List<Button> summarizeBlockedActionButtons = new ArrayList<>();
    private final ChangeListener<Number> configRevisionListener = (obs, oldValue, newValue) ->
        Platform.runLater(this::refreshAssistantConfigurationView);
    private Label statusLabel;

    private ChatBotDialog() {
        root = new VBox(0);
        root.setMinSize(320, 0);
        root.getStyleClass().add("chat-root");

        // --- Header Wrapper (StackPane for background) ---
        StackPane headerContainer = new StackPane();
        headerContainer.getStyleClass().add("chat-header-panel");
        headerContainer.setMaxWidth(Double.MAX_VALUE);

        // --- Header Content (BorderPane) ---
        BorderPane headerContent = new BorderPane();
        headerContent.getStyleClass().add("chat-header-content");
        headerContent.setPadding(new Insets(16, 20, 16, 20));

        // Left side: Avatar + Title
        HBox leftContent = new HBox(12);
        leftContent.setAlignment(Pos.CENTER_LEFT);

        Node botImage = ChatBotAvatar.create(40);
        
        // Add a subtle effect to avatar
        StackPane avatarPane = new StackPane(botImage);
        avatarPane.getStyleClass().add("chat-avatar-container");

        VBox headerText = new VBox(2);
        Label title = new Label("ИИ-Ассистент");
        title.getStyleClass().add("chat-title");
        statusLabel = new Label("● Онлайн");
        statusLabel.getStyleClass().add("chat-status");
        headerText.getChildren().addAll(title, statusLabel);

        leftContent.getChildren().addAll(avatarPane, headerText);
        headerContent.setLeft(leftContent);

        // Right side intentionally left empty (no close button)

        headerContainer.getChildren().add(headerContent);
        root.getChildren().add(headerContainer);

        setupConversationBar();
        setupContextControlBar();
        setupLifecycleStatusBar();
        setupImageLifecycleStatusBar();

        // --- Messages Area ---
        messagesBox.setPadding(new Insets(20));
        messagesBox.getStyleClass().add("chat-messages-box");

        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("chat-scroll-pane");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMinHeight(0);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // --- Input Area ---
        inputArea = new VBox(8);
        inputArea.setAlignment(Pos.CENTER_LEFT);
        inputArea.setPadding(new Insets(15, 20, 20, 20));
        inputArea.getStyleClass().add("chat-input-area");

        generateImageCheckBox.getStyleClass().add("chat-image-checkbox");
        
        // Image generation is only available in External API mode
        updateImageCheckBoxAvailability();

        // Media upload button (multimodal input) - External API mode only
        setupMediaUploadControls();
        installMediaDragAndDrop(inputArea);
        updateMediaUploadAvailability();
        generateImageCheckBox.selectedProperty().addListener((obs, oldValue, selected) -> {
            if (selected) {
                clearAttachedMedia();
            }
            updateMediaUploadAvailability();
        });

        inputField.setPromptText("Задайте вопрос или опишите задачу...");
        inputField.getStyleClass().add("chat-input-field");
        inputField.setOnAction(e -> sendMessage());
        inputField.setContextMenu(createRussianContextMenu(inputField));
        HBox.setHgrow(inputField, Priority.ALWAYS);

        HBox inputRow = new HBox(12);
        inputRow.setAlignment(Pos.CENTER);

        // Send button
        sendButton = new Button();
        FontIcon sendIcon = FontIcon.of(MaterialDesignS.SEND, 18);
        sendButton.setGraphic(sendIcon);
        sendButton.getStyleClass().add("chat-send-btn");
        sendButton.setOnAction(e -> sendMessage());
        
        // Cancel button (initially hidden)
        cancelButton = new Button();
        FontIcon cancelIcon = FontIcon.of(MaterialDesignC.CLOSE, 18);
        cancelButton.setGraphic(cancelIcon);
        cancelButton.getStyleClass().add("chat-cancel-btn");
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        cancelButton.setOnAction(e -> cancelPendingRequest());
        Tooltip.install(cancelButton, new Tooltip("Отменить запрос"));

        inputRow.getChildren().addAll(
            attachMediaButton,
            clearAttachedMediaButton,
            generateImageCheckBox,
            inputField,
            sendButton,
            cancelButton
        );
        inputArea.getChildren().addAll(attachedMediaBox, inputRow);

        setupSummarizeLockBanner();
        root.getChildren().addAll(scrollPane, summarizeLockBanner, inputArea);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        requestSubscription = chatService.subscribeToRequestEvents(this::onChatRequestEvent);
        responseChunkSubscription = chatService.subscribeToResponseChunks(this::onChatResponseChunk);
        imageRequestSubscription = imageGenerationService.subscribeToRequestEvents(this::onImageRequestEvent);
        ConfigManager.configRevisionProperty().addListener(configRevisionListener);
        streamingRenderDebounce.setOnFinished(e -> flushStreamingWebViewRender());
        loadConversations();
        applyPendingLaunchRequestIfPresent();
        refreshAssistantConfigurationView();
        installAdaptiveLayout();
    }

    public static InlineView inline() {
        return new ChatBotDialog();
    }

    public static void queueLaunchRequest(String conversationTitle, String initialPrompt) {
        String safePrompt = initialPrompt == null ? "" : initialPrompt.trim();
        if (safePrompt.isBlank()) {
            return;
        }
        String safeTitle = conversationTitle == null || conversationTitle.isBlank()
                ? DEFAULT_CONVERSATION_TITLE
                : conversationTitle.trim();
        PENDING_LAUNCH_REQUEST.set(new ChatLaunchRequest(safeTitle, safePrompt));
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
        return "ИИ-Ассистент";
    }

    public void applyPendingLaunchRequestIfPresent() {
        ChatLaunchRequest request = PENDING_LAUNCH_REQUEST.getAndSet(null);
        if (request == null) {
            return;
        }
        openSeededConversation(request);
    }

    @Override
    public void onDispose() {
        ConfigManager.configRevisionProperty().removeListener(configRevisionListener);
        if (requestSubscription != null) {
            requestSubscription.close();
            requestSubscription = null;
        }
        if (responseChunkSubscription != null) {
            responseChunkSubscription.close();
            responseChunkSubscription = null;
        }
        if (imageRequestSubscription != null) {
            imageRequestSubscription.close();
            imageRequestSubscription = null;
        }
        lifecycleElapsedTimeline.stop();
        lifecycleHideDelay.stop();
        imageLifecycleElapsedTimeline.stop();
        imageLifecycleHideDelay.stop();
        streamingRenderDebounce.stop();
        lifecycleAnnouncements.clear();
        imageLifecycleAnnouncements.clear();
        resetLifecycleVisualState();
        resetImageLifecycleVisualState();
        resetStreamingMessageState();
        if (pendingRequest != null && !pendingRequest.isDone()) {
            if (activeImageJobId != null && !activeImageJobId.isBlank()) {
                imageGenerationService.pauseJob(activeImageJobId);
            } else {
                pendingRequest.cancel(true);
            }
            pendingRequest = null;
        }
        activeChatRequestId = null;
        activeImageJobId = null;
        activeImageRequestId = null;
        clearRuntimeMediaPayloadCache();
        chatService.clearHistory();
    }

    private void installAdaptiveLayout() {
        root.parentProperty().addListener((obs, oldParent, newParent) -> refreshAdaptiveLayout());
        root.sceneProperty().addListener((obs, oldScene, newScene) -> refreshAdaptiveLayout());
        root.heightProperty().addListener((obs, oldHeight, newHeight) -> refreshAdaptiveLayout());
        root.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> refreshAdaptiveLayout());
        Platform.runLater(this::refreshAdaptiveLayout);
    }

    private void refreshAdaptiveLayout() {
        Node overlayContainer = findAncestorWithStyleClass(root, "overlay-container");
        double hostHeight = resolveAdaptiveHostHeight(overlayContainer);

        boolean compactHeight = hasStyleClass(overlayContainer, "inline-overlay-height-low")
            || (hostHeight > 0.0 && hostHeight < CHAT_HEIGHT_COMPACT_THRESHOLD);
        boolean veryCompactHeight = hasStyleClass(overlayContainer, "inline-overlay-height-very-low")
            || (hostHeight > 0.0 && hostHeight < CHAT_HEIGHT_VERY_COMPACT_THRESHOLD);

        setStyleClassPresent(root, "chat-root-height-compact", compactHeight);
        setStyleClassPresent(root, "chat-root-height-very-compact", veryCompactHeight);

        if (statusLabel != null) {
            statusLabel.setVisible(!veryCompactHeight);
            statusLabel.setManaged(!veryCompactHeight);
        }
    }

    private double resolveAdaptiveHostHeight(Node overlayContainer) {
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

    private void setStyleClassPresent(Node node, String styleClass, boolean present) {
        if (node == null || styleClass == null || styleClass.isBlank()) {
            return;
        }
        if (present) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private void setupConversationBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 20, 10, 20));
        bar.getStyleClass().add("chat-conversation-bar");

        conversationCombo.setPromptText("Выберите переписку");
        conversationCombo.getStyleClass().add("ai-combo-box");
        conversationCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(conversationCombo, Priority.ALWAYS);

        conversationCombo.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(ChatConversation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle());
            }
        });
        conversationCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ChatConversation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getTitle());
            }
        });

        Button newChatBtn = new Button();
        FontIcon plusIcon = FontIcon.of(MaterialDesignP.PLUS_CIRCLE_OUTLINE, 16);
        newChatBtn.setGraphic(plusIcon);
        newChatBtn.getStyleClass().add("chat-new-btn");
        newChatBtn.setTooltip(new Tooltip("Новая переписка"));
        newChatBtn.setOnAction(e -> createNewConversation());

        Button renameBtn = new Button();
        FontIcon renameIcon = FontIcon.of(MaterialDesignP.PENCIL_OUTLINE, 16);
        renameBtn.setGraphic(renameIcon);
        renameBtn.getStyleClass().add("chat-rename-btn");
        renameBtn.setTooltip(new Tooltip("Переименовать переписку"));
        renameBtn.setOnAction(e -> requestRenameCurrentConversation());

        exportMenuButton = createConversationExportMenu();

        Button deleteBtn = new Button();
        FontIcon deleteIcon = FontIcon.of(MaterialDesignD.DELETE_OUTLINE, 16);
        deleteBtn.setGraphic(deleteIcon);
        deleteBtn.getStyleClass().add("chat-delete-btn");
        deleteBtn.setTooltip(new Tooltip("Удалить переписку"));
        deleteBtn.setOnAction(e -> requestDeleteCurrentConversation());

        contextPanelToggleButton.getStyleClass().add("chat-context-toggle-btn");
        contextPanelToggleButton.setTooltip(new Tooltip("Показать менеджер контекста"));
        contextPanelToggleButton.setOnAction(e -> setContextPanelExpanded(!contextPanelExpanded));
        updateContextPanelToggleButton();

        conversationCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressConversationChange) {
                return;
            }
            if (newVal != null) {
                loadConversation(newVal);
            }
        });

        bar.getChildren().addAll(
            conversationCombo,
            contextPanelToggleButton,
            newChatBtn,
            renameBtn,
            exportMenuButton,
            deleteBtn
        );
        root.getChildren().add(bar);
    }

    private MenuButton createConversationExportMenu() {
        MenuButton button = new MenuButton("Экспорт");
        FontIcon exportIcon = FontIcon.of(MaterialDesignF.FILE_EXPORT_OUTLINE, 16);
        button.setGraphic(exportIcon);
        button.getStyleClass().add("chat-export-menu");
        button.setTooltip(new Tooltip("Экспорт текущей переписки"));
        applyExportMenuTheme(button, exportIcon);

        MenuItem pdfItem = new MenuItem("PDF (.pdf)");
        pdfItem.getStyleClass().add("chat-archive-menu-item");
        pdfItem.setOnAction(event -> exportCurrentConversation(ChatArchiveFormat.PDF));

        MenuItem markdownItem = new MenuItem("Markdown (.md)");
        markdownItem.getStyleClass().add("chat-archive-menu-item");
        markdownItem.setOnAction(event -> exportCurrentConversation(ChatArchiveFormat.MARKDOWN));

        MenuItem jsonItem = new MenuItem("JSON (.json)");
        jsonItem.getStyleClass().add("chat-archive-menu-item");
        jsonItem.setOnAction(event -> exportCurrentConversation(ChatArchiveFormat.JSON));

        button.getItems().setAll(pdfItem, markdownItem, jsonItem);
        return button;
    }

    private void applyExportMenuTheme(MenuButton button, FontIcon icon) {
        if (button == null) {
            return;
        }
        boolean darkTheme = ConfigManager.isDarkTheme();
        Color bg = darkTheme ? Color.web("#313244") : Color.web("#1e66f5");
        Color border = darkTheme ? Color.web("rgba(137, 180, 250, 0.18)") : Color.TRANSPARENT;
        Color text = darkTheme ? Color.web("#cdd6f4") : Color.web("#eff1f5");
        button.setBackground(new Background(new BackgroundFill(bg, new CornerRadii(999), Insets.EMPTY)));
        button.setBorder(new Border(new BorderStroke(
            border,
            BorderStrokeStyle.SOLID,
            new CornerRadii(999),
            darkTheme ? new BorderWidths(1) : BorderStroke.THIN
        )));
        button.setTextFill(text);
        if (icon != null) {
            icon.setIconColor(text);
        }
    }

    private void setupContextControlBar() {
        contextControlBar = new VBox(6);
        contextControlBar.setAlignment(Pos.CENTER_LEFT);
        contextControlBar.setPadding(new Insets(6, 12, 6, 12));
        contextControlBar.getStyleClass().add("chat-context-bar");

        HBox cardsRow = new HBox(6);
        cardsRow.setAlignment(Pos.CENTER_LEFT);
        cardsRow.getStyleClass().add("chat-context-cards-row");

        VBox modelCard = new VBox(2);
        modelCard.getStyleClass().addAll("chat-context-card", "chat-context-model-card");
        HBox.setHgrow(modelCard, Priority.ALWAYS);
        Label modelCardTitle = new Label("Модель");
        modelCardTitle.getStyleClass().add("chat-context-card-title");
        contextModelValueLabel.getStyleClass().add("chat-context-model-value");
        contextModelValueLabel.setMaxWidth(Double.MAX_VALUE);
        contextModelValueLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        modelCard.getChildren().addAll(modelCardTitle, contextModelValueLabel);

        VBox windowCard = new VBox(2);
        windowCard.getStyleClass().addAll("chat-context-card", "chat-context-window-card");
        HBox.setHgrow(windowCard, Priority.ALWAYS);
        Label windowCardTitle = new Label("Окно");
        windowCardTitle.getStyleClass().add("chat-context-card-title");
        contextWindowValueLabel.getStyleClass().add("chat-context-window-value");
        contextWindowValueLabel.setMaxWidth(Double.MAX_VALUE);
        contextWindowValueLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        contextWindowHintLabel.getStyleClass().add("chat-context-window-hint");
        contextWindowHintLabel.setManaged(false);
        contextWindowHintLabel.setVisible(false);
        windowCard.getChildren().addAll(windowCardTitle, contextWindowValueLabel, contextWindowHintLabel);

        cardsRow.getChildren().addAll(modelCard, windowCard);

        VBox usageBox = new VBox(4);
        usageBox.getStyleClass().add("chat-context-usage-box");
        contextBudgetProgressBar.getStyleClass().add("chat-context-progress");
        contextBudgetProgressBar.setMaxWidth(Double.MAX_VALUE);
        contextBudgetProgressBar.setPrefWidth(Double.MAX_VALUE);
        contextUsageLabel.getStyleClass().add("chat-context-usage");
        contextUsageLabel.setMaxWidth(Double.MAX_VALUE);
        contextUsageTooltip.setWrapText(true);
        contextUsageTooltip.setShowDelay(Duration.millis(120));
        Tooltip.install(contextUsageLabel, contextUsageTooltip);
        Tooltip.install(contextBudgetProgressBar, contextUsageTooltip);

        contextSummaryStatusLabel.getStyleClass().add("chat-context-summary-status");
        contextSummaryStatusLabel.setMaxWidth(Double.MAX_VALUE);
        contextSummaryStatusLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        Tooltip.install(contextSummaryStatusLabel, contextSummaryStatusTooltip);
        usageBox.getChildren().addAll(contextBudgetProgressBar, contextUsageLabel, contextSummaryStatusLabel);

        contextModeCombo.getItems().setAll(ChatContextMode.values());
        contextModeCombo.getStyleClass().add("chat-context-mode-combo");
        contextModeCombo.setVisibleRowCount(4);
        contextModeCombo.setPrefWidth(108);
        contextModeCombo.setTooltip(new Tooltip("Режим отбора контекста"));
        contextModeCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatContextMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : toContextModeLabel(item));
            }
        });
        contextModeCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ChatContextMode item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : toContextModeLabel(item));
            }
        });
        contextModeCombo.setValue(ChatContextMode.AUTO);
        contextModeCombo.valueProperty().addListener((obs, oldMode, newMode) -> {
            if (suppressContextModeChange) {
                return;
            }
            if (currentConversation == null || newMode == null) {
                return;
            }
            chatService.setContextMode(currentConversation.getId(), newMode);
            refreshContextUsageIndicator();
        });

        contextRebuildButton.setText("Сжать");
        contextRebuildButton.getStyleClass().addAll("chat-context-btn", "chat-context-rebuild-btn");
        contextRebuildButton.setOnAction(e -> handleContextRebuild());
        contextRebuildButton.setTooltip(new Tooltip("Запустить сжатие контекста вручную"));

        contextClearButton.setText("Очистить");
        contextClearButton.getStyleClass().addAll("chat-context-btn", "chat-context-clear-btn");
        contextClearButton.setOnAction(e -> handleContextClear());
        contextClearButton.setTooltip(new Tooltip("Очистить контекст текущей переписки"));

        contextPinButton.setText("Факт");
        contextPinButton.getStyleClass().addAll("chat-context-btn", "chat-context-pin-btn");
        contextPinButton.setOnAction(e -> handleContextPinFact());
        contextPinButton.setTooltip(new Tooltip("Закрепить важный факт в контексте"));

        Region controlsSpacer = new Region();
        HBox.setHgrow(controlsSpacer, Priority.ALWAYS);

        cardsRow.getChildren().addAll(
            controlsSpacer,
            contextModeCombo,
            contextPinButton,
            contextRebuildButton,
            contextClearButton
        );
        contextControlBar.getChildren().addAll(cardsRow, usageBox);
        setContextControlsEnabled(false);
        applyContextPanelExpandedState(ConfigManager.isChatContextPanelExpanded(), false);
        root.getChildren().add(contextControlBar);
    }

    private void setContextPanelExpanded(boolean expanded) {
        applyContextPanelExpandedState(expanded, true);
    }

    private void updateContextPanelToggleButton() {
        FontIcon icon = FontIcon.of(
            contextPanelExpanded ? MaterialDesignC.CHEVRON_DOWN : MaterialDesignC.CHEVRON_RIGHT,
            16
        );
        contextPanelToggleButton.setGraphic(icon);
        contextPanelToggleButton.getStyleClass().remove("expanded");
        if (contextPanelExpanded) {
            contextPanelToggleButton.getStyleClass().add("expanded");
        }
        Tooltip tooltip = contextPanelToggleButton.getTooltip();
        if (tooltip != null) {
            tooltip.setText(contextPanelExpanded
                ? "Скрыть менеджер контекста"
                : "Показать менеджер контекста");
        }
    }

    private void applyContextPanelExpandedState(boolean expanded, boolean persist) {
        contextPanelExpanded = expanded;
        if (contextControlBar != null) {
            contextControlBar.setVisible(expanded);
            contextControlBar.setManaged(expanded);
        }
        if (persist) {
            ConfigManager.setChatContextPanelExpanded(expanded);
        }
        updateContextPanelToggleButton();
    }

    private void refreshAssistantConfigurationView() {
        updateImageCheckBoxAvailability();
        updateMediaUploadAvailability();
        applyExportMenuTheme(exportMenuButton, exportMenuButton != null && exportMenuButton.getGraphic() instanceof FontIcon fontIcon
            ? fontIcon
            : null);
        applyContextPanelExpandedState(ConfigManager.isChatContextPanelExpanded(), false);
        refreshContextUsageIndicator();
    }

    private void setupLifecycleStatusBar() {
        lifecycleStatusBar.getStyleClass().add("chat-lifecycle-bar");
        lifecycleStatusBar.setAlignment(Pos.CENTER_LEFT);
        lifecycleStatusBar.setPadding(new Insets(6, 16, 6, 16));
        lifecycleStatusBar.setVisible(false);
        lifecycleStatusBar.setManaged(false);

        lifecycleSpinner.setPrefSize(14, 14);
        lifecycleSpinner.getStyleClass().add("chat-lifecycle-spinner");
        lifecycleSpinner.setMaxSize(14, 14);

        lifecycleProgressBar.getStyleClass().add("chat-lifecycle-progress");
        lifecycleProgressBar.setPrefWidth(96);
        lifecycleProgressBar.setMaxWidth(96);
        lifecycleProgressBar.setProgress(0.0);

        lifecycleStatusLabel.getStyleClass().add("chat-lifecycle-status-text");
        lifecycleElapsedLabel.getStyleClass().add("chat-lifecycle-elapsed");
        lifecycleAttemptLabel.getStyleClass().add("chat-lifecycle-attempt");
        lifecycleDetailLabel.getStyleClass().add("chat-lifecycle-detail");
        lifecycleDetailLabel.setMaxWidth(Double.MAX_VALUE);
        lifecycleDetailLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(lifecycleDetailLabel, Priority.ALWAYS);

        lifecycleStepper.getStyleClass().add("chat-lifecycle-stepper");
        for (int i = 0; i < 6; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("chat-lifecycle-step-dot");
            lifecycleStepDots.add(dot);
            lifecycleStepper.getChildren().add(dot);
        }

        lifecycleTooltip.setWrapText(true);
        lifecycleTooltip.setShowDelay(Duration.millis(120));
        Tooltip.install(lifecycleStatusBar, lifecycleTooltip);
        Tooltip.install(lifecycleStatusLabel, lifecycleTooltip);
        Tooltip.install(lifecycleDetailLabel, lifecycleTooltip);

        lifecycleElapsedTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> refreshElapsedClock()));
        lifecycleElapsedTimeline.setCycleCount(Timeline.INDEFINITE);

        lifecycleHideDelay.setOnFinished(e -> setLifecycleStatusVisible(false));

        lifecycleStatusBar.getChildren().addAll(
            lifecycleSpinner,
            lifecycleStepper,
            lifecycleProgressBar,
            lifecycleStatusLabel,
            lifecycleElapsedLabel,
            lifecycleAttemptLabel,
            lifecycleDetailLabel
        );
        root.getChildren().add(lifecycleStatusBar);
    }

    private void setupImageLifecycleStatusBar() {
        imageLifecycleStatusBar.getStyleClass().addAll("chat-lifecycle-bar", "chat-lifecycle-bar-image");
        imageLifecycleStatusBar.setAlignment(Pos.CENTER_LEFT);
        imageLifecycleStatusBar.setPadding(new Insets(6, 16, 6, 16));
        imageLifecycleStatusBar.setVisible(false);
        imageLifecycleStatusBar.setManaged(false);

        imageLifecycleTitleLabel.getStyleClass().addAll("chat-lifecycle-status-text", "chat-lifecycle-title-image");

        imageLifecycleSpinner.setPrefSize(14, 14);
        imageLifecycleSpinner.getStyleClass().addAll("chat-lifecycle-spinner", "chat-lifecycle-spinner-image");
        imageLifecycleSpinner.setMaxSize(14, 14);

        imageLifecycleProgressBar.getStyleClass().addAll("chat-lifecycle-progress", "chat-lifecycle-progress-image");
        imageLifecycleProgressBar.setPrefWidth(112);
        imageLifecycleProgressBar.setMaxWidth(112);
        imageLifecycleProgressBar.setProgress(0.0);

        imageLifecycleStatusLabel.getStyleClass().addAll("chat-lifecycle-status-text", "chat-lifecycle-status-text-image");
        imageLifecycleElapsedLabel.getStyleClass().add("chat-lifecycle-elapsed");
        imageLifecycleAttemptLabel.getStyleClass().addAll("chat-lifecycle-attempt", "chat-lifecycle-attempt-image");
        imageLifecycleDetailLabel.getStyleClass().add("chat-lifecycle-detail");
        imageLifecycleDetailLabel.setMaxWidth(Double.MAX_VALUE);
        imageLifecycleDetailLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(imageLifecycleDetailLabel, Priority.ALWAYS);

        imageLifecyclePrimaryActionButton.getStyleClass().add("chat-inline-cancel-btn");
        imageLifecyclePrimaryActionButton.setVisible(false);
        imageLifecyclePrimaryActionButton.setManaged(false);
        imageLifecyclePrimaryActionButton.setOnAction(e -> handlePrimaryImageJobAction());

        imageLifecycleSecondaryActionButton.getStyleClass().add("chat-inline-cancel-btn");
        imageLifecycleSecondaryActionButton.setVisible(false);
        imageLifecycleSecondaryActionButton.setManaged(false);
        imageLifecycleSecondaryActionButton.setOnAction(e -> handleSecondaryImageJobAction());

        imageLifecycleStepper.getStyleClass().add("chat-lifecycle-stepper");
        for (int i = 0; i < 7; i++) {
            Region dot = new Region();
            dot.getStyleClass().add("chat-lifecycle-step-dot");
            imageLifecycleStepDots.add(dot);
            imageLifecycleStepper.getChildren().add(dot);
        }

        imageLifecycleTooltip.setWrapText(true);
        imageLifecycleTooltip.setShowDelay(Duration.millis(120));
        Tooltip.install(imageLifecycleStatusBar, imageLifecycleTooltip);
        Tooltip.install(imageLifecycleTitleLabel, imageLifecycleTooltip);
        Tooltip.install(imageLifecycleStatusLabel, imageLifecycleTooltip);
        Tooltip.install(imageLifecycleDetailLabel, imageLifecycleTooltip);

        imageLifecycleElapsedTimeline.getKeyFrames().add(new KeyFrame(Duration.seconds(1), e -> refreshImageElapsedClock()));
        imageLifecycleElapsedTimeline.setCycleCount(Timeline.INDEFINITE);

        imageLifecycleHideDelay.setOnFinished(e -> setImageLifecycleStatusVisible(false));

        imageLifecycleStatusBar.getChildren().addAll(
            imageLifecycleTitleLabel,
            imageLifecycleSpinner,
            imageLifecycleStepper,
            imageLifecycleProgressBar,
            imageLifecycleStatusLabel,
            imageLifecycleElapsedLabel,
            imageLifecycleAttemptLabel,
            imageLifecycleDetailLabel,
            imageLifecyclePrimaryActionButton,
            imageLifecycleSecondaryActionButton
        );
        root.getChildren().add(imageLifecycleStatusBar);
    }

    private void requestRenameCurrentConversation() {
        if (currentConversation == null) {
            showChatAlert(Alert.AlertType.WARNING, "Переименование переписки", "Выберите переписку для переименования.");
            return;
        }

        boolean isDark = ConfigManager.isDarkTheme();
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Переименовать переписку");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().addAll("styled-alert", "chat-rename-dialog");
        dialogPane.setPrefWidth(460);

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("chat-rename-icon-box");
        FontIcon icon = FontIcon.of(MaterialDesignP.PENCIL_OUTLINE, 34);
        icon.getStyleClass().add("chat-rename-icon");
        iconBox.getChildren().add(icon);

        Label titleLbl = new Label("Переименовать переписку");
        titleLbl.getStyleClass().add("chat-rename-title");

        TextField titleField = new TextField();
        titleField.getStyleClass().add("chat-rename-field");
        titleField.setPromptText("Введите название");
        titleField.setText(currentConversation.getTitle() != null ? currentConversation.getTitle().trim() : "");
        titleField.setMaxWidth(Double.MAX_VALUE);

        Label hint = new Label("Название отображается в списке переписок и при экспорте.");
        hint.getStyleClass().add("chat-rename-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        hint.setAlignment(Pos.CENTER);

        content.getChildren().addAll(iconBox, titleLbl, titleField, hint);
        dialogPane.setContent(content);

        ButtonType saveBtn = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(saveBtn, cancelBtn);

        Button saveButton = (Button) dialogPane.lookupButton(saveBtn);
        saveButton.getStyleClass().add("chat-rename-save-btn");
        saveButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> titleField.getText() == null || titleField.getText().trim().isEmpty(),
            titleField.textProperty()
        ));
        saveButton.setDefaultButton(true);

        Button cancelButton = (Button) dialogPane.lookupButton(cancelBtn);
        cancelButton.getStyleClass().add("chat-rename-cancel-btn");

        dialog.setResultConverter(result -> result == saveBtn ? titleField.getText() : null);
        Platform.runLater(() -> {
            titleField.requestFocus();
            titleField.selectAll();
        });

        dialog.showAndWait().ifPresent(newTitle -> {
            String sanitized = sanitizeTitle(newTitle, DEFAULT_CONVERSATION_TITLE);
            if (currentConversation != null && currentConversation.getId() != null) {
                updateConversationTitle(currentConversation.getId(), sanitized);
            }
        });
    }

    private void requestDeleteCurrentConversation() {
        if (currentConversation == null) {
            showChatAlert(Alert.AlertType.WARNING, "Удаление переписки", "Выберите переписку для удаления.");
            return;
        }

        ChatConversation toDelete = currentConversation;
        boolean confirmed = showDeleteConversationConfirmDialog(toDelete);
        if (!confirmed) {
            return;
        }

        int previousIndex = conversationCombo.getItems().indexOf(toDelete);
        db.deleteChatConversation(toDelete.getId());
        removeRuntimeMediaPayloadsForConversation(toDelete.getId());

        suppressConversationChange = true;
        conversationCombo.getItems().removeIf(c -> c != null && toDelete.getId().equals(c.getId()));

        ChatConversation next;
        if (!conversationCombo.getItems().isEmpty()) {
            int nextIndex = previousIndex;
            if (nextIndex < 0) {
                nextIndex = 0;
            }
            if (nextIndex >= conversationCombo.getItems().size()) {
                nextIndex = conversationCombo.getItems().size() - 1;
            }
            next = conversationCombo.getItems().get(nextIndex);
        } else {
            next = db.createChatConversation(DEFAULT_CONVERSATION_TITLE);
            conversationCombo.getItems().add(next);
        }

        conversationCombo.setValue(next);
        suppressConversationChange = false;
        loadConversation(next);
    }

    private boolean showDeleteConversationConfirmDialog(ChatConversation conversation) {
        boolean isDark = ConfigManager.isDarkTheme();
        int messageCount = db.countChatMessages(conversation.getId());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Удаление переписки");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().addAll("styled-alert", "chat-delete-dialog");
        dialogPane.setPrefWidth(460);

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("chat-delete-icon-box");
        FontIcon warningIcon = FontIcon.of(MaterialDesignD.DELETE_ALERT, 34);
        warningIcon.getStyleClass().add("chat-delete-icon");
        iconBox.getChildren().add(warningIcon);

        Label titleLbl = new Label("Удалить переписку?");
        titleLbl.getStyleClass().add("chat-delete-title");

        String conversationTitle = conversation.getTitle() != null ? conversation.getTitle().trim() : "";
        if (conversationTitle.isEmpty()) {
            conversationTitle = DEFAULT_CONVERSATION_TITLE;
        }
        Label conversationTitleLbl = new Label("«" + conversationTitle + "»");
        conversationTitleLbl.getStyleClass().add("chat-delete-subtitle");
        conversationTitleLbl.setWrapText(true);
        conversationTitleLbl.setMaxWidth(380);
        conversationTitleLbl.setAlignment(Pos.CENTER);

        VBox warningBox = new VBox(6);
        warningBox.setAlignment(Pos.CENTER);
        warningBox.getStyleClass().add("chat-delete-warning-box");

        Label warningLbl = new Label("Это действие нельзя отменить.");
        warningLbl.getStyleClass().add("chat-delete-warning-text");
        Label countLbl = new Label("Будут удалены все сообщения (" + messageCount + ").");
        countLbl.getStyleClass().add("chat-delete-warning-subtext");
        warningBox.getChildren().addAll(warningLbl, countLbl);

        content.getChildren().addAll(iconBox, titleLbl, conversationTitleLbl, warningBox);
        dialogPane.setContent(content);

        ButtonType deleteBtn = new ButtonType("Удалить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(deleteBtn, cancelBtn);

        Button deleteButton = (Button) dialogPane.lookupButton(deleteBtn);
        deleteButton.getStyleClass().add("chat-delete-danger-btn");
        deleteButton.setDefaultButton(false);

        Button cancelButton = (Button) dialogPane.lookupButton(cancelBtn);
        cancelButton.getStyleClass().add("chat-delete-cancel-btn");
        cancelButton.setDefaultButton(true);

        return dialog.showAndWait().filter(result -> result == deleteBtn).isPresent();
    }

    private void exportCurrentConversation(ChatArchiveFormat format) {
        if (chatExportInProgress) {
            showChatAlert(Alert.AlertType.INFORMATION, "Экспорт переписки", "Экспорт уже выполняется.");
            return;
        }
        ChatConversation conversation = currentConversation;
        if (conversation == null) {
            showChatAlert(Alert.AlertType.WARNING, "Экспорт переписки", "Выберите переписку для экспорта.");
            return;
        }
        File file = chooseConversationExportFile(conversation, format);
        if (file == null) {
            return;
        }
        updateConversationExportState(true, format);
        AsyncContext.runAsync(
            () -> {
                try {
                    chatArchiveExportService.exportConversation(file, format, conversation.getId());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            },
            CHAT_EXPORT_EXECUTOR
        ).thenRun(() -> Platform.runLater(() -> {
            updateConversationExportState(false, format);
            showChatAlert(
                Alert.AlertType.INFORMATION,
                "Экспорт завершён",
                exportFormatSuccessLabel(format) + " сохранён: " + file.getName()
            );
        })).exceptionally(error -> {
            Throwable actual = AsyncContext.unwrap(error);
            LOG.error(
                "chat.conversation.export.failed",
                exportFormatErrorCode(format),
                actual,
                "operation", exportOperationName(format),
                "conversationId", conversation.getId(),
                "fileName", file.getName(),
                "format", format.name()
            );
            Platform.runLater(() -> {
                updateConversationExportState(false, format);
                UiErrorNotifier.showMappedError(
                    ownerWindow(),
                    isDark,
                    "Ошибка экспорта " + exportFormatDisplayName(format),
                    actual,
                    exportFormatErrorCode(format),
                    "Не удалось экспортировать переписку в " + exportFormatDisplayName(format) + ".",
                    false,
                    "operation", exportOperationName(format),
                    "conversationId", conversation.getId(),
                    "fileName", file.getName(),
                    "format", format.name()
                );
            });
            return null;
        });
    }

    private File chooseConversationExportFile(ChatConversation conversation, ChatArchiveFormat format) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт переписки в " + exportFormatDisplayName(format));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            exportFormatDisplayName(format) + " (*" + format.defaultExtension() + ")",
            "*" + format.defaultExtension()
        ));
        chooser.setInitialFileName(buildConversationExportFileName(conversation, format));
        return chooser.showSaveDialog(ownerWindow());
    }

    private String buildConversationExportFileName(ChatConversation conversation, ChatArchiveFormat format) {
        String name = sanitizeFileName(conversation == null ? null : conversation.getTitle());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        return "neuroflow_chat_" + name + "_" + timestamp + format.defaultExtension();
    }

    private void updateConversationExportState(boolean exporting, ChatArchiveFormat format) {
        chatExportInProgress = exporting;
        if (exportMenuButton == null) {
            return;
        }
        exportMenuButton.setDisable(exporting);
        exportMenuButton.setText(exporting ? "Экспорт..." : "Экспорт");
        Tooltip tooltip = exportMenuButton.getTooltip();
        if (tooltip != null) {
            tooltip.setText(exporting
                ? "Идёт экспорт в " + exportFormatDisplayName(format)
                : "Экспорт текущей переписки");
        }
    }

    private String exportFormatDisplayName(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "PDF";
            case MARKDOWN -> "Markdown";
            case JSON -> "JSON";
        };
    }

    private String exportFormatSuccessLabel(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "PDF";
            case MARKDOWN -> "Markdown";
            case JSON -> "JSON-архив";
        };
    }

    private ErrorCode exportFormatErrorCode(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> ErrorCode.EXPORT_PDF_FAILED;
            case MARKDOWN -> ErrorCode.EXPORT_MARKDOWN_FAILED;
            case JSON -> ErrorCode.EXPORT_JSON_FAILED;
        };
    }

    private String exportOperationName(ChatArchiveFormat format) {
        return switch (format) {
            case PDF -> "exportConversationPdf";
            case MARKDOWN -> "exportConversationMarkdown";
            case JSON -> "exportConversationJson";
        };
    }

    private void showChatAlert(Alert.AlertType type, String title, String message) {
        if (type == Alert.AlertType.ERROR) {
            UiErrorNotifier.showMappedError(
                ownerWindow(),
                isDark,
                title,
                null,
                ErrorCode.UNEXPECTED_ERROR,
                message,
                false,
                "operation", "chat.showAlert"
            );
            return;
        }
        if (type == Alert.AlertType.WARNING) {
            UiErrorNotifier.showWarning(ownerWindow(), isDark, title, message);
            return;
        }
        UiErrorNotifier.showInfo(ownerWindow(), isDark, title, message);
    }

    private javafx.stage.Window ownerWindow() {
        return root.getScene() != null ? root.getScene().getWindow() : null;
    }

    private String sanitizeFileName(String name) {
        String safe = name == null ? "chat" : name.replaceAll("[^a-zA-Z0-9а-яА-Я _-]", "").trim();
        return safe.isEmpty() ? "chat" : safe;
    }

    private void loadConversations() {
        List<ChatConversation> conversations = db.loadChatConversations();
        suppressConversationChange = true;
        conversationCombo.getItems().setAll(conversations);
        if (!conversations.isEmpty()) {
            currentConversation = conversations.get(0);
            conversationCombo.setValue(currentConversation);
        } else {
            currentConversation = db.createChatConversation(DEFAULT_CONVERSATION_TITLE);
            conversationCombo.getItems().add(currentConversation);
            conversationCombo.setValue(currentConversation);
        }
        suppressConversationChange = false;
        loadConversation(currentConversation);
    }

    private void createNewConversation() {
        ChatConversation conversation = db.createChatConversation(DEFAULT_CONVERSATION_TITLE);
        suppressConversationChange = true;
        conversationCombo.getItems().add(0, conversation);
        conversationCombo.setValue(conversation);
        suppressConversationChange = false;
        loadConversation(conversation);
    }

    private void openSeededConversation(ChatLaunchRequest request) {
        if (request == null || request.initialPrompt().isBlank()) {
            return;
        }
        ChatConversation conversation = db.createChatConversation(request.conversationTitle());
        if (!DEFAULT_CONVERSATION_TITLE.equals(request.conversationTitle())) {
            db.updateChatConversationTitle(conversation.getId(), request.conversationTitle());
            conversation.setTitle(request.conversationTitle());
        }
        suppressConversationChange = true;
        conversationCombo.getItems().add(0, conversation);
        conversationCombo.setValue(conversation);
        suppressConversationChange = false;
        loadConversation(conversation);
        clearAttachedMedia();
        generateImageCheckBox.setSelected(false);
        inputField.setText(request.initialPrompt());
        inputField.positionCaret(request.initialPrompt().length());
        Platform.runLater(inputField::requestFocus);
    }

    private void loadConversation(ChatConversation conversation) {
        currentConversation = conversation;
        messagesBox.getChildren().clear();
        summarizeBlockedActionButtons.clear();
        activeChatRequestId = null;
        latestRequestEvent = null;
        activeImageJobId = null;
        activeImageRequestId = null;
        latestImageRequestEvent = null;
        latestImageJobSnapshot = null;
        resetLifecycleVisualState();
        resetImageLifecycleVisualState();
        resetStreamingMessageState();
        setLifecycleStatusVisible(false);
        setImageLifecycleStatusVisible(false);
        setSummarizationInteractionLocked(false, null);

        if (conversation == null) {
            nextMessageSeq = 0;
            suppressContextModeChange = true;
            contextModeCombo.setValue(ChatContextMode.AUTO);
            suppressContextModeChange = false;
            contextUsageLabel.setText("Контекст: —");
            contextUsageTooltip.setText("");
            setContextControlsEnabled(false);
            return;
        }

        List<ChatMessage> messages = db.loadChatMessages(conversation.getId());
        for (ChatMessage message : messages) {
            if ("user".equalsIgnoreCase(message.getRole())) {
                if (isImageMessage(message.getContent())) {
                    addUserImageMessage(decodeImagePath(message.getContent()));
                } else if (isMediaMessage(message.getContent())) {
                    addUserAttachmentMessage(decodeMediaMessage(message.getContent()));
                } else {
                    addUserMessage(message.getContent());
                }
            } else {
                addBotMessage(message.getContent());
            }
        }
        nextMessageSeq = messages.size();
        if (messages.isEmpty()) {
            showGreeting();
        }
        syncImageJobStateForConversation(conversation, messages);
        setContextControlsEnabled(true);
        syncContextPanelWithConversation(conversation, messages);
    }

    private void setContextControlsEnabled(boolean enabled) {
        boolean disabled = !enabled || isSummarizationInteractionLocked();
        contextModeCombo.setDisable(disabled);
        contextPinButton.setDisable(disabled);
        contextRebuildButton.setDisable(disabled);
        contextClearButton.setDisable(disabled);
    }

    private void syncContextPanelWithConversation(ChatConversation conversation, List<ChatMessage> messages) {
        if (conversation == null) {
            return;
        }
        String conversationId = conversation.getId();
        List<AiRequestOptions.ChatHistoryEntry> history = new ArrayList<>();
        if (messages != null && !messages.isEmpty()) {
            for (ChatMessage message : messages) {
                if (message == null || message.getRole() == null) {
                    continue;
                }
                String role = message.getRole().trim().toLowerCase(Locale.ROOT);
                String content = message.getContent() == null ? "" : message.getContent();
                if ("user".equals(role)) {
                    if (isImageMessage(content)) {
                        history.add(AiRequestOptions.ChatHistoryEntry.user("Пользователь отправил изображение."));
                    } else if (isMediaMessage(content)) {
                        history.add(AiRequestOptions.ChatHistoryEntry.user(describeMediaMessageForContext(content)));
                    } else if (!content.isBlank()) {
                        history.add(AiRequestOptions.ChatHistoryEntry.user(content));
                    }
                } else if ("assistant".equals(role)) {
                    if (isImageMessage(content)) {
                        history.add(AiRequestOptions.ChatHistoryEntry.assistant("Ассистент сгенерировал изображение."));
                    } else if (!content.isBlank()) {
                        ModelTaggedMessage tagged = parseModelTaggedMessage(content);
                        String normalized = tagged.content() == null || tagged.content().isBlank()
                            ? content
                            : tagged.content();
                        history.add(AiRequestOptions.ChatHistoryEntry.assistant(normalized));
                    }
                }
            }
        }
        chatService.replaceConversationHistory(conversationId, history);
        ChatContextMode mode = chatService.getContextMode(conversationId);
        suppressContextModeChange = true;
        contextModeCombo.setValue(mode == null ? ChatContextMode.AUTO : mode);
        suppressContextModeChange = false;
        refreshContextUsageIndicator();
    }

    private void onChatRequestEvent(ChatRequestEvent event) {
        if (event == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!isEventForCurrentConversation(event)) {
                return;
            }
            if (activeChatRequestId != null
                && !activeChatRequestId.isBlank()
                && !activeChatRequestId.equals(event.requestId())
                && !event.state().isTerminal()) {
                return;
            }
            latestRequestEvent = event;
            setSummarizationInteractionLocked(
                event.state() == ChatRequestState.SUMMARIZING,
                event.state() == ChatRequestState.SUMMARIZING ? event.message() : null
            );
            if (event.state() == ChatRequestState.QUEUED) {
                activeChatRequestId = event.requestId();
                prepareLifecycleForNewRequest(event);
            } else if (activeChatRequestId == null || activeChatRequestId.isBlank()) {
                activeChatRequestId = event.requestId();
                prepareLifecycleForNewRequest(event);
            }
            applyLifecycleVisualState(event);
            updateContextUsageFromLifecycle(event);
            maybeAppendLifecycleSystemMessage(event);
            if (event.state().isTerminal() && event.requestId().equals(activeChatRequestId)) {
                activeChatRequestId = null;
                refreshContextUsageIndicator();
            }
        });
    }

    private void onImageRequestEvent(ImageRequestEvent event) {
        if (event == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!isImageEventForCurrentConversation(event)) {
                return;
            }
            if (activeImageJobId != null
                && !activeImageJobId.isBlank()
                && !activeImageJobId.equals(event.jobId())) {
                return;
            }
            latestImageRequestEvent = event;
            latestImageJobSnapshot = imageGenerationService.getJob(event.jobId());
            if (activeImageJobId == null || activeImageJobId.isBlank()) {
                activeImageJobId = event.jobId();
            }
            if (event.requestId() != null && !event.requestId().isBlank()) {
                activeImageRequestId = event.requestId();
            }
            if (event.state() == ImageRequestState.QUEUED) {
                prepareImageLifecycleForNewRequest(event);
            } else if (!imageLifecycleStatusBar.isVisible()) {
                prepareImageLifecycleForNewRequest(event);
            }
            applyImageLifecycleVisualState(event);
            maybeAppendImageLifecycleSystemMessage(event);
            updateImageGenerationIndicator(event);
            updateImageJobActionControls(event);
            maybePersistCompletedAutoResumedImageJob(event);
            maybePersistFailedAutoResumedImageJob(event);
        });
    }

    private boolean isEventForCurrentConversation(ChatRequestEvent event) {
        if (event == null) {
            return false;
        }
        if (currentConversation == null) {
            return true;
        }
        String eventConversationId = event.conversationId();
        if (eventConversationId == null || eventConversationId.isBlank()) {
            return true;
        }
        return currentConversation.getId().equals(eventConversationId);
    }

    private boolean isImageEventForCurrentConversation(ImageRequestEvent event) {
        if (event == null) {
            return false;
        }
        if (currentConversation == null) {
            return true;
        }
        String eventConversationId = event.conversationId();
        if (eventConversationId == null || eventConversationId.isBlank()) {
            return true;
        }
        return currentConversation.getId().equals(eventConversationId);
    }

    private void syncImageJobStateForConversation(ChatConversation conversation, List<ChatMessage> messages) {
        activeImageJobId = null;
        activeImageRequestId = null;
        latestImageRequestEvent = null;
        latestImageJobSnapshot = null;
        resetImageLifecycleVisualState();
        setImageLifecycleStatusVisible(false);
        updateImageJobActionControls(null);

        if (conversation == null) {
            return;
        }

        ImageJobSnapshot snapshot = imageGenerationService.getLatestJobForConversation(conversation.getId());
        latestImageJobSnapshot = snapshot;
        if (snapshot == null) {
            return;
        }

        activeImageJobId = snapshot.getJobId();
        activeImageRequestId = snapshot.getRequestId();

        if (snapshot.getState() == ImageJobState.DONE
            && snapshot.getSavedPath() != null
            && !snapshot.getSavedPath().isBlank()
            && !hasPersistedAssistantMessage(messages, encodeImageMessage(Path.of(snapshot.getSavedPath())))) {
            String encoded = encodeImageMessage(Path.of(snapshot.getSavedPath()));
            addBotImageMessage(snapshot.getSavedPath());
            persistMessage("assistant", encoded);
        }

        ImageRequestEvent event = snapshotToEvent(snapshot);
        latestImageRequestEvent = event;
        prepareImageLifecycleForNewRequest(event);
        applyImageLifecycleVisualState(event);
        updateImageJobActionControls(event);

        if (snapshot.canResume() && snapshot.getState() != ImageJobState.PAUSED) {
            imageGenerationService.resumePendingJobsForConversation(conversation.getId());
        }
    }

    private boolean hasPersistedAssistantMessage(List<ChatMessage> messages, String expectedContent) {
        if (messages == null || messages.isEmpty() || expectedContent == null || expectedContent.isBlank()) {
            return false;
        }
        for (ChatMessage message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            if ("assistant".equalsIgnoreCase(message.getRole())
                && expectedContent.equals(message.getContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private ImageRequestEvent snapshotToEvent(ImageJobSnapshot snapshot) {
        return new ImageRequestEvent(
            snapshot == null ? "" : snapshot.getJobId(),
            snapshot == null ? "" : snapshot.getRequestId(),
            snapshot == null ? "" : snapshot.getConversationId(),
            snapshot == null ? ImageRequestState.QUEUED : toImageRequestState(snapshot.getState()),
            snapshot == null ? "" : safeSnapshotModel(snapshot),
            snapshot == null ? "" : snapshot.getLastMessage(),
            new com.example.neuroflowplanner.service.imageflow.ImageRequestProgress(
                snapshot == null ? 0L : Math.max(0L, System.currentTimeMillis() - Math.max(0L, snapshot.getCreatedAt())),
                snapshot == null ? 1 : Math.max(1, snapshot.getAttempt()),
                snapshot == null ? 1 : Math.max(1, snapshot.getAttempt()),
                snapshot != null && snapshot.getState() != null && snapshot.getState().isTerminal()
            ),
            snapshot != null && snapshot.getUpdatedAt() > 0L ? Instant.ofEpochMilli(snapshot.getUpdatedAt()) : Instant.now(),
            Map.of(
                "jobState", snapshot == null || snapshot.getState() == null ? "" : snapshot.getState().name(),
                "savedPath", snapshot == null ? "" : snapshot.getSavedPath(),
                "remoteUrl", snapshot == null ? "" : snapshot.getRemoteUrl()
            )
        );
    }

    private ImageRequestState toImageRequestState(ImageJobState state) {
        if (state == null) {
            return ImageRequestState.QUEUED;
        }
        return switch (state) {
            case QUEUED -> ImageRequestState.QUEUED;
            case SUBMITTING -> ImageRequestState.SENDING;
            case SUBMITTED, POLLING -> ImageRequestState.POLLING;
            case DOWNLOADING -> ImageRequestState.DOWNLOADING;
            case SAVING -> ImageRequestState.SAVING;
            case PAUSED -> ImageRequestState.PAUSED;
            case FAILED -> ImageRequestState.FAILED;
            case CANCELLED -> ImageRequestState.CANCELLED;
            case DONE -> ImageRequestState.DONE;
        };
    }

    private String safeSnapshotModel(ImageJobSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.getActiveModel() != null && !snapshot.getActiveModel().isBlank()) {
            return snapshot.getActiveModel().trim();
        }
        return snapshot.getRequestedModel() == null ? "" : snapshot.getRequestedModel().trim();
    }

    private void updateImageJobActionControls(ImageRequestEvent event) {
        ImageRequestEvent effectiveEvent = event != null ? event : latestImageRequestEvent;
        ImageJobSnapshot snapshot = latestImageJobSnapshot;
        if (effectiveEvent != null && effectiveEvent.jobId() != null && !effectiveEvent.jobId().isBlank()) {
            snapshot = imageGenerationService.getJob(effectiveEvent.jobId());
            if (snapshot != null) {
                latestImageJobSnapshot = snapshot;
            }
        }
        if (snapshot == null) {
            configureImageLifecycleActionButton(imageLifecyclePrimaryActionButton, null, null);
            configureImageLifecycleActionButton(imageLifecycleSecondaryActionButton, null, null);
            return;
        }

        ImageJobState state = snapshot.getState();
        if (state == null) {
            configureImageLifecycleActionButton(imageLifecyclePrimaryActionButton, null, null);
            configureImageLifecycleActionButton(imageLifecycleSecondaryActionButton, null, null);
            return;
        }

        if (state.isRunning()) {
            configureImageLifecycleActionButton(
                imageLifecyclePrimaryActionButton,
                FontIcon.of(MaterialDesignP.PAUSE, 14),
                "Поставить image-job на паузу"
            );
            configureImageLifecycleActionButton(
                imageLifecycleSecondaryActionButton,
                FontIcon.of(MaterialDesignC.CLOSE, 14),
                "Отменить image-job"
            );
            return;
        }

        if (state == ImageJobState.PAUSED) {
            configureImageLifecycleActionButton(
                imageLifecyclePrimaryActionButton,
                FontIcon.of(MaterialDesignP.PLAY, 14),
                "Возобновить image-job"
            );
            configureImageLifecycleActionButton(
                imageLifecycleSecondaryActionButton,
                FontIcon.of(MaterialDesignC.CLOSE, 14),
                "Отменить image-job"
            );
            return;
        }

        if (state == ImageJobState.FAILED || state == ImageJobState.CANCELLED) {
            configureImageLifecycleActionButton(
                imageLifecyclePrimaryActionButton,
                FontIcon.of(MaterialDesignR.RELOAD, 14),
                "Повторить image-job"
            );
            configureImageLifecycleActionButton(imageLifecycleSecondaryActionButton, null, null);
            return;
        }

        configureImageLifecycleActionButton(imageLifecyclePrimaryActionButton, null, null);
        configureImageLifecycleActionButton(imageLifecycleSecondaryActionButton, null, null);
    }

    private void maybePersistCompletedAutoResumedImageJob(ImageRequestEvent event) {
        if (event == null || event.state() != ImageRequestState.DONE || isCurrentImageIndicator(event.jobId())) {
            return;
        }
        ImageJobSnapshot snapshot = imageGenerationService.getJob(event.jobId());
        if (snapshot == null || snapshot.getSavedPath() == null || snapshot.getSavedPath().isBlank()) {
            return;
        }
        if (currentConversation == null || !isImageEventForCurrentConversation(event)) {
            return;
        }
        Path savedPath = Path.of(snapshot.getSavedPath());
        String encodedImage = encodeImageOutputMessage(buildSuccessImageOutputPayload(snapshot, savedPath));
        if (hasPersistedAssistantMessage(db.loadChatMessages(currentConversation.getId()), encodedImage)) {
            return;
        }
        addBotImageOutputMessage(buildSuccessImageOutputPayload(snapshot, savedPath));
        persistMessage("assistant", encodedImage);
    }

    private void maybePersistFailedAutoResumedImageJob(ImageRequestEvent event) {
        if (event == null || event.state() != ImageRequestState.FAILED || isCurrentImageIndicator(event.jobId())) {
            return;
        }
        ImageJobSnapshot snapshot = imageGenerationService.getJob(event.jobId());
        if (snapshot == null || currentConversation == null || !isImageEventForCurrentConversation(event)) {
            return;
        }
        ImageOutputPayload payload = buildFailureImageOutputPayload(snapshot, null);
        String encoded = encodeImageOutputMessage(payload);
        if (hasPersistedAssistantMessage(db.loadChatMessages(currentConversation.getId()), encoded)) {
            return;
        }
        addBotImageOutputMessage(payload);
        persistMessage("assistant", encoded);
    }

    private void configureImageLifecycleActionButton(Button button, Node graphic, String tooltipText) {
        if (button == null) {
            return;
        }
        boolean visible = graphic != null;
        button.setGraphic(graphic);
        button.setVisible(visible);
        button.setManaged(visible);
        button.setDisable(!visible);
        button.setTooltip(visible ? new Tooltip(tooltipText) : null);
    }

    private void handlePrimaryImageJobAction() {
        ImageJobSnapshot snapshot = latestImageJobSnapshot;
        if (snapshot == null || snapshot.getJobId() == null || snapshot.getJobId().isBlank()) {
            return;
        }
        if (snapshot.getState() != null && snapshot.getState().isRunning()) {
            if (imageGenerationService.pauseJob(snapshot.getJobId())) {
                addSystemMessage("Image-job поставлен на паузу.");
            }
            return;
        }
        if (snapshot.getState() == ImageJobState.PAUSED) {
            addSystemMessage("Возобновляю image-job по сохранённому request-id.");
            CompletableFuture<ImageGenerationService.ImageGenerationResult> request = imageGenerationService.resumeJob(snapshot.getJobId());
            pendingRequest = request;
            activeImageJobId = snapshot.getJobId();
            ensureImageGeneratingIndicator(snapshot.getJobId());
            setRequestInProgress(true);
            observeManagedImageJob(snapshot.getJobId(), request, false, null, null);
            return;
        }
        if (snapshot.getState() == ImageJobState.FAILED || snapshot.getState() == ImageJobState.CANCELLED) {
            addSystemMessage("Повторно запускаю image-job с исходным prompt.");
            CompletableFuture<ImageGenerationService.ImageGenerationResult> request = imageGenerationService.retryJob(snapshot.getJobId());
            pendingRequest = request;
            activeImageJobId = snapshot.getJobId();
            activeImageRequestId = "";
            ensureImageGeneratingIndicator(snapshot.getJobId());
            setRequestInProgress(true);
            observeManagedImageJob(snapshot.getJobId(), request, false, null, null);
        }
    }

    private void handleSecondaryImageJobAction() {
        ImageJobSnapshot snapshot = latestImageJobSnapshot;
        if (snapshot == null || snapshot.getJobId() == null || snapshot.getJobId().isBlank()) {
            return;
        }
        if (imageGenerationService.cancelRequest(snapshot.getJobId())) {
            addSystemMessage("Отменяю image-job.");
        }
    }

    private void ensureImageGeneratingIndicator(String jobId) {
        if (isCurrentImageIndicator(jobId)) {
            return;
        }
        removeTypingIndicator();
        HBox generating = createImageGeneratingIndicatorWithCancel(jobId);
        currentTypingIndicator = generating;
        messagesBox.getChildren().add(generating);
        scrollToBottom();
    }

    private void onChatResponseChunk(ChatResponseChunk chunk) {
        if (chunk == null) {
            return;
        }
        Platform.runLater(() -> {
            if (!isChunkForCurrentConversation(chunk)) {
                return;
            }
            if (activeChatRequestId != null
                && !activeChatRequestId.isBlank()
                && !activeChatRequestId.equals(chunk.requestId())) {
                return;
            }

            ensureStreamingBubble(chunk.requestId(), chunk.model());
            updateStreamingBubbleModel(chunk.model());

            String updatedText = chunk.accumulatedText();
            if (updatedText == null || updatedText.isBlank()) {
                if (chunk.deltaText() != null && !chunk.deltaText().isBlank()) {
                    updatedText = streamingAccumulatedText + chunk.deltaText();
                } else {
                    updatedText = streamingAccumulatedText;
                }
            }
            streamingAccumulatedText = updatedText == null ? "" : updatedText;
            scheduleStreamingWebViewRender();

            if (chunk.terminal()) {
                flushStreamingWebViewRender();
            }
        });
    }

    private boolean isChunkForCurrentConversation(ChatResponseChunk chunk) {
        if (chunk == null) {
            return false;
        }
        if (currentConversation == null) {
            return true;
        }
        String conversationId = chunk.conversationId();
        if (conversationId == null || conversationId.isBlank()) {
            return true;
        }
        return currentConversation.getId().equals(conversationId);
    }

    private void handleContextRebuild() {
        if (currentConversation == null) {
            return;
        }
        if (pendingRequest != null && !pendingRequest.isDone()) {
            return;
        }
        String conversationId = currentConversation.getId();
        String requestModel = AiClientFactory.getInstance().getActiveClient().getDefaultModel();
        ChatContextMode selectedMode = contextModeCombo.getValue() == null
            ? ChatContextMode.AUTO
            : contextModeCombo.getValue();
        cancellationNoticeShown = false;
        lifecycleHideDelay.stop();
        setLifecycleStatusVisible(true);
        resetStreamingMessageState();
        setSummarizationInteractionLocked(true, "Подготавливаем краткий пересказ переписки…");
        setRequestInProgress(true);
        contextRebuildButton.setDisable(true);
        inputField.setPromptText("Сжимаем контекст, отправка временно недоступна…");

        CompletableFuture<?> request = chatService.summarizeContext(conversationId, requestModel, selectedMode);
        pendingRequest = request;

        request.thenAccept(ignored -> Platform.runLater(() -> {
            if (pendingRequest == request) {
                pendingRequest = null;
            }
            setSummarizationInteractionLocked(false, null);
            setRequestInProgress(false);
            setContextControlsEnabled(currentConversation != null);
            refreshContextUsageIndicator();
            addSystemMessage("Контекст: сжатие завершено.");
        })).exceptionally(ex -> {
            Platform.runLater(() -> {
                if (pendingRequest == request) {
                    pendingRequest = null;
                }
                setSummarizationInteractionLocked(false, null);
                setRequestInProgress(false);
                setContextControlsEnabled(currentConversation != null);
                Throwable cause = AsyncContext.unwrap(ex);
                String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                    ? "Не удалось сжать контекст."
                    : cause.getMessage();
                addSystemMessage("Контекст: " + message);
                refreshContextUsageIndicator();
            });
            return null;
        });
    }

    private void handleContextClear() {
        if (currentConversation == null) {
            return;
        }
        if (!showClearContextConfirmDialog()) {
            return;
        }
        String conversationId = currentConversation.getId();
        chatService.clearContext(conversationId);
        chatService.setContextMode(conversationId, ChatContextMode.AUTO);
        suppressContextModeChange = true;
        contextModeCombo.setValue(ChatContextMode.AUTO);
        suppressContextModeChange = false;
        refreshContextUsageIndicator();
        addSystemMessage("Контекст текущей переписки очищен.");
    }

    private void handleContextPinFact() {
        if (currentConversation == null) {
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Закрепить факт");
        dialog.setHeaderText("Добавьте важный факт в контекст");
        dialog.setContentText("Факт:");
        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        dialog.showAndWait().ifPresent(text -> {
            String normalized = text == null ? "" : text.trim();
            if (normalized.isBlank()) {
                return;
            }
            chatService.pinContextItem(currentConversation.getId(), normalized);
            refreshContextUsageIndicator();
            addSystemMessage("Контекст: факт закреплен.");
        });
    }

    private boolean showClearContextConfirmDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Очистить контекст");
        alert.setHeaderText("Очистить контекст текущей переписки?");
        alert.setContentText("Сообщения в ленте останутся, но ИИ перестанет учитывать накопленный контекст.");
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        ButtonType clearBtn = new ButtonType("Очистить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(clearBtn, cancelBtn);
        return alert.showAndWait().orElse(cancelBtn) == clearBtn;
    }

    private void refreshContextUsageIndicator() {
        if (currentConversation == null) {
            resetContextPanelDisplay();
            return;
        }
        ChatContextMode selectedMode = contextModeCombo.getValue() == null
            ? ChatContextMode.AUTO
            : contextModeCombo.getValue();
        String modelId = resolveActiveChatModelId();
        ChatContextBuildResult result = chatService.buildContext(currentConversation.getId(), selectedMode);
        ChatContextBudgetSnapshot budgetSnapshot =
            chatService.buildContextBudgetSnapshot(currentConversation.getId(), modelId, selectedMode);
        ChatContextSummarizationState summarizationState =
            chatService.getContextSummarizationState(currentConversation.getId(), modelId, selectedMode);
        AiTextModelContextMetadata modelContextMetadata = AiTextModelContextResolver.resolveForModel(modelId);
        renderContextPanel(result, budgetSnapshot, summarizationState, modelId, modelContextMetadata);
    }

    private void updateContextUsageFromLifecycle(ChatRequestEvent event) {
        refreshContextUsageIndicator();
    }

    private void resetContextPanelDisplay() {
        contextModelValueLabel.setText("—");
        contextWindowValueLabel.setText("Размер контекста неизвестен");
        contextWindowHintLabel.setText("estimate-only");
        contextWindowHintLabel.setManaged(true);
        contextWindowHintLabel.setVisible(true);
        contextBudgetProgressBar.setProgress(0.0);
        contextUsageLabel.setText("Контекст: —");
        contextSummaryStatusLabel.setText("Статус: —");
        contextSummaryStatusTooltip.setText("Статус: —");
        contextUsageTooltip.setText("");
        updateContextBudgetSeverityStyles(ChatContextBudgetSeverity.UNKNOWN);
    }

    private void renderContextPanel(
            ChatContextBuildResult result,
            ChatContextBudgetSnapshot budgetSnapshot,
            ChatContextSummarizationState summarizationState,
            String modelId,
            AiTextModelContextMetadata modelContextMetadata) {
        if (result == null) {
            resetContextPanelDisplay();
            return;
        }

        String normalizedModelId = modelId == null || modelId.isBlank() ? "Модель не определена" : modelId.trim();
        contextModelValueLabel.setText(normalizedModelId);

        if (modelContextMetadata != null && modelContextMetadata.contextWindowLabel() != null) {
            contextWindowValueLabel.setText(modelContextMetadata.contextWindowLabel());
            contextWindowHintLabel.setText("");
            contextWindowHintLabel.setManaged(false);
            contextWindowHintLabel.setVisible(false);
        } else {
            contextWindowValueLabel.setText("Размер контекста неизвестен");
            contextWindowHintLabel.setText("estimate-only");
            contextWindowHintLabel.setManaged(true);
            contextWindowHintLabel.setVisible(true);
        }

        if (budgetSnapshot != null && budgetSnapshot.hasKnownContextLimit()) {
            double progress = budgetSnapshot.usageRatio() == null ? 0.0 : clampProgress(budgetSnapshot.usageRatio());
            contextBudgetProgressBar.setProgress(progress);
            contextUsageLabel.setText(
                "~%s / %s".formatted(
                    formatTokenCount(budgetSnapshot.estimatedUsedTokens()),
                    formatTokenCount(budgetSnapshot.contextLimitTokens()))
            );
            updateContextBudgetSeverityStyles(budgetSnapshot.severity());
        } else {
            contextBudgetProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            contextUsageLabel.setText("~%s • лимит неизвестен".formatted(
                formatTokenCount(result.estimatedTokens())));
            updateContextBudgetSeverityStyles(ChatContextBudgetSeverity.UNKNOWN);
        }

        String summaryStatusText = resolveContextSummaryStatusText(
            result,
            budgetSnapshot,
            summarizationState,
            modelContextMetadata
        );
        contextSummaryStatusLabel.setText(summaryStatusText);
        contextSummaryStatusTooltip.setText(summaryStatusText);

        String tooltip = """
            Модель: %s
            Запрашиваемый режим: %s
            Эффективный режим: %s
            Используется сообщений: %d из %d
            Сводка: %s
            Закрепленных фактов: %d
            Оценка токенов: %d
            Контекстное окно: %s
            Reserve под ответ: %s
            Остаток: %s
            Защита от переполнения: %s
            """.formatted(
            normalizedModelId,
            toContextModeLabel(result.requestedMode()),
            toContextModeLabel(result.effectiveMode()),
            result.selectedHistoryMessages(),
            result.totalHistoryMessages(),
            result.summaryIncluded() ? "да" : "нет",
            result.pinnedFactsCount(),
            result.estimatedTokens(),
            modelContextMetadata == null || modelContextMetadata.contextWindowLabel() == null
                ? "неизвестно"
                : modelContextMetadata.contextWindowLabel(),
            budgetSnapshot == null ? "—" : formatOptionalTokenCount(budgetSnapshot.reservedCompletionTokens()),
            budgetSnapshot == null ? "—" : formatOptionalTokenCount(budgetSnapshot.estimatedRemainingTokens()),
            result.overflowProtected() ? "включена" : "нет"
        ).trim();
        contextUsageTooltip.setText(tooltip);
    }

    private String resolveContextSummaryStatusText(
            ChatContextBuildResult result,
            ChatContextBudgetSnapshot budgetSnapshot,
            ChatContextSummarizationState summarizationState,
            AiTextModelContextMetadata modelContextMetadata) {
        if (summarizationState != null) {
            if (summarizationState.status() == ChatContextSummarizationStatus.SUMMARIZING) {
                return "Сжимаем контекст. Общение временно заблокировано.";
            }
            if (summarizationState.status() == ChatContextSummarizationStatus.SUMMARY_FAILED) {
                return summarizationState.lastError() == null || summarizationState.lastError().isBlank()
                    ? "Сжатие контекста не удалось."
                    : "Сжатие не удалось: " + summarizationState.lastError().trim();
            }
            if (summarizationState.status() == ChatContextSummarizationStatus.SUMMARY_READY) {
                return "Контекст сжат. Работаем по summary snapshot и новым сообщениям.";
            }
        }
        if (budgetSnapshot != null) {
            if (budgetSnapshot.severity() == ChatContextBudgetSeverity.OVER_LIMIT
                    || budgetSnapshot.severity() == ChatContextBudgetSeverity.CRITICAL) {
                return "Контекст на пределе. Следующим шагом потребуется сжатие.";
            }
            if (budgetSnapshot.severity() == ChatContextBudgetSeverity.WARNING
                    || (summarizationState != null && summarizationState.status() == ChatContextSummarizationStatus.NEAR_LIMIT)) {
                return "Контекст близок к пределу. Можно сжать вручную заранее.";
            }
        }
        if (result.summaryIncluded()) {
            return "Используется summary snapshot и новые сообщения.";
        }
        if (modelContextMetadata == null || modelContextMetadata.contextWindowLabel() == null) {
            return "Размер окна модели неизвестен. Оценка контекста приблизительная.";
        }
        return "Контекст в норме.";
    }

    private void updateContextBudgetSeverityStyles(ChatContextBudgetSeverity severity) {
        contextBudgetProgressBar.getStyleClass().removeAll(
            "chat-context-progress-normal",
            "chat-context-progress-warning",
            "chat-context-progress-critical",
            "chat-context-progress-unknown"
        );
        switch (severity == null ? ChatContextBudgetSeverity.UNKNOWN : severity) {
            case WARNING -> contextBudgetProgressBar.getStyleClass().add("chat-context-progress-warning");
            case CRITICAL, OVER_LIMIT -> contextBudgetProgressBar.getStyleClass().add("chat-context-progress-critical");
            case NORMAL -> contextBudgetProgressBar.getStyleClass().add("chat-context-progress-normal");
            case UNKNOWN -> contextBudgetProgressBar.getStyleClass().add("chat-context-progress-unknown");
        }
    }

    private double clampProgress(Double usageRatio) {
        if (usageRatio == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, usageRatio));
    }

    private String formatOptionalTokenCount(Integer value) {
        if (value == null || value <= 0) {
            return "—";
        }
        return formatTokenCount(value);
    }

    private String formatTokenCount(int tokens) {
        if (tokens >= 1_000_000) {
            return String.format(Locale.US, "%.2fM", tokens / 1_000_000.0).replaceAll("\\.?0+M$", "M");
        }
        if (tokens >= 1_000) {
            return String.format(Locale.US, "%.0fK", tokens / 1_000.0);
        }
        return Integer.toString(tokens);
    }

    private int parseIntOrDefault(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String toContextModeLabel(ChatContextMode mode) {
        if (mode == null) {
            return "Auto";
        }
        return switch (mode) {
            case AUTO -> "Auto";
            case RECENT -> "Recent";
            case FULL -> "Full";
            case MINIMAL -> "Minimal";
        };
    }

    private void ensureStreamingBubble(String requestId, String modelName) {
        if (streamingBotContainer != null) {
            return;
        }
        removeTypingIndicator();
        streamingRequestId = requestId == null ? "" : requestId;
        streamingModelName = modelName == null ? "" : modelName.trim();
        streamingActive = true;
        streamingRenderDirty = false;

        HBox container = new HBox(10);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPadding(new Insets(0, 20, 0, 0));

        Node avatar = ChatBotAvatar.create(32);

        VBox msgBox = new VBox(4);
        msgBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(msgBox, Priority.ALWAYS);

        Label modelLabel = new Label();
        modelLabel.getStyleClass().add("chat-model-label");
        modelLabel.setVisible(false);
        modelLabel.setManaged(false);
        msgBox.getChildren().add(modelLabel);

        WebView webView = new WebView();
        webView.getStyleClass().add("chat-webview");
        webView.setContextMenuEnabled(false);
        webView.setPrefHeight(100);
        webView.setMinHeight(50);
        webView.setMaxHeight(400);
        configureChatWebViewHeightAutoResize(webView);

        msgBox.getChildren().add(webView);
        container.getChildren().addAll(avatar, msgBox);

        streamingBotContainer = container;
        streamingBotModelLabel = modelLabel;
        streamingBotWebView = webView;

        updateStreamingBubbleModel(modelName);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private void updateStreamingBubbleModel(String modelName) {
        if (streamingBotModelLabel == null) {
            return;
        }
        String normalized = modelName == null ? "" : modelName.trim();
        if (!normalized.isBlank()) {
            streamingModelName = normalized;
        }
        if (streamingModelName == null || streamingModelName.isBlank()) {
            streamingBotModelLabel.setText("");
            streamingBotModelLabel.setVisible(false);
            streamingBotModelLabel.setManaged(false);
            return;
        }
        streamingBotModelLabel.setText("Модель: " + streamingModelName);
        streamingBotModelLabel.setVisible(true);
        streamingBotModelLabel.setManaged(true);
    }

    private void scheduleStreamingWebViewRender() {
        if (!streamingActive) {
            return;
        }
        streamingRenderDirty = true;
        streamingRenderDebounce.playFromStart();
    }

    private void flushStreamingWebViewRender() {
        if (!streamingRenderDirty || streamingBotWebView == null) {
            return;
        }
        streamingRenderDirty = false;
        updateWebViewMarkdownContent(streamingBotWebView, streamingAccumulatedText);
    }

    private boolean hasStreamingContentForRequest(String requestId) {
        if (!streamingActive || streamingRequestId == null || streamingRequestId.isBlank()) {
            return false;
        }
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        return streamingRequestId.equals(requestId) && streamingAccumulatedText != null && !streamingAccumulatedText.isBlank();
    }

    private String finishStreamingContent(String requestId, String fallbackContent, String fallbackModel) {
        if (!hasStreamingContentForRequest(requestId)) {
            resetStreamingMessageState();
            return fallbackContent;
        }
        streamingRenderDebounce.stop();
        flushStreamingWebViewRender();
        updateStreamingBubbleModel(fallbackModel);
        String finalText = streamingAccumulatedText == null || streamingAccumulatedText.isBlank()
            ? (fallbackContent == null ? "" : fallbackContent)
            : streamingAccumulatedText;
        streamingActive = false;
        return finalText;
    }

    private void resetStreamingMessageState() {
        streamingRenderDebounce.stop();
        streamingActive = false;
        streamingRenderDirty = false;
        streamingRequestId = null;
        streamingAccumulatedText = "";
        streamingModelName = "";
        streamingBotContainer = null;
        streamingBotModelLabel = null;
        streamingBotWebView = null;
    }

    private void prepareImageLifecycleForNewRequest(ImageRequestEvent event) {
        imageLifecycleHideDelay.stop();
        imageLifecycleAnnouncements.clear();
        imageLifecyclePrimaryModel = event.model() == null ? "" : event.model().trim();
        imageLifecycleActiveModel = imageLifecyclePrimaryModel;
        imageLifecycleLastAttempt = 1;
        imageLifecycleStartEpochMs = System.currentTimeMillis() - event.progress().elapsedMs();
        refreshImageElapsedClock();
        imageLifecycleElapsedTimeline.playFromStart();
        imageLifecycleDetailLabel.setText("");
        setImageLifecycleStatusVisible(true);
    }

    private void applyImageLifecycleVisualState(ImageRequestEvent event) {
        setImageLifecycleStatusVisible(true);
        imageLifecycleStartEpochMs = System.currentTimeMillis() - event.progress().elapsedMs();
        refreshImageElapsedClock();
        if (!event.state().isTerminal()
            && event.state() != ImageRequestState.PAUSED
            && imageLifecycleElapsedTimeline.getStatus() != Timeline.Status.RUNNING) {
            imageLifecycleElapsedTimeline.play();
        }

        ImageRequestState state = event.state();
        int stepIndex = imageLifecycleStepIndex(state);
        int maxStep = imageLifecycleStepDots.size() - 1;
        double progress = maxStep <= 0 ? 0.0 : Math.min(1.0, Math.max(0.0, (double) stepIndex / maxStep));
        imageLifecycleProgressBar.setProgress(progress);

        for (int i = 0; i < imageLifecycleStepDots.size(); i++) {
            Region dot = imageLifecycleStepDots.get(i);
            dot.getStyleClass().removeAll(
                "chat-lifecycle-step-dot-active",
                "chat-lifecycle-step-dot-done",
                "chat-lifecycle-step-dot-error",
                "chat-lifecycle-step-dot-cancelled"
            );
            if (state == ImageRequestState.FAILED && i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-error");
                continue;
            }
            if (state == ImageRequestState.CANCELLED && i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-cancelled");
                continue;
            }
            if (i < stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-done");
            } else if (i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-active");
            }
        }

        imageLifecycleStatusLabel.setText(resolveImageLifecycleHeadline(state));
        imageLifecycleDetailLabel.setText(resolveImageLifecycleDetail(event));

        int attempt = Math.max(1, event.progress().attempt());
        int maxAttempts = Math.max(1, event.progress().maxAttempts());
        String model = event.model() == null ? "" : event.model().trim();
        if (!model.isBlank()) {
            imageLifecycleActiveModel = model;
        }
        boolean fallbackDetected = !imageLifecyclePrimaryModel.isBlank()
            && !imageLifecycleActiveModel.isBlank()
            && !imageLifecyclePrimaryModel.equalsIgnoreCase(imageLifecycleActiveModel);
        imageLifecycleAttemptLabel.setText(
            fallbackDetected
                ? "Попытка %d/%d • fallback".formatted(attempt, maxAttempts)
                : "Попытка %d/%d".formatted(attempt, maxAttempts)
        );

        boolean activeState = !state.isTerminal() && state != ImageRequestState.PAUSED;
        imageLifecycleSpinner.setVisible(activeState);
        imageLifecycleSpinner.setManaged(activeState);
        imageLifecycleTooltip.setText(buildImageLifecycleTooltip(event, fallbackDetected));
        updateImageJobActionControls(event);

        if (state == ImageRequestState.PAUSED) {
            imageLifecycleElapsedTimeline.stop();
            imageLifecycleHideDelay.stop();
            return;
        }
        if (state.isTerminal()) {
            imageLifecycleElapsedTimeline.stop();
            imageLifecycleHideDelay.playFromStart();
        }
    }

    private void maybeAppendImageLifecycleSystemMessage(ImageRequestEvent event) {
        int attempt = Math.max(1, event.progress().attempt());
        int maxAttempts = Math.max(1, event.progress().maxAttempts());

        if (attempt > imageLifecycleLastAttempt) {
            imageLifecycleLastAttempt = attempt;
            String retryKey = "image-retry:%s:%d".formatted(event.jobId(), attempt);
            if (imageLifecycleAnnouncements.add(retryKey)) {
                addSystemMessage("Повторная попытка генерации изображения %d из %d…".formatted(attempt, maxAttempts));
            }
        }

        String model = event.model() == null ? "" : event.model().trim();
        if (!model.isBlank()
            && !imageLifecyclePrimaryModel.isBlank()
            && !model.equalsIgnoreCase(imageLifecyclePrimaryModel)) {
            String fallbackKey = "image-fallback:%s:%s".formatted(event.jobId(), model.toLowerCase(Locale.ROOT));
            if (imageLifecycleAnnouncements.add(fallbackKey)) {
                addSystemMessage("Переключаюсь на резервную модель изображения: " + model);
            }
        }

        if (event.state() == ImageRequestState.RETRYING) {
            String key = "image-retrying:%s:%d".formatted(event.jobId(), attempt);
            if (imageLifecycleAnnouncements.add(key)) {
                addSystemMessage("Повторяю шаг генерации изображения после временной ошибки.");
            }
        }
        if (event.state() == ImageRequestState.FALLBACK_MODEL) {
            String key = "image-fallback-state:%s".formatted(event.jobId());
            if (imageLifecycleAnnouncements.add(key)) {
                addSystemMessage("Переход на fallback-модель для завершения генерации изображения.");
            }
        }
        if (event.state() == ImageRequestState.RESUMING) {
            String key = "image-resume:%s".formatted(event.jobId());
            if (imageLifecycleAnnouncements.add(key)) {
                addSystemMessage("Восстанавливаю ожидание уже отправленного image-запроса.");
            }
        }
        if (event.state() == ImageRequestState.PAUSED) {
            String key = "image-paused:%s".formatted(event.jobId());
            if (imageLifecycleAnnouncements.add(key)) {
                addSystemMessage("Image-job поставлен на паузу и может быть возобновлен позже.");
            }
        }
        if ("user-retry".equalsIgnoreCase(event.metadata().get("jobStartMode"))) {
            String key = "image-user-retry:%s".formatted(event.jobId());
            if (imageLifecycleAnnouncements.add(key)) {
                addSystemMessage("Повторно запускаю image-job по запросу пользователя.");
            }
        }
    }

    private void prepareLifecycleForNewRequest(ChatRequestEvent event) {
        lifecycleHideDelay.stop();
        lifecycleAnnouncements.clear();
        lifecyclePrimaryModel = event.model() == null ? "" : event.model().trim();
        lifecycleActiveModel = lifecyclePrimaryModel;
        lifecycleLastAttempt = 1;
        lifecycleStartEpochMs = System.currentTimeMillis() - event.progress().elapsedMs();
        refreshElapsedClock();
        lifecycleElapsedTimeline.playFromStart();
        lifecycleDetailLabel.setText("");
        setLifecycleStatusVisible(true);
    }

    private void applyLifecycleVisualState(ChatRequestEvent event) {
        setLifecycleStatusVisible(true);
        lifecycleStartEpochMs = System.currentTimeMillis() - event.progress().elapsedMs();
        refreshElapsedClock();
        if (!event.state().isTerminal() && lifecycleElapsedTimeline.getStatus() != Timeline.Status.RUNNING) {
            lifecycleElapsedTimeline.play();
        }

        ChatRequestState state = event.state();
        int stepIndex = lifecycleStepIndex(state);
        int maxStep = lifecycleStepDots.size() - 1;
        double progress = maxStep <= 0 ? 0.0 : Math.min(1.0, Math.max(0.0, (double) stepIndex / maxStep));
        lifecycleProgressBar.setProgress(progress);

        for (int i = 0; i < lifecycleStepDots.size(); i++) {
            Region dot = lifecycleStepDots.get(i);
            dot.getStyleClass().removeAll(
                "chat-lifecycle-step-dot-active",
                "chat-lifecycle-step-dot-done",
                "chat-lifecycle-step-dot-error",
                "chat-lifecycle-step-dot-cancelled"
            );
            if (state == ChatRequestState.FAILED && i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-error");
                continue;
            }
            if (state == ChatRequestState.CANCELLED && i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-cancelled");
                continue;
            }
            if (i < stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-done");
            } else if (i == stepIndex) {
                dot.getStyleClass().add("chat-lifecycle-step-dot-active");
            }
        }

        lifecycleStatusLabel.setText(resolveLifecycleStateText(state));
        lifecycleDetailLabel.setText(event.message() == null ? "" : event.message());

        int attempt = Math.max(1, event.progress().attempt());
        int maxAttempts = Math.max(1, event.progress().maxAttempts());
        String model = event.model() == null ? "" : event.model().trim();
        if (!model.isBlank()) {
            lifecycleActiveModel = model;
        }
        boolean fallbackDetected = !lifecyclePrimaryModel.isBlank()
            && !lifecycleActiveModel.isBlank()
            && !lifecyclePrimaryModel.equalsIgnoreCase(lifecycleActiveModel);
        lifecycleAttemptLabel.setText(
            fallbackDetected
                ? "Попытка %d/%d • fallback".formatted(attempt, maxAttempts)
                : "Попытка %d/%d".formatted(attempt, maxAttempts)
        );

        lifecycleSpinner.setVisible(!state.isTerminal());
        lifecycleSpinner.setManaged(!state.isTerminal());

        lifecycleTooltip.setText(buildLifecycleTooltip(event, fallbackDetected));

        if (state.isTerminal()) {
            lifecycleElapsedTimeline.stop();
            lifecycleHideDelay.playFromStart();
        }
    }

    private void maybeAppendLifecycleSystemMessage(ChatRequestEvent event) {
        int attempt = Math.max(1, event.progress().attempt());
        int maxAttempts = Math.max(1, event.progress().maxAttempts());

        if (attempt > lifecycleLastAttempt) {
            lifecycleLastAttempt = attempt;
            String retryKey = "retry:%s:%d".formatted(event.requestId(), attempt);
            if (lifecycleAnnouncements.add(retryKey)) {
                addSystemMessage("Повторная попытка %d из %d…".formatted(attempt, maxAttempts));
            }
        }

        String model = event.model() == null ? "" : event.model().trim();
        if (!model.isBlank()
            && !lifecyclePrimaryModel.isBlank()
            && !model.equalsIgnoreCase(lifecyclePrimaryModel)) {
            String fallbackKey = "fallback:%s:%s".formatted(event.requestId(), model.toLowerCase(Locale.ROOT));
            if (lifecycleAnnouncements.add(fallbackKey)) {
                addSystemMessage("Переключаюсь на резервную модель: " + model);
            }
        }

        if (event.state() == ChatRequestState.RETRYING) {
            String key = "retry-state:%s:%d".formatted(event.requestId(), attempt);
            if (lifecycleAnnouncements.add(key)) {
                addSystemMessage("Повторяю запрос после временной ошибки.");
            }
        }
        if (event.state() == ChatRequestState.FALLBACK_MODEL) {
            String key = "fallback-state:%s".formatted(event.requestId());
            if (lifecycleAnnouncements.add(key)) {
                addSystemMessage("Переход на fallback-модель для завершения ответа.");
            }
        }
        if (event.state() == ChatRequestState.PARTIAL_DONE) {
            String key = "continuation:%s".formatted(event.requestId());
            if (lifecycleAnnouncements.add(key)) {
                addSystemMessage("Получен частичный ответ. Можно продолжить генерацию.");
            }
        }
    }

    private void refreshElapsedClock() {
        if (lifecycleStartEpochMs <= 0L) {
            lifecycleElapsedLabel.setText("00:00");
            return;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - lifecycleStartEpochMs);
        lifecycleElapsedLabel.setText(formatElapsed(elapsedMs));
    }

    private void refreshImageElapsedClock() {
        if (imageLifecycleStartEpochMs <= 0L) {
            imageLifecycleElapsedLabel.setText("00:00");
            return;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - imageLifecycleStartEpochMs);
        imageLifecycleElapsedLabel.setText(formatElapsed(elapsedMs));
    }

    private String resolveLifecycleStateText(ChatRequestState state) {
        return switch (state) {
            case QUEUED -> "В очереди";
            case SENDING -> "Отправка";
            case SUMMARIZING -> "Сжатие контекста";
            case WAITING_PROVIDER -> "Ожидание модели";
            case GENERATING -> "Генерация ответа";
            case POST_PROCESSING -> "Финализация";
            case DONE -> "Готово";
            case RETRYING -> "Повторная попытка";
            case FALLBACK_MODEL -> "Резервная модель";
            case PARTIAL_DONE -> "Частичный ответ";
            case FAILED -> "Ошибка";
            case CANCELLED -> "Отменено";
        };
    }

    private int lifecycleStepIndex(ChatRequestState state) {
        return switch (state) {
            case QUEUED -> 0;
            case SENDING -> 1;
            case SUMMARIZING -> 1;
            case WAITING_PROVIDER -> 2;
            case RETRYING -> 2;
            case GENERATING -> 3;
            case FALLBACK_MODEL -> 3;
            case POST_PROCESSING -> 4;
            case DONE, PARTIAL_DONE, FAILED, CANCELLED -> 5;
        };
    }

    private String resolveImageLifecycleHeadline(ImageRequestState state) {
        return switch (state) {
            case QUEUED -> "В очереди";
            case SENDING -> "Отправка";
            case PROVIDER_ACCEPTED -> "Принято";
            case POLLING -> "Ожидание результата";
            case DOWNLOADING -> "Скачивание";
            case SAVING -> "Сохранение";
            case DONE -> "Готово";
            case RETRYING -> "Повторная попытка";
            case FALLBACK_MODEL -> "Резервная модель";
            case RESUMING -> "Восстановление";
            case PAUSED -> "Пауза";
            case FAILED -> "Ошибка";
            case CANCELLED -> "Отменено";
        };
    }

    private String resolveImageLifecycleDetail(ImageRequestEvent event) {
        if (event == null || event.message() == null || event.message().isBlank()) {
            return "";
        }
        return event.message().trim();
    }

    private int imageLifecycleStepIndex(ImageRequestState state) {
        return switch (state) {
            case QUEUED -> 0;
            case SENDING -> 1;
            case PROVIDER_ACCEPTED -> 2;
            case POLLING, RETRYING, RESUMING -> 3;
            case DOWNLOADING, FALLBACK_MODEL -> 4;
            case SAVING -> 5;
            case PAUSED -> 3;
            case DONE, FAILED, CANCELLED -> 6;
        };
    }

    private String buildLifecycleTooltip(ChatRequestEvent event, boolean fallbackDetected) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Стадия: ").append(resolveLifecycleStateText(event.state())).append('\n');
        tooltip.append("Запрос: ").append(safeTooltip(event.requestId())).append('\n');
        tooltip.append("Переписка: ").append(safeTooltip(event.conversationId())).append('\n');
        tooltip.append("Попытка: ").append(event.progress().attempt()).append('/').append(event.progress().maxAttempts()).append('\n');
        if (event.model() != null && !event.model().isBlank()) {
            tooltip.append("Модель: ").append(event.model().trim()).append('\n');
        }
        if (fallbackDetected) {
            tooltip.append("Fallback: включен\n");
        }
        tooltip.append("Время: ").append(formatElapsed(event.progress().elapsedMs()));
        if (event.message() != null && !event.message().isBlank()) {
            tooltip.append('\n').append("Детали: ").append(event.message().trim());
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            String statusCode = event.metadata().get("statusCode");
            if (statusCode != null && !statusCode.isBlank()) {
                tooltip.append('\n').append("HTTP: ").append(statusCode.trim());
            }
            String jobState = event.metadata().get("jobState");
            if (jobState != null && !jobState.isBlank()) {
                tooltip.append('\n').append("Job state: ").append(jobState.trim());
            }
        }
        return tooltip.toString();
    }

    private String buildImageLifecycleTooltip(ImageRequestEvent event, boolean fallbackDetected) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("Тип: Генерация изображения").append('\n');
        tooltip.append("Стадия: ").append(resolveImageLifecycleHeadline(event.state())).append('\n');
        tooltip.append("Job: ").append(safeTooltip(event.jobId())).append('\n');
        tooltip.append("Request: ").append(safeTooltip(event.requestId())).append('\n');
        tooltip.append("Переписка: ").append(safeTooltip(event.conversationId())).append('\n');
        tooltip.append("Попытка: ").append(event.progress().attempt()).append('/').append(event.progress().maxAttempts()).append('\n');
        if (event.model() != null && !event.model().isBlank()) {
            tooltip.append("Модель: ").append(event.model().trim()).append('\n');
        }
        if (fallbackDetected) {
            tooltip.append("Fallback: включен").append('\n');
        }
        tooltip.append("Время: ").append(formatElapsed(event.progress().elapsedMs()));
        if (event.message() != null && !event.message().isBlank()) {
            tooltip.append('\n').append("Детали: ").append(event.message().trim());
        }
        if (event.metadata() != null && !event.metadata().isEmpty()) {
            String statusCode = event.metadata().get("statusCode");
            if (statusCode != null && !statusCode.isBlank()) {
                tooltip.append('\n').append("HTTP: ").append(statusCode.trim());
            }
            String remoteUrl = event.metadata().get("remoteUrl");
            if (remoteUrl != null && !remoteUrl.isBlank()) {
                tooltip.append('\n').append("URL: ").append(remoteUrl.trim());
            }
            String savedPath = event.metadata().get("savedPath");
            if (savedPath != null && !savedPath.isBlank()) {
                tooltip.append('\n').append("Файл: ").append(savedPath.trim());
            }
        }
        return tooltip.toString();
    }

    private String safeTooltip(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value.trim();
    }

    private String formatElapsed(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private void setLifecycleStatusVisible(boolean visible) {
        lifecycleStatusBar.setVisible(visible);
        lifecycleStatusBar.setManaged(visible);
    }

    private void setImageLifecycleStatusVisible(boolean visible) {
        imageLifecycleStatusBar.setVisible(visible);
        imageLifecycleStatusBar.setManaged(visible);
    }

    private void resetLifecycleVisualState() {
        lifecycleElapsedTimeline.stop();
        lifecycleHideDelay.stop();
        lifecycleStartEpochMs = 0L;
        lifecyclePrimaryModel = "";
        lifecycleActiveModel = "";
        lifecycleLastAttempt = 1;
        lifecycleAnnouncements.clear();
        lifecycleProgressBar.setProgress(0.0);
        lifecycleStatusLabel.setText("Готово");
        lifecycleElapsedLabel.setText("00:00");
        lifecycleAttemptLabel.setText("Попытка 1/1");
        lifecycleDetailLabel.setText("");
        lifecycleTooltip.setText("");
        lifecycleSpinner.setVisible(false);
        lifecycleSpinner.setManaged(false);
        for (Region dot : lifecycleStepDots) {
            dot.getStyleClass().removeAll(
                "chat-lifecycle-step-dot-active",
                "chat-lifecycle-step-dot-done",
                "chat-lifecycle-step-dot-error",
                "chat-lifecycle-step-dot-cancelled"
            );
        }
    }

    private void resetImageLifecycleVisualState() {
        imageLifecycleElapsedTimeline.stop();
        imageLifecycleHideDelay.stop();
        imageLifecycleStartEpochMs = 0L;
        imageLifecyclePrimaryModel = "";
        imageLifecycleActiveModel = "";
        imageLifecycleLastAttempt = 1;
        imageLifecycleAnnouncements.clear();
        imageLifecycleProgressBar.setProgress(0.0);
        imageLifecycleStatusLabel.setText("Готово");
        imageLifecycleElapsedLabel.setText("00:00");
        imageLifecycleAttemptLabel.setText("Попытка 1/1");
        imageLifecycleDetailLabel.setText("");
        imageLifecycleTooltip.setText("");
        imageLifecycleSpinner.setVisible(false);
        imageLifecycleSpinner.setManaged(false);
        configureImageLifecycleActionButton(imageLifecyclePrimaryActionButton, null, null);
        configureImageLifecycleActionButton(imageLifecycleSecondaryActionButton, null, null);
        for (Region dot : imageLifecycleStepDots) {
            dot.getStyleClass().removeAll(
                "chat-lifecycle-step-dot-active",
                "chat-lifecycle-step-dot-done",
                "chat-lifecycle-step-dot-error",
                "chat-lifecycle-step-dot-cancelled"
            );
        }
    }

    private void showGreeting() {
        addBotMessage("Привет! Я ИИ-ассистент НейроПоток. Готов помочь с планированием, декомпозицией задач и ответами на вопросы.");
    }

    private void persistMessage(String role, String content) {
        if (currentConversation == null) {
            return;
        }
        ChatMessage message = new ChatMessage(
            UUID.randomUUID().toString(),
            currentConversation.getId(),
            role,
            content,
            nextMessageSeq,
            LocalDateTime.now().toString()
        );
        nextMessageSeq++;
        db.saveChatMessage(message);
        db.touchChatConversation(currentConversation.getId());
    }

    private void persistMediaAttachmentMessage(UserMediaAttachmentPayload payload) {
        if (currentConversation == null) {
            return;
        }
        UserMediaAttachmentPayload normalized = payload == null ? UserMediaAttachmentPayload.empty() : payload.normalized();
        String conversationId = currentConversation.getId();
        String runtimeKey = UUID.randomUUID().toString();
        UserMediaAttachmentPayload runtimePayload = normalized.withRuntimeKey(runtimeKey);
        rememberRuntimeMediaPayload(conversationId, runtimePayload);
        persistMessage("user", encodeMediaMessage(runtimePayload.forPersistence()));
    }

    private void rememberRuntimeMediaPayload(String conversationId, UserMediaAttachmentPayload payload) {
        if (conversationId == null || conversationId.isBlank() || payload == null || payload.runtimeKey().isBlank()) {
            return;
        }
        runtimeMediaPayloadCache.put(payload.runtimeKey(), payload);
        runtimeMediaPayloadKeysByConversation
            .computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet())
            .add(payload.runtimeKey());
    }

    private void removeRuntimeMediaPayloadsForConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        Set<String> keys = runtimeMediaPayloadKeysByConversation.remove(conversationId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                runtimeMediaPayloadCache.remove(key);
            }
        }
    }

    private void clearRuntimeMediaPayloadCache() {
        runtimeMediaPayloadCache.clear();
        runtimeMediaPayloadKeysByConversation.clear();
    }

    private void updateConversationTitle(String conversationId, String title) {
        if (conversationId == null || title == null) {
            return;
        }
        db.updateChatConversationTitle(conversationId, title);
        for (int i = 0; i < conversationCombo.getItems().size(); i++) {
            ChatConversation conversation = conversationCombo.getItems().get(i);
            if (conversationId.equals(conversation.getId())) {
                conversation.setTitle(title);
                conversationCombo.getItems().set(i, conversation);
                if (currentConversation != null && conversationId.equals(currentConversation.getId())) {
                    currentConversation = conversation;
                    conversationCombo.setValue(conversation);
                }
                break;
            }
        }
    }

    private boolean isPlaceholderTitle(ChatConversation conversation) {
        if (conversation == null) {
            return true;
        }
        String title = conversation.getTitle();
        return title == null || title.isBlank() || title.startsWith(DEFAULT_CONVERSATION_TITLE);
    }

    private String sanitizeTitle(String title, String fallback) {
        String cleaned = title == null ? "" : title.trim();
        cleaned = cleaned.replace("\"", "").replace("\n", " ").replace("\r", " ").trim();
        if (cleaned.isEmpty() || "null".equalsIgnoreCase(cleaned)) {
            cleaned = fallback;
        }
        cleaned = cleaned.trim();
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60) + "...";
        }
        return cleaned.isEmpty() ? DEFAULT_CONVERSATION_TITLE : cleaned;
    }

    private String fallbackTitleFromUser(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_CONVERSATION_TITLE;
        }
        String[] parts = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && i < 6; i++) {
            if (i > 0) sb.append(" ");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private String fallbackTitleFromAttachments(List<Path> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return DEFAULT_CONVERSATION_TITLE;
        }
        Path first = attachments.get(0);
        String fileName = first != null && first.getFileName() != null
            ? first.getFileName().toString()
            : "Вложение";
        return attachments.size() == 1 ? fileName : "Вложения: " + fileName;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() && pendingMediaAttachments.isEmpty()) return;

        if (latestRequestEvent != null
            && latestRequestEvent.state() == ChatRequestState.SUMMARIZING
            && pendingRequest != null
            && !pendingRequest.isDone()) {
            return;
        }
        
        // Cancel any pending request first
        if (pendingRequest != null && !pendingRequest.isDone()) {
            pendingRequest.cancel(true);
        }

        if (currentConversation == null) {
            createNewConversation();
        }

        if (generateImageCheckBox.isSelected()) {
            sendImagePrompt(text);
            return;
        }

        String conversationId = currentConversation.getId();
        boolean shouldGenerateTitle = nextMessageSeq == 0 && isPlaceholderTitle(currentConversation);
        String titleFallback = text.isBlank()
            ? fallbackTitleFromAttachments(pendingMediaAttachments)
            : fallbackTitleFromUser(text);
        String requestModel = AiClientFactory.getInstance().getActiveClient().getDefaultModel();
        cancellationNoticeShown = false;
        lifecycleHideDelay.stop();
        setLifecycleStatusVisible(true);
        resetStreamingMessageState();

        List<Path> copiedAttachments;
        try {
            copiedAttachments = copyAttachmentsToChatUploads(pendingMediaAttachments);
        } catch (Exception e) {
            UiErrorNotifier.showMappedError(
                ownerWindow(),
                isDark,
                "Загрузка файлов",
                e,
                ErrorCode.IO_WRITE_FAILED,
                "Не удалось подготовить вложения к отправке.",
                true,
                "operation", "copyAttachmentsToChatUploads",
                "attachmentsCount", pendingMediaAttachments.size()
            );
            return;
        }

        if (!text.isBlank()) {
            persistMessage("user", text);
            addUserMessage(text);
        }
        for (Path attachmentPath : copiedAttachments) {
            PendingAttachment attachment = resolvePendingAttachment(attachmentPath);
            if (attachment != null && attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.IMAGE) {
                persistMessage("user", encodeImageMessage(attachmentPath));
                addUserImageMessage(attachmentPath.toString());
            } else {
                UserMediaAttachmentPayload payload = UserMediaAttachmentPayload.fromPath(attachmentPath, attachment);
                persistMediaAttachmentMessage(payload);
                addUserAttachmentMessage(payload);
            }
        }
        inputField.clear();
        clearAttachedMedia();

        HBox typing = createTypingIndicatorWithCancel();
        currentTypingIndicator = typing;
        messagesBox.getChildren().add(typing);
        scrollToBottom();
        
        // Show cancel button, hide send button
        setRequestInProgress(true);

        String requestId = AsyncContext.ensureRequestId();
        activeChatRequestId = requestId;
        CompletableFuture<String> request = copiedAttachments.isEmpty()
            ? chatService.sendMessage(conversationId, text)
            : chatService.sendMessageWithMediaAttachments(conversationId, text, copiedAttachments);
        CompletableFuture<String> observedRequest = AsyncErrorHandler.observeFuture(
            request,
            ownerWindow(),
            isDark,
            "Ошибка ИИ-запроса",
            ErrorCode.AI_REQUEST_FAILED,
            "Не удалось получить ответ ассистента.",
            true,
            "chat.message.request.failed",
            "operation", copiedAttachments.isEmpty() ? "sendMessage" : "sendMessageWithMediaAttachments",
            "conversationId", conversationId,
            "model", requestModel,
            "attachmentsCount", copiedAttachments.size(),
            "requestId", requestId
        );
        pendingRequest = request;
        refreshContextUsageIndicator();

        observedRequest.thenAccept(response ->
            Platform.runLater(() -> {
                pendingRequest = null;
                removeTypingIndicator();
                setRequestInProgress(false);
                
                // null response means cancelled
                if (response == null) {
                    showCancellationMessageOnce("Запрос отменён");
                    return;
                }

                String resolvedModel = streamingModelName == null || streamingModelName.isBlank()
                    ? requestModel
                    : streamingModelName;
                String finalResponse = response;
                if (hasStreamingContentForRequest(requestId)) {
                    finalResponse = finishStreamingContent(requestId, response, resolvedModel);
                } else {
                    addBotMessage(encodeModelMessage(resolvedModel, response));
                }
                persistMessage("assistant", encodeModelMessage(resolvedModel, finalResponse));

                if (shouldGenerateTitle) {
                    String titleSeed = text.isBlank() ? titleFallback : text;
                    chatService.generateConversationTitle(titleSeed, finalResponse)
                        .thenAccept(title -> Platform.runLater(() -> {
                            String finalTitle = sanitizeTitle(title, titleFallback);
                            updateConversationTitle(conversationId, finalTitle);
                        }));
                }
            })
        ).exceptionally(ex -> {
            Platform.runLater(() -> {
                pendingRequest = null;
                removeTypingIndicator();
                setRequestInProgress(false);
                
                // Check for cancellation
                Throwable cause = AsyncContext.unwrap(ex);
                if (cause instanceof java.util.concurrent.CancellationException || request.isCancelled()) {
                    showCancellationMessageOnce("Запрос отменён");
                    if (hasStreamingContentForRequest(requestId)) {
                        finishStreamingContent(requestId, streamingAccumulatedText, streamingModelName);
                    } else {
                        resetStreamingMessageState();
                    }
                } else {
                    resetStreamingMessageState();
                    String errorMsg = "Ошибка соединения. Попробуйте позже.";
                    addBotMessage(encodeModelMessage(requestModel, errorMsg));
                    persistMessage("assistant", encodeModelMessage(requestModel, errorMsg));
                }
            });
            return null;
        });
    }

    private void sendImagePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return;
        }
        
        // Cancel any pending request first
        if (pendingRequest != null && !pendingRequest.isDone()) {
            if (activeImageJobId != null && !activeImageJobId.isBlank()) {
                imageGenerationService.cancelRequest(activeImageJobId);
            } else if (activeChatRequestId != null && !activeChatRequestId.isBlank()) {
                chatService.cancelRequest(activeChatRequestId);
            } else {
                pendingRequest.cancel(true);
            }
        }
        
        if (currentConversation == null) {
            createNewConversation();
        }

        final ImageGenerationService.ImageGenerationOptions options;
        try {
            options = imageGenerationService.loadOptionsFromConfig();
        } catch (IllegalArgumentException ex) {
            addSystemMessage("Проверьте настройки генерации изображения: " + ex.getMessage());
            return;
        }

        String conversationId = currentConversation != null ? currentConversation.getId() : null;
        boolean shouldGenerateTitle = nextMessageSeq == 0 && isPlaceholderTitle(currentConversation);
        String titleFallback = fallbackTitleFromUser(prompt);
        cancellationNoticeShown = false;
        resetLifecycleVisualState();
        setLifecycleStatusVisible(false);
        resetImageLifecycleVisualState();
        setImageLifecycleStatusVisible(false);
        resetStreamingMessageState();

        persistMessage("user", prompt);
        addUserMessage(prompt);
        inputField.clear();

        String imageJobId = UUID.randomUUID().toString();
        activeChatRequestId = null;
        activeImageJobId = imageJobId;
        activeImageRequestId = "";
        latestImageRequestEvent = null;

        HBox generating = createImageGeneratingIndicatorWithCancel(imageJobId);
        currentTypingIndicator = generating;
        messagesBox.getChildren().add(generating);
        scrollToBottom();
        
        // Show cancel button, hide send button
        setRequestInProgress(true);

        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<ImageGenerationService.ImageGenerationResult> request = 
            imageGenerationService.generateImage(conversationId, imageJobId, prompt, options);
        pendingRequest = request;
        refreshContextUsageIndicator();
        observeManagedImageJob(imageJobId, request, shouldGenerateTitle, conversationId, titleFallback);
    }

    private void observeManagedImageJob(
        String imageJobId,
        CompletableFuture<ImageGenerationService.ImageGenerationResult> request,
        boolean shouldGenerateTitle,
        String conversationId,
        String titleFallback
    ) {
        if (request == null) {
            return;
        }
        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<ImageGenerationService.ImageGenerationResult> observedRequest = AsyncErrorHandler.observeFuture(
            request,
            ownerWindow(),
            isDark,
            "Ошибка генерации изображения",
            ErrorCode.AI_REQUEST_FAILED,
            "Не удалось сгенерировать изображение. Попробуйте позже.",
            true,
            "chat.image.request.failed",
            "operation", "managedImageJob",
            "conversationId", conversationId == null ? "" : conversationId,
            "imageJobId", imageJobId == null ? "" : imageJobId,
            "requestId", requestId
        );

        observedRequest.thenAccept(result -> Platform.runLater(() -> {
                boolean stillCurrent = pendingRequest == request;
                if (stillCurrent) {
                    pendingRequest = null;
                }
                if (request.isCancelled()) {
                    clearActiveImageRequestIfMatches(imageJobId);
                    return;
                }
                resetStreamingMessageState();

                removeImageGeneratingIndicator(imageJobId);
                if (stillCurrent) {
                    setRequestInProgress(false);
                }
                latestImageJobSnapshot = imageGenerationService.getJob(imageJobId);
                updateImageJobActionControls(latestImageRequestEvent);

                Path savedPath = result != null ? result.savedPath() : null;
                if (savedPath == null) {
                    String errorMsg = "Не удалось сохранить изображение (пустой результат).";
                    addBotMessage(errorMsg);
                    persistMessage("assistant", errorMsg);
                    clearActiveImageRequestIfMatches(imageJobId);
                    return;
                }

                ImageOutputPayload payload = buildSuccessImageOutputPayload(latestImageJobSnapshot, savedPath);
                String encodedImage = encodeImageOutputMessage(payload);
                if (currentConversation == null || currentConversation.getId() == null || currentConversation.getId().equals(conversationId)) {
                    addBotImageOutputMessage(payload);
                }
                persistMessage("assistant", encodedImage);

                if (shouldGenerateTitle && conversationId != null) {
                    String assistantSummary = "Изображение сохранено: " + savedPath.getFileName();
                    chatService.generateConversationTitle(latestImageJobSnapshot == null ? "" : latestImageJobSnapshot.getPrompt(), assistantSummary)
                        .thenAccept(title -> Platform.runLater(() -> {
                            String finalTitle = sanitizeTitle(title, titleFallback);
                            updateConversationTitle(conversationId, finalTitle);
                        }));
                }
                clearActiveImageRequestIfMatches(imageJobId);
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    boolean stillCurrent = pendingRequest == request;
                    if (stillCurrent) {
                        pendingRequest = null;
                    }
                    if (stillCurrent) {
                        setRequestInProgress(false);
                    }
                    latestImageJobSnapshot = imageGenerationService.getJob(imageJobId);
                    updateImageJobActionControls(latestImageRequestEvent);

                    Throwable cause = AsyncContext.unwrap(ex);
                    if (cause instanceof ImageGenerationService.ImageJobPausedException) {
                        removeImageGeneratingIndicator(imageJobId);
                        addSystemMessage("Image-job поставлен на паузу. Его можно возобновить позже.");
                        return;
                    }
                    if (cause instanceof java.util.concurrent.CancellationException || request.isCancelled()) {
                        removeImageGeneratingIndicator(imageJobId);
                        showCancellationMessageOnce("Генерация изображения отменена");
                        clearActiveImageRequestIfMatches(imageJobId);
                        return;
                    }

                    resetStreamingMessageState();
                    ImageJobSnapshot snapshot = latestImageJobSnapshot != null
                        ? latestImageJobSnapshot
                        : imageGenerationService.getJob(imageJobId);
                    ImageOutputPayload payload = buildFailureImageOutputPayload(snapshot, cause);
                    addBotImageOutputMessage(payload);
                    persistMessage("assistant", encodeImageOutputMessage(payload));
                });
                return null;
            });
    }

    private void addUserMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER_RIGHT);
        container.setPadding(new Insets(0, 0, 0, 40)); // Indent left

        Label msg = new Label(text);
        msg.setWrapText(true);
        msg.getStyleClass().add("chat-bubble-user");

        container.getChildren().add(msg);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private void addUserImageMessage(String imagePath) {
        HBox container = new HBox();
        container.setAlignment(Pos.TOP_RIGHT);
        container.setPadding(new Insets(0, 0, 0, 40)); // Indent left

        VBox bubble = new VBox(8);
        bubble.getStyleClass().addAll("chat-image-bubble", "chat-image-bubble-user");

        Label title = new Label("Изображение");
        title.getStyleClass().add("chat-image-title");

        Node imageNode = buildImageNode(imagePath);
        bubble.getChildren().addAll(title, imageNode);

        container.getChildren().add(bubble);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private void addUserAttachmentMessage(UserMediaAttachmentPayload payload) {
        if (payload == null) {
            return;
        }

        HBox container = new HBox();
        container.setAlignment(Pos.TOP_RIGHT);
        container.setPadding(new Insets(0, 0, 0, 40));

        VBox bubble = new VBox(8);
        bubble.getStyleClass().addAll("chat-file-bubble", "chat-file-bubble-user");

        Label title = new Label(payload.kindLabel());
        title.getStyleClass().add("chat-file-title");

        Label name = new Label(payload.fileName());
        name.getStyleClass().add("chat-file-name");
        name.setWrapText(true);

        Label meta = new Label(payload.metaLabel());
        meta.getStyleClass().add("chat-file-meta");

        bubble.getChildren().addAll(title, name, meta);
        container.getChildren().add(bubble);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private void addBotMessage(String text) {
        if (isImageOutputMessage(text)) {
            addBotImageOutputMessage(decodeImageOutputPayload(text));
            return;
        }
        if (isImageMessage(text)) {
            addBotImageMessage(decodeImagePath(text));
            return;
        }

        ModelTaggedMessage tagged = parseModelTaggedMessage(text);
        String modelName = tagged.model();
        String cleanText = tagged.content();

        HBox container = new HBox(10);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPadding(new Insets(0, 20, 0, 0));

        Node avatar = ChatBotAvatar.create(32);
        
        VBox msgBox = new VBox(4);
        msgBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(msgBox, Priority.ALWAYS);

        if (modelName != null && !modelName.isBlank()) {
            Label modelLabel = new Label("Модель: " + modelName);
            modelLabel.getStyleClass().add("chat-model-label");
            msgBox.getChildren().add(modelLabel);
        }

        TextArea messageArea = buildSelectableBotMessageArea(cleanText);

        // Кнопка копирования
        HBox actions = new HBox();
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button copyBtn = new Button();
        FontIcon copyIcon = FontIcon.of(MaterialDesignC.CONTENT_COPY, 12);
        copyIcon.getStyleClass().add("chat-copy-icon");
        copyBtn.setGraphic(copyIcon);
        copyBtn.getStyleClass().add("chat-copy-btn");
        copyBtn.setTooltip(new Tooltip("Копировать"));
        copyBtn.setOnAction(e -> {
            Clipboard.getSystemClipboard().setContent(
                java.util.Map.of(DataFormat.PLAIN_TEXT, cleanText)
            );
        });
        actions.getChildren().add(copyBtn);

        msgBox.getChildren().addAll(messageArea, actions);

        container.getChildren().addAll(avatar, msgBox);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private TextArea buildSelectableBotMessageArea(String text) {
        String safeText = text == null ? "" : text;
        TextArea messageArea = new TextArea(safeText);
        messageArea.getStyleClass().add("chat-selectable-text");
        messageArea.setEditable(false);
        messageArea.setWrapText(true);
        messageArea.setFocusTraversable(true);
        messageArea.setContextMenu(createRussianContextMenu(messageArea));
        messageArea.setPrefRowCount(estimateAssistantMessageRows(safeText));
        messageArea.setMinHeight(Region.USE_PREF_SIZE);
        messageArea.setPrefHeight(estimateAssistantMessageHeight(safeText));
        messageArea.setMaxHeight(420);
        return messageArea;
    }

    private int estimateAssistantMessageRows(String text) {
        if (text == null || text.isBlank()) {
            return 2;
        }
        int explicitLines = text.split("\\R", -1).length;
        int wrappedLines = Math.max(1, text.length() / 72);
        return Math.max(2, Math.min(18, explicitLines + wrappedLines));
    }

    private double estimateAssistantMessageHeight(String text) {
        return 24 + estimateAssistantMessageRows(text) * 18.0;
    }

    private void configureChatWebViewHeightAutoResize(WebView webView) {
        if (webView == null) {
            return;
        }
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState != Worker.State.SUCCEEDED) {
                return;
            }
            Platform.runLater(() -> {
                try {
                    Object result = webView.getEngine().executeScript(
                        "Math.max(document.body.scrollHeight, document.body.offsetHeight, " +
                            "document.documentElement.scrollHeight, document.documentElement.offsetHeight)"
                    );
                    if (result instanceof Number number) {
                        double height = number.doubleValue() + 10;
                        webView.setPrefHeight(Math.min(Math.max(height, 50), 400));
                    }
                } catch (Exception ignored) {
                }
                scrollToBottom();
            });
        });
    }

    private void updateWebViewMarkdownContent(WebView webView, String markdown) {
        if (webView == null) {
            return;
        }
        String html = convertMarkdownToHtml(markdown == null ? "" : markdown);
        String fullHtml = getChatHtmlTemplate(html);
        webView.getEngine().loadContent(fullHtml);
    }

    private void addBotImageMessage(String imagePath) {
        addBotImageOutputMessage(new ImageOutputPayload(
            "success",
            "",
            "",
            currentConversation == null ? "" : currentConversation.getId(),
            "",
            "",
            imagePath == null ? "" : imagePath,
            "",
            "",
            "",
            "",
            1,
            "",
            "",
            "",
            ""
        ));
    }

    private void addBotImageOutputMessage(ImageOutputPayload payload) {
        ImageOutputPayload safePayload = payload == null ? ImageOutputPayload.empty() : payload.normalized();
        HBox container = new HBox(10);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPadding(new Insets(0, 20, 0, 0));

        Node avatar = ChatBotAvatar.create(32);

        VBox msgBox = new VBox(8);
        msgBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(msgBox, Priority.ALWAYS);

        VBox card = new VBox(10);
        card.getStyleClass().addAll(
            "chat-image-output-card",
            safePayload.isError() ? "chat-image-output-card-error" : "chat-image-output-card-success"
        );

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox headerText = new VBox(2);
        HBox.setHgrow(headerText, Priority.ALWAYS);

        Label headline = new Label(resolveImageOutputHeadline(safePayload));
        headline.getStyleClass().add("chat-image-output-headline");

        Label detail = new Label(resolveImageOutputDetail(safePayload));
        detail.setWrapText(true);
        detail.getStyleClass().add("chat-image-output-detail");

        headerText.getChildren().addAll(headline, detail);

        Label statusBadge = new Label(safePayload.isError() ? "Ошибка" : "Готово");
        statusBadge.getStyleClass().addAll(
            "chat-image-output-status",
            safePayload.isError() ? "chat-image-output-status-error" : "chat-image-output-status-success"
        );

        header.getChildren().addAll(headerText, statusBadge);
        card.getChildren().add(header);

        if (!safePayload.savedPath().isBlank()) {
            VBox bubble = new VBox(8);
            bubble.getStyleClass().add("chat-image-bubble");

            Label title = new Label("Изображение");
            title.getStyleClass().add("chat-image-title");

            Node imageNode = buildImageNode(safePayload.savedPath());
            bubble.getChildren().addAll(title, imageNode);
            card.getChildren().add(bubble);
        }

        String metaText = buildImageOutputMeta(safePayload);
        if (!metaText.isBlank()) {
            Label meta = new Label(metaText);
            meta.setWrapText(true);
            meta.getStyleClass().add("chat-image-output-meta");
            card.getChildren().add(meta);
        }

        if (!safePayload.prompt().isBlank()) {
            Label promptLabel = new Label("Prompt: " + safePayload.prompt());
            promptLabel.setWrapText(true);
            promptLabel.getStyleClass().add("chat-image-output-prompt");
            card.getChildren().add(promptLabel);
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.getStyleClass().add("chat-image-output-actions");
        appendImageOutputActionButtons(actions, safePayload);
        if (!actions.getChildren().isEmpty()) {
            card.getChildren().add(actions);
        }

        msgBox.getChildren().add(card);
        container.getChildren().addAll(avatar, msgBox);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private Node buildImageNode(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            Label missing = new Label("Путь к изображению не указан.");
            missing.getStyleClass().add("chat-image-missing");
            return missing;
        }

        try {
            File file = Path.of(imagePath).toFile();
            if (!file.exists() || !file.isFile()) {
                Label missing = new Label("Файл не найден.");
                missing.getStyleClass().add("chat-image-missing");
                return missing;
            }

            Image image = new Image(file.toURI().toString(), 320, 0, true, true, true);
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(320);
            view.getStyleClass().add("chat-image-view");
            return view;
        } catch (Exception e) {
            Label missing = new Label("Не удалось загрузить изображение.");
            missing.getStyleClass().add("chat-image-missing");
            return missing;
        }
    }

    private ImageOutputPayload buildSuccessImageOutputPayload(ImageJobSnapshot snapshot, Path savedPath) {
        String absolutePath = savedPath == null ? "" : savedPath.toAbsolutePath().toString();
        return new ImageOutputPayload(
            "success",
            snapshot == null ? "" : snapshot.getJobId(),
            snapshot == null ? "" : snapshot.getRequestId(),
            snapshot == null ? "" : snapshot.getConversationId(),
            safeSnapshotModel(snapshot),
            snapshot == null ? "" : snapshot.getPrompt(),
            absolutePath,
            snapshot == null ? "" : snapshot.getRemoteUrl(),
            "Изображение готово",
            absolutePath.isBlank() ? "Файл сохранён." : "Файл сохранён: " + Path.of(absolutePath).getFileName(),
            "",
            snapshot == null ? 1 : Math.max(1, snapshot.getAttempt()),
            snapshot == null || snapshot.getState() == null ? ImageJobState.DONE.name() : snapshot.getState().name(),
            snapshot == null ? "" : snapshot.getSize(),
            snapshot == null ? "" : snapshot.getAspectRatio(),
            snapshot == null ? "" : snapshot.getResolution()
        ).normalized();
    }

    private ImageOutputPayload buildFailureImageOutputPayload(ImageJobSnapshot snapshot, Throwable cause) {
        ImageFailurePresentation failure = classifyImageFailure(snapshot, cause);
        return new ImageOutputPayload(
            "error",
            snapshot == null ? "" : snapshot.getJobId(),
            snapshot == null ? "" : snapshot.getRequestId(),
            snapshot == null ? "" : snapshot.getConversationId(),
            safeSnapshotModel(snapshot),
            snapshot == null ? "" : snapshot.getPrompt(),
            snapshot == null ? "" : snapshot.getSavedPath(),
            snapshot == null ? "" : snapshot.getRemoteUrl(),
            failure.headline(),
            failure.detail(),
            failure.code(),
            snapshot == null ? 1 : Math.max(1, snapshot.getAttempt()),
            snapshot == null || snapshot.getState() == null ? ImageJobState.FAILED.name() : snapshot.getState().name(),
            snapshot == null ? "" : snapshot.getSize(),
            snapshot == null ? "" : snapshot.getAspectRatio(),
            snapshot == null ? "" : snapshot.getResolution()
        ).normalized();
    }

    private ImageFailurePresentation classifyImageFailure(ImageJobSnapshot snapshot, Throwable cause) {
        String raw = normalizeImageFailureText(snapshot, cause);
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("429")
            || lower.contains("rate limit")
            || lower.contains("too many requests")
            || lower.contains("quota")
            || lower.contains("лимит")) {
            return new ImageFailurePresentation(
                "rate-limit",
                "Превышен лимит запросов",
                "Провайдер временно ограничил генерацию. Подождите немного и повторите попытку."
            );
        }
        if (lower.contains("timeout")
            || lower.contains("timed out")
            || lower.contains("time out")
            || lower.contains("budget")
            || lower.contains("ожидани")
            || lower.contains("истекло время")) {
            return new ImageFailurePresentation(
                "timeout",
                "Слишком долго ждали результат",
                "Генерация не завершилась в допустимое время. Можно повторить позже или дождаться более свободного окна у провайдера."
            );
        }
        if (lower.contains("validation")
            || lower.contains("invalid")
            || lower.contains("bad request")
            || lower.contains("prompt")
            || lower.contains("400")) {
            return new ImageFailurePresentation(
                "validation",
                "Параметры изображения не приняты",
                "Проверьте prompt и параметры генерации. Скорее всего, провайдер отклонил запрос как некорректный."
            );
        }
        if (lower.contains("provider")
            || lower.contains("api")
            || lower.contains("status")
            || lower.contains("http")
            || lower.contains("503")
            || lower.contains("502")
            || lower.contains("504")) {
            return new ImageFailurePresentation(
                "provider",
                "Проблема на стороне провайдера",
                "Сервис генерации сейчас отвечает нестабильно. Повторная попытка обычно помогает."
            );
        }
        return new ImageFailurePresentation(
            "unknown",
            "Не удалось завершить генерацию",
            raw.isBlank() ? "Во время генерации произошла неизвестная ошибка. Можно повторить запрос." : raw
        );
    }

    private String normalizeImageFailureText(ImageJobSnapshot snapshot, Throwable cause) {
        Throwable actual = AsyncContext.unwrap(cause);
        if (actual != null && actual.getMessage() != null && !actual.getMessage().isBlank()) {
            return actual.getMessage().trim();
        }
        if (snapshot != null && snapshot.getLastError() != null && !snapshot.getLastError().isBlank()) {
            return snapshot.getLastError().trim();
        }
        if (latestImageRequestEvent != null
            && snapshot != null
            && snapshot.getJobId() != null
            && snapshot.getJobId().equals(latestImageRequestEvent.jobId())
            && latestImageRequestEvent.message() != null
            && !latestImageRequestEvent.message().isBlank()) {
            return latestImageRequestEvent.message().trim();
        }
        return "";
    }

    private String resolveImageOutputHeadline(ImageOutputPayload payload) {
        if (payload == null) {
            return "Изображение";
        }
        if (!payload.headline().isBlank()) {
            return payload.headline();
        }
        return payload.isError() ? "Ошибка генерации изображения" : "Изображение готово";
    }

    private String resolveImageOutputDetail(ImageOutputPayload payload) {
        if (payload == null) {
            return "";
        }
        if (!payload.detail().isBlank()) {
            return payload.detail();
        }
        return payload.isError()
            ? "Не удалось завершить генерацию изображения."
            : "Изображение успешно сохранено и готово к использованию.";
    }

    private String buildImageOutputMeta(ImageOutputPayload payload) {
        if (payload == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (!payload.model().isBlank()) {
            parts.add("Модель: " + payload.model());
        }
        if (!payload.requestId().isBlank()) {
            parts.add("Request: " + payload.requestId());
        }
        if (!payload.jobId().isBlank()) {
            parts.add("Job: " + payload.jobId());
        }
        if (payload.attempt() > 0) {
            parts.add("Попытка: " + payload.attempt());
        }
        if (!payload.savedPath().isBlank()) {
            parts.add("Путь: " + payload.savedPath());
        }
        if (!payload.errorClass().isBlank()) {
            parts.add("Класс ошибки: " + payload.errorClass());
        }
        return String.join(" • ", parts);
    }

    private void appendImageOutputActionButtons(HBox actions, ImageOutputPayload payload) {
        if (actions == null || payload == null) {
            return;
        }
        if (!payload.savedPath().isBlank()) {
            actions.getChildren().add(createImageOutputActionButton("Открыть", () -> openGeneratedImage(payload.savedPath())));
            actions.getChildren().add(createImageOutputActionButton("Копировать путь", () -> copyPlainText(payload.savedPath())));
        }
        if (!payload.prompt().isBlank()) {
            actions.getChildren().add(createImageOutputActionButton("Скопировать prompt", () -> copyPlainText(payload.prompt())));
        }
        if (canRepeatImageOutputPayload(payload)) {
            actions.getChildren().add(createSummarizationBlockedActionButton("Повторить", () -> repeatImageOutputPayload(payload)));
        }
        if (canClearImageJobs(payload)) {
            Button clearButton = createImageOutputActionButton("Очистить jobs", () -> clearImageJobsForPayload(payload));
            clearButton.getStyleClass().add("chat-image-output-action-btn-danger");
            actions.getChildren().add(clearButton);
        }
    }

    private Button createImageOutputActionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("chat-image-output-action-btn");
        button.setOnAction(e -> {
            if (action != null) {
                action.run();
            }
        });
        return button;
    }

    private Button createSummarizationBlockedActionButton(String text, Runnable action) {
        Button button = createImageOutputActionButton(text, action);
        summarizeBlockedActionButtons.add(button);
        button.setDisable(isSummarizationInteractionLocked());
        return button;
    }

    private void openGeneratedImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            showChatAlert(Alert.AlertType.WARNING, "Открыть изображение", "Путь к файлу не найден.");
            return;
        }
        try {
            Path path = Path.of(imagePath);
            if (!Files.exists(path)) {
                showChatAlert(Alert.AlertType.WARNING, "Открыть изображение", "Файл изображения не найден.");
                return;
            }
            Path normalizedPath = path.toAbsolutePath().normalize();
            AsyncContext.runAsync(() -> {
                try {
                    launchGeneratedImageOpen(normalizedPath);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }, IMAGE_OPEN_EXECUTOR)
                .exceptionally(error -> {
                    Throwable actual = AsyncContext.unwrap(error);
                    LOG.error(
                        "chat.image.open.failed",
                        ErrorCode.IO_READ_FAILED,
                        actual,
                        "path", normalizedPath.toString(),
                        "os", System.getProperty("os.name", "")
                    );
                    Platform.runLater(() -> {
                        copyPlainText(normalizedPath.toString());
                        showChatAlert(
                            Alert.AlertType.ERROR,
                            "Открыть изображение",
                            "Не удалось открыть изображение. Путь скопирован в буфер."
                        );
                    });
                    return null;
                });
        } catch (Exception ex) {
            showChatAlert(Alert.AlertType.ERROR, "Открыть изображение", "Не удалось открыть изображение.");
        }
    }

    private void launchGeneratedImageOpen(Path path) throws IOException {
        if (tryLaunchGeneratedImageWithSystemCommand(path)) {
            return;
        }
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("Desktop API is not supported in current environment");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            throw new IOException("Desktop OPEN action is not supported in current environment");
        }
        desktop.open(path.toFile());
    }

    private boolean tryLaunchGeneratedImageWithSystemCommand(Path path) throws IOException {
        List<String> command = buildGeneratedImageOpenCommand(System.getProperty("os.name", ""), path);
        if (command.isEmpty()) {
            return false;
        }
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();
        return true;
    }

    private static List<String> buildGeneratedImageOpenCommand(String osName, Path path) {
        if (path == null) {
            return List.of();
        }
        String normalizedOs = osName == null ? "" : osName.trim().toLowerCase(Locale.ROOT);
        String absolutePath = path.toAbsolutePath().normalize().toString();
        if (normalizedOs.contains("mac")) {
            return List.of("open", absolutePath);
        }
        if (normalizedOs.contains("win")) {
            return List.of("explorer.exe", absolutePath);
        }
        if (normalizedOs.contains("nix")
            || normalizedOs.contains("nux")
            || normalizedOs.contains("linux")
            || normalizedOs.contains("freebsd")
            || normalizedOs.contains("unix")) {
            return List.of("xdg-open", absolutePath);
        }
        return List.of();
    }

    private void copyPlainText(String value) {
        Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, value == null ? "" : value));
    }

    private boolean canRepeatImageOutputPayload(ImageOutputPayload payload) {
        return payload != null
            && !payload.prompt().isBlank()
            && currentConversation != null
            && currentConversation.getId() != null;
    }

    private void repeatImageOutputPayload(ImageOutputPayload payload) {
        if (isSummarizationInteractionLocked()) {
            showChatAlert(Alert.AlertType.INFORMATION, "Сжатие контекста",
                "Дождитесь завершения сжатия контекста перед повторной генерацией."
            );
            return;
        }
        if (!canRepeatImageOutputPayload(payload)) {
            return;
        }
        ImageGenerationService.ImageGenerationOptions defaults = imageGenerationService.loadOptionsFromConfig();
        ImageGenerationService.ImageGenerationOptions options = new ImageGenerationService.ImageGenerationOptions(
            payload.model(),
            payload.size(),
            payload.aspectRatio(),
            payload.resolution(),
            defaults == null ? "" : defaults.quality(),
            defaults == null ? "" : defaults.outputFormat(),
            defaults == null ? "" : defaults.strength(),
            defaults == null ? "" : defaults.guidanceScale()
        );
        String conversationId = currentConversation.getId();
        String imageJobId = UUID.randomUUID().toString();
        activeChatRequestId = null;
        activeImageJobId = imageJobId;
        activeImageRequestId = "";
        latestImageRequestEvent = null;
        latestImageJobSnapshot = null;
        ensureImageGeneratingIndicator(imageJobId);
        setRequestInProgress(true);
        CompletableFuture<ImageGenerationService.ImageGenerationResult> request =
            imageGenerationService.generateImage(conversationId, imageJobId, payload.prompt(), options);
        pendingRequest = request;
        observeManagedImageJob(imageJobId, request, false, conversationId, "Изображение");
    }

    private boolean canClearImageJobs(ImageOutputPayload payload) {
        if (payload == null || payload.conversationId().isBlank()) {
            return false;
        }
        return currentConversation != null && payload.conversationId().equals(currentConversation.getId());
    }

    private void clearImageJobsForPayload(ImageOutputPayload payload) {
        if (!canClearImageJobs(payload) || !showClearImageJobsConfirmDialog()) {
            return;
        }
        db.deleteImageJobStatesByConversation(payload.conversationId());
        if (currentConversation != null && currentConversation.getId().equals(payload.conversationId())) {
            activeImageJobId = null;
            activeImageRequestId = null;
            latestImageRequestEvent = null;
            latestImageJobSnapshot = null;
            removeImageGeneratingIndicator(payload.jobId());
            resetImageLifecycleVisualState();
            setImageLifecycleStatusVisible(false);
        }
        addSystemMessage("Сохранённые image jobs для этой переписки очищены.");
    }

    private boolean showClearImageJobsConfirmDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Очистить image jobs");
        alert.setHeaderText("Удалить сохранённые image jobs текущей переписки?");
        alert.setContentText("Карточки сообщений и сохранённые файлы останутся, но очередь/resume-состояние будет очищено.");
        DialogPane pane = alert.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("styled-alert");
        ButtonType clearBtn = new ButtonType("Очистить jobs", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBtn = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(clearBtn, cancelBtn);
        return alert.showAndWait().orElse(cancelBtn) == clearBtn;
    }
    
    /** Конвертация Markdown в HTML */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        
        String html = markdown;
        
        // Экранируем HTML
        html = html.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        
        // Блоки кода (```code```)
        html = html.replaceAll("(?s)```(\\w*)\\n(.+?)```", "<pre><code>$2</code></pre>");
        html = html.replaceAll("(?s)```(.+?)```", "<pre><code>$1</code></pre>");
        
        // Заголовки
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");
        
        // Жирный текст
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        
        // Курсив
        html = html.replaceAll("(?<!\\*)\\*([^*]+?)\\*(?!\\*)", "<em>$1</em>");
        html = html.replaceAll("(?<!_)_([^_]+?)_(?!_)", "<em>$1</em>");
        
        // Код inline
        html = html.replaceAll("`([^`]+?)`", "<code class=\"inline\">$1</code>");
        
        // Списки
        html = html.replaceAll("(?m)^- (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\* (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");
        html = html.replaceAll("(<li>.*?</li>\\s*)+", "<ul>$0</ul>");
        
        // Переносы строк
        html = html.replace("\n\n", "</p><p>");
        html = html.replace("\n", "<br>");
        html = "<p>" + html + "</p>";
        
        // Убираем пустые параграфы
        html = html.replace("<p></p>", "");
        html = html.replace("<p><br></p>", "");
        
        return html;
    }
    
    /** HTML шаблон для чата с CSS стилями */
    private String getChatHtmlTemplate(String content) {
        String bgColor = isDark ? "#313244" : "#e6e9ef";
        String textColor = isDark ? "#cdd6f4" : "#4c4f69";
        String headingColor = isDark ? "#cba6f7" : "#8839ef";
        String codeColor = isDark ? "#a6e3a1" : "#40a02b";
        String codeBg = isDark ? "#1e1e2e" : "#dce0e8";
        String listColor = isDark ? "#f9e2af" : "#df8e1d";
        String strongColor = isDark ? "#89b4fa" : "#1e66f5";
        String codeBtnBg = isDark ? "#45475a" : "#ccd0da";
        String codeBtnText = isDark ? "#cdd6f4" : "#4c4f69";
        String codeBtnHover = isDark ? "#585b70" : "#bcc0cc";
        String codeBtnSuccess = isDark ? "#a6e3a1" : "#40a02b";
        String codeBtnError = isDark ? "#f38ba8" : "#d20f39";

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="UTF-8">
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: 'Segoe UI', 'PT Root UI', system-ui, sans-serif;
                    font-size: 13px;
                    line-height: 1.5;
                    color: %s;
                    background-color: %s;
                    padding: 10px 12px;
                    word-wrap: break-word;
                }
                h1, h2, h3 {
                    color: %s;
                    margin: 10px 0 6px 0;
                    font-weight: 600;
                }
                h1 { font-size: 16px; }
                h2 { font-size: 14px; }
                h3 { font-size: 13px; }
                p { margin: 6px 0; }
                strong { color: %s; font-weight: 600; }
                em { font-style: italic; }
                code.inline {
                    background-color: %s;
                    color: %s;
                    padding: 1px 5px;
                    border-radius: 4px;
                    font-family: 'JetBrains Mono', 'Consolas', monospace;
                    font-size: 12px;
                }
                pre {
                    background-color: %s;
                    border-radius: 6px;
                    padding: 10px;
                    margin: 8px 0;
                    overflow-x: auto;
                }
                pre code {
                    color: %s;
                    font-family: 'JetBrains Mono', 'Consolas', monospace;
                    font-size: 12px;
                    white-space: pre-wrap;
                }
                .code-block-wrap {
                    position: relative;
                    margin: 8px 0;
                }
                .code-block-wrap pre {
                    margin: 0;
                    padding-top: 34px;
                }
                .code-copy-btn {
                    position: absolute;
                    top: 6px;
                    right: 6px;
                    border: 0;
                    border-radius: 6px;
                    background: %s;
                    color: %s;
                    font-size: 11px;
                    font-weight: 600;
                    padding: 4px 8px;
                    cursor: pointer;
                    transition: background-color 120ms ease, color 120ms ease;
                }
                .code-copy-btn:hover {
                    background: %s;
                }
                .code-copy-btn.copied {
                    color: %s;
                }
                .code-copy-btn.error {
                    color: %s;
                }
                ul, ol {
                    margin: 6px 0;
                    padding-left: 18px;
                }
                li {
                    margin: 3px 0;
                }
                li::marker {
                    color: %s;
                }
            </style>
            </head>
            <body>%s
            <script>
                (function() {
                    function fallbackCopy(text) {
                        var area = document.createElement('textarea');
                        area.value = text;
                        area.setAttribute('readonly', '');
                        area.style.position = 'fixed';
                        area.style.left = '-9999px';
                        document.body.appendChild(area);
                        area.select();
                        var ok = false;
                        try {
                            ok = document.execCommand('copy');
                        } catch (e) {
                            ok = false;
                        }
                        document.body.removeChild(area);
                        return ok;
                    }

                    function updateButtonState(btn, text, cssClass) {
                        var original = btn.dataset.originalText || 'Копировать код';
                        btn.textContent = text;
                        if (cssClass) {
                            btn.classList.add(cssClass);
                        }
                        setTimeout(function() {
                            btn.textContent = original;
                            btn.classList.remove('copied');
                            btn.classList.remove('error');
                        }, 1200);
                    }

                    var blocks = document.querySelectorAll('pre');
                    for (var i = 0; i < blocks.length; i++) {
                        var pre = blocks[i];
                        if (pre.parentElement && pre.parentElement.classList.contains('code-block-wrap')) {
                            continue;
                        }
                        var wrapper = document.createElement('div');
                        wrapper.className = 'code-block-wrap';
                        pre.parentNode.insertBefore(wrapper, pre);
                        wrapper.appendChild(pre);

                        var btn = document.createElement('button');
                        btn.type = 'button';
                        btn.className = 'code-copy-btn';
                        btn.textContent = 'Копировать код';
                        btn.dataset.originalText = 'Копировать код';
                        btn.setAttribute('title', 'Копировать кодовый блок');
                        btn.setAttribute('aria-label', 'Копировать кодовый блок');
                        wrapper.insertBefore(btn, pre);

                        btn.addEventListener('click', function() {
                            var code = this.parentElement.querySelector('code');
                            var text = code ? code.innerText : this.parentElement.querySelector('pre').innerText;
                            var self = this;
                            if (!text) {
                                updateButtonState(self, 'Пусто', 'error');
                                return;
                            }
                            if (navigator.clipboard && navigator.clipboard.writeText) {
                                navigator.clipboard.writeText(text).then(function() {
                                    updateButtonState(self, 'Скопировано', 'copied');
                                }).catch(function() {
                                    var ok = fallbackCopy(text);
                                    updateButtonState(self, ok ? 'Скопировано' : 'Ошибка', ok ? 'copied' : 'error');
                                });
                            } else {
                                var ok = fallbackCopy(text);
                                updateButtonState(self, ok ? 'Скопировано' : 'Ошибка', ok ? 'copied' : 'error');
                            }
                        });
                    }
                })();
            </script>
            </body>
            </html>
            """, textColor, bgColor, headingColor, strongColor, codeBg, codeColor, codeBg, codeColor,
            codeBtnBg, codeBtnText, codeBtnHover, codeBtnSuccess, codeBtnError, listColor, content);
    }

    private HBox createTypingIndicator() {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);

        Node avatar = ChatBotAvatar.create(32);

        Label dots = new Label("●  ●  ●");
        dots.getStyleClass().add("chat-typing-dots");

        container.getChildren().addAll(avatar, dots);
        return container;
    }

    private HBox createImageGeneratingIndicator() {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);

        Node avatar = ChatBotAvatar.create(32);

        HBox bubble = new HBox(10);
        bubble.setAlignment(Pos.CENTER_LEFT);
        bubble.getStyleClass().add("chat-image-generating-bubble");

        Label label = new Label("Генерирую изображение...");
        label.getStyleClass().add("chat-image-generating-text");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.getStyleClass().add("chat-image-progress");

        bubble.getChildren().addAll(label, progress);

        container.getChildren().addAll(avatar, bubble);
        return container;
    }
    
    /**
     * Creates a typing indicator with an inline cancel button.
     */
    private HBox createTypingIndicatorWithCancel() {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);

        Node avatar = ChatBotAvatar.create(32);

        HBox bubble = new HBox(10);
        bubble.setAlignment(Pos.CENTER_LEFT);
        bubble.getStyleClass().add("chat-typing-bubble");

        Label dots = new Label("●  ●  ●");
        dots.getStyleClass().add("chat-typing-dots");
        
        Button inlineCancelBtn = new Button();
        FontIcon cancelIcon = FontIcon.of(MaterialDesignC.CLOSE_CIRCLE, 16);
        inlineCancelBtn.setGraphic(cancelIcon);
        inlineCancelBtn.getStyleClass().add("chat-inline-cancel-btn");
        inlineCancelBtn.setOnAction(e -> cancelPendingRequest());
        Tooltip.install(inlineCancelBtn, new Tooltip("Отменить"));

        bubble.getChildren().addAll(dots, inlineCancelBtn);
        container.getChildren().addAll(avatar, bubble);
        return container;
    }
    
    /**
     * Creates an image generating indicator with an inline cancel button.
     */
    private HBox createImageGeneratingIndicatorWithCancel(String jobId) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);

        Node avatar = ChatBotAvatar.create(32);

        HBox bubble = new HBox(10);
        bubble.setAlignment(Pos.CENTER_LEFT);
        bubble.getStyleClass().add("chat-image-generating-bubble");

        Label label = new Label("Генерирую изображение...");
        label.getStyleClass().add("chat-image-generating-text");
        currentImageGenerationLabel = label;
        currentImageIndicatorJobId = jobId == null ? "" : jobId.trim();

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.getStyleClass().add("chat-image-progress");

        Button inlinePauseBtn = new Button();
        inlinePauseBtn.setGraphic(FontIcon.of(MaterialDesignP.PAUSE, 16));
        inlinePauseBtn.getStyleClass().add("chat-inline-cancel-btn");
        inlinePauseBtn.setOnAction(e -> {
            if (jobId != null && !jobId.isBlank() && imageGenerationService.pauseJob(jobId)) {
                addSystemMessage("Image-job поставлен на паузу.");
            }
        });
        Tooltip.install(inlinePauseBtn, new Tooltip("Пауза"));
        
        Button inlineCancelBtn = new Button();
        FontIcon cancelIcon = FontIcon.of(MaterialDesignC.CLOSE_CIRCLE, 16);
        inlineCancelBtn.setGraphic(cancelIcon);
        inlineCancelBtn.getStyleClass().add("chat-inline-cancel-btn");
        inlineCancelBtn.setOnAction(e -> cancelPendingRequest());
        Tooltip.install(inlineCancelBtn, new Tooltip("Отменить"));

        bubble.getChildren().addAll(label, progress, inlinePauseBtn, inlineCancelBtn);
        container.getChildren().addAll(avatar, bubble);
        return container;
    }
    
    /**
     * Cancels the current pending AI request.
     */
    private void cancelPendingRequest() {
        if (pendingRequest != null && !pendingRequest.isDone()) {
            boolean imageRequestActive = activeImageJobId != null && !activeImageJobId.isBlank();
            boolean cancelledByRequestId = false;
            if (imageRequestActive) {
                cancelledByRequestId = imageGenerationService.cancelRequest(activeImageJobId);
            } else if (activeChatRequestId != null && !activeChatRequestId.isBlank()) {
                cancelledByRequestId = chatService.cancelRequest(activeChatRequestId);
            }
            if (!cancelledByRequestId) {
                pendingRequest.cancel(true);
            }

            if (imageRequestActive && cancelledByRequestId) {
                if (currentImageGenerationLabel != null) {
                    currentImageGenerationLabel.setText("Отменяю генерацию изображения...");
                }
                pendingRequest = null;
                setRequestInProgress(false);
                return;
            }

            removeTypingIndicator();
            setRequestInProgress(false);
            showCancellationMessageOnce("Запрос отменён");
            pendingRequest = null;

            if (!cancelledByRequestId) {
                lifecycleElapsedTimeline.stop();
                lifecycleStatusLabel.setText(resolveLifecycleStateText(ChatRequestState.CANCELLED));
                lifecycleDetailLabel.setText("Запрос отменён пользователем");
                lifecycleSpinner.setVisible(false);
                lifecycleSpinner.setManaged(false);
                lifecycleProgressBar.setProgress(0.0);
                lifecycleHideDelay.playFromStart();
            }
        }
    }

    private void showCancellationMessageOnce(String text) {
        if (cancellationNoticeShown) {
            return;
        }
        cancellationNoticeShown = true;
        addSystemMessage(text);
    }
    
    /**
     * Removes the current typing indicator from the messages box.
     */
    private void removeTypingIndicator() {
        if (currentTypingIndicator != null) {
            messagesBox.getChildren().remove(currentTypingIndicator);
            currentTypingIndicator = null;
        }
        clearCurrentImageIndicatorBinding();
    }
    
    /**
     * Updates UI state when a request is in progress or completed.
     */
    private void setRequestInProgress(boolean inProgress) {
        if (sendButton != null && cancelButton != null) {
            sendButton.setVisible(!inProgress);
            sendButton.setManaged(!inProgress);
            cancelButton.setVisible(inProgress);
            cancelButton.setManaged(inProgress);
        }
        inputField.setDisable(isSummarizationInteractionLocked());
        if (inProgress) {
            generateImageCheckBox.setDisable(true);
            attachMediaButton.setDisable(true);
            clearAttachedMediaButton.setDisable(true);
            boolean summarizing = isSummarizationInteractionLocked();
            inputField.setPromptText(summarizing
                ? "Сжимаем контекст, отправка временно недоступна…"
                : "Можно печатать следующий вопрос, ответ еще формируется…");
        } else {
            updateImageCheckBoxAvailability();
            updateMediaUploadAvailability();
            clearAttachedMediaButton.setDisable(isSummarizationInteractionLocked() || pendingMediaAttachments.isEmpty());
            inputField.setPromptText(isSummarizationInteractionLocked()
                ? "Сжимаем контекст, отправка временно недоступна…"
                : "Задайте вопрос или опишите задачу...");
        }
    }

    private void setupSummarizeLockBanner() {
        summarizeLockBanner.getStyleClass().add("chat-summarize-lock-banner");
        summarizeLockBanner.setAlignment(Pos.CENTER_LEFT);
        summarizeLockBanner.setVisible(false);
        summarizeLockBanner.setManaged(false);

        FontIcon icon = FontIcon.of(MaterialDesignD.DATABASE_SYNC, 16);
        icon.getStyleClass().add("chat-summarize-lock-icon");

        summarizeLockBannerLabel.getStyleClass().add("chat-summarize-lock-text");
        summarizeLockBannerLabel.setWrapText(true);

        summarizeLockBanner.getChildren().addAll(icon, summarizeLockBannerLabel);
    }

    private boolean isSummarizationInteractionLocked() {
        return summarizationInteractionLocked;
    }

    private void setSummarizationInteractionLocked(boolean locked, String detailText) {
        summarizationInteractionLocked = locked;
        summarizeLockBannerLabel.setText(
            locked
                ? (detailText == null || detailText.isBlank()
                    ? "Сжимаем контекст. Новые сообщения временно заблокированы."
                    : detailText.trim())
                : "Сжимаем контекст. Новые сообщения временно заблокированы."
        );
        summarizeLockBanner.setVisible(locked);
        summarizeLockBanner.setManaged(locked);
        if (inputArea != null) {
            clearMediaDragOverState(inputArea);
        }
        if (inputField != null) {
            inputField.setDisable(locked);
        }
        if (clearAttachedMediaButton != null) {
            clearAttachedMediaButton.setDisable(locked || pendingMediaAttachments.isEmpty());
        }
        for (Button button : summarizeBlockedActionButtons) {
            if (button != null) {
                button.setDisable(locked);
            }
        }
    }

    private void updateImageGenerationIndicator(ImageRequestEvent event) {
        if (event == null || currentImageGenerationLabel == null) {
            return;
        }
        if (!isCurrentImageIndicator(event.jobId())) {
            return;
        }
        currentImageGenerationLabel.setText(buildImageLifecycleIndicatorText(event));
    }

    private String buildImageLifecycleIndicatorText(ImageRequestEvent event) {
        if (event == null) {
            return "Генерирую изображение...";
        }
        String stage = resolveImageLifecycleStateText(event.state());
        String detail = event.message() == null ? "" : event.message().trim();
        if (detail.isBlank() || detail.equalsIgnoreCase(stage)) {
            return "Изображение: " + stage;
        }
        return "Изображение: " + stage + " • " + detail;
    }

    private String resolveImageLifecycleStateText(ImageRequestState state) {
        if (state == null) {
            return "обработка";
        }
        return switch (state) {
            case QUEUED -> "в очереди";
            case SENDING -> "отправка";
            case PROVIDER_ACCEPTED -> "принято провайдером";
            case POLLING -> "ожидание результата";
            case DOWNLOADING -> "скачивание";
            case SAVING -> "сохранение";
            case DONE -> "готово";
            case RETRYING -> "повторная попытка";
            case FALLBACK_MODEL -> "резервная модель";
            case RESUMING -> "восстановление";
            case PAUSED -> "пауза";
            case FAILED -> "ошибка";
            case CANCELLED -> "отменено";
        };
    }

    private void removeImageGeneratingIndicator(String jobId) {
        if (!isCurrentImageIndicator(jobId)) {
            return;
        }
        removeTypingIndicator();
    }

    private boolean isCurrentImageIndicator(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return false;
        }
        return currentImageIndicatorJobId != null && currentImageIndicatorJobId.equals(jobId.trim());
    }

    private void clearCurrentImageIndicatorBinding() {
        currentImageGenerationLabel = null;
        currentImageIndicatorJobId = null;
    }

    private void clearActiveImageRequestIfMatches(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return;
        }
        if (!jobId.trim().equals(activeImageJobId)) {
            return;
        }
        activeImageJobId = null;
        activeImageRequestId = null;
        latestImageRequestEvent = null;
        latestImageJobSnapshot = null;
    }

    private void setupMediaUploadControls() {
        attachMediaButton.getStyleClass().add("chat-attach-btn");
        attachMediaButton.setTooltip(new Tooltip(
            "Прикрепить файлы к сообщению.\n" +
            "Доступно только во внешнем API режиме.\n" +
            "Поддерживаются: PNG/JPG/JPEG/GIF/WEBP, PDF/DOCX/TXT, WAV/MP3/FLAC/M4A.\n" +
            "Видео на вход отключено."
        ));
        attachMediaButton.setGraphic(FontIcon.of(MaterialDesignP.PAPERCLIP, 18));
        attachMediaButton.setOnAction(e -> chooseAttachments());

        attachedMediaBox.getStyleClass().add("chat-attachments-box");
        attachedMediaBox.setVisible(false);
        attachedMediaBox.setManaged(false);

        clearAttachedMediaButton.getStyleClass().add("chat-attach-clear-btn");
        clearAttachedMediaButton.setGraphic(FontIcon.of(MaterialDesignC.CLOSE, 14));
        clearAttachedMediaButton.setTooltip(new Tooltip("Убрать прикреплённые файлы"));
        clearAttachedMediaButton.setVisible(false);
        clearAttachedMediaButton.setManaged(false);
        clearAttachedMediaButton.setOnAction(e -> clearAttachedMedia());
    }

    private void installMediaDragAndDrop(VBox inputArea) {
        if (inputArea == null) {
            return;
        }
        root.setOnDragOver(event -> handleMediaDragOver(event, inputArea));
        root.setOnDragExited(event -> clearMediaDragOverState(inputArea));
        root.setOnDragDropped(event -> handleMediaDragDropped(event, inputArea));
    }

    private void handleMediaDragOver(DragEvent event, VBox inputArea) {
        if (isSummarizationInteractionLocked()) {
            clearMediaDragOverState(inputArea);
            event.consume();
            return;
        }
        Dragboard dragboard = event.getDragboard();
        if (canAcceptDraggedFiles(dragboard)) {
            event.acceptTransferModes(TransferMode.COPY);
            if (!inputArea.getStyleClass().contains("chat-input-area-drag-over")) {
                inputArea.getStyleClass().add("chat-input-area-drag-over");
            }
        }
        event.consume();
    }

    private void handleMediaDragDropped(DragEvent event, VBox inputArea) {
        if (isSummarizationInteractionLocked()) {
            clearMediaDragOverState(inputArea);
            event.setDropCompleted(false);
            event.consume();
            return;
        }
        boolean completed = false;
        try {
            Dragboard dragboard = event.getDragboard();
            if (canAcceptDraggedFiles(dragboard)) {
                List<Path> droppedPaths = dragboard.getFiles().stream()
                    .filter(Objects::nonNull)
                    .map(File::toPath)
                    .toList();
                completed = applyAttachmentCandidates(
                    droppedPaths,
                    false,
                    "Перетаскивание файлов"
                );
            }
        } finally {
            clearMediaDragOverState(inputArea);
            event.setDropCompleted(completed);
            event.consume();
        }
    }

    private void clearMediaDragOverState(VBox inputArea) {
        if (inputArea == null) {
            return;
        }
        inputArea.getStyleClass().remove("chat-input-area-drag-over");
    }

    private boolean canAcceptDraggedFiles(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasFiles()) {
            return false;
        }
        if (isSummarizationInteractionLocked()) {
            return false;
        }
        if (!AiClientFactory.getInstance().supportsImageInputs() || generateImageCheckBox.isSelected()) {
            return false;
        }
        return dragboard.getFiles().stream()
            .filter(Objects::nonNull)
            .map(File::toPath)
            .map(this::resolvePendingAttachment)
            .anyMatch(attachment -> attachment != null && attachment.descriptor().supportedInput());
    }

    private void updateMediaUploadAvailability() {
        boolean supported = AiClientFactory.getInstance().supportsImageInputs() && !generateImageCheckBox.isSelected();

        attachMediaButton.setDisable(!supported || isSummarizationInteractionLocked());
        attachMediaButton.setVisible(true);
        attachMediaButton.setManaged(true);

        if (generateImageCheckBox.isSelected()) {
            attachMediaButton.setTooltip(new Tooltip(
                "Во время генерации изображения файловые вложения не используются."
            ));
        } else {
            attachMediaButton.setTooltip(new Tooltip(
                "Прикрепить файлы к сообщению.\n" +
                "Поддерживаются: PNG/JPG/JPEG/GIF/WEBP, PDF/DOCX/TXT, WAV/MP3/FLAC/M4A.\n" +
                "Видео на вход отключено."
            ));
        }

        if (!supported) {
            clearAttachedMedia();
        } else {
            updateAttachedMediaBox();
        }
        clearAttachedMediaButton.setDisable(isSummarizationInteractionLocked() || pendingMediaAttachments.isEmpty());
    }

    private void chooseAttachments() {
        if (isSummarizationInteractionLocked()) {
            showChatAlert(Alert.AlertType.INFORMATION, "Сжатие контекста",
                "Пока выполняется сжатие контекста, нельзя добавлять новые вложения."
            );
            return;
        }
        if (!AiClientFactory.getInstance().supportsImageInputs()) {
            showChatAlert(Alert.AlertType.INFORMATION, "Загрузка файлов",
                "Файловые вложения доступны только во внешнем API режиме.\nИзмените режим ИИ в настройках."
            );
            return;
        }
        if (generateImageCheckBox.isSelected()) {
            showChatAlert(Alert.AlertType.INFORMATION, "Загрузка файлов",
                "В режиме генерации изображения вложения к сообщению не используются."
            );
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите файлы");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(
                "Поддерживаемые файлы",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp",
                "*.pdf", "*.docx", "*.txt",
                "*.wav", "*.mp3", "*.flac", "*.m4a"
            ),
            new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
            new FileChooser.ExtensionFilter("Документы", "*.pdf", "*.docx", "*.txt"),
            new FileChooser.ExtensionFilter("Аудио", "*.wav", "*.mp3", "*.flac", "*.m4a")
        );

        List<File> files = chooser.showOpenMultipleDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (files == null || files.isEmpty()) {
            return;
        }

        applyAttachmentCandidates(
            files.stream().filter(Objects::nonNull).map(File::toPath).toList(),
            true,
            "Загрузка файлов"
        );
    }

    private boolean applyAttachmentCandidates(List<Path> candidates, boolean replaceExisting, String dialogTitle) {
        if (isSummarizationInteractionLocked()) {
            return false;
        }
        if (candidates == null || candidates.isEmpty()) {
            return false;
        }

        List<Path> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (Path path : candidates) {
            if (path == null) {
                continue;
            }
            PendingAttachment attachment = resolvePendingAttachment(path);
            String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            if (attachment == null) {
                rejected.add(fileName + " — неподдерживаемый формат.");
                continue;
            }
            if (!attachment.descriptor().supportedInput()) {
                rejected.add(fileName + " — видео и этот тип файла не поддерживаются.");
                continue;
            }
            accepted.add(path);
        }

        if (accepted.isEmpty()) {
            if (!rejected.isEmpty()) {
                showChatAlert(Alert.AlertType.WARNING, dialogTitle, String.join("\n", rejected));
            }
            return false;
        }

        List<Path> mergedAttachments = new ArrayList<>();
        if (!replaceExisting) {
            mergedAttachments.addAll(pendingMediaAttachments);
        }
        for (Path acceptedPath : accepted) {
            if (!mergedAttachments.contains(acceptedPath)) {
                mergedAttachments.add(acceptedPath);
            }
        }

        pendingMediaAttachments.clear();
        pendingMediaAttachments.addAll(mergedAttachments);
        updateAttachedMediaBox();

        if (!rejected.isEmpty()) {
            showChatAlert(Alert.AlertType.WARNING, "Часть файлов пропущена", String.join("\n", rejected));
        }
        return true;
    }

    private void updateAttachedMediaBox() {
        attachedMediaBox.getChildren().clear();
        if (pendingMediaAttachments.isEmpty()) {
            attachedMediaBox.setVisible(false);
            attachedMediaBox.setManaged(false);
            clearAttachedMediaButton.setVisible(false);
            clearAttachedMediaButton.setManaged(false);
            return;
        }

        for (Path path : pendingMediaAttachments) {
            PendingAttachment attachment = resolvePendingAttachment(path);
            if (attachment == null) {
                continue;
            }

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("chat-attachment-item");

            FontIcon icon = attachmentIcon(attachment.descriptor());
            icon.getStyleClass().add("chat-attachment-icon");

            VBox textBox = new VBox(2);
            HBox.setHgrow(textBox, Priority.ALWAYS);

            Label nameLabel = new Label(attachment.displayName());
            nameLabel.getStyleClass().add("chat-attachment-name");
            nameLabel.setWrapText(true);

            String metaText = attachmentTypeLabel(attachment.descriptor()) + " • "
                    + attachment.descriptor().extension().toUpperCase(Locale.ROOT);
            Label metaLabel = new Label(metaText);
            metaLabel.getStyleClass().add("chat-attachment-meta");
            textBox.getChildren().addAll(nameLabel, metaLabel);

            VBox badgesBox = new VBox(4);
            badgesBox.setAlignment(Pos.CENTER_RIGHT);

            Label typeBadge = new Label(attachmentTypeLabel(attachment.descriptor()));
            typeBadge.getStyleClass().addAll("chat-attachment-badge", "chat-attachment-badge-type");
            badgesBox.getChildren().add(typeBadge);

            String warning = attachmentWarningText(attachment);
            if (warning != null) {
                Label warningLabel = new Label(warning);
                warningLabel.getStyleClass().add("chat-attachment-warning");
                warningLabel.setWrapText(true);
                badgesBox.getChildren().add(warningLabel);
            }

            row.getChildren().addAll(icon, textBox, badgesBox);
            attachedMediaBox.getChildren().add(row);
        }

        Tooltip tooltip = new Tooltip(
            "Файлы будут отправлены вместе с сообщением.\n" +
            "Каталог временных копий: " + ConfigManager.getChatUploadsDirectoryPath()
        );
        Tooltip.install(attachedMediaBox, tooltip);
        attachedMediaBox.setVisible(true);
        attachedMediaBox.setManaged(true);
        clearAttachedMediaButton.setVisible(true);
        clearAttachedMediaButton.setManaged(true);
    }

    private void clearAttachedMedia() {
        if (isSummarizationInteractionLocked()) {
            return;
        }
        pendingMediaAttachments.clear();
        updateAttachedMediaBox();
    }

    private PendingAttachment resolvePendingAttachment(Path path) {
        if (path == null) {
            return null;
        }
        String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        String mimeType = null;
        try {
            mimeType = Files.probeContentType(path);
        } catch (IOException ignored) {
        }
        return AiMediaTypeRegistry.detect(fileName, mimeType)
            .map(descriptor -> new PendingAttachment(path, descriptor))
            .orElse(null);
    }

    private FontIcon attachmentIcon(AiMediaTypeDescriptor descriptor) {
        if (descriptor == null) {
            return FontIcon.of(MaterialDesignF.FILE, 16);
        }
        return switch (descriptor.kind()) {
            case IMAGE -> FontIcon.of(MaterialDesignF.FILE_IMAGE, 16);
            case DOCUMENT -> FontIcon.of(MaterialDesignF.FILE_DOCUMENT_OUTLINE, 16);
            case AUDIO -> FontIcon.of(MaterialDesignF.FILE_MUSIC, 16);
            default -> FontIcon.of(MaterialDesignF.FILE, 16);
        };
    }

    private String attachmentTypeLabel(AiMediaTypeDescriptor descriptor) {
        if (descriptor == null) {
            return "Файл";
        }
        return switch (descriptor.kind()) {
            case IMAGE -> "Изображение";
            case DOCUMENT -> "Документ";
            case AUDIO -> "Аудио";
            case VIDEO -> "Видео";
        };
    }

    private String attachmentWarningText(PendingAttachment attachment) {
        if (attachment == null || attachment.descriptor() == null) {
            return null;
        }
        if (hasMixedAudioAttachments()) {
            boolean isAudio = attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.AUDIO;
            boolean isOther = attachment.descriptor().kind() != com.example.neuroflowplanner.ai.media.AiMediaInputKind.AUDIO;
            if (isAudio || isOther) {
                return "Аудио пока можно отправлять только отдельно, без других вложений.";
            }
        }

        String modelId = resolveActiveChatModelId();
        if (attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.IMAGE
                && !AiModelMediaCapabilityPolicy.supportsImageInput(modelId)) {
            return "Текущая модель не поддерживает image input.";
        }
        if (attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.AUDIO
                && !AiModelMediaCapabilityPolicy.supportsAudioInput(modelId)) {
            return "Текущая модель не поддерживает audio input.";
        }
        if (attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.DOCUMENT
                && !AiModelMediaCapabilityPolicy.supportsFileInput(modelId)) {
            return "Текущая модель не поддерживает file input.";
        }
        return null;
    }

    private String resolveActiveChatModelId() {
        String model = AiClientFactory.getInstance().getActiveClient().getDefaultModel();
        return model == null ? "" : model.trim();
    }

    private boolean hasMixedAudioAttachments() {
        boolean hasAudio = false;
        boolean hasOther = false;
        for (Path path : pendingMediaAttachments) {
            PendingAttachment attachment = resolvePendingAttachment(path);
            if (attachment == null || attachment.descriptor() == null) {
                continue;
            }
            if (attachment.descriptor().kind() == com.example.neuroflowplanner.ai.media.AiMediaInputKind.AUDIO) {
                hasAudio = true;
            } else {
                hasOther = true;
            }
        }
        return hasAudio && hasOther;
    }

    private List<Path> copyAttachmentsToChatUploads(List<Path> sources) throws IOException {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        if (!AiClientFactory.getInstance().supportsImageInputs()) {
            return List.of();
        }

        List<Path> copied = new ArrayList<>();
        Path dir = com.example.neuroflowplanner.util.DataPathManager.getChatUploadsDirectory();
        for (Path src : sources) {
            if (src == null) continue;
            if (!Files.exists(src) || !Files.isRegularFile(src)) {
                throw new IOException("Файл не найден: " + src);
            }

            String fileName = src.getFileName() != null ? src.getFileName().toString() : "attachment";
            PendingAttachment attachment = resolvePendingAttachment(src);
            if (attachment == null || attachment.descriptor() == null) {
                throw new IOException(
                    "Неподдерживаемый формат: " + fileName
                        + " (разрешены изображения, PDF/DOCX/TXT, WAV/MP3/FLAC/M4A)"
                );
            }
            if (!attachment.descriptor().supportedInput()) {
                throw new IOException("Видео на вход пока не поддерживается: " + fileName);
            }
            String ext = "." + attachment.descriptor().extension().toLowerCase(Locale.ROOT);
            Path dest = dir.resolve("chat_upload_" + UUID.randomUUID() + ext);
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            copied.add(dest);
        }

        return copied;
    }
    
    /**
     * Adds a system message (e.g., "Request cancelled") to the chat.
     */
    private void addSystemMessage(String text) {
        HBox container = new HBox();
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(4, 0, 4, 0));

        Label msg = new Label(text);
        msg.getStyleClass().add("chat-system-message");

        container.getChildren().add(msg);
        messagesBox.getChildren().add(container);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private ContextMenu createRussianContextMenu(TextInputControl textField) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("russian-context-menu");

        MenuItem undoItem = new MenuItem("Отменить");
        undoItem.setOnAction(e -> textField.undo());
        undoItem.getStyleClass().add("context-menu-item");

        MenuItem redoItem = new MenuItem("Повторить");
        redoItem.setOnAction(e -> textField.redo());
        redoItem.getStyleClass().add("context-menu-item");

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem cutItem = new MenuItem("Вырезать");
        cutItem.setOnAction(e -> textField.cut());
        cutItem.getStyleClass().add("context-menu-item");

        MenuItem copyItem = new MenuItem("Копировать");
        copyItem.setOnAction(e -> textField.copy());
        copyItem.getStyleClass().add("context-menu-item");

        MenuItem pasteItem = new MenuItem("Вставить");
        pasteItem.setOnAction(e -> textField.paste());
        pasteItem.getStyleClass().add("context-menu-item");

        MenuItem deleteItem = new MenuItem("Удалить");
        deleteItem.setOnAction(e -> textField.replaceSelection(""));
        deleteItem.getStyleClass().add("context-menu-item");

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        MenuItem selectAllItem = new MenuItem("Выделить всё");
        selectAllItem.setOnAction(e -> textField.selectAll());
        selectAllItem.getStyleClass().add("context-menu-item");

        menu.getItems().addAll(undoItem, redoItem, sep1, cutItem, copyItem, pasteItem, deleteItem, sep2, selectAllItem);

        // Обновляем состояние пунктов при показе меню
        menu.setOnShowing(e -> {
            boolean hasSelection = textField.getSelection().getLength() > 0;
            boolean hasText = !textField.getText().isEmpty();
            boolean canPaste = Clipboard.getSystemClipboard().hasContent(DataFormat.PLAIN_TEXT);

            undoItem.setDisable(!textField.isUndoable());
            redoItem.setDisable(!textField.isRedoable());
            cutItem.setDisable(!hasSelection);
            copyItem.setDisable(!hasSelection);
            pasteItem.setDisable(!canPaste);
            deleteItem.setDisable(!hasSelection);
            selectAllItem.setDisable(!hasText);
        });

        return menu;
    }

    private boolean isImageMessage(String content) {
        return content != null && content.startsWith(IMAGE_MESSAGE_PREFIX);
    }

    private boolean isImageOutputMessage(String content) {
        return content != null
            && (content.startsWith(IMAGE_RESULT_MESSAGE_PREFIX) || content.startsWith(IMAGE_ERROR_MESSAGE_PREFIX));
    }

    private String encodeModelMessage(String model, String content) {
        if (content == null) {
            return null;
        }
        String trimmedModel = model != null ? model.trim() : "";
        if (trimmedModel.isBlank()) {
            return content;
        }
        if (content.startsWith(MODEL_MESSAGE_PREFIX) || content.startsWith(IMAGE_MESSAGE_PREFIX) || isImageOutputMessage(content)) {
            return content;
        }
        return MODEL_MESSAGE_PREFIX + trimmedModel + "\n" + content;
    }

    private ModelTaggedMessage parseModelTaggedMessage(String content) {
        if (content == null) {
            return new ModelTaggedMessage(null, "");
        }
        if (!content.startsWith(MODEL_MESSAGE_PREFIX)) {
            return new ModelTaggedMessage(null, content);
        }
        String withoutPrefix = content.substring(MODEL_MESSAGE_PREFIX.length());
        int nl = withoutPrefix.indexOf('\n');
        if (nl < 0) {
            String model = withoutPrefix.trim();
            return new ModelTaggedMessage(model.isBlank() ? null : model, "");
        }
        String model = withoutPrefix.substring(0, nl).trim();
        String text = withoutPrefix.substring(nl + 1);
        return new ModelTaggedMessage(model.isBlank() ? null : model, text);
    }

    private record ModelTaggedMessage(String model, String content) {}

    private String decodeImagePath(String content) {
        if (!isImageMessage(content)) {
            return content;
        }
        return content.substring(IMAGE_MESSAGE_PREFIX.length()).trim();
    }

    private String encodeImageMessage(Path path) {
        if (path == null) {
            return IMAGE_MESSAGE_PREFIX;
        }
        return IMAGE_MESSAGE_PREFIX + path.toAbsolutePath();
    }

    private boolean isMediaMessage(String content) {
        return content != null && content.startsWith(MEDIA_MESSAGE_PREFIX);
    }

    private String encodeMediaMessage(UserMediaAttachmentPayload payload) {
        UserMediaAttachmentPayload safePayload = payload == null ? UserMediaAttachmentPayload.empty() : payload.normalized();
        try {
            byte[] json = CHAT_MESSAGE_MAPPER.writeValueAsBytes(safePayload);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            return MEDIA_MESSAGE_PREFIX + encoded;
        } catch (Exception ex) {
            return MEDIA_MESSAGE_PREFIX;
        }
    }

    private UserMediaAttachmentPayload decodeMediaMessage(String content) {
        if (!isMediaMessage(content)) {
            return UserMediaAttachmentPayload.empty();
        }
        String encoded = content.substring(MEDIA_MESSAGE_PREFIX.length()).trim();
        if (encoded.isBlank()) {
            return UserMediaAttachmentPayload.empty();
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            UserMediaAttachmentPayload payload = CHAT_MESSAGE_MAPPER.readValue(json, UserMediaAttachmentPayload.class);
            return payload == null ? UserMediaAttachmentPayload.empty() : resolveRuntimeMediaPayload(payload.normalized());
        } catch (Exception ex) {
            return UserMediaAttachmentPayload.empty();
        }
    }

    private UserMediaAttachmentPayload resolveRuntimeMediaPayload(UserMediaAttachmentPayload payload) {
        if (payload == null) {
            return UserMediaAttachmentPayload.empty();
        }
        if (!payload.savedPath().isBlank() || payload.runtimeKey().isBlank()) {
            return payload;
        }
        UserMediaAttachmentPayload runtimePayload = runtimeMediaPayloadCache.get(payload.runtimeKey());
        if (runtimePayload == null) {
            return payload;
        }
        return payload.withSavedPath(runtimePayload.savedPath());
    }

    private String describeMediaMessageForContext(String content) {
        UserMediaAttachmentPayload payload = decodeMediaMessage(content);
        if (payload.kind().isBlank()) {
            return "Пользователь прикрепил файл.";
        }
        return "Пользователь прикрепил " + payload.kindLabel().toLowerCase(Locale.ROOT) + ".";
    }

    private String encodeImageOutputMessage(ImageOutputPayload payload) {
        ImageOutputPayload safePayload = payload == null ? ImageOutputPayload.empty() : payload.normalized();
        try {
            byte[] json = CHAT_MESSAGE_MAPPER.writeValueAsBytes(safePayload);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
            return (safePayload.isError() ? IMAGE_ERROR_MESSAGE_PREFIX : IMAGE_RESULT_MESSAGE_PREFIX) + encoded;
        } catch (Exception ex) {
            if (!safePayload.savedPath().isBlank()) {
                return IMAGE_MESSAGE_PREFIX + safePayload.savedPath();
            }
            return safePayload.isError() ? IMAGE_ERROR_MESSAGE_PREFIX : IMAGE_RESULT_MESSAGE_PREFIX;
        }
    }

    private ImageOutputPayload decodeImageOutputPayload(String content) {
        if (!isImageOutputMessage(content)) {
            return ImageOutputPayload.empty();
        }
        String prefix = content.startsWith(IMAGE_ERROR_MESSAGE_PREFIX) ? IMAGE_ERROR_MESSAGE_PREFIX : IMAGE_RESULT_MESSAGE_PREFIX;
        String encoded = content.substring(prefix.length()).trim();
        if (encoded.isBlank()) {
            return ImageOutputPayload.empty().withType(prefix.equals(IMAGE_ERROR_MESSAGE_PREFIX) ? "error" : "success");
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(encoded);
            ImageOutputPayload payload = CHAT_MESSAGE_MAPPER.readValue(json, ImageOutputPayload.class);
            if (payload == null) {
                return ImageOutputPayload.empty().withType(prefix.equals(IMAGE_ERROR_MESSAGE_PREFIX) ? "error" : "success");
            }
            return payload.withType(prefix.equals(IMAGE_ERROR_MESSAGE_PREFIX) ? "error" : "success").normalized();
        } catch (Exception ex) {
            return ImageOutputPayload.empty().withType(prefix.equals(IMAGE_ERROR_MESSAGE_PREFIX) ? "error" : "success");
        }
    }

    private record ImageOutputPayload(
        String type,
        String jobId,
        String requestId,
        String conversationId,
        String model,
        String prompt,
        String savedPath,
        String remoteUrl,
        String headline,
        String detail,
        String errorClass,
        int attempt,
        String stage,
        String size,
        String aspectRatio,
        String resolution
    ) {
        private static ImageOutputPayload empty() {
            return new ImageOutputPayload(
                "success",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                1,
                "",
                "",
                "",
                ""
            );
        }

        private ImageOutputPayload normalized() {
            return new ImageOutputPayload(
                normalize(type),
                normalize(jobId),
                normalize(requestId),
                normalize(conversationId),
                normalize(model),
                normalize(prompt),
                normalize(savedPath),
                normalize(remoteUrl),
                normalize(headline),
                normalize(detail),
                normalize(errorClass),
                Math.max(1, attempt),
                normalize(stage),
                normalize(size),
                normalize(aspectRatio),
                normalize(resolution)
            );
        }

        private ImageOutputPayload withType(String nextType) {
            return new ImageOutputPayload(
                normalize(nextType),
                jobId,
                requestId,
                conversationId,
                model,
                prompt,
                savedPath,
                remoteUrl,
                headline,
                detail,
                errorClass,
                attempt,
                stage,
                size,
                aspectRatio,
                resolution
            );
        }

        private boolean isError() {
            return "error".equalsIgnoreCase(normalize(type));
        }

        private static String normalize(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? "" : normalized;
        }
    }

    private record ImageFailurePresentation(String code, String headline, String detail) {}

    private record PendingAttachment(Path path, AiMediaTypeDescriptor descriptor) {
        private String displayName() {
            if (path == null) {
                return "Файл";
            }
            return path.getFileName() == null ? path.toString() : path.getFileName().toString();
        }
    }

    private record ChatLaunchRequest(String conversationTitle, String initialPrompt) {
    }

    private record UserMediaAttachmentPayload(String kind, String fileName, String mimeType, String savedPath, String runtimeKey) {
        private static UserMediaAttachmentPayload empty() {
            return new UserMediaAttachmentPayload("", "", "", "", "");
        }

        private static UserMediaAttachmentPayload fromPath(Path path, PendingAttachment attachment) {
            String resolvedPath = path == null ? "" : path.toAbsolutePath().toString();
            String resolvedFileName = attachment == null ? resolvedPath : attachment.displayName();
            String resolvedKind = attachment == null || attachment.descriptor() == null
                ? "file"
                : attachment.descriptor().kind().name().toLowerCase(Locale.ROOT);
            String resolvedMime = attachment == null || attachment.descriptor() == null
                ? ""
                : attachment.descriptor().mimeType();
            return new UserMediaAttachmentPayload(resolvedKind, resolvedFileName, resolvedMime, resolvedPath, "").normalized();
        }

        private UserMediaAttachmentPayload normalized() {
            return new UserMediaAttachmentPayload(
                normalize(kind),
                normalize(fileName),
                normalize(mimeType),
                normalize(savedPath),
                normalize(runtimeKey)
            );
        }

        private UserMediaAttachmentPayload forPersistence() {
            return new UserMediaAttachmentPayload(kind, fileName, mimeType, "", runtimeKey).normalized();
        }

        private UserMediaAttachmentPayload withRuntimeKey(String nextRuntimeKey) {
            return new UserMediaAttachmentPayload(kind, fileName, mimeType, savedPath, nextRuntimeKey).normalized();
        }

        private UserMediaAttachmentPayload withSavedPath(String nextSavedPath) {
            return new UserMediaAttachmentPayload(kind, fileName, mimeType, nextSavedPath, runtimeKey).normalized();
        }

        private String kindLabel() {
            return switch (normalize(kind)) {
                case "audio" -> "Аудио";
                case "document" -> "Документ";
                case "image" -> "Изображение";
                default -> "Файл";
            };
        }

        private String metaLabel() {
            String normalizedMime = normalize(mimeType);
            if (!normalizedMime.isBlank()) {
                return normalizedMime;
            }
            return savedPath.isBlank() ? "Вложение" : savedPath;
        }

        private static String normalize(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? "" : normalized;
        }
    }

    /**
     * Updates the image generation checkbox availability based on current AI mode.
     * Image generation is only available in External API mode.
     */
    private void updateImageCheckBoxAvailability() {
        boolean supportsImages = AiClientFactory.getInstance().supportsImages();
        
        generateImageCheckBox.setDisable(!supportsImages);
        
        if (!supportsImages) {
            generateImageCheckBox.setSelected(false);
            generateImageCheckBox.setTooltip(new Tooltip(
                "Генерация изображений доступна только во внешнем API режиме.\n" +
                "Измените режим ИИ в настройках для использования этой функции."
            ));
        } else {
            generateImageCheckBox.setTooltip(new Tooltip(
                "Генерация изображения по вашему запросу.\n" +
                "Модель, соотношение сторон и разрешение настраиваются в «Настройки → Генерация изображений».\n" +
                "Изображения сохраняются в: " + ConfigManager.getImagesDirectoryPath() + "\n" +
                "Путь конечного изображения будет показан в сообщении ассистента."
            ));
        }
    }
}
