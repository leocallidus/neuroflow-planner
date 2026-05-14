package com.example.neuroflowplanner.model;

import java.time.LocalDateTime;

public class TimeSession {
    private final String id;
    private final String taskId;
    private final LocalDateTime startedAt;
    private final long minutes;

    public TimeSession(String id, String taskId, LocalDateTime startedAt, long minutes) {
        this.id = id;
        this.taskId = taskId;
        this.startedAt = startedAt;
        this.minutes = minutes;
    }

    public String getId() {
        return id;
    }

    public String getTaskId() {
        return taskId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public long getMinutes() {
        return minutes;
    }
}
