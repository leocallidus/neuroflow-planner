package com.example.neuroflowplanner.service.focusblocks;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FocusBlockPromptFactoryTest {

    @Test
    void buildsCompactStructuredPromptFromRecommendationSnapshot() {
        FocusProductivityProfile profile = new FocusProductivityProfile(
                Instant.parse("2026-03-11T02:00:00Z"),
                0.81,
                0.22,
                74,
                96,
                540,
                10,
                false,
                List.of(new FocusDayScore(DayOfWeek.WEDNESDAY, 0.84, 4, 250)),
                List.of(
                        new FocusHourScore(10, 0.42, 2, 90, 0.35),
                        new FocusHourScore(14, 0.89, 6, 280, 0.10)
                )
        );
        FocusBlockRecommendation next = new FocusBlockRecommendation(
                "Фокус-блок 14:00-15:30",
                "Это самое сильное окно после обеда.",
                "Заранее подготовьте одну главную задачу.",
                LocalDateTime.of(2026, 3, 11, 14, 0),
                LocalDateTime.of(2026, 3, 11, 15, 30),
                90,
                FocusBlockType.DEEP_FOCUS,
                0.91,
                0.84,
                true,
                List.of(new FocusBlockReason("Исторически сильный час", "После обеда выше устойчивость фокуса."))
        );
        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                LocalDate.of(2026, 3, 11),
                null,
                null,
                profile,
                List.of(new FocusBlockCandidate(
                        "14:00-15:30",
                        LocalDateTime.of(2026, 3, 11, 14, 0),
                        LocalDateTime.of(2026, 3, 11, 15, 30),
                        90,
                        FocusBlockType.DEEP_FOCUS,
                        0.84,
                        0.82,
                        false,
                        List.of()
                )),
                List.of(next),
                List.of(new FocusBlockRecommendation(
                        "Короткий блок 11:30-12:00",
                        "Подходит для лёгких задач.",
                        "Закройте мелкие хвосты.",
                        LocalDateTime.of(2026, 3, 11, 11, 30),
                        LocalDateTime.of(2026, 3, 11, 12, 0),
                        30,
                        FocusBlockType.ADMIN,
                        0.48,
                        0.61,
                        false,
                        List.of()
                )),
                next,
                List.of(new FocusBlockRisk(
                        FocusBlockRiskLevel.WARNING,
                        "Есть просрочки",
                        "Лучшее окно лучше использовать для разгрузки критичных задач."
                )),
                false
        );

        FocusBlockAiPromptPayload payload = FocusBlockPromptFactory.build(snapshot);

        assertTrue(payload.systemPrompt().contains("## Главный блок"));
        assertTrue(payload.systemPrompt().contains("## Почему он подходит"));
        assertTrue(payload.systemPrompt().contains("## Если окно пропустить"));
        assertTrue(payload.userPrompt().contains("### Следующий рекомендуемый блок"));
        assertTrue(payload.userPrompt().contains("### Лучшие фокус-окна"));
        assertTrue(payload.userPrompt().contains("### Короткие окна"));
        assertTrue(payload.userPrompt().contains("### Риски"));
        assertTrue(payload.userPrompt().contains("14:00-15:30"));
        assertTrue(payload.userPrompt().contains("Уверенность профиля"));
        assertFalse(payload.userPrompt().contains("сырую историю"));
    }

    @Test
    void providesUsableFallbackWhenAiUnavailable() {
        FocusBlockRecommendation next = new FocusBlockRecommendation(
                "Фокус-блок 14:00-15:30",
                "Лучшее окно по данным дня.",
                "Начните с главной задачи дня.",
                LocalDateTime.of(2026, 3, 11, 14, 0),
                LocalDateTime.of(2026, 3, 11, 15, 30),
                90,
                FocusBlockType.DEEP_FOCUS,
                0.90,
                0.82,
                true,
                List.of()
        );
        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                LocalDate.of(2026, 3, 11),
                null,
                null,
                FocusProductivityProfile.unavailable(),
                List.of(),
                List.of(next),
                List.of(),
                next,
                List.of(new FocusBlockRisk(
                        FocusBlockRiskLevel.WARNING,
                        "История трекинга ограничена",
                        "Рекомендация построена по ограниченным данным."
                )),
                true
        );

        FocusBlockAiPromptPayload payload = FocusBlockPromptFactory.build(snapshot);

        assertEquals(FocusBlockSummarySource.FALLBACK, payload.fallbackExplanation().source());
        assertTrue(payload.fallbackExplanation().available());
        assertTrue(payload.fallbackExplanation().summary().contains("Лучший блок сейчас"));
        assertTrue(payload.fallbackExplanation().nextAction().contains("Если это окно пропустить"));
        assertTrue(payload.fallbackExplanation().limitations().contains("ограниченной истории"));
    }
}
