package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.SmartNotesExportService;
import com.example.neuroflowplanner.ui.AsyncErrorHandler;
import com.example.neuroflowplanner.ui.UiErrorNotifier;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.interaction.UserActionCommand;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import javafx.application.Platform;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SmartNotesPresenter implements SmartNotesContract.Presenter {
    private final SmartNotesContract.View view;
    private final SmartNotesServices services;
    private final UndoRedoManager undoRedoManager;
    private SmartNotesState state;
    private String historySelectionHint;
    private String historyQueryHint;

    private Function<String, Task> taskResolver;
    private Consumer<String> taskNavigator;
    private Supplier<List<Task>> taskProvider;

    public SmartNotesPresenter(SmartNotesContract.View view, SmartNotesServices services) {
        this.view = view;
        this.services = services;
        this.undoRedoManager = UndoRedoManager.fromConfig();
        this.state = withUndoRedoState(SmartNotesState.initial());
        this.historySelectionHint = "";
        this.historyQueryHint = "";
        this.taskResolver = null;
        this.taskNavigator = null;
        this.taskProvider = null;
    }

    @Override
    public void initialize() {
        rebuildState(state.selectedNoteTitle(), "", "Готово");
    }

    public void setTaskResolver(Function<String, Task> taskResolver) {
        this.taskResolver = taskResolver;
    }

    public void setTaskNavigator(Consumer<String> taskNavigator) {
        this.taskNavigator = taskNavigator;
    }

    public void setTaskProvider(Supplier<List<Task>> taskProvider) {
        this.taskProvider = taskProvider;
    }

    public void onSearchQueryChanged(String query) {
        rebuildState(state.selectedNoteTitle(), normalizeQuery(query), state.statusMessage());
    }

    public void createNewNote() {
        AtomicReference<String> createdTitleRef = new AtomicReference<>("");
        executeUndoableLifecycleCommand(
            "note.create",
            "Создать заметку",
            "notes",
            () -> createdTitleRef.set(services.applicationService().createNewNote()),
            createdTitleRef::get,
            () -> "",
            () -> "Создана новая заметка"
        );
    }

    public void createNoteFromTemplate(String templateKey) {
        AtomicReference<String> createdTitleRef = new AtomicReference<>("");
        executeUndoableLifecycleCommand(
            "note.create.template",
            "Создать из шаблона",
            "notes",
            () -> createdTitleRef.set(services.applicationService().createFromTemplate(templateKey)),
            createdTitleRef::get,
            () -> "",
            () -> "Создано из шаблона"
        );
    }

    public void deleteNote(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        String noteTitle = title.trim();
        String preferredSelection = Objects.equals(state.selectedNoteTitle(), noteTitle) ? "" : state.selectedNoteTitle();
        String activeQuery = state.searchQuery();
        executeUndoableLifecycleCommand(
            "note.delete",
            "Удалить заметку",
            "notes",
            () -> services.applicationService().deleteNote(noteTitle),
            () -> preferredSelection,
            () -> activeQuery,
            () -> "Удалено"
        );
    }

    public void selectNote(String title, String currentTitle, String editedTitle, String editedContent) {
        if (title == null || title.isBlank()) {
            return;
        }

        if (currentTitle != null && !currentTitle.isBlank() && !currentTitle.equals(title)) {
            saveCurrentInternal(currentTitle, editedTitle, editedContent, false);
        }

        rebuildState(title, state.searchQuery(), "Загружено");
    }

    public void saveCurrentNote(String currentTitle, String editedTitle, String editedContent) {
        if (!hasSavablePayload(currentTitle, editedTitle, editedContent, true)) {
            return;
        }

        String safeCurrentTitle = normalizeTitle(currentTitle);
        String safeEditedTitle = normalizeTitle(editedTitle);
        String safeEditedContent = editedContent == null ? "" : editedContent;
        String activeQuery = state.searchQuery();
        AtomicReference<SmartNotesApplicationService.SaveResult> saveResultRef = new AtomicReference<>();
        executeUndoableLifecycleCommand(
            "note.save",
            "Сохранить заметку",
            "notes",
            () -> {
                SmartNotesApplicationService.SaveResult saved = services.applicationService()
                    .saveCurrent(safeCurrentTitle, safeEditedTitle, safeEditedContent);
                saveResultRef.set(saved);
            },
            () -> {
                SmartNotesApplicationService.SaveResult saved = saveResultRef.get();
                return saved == null ? state.selectedNoteTitle() : saved.noteTitle();
            },
            () -> activeQuery,
            () -> "Сохранено " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        );
    }

    public void autoSaveCurrentNote(String currentTitle, String editedTitle, String editedContent) {
        SmartNotesApplicationService.SaveResult saveResult = saveCurrentInternal(
            currentTitle,
            editedTitle,
            editedContent,
            false
        );
        if (saveResult == null) {
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        rebuildState(saveResult.noteTitle(), state.searchQuery(), "Автосохранено " + timestamp);
    }

    public boolean openNoteByTitle(String title) {
        String resolved = resolveExistingNoteTitle(title);
        if (resolved == null) {
            return false;
        }
        rebuildState(resolved, "", "Загружено");
        return true;
    }

    public String createAndOpenNote(String title) {
        AtomicReference<String> createdTitleRef = new AtomicReference<>("");
        executeUndoableLifecycleCommand(
            "note.create.withTitle",
            "Создать заметку",
            "notes",
            () -> createdTitleRef.set(services.applicationService().createNoteWithTitle(title)),
            createdTitleRef::get,
            () -> "",
            () -> "Создана новая заметка"
        );
        return createdTitleRef.get();
    }

    public String resolveExistingNoteTitle(String title) {
        return services.applicationService().resolveExistingTitle(title);
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

        if (result.navigationTarget().type() == GlobalSearchResultType.NOTE) {
            return openNoteByTitle(result.navigationTarget().targetId());
        }

        if (taskNavigator == null) {
            return false;
        }
        taskNavigator.accept(result.navigationTarget().targetId());
        return true;
    }

    public List<LinkChipViewModel> buildOutgoingLinkChips(String content) {
        List<SmartNotesApplicationService.LinkChip> raw = services.applicationService().outgoingLinks(content, taskResolver);
        return mapLinks(raw);
    }

    public List<LinkChipViewModel> buildIncomingLinkChips(String noteTitle) {
        List<SmartNotesApplicationService.LinkChip> raw = services.applicationService().incomingLinks(noteTitle, taskProvider);
        return mapLinks(raw);
    }

    public void handleTaskLinkClick(String target, Window owner, boolean darkTheme) {
        if (taskNavigator == null) {
            UiErrorNotifier.showInfo(owner, darkTheme, "Ссылка на задачу", "Навигация к задачам недоступна.");
            return;
        }
        taskNavigator.accept(target);
    }

    public void requestAiForCurrentNote(String userPrompt, Window owner, boolean darkTheme) {
        String prompt = userPrompt == null ? "" : userPrompt.trim();
        if (prompt.isBlank()) {
            return;
        }
        if (state.selectedNoteTitle().isBlank()) {
            return;
        }

        String targetNoteTitle = state.selectedNoteTitle();
        String context = state.selectedNoteContent();
        String activeQuery = state.searchQuery();
        rebuildState(targetNoteTitle, activeQuery, "Запрос к ИИ отправлен...");

        String requestId = AsyncContext.ensureRequestId();
        CompletableFuture<String> future = services.aiService().requestCompletion(prompt, context);
        CompletableFuture<String> observed = AsyncErrorHandler.observeFuture(
            future,
            owner,
            darkTheme,
            "Ошибка ИИ",
            ErrorCode.AI_REQUEST_FAILED,
            "Не удалось получить ответ от ИИ.",
            true,
            "smartnotes.ai.request.failed",
            "operation", "requestAiForCurrentNote",
            "noteTitle", targetNoteTitle,
            "requestId", requestId
        );

        observed.thenAccept(AsyncContext.withMdcConsumer(response -> Platform.runLater(() -> {
            if (response == null || response.isBlank()) {
                rebuildState(state.selectedNoteTitle(), state.searchQuery(), "Ошибка ИИ");
                return;
            }

            String aiText = "\n\n--- \n**AI (" + prompt + "):**\n\n" + response;
            String latestContent = services.applicationService().loadContent(targetNoteTitle);
            services.applicationService().saveCurrent(targetNoteTitle, targetNoteTitle, latestContent + aiText);

            String status = targetNoteTitle.equals(state.selectedNoteTitle())
                ? "ИИ ответил"
                : "ИИ ответил в заметку: " + targetNoteTitle;
            String preferred = targetNoteTitle.equals(state.selectedNoteTitle()) ? targetNoteTitle : state.selectedNoteTitle();
            rebuildState(preferred, state.searchQuery(), status);
        })));
    }

    public void exportCurrentNoteToPdf(
        File file,
        String currentTitle,
        String editedTitle,
        String editedContent,
        Window owner,
        boolean darkTheme
    ) {
        if (file == null) {
            return;
        }

        SmartNotesApplicationService.SaveResult saved = saveCurrentInternal(currentTitle, editedTitle, editedContent, true);
        if (saved == null || saved.noteTitle().isBlank()) {
            UiErrorNotifier.showInfo(owner, darkTheme, "Экспорт заметки", "Сначала выберите заметку.");
            return;
        }

        try {
            services.exportService().exportNoteToPdf(file, saved.noteTitle(), saved.content());
            UiErrorNotifier.showInfo(owner, darkTheme, "Экспорт завершён", "PDF сохранён: " + file.getName());
            rebuildState(saved.noteTitle(), state.searchQuery(), "Экспорт завершён");
        } catch (Exception ex) {
            UiErrorNotifier.showMappedError(
                owner,
                darkTheme,
                "Ошибка экспорта PDF",
                ex,
                ErrorCode.EXPORT_PDF_FAILED,
                "Не удалось экспортировать заметку в PDF.",
                false,
                "operation", "exportCurrentNoteToPdf",
                "noteTitle", saved.noteTitle(),
                "fileName", file.getName()
            );
        }
    }

    public void exportAllNotesToPdf(
        File file,
        String currentTitle,
        String editedTitle,
        String editedContent,
        Window owner,
        boolean darkTheme
    ) {
        if (file == null) {
            return;
        }

        SmartNotesApplicationService.SaveResult saved = saveCurrentInternal(currentTitle, editedTitle, editedContent, true);
        if (saved != null && !saved.noteTitle().isBlank()) {
            rebuildState(saved.noteTitle(), state.searchQuery(), state.statusMessage());
        }

        List<String> titles = services.applicationService().listTitles();
        if (titles.isEmpty()) {
            UiErrorNotifier.showInfo(owner, darkTheme, "Экспорт заметок", "Нет заметок для экспорта.");
            return;
        }

        List<SmartNotesExportService.NoteExport> notes = new ArrayList<>();
        for (String title : titles) {
            notes.add(new SmartNotesExportService.NoteExport(title, services.applicationService().loadContent(title)));
        }

        try {
            services.exportService().exportAllNotesToPdf(file, notes);
            UiErrorNotifier.showInfo(owner, darkTheme, "Экспорт завершён", "PDF сохранён: " + file.getName());
            rebuildState(state.selectedNoteTitle(), state.searchQuery(), "Экспорт завершён");
        } catch (Exception ex) {
            UiErrorNotifier.showMappedError(
                owner,
                darkTheme,
                "Ошибка экспорта PDF",
                ex,
                ErrorCode.EXPORT_PDF_FAILED,
                "Не удалось экспортировать заметки в PDF.",
                false,
                "operation", "exportAllNotesToPdf",
                "notesCount", titles.size(),
                "fileName", file.getName()
            );
        }
    }

    public String renderPreviewHtml(String markdown, String searchQuery, boolean darkTheme) {
        return services.exportService().renderPreviewHtml(markdown, searchQuery, darkTheme);
    }

    public String sanitizeExportFileName(String name, String fallback) {
        return services.exportService().sanitizeFileName(name, fallback);
    }

    public SmartNotesState getState() {
        return state;
    }

    public SmartNotesServices getServices() {
        return services;
    }

    public UndoRedoManager.CommandResult undoLastAction() {
        UndoRedoManager.CommandResult result = undoRedoManager.undo();
        if (result.successful()) {
            rebuildState(
                consumeHistorySelectionHint(state.selectedNoteTitle()),
                consumeHistoryQueryHint(state.searchQuery()),
                "Отменено: " + result.actionId()
            );
            return result;
        }
        updateUndoRedoStateOnly(result.message());
        return result;
    }

    public UndoRedoManager.CommandResult redoLastAction() {
        UndoRedoManager.CommandResult result = undoRedoManager.redo();
        if (result.successful()) {
            rebuildState(
                consumeHistorySelectionHint(state.selectedNoteTitle()),
                consumeHistoryQueryHint(state.searchQuery()),
                "Повторено: " + result.actionId()
            );
            return result;
        }
        updateUndoRedoStateOnly(result.message());
        return result;
    }

    private void rebuildState(String preferredSelection, String query, String statusMessage) {
        String normalizedQuery = normalizeQuery(query);
        List<String> titles = services.applicationService().searchTitles(normalizedQuery);

        String selected = resolveSelection(preferredSelection, titles);
        if (selected.isBlank() && normalizedQuery.isEmpty()) {
            selected = services.applicationService().createNewNote();
            titles = services.applicationService().searchTitles(normalizedQuery);
        }

        String content = selected.isBlank() ? "" : services.applicationService().loadContent(selected);
        SmartNotesState next = state.withData(titles, selected, content, normalizedQuery, statusMessage);
        state = withUndoRedoState(next);
        view.render(state);
    }

    private void updateUndoRedoStateOnly(String statusMessage) {
        SmartNotesState next = withUndoRedoState(state);
        if (statusMessage != null && !statusMessage.isBlank()) {
            next = next.withStatusMessage(statusMessage);
        }
        state = next;
        view.render(state);
    }

    private SmartNotesState withUndoRedoState(SmartNotesState source) {
        return source.withUndoRedoState(
            undoRedoManager.canUndo(),
            undoRedoManager.canRedo(),
            undoRedoManager.nextUndoLabel(),
            undoRedoManager.nextRedoLabel()
        );
    }

    private String resolveSelection(String preferred, List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return "";
        }

        String candidate = preferred == null ? "" : preferred.trim();
        if (!candidate.isBlank() && titles.contains(candidate)) {
            return candidate;
        }
        return titles.get(0);
    }

    private void executeUndoableLifecycleCommand(
        String actionId,
        String label,
        String category,
        Runnable mutation,
        Supplier<String> preferredSelectionAfterExecute,
        Supplier<String> queryAfterExecute,
        Supplier<String> successStatusMessage
    ) {
        AtomicReference<RuntimeException> failureRef = new AtomicReference<>();
        UserActionCommand command = trackFailure(
            snapshotBackedCommand(
                actionId,
                label,
                category,
                mutation,
                preferredSelectionAfterExecute,
                queryAfterExecute
            ),
            failureRef
        );
        UndoRedoManager.CommandResult result = undoRedoManager.execute(command);
        if (result.successful()) {
            rebuildState(
                consumeHistorySelectionHint(state.selectedNoteTitle()),
                consumeHistoryQueryHint(state.searchQuery()),
                successStatusMessage == null ? state.statusMessage() : successStatusMessage.get()
            );
            return;
        }
        updateUndoRedoStateOnly(result.message());
        RuntimeException failure = failureRef.get();
        if (failure != null) {
            throw failure;
        }
        throw new IllegalStateException(result.message());
    }

    private UserActionCommand snapshotBackedCommand(
        String actionId,
        String label,
        String category,
        Runnable mutation,
        Supplier<String> preferredSelectionAfterExecute,
        Supplier<String> queryAfterExecute
    ) {
        SmartNotesHistorySnapshot beforeState = captureHistorySnapshot();
        Supplier<String> safeSelectionSupplier = preferredSelectionAfterExecute == null
            ? state::selectedNoteTitle
            : preferredSelectionAfterExecute;
        Supplier<String> safeQuerySupplier = queryAfterExecute == null
            ? state::searchQuery
            : queryAfterExecute;
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
                rememberHistoryHint(safeSelectionSupplier.get(), safeQuerySupplier.get());
            }

            @Override
            public void undo() {
                services.applicationService().restoreSnapshot(beforeState.notesSnapshot());
                rememberHistoryHint(beforeState.selectedNoteTitle(), beforeState.searchQuery());
            }
        };
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

    private SmartNotesHistorySnapshot captureHistorySnapshot() {
        return new SmartNotesHistorySnapshot(
            services.applicationService().captureSnapshot(),
            state.selectedNoteTitle(),
            state.searchQuery()
        );
    }

    private void rememberHistoryHint(String selection, String query) {
        historySelectionHint = selection == null ? "" : selection.trim();
        historyQueryHint = query == null ? "" : query.trim();
    }

    private String consumeHistorySelectionHint(String fallback) {
        String hint = historySelectionHint;
        historySelectionHint = "";
        if (hint == null || hint.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return hint;
    }

    private String consumeHistoryQueryHint(String fallback) {
        String hint = historyQueryHint;
        historyQueryHint = "";
        if (hint == null || hint.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return hint;
    }

    private SmartNotesApplicationService.SaveResult saveCurrentInternal(
        String currentTitle,
        String editedTitle,
        String editedContent,
        boolean allowCreateWhenMissingCurrent
    ) {
        if (!hasSavablePayload(currentTitle, editedTitle, editedContent, allowCreateWhenMissingCurrent)) {
            return null;
        }

        String safeCurrentTitle = normalizeTitle(currentTitle);
        String safeEditedTitle = normalizeTitle(editedTitle);
        return services.applicationService().saveCurrent(safeCurrentTitle, safeEditedTitle, editedContent);
    }

    private boolean hasSavablePayload(
        String currentTitle,
        String editedTitle,
        String editedContent,
        boolean allowCreateWhenMissingCurrent
    ) {
        String safeCurrentTitle = normalizeTitle(currentTitle);
        String safeEditedTitle = normalizeTitle(editedTitle);
        if (safeCurrentTitle.isBlank() && !allowCreateWhenMissingCurrent) {
            return false;
        }
        return !(safeCurrentTitle.isBlank()
            && safeEditedTitle.isBlank()
            && (editedContent == null || editedContent.isBlank()));
    }

    private String normalizeTitle(String title) {
        return title == null ? "" : title.trim();
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim();
    }

    private List<LinkChipViewModel> mapLinks(List<SmartNotesApplicationService.LinkChip> raw) {
        List<LinkChipViewModel> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (SmartNotesApplicationService.LinkChip chip : raw) {
            if (chip == null) {
                continue;
            }
            result.add(new LinkChipViewModel(chip.label(), chip.target(), chip.type(), chip.exists()));
        }
        return result;
    }

    public record LinkChipViewModel(
        String label,
        String target,
        SmartNotesApplicationService.LinkType type,
        boolean exists
    ) {
    }

    private record SmartNotesHistorySnapshot(
        SmartNotesApplicationService.NotesSnapshot notesSnapshot,
        String selectedNoteTitle,
        String searchQuery
    ) {
    }
}
