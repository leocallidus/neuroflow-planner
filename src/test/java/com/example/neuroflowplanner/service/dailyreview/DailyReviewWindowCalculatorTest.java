package com.example.neuroflowplanner.service.dailyreview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyReviewWindowCalculatorTest {

    @Test
    void calculatesFreeWindowsInsideWorkIntervals() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        DailyReviewWindowCalculator calculator = new DailyReviewWindowCalculator();

        List<DailyReviewWorkInterval> workIntervals = List.of(
                new DailyReviewWorkInterval(
                        LocalDateTime.of(date, LocalTime.of(9, 0)),
                        LocalDateTime.of(date, LocalTime.of(18, 0)),
                        540,
                        true,
                        "09:00-18:00"
                )
        );
        List<DailyReviewTimeBlock> busyBlocks = List.of(
                new DailyReviewTimeBlock(
                        "a",
                        "Standup",
                        LocalDateTime.of(date, LocalTime.of(10, 0)),
                        LocalDateTime.of(date, LocalTime.of(10, 30)),
                        30,
                        "task_schedule",
                        false
                ),
                new DailyReviewTimeBlock(
                        "b",
                        "Deep work",
                        LocalDateTime.of(date, LocalTime.of(13, 0)),
                        LocalDateTime.of(date, LocalTime.of(14, 30)),
                        90,
                        "time_session",
                        false
                )
        );

        DailyReviewWindowCalculationResult result = calculator.calculate(workIntervals, busyBlocks);

        assertFalse(result.approximate());
        assertEquals(3, result.freeWindows().size());
        assertEquals("09:00-10:00", result.freeWindows().get(0).label());
        assertEquals("10:30-13:00", result.freeWindows().get(1).label());
        assertEquals(DailyReviewWindowSuitability.DEEP_WORK, result.freeWindows().get(1).suitability());
        assertEquals("14:30-18:00", result.freeWindows().get(2).label());
    }

    @Test
    void fallsBackToApproximateWhenWorkHoursUnknown() {
        DailyReviewWindowCalculator calculator = new DailyReviewWindowCalculator();
        DailyReviewWindowCalculationResult result = calculator.calculate(List.of(), List.of());

        assertTrue(result.approximate());
        assertTrue(result.freeWindows().isEmpty());
    }

    @Test
    void mergesOverlappingBusyBlocksAndSkipsTinyWindows() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        DailyReviewWindowCalculator calculator = new DailyReviewWindowCalculator();

        List<DailyReviewWorkInterval> workIntervals = List.of(
                new DailyReviewWorkInterval(
                        LocalDateTime.of(date, LocalTime.of(9, 0)),
                        LocalDateTime.of(date, LocalTime.of(12, 0)),
                        180,
                        true,
                        "09:00-12:00"
                )
        );
        List<DailyReviewTimeBlock> busyBlocks = List.of(
                new DailyReviewTimeBlock(
                        "a",
                        "Block A",
                        LocalDateTime.of(date, LocalTime.of(9, 30)),
                        LocalDateTime.of(date, LocalTime.of(10, 30)),
                        60,
                        "task_schedule",
                        false
                ),
                new DailyReviewTimeBlock(
                        "b",
                        "Block B",
                        LocalDateTime.of(date, LocalTime.of(10, 20)),
                        LocalDateTime.of(date, LocalTime.of(11, 45)),
                        85,
                        "task_schedule",
                        true
                )
        );

        DailyReviewWindowCalculationResult result = calculator.calculate(workIntervals, busyBlocks);

        assertTrue(result.approximate());
        assertEquals(1, result.freeWindows().size());
        assertEquals("09:00-09:30", result.freeWindows().getFirst().label());
    }
}
