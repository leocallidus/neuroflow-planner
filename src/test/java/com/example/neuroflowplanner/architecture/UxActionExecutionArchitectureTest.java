package com.example.neuroflowplanner.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UxActionExecutionArchitectureTest {
    private static final Path MAIN_VIEW_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/mainview/LegacyMainView.java"
    );
    private static final Path SMART_NOTES_FILE = Path.of(
        "src/main/java/com/example/neuroflowplanner/ui/smartnotes/LegacySmartNotesDialog.java"
    );

    @Test
    void mainViewShortcutHandlerMustDispatchThroughUiActionRegistry() throws IOException {
        String content = Files.readString(MAIN_VIEW_FILE);
        String handlerBody = extractMethodBody(content, "private void handleGlobalShortcutKeyPressed(KeyEvent event)");

        assertTrue(
            handlerBody.contains("commandActionRegistry.execute("),
            "MainView shortcut handler must route commands through UiActionRegistry"
        );
        assertFalse(handlerBody.contains("handleUndoAction("));
        assertFalse(handlerBody.contains("handleRedoAction("));
        assertFalse(handlerBody.contains("openCommandPalette("));
        assertFalse(handlerBody.contains("focusGlobalSearch("));
    }

    @Test
    void smartNotesShortcutHandlerMustDispatchThroughUiActionRegistry() throws IOException {
        String content = Files.readString(SMART_NOTES_FILE);
        String handlerBody = extractMethodBody(content, "private void handleGlobalShortcutKeyPressed(KeyEvent event)");

        assertTrue(
            handlerBody.contains("commandActionRegistry.execute("),
            "SmartNotes shortcut handler must route commands through UiActionRegistry"
        );
        assertFalse(handlerBody.contains("undoLastNoteAction("));
        assertFalse(handlerBody.contains("redoLastNoteAction("));
        assertFalse(handlerBody.contains("openCommandPalette("));
        assertFalse(handlerBody.contains("focusGlobalSearch("));
    }

    @Test
    void viewAdaptersMustNotExecuteUndoRedoOrCompositeCommandsDirectly() throws IOException {
        String mainContent = Files.readString(MAIN_VIEW_FILE);
        String notesContent = Files.readString(SMART_NOTES_FILE);

        assertFalse(mainContent.contains("undoRedoManager.execute("));
        assertFalse(notesContent.contains("undoRedoManager.execute("));
        assertFalse(mainContent.contains("new CompositeCommand("));
        assertFalse(notesContent.contains("new CompositeCommand("));
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
