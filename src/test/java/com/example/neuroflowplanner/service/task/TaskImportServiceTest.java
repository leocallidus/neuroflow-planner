package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Task import service")
class TaskImportServiceTest {

    @Test
    @DisplayName("dry-run parses JSON and applies id/title dedupe policies")
    void dryRunJsonAppliesDedupePolicies() {
        RecordingTaskApplicationService appService = new RecordingTaskApplicationService();
        appService.loadedTasks.add(task("existing-id", "Existing title", LocalDate.now().plusDays(5)));
        RecordingTaskAnalysisService analysisService = new RecordingTaskAnalysisService();

        DefaultTaskImportService importService = new DefaultTaskImportService(appService, analysisService);

        String payload = """
            [
              {"id":"dup-id","title":"Первый дубль","deadline":"2030-01-01","complexity":2},
              {"id":"dup-id","title":"Второй дубль","deadline":"2030-01-02","complexity":3},
              {"title":"Existing title","deadline":"2030-01-03","complexity":4},
              {"title":"", "deadline":"2030-01-04"}
            ]
            """;

        TaskImportService.ImportPreview preview = importService.dryRun(
            payload,
            TaskImportService.ImportFormat.JSON,
            TaskImportService.ImportOptions.defaults()
        );

        assertEquals(4, preview.sourceCount());
        assertEquals(1, preview.acceptedCount());
        assertEquals(1, preview.toCreateCount());
        assertEquals(0, preview.toUpdateCount());
        assertEquals(1, preview.duplicateIdCount());
        assertEquals(1, preview.duplicateTitleCount());
        assertEquals(1, preview.invalidCount());
        assertEquals(1, preview.tasksToPersist().size());
        assertEquals("Второй дубль", preview.tasksToPersist().get(0).getTitle());
        assertEquals(1, analysisService.calculatePriorityCalls);
    }

    @Test
    @DisplayName("apply persists CSV import atomically via saveTasksBulk")
    void applyCsvUsesBulkSave() {
        RecordingTaskApplicationService appService = new RecordingTaskApplicationService();
        RecordingTaskAnalysisService analysisService = new RecordingTaskAnalysisService();
        DefaultTaskImportService importService = new DefaultTaskImportService(appService, analysisService);

        String payload = """
            id,title,deadline,complexity,tags
            task-1,Task One,2030-02-10,2,work
            task-2,Task Two,2030-02-11,5,home
            """;

        TaskImportService.ImportResult result = importService.apply(
            payload,
            TaskImportService.ImportFormat.CSV,
            TaskImportService.ImportOptions.defaults()
        );

        assertEquals(2, result.preview().acceptedCount());
        assertEquals(1, appService.saveTasksBulkCalls);
        assertEquals(2, appService.savedTasks.size());
        assertEquals(2, result.bulkResult().processedCount());
        assertTrue(result.bulkResult().isSuccessful());
        assertEquals(2, analysisService.calculatePriorityCalls);
    }

    @Test
    @DisplayName("task JSON export stays compatible with import dry-run and flattens subtasks via parentId")
    void exportedJsonRoundTripsIntoImportPreview() throws Exception {
        RecordingTaskApplicationService appService = new RecordingTaskApplicationService();
        RecordingTaskAnalysisService analysisService = new RecordingTaskAnalysisService();
        DefaultTaskImportService importService = new DefaultTaskImportService(appService, analysisService);
        DefaultTaskExportService exportService = new DefaultTaskExportService();

        Task root = new Task("root-1", "Родитель", "Описание", LocalDate.of(2030, 4, 10), 4);
        root.setTags("alpha,beta");
        root.setRecurrence("weekly");
        root.setTrackedMinutes(120);
        root.setStartDate(LocalDate.of(2030, 4, 1));

        Task child = new Task("child-1", "Подзадача", "", LocalDate.of(2030, 4, 8), 2);
        child.setCompleted(true);
        child.setCompletedDate(LocalDate.of(2030, 4, 7));
        root.getSubtasks().add(child);

        String json = exportService.serializeTasksJson(List.of(root));

        TaskImportService.ImportPreview preview = importService.dryRun(
            json,
            TaskImportService.ImportFormat.JSON,
            TaskImportService.ImportOptions.defaults()
        );

        assertEquals(2, preview.sourceCount());
        assertEquals(2, preview.acceptedCount());
        assertEquals(2, preview.toCreateCount());
        assertEquals(0, preview.invalidCount());
        assertEquals(2, preview.tasksToPersist().size());

        Task importedRoot = preview.tasksToPersist().stream()
            .filter(task -> "root-1".equals(task.getId()))
            .findFirst()
            .orElseThrow();
        Task importedChild = preview.tasksToPersist().stream()
            .filter(task -> "child-1".equals(task.getId()))
            .findFirst()
            .orElseThrow();

        assertEquals("alpha,beta", importedRoot.getTags());
        assertEquals("weekly", importedRoot.getRecurrence());
        assertEquals(120L, importedRoot.getTrackedMinutes());
        assertEquals(LocalDate.of(2030, 4, 1), importedRoot.getStartDate());
        assertEquals("root-1", importedChild.getParentId());
        assertTrue(importedChild.isCompleted());
        assertEquals(LocalDate.of(2030, 4, 7), importedChild.getCompletedDate());
    }

