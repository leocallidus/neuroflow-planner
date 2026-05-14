package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.Task;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TaskAnalysisService {
    void calculatePriority(Task task);

    CompletableFuture<String> analyzeTask(Task task);

    CompletableFuture<String> prioritizeWithAi(List<Task> tasks);

    String autoSchedule(List<Task> tasks, int dailyComplexityBudget);

    CompletableFuture<String> predictTime(Task task);

    CompletableFuture<String> recommendations(List<Task> tasks);

    CompletableFuture<String> productivityAnalysis(List<Task> tasks);
}
