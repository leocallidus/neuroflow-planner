package com.example.neuroflowplanner.model;

public record CriticalPathTaskMetrics(
        String taskId,
        int duration,
        int earliestStart,
        int earliestFinish,
        int latestStart,
        int latestFinish,
        int totalSlack,
        boolean critical
) {
}
