package com.example.neuroflowplanner.ui.interaction;

import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Groups several commands into one logical command with a single undo entry.
 */
public final class CompositeCommand implements UserActionCommand {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CompositeCommand.class);

    private final String actionId;
    private final String label;
    private final String category;
    private final List<UserActionCommand> commands;

    public CompositeCommand(
        String actionId,
        String label,
        String category,
        List<UserActionCommand> commands
    ) {
        this.actionId = normalize(actionId, "composite");
        this.label = normalize(label, this.actionId);
        this.category = normalize(category, "bulk");
        this.commands = sanitizeCommands(commands);
    }

    public CompositeCommand(String actionId, String label, List<UserActionCommand> commands) {
        this(actionId, label, "bulk", commands);
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
        return category;
    }

    @Override
    public boolean canExecute() {
        if (commands.isEmpty()) {
            return false;
        }
        for (UserActionCommand command : commands) {
            if (!command.canExecute()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canUndo() {
        if (commands.isEmpty()) {
            return false;
        }
        for (UserActionCommand command : commands) {
            if (!command.canUndo()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void execute() {
        List<UserActionCommand> executed = new ArrayList<>();
        try {
            for (UserActionCommand command : commands) {
                command.execute();
                executed.add(command);
            }
        } catch (RuntimeException ex) {
            rollbackExecuted(executed, ex);
            throw ex;
        }
    }

    @Override
    public void undo() {
        for (int i = commands.size() - 1; i >= 0; i--) {
            UserActionCommand command = commands.get(i);
            if (!command.canUndo()) {
                throw new UnsupportedOperationException(
                    "Nested command does not support undo: " + command.actionId()
                );
            }
            command.undo();
        }
    }

    public List<UserActionCommand> commands() {
        return commands;
    }

    private void rollbackExecuted(List<UserActionCommand> executed, RuntimeException sourceError) {
        RuntimeException rollbackFailure = null;
        for (int i = executed.size() - 1; i >= 0; i--) {
            UserActionCommand command = executed.get(i);
            try {
                if (command.canUndo()) {
                    command.undo();
                } else {
                    throw new UnsupportedOperationException(
                        "Nested command cannot be undone during rollback: " + command.actionId()
                    );
                }
            } catch (RuntimeException rollbackError) {
                if (rollbackFailure == null) {
                    rollbackFailure = new IllegalStateException("Composite rollback failed", rollbackError);
                } else {
                    rollbackFailure.addSuppressed(rollbackError);
                }
            }
        }
        if (rollbackFailure != null) {
            sourceError.addSuppressed(rollbackFailure);
            LOG.error(
                "ux.command.composite.rollback.failed",
                rollbackFailure,
                "actionId", actionId,
                "label", label,
                "category", category
            );
        }
    }

    private List<UserActionCommand> sanitizeCommands(List<UserActionCommand> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<UserActionCommand> out = new ArrayList<>(source.size());
        for (UserActionCommand command : source) {
            out.add(Objects.requireNonNull(command, "Composite command contains null command"));
        }
        return Collections.unmodifiableList(out);
    }

    private String normalize(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