    private static Task task(String id, String title, LocalDate deadline) {
        return new Task(id, title, "", deadline, 3);
    }

    private static final class RecordingTaskApplicationService implements TaskApplicationService {
        private final List<Task> loadedTasks = new ArrayList<>();
        private final List<Task> savedTasks = new ArrayList<>();
        private final Map<String, LinkedHashSet<String>> blockers = new LinkedHashMap<>();
        private int saveTasksBulkCalls;

        @Override
        public List<Task> loadTasks() {
            return new ArrayList<>(loadedTasks);
        }

        @Override
        public void saveTask(Task task) {
            if (task != null) {
                savedTasks.add(task);
            }
        }

        @Override
        public void saveTasks(List<Task> tasks) {
            if (tasks != null) {
                savedTasks.addAll(tasks);
            }
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
            saveTasksBulkCalls++;
            if (tasks != null) {
                savedTasks.addAll(tasks);
            }
            int processed = tasks == null ? 0 : tasks.size();
            return new TaskBulkOperationResult("saveTasksBatch", processed, processed, 0, 1, 0);
        }

        @Override
        public void deleteTask(String taskId) {
            // not used
        }

        @Override
        public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
            int processed = taskIds == null ? 0 : taskIds.size();
            return new TaskBulkOperationResult("archiveTasksBatch", processed, processed, 0, 1, 0);
        }

        @Override
        public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
            int processed = taskIds == null ? 0 : taskIds.size();
            return new TaskBulkOperationResult("deleteTasksBatch", processed, processed, 0, 1, 0);
        }

        @Override
        public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
            int processed = tagsByTaskId == null ? 0 : tagsByTaskId.size();
            return new TaskBulkOperationResult("updateTaskTagsBatch", processed, processed, 0, 1, 0);
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
            blockers.computeIfAbsent(dependentTaskId, key -> new LinkedHashSet<>()).add(blockerTaskId);
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            if (blockerTaskIds != null) {
                set.addAll(blockerTaskIds);
            }
            blockers.put(taskId, set);
        }

        @Override
        public List<String> loadDependencies(String taskId) {
            return List.copyOf(blockers.getOrDefault(taskId, new LinkedHashSet<>()));
        }

        @Override
        public List<TaskDependencyEdge> loadAllDependencyEdges() {
            return List.of();
        }

        @Override
        public void deleteDependenciesForTask(String taskId) {
            blockers.remove(taskId);
        }

        @Override
        public CriticalPathResult computeCriticalPathFullGraph() {
            return CriticalPathResult.empty(com.example.neuroflowplanner.model.CriticalPathScopeMode.FULL_GRAPH, null);
        }

        @Override
        public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
            return CriticalPathResult.empty(com.example.neuroflowplanner.model.CriticalPathScopeMode.ROOT_TASK, rootTaskId);
        }

        @Override
        public List<TaskTemplate> loadAllTemplates() {
            return List.of();
        }

        @Override
        public void saveTemplate(TaskTemplate template) {
            // not used
        }
    }

    private static final class RecordingTaskAnalysisService implements TaskAnalysisService {
        private int calculatePriorityCalls;

        @Override
        public void calculatePriority(Task task) {
            calculatePriorityCalls++;
            if (task != null) {
                task.setSmartPriority(Math.max(1.0, task.getComplexity()));
            }
        }

        @Override
        public CompletableFuture<String> analyzeTask(Task task) {
            return CompletableFuture.completedFuture("analysis");
        }

        @Override
        public CompletableFuture<String> prioritizeWithAi(List<Task> tasks) {
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public String autoSchedule(List<Task> tasks, int dailyComplexityBudget) {
            return "ok";
        }

        @Override
        public CompletableFuture<String> predictTime(Task task) {
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public CompletableFuture<String> recommendations(List<Task> tasks) {
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public CompletableFuture<String> productivityAnalysis(List<Task> tasks) {
            return CompletableFuture.completedFuture("ok");
        }
    }
}
