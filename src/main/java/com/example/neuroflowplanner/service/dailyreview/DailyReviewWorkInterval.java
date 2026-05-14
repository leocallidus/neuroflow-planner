package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDateTime;

public record DailyReviewWorkInterval(
        LocalDateTime start,
        LocalDateTime end,
        int durationMinutes,
        boolean activeDay,
        String label) {

    public DailyReviewWorkInterval {
        durationMinutes = Math.max(0, durationMinutes);
        label = label == null ? "" : label.trim();
    }
}
