package com.example.neuroflowplanner.ui.interaction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UndoRedoManagerTest {

    @Test
    void executeUndoRedoPushesAndPopsHistory() {
        UndoRedoManager manager = new UndoRedoManager(true, 20);
        ProbeCommand command = new ProbeCommand("task.add", "Add task", true);

        UndoRedoManager.CommandResult execute = manager.execute(command);
        assertTrue(execute.successful());
        assertEquals(1, command.executeCalls.get());
        assertEquals(1, manager.undoDepth());
        assertEquals(0, manager.redoDepth());
        assertTrue(manager.canUndo());
        assertEquals("Add task", manager.nextUndoLabel());

        UndoRedoManager.CommandResult undo = manager.undo();
        assertTrue(undo.successful());
        assertEquals(1, command.undoCalls.get());
        assertEquals(0, manager.undoDepth());
        assertEquals(1, manager.redoDepth());
        assertTrue(manager.canRedo());
        assertEquals("Add task", manager.nextRedoLabel());

        UndoRedoManager.CommandResult redo = manager.redo();
        assertTrue(redo.successful());
        assertEquals(2, command.executeCalls.get());
        assertEquals(1, manager.undoDepth());
        assertEquals(0, manager.redoDepth());
    }

    @Test
    void historyIsBoundedByConfiguredMaxSize() {
        UndoRedoManager manager = new UndoRedoManager(true, 10);
        List<ProbeCommand> commands = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ProbeCommand command = new ProbeCommand("cmd." + i, "Command " + i, true);
            commands.add(command);
            assertTrue(manager.execute(command).successful());
        }

        assertEquals(10, manager.undoDepth());
        assertEquals("Command 11", manager.nextUndoLabel());

        for (int i = 0; i < 10; i++) {
            assertTrue(manager.undo().successful());
        }
        assertFalse(manager.undo().successful());

        assertEquals(0, commands.get(0).undoCalls.get(), "Oldest command should be evicted");
        assertEquals(0, commands.get(1).undoCalls.get(), "Second oldest command should be evicted");
        for (int i = 2; i < 12; i++) {
            assertEquals(1, commands.get(i).undoCalls.get(), "Command should remain in bounded history: " + i);
        }
    }

    @Test
    void compositeCommandFailureRollsBackExecutedCommandsAndDoesNotPolluteHistory() {
        UndoRedoManager manager = new UndoRedoManager(true, 20);
        AtomicInteger firstExecute = new AtomicInteger();
        AtomicInteger firstUndo = new AtomicInteger();
        AtomicInteger secondExecute = new AtomicInteger();

        UserActionCommand first = new UserActionCommand() {
            @Override
            public String actionId() {
                return "bulk.first";
            }

            @Override
            public String label() {
                return "First";
            }

            @Override
            public String category() {
                return "bulk";
            }

            @Override
            public void execute() {
                firstExecute.incrementAndGet();
            }

            @Override
            public void undo() {
                firstUndo.incrementAndGet();
            }
        };
        UserActionCommand second = new UserActionCommand() {
            @Override
            public String actionId() {
                return "bulk.second";
            }

            @Override
            public String label() {
                return "Second";
            }

            @Override
            public String category() {
                return "bulk";
            }

            @Override
            public void execute() {
                secondExecute.incrementAndGet();
                throw new IllegalStateException("boom");
            }

            @Override
            public void undo() {
                // no-op
            }
        };

        CompositeCommand composite = new CompositeCommand(
            "bulk.execute",
            "Bulk Execute",
            "bulk",
            List.of(first, second)
        );

        UndoRedoManager.CommandResult result = manager.execute(composite);
        assertFalse(result.successful());
        assertEquals(1, firstExecute.get());
        assertEquals(1, secondExecute.get());
        assertEquals(1, firstUndo.get(), "First command should be rolled back");
        assertEquals(0, manager.undoDepth());
        assertEquals(0, manager.redoDepth());
    }

    private static final class ProbeCommand implements UserActionCommand {
        private final String actionId;
        private final String label;
        private final boolean undoable;
        private final AtomicInteger executeCalls = new AtomicInteger();
        private final AtomicInteger undoCalls = new AtomicInteger();

        private ProbeCommand(String actionId, String label, boolean undoable) {
            this.actionId = actionId;
            this.label = label;
            this.undoable = undoable;
        }

        @Override
        public String actionId() {
            return actionId;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public String category() {
            return "test";
        }

        @Override
        public boolean canUndo() {
            return undoable;
        }

        @Override
        public void execute() {
            executeCalls.incrementAndGet();
        }

        @Override
        public void undo() {
            undoCalls.incrementAndGet();
        }
    }
}
