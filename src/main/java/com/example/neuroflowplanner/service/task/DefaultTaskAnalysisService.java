package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.AISchedulingEngine;
import com.example.neuroflowplanner.service.AutoPrioritizationService;
import com.example.neuroflowplanner.service.ProductivityAnalysisService;
import com.example.neuroflowplanner.service.SmartRecommendationsService;
import com.example.neuroflowplanner.service.TimePredictionService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DefaultTaskAnalysisService implements TaskAnalysisService {
    private final AISchedulingEngine aiScheduling;
    private final AutoPrioritizationService autoPrioritization;
    private final TimePredictionService timePrediction;
    private final SmartRecommendationsService recommendations;
    private final ProductivityAnalysisService productivityAnalysis;

    public DefaultTaskAnalysisService() {
        this(
            new AISchedulingEngine(),
            new AutoPrioritizationService(),
            new TimePredictionService(),
            new SmartRecommendationsService(),
            new ProductivityAnalysisService()
        );
    }

    DefaultTaskAnalysisService(
        AISchedulingEngine aiScheduling,
        AutoPrioritizationService autoPrioritization,
        TimePredictionService timePrediction,
        SmartRecommendationsService recommendations,
        ProductivityAnalysisService productivityAnalysis
    ) {
        this.aiScheduling = aiScheduling;
        this.autoPrioritization = autoPrioritization;
        this.timePrediction = timePrediction;
        this.recommendations = recommendations;
        this.productivityAnalysis = productivityAnalysis;
    }

    @Override
    public void calculatePriority(Task task) {
        if (task == null) {
            return;
        }
        aiScheduling.calculatePriority(task);
    }

    @Override
    public CompletableFuture<String> analyzeTask(Task task) {
        return aiScheduling.analyzeTaskWithAI(task);
    }

    @Override
    public CompletableFuture<String> prioritizeWithAi(List<Task> tasks) {
        return autoPrioritization.prioritizeWithAI(tasks);
    }

    @Override
    public String autoSchedule(List<Task> tasks, int dailyComplexityBudget) {
        return aiScheduling.autoSchedule(tasks, dailyComplexityBudget);
    }

    @Override
    public CompletableFuture<String> predictTime(Task task) {
        return timePrediction.predictTime(task);
    }

    @Override
    public CompletableFuture<String> recommendations(List<Task> tasks) {
        return recommendations.getRecommendations(tasks);
    }

    @Override
    public CompletableFuture<String> productivityAnalysis(List<Task> tasks) {
        return productivityAnalysis.analyzeProductivity(tasks);
    }
}
