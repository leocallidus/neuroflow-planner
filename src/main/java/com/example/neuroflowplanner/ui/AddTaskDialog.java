package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.SmartCategorizationService;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Inline view for creating tasks/subtasks.
 */
public class AddTaskDialog implements InlineView {

    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final TextField tagsField = new TextField();
    private final DatePicker deadlinePicker = new DatePicker(LocalDate.now().plusDays(7));
    private final DatePicker startDatePicker = new DatePicker();
    private final Slider complexitySlider = new Slider(1, 10, 5);
    private final Label complexityValue = new Label("5 - Средняя");
    private final ComboBox<String> recurrenceBox = new ComboBox<>();
    private final String parentId;
    private final Consumer<Task> onSubmitTask;
    private final Runnable onClose;
    private VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    
    // AI autofill
    private Button aiAutoFillBtn;
    private Button aiImproveDescBtn;
    private boolean aiAvailable = false;
    private String apiUrl;
    private String apiModel;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private AddTaskDialog(String parentId, Consumer<Task> onSubmitTask, Runnable onClose) {
        this.parentId = parentId;
        this.onSubmitTask = onSubmitTask;
        this.onClose = onClose;
        loadApiConfig();
        buildView();
        checkAIAvailability();
    }
    
    private String apiKey;
    
    private void loadApiConfig() {
        String url = ConfigManager.getProperty("api.url");
        String model = ConfigManager.getProperty("api.model");
        String key = ConfigManager.getProperty("api.key");
        apiUrl = url != null ? url : "http://localhost:11434/api/chat";
        apiModel = model != null ? model : "llama3";
        apiKey = key;
    }
    
