package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Welcome dialog shown on first launch to configure AI server.
 */
public class WelcomeDialog {

    private static final String FIRST_LAUNCH_KEY = "app.first.launch.done";
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    /**
     * Check if this is the first launch and show welcome dialog if needed.
     */
    public static void showIfFirstLaunch() {
        String firstLaunchDone = ConfigManager.getProperty(FIRST_LAUNCH_KEY);
        if (!"true".equals(firstLaunchDone)) {
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
        dialogPane.setPrefWidth(550);
        dialogPane.setMinHeight(620);

        VBox content = new VBox(18);
        content.setPadding(new Insets(25));
        content.setAlignment(Pos.TOP_CENTER);
        content.setMaxWidth(500);

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
        Label title = new Label("Добро пожаловать в НейроФлоу!");
        title.getStyleClass().add("welcome-title");

        Label subtitle = new Label("Умный ИИ-планировщик задач");
        subtitle.getStyleClass().add("welcome-subtitle");

        // Description
        VBox descBox = new VBox(10);
        descBox.setAlignment(Pos.CENTER_LEFT);
        descBox.getStyleClass().add("welcome-desc-box");
        descBox.setPadding(new Insets(15));

        Label descTitle = new Label("Настройка ИИ-функций");
        descTitle.getStyleClass().add("welcome-section-title");

        Label descText = new Label(
            "Для работы ИИ-функций (анализ задач, авто-заполнение, чат-ассистент) " +
            "необходимо подключение к серверу Ollama.\n\n" +
            "Если сервер на другом компьютере — узнайте IP-адрес у администратора."
        );
        descText.getStyleClass().add("welcome-desc-text");
        descText.setWrapText(true);
        descText.setMaxWidth(450);

        descBox.getChildren().addAll(descTitle, descText);
        descBox.setMaxWidth(480);

        // Server input section
        VBox serverBox = new VBox(12);
        serverBox.setAlignment(Pos.CENTER_LEFT);

        Label serverLabel = new Label("Адрес сервера:");
        serverLabel.getStyleClass().add("welcome-field-label");

        TextField serverField = new TextField();
        serverField.setPromptText("http://192.168.1.100:11434/api/chat");
        serverField.getStyleClass().add("welcome-text-field");
        
        // Pre-fill with current value or default
        String currentUrl = ConfigManager.getProperty("api.url");
        if (currentUrl != null && !currentUrl.isEmpty()) {
            serverField.setText(currentUrl);
        } else {
            serverField.setText("http://localhost:11434/api/chat");
        }

        // Status indicator
        HBox statusBox = new HBox(10);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        
        FontIcon statusIcon = FontIcon.of(MaterialDesignC.CIRCLE_OUTLINE, 16);
        statusIcon.getStyleClass().add("welcome-status-icon-neutral");
        
        Label statusLabel = new Label("Введите адрес и проверьте соединение");
        statusLabel.getStyleClass().add("welcome-status-text");
        
        statusBox.getChildren().addAll(statusIcon, statusLabel);

        // Check button
        Button checkBtn = new Button("Проверить соединение");
        checkBtn.getStyleClass().add("welcome-check-btn");
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CONNECTION, 16);
        checkIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        checkBtn.setGraphic(checkIcon);
        
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(20, 20);
        progress.setVisible(false);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.getChildren().addAll(checkBtn, progress);

        checkBtn.setOnAction(e -> {
            String url = serverField.getText().trim();
            if (url.isEmpty()) {
                updateStatus(statusIcon, statusLabel, "error", "Введите адрес сервера");
                return;
            }
            
            checkBtn.setDisable(true);
            progress.setVisible(true);
            updateStatus(statusIcon, statusLabel, "checking", "Проверка соединения...");
            
            checkConnection(url).thenAccept(success -> Platform.runLater(() -> {
                checkBtn.setDisable(false);
                progress.setVisible(false);
                
                if (success) {
                    updateStatus(statusIcon, statusLabel, "success", "Соединение установлено!");
                } else {
                    updateStatus(statusIcon, statusLabel, "error", "Не удалось подключиться к серверу");
                }
            }));
        });

        serverBox.getChildren().addAll(serverLabel, serverField, btnBox, statusBox);

        // Info note
        HBox noteBox = new HBox(10);
        noteBox.setAlignment(Pos.CENTER_LEFT);
        noteBox.getStyleClass().add("welcome-note-box");
        noteBox.setPadding(new Insets(12));
        
        FontIcon noteIcon = FontIcon.of(MaterialDesignI.INFORMATION_OUTLINE, 18);
        noteIcon.getStyleClass().add("welcome-note-icon");
        
        Label noteText = new Label("Настройки сервера можно изменить позже в разделе «Настройки»");
        noteText.getStyleClass().add("welcome-note-text");
        noteText.setWrapText(true);
        
        noteBox.getChildren().addAll(noteIcon, noteText);

        content.getChildren().addAll(title, subtitle, descBox, serverBox, noteBox);

        dialogPane.setContent(content);

        // Buttons
        ButtonType saveBtn = new ButtonType("Сохранить и начать", ButtonBar.ButtonData.OK_DONE);
        ButtonType skipBtn = new ButtonType("Пропустить", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(saveBtn, skipBtn);

        // Style buttons
        Button saveButton = (Button) dialogPane.lookupButton(saveBtn);
        saveButton.getStyleClass().add("welcome-save-btn");
        
        Button skipButton = (Button) dialogPane.lookupButton(skipBtn);
        skipButton.getStyleClass().add("welcome-skip-btn");

        dialog.showAndWait().ifPresent(result -> {
            String url = serverField.getText().trim();
            
            if (result == saveBtn && !url.isEmpty()) {
                ConfigManager.setProperty("api.url", url);
                showResultDialog(true, url);
            } else {
                showResultDialog(false, null);
            }
            
            // Mark first launch as done
            ConfigManager.setProperty(FIRST_LAUNCH_KEY, "true");
        });
    }

    private CompletableFuture<Boolean> checkConnection(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Try to connect to base URL (without /api/chat)
                String baseUrl = url.replace("/api/chat", "").replace("/api/generate", "");
                if (!baseUrl.endsWith("/")) {
                    baseUrl = baseUrl + "/";
                }
                // Remove trailing slash for check
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
                    
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        });
    }

    private void updateStatus(FontIcon icon, Label label, String status, String text) {
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

    private void showResultDialog(boolean saved, String url) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(saved ? "Настройка завершена" : "ИИ-функции недоступны");
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
        if (saved) {
            iconPane.setStyle("-fx-background-color: " + (isDark ? "rgba(166,227,161,0.15)" : "rgba(64,160,43,0.1)") + "; -fx-background-radius: 50%;");
            icon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 32);
            icon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#a6e3a1" : "#40a02b"));
        } else {
            iconPane.setStyle("-fx-background-color: " + (isDark ? "rgba(249,226,175,0.15)" : "rgba(223,142,29,0.1)") + "; -fx-background-radius: 50%;");
            icon = FontIcon.of(MaterialDesignA.ALERT_CIRCLE_OUTLINE, 32);
            icon.setIconColor(javafx.scene.paint.Color.web(isDark ? "#f9e2af" : "#df8e1d"));
        }
        iconPane.getChildren().add(icon);
        
        // Title
        Label titleLabel = new Label(saved ? "Готово!" : "Пропущено");
        titleLabel.getStyleClass().add("welcome-title");
        titleLabel.setStyle("-fx-font-size: 20px;");
        
        // Message
        Label msgLabel = new Label();
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(380);
        msgLabel.setAlignment(Pos.CENTER);
        msgLabel.setStyle("-fx-text-alignment: center;");
        msgLabel.getStyleClass().add("welcome-result-text");
        
        if (saved) {
            msgLabel.setText(
                "Сервер ИИ настроен:\n" + url + "\n\n" +
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
        okBtn.setText("Понятно");
        okBtn.getStyleClass().add("welcome-save-btn");
        
        dialog.showAndWait();
        
        // Перезапуск приложения для применения настроек
        restartApplication();
    }
    
    private void restartApplication() {
        Platform.runLater(() -> {
            try {
                // Получаем путь к текущему JAR или классам
                String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
                java.io.File currentJar = new java.io.File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
                
                // Проверяем, запущено ли из JAR
                if (currentJar.getName().endsWith(".jar")) {
                    // Запуск из JAR
                    ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", currentJar.getPath());
                    builder.start();
                } else {
                    // Запуск из IDE - перезапускаем через main класс
                    String className = "com.example.neuroflowplanner.NeuroFlowApp";
                    String classpath = System.getProperty("java.class.path");
                    String modulePath = System.getProperty("jdk.module.path");
                    
                    java.util.List<String> command = new java.util.ArrayList<>();
                    command.add(javaBin);
                    
                    if (modulePath != null && !modulePath.isEmpty()) {
                        command.add("--module-path");
                        command.add(modulePath);
                        command.add("--add-modules");
                        command.add("ALL-MODULE-PATH");
                    }
                    
                    command.add("-cp");
                    command.add(classpath);
                    command.add(className);
                    
                    ProcessBuilder builder = new ProcessBuilder(command);
                    builder.start();
                }
                
                // Закрываем текущее приложение
                Platform.exit();
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Не удалось перезапустить приложение: " + e.getMessage());
                // Если перезапуск не удался, просто продолжаем работу
            }
        });
    }
}
