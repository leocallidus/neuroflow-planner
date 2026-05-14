package com.example.neuroflowplanner.service.focusblocks;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FocusBlockRecommendationEngineTest {

    @Test
    void ranksHistoricallyStrongDeepWindowAboveShorterAlternatives() {
        FocusProductivityProfile profile = new FocusProductivityProfile(
                Instant.parse("2026-03-11T02:00:00Z"),
                0.82,
                0.24,
                78,
                96,
                640,
                11,
                false,
                List.of(new FocusDayScore(DayOfWeek.WEDNESDAY, 0.81, 4, 280)),
                List.of(
                        new FocusHourScore(9, 0.35, 2, 70, 0.35),
                        new FocusHourScore(10, 0.38, 2, 80, 0.32),
                        new FocusHourScore(14, 0.90, 6, 290, 0.10),
                        new FocusHourScore(15, 0.88, 5, 240, 0.12)
                )
        );
        FocusBlockCandidate earlyShort = new FocusBlockCandidate(
                "09:00-09:40",
                LocalDateTime.of(2026, 3, 11, 9, 0),
                LocalDateTime.of(2026, 3, 11, 9, 40),
                40,
                FocusBlockType.ADMIN,
                0.44,
                0.70,
                false,
                List.of()
        );
        FocusBlockCandidate bestWindow = new FocusBlockCandidate(
                "14:00-15:30",
                LocalDateTime.of(2026, 3, 11, 14, 0),
                LocalDateTime.of(2026, 3, 11, 15, 30),
                90,
                FocusBlockType.DEEP_FOCUS,
                0.84,
                0.82,
                false,
                List.of()
        );
        FocusBlockCandidate mediumWindow = new FocusBlockCandidate(
                "11:30-12:20",
                LocalDateTime.of(2026, 3, 11, 11, 30),
                LocalDateTime.of(2026, 3, 11, 12, 20),
                50,
                FocusBlockType.LIGHT_FOCUS,
                0.62,
                0.68,
                false,
                List.of()
        );

        FocusBlockRecommendationEngine engine = new FocusBlockRecommendationEngine();
        FocusBlockRecommendationEngineResult result = engine.recommend(new FocusBlockRecommendationEngineInput(
                LocalDate.of(2026, 3, 11),
                profile,
                List.of(earlyShort, bestWindow, mediumWindow),
                9,
                0,
                1
        ));

        assertTrue(result.nextRecommendedBlock().available());
        assertEquals("Фокус-блок 14:00-15:30", result.nextRecommendedBlock().title());
        assertFalse(result.focusWindows().isEmpty());
        assertEquals(LocalDateTime.of(2026, 3, 11, 14, 0), result.focusWindows().getFirst().startAt());
        assertTrue(result.focusWindows().getFirst().suitabilityScore() >= result.shortWindows().getFirst().suitabilityScore());
    }

    @Test
    void addsUrgencyAndLimitedHistoryRisks() {
        FocusProductivityProfile profile = new FocusProductivityProfile(
                Instant.now(),
                0.28,
                0.74,
                32,
                40,
                120,
                3,
                true,
                List.of(new FocusDayScore(DayOfWeek.WEDNESDAY, 0.4, 1, 60)),
                List.of(new FocusHourScore(10, 0.48, 1, 60, 0.65))
        );
        FocusBlockCandidate candidate = new FocusBlockCandidate(
                "10:00-10:30",
                LocalDateTime.of(2026, 3, 11, 10, 0),
                LocalDateTime.of(2026, 3, 11, 10, 30),
                30,
                FocusBlockType.ADMIN,
                0.44,
                0.48,
                true,
                List.of()
        );

        FocusBlockRecommendationEngine engine = new FocusBlockRecommendationEngine();
        FocusBlockRecommendationEngineResult result = engine.recommend(new FocusBlockRecommendationEngineInput(
                LocalDate.of(2026, 3, 11),
                profile,
                List.of(candidate),
                14,
                3,
                2
        ));

        assertTrue(result.nextRecommendedBlock().available());
        assertTrue(result.risks().stream().anyMatch(risk -> risk.title().contains("просрочки") || risk.title().contains("Просрочки") || risk.title().contains("Есть просрочки")));
        assertTrue(result.risks().stream().anyMatch(risk -> risk.title().contains("История трекинга")));
        assertTrue(result.risks().stream().anyMatch(risk -> risk.title().contains("Высокая переключаемость")));
    }

    @Test
    void returnsUnavailableRecommendationWhenNoCandidatesExist() {
        FocusBlockRecommendationEngine engine = new FocusBlockRecommendationEngine();

        FocusBlockRecommendationEngineResult result = engine.recommend(new FocusBlockRecommendationEngineInput(
                LocalDate.of(2026, 3, 11),
                FocusProductivityProfile.unavailable(),
                List.of(),
                0,
                0,
                0
        ));

        assertFalse(result.nextRecommendedBlock().available());
        assertTrue(result.focusWindows().isEmpty());
        assertTrue(result.shortWindows().isEmpty());
        assertFalse(result.risks().isEmpty());
    }
}
