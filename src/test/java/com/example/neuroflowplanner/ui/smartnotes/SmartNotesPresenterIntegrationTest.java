package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesExportService;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.scene.Node;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartNotesPresenterIntegrationTest extends IsolatedTestDataFixture {
    private static final String NOTE_PREFIX = "ux-int-note-";

    private final SmartNotesApplicationService notesApplicationService = new DefaultSmartNotesApplicationService();

    @AfterEach
    void cleanup() {
        deletePrefixedNotes();
    }

    @Test
    void noteRenameUndoRedoPersistsAcrossApplicationService() {
        String originalTitle = NOTE_PREFIX + "original-" + UUID.randomUUID();
        String renamedTitle = NOTE_PREFIX + "renamed-" + UUID.randomUUID();
        notesApplicationService.createNoteWithTitle(originalTitle);
        notesApplicationService.saveCurrent(originalTitle, originalTitle, "initial body");

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(
                notesApplicationService,
                (prompt, context) -> CompletableFuture.completedFuture("ok"),
                new DefaultSmartNotesExportService()
            )
        );
        presenter.initialize();
        presenter.selectNote(originalTitle, presenter.getState().selectedNoteTitle(), originalTitle, "initial body");

        presenter.saveCurrentNote(originalTitle, renamedTitle, "renamed body");
        assertTrue(notesApplicationService.listTitles().contains(renamedTitle));
        assertFalse(notesApplicationService.listTitles().contains(originalTitle));
        assertEquals("renamed body", notesApplicationService.loadContent(renamedTitle));

        assertTrue(presenter.undoLastAction().successful());
        assertTrue(notesApplicationService.listTitles().contains(originalTitle));
        assertFalse(notesApplicationService.listTitles().contains(renamedTitle));
        assertEquals("initial body", notesApplicationService.loadContent(originalTitle));

        assertTrue(presenter.redoLastAction().successful());
        assertTrue(notesApplicationService.listTitles().contains(renamedTitle));
        assertFalse(notesApplicationService.listTitles().contains(originalTitle));
        assertEquals("renamed body", notesApplicationService.loadContent(renamedTitle));
    }

    private void deletePrefixedNotes() {
        for (String title : notesApplicationService.listTitles()) {
            if (title != null && title.startsWith(NOTE_PREFIX)) {
                notesApplicationService.deleteNote(title);
            }
        }
    }

    private static final class FakeSmartNotesView implements SmartNotesContract.View {
        @Override
        public Node getRootNode() {
            return null;
        }

        @Override
        public void bindPresenter(SmartNotesContract.Presenter presenter) {
            // no-op
        }

        @Override
        public void render(SmartNotesState state) {
            // no-op
        }

        @Override
        public void refreshTheme() {
            // no-op
        }

        @Override
        public void setTaskResolver(Function<String, Task> resolver) {
            // no-op
        }

        @Override
        public void setTaskNavigator(Consumer<String> navigator) {
            // no-op
        }

        @Override
        public void setTaskProvider(Supplier<List<Task>> provider) {
            // no-op
        }

        @Override
        public void openNoteByTitle(String title) {
            // no-op
        }

        @Override
        public Runnable getOnCloseAction() {
            return null;
        }

        @Override
        public void setCloseAction(Runnable closeAction) {
            // no-op
        }

        @Override
        public String getTitle() {
            return "Smart Notes Test";
        }
    }
}
