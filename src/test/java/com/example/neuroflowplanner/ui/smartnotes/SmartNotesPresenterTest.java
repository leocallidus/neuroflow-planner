package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.service.notes.SmartNotesAiService;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
import com.example.neuroflowplanner.service.search.GlobalSearchService;
import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartNotesPresenterTest {

    @Test
    void initializeLoadsNotesAndRendersState() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "first");
        appService.put("Beta", "second");

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        presenter.initialize();

        assertTrue(view.lastState.initialized());
        assertEquals(2, view.lastState.noteTitles().size());
        assertEquals("Alpha", view.lastState.selectedNoteTitle());
        assertEquals("first", view.lastState.selectedNoteContent());
    }

    @Test
    void saveDeleteAndCreateFlowsAreHandledByPresenter() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "old");

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        presenter.initialize();
        presenter.createNewNote();

        assertEquals(2, appService.titles.size());
        String createdTitle = view.lastState.selectedNoteTitle();
        assertTrue(createdTitle.toLowerCase(Locale.ROOT).contains("заметка") || createdTitle.equals("Untitled"));

        presenter.saveCurrentNote(createdTitle, "Renamed", "content");

        assertEquals("Renamed", view.lastState.selectedNoteTitle());
        assertEquals("content", view.lastState.selectedNoteContent());
        assertEquals("Renamed", appService.lastSavedTitle);

        presenter.deleteNote("Renamed");

        assertFalse(appService.titles.contains("Renamed"));
        assertEquals("Alpha", view.lastState.selectedNoteTitle());
    }

    @Test
    void linksAndTaskNavigationAreDelegated() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "body");
        appService.outgoing = List.of(
            new SmartNotesApplicationService.LinkChip("Note A", "Note A", SmartNotesApplicationService.LinkType.NOTE, true),
            new SmartNotesApplicationService.LinkChip("Task X", "task-x", SmartNotesApplicationService.LinkType.TASK, true)
        );
        appService.incoming = List.of(
            new SmartNotesApplicationService.LinkChip("Ref", "Ref", SmartNotesApplicationService.LinkType.NOTE, true)
        );

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        AtomicReference<String> navigatedTarget = new AtomicReference<>();
        presenter.setTaskNavigator(navigatedTarget::set);

        presenter.initialize();
        List<SmartNotesPresenter.LinkChipViewModel> outgoing = presenter.buildOutgoingLinkChips("ignored");
        List<SmartNotesPresenter.LinkChipViewModel> incoming = presenter.buildIncomingLinkChips("Alpha");

        assertEquals(2, outgoing.size());
        assertEquals(SmartNotesApplicationService.LinkType.NOTE, outgoing.get(0).type());
        assertEquals(SmartNotesApplicationService.LinkType.TASK, outgoing.get(1).type());
        assertEquals(1, incoming.size());

        presenter.handleTaskLinkClick("task-x", null, false);
        assertEquals("task-x", navigatedTarget.get());
    }

    @Test
    void openNoteByTitleUsesApplicationResolution() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Roadmap", "v1");

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        presenter.initialize();

        assertTrue(presenter.openNoteByTitle("roadmap"));
        assertEquals("Roadmap", view.lastState.selectedNoteTitle());

        assertFalse(presenter.openNoteByTitle("missing"));
        assertNotNull(view.lastState);
    }

    @Test
    void searchQueryAndTemplateCreationAreHandledByPresenter() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Roadmap", "release plan");
        appService.put("Journal", "daily sync");

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        presenter.initialize();
        presenter.onSearchQueryChanged("journal");

        assertEquals("journal", view.lastState.searchQuery());
        assertEquals(1, view.lastState.noteTitles().size());
        assertEquals("Journal", view.lastState.noteTitles().get(0));

        presenter.createNoteFromTemplate("retro");
        assertEquals("Создано из шаблона", view.lastState.statusMessage());
    }

    @Test
    void saveCurrentNoteNoopsWhenPayloadIsEmpty() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "body");

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );

        presenter.initialize();
        presenter.saveCurrentNote("", "", "");

        assertEquals(0, appService.saveCalls);
        assertEquals("Alpha", view.lastState.selectedNoteTitle());
    }

    @Test
    void requestAiFailureDoesNotCorruptSavedContent() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "body");
        FakeAiService aiService = new FakeAiService();
        aiService.responseFuture = CompletableFuture.failedFuture(new IllegalStateException("ai-down"));

        FakeSmartNotesView view = new FakeSmartNotesView();
        SmartNotesPresenter presenter = new SmartNotesPresenter(
            view,
            new SmartNotesServices(appService, aiService, new FakeExportService())
        );

        presenter.initialize();
        int savedBefore = appService.saveCalls;

        presenter.requestAiForCurrentNote("summarize", null, false);

        assertEquals(savedBefore, appService.saveCalls);
        assertEquals("Запрос к ИИ отправлен...", view.lastState.statusMessage());
        assertEquals("body", appService.loadContent("Alpha"));
    }

    @Test
    void previewAndSanitizeAreDelegatedToExportService() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "body");
        FakeExportService exportService = new FakeExportService();
        exportService.previewHtml = "<html>preview</html>";
        exportService.sanitizedName = "safe-name";

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(appService, new FakeAiService(), exportService)
        );

        String html = presenter.renderPreviewHtml("# title", "title", true);
        String safeFileName = presenter.sanitizeExportFileName("raw-name", "fallback");

        assertEquals("<html>preview</html>", html);
        assertEquals("safe-name", safeFileName);
        assertEquals(1, exportService.renderCalls);
        assertEquals(1, exportService.sanitizeCalls);
    }

    @Test
    void undoRedoRestoresCreateDeleteAndRenameOperations() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "old");

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );
        presenter.initialize();

        presenter.createNewNote();
        String created = presenter.getState().selectedNoteTitle();
        assertTrue(appService.titles.contains(created));

        assertTrue(presenter.undoLastAction().successful());
        assertFalse(appService.titles.contains(created));
        assertEquals("Alpha", presenter.getState().selectedNoteTitle());

        assertTrue(presenter.redoLastAction().successful());
        assertTrue(appService.titles.contains(created));

        presenter.saveCurrentNote(created, "Renamed", "renamed-content");
        assertTrue(appService.titles.contains("Renamed"));

        assertTrue(presenter.undoLastAction().successful());
        assertTrue(appService.titles.contains(created));
        assertFalse(appService.titles.contains("Renamed"));
        assertEquals("", appService.loadContent(created));

        assertTrue(presenter.redoLastAction().successful());
        assertTrue(appService.titles.contains("Renamed"));
        assertEquals("renamed-content", appService.loadContent("Renamed"));
    }

    @Test
    void autosaveDoesNotPolluteUndoHistory() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "v1");

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );
        presenter.initialize();

        presenter.autoSaveCurrentNote("Alpha", "Alpha", "v2");

        assertEquals("v2", appService.loadContent("Alpha"));
        assertFalse(presenter.getState().undoAvailable());
        assertFalse(presenter.undoLastAction().successful());
    }

    @Test
    void redoIsUnavailableWhenHistoryIsEmpty() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Alpha", "body");

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(appService, new FakeAiService(), new FakeExportService())
        );
        presenter.initialize();

        assertFalse(presenter.getState().redoAvailable());
        assertFalse(presenter.redoLastAction().successful());
        assertTrue(presenter.getState().statusMessage().toLowerCase(Locale.ROOT).contains("history"));
    }

    @Test
    void globalSearchFindsAndOpensNoteAndTaskResults() {
        FakeApplicationService appService = new FakeApplicationService();
        appService.put("Roadmap", "Quarterly goals");
        FakeGlobalSearchService globalSearchService = new FakeGlobalSearchService();
        globalSearchService.results = List.of(
            GlobalSearchResult.note("Roadmap", "Quarterly goals", 90.0),
            GlobalSearchResult.task("task-99", "Deploy API", "Task snippet", 88.0)
        );

        SmartNotesPresenter presenter = new SmartNotesPresenter(
            new FakeSmartNotesView(),
            new SmartNotesServices(
                appService,
                new FakeAiService(),
                new FakeExportService(),
                globalSearchService
            )
        );
        AtomicReference<String> taskNavigationTarget = new AtomicReference<>();
        presenter.setTaskNavigator(taskNavigationTarget::set);
        presenter.initialize();

        List<GlobalSearchResult> results = presenter.searchGlobal("roadmap", 7);
        assertEquals(2, results.size());
        assertEquals("roadmap", globalSearchService.lastQuery);
        assertEquals(7, globalSearchService.lastLimit);

        assertTrue(presenter.openGlobalSearchResult(results.get(0)));
        assertEquals("Roadmap", presenter.getState().selectedNoteTitle());

        assertTrue(presenter.openGlobalSearchResult(results.get(1)));
        assertEquals("task-99", taskNavigationTarget.get());
    }

    private static final class FakeSmartNotesView implements SmartNotesContract.View {
        private SmartNotesState lastState = SmartNotesState.initial();

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
            this.lastState = state;
        }

        @Override
        public void refreshTheme() {
            // no-op
        }

        @Override
        public void setTaskResolver(java.util.function.Function<String, Task> resolver) {
            // no-op
        }

        @Override
        public void setTaskNavigator(java.util.function.Consumer<String> navigator) {
            // no-op
        }

        @Override
        public void setTaskProvider(java.util.function.Supplier<List<Task>> provider) {
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
            return "";
        }
    }

    private static final class FakeApplicationService implements SmartNotesApplicationService {
        private final List<String> titles = new ArrayList<>();
        private final Map<String, String> contents = new HashMap<>();
        private List<LinkChip> outgoing = List.of();
        private List<LinkChip> incoming = List.of();
        private String lastSavedTitle;
        private int saveCalls;

        void put(String title, String content) {
            if (!titles.contains(title)) {
                titles.add(title);
            }
            contents.put(title, content);
            titles.sort(String::compareTo);
        }

        @Override
        public List<String> listTitles() {
            return new ArrayList<>(titles);
        }

        @Override
        public List<String> searchTitles(String query) {
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return listTitles();
            }
            List<String> filtered = new ArrayList<>();
            for (String title : titles) {
                String content = contents.getOrDefault(title, "");
                if (title.toLowerCase(Locale.ROOT).contains(normalized) || content.toLowerCase(Locale.ROOT).contains(normalized)) {
                    filtered.add(title);
                }
            }
            return filtered;
        }

        @Override
        public String loadContent(String title) {
            return contents.getOrDefault(title, "");
        }

        @Override
        public SaveResult saveCurrent(String currentTitle, String editedTitle, String content) {
            saveCalls++;
            String resolvedTitle = editedTitle == null || editedTitle.isBlank() ? currentTitle : editedTitle.trim();
            if (resolvedTitle == null || resolvedTitle.isBlank()) {
                resolvedTitle = "Untitled";
            }
            if (currentTitle != null && !currentTitle.isBlank() && !currentTitle.equals(resolvedTitle)) {
                titles.remove(currentTitle);
                contents.remove(currentTitle);
            }
            if (!titles.contains(resolvedTitle)) {
                titles.add(resolvedTitle);
            }
            titles.sort(String::compareTo);
            contents.put(resolvedTitle, content == null ? "" : content);
            lastSavedTitle = resolvedTitle;
            return new SaveResult(resolvedTitle, contents.get(resolvedTitle), !resolvedTitle.equals(currentTitle));
        }

        @Override
        public String createNewNote() {
            String base = "Новая заметка";
            String title = base;
            int index = 1;
            while (titles.contains(title)) {
                title = base + " " + index++;
            }
            put(title, "");
            return title;
        }

        @Override
        public String createNoteWithTitle(String title) {
            String resolved = (title == null || title.isBlank()) ? "Untitled" : title.trim();
            String candidate = resolved;
            int index = 1;
            while (titles.contains(candidate)) {
                candidate = resolved + " " + index++;
            }
            put(candidate, "");
            return candidate;
        }

        @Override
        public String createFromTemplate(String templateKey) {
            String title = createNoteWithTitle(templateKey + " note");
            contents.put(title, "template");
            return title;
        }

        @Override
        public void deleteNote(String title) {
            titles.remove(title);
            contents.remove(title);
        }

        @Override
        public String resolveExistingTitle(String title) {
            if (title == null) {
                return null;
            }
            for (String existing : titles) {
                if (existing.equalsIgnoreCase(title.trim())) {
                    return existing;
                }
            }
            return null;
        }

        @Override
        public List<LinkChip> outgoingLinks(String content, java.util.function.Function<String, Task> taskResolver) {
            return outgoing;
        }

        @Override
        public List<LinkChip> incomingLinks(String noteTitle, java.util.function.Supplier<List<Task>> taskProvider) {
            return incoming;
        }

        @Override
        public NotesSnapshot captureSnapshot() {
            List<NoteSnapshot> notes = new ArrayList<>();
            for (String title : titles) {
                notes.add(new NoteSnapshot(title, contents.getOrDefault(title, "")));
            }
            return new NotesSnapshot(notes);
        }

        @Override
        public void restoreSnapshot(NotesSnapshot snapshot) {
            titles.clear();
            contents.clear();
            if (snapshot == null) {
                return;
            }
            for (NoteSnapshot note : snapshot.notes()) {
                if (note == null || note.title() == null || note.title().isBlank()) {
                    continue;
                }
                put(note.title(), note.content());
            }
        }
    }

    private static final class FakeAiService implements SmartNotesAiService {
        private CompletableFuture<String> responseFuture = CompletableFuture.completedFuture("ok");

        @Override
        public CompletableFuture<String> requestCompletion(String userPrompt, String context) {
            return responseFuture;
        }
    }

    private static final class FakeExportService implements SmartNotesExportService {
        private String previewHtml = "<html></html>";
        private String sanitizedName;
        private int renderCalls;
        private int sanitizeCalls;

        @Override
        public void exportNoteToPdf(File file, String noteTitle, String noteContent) {
            // no-op
        }

        @Override
        public void exportAllNotesToPdf(File file, List<NoteExport> notes) {
            // no-op
        }

        @Override
        public void exportNoteToMarkdown(File file, String noteTitle, String noteContent) {
            // no-op
        }

        @Override
        public String renderPreviewHtml(String markdown, String searchQuery, boolean darkTheme) {
            renderCalls++;
            return previewHtml;
        }

        @Override
        public String sanitizeFileName(String name, String fallback) {
            sanitizeCalls++;
            if (sanitizedName != null) {
                return sanitizedName;
            }
            return name == null || name.isBlank() ? fallback : name;
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
