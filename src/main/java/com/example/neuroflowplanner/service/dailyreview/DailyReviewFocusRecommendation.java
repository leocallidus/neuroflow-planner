package com.example.neuroflowplanner.service.dailyreview;

public record DailyReviewFocusRecommendation(
        String title,
        String rationale,
        String suggestedNextStep,
        DailyReviewSummarySource source) {

    public DailyReviewFocusRecommendation {
        title = title == null ? "" : title.trim();
        rationale = rationale == null ? "" : rationale.trim();
        suggestedNextStep = suggestedNextStep == null ? "" : suggestedNextStep.trim();
        source = source == null ? DailyReviewSummarySource.UNAVAILABLE : source;
    }

    public boolean available() {
        return !title.isBlank() || !rationale.isBlank() || !suggestedNextStep.isBlank();
    }
}
