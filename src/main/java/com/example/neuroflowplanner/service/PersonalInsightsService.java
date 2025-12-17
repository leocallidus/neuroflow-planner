package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class PersonalInsightsService {

    public record Insight(String icon, String title, String description, InsightType type) {}
    public enum InsightType { POSITIVE, WARNING, INFO, TIP }

    public record Stats(
        int totalTasks, int archivedTasks, long totalMinutes,
        double avgComplexity, int overdueCount, int urgentCount,
        DayOfWeek mostProductiveDay, String topTag, double completionRate
    ) {}

    public Stats calculateStats(List<Task> tasks) {
        int total = tasks.size();
        int archived = (int) tasks.stream().filter(Task::isArchived).count();
        long minutes = tasks.stream().mapToLong(Task::getTrackedMinutes).sum();
        double avgComp = tasks.stream().mapToInt(Task::getComplexity).average().orElse(0);
        
        LocalDate today = LocalDate.now();
        int overdue = (int) tasks.stream()
            .filter(t -> !t.isArchived() && t.getDeadline() != null && t.getDeadline().isBefore(today))
            .count();
        int urgent = (int) tasks.stream()
            .filter(t -> !t.isArchived() && t.getDeadline() != null && 
                    !t.getDeadline().isBefore(today) && ChronoUnit.DAYS.between(today, t.getDeadline()) <= 3)
            .count();

        // Most productive day (by archived tasks deadline)
        Map<DayOfWeek, Long> dayCount = tasks.stream()
            .filter(t -> t.isArchived() && t.getDeadline() != null)
            .collect(Collectors.groupingBy(t -> t.getDeadline().getDayOfWeek(), Collectors.counting()));
        DayOfWeek bestDay = dayCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(DayOfWeek.MONDAY);

        // Top tag
        Map<String, Long> tagCount = tasks.stream()
            .flatMap(t -> Arrays.stream(t.getTags().split(",")).map(String::trim).filter(s -> !s.isEmpty()))
            .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
        String topTag = tagCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse("—");

        double rate = total > 0 ? (archived * 100.0 / total) : 0;
        return new Stats(total, archived, minutes, avgComp, overdue, urgent, bestDay, topTag, rate);
    }

    public List<Insight> generateInsights(List<Task> tasks) {
        List<Insight> insights = new ArrayList<>();
        Stats stats = calculateStats(tasks);

        // Completion rate
        if (stats.completionRate() >= 70) {
            insights.add(new Insight("🏆", "Отличный прогресс!", 
                String.format("Вы выполнили %.0f%% задач — продолжайте в том же духе!", stats.completionRate()), InsightType.POSITIVE));
        } else if (stats.completionRate() >= 40) {
            insights.add(new Insight("📊", "Стабильный темп", 
                String.format("Выполнено %.0f%% задач. Есть потенциал для роста!", stats.completionRate()), InsightType.INFO));
        } else if (stats.totalTasks() > 0) {
            insights.add(new Insight("⚡", "Время ускориться", 
                String.format("Только %.0f%% задач завершено. Попробуйте разбить крупные задачи на мелкие.", stats.completionRate()), InsightType.WARNING));
        }

        // Overdue
        if (stats.overdueCount() > 0) {
            insights.add(new Insight("🔴", "Просроченные задачи", 
                String.format("%d задач просрочено. Пересмотрите приоритеты или перенесите дедлайны.", stats.overdueCount()), InsightType.WARNING));
        }

        // Urgent
        if (stats.urgentCount() > 3) {
            insights.add(new Insight("🔥", "Много срочных задач", 
                String.format("%d задач с дедлайном в ближайшие 3 дня. Сфокусируйтесь на них!", stats.urgentCount()), InsightType.WARNING));
        } else if (stats.urgentCount() == 0 && stats.totalTasks() > 0) {
            insights.add(new Insight("✨", "Нет срочных задач", 
                "Отличное время для работы над долгосрочными проектами.", InsightType.POSITIVE));
        }

        // Time tracking
        if (stats.totalMinutes() > 600) {
            insights.add(new Insight("⏱", "Активный трекинг", 
                String.format("Отслежено %d ч %d мин. Это помогает понять реальные затраты времени.", 
                    stats.totalMinutes() / 60, stats.totalMinutes() % 60), InsightType.POSITIVE));
        } else if (stats.totalMinutes() == 0 && stats.totalTasks() > 5) {
            insights.add(new Insight("💡", "Включите трекинг", 
                "Отслеживание времени поможет точнее планировать задачи.", InsightType.TIP));
        }

        // Complexity
        if (stats.avgComplexity() > 7) {
            insights.add(new Insight("🎯", "Высокая сложность", 
                String.format("Средняя сложность %.1f/10. Разбивайте сложные задачи на подзадачи.", stats.avgComplexity()), InsightType.TIP));
        }

        // Best day
        String dayName = switch (stats.mostProductiveDay()) {
            case MONDAY -> "Понедельник";
            case TUESDAY -> "Вторник";
            case WEDNESDAY -> "Среда";
            case THURSDAY -> "Четверг";
            case FRIDAY -> "Пятница";
            case SATURDAY -> "Суббота";
            case SUNDAY -> "Воскресенье";
        };
        if (stats.archivedTasks() > 3) {
            insights.add(new Insight("📅", "Продуктивный день", 
                String.format("%s — ваш самый продуктивный день. Планируйте важные задачи на него.", dayName), InsightType.INFO));
        }

        // Top tag
        if (!stats.topTag().equals("—")) {
            insights.add(new Insight("🏷", "Популярная категория", 
                String.format("Больше всего задач с тегом «%s». Возможно, стоит выделить время блоками.", stats.topTag()), InsightType.INFO));
        }

        // Task count
        if (stats.totalTasks() > 20) {
            insights.add(new Insight("📋", "Много задач", 
                String.format("У вас %d задач. Используйте фильтры и приоритизацию для фокуса.", stats.totalTasks()), InsightType.TIP));
        }

        return insights;
    }
}
