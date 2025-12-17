package com.example.neuroflowplanner.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для MoodAnalysisService.
 * UT-010
 */
@DisplayName("MoodAnalysisService Tests")
class MoodAnalysisServiceTest {

    private MoodAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new MoodAnalysisService();
    }

    // UT-010: analyze
    @Test
    @DisplayName("UT-010: Анализ позитивного текста")
    void testAnalyzePositiveText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Сегодня отличный день, чувствую себя хорошо и продуктивно!",
            7
        );

        assertNotNull(result);
        assertEquals("Позитивное", result.label);
        assertTrue(result.adjustedScore >= 7);
    }

    @Test
    @DisplayName("UT-010: Анализ негативного текста")
    void testAnalyzeNegativeText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Устал, много стресса на работе, чувствую себя плохо",
            4
        );

        assertNotNull(result);
        assertEquals("Негативное", result.label);
        assertTrue(result.adjustedScore <= 4);
    }

    @Test
    @DisplayName("UT-010: Анализ смешанного текста")
    void testAnalyzeMixedText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "День был хороший и отлично, но устал и стресс",
            5
        );

        assertNotNull(result);
        assertEquals("Смешанное", result.label);
    }

    @Test
    @DisplayName("UT-010: Анализ нейтрального текста")
    void testAnalyzeNeutralText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Обычный день, ничего особенного",
            5
        );

        assertNotNull(result);
        assertNotNull(result.label);
        assertEquals(5, result.adjustedScore);
    }

    @Test
    @DisplayName("UT-010: Анализ пустого текста")
    void testAnalyzeEmptyText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze("", 6);

        assertNotNull(result);
        assertEquals(6, result.adjustedScore);
        assertEquals("Хорошее", result.label);
    }

    @Test
    @DisplayName("UT-010: Анализ null текста")
    void testAnalyzeNullText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(null, 5);

        assertNotNull(result);
        assertEquals(5, result.adjustedScore);
    }

    @ParameterizedTest
    @CsvSource({
        "10, Отличное",
        "8, Отличное",
        "7, Хорошее",
        "6, Хорошее",
        "5, Нормальное",
        "4, Нормальное",
        "3, Так себе",
        "2, Так себе",
        "1, Плохое"
    })
    @DisplayName("UT-010: Метки для разных оценок")
    void testLabelsForScores(int score, String expectedLabel) {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze("", score);

        assertEquals(expectedLabel, result.label);
    }

    @Test
    @DisplayName("Позитивный текст повышает низкую оценку")
    void testPositiveTextIncreasesLowScore() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Чувствую себя отлично, день прошёл хорошо!",
            3
        );

        assertTrue(result.adjustedScore >= 3, "Оценка не должна уменьшиться");
        // При позитивном тексте и низкой оценке, оценка может увеличиться
    }

    @Test
    @DisplayName("Негативный текст понижает высокую оценку")
    void testNegativeTextDecreasesHighScore() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Устал, стресс, всё плохо",
            8
        );

        assertTrue(result.adjustedScore <= 8, "Оценка не должна увеличиться");
    }

    @Test
    @DisplayName("Анализ текста на английском")
    void testAnalyzeEnglishText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "I feel happy and excited today, everything is great!",
            7
        );

        assertNotNull(result);
        assertEquals("Позитивное", result.label);
    }

    @Test
    @DisplayName("Анализ текста на английском (негативный)")
    void testAnalyzeEnglishNegativeText() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "I'm tired and stressed, feeling bad",
            4
        );

        assertNotNull(result);
        assertEquals("Негативное", result.label);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    @DisplayName("Оценка остаётся в допустимом диапазоне")
    void testScoreRemainsInRange(int initialScore) {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze(
            "Тестовый текст с разными словами хорошо плохо",
            initialScore
        );

        assertTrue(result.adjustedScore >= 1, "Оценка не должна быть меньше 1");
        assertTrue(result.adjustedScore <= 10, "Оценка не должна быть больше 10");
    }

    @Test
    @DisplayName("Результат содержит корректные поля")
    void testResultStructure() {
        MoodAnalysisService.MoodAnalysisResult result = service.analyze("Тест", 5);

        assertNotNull(result.label);
        assertFalse(result.label.isEmpty());
        assertTrue(result.adjustedScore >= 1 && result.adjustedScore <= 10);
    }

    @Test
    @DisplayName("Регистронезависимый анализ")
    void testCaseInsensitiveAnalysis() {
        MoodAnalysisService.MoodAnalysisResult result1 = service.analyze("ОТЛИЧНО", 5);
        MoodAnalysisService.MoodAnalysisResult result2 = service.analyze("отлично", 5);
        MoodAnalysisService.MoodAnalysisResult result3 = service.analyze("Отлично", 5);

        assertEquals(result1.label, result2.label);
        assertEquals(result2.label, result3.label);
    }
}
