package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class DailyReviewContentFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private DailyReviewContentFormatter() {
    }

    public static String buildExportTitle(DailyReviewResult result) {
        LocalDate reviewDate = result == null ? LocalDate.now() : result.reviewDate();
        return "Ежедневный обзор — " + DATE_FORMAT.format(reviewDate);
    }

    public static String toMarkdown(DailyReviewResult result) {
        DailyReviewResult safeResult = result == null
                ? new DailyReviewResult(null, null, "", false, false)
                : result;
        DailyReviewSnapshot snapshot = safeResult.snapshot();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(buildExportTitle(safeResult)).append("\n\n");
        markdown.append("*Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()))
                .append("*\n\n");

        DailyReviewSummary summary = snapshot.summary();
        markdown.append("## AI-сводка дня\n\n");
        if (!summary.headline().isBlank()) {
            markdown.append("**").append(escapeInline(summary.headline())).append("**\n\n");
        }
        if (summary.bullets().isEmpty()) {
            markdown.append("- AI-сводка сейчас недоступна.\n");
        } else {
            appendBullets(markdown, summary.bullets());
        }
        if (!summary.riskNote().isBlank()) {
            markdown.append("\n**Риск:** ").append(escapeInline(summary.riskNote())).append("\n");
        }
        if (!summary.nextStep().isBlank()) {
            markdown.append("\n**Следующий шаг:** ").append(escapeInline(summary.nextStep())).append("\n");
        }

        markdown.append("\n## Просрочки\n\n");
        if (snapshot.overdueItems().isEmpty()) {
            markdown.append("- Просроченных задач нет.\n");
        } else {
            for (DailyReviewOverdueItem item : snapshot.overdueItems()) {
                markdown.append("- **").append(escapeInline(item.title())).append("**");
                markdown.append(" — просрочка ").append(item.overdueDays()).append(" дн.");
                if (item.deadlineDateTime() != null) {
                    markdown.append(" • ").append(DATE_TIME_FORMAT.format(item.deadlineDateTime()));
                }
                markdown.append("\n");
            }
        }

        markdown.append("\n## Ближайшие дедлайны\n\n");
        if (snapshot.upcomingItems().isEmpty()) {
            markdown.append("- Ближайших дедлайнов не найдено.\n");
        } else {
            for (DailyReviewUpcomingItem item : snapshot.upcomingItems()) {
                markdown.append("- **").append(escapeInline(item.title())).append("**");
                markdown.append(item.dueToday()
                        ? " — сегодня"
                        : " — через " + item.daysUntilDue() + " дн.");
                if (item.deadlineDateTime() != null) {
                    markdown.append(" • ").append(DATE_TIME_FORMAT.format(item.deadlineDateTime()));
                }
                if (item.urgent()) {
                    markdown.append(" • срочно");
                }
                markdown.append("\n");
            }
        }

        markdown.append("\n## Свободные окна\n\n");
        if (snapshot.freeWindows().isEmpty()) {
            markdown.append(snapshot.approximateFreeWindows()
                    ? "- Нет надёжных данных для точного расчёта свободных окон.\n"
                    : "- Свободные окна не найдены.\n");
        } else {
            for (DailyReviewFreeWindow window : snapshot.freeWindows()) {
                markdown.append("- **").append(escapeInline(window.label())).append("**");
                markdown.append(" — ").append(window.durationMinutes()).append(" мин");
                markdown.append(" • ").append(window.suitability().name().replace('_', ' ').toLowerCase());
                if (window.approximate()) {
                    markdown.append(" • приблизительно");
                }
                markdown.append("\n");
            }
        }

        markdown.append("\n## Рекомендация фокуса\n\n");
        DailyReviewFocusRecommendation focus = snapshot.focusRecommendation();
        if (!focus.title().isBlank()) {
            markdown.append("**").append(escapeInline(focus.title())).append("**\n\n");
        }
        if (!focus.rationale().isBlank()) {
            markdown.append(focus.rationale()).append("\n\n");
        }
        if (!focus.suggestedNextStep().isBlank()) {
            markdown.append("**Следующий шаг:** ").append(escapeInline(focus.suggestedNextStep())).append("\n");
        }
        if (focus.title().isBlank() && focus.rationale().isBlank() && focus.suggestedNextStep().isBlank()) {
            markdown.append("Фокус дня пока не определён.\n");
        }

        markdown.append("\n## Сигналы дня\n\n");
        markdown.append("- Активных задач: ").append(snapshot.activeTaskCount()).append("\n");
        markdown.append("- Просроченных задач: ").append(snapshot.overdueTaskCount()).append("\n");
        markdown.append("- Дедлайнов на сегодня: ").append(snapshot.tasksDueTodayCount()).append("\n");
        markdown.append("- Ближайших дедлайнов: ").append(snapshot.upcomingTaskCount()).append("\n");
        markdown.append("- Отслежено сегодня: ").append(snapshot.trackedMinutesToday()).append(" мин\n");

        return markdown.toString().trim() + "\n";
    }

    public static String toChatSeedPrompt(DailyReviewResult result) {
        DailyReviewResult safeResult = result == null
                ? new DailyReviewResult(null, null, "", false, false)
                : result;
        DailyReviewSnapshot snapshot = safeResult.snapshot();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Используй этот ежедневный обзор как стартовый контекст дня.\n");
        prompt.append("Помоги уточнить ближайший план действий, риски и фокус на следующие часы.\n\n");
        prompt.append("Дата обзора: ").append(DATE_FORMAT.format(safeResult.reviewDate())).append("\n");
        prompt.append("Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()))
                .append("\n\n");
        prompt.append(toMarkdown(safeResult));
        prompt.append("\nС учётом этого обзора:\n");
        prompt.append("1. Коротко оцени ситуацию на день.\n");
        prompt.append("2. Назови 1-2 ближайших приоритета.\n");
        prompt.append("3. Предложи конкретный следующий шаг на ближайший час.\n");
        if (snapshot.approximateFreeWindows()) {
            prompt.append("4. Учти, что свободные окна рассчитаны приблизительно.\n");
        }
        return prompt.toString().trim();
    }

    private static void appendBullets(StringBuilder markdown, List<String> bullets) {
        for (String bullet : bullets) {
            markdown.append("- ").append(escapeInline(bullet)).append("\n");
        }
    }

    private static String escapeInline(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r", " ").replace("\n", " ").trim();
    }
}
