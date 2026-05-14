package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RightPanelLayoutArchitectureGuardTest {
    private static final Path MAIN_SOURCES_ROOT = Path.of("src/main/java");
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final Path COORDINATOR_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/layout/MainLayoutCoordinator.java"
    );
    private static final Path RIGHT_PANEL_LAYOUT_SERVICE_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/layout/rightpanel/RightPanelLayoutService.java"
    );

    @Test
    void legacyMainViewRightPanelModeResolutionMustDelegateToCoordinator() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String body = extractMethodBody(content, "private UiRightContextMode resolveRightPanelDisplayMode()");

        assertTrue(
            body.contains("return mainLayoutCoordinator.snapshot().rightContextMode();"),
            "LegacyMainView must delegate right-panel mode semantics to coordinator snapshot"
        );
        assertFalse(body.contains("case WIDE -> UiRightContextMode.PINNED"));
        assertFalse(body.contains("case NORMAL -> UiRightContextMode.COLLAPSIBLE"));
        assertFalse(body.contains("case COMPACT -> UiRightContextMode.OVERLAY"));
    }

    @Test
    void legacyMainViewTabbedInspectorMustRenderFromResolvedInspectorPolicy() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String body = extractMethodBody(content, "private void applyRightPanelLayoutPolicy()");

        assertTrue(body.contains("RightPanelInspectorDisplayPolicy policy ="));
        assertTrue(body.contains("updateRightPanelInspectorTabStrip(policy);"));
        assertTrue(body.contains("refreshRightPanelInspectorContentHost(policy);"));
        assertFalse(
            body.contains("UiLayoutBreakpoint."),
            "Tabbed inspector content visibility/order must come from inspector policy, not ad-hoc breakpoint checks"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveVisibleSections("),
            "LegacyMainView should consume coordinator/display policy instead of raw service visibility resolution"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveCompactionPlan("),
            "LegacyMainView should consume coordinator/display policy instead of raw service compaction resolution"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveTabHeightCompactionPlan("),
            "LegacyMainView should consume coordinator inspector policy instead of raw service inspector compaction resolution"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveInspectorTabs("),
            "LegacyMainView should not resolve inspector tab set directly; tabs must come from coordinator policy"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveActiveInspectorTab("),
            "LegacyMainView should not resolve active inspector tab directly; active tab must come from coordinator policy"
        );
        assertFalse(
            content.contains("rightPanelLayoutService.resolveInspectorContentPolicy("),
            "LegacyMainView should not resolve per-tab content policy directly; policy must come from coordinator"
        );
    }

    @Test
    void coordinatorMustBuildRightPanelDisplayPolicyThroughLayoutService() throws IOException {
        String content = Files.readString(COORDINATOR_FILE);
        String body = extractMethodBody(content, "public RightPanelDisplayPolicy rightPanelDisplayPolicy()");

        assertTrue(body.contains("rightPanelLayoutService.resolveCompactionPlan("));
        assertTrue(body.contains("effectiveRightPanelState()"));
        assertTrue(body.contains("state.breakpoint()"));
    }

    @Test
    void rightPanelInspectorPolicyApiUsageMustStayCentralized() throws IOException {
        List<Path> tabHeightPlanCallers = findMainSourcesContaining("rightPanelLayoutService\\.resolveTabHeightCompactionPlan\\(");
        assertEquals(
            List.of(COORDINATOR_FILE),
            tabHeightPlanCallers,
            "resolveTabHeightCompactionPlan should be consumed only by MainLayoutCoordinator"
        );

        List<Path> tabResolverCallers = findMainSourcesContaining("rightPanelLayoutService\\.resolveInspectorTabs\\(");
        assertTrue(
            tabResolverCallers.isEmpty(),
            "resolveInspectorTabs must not be called ad-hoc from UI sources"
        );

        List<Path> activeResolverCallers = findMainSourcesContaining("rightPanelLayoutService\\.resolveActiveInspectorTab\\(");
        assertTrue(
            activeResolverCallers.isEmpty(),
            "resolveActiveInspectorTab must not be called ad-hoc from UI sources"
        );

        List<Path> contentPolicyCallers = findMainSourcesContaining("rightPanelLayoutService\\.resolveInspectorContentPolicy\\(");
        assertTrue(
            contentPolicyCallers.isEmpty(),
            "resolveInspectorContentPolicy must not be called ad-hoc from UI sources"
        );
    }

    @Test
    void rightPanelInspectorPolicyMethodsMustBeDefinedInLayoutService() throws IOException {
        String content = Files.readString(RIGHT_PANEL_LAYOUT_SERVICE_FILE);
        assertTrue(content.contains("public List<RightPanelInspectorTab> resolveInspectorTabs("));
        assertTrue(content.contains("public RightPanelInspectorTab resolveActiveInspectorTab("));
        assertTrue(content.contains("public RightPanelTabContentPolicy resolveInspectorContentPolicy("));
        assertTrue(content.contains("public RightPanelInspectorDisplayPolicy resolveTabHeightCompactionPlan("));
    }

    private List<Path> findMainSourcesContaining(String regex) throws IOException {
        Pattern pattern = Pattern.compile(regex);
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(MAIN_SOURCES_ROOT)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        if (pattern.matcher(content).find()) {
                            matches.add(path);
                        }
                    } catch (IOException ex) {
                        throw new IllegalStateException("Unable to read source: " + path, ex);
                    }
                });
        }
        matches.sort(Comparator.comparing(Path::toString));
        return List.copyOf(matches);
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
