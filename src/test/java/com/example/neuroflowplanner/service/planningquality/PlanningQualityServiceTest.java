package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQualityServiceTest {

    @Test
    void restoresPersistedDashboardAfterRestartWhenFingerprintMatches() {
        ConfigManager.setPersistedPlanningQuality(null);
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 14);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T03:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина
                        - Планирование в целом управляемо, но с заметным шумом в середине периода.
                        - Основная просадка связана с частыми переносами и скачками ритма.
                        ## Слабая зона
                        - Главная слабая зона — плотные дни с поздним перепланированием задач.
                        ## Сильные стороны
                        - Точность оценки времени уже выглядит рабочей для части задач.
                        - База по трекингу достаточна для устойчивых выводов.
                        ## Что улучшить прямо сейчас
                        - Ослабьте самый плотный день недели и заложите буфер перед задачами с жёстким дедлайном.
                        """, "openai/gpt-5.4")
        );

        PlanningQualityService firstService = createService(periodStart, periodEnd, aiClient, clock, Duration.ofMinutes(20));
        PlanningQualityResult first = firstService.getDashboard(periodStart, periodEnd, true).join();
        assertTrue(first.aiUsed());
        assertEquals(1, aiClient.sendCount.get());

        StubAiClient restartedAiClient = new StubAiClient("openai/gpt-5.4", AiResponse.error("should not be called"));
        PlanningQualityService restartedService = createService(periodStart, periodEnd, restartedAiClient, clock, Duration.ofMinutes(20));
        PlanningQualityResult restored = restartedService.getDashboard(periodStart, periodEnd, false).join();

        assertTrue(restored.fromCache());
        assertEquals(PlanningQualitySummarySource.AI, restored.summary().source());
        assertEquals(first.summary().headline(), restored.summary().headline());
        assertEquals(0, restartedAiClient.sendCount.get());
    }

    @Test
    void refreshesPersistedDashboardWhenSnapshotFingerprintChanged() {
        ConfigManager.setPersistedPlanningQuality(null);
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 14);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T03:00:00Z"), ZoneId.of("UTC"));

        PlanningQualityService firstService = createService(
                periodStart,
                periodEnd,
                new StubAiClient(
                        "openai/gpt-5.4",
                        AiResponse.success("""
                                ## Общая картина
                                - Старый planning quality dashboard.
                                ## Слабая зона
                                - Старый риск.
                                ## Сильные стороны
                                - Старая сильная сторона.
                                ## Что улучшить прямо сейчас
                                - Старое действие.
                                """, "openai/gpt-5.4")),
                clock,
                Duration.ofMinutes(20)
        );
        firstService.getDashboard(periodStart, periodEnd, true).join();

        StubAiClient refreshedAiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина
                        - Новый planning quality dashboard после изменения данных.
                        ## Слабая зона
                        - Новый риск.
                        ## Сильные стороны
                        - Обновлённая сильная сторона.
                        ## Что улучшить прямо сейчас
                        - Новое действие.
                        """, "openai/gpt-5.4")
        );
        PlanningQualityService changedService = createService(
                periodStart,
                periodEnd,
                refreshedAiClient,
                clock,
                Duration.ofMinutes(20),
                buildChangedTasks(),
                buildChangedSessions()
        );

        PlanningQualityResult refreshed = changedService.getDashboard(periodStart, periodEnd, false).join();

        assertFalse(refreshed.fromCache());
        assertTrue(refreshed.aiUsed());
        assertEquals(1, refreshedAiClient.sendCount.get());
        assertTrue(refreshed.summary().headline().contains("Новый planning quality dashboard"));
    }

    @Test
    void returnsFallbackResultWhenAiUnavailable() {
        ConfigManager.setPersistedPlanningQuality(null);
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 14);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T03:00:00Z"), ZoneId.of("UTC"));
        PlanningQualityService service = createService(periodStart, periodEnd, null, clock, Duration.ofMinutes(20));

        PlanningQualityResult result = service.getDashboard(periodStart, periodEnd, true).join();

        assertFalse(result.aiUsed());
        assertFalse(result.fromCache());
        assertEquals(PlanningQualitySummarySource.FALLBACK, result.summary().source());
        assertTrue(result.summary().available());
        assertTrue(result.snapshot().hasDeterministicMetrics());
    }

    @Test
    void buildsAiEnhancedResultWhenStructuredAnswerAvailable() {
        ConfigManager.setPersistedPlanningQuality(null);
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 14);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T03:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина
                        - Планирование в целом управляемо, но с заметным шумом в середине периода.
                        - Основная просадка связана с частыми переносами и скачками ритма.
                        ## Слабая зона
                        - Главная слабая зона — плотные дни с поздним перепланированием задач.
                        ## Сильные стороны
                        - Точность оценки времени уже выглядит рабочей для части задач.
                        - База по трекингу достаточна для устойчивых выводов.
                        ## Что улучшить прямо сейчас
                        - Ослабьте самый плотный день недели и заложите буфер перед задачами с жёстким дедлайном.
                        """, "openai/gpt-5.4")
        );
        PlanningQualityService service = createService(periodStart, periodEnd, aiClient, clock, Duration.ofMinutes(20));

        PlanningQualityResult result = service.getDashboard(periodStart, periodEnd, true).join();

        assertTrue(result.aiUsed());
        assertEquals("openai/gpt-5.4", result.modelId());
        assertEquals(PlanningQualitySummarySource.AI, result.summary().source());
        assertTrue(result.summary().headline().contains("Планирование"));
        assertTrue(result.summary().summary().contains("Главная слабая зона"));
        assertTrue(result.summary().summary().contains("Точность оценки времени"));
        assertTrue(result.summary().nextAction().contains("заложите буфер"));
        assertEquals(1, aiClient.sendCount.get());
    }

    @Test
    void returnsCachedResultWhenStillFresh() {
        ConfigManager.setPersistedPlanningQuality(null);
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 14);
        MutableClock clock = new MutableClock(Instant.parse("2026-03-11T03:00:00Z"), ZoneId.of("UTC"));
        StubAiClient aiClient = new StubAiClient(
                "openai/gpt-5.4",
                AiResponse.success("""
                        ## Общая картина
                        - Качество планирования умеренно устойчивое.
                        - Основная точка роста — уменьшить частоту поздних переносов.
                        ## Слабая зона
                        - Поздние переносы перед дедлайном размывают план.
                        ## Сильные стороны
                        - Есть достаточная база трекинга для оценки.
                        ## Что улучшить прямо сейчас
                        - Ослабьте плотность календаря на перегруженных днях.
                        """, "openai/gpt-5.4")
        );
        PlanningQualityService service = createService(periodStart, periodEnd, aiClient, clock, Duration.ofMinutes(20));

        PlanningQualityResult first = service.getDashboard(periodStart, periodEnd, false).join();
        PlanningQualityResult second = service.getDashboard(periodStart, periodEnd, false).join();

        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(1, aiClient.sendCount.get());
    }

    private PlanningQualityService createService(
            LocalDate periodStart,
            LocalDate periodEnd,
            StubAiClient aiClient,
            MutableClock clock,
            Duration cacheTtl) {
        return createService(periodStart, periodEnd, aiClient, clock, cacheTtl, buildTasks(), buildSessions());
    }

    private PlanningQualityService createService(
            LocalDate periodStart,
            LocalDate periodEnd,
            StubAiClient aiClient,
            MutableClock clock,
            Duration cacheTtl,
            List<Task> tasks,
            List<TimeSession> sessions) {
        PlanningQualitySnapshotBuilder builder = new PlanningQualitySnapshotBuilder(
                new StubTaskService(tasks),
                () -> sessions
        );
        return new PlanningQualityService(
                builder,
                () -> aiClient,
                clock,
                cacheTtl
        );
    }

    private List<Task> buildChangedTasks() {
        Task shifted = new Task("shifted", "Крупный перенос", "", LocalDate.of(2026, 3, 5), 8);
        shifted.setStartDate(LocalDate.of(2026, 3, 5));
        shifted.setStartTime(LocalTime.of(15, 0));
        shifted.setDeadlineTime(LocalTime.of(17, 0));
        shifted.setCompleted(true);
        shifted.setCompletedDate(LocalDate.of(2026, 3, 7));

        Task volatileTask = new Task("volatile", "Шумный день", "", LocalDate.of(2026, 3, 8), 6);
        volatileTask.setStartDate(LocalDate.of(2026, 3, 8));
        volatileTask.setStartTime(LocalTime.of(18, 0));
        volatileTask.setDeadlineTime(LocalTime.of(19, 0));
        volatileTask.setCompleted(true);
        volatileTask.setCompletedDate(LocalDate.of(2026, 3, 10));

        Task active = new Task("active-new", "Новый активный хвост", "", LocalDate.of(2026, 3, 13), 4);
        active.setStartDate(LocalDate.of(2026, 3, 13));
        active.setStartTime(LocalTime.of(17, 0));
        active.setDeadlineTime(LocalTime.of(18, 30));

        return List.of(shifted, volatileTask, active);
    }

    private List<TimeSession> buildChangedSessions() {
        return List.of(
                new TimeSession("cs1", "shifted", LocalDateTime.of(2026, 3, 6, 16, 0), 120),
                new TimeSession("cs2", "shifted", LocalDateTime.of(2026, 3, 7, 17, 30), 90),
                new TimeSession("cs3", "volatile", LocalDateTime.of(2026, 3, 8, 18, 15), 80),
                new TimeSession("cs4", "volatile", LocalDateTime.of(2026, 3, 9, 20, 0), 50),
                new TimeSession("cs5", "volatile", LocalDateTime.of(2026, 3, 10, 21, 0), 40)
        );
    }

    private List<Task> buildTasks() {
        Task accurate = new Task("accurate", "Точное планирование", "", LocalDate.of(2026, 3, 3), 4);
        accurate.setStartDate(LocalDate.of(2026, 3, 3));
        accurate.setStartTime(LocalTime.of(9, 0));
        accurate.setDeadlineTime(LocalTime.of(10, 0));
        accurate.setCompleted(true);
        accurate.setCompletedDate(LocalDate.of(2026, 3, 3));

        Task drifted = new Task("drifted", "Сдвинутая задача", "", LocalDate.of(2026, 3, 4), 7);
        drifted.setStartDate(LocalDate.of(2026, 3, 4));
        drifted.setStartTime(LocalTime.of(10, 0));
        drifted.setDeadlineTime(LocalTime.of(12, 0));
        drifted.setCompleted(true);
        drifted.setCompletedDate(LocalDate.of(2026, 3, 6));

        Task moderate = new Task("moderate", "Умеренная задача", "", LocalDate.of(2026, 3, 7), 5);
        moderate.setStartDate(LocalDate.of(2026, 3, 7));
        moderate.setStartTime(LocalTime.of(14, 0));
        moderate.setDeadlineTime(LocalTime.of(15, 30));
        moderate.setCompleted(true);
        moderate.setCompletedDate(LocalDate.of(2026, 3, 7));

        Task active = new Task("active", "Активный хвост", "", LocalDate.of(2026, 3, 12), 3);
        active.setStartDate(LocalDate.of(2026, 3, 12));
        active.setStartTime(LocalTime.of(16, 0));
        active.setDeadlineTime(LocalTime.of(17, 0));

        return List.of(accurate, drifted, moderate, active);
    }

    private List<TimeSession> buildSessions() {
        return List.of(
                new TimeSession("s1", "accurate", LocalDateTime.of(2026, 3, 3, 9, 0), 60),
                new TimeSession("s2", "drifted", LocalDateTime.of(2026, 3, 5, 15, 0), 95),
                new TimeSession("s3", "drifted", LocalDateTime.of(2026, 3, 6, 10, 30), 90),
                new TimeSession("s4", "moderate", LocalDateTime.of(2026, 3, 7, 14, 5), 70),
                new TimeSession("s5", "moderate", LocalDateTime.of(2026, 3, 8, 9, 0), 20),
                new TimeSession("s6", "other", LocalDateTime.of(2026, 3, 10, 9, 15), 150),
                new TimeSession("s7", "other", LocalDateTime.of(2026, 3, 11, 9, 10), 145),
                new TimeSession("s8", "other", LocalDateTime.of(2026, 3, 12, 9, 5), 140)
        );
    }

    private static final class StubTaskService implements TaskApplicationService {
        private final List<Task> tasks;

        private StubTaskService(List<Task> tasks) {
            this.tasks = tasks;
        }

        @Override
        public List<Task> loadTasks() {
            return tasks;
        }

        @Override public void saveTask(Task task) { throw new UnsupportedOperationException(); }
        @Override public void saveTasks(List<Task> tasks) { throw new UnsupportedOperationException(); }
        @Override public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) { throw new UnsupportedOperationException(); }
        @Override public void deleteTask(String taskId) { throw new UnsupportedOperationException(); }
        @Override public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) { throw new UnsupportedOperationException(); }
        @Override public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) { throw new UnsupportedOperationException(); }
        @Override public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) { throw new UnsupportedOperationException(); }
        @Override public void linkDependency(String dependentTaskId, String blockerTaskId) { throw new UnsupportedOperationException(); }
        @Override public void saveDependencies(String taskId, List<String> blockerTaskIds) { throw new UnsupportedOperationException(); }
        @Override public List<String> loadDependencies(String taskId) { throw new UnsupportedOperationException(); }
        @Override public List<TaskDependencyEdge> loadAllDependencyEdges() { throw new UnsupportedOperationException(); }
        @Override public void deleteDependenciesForTask(String taskId) { throw new UnsupportedOperationException(); }
        @Override public CriticalPathResult computeCriticalPathFullGraph() { throw new UnsupportedOperationException(); }
        @Override public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) { throw new UnsupportedOperationException(); }
        @Override public List<TaskTemplate> loadAllTemplates() { throw new UnsupportedOperationException(); }
        @Override public void saveTemplate(TaskTemplate template) { throw new UnsupportedOperationException(); }
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
        private final Instant instant;
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
