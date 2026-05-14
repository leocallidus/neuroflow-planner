package com.example.neuroflowplanner.ai;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ExternalOpenAiClient Resilience Integration")
class ExternalOpenAiClientResilienceIT {

    private static final List<String> CONFIG_KEYS = List.of(
        "ai.retry.maxAttempts",
        "ai.retry.baseDelayMs",
        "ai.retry.maxDelayMs",
        "ai.retry.jitterRatio",
        "ai.concurrent.maxInFlight",
        "ai.concurrent.acquireTimeoutMs",
        "ai.request.readTimeoutMs",
        "ai.fallback.models",
        "ai.json.parser.mode",
        "ai.json.schema.validation.enabled",
        "ai.json.parser.failOnUnknownProviderProperties"
    );

    private final Map<String, String> configSnapshot = new LinkedHashMap<>();
    private StubAiServer server;
    private static final Field PROPERTIES_FIELD = findPropertiesField();

    @BeforeEach
    void setUp() throws IOException {
        snapshotConfig();
        server = new StubAiServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.close();
        }
        restoreConfig();
    }

    @Test
    @DisplayName("429 + Retry-After -> successful retry")
    void retriesOn429WithRetryAfterAndSucceeds() {
        configureFastPolicy(3, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/models".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            int current = requestCount.incrementAndGet();
            if (current == 1) {
                return StubResponse.json(429, "{\"error\":\"too many requests\"}", Map.of("Retry-After", "0"));
            }
            return StubResponse.json(200, "{\"data\":[{\"id\":\"primary-model\"}]}");
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        ConnectionTestResult result = client.testConnection().join();

        assertTrue(result.success());
        assertEquals(2, requestCount.get());
    }

    @Test
    @DisplayName("503 -> retry -> fallback model -> success")
    void retriesProviderErrorAndFallsBackToConfiguredModel() {
        configureFastPolicy(2, "fallback-model");

        AtomicInteger totalRequests = new AtomicInteger();
        AtomicInteger primaryModelRequests = new AtomicInteger();
        AtomicInteger fallbackModelRequests = new AtomicInteger();

        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            totalRequests.incrementAndGet();
            String body = request.body();
            if (body.contains("\"model\":\"primary-model\"")) {
                primaryModelRequests.incrementAndGet();
                return StubResponse.json(503, "{\"error\":\"provider unavailable\"}");
            }
            if (body.contains("\"model\":\"fallback-model\"")) {
                fallbackModelRequests.incrementAndGet();
                return StubResponse.json(200, chatCompletion("fallback success"));
            }
            return StubResponse.json(500, "{\"error\":\"unexpected model\"}");
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder()
            .model("primary-model")
            .build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertTrue(response.success());
        assertEquals("fallback success", response.content());
        assertEquals(2, primaryModelRequests.get());
        assertEquals(1, fallbackModelRequests.get());
        assertEquals(3, totalRequests.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403})
    @DisplayName("400/401/403 are fail-fast without retries")
    void deterministicClientErrorsAreFailFast(int statusCode) {
        configureFastPolicy(5, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            requestCount.incrementAndGet();
            return StubResponse.json(statusCode, "{\"error\":\"client error\"}");
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder()
            .model("primary-model")
            .build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertFalse(response.success());
        assertEquals(statusCode, response.errorCode());
        assertEquals(statusCode, response.httpStatus());
        assertEquals(1, response.attempts());
        assertEquals(1, requestCount.get());
    }

    @Test
    @DisplayName("JSON contract: reordered fields are parsed successfully")
    void parsesReorderedFieldsFromStubServer() {
        configureFastPolicy(3, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            requestCount.incrementAndGet();
            String body = """
                    {
                      "usage": {"prompt_tokens": 5, "completion_tokens": 1},
                      "choices": [{"finish_reason":"stop","message":{"content":"Reordered is OK","role":"assistant"},"index":0}],
                      "object": "chat.completion",
                      "id": "chatcmpl-contract-1"
                    }
                    """;
            return StubResponse.json(200, body);
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder().model("primary-model").build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertTrue(response.success());
        assertEquals("Reordered is OK", response.content());
        assertEquals(1, requestCount.get());
    }

    @Test
    @DisplayName("JSON contract: unknown fields do not break parsing")
    void ignoresUnknownFieldsFromStubServer() {
        configureFastPolicy(3, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            requestCount.incrementAndGet();
            String body = """
                    {
                      "traceId": "trace-123",
                      "choices": [{
                        "index": 0,
                        "message": {"role":"assistant","content":"Unknown fields are ignored","extra":"x"},
                        "new_provider_field": {"k": "v"}
                      }],
                      "provider_debug": {"node":"edge-gw-2"}
                    }
                    """;
            return StubResponse.json(200, body);
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder().model("primary-model").build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertTrue(response.success());
        assertEquals("Unknown fields are ignored", response.content());
        assertEquals(1, requestCount.get());
    }

    @Test
    @DisplayName("JSON contract: missing required fields fail-fast without retry")
    void missingRequiredFieldsFailFastWithoutRetry() {
        configureFastPolicy(5, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            requestCount.incrementAndGet();
            String body = """
                    {
                      "choices": [
                        {
                          "index": 0,
                          "message": {"role":"assistant"}
                        }
                      ]
                    }
                    """;
            return StubResponse.json(200, body);
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder().model("primary-model").build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertFalse(response.success());
        assertEquals(200, response.httpStatus());
        assertEquals(1, response.attempts());
        assertEquals(1, requestCount.get());
        assertTrue(response.errorMessage().contains("Некорректный формат ответа AI"));
    }

    @Test
    @DisplayName("JSON contract: incompatible field types fail-fast without retry")
    void incompatibleFieldTypesFailFastWithoutRetry() {
        configureFastPolicy(5, "fallback-model");

        AtomicInteger requestCount = new AtomicInteger();
        server.setHandler(request -> {
            if (!"/v1/chat/completions".equals(request.path())) {
                return StubResponse.json(404, "{\"error\":\"not found\"}");
            }
            requestCount.incrementAndGet();
            String body = """
                    {
                      "choices": [
                        {
                          "message": {"role":"assistant","content": 42}
                        }
                      ]
                    }
                    """;
            return StubResponse.json(200, body);
        });

        ExternalOpenAiClient client = new ExternalOpenAiClient(server.baseUrl("/v1"), "test-key", "primary-model");
        AiRequestOptions options = AiRequestOptions.builder().model("primary-model").build();

        AiResponse response = client.sendChatMessage("hello", options).join();

        assertFalse(response.success());
        assertEquals(200, response.httpStatus());
        assertEquals(1, response.attempts());
        assertEquals(1, requestCount.get());
        assertTrue(response.errorMessage().contains("Некорректный формат ответа AI"));
    }

    private void configureFastPolicy(int maxAttempts, String fallbackModels) {
        setRuntimeConfig("ai.retry.maxAttempts", String.valueOf(maxAttempts));
        setRuntimeConfig("ai.retry.baseDelayMs", "5");
        setRuntimeConfig("ai.retry.maxDelayMs", "25");
        setRuntimeConfig("ai.retry.jitterRatio", "0");
        setRuntimeConfig("ai.concurrent.maxInFlight", "2");
        setRuntimeConfig("ai.concurrent.acquireTimeoutMs", "500");
        setRuntimeConfig("ai.request.readTimeoutMs", "1500");
        setRuntimeConfig("ai.fallback.models", fallbackModels);
        setRuntimeConfig("ai.json.parser.mode", "jackson");
        setRuntimeConfig("ai.json.schema.validation.enabled", "true");
        setRuntimeConfig("ai.json.parser.failOnUnknownProviderProperties", "false");
        AiObjectMapperFactory.reloadFromConfig();
    }

    private void snapshotConfig() {
        configSnapshot.clear();
        for (String key : CONFIG_KEYS) {
            configSnapshot.put(key, ConfigManager.getProperty(key));
        }
    }

    private void restoreConfig() {
        for (Map.Entry<String, String> entry : configSnapshot.entrySet()) {
            setRuntimeConfig(entry.getKey(), entry.getValue());
        }
        AiObjectMapperFactory.reloadFromConfig();
    }

    private static Field findPropertiesField() {
        try {
            Field field = ConfigManager.class.getDeclaredField("properties");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties", ex);
        }
    }

    private Properties runtimeProperties() {
        try {
            return (Properties) PROPERTIES_FIELD.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to read ConfigManager.properties", ex);
        }
    }

    private void setRuntimeConfig(String key, String value) {
        Properties properties = runtimeProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    private static String chatCompletion(String content) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"}}]}";
    }

    private static final class StubAiServer implements AutoCloseable {
        private static final int SOCKET_BACKLOG = 32;

        private final ServerSocket socket;
        private final Thread thread;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger sequence = new AtomicInteger(0);

        private volatile Handler handler = request -> StubResponse.json(404, "{\"error\":\"no handler\"}");

        private StubAiServer() throws IOException {
            this.socket = new ServerSocket(0, SOCKET_BACKLOG, InetAddress.getByName("127.0.0.1"));
            this.thread = new Thread(this::serveLoop, "external-openai-resilience-it-server");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        String baseUrl(String prefix) {
            String normalizedPrefix = prefix == null ? "" : prefix.trim();
            return "http://127.0.0.1:" + socket.getLocalPort() + normalizedPrefix;
        }

        void setHandler(Handler handler) {
            this.handler = handler == null ? request -> StubResponse.json(500, "{\"error\":\"null handler\"}") : handler;
        }

        @Override
        public void close() throws IOException {
            running.set(false);
            socket.close();
            try {
                thread.join(1000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }

        private void serveLoop() {
            while (running.get()) {
                try (Socket client = socket.accept()) {
                    handleClient(client);
                } catch (IOException ignored) {
                    if (!running.get()) {
                        return;
                    }
                }
            }
        }

        private void handleClient(Socket client) throws IOException {
            StubRequest request = readRequest(client.getInputStream(), sequence.incrementAndGet());
            if (request == null) {
                return;
            }
            StubResponse response = handler.handle(request);
            writeResponse(client.getOutputStream(), response);
        }

        private StubRequest readRequest(InputStream input, int requestNo) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isBlank()) {
                return null;
            }

            String[] requestLineParts = requestLine.split(" ");
            String method = requestLineParts.length > 0 ? requestLineParts[0].trim().toUpperCase(Locale.ROOT) : "GET";
            String path = requestLineParts.length > 1 ? requestLineParts[1].trim() : "/";

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readLine(input)) != null && !line.isEmpty()) {
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                headers.put(key, value);
            }

            int contentLength = parseContentLength(headers);
            byte[] bodyBytes = contentLength <= 0 ? new byte[0] : input.readNBytes(contentLength);
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            return new StubRequest(method, path, headers, body, requestNo);
        }

        private String readLine(InputStream input) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int previous = -1;
            while (true) {
                int current = input.read();
                if (current == -1) {
                    if (previous != -1) {
                        buffer.write(previous);
                    }
                    if (buffer.size() == 0) {
                        return null;
                    }
                    return buffer.toString(StandardCharsets.ISO_8859_1);
                }
                if (previous == '\r' && current == '\n') {
                    return buffer.toString(StandardCharsets.ISO_8859_1);
                }
                if (previous != -1) {
                    buffer.write(previous);
                }
                previous = current;
            }
        }

        private int parseContentLength(Map<String, String> headers) {
            String rawValue = headers.get("content-length");
            if (rawValue == null || rawValue.isBlank()) {
                return 0;
            }
            try {
                return Integer.parseInt(rawValue.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private void writeResponse(OutputStream output, StubResponse response) throws IOException {
            StubResponse safeResponse = response == null ? StubResponse.json(500, "{\"error\":\"null response\"}") : response;
            byte[] bodyBytes = safeResponse.body().getBytes(StandardCharsets.UTF_8);

            StringBuilder headers = new StringBuilder();
            headers.append("HTTP/1.1 ")
                .append(safeResponse.statusCode())
                .append(' ')
                .append(statusText(safeResponse.statusCode()))
                .append("\r\n");
            headers.append("Content-Type: application/json\r\n");
            for (Map.Entry<String, String> header : safeResponse.headers().entrySet()) {
                headers.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
            }
            headers.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            headers.append("Connection: close\r\n\r\n");

            output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.write(bodyBytes);
            output.flush();
        }

        private String statusText(int statusCode) {
            return switch (statusCode) {
                case 200 -> "OK";
                case 400 -> "Bad Request";
                case 401 -> "Unauthorized";
                case 403 -> "Forbidden";
                case 404 -> "Not Found";
                case 429 -> "Too Many Requests";
                case 502 -> "Bad Gateway";
                case 503 -> "Service Unavailable";
                default -> "Error";
            };
        }
    }

    private interface Handler {
        StubResponse handle(StubRequest request);
    }

    private record StubRequest(
        String method,
        String path,
        Map<String, String> headers,
        String body,
        int requestNo
    ) {
    }

    private record StubResponse(int statusCode, String body, Map<String, String> headers) {
        static StubResponse json(int statusCode, String body) {
            return new StubResponse(statusCode, body == null ? "" : body, Map.of());
        }

        static StubResponse json(int statusCode, String body, Map<String, String> headers) {
            Map<String, String> safeHeaders = headers == null ? Map.of() : new LinkedHashMap<>(headers);
            return new StubResponse(statusCode, body == null ? "" : body, safeHeaders);
        }
    }
}
