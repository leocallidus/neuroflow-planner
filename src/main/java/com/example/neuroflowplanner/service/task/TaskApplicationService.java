package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;

import java.util.List;
import java.util.Map;

public interface TaskApplicationService {
    List<Task> loadTasks();

    void saveTask(Task task);

    void saveTasks(List<Task> tasks);

    TaskBulkOperationResult saveTasksBulk(List<Task> tasks);

    void deleteTask(String taskId);

    TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks);

    TaskBulkOperationResult deleteTasksBulk(List<String> taskIds);

    TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId);

    void linkDependency(String dependentTaskId, String blockerTaskId);

    void saveDependencies(String taskId, List<String> blockerTaskIds);

    List<String> loadDependencies(String taskId);

    List<TaskDependencyEdge> loadAllDependencyEdges();

    void deleteDependenciesForTask(String taskId);

    CriticalPathResult computeCriticalPathFullGraph();

    CriticalPathResult computeCriticalPathForRootTask(String rootTaskId);

    List<TaskTemplate> loadAllTemplates();

    void saveTemplate(TaskTemplate template);
}
