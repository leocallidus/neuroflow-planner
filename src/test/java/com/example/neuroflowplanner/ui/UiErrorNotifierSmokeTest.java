package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.AppError;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.error.ErrorContext;
import com.example.neuroflowplanner.error.ErrorMapper;
import com.example.neuroflowplanner.service.task.TaskDependencyException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.Alert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UiErrorNotifier Smoke Tests")
class UiErrorNotifierSmokeTest {

    @Test
    @DisplayName("AI timeout error is shown as retryable UI notification")
    void showsAiTimeoutNotification() throws Exception {
        AtomicReference<CapturedAlert> captured = new AtomicReference<>();
        AppError appError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.set(new CapturedAlert(type, title, message))
        )) {
            appError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка AI",
                new TimeoutException("AI request timeout token=abc123"),
                ErrorCode.AI_REQUEST_FAILED,
                null,
                false,
                "operation", "chat"
            );
        }

        CapturedAlert alert = captured.get();
        assertNotNull(alert);
        assertEquals(ErrorCode.AI_TIMEOUT, appError.code());
        assertTrue(appError.retryable());
        assertEquals(Alert.AlertType.ERROR, alert.type());
        assertTrue(alert.message().contains("Код: AI_TIMEOUT"));
        assertTrue(alert.message().contains("ID: ERR-"));
        assertTrue(alert.message().contains("Повторите действие позже."));
        assertFalse(appError.technicalMessage().contains("abc123"));
    }

    @Test
    @DisplayName("IO failure is shown with IO code and without retry hint")
    void showsIoFailureNotification() throws Exception {
        AtomicReference<CapturedAlert> captured = new AtomicReference<>();
        AppError appError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.set(new CapturedAlert(type, title, message))
        )) {
            appError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка импорта",
                new IOException("cannot read file"),
                ErrorCode.IO_READ_FAILED,
                null,
                false,
                "operation", "import"
            );
        }

        CapturedAlert alert = captured.get();
        assertNotNull(alert);
        assertEquals(ErrorCode.IO_READ_FAILED, appError.code());
        assertFalse(appError.retryable());
        assertTrue(alert.message().contains("Код: IO_READ_FAILED"));
        assertFalse(alert.message().contains("Повторите действие позже."));
    }

    @Test
    @DisplayName("Export IO failure keeps export code in UI notification")
    void showsExportFailureNotification() throws Exception {
        AtomicReference<CapturedAlert> captured = new AtomicReference<>();
        AppError appError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.set(new CapturedAlert(type, title, message))
        )) {
            appError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка экспорта",
                new IOException("failed to write pdf"),
                ErrorCode.EXPORT_PDF_FAILED,
                null,
                false,
                "operation", "export"
            );
        }

        CapturedAlert alert = captured.get();
        assertNotNull(alert);
        assertEquals(ErrorCode.EXPORT_PDF_FAILED, appError.code());
        assertFalse(appError.retryable());
        assertTrue(alert.message().contains("Код: EXPORT_PDF_FAILED"));
    }

    @Test
    @DisplayName("Mapped error supports ErrorContext payload")
    void mapsErrorWithStructuredContext() throws Exception {
        AtomicReference<CapturedAlert> captured = new AtomicReference<>();
        AppError appError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.set(new CapturedAlert(type, title, message))
        )) {
            appError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка загрузки",
                new RuntimeException("boom"),
                ErrorCode.UNEXPECTED_ERROR,
                "Не удалось инициализировать экран.",
                false,
                ErrorContext.of("SmartNotesDialog", "bootstrap", "entryPoint", "SmartNotesDialog.inline")
            );
        }

        CapturedAlert alert = captured.get();
        assertNotNull(alert);
        assertEquals(ErrorCode.UNEXPECTED_ERROR, appError.code());
        assertEquals("SmartNotesDialog", appError.details().get("component"));
        assertEquals("bootstrap", appError.details().get("operation"));
        assertEquals("SmartNotesDialog.inline", appError.details().get("entryPoint"));
        assertEquals("Ошибка загрузки", alert.title());
    }

    @Test
    @DisplayName("Rate limit and provider failures show specific AI hints")
    void showsAiSpecificHints() throws Exception {
        List<CapturedAlert> captured = new ArrayList<>();
        AppError rateLimited;
        AppError providerError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.add(new CapturedAlert(type, title, message))
        )) {
            rateLimited = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка ИИ",
                new RuntimeException("Ошибка API: 429"),
                ErrorCode.AI_REQUEST_FAILED,
                "Не удалось выполнить запрос к ИИ.",
                false,
                "operation", "chat"
            );

            providerError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка ИИ",
                new RuntimeException("HTTP status 503"),
                ErrorCode.AI_REQUEST_FAILED,
                "Не удалось выполнить запрос к ИИ.",
                false,
                "operation", "chat"
            );
        }

        assertEquals(2, captured.size());
        assertEquals(ErrorCode.AI_PROVIDER_ERROR, providerError.code());
        assertEquals(ErrorCode.AI_RATE_LIMITED, rateLimited.code());
        assertTrue(captured.get(0).message().contains("Лимит запросов исчерпан"));
        assertTrue(captured.get(1).message().contains("Сбой на стороне AI-провайдера"));
    }

    @Test
    @DisplayName("Task dependency domain error is shown with task error code and no retry hint")
    void showsTaskDependencyErrorNotification() throws Exception {
        AtomicReference<CapturedAlert> captured = new AtomicReference<>();
        AppError appError;

        try (AutoCloseable ignored = UiErrorNotifier.withAlertPresenter(
            (owner, isDarkTheme, type, title, message) -> captured.set(new CapturedAlert(type, title, message))
        )) {
            appError = UiErrorNotifier.showMappedError(
                null,
                false,
                "Ошибка зависимостей",
                new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_CYCLE,
                    "cycle",
                    ErrorMapper.details("dependentTaskId", "task-A", "blockerTaskId", "task-B")
                ),
                ErrorCode.VALIDATION_FAILED,
                "Не удалось обновить зависимости.",
                false,
                "operation", "linkDependency"
            );
        }

        CapturedAlert alert = captured.get();
        assertNotNull(alert);
        assertEquals(ErrorCode.TASK_DEPENDENCY_CYCLE, appError.code());
        assertFalse(appError.retryable());
        assertTrue(alert.message().contains("Код: TASK_DEPENDENCY_CYCLE"));
        assertFalse(alert.message().contains("Повторите действие позже."));
    }

    private record CapturedAlert(Alert.AlertType type, String title, String message) {
    }
}
