package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.SmartCategorizationService;
import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.dto.ui.AiTaskAutofillResponseDto;
import com.example.neuroflowplanner.ai.json.AiCoreResponseMapper;
import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.util.AsyncContext;
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
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Inline view for creating tasks/subtasks.
 */
public class AddTaskDialog implements InlineView {

    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final TextField tagsField = new TextField();
    private final DatePicker deadlinePicker = new DatePicker(LocalDate.now().plusDays(7));
    private final DatePicker startDatePicker = new DatePicker();
    private final ToggleButton startTimeEnabled = new ToggleButton("Добавить время");
    private final Spinner<Integer> startHourSpinner = createTimeSpinner(0, 23);
    private final Spinner<Integer> startMinuteSpinner = createTimeSpinner(0, 59);
    private final ToggleButton deadlineTimeEnabled = new ToggleButton("Добавить время");
    private final Spinner<Integer> deadlineHourSpinner = createTimeSpinner(0, 23);
    private final Spinner<Integer> deadlineMinuteSpinner = createTimeSpinner(0, 59);
    private final Slider complexitySlider = new Slider(1, 10, 5);
    private final Label complexityValue = new Label("5 - Средняя");
    private final ComboBox<String> recurrenceBox = new ComboBox<>();
    private final String parentId;
    private final Consumer<Task> onSubmitTask;
    private final Runnable onClose;
    private VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private boolean isDescriptionExpanded = false;
    private VBox descriptionGroup;
    private VBox titleGroup;
    private FlowPane datesRow;
    private FlowPane metaRow;
    private VBox complexityBox;
    private ScrollPane contentScrollPane;
    private Label descriptionHintLabel;
    private Tooltip descriptionToggleTooltip;

    // AI autofill
    private Button aiAutoFillBtn;
    private Button aiImproveDescBtn;
    private boolean aiAvailable = false;

    private AddTaskDialog(String parentId, Consumer<Task> onSubmitTask, Runnable onClose) {
        this.parentId = parentId;
        this.onSubmitTask = onSubmitTask;
        this.onClose = onClose;
        buildView();
        checkAIAvailability();
    }

