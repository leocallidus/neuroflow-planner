package com.example.neuroflowplanner.sync;

import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Optional;
import java.util.Objects;

final class SyncTransport {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(SyncTransport.class);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final String baseUrlOverride;
    private final Duration requestTimeoutOverride;
    private final Integer maxAttemptsOverride;
    private final Duration retryBaseDelayOverride;
    private final Duration retryMaxDelayOverride;
    private final SyncCircuitBreaker circuitBreaker;

    SyncTransport() {
        this(null, null, null, null, null, null);
    }

    SyncTransport(
            String baseUrlOverride,
            Duration connectTimeoutOverride,
            Duration requestTimeoutOverride,
            Integer maxAttemptsOverride,
            Duration retryBaseDelayOverride,
            Duration retryMaxDelayOverride) {
        Duration connectTimeout = connectTimeoutOverride != null
                ? connectTimeoutOverride
                : ConfigManager.getCloudSyncConnectTimeout();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.mapper = AiObjectMapperFactory.createMapper(false);
        this.baseUrlOverride = baseUrlOverride;
        this.requestTimeoutOverride = requestTimeoutOverride;
        this.maxAttemptsOverride = maxAttemptsOverride;
        this.retryBaseDelayOverride = retryBaseDelayOverride;
        this.retryMaxDelayOverride = retryMaxDelayOverride;
        this.circuitBreaker = new SyncCircuitBreaker(
                ConfigManager.getCloudSyncCircuitBreakerFailureThreshold(),
                ConfigManager.getCloudSyncCircuitBreakerCooldown());
    }

    public <T> T postJson(String path, Object requestBody, String bearerToken, Class<T> responseType) {
        return send("POST", path, requestBody, bearerToken, responseType);
    }

    public void postNoContent(String path, Object requestBody, String bearerToken) {
        send("POST", path, requestBody, bearerToken, Void.class);
    }

    public <T> T getJson(String path, String bearerToken, Class<T> responseType) {
        return send("GET", path, null, bearerToken, responseType);
    }

    public boolean isReachable() {
        try {
            send("GET", "/health/live", null, null, Void.class);
            return true;
        } catch (RuntimeException e) {
            Throwable actual = AsyncContext.unwrap(e);
            if (actual instanceof SyncHttpException http && http.statusCode() >= 200 && http.statusCode() < 500) {
                return true;
            }
            return false;
        }
    }

    private <T> T send(String method, String path, Object requestBody, String bearerToken, Class<T> responseType) {
        int maxAttempts = maxAttemptsOverride != null
                ? maxAttemptsOverride
                : ConfigManager.getCloudSyncRetryMaxAttempts();
        Duration requestTimeout = requestTimeoutOverride != null
                ? requestTimeoutOverride
                : ConfigManager.getCloudSyncRequestTimeout();
        Duration retryBaseDelay = retryBaseDelayOverride != null
                ? retryBaseDelayOverride
                : ConfigManager.getCloudSyncRetryBaseDelay();
        Duration retryMaxDelay = retryMaxDelayOverride != null
                ? retryMaxDelayOverride
                : ConfigManager.getCloudSyncRetryMaxDelay();

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            circuitBreaker.beforeRequest();
            HttpRequest request = buildRequest(method, path, requestBody, bearerToken, requestTimeout);
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    circuitBreaker.recordSuccess();
                    if (responseType == Void.class) {
                        return null;
                    }
                    String body = response.body();
                    if (body == null || body.isBlank()) {
                        return null;
                    }
                    return mapper.readValue(body, responseType);
                }

