package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.sync.AccountLinkStrategy;
import com.example.neuroflowplanner.sync.LocalSyncProfileSummary;
import com.example.neuroflowplanner.sync.SyncHealthEvent;
import com.example.neuroflowplanner.sync.SyncPayloads;
import com.example.neuroflowplanner.sync.SyncClientFacade;
import com.example.neuroflowplanner.sync.SyncUiSnapshot;
import com.example.neuroflowplanner.sync.SyncUiStatus;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.CloudSyncUrlSupport;
import com.example.neuroflowplanner.util.ConfigManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

public final class CloudSyncSettingsSection implements AutoCloseable {
    private static final DateTimeFormatter UI_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final PseudoClass SELECTED_OPTION = PseudoClass.getPseudoClass("selected");

    private final Supplier<Window> ownerSupplier;
    private final BooleanSupplier darkThemeSupplier;
    private final SyncClientFacade syncFacade;

    private final VBox root;
    private final FontIcon statusIcon;
    private final Label statusTitleLabel;
    private final Label statusDetailLabel;
    private final Label accountSummaryLabel;
    private final Label strategySummaryLabel;
    private final Label lastSyncLabel;
    private final Label rolloutSummaryLabel;
    private final Label diagnosticsSummaryLabel;
    private final Label errorSummaryLabel;
    private final VBox warningBox;
    private final Label warningLabel;
    private final TextField baseUrlField;
    private final Button saveBaseUrlButton;
    private final VBox signedOutBox;
    private final VBox signedInBox;
    private TextField loginEmailField;
    private PasswordField loginPasswordField;
    private Button loginButton;
    private TextField registerEmailField;
    private PasswordField registerPasswordField;
    private TextField registerDisplayNameField;
    private Button registerButton;
    private final Button syncNowButton;
    private final Button chooseStrategyButton;
    private final Button logoutButton;
    private final Button toggleInternalToolsButton;
    private final VBox internalToolsBox;
    private final Button forceBootstrapButton;
    private final Button clearDiagnosticsButton;
    private final Button copyDebugSummaryButton;
    private final Button exportDiagnosticsBundleButton;
    private final Button refreshDevicesButton;
    private final Button disconnectLocalSessionButton;
    private final Button prepareReauthButton;
    private final Button copyHealthSummaryButton;
    private final Label devicesSummaryLabel;
    private final VBox devicesListBox;
    private final Label healthSummaryLabel;
    private final VBox healthTimelineBox;

    private AutoCloseable listenerRegistration;

    public CloudSyncSettingsSection(Supplier<Window> ownerSupplier, BooleanSupplier darkThemeSupplier) {
        this.ownerSupplier = ownerSupplier == null ? () -> null : ownerSupplier;
        this.darkThemeSupplier = darkThemeSupplier == null ? ConfigManager::isDarkTheme : darkThemeSupplier;
        this.syncFacade = SyncClientFacade.getInstance();

        root = new VBox(12);

        Label title = new Label("Облачная синхронизация");
        title.getStyleClass().add("settings-section-title");

        statusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 16);
        statusTitleLabel = new Label("Не подключено");
        statusTitleLabel.getStyleClass().add("settings-toggle-label");
        statusDetailLabel = new Label("Войдите в аккаунт, чтобы включить облачную синхронизацию.");
        statusDetailLabel.getStyleClass().add("settings-muted-text");
        statusDetailLabel.setWrapText(true);

        VBox statusTextBox = new VBox(4, statusTitleLabel, statusDetailLabel);
        statusTextBox.setFillWidth(true);
        HBox statusHeader = new HBox(10, statusIcon, statusTextBox);
        statusHeader.setAlignment(Pos.TOP_LEFT);

        accountSummaryLabel = new Label("Аккаунт: не подключен");
        accountSummaryLabel.getStyleClass().add("settings-muted-text");
        strategySummaryLabel = new Label("Стратегия: не выбрана");
        strategySummaryLabel.getStyleClass().add("settings-muted-text");
        lastSyncLabel = new Label("Последняя синхронизация: —");
        lastSyncLabel.getStyleClass().add("settings-muted-text");
        rolloutSummaryLabel = new Label("Бета-режим: —");
        rolloutSummaryLabel.getStyleClass().add("settings-muted-text");
        rolloutSummaryLabel.setWrapText(true);
        diagnosticsSummaryLabel = new Label("Диагностика: —");
        diagnosticsSummaryLabel.getStyleClass().add("settings-muted-text");
        diagnosticsSummaryLabel.setWrapText(true);
        errorSummaryLabel = new Label("Последняя ошибка: —");
        errorSummaryLabel.getStyleClass().add("settings-muted-text");
        errorSummaryLabel.setWrapText(true);

        VBox statusCard = new VBox(8,
                statusHeader,
                accountSummaryLabel,
                strategySummaryLabel,
                lastSyncLabel,
                rolloutSummaryLabel,
                diagnosticsSummaryLabel,
                errorSummaryLabel);
        statusCard.getStyleClass().add("settings-plugin-box");

