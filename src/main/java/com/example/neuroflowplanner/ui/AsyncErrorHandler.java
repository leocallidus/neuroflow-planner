package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.AppError;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.error.ErrorMapper;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.Window;

public final class AsyncErrorHandler {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(AsyncErrorHandler.class);
    private static final AtomicBoolean GLOBAL_HANDLERS_INSTALLED = new AtomicBoolean(false);
    private static final ThreadLocal<Boolean> NOTIFICATION_GUARD = ThreadLocal.withInitial(() -> false);
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b([1-5]\\d{2})\\b");

    private AsyncErrorHandler() {
    }

    public static void installGlobalExceptionHandlers() {
        if (!GLOBAL_HANDLERS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Thread.UncaughtExceptionHandler handler = AsyncErrorHandler::handleUncaughtException;
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    public static <T> CompletableFuture<T> observeFuture(
        CompletableFuture<T> future,
        Window owner,
        boolean isDarkTheme,
        String title,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        String event,
        Object... details
    ) {
        if (future == null) {
            return null;
        }
        return future.whenComplete(AsyncContext.withMdcBiConsumer((result, throwable) -> {
            if (throwable != null) {
                reportAsyncException(
                    throwable,
                    owner,
                    isDarkTheme,
                    title,
                    fallbackCode,
                    fallbackUserMessage,
                    retryable,
                    event,
                    details
                );
            }
        }));
    }

    public static <T> void bindTaskFailureHandler(
        Task<T> task,
        Window owner,
        boolean isDarkTheme,
        String title,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        String event,
        Object... details
    ) {
        if (task == null) {
            return;
        }
        task.setOnFailed(evt -> reportAsyncException(
            task.getException(),
            owner,
            isDarkTheme,
            title,
            fallbackCode,
            fallbackUserMessage,
            retryable,
            event,
            details
        ));
    }

    public static void reportAsyncException(
        Throwable throwable,
        Window owner,
        boolean isDarkTheme,
        String title,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        String event,
        Object... details
    ) {
        Throwable cause = AsyncContext.unwrap(throwable);
        if (cause == null || cause instanceof CancellationException) {
            return;
        }

        String requestId = AsyncContext.ensureRequestId();
        Object[] mergedDetails = appendDetails(details, "requestId", requestId);
        String resolvedEvent = (event == null || event.isBlank()) ? "async.operation.failed" : event;
        ErrorCode resolvedCode = fallbackCode == null ? ErrorCode.UNEXPECTED_ERROR : fallbackCode;
        Map<String, String> detailMap = buildDetailMap(cause, resolvedCode, mergedDetails);
        AppError appError = ErrorMapper.map(cause, resolvedCode, fallbackUserMessage, retryable, detailMap);

        LOG.error(resolvedEvent, appError.code(), cause, toLogDetails(appError));
        notifyUser(owner, isDarkTheme, title, appError, cause);
    }

    private static void handleUncaughtException(Thread thread, Throwable throwable) {
        String threadName = thread == null ? "unknown" : thread.getName();
        reportAsyncException(
            throwable,
            null,
            ConfigManager.isDarkTheme(),
            "Непредвиденная ошибка",
            ErrorCode.UNEXPECTED_ERROR,
            "Произошла непредвиденная ошибка. Попробуйте повторить действие.",
            false,
            "app.uncaught.exception",
            "thread", threadName
        );
    }

    private static void notifyUser(
        Window owner,
        boolean isDarkTheme,
        String title,
        AppError error,
        Throwable cause
    ) {
        Runnable action = () -> {
            if (Boolean.TRUE.equals(NOTIFICATION_GUARD.get())) {
                return;
            }
            NOTIFICATION_GUARD.set(true);
            try {
                UiErrorNotifier.showError(
                    owner,
                    isDarkTheme,
                    title,
                    error,
                    cause
                );
            } catch (Throwable notificationError) {
                LOG.error(
                    "async.ui.notification.failed",
                    ErrorCode.UNEXPECTED_ERROR,
                    notificationError,
                    "operation", "UiErrorNotifier.showError"
                );
            } finally {
                NOTIFICATION_GUARD.set(false);
            }
        };
        runOnFxThread(action);
    }

    private static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException ex) {
            LOG.error(
                "async.ui.dispatch.skipped",
                ErrorCode.UNEXPECTED_ERROR,
                ex,
                "reason", "javafx.runtime.not.initialized"
            );
        }
    }

