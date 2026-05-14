package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SmartCategorizationService Bulk Writes")
class SmartCategorizationServiceBulkWriteTest {

    @Test
    @DisplayName("categorizeAndSave flushes tag updates in configured chunks")
    void categorizeAndSaveFlushesInChunks() {
        RecordingTaskApplicationService service = new RecordingTaskApplicationService();
        SmartCategorizationService categorizationService = new SmartCategorizationService(service, 2, 1);

        List<Task> tasks = List.of(
            task("t1", "alpha"),
            task("t2", "beta"),
            task("t3", "gamma"),
            task("t4", "delta"),
            task("t5", "epsilon")
        );

        categorizationService.categorizeAndSave(tasks);

        assertEquals(3, service.tagUpdateBatches.size());
        assertEquals(2, service.tagUpdateBatches.get(0).size());
        assertEquals(2, service.tagUpdateBatches.get(1).size());
        assertEquals(1, service.tagUpdateBatches.get(2).size());
        assertEquals(0, service.saveTasksBulkCalls);
    }

    @Test
    @DisplayName("categorizeAndSave skips bulk write when no tags changed")
    void categorizeAndSaveSkipsUnchangedTags() {
        RecordingTaskApplicationService service = new RecordingTaskApplicationService();
        SmartCategorizationService categorizationService = new SmartCategorizationService(service, 2, 1);

        Task task = task("t1", "готовая задача");
        task.setTags("работа");

        categorizationService.categorizeAndSave(List.of(task));

        assertTrue(service.tagUpdateBatches.isEmpty());
        assertEquals(0, service.saveTasksBulkCalls);
    }

    private Task task(String id, String title) {
        return new Task(id, title, "", LocalDate.now().plusDays(1), 2);
    }

    private static final class RecordingTaskApplicationService implements TaskApplicationService {
        private final List<Map<String, String>> tagUpdateBatches = new ArrayList<>();
        private int saveTasksBulkCalls;

        @Override
        public List<Task> loadTasks() {
            return List.of();
        }

        @Override
        public void saveTask(Task task) {
            // not used
        }

        @Override
        public void saveTasks(List<Task> tasks) {
            // not used
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
            saveTasksBulkCalls++;
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
            Map<String, String> snapshot = new LinkedHashMap<>();
            if (tagsByTaskId != null) {
                snapshot.putAll(tagsByTaskId);
            }
            tagUpdateBatches.add(snapshot);
            int processed = snapshot.size();
            return new TaskBulkOperationResult("updateTaskTagsBatch", processed, processed, 0, 1, 0);
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
            // not used
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
            // not used
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
            // not used
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
}
