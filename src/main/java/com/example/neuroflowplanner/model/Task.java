package com.example.neuroflowplanner.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public class Task {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty title = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> deadline = new SimpleObjectProperty<>();
    private final IntegerProperty complexity = new SimpleIntegerProperty();
    private final DoubleProperty smartPriority = new SimpleDoubleProperty();
    private final StringProperty aiInsight = new SimpleStringProperty();
    private final StringProperty parentId = new SimpleStringProperty();
    private final StringProperty tags = new SimpleStringProperty("");
    private final StringProperty recurrence = new SimpleStringProperty(""); // daily, weekly, monthly, yearly
    private final StringProperty dependsOn = new SimpleStringProperty(""); // comma-separated task IDs
    private final BooleanProperty archived = new SimpleBooleanProperty(false);
    private final LongProperty trackedMinutes = new SimpleLongProperty(0);
    private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> startTime = new SimpleObjectProperty<>();
    private final BooleanProperty completed = new SimpleBooleanProperty(false);
    private final ObjectProperty<LocalDate> completedDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalTime> deadlineTime = new SimpleObjectProperty<>();
    private final ObservableList<Task> subtasks = FXCollections.observableArrayList();

    public Task(String title, String description, LocalDate deadline, int complexity) {
        this(UUID.randomUUID().toString(), title, description, deadline, complexity, null, "", "");
    }

    public Task(String id, String title, String description, LocalDate deadline, int complexity) {
        this(id, title, description, deadline, complexity, null, "", "");
    }

    public Task(String id, String title, String description, LocalDate deadline, int complexity, String parentId) {
        this(id, title, description, deadline, complexity, parentId, "", "");
    }

    public Task(String id, String title, String description, LocalDate deadline, int complexity, String parentId, String tags) {
        this(id, title, description, deadline, complexity, parentId, tags, "");
    }

    public Task(String id, String title, String description, LocalDate deadline, int complexity, String parentId, String tags, String recurrence) {
        this.id.set(id);
        this.title.set(title);
        this.description.set(description);
        this.deadline.set(deadline);
        this.complexity.set(complexity);
        this.parentId.set(parentId);
        this.tags.set(tags != null ? tags : "");
        this.recurrence.set(recurrence != null ? recurrence : "");
    }

    public StringProperty titleProperty() { return title; }
    public StringProperty descriptionProperty() { return description; }
    public ObjectProperty<LocalDate> deadlineProperty() { return deadline; }
    public IntegerProperty complexityProperty() { return complexity; }
    public DoubleProperty smartPriorityProperty() { return smartPriority; }
    public StringProperty aiInsightProperty() { return aiInsight; }

    public String getId() { return id.get(); }
    public String getTitle() { return title.get(); }
    public String getDescription() { return description.get(); }
    public LocalDate getDeadline() { return deadline.get(); }
    public int getComplexity() { return complexity.get(); }
    public double getSmartPriority() { return smartPriority.get(); }
    public String getAiInsight() { return aiInsight.get(); }

    public void setSmartPriority(double value) { smartPriority.set(value); }
    public void setAiInsight(String value) { aiInsight.set(value); }

    public String getParentId() { return parentId.get(); }
    public void setParentId(String value) { parentId.set(value); }
    public StringProperty parentIdProperty() { return parentId; }

    public String getTags() { return tags.get(); }
    public void setTags(String value) { tags.set(value != null ? value : ""); }
    public StringProperty tagsProperty() { return tags; }

    public String getRecurrence() { return recurrence.get(); }
    public void setRecurrence(String value) { recurrence.set(value != null ? value : ""); }
    public StringProperty recurrenceProperty() { return recurrence; }
    public boolean isRecurring() { return recurrence.get() != null && !recurrence.get().isEmpty(); }

    /**
     * @deprecated Legacy CSV projection for compatibility only.
     * Use task dependency APIs from TaskApplicationService instead.
     */
    @Deprecated(since = "7.0", forRemoval = false)
    public String getDependsOn() { return dependsOn.get(); }

    /**
     * @deprecated Legacy CSV projection for compatibility only.
     * Use task dependency APIs from TaskApplicationService instead.
     */
    @Deprecated(since = "7.0", forRemoval = false)
    public void setDependsOn(String value) { dependsOn.set(value != null ? value : ""); }

    /**
     * @deprecated Legacy CSV projection for compatibility only.
     * Use task dependency APIs from TaskApplicationService instead.
     */
    @Deprecated(since = "7.0", forRemoval = false)
    public StringProperty dependsOnProperty() { return dependsOn; }

    /**
     * @deprecated Legacy CSV projection for compatibility only.
     * Use task dependency APIs from TaskApplicationService instead.
     */
    @Deprecated(since = "7.0", forRemoval = false)
    public boolean hasDependencies() { return dependsOn.get() != null && !dependsOn.get().isEmpty(); }

    public boolean isArchived() { return archived.get(); }
    public void setArchived(boolean value) { archived.set(value); }
    public BooleanProperty archivedProperty() { return archived; }

    public long getTrackedMinutes() { return trackedMinutes.get(); }
    public void setTrackedMinutes(long value) { trackedMinutes.set(value); }
    public void addTrackedMinutes(long mins) { trackedMinutes.set(trackedMinutes.get() + mins); }
    public LongProperty trackedMinutesProperty() { return trackedMinutes; }

    public LocalDate getStartDate() { return startDate.get(); }
    public void setStartDate(LocalDate value) { startDate.set(value); }
    public ObjectProperty<LocalDate> startDateProperty() { return startDate; }
    public boolean hasStartDate() { return startDate.get() != null; }
    public LocalTime getStartTime() { return startTime.get(); }
    public void setStartTime(LocalTime value) { startTime.set(value); }
    public ObjectProperty<LocalTime> startTimeProperty() { return startTime; }
    public boolean hasStartTime() { return startTime.get() != null; }
    public LocalDateTime getStartDateTime() {
        if (startDate.get() == null) {
            return null;
        }
        return LocalDateTime.of(startDate.get(), startTime.get() != null ? startTime.get() : LocalTime.MIN);
    }
    public boolean isStarted() {
        if (startDate.get() == null) {
            return true;
        }
        if (startTime.get() == null) {
            return !startDate.get().isAfter(LocalDate.now());
        }
        return !getStartDateTime().isAfter(LocalDateTime.now());
    }

    public boolean isCompleted() { return completed.get(); }
    public void setCompleted(boolean value) { completed.set(value); }
    public BooleanProperty completedProperty() { return completed; }
    
    public LocalTime getDeadlineTime() { return deadlineTime.get(); }
    public void setDeadlineTime(LocalTime value) { deadlineTime.set(value); }
    public ObjectProperty<LocalTime> deadlineTimeProperty() { return deadlineTime; }
    public boolean hasDeadlineTime() { return deadlineTime.get() != null; }
    public LocalDateTime getDeadlineDateTime() {
        if (deadline.get() == null) {
            return null;
        }
        return LocalDateTime.of(deadline.get(), deadlineTime.get() != null ? deadlineTime.get() : LocalTime.MAX);
    }

    public LocalDate getCompletedDate() { return completedDate.get(); }
    public void setCompletedDate(LocalDate value) { completedDate.set(value); }
    public ObjectProperty<LocalDate> completedDateProperty() { return completedDate; }

    public ObservableList<Task> getSubtasks() { return subtasks; }
    public boolean hasSubtasks() { return !subtasks.isEmpty(); }
    public boolean isSubtask() { return parentId.get() != null; }

    @Override
    public String toString() {
        return getTitle() != null ? getTitle() : "";
    }
}
