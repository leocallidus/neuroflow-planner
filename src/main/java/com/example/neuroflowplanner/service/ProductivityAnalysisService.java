package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.model.Task;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProductivityAnalysisService {

    public ProductivityAnalysisService() {
        // AiClientFactory is used directly, no initialization needed
    }

    public CompletableFuture<String> analyzeProductivity(List<Task> tasks) {
        if (tasks.isEmpty()) return CompletableFuture.completedFuture("Нет данных для анализа");

        long totalTracked = tasks.stream().mapToLong(Task::getTrackedMinutes).sum();
        long archived = tasks.stream().filter(Task::isArchived).count();
        double avgComplexity = tasks.stream().mapToInt(Task::getComplexity).average().orElse(0);
        long withSubtasks = tasks.stream().filter(Task::hasSubtasks).count();

        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        
        // В офлайн-режиме возвращаем fallback
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return CompletableFuture.completedFuture(fallback(tasks, totalTracked, archived, avgComplexity));
        }

        String prompt = """
            Проанализируй паттерны продуктивности и дай 3-4 инсайта:
            - Задач: %d (архивировано: %d)
            - Отслежено времени: %d мин
            - Средняя сложность: %.1f/10
            - С подзадачами: %d
            
            Выяви паттерны и дай рекомендации по улучшению продуктивности.
            """.formatted(tasks.size(), archived, totalTracked, avgComplexity, withSubtasks);

        AiRequestOptions options = AiRequestOptions.builder()
            .model(aiClient.getDefaultModel())
            .systemPrompt("Ты аналитик продуктивности. Давай конкретные инсайты на основе данных.")
            .build();

        return aiClient.sendChatMessage(prompt, options)
            .thenApply(response -> {
                if (response.success() && response.content() != null) {
                    return response.content();
                }
                return fallback(tasks, totalTracked, archived, avgComplexity);
            })
            .exceptionally(e -> fallback(tasks, totalTracked, archived, avgComplexity));
    }

    private String fallback(List<Task> tasks, long totalTracked, long archived, double avgComplexity) {
        StringBuilder sb = new StringBuilder("Анализ продуктивности:\n\n");
        
        double completionRate = tasks.isEmpty() ? 0 : (archived * 100.0 / tasks.size());
        sb.append(String.format("Выполнено: %.0f%% задач\n", completionRate));
        sb.append(String.format("Отслежено: %d ч %d мин\n", totalTracked / 60, totalTracked % 60));
        
        if (completionRate < 30) sb.append("\nНизкий % выполнения — попробуйте разбивать задачи\n");
        if (avgComplexity > 7) sb.append("Высокая сложность — делегируйте или упрощайте\n");
        if (totalTracked == 0) sb.append("Включите трекинг времени для точного анализа\n");
        
        return sb.toString();
    }
}
