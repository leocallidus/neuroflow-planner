package com.example.neuroflowplanner.ai.resilience;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

public class AiResilienceExecutorFallbackTest {

    private AiResiliencePolicy policyWithFallback;
    private AiResilienceExecutor executor;
    private StubHttpClient httpClient;
    private HttpRequest.Builder requestBuilder;

    @BeforeEach
    void setUp() {
        policyWithFallback = new AiResiliencePolicy(
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                new AiRetryPolicy(2, new AiRetryDelayStrategy(Duration.ofMillis(10), Duration.ofMillis(50), 2.0)),
                new AiConcurrencyLimiter(10, Duration.ofSeconds(1)),
                List.of("model-b", "model-c"),
                true);

        executor = new AiResilienceExecutor(policyWithFallback);
        httpClient = new StubHttpClient();
        requestBuilder = HttpRequest.newBuilder().uri(URI.create("http://localhost"));
    }

    @Test
    void shouldSwitchToFallbackModelWhenPrimaryFails() throws Exception {
        AiCallContext context = new AiCallContext("testMode", "model-a", "http://test", "test");

        HttpResponse<String> badResponse = new StubHttpResponse<>(500, "Error");
        HttpResponse<String> goodResponse = new StubHttpResponse<>(200, "OK");

        // Fail 2 times with model-a (exhausting maxAttempts=2), then succeed with model-b
        httpClient.addResponse(CompletableFuture.completedFuture(badResponse));  // attempt 1, model-a
        httpClient.addResponse(CompletableFuture.completedFuture(badResponse));  // attempt 2, model-a -> triggers fallback
        httpClient.addResponse(CompletableFuture.completedFuture(goodResponse)); // attempt 1, model-b -> success

        BiFunction<HttpResponse<String>, AiCallContext, String> responseMapper = (resp, ctx) -> ctx.getModel();

        String result = executor.executeWithResilience(
                context,
                httpClient,
                ctx -> requestBuilder,
                HttpResponse.BodyHandlers.ofString(),
                responseMapper).get();

        // Should be "model-b" because that's the active model when it succeeded
        assertEquals("model-b", result);
        assertTrue(context.isFallbackUsed());
        assertEquals(1, context.getFallbackIndex());
        assertEquals(1, context.getAttempt()); // Attempts reset after fallback
    }

    @Test
    void shouldFailIfAllFallbacksExhausted() {
        AiCallContext context = new AiCallContext("testMode", "model-a", "http://test", "test");

        HttpResponse<String> badResponse = new StubHttpResponse<>(500, "Error");

        // Always fail
        httpClient.setDefaultResponse(CompletableFuture.completedFuture(badResponse));

        BiFunction<HttpResponse<String>, AiCallContext, String> responseMapper = (resp, ctx) -> {
            if (resp.statusCode() == 500) {
                throw new RuntimeException("HTTP 500");
            }
            return ctx.getModel();
        };

        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            executor.executeWithResilience(
                    context,
                    httpClient,
                    ctx -> requestBuilder,
                    HttpResponse.BodyHandlers.ofString(),
                    responseMapper).get();
        });

        assertTrue(exception.getCause().getMessage().contains("500"));
        assertEquals(2, context.getFallbackIndex());
    }

    // A simple stub HttpClient to avoid MockitoException on final class
    private static class StubHttpClient extends HttpClient {
        private final Queue<CompletableFuture<HttpResponse<?>>> responses = new LinkedList<>();
        private CompletableFuture<HttpResponse<?>> defaultResponse;

        public void addResponse(CompletableFuture<?> response) {
            responses.add((CompletableFuture<HttpResponse<?>>) response);
        }

        public void setDefaultResponse(CompletableFuture<?> response) {
            this.defaultResponse = (CompletableFuture<HttpResponse<?>>) response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            if (!responses.isEmpty()) {
                return (CompletableFuture<HttpResponse<T>>) (Object) responses.poll();
            }
            if (defaultResponse != null) {
                return (CompletableFuture<HttpResponse<T>>) (Object) defaultResponse;
            }
            throw new IllegalStateException("No more responses configured in StubHttpClient");
        }

        // Required overrides, unused
        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return null;
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
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return null;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            return null;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return null;
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return null;
        }
    }

    // A simple stub HttpResponse
    private static class StubHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;

        public StubHttpResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Collections.emptyMap(), (s1, s2) -> true);
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return null;
        }
    }
}
