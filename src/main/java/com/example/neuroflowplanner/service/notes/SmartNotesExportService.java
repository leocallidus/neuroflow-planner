package com.example.neuroflowplanner.service.notes;

import java.io.File;
import java.util.List;

public interface SmartNotesExportService {

    record NoteExport(String title, String content) {
    }

    void exportNoteToPdf(File file, String noteTitle, String noteContent) throws Exception;

    void exportAllNotesToPdf(File file, List<NoteExport> notes) throws Exception;

    void exportNoteToMarkdown(File file, String noteTitle, String noteContent) throws Exception;

    String renderPreviewHtml(String markdown, String searchQuery, boolean darkTheme);

    String sanitizeFileName(String name, String fallback);
}
