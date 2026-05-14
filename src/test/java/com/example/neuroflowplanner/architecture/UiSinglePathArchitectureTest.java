package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSinglePathArchitectureTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");
    private static final Path CONFIG_MANAGER_FILE = SOURCE_ROOT.resolve(
        "com/example/neuroflowplanner/util/ConfigManager.java"
    );
    private static final Path MAIN_VIEW_FILE = SOURCE_ROOT.resolve(
        "com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final List<String> REMOVED_UX_ROLLOUT_KEYS = List.of(
        "ux.undo.enabled",
        "ux.search.global.enabled",
        "ux.commandPalette.enabled",
        "ux.shortcuts.enabled",
        "ux.layout.adaptive.enabled",
        "ux.layout.obsidianInspired.enabled",
        "ux.layout.compact.autoCollapseRightPanel",
        "ux.sidebar.v2.enabled",
        "ux.sidebar.filter.enabled",
        "ux.sidebar.favorites.enabled",
        "ux.sidebar.recent.enabled"
    );

    @Test
    void removedUxRolloutKeysMustOnlyExistInMigrationAllowlist() throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> pathStream = Files.walk(SOURCE_ROOT)) {
            javaFiles = pathStream
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .toList();
        }
        String configManagerContent = Files.readString(CONFIG_MANAGER_FILE);

        assertTrue(
            configManagerContent.contains("LEGACY_UX_ROLLOUT_KEYS"),
            "ConfigManager must keep explicit legacy UX rollout key allowlist for migration"
        );
        assertTrue(
            configManagerContent.contains("normalizeLegacyUxRolloutProperties()"),
            "ConfigManager must invoke legacy UX key normalization during startup"
        );

        String configManagerNormalizedPath = normalizePath(CONFIG_MANAGER_FILE);
        for (String key : REMOVED_UX_ROLLOUT_KEYS) {
            List<String> filesWithKey = new ArrayList<>();
            for (Path javaFile : javaFiles) {
                String content = Files.readString(javaFile);
                if (content.contains(key)) {
                    filesWithKey.add(normalizePath(javaFile));
                }
            }
            assertEquals(
                List.of(configManagerNormalizedPath),
                filesWithKey,
                "Removed rollout key must not appear outside ConfigManager migration list: " + key
            );
            assertFalse(
                configManagerContent.contains("properties.getProperty(\"" + key + "\""),
                "Removed rollout key must not be read from properties in runtime path: " + key
            );
            assertFalse(
                configManagerContent.contains("getProperty(\"" + key + "\""),
                "Removed rollout key must not be read through ConfigManager.getProperty in runtime path: " + key
            );
        }
    }

    @Test
    void mainShellAndSidebarMustUseSingleRenderPathWithoutRolloutBranches() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String constructorBody = extractMethodBody(content, "public LegacyMainView()");
        String sidebarEntryBody = extractMethodBody(content, "private Region createSidebar()");

        assertTrue(constructorBody.contains("mainLayoutShell.setLeftRail(createSidebar());"));
        assertTrue(constructorBody.contains("mainLayoutShell.setCenterWorkspace(createCenterPanel());"));
        assertTrue(constructorBody.contains("mainLayoutShell.setRightContextDrawer(createRightPanel());"));
        assertTrue(constructorBody.contains("setCenter(mainLayoutShell.root());"));

        assertTrue(sidebarEntryBody.contains("return createSidebarFromNavigationModel()"));
        assertFalse(content.contains("createLegacySidebar("));
        assertFalse(content.contains("createLegacyShell("));
        assertFalse(content.contains("isUxSidebarV2Enabled("));
        assertFalse(content.contains("isUxAdaptiveLayoutEnabled("));
        assertFalse(content.contains("isUxObsidianInspiredLayoutEnabled("));
    }

    private static String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String extractMethodBody(String content, String methodSignature) {
        int signatureIndex = content.indexOf(methodSignature);
        assertTrue(signatureIndex >= 0, "Method not found: " + methodSignature);

        int blockStart = content.indexOf('{', signatureIndex);
        assertTrue(blockStart >= 0, "Method body start not found: " + methodSignature);

        int depth = 0;
        for (int i = blockStart; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(blockStart + 1, i);
                }
            }
        }
        throw new IllegalStateException("Method body end not found: " + methodSignature);
    }
}

