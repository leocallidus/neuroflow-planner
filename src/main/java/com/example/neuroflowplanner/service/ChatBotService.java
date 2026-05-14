package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiImageDataUrl;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiTextModelParameterResolver;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.AiStreamChunk;
import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.ai.media.AiMediaInputKind;
import com.example.neuroflowplanner.ai.media.AiMediaTypeDescriptor;
import com.example.neuroflowplanner.ai.media.AiMediaTypeRegistry;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.ChatContextState;
import com.example.neuroflowplanner.service.chatflow.ChatRequestEvent;
import com.example.neuroflowplanner.service.chatflow.ChatRequestEventPublisher;
import com.example.neuroflowplanner.service.chatflow.ChatRequestProgress;
import com.example.neuroflowplanner.service.chatflow.ChatResponseChunk;
import com.example.neuroflowplanner.service.chatflow.ChatResponseChunkPublisher;
import com.example.neuroflowplanner.service.chatflow.ChatRequestState;
import com.example.neuroflowplanner.service.chatflow.ChatRequestSubscription;
import com.example.neuroflowplanner.service.context.ChatContextBuildResult;
import com.example.neuroflowplanner.service.context.ChatContextCompactionService;
import com.example.neuroflowplanner.service.context.ChatContextManager;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.service.context.ChatContextSummaryTemplate;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationState;
import com.example.neuroflowplanner.service.context.ChatContextSummarizationStatus;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Service for chatbot functionality.
 * Uses AiClientFactory to send messages to the configured AI backend.
 */
public class ChatBotService {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(ChatBotService.class);
    private static final String UNKNOWN_CONVERSATION_ID = "conversation:unknown";
    private static final int CONTROLLED_PARTIAL_MIN_LENGTH = 240;
    private static final int CONTROLLED_PARTIAL_MAX_CHUNKS = 10;
    private static final long CONTROLLED_PARTIAL_CHUNK_DELAY_MS = 35L;
    private static final int CONTINUATION_OVERLAP_MAX_SCAN = 320;

