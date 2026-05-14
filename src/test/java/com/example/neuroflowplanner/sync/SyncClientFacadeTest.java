package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.SyncConfigDefaults;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncClientFacade Tests")
class SyncClientFacadeTest extends IsolatedTestDataFixture {

    @AfterEach
    void resetSingleton() {
        SyncClientFacade.resetForTesting();
    }

    @Test
    @DisplayName("login requires explicit account linking strategy before sync is enabled")
    void loginRequiresExplicitStrategySelection() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        SyncUiSnapshot snapshot = facade.login("sync@example.com", "password").get();

        assertTrue(snapshot.authenticated());
        assertTrue(snapshot.strategyRequired());
        assertEquals(SyncUiStatus.CONFLICT, snapshot.status());
        assertEquals("sync@example.com", snapshot.accountEmail());
        assertNull(snapshot.selectedStrategy());
        assertFalse(snapshot.syncEnabled());

        facade.close();
    }

    @Test
    @DisplayName("applying upload strategy triggers sync and marks profile synced when backend has no remote delta")
    void applyingStrategyTriggersManualSyncAndMarksSynced() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_completed",
                1,
                1,
                0,
                7L,
                7L,
                false,
                1,
                List.of()));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        SyncUiSnapshot snapshot = facade.applyAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL).get();

        assertEquals(SyncUiStatus.SYNCED, snapshot.status());
        assertFalse(snapshot.strategyRequired());
        assertEquals(AccountLinkStrategy.UPLOAD_LOCAL, snapshot.selectedStrategy());
        assertTrue(snapshot.syncEnabled());
        assertEquals("Синхронизация актуальна", snapshot.statusMessage());
        assertTrue(snapshot.diagnosticsMessage().contains("раундов=1"));

        facade.close();
    }

    @Test
    @DisplayName("saving base URL normalizes local wildcard uvicorn address")
    void savingBaseUrlNormalizesLocalWildcardUvicornAddress() {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.saveBaseUrl("https://0.0.0.0:8000/");

        assertEquals("http://127.0.0.1:8000", ConfigManager.getCloudSyncBaseUrl());

        facade.close();
    }

    @Test
    @DisplayName("remote delta after strategy selection is reflected as synced state")
    void remoteDeltaAfterStrategySelectionUpdatesSnapshot() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_pull_completed",
                0,
                0,
                2,
                0L,
                9L,
                false,
                2,
                List.of(
                        new SyncPayloads.ServerSyncChange(
                                8L,
                                SyncPayloads.SyncEntityCode.TASK,
                                "task-1",
                                SyncPayloads.SyncOperationCode.UPSERT,
                                "2026-03-23T00:00:00Z",
                                JsonNodeFactory.instance.objectNode()))));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        SyncUiSnapshot snapshot = facade.applyAccountLinkStrategy(AccountLinkStrategy.MERGE).get();

        assertEquals(SyncUiStatus.SYNCED, snapshot.status());
        assertEquals(0, snapshot.remotePreviewChangeCount());
        assertTrue(snapshot.detailMessage().contains("Применено 2 удалённых изменений"));
        assertTrue(snapshot.diagnosticsMessage().contains("раундов=2"));

        facade.close();
    }

    @Test
    @DisplayName("beta cohort blocks login for accounts outside allowed list")
    void betaCohortBlocksLoginOutsideAllowedList() throws Exception {
        configureSyncDefaults();
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BETA_ALLOWED_EMAILS, "beta@example.com");
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        SyncUiSnapshot snapshot = facade.login("other@example.com", "password").get();

        assertFalse(snapshot.authenticated());
        assertEquals(SyncUiStatus.SIGNED_OUT, snapshot.status());
        assertTrue(snapshot.detailMessage().contains("тестовую выборку"));
        assertTrue(snapshot.rolloutMessage().contains("тестовых аккаунтов"));

        facade.close();
    }

    @Test
    @DisplayName("force bootstrap resets sync progress and reruns sync")
    void forceBootstrapResetsSyncProgressAndRerunsSync() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_completed",
                0,
                0,
                1,
                0L,
                11L,
                false,
                2,
                List.of()));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        facade.applyAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL).get();
        repository.saveAppliedCursor(25L);
        repository.saveLastKnownChangeId(30L);
        repository.saveLastSuccessfulSyncAt("2026-03-23T00:00:00Z");

        SyncUiSnapshot snapshot = facade.forceBootstrapFromCurrentDevice().get();

        assertTrue(coordinator.bootstrapReplayPrepared);
        assertEquals(SyncUiStatus.SYNCED, snapshot.status());
        assertTrue(snapshot.statusMessage().contains("Bootstrap повторно выполнен"));

        facade.close();
    }

    @Test
    @DisplayName("clear diagnostics removes stored sync error from snapshot")
    void clearDiagnosticsRemovesStoredSyncErrorFromSnapshot() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        repository.saveAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL);
        repository.saveLastError("Goal not found");
        facade.initialize();

        SyncUiSnapshot cleared = facade.clearDiagnostics();

        assertEquals("", cleared.lastErrorSummary());
        assertEquals("", cleared.diagnosticsMessage());

        facade.close();
    }

    @Test
    @DisplayName("list linked devices returns coordinator device inventory")
    void listLinkedDevicesReturnsCoordinatorInventory() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setLinkedDevices(List.of(
                new SyncPayloads.DeviceListItemResponse(
                        "device-1",
                        "Main Laptop",
                        "linux",
                        "1.0.0",
                        "2026-03-23T00:00:00Z",
                        "2026-03-23T01:00:00Z",
                        null,
                        1,
                        true),
                new SyncPayloads.DeviceListItemResponse(
                        "device-2",
                        "Office PC",
                        "windows",
                        "1.0.0",
                        "2026-03-22T00:00:00Z",
                        "2026-03-23T02:00:00Z",
                        null,
                        1,
                        false)));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        List<SyncPayloads.DeviceListItemResponse> devices = facade.listLinkedDevices().get();

        assertEquals(2, devices.size());
        assertEquals("device-1", devices.getFirst().id());
        assertTrue(devices.getFirst().is_current_device());

        facade.close();
    }

    @Test
    @DisplayName("revoking current device disconnects local session")
    void revokingCurrentDeviceDisconnectsLocalSession() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        repository.saveAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL);
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");

        SyncUiSnapshot snapshot = facade.revokeLinkedDevice("device-current", true).get();

        assertEquals("device-current", coordinator.lastRevokedDeviceId);
        assertFalse(repository.hasAuthenticatedSession());
        assertFalse(snapshot.authenticated());
        assertEquals(SyncUiStatus.SIGNED_OUT, snapshot.status());
        assertFalse(ConfigManager.isCloudSyncEnabled());

        facade.close();
    }

    @Test
    @DisplayName("disconnect local session clears local auth state without remote call")
    void disconnectLocalSessionClearsLocalAuthState() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        repository.saveAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL);
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");

        SyncUiSnapshot snapshot = facade.disconnectLocalSession();

        assertFalse(repository.hasAuthenticatedSession());
        assertFalse(snapshot.authenticated());
        assertEquals(SyncUiStatus.SIGNED_OUT, snapshot.status());
        assertFalse(ConfigManager.isCloudSyncEnabled());
        assertTrue(snapshot.diagnosticsMessage().contains("локальное отключение сессии"));
        assertEquals("sync@example.com", snapshot.accountEmail());

        facade.close();
    }

    @Test
    @DisplayName("prepare reauthentication keeps remembered account identity")
    void prepareReauthenticationKeepsRememberedAccountIdentity() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        repository.saveAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL);
        facade.disconnectLocalSession();

        SyncUiSnapshot snapshot = facade.prepareReauthentication();

        assertFalse(snapshot.authenticated());
        assertEquals(SyncUiStatus.SIGNED_OUT, snapshot.status());
        assertEquals("sync@example.com", snapshot.accountEmail());
        assertEquals("Готово к повторному входу", snapshot.statusMessage());
        assertTrue(snapshot.detailMessage().contains("Последний аккаунт"));

        facade.close();
    }

    @Test
    @DisplayName("deferred convergence is recorded in health timeline")
    void deferredConvergenceIsRecordedInHealthTimeline() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_convergence_deferred",
                4,
                3,
                2,
                17L,
                19L,
                true,
                50,
                List.of()));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        SyncUiSnapshot snapshot = facade.applyAccountLinkStrategy(AccountLinkStrategy.MERGE).get();
        List<SyncHealthEvent> events = facade.recentHealthEvents();

        assertEquals(SyncUiStatus.SYNCED, snapshot.status());
        assertFalse(events.isEmpty());
        assertTrue(events.getFirst().deferred());
        assertTrue(events.getFirst().detail().contains("convergence-раундов"));
        assertTrue(facade.buildHealthTimelineSummary().contains("Сходимость sync отложена"));

        facade.close();
    }

    @Test
    @DisplayName("health timeline is restored after facade relaunch")
    void healthTimelineIsRestoredAfterFacadeRelaunch() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_convergence_deferred",
                2,
                2,
                1,
                8L,
                9L,
                true,
                50,
                List.of()));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        facade.applyAccountLinkStrategy(AccountLinkStrategy.MERGE).get();
        String persistedSummary = facade.buildHealthTimelineSummary();
        facade.close();

        SyncClientFacade relaunched = new SyncClientFacade(repository, new TestSyncCoordinator(repository));

        assertFalse(relaunched.recentHealthEvents().isEmpty());
        assertTrue(relaunched.recentHealthEvents().getFirst().deferred());
        assertTrue(relaunched.buildHealthTimelineSummary().contains("Сходимость sync отложена"));
        assertEquals(persistedSummary, relaunched.buildHealthTimelineSummary());

        relaunched.close();
    }

    @Test
    @DisplayName("diagnostics bundle includes health timeline and linked device inventory")
    void diagnosticsBundleIncludesHealthTimelineAndLinkedDeviceInventory() throws Exception {
        configureSyncDefaults();
        SyncStateRepository repository = new SyncStateRepository(DatabaseManager.getInstance());
        repository.clearAuthenticatedSession();
        repository.saveAccountLinkStrategy(null);
        TestSyncCoordinator coordinator = new TestSyncCoordinator(repository);
        coordinator.setNextSyncResult(new SyncRunResult(
                SyncTrigger.MANUAL,
                "sync_completed",
                3,
                3,
                1,
                14L,
                15L,
                false,
                2,
                List.of()));
        coordinator.setLinkedDevices(List.of(
                new SyncPayloads.DeviceListItemResponse(
                        "device-1",
                        "Main Laptop",
                        "linux",
                        "1.0.0",
                        "2026-03-23T00:00:00Z",
                        "2026-03-23T01:00:00Z",
                        null,
                        1,
                        true)));
        SyncClientFacade facade = new SyncClientFacade(repository, coordinator);

        facade.login("sync@example.com", "password").get();
        facade.applyAccountLinkStrategy(AccountLinkStrategy.UPLOAD_LOCAL).get();

        String bundle = facade.buildDiagnosticsBundle().get();

        assertTrue(bundle.contains("Cloud Sync Diagnostics Bundle"));
        assertTrue(bundle.contains("=== Runtime Debug Summary ==="));
        assertTrue(bundle.contains("Sync Health Timeline"));
        assertTrue(bundle.contains("=== Device Inventory ==="));
        assertTrue(bundle.contains("inventoryStatus=Live inventory устройств получен с backend."));
        assertTrue(bundle.contains("Main Laptop"));
        assertTrue(bundle.contains("accountEmail=sync@example.com"));

        facade.close();
    }

    private void configureSyncDefaults() {
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL, "http://127.0.0.1:8000");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BETA_ENABLED, "true");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_BETA_ALLOWED_EMAILS, "");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "false");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_STARTUP_ENABLED, "false");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_RECONNECT_ENABLED, "false");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_PERIODIC_INTERVAL_SECONDS, "300");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_HEALTHCHECK_INTERVAL_SECONDS, "300");
    }

    private static final class TestSyncCoordinator extends SyncCoordinator {
        private final SyncStateRepository repository;
        private volatile SyncRunResult nextSyncResult;
        private volatile boolean bootstrapReplayPrepared;
        private volatile List<SyncPayloads.DeviceListItemResponse> linkedDevices = List.of();
        private volatile String lastRevokedDeviceId;

        private TestSyncCoordinator(SyncStateRepository repository) {
            super(repository, new AuthClient(), new SyncApiClient());
            this.repository = repository;
        }

        private void setNextSyncResult(SyncRunResult nextSyncResult) {
            this.nextSyncResult = nextSyncResult;
        }

        private void setLinkedDevices(List<SyncPayloads.DeviceListItemResponse> linkedDevices) {
            this.linkedDevices = linkedDevices == null ? List.of() : List.copyOf(linkedDevices);
        }

        @Override
        public boolean isSyncConfigured() {
            return true;
        }

        @Override
        public boolean hasAuthenticatedSession() {
            return repository.hasAuthenticatedSession();
        }

        @Override
        public CompletableFuture<SyncSessionSnapshot> login(String email, String password) {
            LocalDeviceIdentity identity = repository.ensureDeviceIdentity();
            SyncPayloads.TokenBundleResponse tokenBundle = new SyncPayloads.TokenBundleResponse(
                    "access-token",
                    "refresh-token",
                    "bearer",
                    3600,
                    new SyncPayloads.AuthenticatedUserResponse(
                            "user-1",
                            email,
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
                    "refresh-session-1");
            repository.saveAuthenticatedSession(tokenBundle, identity);
            return CompletableFuture.completedFuture(new SyncSessionSnapshot(
                    "user-1",
                    email,
                    "Sync User",
                    identity.deviceId(),
                    "refresh-session-1",
                    true));
        }

        @Override
        public CompletableFuture<SyncSessionSnapshot> register(String email, String password, String displayName) {
            return login(email, password);
        }

        @Override
        public CompletableFuture<Void> logout() {
            repository.clearAuthenticatedSession();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SyncRunResult> syncNow(SyncTrigger trigger) {
            SyncRunResult result = nextSyncResult == null
                    ? SyncRunResult.skipped(trigger, "sync_disabled")
                    : nextSyncResult;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Void> prepareBootstrapReplay() {
            bootstrapReplayPrepared = true;
            repository.resetSyncProgressForBootstrap();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SyncPayloads.DeviceListResponse> listDevices() {
            return CompletableFuture.completedFuture(new SyncPayloads.DeviceListResponse(linkedDevices));
        }

        @Override
        public CompletableFuture<SyncPayloads.DeviceRevokeResponse> revokeDevice(String deviceId) {
            lastRevokedDeviceId = deviceId;
            return CompletableFuture.completedFuture(new SyncPayloads.DeviceRevokeResponse(
                    deviceId,
                    "2026-03-23T00:00:00Z",
                    1));
        }
    }
}
