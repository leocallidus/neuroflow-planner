package com.example.neuroflowplanner.service.imagejob;

/**
 * Persisted lifecycle states for image generation jobs.
 */
public enum ImageJobState {
    QUEUED,
    SUBMITTING,
    SUBMITTED,
    POLLING,
    DOWNLOADING,
    SAVING,
    PAUSED,
    FAILED,
    CANCELLED,
    DONE;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == DONE;
    }

    public boolean isRunning() {
        return this == QUEUED
            || this == SUBMITTING
            || this == SUBMITTED
            || this == POLLING
            || this == DOWNLOADING
            || this == SAVING;
    }

    public boolean canResumeFromRequestId() {
        return this == SUBMITTED
            || this == POLLING
            || this == DOWNLOADING
            || this == SAVING
            || this == FAILED
            || this == PAUSED;
    }
}
