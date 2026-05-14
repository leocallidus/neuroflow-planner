package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.model.Task;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SmartRecommendationsService {

    public SmartRecommendationsService() {
        // AiClientFactory is used directly, no initialization needed
    }

    public CompletableFuture<String> getRecommendations(List<Task> tasks) {
        if (tasks.isEmpty()) return CompletableFuture.completedFuture("Нет задач для анализа");

        long overdue = tasks.stream().filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now())).count();
        long urgent = tasks.stream().filter(t -> t.getDeadline() != null && ChronoUnit.DAYS.between(LocalDate.now(), t.getDeadline()) <= 3 && !t.getDeadline().isBefore(LocalDate.now())).count();
        double avgComplexity = tasks.stream().mapToInt(Task::getComplexity).average().orElse(0);

        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        
        // В офлайн-режиме возвращаем fallback
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return CompletableFuture.completedFuture(fallback(tasks, overdue, urgent, avgComplexity));
        }

        String prompt = """
            Дай 3-4 кратких совета по оптимизации работы на основе статистики:
            - Всего задач: %d
            - Просроченных: %d
            - Срочных (до 3 дней): %d
            - Средняя сложность: %.1f/10
            
            Советы должны быть конкретными и практичными.
            """.formatted(tasks.size(), overdue, urgent, avgComplexity);

        AiRequestOptions options = AiRequestOptions.builder()
            .model(aiClient.getDefaultModel())
            .systemPrompt("Ты помощник по продуктивности. Давай краткие и практичные советы.")
            .build();

        return aiClient.sendChatMessage(prompt, options)
            .thenApply(response -> {
                if (response.success() && response.content() != null) {
                    return response.content();
                }
                return fallback(tasks, overdue, urgent, avgComplexity);
            })
            .exceptionally(e -> fallback(tasks, overdue, urgent, avgComplexity));
    }

    private String fallback(List<Task> tasks, long overdue, long urgent, double avgComplexity) {
        StringBuilder sb = new StringBuilder("Рекомендации:\n\n");
        if (overdue > 0) sb.append("Просрочено ").append(overdue).append(" задач — разберитесь с ними в первую очередь\n");
        if (urgent > 0) sb.append(urgent).append(" срочных задач — спланируйте время на них\n");
        if (avgComplexity > 7) sb.append("Высокая сложность — разбивайте задачи на подзадачи\n");
        if (tasks.size() > 10) sb.append("Много задач — используйте фильтры и приоритизацию\n");
        if (sb.toString().equals("Рекомендации:\n\n")) sb.append("Всё под контролем!");
        return sb.toString();
    }
}
