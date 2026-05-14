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

class AiJsonArchitectureTest {

    private static final List<Path> TARGET_ROOTS = List.of(
            Path.of("src/main/java/com/example/neuroflowplanner/ai"),
            Path.of("src/main/java/com/example/neuroflowplanner/service"),
            Path.of("src/main/java/com/example/neuroflowplanner/ui"));

    @Test
    void aiServiceUiShouldNotUseLegacyAiApiJsonHelpers() throws IOException {
        Pattern legacyHelpers = Pattern.compile(
                "AiApiUtils\\.(extractJsonField|parseJsonMap|extractContent|cleanJson)\\s*\\(");
        List<String> violations = scanViolations(legacyHelpers);
        assertTrue(violations.isEmpty(),
                () -> "Forbidden legacy AI JSON helpers detected:\n" + String.join("\n", violations));
    }

    @Test
    void aiServiceUiShouldNotUseStringIndexOfWithEscapedJsonQuotes() throws IOException {
        Pattern jsonIndexOf = Pattern.compile("indexOf\\(\\s*\"\\\\\"");
        List<String> violations = scanViolations(jsonIndexOf);
        assertTrue(violations.isEmpty(),
                () -> "Potential manual JSON parsing via indexOf(\\\"...) detected:\n" + String.join("\n", violations));
    }

    private List<String> scanViolations(Pattern forbiddenPattern) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : TARGET_ROOTS) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> collectMatches(path, forbiddenPattern, violations));
            }
        }
        return violations;
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
