package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.ChatBotService;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Inline smart reminders view.
 */
public class SmartRemindersDialog implements InlineView {

    private final ScrollPane root;
    private final boolean isDark = ConfigManager.isDarkTheme();
    private final Label summaryLabel;
    private Runnable closeAction;

    private SmartRemindersDialog(List<Task> tasks) {
        List<Task> all = new ArrayList<>();
        for (Task t : tasks) {
            if (!t.isArchived()) { all.add(t); }
            for (Task sub : t.getSubtasks()) {
                if (!sub.isArchived()) all.add(sub);
            }
        }

        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.getStyleClass().add("reminders-content");

        // --- Header ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("reminders-header");

        StackPane iconPane = new StackPane();
        iconPane.getStyleClass().add("reminders-icon-container");
        FontIcon icon = FontIcon.of(MaterialDesignB.BELL_RING, 22);
        icon.getStyleClass().add("reminders-header-icon");
        iconPane.getChildren().add(icon);

        VBox titleBox = new VBox(2);
        Label title = new Label("ИИ-Напоминания");
        title.getStyleClass().add("reminders-title");
        Label subtitle = new Label("Умный анализ ваших задач");
        subtitle.getStyleClass().add("reminders-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        header.getChildren().addAll(iconPane, titleBox);
        content.getChildren().add(header);

        // --- Reminders List ---
        LocalDate today = LocalDate.now();
        List<Reminder> reminders = new ArrayList<>();

        for (Task t : all) {
            long daysLeft = ChronoUnit.DAYS.between(today, t.getDeadline());

            if (daysLeft < 0) {
                reminders.add(new Reminder(MaterialDesignA.ALERT_CIRCLE, "ПРОСРОЧЕНО", t.getTitle() + " просрочена на " + (-daysLeft) + " дн.", "reminder-danger", 100));
            } else if (daysLeft == 0) {
                reminders.add(new Reminder(MaterialDesignC.CALENDAR_CLOCK, "СЕГОДНЯ", t.getTitle() + " — дедлайн сегодня!", "reminder-danger", 90));
            } else if (daysLeft == 1) {
                reminders.add(new Reminder(MaterialDesignC.CLOCK_FAST, "ЗАВТРА", t.getTitle() + " — дедлайн завтра", "reminder-warning", 80));
            } else if (t.getSmartPriority() >= 7 && daysLeft <= 3) {
                reminders.add(new Reminder(MaterialDesignL.LIGHTNING_BOLT, "СРОЧНО", t.getTitle() + " — высокий приоритет, осталось " + daysLeft + " дн.", "reminder-danger", 85));
            } else if (t.getComplexity() >= 7 && daysLeft <= t.getComplexity()) {
                reminders.add(new Reminder(MaterialDesignA.ARM_FLEX, "НАЧНИТЕ СЕЙЧАС", t.getTitle() + " — сложная задача, начните заранее", "reminder-info", 70));
            }
            if (t.hasDependencies()) {
                reminders.add(new Reminder(MaterialDesignL.LINK_VARIANT, "ЗАВИСИМОСТЬ", t.getTitle() + " зависит от других задач", "reminder-info", 50));
            }
            if (t.getSmartPriority() >= 5 && t.getTrackedMinutes() == 0 && daysLeft <= 7) {
                reminders.add(new Reminder(MaterialDesignC.CHART_DONUT, "НЕТ ПРОГРЕССА", t.getTitle() + " — нет учтённого времени", "reminder-warning", 60));
            }
        }

        reminders.sort((a, b) -> Integer.compare(b.priority, a.priority));

        VBox cardsContainer = new VBox(12);
        if (reminders.isEmpty()) {
            cardsContainer.getChildren().add(createEmptyState());
        } else {
            for (Reminder r : reminders) {
                cardsContainer.getChildren().add(createReminderCard(r));
            }
        }
        content.getChildren().add(cardsContainer);

        // --- AI Summary ---
        VBox summaryBox = new VBox(10);
        summaryBox.getStyleClass().add("ai-summary-box");
        
        HBox summaryHeader = new HBox(8);
        summaryHeader.setAlignment(Pos.CENTER_LEFT);
        FontIcon aiIcon = FontIcon.of(MaterialDesignA.AUTO_FIX, 18);
        aiIcon.getStyleClass().add("ai-summary-icon");
        Label summaryTitle = new Label("ИИ-Рекомендация");
        summaryTitle.getStyleClass().add("ai-summary-title");
        summaryHeader.getChildren().addAll(aiIcon, summaryTitle);

        summaryLabel = new Label("⏳ Анализирую задачи...");
        summaryLabel.setWrapText(true);
        summaryLabel.getStyleClass().add("ai-summary-text");
        
        summaryBox.getChildren().addAll(summaryHeader, summaryLabel);
        content.getChildren().add(summaryBox);

        root = new ScrollPane(content);
        root.setFitToWidth(true);
        // Адаптивные размеры для низких разрешений
        root.setMinSize(320, 350);
        root.getStyleClass().add("reminders-root");
        
        root.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (isDark) {
            root.getStylesheets().add(getClass().getResource("/styles/dark-theme.css").toExternalForm());
        }

        // Запрос к ИИ
        requestAISummary(all, today);
    }

    private void requestAISummary(List<Task> tasks, LocalDate today) {
        StringBuilder prompt = new StringBuilder("Проанализируй мои задачи и дай 2-3 кратких совета по приоритетам:\n\n");
        
        int count = 0;
        for (Task t : tasks) {
            if (count >= 10) break;
            long days = ChronoUnit.DAYS.between(today, t.getDeadline());
            String status = days < 0 ? "ПРОСРОЧЕНА" : days == 0 ? "сегодня" : days + " дн.";
            prompt.append("- ").append(t.getTitle())
                  .append(" (сложность: ").append(t.getComplexity())
                  .append(", дедлайн: ").append(status).append(")\n");
            count++;
        }
        
        prompt.append("\nОтветь кратко, 2-3 пункта на русском.");

        try {
            ChatBotService chatService = new ChatBotService();
            chatService.sendMessage(prompt.toString())
                .thenAccept(response -> Platform.runLater(() -> summaryLabel.setText(response)))
                .exceptionally(e -> {
                    Platform.runLater(() -> summaryLabel.setText(generateFallbackSummary(tasks, today)));
                    return null;
                });
        } catch (Exception e) {
            summaryLabel.setText(generateFallbackSummary(tasks, today));
        }
    }

    private String generateFallbackSummary(List<Task> tasks, LocalDate today) {
        long overdue = tasks.stream().filter(t -> t.getDeadline().isBefore(today)).count();
        long dueToday = tasks.stream().filter(t -> t.getDeadline().equals(today)).count();
        long highPriority = tasks.stream().filter(t -> t.getSmartPriority() >= 7).count();
        long thisWeek = tasks.stream().filter(t -> {
            long d = ChronoUnit.DAYS.between(today, t.getDeadline());
            return d >= 0 && d <= 7;
        }).count();

        StringBuilder sb = new StringBuilder();

        if (overdue > 0) {
            sb.append("⚠️ У вас ").append(overdue).append(" просроченных задач. Рекомендую разобраться с ними в первую очередь.\n\n");
        }
        if (dueToday > 0) {
            sb.append("📅 Сегодня дедлайн у ").append(dueToday).append(" задач. Сфокусируйтесь на них.\n\n");
        }
        if (highPriority > 0 && overdue == 0 && dueToday == 0) {
            sb.append("🎯 ").append(highPriority).append(" задач с высоким приоритетом. Начните с самой важной.\n\n");
        }
        if (thisWeek > 3) {
            sb.append("📊 На этой неделе ").append(thisWeek).append(" задач. Распределите нагрузку равномерно.\n\n");
        }
        if (sb.isEmpty()) {
            sb.append("✨ Отличная работа! Нагрузка сбалансирована. Продолжайте в том же духе.");
        }

        return sb.toString().trim();
    }

    public static InlineView inline(List<Task> tasks) {
        return new SmartRemindersDialog(tasks);
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
        return "Напоминания";
    }

    private HBox createEmptyState() {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("reminder-empty-state");
        
        FontIcon icon = FontIcon.of(MaterialDesignC.CHECK_CIRCLE, 24);
        icon.getStyleClass().add("reminder-empty-icon");
        Label lbl = new Label("Нет срочных напоминаний. Всё под контролем!");
        lbl.getStyleClass().add("reminder-empty-text");
        
        box.getChildren().addAll(icon, lbl);
        return box;
    }

    private HBox createReminderCard(Reminder r) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("reminder-card", r.styleClass);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("reminder-card-icon-box");
        FontIcon icon = FontIcon.of(r.iconCode, 20);
        icon.getStyleClass().add("reminder-card-icon");
        iconBox.getChildren().add(icon);

        VBox text = new VBox(3);
        Label type = new Label(r.type);
        type.getStyleClass().add("reminder-card-type");
        Label msg = new Label(r.message);
        msg.getStyleClass().add("reminder-card-msg");
        msg.setWrapText(true);
        text.getChildren().addAll(type, msg);
        HBox.setHgrow(text, Priority.ALWAYS);

        card.getChildren().addAll(iconBox, text);
        return card;
    }

    private record Reminder(org.kordamp.ikonli.Ikon iconCode, String type, String message, String styleClass, int priority) {}
}
