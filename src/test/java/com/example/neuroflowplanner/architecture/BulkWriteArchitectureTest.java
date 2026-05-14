package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkWriteArchitectureTest {

    private static final List<Path> TARGET_ROOTS = List.of(
        Path.of("src/main/java/com/example/neuroflowplanner/ui/mainview"),
        Path.of("src/main/java/com/example/neuroflowplanner/service")
    );

    private static final Pattern FORBIDDEN_WRITE_CALL = Pattern.compile("\\b(saveTask|deleteTask)\\s*\\(");

    @Test
    void bulkFlowsShouldNotUsePerItemSaveOrDeleteLoops() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : TARGET_ROOTS) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scanFileForForbiddenWriteLoops(path, violations));
            }
        }

        assertTrue(
            violations.isEmpty(),
            () -> "Forbidden per-item write loops detected in bulk flow:\n" + String.join("\n", violations)
        );
    }

    private void scanFileForForbiddenWriteLoops(Path file, List<String> violations) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read source file: " + file, e);
        }

        int index = 0;
        while (index < content.length()) {
            int forIndex = findNextForLoop(content, index);
            if (forIndex < 0) {
                break;
            }
            int headerEnd = findForHeaderEnd(content, forIndex);
            if (headerEnd < 0) {
                break;
            }
            int blockStart = skipWhitespace(content, headerEnd + 1);
            if (blockStart < 0 || blockStart >= content.length() || content.charAt(blockStart) != '{') {
                index = headerEnd + 1;
                continue;
            }

            int blockEnd = findMatchingBrace(content, blockStart);
            if (blockEnd < 0) {
                break;
            }

            String block = content.substring(blockStart, blockEnd + 1);
            if (FORBIDDEN_WRITE_CALL.matcher(block).find()) {
                int line = lineNumberAt(content, forIndex);
                String snippet = content.substring(forIndex, Math.min(blockEnd + 1, forIndex + 220))
                    .replace('\n', ' ')
                    .trim();
                violations.add(file + ":" + line + " -> " + snippet);
            }
            index = blockEnd + 1;
        }
    }

    private int findNextForLoop(String content, int fromIndex) {
        int index = fromIndex;
        while (index >= 0 && index < content.length()) {
            int candidate = content.indexOf("for", index);
            if (candidate < 0) {
                return -1;
            }

            boolean leftBoundary = candidate == 0 || !Character.isJavaIdentifierPart(content.charAt(candidate - 1));
            int afterKeyword = candidate + 3;
            boolean rightBoundary = afterKeyword >= content.length() || !Character.isJavaIdentifierPart(content.charAt(afterKeyword));
            if (leftBoundary && rightBoundary) {
                int parenIndex = skipWhitespace(content, afterKeyword);
                if (parenIndex >= 0 && parenIndex < content.length() && content.charAt(parenIndex) == '(') {
                    return candidate;
                }
            }
            index = candidate + 3;
        }
        return -1;
    }

    private int findForHeaderEnd(String content, int forIndex) {
        int openParen = skipWhitespace(content, forIndex + 3);
        if (openParen < 0 || openParen >= content.length() || content.charAt(openParen) != '(') {
            return -1;
        }
        int depth = 0;
        for (int i = openParen; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findMatchingBrace(String content, int openBraceIndex) {
        int depth = 0;
        for (int i = openBraceIndex; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int skipWhitespace(String content, int fromIndex) {
        for (int i = fromIndex; i < content.length(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private int lineNumberAt(String content, int index) {
        int line = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
