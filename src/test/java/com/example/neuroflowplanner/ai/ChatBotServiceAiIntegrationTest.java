package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.media.AiMediaInput;
import com.example.neuroflowplanner.ai.media.AiMediaInputKind;
import com.example.neuroflowplanner.ai.dto.AiDiscoveredModelInfo;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.service.ChatBotService;
import com.example.neuroflowplanner.service.chatflow.ChatRequestEvent;
import com.example.neuroflowplanner.service.chatflow.ChatRequestState;
import com.example.neuroflowplanner.service.chatflow.ChatRequestSubscription;
import com.example.neuroflowplanner.service.context.ChatContextMode;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.AiConfigDefaults;
import com.example.neuroflowplanner.util.ConfigManager;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBotServiceAiIntegrationTest extends IsolatedTestDataFixture {

    private static final Field ACTIVE_CLIENT_FIELD = findFactoryField("activeClient");
    private static final Field CURRENT_MODE_FIELD = findFactoryField("currentMode");
    private static final Field IN_FLIGHT_FIELD = findServiceField("inFlightRequests");
    private static final Field CONFIG_PROPERTIES_FIELD = findConfigField("properties");

    private static final List<String> OVERRIDDEN_KEYS = List.of(
        "ai.request.continuation.enabled",
        "ai.request.continuation.maxSteps",
        "ai.request.continuation.minPartialChars",
        AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EFFORT,
        AiConfigDefaults.CONFIG_PLUGIN_WEB_ENABLED,
        AiConfigDefaults.CONFIG_PLUGIN_WEB_ENGINE,
        AiConfigDefaults.CONFIG_PLUGIN_WEB_MAX_RESULTS,
        AiConfigDefaults.CONFIG_PLUGIN_WEB_SEARCH_PROMPT,
        AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_ENABLED,
        AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_PDF_ENGINE,
        AiConfigDefaults.CONFIG_PLUGIN_RESPONSE_HEALING_ENABLED,
        "ai.chat.context.recentWindowMessages",
        "ai.chat.context.summaryTriggerMessages"
    );

    private final Map<String, String> previousConfigValues = new HashMap<>();
    private final DatabaseManager db = DatabaseManager.getInstance();
    private AiClient originalActiveClient;
    private AiMode originalMode;

    @BeforeEach
    void setUp() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        originalActiveClient = (AiClient) ACTIVE_CLIENT_FIELD.get(factory);
        originalMode = (AiMode) CURRENT_MODE_FIELD.get(factory);

        for (String key : OVERRIDDEN_KEYS) {
            previousConfigValues.put(key, ConfigManager.getProperty(key));
        }
    }

    @AfterEach
    void tearDown() throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, originalActiveClient);
        CURRENT_MODE_FIELD.set(factory, originalMode);

        for (Map.Entry<String, String> entry : previousConfigValues.entrySet()) {
            restoreInMemoryConfig(entry.getKey(), entry.getValue());
        }
    }

    @Test
    void keepsContextIsolatedPerConversationForRealAiRequests() throws Exception {
        useActiveAiClient(new EchoStubAiClient());
        ChatBotService service = new ChatBotService();
        String convA = db.createChatConversation("Service AI Test A").getId();
        String convB = db.createChatConversation("Service AI Test B").getId();

        String responseA = service.sendMessage(convA, "Статус проекта A").get(5, TimeUnit.SECONDS);
        String responseB = service.sendMessage(convB, "Статус проекта B").get(5, TimeUnit.SECONDS);

        assertNotNull(responseA);
        assertNotNull(responseB);

        String contextA = joinContext(service.buildContext(convA, ChatContextMode.FULL).entries());
        String contextB = joinContext(service.buildContext(convB, ChatContextMode.FULL).entries());

        assertEquals(2, service.getHistorySize(convA));
        assertEquals(2, service.getHistorySize(convB));

        assertTrue(contextA.contains("Статус проекта A"));
        assertTrue(contextA.contains(responseA));
        assertFalse(contextA.contains("Статус проекта B"));

        assertTrue(contextB.contains("Статус проекта B"));
        assertTrue(contextB.contains(responseB));
        assertFalse(contextB.contains("Статус проекта A"));
    }

    @Test
    void continuesResponseAfterBudgetDepletionSignal() throws Exception {
        setInMemoryConfig("ai.request.continuation.enabled", "true");
        setInMemoryConfig("ai.request.continuation.maxSteps", "1");
        setInMemoryConfig("ai.request.continuation.minPartialChars", "20");

        String partial = "Это частичный ответ, который успел сформироваться до исчерпания budget.";
        String continuationTail = " Продолжение после budget depletion получено успешно.";
        ContinuationStubAiClient client = new ContinuationStubAiClient(partial, continuationTail);
        useActiveAiClient(client);

        ChatBotService service = new ChatBotService();
        String conversationId = db.createChatConversation("Service Continuation Test").getId();
        List<ChatRequestEvent> events = new CopyOnWriteArrayList<>();
        ChatRequestSubscription subscription = service.subscribeToRequestEvents(events::add);

        String response;
        try {
            response = service.sendMessage(conversationId, "Сформируй подробный план").get(8, TimeUnit.SECONDS);
        } finally {
            subscription.close();
        }

        assertTrue(client.continuationPromptRequested.get(), "Service should request continuation follow-up");
        assertTrue(response.startsWith(partial));
        assertTrue(response.contains("Продолжение после budget depletion"));

        assertTrue(events.stream().anyMatch(event ->
            event.state() == ChatRequestState.RETRYING
                && "true".equals(event.metadata().get("continuation"))
        ));
        assertTrue(events.stream().anyMatch(event ->
            event.state() == ChatRequestState.DONE
                && "true".equals(event.metadata().get("continuation"))
        ));
        assertFalse(events.stream().anyMatch(event -> event.state() == ChatRequestState.FAILED));
    }

    @Test
    void cancelsInFlightRequestAndCleansUpState() throws Exception {
        BlockingStubAiClient client = new BlockingStubAiClient();
        useActiveAiClient(client);

        ChatBotService service = new ChatBotService();
        String conversationId = db.createChatConversation("Service Cancel Test").getId();
        List<ChatRequestEvent> events = new CopyOnWriteArrayList<>();
        AtomicReference<String> requestIdRef = new AtomicReference<>();
        ChatRequestSubscription subscription = service.subscribeToRequestEvents(event -> {
            events.add(event);
            if (requestIdRef.get() == null && event.requestId() != null && !event.requestId().isBlank()) {
                requestIdRef.set(event.requestId());
            }
        });

        CompletableFuture<String> request = service.sendMessage(conversationId, "Нужен длинный ответ");
        assertTrue(awaitCondition(() -> requestIdRef.get() != null, Duration.ofSeconds(2)));

        String requestId = requestIdRef.get();
        assertNotNull(requestId);
        assertTrue(service.cancelRequest(requestId));

        assertTrue(awaitCondition(
            () -> events.stream().anyMatch(event -> event.state() == ChatRequestState.CANCELLED),
            Duration.ofSeconds(3)
        ));
        assertTrue(awaitCondition(() -> isFutureCancelledOrNull(request), Duration.ofSeconds(2)));

        assertTrue(awaitCondition(() -> inFlightRequests(service).isEmpty(), Duration.ofSeconds(3)));
        assertFalse(service.cancelRequest(requestId), "Second cancellation should return false after cleanup");

        subscription.close();
    }

    @Test
    void passesReasoningEffortToAssistantRequestsForSupportedModels() throws Exception {
        setInMemoryConfig(AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EFFORT, "high");
        CapturingStubAiClient client = new CapturingStubAiClient("openai/gpt-5.4");
        useActiveAiClient(client);

        ChatBotService service = new ChatBotService();
        String conversationId = db.createChatConversation("Service Reasoning Supported").getId();

        String response = service.sendMessage(conversationId, "Собери план действий").get(5, TimeUnit.SECONDS);

        assertEquals("captured", response);
        assertNotNull(client.lastOptions.get());
        assertEquals("high", client.lastOptions.get().reasoningEffort());
    }

    @Test
    void skipsReasoningEffortForUnsupportedModels() throws Exception {
        setInMemoryConfig(AiConfigDefaults.CONFIG_ASSISTANT_REASONING_EFFORT, "high");
        CapturingStubAiClient client = new CapturingStubAiClient("claude-haiku-4.5");
        useActiveAiClient(client);

        ChatBotService service = new ChatBotService();
        String conversationId = db.createChatConversation("Service Reasoning Unsupported").getId();

        String response = service.sendMessage(conversationId, "Собери план действий").get(5, TimeUnit.SECONDS);

        assertEquals("captured", response);
        assertNotNull(client.lastOptions.get());
        assertNull(client.lastOptions.get().reasoningEffort());
    }

    @Test
    void propagatesMediaInputsAndPluginOptionsForMediaAttachFlow() throws Exception {
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_WEB_ENABLED, "true");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_WEB_ENGINE, "exa");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_WEB_MAX_RESULTS, "3");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_WEB_SEARCH_PROMPT, "Найти источники");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_ENABLED, "true");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_FILE_PARSER_PDF_ENGINE, "mistral-ocr");
        setInMemoryConfig(AiConfigDefaults.CONFIG_PLUGIN_RESPONSE_HEALING_ENABLED, "true");

        List<String> previousMultimodal = ConfigManager.getExternalApiMultimodalModels();
        List<String> previousFile = ConfigManager.getExternalApiFileInputModels();
        try {
            ConfigManager.setExternalApiMultimodalModels(List.of("openai/gpt-4o"));
            ConfigManager.setExternalApiFileInputModels(List.of("openai/gpt-4o"));

            CapturingMediaStubAiClient client = new CapturingMediaStubAiClient("openai/gpt-4o");
            useActiveAiClient(client);

            Path image = Files.createTempFile("nf-chat-", ".png");
            Files.write(image, new byte[] {1, 2, 3});
            Path pdf = Files.createTempFile("nf-chat-", ".pdf");
            Files.writeString(pdf, "fake pdf content");

            ChatBotService service = new ChatBotService();
            String conversationId = db.createChatConversation("Service Media Attach Test").getId();

            String response = service.sendMessageWithMediaAttachments(
                    conversationId,
                    "Разбери вложения",
                    List.of(image, pdf)).get(5, TimeUnit.SECONDS);

            assertEquals("captured-media", response);
            assertEquals("Разбери вложения", client.lastUserText.get());
            assertNotNull(client.lastOptions.get());
            assertNotNull(client.lastMediaInputs.get());
            assertEquals(2, client.lastMediaInputs.get().size());
            assertTrue(client.lastMediaInputs.get().stream().anyMatch(input -> input.kind() == AiMediaInputKind.IMAGE));
            assertTrue(client.lastMediaInputs.get().stream().anyMatch(input -> input.kind() == AiMediaInputKind.DOCUMENT));

            AiRequestOptions.PluginOptions plugins = client.lastOptions.get().pluginOptions();
            assertNotNull(plugins);
            assertNotNull(plugins.web());
            assertTrue(plugins.web().enabled());
            assertEquals("exa", plugins.web().engine());
            assertEquals(3, plugins.web().maxResults());
            assertEquals("Найти источники", plugins.web().searchPrompt());
            assertNotNull(plugins.fileParser());
            assertTrue(plugins.fileParser().enabled());
            assertEquals("mistral-ocr", plugins.fileParser().pdfEngine());
            assertNotNull(plugins.responseHealing());
            assertTrue(plugins.responseHealing().enabled());
        } finally {
            ConfigManager.setExternalApiMultimodalModels(previousMultimodal);
            ConfigManager.setExternalApiFileInputModels(previousFile);
        }
    }

    @Test
    void autoSummarizesContextBeforeSendingWhenBudgetThresholdExceeded() throws Exception {
        setInMemoryConfig("ai.chat.context.recentWindowMessages", "4");
        setInMemoryConfig("ai.chat.context.summaryTriggerMessages", "4");

        List<AiDiscoveredModelInfo> previousCatalog = ConfigManager.getExternalApiModelCatalog();
        Integer previousAssistantMaxTokens = ConfigManager.getAssistantTextMaxTokens();
        try {
            ConfigManager.setAssistantTextMaxTokens(128);
            ConfigManager.setExternalApiModelCatalog(List.of(
                new AiDiscoveredModelInfo(
                    "openai/gpt-5.4",
                    "chat",
                    true,
                    true,
                    false,
                    true,
                    AiTextModelContextMetadata.fromTokens(700),
                    null)
            ));

            CapturingStubAiClient client = new CapturingStubAiClient("openai/gpt-5.4");
            useActiveAiClient(client);

            ChatBotService service = new ChatBotService();
            String conversationId = db.createChatConversation("Auto summarize before send").getId();
            service.replaceConversationHistory(conversationId, List.of(
                new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 1 " + "x".repeat(900)),
                new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 1 " + "y".repeat(820)),
                new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 2 " + "z".repeat(900)),
                new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 2 " + "k".repeat(820)),
                new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 3 " + "m".repeat(900)),
                new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 3 " + "n".repeat(820)),
                new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 4 " + "p".repeat(900)),
                new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 4 " + "q".repeat(820)),
                new AiRequestOptions.ChatHistoryEntry("user", "Сообщение 5 " + "r".repeat(900)),
                new AiRequestOptions.ChatHistoryEntry("assistant", "Ответ 5 " + "s".repeat(820))
            ));

            List<ChatRequestEvent> events = new CopyOnWriteArrayList<>();
            ChatRequestSubscription subscription = service.subscribeToRequestEvents(events::add);
            String response;
            try {
                response = service.sendMessage(conversationId, "Финальный вопрос после сжатия").get(5, TimeUnit.SECONDS);
            } finally {
                subscription.close();
            }

            assertEquals("captured", response);
            assertNotNull(client.lastOptions.get());
            assertTrue(client.lastOptions.get().conversationHistory().stream().anyMatch(entry ->
                "system".equalsIgnoreCase(entry.role())
                    && entry.content() != null
                    && entry.content().startsWith("Сводка предыдущего контекста"))
            );
            assertTrue(events.stream().anyMatch(event -> event.state() == ChatRequestState.SUMMARIZING));
            assertTrue(events.stream().anyMatch(event -> event.state() == ChatRequestState.WAITING_PROVIDER));
        } finally {
            ConfigManager.setExternalApiModelCatalog(previousCatalog);
            ConfigManager.setAssistantTextMaxTokens(previousAssistantMaxTokens);
        }
    }

    private void useActiveAiClient(AiClient aiClient) throws IllegalAccessException {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, aiClient);
        CURRENT_MODE_FIELD.set(factory, aiClient.getMode());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CompletableFuture<?>> inFlightRequests(ChatBotService service) {
        try {
            return (Map<String, CompletableFuture<?>>) IN_FLIGHT_FIELD.get(service);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to read in-flight request map", e);
        }
    }

    private static String joinContext(List<AiRequestOptions.ChatHistoryEntry> entries) {
        StringBuilder joined = new StringBuilder();
        for (AiRequestOptions.ChatHistoryEntry entry : entries) {
            if (entry == null || entry.content() == null) {
                continue;
            }
            if (!joined.isEmpty()) {
                joined.append('\n');
            }
            joined.append(entry.content());
        }
        return joined.toString();
    }

    private static boolean isFutureCancelledOrNull(CompletableFuture<String> future) {
        if (future.isCancelled()) {
            return true;
        }
        try {
            return future.get(1, TimeUnit.SECONDS) == null;
        } catch (CancellationException e) {
            return true;
        } catch (ExecutionException e) {
            return e.getCause() instanceof CancellationException;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException e) {
            return false;
        }
    }

    private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(30L);
        }
        return condition.getAsBoolean();
    }

    private static Field findFactoryField(String fieldName) {
        try {
            Field field = AiClientFactory.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access AiClientFactory field: " + fieldName, ex);
        }
    }

    private static Field findConfigField(String fieldName) {
        try {
            Field field = ConfigManager.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access ConfigManager field: " + fieldName, ex);
        }
    }

    private static void restoreInMemoryConfig(String key, String value) {
        setInMemoryConfig(key, value);
    }

    private static void setInMemoryConfig(String key, String value) {
        try {
            Properties properties = (Properties) CONFIG_PROPERTIES_FIELD.get(null);
            if (value == null) {
                properties.remove(key);
            } else {
                properties.setProperty(key, value);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unable to update in-memory config for key: " + key, e);
        }
    }

    private static Field findServiceField(String fieldName) {
        try {
            Field field = ChatBotService.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to access ChatBotService field: " + fieldName, ex);
        }
    }

    private abstract static class BaseStubAiClient implements AiClient {
        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return CompletableFuture.completedFuture(ConnectionTestResult.success(
                "ok",
                getMode(),
                "stub://ai",
                getDefaultModel(),
                "pong",
                1
            ));
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection(String baseUrl, String apiKey) {
            return testConnection();
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testModel(String model) {
            return testConnection();
        }

        @Override
        public CompletableFuture<List<String>> fetchAvailableModels() {
            return CompletableFuture.completedFuture(List.of(getDefaultModel()));
        }

        @Override
        public boolean supportsImages() {
            return false;
        }

        @Override
        public AiMode getMode() {
            return AiMode.EXTERNAL_OPENAI;
        }

        @Override
        public String getDefaultModel() {
            return "primary-model";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getBaseUrl() {
            return "stub://ai";
        }

        @Override
        public void reloadConfiguration() {
            // no-op
        }
    }

    private static final class EchoStubAiClient extends BaseStubAiClient {
        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return CompletableFuture.completedFuture(AiResponse.success("Echo: " + userText, getDefaultModel()));
        }
    }

    private static final class ContinuationStubAiClient extends BaseStubAiClient {
        private final String partial;
        private final String continuationTail;
        private final AtomicBoolean continuationPromptRequested = new AtomicBoolean(false);

        private ContinuationStubAiClient(String partial, String continuationTail) {
            this.partial = partial;
            this.continuationTail = continuationTail;
        }

        @Override
        public boolean supportsStreaming() {
            return true;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessageStreaming(
            String userText,
            AiRequestOptions options,
            java.util.function.Consumer<AiStreamChunk> onChunk
        ) {
            onChunk.accept(AiStreamChunk.delta(partial, getDefaultModel()));
            return CompletableFuture.completedFuture(new AiResponse(
                null,
                false,
                "request budget depleted",
                408,
                getDefaultModel(),
                null,
                null,
                null,
                Instant.now(),
                120L,
                408,
                1
            ));
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            if (userText != null && userText.contains("Продолжи предыдущий ответ")) {
                continuationPromptRequested.set(true);
                return CompletableFuture.completedFuture(AiResponse.success(continuationTail, getDefaultModel()));
            }
            return CompletableFuture.completedFuture(AiResponse.success("unexpected", getDefaultModel()));
        }
    }

    private static final class BlockingStubAiClient extends BaseStubAiClient {
        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return new CompletableFuture<>();
        }
    }

    private static final class CapturingStubAiClient extends BaseStubAiClient {
        private final String model;
        private final AtomicReference<AiRequestOptions> lastOptions = new AtomicReference<>();

        private CapturingStubAiClient(String model) {
            this.model = model;
        }

        @Override
        public String getDefaultModel() {
            return model;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            lastOptions.set(options);
            return CompletableFuture.completedFuture(AiResponse.success("captured", model));
        }
    }

    private static final class CapturingMediaStubAiClient extends BaseStubAiClient {
        private final String model;
        private final AtomicReference<String> lastUserText = new AtomicReference<>();
        private final AtomicReference<AiRequestOptions> lastOptions = new AtomicReference<>();
        private final AtomicReference<List<AiMediaInput>> lastMediaInputs = new AtomicReference<>();

        private CapturingMediaStubAiClient(String model) {
            this.model = model;
        }

        @Override
        public String getDefaultModel() {
            return model;
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            lastUserText.set(userText);
            lastOptions.set(options);
            lastMediaInputs.set(options.mediaInputs());
            return CompletableFuture.completedFuture(AiResponse.success("captured-media", model));
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessageWithMedia(
                String userText,
                List<AiMediaInput> mediaInputs,
                AiRequestOptions options) {
            lastUserText.set(userText);
            lastMediaInputs.set(mediaInputs == null ? List.of() : List.copyOf(mediaInputs));
            lastOptions.set(options.withMediaInputs(mediaInputs));
            return CompletableFuture.completedFuture(AiResponse.success("captured-media", model));
        }
    }
}
