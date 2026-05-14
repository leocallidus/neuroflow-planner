package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.model.Task;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

public class AISchedulingEngine {

    private static final double URGENCY_WEIGHT = 0.6;
    private static final double COMPLEXITY_WEIGHT = 0.4;

    public AISchedulingEngine() {
        // AiClientFactory is used directly, no initialization needed
    }

    public void calculatePriority(Task task) {
        double urgencyScore = calculateUrgency(task.getDeadline());
        double priority = (urgencyScore * URGENCY_WEIGHT) + (task.getComplexity() * COMPLEXITY_WEIGHT);
        task.setSmartPriority(Math.round(priority * 10.0) / 10.0);
    }

    public CompletableFuture<String> analyzeTaskWithAI(Task task) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
        
        String description = task.getDescription();
        String descText = (description == null || description.trim().isEmpty()) 
            ? "не указано" 
            : description.trim();
        
        String complexityLevel;
        int complexity = task.getComplexity();
        if (complexity <= 3) complexityLevel = "низкая (простая задача)";
        else if (complexity <= 6) complexityLevel = "средняя";
        else if (complexity <= 8) complexityLevel = "высокая (требует концентрации)";
        else complexityLevel = "очень высокая (сложная задача)";
        
        String tagsText = task.getTags().isEmpty() ? "не указаны" : task.getTags();
        
        String prompt;
        String systemPrompt;
        
