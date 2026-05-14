package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.TaskScheduleFormatter;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.service.SmartCategorizationService;
import com.example.neuroflowplanner.service.SmartCategorizationService.CategorizedTask;
import com.example.neuroflowplanner.service.SmartCategorizationService.Category;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.*;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SmartCategorizationDialog implements InlineView {

    private final VBox root;
    private Runnable closeAction;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final SmartCategorizationService service = new SmartCategorizationService();
    private final Consumer<Task> onTaskSelect;
    private Button autoAssignButton;
    private ProgressBar autoAssignProgress;
    private Label autoAssignProgressLabel;
    private boolean aiAvailable = false;
    private boolean autoAssignRunning = false;
    private SmartCategorizationService.CategorizationJob activeJob;
    private Consumer<SmartCategorizationService.CategorizeProgress> progressListener;

    private SmartCategorizationDialog(List<Task> tasks, Consumer<Task> onTaskSelect) {
        this.onTaskSelect = onTaskSelect;
        root = new VBox(0);
        root.getStyleClass().add("category-root");
        
        // Асинхронно проверяем доступность ИИ с fallback таймером
        service.checkAIAvailabilityAsync()
            .thenAccept(available -> {
                aiAvailable = available;
                javafx.application.Platform.runLater(this::updateAIButtonState);
            })
            .exceptionally(ex -> {
                aiAvailable = false;
                javafx.application.Platform.runLater(this::updateAIButtonState);
                return null;
            });

        // Header
        HBox header = createHeader();
        root.getChildren().add(header);

        // Content
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("category-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 25, 25, 25));
        content.getStyleClass().add("category-content");

        // Categorize tasks
        Map<String, List<CategorizedTask>> categories = service.categorize(tasks);

        // Summary
        HBox summary = createSummary(categories, tasks.size());
        content.getChildren().add(summary);

        // Category sections
        for (Map.Entry<String, List<CategorizedTask>> entry : categories.entrySet()) {
            VBox section = createCategorySection(entry.getKey(), entry.getValue());
            content.getChildren().add(section);
        }

        scrollPane.setContent(content);
        root.getChildren().add(scrollPane);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(400, 350);

        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        resumeRunningJobIfAny();
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 25, 15, 25));
        header.getStyleClass().add("category-header-panel");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("category-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignS.SHAPE_OUTLINE, 24);
        icon.getStyleClass().add("category-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("Умная категоризация");
        title.getStyleClass().add("category-title");
        Label subtitle = new Label("Автоматическое распределение задач по категориям");
        subtitle.getStyleClass().add("category-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        return header;
    }

    private HBox createSummary(Map<String, List<CategorizedTask>> categories, int total) {
        HBox cards = new HBox(12);
        cards.setAlignment(Pos.CENTER_LEFT);
        cards.setSpacing(12);
        cards.setPadding(new Insets(0, 0, 8, 0));

        int categorized = categories.values().stream()
            .flatMap(List::stream)
            .filter(ct -> ct.confidence() > 0)
            .mapToInt(ct -> 1).sum();

        cards.getChildren().addAll(
            createMetricCard("Всего задач", String.valueOf(total), MaterialDesignF.FORMAT_LIST_BULLETED, "category-card-total"),
            createMetricCard("Категорий", String.valueOf(categories.size()), MaterialDesignS.SHAPE, "category-card-categories"),
            createMetricCard("Распознано", String.valueOf(categorized), MaterialDesignC.CHECK_CIRCLE, "category-card-recognized"),
            createMetricCard("Точность", categorized > 0 ? Math.round(categorized * 100.0 / total) + "%" : "—", MaterialDesignT.TARGET, "category-card-accuracy"),
            createAutoAssignCard()
        );

        return cards;
    }

    private VBox createMetricCard(String label, String value, Enum<?> iconEnum, String styleClass) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12));
        card.setPrefWidth(160);
        card.getStyleClass().addAll("category-metric-card", styleClass);

        FontIcon icon = FontIcon.of((org.kordamp.ikonli.Ikon) iconEnum, 24);
        icon.getStyleClass().add("category-card-icon");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("category-card-value");

        Label nameLabel = new Label(label);
        nameLabel.getStyleClass().add("category-card-label");

        card.getChildren().addAll(icon, valueLabel, nameLabel);
        return card;
    }

    private VBox createAutoAssignCard() {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16));
        card.setPrefWidth(140);
        card.setMinWidth(140);
        card.getStyleClass().addAll("category-metric-card", "category-auto-card");

        // Иконка в круге
        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("category-auto-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignR.ROBOT, 24);
        icon.getStyleClass().add("category-auto-icon");
        iconPane.getChildren().add(icon);

        // Заголовок
        Label title = new Label("ИИ");
        title.getStyleClass().add("category-card-value");

        // Подзаголовок
        Label subtitle = new Label("Авто-теги");
        subtitle.getStyleClass().add("category-card-label");

        // Кнопка
        autoAssignButton = new Button("Запустить");
        autoAssignButton.getStyleClass().add("category-auto-btn");
        autoAssignButton.setMaxWidth(Double.MAX_VALUE);
        autoAssignButton.setOnAction(e -> runAutoCategorization());

        // Прогресс
        autoAssignProgress = new ProgressBar(0);
        autoAssignProgress.getStyleClass().add("category-auto-progress");
        autoAssignProgress.setMaxWidth(Double.MAX_VALUE);
        autoAssignProgress.setVisible(false);
        autoAssignProgress.setManaged(false);

        autoAssignProgressLabel = new Label("");
        autoAssignProgressLabel.getStyleClass().add("category-auto-progress-label");
        autoAssignProgressLabel.setVisible(false);
        autoAssignProgressLabel.setManaged(false);
        
        // Изначально кнопка недоступна пока не проверим ИИ
        autoAssignButton.setDisable(true);
        autoAssignButton.setText("Проверка...");

        card.getChildren().addAll(iconPane, title, subtitle, autoAssignButton, autoAssignProgress, autoAssignProgressLabel);
        return card;
    }

    private void updateAIButtonState() {
        if (autoAssignButton != null) {
            if (autoAssignRunning) {
                return;
            }
            if (aiAvailable) {
                autoAssignButton.setDisable(false);
                autoAssignButton.setText("Запустить");
                autoAssignButton.getStyleClass().remove("category-auto-btn-disabled");
            } else {
                autoAssignButton.setDisable(true);
                autoAssignButton.setText("Недоступен");
                autoAssignButton.getStyleClass().add("category-auto-btn-disabled");
            }
        }
    }

    private void runAutoCategorization() {
        AsyncContext.ensureRequestId();
        SmartCategorizationService.CategorizationJob job = service.startCategorizationWithAI(null);
        attachToJob(job);
    }

    private void updateProgress(SmartCategorizationService.CategorizeProgress progress) {
        if (autoAssignProgress == null || autoAssignProgressLabel == null) {
            return;
        }
        if (progress.total() <= 0) {
            autoAssignProgress.setProgress(0);
            updateProgressLabel("Нет задач для обработки");
            return;
        }
        double pct = progress.processed() / (double) progress.total();
        autoAssignProgress.setProgress(pct);
        String etaText = formatEta(progress.etaMillis());
        updateProgressLabel(progress.processed() + "/" + progress.total() + " • " + etaText);
    }

    private void showAutoAssignProgress(boolean visible) {
        if (autoAssignProgress != null) {
            autoAssignProgress.setVisible(visible);
            autoAssignProgress.setManaged(visible);
        }
        if (autoAssignProgressLabel != null) {
            autoAssignProgressLabel.setVisible(visible);
            autoAssignProgressLabel.setManaged(visible);
        }
    }

    private void updateProgressLabel(String text) {
        if (autoAssignProgressLabel != null) {
            autoAssignProgressLabel.setText(text);
        }
    }

    private String formatEta(long etaMillis) {
        if (etaMillis < 0) {
            return "расчёт...";
        }
        long totalSeconds = Math.max(0, etaMillis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            long remMinutes = minutes % 60;
            return String.format("осталось ~%d:%02d:%02d", hours, remMinutes, seconds);
        }
        return String.format("осталось ~%d:%02d", minutes, seconds);
    }

    private void resumeRunningJobIfAny() {
        SmartCategorizationService.CategorizationJob job = service.getActiveCategorizationJob();
        if (job != null) {
            attachToJob(job);
        }
    }

    private void attachToJob(SmartCategorizationService.CategorizationJob job) {
        if (job == null) {
            return;
        }
        detachFromJob();
        activeJob = job;
        progressListener = progress -> javafx.application.Platform.runLater(() -> updateProgress(progress));
        activeJob.addListener(progressListener);

        if (activeJob.isRunning()) {
            autoAssignRunning = true;
            autoAssignButton.setDisable(true);
            autoAssignButton.setText("В работе...");
            autoAssignButton.getStyleClass().add("category-auto-btn-loading");
            showAutoAssignProgress(true);
            SmartCategorizationService.CategorizeProgress last = activeJob.getLastProgress();
            if (last != null) {
                updateProgress(last);
            } else {
                updateProgressLabel("Подготовка...");
            }
            SmartCategorizationService.CategorizationJob jobRef = activeJob;
            activeJob.future().thenAccept(result -> javafx.application.Platform.runLater(() -> {
                if (activeJob != jobRef) {
                    return;
                }
                autoAssignRunning = false;
                showResultNotification(result);
                autoAssignButton.setText("Готово ✓");
                autoAssignButton.getStyleClass().remove("category-auto-btn-loading");
                autoAssignButton.getStyleClass().add("category-auto-btn-success");
                updateProgressLabel("Готово • обновлено " + result.updated());
                refreshContent();
                autoAssignButton.getStyleClass().remove("category-auto-btn-success");
                showAutoAssignProgress(false);
                updateAIButtonState();
            })).exceptionally(ex -> {
                if (activeJob != jobRef) {
                    return null;
                }
                AsyncErrorHandler.reportAsyncException(
                    ex,
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    isDark,
                    "Ошибка умной категоризации",
                    ErrorCode.AI_REQUEST_FAILED,
                    "Не удалось завершить умную категоризацию. Попробуйте позже.",
                    true,
                    "smart.categorization.dialog.job.failed",
                    "operation", "categorizeAllWithAI"
                );
                javafx.application.Platform.runLater(() -> {
                    if (activeJob != jobRef) {
                        return;
                    }
                    autoAssignRunning = false;
                    autoAssignButton.setText("Ошибка");
                    autoAssignButton.getStyleClass().remove("category-auto-btn-loading");
                    autoAssignButton.getStyleClass().add("category-auto-btn-error");
                    updateProgressLabel("Ошибка при обработке");
                    autoAssignButton.getStyleClass().remove("category-auto-btn-error");
                    showAutoAssignProgress(false);
                    updateAIButtonState();
                });
                return null;
            });
        } else {
            autoAssignRunning = false;
            updateAIButtonState();
            detachFromJob();
        }
    }

    private void detachFromJob() {
        if (activeJob != null && progressListener != null) {
            activeJob.removeListener(progressListener);
        }
        activeJob = null;
        progressListener = null;
    }

    private void showResultNotification(SmartCategorizationService.CategorizeResult result) {
        // Создаём уведомление о результате
        HBox notification = new HBox(10);
        notification.setAlignment(Pos.CENTER_LEFT);
        notification.getStyleClass().add("category-notification");
        
        FontIcon checkIcon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 18);
        checkIcon.getStyleClass().add("category-notification-icon");
        
        Label messageLabel = new Label(result.message());
        messageLabel.getStyleClass().add("category-notification-text");
        
        notification.getChildren().addAll(checkIcon, messageLabel);
        
        // Добавляем в начало контента
        if (root.getChildren().size() > 1 && root.getChildren().get(1) instanceof ScrollPane sp) {
            if (sp.getContent() instanceof VBox content && !content.getChildren().isEmpty()) {
                content.getChildren().add(0, notification);
                
                // Убираем уведомление через 4 секунды
                new Thread(() -> {
                    try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
                    javafx.application.Platform.runLater(() -> content.getChildren().remove(notification));
                }).start();
            }
        }
    }

    private void refreshContent() {
        root.getChildren().clear();
        root.getChildren().add(createHeader());

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("category-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox content = new VBox(20);
        content.setPadding(new Insets(20, 25, 25, 25));
        content.getStyleClass().add("category-content");

        List<Task> allTasks = service.getAllTasks();
        Map<String, List<CategorizedTask>> categories = service.categorize(allTasks);
        content.getChildren().add(createSummary(categories, allTasks.size()));
        
        for (Map.Entry<String, List<CategorizedTask>> entry : categories.entrySet()) {
            VBox section = createCategorySection(entry.getKey(), entry.getValue());
            content.getChildren().add(section);
        }
        
        scrollPane.setContent(content);
        root.getChildren().add(scrollPane);
    }

    private VBox createCategorySection(String categoryName, List<CategorizedTask> tasks) {
        VBox section = new VBox(10);
        section.getStyleClass().add("category-section");
        section.setPadding(new Insets(15));

        // Header
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Category cat = tasks.isEmpty() ? null : tasks.get(0).category();
        String emoji = cat != null ? cat.icon() : "📋";
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.getStyleClass().add("category-emoji");

        Label nameLabel = new Label(categoryName);
        nameLabel.getStyleClass().add("category-section-title");

        Label countBadge = new Label(String.valueOf(tasks.size()));
        countBadge.getStyleClass().add("category-count-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        headerRow.getChildren().addAll(emojiLabel, nameLabel, countBadge, spacer);
        section.getChildren().add(headerRow);

        // Task list
        VBox taskList = new VBox(6);
        for (CategorizedTask ct : tasks) {
            HBox taskRow = createTaskRow(ct);
            taskList.getChildren().add(taskRow);
        }
        section.getChildren().add(taskList);

        return section;
    }

    private HBox createTaskRow(CategorizedTask ct) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 12));
        row.getStyleClass().add("category-task-row");

        // Confidence indicator
        Region dot = new Region();
        dot.setMinSize(8, 8);
        dot.setMaxSize(8, 8);
        dot.getStyleClass().add(ct.confidence() > 0.6 ? "confidence-high" : ct.confidence() > 0.3 ? "confidence-medium" : "confidence-low");

        // Task title
        Label titleLabel = new Label(ct.task().getTitle());
        titleLabel.getStyleClass().add("category-task-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        // Tags
        String tags = ct.task().getTags();
        Label tagsLabel = new Label(tags.isEmpty() ? "—" : tags);
        tagsLabel.getStyleClass().add("category-task-tags");

        // Deadline
        Label deadlineLabel = new Label(TaskScheduleFormatter.formatDeadline(ct.task()));
        deadlineLabel.getStyleClass().add("category-task-deadline");

        row.getChildren().addAll(dot, titleLabel, tagsLabel, deadlineLabel);

        row.setOnMouseClicked(e -> {
            if (onTaskSelect != null) onTaskSelect.accept(ct.task());
        });

        return row;
    }

    public static InlineView inline(List<Task> tasks, Consumer<Task> onTaskSelect) {
        return new SmartCategorizationDialog(tasks, onTaskSelect);
    }

    public static InlineView inline(List<Task> tasks) {
        return new SmartCategorizationDialog(tasks, null);
    }

    @Override
    public Node getContent() { return root; }

    @Override
    public Runnable getOnClose() { return this::detachFromJob; }

    @Override
    public void setCloseAction(Runnable closeAction) { this.closeAction = closeAction; }

    @Override
    public String getTitle() { return "Умная категоризация"; }
}
