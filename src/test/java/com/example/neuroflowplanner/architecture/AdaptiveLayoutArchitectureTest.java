package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveLayoutArchitectureTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final Path SMART_NOTES_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/smartnotes/LegacySmartNotesDialog.java"
    );

    @Test
    void mainViewShellWidthsMustUseAdaptiveWidthHelper() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);

        assertTrue(content.contains("applyRegionWidth(sidebarContainer, totalWidth);"));
        assertTrue(content.contains("applyRegionWidth(sidebarScrollPane, contextWidth);"));
        assertTrue(content.contains("applyRegionWidth(rightPanelWrapper,"));

        assertFalse(content.contains("sidebarContainer.setMinWidth("));
        assertFalse(content.contains("sidebarContainer.setPrefWidth("));
        assertFalse(content.contains("sidebarContainer.setMaxWidth("));

        assertFalse(content.contains("sidebarScrollPane.setMinWidth("));
        assertFalse(content.contains("sidebarScrollPane.setPrefWidth("));
        assertFalse(content.contains("sidebarScrollPane.setMaxWidth("));

        assertFalse(content.contains("rightPanelWrapper.setMinWidth("));
        assertFalse(content.contains("rightPanelWrapper.setPrefWidth("));
        assertFalse(content.contains("rightPanelWrapper.setMaxWidth("));
    }

    @Test
    void smartNotesSidebarWidthPolicyMustBeAdaptive() throws IOException {
        String content = Files.readString(SMART_NOTES_FILE);
        String constructorBody = extractMethodBody(content, "private LegacySmartNotesDialog()");
        String sizingBody = extractMethodBody(content, "private void applyAdaptiveSizing()");

        assertFalse(
            constructorBody.contains("sidebarBox.setPrefWidth("),
            "SmartNotes constructor must not hardcode sidebar width outside adaptive policy"
        );

        assertTrue(sizingBody.contains("double sidebarWidth = compact ? 74.0 : 250.0;"));
        assertTrue(sizingBody.contains("sidebarBox.setMinWidth(sidebarWidth);"));
        assertTrue(sizingBody.contains("sidebarBox.setPrefWidth(sidebarWidth);"));
        assertTrue(sizingBody.contains("sidebarBox.setMaxWidth(compact ? 84.0 : 300.0);"));
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
