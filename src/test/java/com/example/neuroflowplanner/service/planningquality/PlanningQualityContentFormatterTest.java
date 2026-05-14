package com.example.neuroflowplanner.service.planningquality;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQualityContentFormatterTest {

    @Test
    void rendersMarkdownWithKeySections() {
        PlanningQualityResult result = buildResult();

        String markdown = PlanningQualityContentFormatter.toMarkdown(result);

        assertTrue(markdown.contains("# Качество планирования"), "title should be present");
        assertTrue(markdown.contains("## Сводка качества планирования"), "summary section should be present");
        assertTrue(markdown.contains("## Метрики"), "metrics section should be present");
        assertTrue(markdown.contains("### Точность оценки времени"), "accuracy metric should be present");
        assertTrue(markdown.contains("### Доля переносов"), "reschedule metric should be present");
        assertTrue(markdown.contains("### Стабильность ритма"), "rhythm metric should be present");
        assertTrue(markdown.contains("## Проблемные паттерны"), "risks section should be present");
        assertTrue(markdown.contains("## Что улучшить"), "recommendations section should be present");
        assertTrue(markdown.contains("Сильный разброс оценки"), "risk content should be present");
        assertTrue(markdown.contains("Добавляйте буфер"), "recommendation content should be present");
    }

    @Test
    void rendersChatSeedPromptFromDashboard() {
        PlanningQualityResult result = buildResult();

        String prompt = PlanningQualityContentFormatter.toChatSeedPrompt(result);

        assertTrue(prompt.contains("Используй этот дашборд качества планирования как стартовый контекст."));
        assertTrue(prompt.contains("1. Коротко оцени общее качество планирования."));
        assertTrue(prompt.contains("3. Предложи 1-2 конкретных изменения"));
    }

    private PlanningQualityResult buildResult() {
        PlanningQualitySnapshot snapshot = new PlanningQualitySnapshot(
                LocalDate.of(2026, 2, 26),
                LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-10T08:40:00Z"),
                new PlanningQualitySummary(
                        PlanningQualitySummarySource.AI,
                        "Планирование стало стабильнее, но переносы всё ещё мешают темпу.",
                        "Основная просадка сейчас в том, что часть задач недооценивается и переносится слишком поздно.",
                        "Добавляйте буфер перед задачами с жёстким дедлайном и пересматривайте оценки для длинных задач.",
                        "Часть выводов всё ещё approximate из-за ограниченного event log переносов."
                ),
                new TimeEstimateAccuracyMetric(
                        18,
                        12,
                        0.27,
                        0.58,
                        0.34,
                        0.12,
                        false
                ),
                new RescheduleRateMetric(
                        16,
                        7,
                        9,
                        3,
                        2,
                        0.44,
                        true
                ),
                new RhythmStabilityMetric(
                        RhythmStabilityBand.MODERATE,
                        0.63,
                        10,
                        7,
                        48,
                        0.29,
                        false
                ),
                List.of(
                        new PlanningQualityRisk(
                                PlanningQualityRiskSeverity.WARNING,
                                "Сильный разброс оценки",
                                "Часть длинных задач всё ещё получает слишком оптимистичную оценку."
                        )
                ),
                List.of(
                        new PlanningQualityRecommendation(
                                "Добавляйте буфер к длинным задачам",
                                "Это снизит число поздних переносов и стабилизирует ритм.",
                                "Для задач длиннее часа закладывайте дополнительный буфер 15-20%.",
                                PlanningQualitySummarySource.AI
                        )
                ),
                List.of(),
                14,
                9,
                18,
                11,
                10,
                26,
                false
        );
        return new PlanningQualityResult(snapshot, snapshot.generatedAt(), "openai/gpt-5.4", true, false);
    }
}
