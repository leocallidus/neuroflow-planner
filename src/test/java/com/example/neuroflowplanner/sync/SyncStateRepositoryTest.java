package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.LocalAccountLink;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.model.LocalSyncOutboxEntry;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.DataPathManager;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncStateRepository Tests")
class SyncStateRepositoryTest extends IsolatedTestDataFixture {

    @Test
    @DisplayName("device identity and refresh token are persisted without leaking secret into config file")
    void ensureDeviceIdentityAndRefreshTokenStorage() throws Exception {
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());

        LocalDeviceIdentity identity = repository.ensureDeviceIdentity();
        assertNotNull(identity);
        assertNotNull(identity.deviceId());
        assertFalse(identity.deviceId().isBlank());
        assertNotNull(identity.deviceLabel());
        assertFalse(identity.deviceLabel().isBlank());

        repository.saveRefreshToken("refresh-secret-1234567890");
        assertEquals("refresh-secret-1234567890", repository.loadRefreshToken());

        String configContent = Files.readString(DataPathManager.getConfigPath());
        assertFalse(configContent.contains("refresh-secret-1234567890"));
    }

    @Test
    @DisplayName("authenticated session writes account link and sync cursors")
    void saveAuthenticatedSessionPersistsState() {
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        LocalDeviceIdentity identity = repository.ensureDeviceIdentity();

        SyncPayloads.TokenBundleResponse tokenBundle = new SyncPayloads.TokenBundleResponse(
                "access-token",
                "refresh-token",
                "bearer",
                3600,
                new SyncPayloads.AuthenticatedUserResponse(
                        "8b2e3f9a-5d5a-4a6c-8f31-f5fdf7f3d5a1",
                        "sync@example.com",
                        "Sync User",
                        true),
                new SyncPayloads.DeviceSessionResponse(
                        identity.deviceId(),
                        identity.deviceLabel(),
                        identity.platform(),
                        identity.appVersion(),
                        "2026-03-23T00:00:00Z",
                        null,
                        null),
                "2f3930f6-77d3-4be3-a437-f7906a793db8");

        repository.saveAuthenticatedSession(tokenBundle, identity);
        repository.saveAppliedCursor(12L);
        repository.saveLastKnownChangeId(15L);

        LocalAccountLink accountLink = repository.loadAccountLink();
        assertNotNull(accountLink);
        assertEquals("sync@example.com", accountLink.email());
        assertEquals("LINKED", accountLink.status());
        assertEquals("refresh-token", repository.loadRefreshToken());
        assertEquals("2f3930f6-77d3-4be3-a437-f7906a793db8", repository.loadRefreshSessionId());
        assertEquals(12L, repository.loadAppliedCursor());
        assertEquals(15L, repository.loadLastKnownChangeId());
    }

    @Test
    @DisplayName("switching to another account resets local sync metadata and prepares relink upload")
    void switchingToAnotherAccountResetsLocalSyncMetadata() {
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        SyncStateRepository repository = new SyncStateRepository(databaseManager);
        LocalDeviceIdentity identity = repository.ensureDeviceIdentity();

        Task task = new Task(
                "account-switch-task-" + java.util.UUID.randomUUID(),
                "Local task",
                "Should be uploaded to the next account",
                LocalDate.now().plusDays(3),
                3);
        databaseManager.applySyncedTask(task, "2026-03-23T00:00:00Z", 11L);

        repository.saveAuthenticatedSession(tokenBundle(identity, "user-a", "a@example.com"), identity);
        repository.saveAppliedCursor(12L);
        repository.saveLastKnownChangeId(12L);
        repository.saveAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL);
        repository.clearAuthenticatedSession();

        repository.saveAuthenticatedSession(tokenBundle(identity, "user-b", "b@example.com"), identity);

        assertEquals(0L, repository.loadAppliedCursor());
        assertEquals(0L, repository.loadLastKnownChangeId());
        assertNull(repository.loadAccountLinkStrategy());

        repository.stageLocalOnlyWave1Entities();
        List<LocalSyncOutboxEntry> outboxEntries = repository.loadPendingOutbox();
        assertTrue(outboxEntries.stream().anyMatch(entry ->
                "TASK".equals(entry.entityType())
                        && task.getId().equals(entry.entityId())
                        && "UPSERT".equals(entry.operation())));
    }

    @Test
    @DisplayName("reset sync progress for bootstrap clears cursors and recovers in-flight outbox")
    void resetSyncProgressForBootstrapClearsCursorsAndRecoversInFlightOutbox() {
        DatabaseManager databaseManager = DatabaseManager.getInstance();
        SyncStateRepository repository = new SyncStateRepository(databaseManager);

        repository.saveAppliedCursor(41L);
        repository.saveLastKnownChangeId(47L);
        repository.saveLastSuccessfulSyncAt("2026-03-23T00:00:00Z");
        repository.saveLastError("Unknown goal period type");
        databaseManager.enqueueSyncChange("TASK", "task-bootstrap-test", "UPSERT", "{\"id\":\"task-bootstrap-test\"}");
        List<LocalSyncOutboxEntry> initialEntries = repository.loadPendingOutbox();
        assertEquals(1, initialEntries.size());
        databaseManager.markSyncOutboxInFlight(initialEntries.get(0).id());

        repository.resetSyncProgressForBootstrap();

        assertEquals(0L, repository.loadAppliedCursor());
        assertEquals(0L, repository.loadLastKnownChangeId());
        assertEquals("", repository.loadLastSuccessfulSyncAt());
        assertEquals("", repository.loadLastError());

        List<LocalSyncOutboxEntry> recoveredEntries = repository.loadPendingOutbox();
        assertEquals(1, recoveredEntries.size());
        assertEquals("FAILED", recoveredEntries.get(0).status());
    }

    @Test
    @DisplayName("health events are persisted and restored from sync state")
    void healthEventsArePersistedAndRestored() {
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        List<SyncHealthEvent> events = List.of(
                new SyncHealthEvent(
                        "2026-03-23T00:00:00Z",
                        "manual",
                        "Sync-цикл завершён",
                        "Сходимость достигнута.",
                        false,
                        false),
                new SyncHealthEvent(
                        "2026-03-23T00:01:00Z",
                        "manual",
                        "Сходимость sync отложена",
                        "Достигнут лимит convergence-раундов.",
                        false,
                        true));

        repository.saveHealthEvents(events);
        List<SyncHealthEvent> restored = repository.loadHealthEvents();

        assertEquals(2, restored.size());
        assertEquals("Sync-цикл завершён", restored.get(0).title());
        assertTrue(restored.get(1).deferred());
    }

    private SyncPayloads.TokenBundleResponse tokenBundle(
            LocalDeviceIdentity identity,
            String userId,
            String email) {
        return new SyncPayloads.TokenBundleResponse(
                "access-token-" + userId,
                "refresh-token-" + userId,
                "bearer",
                3600,
                new SyncPayloads.AuthenticatedUserResponse(
                        userId,
                        email,
                        "Sync User " + userId,
                        true),
                new SyncPayloads.DeviceSessionResponse(
                        identity.deviceId(),
                        identity.deviceLabel(),
                        identity.platform(),
                        identity.appVersion(),
                        "2026-03-23T00:00:00Z",
                        null,
                        null),
                "refresh-session-" + userId);
    }
}