                SyncHttpException httpException = toHttpException(
                        statusCode,
                        response.body(),
                        response.headers().firstValue("Retry-After"));
                if (attempt < maxAttempts && isRetryableStatus(statusCode)) {
                    lastFailure = httpException;
                    circuitBreaker.recordFailure();
                    logRetryableFailure(method, path, attempt, maxAttempts, statusCode, null);
                    sleepBeforeRetry(attempt, retryBaseDelay, retryMaxDelay, httpException.retryAfterSeconds());
                    continue;
                }
                resetCircuitOnNonRetryable(statusCode);
                throw httpException;
            } catch (SyncCircuitOpenException e) {
                LOG.warning(
                        "cloud.sync.transport.circuit.open",
                        "method", method,
                        "path", path,
                        "retryAfterMs", String.valueOf(e.retryAfterMillis()),
                        "requestId", AsyncContext.ensureRequestId());
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Cloud sync request interrupted", e);
            } catch (IOException e) {
                lastFailure = new RuntimeException("Cloud sync request failed", e);
                if (attempt < maxAttempts && isRetryableThrowable(e)) {
                    circuitBreaker.recordFailure();
                    logRetryableFailure(method, path, attempt, maxAttempts, 0, e);
                    sleepBeforeRetry(attempt, retryBaseDelay, retryMaxDelay, 0L);
                    continue;
                }
                if (!isRetryableThrowable(e)) {
                    circuitBreaker.recordSuccess();
                } else {
                    circuitBreaker.recordFailure();
                }
                throw lastFailure;
            }
        }

        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new RuntimeException("Cloud sync request failed without a captured cause");
    }

    private HttpRequest buildRequest(String method, String path, Object requestBody, String bearerToken, Duration requestTimeout) {
        URI uri = resolveUri(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("X-Request-ID", AsyncContext.ensureRequestId());
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header("Authorization", "Bearer " + bearerToken.trim());
        }
        if ("GET".equalsIgnoreCase(method)) {
            builder.GET();
            return builder.build();
        }
        try {
            String json = requestBody == null ? "" : mapper.writeValueAsString(requestBody);
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(json));
            return builder.build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize cloud sync request body", e);
        }
    }

    private URI resolveUri(String path) {
        String baseUrl = baseUrlOverride != null ? baseUrlOverride : ConfigManager.getCloudSyncBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("cloud.sync.baseUrl is not configured");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        try {
            return new URI(normalizedBase + normalizedPath);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid cloud sync base URL or path", e);
        }
    }

    private SyncHttpException toHttpException(int statusCode, String body, Optional<String> retryAfterHeader) {
        long retryAfterSeconds = parseRetryAfterSeconds(retryAfterHeader.orElse(""));
        if (body != null && !body.isBlank()) {
            try {
                SyncPayloads.ApiErrorEnvelope envelope = mapper.readValue(body, SyncPayloads.ApiErrorEnvelope.class);
                if (envelope != null && envelope.error() != null) {
                    String detailedMessage = extractErrorMessage(envelope.error());
                    return new SyncHttpException(
                            statusCode,
                            envelope.error().code(),
                            envelope.error().category(),
                            Boolean.TRUE.equals(envelope.error().retryable()) || isRetryableStatus(statusCode),
                            retryAfterSeconds,
                            detailedMessage,
                            envelope.error().request_id());
                }
            } catch (IOException ignored) {
                // Fall back to raw body below.
            }
        }
        return new SyncHttpException(
                statusCode,
                "http_error",
                "http",
                isRetryableStatus(statusCode),
                retryAfterSeconds,
                Objects.toString(body, ""),
                "");
    }

    private String extractErrorMessage(SyncPayloads.ApiErrorBody errorBody) {
        if (errorBody == null) {
            return "";
        }
        String errorCode = errorBody.code() == null ? "" : errorBody.code().trim();
        if ("validation_error".equalsIgnoreCase(errorCode)) {
            String detailed = formatValidationMessage(errorBody.details());
            if (!detailed.isBlank()) {
                return detailed;
            }
        }
        return errorBody.message() == null ? "" : errorBody.message().trim();
    }

    private String formatValidationMessage(JsonNode details) {
        if (details == null || details.isMissingNode() || details.isNull()) {
            return "";
        }
        JsonNode errors = details.get("errors");
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return "";
        }
        JsonNode first = errors.get(0);
        if (first == null || !first.isObject()) {
            return "";
        }
        String fieldLabel = resolveValidationFieldLabel(first.get("loc"));
        String type = textValue(first.get("type")).toLowerCase();
        JsonNode ctx = first.get("ctx");
        if (type.contains("string_too_short")) {
            int minLength = ctx != null && ctx.has("min_length") ? ctx.get("min_length").asInt(0) : 0;
            return minLength > 0
                    ? fieldLabel + " должен содержать минимум " + minLength + " символов."
                    : fieldLabel + " слишком короткий.";
        }
        if (type.contains("string_too_long")) {
            int maxLength = ctx != null && ctx.has("max_length") ? ctx.get("max_length").asInt(0) : 0;
            return maxLength > 0
                    ? fieldLabel + " должен содержать не больше " + maxLength + " символов."
                    : fieldLabel + " слишком длинный.";
        }
        if (type.contains("missing")) {
            return "Поле " + fieldLabel + " обязательно.";
        }
        if (type.contains("uuid")) {
            return fieldLabel + " содержит некорректный UUID.";
        }
        String message = textValue(first.get("msg"));
        if (!message.isBlank()) {
            return fieldLabel + ": " + message;
        }
        return "";
    }

    private String resolveValidationFieldLabel(JsonNode locNode) {
        if (locNode == null || !locNode.isArray() || locNode.isEmpty()) {
            return "Поле";
        }
        StringBuilder path = new StringBuilder();
        for (JsonNode part : locNode) {
            String value = textValue(part);
            if (value.isBlank() || "body".equalsIgnoreCase(value)) {
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(value);
        }
        String fieldPath = path.toString();
        return switch (fieldPath) {
            case "email" -> "Email";
            case "password" -> "Пароль";
            case "display_name" -> "Имя профиля";
            case "device" -> "Данные устройства";
            case "device.device_id" -> "Идентификатор устройства";
            case "device.device_label" -> "Имя устройства";
            case "device.platform" -> "Платформа";
            case "device.app_version" -> "Версия приложения";
            default -> fieldPath.isBlank() ? "Поле" : fieldPath;
        };
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText("") : node.toString();
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private boolean isRetryableThrowable(Throwable throwable) {
        return throwable instanceof ConnectException
                || throwable instanceof HttpTimeoutException
                || throwable instanceof IOException;
    }

    private void sleepBeforeRetry(int attempt, Duration baseDelay, Duration maxDelay, long retryAfterSeconds) {
        long baseMillis = Math.max(1L, baseDelay.toMillis());
        long maxMillis = Math.max(baseMillis, maxDelay.toMillis());
        long multiplier = 1L << Math.max(0, attempt - 1);
        long delayMillis = Math.min(maxMillis, baseMillis * multiplier);
        if (retryAfterSeconds > 0L) {
            delayMillis = Math.max(delayMillis, Math.min(maxMillis, retryAfterSeconds * 1000L));
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cloud sync retry delay interrupted", e);
        }
    }

    private void resetCircuitOnNonRetryable(int statusCode) {
        if (!isRetryableStatus(statusCode)) {
            circuitBreaker.recordSuccess();
        } else {
            circuitBreaker.recordFailure();
        }
    }

    private long parseRetryAfterSeconds(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(headerValue.trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void logRetryableFailure(String method, String path, int attempt, int maxAttempts, int statusCode, Throwable error) {
        LOG.warning(
                "cloud.sync.transport.retry.scheduled",
                "method", method,
                "path", path,
                "attempt", attempt,
                "maxAttempts", maxAttempts,
                "httpStatus", statusCode <= 0 ? "unknown" : String.valueOf(statusCode),
                "errorType", error == null ? "" : error.getClass().getSimpleName(),
                "requestId", AsyncContext.ensureRequestId());
    }
}
