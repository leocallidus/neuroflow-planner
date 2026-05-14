package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.db.DatabaseException;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Task Services Integration")
class TaskServicesIntegrationTest extends IsolatedTestDataFixture {
    private static final String ISOLATED_DIR_PREFIX = "neuroflow-test-data-";
    private static final String TITLE_PREFIX = "stage6-task-it-";
    private static final String ID_PREFIX = "stage6-task-it-";

    private final DatabaseManager db = DatabaseManager.getInstance();
    private final DefaultTaskApplicationService applicationService = new DefaultTaskApplicationService();
    private final DefaultTaskAnalysisService analysisService = new DefaultTaskAnalysisService();
    private final DefaultTaskExportService exportService = new DefaultTaskExportService();
    private final DefaultTaskImportService importService = new DefaultTaskImportService(applicationService, analysisService);
    private AiClient originalActiveClient;
    private AiMode originalMode;

    private static final Field ACTIVE_CLIENT_FIELD = findField("activeClient");
    private static final Field CURRENT_MODE_FIELD = findField("currentMode");

    @BeforeEach
    void setUp() throws Exception {
        assertIsolatedDataDir();
        captureAiFactoryState();
        cleanupPrefixedTasks();
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreAiFactoryState();
        assertIsolatedDataDir();
        cleanupPrefixedTasks();
    }

    @Test
    @DisplayName("CRUD round-trip through TaskApplicationService")
    void taskCrudRoundTrip() {
        String taskId = ID_PREFIX + UUID.randomUUID();
        String title = TITLE_PREFIX + "crud";
        Task task = new Task(taskId, title, "integration", LocalDate.now().plusDays(2), 4);

        applicationService.saveTask(task);
        Task stored = findById(applicationService.loadTasks(), taskId);
        assertNotNull(stored);
        assertEquals(title, stored.getTitle());

        applicationService.deleteTask(taskId);
        Task deleted = findById(applicationService.loadTasks(), taskId);
        assertNull(deleted);
    }

    @Test
    @DisplayName("TaskApplicationService exposes atomic bulk API with unified result contract")
    void bulkApiReturnsUnifiedResultContract() {
        String taskAId = ID_PREFIX + "bulk-api-a-" + UUID.randomUUID();
        String taskBId = ID_PREFIX + "bulk-api-b-" + UUID.randomUUID();
        String missingId = ID_PREFIX + "bulk-api-missing-" + UUID.randomUUID();

        Task a = new Task(taskAId, TITLE_PREFIX + "bulk-a", "a", LocalDate.now().plusDays(2), 3);
        Task b = new Task(taskBId, TITLE_PREFIX + "bulk-b", "b", LocalDate.now().plusDays(3), 2);

        TaskBulkOperationResult saveResult = applicationService.saveTasksBulk(List.of(a, b));
        assertTrue(saveResult.isSuccessful());
        assertEquals(0, saveResult.failedCount());
        assertEquals(2, saveResult.processedCount());
        assertEquals(2, saveResult.updatedCount());

        TaskBulkOperationResult tagsResult = applicationService.updateTaskTagsBulk(Map.of(
            taskAId, "tag-a",
            taskBId, "tag-b",
            missingId, "missing"
        ));
        assertTrue(tagsResult.isSuccessful());
        assertEquals(0, tagsResult.failedCount());
        assertEquals(3, tagsResult.processedCount());
        assertEquals(2, tagsResult.updatedCount());

        TaskBulkOperationResult archiveResult = applicationService.archiveTasksBulk(List.of(taskAId), false);
        assertTrue(archiveResult.isSuccessful());
        assertEquals(0, archiveResult.failedCount());
        Task archived = findById(applicationService.loadTasks(), taskAId);
        assertNotNull(archived);
        assertTrue(archived.isArchived());

        TaskBulkOperationResult deleteResult = applicationService.deleteTasksBulk(List.of(taskAId, taskBId));
        assertTrue(deleteResult.isSuccessful());
        assertEquals(0, deleteResult.failedCount());
        assertTrue(deleteResult.updatedCount() >= 2);

        assertNull(findById(applicationService.loadTasks(), taskAId));
        assertNull(findById(applicationService.loadTasks(), taskBId));
    }

