package com.example.neuroflowplanner.testinfra;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.db.DatabaseMigrationRunner;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.util.DataPathManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Target Test Data Smoke")
class TestTargetTestDataSmokeTest {
    private static final Path TARGET_TEST_DATA_PREFIX = Path.of("target", "test-data").toAbsolutePath().normalize();
    private static final Path PRODUCTION_DB_PATH = Path.of("neuroflow_data", "neuroflow.db").toAbsolutePath().normalize();
    private static final Path PRODUCTION_NOTES_DIR = Path.of("neuroflow_data", "notes").toAbsolutePath().normalize();

    @Test
    @DisplayName("test runtime creates and writes data only under target/test-data")
    void writesAreScopedToTargetTestData() throws Exception {
        String productionDbHashBefore = hashFileOrMissing(PRODUCTION_DB_PATH);
        String productionNotesHashBefore = hashDirectoryOrMissing(PRODUCTION_NOTES_DIR);

        DatabaseManager.resetForTesting();
        NotesService.resetForTesting();
        DatabaseMigrationRunner.migrate();

        Path runtimeDataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        assertTrue(
            runtimeDataDir.startsWith(TARGET_TEST_DATA_PREFIX),
            "Expected test runtime data dir under target/test-data, actual: " + runtimeDataDir
        );

        DatabaseManager databaseManager = DatabaseManager.getInstance();
        String taskId = "stage5-smoke-task-" + UUID.randomUUID();
        Task task = new Task(taskId, "stage5 smoke", "runtime path check", LocalDate.now().plusDays(1), 1);
        databaseManager.saveTask(task);
        databaseManager.deleteTask(taskId);

        NotesService notesService = NotesService.getInstance();
        String noteTitle = "stage5-smoke-note-" + UUID.randomUUID();
        notesService.saveNote(noteTitle, "smoke content");
        assertEquals("smoke content", notesService.loadNoteContent(noteTitle));
        notesService.deleteNote(noteTitle);

        Path runtimeDbPath = DataPathManager.getDatabasePath().toAbsolutePath().normalize();
        Path runtimeNotesDir = runtimeDataDir.resolve("notes").toAbsolutePath().normalize();
        assertTrue(runtimeDbPath.startsWith(TARGET_TEST_DATA_PREFIX));
        assertTrue(runtimeNotesDir.startsWith(TARGET_TEST_DATA_PREFIX));
        assertTrue(Files.exists(runtimeDbPath), "Runtime DB must exist in test data directory");
        assertTrue(Files.isDirectory(runtimeNotesDir), "Runtime notes directory must exist in test data directory");

        String productionDbHashAfter = hashFileOrMissing(PRODUCTION_DB_PATH);
        String productionNotesHashAfter = hashDirectoryOrMissing(PRODUCTION_NOTES_DIR);
        assertEquals(productionDbHashBefore, productionDbHashAfter, "Production DB file must not change during tests");
        assertEquals(productionNotesHashBefore, productionNotesHashAfter, "Production notes files must not change during tests");
    }

    private static String hashDirectoryOrMissing(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return "MISSING";
        }

        List<Path> files;
        try (var walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path file : files) {
            digest.update(directory.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(hashFileBytes(file));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashFileOrMissing(Path file) throws Exception {
        if (!Files.exists(file)) {
            return "MISSING";
        }
        return HexFormat.of().formatHex(hashFileBytes(file));
    }

    private static byte[] hashFileBytes(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to hash file: " + file, ex);
        }
        return digest.digest();
    }
}
