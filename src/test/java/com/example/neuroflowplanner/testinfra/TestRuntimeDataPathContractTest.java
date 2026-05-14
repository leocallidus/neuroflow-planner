package com.example.neuroflowplanner.testinfra;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.util.DataPathManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Runtime Data Path Contract")
class TestRuntimeDataPathContractTest {
    private static final Path PRODUCTION_DEFAULT_DIR = Path.of("neuroflow_data").toAbsolutePath().normalize();
    private static final Path TARGET_TEST_DATA_PREFIX = Path.of("target", "test-data").toAbsolutePath().normalize();

    @Test
    @DisplayName("test runtime path must not point to production default directory")
    void testRuntimePathIsNotProductionDefault() {
        DatabaseManager.resetForTesting();
        NotesService.resetForTesting();

        Path runtimeDataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        assertNotEquals(
            PRODUCTION_DEFAULT_DIR,
            runtimeDataDir,
            "Test runtime data dir must not match production default directory"
        );
    }

    @Test
    @DisplayName("test runtime path must resolve inside target/test-data")
    void testRuntimePathIsInsideTargetTestData() {
        DatabaseManager.resetForTesting();
        NotesService.resetForTesting();

        Path runtimeDataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        assertTrue(
            runtimeDataDir.startsWith(TARGET_TEST_DATA_PREFIX),
            "Expected test runtime data dir under target/test-data, actual: " + runtimeDataDir
        );
    }
}
