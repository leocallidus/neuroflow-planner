package com.example.neuroflowplanner.error;

import com.example.neuroflowplanner.ai.json.AiParsingException;
import com.example.neuroflowplanner.db.DatabaseException;
import com.example.neuroflowplanner.service.task.TaskDependencyException;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ErrorMapper Tests")
class ErrorMapperTest {

    @Test
    @DisplayName("Maps timeout to AI_TIMEOUT and marks error retryable")
    void mapsTimeoutToAiTimeout() {
        AppError error = ErrorMapper.map(
            new CompletionException(new HttpTimeoutException("request token=abc123")),
            ErrorCode.AI_REQUEST_FAILED,
            null,
            false,
            "operation", "chat-completion",
            "token", "sk-live-secret-123456"
        );

        assertEquals(ErrorCode.AI_TIMEOUT, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.AI_TIMEOUT), error.userMessage());
        assertTrue(error.retryable());
        assertTrue(error.technicalMessage().contains("token=***"));
        assertFalse(error.technicalMessage().contains("abc123"));
        assertNotEquals("sk-live-secret-123456", error.details().get("token"));
    }

    @Test
    @DisplayName("Keeps export fallback code for IO exception")
    void keepsExportFallbackForIoException() {
        AppError error = ErrorMapper.map(
            new IOException("failed to write export file"),
            ErrorCode.EXPORT_PDF_FAILED,
            null,
            false,
            "operation", "export-pdf"
        );

        assertEquals(ErrorCode.EXPORT_PDF_FAILED, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.EXPORT_PDF_FAILED), error.userMessage());
        assertFalse(error.retryable());
    }

    @Test
    @DisplayName("Uses DB fallback code for DatabaseException")
    void usesDbFallbackForDatabaseException() {
        AppError error = ErrorMapper.map(
            new DatabaseException("query failed", new SQLException("connection refused")),
            ErrorCode.DB_CONNECTION_FAILED,
            null,
            false,
            Map.of("operation", "load-tasks")
        );

        assertEquals(ErrorCode.DB_CONNECTION_FAILED, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.DB_CONNECTION_FAILED), error.userMessage());
        assertTrue(error.retryable());
        assertTrue(error.technicalMessage().startsWith("DatabaseException"));
    }

    @Test
    @DisplayName("Maps IllegalArgumentException to VALIDATION_INVALID_VALUE")
    void mapsIllegalArgumentToValidationCode() {
        AppError error = ErrorMapper.map(
            new IllegalArgumentException("invalid duration value"),
            ErrorCode.UNEXPECTED_ERROR,
            null,
            false,
            "field", "duration"
        );

        assertEquals(ErrorCode.VALIDATION_INVALID_VALUE, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.VALIDATION_INVALID_VALUE), error.userMessage());
        assertFalse(error.retryable());
    }

    @Test
    @DisplayName("Falls back to unexpected error when cause is missing")
    void fallsBackToUnexpectedWhenCauseMissing() {
        AppError error = ErrorMapper.map(null, null, null, false, Map.of("requestId", "REQ-1"));

        assertEquals(ErrorCode.UNEXPECTED_ERROR, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.UNEXPECTED_ERROR), error.userMessage());
        assertEquals("no-cause", error.technicalMessage());
        assertEquals("REQ-1", error.details().get("requestId"));
    }

    @Test
    @DisplayName("Details helper redacts sensitive values and supports odd number of args")
    void detailsHelperRedactsAndSupportsOddArgs() {
        Map<String, String> details = ErrorMapper.details(
            "requestId", "REQ-42",
            "apiKey", "sk-very-secret-123456",
            "operation"
        );

        assertEquals("REQ-42", details.get("requestId"));
        assertNotEquals("sk-very-secret-123456", details.get("apiKey"));
        assertEquals("", details.get("operation"));
    }

    @Test
    @DisplayName("Supports structured ErrorContext with component and operation")
    void mapsWithStructuredContext() {
        ErrorContext context = ErrorContext.of(
            "MainView",
            "bootstrap",
            "entryPoint", "MainView"
        );

        AppError error = ErrorMapper.map(
            new RuntimeException("boom"),
            ErrorCode.UNEXPECTED_ERROR,
            "Не удалось открыть экран.",
            false,
            context
        );

        assertEquals(ErrorCode.UNEXPECTED_ERROR, error.code());
        assertEquals("MainView", error.details().get("component"));
        assertEquals("bootstrap", error.details().get("operation"));
        assertEquals("MainView", error.details().get("entryPoint"));
    }

    @Test
    @DisplayName("Maps HTTP 429 AI failures to AI_RATE_LIMITED")
    void mapsAiRateLimitFromHttpStatus() {
        AppError error = ErrorMapper.map(
            new IllegalStateException("Ошибка API: 429"),
            ErrorCode.AI_REQUEST_FAILED,
            "Не удалось получить ответ от ИИ.",
            false,
            Map.of("operation", "chat")
        );

        assertEquals(ErrorCode.AI_RATE_LIMITED, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.AI_RATE_LIMITED), error.userMessage());
        assertTrue(error.retryable());
    }

    @Test
    @DisplayName("Maps HTTP 5xx AI failures to AI_PROVIDER_ERROR")
    void mapsAiProviderFailureFromHttpStatus() {
        AppError error = ErrorMapper.map(
            new RuntimeException("HTTP status 503 while calling provider"),
            ErrorCode.AI_REQUEST_FAILED,
            null,
            false,
            "provider", "external_openai",
            "model", "openai/gpt-4o-mini"
        );

        assertEquals(ErrorCode.AI_PROVIDER_ERROR, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.AI_PROVIDER_ERROR), error.userMessage());
        assertTrue(error.retryable());
    }

    @Test
    @DisplayName("Maps exhausted retry metadata to AI_RETRY_EXHAUSTED")
    void mapsAiRetryExhausted() {
        AppError error = ErrorMapper.map(
            new RuntimeException("request failed after retries exhausted"),
            ErrorCode.AI_REQUEST_FAILED,
            null,
            false,
            "attempt", "3",
            "maxAttempts", "3",
            "provider", "local_ollama"
        );

        assertEquals(ErrorCode.AI_RETRY_EXHAUSTED, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.AI_RETRY_EXHAUSTED), error.userMessage());
        assertTrue(error.retryable());
    }

    @Test
    @DisplayName("Maps AI parsing failures to AI_RESPONSE_INVALID and marks as non-retryable")
    void mapsAiParsingFailureAsNonRetryable() {
        AppError error = ErrorMapper.map(
            new AiParsingException("schema mismatch for chat response"),
            ErrorCode.AI_REQUEST_FAILED,
            null,
            false,
            "operation", "chat"
        );

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.AI_RESPONSE_INVALID), error.userMessage());
        assertFalse(error.retryable());
    }

    @Test
    @DisplayName("Maps task dependency cycle domain error with default message and details")
    void mapsTaskDependencyCycle() {
        TaskDependencyException cause = new TaskDependencyException(
            ErrorCode.TASK_DEPENDENCY_CYCLE,
            "Cycle detected",
            Map.of("dependentTaskId", "task-A", "blockerTaskId", "task-B")
        );
        AppError error = ErrorMapper.map(
            cause,
            ErrorCode.VALIDATION_FAILED,
            null,
            false,
            "operation", "linkDependency"
        );

        assertEquals(ErrorCode.TASK_DEPENDENCY_CYCLE, error.code());
        assertEquals(ErrorMapper.defaultUserMessage(ErrorCode.TASK_DEPENDENCY_CYCLE), error.userMessage());
        assertFalse(error.retryable());
        assertEquals("task-A", error.details().get("dependentTaskId"));
        assertEquals("task-B", error.details().get("blockerTaskId"));
        assertEquals("linkDependency", error.details().get("operation"));
    }
}
