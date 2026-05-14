package com.example.neuroflowplanner.ui.interaction;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutConflictGuardTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final Path SMART_NOTES_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/smartnotes/LegacySmartNotesDialog.java"
    );
    private static final Pattern REGISTER_SHORTCUT_PATTERN = Pattern.compile(
        "registerShortcutBinding\\(\\s*\"([^\"]+)\"\\s*,"
    );
    private static final Set<String> REQUIRED_SHORTCUTS = Set.of(
        "CTRL+K",
        "CTRL+F",
        "CTRL+Z",
        "CTRL+SHIFT+Z",
        "CTRL+N"
    );

    @Test
    void mainViewShortcutBindingsHaveNoConflictsAndCoverRequiredSet() throws IOException {
        validateShortcutBindings("mainview", MAIN_VIEW_FILE);
    }

    @Test
    void smartNotesShortcutBindingsHaveNoConflictsAndCoverRequiredSet() throws IOException {
        validateShortcutBindings("smartnotes", SMART_NOTES_FILE);
    }

    private void validateShortcutBindings(String scope, Path sourceFile) throws IOException {
        List<String> bindings = parseShortcutBindings(sourceFile);
        assertEquals(
            bindings.size(),
            new HashSet<>(bindings).size(),
            () -> "Duplicate shortcuts detected in " + sourceFile + ": " + bindings
        );
        assertTrue(
            bindings.containsAll(REQUIRED_SHORTCUTS),
            () -> "Required shortcuts are missing in " + sourceFile + ", found: " + bindings
        );

        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        List<String> rejected = new ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            String shortcut = bindings.get(i);
            ShortcutRegistry.RegistrationResult result = registry.register(
                new ShortcutRegistry.ShortcutBinding(
                    shortcut,
                    ShortcutRegistry.ShortcutContext.GLOBAL,
                    scope + ".action." + i,
                    false,
                    false
                )
            );
            if (!result.accepted()) {
                rejected.add(shortcut + " -> " + result.reason());
            }
        }
        assertTrue(rejected.isEmpty(), () -> "Shortcut conflicts detected for " + sourceFile + ": " + rejected);
        assertTrue(
            registry.runStartupConflictCheck(scope).isEmpty(),
            () -> "Startup shortcut conflict check must pass for " + sourceFile
        );
    }

    private List<String> parseShortcutBindings(Path sourceFile) throws IOException {
        if (!Files.exists(sourceFile)) {
            return List.of();
        }
        List<String> shortcuts = new ArrayList<>();
        for (String line : Files.readAllLines(sourceFile)) {
            Matcher matcher = REGISTER_SHORTCUT_PATTERN.matcher(line);
            if (matcher.find()) {
                shortcuts.add(matcher.group(1).trim().toUpperCase());
            }
        }
        return List.copyOf(new LinkedHashSet<>(shortcuts));
    }
}
