package com.example.neuroflowplanner.service.dailyreview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyReviewPromptFactoryTest {

    @Test
    void buildsStructuredCompactPromptFromSnapshot() {
        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                LocalDate.of(2026, 3, 10),
                null,
                7,
                1,
                2,
                3,
                45,
                false,
                null,
                List.of(new DailyReviewOverdueItem(
                        "task-1",
                        "Fix billing export before client sync",
                        LocalDate.of(2026, 3, 9),
                        LocalDateTime.of(2026, 3, 9, 18, 0),
                        1,
                        8,
                        0.9,
                        List.of("finance")
                )),
                List.of(new DailyReviewUpcomingItem(
                        "task-2",
                        "Prepare team sync notes",
                        LocalDate.of(2026, 3, 10),
                        LocalDateTime.of(2026, 3, 10, 16, 0),
                        0,
                        true,
                        true,
                        5,
                        0.7,
                        List.of("team")
                )),
                List.of(new DailyReviewWorkInterval(
                        LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 10, 18, 0),
                        540,
                        true,
                        "09:00-18:00"
                )),
                List.of(new DailyReviewTimeBlock(
                        "task-2",
                        "Prepare team sync notes",
                        LocalDateTime.of(2026, 3, 10, 10, 0),
                        LocalDateTime.of(2026, 3, 10, 11, 0),
                        60,
                        "task_schedule",
                        false
                )),
                List.of(new DailyReviewFreeWindow(
                        LocalDateTime.of(2026, 3, 10, 14, 0),
                        LocalDateTime.of(2026, 3, 10, 15, 30),
                        90,
                        DailyReviewWindowSuitability.DEEP_WORK,
                        false,
                        "14:00-15:30"
                )),
                null
        );

        DailyReviewAiPromptPayload payload = DailyReviewPromptFactory.build(snapshot);

        assertTrue(payload.systemPrompt().contains("## Общая картина дня"));
        assertTrue(payload.systemPrompt().contains("## Риски"));
        assertTrue(payload.systemPrompt().contains("## Приоритеты"));
        assertTrue(payload.systemPrompt().contains("## Фокус-рекомендация"));

        assertTrue(payload.userPrompt().contains("### Просрочки"));
        assertTrue(payload.userPrompt().contains("### Ближайшие дедлайны"));
        assertTrue(payload.userPrompt().contains("### Свободные окна"));
        assertTrue(payload.userPrompt().contains("Fix billing export before client sync"));
        assertTrue(payload.userPrompt().contains("14:00-15:30"));
        assertFalse(payload.userPrompt().contains("description"));
    }

    @Test
    void providesUsableFallbackWhenAiUnavailable() {
        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                LocalDate.of(2026, 3, 10),
                null,
                2,
                0,
                1,
                1,
                0,
                true,
                null,
                List.of(),
                List.of(new DailyReviewUpcomingItem(
                        "task-2",
                        "Prepare team sync notes",
                        LocalDate.of(2026, 3, 10),
                        LocalDateTime.of(2026, 3, 10, 16, 0),
                        0,
                        true,
                        true,
                        5,
                        0.7,
                        List.of("team")
                )),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        DailyReviewAiPromptPayload payload = DailyReviewPromptFactory.build(snapshot);

        assertEquals(DailyReviewSummarySource.FALLBACK, payload.fallbackSummary().source());
        assertFalse(payload.fallbackSummary().headline().isBlank());
        assertFalse(payload.fallbackSummary().bullets().isEmpty());
        assertTrue(payload.fallbackFocusRecommendation().available());
        assertEquals(DailyReviewSummarySource.FALLBACK, payload.fallbackFocusRecommendation().source());
        assertTrue(payload.fallbackFocusRecommendation().suggestedNextStep().contains("Сейчас лучше заняться"));
    }
}
