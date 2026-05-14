package com.example.neuroflowplanner.service.focusblocks;

public record FocusHourScore(
        int hourOfDay,
        double productivityScore,
        int sessionCount,
        long trackedMinutes,
        double interruptionPenaltyScore) {

    public FocusHourScore {
        hourOfDay = Math.max(0, Math.min(23, hourOfDay));
        productivityScore = clamp(productivityScore);
        sessionCount = Math.max(0, sessionCount);
        trackedMinutes = Math.max(0L, trackedMinutes);
        interruptionPenaltyScore = clamp(interruptionPenaltyScore);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
