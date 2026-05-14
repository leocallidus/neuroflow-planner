package com.example.neuroflowplanner.service.dailyreview;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class DailyReviewPromptFactory {

    private static final int MAX_ITEMS_PER_SECTION = 6;
    private static final int MAX_TITLE_LENGTH = 120;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu, HH:mm");

    private DailyReviewPromptFactory() {
    }

    public static DailyReviewAiPromptPayload build(DailyReviewSnapshot snapshot) {
        DailyReviewSnapshot safeSnapshot = snapshot == null
                ? new DailyReviewSnapshot(
                        null, null, 0, 0, 0, 0, 0, true,
                        null, List.of(), List.of(), List.of(), List.of(), List.of(), null
                )
                : snapshot;
        return new DailyReviewAiPromptPayload(
                buildSystemPrompt(),
                buildUserPrompt(safeSnapshot),
                buildFallbackSummary(safeSnapshot),
                buildFallbackFocusRecommendation(safeSnapshot)
        );
    }

    public static String buildSystemPrompt() {
        return """
                Ты формируешь краткий ежедневный обзор для NeuroFlow Planner.
                Тебе нельзя пересказывать весь бэклог. Нужно опираться только на структурированный daily snapshot текущего дня.

                Цель ответа:
                - быстро дать пользователю картину дня;
                - показать риски;
                - назвать 1-2 приоритета;
                - дать одну конкретную рекомендацию фокуса.

                Обязательные правила:
                1. Ответ должен быть на русском языке.
                2. Не выдумывай факты, которых нет во входных данных.
                3. Не перечисляй все задачи подряд.
                4. Формулировки должны быть конкретными и прикладными.
                5. Не используй общие фразы вроде "день выглядит интересным" или "стоит сохранять баланс".
                6. Если свободные окна неизвестны или приблизительны, честно отрази это.
                7. Не добавляй вступление или послесловие вне формата.

                Верни ответ строго в таком формате:
                ## Общая картина дня
                - 2-3 коротких bullet
                ## Риски
                - 1-2 bullet
                ## Приоритеты
                - 1-2 bullet
                ## Фокус-рекомендация
                - 1 bullet в формате "Сейчас лучше заняться ... потому что ..."
                """;
    }

    public static String buildUserPrompt(DailyReviewSnapshot snapshot) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Собери ежедневный обзор по данным текущего дня.\n\n");
        prompt.append("Дата обзора: ").append(snapshot.reviewDate().format(DATE_FORMAT)).append('\n');
        prompt.append("Активных задач: ").append(snapshot.activeTaskCount()).append('\n');
        prompt.append("Просроченных задач: ").append(snapshot.overdueTaskCount()).append('\n');
        prompt.append("Задач с дедлайном сегодня: ").append(snapshot.tasksDueTodayCount()).append('\n');
        prompt.append("Ближайших дедлайнов: ").append(snapshot.upcomingTaskCount()).append('\n');
        prompt.append("Отслежено минут сегодня: ").append(snapshot.trackedMinutesToday()).append('\n');
        prompt.append("Свободные окна приблизительные: ").append(snapshot.approximateFreeWindows() ? "да" : "нет").append("\n\n");

        appendOverdueSection(prompt, snapshot.overdueItems());
        appendUpcomingSection(prompt, snapshot.upcomingItems());
        appendWorkIntervalsSection(prompt, snapshot.workIntervals());
        appendBusyBlocksSection(prompt, snapshot.knownTimeBlocks());
        appendFreeWindowsSection(prompt, snapshot.freeWindows(), snapshot.approximateFreeWindows());
        appendFallbackContext(prompt, snapshot);

        return prompt.toString().trim();
    }

    public static DailyReviewSummary buildFallbackSummary(DailyReviewSnapshot snapshot) {
        DailyReviewSummary existing = snapshot.summary();
        if (existing != null && existing.source() == DailyReviewSummarySource.FALLBACK && existing.available()) {
            return existing;
        }

        List<String> bullets = new ArrayList<>();
        if (snapshot.overdueTaskCount() > 0) {
            bullets.add("Есть " + snapshot.overdueTaskCount() + " просроченных задач, они создают основное давление на день.");
        }
        if (snapshot.tasksDueTodayCount() > 0) {
            bullets.add("Сегодня истекает " + snapshot.tasksDueTodayCount() + " дедлайнов, день требует приоритизации.");
        }
        if (snapshot.hasFreeWindows()) {
            DailyReviewFreeWindow bestWindow = snapshot.freeWindows().stream()
                    .max(java.util.Comparator.comparingInt(DailyReviewFreeWindow::durationMinutes))
                    .orElse(null);
            if (bestWindow != null) {
                bullets.add("Лучшее свободное окно: " + bestWindow.label() + " (" + bestWindow.durationMinutes() + " мин).");
            }
        } else if (snapshot.approximateFreeWindows()) {
            bullets.add("Свободные окна рассчитаны приблизительно, потому что данных по дню недостаточно.");
        }
        if (bullets.isEmpty()) {
            bullets.add("Критических сигналов не найдено, день выглядит управляемым.");
        }

        String headline = snapshot.overdueTaskCount() > 0
                ? "День требует быстрой стабилизации"
                : snapshot.tasksDueTodayCount() > 0
                ? "Главный фокус — ближайшие дедлайны"
                : "День выглядит управляемым";
        String riskNote = snapshot.overdueTaskCount() > 0
                ? "Просрочки уже влияют на остаток дня и требуют немедленного внимания."
                : snapshot.tasksDueTodayCount() > 0
                ? "Без раннего фокуса задачи с дедлайном сегодня могут перейти в зону риска."
                : "";
        String nextStep = buildFallbackFocusRecommendation(snapshot).suggestedNextStep();

        return new DailyReviewSummary(
                DailyReviewSummarySource.FALLBACK,
                headline,
                bullets,
                riskNote,
                nextStep,
                ""
        );
    }

    public static DailyReviewFocusRecommendation buildFallbackFocusRecommendation(DailyReviewSnapshot snapshot) {
        DailyReviewFocusRecommendation existing = snapshot.focusRecommendation();
        if (existing != null && existing.source() == DailyReviewSummarySource.FALLBACK && existing.available()) {
            return existing;
        }
        if (snapshot.hasOverdueItems()) {
            DailyReviewOverdueItem item = snapshot.overdueItems().getFirst();
            return new DailyReviewFocusRecommendation(
                    item.title(),
                    "Это самая критичная просрочка в текущем дне.",
                    "Сейчас лучше заняться \"" + item.title() + "\", потому что задача уже просрочена и тянет за собой остальной план.",
                    DailyReviewSummarySource.FALLBACK
            );
        }
        if (snapshot.hasUpcomingItems()) {
            DailyReviewUpcomingItem item = snapshot.upcomingItems().getFirst();
            return new DailyReviewFocusRecommendation(
                    item.title(),
                    item.dueToday()
                            ? "Задача закрывает дедлайн сегодняшнего дня."
                            : "Это ближайший дедлайн с наибольшим влиянием на день.",
                    "Сейчас лучше заняться \"" + item.title() + "\", потому что это ближайший дедлайн и его стоит продвинуть до начала хаотичных переключений.",
                    DailyReviewSummarySource.FALLBACK
            );
        }
        return new DailyReviewFocusRecommendation(
                "Профилактический фокус",
                "Критических задач не найдено.",
                "Сейчас лучше заняться разбором мелких хвостов или подготовкой следующего важного блока, потому что день пока не перегружен.",
                DailyReviewSummarySource.FALLBACK
        );
    }

    private static void appendOverdueSection(StringBuilder prompt, List<DailyReviewOverdueItem> items) {
        prompt.append("### Просрочки\n");
        if (items == null || items.isEmpty()) {
            prompt.append("- Нет просроченных задач.\n\n");
            return;
        }
        for (DailyReviewOverdueItem item : items.stream().limit(MAX_ITEMS_PER_SECTION).toList()) {
            prompt.append("- ")
                    .append(limit(item.title()))
                    .append(" | просрочка: ")
                    .append(item.overdueDays())
                    .append(" дн");
            if (item.deadlineDateTime() != null) {
                prompt.append(" | дедлайн: ").append(item.deadlineDateTime().format(DATE_TIME_FORMAT));
            } else if (item.deadlineDate() != null) {
                prompt.append(" | дедлайн: ").append(item.deadlineDate().format(DATE_FORMAT));
            }
            prompt.append('\n');
        }
        prompt.append('\n');
    }

    private static void appendUpcomingSection(StringBuilder prompt, List<DailyReviewUpcomingItem> items) {
        prompt.append("### Ближайшие дедлайны\n");
        if (items == null || items.isEmpty()) {
            prompt.append("- Ближайших дедлайнов не найдено.\n\n");
            return;
        }
        for (DailyReviewUpcomingItem item : items.stream().limit(MAX_ITEMS_PER_SECTION).toList()) {
            prompt.append("- ")
                    .append(limit(item.title()))
                    .append(" | через ")
                    .append(item.daysUntilDue())
                    .append(" дн");
            if (item.dueToday()) {
                prompt.append(" | СЕГОДНЯ");
            }
            if (item.urgent()) {
                prompt.append(" | срочно");
            }
            if (item.deadlineDateTime() != null) {
                prompt.append(" | дедлайн: ").append(item.deadlineDateTime().format(DATE_TIME_FORMAT));
            }
            prompt.append('\n');
        }
        prompt.append('\n');
    }

    private static void appendWorkIntervalsSection(StringBuilder prompt, List<DailyReviewWorkInterval> intervals) {
        prompt.append("### Рабочие интервалы\n");
        if (intervals == null || intervals.isEmpty()) {
            prompt.append("- Рабочие часы на день не настроены.\n\n");
            return;
        }
        for (DailyReviewWorkInterval interval : intervals.stream().limit(MAX_ITEMS_PER_SECTION).toList()) {
            prompt.append("- ")
                    .append(interval.label().isBlank() ? formatLabel(interval.start(), interval.end()) : interval.label())
                    .append(" | длительность: ")
                    .append(interval.durationMinutes())
                    .append(" мин\n");
        }
        prompt.append('\n');
    }

    private static void appendBusyBlocksSection(StringBuilder prompt, List<DailyReviewTimeBlock> blocks) {
        prompt.append("### Известные занятые блоки\n");
        if (blocks == null || blocks.isEmpty()) {
            prompt.append("- Явных занятых блоков нет.\n\n");
            return;
        }
        for (DailyReviewTimeBlock block : blocks.stream().limit(MAX_ITEMS_PER_SECTION).toList()) {
            prompt.append("- ")
                    .append(limit(block.title().isBlank() ? "Занятый блок" : block.title()))
                    .append(" | ")
                    .append(formatLabel(block.start(), block.end()))
                    .append(" | source: ")
                    .append(block.source())
                    .append(block.approximate() ? " | approximate" : "")
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendFreeWindowsSection(StringBuilder prompt, List<DailyReviewFreeWindow> freeWindows, boolean approximate) {
        prompt.append("### Свободные окна\n");
        if (freeWindows == null || freeWindows.isEmpty()) {
            if (approximate) {
                prompt.append("- Нет надёжных данных для точного расчёта свободных окон.\n\n");
            } else {
                prompt.append("- Свободные окна не найдены.\n\n");
            }
            return;
        }
        for (DailyReviewFreeWindow window : freeWindows.stream().limit(MAX_ITEMS_PER_SECTION).toList()) {
            prompt.append("- ")
                    .append(window.label().isBlank() ? formatLabel(window.start(), window.end()) : window.label())
                    .append(" | ")
                    .append(window.durationMinutes())
                    .append(" мин | suitability: ")
                    .append(window.suitability().name())
                    .append(window.approximate() ? " | approximate" : "")
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendFallbackContext(StringBuilder prompt, DailyReviewSnapshot snapshot) {
        prompt.append("### Локальный fallback\n");
        DailyReviewSummary fallbackSummary = buildFallbackSummary(snapshot);
        prompt.append("- headline: ").append(fallbackSummary.headline()).append('\n');
        for (String bullet : fallbackSummary.bullets()) {
            prompt.append("- fallback_bullet: ").append(bullet).append('\n');
        }
        if (!fallbackSummary.riskNote().isBlank()) {
            prompt.append("- fallback_risk: ").append(fallbackSummary.riskNote()).append('\n');
        }
        DailyReviewFocusRecommendation fallbackFocus = buildFallbackFocusRecommendation(snapshot);
        if (fallbackFocus.available()) {
            prompt.append("- fallback_focus: ").append(fallbackFocus.suggestedNextStep()).append('\n');
        }
        prompt.append('\n');
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_TITLE_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TITLE_LENGTH).trim() + "...";
    }

    private static String formatLabel(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) {
            return "";
        }
        return start.format(DateTimeFormatter.ofPattern("HH:mm")) + "-" + end.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
}