    @Test
    @DisplayName("saveTasksBulk rolls back all tasks when one row in batch is invalid")
    void saveTasksBulkRollbackOnMidBatchFailure() {
        String validId = ID_PREFIX + "bulk-rollback-valid-" + UUID.randomUUID();
        String invalidId = ID_PREFIX + "bulk-rollback-invalid-" + UUID.randomUUID();

        Task valid = new Task(validId, TITLE_PREFIX + "valid", "", LocalDate.now().plusDays(3), 2);
        Task invalid = new Task(invalidId, null, "", LocalDate.now().plusDays(3), 2);

        assertThrows(DatabaseException.class, () -> applicationService.saveTasksBulk(List.of(valid, invalid)));
        assertNull(findById(applicationService.loadTasks(), validId));
        assertNull(findById(applicationService.loadTasks(), invalidId));
    }

    @Test
    @DisplayName("deleteTasksBulk keeps dependency graph consistent after mass delete")
    void deleteTasksBulkMaintainsDependencyIntegrity() {
        String dependentAId = ID_PREFIX + "bulk-dep-a-" + UUID.randomUUID();
        String dependentBId = ID_PREFIX + "bulk-dep-b-" + UUID.randomUUID();
        String blockerAId = ID_PREFIX + "bulk-dep-blocker-a-" + UUID.randomUUID();
        String blockerBId = ID_PREFIX + "bulk-dep-blocker-b-" + UUID.randomUUID();

        applicationService.saveTasksBulk(List.of(
            new Task(dependentAId, TITLE_PREFIX + "dep-a", "", LocalDate.now().plusDays(4), 3),
            new Task(dependentBId, TITLE_PREFIX + "dep-b", "", LocalDate.now().plusDays(4), 2),
            new Task(blockerAId, TITLE_PREFIX + "blocker-a", "", LocalDate.now().plusDays(4), 2),
            new Task(blockerBId, TITLE_PREFIX + "blocker-b", "", LocalDate.now().plusDays(4), 1)
        ));

        applicationService.saveDependencies(dependentAId, List.of(blockerAId, blockerBId));
        applicationService.saveDependencies(dependentBId, List.of(blockerAId));
        assertEquals(3, applicationService.loadAllDependencyEdges().size());

        TaskBulkOperationResult result = applicationService.deleteTasksBulk(List.of(blockerAId, dependentAId));
        assertTrue(result.isSuccessful());

        List<TaskDependencyEdge> edges = applicationService.loadAllDependencyEdges();
        assertTrue(edges.stream().noneMatch(edge ->
            edge.dependentTaskId().equals(dependentAId) || edge.blockerTaskId().equals(blockerAId)
        ));
        assertTrue(applicationService.loadDependencies(dependentBId).isEmpty());
        assertNotNull(findById(applicationService.loadTasks(), dependentBId));
        assertNotNull(findById(applicationService.loadTasks(), blockerBId));

        applicationService.deleteTasksBulk(List.of(dependentBId, blockerBId));
    }

    @Test
    @DisplayName("Overdue recurring task produces next instance and resets source recurrence")
    void recurringTaskProcessing() {
        String recurringId = ID_PREFIX + UUID.randomUUID();
        String recurringTitle = TITLE_PREFIX + "recurring-" + UUID.randomUUID();
        Task recurring = new Task(recurringId, recurringTitle, "daily", LocalDate.now().minusDays(3), 3);
        recurring.setRecurrence("daily");

        applicationService.saveTask(recurring);
        List<Task> loaded = applicationService.loadTasks();
        List<Task> flattened = flattenTasks(loaded);

        boolean generatedNextInstance = flattened.stream().anyMatch(task ->
            recurringTitle.equals(task.getTitle())
                && !recurringId.equals(task.getId())
                && !task.getDeadline().isBefore(LocalDate.now())
        );
        assertTrue(generatedNextInstance, "Expected next recurring instance");

        Task original = findById(applicationService.loadTasks(), recurringId);
        assertNotNull(original);
        assertEquals("", original.getRecurrence());
    }

