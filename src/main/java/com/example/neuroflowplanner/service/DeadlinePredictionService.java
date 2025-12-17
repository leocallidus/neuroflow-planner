package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class DeadlinePredictionService {

    public List<TaskRiskAnalysis> analyzeRisks(List<Task> tasks) {
        List<TaskRiskAnalysis> results = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Task task : tasks) {
            if (task.isArchived()) continue;
            
            long daysRemaining = ChronoUnit.DAYS.between(today, task.getDeadline());
            int complexity = task.getComplexity();
            
            RiskLevel level;
            String reason;

            if (daysRemaining < 0) {
                level = RiskLevel.OVERDUE;
                reason = "Дедлайн прошел (" + Math.abs(daysRemaining) + " дн. назад)";
            } else if (daysRemaining == 0) {
                level = RiskLevel.CRITICAL;
                reason = "Дедлайн сегодня!";
            } else {
                // Heuristic: Complexity 10 needs ~5 days buffer to be safe?
                // Complexity 1 needs ~0.5 days.
                // Ratio: Days / Complexity
                double ratio = (double) daysRemaining / Math.max(1, complexity);
                
                if (ratio < 0.3) { // e.g. 2 days for complexity 8 (0.25)
                    level = RiskLevel.HIGH;
                    reason = "Мало времени для такой сложности";
                } else if (ratio < 0.7) { // e.g. 5 days for complexity 8 (0.625)
                    level = RiskLevel.MEDIUM;
                    reason = "Плотный график, риск задержки";
                } else {
                    level = RiskLevel.LOW;
                    reason = "Времени достаточно";
                }
            }
            
            results.add(new TaskRiskAnalysis(task, level, reason, daysRemaining));
        }
        
        // Sort by risk (Critical first)
        results.sort((a, b) -> Integer.compare(b.level.severity, a.level.severity));
        
        return results;
    }

    public record TaskRiskAnalysis(Task task, RiskLevel level, String reason, long daysRemaining) {}

    public enum RiskLevel {
        LOW(1, "Низкий", "risk-low"),
        MEDIUM(2, "Средний", "risk-medium"),
        HIGH(3, "Высокий", "risk-high"),
        CRITICAL(4, "Критический", "risk-critical"),
        OVERDUE(5, "Просрочено", "risk-overdue");

        public final int severity;
        public final String label;
        public final String styleClass;

        RiskLevel(int severity, String label, String styleClass) {
            this.severity = severity;
            this.label = label;
            this.styleClass = styleClass;
        }
    }
}
