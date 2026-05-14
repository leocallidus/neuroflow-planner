package com.example.neuroflowplanner.service.planningquality;

import java.time.Instant;
import java.time.LocalDate;

public record PlanningQualityResult(
        PlanningQualitySnapshot snapshot,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        boolean fromCache) {

    public PlanningQualityResult {
        snapshot = snapshot == null
                ? new PlanningQualitySnapshot(
                LocalDate.now().minusDays(13),
                LocalDate.now(),
                Instant.now(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                true
        )
                : snapshot;
        generatedAt = generatedAt == null ? snapshot.generatedAt() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
    }

    public LocalDate periodStart() {
        return snapshot.periodStart();
    }

    public LocalDate periodEnd() {
        return snapshot.periodEnd();
    }

    public PlanningQualitySummary summary() {
        return snapshot.summary();
    }

    public PlanningQualityResult withFromCache(boolean newFromCache) {
        return new PlanningQualityResult(snapshot, generatedAt, modelId, aiUsed, newFromCache);
    }
}
