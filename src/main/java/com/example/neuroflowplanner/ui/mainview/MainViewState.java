package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.Task;

import java.time.Instant;
import java.util.List;

public record MainViewState(
    boolean initialized,
    List<Task> tasks,
    String activeSection,
    String statusMessage,
    CriticalPathResult criticalPathResult,
    boolean undoAvailable,
    boolean redoAvailable,
    String nextUndoLabel,
    String nextRedoLabel,
    Instant updatedAt
) {
    public static MainViewState initial() {
        return new MainViewState(
            false,
            List.of(),
            "tasks",
            "ready",
            CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null),
            false,
            false,
            "",
            "",
            Instant.now()
        );
    }

    public MainViewState markInitialized(List<Task> loadedTasks) {
        return markInitialized(loadedTasks, criticalPathResult);
    }

    public MainViewState markInitialized(List<Task> loadedTasks, CriticalPathResult criticalPath) {
        List<Task> safeTasks = loadedTasks == null ? List.of() : List.copyOf(loadedTasks);
        CriticalPathResult safeCriticalPath = criticalPath == null
            ? CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null)
            : criticalPath;
        return new MainViewState(
            true,
            safeTasks,
            activeSection,
            statusMessage,
            safeCriticalPath,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    public MainViewState withCriticalPath(CriticalPathResult criticalPath) {
        CriticalPathResult safeCriticalPath = criticalPath == null
            ? CriticalPathResult.empty(CriticalPathScopeMode.FULL_GRAPH, null)
            : criticalPath;
        return new MainViewState(
            initialized,
            tasks,
            activeSection,
            statusMessage,
            safeCriticalPath,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    public MainViewState withUndoRedoState(
        boolean undoAvailable,
        boolean redoAvailable,
        String nextUndoLabel,
        String nextRedoLabel
    ) {
        return new MainViewState(
            initialized,
            tasks,
            activeSection,
            statusMessage,
            criticalPathResult,
            undoAvailable,
            redoAvailable,
            safeLabel(nextUndoLabel),
            safeLabel(nextRedoLabel),
            Instant.now()
        );
    }

    public MainViewState withStatusMessage(String statusMessage) {
        String safeStatus = statusMessage == null || statusMessage.isBlank()
            ? this.statusMessage
            : statusMessage;
        return new MainViewState(
            initialized,
            tasks,
            activeSection,
            safeStatus,
            criticalPathResult,
            undoAvailable,
            redoAvailable,
            nextUndoLabel,
            nextRedoLabel,
            Instant.now()
        );
    }

    private static String safeLabel(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
