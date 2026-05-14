package com.example.neuroflowplanner.ui.interaction;

import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Central registry for user-facing UI actions.
 */
public final class UiActionRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UiActionRegistry.class);

    private final UndoRedoManager undoRedoManager;
    private final Map<String, RegisteredAction> actionsById = new LinkedHashMap<>();
    private final List<ExecutionListener> executionListeners = new ArrayList<>();

    public UiActionRegistry(UndoRedoManager undoRedoManager) {
        this.undoRedoManager = Objects.requireNonNull(undoRedoManager, "undoRedoManager");
    }

    public static UiActionRegistry withConfigDefaults() {
        return new UiActionRegistry(UndoRedoManager.fromConfig());
    }

    public synchronized boolean register(RegisteredAction action) {
        RegisteredAction safe = Objects.requireNonNull(action, "action");
        if (actionsById.containsKey(safe.actionId())) {
            LOG.warning(
                "ux.action.register.rejected",
                "actionId", safe.actionId(),
                "reason", "duplicate_action_id"
            );
            return false;
        }
        actionsById.put(safe.actionId(), safe);
        LOG.info(
            "ux.action.registered",
            "actionId", safe.actionId(),
            "label", safe.label(),
            "category", safe.category(),
            "defaultShortcut", safe.defaultShortcut()
        );
        return true;
    }

    public synchronized boolean unregister(String actionId) {
        String normalized = normalizeActionId(actionId);
        if (normalized == null) {
            return false;
        }
        boolean removed = actionsById.remove(normalized) != null;
        if (removed) {
            LOG.info("ux.action.unregistered", "actionId", normalized);
        }
        return removed;
    }

    public synchronized Optional<RegisteredAction> find(String actionId) {
        String normalized = normalizeActionId(actionId);
        if (normalized == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(actionsById.get(normalized));
    }

    public synchronized boolean isRegistered(String actionId) {
        String normalized = normalizeActionId(actionId);
        return normalized != null && actionsById.containsKey(normalized);
    }

    public synchronized List<RegisteredAction> listActions() {
        return List.copyOf(actionsById.values());
    }

    public synchronized List<RegisteredAction> searchActions(String query, int limit) {
        int safeLimit = Math.max(1, limit);
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<RegisteredAction> matched = new ArrayList<>();
        for (RegisteredAction action : actionsById.values()) {
            if (needle.isEmpty() || matches(action, needle)) {
                matched.add(action);
            }
            if (matched.size() >= safeLimit) {
                break;
            }
        }
        return Collections.unmodifiableList(matched);
    }

    public synchronized boolean isAvailable(String actionId) {
        RegisteredAction action = actionsById.get(normalizeActionId(actionId));
        return action != null && action.availability().getAsBoolean();
    }

    public synchronized String unavailableReason(String actionId) {
        RegisteredAction action = actionsById.get(normalizeActionId(actionId));
        if (action == null) {
            return "Action is not registered";
        }
        if (action.availability().getAsBoolean()) {
            return "";
        }
        String reason = action.unavailableReason().get();
        if (reason == null || reason.isBlank()) {
            return "Action is unavailable";
        }
        return reason.trim();
    }

    public synchronized ActionAvailability actionAvailability(String actionId) {
        String normalized = normalizeActionId(actionId);
        if (normalized == null) {
            return new ActionAvailability("", false, false, "Action id is empty");
        }
        RegisteredAction action = actionsById.get(normalized);
        if (action == null) {
            return new ActionAvailability(normalized, false, false, "Action is not registered");
        }
        boolean available;
        try {
            available = action.availability().getAsBoolean();
        } catch (RuntimeException ex) {
            return new ActionAvailability(action.actionId(), true, false, "Availability check failed");
        }
        if (available) {
            return new ActionAvailability(action.actionId(), true, true, "");
        }
        String reason;
        try {
            reason = action.unavailableReason().get();
        } catch (RuntimeException ex) {
            reason = "Action is unavailable";
        }
        if (reason == null || reason.isBlank()) {
            reason = "Action is unavailable";
        }
        return new ActionAvailability(action.actionId(), true, false, reason.trim());
    }

    public boolean addExecutionListener(ExecutionListener listener) {
        if (listener == null) {
            return false;
        }
        synchronized (this) {
            if (executionListeners.contains(listener)) {
                return false;
            }
            executionListeners.add(listener);
            return true;
        }
    }

    public boolean removeExecutionListener(ExecutionListener listener) {
        if (listener == null) {
            return false;
        }
        synchronized (this) {
            return executionListeners.remove(listener);
        }
    }

    public UndoRedoManager.CommandResult execute(String actionId) {
        UndoRedoManager.CommandResult result;
        List<ExecutionListener> listenersSnapshot;
        synchronized (this) {
            String normalized = normalizeActionId(actionId);
            if (normalized == null) {
                result = UndoRedoManager.CommandResult.skipped(
                    UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE,
                    "unknown",
                    "Action id is empty"
                );
                listenersSnapshot = List.copyOf(executionListeners);
            } else {
                RegisteredAction action = actionsById.get(normalized);
                if (action == null) {
                    LOG.warning(
                        "ux.action.execute.skipped",
                        "actionId", normalized,
                        "reason", "action_not_registered"
                    );
                    result = UndoRedoManager.CommandResult.skipped(
                        UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE,
                        normalized,
                        "Action is not registered"
                    );
                    listenersSnapshot = List.copyOf(executionListeners);
                } else if (!action.availability().getAsBoolean()) {
                    String reason = unavailableReason(normalized);
                    LOG.warning(
                        "ux.action.execute.skipped",
                        "actionId", normalized,
                        "reason", reason
                    );
                    result = UndoRedoManager.CommandResult.skipped(
                        UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE,
                        normalized,
                        reason
                    );
                    listenersSnapshot = List.copyOf(executionListeners);
                } else {
                    UserActionCommand command = action.commandFactory().get();
                    if (command == null) {
                        LOG.warning(
                            "ux.action.execute.skipped",
                            "actionId", normalized,
                            "reason", "command_factory_returned_null"
                        );
                        result = UndoRedoManager.CommandResult.skipped(
                            UndoRedoManager.CommandStatus.SKIPPED_UNAVAILABLE,
                            normalized,
                            "Command factory returned null"
                        );
                    } else {
                        result = undoRedoManager.execute(new ActionWrapperCommand(action, command));
                    }
                    listenersSnapshot = List.copyOf(executionListeners);
                }
            }
        }

        notifyExecutionListeners(result, listenersSnapshot);
        return result;
    }

    public UndoRedoManager getUndoRedoManager() {
        return undoRedoManager;
    }

    private String normalizeActionId(String actionId) {
        if (actionId == null) {
            return null;
        }
        String trimmed = actionId.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matches(RegisteredAction action, String needle) {
        return action.actionId().toLowerCase().contains(needle)
            || action.label().toLowerCase().contains(needle)
            || action.category().toLowerCase().contains(needle);
    }

    private void notifyExecutionListeners(
        UndoRedoManager.CommandResult result,
        List<ExecutionListener> listeners
    ) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (ExecutionListener listener : listeners) {
            try {
                listener.onActionExecuted(result.actionId(), result);
            } catch (RuntimeException ex) {
                LOG.warning(
                    "ux.action.listener.failed",
                    "actionId", result.actionId(),
                    "error", ex.getMessage()
                );
            }
        }
    }

    private static final class ActionWrapperCommand implements UserActionCommand {
        private final RegisteredAction action;
        private final UserActionCommand delegate;

        private ActionWrapperCommand(RegisteredAction action, UserActionCommand delegate) {
            this.action = action;
            this.delegate = delegate;
        }

        @Override
        public String actionId() {
            return action.actionId();
        }

        @Override
        public String label() {
            return action.label();
        }

        @Override
        public String category() {
            return action.category();
        }

        @Override
        public boolean canExecute() {
            return delegate.canExecute();
        }

        @Override
        public boolean canUndo() {
            return delegate.canUndo();
        }

        @Override
        public void execute() {
            delegate.execute();
        }

        @Override
        public void undo() {
            delegate.undo();
        }
    }

    public record RegisteredAction(
        String actionId,
        String label,
        String category,
        String defaultShortcut,
        Supplier<UserActionCommand> commandFactory,
        BooleanSupplier availability,
        Supplier<String> unavailableReason,
        boolean safetyCritical
    ) {
        public RegisteredAction {
            actionId = normalize(actionId, "unknown.action");
            label = normalize(label, actionId);
            category = normalize(category, "general");
            defaultShortcut = defaultShortcut == null ? "" : defaultShortcut.trim();
            commandFactory = Objects.requireNonNull(commandFactory, "commandFactory");
            availability = availability == null ? () -> true : availability;
            unavailableReason = unavailableReason == null ? () -> "Action is unavailable" : unavailableReason;
        }

        public static RegisteredAction of(
            String actionId,
            String label,
            String category,
            String defaultShortcut,
            Supplier<UserActionCommand> commandFactory
        ) {
            return new RegisteredAction(
                actionId,
                label,
                category,
                defaultShortcut,
                commandFactory,
                () -> true,
                () -> "Action is unavailable",
                false
            );
        }

        private static String normalize(String value, String fallback) {
            if (value == null) {
                return fallback;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? fallback : trimmed;
        }
    }

    public record ActionAvailability(
        String actionId,
        boolean registered,
        boolean available,
        String unavailableReason
    ) {
        public ActionAvailability {
            actionId = actionId == null ? "" : actionId.trim();
            unavailableReason = unavailableReason == null ? "" : unavailableReason.trim();
        }
    }

    @FunctionalInterface
    public interface ExecutionListener {
        void onActionExecuted(String actionId, UndoRedoManager.CommandResult result);
    }
}
