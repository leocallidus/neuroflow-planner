package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.service.search.GlobalSearchService;
import com.example.neuroflowplanner.service.task.TaskAnalysisService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskDependencyException;
import com.example.neuroflowplanner.service.task.TaskExportService;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewPresenterTest {

    @Test
    void initializeLoadsTasksAndRendersState() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        Task t1 = new Task("task-1", "Task 1", "", LocalDate.now().plusDays(1), 3);
        appService.loadedTasks.add(t1);

        FakeMainView view = new FakeMainView();
        MainViewPresenter presenter = new MainViewPresenter(
            view,
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );

        presenter.initialize();

        assertTrue(view.lastState.initialized());
        assertEquals(1, view.lastState.tasks().size());
        assertEquals("Task 1", view.lastState.tasks().get(0).getTitle());
    }

    @Test
    void filterMethodsUseExpectedRules() {
        FakeMainView view = new FakeMainView();
        MainViewPresenter presenter = new MainViewPresenter(
            view,
            new MainViewServices(new FakeTaskApplicationService(), new FakeTaskAnalysisService(), new FakeTaskExportService())
        );

        Task scheduled = new Task("scheduled", "Scheduled", "", LocalDate.now().plusDays(2), 2);
        scheduled.setStartDate(LocalDate.now().plusDays(1));

        Task started = new Task("started", "Started", "", LocalDate.now().plusDays(2), 2);
        started.setStartDate(LocalDate.now().minusDays(1));

        Task urgent = new Task("urgent", "Urgent", "", LocalDate.now().plusDays(1), 4);
        urgent.setSmartPriority(8.5);

        Task regular = new Task("regular", "Regular", "", LocalDate.now().plusDays(5), 2);
        regular.setSmartPriority(2.0);

        Task taggedParent = new Task("parent", "Parent", "", LocalDate.now().plusDays(5), 2);
        Task taggedSub = new Task("sub", "Sub", "", LocalDate.now().plusDays(5), 2, taggedParent.getId());
        taggedSub.setTags("work,api");
        taggedParent.getSubtasks().add(taggedSub);

        List<Task> all = List.of(scheduled, started, urgent, regular, taggedParent);

        List<Task> scheduledOnly = presenter.filterScheduled(all);
        assertEquals(1, scheduledOnly.size());
        assertTrue(scheduledOnly.contains(scheduled));

        List<Task> urgentOnly = presenter.filterUrgent(all, 6);
        assertEquals(1, urgentOnly.size());
        assertTrue(urgentOnly.contains(urgent));

        List<Task> tagged = presenter.filterByTag(all, "api");
        assertEquals(1, tagged.size());
        assertSame(taggedParent, tagged.get(0));
    }

    @Test
    void delegatesToServicesForPersistenceAnalysisAndExport() throws Exception {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        FakeTaskAnalysisService analysisService = new FakeTaskAnalysisService();
        FakeTaskExportService exportService = new FakeTaskExportService();

        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, analysisService, exportService)
        );

        Task task = new Task("task", "Task", "", LocalDate.now().plusDays(1), 3);
        TaskTemplate template = new TaskTemplate("tpl", "title", "desc", 3, 7, "work");

        presenter.saveTask(task);
        presenter.deleteTask(task.getId());
        presenter.archiveTasksBulk(List.of(task.getId()), true);
        presenter.deleteTasksBulk(List.of(task.getId()));
        presenter.updateTaskTagsBulk(Map.of(task.getId(), "work"));
        presenter.saveTemplate(template);
        presenter.calculatePriority(task);
        presenter.analyzeTask(task).join();
        presenter.prioritizeWithAi(List.of(task)).join();
        presenter.predictTime(task).join();
        presenter.recommendations(List.of(task)).join();
        presenter.productivityAnalysis(List.of(task)).join();
        presenter.exportInsight(new File("/tmp/test-export.md"), ".md", "content");

        assertEquals(1, appService.savedTasks.size());
        assertEquals(2, appService.deletedTaskIds.size());
        assertEquals(1, appService.archiveBulkCalls);
        assertTrue(appService.lastArchiveIncludeSubtasks);
        assertEquals(1, appService.deleteBulkCalls);
        assertEquals(1, appService.updateTagsBulkCalls);
        assertEquals("work", appService.lastTagsBulk.get(task.getId()));
        assertEquals(1, appService.savedTemplates.size());

        assertEquals(1, analysisService.calculatePriorityCalls);
        assertEquals(1, analysisService.analyzeCalls);
        assertEquals(1, analysisService.autoPrioritizeCalls);
        assertEquals(1, analysisService.predictCalls);
        assertEquals(1, analysisService.recommendationCalls);
        assertEquals(1, analysisService.productivityCalls);

        assertEquals(1, exportService.exportCalls);
    }

    @Test
    void dependencyAndCriticalPathOperationsUseApplicationService() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );

        presenter.linkDependency("task-a", "task-b");
        assertEquals(List.of("task-b"), presenter.loadDependencies("task-a"));
        assertEquals(1, presenter.loadAllDependencyEdges().size());
        assertEquals(List.of("task-a"), presenter.loadDependents("task-b"));

        presenter.unlinkDependency("task-a", "task-b");
        assertTrue(presenter.loadDependencies("task-a").isEmpty());
        assertTrue(presenter.loadAllDependencyEdges().isEmpty());

        presenter.computeCriticalPathFullGraph();
        presenter.computeCriticalPathForRootTask("task-a");
        assertEquals(1, appService.computeFullCriticalPathCalls);
        assertEquals(1, appService.computeScopedCriticalPathCalls);
    }

    @Test
    void bulkOperationsDoNotFallbackToPerItemSaveOrDelete() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );

        presenter.archiveTasksBulk(List.of("task-1", "task-2"), true);
        presenter.deleteTasksBulk(List.of("task-1", "task-2"));
        presenter.updateTaskTagsBulk(Map.of("task-1", "tag-a"));

        assertEquals(1, appService.archiveBulkCalls);
        assertEquals(1, appService.deleteBulkCalls);
        assertEquals(1, appService.updateTagsBulkCalls);
        assertEquals(0, appService.saveTaskCalls);
        assertEquals(0, appService.deleteTaskCalls);
    }

    @Test
    void linkDependencyFailFastCycleBubblesDomainError() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        appService.linkFailure = new TaskDependencyException(
            ErrorCode.TASK_DEPENDENCY_CYCLE,
            "cycle",
            Map.of("dependentTaskId", "task-a", "blockerTaskId", "task-b")
        );
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );

        TaskDependencyException exception = assertThrows(
            TaskDependencyException.class,
            () -> presenter.linkDependency("task-a", "task-b")
        );
        assertEquals(ErrorCode.TASK_DEPENDENCY_CYCLE, exception.errorCode());
    }

    @Test
    void analyzeTaskObservedPersistsInsightOnSuccess() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        FakeTaskAnalysisService analysisService = new FakeTaskAnalysisService();
        analysisService.analyzeFuture = CompletableFuture.completedFuture("structured insight");

        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, analysisService, new FakeTaskExportService())
        );

        Task task = new Task("task-obs-ok", "Observed", "", LocalDate.now().plusDays(1), 3);
        String result = presenter.analyzeTaskObserved(task, null, false).join();

        assertEquals("structured insight", result);
        assertEquals("structured insight", task.getAiInsight());
        assertEquals(1, appService.savedTasks.size());
        assertSame(task, appService.savedTasks.get(0));
    }

    @Test
    void analyzeTaskObservedKeepsTaskUnchangedOnFailure() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        FakeTaskAnalysisService analysisService = new FakeTaskAnalysisService();
        analysisService.analyzeFuture = CompletableFuture.failedFuture(new IllegalStateException("ai-down"));

        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, analysisService, new FakeTaskExportService())
        );

        Task task = new Task("task-obs-fail", "Observed", "", LocalDate.now().plusDays(1), 3);
        CompletionException error = assertThrows(
            CompletionException.class,
            () -> presenter.analyzeTaskObserved(task, null, false).join()
        );

        assertEquals("ai-down", error.getCause().getMessage());
        assertTrue(task.getAiInsight() == null || task.getAiInsight().isBlank());
        assertEquals(0, appService.savedTasks.size());
    }

    @Test
    void undoRedoAddTaskRestoresStateConsistency() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );
        presenter.initialize();

        Task task = new Task("task-undo-add", "Undo Add", "", LocalDate.now().plusDays(2), 3);

        assertTrue(presenter.addTaskUndoable(task).successful());
        assertEquals(1, presenter.getState().tasks().size());
        assertTrue(presenter.getState().undoAvailable());

        assertTrue(presenter.undoLastAction().successful());
        assertTrue(presenter.getState().tasks().isEmpty());
        assertTrue(presenter.getState().redoAvailable());

        assertTrue(presenter.redoLastAction().successful());
        assertEquals(1, presenter.getState().tasks().size());
    }

    @Test
    void undoIsUnavailableWhenHistoryIsEmpty() {
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(new FakeTaskApplicationService(), new FakeTaskAnalysisService(), new FakeTaskExportService())
        );
        presenter.initialize();

        assertFalse(presenter.getState().undoAvailable());
        assertFalse(presenter.undoLastAction().successful());
        assertTrue(presenter.getState().statusMessage().toLowerCase().contains("history"));
    }

    @Test
    void undoRedoDependencyLinkRestoresCriticalPathInputs() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        appService.loadedTasks.add(new Task("task-a", "Task A", "", LocalDate.now().plusDays(3), 3));
        appService.loadedTasks.add(new Task("task-b", "Task B", "", LocalDate.now().plusDays(2), 2));
        MainViewPresenter presenter = new MainViewPresenter(
            new FakeMainView(),
            new MainViewServices(appService, new FakeTaskAnalysisService(), new FakeTaskExportService())
        );
        presenter.initialize();
        int initialCriticalPathCalls = appService.computeFullCriticalPathCalls;

        assertTrue(presenter.linkDependencyUndoable("task-a", "task-b").successful());
        assertEquals(List.of("task-b"), presenter.loadDependencies("task-a"));

        assertTrue(presenter.undoLastAction().successful());
        assertTrue(presenter.loadDependencies("task-a").isEmpty());

        assertTrue(presenter.redoLastAction().successful());
        assertEquals(List.of("task-b"), presenter.loadDependencies("task-a"));
        assertTrue(appService.computeFullCriticalPathCalls > initialCriticalPathCalls);
    }

    @Test
    void globalSearchDelegatesAndOpensTaskAndNoteResults() {
        FakeTaskApplicationService appService = new FakeTaskApplicationService();
        FakeGlobalSearchService globalSearchService = new FakeGlobalSearchService();
        globalSearchService.results = List.of(
            GlobalSearchResult.task("task-7", "Deploy API", "snippet", 120.0),
            GlobalSearchResult.note("Roadmap", "note snippet", 98.0)
        );

        FakeMainView view = new FakeMainView();
        MainViewPresenter presenter = new MainViewPresenter(
            view,
            new MainViewServices(
                appService,
                new FakeTaskAnalysisService(),
                new FakeTaskExportService(),
                globalSearchService
            )
        );

        List<GlobalSearchResult> results = presenter.searchGlobal("deploy", 10);
        assertEquals(2, results.size());
        assertEquals("deploy", globalSearchService.lastQuery);
        assertEquals(10, globalSearchService.lastLimit);

        assertTrue(presenter.openGlobalSearchResult(results.get(0)));
        assertEquals("task-7", view.lastOpenedTaskId);

        assertTrue(presenter.openGlobalSearchResult(results.get(1)));
        assertEquals("Roadmap", view.lastOpenedNoteTitle);
    }

    private static final class FakeMainView implements MainViewContract.View {
        private MainViewState lastState = MainViewState.initial();
        private String lastOpenedTaskId;
        private String lastOpenedNoteTitle;

        @Override
        public Node getRootNode() {
            return null;
        }

        @Override
        public void bindPresenter(MainViewContract.Presenter presenter) {
            // no-op
        }

        @Override
        public void render(MainViewState state) {
            this.lastState = state;
        }

        @Override
        public boolean canCloseApplication() {
            return true;
        }

        @Override
        public boolean openTaskById(String taskId) {
            this.lastOpenedTaskId = taskId;
            return taskId != null && !taskId.isBlank();
        }

        @Override
        public boolean openNoteByTitle(String noteTitle) {
            this.lastOpenedNoteTitle = noteTitle;
            return noteTitle != null && !noteTitle.isBlank();
        }
    }

    private static final class FakeTaskApplicationService implements TaskApplicationService {
        private final List<Task> loadedTasks = new ArrayList<>();
        private final List<Task> savedTasks = new ArrayList<>();
        private final List<String> deletedTaskIds = new ArrayList<>();
        private final List<TaskTemplate> savedTemplates = new ArrayList<>();
        private final Map<String, LinkedHashSet<String>> dependencyBlockersByTask = new HashMap<>();
        private int archiveBulkCalls;
        private int deleteBulkCalls;
        private int updateTagsBulkCalls;
        private int saveTaskCalls;
        private int deleteTaskCalls;
        private boolean lastArchiveIncludeSubtasks;
        private Map<String, String> lastTagsBulk = Map.of();
        private int computeFullCriticalPathCalls;
        private int computeScopedCriticalPathCalls;
        private TaskDependencyException linkFailure;

        @Override
        public List<Task> loadTasks() {
            return new ArrayList<>(loadedTasks);
        }

        @Override
        public void saveTask(Task task) {
            saveTaskCalls++;
            savedTasks.add(task);
            upsertLoadedTask(task);
        }

        @Override
        public void saveTasks(List<Task> tasks) {
            if (tasks == null) {
                return;
            }
            savedTasks.addAll(tasks);
            for (Task task : tasks) {
                upsertLoadedTask(task);
            }
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
            if (tasks != null) {
                savedTasks.addAll(tasks);
                for (Task task : tasks) {
                    upsertLoadedTask(task);
                }
            }
            int processed = tasks == null ? 0 : tasks.size();
            return new TaskBulkOperationResult("saveTasksBatch", processed, processed, 0, processed, 0);
        }

        @Override
        public void deleteTask(String taskId) {
            deleteTaskCalls++;
            deletedTaskIds.add(taskId);
            removeLoadedTask(taskId);
        }

        @Override
        public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
            int processed = taskIds == null ? 0 : taskIds.size();
            archiveBulkCalls++;
            lastArchiveIncludeSubtasks = includeSubtasks;
            if (taskIds != null) {
                for (String taskId : taskIds) {
                    Task task = findLoadedTask(taskId);
                    if (task != null) {
                        task.setArchived(true);
                    }
                }
            }
            return new TaskBulkOperationResult(
                includeSubtasks ? "archiveTasksBatchWithSubtasks" : "archiveTasksBatch",
                processed,
                processed,
                0,
                processed,
                0
            );
        }

        @Override
        public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
            int processed = taskIds == null ? 0 : taskIds.size();
            deleteBulkCalls++;
            if (taskIds != null) {
                for (String taskId : taskIds) {
                    deletedTaskIds.add(taskId);
                    removeLoadedTask(taskId);
                }
            }
            return new TaskBulkOperationResult("deleteTasksBatch", processed, processed, 0, processed, 0);
        }

        @Override
        public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
            updateTagsBulkCalls++;
            lastTagsBulk = tagsByTaskId == null ? Map.of() : new HashMap<>(tagsByTaskId);
            if (tagsByTaskId != null) {
                for (Map.Entry<String, String> entry : tagsByTaskId.entrySet()) {
                    Task task = findLoadedTask(entry.getKey());
                    if (task != null) {
                        task.setTags(entry.getValue());
                    }
                }
            }
            int processed = tagsByTaskId == null ? 0 : tagsByTaskId.size();
            return new TaskBulkOperationResult("updateTaskTagsBatch", processed, processed, 0, processed, 0);
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
            if (linkFailure != null) {
                throw linkFailure;
            }
            dependencyBlockersByTask
                .computeIfAbsent(dependentTaskId, key -> new LinkedHashSet<>())
                .add(blockerTaskId);
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (blockerTaskIds != null) {
                normalized.addAll(blockerTaskIds);
            }
            dependencyBlockersByTask.put(taskId, normalized);
        }

        @Override
        public List<String> loadDependencies(String taskId) {
            return List.copyOf(dependencyBlockersByTask.getOrDefault(taskId, new LinkedHashSet<>()));
        }

        @Override
        public List<TaskDependencyEdge> loadAllDependencyEdges() {
            List<TaskDependencyEdge> edges = new ArrayList<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : dependencyBlockersByTask.entrySet()) {
                for (String blockerTaskId : entry.getValue()) {
                    edges.add(new TaskDependencyEdge(entry.getKey(), blockerTaskId));
                }
            }
            return edges;
        }

        @Override
        public void deleteDependenciesForTask(String taskId) {
            // no-op for presenter tests
        }

        @Override
        public CriticalPathResult computeCriticalPathFullGraph() {
            computeFullCriticalPathCalls++;
            return CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null);
        }

        @Override
        public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
            computeScopedCriticalPathCalls++;
            return CriticalPathResult.empty(CriticalPathScopeMode.ROOT_TASK, rootTaskId);
        }

        @Override
        public List<TaskTemplate> loadAllTemplates() {
            return List.of();
        }

        @Override
        public void saveTemplate(TaskTemplate template) {
            savedTemplates.add(template);
        }

        private void upsertLoadedTask(Task task) {
            if (task == null || task.getId() == null) {
                return;
            }
            for (int i = 0; i < loadedTasks.size(); i++) {
                Task existing = loadedTasks.get(i);
                if (task.getId().equals(existing.getId())) {
                    loadedTasks.set(i, task);
                    return;
                }
            }
            loadedTasks.add(task);
        }

        private void removeLoadedTask(String taskId) {
            if (taskId == null) {
                return;
            }
            loadedTasks.removeIf(task -> task != null && taskId.equals(task.getId()));
            dependencyBlockersByTask.remove(taskId);
            for (LinkedHashSet<String> blockers : dependencyBlockersByTask.values()) {
                blockers.remove(taskId);
            }
        }

        private Task findLoadedTask(String taskId) {
            if (taskId == null) {
                return null;
            }
            for (Task task : loadedTasks) {
                if (task != null && taskId.equals(task.getId())) {
                    return task;
                }
            }
            return null;
        }
    }

    private static final class FakeTaskAnalysisService implements TaskAnalysisService {
        private int calculatePriorityCalls;
        private int analyzeCalls;
        private int autoPrioritizeCalls;
        private int predictCalls;
        private int recommendationCalls;
        private int productivityCalls;
        private CompletableFuture<String> analyzeFuture = CompletableFuture.completedFuture("analysis");

        @Override
        public void calculatePriority(Task task) {
            calculatePriorityCalls++;
        }

        @Override
        public CompletableFuture<String> analyzeTask(Task task) {
            analyzeCalls++;
            return analyzeFuture;
        }

        @Override
        public CompletableFuture<String> prioritizeWithAi(List<Task> tasks) {
            autoPrioritizeCalls++;
            return CompletableFuture.completedFuture("prioritized");
        }

        @Override
        public String autoSchedule(List<Task> tasks, int dailyComplexityBudget) {
            return "scheduled";
        }

        @Override
        public CompletableFuture<String> predictTime(Task task) {
            predictCalls++;
            return CompletableFuture.completedFuture("prediction");
        }

        @Override
        public CompletableFuture<String> recommendations(List<Task> tasks) {
            recommendationCalls++;
            return CompletableFuture.completedFuture("recommendations");
        }

        @Override
        public CompletableFuture<String> productivityAnalysis(List<Task> tasks) {
            productivityCalls++;
            return CompletableFuture.completedFuture("productivity");
        }
    }

    private static final class FakeTaskExportService implements TaskExportService {
        private int exportCalls;

        @Override
        public void exportInsight(File file, String extension, String content) {
            exportCalls++;
            assertFalse(file.getPath().isBlank());
            assertFalse(extension.isBlank());
            assertEquals("content", content);
        }
    }

    private static final class FakeGlobalSearchService implements GlobalSearchService {
        private List<GlobalSearchResult> results = List.of();
        private String lastQuery = "";
        private int lastLimit;

        @Override
        public List<GlobalSearchResult> search(String query, int limit) {
            lastQuery = query == null ? "" : query;
            lastLimit = limit;
            return results;
        }
    }
}
