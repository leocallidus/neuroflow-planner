package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.service.imageflow.ImageRequestEvent;
import com.example.neuroflowplanner.service.imageflow.ImageRequestProgress;
import com.example.neuroflowplanner.service.imageflow.ImageRequestState;
import com.example.neuroflowplanner.service.imagejob.ImageJobSnapshot;
import com.example.neuroflowplanner.service.imagejob.ImageJobState;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
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

class ImageGenerationLifecycleUiIntegrationTest extends IsolatedTestDataFixture {

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
    void userSeesImageLifecycleStagesDuringExecution() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String conversationId = currentConversationId(dialog);

        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.QUEUED, 1, 2, 50L, "nano-banana", "Запрос в очереди", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.SENDING, 1, 2, 120L, "nano-banana", "Отправка запроса", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.PROVIDER_ACCEPTED, 1, 2, 220L, "nano-banana", "Провайдер принял запрос", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.POLLING, 1, 2, 540L, "nano-banana", "Ожидаю готовность изображения", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.DOWNLOADING, 1, 2, 820L, "nano-banana", "Скачиваю изображение", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.SAVING, 1, 2, 1080L, "nano-banana", "Сохраняю на диск", Map.of()));

        runOnFxThread(() -> {
            HBox statusBar = getField(dialog, "imageLifecycleStatusBar", HBox.class);
            Label statusLabel = getField(dialog, "imageLifecycleStatusLabel", Label.class);
            ProgressIndicator spinner = getField(dialog, "imageLifecycleSpinner", ProgressIndicator.class);
            ProgressBar progressBar = getField(dialog, "imageLifecycleProgressBar", ProgressBar.class);
            assertTrue(statusBar.isVisible(), "Image lifecycle bar must stay visible while request is active");
            assertEquals("Сохранение", statusLabel.getText());
            assertTrue(spinner.isVisible(), "Spinner should indicate active image processing");
            assertTrue(progressBar.getProgress() > 0.0, "Progress bar should reflect active lifecycle stage");
            return null;
        });

        applyImageLifecycleUiState(dialog, event("job-ui-stages", "req-ui-stages", conversationId, ImageRequestState.DONE, 1, 2, 1350L, "nano-banana", "Изображение готово", Map.of("savedPath", "/tmp/image.png")));
        runOnFxThread(() -> {
            Label statusLabel = getField(dialog, "imageLifecycleStatusLabel", Label.class);
            ProgressIndicator spinner = getField(dialog, "imageLifecycleSpinner", ProgressIndicator.class);
            assertEquals("Готово", statusLabel.getText());
            assertFalse(spinner.isVisible(), "Spinner must hide after terminal state");
            return null;
        });

        disposeDialog(dialog);
    }

    @Test
    void retryResumeAndFallbackAnnouncementsAreVisibleWithoutDuplicates() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String conversationId = currentConversationId(dialog);

        applyImageLifecycleUiState(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.QUEUED, 1, 3, 40L, "nano-banana", "Запрос поставлен в очередь", Map.of()));
        appendImageLifecycleAnnouncement(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RETRYING, 2, 3, 400L, "nano-banana", "Повторяю после временной ошибки", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RETRYING, 2, 3, 400L, "nano-banana", "Повторяю после временной ошибки", Map.of()));
        appendImageLifecycleAnnouncement(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RESUMING, 2, 3, 550L, "nano-banana", "Восстанавливаю ожидание", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RESUMING, 2, 3, 550L, "nano-banana", "Восстанавливаю ожидание", Map.of()));
        appendImageLifecycleAnnouncement(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RESUMING, 2, 3, 650L, "nano-banana", "Восстанавливаю ожидание", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.RESUMING, 2, 3, 650L, "nano-banana", "Восстанавливаю ожидание", Map.of()));
        appendImageLifecycleAnnouncement(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.FALLBACK_MODEL, 2, 3, 800L, "gemini-3-pro-image-preview", "Переключаюсь на fallback", Map.of()));
        applyImageLifecycleUiState(dialog, event("job-ui-announce", "req-ui-announce", conversationId, ImageRequestState.FALLBACK_MODEL, 2, 3, 800L, "gemini-3-pro-image-preview", "Переключаюсь на fallback", Map.of()));

        runOnFxThread(() -> {
            Label attemptLabel = getField(dialog, "imageLifecycleAttemptLabel", Label.class);
            List<String> systemMessages = collectSystemMessages(dialog);
            assertTrue(attemptLabel.getText().contains("fallback"));
            assertEquals(1, systemMessages.stream().filter(text -> text.contains("Повторная попытка генерации изображения")).count());
            assertEquals(1, systemMessages.stream().filter(text -> text.contains("Восстанавливаю ожидание уже отправленного image-запроса")).count());
            assertEquals(1, systemMessages.stream().filter(text -> text.contains("резервную модель изображения")).count());
            return null;
        });

        disposeDialog(dialog);
    }

    @Test
    void actionButtonsSwitchBetweenPauseResumeAndRetryStates() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();

        runOnFxThread(() -> {
            setField(dialog, "latestImageJobSnapshot", snapshot("job-controls", ImageJobState.POLLING));
            setField(dialog, "latestImageRequestEvent", null);
            invoke(dialog, "updateImageJobActionControls", new Class<?>[]{ImageRequestEvent.class}, new Object[]{null});
            Button primary = getField(dialog, "imageLifecyclePrimaryActionButton", Button.class);
            Button secondary = getField(dialog, "imageLifecycleSecondaryActionButton", Button.class);
            assertTrue(primary.isVisible());
            assertTrue(secondary.isVisible());

            setField(dialog, "latestImageJobSnapshot", snapshot("job-controls", ImageJobState.PAUSED));
            invoke(dialog, "updateImageJobActionControls", new Class<?>[]{ImageRequestEvent.class}, new Object[]{null});
            assertTrue(primary.isVisible(), "Resume action should be visible for paused job");
            assertTrue(secondary.isVisible(), "Cancel action should remain visible for paused job");

            setField(dialog, "latestImageJobSnapshot", snapshot("job-controls", ImageJobState.FAILED));
            invoke(dialog, "updateImageJobActionControls", new Class<?>[]{ImageRequestEvent.class}, new Object[]{null});
            assertTrue(primary.isVisible(), "Retry action should be visible for failed job");
            assertFalse(secondary.isVisible(), "Secondary cancel action should be hidden for failed job");
            return null;
        });

        disposeDialog(dialog);
    }

    @Test
    void longRunningImageScenarioDoesNotLookStuck() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = createDialog();
        String conversationId = currentConversationId(dialog);

        applyImageLifecycleUiState(dialog, event("job-ui-long", "req-ui-long", conversationId, ImageRequestState.QUEUED, 1, 2, 20L, "nano-banana", "Запрос поставлен в очередь", Map.of()));
        applyImageLifecycleUiState(dialog, event(
            "job-ui-long",
            "req-ui-long",
            conversationId,
            ImageRequestState.POLLING,
            1,
            2,
            65_000L,
            "nano-banana",
            "Изображение ещё готовится. Продолжаю ждать.",
            Map.of("heartbeat", "true")
        ));

        runOnFxThread(() -> {
            HBox statusBar = getField(dialog, "imageLifecycleStatusBar", HBox.class);
            Label statusLabel = getField(dialog, "imageLifecycleStatusLabel", Label.class);
            Label elapsedLabel = getField(dialog, "imageLifecycleElapsedLabel", Label.class);
            Label detailLabel = getField(dialog, "imageLifecycleDetailLabel", Label.class);
            ProgressIndicator spinner = getField(dialog, "imageLifecycleSpinner", ProgressIndicator.class);
            assertTrue(statusBar.isVisible());
            assertEquals("Ожидание результата", statusLabel.getText());
            assertEquals("01:05", elapsedLabel.getText());
            assertTrue(detailLabel.getText().contains("Продолжаю ждать"));
            assertTrue(spinner.isVisible());
            return null;
        });

        applyImageLifecycleUiState(dialog, event("job-ui-long", "req-ui-long", conversationId, ImageRequestState.DONE, 1, 2, 70_000L, "nano-banana", "Изображение успешно сохранено.", Map.of("savedPath", "/tmp/ui-long.png")));
        assertTrue(awaitCondition(
            () -> {
                try {
                    return runOnFxThread(() -> !getField(dialog, "imageLifecycleStatusBar", HBox.class).isVisible());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            },
            Duration.ofSeconds(3)
        ));

        disposeDialog(dialog);
    }

    private static ImageJobSnapshot snapshot(String jobId, ImageJobState state) {
        ImageJobSnapshot snapshot = new ImageJobSnapshot();
        snapshot.setJobId(jobId);
        snapshot.setState(state);
        snapshot.setRequestedModel("nano-banana");
        snapshot.setCreatedAt(System.currentTimeMillis());
        snapshot.setUpdatedAt(System.currentTimeMillis());
        return snapshot;
    }

    private static ImageRequestEvent event(
        String jobId,
        String requestId,
        String conversationId,
        ImageRequestState state,
        int attempt,
        int maxAttempts,
        long elapsedMs,
        String model,
        String message,
        Map<String, String> metadata
    ) {
        return new ImageRequestEvent(
            jobId,
            requestId,
            conversationId,
            state,
            model,
            message,
            new ImageRequestProgress(elapsedMs, attempt, maxAttempts, state.isTerminal()),
            Instant.now(),
            metadata
        );
    }

    private static String currentConversationId(ChatBotDialog dialog) throws Exception {
        Object conversation = getField(dialog, "currentConversation", Object.class);
        Method method = conversation.getClass().getMethod("getId");
        return (String) method.invoke(conversation);
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

    private static void applyImageLifecycleUiState(ChatBotDialog dialog, ImageRequestEvent event) throws Exception {
        runOnFxThread(() -> {
            if (event.state() == ImageRequestState.QUEUED) {
                invoke(dialog, "prepareImageLifecycleForNewRequest", new Class<?>[]{ImageRequestEvent.class}, event);
            }
            invoke(dialog, "applyImageLifecycleVisualState", new Class<?>[]{ImageRequestEvent.class}, event);
            return null;
        });
    }

    private static void appendImageLifecycleAnnouncement(ChatBotDialog dialog, ImageRequestEvent event) throws Exception {
        runOnFxThread(() -> {
            invoke(dialog, "maybeAppendImageLifecycleSystemMessage", new Class<?>[]{ImageRequestEvent.class}, event);
            return null;
        });
    }

    private static List<String> collectSystemMessages(ChatBotDialog dialog) {
        VBox messagesBox = getField(dialog, "messagesBox", VBox.class);
        List<String> result = new ArrayList<>();
        for (javafx.scene.Node node : messagesBox.lookupAll(".chat-system-message")) {
            if (node instanceof Label label) {
                result.add(label.getText() == null ? "" : label.getText());
            }
        }
        return result;
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

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to write field " + name, e);
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke " + methodName, e);
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
