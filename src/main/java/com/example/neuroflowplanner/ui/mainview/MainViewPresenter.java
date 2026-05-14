package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.ui.AsyncErrorHandler;
import com.example.neuroflowplanner.ui.interaction.CompositeCommand;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class MainViewPresenter implements MainViewContract.Presenter {
    private final MainViewContract.View view;
    private final MainViewServices services;
    private final UndoRedoManager undoRedoManager;
    private MainViewState state;

    public MainViewPresenter(MainViewContract.View view, MainViewServices services) {
        this.view = view;
        this.services = services;
        this.undoRedoManager = UndoRedoManager.fromConfig();
        this.state = withUndoRedoState(MainViewState.initial());
    }

    @Override
    public void initialize() {
        reloadState("ready");
    }

    @Override
    public boolean canCloseApplication() {
        return view.canCloseApplication();
    }

    public List<Task> loadTasks() {
        return services.taskApplicationService().loadTasks();
    }

    public void saveTask(Task task) {
        services.taskApplicationService().saveTask(task);
    }

    public void saveTasks(List<Task> tasks) {
        services.taskApplicationService().saveTasks(tasks);
    }

    public UndoRedoManager.CommandResult addTaskUndoable(Task task) {
        String taskId = task == null ? null : normalizeTaskId(task.getId());
        if (task == null || taskId == null) {
            return skipped("task.add", "Task is required for add");
        }
        UserActionCommand command = snapshotBackedCommand(
            "task.add",
            "Добавить: " + safeTaskLabel(task),
            "task",
            () -> services.taskApplicationService().saveTask(task)
        );
        return executeUndoableCommand(command, "Задача добавлена");
    }

    public UndoRedoManager.CommandResult editTaskUndoable(Task task) {
        String taskId = task == null ? null : normalizeTaskId(task.getId());
        if (task == null || taskId == null) {
            return skipped("task.edit", "Task is required for edit");
        }
        UserActionCommand command = snapshotBackedCommand(
            "task.edit",
            "Изменить: " + safeTaskLabel(task),
            "task",
            () -> services.taskApplicationService().saveTask(task)
        );
        return executeUndoableCommand(command, "Изменения задачи сохранены");
    }

    public void deleteTask(String taskId) {
        services.taskApplicationService().deleteTask(taskId);
    }

    public UndoRedoManager.CommandResult deleteTaskUndoable(String taskId) {
        String normalizedTaskId = normalizeTaskId(taskId);
        if (normalizedTaskId == null) {
            return skipped("task.delete", "Task id is required for delete");
        }
        UserActionCommand command = snapshotBackedCommand(
            "task.delete",
            "Удалить: " + normalizedTaskId,
            "task",
            () -> services.taskApplicationService().deleteTask(normalizedTaskId)
        );
        return executeUndoableCommand(command, "Задача удалена");
    }

    public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
        return services.taskApplicationService().archiveTasksBulk(taskIds, includeSubtasks);
    }

    public UndoRedoManager.CommandResult archiveTasksUndoable(List<String> taskIds, boolean includeSubtasks) {
        List<String> normalizedTaskIds = normalizeTaskIds(taskIds);
        if (normalizedTaskIds.isEmpty()) {
            return skipped("task.bulk.archive", "No task ids provided for archive");
        }
        UserActionCommand archiveCommand = snapshotBackedCommand(
            "task.bulk.archive.execute",
            "Архивировать " + normalizedTaskIds.size() + " задач",
            "task.bulk",
            () -> services.taskApplicationService().archiveTasksBulk(normalizedTaskIds, includeSubtasks)
        );
        CompositeCommand command = new CompositeCommand(
            "task.bulk.archive",
            "Архивировать задачи (" + normalizedTaskIds.size() + ")",
            "task.bulk",
            List.of(archiveCommand)
        );
        return executeUndoableCommand(command, "Выбранные задачи архивированы");
    }

    public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
        return services.taskApplicationService().deleteTasksBulk(taskIds);
    }

    public UndoRedoManager.CommandResult deleteTasksUndoable(List<String> taskIds) {
        List<String> normalizedTaskIds = normalizeTaskIds(taskIds);
        if (normalizedTaskIds.isEmpty()) {
            return skipped("task.bulk.delete", "No task ids provided for delete");
        }
        UserActionCommand deleteCommand = snapshotBackedCommand(
            "task.bulk.delete.execute",
            "Удалить " + normalizedTaskIds.size() + " задач",
            "task.bulk",
            () -> services.taskApplicationService().deleteTasksBulk(normalizedTaskIds)
        );
        CompositeCommand command = new CompositeCommand(
            "task.bulk.delete",
            "Удалить задачи (" + normalizedTaskIds.size() + ")",
            "task.bulk",
            List.of(deleteCommand)
        );
        return executeUndoableCommand(command, "Выбранные задачи удалены");
    }

    public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
        return services.taskApplicationService().updateTaskTagsBulk(tagsByTaskId);
    }

    public UndoRedoManager.CommandResult updateTaskTagsUndoable(Map<String, String> tagsByTaskId) {
        Map<String, String> normalizedTagsByTaskId = normalizeTagsByTaskId(tagsByTaskId);
        if (normalizedTagsByTaskId.isEmpty()) {
            return skipped("task.bulk.tags", "No valid tag updates provided");
        }
        UserActionCommand updateTagsCommand = snapshotBackedCommand(
            "task.bulk.tags.execute",
            "Обновить теги для " + normalizedTagsByTaskId.size() + " задач",
            "task.bulk",
            () -> services.taskApplicationService().updateTaskTagsBulk(normalizedTagsByTaskId)
        );
        CompositeCommand command = new CompositeCommand(
            "task.bulk.tags",
            "Добавить тег (" + normalizedTagsByTaskId.size() + ")",
            "task.bulk",
            List.of(updateTagsCommand)
        );
        return executeUndoableCommand(command, "Теги задач обновлены");
    }

    public void linkDependency(String dependentTaskId, String blockerTaskId) {
        services.taskApplicationService().linkDependency(dependentTaskId, blockerTaskId);
    }

    public UndoRedoManager.CommandResult linkDependencyUndoable(String dependentTaskId, String blockerTaskId) {
        String dependentId = normalizeTaskId(dependentTaskId);
        String blockerId = normalizeTaskId(blockerTaskId);
        if (dependentId == null || blockerId == null) {
            return skipped("task.linkDependency", "Both dependent and blocker task ids are required");
        }
        UserActionCommand command = snapshotBackedCommand(
            "task.linkDependency",
            "Связать задачи",
            "task",
            () -> services.taskApplicationService().linkDependency(dependentId, blockerId)
        );
        return executeUndoableCommand(command, "Зависимость между задачами добавлена");
    }

    public void unlinkDependency(String dependentTaskId, String blockerTaskId) {
        String dependentId = normalizeTaskId(dependentTaskId);
        String blockerId = normalizeTaskId(blockerTaskId);
        if (dependentId == null || blockerId == null) {
            return;
        }
        List<String> dependencies = new ArrayList<>(services.taskApplicationService().loadDependencies(dependentId));
        dependencies.removeIf(blockerId::equals);
        services.taskApplicationService().saveDependencies(dependentId, dependencies);
    }

    public UndoRedoManager.CommandResult unlinkDependencyUndoable(String dependentTaskId, String blockerTaskId) {
        String dependentId = normalizeTaskId(dependentTaskId);
        String blockerId = normalizeTaskId(blockerTaskId);
        if (dependentId == null || blockerId == null) {
            return skipped("task.unlinkDependency", "Both dependent and blocker task ids are required");
        }
        UserActionCommand command = snapshotBackedCommand(
            "task.unlinkDependency",
            "Удалить связь задач",
            "task",
            () -> unlinkDependency(dependentId, blockerId)
        );
        return executeUndoableCommand(command, "Зависимость между задачами удалена");
    }

    public void saveDependencies(String taskId, List<String> blockerTaskIds) {
        services.taskApplicationService().saveDependencies(taskId, blockerTaskIds);
    }

    public List<String> loadDependencies(String taskId) {
        return services.taskApplicationService().loadDependencies(taskId);
    }

    public List<TaskDependencyEdge> loadAllDependencyEdges() {
        return services.taskApplicationService().loadAllDependencyEdges();
    }

    public List<String> loadDependents(String taskId) {
        String blockerId = normalizeTaskId(taskId);
        if (blockerId == null) {
            return List.of();
        }
        LinkedHashSet<String> dependents = new LinkedHashSet<>();
        for (TaskDependencyEdge edge : loadAllDependencyEdges()) {
            if (edge == null) {
                continue;
            }
            if (blockerId.equals(normalizeTaskId(edge.blockerTaskId()))) {
                String dependentId = normalizeTaskId(edge.dependentTaskId());
                if (dependentId != null) {
                    dependents.add(dependentId);
                }
            }
        }
        return List.copyOf(dependents);
    }

    public CriticalPathResult computeCriticalPathFullGraph() {
        return services.taskApplicationService().computeCriticalPathFullGraph();
    }

    public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
        return services.taskApplicationService().computeCriticalPathForRootTask(rootTaskId);
    }

    public List<TaskTemplate> loadAllTemplates() {
        return services.taskApplicationService().loadAllTemplates();
    }

    public void saveTemplate(TaskTemplate template) {
        services.taskApplicationService().saveTemplate(template);
    }

    public void calculatePriority(Task task) {
        services.taskAnalysisService().calculatePriority(task);
    }

    public CompletableFuture<String> analyzeTask(Task task) {
        return services.taskAnalysisService().analyzeTask(task);
    }

    public CompletableFuture<String> analyzeTaskObserved(Task task, Window owner, boolean darkTheme) {
        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<String> observed = AsyncErrorHandler.observeFuture(
            services.taskAnalysisService().analyzeTask(task),
            owner,
            darkTheme,
            "Ошибка анализа задачи",
            ErrorCode.AI_REQUEST_FAILED,
            "Не удалось проанализировать задачу. Попробуйте позже.",
            true,
            "mainview.ai.analysis.failed",
            "operation", "analyzeTask",
            "taskId", task != null ? task.getId() : "",
            "taskTitle", task != null ? task.getTitle() : "",
            "requestId", requestId
        );
        return observed.thenApply(insight -> {
            if (task != null) {
                task.setAiInsight(insight);
                saveTask(task);
            }
            return insight;
        });
    }

    public CompletableFuture<String> prioritizeWithAi(List<Task> tasks) {
        return services.taskAnalysisService().prioritizeWithAi(tasks);
    }

    public String autoSchedule(List<Task> tasks, int dailyComplexityBudget) {
        return services.taskAnalysisService().autoSchedule(tasks, dailyComplexityBudget);
    }

    public CompletableFuture<String> predictTime(Task task) {
        return services.taskAnalysisService().predictTime(task);
    }

    public CompletableFuture<String> recommendations(List<Task> tasks) {
        return services.taskAnalysisService().recommendations(tasks);
    }

    public CompletableFuture<String> productivityAnalysis(List<Task> tasks) {
        return services.taskAnalysisService().productivityAnalysis(tasks);
    }

    public void exportInsight(File file, String extension, String content) throws Exception {
        services.taskExportService().exportInsight(file, extension, content);
    }

    public List<Task> filterScheduled(List<Task> source) {
        List<Task> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (Task task : source) {
            if (!task.isArchived() && !task.isStarted()) {
                out.add(task);
            }
        }
        return out;
    }

    public List<Task> filterUrgent(List<Task> source, double minPriority) {
        List<Task> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        for (Task task : source) {
            if (task.getSmartPriority() >= minPriority) {
                out.add(task);
            }
        }
        return out;
    }

    public List<Task> filterByTag(List<Task> source, String tag) {
        List<Task> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        String needle = tag == null ? "" : tag.trim().toLowerCase();
        if (needle.isEmpty()) {
            return new ArrayList<>(source);
        }
        for (Task task : source) {
            if (task.getTags().toLowerCase().contains(needle)) {
                out.add(task);
                continue;
            }
            for (Task sub : task.getSubtasks()) {
                if (sub.getTags().toLowerCase().contains(needle)) {
                    out.add(task);
                    break;
                }
            }
        }
        return out;
    }

    public List<GlobalSearchResult> searchGlobal(String query, int limit) {
        if (!ConfigManager.isUxGlobalSearchEnabled()) {
            return List.of();
        }
        return services.globalSearchService().search(query, limit);
    }

    public boolean openGlobalSearchResult(GlobalSearchResult result) {
        if (result == null || result.navigationTarget() == null || result.navigationTarget().isEmpty()) {
            return false;
        }

        boolean opened;
        if (result.navigationTarget().type() == GlobalSearchResultType.TASK) {
            opened = view.openTaskById(result.navigationTarget().targetId());
            if (opened) {
                updateUndoRedoStateOnly("Открыта задача: " + result.title());
            }
            return opened;
        }

        opened = view.openNoteByTitle(result.navigationTarget().targetId());
        if (opened) {
            updateUndoRedoStateOnly("Открыта заметка: " + result.title());
        }
        return opened;
    }

    public MainViewState getState() {
        return state;
    }

    public MainViewServices getServices() {
        return services;
    }

    public UndoRedoManager.CommandResult undoLastAction() {
        UndoRedoManager.CommandResult result = undoRedoManager.undo();
        if (result.successful()) {
            reloadState("Отмена действия: " + result.actionId());
            return result;
        }
        updateUndoRedoStateOnly(result.message());
        return result;
    }

    public UndoRedoManager.CommandResult redoLastAction() {
        UndoRedoManager.CommandResult result = undoRedoManager.redo();
        if (result.successful()) {
            reloadState("Повтор действия: " + result.actionId());
            return result;
        }
        updateUndoRedoStateOnly(result.message());
        return result;
    }

    private CriticalPathResult computeCriticalPathSafely() {
        try {
            return services.taskApplicationService().computeCriticalPathFullGraph();
        } catch (RuntimeException ex) {
            return CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null);
        }
    }

    private UndoRedoManager.CommandResult executeUndoableCommand(
        UserActionCommand command,
        String successStatusMessage
    ) {
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
        UndoRedoManager.CommandResult result = undoRedoManager.execute(
            trackFailure(command, failureRef)
        );
        if (result.successful()) {
            reloadState(successStatusMessage);
            return result;
        }
        updateUndoRedoStateOnly(result.message());
        RuntimeException failure = failureRef.get();
        if (failure != null) {
            throw failure;
        }
        throw new IllegalStateException(result.message());
    }

    private UserActionCommand trackFailure(UserActionCommand delegate, AtomicReference<RuntimeException> failureRef) {
        return new UserActionCommand() {
            @Override
            public String actionId() {
                return delegate.actionId();
            }

            @Override
            public String label() {
                return delegate.label();
            }

            @Override
            public String category() {
                return delegate.category();
            }

            @Override
            public boolean canExecute() {
                return delegate.canExecute();
            }

            @Override
            public boolean canUndo() {
                return delegate.canUndo();
            }

            @Override
            public void execute() {
                try {
                    delegate.execute();
                } catch (RuntimeException ex) {
                    failureRef.set(ex);
                    throw ex;
                }
            }

            @Override
            public void undo() {
                delegate.undo();
            }
        };
    }

    private UserActionCommand snapshotBackedCommand(
        String actionId,
        String label,
        String category,
        Runnable mutation
    ) {
        TaskGraphSnapshot beforeState = captureTaskGraphSnapshot();
        return new UserActionCommand() {
            @Override
            public String actionId() {
                return actionId;
            }

            @Override
            public String label() {
                return label;
            }

            @Override
            public String category() {
                return category;
            }

            @Override
            public void execute() {
                mutation.run();
            }

            @Override
            public void undo() {
                restoreTaskGraphSnapshot(beforeState);
            }
        };
    }

    private TaskGraphSnapshot captureTaskGraphSnapshot() {
        List<TaskSnapshot> taskSnapshots = new ArrayList<>();
        for (Task task : flattenTasks(services.taskApplicationService().loadTasks())) {
            taskSnapshots.add(TaskSnapshot.fromTask(task));
        }

        Map<String, LinkedHashSet<String>> blockersByTaskId = new LinkedHashMap<>();
        for (TaskDependencyEdge edge : services.taskApplicationService().loadAllDependencyEdges()) {
            if (edge == null) {
                continue;
            }
            String dependentId = normalizeTaskId(edge.dependentTaskId());
            String blockerId = normalizeTaskId(edge.blockerTaskId());
            if (dependentId == null || blockerId == null) {
                continue;
            }
            blockersByTaskId
                .computeIfAbsent(dependentId, ignored -> new LinkedHashSet<>())
                .add(blockerId);
        }

        Map<String, List<String>> normalizedBlockers = new LinkedHashMap<>();
        for (TaskSnapshot taskSnapshot : taskSnapshots) {
            normalizedBlockers.put(
                taskSnapshot.id(),
                List.copyOf(blockersByTaskId.getOrDefault(taskSnapshot.id(), new LinkedHashSet<>()))
            );
        }
        return new TaskGraphSnapshot(List.copyOf(taskSnapshots), Map.copyOf(normalizedBlockers));
    }

    private void restoreTaskGraphSnapshot(TaskGraphSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        List<Task> currentTasks = flattenTasks(services.taskApplicationService().loadTasks());
        Set<String> targetIds = snapshot.taskIds();
        List<String> toDelete = new ArrayList<>();
        for (Task currentTask : currentTasks) {
            String taskId = normalizeTaskId(currentTask.getId());
            if (taskId != null && !targetIds.contains(taskId)) {
                toDelete.add(taskId);
            }
        }
        if (!toDelete.isEmpty()) {
            services.taskApplicationService().deleteTasksBulk(toDelete);
        }

        List<Task> tasksToRestore = snapshot.toTasks();
        if (!tasksToRestore.isEmpty()) {
            services.taskApplicationService().saveTasksBulk(tasksToRestore);
        }

        for (String taskId : targetIds) {
            List<String> blockers = snapshot.blockersByTaskId().getOrDefault(taskId, List.of());
            services.taskApplicationService().saveDependencies(taskId, blockers);
        }
    }

    private List<Task> flattenTasks(List<Task> rootTasks) {
        if (rootTasks == null || rootTasks.isEmpty()) {
            return List.of();
        }
        List<Task> flattened = new ArrayList<>();
        for (Task rootTask : rootTasks) {
            collectTasks(rootTask, flattened);
        }
        return flattened;
    }

    private void collectTasks(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            collectTasks(subtask, sink);
        }
    }

    private MainViewState withUndoRedoState(MainViewState source) {
        return source.withUndoRedoState(
            undoRedoManager.canUndo(),
            undoRedoManager.canRedo(),
            undoRedoManager.nextUndoLabel(),
            undoRedoManager.nextRedoLabel()
        );
    }

    private void reloadState(String statusMessage) {
        List<Task> loaded = services.taskApplicationService().loadTasks();
        CriticalPathResult criticalPath = computeCriticalPathSafely();
        MainViewState next = state.markInitialized(loaded, criticalPath);
        if (statusMessage != null && !statusMessage.isBlank()) {
            next = next.withStatusMessage(statusMessage);
        }
        state = withUndoRedoState(next);
        view.render(state);
    }

    private void updateUndoRedoStateOnly(String statusMessage) {
        MainViewState next = withUndoRedoState(state);
        if (statusMessage != null && !statusMessage.isBlank()) {
            next = next.withStatusMessage(statusMessage);
        }
        state = next;
        view.render(state);
    }

    private UndoRedoManager.CommandResult skipped(String actionId, String message) {
        UndoRedoManager.CommandResult result = UndoRedoManager.CommandResult.skipped(
            UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE,
            actionId,
            message
        );
        updateUndoRedoStateOnly(message);
        return result;
    }

    private List<String> normalizeTaskIds(List<String> rawTaskIds) {
        if (rawTaskIds == null || rawTaskIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawTaskId : rawTaskIds) {
            String taskId = normalizeTaskId(rawTaskId);
            if (taskId != null) {
                normalized.add(taskId);
            }
        }
        return List.copyOf(normalized);
    }

    private Map<String, String> normalizeTagsByTaskId(Map<String, String> rawTagsByTaskId) {
        if (rawTagsByTaskId == null || rawTagsByTaskId.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawTagsByTaskId.entrySet()) {
            String taskId = normalizeTaskId(entry.getKey());
            if (taskId == null) {
                continue;
            }
            String tags = entry.getValue() == null ? "" : entry.getValue().trim();
            normalized.put(taskId, tags);
        }
        if (normalized.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(normalized);
    }

    private String safeTaskLabel(Task task) {
        if (task == null) {
            return "задача";
        }
        String title = task.getTitle();
        if (title == null || title.isBlank()) {
            return task.getId() == null || task.getId().isBlank() ? "задача" : task.getId();
        }
        return title.trim();
    }

    private String normalizeTaskId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record TaskGraphSnapshot(List<TaskSnapshot> tasks, Map<String, List<String>> blockersByTaskId) {
        private TaskGraphSnapshot {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
            blockersByTaskId = blockersByTaskId == null ? Map.of() : Map.copyOf(blockersByTaskId);
        }

        private Set<String> taskIds() {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            for (TaskSnapshot task : tasks) {
                ids.add(task.id());
            }
            return ids;
        }

        private List<Task> toTasks() {
            if (tasks.isEmpty()) {
                return List.of();
            }
            List<Task> restored = new ArrayList<>(tasks.size());
            for (TaskSnapshot task : tasks) {
                restored.add(task.toTask());
            }
            return restored;
        }
    }

    private record TaskSnapshot(
        String id,
        String title,
        String description,
        java.time.LocalDate deadline,
        int complexity,
        double smartPriority,
        String aiInsight,
        String parentId,
        String tags,
        String recurrence,
        String dependsOn,
        boolean archived,
        long trackedMinutes,
        java.time.LocalDate startDate,
        java.time.LocalTime startTime,
        boolean completed,
        java.time.LocalDate completedDate,
        java.time.LocalTime deadlineTime
    ) {
        private static TaskSnapshot fromTask(Task task) {
            return new TaskSnapshot(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getDeadline(),
                task.getComplexity(),
                task.getSmartPriority(),
                task.getAiInsight(),
                task.getParentId(),
                task.getTags(),
                task.getRecurrence(),
                task.getDependsOn(),
                task.isArchived(),
                task.getTrackedMinutes(),
                task.getStartDate(),
                task.getStartTime(),
                task.isCompleted(),
                task.getCompletedDate(),
                task.getDeadlineTime()
            );
        }

        private Task toTask() {
            Task restored = new Task(
                id,
                title,
                description,
                deadline,
                complexity,
                parentId,
                tags,
                recurrence
            );
            restored.setSmartPriority(smartPriority);
            restored.setAiInsight(aiInsight);
            restored.setDependsOn(dependsOn);
            restored.setArchived(archived);
            restored.setTrackedMinutes(trackedMinutes);
            restored.setStartDate(startDate);
            restored.setStartTime(startTime);
            restored.setCompleted(completed);
            restored.setCompletedDate(completedDate);
            restored.setDeadlineTime(deadlineTime);
            return restored;
        }
    }
}
