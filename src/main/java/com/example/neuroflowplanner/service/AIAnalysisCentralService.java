package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Central service for AI Analysis logic.
 * Consolidates functionality previously scattered across MainView and other controllers.
 */
public class AIAnalysisCentralService {

    private final AISchedulingEngine schedulingEngine;
    private final TimePredictionService timePredictionService;

    public AIAnalysisCentralService() {
        this.schedulingEngine = new AISchedulingEngine();
        this.timePredictionService = new TimePredictionService();
    }

    /**
     * Recalculates smart priorities for all provided tasks and their subtasks.
     * Logic moved from MainView.handleAnalyzeAll().
     */
    public void recalculateAllPriorities(List<Task> tasks) {
        tasks.forEach(task -> {
            schedulingEngine.calculatePriority(task);
            task.getSubtasks().forEach(schedulingEngine::calculatePriority);
        });
    }

    /**
     * Analyzes a single task using AI to generate insights.
     */
    public CompletableFuture<String> analyzeTaskInsight(Task task) {
        return schedulingEngine.analyzeTaskWithAI(task);
    }

    /**
     * Predicts time for a task using the TimePredictionService.
     */
    public CompletableFuture<String> predictTaskTime(Task task) {
        return timePredictionService.predictTime(task);
    }

    /**
     * Analyzes task wording for SMART criteria.
     */
    public CompletableFuture<String> analyzeSmartCriteria(Task task) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder report = new StringBuilder();
            int score = 0;
            int maxScore = 5;
            
            String title = task.getTitle() != null ? task.getTitle() : "";
            String desc = task.getDescription() != null ? task.getDescription() : "";
            String combined = title + " " + desc;
            
            // S - Specific (Конкретная)
            boolean isSpecific = title.length() >= 10 && !title.matches("(?i).*(задача|дело|работа|сделать)\\s*\\d*$");
            boolean hasActionVerb = combined.matches("(?i).*(создать|разработать|написать|исправить|добавить|удалить|обновить|настроить|протестировать|проверить|реализовать|внедрить|оптимизировать|документировать).*");
            
            if (isSpecific && hasActionVerb) {
                report.append("✅ Specific (Конкретная)\n");
                report.append("   Чёткая формулировка с глаголом действия.\n\n");
                score++;
            } else if (isSpecific || hasActionVerb) {
                report.append("⚠️ Specific (Конкретная)\n");
                if (!hasActionVerb) report.append("   Добавьте глагол действия (создать, исправить...).\n\n");
                else report.append("   Уточните название задачи.\n\n");
            } else {
                report.append("❌ Specific (Конкретная)\n");
                report.append("   Название слишком общее. Укажите конкретное действие.\n\n");
            }
            
            // M - Measurable (Измеримая)
            boolean hasMeasure = combined.matches(".*\\d+.*") || 
                                 combined.matches("(?i).*(процент|количество|число|раз|штук|единиц|минут|часов|дней|страниц|строк|тестов|пунктов).*");
            boolean hasResult = combined.matches("(?i).*(результат|итог|цель|достичь|получить|завершить).*");
            
            if (hasMeasure) {
                report.append("✅ Measurable (Измеримая)\n");
                report.append("   Есть количественные показатели.\n\n");
                score++;
            } else if (hasResult) {
                report.append("⚠️ Measurable (Измеримая)\n");
                report.append("   Добавьте числовые метрики для оценки.\n\n");
            } else {
                report.append("❌ Measurable (Измеримая)\n");
                report.append("   Как измерить успех? Добавьте метрики.\n\n");
            }
            
            // A - Achievable (Достижимая)
            int complexity = task.getComplexity();
            boolean hasSubtasks = task.hasSubtasks();
            boolean isAchievable = complexity <= 7 || (complexity > 7 && hasSubtasks);
            
            if (isAchievable) {
                report.append("✅ Achievable (Достижимая)\n");
                if (hasSubtasks) report.append("   Разбита на подзадачи — отлично!\n\n");
                else report.append("   Сложность в пределах нормы.\n\n");
                score++;
            } else {
                report.append("❌ Achievable (Достижимая)\n");
                report.append("   Сложность " + complexity + "/10 — разбейте на подзадачи.\n\n");
            }
            
