package com.example.neuroflowplanner.testinfra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test Source Hardcoded Data Path Guard")
class TestSourceHardcodedDataPathGuardTest {
    private static final String FORBIDDEN_DB_PATH = "neuroflow_data" + "/neuroflow.db";
    private static final String FORBIDDEN_NOTES_PATH = "neuroflow_data" + "/notes";

    @Test
    @DisplayName("test sources must not use production data paths in executable code")
    void noHardcodedProductionDataPathsInExecutableCode() throws IOException {
        Path testSourcesRoot = Path.of("src/test/java");
        List<String> violations = new ArrayList<>();

        try (var walk = Files.walk(testSourcesRoot)) {
            walk.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanFile(path, violations));
        }

        assertTrue(
            violations.isEmpty(),
            "Forbidden hardcoded production data paths found:\n" + String.join("\n", violations)
        );
    }

    private static void scanFile(Path path, List<String> violations) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read test source file: " + path, ex);
        }

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.stripLeading();
            if (isCommentLine(trimmed)) {
                continue;
            }
            if (line.contains(FORBIDDEN_DB_PATH) || line.contains(FORBIDDEN_NOTES_PATH)) {
                violations.add(path + ":" + (index + 1) + ": " + trimmed);
            }
        }
    }

    private static boolean isCommentLine(String trimmedLine) {
        return trimmedLine.startsWith("//")
            || trimmedLine.startsWith("/*")
            || trimmedLine.startsWith("*")
            || trimmedLine.startsWith("*/");
    }
}
