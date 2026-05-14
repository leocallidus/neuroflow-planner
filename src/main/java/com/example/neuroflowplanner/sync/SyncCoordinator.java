package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncCoordinator implements AutoCloseable {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SyncCoordinator.class);
    private static final long ACCESS_TOKEN_SAFETY_WINDOW_SECONDS = 30L;
    private static final int MAX_SYNC_ROUNDS = 50;

    private final SyncStateRepository stateRepository;
    private final AuthClient authClient;
    private final SyncApiClient syncApiClient;
    private final ObjectMapper mapper;
    private final ExecutorService executor;
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final Wave1SyncPayloadMapper payloadMapper;
    private final Wave1SyncApplyService applyService;

    private volatile AccessTokenState accessTokenState;

    public SyncCoordinator() {
        this(new SyncStateRepository(), new AuthClient(), new SyncApiClient());
    }

    public SyncCoordinator(
            SyncStateRepository stateRepository,
            AuthClient authClient,
            SyncApiClient syncApiClient) {
        this.stateRepository = stateRepository;
        this.authClient = authClient;
        this.syncApiClient = syncApiClient;
        this.mapper = AiObjectMapperFactory.createMapper(false);
        this.executor = Executors.newSingleThreadExecutor(AsyncContext.namedThreadFactory("cloud-sync", true));
        this.payloadMapper = new Wave1SyncPayloadMapper();
        this.applyService = new Wave1SyncApplyService();
    }

    public boolean isSyncConfigured() {
        String baseUrl = ConfigManager.getCloudSyncBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }

    public boolean hasAuthenticatedSession() {
        return stateRepository.hasAuthenticatedSession();
    }

    public CompletableFuture<SyncSessionSnapshot> register(String email, String password, String displayName) {
        return AsyncContext.supplyAsync(() -> {
            LocalDeviceIdentity deviceIdentity = stateRepository.ensureDeviceIdentity();
            SyncPayloads.TokenBundleResponse tokenBundle = authClient.register(email, password, displayName, deviceIdentity);
            stateRepository.saveAuthenticatedSession(tokenBundle, deviceIdentity);
            accessTokenState = AccessTokenState.fromTokenBundle(tokenBundle);
            return toSessionSnapshot(tokenBundle);
        }, executor);
    }

    public CompletableFuture<SyncSessionSnapshot> login(String email, String password) {
        return AsyncContext.supplyAsync(() -> {
            LocalDeviceIdentity deviceIdentity = stateRepository.ensureDeviceIdentity();
            SyncPayloads.TokenBundleResponse tokenBundle = authClient.login(email, password, deviceIdentity);
            stateRepository.saveAuthenticatedSession(tokenBundle, deviceIdentity);
            accessTokenState = AccessTokenState.fromTokenBundle(tokenBundle);
            return toSessionSnapshot(tokenBundle);
        }, executor);
    }

    public CompletableFuture<Void> logout() {
        return AsyncContext.runAsync(() -> {
            String refreshToken = stateRepository.loadRefreshToken();
            try {
                if (!refreshToken.isBlank()) {
                    authClient.logout(refreshToken);
                }
            } catch (RuntimeException e) {
                LOG.warning("cloud.sync.logout.remote.failed", "errorType", e.getClass().getSimpleName());
            } finally {
                stateRepository.clearAuthenticatedSession();
                accessTokenState = null;
            }
        }, executor);
    }

    public CompletableFuture<Boolean> checkLiveness() {
        if (!isSyncConfigured()) {
            return CompletableFuture.completedFuture(false);
        }
        return AsyncContext.supplyAsync(syncApiClient::checkLiveness, executor);
    }

    public CompletableFuture<SyncPayloads.DeviceListResponse> listDevices() {
        return AsyncContext.supplyAsync(() -> {
            String accessToken = ensureValidAccessToken();
            try {
                return authClient.listDevices(accessToken);
            } catch (SyncHttpException e) {
                if (e.statusCode() == 401) {
                    return authClient.listDevices(refreshAccessToken());
                }
                throw e;
            }
        }, executor);
    }

    public CompletableFuture<SyncPayloads.DeviceRevokeResponse> revokeDevice(String deviceId) {
        return AsyncContext.supplyAsync(() -> {
            if (deviceId == null || deviceId.isBlank()) {
                throw new IllegalArgumentException("deviceId is required");
            }
            String accessToken = ensureValidAccessToken();
            try {
                return authClient.revokeDevice(accessToken, deviceId.trim());
            } catch (SyncHttpException e) {
                if (e.statusCode() == 401) {
                    return authClient.revokeDevice(refreshAccessToken(), deviceId.trim());
                }
                throw e;
            }
        }, executor);
    }

    public CompletableFuture<Void> prepareBootstrapReplay() {
        if (!syncInProgress.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Sync is already running"));
        }
        return AsyncContext.runAsync(() -> {
            try {
                stateRepository.resetSyncProgressForBootstrap();
                LOG.info("cloud.sync.bootstrap.replay.prepared");
            } finally {
                syncInProgress.set(false);
            }
        }, executor);
    }

    public CompletableFuture<SyncRunResult> syncNow(SyncTrigger trigger) {
        if (!ConfigManager.isCloudSyncEnabled()) {
            return CompletableFuture.completedFuture(SyncRunResult.skipped(trigger, "sync_disabled"));
        }
        if (!isSyncConfigured()) {
            return CompletableFuture.completedFuture(SyncRunResult.skipped(trigger, "sync_not_configured"));
        }
        if (!hasAuthenticatedSession()) {
            return CompletableFuture.completedFuture(SyncRunResult.skipped(trigger, "sync_not_authenticated"));
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(SyncRunResult.skipped(trigger, "sync_already_running"));
        }
        return AsyncContext.supplyAsync(() -> {
            try {
                return doSync(trigger);
            } finally {
                syncInProgress.set(false);
            }
        }, executor);
    }

    private SyncRunResult doSync(SyncTrigger trigger) {
        String accessToken = ensureValidAccessToken();
        int pullLimit = ConfigManager.getCloudSyncPullLimit();
        AccountLinkStrategy linkStrategy = stateRepository.loadAccountLinkStrategy();
        int attemptedChanges = 0;
        int acceptedChanges = 0;
        int remoteChangeCount = 0;
        long latestKnownChangeId = stateRepository.loadLastKnownChangeId();
        long appliedCursor = stateRepository.loadAppliedCursor();
        boolean hasMoreRemoteChanges = false;
        String finalStatus = "sync_completed";
        List<SyncPayloads.ServerSyncChange> lastRemoteChanges = List.of();

        for (int round = 1; round <= MAX_SYNC_ROUNDS; round++) {
            appliedCursor = stateRepository.loadAppliedCursor();
            SyncRunResult roundResult;
            if (appliedCursor <= 0L) {
                SyncPayloads.SyncBootstrapResponse response = syncApiClient.bootstrap(accessToken, pullLimit);
                roundResult = handleBootstrapResponse(trigger, appliedCursor, response, linkStrategy);
                if (shouldStageLocalOnlyAfterBootstrap(linkStrategy)) {
                    stateRepository.stageLocalOnlyWave1Entities();
                }
            } else {
                stateRepository.stageLocalOnlyWave1Entities();
                List<LocalSyncOutboxEntry> outboxEntries = stateRepository.loadPendingOutbox();
                if (outboxEntries.isEmpty()) {
                    SyncPayloads.SyncPullResponse response = runPull(accessToken, appliedCursor, pullLimit);
                    roundResult = handlePullResponse(trigger, appliedCursor, response);
                } else {
                    roundResult = runPush(trigger, accessToken, appliedCursor, pullLimit, outboxEntries);
                }
            }

            attemptedChanges += Math.max(0, roundResult.attemptedChanges());
            acceptedChanges += Math.max(0, roundResult.acceptedChanges());
            remoteChangeCount += Math.max(0, roundResult.remoteChangeCount());
            latestKnownChangeId = Math.max(latestKnownChangeId, roundResult.latestKnownChangeId());
            appliedCursor = stateRepository.loadAppliedCursor();
            hasMoreRemoteChanges = roundResult.hasMoreRemoteChanges();
            finalStatus = roundResult.status();
            lastRemoteChanges = roundResult.remoteChanges() == null ? List.of() : List.copyOf(roundResult.remoteChanges());
            boolean pendingLocalChanges = hasPendingLocalChanges();

            LOG.info(
                    "cloud.sync.round.completed",
                    "trigger", trigger.name(),
                    "round", round,
                    "status", safe(roundResult.status()),
                    "attemptedChanges", roundResult.attemptedChanges(),
                    "acceptedChanges", roundResult.acceptedChanges(),
                    "remoteChangeCount", roundResult.remoteChangeCount(),
                    "appliedCursor", appliedCursor,
                    "latestKnownChangeId", latestKnownChangeId,
                    "hasMoreRemoteChanges", hasMoreRemoteChanges,
                    "pendingLocalChanges", pendingLocalChanges);

            if (!hasMoreRemoteChanges && !pendingLocalChanges) {
                LOG.info(
                        "cloud.sync.converged",
                        "trigger", trigger.name(),
                        "rounds", round,
                        "attemptedChanges", attemptedChanges,
                        "acceptedChanges", acceptedChanges,
                        "remoteChangeCount", remoteChangeCount,
                        "appliedCursor", appliedCursor,
                        "latestKnownChangeId", latestKnownChangeId);
                return new SyncRunResult(
                        trigger,
                        finalStatus,
                        attemptedChanges,
                        acceptedChanges,
                        remoteChangeCount,
                        appliedCursor,
                        latestKnownChangeId,
                        false,
                        round,
                        lastRemoteChanges);
            }
        }

        LOG.warning(
                "cloud.sync.convergence.deferred",
                "trigger", trigger.name(),
                "maxRounds", MAX_SYNC_ROUNDS,
                "appliedCursor", stateRepository.loadAppliedCursor(),
                "latestKnownChangeId", stateRepository.loadLastKnownChangeId());
        return new SyncRunResult(
                trigger,
                "sync_convergence_deferred",
                attemptedChanges,
                acceptedChanges,
                remoteChangeCount,
                stateRepository.loadAppliedCursor(),
                Math.max(latestKnownChangeId, stateRepository.loadLastKnownChangeId()),
                true,
                MAX_SYNC_ROUNDS,
                lastRemoteChanges);
    }

    private boolean hasPendingLocalChanges() {
        stateRepository.stageLocalOnlyWave1Entities();
        return !stateRepository.loadPendingOutbox().isEmpty();
    }

    private SyncRunResult runPush(
            SyncTrigger trigger,
            String accessToken,
            long appliedCursor,
            int pullLimit,
            List<LocalSyncOutboxEntry> outboxEntries) {
        List<SyncPayloads.ClientSyncChange> changes = new ArrayList<>();
        Map<String, LocalSyncOutboxEntry> outboxByClientChangeId = new HashMap<>();
        for (LocalSyncOutboxEntry entry : outboxEntries) {
            SyncPayloads.ClientSyncChange change = toClientChange(entry);
            if (change == null) {
                stateRepository.markOutboxFailed(entry.id(), "Unsupported outbox payload");
                continue;
            }
            stateRepository.markOutboxInFlight(entry.id());
            changes.add(change);
            outboxByClientChangeId.put(change.client_change_id(), entry);
        }
        changes.sort(Comparator.comparingInt(change -> pushPriority(change.entity_type())));

        if (changes.isEmpty()) {
            return SyncRunResult.skipped(trigger, "sync_no_serializable_outbox_changes");
        }

        SyncPayloads.SyncPushResponse response;
        try {
            response = syncApiClient.push(accessToken, appliedCursor, pullLimit, changes);
        } catch (RuntimeException e) {
            for (LocalSyncOutboxEntry entry : outboxEntries) {
                stateRepository.markOutboxFailed(entry.id(), e.getMessage());
            }
            throw e;
        }

        for (SyncPayloads.PushAcceptedChange accepted : response.accepted()) {
            LocalSyncOutboxEntry entry = outboxByClientChangeId.get(accepted.client_change_id());
            if (entry != null) {
                applyService.acknowledgeAcceptedChange(entry, accepted);
                stateRepository.deleteOutboxEntry(entry.id());
            }
        }
        for (LocalSyncOutboxEntry entry : outboxEntries) {
            if (outboxByClientChangeId.containsKey(entry.id())) {
                boolean acknowledged = response.accepted().stream()
                        .anyMatch(accepted -> entry.id().equals(accepted.client_change_id()));
                if (!acknowledged) {
                    stateRepository.markOutboxFailed(entry.id(), "Server did not acknowledge change");
                }
            }
        }

        stateRepository.saveLastKnownChangeId(response.latest_change_id());
        applyRemoteChanges(response.remote_changes(), response.remote_next_change_id(), false);
        stateRepository.saveLastSuccessfulSyncAt(Instant.now().toString());
        stateRepository.clearLastError();
        return new SyncRunResult(
                trigger,
                "sync_completed",
                changes.size(),
                response.accepted().size(),
                response.remote_changes() == null ? 0 : response.remote_changes().size(),
                response.remote_changes() == null || response.remote_changes().isEmpty()
                        ? response.remote_next_change_id()
                        : appliedCursor,
                response.latest_change_id(),
                response.has_more_remote_changes(),
                1,
                response.remote_changes() == null ? List.of() : List.copyOf(response.remote_changes()));
    }

    private SyncRunResult handleBootstrapResponse(
            SyncTrigger trigger,
            long appliedCursor,
            SyncPayloads.SyncBootstrapResponse response,
            AccountLinkStrategy linkStrategy) {
        stateRepository.saveLastKnownChangeId(response.latest_change_id());
        applyRemoteChanges(
            response.changes(),
            response.next_change_id(),
            linkStrategy == AccountLinkStrategy.UPLOAD_LOCAL
        );
        stateRepository.saveLastSuccessfulSyncAt(Instant.now().toString());
        stateRepository.clearLastError();
        return new SyncRunResult(
                trigger,
                "sync_bootstrap_completed",
                0,
                0,
                response.changes() == null ? 0 : response.changes().size(),
                response.changes() == null || response.changes().isEmpty() ? response.next_change_id() : appliedCursor,
                response.latest_change_id(),
                response.has_more(),
                1,
                response.changes() == null ? List.of() : List.copyOf(response.changes()));
    }

    private SyncRunResult handlePullResponse(
            SyncTrigger trigger,
            long appliedCursor,
            SyncPayloads.SyncPullResponse response) {
        stateRepository.saveLastKnownChangeId(response.latest_change_id());
        applyRemoteChanges(response.changes(), response.next_change_id(), false);
        stateRepository.saveLastSuccessfulSyncAt(Instant.now().toString());
        stateRepository.clearLastError();
        return new SyncRunResult(
                trigger,
                "sync_pull_completed",
                0,
                0,
                response.changes() == null ? 0 : response.changes().size(),
                response.changes() == null || response.changes().isEmpty() ? response.next_change_id() : appliedCursor,
                response.latest_change_id(),
                response.has_more(),
                1,
                response.changes() == null ? List.of() : List.copyOf(response.changes()));
    }

    private SyncPayloads.SyncPullResponse runPull(String accessToken, long appliedCursor, int pullLimit) {
        try {
            return syncApiClient.pull(accessToken, appliedCursor, pullLimit);
        } catch (SyncHttpException e) {
            if (e.statusCode() == 401) {
                accessToken = refreshAccessToken();
                return syncApiClient.pull(accessToken, appliedCursor, pullLimit);
            }
            stateRepository.saveLastError(e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            stateRepository.saveLastError(e.getMessage());
            throw e;
        }
    }

    private String ensureValidAccessToken() {
        AccessTokenState current = accessTokenState;
        if (current != null && !current.isExpiringSoon()) {
            return current.token();
        }
        return refreshAccessToken();
    }

    private String refreshAccessToken() {
        String refreshToken = stateRepository.loadRefreshToken();
        if (refreshToken.isBlank()) {
            throw new IllegalStateException("No persisted refresh token available");
        }
        SyncPayloads.TokenBundleResponse tokenBundle = authClient.refresh(refreshToken);
        LocalDeviceIdentity deviceIdentity = stateRepository.ensureDeviceIdentity();
        stateRepository.saveAuthenticatedSession(tokenBundle, deviceIdentity);
        accessTokenState = AccessTokenState.fromTokenBundle(tokenBundle);
        return accessTokenState.token();
    }

    private SyncPayloads.ClientSyncChange toClientChange(LocalSyncOutboxEntry entry) {
        try {
            SyncPayloads.SyncEntityCode entityType = SyncPayloads.SyncEntityCode.valueOf(entry.entityType().trim().toUpperCase());
            SyncPayloads.SyncOperationCode operation = SyncPayloads.SyncOperationCode.valueOf(entry.operation().trim().toUpperCase());
            String clientChangeId = normalizeUuid(entry.id());
            String entityId = normalizeUuid(entry.entityId());
            JsonNode payload = entry.payloadJson() == null || entry.payloadJson().isBlank()
                    ? payloadMapper.buildPayload(entry)
                    : mapper.readTree(entry.payloadJson());
            return new SyncPayloads.ClientSyncChange(clientChangeId, entityType, operation, entityId, payload);
        } catch (Exception e) {
            LOG.warning(
                    "cloud.sync.outbox.serialization.skipped",
                    "outboxId", entry.id(),
                    "entityType", entry.entityType(),
                    "operation", entry.operation(),
                    "errorType", e.getClass().getSimpleName());
            return null;
        }
    }

    private String normalizeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int pushPriority(SyncPayloads.SyncEntityCode entityType) {
        if (entityType == null) {
            return Integer.MAX_VALUE;
        }
        return switch (entityType) {
            case TASK -> 10;
            case TASK_TEMPLATE -> 20;
            case GOAL -> 30;
            case MOOD_ENTRY -> 40;
            case TIME_SESSION -> 50;
            case TASK_DEPENDENCY -> 60;
            case GOAL_PROGRESS_ENTRY -> 70;
        };
    }

    private SyncSessionSnapshot toSessionSnapshot(SyncPayloads.TokenBundleResponse tokenBundle) {
        return new SyncSessionSnapshot(
                tokenBundle.user() == null ? "" : tokenBundle.user().id(),
                tokenBundle.user() == null ? "" : tokenBundle.user().email(),
                tokenBundle.user() == null ? "" : tokenBundle.user().display_name(),
                tokenBundle.device() == null ? "" : tokenBundle.device().id(),
                tokenBundle.refresh_session_id(),
                true);
    }

    private void applyRemoteChanges(
        List<SyncPayloads.ServerSyncChange> changes,
        long nextCursor,
        boolean skipInitialApply
    ) {
        applyService.applyRemoteChanges(changes, skipInitialApply);
        stateRepository.saveAppliedCursor(nextCursor);
    }

    private boolean shouldStageLocalOnlyAfterBootstrap(AccountLinkStrategy linkStrategy) {
        return linkStrategy == null || linkStrategy != AccountLinkStrategy.REPLACE_LOCAL;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private record AccessTokenState(String token, long expiresAtEpochSeconds) {
        private static AccessTokenState fromTokenBundle(SyncPayloads.TokenBundleResponse tokenBundle) {
            long expiresAt = Instant.now().getEpochSecond() + Math.max(1, tokenBundle.expires_in_seconds());
            return new AccessTokenState(tokenBundle.access_token(), expiresAt);
        }

        private boolean isExpiringSoon() {
            long now = Instant.now().getEpochSecond();
            return now + ACCESS_TOKEN_SAFETY_WINDOW_SECONDS >= expiresAtEpochSeconds;
        }
    }
}
