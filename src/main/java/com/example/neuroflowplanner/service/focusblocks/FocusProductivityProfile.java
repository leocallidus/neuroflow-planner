package com.example.neuroflowplanner.service.focusblocks;

import java.time.Instant;
import java.util.List;

public record FocusProductivityProfile(
        Instant generatedAt,
        double confidence,
        double switchDensityScore,
        long averageFocusMinutes,
        long stableFocusMinutes,
        long totalTrackedMinutes,
        int totalSessions,
        boolean limitedHistory,
        List<FocusDayScore> dayScores,
        List<FocusHourScore> hourScores) {

    public FocusProductivityProfile {
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        switchDensityScore = Math.max(0.0, Math.min(1.0, switchDensityScore));
        averageFocusMinutes = Math.max(0L, averageFocusMinutes);
        stableFocusMinutes = Math.max(0L, stableFocusMinutes);
        totalTrackedMinutes = Math.max(0L, totalTrackedMinutes);
        totalSessions = Math.max(0, totalSessions);
        dayScores = dayScores == null ? List.of() : List.copyOf(dayScores);
        hourScores = hourScores == null ? List.of() : List.copyOf(hourScores);
    }

    public static FocusProductivityProfile unavailable() {
        return new FocusProductivityProfile(
                Instant.now(),
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                true,
                List.of(),
                List.of()
        );
    }

    public boolean available() {
        return !hourScores.isEmpty() || !dayScores.isEmpty();
    }
}
