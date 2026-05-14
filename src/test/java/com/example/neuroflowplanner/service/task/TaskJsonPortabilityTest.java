package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Task JSON portability")
class TaskJsonPortabilityTest {

    @Test
    @DisplayName("serializer writes canonical task export schema envelope")
    void serializerWritesCanonicalTaskExportEnvelope() throws Exception {
        DefaultTaskExportService exportService = new DefaultTaskExportService();
        Task root = new Task("task-root", "Родитель", "Описание", LocalDate.of(2030, 5, 10), 4);
        root.setTags("alpha");
        Task child = new Task("task-child", "Подзадача", "", LocalDate.of(2030, 5, 8), 2);
        root.getSubtasks().add(child);

        String json = exportService.serializeTasksJson(List.of(root));

        assertTrue(json.contains("\"schemaType\" : \"task-export\""));
        assertTrue(json.contains("\"schemaVersion\" : 1"));
        assertTrue(json.contains("\"exportedAt\""));
        assertTrue(json.contains("\"source\""));
        assertTrue(json.contains("\"module\" : \"tasks\""));
        assertTrue(json.contains("\"parentId\" : \"task-root\""));
    }

    @Test
    @DisplayName("task JSON round-trip remains compatible with import preview")
    void taskJsonRoundTripRemainsCompatibleWithImportPreview() throws Exception {
        DefaultTaskExportService exportService = new DefaultTaskExportService();
        DefaultTaskImportService importService = new DefaultTaskImportService(
            new InMemoryTaskApplicationService(),
            new InMemoryTaskAnalysisService()
        );

        Task root = new Task("task-root", "Родитель", "Описание", LocalDate.of(2030, 5, 10), 4);
        root.setStartDate(LocalDate.of(2030, 5, 1));
        Task child = new Task("task-child", "Подзадача", "", LocalDate.of(2030, 5, 8), 2);
        child.setCompleted(true);
        child.setCompletedDate(LocalDate.of(2030, 5, 7));
        root.getSubtasks().add(child);

        TaskImportService.ImportPreview preview = importService.dryRun(
            exportService.serializeTasksJson(List.of(root)),
            TaskImportService.ImportFormat.JSON,
            TaskImportService.ImportOptions.defaults()
        );

        assertEquals(2, preview.sourceCount());
        assertEquals(2, preview.acceptedCount());
        assertEquals(0, preview.invalidCount());
        Task importedChild = preview.tasksToPersist().stream()
            .filter(task -> "task-child".equals(task.getId()))
            .findFirst()
            .orElseThrow();
        assertEquals("task-root", importedChild.getParentId());
        assertTrue(importedChild.isCompleted());
        assertEquals(LocalDate.of(2030, 5, 7), importedChild.getCompletedDate());
    }

    private static final class InMemoryTaskApplicationService implements TaskApplicationService {
        @Override
        public List<Task> loadTasks() {
            return List.of();
        }

        @Override
        public void saveTask(Task task) {
        }

        @Override
        public void saveTasks(List<Task> tasks) {
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
            int count = tasks == null ? 0 : tasks.size();
            return new TaskBulkOperationResult("saveTasksBatch", count, count, 0, 1, 0);
        }

        @Override
        public void deleteTask(String taskId) {
        }

        @Override
        public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
            int count = taskIds == null ? 0 : taskIds.size();
            return new TaskBulkOperationResult("archiveTasksBatch", count, count, 0, 1, 0);
        }

        @Override
        public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
            int count = taskIds == null ? 0 : taskIds.size();
            return new TaskBulkOperationResult("deleteTasksBatch", count, count, 0, 1, 0);
        }

        @Override
        public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
            int count = tagsByTaskId == null ? 0 : tagsByTaskId.size();
            return new TaskBulkOperationResult("updateTaskTagsBatch", count, count, 0, 1, 0);
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
        }

        @Override
        public List<String> loadDependencies(String taskId) {
            return List.of();
        }

        @Override
        public List<TaskDependencyEdge> loadAllDependencyEdges() {
            return List.of();
        }

        @Override
        public void deleteDependenciesForTask(String taskId) {
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
        }
    }

    private static final class InMemoryTaskAnalysisService implements TaskAnalysisService {
        @Override
        public void calculatePriority(Task task) {
            if (task != null) {
                task.setSmartPriority(Math.max(1.0, task.getComplexity()));
            }
        }

        @Override
        public CompletableFuture<String> analyzeTask(Task task) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public CompletableFuture<String> prioritizeWithAi(List<Task> tasks) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public String autoSchedule(List<Task> tasks, int workHoursPerDay) {
            return "";
        }

        @Override
        public CompletableFuture<String> predictTime(Task task) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public CompletableFuture<String> recommendations(List<Task> tasks) {
            return CompletableFuture.completedFuture("");
        }

        @Override
        public CompletableFuture<String> productivityAnalysis(List<Task> tasks) {
            return CompletableFuture.completedFuture("");
        }
    }
}
