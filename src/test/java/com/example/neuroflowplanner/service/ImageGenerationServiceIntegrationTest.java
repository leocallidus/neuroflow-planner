package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiImageInput;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.AiStreamChunk;
import com.example.neuroflowplanner.ai.ConnectionTestResult;
import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.service.imageflow.ImageRequestEvent;
import com.example.neuroflowplanner.service.imageflow.ImageRequestState;
import com.example.neuroflowplanner.service.imageflow.ImageRequestSubscription;
import com.example.neuroflowplanner.service.imagejob.ImageJobSnapshot;
import com.example.neuroflowplanner.service.imagejob.ImageJobState;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import com.example.neuroflowplanner.util.ConfigManager;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.cert.Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageGenerationServiceIntegrationTest extends IsolatedTestDataFixture {

    private static final Field ACTIVE_CLIENT_FIELD = findFactoryField("activeClient");
    private static final Field CURRENT_MODE_FIELD = findFactoryField("currentMode");
    private static final Field HTTP_CLIENT_FIELD = findServiceField("client");
    private static final Field IN_FLIGHT_REQUESTS_FIELD = findServiceField("inFlightRequests");
    private static final Field IN_FLIGHT_RUNTIMES_FIELD = findServiceField("inFlightRuntimes");

    private final ImageGenerationService service = ImageGenerationService.getInstance();
    private final DatabaseManager db = DatabaseManager.getInstance();

    private AiClient originalActiveClient;
    private AiMode originalMode;
    private HttpClient originalHttpClient;

    @BeforeEach
    void setUp() throws Exception {
        AiClientFactory factory = AiClientFactory.getInstance();
        originalActiveClient = (AiClient) ACTIVE_CLIENT_FIELD.get(factory);
        originalMode = (AiMode) CURRENT_MODE_FIELD.get(factory);
        originalHttpClient = (HttpClient) HTTP_CLIENT_FIELD.get(service);

        ACTIVE_CLIENT_FIELD.set(factory, new ImageCapableStubAiClient());
        CURRENT_MODE_FIELD.set(factory, AiMode.EXTERNAL_OPENAI);

        clearMapField(IN_FLIGHT_REQUESTS_FIELD, service);
        clearMapField(IN_FLIGHT_RUNTIMES_FIELD, service);

        ConfigManager.setProperty("external.api.baseUrl", "http://provider.test/v1");
        ConfigManager.setProperty("external.api.key", "test-key");
        ConfigManager.setProperty("ai.image.request.totalBudgetMs", "10000");
        ConfigManager.setProperty("ai.image.request.heartbeatIntervalMs", "500");
        ConfigManager.setProperty("ai.image.retry.submit.maxAttempts", "1");
        ConfigManager.setProperty("ai.image.retry.poll.maxAttempts", "2");
        ConfigManager.setProperty("ai.image.retry.download.maxAttempts", "2");
        ConfigManager.setProperty("ai.image.retry.baseDelayMs", "100");
        ConfigManager.setProperty("ai.image.retry.maxDelayMs", "100");
        ConfigManager.setProperty("ai.image.poll.initialDelayMs", "250");
        ConfigManager.setProperty("ai.image.poll.maxDelayMs", "250");
        ConfigManager.setProperty("ai.image.poll.jitterRatio", "0.0");
    }

    @AfterEach
    void tearDown() throws Exception {
        AiClientFactory factory = AiClientFactory.getInstance();
        ACTIVE_CLIENT_FIELD.set(factory, originalActiveClient);
        CURRENT_MODE_FIELD.set(factory, originalMode);
        HTTP_CLIENT_FIELD.set(service, originalHttpClient);
        clearMapField(IN_FLIGHT_REQUESTS_FIELD, service);
        clearMapField(IN_FLIGHT_RUNTIMES_FIELD, service);
    }

    @Test
    void completesHappyPathAndPublishesFullLifecycle() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"req-happy","status":"pending","model":"google/gemini-2.5-flash-image"}
                """));
        client.on("GET", "/v1/media/req-happy", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"req-happy","status":"completed","data":{"url":"http://provider.test/downloads/req-happy.png"}}
                """));
        client.on("GET", "/downloads/req-happy.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{1, 2, 3, 4}));
        HTTP_CLIENT_FIELD.set(service, client);

        String conversationId = db.createChatConversation("Image Happy Path").getId();
        List<ImageRequestEvent> events = new ArrayList<>();
        try (ImageRequestSubscription ignored = service.subscribeToRequestEvents(events::add)) {
            ImageGenerationService.ImageGenerationResult result = service.generateImage(
                conversationId,
                "job-happy",
                "Draw a sunrise over the mountains",
                new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "1:1", "", "", "", "", "")
            ).get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            assertTrue(result.savedPath().toFile().exists());
            assertEquals("req-happy", result.requestId());

            ImageJobSnapshot snapshot = service.getJob("job-happy");
            assertEquals(ImageJobState.DONE, snapshot.getState());
            assertEquals("req-happy", snapshot.getRequestId());
            assertTrue(snapshot.getSavedPath().endsWith(".png"));
            assertStateSequence(events, List.of(
                ImageRequestState.QUEUED,
                ImageRequestState.SENDING,
                ImageRequestState.PROVIDER_ACCEPTED,
                ImageRequestState.POLLING,
                ImageRequestState.DOWNLOADING,
                ImageRequestState.SAVING,
                ImageRequestState.DONE
            ));
        }
    }

    @Test
    void retriesPollingAndFallsBackToReserveModel() throws Exception {
        ConfigManager.setProperty("ai.image.fallback.enabled", "true");
        ConfigManager.setProperty("ai.image.fallback.models", "gemini-3-pro-image-preview");
        ConfigManager.setProperty("ai.image.retry.submit.maxAttempts", "1");

        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) -> {
            if (attempt == 1) {
                return ResponsePlan.json(503, "{\"error\":\"primary model unavailable\"}");
            }
            return ResponsePlan.json(200, "{\"id\":\"req-fallback\",\"status\":\"pending\",\"model\":\"google/gemini-3-pro-image-preview\"}");
        });
        client.on("GET", "/v1/media/req-fallback", (request, attempt) -> {
            if (attempt == 1) {
                return ResponsePlan.json(500, "{\"error\":\"temporary poll issue\"}");
            }
            return ResponsePlan.json(200, """
                {"id":"req-fallback","status":"completed","data":{"url":"http://provider.test/downloads/req-fallback.png"}}
                """);
        });
        client.on("GET", "/downloads/req-fallback.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{9, 8, 7}));
        HTTP_CLIENT_FIELD.set(service, client);

        List<ImageRequestEvent> events = new ArrayList<>();
        try (ImageRequestSubscription ignored = service.subscribeToRequestEvents(events::add)) {
            ImageGenerationService.ImageGenerationResult result = service.generateImage(
                "",
                "job-fallback",
                "Create a futuristic skyline",
                new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "16:9", "", "", "", "", "")
            ).get(5, TimeUnit.SECONDS);

            assertNotNull(result);
            ImageJobSnapshot snapshot = service.getJob("job-fallback");
            assertEquals(ImageJobState.DONE, snapshot.getState());
            assertEquals("google/gemini-2.5-flash-image", snapshot.getRequestedModel());
            assertEquals("google/gemini-3-pro-image-preview", snapshot.getActiveModel());
            assertTrue(events.stream().anyMatch(event -> event.state() == ImageRequestState.FALLBACK_MODEL));
            assertTrue(events.stream().anyMatch(event -> event.state() == ImageRequestState.RETRYING));
            assertTrue(events.stream().anyMatch(event -> event.state() == ImageRequestState.RESUMING));
        }
    }

    @Test
    void pauseAndResumeContinueWithExistingRequestId() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) ->
            ResponsePlan.json(200, "{\"id\":\"req-resume\",\"status\":\"pending\",\"model\":\"google/gemini-2.5-flash-image\"}"));
        client.on("GET", "/v1/media/req-resume", (request, attempt) -> {
            if (!completed.get()) {
                return ResponsePlan.json(200, "{\"id\":\"req-resume\",\"status\":\"processing\"}");
            }
            return ResponsePlan.json(200, """
                {"id":"req-resume","status":"completed","data":{"url":"http://provider.test/downloads/req-resume.png"}}
                """);
        });
        client.on("GET", "/downloads/req-resume.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{4, 5, 6}));
        HTTP_CLIENT_FIELD.set(service, client);

        List<ImageRequestEvent> events = new ArrayList<>();
        CompletableFuture<ImageGenerationService.ImageGenerationResult> request;
        try (ImageRequestSubscription ignored = service.subscribeToRequestEvents(events::add)) {
            request = service.generateImage(
                "",
                "job-resume",
                "Draw a calm lake",
                new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "1:1", "", "", "", "", "")
            );

            assertTrue(awaitCondition(() -> {
                ImageJobSnapshot snapshot = service.getJob("job-resume");
                return snapshot != null && !snapshot.getRequestId().isBlank() && snapshot.getState() == ImageJobState.POLLING;
            }, Duration.ofSeconds(3)));

            assertTrue(service.pauseJob("job-resume"));
            assertThrowsFutureFailure(request);

            assertTrue(awaitCondition(() -> {
                ImageJobSnapshot snapshot = service.getJob("job-resume");
                return snapshot != null && snapshot.getState() == ImageJobState.PAUSED;
            }, Duration.ofSeconds(3)));

            String requestIdBeforeResume = service.getJob("job-resume").getRequestId();
            completed.set(true);

            ImageGenerationService.ImageGenerationResult resumed = service.resumeJob("job-resume").get(5, TimeUnit.SECONDS);
            ImageJobSnapshot snapshot = service.getJob("job-resume");

            assertEquals("req-resume", requestIdBeforeResume);
            assertEquals("req-resume", resumed.requestId());
            assertEquals(ImageJobState.DONE, snapshot.getState());
            assertTrue(events.stream().anyMatch(event -> event.state() == ImageRequestState.PAUSED));
            assertTrue(events.stream().anyMatch(event -> event.state() == ImageRequestState.RESUMING));
        }
    }

    @Test
    void cancelMarksJobCancelledAndCleansUpInflightMaps() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) ->
            ResponsePlan.json(200, "{\"id\":\"req-cancel\",\"status\":\"pending\",\"model\":\"google/gemini-2.5-flash-image\"}"));
        client.on("GET", "/v1/media/req-cancel", (request, attempt) ->
            ResponsePlan.json(200, "{\"id\":\"req-cancel\",\"status\":\"processing\"}"));
        HTTP_CLIENT_FIELD.set(service, client);

        CompletableFuture<ImageGenerationService.ImageGenerationResult> request = service.generateImage(
            "",
            "job-cancel",
            "Generate a cancellation scenario",
            new ImageGenerationService.ImageGenerationOptions("nano-banana", "", "1:1", "", "", "", "", "")
        );

        assertTrue(awaitCondition(() -> {
            ImageJobSnapshot snapshot = service.getJob("job-cancel");
            return snapshot != null && !snapshot.getRequestId().isBlank();
        }, Duration.ofSeconds(3)));

        assertTrue(service.cancelRequest("job-cancel"));
        assertThrowsFutureFailure(request);

        assertTrue(awaitCondition(() -> {
            ImageJobSnapshot snapshot = service.getJob("job-cancel");
            return snapshot != null && snapshot.getState() == ImageJobState.CANCELLED;
        }, Duration.ofSeconds(3)));
        assertFalse(getInFlightRequests().containsKey("job-cancel"));
        assertFalse(getInFlightRuntimes().containsKey("job-cancel"));
    }

    @Test
    void gpt5ImageUsesMediaApiAndMediaPollingContract() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) -> {
            return ResponsePlan.json(200, """
                {"id":"aig_gpt5_123","object":"media.generation","status":"pending","model":"openai/gpt-5-image"}
                """);
        });
        client.on("GET", "/v1/media/aig_gpt5_123", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"aig_gpt5_123","status":"completed","data":{"url":"http://provider.test/downloads/gpt5-image.png"}}
                """));
        client.on("GET", "/downloads/gpt5-image.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{7, 7, 7}));
        HTTP_CLIENT_FIELD.set(service, client);

        ImageGenerationService.ImageGenerationResult result = service.generateImage(
            "",
            "job-gpt5-media",
            "Create a test image",
            new ImageGenerationService.ImageGenerationOptions("openai/gpt-5-image", "", "16:9", "", "", "", "", "")
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("aig_gpt5_123", result.requestId());
        assertTrue(result.savedPath().toFile().exists());
    }

    @Test
    void normalizesLegacyImageEndpointConfigAndUsesMediaApiForUnknownModel() throws Exception {
        ConfigManager.setProperty("external.api.baseUrl", "http://provider.test/v1/images/generations");

        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"req-legacy-config","status":"pending","model":"custom/provider-image-model"}
                """));
        client.on("GET", "/v1/media/req-legacy-config", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"req-legacy-config","status":"completed","data":{"url":"http://provider.test/downloads/legacy-config.png"}}
                """));
        client.on("GET", "/downloads/legacy-config.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{3, 1, 4}));
        HTTP_CLIENT_FIELD.set(service, client);

        ImageGenerationService.ImageGenerationResult result = service.generateImage(
            "",
            "job-legacy-config",
            "Create a migration smoke test image",
            new ImageGenerationService.ImageGenerationOptions("custom/provider-image-model", "", "", "", "", "", "", "")
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("req-legacy-config", result.requestId());
        assertTrue(result.savedPath().toFile().exists());
    }

    @Test
    void fallsBackToHistoryEndpointWhenMediaStatusRouteIsUnavailable() throws Exception {
        ScriptedHttpClient client = new ScriptedHttpClient();
        client.on("POST", "/v1/media", (request, attempt) ->
            ResponsePlan.json(200, """
                {"id":"req-history-fallback","status":"pending","model":"openai/gpt-5-image"}
                """));
        client.on("GET", "/v1/media/req-history-fallback", (request, attempt) ->
            ResponsePlan.json(404, "{\"error\":\"not found\"}"));
        client.on("GET", "/v1/history/generations/req-history-fallback", (request, attempt) -> {
            if (attempt == 1) {
                return ResponsePlan.json(200, """
                    {"id":"req-history-fallback","status":"processing","attempts":[]}
                    """);
            }
            return ResponsePlan.json(200, """
                {
                  "id":"req-history-fallback",
                  "status":"completed",
                  "attempts":[
                    {
                      "response":{
                        "result":{
                          "url":"http://provider.test/downloads/history-fallback.png"
                        }
                      }
                    }
                  ]
                }
                """);
        });
        client.on("GET", "/downloads/history-fallback.png", (request, attempt) ->
            ResponsePlan.bytes(200, "image/png", new byte[]{8, 6, 7, 5}));
        HTTP_CLIENT_FIELD.set(service, client);

        ImageGenerationService.ImageGenerationResult result = service.generateImage(
            "",
            "job-history-fallback",
            "Create a compatible fallback image",
            new ImageGenerationService.ImageGenerationOptions("openai/gpt-5-image", "", "1:1", "", "", "", "", "")
        ).get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("req-history-fallback", result.requestId());
        assertTrue(result.savedPath().toFile().exists());
    }

    private static void assertStateSequence(List<ImageRequestEvent> events, List<ImageRequestState> expectedStates) {
        List<ImageRequestState> actual = events.stream().map(ImageRequestEvent::state).toList();
        int cursor = 0;
        for (ImageRequestState state : actual) {
            if (cursor < expectedStates.size() && expectedStates.get(cursor) == state) {
                cursor++;
            }
        }
        assertEquals(expectedStates.size(), cursor, "Expected ordered state subsequence not found in events: " + actual);
    }

    private static void assertThrowsFutureFailure(CompletableFuture<?> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            assertTrue(cause instanceof CancellationException || cause instanceof ImageGenerationService.ImageJobPausedException);
            return;
        } catch (CancellationException ex) {
            return;
        } catch (Exception ex) {
            throw new AssertionError("Unexpected exception type", ex);
        }
        throw new AssertionError("Expected future to complete exceptionally");
    }

    private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(30L);
        }
        return condition.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompletableFuture<?>> getInFlightRequests() throws IllegalAccessException {
        return (Map<String, CompletableFuture<?>>) IN_FLIGHT_REQUESTS_FIELD.get(service);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getInFlightRuntimes() throws IllegalAccessException {
        return (Map<String, Object>) IN_FLIGHT_RUNTIMES_FIELD.get(service);
    }

    @SuppressWarnings("unchecked")
    private static void clearMapField(Field field, Object target) throws IllegalAccessException {
        ((Map<String, ?>) field.get(target)).clear();
    }

    private static Field findFactoryField(String name) {
        try {
            Field field = AiClientFactory.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access AiClientFactory." + name, e);
        }
    }

    private static Field findServiceField(String name) {
        try {
            Field field = ImageGenerationService.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access ImageGenerationService." + name, e);
        }
    }

    private interface RequestHandler {
        ResponsePlan handle(HttpRequest request, int callIndex) throws Exception;
    }

    private record ResponsePlan(int statusCode, HttpHeaders headers, Object body) {
        private static ResponsePlan json(int statusCode, String body) {
            return new ResponsePlan(statusCode, HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (a, b) -> true), body);
        }

        private static ResponsePlan bytes(int statusCode, String contentType, byte[] body) {
            return new ResponsePlan(statusCode, HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (a, b) -> true), body);
        }
    }

    private static final class ScriptedHttpClient extends HttpClient {
        private final Map<String, RequestHandler> handlers = new ConcurrentHashMap<>();
        private final Map<String, Integer> callCounts = new ConcurrentHashMap<>();

        void on(String method, String path, RequestHandler handler) {
            handlers.put(method + " " + path, handler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(1));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException {
            try {
                return sendAsync(request, responseBodyHandler).get(5, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException io) {
                    throw io;
                }
                if (cause instanceof InterruptedException interrupted) {
                    throw interrupted;
                }
                throw new IOException(cause);
            } catch (java.util.concurrent.TimeoutException ex) {
                throw new IOException(ex);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            String key = request.method() + " " + request.uri().getPath();
            RequestHandler handler = handlers.get(key);
            if (handler == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("No scripted handler for " + key));
            }
            int callIndex = callCounts.merge(key, 1, Integer::sum);
            try {
                ResponsePlan plan = handler.handle(request, callIndex);
                @SuppressWarnings("unchecked")
                T typedBody = (T) plan.body();
                return CompletableFuture.completedFuture(new SimpleHttpResponse<>(
                    request,
                    plan.statusCode(),
                    plan.headers(),
                    typedBody
                ));
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("WebSocket not used in image tests");
        }
    }

    private record SimpleHttpResponse<T>(
        HttpRequest request,
        int statusCode,
        HttpHeaders headers,
        T body
    ) implements HttpResponse<T> {

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
    }

    private static final class ImageCapableStubAiClient implements AiClient {
        @Override
        public CompletableFuture<AiResponse> sendChatMessage(String userText, AiRequestOptions options) {
            return CompletableFuture.completedFuture(AiResponse.success("stub", "stub-model"));
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessageWithImages(String userText, List<AiImageInput> images, AiRequestOptions options) {
            return sendChatMessage(userText, options);
        }

        @Override
        public CompletableFuture<AiResponse> sendChatMessageStreaming(String userText, AiRequestOptions options, Consumer<AiStreamChunk> onChunk) {
            return sendChatMessage(userText, options);
        }

        @Override
        public CompletableFuture<ConnectionTestResult> testConnection() {
            return CompletableFuture.completedFuture(ConnectionTestResult.success("ok", AiMode.EXTERNAL_OPENAI, "http://provider.test"));
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
            return CompletableFuture.completedFuture(List.of("nano-banana"));
        }

        @Override
        public boolean supportsImages() {
            return true;
        }

        @Override
        public boolean supportsImageInputs() {
            return true;
        }

        @Override
        public AiMode getMode() {
            return AiMode.EXTERNAL_OPENAI;
        }

        @Override
        public String getDefaultModel() {
            return "nano-banana";
        }

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String getBaseUrl() {
            return "http://provider.test";
        }

        @Override
        public void reloadConfiguration() {
            // no-op
        }
    }
}
