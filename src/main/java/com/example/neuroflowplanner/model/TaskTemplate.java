package com.example.neuroflowplanner.model;

import java.time.LocalDate;
import java.util.UUID;

public class TaskTemplate {
    private final String id;
    private final String name;
    private final String title;
    private final String description;
    private final int complexity;
    private final int daysUntilDeadline;
    private final String tags;

    public TaskTemplate(String name, String title, String description, int complexity, int daysUntilDeadline, String tags) {
        this(UUID.randomUUID().toString(), name, title, description, complexity, daysUntilDeadline, tags);
    }

    public TaskTemplate(String id, String name, String title, String description, int complexity, int daysUntilDeadline, String tags) {
        this.id = id;
        this.name = name;
        this.title = title;
        this.description = description;
        this.complexity = complexity;
        this.daysUntilDeadline = daysUntilDeadline;
        this.tags = tags != null ? tags : "";
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getComplexity() { return complexity; }
    public int getDaysUntilDeadline() { return daysUntilDeadline; }
    public String getTags() { return tags; }

    public Task createTask() {
        return new Task(UUID.randomUUID().toString(), title, description, 
            LocalDate.now().plusDays(daysUntilDeadline), complexity, null, tags);
    }

    @Override
    public String toString() { return name; }
}
