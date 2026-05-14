package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RescheduleRateCalculator {

    private static final Duration START_DRIFT_TOLERANCE = Duration.ofHours(4);
    private static final Duration DEADLINE_DRIFT_TOLERANCE = Duration.ofHours(2);
    private static final Duration LATE_RESCHEDULE_THRESHOLD = Duration.ofHours(8);

    public RescheduleRateMetric calculate(
            List<Task> allTasks,
            List<TimeSession> sessionsInPeriod,
            LocalDate periodStart,
            LocalDate periodEnd) {
        List<Task> safeTasks = allTasks == null ? List.of() : allTasks;
        List<TimeSession> safeSessions = sessionsInPeriod == null ? List.of() : sessionsInPeriod;

        Map<String, TaskTimingSignal> timingSignals = buildTimingSignals(safeSessions);

        int analyzedTaskCount = 0;
        int rescheduledTaskCount = 0;
        int multipleRescheduleCount = 0;
        int lateRescheduleCount = 0;

        for (Task task : safeTasks) {
            if (!hasScheduleAnchor(task) || !touchesPeriod(task, periodStart, periodEnd)) {
                continue;
            }
            analyzedTaskCount++;

            TaskTimingSignal signal = timingSignals.getOrDefault(task.getId(), TaskTimingSignal.EMPTY);
            HeuristicRescheduleAssessment assessment = assessTask(task, signal);
            if (assessment.rescheduled()) {
                rescheduledTaskCount++;
            }
            if (assessment.multipleReschedules()) {
                multipleRescheduleCount++;
            }
            if (assessment.lateReschedule()) {
                lateRescheduleCount++;
            }
        }

        if (analyzedTaskCount == 0) {
            return RescheduleRateMetric.unavailable();
        }

        return new RescheduleRateMetric(
                analyzedTaskCount,
                rescheduledTaskCount,
                Math.max(0, analyzedTaskCount - rescheduledTaskCount),
                multipleRescheduleCount,
                lateRescheduleCount,
                rescheduledTaskCount / (double) analyzedTaskCount,
                true
        );
    }

    private Map<String, TaskTimingSignal> buildTimingSignals(List<TimeSession> sessionsInPeriod) {
        Map<String, TaskTimingSignal> byTaskId = new HashMap<>();
        for (TimeSession session : sessionsInPeriod) {
            if (session == null || session.getTaskId() == null || session.getTaskId().isBlank() || session.getStartedAt() == null) {
                continue;
            }
            LocalDateTime start = session.getStartedAt();
            long safeMinutes = Math.max(0L, session.getMinutes());
            LocalDateTime end = start.plusMinutes(safeMinutes);
            byTaskId.merge(
                    session.getTaskId(),
                    new TaskTimingSignal(start, end, safeMinutes, 1),
                    TaskTimingSignal::merge
            );
        }
        return byTaskId;
    }

    private HeuristicRescheduleAssessment assessTask(Task task, TaskTimingSignal signal) {
        LocalDateTime plannedStart = task.getStartDateTime();
        LocalDateTime plannedEnd = task.getDeadlineDateTime();
        LocalDateTime completedAt = resolveCompletionDateTime(task);
        long trackedMinutes = Math.max(signal.totalTrackedMinutes(), Math.max(0L, task.getTrackedMinutes()));

        boolean startMoved = plannedStart != null
                && signal.firstWorkAt() != null
                && signal.firstWorkAt().isAfter(plannedStart.plus(START_DRIFT_TOLERANCE));

        boolean deadlineMoved = plannedEnd != null && (
                (signal.lastWorkAt() != null && signal.lastWorkAt().isAfter(plannedEnd.plus(DEADLINE_DRIFT_TOLERANCE)))
                        || (completedAt != null && completedAt.isAfter(plannedEnd))
        );

        long plannedMinutes = plannedStart != null && plannedEnd != null && plannedEnd.isAfter(plannedStart)
                ? Duration.between(plannedStart, plannedEnd).toMinutes()
                : 0L;

        boolean heavyDrift = plannedMinutes > 0 && trackedMinutes > Math.round(plannedMinutes * 1.75);
        boolean multiSessionSprawl = signal.sessionCount() >= 3
                && plannedEnd != null
                && signal.lastWorkAt() != null
                && signal.lastWorkAt().isAfter(plannedEnd.plusDays(1));

        boolean multipleReschedules = (startMoved && deadlineMoved) || (deadlineMoved && heavyDrift) || multiSessionSprawl;

        boolean lateReschedule = deadlineMoved && plannedEnd != null && (
                (signal.firstWorkAt() != null && !signal.firstWorkAt().isBefore(plannedEnd.minus(LATE_RESCHEDULE_THRESHOLD)))
                        || (completedAt != null && !completedAt.isBefore(plannedEnd))
                        || (signal.lastWorkAt() != null && !signal.lastWorkAt().isBefore(plannedEnd))
        );

        boolean rescheduled = startMoved || deadlineMoved || multipleReschedules;
        return new HeuristicRescheduleAssessment(rescheduled, multipleReschedules, lateReschedule);
    }

    private LocalDateTime resolveCompletionDateTime(Task task) {
        if (task == null || task.getCompletedDate() == null) {
            return null;
        }
        LocalTime fallbackTime = task.getDeadlineTime() != null ? task.getDeadlineTime() : LocalTime.MAX;
        return LocalDateTime.of(task.getCompletedDate(), fallbackTime);
    }

    private boolean hasScheduleAnchor(Task task) {
        return task != null && (task.getStartDate() != null || task.getDeadline() != null);
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

    private record HeuristicRescheduleAssessment(
            boolean rescheduled,
            boolean multipleReschedules,
            boolean lateReschedule) {
    }

    private record TaskTimingSignal(
            LocalDateTime firstWorkAt,
            LocalDateTime lastWorkAt,
            long totalTrackedMinutes,
            int sessionCount) {

        private static final TaskTimingSignal EMPTY = new TaskTimingSignal(null, null, 0L, 0);

        private TaskTimingSignal merge(TaskTimingSignal other) {
            if (other == null) {
                return this;
            }
            LocalDateTime mergedFirst = firstWorkAt == null ? other.firstWorkAt : other.firstWorkAt == null
                    ? firstWorkAt
                    : firstWorkAt.isBefore(other.firstWorkAt) ? firstWorkAt : other.firstWorkAt;
            LocalDateTime mergedLast = lastWorkAt == null ? other.lastWorkAt : other.lastWorkAt == null
                    ? lastWorkAt
                    : lastWorkAt.isAfter(other.lastWorkAt) ? lastWorkAt : other.lastWorkAt;
            return new TaskTimingSignal(
                    mergedFirst,
                    mergedLast,
                    Math.max(0L, totalTrackedMinutes) + Math.max(0L, other.totalTrackedMinutes),
                    Math.max(0, sessionCount) + Math.max(0, other.sessionCount)
            );
        }
    }
}
