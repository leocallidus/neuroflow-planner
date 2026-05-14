package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeftNavSurfaceArchitectureGuardTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final Path COORDINATOR_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/layout/MainLayoutCoordinator.java"
    );
    private static final Path PALETTE_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/commandpalette/CommandPaletteView.java"
    );

    @Test
    void legacyMainViewLeftNavLayoutApplyMustConsumeCoordinatorPolicies() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String body = extractMethodBody(content, "private void applyLeftNavSurfaceLayoutPolicy()");

        assertTrue(body.contains("LeftPanelDisplayPolicy policy ="));
        assertTrue(body.contains("mainLayoutCoordinator.leftPanelDisplayPolicy()"));
        assertTrue(body.contains("applyCommandPaletteOverlayLayoutPolicy(policy.palettePolicy())"));
        assertTrue(body.contains("refreshLeftNavSurfaceIndicators(policy)"));
        assertFalse(
            body.contains("UiLayoutBreakpoint."),
            "LegacyMainView left-nav layout application must not contain ad-hoc breakpoint rules"
        );
        assertFalse(
            content.contains("navSurfacePolicyService.resolveHeightCompactionPlan("),
            "LegacyMainView must not resolve left-nav policy directly; coordinator should own policy resolution"
        );
        assertFalse(
            content.contains("navSurfacePolicyService.resolveSidebarVisibleZones("),
            "LegacyMainView must consume policy instead of raw service zone visibility resolution"
        );
    }

    @Test
    void mainLayoutCoordinatorMustResolveLeftNavPoliciesThroughPolicyService() throws IOException {
        String content = Files.readString(COORDINATOR_FILE);
        String refreshBody = extractMethodBody(content, "private void refreshLeftPanelDisplayPolicy()");
        String leftPolicyBody = extractMethodBody(content, "public LeftPanelDisplayPolicy leftPanelDisplayPolicy()");
        String palettePolicyBody = extractMethodBody(content, "public CommandPaletteDisplayPolicy commandPaletteDisplayPolicy()");

        assertTrue(refreshBody.contains("navSurfacePolicyService.resolveHeightCompactionPlan("));
        assertTrue(refreshBody.contains("effectiveLeftPanelState()"));
        assertTrue(refreshBody.contains("navSurfaceHeightBand"));
        assertTrue(leftPolicyBody.contains("refreshLeftPanelDisplayPolicy()"));
        assertTrue(palettePolicyBody.contains("LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy();"));
        assertTrue(palettePolicyBody.contains("return policy == null ? null : policy.palettePolicy();"));
    }

    @Test
    void mainLayoutCoordinatorMustResolveTwoTierSidebarPoliciesThroughPolicyService() throws IOException {
        String content = Files.readString(COORDINATOR_FILE);
        String refreshBody = extractMethodBody(content, "private void refreshTwoTierSidebarDisplayPolicy()");
        String twoTierPolicyBody = extractMethodBody(content, "public TwoTierSidebarDisplayPolicy twoTierSidebarDisplayPolicy()");
        String contextPolicyBody = extractMethodBody(content, "public ContextSidebarDisplayPolicy contextSidebarDisplayPolicy()");

        assertTrue(refreshBody.contains("twoTierSidebarPolicyService.resolveHeightCompactionPlan("));
        assertTrue(refreshBody.contains("effectiveTwoTierSidebarState()"));
        assertTrue(refreshBody.contains("navSurfaceHeightBand"));
        assertTrue(twoTierPolicyBody.contains("refreshTwoTierSidebarDisplayPolicy()"));
        assertTrue(contextPolicyBody.contains("TwoTierSidebarDisplayPolicy policy = twoTierSidebarDisplayPolicy();"));
        assertTrue(contextPolicyBody.contains("return policy == null ? null : policy.contextSidebarPolicy();"));
    }

    @Test
    void commandPaletteViewMustRenderThroughControllerViewModelPipeline() throws IOException {
        String content = Files.readString(PALETTE_VIEW_FILE);
        String body = extractMethodBody(content, "private void refreshResults()");

        assertTrue(body.contains("controller.buildViewModel(queryField.getText(), displayPolicy)"));
        assertTrue(body.contains("applyGroupingMetadata(viewModel)"));
        assertTrue(body.contains("resultsView.getItems().setAll(viewModel.flatItems())"));
        assertFalse(
            body.contains("controller.search("),
            "Palette view must render grouped view-model, not ad-hoc raw search results"
        );
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
