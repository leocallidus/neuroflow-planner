package com.example.neuroflowplanner.service.planningquality;

public record TimeEstimateAccuracyMetric(
        int estimatedTaskCount,
        int comparableTaskCount,
        double averageErrorRatio,
        double hitRate,
        double underestimationBias,
        double overestimationBias,
        boolean approximate) {

    public TimeEstimateAccuracyMetric {
        estimatedTaskCount = Math.max(0, estimatedTaskCount);
        comparableTaskCount = Math.max(0, comparableTaskCount);
        averageErrorRatio = clampRatio(averageErrorRatio);
        hitRate = clampRatio(hitRate);
        underestimationBias = clampRatio(underestimationBias);
        overestimationBias = clampRatio(overestimationBias);
    }

    public static TimeEstimateAccuracyMetric unavailable() {
        return new TimeEstimateAccuracyMetric(0, 0, 0.0, 0.0, 0.0, 0.0, true);
    }

    public boolean available() {
        return comparableTaskCount > 0;
    }

    private static double clampRatio(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
