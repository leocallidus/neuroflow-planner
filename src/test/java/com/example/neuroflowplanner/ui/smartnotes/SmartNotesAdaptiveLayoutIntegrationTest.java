package com.example.neuroflowplanner.ui.smartnotes;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartNotesAdaptiveLayoutIntegrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE
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
        resetSingleton();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
        resetSingleton();
    }

    @Test
    void normal1366x768UsesNormalBreakpointSizing() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);

        LegacySmartNotesDialog dialog = createDialog(1366, 768);
        HBox root = getPrivateField(dialog, "root", HBox.class);
        VBox sidebar = getPrivateField(dialog, "sidebarBox", VBox.class);
        FlowPane actionsToolbar = getPrivateField(dialog, "actionsToolbar", FlowPane.class);

        assertTrue(root.getStyleClass().contains("layout-breakpoint-normal"));
        assertTrue(root.getStyleClass().contains("layout-density-comfortable"));
        assertEquals(250.0, sidebar.getPrefWidth(), 0.5);
        assertEquals(360.0, actionsToolbar.getPrefWrapLength(), 0.5);
    }

    @Test
    void compact1280x800UsesCompactBreakpointSizing() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMPACT);

        LegacySmartNotesDialog dialog = createDialog(1280, 800);
        HBox root = getPrivateField(dialog, "root", HBox.class);
        VBox sidebar = getPrivateField(dialog, "sidebarBox", VBox.class);
        FlowPane actionsToolbar = getPrivateField(dialog, "actionsToolbar", FlowPane.class);

        assertTrue(root.getStyleClass().contains("layout-breakpoint-compact"));
        assertTrue(root.getStyleClass().contains("layout-density-compact"));
        assertEquals(74.0, sidebar.getPrefWidth(), 0.5);
        assertEquals(260.0, actionsToolbar.getPrefWrapLength(), 0.5);
    }

    private LegacySmartNotesDialog createDialog(double width, double height) throws Exception {
        return runOnFxThread(() -> {
            LegacySmartNotesDialog dialog = (LegacySmartNotesDialog) LegacySmartNotesDialog.inline();
            Parent rootNode = (Parent) dialog.getContent();
            new Scene(rootNode, width, height);
            rootNode.applyCss();
            rootNode.layout();
            return dialog;
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

    private static void resetSingleton() {
        try {
            Field instanceField = LegacySmartNotesDialog.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Unable to reset LegacySmartNotesDialog singleton", ex);
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