    @Test
    @DisplayName("TaskAnalysisService auto-schedule assigns start dates")
    void autoScheduleAssignsStartDates() {
        Task first = new Task(ID_PREFIX + UUID.randomUUID(), TITLE_PREFIX + "first", "", LocalDate.now().plusDays(2), 2);
        Task second = new Task(ID_PREFIX + UUID.randomUUID(), TITLE_PREFIX + "second", "", LocalDate.now().plusDays(3), 3);
        List<Task> tasks = new ArrayList<>(List.of(first, second));

        String result = analysisService.autoSchedule(tasks, 4);

        assertTrue(result.contains("Авто-планирование завершено"));
        assertNotNull(first.getStartDate());
        assertNotNull(second.getStartDate());
    }

    @Test
    @DisplayName("TaskExportService writes markdown and rejects unsupported extension")
    void exportServiceHappyAndFailPath(@TempDir Path tempDir) throws Exception {
        Path markdown = tempDir.resolve("insight.md");
        exportService.exportInsight(markdown.toFile(), ".md", "content line");

        assertTrue(Files.exists(markdown));
        assertTrue(Files.readString(markdown).contains("content line"));

        assertThrows(
            IllegalArgumentException.class,
            () -> exportService.exportInsight(tempDir.resolve("insight.bin").toFile(), ".bin", "content")
        );
    }

    @Test
    @DisplayName("TaskExportService writes canonical task JSON envelope")
    void exportServiceWritesTaskJson(@TempDir Path tempDir) throws Exception {
        Task root = new Task(ID_PREFIX + "json-root-" + UUID.randomUUID(), TITLE_PREFIX + "json-root", "", LocalDate.now().plusDays(4), 3);
        Task child = new Task(ID_PREFIX + "json-child-" + UUID.randomUUID(), TITLE_PREFIX + "json-child", "", LocalDate.now().plusDays(2), 2);
        root.getSubtasks().add(child);

        Path jsonFile = tempDir.resolve("tasks.json");
        exportService.exportTasksJson(jsonFile.toFile(), List.of(root));

        String payload = Files.readString(jsonFile);
        assertTrue(payload.contains("\"schemaType\" : \"task-export\""));
        assertTrue(payload.contains("\"schemaVersion\" : 1"));
        assertTrue(payload.contains("\"tasks\""));
        assertTrue(payload.contains("\"parentId\" : \"" + root.getId() + "\""));
    }

    @Test
    @DisplayName("task JSON export/import happy-path restores persisted tasks without manual normalization")
    void taskJsonExportImportHappyPath(@TempDir Path tempDir) throws Exception {
        Task root = new Task(ID_PREFIX + "import-root-" + UUID.randomUUID(), TITLE_PREFIX + "import-root", "", LocalDate.now().plusDays(4), 3);
        root.setTags("alpha,beta");
        root.setStartDate(LocalDate.now().plusDays(1));
        Task child = new Task(ID_PREFIX + "import-child-" + UUID.randomUUID(), TITLE_PREFIX + "import-child", "", LocalDate.now().plusDays(2), 2);
        child.setCompleted(true);
        child.setCompletedDate(LocalDate.now().plusDays(1));
        root.getSubtasks().add(child);

        Path file = tempDir.resolve("tasks-portability.json");
        exportService.exportTasksJson(file.toFile(), List.of(root));
        String payload = Files.readString(file);

        TaskImportService.ImportPreview preview = importService.dryRun(
            payload,
            TaskImportService.ImportFormat.JSON,
            new TaskImportService.ImportOptions(
                TaskImportService.DuplicateIdPolicy.KEEP_LAST,
                TaskImportService.TitleDedupePolicy.ALLOW_DUPLICATES,
                true
            )
        );
        TaskImportService.ImportResult result = importService.apply(preview);

        assertEquals(2, preview.sourceCount());
        assertEquals(2, preview.acceptedCount());
        assertTrue(result.bulkResult().isSuccessful());

        Task storedRoot = findById(applicationService.loadTasks(), root.getId());
        Task storedChild = findById(applicationService.loadTasks(), child.getId());
        assertNotNull(storedRoot);
        assertNotNull(storedChild);
        assertEquals("alpha,beta", storedRoot.getTags());
        assertEquals(root.getStartDate(), storedRoot.getStartDate());
        assertEquals(root.getId(), storedChild.getParentId());
        assertTrue(storedChild.isCompleted());
        assertEquals(child.getCompletedDate(), storedChild.getCompletedDate());
    }

