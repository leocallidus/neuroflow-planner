package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.chatio.ChatArchiveFormat;
import com.example.neuroflowplanner.testinfra.IsolatedTestDataFixture;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortabilityUiIntegrationTest extends IsolatedTestDataFixture {

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
    void chatBotDialogShowsDropdownExportMenuForCurrentConversation() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ChatBotDialog dialog = runOnFxThread(() -> {
            ChatBotDialog created = (ChatBotDialog) ChatBotDialog.inline();
            attachScene(created.getContent());
            return created;
        });

        runOnFxThread(() -> {
            MenuButton exportMenuButton = getField(dialog, "exportMenuButton", MenuButton.class);
            assertNotNull(exportMenuButton);
            assertEquals("Экспорт", exportMenuButton.getText());
            assertEquals(List.of("PDF (.pdf)", "Markdown (.md)", "JSON (.json)"), exportMenuButton.getItems().stream().map(MenuItem::getText).toList());
            assertTrue(exportMenuButton.getItems().stream().allMatch(item -> item.getStyleClass().contains("chat-archive-menu-item")));

            invoke(dialog, "updateConversationExportState", new Class<?>[]{boolean.class, ChatArchiveFormat.class}, true, ChatArchiveFormat.JSON);
            assertTrue(exportMenuButton.isDisabled());
            assertEquals("Экспорт...", exportMenuButton.getText());

            invoke(dialog, "updateConversationExportState", new Class<?>[]{boolean.class, ChatArchiveFormat.class}, false, ChatArchiveFormat.JSON);
            assertFalse(exportMenuButton.isDisabled());
            assertEquals("Экспорт", exportMenuButton.getText());
            return null;
        });

        runOnFxThread(() -> {
            dialog.onDispose();
            return null;
        });
    }

    @Test
    void settingsDialogShowsArchiveExportMenuAndImportAction() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        SettingsDialog dialog = runOnFxThread(() -> {
            SettingsDialog created = (SettingsDialog) SettingsDialog.inline();
            attachScene(created.getContent());
            return created;
        });

        runOnFxThread(() -> {
            MenuButton exportMenuButton = getField(dialog, "assistantExportChatsMenuButton", MenuButton.class);
            Button importButton = getField(dialog, "assistantImportChatsButton", Button.class);
            assertNotNull(exportMenuButton);
            assertNotNull(importButton);
            assertEquals(List.of("PDF (.pdf)", "Markdown (.md)", "JSON (.json)"), exportMenuButton.getItems().stream().map(MenuItem::getText).toList());
            assertTrue(exportMenuButton.getItems().stream().allMatch(item -> item.getStyleClass().contains("chat-archive-menu-item")));
            assertEquals("Импортировать переписки (JSON)", importButton.getText());

            invoke(dialog, "updateAssistantChatArchiveBusy", new Class<?>[]{boolean.class, String.class}, true, "Импорт...");
            assertTrue(exportMenuButton.isDisabled());
            assertTrue(importButton.isDisabled());
            assertEquals("Импорт...", importButton.getText());

            invoke(dialog, "updateAssistantChatArchiveBusy", new Class<?>[]{boolean.class, String.class}, false, null);
            assertFalse(exportMenuButton.isDisabled());
            assertFalse(importButton.isDisabled());
            assertEquals("Экспортировать все переписки", exportMenuButton.getText());
            assertEquals("Импортировать переписки (JSON)", importButton.getText());
            return null;
        });
    }

    @Test
    void exportDialogShowsJsonCardAndPortabilityHint() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        ExportDialog dialog = runOnFxThread(() -> {
            ExportDialog created = (ExportDialog) ExportDialog.inline(List.of(
                new Task("export-task", "Экспортируемая задача", "", LocalDate.now().plusDays(2), 3)
            ));
            attachScene(created.getContent());
            return created;
        });

        runOnFxThread(() -> {
            Parent root = (Parent) dialog.getContent();
            assertFalse(root.lookupAll(".export-card-json").isEmpty(), "JSON export card should be visible in ExportDialog");
            assertTrue(root.lookupAll(".export-hint-box").size() == 1, "Portability hint box should be rendered");
            List<String> labels = root.lookupAll(".label").stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getText)
                .toList();
            assertTrue(labels.stream().anyMatch(text -> text != null && text.contains("JSON (.json)")));
            assertTrue(labels.stream().anyMatch(text -> text != null && text.contains("JSON для переноса")));

            String fileName = (String) invoke(dialog, "generateFileName", new Class<?>[]{String.class}, "json");
            assertTrue(fileName.startsWith("neuroflow_tasks_"));
            assertTrue(fileName.endsWith(".json"));
            return null;
        });
    }

    private static void attachScene(javafx.scene.Node node) {
        if (node.getScene() == null) {
            new Scene((Parent) node, 1200, 800);
        }
        node.applyCss();
        if (node instanceof Parent parent) {
            parent.layout();
        }
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

    private static Object invoke(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName, paramTypes);
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke method " + methodName, e);
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
