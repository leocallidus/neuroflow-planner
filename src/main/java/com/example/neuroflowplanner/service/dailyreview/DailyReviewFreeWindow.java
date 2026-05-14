package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDateTime;

public record DailyReviewFreeWindow(
        LocalDateTime start,
        LocalDateTime end,
        int durationMinutes,
        DailyReviewWindowSuitability suitability,
        boolean approximate,
        String label) {

    public DailyReviewFreeWindow {
        durationMinutes = Math.max(0, durationMinutes);
        suitability = suitability == null ? DailyReviewWindowSuitability.UNKNOWN : suitability;
        label = label == null ? "" : label.trim();
    }
}