    @Test
    @DisplayName("TaskAnalysisService returns local fallback on AI 429 rate-limit")
    void analyzeTaskFallsBackOnRateLimitedResponse() throws Exception {
        useActiveAiClient(new StubAiClient(
            AiMode.EXTERNAL_OPENAI,
            () -> CompletableFuture.completedFuture(aiFailureResponse(429, "Too Many Requests"))
        ));

        Task task = new Task(
            ID_PREFIX + UUID.randomUUID(),
            TITLE_PREFIX + "rate-limit",
            "Analyze this",
            LocalDate.now().plusDays(4),
            5
        );

        String insight = analysisService.analyzeTask(task).join();

        assertNotNull(insight);
        assertTrue(insight.contains("Анализ задачи"));
        assertTrue(insight.contains("Сложность"));
    }

    @Test
    @DisplayName("TaskAnalysisService returns local fallback on provider failure (503)")
    void analyzeTaskFallsBackOnProviderError() throws Exception {
        useActiveAiClient(new StubAiClient(
            AiMode.EXTERNAL_OPENAI,
            () -> CompletableFuture.failedFuture(new RuntimeException("HTTP status 503"))
        ));

        Task task = new Task(
            ID_PREFIX + UUID.randomUUID(),
            TITLE_PREFIX + "provider-error",
            "Analyze this",
            LocalDate.now().plusDays(2),
            7
        );

        String insight = analysisService.analyzeTask(task).join();

        assertNotNull(insight);
        assertTrue(insight.contains("Анализ задачи"));
        assertTrue(insight.contains("Сложность"));
    }

    @Test
    @DisplayName("TaskApplicationService dependency CRUD uses normalized edge storage")
    void dependencyCrudUsesNormalizedStorage() {
        String aId = ID_PREFIX + "dep-A-" + UUID.randomUUID();
        String bId = ID_PREFIX + "dep-B-" + UUID.randomUUID();
        String cId = ID_PREFIX + "dep-C-" + UUID.randomUUID();

        Task a = new Task(aId, TITLE_PREFIX + "dep-A", "", LocalDate.now().plusDays(3), 3);
        Task b = new Task(bId, TITLE_PREFIX + "dep-B", "", LocalDate.now().plusDays(3), 2);
        Task c = new Task(cId, TITLE_PREFIX + "dep-C", "", LocalDate.now().plusDays(3), 4);

        applicationService.saveTask(a);
        applicationService.saveTask(b);
        applicationService.saveTask(c);

        applicationService.saveDependencies(aId, List.of(" ", bId, cId, bId, aId, "missing-task-id"));

        List<String> blockers = applicationService.loadDependencies(aId);
        assertEquals(List.of(bId, cId), blockers);

        List<TaskDependencyEdge> edges = applicationService.loadAllDependencyEdges();
        assertTrue(edges.contains(new TaskDependencyEdge(aId, bId)));
        assertTrue(edges.contains(new TaskDependencyEdge(aId, cId)));

        if (ConfigManager.getTaskDependencyMode() == TaskDependencyMode.DUAL) {
            assertEquals(bId + "," + cId, db.loadLegacyDependsOn(aId));
        }

        applicationService.deleteDependenciesForTask(aId);
        assertTrue(applicationService.loadDependencies(aId).isEmpty());

        if (ConfigManager.getTaskDependencyMode() == TaskDependencyMode.DUAL) {
            assertEquals("", db.loadLegacyDependsOn(aId));
        }

        applicationService.deleteTask(aId);
        applicationService.deleteTask(bId);
        applicationService.deleteTask(cId);
    }

