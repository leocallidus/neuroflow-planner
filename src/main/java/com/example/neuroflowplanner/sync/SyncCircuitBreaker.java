package com.example.neuroflowplanner.sync;

import java.time.Duration;

final class SyncCircuitBreaker {
    private final int failureThreshold;
    private final Duration cooldown;

    private int consecutiveFailures;
    private long openedAtEpochMillis;
    private boolean halfOpenProbeInFlight;

    SyncCircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldown = cooldown == null || cooldown.isNegative() || cooldown.isZero()
                ? Duration.ofSeconds(30)
                : cooldown;
    }

    synchronized void beforeRequest() {
        if (openedAtEpochMillis <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - openedAtEpochMillis;
        long cooldownMillis = cooldown.toMillis();
        if (elapsed >= cooldownMillis) {
            if (!halfOpenProbeInFlight) {
                halfOpenProbeInFlight = true;
                return;
            }
            throw new SyncCircuitOpenException("Cloud sync circuit breaker is half-open", 0L);
        }
        throw new SyncCircuitOpenException(
                "Cloud sync circuit breaker is open",
                Math.max(0L, cooldownMillis - elapsed));
    }

    synchronized void recordSuccess() {
        consecutiveFailures = 0;
        openedAtEpochMillis = 0L;
        halfOpenProbeInFlight = false;
    }

    synchronized void recordFailure() {
        if (openedAtEpochMillis > 0L && halfOpenProbeInFlight) {
            openedAtEpochMillis = System.currentTimeMillis();
            halfOpenProbeInFlight = false;
            consecutiveFailures = failureThreshold;
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            openedAtEpochMillis = System.currentTimeMillis();
            halfOpenProbeInFlight = false;
        }
    }
}
