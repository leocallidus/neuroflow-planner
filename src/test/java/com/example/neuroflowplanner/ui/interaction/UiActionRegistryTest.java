package com.example.neuroflowplanner.ui.interaction;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiActionRegistryTest {

    @Test
    void registerLookupAndAvailabilityWorkAsExpected() {
        UiActionRegistry registry = new UiActionRegistry(new UndoRedoManager(true, 20));
        UiActionRegistry.RegisteredAction unavailableAction = action(
            "task.bulk.delete",
            "Delete tasks",
            "bulk",
            "",
            () -> {},
            () -> false,
            () -> "Select tasks first"
        );

        assertTrue(registry.register(unavailableAction));
        assertTrue(registry.find("task.bulk.delete").isPresent());
        assertFalse(registry.isAvailable("task.bulk.delete"));
        assertEquals("Select tasks first", registry.unavailableReason("task.bulk.delete"));
        assertEquals("Action is not registered", registry.unavailableReason("missing.action"));
    }

    @Test
    void executeDelegatesToUndoRedoManagerAndSupportsUndo() {
        UndoRedoManager undoRedoManager = new UndoRedoManager(true, 20);
        UiActionRegistry registry = new UiActionRegistry(undoRedoManager);
        AtomicInteger executeCalls = new AtomicInteger();
        AtomicInteger undoCalls = new AtomicInteger();

        assertTrue(registry.register(new UiActionRegistry.RegisteredAction(
            "task.create",
            "Create task",
            "task",
            "Ctrl/Cmd+N",
            () -> new UserActionCommand() {
                @Override
                public String actionId() {
                    return "task.create";
                }

                @Override
                public String label() {
                    return "Create task";
                }

                @Override
                public String category() {
                    return "task";
                }

                @Override
                public void execute() {
                    executeCalls.incrementAndGet();
                }

                @Override
                public void undo() {
                    undoCalls.incrementAndGet();
                }
            },
            () -> true,
            () -> "",
            false
        )));

        UndoRedoManager.CommandResult executeResult = registry.execute("task.create");
        assertTrue(executeResult.successful());
        assertEquals(1, executeCalls.get());
        assertTrue(undoRedoManager.canUndo());

        UndoRedoManager.CommandResult undoResult = undoRedoManager.undo();
        assertTrue(undoResult.successful());
        assertEquals(1, undoCalls.get());
    }

    @Test
    void executeFailsFastWhenActionUnavailableWithoutInvokingFactory() {
        UiActionRegistry registry = new UiActionRegistry(new UndoRedoManager(true, 20));
        AtomicInteger factoryCalls = new AtomicInteger();
        registry.register(new UiActionRegistry.RegisteredAction(
            "task.archive",
            "Archive task",
            "task",
            "",
            () -> {
                factoryCalls.incrementAndGet();
                return new UserActionCommand() {
                    @Override
                    public String actionId() {
                        return "task.archive";
                    }

                    @Override
                    public String label() {
                        return "Archive task";
                    }

                    @Override
                    public String category() {
                        return "task";
                    }

                    @Override
                    public boolean canUndo() {
                        return false;
                    }

                    @Override
                    public void execute() {
                        // no-op
                    }
                };
            },
            () -> false,
            () -> "No selected task",
            false
        ));

        UndoRedoManager.CommandResult result = registry.execute("task.archive");
        assertFalse(result.successful());
        assertEquals(UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE, result.status());
        assertEquals("No selected task", result.message());
        assertEquals(0, factoryCalls.get());
    }

    @Test
    void duplicateActionIdIsRejected() {
        UiActionRegistry registry = new UiActionRegistry(new UndoRedoManager(true, 20));
        assertTrue(registry.register(action(
            "main.palette.open",
            "Open palette",
            "system",
            "Ctrl/Cmd+K",
            () -> {},
            () -> true,
            () -> ""
        )));
        assertFalse(registry.register(action(
            "main.palette.open",
            "Open palette duplicate",
            "system",
            "",
            () -> {},
            () -> true,
            () -> ""
        )));
    }

    @Test
    void executionListenerReceivesSuccessfulResult() {
        UiActionRegistry registry = new UiActionRegistry(new UndoRedoManager(true, 20));
        AtomicInteger listenerCalls = new AtomicInteger();
        AtomicInteger executeCalls = new AtomicInteger();

        registry.addExecutionListener((actionId, result) -> {
            if ("task.run".equals(actionId) && result.successful()) {
                listenerCalls.incrementAndGet();
            }
        });
        registry.register(action(
            "task.run",
            "Run task action",
            "task",
            "",
            executeCalls::incrementAndGet,
            () -> true,
            () -> ""
        ));

        UndoRedoManager.CommandResult result = registry.execute("task.run");
        assertTrue(result.successful());
        assertEquals(1, executeCalls.get());
        assertEquals(1, listenerCalls.get());
    }

    @Test
    void executionListenerReceivesSkippedResultAndCanBeRemoved() {
        UiActionRegistry registry = new UiActionRegistry(new UndoRedoManager(true, 20));
        AtomicInteger listenerCalls = new AtomicInteger();
        UiActionRegistry.ExecutionListener listener = (actionId, result) -> {
            if ("task.unavailable".equals(actionId)
                && result.status() == UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE) {
                listenerCalls.incrementAndGet();
            }
        };

        assertTrue(registry.addExecutionListener(listener));
        registry.register(new UiActionRegistry.RegisteredAction(
            "task.unavailable",
            "Unavailable action",
            "task",
            "",
            () -> runOnlyCommand("task.unavailable", "Unavailable action", "task", () -> {}, () -> false),
            () -> false,
            () -> "Unavailable",
            false
        ));

        UndoRedoManager.CommandResult skipped = registry.execute("task.unavailable");
        assertFalse(skipped.successful());
        assertEquals(1, listenerCalls.get());

        assertTrue(registry.removeExecutionListener(listener));
        registry.execute("task.unavailable");
        assertEquals(1, listenerCalls.get());
    }

    private static UiActionRegistry.RegisteredAction action(
        String actionId,
        String label,
        String category,
        String shortcut,
        Runnable runnable,
        BooleanSupplier availability,
        Supplier<String> unavailableReason
    ) {
        return new UiActionRegistry.RegisteredAction(
            actionId,
            label,
            category,
            shortcut,
            () -> runOnlyCommand(actionId, label, category, runnable, availability),
            availability,
            unavailableReason,
            false
        );
    }

    private static UserActionCommand runOnlyCommand(
        String actionId,
        String label,
        String category,
        Runnable runnable,
        BooleanSupplier availability
    ) {
        return new UserActionCommand() {
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
                return category;
            }

            @Override
            public boolean canExecute() {
                return availability.getAsBoolean();
            }

            @Override
            public boolean canUndo() {
                return false;
            }

            @Override
            public void execute() {
                runnable.run();
            }
        };
    }
}
