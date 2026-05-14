package com.example.neuroflowplanner.service.dailyreview;

import java.util.List;

public record DailyReviewSummary(
        DailyReviewSummarySource source,
        String headline,
        List<String> bullets,
        String riskNote,
        String nextStep,
        String unavailableReason) {

    public DailyReviewSummary {
        source = source == null ? DailyReviewSummarySource.UNAVAILABLE : source;
        headline = headline == null ? "" : headline.trim();
        bullets = bullets == null ? List.of() : bullets.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(String::trim)
                .toList();
        riskNote = riskNote == null ? "" : riskNote.trim();
        nextStep = nextStep == null ? "" : nextStep.trim();
        unavailableReason = unavailableReason == null ? "" : unavailableReason.trim();
    }

    public boolean available() {
        return source != DailyReviewSummarySource.UNAVAILABLE;
    }

    public boolean aiGenerated() {
        return source == DailyReviewSummarySource.AI;
    }
}
