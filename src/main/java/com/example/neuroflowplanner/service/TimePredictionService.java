package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

public class TimePredictionService {

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

    public CompletableFuture<String> predictTime(Task task) {
        String prompt = """
            Оцени время выполнения задачи в часах/минутах.
            Название: %s
            Описание: %s
            Сложность: %d/10
            
            Ответь кратко: "Оценка: X часов Y минут" и 1 строка обоснования.
            """.formatted(task.getTitle(), 
                task.getDescription().isEmpty() ? "нет" : task.getDescription(),
                task.getComplexity());

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
            .thenApply(r -> r.statusCode() == 200 ? extractContent(r.body()) : fallback(task))
            .exceptionally(e -> fallback(task));
    }

    private String fallback(Task task) {
        int mins = task.getComplexity() * 30;
        int h = mins / 60, m = mins % 60;
        return "⏱ Оценка: " + (h > 0 ? h + " ч " : "") + m + " мин\n(на основе сложности " + task.getComplexity() + "/10)";
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
