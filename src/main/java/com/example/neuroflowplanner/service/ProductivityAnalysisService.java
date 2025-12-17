package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProductivityAnalysisService {

    private static final String DEFAULT_API_URL = "http://localhost:11434/api/chat";
    private static final String DEFAULT_MODEL = "llama3";
    private final HttpClient client = HttpClient.newHttpClient();

    private String getApiUrl() {
        String url = ConfigManager.getProperty("api.url");
        return url != null ? url : DEFAULT_API_URL;
    }

    private String getModel() {
        String model = ConfigManager.getProperty("api.model");
        return model != null ? model : DEFAULT_MODEL;
    }

    private String getApiKey() {
        return ConfigManager.getProperty("api.key");
    }

    public CompletableFuture<String> analyzeProductivity(List<Task> tasks) {
        if (tasks.isEmpty()) return CompletableFuture.completedFuture("Нет данных для анализа");

        long totalTracked = tasks.stream().mapToLong(Task::getTrackedMinutes).sum();
        long archived = tasks.stream().filter(Task::isArchived).count();
        double avgComplexity = tasks.stream().mapToInt(Task::getComplexity).average().orElse(0);
        long withSubtasks = tasks.stream().filter(Task::hasSubtasks).count();

        String prompt = """
            Проанализируй паттерны продуктивности и дай 3-4 инсайта:
            - Задач: %d (архивировано: %d)
            - Отслежено времени: %d мин
            - Средняя сложность: %.1f/10
            - С подзадачами: %d
            
            Выяви паттерны и дай рекомендации по улучшению продуктивности.
            """.formatted(tasks.size(), archived, totalTracked, avgComplexity, withSubtasks);

        String json = """
            {"model":"%s","messages":[{"role":"user","content":"%s"}],"stream":false}
            """.formatted(getModel(), escapeJson(prompt));

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(getApiUrl()))
            .header("Content-Type", "application/json");
        
        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(r -> r.statusCode() == 200 ? extractContent(r.body()) : fallback(tasks, totalTracked, archived, avgComplexity))
            .exceptionally(e -> fallback(tasks, totalTracked, archived, avgComplexity));
    }

    private String fallback(List<Task> tasks, long totalTracked, long archived, double avgComplexity) {
        StringBuilder sb = new StringBuilder("📈 Анализ продуктивности:\n\n");
        
        double completionRate = tasks.isEmpty() ? 0 : (archived * 100.0 / tasks.size());
        sb.append(String.format("✅ Выполнено: %.0f%% задач\n", completionRate));
        sb.append(String.format("⏱ Отслежено: %d ч %d мин\n", totalTracked / 60, totalTracked % 60));
        
        if (completionRate < 30) sb.append("\n💡 Низкий % выполнения — попробуйте разбивать задачи\n");
        if (avgComplexity > 7) sb.append("💡 Высокая сложность — делегируйте или упрощайте\n");
        if (totalTracked == 0) sb.append("💡 Включите трекинг времени для точного анализа\n");
        
        return sb.toString();
    }

    private String extractContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return "Ошибка";
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = start;
        while (end < json.length() && !(json.charAt(end) == '"' && json.charAt(end - 1) != '\\')) end++;
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String escapeJson(String t) {
        return t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
