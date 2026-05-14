package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.model.Task;
import java.util.concurrent.CompletableFuture;

public class TimePredictionService {

    public TimePredictionService() {
        // AiClientFactory is used directly, no initialization needed
    }

    public CompletableFuture<String> predictTime(Task task) {
        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        
        // В офлайн-режиме возвращаем fallback
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return CompletableFuture.completedFuture(fallback(task));
        }
        
        String prompt = """
            Оцени время выполнения задачи в часах/минутах.
            Название: %s
            Описание: %s
            Сложность: %d/10
            
            Ответь кратко: "Оценка: X часов Y минут" и 1 строка обоснования.
            """.formatted(task.getTitle(), 
                task.getDescription().isEmpty() ? "нет" : task.getDescription(),
                task.getComplexity());

        AiRequestOptions options = AiRequestOptions.builder()
            .model(aiClient.getDefaultModel())
            .systemPrompt("Ты помощник по оценке времени выполнения задач. Отвечай кратко и конкретно.")
            .build();

        return aiClient.sendChatMessage(prompt, options)
            .thenApply(response -> {
                if (response.success() && response.content() != null) {
                    return response.content();
                }
                return fallback(task);
            })
            .exceptionally(e -> fallback(task));
    }

    private String fallback(Task task) {
        int mins = task.getComplexity() * 30;
        int h = mins / 60, m = mins % 60;
        return "Оценка: " + (h > 0 ? h + " ч " : "") + m + " мин\n(на основе сложности " + task.getComplexity() + "/10)";
    }
}
