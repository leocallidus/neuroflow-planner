package com.example.neuroflowplanner.db;

import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DatabaseMigrationRunner {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DatabaseMigrationRunner.class);
    private static final String MIGRATIONS_LOCATION = "classpath:db/migration";
    private static final String FLYWAY_HISTORY_TABLE = "flyway_schema_history";
    private static final String BASELINE_VERSION = "1";
    // Transitional safety switch: keep true for the first Flyway rollout release only.
    private static final String BASELINE_ON_MIGRATE_PROPERTY = "neuroflow.flyway.baselineOnMigrate";
    private static final boolean BASELINE_ON_MIGRATE_DEFAULT = true;

    // Legacy pre-check validates V1-compatible schema before baselineing.
    // Newer tables (e.g. task_dependencies from V2) are created by Flyway after baseline.
    private static final Set<String> REQUIRED_LEGACY_TABLES = new LinkedHashSet<>(List.of(
        "tasks",
        "task_templates",
        "mood_entries",
        "chat_conversations",
        "chat_messages",
        "time_sessions",
        "goals"
    ));
    // Stage-7: `depends_on` is optional because V4 drops it after backfill rollout.
    private static final Set<String> REQUIRED_LEGACY_TASK_COLUMNS = new LinkedHashSet<>(List.of(
        "parent_id",
        "tags",
        "recurrence",
        "archived",
        "tracked_minutes",
        "start_date",
        "completed",
        "completed_date"
    ));
    private static final Set<String> REQUIRED_LEGACY_INDEXES = new LinkedHashSet<>(List.of(
        "idx_chat_messages_conv",
        "idx_time_sessions_started"
    ));

    private DatabaseMigrationRunner() {
    }

    public static void migrate() {
        migrate(DataPathManager.getDatabaseUrl(), resolveDefaultLocations());
    }

    static void migrate(String dbUrl) {
        migrate(dbUrl, MIGRATIONS_LOCATION);
    }

    static void migrate(String dbUrl, String... locations) {
        if (locations == null || locations.length == 0) {
            throw new IllegalArgumentException("At least one Flyway location must be provided");
        }

        MigrationPolicy policy = MigrationPolicy.STANDARD;
        try {
            policy = resolveMigrationPolicy(dbUrl);
            Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, null, null)
                .locations(locations)
                .baselineOnMigrate(policy.baselineOnMigrate())
                .baselineVersion(BASELINE_VERSION)
                .validateOnMigrate(true)
                .load();

            MigrateResult result = flyway.migrate();
            LOG.info(
                "db.migration.completed",
                "dbUrl", dbUrl,
                "location", String.join(",", locations),
                "mode", policy.name(),
                "baselineVersion", BASELINE_VERSION,
                "migrationsExecuted", result.migrationsExecuted
            );
        } catch (Exception e) {
            LOG.error(
                "db.migration.failed",
                e,
                "dbUrl", dbUrl,
                "location", String.join(",", locations),
                "mode", policy.name(),
                "baselineVersion", BASELINE_VERSION
            );
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    private static String[] resolveDefaultLocations() {
        String fileSystemLocation = resolveFileSystemMigrationLocation();
        if (fileSystemLocation != null) {
            return new String[] { fileSystemLocation };
        }
        return new String[] { MIGRATIONS_LOCATION };
    }

    private static String resolveFileSystemMigrationLocation() {
        URL migrationDir = DatabaseMigrationRunner.class.getResource("/db/migration");
        if (migrationDir == null) {
            return null;
        }
        if (!"file".equalsIgnoreCase(migrationDir.getProtocol())) {
            return null;
        }
        try {
            URI uri = migrationDir.toURI();
            Path path = Path.of(uri);
            if (!Files.isDirectory(path)) {
                return null;
            }
            return "filesystem:" + path.toAbsolutePath();
        } catch (URISyntaxException e) {
            LOG.warning("db.migration.location.filesystem.resolve.failed", "locationUrl", migrationDir.toString());
            return null;
        }
    }

    private static MigrationPolicy resolveMigrationPolicy(String dbUrl) throws SQLException {
        try (Connection connection = DriverManager.getConnection(dbUrl)) {
            boolean historyExists = tableExists(connection, FLYWAY_HISTORY_TABLE);
            if (historyExists) {
                return MigrationPolicy.STANDARD;
            }

            if (isUserSchemaEmpty(connection)) {
                return MigrationPolicy.STANDARD;
            }

            boolean baselineEnabled = isBaselineOnMigrateEnabled();
            if (!baselineEnabled) {
                throw new IllegalStateException(
                    "Legacy schema detected without flyway history while baseline mode is disabled");
            }

            runLegacySchemaPreCheck(connection);
            LOG.info(
                "db.migration.legacy.precheck.passed",
                "dbUrl", dbUrl,
                "baselineOnMigrateProperty", BASELINE_ON_MIGRATE_PROPERTY,
                "baselineVersion", BASELINE_VERSION
            );
            return MigrationPolicy.LEGACY_BASELINE;
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean isUserSchemaEmpty(Connection connection) throws SQLException {
        String sql = """
            SELECT COUNT(1)
            FROM sqlite_master
            WHERE type IN ('table', 'index', 'view', 'trigger')
              AND name NOT LIKE 'sqlite_%'
              AND name <> ?
        """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, FLYWAY_HISTORY_TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                return !resultSet.next() || resultSet.getInt(1) == 0;
            }
        }
    }

    private static boolean isBaselineOnMigrateEnabled() {
        String value = System.getProperty(
            BASELINE_ON_MIGRATE_PROPERTY,
            String.valueOf(BASELINE_ON_MIGRATE_DEFAULT)
        );
        return Boolean.parseBoolean(value);
    }

    private static void runLegacySchemaPreCheck(Connection connection) throws SQLException {
        Set<String> tables = loadSchemaObjects(connection, "table");
        requireAll("tables", REQUIRED_LEGACY_TABLES, tables);

        Set<String> taskColumns = loadTaskColumns(connection);
        requireAll("tasks.columns", REQUIRED_LEGACY_TASK_COLUMNS, taskColumns);

        Set<String> indexes = loadSchemaObjects(connection, "index");
        requireAll("indexes", REQUIRED_LEGACY_INDEXES, indexes);
    }

    private static Set<String> loadSchemaObjects(Connection connection, String objectType) throws SQLException {
        String sql = """
            SELECT name
            FROM sqlite_master
            WHERE type = ?
              AND name NOT LIKE 'sqlite_%'
        """;
        Set<String> names = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, objectType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    names.add(resultSet.getString("name"));
                }
            }
        }
        return names;
    }

    private static Set<String> loadTaskColumns(Connection connection) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(tasks)")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private static void requireAll(String section, Set<String> required, Set<String> actual) {
        Set<String> missing = new LinkedHashSet<>(required);
        missing.removeAll(actual);
        if (!missing.isEmpty()) {
            LOG.warning(
                "db.migration.legacy.precheck.failed",
                "section", section,
                "missing", String.join(",", missing)
            );
            throw new IllegalStateException(
                "Legacy schema pre-check failed for " + section + ": missing " + String.join(",", missing));
        }
    }

    private enum MigrationPolicy {
        STANDARD(false),
        LEGACY_BASELINE(true);

        private final boolean baselineOnMigrate;

        MigrationPolicy(boolean baselineOnMigrate) {
            this.baselineOnMigrate = baselineOnMigrate;
        }

        boolean baselineOnMigrate() {
            return baselineOnMigrate;
        }
    }
}
