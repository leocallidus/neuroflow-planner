package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NotesService {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(NotesService.class);
    private static final String NOTES_SUBDIR = "notes";
    private static NotesService instance;
    private final Path notesDir;

    private NotesService() {
        this.notesDir = resolveNotesDir();
        try {
            Files.createDirectories(notesDir);
        } catch (IOException e) {
            LOG.error(
                "notes.directory.create.failed",
                ErrorCode.IO_WRITE_FAILED,
                e,
                "operation", "createDirectories",
                "notesDir", notesDir
            );
        }
    }

    private static Path resolveNotesDir() {
        return DataPathManager.getDataDirectory().resolve(NOTES_SUBDIR).toAbsolutePath().normalize();
    }

    public static synchronized NotesService getInstance() {
        if (instance == null) {
            instance = new NotesService();
        }
        return instance;
    }

    /**
     * Test-only seam: reset singleton lifecycle and recompute notes path.
     */
    public static synchronized void resetForTesting() {
        DataPathManager.reinitializeForTesting();
        instance = null;
        LOG.info("notes.service.reset.for.testing", "notesDir", resolveNotesDir());
    }

    public List<String> getAllNoteTitles() {
        try (Stream<Path> stream = Files.list(notesDir)) {
            return stream
                .filter(file -> !Files.isDirectory(file))
                .map(Path::getFileName)
                .map(Path::toString)
                .filter(name -> name.endsWith(".md"))
                .map(name -> name.substring(0, name.length() - 3)) // Remove .md
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException e) {
            LOG.error(
                "notes.list.failed",
                ErrorCode.IO_READ_FAILED,
                e,
                "operation", "listNoteTitles",
                "notesDir", notesDir
            );
            return Collections.emptyList();
        }
    }

    public String loadNoteContent(String title) {
        try {
            Path path = notesDir.resolve(title + ".md");
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            LOG.error(
                "notes.load.failed",
                ErrorCode.IO_READ_FAILED,
                e,
                "operation", "loadNote",
                "noteTitle", title,
                "notesDir", notesDir
            );
        }
        return "";
    }

    public void saveNote(String title, String content) {
        if (title == null || title.isBlank()) return;
        // Sanitize title
        String safeTitle = title.replaceAll("[^a-zA-Z0-9а-яА-Я _-]", "").trim();
        if (safeTitle.isEmpty()) safeTitle = "Untitled";

        try {
            Path path = notesDir.resolve(safeTitle + ".md");
            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.error(
                "notes.save.failed",
                ErrorCode.IO_WRITE_FAILED,
                e,
                "operation", "saveNote",
                "noteTitle", safeTitle,
                "notesDir", notesDir
            );
        }
    }

    public void deleteNote(String title) {
        try {
            Path path = notesDir.resolve(title + ".md");
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.error(
                "notes.delete.failed",
                ErrorCode.IO_DELETE_FAILED,
                e,
                "operation", "deleteNote",
                "noteTitle", title,
                "notesDir", notesDir
            );
        }
    }
    
    public void renameNote(String oldTitle, String newTitle) {
        try {
            Path source = notesDir.resolve(oldTitle + ".md");
            Path target = notesDir.resolve(newTitle + ".md");
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOG.error(
                "notes.rename.failed",
                ErrorCode.IO_WRITE_FAILED,
                e,
                "operation", "renameNote",
                "oldTitle", oldTitle,
                "newTitle", newTitle,
                "notesDir", notesDir
            );
        }
    }
}
