package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.service.task.DefaultTaskAnalysisService;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.DefaultTaskExportService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.scene.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainViewPresenterIntegrationTest extends IsolatedTestDataFixture {
    private static final String TASK_PREFIX = "ux-int-task-";
    private static final String NOTE_PREFIX = "ux-int-note-";

    private final TaskApplicationService taskApplicationService = new DefaultTaskApplicationService();
    private final SmartNotesApplicationService notesApplicationService = new DefaultSmartNotesApplicationService();

    @AfterEach
    void cleanup() {
        deletePrefixedTasks();
        deletePrefixedNotes();
    }

    @Test
    void taskUndoRedoPersistsChangesInDatabase() {
        FakeMainView view = new FakeMainView();
        MainViewPresenter presenter = new MainViewPresenter(
            view,
            new MainViewServices(
                taskApplicationService,
                new DefaultTaskAnalysisService(),
                new DefaultTaskExportService()
            )
        );
        presenter.initialize();

        String taskId = TASK_PREFIX + UUID.randomUUID();
        Task task = new Task(taskId, "UX integration task", "integration", LocalDate.now().plusDays(2), 3);

        assertTrue(presenter.addTaskUndoable(task).successful());
        assertNotNull(findTaskById(taskId, taskApplicationService.loadTasks()));

        assertTrue(presenter.undoLastAction().successful());
        assertNull(findTaskById(taskId, taskApplicationService.loadTasks()));

        assertTrue(presenter.redoLastAction().successful());
        assertNotNull(findTaskById(taskId, taskApplicationService.loadTasks()));
    }

    @Test
    void globalSearchResultNavigationWorksAcrossTaskAndNote() {
        String token = "ux-nav-" + UUID.randomUUID();
        String taskId = TASK_PREFIX + UUID.randomUUID();
        String noteTitle = NOTE_PREFIX + token;

        Task task = new Task(taskId, "Task " + token, "cross navigation", LocalDate.now().plusDays(4), 2);
        taskApplicationService.saveTask(task);
        notesApplicationService.createNoteWithTitle(noteTitle);
        notesApplicationService.saveCurrent(noteTitle, noteTitle, "Note body " + token);

        FakeMainView view = new FakeMainView();
        MainViewPresenter presenter = new MainViewPresenter(
            view,
            new MainViewServices(
                taskApplicationService,
                new DefaultTaskAnalysisService(),
                new DefaultTaskExportService()
            )
        );
        presenter.initialize();

        List<GlobalSearchResult> results = presenter.getServices().globalSearchService().search(token, 20);
        GlobalSearchResult taskResult = results.stream()
            .filter(result -> result.type() == GlobalSearchResultType.TASK && taskId.equals(result.id()))
            .findFirst()
            .orElse(null);
        GlobalSearchResult noteResult = results.stream()
            .filter(result -> result.type() == GlobalSearchResultType.NOTE && noteTitle.equals(result.id()))
            .findFirst()
            .orElse(null);

        assertNotNull(taskResult);
        assertNotNull(noteResult);

        assertTrue(presenter.openGlobalSearchResult(taskResult));
        assertEquals(taskId, view.lastOpenedTaskId);

        assertTrue(presenter.openGlobalSearchResult(noteResult));
        assertEquals(noteTitle, view.lastOpenedNoteTitle);
    }

    private void deletePrefixedTasks() {
        List<String> toDelete = new ArrayList<>();
        for (Task task : flattenTasks(taskApplicationService.loadTasks())) {
            if (task.getId() != null && task.getId().startsWith(TASK_PREFIX)) {
                toDelete.add(task.getId());
            }
        }
        if (!toDelete.isEmpty()) {
            taskApplicationService.deleteTasksBulk(toDelete);
        }
    }

    private void deletePrefixedNotes() {
        for (String title : notesApplicationService.listTitles()) {
            if (title != null && title.startsWith(NOTE_PREFIX)) {
                notesApplicationService.deleteNote(title);
            }
        }
    }

    private Task findTaskById(String taskId, List<Task> rootTasks) {
        if (taskId == null || rootTasks == null) {
            return null;
        }
        for (Task task : rootTasks) {
            Task found = findTaskByIdRecursive(taskId, task);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Task findTaskByIdRecursive(String taskId, Task task) {
        if (task == null) {
            return null;
        }
        if (taskId.equals(task.getId())) {
            return task;
        }
        for (Task subtask : task.getSubtasks()) {
            Task found = findTaskByIdRecursive(taskId, subtask);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<Task> flattenTasks(List<Task> rootTasks) {
        List<Task> flattened = new ArrayList<>();
        if (rootTasks == null) {
            return flattened;
        }
        for (Task task : rootTasks) {
            collectRecursively(task, flattened);
        }
        return flattened;
    }

    private void collectRecursively(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            collectRecursively(subtask, sink);
        }
    }

    private static final class FakeMainView implements MainViewContract.View {
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
            // no-op
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
}
