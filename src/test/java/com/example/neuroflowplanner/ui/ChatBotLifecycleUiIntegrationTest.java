package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.chatflow.ChatRequestEvent;
import com.example.neuroflowplanner.service.chatflow.ChatRequestProgress;
import com.example.neuroflowplanner.service.chatflow.ChatRequestState;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBotLifecycleUiIntegrationTest extends IsolatedTestDataFixture {

    private static boolean fxRuntimeReady;

    @BeforeAll
    static void initFxRuntime() {
        try {
            CompletableFuture<Void> started = new CompletableFuture<>();
            Platform.startup(() -> started.complete(null));
            started.get(5, TimeUnit.SECONDS);
            fxRuntimeReady = true;
        } catch (IllegalStateException alreadyStarted) {
            fxRuntimeReady = true;
        } catch (Throwable ignored) {
            fxRuntimeReady = false;
        }
    }

    @Test
    void userSeesLifecycleStagesDuringExecution() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String requestId = "req-ui-stages";

        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.QUEUED, 1, 3, 80L, "primary-model", "Запрос в очереди", Map.of()));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.SENDING, 1, 3, 140L, "primary-model", "Отправка запроса", Map.of()));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.WAITING_PROVIDER, 1, 3, 260L, "primary-model", "Ожидаю ответ провайдера", Map.of()));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.GENERATING, 1, 3, 520L, "primary-model", "Поступают части ответа", Map.of("streaming", "true")));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.POST_PROCESSING, 1, 3, 900L, "primary-model", "Подготавливаю финальный ответ", Map.of()));

        HBox statusBar = getField(dialog, "lifecycleStatusBar", HBox.class);
        Label statusLabel = getField(dialog, "lifecycleStatusLabel", Label.class);
        ProgressIndicator spinner = getField(dialog, "lifecycleSpinner", ProgressIndicator.class);
        ProgressBar progressBar = getField(dialog, "lifecycleProgressBar", ProgressBar.class);

        assertTrue(statusBar.isVisible(), "Lifecycle bar must be visible while request is running");
        assertEquals("Финализация", statusLabel.getText());
        assertTrue(spinner.isVisible(), "Spinner should stay visible for non-terminal states");
        assertTrue(progressBar.getProgress() > 0.0, "Progress bar should reflect non-zero stage progress");

        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.DONE, 1, 3, 1200L, "primary-model", "Ответ готов", Map.of()));
        assertEquals("Готово", statusLabel.getText());
        assertFalse(spinner.isVisible(), "Spinner must be hidden after terminal state");

        disposeDialog(dialog);
    }

    @Test
    void retryAndFallbackAreReflectedInUi() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String requestId = "req-ui-fallback";

        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.QUEUED, 1, 3, 50L, "model-primary", "Запрос поставлен в очередь", Map.of()));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.RETRYING, 2, 3, 400L, "model-primary", "Ответ получен после повторной попытки", Map.of("attemptRecovered", "true")));
        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.FALLBACK_MODEL, 2, 3, 520L, "model-fallback", "Включена резервная модель", Map.of("fallback", "true")));

        Label attemptLabel = getField(dialog, "lifecycleAttemptLabel", Label.class);
        Label statusLabel = getField(dialog, "lifecycleStatusLabel", Label.class);
        List<String> systemMessages = collectSystemMessages(dialog);

        assertTrue(attemptLabel.getText().contains("fallback"), "Attempt indicator should show fallback mode");
        assertEquals("Резервная модель", statusLabel.getText());
        assertTrue(systemMessages.stream().anyMatch(text -> text.contains("Повторная попытка")));
        assertTrue(systemMessages.stream().anyMatch(text -> text.contains("резервную модель")));

        disposeDialog(dialog);
    }

    @Test
    void longRunningScenarioShowsFeedbackAndDoesNotLookStuck() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String requestId = "req-ui-long-running";

        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.QUEUED, 1, 2, 20L, "model-primary", "Запрос поставлен в очередь", Map.of()));
        emitLifecycleEvent(dialog, event(
            requestId,
            ChatRequestState.WAITING_PROVIDER,
            1,
            2,
            65_000L,
            "model-primary",
            "Ожидаю ответ провайдера",
            Map.of("heartbeat", "true")
        ));

        HBox statusBar = getField(dialog, "lifecycleStatusBar", HBox.class);
        Label statusLabel = getField(dialog, "lifecycleStatusLabel", Label.class);
        Label elapsedLabel = getField(dialog, "lifecycleElapsedLabel", Label.class);
        Label detailLabel = getField(dialog, "lifecycleDetailLabel", Label.class);
        ProgressIndicator spinner = getField(dialog, "lifecycleSpinner", ProgressIndicator.class);

        assertTrue(statusBar.isVisible(), "Lifecycle bar should remain visible during long-running state");
        assertEquals("Ожидание модели", statusLabel.getText());
        assertEquals("01:05", elapsedLabel.getText());
        assertTrue(detailLabel.getText().contains("Ожидаю ответ провайдера"));
        assertTrue(spinner.isVisible(), "Spinner should indicate active processing");

        emitLifecycleEvent(dialog, event(requestId, ChatRequestState.DONE, 1, 2, 70_000L, "model-primary", "Ответ готов", Map.of()));
        assertTrue(awaitCondition(
            () -> !getField(dialog, "lifecycleStatusBar", HBox.class).isVisible(),
            Duration.ofSeconds(3)
        ), "Lifecycle bar should auto-hide after terminal state");

        disposeDialog(dialog);
    }

    @Test
    void summarizingStateLocksInputAndShowsBanner() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String requestId = "req-ui-summarizing";

        emitLifecycleEvent(dialog, event(
            requestId,
            ChatRequestState.SUMMARIZING,
            1,
            1,
            350L,
            "openai/gpt-5.4",
            "Сжимаем контекст перед ответом",
            Map.of()
        ));

        HBox banner = getField(dialog, "summarizeLockBanner", HBox.class);
        Label bannerLabel = getField(dialog, "summarizeLockBannerLabel", Label.class);
        TextField inputField = getField(dialog, "inputField", TextField.class);
        Button attachMediaButton = getField(dialog, "attachMediaButton", Button.class);

        assertTrue(banner.isVisible(), "Summarize lock banner must be visible while context is compacting");
        assertTrue(bannerLabel.getText().contains("Сжимаем контекст"));
        assertTrue(inputField.isDisable(), "Input field must be disabled during summarize");
        assertTrue(attachMediaButton.isDisable(), "Attach action must be blocked during summarize");

        emitLifecycleEvent(dialog, event(
            requestId,
            ChatRequestState.DONE,
            1,
            1,
            1200L,
            "openai/gpt-5.4",
            "Ответ готов",
            Map.of()
        ));

        assertFalse(getField(dialog, "summarizeLockBanner", HBox.class).isVisible());
        assertFalse(getField(dialog, "inputField", TextField.class).isDisable());

        disposeDialog(dialog);
    }

    private static ChatRequestEvent event(
        String requestId,
        ChatRequestState state,
        int attempt,
        int maxAttempts,
        long elapsedMs,
        String model,
        String message,
        Map<String, String> metadata
    ) {
        return new ChatRequestEvent(
            requestId,
            "",
            state,
            model,
            message,
            new ChatRequestProgress(elapsedMs, attempt, maxAttempts, state.isTerminal()),
            Instant.now(),
            metadata
        );
    }

    private static ChatBotDialog createDialog() throws Exception {
        ChatBotDialog dialog = runOnFxThread(() -> (ChatBotDialog) ChatBotDialog.inline());
        runOnFxThread(() -> null);
        return dialog;
    }

    private static void disposeDialog(ChatBotDialog dialog) throws Exception {
        if (dialog == null) {
            return;
        }
        runOnFxThread(() -> {
            dialog.onDispose();
            return null;
        });
    }

    private static void emitLifecycleEvent(ChatBotDialog dialog, ChatRequestEvent event) throws Exception {
        Method method = ChatBotDialog.class.getDeclaredMethod("onChatRequestEvent", ChatRequestEvent.class);
        method.setAccessible(true);
        method.invoke(dialog, event);
        runOnFxThread(() -> null);
    }

    private static List<String> collectSystemMessages(ChatBotDialog dialog) throws Exception {
        return runOnFxThread(() -> {
            VBox messagesBox = getField(dialog, "messagesBox", VBox.class);
            List<String> result = new ArrayList<>();
            for (javafx.scene.Node node : messagesBox.lookupAll(".chat-system-message")) {
                if (node instanceof Label label) {
                    result.add(label.getText() == null ? "" : label.getText());
                }
            }
            return result;
        });
    }

    private static boolean awaitCondition(BooleanSupplier condition, Duration timeout) throws Exception {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(40L);
            runOnFxThread(() -> null);
        }
        return condition.getAsBoolean();
    }

    private static <T> T getField(Object target, String name, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to read field " + name, e);
        }
    }

    private static <T> T runOnFxThread(Supplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }
}
