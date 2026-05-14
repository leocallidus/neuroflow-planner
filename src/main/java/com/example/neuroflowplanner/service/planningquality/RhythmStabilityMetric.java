package com.example.neuroflowplanner.service.planningquality;

public record RhythmStabilityMetric(
        RhythmStabilityBand band,
        double score,
        int analyzedDayCount,
        int productiveDayCount,
        int startTimeVariabilityMinutes,
        double focusMinutesVariability,
        boolean approximate) {

    public RhythmStabilityMetric {
        band = band == null ? RhythmStabilityBand.UNAVAILABLE : band;
        score = Math.max(0.0, Math.min(1.0, Double.isFinite(score) ? score : 0.0));
        analyzedDayCount = Math.max(0, analyzedDayCount);
        productiveDayCount = Math.max(0, productiveDayCount);
        startTimeVariabilityMinutes = Math.max(0, startTimeVariabilityMinutes);
        focusMinutesVariability = Math.max(0.0, Double.isFinite(focusMinutesVariability) ? focusMinutesVariability : 0.0);
    }

    public static RhythmStabilityMetric unavailable() {
        return new RhythmStabilityMetric(RhythmStabilityBand.UNAVAILABLE, 0.0, 0, 0, 0, 0.0, true);
    }

    public boolean available() {
        return band != RhythmStabilityBand.UNAVAILABLE && analyzedDayCount > 0;
    }
}
