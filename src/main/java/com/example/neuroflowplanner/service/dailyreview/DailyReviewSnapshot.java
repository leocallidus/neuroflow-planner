package com.example.neuroflowplanner.service.dailyreview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyReviewSnapshot(
        LocalDate reviewDate,
        Instant generatedAt,
        int activeTaskCount,
        int overdueTaskCount,
        int tasksDueTodayCount,
        int upcomingTaskCount,
        long trackedMinutesToday,
        boolean approximateFreeWindows,
        DailyReviewSummary summary,
        List<DailyReviewOverdueItem> overdueItems,
        List<DailyReviewUpcomingItem> upcomingItems,
        List<DailyReviewWorkInterval> workIntervals,
        List<DailyReviewTimeBlock> knownTimeBlocks,
        List<DailyReviewFreeWindow> freeWindows,
        DailyReviewFocusRecommendation focusRecommendation) {

    public DailyReviewSnapshot {
        reviewDate = reviewDate == null ? LocalDate.now() : reviewDate;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        activeTaskCount = Math.max(0, activeTaskCount);
        overdueTaskCount = Math.max(0, overdueTaskCount);
        tasksDueTodayCount = Math.max(0, tasksDueTodayCount);
        upcomingTaskCount = Math.max(0, upcomingTaskCount);
        trackedMinutesToday = Math.max(0L, trackedMinutesToday);
        summary = summary == null ? new DailyReviewSummary(
                DailyReviewSummarySource.UNAVAILABLE,
                "",
                List.of(),
                "",
                "",
                "AI summary has not been generated yet."
        ) : summary;
        overdueItems = overdueItems == null ? List.of() : List.copyOf(overdueItems);
        upcomingItems = upcomingItems == null ? List.of() : List.copyOf(upcomingItems);
        workIntervals = workIntervals == null ? List.of() : List.copyOf(workIntervals);
        knownTimeBlocks = knownTimeBlocks == null ? List.of() : List.copyOf(knownTimeBlocks);
        freeWindows = freeWindows == null ? List.of() : List.copyOf(freeWindows);
        focusRecommendation = focusRecommendation == null ? new DailyReviewFocusRecommendation(
                "",
                "",
                "",
                DailyReviewSummarySource.UNAVAILABLE
        ) : focusRecommendation;
    }

    public boolean hasOverdueItems() {
        return !overdueItems.isEmpty();
    }

    public boolean hasUpcomingItems() {
        return !upcomingItems.isEmpty();
    }

    public boolean hasWorkIntervals() {
        return !workIntervals.isEmpty();
    }

    public boolean hasKnownTimeBlocks() {
        return !knownTimeBlocks.isEmpty();
    }

    public boolean hasFreeWindows() {
        return !freeWindows.isEmpty();
    }
}