    private final ChatRequestEventPublisher lifecyclePublisher = new ChatRequestEventPublisher();
    private final ChatResponseChunkPublisher responseChunkPublisher = new ChatResponseChunkPublisher();
    private final ConcurrentHashMap<String, CompletableFuture<?>> inFlightRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<ChatContextSummarizationState>> inFlightSummaries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> loadedPersistentContext = new ConcurrentHashMap<>();
    private final ChatContextManager contextManager = new ChatContextManager();
    private final ChatContextCompactionService contextCompactionService = new ChatContextCompactionService();
    private final DatabaseManager db = DatabaseManager.getInstance();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
        AsyncContext.namedThreadFactory("ChatHeartbeat", true)
    );

    public ChatBotService() {
        // No initialization needed - we use AiClientFactory
    }

    /**
     * Subscribe to chat lifecycle events.
     */
    public ChatRequestSubscription subscribeToRequestEvents(Consumer<ChatRequestEvent> listener) {
        return lifecyclePublisher.subscribe(listener);
    }

    /**
     * Returns last emitted lifecycle event (if any).
     */
    public ChatRequestEvent getLastRequestEvent() {
        return lifecyclePublisher.getLastEvent();
    }

    /**
     * Subscribe to incremental response chunks.
     */
    public ChatRequestSubscription subscribeToResponseChunks(Consumer<ChatResponseChunk> listener) {
        return responseChunkPublisher.subscribe(listener);
    }

    /**
     * Returns last emitted response chunk (if any).
     */
    public ChatResponseChunk getLastResponseChunk() {
        return responseChunkPublisher.getLastChunk();
    }

    /**
     * Attempts to cancel an in-flight request by requestId.
     */
    public boolean cancelRequest(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        CompletableFuture<?> future = inFlightRequests.get(requestId.trim());
        if (future == null || future.isDone()) {
            return false;
        }
        return future.cancel(true);
    }

    /**
     * Sends a message to the AI and returns the response.
     *
     * @param message the user's message
     * @return a CompletableFuture with the AI response text
     */
    public CompletableFuture<String> sendMessage(String message) {
        return sendMessage(UNKNOWN_CONVERSATION_ID, message);
    }

    /**
     * Sends a message to the AI in context of a conversation.
     */
    public CompletableFuture<String> sendMessage(String conversationId, String message) {
        AsyncContext.ensureRequestId();
        AiClient client = AiClientFactory.getInstance().getActiveClient();
        String requestId = AsyncContext.ensureRequestId();
        String safeConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(safeConversationId);
        String model = resolveModelLabel(client.getDefaultModel());
        int maxAttempts = resolveMaxAttempts();
        long startedAt = System.currentTimeMillis();
        AtomicBoolean terminalPublished = new AtomicBoolean(false);
        AtomicReference<ChatContextBuildResult> contextRef = new AtomicReference<>();
        AtomicReference<AiRequestOptions> preparedOptionsRef = new AtomicReference<>();
        boolean continuationEnabled = ConfigManager.isAiContinuationEnabled();
        int continuationMaxSteps = ConfigManager.getAiContinuationMaxSteps();
        int continuationMinPartialChars = ConfigManager.getAiContinuationMinPartialChars();
        AtomicInteger continuationStepsRef = new AtomicInteger(0);

        publishState(
            requestId,
            safeConversationId,
            ChatRequestState.QUEUED,
            model,
            "Запрос поставлен в очередь",
            1,
            maxAttempts,
            startedAt,
            Map.of()
        );
        publishState(
            requestId,
            safeConversationId,
            ChatRequestState.SENDING,
            model,
            "Отправляю запрос в модель",
            1,
            maxAttempts,
            startedAt,
            Map.of()
        );

        StringBuilder streamedContent = new StringBuilder();
        AtomicBoolean streamingChunkReceived = new AtomicBoolean(false);
        AtomicBoolean syntheticPartialUsed = new AtomicBoolean(false);
        AtomicInteger streamAttemptRef = new AtomicInteger(1);

        RequestHeartbeat heartbeat = startHeartbeat(requestId, safeConversationId, model, maxAttempts, startedAt);

        Consumer<AiStreamChunk> streamConsumer = chunk -> {
            if (chunk == null) {
                return;
            }
            if (chunk.done()) {
                return;
            }
            String delta = chunk.contentDelta();
            if (delta == null || delta.isBlank()) {
                return;
            }
            String responseModel = resolveModelLabel(chunk.model(), model);
            String snapshot;
            synchronized (streamedContent) {
                streamedContent.append(delta);
                snapshot = streamedContent.toString();
            }
            streamingChunkReceived.set(true);
            heartbeat.update(ChatRequestState.GENERATING, responseModel, streamAttemptRef.get());
            publishState(
                requestId,
                safeConversationId,
                ChatRequestState.GENERATING,
                responseModel,
                "Поступают части ответа",
                streamAttemptRef.get(),
                maxAttempts,
                startedAt,
                Map.of("streaming", "true")
            );
            publishResponseChunk(
                requestId,
                safeConversationId,
                responseModel,
                delta,
                snapshot,
                false,
                startedAt
            );
        };

        CompletableFuture<AiResponse> providerFuture = prepareContextForOutgoingRequest(
                requestId,
                safeConversationId,
                model,
                message,
                maxAttempts,
                startedAt,
                Map.of())
            .thenCompose(AsyncContext.withMdcFunction(context -> {
                contextRef.set(context);
                String systemPrompt = buildAssistantSystemPrompt();
                AiRequestOptions options = buildAssistantRequestOptions(client, systemPrompt, context.entries());
                preparedOptionsRef.set(options);
                publishState(
                    requestId,
                    safeConversationId,
                    ChatRequestState.WAITING_PROVIDER,
                    model,
                    "Ожидаю ответ провайдера",
                    1,
                    maxAttempts,
                    startedAt,
                    buildContextMetadata(context)
                );
                heartbeat.update(ChatRequestState.WAITING_PROVIDER, model, 1);
                return client.supportsStreaming()
                    ? client.sendChatMessageStreaming(message, options.withStream(true), streamConsumer)
                    : client.sendChatMessage(message, options.withStream(false));
            }));

        CompletableFuture<String> requestFuture = providerFuture
            .thenCompose(AsyncContext.withMdcFunction(response -> {
                int attempt = resolveAttempts(response == null ? null : response.attempts(), maxAttempts);
                streamAttemptRef.set(attempt);
                String responseModel = resolveModelLabel(response == null ? null : response.model(), model);
                if (attempt > 1) {
                    heartbeat.update(ChatRequestState.RETRYING, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.RETRYING,
                        responseModel,
                        "Ответ получен после повторной попытки",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("attemptRecovered", "true")
                    );
                }
                if (!model.isBlank() && !responseModel.isBlank() && !responseModel.equalsIgnoreCase(model)) {
                    heartbeat.update(ChatRequestState.FALLBACK_MODEL, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.FALLBACK_MODEL,
                        responseModel,
                        "Включена резервная модель",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("fallback", "true", "fromModel", model, "toModel", responseModel)
                    );
                }
                if (response != null && response.success()) {
                    String content = response.content() == null ? "" : response.content();
                    String streamedSnapshot;
                    synchronized (streamedContent) {
                        streamedSnapshot = streamedContent.toString();
                    }
                    if (streamingChunkReceived.get() && !streamedSnapshot.isBlank()) {
                        content = streamedSnapshot;
                    }
                    if (!streamingChunkReceived.get() && shouldUseControlledPartial(content)) {
                        syntheticPartialUsed.set(true);
                        publishState(
                            requestId,
                            safeConversationId,
                            ChatRequestState.GENERATING,
                            responseModel,
                            "Показываю ответ частями",
                            attempt,
                            maxAttempts,
                            startedAt,
                            Map.of("streaming", "synthetic")
                        );
                        emitControlledPartialChunks(
                            requestId,
                            safeConversationId,
                            responseModel,
                            content,
                            startedAt
                        );
                    } else if (!streamingChunkReceived.get()) {
                        publishState(
                            requestId,
                            safeConversationId,
                            ChatRequestState.GENERATING,
                            responseModel,
                            "Модель формирует ответ",
                            attempt,
                            maxAttempts,
                            startedAt,
                            Map.of()
                        );
                    }
                    heartbeat.update(ChatRequestState.POST_PROCESSING, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.POST_PROCESSING,
                        responseModel,
                        "Подготавливаю финальный ответ",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of()
                    );
                    contextManager.appendAssistantMessage(safeConversationId, content);
                    persistContextState(safeConversationId);
                    if (streamingChunkReceived.get() || syntheticPartialUsed.get()) {
                        publishTerminalResponseChunk(
                            requestId,
                            safeConversationId,
                            responseModel,
                            content,
                            syntheticPartialUsed.get(),
                            startedAt
                        );
                    }
                    publishTerminalOnce(
                        terminalPublished,
                        requestId,
                        safeConversationId,
                        ChatRequestState.DONE,
                        responseModel,
                        "Ответ готов",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of()
                    );
                    return CompletableFuture.completedFuture(content);
                }

                String errorMessage = response == null
                    ? "Пустой ответ от модели"
                    : (response.errorMessage() == null || response.errorMessage().isBlank()
                        ? "Неизвестная ошибка"
                        : response.errorMessage());
                Integer statusCode = response == null
                    ? null
                    : (response.errorCode() != null ? response.errorCode() : response.httpStatus());
                String partialSnapshot = snapshotStreamedContent(streamedContent);
                boolean shouldRouteToContinuation = continuationEnabled
                    && continuationStepsRef.get() < continuationMaxSteps
                    && partialSnapshot.length() >= continuationMinPartialChars
                    && (
                        (statusCode != null && (statusCode == 408 || statusCode == 425))
                        || isTimeoutLike(new RuntimeException(errorMessage))
                    );
                if (shouldRouteToContinuation) {
                    throw new CompletionException(new TimeoutException(errorMessage));
                }
                NormalizedFailure failure = normalizeFailure(statusCode, null, errorMessage, attempt, maxAttempts);
                publishTerminalOnce(
                    terminalPublished,
                    requestId,
                    safeConversationId,
                    ChatRequestState.FAILED,
                    responseModel,
                    "Не удалось получить ответ",
                    attempt,
                    maxAttempts,
                    startedAt,
                    buildFailureMetadata(statusCode, errorMessage, failure.category())
                );
                if (streamingChunkReceived.get() && !partialSnapshot.isBlank()) {
                        publishTerminalResponseChunk(
                            requestId,
                            safeConversationId,
                            responseModel,
                            partialSnapshot,
                            false,
                            startedAt
                        );
                }
                return CompletableFuture.completedFuture(failure.userMessage());
            }))
            .exceptionallyCompose(AsyncContext.withMdcFunction(e -> {
                Throwable cause = AsyncContext.unwrap(e);
                if (cause instanceof CancellationException) {
                    publishTerminalOnce(
                        terminalPublished,
                        requestId,
                        safeConversationId,
                        ChatRequestState.CANCELLED,
                        model,
                        "Запрос отменён",
                        1,
                        maxAttempts,
                        startedAt,
                        Map.of()
                    );
                    String snapshot;
                    synchronized (streamedContent) {
                        snapshot = streamedContent.toString();
                    }
                    if (!snapshot.isBlank()) {
                        publishTerminalResponseChunk(
                            requestId,
                            safeConversationId,
                            model,
                            snapshot,
                            syntheticPartialUsed.get(),
                            startedAt
                        );
                    }
                    return CompletableFuture.completedFuture(null);
                }

                String partialSnapshot = snapshotStreamedContent(streamedContent);
                boolean canAutoContinue = continuationEnabled
                    && continuationStepsRef.get() < continuationMaxSteps
                    && isTimeoutLike(cause)
                    && partialSnapshot.length() >= continuationMinPartialChars;

                if (canAutoContinue) {
                    int continuationStep = continuationStepsRef.incrementAndGet();
                    int continuationAttempt = Math.max(1, streamAttemptRef.get());
                    heartbeat.update(ChatRequestState.RETRYING, model, continuationAttempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.RETRYING,
                        model,
                        "Долгая генерация, запрашиваю продолжение ответа",
                        continuationAttempt,
                        maxAttempts,
                        startedAt,
                        Map.of(
                            "continuation", "true",
                            "continuationStep", String.valueOf(continuationStep),
                            "continuationReason", "timeout"
                        )
                    );
                    heartbeat.update(ChatRequestState.WAITING_PROVIDER, model, continuationAttempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.WAITING_PROVIDER,
                        model,
                        "Отправляю follow-up: продолжи ответ с места остановки",
                        continuationAttempt,
                        maxAttempts,
                        startedAt,
                        Map.of(
                            "continuation", "true",
                            "continuationStep", String.valueOf(continuationStep)
                        )
                    );

                    String continuationPrompt = buildContinuationPrompt(message, partialSnapshot);
                    AiRequestOptions continuationOptions = preparedOptionsRef.get();
                    if (continuationOptions == null) {
                        continuationOptions = buildAssistantRequestOptions(
                            client,
                            buildAssistantSystemPrompt(),
                            contextRef.get() == null ? List.of() : contextRef.get().entries()
                        );
                    }
                    return client.sendChatMessage(continuationPrompt, continuationOptions.withStream(false))
                        .thenApply(AsyncContext.withMdcFunction(continuationResponse -> {
                            int resolvedAttempt = resolveAttempts(
                                continuationResponse == null ? null : continuationResponse.attempts(),
                                maxAttempts
                            );
                            String continuationModel = resolveModelLabel(
                                continuationResponse == null ? null : continuationResponse.model(),
                                model
                            );
                            if (!model.isBlank()
                                && !continuationModel.isBlank()
                                && !continuationModel.equalsIgnoreCase(model)) {
                                publishState(
                                    requestId,
                                    safeConversationId,
                                    ChatRequestState.FALLBACK_MODEL,
                                    continuationModel,
                                    "Продолжение выполняется на резервной модели",
                                    resolvedAttempt,
                                    maxAttempts,
                                    startedAt,
                                    Map.of(
                                        "fallback", "true",
                                        "continuation", "true",
                                        "fromModel", model,
                                        "toModel", continuationModel
                                    )
                                );
                            }
                            if (continuationResponse != null && continuationResponse.success()) {
                                String continuationContent = continuationResponse.content() == null
                                    ? ""
                                    : continuationResponse.content();
                                String mergedContent = mergeContinuationText(partialSnapshot, continuationContent);
                                publishState(
                                    requestId,
                                    safeConversationId,
                                    ChatRequestState.POST_PROCESSING,
                                    continuationModel,
                                    "Собираю итоговый ответ после continuation",
                                    resolvedAttempt,
                                    maxAttempts,
                                    startedAt,
                                    Map.of(
                                        "continuation", "true",
                                        "continuationStep", String.valueOf(continuationStep)
                                    )
                                );
                                contextManager.appendAssistantMessage(safeConversationId, mergedContent);
                                persistContextState(safeConversationId);
                                publishTerminalResponseChunk(
                                    requestId,
                                    safeConversationId,
                                    continuationModel,
                                    mergedContent,
                                    false,
                                    startedAt
                                );
                                publishTerminalOnce(
                                    terminalPublished,
                                    requestId,
                                    safeConversationId,
                                    ChatRequestState.DONE,
                                    continuationModel,
                                    "Ответ готов (с continuation)",
                                    resolvedAttempt,
                                    maxAttempts,
                                    startedAt,
                                    Map.of(
                                        "continuation", "true",
                                        "continuationStep", String.valueOf(continuationStep)
                                    )
                                );
                                return mergedContent;
                            }

                            Integer continuationStatus = continuationResponse == null
                                ? null
                                : (continuationResponse.errorCode() != null
                                    ? continuationResponse.errorCode()
                                    : continuationResponse.httpStatus());
                            String continuationError = continuationResponse == null
                                ? "Пустой ответ от модели"
                                : (continuationResponse.errorMessage() == null
                                    || continuationResponse.errorMessage().isBlank()
                                        ? "Неизвестная ошибка"
                                        : continuationResponse.errorMessage());
                            NormalizedFailure continuationFailure = normalizeFailure(
                                continuationStatus,
                                null,
                                continuationError,
                                resolvedAttempt,
                                maxAttempts
                            );
                            if (!partialSnapshot.isBlank()) {
                                contextManager.appendAssistantMessage(safeConversationId, partialSnapshot);
                                persistContextState(safeConversationId);
                                publishTerminalResponseChunk(
                                    requestId,
                                    safeConversationId,
                                    continuationModel,
                                    partialSnapshot,
                                    false,
                                    startedAt
                                );
                                publishTerminalOnce(
                                    terminalPublished,
                                    requestId,
                                    safeConversationId,
                                    ChatRequestState.PARTIAL_DONE,
                                    continuationModel,
                                    "Получен частичный ответ, continuation завершился ошибкой",
                                    resolvedAttempt,
                                    maxAttempts,
                                    startedAt,
                                    buildFailureMetadata(
                                        continuationStatus,
                                        continuationError,
                                        continuationFailure.category()
                                    )
                                );
                                return partialSnapshot;
                            }
                            publishTerminalOnce(
                                terminalPublished,
                                requestId,
                                safeConversationId,
                                ChatRequestState.FAILED,
                                continuationModel,
                                "Continuation завершился ошибкой",
                                resolvedAttempt,
                                maxAttempts,
                                startedAt,
                                buildFailureMetadata(
                                    continuationStatus,
                                    continuationError,
                                    continuationFailure.category()
                                )
                            );
                            return continuationFailure.userMessage();
                        }))
                        .exceptionally(AsyncContext.withMdcFunction(continuationError -> {
                            Throwable continuationCause = AsyncContext.unwrap(continuationError);
                            NormalizedFailure continuationFailure = normalizeFailure(
                                null,
                                continuationCause,
                                continuationCause == null ? null : continuationCause.getMessage(),
                                maxAttempts,
                                maxAttempts
                            );
                            if (!partialSnapshot.isBlank()) {
                                contextManager.appendAssistantMessage(safeConversationId, partialSnapshot);
                                persistContextState(safeConversationId);
                                publishTerminalResponseChunk(
                                    requestId,
                                    safeConversationId,
                                    model,
                                    partialSnapshot,
                                    false,
                                    startedAt
                                );
                                publishTerminalOnce(
                                    terminalPublished,
                                    requestId,
                                    safeConversationId,
                                    ChatRequestState.PARTIAL_DONE,
                                    model,
                                    "Получен частичный ответ, continuation недоступен",
                                    maxAttempts,
                                    maxAttempts,
                                    startedAt,
                                    buildFailureMetadata(
                                        null,
                                        continuationCause == null ? "Continuation error" : continuationCause.getMessage(),
                                        continuationFailure.category()
                                    )
                                );
                                return partialSnapshot;
                            }
                            publishTerminalOnce(
                                terminalPublished,
                                requestId,
                                safeConversationId,
                                ChatRequestState.FAILED,
                                model,
                                "Continuation завершился ошибкой",
                                maxAttempts,
                                maxAttempts,
                                startedAt,
                                buildFailureMetadata(
                                    null,
                                    continuationCause == null ? "Continuation error" : continuationCause.getMessage(),
                                    continuationFailure.category()
                                )
                            );
                            return continuationFailure.userMessage();
                        }));
                }

                LOG.error(
                    "chat.service.send.failed",
                    ErrorCode.AI_REQUEST_FAILED,
                    cause,
                    "operation", "sendMessage",
                    "model", model,
                    "conversationId", safeConversationId,
                    "requestId", requestId,
                    "messageLength", message == null ? 0 : message.length()
                );
                String error = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                    ? "Неизвестная ошибка"
                    : cause.getMessage();
                NormalizedFailure failure = normalizeFailure(null, cause, error, maxAttempts, maxAttempts);
                if (!partialSnapshot.isBlank()) {
                    contextManager.appendAssistantMessage(safeConversationId, partialSnapshot);
                    persistContextState(safeConversationId);
                    publishTerminalResponseChunk(
                        requestId,
                        safeConversationId,
                        model,
                        partialSnapshot,
                        syntheticPartialUsed.get(),
                        startedAt
                    );
                    publishTerminalOnce(
                        terminalPublished,
                        requestId,
                        safeConversationId,
                        ChatRequestState.PARTIAL_DONE,
                        model,
                        "Получен частичный ответ",
                        maxAttempts,
                        maxAttempts,
                        startedAt,
                        buildFailureMetadata(null, error, failure.category())
                    );
                    return CompletableFuture.completedFuture(partialSnapshot);
                }
                publishTerminalOnce(
                    terminalPublished,
                    requestId,
                    safeConversationId,
                    ChatRequestState.FAILED,
                    model,
                    "Ошибка выполнения запроса",
                    maxAttempts,
                    maxAttempts,
                    startedAt,
                    buildFailureMetadata(null, error, failure.category())
                );
                return CompletableFuture.completedFuture(failure.userMessage());
            }));

        return trackRequestFuture(
            requestId,
            safeConversationId,
            model,
            maxAttempts,
            startedAt,
            terminalPublished,
            heartbeat,
            requestFuture
        );
    }

    /**
     * Sends a message to the AI with attached image inputs (external API mode only).
     *
     * @param message the user's message
     * @param imagePaths local image paths to encode and attach
     * @return a CompletableFuture with the AI response text
     */
    public CompletableFuture<String> sendMessageWithImages(String message, List<Path> imagePaths) {
        return sendMessageWithMediaAttachments(UNKNOWN_CONVERSATION_ID, message, imagePaths);
    }

    /**
     * Sends a message with image attachments in context of a conversation.
     */
    public CompletableFuture<String> sendMessageWithImages(String conversationId, String message, List<Path> imagePaths) {
        return sendMessageWithMediaAttachments(conversationId, message, imagePaths);
    }

    public CompletableFuture<String> sendMessageWithMediaAttachments(String message, List<Path> attachmentPaths) {
        return sendMessageWithMediaAttachments(UNKNOWN_CONVERSATION_ID, message, attachmentPaths);
    }

    public CompletableFuture<String> sendMessageWithMediaAttachments(
            String conversationId,
            String message,
            List<Path> attachmentPaths) {
        if (attachmentPaths == null || attachmentPaths.isEmpty()) {
            return sendMessage(conversationId, message);
        }

        AsyncContext.ensureRequestId();
        AiClient client = AiClientFactory.getInstance().getActiveClient();
        String requestId = AsyncContext.ensureRequestId();
        String safeConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(safeConversationId);
        String model = resolveModelLabel(client.getDefaultModel());
        int maxAttempts = resolveMaxAttempts();
        long startedAt = System.currentTimeMillis();
        AtomicBoolean terminalPublished = new AtomicBoolean(false);
        AtomicReference<ChatContextBuildResult> contextRef = new AtomicReference<>();

        publishState(
            requestId,
            safeConversationId,
            ChatRequestState.QUEUED,
            model,
            "Запрос с вложениями поставлен в очередь",
            1,
            maxAttempts,
            startedAt,
            Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
        );
        publishState(
            requestId,
            safeConversationId,
            ChatRequestState.SENDING,
            model,
            "Подготавливаю вложения к отправке",
            1,
            maxAttempts,
            startedAt,
            Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
        );

        if (client.getMode() != com.example.neuroflowplanner.ai.AiMode.EXTERNAL_OPENAI) {
            String unsupportedMessage = "Вложения поддерживаются только во внешнем API режиме. "
                + "Измените режим ИИ в настройках.";
            publishTerminalOnce(
                terminalPublished,
                requestId,
                safeConversationId,
                ChatRequestState.FAILED,
                model,
                "Текущий режим не поддерживает вложения",
                1,
                maxAttempts,
                startedAt,
                buildFailureMetadata(400, unsupportedMessage)
            );
            return CompletableFuture.completedFuture(unsupportedMessage);
        }

        RequestHeartbeat heartbeat = startHeartbeat(requestId, safeConversationId, model, maxAttempts, startedAt);

        CompletableFuture<String> requestFuture = prepareContextForOutgoingRequest(
                requestId,
                safeConversationId,
                model,
                message,
                maxAttempts,
                startedAt,
                Map.of("attachmentsCount", String.valueOf(attachmentPaths.size())))
            .thenCompose(AsyncContext.withMdcFunction(context -> {
                contextRef.set(context);
                String systemPrompt = buildAssistantSystemPrompt();
                AiRequestOptions options = buildAssistantRequestOptions(client, systemPrompt, context.entries());
                return AsyncContext.supplyAsync(() -> prepareMediaInputs(attachmentPaths))
                    .handle(AsyncContext.withMdcBiFunction((images, throwable) -> {
                        if (throwable != null) {
                            contextManager.rollbackLastMessage(safeConversationId);
                            Throwable cause = AsyncContext.unwrap(throwable);
                            throw new CompletionException(cause);
                        }
                        return images;
                    }))
                    .thenCompose(AsyncContext.withMdcFunction(images -> {
                        publishState(
                            requestId,
                            safeConversationId,
                            ChatRequestState.WAITING_PROVIDER,
                            model,
                            "Ожидаю ответ провайдера",
                            1,
                            maxAttempts,
                            startedAt,
                            mergeMetadata(
                                buildContextMetadata(context),
                                Map.of("attachmentsCount", String.valueOf(images.size()))
                            )
                        );
                        heartbeat.update(ChatRequestState.WAITING_PROVIDER, model, 1);
                        AiRequestOptions requestOptions = options.withMediaInputs(images);
                        return client.sendChatMessageWithMedia(message == null ? "" : message, images, requestOptions);
                    }));
            }))
            .thenApply(AsyncContext.withMdcFunction(response -> {
                int attempt = resolveAttempts(response == null ? null : response.attempts(), maxAttempts);
                String responseModel = resolveModelLabel(response == null ? null : response.model(), model);
                if (attempt > 1) {
                    heartbeat.update(ChatRequestState.RETRYING, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.RETRYING,
                        responseModel,
                        "Ответ получен после повторной попытки",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("attemptRecovered", "true", "attachmentsCount", String.valueOf(attachmentPaths.size()))
                    );
                }
                if (!model.isBlank() && !responseModel.isBlank() && !responseModel.equalsIgnoreCase(model)) {
                    heartbeat.update(ChatRequestState.FALLBACK_MODEL, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.FALLBACK_MODEL,
                        responseModel,
                        "Включена резервная модель",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of(
                            "fallback", "true",
                            "fromModel", model,
                            "toModel", responseModel,
                            "attachmentsCount", String.valueOf(attachmentPaths.size())
                        )
                    );
                }
                if (response != null && response.success()) {
                    String content = response.content();
                    heartbeat.update(ChatRequestState.GENERATING, responseModel, attempt);
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.GENERATING,
                        responseModel,
                        "Модель формирует ответ",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
                    );
                    publishState(
                        requestId,
                        safeConversationId,
                        ChatRequestState.POST_PROCESSING,
                        responseModel,
                        "Подготавливаю финальный ответ",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
                    );
                    contextManager.appendAssistantMessage(safeConversationId, content);
                    persistContextState(safeConversationId);
                    publishTerminalOnce(
                        terminalPublished,
                        requestId,
                        safeConversationId,
                        ChatRequestState.DONE,
                        responseModel,
                        "Ответ готов",
                        attempt,
                        maxAttempts,
                        startedAt,
                        Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
                    );
                    return content;
                }

                Integer statusCode = response == null
                    ? null
                    : (response.errorCode() != null ? response.errorCode() : response.httpStatus());
                String errorMessage = response == null
                    ? "Пустой ответ от модели"
                    : (response.errorMessage() == null || response.errorMessage().isBlank()
                        ? "Неизвестная ошибка"
                        : response.errorMessage());

                NormalizedFailure failure = normalizeFailure(statusCode, null, errorMessage, attempt, maxAttempts);
                String userVisibleError = errorMessage == null || errorMessage.isBlank()
                        ? failure.userMessage()
                        : errorMessage;

                publishTerminalOnce(
                    terminalPublished,
                    requestId,
                    safeConversationId,
                    ChatRequestState.FAILED,
                    responseModel,
                    "Не удалось получить ответ",
                    attempt,
                    maxAttempts,
                    startedAt,
                    buildFailureMetadata(statusCode, errorMessage, failure.category())
                );
                return userVisibleError;
            }))
            .exceptionally(AsyncContext.withMdcFunction(e -> {
                Throwable cause = AsyncContext.unwrap(e);
                if (cause instanceof CancellationException) {
                    publishTerminalOnce(
                        terminalPublished,
                        requestId,
                        safeConversationId,
                        ChatRequestState.CANCELLED,
                        model,
                        "Запрос отменён",
                        1,
                        maxAttempts,
                        startedAt,
                        Map.of("attachmentsCount", String.valueOf(attachmentPaths.size()))
                    );
                    return null;
                }
                LOG.error(
                    "chat.service.send.with.media.failed",
                    ErrorCode.AI_REQUEST_FAILED,
                    cause,
                    "operation", "sendMessageWithMediaAttachments",
                    "model", model,
                    "conversationId", safeConversationId,
                    "requestId", requestId,
                    "messageLength", message == null ? 0 : message.length(),
                    "attachmentsCount", attachmentPaths.size()
                );
                String error = cause.getMessage() == null || cause.getMessage().isBlank()
                    ? "Неизвестная ошибка"
                    : cause.getMessage();
                String userMessage = cause instanceof IllegalArgumentException
                        ? error
                        : normalizeFailure(null, cause, error, maxAttempts, maxAttempts).userMessage();
                publishTerminalOnce(
                    terminalPublished,
                    requestId,
                    safeConversationId,
                    ChatRequestState.FAILED,
                    model,
                    "Ошибка выполнения запроса",
                    maxAttempts,
                    maxAttempts,
                    startedAt,
                    buildFailureMetadata(null, error, cause instanceof IllegalArgumentException ? "validation" : "generic_error")
                );
                return userMessage;
            }));

        return trackRequestFuture(
            requestId,
            safeConversationId,
            model,
            maxAttempts,
            startedAt,
            terminalPublished,
            heartbeat,
            requestFuture
        );
    }

    /**
     * Generates a title for the current conversation.
     *
     * @param userMessage the user's message
     * @param assistantMessage the assistant's response
     * @return a CompletableFuture with the generated title
     */
    public CompletableFuture<String> generateConversationTitle(String userMessage, String assistantMessage) {
        AsyncContext.ensureRequestId();
        AiClient client = AiClientFactory.getInstance().getActiveClient();

        String prompt = """
            Сформируй короткий заголовок переписки (3-6 слов).
            Верни только заголовок без кавычек и точек.

            Сообщение пользователя: %s
            Ответ ассистента: %s
            """.formatted(limitText(userMessage, 400), limitText(assistantMessage, 600));

        AiRequestOptions options = AiRequestOptions.builder()
            .model(client.getDefaultModel())
            .systemPrompt("Ты создаешь короткие заголовки переписок. Отвечай только заголовком, без пояснений.")
            .build();

        return client.sendChatMessage(prompt, options)
            .thenApply(AsyncContext.withMdcFunction(response -> response.success() ? response.content() : null))
            .exceptionally(AsyncContext.withMdcFunction(e -> {
                LOG.warning(
                    "chat.service.title.generation.failed",
                    ErrorCode.AI_REQUEST_FAILED,
                    "operation", "generateConversationTitle",
                    "model", client.getDefaultModel()
                );
                return null;
            }));
    }

    /**
     * Clears the conversation history.
     */
    public void clearHistory() {
        contextManager.clearContext(null);
        contextCompactionService.clearState(null);
        inFlightSummaries.clear();
        loadedPersistentContext.clear();
        db.deleteAllChatContextStates();
    }

    /**
     * Returns the current conversation history size.
     */
    public int getHistorySize() {
        return contextManager.getHistorySize(UNKNOWN_CONVERSATION_ID);
    }

    /**
     * Returns history size for a specific conversation.
     */
    public int getHistorySize(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextManager.getHistorySize(normalizedConversationId);
    }

    /**
     * Builds context according to selected mode and policy.
     */
    public ChatContextBuildResult buildContext(String conversationId, ChatContextMode mode) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextManager.buildContext(normalizedConversationId, mode);
    }

    /**
     * Builds a budget snapshot for the current conversation context and model.
     */
    public ChatContextBudgetSnapshot buildContextBudgetSnapshot(String conversationId, String modelId, ChatContextMode mode) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextManager.buildBudgetSnapshot(normalizedConversationId, modelId, mode);
    }

    public CompletableFuture<ChatContextSummarizationState> summarizeContext(String conversationId, String modelId, ChatContextMode mode) {
        AsyncContext.ensureRequestId();
        String requestId = AsyncContext.ensureRequestId();
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        String resolvedModelId = resolveModelLabel(modelId);
        ChatContextMode effectiveMode = mode == null ? contextManager.getPreferredMode(normalizedConversationId) : mode;
        long startedAt = System.currentTimeMillis();

        publishState(
            requestId,
            normalizedConversationId,
            ChatRequestState.QUEUED,
            resolvedModelId,
            "Сжатие контекста поставлено в очередь",
            1,
            1,
            startedAt,
            Map.of("manualSummarize", "true")
        );

        ChatContextSummarizationState budgetState = getContextSummarizationState(
            normalizedConversationId,
            resolvedModelId,
            effectiveMode
        );
        publishState(
            requestId,
            normalizedConversationId,
            ChatRequestState.SUMMARIZING,
            resolvedModelId,
            budgetState.status() == ChatContextSummarizationStatus.SUMMARIZING
                ? "Ожидаю завершения текущего сжатия контекста"
                : "Сжимаю контекст текущей переписки",
            1,
            1,
            startedAt,
            mergeMetadata(Map.of("manualSummarize", "true"), buildSummarizationMetadata(budgetState))
        );

        CompletableFuture<ChatContextSummarizationState> future = resolveOrStartSummarizationFuture(
            normalizedConversationId,
            resolvedModelId,
            effectiveMode
        );
        if (future == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                "Не удалось запустить сжатие контекста для текущей переписки."));
        }
        return future.thenApply(AsyncContext.withMdcFunction(state -> {
            publishState(
                requestId,
                normalizedConversationId,
                ChatRequestState.DONE,
                resolvedModelId,
                "Сжатие контекста завершено",
                1,
                1,
                startedAt,
                mergeMetadata(Map.of("manualSummarize", "true"), buildSummarizationMetadata(state))
            );
            return state;
        }));
    }

    private CompletableFuture<ChatContextBuildResult> prepareContextForOutgoingRequest(
            String requestId,
            String conversationId,
            String modelId,
            String userMessage,
            int maxAttempts,
            long startedAt,
            Map<String, String> extraMetadata) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        ChatContextMode preferredMode = contextManager.getPreferredMode(normalizedConversationId);
        return ensureContextCompactedBeforeSend(
                requestId,
                normalizedConversationId,
                modelId,
                preferredMode,
                maxAttempts,
                startedAt,
                extraMetadata)
            .thenApply(AsyncContext.withMdcFunction(ignored -> {
                contextManager.appendUserMessage(normalizedConversationId, userMessage);
                persistContextState(normalizedConversationId);
                return contextManager.buildContext(normalizedConversationId, preferredMode);
            }));
    }

    private CompletableFuture<ChatContextSummarizationState> ensureContextCompactedBeforeSend(
            String requestId,
            String conversationId,
            String modelId,
            ChatContextMode mode,
            int maxAttempts,
            long startedAt,
            Map<String, String> extraMetadata) {
        ChatContextSummarizationState state = getContextSummarizationState(conversationId, modelId, mode);
        if (state.status() == ChatContextSummarizationStatus.SUMMARY_READY
                || state.status() == ChatContextSummarizationStatus.SUMMARY_FAILED) {
            contextCompactionService.resetState(conversationId);
            state = getContextSummarizationState(conversationId, modelId, mode);
        }
        if (state.status() != ChatContextSummarizationStatus.NEAR_LIMIT
                && state.status() != ChatContextSummarizationStatus.SUMMARIZING) {
            return CompletableFuture.completedFuture(state);
        }

        publishState(
            requestId,
            conversationId,
            ChatRequestState.SUMMARIZING,
            modelId,
            state.status() == ChatContextSummarizationStatus.SUMMARIZING
                ? "Ожидаю завершения сжатия контекста"
                : "Сжимаю контекст перед отправкой запроса",
            1,
            maxAttempts,
            startedAt,
            mergeMetadata(
                mergeMetadata(
                    Map.of("autoSummarize", "true"),
                    buildSummarizationMetadata(state)
                ),
                extraMetadata
            )
        );

        CompletableFuture<ChatContextSummarizationState> summaryFuture = resolveOrStartSummarizationFuture(
                conversationId,
                modelId,
                mode);
        if (summaryFuture == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Контекст сейчас сжимается. Дождитесь завершения операции."));
        }
        return summaryFuture;
    }

    private CompletableFuture<ChatContextSummarizationState> resolveOrStartSummarizationFuture(
            String conversationId,
            String modelId,
            ChatContextMode mode) {
        AtomicReference<CompletableFuture<ChatContextSummarizationState>> createdRef = new AtomicReference<>();
        CompletableFuture<ChatContextSummarizationState> future = inFlightSummaries.compute(
                conversationId,
                (key, existing) -> {
                    if (existing != null && !existing.isDone()) {
                        return existing;
                    }
                    String operationId = contextCompactionService.tryStartSummarization(key);
                    if (operationId == null) {
                        return existing != null && !existing.isDone() ? existing : null;
                    }
                    CompletableFuture<ChatContextSummarizationState> created =
                            AsyncContext.supplyAsync(() -> performContextSummarization(key, modelId, mode, operationId));
                    createdRef.set(created);
                    return created;
                });
        CompletableFuture<ChatContextSummarizationState> created = createdRef.get();
        if (created != null) {
            created.whenComplete((result, throwable) -> inFlightSummaries.remove(conversationId, created));
        }
        if (future != null) {
            return future;
        }
        CompletableFuture<ChatContextSummarizationState> running = inFlightSummaries.get(conversationId);
        return running != null && !running.isDone() ? running : null;
    }

    private ChatContextSummarizationState performContextSummarization(
            String conversationId,
            String modelId,
            ChatContextMode mode,
            String operationId) {
        try {
            String summary = generateConversationSummary(conversationId, modelId);
            ChatContextManager.ContextSummarizationInput input = contextManager.buildSummarizationInput(conversationId);
            contextManager.applySummary(conversationId, summary, input.coveredMessages());
            contextCompactionService.markSummaryReady(conversationId, operationId);
            persistContextState(conversationId, modelId);
            contextCompactionService.resetState(conversationId);
            return getContextSummarizationState(conversationId, modelId, mode);
        } catch (RuntimeException e) {
            contextCompactionService.markSummaryFailed(conversationId, operationId, e.getMessage());
            persistContextState(conversationId, modelId);
            throw e;
        }
    }

    private String generateConversationSummary(String conversationId, String modelId) {
        ChatContextManager.ContextSummarizationInput input = contextManager.buildSummarizationInput(conversationId);
        if (input.coveredMessages() <= 0) {
            throw new IllegalStateException("Недостаточно истории для сжатия контекста.");
        }
        String fallbackSummary = input.fallbackSummary() == null ? "" : input.fallbackSummary().trim();
        if (fallbackSummary.isBlank()) {
            throw new IllegalStateException("Не удалось подготовить исходные данные для summary.");
        }

        AiClient client = AiClientFactory.getInstance().getActiveClient();
        String resolvedModel = resolveModelLabel(modelId, client.getDefaultModel());
        String prompt = ChatContextSummaryTemplate.buildUserPrompt(input);
        AiRequestOptions options = buildSummarizationRequestOptions(resolvedModel);

        try {
            AiResponse response = client.sendChatMessage(prompt, options).join();
            String candidate = response == null ? "" : response.content();
            if (response != null && response.success() && ChatContextSummaryTemplate.isAcceptable(candidate)) {
                return ChatContextSummaryTemplate.normalizeSummary(candidate);
            }
            LOG.warning(
                "chat.context.summary.fallback.used",
                "conversationId", conversationId,
                "model", resolvedModel,
                "reason", response == null
                    ? "empty_response"
                    : (response.success() ? "summary_quality_check_failed" : "provider_summary_failed")
            );
            return fallbackSummary;
        } catch (CompletionException e) {
            Throwable cause = AsyncContext.unwrap(e);
            LOG.warning(
                "chat.context.summary.fallback.used",
                "conversationId", conversationId,
                "model", resolvedModel,
                "reason", cause == null ? "completion_exception" : cause.getClass().getSimpleName()
            );
            return fallbackSummary;
        }
    }

    private Map<String, String> buildSummarizationMetadata(ChatContextSummarizationState state) {
        if (state == null) {
            return Map.of();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("summarizationStatus", state.status().name());
        metadata.put("contextSeverity", state.lastBudgetSeverity().name());
        if (state.lastUsageRatio() != null) {
            metadata.put("contextUsageRatio", String.format(java.util.Locale.US, "%.3f", state.lastUsageRatio()));
        }
        return metadata;
    }

    /**
     * Returns current summarize state for the conversation after syncing it with
     * the latest budget snapshot.
     */
    public ChatContextSummarizationState getContextSummarizationState(String conversationId, String modelId, ChatContextMode mode) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        ChatContextBudgetSnapshot snapshot = contextManager.buildBudgetSnapshot(normalizedConversationId, modelId, mode);
        return contextCompactionService.updateBudgetState(normalizedConversationId, snapshot);
    }

    /**
     * Returns current summarize state without recalculating context budget.
     */
    public ChatContextSummarizationState getCachedContextSummarizationState(String conversationId) {
        return contextCompactionService.getState(normalizeConversationId(conversationId));
    }

    /**
     * Attempts to begin summarize operation for the conversation.
     * Returns operation id when lock acquired, otherwise null.
     */
    public String tryStartContextSummarization(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextCompactionService.tryStartSummarization(normalizedConversationId);
    }

    public ChatContextSummarizationState markContextSummarizationReady(String conversationId, String operationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextCompactionService.markSummaryReady(normalizedConversationId, operationId);
    }

    public ChatContextSummarizationState markContextSummarizationFailed(
            String conversationId,
            String operationId,
            String errorMessage) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextCompactionService.markSummaryFailed(normalizedConversationId, operationId, errorMessage);
    }

    public ChatContextSummarizationState resetContextSummarizationState(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextCompactionService.resetState(normalizedConversationId);
    }

    /**
     * Replaces context history for a conversation (used when switching to saved
     * chat).
     */
    public void replaceConversationHistory(String conversationId, List<AiRequestOptions.ChatHistoryEntry> history) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        contextManager.replaceHistory(normalizedConversationId, history);
        persistContextState(normalizedConversationId);
    }

    /**
     * Sets preferred context mode for a conversation.
     */
    public void setContextMode(String conversationId, ChatContextMode mode) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        contextManager.setPreferredMode(normalizedConversationId, mode);
        persistContextState(normalizedConversationId);
    }

    /**
     * Returns preferred context mode for a conversation.
     */
    public ChatContextMode getContextMode(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        return contextManager.getPreferredMode(normalizedConversationId);
    }

    /**
     * Pins a fact/note to conversation context.
     */
    public void pinContextItem(String conversationId, String item) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        contextManager.pinContextItem(normalizedConversationId, item);
        persistContextState(normalizedConversationId);
    }

    /**
     * Rebuilds auto-summary for selected conversation.
     */
    public String rebuildSummary(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        ensureContextStateLoaded(normalizedConversationId);
        String rebuilt = contextManager.rebuildSummary(normalizedConversationId);
        persistContextState(normalizedConversationId);
        return rebuilt;
    }

    /**
     * Clears context for selected conversation (or all when id is null/blank).
     */
    public void clearContext(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            contextManager.clearContext(null);
            contextCompactionService.clearState(null);
            inFlightSummaries.clear();
            loadedPersistentContext.clear();
            db.deleteAllChatContextStates();
            return;
        }
        String normalizedConversationId = normalizeConversationId(conversationId);
        contextManager.clearContext(normalizedConversationId);
        contextCompactionService.clearState(normalizedConversationId);
        inFlightSummaries.remove(normalizedConversationId);
        loadedPersistentContext.remove(normalizedConversationId);
        if (!UNKNOWN_CONVERSATION_ID.equals(normalizedConversationId)) {
            db.deleteChatContextState(normalizedConversationId);
        }
    }

    /**
     * Builds the system prompt based on user settings.
     */
    private String buildAssistantSystemPrompt() {
        String detail = normalizeDetail(ConfigManager.getProperty(AiConfigDefaults.CONFIG_ASSISTANT_DETAIL));
        String tone = normalizeTone(ConfigManager.getProperty(AiConfigDefaults.CONFIG_ASSISTANT_TONE));
        String detailInstruction = detail.equals(AiConfigDefaults.ASSISTANT_DETAIL_DETAILED)
            ? "Отвечай подробно, структурированно и с пояснениями."
            : "Отвечай кратко и по делу.";
        String toneInstruction = tone.equals(AiConfigDefaults.ASSISTANT_TONE_FORMAL)
            ? "Тон формальный и профессиональный."
            : "Тон дружелюбный и поддерживающий.";
        return "Ты — умный ассистент планировщика задач NeuroFlow. " +
            "Помогай пользователю с планированием, приоритизацией и организацией задач. " +
            detailInstruction + " " + toneInstruction + " Отвечай на русском языке. Не используй эмодзи.";
    }

    private AiRequestOptions buildAssistantRequestOptions(
        AiClient client,
        String systemPrompt,
        List<AiRequestOptions.ChatHistoryEntry> conversationHistory
    ) {
        return buildAssistantRequestOptions(client, client.getDefaultModel(), systemPrompt, conversationHistory);
    }

    private AiRequestOptions buildAssistantRequestOptions(
        AiClient client,
        String modelId,
        String systemPrompt,
        List<AiRequestOptions.ChatHistoryEntry> conversationHistory
    ) {
        String resolvedModelId = resolveModelLabel(modelId, client.getDefaultModel());
        return AiRequestOptions.builder()
            .model(resolvedModelId)
            .systemPrompt(systemPrompt)
            .temperature(resolveAssistantTemperature(resolvedModelId))
            .maxTokens(resolveAssistantMaxTokens(resolvedModelId))
            .topP(resolveAssistantTopP(resolvedModelId))
            .frequencyPenalty(resolveAssistantFrequencyPenalty(resolvedModelId))
            .presencePenalty(resolveAssistantPresencePenalty(resolvedModelId))
            .conversationHistory(conversationHistory)
            .reasoningEffort(resolveAssistantReasoningEffort(resolvedModelId))
            .reasoning(resolveAssistantReasoningOptions(resolvedModelId))
            .pluginOptions(resolveAssistantPluginOptions())
            .build();
    }

    private AiRequestOptions buildSummarizationRequestOptions(String modelId) {
        String resolvedModelId = resolveModelLabel(modelId);
        return AiRequestOptions.builder()
            .model(resolvedModelId)
            .systemPrompt(ChatContextSummaryTemplate.buildSystemPrompt())
            .temperature(0.15)
            .maxTokens(resolveSummarizationMaxTokens(resolvedModelId))
            .build();
    }

    private AiRequestOptions.PluginOptions resolveAssistantPluginOptions() {
        return new AiRequestOptions.PluginOptions(
                new AiRequestOptions.WebPluginOptions(
                        ConfigManager.isAiPluginWebEnabled(),
                        ConfigManager.getAiPluginWebEngine(),
                        ConfigManager.getAiPluginWebMaxResults(),
                        ConfigManager.getAiPluginWebSearchPrompt()),
                new AiRequestOptions.FileParserPluginOptions(
                        ConfigManager.isAiPluginFileParserEnabled(),
                        ConfigManager.getAiPluginFileParserPdfEngine()),
                new AiRequestOptions.ResponseHealingPluginOptions(
                        ConfigManager.isAiPluginResponseHealingEnabled()));
    }

    private Integer resolveAssistantMaxTokens(String model) {
        Integer configured = ConfigManager.getAssistantTextMaxTokens();
        if (configured == null) {
            return null;
        }
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        if (metadata != null && metadata.maxCompletionTokens() != null && metadata.maxCompletionTokens() > 0) {
            return Math.min(configured, metadata.maxCompletionTokens());
        }
        return configured;
    }

    private Integer resolveSummarizationMaxTokens(String model) {
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        int preferred = 1200;
        if (metadata != null && metadata.maxCompletionTokens() != null && metadata.maxCompletionTokens() > 0) {
            return Math.max(256, Math.min(preferred, metadata.maxCompletionTokens()));
        }
        Integer configured = ConfigManager.getAssistantTextMaxTokens();
        if (configured == null || configured <= 0) {
            return preferred;
        }
        return Math.max(256, Math.min(preferred, configured));
    }

    private Double resolveAssistantTemperature(String model) {
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        if (metadata == null || !metadata.supportsTemperature()) {
            return null;
        }
        return ConfigManager.getAssistantTextTemperature();
    }

    private Double resolveAssistantTopP(String model) {
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        if (metadata == null || !metadata.supportsTopP()) {
            return null;
        }
        return ConfigManager.getAssistantTextTopP();
    }

    private Double resolveAssistantFrequencyPenalty(String model) {
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        if (metadata == null || !metadata.supportsFrequencyPenalty()) {
            return null;
        }
        return ConfigManager.getAssistantTextFrequencyPenalty();
    }

    private Double resolveAssistantPresencePenalty(String model) {
        AiTextModelParameterMetadata metadata = AiTextModelParameterResolver.resolveForModel(model);
        if (metadata == null || !metadata.supportsPresencePenalty()) {
            return null;
        }
        return ConfigManager.getAssistantTextPresencePenalty();
    }

    private List<AiMediaInput> prepareMediaInputs(List<Path> attachmentPaths) {
        List<AiMediaInput> mediaInputs = new java.util.ArrayList<>();
        boolean hasAudio = false;
        boolean hasNonAudio = false;

        for (Path path : attachmentPaths) {
            if (path == null) {
                continue;
            }
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Файл не найден: " + path);
            }

            String fileName = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String mimeType = detectMimeType(path);
            AiMediaTypeDescriptor descriptor = AiMediaTypeRegistry.detect(fileName, mimeType)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Неподдерживаемый тип файла: " + fileName
                                    + ". Разрешены изображения, PDF/DOCX/TXT и WAV/MP3/FLAC/M4A."));
            if (!descriptor.supportedInput() || descriptor.kind() == AiMediaInputKind.VIDEO) {
                throw new IllegalArgumentException("Видео на вход пока не поддерживается: " + fileName);
            }

            byte[] rawBytes;
            try {
                rawBytes = Files.readAllBytes(path);
            } catch (IOException ex) {
                throw new IllegalArgumentException("Не удалось прочитать файл: " + fileName, ex);
            }
            mediaInputs.add(AiMediaInput.rawBytes(
                    descriptor.kind(),
                    rawBytes,
                    fileName,
                    descriptor.mimeType(),
                    descriptor.audioFormat()));

            if (descriptor.kind() == AiMediaInputKind.AUDIO) {
                hasAudio = true;
            } else {
                hasNonAudio = true;
            }
        }

        if (hasAudio && hasNonAudio) {
            throw new IllegalArgumentException(
                    "Аудио пока можно отправлять только отдельно, без изображений и документов.");
        }
        return List.copyOf(mediaInputs);
    }

    private String detectMimeType(Path path) {
        try {
            return Files.probeContentType(path);
        } catch (IOException ignored) {
            return null;
        }
    }

    private CompletableFuture<String> trackRequestFuture(
        String requestId,
        String conversationId,
        String model,
        int maxAttempts,
        long startedAt,
        AtomicBoolean terminalPublished,
        RequestHeartbeat heartbeat,
        CompletableFuture<String> requestFuture
    ) {
        inFlightRequests.put(requestId, requestFuture);
        requestFuture.whenComplete(AsyncContext.withMdcBiConsumer((result, throwable) -> {
            inFlightRequests.remove(requestId, requestFuture);
            if (heartbeat != null) {
                heartbeat.stop();
            }
            Throwable cause = throwable == null ? null : AsyncContext.unwrap(throwable);
            if (cause instanceof CancellationException || requestFuture.isCancelled()) {
                publishTerminalOnce(
                    terminalPublished,
                    requestId,
                    conversationId,
                    ChatRequestState.CANCELLED,
                    model,
                    "Запрос отменён",
                    1,
                    maxAttempts,
                    startedAt,
                    Map.of()
                );
            }
        }));
        return requestFuture;
    }

    private void publishState(
        String requestId,
        String conversationId,
        ChatRequestState state,
        String model,
        String message,
        int attempt,
        int maxAttempts,
        long startedAt,
        Map<String, String> metadata
    ) {
        ChatRequestProgress progress = new ChatRequestProgress(
            System.currentTimeMillis() - startedAt,
            attempt,
            maxAttempts,
            state.isTerminal()
        );
        lifecyclePublisher.publish(new ChatRequestEvent(
            requestId,
            conversationId,
            state,
            model,
            message,
            progress,
            Instant.now(),
            metadata
        ));
    }

    private void publishTerminalOnce(
        AtomicBoolean terminalPublished,
        String requestId,
        String conversationId,
        ChatRequestState state,
        String model,
        String message,
        int attempt,
        int maxAttempts,
        long startedAt,
        Map<String, String> metadata
    ) {
        if (!state.isTerminal()) {
            publishState(requestId, conversationId, state, model, message, attempt, maxAttempts, startedAt, metadata);
            return;
        }
        if (terminalPublished.compareAndSet(false, true)) {
            publishState(requestId, conversationId, state, model, message, attempt, maxAttempts, startedAt, metadata);
        }
    }

    private void publishResponseChunk(
        String requestId,
        String conversationId,
        String model,
        String delta,
        String accumulated,
        boolean synthetic,
        long startedAt
    ) {
        responseChunkPublisher.publish(ChatResponseChunk.delta(
            requestId,
            conversationId,
            model,
            delta,
            accumulated,
            synthetic,
            Math.max(0L, System.currentTimeMillis() - startedAt)
        ));
    }

    private void publishTerminalResponseChunk(
        String requestId,
        String conversationId,
        String model,
        String accumulated,
        boolean synthetic,
        long startedAt
    ) {
        responseChunkPublisher.publish(ChatResponseChunk.terminal(
            requestId,
            conversationId,
            model,
            accumulated,
            synthetic,
            Math.max(0L, System.currentTimeMillis() - startedAt)
        ));
    }

    private boolean shouldUseControlledPartial(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.length() >= CONTROLLED_PARTIAL_MIN_LENGTH;
    }

    private void emitControlledPartialChunks(
        String requestId,
        String conversationId,
        String model,
        String content,
        long startedAt
    ) {
        if (content == null || content.isBlank()) {
            return;
        }
        int totalLength = content.length();
        int chunkCount = Math.max(3, Math.min(CONTROLLED_PARTIAL_MAX_CHUNKS, totalLength / 120));
        int chunkSize = Math.max(80, totalLength / chunkCount);
        StringBuilder accumulated = new StringBuilder(totalLength);
        int index = 0;
        while (index < totalLength) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Controlled partial delivery interrupted");
            }
            int end = Math.min(totalLength, index + chunkSize);
            if (end < totalLength) {
                int boundary = content.lastIndexOf(' ', end);
                if (boundary > index + 30) {
                    end = boundary;
                }
            }
            String delta = content.substring(index, end);
            accumulated.append(delta);
            publishResponseChunk(
                requestId,
                conversationId,
                model,
                delta,
                accumulated.toString(),
                true,
                startedAt
            );
            index = end;
            if (index < totalLength) {
                try {
                    Thread.sleep(CONTROLLED_PARTIAL_CHUNK_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Controlled partial delivery interrupted");
                }
            }
        }
    }

    private RequestHeartbeat startHeartbeat(
        String requestId,
        String conversationId,
        String model,
        int maxAttempts,
        long startedAt
    ) {
        long intervalMs = ConfigManager.getAiRequestHeartbeatIntervalMs();
        RequestHeartbeat heartbeat = new RequestHeartbeat(requestId, conversationId, model, maxAttempts, startedAt);
        Runnable task = AsyncContext.withMdc(() -> {
            if (!heartbeat.active.get()) {
                return;
            }
            ChatRequestState state = heartbeat.state.get();
            if (state == null || state.isTerminal()) {
                return;
            }
            String heartbeatModel = resolveModelLabel(heartbeat.model.get(), model);
            int attempt = Math.max(1, heartbeat.attempt.get());
            publishState(
                requestId,
                conversationId,
                state,
                heartbeatModel,
                resolveHeartbeatMessage(state),
                attempt,
                maxAttempts,
                startedAt,
                Map.of("heartbeat", "true")
            );
        });
        heartbeat.future = heartbeatScheduler.scheduleAtFixedRate(task, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        return heartbeat;
    }

    private String resolveHeartbeatMessage(ChatRequestState state) {
        if (state == null) {
            return "Запрос выполняется";
        }
        return switch (state) {
            case QUEUED -> "Запрос в очереди";
            case SENDING -> "Отправка запроса";
            case SUMMARIZING -> "Сжимаю контекст перед ответом";
            case WAITING_PROVIDER -> "Ожидаю ответ провайдера";
            case GENERATING -> "Модель продолжает формировать ответ";
            case POST_PROCESSING -> "Подготавливаю финальный ответ";
            case RETRYING -> "Выполняется повторная попытка";
            case FALLBACK_MODEL -> "Переключаюсь на резервную модель";
            case PARTIAL_DONE -> "Получен частичный ответ";
            case DONE -> "Ответ готов";
            case FAILED -> "Запрос завершился ошибкой";
            case CANCELLED -> "Запрос отменен";
        };
    }

    private String snapshotStreamedContent(StringBuilder streamedContent) {
        if (streamedContent == null) {
            return "";
        }
        synchronized (streamedContent) {
            return streamedContent.toString();
        }
    }

    private boolean isTimeoutLike(Throwable cause) {
        if (cause == null) {
            return false;
        }
        Throwable unwrapped = AsyncContext.unwrap(cause);
        if (unwrapped instanceof TimeoutException || unwrapped instanceof java.net.http.HttpTimeoutException) {
            return true;
        }
        String message = unwrapped.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("timeout")
            || normalized.contains("timed out")
            || normalized.contains("budget");
    }

    private String buildContinuationPrompt(String originalUserMessage, String partialAnswer) {
        String safeQuestion = limitText(originalUserMessage, 900);
        String safePartial = limitText(partialAnswer, AiConfigDefaults.CONTINUATION_PROMPT_MAX_CHARS);
        return """
            Продолжи предыдущий ответ с места остановки.
            Не повторяй уже написанный текст и не начинай ответ заново.
            Если ответ уже фактически завершен, коротко дополни только недостающую часть.

            Исходный вопрос пользователя:
            %s

            Уже полученная часть ответа:
            %s
            """.formatted(safeQuestion, safePartial);
    }

    private String mergeContinuationText(String partial, String continuation) {
        if (partial == null || partial.isBlank()) {
            return continuation == null ? "" : continuation;
        }
        if (continuation == null || continuation.isBlank()) {
            return partial;
        }
        String trimmedPartial = partial.trim();
        String trimmedContinuation = continuation.trim();
        int overlap = calculateSuffixPrefixOverlap(trimmedPartial, trimmedContinuation);
        if (overlap <= 0) {
            return trimmedPartial + "\n" + trimmedContinuation;
        }
        return trimmedPartial + trimmedContinuation.substring(overlap);
    }

    private int calculateSuffixPrefixOverlap(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return 0;
        }
        int maxScan = Math.min(CONTINUATION_OVERLAP_MAX_SCAN, Math.min(left.length(), right.length()));
        for (int len = maxScan; len >= 24; len--) {
            String suffix = left.substring(left.length() - len);
            String prefix = right.substring(0, len);
            if (suffix.equalsIgnoreCase(prefix)) {
                return len;
            }
        }
        return 0;
    }

    private NormalizedFailure normalizeFailure(
        Integer statusCode,
        Throwable cause,
        String errorMessage,
        int attempt,
        int maxAttempts
    ) {
        String message = errorMessage == null ? "" : errorMessage.trim();
        Throwable unwrapped = cause == null ? null : AsyncContext.unwrap(cause);
        int resolvedStatus = statusCode == null ? -1 : statusCode;
        if (resolvedStatus == 429 || message.toLowerCase(java.util.Locale.ROOT).contains("rate limit")
            || message.toLowerCase(java.util.Locale.ROOT).contains("quota")) {
            return new NormalizedFailure(
                "provider_limit",
                "Лимит провайдера исчерпан. Подождите немного и повторите запрос."
            );
        }
        if (resolvedStatus == 408 || resolvedStatus == 425 || isTimeoutLike(unwrapped)) {
            return new NormalizedFailure(
                "temporary_delay",
                "Временная задержка на стороне ИИ. Запрос выполнялся слишком долго."
            );
        }
        if (attempt >= Math.max(1, maxAttempts)) {
            return new NormalizedFailure(
                "retry_exhausted",
                "Исчерпаны попытки запроса. Попробуйте снова или смените модель."
            );
        }
        return new NormalizedFailure(
            "generic_error",
            "Не удалось получить ответ от ИИ. Попробуйте повторить запрос."
        );
    }

    private Map<String, String> buildFailureMetadata(Integer statusCode, String errorMessage) {
        return buildFailureMetadata(statusCode, errorMessage, null);
    }

    private Map<String, String> buildFailureMetadata(Integer statusCode, String errorMessage, String category) {
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        if (statusCode != null) {
            metadata.put("statusCode", String.valueOf(statusCode));
        }
        if (errorMessage != null && !errorMessage.isBlank()) {
            metadata.put("error", errorMessage);
        }
        if (category != null && !category.isBlank()) {
            metadata.put("failureCategory", category);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, String> buildContextMetadata(ChatContextBuildResult context) {
        if (context == null) {
            return Map.of();
        }
        LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        metadata.put("contextRequestedMode", context.requestedMode().name().toLowerCase(java.util.Locale.ROOT));
        metadata.put("contextEffectiveMode", context.effectiveMode().name().toLowerCase(java.util.Locale.ROOT));
        metadata.put("contextTokens", String.valueOf(context.estimatedTokens()));
        metadata.put("contextSelectedMessages", String.valueOf(context.selectedHistoryMessages()));
        metadata.put("contextTotalMessages", String.valueOf(context.totalHistoryMessages()));
        metadata.put("contextPinnedFacts", String.valueOf(context.pinnedFactsCount()));
        metadata.put("contextSummaryIncluded", String.valueOf(context.summaryIncluded()));
        metadata.put("contextOverflowProtected", String.valueOf(context.overflowProtected()));
        return Map.copyOf(metadata);
    }

    private Map<String, String> mergeMetadata(Map<String, String> first, Map<String, String> second) {
        LinkedHashMap<String, String> merged = new LinkedHashMap<>();
        if (first != null && !first.isEmpty()) {
            merged.putAll(first);
        }
        if (second != null && !second.isEmpty()) {
            merged.putAll(second);
        }
        return Map.copyOf(merged);
    }

    private void ensureContextStateLoaded(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        if (UNKNOWN_CONVERSATION_ID.equals(normalizedConversationId)) {
            return;
        }
        if (loadedPersistentContext.putIfAbsent(normalizedConversationId, Boolean.TRUE) != null) {
            return;
        }
        ChatContextState persistedState = db.loadChatContextState(normalizedConversationId);
        if (persistedState == null) {
            return;
        }
        contextManager.restorePersistentState(
            normalizedConversationId,
            new ChatContextManager.ContextPersistentState(
                parsePersistedMode(persistedState.getPreferredMode()),
                persistedState.getSummary(),
                persistedState.getSummaryCoveredMessages(),
                persistedState.getPinnedFacts(),
                persistedState.getActiveSummaryRevision() == null ? 0 : persistedState.getActiveSummaryRevision(),
                persistedState.getLastSummarizeAt()
            )
        );
        contextCompactionService.restoreState(
            normalizedConversationId,
            parsePersistedSummarizationStatus(persistedState.getLastSummarizeStatus()),
            null,
            parsePersistedBudgetSeverity(persistedState.getLastBudgetSeverity()),
            persistedState.getLastUsageRatio(),
            persistedState.getActiveSummaryRevision() == null || persistedState.getActiveSummaryRevision() <= 0
                ? null
                : "summary-rev-" + persistedState.getActiveSummaryRevision(),
            parsePersistedTimestamp(
                persistedState.getLastSummarizeAt() == null || persistedState.getLastSummarizeAt().isBlank()
                    ? persistedState.getUpdatedAt()
                    : persistedState.getLastSummarizeAt()
            )
        );
    }

    private void persistContextState(String conversationId) {
        persistContextState(conversationId, null);
    }

    private void persistContextState(String conversationId, String preferredModelId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        if (UNKNOWN_CONVERSATION_ID.equals(normalizedConversationId)) {
            return;
        }
        ChatContextManager.ContextPersistentState snapshot = contextManager.exportPersistentState(normalizedConversationId);
        String activeModelId = resolveModelLabel(
            preferredModelId == null || preferredModelId.isBlank()
                ? AiClientFactory.getInstance().getActiveClient().getDefaultModel()
                : preferredModelId
        );
        ChatContextBudgetSnapshot budgetSnapshot = contextManager.buildBudgetSnapshot(
            normalizedConversationId,
            activeModelId,
            snapshot.preferredMode()
        );
        ChatContextSummarizationState summarizeState = contextCompactionService.getState(normalizedConversationId);
        ChatContextState contextState = new ChatContextState(
            normalizedConversationId,
            snapshot.preferredMode().name(),
            snapshot.summary(),
            snapshot.summaryCoveredMessages(),
            snapshot.pinnedFacts(),
            budgetSnapshot == null ? null : budgetSnapshot.contextLimitTokens(),
            budgetSnapshot == null ? null : budgetSnapshot.estimatedUsedTokens(),
            budgetSnapshot == null ? null : budgetSnapshot.reservedCompletionTokens(),
            snapshot.lastSummarizedAt(),
            summarizeState == null ? "" : summarizeState.status().name(),
            snapshot.activeSummaryRevision(),
            summarizeState == null || summarizeState.lastBudgetSeverity() == null
                ? ""
                : summarizeState.lastBudgetSeverity().name(),
            summarizeState == null ? null : summarizeState.lastUsageRatio(),
            LocalDateTime.now().toString()
        );
        db.saveChatContextState(contextState);
        loadedPersistentContext.put(normalizedConversationId, Boolean.TRUE);
    }

    private ChatContextMode parsePersistedMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return ChatContextMode.AUTO;
        }
        try {
            return ChatContextMode.valueOf(rawMode.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ChatContextMode.AUTO;
        }
    }

    private ChatContextSummarizationStatus parsePersistedSummarizationStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return ChatContextSummarizationStatus.IDLE;
        }
        try {
            ChatContextSummarizationStatus parsed =
                ChatContextSummarizationStatus.valueOf(rawStatus.trim().toUpperCase(java.util.Locale.ROOT));
            return parsed == ChatContextSummarizationStatus.SUMMARIZING
                ? ChatContextSummarizationStatus.NEAR_LIMIT
                : parsed;
        } catch (IllegalArgumentException ignored) {
            return ChatContextSummarizationStatus.IDLE;
        }
    }

    private com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity parsePersistedBudgetSeverity(String rawSeverity) {
        if (rawSeverity == null || rawSeverity.isBlank()) {
            return com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity.UNKNOWN;
        }
        try {
            return com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity.valueOf(
                rawSeverity.trim().toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException ignored) {
            return com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSeverity.UNKNOWN;
        }
    }

    private Instant parsePersistedTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(rawTimestamp.trim()).atZone(java.time.ZoneId.systemDefault()).toInstant();
        } catch (RuntimeException ignored) {
            try {
                return Instant.parse(rawTimestamp.trim());
            } catch (RuntimeException ignoredAgain) {
                return Instant.now();
            }
        }
    }

    private static final class RequestHeartbeat {
        private final String requestId;
        private final String conversationId;
        private final int maxAttempts;
        private final long startedAt;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicReference<ChatRequestState> state = new AtomicReference<>(ChatRequestState.WAITING_PROVIDER);
        private final AtomicReference<String> model;
        private final AtomicInteger attempt = new AtomicInteger(1);
        private volatile ScheduledFuture<?> future;

        private RequestHeartbeat(String requestId, String conversationId, String model, int maxAttempts, long startedAt) {
            this.requestId = requestId;
            this.conversationId = conversationId;
            this.maxAttempts = maxAttempts;
            this.startedAt = startedAt;
            this.model = new AtomicReference<>(model == null ? "" : model);
        }

        private void update(ChatRequestState nextState, String nextModel, int nextAttempt) {
            if (nextState != null) {
                this.state.set(nextState);
            }
            if (nextModel != null && !nextModel.isBlank()) {
                this.model.set(nextModel.trim());
            }
            if (nextAttempt > 0) {
                this.attempt.set(nextAttempt);
            }
        }

        private void stop() {
            active.set(false);
            ScheduledFuture<?> local = future;
            if (local != null) {
                local.cancel(false);
            }
        }
    }

    private record NormalizedFailure(String category, String userMessage) {
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UNKNOWN_CONVERSATION_ID;
        }
        return conversationId.trim();
    }

    private int resolveMaxAttempts() {
        try {
            return Math.max(1, ConfigManager.getAiResiliencePolicy().getRetryPolicy().getMaxAttempts());
        } catch (RuntimeException ignored) {
            return Math.max(1, AiConfigDefaults.RETRY_MAX_ATTEMPTS);
        }
    }

    private int resolveAttempts(Integer attempts, int maxAttempts) {
        if (attempts == null || attempts < 1) {
            return 1;
        }
        return Math.min(attempts, Math.max(1, maxAttempts));
    }

    private String resolveModelLabel(String model) {
        return resolveModelLabel(model, "");
    }

    private String resolveModelLabel(String model, String fallback) {
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "";
    }

    private String normalizeDetail(String value) {
        if (AiConfigDefaults.ASSISTANT_DETAIL_DETAILED.equalsIgnoreCase(value)) {
            return AiConfigDefaults.ASSISTANT_DETAIL_DETAILED;
        }
        return AiConfigDefaults.DEFAULT_ASSISTANT_DETAIL;
    }

    private String normalizeTone(String value) {
        if (AiConfigDefaults.ASSISTANT_TONE_FORMAL.equalsIgnoreCase(value)) {
            return AiConfigDefaults.ASSISTANT_TONE_FORMAL;
        }
        return AiConfigDefaults.DEFAULT_ASSISTANT_TONE;
    }

    private String resolveAssistantReasoningEffort(String model) {
        if (!AiConfigDefaults.supportsReasoningEffort(model)) {
            return null;
        }
        return AiConfigDefaults.toLegacyReasoningEffort(ConfigManager.getAssistantReasoningEffort());
    }

    private AiRequestOptions.ReasoningOptions resolveAssistantReasoningOptions(String model) {
        if (!AiConfigDefaults.supportsStructuredReasoning(model)) {
            return null;
        }
        String effort = ConfigManager.getAssistantReasoningEffort();
        boolean disabled = AiConfigDefaults.ASSISTANT_REASONING_NONE.equals(effort);
        return new AiRequestOptions.ReasoningOptions(
            effort,
            disabled ? null : ConfigManager.getAssistantReasoningMaxTokens(),
            ConfigManager.getAssistantReasoningSummary(),
            disabled ? Boolean.FALSE : Boolean.TRUE,
            ConfigManager.isAssistantReasoningExcluded()
        );
    }

    private String limitText(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max) + "...";
    }
}
