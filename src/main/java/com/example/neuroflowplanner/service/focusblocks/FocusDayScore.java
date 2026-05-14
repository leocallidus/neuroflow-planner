package com.example.neuroflowplanner.service.focusblocks;

import java.time.DayOfWeek;

public record FocusDayScore(
        DayOfWeek dayOfWeek,
        double productivityScore,
        int sessionCount,
        long trackedMinutes) {

    public FocusDayScore {
        dayOfWeek = dayOfWeek == null ? DayOfWeek.MONDAY : dayOfWeek;
        productivityScore = Math.max(0.0, Math.min(1.0, productivityScore));
        sessionCount = Math.max(0, sessionCount);
        trackedMinutes = Math.max(0L, trackedMinutes);
    }
}
