package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.LocalAccountLink;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SyncStateRepository {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SyncStateRepository.class);

    private static final String KEY_APPLIED_CURSOR = "cloud.sync.applied_cursor";
    private static final String KEY_LAST_KNOWN_CHANGE_ID = "cloud.sync.last_known_change_id";
    private static final String KEY_REFRESH_SESSION_ID = "cloud.sync.refresh_session_id";
    private static final String KEY_LAST_SUCCESS_AT = "cloud.sync.last_success_at";
    private static final String KEY_LAST_ERROR = "cloud.sync.last_error";
    private static final String KEY_LINK_STRATEGY = "cloud.sync.link_strategy";
    private static final String KEY_LAST_LINKED_USER_ID = "cloud.sync.last_linked_user_id";
    private static final String KEY_LAST_LINKED_EMAIL = "cloud.sync.last_linked_email";
    private static final String KEY_LAST_LINKED_DISPLAY_NAME = "cloud.sync.last_linked_display_name";
    private static final String KEY_HEALTH_EVENTS = "cloud.sync.health_events";
    private static final TypeReference<List<SyncHealthEvent>> HEALTH_EVENTS_TYPE = new TypeReference<>() {
    };

    private final DatabaseManager databaseManager;
    private final ObjectMapper mapper;

    public SyncStateRepository() {
        this(DatabaseManager.getInstance());
    }

    public SyncStateRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        this.mapper = AiObjectMapperFactory.createMapper(false);
    }

    public synchronized LocalDeviceIdentity ensureDeviceIdentity() {
        LocalDeviceIdentity existing = databaseManager.loadDeviceIdentity();
        if (existing != null && hasText(existing.deviceId())) {
            return existing;
        }

        String now = Instant.now().toString();
        LocalDeviceIdentity created = new LocalDeviceIdentity(
                UUID.randomUUID().toString(),
                resolveDefaultDeviceLabel(),
                resolvePlatform(),
                resolveAppVersion(),
                now,
                now);
        databaseManager.saveDeviceIdentity(created);
        return created;
    }

    public synchronized LocalAccountLink loadAccountLink() {
        return databaseManager.loadAccountLink();
    }

    public synchronized boolean hasAuthenticatedSession() {
        LocalAccountLink accountLink = databaseManager.loadAccountLink();
        return accountLink != null
                && hasText(accountLink.userId())
                && hasText(loadRefreshToken());
    }

    public synchronized void saveAuthenticatedSession(SyncPayloads.TokenBundleResponse tokenBundle, LocalDeviceIdentity localDevice) {
        if (tokenBundle == null || tokenBundle.user() == null) {
            throw new IllegalArgumentException("token bundle is required");
        }
        String now = Instant.now().toString();
        String nextUserId = emptyIfNull(tokenBundle.user().id());
        LocalAccountLink existing = databaseManager.loadAccountLink();
        String previousLinkedUserId = firstNonBlank(
                existing == null ? "" : existing.userId(),
                databaseManager.loadSyncState(KEY_LAST_LINKED_USER_ID));
        boolean switchingAccount = hasText(previousLinkedUserId)
                && hasText(nextUserId)
                && !previousLinkedUserId.equals(nextUserId);
        if (switchingAccount) {
            databaseManager.resetLocalSyncStateForAccountRelink();
            clearSyncRuntimeState();
        }
        String linkedAt = switchingAccount
                ? now
                : existing != null && hasText(existing.linkedAt()) ? existing.linkedAt() : now;
        databaseManager.saveAccountLink(new LocalAccountLink(
                nextUserId,
                emptyIfNull(tokenBundle.user().email()),
                emptyIfNull(tokenBundle.user().display_name()),
                "LINKED",
                linkedAt,
                now));

        LocalDeviceIdentity persistedDevice = localDevice == null
                ? ensureDeviceIdentity()
                : new LocalDeviceIdentity(
                        emptyIfNull(tokenBundle.device() == null ? localDevice.deviceId() : tokenBundle.device().id()),
                        emptyIfNull(localDevice.deviceLabel()),
                        emptyIfNull(localDevice.platform()),
                        emptyIfNull(localDevice.appVersion()),
                        hasText(localDevice.createdAt()) ? localDevice.createdAt() : now,
                        now);
        databaseManager.saveDeviceIdentity(persistedDevice);
        saveRefreshToken(tokenBundle.refresh_token());
        saveRefreshSessionId(tokenBundle.refresh_session_id());
        databaseManager.saveSyncState(KEY_LAST_LINKED_USER_ID, nextUserId);
        databaseManager.saveSyncState(KEY_LAST_LINKED_EMAIL, emptyIfNull(tokenBundle.user().email()));
        databaseManager.saveSyncState(KEY_LAST_LINKED_DISPLAY_NAME, emptyIfNull(tokenBundle.user().display_name()));
        if (switchingAccount) {
            clearHealthEvents();
        }
        clearLastError();
    }

    public synchronized void clearAuthenticatedSession() {
        databaseManager.clearAccountLink();
        clearSyncRuntimeState();
    }

    public synchronized void resetSyncProgressForBootstrap() {
        databaseManager.resetInFlightSyncOutboxEntries("Reset by manual bootstrap recovery");
        databaseManager.deleteSyncState(KEY_APPLIED_CURSOR);
        databaseManager.deleteSyncState(KEY_LAST_KNOWN_CHANGE_ID);
        databaseManager.deleteSyncState(KEY_LAST_SUCCESS_AT);
        databaseManager.deleteSyncState(KEY_LAST_ERROR);
    }

    public synchronized long loadAppliedCursor() {
        return parseLong(databaseManager.loadSyncState(KEY_APPLIED_CURSOR), 0L);
    }

    public synchronized void saveAppliedCursor(long cursor) {
        databaseManager.saveSyncState(KEY_APPLIED_CURSOR, Long.toString(Math.max(0L, cursor)));
    }

    public synchronized long loadLastKnownChangeId() {
        return parseLong(databaseManager.loadSyncState(KEY_LAST_KNOWN_CHANGE_ID), 0L);
    }

    public synchronized void saveLastKnownChangeId(long changeId) {
        databaseManager.saveSyncState(KEY_LAST_KNOWN_CHANGE_ID, Long.toString(Math.max(0L, changeId)));
    }

    public synchronized String loadRefreshSessionId() {
        return emptyIfNull(databaseManager.loadSyncState(KEY_REFRESH_SESSION_ID));
    }

    public synchronized void saveRefreshSessionId(String refreshSessionId) {
        databaseManager.saveSyncState(KEY_REFRESH_SESSION_ID, emptyIfNull(refreshSessionId));
    }

    public synchronized void saveLastSuccessfulSyncAt(String timestamp) {
        databaseManager.saveSyncState(KEY_LAST_SUCCESS_AT, emptyIfNull(timestamp));
    }

    public synchronized String loadLastSuccessfulSyncAt() {
        return emptyIfNull(databaseManager.loadSyncState(KEY_LAST_SUCCESS_AT));
    }

    public synchronized void saveLastError(String errorMessage) {
        databaseManager.saveSyncState(KEY_LAST_ERROR, emptyIfNull(errorMessage));
    }

    public synchronized String loadLastError() {
        return emptyIfNull(databaseManager.loadSyncState(KEY_LAST_ERROR));
    }

    public synchronized void clearLastError() {
        databaseManager.deleteSyncState(KEY_LAST_ERROR);
    }

    public synchronized AccountLinkStrategy loadAccountLinkStrategy() {
        return AccountLinkStrategy.fromPersistedValue(databaseManager.loadSyncState(KEY_LINK_STRATEGY));
    }

    public synchronized void saveAccountLinkStrategy(AccountLinkStrategy strategy) {
        if (strategy == null) {
            databaseManager.deleteSyncState(KEY_LINK_STRATEGY);
            return;
        }
        databaseManager.saveSyncState(KEY_LINK_STRATEGY, strategy.persistedValue());
    }

    public synchronized String loadRememberedAccountEmail() {
        return emptyIfNull(databaseManager.loadSyncState(KEY_LAST_LINKED_EMAIL));
    }

    public synchronized String loadRememberedDisplayName() {
        return emptyIfNull(databaseManager.loadSyncState(KEY_LAST_LINKED_DISPLAY_NAME));
    }

    public synchronized List<SyncHealthEvent> loadHealthEvents() {
        String raw = emptyIfNull(databaseManager.loadSyncState(KEY_HEALTH_EVENTS));
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            List<SyncHealthEvent> events = mapper.readValue(raw, HEALTH_EVENTS_TYPE);
            return events == null ? List.of() : List.copyOf(events);
        } catch (Exception e) {
            LOG.warning("cloud.sync.health_events.load.failed", "errorType", e.getClass().getSimpleName());
            return List.of();
        }
    }

    public synchronized void saveHealthEvents(List<SyncHealthEvent> events) {
        if (events == null || events.isEmpty()) {
            clearHealthEvents();
            return;
        }
        try {
            databaseManager.saveSyncState(KEY_HEALTH_EVENTS, mapper.writeValueAsString(events));
        } catch (Exception e) {
            LOG.warning("cloud.sync.health_events.save.failed", "errorType", e.getClass().getSimpleName());
        }
    }

    public synchronized void clearHealthEvents() {
        databaseManager.deleteSyncState(KEY_HEALTH_EVENTS);
    }

    public synchronized String loadRefreshToken() {
        return emptyIfNull(ConfigManager.getProperty(ConfigManager.CONFIG_CLOUD_SYNC_REFRESH_TOKEN));
    }

    public synchronized void saveRefreshToken(String refreshToken) {
        ConfigManager.setProperty(ConfigManager.CONFIG_CLOUD_SYNC_REFRESH_TOKEN, emptyIfNull(refreshToken));
    }

    public synchronized void clearRefreshToken() {
        ConfigManager.setProperty(ConfigManager.CONFIG_CLOUD_SYNC_REFRESH_TOKEN, "");
    }

    public synchronized LocalDeviceIdentity loadDeviceIdentity() {
        return databaseManager.loadDeviceIdentity();
    }

    public List<LocalSyncOutboxEntry> loadPendingOutbox() {
        return databaseManager.loadPendingSyncOutbox(ConfigManager.getCloudSyncOutboxBatchSize());
    }

    public void markOutboxInFlight(String outboxId) {
        databaseManager.markSyncOutboxInFlight(outboxId);
    }

    public void markOutboxFailed(String outboxId, String errorMessage) {
        databaseManager.markSyncOutboxFailed(outboxId, errorMessage);
    }

    public void deleteOutboxEntry(String outboxId) {
        databaseManager.deleteSyncOutboxEntry(outboxId);
    }

    public int stageLocalOnlyWave1Entities() {
        return databaseManager.stageLocalOnlyWave1Entities();
    }

    public LocalSyncProfileSummary loadLocalProfileSummary() {
        return databaseManager.runInTransaction("loadLocalSyncProfileSummary", connection -> new LocalSyncProfileSummary(
                countActiveRows(connection, "tasks"),
                countActiveRows(connection, "task_dependencies"),
                countActiveRows(connection, "time_sessions"),
                countActiveRows(connection, "task_templates"),
                countActiveRows(connection, "goals"),
                countActiveRows(connection, "mood_entries"),
                countRows(connection, "sync_outbox")));
    }

    private long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOG.warning("cloud.sync.state.invalid_long", "value", value, "defaultValue", defaultValue);
            return defaultValue;
        }
    }

    private String resolveDefaultDeviceLabel() {
        String host = tryResolveHostName();
        if (hasText(host)) {
            return host;
        }
        String user = System.getProperty("user.name", "").trim();
        if (!user.isEmpty()) {
            return "NeuroFlow " + user;
        }
        return "NeuroFlow Desktop";
    }

    private String resolvePlatform() {
        return System.getProperty("os.name", "unknown").trim().toLowerCase(Locale.ROOT);
    }

    private String resolveAppVersion() {
        Package appPackage = SyncStateRepository.class.getPackage();
        if (appPackage != null && hasText(appPackage.getImplementationVersion())) {
            return appPackage.getImplementationVersion().trim();
        }
        return "dev";
    }

    private String tryResolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    private void clearSyncRuntimeState() {
        clearRefreshToken();
        databaseManager.deleteSyncState(KEY_REFRESH_SESSION_ID);
        databaseManager.deleteSyncState(KEY_APPLIED_CURSOR);
        databaseManager.deleteSyncState(KEY_LAST_KNOWN_CHANGE_ID);
        databaseManager.deleteSyncState(KEY_LAST_SUCCESS_AT);
        databaseManager.deleteSyncState(KEY_LAST_ERROR);
        databaseManager.deleteSyncState(KEY_LINK_STRATEGY);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (hasText(primary)) {
            return primary.trim();
        }
        return emptyIfNull(fallback);
    }

    private int countActiveRows(java.sql.Connection connection, String tableName) throws java.sql.SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE deleted_at IS NULL OR deleted_at = ''";
        try (java.sql.PreparedStatement statement = connection.prepareStatement(sql);
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private int countRows(java.sql.Connection connection, String tableName) throws java.sql.SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (java.sql.PreparedStatement statement = connection.prepareStatement(sql);
             java.sql.ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }
}