    private void checkAIAvailability() {
        CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = apiUrl.replace("/api/chat", "");
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }).orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
          .exceptionally(ex -> false)
          .thenAccept(available -> {
              aiAvailable = available;
              Platform.runLater(() -> {
                  updateAIButtonState();
                  updateImproveDescButtonState();
              });
          });
    }

    public static InlineView inline(String parentId, Consumer<Task> onSubmitTask, Runnable onClose) {
        return new AddTaskDialog(parentId, onSubmitTask, onClose);
    }

    @Override
    public Node getContent() {
        return root;
    }

    @Override
    public Runnable getOnClose() {
        return onClose;
    }

    @Override
    public void setCloseAction(Runnable closeAction) {
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return parentId == null ? "Новая задача" : "Новая подзадача";
    }
    
    private boolean saved = false;
    
    @Override
    public boolean canClose() {
        if (saved) {
            return true;
        }
        if (hasUnsavedChanges()) {
            return UnsavedChangesDialog.showAndWait();
        }
        return true;
    }
    
    private boolean hasUnsavedChanges() {
        return !titleField.getText().trim().isEmpty() 
            || !descriptionArea.getText().trim().isEmpty()
            || !tagsField.getText().trim().isEmpty()
            || startDatePicker.getValue() != null
            || (int) complexitySlider.getValue() != 5
            || recurrenceBox.getSelectionModel().getSelectedIndex() > 0;
    }

    private void buildView() {
        root = new VBox(0);
        root.setMinWidth(400);
        root.setMaxWidth(Double.MAX_VALUE);
        root.getStyleClass().add("add-task-root");

        // --- Header Wrapper ---
        StackPane headerContainer = new StackPane();
        headerContainer.getStyleClass().add("add-task-header-panel");
        headerContainer.setMaxWidth(Double.MAX_VALUE);

        // --- Header Content ---
        HBox headerContent = new HBox(15);
        headerContent.setAlignment(Pos.CENTER_LEFT);
        headerContent.setPadding(new Insets(20, 25, 20, 25));

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("add-task-icon-container");
        FontIcon icon = FontIcon.of(parentId == null ? MaterialDesignP.PLUS_CIRCLE : MaterialDesignF.FORMAT_LIST_BULLETED_TYPE, 24);
        icon.getStyleClass().add("add-task-icon");
        iconWrap.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label(parentId == null ? "Новая Задача" : "Новая Подзадача");
        title.getStyleClass().add("add-task-title");
        Label subtitle = new Label(parentId == null ? "Заполните детали задачи" : "Добавьте подзадачу к родительскому элементу");
        subtitle.getStyleClass().add("add-task-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(parentId == null ? "ОСНОВНАЯ" : "ПОДЗАДАЧА");
        badge.getStyleClass().addAll("add-task-badge", parentId == null ? "add-task-badge-primary" : "add-task-badge-warning");

        headerContent.getChildren().addAll(iconWrap, titleBox, spacer, badge);
        headerContainer.getChildren().add(headerContent);
        root.getChildren().add(headerContainer);

        // --- Form Content ---
        VBox content = new VBox(15);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("add-task-content");

        // Title Group with AI buttons
        VBox titleGroup = new VBox(6);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleField.setPromptText("Краткое и понятное название...");
        titleField.getStyleClass().add("add-task-input");
        HBox.setHgrow(titleField, Priority.ALWAYS);
        
        // AI Auto-fill button
        aiAutoFillBtn = new Button();
        aiAutoFillBtn.getStyleClass().add("ai-autofill-btn");
        aiAutoFillBtn.setTooltip(new Tooltip("ИИ заполнит описание, теги и сложность"));
        FontIcon aiIcon = FontIcon.of(MaterialDesignR.ROBOT, 16);
        aiAutoFillBtn.setGraphic(aiIcon);
        aiAutoFillBtn.setDisable(true);
        aiAutoFillBtn.setOnAction(e -> runAIAutoFill());
        
        // AI Improve description button
        aiImproveDescBtn = new Button();
        aiImproveDescBtn.getStyleClass().add("ai-autofill-btn");
        aiImproveDescBtn.setTooltip(new Tooltip("ИИ улучшит и дополнит описание"));
        aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignA.AUTO_FIX, 16));
        aiImproveDescBtn.setDisable(true);
        aiImproveDescBtn.setOnAction(e -> runAIImproveDescription());
        
        // Кнопки доступны когда есть название/описание и ИИ доступен
        titleField.textProperty().addListener((obs, old, newVal) -> updateAIButtonState());
        descriptionArea.textProperty().addListener((obs, old, newVal) -> updateImproveDescButtonState());
        
        titleField.setContextMenu(createRussianContextMenu(titleField));
        
        titleRow.getChildren().addAll(titleField, aiAutoFillBtn, aiImproveDescBtn);
        titleGroup.getChildren().addAll(createLabel("Название задачи", MaterialDesignF.FORMAT_TITLE), titleRow);

        // Description Group
        VBox descGroup = new VBox(6);
        HBox descLabelRow = createDescriptionLabel();
        descGroup.getChildren().add(descLabelRow);
        descriptionArea.setPromptText("Добавьте детали, ссылки или заметки...");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        descriptionArea.getStyleClass().add("add-task-textarea");
        descriptionArea.setContextMenu(createRussianContextMenu(descriptionArea));
        descGroup.getChildren().add(descriptionArea);

        // Row 1: Dates
        HBox datesRow = new HBox(20);
        VBox startBox = new VBox(6);
        startBox.getChildren().addAll(createLabel("Дата начала", MaterialDesignC.CALENDAR_ARROW_RIGHT), startDatePicker);
        startDatePicker.setMaxWidth(Double.MAX_VALUE);
        startDatePicker.getStyleClass().add("add-task-date");
        HBox.setHgrow(startBox, Priority.ALWAYS);

        VBox endBox = new VBox(6);
        endBox.getChildren().addAll(createLabel("Дедлайн", MaterialDesignC.CALENDAR_CHECK), deadlinePicker);
        deadlinePicker.setMaxWidth(Double.MAX_VALUE);
        deadlinePicker.getStyleClass().add("add-task-date");
        HBox.setHgrow(endBox, Priority.ALWAYS);
        datesRow.getChildren().addAll(startBox, endBox);

        // Row 2: Meta
        HBox metaRow = new HBox(20);
        VBox tagsBox = new VBox(6);
        tagsBox.getChildren().addAll(createLabel("Теги", MaterialDesignT.TAG_TEXT_OUTLINE), tagsField);
        tagsField.setPromptText("работа, проект, важно...");
        tagsField.getStyleClass().add("add-task-input");
        tagsField.setContextMenu(createRussianContextMenu(tagsField));
        HBox.setHgrow(tagsBox, Priority.ALWAYS);

        VBox repeatBox = new VBox(6);
        repeatBox.getChildren().addAll(createLabel("Повторение", MaterialDesignR.REPEAT), recurrenceBox);
        recurrenceBox.getItems().addAll("Без повтора", "Ежедневно", "Еженедельно", "Ежемесячно", "Ежегодно");
        recurrenceBox.setValue("Без повтора");
        recurrenceBox.setMaxWidth(Double.MAX_VALUE);
        recurrenceBox.getStyleClass().add("add-task-combo");
        HBox.setHgrow(repeatBox, Priority.ALWAYS);
        metaRow.getChildren().addAll(tagsBox, repeatBox);

        // Complexity
        VBox complexityBox = new VBox(10);
        complexityBox.getStyleClass().add("add-task-complexity-box");
        HBox compHeader = new HBox(10);
        compHeader.setAlignment(Pos.CENTER_LEFT);
        complexityValue.getStyleClass().add("add-task-complexity-value");
        compHeader.getChildren().addAll(createLabel("Сложность", MaterialDesignG.GAUGE), complexityValue);
        
        complexitySlider.valueProperty().addListener((obs, old, val) -> {
            int v = val.intValue();
            complexitySlider.setValue(v);
            updateComplexityLabel(v);
        });
        complexitySlider.setMajorTickUnit(1);
        complexitySlider.setMinorTickCount(0);
        complexitySlider.setBlockIncrement(1);
        complexitySlider.setSnapToTicks(true);
        updateComplexityLabel((int) complexitySlider.getValue());
        
        complexityBox.getChildren().addAll(compHeader, complexitySlider);

        content.getChildren().addAll(titleGroup, descGroup, datesRow, metaRow, complexityBox);
        root.getChildren().add(content);

        // --- Footer ---
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 25, 25, 25));

        Button cancelBtn = new Button("Отмена");
        cancelBtn.getStyleClass().add("add-task-btn-cancel");
        cancelBtn.setOnAction(e -> { if (closeAction != null) closeAction.run(); });

        Button saveBtn = new Button(parentId == null ? "Создать задачу" : "Добавить");
        saveBtn.getStyleClass().add("add-task-btn-save");
        saveBtn.setGraphic(FontIcon.of(MaterialDesignC.CONTENT_SAVE, 16));
        saveBtn.disableProperty().bind(titleField.textProperty().isEmpty());
        saveBtn.setOnAction(e -> handleSubmit());

        footer.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().add(footer);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    private void handleSubmit() {
        if (onSubmitTask == null) {
            if (closeAction != null) closeAction.run();
            return;
        }
        String recurrence = switch (recurrenceBox.getValue()) {
            case "Ежедневно" -> "daily";
            case "Еженедельно" -> "weekly";
            case "Ежемесячно" -> "monthly";
            case "Ежегодно" -> "yearly";
            default -> "";
        };
        Task task = new Task(
            UUID.randomUUID().toString(),
            titleField.getText().trim(),
            descriptionArea.getText().trim(),
            deadlinePicker.getValue(),
            (int) complexitySlider.getValue(),
            parentId,
            tagsField.getText().trim(),
            recurrence
        );
        task.setStartDate(startDatePicker.getValue());
        onSubmitTask.accept(task);
        saved = true;
        if (closeAction != null) closeAction.run();
    }

    private void updateComplexityLabel(int v) {
        String text = String.valueOf(v);
        complexityValue.getStyleClass().removeAll("complexity-low", "complexity-medium", "complexity-high");
        if (v <= 3) {
            text += " — Легко";
            complexityValue.getStyleClass().add("complexity-low");
        } else if (v <= 7) {
            text += " — Средне";
            complexityValue.getStyleClass().add("complexity-medium");
        } else {
            text += " — Сложно";
            complexityValue.getStyleClass().add("complexity-high");
        }
        complexityValue.setText(text);
    }

    private HBox createLabel(String text, Enum<?> iconEnum) {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 14);
        icon.getStyleClass().add("add-task-field-icon");
        Label label = new Label(text);
        label.getStyleClass().add("add-task-field-label");
        box.getChildren().addAll(icon, label);
        return box;
    }
    
    private void updateAIButtonState() {
        if (aiAutoFillBtn == null) return;
        boolean hasTitle = titleField.getText() != null && !titleField.getText().trim().isEmpty();
        
        aiAutoFillBtn.getStyleClass().removeAll("ai-autofill-btn-ready", "ai-autofill-btn-disabled");
        
        if (aiAvailable && hasTitle) {
            aiAutoFillBtn.setDisable(false);
            aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-ready");
            aiAutoFillBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 16));
        } else {
            aiAutoFillBtn.setDisable(true);
            aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-disabled");
            if (!aiAvailable) {
                aiAutoFillBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOUD_OFF_OUTLINE, 16));
            } else {
                aiAutoFillBtn.setGraphic(FontIcon.of(MaterialDesignR.ROBOT, 16));
            }
        }
    }
    
    private void updateImproveDescButtonState() {
        if (aiImproveDescBtn == null) return;
        boolean hasDesc = descriptionArea.getText() != null && !descriptionArea.getText().trim().isEmpty();
        
        aiImproveDescBtn.getStyleClass().removeAll("ai-autofill-btn-ready", "ai-autofill-btn-disabled");
        
        if (aiAvailable && hasDesc) {
            aiImproveDescBtn.setDisable(false);
            aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-ready");
            aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignA.AUTO_FIX, 14));
        } else {
            aiImproveDescBtn.setDisable(true);
            aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-disabled");
            if (!aiAvailable) {
                aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignC.CLOUD_OFF_OUTLINE, 14));
            } else {
                aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignA.AUTO_FIX, 14));
            }
        }
    }
    
    private void runAIAutoFill() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) return;
        
        aiAutoFillBtn.setDisable(true);
        aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-loading");
        FontIcon loadingIcon = FontIcon.of(MaterialDesignL.LOADING, 16);
        aiAutoFillBtn.setGraphic(loadingIcon);
        
        CompletableFuture.supplyAsync(() -> requestAIAutoFill(title))
            .thenAccept(result -> Platform.runLater(() -> {
                if (result != null) {
                    // Заполняем поля
                    if (result.description != null && !result.description.isEmpty()) {
                        descriptionArea.setText(result.description);
                    }
                    if (result.tags != null && !result.tags.isEmpty()) {
                        tagsField.setText(result.tags);
                    }
                    if (result.complexity >= 1 && result.complexity <= 10) {
                        complexitySlider.setValue(result.complexity);
                    }
                    
                    // Успех
                    aiAutoFillBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-success");
                    aiAutoFillBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK, 16));
                    
                    // Через 1.5 сек возвращаем нормальное состояние
                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            aiAutoFillBtn.getStyleClass().remove("ai-autofill-btn-success");
                            updateAIButtonState();
                        });
                    }).start();
                } else {
                    // Ошибка
                    aiAutoFillBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-error");
                    aiAutoFillBtn.setGraphic(FontIcon.of(MaterialDesignA.ALERT_CIRCLE, 16));
                    
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            aiAutoFillBtn.getStyleClass().remove("ai-autofill-btn-error");
                            updateAIButtonState();
                        });
                    }).start();
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    aiAutoFillBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    updateAIButtonState();
                });
                return null;
            });
    }
    
    private record AIAutoFillResult(String description, String tags, int complexity) {}
    
    private AIAutoFillResult requestAIAutoFill(String title) {
        String prompt = """
            Для задачи с названием "%s" предложи:
            1. Краткое описание (1-2 предложения)
            2. Подходящие теги (2-3 тега через запятую, на русском)
            3. Сложность от 1 до 10
            
            Ответь СТРОГО в формате JSON без markdown:
            {"description": "...", "tags": "...", "complexity": N}
            """.formatted(title);
        
        String json = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "Ты помощник по планированию задач. Отвечай только JSON без markdown."},
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
            """.formatted(apiModel, escapeJson(prompt));
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
            
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            
            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String content = extractContent(response.body());
                return parseAIResponse(content);
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
    
    private AIAutoFillResult parseAIResponse(String content) {
        if (content == null) return null;
        try {
            // Извлекаем JSON из ответа
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");
            if (start == -1 || end == -1) return null;
            
            String jsonStr = content.substring(start, end + 1);
            
            // Простой парсинг JSON
            String description = extractJsonField(jsonStr, "description");
            String tags = extractJsonField(jsonStr, "tags");
            int complexity = 5;
            try {
                String compStr = extractJsonField(jsonStr, "complexity");
                if (compStr != null) {
                    complexity = Integer.parseInt(compStr.replaceAll("[^0-9]", ""));
                    complexity = Math.max(1, Math.min(10, complexity));
                }
            } catch (Exception ignored) {}
            
            return new AIAutoFillResult(description, tags, complexity);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*";
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1) return null;
        
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx == -1) return null;
        
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        if (json.charAt(valueStart) == '"') {
            // String value
            int valueEnd = valueStart + 1;
            while (valueEnd < json.length()) {
                if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') break;
                valueEnd++;
            }
            return json.substring(valueStart + 1, valueEnd).replace("\\\"", "\"").replace("\\n", "\n");
        } else {
            // Number or other
            int valueEnd = valueStart;
            while (valueEnd < json.length() && !",}".contains(String.valueOf(json.charAt(valueEnd)))) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd).trim();
        }
    }
    
    private String extractContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return null;
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        if (start >= end) return null;
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
    
    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
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
    
    private void runAIImproveDescription() {
        String description = descriptionArea.getText().trim();
        String title = titleField.getText().trim();
        if (description.isEmpty()) return;
        
        aiImproveDescBtn.setDisable(true);
        aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-loading");
        aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignL.LOADING, 14));
        
        CompletableFuture.supplyAsync(() -> requestAIImproveDescription(title, description))
            .thenAccept(result -> Platform.runLater(() -> {
                if (result != null && !result.isEmpty()) {
                    descriptionArea.setText(result);
                    
                    aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-success");
                    aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK, 14));
                    
                    new Thread(() -> {
                        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-success");
                            updateImproveDescButtonState();
                        });
                    }).start();
                } else {
                    aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-error");
                    aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignA.ALERT_CIRCLE, 14));
                    
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                        Platform.runLater(() -> {
                            aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-error");
                            updateImproveDescButtonState();
                        });
                    }).start();
                }
            }))
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-loading");
                    updateImproveDescButtonState();
                });
                return null;
            });
    }
    
    private String requestAIImproveDescription(String title, String description) {
        String prompt = """
            Улучши описание задачи. Сделай его более чётким, структурированным и информативным.
            Убери лишнее, добавь важные детали если нужно. Сохрани смысл.
            
            Название задачи: %s
            Текущее описание: %s
            
            Ответь ТОЛЬКО улучшенным описанием, без пояснений и кавычек.
            """.formatted(title.isEmpty() ? "не указано" : title, description);
        
        String json = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "Ты помощник по улучшению текстов. Отвечай только улучшенным текстом, без пояснений."},
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
            """.formatted(apiModel, escapeJson(prompt));
        
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
            
            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            
            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String content = extractContent(response.body());
                if (content != null) {
                    content = decodeUnicodeEscapes(content);
                    return content.trim();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
    
    private String decodeUnicodeEscapes(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                try {
                    String hex = text.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    result.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException e) {
                    result.append(text.charAt(i));
                    i++;
                }
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    // --- Вынос описания в отдельное окно ---
    private javafx.stage.Stage descriptionPopupStage = null;
    private VBox descGroupRef = null;
    private int descriptionAreaIndex = -1;

    private HBox createDescriptionLabel() {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(javafx.scene.Cursor.HAND);
        
        FontIcon icon = FontIcon.of(MaterialDesignT.TEXT_SUBJECT, 14);
        icon.getStyleClass().add("add-task-field-icon");
        
        Label label = new Label("Описание");
        label.getStyleClass().add("add-task-field-label");
        
        Label hint = new Label("(нажмите для вынесения в отдельное окно)");
        hint.getStyleClass().add("add-task-field-hint");
        hint.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");
        
        box.getChildren().addAll(icon, label, hint);
        
        box.setOnMouseClicked(e -> openDescriptionPopup());
        
        Tooltip tooltip = new Tooltip("Нажмите для вынесения описания в отдельное окно");
        Tooltip.install(box, tooltip);
        
        return box;
    }

    private void openDescriptionPopup() {
        if (descriptionPopupStage != null && descriptionPopupStage.isShowing()) {
            descriptionPopupStage.requestFocus();
            return;
        }

        // Сохраняем ссылку на родительский контейнер
        if (descriptionArea.getParent() instanceof VBox parent) {
            descGroupRef = parent;
            descriptionAreaIndex = parent.getChildren().indexOf(descriptionArea);
            parent.getChildren().remove(descriptionArea);
            
            // Добавляем placeholder
            Label placeholder = new Label("Описание открыто в отдельном окне");
            placeholder.getStyleClass().add("description-placeholder");
            placeholder.setStyle("-fx-padding: 20; -fx-background-color: rgba(128,128,128,0.1); -fx-background-radius: 8; -fx-text-fill: #888;");
            placeholder.setMaxWidth(Double.MAX_VALUE);
            placeholder.setAlignment(Pos.CENTER);
            parent.getChildren().add(descriptionAreaIndex, placeholder);
        }

        // Создаём новое окно
        descriptionPopupStage = new javafx.stage.Stage();
        descriptionPopupStage.initModality(javafx.stage.Modality.NONE);
        descriptionPopupStage.setTitle("Описание задачи");
        descriptionPopupStage.initStyle(javafx.stage.StageStyle.DECORATED);

        VBox popupRoot = new VBox(10);
        popupRoot.setPadding(new Insets(15));
        popupRoot.getStyleClass().add("description-popup-root");

        // Заголовок
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        FontIcon popupIcon = FontIcon.of(MaterialDesignT.TEXT_SUBJECT, 18);
        popupIcon.setIconColor(isDark ? Color.web("#74c7ec") : Color.web("#209fb5"));
        Label popupTitle = new Label("Описание задачи");
        popupTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + (isDark ? "#cdd6f4" : "#4c4f69") + ";");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button attachBtn = new Button("Прикрепить обратно");
        attachBtn.setGraphic(FontIcon.of(MaterialDesignA.ATTACHMENT, 14));
        attachBtn.getStyleClass().add("add-task-btn-save");
        attachBtn.setOnAction(e -> closeDescriptionPopup());
        
        header.getChildren().addAll(popupIcon, popupTitle, spacer, attachBtn);

        // TextArea занимает всё пространство
        descriptionArea.setPrefRowCount(15);
        VBox.setVgrow(descriptionArea, Priority.ALWAYS);

        popupRoot.getChildren().addAll(header, descriptionArea);

        // Применяем стили
        popupRoot.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            popupRoot.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        popupRoot.setStyle("-fx-background-color: " + (isDark ? "#1e1e2e" : "#eff1f5") + ";");

        javafx.scene.Scene popupScene = new javafx.scene.Scene(popupRoot, 500, 400);
        descriptionPopupStage.setScene(popupScene);
        descriptionPopupStage.setMinWidth(350);
        descriptionPopupStage.setMinHeight(250);

        // При закрытии окна возвращаем TextArea обратно
        descriptionPopupStage.setOnCloseRequest(e -> closeDescriptionPopup());

        descriptionPopupStage.show();
    }

    private void closeDescriptionPopup() {
        if (descGroupRef != null && descriptionAreaIndex >= 0) {
            // Удаляем placeholder
            if (descriptionAreaIndex < descGroupRef.getChildren().size()) {
                Node placeholder = descGroupRef.getChildren().get(descriptionAreaIndex);
                if (placeholder instanceof Label) {
                    descGroupRef.getChildren().remove(descriptionAreaIndex);
                }
            }
            
            // Возвращаем TextArea
            descriptionArea.setPrefRowCount(3);
            if (!descGroupRef.getChildren().contains(descriptionArea)) {
                descGroupRef.getChildren().add(Math.min(descriptionAreaIndex, descGroupRef.getChildren().size()), descriptionArea);
            }
        }

        if (descriptionPopupStage != null) {
            descriptionPopupStage.close();
            descriptionPopupStage = null;
        }
    }
}
