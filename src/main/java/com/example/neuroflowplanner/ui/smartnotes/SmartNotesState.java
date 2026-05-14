package com.example.neuroflowplanner.ui.smartnotes;

import java.time.Instant;
import java.util.List;

public record SmartNotesState(
    boolean initialized,
    List<String> noteTitles,
    String selectedNoteTitle,
    String selectedNoteContent,
    String searchQuery,
    String statusMessage,
    boolean undoAvailable,
    boolean redoAvailable,
    String nextUndoLabel,
    String nextRedoLabel,
    Instant updatedAt
) {
    public SmartNotesState {
        noteTitles = noteTitles == null ? List.of() : List.copyOf(noteTitles);
        selectedNoteTitle = selectedNoteTitle == null ? "" : selectedNoteTitle;
        selectedNoteContent = selectedNoteContent == null ? "" : selectedNoteContent;
        searchQuery = searchQuery == null ? "" : searchQuery;
        statusMessage = statusMessage == null ? "ready" : statusMessage;
        nextUndoLabel = safeLabel(nextUndoLabel);
        nextRedoLabel = safeLabel(nextRedoLabel);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public static SmartNotesState initial() {
        return new SmartNotesState(false, List.of(), "", "", "", "ready", false, false, "", "", Instant.now());
    }

    public SmartNotesState withData(
        List<String> noteTitles,
        String selectedNoteTitle,
        String selectedNoteContent,
        String searchQuery,
        String statusMessage
    ) {
        return new SmartNotesState(
            true,
            noteTitles,
            selectedNoteTitle,
            selectedNoteContent,
            searchQuery,
            statusMessage,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    public SmartNotesState withUndoRedoState(
        boolean undoAvailable,
        boolean redoAvailable,
        String nextUndoLabel,
        String nextRedoLabel
    ) {
        return new SmartNotesState(
            initialized,
            noteTitles,
            selectedNoteTitle,
            selectedNoteContent,
            searchQuery,
            statusMessage,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    public SmartNotesState withStatusMessage(String statusMessage) {
        String safeStatus = statusMessage == null || statusMessage.isBlank() ? this.statusMessage : statusMessage;
        return new SmartNotesState(
            initialized,
            noteTitles,
            selectedNoteTitle,
            selectedNoteContent,
            searchQuery,
            safeStatus,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    private static String safeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