    private void checkAIAvailability() {
        CompletableFuture.supplyAsync(() -> {
            try {
                AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
                return aiClient.isConfigured() && aiClient.getMode() != AiMode.OFFLINE;
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
                || getStartTimeValue() != null
                || getDeadlineTimeValue() != null
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
        FontIcon icon = FontIcon
                .of(parentId == null ? MaterialDesignP.PLUS_CIRCLE : MaterialDesignF.FORMAT_LIST_BULLETED_TYPE, 24);
        icon.getStyleClass().add("add-task-icon");
        iconWrap.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label(parentId == null ? "Новая Задача" : "Новая Подзадача");
        title.getStyleClass().add("add-task-title");
        Label subtitle = new Label(
                parentId == null ? "Заполните детали задачи" : "Добавьте подзадачу к родительскому элементу");
        subtitle.getStyleClass().add("add-task-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label(parentId == null ? "ОСНОВНАЯ" : "ПОДЗАДАЧА");
        badge.getStyleClass().addAll("add-task-badge",
                parentId == null ? "add-task-badge-primary" : "add-task-badge-warning");

        headerContent.getChildren().addAll(iconWrap, titleBox, spacer, badge);
        headerContainer.getChildren().add(headerContent);
        root.getChildren().add(headerContainer);

        // --- Form Content (Scrollable) ---
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 25, 20, 25));
        content.getStyleClass().add("add-task-content");

        // Title Group with AI buttons
        titleGroup = new VBox(6);
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
        descriptionGroup = new VBox(6);
        HBox descLabelRow = createDescriptionLabel();
        descriptionGroup.getChildren().add(descLabelRow);
        descriptionArea.setPromptText("Добавьте детали, ссылки или заметки...");
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        descriptionArea.getStyleClass().add("add-task-textarea");
        descriptionArea.setContextMenu(createRussianContextMenu(descriptionArea));
        descriptionGroup.getChildren().add(descriptionArea);

        // Row 1: Dates (Adaptive FlowPane)
        datesRow = new FlowPane(20, 15);
        datesRow.setAlignment(Pos.TOP_LEFT);
        VBox startBox = new VBox(6);
        startBox.getChildren().addAll(createLabel("Дата начала", MaterialDesignC.CALENDAR_ARROW_RIGHT),
                startDatePicker);
        startDatePicker.setPrefWidth(200);
        startDatePicker.getStyleClass().add("add-task-date");
        startBox.getChildren().add(createTimeSelector(
                startDatePicker,
                startTimeEnabled,
                startHourSpinner,
                startMinuteSpinner,
                LocalDate::now));

        VBox endBox = new VBox(6);
        endBox.getChildren().addAll(createLabel("Дедлайн", MaterialDesignC.CALENDAR_CHECK), deadlinePicker);
        deadlinePicker.setPrefWidth(200);
        deadlinePicker.getStyleClass().add("add-task-date");
        endBox.getChildren().add(createTimeSelector(
                deadlinePicker,
                deadlineTimeEnabled,
                deadlineHourSpinner,
                deadlineMinuteSpinner,
                () -> LocalDate.now().plusDays(7)));
        datesRow.getChildren().addAll(startBox, endBox);

        // Row 2: Meta (Adaptive FlowPane)
        metaRow = new FlowPane(20, 15);
        metaRow.setAlignment(Pos.TOP_LEFT);
        VBox tagsBox = new VBox(6);
        tagsBox.getChildren().addAll(createLabel("Теги", MaterialDesignT.TAG_TEXT_OUTLINE), tagsField);
        tagsField.setPromptText("работа, проект, важно...");
        tagsField.setPrefWidth(200);
        tagsField.getStyleClass().add("add-task-input");
        tagsField.setContextMenu(createRussianContextMenu(tagsField));

        VBox repeatBox = new VBox(6);
        repeatBox.getChildren().addAll(createLabel("Повторение", MaterialDesignR.REPEAT), recurrenceBox);
        recurrenceBox.getItems().addAll("Без повтора", "Ежедневно", "Еженедельно", "Ежемесячно", "Ежегодно");
        recurrenceBox.setValue("Без повтора");
        recurrenceBox.setPrefWidth(200);
        recurrenceBox.getStyleClass().add("add-task-combo");
        metaRow.getChildren().addAll(tagsBox, repeatBox);

        // Complexity
        complexityBox = new VBox(10);
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

        content.getChildren().addAll(titleGroup, descriptionGroup, datesRow, metaRow, complexityBox);

        // Wrap content in ScrollPane
        contentScrollPane = new ScrollPane(content);
        contentScrollPane.setFitToWidth(true);
        contentScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contentScrollPane.getStyleClass().add("add-task-scroll");
        VBox.setVgrow(contentScrollPane, Priority.ALWAYS);

        root.getChildren().add(contentScrollPane);

        // --- Footer (Fixed) ---
        HBox footer = new HBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(15, 25, 20, 25));
        footer.getStyleClass().add("add-task-footer");

        Button cancelBtn = new Button("Отмена");
        cancelBtn.getStyleClass().add("add-task-btn-cancel");
        cancelBtn.setOnAction(e -> {
            if (closeAction != null)
                closeAction.run();
        });

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
            if (closeAction != null)
                closeAction.run();
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
                recurrence);
        task.setStartDate(startDatePicker.getValue());
        task.setStartTime(getStartTimeValue());
        task.setDeadlineTime(getDeadlineTimeValue());
        onSubmitTask.accept(task);
        saved = true;
        if (closeAction != null)
            closeAction.run();
    }

    private Spinner<Integer> createTimeSpinner(int min, int max) {
        Spinner<Integer> spinner = new Spinner<>(min, max, min);
        spinner.setEditable(true);
        spinner.setPrefWidth(76);
        spinner.getStyleClass().add("add-task-time-spinner");
        SpinnerValueFactory.IntegerSpinnerValueFactory valueFactory =
                (SpinnerValueFactory.IntegerSpinnerValueFactory) spinner.getValueFactory();
        valueFactory.setWrapAround(true);
        valueFactory.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "00" : String.format("%02d", value);
            }

