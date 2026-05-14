package com.example.neuroflowplanner.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseMigrationRunner Tests")
class DatabaseMigrationRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("new DB -> migrate -> schema and flyway history created")
    void migrateNewDatabaseCreatesSchema() throws SQLException {
        Path dbPath = tempDir.resolve("new.db");
        String dbUrl = sqliteUrl(dbPath);

        DatabaseMigrationRunner.migrate(dbUrl, defaultMigrationLocation());

        assertSchemaTablesExist(dbUrl);
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '1' AND type = 'SQL' AND script = 'V1__initial_schema.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '2' AND type = 'SQL' AND script = 'V2__task_dependencies.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '3' AND type = 'SQL' AND script = 'V3__backfill_task_dependencies.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '4' AND type = 'SQL' AND script = 'V4__drop_tasks_depends_on.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '9' AND type = 'SQL' AND script = 'V9__local_sync_metadata.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '10' AND type = 'SQL' AND script = 'V10__local_sync_state.sql' AND success = 1
        """));
        assertFalse(taskColumnExists(dbUrl, "depends_on"));
        assertTrue(tableColumnExists(dbUrl, "tasks", "sync_status"));
        assertTrue(tableColumnExists(dbUrl, "tasks", "deleted_at"));
        assertTrue(tableColumnExists(dbUrl, "task_dependencies", "sync_status"));
        assertTrue(tableColumnExists(dbUrl, "time_sessions", "updated_at"));
        assertTrue(tableColumnExists(dbUrl, "task_templates", "server_version"));
        assertTrue(tableColumnExists(dbUrl, "mood_entries", "last_synced_at"));
        assertTrue(tableColumnExists(dbUrl, "goals", "sync_status"));
    }

    @Test
    @DisplayName("legacy DB without history -> baseline and upgrade succeed")
    void migrateLegacyDatabaseRunsBaselineAndUpgrade() throws Exception {
        Path dbPath = tempDir.resolve("legacy.db");
        String dbUrl = sqliteUrl(dbPath);
        Path customMigrations = tempDir.resolve("custom-migrations");
        Files.createDirectories(customMigrations);

        Files.writeString(
            customMigrations.resolve("V1__initial_schema.sql"),
            Files.readString(Path.of("src/main/resources/db/migration/V1__initial_schema.sql"))
        );
        Files.writeString(
            customMigrations.resolve("V2__tasks_marker_column.sql"),
            "ALTER TABLE tasks ADD COLUMN stage5_marker TEXT DEFAULT '';"
        );

        // Prepare legacy schema (V1 objects only), then remove history table.
        Flyway.configure()
            .dataSource(dbUrl, null, null)
            .locations("filesystem:" + customMigrations.toAbsolutePath())
            .target("1")
            .load()
            .migrate();
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE flyway_schema_history");
        }

        DatabaseMigrationRunner.migrate(dbUrl, "filesystem:" + customMigrations.toAbsolutePath());

        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*) FROM flyway_schema_history
            WHERE version = '1' AND type = 'BASELINE' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*) FROM flyway_schema_history
            WHERE version = '2' AND type = 'SQL' AND script = 'V2__tasks_marker_column.sql' AND success = 1
        """));
        assertTrue(taskColumnExists(dbUrl, "stage5_marker"));
    }

    @Test
    @DisplayName("broken migration -> startup fails with clear initialization error")
    void migrateFailsOnBrokenSqlMigration() throws Exception {
        Path dbPath = tempDir.resolve("broken.db");
        String dbUrl = sqliteUrl(dbPath);
        Path customMigrations = tempDir.resolve("broken-migrations");
        Files.createDirectories(customMigrations);

        Files.writeString(
            customMigrations.resolve("V1__initial_schema.sql"),
            Files.readString(Path.of("src/main/resources/db/migration/V1__initial_schema.sql"))
        );
        Files.writeString(
            customMigrations.resolve("V2__broken.sql"),
            "CREAT TABLE invalid_sql (id INTEGER PRIMARY KEY);"
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> DatabaseMigrationRunner.migrate(dbUrl, "filesystem:" + customMigrations.toAbsolutePath())
        );
        assertTrue(exception.getMessage().contains("Database migration failed"));
        assertNotNull(exception.getCause());
        String causeMessage = exception.getCause().getMessage();
        assertTrue(causeMessage != null && !causeMessage.isBlank());
        assertTrue(causeMessage.contains("V2__broken.sql") || causeMessage.toLowerCase().contains("syntax"));
    }

    @Test
    @DisplayName("legacy schema drift -> fail-fast with explicit pre-check error")
    void migrateFailsWhenLegacySchemaIsIncompatible() throws Exception {
        Path dbPath = tempDir.resolve("legacy-drift.db");
        String dbUrl = sqliteUrl(dbPath);

        Flyway.configure()
            .dataSource(dbUrl, null, null)
            .locations(defaultMigrationLocation())
            .target("1")
            .load()
            .migrate();
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE flyway_schema_history");
            statement.execute("DROP TABLE tasks");
            statement.execute("""
                CREATE TABLE tasks (
                    id TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT,
                    deadline TEXT NOT NULL,
                    complexity INTEGER NOT NULL,
                    smart_priority REAL DEFAULT 0,
                    ai_insight TEXT,
                    parent_id TEXT,
                    tags TEXT DEFAULT '',
                    FOREIGN KEY (parent_id) REFERENCES tasks(id) ON DELETE CASCADE
                )
            """);
        }

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> DatabaseMigrationRunner.migrate(dbUrl, defaultMigrationLocation())
        );
        assertTrue(exception.getMessage().contains("Database migration failed"));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("Legacy schema pre-check failed"));
    }

    @Test
    @DisplayName("V3 backfill imports valid edges and logs skipped legacy tokens")
    void migrateBackfillsTaskDependenciesFromLegacyDependsOn() throws Exception {
        Path dbPath = tempDir.resolve("backfill.db");
        String dbUrl = sqliteUrl(dbPath);
        String location = defaultMigrationLocation();

        // Prepare DB in V1 state, then add legacy dependency CSV values.
        Flyway.configure()
            .dataSource(dbUrl, null, null)
            .locations(location)
            .target("1")
            .load()
            .migrate();
        try (Connection connection = DriverManager.getConnection(dbUrl)) {
            insertLegacyTask(connection, "task-A", "task-B, task-C, , task-B,missing-id,task-A");
            insertLegacyTask(connection, "task-B", "");
            insertLegacyTask(connection, "task-C", "task-B");
            insertLegacyTask(connection, "task-D", ", ,task-C,,");
        }

        DatabaseMigrationRunner.migrate(dbUrl, location);

        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '2' AND type = 'SQL' AND script = 'V2__task_dependencies.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '3' AND type = 'SQL' AND script = 'V3__backfill_task_dependencies.sql' AND success = 1
        """));
        assertEquals(1, count(dbUrl, """
            SELECT COUNT(*)
            FROM flyway_schema_history
            WHERE version = '4' AND type = 'SQL' AND script = 'V4__drop_tasks_depends_on.sql' AND success = 1
        """));
        assertFalse(taskColumnExists(dbUrl, "depends_on"));

        assertEquals(4, count(dbUrl, "SELECT COUNT(*) FROM task_dependencies"));
        assertEdgeExists(dbUrl, "task-A", "task-B");
        assertEdgeExists(dbUrl, "task-A", "task-C");
        assertEdgeExists(dbUrl, "task-C", "task-B");
        assertEdgeExists(dbUrl, "task-D", "task-C");

        assertEquals(0, count(dbUrl, """
            SELECT COUNT(*)
            FROM task_dependencies
            WHERE dependent_task_id = 'task-A' AND blocker_task_id IN ('task-A', 'missing-id')
        """));

        assertTrue(count(dbUrl, """
            SELECT COUNT(*) FROM task_dependency_backfill_log
            WHERE reason = 'missing_blocker'
        """) >= 1);
        assertTrue(count(dbUrl, """
            SELECT COUNT(*) FROM task_dependency_backfill_log
            WHERE reason = 'self_loop'
        """) >= 1);
        assertTrue(count(dbUrl, """
            SELECT COUNT(*) FROM task_dependency_backfill_log
            WHERE reason = 'empty_token'
        """) >= 1);
        assertTrue(count(dbUrl, """
            SELECT COUNT(*) FROM task_dependency_backfill_log
            WHERE reason LIKE 'duplicate_edge(%'
        """) >= 1);
    }

    private static void assertSchemaTablesExist(String dbUrl) throws SQLException {
        Set<String> expected = Set.of(
            "tasks",
            "task_dependencies",
            "task_dependency_backfill_log",
            "task_templates",
            "mood_entries",
            "chat_conversations",
            "chat_messages",
            "time_sessions",
            "goals",
            "sync_state",
            "sync_outbox",
            "account_link",
            "device_identity",
            "flyway_schema_history"
        );
        Set<String> actual = new HashSet<>();
        String sql = "SELECT name FROM sqlite_master WHERE type = 'table'";
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                actual.add(resultSet.getString("name"));
            }
        }
        assertTrue(actual.containsAll(expected), "Missing tables: " + difference(expected, actual));
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static boolean taskColumnExists(String dbUrl, String columnName) throws SQLException {
        return tableColumnExists(dbUrl, "tasks", columnName);
    }

    private static boolean tableColumnExists(String dbUrl, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertEdgeExists(String dbUrl, String dependentTaskId, String blockerTaskId) throws SQLException {
        int found = count(dbUrl, """
            SELECT COUNT(*)
            FROM task_dependencies
            WHERE dependent_task_id = ? AND blocker_task_id = ?
        """, dependentTaskId, blockerTaskId);
        assertEquals(1, found, "Expected edge " + dependentTaskId + " -> " + blockerTaskId);
    }

    private static int count(String dbUrl, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(dbUrl);
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static int count(String dbUrl, String sql, String firstParam, String secondParam) throws SQLException {
        try (Connection connection = DriverManager.getConnection(dbUrl);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, firstParam);
            statement.setString(2, secondParam);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static void insertLegacyTask(Connection connection, String id, String dependsOn) throws SQLException {
        String sql = """
            INSERT INTO tasks (id, title, description, deadline, complexity, depends_on)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, "legacy-" + id);
            statement.setString(3, "seed");
            statement.setString(4, "2026-12-31");
            statement.setInt(5, 3);
            statement.setString(6, dependsOn);
            statement.executeUpdate();
        }
    }

    private static String sqliteUrl(Path dbPath) {
        return "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    private static String defaultMigrationLocation() {
        return "filesystem:" + Path.of("src/main/resources/db/migration").toAbsolutePath();
    }
}
