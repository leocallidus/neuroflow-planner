package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveCenterLayoutIntegrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED
    );

    private static boolean fxRuntimeReady;
    private final Map<String, String> snapshot = new LinkedHashMap<>();

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

    @BeforeEach
    void setUp() {
        snapshotConfig();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
    }

    @Test
    void compactBreakpointCollapsesSecondaryTaskColumns() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");

        LegacyMainView view = createView(1280, 760);
        TreeTableView<?> taskTable = getPrivateField(view, "taskTable", TreeTableView.class);
        TreeTableColumn<?, ?> tagsColumn = getPrivateField(view, "taskTagsColumn", TreeTableColumn.class);
        TreeTableColumn<?, ?> complexityColumn = getPrivateField(view, "taskComplexityColumn", TreeTableColumn.class);
        TreeTableColumn<?, ?> priorityColumn = getPrivateField(view, "taskPriorityColumn", TreeTableColumn.class);
        TreeTableColumn<?, ?> deadlineColumn = getPrivateField(view, "taskDeadlineColumn", TreeTableColumn.class);

        assertFalse(tagsColumn.isVisible());
        assertFalse(complexityColumn.isVisible());
        assertFalse(priorityColumn.isVisible());
        assertTrue(deadlineColumn.isVisible());
        assertTrue(taskTable.getStyleClass().contains("task-table-narrow"));
        assertTrue(taskTable.getStyleClass().contains("task-table-compact"));
    }

    @Test
    void wideBreakpointKeepsSecondaryColumnsVisible() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");

        LegacyMainView view = createView(1720, 900);
        TreeTableView<?> taskTable = getPrivateField(view, "taskTable", TreeTableView.class);
        TreeTableColumn<?, ?> tagsColumn = getPrivateField(view, "taskTagsColumn", TreeTableColumn.class);
        TreeTableColumn<?, ?> complexityColumn = getPrivateField(view, "taskComplexityColumn", TreeTableColumn.class);
        TreeTableColumn<?, ?> priorityColumn = getPrivateField(view, "taskPriorityColumn", TreeTableColumn.class);

        assertTrue(tagsColumn.isVisible());
        assertTrue(complexityColumn.isVisible());
        assertTrue(priorityColumn.isVisible());
        assertFalse(taskTable.getStyleClass().contains("task-table-narrow"));
    }

    private LegacyMainView createView(double width, double height) throws Exception {
        return runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, width, height);
            created.applyCss();
            created.layout();
            return created;
        });
    }

    private void snapshotConfig() {
        snapshot.clear();
        for (String key : CONFIG_KEYS) {
            snapshot.put(key, ConfigManager.getProperty(key));
        }
    }

    private void restoreConfig() {
        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            setRuntimeConfig(entry.getKey(), entry.getValue());
        }
    }

    private void setRuntimeConfig(String key, String value) {
        Properties properties = runtimeProperties();
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }

    private Properties runtimeProperties() {
        try {
            return (Properties) PROPERTIES_FIELD.get(null);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties", ex);
        }
    }

    private static Field resolvePropertiesField() {
        try {
            Field field = ConfigManager.class.getDeclaredField("properties");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to access ConfigManager.properties field", ex);
        }
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
