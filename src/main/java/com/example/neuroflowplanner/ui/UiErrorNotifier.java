package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.error.AppError;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.error.ErrorContext;
import com.example.neuroflowplanner.error.ErrorMapper;
import com.example.neuroflowplanner.util.StructuredLogger;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Unified UI notifier for info/warning/error notifications.
 */
public final class UiErrorNotifier {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(UiErrorNotifier.class);
    private static final String APP_STYLESHEET = "/styles/app.css";
    private static final String DARK_STYLESHEET = "/styles/dark-theme.css";
    private static volatile AlertPresenter alertPresenter = UiErrorNotifier::showAlertInternal;

    private UiErrorNotifier() {
    }

    public static AppError showMappedError(
        Window owner,
        boolean isDarkTheme,
        String title,
        Throwable throwable,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        Object... details
    ) {
        AppError appError = ErrorMapper.map(throwable, fallbackCode, fallbackUserMessage, retryable, details);
        showError(owner, isDarkTheme, title, appError, throwable);
        return appError;
    }

    public static AppError showMappedError(
        Window owner,
        boolean isDarkTheme,
        String title,
        Throwable throwable,
        ErrorCode fallbackCode,
        String fallbackUserMessage,
        boolean retryable,
        ErrorContext context
    ) {
        AppError appError = ErrorMapper.map(throwable, fallbackCode, fallbackUserMessage, retryable, context);
        showError(owner, isDarkTheme, title, appError, throwable);
        return appError;
    }

    public static void showError(Window owner, boolean isDarkTheme, String title, AppError error) {
        showError(owner, isDarkTheme, title, error, null);
    }

    public static void showError(Window owner, boolean isDarkTheme, String title, AppError error, Throwable throwable) {
        AppError resolved = error == null
            ? ErrorMapper.map(null, ErrorCode.UNEXPECTED_ERROR, ErrorMapper.defaultUserMessage(ErrorCode.UNEXPECTED_ERROR), false)
            : error;
        String errorId = newErrorId();
        logError(resolved, errorId, throwable, title);

        StringBuilder message = new StringBuilder(resolved.userMessage());
        String aiHint = aiScenarioHint(resolved.code());
        if (hasText(aiHint)) {
            message.append("\n").append(aiHint);
        }
        if (resolved.retryable()) {
            message.append("\nПовторите действие позже.");
        }
        message.append("\nКод: ").append(resolved.code().name());
        message.append("\nID: ").append(errorId);

        showAlert(owner, isDarkTheme, Alert.AlertType.ERROR, title, message.toString());
    }

    public static void showInfo(Window owner, boolean isDarkTheme, String title, String message) {
        showAlert(owner, isDarkTheme, Alert.AlertType.INFORMATION, title, message);
    }

    public static void showWarning(Window owner, boolean isDarkTheme, String title, String message) {
        showAlert(owner, isDarkTheme, Alert.AlertType.WARNING, title, message);
    }

    private static void showAlert(
        Window owner,
        boolean isDarkTheme,
        Alert.AlertType type,
        String title,
        String message
    ) {
        alertPresenter.show(owner, isDarkTheme, type, title, message);
    }

    static AutoCloseable withAlertPresenter(AlertPresenter presenter) {
        AlertPresenter previous = alertPresenter;
        alertPresenter = presenter == null ? UiErrorNotifier::showAlertInternal : presenter;
        return () -> alertPresenter = previous;
    }

    private static void showAlertInternal(
        Window owner,
        boolean isDarkTheme,
        Alert.AlertType type,
        String title,
        String message
    ) {
        Alert alert = new Alert(type, message == null ? "" : message, ButtonType.OK);
        alert.setTitle(title == null ? "Уведомление" : title);
        alert.setHeaderText(null);
        DialogPane pane = alert.getDialogPane();
        addStylesheetIfPresent(pane, APP_STYLESHEET);
        if (isDarkTheme) {
            addStylesheetIfPresent(pane, DARK_STYLESHEET);
        }
        pane.getStyleClass().add("styled-alert");
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    private static String newErrorId() {
        return "ERR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static void logError(AppError error, String errorId, Throwable throwable, String title) {
        List<Object> values = new ArrayList<>();
        values.add("errorId");
        values.add(errorId);
        values.add("uiTitle");
        values.add(title == null ? "" : title);
        values.add("retryable");
        values.add(String.valueOf(error.retryable()));
        values.add("technicalMessage");
        values.add(error.technicalMessage());
        for (Map.Entry<String, String> detail : error.details().entrySet()) {
            values.add(detail.getKey());
            values.add(detail.getValue());
        }
        LOG.error("ui.error.notified", error.code(), throwable, values.toArray());
    }

    private static void addStylesheetIfPresent(DialogPane pane, String path) {
        URL resource = UiErrorNotifier.class.getResource(path);
        if (resource != null) {
            pane.getStylesheets().add(resource.toExternalForm());
        }
    }

    private static String aiScenarioHint(ErrorCode code) {
        if (code == null) {
            return "";
        }
        return switch (code) {
            case AI_RATE_LIMITED -> "Лимит запросов исчерпан. Подождите немного перед повторной попыткой.";
            case AI_PROVIDER_ERROR -> "Сбой на стороне AI-провайдера. Это не связано с вашими данными.";
            case AI_RETRY_EXHAUSTED -> "Автоматические повторы исчерпаны.";
            case TASK_DEPENDENCY_CYCLE -> "Проверьте зависимости: задача не может зависеть от себя через цепочку.";
            case TASK_DEPENDENCY_INVALID_REFERENCE -> "Проверьте, что обе задачи существуют и не были удалены.";
            default -> "";
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    interface AlertPresenter {
        void show(Window owner, boolean isDarkTheme, Alert.AlertType type, String title, String message);
    }
}
