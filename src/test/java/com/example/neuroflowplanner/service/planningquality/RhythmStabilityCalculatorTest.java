package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.TimeSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmStabilityCalculatorTest {

    private final RhythmStabilityCalculator calculator = new RhythmStabilityCalculator();

    @Test
    void detectsStableRhythmWhenStartsAndFocusMinutesAreConsistent() {
        List<PlanningQualityDayAggregate> dayAggregates = List.of(
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 2), 3, 2, 2, 145, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 3), 2, 2, 2, 150, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 4), 3, 2, 2, 140, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 5), 2, 1, 2, 155, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 6), 2, 2, 2, 148, false, false, false)
        );
        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "a", LocalDateTime.of(2026, 3, 2, 9, 5), 70),
                new TimeSession("s2", "a", LocalDateTime.of(2026, 3, 2, 10, 40), 75),
                new TimeSession("s3", "b", LocalDateTime.of(2026, 3, 3, 9, 10), 75),
                new TimeSession("s4", "b", LocalDateTime.of(2026, 3, 3, 10, 50), 75),
                new TimeSession("s5", "c", LocalDateTime.of(2026, 3, 4, 9, 0), 70),
                new TimeSession("s6", "c", LocalDateTime.of(2026, 3, 4, 10, 45), 70),
                new TimeSession("s7", "d", LocalDateTime.of(2026, 3, 5, 9, 15), 80),
                new TimeSession("s8", "d", LocalDateTime.of(2026, 3, 5, 11, 0), 75),
                new TimeSession("s9", "e", LocalDateTime.of(2026, 3, 6, 9, 5), 73),
                new TimeSession("s10", "e", LocalDateTime.of(2026, 3, 6, 10, 45), 75)
        );

        RhythmStabilityMetric metric = calculator.calculate(dayAggregates, sessions);

        assertTrue(metric.available());
        assertFalse(metric.approximate());
        assertEquals(RhythmStabilityBand.STABLE, metric.band());
        assertEquals(5, metric.analyzedDayCount());
        assertEquals(5, metric.productiveDayCount());
        assertTrue(metric.score() >= 0.72);
        assertTrue(metric.startTimeVariabilityMinutes() < 15);
        assertTrue(metric.focusMinutesVariability() < 0.1);
    }

    @Test
    void marksRhythmAsChaoticWhenDaysAndStartsVaryStrongly() {
        List<PlanningQualityDayAggregate> dayAggregates = List.of(
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 2), 5, 1, 1, 45, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 3), 0, 0, 0, 0, false, true, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 4), 7, 2, 3, 380, true, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 5), 1, 0, 1, 55, false, false, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 6), 0, 0, 0, 0, false, true, false)
        );
        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "a", LocalDateTime.of(2026, 3, 2, 8, 0), 45),
                new TimeSession("s2", "b", LocalDateTime.of(2026, 3, 4, 13, 30), 180),
                new TimeSession("s3", "b", LocalDateTime.of(2026, 3, 4, 18, 0), 200),
                new TimeSession("s4", "c", LocalDateTime.of(2026, 3, 5, 16, 45), 55)
        );

        RhythmStabilityMetric metric = calculator.calculate(dayAggregates, sessions);

        assertTrue(metric.available());
        assertEquals(RhythmStabilityBand.CHAOTIC, metric.band());
        assertTrue(metric.score() < 0.45);
        assertTrue(metric.startTimeVariabilityMinutes() >= 120);
        assertTrue(metric.focusMinutesVariability() > 0.7);
    }

    @Test
    void returnsUnavailableWhenNoProductiveDaysExist() {
        List<PlanningQualityDayAggregate> dayAggregates = List.of(
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 2), 0, 0, 0, 0, false, true, false),
                new PlanningQualityDayAggregate(LocalDate.of(2026, 3, 3), 0, 0, 0, 0, false, true, false)
        );

        RhythmStabilityMetric metric = calculator.calculate(dayAggregates, List.of());

        assertFalse(metric.available());
        assertTrue(metric.approximate());
        assertEquals(RhythmStabilityBand.UNAVAILABLE, metric.band());
    }
}
