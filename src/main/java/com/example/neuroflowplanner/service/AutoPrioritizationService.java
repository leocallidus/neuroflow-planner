package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AutoPrioritizationService {

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

    public CompletableFuture<String> prioritizeWithAI(List<Task> tasks) {
        if (tasks.isEmpty()) return CompletableFuture.completedFuture("Нет задач для анализа");

        StringBuilder taskList = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            long days = t.getDeadline() != null ? ChronoUnit.DAYS.between(LocalDate.now(), t.getDeadline()) : 999;
            taskList.append(String.format("%d. %s (дней: %d, сложность: %d)\n", 
                i + 1, t.getTitle(), days, t.getComplexity()));
        }

        String prompt = """
            Проанализируй список задач и определи приоритет каждой от 1 до 10.
            Учитывай: срочность (дни до дедлайна), сложность, зависимости.
            
            Задачи:
            %s
            
            Ответь в формате:
            1. [название] - приоритет: X (причина)
            """.formatted(taskList);

        String json = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "Ты эксперт по приоритизации задач. Отвечай кратко, по делу."},
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
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
            .thenApply(response -> {
                if (response.statusCode() == 200) {
                    String result = extractContent(response.body());
                    applyPriorities(tasks, result);
                    return result;
                }
                return fallbackPrioritization(tasks);
            })
            .exceptionally(e -> fallbackPrioritization(tasks));
    }

    private void applyPriorities(List<Task> tasks, String aiResponse) {
        for (Task task : tasks) {
            double priority = extractPriorityForTask(task.getTitle(), aiResponse);
            if (priority > 0) task.setSmartPriority(priority);
        }
    }

    private double extractPriorityForTask(String title, String response) {
        String lower = response.toLowerCase();
        String titleLower = title.toLowerCase();
        int idx = lower.indexOf(titleLower.substring(0, Math.min(10, titleLower.length())));
        if (idx == -1) return 0;
        
        int prIdx = lower.indexOf("приоритет:", idx);
        if (prIdx == -1 || prIdx > idx + 100) return 0;
        
        String sub = response.substring(prIdx + 10, Math.min(prIdx + 15, response.length())).trim();
        try {
            return Double.parseDouble(sub.replaceAll("[^0-9.]", "").substring(0, 1));
        } catch (Exception e) {
            return 0;
        }
    }

    private String fallbackPrioritization(List<Task> tasks) {
        StringBuilder sb = new StringBuilder("📊 Автоматическая приоритизация:\n\n");
        for (Task task : tasks) {
            double priority = calculateLocalPriority(task);
            task.setSmartPriority(priority);
            sb.append(String.format("• %s — приоритет: %.1f\n", task.getTitle(), priority));
        }
        return sb.toString();
    }

    private double calculateLocalPriority(Task task) {
        double urgency = 5.0;
        if (task.getDeadline() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
            if (days <= 0) urgency = 10.0;
            else if (days <= 3) urgency = 9.0;
            else if (days <= 7) urgency = 7.0;
            else if (days <= 14) urgency = 5.0;
            else urgency = 3.0;
        }
        double complexity = task.getComplexity() * 0.3;
        return Math.min(10.0, Math.round((urgency * 0.7 + complexity) * 10.0) / 10.0);
    }

    private String extractContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return "Ошибка парсинга";
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
