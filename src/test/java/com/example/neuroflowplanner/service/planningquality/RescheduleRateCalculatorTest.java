package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RescheduleRateCalculatorTest {

    private final RescheduleRateCalculator calculator = new RescheduleRateCalculator();

    @Test
    void marksTasksWithStrongScheduleDriftAsRescheduled() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 7);

        Task stableTask = new Task("stable", "Стабильная задача", "", LocalDate.of(2026, 3, 3), 3);
        stableTask.setStartDate(LocalDate.of(2026, 3, 3));
        stableTask.setStartTime(LocalTime.of(9, 0));
        stableTask.setDeadlineTime(LocalTime.of(11, 0));
        stableTask.setCompleted(true);
        stableTask.setCompletedDate(LocalDate.of(2026, 3, 3));

        Task driftedTask = new Task("drifted", "Сдвинутая задача", "", LocalDate.of(2026, 3, 4), 5);
        driftedTask.setStartDate(LocalDate.of(2026, 3, 4));
        driftedTask.setStartTime(LocalTime.of(9, 0));
        driftedTask.setDeadlineTime(LocalTime.of(11, 0));
        driftedTask.setCompleted(true);
        driftedTask.setCompletedDate(LocalDate.of(2026, 3, 6));

        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "stable", LocalDateTime.of(2026, 3, 3, 9, 10), 95),
                new TimeSession("s2", "drifted", LocalDateTime.of(2026, 3, 5, 15, 0), 90),
                new TimeSession("s3", "drifted", LocalDateTime.of(2026, 3, 6, 10, 0), 80),
                new TimeSession("s4", "drifted", LocalDateTime.of(2026, 3, 6, 14, 0), 70)
        );

        RescheduleRateMetric metric = calculator.calculate(List.of(stableTask, driftedTask), sessions, start, end);

        assertTrue(metric.available());
        assertTrue(metric.approximate());
        assertEquals(2, metric.analyzedTaskCount());
        assertEquals(1, metric.rescheduledTaskCount());
        assertEquals(1, metric.untouchedTaskCount());
        assertEquals(1, metric.multipleRescheduleCount());
        assertEquals(1, metric.lateRescheduleCount());
        assertEquals(0.5, metric.rescheduleRate(), 0.0001);
    }

    @Test
    void returnsUnavailableWhenPeriodHasNoScheduledTasks() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 7);
        Task unscheduled = new Task("plain", "Без расписания", "", null, 2);

        RescheduleRateMetric metric = calculator.calculate(List.of(unscheduled), List.of(), start, end);

        assertTrue(metric.approximate());
        assertEquals(0, metric.analyzedTaskCount());
        assertEquals(0, metric.rescheduledTaskCount());
    }
}
