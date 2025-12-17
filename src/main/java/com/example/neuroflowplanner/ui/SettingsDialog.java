package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

/**
 * Inline settings view (theme toggle etc.).
 */
public class SettingsDialog implements InlineView {

    private static boolean isDarkTheme = ConfigManager.isDarkTheme();
    private static Scene mainScene = null;
    private static Runnable themeChangeCallback = null;

    private final VBox root;
    private final HBox themeToggle;
    private final StackPane toggleSwitch;
    private final Label themeLabel;
    private final Label themeDesc;
    private final FontIcon themeIcon;
    private Runnable closeAction;
    
    // AI Server settings
    private TextField serverField;
    private Label serverStatusLabel;
    private FontIcon serverStatusIcon;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .build();

    private SettingsDialog() {
        root = new VBox(0);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(320, 300);
        root.getStyleClass().add("settings-root");

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 10, 25));
        header.getStyleClass().add("settings-header-panel");

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

        Button closeBtn = new Button();
        FontIcon closeIcon = FontIcon.of(MaterialDesignC.CLOSE, 20);
        closeBtn.setGraphic(closeIcon);
        closeBtn.getStyleClass().add("settings-close-btn");
        closeBtn.setOnAction(e -> {
            if (closeAction != null) closeAction.run();
        });

        header.getChildren().addAll(iconPane, titleBox, spacer, closeBtn);

        VBox content = new VBox(20);
        content.getStyleClass().add("settings-content");

        themeToggle = new HBox(15);
        toggleSwitch = new StackPane();
        themeLabel = new Label();
        themeDesc = new Label();
        themeIcon = new FontIcon();

        VBox themeSection = createThemeSection();
        VBox serverSection = createServerSection();
        content.getChildren().addAll(themeSection, serverSection);

        root.getChildren().addAll(header, content);

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
        this.closeAction = closeAction;
    }

    @Override
    public String getTitle() {
        return "Настройки";
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
                // Уведомляем MainView о смене темы для обновления WebView
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

    private VBox createServerSection() {
        VBox section = new VBox(12);

        Label sectionTitle = new Label("ИИ-сервер");
        sectionTitle.getStyleClass().add("settings-section-title");

        // Server URL field
        VBox fieldBox = new VBox(8);
        
        Label fieldLabel = new Label("Адрес сервера Ollama:");
        fieldLabel.getStyleClass().add("settings-field-label");

        serverField = new TextField();
        serverField.setPromptText("http://192.168.1.100:11434/api/chat");
        serverField.getStyleClass().add("settings-text-field");
        
        String currentUrl = ConfigManager.getProperty("api.url");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            serverField.setText(currentUrl);
        }

        // Status row
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        serverStatusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 14);
        serverStatusIcon.getStyleClass().add("settings-status-icon-neutral");
        
        serverStatusLabel = new Label("Введите адрес и проверьте соединение");
        serverStatusLabel.getStyleClass().add("settings-status-text");
        
        statusBox.getChildren().addAll(serverStatusIcon, serverStatusLabel);

        // Buttons row
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button checkBtn = new Button("Проверить");
        checkBtn.getStyleClass().add("settings-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 14);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);

        Button saveBtn = new Button("Сохранить");
        saveBtn.getStyleClass().add("settings-save-btn");
        FontIcon saveIcon = FontIcon.of(MaterialDesignC.CONTENT_SAVE, 14);
        saveIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        saveBtn.setGraphic(saveIcon);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        btnBox.getChildren().addAll(checkBtn, saveBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = serverField.getText().trim();
            if (url.isEmpty()) {
                updateServerStatus("error", "Введите адрес сервера");
                return;
            }
            
            checkBtn.setDisable(true);
            saveBtn.setDisable(true);
            progress.setVisible(true);
            updateServerStatus("checking", "Проверка соединения...");
            
            checkConnection(url).thenAccept(success -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                saveBtn.setDisable(false);
                progress.setVisible(false);
                
                if (success) {
                    updateServerStatus("success", "Соединение установлено!");
                } else {
                    updateServerStatus("error", "Не удалось подключиться");
                }
            }));
        });

        saveBtn.setOnAction(e -> {
            String url = serverField.getText().trim();
            if (!url.isEmpty()) {
                ConfigManager.setProperty("api.url", url);
                updateServerStatus("success", "Сохранено! Перезапустите для применения.");
            }
        });

        fieldBox.getChildren().addAll(fieldLabel, serverField, btnBox, statusBox);

        // Info note
        HBox noteBox = new HBox(8);
        noteBox.setAlignment(Pos.CENTER_LEFT);
        noteBox.getStyleClass().add("settings-note-box");
        noteBox.setPadding(new Insets(10));
        
        FontIcon noteIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 16);
        noteIcon.getStyleClass().add("settings-note-icon");
        
        Label noteText = new Label("Для работы ИИ-функций требуется сервер Ollama");
        noteText.getStyleClass().add("settings-note-text");
        
        noteBox.getChildren().addAll(noteIcon, noteText);

        section.getChildren().addAll(sectionTitle, fieldBox, noteBox);
        return section;
    }

    private CompletableFuture<Boolean> checkConnection(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String baseUrl = url.replace("/api/chat", "").replace("/api/generate", "");
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void updateServerStatus(String status, String text) {
        serverStatusIcon.getStyleClass().removeAll("settings-status-icon-neutral", 
            "settings-status-icon-success", "settings-status-icon-error", "settings-status-icon-checking");
        
        switch (status) {
            case "success" -> {
                serverStatusIcon.setIconCode(MaterialDesignC.CHECK_CIRCLE);
                serverStatusIcon.getStyleClass().add("settings-status-icon-success");
            }
            case "error" -> {
                serverStatusIcon.setIconCode(MaterialDesignA.ALERT_CIRCLE);
                serverStatusIcon.getStyleClass().add("settings-status-icon-error");
            }
            case "checking" -> {
                serverStatusIcon.setIconCode(MaterialDesignS.SYNC);
                serverStatusIcon.getStyleClass().add("settings-status-icon-checking");
            }
            default -> {
                serverStatusIcon.setIconCode(MaterialDesignC.CIRCLE_OUTLINE);
                serverStatusIcon.getStyleClass().add("settings-status-icon-neutral");
            }
        }
        serverStatusLabel.setText(text);
    }
}
