package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class SyncScheduler implements AutoCloseable {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SyncScheduler.class);

    private final SyncCoordinator coordinator;
    private final boolean startupEnabled;
    private final boolean reconnectEnabled;
    private final Duration periodicInterval;
    private final Duration healthcheckInterval;
    private final BiConsumer<SyncRunResult, Throwable> runObserver;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean lastKnownOnline = new AtomicBoolean(true);

    public SyncScheduler(SyncCoordinator coordinator) {
        this(
                coordinator,
                ConfigManager.isCloudSyncStartupEnabled(),
                ConfigManager.isCloudSyncReconnectEnabled(),
                ConfigManager.getCloudSyncPeriodicInterval(),
                ConfigManager.getCloudSyncHealthcheckInterval(),
                (result, error) -> {
                });
    }

    SyncScheduler(SyncCoordinator coordinator, BiConsumer<SyncRunResult, Throwable> runObserver) {
        this(
                coordinator,
                ConfigManager.isCloudSyncStartupEnabled(),
                ConfigManager.isCloudSyncReconnectEnabled(),
                ConfigManager.getCloudSyncPeriodicInterval(),
                ConfigManager.getCloudSyncHealthcheckInterval(),
                runObserver);
    }

    SyncScheduler(
            SyncCoordinator coordinator,
            boolean startupEnabled,
            boolean reconnectEnabled,
            Duration periodicInterval,
            Duration healthcheckInterval) {
        this(coordinator, startupEnabled, reconnectEnabled, periodicInterval, healthcheckInterval, true, (result, error) -> {
        });
    }

    SyncScheduler(
            SyncCoordinator coordinator,
            boolean startupEnabled,
            boolean reconnectEnabled,
            Duration periodicInterval,
            Duration healthcheckInterval,
            BiConsumer<SyncRunResult, Throwable> runObserver) {
        this(coordinator, startupEnabled, reconnectEnabled, periodicInterval, healthcheckInterval, true, runObserver);
    }

    SyncScheduler(
            SyncCoordinator coordinator,
            boolean startupEnabled,
            boolean reconnectEnabled,
            Duration periodicInterval,
            Duration healthcheckInterval,
            boolean initiallyOnline) {
        this(coordinator, startupEnabled, reconnectEnabled, periodicInterval, healthcheckInterval, initiallyOnline, (result, error) -> {
        });
    }

    SyncScheduler(
            SyncCoordinator coordinator,
            boolean startupEnabled,
            boolean reconnectEnabled,
            Duration periodicInterval,
            Duration healthcheckInterval,
            boolean initiallyOnline,
            BiConsumer<SyncRunResult, Throwable> runObserver) {
        this.coordinator = coordinator;
        this.startupEnabled = startupEnabled;
        this.reconnectEnabled = reconnectEnabled;
        this.periodicInterval = periodicInterval;
        this.healthcheckInterval = healthcheckInterval;
        this.runObserver = Objects.requireNonNull(runObserver, "runObserver");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                AsyncContext.namedThreadFactory("cloud-sync-scheduler", true));
        this.lastKnownOnline.set(initiallyOnline);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (!coordinator.isSyncConfigured()) {
            LOG.info("cloud.sync.scheduler.start.skipped", "reason", "sync_not_configured");
            return;
        }
        if (startupEnabled) {
            scheduler.execute(() -> triggerInternal(SyncTrigger.STARTUP));
        }
        scheduler.scheduleWithFixedDelay(
                () -> triggerInternal(SyncTrigger.PERIODIC),
                periodicInterval.toSeconds(),
                periodicInterval.toSeconds(),
                TimeUnit.SECONDS);
        scheduler.scheduleWithFixedDelay(
                this::probeReconnect,
                healthcheckInterval.toSeconds(),
                healthcheckInterval.toSeconds(),
                TimeUnit.SECONDS);
    }

    public CompletableFuture<SyncRunResult> triggerManualSync() {
        return coordinator.syncNow(SyncTrigger.MANUAL);
    }

    void probeReconnect() {
        if (!reconnectEnabled || !coordinator.isSyncConfigured()) {
            return;
        }
        coordinator.checkLiveness().whenComplete((online, error) -> {
            if (error != null) {
                lastKnownOnline.set(false);
                LOG.warning("cloud.sync.scheduler.healthcheck.failed", "errorType", error.getClass().getSimpleName());
                return;
            }
            boolean isOnline = Boolean.TRUE.equals(online);
            boolean wasOnline = lastKnownOnline.getAndSet(isOnline);
            if (!wasOnline && isOnline) {
                triggerInternal(SyncTrigger.RECONNECT);
            }
        });
    }

    private void triggerInternal(SyncTrigger trigger) {
        coordinator.syncNow(trigger).whenComplete((result, error) -> {
            if (error != null) {
                lastKnownOnline.set(false);
                LOG.warning(
                        "cloud.sync.scheduler.run.failed",
                        "trigger", trigger.name(),
                        "errorType", error.getClass().getSimpleName());
                runObserver.accept(null, error);
                return;
            }
            lastKnownOnline.set(true);
            LOG.info(
                    "cloud.sync.scheduler.run.completed",
                    "trigger", trigger.name(),
                    "status", result.status(),
                    "acceptedChanges", result.acceptedChanges(),
                    "remoteChangeCount", result.remoteChangeCount());
            runObserver.accept(result, null);
        });
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
