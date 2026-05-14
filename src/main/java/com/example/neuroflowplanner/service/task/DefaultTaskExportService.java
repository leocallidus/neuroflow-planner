package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.model.Task;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.io.font.PdfEncodings;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTaskExportService implements TaskExportService {
    private static final int TASK_EXPORT_SCHEMA_VERSION = 1;

    @Override
    public void exportInsight(File file, String extension, String content) throws Exception {
        String ext = extension == null ? ".pdf" : extension;
        if (".pdf".equals(ext)) {
            exportToPdf(file, content);
            return;
        }
        if (".md".equals(ext)) {
            exportToMd(file, content);
            return;
        }
        if (".docx".equals(ext)) {
            exportToDocx(file, content);
            return;
        }
        throw new IllegalArgumentException("Unsupported export extension: " + ext);
    }

    @Override
    public String serializeTasksJson(List<Task> tasks) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaType", "task-export");
        payload.put("schemaVersion", TASK_EXPORT_SCHEMA_VERSION);
        payload.put("exportedAt", LocalDateTime.now().toString());
        payload.put("source", Map.of("app", "NeuroFlow Planner", "module", "tasks"));
        payload.put("tasks", flattenTasks(tasks));
        return AiObjectMapperFactory.providerResponseMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(payload);
    }

    @Override
    public void exportTasksJson(File file, List<Task> tasks) throws Exception {
        if (file == null) {
            throw new IllegalArgumentException("File is required");
        }
        Files.writeString(file.toPath(), serializeTasksJson(tasks), StandardCharsets.UTF_8);
    }

    private void exportToPdf(File file, String content) throws Exception {
        try (PdfWriter writer = new PdfWriter(file); PdfDocument pdf = new PdfDocument(writer); Document doc = new Document(pdf)) {
            PdfFont font = null;
            String[] fontPaths = {
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "C:/Windows/Fonts/arial.ttf"
            };
            for (String path : fontPaths) {
                try {
                    font = PdfFontFactory.createFont(path, PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    break;
                } catch (Exception ignored) {
                }
            }
            if (font != null) {
                doc.setFont(font);
            }

            doc.add(new Paragraph("ИИ-Рекомендации NeuroFlow").setFontSize(20));
            doc.add(new Paragraph(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).setFontSize(10));
            doc.add(new Paragraph(" "));

            String safe = content == null ? "" : content;
            for (String line : safe.split("\\n")) {
                doc.add(new Paragraph(line));
            }
        }
    }

    private void exportToMd(File file, String content) throws Exception {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("# ИИ-Рекомендации NeuroFlow\\n\\n");
            fw.write("*" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + "*\\n\\n");
            fw.write("---\\n\\n");
            fw.write(content == null ? "" : content);
        }
    }

    private void exportToDocx(File file, String content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); FileOutputStream out = new FileOutputStream(file)) {
            XWPFParagraph title = doc.createParagraph();
            XWPFRun titleRun = title.createRun();
            titleRun.setText("ИИ-Рекомендации NeuroFlow");
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            XWPFParagraph date = doc.createParagraph();
            date.createRun().setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            String safe = content == null ? "" : content;
            for (String line : safe.split("\\n")) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(line);
            }
            doc.write(out);
        }
    }

    private List<Map<String, Object>> flattenTasks(List<Task> roots) {
        List<Map<String, Object>> flattened = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        if (roots == null) {
            return flattened;
        }
        for (Task task : roots) {
            flattenTask(task, null, flattened, seenIds);
        }
        return flattened;
    }

    private void flattenTask(Task task, String inheritedParentId, List<Map<String, Object>> sink, Set<String> seenIds) {
        if (task == null) {
            return;
        }
        String taskId = normalize(task.getId());
        if (taskId != null && !seenIds.add(taskId)) {
            return;
        }

        String resolvedParentId = firstNonBlank(task.getParentId(), inheritedParentId);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", taskId);
        entry.put("title", normalize(task.getTitle()));
        entry.put("description", normalize(task.getDescription()));
        entry.put("deadline", formatDate(task.getDeadline()));
        entry.put("complexity", task.getComplexity());
        entry.put("parentId", resolvedParentId);
        entry.put("tags", normalize(task.getTags()));
        entry.put("recurrence", normalize(task.getRecurrence()));
        entry.put("archived", task.isArchived());
        entry.put("trackedMinutes", task.getTrackedMinutes());
        entry.put("startDate", formatDate(task.getStartDate()));
        entry.put("completed", task.isCompleted());
        entry.put("completedDate", formatDate(task.getCompletedDate()));
        sink.add(entry);

        for (Task subtask : task.getSubtasks()) {
            flattenTask(subtask, taskId, sink, seenIds);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalize(first);
        return normalizedFirst != null ? normalizedFirst : normalize(second);
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
