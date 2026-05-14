package com.example.neuroflowplanner.service.planningquality;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PlanningQualityPersistenceRecord(
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant generatedAt,
        String modelId,
        boolean aiUsed,
        String snapshotFingerprint,
        int activeTaskCount,
        int completedTaskCount,
        int estimatedTaskCount,
        int scheduledTaskCount,
        int trackedTaskCount,
        int trackedSessionCount,
        boolean limitedData,
        PlanningQualitySummary summary,
        TimeEstimateAccuracyMetric accuracyMetric,
        RescheduleRateMetric rescheduleMetric,
        RhythmStabilityMetric rhythmMetric,
        List<PlanningQualityRisk> risks,
        List<PlanningQualityRecommendation> recommendations) {

    public PlanningQualityPersistenceRecord {
        periodEnd = periodEnd == null ? LocalDate.now() : periodEnd;
        periodStart = periodStart == null ? periodEnd.minusDays(13) : periodStart;
        if (periodStart.isAfter(periodEnd)) {
            LocalDate originalStart = periodStart;
            periodStart = periodEnd;
            periodEnd = originalStart;
        }
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        modelId = modelId == null ? "" : modelId.trim();
        snapshotFingerprint = snapshotFingerprint == null ? "" : snapshotFingerprint.trim();
        activeTaskCount = Math.max(0, activeTaskCount);
        completedTaskCount = Math.max(0, completedTaskCount);
        estimatedTaskCount = Math.max(0, estimatedTaskCount);
        scheduledTaskCount = Math.max(0, scheduledTaskCount);
        trackedTaskCount = Math.max(0, trackedTaskCount);
        trackedSessionCount = Math.max(0, trackedSessionCount);
        summary = summary == null ? PlanningQualitySummary.unavailable() : summary;
        accuracyMetric = accuracyMetric == null ? TimeEstimateAccuracyMetric.unavailable() : accuracyMetric;
        rescheduleMetric = rescheduleMetric == null ? RescheduleRateMetric.unavailable() : rescheduleMetric;
        rhythmMetric = rhythmMetric == null ? RhythmStabilityMetric.unavailable() : rhythmMetric;
        risks = risks == null ? List.of() : List.copyOf(risks);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
