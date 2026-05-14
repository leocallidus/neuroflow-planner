package com.example.neuroflowplanner.db;

import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.model.ChatMessage;
import com.example.neuroflowplanner.model.Goal;
import com.example.neuroflowplanner.model.ImageJobRecord;
import com.example.neuroflowplanner.model.LocalAccountLink;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.model.MoodEntry;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.DbWriteConfigDefaults;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DatabaseManager.class);
    private static volatile String DB_URL = resolveDbUrl();
    private static DatabaseManager instance;
    private static final String SYNC_STATUS_LOCAL_ONLY = "LOCAL_ONLY";
    private static final String SYNC_STATUS_SYNCED = "SYNCED";
    private static final String SYNC_STATUS_PENDING_UPLOAD = "PENDING_UPLOAD";
    private static final String SYNC_STATUS_PENDING_DELETE = "PENDING_DELETE";
    private static final String SYNC_OUTBOX_STATUS_PENDING = "PENDING";
    private static final String SYNC_OUTBOX_STATUS_IN_FLIGHT = "IN_FLIGHT";
    private static final String SYNC_OUTBOX_STATUS_FAILED = "FAILED";
    private static final UUID TASK_DEPENDENCY_NAMESPACE = UUID.fromString("f9c38594-0626-4a85-a33f-59ddafb411e3");
    private static final Set<String> SUPPORTED_SYNC_ENTITY_TYPES = Set.of(
        "TASK",
        "TASK_DEPENDENCY",
        "TIME_SESSION",
        "TASK_TEMPLATE",
        "GOAL",
        "GOAL_PROGRESS_ENTRY",
        "MOOD_ENTRY"
    );
    private static final Pattern GOAL_ID_JSON_PATTERN =
        Pattern.compile("\"goal_id\"\\s*:\\s*\"([^\"]+)\"");
    private static final String UPSERT_TASK_SQL = """
        INSERT INTO tasks (id, title, description, deadline, complexity, smart_priority, ai_insight, parent_id, tags, recurrence, archived, tracked_minutes, start_date, start_time, completed, completed_date, deadline_time)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            title = excluded.title,
            description = excluded.description,
            deadline = excluded.deadline,
            complexity = excluded.complexity,
            smart_priority = excluded.smart_priority,
            ai_insight = excluded.ai_insight,
            parent_id = excluded.parent_id,
            tags = excluded.tags,
            recurrence = excluded.recurrence,
            archived = excluded.archived,
            tracked_minutes = excluded.tracked_minutes,
            start_date = excluded.start_date,
            start_time = excluded.start_time,
            completed = excluded.completed,
            completed_date = excluded.completed_date,
            deadline_time = excluded.deadline_time
    """;
    private static final String UPSERT_TASK_TEMPLATE_SQL = """
        INSERT INTO task_templates (id, name, title, description, complexity, days_until_deadline, tags)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            name = excluded.name,
            title = excluded.title,
            description = excluded.description,
            complexity = excluded.complexity,
            days_until_deadline = excluded.days_until_deadline,
            tags = excluded.tags
    """;
    private static final String UPSERT_MOOD_ENTRY_SQL = """
        INSERT INTO mood_entries (id, timestamp, score, note, analysis)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            timestamp = excluded.timestamp,
            score = excluded.score,
            note = excluded.note,
            analysis = excluded.analysis
    """;
    private static final String UPSERT_GOAL_SQL = """
        INSERT INTO goals (id, title, period, target, progress, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            title = excluded.title,
            period = excluded.period,
            target = excluded.target,
            progress = excluded.progress,
            created_at = excluded.created_at,
            updated_at = excluded.updated_at
    """;
    private static final String UPSERT_TIME_SESSION_SQL = """
        INSERT INTO time_sessions (id, task_id, started_at, minutes)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
            task_id = excluded.task_id,
            started_at = excluded.started_at,
            minutes = excluded.minutes
    """;

    private DatabaseManager() {
        verifyDatabaseConnection();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Test-only seam: reset singleton lifecycle and recompute DB URL from current overrides.
     */
    public static synchronized void resetForTesting() {
        DataPathManager.reinitializeForTesting();
        DB_URL = resolveDbUrl();
        instance = null;
        LOG.info("db.manager.reset.for.testing", "dbUrl", DB_URL);
    }

    private static String resolveDbUrl() {
        String resolved = DataPathManager.getDatabaseUrl();
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("Database URL is empty. Check DataPathManager overrides.");
        }
        return resolved;
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
        return connection;
    }

    private void verifyDatabaseConnection() {
        // Schema migration is handled by Flyway before DAL initialization.
        try (Connection connection = openConnection()) {
            // no-op
        } catch (SQLException e) {
            throw fail("db.connection.open.failed", e);
        }
    }

    private DatabaseException fail(String event, SQLException e, Object... keyValues) {
        LOG.error(event, e, withDbUrl(keyValues));
        return new DatabaseException(event, e);
    }

    private Object[] withDbUrl(Object... keyValues) {
        int baseLength = keyValues == null ? 0 : keyValues.length;
        Object[] merged = new Object[baseLength + 2];
        if (baseLength > 0) {
            System.arraycopy(keyValues, 0, merged, 0, baseLength);
        }
        merged[baseLength] = "dbUrl";
        merged[baseLength + 1] = DB_URL;
        return merged;
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String nowUtc() {
        return Instant.now().toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isSupportedSyncEntityType(String entityType) {
        return hasText(entityType) && SUPPORTED_SYNC_ENTITY_TYPES.contains(entityType.trim().toUpperCase(Locale.ROOT));
    }

    private static Integer getNullableInt(ResultSet rs, String columnLabel) throws SQLException {
        int value = rs.getInt(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, String columnLabel) throws SQLException {
        double value = rs.getDouble(columnLabel);
        return rs.wasNull() ? null : value;
    }

    private static void setNullableInt(PreparedStatement ps, int parameterIndex, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(parameterIndex, Types.INTEGER);
        } else {
            ps.setInt(parameterIndex, value);
        }
    }

    private static void setNullableDouble(PreparedStatement ps, int parameterIndex, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(parameterIndex, Types.REAL);
        } else {
            ps.setDouble(parameterIndex, value);
        }
    }

    @FunctionalInterface
    public interface TransactionCallback<T> {
        T execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface TransactionAction {
        void execute(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface BatchedStatementBinder<T> {
        void bind(PreparedStatement statement, T item) throws SQLException;
    }

    public record BatchExecutionStats(int itemCount, int updatedCount, int batchCount, long durationMs) {
        static BatchExecutionStats empty(long durationMs) {
            return new BatchExecutionStats(0, 0, 0, durationMs);
        }
    }

    public record BulkOperationSummary(
        String operation,
        int processedCount,
        int updatedCount,
        int failedCount,
        int batchCount,
        long durationMs
    ) {
        static BulkOperationSummary empty(String operation, long durationMs) {
            return new BulkOperationSummary(operation, 0, 0, 0, 0, durationMs);
        }
    }

    public void runInTransactionAction(String operation, TransactionAction action, Object... keyValues) {
        runInTransaction(operation, connection -> {
            action.execute(connection);
            return null;
        }, keyValues);
    }

    public <T> T runInTransaction(String operation, TransactionCallback<T> callback, Object... keyValues) {
        String normalizedOperation = normalizeOperationName(operation);
        String transactionId = UUID.randomUUID().toString();
        long startedAtNanos = System.nanoTime();
        Object[] txContext = appendKeyValues(keyValues, "operation", normalizedOperation, "transactionId", transactionId);
        LOG.info("db.transaction.started", withDbUrl(txContext));

        try (Connection connection = openConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Throwable transactionFailure = null;
            try {
                T result = callback.execute(connection);
                connection.commit();
                long durationMs = elapsedMillis(startedAtNanos);
                LOG.info("db.transaction.completed", withDbUrl(appendKeyValues(txContext, "durationMs", durationMs)));
                return result;
            } catch (SQLException e) {
                transactionFailure = e;
                rollbackWithLogging(connection, normalizedOperation, transactionId, e);
                throw e;
            } catch (RuntimeException e) {
                transactionFailure = e;
                rollbackWithLogging(connection, normalizedOperation, transactionId, e);
                throw e;
            } finally {
                restoreAutoCommitWithLogging(
                    connection,
                    previousAutoCommit,
                    normalizedOperation,
                    transactionId,
                    transactionFailure
                );
            }
        } catch (SQLException e) {
            long durationMs = elapsedMillis(startedAtNanos);
            throw fail("db.transaction.failed", e, appendKeyValues(txContext, "durationMs", durationMs));
        } catch (RuntimeException e) {
            long durationMs = elapsedMillis(startedAtNanos);
            LOG.error(
                "db.transaction.failed.runtime",
                e,
                withDbUrl(appendKeyValues(txContext, "durationMs", durationMs))
            );
            throw e;
        }
    }

    public <T> BatchExecutionStats executeBatchedStatement(
        Connection connection,
        String operation,
        String sql,
        List<T> items,
        BatchedStatementBinder<T> binder
    ) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("Connection is required for batched execution");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is required for batched execution");
        }
        if (binder == null) {
            throw new IllegalArgumentException("BatchedStatementBinder is required");
        }

        List<T> safeItems = items == null ? List.of() : items;
        String normalizedOperation = normalizeOperationName(operation);
        String operationId = UUID.randomUUID().toString();
        String writeMode = ConfigManager.getDbBulkWritesMode();
        int configuredBatchSize = ConfigManager.getDbBulkBatchSize();
        int effectiveBatchSize = resolveEffectiveBatchSize(writeMode, configuredBatchSize, safeItems.size());
        long startedAtNanos = System.nanoTime();

        LOG.info(
            "db.bulk.started",
            withDbUrl(
                "operation", normalizedOperation,
                "operationId", operationId,
                "itemCount", safeItems.size(),
                "mode", writeMode,
                "batchSize", effectiveBatchSize
            )
        );

        if (safeItems.isEmpty()) {
            long durationMs = elapsedMillis(startedAtNanos);
            LOG.info(
                "db.bulk.completed",
                withDbUrl(
                    "operation", normalizedOperation,
                    "operationId", operationId,
                    "itemCount", 0,
                    "batchCount", 0,
                    "mode", writeMode,
                    "batchSize", effectiveBatchSize,
                    "durationMs", durationMs
                )
            );
            return BatchExecutionStats.empty(durationMs);
        }

        int executedBatchCount = 0;
        int updatedRowCount = 0;
        int queuedInCurrentBatch = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (T item : safeItems) {
                binder.bind(statement, item);
                statement.addBatch();
                queuedInCurrentBatch++;
                if (queuedInCurrentBatch >= effectiveBatchSize) {
                    int[] execution = statement.executeBatch();
                    updatedRowCount += countUpdatedRows(execution, queuedInCurrentBatch);
                    statement.clearBatch();
                    queuedInCurrentBatch = 0;
                    executedBatchCount++;
                }
            }
            if (queuedInCurrentBatch > 0) {
                int[] execution = statement.executeBatch();
                updatedRowCount += countUpdatedRows(execution, queuedInCurrentBatch);
                statement.clearBatch();
                executedBatchCount++;
            }

            long durationMs = elapsedMillis(startedAtNanos);
            LOG.info(
                "db.bulk.completed",
                withDbUrl(
                    "operation", normalizedOperation,
                    "operationId", operationId,
                    "itemCount", safeItems.size(),
                    "updatedCount", updatedRowCount,
                    "batchCount", executedBatchCount,
                    "mode", writeMode,
                    "batchSize", effectiveBatchSize,
                    "durationMs", durationMs
                )
            );
            return new BatchExecutionStats(safeItems.size(), updatedRowCount, executedBatchCount, durationMs);
        } catch (SQLException e) {
            long durationMs = elapsedMillis(startedAtNanos);
            LOG.error(
                "db.bulk.failed",
                e,
                withDbUrl(
                    "operation", normalizedOperation,
                    "operationId", operationId,
                    "itemCount", safeItems.size(),
                    "updatedCount", updatedRowCount,
                    "batchCount", executedBatchCount,
                    "failedAtBatch", executedBatchCount + 1,
                    "mode", writeMode,
                    "batchSize", effectiveBatchSize,
                    "durationMs", durationMs
                )
            );
            throw e;
        }
    }

    private int countUpdatedRows(int[] executionResult, int fallbackCount) {
        if (executionResult == null || executionResult.length == 0) {
            return Math.max(0, fallbackCount);
        }
        int updated = 0;
        for (int result : executionResult) {
            if (result >= 0) {
                updated += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                updated += 1;
            }
        }
        return updated;
    }

    private String normalizeOperationName(String operation) {
        if (operation == null || operation.isBlank()) {
            return "unknown";
        }
        return operation.trim();
    }

    private int resolveEffectiveBatchSize(String mode, int configuredBatchSize, int itemCount) {
        int normalizedConfigured = Math.max(1, configuredBatchSize);
        if (DbWriteConfigDefaults.MODE_LEGACY.equals(mode)) {
            return 1;
        }
        if (DbWriteConfigDefaults.MODE_TRANSACTIONAL.equals(mode)) {
            return Math.max(1, itemCount);
        }
        return normalizedConfigured;
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private Object[] appendKeyValues(Object[] keyValues, Object... additions) {
        int baseLength = keyValues == null ? 0 : keyValues.length;
        int additionsLength = additions == null ? 0 : additions.length;
        Object[] merged = new Object[baseLength + additionsLength];
        if (baseLength > 0) {
            System.arraycopy(keyValues, 0, merged, 0, baseLength);
        }
        if (additionsLength > 0) {
            System.arraycopy(additions, 0, merged, baseLength, additionsLength);
        }
        return merged;
    }

    public BulkOperationSummary saveTasksBatch(List<Task> tasks) {
        String operation = "saveTasksBatch";
        long startedAtNanos = System.nanoTime();
        List<Task> normalizedTasks = normalizeTasks(tasks);
        if (normalizedTasks.isEmpty()) {
            return BulkOperationSummary.empty(operation, elapsedMillis(startedAtNanos));
        }

        BatchExecutionStats stats = runInTransaction(
            operation,
            connection -> {
                BatchExecutionStats batchStats = executeBatchedStatement(
                    connection,
                    operation,
                    UPSERT_TASK_SQL,
                    normalizedTasks,
                    this::bindTaskUpsertStatement
                );
                for (Task task : normalizedTasks) {
                    touchEntityAsLocalOnly(connection, "tasks", task.getId(), true);
                }
                return batchStats;
            },
            "itemCount", normalizedTasks.size()
        );
        return new BulkOperationSummary(
            operation,
            normalizedTasks.size(),
            stats.updatedCount(),
            0,
            stats.batchCount(),
            elapsedMillis(startedAtNanos)
        );
    }

    public BulkOperationSummary archiveTasksBatch(List<String> taskIds, boolean includeSubtasks) {
        String operation = includeSubtasks ? "archiveTasksBatchWithSubtasks" : "archiveTasksBatch";
        long startedAtNanos = System.nanoTime();
        List<String> normalizedTaskIds = normalizeTaskIds(taskIds);
        if (normalizedTaskIds.isEmpty()) {
            return BulkOperationSummary.empty(operation, elapsedMillis(startedAtNanos));
        }

        String sql = includeSubtasks
            ? "UPDATE tasks SET archived = 1 WHERE id = ? OR parent_id = ?"
            : "UPDATE tasks SET archived = 1 WHERE id = ?";
        BatchExecutionStats stats = runInTransaction(
            operation,
            connection -> {
                BatchExecutionStats batchStats = executeBatchedStatement(
                    connection,
                    operation,
                    sql,
                    normalizedTaskIds,
                    (statement, taskId) -> {
                        statement.setString(1, taskId);
                        if (includeSubtasks) {
                            statement.setString(2, taskId);
                        }
                    }
                );
                for (String taskId : normalizedTaskIds) {
                    touchEntityAsLocalOnly(connection, "tasks", taskId, true);
                }
                return batchStats;
            },
            "itemCount", normalizedTaskIds.size(),
            "includeSubtasks", includeSubtasks
        );
        return new BulkOperationSummary(
            operation,
            normalizedTaskIds.size(),
            stats.updatedCount(),
            0,
            stats.batchCount(),
            elapsedMillis(startedAtNanos)
        );
    }

    public BulkOperationSummary deleteTasksBatch(List<String> taskIds) {
        String operation = "deleteTasksBatch";
        long startedAtNanos = System.nanoTime();
        List<String> normalizedTaskIds = normalizeTaskIds(taskIds);
        if (normalizedTaskIds.isEmpty()) {
            return BulkOperationSummary.empty(operation, elapsedMillis(startedAtNanos));
        }

        BatchExecutionStats stats = runInTransaction(
            operation,
            connection -> {
                if (!hasTableColumn(connection, "tasks", "sync_status")) {
                    String sql = "DELETE FROM tasks WHERE id = ? OR parent_id = ?";
                    return executeBatchedStatement(
                        connection,
                        operation,
                        sql,
                        normalizedTaskIds,
                        (statement, taskId) -> {
                            statement.setString(1, taskId);
                            statement.setString(2, taskId);
                        }
                    );
                }
                int updatedCount = 0;
                for (String taskId : normalizedTaskIds) {
                    updatedCount += softDeleteTaskLocally(connection, taskId);
                }
                return new BatchExecutionStats(
                    normalizedTaskIds.size(),
                    updatedCount,
                    normalizedTaskIds.size(),
                    0L
                );
            },
            "itemCount", normalizedTaskIds.size()
        );
        return new BulkOperationSummary(
            operation,
            normalizedTaskIds.size(),
            stats.updatedCount(),
            0,
            stats.batchCount(),
            elapsedMillis(startedAtNanos)
        );
    }

    public BulkOperationSummary updateTaskTagsBatch(Map<String, String> tagsByTaskId) {
        String operation = "updateTaskTagsBatch";
        long startedAtNanos = System.nanoTime();
        List<TaskTagUpdate> normalizedUpdates = normalizeTaskTagUpdates(tagsByTaskId);
        if (normalizedUpdates.isEmpty()) {
            return BulkOperationSummary.empty(operation, elapsedMillis(startedAtNanos));
        }

        String sql = "UPDATE tasks SET tags = ? WHERE id = ?";
        BatchExecutionStats stats = runInTransaction(
            operation,
            connection -> {
                BatchExecutionStats batchStats = executeBatchedStatement(
                    connection,
                    operation,
                    sql,
                    normalizedUpdates,
                    (statement, update) -> {
                        statement.setString(1, update.tags());
                        statement.setString(2, update.taskId());
                    }
                );
                for (TaskTagUpdate update : normalizedUpdates) {
                    touchEntityAsLocalOnly(connection, "tasks", update.taskId(), true);
                }
                return batchStats;
            },
            "itemCount", normalizedUpdates.size()
        );
        return new BulkOperationSummary(
            operation,
            normalizedUpdates.size(),
            stats.updatedCount(),
            0,
            stats.batchCount(),
            elapsedMillis(startedAtNanos)
        );
    }

    public void saveTask(Task task) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_TASK_SQL)) {
            bindTaskUpsertStatement(ps, task);
            ps.executeUpdate();
            touchEntityAsLocalOnly(conn, "tasks", task.getId(), true);
        } catch (SQLException e) {
            throw fail("db.task.save.failed", e, "taskId", task == null ? null : task.getId());
        }
    }

    private void bindTaskUpsertStatement(PreparedStatement ps, Task task) throws SQLException {
        ps.setString(1, task.getId());
        ps.setString(2, task.getTitle());
        ps.setString(3, task.getDescription());
        ps.setString(4, task.getDeadline().toString());
        ps.setInt(5, task.getComplexity());
        ps.setDouble(6, task.getSmartPriority());
        ps.setString(7, task.getAiInsight());
        ps.setString(8, task.getParentId());
        ps.setString(9, task.getTags());
        ps.setString(10, task.getRecurrence());
        ps.setInt(11, task.isArchived() ? 1 : 0);
        ps.setLong(12, task.getTrackedMinutes());
        ps.setString(13, task.getStartDate() != null ? task.getStartDate().toString() : null);
        ps.setString(14, task.getStartTime() != null ? task.getStartTime().toString() : null);
        ps.setInt(15, task.isCompleted() ? 1 : 0);
        ps.setString(16, task.getCompletedDate() != null ? task.getCompletedDate().toString() : null);
        ps.setString(17, task.getDeadlineTime() != null ? task.getDeadlineTime().toString() : null);
    }

    public void deleteTask(String id) {
        try (Connection conn = openConnection()) {
            if (hasTaskColumn(conn, "sync_status")) {
                runInTransactionAction("deleteTaskLocally", connection -> softDeleteTaskLocally(connection, id), "taskId", id);
                return;
            }
            String sql = "DELETE FROM tasks WHERE id = ? OR parent_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw fail("db.task.delete.failed", e, "taskId", id);
        }
    }

    public List<Task> loadAllTasks() {
        List<Task> allTasks = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTaskColumn(conn, "deleted_at")
                ? "SELECT * FROM tasks WHERE deleted_at = ''"
                : "SELECT * FROM tasks";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
            boolean hasDependsOnColumn = resultSetHasColumn(rs, "depends_on");
            boolean hasStartTimeColumn = resultSetHasColumn(rs, "start_time");
            boolean hasDeadlineTimeColumn = resultSetHasColumn(rs, "deadline_time");
            while (rs.next()) {
                String tags = emptyIfNull(rs.getString("tags"));
                String recurrence = emptyIfNull(rs.getString("recurrence"));
                String dependsOn = hasDependsOnColumn ? emptyIfNull(rs.getString("depends_on")) : "";
                int archived = rs.getInt("archived");
                long trackedMinutes = rs.getLong("tracked_minutes");
                String startDateStr = rs.getString("start_date");
                String startTimeStr = hasStartTimeColumn ? rs.getString("start_time") : null;
                int completed = rs.getInt("completed");
                String completedDateStr = rs.getString("completed_date");
                String deadlineTimeStr = hasDeadlineTimeColumn ? rs.getString("deadline_time") : null;

                Task task = new Task(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    LocalDate.parse(rs.getString("deadline")),
                    rs.getInt("complexity"),
                    rs.getString("parent_id"),
                    tags,
                    recurrence
                );
                task.setSmartPriority(rs.getDouble("smart_priority"));
                task.setAiInsight(rs.getString("ai_insight"));
                task.setDependsOn(dependsOn);
                task.setArchived(archived == 1);
                task.setTrackedMinutes(trackedMinutes);
                if (hasText(startDateStr)) {
                    task.setStartDate(LocalDate.parse(startDateStr));
                }
                if (hasText(startTimeStr)) {
                    task.setStartTime(LocalTime.parse(startTimeStr));
                }
                task.setCompleted(completed == 1);
                if (hasText(completedDateStr)) {
                    task.setCompletedDate(LocalDate.parse(completedDateStr));
                }
                if (hasText(deadlineTimeStr)) {
                    task.setDeadlineTime(LocalTime.parse(deadlineTimeStr));
                }
                allTasks.add(task);
            }
            }
        } catch (SQLException e) {
            throw fail("db.tasks.load.failed", e);
        }

        Map<String, Task> taskMap = allTasks.stream().collect(Collectors.toMap(Task::getId, t -> t));
        List<Task> rootTasks = new ArrayList<>();
        for (Task task : allTasks) {
            if (task.getParentId() != null && taskMap.containsKey(task.getParentId())) {
                taskMap.get(task.getParentId()).getSubtasks().add(task);
            } else {
                rootTasks.add(task);
            }
        }
        return rootTasks;
    }

    public void saveDependencies(String taskId, List<String> blockerTaskIds) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }

        List<String> normalizedBlockers = normalizeDependencyIds(taskId, blockerTaskIds);
        runInTransactionAction(
            "saveTaskDependencies",
            connection -> {
                if (!hasTableColumn(connection, "task_dependencies", "sync_status")) {
                    String deleteSql = "DELETE FROM task_dependencies WHERE dependent_task_id = ?";
                    String insertSql = """
                        INSERT OR IGNORE INTO task_dependencies (dependent_task_id, blocker_task_id)
                        SELECT ?, ?
                        WHERE EXISTS (SELECT 1 FROM tasks WHERE id = ?)
                          AND EXISTS (SELECT 1 FROM tasks WHERE id = ?)
                          AND ? <> ?
                    """;
                    try (PreparedStatement deletePs = connection.prepareStatement(deleteSql)) {
                        deletePs.setString(1, taskId);
                        deletePs.executeUpdate();
                    }
                    if (!normalizedBlockers.isEmpty()) {
                        executeBatchedStatement(
                            connection,
                            "saveTaskDependenciesLegacy",
                            insertSql,
                            normalizedBlockers,
                            (statement, blockerTaskId) -> {
                                statement.setString(1, taskId);
                                statement.setString(2, blockerTaskId);
                                statement.setString(3, taskId);
                                statement.setString(4, blockerTaskId);
                                statement.setString(5, taskId);
                                statement.setString(6, blockerTaskId);
                            }
                        );
                    }
                    return;
                }

                Map<String, Boolean> existingRows = new java.util.LinkedHashMap<>();
                String selectSql = """
                    SELECT blocker_task_id, deleted_at
                    FROM task_dependencies
                    WHERE dependent_task_id = ?
                """;
                try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                    statement.setString(1, taskId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            existingRows.put(
                                resultSet.getString("blocker_task_id"),
                                !emptyIfNull(resultSet.getString("deleted_at")).isBlank()
                            );
                        }
                    }
                }

                Set<String> existingActive = existingRows.entrySet().stream()
                    .filter(entry -> !entry.getValue())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> requested = new LinkedHashSet<>(normalizedBlockers);

                for (String blockerTaskId : existingActive) {
                    if (!requested.contains(blockerTaskId)) {
                        softDeleteTaskDependencyLocally(connection, taskId, blockerTaskId);
                    }
                }

                for (String blockerTaskId : requested) {
                    if (existingActive.contains(blockerTaskId)) {
                        continue;
                    }
                    upsertTaskDependencyAsLocalOnly(connection, taskId, blockerTaskId);
                }
            },
            "taskId", taskId,
            "blockerCount", normalizedBlockers.size()
        );
    }

    public List<String> loadDependencies(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return List.of();
        }

        List<String> dependencies = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "task_dependencies", "deleted_at")
                ? """
                    SELECT blocker_task_id
                    FROM task_dependencies
                    WHERE dependent_task_id = ?
                      AND deleted_at = ''
                    ORDER BY blocker_task_id
                """
                : """
                    SELECT blocker_task_id
                    FROM task_dependencies
                    WHERE dependent_task_id = ?
                    ORDER BY blocker_task_id
                """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String blockerId = rs.getString("blocker_task_id");
                    if (hasText(blockerId)) {
                        dependencies.add(blockerId);
                    }
                }
            }
            }
        } catch (SQLException e) {
            throw fail("db.task.dependencies.load.failed", e, "taskId", taskId);
        }
        return dependencies;
    }

    public List<TaskDependencyEdge> loadAllDependencyEdges() {
        List<TaskDependencyEdge> edges = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "task_dependencies", "deleted_at")
                ? """
                    SELECT dependent_task_id, blocker_task_id
                    FROM task_dependencies
                    WHERE deleted_at = ''
                    ORDER BY dependent_task_id, blocker_task_id
                """
                : """
                    SELECT dependent_task_id, blocker_task_id
                    FROM task_dependencies
                    ORDER BY dependent_task_id, blocker_task_id
                """;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String dependentTaskId = rs.getString("dependent_task_id");
                    String blockerTaskId = rs.getString("blocker_task_id");
                    if (hasText(dependentTaskId) && hasText(blockerTaskId)) {
                        edges.add(new TaskDependencyEdge(dependentTaskId, blockerTaskId));
                    }
                }
            }
        } catch (SQLException e) {
            throw fail("db.task.dependencies.load_all.failed", e);
        }
        return edges;
    }

    public void deleteDependenciesForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try (Connection conn = openConnection()) {
            if (hasTableColumn(conn, "task_dependencies", "sync_status")) {
                runInTransactionAction(
                    "deleteDependenciesForTaskLocally",
                    connection -> softDeleteDependenciesForTaskLocally(connection, taskId),
                    "taskId", taskId
                );
                return;
            }
            String sql = "DELETE FROM task_dependencies WHERE dependent_task_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, taskId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw fail("db.task.dependencies.delete.failed", e, "taskId", taskId);
        }
    }

    public void updateLegacyDependsOn(String taskId, String dependsOnCsv) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        String sql = "UPDATE tasks SET depends_on = ? WHERE id = ?";
        try (Connection conn = openConnection()) {
            if (!hasTaskColumn(conn, "depends_on")) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, emptyIfNull(dependsOnCsv));
                ps.setString(2, taskId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw fail("db.task.legacy_depends_on.update.failed", e, "taskId", taskId);
        }
    }

    public String loadLegacyDependsOn(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "";
        }
        String sql = "SELECT depends_on FROM tasks WHERE id = ?";
        try (Connection conn = openConnection()) {
            if (!hasTaskColumn(conn, "depends_on")) {
                return "";
            }
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, taskId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return emptyIfNull(rs.getString("depends_on"));
                    }
                    return "";
                }
            }
        } catch (SQLException e) {
            throw fail("db.task.legacy_depends_on.load.failed", e, "taskId", taskId);
        }
    }

    private int softDeleteTaskLocally(Connection connection, String taskId) throws SQLException {
        if (!hasText(taskId) || !hasTaskColumn(connection, "sync_status")) {
            return 0;
        }
        String now = nowUtc();
        List<String> affectedTaskIds = new ArrayList<>();
        String queryTasks = "SELECT id FROM tasks WHERE id = ? OR parent_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(queryTasks)) {
            statement.setString(1, taskId);
            statement.setString(2, taskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    affectedTaskIds.add(resultSet.getString("id"));
                }
            }
        }
        int updatedCount = 0;
        String deleteTasksSql = """
            UPDATE tasks
            SET deleted_at = ?, updated_at = ?, sync_status = ?, last_modified_by_device = ''
            WHERE id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(deleteTasksSql)) {
            for (String affectedTaskId : affectedTaskIds) {
                statement.setString(1, now);
                statement.setString(2, now);
                statement.setString(3, SYNC_STATUS_LOCAL_ONLY);
                statement.setString(4, affectedTaskId);
                updatedCount += statement.executeUpdate();
            }
        }

        String deleteDependenciesSql = """
            UPDATE task_dependencies
            SET deleted_at = ?, updated_at = ?, sync_status = ?, last_modified_by_device = ''
            WHERE dependent_task_id = ?
               OR blocker_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(deleteDependenciesSql)) {
            for (String affectedTaskId : affectedTaskIds) {
                statement.setString(1, now);
                statement.setString(2, now);
                statement.setString(3, SYNC_STATUS_LOCAL_ONLY);
                statement.setString(4, affectedTaskId);
                statement.setString(5, affectedTaskId);
                statement.executeUpdate();
            }
        }
        return updatedCount;
    }

    private void softDeleteSingleIdEntityLocally(Connection connection, String entityType, String entityId) throws SQLException {
        String tableName = resolveSingleIdEntityTable(entityType);
        if (tableName == null || !hasText(entityId) || !hasTableColumn(connection, tableName, "sync_status")) {
            return;
        }
        String now = nowUtc();
        String sql = """
            UPDATE %s
            SET deleted_at = ?, updated_at = ?, sync_status = ?, last_modified_by_device = ''
            WHERE id = ?
        """.formatted(tableName);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now);
            statement.setString(2, now);
            statement.setString(3, SYNC_STATUS_LOCAL_ONLY);
            statement.setString(4, entityId.trim());
            statement.executeUpdate();
        }
    }

    private void softDeleteDependenciesForTaskLocally(Connection connection, String taskId) throws SQLException {
        if (!hasText(taskId) || !hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        String sql = """
            UPDATE task_dependencies
            SET deleted_at = ?, updated_at = ?, sync_status = ?, last_modified_by_device = ''
            WHERE dependent_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nowUtc());
            statement.setString(2, nowUtc());
            statement.setString(3, SYNC_STATUS_LOCAL_ONLY);
            statement.setString(4, taskId.trim());
            statement.executeUpdate();
        }
    }

    private void softDeleteTaskDependencyLocally(Connection connection, String taskId, String blockerTaskId) throws SQLException {
        if (!hasText(taskId)
            || !hasText(blockerTaskId)
            || !hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        String now = nowUtc();
        String sql = """
            UPDATE task_dependencies
            SET deleted_at = ?, updated_at = ?, sync_status = ?, last_modified_by_device = ''
            WHERE dependent_task_id = ?
              AND blocker_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now);
            statement.setString(2, now);
            statement.setString(3, SYNC_STATUS_LOCAL_ONLY);
            statement.setString(4, taskId.trim());
            statement.setString(5, blockerTaskId.trim());
            statement.executeUpdate();
        }
    }

    private void upsertTaskDependencyAsLocalOnly(Connection connection, String taskId, String blockerTaskId) throws SQLException {
        if (!hasText(taskId)
            || !hasText(blockerTaskId)
            || !hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        String now = nowUtc();
        String sql = """
            INSERT INTO task_dependencies (
                dependent_task_id,
                blocker_task_id,
                created_at,
                updated_at,
                deleted_at,
                sync_status,
                last_synced_at,
                server_version,
                last_modified_by_device
            )
            SELECT ?, ?, ?, ?, '', ?, '', 0, ''
            WHERE EXISTS (SELECT 1 FROM tasks WHERE id = ?)
              AND EXISTS (SELECT 1 FROM tasks WHERE id = ?)
              AND ? <> ?
            ON CONFLICT(dependent_task_id, blocker_task_id) DO UPDATE SET
                updated_at = excluded.updated_at,
                deleted_at = '',
                sync_status = excluded.sync_status,
                last_modified_by_device = ''
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, taskId.trim());
            statement.setString(2, blockerTaskId.trim());
            statement.setString(3, now);
            statement.setString(4, now);
            statement.setString(5, SYNC_STATUS_LOCAL_ONLY);
            statement.setString(6, taskId.trim());
            statement.setString(7, blockerTaskId.trim());
            statement.setString(8, taskId.trim());
            statement.setString(9, blockerTaskId.trim());
            statement.executeUpdate();
        }
    }

    private int loadGoalProgress(Connection connection, String goalId) throws SQLException {
        if (!hasText(goalId)) {
            return 0;
        }
        String sql = "SELECT progress FROM goals WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, goalId.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("progress") : 0;
            }
        }
    }

    private static List<String> normalizeDependencyIds(String taskId, List<String> blockerTaskIds) {
        if (blockerTaskIds == null || blockerTaskIds.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String rawId : blockerTaskIds) {
            if (rawId == null) {
                continue;
            }
            String normalized = rawId.trim();
            if (normalized.isEmpty() || normalized.equals(taskId)) {
                continue;
            }
            deduplicated.add(normalized);
        }
        return new ArrayList<>(deduplicated);
    }

    private static List<Task> normalizeTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Task> normalized = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            if (task != null) {
                normalized.add(task);
            }
        }
        return normalized;
    }

    private static List<String> normalizeTaskIds(List<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        Set<String> deduplicated = new LinkedHashSet<>();
        for (String rawId : taskIds) {
            if (rawId == null) {
                continue;
            }
            String normalized = rawId.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            deduplicated.add(normalized);
        }
        return new ArrayList<>(deduplicated);
    }

    private static List<TaskTagUpdate> normalizeTaskTagUpdates(Map<String, String> tagsByTaskId) {
        if (tagsByTaskId == null || tagsByTaskId.isEmpty()) {
            return List.of();
        }
        return tagsByTaskId.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().trim().isEmpty())
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new TaskTagUpdate(entry.getKey().trim(), emptyIfNull(entry.getValue())))
            .toList();
    }

    private record TaskTagUpdate(String taskId, String tags) {
    }

    private boolean hasTaskColumn(Connection connection, String columnName) throws SQLException {
        return hasTableColumn(connection, "tasks", columnName);
    }

    private boolean hasTableColumn(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean resultSetHasColumn(ResultSet resultSet, String columnName) throws SQLException {
        int columnCount = resultSet.getMetaData().getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            if (columnName.equalsIgnoreCase(resultSet.getMetaData().getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    private String resolveSingleIdEntityTable(String entityType) {
        if (entityType == null) {
            return null;
        }
        return switch (entityType.trim().toUpperCase(Locale.ROOT)) {
            case "TASK" -> "tasks";
            case "TIME_SESSION" -> "time_sessions";
            case "TASK_TEMPLATE" -> "task_templates";
            case "GOAL" -> "goals";
            case "MOOD_ENTRY" -> "mood_entries";
            default -> null;
        };
    }

    public String deriveTaskDependencyEntityId(String dependentTaskId, String blockerTaskId) {
        if (!hasText(dependentTaskId) || !hasText(blockerTaskId)) {
            return "";
        }
        return uuidV5(TASK_DEPENDENCY_NAMESPACE, dependentTaskId.trim() + ":" + blockerTaskId.trim()).toString();
    }

    private static UUID uuidV5(UUID namespace, String name) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(toBytes(namespace));
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest();
            hash[6] &= 0x0f;
            hash[6] |= 0x50;
            hash[8] &= 0x3f;
            hash[8] |= (byte) 0x80;
            ByteBuffer buffer = ByteBuffer.wrap(hash, 0, 16);
            return new UUID(buffer.getLong(), buffer.getLong());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm is unavailable for UUIDv5 derivation", e);
        }
    }

    private static byte[] toBytes(UUID uuid) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private void touchEntityAsLocalOnly(Connection connection, String tableName, String entityId, boolean updateTimestamp)
        throws SQLException {
        if (!hasText(entityId) || !hasTableColumn(connection, tableName, "sync_status")) {
            return;
        }

        StringBuilder sql = new StringBuilder("UPDATE ")
            .append(tableName)
            .append(" SET deleted_at = '', sync_status = ?, last_modified_by_device = ''");
        if (updateTimestamp) {
            sql.append(", updated_at = ?");
        }
        sql.append(" WHERE id = ?");

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, SYNC_STATUS_LOCAL_ONLY);
            int nextIndex = 2;
            if (updateTimestamp) {
                statement.setString(nextIndex++, nowUtc());
            }
            statement.setString(nextIndex, entityId);
            statement.executeUpdate();
        }
    }

    private void touchDependenciesAsLocalOnly(Connection connection, String dependentTaskId) throws SQLException {
        if (!hasText(dependentTaskId) || !hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        String sql = """
            UPDATE task_dependencies
            SET updated_at = ?, deleted_at = '', sync_status = ?, last_modified_by_device = ''
            WHERE dependent_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, nowUtc());
            statement.setString(2, SYNC_STATUS_LOCAL_ONLY);
            statement.setString(3, dependentTaskId);
            statement.executeUpdate();
        }
    }

    private void markSingleIdEntitySynced(
        Connection connection,
        String entityType,
        String entityId,
        long serverVersion,
        String syncedAt
    ) throws SQLException {
        String tableName = resolveSingleIdEntityTable(entityType);
        if (tableName == null || !hasText(entityId) || !hasTableColumn(connection, tableName, "sync_status")) {
            return;
        }
        String sql = """
            UPDATE %s
            SET deleted_at = '',
                sync_status = ?,
                last_synced_at = ?,
                server_version = ?,
                last_modified_by_device = ''
            WHERE id = ?
        """.formatted(tableName);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_STATUS_SYNCED);
            statement.setString(2, trimToEmpty(syncedAt));
            statement.setLong(3, Math.max(0L, serverVersion));
            statement.setString(4, trimToEmpty(entityId));
            statement.executeUpdate();
        }
    }

    private void markTaskDependencySynced(
        Connection connection,
        String dependentTaskId,
        String blockerTaskId,
        long serverVersion,
        String syncedAt
    ) throws SQLException {
        if (!hasText(dependentTaskId)
            || !hasText(blockerTaskId)
            || !hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        String sql = """
            UPDATE task_dependencies
            SET deleted_at = '',
                sync_status = ?,
                last_synced_at = ?,
                server_version = ?,
                last_modified_by_device = ''
            WHERE dependent_task_id = ?
              AND blocker_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_STATUS_SYNCED);
            statement.setString(2, trimToEmpty(syncedAt));
            statement.setLong(3, Math.max(0L, serverVersion));
            statement.setString(4, dependentTaskId.trim());
            statement.setString(5, blockerTaskId.trim());
            statement.executeUpdate();
        }
    }

    private void upsertSyncOutbox(
        Connection connection,
        String entityType,
        String entityId,
        String operation,
        String payloadJson
    ) throws SQLException {
        String normalizedEntityType = trimToEmpty(entityType).toUpperCase(Locale.ROOT);
        if (!isSupportedSyncEntityType(normalizedEntityType)) {
            return;
        }
        String sql = """
            INSERT INTO sync_outbox (
                id,
                entity_type,
                entity_id,
                operation,
                payload_json,
                status,
                attempt_count,
                error_message,
                last_attempt_at,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, 0, '', '', ?, ?)
            ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                operation = excluded.operation,
                payload_json = excluded.payload_json,
                status = excluded.status,
                attempt_count = 0,
                error_message = '',
                last_attempt_at = '',
                updated_at = excluded.updated_at
        """;
        String now = nowUtc();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, normalizedEntityType);
            statement.setString(3, trimToEmpty(entityId));
            statement.setString(4, trimToEmpty(operation).toUpperCase(Locale.ROOT));
            statement.setString(5, emptyIfNull(payloadJson));
            statement.setString(6, SYNC_OUTBOX_STATUS_PENDING);
            statement.setString(7, now);
            statement.setString(8, now);
            statement.executeUpdate();
        }
    }

    private int markEntityPendingForSync(
        Connection connection,
        String entityType,
        String entityId,
        String syncStatus,
        String payloadJson,
        String lastModifiedByDevice
    ) throws SQLException {
        String tableName = resolveSingleIdEntityTable(entityType);
        if (tableName == null || !hasText(entityId)) {
            return 0;
        }

        boolean deleteOperation = SYNC_STATUS_PENDING_DELETE.equals(syncStatus);
        String sql = """
            UPDATE %s
            SET updated_at = ?, deleted_at = ?, sync_status = ?, last_modified_by_device = ?
            WHERE id = ?
        """.formatted(tableName);
        String now = nowUtc();
        String deletedAt = deleteOperation ? now : "";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, now);
            statement.setString(2, deletedAt);
            statement.setString(3, syncStatus);
            statement.setString(4, trimToEmpty(lastModifiedByDevice));
            statement.setString(5, trimToEmpty(entityId));
            int updatedRows = statement.executeUpdate();
            if (updatedRows > 0) {
                upsertSyncOutbox(
                    connection,
                    entityType,
                    entityId,
                    deleteOperation ? "DELETE" : "UPSERT",
                    payloadJson
                );
            }
            return updatedRows;
        }
    }

    public void markEntityPendingSync(
        String entityType,
        String entityId,
        String payloadJson,
        String lastModifiedByDevice
    ) {
        try {
            runInTransactionAction(
                "markEntityPendingSync",
                connection -> markEntityPendingForSync(
                    connection,
                    entityType,
                    entityId,
                    SYNC_STATUS_PENDING_UPLOAD,
                    payloadJson,
                    lastModifiedByDevice
                ),
                "entityType", entityType,
                "entityId", entityId
            );
        } catch (RuntimeException e) {
            throw e;
        }
    }

    public void markEntityPendingDelete(
        String entityType,
        String entityId,
        String payloadJson,
        String lastModifiedByDevice
    ) {
        runInTransactionAction(
            "markEntityPendingDelete",
            connection -> markEntityPendingForSync(
                connection,
                entityType,
                entityId,
                SYNC_STATUS_PENDING_DELETE,
                payloadJson,
                lastModifiedByDevice
            ),
            "entityType", entityType,
            "entityId", entityId
        );
    }

    public void softDeleteTaskForSync(String taskId, String payloadJson, String lastModifiedByDevice) {
        if (!hasText(taskId)) {
            return;
        }
        runInTransactionAction(
            "softDeleteTaskForSync",
            connection -> {
                String query = "SELECT id FROM tasks WHERE id = ? OR parent_id = ?";
                List<String> affectedTaskIds = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setString(1, taskId);
                    statement.setString(2, taskId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            affectedTaskIds.add(resultSet.getString("id"));
                        }
                    }
                }
                for (String affectedTaskId : affectedTaskIds) {
                    String affectedPayload = taskId.equals(affectedTaskId) ? payloadJson : "";
                    markEntityPendingForSync(
                        connection,
                        "TASK",
                        affectedTaskId,
                        SYNC_STATUS_PENDING_DELETE,
                        affectedPayload,
                        lastModifiedByDevice
                    );
                }
            },
            "taskId", taskId
        );
    }

    public void saveSyncState(String key, String value) {
        if (!hasText(key)) {
            return;
        }
        String sql = """
            INSERT INTO sync_state (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at
        """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            statement.setString(2, emptyIfNull(value));
            statement.setString(3, nowUtc());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.syncState.save.failed", e, "key", key);
        }
    }

    public String loadSyncState(String key) {
        if (!hasText(key)) {
            return "";
        }
        String sql = "SELECT value FROM sync_state WHERE key = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? emptyIfNull(resultSet.getString("value")) : "";
            }
        } catch (SQLException e) {
            throw fail("db.syncState.load.failed", e, "key", key);
        }
    }

    public void deleteSyncState(String key) {
        if (!hasText(key)) {
            return;
        }
        String sql = "DELETE FROM sync_state WHERE key = ?";
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.syncState.delete.failed", e, "key", key);
        }
    }

    private String loadSyncState(Connection connection, String key) throws SQLException {
        if (!hasText(key)) {
            return "";
        }
        String sql = "SELECT value FROM sync_state WHERE key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? emptyIfNull(resultSet.getString("value")) : "";
            }
        }
    }

    private void saveSyncState(Connection connection, String key, String value) throws SQLException {
        if (!hasText(key)) {
            return;
        }
        String sql = """
            INSERT INTO sync_state (key, value, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
                value = excluded.value,
                updated_at = excluded.updated_at
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            statement.setString(2, emptyIfNull(value));
            statement.setString(3, nowUtc());
            statement.executeUpdate();
        }
    }

    private void deleteSyncState(Connection connection, String key) throws SQLException {
        if (!hasText(key)) {
            return;
        }
        String sql = "DELETE FROM sync_state WHERE key = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, key.trim());
            statement.executeUpdate();
        }
    }

    public void saveAccountLink(LocalAccountLink accountLink) {
        if (accountLink == null) {
            return;
        }
        String sql = """
            INSERT INTO account_link (id, user_id, email, display_name, status, linked_at, last_authenticated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                user_id = excluded.user_id,
                email = excluded.email,
                display_name = excluded.display_name,
                status = excluded.status,
                linked_at = excluded.linked_at,
                last_authenticated_at = excluded.last_authenticated_at
        """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trimToEmpty(accountLink.userId()));
            statement.setString(2, trimToEmpty(accountLink.email()));
            statement.setString(3, trimToEmpty(accountLink.displayName()));
            statement.setString(4, trimToEmpty(accountLink.status()));
            statement.setString(5, trimToEmpty(accountLink.linkedAt()));
            statement.setString(6, trimToEmpty(accountLink.lastAuthenticatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.accountLink.save.failed", e);
        }
    }

    public LocalAccountLink loadAccountLink() {
        String sql = """
            SELECT user_id, email, display_name, status, linked_at, last_authenticated_at
            FROM account_link
            WHERE id = 1
        """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return new LocalAccountLink(
                emptyIfNull(resultSet.getString("user_id")),
                emptyIfNull(resultSet.getString("email")),
                emptyIfNull(resultSet.getString("display_name")),
                emptyIfNull(resultSet.getString("status")),
                emptyIfNull(resultSet.getString("linked_at")),
                emptyIfNull(resultSet.getString("last_authenticated_at"))
            );
        } catch (SQLException e) {
            throw fail("db.accountLink.load.failed", e);
        }
    }

    public void clearAccountLink() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM account_link WHERE id = 1");
        } catch (SQLException e) {
            throw fail("db.accountLink.clear.failed", e);
        }
    }

    public void saveDeviceIdentity(LocalDeviceIdentity deviceIdentity) {
        if (deviceIdentity == null) {
            return;
        }
        String sql = """
            INSERT INTO device_identity (id, device_id, device_label, platform, app_version, created_at, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                device_id = excluded.device_id,
                device_label = excluded.device_label,
                platform = excluded.platform,
                app_version = excluded.app_version,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at
        """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trimToEmpty(deviceIdentity.deviceId()));
            statement.setString(2, trimToEmpty(deviceIdentity.deviceLabel()));
            statement.setString(3, trimToEmpty(deviceIdentity.platform()));
            statement.setString(4, trimToEmpty(deviceIdentity.appVersion()));
            statement.setString(5, trimToEmpty(deviceIdentity.createdAt()));
            statement.setString(6, trimToEmpty(deviceIdentity.updatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.deviceIdentity.save.failed", e);
        }
    }

    public LocalDeviceIdentity loadDeviceIdentity() {
        String sql = """
            SELECT device_id, device_label, platform, app_version, created_at, updated_at
            FROM device_identity
            WHERE id = 1
        """;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                return null;
            }
            return new LocalDeviceIdentity(
                emptyIfNull(resultSet.getString("device_id")),
                emptyIfNull(resultSet.getString("device_label")),
                emptyIfNull(resultSet.getString("platform")),
                emptyIfNull(resultSet.getString("app_version")),
                emptyIfNull(resultSet.getString("created_at")),
                emptyIfNull(resultSet.getString("updated_at"))
            );
        } catch (SQLException e) {
            throw fail("db.deviceIdentity.load.failed", e);
        }
    }

    public void clearDeviceIdentity() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM device_identity WHERE id = 1");
        } catch (SQLException e) {
            throw fail("db.deviceIdentity.clear.failed", e);
        }
    }

    public void enqueueSyncChange(String entityType, String entityId, String operation, String payloadJson) {
        if (!hasText(entityType) || !hasText(entityId) || !hasText(operation)) {
            return;
        }
        String normalizedEntityType = entityType.trim().toUpperCase(Locale.ROOT);
        if (!isSupportedSyncEntityType(normalizedEntityType)) {
            LOG.info(
                "cloud.sync.outbox.enqueue.skipped",
                "entityType", normalizedEntityType,
                "reason", "unsupported_entity_type"
            );
            return;
        }
        runInTransactionAction(
            "enqueueSyncChange",
            connection -> upsertSyncOutbox(connection, normalizedEntityType, entityId, operation, payloadJson),
            "entityType", normalizedEntityType,
            "entityId", entityId,
            "operation", operation
        );
    }

    public void resetLocalSyncStateForAccountRelink() {
        runInTransactionAction(
            "resetLocalSyncStateForAccountRelink",
            connection -> {
                clearSyncOutbox(connection);
                resetSingleIdEntitySyncMetadata(connection, "tasks");
                resetTaskDependencySyncMetadata(connection);
                resetSingleIdEntitySyncMetadata(connection, "time_sessions");
                resetSingleIdEntitySyncMetadata(connection, "task_templates");
                resetSingleIdEntitySyncMetadata(connection, "goals");
                resetSingleIdEntitySyncMetadata(connection, "mood_entries");
            }
        );
    }

    public List<LocalSyncOutboxEntry> loadPendingSyncOutbox(int limit) {
        int safeLimit = Math.max(1, limit);
        String sql = """
            SELECT id, entity_type, entity_id, operation, payload_json, status,
                   attempt_count, error_message, last_attempt_at, created_at, updated_at
            FROM sync_outbox
            WHERE status IN (?, ?)
            ORDER BY created_at ASC, id ASC
            LIMIT ?
        """;
        List<LocalSyncOutboxEntry> entries = new ArrayList<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_OUTBOX_STATUS_PENDING);
            statement.setString(2, SYNC_OUTBOX_STATUS_FAILED);
            statement.setInt(3, safeLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    entries.add(new LocalSyncOutboxEntry(
                        resultSet.getString("id"),
                        resultSet.getString("entity_type"),
                        resultSet.getString("entity_id"),
                        resultSet.getString("operation"),
                        emptyIfNull(resultSet.getString("payload_json")),
                        resultSet.getString("status"),
                        resultSet.getInt("attempt_count"),
                        emptyIfNull(resultSet.getString("error_message")),
                        emptyIfNull(resultSet.getString("last_attempt_at")),
                        resultSet.getString("created_at"),
                        resultSet.getString("updated_at")
                    ));
                }
            }
            discardUnsupportedSyncEntries(connection, entries);
            discardOrphanGoalProgressEntries(connection, entries);
        } catch (SQLException e) {
            throw fail("db.syncOutbox.load.failed", e, "limit", safeLimit);
        }
        return entries;
    }

    public void markSyncOutboxInFlight(String outboxId) {
        if (!hasText(outboxId)) {
            return;
        }
        String sql = """
            UPDATE sync_outbox
            SET status = ?, attempt_count = attempt_count + 1, last_attempt_at = ?, updated_at = ?
            WHERE id = ?
        """;
        String now = nowUtc();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_OUTBOX_STATUS_IN_FLIGHT);
            statement.setString(2, now);
            statement.setString(3, now);
            statement.setString(4, outboxId.trim());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.syncOutbox.mark_in_flight.failed", e, "outboxId", outboxId);
        }
    }

    public void markSyncOutboxFailed(String outboxId, String errorMessage) {
        if (!hasText(outboxId)) {
            return;
        }
        String sql = """
            UPDATE sync_outbox
            SET status = ?, error_message = ?, updated_at = ?
            WHERE id = ?
        """;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_OUTBOX_STATUS_FAILED);
            statement.setString(2, emptyIfNull(errorMessage));
            statement.setString(3, nowUtc());
            statement.setString(4, outboxId.trim());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.syncOutbox.mark_failed.failed", e, "outboxId", outboxId);
        }
    }

    public void resetInFlightSyncOutboxEntries(String errorMessage) {
        String sql = """
            UPDATE sync_outbox
            SET status = ?, error_message = ?, updated_at = ?
            WHERE status = ?
        """;
        String now = nowUtc();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_OUTBOX_STATUS_FAILED);
            statement.setString(2, emptyIfNull(errorMessage));
            statement.setString(3, now);
            statement.setString(4, SYNC_OUTBOX_STATUS_IN_FLIGHT);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.syncOutbox.reset_in_flight.failed", e);
        }
    }

    public void deleteSyncOutboxEntry(String outboxId) {
        if (!hasText(outboxId)) {
            return;
        }
        try (Connection connection = openConnection()) {
            deleteSyncOutboxEntry(connection, outboxId);
        } catch (SQLException e) {
            throw fail("db.syncOutbox.delete.failed", e, "outboxId", outboxId);
        }
    }

    private void discardOrphanGoalProgressEntries(Connection connection, List<LocalSyncOutboxEntry> entries)
        throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> orphanEntryIds = new LinkedHashSet<>();
        Map<String, Boolean> goalPresenceCache = new HashMap<>();
        for (LocalSyncOutboxEntry entry : entries) {
            if (entry == null || !"GOAL_PROGRESS_ENTRY".equalsIgnoreCase(entry.entityType())) {
                continue;
            }
            String goalId = extractGoalId(entry.payloadJson());
            boolean goalExists = hasText(goalId)
                && goalPresenceCache.computeIfAbsent(goalId, id -> hasActiveGoal(connection, id));
            if (goalExists) {
                continue;
            }
            orphanEntryIds.add(entry.id());
            deleteSyncOutboxEntry(connection, entry.id());
            LOG.info(
                "cloud.sync.outbox.goal_progress.discarded",
                "outboxId", entry.id(),
                "goalId", goalId,
                "reason", "missing_or_deleted_goal"
            );
        }
        if (!orphanEntryIds.isEmpty()) {
            entries.removeIf(entry -> entry != null && orphanEntryIds.contains(entry.id()));
        }
    }

    private void discardUnsupportedSyncEntries(Connection connection, List<LocalSyncOutboxEntry> entries)
        throws SQLException {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Set<String> unsupportedEntryIds = new LinkedHashSet<>();
        for (LocalSyncOutboxEntry entry : entries) {
            if (entry == null || isSupportedSyncEntityType(entry.entityType())) {
                continue;
            }
            unsupportedEntryIds.add(entry.id());
            deleteSyncOutboxEntry(connection, entry.id());
            LOG.info(
                "cloud.sync.outbox.legacy_discarded",
                "outboxId", entry.id(),
                "entityType", entry.entityType(),
                "reason", "unsupported_entity_type"
            );
        }
        if (!unsupportedEntryIds.isEmpty()) {
            entries.removeIf(entry -> entry != null && unsupportedEntryIds.contains(entry.id()));
        }
    }

    private boolean hasActiveGoal(Connection connection, String goalId) {
        if (!hasText(goalId)) {
            return false;
        }
        try {
            String sql = hasTableColumn(connection, "goals", "deleted_at")
                ? "SELECT 1 FROM goals WHERE id = ? AND deleted_at = ''"
                : "SELECT 1 FROM goals WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, goalId.trim());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            }
        } catch (SQLException e) {
            throw fail("db.goals.exists_for_sync.failed", e, "goalId", goalId);
        }
    }

    private String extractGoalId(String payloadJson) {
        if (!hasText(payloadJson)) {
            return "";
        }
        Matcher matcher = GOAL_ID_JSON_PATTERN.matcher(payloadJson);
        return matcher.find() ? emptyIfNull(matcher.group(1)).trim() : "";
    }

    private void deleteSyncOutboxEntry(Connection connection, String outboxId) throws SQLException {
        if (!hasText(outboxId)) {
            return;
        }
        String sql = "DELETE FROM sync_outbox WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, outboxId.trim());
            statement.executeUpdate();
        }
    }

    private void deleteSyncOutboxEntry(Connection connection, String entityType, String entityId) throws SQLException {
        if (!hasText(entityType) || !hasText(entityId)) {
            return;
        }
        String sql = "DELETE FROM sync_outbox WHERE entity_type = ? AND entity_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityType.trim().toUpperCase(Locale.ROOT));
            statement.setString(2, entityId.trim());
            statement.executeUpdate();
        }
    }

    private void clearSyncOutbox(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM sync_outbox");
        }
    }

    private void resetSingleIdEntitySyncMetadata(Connection connection, String tableName) throws SQLException {
        if (!hasText(tableName) || !hasTableColumn(connection, tableName, "sync_status")) {
            return;
        }
        boolean hasUpdatedAt = hasTableColumn(connection, tableName, "updated_at");
        boolean hasLastModifiedByDevice = hasTableColumn(connection, tableName, "last_modified_by_device");
        boolean hasLastSyncedAt = hasTableColumn(connection, tableName, "last_synced_at");
        boolean hasServerVersion = hasTableColumn(connection, tableName, "server_version");
        StringBuilder sql = new StringBuilder("UPDATE ")
            .append(tableName)
            .append(" SET sync_status = ?");
        if (hasLastSyncedAt) {
            sql.append(", last_synced_at = ''");
        }
        if (hasServerVersion) {
            sql.append(", server_version = 0");
        }
        if (hasLastModifiedByDevice) {
            sql.append(", last_modified_by_device = ''");
        }
        if (hasUpdatedAt) {
            sql.append(", updated_at = CASE WHEN updated_at IS NULL OR updated_at = '' THEN ? ELSE updated_at END");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, SYNC_STATUS_LOCAL_ONLY);
            if (hasUpdatedAt) {
                statement.setString(2, nowUtc());
            }
            statement.executeUpdate();
        }
    }

    private void resetTaskDependencySyncMetadata(Connection connection) throws SQLException {
        if (!hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return;
        }
        boolean hasUpdatedAt = hasTableColumn(connection, "task_dependencies", "updated_at");
        boolean hasLastModifiedByDevice = hasTableColumn(connection, "task_dependencies", "last_modified_by_device");
        boolean hasLastSyncedAt = hasTableColumn(connection, "task_dependencies", "last_synced_at");
        boolean hasServerVersion = hasTableColumn(connection, "task_dependencies", "server_version");
        StringBuilder sql = new StringBuilder("UPDATE task_dependencies SET sync_status = ?");
        if (hasLastSyncedAt) {
            sql.append(", last_synced_at = ''");
        }
        if (hasServerVersion) {
            sql.append(", server_version = 0");
        }
        if (hasLastModifiedByDevice) {
            sql.append(", last_modified_by_device = ''");
        }
        if (hasUpdatedAt) {
            sql.append(", updated_at = CASE WHEN updated_at IS NULL OR updated_at = '' THEN ? ELSE updated_at END");
        }
        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, SYNC_STATUS_LOCAL_ONLY);
            if (hasUpdatedAt) {
                statement.setString(2, nowUtc());
            }
            statement.executeUpdate();
        }
    }

    public int stageLocalOnlyWave1Entities() {
        return runInTransaction("stageLocalOnlyWave1Entities", connection -> {
            int stagedCount = 0;
            stagedCount += stageSingleIdEntities(connection, "TASK", "tasks");
            stagedCount += stageTaskDependencyEntities(connection);
            stagedCount += stageSingleIdEntities(connection, "TIME_SESSION", "time_sessions");
            stagedCount += stageSingleIdEntities(connection, "TASK_TEMPLATE", "task_templates");
            stagedCount += stageSingleIdEntities(connection, "GOAL", "goals");
            stagedCount += stageSingleIdEntities(connection, "MOOD_ENTRY", "mood_entries");
            return stagedCount;
        });
    }

    public void markSingleIdEntityAccepted(
        String entityType,
        String entityId,
        String operation,
        long serverVersion,
        String syncedAt
    ) {
        if (!hasText(entityId)) {
            return;
        }
        runInTransactionAction(
            "markSingleIdEntityAccepted",
            connection -> {
                if ("DELETE".equalsIgnoreCase(operation)) {
                    deleteSingleIdEntity(connection, entityType, entityId);
                    return;
                }
                markSingleIdEntitySynced(connection, entityType, entityId, serverVersion, syncedAt);
            },
            "entityType", entityType,
            "entityId", entityId,
            "operation", operation
        );
    }

    public void markTaskDependencyAccepted(
        String dependentTaskId,
        String blockerTaskId,
        String operation,
        long serverVersion,
        String syncedAt
    ) {
        if (!hasText(dependentTaskId) || !hasText(blockerTaskId)) {
            return;
        }
        runInTransactionAction(
            "markTaskDependencyAccepted",
            connection -> {
                if ("DELETE".equalsIgnoreCase(operation)) {
                    deleteTaskDependency(connection, dependentTaskId, blockerTaskId);
                    return;
                }
                markTaskDependencySynced(connection, dependentTaskId, blockerTaskId, serverVersion, syncedAt);
            },
            "dependentTaskId", dependentTaskId,
            "blockerTaskId", blockerTaskId,
            "operation", operation
        );
    }

    public void applySyncedTask(Task task, String syncedAt, long serverVersion) {
        if (task == null || !hasText(task.getId())) {
            return;
        }
        runInTransactionAction(
            "applySyncedTask",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_TASK_SQL)) {
                    bindTaskUpsertStatement(statement, task);
                    statement.executeUpdate();
                }
                markSingleIdEntitySynced(connection, "TASK", task.getId(), serverVersion, syncedAt);
            },
            "taskId", task.getId(),
            "serverVersion", serverVersion
        );
    }

    public void applySyncedTimeSession(TimeSession session, String syncedAt, long serverVersion) {
        if (session == null || !hasText(session.getId())) {
            return;
        }
        runInTransactionAction(
            "applySyncedTimeSession",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_TIME_SESSION_SQL)) {
                    statement.setString(1, session.getId());
                    statement.setString(2, session.getTaskId());
                    statement.setString(3, session.getStartedAt().toString());
                    statement.setLong(4, session.getMinutes());
                    statement.executeUpdate();
                }
                markSingleIdEntitySynced(connection, "TIME_SESSION", session.getId(), serverVersion, syncedAt);
            },
            "sessionId", session.getId(),
            "serverVersion", serverVersion
        );
    }

    public void applySyncedTaskTemplate(TaskTemplate template, String syncedAt, long serverVersion) {
        if (template == null || !hasText(template.getId())) {
            return;
        }
        runInTransactionAction(
            "applySyncedTaskTemplate",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_TASK_TEMPLATE_SQL)) {
                    statement.setString(1, template.getId());
                    statement.setString(2, template.getName());
                    statement.setString(3, template.getTitle());
                    statement.setString(4, template.getDescription());
                    statement.setInt(5, template.getComplexity());
                    statement.setInt(6, template.getDaysUntilDeadline());
                    statement.setString(7, template.getTags());
                    statement.executeUpdate();
                }
                markSingleIdEntitySynced(connection, "TASK_TEMPLATE", template.getId(), serverVersion, syncedAt);
            },
            "templateId", template.getId(),
            "serverVersion", serverVersion
        );
    }

    public void applySyncedMoodEntry(MoodEntry entry, String syncedAt, long serverVersion) {
        if (entry == null || !hasText(entry.getId())) {
            return;
        }
        runInTransactionAction(
            "applySyncedMoodEntry",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_MOOD_ENTRY_SQL)) {
                    statement.setString(1, entry.getId());
                    statement.setString(2, entry.getTimestamp().toString());
                    statement.setInt(3, entry.getScore());
                    statement.setString(4, entry.getNote());
                    statement.setString(5, entry.getAnalysis());
                    statement.executeUpdate();
                }
                markSingleIdEntitySynced(connection, "MOOD_ENTRY", entry.getId(), serverVersion, syncedAt);
            },
            "moodEntryId", entry.getId(),
            "serverVersion", serverVersion
        );
    }

    public void applySyncedGoal(Goal goal, String syncedAt, long serverVersion) {
        if (goal == null || !hasText(goal.getId())) {
            return;
        }
        runInTransactionAction(
            "applySyncedGoal",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_GOAL_SQL)) {
                    statement.setString(1, goal.getId());
                    statement.setString(2, goal.getTitle());
                    statement.setString(3, goal.getPeriod());
                    statement.setInt(4, goal.getTarget());
                    statement.setInt(5, goal.getProgress());
                    statement.setString(6, goal.getCreatedAt());
                    statement.setString(7, goal.getUpdatedAt());
                    statement.executeUpdate();
                }
                markSingleIdEntitySynced(connection, "GOAL", goal.getId(), serverVersion, syncedAt);
            },
            "goalId", goal.getId(),
            "serverVersion", serverVersion
        );
    }

    public void applyGoalProgressDelta(String goalId, int delta, String syncedAt, long serverVersion) {
        if (!hasText(goalId) || delta == 0) {
            return;
        }
        runInTransactionAction(
            "applyGoalProgressDelta",
            connection -> {
                String sql = """
                    UPDATE goals
                    SET progress = MAX(0, progress + ?),
                        updated_at = ?,
                        deleted_at = '',
                        sync_status = ?,
                        last_synced_at = ?,
                        server_version = ?,
                        last_modified_by_device = ''
                    WHERE id = ?
                """;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, delta);
                    statement.setString(2, trimToEmpty(syncedAt));
                    statement.setString(3, SYNC_STATUS_SYNCED);
                    statement.setString(4, trimToEmpty(syncedAt));
                    statement.setLong(5, Math.max(0L, serverVersion));
                    statement.setString(6, goalId.trim());
                    statement.executeUpdate();
                }
            },
            "goalId", goalId,
            "delta", delta,
            "serverVersion", serverVersion
        );
    }

    public void applySyncedTaskDependency(
        String dependentTaskId,
        String blockerTaskId,
        String syncedAt,
        long serverVersion
    ) {
        if (!hasText(dependentTaskId) || !hasText(blockerTaskId)) {
            return;
        }
        runInTransactionAction(
            "applySyncedTaskDependency",
            connection -> {
                String now = trimToEmpty(syncedAt).isEmpty() ? nowUtc() : trimToEmpty(syncedAt);
                String sql;
                if (hasTableColumn(connection, "task_dependencies", "sync_status")) {
                    sql = """
                        INSERT INTO task_dependencies (
                            dependent_task_id,
                            blocker_task_id,
                            created_at,
                            updated_at,
                            deleted_at,
                            sync_status,
                            last_synced_at,
                            server_version,
                            last_modified_by_device
                        ) VALUES (?, ?, ?, ?, '', ?, ?, ?, '')
                        ON CONFLICT(dependent_task_id, blocker_task_id) DO UPDATE SET
                            updated_at = excluded.updated_at,
                            deleted_at = '',
                            sync_status = excluded.sync_status,
                            last_synced_at = excluded.last_synced_at,
                            server_version = excluded.server_version,
                            last_modified_by_device = ''
                    """;
                } else {
                    sql = """
                        INSERT OR IGNORE INTO task_dependencies (dependent_task_id, blocker_task_id, created_at)
                        VALUES (?, ?, ?)
                    """;
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, dependentTaskId.trim());
                    statement.setString(2, blockerTaskId.trim());
                    statement.setString(3, now);
                    if (hasTableColumn(connection, "task_dependencies", "sync_status")) {
                        statement.setString(4, now);
                        statement.setString(5, SYNC_STATUS_SYNCED);
                        statement.setString(6, now);
                        statement.setLong(7, Math.max(0L, serverVersion));
                    }
                    statement.executeUpdate();
                }
                markTaskDependencySynced(connection, dependentTaskId, blockerTaskId, serverVersion, now);
            },
            "dependentTaskId", dependentTaskId,
            "blockerTaskId", blockerTaskId,
            "serverVersion", serverVersion
        );
    }

    public void deleteSyncedTaskDependency(String dependentTaskId, String blockerTaskId) {
        if (!hasText(dependentTaskId) || !hasText(blockerTaskId)) {
            return;
        }
        runInTransactionAction(
            "deleteSyncedTaskDependency",
            connection -> deleteTaskDependency(connection, dependentTaskId, blockerTaskId),
            "dependentTaskId", dependentTaskId,
            "blockerTaskId", blockerTaskId
        );
    }

    private int stageSingleIdEntities(Connection connection, String entityType, String tableName) throws SQLException {
        if (!hasTableColumn(connection, tableName, "sync_status")) {
            return 0;
        }
        String sql = "SELECT id, deleted_at FROM " + tableName + " WHERE sync_status = ?";
        int stagedCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_STATUS_LOCAL_ONLY);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String entityId = resultSet.getString("id");
                    String deletedAt = emptyIfNull(resultSet.getString("deleted_at"));
                    stagedCount += markEntityPendingForSync(
                        connection,
                        entityType,
                        entityId,
                        deletedAt.isBlank() ? SYNC_STATUS_PENDING_UPLOAD : SYNC_STATUS_PENDING_DELETE,
                        "",
                        ""
                    );
                }
            }
        }
        return stagedCount;
    }

    private int stageTaskDependencyEntities(Connection connection) throws SQLException {
        if (!hasTableColumn(connection, "task_dependencies", "sync_status")) {
            return 0;
        }
        String sql = """
            SELECT dependent_task_id, blocker_task_id, deleted_at
            FROM task_dependencies
            WHERE sync_status = ?
        """;
        int stagedCount = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SYNC_STATUS_LOCAL_ONLY);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String dependentTaskId = resultSet.getString("dependent_task_id");
                    String blockerTaskId = resultSet.getString("blocker_task_id");
                    String operation = emptyIfNull(resultSet.getString("deleted_at")).isBlank()
                        ? "UPSERT"
                        : "DELETE";
                    upsertSyncOutbox(
                        connection,
                        "TASK_DEPENDENCY",
                        deriveTaskDependencyEntityId(dependentTaskId, blockerTaskId),
                        operation,
                        ""
                    );
                    String updateSql = """
                        UPDATE task_dependencies
                        SET sync_status = ?, updated_at = ?
                        WHERE dependent_task_id = ?
                          AND blocker_task_id = ?
                    """;
                    try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                        update.setString(1, "DELETE".equals(operation) ? SYNC_STATUS_PENDING_DELETE : SYNC_STATUS_PENDING_UPLOAD);
                        update.setString(2, nowUtc());
                        update.setString(3, dependentTaskId);
                        update.setString(4, blockerTaskId);
                        stagedCount += update.executeUpdate();
                    }
                }
            }
        }
        return stagedCount;
    }

    private void deleteSingleIdEntity(Connection connection, String entityType, String entityId) throws SQLException {
        String tableName = resolveSingleIdEntityTable(entityType);
        if (tableName == null || !hasText(entityId)) {
            return;
        }
        String sql = "DELETE FROM " + tableName + " WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, entityId.trim());
            statement.executeUpdate();
        }
    }

    private void deleteTaskDependency(Connection connection, String dependentTaskId, String blockerTaskId) throws SQLException {
        String sql = """
            DELETE FROM task_dependencies
            WHERE dependent_task_id = ?
              AND blocker_task_id = ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dependentTaskId.trim());
            statement.setString(2, blockerTaskId.trim());
            statement.executeUpdate();
        }
    }

    public void saveTimeSession(String taskId, LocalDateTime startedAt, long minutes) {
        if (taskId == null || startedAt == null || minutes <= 0) {
            return;
        }
        String sql = "INSERT INTO time_sessions (id, task_id, started_at, minutes) VALUES (?, ?, ?, ?)";
        String sessionId = UUID.randomUUID().toString();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, taskId);
            ps.setString(3, startedAt.toString());
            ps.setLong(4, minutes);
            ps.executeUpdate();
            touchEntityAsLocalOnly(conn, "time_sessions", sessionId, true);
        } catch (SQLException e) {
            throw fail("db.timeSession.save.failed", e, "taskId", taskId);
        }
    }

    public List<TimeSession> loadTimeSessions() {
        List<TimeSession> sessions = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "time_sessions", "deleted_at")
                ? "SELECT * FROM time_sessions WHERE deleted_at = ''"
                : "SELECT * FROM time_sessions";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    LocalDateTime startedAt = LocalDateTime.parse(rs.getString("started_at"));
                    sessions.add(new TimeSession(
                        rs.getString("id"),
                        rs.getString("task_id"),
                        startedAt,
                        rs.getLong("minutes")
                    ));
                }
            }
        } catch (SQLException e) {
            throw fail("db.timeSessions.load.failed", e);
        }
        return sessions;
    }

    public void saveTemplate(TaskTemplate template) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_TASK_TEMPLATE_SQL)) {
            ps.setString(1, template.getId());
            ps.setString(2, template.getName());
            ps.setString(3, template.getTitle());
            ps.setString(4, template.getDescription());
            ps.setInt(5, template.getComplexity());
            ps.setInt(6, template.getDaysUntilDeadline());
            ps.setString(7, template.getTags());
            ps.executeUpdate();
            touchEntityAsLocalOnly(conn, "task_templates", template.getId(), true);
        } catch (SQLException e) {
            throw fail("db.template.save.failed", e, "templateId", template.getId());
        }
    }

    public void deleteTemplate(String id) {
        try (Connection conn = openConnection()) {
            if (hasTableColumn(conn, "task_templates", "sync_status")) {
                runInTransactionAction(
                    "deleteTemplateLocally",
                    connection -> softDeleteSingleIdEntityLocally(connection, "TASK_TEMPLATE", id),
                    "templateId", id
                );
                return;
            }
            String sql = "DELETE FROM task_templates WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw fail("db.template.delete.failed", e, "templateId", id);
        }
    }

    public List<TaskTemplate> loadAllTemplates() {
        List<TaskTemplate> templates = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "task_templates", "deleted_at")
                ? "SELECT * FROM task_templates WHERE deleted_at = ''"
                : "SELECT * FROM task_templates";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    templates.add(new TaskTemplate(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("complexity"),
                        rs.getInt("days_until_deadline"),
                        rs.getString("tags")
                    ));
                }
            }
        } catch (SQLException e) {
            throw fail("db.templates.load.failed", e);
        }
        return templates;
    }

    public void saveMoodEntry(MoodEntry entry) {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(UPSERT_MOOD_ENTRY_SQL)) {
            ps.setString(1, entry.getId());
            ps.setString(2, entry.getTimestamp().toString());
            ps.setInt(3, entry.getScore());
            ps.setString(4, entry.getNote());
            ps.setString(5, entry.getAnalysis());
            ps.executeUpdate();
            touchEntityAsLocalOnly(conn, "mood_entries", entry.getId(), true);
        } catch (SQLException e) {
            throw fail("db.moodEntry.save.failed", e, "moodEntryId", entry.getId());
        }
    }

    public List<MoodEntry> loadMoodHistory() {
        List<MoodEntry> list = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "mood_entries", "deleted_at")
                ? "SELECT * FROM mood_entries WHERE deleted_at = '' ORDER BY timestamp DESC"
                : "SELECT * FROM mood_entries ORDER BY timestamp DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    list.add(new MoodEntry(
                        rs.getString("id"),
                        LocalDateTime.parse(rs.getString("timestamp")),
                        rs.getInt("score"),
                        rs.getString("note"),
                        rs.getString("analysis")
                    ));
                }
            }
        } catch (SQLException e) {
            throw fail("db.moodEntries.load.failed", e);
        }
        return list;
    }

    public ChatConversation createChatConversation(String title) {
        String id = UUID.randomUUID().toString();
        String now = LocalDateTime.now().toString();
        ChatConversation conversation = new ChatConversation(id, title, now, now);
        saveChatConversation(conversation);
        return conversation;
    }

    public void saveChatConversation(ChatConversation conversation) {
        String sql = """
            INSERT OR REPLACE INTO chat_conversations (id, title, created_at, updated_at)
            VALUES (?, ?, ?, ?)
        """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversation.getId());
            ps.setString(2, conversation.getTitle());
            ps.setString(3, conversation.getCreatedAt());
            ps.setString(4, conversation.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.chatConversation.save.failed", e, "conversationId", conversation.getId());
        }
    }

    public void updateChatConversationTitle(String id, String title) {
        runInTransactionAction(
            "updateChatConversationTitle",
            connection -> {
                String sql = "UPDATE chat_conversations SET title = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, title);
                    ps.setString(2, LocalDateTime.now().toString());
                    ps.setString(3, id);
                    ps.executeUpdate();
                }
            },
            "conversationId", id
        );
    }

    public void touchChatConversation(String id) {
        String sql = "UPDATE chat_conversations SET updated_at = ? WHERE id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDateTime.now().toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw fail("db.chatConversation.touch.failed", e, "conversationId", id);
        }
    }

    public List<ChatConversation> loadChatConversations() {
        List<ChatConversation> conversations = new ArrayList<>();
        String sql = "SELECT * FROM chat_conversations ORDER BY updated_at DESC";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                conversations.add(new ChatConversation(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("created_at"),
                    rs.getString("updated_at")
                ));
            }
        } catch (SQLException e) {
            throw fail("db.chatConversations.load.failed", e);
        }
        return conversations;
    }

    public ChatConversation loadChatConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM chat_conversations WHERE id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ChatConversation(
                    rs.getString("id"),
                    rs.getString("title"),
                    rs.getString("created_at"),
                    rs.getString("updated_at")
                );
            }
        } catch (SQLException e) {
            throw fail("db.chatConversation.load.failed", e, "conversationId", conversationId);
        }
    }

    public List<ChatMessage> loadChatMessages(String conversationId) {
        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT * FROM chat_messages WHERE conversation_id = ? ORDER BY seq ASC";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ChatMessage(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("role"),
                        rs.getString("content"),
                        rs.getInt("seq"),
                        rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw fail("db.chatMessages.load.failed", e, "conversationId", conversationId);
        }
        return messages;
    }

    public List<String> loadAllChatMessageIds() {
        List<String> messageIds = new ArrayList<>();
        String sql = "SELECT id FROM chat_messages";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                if (id != null && !id.isBlank()) {
                    messageIds.add(id);
                }
            }
        } catch (SQLException e) {
            throw fail("db.chatMessageIds.load.failed", e);
        }
        return messageIds;
    }

    public ChatContextState loadChatContextState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        String sql = """
            SELECT conversation_id, preferred_mode, summary, summary_covered_messages, pinned_facts,
                   last_context_window_tokens, last_estimated_usage_tokens, last_reserved_completion_tokens,
                   last_summarize_at, last_summarize_status, active_summary_revision, last_budget_severity,
                   last_usage_ratio, updated_at
            FROM chat_context_state
            WHERE conversation_id = ?
        """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ChatContextState(
                    rs.getString("conversation_id"),
                    rs.getString("preferred_mode"),
                    rs.getString("summary"),
                    rs.getInt("summary_covered_messages"),
                    decodePinnedFacts(rs.getString("pinned_facts")),
                    getNullableInt(rs, "last_context_window_tokens"),
                    getNullableInt(rs, "last_estimated_usage_tokens"),
                    getNullableInt(rs, "last_reserved_completion_tokens"),
                    rs.getString("last_summarize_at"),
                    rs.getString("last_summarize_status"),
                    getNullableInt(rs, "active_summary_revision"),
                    rs.getString("last_budget_severity"),
                    getNullableDouble(rs, "last_usage_ratio"),
                    rs.getString("updated_at")
                );
            }
        } catch (SQLException e) {
            if (isMissingChatContextStateTable(e)) {
                LOG.warning(
                    "db.chatContextState.table.missing",
                    "conversationId", conversationId,
                    "fallback", "skip_load"
                );
                return null;
            }
            throw fail("db.chatContextState.load.failed", e, "conversationId", conversationId);
        }
    }

    public void saveChatContextState(ChatContextState contextState) {
        if (contextState == null || contextState.getConversationId() == null || contextState.getConversationId().isBlank()) {
            return;
        }
        String sql = """
            INSERT INTO chat_context_state (
                conversation_id,
                preferred_mode,
                summary,
                summary_covered_messages,
                pinned_facts,
                last_context_window_tokens,
                last_estimated_usage_tokens,
                last_reserved_completion_tokens,
                last_summarize_at,
                last_summarize_status,
                active_summary_revision,
                last_budget_severity,
                last_usage_ratio,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(conversation_id) DO UPDATE SET
                preferred_mode = excluded.preferred_mode,
                summary = excluded.summary,
                summary_covered_messages = excluded.summary_covered_messages,
                pinned_facts = excluded.pinned_facts,
                last_context_window_tokens = excluded.last_context_window_tokens,
                last_estimated_usage_tokens = excluded.last_estimated_usage_tokens,
                last_reserved_completion_tokens = excluded.last_reserved_completion_tokens,
                last_summarize_at = excluded.last_summarize_at,
                last_summarize_status = excluded.last_summarize_status,
                active_summary_revision = excluded.active_summary_revision,
                last_budget_severity = excluded.last_budget_severity,
                last_usage_ratio = excluded.last_usage_ratio,
                updated_at = excluded.updated_at
        """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contextState.getConversationId().trim());
            ps.setString(2, normalizePreferredMode(contextState.getPreferredMode()));
            ps.setString(3, emptyIfNull(contextState.getSummary()));
            ps.setInt(4, Math.max(0, contextState.getSummaryCoveredMessages()));
            ps.setString(5, encodePinnedFacts(contextState.getPinnedFacts()));
            setNullableInt(ps, 6, contextState.getLastContextWindowTokens());
            setNullableInt(ps, 7, contextState.getLastEstimatedUsageTokens());
            setNullableInt(ps, 8, contextState.getLastReservedCompletionTokens());
            ps.setString(9, emptyIfNull(contextState.getLastSummarizeAt()));
            ps.setString(10, emptyIfNull(contextState.getLastSummarizeStatus()));
            ps.setInt(11, Math.max(0, contextState.getActiveSummaryRevision() == null ? 0 : contextState.getActiveSummaryRevision()));
            ps.setString(12, emptyIfNull(contextState.getLastBudgetSeverity()));
            setNullableDouble(ps, 13, contextState.getLastUsageRatio());
            ps.setString(14, hasText(contextState.getUpdatedAt()) ? contextState.getUpdatedAt() : LocalDateTime.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isMissingChatContextStateTable(e)) {
                LOG.warning(
                    "db.chatContextState.table.missing",
                    "conversationId", contextState.getConversationId(),
                    "fallback", "skip_save"
                );
                return;
            }
            throw fail(
                "db.chatContextState.save.failed",
                e,
                "conversationId", contextState.getConversationId()
            );
        }
    }

    public void deleteChatContextState(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String sql = "DELETE FROM chat_context_state WHERE conversation_id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isMissingChatContextStateTable(e)) {
                LOG.warning(
                    "db.chatContextState.table.missing",
                    "conversationId", conversationId,
                    "fallback", "skip_delete"
                );
                return;
            }
            throw fail("db.chatContextState.delete.failed", e, "conversationId", conversationId);
        }
    }

    public void deleteAllChatContextStates() {
        String sql = "DELETE FROM chat_context_state";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            if (isMissingChatContextStateTable(e)) {
                LOG.warning("db.chatContextState.table.missing", "fallback", "skip_delete_all");
                return;
            }
            throw fail("db.chatContextState.deleteAll.failed", e);
        }
    }

    public int countImageJobStates() {
        String sql = "SELECT COUNT(*) FROM image_job_state";
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning("db.imageJobState.table.missing", "fallback", "count_zero");
                return 0;
            }
            throw fail("db.imageJobState.count.failed", e);
        }
    }

    public ImageJobRecord loadImageJobState(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        String sql = """
            SELECT *
            FROM image_job_state
            WHERE job_id = ?
        """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, jobId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapImageJobRecord(rs) : null;
            }
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning("db.imageJobState.table.missing", "jobId", jobId, "fallback", "skip_load");
                return null;
            }
            throw fail("db.imageJobState.load.failed", e, "jobId", jobId);
        }
    }

    public List<ImageJobRecord> loadImageJobStatesByConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        String sql = """
            SELECT *
            FROM image_job_state
            WHERE conversation_id = ?
            ORDER BY updated_at DESC
        """;
        List<ImageJobRecord> jobs = new ArrayList<>();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    jobs.add(mapImageJobRecord(rs));
                }
            }
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning("db.imageJobState.table.missing", "conversationId", conversationId, "fallback", "skip_load_conversation");
                return List.of();
            }
            throw fail("db.imageJobState.loadByConversation.failed", e, "conversationId", conversationId);
        }
        return jobs;
    }

    public ImageJobRecord loadLatestImageJobStateByConversation(String conversationId) {
        List<ImageJobRecord> jobs = loadImageJobStatesByConversation(conversationId);
        return jobs.isEmpty() ? null : jobs.get(0);
    }

    public List<ImageJobRecord> loadResumableImageJobStates() {
        String sql = """
            SELECT *
            FROM image_job_state
            WHERE request_id <> ''
              AND cancel_requested = 0
              AND stage IN ('SUBMITTED', 'POLLING', 'DOWNLOADING', 'SAVING', 'FAILED', 'PAUSED')
            ORDER BY updated_at DESC
        """;
        List<ImageJobRecord> jobs = new ArrayList<>();
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                jobs.add(mapImageJobRecord(rs));
            }
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning("db.imageJobState.table.missing", "fallback", "skip_load_resumable");
                return List.of();
            }
            throw fail("db.imageJobState.loadResumable.failed", e);
        }
        return jobs;
    }

    public void saveImageJobState(ImageJobRecord record) {
        if (record == null || record.getJobId() == null || record.getJobId().isBlank()) {
            return;
        }
        String sql = """
            INSERT INTO image_job_state (
                job_id,
                conversation_id,
                request_id,
                requested_model,
                active_model,
                prompt,
                prompt_hash,
                size,
                aspect_ratio,
                resolution,
                stage,
                attempt,
                user_retry_count,
                remote_url,
                saved_path,
                last_message,
                last_error,
                pause_requested,
                cancel_requested,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(job_id) DO UPDATE SET
                conversation_id = excluded.conversation_id,
                request_id = excluded.request_id,
                requested_model = excluded.requested_model,
                active_model = excluded.active_model,
                prompt = excluded.prompt,
                prompt_hash = excluded.prompt_hash,
                size = excluded.size,
                aspect_ratio = excluded.aspect_ratio,
                resolution = excluded.resolution,
                stage = excluded.stage,
                attempt = excluded.attempt,
                user_retry_count = excluded.user_retry_count,
                remote_url = excluded.remote_url,
                saved_path = excluded.saved_path,
                last_message = excluded.last_message,
                last_error = excluded.last_error,
                pause_requested = excluded.pause_requested,
                cancel_requested = excluded.cancel_requested,
                created_at = excluded.created_at,
                updated_at = excluded.updated_at
        """;
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getJobId());
            if (hasText(record.getConversationId())) {
                ps.setString(2, record.getConversationId());
            } else {
                ps.setNull(2, Types.VARCHAR);
            }
            ps.setString(3, emptyIfNull(record.getRequestId()));
            ps.setString(4, emptyIfNull(record.getRequestedModel()));
            ps.setString(5, emptyIfNull(record.getActiveModel()));
            ps.setString(6, emptyIfNull(record.getPrompt()));
            ps.setString(7, emptyIfNull(record.getPromptHash()));
            ps.setString(8, emptyIfNull(record.getSize()));
            ps.setString(9, emptyIfNull(record.getAspectRatio()));
            ps.setString(10, emptyIfNull(record.getResolution()));
            ps.setString(11, emptyIfNull(record.getStage()));
            ps.setInt(12, Math.max(1, record.getAttempt()));
            ps.setInt(13, Math.max(0, record.getUserRetryCount()));
            ps.setString(14, emptyIfNull(record.getRemoteUrl()));
            ps.setString(15, emptyIfNull(record.getSavedPath()));
            ps.setString(16, emptyIfNull(record.getLastMessage()));
            ps.setString(17, emptyIfNull(record.getLastError()));
            ps.setInt(18, record.isPauseRequested() ? 1 : 0);
            ps.setInt(19, record.isCancelRequested() ? 1 : 0);
            ps.setString(20, hasText(record.getCreatedAt()) ? record.getCreatedAt() : LocalDateTime.now().toString());
            ps.setString(21, hasText(record.getUpdatedAt()) ? record.getUpdatedAt() : LocalDateTime.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning("db.imageJobState.table.missing", "jobId", record.getJobId(), "fallback", "skip_save");
                return;
            }
            throw fail("db.imageJobState.save.failed", e, "jobId", record.getJobId());
        }
    }

    public void deleteImageJobStatesByConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String sql = "DELETE FROM image_job_state WHERE conversation_id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            if (isMissingImageJobStateTable(e)) {
                LOG.warning(
                    "db.imageJobState.table.missing",
                    "conversationId", conversationId,
                    "fallback", "skip_delete_conversation"
                );
                return;
            }
            throw fail("db.imageJobState.deleteByConversation.failed", e, "conversationId", conversationId);
        }
    }

    public void saveChatMessage(ChatMessage message) {
        runInTransactionAction(
            "saveChatMessage",
            connection -> saveChatMessage(connection, message),
            "messageId", message == null ? "" : message.getId()
        );
    }

    public int countChatMessages(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM chat_messages WHERE conversation_id = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw fail("db.chatMessages.count.failed", e, "conversationId", conversationId);
        }
    }

    public void deleteChatConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try (Connection conn = openConnection()) {
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            SQLException transactionalFailure = null;
            try {
                deleteChatConversation(conn, conversationId);
                conn.commit();
            } catch (SQLException e) {
                transactionalFailure = e;
                rollbackWithLogging(conn, "deleteChatConversation", conversationId, e);
                throw e;
            } finally {
                restoreAutoCommitWithLogging(conn, previousAutoCommit, "deleteChatConversation", conversationId, transactionalFailure);
            }
        } catch (SQLException e) {
            throw fail("db.chatConversation.delete.failed", e, "conversationId", conversationId);
        }
    }

    public void persistChatConversationBundle(
        ChatConversation conversation,
        List<ChatMessage> messages,
        ChatContextState contextState,
        boolean replaceExisting
    ) {
        if (conversation == null || conversation.getId() == null || conversation.getId().isBlank()) {
            return;
        }
        runInTransactionAction(
            "persistChatConversationBundle",
            connection -> {
                if (replaceExisting) {
                    deleteChatConversation(connection, conversation.getId());
                }
                saveChatConversation(connection, conversation);
                if (messages != null) {
                    for (ChatMessage message : messages) {
                        saveChatMessage(connection, message);
                    }
                }
                if (contextState != null) {
                    saveChatContextState(connection, contextState);
                }
            },
            "conversationId", conversation.getId(),
            "messageCount", messages == null ? 0 : messages.size(),
            "replaceExisting", replaceExisting
        );
    }

    private void saveChatConversation(Connection conn, ChatConversation conversation) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO chat_conversations (id, title, created_at, updated_at)
            VALUES (?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, conversation.getId());
            ps.setString(2, conversation.getTitle());
            ps.setString(3, conversation.getCreatedAt());
            ps.setString(4, conversation.getUpdatedAt());
            ps.executeUpdate();
        }
    }

    private void saveChatMessage(Connection conn, ChatMessage message) throws SQLException {
        String sql = """
            INSERT OR REPLACE INTO chat_messages (id, conversation_id, role, content, seq, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, message.getId());
            ps.setString(2, message.getConversationId());
            ps.setString(3, message.getRole());
            ps.setString(4, message.getContent());
            ps.setInt(5, message.getSeq());
            ps.setString(6, message.getCreatedAt());
            ps.executeUpdate();
        }
    }

    private void saveChatContextState(Connection conn, ChatContextState contextState) throws SQLException {
        if (contextState == null || contextState.getConversationId() == null || contextState.getConversationId().isBlank()) {
            return;
        }
        String sql = """
            INSERT INTO chat_context_state (
                conversation_id,
                preferred_mode,
                summary,
                summary_covered_messages,
                pinned_facts,
                last_context_window_tokens,
                last_estimated_usage_tokens,
                last_reserved_completion_tokens,
                last_summarize_at,
                last_summarize_status,
                active_summary_revision,
                last_budget_severity,
                last_usage_ratio,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(conversation_id) DO UPDATE SET
                preferred_mode = excluded.preferred_mode,
                summary = excluded.summary,
                summary_covered_messages = excluded.summary_covered_messages,
                pinned_facts = excluded.pinned_facts,
                last_context_window_tokens = excluded.last_context_window_tokens,
                last_estimated_usage_tokens = excluded.last_estimated_usage_tokens,
                last_reserved_completion_tokens = excluded.last_reserved_completion_tokens,
                last_summarize_at = excluded.last_summarize_at,
                last_summarize_status = excluded.last_summarize_status,
                active_summary_revision = excluded.active_summary_revision,
                last_budget_severity = excluded.last_budget_severity,
                last_usage_ratio = excluded.last_usage_ratio,
                updated_at = excluded.updated_at
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contextState.getConversationId().trim());
            ps.setString(2, normalizePreferredMode(contextState.getPreferredMode()));
            ps.setString(3, emptyIfNull(contextState.getSummary()));
            ps.setInt(4, Math.max(0, contextState.getSummaryCoveredMessages()));
            ps.setString(5, encodePinnedFacts(contextState.getPinnedFacts()));
            setNullableInt(ps, 6, contextState.getLastContextWindowTokens());
            setNullableInt(ps, 7, contextState.getLastEstimatedUsageTokens());
            setNullableInt(ps, 8, contextState.getLastReservedCompletionTokens());
            ps.setString(9, emptyIfNull(contextState.getLastSummarizeAt()));
            ps.setString(10, emptyIfNull(contextState.getLastSummarizeStatus()));
            ps.setInt(11, Math.max(0, contextState.getActiveSummaryRevision() == null ? 0 : contextState.getActiveSummaryRevision()));
            ps.setString(12, emptyIfNull(contextState.getLastBudgetSeverity()));
            setNullableDouble(ps, 13, contextState.getLastUsageRatio());
            ps.setString(14, hasText(contextState.getUpdatedAt()) ? contextState.getUpdatedAt() : LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    private void deleteChatConversation(Connection conn, String conversationId) throws SQLException {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try (PreparedStatement deleteContext = conn.prepareStatement("DELETE FROM chat_context_state WHERE conversation_id = ?");
             PreparedStatement deleteMessages = conn.prepareStatement("DELETE FROM chat_messages WHERE conversation_id = ?");
             PreparedStatement deleteConversation = conn.prepareStatement("DELETE FROM chat_conversations WHERE id = ?")) {
            deleteContext.setString(1, conversationId);
            deleteContext.executeUpdate();
            deleteMessages.setString(1, conversationId);
            deleteMessages.executeUpdate();
            deleteConversation.setString(1, conversationId);
            deleteConversation.executeUpdate();
        }
    }

    private String normalizePreferredMode(String preferredMode) {
        if (preferredMode == null || preferredMode.isBlank()) {
            return "AUTO";
        }
        String normalized = preferredMode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "AUTO", "RECENT", "FULL", "MINIMAL" -> normalized;
            default -> "AUTO";
        };
    }

    private String encodePinnedFacts(List<String> pinnedFacts) {
        if (pinnedFacts == null || pinnedFacts.isEmpty()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder();
        for (String fact : pinnedFacts) {
            if (fact == null || fact.isBlank()) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append('\n');
            }
            encoded.append(fact.replace('\n', ' ').replace('\r', ' ').trim());
        }
        return encoded.toString();
    }

    private List<String> decodePinnedFacts(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        String[] parts = encoded.split("\\R");
        List<String> decoded = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String normalized = part.trim();
            if (!normalized.isBlank()) {
                decoded.add(normalized);
            }
        }
        return decoded;
    }

    private boolean isMissingChatContextStateTable(SQLException e) {
        if (e == null) {
            return false;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("no such table") && normalized.contains("chat_context_state");
    }

    private boolean isMissingImageJobStateTable(SQLException e) {
        if (e == null) {
            return false;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("no such table") && normalized.contains("image_job_state");
    }

    private ImageJobRecord mapImageJobRecord(ResultSet rs) throws SQLException {
        return new ImageJobRecord(
            rs.getString("job_id"),
            rs.getString("conversation_id"),
            rs.getString("request_id"),
            rs.getString("requested_model"),
            rs.getString("active_model"),
            rs.getString("prompt"),
            rs.getString("prompt_hash"),
            rs.getString("size"),
            rs.getString("aspect_ratio"),
            rs.getString("resolution"),
            rs.getString("stage"),
            rs.getInt("attempt"),
            rs.getInt("user_retry_count"),
            rs.getString("remote_url"),
            rs.getString("saved_path"),
            rs.getString("last_message"),
            rs.getString("last_error"),
            rs.getInt("pause_requested") != 0,
            rs.getInt("cancel_requested") != 0,
            rs.getString("created_at"),
            rs.getString("updated_at")
        );
    }

    private void rollbackWithLogging(Connection conn, String operation, String operationId, Throwable original) {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
            LOG.error(
                "db.transaction.rollback.failed",
                rollbackError,
                withDbUrl("operation", operation, "operationId", operationId)
            );
        }
    }

    private void restoreAutoCommitWithLogging(
        Connection conn,
        boolean previousAutoCommit,
        String operation,
        String operationId,
        Throwable original
    ) {
        try {
            conn.setAutoCommit(previousAutoCommit);
        } catch (SQLException restoreError) {
            if (original != null) {
                original.addSuppressed(restoreError);
            }
            LOG.error(
                "db.transaction.autocommit.restore.failed",
                restoreError,
                withDbUrl("operation", operation, "operationId", operationId)
            );
        }
    }

    public void saveGoal(Goal goal) {
        runInTransactionAction(
            "saveGoal",
            connection -> {
                int previousProgress = loadGoalProgress(connection, goal.getId());
                try (PreparedStatement statement = connection.prepareStatement(UPSERT_GOAL_SQL)) {
                    statement.setString(1, goal.getId());
                    statement.setString(2, goal.getTitle());
                    statement.setString(3, goal.getPeriod());
                    statement.setInt(4, goal.getTarget());
                    statement.setInt(5, goal.getProgress());
                    statement.setString(6, goal.getCreatedAt());
                    statement.setString(7, goal.getUpdatedAt());
                    statement.executeUpdate();
                }
                touchEntityAsLocalOnly(connection, "goals", goal.getId(), false);

                int progressDelta = goal.getProgress() - previousProgress;
                if (progressDelta != 0 && hasTableColumn(connection, "sync_outbox", "payload_json")) {
                    String entryId = UUID.randomUUID().toString();
                    String recordedAt = hasText(goal.getUpdatedAt()) ? goal.getUpdatedAt() : nowUtc();
                    String payloadJson = "{\"id\":\"" + entryId
                        + "\",\"goal_id\":\"" + goal.getId()
                        + "\",\"value_delta\":" + progressDelta
                        + ",\"recorded_at\":\"" + recordedAt + "\"}";
                    upsertSyncOutbox(
                        connection,
                        "GOAL_PROGRESS_ENTRY",
                        entryId,
                        "UPSERT",
                        payloadJson
                    );
                }
            },
            "goalId", goal == null ? null : goal.getId()
        );
    }

    public List<Goal> loadGoals() {
        List<Goal> goals = new ArrayList<>();
        try (Connection conn = openConnection()) {
            String sql = hasTableColumn(conn, "goals", "deleted_at")
                ? "SELECT * FROM goals WHERE deleted_at = '' ORDER BY updated_at DESC"
                : "SELECT * FROM goals ORDER BY updated_at DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    goals.add(new Goal(
                        rs.getString("id"),
                        rs.getString("title"),
                        rs.getString("period"),
                        rs.getInt("target"),
                        rs.getInt("progress"),
                        rs.getString("created_at"),
                        rs.getString("updated_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw fail("db.goals.load.failed", e);
        }
        return goals;
    }

    public void deleteGoal(String id) {
        try (Connection conn = openConnection()) {
            if (hasTableColumn(conn, "goals", "sync_status")) {
                runInTransactionAction(
                    "deleteGoalLocally",
                    connection -> softDeleteSingleIdEntityLocally(connection, "GOAL", id),
                    "goalId", id
                );
                return;
            }
            String sql = "DELETE FROM goals WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw fail("db.goal.delete.failed", e, "goalId", id);
        }
    }
}
