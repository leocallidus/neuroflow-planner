package com.example.neuroflowplanner.model;

public record CriticalPathEdgeMetrics(
        String blockerTaskId,
        String dependentTaskId,
        int totalSlack,
        boolean critical
) {
}