            // R - Relevant (Актуальная)
            boolean hasTags = task.getTags() != null && !task.getTags().isEmpty();
            boolean hasContext = desc.length() > 20;
            boolean isRelevant = hasTags || hasContext;
            
            if (isRelevant) {
                report.append("✅ Relevant (Актуальная)\n");
                if (hasTags) report.append("   Теги: " + task.getTags() + "\n\n");
                else report.append("   Контекст задачи понятен из описания.\n\n");
                score++;
            } else {
                report.append("⚠️ Relevant (Актуальная)\n");
                report.append("   Добавьте теги или расширьте описание.\n\n");
            }
            
            // T - Time-bound (Ограничена по времени)
            boolean hasDeadline = task.getDeadline() != null;
            
            if (hasDeadline) {
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), task.getDeadline());
                report.append("✅ Time-bound (Ограничена по времени)\n");
                if (daysUntil < 0) {
                    report.append("   ⚠️ Дедлайн просрочен на " + Math.abs(daysUntil) + " дн.!\n\n");
                } else if (daysUntil == 0) {
                    report.append("   Дедлайн сегодня!\n\n");
                } else {
                    report.append("   Осталось " + daysUntil + " дн. до дедлайна.\n\n");
                }
                score++;
            } else {
                report.append("❌ Time-bound (Ограничена по времени)\n");
                report.append("   Установите дедлайн для контроля.\n\n");
            }
            
            // Summary
            report.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            report.append("📊 Оценка SMART: " + score + "/" + maxScore + " ");
            
            if (score == maxScore) {
                report.append("⭐ Отлично!\n");
                report.append("Задача полностью соответствует SMART.");
            } else if (score >= 4) {
                report.append("👍 Хорошо\n");
                report.append("Небольшие улучшения сделают задачу идеальной.");
            } else if (score >= 3) {
                report.append("📝 Нормально\n");
                report.append("Рекомендуется доработать формулировку.");
            } else {
                report.append("⚠️ Требует доработки\n");
                report.append("Уточните задачу по указанным критериям.");
            }
            
            return report.toString();
        });
    }

    public List<Recommendation> getRecommendations(List<Task> tasks) {
        List<Recommendation> recs = new ArrayList<>();
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // 1. Complexity Check
        tasks.stream()
             .filter(t -> t.getComplexity() >= 8 && !t.hasSubtasks() && !t.isArchived())
             .forEach(t -> recs.add(new Recommendation(
                 "Высокая сложность", 
                 "Задача '" + t.getTitle() + "' слишком сложная (" + t.getComplexity() + "/10).", 
                 "Разбить на подзадачи", 
                 RecommendationType.SPLIT, 
                 t
             )));

        // 2. Overload Check (Tomorrow)
        long tasksTomorrow = tasks.stream()
            .filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().equals(tomorrow))
            .count();
        if (tasksTomorrow > 4) {
             recs.add(new Recommendation(
                 "Риск перегрузки", 
                 "На завтра запланировано " + tasksTomorrow + " задач. Это может привести к снижению качества.", 
                 "Перенести часть задач", 
                 RecommendationType.RESCHEDULE, 
                 null
             ));
        }
        
        // 3. Stagnation Check (Old high priority tasks)
        LocalDate weekAgo = LocalDate.now().minusDays(7);
        tasks.stream()
            .filter(t -> !t.isArchived() && t.getSmartPriority() >= 7 && t.getDeadline() != null && t.getDeadline().isBefore(weekAgo))
            .forEach(t -> recs.add(new Recommendation(
                "Застойная задача",
                "Важная задача '" + t.getTitle() + "' висит уже неделю.",
                "Повысить приоритет",
                RecommendationType.PRIORITIZE,
                t
            )));

        return recs;
    }

    public record Recommendation(String title, String description, String actionLabel, RecommendationType type, Task relatedTask) {}
    public enum RecommendationType { SPLIT, RESCHEDULE, PRIORITIZE }
}
