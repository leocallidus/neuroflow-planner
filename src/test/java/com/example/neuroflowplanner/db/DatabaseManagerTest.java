package com.example.neuroflowplanner.db;

import com.example.neuroflowplanner.model.ChatConversation;
import com.example.neuroflowplanner.model.ChatMessage;
import com.example.neuroflowplanner.model.Goal;
import com.example.neuroflowplanner.model.LocalAccountLink;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.DbWriteConfigDefaults;
import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для DatabaseManager.
 * UT-003, UT-004, UT-005
 */
@DisplayName("DatabaseManager Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseManagerTest extends IsolatedTestDataFixture {
    private static final String ISOLATED_DIR_PREFIX = "neuroflow-test-data-";

    private static DatabaseManager db;
    private static String testTaskId;

    @BeforeAll
    static void setUpClass() {
        assertIsolatedDataDir();
        db = DatabaseManager.getInstance();
        testTaskId = "test-" + UUID.randomUUID().toString();
    }

    @BeforeEach
    void ensureIsolationBeforeTest() {
        assertIsolatedDataDir();
    }

    private static void assertIsolatedDataDir() {
        Path dataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        Path fileName = dataDir.getFileName();
        assertTrue(
            fileName != null && fileName.toString().startsWith(ISOLATED_DIR_PREFIX),
            "Cleanup allowed only in isolated test data dir, actual: " + dataDir
        );
    }

    // UT-003: Сохранение задачи
    @Test
    @Order(1)
    @DisplayName("UT-003: Сохранение новой задачи")
    void testSaveTask() {
        Task task = new Task(
            testTaskId,
            "Тестовая задача для БД",
            "Описание тестовой задачи",
            LocalDate.now().plusDays(7),
            5,
            null,
            "тест, junit",
            ""
        );
        task.setSmartPriority(6.5);

        assertDoesNotThrow(() -> db.saveTask(task));
    }

    // UT-004: Загрузка задач
    @Test
    @Order(2)
    @DisplayName("UT-004: Загрузка всех задач")
    void testLoadAllTasks() {
        List<Task> tasks = db.loadAllTasks();

        assertNotNull(tasks, "Список задач не должен быть null");
        // Список может быть пустым или содержать задачи
    }

    @Test
    @Order(3)
    @DisplayName("UT-004: Загрузка сохранённой задачи")
    void testLoadSavedTask() {
        List<Task> tasks = db.loadAllTasks();

        // Ищем нашу тестовую задачу
        Task found = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(found, "Сохранённая задача должна быть найдена");
        assertEquals("Тестовая задача для БД", found.getTitle());
        assertEquals("Описание тестовой задачи", found.getDescription());
        assertEquals(5, found.getComplexity());
        assertEquals("тест, junit", found.getTags());
    }

    @Test
    @Order(4)
    @DisplayName("UT-003: Обновление существующей задачи")
    void testUpdateTask() {
        List<Task> tasks = db.loadAllTasks();
        Task task = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(task);

        // Обновляем задачу
        task.setTags("тест, junit, обновлено");
        task.setSmartPriority(8.0);
        task.setCompleted(true);
        task.setCompletedDate(LocalDate.now());

        db.saveTask(task);

        // Перезагружаем и проверяем
        List<Task> reloaded = db.loadAllTasks();
        Task updated = reloaded.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(updated);
        assertEquals("тест, junit, обновлено", updated.getTags());
        assertTrue(updated.isCompleted());
        assertNotNull(updated.getCompletedDate());
    }

    @Test
    @Order(5)
    @DisplayName("Сохранение задачи с подзадачами")
    void testSaveTaskWithSubtasks() {
        String parentId = "parent-" + UUID.randomUUID().toString();
        String subtaskId = "subtask-" + UUID.randomUUID().toString();

        Task parent = new Task(
            parentId,
            "Родительская задача",
            "Описание",
            LocalDate.now().plusDays(10),
            7
        );

        Task subtask = new Task(
            subtaskId,
            "Подзадача",
            "Описание подзадачи",
            LocalDate.now().plusDays(5),
            3,
            parentId
        );

        db.saveTask(parent);
        db.saveTask(subtask);

        // Проверяем загрузку - подзадачи могут быть вложены в родительские задачи
        List<Task> tasks = db.loadAllTasks();
        
        // Ищем подзадачу либо в корневом списке, либо внутри родительской задачи
        Task loadedSubtask = tasks.stream()
            .filter(t -> t.getId().equals(subtaskId))
            .findFirst()
            .orElse(null);
        
        // Если не нашли в корне, ищем в подзадачах родителя
        if (loadedSubtask == null) {
            Task loadedParent = tasks.stream()
                .filter(t -> t.getId().equals(parentId))
                .findFirst()
                .orElse(null);
            if (loadedParent != null && loadedParent.hasSubtasks()) {
                loadedSubtask = loadedParent.getSubtasks().stream()
                    .filter(t -> t.getId().equals(subtaskId))
                    .findFirst()
                    .orElse(null);
            }
        }

        // Подзадача должна быть найдена где-то
        assertNotNull(loadedSubtask, "Подзадача должна быть сохранена и загружена");
        assertEquals(parentId, loadedSubtask.getParentId());
        assertTrue(loadedSubtask.isSubtask());

        // Очистка
        db.deleteTask(subtaskId);
        db.deleteTask(parentId);
    }

    @Test
    @Order(6)
    @DisplayName("Сохранение задачи с датой начала")
    void testSaveTaskWithStartDate() {
        String taskId = "start-date-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с датой начала",
            "Описание",
            LocalDate.now().plusDays(14),
            5
        );
        task.setStartDate(LocalDate.now().plusDays(7));
        task.setStartTime(LocalTime.of(9, 30));
        task.setDeadlineTime(LocalTime.of(18, 45));

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertNotNull(loaded.getStartDate());
        assertEquals(LocalDate.now().plusDays(7), loaded.getStartDate());
        assertEquals(LocalTime.of(9, 30), loaded.getStartTime());
        assertEquals(LocalTime.of(18, 45), loaded.getDeadlineTime());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @Order(7)
    @DisplayName("Сохранение архивной задачи")
    void testSaveArchivedTask() {
        String taskId = "archived-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Архивная задача",
            "Описание",
            LocalDate.now().plusDays(5),
            3
        );
        task.setArchived(true);

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertTrue(loaded.isArchived());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @Order(8)
    @DisplayName("Сохранение задачи с трекингом времени")
    void testSaveTaskWithTrackedTime() {
        String taskId = "tracked-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с трекингом",
            "Описание",
            LocalDate.now().plusDays(5),
            5
        );
        task.setTrackedMinutes(120); // 2 часа

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals(120, loaded.getTrackedMinutes());

        // Очистка
        db.deleteTask(taskId);
    }

    // UT-005: Удаление задачи
    @Test
    @Order(100) // Выполняется последним
    @DisplayName("UT-005: Удаление задачи")
    void testDeleteTask() {
        // Удаляем тестовую задачу
        db.deleteTask(testTaskId);

        // Проверяем, что задача удалена
        List<Task> tasks = db.loadAllTasks();
        Task found = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNull(found, "Удалённая задача не должна быть найдена");
    }

    @Test
    @DisplayName("Удаление несуществующей задачи не вызывает ошибку")
    void testDeleteNonExistingTask() {
        assertDoesNotThrow(() -> db.deleteTask("non-existing-task-id-xyz"));
    }

    @Test
    @DisplayName("Singleton паттерн DatabaseManager")
    void testSingletonPattern() {
        DatabaseManager instance1 = DatabaseManager.getInstance();
        DatabaseManager instance2 = DatabaseManager.getInstance();

        assertSame(instance1, instance2, "Должен возвращаться один и тот же экземпляр");
    }

    @Test
    @DisplayName("Сохранение задачи с повторением")
    void testSaveRecurringTask() {
        String taskId = "recurring-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Повторяющаяся задача",
            "Описание",
            LocalDate.now().plusDays(7),
            4,
            null,
            "повтор",
            "weekly"
        );

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals("weekly", loaded.getRecurrence());
        assertTrue(loaded.isRecurring());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("Legacy dependsOn CSV больше не сохраняется в tasks")
    void testSaveTaskIgnoresLegacyDependsOnCsv() {
        String taskId = "deps-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с зависимостями",
            "Описание",
            LocalDate.now().plusDays(10),
            6
        );
        task.setDependsOn("task-1, task-2");

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals("", loaded.getDependsOn());
        assertFalse(loaded.hasDependencies());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("Legacy bridge load/update depends_on no-op when column dropped")
    void testLegacyDependsOnBridgeNoOpAfterColumnDrop() {
        String taskId = "legacy-bridge-" + UUID.randomUUID().toString();
        Task task = new Task(taskId, "legacy-bridge", "", LocalDate.now().plusDays(2), 2);
        db.saveTask(task);

        assertDoesNotThrow(() -> db.updateLegacyDependsOn(taskId, "task-1"));
        assertEquals("", db.loadLegacyDependsOn(taskId));

        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("CRUD ребер зависимостей через нормализованную таблицу")
    void testDependencyEdgeCrud() {
        String taskId = "dep-main-" + UUID.randomUUID();
        String blockerAId = "dep-blocker-A-" + UUID.randomUUID();
        String blockerBId = "dep-blocker-B-" + UUID.randomUUID();

        Task mainTask = new Task(taskId, "main", "main", LocalDate.now().plusDays(8), 4);
        Task blockerA = new Task(blockerAId, "blockerA", "blockerA", LocalDate.now().plusDays(6), 3);
        Task blockerB = new Task(blockerBId, "blockerB", "blockerB", LocalDate.now().plusDays(5), 2);
        db.saveTask(mainTask);
        db.saveTask(blockerA);
        db.saveTask(blockerB);

        db.saveDependencies(taskId, List.of(blockerAId, blockerBId, blockerAId, " ", taskId, "missing-task"));

        List<String> loadedDependencies = db.loadDependencies(taskId);
        assertEquals(List.of(blockerAId, blockerBId), loadedDependencies);

        List<TaskDependencyEdge> edges = db.loadAllDependencyEdges();
        assertTrue(edges.contains(new TaskDependencyEdge(taskId, blockerAId)));
        assertTrue(edges.contains(new TaskDependencyEdge(taskId, blockerBId)));
        assertFalse(edges.contains(new TaskDependencyEdge(taskId, taskId)));
        assertFalse(edges.contains(new TaskDependencyEdge(taskId, "missing-task")));

        db.deleteDependenciesForTask(taskId);
        assertTrue(db.loadDependencies(taskId).isEmpty());

        db.deleteTask(taskId);
        db.deleteTask(blockerAId);
        db.deleteTask(blockerBId);
    }

    @Test
    @DisplayName("Ограничения FK/CHECK в task_dependencies валидируют ссылочную целостность и self-loop")
    void testDependencyTableConstraints() throws Exception {
        String dependentId = "dep-constraint-main-" + UUID.randomUUID();
        String blockerId = "dep-constraint-blocker-" + UUID.randomUUID();

        db.saveTask(new Task(dependentId, "constraint-main", "", LocalDate.now().plusDays(4), 3));
        db.saveTask(new Task(blockerId, "constraint-blocker", "", LocalDate.now().plusDays(4), 2));

        String insertSql = "INSERT INTO task_dependencies (dependent_task_id, blocker_task_id) VALUES (?, ?)";
        try (Connection connection = openConnectionWithForeignKeys()) {
            SQLException fkViolation = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, dependentId);
                    ps.setString(2, "missing-" + UUID.randomUUID());
                    ps.executeUpdate();
                }
            });
            assertTrue(fkViolation.getMessage().toLowerCase().contains("foreign key"));

            SQLException selfLoopViolation = assertThrows(SQLException.class, () -> {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, blockerId);
                    ps.setString(2, blockerId);
                    ps.executeUpdate();
                }
            });
            assertTrue(selfLoopViolation.getMessage().toLowerCase().contains("check"));
        } finally {
            db.deleteTask(dependentId);
            db.deleteTask(blockerId);
        }
    }

    @Test
    @DisplayName("Удаление задачи каскадно удаляет ребра зависимостей")
    void testDependencyEdgesCascadeOnTaskDelete() {
        String dependentId = "dep-cascade-main-" + UUID.randomUUID();
        String blockerId = "dep-cascade-blocker-" + UUID.randomUUID();

        db.saveTask(new Task(dependentId, "cascade-main", "", LocalDate.now().plusDays(4), 4));
        db.saveTask(new Task(blockerId, "cascade-blocker", "", LocalDate.now().plusDays(4), 2));
        db.saveDependencies(dependentId, List.of(blockerId));

        assertEquals(List.of(blockerId), db.loadDependencies(dependentId));
        assertTrue(db.loadAllDependencyEdges().contains(new TaskDependencyEdge(dependentId, blockerId)));

        db.deleteTask(blockerId);

        assertTrue(db.loadDependencies(dependentId).isEmpty());
        assertFalse(db.loadAllDependencyEdges().contains(new TaskDependencyEdge(dependentId, blockerId)));

        db.deleteTask(dependentId);
    }

    @Test
    @DisplayName("loadChatConversation возвращает одну переписку по id")
    void testLoadSingleChatConversation() {
        ChatConversation created = db.createChatConversation("Одиночная переписка");

        ChatConversation loaded = db.loadChatConversation(created.getId());

        assertNotNull(loaded);
        assertEquals(created.getId(), loaded.getId());
        assertEquals("Одиночная переписка", loaded.getTitle());
    }

    @Test
    @DisplayName("Удаление переписки удаляет и сообщения")
    void testDeleteChatConversation() {
        ChatConversation conversation = db.createChatConversation("Тестовая переписка");

        ChatMessage m1 = new ChatMessage(
            "msg-" + UUID.randomUUID(),
            conversation.getId(),
            "user",
            "Привет",
            0,
            LocalDateTime.now().toString()
        );
        ChatMessage m2 = new ChatMessage(
            "msg-" + UUID.randomUUID(),
            conversation.getId(),
            "assistant",
            "Здравствуйте",
            1,
            LocalDateTime.now().toString()
        );
        db.saveChatMessage(m1);
        db.saveChatMessage(m2);

        assertEquals(2, db.countChatMessages(conversation.getId()));

        db.deleteChatConversation(conversation.getId());

        assertEquals(0, db.countChatMessages(conversation.getId()));
        assertTrue(db.loadChatMessages(conversation.getId()).isEmpty(), "Сообщения должны быть удалены");

        boolean stillExists = db.loadChatConversations().stream().anyMatch(c -> c.getId().equals(conversation.getId()));
        assertFalse(stillExists, "Переписка должна быть удалена");
    }

    @Test
    @DisplayName("runInTransaction откатывает изменения при SQL ошибке")
    void testRunInTransactionRollbackOnSqlFailure() {
        String taskId = "tx-rollback-" + UUID.randomUUID();
        String insertSql = "INSERT INTO tasks (id, title, description, deadline, complexity) VALUES (?, ?, ?, ?, ?)";

        DatabaseException error = assertThrows(DatabaseException.class, () ->
            db.runInTransaction("test.tx.rollback", connection -> {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, taskId);
                    ps.setString(2, "tx rollback");
                    ps.setString(3, "should rollback");
                    ps.setString(4, LocalDate.now().plusDays(1).toString());
                    ps.setInt(5, 1);
                    ps.executeUpdate();
                }
                throw new SQLException("forced rollback");
            })
        );

        assertEquals("db.transaction.failed", error.getMessage());
        boolean existsAfterRollback = db.loadAllTasks().stream().anyMatch(task -> task.getId().equals(taskId));
        assertFalse(existsAfterRollback, "Задача не должна остаться после rollback");
    }

    @Test
    @DisplayName("runInTransaction фиксирует изменения при успешном commit")
    void testRunInTransactionCommitPersistsChanges() {
        String taskId = "tx-commit-" + UUID.randomUUID();
        String insertSql = "INSERT INTO tasks (id, title, description, deadline, complexity) VALUES (?, ?, ?, ?, ?)";

        db.runInTransaction(
            "test.tx.commit",
            connection -> {
                try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
                    ps.setString(1, taskId);
                    ps.setString(2, "tx commit");
                    ps.setString(3, "commit expected");
                    ps.setString(4, LocalDate.now().plusDays(1).toString());
                    ps.setInt(5, 2);
                    ps.executeUpdate();
                }
                return null;
            }
        );

        boolean existsAfterCommit = db.loadAllTasks().stream().anyMatch(task -> task.getId().equals(taskId));
        assertTrue(existsAfterCommit, "Задача должна сохраниться после commit");
        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("executeBatchedStatement учитывает batch size в batched режиме")
    void testExecuteBatchedStatementReturnsExpectedBatchStats() {
        String previousMode = ConfigManager.getProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE);
        String previousBatchSize = ConfigManager.getProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE);

        List<String> taskIds = IntStream.range(0, 51)
            .mapToObj(i -> "batch-task-" + i + "-" + UUID.randomUUID())
            .toList();
        String insertSql = "INSERT INTO tasks (id, title, description, deadline, complexity) VALUES (?, ?, ?, ?, ?)";

        try {
            ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE, DbWriteConfigDefaults.MODE_BATCHED);
            ConfigManager.setProperty(DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE, "2");

            DatabaseManager.BatchExecutionStats stats = db.runInTransaction(
                "test.batch.insert",
                connection -> db.executeBatchedStatement(
                    connection,
                    "test.batch.insert.tasks",
                    insertSql,
                    taskIds,
                    (statement, taskId) -> {
                        statement.setString(1, taskId);
                        statement.setString(2, "batch-task");
                        statement.setString(3, "batch-insert");
                        statement.setString(4, LocalDate.now().plusDays(2).toString());
                        statement.setInt(5, 2);
                    }
                )
            );

            assertEquals(51, stats.itemCount());
            assertEquals(51, stats.updatedCount());
            assertEquals(2, stats.batchCount());
            assertTrue(stats.durationMs() >= 0);

            List<String> persisted = db.loadAllTasks().stream()
                .map(Task::getId)
                .filter(taskIds::contains)
                .toList();
            assertEquals(taskIds.size(), persisted.size());
        } finally {
            for (String taskId : taskIds) {
                db.deleteTask(taskId);
            }
            ConfigManager.setProperty(
                DbWriteConfigDefaults.CONFIG_DB_BULK_WRITES_MODE,
                previousMode == null ? "" : previousMode
            );
            ConfigManager.setProperty(
                DbWriteConfigDefaults.CONFIG_DB_BULK_BATCH_SIZE,
                previousBatchSize == null ? "" : previousBatchSize
            );
        }
    }

    @Test
    @DisplayName("saveTasksBatch сохраняет задачи атомарно и возвращает summary")
    void testSaveTasksBatchSummary() {
        String taskAId = "bulk-save-a-" + UUID.randomUUID();
        String taskBId = "bulk-save-b-" + UUID.randomUUID();
        String taskCId = "bulk-save-c-" + UUID.randomUUID();
        List<Task> tasks = List.of(
            new Task(taskAId, "bulk-a", "a", LocalDate.now().plusDays(1), 1),
            new Task(taskBId, "bulk-b", "b", LocalDate.now().plusDays(2), 2),
            new Task(taskCId, "bulk-c", "c", LocalDate.now().plusDays(3), 3)
        );

        DatabaseManager.BulkOperationSummary summary = db.saveTasksBatch(tasks);

        assertEquals("saveTasksBatch", summary.operation());
        assertEquals(3, summary.processedCount());
        assertEquals(3, summary.updatedCount());
        assertEquals(0, summary.failedCount());
        assertTrue(summary.batchCount() >= 1);

        List<String> persistedIds = db.loadAllTasks().stream()
            .map(Task::getId)
            .filter(id -> id.equals(taskAId) || id.equals(taskBId) || id.equals(taskCId))
            .toList();
        assertEquals(3, persistedIds.size());

        db.deleteTasksBatch(List.of(taskAId, taskBId, taskCId));
    }

    @Test
    @DisplayName("saveTasksBatch откатывает все изменения при ошибке внутри батча")
    void testSaveTasksBatchRollbackOnFailure() {
        String validTaskId = "bulk-tx-valid-" + UUID.randomUUID();
        String invalidTaskId = "bulk-tx-invalid-" + UUID.randomUUID();
        Task validTask = new Task(validTaskId, "valid", "valid", LocalDate.now().plusDays(4), 2);
        Task invalidTask = new Task(invalidTaskId, null, "invalid", LocalDate.now().plusDays(5), 3);

        assertThrows(DatabaseException.class, () -> db.saveTasksBatch(List.of(validTask, invalidTask)));

        boolean validPersisted = db.loadAllTasks().stream().anyMatch(task -> validTaskId.equals(task.getId()));
        boolean invalidPersisted = db.loadAllTasks().stream().anyMatch(task -> invalidTaskId.equals(task.getId()));
        assertFalse(validPersisted, "Валидная задача не должна сохраниться из-за rollback");
        assertFalse(invalidPersisted, "Невалидная задача не должна сохраниться");
    }

    @Test
    @DisplayName("archiveTasksBatch откатывает все изменения при падении в середине батча")
    void testArchiveTasksBatchRollbackOnMidBatchFailure() throws Exception {
        String firstId = "bulk-archive-rollback-first-" + UUID.randomUUID();
        String secondId = "bulk-archive-rollback-second-" + UUID.randomUUID();
        String triggerName = "trg_archive_abort_" + UUID.randomUUID().toString().replace("-", "");

        db.saveTasksBatch(List.of(
            new Task(firstId, "archive-first", "", LocalDate.now().plusDays(3), 2),
            new Task(secondId, "archive-second", "", LocalDate.now().plusDays(3), 2)
        ));

        createAbortTrigger(
            triggerName,
            """
                CREATE TRIGGER %s
                BEFORE UPDATE OF archived ON tasks
                WHEN NEW.id = '%s' AND NEW.archived = 1
                BEGIN
                    SELECT RAISE(ABORT, 'archive rollback test');
                END;
                """.formatted(triggerName, escapeSqlLiteral(secondId))
        );

        try {
            assertThrows(DatabaseException.class, () -> db.archiveTasksBatch(List.of(firstId, secondId), false));
            Task first = findTaskById(firstId);
            Task second = findTaskById(secondId);
            assertNotNull(first);
            assertNotNull(second);
            assertFalse(first.isArchived(), "Первая задача должна быть откатана");
            assertFalse(second.isArchived(), "Вторая задача не должна архивироваться");
        } finally {
            dropTrigger(triggerName);
            db.deleteTasksBatch(List.of(firstId, secondId));
        }
    }

    @Test
    @DisplayName("deleteTasksBatch откатывает soft-delete при падении в середине батча")
    void testDeleteTasksBatchRollbackOnMidBatchFailure() throws Exception {
        String firstId = "bulk-delete-rollback-first-" + UUID.randomUUID();
        String secondId = "bulk-delete-rollback-second-" + UUID.randomUUID();
        String triggerName = "trg_delete_abort_" + UUID.randomUUID().toString().replace("-", "");

        db.saveTasksBatch(List.of(
            new Task(firstId, "delete-first", "", LocalDate.now().plusDays(4), 2),
            new Task(secondId, "delete-second", "", LocalDate.now().plusDays(4), 2)
        ));

        createAbortTrigger(
            triggerName,
            """
                CREATE TRIGGER %s
                BEFORE UPDATE ON tasks
                WHEN OLD.id = '%s' AND NEW.deleted_at <> OLD.deleted_at
                BEGIN
                    SELECT RAISE(ABORT, 'delete rollback test');
                END;
                """.formatted(triggerName, escapeSqlLiteral(secondId))
        );

        try {
            assertThrows(DatabaseException.class, () -> db.deleteTasksBatch(List.of(firstId, secondId)));
            assertNotNull(findTaskById(firstId), "Первая задача должна быть восстановлена rollback");
            assertNotNull(findTaskById(secondId), "Вторая задача должна остаться после abort");
        } finally {
            dropTrigger(triggerName);
            db.deleteTasksBatch(List.of(firstId, secondId));
        }
    }

    @Test
    @DisplayName("updateTaskTagsBatch откатывает частичные изменения при падении в середине батча")
    void testUpdateTaskTagsBatchRollbackOnMidBatchFailure() throws Exception {
        String firstId = "bulk-tags-rollback-first-" + UUID.randomUUID();
        String secondId = "bulk-tags-rollback-second-" + UUID.randomUUID();
        String triggerName = "trg_tags_abort_" + UUID.randomUUID().toString().replace("-", "");

        db.saveTasksBatch(List.of(
            new Task(firstId, "tags-first", "", LocalDate.now().plusDays(4), 2),
            new Task(secondId, "tags-second", "", LocalDate.now().plusDays(4), 2)
        ));

        createAbortTrigger(
            triggerName,
            """
                CREATE TRIGGER %s
                BEFORE UPDATE OF tags ON tasks
                WHEN NEW.id = '%s'
                BEGIN
                    SELECT RAISE(ABORT, 'tags rollback test');
                END;
                """.formatted(triggerName, escapeSqlLiteral(secondId))
        );

        Map<String, String> updates = new java.util.LinkedHashMap<>();
        updates.put(firstId, "alpha");
        updates.put(secondId, "beta");

        try {
            assertThrows(DatabaseException.class, () -> db.updateTaskTagsBatch(updates));
            Task first = findTaskById(firstId);
            Task second = findTaskById(secondId);
            assertNotNull(first);
            assertNotNull(second);
            assertEquals("", first.getTags(), "Теги первой задачи должны быть откатаны");
            assertEquals("", second.getTags(), "Теги второй задачи не должны примениться");
        } finally {
            dropTrigger(triggerName);
            db.deleteTasksBatch(List.of(firstId, secondId));
        }
    }

    @Test
    @DisplayName("archiveTasksBatch архивирует задачи и подзадачи в одной операции")
    void testArchiveTasksBatchWithSubtasksSummary() {
        String parentId = "bulk-archive-parent-" + UUID.randomUUID();
        String childId = "bulk-archive-child-" + UUID.randomUUID();
        db.saveTasksBatch(List.of(
            new Task(parentId, "parent", "parent", LocalDate.now().plusDays(6), 3),
            new Task(childId, "child", "child", LocalDate.now().plusDays(6), 2, parentId)
        ));

        DatabaseManager.BulkOperationSummary summary = db.archiveTasksBatch(List.of(parentId), true);
        assertEquals("archiveTasksBatchWithSubtasks", summary.operation());
        assertEquals(1, summary.processedCount());
        assertTrue(summary.updatedCount() >= 2);
        assertEquals(0, summary.failedCount());

        Task parent = findTaskById(parentId);
        Task child = findTaskById(childId);
        assertNotNull(parent);
        assertNotNull(child);
        assertTrue(parent.isArchived());
        assertTrue(child.isArchived());

        db.deleteTasksBatch(List.of(parentId));
    }

    @Test
    @DisplayName("deleteTasksBatch удаляет задачи и возвращает summary")
    void testDeleteTasksBatchSummary() {
        String parentId = "bulk-delete-parent-" + UUID.randomUUID();
        String childId = "bulk-delete-child-" + UUID.randomUUID();
        String standaloneId = "bulk-delete-standalone-" + UUID.randomUUID();
        db.saveTasksBatch(List.of(
            new Task(parentId, "parent", "parent", LocalDate.now().plusDays(6), 3),
            new Task(childId, "child", "child", LocalDate.now().plusDays(6), 2, parentId),
            new Task(standaloneId, "standalone", "standalone", LocalDate.now().plusDays(7), 2)
        ));

        DatabaseManager.BulkOperationSummary summary = db.deleteTasksBatch(List.of(parentId, standaloneId));
        assertEquals("deleteTasksBatch", summary.operation());
        assertEquals(2, summary.processedCount());
        assertTrue(summary.updatedCount() >= 2);
        assertEquals(0, summary.failedCount());
        assertNull(findTaskById(parentId));
        assertNull(findTaskById(childId));
        assertNull(findTaskById(standaloneId));
    }

    @Test
    @DisplayName("deleteTasksBatch каскадно удаляет dependency edges для удаленных задач")
    void testDeleteTasksBatchCascadeDeletesDependencyEdges() {
        String dependentAId = "bulk-dep-a-" + UUID.randomUUID();
        String dependentBId = "bulk-dep-b-" + UUID.randomUUID();
        String blockerAId = "bulk-dep-blocker-a-" + UUID.randomUUID();
        String blockerBId = "bulk-dep-blocker-b-" + UUID.randomUUID();

        db.saveTasksBatch(List.of(
            new Task(dependentAId, "dep-a", "", LocalDate.now().plusDays(4), 3),
            new Task(dependentBId, "dep-b", "", LocalDate.now().plusDays(4), 3),
            new Task(blockerAId, "blocker-a", "", LocalDate.now().plusDays(4), 2),
            new Task(blockerBId, "blocker-b", "", LocalDate.now().plusDays(4), 2)
        ));

        db.saveDependencies(dependentAId, List.of(blockerAId, blockerBId));
        db.saveDependencies(dependentBId, List.of(blockerAId));
        assertEquals(3, db.loadAllDependencyEdges().size());

        db.deleteTasksBatch(List.of(blockerAId, dependentAId));

        List<TaskDependencyEdge> edges = db.loadAllDependencyEdges();
        assertTrue(edges.stream().noneMatch(edge ->
            edge.dependentTaskId().equals(dependentAId) || edge.blockerTaskId().equals(blockerAId)
        ));
        assertTrue(db.loadDependencies(dependentBId).isEmpty(), "Оставшаяся задача не должна ссылаться на удаленный blocker");
        assertNotNull(findTaskById(dependentBId));
        assertNotNull(findTaskById(blockerBId));

        db.deleteTasksBatch(List.of(dependentBId, blockerBId));
    }

    @Test
    @DisplayName("updateTaskTagsBatch обновляет теги и учитывает missing task в summary")
    void testUpdateTaskTagsBatchSummary() {
        String firstId = "bulk-tags-first-" + UUID.randomUUID();
        String secondId = "bulk-tags-second-" + UUID.randomUUID();
        String missingId = "bulk-tags-missing-" + UUID.randomUUID();
        db.saveTasksBatch(List.of(
            new Task(firstId, "first", "first", LocalDate.now().plusDays(3), 2),
            new Task(secondId, "second", "second", LocalDate.now().plusDays(3), 2)
        ));

        Map<String, String> updates = new HashMap<>();
        updates.put(secondId, "beta");
        updates.put(missingId, "missing");
        updates.put(firstId, "alpha");

        DatabaseManager.BulkOperationSummary summary = db.updateTaskTagsBatch(updates);
        assertEquals("updateTaskTagsBatch", summary.operation());
        assertEquals(3, summary.processedCount());
        assertEquals(2, summary.updatedCount());
        assertEquals(0, summary.failedCount());

        Task first = findTaskById(firstId);
        Task second = findTaskById(secondId);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals("alpha", first.getTags());
        assertEquals("beta", second.getTags());

        db.deleteTasksBatch(List.of(firstId, secondId));
    }

    @Test
    @DisplayName("Локальные sync_state, account_link и device_identity доступны через DatabaseManager")
    void testLocalSyncMetadataCrud() {
        db.saveSyncState("cursor.tasks", "42");
        assertEquals("42", db.loadSyncState("cursor.tasks"));

        LocalAccountLink accountLink = new LocalAccountLink(
            "user-123",
            "sync@example.com",
            "Sync User",
            "LINKED",
            "2026-03-23T00:00:00Z",
            "2026-03-23T01:00:00Z"
        );
        db.saveAccountLink(accountLink);

        LocalDeviceIdentity deviceIdentity = new LocalDeviceIdentity(
            "device-123",
            "Leo Desktop",
            "linux",
            "1.0.0",
            "2026-03-23T00:00:00Z",
            "2026-03-23T01:00:00Z"
        );
        db.saveDeviceIdentity(deviceIdentity);

        assertEquals(accountLink, db.loadAccountLink());
        assertEquals(deviceIdentity, db.loadDeviceIdentity());

        db.deleteSyncState("cursor.tasks");
        db.clearAccountLink();
        db.clearDeviceIdentity();

        assertEquals("", db.loadSyncState("cursor.tasks"));
        assertNull(db.loadAccountLink());
        assertNull(db.loadDeviceIdentity());
    }

    @Test
    @DisplayName("markEntityPendingSync и sync_outbox сохраняют локальную очередь синхронизации")
    void testSyncOutboxLifecycle() {
        String taskId = "sync-outbox-task-" + UUID.randomUUID();
        db.saveTask(new Task(taskId, "sync-outbox", "", LocalDate.now().plusDays(2), 2));

        db.markEntityPendingSync("TASK", taskId, "{\"id\":\"" + taskId + "\"}", "device-sync-1");

        LocalSyncOutboxEntry queued = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> taskId.equals(entry.entityId()))
            .findFirst()
            .orElse(null);

        assertNotNull(queued);
        assertEquals("TASK", queued.entityType());
        assertEquals("UPSERT", queued.operation());
        assertEquals("PENDING", queued.status());
        assertEquals("PENDING_UPLOAD", querySingleString(
            "SELECT sync_status FROM tasks WHERE id = ?",
            taskId
        ));

        db.markSyncOutboxInFlight(queued.id());
        assertTrue(db.loadPendingSyncOutbox(1000).stream().noneMatch(entry -> queued.id().equals(entry.id())));

        db.markSyncOutboxFailed(queued.id(), "timeout");
        LocalSyncOutboxEntry failed = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> queued.id().equals(entry.id()))
            .findFirst()
            .orElse(null);
        assertNotNull(failed);
        assertEquals("FAILED", failed.status());
        assertEquals("timeout", failed.errorMessage());

        db.deleteSyncOutboxEntry(queued.id());
        assertTrue(db.loadPendingSyncOutbox(1000).stream().noneMatch(entry -> queued.id().equals(entry.id())));

        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("stageLocalOnlyWave1Entities переводит локальные задачи в pending upload")
    void testStageLocalOnlyWave1Entities() {
        String taskId = "stage-local-task-" + UUID.randomUUID();
        db.saveTask(new Task(taskId, "stage-local", "", LocalDate.now().plusDays(2), 2));

        int stagedCount = db.stageLocalOnlyWave1Entities();

        assertTrue(stagedCount >= 1);
        LocalSyncOutboxEntry queued = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> taskId.equals(entry.entityId()))
            .findFirst()
            .orElse(null);
        assertNotNull(queued);
        assertEquals("TASK", queued.entityType());
        assertEquals("UPSERT", queued.operation());
        assertEquals("PENDING_UPLOAD", querySingleString(
            "SELECT sync_status FROM tasks WHERE id = ?",
            taskId
        ));

        db.deleteSyncOutboxEntry(queued.id());
        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("stageLocalOnlyWave1Entities does not stage local chat history")
    void testStageLocalOnlyWave1EntitiesSkipsChatHistory() {
        ChatConversation conversation = db.createChatConversation("Sync chat");
        ChatMessage message = new ChatMessage(
            "chat-stage-message-" + UUID.randomUUID(),
            conversation.getId(),
            "user",
            "hello",
            1,
            "2026-03-23T00:00:00"
        );
        db.saveChatMessage(message);

        int firstStageCount = db.stageLocalOnlyWave1Entities();
        List<LocalSyncOutboxEntry> stagedEntries = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> conversation.getId().equals(entry.entityId()) || message.getId().equals(entry.entityId()))
            .toList();

        assertTrue(firstStageCount >= 0);
        assertTrue(stagedEntries.isEmpty());
        db.deleteChatConversation(conversation.getId());
    }

    @Test
    @DisplayName("saveGoal enqueue goal progress delta event when progress changes")
    void testSaveGoalEnqueuesProgressDelta() {
        String goalId = "goal-progress-sync-" + UUID.randomUUID();
        String now = "2026-03-23T00:00:00Z";
        Goal goal = new Goal(goalId, "Goal sync", "weekly", 5, 0, now, now);
        db.saveGoal(goal);

        goal.setProgress(3);
        goal.setUpdatedAt("2026-03-23T01:00:00Z");
        db.saveGoal(goal);

        LocalSyncOutboxEntry progressEntry = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> "GOAL_PROGRESS_ENTRY".equals(entry.entityType()))
            .findFirst()
            .orElse(null);

        assertNotNull(progressEntry);
        assertTrue(progressEntry.payloadJson().contains("\"goal_id\":\"" + goalId + "\""));
        assertTrue(progressEntry.payloadJson().contains("\"value_delta\":3"));

        db.deleteSyncOutboxEntry(progressEntry.id());
        db.deleteGoal(goalId);
    }

    @Test
    @DisplayName("loadPendingSyncOutbox discards orphan goal progress entries")
    void testLoadPendingSyncOutboxDiscardsOrphanGoalProgressEntries() {
        String goalId = "goal-progress-orphan-" + UUID.randomUUID();
        String now = "2026-03-23T00:00:00Z";
        Goal goal = new Goal(goalId, "Goal orphan", "weekly", 7, 0, now, now);
        db.saveGoal(goal);

        goal.setProgress(2);
        goal.setUpdatedAt("2026-03-23T02:00:00Z");
        db.saveGoal(goal);
        db.deleteGoal(goalId);
        db.stageLocalOnlyWave1Entities();

        List<LocalSyncOutboxEntry> entries = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> goalId.equals(entry.entityId())
                || entry.payloadJson().contains("\"goal_id\":\"" + goalId + "\""))
            .toList();

        assertTrue(entries.stream().noneMatch(entry -> "GOAL_PROGRESS_ENTRY".equals(entry.entityType())));
        assertTrue(entries.stream().anyMatch(entry ->
            "GOAL".equals(entry.entityType())
                && goalId.equals(entry.entityId())
                && "DELETE".equals(entry.operation())));

        entries.forEach(entry -> db.deleteSyncOutboxEntry(entry.id()));
    }

    @Test
    @DisplayName("softDeleteTaskForSync скрывает задачу из обычной загрузки и ставит DELETE в outbox")
    void testSoftDeleteTaskForSync() {
        String parentId = "sync-delete-parent-" + UUID.randomUUID();
        String childId = "sync-delete-child-" + UUID.randomUUID();
        db.saveTask(new Task(parentId, "parent", "", LocalDate.now().plusDays(3), 2));
        db.saveTask(new Task(childId, "child", "", LocalDate.now().plusDays(3), 1, parentId));

        db.softDeleteTaskForSync(parentId, "{\"id\":\"" + parentId + "\"}", "device-sync-2");

        assertNull(findTaskById(parentId));
        assertNull(findTaskById(childId));
        assertEquals(2, countRows(
            "SELECT COUNT(*) FROM tasks WHERE id IN (?, ?) AND deleted_at <> ''",
            parentId,
            childId
        ));

        List<LocalSyncOutboxEntry> outboxEntries = db.loadPendingSyncOutbox(1000).stream()
            .filter(entry -> "TASK".equals(entry.entityType()))
            .filter(entry -> parentId.equals(entry.entityId()) || childId.equals(entry.entityId()))
            .toList();
        assertEquals(2, outboxEntries.size());
        assertTrue(outboxEntries.stream().allMatch(entry -> "DELETE".equals(entry.operation())));

        for (LocalSyncOutboxEntry entry : outboxEntries) {
            db.deleteSyncOutboxEntry(entry.id());
        }
        db.deleteTasksBatch(List.of(parentId, childId));
    }

    private Task findTaskById(String taskId) {
        for (Task root : db.loadAllTasks()) {
            if (taskId.equals(root.getId())) {
                return root;
            }
            for (Task subtask : root.getSubtasks()) {
                if (taskId.equals(subtask.getId())) {
                    return subtask;
                }
            }
        }
        return null;
    }

    private static Connection openConnectionWithForeignKeys() throws SQLException {
        Connection connection = DriverManager.getConnection(DataPathManager.getDatabaseUrl());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
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

    private static int countRows(String sql, String... params) {
        try (Connection connection = openConnectionWithForeignKeys();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < params.length; index++) {
                statement.setString(index + 1, params[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new AssertionError("Failed to count rows", e);
        }
    }

    private static String querySingleString(String sql, String... params) {
        try (Connection connection = openConnectionWithForeignKeys();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < params.length; index++) {
                statement.setString(index + 1, params[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new AssertionError("Failed to query string value", e);
        }
    }

    private static String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private static void createAbortTrigger(String triggerName, String triggerSql) throws SQLException {
        try (Connection connection = openConnectionWithForeignKeys();
             Statement statement = connection.createStatement()) {
            statement.execute(triggerSql);
        }
    }

    private static void dropTrigger(String triggerName) {
        if (triggerName == null || triggerName.isBlank()) {
            return;
        }
        try (Connection connection = openConnectionWithForeignKeys();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + triggerName);
        } catch (SQLException ignored) {
            // best-effort cleanup in tests
        }
    }
}
