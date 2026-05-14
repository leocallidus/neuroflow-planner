package com.example.neuroflowplanner.service.notes;

import com.example.neuroflowplanner.model.Task;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface SmartNotesApplicationService {

    record SaveResult(String noteTitle, String content, boolean renamed) {
    }

    record NoteSnapshot(String title, String content) {
    }

    record NotesSnapshot(List<NoteSnapshot> notes) {
        public NotesSnapshot {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    enum LinkType {
        NOTE,
        TASK
    }

    record LinkChip(String label, String target, LinkType type, boolean exists) {
    }

    List<String> listTitles();

    List<String> searchTitles(String query);

    String loadContent(String title);

    SaveResult saveCurrent(String currentTitle, String editedTitle, String content);

    String createNewNote();

    String createNoteWithTitle(String title);

    String createFromTemplate(String templateKey);

    void deleteNote(String title);

    String resolveExistingTitle(String title);

    List<LinkChip> outgoingLinks(String content, Function<String, Task> taskResolver);

    List<LinkChip> incomingLinks(String noteTitle, Supplier<List<Task>> taskProvider);

    NotesSnapshot captureSnapshot();

    void restoreSnapshot(NotesSnapshot snapshot);
}
