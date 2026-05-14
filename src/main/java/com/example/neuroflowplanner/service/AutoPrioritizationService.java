package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.AsyncContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPrioritizationService {

    public AutoPrioritizationService() {
        // AiClientFactory is used directly, no initialization needed
    }

    public CompletableFuture<String> prioritizeWithAI(List<Task> tasks) {
        AsyncContext.ensureRequestId();
        if (tasks.isEmpty())
            return CompletableFuture.completedFuture("Нет задач для анализа");

        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();

        // В офлайн-режиме возвращаем fallback
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return CompletableFuture.completedFuture(fallbackPrioritization(tasks));
        }

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

        String systemPrompt = "Ты эксперт по приоритизации задач. Отвечай кратко, по делу. Не используй эмодзи.";

        AiRequestOptions options = AiRequestOptions.builder()
                .model(aiClient.getDefaultModel())
                .systemPrompt(systemPrompt)
                .build();

        return aiClient.sendChatMessage(prompt, options)
                .thenApply(response -> {
                    if (response.success() && response.content() != null) {
                        String result = sanitizeText(response.content());
                        if (result != null && !result.isBlank()) {
                            boolean applied = applyPriorities(tasks, result);
                            if (applied) {
                                return result;
                            }
                        }
                    }
                    return fallbackPrioritization(tasks);
                })
                .exceptionally(e -> fallbackPrioritization(tasks));
    }

    private boolean applyPriorities(List<Task> tasks, String aiResponse) {
        if (aiResponse == null || aiResponse.isBlank()) {
            return false;
        }
        String[] lines = aiResponse.split("\\R");
        boolean[] updated = new boolean[tasks.size()];
        boolean anyApplied = applyPrioritiesByIndex(tasks, lines, updated);
        for (int i = 0; i < tasks.size(); i++) {
            if (updated[i]) {
                continue;
            }
            Task task = tasks.get(i);
            Double priority = extractPriorityForTask(task.getTitle(), lines, aiResponse);
            if (priority != null && priority > 0) {
                task.setSmartPriority(clampPriority(priority));
                updated[i] = true;
                anyApplied = true;
            }
        }
        return anyApplied;
    }

    private Double extractPriorityForTask(String title, String[] lines, String response) {
        String normalizedTitle = normalizeText(title);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String normalizedLine = normalizeText(line);
            if (normalizedLine.contains(normalizedTitle)) {
                Double parsed = parsePriority(line);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        String pattern = "(?i)\\b" + Pattern.quote(title.trim())
                + "\\b.*?(?:приоритет|priority)\\s*[:=\\-]?\\s*(10|[1-9](?:[\\.,]\\d+)?)";
        Matcher matcher = Pattern.compile(pattern).matcher(response);
        if (matcher.find()) {
            return parseNumber(matcher.group(1));
        }
        return null;
    }

    private boolean applyPrioritiesByIndex(List<Task> tasks, String[] lines, boolean[] updated) {
        boolean anyApplied = false;
        Pattern indexPattern = Pattern.compile("^\\s*(\\d+)[\\).\\-]?");
        for (String line : lines) {
            Matcher matcher = indexPattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (index < 1 || index > tasks.size()) {
                continue;
            }
            Double priority = parsePriority(line);
            if (priority != null && priority > 0) {
                tasks.get(index - 1).setSmartPriority(clampPriority(priority));
                updated[index - 1] = true;
                anyApplied = true;
            }
        }
        return anyApplied;
    }

    private String fallbackPrioritization(List<Task> tasks) {
        StringBuilder sb = new StringBuilder("Автоматическая приоритизация:\n\n");
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
            if (days <= 0)
                urgency = 10.0;
            else if (days <= 3)
                urgency = 9.0;
            else if (days <= 7)
                urgency = 7.0;
            else if (days <= 14)
                urgency = 5.0;
            else
                urgency = 3.0;
        }
        double complexity = task.getComplexity() * 0.3;
        return Math.min(10.0, Math.round((urgency * 0.7 + complexity) * 10.0) / 10.0);
    }

    private Double parsePriority(String line) {
        if (line == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)(?:приоритет|priority)[^0-9]*(10|[1-9](?:[\\.,]\\d+)?)").matcher(line);
        if (matcher.find()) {
            return parseNumber(matcher.group(1));
        }
        return null;
    }

    private double parseNumber(String value) {
        if (value == null) {
            return 0;
        }
        String normalized = value.replace(',', '.').trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double clampPriority(double value) {
        if (value < 0) {
            return 0;
        }
        if (value > 10) {
            return 10;
        }
        return Math.round(value * 10.0) / 10.0;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]+", " ").trim();
    }

    private String sanitizeText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace("\uFEFF", "")
                .replace("\uFFFD", "")
                .replace("\u0000", "");
        return cleaned.stripLeading();
    }
}
