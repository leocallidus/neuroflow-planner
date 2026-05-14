package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.model.Goal;
import com.example.neuroflowplanner.model.MoodEntry;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.TimeSession;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Wave1SyncApplyService {
    private final DatabaseManager databaseManager;
    private final Wave1SyncPayloadMapper payloadMapper;

    Wave1SyncApplyService() {
        this(DatabaseManager.getInstance(), new Wave1SyncPayloadMapper());
    }

    Wave1SyncApplyService(DatabaseManager databaseManager, Wave1SyncPayloadMapper payloadMapper) {
        this.databaseManager = databaseManager;
        this.payloadMapper = payloadMapper;
    }

    int applyRemoteChanges(List<SyncPayloads.ServerSyncChange> changes, boolean skipInitialApply) {
        if (changes == null || changes.isEmpty()) {
            return 0;
        }
        if (skipInitialApply) {
            return 0;
        }
        List<SyncPayloads.ServerSyncChange> ordered = new ArrayList<>(changes);
        ordered.sort(Comparator.comparingLong(SyncPayloads.ServerSyncChange::change_id));
        int appliedCount = 0;
        for (SyncPayloads.ServerSyncChange change : ordered) {
            if (change == null) {
                continue;
            }
            applySingleChange(change);
            appliedCount++;
        }
        return appliedCount;
    }

    void acknowledgeAcceptedChange(LocalSyncOutboxEntry entry, SyncPayloads.PushAcceptedChange accepted) {
        if (entry == null || accepted == null) {
            return;
        }
        String syncedAt = Instant.now().toString();
        switch (accepted.entity_type()) {
            case TASK_DEPENDENCY -> {
                Wave1SyncPayloadMapper.TaskDependencyKey key = payloadMapper.resolveTaskDependency(entry);
                if (key != null) {
                    databaseManager.markTaskDependencyAccepted(
                        key.dependentTaskId(),
                        key.blockerTaskId(),
                        accepted.operation().name(),
                        accepted.server_change_id(),
                        syncedAt
                    );
                }
            }
            case GOAL_PROGRESS_ENTRY -> {
                // Outbox-only entity: acknowledgement is deleting the receipt row from sync_outbox.
            }
            default -> databaseManager.markSingleIdEntityAccepted(
                accepted.entity_type().name(),
                accepted.entity_id(),
                accepted.operation().name(),
                accepted.server_change_id(),
                syncedAt
            );
        }
    }

    private void applySingleChange(SyncPayloads.ServerSyncChange change) {
        switch (change.entity_type()) {
            case TASK -> applyTask(change);
            case TASK_DEPENDENCY -> applyTaskDependency(change);
            case TIME_SESSION -> applyTimeSession(change);
            case TASK_TEMPLATE -> applyTaskTemplate(change);
            case GOAL -> applyGoal(change);
            case GOAL_PROGRESS_ENTRY -> applyGoalProgressEntry(change);
            case MOOD_ENTRY -> applyMoodEntry(change);
        }
    }

    private void applyTask(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.markSingleIdEntityAccepted("TASK", change.entity_id(), "DELETE", change.change_id(), change.committed_at());
            return;
        }
        JsonNode payload = change.payload();
        Task task = new Task(
            text(payload, "id", change.entity_id()),
            text(payload, "title", ""),
            blankToNull(nullableText(payload, "description")),
            parseDate(payload.get("deadline_date")),
            intValue(payload, "complexity", 0),
            blankToNull(nullableText(payload, "parent_task_id")),
            joinCsv(payload.get("tags")),
            recurrenceCode(payload.get("recurrence_rule"))
        );
        if (payload.hasNonNull("smart_priority")) {
            task.setSmartPriority(payload.get("smart_priority").asDouble());
        }
        task.setAiInsight(nullableText(payload, "ai_insight"));
        task.setStartDate(parseDate(payload.get("start_date")));
        task.setStartTime(parseTime(payload.get("start_time")));
        task.setDeadlineTime(parseTime(payload.get("deadline_time")));
        String completedAt = nullableText(payload, "completed_at");
        if (!completedAt.isBlank()) {
            task.setCompleted(true);
            task.setCompletedDate(parseDateFromDateTime(completedAt));
        } else {
            task.setCompleted(false);
            task.setCompletedDate(null);
        }
        task.setArchived(!nullableText(payload, "archived_at").isBlank());
        databaseManager.applySyncedTask(task, change.committed_at(), change.change_id());
    }

    private void applyTaskDependency(SyncPayloads.ServerSyncChange change) {
        JsonNode payload = change.payload();
        String dependentTaskId = text(payload, "dependent_task_id", "");
        String blockerTaskId = text(payload, "blocker_task_id", "");
        if (dependentTaskId.isBlank() || blockerTaskId.isBlank()) {
            return;
        }
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.deleteSyncedTaskDependency(dependentTaskId, blockerTaskId);
            return;
        }
        databaseManager.applySyncedTaskDependency(
            dependentTaskId,
            blockerTaskId,
            change.committed_at(),
            change.change_id()
        );
    }

    private void applyTimeSession(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.markSingleIdEntityAccepted(
                "TIME_SESSION",
                change.entity_id(),
                "DELETE",
                change.change_id(),
                change.committed_at()
            );
            return;
        }
        JsonNode payload = change.payload();
        LocalDateTime startedAt = parseDateTime(payload.get("started_at"));
        LocalDateTime endedAt = parseDateTime(payload.get("ended_at"));
        long minutes = Math.max(0L, java.time.Duration.between(startedAt, endedAt).toMinutes());
        databaseManager.applySyncedTimeSession(
            new TimeSession(
                text(payload, "id", change.entity_id()),
                text(payload, "task_id", ""),
                startedAt,
                minutes
            ),
            change.committed_at(),
            change.change_id()
        );
    }

    private void applyTaskTemplate(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.markSingleIdEntityAccepted(
                "TASK_TEMPLATE",
                change.entity_id(),
                "DELETE",
                change.change_id(),
                change.committed_at()
            );
            return;
        }
        JsonNode payload = change.payload();
        databaseManager.applySyncedTaskTemplate(
            new TaskTemplate(
                text(payload, "id", change.entity_id()),
                text(payload, "name", ""),
                text(payload, "title_template", ""),
                nullableText(payload, "description_template"),
                intValue(payload, "default_complexity", 0),
                intValue(payload, "default_deadline_offset_days", 0),
                joinCsv(payload.get("tags"))
            ),
            change.committed_at(),
            change.change_id()
        );
    }

    private void applyGoal(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.markSingleIdEntityAccepted("GOAL", change.entity_id(), "DELETE", change.change_id(), change.committed_at());
            return;
        }
        JsonNode payload = change.payload();
        String createdAt = nullableText(payload, "created_at");
        String updatedAt = nullableText(payload, "updated_at");
        if (createdAt.isBlank()) {
            createdAt = change.committed_at();
        }
        if (updatedAt.isBlank()) {
            updatedAt = change.committed_at();
        }
        databaseManager.applySyncedGoal(
            new Goal(
                text(payload, "id", change.entity_id()),
                text(payload, "title", ""),
                localGoalPeriod(payload.get("period_type_code")),
                intValue(payload, "target_value", 0),
                0,
                createdAt,
                updatedAt
            ),
            change.committed_at(),
            change.change_id()
        );
    }

    private void applyGoalProgressEntry(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            return;
        }
        JsonNode payload = change.payload();
        String goalId = text(payload, "goal_id", "");
        if (goalId.isBlank()) {
            return;
        }
        databaseManager.applyGoalProgressDelta(
            goalId,
            intValue(payload, "value_delta", 0),
            change.committed_at(),
            change.change_id()
        );
    }

    private void applyMoodEntry(SyncPayloads.ServerSyncChange change) {
        if (change.operation() == SyncPayloads.SyncOperationCode.DELETE) {
            databaseManager.markSingleIdEntityAccepted("MOOD_ENTRY", change.entity_id(), "DELETE", change.change_id(), change.committed_at());
            return;
        }
        JsonNode payload = change.payload();
        databaseManager.applySyncedMoodEntry(
            new MoodEntry(
                text(payload, "id", change.entity_id()),
                parseDateTime(payload.get("recorded_at")),
                intValue(payload, "score", 1),
                nullableText(payload, "note"),
                analysisValue(payload)
            ),
            change.committed_at(),
            change.change_id()
        );
    }

    private String text(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return defaultValue;
        }
        return node.get(fieldName).asText(defaultValue);
    }

    private String nullableText(JsonNode node, String fieldName) {
        return text(node, fieldName, "").trim();
    }

    private int intValue(JsonNode node, String fieldName, int defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        return node.get(fieldName).asInt(defaultValue);
    }

    private String recurrenceCode(JsonNode recurrenceRule) {
        if (recurrenceRule == null || recurrenceRule.isNull() || !recurrenceRule.hasNonNull("frequency_code")) {
            return "";
        }
        return recurrenceRule.get("frequency_code").asText("").trim().toLowerCase(Locale.ROOT);
    }

    private String localGoalPeriod(JsonNode periodTypeCode) {
        String code = periodTypeCode == null || periodTypeCode.isNull()
            ? "weekly"
            : periodTypeCode.asText("weekly").trim().toLowerCase(Locale.ROOT);
        return switch (code) {
            case "month", "monthly", "quarter", "quarterly", "year", "yearly" -> "monthly";
            case "week", "weekly" -> "weekly";
            default -> "weekly";
        };
    }

    private String joinCsv(JsonNode arrayNode) {
        if (arrayNode == null || arrayNode.isNull() || !arrayNode.isArray()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            if (item != null && !item.isNull()) {
                String text = item.asText("").trim();
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        }
        return String.join(", ", values);
    }

    private String analysisValue(JsonNode payload) {
        String label = nullableText(payload, "analysis_label");
        if (!label.isBlank()) {
            return label;
        }
        return nullableText(payload, "analysis_text");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDate parseDate(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return LocalDate.now(ZoneOffset.UTC);
        }
        return LocalDate.parse(node.asText());
    }

    private LocalDate parseDateFromDateTime(String rawValue) {
        return parseDateTimeNode(rawValue).toLocalDate();
    }

    private LocalTime parseTime(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return LocalTime.parse(node.asText());
    }

    private LocalDateTime parseDateTime(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
        return parseDateTimeNode(node.asText());
    }

    private LocalDateTime parseDateTimeNode(String rawValue) {
        try {
            return OffsetDateTime.parse(rawValue).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(rawValue);
        } catch (Exception ignored) {
            return LocalDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
        }
    }
}
