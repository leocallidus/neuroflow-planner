package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.ai.*;
import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Screen;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

/**
 * Welcome dialog shown on first launch to configure AI mode.
 * Supports three modes: Offline, Local Ollama, External API.
 */
public class WelcomeDialog {

    private static final String FIRST_LAUNCH_KEY = "app.first.launch.done";
    private static final String UX_LAYOUT_PRESET_KEY = "ux.layout.preset";
    private final boolean isDark = ConfigManager.isDarkTheme();

    // Mode selection
    private AiMode selectedMode = AiMode.OFFLINE;
    private ToggleGroup modeToggleGroup;

    // Local Ollama fields
    private TextField localUrlField;
    private ComboBox<String> localModelCombo;
    private Label localStatusLabel;
    private FontIcon localStatusIcon;
    private boolean localConnectionValid = false;

    // External API fields
    private TextField externalUrlField;
    private PasswordField externalKeyField;
    private TextField externalModelField;
    private Label externalStatusLabel;
    private FontIcon externalStatusIcon;
    private boolean externalConnectionValid = false;
    private final java.util.List<String> externalCustomModels = new java.util.ArrayList<>(ConfigManager.getExternalApiCustomModels());
    private final java.util.List<String> externalDiscoveredModels = new java.util.ArrayList<>(ConfigManager.getExternalApiDiscoveredModels());
    private final java.util.List<String> externalMultimodalModels = new java.util.ArrayList<>(ConfigManager.getExternalApiMultimodalModels());
    private final java.util.List<String> externalAudioInputModels = new java.util.ArrayList<>(ConfigManager.getExternalApiAudioInputModels());
    private final java.util.List<String> externalFileInputModels = new java.util.ArrayList<>(ConfigManager.getExternalApiFileInputModels());

    // Continue button
    private Button continueButton;

    // Content panels for different modes
    private VBox offlineModePanel;
    private VBox localModePanel;
    private VBox externalModePanel;
    private StackPane modePanelContainer;

    /**
     * Check if this is the first launch and show welcome dialog if needed.
     */
    public static void showIfFirstLaunch() {
        String firstLaunchDone = ConfigManager.getProperty(FIRST_LAUNCH_KEY);
        if (!"true".equals(firstLaunchDone)) {
            // Perform migration of legacy config
            AiConfigMigration.migrateIfNeeded();
            new WelcomeDialog().show();
        }
    }

    private void show() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Добро пожаловать");
        dialog.setHeaderText(null);

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            dialogPane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        dialogPane.getStyleClass().add("welcome-dialog");

        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        dialogPane.setPrefWidth(Math.min(700, visualBounds.getWidth() * 0.9));
        dialogPane.setMaxHeight(Math.min(820, visualBounds.getHeight() * 0.9));

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("welcome-scroll");

        VBox content = new VBox(18);
        content.setPadding(new Insets(25));
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(650);

        // Logo
        try {
            String logoPath = isDark
                    ? "/com/example/neuroflowplanner/images/logo_mocha.png"
                    : "/com/example/neuroflowplanner/images/logo_latte.png";
            ImageView logo = new ImageView(new Image(getClass().getResourceAsStream(logoPath)));
            logo.setFitHeight(80);
            logo.setPreserveRatio(true);
            content.getChildren().add(logo);
        } catch (Exception ignored) {}

        // Title
        Label title = new Label("Добро пожаловать в НейроПоток!");
        title.getStyleClass().add("welcome-title");

        Label subtitle = new Label("Умный ИИ-планировщик задач");
        subtitle.getStyleClass().add("welcome-subtitle");

        // Mode selection section
        VBox modeSection = createModeSelectionSection();

        // Mode-specific panels
        createModePanels();

        modePanelContainer = new StackPane();
        modePanelContainer.getChildren().addAll(offlineModePanel, localModePanel, externalModePanel);
        showModePanel(selectedMode);

