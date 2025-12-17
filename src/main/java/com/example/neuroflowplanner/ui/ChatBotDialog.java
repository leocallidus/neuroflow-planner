package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.ChatBotService;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;

/**
 * Inline chat bot view.
 */
public class ChatBotDialog implements InlineView {

    private final VBox messagesBox = new VBox(16);
    private final TextField inputField = new TextField();
    private final ChatBotService chatService = new ChatBotService();
    private final ScrollPane scrollPane;
    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();

    private ChatBotDialog() {
        root = new VBox(0);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(320, 400);
        root.getStyleClass().add("chat-root");

        // --- Header Wrapper (StackPane for background) ---
        StackPane headerContainer = new StackPane();
        headerContainer.getStyleClass().add("chat-header-panel");
        headerContainer.setMaxWidth(Double.MAX_VALUE);

        // --- Header Content (BorderPane) ---
        BorderPane headerContent = new BorderPane();
        headerContent.setPadding(new Insets(16, 20, 16, 20));

        // Left side: Avatar + Title
        HBox leftContent = new HBox(12);
        leftContent.setAlignment(Pos.CENTER_LEFT);

        ImageView botImage = new ImageView(new Image(getClass().getResourceAsStream("/com/example/neuroflowplanner/images/chatbot.png")));
        botImage.setFitHeight(40);
        botImage.setFitWidth(40);
        botImage.setPreserveRatio(true);
        
        // Add a subtle effect to avatar
        StackPane avatarPane = new StackPane(botImage);
        avatarPane.getStyleClass().add("chat-avatar-container");

        VBox headerText = new VBox(2);
        Label title = new Label("ИИ-Ассистент");
        title.getStyleClass().add("chat-title");
        Label status = new Label("● Онлайн");
        status.getStyleClass().add("chat-status");
        headerText.getChildren().addAll(title, status);

        leftContent.getChildren().addAll(avatarPane, headerText);
        headerContent.setLeft(leftContent);

        // Right side: Close button
        Button closeBtn = new Button();
        FontIcon closeIcon = FontIcon.of(MaterialDesignC.CLOSE, 20);
        closeBtn.setGraphic(closeIcon);
        closeBtn.getStyleClass().add("chat-close-btn");
        closeBtn.setOnAction(e -> {
            if (closeAction != null) closeAction.run();
        });
        headerContent.setRight(closeBtn);

        headerContainer.getChildren().add(headerContent);
        root.getChildren().add(headerContainer);

        // --- Messages Area ---
        messagesBox.setPadding(new Insets(20));
        messagesBox.getStyleClass().add("chat-messages-box");

        scrollPane = new ScrollPane(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("chat-scroll-pane");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        new Thread(() -> {
            try { Thread.sleep(600); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> addBotMessage("Привет! Я ИИ-ассистент НейроФлоу. Готов помочь с планированием, декомпозицией задач и ответами на вопросы."));
        }).start();

        // --- Input Area ---
        HBox inputArea = new HBox(12);
        inputArea.setAlignment(Pos.CENTER);
        inputArea.setPadding(new Insets(15, 20, 20, 20));
        inputArea.getStyleClass().add("chat-input-area");

        inputField.setPromptText("Задайте вопрос или опишите задачу...");
        inputField.getStyleClass().add("chat-input-field");
        inputField.setOnAction(e -> sendMessage());
        inputField.setContextMenu(createRussianContextMenu(inputField));
        HBox.setHgrow(inputField, Priority.ALWAYS);

        Button sendBtn = new Button();
        FontIcon sendIcon = FontIcon.of(MaterialDesignS.SEND, 18);
        sendBtn.setGraphic(sendIcon);
        sendBtn.getStyleClass().add("chat-send-btn");
        sendBtn.setOnAction(e -> sendMessage());

        inputArea.getChildren().addAll(inputField, sendBtn);

        root.getChildren().addAll(scrollPane, inputArea);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }
    }

    public static InlineView inline() {
        return new ChatBotDialog();
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

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        addUserMessage(text);
        inputField.clear();

        HBox typing = createTypingIndicator();
        messagesBox.getChildren().add(typing);
        scrollToBottom();

        chatService.sendMessage(text).thenAccept(response ->
            Platform.runLater(() -> {
                messagesBox.getChildren().remove(typing);
                addBotMessage(response);
            })
        );
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

    private void addBotMessage(String text) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.TOP_LEFT);
        container.setPadding(new Insets(0, 20, 0, 0));

        ImageView avatar = new ImageView(new Image(getClass().getResourceAsStream("/com/example/neuroflowplanner/images/chatbot.png")));
        avatar.setFitHeight(32);
        avatar.setFitWidth(32);
        
        VBox msgBox = new VBox(4);
        msgBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(msgBox, Priority.ALWAYS);
        
        // Используем WebView для рендеринга Markdown
        WebView webView = new WebView();
        webView.getStyleClass().add("chat-webview");
        webView.setContextMenuEnabled(false);
        
        // Устанавливаем размеры
        webView.setPrefHeight(100);
        webView.setMinHeight(50);
        webView.setMaxHeight(400);
        
        // Конвертируем Markdown в HTML и загружаем
        String html = convertMarkdownToHtml(text);
        String fullHtml = getChatHtmlTemplate(html);
        webView.getEngine().loadContent(fullHtml);
        
        // Автоматическая подстройка высоты после загрузки
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                Platform.runLater(() -> {
                    try {
                        Object result = webView.getEngine().executeScript(
                            "Math.max(document.body.scrollHeight, document.body.offsetHeight, " +
                            "document.documentElement.scrollHeight, document.documentElement.offsetHeight)"
                        );
                        if (result instanceof Number) {
                            double height = ((Number) result).doubleValue() + 10;
                            webView.setPrefHeight(Math.min(Math.max(height, 50), 400));
                        }
                    } catch (Exception ignored) {}
                    scrollToBottom();
                });
            }
        });
        
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
                java.util.Map.of(DataFormat.PLAIN_TEXT, text)
            );
        });
        actions.getChildren().add(copyBtn);
        
        msgBox.getChildren().addAll(webView, actions);

        container.getChildren().addAll(avatar, msgBox);
        messagesBox.getChildren().add(container);
        scrollToBottom();
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
            <body>%s</body>
            </html>
            """, textColor, bgColor, headingColor, strongColor, codeBg, codeColor, codeBg, codeColor, listColor, content);
    }

    private HBox createTypingIndicator() {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);

        ImageView avatar = new ImageView(new Image(getClass().getResourceAsStream("/com/example/neuroflowplanner/images/chatbot.png")));
        avatar.setFitHeight(32);
        avatar.setFitWidth(32);

        Label dots = new Label("●  ●  ●");
        dots.getStyleClass().add("chat-typing-dots");

        container.getChildren().addAll(avatar, dots);
        return container;
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private ContextMenu createRussianContextMenu(TextField textField) {
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
}
