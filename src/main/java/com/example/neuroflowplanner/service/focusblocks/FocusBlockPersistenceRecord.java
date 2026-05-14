package com.example.neuroflowplanner.service.focusblocks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FocusBlockPersistenceRecord(
        LocalDate reviewDate,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        String snapshotFingerprint,
        boolean limitedHistory,
        double profileConfidence,
        double switchDensityScore,
        long averageFocusMinutes,
        long stableFocusMinutes,
        long totalTrackedMinutes,
        int totalSessions,
        FocusBlockExplanation explanation,
        List<FocusBlockCandidate> candidateWindows,
        List<FocusBlockRecommendation> focusWindows,
        List<FocusBlockRecommendation> shortWindows,
        FocusBlockRecommendation nextRecommendedBlock,
        List<FocusBlockRisk> risks) {

    public FocusBlockPersistenceRecord {
        reviewDate = reviewDate == null ? LocalDate.now() : reviewDate;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
        snapshotFingerprint = snapshotFingerprint == null ? "" : snapshotFingerprint.trim();
        limitedHistory = limitedHistory;
        profileConfidence = clamp(profileConfidence);
        switchDensityScore = clamp(switchDensityScore);
        averageFocusMinutes = Math.max(0L, averageFocusMinutes);
        stableFocusMinutes = Math.max(0L, stableFocusMinutes);
        totalTrackedMinutes = Math.max(0L, totalTrackedMinutes);
        totalSessions = Math.max(0, totalSessions);
        explanation = explanation == null ? FocusBlockExplanation.unavailable() : explanation;
        candidateWindows = candidateWindows == null ? List.of() : List.copyOf(candidateWindows);
        focusWindows = focusWindows == null ? List.of() : List.copyOf(focusWindows);
        shortWindows = shortWindows == null ? List.of() : List.copyOf(shortWindows);
        nextRecommendedBlock = nextRecommendedBlock == null
                ? FocusBlockRecommendation.unavailable()
                : nextRecommendedBlock;
        risks = risks == null ? List.of() : List.copyOf(risks);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
