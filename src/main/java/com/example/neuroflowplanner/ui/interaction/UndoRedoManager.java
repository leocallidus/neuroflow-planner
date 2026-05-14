package com.example.neuroflowplanner.ui.interaction;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UndoRedoManager {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UndoRedoManager.class);

    private final boolean enabled;
    private final int maxHistory;
    private final Deque<UserActionCommand> undoStack = new ArrayDeque<>();
    private final Deque<UserActionCommand> redoStack = new ArrayDeque<>();

    public UndoRedoManager(boolean enabled, int maxHistory) {
        this.enabled = enabled;
        this.maxHistory = clampHistorySize(maxHistory);
    }

    public static UndoRedoManager fromConfig() {
        return new UndoRedoManager(
            ConfigManager.isUxUndoEnabled(),
            ConfigManager.getUxUndoMaxHistory()
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int undoDepth() {
        return undoStack.size();
    }

    public int redoDepth() {
        return redoStack.size();
    }

    public boolean canUndo() {
        return enabled && !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return enabled && !redoStack.isEmpty();
    }

    public String nextUndoLabel() {
        UserActionCommand command = undoStack.peekLast();
        return command == null ? "" : safeLabel(command);
    }

    public String nextRedoLabel() {
        UserActionCommand command = redoStack.peekLast();
        return command == null ? "" : safeLabel(command);
    }

    public CommandResult execute(UserActionCommand command) {
        if (command == null) {
            return CommandResult.skipped(
                CommandStatus.SKIPPED_UNAVAILABLE,
                "unknown",
                "Command is null"
            );
        }

        String actionId = safeActionId(command);
        if (!command.canExecute()) {
            LOG.warning(
                "ux.command.execute.skipped",
                "actionId", actionId,
                "reason", "canExecute_false",
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.skipped(
                CommandStatus.SKIPPED_UNAVAILABLE,
                actionId,
                "Action is not executable in current state"
            );
        }

        LOG.info(
            "ux.command.execute.started",
            "actionId", actionId,
            "label", safeLabel(command),
            "category", safeCategory(command),
            "undoEnabled", enabled,
            "undoDepth", undoDepth(),
            "redoDepth", redoDepth()
        );

        try {
            command.execute();
            boolean tracked = enabled && command.canUndo();
            if (tracked) {
                pushUndo(command);
                redoStack.clear();
            }

            LOG.info(
                "ux.command.execute.completed",
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "tracked", tracked,
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.success(
                actionId,
                tracked ? "Executed and added to undo history" : "Executed without undo history"
            );
        } catch (RuntimeException ex) {
            LOG.error(
                "ux.command.execute.failed",
                ex,
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.failed(actionId, "Execution failed: " + ex.getClass().getSimpleName());
        }
    }

    public CommandResult undo() {
        if (!enabled) {
            return CommandResult.skipped(
                CommandStatus.SKIPPED_DISABLED,
                "undo",
                "Undo is disabled by configuration"
            );
        }

        UserActionCommand command = undoStack.pollLast();
        if (command == null) {
            return CommandResult.skipped(
                CommandStatus.SKIPPED_UNAVAILABLE,
                "undo",
                "Undo history is empty"
            );
        }

        String actionId = safeActionId(command);
        LOG.info(
            "ux.command.undo.started",
            "actionId", actionId,
            "label", safeLabel(command),
            "category", safeCategory(command),
            "undoDepth", undoDepth(),
            "redoDepth", redoDepth()
        );

        try {
            if (!command.canUndo()) {
                throw new UnsupportedOperationException("Undo is not supported by action");
            }
            command.undo();
            redoStack.addLast(command);

            LOG.info(
                "ux.command.undo.completed",
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.success(actionId, "Action was undone");
        } catch (RuntimeException ex) {
            // Preserve stack state on failed undo.
            undoStack.addLast(command);
            LOG.error(
                "ux.command.undo.failed",
                ex,
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.failed(actionId, "Undo failed: " + ex.getClass().getSimpleName());
        }
    }

    public CommandResult redo() {
        if (!enabled) {
            return CommandResult.skipped(
                CommandStatus.SKIPPED_DISABLED,
                "redo",
                "Redo is disabled by configuration"
            );
        }

        UserActionCommand command = redoStack.pollLast();
        if (command == null) {
            return CommandResult.skipped(
                CommandStatus.SKIPPED_UNAVAILABLE,
                "redo",
                "Redo history is empty"
            );
        }

        String actionId = safeActionId(command);
        if (!command.canExecute()) {
            redoStack.addLast(command);
            return CommandResult.skipped(
                CommandStatus.SKIPPED_UNAVAILABLE,
                actionId,
                "Action cannot be re-executed in current state"
            );
        }

        LOG.info(
            "ux.command.redo.started",
            "actionId", actionId,
            "label", safeLabel(command),
            "category", safeCategory(command),
            "undoDepth", undoDepth(),
            "redoDepth", redoDepth()
        );

        try {
            command.execute();
            pushUndo(command);

            LOG.info(
                "ux.command.redo.completed",
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.success(actionId, "Action was re-executed");
        } catch (RuntimeException ex) {
            // Preserve stack state on failed redo.
            redoStack.addLast(command);
            LOG.error(
                "ux.command.redo.failed",
                ex,
                "actionId", actionId,
                "label", safeLabel(command),
                "category", safeCategory(command),
                "undoDepth", undoDepth(),
                "redoDepth", redoDepth()
            );
            return CommandResult.failed(actionId, "Redo failed: " + ex.getClass().getSimpleName());
        }
    }

    public void clearHistory() {
        int oldUndo = undoDepth();
        int oldRedo = redoDepth();
        undoStack.clear();
        redoStack.clear();
        LOG.info(
            "ux.command.history.cleared",
            "oldUndoDepth", oldUndo,
            "oldRedoDepth", oldRedo
        );
    }

    private void pushUndo(UserActionCommand command) {
        undoStack.addLast(command);
        while (undoStack.size() > maxHistory) {
            undoStack.removeFirst();
        }
    }

    private int clampHistorySize(int size) {
        if (size < UxConfigDefaults.UX_UNDO_MAX_HISTORY_MIN) {
            return UxConfigDefaults.UX_UNDO_MAX_HISTORY_MIN;
        }
        if (size > UxConfigDefaults.UX_UNDO_MAX_HISTORY_MAX) {
            return UxConfigDefaults.UX_UNDO_MAX_HISTORY_MAX;
        }
        return size;
    }

    private String safeActionId(UserActionCommand command) {
        String actionId = command.actionId();
        if (actionId == null || actionId.isBlank()) {
            return "unknown";
        }
        return actionId.trim();
    }

    private String safeLabel(UserActionCommand command) {
        String label = command.label();
        if (label == null || label.isBlank()) {
            return safeActionId(command);
        }
        return label.trim();
    }

    private String safeCategory(UserActionCommand command) {
        String category = command.category();
        if (category == null || category.isBlank()) {
            return "general";
        }
        return category.trim();
    }

    public enum CommandStatus {
        SUCCESS,
        SKIPPED_DISABLED,
        SKIPPED_UNAVAILABLE,
        FAILED
    }

    public record CommandResult(CommandStatus status, String actionId, String message) {
        public static CommandResult success(String actionId, String message) {
            return new CommandResult(CommandStatus.SUCCESS, actionId, message);
        }

        public static CommandResult skipped(CommandStatus status, String actionId, String message) {
            return new CommandResult(status, actionId, message);
        }

        public static CommandResult failed(String actionId, String message) {
            return new CommandResult(CommandStatus.FAILED, actionId, message);
        }

        public boolean successful() {
            return status == CommandStatus.SUCCESS;
        }
    }
}
