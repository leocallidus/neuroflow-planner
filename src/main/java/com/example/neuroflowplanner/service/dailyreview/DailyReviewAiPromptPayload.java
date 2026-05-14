package com.example.neuroflowplanner.service.dailyreview;

public record DailyReviewAiPromptPayload(
        String systemPrompt,
        String userPrompt,
        DailyReviewSummary fallbackSummary,
        DailyReviewFocusRecommendation fallbackFocusRecommendation) {

    public DailyReviewAiPromptPayload {
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        userPrompt = userPrompt == null ? "" : userPrompt.trim();
        fallbackSummary = fallbackSummary == null
                ? new DailyReviewSummary(
                        DailyReviewSummarySource.UNAVAILABLE,
                        "",
                        java.util.List.of(),
                        "",
                        "",
                        "AI daily review prompt is unavailable."
                )
                : fallbackSummary;
        fallbackFocusRecommendation = fallbackFocusRecommendation == null
                ? new DailyReviewFocusRecommendation("", "", "", DailyReviewSummarySource.UNAVAILABLE)
                : fallbackFocusRecommendation;
    }
}
