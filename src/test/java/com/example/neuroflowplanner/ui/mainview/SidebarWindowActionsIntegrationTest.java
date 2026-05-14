package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarWindowActionsIntegrationTest {
    private static final List<String> WINDOW_ACTION_IDS = List.of(
        "main.view.calendar",
        "main.view.kanban",
        "main.view.gantt",
        "main.analytics.dashboard",
        "main.analytics.planningQuality",
        "main.analytics.statistics",
        "main.analytics.personalInsights",
        "main.analytics.goals",
        "main.analytics.timeStats",
        "main.analytics.workload",
        "main.analytics.heatmap",
        "main.analytics.projectProgress",
        "main.tools.notes.open",
        "main.tools.pomodoro",
        "main.tools.timeTracker",
        "main.tools.workHours",
        "main.ai.chat",
        "main.ai.analyzeCenter",
        "main.ai.reminders",
        "main.ai.categorization",
        "main.system.export",
        "main.system.settings",
        "main.system.help"
    );

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
    void sidebarWindowActionsOpenInlineViews() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        UiActionRegistry registry = getPrivateField(view, "commandActionRegistry", UiActionRegistry.class);
        StackPane overlayHost = getPrivateField(view, "overlayHost", StackPane.class);

        runOnFxThread(() -> {
            Task demoTask = new Task("task-smoke-1", "Demo", "Smoke", LocalDate.now().plusDays(1), 3);
            view.applyState(MainViewState.initial().markInitialized(List.of(demoTask)));
            return null;
        });

        for (String actionId : WINDOW_ACTION_IDS) {
            UndoRedoManager.CommandResult result = runOnFxThread(() -> registry.execute(actionId));
            assertTrue(
                result.successful(),
                () -> "Action " + actionId + " should open window, got: " + result.status() + " / " + result.message()
            );
            assertTrue(
                runOnFxThread(overlayHost::isVisible),
                () -> "Overlay must be visible after action: " + actionId
            );
            runOnFxThread(() -> {
                view.closeInline();
                return null;
            });
        }
    }

    @Test
    void sidebarButtonsAreBoundToRegistryActionsAfterViewConstruction() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        StackPane overlayHost = getPrivateField(view, "overlayHost", StackPane.class);
        @SuppressWarnings("unchecked")
        List<Button> sidebarButtons = getPrivateField(view, "sidebarButtons", List.class);
        @SuppressWarnings("unchecked")
        List<Button> contextButtons = getPrivateField(view, "contextSidebarDomainButtons", List.class);

        runOnFxThread(() -> {
            Task demoTask = new Task("task-smoke-2", "Demo 2", "Smoke", LocalDate.now().plusDays(2), 4);
            view.applyState(MainViewState.initial().markInitialized(List.of(demoTask)));
            return null;
        });

        Button calendarButton = runOnFxThread(() -> {
            Button found = findSidebarButton(sidebarButtons, "main.view.calendar");
            if (found != null) {
                return found;
            }
            found = findSidebarButton(contextButtons, "main.view.calendar");
            if (found != null) {
                return found;
            }
            invokeShowActionInSidebar(view, "main.view.calendar");
            found = findSidebarButton(sidebarButtons, "main.view.calendar");
            if (found != null) {
                return found;
            }
            return findSidebarButton(contextButtons, "main.view.calendar");
        });
        assertNotNull(calendarButton, "Sidebar button for calendar action should exist");
        assertNotNull(calendarButton.getOnAction(), "Sidebar button should have onAction binding");

        runOnFxThread(() -> {
            calendarButton.fire();
            return null;
        });
        assertTrue(
            runOnFxThread(overlayHost::isVisible),
            "Sidebar click should execute action and open inline overlay"
        );
    }

    private static LegacyMainView createView(double width, double height) throws Exception {
        return runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, width, height);
            created.applyCss();
            created.layout();
            return created;
        });
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access field: " + fieldName, ex);
        }
    }

    private static boolean invokeShowActionInSidebar(LegacyMainView view, String actionId) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod("showActionInSidebar", String.class);
            method.setAccessible(true);
            Object result = method.invoke(view, actionId);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot invoke showActionInSidebar", ex);
        }
    }

    private static Button findSidebarButton(List<Button> buttons, String actionId) {
        if (buttons == null || actionId == null) {
            return null;
        }
        for (Button button : buttons) {
            if (button == null) {
                continue;
            }
            Object rawActionId = button.getProperties().get("sidebar.actionId");
            if (actionId.equals(rawActionId)) {
                return button;
            }
        }
        return null;
    }

    private static <T> T runOnFxThread(ThrowingSupplier<T> supplier) throws Exception {
        CompletableFuture<T> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get(20, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
