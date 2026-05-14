package com.example.neuroflowplanner.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Production Logging Safety Tests")
class ProductionLoggingSafetyTest {

    private static final Pattern FORBIDDEN_ERROR_OUTPUT = Pattern.compile(
        "printStackTrace\\s*\\(|System\\.err\\.println\\s*\\("
    );

    @Test
    @DisplayName("Production code does not use printStackTrace or System.err.println")
    void productionCodeDoesNotUseForbiddenErrorOutput() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path mainJava = projectRoot.resolve("src/main/java");
        List<String> violations = new ArrayList<>();

        try (Stream<Path> files = Files.walk(mainJava)) {
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectViolations(projectRoot, path, violations));
        }

        assertTrue(
            violations.isEmpty(),
            () -> "Forbidden error output detected:\n" + String.join("\n", violations)
        );
    }

    private static void collectViolations(Path projectRoot, Path file, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (FORBIDDEN_ERROR_OUTPUT.matcher(line).find()) {
                    Path relative = projectRoot.relativize(file);
                    violations.add(relative + ":" + (i + 1) + " -> " + line.trim());
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
