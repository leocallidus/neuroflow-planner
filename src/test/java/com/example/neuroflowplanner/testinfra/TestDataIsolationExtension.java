package com.example.neuroflowplanner.testinfra;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.db.DatabaseMigrationRunner;
import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.DataPathManager;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class TestDataIsolationExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(TestDataIsolationExtension.class);
    private static final String STATE_KEY = "state";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        IsolatedTestData config = getConfig(context);
        if (config.scope() == TestDataIsolationScope.PER_CLASS) {
            activate(context, config);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        IsolatedTestData config = getConfig(context);
        if (config.scope() == TestDataIsolationScope.PER_CLASS) {
            deactivate(context);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        IsolatedTestData config = getConfig(context);
        if (config.scope() == TestDataIsolationScope.PER_METHOD) {
            activate(context, config);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        IsolatedTestData config = getConfig(context);
        if (config.scope() == TestDataIsolationScope.PER_METHOD) {
            deactivate(context);
        }
    }

    private static IsolatedTestData getConfig(ExtensionContext context) {
        IsolatedTestData config = context.getRequiredTestClass().getAnnotation(IsolatedTestData.class);
        if (config == null) {
            throw new ExtensionConfigurationException("@IsolatedTestData is required for TestDataIsolationExtension");
        }
        return config;
    }

    private static void activate(ExtensionContext context, IsolatedTestData config) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        if (store.get(STATE_KEY) != null) {
            return;
        }
        IsolationState state = setupIsolation(config);
        store.put(STATE_KEY, state);
        context.publishReportEntry("neuroflow.test.data.dir", state.isolatedDataDir().toString());
    }

    private static void deactivate(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getStore(NAMESPACE);
        IsolationState state = store.remove(STATE_KEY, IsolationState.class);
        if (state == null) {
            return;
        }
        tearDownIsolation(state);
    }

    private static IsolationState setupIsolation(IsolatedTestData config) throws Exception {
        Path dataDir = Files.createTempDirectory("neuroflow-test-data-").toAbsolutePath().normalize();
        Path dbPath = dataDir.resolve("neuroflow.db").toAbsolutePath().normalize();

        IsolationState state = new IsolationState(
            dataDir,
            System.getProperty(DataPathManager.PROP_DATA_DIR),
            System.getProperty(DataPathManager.PROP_DB_PATH),
            System.getProperty(DataPathManager.PROP_DB_URL)
        );

        try {
            System.setProperty(DataPathManager.PROP_DATA_DIR, dataDir.toString());
            System.setProperty(DataPathManager.PROP_DB_PATH, dbPath.toString());
            System.setProperty(DataPathManager.PROP_DB_URL, "jdbc:sqlite:" + dbPath);
            resetSingletons();

            if (config.migrateSchema()) {
                DatabaseMigrationRunner.migrate();
            }
            return state;
        } catch (Exception setupError) {
            try {
                tearDownIsolation(state);
            } catch (Exception cleanupError) {
                setupError.addSuppressed(cleanupError);
            }
            throw setupError;
        }
    }

    private static void tearDownIsolation(IsolationState state) throws Exception {
        try {
            resetSingletons();
            deleteRecursively(state.isolatedDataDir());
        } finally {
            restoreProperty(DataPathManager.PROP_DATA_DIR, state.previousDataDir());
            restoreProperty(DataPathManager.PROP_DB_PATH, state.previousDbPath());
            restoreProperty(DataPathManager.PROP_DB_URL, state.previousDbUrl());
            resetSingletons();
        }
    }

    private static void resetSingletons() {
        DatabaseManager.resetForTesting();
        NotesService.resetForTesting();
        ConfigManager.resetForTesting();
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (var walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private record IsolationState(
        Path isolatedDataDir,
        String previousDataDir,
        String previousDbPath,
        String previousDbUrl
    ) {
    }
}