        warningLabel = new Label();
        warningLabel.getStyleClass().add("settings-note-text");
        warningLabel.setWrapText(true);
        FontIcon noteIcon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE, 16);
        noteIcon.getStyleClass().add("settings-note-icon");
        warningBox = new VBox();
        HBox warningRow = new HBox(8, noteIcon, warningLabel);
        warningRow.setAlignment(Pos.TOP_LEFT);
        warningBox.getChildren().add(warningRow);
        warningBox.getStyleClass().add("settings-note-box");
        warningBox.setPadding(new Insets(10));
        warningBox.setManaged(false);
        warningBox.setVisible(false);

        Label baseUrlLabel = new Label("Адрес сервера синхронизации");
        baseUrlLabel.getStyleClass().add("settings-field-label");
        baseUrlField = new TextField();
        baseUrlField.getStyleClass().add("settings-text-field");
        baseUrlField.setPromptText("http://127.0.0.1:8000");
        HBox.setHgrow(baseUrlField, Priority.ALWAYS);
        saveBaseUrlButton = new Button("Сохранить адрес");
        saveBaseUrlButton.getStyleClass().add("settings-action-btn");
        saveBaseUrlButton.setOnAction(event -> handleSaveBaseUrl());
        HBox baseUrlRow = new HBox(10, baseUrlField, saveBaseUrlButton);
        baseUrlRow.setAlignment(Pos.CENTER_LEFT);

        signedOutBox = new VBox(12,
                buildLoginPane(),
                buildRegisterPane());

        syncNowButton = new Button("Синхронизировать сейчас");
        syncNowButton.getStyleClass().add("settings-save-btn");
        syncNowButton.setOnAction(event -> handleManualSync());
        chooseStrategyButton = new Button("Выбрать стратегию");
        chooseStrategyButton.getStyleClass().add("settings-action-btn");
        chooseStrategyButton.setOnAction(event -> openStrategyDialog(snapshot()));
        logoutButton = new Button("Выйти");
        logoutButton.getStyleClass().add("settings-check-btn");
        logoutButton.setOnAction(event -> handleLogout());

        HBox signedInActions = new HBox(10, syncNowButton, chooseStrategyButton, logoutButton);
        signedInActions.setAlignment(Pos.CENTER_LEFT);
        signedInBox = new VBox(10, signedInActions);
        signedInBox.setManaged(false);
        signedInBox.setVisible(false);

        toggleInternalToolsButton = new Button("Показать внутренние инструменты beta");
        toggleInternalToolsButton.getStyleClass().add("settings-check-btn");
        toggleInternalToolsButton.setOnAction(event -> toggleInternalToolsVisibility());

        Label internalToolsTitle = new Label("Внутренние инструменты beta");
        internalToolsTitle.getStyleClass().add("settings-field-label");
        Label internalToolsDescription = new Label(
                "Эти действия нужны для beta-smoke и recovery: повторный bootstrap, очистка diagnostics, проверка device/session состояния и копирование debug summary.");
        internalToolsDescription.getStyleClass().add("settings-muted-text");
        internalToolsDescription.setWrapText(true);
        forceBootstrapButton = new Button("Повторить bootstrap");
        forceBootstrapButton.getStyleClass().add("settings-action-btn");
        forceBootstrapButton.setOnAction(event -> handleForceBootstrap());
        clearDiagnosticsButton = new Button("Сбросить диагностику");
        clearDiagnosticsButton.getStyleClass().add("settings-action-btn");
        clearDiagnosticsButton.setOnAction(event -> handleClearDiagnostics());
        copyDebugSummaryButton = new Button("Скопировать debug summary");
        copyDebugSummaryButton.getStyleClass().add("settings-action-btn");
        copyDebugSummaryButton.setOnAction(event -> handleCopyDebugSummary());
        exportDiagnosticsBundleButton = new Button("Экспортировать diagnostics bundle");
        exportDiagnosticsBundleButton.getStyleClass().add("settings-action-btn");
        exportDiagnosticsBundleButton.setOnAction(event -> handleExportDiagnosticsBundle());
        HBox internalActions = new HBox(
                10,
                forceBootstrapButton,
                clearDiagnosticsButton,
                copyDebugSummaryButton,
                exportDiagnosticsBundleButton);
        internalActions.setAlignment(Pos.CENTER_LEFT);
        Label devicesTitle = new Label("Привязанные устройства и локальная сессия");
        devicesTitle.getStyleClass().add("settings-field-label");
        Label devicesDescription = new Label(
                "Показывает устройства, связанные с текущим аккаунтом. Можно отозвать чужое устройство или локально очистить текущую cloud-сессию на этом компьютере.");
        devicesDescription.getStyleClass().add("settings-muted-text");
        devicesDescription.setWrapText(true);
        refreshDevicesButton = new Button("Обновить список устройств");
        refreshDevicesButton.getStyleClass().add("settings-action-btn");
        refreshDevicesButton.setOnAction(event -> handleRefreshDevices());
        disconnectLocalSessionButton = new Button("Сбросить локальную сессию");
        disconnectLocalSessionButton.getStyleClass().add("settings-action-btn");
        disconnectLocalSessionButton.setOnAction(event -> handleDisconnectLocalSession());
        prepareReauthButton = new Button("Подготовить повторный вход");
        prepareReauthButton.getStyleClass().add("settings-action-btn");
        prepareReauthButton.setOnAction(event -> handlePrepareReauthentication());
        HBox deviceActions = new HBox(10, refreshDevicesButton, disconnectLocalSessionButton, prepareReauthButton);
        deviceActions.setAlignment(Pos.CENTER_LEFT);
        devicesSummaryLabel = new Label("Список устройств ещё не запрашивался.");
        devicesSummaryLabel.getStyleClass().add("settings-muted-text");
        devicesSummaryLabel.setWrapText(true);
        devicesListBox = new VBox(8);
        Label healthTitle = new Label("Sync health и recovery timeline");
        healthTitle.getStyleClass().add("settings-field-label");
        Label healthDescription = new Label(
                "Показывает последние sync-циклы, recovery actions и причины deferred convergence без просмотра логов backend.");
        healthDescription.getStyleClass().add("settings-muted-text");
        healthDescription.setWrapText(true);
        copyHealthSummaryButton = new Button("Скопировать health summary");
        copyHealthSummaryButton.getStyleClass().add("settings-action-btn");
        copyHealthSummaryButton.setOnAction(event -> handleCopyHealthSummary());
        healthSummaryLabel = new Label("История sync-циклов появится после первого запуска синхронизации.");
        healthSummaryLabel.getStyleClass().add("settings-muted-text");
        healthSummaryLabel.setWrapText(true);
        healthTimelineBox = new VBox(8);
        internalToolsBox = buildCard(
                internalToolsTitle,
                internalToolsDescription,
                internalActions,
                devicesTitle,
                devicesDescription,
                deviceActions,
                devicesSummaryLabel,
                devicesListBox,
                healthTitle,
                healthDescription,
                copyHealthSummaryButton,
                healthSummaryLabel,
                healthTimelineBox);
        internalToolsBox.setManaged(false);
        internalToolsBox.setVisible(false);

        root.getChildren().addAll(
                title,
                statusCard,
                warningBox,
                baseUrlLabel,
                baseUrlRow,
                signedOutBox,
                signedInBox,
                toggleInternalToolsButton,
                internalToolsBox);

        try {
            listenerRegistration = syncFacade.addListener(snapshot -> {
                if (Platform.isFxApplicationThread()) {
                    applySnapshot(snapshot);
                } else {
                    Platform.runLater(() -> applySnapshot(snapshot));
                }
            });
        } catch (Exception ignored) {
            listenerRegistration = null;
        }
        applySnapshot(syncFacade.snapshot());
    }

    public VBox getContent() {
        return root;
    }

    @Override
    public void close() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (Exception ignored) {
                // no-op
            }
            listenerRegistration = null;
        }
    }

    private VBox buildLoginPane() {
        Label loginTitle = new Label("Вход");
        loginTitle.getStyleClass().add("settings-field-label");
        loginEmailField = new TextField();
        loginEmailField.getStyleClass().add("settings-text-field");
        loginEmailField.setPromptText("email@example.com");
        loginPasswordField = new PasswordField();
        loginPasswordField.getStyleClass().add("settings-text-field");
        loginPasswordField.setPromptText("Пароль");
        loginButton = new Button("Войти");
        loginButton.getStyleClass().add("settings-save-btn");
        loginButton.setOnAction(event -> handleLogin());
        HBox actions = new HBox(10, loginButton);
        return buildCard(loginTitle, loginEmailField, loginPasswordField, actions);
    }

    private VBox buildRegisterPane() {
        Label registerTitle = new Label("Регистрация");
        registerTitle.getStyleClass().add("settings-field-label");
        registerDisplayNameField = new TextField();
        registerDisplayNameField.getStyleClass().add("settings-text-field");
        registerDisplayNameField.setPromptText("Имя профиля");
        registerEmailField = new TextField();
        registerEmailField.getStyleClass().add("settings-text-field");
        registerEmailField.setPromptText("email@example.com");
        registerPasswordField = new PasswordField();
        registerPasswordField.getStyleClass().add("settings-text-field");
        registerPasswordField.setPromptText("Пароль");
        registerButton = new Button("Создать аккаунт");
        registerButton.getStyleClass().add("settings-action-btn");
        registerButton.setOnAction(event -> handleRegister());
        HBox actions = new HBox(10, registerButton);
        return buildCard(registerTitle, registerDisplayNameField, registerEmailField, registerPasswordField, actions);
    }

    private VBox buildCard(Label title, javafx.scene.Node... content) {
        VBox card = new VBox(8);
        card.getStyleClass().add("settings-plugin-box");
        card.getChildren().add(title);
        card.getChildren().addAll(content);
        return card;
    }

    private void handleSaveBaseUrl() {
        String baseUrl = normalizedBaseUrl();
        if (baseUrl.isBlank()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Укажите адрес сервера синхронизации.");
            return;
        }
        syncFacade.saveBaseUrl(baseUrl);
    }

    private void handleLogin() {
        if (!validateBaseUrl()) {
            return;
        }
        String email = safe(loginEmailField.getText());
        String password = safe(loginPasswordField.getText());
        if (email.isBlank() || password.isBlank()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Для входа нужны email и пароль.");
            return;
        }
        syncFacade.saveBaseUrl(normalizedBaseUrl());
        syncFacade.login(email, password)
                .thenAccept(AsyncContext.withMdcConsumer(this::handlePostAuthSnapshot));
    }

    private void handleRegister() {
        if (!validateBaseUrl()) {
            return;
        }
        String displayName = safe(registerDisplayNameField.getText());
        String email = safe(registerEmailField.getText());
        String password = safe(registerPasswordField.getText());
        if (displayName.isBlank() || email.isBlank() || password.isBlank()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Для регистрации нужны имя профиля, email и пароль.");
            return;
        }
        if (password.length() < 8) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Пароль должен содержать минимум 8 символов.");
            return;
        }
        syncFacade.saveBaseUrl(normalizedBaseUrl());
        syncFacade.register(email, password, displayName)
                .thenAccept(AsyncContext.withMdcConsumer(this::handlePostAuthSnapshot));
    }

    private void handleManualSync() {
        SyncUiSnapshot current = snapshot();
        if (!current.authenticated()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Сначала войдите в аккаунт.");
            return;
        }
        if (current.strategyRequired()) {
            openStrategyDialog(current);
            return;
        }
        syncFacade.triggerManualSync();
    }

    private void handleLogout() {
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Отключить текущий облачный аккаунт на этом устройстве?",
                ButtonType.YES,
                ButtonType.NO);
        applyDialogStyles(confirm.getDialogPane());
        if (ownerSupplier.get() != null) {
            confirm.initOwner(ownerSupplier.get());
        }
        confirm.setHeaderText(null);
        confirm.setTitle("Облачная синхронизация");
        confirm.getButtonTypes().setAll(
                new ButtonType("Да", ButtonBar.ButtonData.YES),
                new ButtonType("Нет", ButtonBar.ButtonData.NO));
        confirm.showAndWait()
                .filter(buttonType -> buttonType.getButtonData() == ButtonBar.ButtonData.YES)
                .ifPresent(ignored -> syncFacade.logout());
    }

    private void handleForceBootstrap() {
        SyncUiSnapshot current = snapshot();
        if (!current.authenticated()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Сначала войдите в аккаунт.");
            return;
        }
        if (current.strategyRequired()) {
            openStrategyDialog(current);
            return;
        }
        syncFacade.forceBootstrapFromCurrentDevice()
                .thenAccept(AsyncContext.withMdcConsumer(this::applySnapshot));
    }

    private void handleClearDiagnostics() {
        applySnapshot(syncFacade.clearDiagnostics());
        UiErrorNotifier.showInfo(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                "Облачная синхронизация", "Локальная диагностика и последняя sync-ошибка очищены.");
    }

    private void handleCopyDebugSummary() {
        ClipboardContent content = new ClipboardContent();
        content.putString(syncFacade.buildInternalDebugSummary());
        Clipboard.getSystemClipboard().setContent(content);
        UiErrorNotifier.showInfo(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                "Облачная синхронизация", "Debug summary скопирован в буфер обмена.");
    }

    private void handleExportDiagnosticsBundle() {
        File file = chooseDiagnosticsBundleFile();
        if (file == null) {
            return;
        }
        exportDiagnosticsBundleButton.setDisable(true);
        syncFacade.buildDiagnosticsBundle()
                .thenAccept(AsyncContext.withMdcConsumer(bundle -> {
                    try {
                        Files.writeString(file.toPath(), bundle, StandardCharsets.UTF_8);
                        Platform.runLater(() -> UiErrorNotifier.showInfo(
                                ownerSupplier.get(),
                                darkThemeSupplier.getAsBoolean(),
                                "Облачная синхронизация",
                                "Diagnostics bundle сохранён: " + file.getName()));
                    } catch (Exception ex) {
                        Platform.runLater(() -> UiErrorNotifier.showWarning(
                                ownerSupplier.get(),
                                darkThemeSupplier.getAsBoolean(),
                                "Облачная синхронизация",
                                "Не удалось сохранить diagnostics bundle: " + safe(ex.getMessage())));
                    } finally {
                        Platform.runLater(() -> exportDiagnosticsBundleButton.setDisable(false));
                    }
                }))
                .exceptionally(AsyncContext.withMdcFunction(error -> {
                    Throwable cause = AsyncContext.unwrap(error);
                    Platform.runLater(() -> {
                        exportDiagnosticsBundleButton.setDisable(false);
                        UiErrorNotifier.showWarning(
                                ownerSupplier.get(),
                                darkThemeSupplier.getAsBoolean(),
                                "Облачная синхронизация",
                                cause == null || safe(cause.getMessage()).isBlank()
                                        ? "Не удалось собрать diagnostics bundle."
                                        : safe(cause.getMessage()));
                    });
                    return null;
                }));
    }

    private void handleCopyHealthSummary() {
        ClipboardContent content = new ClipboardContent();
        content.putString(syncFacade.buildHealthTimelineSummary());
        Clipboard.getSystemClipboard().setContent(content);
        UiErrorNotifier.showInfo(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                "Облачная синхронизация", "Health summary скопирован в буфер обмена.");
    }

    private void handleRefreshDevices() {
        SyncUiSnapshot current = snapshot();
        if (!current.authenticated()) {
            UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Сначала войдите в аккаунт.");
            return;
        }
        devicesSummaryLabel.setText("Обновляю список привязанных устройств...");
        syncFacade.listLinkedDevices()
                .thenAccept(AsyncContext.withMdcConsumer(devices ->
                        Platform.runLater(() -> renderLinkedDevices(devices, null))))
                .exceptionally(AsyncContext.withMdcFunction(error -> {
                    Throwable cause = AsyncContext.unwrap(error);
                    Platform.runLater(() -> {
                        String message = cause == null || safe(cause.getMessage()).isBlank()
                                ? "Не удалось загрузить список устройств."
                                : safe(cause.getMessage());
                        devicesSummaryLabel.setText(message);
                        UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                                "Облачная синхронизация", message);
                    });
                    return null;
                }));
    }

    private void handleDisconnectLocalSession() {
        SyncUiSnapshot current = snapshot();
        if (!current.authenticated()) {
            UiErrorNotifier.showInfo(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                    "Облачная синхронизация", "Локальная cloud-сессия уже очищена.");
            return;
        }
        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Очистить локальную cloud-сессию только на этом устройстве?",
                ButtonType.YES,
                ButtonType.NO);
        applyDialogStyles(confirm.getDialogPane());
        if (ownerSupplier.get() != null) {
            confirm.initOwner(ownerSupplier.get());
        }
        confirm.setHeaderText(null);
        confirm.setTitle("Облачная синхронизация");
        confirm.getButtonTypes().setAll(
                new ButtonType("Да", ButtonBar.ButtonData.YES),
                new ButtonType("Нет", ButtonBar.ButtonData.NO));
        confirm.showAndWait()
                .filter(buttonType -> buttonType.getButtonData() == ButtonBar.ButtonData.YES)
                .ifPresent(ignored -> {
                    SyncUiSnapshot updated = syncFacade.disconnectLocalSession();
                    applySnapshot(updated);
                    devicesListBox.getChildren().clear();
                    devicesSummaryLabel.setText("Локальная cloud-сессия очищена. Для списка устройств нужен новый вход.");
                    focusReauthFields(updated);
                });
    }

    private void handlePrepareReauthentication() {
        SyncUiSnapshot updated = syncFacade.prepareReauthentication();
        applySnapshot(updated);
        focusReauthFields(updated);
    }

    private void toggleInternalToolsVisibility() {
        boolean show = !internalToolsBox.isVisible();
        internalToolsBox.setVisible(show);
        internalToolsBox.setManaged(show);
        toggleInternalToolsButton.setText(show
                ? "Скрыть внутренние инструменты beta"
                : "Показать внутренние инструменты beta");
        if (show && snapshot().authenticated()) {
            handleRefreshDevices();
        }
    }

    private void handlePostAuthSnapshot(SyncUiSnapshot snapshot) {
        Platform.runLater(() -> {
            applySnapshot(snapshot);
            if (snapshot != null && snapshot.strategyRequired()) {
                openStrategyDialog(snapshot);
            }
        });
    }

    private void openStrategyDialog(SyncUiSnapshot snapshot) {
        Dialog<AccountLinkStrategy> dialog = new Dialog<>();
        dialog.setTitle("Стратегия первого связывания");
        dialog.setHeaderText("Выберите, как связывать локальный профиль с облачным аккаунтом");
        if (ownerSupplier.get() != null) {
            dialog.initOwner(ownerSupplier.get());
        }
        DialogPane pane = dialog.getDialogPane();
        applyDialogStyles(pane);
        ButtonType applyType = new ButtonType("Продолжить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().addAll(applyType, cancelType);

        ToggleGroup group = new ToggleGroup();
        VBox content = new VBox(12);
        content.setPadding(new Insets(10, 0, 0, 0));

        LocalSyncProfileSummary summary = snapshot == null ? new LocalSyncProfileSummary(0, 0, 0, 0, 0, 0, 0) : snapshot.localSummary();
        Label preview = new Label(buildLinkPreviewText(summary, snapshot == null ? 0 : snapshot.remotePreviewChangeCount()));
        preview.getStyleClass().add("settings-muted-text");
        preview.setWrapText(true);
        content.getChildren().add(preview);

        AccountLinkStrategy recommended = recommendStrategy(summary, snapshot == null ? 0 : snapshot.remotePreviewChangeCount());
        for (AccountLinkStrategy strategy : AccountLinkStrategy.values()) {
            RadioButton option = new RadioButton(strategy.displayLabel());
            option.setToggleGroup(group);
            option.setUserData(strategy);
            if (strategy == (snapshot == null ? null : snapshot.selectedStrategy())
                    || (snapshot != null && snapshot.selectedStrategy() == null && strategy == recommended)) {
                option.setSelected(true);
            }

            Label description = new Label(strategyDescription(strategy));
            description.getStyleClass().add("settings-muted-text");
            description.setWrapText(true);

            Label note = new Label(strategy == recommended
                    ? "Рекомендуемый вариант для текущего состояния профиля."
                    : "Выбор сохраняется явно и не запускает скрытые опасные действия.");
            note.getStyleClass().add("settings-note-text");
            note.setWrapText(true);
            description.setMouseTransparent(true);
            note.setMouseTransparent(true);

            VBox optionBox = new VBox(4, option, description, note);
            optionBox.getStyleClass().addAll("settings-plugin-box", "settings-strategy-option");
            optionBox.setCursor(Cursor.HAND);
            optionBox.setOnMouseClicked(event -> option.setSelected(true));
            bindSelectableStrategyOption(optionBox, option);
            content.getChildren().add(optionBox);
        }

        Label guardrail = new Label(
                "Важно: сейчас доступно безопасное первичное связывание. Полное аккуратное объединение и применение входящих данных будет добавлено следующим этапом.");
        guardrail.getStyleClass().add("settings-note-text");
        guardrail.setWrapText(true);
        content.getChildren().add(guardrail);

        pane.setContent(content);
        Button applyButton = (Button) pane.lookupButton(applyType);
        applyButton.setDisable(group.getSelectedToggle() == null);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
                applyButton.setDisable(newToggle == null));

        dialog.setResultConverter(buttonType -> {
            if (!applyType.equals(buttonType) || group.getSelectedToggle() == null) {
                return null;
            }
            Object userData = group.getSelectedToggle().getUserData();
            return userData instanceof AccountLinkStrategy strategy ? strategy : null;
        });

        dialog.showAndWait().ifPresent(strategy -> syncFacade.applyAccountLinkStrategy(strategy));
    }

    private void applySnapshot(SyncUiSnapshot snapshot) {
        SyncUiSnapshot safeSnapshot = snapshot == null ? syncFacade.snapshot() : snapshot;
        if (!baseUrlField.isFocused()) {
            baseUrlField.setText(safeSnapshot.baseUrl());
        }
        updateStatusIcon(safeSnapshot.status());
        statusTitleLabel.setText(safeSnapshot.statusMessage());
        statusDetailLabel.setText(safeSnapshot.detailMessage());

        String accountText = safeSnapshot.authenticated()
                ? "Аккаунт: " + firstNonBlank(safeSnapshot.displayName(), safeSnapshot.accountEmail())
                : safeSnapshot.accountEmail().isBlank()
                        ? "Аккаунт: не подключен"
                        : "Последний аккаунт: " + firstNonBlank(safeSnapshot.displayName(), safeSnapshot.accountEmail());
        accountSummaryLabel.setText(accountText);
        strategySummaryLabel.setText("Стратегия: " + resolveStrategyLabel(safeSnapshot));
        lastSyncLabel.setText("Последняя синхронизация: " + formatTimestamp(safeSnapshot.lastSyncAt()));
        rolloutSummaryLabel.setText("Бета-режим: " + firstNonBlank(safeSnapshot.rolloutMessage(), "—"));
        diagnosticsSummaryLabel.setText("Диагностика: " + firstNonBlank(safeSnapshot.diagnosticsMessage(), "—"));
        errorSummaryLabel.setText("Последняя ошибка: " + firstNonBlank(safeSnapshot.lastErrorSummary(), "—"));
        renderHealthTimeline(syncFacade.recentHealthEvents());

        signedOutBox.setVisible(!safeSnapshot.authenticated());
        signedOutBox.setManaged(!safeSnapshot.authenticated());
        signedInBox.setVisible(safeSnapshot.authenticated());
        signedInBox.setManaged(safeSnapshot.authenticated());
        if (!safeSnapshot.authenticated() && !loginEmailField.isFocused() && !safeSnapshot.accountEmail().isBlank()) {
            loginEmailField.setText(safeSnapshot.accountEmail());
        }

        boolean busy = safeSnapshot.status() == SyncUiStatus.SYNCING;
        saveBaseUrlButton.setDisable(busy);
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        syncNowButton.setDisable(busy || !safeSnapshot.authenticated() || safeSnapshot.strategyRequired());
        chooseStrategyButton.setDisable(busy || !safeSnapshot.authenticated());
        logoutButton.setDisable(busy || !safeSnapshot.authenticated());
        forceBootstrapButton.setDisable(busy || !safeSnapshot.authenticated() || safeSnapshot.strategyRequired());
        clearDiagnosticsButton.setDisable(busy);
        copyDebugSummaryButton.setDisable(false);
        exportDiagnosticsBundleButton.setDisable(false);
        refreshDevicesButton.setDisable(busy || !safeSnapshot.authenticated());
        disconnectLocalSessionButton.setDisable(busy || !safeSnapshot.authenticated());
        prepareReauthButton.setDisable(busy || safeSnapshot.authenticated());
        copyHealthSummaryButton.setDisable(false);
        chooseStrategyButton.setText(safeSnapshot.selectedStrategy() == null ? "Выбрать стратегию" : "Изменить стратегию");
        updateDeviceCardButtonsDisabled(busy || !safeSnapshot.authenticated());

        if (!safeSnapshot.authenticated()) {
            devicesListBox.getChildren().clear();
            devicesSummaryLabel.setText(safeSnapshot.accountEmail().isBlank()
                    ? "Сначала войдите в аккаунт, чтобы увидеть список устройств."
                    : "Последняя известная привязка: " + safeSnapshot.accountEmail()
                            + ". Можно подготовить повторный вход без смены base URL.");
        }

        String warning = buildWarningText(safeSnapshot);
        boolean showWarning = !warning.isBlank();
        warningLabel.setText(warning);
        warningBox.setVisible(showWarning);
        warningBox.setManaged(showWarning);
    }

    private void updateStatusIcon(SyncUiStatus status) {
        statusIcon.getStyleClass().removeAll(
                "settings-status-icon-neutral",
                "settings-status-icon-success",
                "settings-status-icon-error",
                "settings-status-icon-checking");
        if (status == null) {
            statusIcon.setIconCode(MaterialDesignC.CIRCLE_OUTLINE);
            statusIcon.getStyleClass().add("settings-status-icon-neutral");
            return;
        }
        switch (status) {
            case SIGNED_OUT -> {
                statusIcon.setIconCode(MaterialDesignL.LOGOUT);
                statusIcon.getStyleClass().add("settings-status-icon-neutral");
            }
            case SYNCING -> {
                statusIcon.setIconCode(MaterialDesignS.SYNC);
                statusIcon.getStyleClass().add("settings-status-icon-checking");
            }
            case SYNCED -> {
                statusIcon.setIconCode(MaterialDesignC.CHECK_CIRCLE);
                statusIcon.getStyleClass().add("settings-status-icon-success");
            }
            case CONFLICT -> {
                statusIcon.setIconCode(MaterialDesignA.ALERT_CIRCLE);
                statusIcon.getStyleClass().add("settings-status-icon-error");
            }
            case OFFLINE -> {
                statusIcon.setIconCode(MaterialDesignL.LAN_DISCONNECT);
                statusIcon.getStyleClass().add("settings-status-icon-error");
            }
        }
    }

    private SyncUiSnapshot snapshot() {
        return syncFacade.snapshot();
    }

    private boolean validateBaseUrl() {
        if (!normalizedBaseUrl().isBlank()) {
            return true;
        }
        UiErrorNotifier.showWarning(ownerSupplier.get(), darkThemeSupplier.getAsBoolean(),
                "Облачная синхронизация", "Сначала сохраните адрес сервера синхронизации.");
        return false;
    }

    private String normalizedBaseUrl() {
        return CloudSyncUrlSupport.normalizeBaseUrl(safe(baseUrlField.getText()));
    }

    private String resolveStrategyLabel(SyncUiSnapshot snapshot) {
        if (snapshot == null || snapshot.selectedStrategy() == null) {
            return "не выбрана";
        }
        return snapshot.selectedStrategy().displayLabel();
    }

    private String buildWarningText(SyncUiSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.strategyRequired()) {
            return "Нужен явный выбор стратегии первого связывания. Локальные данные не будут молча смешаны с облаком.";
        }
        if (snapshot.status() == SyncUiStatus.CONFLICT) {
            return firstNonBlank(snapshot.detailMessage(), snapshot.lastErrorSummary());
        }
        if (snapshot.status() == SyncUiStatus.OFFLINE) {
            return "Сервер синхронизации недоступен. Локальный режим продолжает работать, а синхронизацию можно повторить позже.";
        }
        return "";
    }

    private String buildLinkPreviewText(LocalSyncProfileSummary summary, int remotePreviewChangeCount) {
        int localItems = summary == null ? 0 : summary.trackedEntityCount();
        int outboxItems = summary == null ? 0 : summary.pendingOutboxCount();
        String remoteText = remotePreviewChangeCount > 0
                ? remotePreviewChangeCount + " удалённых изменений уже обнаружены"
                : "удалённые изменения ещё не подтверждены";
        return "Локально: " + localItems + " записей, ожидают отправки: " + outboxItems + ". В облаке: " + remoteText + ".";
    }

    private AccountLinkStrategy recommendStrategy(LocalSyncProfileSummary summary, int remotePreviewChangeCount) {
        boolean localEmpty = summary == null || summary.isEmpty();
        if (localEmpty) {
            return AccountLinkStrategy.REPLACE_LOCAL;
        }
        if (remotePreviewChangeCount <= 0) {
            return AccountLinkStrategy.UPLOAD_LOCAL;
        }
        return AccountLinkStrategy.MERGE;
    }

    private String strategyDescription(AccountLinkStrategy strategy) {
        return switch (Objects.requireNonNull(strategy, "strategy")) {
            case UPLOAD_LOCAL ->
                    "Используйте локальный профиль как основную стартовую точку. Подходит, если на этом устройстве уже есть актуальные рабочие данные.";
            case REPLACE_LOCAL ->
                    "Считать облако главным источником после связывания. Подходит, если локальный профиль пустой, временный или тестовый.";
            case MERGE ->
                    "Сохранить намерение на осторожное объединение двух наборов данных. Полноценное объединение и применение изменений будет включено следующим этапом.";
        };
    }

    private String formatTimestamp(String rawTimestamp) {
        String safeTimestamp = safe(rawTimestamp);
        if (safeTimestamp.isBlank()) {
            return "—";
        }
        try {
            return UI_TIME_FORMAT.format(Instant.parse(safeTimestamp).atZone(ZoneId.systemDefault()));
        } catch (Exception ignored) {
            return safeTimestamp;
        }
    }

    private void applyDialogStyles(DialogPane pane) {
        if (pane == null) {
            return;
        }
        addStylesheetIfPresent(pane, "/styles/app.css");
        if (darkThemeSupplier.getAsBoolean()) {
            addStylesheetIfPresent(pane, "/styles/dark-theme.css");
        }
        if (!pane.getStyleClass().contains("styled-alert")) {
            pane.getStyleClass().add("styled-alert");
        }
    }

    private void bindSelectableStrategyOption(Node optionBox, RadioButton option) {
        optionBox.pseudoClassStateChanged(SELECTED_OPTION, option.isSelected());
        option.selectedProperty().addListener((obs, wasSelected, isSelected) ->
                optionBox.pseudoClassStateChanged(SELECTED_OPTION, isSelected));
    }

    private void addStylesheetIfPresent(DialogPane pane, String path) {
        URL resource = CloudSyncSettingsSection.class.getResource(path);
        if (resource != null && !pane.getStylesheets().contains(resource.toExternalForm())) {
            pane.getStylesheets().add(resource.toExternalForm());
        }
    }

    private void renderLinkedDevices(List<SyncPayloads.DeviceListItemResponse> devices, String summaryOverride) {
        List<SyncPayloads.DeviceListItemResponse> safeDevices = devices == null ? List.of() : List.copyOf(devices);
        devicesListBox.getChildren().clear();
        if (summaryOverride != null && !summaryOverride.isBlank()) {
            devicesSummaryLabel.setText(summaryOverride);
        } else if (safeDevices.isEmpty()) {
            devicesSummaryLabel.setText("У текущего аккаунта пока нет активных привязанных устройств.");
        } else {
            devicesSummaryLabel.setText("Найдено устройств: " + safeDevices.size() + ".");
        }
        for (SyncPayloads.DeviceListItemResponse device : safeDevices) {
            devicesListBox.getChildren().add(buildDeviceCard(device));
        }
        updateDeviceCardButtonsDisabled(snapshot().status() == SyncUiStatus.SYNCING || !snapshot().authenticated());
    }

    private VBox buildDeviceCard(SyncPayloads.DeviceListItemResponse device) {
        String title = safe(device.device_label()).isBlank()
                ? "Устройство " + safe(device.id())
                : safe(device.device_label());
        if (device.is_current_device()) {
            title += " (это устройство)";
        }
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-field-label");

        StringBuilder meta = new StringBuilder();
        meta.append("Платформа: ").append(firstNonBlank(device.platform(), "—"));
        meta.append("  •  Версия: ").append(firstNonBlank(device.app_version(), "—"));
        meta.append("  •  Зарегистрировано: ").append(formatTimestamp(device.registered_at()));
        meta.append("  •  Последняя активность: ").append(formatTimestamp(device.last_seen_at()));
        Label metaLabel = new Label(meta.toString());
        metaLabel.getStyleClass().add("settings-muted-text");
        metaLabel.setWrapText(true);

        String statusText = safe(device.revoked_at()).isBlank()
                ? "Статус: активно, refresh-сессий=" + Math.max(0, device.active_refresh_session_count())
                : "Статус: отозвано " + formatTimestamp(device.revoked_at())
                        + ", активных refresh-сессий=" + Math.max(0, device.active_refresh_session_count());
        Label statusLabel = new Label(statusText);
        statusLabel.getStyleClass().add("settings-note-text");
        statusLabel.setWrapText(true);

        Label sessionRoleLabel = new Label(device.is_current_device()
                ? "Роль: текущее устройство этого desktop-клиента."
                : "Роль: другое устройство, привязанное к тому же аккаунту.");
        sessionRoleLabel.getStyleClass().add("settings-muted-text");
        sessionRoleLabel.setWrapText(true);

        Button revokeButton = new Button(device.is_current_device()
                ? "Отозвать это устройство"
                : "Отозвать устройство");
        revokeButton.getStyleClass().add("settings-action-btn");
        revokeButton.getProperties().put("device-action-button", Boolean.TRUE);
        revokeButton.getProperties().put("device-revoked", !safe(device.revoked_at()).isBlank());
        revokeButton.setDisable(!safe(device.revoked_at()).isBlank());
        revokeButton.setOnAction(event -> handleRevokeDevice(device));

        VBox card = new VBox(8, titleLabel, metaLabel, statusLabel, sessionRoleLabel, revokeButton);
        card.getStyleClass().add("settings-plugin-box");
        return card;
    }

    private void handleRevokeDevice(SyncPayloads.DeviceListItemResponse device) {
        if (device == null || safe(device.id()).isBlank()) {
            return;
        }
        String question = device.is_current_device()
                ? "Отозвать текущее устройство? После этого потребуется повторный вход."
                : "Отозвать выбранное устройство и закрыть его refresh-сессии?";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, question, ButtonType.YES, ButtonType.NO);
        applyDialogStyles(confirm.getDialogPane());
        if (ownerSupplier.get() != null) {
            confirm.initOwner(ownerSupplier.get());
        }
        confirm.setHeaderText(null);
        confirm.setTitle("Облачная синхронизация");
        confirm.getButtonTypes().setAll(
                new ButtonType("Да", ButtonBar.ButtonData.YES),
                new ButtonType("Нет", ButtonBar.ButtonData.NO));
        confirm.showAndWait()
                .filter(buttonType -> buttonType.getButtonData() == ButtonBar.ButtonData.YES)
                .ifPresent(ignored -> syncFacade.revokeLinkedDevice(device.id(), device.is_current_device())
                        .thenAccept(AsyncContext.withMdcConsumer(updatedSnapshot -> Platform.runLater(() -> {
                            applySnapshot(updatedSnapshot);
                            if (updatedSnapshot.authenticated()) {
                                handleRefreshDevices();
                            } else {
                                devicesListBox.getChildren().clear();
                                devicesSummaryLabel.setText("Текущее устройство отозвано. Для нового списка устройств нужен повторный вход.");
                            }
                        })))
                        .exceptionally(AsyncContext.withMdcFunction(error -> {
                            Throwable cause = AsyncContext.unwrap(error);
                            Platform.runLater(() -> UiErrorNotifier.showWarning(
                                    ownerSupplier.get(),
                                    darkThemeSupplier.getAsBoolean(),
                                    "Облачная синхронизация",
                                    cause == null || safe(cause.getMessage()).isBlank()
                                            ? "Не удалось отозвать устройство."
                                            : safe(cause.getMessage())));
                            return null;
                        })));
    }

    private void updateDeviceCardButtonsDisabled(boolean disabled) {
        for (Node child : devicesListBox.getChildren()) {
            if (child instanceof VBox card) {
                for (Node cardChild : card.getChildren()) {
                    if (cardChild instanceof Button button
                            && Boolean.TRUE.equals(button.getProperties().get("device-action-button"))) {
                        boolean revoked = Boolean.TRUE.equals(button.getProperties().get("device-revoked"));
                        button.setDisable(disabled || revoked);
                    }
                }
            }
        }
    }

    private void renderHealthTimeline(List<SyncHealthEvent> events) {
        List<SyncHealthEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
        healthTimelineBox.getChildren().clear();
        if (safeEvents.isEmpty()) {
            healthSummaryLabel.setText("История sync-циклов пока пуста. После первого sync или recovery action здесь появятся записи.");
            return;
        }
        SyncHealthEvent latest = safeEvents.getFirst();
        String latestFlags = latest.deferred()
                ? " Сходимость отложена."
                : latest.failure() ? " Есть ошибка." : " Сходимость достигнута или действие завершено.";
        healthSummaryLabel.setText("Последнее событие: " + latest.title() + "." + latestFlags);
        for (SyncHealthEvent event : safeEvents) {
            healthTimelineBox.getChildren().add(buildHealthEventCard(event));
        }
    }

    private VBox buildHealthEventCard(SyncHealthEvent event) {
        Label titleLabel = new Label(formatTimestamp(event.occurredAt()) + " • " + firstNonBlank(event.title(), "Sync event"));
        titleLabel.getStyleClass().add("settings-field-label");

        Label categoryLabel = new Label("Категория: " + firstNonBlank(event.category(), "—"));
        categoryLabel.getStyleClass().add("settings-muted-text");
        categoryLabel.setWrapText(true);

        String state = event.failure()
                ? "Состояние: ошибка"
                : event.deferred() ? "Состояние: convergence deferred" : "Состояние: завершено";
        Label stateLabel = new Label(state);
        stateLabel.getStyleClass().add(event.failure() ? "settings-note-text" : "settings-muted-text");
        stateLabel.setWrapText(true);

        Label detailLabel = new Label(firstNonBlank(event.detail(), "—"));
        detailLabel.getStyleClass().add("settings-note-text");
        detailLabel.setWrapText(true);

        VBox card = new VBox(6, titleLabel, categoryLabel, stateLabel, detailLabel);
        HBox actions = buildHealthEventActions(event);
        if (actions != null) {
            card.getChildren().add(actions);
        }
        card.getStyleClass().add("settings-plugin-box");
        return card;
    }

    private HBox buildHealthEventActions(SyncHealthEvent event) {
        SyncUiSnapshot current = snapshot();
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        if (event.deferred() && current.authenticated() && !current.strategyRequired()) {
            Button retryCycleButton = new Button("Повторить цикл");
            retryCycleButton.getStyleClass().add("settings-action-btn");
            retryCycleButton.setOnAction(ignored -> handleManualSync());
            actions.getChildren().add(retryCycleButton);

            Button retryBootstrapButton = new Button("Повторить bootstrap");
            retryBootstrapButton.getStyleClass().add("settings-action-btn");
            retryBootstrapButton.setOnAction(ignored -> handleForceBootstrap());
            actions.getChildren().add(retryBootstrapButton);
        } else if (event.failure()) {
            if (current.authenticated() && !current.strategyRequired()) {
                Button retryCycleButton = new Button("Повторить цикл");
                retryCycleButton.getStyleClass().add("settings-action-btn");
                retryCycleButton.setOnAction(ignored -> handleManualSync());
                actions.getChildren().add(retryCycleButton);
            }
            if (current.authenticated()) {
                Button bootstrapButton = new Button("Повторить bootstrap");
                bootstrapButton.getStyleClass().add("settings-action-btn");
                bootstrapButton.setOnAction(ignored -> handleForceBootstrap());
                actions.getChildren().add(bootstrapButton);
            } else {
                Button reauthButton = new Button("Подготовить повторный вход");
                reauthButton.getStyleClass().add("settings-action-btn");
                reauthButton.setOnAction(ignored -> handlePrepareReauthentication());
                actions.getChildren().add(reauthButton);
            }
        } else if (!current.authenticated() && !safe(current.accountEmail()).isBlank()) {
            Button reauthButton = new Button("Подготовить повторный вход");
            reauthButton.getStyleClass().add("settings-action-btn");
            reauthButton.setOnAction(ignored -> handlePrepareReauthentication());
            actions.getChildren().add(reauthButton);
        }

        if (actions.getChildren().isEmpty()) {
            return null;
        }
        return actions;
    }

    private void focusReauthFields(SyncUiSnapshot snapshot) {
        if (snapshot == null || snapshot.authenticated()) {
            return;
        }
        Platform.runLater(() -> {
            if (!safe(snapshot.accountEmail()).isBlank()) {
                loginEmailField.setText(snapshot.accountEmail());
                loginPasswordField.requestFocus();
            } else {
                loginEmailField.requestFocus();
            }
        });
    }

    private String firstNonBlank(String primary, String fallback) {
        String safePrimary = safe(primary);
        if (!safePrimary.isBlank()) {
            return safePrimary;
        }
        return safe(fallback);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private File chooseDiagnosticsBundleFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт diagnostics bundle");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Text (*.txt)",
                "*.txt"));
        chooser.setInitialFileName("cloud-sync-diagnostics-" + Instant.now().toString().replace(':', '-') + ".txt");
        return chooser.showSaveDialog(ownerSupplier.get());
    }
}
