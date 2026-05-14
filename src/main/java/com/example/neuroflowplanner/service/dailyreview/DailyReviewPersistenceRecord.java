package com.example.neuroflowplanner.service.dailyreview;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyReviewPersistenceRecord(
        LocalDate reviewDate,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        String snapshotFingerprint,
        int activeTaskCount,
        int overdueTaskCount,
        int tasksDueTodayCount,
        int upcomingTaskCount,
        long trackedMinutesToday,
        boolean approximateFreeWindows,
        DailyReviewSummary summary,
        DailyReviewFocusRecommendation focusRecommendation,
        List<DailyReviewOverdueItem> overdueItems,
        List<DailyReviewUpcomingItem> upcomingItems,
        List<DailyReviewFreeWindow> freeWindows) {

    public DailyReviewPersistenceRecord {
        reviewDate = reviewDate == null ? LocalDate.now() : reviewDate;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
        snapshotFingerprint = snapshotFingerprint == null ? "" : snapshotFingerprint.trim();
        activeTaskCount = Math.max(0, activeTaskCount);
        overdueTaskCount = Math.max(0, overdueTaskCount);
        tasksDueTodayCount = Math.max(0, tasksDueTodayCount);
        upcomingTaskCount = Math.max(0, upcomingTaskCount);
        trackedMinutesToday = Math.max(0L, trackedMinutesToday);
        summary = summary == null
                ? new DailyReviewSummary(DailyReviewSummarySource.UNAVAILABLE, "", List.of(), "", "", "")
                : summary;
        focusRecommendation = focusRecommendation == null
                ? new DailyReviewFocusRecommendation("", "", "", DailyReviewSummarySource.UNAVAILABLE)
                : focusRecommendation;
        overdueItems = overdueItems == null ? List.of() : List.copyOf(overdueItems);
        upcomingItems = upcomingItems == null ? List.of() : List.copyOf(upcomingItems);
        freeWindows = freeWindows == null ? List.of() : List.copyOf(freeWindows);
    }
}
