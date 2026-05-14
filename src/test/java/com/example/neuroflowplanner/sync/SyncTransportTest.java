package com.example.neuroflowplanner.sync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.SyncConfigDefaults;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncTransport Tests")
class SyncTransportTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        ConfigManager.setProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
                String.valueOf(SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD_DEFAULT));
        ConfigManager.setProperty(
                SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS,
                String.valueOf(SyncConfigDefaults.CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS_DEFAULT));
    }

    @Test
    @DisplayName("parses machine-readable backend error with retry metadata")
    void parsesMachineReadableBackendError() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/login", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Retry-After", "3");
            writeJson(exchange, 429, """
                {
                  "error": {
                    "status": 429,
                    "code": "rate_limit_exceeded",
                    "message": "Too many failed authentication attempts. Try again later.",
                    "details": {"retry_after_seconds": 3},
                    "category": "auth",
                    "retryable": true,
                    "request_id": "req-429"
                  }
                }
                """);
        });
        server.start();

        SyncTransport transport = new SyncTransport(
                baseUrl(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10));

        SyncHttpException error = assertThrows(
                SyncHttpException.class,
                () -> transport.postJson("/auth/login", Map.of("email", "user@example.com"), null, Object.class));
        assertEquals(429, error.statusCode());
        assertEquals("rate_limit_exceeded", error.errorCode());
        assertEquals("auth", error.category());
        assertTrue(error.retryable());
        assertEquals(3L, error.retryAfterSeconds());
        assertEquals("req-429", error.requestId());
    }

    @Test
    @DisplayName("formats validation error details into actionable message")
    void formatsValidationErrorDetailsIntoActionableMessage() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth/register", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            writeJson(exchange, 422, """
                {
                  "error": {
                    "status": 422,
                    "code": "validation_error",
                    "message": "Request validation failed.",
                    "details": {
                      "errors": [
                        {
                          "type": "string_too_short",
                          "loc": ["body", "password"],
                          "msg": "String should have at least 8 characters",
                          "input": "123",
                          "ctx": {"min_length": 8}
                        }
                      ]
                    },
                    "category": "validation",
                    "retryable": false,
                    "request_id": "req-422"
                  }
                }
                """);
        });
        server.start();

        SyncTransport transport = new SyncTransport(
                baseUrl(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10));

        SyncHttpException error = assertThrows(
                SyncHttpException.class,
                () -> transport.postJson("/auth/register", Map.of("email", "user@example.com"), null, Object.class));
        assertEquals(422, error.statusCode());
        assertEquals("validation_error", error.errorCode());
        assertEquals("Пароль должен содержать минимум 8 символов.", error.getMessage());
    }

    @Test
    @DisplayName("opens circuit breaker after consecutive retryable failures")
    void opensCircuitBreakerAfterConsecutiveRetryableFailures() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health/live", exchange -> {
            requestCount.incrementAndGet();
            writeJson(exchange, 503, """
                {
                  "error": {
                    "status": 503,
                    "code": "service_unavailable",
                    "message": "Backend is temporarily unavailable.",
                    "details": {},
                    "category": "sync",
                    "retryable": true,
                    "request_id": "req-503"
                  }
                }
                """);
        });
        server.start();

        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_FAILURE_THRESHOLD, "2");
        ConfigManager.setProperty(SyncConfigDefaults.CONFIG_CLOUD_SYNC_CIRCUIT_BREAKER_COOLDOWN_MS, "5000");

        SyncTransport transport = new SyncTransport(
                baseUrl(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                1,
                Duration.ofMillis(10),
                Duration.ofMillis(10));

        assertThrows(SyncHttpException.class, () -> transport.getJson("/health/live", null, Object.class));
        assertThrows(SyncHttpException.class, () -> transport.getJson("/health/live", null, Object.class));

        SyncCircuitOpenException open = assertThrows(
                SyncCircuitOpenException.class,
                () -> transport.getJson("/health/live", null, Object.class));
        assertTrue(open.retryAfterMillis() > 0L);
        assertEquals(2, requestCount.get(), "circuit should short-circuit the third request");
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
