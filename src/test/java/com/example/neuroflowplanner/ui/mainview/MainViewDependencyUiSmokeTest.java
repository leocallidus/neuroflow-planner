package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.service.task.TaskAnalysisService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskCriticalPathService;
import com.example.neuroflowplanner.service.task.TaskExportService;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MainView dependency and critical-path smoke")
class MainViewDependencyUiSmokeTest {
    private static boolean fxRuntimeReady;

    @BeforeAll
    static void initFxRuntime() {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(5, TimeUnit.SECONDS);
            fxRuntimeReady = true;
        } catch (IllegalStateException alreadyStarted) {
            fxRuntimeReady = true;
        } catch (Throwable throwable) {
            fxRuntimeReady = false;
        }
    }

    @Test
    @DisplayName("Link/unlink dependency updates details and critical-path metrics in UI")
    void linkUnlinkDependencyRefreshesCriticalPathPanel() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");

        Task dependent = new Task("task-a", "Dependent", "Task A", LocalDate.now().plusDays(3), 3);
        Task blocker = new Task("task-b", "Blocker", "Task B", LocalDate.now().plusDays(2), 4);
        InMemoryTaskApplicationService appService = new InMemoryTaskApplicationService(List.of(dependent, blocker));
        MainViewServices services = new MainViewServices(appService, new NoopTaskAnalysisService(), new NoopTaskExportService());

        MainViewFactory.Assembly assembly = runOnFxThread(() -> MainViewFactory.create(services));
        MainViewPresenter presenter = (MainViewPresenter) assembly.presenter();
        MainViewView viewAdapter = (MainViewView) assembly.view();
        LegacyMainView legacyView = viewAdapter.getLegacyView();

        runOnFxThread(() -> {
            Scene scene = new Scene(legacyView, 1300, 860);
            assertNotNull(scene);
            presenter.initialize();
            legacyView.applyCss();
            legacyView.layout();
            return null;
        });

        TreeTableView<Task> taskTable = getPrivateField(legacyView, "taskTable", TreeTableView.class);
        Label detailDependsOn = getPrivateField(legacyView, "detailDependsOn", Label.class);
        Label detailDependents = getPrivateField(legacyView, "detailDependents", Label.class);
        Label criticalSummary = getPrivateField(legacyView, "criticalPathSummaryLabel", Label.class);
        Label criticalSelected = getPrivateField(legacyView, "criticalPathSelectedTaskLabel", Label.class);

        runOnFxThread(() -> {
            selectTask(taskTable, "task-a");
            assertEquals("-", detailDependsOn.getText());
            assertTrue(criticalSummary.getText().contains("Длина: 4"));
            assertTrue(criticalSelected.getText().contains("некритическая"));
            return null;
        });

        runOnFxThread(() -> {
            presenter.linkDependency("task-a", "task-b");
            presenter.initialize();
            legacyView.applyCss();
            legacyView.layout();

            selectTask(taskTable, "task-a");
            assertTrue(detailDependsOn.getText().contains("Blocker"));
            assertTrue(criticalSummary.getText().contains("Длина: 7"));
            assertTrue(criticalSelected.getText().contains("критическая"));

            selectTask(taskTable, "task-b");
            assertTrue(detailDependents.getText().contains("Dependent"));
            return null;
        });

        runOnFxThread(() -> {
            presenter.unlinkDependency("task-a", "task-b");
            presenter.initialize();
            legacyView.applyCss();
            legacyView.layout();

            selectTask(taskTable, "task-a");
            assertEquals("-", detailDependsOn.getText());
            assertTrue(criticalSummary.getText().contains("Длина: 4"));
            assertTrue(criticalSelected.getText().contains("некритическая"));

            selectTask(taskTable, "task-b");
            assertEquals("-", detailDependents.getText());
            return null;
        });
    }

    private static void selectTask(TreeTableView<Task> table, String taskId) {
        TreeItem<Task> item = findById(table.getRoot(), taskId);
        assertNotNull(item, "Task with id " + taskId + " not found in tree");
        table.getSelectionModel().clearSelection();
        table.getSelectionModel().select(item);
    }

    private static TreeItem<Task> findById(TreeItem<Task> root, String taskId) {
        if (root == null || taskId == null) {
            return null;
        }
        Task value = root.getValue();
        if (value != null && taskId.equals(value.getId())) {
            return root;
        }
        for (TreeItem<Task> child : root.getChildren()) {
            TreeItem<Task> found = findById(child, taskId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(target);
            return type.cast(value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot access field: " + fieldName, ex);
        }
    }

    private static <T> T runOnFxThread(ThrowingSupplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class InMemoryTaskApplicationService implements TaskApplicationService {
        private final List<Task> tasks = new ArrayList<>();
        private final Map<String, LinkedHashSet<String>> blockersByDependent = new HashMap<>();
        private final TaskCriticalPathService criticalPathService = new TaskCriticalPathService();

        private InMemoryTaskApplicationService(List<Task> seededTasks) {
            if (seededTasks != null) {
                tasks.addAll(seededTasks);
            }
        }

        @Override
        public List<Task> loadTasks() {
            return new ArrayList<>(tasks);
        }

        @Override
        public void saveTask(Task task) {
            if (task == null || task.getId() == null) {
                return;
            }
            for (int i = 0; i < tasks.size(); i++) {
                if (task.getId().equals(tasks.get(i).getId())) {
                    tasks.set(i, task);
                    return;
                }
            }
            tasks.add(task);
        }

        @Override
        public void saveTasks(List<Task> tasksToSave) {
            if (tasksToSave == null) {
                return;
            }
            for (Task task : tasksToSave) {
                saveTask(task);
            }
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasksToSave) {
            if (tasksToSave != null) {
                for (Task task : tasksToSave) {
                    saveTask(task);
                }
            }
            int processed = tasksToSave == null ? 0 : tasksToSave.size();
            return new TaskBulkOperationResult("saveTasksBatch", processed, processed, 0, processed, 0);
        }

        @Override
        public void deleteTask(String taskId) {
            if (taskId == null) {
                return;
            }
            tasks.removeIf(task -> taskId.equals(task.getId()));
            blockersByDependent.remove(taskId);
            for (LinkedHashSet<String> blockers : blockersByDependent.values()) {
                blockers.remove(taskId);
            }
        }

        @Override
        public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
            int updated = 0;
            if (taskIds != null) {
                for (String taskId : taskIds) {
                    Task task = findTask(taskId);
                    if (task != null && !task.isArchived()) {
                        task.setArchived(true);
                        updated++;
                    }
                    if (includeSubtasks) {
                        for (Task candidate : tasks) {
                            if (taskId != null && taskId.equals(candidate.getParentId()) && !candidate.isArchived()) {
                                candidate.setArchived(true);
                                updated++;
                            }
                        }
                    }
                }
            }
            int processed = taskIds == null ? 0 : taskIds.size();
            return new TaskBulkOperationResult(
                includeSubtasks ? "archiveTasksBatchWithSubtasks" : "archiveTasksBatch",
                processed,
                updated,
                0,
                processed,
                0
            );
        }

        @Override
        public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
            int processed = taskIds == null ? 0 : taskIds.size();
            if (taskIds != null) {
                for (String taskId : taskIds) {
                    deleteTask(taskId);
                }
            }
            return new TaskBulkOperationResult("deleteTasksBatch", processed, processed, 0, processed, 0);
        }

        @Override
        public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
            int updated = 0;
            if (tagsByTaskId != null) {
                for (Map.Entry<String, String> entry : tagsByTaskId.entrySet()) {
                    Task task = findTask(entry.getKey());
                    if (task != null) {
                        task.setTags(entry.getValue());
                        updated++;
                    }
                }
            }
            int processed = tagsByTaskId == null ? 0 : tagsByTaskId.size();
            return new TaskBulkOperationResult("updateTaskTagsBatch", processed, updated, 0, processed, 0);
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
            blockersByDependent.computeIfAbsent(dependentTaskId, key -> new LinkedHashSet<>()).add(blockerTaskId);
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
            LinkedHashSet<String> blockers = new LinkedHashSet<>();
            if (blockerTaskIds != null) {
                blockers.addAll(blockerTaskIds);
            }
            blockersByDependent.put(taskId, blockers);
        }

        @Override
        public List<String> loadDependencies(String taskId) {
            return List.copyOf(blockersByDependent.getOrDefault(taskId, new LinkedHashSet<>()));
        }

        @Override
        public List<TaskDependencyEdge> loadAllDependencyEdges() {
            List<TaskDependencyEdge> edges = new ArrayList<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : blockersByDependent.entrySet()) {
                for (String blockerId : entry.getValue()) {
                    edges.add(new TaskDependencyEdge(entry.getKey(), blockerId));
                }
            }
            return edges;
        }

        @Override
        public void deleteDependenciesForTask(String taskId) {
            blockersByDependent.remove(taskId);
        }

        @Override
        public CriticalPathResult computeCriticalPathFullGraph() {
            return criticalPathService.computeFullGraph(tasks, loadAllDependencyEdges());
        }

        @Override
        public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
            return criticalPathService.computeForRootTask(rootTaskId, tasks, loadAllDependencyEdges());
        }

        @Override
        public List<TaskTemplate> loadAllTemplates() {
            return List.of();
        }

        @Override
        public void saveTemplate(TaskTemplate template) {
            // no-op for smoke test
        }

        private Task findTask(String taskId) {
            if (taskId == null) {
                return null;
            }
            for (Task task : tasks) {
                if (taskId.equals(task.getId())) {
                    return task;
                }
            }
            return null;
        }
    }

    private static final class NoopTaskAnalysisService implements TaskAnalysisService {
        @Override
        public void calculatePriority(Task task) {
            // no-op for smoke test
        }

        @Override
        public CompletableFuture<String> analyzeTask(Task task) {
            return CompletableFuture.completedFuture("ok");
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

    private static final class NoopTaskExportService implements TaskExportService {
        @Override
        public void exportInsight(File file, String extension, String content) {
            // no-op for smoke test
        }
    }
}
