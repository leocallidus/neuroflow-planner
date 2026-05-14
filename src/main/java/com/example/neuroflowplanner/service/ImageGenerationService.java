package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.dto.AiImageResponseDto;
import com.example.neuroflowplanner.ai.json.AiCoreResponseMapper;
import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.ai.resilience.AiHttpErrorClassifier;
import com.example.neuroflowplanner.ai.resilience.AiResiliencePolicy;
import com.example.neuroflowplanner.ai.resilience.AiRetryDelayStrategy;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.service.imagecapability.ImageCapabilityField;
import com.example.neuroflowplanner.service.imagecapability.ImageConfigResolution;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapability;
import com.example.neuroflowplanner.service.imagecapability.ImageModelCapabilityRegistry;
import com.example.neuroflowplanner.service.imagecapability.ImageValidatedOptions;
import com.example.neuroflowplanner.service.imageflow.ImageRequestEvent;
import com.example.neuroflowplanner.service.imageflow.ImageRequestEventPublisher;
import com.example.neuroflowplanner.service.imageflow.ImageRequestProgress;
import com.example.neuroflowplanner.service.imageflow.ImageRequestState;
import com.example.neuroflowplanner.service.imageflow.ImageRequestSubscription;
import com.example.neuroflowplanner.service.imagejob.ImageJobManager;
import com.example.neuroflowplanner.service.imagejob.ImageJobSnapshot;
import com.example.neuroflowplanner.service.imagejob.ImageJobState;
import com.example.neuroflowplanner.util.AiApiUtils;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.DataPathManager;
import com.example.neuroflowplanner.util.ImageGenConfigDefaults;
import com.example.neuroflowplanner.util.StructuredLogger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ImageGenerationService {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ImageGenerationService.class);
    private static final ImageGenerationService INSTANCE = new ImageGenerationService();
    private static final Duration MIN_ATTEMPT_TIMEOUT = Duration.ofMillis(500);

    private final HttpClient client;
    private final ImageRequestEventPublisher lifecyclePublisher = new ImageRequestEventPublisher();
    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ImageRequestRuntime> inFlightRuntimes = new ConcurrentHashMap<>();
    private final ImageModelCapabilityRegistry capabilityRegistry = ImageModelCapabilityRegistry.getInstance();
    private final ImageJobManager jobManager = ImageJobManager.getInstance();

    private ImageGenerationService() {
        this.client = createTrustAllClient();
    }

    public static ImageGenerationService getInstance() {
        return INSTANCE;
    }

    public record ImageGenerationOptions(
        String model,
        String size,
        String aspectRatio,
        String resolution,
        String quality,
        String outputFormat,
        String strength,
        String guidanceScale
    ) {
    }

    public record ImageGenerationResult(
        Path savedPath,
        String remoteUrl,
        String requestId
    ) {
    }

    public CompletableFuture<ImageGenerationResult> generateImage(String prompt, ImageGenerationOptions options) {
        return generateImage("", UUID.randomUUID().toString(), prompt, options);
    }

    public CompletableFuture<ImageGenerationResult> generateImage(
        String conversationId,
        String jobId,
        String prompt,
        ImageGenerationOptions options
    ) {
        String normalizedConversationId = sanitizeId(conversationId);
        String effectiveJobId = sanitizeJobId(jobId);

        if (!AiClientFactory.getInstance().supportsImages()) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Генерация изображений недоступна в текущем режиме ИИ. Используйте режим 'Внешний API'."
            ));
        }
        if (prompt == null || prompt.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Пустой prompt."));
        }

        ImageValidatedOptions validatedOptions = validateRequestOptions(options);
        ImageJobSnapshot snapshot = createQueuedJobSnapshot(
            effectiveJobId,
            normalizedConversationId,
            prompt,
            validatedOptions
        );
        jobManager.upsertJob(snapshot);
        return startManagedJob(snapshot, ImageJobStartMode.NEW);
    }

    public ImageRequestSubscription subscribeToRequestEvents(Consumer<ImageRequestEvent> listener) {
        return lifecyclePublisher.subscribe(listener);
    }

    public ImageRequestEvent getLastRequestEvent() {
        return lifecyclePublisher.getLastEvent();
    }

    public ImageJobSnapshot getJob(String jobId) {
        return jobManager.getJob(jobId);
    }

    public ImageJobSnapshot getLatestJobForConversation(String conversationId) {
        return jobManager.findLatestJobForConversation(conversationId);
    }

    public List<ImageJobSnapshot> listJobsForConversation(String conversationId) {
        return jobManager.listJobsForConversation(conversationId);
    }

    public void resumeAllPendingJobs() {
        if (!AiClientFactory.getInstance().supportsImages()) {
            return;
        }
        for (ImageJobSnapshot snapshot : jobManager.listResumableJobs()) {
            if (snapshot == null
                || snapshot.getJobId().isBlank()
                || snapshot.getState() == ImageJobState.PAUSED
                || inFlightRequests.containsKey(snapshot.getJobId())) {
                continue;
            }
            startManagedJob(snapshot, ImageJobStartMode.AUTO_RESUME);
        }
    }

    public void resumePendingJobsForConversation(String conversationId) {
        for (ImageJobSnapshot snapshot : jobManager.listResumableJobsForConversation(conversationId)) {
            if (snapshot == null
                || snapshot.getJobId().isBlank()
                || snapshot.getState() == ImageJobState.PAUSED
                || inFlightRequests.containsKey(snapshot.getJobId())) {
                continue;
            }
            startManagedJob(snapshot, ImageJobStartMode.AUTO_RESUME);
        }
    }

    public CompletableFuture<ImageGenerationResult> resumeJob(String jobId) {
        ImageJobSnapshot snapshot = jobManager.getJob(jobId);
        if (snapshot == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Image job не найден."));
        }
        return startManagedJob(snapshot, ImageJobStartMode.USER_RESUME);
    }

    public CompletableFuture<ImageGenerationResult> retryJob(String jobId) {
        ImageJobSnapshot snapshot = jobManager.prepareUserRetry(jobId);
        if (snapshot == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Image job не найден."));
        }
        return startManagedJob(snapshot, ImageJobStartMode.USER_RETRY);
    }

    public boolean pauseJob(String jobId) {
        String normalizedJobId = sanitizeId(jobId);
        if (normalizedJobId.isBlank()) {
            return false;
        }
        boolean requested = jobManager.requestPause(normalizedJobId);
        ImageRequestRuntime runtime = inFlightRuntimes.get(normalizedJobId);
        if (runtime != null) {
            runtime.pauseRequested().set(true);
        }
        CompletableFuture<?> future = inFlightRequests.get(normalizedJobId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
            return true;
        }
        if (requested) {
            ImageJobSnapshot snapshot = jobManager.updateJob(normalizedJobId, job -> {
                job.setState(ImageJobState.PAUSED);
                job.setPauseRequested(false);
                job.setLastMessage("Генерация изображения поставлена на паузу.");
                job.setUpdatedAt(System.currentTimeMillis());
            });
            publishSnapshotEvent(snapshot, ImageRequestState.PAUSED, "Генерация изображения поставлена на паузу.", Map.of());
        }
        return requested;
    }

    public boolean cancelRequest(String jobId) {
        String normalizedJobId = sanitizeId(jobId);
        if (normalizedJobId.isBlank()) {
            return false;
        }
        boolean requested = jobManager.requestCancel(normalizedJobId);
        ImageRequestRuntime runtime = inFlightRuntimes.get(normalizedJobId);
        if (runtime != null) {
            runtime.cancellationRequested().set(true);
            runtime.pauseRequested().set(false);
        }
        CompletableFuture<?> future = inFlightRequests.get(normalizedJobId);
        if (future == null || future.isDone()) {
            if (requested) {
                ImageJobSnapshot snapshot = jobManager.updateJob(normalizedJobId, job -> {
                    job.setState(ImageJobState.CANCELLED);
                    job.setCancelRequested(false);
                    job.setPauseRequested(false);
                    job.setLastMessage("Генерация изображения отменена.");
                    job.setUpdatedAt(System.currentTimeMillis());
                });
                publishSnapshotEvent(snapshot, ImageRequestState.CANCELLED, "Генерация изображения отменена.", Map.of());
            }
            return requested;
        }
        future.cancel(true);
        return true;
    }

    public ImageGenerationOptions loadOptionsFromConfig() {
        ImageConfigResolution resolved = ImageGenConfigDefaults.resolveConfiguredOptions(
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_MODEL),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_SIZE),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_ASPECT_RATIO),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_RESOLUTION),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_QUALITY),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_OUTPUT_FORMAT),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_STRENGTH),
            ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_GUIDANCE_SCALE)
        );
        if (resolved.hasIssues()) {
            LOG.warning(
                "image.config.sanitized",
                "issues", resolved.summary(),
                "model", resolved.options().model()
            );
        }
        ImageValidatedOptions options = resolved.options();
        return new ImageGenerationOptions(
            options.model(),
            options.size(),
            options.aspectRatio(),
            options.resolution(),
            options.quality(),
            options.outputFormat(),
            options.strength(),
            options.guidanceScale()
        );
    }

    private CompletableFuture<ImageGenerationResult> startManagedJob(
        ImageJobSnapshot snapshot,
        ImageJobStartMode startMode
    ) {
        if (snapshot == null || snapshot.getJobId().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Image job не найден."));
        }
        CompletableFuture<?> existing = inFlightRequests.get(snapshot.getJobId());
        if (existing != null && !existing.isDone()) {
            @SuppressWarnings("unchecked")
            CompletableFuture<ImageGenerationResult> typed = (CompletableFuture<ImageGenerationResult>) existing;
            return typed;
        }

        String correlationId = AsyncContext.ensureRequestId();
        String apiKeyConfig = ConfigManager.getProperty("external.api.key");
        if (apiKeyConfig == null || apiKeyConfig.isBlank()) {
            apiKeyConfig = ConfigManager.getProperty("api.key");
        }
        String baseUrl = ConfigManager.getProperty("external.api.baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ConfigManager.getProperty("api.url");
        }

        ImageGenerationOptions options = optionsFromSnapshot(snapshot);
        ImageValidatedOptions validatedOptions = validateRequestOptions(options);
        ImageExecutionConfig executionConfig = loadExecutionConfig();
        ImageExecutionPlan executionPlan = buildExecutionPlan(
            snapshot.getPrompt(),
            options,
            validatedOptions.model(),
            apiKeyConfig,
            resolveImagesBaseUrl(baseUrl),
            executionConfig
        );

        ImageJobSnapshot persistedSnapshot = snapshot.copy();
        persistedSnapshot.setRequestedModel(validatedOptions.model());
        persistedSnapshot.setUpdatedAt(System.currentTimeMillis());
        persistedSnapshot.setPauseRequested(false);
        persistedSnapshot.setCancelRequested(false);
        if (persistedSnapshot.getCreatedAt() <= 0L) {
            persistedSnapshot.setCreatedAt(System.currentTimeMillis());
        }
        jobManager.upsertJob(persistedSnapshot);

        ImageRequestRuntime runtime = new ImageRequestRuntime(persistedSnapshot);
        runtime.activeModel().set(firstNonBlank(persistedSnapshot.getActiveModel(), validatedOptions.model()));
        runtime.providerRequestId().set(persistedSnapshot.getRequestId());

        publishStartEvent(runtime, persistedSnapshot, startMode, correlationId);

        CompletableFuture<ImageGenerationResult> requestFuture = switch (startMode) {
            case NEW, USER_RETRY -> executeWithModelFallback(runtime, executionPlan, 0);
            case USER_RESUME, AUTO_RESUME -> resumeManagedJob(runtime, executionPlan, persistedSnapshot);
        };

        if (!requestFuture.isDone()) {
            inFlightRequests.put(persistedSnapshot.getJobId(), requestFuture);
            inFlightRuntimes.put(persistedSnapshot.getJobId(), runtime);
        }

        final String finalPrompt = persistedSnapshot.getPrompt();
        return requestFuture.whenComplete(AsyncContext.withMdcBiConsumer((result, error) -> {
            inFlightRequests.remove(persistedSnapshot.getJobId());
            inFlightRuntimes.remove(persistedSnapshot.getJobId());

            String providerRequestId = runtime.providerRequestId().get();
            String activeModel = runtime.activeModel().get();
            if (error != null) {
                Throwable actual = AsyncContext.unwrap(error);
                if (runtime.pauseRequested().get() || actual instanceof ImageJobPausedException) {
                    ImageJobSnapshot paused = jobManager.updateJob(persistedSnapshot.getJobId(), job -> {
                        job.setState(ImageJobState.PAUSED);
                        job.setPauseRequested(false);
                        job.setCancelRequested(false);
                        job.setActiveModel(activeModel);
                        job.setRequestId(firstNonBlank(providerRequestId, job.getRequestId()));
                        job.setLastMessage("Генерация изображения поставлена на паузу.");
                        job.setUpdatedAt(System.currentTimeMillis());
                    });
                    publishSnapshotEvent(paused, ImageRequestState.PAUSED, "Генерация изображения поставлена на паузу.", Map.of());
                    return;
                }
                if (actual instanceof CancellationException) {
                    ImageJobSnapshot cancelled = jobManager.updateJob(persistedSnapshot.getJobId(), job -> {
                        job.setState(ImageJobState.CANCELLED);
                        job.setPauseRequested(false);
                        job.setCancelRequested(false);
                        job.setActiveModel(activeModel);
                        job.setRequestId(firstNonBlank(providerRequestId, job.getRequestId()));
                        job.setLastMessage("Генерация изображения отменена.");
                        job.setUpdatedAt(System.currentTimeMillis());
                    });
                    publishTerminalOnce(
                        runtime,
                        providerRequestId,
                        ImageRequestState.CANCELLED,
                        activeModel,
                        "Генерация изображения отменена.",
                        Map.of()
                    );
                    return;
                }
                jobManager.updateJob(persistedSnapshot.getJobId(), job -> {
                    job.setState(ImageJobState.FAILED);
                    job.setPauseRequested(false);
                    job.setCancelRequested(false);
                    job.setActiveModel(activeModel);
                    job.setRequestId(firstNonBlank(providerRequestId, job.getRequestId()));
                    job.setLastMessage(normalizeFailureMessage(actual));
                    job.setLastError(normalizeFailureMessage(actual));
                    job.setUpdatedAt(System.currentTimeMillis());
                });
                publishTerminalOnce(
                    runtime,
                    providerRequestId,
                    ImageRequestState.FAILED,
                    activeModel,
                    normalizeFailureMessage(actual),
                    metadata("errorType", actual == null ? "" : actual.getClass().getSimpleName())
                );
                LOG.error(
                    "image.generation.failed",
                    ErrorCode.AI_REQUEST_FAILED,
                    actual,
                    "operation", startMode == ImageJobStartMode.NEW ? "generateImage" : "resumeImageJob",
                    "model", activeModel,
                    "promptLength", finalPrompt == null ? 0 : finalPrompt.length(),
                    "requestId", correlationId,
                    "imageJobId", persistedSnapshot.getJobId(),
                    "imageRequestId", providerRequestId
                );
                return;
            }
            if (result != null) {
                jobManager.updateJob(persistedSnapshot.getJobId(), job -> {
                    job.setState(ImageJobState.DONE);
                    job.setPauseRequested(false);
                    job.setCancelRequested(false);
                    job.setActiveModel(activeModel);
                    job.setRequestId(firstNonBlank(result.requestId(), job.getRequestId()));
                    job.setRemoteUrl(firstNonBlank(result.remoteUrl(), job.getRemoteUrl()));
                    job.setSavedPath(result.savedPath() == null ? job.getSavedPath() : result.savedPath().toString());
                    job.setLastMessage("Изображение успешно сохранено.");
                    job.setLastError("");
                    job.setUpdatedAt(System.currentTimeMillis());
                });
                publishTerminalOnce(
                    runtime,
                    result.requestId(),
                    ImageRequestState.DONE,
                    activeModel,
                    "Изображение успешно сохранено.",
                    metadata(
                        "remoteUrl", result.remoteUrl(),
                        "savedPath", result.savedPath() == null ? "" : result.savedPath().toString()
                    )
                );
                LOG.info(
                    "image.generation.completed",
                    "operation", startMode == ImageJobStartMode.NEW ? "generateImage" : "resumeImageJob",
                    "model", activeModel,
                    "requestId", correlationId,
                    "imageJobId", persistedSnapshot.getJobId(),
                    "imageRequestId", result.requestId(),
                    "savedPath", result.savedPath() == null ? "" : result.savedPath().toString()
                );
            }
        }));
    }

    private CompletableFuture<ImageGenerationResult> resumeManagedJob(
        ImageRequestRuntime runtime,
        ImageExecutionPlan executionPlan,
        ImageJobSnapshot snapshot
    ) {
        String requestId = sanitizeId(snapshot.getRequestId());
        if (requestId.isBlank()) {
            return executeWithModelFallback(runtime, executionPlan, 0);
        }
        runtime.providerRequestId().set(requestId);
        ImageModelExecution modelExecution = resolveModelExecution(
            executionPlan.originalOptions(),
            firstNonBlank(snapshot.getActiveModel(), snapshot.getRequestedModel())
        );
        runtime.activeModel().set(modelExecution.options().model());
        if (snapshot.getRemoteUrl() != null && !snapshot.getRemoteUrl().isBlank()) {
            return downloadAndSaveImage(
                runtime,
                executionPlan,
                modelExecution,
                snapshot.getRemoteUrl(),
                requestId,
                1
            ).thenApply(savedPath -> new ImageGenerationResult(savedPath, snapshot.getRemoteUrl(), requestId));
        }
        return pollForResultUrl(runtime, executionPlan, modelExecution, requestId, 0, 1)
            .thenCompose(url -> downloadAndSaveImage(runtime, executionPlan, modelExecution, url, requestId, 1)
                .thenApply(savedPath -> new ImageGenerationResult(savedPath, url, requestId)));
    }

    private void publishStartEvent(
        ImageRequestRuntime runtime,
        ImageJobSnapshot snapshot,
        ImageJobStartMode startMode,
        String correlationId
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("correlationId", sanitizeId(correlationId));
        metadata.put("jobStartMode", startMode.code());
        if (snapshot != null && snapshot.getUserRetryCount() > 0) {
            metadata.put("userRetryCount", String.valueOf(snapshot.getUserRetryCount()));
        }
        switch (startMode) {
            case NEW -> publishState(
                runtime,
                "",
                ImageRequestState.QUEUED,
                snapshot == null ? "" : snapshot.getRequestedModel(),
                "Запрос поставлен в очередь.",
                metadata
            );
            case USER_RETRY -> publishState(
                runtime,
                "",
                ImageRequestState.QUEUED,
                snapshot == null ? "" : snapshot.getRequestedModel(),
                "Пользователь повторно запустил image-job.",
                metadata
            );
            case USER_RESUME, AUTO_RESUME -> publishState(
                runtime,
                snapshot == null ? "" : snapshot.getRequestId(),
                ImageRequestState.RESUMING,
                snapshot == null ? "" : firstNonBlank(snapshot.getActiveModel(), snapshot.getRequestedModel()),
                "Восстанавливаю image-job по сохранённому request-id.",
                metadata
            );
        }
    }

    private ImageJobSnapshot createQueuedJobSnapshot(
        String jobId,
        String conversationId,
        String prompt,
        ImageValidatedOptions validatedOptions
    ) {
        long now = System.currentTimeMillis();
        ImageJobSnapshot snapshot = new ImageJobSnapshot();
        snapshot.setJobId(jobId);
        snapshot.setConversationId(conversationId);
        snapshot.setPrompt(prompt);
        snapshot.setRequestedModel(validatedOptions.model());
        snapshot.setActiveModel(validatedOptions.model());
        snapshot.setSize(validatedOptions.size());
        snapshot.setAspectRatio(validatedOptions.aspectRatio());
        snapshot.setResolution(validatedOptions.resolution());
        snapshot.setState(ImageJobState.QUEUED);
        snapshot.setAttempt(1);
        snapshot.setLastMessage("Запрос поставлен в очередь.");
        snapshot.setCreatedAt(now);
        snapshot.setUpdatedAt(now);
        return snapshot;
    }

    private ImageGenerationOptions optionsFromSnapshot(ImageJobSnapshot snapshot) {
        return new ImageGenerationOptions(
            snapshot == null ? "" : snapshot.getRequestedModel(),
            snapshot == null ? "" : snapshot.getSize(),
            snapshot == null ? "" : snapshot.getAspectRatio(),
            snapshot == null ? "" : snapshot.getResolution(),
            sanitizeId(ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_QUALITY)),
            sanitizeId(ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_OUTPUT_FORMAT)),
            sanitizeId(ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_STRENGTH)),
            sanitizeId(ConfigManager.getProperty(ImageGenConfigDefaults.CONFIG_KEY_IMAGE_GUIDANCE_SCALE))
        );
    }

    private void publishSnapshotEvent(
        ImageJobSnapshot snapshot,
        ImageRequestState state,
        String message,
        Map<String, String> metadata
    ) {
        if (snapshot == null || state == null) {
            return;
        }
        lifecyclePublisher.publish(new ImageRequestEvent(
            sanitizeId(snapshot.getJobId()),
            sanitizeId(snapshot.getRequestId()),
            sanitizeId(snapshot.getConversationId()),
            state,
            firstNonBlank(snapshot.getActiveModel(), snapshot.getRequestedModel()),
            message == null ? "" : message.trim(),
            new ImageRequestProgress(
                Math.max(0L, System.currentTimeMillis() - Math.max(0L, snapshot.getCreatedAt())),
                Math.max(1, snapshot.getAttempt()),
                Math.max(1, snapshot.getAttempt()),
                state.isTerminal()
            ),
            Instant.now(),
            metadata == null ? Map.of() : Map.copyOf(metadata)
        ));
    }

    private CompletableFuture<ImageGenerationResult> executeWithModelFallback(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        int modelIndex
    ) {
        if (modelIndex >= plan.candidateModels().size()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Не удалось завершить генерацию изображения ни одной доступной моделью."
            ));
        }

        String targetModel = plan.candidateModels().get(modelIndex);
        ImageModelExecution modelExecution = resolveModelExecution(plan.originalOptions(), targetModel);
        String previousModel = sanitizeId(runtime.activeModel().get());
        runtime.activeModel().set(modelExecution.options().model());
        runtime.providerRequestId().set("");

        if (modelIndex > 0) {
            publishFallbackTransition(runtime, previousModel, modelExecution, plan.config().submitPolicy().maxAttempts());
        }

        return executeSingleModel(runtime, plan, modelExecution)
            .<CompletableFuture<ImageGenerationResult>>handle((result, error) -> {
                if (error == null) {
                    return CompletableFuture.completedFuture(result);
                }
                Throwable actual = AsyncContext.unwrap(error);
                CompletableFuture<ImageGenerationResult> fallback = maybeFallbackToNextModel(
                    runtime,
                    plan,
                    modelIndex,
                    actual
                );
                return fallback != null ? fallback : CompletableFuture.failedFuture(actual);
            })
            .thenCompose((CompletableFuture<ImageGenerationResult> next) -> next);
    }

    private CompletableFuture<ImageGenerationResult> executeSingleModel(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        ImageModelExecution modelExecution
    ) {
        return submitGenerationRequest(runtime, plan, modelExecution, 1)
            .thenCompose(requestId -> pollForResultUrl(runtime, plan, modelExecution, requestId, 0, 1)
                .thenCompose(url -> downloadAndSaveImage(runtime, plan, modelExecution, url, requestId, 1)
                    .thenApply(savedPath -> new ImageGenerationResult(savedPath, url, requestId))));
    }

    private CompletableFuture<String> submitGenerationRequest(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        ImageModelExecution modelExecution,
        int attempt
    ) {
        CompletableFuture<String> cancellation = interruptedFutureIfNeeded(runtime);
        if (cancellation != null) {
            return cancellation;
        }
        CompletableFuture<String> budget = budgetExceededFutureIfNeeded(runtime, plan.config());
        if (budget != null) {
            return budget;
        }

        ImageStageRetryPolicy policy = plan.config().submitPolicy();
        ImageValidatedOptions options = modelExecution.options();
        String generateUrl = plan.imagesBaseUrl() + "/media";

        publishState(
            runtime,
            "",
            ImageRequestState.SENDING,
            options.model(),
            attempt == 1
                ? "Отправляю запрос на генерацию изображения."
                : "Повторно отправляю запрос на генерацию изображения.",
            attempt,
            policy.maxAttempts(),
            metadata("endpoint", generateUrl, "stage", policy.stage().code())
        );

        String body = buildMediaRequestBody(plan.prompt(), options);
        HttpRequest.Builder requestBuilder = newRequestBuilder(URI.create(generateUrl), runtime, plan.config())
            .header("Content-Type", "application/json");
        if (plan.apiKey() != null && !plan.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + plan.apiKey().trim());
        }

        HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .<CompletableFuture<String>>handle(AsyncContext.withMdcBiFunction((response, error) -> {
                if (error != null) {
                    return retryStageOrFail(
                        runtime,
                        plan,
                        "",
                        options.model(),
                        policy,
                        attempt,
                        false,
                        error,
                        () -> submitGenerationRequest(runtime, plan, modelExecution, attempt + 1)
                    );
                }
                if (!AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                    ImageRequestStageException statusFailure = stageHttpFailure(
                        policy.stage(),
                        response.statusCode(),
                        "Ошибка API (" + response.statusCode() + "): " + response.body()
                    );
                    return retryStageOrFail(
                        runtime,
                        plan,
                        "",
                        options.model(),
                        policy,
                        attempt,
                        false,
                        statusFailure,
                        () -> submitGenerationRequest(runtime, plan, modelExecution, attempt + 1)
                    );
                }
                try {
                    String requestId = AiCoreResponseMapper.extractImageRequestIdFromGeneration(response.body());
                    runtime.providerRequestId().set(requestId);
                    publishState(
                        runtime,
                        requestId,
                        ImageRequestState.PROVIDER_ACCEPTED,
                        options.model(),
                        "Провайдер принял запрос на генерацию.",
                        attempt,
                        policy.maxAttempts(),
                        metadata(
                            "statusCode", String.valueOf(response.statusCode()),
                            "stage", policy.stage().code()
                        )
                    );
                    return CompletableFuture.completedFuture(requestId);
                } catch (AiParsingException e) {
                    return CompletableFuture.failedFuture(new ImageRequestStageException(
                        policy.stage(),
                        "Не удалось получить requestId из ответа генерации изображения.",
                        false,
                        null,
                        null,
                        e
                    ));
                }
            }))
            .thenCompose((CompletableFuture<String> next) -> next);
    }

    private CompletableFuture<String> pollForResultUrl(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        ImageModelExecution modelExecution,
        String requestId,
        int pollIteration,
        int attempt
    ) {
        CompletableFuture<String> cancellation = interruptedFutureIfNeeded(runtime);
        if (cancellation != null) {
            return cancellation;
        }
        CompletableFuture<String> budget = budgetExceededFutureIfNeeded(runtime, plan.config());
        if (budget != null) {
            return budget;
        }

        ImageStageRetryPolicy policy = plan.config().pollPolicy();
        publishState(
            runtime,
            requestId,
            ImageRequestState.POLLING,
            modelExecution.options().model(),
            pollIteration == 0
                ? "Ожидаю готовность изображения у провайдера."
                : "Проверяю статус генерации изображения.",
            attempt,
            policy.maxAttempts(),
            metadata(
                "pollIteration", String.valueOf(pollIteration + 1),
                "stage", policy.stage().code()
            )
        );

        return pollForResultUrl(runtime, plan, modelExecution, requestId, pollIteration, attempt, false);
    }

    private CompletableFuture<String> pollForResultUrl(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        ImageModelExecution modelExecution,
        String requestId,
        int pollIteration,
        int attempt,
        boolean useHistoryEndpoint
    ) {
        ImageStageRetryPolicy policy = plan.config().pollPolicy();
        String statusUrl = useHistoryEndpoint
            ? plan.imagesBaseUrl() + "/history/generations/" + requestId
            : plan.imagesBaseUrl() + "/media/" + requestId;
        HttpRequest.Builder requestBuilder = newRequestBuilder(URI.create(statusUrl), runtime, plan.config())
            .header("Accept", "application/json");
        if (plan.apiKey() != null && !plan.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + plan.apiKey().trim());
        }

        HttpRequest request = requestBuilder.GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .<CompletableFuture<String>>handle(AsyncContext.withMdcBiFunction((response, error) -> {
                if (error != null) {
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        error,
                        () -> pollForResultUrl(runtime, plan, modelExecution, requestId, pollIteration, attempt + 1, useHistoryEndpoint)
                    );
                }
                if (!AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                    if (!useHistoryEndpoint && shouldTryHistoryPollingFallback(response.statusCode())) {
                        publishState(
                            runtime,
                            requestId,
                            ImageRequestState.POLLING,
                            modelExecution.options().model(),
                            "Polza Media API не вернул статус. Переключаюсь на history endpoint.",
                            attempt,
                            policy.maxAttempts(),
                            metadata(
                                "statusCode", String.valueOf(response.statusCode()),
                                "endpoint", statusUrl,
                                "fallbackEndpoint", plan.imagesBaseUrl() + "/history/generations/" + requestId,
                                "stage", policy.stage().code()
                            )
                        );
                        return pollForResultUrl(runtime, plan, modelExecution, requestId, pollIteration, attempt, true);
                    }
                    ImageRequestStageException statusFailure = stageHttpFailure(
                        policy.stage(),
                        response.statusCode(),
                        "Ошибка статуса (" + response.statusCode() + "): " + response.body()
                    );
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        statusFailure,
                        () -> pollForResultUrl(runtime, plan, modelExecution, requestId, pollIteration, attempt + 1, useHistoryEndpoint)
                    );
                }

                String body = response.body();
                String url;
                String providerStatus;
                try {
                    if (useHistoryEndpoint) {
                        url = AiCoreResponseMapper.extractImageUrlFromHistory(body);
                        providerStatus = AiCoreResponseMapper.extractImageStatusFromHistory(body);
                    } else {
                        AiImageResponseDto pollingResponse = AiCoreResponseMapper.parseImagePollingResponse(body);
                        url = AiCoreResponseMapper.projectBestImageUrl(pollingResponse);
                        providerStatus = firstNonBlank(
                            pollingResponse == null ? null : pollingResponse.status(),
                            pollingResponse == null ? null : pollingResponse.state()
                        );
                    }
                } catch (AiParsingException e) {
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        new ImageRequestStageException(
                            policy.stage(),
                            "Некорректный JSON в ответе статуса генерации изображения.",
                            true,
                            response.statusCode(),
                            null,
                            e
                        ),
                        () -> pollForResultUrl(runtime, plan, modelExecution, requestId, pollIteration, attempt + 1, useHistoryEndpoint)
                    );
                }

                if (url != null && !url.isBlank()) {
                    return CompletableFuture.completedFuture(url);
                }

                if (isTerminalFailedStatus(providerStatus)) {
                    return CompletableFuture.failedFuture(new ImageRequestStageException(
                        policy.stage(),
                        "Генерация завершилась ошибкой у провайдера.",
                        false,
                        response.statusCode(),
                        providerStatus,
                        null
                    ));
                }
                if (useHistoryEndpoint && isTerminalCompletedStatus(providerStatus)) {
                    return CompletableFuture.failedFuture(new ImageRequestStageException(
                        policy.stage(),
                        "Polza history endpoint подтвердил завершение генерации, но не вернул URL результата.",
                        false,
                        response.statusCode(),
                        providerStatus,
                        null
                    ));
                }

                long delayMs = nextPollDelayMs(plan.config(), pollIteration + 1, runtime);
                Map<String, String> waitMetadata = metadata(
                    "pollIteration", String.valueOf(pollIteration + 1),
                    "nextPollInMs", String.valueOf(delayMs),
                    "providerStatus", sanitizeId(providerStatus),
                    "endpointType", useHistoryEndpoint ? "history" : "media",
                    "stage", policy.stage().code()
                );
                return delayWithHeartbeat(
                    runtime,
                    plan.config(),
                    requestId,
                    ImageRequestState.POLLING,
                    modelExecution.options().model(),
                    delayMs,
                    attempt,
                    policy.maxAttempts(),
                    "Изображение ещё готовится. Продолжаю ждать.",
                    waitMetadata
                ).thenCompose(ignored -> pollForResultUrl(
                    runtime,
                    plan,
                    modelExecution,
                    requestId,
                    pollIteration + 1,
                    attempt,
                    useHistoryEndpoint
                ));
            }))
            .thenCompose((CompletableFuture<String> next) -> next);
    }

    private CompletableFuture<Path> downloadAndSaveImage(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        ImageModelExecution modelExecution,
        String url,
        String requestId,
        int attempt
    ) {
        CompletableFuture<Path> cancellation = interruptedFutureIfNeeded(runtime);
        if (cancellation != null) {
            return cancellation;
        }
        CompletableFuture<Path> budget = budgetExceededFutureIfNeeded(runtime, plan.config());
        if (budget != null) {
            return budget;
        }

        ImageStageRetryPolicy policy = plan.config().downloadPolicy();
        publishState(
            runtime,
            requestId,
            ImageRequestState.DOWNLOADING,
            modelExecution.options().model(),
            attempt == 1
                ? "Скачиваю готовое изображение."
                : "Повторно скачиваю готовое изображение.",
            attempt,
            policy.maxAttempts(),
            metadata(
                "remoteUrl", url,
                "stage", policy.stage().code()
            )
        );

        HttpRequest.Builder requestBuilder = newRequestBuilder(URI.create(url), runtime, plan.config())
            .header("Accept", "image/*");
        if (plan.apiKey() != null && !plan.apiKey().isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + plan.apiKey().trim());
        }

        HttpRequest request = requestBuilder.GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .<CompletableFuture<Path>>handle(AsyncContext.withMdcBiFunction((response, error) -> {
                if (error != null) {
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        error,
                        () -> downloadAndSaveImage(runtime, plan, modelExecution, url, requestId, attempt + 1)
                    );
                }
                if (!AiApiUtils.isSuccessfulStatus(response.statusCode())) {
                    ImageRequestStageException statusFailure = stageHttpFailure(
                        policy.stage(),
                        response.statusCode(),
                        "Ошибка скачивания (" + response.statusCode() + "): " + url
                    );
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        statusFailure,
                        () -> downloadAndSaveImage(runtime, plan, modelExecution, url, requestId, attempt + 1)
                    );
                }

                byte[] bytes = response.body();
                if (bytes == null || bytes.length == 0) {
                    return retryStageOrFail(
                        runtime,
                        plan,
                        requestId,
                        modelExecution.options().model(),
                        policy,
                        attempt,
                        true,
                        new ImageRequestStageException(
                            policy.stage(),
                            "Пустой ответ при скачивании изображения.",
                            true,
                            response.statusCode(),
                            null,
                            null
                        ),
                        () -> downloadAndSaveImage(runtime, plan, modelExecution, url, requestId, attempt + 1)
                    );
                }

                String contentType = response.headers().firstValue("Content-Type").orElse("");
                String ext = guessExtension(url, contentType);
                Path imagesDir = DataPathManager.getImagesDirectory();
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String suffix = requestId.length() > 8 ? requestId.substring(0, 8) : requestId;
                Path out = imagesDir.resolve("img_" + ts + "_" + suffix + "." + ext);

                try {
                    publishState(
                        runtime,
                        requestId,
                        ImageRequestState.SAVING,
                        modelExecution.options().model(),
                        "Сохраняю изображение на диск.",
                        attempt,
                        policy.maxAttempts(),
                        metadata(
                            "remoteUrl", url,
                            "contentType", contentType,
                            "bytes", String.valueOf(bytes.length),
                            "stage", policy.stage().code()
                        )
                    );
                    Files.createDirectories(imagesDir);
                    Files.write(out, bytes);
                    return CompletableFuture.completedFuture(out.toAbsolutePath());
                } catch (Exception e) {
                    return CompletableFuture.failedFuture(new ImageRequestStageException(
                        policy.stage(),
                        "Не удалось сохранить изображение: " + e.getMessage(),
                        false,
                        null,
                        null,
                        e
                    ));
                }
            }))
            .thenCompose((CompletableFuture<Path> next) -> next);
    }

    private <T> CompletableFuture<T> retryStageOrFail(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        String requestId,
        String model,
        ImageStageRetryPolicy policy,
        int attempt,
        boolean resumeAfterRetry,
        Throwable error,
        Supplier<CompletableFuture<T>> nextAttemptSupplier
    ) {
        Throwable actual = AsyncContext.unwrap(error);
        if (actual instanceof CancellationException) {
            return CompletableFuture.failedFuture(actual);
        }

        ImageRequestStageException stageException = toStageException(policy.stage(), actual);
        if (!stageException.retryable() || attempt >= policy.maxAttempts()) {
            return CompletableFuture.failedFuture(stageException);
        }

        long delayMs = nextRetryDelayMs(plan.config(), attempt + 1, runtime);
        if (delayMs <= 0L) {
            return CompletableFuture.failedFuture(new TimeoutException("Исчерпан бюджет генерации изображения."));
        }

        Map<String, String> retryMetadata = mergeMetadata(
            stageFailureMetadata(stageException),
            "stage", policy.stage().code(),
            "retryDelayMs", String.valueOf(delayMs)
        );
        publishState(
            runtime,
            requestId,
            ImageRequestState.RETRYING,
            model,
            retryMessage(policy.stage(), attempt + 1, policy.maxAttempts()),
            attempt + 1,
            policy.maxAttempts(),
            retryMetadata
        );

        return delayWithHeartbeat(
            runtime,
            plan.config(),
            requestId,
            ImageRequestState.RETRYING,
            model,
            delayMs,
            attempt + 1,
            policy.maxAttempts(),
            "Ожидаю повторную попытку после временной ошибки.",
            retryMetadata
        ).thenCompose(ignored -> {
            if (resumeAfterRetry) {
                publishState(
                    runtime,
                    requestId,
                    ImageRequestState.RESUMING,
                    model,
                    resumeMessage(policy.stage()),
                    attempt + 1,
                    policy.maxAttempts(),
                    metadata(
                        "stage", policy.stage().code(),
                        "resume", "true"
                    )
                );
            }
            return nextAttemptSupplier.get();
        });
    }

    private CompletableFuture<ImageGenerationResult> maybeFallbackToNextModel(
        ImageRequestRuntime runtime,
        ImageExecutionPlan plan,
        int modelIndex,
        Throwable error
    ) {
        if (error instanceof CancellationException || error instanceof TimeoutException) {
            return null;
        }
        if (!plan.config().fallbackEnabled()) {
            return null;
        }
        if (modelIndex + 1 >= plan.candidateModels().size()) {
            return null;
        }
        if (remainingBudgetMs(runtime, plan.config()) <= 1_000L) {
            return null;
        }
        return executeWithModelFallback(runtime, plan, modelIndex + 1);
    }

    private void publishFallbackTransition(
        ImageRequestRuntime runtime,
        String previousModel,
        ImageModelExecution modelExecution,
        int maxAttempts
    ) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("fromModel", previousModel);
        metadata.put("toModel", modelExecution.options().model());
        metadata.put("stage", "fallback");
        String requestId = sanitizeId(runtime.providerRequestId().get());
        if (!requestId.isBlank()) {
            metadata.put("previousRequestId", requestId);
        }
        if (!modelExecution.adjustmentSummary().isBlank()) {
            metadata.put("capabilityAdjustments", modelExecution.adjustmentSummary());
        }
        publishState(
            runtime,
            "",
            ImageRequestState.FALLBACK_MODEL,
            modelExecution.options().model(),
            modelExecution.adjustmentSummary().isBlank()
                ? "Переключаюсь на резервную модель изображения."
                : "Переключаюсь на резервную модель изображения и адаптирую параметры.",
            1,
            maxAttempts,
            metadata
        );
    }

    private ImageExecutionPlan buildExecutionPlan(
        String prompt,
        ImageGenerationOptions originalOptions,
        String initialModel,
        String apiKey,
        String imagesBaseUrl,
        ImageExecutionConfig config
    ) {
        List<String> candidateModels = new ArrayList<>();
        candidateModels.add(normalizeModel(initialModel));
        if (config.fallbackEnabled()) {
            for (String fallbackModel : config.fallbackModels()) {
                String normalized = normalizeModel(fallbackModel);
                if (!normalized.isBlank() && !candidateModels.contains(normalized)) {
                    candidateModels.add(normalized);
                }
            }
        }

        ImageGenerationOptions preservedOptions = new ImageGenerationOptions(
            normalizeModel(initialModel),
            sanitizeId(originalOptions == null ? null : originalOptions.size()),
            sanitizeId(originalOptions == null ? null : originalOptions.aspectRatio()),
            sanitizeId(originalOptions == null ? null : originalOptions.resolution()),
            sanitizeId(originalOptions == null ? null : originalOptions.quality()),
            sanitizeId(originalOptions == null ? null : originalOptions.outputFormat()),
            sanitizeId(originalOptions == null ? null : originalOptions.strength()),
            sanitizeId(originalOptions == null ? null : originalOptions.guidanceScale())
        );
        return new ImageExecutionPlan(
            prompt,
            preservedOptions,
            apiKey,
            imagesBaseUrl,
            config,
            List.copyOf(candidateModels)
        );
    }

    private ImageExecutionConfig loadExecutionConfig() {
        AiResiliencePolicy basePolicy = ConfigManager.getAiResiliencePolicy();
        return new ImageExecutionConfig(
            Duration.ofMillis(ConfigManager.getImageRequestBudgetMs()),
            Duration.ofMillis(ConfigManager.getImageRequestHeartbeatIntervalMs()),
            basePolicy.getReadTimeout(),
            new AiRetryDelayStrategy(
                Duration.ofMillis(ConfigManager.getImageRetryBaseDelayMs()),
                Duration.ofMillis(ConfigManager.getImageRetryMaxDelayMs()),
                AiConfigDefaults.RETRY_JITTER_RATIO
            ),
            new AiRetryDelayStrategy(
                Duration.ofMillis(ConfigManager.getImagePollInitialDelayMs()),
                Duration.ofMillis(ConfigManager.getImagePollMaxDelayMs()),
                ConfigManager.getImagePollJitterRatio()
            ),
            new ImageStageRetryPolicy(ImageRequestStage.SUBMIT, ConfigManager.getImageSubmitMaxAttempts()),
            new ImageStageRetryPolicy(ImageRequestStage.POLL, ConfigManager.getImagePollMaxAttempts()),
            new ImageStageRetryPolicy(ImageRequestStage.DOWNLOAD, ConfigManager.getImageDownloadMaxAttempts()),
            ConfigManager.isImageFallbackModeEnabled(),
            ConfigManager.getImageFallbackModels()
        );
    }

    private ImageModelExecution resolveModelExecution(ImageGenerationOptions sourceOptions, String targetModel) {
        String requestedSize = sanitizeId(sourceOptions == null ? null : sourceOptions.size());
        String requestedAspectRatio = sanitizeId(sourceOptions == null ? null : sourceOptions.aspectRatio());
        String requestedResolution = sanitizeId(sourceOptions == null ? null : sourceOptions.resolution());
        String requestedQuality = sanitizeId(sourceOptions == null ? null : sourceOptions.quality());
        String requestedOutputFormat = sanitizeId(sourceOptions == null ? null : sourceOptions.outputFormat());
        String requestedStrength = sanitizeId(sourceOptions == null ? null : sourceOptions.strength());
        String requestedGuidanceScale = sanitizeId(sourceOptions == null ? null : sourceOptions.guidanceScale());

        String candidateSize = !requestedSize.isBlank() ? requestedSize : requestedAspectRatio;
        String candidateAspectRatio = !requestedAspectRatio.isBlank() ? requestedAspectRatio : requestedSize;
        ImageConfigResolution resolved = capabilityRegistry.resolveConfiguredOptions(
            targetModel,
            candidateSize,
            candidateAspectRatio,
            requestedResolution,
            requestedQuality,
            requestedOutputFormat,
            requestedStrength,
            requestedGuidanceScale
        );
        return new ImageModelExecution(
            resolved.options(),
            resolved.hasIssues() ? resolved.summary() : ""
        );
    }

    private HttpRequest.Builder newRequestBuilder(
        URI uri,
        ImageRequestRuntime runtime,
        ImageExecutionConfig config
    ) {
        return HttpRequest.newBuilder()
            .uri(uri)
            .timeout(effectiveAttemptTimeout(runtime, config));
    }

    private Duration effectiveAttemptTimeout(ImageRequestRuntime runtime, ImageExecutionConfig config) {
        long remainingBudgetMs = remainingBudgetMs(runtime, config);
        if (remainingBudgetMs <= 0L) {
            return MIN_ATTEMPT_TIMEOUT;
        }
        long timeoutMs = Math.min(config.perRequestTimeout().toMillis(), remainingBudgetMs);
        return Duration.ofMillis(Math.max(MIN_ATTEMPT_TIMEOUT.toMillis(), timeoutMs));
    }

    private long remainingBudgetMs(ImageRequestRuntime runtime, ImageExecutionConfig config) {
        if (runtime == null || config == null) {
            return Long.MAX_VALUE;
        }
        long deadline = runtime.startedAt() + config.requestBudget().toMillis();
        return Math.max(0L, deadline - System.currentTimeMillis());
    }

    private long nextRetryDelayMs(ImageExecutionConfig config, int nextAttempt, ImageRequestRuntime runtime) {
        long remaining = remainingBudgetMs(runtime, config);
        if (remaining <= 1L) {
            return 0L;
        }
        long delay = config.retryDelayStrategy().calculateDelay(nextAttempt).toMillis();
        return Math.min(delay, Math.max(0L, remaining - 1L));
    }

    private long nextPollDelayMs(ImageExecutionConfig config, int pollIteration, ImageRequestRuntime runtime) {
        long remaining = remainingBudgetMs(runtime, config);
        if (remaining <= 1L) {
            return 0L;
        }
        long delay = config.pollDelayStrategy().calculateDelay(Math.max(1, pollIteration)).toMillis();
        return Math.min(delay, Math.max(0L, remaining - 1L));
    }

    private CompletableFuture<Void> delayWithHeartbeat(
        ImageRequestRuntime runtime,
        ImageExecutionConfig config,
        String requestId,
        ImageRequestState state,
        String model,
        long totalDelayMs,
        int attempt,
        int maxAttempts,
        String message,
        Map<String, String> metadata
    ) {
        long normalizedDelay = Math.max(0L, totalDelayMs);
        if (normalizedDelay == 0L) {
            return CompletableFuture.completedFuture(null);
        }
        long remainingBudget = remainingBudgetMs(runtime, config);
        if (remainingBudget <= 1L) {
            return CompletableFuture.failedFuture(new TimeoutException("Исчерпан бюджет генерации изображения."));
        }
        long boundedDelay = Math.min(normalizedDelay, Math.max(0L, remainingBudget - 1L));
        return delayWithHeartbeatSlice(
            runtime,
            config,
            requestId,
            state,
            model,
            boundedDelay,
            attempt,
            maxAttempts,
            message,
            metadata,
            1
        );
    }

    private CompletableFuture<Void> delayWithHeartbeatSlice(
        ImageRequestRuntime runtime,
        ImageExecutionConfig config,
        String requestId,
        ImageRequestState state,
        String model,
        long remainingDelayMs,
        int attempt,
        int maxAttempts,
        String message,
        Map<String, String> metadata,
        int heartbeatIndex
    ) {
        CompletableFuture<Void> cancellation = interruptedFutureIfNeeded(runtime);
        if (cancellation != null) {
            return cancellation;
        }
        if (remainingDelayMs <= 0L) {
            return CompletableFuture.completedFuture(null);
        }

        long remainingBudget = remainingBudgetMs(runtime, config);
        if (remainingBudget <= 1L) {
            return CompletableFuture.failedFuture(new TimeoutException("Исчерпан бюджет генерации изображения."));
        }

        long slice = Math.min(
            remainingDelayMs,
            Math.min(config.heartbeatInterval().toMillis(), Math.max(1L, remainingBudget - 1L))
        );
        return AsyncContext.supplyAsync(
            () -> null,
            CompletableFuture.delayedExecutor(slice, TimeUnit.MILLISECONDS)
        ).thenCompose(ignored -> {
            CompletableFuture<Void> cancelled = interruptedFutureIfNeeded(runtime);
            if (cancelled != null) {
                return cancelled;
            }
            long nextRemaining = remainingDelayMs - slice;
            if (nextRemaining <= 0L) {
                return CompletableFuture.completedFuture(null);
            }
            publishState(
                runtime,
                requestId,
                state,
                model,
                message,
                attempt,
                maxAttempts,
                mergeMetadata(
                    metadata,
                    "heartbeat", "true",
                    "heartbeatIndex", String.valueOf(heartbeatIndex)
                )
            );
            return delayWithHeartbeatSlice(
                runtime,
                config,
                requestId,
                state,
                model,
                nextRemaining,
                attempt,
                maxAttempts,
                message,
                metadata,
                heartbeatIndex + 1
            );
        });
    }

    private <T> CompletableFuture<T> budgetExceededFutureIfNeeded(
        ImageRequestRuntime runtime,
        ImageExecutionConfig config
    ) {
        if (remainingBudgetMs(runtime, config) > 0L) {
            return null;
        }
        return CompletableFuture.failedFuture(new TimeoutException("Исчерпан бюджет генерации изображения."));
    }

    private ImageRequestStageException stageHttpFailure(
        ImageRequestStage stage,
        int statusCode,
        String message
    ) {
        AiHttpErrorClassifier.ErrorCategory category = AiHttpErrorClassifier.classifyHttpStatus(statusCode);
        boolean retryable = category == AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE;
        return new ImageRequestStageException(stage, message, retryable, statusCode, null, null);
    }

    private ImageRequestStageException toStageException(ImageRequestStage stage, Throwable error) {
        if (error instanceof ImageRequestStageException stageException) {
            return stageException;
        }
        Throwable actual = AsyncContext.unwrap(error);
        AiHttpErrorClassifier.ErrorCategory category = AiHttpErrorClassifier.classifyException(actual);
        boolean retryable = category != AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST;
        String message = actual == null || actual.getMessage() == null || actual.getMessage().isBlank()
            ? "Временная ошибка на этапе " + stage.userFacingLabel() + "."
            : actual.getMessage().trim();
        return new ImageRequestStageException(stage, message, retryable, null, null, actual);
    }

    private Map<String, String> stageFailureMetadata(ImageRequestStageException exception) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        if (exception.httpStatus() != null) {
            metadata.put("httpStatus", String.valueOf(exception.httpStatus()));
        }
        if (exception.providerStatus() != null && !exception.providerStatus().isBlank()) {
            metadata.put("providerStatus", exception.providerStatus());
        }
        return metadata;
    }

    private String retryMessage(ImageRequestStage stage, int nextAttempt, int maxAttempts) {
        return switch (stage) {
            case SUBMIT -> "Временная ошибка на этапе отправки. Повторяю попытку %d из %d."
                .formatted(nextAttempt, maxAttempts);
            case POLL -> "Временная ошибка при ожидании результата. Повторяю попытку %d из %d."
                .formatted(nextAttempt, maxAttempts);
            case DOWNLOAD -> "Временная ошибка при скачивании изображения. Повторяю попытку %d из %d."
                .formatted(nextAttempt, maxAttempts);
        };
    }

    private String resumeMessage(ImageRequestStage stage) {
        return switch (stage) {
            case SUBMIT -> "Продолжаю отправку запроса на генерацию изображения.";
            case POLL -> "Восстанавливаю ожидание уже отправленного image-запроса.";
            case DOWNLOAD -> "Восстанавливаю скачивание готового изображения.";
        };
    }

    private String buildRequestBody(String prompt, ImageValidatedOptions options) {
        ImageModelCapability capability = capabilityRegistry.resolveCapability(options.model());
        String model = capability.model();
        String escapedPrompt = AiApiUtils.escapeJson(prompt.trim());
        StringBuilder json = new StringBuilder();
        json.append("{\"model\":\"").append(model).append("\",");
        json.append("\"prompt\":\"").append(escapedPrompt).append("\"");

        capability.fixedPayloadFields().forEach((key, value) -> json
            .append(",\"")
            .append(AiApiUtils.escapeJson(key))
            .append("\":\"")
            .append(AiApiUtils.escapeJson(value))
            .append("\""));

        appendCapabilityField(json, capability, ImageCapabilityField.SIZE, options.size());
        appendCapabilityField(json, capability, ImageCapabilityField.ASPECT_RATIO, options.aspectRatio());
        appendCapabilityField(json, capability, ImageCapabilityField.RESOLUTION, options.resolution());
        appendCapabilityField(json, capability, ImageCapabilityField.QUALITY, options.quality());
        appendCapabilityField(json, capability, ImageCapabilityField.OUTPUT_FORMAT, options.outputFormat());
        appendCapabilityField(json, capability, ImageCapabilityField.STRENGTH, options.strength());
        appendCapabilityField(json, capability, ImageCapabilityField.GUIDANCE_SCALE, options.guidanceScale());
        json.append("}");
        return json.toString();
    }

    private String buildMediaRequestBody(String prompt, ImageValidatedOptions options) {
        ImageModelCapability capability = capabilityRegistry.resolveCapability(options.model());
        StringBuilder json = new StringBuilder();
        json.append("{\"model\":\"")
            .append(AiApiUtils.escapeJson(capability.model()))
            .append("\",\"input\":{")
            .append("\"prompt\":\"")
            .append(AiApiUtils.escapeJson(prompt.trim()))
            .append("\"");

        appendMediaCapabilityField(json, capability, ImageCapabilityField.SIZE, options.size());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.ASPECT_RATIO, options.aspectRatio());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.RESOLUTION, options.resolution());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.QUALITY, options.quality());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.OUTPUT_FORMAT, options.outputFormat());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.STRENGTH, options.strength());
        appendMediaCapabilityField(json, capability, ImageCapabilityField.GUIDANCE_SCALE, options.guidanceScale());
        json.append("},\"async\":true}");
        return json.toString();
    }

    private String resolveImagesBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.isEmpty()) {
            normalized = "https://api.polza.ai/api/v1";
        }
        normalized = trimTrailingSlash(normalized);
        normalized = stripDeprecatedImageEndpoint(normalized, "/images/generations");
        normalized = stripDeprecatedImageEndpoint(normalized, "/history/generations");
        normalized = stripDeprecatedImageEndpoint(normalized, "/media");
        normalized = stripDeprecatedImageEndpoint(normalized, "/images");
        return normalized;
    }

    private String stripDeprecatedImageEndpoint(String value, String suffix) {
        if (value == null || suffix == null || suffix.isBlank()) {
            return trimTrailingSlash(value);
        }
        String normalized = trimTrailingSlash(value);
        if (normalized.endsWith(suffix)) {
            normalized = normalized.substring(0, normalized.length() - suffix.length());
        }
        return trimTrailingSlash(normalized);
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private void appendMediaCapabilityField(
        StringBuilder json,
        ImageModelCapability capability,
        ImageCapabilityField field,
        String value
    ) {
        if (!capability.supports(field)) {
            return;
        }
        if (value == null || value.isBlank()) {
            return;
        }
        json.append(",\"")
            .append(AiApiUtils.escapeJson(capability.transportFieldName(field)))
            .append("\":\"")
            .append(AiApiUtils.escapeJson(value))
            .append("\"");
    }

    private boolean isTerminalFailedStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim().toLowerCase(Locale.ROOT);
        return s.contains("fail") || s.contains("error") || s.contains("rejected");
    }

    private boolean isTerminalCompletedStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("complete")
            || normalized.contains("success")
            || normalized.contains("done");
    }

    private boolean shouldTryHistoryPollingFallback(int statusCode) {
        return statusCode == 400
            || statusCode == 404
            || statusCode == 405
            || statusCode == 410;
    }

    private String guessExtension(String url, String contentType) {
        String byType = guessExtensionFromContentType(contentType);
        if (byType != null) {
            return byType;
        }
        if (url != null) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (lower.contains("?")) {
                lower = lower.substring(0, lower.indexOf('?'));
            }
            if (lower.endsWith(".png")) return "png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpg";
            if (lower.endsWith(".webp")) return "webp";
        }
        return "png";
    }

    private String guessExtensionFromContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("image/png")) return "png";
        if (lower.contains("image/jpeg") || lower.contains("image/jpg")) return "jpg";
        if (lower.contains("image/webp")) return "webp";
        return null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private void publishState(
        ImageRequestRuntime runtime,
        String requestId,
        ImageRequestState state,
        String model,
        String message,
        Map<String, String> metadata
    ) {
        int attempt = runtime == null ? 1 : Math.max(1, runtime.attempt().get());
        int maxAttempts = runtime == null ? 1 : Math.max(attempt, runtime.maxAttempts().get());
        publishState(runtime, requestId, state, model, message, attempt, maxAttempts, metadata);
    }

    private void publishState(
        ImageRequestRuntime runtime,
        String requestId,
        ImageRequestState state,
        String model,
        String message,
        int attempt,
        int maxAttempts,
        Map<String, String> metadata
    ) {
        if (runtime == null) {
            return;
        }
        if (state != ImageRequestState.PAUSED
            && !state.isTerminal()
            && (runtime.terminalPublished().get() || runtime.cancellationRequested().get() || runtime.pauseRequested().get())) {
            return;
        }
        runtime.attempt().set(Math.max(1, attempt));
        runtime.maxAttempts().set(Math.max(1, maxAttempts));
        syncPersistedJobState(runtime, requestId, state, model, message, attempt, metadata);
        ImageRequestProgress progress = new ImageRequestProgress(
            System.currentTimeMillis() - runtime.startedAt(),
            attempt,
            maxAttempts,
            state.isTerminal()
        );
        lifecyclePublisher.publish(new ImageRequestEvent(
            runtime.jobId(),
            requestId,
            runtime.conversationId(),
            state,
            model,
            message,
            progress,
            Instant.now(),
            metadata
        ));
    }

    private void publishTerminalOnce(
        ImageRequestRuntime runtime,
        String requestId,
        ImageRequestState state,
        String model,
        String message,
        Map<String, String> metadata
    ) {
        if (!state.isTerminal()) {
            publishState(runtime, requestId, state, model, message, metadata);
            return;
        }
        if (runtime != null && runtime.terminalPublished().compareAndSet(false, true)) {
            publishState(runtime, requestId, state, model, message, metadata);
        }
    }

    private <T> CompletableFuture<T> interruptedFutureIfNeeded(ImageRequestRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        if (runtime.pauseRequested().get()) {
            return CompletableFuture.failedFuture(new ImageJobPausedException("Image request paused."));
        }
        if (!runtime.cancellationRequested().get() && !runtime.terminalPublished().get()) {
            return null;
        }
        return CompletableFuture.failedFuture(new CancellationException("Image request cancelled."));
    }

    private Map<String, String> metadata(String... keyValues) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        if (keyValues == null || keyValues.length == 0) {
            return metadata;
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = keyValues[i];
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = keyValues[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            metadata.put(key.trim(), value.trim());
        }
        return metadata;
    }

    private Map<String, String> mergeMetadata(Map<String, String> base, String... keyValues) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (base != null) {
            merged.putAll(base);
        }
        merged.putAll(metadata(keyValues));
        return merged;
    }

    private void syncPersistedJobState(
        ImageRequestRuntime runtime,
        String requestId,
        ImageRequestState state,
        String model,
        String message,
        int attempt,
        Map<String, String> metadata
    ) {
        if (runtime == null || runtime.jobId() == null || runtime.jobId().isBlank()) {
            return;
        }
        jobManager.updateJob(runtime.jobId(), job -> {
            job.setUpdatedAt(System.currentTimeMillis());
            job.setAttempt(attempt);
            if (model != null && !model.isBlank()) {
                job.setActiveModel(model);
            }
            if (requestId != null && !requestId.isBlank()) {
                job.setRequestId(requestId);
            }
            if (message != null && !message.isBlank()) {
                job.setLastMessage(message);
            }
            if (metadata != null) {
                String remoteUrl = metadata.get("remoteUrl");
                if (remoteUrl != null && !remoteUrl.isBlank()) {
                    job.setRemoteUrl(remoteUrl);
                }
                String savedPath = metadata.get("savedPath");
                if (savedPath != null && !savedPath.isBlank()) {
                    job.setSavedPath(savedPath);
                }
            }
            ImageJobState mappedState = mapJobState(state, job);
            if (mappedState != null) {
                job.setState(mappedState);
            }
            if (state == ImageRequestState.DONE) {
                job.setLastError("");
                job.setPauseRequested(false);
                job.setCancelRequested(false);
            } else if (state == ImageRequestState.FAILED) {
                job.setLastError(message);
                job.setPauseRequested(false);
                job.setCancelRequested(false);
            } else if (state == ImageRequestState.CANCELLED) {
                job.setPauseRequested(false);
                job.setCancelRequested(false);
            } else if (state == ImageRequestState.PAUSED) {
                job.setPauseRequested(false);
                job.setCancelRequested(false);
            }
        });
    }

    private ImageJobState mapJobState(ImageRequestState state, ImageJobSnapshot job) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case QUEUED -> ImageJobState.QUEUED;
            case SENDING -> ImageJobState.SUBMITTING;
            case PROVIDER_ACCEPTED -> ImageJobState.SUBMITTED;
            case POLLING, RESUMING, RETRYING -> {
                if (job != null && job.getRemoteUrl() != null && !job.getRemoteUrl().isBlank()) {
                    yield ImageJobState.DOWNLOADING;
                }
                yield ImageJobState.POLLING;
            }
            case DOWNLOADING -> ImageJobState.DOWNLOADING;
            case SAVING -> ImageJobState.SAVING;
            case DONE -> ImageJobState.DONE;
            case FAILED -> ImageJobState.FAILED;
            case CANCELLED -> ImageJobState.CANCELLED;
            case PAUSED -> ImageJobState.PAUSED;
            case FALLBACK_MODEL -> null;
        };
    }

    private String normalizeFailureMessage(Throwable error) {
        if (error == null) {
            return "Генерация изображения завершилась ошибкой.";
        }
        if (error instanceof CancellationException) {
            return "Генерация изображения отменена.";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Генерация изображения завершилась ошибкой.";
        }
        return message.trim();
    }

    private String normalizeModel(String model) {
        return ImageGenConfigDefaults.isSupportedImageModel(model)
            ? ImageGenConfigDefaults.normalizeImageModel(model)
            : sanitizeId(model);
    }

    private ImageValidatedOptions validateRequestOptions(ImageGenerationOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Параметры генерации изображения не заданы.");
        }
        return ImageGenConfigDefaults.validateImageOptions(
            options.model(),
            options.size(),
            options.aspectRatio(),
            options.resolution(),
            options.quality(),
            options.outputFormat(),
            options.strength(),
            options.guidanceScale()
        );
    }

    private void appendCapabilityField(
        StringBuilder json,
        ImageModelCapability capability,
        ImageCapabilityField field,
        String value
    ) {
        if (!capability.supports(field)) {
            return;
        }
        if (value == null || value.isBlank()) {
            return;
        }
        json.append(",\"")
            .append(AiApiUtils.escapeJson(capability.transportFieldName(field)))
            .append("\":\"")
            .append(AiApiUtils.escapeJson(value))
            .append("\"");
    }

    private String sanitizeJobId(String jobId) {
        String normalized = sanitizeId(jobId);
        return normalized.isBlank() ? UUID.randomUUID().toString() : normalized;
    }

    private String sanitizeId(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? "" : normalized;
    }

    private HttpClient createTrustAllClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());
            AiResiliencePolicy policy = ConfigManager.getAiResiliencePolicy();
            return HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(policy.getConnectTimeout())
                .build();
        } catch (Exception e) {
            return HttpClient.newHttpClient();
        }
    }

    private record ImageRequestRuntime(
        String jobId,
        String conversationId,
        String requestedModel,
        long startedAt,
        AtomicBoolean terminalPublished,
        AtomicBoolean cancellationRequested,
        AtomicBoolean pauseRequested,
        AtomicInteger attempt,
        AtomicInteger maxAttempts,
        AtomicReference<String> providerRequestId,
        AtomicReference<String> activeModel
    ) {
        private ImageRequestRuntime(ImageJobSnapshot snapshot) {
            this(
                snapshot == null ? "" : sanitizeValue(snapshot.getJobId()),
                snapshot == null ? "" : sanitizeValue(snapshot.getConversationId()),
                snapshot == null ? "" : sanitizeValue(snapshot.getRequestedModel()),
                snapshot != null && snapshot.getCreatedAt() > 0L ? snapshot.getCreatedAt() : System.currentTimeMillis(),
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                new AtomicInteger(snapshot == null ? 1 : Math.max(1, snapshot.getAttempt())),
                new AtomicInteger(snapshot == null ? 1 : Math.max(1, snapshot.getAttempt())),
                new AtomicReference<>(snapshot == null ? "" : sanitizeValue(snapshot.getRequestId())),
                new AtomicReference<>(preferredModel(snapshot))
            );
        }

        private static String sanitizeValue(String value) {
            if (value == null) {
                return "";
            }
            String normalized = value.trim();
            return normalized.isEmpty() ? "" : normalized;
        }

        private static String preferredModel(ImageJobSnapshot snapshot) {
            if (snapshot == null) {
                return "";
            }
            String active = sanitizeValue(snapshot.getActiveModel());
            if (!active.isBlank()) {
                return active;
            }
            return sanitizeValue(snapshot.getRequestedModel());
        }
    }

    private record ImageExecutionPlan(
        String prompt,
        ImageGenerationOptions originalOptions,
        String apiKey,
        String imagesBaseUrl,
        ImageExecutionConfig config,
        List<String> candidateModels
    ) {
    }

    private record ImageExecutionConfig(
        Duration requestBudget,
        Duration heartbeatInterval,
        Duration perRequestTimeout,
        AiRetryDelayStrategy retryDelayStrategy,
        AiRetryDelayStrategy pollDelayStrategy,
        ImageStageRetryPolicy submitPolicy,
        ImageStageRetryPolicy pollPolicy,
        ImageStageRetryPolicy downloadPolicy,
        boolean fallbackEnabled,
        List<String> fallbackModels
    ) {
    }

    private enum ImageJobStartMode {
        NEW("new"),
        USER_RETRY("user-retry"),
        USER_RESUME("user-resume"),
        AUTO_RESUME("auto-resume");

        private final String code;

        ImageJobStartMode(String code) {
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private record ImageStageRetryPolicy(
        ImageRequestStage stage,
        int maxAttempts
    ) {
    }

    private record ImageModelExecution(
        ImageValidatedOptions options,
        String adjustmentSummary
    ) {
    }

    public static final class ImageJobPausedException extends CancellationException {
        public ImageJobPausedException(String message) {
            super(message);
        }
    }

    private enum ImageRequestStage {
        SUBMIT("submit", "отправки"),
        POLL("poll", "ожидания"),
        DOWNLOAD("download", "скачивания");

        private final String code;
        private final String userFacingLabel;

        ImageRequestStage(String code, String userFacingLabel) {
            this.code = code;
            this.userFacingLabel = userFacingLabel;
        }

        private String code() {
            return code;
        }

        private String userFacingLabel() {
            return userFacingLabel;
        }
    }

    private static final class ImageRequestStageException extends RuntimeException {
        private final ImageRequestStage stage;
        private final boolean retryable;
        private final Integer httpStatus;
        private final String providerStatus;

        private ImageRequestStageException(
            ImageRequestStage stage,
            String message,
            boolean retryable,
            Integer httpStatus,
            String providerStatus,
            Throwable cause
        ) {
            super(message, cause);
            this.stage = stage;
            this.retryable = retryable;
            this.httpStatus = httpStatus;
            this.providerStatus = providerStatus;
        }

        private ImageRequestStage stage() {
            return stage;
        }

        private boolean retryable() {
            return retryable;
        }

        private Integer httpStatus() {
            return httpStatus;
        }

        private String providerStatus() {
            return providerStatus;
        }
    }
}
