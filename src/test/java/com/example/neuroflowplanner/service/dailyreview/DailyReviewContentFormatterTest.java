package com.example.neuroflowplanner.service.dailyreview;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyReviewContentFormatterTest {

    @Test
    void rendersMarkdownWithKeySections() {
        DailyReviewResult result = buildResult();

        String markdown = DailyReviewContentFormatter.toMarkdown(result);

        assertTrue(markdown.contains("# Ежедневный обзор"), "title should be present");
        assertTrue(markdown.contains("## AI-сводка дня"), "summary section should be present");
        assertTrue(markdown.contains("## Просрочки"), "overdue section should be present");
        assertTrue(markdown.contains("## Ближайшие дедлайны"), "upcoming section should be present");
        assertTrue(markdown.contains("## Свободные окна"), "free windows section should be present");
        assertTrue(markdown.contains("## Рекомендация фокуса"), "focus section should be present");
        assertTrue(markdown.contains("Подготовить отчёт"), "overdue item should be included");
        assertTrue(markdown.contains("Сделать ревью"), "upcoming item should be included");
    }

    @Test
    void rendersChatSeedPromptFromSnapshot() {
        DailyReviewResult result = buildResult();

        String prompt = DailyReviewContentFormatter.toChatSeedPrompt(result);

        assertTrue(prompt.contains("Используй этот ежедневный обзор как стартовый контекст дня."));
        assertTrue(prompt.contains("1. Коротко оцени ситуацию на день."));
        assertTrue(prompt.contains("Следующий шаг"), "prompt should include actionable guidance");
    }

    private DailyReviewResult buildResult() {
        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-10T08:30:00Z"),
                7,
                1,
                2,
                3,
                45,
                true,
                new DailyReviewSummary(
                        DailyReviewSummarySource.AI,
                        "День плотный, но управляемый",
                        List.of("Закрыть просрочку до полудня", "Держать окно 16:00 под фокусную работу"),
                        "Есть риск срыва дедлайна по отчёту.",
                        "Начать с отчёта и не переключаться 45 минут.",
                        ""
                ),
                List.of(new DailyReviewOverdueItem(
                        "t-1",
                        "Подготовить отчёт",
                        LocalDate.of(2026, 3, 9),
                        LocalDateTime.of(2026, 3, 9, 18, 0),
                        1,
                        3,
                        0.9,
                        List.of("работа")
                )),
                List.of(new DailyReviewUpcomingItem(
                        "t-2",
                        "Сделать ревью",
                        LocalDate.of(2026, 3, 10),
                        LocalDateTime.of(2026, 3, 10, 15, 0),
                        0,
                        true,
                        true,
                        2,
                        0.8,
                        List.of("команда")
                )),
                List.of(),
                List.of(),
                List.of(new DailyReviewFreeWindow(
                        LocalDateTime.of(2026, 3, 10, 16, 0),
                        LocalDateTime.of(2026, 3, 10, 17, 30),
                        90,
                        DailyReviewWindowSuitability.DEEP_WORK,
                        true,
                        "16:00-17:30"
                )),
                new DailyReviewFocusRecommendation(
                        "Закрыть отчёт",
                        "Это снимет главный риск дня и освободит голову под остальные задачи.",
                        "Открой отчёт и работай без переключений 45 минут.",
                        DailyReviewSummarySource.AI
                )
        );
        return new DailyReviewResult(snapshot, snapshot.generatedAt(), "openai/gpt-5.4", true, false);
    }
}
