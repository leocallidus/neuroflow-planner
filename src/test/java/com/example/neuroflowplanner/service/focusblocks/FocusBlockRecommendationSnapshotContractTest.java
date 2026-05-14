package com.example.neuroflowplanner.service.focusblocks;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FocusBlockRecommendationSnapshotContractTest {

    @Test
    void normalizesSnapshotAndProvidesUnavailableFallbacks() {
        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                LocalDate.of(2026, 3, 11),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );

        assertEquals(LocalDate.of(2026, 3, 11), snapshot.reviewDate());
        assertEquals(FocusBlockSummarySource.UNAVAILABLE, snapshot.explanation().source());
        assertFalse(snapshot.explanation().available());
        assertFalse(snapshot.productivityProfile().available());
        assertTrue(snapshot.candidateWindows().isEmpty());
        assertTrue(snapshot.focusWindows().isEmpty());
        assertTrue(snapshot.shortWindows().isEmpty());
        assertFalse(snapshot.nextRecommendedBlock().available());
        assertTrue(snapshot.risks().isEmpty());
    }

    @Test
    void preservesMinimalDataNeededForUiAndAiConsumers() {
        FocusProductivityProfile profile = new FocusProductivityProfile(
                Instant.parse("2026-03-11T03:15:00Z"),
                0.78,
                0.34,
                92,
                108,
                550,
                10,
                false,
                List.of(
                        new FocusDayScore(DayOfWeek.TUESDAY, 0.88, 4, 240),
                        new FocusDayScore(DayOfWeek.WEDNESDAY, 0.64, 3, 180)
                ),
                List.of(
                        new FocusHourScore(9, 0.44, 3, 130, 0.22),
                        new FocusHourScore(14, 0.87, 7, 420, 0.08)
                )
        );
        FocusBlockCandidate candidate = new FocusBlockCandidate(
                "14:00-15:30",
                LocalDateTime.of(2026, 3, 11, 14, 0),
                LocalDateTime.of(2026, 3, 11, 15, 30),
                90,
                FocusBlockType.DEEP_FOCUS,
                0.91,
                0.83,
                false,
                List.of(new FocusBlockReason("Высокая продуктивность", "Исторически сильный час после обеда."))
        );
        FocusBlockRecommendation nextBlock = new FocusBlockRecommendation(
                "Главный фокус-блок дня",
                "Окно длинное, без конфликтов и попадает в пик концентрации.",
                "Используйте слот для главной сложной задачи дня.",
                LocalDateTime.of(2026, 3, 11, 14, 0),
                LocalDateTime.of(2026, 3, 11, 15, 30),
                90,
                FocusBlockType.DEEP_FOCUS,
                0.93,
                0.85,
                true,
                List.of(
                        new FocusBlockReason("История трекинга", "В это время выше средняя длина устойчивых сессий."),
                        new FocusBlockReason("Чистое окно", "Нет назначенных блоков и мало риска переключений.")
                )
        );
        FocusBlockExplanation explanation = new FocusBlockExplanation(
                FocusBlockSummarySource.FALLBACK,
                "Сильное окно после обеда",
                "Лучшее окно для глубокого фокуса сегодня приходится на 14:00-15:30.",
                "Подготовьте одну главную задачу и отключите отвлекающие каналы.",
                ""
        );

        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                LocalDate.of(2026, 3, 11),
                Instant.parse("2026-03-11T03:20:00Z"),
                explanation,
                profile,
                List.of(candidate),
                List.of(nextBlock),
                List.of(new FocusBlockRecommendation(
                        "Короткий резервный блок",
                        "Подходит для лёгких задач.",
                        "Закройте мелкие административные дела.",
                        LocalDateTime.of(2026, 3, 11, 11, 20),
                        LocalDateTime.of(2026, 3, 11, 11, 50),
                        30,
                        FocusBlockType.ADMIN,
                        0.58,
                        0.60,
                        false,
                        List.of(new FocusBlockReason("Короткое окно", "Слишком коротко для deep work."))
                )),
                nextBlock,
                List.of(new FocusBlockRisk(
                        FocusBlockRiskLevel.WARNING,
                        "Ограниченная первая половина дня",
                        "До обеда почти нет длинных чистых слотов."
                )),
                false
        );

        assertTrue(snapshot.explanation().available());
        assertTrue(snapshot.productivityProfile().available());
        assertEquals(2, snapshot.productivityProfile().dayScores().size());
        assertEquals(2, snapshot.productivityProfile().hourScores().size());
        assertTrue(snapshot.hasCandidateWindows());
        assertTrue(snapshot.hasFocusWindows());
        assertTrue(snapshot.hasShortWindows());
        assertTrue(snapshot.nextRecommendedBlock().available());
        assertEquals(FocusBlockType.DEEP_FOCUS, snapshot.nextRecommendedBlock().type());
        assertEquals("Главный фокус-блок дня", snapshot.nextRecommendedBlock().title());
        assertTrue(snapshot.hasRisks());
        assertEquals(FocusBlockRiskLevel.WARNING, snapshot.risks().getFirst().level());
    }
}
