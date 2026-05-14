package com.example.neuroflowplanner.testinfra;

import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.util.DataPathManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test Data Isolation Extension")
@IsolatedTestData
class TestDataIsolationExtensionTest {

    @Test
    @DisplayName("isolated data dir is wired through DataPathManager and system properties")
    void dataDirIsolationIsActive() {
        Path configured = Path.of(System.getProperty(DataPathManager.PROP_DATA_DIR))
            .toAbsolutePath()
            .normalize();
        Path actual = DataPathManager.getDataDirectory().toAbsolutePath().normalize();

        assertEquals(configured, actual);
        assertTrue(actual.getFileName().toString().startsWith("neuroflow-test-data-"));
        assertTrue(DataPathManager.getDatabasePath().toAbsolutePath().normalize().startsWith(actual));
    }

    @Test
    @DisplayName("notes are written only into isolated notes directory")
    void notesWriteIntoIsolatedDirectory() throws IOException {
        String title = "harness-note-" + UUID.randomUUID();
        NotesService service = NotesService.getInstance();
        service.saveNote(title, "isolation-check");

        Path dataDir = DataPathManager.getDataDirectory().toAbsolutePath().normalize();
        Path notePath = dataDir.resolve("notes").resolve(title + ".md");

        assertTrue(Files.exists(notePath));
        assertTrue(Files.readString(notePath).contains("isolation-check"));
    }
}
