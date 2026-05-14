package com.example.neuroflowplanner.ai.resilience;

import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Function;

public class AiResilienceExecutor {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(AiResilienceExecutor.class);

    private final AiResiliencePolicy policy;
    private final ScheduledExecutorService scheduler;

    public AiResilienceExecutor(AiResiliencePolicy policy) {
        this.policy = policy;
        // Shared scheduler for delayed retries
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AiRetryScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    public <T, R> CompletableFuture<R> executeWithResilience(
            AiCallContext context,
            HttpClient httpClient,
            Function<AiCallContext, HttpRequest.Builder> requestBuilderFactory,
            HttpResponse.BodyHandler<T> bodyHandler,
            BiFunction<HttpResponse<T>, AiCallContext, R> responseProcessor) {

        long executionStartMs = System.currentTimeMillis();
        return executeAttempt(context, httpClient, requestBuilderFactory, bodyHandler, responseProcessor, executionStartMs);
    }

    private <T, R> CompletableFuture<R> executeAttempt(
            AiCallContext context,
            HttpClient httpClient,
            Function<AiCallContext, HttpRequest.Builder> requestBuilderFactory,
            HttpResponse.BodyHandler<T> bodyHandler,
            BiFunction<HttpResponse<T>, AiCallContext, R> responseProcessor,
            long executionStartMs) {

        CompletableFuture<R> future = new CompletableFuture<>();
        long remainingBudgetMs = remainingBudgetMs(executionStartMs);
        if (remainingBudgetMs <= 0L) {
            future.completeExceptionally(new TimeoutException("AI request budget exceeded before attempt start"));
            return future;
        }

        try {
            policy.getConcurrencyLimiter().acquire();
        } catch (InterruptedException | TimeoutException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            ErrorCode acquireCode = e instanceof TimeoutException ? ErrorCode.AI_UNAVAILABLE : ErrorCode.AI_REQUEST_FAILED;
            LOG.error("ai.request.bulkhead.acquire.failed", acquireCode, e,
                    "requestId", context.getRequestId(),
                    "httpStatus", "unknown",
                    "attempt", context.getAttempt(),
                    "maxAttempts", policy.getRetryPolicy().getMaxAttempts(),
                    "backoffMs", 0,
                    "model", context.getModel(),
                    "fallbackUsed", context.isFallbackUsed(),
                    "provider", context.getMode());
            future.completeExceptionally(e);
            return future;
        }

        Duration effectiveAttemptTimeout = capPerAttemptTimeout(remainingBudgetMs);
        HttpRequest request = requestBuilderFactory.apply(context)
                .timeout(effectiveAttemptTimeout)
                .build();

        long startTime = System.currentTimeMillis();

        httpClient.sendAsync(request, bodyHandler).whenComplete((response, throwable) -> {
            policy.getConcurrencyLimiter().release();
            long duration = System.currentTimeMillis() - startTime;

            if (throwable != null) {
                AiHttpErrorClassifier.ErrorCategory exceptionCategory = AiHttpErrorClassifier.classifyException(throwable);
                if (exceptionCategory == AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST) {
                    ErrorCode failureCode = classifyFailureCode(throwable, null, false);
                    logAttemptFailure(
                            context,
                            throwable,
                            failureCode,
                            context.getAttempt(),
                            policy.getRetryPolicy().getMaxAttempts(),
                            null,
                            0L,
                            duration);
                    future.completeExceptionally(throwable);
                } else {
                    handleFailure(context, httpClient, requestBuilderFactory, bodyHandler, responseProcessor,
                            throwable, null, duration, future, executionStartMs);
                }
            } else {
                AiHttpErrorClassifier.ErrorCategory category = AiHttpErrorClassifier
                        .classifyHttpStatus(response.statusCode());
                if (category == AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE) {
                    handleFailure(context, httpClient, requestBuilderFactory, bodyHandler, responseProcessor,
                            null, response, duration, future, executionStartMs);
                } else if (category == AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST) {
                    // Fail fast without retry
                    try {
                        R result = responseProcessor.apply(response, context);
                        future.complete(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    // Success or Unknown
                    try {
                        R result = responseProcessor.apply(response, context);
                        future.complete(result);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                }
            }
        });

        return future;
    }

    private <T, R> void handleFailure(
            AiCallContext context,
            HttpClient httpClient,
            Function<AiCallContext, HttpRequest.Builder> requestBuilderFactory,
            HttpResponse.BodyHandler<T> bodyHandler,
            BiFunction<HttpResponse<T>, AiCallContext, R> responseProcessor,
            Throwable throwable,
            HttpResponse<T> failedResponse,
            long duration,
            CompletableFuture<R> mainFuture,
            long executionStartMs) {

        int attempt = context.getAttempt();
        int maxAttempts = policy.getRetryPolicy().getMaxAttempts();
        Integer httpStatus = failedResponse != null ? failedResponse.statusCode() : null;
        ErrorCode failureCode = classifyFailureCode(throwable, httpStatus, false);
        long backoffMs = 0L;
        if (attempt < maxAttempts) {
            backoffMs = calculateDelay(attempt + 1, failedResponse).toMillis();
        }

        logAttemptFailure(
                context,
                throwable,
                failureCode,
                attempt,
                maxAttempts,
                httpStatus,
                backoffMs,
                duration);

        if (attempt >= maxAttempts) {
            // Check for model fallback
            List<String> fallbacks = policy.getFallbackModels();
            int nextFallbackIndex = context.getFallbackIndex();
            long remainingBudgetMs = remainingBudgetMs(executionStartMs);

            if (remainingBudgetMs > 0L && fallbacks != null && nextFallbackIndex < fallbacks.size()) {
                String fallbackModel = fallbacks.get(nextFallbackIndex);
                String failedModel = context.getModel();
                LOG.warning("ai.request.model_fallback", ErrorCode.AI_RETRY_EXHAUSTED,
                        "requestId", context.getRequestId(),
                        "httpStatus", httpStatus == null ? "unknown" : String.valueOf(httpStatus),
                        "attempt", attempt,
                        "maxAttempts", maxAttempts,
                        "backoffMs", 0,
                        "model", failedModel,
                        "fromModel", failedModel,
                        "toModel", fallbackModel,
                        "fallbackUsed", true,
                        "provider", context.getMode(),
                        "reason", "retry_exhausted");

                context.fallbackTo(fallbackModel, nextFallbackIndex + 1);

                scheduler.execute(() -> {
                    executeAttempt(context, httpClient, requestBuilderFactory, bodyHandler, responseProcessor,
                            executionStartMs)
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    mainFuture.completeExceptionally(ex);
                                } else {
                                    mainFuture.complete(result);
                                }
                            });
                });
                return;
            }

            if (remainingBudgetMs <= 0L) {
                mainFuture.completeExceptionally(new TimeoutException("AI request budget exhausted"));
                return;
            }

            ErrorCode exhaustedCode = classifyFailureCode(throwable, httpStatus, true);
            LOG.error("ai.request.retry.exhausted", exhaustedCode, throwable,
                    "requestId", context.getRequestId(),
                    "httpStatus", httpStatus == null ? "unknown" : String.valueOf(httpStatus),
                    "attempt", attempt,
                    "maxAttempts", maxAttempts,
                    "backoffMs", 0,
                    "model", context.getModel(),
                    "fallbackUsed", context.isFallbackUsed(),
                    "provider", context.getMode(),
                    "retryExhausted", true);
            if (failedResponse != null) {
                try {
                    R result = responseProcessor.apply(failedResponse, context);
                    mainFuture.complete(result);
                } catch (Exception e) {
                    mainFuture.completeExceptionally(e);
                }
            } else if (throwable != null) {
                mainFuture.completeExceptionally(throwable);
            } else {
                mainFuture.completeExceptionally(new RuntimeException("Unknown AI request error"));
            }
            return;
        }

        context.incrementAttempt();
        long remainingBudgetMs = remainingBudgetMs(executionStartMs);
        if (remainingBudgetMs <= 0L) {
            mainFuture.completeExceptionally(new TimeoutException("AI request budget exhausted"));
            return;
        }
        long effectiveDelayMs = Math.min(backoffMs, Math.max(0L, remainingBudgetMs - 1L));
        Duration delay = Duration.ofMillis(effectiveDelayMs);

        LOG.info("ai.request.retry.scheduled", failureCode,
                "requestId", context.getRequestId(),
                "httpStatus", httpStatus == null ? "unknown" : String.valueOf(httpStatus),
                "attempt", attempt,
                "nextAttempt", context.getAttempt(),
                "maxAttempts", maxAttempts,
                "backoffMs", delay.toMillis(),
                "remainingBudgetMs", remainingBudgetMs,
                "model", context.getModel(),
                "fallbackUsed", context.isFallbackUsed(),
                "provider", context.getMode());

        scheduler.schedule(() -> {
            executeAttempt(context, httpClient, requestBuilderFactory, bodyHandler, responseProcessor, executionStartMs)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            mainFuture.completeExceptionally(ex);
                        } else {
                            mainFuture.complete(result);
                        }
                    });
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private <T> Duration calculateDelay(int attempt, HttpResponse<T> response) {
        if (response != null && response.statusCode() == 429) {
            Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After");
            if (retryAfterHeader.isPresent()) {
                Duration parsed = RetryAfterParser.parse(retryAfterHeader.get());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return policy.getRetryPolicy().getDelayStrategy().calculateDelay(attempt);
    }

    private long remainingBudgetMs(long executionStartMs) {
        Duration budget = policy.getRequestBudget();
        if (budget == null || budget.isZero() || budget.isNegative() || budget.toMillis() >= Long.MAX_VALUE / 4) {
            return Long.MAX_VALUE;
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - executionStartMs);
        return Math.max(0L, budget.toMillis() - elapsed);
    }

    private Duration capPerAttemptTimeout(long remainingBudgetMs) {
        Duration readTimeout = policy.getReadTimeout();
        long readMs = readTimeout == null ? 0L : readTimeout.toMillis();
        long base = readMs <= 0L ? remainingBudgetMs : Math.min(readMs, remainingBudgetMs);
        return Duration.ofMillis(Math.max(1L, base));
    }

    private void logAttemptFailure(
            AiCallContext context,
            Throwable throwable,
            ErrorCode failureCode,
            int attempt,
            int maxAttempts,
            Integer httpStatus,
            long backoffMs,
            long durationMs) {
        String status = httpStatus == null ? "unknown" : String.valueOf(httpStatus);
        if (throwable != null) {
            LOG.error("ai.request.failed", failureCode, throwable,
                    "requestId", context.getRequestId(),
                    "httpStatus", status,
                    "attempt", attempt,
                    "maxAttempts", maxAttempts,
                    "backoffMs", backoffMs,
                    "model", context.getModel(),
                    "fallbackUsed", context.isFallbackUsed(),
                    "provider", context.getMode(),
                    "durationMs", durationMs);
            return;
        }
        LOG.warning("ai.request.failed", failureCode,
                "requestId", context.getRequestId(),
                "httpStatus", status,
                "attempt", attempt,
                "maxAttempts", maxAttempts,
                "backoffMs", backoffMs,
                "model", context.getModel(),
                "fallbackUsed", context.isFallbackUsed(),
                "provider", context.getMode(),
                "durationMs", durationMs);
    }

    private ErrorCode classifyFailureCode(Throwable throwable, Integer httpStatus, boolean retryExhausted) {
        if (retryExhausted) {
            return ErrorCode.AI_RETRY_EXHAUSTED;
        }
        Throwable root = unwrap(throwable);
        if (root instanceof AiParsingException) {
            return ErrorCode.AI_RESPONSE_INVALID;
        }
        if (root instanceof TimeoutException || root instanceof HttpTimeoutException) {
            return ErrorCode.AI_TIMEOUT;
        }
        if (httpStatus != null) {
            if (httpStatus == 429) {
                return ErrorCode.AI_RATE_LIMITED;
            }
            if (httpStatus == 408 || httpStatus == 425) {
                return ErrorCode.AI_TIMEOUT;
            }
            if (httpStatus >= 500 && httpStatus < 600) {
                return ErrorCode.AI_PROVIDER_ERROR;
            }
            if (httpStatus >= 400 && httpStatus < 500) {
                return ErrorCode.AI_REQUEST_FAILED;
            }
        }
        return ErrorCode.AI_REQUEST_FAILED;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            if (current instanceof java.util.concurrent.CompletionException
                    || current instanceof java.util.concurrent.ExecutionException) {
                current = current.getCause();
                continue;
            }
            break;
        }
        return current;
    }
}
