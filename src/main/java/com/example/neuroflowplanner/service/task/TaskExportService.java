package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.Task;

import java.io.File;
import java.util.List;

public interface TaskExportService {
    void exportInsight(File file, String extension, String content) throws Exception;

    default String serializeTasksJson(List<Task> tasks) throws Exception {
        throw new UnsupportedOperationException("Task JSON export is not implemented");
    }

    default void exportTasksJson(File file, List<Task> tasks) throws Exception {
        throw new UnsupportedOperationException("Task JSON export is not implemented");
    }
}
