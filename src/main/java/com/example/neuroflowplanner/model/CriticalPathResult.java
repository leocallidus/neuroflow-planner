package com.example.neuroflowplanner.model;

import java.util.List;

public record CriticalPathResult(
        CriticalPathScopeMode scopeMode,
        String scopeRootTaskId,
        int projectDuration,
        int taskCount,
        int edgeCount,
        int criticalTaskCount,
        int criticalEdgeCount,
        List<String> topologicalOrderTaskIds,
        List<String> criticalChainTaskIds,
        List<CriticalPathTaskMetrics> taskMetrics,
        List<CriticalPathEdgeMetrics> edgeMetrics
) {
    public CriticalPathResult {
        topologicalOrderTaskIds = topologicalOrderTaskIds == null ? List.of() : List.copyOf(topologicalOrderTaskIds);
        criticalChainTaskIds = criticalChainTaskIds == null ? List.of() : List.copyOf(criticalChainTaskIds);
        taskMetrics = taskMetrics == null ? List.of() : List.copyOf(taskMetrics);
        edgeMetrics = edgeMetrics == null ? List.of() : List.copyOf(edgeMetrics);
    }

    public static CriticalPathResult empty(CriticalPathScopeMode scopeMode, String scopeRootTaskId) {
        return new CriticalPathResult(
                scopeMode == null ? CriticalPathScopeMode.FULL_GRAPH : scopeMode,
                scopeRootTaskId,
                0,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
