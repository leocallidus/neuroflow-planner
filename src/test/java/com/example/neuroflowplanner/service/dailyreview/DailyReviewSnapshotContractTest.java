package com.example.neuroflowplanner.service.dailyreview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DailyReviewSnapshotContractTest {

    @Test
    void normalizesSnapshotAndProvidesUnavailableFallbacks() {
        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                LocalDate.of(2026, 3, 10),
                null,
                -5,
                -1,
                -2,
                -3,
                -10,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(LocalDate.of(2026, 3, 10), snapshot.reviewDate());
        assertEquals(0, snapshot.activeTaskCount());
        assertEquals(0, snapshot.overdueTaskCount());
        assertEquals(0, snapshot.tasksDueTodayCount());
        assertEquals(0, snapshot.upcomingTaskCount());
        assertEquals(0L, snapshot.trackedMinutesToday());
        assertEquals(DailyReviewSummarySource.UNAVAILABLE, snapshot.summary().source());
        assertFalse(snapshot.summary().available());
        assertTrue(snapshot.overdueItems().isEmpty());
        assertTrue(snapshot.upcomingItems().isEmpty());
        assertTrue(snapshot.freeWindows().isEmpty());
        assertFalse(snapshot.focusRecommendation().available());
    }

    @Test
    void preservesMinimalDataNeededForUiAndAiConsumers() {
        DailyReviewSummary summary = new DailyReviewSummary(
                DailyReviewSummarySource.FALLBACK,
                "High-pressure day",
                List.of("Resolve two overdue tasks", "Use the 14:00 slot for focused work"),
                "Deadline pressure is rising.",
                "Start with the oldest overdue item.",
                ""
        );
        DailyReviewOverdueItem overdue = new DailyReviewOverdueItem(
                "task-1",
                "Fix billing export",
                LocalDate.of(2026, 3, 9),
                LocalDateTime.of(2026, 3, 9, 18, 0),
                1,
                7,
                0.85,
                List.of("finance", "ops")
        );
        DailyReviewUpcomingItem upcoming = new DailyReviewUpcomingItem(
                "task-2",
                "Prepare team sync notes",
                LocalDate.of(2026, 3, 10),
                LocalDateTime.of(2026, 3, 10, 16, 0),
                0,
                true,
                true,
                4,
                0.55,
                List.of("team")
        );
        DailyReviewFreeWindow window = new DailyReviewFreeWindow(
                LocalDateTime.of(2026, 3, 10, 14, 0),
                LocalDateTime.of(2026, 3, 10, 15, 0),
                60,
                DailyReviewWindowSuitability.DEEP_WORK,
                false,
                "14:00-15:00"
        );
        DailyReviewFocusRecommendation focus = new DailyReviewFocusRecommendation(
                "Close the billing gap",
                "It is overdue and blocks downstream work.",
                "Use the 14:00 slot to finish the export fix.",
                DailyReviewSummarySource.AI
        );

        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                LocalDate.of(2026, 3, 10),
                null,
                8,
                1,
                2,
                3,
                95,
                false,
                summary,
                List.of(overdue),
                List.of(upcoming),
                List.of(new DailyReviewWorkInterval(
                        LocalDateTime.of(2026, 3, 10, 9, 0),
                        LocalDateTime.of(2026, 3, 10, 18, 0),
                        540,
                        true,
                        "09:00-18:00"
                )),
                List.of(new DailyReviewTimeBlock(
                        "task-2",
                        "Prepare team sync notes",
                        LocalDateTime.of(2026, 3, 10, 10, 0),
                        LocalDateTime.of(2026, 3, 10, 11, 0),
                        60,
                        "task_schedule",
                        true
                )),
                List.of(window),
                focus
        );

        assertEquals("High-pressure day", snapshot.summary().headline());
        assertEquals(2, snapshot.summary().bullets().size());
        assertTrue(snapshot.hasOverdueItems());
        assertTrue(snapshot.hasUpcomingItems());
        assertTrue(snapshot.hasWorkIntervals());
        assertTrue(snapshot.hasKnownTimeBlocks());
        assertTrue(snapshot.hasFreeWindows());
        assertEquals(DailyReviewWindowSuitability.DEEP_WORK, snapshot.freeWindows().getFirst().suitability());
        assertEquals("Close the billing gap", snapshot.focusRecommendation().title());
        assertTrue(snapshot.focusRecommendation().available());
    }
}
