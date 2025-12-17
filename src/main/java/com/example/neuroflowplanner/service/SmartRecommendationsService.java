package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SmartRecommendationsService {

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

    public CompletableFuture<String> getRecommendations(List<Task> tasks) {
        if (tasks.isEmpty()) return CompletableFuture.completedFuture("Нет задач для анализа");

        long overdue = tasks.stream().filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(LocalDate.now())).count();
        long urgent = tasks.stream().filter(t -> t.getDeadline() != null && ChronoUnit.DAYS.between(LocalDate.now(), t.getDeadline()) <= 3 && !t.getDeadline().isBefore(LocalDate.now())).count();
        double avgComplexity = tasks.stream().mapToInt(Task::getComplexity).average().orElse(0);

        String prompt = """
            Дай 3-4 кратких совета по оптимизации работы на основе статистики:
            - Всего задач: %d
            - Просроченных: %d
            - Срочных (до 3 дней): %d
            - Средняя сложность: %.1f/10
            
            Советы должны быть конкретными и практичными.
            """.formatted(tasks.size(), overdue, urgent, avgComplexity);

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
            .thenApply(r -> r.statusCode() == 200 ? extractContent(r.body()) : fallback(tasks, overdue, urgent, avgComplexity))
            .exceptionally(e -> fallback(tasks, overdue, urgent, avgComplexity));
    }

    private String fallback(List<Task> tasks, long overdue, long urgent, double avgComplexity) {
        StringBuilder sb = new StringBuilder("💡 Рекомендации:\n\n");
        if (overdue > 0) sb.append("🔴 Просрочено ").append(overdue).append(" задач — разберитесь с ними в первую очередь\n");
        if (urgent > 0) sb.append("🟠 ").append(urgent).append(" срочных задач — спланируйте время на них\n");
        if (avgComplexity > 7) sb.append("⚠️ Высокая сложность — разбивайте задачи на подзадачи\n");
        if (tasks.size() > 10) sb.append("📋 Много задач — используйте фильтры и приоритизацию\n");
        if (sb.toString().equals("💡 Рекомендации:\n\n")) sb.append("✅ Всё под контролем!");
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
