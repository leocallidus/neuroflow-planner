package com.example.neuroflowplanner.error;

import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.db.DatabaseException;
import com.example.neuroflowplanner.service.task.TaskDependencyException;
import com.example.neuroflowplanner.util.SensitiveDataRedactor;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps exceptions to canonical application errors for UI notifications.
 */
public final class ErrorMapper {
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\b([1-5]\\d{2})\\b");

    private ErrorMapper() {
    }

    public static AppError map(
        Throwable throwable,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        Map<String, String> details
    ) {
        Throwable cause = unwrap(throwable);
        Map<String, String> safeDetails = mergeDetails(details, dependencyDetails(cause));
        Integer httpStatus = extractHttpStatus(cause, safeDetails);
        ErrorCode code = resolveCode(cause, fallbackCode, safeDetails, httpStatus);
        String userMessage = resolveUserMessage(code, fallbackUserMessage);
        boolean shouldRetry = retryable || isRetryable(cause, code, safeDetails, httpStatus);
        String technicalMessage = buildTechnicalMessage(cause);
        return new AppError(code, userMessage, technicalMessage, shouldRetry, safeDetails);
    }

    public static AppError map(
        Throwable throwable,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        Object... details
    ) {
        return map(throwable, fallbackCode, fallbackUserMessage, retryable, details(details));
    }

    public static AppError map(
        Throwable throwable,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        ErrorContext context
    ) {
        Map<String, String> details = context == null ? Map.of() : context.details();
        return map(throwable, fallbackCode, fallbackUserMessage, retryable, details);
    }

