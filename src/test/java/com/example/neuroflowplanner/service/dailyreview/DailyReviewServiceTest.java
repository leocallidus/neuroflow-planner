package com.example.neuroflowplanner.service.dailyreview;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DailyReviewServiceTest {

    @Test
    void restoresPersistedReviewAfterRestartWhenFingerprintMatches() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));
        CountingBuilder builder = new CountingBuilder(reviewDate);
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина дня
                        - День требует собранного старта.
                        ## Риски
                        - Просрочки могут съесть первую половину дня.
                        ## Приоритеты
                        - Закрыть просрочку по клиентскому экспорту.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться клиентским экспортом, потому что это снимет главный риск дня.
                        """, "openai/gpt-5.4")
        );

        DailyReviewService firstService = new DailyReviewService(builder, () -> aiClient, clock, Duration.ofMinutes(20));
        DailyReviewResult first = firstService.getReview(reviewDate, true).join();
        assertTrue(first.aiUsed());
        assertEquals(1, aiClient.sendCount.get());

        StubAiClient restartedAiClient = new StubAiClient("openai/gpt-5.4", AiResponse.error("should not be called"));
        DailyReviewService restartedService = new DailyReviewService(new CountingBuilder(reviewDate), () -> restartedAiClient, clock, Duration.ofMinutes(20));
        DailyReviewResult restored = restartedService.getReview(reviewDate, false).join();

        assertTrue(restored.fromCache());
        assertEquals(DailyReviewSummarySource.AI, restored.summary().source());
        assertEquals(0, restartedAiClient.sendCount.get());
    }

    @Test
    void refreshesPersistedReviewWhenSnapshotFingerprintChanged() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));

        DailyReviewService firstService = new DailyReviewService(
                new CountingBuilder(reviewDate),
                () -> new StubAiClient("openai/gpt-5.4", AiResponse.success("""
                        ## Общая картина дня
                        - Старый обзор.
                        ## Риски
                        - Старый риск.
                        ## Приоритеты
                        - Старый приоритет.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться старой задачей, потому что она главная.
                        """, "openai/gpt-5.4")),
                clock,
                Duration.ofMinutes(20)
        );
        firstService.getReview(reviewDate, true).join();

        DifferentSnapshotBuilder changedBuilder = new DifferentSnapshotBuilder(reviewDate);
        StubAiClient refreshedAiClient = new StubAiClient("openai/gpt-5.4", AiResponse.success("""
                ## Общая картина дня
                - Новый обзор после изменения задач.
                ## Риски
                - Новый риск.
                ## Приоритеты
                - Новый приоритет.
                ## Фокус-рекомендация
                - Сейчас лучше заняться новой задачей, потому что она теперь важнее.
                """, "openai/gpt-5.4"));
        DailyReviewService restartedService = new DailyReviewService(changedBuilder, () -> refreshedAiClient, clock, Duration.ofMinutes(20));

        DailyReviewResult refreshed = restartedService.getReview(reviewDate, false).join();

        assertFalse(refreshed.fromCache());
        assertTrue(refreshed.aiUsed());
        assertEquals(1, refreshedAiClient.sendCount.get());
        assertTrue(refreshed.summary().headline().contains("Новый обзор"));
    }

    @Test
    void returnsCachedResultWhenStillFresh() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));
        CountingBuilder builder = new CountingBuilder(reviewDate);
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина дня
                        - День требует собранного старта.
                        ## Риски
                        - Просрочки могут съесть первую половину дня.
                        ## Приоритеты
                        - Закрыть просрочку по клиентскому экспорту.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться клиентским экспортом, потому что это снимет главный риск дня.
                        """, "openai/gpt-5.4")
        );
        DailyReviewService service = new DailyReviewService(builder, () -> aiClient, clock, Duration.ofMinutes(20));

        DailyReviewResult first = service.getReview(reviewDate, false).join();
        DailyReviewResult second = service.getReview(reviewDate, false).join();

        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(2, builder.buildCount.get());
        assertEquals(1, aiClient.sendCount.get());
    }

    @Test
    void refreshesWhenCachedReviewBelongsToPreviousDay() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate firstDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));

        CountingBuilder firstBuilder = new CountingBuilder(firstDate);
        StubAiClient firstAiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина дня
                        - Обзор за первый день.
                        ## Риски
                        - Риск первого дня.
                        ## Приоритеты
                        - Приоритет первого дня.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться первой задачей, потому что она главная.
                        """, "openai/gpt-5.4")
        );
        DailyReviewService firstService = new DailyReviewService(firstBuilder, () -> firstAiClient, clock, Duration.ofMinutes(20));
        firstService.getReview(firstDate, true).join();

        clock.setInstant(Instant.parse("2026-03-11T02:00:00Z"));
        LocalDate secondDate = LocalDate.of(2026, 3, 11);
        CountingBuilder secondBuilder = new CountingBuilder(secondDate);
        StubAiClient secondAiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина дня
                        - Обзор за новый день.
                        ## Риски
                        - Риск второго дня.
                        ## Приоритеты
                        - Приоритет второго дня.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться новой задачей, потому что начался новый день.
                        """, "openai/gpt-5.4")
        );
        DailyReviewService secondService = new DailyReviewService(secondBuilder, () -> secondAiClient, clock, Duration.ofMinutes(20));

        DailyReviewResult result = secondService.getReview(secondDate, false).join();

        assertFalse(result.fromCache());
        assertEquals(secondDate, result.reviewDate());
        assertEquals(1, secondAiClient.sendCount.get());
    }

    @Test
    void buildsAiEnhancedResultWhenStructuredAnswerAvailable() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));
        CountingBuilder builder = new CountingBuilder(reviewDate);
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина дня
                        - День плотный, но управляемый.
                        - Самое важное окно находится до полудня.
                        ## Риски
                        - Просроченная задача по клиенту может сорвать остальные переключения.
                        ## Приоритеты
                        - Сначала закрыть клиентский экспорт.
                        - Затем подготовить заметки к синку.
                        ## Фокус-рекомендация
                        - Сейчас лучше заняться клиентским экспортом, потому что это главный источник давления на день.
                        """, "openai/gpt-5.4")
        );
        DailyReviewService service = new DailyReviewService(builder, () -> aiClient, clock, Duration.ofMinutes(20));

        DailyReviewResult result = service.getReview(reviewDate, true).join();

        assertTrue(result.aiUsed());
        assertEquals("openai/gpt-5.4", result.modelId());
        assertEquals(DailyReviewSummarySource.AI, result.summary().source());
        assertEquals("День плотный, но управляемый.", result.summary().headline());
        assertTrue(result.summary().bullets().contains("Сначала закрыть клиентский экспорт."));
        assertEquals(DailyReviewSummarySource.AI, result.focusRecommendation().source());
        assertTrue(result.focusRecommendation().suggestedNextStep().contains("Сейчас лучше заняться"));
    }

    @Test
    void fallsBackWhenAiUnavailableOrResponseIsUnstructured() {
        ConfigManager.setPersistedDailyReview(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-10T02:00:00Z"), ZoneId.of("UTC"));
        CountingBuilder builder = new CountingBuilder(reviewDate);
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("Просто общий ответ без требуемых секций", "openai/gpt-5.4")
        );
        DailyReviewService service = new DailyReviewService(builder, () -> aiClient, clock, Duration.ofMinutes(20));

        DailyReviewResult result = service.getReview(reviewDate, true).join();

        assertFalse(result.aiUsed());
        assertEquals(DailyReviewSummarySource.FALLBACK, result.summary().source());
        assertEquals(DailyReviewSummarySource.FALLBACK, result.focusRecommendation().source());
        assertFalse(result.summary().headline().isBlank());
    }

    private static class CountingBuilder extends DailyReviewBuilder {
        private final LocalDate reviewDate;
        private final AtomicInteger buildCount = new AtomicInteger();

        private CountingBuilder(LocalDate reviewDate) {
            this.reviewDate = reviewDate;
        }

        @Override
        public DailyReviewSnapshot buildForDate(LocalDate ignored) {
            buildCount.incrementAndGet();
            return new DailyReviewSnapshot(
                    reviewDate,
                    Instant.parse("2026-03-10T02:00:00Z"),
                    4,
                    1,
                    1,
                    2,
                    25,
                    false,
                    null,
                    List.of(new DailyReviewOverdueItem(
                            "task-1",
                            "Fix billing export",
                            reviewDate.minusDays(1),
                            LocalDateTime.of(reviewDate.minusDays(1), java.time.LocalTime.of(18, 0)),
                            1,
                            8,
                            0.9,
                            List.of("finance")
                    )),
                    List.of(new DailyReviewUpcomingItem(
                            "task-2",
                            "Prepare team sync",
                            reviewDate,
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(16, 0)),
                            0,
                            true,
                            true,
                            5,
                            0.7,
                            List.of("team")
                    )),
                    List.of(new DailyReviewWorkInterval(
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(9, 0)),
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(18, 0)),
                            540,
                            true,
                            "09:00-18:00"
                    )),
                    List.of(new DailyReviewTimeBlock(
                            "task-2",
                            "Prepare team sync",
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(10, 0)),
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(11, 0)),
                            60,
                            "task_schedule",
                            false
                    )),
                    List.of(new DailyReviewFreeWindow(
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(14, 0)),
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(15, 30)),
                            90,
                            DailyReviewWindowSuitability.DEEP_WORK,
                            false,
                            "14:00-15:30"
                    )),
                    null
            );
        }
    }

    private static final class DifferentSnapshotBuilder extends CountingBuilder {
        private final LocalDate reviewDate;

        private DifferentSnapshotBuilder(LocalDate reviewDate) {
            super(reviewDate);
            this.reviewDate = reviewDate;
        }

        @Override
        public DailyReviewSnapshot buildForDate(LocalDate ignored) {
            return new DailyReviewSnapshot(
                    reviewDate,
                    Instant.parse("2026-03-10T02:10:00Z"),
                    9,
                    2,
                    1,
                    4,
                    60,
                    false,
                    null,
                    java.util.List.of(new DailyReviewOverdueItem(
                            "task-x",
                            "Fix contract appendix",
                            reviewDate.minusDays(2),
                            LocalDateTime.of(reviewDate.minusDays(2), java.time.LocalTime.of(12, 0)),
                            2,
                            7,
                            0.95,
                            java.util.List.of("legal")
                    )),
                    java.util.List.of(new DailyReviewUpcomingItem(
                            "task-y",
                            "Prepare board packet",
                            reviewDate.plusDays(1),
                            LocalDateTime.of(reviewDate.plusDays(1), java.time.LocalTime.of(9, 0)),
                            1,
                            false,
                            true,
                            8,
                            0.9,
                            java.util.List.of("board")
                    )),
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(new DailyReviewFreeWindow(
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(11, 0)),
                            LocalDateTime.of(reviewDate, java.time.LocalTime.of(12, 0)),
                            60,
                            DailyReviewWindowSuitability.SHORT_WORK,
                            false,
                            "11:00-12:00"
                    )),
                    null
            );
        }
    }

    private static final class StubAiClient implements AiClient {
        private final String defaultModel;
        private final AiResponse response;
        private final AtomicInteger sendCount = new AtomicInteger();

        private StubAiClient(String defaultModel, AiResponse response) {
            this.defaultModel = defaultModel;
            this.response = response;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            sendCount.incrementAndGet();
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supportsImages() {
            return false;
        }

        @Override
        public AiMode getMode() {
            return AiMode.EXTERNAL_OPENAI;
        }

        @Override
        public String getDefaultModel() {
            return defaultModel;
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getBaseUrl() {
            return "https://example.test/v1";
        }

        @Override
        public void reloadConfiguration() {
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zoneId;

        private MutableClock(Instant instant, ZoneId zoneId) {
            this.instant = instant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(Instant nextInstant) {
            this.instant = nextInstant;
        }
    }
}
