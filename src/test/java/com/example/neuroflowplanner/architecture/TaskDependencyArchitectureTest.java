package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDependencyArchitectureTest {

    private static final Path PRODUCTION_ROOT = Path.of("src/main/java/com/example/neuroflowplanner");
    private static final Pattern LEGACY_DEPENDENCY_CSV_PARSE = Pattern.compile(
            "(getDependsOn\\s*\\(\\s*\\)|depends[_A-Za-z0-9]*)\\s*\\.split\\s*\\(\\s*\"\\s*,\\s*\"\\s*\\)");

    @Test
    void productionCodeShouldNotParseDependencyCsvWithSplit() throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(PRODUCTION_ROOT)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(PRODUCTION_ROOT)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectMatches(path, LEGACY_DEPENDENCY_CSV_PARSE, violations));
        }

        assertTrue(violations.isEmpty(),
                () -> "Forbidden legacy dependency CSV parsing detected in production code:\n"
                        + String.join("\n", violations));
    }

    private void collectMatches(Path file, Pattern pattern, List<String> violations) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    violations.add(file + ":" + (i + 1) + " -> " + line.trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source file: " + file, e);
        }
    }
}