    @Test
    @DisplayName("linkDependency fail-fast blocks cycle creation before persistence")
    void linkDependencyRejectsCycle() {
        String aId = ID_PREFIX + "cycle-A-" + UUID.randomUUID();
        String bId = ID_PREFIX + "cycle-B-" + UUID.randomUUID();
        String cId = ID_PREFIX + "cycle-C-" + UUID.randomUUID();

        applicationService.saveTask(new Task(aId, TITLE_PREFIX + "cycle-A", "", LocalDate.now().plusDays(5), 3));
        applicationService.saveTask(new Task(bId, TITLE_PREFIX + "cycle-B", "", LocalDate.now().plusDays(5), 2));
        applicationService.saveTask(new Task(cId, TITLE_PREFIX + "cycle-C", "", LocalDate.now().plusDays(5), 4));

        applicationService.linkDependency(aId, bId); // A depends on B
        applicationService.linkDependency(bId, cId); // B depends on C

        TaskDependencyException exception = assertThrows(
            TaskDependencyException.class,
            () -> applicationService.linkDependency(cId, aId) // would close cycle C -> A -> B -> C
        );
        assertEquals(ErrorCode.TASK_DEPENDENCY_CYCLE, exception.errorCode());
        assertTrue(applicationService.loadDependencies(cId).isEmpty());

        applicationService.deleteTask(aId);
        applicationService.deleteTask(bId);
        applicationService.deleteTask(cId);
    }

    @Test
    @DisplayName("linkDependency fail-fast rejects unknown task reference")
    void linkDependencyRejectsUnknownReference() {
        String dependentId = ID_PREFIX + "invalid-ref-" + UUID.randomUUID();
        String blockerId = ID_PREFIX + "missing-" + UUID.randomUUID();

        applicationService.saveTask(new Task(
            dependentId,
            TITLE_PREFIX + "invalid-ref",
            "",
            LocalDate.now().plusDays(3),
            2
        ));

        TaskDependencyException exception = assertThrows(
            TaskDependencyException.class,
            () -> applicationService.linkDependency(dependentId, blockerId)
        );
        assertEquals(ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE, exception.errorCode());
        assertTrue(applicationService.loadDependencies(dependentId).isEmpty());

        applicationService.deleteTask(dependentId);
    }

    @Test
    @DisplayName("TaskApplicationService computes critical path for full graph and root scope")
    void criticalPathSupportsFullAndRootScopes() {
        String rootId = ID_PREFIX + "cp-root-" + UUID.randomUUID();
        String aId = ID_PREFIX + "cp-a-" + UUID.randomUUID();
        String bId = ID_PREFIX + "cp-b-" + UUID.randomUUID();
        String externalId = ID_PREFIX + "cp-x-" + UUID.randomUUID();

        Task root = new Task(rootId, TITLE_PREFIX + "cp-root", "", LocalDate.now().plusDays(7), 1);
        Task a = new Task(aId, TITLE_PREFIX + "cp-a", "", LocalDate.now().plusDays(6), 3, rootId, "", "");
        Task b = new Task(bId, TITLE_PREFIX + "cp-b", "", LocalDate.now().plusDays(6), 2, rootId, "", "");
        Task external = new Task(externalId, TITLE_PREFIX + "cp-x", "", LocalDate.now().plusDays(6), 5);

        applicationService.saveTask(root);
        applicationService.saveTask(a);
        applicationService.saveTask(b);
        applicationService.saveTask(external);

        applicationService.linkDependency(aId, bId);
        applicationService.linkDependency(bId, externalId);

        CriticalPathResult full = applicationService.computeCriticalPathFullGraph();
        CriticalPathResult scoped = applicationService.computeCriticalPathForRootTask(rootId);

        assertEquals(10, full.projectDuration());
        assertEquals(List.of(externalId, bId, aId), full.criticalChainTaskIds());
        assertEquals(5, scoped.projectDuration());
        assertEquals(List.of(bId, aId), scoped.criticalChainTaskIds());

        applicationService.deleteTask(aId);
        applicationService.deleteTask(bId);
        applicationService.deleteTask(externalId);
        applicationService.deleteTask(rootId);
    }

