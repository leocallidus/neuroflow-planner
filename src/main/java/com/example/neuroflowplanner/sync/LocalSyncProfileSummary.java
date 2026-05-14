package com.example.neuroflowplanner.sync;

public record LocalSyncProfileSummary(
        int taskCount,
        int dependencyCount,
        int timeSessionCount,
        int templateCount,
        int goalCount,
        int moodEntryCount,
        int pendingOutboxCount) {

    public int trackedEntityCount() {
        return Math.max(0, taskCount)
                + Math.max(0, dependencyCount)
                + Math.max(0, timeSessionCount)
                + Math.max(0, templateCount)
                + Math.max(0, goalCount)
                + Math.max(0, moodEntryCount);
    }

    public int totalRelevantItems() {
        return trackedEntityCount() + Math.max(0, pendingOutboxCount);
    }

    public boolean isEmpty() {
        return totalRelevantItems() <= 0;
    }
}
