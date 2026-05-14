package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Wave1SyncPayloadMapper {
    private final DatabaseManager databaseManager;
    private final ObjectMapper mapper;

    Wave1SyncPayloadMapper() {
        this(DatabaseManager.getInstance(), AiObjectMapperFactory.createMapper(false));
    }

    Wave1SyncPayloadMapper(DatabaseManager databaseManager, ObjectMapper mapper) {
        this.databaseManager = databaseManager;
        this.mapper = mapper;
    }

    JsonNode buildPayload(LocalSyncOutboxEntry entry) {
        if (entry == null || isBlank(entry.entityType())) {
            return null;
        }
        if ("GOAL_PROGRESS_ENTRY".equalsIgnoreCase(entry.entityType())) {
            return parseJson(entry.payloadJson());
        }
        return databaseManager.runInTransaction(
            "buildWave1SyncPayload",
            connection -> buildPayload(connection, entry),
            "entityType", entry.entityType(),
            "entityId", entry.entityId(),
            "operation", entry.operation()
        );
    }

    TaskDependencyKey resolveTaskDependency(LocalSyncOutboxEntry entry) {
        if (entry == null) {
            return null;
        }
        JsonNode payload = parseJson(entry.payloadJson());
        if (payload != null) {
            String dependentTaskId = text(payload, "dependent_task_id");
            String blockerTaskId = text(payload, "blocker_task_id");
            if (!isBlank(dependentTaskId) && !isBlank(blockerTaskId)) {
                return new TaskDependencyKey(dependentTaskId, blockerTaskId);
            }
        }
        if (isBlank(entry.entityId())) {
            return null;
        }
        return databaseManager.runInTransaction(
            "resolveTaskDependencyNaturalKey",
            connection -> loadTaskDependencyByEntityId(connection, entry.entityId()),
            "entityId", entry.entityId()
        );
    }

    private JsonNode buildPayload(Connection connection, LocalSyncOutboxEntry entry) throws SQLException {
        String entityType = entry.entityType().trim().toUpperCase(Locale.ROOT);
        return switch (entityType) {
            case "TASK" -> buildTaskPayload(connection, entry.entityId());
            case "TASK_DEPENDENCY" -> buildTaskDependencyPayload(connection, entry.entityId());
            case "TIME_SESSION" -> buildTimeSessionPayload(connection, entry.entityId());
            case "TASK_TEMPLATE" -> buildTaskTemplatePayload(connection, entry.entityId());
            case "GOAL" -> buildGoalPayload(connection, entry.entityId());
            case "MOOD_ENTRY" -> buildMoodEntryPayload(connection, entry.entityId());
            default -> parseJson(entry.payloadJson());
        };
    }

    private JsonNode buildTaskPayload(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT id, title, description, deadline, complexity, smart_priority, ai_insight,
                   parent_id, tags, recurrence, archived, updated_at, start_date, start_time,
                   completed, completed_date, deadline_time
            FROM tasks
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", resultSet.getString("id"));
                putNullable(payload, "parent_task_id", resultSet.getString("parent_id"));
                payload.put("title", resultSet.getString("title"));
                putNullable(payload, "description", resultSet.getString("description"));
                putNullable(payload, "start_date", resultSet.getString("start_date"));
                putNullable(payload, "start_time", normalizeLocalTime(resultSet.getString("start_time")));
                putNullable(payload, "deadline_date", resultSet.getString("deadline"));
                putNullable(payload, "deadline_time", normalizeLocalTime(resultSet.getString("deadline_time")));
                payload.put("complexity", resultSet.getInt("complexity"));
                payload.put("smart_priority", resultSet.getDouble("smart_priority"));
                putNullable(payload, "ai_insight", resultSet.getString("ai_insight"));
                if (resultSet.getInt("completed") != 0 && !isBlank(resultSet.getString("completed_date"))) {
                    payload.put("completed_at", toUtcDateTime(resultSet.getString("completed_date"), null));
                } else {
                    payload.putNull("completed_at");
                }
                if (resultSet.getInt("archived") != 0) {
                    payload.put("archived_at", safeText(resultSet.getString("updated_at")));
                } else {
                    payload.putNull("archived_at");
                }
                putNullable(payload, "created_at", null);
                putNullable(payload, "updated_at", resultSet.getString("updated_at"));
                payload.set("tags", splitCsv(resultSet.getString("tags")));
                payload.set("recurrence_rule", recurrenceRuleNode(resultSet.getString("recurrence")));
                return payload;
            }
        }
    }

    private JsonNode buildTaskDependencyPayload(Connection connection, String entityId) throws SQLException {
        TaskDependencyKey key = loadTaskDependencyByEntityId(connection, entityId);
        if (key == null) {
            return null;
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("dependent_task_id", key.dependentTaskId());
        payload.put("blocker_task_id", key.blockerTaskId());
        String sql = """
            SELECT created_at
            FROM task_dependencies
            WHERE dependent_task_id = ?
              AND blocker_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.dependentTaskId());
            statement.setString(2, key.blockerTaskId());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    putNullable(payload, "created_at", resultSet.getString("created_at"));
                }
            }
        }
        return payload;
    }

    private JsonNode buildTimeSessionPayload(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT id, task_id, started_at, minutes
            FROM time_sessions
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", resultSet.getString("id"));
                payload.put("task_id", resultSet.getString("task_id"));
                LocalDateTime startedAt = LocalDateTime.parse(resultSet.getString("started_at"));
                payload.put("started_at", startedAt.atOffset(ZoneOffset.UTC).toInstant().toString());
                payload.put(
                    "ended_at",
                    startedAt.plusMinutes(resultSet.getLong("minutes")).atOffset(ZoneOffset.UTC).toInstant().toString()
                );
                return payload;
            }
        }
    }

    private JsonNode buildTaskTemplatePayload(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT id, name, title, description, complexity, days_until_deadline, tags, updated_at
            FROM task_templates
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", resultSet.getString("id"));
                payload.put("name", resultSet.getString("name"));
                payload.put("title_template", resultSet.getString("title"));
                putNullable(payload, "description_template", resultSet.getString("description"));
                payload.put("default_complexity", resultSet.getInt("complexity"));
                payload.put("default_deadline_offset_days", resultSet.getInt("days_until_deadline"));
                putNullable(payload, "created_at", null);
                putNullable(payload, "updated_at", resultSet.getString("updated_at"));
                payload.set("tags", splitCsv(resultSet.getString("tags")));
                payload.putNull("recurrence_rule");
                return payload;
            }
        }
    }

    private JsonNode buildGoalPayload(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT id, title, period, target, created_at, updated_at
            FROM goals
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                GoalWindow window = goalWindow(resultSet.getString("period"), resultSet.getString("updated_at"));
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", resultSet.getString("id"));
                payload.put("period_type_code", window.periodTypeCode());
                payload.put("title", resultSet.getString("title"));
                payload.put("period_start", window.periodStart().toString());
                payload.put("period_end", window.periodEnd().toString());
                payload.put("target_value", resultSet.getInt("target"));
                payload.putNull("archived_at");
                putNullable(payload, "created_at", resultSet.getString("created_at"));
                putNullable(payload, "updated_at", resultSet.getString("updated_at"));
                return payload;
            }
        }
    }

    private JsonNode buildMoodEntryPayload(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT id, timestamp, score, note, analysis
            FROM mood_entries
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ObjectNode payload = mapper.createObjectNode();
                payload.put("id", resultSet.getString("id"));
                payload.put("recorded_at", normalizeUtcTimestamp(resultSet.getString("timestamp")));
                payload.put("score", resultSet.getInt("score"));
                putNullable(payload, "note", resultSet.getString("note"));
                putNullable(payload, "analysis_label", resultSet.getString("analysis"));
                payload.put("analysis_text", "");
                return payload;
            }
        }
    }

    private TaskDependencyKey loadTaskDependencyByEntityId(Connection connection, String entityId) throws SQLException {
        String sql = """
            SELECT dependent_task_id, blocker_task_id
            FROM task_dependencies
            ORDER BY dependent_task_id, blocker_task_id
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String dependentTaskId = resultSet.getString("dependent_task_id");
                String blockerTaskId = resultSet.getString("blocker_task_id");
                String derivedEntityId = databaseManager.deriveTaskDependencyEntityId(dependentTaskId, blockerTaskId);
                if (entityId.equalsIgnoreCase(derivedEntityId)) {
                    return new TaskDependencyKey(dependentTaskId, blockerTaskId);
                }
            }
        }
        return null;
    }

    private ArrayNode splitCsv(String raw) {
        ArrayNode tags = mapper.createArrayNode();
        if (isBlank(raw)) {
            return tags;
        }
        List<String> normalized = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = safeText(part);
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        normalized.stream().distinct().sorted(Comparator.naturalOrder()).forEach(tags::add);
        return tags;
    }

    private JsonNode recurrenceRuleNode(String recurrence) {
        if (isBlank(recurrence)) {
            return null;
        }
        ObjectNode payload = mapper.createObjectNode();
        payload.put("frequency_code", recurrence.trim().toUpperCase(Locale.ROOT));
        payload.put("interval_value", 1);
        payload.putNull("by_weekday");
        payload.putNull("day_of_month");
        payload.putNull("end_date");
        payload.putNull("occurrence_limit");
        return payload;
    }

    private GoalWindow goalWindow(String period, String anchorTimestamp) {
        LocalDate anchorDate = parseAnchorDate(anchorTimestamp);
        String normalized = safeText(period).toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MONTHLY", "MONTH" -> new GoalWindow(
                "MONTH",
                anchorDate.withDayOfMonth(1),
                anchorDate.with(TemporalAdjusters.lastDayOfMonth())
            );
            case "YEARLY", "YEAR" -> new GoalWindow(
                "YEAR",
                anchorDate.withDayOfYear(1),
                anchorDate.with(TemporalAdjusters.lastDayOfYear())
            );
            case "QUARTERLY", "QUARTER" -> {
                int quarterStartMonth = ((anchorDate.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate quarterStart = LocalDate.of(anchorDate.getYear(), quarterStartMonth, 1);
                yield new GoalWindow(
                    "QUARTER",
                    quarterStart,
                    quarterStart.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth())
                );
            }
            default -> new GoalWindow(
                "WEEK",
                anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                anchorDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
        };
    }

    private LocalDate parseAnchorDate(String raw) {
        if (isBlank(raw)) {
            return LocalDate.now(ZoneOffset.UTC);
        }
        try {
            return OffsetDateTime.parse(raw).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw).toLocalDate();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception ignored) {
            return LocalDate.now(ZoneOffset.UTC);
        }
    }

    private String normalizeUtcTimestamp(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw).toInstant().toString();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(raw).atOffset(ZoneOffset.UTC).toInstant().toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String normalizeLocalTime(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        try {
            return LocalTime.parse(raw).toString();
        } catch (Exception ignored) {
            return raw;
        }
    }

    private String toUtcDateTime(String dateValue, String timeValue) {
        LocalDate date = LocalDate.parse(dateValue);
        LocalTime time = isBlank(timeValue) ? LocalTime.MIDNIGHT : LocalTime.parse(timeValue);
        return LocalDateTime.of(date, time).atOffset(ZoneOffset.UTC).toInstant().toString();
    }

    private JsonNode parseJson(String rawJson) {
        if (isBlank(rawJson)) {
            return null;
        }
        try {
            return mapper.readTree(rawJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void putNullable(ObjectNode node, String fieldName, String value) {
        if (isBlank(value)) {
            node.putNull(fieldName);
        } else {
            node.put(fieldName, value);
        }
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return "";
        }
        return safeText(node.get(fieldName).asText());
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record TaskDependencyKey(String dependentTaskId, String blockerTaskId) {
    }

    private record GoalWindow(String periodTypeCode, LocalDate periodStart, LocalDate periodEnd) {
    }
}
