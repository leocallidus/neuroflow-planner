package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.model.LocalAccountLink;
import com.example.neuroflowplanner.model.LocalDeviceIdentity;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.CloudSyncUrlSupport;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.example.neuroflowplanner.util.SyncConfigDefaults;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class SyncClientFacade implements AutoCloseable {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SyncClientFacade.class);
    private static final int MAX_HEALTH_EVENTS = 12;

    private static volatile SyncClientFacade instance;

    private final SyncStateRepository stateRepository;
    private final SyncCoordinator coordinator;
    private final SyncScheduler scheduler;
    private final CopyOnWriteArrayList<Consumer<SyncUiSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SyncHealthEvent> healthEvents = new CopyOnWriteArrayList<>();
    private final AtomicReference<SyncUiSnapshot> snapshot;
    private final AtomicReference<String> diagnosticsMessage = new AtomicReference<>("");
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);

    public static SyncClientFacade getInstance() {
        if (instance == null) {
            synchronized (SyncClientFacade.class) {
                if (instance == null) {
                    SyncClientFacade created = new SyncClientFacade();
                    created.initialize();
                    instance = created;
                }
            }
        }
        return instance;
    }

    public static synchronized void resetForTesting() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    public SyncClientFacade() {
        this(new SyncStateRepository());
    }

    SyncClientFacade(SyncStateRepository stateRepository) {
        this(
                stateRepository,
                new SyncCoordinator(stateRepository, new AuthClient(), new SyncApiClient()));
    }

    SyncClientFacade(SyncStateRepository stateRepository, SyncCoordinator coordinator) {
        this.stateRepository = Objects.requireNonNull(stateRepository, "stateRepository");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.healthEvents.addAll(this.stateRepository.loadHealthEvents());
        this.snapshot = new AtomicReference<>(SyncUiSnapshot.initial(ConfigManager.getCloudSyncBaseUrl()));
        this.scheduler = new SyncScheduler(
                coordinator,
                ConfigManager.isCloudSyncStartupEnabled(),
                ConfigManager.isCloudSyncReconnectEnabled(),
                ConfigManager.getCloudSyncPeriodicInterval(),
                ConfigManager.getCloudSyncHealthcheckInterval(),
                this::handleSchedulerResult);
    }

    public void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        if (!isBetaRolloutEnabled()) {
            publish(buildSnapshot(
                    SyncUiStatus.SIGNED_OUT,
                    "Бета-синхронизация отключена",
                    "Облачная синхронизация выключена скрытым флагом `cloud.sync.beta.enabled`.",
                    null,
                    null));
            return;
        }
        publish(buildSnapshot(resolveInitialStatus(), initialStatusMessage(), initialDetailMessage(), null, null));
        startSchedulerIfEligible();
    }

    public SyncUiSnapshot snapshot() {
        initialize();
        return snapshot.get();
    }

    public AutoCloseable addListener(Consumer<SyncUiSnapshot> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        initialize();
        listeners.add(listener);
        listener.accept(snapshot.get());
        return () -> listeners.remove(listener);
    }

    public CompletableFuture<SyncUiSnapshot> register(String email, String password, String displayName) {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!isEmailAllowedForBeta(email)) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.SIGNED_OUT,
                    "Аккаунт не включён в beta rollout",
                    buildBetaCohortDeniedMessage(email),
                    buildBetaCohortDeniedMessage(email),
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Создаю облачный аккаунт",
                "Регистрация выполняется в фоновом режиме.",
                null,
                null));
        return coordinator.register(email, password, displayName)
                .thenCompose(AsyncContext.withMdcFunction(this::handleAuthenticatedSession))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "register")));
    }

    public CompletableFuture<SyncUiSnapshot> login(String email, String password) {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!isEmailAllowedForBeta(email)) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.SIGNED_OUT,
                    "Аккаунт не включён в beta rollout",
                    buildBetaCohortDeniedMessage(email),
                    buildBetaCohortDeniedMessage(email),
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Подключаю аккаунт",
                "Выполняется вход в облачную учётную запись.",
                null,
                null));
        return coordinator.login(email, password)
                .thenCompose(AsyncContext.withMdcFunction(this::handleAuthenticatedSession))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "login")));
    }

    public CompletableFuture<SyncUiSnapshot> logout() {
        initialize();
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Отключаю аккаунт",
                "Локальный профиль отвязывается от облачной сессии.",
                null,
                null));
        return coordinator.logout()
                .thenApply(AsyncContext.withMdcFunction(ignored -> {
                    ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "false");
                    schedulerStarted.set(false);
                    SyncUiSnapshot next = buildSnapshot(
                            SyncUiStatus.SIGNED_OUT,
                            "Не подключено",
                            "Войдите в аккаунт, чтобы включить облачную синхронизацию.",
                            null,
                            0);
                    publish(next);
                    return next;
                }))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "logout")));
    }

    public CompletableFuture<SyncUiSnapshot> applyAccountLinkStrategy(AccountLinkStrategy strategy) {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!isLinkedAccountAllowedForBeta()) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Текущий аккаунт вне тестовой выборки",
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        if (strategy == null) {
            return CompletableFuture.completedFuture(snapshot());
        }
        stateRepository.saveAccountLinkStrategy(strategy);
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Применяю стратегию связывания",
                "Выбрана стратегия: " + strategy.displayLabel(),
                null,
                null));
        startSchedulerIfEligible();
        return triggerManualSync();
    }

    public CompletableFuture<SyncUiSnapshot> triggerManualSync() {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!isLinkedAccountAllowedForBeta()) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Текущий аккаунт вне тестовой выборки",
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Синхронизация выполняется",
                "Обмениваюсь изменениями с облаком в фоновом режиме.",
                null,
                null));
        return coordinator.syncNow(SyncTrigger.MANUAL)
                .thenApply(AsyncContext.withMdcFunction(result -> handleSyncResult(result, null)))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "manualSync")));
    }

    public CompletableFuture<SyncUiSnapshot> forceBootstrapFromCurrentDevice() {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.SIGNED_OUT,
                    "Сначала войдите в аккаунт",
                    "Повторный bootstrap доступен только после входа в облачный аккаунт.",
                    null,
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        if (!isLinkedAccountAllowedForBeta()) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Текущий аккаунт вне тестовой выборки",
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                            ? ""
                            : stateRepository.loadAccountLink().email())),
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        if (stateRepository.loadAccountLinkStrategy() == null) {
            SyncUiSnapshot blocked = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Сначала выберите стратегию связывания",
                    buildStrategyRequiredMessage(),
                    null,
                    null);
            publish(blocked);
            return CompletableFuture.completedFuture(blocked);
        }
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                "Перезапускаю bootstrap",
                "Сбрасываю локальный sync-курсор и повторно сверяю устройство с облаком.",
                null,
                null));
        return coordinator.prepareBootstrapReplay()
                .thenCompose(AsyncContext.withMdcFunction(ignored -> coordinator.syncNow(SyncTrigger.MANUAL)))
                .thenApply(AsyncContext.withMdcFunction(result -> handleSyncResult(result, "Bootstrap повторно выполнен")))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "forceBootstrap")));
    }

    public CompletableFuture<List<SyncPayloads.DeviceListItemResponse>> listLinkedDevices() {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(List.of());
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Cloud sync session is not linked"));
        }
        return coordinator.listDevices()
                .thenApply(response -> response == null || response.devices() == null
                        ? List.<SyncPayloads.DeviceListItemResponse>of()
                        : List.copyOf(response.devices()))
                .exceptionallyCompose(error -> {
                    Throwable cause = AsyncContext.unwrap(error);
                    if (isInvalidAccessFailure(cause)) {
                        SyncUiSnapshot snapshot = disconnectLocalSession(
                                "Сессия устройства больше недействительна",
                                userFacingErrorMessage(cause));
                        return CompletableFuture.<List<SyncPayloads.DeviceListItemResponse>>failedFuture(
                                new IllegalStateException(snapshot.detailMessage()));
                    }
                    return CompletableFuture.<List<SyncPayloads.DeviceListItemResponse>>failedFuture(cause);
                });
    }

    public CompletableFuture<SyncUiSnapshot> revokeLinkedDevice(String deviceId, boolean currentDevice) {
        initialize();
        if (!isBetaRolloutEnabled()) {
            return CompletableFuture.completedFuture(betaDisabledSnapshot());
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            return CompletableFuture.completedFuture(buildSnapshot(
                    SyncUiStatus.SIGNED_OUT,
                    "Сначала войдите в аккаунт",
                    "Отзыв устройства доступен только после входа в облачный аккаунт.",
                    null,
                    null));
        }
        publish(buildSnapshot(
                SyncUiStatus.SYNCING,
                currentDevice ? "Отзываю текущее устройство" : "Отзываю устройство",
                "Обновляю cloud-session lifecycle для выбранного устройства.",
                null,
                null));
        return coordinator.revokeDevice(deviceId)
                .thenApply(AsyncContext.withMdcFunction(response -> {
                    diagnosticsMessage.set("Последний цикл: операция=отзыв устройства, результат=успех, deviceId="
                            + safe(response == null ? deviceId : response.id()) + ".");
                    recordHealthEvent(
                            "device",
                            currentDevice ? "Текущее устройство отозвано" : "Устройство отозвано",
                            currentDevice
                                    ? "Локальная cloud-сессия завершена из-за revoke текущего устройства."
                                    : "Выбранное устройство отозвано, его refresh-сессии закрыты на backend.",
                            false,
                            false);
                    if (currentDevice) {
                        return disconnectLocalSession(
                                "Текущее устройство отозвано",
                                "Это устройство отключено от облачного аккаунта. Войдите заново для повторной привязки.");
                    }
                    SyncUiSnapshot updated = buildSnapshot(
                            resolveCurrentStatus(),
                            "Устройство отозвано",
                            "Выбранное устройство отозвано. Обновите список устройств, чтобы увидеть актуальное состояние.",
                            "",
                            null);
                    publish(updated);
                    return updated;
                }))
                .exceptionallyCompose(AsyncContext.withMdcFunction(error -> handleAsyncFailure(error, "revokeDevice")));
    }

    public SyncUiSnapshot clearDiagnostics() {
        initialize();
        stateRepository.clearLastError();
        diagnosticsMessage.set("");
        recordHealthEvent(
                "recovery",
                "Локальная диагностика очищена",
                "Очищены последняя sync-ошибка и diagnostics summary на этом устройстве.",
                false,
                false);
        SyncUiSnapshot cleared = buildSnapshot(
                null,
                null,
                null,
                "",
                null);
        publish(cleared);
        return cleared;
    }

    public String buildInternalDebugSummary() {
        initialize();
        return buildRuntimeDebugSummary();
    }

    public CompletableFuture<String> buildDiagnosticsBundle() {
        initialize();
        CompletableFuture<DeviceInventorySection> inventoryFuture;
        if (!snapshot().authenticated()) {
            inventoryFuture = CompletableFuture.completedFuture(new DeviceInventorySection(
                    List.of(),
                    "Устройство сейчас не аутентифицировано, поэтому live inventory устройств недоступен."));
        } else {
            inventoryFuture = listLinkedDevices()
                    .handle(AsyncContext.withMdcBiFunction((devices, error) -> {
                        Throwable cause = AsyncContext.unwrap(error);
                        if (cause != null) {
                            return new DeviceInventorySection(
                                    List.of(),
                                    "Не удалось получить live inventory устройств: " + userFacingErrorMessage(cause));
                        }
                        return new DeviceInventorySection(
                                devices == null ? List.of() : List.copyOf(devices),
                                "Live inventory устройств получен с backend.");
                    }));
        }
        return inventoryFuture.thenApply(AsyncContext.withMdcFunction(this::buildDiagnosticsBundle));
    }

    private String buildRuntimeDebugSummary() {
        SyncUiSnapshot current = snapshot();
        LocalDeviceIdentity deviceIdentity = stateRepository.loadDeviceIdentity();
        LocalSyncProfileSummary localSummary = current.localSummary() == null
                ? new LocalSyncProfileSummary(0, 0, 0, 0, 0, 0, 0)
                : current.localSummary();
        StringBuilder builder = new StringBuilder();
        builder.append("Cloud Sync Debug Summary\n");
        builder.append("status=").append(current.status()).append('\n');
        builder.append("statusMessage=").append(safe(current.statusMessage())).append('\n');
        builder.append("detailMessage=").append(safe(current.detailMessage())).append('\n');
        builder.append("baseUrl=").append(safe(current.baseUrl())).append('\n');
        builder.append("betaEnabled=").append(isBetaRolloutEnabled()).append('\n');
        builder.append("betaAllowedEmails=").append(String.join(",", ConfigManager.getCloudSyncBetaAllowedEmails())).append('\n');
        builder.append("authenticated=").append(current.authenticated()).append('\n');
        builder.append("syncEnabled=").append(current.syncEnabled()).append('\n');
        builder.append("strategyRequired=").append(current.strategyRequired()).append('\n');
        builder.append("accountEmail=").append(safe(current.accountEmail())).append('\n');
        builder.append("displayName=").append(safe(current.displayName())).append('\n');
        builder.append("selectedStrategy=").append(current.selectedStrategy() == null ? "" : current.selectedStrategy().name()).append('\n');
        builder.append("deviceId=").append(deviceIdentity == null ? "" : safe(deviceIdentity.deviceId())).append('\n');
        builder.append("deviceLabel=").append(deviceIdentity == null ? "" : safe(deviceIdentity.deviceLabel())).append('\n');
        builder.append("refreshSessionId=").append(safe(stateRepository.loadRefreshSessionId())).append('\n');
        builder.append("appliedCursor=").append(stateRepository.loadAppliedCursor()).append('\n');
        builder.append("lastKnownChangeId=").append(stateRepository.loadLastKnownChangeId()).append('\n');
        builder.append("lastSyncAt=").append(safe(current.lastSyncAt())).append('\n');
        builder.append("lastError=").append(safe(current.lastErrorSummary())).append('\n');
        builder.append("diagnostics=").append(safe(current.diagnosticsMessage())).append('\n');
        builder.append("trackedTasks=").append(localSummary.taskCount()).append('\n');
        builder.append("trackedDependencies=").append(localSummary.dependencyCount()).append('\n');
        builder.append("trackedTimeSessions=").append(localSummary.timeSessionCount()).append('\n');
        builder.append("trackedTemplates=").append(localSummary.templateCount()).append('\n');
        builder.append("trackedGoals=").append(localSummary.goalCount()).append('\n');
        builder.append("trackedMoodEntries=").append(localSummary.moodEntryCount()).append('\n');
        builder.append("pendingOutbox=").append(localSummary.pendingOutboxCount()).append('\n');
        builder.append('\n').append(buildHealthTimelineSummary());
        return builder.toString();
    }

    private String buildDiagnosticsBundle(DeviceInventorySection inventory) {
        StringBuilder builder = new StringBuilder();
        builder.append("Cloud Sync Diagnostics Bundle\n");
        builder.append("generatedAt=").append(Instant.now()).append('\n');
        builder.append("bundleVersion=1\n\n");
        builder.append("=== Runtime Debug Summary ===\n");
        builder.append(buildRuntimeDebugSummary()).append("\n\n");
        builder.append("=== Device Inventory ===\n");
        builder.append("inventoryStatus=").append(safe(inventory.status())).append('\n');
        if (inventory.devices().isEmpty()) {
            builder.append("(empty)\n");
        } else {
            for (SyncPayloads.DeviceListItemResponse device : inventory.devices()) {
                builder.append("- id=").append(safe(device.id())).append('\n');
                builder.append("  label=").append(safe(device.device_label())).append('\n');
                builder.append("  platform=").append(safe(device.platform())).append('\n');
                builder.append("  appVersion=").append(safe(device.app_version())).append('\n');
                builder.append("  registeredAt=").append(safe(device.registered_at())).append('\n');
                builder.append("  lastSeenAt=").append(safe(device.last_seen_at())).append('\n');
                builder.append("  revokedAt=").append(safe(device.revoked_at())).append('\n');
                builder.append("  activeRefreshSessions=").append(Math.max(0, device.active_refresh_session_count())).append('\n');
                builder.append("  currentDevice=").append(device.is_current_device()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    public List<SyncHealthEvent> recentHealthEvents() {
        initialize();
        return List.copyOf(healthEvents);
    }

    public String buildHealthTimelineSummary() {
        initialize();
        if (healthEvents.isEmpty()) {
            return "Sync Health Timeline\n(empty)";
        }
        StringBuilder builder = new StringBuilder("Sync Health Timeline\n");
        for (SyncHealthEvent event : new ArrayList<>(healthEvents)) {
            builder.append(event.occurredAt())
                    .append(" | ")
                    .append(safe(event.category()))
                    .append(" | ")
                    .append(safe(event.title()))
                    .append(" | ")
                    .append(safe(event.detail()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    public SyncUiSnapshot disconnectLocalSession() {
        initialize();
        diagnosticsMessage.set("Последний цикл: операция=локальное отключение сессии, результат=выполнено.");
        recordHealthEvent(
                "recovery",
                "Локальная cloud-сессия очищена",
                "Текущее устройство переведено в signed-out состояние без глобального logout аккаунта.",
                false,
                false);
        return disconnectLocalSession(
                "Локальная облачная сессия очищена",
                "Текущая cloud-сессия отключена только на этом устройстве. Для продолжения войдите заново.");
    }

    public SyncUiSnapshot prepareReauthentication() {
        initialize();
        stateRepository.clearLastError();
        String rememberedEmail = safe(stateRepository.loadRememberedAccountEmail());
        String rememberedDisplayName = safe(stateRepository.loadRememberedDisplayName());
        diagnosticsMessage.set("Последний цикл: операция=подготовка повторного входа, результат=готово.");
        recordHealthEvent(
                "recovery",
                "Подготовлен повторный вход",
                rememberedEmail.isBlank()
                        ? "Локальное устройство подготовлено к новой cloud-привязке."
                        : "Подготовлен повторный вход для аккаунта " + firstNonBlank(rememberedDisplayName, rememberedEmail) + ".",
                false,
                false);
        SyncUiSnapshot next = buildSnapshot(
                SyncUiStatus.SIGNED_OUT,
                "Готово к повторному входу",
                rememberedEmail.isBlank()
                        ? "Введите email и пароль, чтобы заново привязать устройство к облачному аккаунту."
                        : "Последний аккаунт: " + firstNonBlank(rememberedDisplayName, rememberedEmail)
                                + ". Введите пароль и подтвердите повторный вход.",
                "",
                0);
        publish(next);
        return next;
    }

    public void saveBaseUrl(String baseUrl) {
        ConfigManager.setProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_BASE_URL,
                CloudSyncUrlSupport.normalizeBaseUrl(safe(baseUrl)));
        publish(buildSnapshot(
                resolveCurrentStatus(),
                snapshot().statusMessage(),
                snapshot().detailMessage(),
                snapshot().lastErrorSummary(),
                snapshot().remotePreviewChangeCount()));
    }

    public boolean hasConfiguredBaseUrl() {
        String baseUrl = ConfigManager.getCloudSyncBaseUrl();
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public void close() {
        try {
            scheduler.close();
        } catch (RuntimeException e) {
            LOG.warning("cloud.sync.facade.scheduler.close.failed", "errorType", e.getClass().getSimpleName());
        }
        try {
            coordinator.close();
        } catch (RuntimeException e) {
            LOG.warning("cloud.sync.facade.coordinator.close.failed", "errorType", e.getClass().getSimpleName());
        }
    }

    private CompletableFuture<SyncUiSnapshot> handleAuthenticatedSession(SyncSessionSnapshot session) {
        if (session == null || !session.linked()) {
            SyncUiSnapshot unresolved = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Требуется проверка аккаунта",
                    "Не удалось подтвердить облачную сессию.",
                    null,
                    null);
            publish(unresolved);
            return CompletableFuture.completedFuture(unresolved);
        }

        AccountLinkStrategy selectedStrategy = stateRepository.loadAccountLinkStrategy();
        if (selectedStrategy == null) {
            SyncUiSnapshot waitingForStrategy = buildSnapshot(
                    SyncUiStatus.CONFLICT,
                    "Выберите стратегию связывания",
                    buildStrategyRequiredMessage(),
                    null,
                    null);
            publish(waitingForStrategy);
            return CompletableFuture.completedFuture(waitingForStrategy);
        }

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "true");
        startSchedulerIfEligible();
        return triggerManualSync();
    }

    private CompletableFuture<SyncUiSnapshot> handleAsyncFailure(Throwable throwable, String operation) {
        Throwable cause = AsyncContext.unwrap(throwable);
        String message = userFacingErrorMessage(cause);
        stateRepository.saveLastError(message);
        diagnosticsMessage.set(buildFailureDiagnostics(operation, cause));
        recordHealthEvent(
                operationLabel(operation),
                failureStatusMessage(operation),
                message,
                true,
                false);
        if (isInvalidAccessFailure(cause)) {
            return CompletableFuture.completedFuture(disconnectLocalSession(failureStatusMessage(operation), message));
        }
        SyncUiSnapshot failed = buildSnapshot(
                resolveFailureStatus(cause),
                failureStatusMessage(operation),
                message,
                message,
                null);
        publish(failed);
        return CompletableFuture.completedFuture(failed);
    }

    private void handleSchedulerResult(SyncRunResult result, Throwable error) {
        if (error != null) {
            Throwable cause = AsyncContext.unwrap(error);
            String message = userFacingErrorMessage(cause);
            stateRepository.saveLastError(message);
            diagnosticsMessage.set(buildFailureDiagnostics("scheduler", cause));
            recordHealthEvent(
                    "фоновая синхронизация",
                    "Фоновый цикл завершился ошибкой",
                    message,
                    true,
                    false);
            if (isInvalidAccessFailure(cause)) {
                disconnectLocalSession("Фоновая синхронизация остановлена", message);
                return;
            }
            publish(buildSnapshot(
                    resolveFailureStatus(cause),
                    "Фоновая синхронизация приостановлена",
                    message,
                    message,
                    null));
            return;
        }
        if (result != null) {
            handleSyncResult(result, result.trigger() == SyncTrigger.RECONNECT
                    ? "Соединение восстановлено"
                    : null);
        }
    }

    private SyncUiSnapshot handleSyncResult(SyncRunResult result, String overrideStatusMessage) {
        if (result == null) {
            SyncUiSnapshot current = snapshot();
            publish(current);
            return current;
        }

        String status = safe(result.status());
        diagnosticsMessage.set(buildSyncDiagnostics(result));
        recordHealthEvent(
                triggerLabel(result.trigger()),
                healthEventTitle(result),
                healthEventDetail(result),
                false,
                isDeferredResult(result));
        if (status.startsWith("sync_")) {
            String detail = buildSuccessDetail(result);
            SyncUiSnapshot synced = buildSnapshot(
                    SyncUiStatus.SYNCED,
                    overrideStatusMessage == null ? "Синхронизация актуальна" : overrideStatusMessage,
                    detail,
                    "",
                    0);
            publish(synced);
            return synced;
        }

        SyncUiSnapshot next = buildSnapshot(
                resolveCurrentStatus(),
                mapSkippedStatusTitle(status),
                mapSkippedStatusDetail(status),
                snapshot.get().lastErrorSummary(),
                snapshot.get().remotePreviewChangeCount());
        publish(next);
        return next;
    }

    private void startSchedulerIfEligible() {
        if (schedulerStarted.get()) {
            return;
        }
        if (!isBetaRolloutEnabled()) {
            return;
        }
        if (!ConfigManager.isCloudSyncEnabled()) {
            return;
        }
        if (!coordinator.isSyncConfigured() || !coordinator.hasAuthenticatedSession()) {
            return;
        }
        if (stateRepository.loadAccountLinkStrategy() == null) {
            return;
        }
        if (!isLinkedAccountAllowedForBeta()) {
            return;
        }
        scheduler.start();
        schedulerStarted.set(true);
    }

    private SyncUiSnapshot buildSnapshot(
            SyncUiStatus requestedStatus,
            String requestedStatusMessage,
            String requestedDetailMessage,
            String lastErrorOverride,
            Integer remotePreviewOverride) {
        LocalAccountLink accountLink = stateRepository.loadAccountLink();
        String rememberedEmail = safe(stateRepository.loadRememberedAccountEmail());
        String rememberedDisplayName = safe(stateRepository.loadRememberedDisplayName());
        String accountEmail = accountLink == null ? rememberedEmail : safe(accountLink.email());
        String displayName = accountLink == null ? rememberedDisplayName : safe(accountLink.displayName());
        LocalSyncProfileSummary localSummary = stateRepository.loadLocalProfileSummary();
        boolean authenticated = stateRepository.hasAuthenticatedSession();
        boolean syncEnabled = ConfigManager.isCloudSyncEnabled();
        AccountLinkStrategy selectedStrategy = stateRepository.loadAccountLinkStrategy();
        boolean strategyRequired = authenticated && selectedStrategy == null;
        String lastSyncAt = safe(stateRepository.loadLastSuccessfulSyncAt());
        String lastError = lastErrorOverride != null ? safe(lastErrorOverride) : safe(stateRepository.loadLastError());
        int remotePreviewCount = remotePreviewOverride != null
                ? Math.max(0, remotePreviewOverride)
                : Math.max(0, snapshot.get().remotePreviewChangeCount());
        SyncUiStatus status = requestedStatus;
        if (!authenticated) {
            status = SyncUiStatus.SIGNED_OUT;
        } else if (!isLinkedAccountAllowedForBeta()) {
            status = SyncUiStatus.CONFLICT;
        } else if (strategyRequired) {
            status = SyncUiStatus.CONFLICT;
        } else if (status == null) {
            status = resolveCurrentStatus();
        }

        String statusMessage = safe(requestedStatusMessage);
        if (statusMessage.isBlank()) {
            statusMessage = defaultStatusMessage(status, strategyRequired);
        }
        String detailMessage = safe(requestedDetailMessage);
        if (detailMessage.isBlank()) {
            detailMessage = defaultDetailMessage(status, strategyRequired, localSummary, remotePreviewCount);
        }

        return new SyncUiSnapshot(
                status,
                authenticated,
                syncEnabled,
                strategyRequired,
                accountEmail,
                displayName,
                safe(ConfigManager.getCloudSyncBaseUrl()),
                selectedStrategy,
                localSummary,
                remotePreviewCount,
                statusMessage,
                detailMessage,
                buildRolloutMessage(authenticated ? accountLink : null),
                safe(diagnosticsMessage.get()),
                lastSyncAt,
                lastError);
    }

    private void publish(SyncUiSnapshot next) {
        snapshot.set(next);
        for (Consumer<SyncUiSnapshot> listener : listeners) {
            try {
                listener.accept(next);
            } catch (RuntimeException e) {
                LOG.warning("cloud.sync.facade.listener.failed", "errorType", e.getClass().getSimpleName());
            }
        }
    }

    private SyncUiStatus resolveInitialStatus() {
        if (!isBetaRolloutEnabled()) {
            return SyncUiStatus.SIGNED_OUT;
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            return SyncUiStatus.SIGNED_OUT;
        }
        if (!isLinkedAccountAllowedForBeta()) {
            return SyncUiStatus.CONFLICT;
        }
        if (stateRepository.loadAccountLinkStrategy() == null) {
            return SyncUiStatus.CONFLICT;
        }
        String lastError = safe(stateRepository.loadLastError());
        if (!lastError.isBlank()) {
            return looksLikeOfflineMessage(lastError) ? SyncUiStatus.OFFLINE : SyncUiStatus.CONFLICT;
        }
        return SyncUiStatus.SYNCED;
    }

    private SyncUiStatus resolveCurrentStatus() {
        SyncUiSnapshot current = snapshot.get();
        if (current == null) {
            return resolveInitialStatus();
        }
        if (!isBetaRolloutEnabled()) {
            return SyncUiStatus.SIGNED_OUT;
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            return SyncUiStatus.SIGNED_OUT;
        }
        if (!isLinkedAccountAllowedForBeta()) {
            return SyncUiStatus.CONFLICT;
        }
        if (stateRepository.loadAccountLinkStrategy() == null) {
            return SyncUiStatus.CONFLICT;
        }
        String lastError = safe(stateRepository.loadLastError());
        if (!lastError.isBlank()) {
            return looksLikeOfflineMessage(lastError) ? SyncUiStatus.OFFLINE : SyncUiStatus.CONFLICT;
        }
        return SyncUiStatus.SYNCED;
    }

    private SyncUiStatus resolveFailureStatus(Throwable cause) {
        if (cause instanceof SyncHttpException http && http.statusCode() == 401) {
            return SyncUiStatus.CONFLICT;
        }
        if (cause instanceof SyncCircuitOpenException) {
            return SyncUiStatus.OFFLINE;
        }
        return looksLikeOfflineFailure(cause) ? SyncUiStatus.OFFLINE : SyncUiStatus.CONFLICT;
    }

    private boolean looksLikeOfflineFailure(Throwable cause) {
        return cause instanceof ConnectException
                || cause instanceof HttpTimeoutException
                || cause instanceof java.io.IOException
                || (cause != null && looksLikeOfflineMessage(cause.getMessage()));
    }

    private boolean looksLikeOfflineMessage(String message) {
        String normalized = safe(message).toLowerCase();
        return normalized.contains("timeout")
                || normalized.contains("timed out")
                || normalized.contains("connect")
                || normalized.contains("network")
                || normalized.contains("offline")
                || normalized.contains("refused");
    }

    private String initialStatusMessage() {
        return switch (resolveInitialStatus()) {
            case SIGNED_OUT -> "Не подключено";
            case CONFLICT -> "Требуется действие";
            case OFFLINE -> "Соединение недоступно";
            case SYNCED -> "Синхронизация готова";
            case SYNCING -> "Синхронизация выполняется";
        };
    }

    private String initialDetailMessage() {
        if (!isBetaRolloutEnabled()) {
            return "Облачная синхронизация выключена скрытым флагом `cloud.sync.beta.enabled`.";
        }
        if (!stateRepository.hasAuthenticatedSession()) {
            return "Войдите в аккаунт, чтобы включить облачную синхронизацию.";
        }
        if (!isLinkedAccountAllowedForBeta()) {
            return buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                    ? ""
                    : stateRepository.loadAccountLink().email()));
        }
        if (stateRepository.loadAccountLinkStrategy() == null) {
            return buildStrategyRequiredMessage();
        }
        String lastError = safe(stateRepository.loadLastError());
        if (!lastError.isBlank()) {
            return lastError;
        }
        return "Аккаунт уже привязан. Можно запускать ручную синхронизацию.";
    }

    private String defaultStatusMessage(SyncUiStatus status, boolean strategyRequired) {
        return switch (status) {
            case SIGNED_OUT -> "Не подключено";
            case SYNCING -> "Синхронизация выполняется";
            case SYNCED -> "Синхронизация актуальна";
            case CONFLICT -> strategyRequired ? "Выберите стратегию связывания" : "Нужна проверка синхронизации";
            case OFFLINE -> "Соединение недоступно";
        };
    }

    private String defaultDetailMessage(
            SyncUiStatus status,
            boolean strategyRequired,
            LocalSyncProfileSummary localSummary,
            int remotePreviewCount) {
        if (strategyRequired) {
            return buildStrategyRequiredMessage();
        }
        if (status == SyncUiStatus.CONFLICT && !isLinkedAccountAllowedForBeta()) {
            return buildBetaCohortDeniedMessage(safe(stateRepository.loadAccountLink() == null
                    ? ""
                    : stateRepository.loadAccountLink().email()));
        }
        return switch (status) {
            case SIGNED_OUT -> "Войдите в аккаунт, чтобы включить облачную синхронизацию.";
            case SYNCING -> "Операция выполняется в фоне и не блокирует интерфейс.";
            case SYNCED -> "Локальный профиль подключён. Новые изменения будут уходить через sync outbox.";
            case CONFLICT -> remotePreviewCount > 0
                    ? buildRemoteConflictMessage(remotePreviewCount)
                    : "Требуется ручная проверка статуса синхронизации.";
            case OFFLINE -> "Backend недоступен. Локальный режим продолжает работать без потери данных.";
        };
    }

    private String buildStrategyRequiredMessage() {
        LocalSyncProfileSummary summary = stateRepository.loadLocalProfileSummary();
        if (summary.isEmpty()) {
            return "Локальный профиль пуст или почти пуст. Выберите, как связывать это устройство с облаком.";
        }
        return "Локальный профиль уже содержит " + summary.trackedEntityCount()
                + " записей wave 1. Выберите стратегию первого связывания, прежде чем включать sync.";
    }

    private String buildRemoteConflictMessage(int remoteChangeCount) {
        return "Обнаружены " + remoteChangeCount
                + " облачных изменений. Они уже применены локально или учтены в выбранной стратегии связывания.";
    }

    private String buildSuccessDetail(SyncRunResult result) {
        String roundsPart = result.roundsPerformed() > 1
                ? " Цикл завершился за " + result.roundsPerformed() + " раунда."
                : "";
        int accepted = Math.max(0, result.acceptedChanges());
        int attempted = Math.max(0, result.attemptedChanges());
        if (accepted > 0) {
            String remotePart = result.remoteChangeCount() > 0
                ? " Параллельно применено " + result.remoteChangeCount() + " удалённых изменений."
                : "";
            return "Подтверждено " + accepted + " из " + attempted + " локальных изменений. Облако и локальный outbox согласованы." + remotePart + roundsPart;
        }
        if (result.remoteChangeCount() > 0) {
            return "Применено " + result.remoteChangeCount() + " удалённых изменений. Локальная SQLite приведена к актуальному состоянию сервера." + roundsPart;
        }
        return "Синхронизация завершена без конфликтов. Новых удалённых изменений не обнаружено." + roundsPart;
    }

    private String mapSkippedStatusTitle(String status) {
        return switch (safe(status)) {
            case "sync_not_authenticated" -> "Нужно войти в аккаунт";
            case "sync_not_configured" -> "Укажите адрес backend";
            case "sync_disabled" -> "Синхронизация выключена";
            case "sync_already_running" -> "Синхронизация уже выполняется";
            case "sync_no_serializable_outbox_changes" -> "Нет изменений для отправки";
            default -> "Синхронизация ждёт действия";
        };
    }

    private String mapSkippedStatusDetail(String status) {
        return switch (safe(status)) {
            case "sync_not_authenticated" -> "Текущий профиль не связан с облачным аккаунтом.";
            case "sync_not_configured" -> "Сначала сохраните `cloud.sync.baseUrl` в настройках.";
            case "sync_disabled" -> "Выберите стратегию связывания, чтобы включить sync.";
            case "sync_already_running" -> "Параллельный запуск предотвращён. Дождитесь завершения текущего цикла.";
            case "sync_no_serializable_outbox_changes" -> "В outbox нет актуальных wave 1 изменений для отправки.";
            default -> "Требуется дополнительная проверка состояния sync.";
        };
    }

    private String failureStatusMessage(String operation) {
        return switch (safe(operation)) {
            case "register" -> "Не удалось создать аккаунт";
            case "login" -> "Не удалось войти";
            case "logout" -> "Не удалось завершить сессию";
            case "manualSync" -> "Синхронизация завершилась с ошибкой";
            case "forceBootstrap" -> "Повторный bootstrap завершился с ошибкой";
            case "revokeDevice" -> "Не удалось отозвать устройство";
            default -> "Операция sync завершилась с ошибкой";
        };
    }

    private String userFacingErrorMessage(Throwable cause) {
        String transportHint = CloudSyncUrlSupport.describeTransportFailure(
                cause,
                ConfigManager.getCloudSyncBaseUrl());
        if (!transportHint.isBlank()) {
            return transportHint;
        }
        if (cause instanceof SyncCircuitOpenException circuit) {
            long seconds = Math.max(1L, circuit.retryAfterMillis() / 1000L);
            return "Облачная синхронизация временно приостановлена после серии ошибок. Повторите через "
                    + seconds + " сек.";
        }
        if (cause instanceof SyncHttpException http) {
            if (http.statusCode() == 401 && "invalid_access_token".equalsIgnoreCase(http.errorCode())) {
                return "Текущая облачная сессия больше недействительна. Войдите в аккаунт заново.";
            }
            String message = safe(http.getMessage());
            if (!message.isBlank()) {
                return message;
            }
            return "HTTP " + http.statusCode();
        }
        String raw = cause == null ? "" : safe(cause.getMessage());
        if (!raw.isBlank()) {
            return raw;
        }
        return "Не удалось связаться с backend синхронизации.";
    }

    private boolean isInvalidAccessFailure(Throwable cause) {
        return cause instanceof SyncHttpException http
                && http.statusCode() == 401
                && "invalid_access_token".equalsIgnoreCase(http.errorCode());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String primary, String fallback) {
        String safePrimary = safe(primary);
        if (!safePrimary.isBlank()) {
            return safePrimary;
        }
        return safe(fallback);
    }

    private boolean isBetaRolloutEnabled() {
        return ConfigManager.isCloudSyncBetaEnabled();
    }

    private boolean isEmailAllowedForBeta(String email) {
        List<String> allowedEmails = ConfigManager.getCloudSyncBetaAllowedEmails();
        if (allowedEmails.isEmpty()) {
            return true;
        }
        String normalized = safe(email).toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && allowedEmails.contains(normalized);
    }

    private boolean isLinkedAccountAllowedForBeta() {
        LocalAccountLink accountLink = stateRepository.loadAccountLink();
        if (accountLink == null) {
            return true;
        }
        return isEmailAllowedForBeta(accountLink.email());
    }

    private String buildRolloutMessage(LocalAccountLink accountLink) {
        if (!isBetaRolloutEnabled()) {
            return "Бета-режим выключен скрытым флагом.";
        }
        List<String> allowedEmails = ConfigManager.getCloudSyncBetaAllowedEmails();
        if (allowedEmails.isEmpty()) {
            return "Открытый внутренний бета-режим без ограничений по аккаунтам.";
        }
        String suffix = accountLink != null && isEmailAllowedForBeta(accountLink.email())
                ? " Текущий аккаунт включён в тестовую выборку."
                : " Доступ разрешён только выбранным тестовым аккаунтам.";
        return "Бета-режим ограничен выборкой из " + allowedEmails.size() + " тестовых аккаунтов." + suffix;
    }

    private String buildBetaCohortDeniedMessage(String email) {
        List<String> allowedEmails = ConfigManager.getCloudSyncBetaAllowedEmails();
        if (allowedEmails.isEmpty()) {
            return "Текущий бета-режим не ограничивает аккаунты.";
        }
        String normalized = safe(email);
        return normalized.isBlank()
                ? "Этот экран доступен только для тестовых бета-аккаунтов из заданной выборки."
                : "Аккаунт `" + normalized + "` не входит в тестовую выборку. Используйте тестовый аккаунт из rollout-списка.";
    }

    private String buildSyncDiagnostics(SyncRunResult result) {
        if (result == null) {
            return "";
        }
        String deferredPart = result.hasMoreRemoteChanges() || "sync_convergence_deferred".equalsIgnoreCase(safe(result.status()))
                ? " Сходимость отложена, нужен следующий цикл."
                : " Сходимость достигнута.";
        return "Последний цикл: источник=" + triggerLabel(result.trigger())
                + ", раундов=" + Math.max(0, result.roundsPerformed())
                + ", выгружено=" + Math.max(0, result.acceptedChanges()) + "/" + Math.max(0, result.attemptedChanges())
                + ", получено=" + Math.max(0, result.remoteChangeCount())
                + ", курсор=" + result.appliedCursor() + "." + deferredPart;
    }

    private String buildFailureDiagnostics(String operation, Throwable cause) {
        String errorType = cause == null ? "Unknown" : cause.getClass().getSimpleName();
        return "Последний цикл: операция=" + operationLabel(operation)
                + ", результат=ошибка"
                + ", тип=" + errorType + ".";
    }

    private String healthEventTitle(SyncRunResult result) {
        String status = safe(result == null ? "" : result.status());
        if ("sync_convergence_deferred".equals(status)) {
            return "Сходимость sync отложена";
        }
        if (status.startsWith("sync_") && result != null && result.roundsPerformed() > 0) {
            return "Sync-цикл завершён";
        }
        return mapSkippedStatusTitle(status);
    }

    private String healthEventDetail(SyncRunResult result) {
        if (result == null) {
            return "";
        }
        if (isDeferredResult(result)) {
            return buildDeferredReason(result);
        }
        String status = safe(result.status());
        if (status.startsWith("sync_")) {
            return buildSuccessDetail(result);
        }
        return mapSkippedStatusDetail(status);
    }

    private boolean isDeferredResult(SyncRunResult result) {
        return result != null
                && ("sync_convergence_deferred".equalsIgnoreCase(safe(result.status()))
                || result.hasMoreRemoteChanges());
    }

    private String buildDeferredReason(SyncRunResult result) {
        return "Достигнут лимит convergence-раундов или после цикла остались необработанные изменения. "
                + "Раундов=" + Math.max(0, result.roundsPerformed())
                + ", выгружено=" + Math.max(0, result.acceptedChanges()) + "/" + Math.max(0, result.attemptedChanges())
                + ", получено=" + Math.max(0, result.remoteChangeCount())
                + ", курсор=" + result.appliedCursor()
                + ", latestKnownChangeId=" + result.latestKnownChangeId() + ".";
    }

    private String triggerLabel(SyncTrigger trigger) {
        if (trigger == null) {
            return "неизвестно";
        }
        return switch (trigger) {
            case MANUAL -> "ручной запуск";
            case STARTUP -> "запуск приложения";
            case PERIODIC -> "периодический цикл";
            case RECONNECT -> "восстановление соединения";
        };
    }

    private String operationLabel(String operation) {
        return switch (safe(operation)) {
            case "register" -> "регистрация";
            case "login" -> "вход";
            case "logout" -> "выход";
            case "manualSync" -> "ручная синхронизация";
            case "forceBootstrap" -> "повторный bootstrap";
            case "revokeDevice" -> "отзыв устройства";
            case "scheduler" -> "фоновая синхронизация";
            default -> safe(operation).isBlank() ? "неизвестно" : safe(operation);
        };
    }

    private SyncUiSnapshot betaDisabledSnapshot() {
        SyncUiSnapshot disabled = buildSnapshot(
                SyncUiStatus.SIGNED_OUT,
                "Бета-синхронизация отключена",
                "Облачная синхронизация выключена скрытым флагом `cloud.sync.beta.enabled`.",
                "",
                0);
        publish(disabled);
        return disabled;
    }

    private SyncUiSnapshot disconnectLocalSession(String statusMessage, String detailMessage) {
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_ENABLED, "false");
        schedulerStarted.set(false);
        stateRepository.clearAuthenticatedSession();
        SyncUiSnapshot next = buildSnapshot(
                SyncUiStatus.SIGNED_OUT,
                statusMessage,
                detailMessage,
                "",
                0);
        publish(next);
        return next;
    }

    private void recordHealthEvent(
            String category,
            String title,
            String detail,
            boolean failure,
            boolean deferred) {
        SyncHealthEvent event = new SyncHealthEvent(
                Instant.now().toString(),
                safe(category),
                safe(title),
                safe(detail),
                failure,
                deferred);
        healthEvents.add(0, event);
        while (healthEvents.size() > MAX_HEALTH_EVENTS) {
            healthEvents.remove(healthEvents.size() - 1);
        }
        stateRepository.saveHealthEvents(List.copyOf(healthEvents));
    }

    private record DeviceInventorySection(
            List<SyncPayloads.DeviceListItemResponse> devices,
            String status) {
    }
}
