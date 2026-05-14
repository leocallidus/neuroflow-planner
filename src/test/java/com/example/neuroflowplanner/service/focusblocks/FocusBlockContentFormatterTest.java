package com.example.neuroflowplanner.service.focusblocks;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FocusBlockContentFormatterTest {

    @Test
    void rendersMarkdownWithKeySections() {
        FocusBlockRecommendationResult result = buildResult();

        String markdown = FocusBlockContentFormatter.toMarkdown(result);

        assertTrue(markdown.contains("# Рекомендации фокус-блоков"), "title should be present");
        assertTrue(markdown.contains("## Следующий рекомендуемый блок"), "next block section should be present");
        assertTrue(markdown.contains("## Объяснение рекомендации"), "explanation section should be present");
        assertTrue(markdown.contains("## Лучшие фокус-окна"), "focus windows section should be present");
        assertTrue(markdown.contains("## Короткие окна"), "short windows section should be present");
        assertTrue(markdown.contains("## Риски"), "risks section should be present");
        assertTrue(markdown.contains("## Профиль продуктивности"), "profile section should be present");
        assertTrue(markdown.contains("Утренний deep work"), "next recommendation should be included");
        assertTrue(markdown.contains("После 16:00 растёт риск переключений"), "risk should be included");
    }

    @Test
    void rendersChatSeedPromptFromRecommendationSnapshot() {
        FocusBlockRecommendationResult result = buildResult();

        String prompt = FocusBlockContentFormatter.toChatSeedPrompt(result);

        assertTrue(prompt.contains("Используй эти рекомендации фокус-блоков как стартовый контекст"));
        assertTrue(prompt.contains("1. Коротко оцени лучший следующий блок."));
        assertTrue(prompt.contains("3. Предложи конкретный следующий шаг"));
    }

    private FocusBlockRecommendationResult buildResult() {
        FocusBlockRecommendation nextBlock = new FocusBlockRecommendation(
                "Утренний deep work",
                "Окно совпадает с вашим сильным часом концентрации и не конфликтует с ближайшими дедлайнами.",
                "Закрой мессенджеры и начни с самой тяжёлой части отчёта.",
                LocalDateTime.of(2026, 3, 10, 9, 30),
                LocalDateTime.of(2026, 3, 10, 10, 45),
                75,
                FocusBlockType.DEEP_FOCUS,
                0.91,
                0.84,
                true,
                List.of()
        );

        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-10T08:40:00Z"),
                new FocusBlockExplanation(
                        FocusBlockSummarySource.AI,
                        "Сейчас лучше взять длинный блок до начала дневного шума.",
                        "Утреннее окно даёт лучший шанс закрыть тяжёлую работу без переключений.",
                        "Подготовьте материалы и войдите в блок без входящих отвлечений.",
                        "История трекинга за последние дни ещё не идеальна, поэтому оценка умеренно консервативна."
                ),
                new FocusProductivityProfile(
                        Instant.parse("2026-03-10T08:40:00Z"),
                        0.73,
                        0.28,
                        58,
                        72,
                        310,
                        6,
                        false,
                        List.of(new FocusDayScore(java.time.DayOfWeek.TUESDAY, 0.77, 4, 190)),
                        List.of(new FocusHourScore(9, 0.82, 6, 145, 0.14))
                ),
                List.of(),
                List.of(
                        nextBlock,
                        new FocusBlockRecommendation(
                                "Послеобеденный блок",
                                "",
                                "",
                                LocalDateTime.of(2026, 3, 10, 13, 30),
                                LocalDateTime.of(2026, 3, 10, 14, 15),
                                45,
                                FocusBlockType.DEEP_FOCUS,
                                0.74,
                                0.68,
                                false,
                                List.of()
                        )
                ),
                List.of(
                        new FocusBlockRecommendation(
                                "Короткое окно на ревью",
                                "",
                                "",
                                LocalDateTime.of(2026, 3, 10, 16, 0),
                                LocalDateTime.of(2026, 3, 10, 16, 25),
                                25,
                                FocusBlockType.LIGHT_FOCUS,
                                0.61,
                                0.57,
                                false,
                                List.of()
                        )
                ),
                nextBlock,
                List.of(
                        new FocusBlockRisk(
                                FocusBlockRiskLevel.WARNING,
                                "Пик отвлечений после обеда",
                                "После 16:00 растёт риск переключений и потери темпа."
                        )
                ),
                false
        );
        return new FocusBlockRecommendationResult(snapshot, snapshot.generatedAt(), "openai/gpt-5.4", true, false);
    }
}