            @Override
            public Integer fromString(String value) {
                if (value == null || value.isBlank()) {
                    return min;
                }
                return Integer.parseInt(value.trim());
            }
        });
        spinner.getEditor().setText(valueFactory.getConverter().toString(spinner.getValue()));
        return spinner;
    }

    private HBox createTimeSelector(
        DatePicker datePicker,
        ToggleButton enabledBox,
        Spinner<Integer> hourSpinner,
        Spinner<Integer> minuteSpinner,
        Supplier<LocalDate> defaultDateSupplier
    ) {
        enabledBox.getStyleClass().add("add-task-time-toggle-btn");
        Label separator = new Label(":");
        separator.getStyleClass().add("add-task-time-separator");
        HBox timeRow = new HBox(8, enabledBox, hourSpinner, separator, minuteSpinner);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        timeRow.getStyleClass().add("add-task-time-row");

        hourSpinner.disableProperty().bind(enabledBox.selectedProperty().not().or(datePicker.valueProperty().isNull()));
        minuteSpinner.disableProperty().bind(enabledBox.selectedProperty().not().or(datePicker.valueProperty().isNull()));
        enabledBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (isSelected && datePicker.getValue() == null) {
                datePicker.setValue(defaultDateSupplier.get());
            }
        });
        datePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                enabledBox.setSelected(false);
            }
        });
        return timeRow;
    }

    private LocalTime getStartTimeValue() {
        return resolveTimeValue(startDatePicker, startTimeEnabled, startHourSpinner, startMinuteSpinner);
    }

    private LocalTime getDeadlineTimeValue() {
        return resolveTimeValue(deadlinePicker, deadlineTimeEnabled, deadlineHourSpinner, deadlineMinuteSpinner);
    }

    private LocalTime resolveTimeValue(
        DatePicker datePicker,
        ToggleButton enabledBox,
        Spinner<Integer> hourSpinner,
        Spinner<Integer> minuteSpinner
    ) {
        if (datePicker.getValue() == null || !enabledBox.isSelected()) {
            return null;
        }
        commitSpinnerValue(hourSpinner);
        commitSpinnerValue(minuteSpinner);
        return LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue());
    }

    private void commitSpinnerValue(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> valueFactory = spinner.getValueFactory();
        if (valueFactory == null) {
            return;
        }
        try {
            Integer parsed = valueFactory.getConverter().fromString(spinner.getEditor().getText());
            valueFactory.setValue(parsed);
        } catch (RuntimeException ignored) {
            spinner.getEditor().setText(valueFactory.getConverter().toString(valueFactory.getValue()));
        }
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
        if (aiAutoFillBtn == null)
            return;
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
        if (aiImproveDescBtn == null)
            return;
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
        if (title.isEmpty())
            return;

        aiAutoFillBtn.setDisable(true);
        aiAutoFillBtn.getStyleClass().add("ai-autofill-btn-loading");
        FontIcon loadingIcon = FontIcon.of(MaterialDesignL.LOADING, 16);
        aiAutoFillBtn.setGraphic(loadingIcon);

        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<AIAutoFillResult> request = AsyncContext.supplyAsync(() -> requestAIAutoFill(title));
        CompletableFuture<AIAutoFillResult> observedRequest = AsyncErrorHandler.observeFuture(
                request,
                ownerWindow(),
                isDark,
                "Ошибка AI-автозаполнения",
                ErrorCode.AI_REQUEST_FAILED,
                "Не удалось выполнить AI-автозаполнение. Попробуйте позже.",
                true,
                "addtask.ai.autofill.failed",
                "operation", "runAIAutoFill",
                "titleLength", title.length(),
                "requestId", requestId);
        observedRequest
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
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ignored) {
                            }
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
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ignored) {
                            }
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

    private record AIAutoFillResult(String description, String tags, int complexity) {
    }

    private AIAutoFillResult requestAIAutoFill(String title) {
        String prompt = """
                Для задачи с названием "%s" предложи:
                1. Краткое описание (1-2 предложения)
                2. Подходящие теги (2-3 тега через запятую, на русском)
                3. Сложность от 1 до 10

                Ответь СТРОГО в формате JSON:
                {
                  "description": "...",
                  "tags": "...",
                  "complexity": 5
                }
                """.formatted(title);

        String systemPrompt = "Ты помощник по планированию задач. Отвечай только валидным JSON.";

        try {
            AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
            AiRequestOptions options = AiRequestOptions.builder()
                    .model(aiClient.getDefaultModel())
                    .systemPrompt(systemPrompt)
                    .build();

            var response = aiClient.sendChatMessage(prompt, options).get();
            if (response.success() && response.content() != null) {
                AIAutoFillResult parsed = parseAIResponse(response.content());
                if (parsed != null) {
                    return parsed;
                }
                throw new IllegalStateException("Пустой или некорректный ответ AI для автозаполнения.");
            }
            throw new IllegalStateException("AI вернул неуспешный ответ.");
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось выполнить AI-автозаполнение.", e);
        }
    }

    private AIAutoFillResult parseAIResponse(String content) {
        if (content == null) {
            return null;
        }
        try {
            AiTaskAutofillResponseDto dto = AiCoreResponseMapper.parseUiTaskAutofillResponse(content);
            return new AIAutoFillResult(dto.description(), dto.tags(), dto.complexity());
        } catch (AiParsingException e) {
            return null;
        }
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
        if (description.isEmpty())
            return;

        aiImproveDescBtn.setDisable(true);
        aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-loading");
        aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignL.LOADING, 14));

        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<String> request = AsyncContext
                .supplyAsync(() -> requestAIImproveDescription(title, description));
        CompletableFuture<String> observedRequest = AsyncErrorHandler.observeFuture(
                request,
                ownerWindow(),
                isDark,
                "Ошибка AI-улучшения описания",
                ErrorCode.AI_REQUEST_FAILED,
                "Не удалось улучшить описание через AI. Попробуйте позже.",
                true,
                "addtask.ai.improve.description.failed",
                "operation", "runAIImproveDescription",
                "titleLength", title.length(),
                "descriptionLength", description.length(),
                "requestId", requestId);
        observedRequest
                .thenAccept(result -> Platform.runLater(() -> {
                    if (result != null && !result.isEmpty()) {
                        descriptionArea.setText(result);

                        aiImproveDescBtn.getStyleClass().remove("ai-autofill-btn-loading");
                        aiImproveDescBtn.getStyleClass().add("ai-autofill-btn-success");
                        aiImproveDescBtn.setGraphic(FontIcon.of(MaterialDesignC.CHECK, 14));

                        new Thread(() -> {
                            try {
                                Thread.sleep(1500);
                            } catch (InterruptedException ignored) {
                            }
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
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ignored) {
                            }
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

    private javafx.stage.Window ownerWindow() {
        return root != null && root.getScene() != null ? root.getScene().getWindow() : null;
    }

    private String requestAIImproveDescription(String title, String description) {
        String prompt = """
                Улучши описание задачи. Сделай его более чётким, структурированным и информативным.
                Убери лишнее, добавь важные детали если нужно. Сохрани смысл.

                Название задачи: %s
                Текущее описание: %s

                Ответь ТОЛЬКО улучшенным описанием, без пояснений и кавычек.
                """.formatted(title.isEmpty() ? "не указано" : title, description);

        String systemPrompt = "Ты помощник по улучшению текстов. Отвечай только улучшенным текстом, без пояснений.";

        try {
            AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
            AiRequestOptions options = AiRequestOptions.builder()
                    .model(aiClient.getDefaultModel())
                    .systemPrompt(systemPrompt)
                    .build();

            var response = aiClient.sendChatMessage(prompt, options).get();
            if (response.success() && response.content() != null) {
                String result = response.content().trim();
                if (!result.isEmpty()) {
                    return result;
                }
                throw new IllegalStateException("Пустой ответ AI при улучшении описания.");
            }
            throw new IllegalStateException("AI вернул неуспешный ответ.");
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось улучшить описание через AI.", e);
        }
    }

    private HBox createDescriptionLabel() {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setCursor(javafx.scene.Cursor.HAND);

        FontIcon icon = FontIcon.of(MaterialDesignT.TEXT_SUBJECT, 14);
        icon.getStyleClass().add("add-task-field-icon");

        Label label = new Label("Описание");
        label.getStyleClass().add("add-task-field-label");

        descriptionHintLabel = new Label();
        descriptionHintLabel.getStyleClass().add("add-task-field-hint");
        descriptionHintLabel.setStyle("-fx-font-size: 10px; -fx-opacity: 0.6;");

        descriptionToggleTooltip = new Tooltip();
        updateDescriptionToggleUi();
        box.getChildren().addAll(icon, label, descriptionHintLabel);

        box.setOnMouseClicked(e -> toggleDescriptionExpanded());

        Tooltip.install(box, descriptionToggleTooltip);

        return box;
    }

    private void toggleDescriptionExpanded() {
        isDescriptionExpanded = !isDescriptionExpanded;

        setNodeVisible(titleGroup, !isDescriptionExpanded);
        setNodeVisible(datesRow, !isDescriptionExpanded);
        setNodeVisible(metaRow, !isDescriptionExpanded);
        setNodeVisible(complexityBox, !isDescriptionExpanded);

        if (isDescriptionExpanded) {
            descriptionArea.setPrefRowCount(14);
            descriptionArea.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(descriptionArea, Priority.ALWAYS);
            VBox.setVgrow(descriptionGroup, Priority.ALWAYS);
            if (contentScrollPane != null) {
                contentScrollPane.setFitToHeight(true);
            }
            descriptionArea.requestFocus();
        } else {
            descriptionArea.setPrefRowCount(3);
            descriptionArea.setMaxHeight(Region.USE_COMPUTED_SIZE);
            VBox.setVgrow(descriptionArea, Priority.NEVER);
            VBox.setVgrow(descriptionGroup, Priority.NEVER);
            if (contentScrollPane != null) {
                contentScrollPane.setFitToHeight(false);
            }
        }

        updateDescriptionToggleUi();
    }

    private void updateDescriptionToggleUi() {
        String hintText = isDescriptionExpanded
                ? "(нажмите для сворачивания)"
                : "(нажмите для раскрытия)";
        String tooltipText = isDescriptionExpanded
                ? "Свернуть описание"
                : "Развернуть описание на весь диалог";

        if (descriptionHintLabel != null) {
            descriptionHintLabel.setText(hintText);
        }
        if (descriptionToggleTooltip != null) {
            descriptionToggleTooltip.setText(tooltipText);
        }
    }

    private void setNodeVisible(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
