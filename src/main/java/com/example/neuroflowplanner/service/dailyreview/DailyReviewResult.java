package com.example.neuroflowplanner.service.dailyreview;

import java.time.Instant;
import java.time.LocalDate;

public record DailyReviewResult(
        DailyReviewSnapshot snapshot,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        boolean fromCache) {

    public DailyReviewResult {
        snapshot = snapshot == null
                ? new DailyReviewSnapshot(
                        LocalDate.now(),
                        Instant.now(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)
                : snapshot;
        generatedAt = generatedAt == null ? snapshot.generatedAt() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
    }

    public LocalDate reviewDate() {
        return snapshot.reviewDate();
    }

    public DailyReviewSummary summary() {
        return snapshot.summary();
    }

    public DailyReviewFocusRecommendation focusRecommendation() {
        return snapshot.focusRecommendation();
    }

    public DailyReviewResult withFromCache(boolean newFromCache) {
        return new DailyReviewResult(snapshot, generatedAt, modelId, aiUsed, newFromCache);
    }
}
