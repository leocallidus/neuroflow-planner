package com.example.neuroflowplanner.service.focusblocks;

import java.time.Instant;
import java.time.LocalDate;

public record FocusBlockRecommendationResult(
        FocusBlockRecommendationSnapshot snapshot,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        boolean fromCache) {

    public FocusBlockRecommendationResult {
        snapshot = snapshot == null
                ? new FocusBlockRecommendationSnapshot(
                        LocalDate.now(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true)
                : snapshot;
        generatedAt = generatedAt == null ? snapshot.generatedAt() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
    }

    public LocalDate reviewDate() {
        return snapshot.reviewDate();
    }

    public FocusBlockExplanation explanation() {
        return snapshot.explanation();
    }

    public FocusBlockRecommendation nextRecommendedBlock() {
        return snapshot.nextRecommendedBlock();
    }

    public FocusBlockRecommendationResult withFromCache(boolean newFromCache) {
        return new FocusBlockRecommendationResult(snapshot, generatedAt, modelId, aiUsed, newFromCache);
    }
}
