package com.example.neuroflowplanner.ui.interaction;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutDispatchTest {

    @Test
    void shortcutTokenResolutionDispatchesToRegisteredAction() {
        UndoRedoManager undoRedoManager = new UndoRedoManager(true, 20);
        UiActionRegistry actionRegistry = new UiActionRegistry(undoRedoManager);
        AtomicInteger executions = new AtomicInteger();
        actionRegistry.register(new UiActionRegistry.RegisteredAction(
            "main.system.commandPalette",
            "Open palette",
            "system",
            "Ctrl/Cmd+K",
            () -> new UserActionCommand() {
                @Override
                public String actionId() {
                    return "main.system.commandPalette";
                }

                @Override
                public String label() {
                    return "Open palette";
                }

                @Override
                public String category() {
                    return "system";
                }

                @Override
                public boolean canUndo() {
                    return false;
                }

                @Override
                public void execute() {
                    executions.incrementAndGet();
                }
            },
            () -> true,
            () -> "",
            false
        ));

        ShortcutRegistry shortcutRegistry = new ShortcutRegistry(true, true);
        shortcutRegistry.register(new ShortcutRegistry.ShortcutBinding(
            "CTRL+K",
            ShortcutRegistry.ShortcutContext.GLOBAL,
            "main.system.commandPalette",
            false,
            false
        ));

        KeyEvent keyEvent = new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            KeyCode.K,
            false,
            true,
            false,
            false
        );
        String token = ShortcutRegistry.toShortcutToken(keyEvent);
        assertEquals("CTRL+K", token);

        ShortcutRegistry.ShortcutBinding binding = shortcutRegistry.resolve(
            token,
            Set.of(ShortcutRegistry.ShortcutContext.GLOBAL)
        ).orElse(null);
        assertNotNull(binding);
        assertEquals("main.system.commandPalette", binding.actionId());

        UndoRedoManager.CommandResult result = actionRegistry.execute(binding.actionId());
        assertTrue(result.successful());
        assertEquals(1, executions.get());
    }

    @Test
    void undoShortcutActionIsUnavailableWhenHistoryIsEmpty() {
        UndoRedoManager undoRedoManager = new UndoRedoManager(true, 20);
        UiActionRegistry actionRegistry = new UiActionRegistry(undoRedoManager);
        actionRegistry.register(new UiActionRegistry.RegisteredAction(
            "main.history.undo",
            "Undo",
            "history",
            "Ctrl/Cmd+Z",
            () -> new UserActionCommand() {
                @Override
                public String actionId() {
                    return "main.history.undo";
                }

                @Override
                public String label() {
                    return "Undo";
                }

                @Override
                public String category() {
                    return "history";
                }

                @Override
                public boolean canUndo() {
                    return false;
                }

                @Override
                public void execute() {
                    // no-op
                }
            },
            undoRedoManager::canUndo,
            () -> "Undo history is empty",
            false
        ));

        UndoRedoManager.CommandResult result = actionRegistry.execute("main.history.undo");
        assertFalse(result.successful());
        assertEquals(UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE, result.status());
        assertEquals("Undo history is empty", result.message());
    }
}
