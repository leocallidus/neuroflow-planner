package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.service.AISchedulingEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTaskApplicationService implements TaskApplicationService {
    private final DatabaseManager db;
    private final AISchedulingEngine aiEngine;
    private final TaskDependencyGraphService dependencyGraphService;
    private final TaskCriticalPathService criticalPathService;

    public DefaultTaskApplicationService() {
        this(DatabaseManager.getInstance(), new AISchedulingEngine(), new TaskDependencyGraphService(), new TaskCriticalPathService());
    }

    DefaultTaskApplicationService(DatabaseManager db, AISchedulingEngine aiEngine) {
        this(db, aiEngine, new TaskDependencyGraphService(), new TaskCriticalPathService());
    }

    DefaultTaskApplicationService(
            DatabaseManager db,
            AISchedulingEngine aiEngine,
            TaskDependencyGraphService dependencyGraphService,
            TaskCriticalPathService criticalPathService
    ) {
        this.db = db;
        this.aiEngine = aiEngine;
        this.dependencyGraphService = dependencyGraphService;
        this.criticalPathService = criticalPathService;
    }

    @Override
    public List<Task> loadTasks() {
        List<Task> loaded = new ArrayList<>(db.loadAllTasks());
//        if (loaded.isEmpty()) {
//            loaded.addAll(seedSampleTasks());
//        }
        hydrateDependenciesFromNormalizedStore(loaded);
        processRecurringTasks(loaded);
        return loaded;
    }

    @Override
    public void saveTask(Task task) {
        if (task == null) {
            return;
        }
        saveTasksBulk(List.of(task));
        task.setDependsOn(joinDependencyIds(db.loadDependencies(task.getId())));
    }

    @Override
    public void saveTasks(List<Task> tasks) {
        if (tasks == null) {
            return;
        }
        TaskBulkOperationResult result = saveTasksBulk(tasks);
        if (!result.isSuccessful()) {
            throw new IllegalStateException("Task bulk save reported partial failure");
        }
    }

    @Override
    public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
        List<Task> normalizedTasks = normalizeTaskList(tasks);
        if (normalizedTasks.isEmpty()) {
            return new TaskBulkOperationResult("saveTasksBatch", 0, 0, 0, 0, 0);
        }
        DatabaseManager.BulkOperationSummary summary = db.saveTasksBatch(normalizedTasks);
        TaskBulkOperationResult result = toBulkOperationResult(summary);
        refreshDependsOnProjection(normalizedTasks);
        return result;
    }

    @Override
    public void deleteTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        db.deleteTask(taskId);
    }

    @Override
    public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
        DatabaseManager.BulkOperationSummary summary = db.archiveTasksBatch(taskIds, includeSubtasks);
        return toBulkOperationResult(summary);
    }

    @Override
    public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
        DatabaseManager.BulkOperationSummary summary = db.deleteTasksBatch(taskIds);
        return toBulkOperationResult(summary);
    }

    @Override
    public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
        DatabaseManager.BulkOperationSummary summary = db.updateTaskTagsBatch(tagsByTaskId);
        return toBulkOperationResult(summary);
    }

    @Override
    public void linkDependency(String dependentTaskId, String blockerTaskId) {
        String dependentId = normalizeTaskId(dependentTaskId);
        String blockerId = normalizeTaskId(blockerTaskId);
        if (dependentId == null || blockerId == null) {
            throw new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE,
                    "Task dependency link requires non-empty task identifiers",
                    Map.of(
                            "dependentTaskId", dependentTaskId == null ? "" : dependentTaskId,
                            "blockerTaskId", blockerTaskId == null ? "" : blockerTaskId));
        }

        requireTaskExists(dependentId);
        requireTaskExists(blockerId);

        List<TaskDependencyEdge> edges = loadAllDependencyEdges();
        if (dependencyGraphService.wouldCreateCycle(edges, dependentId, blockerId)) {
            throw new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_CYCLE,
                    "Adding dependency would create a cycle",
                    Map.of(
                            "dependentTaskId", dependentId,
                            "blockerTaskId", blockerId));
        }

        List<String> currentBlockers = loadDependencies(dependentId);
        if (currentBlockers.contains(blockerId)) {
            return;
        }
        List<String> updated = new ArrayList<>(currentBlockers);
        updated.add(blockerId);
        saveDependencies(dependentId, updated);
    }

    @Override
    public void saveDependencies(String taskId, List<String> blockerTaskIds) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }

        List<String> normalized = normalizeDependencyIds(blockerTaskIds, taskId);
        db.saveDependencies(taskId, normalized);
    }

    @Override
    public List<String> loadDependencies(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }
        return normalizeDependencyIds(db.loadDependencies(taskId), taskId);
    }

    @Override
    public List<TaskDependencyEdge> loadAllDependencyEdges() {
        return db.loadAllDependencyEdges();
    }

    @Override
    public void deleteDependenciesForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        db.deleteDependenciesForTask(taskId);
    }

    @Override
    public CriticalPathResult computeCriticalPathFullGraph() {
        return criticalPathService.computeFullGraph(flattenTasks(db.loadAllTasks()), loadAllDependencyEdges());
    }

    @Override
    public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
        return criticalPathService.computeForRootTask(rootTaskId, flattenTasks(db.loadAllTasks()), loadAllDependencyEdges());
    }

    @Override
    public List<TaskTemplate> loadAllTemplates() {
        return db.loadAllTemplates();
    }

    @Override
    public void saveTemplate(TaskTemplate template) {
        if (template == null) {
            return;
        }
        db.saveTemplate(template);
    }

    private void processRecurringTasks(List<Task> tasks) {
        LocalDate today = LocalDate.now();
        List<Task> newTasks = new ArrayList<>();
        List<Task> tasksToPersist = new ArrayList<>();
        for (Task task : tasks) {
            if (!task.isRecurring() || task.getDeadline() == null || !task.getDeadline().isBefore(today)) {
                continue;
            }
            LocalDate newDeadline = switch (task.getRecurrence()) {
                case "daily" -> task.getDeadline().plusDays(1);
                case "weekly" -> task.getDeadline().plusWeeks(1);
                case "monthly" -> task.getDeadline().plusMonths(1);
                case "yearly" -> task.getDeadline().plusYears(1);
                default -> task.getDeadline();
            };
            while (newDeadline.isBefore(today)) {
                newDeadline = switch (task.getRecurrence()) {
                    case "daily" -> newDeadline.plusDays(1);
                    case "weekly" -> newDeadline.plusWeeks(1);
                    case "monthly" -> newDeadline.plusMonths(1);
                    case "yearly" -> newDeadline.plusYears(1);
                    default -> newDeadline;
                };
            }

            Task next = new Task(
                java.util.UUID.randomUUID().toString(),
                task.getTitle(),
                task.getDescription(),
                newDeadline,
                task.getComplexity(),
                null,
                task.getTags(),
                task.getRecurrence()
            );
            next.setDeadlineTime(task.getDeadlineTime());
            next.setStartTime(task.getStartTime());
            aiEngine.calculatePriority(next);
            newTasks.add(next);
            tasksToPersist.add(next);

            task.setRecurrence("");
            tasksToPersist.add(task);
        }
        if (!tasksToPersist.isEmpty()) {
            saveTasksBulk(tasksToPersist);
        }
        tasks.addAll(newTasks);
    }

    private List<Task> seedSampleTasks() {
        List<Task> seeded = new ArrayList<>();

        Task work = new Task("Подготовить презентацию", "Слайды для встречи с клиентом", LocalDate.now().plusDays(4), 6);
        work.setTags("работа,презентация");
        aiEngine.calculatePriority(work);
        seeded.add(work);

        Task study = new Task("Пройти модуль по Kubernetes", "Deployments и Services", LocalDate.now().plusDays(7), 7);
        study.setTags("учеба,IT");
        aiEngine.calculatePriority(study);
        seeded.add(study);

        Task personal = new Task("Оплатить коммунальные", "Квартплата и интернет", LocalDate.now().plusDays(2), 2);
        personal.setTags("финансы,личное");
        personal.setRecurrence("monthly");
        aiEngine.calculatePriority(personal);
        seeded.add(personal);

        saveTasksBulk(seeded);

        return seeded;
    }

    private TaskBulkOperationResult toBulkOperationResult(DatabaseManager.BulkOperationSummary summary) {
        if (summary == null) {
            return new TaskBulkOperationResult("unknown", 0, 0, 0, 0, 0);
        }
        if (summary.failedCount() > 0) {
            throw new IllegalStateException(
                "Task bulk operation must be all-or-nothing, failedCount=" + summary.failedCount()
            );
        }
        return new TaskBulkOperationResult(
            summary.operation(),
            summary.processedCount(),
            summary.updatedCount(),
            summary.failedCount(),
            summary.batchCount(),
            summary.durationMs()
        );
    }

    private List<Task> normalizeTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Task> normalized = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (task != null) {
                normalized.add(task);
            }
        }
        return normalized;
    }

    private void refreshDependsOnProjection(List<Task> tasks) {
        for (Task task : tasks) {
            if (task == null || task.getId() == null || task.getId().isBlank()) {
                continue;
            }
            task.setDependsOn(joinDependencyIds(db.loadDependencies(task.getId())));
        }
    }

    private void hydrateDependenciesFromNormalizedStore(List<Task> tasks) {
        Map<String, List<String>> blockersByTaskId = new LinkedHashMap<>();
        for (TaskDependencyEdge edge : db.loadAllDependencyEdges()) {
            if (edge == null) {
                continue;
            }
            blockersByTaskId.computeIfAbsent(edge.dependentTaskId(), key -> new ArrayList<>())
                    .add(edge.blockerTaskId());
        }

        for (Task task : flattenTasks(tasks)) {
            List<String> normalized = normalizeDependencyIds(
                    blockersByTaskId.getOrDefault(task.getId(), List.of()),
                    task.getId());
            task.setDependsOn(joinDependencyIds(normalized));
        }
    }

    private List<Task> flattenTasks(List<Task> rootTasks) {
        List<Task> flattened = new ArrayList<>();
        if (rootTasks == null) {
            return flattened;
        }
        for (Task task : rootTasks) {
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

    private List<String> normalizeDependencyIds(List<String> blockerTaskIds, String taskId) {
        if (blockerTaskIds == null || blockerTaskIds.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String raw : blockerTaskIds) {
            if (raw == null) {
                continue;
            }
            String normalized = raw.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            if (taskId != null && taskId.equals(normalized)) {
                continue;
            }
            deduplicated.add(normalized);
        }
        return new ArrayList<>(deduplicated);
    }

    private String joinDependencyIds(List<String> blockerTaskIds) {
        if (blockerTaskIds == null || blockerTaskIds.isEmpty()) {
            return "";
        }
        return String.join(",", blockerTaskIds);
    }

    private String normalizeTaskId(String rawTaskId) {
        if (rawTaskId == null) {
            return null;
        }
        String normalized = rawTaskId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireTaskExists(String taskId) {
        if (taskExists(taskId)) {
            return;
        }
        throw new TaskDependencyException(
                ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE,
                "Task dependency reference points to missing task",
                Map.of("taskId", taskId));
    }

    private boolean taskExists(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        for (Task task : flattenTasks(db.loadAllTasks())) {
            if (taskId.equals(task.getId())) {
                return true;
            }
        }
        return false;
    }
}
