package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DefaultTaskImportService implements TaskImportService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DefaultTaskImportService.class);
    private static final String OPERATION_DRY_RUN = "task.import.dryRun";
    private static final String OPERATION_APPLY = "task.import.apply";
    private static final ObjectMapper MAPPER = AiObjectMapperFactory.providerResponseMapper();

    private final TaskApplicationService taskApplicationService;
    private final TaskAnalysisService taskAnalysisService;

    public DefaultTaskImportService() {
        this(new DefaultTaskApplicationService(), new DefaultTaskAnalysisService());
    }

    public DefaultTaskImportService(TaskApplicationService taskApplicationService, TaskAnalysisService taskAnalysisService) {
        this.taskApplicationService = Objects.requireNonNull(taskApplicationService, "taskApplicationService is required");
        this.taskAnalysisService = Objects.requireNonNull(taskAnalysisService, "taskAnalysisService is required");
    }

    @Override
    public ImportPreview dryRun(String payload, ImportFormat format, ImportOptions options) {
        ImportFormat resolvedFormat = Objects.requireNonNull(format, "Import format is required");
        ImportOptions resolvedOptions = options == null ? ImportOptions.defaults() : options;
        String normalizedPayload = payload == null ? "" : payload.trim();
        if (normalizedPayload.isEmpty()) {
            throw new IllegalArgumentException("Import payload is empty");
        }

        List<ParsedRow> rows = parseRows(normalizedPayload, resolvedFormat);
        List<String> warnings = new ArrayList<>();
        List<ParsedTask> parsedTasks = new ArrayList<>();
        int invalidCount = 0;

        for (ParsedRow row : rows) {
            ParsedTask parsedTask = parseTaskRow(row, warnings);
            if (parsedTask == null) {
                invalidCount++;
                continue;
            }
            parsedTasks.add(parsedTask);
        }

        Map<String, Task> existingById = loadExistingTasksById();
        Set<String> existingTitles = loadExistingTitles(existingById.values());

        List<ParsedTask> dedupedById = dedupeById(parsedTasks, resolvedOptions.duplicateIdPolicy());
        int duplicateIdCount = parsedTasks.size() - dedupedById.size();

        List<ParsedTask> accepted = new ArrayList<>();
        int duplicateTitleCount = 0;
        Set<String> acceptedTitles = new HashSet<>();

        for (ParsedTask task : dedupedById) {
            String normalizedTitle = normalizeTitle(task.title());
            Task existingTask = existingById.get(task.id());
            if (shouldSkipByTitle(resolvedOptions.titleDedupePolicy(), normalizedTitle, existingTask, existingTitles, acceptedTitles)) {
                duplicateTitleCount++;
                warnings.add("Строка " + task.rowNumber() + ": пропущен дубликат title='" + task.title() + "'");
                continue;
            }
            accepted.add(task);
            acceptedTitles.add(normalizedTitle);
        }

        List<Task> tasksToPersist = new ArrayList<>(accepted.size());
        int toCreateCount = 0;
        int toUpdateCount = 0;

        for (ParsedTask parsedTask : accepted) {
            Task existingTask = existingById.get(parsedTask.id());
            Task mapped = mapToTask(parsedTask);
            if (resolvedOptions.recalculatePriority()) {
                taskAnalysisService.calculatePriority(mapped);
            }
            if (existingTask == null) {
                toCreateCount++;
            } else {
                toUpdateCount++;
            }
            tasksToPersist.add(mapped);
        }

        LOG.info(
            OPERATION_DRY_RUN,
            "format", resolvedFormat.name().toLowerCase(Locale.ROOT),
            "sourceCount", rows.size(),
            "acceptedCount", tasksToPersist.size(),
            "toCreateCount", toCreateCount,
            "toUpdateCount", toUpdateCount,
            "duplicateIdCount", duplicateIdCount,
            "duplicateTitleCount", duplicateTitleCount,
            "invalidCount", invalidCount
        );

        return new ImportPreview(
            resolvedFormat,
            rows.size(),
            tasksToPersist.size(),
            toCreateCount,
            toUpdateCount,
            duplicateIdCount,
            duplicateTitleCount,
            invalidCount,
            warnings,
            tasksToPersist
        );
    }

    @Override
    public ImportResult apply(ImportPreview preview) {
        if (preview == null) {
            throw new IllegalArgumentException("Import preview is required");
        }

        TaskBulkOperationResult bulkResult;
        if (!preview.hasChanges()) {
            bulkResult = new TaskBulkOperationResult("saveTasksBatch", 0, 0, 0, 0, 0);
        } else {
            bulkResult = taskApplicationService.saveTasksBulk(preview.tasksToPersist());
            if (!bulkResult.isSuccessful()) {
                throw new IllegalStateException("Import apply failed: partial result is not allowed");
            }
        }

        LOG.info(
            OPERATION_APPLY,
            "acceptedCount", preview.acceptedCount(),
            "toCreateCount", preview.toCreateCount(),
            "toUpdateCount", preview.toUpdateCount(),
            "updatedCount", bulkResult.updatedCount(),
            "failedCount", bulkResult.failedCount()
        );

        return new ImportResult(preview, bulkResult);
    }

    private List<ParsedRow> parseRows(String payload, ImportFormat format) {
        return switch (format) {
            case JSON -> parseJsonRows(payload);
            case CSV -> parseCsvRows(payload);
        };
    }

    private List<ParsedRow> parseJsonRows(String payload) {
        List<ParsedRow> rows = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode tasksNode = root;
            if (root != null && root.isObject()) {
                tasksNode = root.get("tasks");
            }
            if (tasksNode == null || !tasksNode.isArray()) {
                throw new IllegalArgumentException("JSON import expects array payload or object with 'tasks' array");
            }
            int rowNumber = 1;
            for (JsonNode node : tasksNode) {
                if (node == null || !node.isObject()) {
                    rowNumber++;
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value == null || value.isNull()) {
                        return;
                    }
                    values.put(normalizeColumn(entry.getKey()), value.isTextual() ? value.asText() : value.toString());
                });
                rows.add(new ParsedRow(rowNumber++, values));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse JSON import payload", e);
        }
    }

    private List<ParsedRow> parseCsvRows(String payload) {
        String[] lines = payload.split("\\R", -1);
        int headerIndex = findHeaderIndex(lines);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("CSV import payload must contain a header row");
        }

        List<String> headers = parseCsvLine(lines[headerIndex]);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV import header is empty");
        }

        List<String> normalizedHeaders = headers.stream()
            .map(this::normalizeColumn)
            .toList();

        List<ParsedRow> rows = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            List<String> values = parseCsvLine(line);
            Map<String, String> mapped = new LinkedHashMap<>();
            for (int col = 0; col < normalizedHeaders.size(); col++) {
                String key = normalizedHeaders.get(col);
                String value = col < values.size() ? values.get(col) : "";
                mapped.put(key, value);
            }
            rows.add(new ParsedRow(i + 1, mapped));
        }
        return rows;
    }

    private ParsedTask parseTaskRow(ParsedRow row, List<String> warnings) {
        String idRaw = normalizeNullable(row.value("id"));
        String id = idRaw == null ? UUID.randomUUID().toString() : idRaw;

        String title = normalizeNullable(row.value("title"));
        if (title == null) {
            warnings.add("Строка " + row.rowNumber() + ": отсутствует обязательное поле title");
            return null;
        }

        LocalDate deadline = parseDate(row.value("deadline"), "deadline", row.rowNumber(), warnings, true);
        if (deadline == null) {
            return null;
        }

        int complexity = parseComplexity(row.value("complexity"), row.rowNumber(), warnings);
        String description = normalizeNullable(row.value("description"));
        String tags = normalizeNullable(row.value("tags"));
        String recurrence = normalizeRecurrence(row.value("recurrence"), row.rowNumber(), warnings);
        String parentId = normalizeNullable(firstNonBlank(row.value("parent_id"), row.value("parentid")));

        Boolean archived = parseBoolean(row.value("archived"), "archived", row.rowNumber(), warnings);
        Long trackedMinutes = parseLong(row.value("tracked_minutes"), "tracked_minutes", row.rowNumber(), warnings);
        LocalDate startDate = parseDate(firstNonBlank(row.value("start_date"), row.value("startdate")), "start_date", row.rowNumber(), warnings, false);
        Boolean completed = parseBoolean(row.value("completed"), "completed", row.rowNumber(), warnings);
        LocalDate completedDate = parseDate(firstNonBlank(row.value("completed_date"), row.value("completeddate")), "completed_date", row.rowNumber(), warnings, false);

        return new ParsedTask(
            row.rowNumber(),
            id,
            title,
            description == null ? "" : description,
            deadline,
            complexity,
            parentId,
            tags == null ? "" : tags,
            recurrence,
            archived,
            trackedMinutes,
            startDate,
            completed,
            completedDate
        );
    }

    private List<ParsedTask> dedupeById(List<ParsedTask> parsedTasks, DuplicateIdPolicy policy) {
        if (parsedTasks.isEmpty()) {
            return List.of();
        }

        Map<String, ParsedTask> byId = new LinkedHashMap<>();
        if (policy == DuplicateIdPolicy.KEEP_FIRST) {
            for (ParsedTask parsedTask : parsedTasks) {
                byId.putIfAbsent(parsedTask.id(), parsedTask);
            }
        } else {
            for (ParsedTask parsedTask : parsedTasks) {
                byId.put(parsedTask.id(), parsedTask);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private boolean shouldSkipByTitle(
        TitleDedupePolicy policy,
        String normalizedTitle,
        Task existingTask,
        Set<String> existingTitles,
        Set<String> acceptedTitles
    ) {
        if (policy != TitleDedupePolicy.SKIP_EXISTING) {
            return false;
        }
        if (normalizedTitle == null) {
            return false;
        }

        if (acceptedTitles.contains(normalizedTitle)) {
            return true;
        }

        if (existingTask == null) {
            return existingTitles.contains(normalizedTitle);
        }

        String existingTitle = normalizeTitle(existingTask.getTitle());
        if (Objects.equals(existingTitle, normalizedTitle)) {
            return false;
        }
        return existingTitles.contains(normalizedTitle);
    }

    private Task mapToTask(ParsedTask parsedTask) {
        Task task = new Task(
            parsedTask.id(),
            parsedTask.title(),
            parsedTask.description(),
            parsedTask.deadline(),
            parsedTask.complexity(),
            parsedTask.parentId(),
            parsedTask.tags(),
            parsedTask.recurrence()
        );

        task.setArchived(parsedTask.archived() != null && parsedTask.archived());
        task.setTrackedMinutes(parsedTask.trackedMinutes() == null ? 0L : Math.max(0L, parsedTask.trackedMinutes()));
        task.setStartDate(parsedTask.startDate());
        task.setCompleted(parsedTask.completed() != null && parsedTask.completed());
        task.setCompletedDate(task.isCompleted() ? parsedTask.completedDate() : null);
        return task;
    }

    private Map<String, Task> loadExistingTasksById() {
        Map<String, Task> byId = new HashMap<>();
        for (Task task : flattenTasks(taskApplicationService.loadTasks())) {
            if (task == null || task.getId() == null || task.getId().isBlank()) {
                continue;
            }
            byId.put(task.getId(), task);
        }
        return byId;
    }

    private Set<String> loadExistingTitles(Iterable<Task> tasks) {
        Set<String> titles = new LinkedHashSet<>();
        for (Task task : tasks) {
            String normalized = normalizeTitle(task == null ? null : task.getTitle());
            if (normalized != null) {
                titles.add(normalized);
            }
        }
        return titles;
    }

    private List<Task> flattenTasks(List<Task> roots) {
        List<Task> all = new ArrayList<>();
        if (roots == null) {
            return all;
        }
        for (Task task : roots) {
            flatten(task, all);
        }
        return all;
    }

    private void flatten(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            flatten(subtask, sink);
        }
    }

    private int findHeaderIndex(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line != null && !line.trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        values.add(current.toString().trim());
        return values;
    }

    private int parseComplexity(String rawValue, int rowNumber, List<String> warnings) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            return 1;
        }
        try {
            int complexity = Integer.parseInt(normalized);
            if (complexity < 1) {
                warnings.add("Строка " + rowNumber + ": complexity < 1, использовано значение 1");
                return 1;
            }
            return complexity;
        } catch (NumberFormatException ex) {
            warnings.add("Строка " + rowNumber + ": неверный complexity, использовано значение 1");
            return 1;
        }
    }

    private Long parseLong(String rawValue, String fieldName, int rowNumber, List<String> warnings) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            long value = Long.parseLong(normalized);
            if (value < 0) {
                warnings.add("Строка " + rowNumber + ": " + fieldName + " < 0, использовано значение 0");
                return 0L;
            }
            return value;
        } catch (NumberFormatException ex) {
            warnings.add("Строка " + rowNumber + ": неверный формат " + fieldName + ", поле пропущено");
            return null;
        }
    }

    private Boolean parseBoolean(String rawValue, String fieldName, int rowNumber, List<String> warnings) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            return null;
        }
        switch (normalized.toLowerCase(Locale.ROOT)) {
            case "true":
            case "1":
            case "yes":
            case "y":
            case "да":
                return true;
            case "false":
            case "0":
            case "no":
            case "n":
            case "нет":
                return false;
            default:
                warnings.add("Строка " + rowNumber + ": неверный формат " + fieldName + ", поле пропущено");
                return null;
        }
    }

    private LocalDate parseDate(String rawValue, String fieldName, int rowNumber, List<String> warnings, boolean required) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            if (required) {
                warnings.add("Строка " + rowNumber + ": отсутствует обязательное поле " + fieldName);
            }
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ex) {
            warnings.add("Строка " + rowNumber + ": неверный формат даты для " + fieldName + " (ожидается yyyy-MM-dd)");
            return null;
        }
    }

    private String normalizeRecurrence(String rawValue, int rowNumber, List<String> warnings) {
        String normalized = normalizeNullable(rawValue);
        if (normalized == null) {
            return "";
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "daily", "weekly", "monthly", "yearly" -> value;
            default -> {
                warnings.add("Строка " + rowNumber + ": неизвестное recurrence='" + normalized + "', значение очищено");
                yield "";
            }
        };
    }

    private String normalizeColumn(String rawColumn) {
        if (rawColumn == null) {
            return "";
        }
        String normalized = rawColumn.trim()
            .toLowerCase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_");
        return switch (normalized) {
            case "parent", "parent_task_id", "parenttaskid" -> "parent_id";
            case "parentid" -> "parent_id";
            case "startdate" -> "start_date";
            case "trackedminutes" -> "tracked_minutes";
            case "completeddate" -> "completed_date";
            default -> normalized;
        };
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeTitle(String title) {
        String normalized = normalizeNullable(title);
        if (normalized == null) {
            return null;
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = normalizeNullable(first);
        if (normalizedFirst != null) {
            return normalizedFirst;
        }
        return normalizeNullable(second);
    }

    private record ParsedRow(int rowNumber, Map<String, String> values) {
        private String value(String key) {
            if (values == null || key == null) {
                return null;
            }
            return values.get(key);
        }
    }

    private record ParsedTask(
        int rowNumber,
        String id,
        String title,
        String description,
        LocalDate deadline,
        int complexity,
        String parentId,
        String tags,
        String recurrence,
        Boolean archived,
        Long trackedMinutes,
        LocalDate startDate,
        Boolean completed,
        LocalDate completedDate
    ) {
    }
}
