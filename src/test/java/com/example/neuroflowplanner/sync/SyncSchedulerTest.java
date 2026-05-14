package com.example.neuroflowplanner.sync;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SyncScheduler Tests")
class SyncSchedulerTest {

    @Test
    @DisplayName("scheduler triggers startup, periodic and manual sync runs")
    void schedulerTriggersStartupPeriodicAndManualSyncRuns() throws Exception {
        try (StubSyncCoordinator coordinator = new StubSyncCoordinator();
             SyncScheduler scheduler = new SyncScheduler(
                     coordinator,
                     true,
                     false,
                     Duration.ofSeconds(1),
                     Duration.ofSeconds(1),
                     false)) {
            scheduler.start();
            scheduler.triggerManualSync().get();

            waitUntil(() -> coordinator.triggers.contains(SyncTrigger.STARTUP), 2_500);
            waitUntil(() -> coordinator.triggers.contains(SyncTrigger.PERIODIC), 4_000);

            assertTrue(coordinator.triggers.contains(SyncTrigger.MANUAL));
        }
    }

    @Test
    @DisplayName("scheduler triggers reconnect sync after offline to online transition")
    void schedulerTriggersReconnectAfterOfflineTransition() throws Exception {
        try (StubSyncCoordinator coordinator = new StubSyncCoordinator();
             SyncScheduler scheduler = new SyncScheduler(
                     coordinator,
                     false,
                     true,
                     Duration.ofSeconds(30),
                     Duration.ofSeconds(30),
                     false)) {
            scheduler.probeReconnect();

            waitUntil(() -> coordinator.triggers.contains(SyncTrigger.RECONNECT), 2_000);
        }
    }

    private void waitUntil(Check check, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (check.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(check.getAsBoolean(), "Condition was not met within timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean getAsBoolean();
    }

    private static final class StubSyncCoordinator extends SyncCoordinator {
        private final List<SyncTrigger> triggers = new CopyOnWriteArrayList<>();

        private StubSyncCoordinator() {
            super();
        }

        @Override
        public boolean isSyncConfigured() {
            return true;
        }

        @Override
        public CompletableFuture<Boolean> checkLiveness() {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<SyncRunResult> syncNow(SyncTrigger trigger) {
            triggers.add(trigger);
            return CompletableFuture.completedFuture(new SyncRunResult(
                    trigger,
                    "ok",
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    false,
                    1,
                    List.of()));
        }
    }
}
