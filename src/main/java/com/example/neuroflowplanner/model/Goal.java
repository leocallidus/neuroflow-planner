package com.example.neuroflowplanner.model;

public class Goal {
    private final String id;
    private String title;
    private String period;
    private int target;
    private int progress;
    private String createdAt;
    private String updatedAt;

    public Goal(String id, String title, String period, int target, int progress, String createdAt, String updatedAt) {
        this.id = id;
        this.title = title;
        this.period = period;
        this.target = target;
        this.progress = progress;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isCompleted() {
        return target > 0 && progress >= target;
    }
}
