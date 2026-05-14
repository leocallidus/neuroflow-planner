package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TimeEstimateAccuracyCalculator {

    private static final double HIT_TOLERANCE_RATIO = 0.25;

    public TimeEstimateAccuracyMetric calculate(
            List<Task> allTasks,
            List<TimeSession> sessionsInPeriod,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<Task> safeTasks = allTasks == null ? List.of() : allTasks;
        List<TimeSession> safeSessions = sessionsInPeriod == null ? List.of() : sessionsInPeriod;

        Map<String, Long> trackedMinutesByTaskId = new HashMap<>();
        for (TimeSession session : safeSessions) {
            if (session == null || session.getTaskId() == null || session.getTaskId().isBlank()) {
                continue;
            }
            trackedMinutesByTaskId.merge(session.getTaskId(), Math.max(0L, session.getMinutes()), Long::sum);
        }

        int estimatedTaskCount = 0;
        int comparableTaskCount = 0;
        double totalAbsoluteError = 0.0;
        int hitCount = 0;
        int underestimatedCount = 0;
        int overestimatedCount = 0;

        for (Task task : safeTasks) {
            if (!hasComparablePlannedWindow(task) || !touchesPeriod(task, periodStart, periodEnd)) {
                continue;
            }
            estimatedTaskCount++;

            long plannedMinutes = plannedMinutes(task);
            if (plannedMinutes <= 0) {
                continue;
            }

            long actualMinutes = Math.max(
                    trackedMinutesByTaskId.getOrDefault(task.getId(), 0L),
                    Math.max(0L, task.getTrackedMinutes())
            );
            if (actualMinutes <= 0) {
                continue;
            }

            comparableTaskCount++;
            double errorRatio = Math.abs(actualMinutes - plannedMinutes) / (double) plannedMinutes;
            totalAbsoluteError += errorRatio;
            if (errorRatio <= HIT_TOLERANCE_RATIO) {
                hitCount++;
            }
            if (actualMinutes > plannedMinutes) {
                underestimatedCount++;
            } else if (actualMinutes < plannedMinutes) {
                overestimatedCount++;
            }
        }

        if (comparableTaskCount == 0) {
            return new TimeEstimateAccuracyMetric(
                    estimatedTaskCount,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    true
            );
        }

        return new TimeEstimateAccuracyMetric(
                estimatedTaskCount,
                comparableTaskCount,
                totalAbsoluteError / comparableTaskCount,
                hitCount / (double) comparableTaskCount,
                underestimatedCount / (double) comparableTaskCount,
                overestimatedCount / (double) comparableTaskCount,
                false
        );
    }

    private boolean hasComparablePlannedWindow(Task task) {
        LocalDateTime start = task == null ? null : task.getStartDateTime();
        LocalDateTime end = task == null ? null : task.getDeadlineDateTime();
        return start != null && end != null && end.isAfter(start);
    }

    private long plannedMinutes(Task task) {
        return Duration.between(task.getStartDateTime(), task.getDeadlineDateTime()).toMinutes();
    }

    private boolean touchesPeriod(Task task, LocalDate periodStart, LocalDate periodEnd) {
        if (task == null) {
            return false;
        }
        return isWithinPeriod(task.getStartDate(), periodStart, periodEnd)
                || isWithinPeriod(task.getDeadline(), periodStart, periodEnd)
                || isWithinPeriod(task.getCompletedDate(), periodStart, periodEnd);
    }

    private boolean isWithinPeriod(LocalDate date, LocalDate periodStart, LocalDate periodEnd) {
        return date != null && !date.isBefore(periodStart) && !date.isAfter(periodEnd);
    }
}
