package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.TimeSession;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FocusProductivityProfileBuilder {

    static final int DEFAULT_LOOKBACK_DAYS = 28;
    private static final long MINUTES_FOR_LIMITED_HISTORY = 180;
    private static final int SESSIONS_FOR_LIMITED_HISTORY = 5;
    private static final double TARGET_STABLE_FOCUS_MINUTES = 90.0;

    private final Supplier<List<TimeSession>> timeSessionsSupplier;

    public FocusProductivityProfileBuilder() {
        this(() -> DatabaseManager.getInstance().loadTimeSessions());
    }

    FocusProductivityProfileBuilder(Supplier<List<TimeSession>> timeSessionsSupplier) {
        this.timeSessionsSupplier = timeSessionsSupplier;
    }

    public FocusProductivityProfile buildProfile() {
        return buildProfile(LocalDate.now(), DEFAULT_LOOKBACK_DAYS);
    }

    public FocusProductivityProfile buildProfile(LocalDate referenceDate, int lookbackDays) {
        LocalDate effectiveReferenceDate = referenceDate == null ? LocalDate.now() : referenceDate;
        int effectiveLookbackDays = Math.max(1, lookbackDays);
        LocalDate lowerBound = effectiveReferenceDate.minusDays(effectiveLookbackDays - 1L);

        List<TimeSession> sessions = safeSessions().stream()
                .filter(session -> session != null
                        && session.getStartedAt() != null
                        && session.getMinutes() > 0
                        && !session.getStartedAt().toLocalDate().isBefore(lowerBound)
                        && !session.getStartedAt().toLocalDate().isAfter(effectiveReferenceDate))
                .sorted(Comparator.comparing(TimeSession::getStartedAt))
                .toList();

        if (sessions.isEmpty()) {
            return FocusProductivityProfile.unavailable();
        }

        long totalTrackedMinutes = sessions.stream()
                .mapToLong(TimeSession::getMinutes)
                .sum();
        int totalSessions = sessions.size();
        long averageFocusMinutes = Math.round(totalTrackedMinutes / (double) totalSessions);
        long stableFocusMinutes = Math.round(
                sessions.stream()
                        .mapToLong(TimeSession::getMinutes)
                        .filter(minutes -> minutes >= 45)
                        .average()
                        .orElse(averageFocusMinutes)
        );
        double switchDensityScore = calculateSwitchDensityScore(totalSessions, totalTrackedMinutes);

        Map<Integer, AggregateBucket> hourBuckets = new HashMap<>();
        Map<DayOfWeek, AggregateBucket> dayBuckets = new EnumMap<>(DayOfWeek.class);

        for (TimeSession session : sessions) {
            LocalDateTime startedAt = session.getStartedAt();
            long minutes = Math.max(0L, session.getMinutes());
            hourBuckets.computeIfAbsent(startedAt.getHour(), key -> new AggregateBucket()).add(minutes);
            dayBuckets.computeIfAbsent(startedAt.getDayOfWeek(), key -> new AggregateBucket()).add(minutes);
        }

        List<FocusHourScore> hourScores = buildHourScores(hourBuckets);
        List<FocusDayScore> dayScores = buildDayScores(dayBuckets);
        boolean limitedHistory = totalTrackedMinutes < MINUTES_FOR_LIMITED_HISTORY
                || totalSessions < SESSIONS_FOR_LIMITED_HISTORY;
        double confidence = calculateConfidence(totalTrackedMinutes, totalSessions, switchDensityScore, limitedHistory);

        return new FocusProductivityProfile(
                Instant.now(),
                confidence,
                switchDensityScore,
                averageFocusMinutes,
                stableFocusMinutes,
                totalTrackedMinutes,
                totalSessions,
                limitedHistory,
                dayScores,
                hourScores
        );
    }

    private List<TimeSession> safeSessions() {
        List<TimeSession> sessions = timeSessionsSupplier == null ? List.of() : timeSessionsSupplier.get();
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    private List<FocusHourScore> buildHourScores(Map<Integer, AggregateBucket> hourBuckets) {
        long maxMinutes = hourBuckets.values().stream()
                .mapToLong(AggregateBucket::trackedMinutes)
                .max()
                .orElse(0L);
        List<FocusHourScore> scores = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            AggregateBucket bucket = hourBuckets.getOrDefault(hour, AggregateBucket.EMPTY);
            double productivityScore = computeProductivityScore(bucket, maxMinutes);
            scores.add(new FocusHourScore(
                    hour,
                    productivityScore,
                    bucket.sessionCount(),
                    bucket.trackedMinutes(),
                    computeInterruptionPenalty(bucket)
            ));
        }
        return List.copyOf(scores);
    }

    private List<FocusDayScore> buildDayScores(Map<DayOfWeek, AggregateBucket> dayBuckets) {
        long maxMinutes = dayBuckets.values().stream()
                .mapToLong(AggregateBucket::trackedMinutes)
                .max()
                .orElse(0L);
        List<FocusDayScore> scores = new ArrayList<>(DayOfWeek.values().length);
        for (DayOfWeek dayOfWeek : DayOfWeek.values()) {
            AggregateBucket bucket = dayBuckets.getOrDefault(dayOfWeek, AggregateBucket.EMPTY);
            scores.add(new FocusDayScore(
                    dayOfWeek,
                    computeProductivityScore(bucket, maxMinutes),
                    bucket.sessionCount(),
                    bucket.trackedMinutes()
            ));
        }
        return List.copyOf(scores);
    }

    private double computeProductivityScore(AggregateBucket bucket, long maxMinutes) {
        if (bucket.sessionCount() == 0 || maxMinutes <= 0) {
            return 0.0;
        }
        double normalizedMinutes = bucket.trackedMinutes() / (double) maxMinutes;
        double stabilityScore = Math.min(1.0, bucket.averageMinutes() / TARGET_STABLE_FOCUS_MINUTES);
        double interruptionPenalty = computeInterruptionPenalty(bucket);
        return clamp((normalizedMinutes * 0.58) + (stabilityScore * 0.42) - (interruptionPenalty * 0.22));
    }

    private double computeInterruptionPenalty(AggregateBucket bucket) {
        if (bucket.sessionCount() == 0 || bucket.trackedMinutes() <= 0) {
            return 0.0;
        }
        double sessionsPerTrackedHour = bucket.sessionCount() / Math.max(1.0, bucket.trackedMinutes() / 60.0);
        return clamp(sessionsPerTrackedHour / 3.5);
    }

    private double calculateSwitchDensityScore(int totalSessions, long totalTrackedMinutes) {
        if (totalSessions <= 0 || totalTrackedMinutes <= 0) {
            return 0.0;
        }
        double sessionsPerTrackedHour = totalSessions / Math.max(1.0, totalTrackedMinutes / 60.0);
        return clamp(sessionsPerTrackedHour / 3.5);
    }

    private double calculateConfidence(long totalTrackedMinutes, int totalSessions, double switchDensityScore, boolean limitedHistory) {
        double minuteSignal = Math.min(1.0, totalTrackedMinutes / 720.0);
        double sessionSignal = Math.min(1.0, totalSessions / 14.0);
        double confidence = (minuteSignal * 0.6) + (sessionSignal * 0.4) - (switchDensityScore * 0.12);
        if (limitedHistory) {
            confidence *= 0.78;
        }
        return clamp(confidence);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class AggregateBucket {

        static final AggregateBucket EMPTY = new AggregateBucket();

        private long trackedMinutes;
        private int sessionCount;

        void add(long minutes) {
            trackedMinutes += Math.max(0L, minutes);
            sessionCount++;
        }

        long trackedMinutes() {
            return trackedMinutes;
        }

        int sessionCount() {
            return sessionCount;
        }

        double averageMinutes() {
            return sessionCount <= 0 ? 0.0 : trackedMinutes / (double) sessionCount;
        }
    }
}