        // Info note
        HBox noteBox = new HBox(10);
        noteBox.setAlignment(Pos.CENTER_LEFT);
        noteBox.getStyleClass().add("welcome-note-box");
        noteBox.setPadding(new Insets(12));

        FontIcon noteIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 18);
        noteIcon.getStyleClass().add("welcome-note-icon");

        Label noteText = new Label("Режим ИИ можно изменить позже в «Настройках»");
        noteText.getStyleClass().add("welcome-note-text");
        noteText.setWrapText(true);

        noteBox.getChildren().addAll(noteIcon, noteText);
        VBox onboardingHintsSection = createOnboardingHintsSection();

        content.getChildren().addAll(
                title,
                subtitle,
                modeSection,
                modePanelContainer,
                onboardingHintsSection,
                noteBox
        );

        scrollPane.setContent(content);
        dialogPane.setContent(scrollPane);

        // Buttons
        ButtonType continueBtn = new ButtonType("Продолжить", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipBtn = new ButtonType("Офлайн режим", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(continueBtn, skipBtn);

        // Style buttons
        continueButton = (Button) dialogPane.lookupButton(continueBtn);
        continueButton.getStyleClass().add("welcome-save-btn");
        updateContinueButtonState();

        Button skipButton = (Button) dialogPane.lookupButton(skipBtn);
        skipButton.getStyleClass().add("welcome-skip-btn");

        dialog.showAndWait().ifPresent(result -> {
            if (result == continueBtn) {
                saveConfiguration();
                showResultDialog(true);
            } else {
                // Skip = Offline mode
                selectedMode = AiMode.OFFLINE;
                saveConfiguration();
                showResultDialog(false);
            }

            // Mark first launch as done
            ConfigManager.setProperty(FIRST_LAUNCH_KEY, "true");
        });
    }

    private VBox createModeSelectionSection() {
        VBox section = new VBox(12);
        section.getStyleClass().add("welcome-desc-box");
        section.setPadding(new Insets(15));
        section.setMaxWidth(650);

        Label sectionTitle = new Label("Выберите режим ИИ");
        sectionTitle.getStyleClass().add("welcome-section-title");

        modeToggleGroup = new ToggleGroup();

        VBox modesBox = new VBox(6);

        Label modeDescriptionLabel = new Label();
        modeDescriptionLabel.getStyleClass().add("welcome-mode-description");
        modeDescriptionLabel.setWrapText(true);

        // Offline mode
        RadioButton offlineRadio = createModeRadioButton(
                AiMode.OFFLINE,
                "Офлайн (без ИИ)",
                "ИИ-функции отключены. Используются заглушки.",
                MaterialDesignW.WIFI_OFF,
                modeDescriptionLabel
        );
        offlineRadio.setSelected(true);
        modeDescriptionLabel.setText("ИИ-функции отключены. Используются заглушки.");

        // Local Ollama mode
        RadioButton localRadio = createModeRadioButton(
                AiMode.LOCAL_OLLAMA,
                "Локальный (Ollama)",
                "Подключение к локальному серверу Ollama",
                MaterialDesignS.SERVER,
                modeDescriptionLabel
        );

        // External API mode
        RadioButton externalRadio = createModeRadioButton(
                AiMode.EXTERNAL_OPENAI,
                "Внешний API (OpenAI-совместимый)",
                "Подключение к внешнему API с ключом",
                MaterialDesignC.CLOUD,
                modeDescriptionLabel
        );

        modesBox.getChildren().addAll(offlineRadio, localRadio, externalRadio);
        section.getChildren().addAll(sectionTitle, modesBox, modeDescriptionLabel);

        return section;
    }

    private VBox createOnboardingHintsSection() {
        VBox section = new VBox(8);
        section.getStyleClass().add("welcome-desc-box");
        section.setPadding(new Insets(15));
        section.setMaxWidth(650);

        Label sectionTitle = new Label("Быстрый старт");
        sectionTitle.getStyleClass().add("welcome-section-title");

        VBox hints = new VBox(8);
        hints.getChildren().addAll(
                createOnboardingHint(
                        MaterialDesignM.MAGNIFY,
                        "Командная палитра",
                        "Ctrl/Cmd+K для быстрого запуска команд."
                ),
                createOnboardingHint(
                        MaterialDesignM.MAGNIFY,
                        "Глобальный поиск",
                        "Ctrl/Cmd+F для поиска по задачам и действиям."
                ),
                createOnboardingHint(
                        MaterialDesignM.MENU_OPEN,
                        "Сворачивание панелей",
                        "Левая панель: кнопка «бургер». Правая панель: toggle в заголовке панели."
                )
        );

        section.getChildren().addAll(sectionTitle, hints);
        return section;
    }

    private HBox createOnboardingHint(org.kordamp.ikonli.Ikon iconCode, String title, String description) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = FontIcon.of(iconCode, 16);
        icon.getStyleClass().add("welcome-note-icon");

        VBox textBox = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("welcome-field-label");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("welcome-hint-text");
        descriptionLabel.setWrapText(true);

        textBox.getChildren().addAll(titleLabel, descriptionLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        row.getChildren().addAll(icon, textBox);
        return row;
    }

    private RadioButton createModeRadioButton(
            AiMode mode,
            String title,
            String description,
            org.kordamp.ikonli.Ikon icon,
            Label modeDescriptionLabel
    ) {
        RadioButton radio = new RadioButton();
        radio.setToggleGroup(modeToggleGroup);
        radio.setUserData(mode);
        radio.getStyleClass().add("welcome-mode-radio");

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);

        FontIcon modeIcon = FontIcon.of(icon, 24);
        modeIcon.getStyleClass().add("welcome-mode-icon");

        VBox textBox = new VBox(2);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("welcome-mode-title");

        textBox.getChildren().add(titleLabel);
        content.getChildren().addAll(modeIcon, textBox);

        radio.setGraphic(content);

        radio.setOnAction(e -> {
            selectedMode = mode;
            showModePanel(mode);
            if (modeDescriptionLabel != null) {
                modeDescriptionLabel.setText(description);
            }
            updateContinueButtonState();
        });

        return radio;
    }

    private void createModePanels() {
        // Offline panel
        offlineModePanel = new VBox(12);
        offlineModePanel.getStyleClass().add("welcome-mode-panel");
        offlineModePanel.setPadding(new Insets(15));

        Label offlineInfo = new Label(
                "В офлайн-режиме ИИ-функции недоступны.\n\n" +
                "Вы можете использовать все остальные функции приложения:\n" +
                "• Создание и управление задачами\n" +
                "• Календарь и планирование\n" +
                "• Отслеживание целей\n" +
                "• Статистика продуктивности"
        );
        offlineInfo.setWrapText(true);
        offlineInfo.getStyleClass().add("welcome-desc-text");
        offlineModePanel.getChildren().add(offlineInfo);

        // Local Ollama panel
        localModePanel = createLocalModePanel();

        // External API panel
        externalModePanel = createExternalModePanel();
    }

    private VBox createLocalModePanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("welcome-mode-panel");
        panel.setPadding(new Insets(15));

        Label urlLabel = new Label("Адрес сервера Ollama:");
        urlLabel.getStyleClass().add("welcome-field-label");

        localUrlField = new TextField();
        localUrlField.setPromptText("http://localhost:11434");
        localUrlField.setText(LocalOllamaClient.DEFAULT_BASE_URL);
        localUrlField.getStyleClass().add("welcome-text-field");
        localUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
            localConnectionValid = false;
            updateContinueButtonState();
        });

        Label modelLabel = new Label("Модель:");
        modelLabel.getStyleClass().add("welcome-field-label");

        localModelCombo = new ComboBox<>();
        localModelCombo.setEditable(true);
        localModelCombo.setPromptText("Выберите или введите модель");
        localModelCombo.getStyleClass().add("ai-combo-box");
        localModelCombo.setMaxWidth(Double.MAX_VALUE);
        localModelCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateContinueButtonState());

        // Status
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        localStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        localStatusIcon.getStyleClass().add("welcome-status-icon-neutral");

        localStatusLabel = new Label("Введите адрес сервера и проверьте соединение");
        localStatusLabel.getStyleClass().add("welcome-status-text");

        statusBox.getChildren().addAll(localStatusIcon, localStatusLabel);

        // Check button
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button checkBtn = new Button("Проверить соединение");
        checkBtn.getStyleClass().add("welcome-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 14);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        btnBox.getChildren().addAll(checkBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = localUrlField.getText().trim();
            if (url.isEmpty()) {
                updateLocalStatus("error", "Введите адрес сервера");
                return;
            }

            checkBtn.setDisable(true);
            progress.setVisible(true);
            updateLocalStatus("checking", "Проверка соединения...");

            LocalOllamaClient testClient = AiClientFactory.getInstance()
                    .createTestOllamaClient(url, null);

            testClient.testConnection().thenAccept(result -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                progress.setVisible(false);

                if (result.success()) {
                    localConnectionValid = true;
                    updateLocalStatus("success", result.message());

                    // Load available models
                    if (result.hasModels()) {
                        localModelCombo.getItems().clear();
                        localModelCombo.getItems().addAll(result.availableModels());
                        if (!result.availableModels().isEmpty()) {
                            localModelCombo.setValue(result.availableModels().get(0));
                        }
                    }
                } else {
                    localConnectionValid = false;
                    updateLocalStatus("error", result.message());
                }
                updateContinueButtonState();
            }));
        });

        // Example hint
        Label hint = new Label("Пример: http://192.168.1.100:11434");
        hint.getStyleClass().add("welcome-hint-text");

        panel.getChildren().addAll(urlLabel, localUrlField, hint, modelLabel, localModelCombo, btnBox, statusBox);
        return panel;
    }

    private VBox createExternalModePanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("welcome-mode-panel");
        panel.setPadding(new Insets(15));

        Label apiNote = new Label("Работает только с OpenAI-совместимыми API");
        apiNote.getStyleClass().add("welcome-hint-text");
        apiNote.setStyle("-fx-font-style: italic;");

        Label urlLabel = new Label("Базовый URL API:");
        urlLabel.getStyleClass().add("welcome-field-label");

        externalUrlField = new TextField();
        externalUrlField.setPromptText("https://api.openai.com/v1");
        externalUrlField.setText(ExternalOpenAiClient.DEFAULT_BASE_URL);
        externalUrlField.getStyleClass().add("welcome-text-field");
        externalUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
            externalConnectionValid = false;
            updateContinueButtonState();
        });

        Label keyLabel = new Label("API ключ:");
        keyLabel.getStyleClass().add("welcome-field-label");

        externalKeyField = new PasswordField();
        externalKeyField.setPromptText("sk-...");
        externalKeyField.getStyleClass().add("welcome-text-field");
        externalKeyField.textProperty().addListener((obs, oldVal, newVal) -> {
            externalConnectionValid = false;
            updateContinueButtonState();
        });

        Label modelLabel = new Label("ID модели:");
        modelLabel.getStyleClass().add("welcome-field-label");

        externalModelField = new TextField();
        externalModelField.setPromptText("Например: openai/gpt-5.4");
        externalModelField.getStyleClass().add("welcome-text-field");
        String currentModel = ConfigManager.getProperty(ExternalOpenAiClient.CONFIG_MODEL);
        if (currentModel != null && !currentModel.isBlank()) {
            externalModelField.setText(currentModel);
        }
        externalModelField.textProperty().addListener((obs, oldVal, newVal) -> updateContinueButtonState());

        Button manageModelsBtn = new Button("Каталог моделей");
        manageModelsBtn.getStyleClass().add("welcome-check-btn");
        manageModelsBtn.setGraphic(FontIcon.of(MaterialDesignC.CUBE_OUTLINE, 14));
        manageModelsBtn.setOnAction(e -> openExternalModelManagementDialog());

        // Status
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);

        externalStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        externalStatusIcon.getStyleClass().add("welcome-status-icon-neutral");

        externalStatusLabel = new Label("Введите URL, ключ API и проверьте соединение");
        externalStatusLabel.getStyleClass().add("welcome-status-text");

        statusBox.getChildren().addAll(externalStatusIcon, externalStatusLabel);

        // Check button
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button checkBtn = new Button("Проверить соединение");
        checkBtn.getStyleClass().add("welcome-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 14);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        btnBox.getChildren().addAll(checkBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = externalUrlField.getText().trim();
            String key = externalKeyField.getText().trim();

            if (url.isEmpty()) {
                updateExternalStatus("error", "Введите URL API");
                return;
            }
            if (key.isEmpty()) {
                updateExternalStatus("error", "Введите API ключ");
                return;
            }

            checkBtn.setDisable(true);
            progress.setVisible(true);
            updateExternalStatus("checking", "Проверка соединения...");

            ExternalOpenAiClient testClient = AiClientFactory.getInstance()
                    .createTestExternalClient(url, key, null);

            testClient.testConnection().thenAccept(result -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                progress.setVisible(false);

                if (result.success()) {
                    externalConnectionValid = true;
                    updateExternalStatus("success", result.message());

                    updateExternalDiscoveredModels(
                            result.hasModels() ? result.availableModels() : java.util.List.of(),
                            result.hasMultimodalModels() ? result.multimodalModels() : java.util.List.of(),
                            result.hasAudioInputModels() ? result.audioInputModels() : java.util.List.of(),
                            result.hasFileInputModels() ? result.fileInputModels() : java.util.List.of());
                    ConfigManager.setExternalApiModelCatalog(
                            result.hasModelCatalog() ? result.modelCatalog() : java.util.List.of());
                } else {
                    externalConnectionValid = false;
                    updateExternalStatus("error", result.message());
                }
                updateContinueButtonState();
            }));
        });

        panel.getChildren().addAll(apiNote, urlLabel, externalUrlField, keyLabel, externalKeyField,
                modelLabel, externalModelField, manageModelsBtn, btnBox, statusBox);
        return panel;
    }

    private void showModePanel(AiMode mode) {
        offlineModePanel.setVisible(mode == AiMode.OFFLINE);
        offlineModePanel.setManaged(mode == AiMode.OFFLINE);

        localModePanel.setVisible(mode == AiMode.LOCAL_OLLAMA);
        localModePanel.setManaged(mode == AiMode.LOCAL_OLLAMA);

        externalModePanel.setVisible(mode == AiMode.EXTERNAL_OPENAI);
        externalModePanel.setManaged(mode == AiMode.EXTERNAL_OPENAI);
    }

    private void updateContinueButtonState() {
        if (continueButton == null) return;

        boolean canContinue = switch (selectedMode) {
            case OFFLINE -> true;
            case LOCAL_OLLAMA -> localConnectionValid && getLocalModel() != null && !getLocalModel().isBlank();
            case EXTERNAL_OPENAI -> externalConnectionValid && getExternalModel() != null && !getExternalModel().isBlank();
        };

        continueButton.setDisable(!canContinue);
    }

    private String getLocalModel() {
        if (localModelCombo == null) return null;
        String value = localModelCombo.getValue();
        if (value != null && !value.isBlank()) return value;
        String editorValue = localModelCombo.getEditor().getText();
        return editorValue != null ? editorValue.trim() : null;
    }

    private String getExternalModel() {
        if (externalModelField == null) return null;
        String value = AiConfigDefaults.normalizeExternalModelId(externalModelField.getText());
        externalModelField.setText(value);
        return value;
    }

    private void updateExternalDiscoveredModels(
            java.util.List<String> models,
            java.util.List<String> multimodalModels,
            java.util.List<String> audioInputModels,
            java.util.List<String> fileInputModels) {
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
        ConfigManager.setExternalApiDiscoveredModels(externalDiscoveredModels);
        ConfigManager.setExternalApiMultimodalModels(externalMultimodalModels);
        ConfigManager.setExternalApiAudioInputModels(externalAudioInputModels);
        ConfigManager.setExternalApiFileInputModels(externalFileInputModels);
        String currentValue = getExternalModel();
        if ((currentValue == null || currentValue.isBlank()) && !externalDiscoveredModels.isEmpty()) {
            externalModelField.setText(externalDiscoveredModels.get(0));
        }
        updateContinueButtonState();
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

    private void openExternalModelManagementDialog() {
        ModelManagementDialog.Result result = ModelManagementDialog.showExternalApi(
                java.util.List.copyOf(externalDiscoveredModels),
                java.util.List.copyOf(externalMultimodalModels),
                java.util.List.copyOf(externalAudioInputModels),
                java.util.List.copyOf(externalFileInputModels),
                java.util.List.copyOf(externalCustomModels),
                getExternalModel()
        );
        if (result == null) {
            return;
        }
        externalCustomModels.clear();
        externalCustomModels.addAll(result.customModels());
        externalModelField.setText(AiConfigDefaults.normalizeExternalModelId(result.selectedModel()));
        updateContinueButtonState();
    }

    private void updateLocalStatus(String status, String text) {
        updateStatusIcon(localStatusIcon, localStatusLabel, status, text);
    }

    private void updateExternalStatus(String status, String text) {
        updateStatusIcon(externalStatusIcon, externalStatusLabel, status, text);
    }

    private void updateStatusIcon(FontIcon icon, Label label, String status, String text) {
        icon.getStyleClass().removeAll("welcome-status-icon-neutral", "welcome-status-icon-success",
                "welcome-status-icon-error", "welcome-status-icon-checking");

        switch (status) {
            case "success" -> {
                icon.setIconCode(MaterialDesignC.CHECK_CIRCLE);
                icon.getStyleClass().add("welcome-status-icon-success");
            }
            case "error" -> {
                icon.setIconCode(MaterialDesignA.ALERT_CIRCLE);
                icon.getStyleClass().add("welcome-status-icon-error");
            }
            case "checking" -> {
                icon.setIconCode(MaterialDesignS.SYNC);
                icon.getStyleClass().add("welcome-status-icon-checking");
            }
            default -> {
                icon.setIconCode(MaterialDesignC.CIRCLE_OUTLINE);
                icon.getStyleClass().add("welcome-status-icon-neutral");
            }
        }
        label.setText(text);
    }

    private void saveConfiguration() {
        applyLayoutPresetConfiguration();

        // Save mode
        ConfigManager.setProperty(AiClientFactory.CONFIG_AI_MODE, selectedMode.getConfigValue());

        switch (selectedMode) {
            case OFFLINE -> {
                // Nothing extra to save
            }
            case LOCAL_OLLAMA -> {
                String url = localUrlField.getText().trim();
                String model = getLocalModel();
                ConfigManager.setProperty(LocalOllamaClient.CONFIG_BASE_URL, url);
                if (model != null && !model.isBlank()) {
                    ConfigManager.setProperty(LocalOllamaClient.CONFIG_MODEL, model);
                }
            }
            case EXTERNAL_OPENAI -> {
                String url = externalUrlField.getText().trim();
                String key = externalKeyField.getText().trim();
                String model = getExternalModel();
                ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_BASE_URL, url);
                ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_API_KEY, key);
                if (model != null && !model.isBlank()) {
                    rememberExternalCustomModel(model);
                    ConfigManager.setProperty(ExternalOpenAiClient.CONFIG_MODEL, model);
                }
                ConfigManager.setExternalApiCustomModels(externalCustomModels);
                ConfigManager.setExternalApiDiscoveredModels(externalDiscoveredModels);
                ConfigManager.setExternalApiMultimodalModels(externalMultimodalModels);
                ConfigManager.setExternalApiAudioInputModels(externalAudioInputModels);
                ConfigManager.setExternalApiFileInputModels(externalFileInputModels);
                if (ConfigManager.getExternalApiModelCatalog().isEmpty()) {
                    ConfigManager.setExternalApiModelCatalog(java.util.List.of());
                }
            }
        }

        // Reload the client factory
        AiClientFactory.getInstance().reloadFromConfig();
    }

    private void applyLayoutPresetConfiguration() {
        ConfigManager.setProperty(UX_LAYOUT_PRESET_KEY, "obsidian-inspired");
        ConfigManager.setProperty(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT);
        ConfigManager.setProperty(
                ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE,
                ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN);

        ConfigManager.setUxLayoutStateLeftPanelCollapsed(false);
        ConfigManager.setUxLayoutStateRightPanelCollapsed(true);
    }

    private void showResultDialog(boolean configured) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(configured ? "Настройка завершена" : "Офлайн режим");
        dialog.setHeaderText(null);

        DialogPane pane = dialog.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            pane.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
        pane.getStyleClass().add("welcome-dialog");
        pane.setPrefWidth(450);
        pane.setMinHeight(280);

        VBox content = new VBox(15);
        content.setPadding(new Insets(25));
        content.setAlignment(Pos.CENTER);

        // Icon
        StackPane iconPane = new StackPane();
        iconPane.setMinSize(60, 60);
        iconPane.setMaxSize(60, 60);
        FontIcon icon;

        if (configured && selectedMode != AiMode.OFFLINE) {
            iconPane.setStyle("-fx-background-color: " + (isDark ? "rgba(166,227,161,0.15)" : "rgba(64,160,43,0.1)") + "; -fx-background-radius: 50%;");
            icon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 32);
            icon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#a6e3a1" : "#40a02b"));
        } else {
            iconPane.setStyle("-fx-background-color: " + (isDark ? "rgba(249,226,175,0.15)" : "rgba(223,142,29,0.1)") + "; -fx-background-radius: 50%;");
            icon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 32);
            icon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f9e2af" : "#df8e1d"));
        }
        iconPane.getChildren().add(icon);

        // Title
        Label titleLabel = new Label(configured && selectedMode != AiMode.OFFLINE ? "Готово!" : "Офлайн режим");
        titleLabel.getStyleClass().add("welcome-title");
        titleLabel.setStyle("-fx-font-size: 20px;");

        // Message
        Label msgLabel = new Label();
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(380);
        msgLabel.setAlignment(Pos.CENTER);
        msgLabel.setStyle("-fx-text-alignment: center;");
        msgLabel.getStyleClass().add("welcome-result-text");

        String modeDesc = switch (selectedMode) {
            case OFFLINE -> "Офлайн режим";
            case LOCAL_OLLAMA -> "Локальный Ollama: " + localUrlField.getText().trim();
            case EXTERNAL_OPENAI -> "Внешний API: " + externalUrlField.getText().trim();
        };

        if (configured && selectedMode != AiMode.OFFLINE) {
            msgLabel.setText(
                    "Режим ИИ: " + selectedMode.getDisplayName() + "\n\n" +
                    modeDesc + "\n\n" +
                    "ИИ-функции теперь доступны!\n\n" +
                    "Изменить настройки можно в «Настройках»."
            );
        } else {
            msgLabel.setText(
                    "ИИ-функции недоступны.\n\n" +
                    "Остальные функции работают.\n\n" +
                    "Настроить ИИ можно в «Настройках»."
            );
        }

        content.getChildren().addAll(iconPane, titleLabel, msgLabel);
        pane.setContent(content);

        pane.getButtonTypes().add(ButtonType.OK);
        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText("Начать работу");
        okBtn.getStyleClass().add("welcome-save-btn");

        dialog.showAndWait();
    }
}