        // Разные промпты для выполненных и невыполненных задач
        if (task.isCompleted()) {
            // Промпт для выполненной задачи
            LocalDate completedDate = task.getCompletedDate();
            long daysBeforeDeadline = completedDate != null 
                ? ChronoUnit.DAYS.between(completedDate, task.getDeadline())
                : days;
            String completionStatus = daysBeforeDeadline >= 0 
                ? "до дедлайна осталось " + daysBeforeDeadline + " дней"
                : "просрочено на " + Math.abs(daysBeforeDeadline) + " дней";
            
            prompt = """
                Задача ВЫПОЛНЕНА! Проанализируй результат и дай рекомендации для развития.
                
                ## Информация о выполненной задаче:
                - **Название:** %s
                - **Описание:** %s
                - **Дедлайн:** %s
                - **Дата выполнения:** %s (%s)
                - **Сложность:** %d/10 (%s)
                - **Теги:** %s
                
                ## Что нужно сделать:
                1. Похвали за выполнение задачи
                2. Проанализируй, что можно было сделать лучше
                3. Предложи следующие цели для развития
                4. Дай советы по улучшению продуктивности
                
                ## Формат ответа (используй именно такие заголовки):
                
                ### Поздравляем!
                Краткая похвала за выполнение (1-2 предложения)
                
                ### Анализ выполнения
                - Что было сделано хорошо
                - Что можно улучшить в следующий раз
                
                ### Следующие цели
                - Предложи 2-3 логичных следующих шага или задачи
                - Как развить навыки, полученные при выполнении
                
                ### Советы по улучшению
                - Конкретный совет для повышения эффективности
                - Как избежать типичных ошибок
                - Рекомендация по тайм-менеджменту
                
                Отвечай на русском языке. Будь позитивен и мотивируй. НЕ используй эмодзи в ответе.
                """.formatted(
                    task.getTitle(),
                    descText,
                    task.getDeadline(),
                    completedDate != null ? completedDate.toString() : "сегодня",
                    completionStatus,
                    complexity,
                    complexityLevel,
                    tagsText
                );
            
            systemPrompt = """
                Ты - умный помощник по планированию задач и личностному развитию.
                Твоя цель - помочь пользователю расти и развиваться после выполнения задачи.
                
                Правила:
                - Начни с позитивной оценки выполненной работы
                - Анализируй задачу на основе её названия, описания и контекста
                - Предлагай конкретные следующие шаги для развития
                - Давай практичные советы по улучшению продуктивности
                - Мотивируй пользователя на новые достижения
                - НЕ используй эмодзи в ответе
                - Отвечай на русском языке
                - Используй Markdown для форматирования
                """;
        } else {
            // Промпт для невыполненной задачи (оригинальный)
            String urgency;
            if (days <= 0) urgency = "ПРОСРОЧЕНА";
            else if (days <= 3) urgency = "СРОЧНАЯ";
            else if (days <= 7) urgency = "скоро дедлайн";
            else urgency = "есть запас времени";
            
            prompt = """
                Проанализируй задачу и предоставь полезные рекомендации.
                
                ## Информация о задаче:
                - **Название:** %s
                - **Описание:** %s
                - **Дедлайн:** %s (осталось дней: %d)
                - **Сложность:** %d/10 (%s)
                - **Теги:** %s
                
                ## Что нужно сделать:
                1. Проанализируй задачу на основе названия и описания
                2. Оцени риски срыва дедлайна
                3. Предложи конкретный план действий
                
                ## Формат ответа (используй именно такие заголовки):
                
                ### Анализ
                Краткий анализ задачи (1-2 предложения)
                
                ### Риски
                - Перечисли возможные риски (если есть)
                
                ### Рекомендации
                - Конкретный совет 1
                - Конкретный совет 2
                - Конкретный совет 3 (опционально)
                
                ### Оценка времени
                Примерная оценка времени на выполнение
                
                Отвечай на русском языке. Будь конкретен и полезен. НЕ используй эмодзи в ответе.
                """.formatted(
                    task.getTitle(),
                    descText,
                    urgency,
                    days,
                    complexity,
                    complexityLevel,
                    tagsText
                );
            
            systemPrompt = """
                Ты - умный помощник по планированию задач и тайм-менеджменту. 
                Твоя цель - помочь пользователю эффективно выполнить задачу.
                
                Правила:
                - Анализируй задачу на основе её названия, описания и контекста
                - Давай практичные и конкретные советы
                - Учитывай сложность и срочность задачи
                - Если описание отсутствует, делай выводы из названия
                - НЕ используй эмодзи в ответе
                - Отвечай на русском языке
                - Используй Markdown для форматирования
                """;
        }
        
        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        
        // В офлайн-режиме возвращаем fallback
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return CompletableFuture.completedFuture(generateFallbackInsight(task));
        }
        
        AiRequestOptions options = AiRequestOptions.builder()
            .model(aiClient.getDefaultModel())
            .systemPrompt(systemPrompt)
            .build();
        
        return aiClient.sendChatMessage(prompt, options)
            .thenApply(response -> {
                if (response.success() && response.content() != null) {
                    return decodeUnicodeEscapes(response.content());
                }
                return generateFallbackInsight(task);
            })
            .exceptionally(e -> generateFallbackInsight(task));
    }

    public String autoSchedule(java.util.List<Task> tasks, int maxDailyComplexity) {
        java.util.List<Task> pendingTasks = tasks.stream()
            .filter(t -> !t.isArchived() && (!t.hasStartDate() || t.getStartDate().isAfter(LocalDate.now())))
            .sorted(java.util.Comparator.comparing(Task::getDeadline)
                .thenComparing(java.util.Comparator.comparingDouble(Task::getSmartPriority).reversed()))
            .collect(java.util.stream.Collectors.toList());

        if (pendingTasks.isEmpty()) return "Нет задач для планирования.";

        LocalDate currentDate = LocalDate.now().plusDays(1);
        int currentLoad = 0;
        int scheduledCount = 0;
        int daysUsed = 1;

        for (Task task : pendingTasks) {
            // Если задача слишком сложная для остатка дня, переносим на следующий
            if (currentLoad + task.getComplexity() > maxDailyComplexity && currentLoad > 0) {
                currentDate = currentDate.plusDays(1);
                currentLoad = 0;
                daysUsed++;
            }
            
            task.setStartDate(currentDate);
            currentLoad += Math.max(1, task.getComplexity()); // Минимум 1 ед. нагрузки
            scheduledCount++;
        }

        return String.format("Авто-планирование завершено!\n\nРаспределено задач: %d\nЗадействовано дней: %d\nНагрузка в день: ~%d ед.", 
            scheduledCount, daysUsed, maxDailyComplexity);
    }

    private double calculateUrgency(LocalDate deadline) {
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        if (daysUntil <= 0) return 10.0;
        if (daysUntil >= 30) return 1.0;
        return 10.0 - (daysUntil * 0.3);
    }

    private String generateFallbackInsight(Task task) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
        StringBuilder sb = new StringBuilder("📊 Анализ задачи:\n\n");
        
        if (days <= 0) sb.append("🔴 ПРОСРОЧЕНО!\n");
        else if (days <= 3) sb.append("🟠 Срочно: ").append(days).append(" дн.\n");
        else if (days <= 7) sb.append("🟡 Скоро дедлайн: ").append(days).append(" дн.\n");
        else sb.append("🟢 Есть время: ").append(days).append(" дн.\n");
        
        sb.append("\n💡 Сложность: ").append(task.getComplexity()).append("/10");
        if (task.getComplexity() >= 7) sb.append("\nРазбейте на подзадачи.");
        
        return sb.toString();
    }
    
    private String decodeUnicodeEscapes(String text) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && text.charAt(i + 1) == 'u') {
                try {
                    String hex = text.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    
                    // Проверяем на суррогатную пару (для эмодзи)
                    if (Character.isHighSurrogate((char) codePoint) && 
                        i + 11 < text.length() && 
                        text.charAt(i + 6) == '\\' && 
                        text.charAt(i + 7) == 'u') {
                        String hex2 = text.substring(i + 8, i + 12);
                        int lowSurrogate = Integer.parseInt(hex2, 16);
                        if (Character.isLowSurrogate((char) lowSurrogate)) {
                            int fullCodePoint = Character.toCodePoint((char) codePoint, (char) lowSurrogate);
                            result.appendCodePoint(fullCodePoint);
                            i += 12;
                            continue;
                        }
                    }
                    
                    result.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException e) {
                    result.append(text.charAt(i));
                    i++;
                }
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
}
