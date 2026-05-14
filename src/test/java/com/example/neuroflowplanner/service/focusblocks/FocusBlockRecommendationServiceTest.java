package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FocusBlockRecommendationServiceTest {

    @Test
    void returnsFallbackResultWhenAiUnavailable() {
        ConfigManager.setPersistedFocusBlockRecommendations(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 11);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T02:00:00Z"), ZoneId.of("UTC"));
        FocusBlockRecommendationService service = createService(reviewDate, null, clock, Duration.ofMinutes(20));

        FocusBlockRecommendationResult result = service.getRecommendations(reviewDate, true).join();

        assertFalse(result.aiUsed());
        assertFalse(result.fromCache());
        assertEquals(FocusBlockSummarySource.FALLBACK, result.explanation().source());
        assertTrue(result.nextRecommendedBlock().available());
    }

    @Test
    void buildsAiEnhancedResultWhenStructuredAnswerAvailable() {
        ConfigManager.setPersistedFocusBlockRecommendations(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 11);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T02:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Главный блок
                        - Лучший блок сейчас: 09:30-11:00 для глубокой работы по приоритетной задаче.
                        ## Почему он подходит
                        - Это окно совпадает с вашим исторически сильным утренним периодом.
                        - В нём достаточно времени, чтобы закончить один цельный кусок работы без дробления.
                        ## Если окно пропустить
                        - Тогда используйте следующий блок после обеда и сократите цель до одной подзадачи.
                        """, "openai/gpt-5.4")
        );
        FocusBlockRecommendationService service = createService(reviewDate, aiClient, clock, Duration.ofMinutes(20));

        FocusBlockRecommendationResult result = service.getRecommendations(reviewDate, true).join();

        assertTrue(result.aiUsed());
        assertEquals("openai/gpt-5.4", result.modelId());
        assertEquals(FocusBlockSummarySource.AI, result.explanation().source());
        assertTrue(result.explanation().headline().contains("Лучший блок сейчас"));
        assertTrue(result.explanation().summary().contains("исторически сильным утренним периодом"));
        assertTrue(result.explanation().nextAction().contains("следующий блок после обеда"));
        assertEquals(1, aiClient.sendCount.get());
    }

    @Test
    void returnsCachedResultWhenStillFresh() {
        ConfigManager.setPersistedFocusBlockRecommendations(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 11);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T02:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Главный блок
                        - Лучший блок сейчас: 09:30-11:00.
                        ## Почему он подходит
                        - Слот длинный и чистый.
                        - Он совпадает с лучшими часами.
                        ## Если окно пропустить
                        - Возьмите следующий 45-минутный слот.
                        """, "openai/gpt-5.4")
        );
        FocusBlockRecommendationService service = createService(reviewDate, aiClient, clock, Duration.ofMinutes(20));

        FocusBlockRecommendationResult first = service.getRecommendations(reviewDate, false).join();
        FocusBlockRecommendationResult second = service.getRecommendations(reviewDate, false).join();

        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(1, aiClient.sendCount.get());
    }

    @Test
    void restoresPersistedRecommendationsAfterRestartWhenSnapshotMatches() {
        ConfigManager.setPersistedFocusBlockRecommendations(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 11);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T02:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Главный блок
                        - Лучший блок сейчас: 09:30-11:00.
                        ## Почему он подходит
                        - Это окно совпадает с сильным утренним периодом.
                        - Оно достаточно длинное для цельной глубокой сессии.
                        ## Если окно пропустить
                        - Перейдите на ближайший короткий блок после обеда.
                        """, "openai/gpt-5.4")
        );
        FocusBlockRecommendationService firstService = createService(reviewDate, aiClient, clock, Duration.ofMinutes(20));
        FocusBlockRecommendationResult first = firstService.getRecommendations(reviewDate, true).join();

        StubAiClient restartedAiClient = new StubAiClient("openai/gpt-5.4", AiResponse.error("should not be called"));
        FocusBlockRecommendationService restartedService = createService(reviewDate, restartedAiClient, clock, Duration.ofMinutes(20));
        FocusBlockRecommendationResult restored = restartedService.getRecommendations(reviewDate, false).join();

        assertTrue(first.aiUsed());
        assertTrue(restored.fromCache());
        assertEquals(first.explanation().headline(), restored.explanation().headline());
        assertEquals(0, restartedAiClient.sendCount.get());
    }

    @Test
    void refreshesPersistedRecommendationsWhenSnapshotChanged() {
        ConfigManager.setPersistedFocusBlockRecommendations(null);
        LocalDate reviewDate = LocalDate.of(2026, 3, 11);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T02:00:00Z"), ZoneId.of("UTC"));
        StubAiClient firstAiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Главный блок
                        - Лучший блок сейчас: 09:30-11:00.
                        ## Почему он подходит
                        - Это окно совпадает с сильным утренним периодом.
                        - Оно достаточно длинное для цельной глубокой сессии.
                        ## Если окно пропустить
                        - Перейдите на ближайший короткий блок после обеда.
                        """, "openai/gpt-5.4")
        );
        createService(reviewDate, firstAiClient, clock, Duration.ofMinutes(20))
                .getRecommendations(reviewDate, true)
                .join();

        StubAiClient refreshedAiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Главный блок
                        - Лучший блок сейчас: 15:00-16:00.
                        ## Почему он подходит
                        - Утро уже занято новыми блоками.
                        - Послеобеденное окно стало чище по данным дня.
                        ## Если окно пропустить
                        - Возьмите следующий 30-минутный слот до конца рабочего дня.
                        """, "openai/gpt-5.4")
        );
        FocusBlockRecommendationService changedService = createService(
                reviewDate,
                refreshedAiClient,
                clock,
                Duration.ofMinutes(20),
                buildChangedTasks(reviewDate),
                buildChangedSessions(reviewDate)
        );

        FocusBlockRecommendationResult refreshed = changedService.getRecommendations(reviewDate, false).join();

        assertFalse(refreshed.fromCache());
        assertTrue(refreshed.aiUsed());
        assertEquals(1, refreshedAiClient.sendCount.get());
    }

    private FocusBlockRecommendationService createService(
            LocalDate reviewDate,
            StubAiClient aiClient,
            MutableClock clock,
            Duration cacheTtl) {
        return createService(reviewDate, aiClient, clock, cacheTtl, buildTasks(reviewDate), buildSessions(reviewDate));
    }

    private FocusBlockRecommendationService createService(
            LocalDate reviewDate,
            StubAiClient aiClient,
            MutableClock clock,
            Duration cacheTtl,
            List<Task> tasks,
            List<TimeSession> sessions) {
        TaskApplicationService taskService = new StubTaskApplicationService(tasks);
        FocusProductivityProfileBuilder profileBuilder = new FocusProductivityProfileBuilder(() -> sessions);
        FocusBlockCandidateWindowCalculator calculator = new FocusBlockCandidateWindowCalculator(
                taskService,
                () -> sessions,
                date -> List.of(new com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkInterval(
                        LocalDateTime.of(date, LocalTime.of(9, 0)),
                        LocalDateTime.of(date, LocalTime.of(18, 0)),
                        540,
                        true,
                        "09:00-18:00"
                )),
                new com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowCalculator()
        );
        return new FocusBlockRecommendationService(
                taskService,
                profileBuilder,
                calculator,
                new FocusBlockRecommendationEngine(),
                () -> aiClient,
                clock,
                cacheTtl
        );
    }

    private List<Task> buildChangedTasks(LocalDate reviewDate) {
        Task morningBusy = new Task("task-4", "Внутренний синк", "", reviewDate, 3);
        morningBusy.setStartDate(reviewDate);
        morningBusy.setStartTime(LocalTime.of(9, 0));
        morningBusy.setDeadlineTime(LocalTime.of(12, 0));

        Task afternoon = new Task("task-5", "Подготовить follow-up", "", reviewDate.plusDays(1), 5);
        return List.of(morningBusy, afternoon);
    }

    private List<TimeSession> buildChangedSessions(LocalDate reviewDate) {
        return List.of(
                new TimeSession("session-6", "task-9", LocalDateTime.of(reviewDate.minusDays(1), LocalTime.of(15, 0)), 70),
                new TimeSession("session-7", "task-9", LocalDateTime.of(reviewDate.minusDays(2), LocalTime.of(16, 0)), 65),
                new TimeSession("session-8", "task-9", LocalDateTime.of(reviewDate.minusDays(3), LocalTime.of(15, 30)), 55),
                new TimeSession("session-9", "task-9", LocalDateTime.of(reviewDate.minusDays(4), LocalTime.of(14, 30)), 45),
                new TimeSession("session-10", "task-9", LocalDateTime.of(reviewDate.minusDays(5), LocalTime.of(15, 0)), 60)
        );
    }

    private List<Task> buildTasks(LocalDate reviewDate) {
        Task urgent = new Task("task-1", "Собрать клиентский пакет", "", reviewDate, 8);
        urgent.setStartDate(reviewDate);
        urgent.setStartTime(LocalTime.of(13, 0));
        urgent.setDeadlineTime(LocalTime.of(14, 0));

        Task overdue = new Task("task-2", "Закрыть старую просрочку", "", reviewDate.minusDays(1), 7);
        overdue.setDeadlineTime(LocalTime.of(12, 0));

        Task upcoming = new Task("task-3", "Подготовить sync notes", "", reviewDate.plusDays(1), 5);
        return List.of(urgent, overdue, upcoming);
    }

    private List<TimeSession> buildSessions(LocalDate reviewDate) {
        return List.of(
                new TimeSession("session-1", "task-9", LocalDateTime.of(reviewDate.minusDays(1), LocalTime.of(9, 0)), 90),
                new TimeSession("session-2", "task-9", LocalDateTime.of(reviewDate.minusDays(2), LocalTime.of(10, 0)), 75),
                new TimeSession("session-3", "task-9", LocalDateTime.of(reviewDate.minusDays(3), LocalTime.of(9, 30)), 80),
                new TimeSession("session-4", "task-9", LocalDateTime.of(reviewDate.minusDays(4), LocalTime.of(14, 0)), 35),
                new TimeSession("session-5", "task-9", LocalDateTime.of(reviewDate.minusDays(5), LocalTime.of(11, 0)), 60)
        );
    }

    private static final class StubTaskApplicationService implements TaskApplicationService {

        private final List<Task> tasks;

        private StubTaskApplicationService(List<Task> tasks) {
            this.tasks = tasks;
        }

        @Override
        public List<Task> loadTasks() {
            return tasks;
        }

        @Override public void saveTask(Task task) { throw new UnsupportedOperationException(); }
        @Override public void saveTasks(List<Task> tasks) { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.TaskBulkOperationResult saveTasksBulk(List<Task> tasks) { throw new UnsupportedOperationException(); }
        @Override public void deleteTask(String taskId) { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) { throw new UnsupportedOperationException(); }
        @Override public void linkDependency(String dependentTaskId, String blockerTaskId) { throw new UnsupportedOperationException(); }
        @Override public void saveDependencies(String taskId, List<String> blockerTaskIds) { throw new UnsupportedOperationException(); }
        @Override public List<String> loadDependencies(String taskId) { throw new UnsupportedOperationException(); }
        @Override public List<com.example.neuroflowplanner.model.TaskDependencyEdge> loadAllDependencyEdges() { throw new UnsupportedOperationException(); }
        @Override public void deleteDependenciesForTask(String taskId) { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.CriticalPathResult computeCriticalPathFullGraph() { throw new UnsupportedOperationException(); }
        @Override public com.example.neuroflowplanner.model.CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) { throw new UnsupportedOperationException(); }
        @Override public List<com.example.neuroflowplanner.model.TaskTemplate> loadAllTemplates() { throw new UnsupportedOperationException(); }
        @Override public void saveTemplate(com.example.neuroflowplanner.model.TaskTemplate template) { throw new UnsupportedOperationException(); }
    }

    private static final class StubAiClient implements AiClient {

        private final String modelId;
        private final AiResponse response;
        private final AtomicInteger sendCount = new AtomicInteger();

        private StubAiClient(String modelId, AiResponse response) {
            this.modelId = modelId;
            this.response = response;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            sendCount.incrementAndGet();
            return CompletableFuture.completedFuture(response);
        }

        @Override public CompletableFuture<ConnectionTestResult> testConnection() { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<ConnectionTestResult> testModel(String model) { throw new UnsupportedOperationException(); }
        @Override public CompletableFuture<List<String>> fetchAvailableModels() { throw new UnsupportedOperationException(); }
        @Override public boolean supportsImages() { return false; }
        @Override public AiMode getMode() { return AiMode.EXTERNAL_OPENAI; }
        @Override public String getDefaultModel() { return modelId; }
        @Override public boolean isConfigured() { return true; }
        @Override public String getBaseUrl() { return "https://example.invalid"; }
        @Override public void reloadConfiguration() { }
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
    }
}
