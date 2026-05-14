package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.TimeSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RhythmStabilityCalculator {

    private static final long PRODUCTIVE_DAY_MINUTES = 45L;
    private static final double STABLE_THRESHOLD = 0.72;
    private static final double MODERATE_THRESHOLD = 0.45;

    public RhythmStabilityMetric calculate(
            List<PlanningQualityDayAggregate> dayAggregates,
            List<TimeSession> sessionsInPeriod) {
        List<PlanningQualityDayAggregate> safeAggregates = dayAggregates == null ? List.of() : dayAggregates;
        List<TimeSession> safeSessions = sessionsInPeriod == null ? List.of() : sessionsInPeriod;
        if (safeAggregates.isEmpty()) {
            return RhythmStabilityMetric.unavailable();
        }

        Map<LocalDate, LocalTime> firstSessionStartByDay = new HashMap<>();
        for (TimeSession session : safeSessions) {
            if (session == null || session.getStartedAt() == null) {
                continue;
            }
            LocalDate date = session.getStartedAt().toLocalDate();
            LocalTime start = session.getStartedAt().toLocalTime();
            firstSessionStartByDay.merge(
                    date,
                    start,
                    (existing, candidate) -> existing.isBefore(candidate) ? existing : candidate
            );
        }

        int analyzedDayCount = safeAggregates.size();
        int productiveDayCount = 0;
        int overloadedDayCount = 0;
        int emptyDayCount = 0;
        int approximateDayCount = 0;

        List<Double> focusMinutesValues = new ArrayList<>();
        List<Double> startMinuteValues = new ArrayList<>();

        for (PlanningQualityDayAggregate aggregate : safeAggregates) {
            if (aggregate == null) {
                continue;
            }
            if (aggregate.approximate()) {
                approximateDayCount++;
            }
            if (aggregate.overloaded()) {
                overloadedDayCount++;
            }
            if (aggregate.emptyWorkday()) {
                emptyDayCount++;
            }
            if (aggregate.trackedMinutes() >= PRODUCTIVE_DAY_MINUTES || aggregate.trackedSessionCount() > 0) {
                productiveDayCount++;
                focusMinutesValues.add((double) aggregate.trackedMinutes());
                LocalTime firstStart = firstSessionStartByDay.get(aggregate.date());
                if (firstStart != null) {
                    startMinuteValues.add((double) (firstStart.getHour() * 60 + firstStart.getMinute()));
                }
            }
        }

        if (productiveDayCount == 0) {
            return RhythmStabilityMetric.unavailable();
        }

        int startTimeVariabilityMinutes = (int) Math.round(standardDeviation(startMinuteValues));
        double meanFocusMinutes = mean(focusMinutesValues);
        double focusStdDev = standardDeviation(focusMinutesValues);
        double focusMinutesVariability = meanFocusMinutes <= 0.0 ? 0.0 : focusStdDev / meanFocusMinutes;

        double productiveCoverage = productiveDayCount / (double) analyzedDayCount;
        double startConsistency = startMinuteValues.size() >= 2
                ? clamp01(1.0 - (startTimeVariabilityMinutes / 180.0))
                : 0.55;
        double focusConsistency = clamp01(1.0 - focusMinutesVariability);
        double overloadPenalty = overloadedDayCount / (double) analyzedDayCount;
        double emptyPenalty = emptyDayCount / (double) analyzedDayCount;
        double shapeBalance = clamp01(1.0 - (overloadPenalty * 0.7 + emptyPenalty * 0.5));
        double score = clamp01(
                productiveCoverage * 0.30
                        + startConsistency * 0.30
                        + focusConsistency * 0.25
                        + shapeBalance * 0.15
        );

        RhythmStabilityBand band = resolveBand(score);
        boolean approximate = analyzedDayCount < 5
                || productiveDayCount < 3
                || approximateDayCount > 0
                || startMinuteValues.size() < 2;

        return new RhythmStabilityMetric(
                band,
                score,
                analyzedDayCount,
                productiveDayCount,
                startTimeVariabilityMinutes,
                focusMinutesVariability,
                approximate
        );
    }

    private RhythmStabilityBand resolveBand(double score) {
        if (score >= STABLE_THRESHOLD) {
            return RhythmStabilityBand.STABLE;
        }
        if (score >= MODERATE_THRESHOLD) {
            return RhythmStabilityBand.MODERATE;
        }
        return RhythmStabilityBand.CHAOTIC;
    }

    private double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (Double value : values) {
            if (value != null && Double.isFinite(value)) {
                total += value;
            }
        }
        return total / values.size();
    }

    private double standardDeviation(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double variance = 0.0;
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) {
                continue;
            }
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= values.size();
        return Math.sqrt(variance);
    }

    private double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
