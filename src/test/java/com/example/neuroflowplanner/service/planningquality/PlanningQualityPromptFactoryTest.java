package com.example.neuroflowplanner.service.planningquality;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQualityPromptFactoryTest {

    @Test
    void buildsCompactPromptFromStructuredSnapshot() {
        PlanningQualitySnapshot snapshot = new PlanningQualitySnapshot(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 14),
                Instant.parse("2026-03-11T10:15:00Z"),
                null,
                new TimeEstimateAccuracyMetric(20, 14, 0.18, 0.64, 0.57, 0.14, false),
                new RescheduleRateMetric(18, 8, 10, 3, 2, 0.44, true),
                new RhythmStabilityMetric(RhythmStabilityBand.MODERATE, 0.58, 10, 8, 52, 0.26, false),
                List.of(
                        new PlanningQualityRisk(
                                PlanningQualityRiskSeverity.WARNING,
                                "Частые переносы перед дедлайном",
                                "Несколько задач сдвигаются уже вблизи дедлайна."
                        )
                ),
                List.of(
                        new PlanningQualityRecommendation(
                                "Ослабить перегруженные дни",
                                "Много жёстких окон увеличивает переносы.",
                                "Оставьте буфер между сложными задачами.",
                                PlanningQualitySummarySource.FALLBACK
                        )
                ),
                List.of(
                        new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 10), 3, 1, 2, 145, false, false, false),
                        new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 11), 5, 2, 3, 240, true, false, false)
                ),
                17,
                9,
                14,
                18,
                12,
                24,
                false
        );

        PlanningQualityAiPromptPayload payload = PlanningQualityPromptFactory.build(snapshot);

        assertTrue(payload.systemPrompt().contains("## Общая картина"));
        assertTrue(payload.userPrompt().contains("### Accuracy"));
        assertTrue(payload.userPrompt().contains("### Reschedule rate"));
        assertTrue(payload.userPrompt().contains("### Rhythm stability"));
        assertTrue(payload.userPrompt().contains("Частые переносы перед дедлайном"));
        assertTrue(payload.userPrompt().contains("Ослабить перегруженные дни"));
        assertTrue(payload.fallbackSummary().available());
        assertEquals(PlanningQualitySummarySource.FALLBACK, payload.fallbackSummary().source());
        assertFalse(payload.fallbackSummary().headline().isBlank());
        assertFalse(payload.fallbackSummary().nextAction().isBlank());
    }

    @Test
    void fallsBackGracefullyForEmptySnapshot() {
        PlanningQualityAiPromptPayload payload = PlanningQualityPromptFactory.build(null);

        assertFalse(payload.systemPrompt().isBlank());
        assertFalse(payload.userPrompt().isBlank());
        assertTrue(payload.fallbackSummary().available());
        assertEquals(PlanningQualitySummarySource.FALLBACK, payload.fallbackSummary().source());
        assertFalse(payload.fallbackSummary().headline().isBlank());
    }
}
