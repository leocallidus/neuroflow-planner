package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineOverlayTabbedPathArchitectureGuardTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );

    @Test
    void inlineOverlayMustStayOnTabbedStateModelAndPublicTabOperations() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);

        assertTrue(content.contains("private final LinkedHashMap<String, InlineOverlayTab> inlineOverlayTabs"));
        assertTrue(content.contains("public void openOrActivateTab(String tabId, InlineView view, String title)"));
        assertTrue(content.contains("public boolean activateTab(String tabId)"));
        assertTrue(content.contains("public boolean closeActiveTab()"));
        assertTrue(content.contains("public boolean closeTab(String tabId)"));
        assertTrue(content.contains("public boolean canCloseAllTabs()"));
    }

    @Test
    void inlineOpenPathMustNotReintroduceLegacyCloseBeforeOpenPattern() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String openInlineSupplierBody = extractMethodBody(
            content,
            "private InlineView openInlineView(InlineTabMetadata metadata, Supplier<InlineView> viewFactory)"
        );
        String showInlineNodeBody = extractMethodBody(
            content,
            "public void showInline(Node content, Runnable onClose, String title)"
        );
        String showInlineViewBody = extractMethodBody(
            content,
            "public void showInline(InlineView view, String title)"
        );

        assertTrue(openInlineSupplierBody.contains("InlineOverlayTab existing = inlineOverlayTabs.get(precomputedTabId);"));
        assertTrue(openInlineSupplierBody.contains("activateTab(precomputedTabId);"));
        assertFalse(
            openInlineSupplierBody.contains("closeInline()"),
            "Open path must not close currently opened tab by default"
        );
        assertTrue(showInlineNodeBody.contains("openOrActivateTab(fallbackId, content, onClose, title, false);"));
        assertTrue(showInlineViewBody.contains("openOrActivateTab(tabId, view, headerTitle);"));
        assertFalse(content.contains("if (overlayHost.isVisible()) { closeInline(); }"));
    }

    @Test
    void overlayContentBindingMustRenderThroughActivateTabOnly() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String activateBody = extractMethodBody(content, "public boolean activateTab(String tabId)");

        assertTrue(activateBody.contains("overlayContentHolder.getChildren().setAll(tab.contentNode());"));
        assertEquals(
            1,
            countOccurrences(content, "overlayContentHolder.getChildren().setAll("),
            "Content host rendering should happen only via activateTab to keep switching stateful"
        );
    }

    @Test
    void applicationCloseContractMustDelegateToCanCloseAllTabs() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String body = extractMethodBody(content, "public boolean canCloseApplication()");

        assertTrue(body.contains("return canCloseAllTabs();"));
        assertFalse(body.contains("currentInlineView"));
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int index = 0;
        while (index >= 0) {
            index = content.indexOf(needle, index);
            if (index >= 0) {
                count++;
                index += needle.length();
            }
        }
        return count;
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