    private static Object[] appendDetails(Object[] details, Object... extraPairs) {
        int detailsLength = details == null ? 0 : details.length;
        int extraLength = extraPairs == null ? 0 : extraPairs.length;
        Object[] merged = new Object[detailsLength + extraLength];
        if (detailsLength > 0) {
            System.arraycopy(details, 0, merged, 0, detailsLength);
        }
        if (extraLength > 0) {
            System.arraycopy(extraPairs, 0, merged, detailsLength, extraLength);
        }
        return merged;
    }

    private static Map<String, String> buildDetailMap(Throwable cause, ErrorCode fallbackCode, Object[] details) {
        Map<String, String> values = new LinkedHashMap<>(ErrorMapper.details(details));
        enrichAiObservabilityFields(values, cause, fallbackCode);
        return Map.copyOf(values);
    }

    private static void enrichAiObservabilityFields(
        Map<String, String> details,
        Throwable cause,
        ErrorCode fallbackCode
    ) {
        if (!isAiContext(fallbackCode, details)) {
            return;
        }
        String httpStatus = firstNonBlank(
            details.get("httpStatus"),
            details.get("statusCode"),
            details.get("status")
        );
        if (!hasText(httpStatus)) {
            httpStatus = extractHttpStatusFromThrowable(cause);
        }
        details.put("httpStatus", hasText(httpStatus) ? httpStatus : "unknown");
        details.putIfAbsent("attempt", "n/a");
        details.putIfAbsent("maxAttempts", "n/a");
        details.putIfAbsent("backoffMs", "n/a");
        details.putIfAbsent("model", "unknown");
        details.putIfAbsent("fallbackUsed", "false");
        details.putIfAbsent("provider", resolveProvider(details, fallbackCode));
    }

    private static Object[] toLogDetails(AppError error) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("retryable", String.valueOf(error.retryable()));
        values.put("technicalMessage", error.technicalMessage());
        values.putAll(error.details());
        Object[] pairs = new Object[values.size() * 2];
        int i = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            pairs[i++] = entry.getKey();
            pairs[i++] = entry.getValue();
        }
        return pairs;
    }

    private static boolean isAiContext(ErrorCode fallbackCode, Map<String, String> details) {
        if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.AI) {
            return true;
        }
        if (details == null || details.isEmpty()) {
            return false;
        }
        return hasText(details.get("model"))
            || hasText(details.get("provider"))
            || hasText(details.get("httpStatus"))
            || hasText(details.get("statusCode"))
            || hasText(details.get("fallbackUsed"))
            || hasText(details.get("maxAttempts"));
    }

    private static String resolveProvider(Map<String, String> details, ErrorCode fallbackCode) {
        String current = details.get("provider");
        if (hasText(current)) {
            return current;
        }
        String mode = firstNonBlank(details.get("mode"), details.get("aiMode"));
        if (hasText(mode)) {
            return mode.toLowerCase(Locale.ROOT);
        }
        String model = details.get("model");
        if (hasText(model) && model.contains("/")) {
            return model.substring(0, model.indexOf('/')).toLowerCase(Locale.ROOT);
        }
        if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.AI) {
            return "ai";
        }
        return "unknown";
    }

    private static String extractHttpStatusFromThrowable(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            String message = current.getMessage();
            if (hasText(message)) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("http")
                    || lower.contains("status")
                    || lower.contains("api")
                    || lower.contains("код")
                    || lower.contains("ошибка")) {
                    Matcher matcher = HTTP_STATUS_PATTERN.matcher(message);
                    while (matcher.find()) {
                        String candidate = matcher.group(1);
                        if (hasText(candidate)) {
                            return candidate;
                        }
                    }
                }
            }
            Throwable nested = current.getCause();
            if (nested == null || nested == current) {
                break;
            }
            current = nested;
        }
        return null;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        for (String candidate : candidates) {
            if (hasText(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
