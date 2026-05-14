package com.example.neuroflowplanner.service.planningquality;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQualitySnapshotContractTest {

    @Test
    void normalizesSnapshotAndProvidesUnavailableFallbacks() {
        PlanningQualitySnapshot snapshot = new PlanningQualitySnapshot(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 14),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                true
        );

        assertEquals(LocalDate.of(2026, 3, 1), snapshot.periodStart());
        assertEquals(LocalDate.of(2026, 3, 14), snapshot.periodEnd());
        assertEquals(PlanningQualitySummarySource.UNAVAILABLE, snapshot.summary().source());
        assertFalse(snapshot.summary().available());
        assertFalse(snapshot.accuracyMetric().available());
        assertFalse(snapshot.rescheduleMetric().available());
        assertFalse(snapshot.rhythmMetric().available());
        assertTrue(snapshot.risks().isEmpty());
        assertTrue(snapshot.recommendations().isEmpty());
        assertTrue(snapshot.dayAggregates().isEmpty());
        assertFalse(snapshot.hasDayAggregates());
        assertFalse(snapshot.hasDeterministicMetrics());
    }

    @Test
    void preservesMinimalDataNeededForUiAndAiConsumers() {
        PlanningQualitySnapshot snapshot = new PlanningQualitySnapshot(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 14),
                Instant.parse("2026-03-11T03:20:00Z"),
                new PlanningQualitySummary(
                        PlanningQualitySummarySource.FALLBACK,
                        "Планирование умеренно устойчивое",
                        "Основная проблема недели - системная недооценка времени.",
                        "Увеличьте оценки для сложных задач на 20-30%.",
                        ""
                ),
                new TimeEstimateAccuracyMetric(18, 14, 0.22, 0.64, 0.58, 0.12, false),
                new RescheduleRateMetric(24, 9, 15, 4, 2, 0.375, true),
                new RhythmStabilityMetric(RhythmStabilityBand.MODERATE, 0.61, 10, 8, 42, 0.28, false),
                List.of(new PlanningQualityRisk(
                        PlanningQualityRiskSeverity.WARNING,
                        "Частая недооценка сложных задач",
                        "Фактическое время часто превышает ожидания."
                )),
                List.of(new PlanningQualityRecommendation(
                        "Добавить буфер",
                        "Закладывайте запас в длинные задачи.",
                        "Попробуйте +25% к оценкам задач глубокой работы.",
                        PlanningQualitySummarySource.FALLBACK
                )),
                List.of(
                        new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 10), 3, 1, 2, 145, false, false, false),
                        new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 11), 4, 2, 3, 210, true, false, false)
                ),
                17,
                11,
                14,
                19,
                12,
                26,
                false
        );

        assertTrue(snapshot.summary().available());
        assertTrue(snapshot.accuracyMetric().available());
        assertTrue(snapshot.rescheduleMetric().available());
        assertTrue(snapshot.rhythmMetric().available());
        assertTrue(snapshot.hasDeterministicMetrics());
        assertTrue(snapshot.hasRisks());
        assertTrue(snapshot.hasRecommendations());
        assertTrue(snapshot.hasDayAggregates());
        assertEquals(2, snapshot.dayAggregates().size());
        assertEquals(RhythmStabilityBand.MODERATE, snapshot.rhythmMetric().band());
        assertEquals(PlanningQualityRiskSeverity.WARNING, snapshot.risks().getFirst().severity());
        assertEquals("Добавить буфер", snapshot.recommendations().getFirst().title());
    }
}