    private Task findById(List<Task> tasks, String taskId) {
        for (Task task : flattenTasks(tasks)) {
            if (taskId.equals(task.getId())) {
                return task;
            }
        }
        return null;
    }

    private List<Task> flattenTasks(List<Task> tasks) {
        List<Task> flattened = new ArrayList<>();
        if (tasks == null) {
            return flattened;
        }
        for (Task task : tasks) {
            collectRecursive(task, flattened);
        }
        return flattened;
    }

    private void collectRecursive(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            collectRecursive(subtask, sink);
        }
    }

    private void cleanupPrefixedTasks() {
        for (Task task : flattenTasks(db.loadAllTasks())) {
            String title = task.getTitle();
            String id = task.getId();
            if ((title != null && title.startsWith(TITLE_PREFIX)) || (id != null && id.startsWith(ID_PREFIX))) {
                db.deleteTask(id);
            }
        }
    }

    private void assertIsolatedDataDir() {
        Path dataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        Path fileName = dataDir.getFileName();
        assertTrue(
            fileName != null && fileName.toString().startsWith(ISOLATED_DIR_PREFIX),
            "Cleanup allowed only in isolated test data dir, actual: " + dataDir
        );
    }

    private void captureAiFactoryState() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        originalActiveClient = (AiClient) ACTIVE_CLIENT_FIELD.get(factory);
        originalMode = (AiMode) CURRENT_MODE_FIELD.get(factory);
    }

    private void restoreAiFactoryState() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, originalActiveClient);
        CURRENT_MODE_FIELD.set(factory, originalMode);
    }

    private void useActiveAiClient(AiClient aiClient) throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, aiClient);
        CURRENT_MODE_FIELD.set(factory, aiClient.getMode());
    }

    private static AiResponse aiFailureResponse(int httpStatus, String message) {
        return new AiResponse(
            null,
            false,
            message,
            httpStatus,
            "test-model",
            null,
            null,
            null,
            Instant.now(),
            5L,
            httpStatus,
            1
        );
    }

    private static Field findField(String name) {
        try {
            Field field = AiClientFactory.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access field: " + name, ex);
        }
    }

    private static final class StubAiClient implements AiClient {
        private final AiMode mode;
        private final Supplier<CompletableFuture<AiResponse>> responseSupplier;

        private StubAiClient(AiMode mode, Supplier<CompletableFuture<AiResponse>> responseSupplier) {
            this.mode = mode;
            this.responseSupplier = responseSupplier;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return responseSupplier.get();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return CompletableFuture.completedFuture(ConnectionTestResult.success(
                "ok",
                mode,
                "stub://test",
                "test-model",
                "pong",
                1
            ));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            return testConnection();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            return testConnection();
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            return CompletableFuture.completedFuture(List.of("test-model"));
        }

        @Override
        public boolean supportsImages() {
            return false;
        }

        @Override
        public AiMode getMode() {
            return mode;
        }

        @Override
        public String getDefaultModel() {
            return "test-model";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getBaseUrl() {
            return "stub://test";
        }

        @Override
        public void reloadConfiguration() {
            // no-op for tests
        }
    }
}
