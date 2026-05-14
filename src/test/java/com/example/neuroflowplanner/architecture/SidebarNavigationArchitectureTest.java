package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarNavigationArchitectureTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );

    @Test
    void sidebarV2RenderPathMustUseNavigationModelFactoryMethods() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String methodBody = extractMethodBody(content, "private Region createSidebarFromNavigationModel()");

        assertTrue(methodBody.contains("sidebarNavigationService.buildSections()"));
        assertTrue(methodBody.contains("sidebarNavigationService.buildItems()"));
        assertTrue(methodBody.contains("createPinnedQuickZone("));
        assertTrue(methodBody.contains("createContextSidebarDomainHeader()"));
        assertTrue(methodBody.contains("createContextSidebarDomainList()"));
        assertFalse(
            methodBody.contains("renderUnifiedSidebarSurfaceGroups("),
            "Two-tier sidebar renderer should not render legacy grouped cards in createSidebarFromNavigationModel"
        );
        assertFalse(
            methodBody.contains("createCollapsibleSidebarSection("),
            "Sidebar V2 top-level renderer should delegate grouped rendering instead of assembling raw sections inline"
        );
        assertFalse(
            methodBody.contains("createSidebarSection("),
            "Sidebar V2 renderer must not construct ad-hoc legacy sections"
        );
        assertFalse(
            methodBody.contains("createLegacySidebar("),
            "Sidebar V2 renderer must not fallback to legacy tree while rendering"
        );
    }

    @Test
    void sidebarEntryPointMustUseSingleNavigationModelPath() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String methodBody = extractMethodBody(content, "private Region createSidebar()");

        assertTrue(methodBody.contains("return createSidebarFromNavigationModel()"));
        assertFalse(
            methodBody.contains("createLegacySidebar("),
            "Sidebar entry point must not fallback to legacy renderer"
        );
        assertFalse(
            methodBody.contains("isSidebarV2Enabled("),
            "Sidebar entry point must not branch by rollout flags"
        );
    }

    @Test
    void sidebarActionsMustBeBridgedThroughActionRegistry() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        int methodMarker = content.indexOf("boolean trackForSidebarCollapse");
        assertTrue(methodMarker >= 0, "Could not locate extended createSidebarButtonFromModelItem signature");
        int signatureStart = content.lastIndexOf("private Button createSidebarButtonFromModelItem(", methodMarker);
        assertTrue(signatureStart >= 0, "Could not locate createSidebarButtonFromModelItem method start");
        String createButtonBody = extractMethodBodyFromIndex(content, signatureStart);
        String registerActionsBody = extractMethodBody(content, "private void registerCommandPaletteActions()");

        assertTrue(createButtonBody.contains("commandActionRegistry.execute(actionId)"));
        assertFalse(
            createButtonBody.contains("resolveSidebarAction(actionId)"),
            "Sidebar button execution must not bypass UiActionRegistry"
        );
        assertFalse(
            createButtonBody.contains("action.run()"),
            "Sidebar button execution must not run ad-hoc handlers directly"
        );
        assertTrue(registerActionsBody.contains("registerSidebarBridgeActions()"));
    }

    @Test
    void twoTierContextSidebarRenderMustUseNavModelAndSharedButtonFactory() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String applyPolicyBody = extractMethodBody(content, "private void applyContextSidebarDomainContentPolicy(TwoTierSidebarDisplayPolicy policy)");
        String renderDomainBody = extractMethodBody(content, "private void renderContextSidebarDomainList(SidebarRailDomain domain)");

        assertTrue(applyPolicyBody.contains("renderContextSidebarDomainList(activeDomain)"));
        assertFalse(
            applyPolicyBody.contains("UiLayoutBreakpoint."),
            "Context sidebar policy application must not contain ad-hoc breakpoint rules"
        );

        assertTrue(renderDomainBody.contains("sidebarNavigationService.buildContextSidebarDomainItems(safeDomain)"));
        assertTrue(renderDomainBody.contains("Button button = createSidebarButtonFromModelItem(item);"));
        assertFalse(
            renderDomainBody.contains("commandActionRegistry.execute("),
            "Context sidebar renderer must not execute actions directly while rendering rows"
        );
        assertFalse(
            renderDomainBody.contains("resolveSidebarAction("),
            "Context sidebar renderer must not bypass nav model/action registry bridge"
        );
    }

    private String extractMethodBody(String content, String methodSignature) {
        int signatureIndex = content.indexOf(methodSignature);
        assertTrue(signatureIndex >= 0, "Method not found: " + methodSignature);

        return extractMethodBodyFromIndex(content, signatureIndex);
    }

    private String extractMethodBodyFromIndex(String content, int signatureIndex) {
        int blockStart = content.indexOf('{', signatureIndex);
        assertTrue(blockStart >= 0, "Method body start not found for signature index: " + signatureIndex);

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
        throw new IllegalStateException("Method body end not found for signature index: " + signatureIndex);
    }
}