    public static Map<String, String> details(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object rawKey = keyValues[i];
            String key = rawKey == null ? "detail" : String.valueOf(rawKey).trim();
            if (key.isEmpty()) {
                key = "detail";
            }
            Object rawValue = (i + 1) < keyValues.length ? keyValues[i + 1] : "";
            String value = rawValue == null ? "" : String.valueOf(rawValue);
            values.put(key, SensitiveDataRedactor.redactFieldValue(key, value));
        }
        return Map.copyOf(values);
    }

    public static String defaultUserMessage(ErrorCode code) {
        ErrorCode resolved = code == null ? ErrorCode.UNEXPECTED_ERROR : code;
        return switch (resolved) {
            case DB_CONNECTION_FAILED -> "Не удалось подключиться к базе данных.";
            case DB_QUERY_FAILED, DB_MIGRATION_FAILED, DB_CONSTRAINT_VIOLATION -> "Не удалось выполнить операцию с данными.";
            case AI_TIMEOUT -> "Сервис ИИ не ответил вовремя.";
            case AI_UNAVAILABLE -> "Сервис ИИ сейчас недоступен.";
            case AI_RATE_LIMITED -> "Превышен лимит запросов к сервису ИИ. Подождите немного и повторите.";
            case AI_PROVIDER_ERROR -> "Сервис ИИ временно недоступен на стороне провайдера.";
            case AI_RETRY_EXHAUSTED -> "Сервис ИИ не восстановился после повторов. Попробуйте позже.";
            case AI_REQUEST_FAILED, AI_RESPONSE_INVALID -> "Ошибка обращения к сервису ИИ.";
            case IO_READ_FAILED -> "Не удалось прочитать данные.";
            case IO_WRITE_FAILED -> "Не удалось сохранить данные.";
            case IO_DELETE_FAILED -> "Не удалось удалить данные.";
            case EXPORT_EXCEL_FAILED -> "Не удалось экспортировать файл Excel.";
            case EXPORT_PDF_FAILED -> "Не удалось экспортировать PDF.";
            case EXPORT_CSV_FAILED -> "Не удалось экспортировать CSV.";
            case EXPORT_DOCX_FAILED -> "Не удалось экспортировать Word документ.";
            case EXPORT_MARKDOWN_FAILED -> "Не удалось экспортировать Markdown.";
            case EXPORT_JSON_FAILED -> "Не удалось экспортировать JSON.";
            case VALIDATION_FAILED, VALIDATION_REQUIRED_FIELD, VALIDATION_INVALID_VALUE -> "Проверьте корректность введенных данных.";
            case TASK_DEPENDENCY_CYCLE -> "Нельзя сохранить зависимость: обнаружен цикл в графе задач.";
            case TASK_DEPENDENCY_INVALID_REFERENCE -> "Нельзя сохранить зависимость: указана несуществующая задача.";
            case UNEXPECTED_ERROR -> "Произошла непредвиденная ошибка.";
        };
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable nested = current.getCause();
            if (nested == null || nested == current) {
                break;
            }
            current = nested;
        }
        return current;
    }

    private static ErrorCode resolveCode(
        Throwable cause,
        ErrorCode fallbackCode,
        Map<String, String> details,
        Integer httpStatus
    ) {
        if (cause == null) {
            return fallbackOrUnexpected(fallbackCode);
        }
        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
            return ErrorCode.AI_TIMEOUT;
        }
        if (cause instanceof AiParsingException) {
            return ErrorCode.AI_RESPONSE_INVALID;
        }
        if (cause instanceof TaskDependencyException taskDependencyException) {
            return taskDependencyException.errorCode();
        }
        if (isAiContext(fallbackCode, details, httpStatus)) {
            ErrorCode aiCode = resolveAiCode(cause, fallbackCode, details, httpStatus);
            if (aiCode != null) {
                return aiCode;
            }
        }
        if (cause instanceof DatabaseException || cause instanceof SQLException) {
            if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.DB) {
                return fallbackCode;
            }
            return ErrorCode.DB_QUERY_FAILED;
        }
        if (cause instanceof IOException) {
            if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.EXPORT) {
                return fallbackCode;
            }
            if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.IO) {
                return fallbackCode;
            }
            return ErrorCode.IO_READ_FAILED;
        }
        if (cause instanceof IllegalArgumentException) {
            return ErrorCode.VALIDATION_INVALID_VALUE;
        }
        return fallbackOrUnexpected(fallbackCode);
    }

    private static ErrorCode fallbackOrUnexpected(ErrorCode fallbackCode) {
        return fallbackCode == null ? ErrorCode.UNEXPECTED_ERROR : fallbackCode;
    }

    private static boolean isRetryable(
        Throwable cause,
        ErrorCode code,
        Map<String, String> details,
        Integer httpStatus
    ) {
        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException) {
            return true;
        }
        if (code == ErrorCode.AI_REQUEST_FAILED && httpStatus != null) {
            return isRetryableAiStatus(httpStatus);
        }
        if (code == ErrorCode.AI_REQUEST_FAILED && isRetryExhausted(cause, details)) {
            return true;
        }
        if (code == null) {
            return false;
        }
        return switch (code) {
            case DB_CONNECTION_FAILED, AI_TIMEOUT, AI_REQUEST_FAILED, AI_UNAVAILABLE,
                 AI_RATE_LIMITED, AI_PROVIDER_ERROR, AI_RETRY_EXHAUSTED -> true;
            case DB_QUERY_FAILED, DB_MIGRATION_FAILED, DB_CONSTRAINT_VIOLATION -> false;
            case AI_RESPONSE_INVALID -> false;
            case IO_READ_FAILED, IO_WRITE_FAILED, IO_DELETE_FAILED -> false;
            case EXPORT_EXCEL_FAILED, EXPORT_PDF_FAILED, EXPORT_CSV_FAILED, EXPORT_DOCX_FAILED, EXPORT_MARKDOWN_FAILED, EXPORT_JSON_FAILED -> false;
            case VALIDATION_FAILED, VALIDATION_REQUIRED_FIELD, VALIDATION_INVALID_VALUE -> false;
            case TASK_DEPENDENCY_CYCLE, TASK_DEPENDENCY_INVALID_REFERENCE -> false;
            case UNEXPECTED_ERROR -> false;
        };
    }

    private static Map<String, String> dependencyDetails(Throwable cause) {
        if (cause instanceof TaskDependencyException taskDependencyException) {
            return taskDependencyException.details();
        }
        return Map.of();
    }

    private static Map<String, String> mergeDetails(Map<String, String> base, Map<String, String> additional) {
        Map<String, String> safeBase = base == null ? Map.of() : base;
        Map<String, String> safeAdditional = additional == null ? Map.of() : additional;
        if (safeBase.isEmpty() && safeAdditional.isEmpty()) {
            return Map.of();
        }
        Map<String, String> merged = new LinkedHashMap<>(safeBase);
        merged.putAll(safeAdditional);
        return Map.copyOf(merged);
    }

    private static String buildTechnicalMessage(Throwable cause) {
        if (cause == null) {
            return "no-cause";
        }
        String type = cause.getClass().getSimpleName();
        String message = cause.getMessage();
        if (!hasText(message)) {
            return type;
        }
        return type + ": " + SensitiveDataRedactor.redactText(message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String resolveUserMessage(ErrorCode code, String fallbackUserMessage) {
        if (!hasText(fallbackUserMessage)) {
            return defaultUserMessage(code);
        }
        if (code == ErrorCode.AI_RATE_LIMITED
            || code == ErrorCode.AI_PROVIDER_ERROR
            || code == ErrorCode.AI_RETRY_EXHAUSTED
            || code == ErrorCode.TASK_DEPENDENCY_CYCLE
            || code == ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE) {
            return defaultUserMessage(code);
        }
        return fallbackUserMessage;
    }

    private static boolean isAiContext(ErrorCode fallbackCode, Map<String, String> details, Integer httpStatus) {
        if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.AI) {
            return true;
        }
        if (httpStatus != null) {
            return true;
        }
        if (details == null || details.isEmpty()) {
            return false;
        }
        return hasText(details.get("provider"))
            || hasText(details.get("model"))
            || hasText(details.get("fallbackUsed"))
            || hasText(details.get("maxAttempts"))
            || hasText(details.get("attempt"))
            || hasText(details.get("backoffMs"));
    }

    private static ErrorCode resolveAiCode(
        Throwable cause,
        ErrorCode fallbackCode,
        Map<String, String> details,
        Integer httpStatus
    ) {
        if (isRetryExhausted(cause, details)) {
            return ErrorCode.AI_RETRY_EXHAUSTED;
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
        if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
            return ErrorCode.AI_TIMEOUT;
        }
        if (cause instanceof AiParsingException) {
            return ErrorCode.AI_RESPONSE_INVALID;
        }
        if (cause instanceof IOException) {
            return ErrorCode.AI_PROVIDER_ERROR;
        }
        String message = safeLowerMessage(cause);
        if (message.contains("rate limit") || message.contains("too many requests")) {
            return ErrorCode.AI_RATE_LIMITED;
        }
        if (fallbackCode != null && fallbackCode.family() == ErrorCode.Family.AI) {
            return fallbackCode;
        }
        return null;
    }

    private static boolean isRetryExhausted(Throwable cause, Map<String, String> details) {
        if (details != null && !details.isEmpty()) {
            if (parseBoolean(details.get("retryExhausted")) || parseBoolean(details.get("retriesExhausted"))) {
                return true;
            }
            Integer attempt = parseInteger(details.get("attempt"));
            Integer maxAttempts = parseInteger(details.get("maxAttempts"));
            if (attempt != null && maxAttempts != null && maxAttempts > 0 && attempt >= maxAttempts) {
                return true;
            }
        }
        String message = safeLowerMessage(cause);
        return message.contains("retry exhausted")
            || message.contains("retries exhausted")
            || message.contains("attempts exhausted")
            || message.contains("max attempts reached")
            || message.contains("исчерпан");
    }

    private static boolean isRetryableAiStatus(int httpStatus) {
        return httpStatus == 429
            || httpStatus == 408
            || httpStatus == 425
            || (httpStatus >= 500 && httpStatus < 600);
    }

    private static Integer extractHttpStatus(Throwable cause, Map<String, String> details) {
        Integer fromDetails = extractHttpStatusFromDetails(details);
        if (fromDetails != null) {
            return fromDetails;
        }
        if (cause == null) {
            return null;
        }
        Throwable current = cause;
        while (current != null) {
            Integer parsed = extractHttpStatusFromText(current.getMessage());
            if (parsed != null) {
                return parsed;
            }
            Throwable nested = current.getCause();
            if (nested == null || nested == current) {
                break;
            }
            current = nested;
        }
        return null;
    }

    private static Integer extractHttpStatusFromDetails(Map<String, String> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        Integer status = parseInteger(details.get("httpStatus"));
        if (status != null) {
            return status;
        }
        status = parseInteger(details.get("statusCode"));
        if (status != null) {
            return status;
        }
        status = parseInteger(details.get("status"));
        if (status != null) {
            return status;
        }
        return null;
    }

    private static Integer extractHttpStatusFromText(String text) {
        if (!hasText(text)) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasStatusHint = lower.contains("http")
            || lower.contains("status")
            || lower.contains("api")
            || lower.contains("код")
            || lower.contains("ошибка");
        if (!hasStatusHint) {
            return null;
        }
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(text);
        while (matcher.find()) {
            Integer status = parseInteger(matcher.group(1));
            if (status != null && status >= 100 && status <= 599) {
                return status;
            }
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true")
            || normalized.equals("1")
            || normalized.equals("yes")
            || normalized.equals("y")
            || normalized.equals("да");
    }

    private static String safeLowerMessage(Throwable cause) {
        if (cause == null || !hasText(cause.getMessage())) {
            return "";
        }
        return cause.getMessage().toLowerCase(Locale.ROOT);
    }
}
