package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeEstimateAccuracyCalculatorTest {

    @Test
    void calculatesAccuracyHitRateAndBiasesForComparableTasks() {
        LocalDate day = LocalDate.of(2026, 3, 11);

        Task underestimated = new Task("under", "Недооцененная задача", "", day, 6);
        underestimated.setStartDate(day);
        underestimated.setStartTime(LocalTime.of(9, 0));
        underestimated.setDeadlineTime(LocalTime.of(10, 0));

        Task accurate = new Task("hit", "Попадание в оценку", "", day, 4);
        accurate.setStartDate(day);
        accurate.setStartTime(LocalTime.of(11, 0));
        accurate.setDeadlineTime(LocalTime.of(12, 0));

        Task overestimated = new Task("over", "Переоцененная задача", "", day, 3);
        overestimated.setStartDate(day);
        overestimated.setStartTime(LocalTime.of(13, 0));
        overestimated.setDeadlineTime(LocalTime.of(14, 30));

        Task noActual = new Task("no-actual", "Без факта", "", day, 2);
        noActual.setStartDate(day);
        noActual.setStartTime(LocalTime.of(15, 0));
        noActual.setDeadlineTime(LocalTime.of(15, 45));

        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "under", LocalDateTime.of(day, LocalTime.of(9, 0)), 95),
                new TimeSession("s2", "hit", LocalDateTime.of(day, LocalTime.of(11, 0)), 65),
                new TimeSession("s3", "over", LocalDateTime.of(day, LocalTime.of(13, 0)), 45)
        );

        TimeEstimateAccuracyMetric metric = new TimeEstimateAccuracyCalculator().calculate(
                List.of(underestimated, accurate, overestimated, noActual),
                sessions,
                day.minusDays(1),
                day.plusDays(1)
        );

        assertTrue(metric.available());
        assertFalse(metric.approximate());
        assertEquals(4, metric.estimatedTaskCount());
        assertEquals(3, metric.comparableTaskCount());
        assertEquals(1.0 / 3.0, metric.hitRate(), 0.0001);
        assertEquals(2.0 / 3.0, metric.underestimationBias(), 0.0001);
        assertEquals(1.0 / 3.0, metric.overestimationBias(), 0.0001);
        assertTrue(metric.averageErrorRatio() > 0.20);
    }

    @Test
    void returnsApproximateMetricWhenComparableActualsAreMissing() {
        LocalDate day = LocalDate.of(2026, 3, 11);
        Task estimated = new Task("estimated", "Только оценка", "", day, 5);
        estimated.setStartDate(day);
        estimated.setStartTime(LocalTime.of(9, 0));
        estimated.setDeadlineTime(LocalTime.of(10, 0));

        TimeEstimateAccuracyMetric metric = new TimeEstimateAccuracyCalculator().calculate(
                List.of(estimated),
                List.of(),
                day.minusDays(1),
                day.plusDays(1)
        );

        assertFalse(metric.available());
        assertTrue(metric.approximate());
        assertEquals(1, metric.estimatedTaskCount());
        assertEquals(0, metric.comparableTaskCount());
    }
}
