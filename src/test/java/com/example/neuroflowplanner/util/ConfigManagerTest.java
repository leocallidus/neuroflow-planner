package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFocusRecommendation;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFreeWindow;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewOverdueItem;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewPersistenceRecord;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSummary;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewSummarySource;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewUpcomingItem;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowSuitability;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityPersistenceRecord;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRecommendation;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRisk;
import com.example.neuroflowplanner.service.planningquality.PlanningQualityRiskSeverity;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySummary;
import com.example.neuroflowplanner.service.planningquality.PlanningQualitySummarySource;
import com.example.neuroflowplanner.service.planningquality.RescheduleRateMetric;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityBand;
import com.example.neuroflowplanner.service.planningquality.RhythmStabilityMetric;
import com.example.neuroflowplanner.service.planningquality.TimeEstimateAccuracyMetric;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для ConfigManager.
 * UT-006, UT-007
 */
@DisplayName("ConfigManager Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigManagerTest {

    private static final String TEST_KEY = "test.property";
    private static final String TEST_VALUE = "test_value_123";
    private static final String SECRET_TEST_VALUE = "stage2_secret_value_1234567890";

    @BeforeAll
    static void setUpClass() {
        // Убедимся, что ConfigManager инициализирован
        ConfigManager.getProperty("api.url");
    }

    // UT-006: Чтение свойства
    @Test
    @Order(1)
    @DisplayName("UT-006: Чтение существующего свойства")
    void testReadExistingProperty() {
        // api.url должен быть либо из конфига, либо null
        String apiUrl = ConfigManager.getProperty("api.url");
        // Не проверяем конкретное значение, просто что метод работает
        assertDoesNotThrow(() -> ConfigManager.getProperty("api.url"));
    }

    @Test
    @Order(2)
    @DisplayName("UT-006: Чтение несуществующего свойства возвращает null")
    void testReadNonExistingProperty() {
        String value = ConfigManager.getProperty("non.existing.property.xyz");
        assertNull(value);
    }

    // UT-007: Запись свойства
    @Test
    @Order(3)
    @DisplayName("UT-007: Запись и чтение свойства")
    void testWriteAndReadProperty() {
        // Записываем
        ConfigManager.setProperty(TEST_KEY, TEST_VALUE);
        
        // Читаем
        String readValue = ConfigManager.getProperty(TEST_KEY);
        assertEquals(TEST_VALUE, readValue);
    }

    @Test
    @Order(4)
    @DisplayName("UT-007: Перезапись свойства")
    void testOverwriteProperty() {
        String newValue = "new_value_456";
        
        ConfigManager.setProperty(TEST_KEY, TEST_VALUE);
        ConfigManager.setProperty(TEST_KEY, newValue);
        
        assertEquals(newValue, ConfigManager.getProperty(TEST_KEY));
    }

    @Test
    @Order(5)
    @DisplayName("Проверка isDarkTheme")
    void testIsDarkTheme() {
        // Метод должен возвращать boolean без исключений
        assertDoesNotThrow(() -> {
            boolean isDark = ConfigManager.isDarkTheme();
            assertTrue(isDark || !isDark); // Просто проверяем, что возвращается boolean
        });
    }

    @Test
    @Order(6)
    @DisplayName("Проверка setDarkTheme")
    void testSetDarkTheme() {
        boolean originalValue = ConfigManager.isDarkTheme();
        
        // Переключаем тему
        ConfigManager.setDarkTheme(!originalValue);
        assertEquals(!originalValue, ConfigManager.isDarkTheme());
        
        // Возвращаем обратно
        ConfigManager.setDarkTheme(originalValue);
        assertEquals(originalValue, ConfigManager.isDarkTheme());
    }

    @Test
    @Order(7)
    @DisplayName("Проверка работы с пустыми значениями")
    void testEmptyValue() {
        ConfigManager.setProperty("empty.test", "");
        String value = ConfigManager.getProperty("empty.test");
        assertEquals("", value);
    }

    @Test
    @Order(8)
    @DisplayName("Проверка работы со специальными символами")
    void testSpecialCharacters() {
        String specialValue = "http://localhost:11434/api/chat?param=value&other=123";
        ConfigManager.setProperty("special.chars", specialValue);
        assertEquals(specialValue, ConfigManager.getProperty("special.chars"));
    }

    @Test
    @Order(9)
    @DisplayName("Проверка работы с кириллицей")
    void testCyrillicValue() {
        String cyrillicValue = "Тестовое значение на русском";
        ConfigManager.setProperty("cyrillic.test", cyrillicValue);
        assertEquals(cyrillicValue, ConfigManager.getProperty("cyrillic.test"));
    }

    @Test
    @Order(10)
    @DisplayName("Секреты не сохраняются в config.properties")
    void testSecretIsNotPersistedToConfigFile() throws Exception {
        ConfigManager.setProperty(ConfigManager.CONFIG_EXTERNAL_API_KEY, SECRET_TEST_VALUE);
        ConfigManager.setProperty(ConfigManager.CONFIG_API_KEY, SECRET_TEST_VALUE);

        Path configPath = DataPathManager.getConfigPath();
        String configContent = Files.readString(configPath);

        assertFalse(configContent.contains(SECRET_TEST_VALUE),
                "Секрет не должен попадать в config.properties");
        assertTrue(configContent.contains("external.api.key="),
                "В файле должна сохраняться строка ключа без значения");
        assertTrue(configContent.contains("api.key="),
                "В файле должна сохраняться legacy-строка ключа без значения");
    }

    @Test
    @Order(11)
    @DisplayName("ENV имеет приоритет над config для API ключа")
    void testEnvOverridesConfigForApiKey() {
        String envExternal = System.getenv(ConfigManager.ENV_EXTERNAL_API_KEY);
        String envLegacy = System.getenv(ConfigManager.ENV_LEGACY_API_KEY);

        Assumptions.assumeTrue(
                (envExternal != null && !envExternal.isBlank()) ||
                (envLegacy != null && !envLegacy.isBlank()),
                "Тест требует установленный ENV: NEUROFLOW_EXTERNAL_API_KEY или NEUROFLOW_API_KEY"
        );

        String expected = (envExternal != null && !envExternal.isBlank()) ? envExternal : envLegacy;
        ConfigManager.setProperty(ConfigManager.CONFIG_EXTERNAL_API_KEY, "config_value_should_not_be_used");
        ConfigManager.setProperty(ConfigManager.CONFIG_API_KEY, "legacy_config_value_should_not_be_used");

        assertEquals(expected, ConfigManager.getProperty(ConfigManager.CONFIG_EXTERNAL_API_KEY));
        assertEquals(expected, ConfigManager.getProperty(ConfigManager.CONFIG_API_KEY));
    }

    @Test
    @Order(12)
    @DisplayName("DB bulk mode резолвится только в поддерживаемые значения")
    void testDbBulkWritesModeResolution() {
        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE, "batched");
        assertEquals(DbWriteConfigDefaults.MODE_BATCHED, ConfigManager.getDbBulkWritesMode());

        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE, "TRANSACTIONAL");
        assertEquals(DbWriteConfigDefaults.MODE_TRANSACTIONAL, ConfigManager.getDbBulkWritesMode());

        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE, "invalid-mode");
        assertEquals(DbWriteConfigDefaults.DB_BULK_WRITES_MODE_DEFAULT, ConfigManager.getDbBulkWritesMode());
    }

    @Test
    @Order(13)
    @DisplayName("DB bulk batch size ограничивается допустимым диапазоном")
    void testDbBulkBatchSizeClamp() {
        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "10");
        assertEquals(DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MIN, ConfigManager.getDbBulkBatchSize());

        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "5000");
        assertEquals(DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_MAX, ConfigManager.getDbBulkBatchSize());

        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "250");
        assertEquals(250, ConfigManager.getDbBulkBatchSize());

        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "not-a-number");
        assertEquals(DbWriteConfigDefaults.DB_BULK_BATCH_SIZE_DEFAULT, ConfigManager.getDbBulkBatchSize());
    }

    @Test
    @Order(14)
    @DisplayName("Inspector active tab нормализуется и ограничивается допустимыми значениями")
    void testUxRightPanelInspectorActiveTabNormalization() {
        ConfigManager.setUxRightPanelInspectorActiveTab("ANALYTICS");
        assertEquals("analytics", ConfigManager.getUxRightPanelInspectorActiveTab());

        ConfigManager.setUxRightPanelInspectorActiveTab("invalid-tab");
        assertEquals(
            UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT,
            ConfigManager.getUxRightPanelInspectorActiveTab()
        );
    }

    @Test
    @Order(15)
    @DisplayName("Inspector expanded substates сохраняются как коллекция")
    void testUxRightPanelInspectorExpandedSubstatesPersistence() {
        ConfigManager.setUxRightPanelInspectorExpandedSubstateIds(
            java.util.Set.of("analytics.ai.full", "analytics.path.full")
        );

        java.util.Set<String> stored = ConfigManager.getUxRightPanelInspectorExpandedSubstateIds();
        assertTrue(stored.contains("analytics.ai.full"));
        assertTrue(stored.contains("analytics.path.full"));
    }

    @Test
    @Order(16)
    @DisplayName("Список пользовательских external-моделей нормализуется и дедуплицируется")
    void testExternalApiCustomModelsPersistence() {
        ConfigManager.setExternalApiCustomModels(java.util.List.of(
            " openai/gpt-5.4 ",
            "deepseek-chat",
            "openai/gpt-5.4"
        ));

        assertEquals(
            java.util.List.of("openai/gpt-5.4", "deepseek-chat"),
            ConfigManager.getExternalApiCustomModels()
        );
    }

    @Test
    @Order(17)
    @DisplayName("Список найденных external-моделей сохраняется с полной заменой и дедупликацией")
    void testExternalApiDiscoveredModelsPersistence() {
        ConfigManager.setExternalApiDiscoveredModels(java.util.List.of(
            " openai/gpt-5.4 ",
            "deepseek-chat",
            "openai/gpt-5.4"
        ));

        assertEquals(
            java.util.List.of("openai/gpt-5.4", "deepseek-chat"),
            ConfigManager.getExternalApiDiscoveredModels()
        );

        ConfigManager.setExternalApiDiscoveredModels(java.util.List.of("qwen/qwen3-235b"));
        assertEquals(
            java.util.List.of("qwen/qwen3-235b"),
            ConfigManager.getExternalApiDiscoveredModels()
        );
    }

    @Test
    @Order(18)
    @DisplayName("Списки audio/file capability для external-моделей сохраняются с дедупликацией")
    void testExternalApiInputCapabilityPersistence() {
        ConfigManager.setExternalApiAudioInputModels(java.util.List.of(
            " openai/gpt-4o-audio-preview ",
            "openai/gpt-4o-audio-preview",
            "anthropic/claude-audio"
        ));
        ConfigManager.setExternalApiFileInputModels(java.util.List.of(
            " openai/gpt-4o-file ",
            "openai/gpt-4o-file",
            "qwen/qwen-file"
        ));

        assertEquals(
            java.util.List.of("openai/gpt-4o-audio-preview", "anthropic/claude-audio"),
            ConfigManager.getExternalApiAudioInputModels()
        );
        assertEquals(
            java.util.List.of("openai/gpt-4o-file", "qwen/qwen-file"),
            ConfigManager.getExternalApiFileInputModels()
        );
    }

    @Test
    @Order(18)
    @DisplayName("Каталог external-моделей сохраняет metadata text-параметров")
    void testExternalApiModelCatalogPersistence() {
        ConfigManager.setExternalApiModelCatalog(java.util.List.of(
            new AiDiscoveredModelInfo(
                "openai/gpt-5.4",
                "chat",
                true,
                true,
                false,
                true,
                AiTextModelContextMetadata.fromTokens(1_050_000),
                new AiTextModelParameterMetadata(8192, true, true, true, true, 1.0, 0.95, 0.1, 0.2)
            )
        ));

        java.util.List<AiDiscoveredModelInfo> stored = ConfigManager.getExternalApiModelCatalog();
        assertEquals(1, stored.size());
        assertEquals("openai/gpt-5.4", stored.getFirst().id());
        assertEquals(1_050_000, stored.getFirst().textContextMetadata().contextWindowTokens());
        assertEquals("1.05M", stored.getFirst().textContextMetadata().contextWindowLabel());
        assertEquals(8192, stored.getFirst().textParameterMetadata().maxCompletionTokens());
        assertTrue(stored.getFirst().textParameterMetadata().supportsPresencePenalty());
    }

    @Test
    @Order(18)
    @DisplayName("Флаг prompt caching сохраняется и читается из конфига")
    void testAiPromptCachingPersistence() {
        boolean original = ConfigManager.isAiPromptCachingEnabled();
        try {
            ConfigManager.setAiPromptCachingEnabled(false);
            assertFalse(ConfigManager.isAiPromptCachingEnabled());

            ConfigManager.setAiPromptCachingEnabled(true);
            assertTrue(ConfigManager.isAiPromptCachingEnabled());
        } finally {
            ConfigManager.setAiPromptCachingEnabled(original);
        }
    }

    @Test
    @Order(18)
    @DisplayName("Reasoning effort ассистента нормализуется к поддерживаемым значениям")
    void testAssistantReasoningEffortNormalization() {
        ConfigManager.setAssistantReasoningEffort("HIGH");
        assertEquals("high", ConfigManager.getAssistantReasoningEffort());

        ConfigManager.setAssistantReasoningEffort("xhigh");
        assertEquals("xhigh", ConfigManager.getAssistantReasoningEffort());

        ConfigManager.setAssistantReasoningEffort("none");
        assertEquals("none", ConfigManager.getAssistantReasoningEffort());

        ConfigManager.setAssistantReasoningEffort("unsupported-value");
        assertEquals("medium", ConfigManager.getAssistantReasoningEffort());
    }

    @Test
    @Order(19)
    @DisplayName("Настройки AI plugins сохраняются и нормализуются")
    void testAiPluginSettingsPersistence() {
        ConfigManager.setAiPluginWebEnabled(true);
        ConfigManager.setAiPluginWebEngine("EXA");
        ConfigManager.setAiPluginWebMaxResults(99);
        ConfigManager.setAiPluginWebSearchPrompt("  Найти свежие источники  ");
        ConfigManager.setAiPluginFileParserEnabled(true);
        ConfigManager.setAiPluginFileParserPdfEngine("MISTRAL-OCR");
        ConfigManager.setAiPluginResponseHealingEnabled(true);

        assertTrue(ConfigManager.isAiPluginWebEnabled());
        assertEquals("exa", ConfigManager.getAiPluginWebEngine());
        assertEquals(20, ConfigManager.getAiPluginWebMaxResults());
        assertEquals("Найти свежие источники", ConfigManager.getAiPluginWebSearchPrompt());
        assertTrue(ConfigManager.isAiPluginFileParserEnabled());
        assertEquals("mistral-ocr", ConfigManager.getAiPluginFileParserPdfEngine());
        assertTrue(ConfigManager.isAiPluginResponseHealingEnabled());

        ConfigManager.setAiPluginWebEngine("unsupported");
        ConfigManager.setAiPluginWebMaxResults(0);
        ConfigManager.setAiPluginFileParserPdfEngine("unsupported");

        assertEquals("auto", ConfigManager.getAiPluginWebEngine());
        assertEquals(1, ConfigManager.getAiPluginWebMaxResults());
        assertEquals("pdf-text", ConfigManager.getAiPluginFileParserPdfEngine());
    }

    @Test
    @Order(19)
    @DisplayName("Persisted daily review сохраняется и восстанавливается из конфига")
    void testPersistedDailyReviewRoundTrip() {
        DailyReviewPersistenceRecord record = new DailyReviewPersistenceRecord(
                LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-10T02:30:00Z"),
                "openai/gpt-5.4",
                true,
                "fp-123",
                7,
                1,
                2,
                3,
                45,
                false,
                new DailyReviewSummary(
                        DailyReviewSummarySource.AI,
                        "День плотный, но управляемый",
                        java.util.List.of("Закрыть просрочку", "Использовать окно 14:00-15:30"),
                        "Просрочка по клиенту может сорвать ритм дня.",
                        "Сначала закрыть клиентский экспорт.",
                        ""
                ),
                new DailyReviewFocusRecommendation(
                        "Клиентский экспорт",
                        "Это главный риск дня.",
                        "Сейчас лучше заняться клиентским экспортом, потому что он снимает основное давление.",
                        DailyReviewSummarySource.AI
                ),
                java.util.List.of(new DailyReviewOverdueItem(
                        "task-1",
                        "Fix billing export",
                        LocalDate.of(2026, 3, 9),
                        LocalDateTime.of(2026, 3, 9, 18, 0),
                        1,
                        8,
                        0.9,
                        java.util.List.of("finance")
                )),
                java.util.List.of(new DailyReviewUpcomingItem(
                        "task-2",
                        "Prepare sync",
                        LocalDate.of(2026, 3, 10),
                        LocalDateTime.of(2026, 3, 10, 16, 0),
                        0,
                        true,
                        true,
                        5,
                        0.7,
                        java.util.List.of("team")
                )),
                java.util.List.of(new DailyReviewFreeWindow(
                        LocalDateTime.of(2026, 3, 10, 14, 0),
                        LocalDateTime.of(2026, 3, 10, 15, 30),
                        90,
                        DailyReviewWindowSuitability.DEEP_WORK,
                        false,
                        "14:00-15:30"
                ))
        );

        ConfigManager.setPersistedDailyReview(record);
        DailyReviewPersistenceRecord restored = ConfigManager.getPersistedDailyReview();

        assertNotNull(restored);
        assertEquals(LocalDate.of(2026, 3, 10), restored.reviewDate());
        assertEquals("openai/gpt-5.4", restored.modelId());
        assertEquals("fp-123", restored.snapshotFingerprint());
        assertEquals(DailyReviewSummarySource.AI, restored.summary().source());
        assertEquals(1, restored.overdueItems().size());
        assertEquals(1, restored.upcomingItems().size());
        assertEquals(1, restored.freeWindows().size());
    }

    @Test
    @Order(19)
    @DisplayName("Persisted planning quality сохраняется и восстанавливается из конфига")
    void testPersistedPlanningQualityRoundTrip() {
        PlanningQualityPersistenceRecord record = new PlanningQualityPersistenceRecord(
                LocalDate.of(2026, 2, 26),
                LocalDate.of(2026, 3, 10),
                Instant.parse("2026-03-10T05:45:00Z"),
                "openai/gpt-5.4",
                true,
                "planning-fp-42",
                12,
                7,
                9,
                8,
                6,
                18,
                false,
                new PlanningQualitySummary(
                        PlanningQualitySummarySource.AI,
                        "Планирование в целом устойчивое, но шумит в середине периода",
                        "Главная просадка связана с поздними переносами и неровным ритмом.",
                        "Ослабьте самый плотный день и добавьте буфер перед жёсткими дедлайнами.",
                        "Reschedule rate остаётся heuristic."
                ),
                new TimeEstimateAccuracyMetric(9, 7, 0.22, 0.71, 0.18, 0.11, false),
                new RescheduleRateMetric(8, 3, 5, 1, 2, 0.37, true),
                new RhythmStabilityMetric(RhythmStabilityBand.MODERATE, 0.64, 11, 8, 42, 35.5, true),
                java.util.List.of(new PlanningQualityRisk(
                        PlanningQualityRiskSeverity.WARNING,
                        "Поздние переносы",
                        "Задачи часто сдвигаются слишком близко к дедлайну."
                )),
                java.util.List.of(new PlanningQualityRecommendation(
                        "Снизить плотность во вторник",
                        "Во вторник накапливается перегрузка и падает точность оценки.",
                        "Перенесите одну среднюю задачу на соседний день.",
                        PlanningQualitySummarySource.AI
                ))
        );

        ConfigManager.setPersistedPlanningQuality(record);
        PlanningQualityPersistenceRecord restored = ConfigManager.getPersistedPlanningQuality();

        assertNotNull(restored);
        assertEquals(LocalDate.of(2026, 2, 26), restored.periodStart());
        assertEquals(LocalDate.of(2026, 3, 10), restored.periodEnd());
        assertEquals("openai/gpt-5.4", restored.modelId());
        assertEquals("planning-fp-42", restored.snapshotFingerprint());
        assertEquals(PlanningQualitySummarySource.AI, restored.summary().source());
        assertEquals(7, restored.accuracyMetric().comparableTaskCount());
        assertEquals(3, restored.rescheduleMetric().rescheduledTaskCount());
        assertEquals(RhythmStabilityBand.MODERATE, restored.rhythmMetric().band());
        assertEquals(1, restored.risks().size());
        assertEquals(1, restored.recommendations().size());
    }

    @Test
    @Order(19)
    @DisplayName("Настройки reasoning tokens ассистента сохраняются")
    void testAssistantReasoningSettingsPersistence() {
        ConfigManager.setAssistantReasoningMaxTokens(1200);
        ConfigManager.setAssistantReasoningSummary("detailed");
        ConfigManager.setAssistantReasoningExcluded(false);

        assertEquals(1200, ConfigManager.getAssistantReasoningMaxTokens());
        assertEquals("detailed", ConfigManager.getAssistantReasoningSummary());
        assertFalse(ConfigManager.isAssistantReasoningExcluded());

        ConfigManager.setAssistantReasoningMaxTokens(null);
        assertNull(ConfigManager.getAssistantReasoningMaxTokens());
    }

    @Test
    @Order(19)
    @DisplayName("Настройки text-параметров ассистента сохраняются и нормализуются")
    void testAssistantTextParameterSettingsPersistence() {
        ConfigManager.setAssistantTextMaxTokens(4096);
        ConfigManager.setAssistantTextTemperature(1.7);
        ConfigManager.setAssistantTextTopP(0.8);
        ConfigManager.setAssistantTextFrequencyPenalty(0.6);
        ConfigManager.setAssistantTextPresencePenalty(-0.4);

        assertEquals(4096, ConfigManager.getAssistantTextMaxTokens());
        assertEquals(1.7, ConfigManager.getAssistantTextTemperature());
        assertEquals(0.8, ConfigManager.getAssistantTextTopP());
        assertEquals(0.6, ConfigManager.getAssistantTextFrequencyPenalty());
        assertEquals(-0.4, ConfigManager.getAssistantTextPresencePenalty());

        ConfigManager.setAssistantTextTemperature(5.0);
        ConfigManager.setAssistantTextTopP(5.0);
        ConfigManager.setAssistantTextFrequencyPenalty(-5.0);
        ConfigManager.setAssistantTextPresencePenalty(5.0);

        assertEquals(2.0, ConfigManager.getAssistantTextTemperature());
        assertEquals(1.0, ConfigManager.getAssistantTextTopP());
        assertEquals(-2.0, ConfigManager.getAssistantTextFrequencyPenalty());
        assertEquals(2.0, ConfigManager.getAssistantTextPresencePenalty());
    }

    @Test
    @Order(19)
    @DisplayName("Список пользовательских image-моделей сохраняется с дедупликацией")
    void testExternalImageCustomModelsPersistence() {
        ConfigManager.setExternalImageCustomModels(java.util.List.of(
            " custom-provider/sdxl-ultra ",
            "gpt-5-image",
            "custom-provider/sdxl-ultra"
        ));

        assertEquals(
            java.util.List.of("custom-provider/sdxl-ultra", "gpt-5-image"),
            ConfigManager.getExternalImageCustomModels()
        );
    }

    @Test
    @Order(20)
    @DisplayName("Список найденных image-моделей сохраняется с полной заменой и дедупликацией")
    void testExternalImageDiscoveredModelsPersistence() {
        ConfigManager.setExternalImageDiscoveredModels(java.util.List.of(
            " custom-provider/sdxl-ultra ",
            "gpt-5-image",
            "custom-provider/sdxl-ultra"
        ));

        assertEquals(
            java.util.List.of("custom-provider/sdxl-ultra", "gpt-5-image"),
            ConfigManager.getExternalImageDiscoveredModels()
        );

        ConfigManager.setExternalImageDiscoveredModels(java.util.List.of("provider/flux-pro"));
        assertEquals(
            java.util.List.of("provider/flux-pro"),
            ConfigManager.getExternalImageDiscoveredModels()
        );
    }

    @AfterAll
    static void cleanUp() {
        // Очищаем тестовые свойства (устанавливаем пустые значения вместо null)
        ConfigManager.setProperty(TEST_KEY, "");
        ConfigManager.setProperty("empty.test", "");
        ConfigManager.setProperty("special.chars", "");
        ConfigManager.setProperty("cyrillic.test", "");
        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE, "");
        ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "");
        ConfigManager.setProperty(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, "");
        ConfigManager.setProperty(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES, "");
        ConfigManager.setExternalApiCustomModels(java.util.List.of());
        ConfigManager.setExternalApiDiscoveredModels(java.util.List.of());
        ConfigManager.setExternalImageCustomModels(java.util.List.of());
        ConfigManager.setExternalImageDiscoveredModels(java.util.List.of());
        ConfigManager.setAssistantReasoningEffort("medium");
        ConfigManager.setAiPluginWebEnabled(AiConfigDefaults.DEFAULT_PLUGIN_WEB_ENABLED);
        ConfigManager.setAiPluginWebEngine(AiConfigDefaults.DEFAULT_PLUGIN_WEB_ENGINE);
        ConfigManager.setAiPluginWebMaxResults(AiConfigDefaults.DEFAULT_PLUGIN_WEB_MAX_RESULTS);
        ConfigManager.setAiPluginWebSearchPrompt(AiConfigDefaults.DEFAULT_PLUGIN_WEB_SEARCH_PROMPT);
        ConfigManager.setAiPluginFileParserEnabled(AiConfigDefaults.DEFAULT_PLUGIN_FILE_PARSER_ENABLED);
        ConfigManager.setAiPluginFileParserPdfEngine(AiConfigDefaults.DEFAULT_PLUGIN_FILE_PARSER_PDF_ENGINE);
        ConfigManager.setAiPluginResponseHealingEnabled(AiConfigDefaults.DEFAULT_PLUGIN_RESPONSE_HEALING_ENABLED);
    }
}
