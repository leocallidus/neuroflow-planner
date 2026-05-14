package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.commandpalette.OverlayDialogManager;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteController;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteDialog;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteItem;
import com.example.neuroflowplanner.ui.commandpalette.CommandPaletteView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPaletteContractIntegrationTest {
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
    void toggleContractOpensAndClosesPaletteDeterministically() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        OverlayDialogManager manager = getPrivateField(view, "overlayDialogManager", OverlayDialogManager.class);
        StubOverlayHandle stub = new StubOverlayHandle();

        runOnFxThread(() -> {
            manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, stub);
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertEquals(1, stub.openCalls.get());
        assertEquals(0, stub.closeCalls.get());

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertFalse(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertEquals(1, stub.openCalls.get());
        assertEquals(1, stub.closeCalls.get());
    }

    @Test
    void openContractKeepsPaletteOpenAndUpdatesRequest() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        OverlayDialogManager manager = getPrivateField(view, "overlayDialogManager", OverlayDialogManager.class);
        StubOverlayHandle stub = new StubOverlayHandle();

        runOnFxThread(() -> {
            manager.register(OverlayDialogManager.OverlayId.COMMAND_PALETTE, stub);
            invokeOpenPalette(view, "first", false);
            invokeOpenPalette(view, "second", false);
            return null;
        });

        assertTrue(manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE));
        assertEquals(2, stub.openCalls.get());
        assertEquals("second", stub.lastRequest.initialQuery());
    }

    @Test
    void realPaletteClosesViaToggleEscAndCloseButton() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        CommandPaletteDialog paletteDialog = getPrivateField(view, "commandPaletteDialog", CommandPaletteDialog.class);

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertTrue(runOnFxThread(paletteDialog::isOpen));

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertFalse(runOnFxThread(paletteDialog::isOpen));

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertTrue(runOnFxThread(paletteDialog::isOpen));

        runOnFxThread(() -> {
            javafx.scene.control.Dialog<?> dialog = getPrivateField(paletteDialog, "activeDialog", javafx.scene.control.Dialog.class);
            if (dialog == null || dialog.getDialogPane() == null || dialog.getDialogPane().getScene() == null) {
                throw new IllegalStateException("Palette dialog scene is unavailable");
            }
            KeyEvent escEvent = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
            javafx.event.Event.fireEvent(dialog.getDialogPane().getScene(), escEvent);
            return null;
        });
        assertFalse(runOnFxThread(paletteDialog::isOpen));

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertTrue(runOnFxThread(paletteDialog::isOpen));

        runOnFxThread(() -> {
            CommandPaletteView paletteView = getPrivateField(paletteDialog, "activePaletteView", CommandPaletteView.class);
            Button closeButton = getPrivateField(paletteView, "closeButton", Button.class);
            closeButton.fire();
            return null;
        });
        assertFalse(runOnFxThread(paletteDialog::isOpen));
    }

    @Test
    void realPaletteTabCycleStaysInsidePaletteControls() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        CommandPaletteDialog paletteDialog = getPrivateField(view, "commandPaletteDialog", CommandPaletteDialog.class);

        runOnFxThread(() -> {
            invokeOpenPalette(view, "", true);
            return null;
        });
        assertTrue(runOnFxThread(paletteDialog::isOpen));

        CommandPaletteView paletteView = runOnFxThread(
            () -> getPrivateField(paletteDialog, "activePaletteView", CommandPaletteView.class)
        );
        TextField queryField = getPrivateField(paletteView, "queryField", TextField.class);
        @SuppressWarnings("unchecked")
        ListView<Object> resultsView = (ListView<Object>) getPrivateField(paletteView, "resultsView", ListView.class);
        Button closeButton = getPrivateField(paletteView, "closeButton", Button.class);

        runOnFxThread(() -> {
            queryField.requestFocus();
            return null;
        });
        runOnFxThread(() -> {
            javafx.event.Event.fireEvent(queryField, tabKey(false));
            return null;
        });
        runOnFxThread(() -> null);
        assertEquals(resultsView, runOnFxThread(() -> activeDialogFocusOwner(paletteDialog)));

        runOnFxThread(() -> {
            javafx.event.Event.fireEvent(resultsView, tabKey(false));
            return null;
        });
        runOnFxThread(() -> null);
        Node focusAfterListTab = runOnFxThread(() -> activeDialogFocusOwner(paletteDialog));
        assertTrue(focusAfterListTab == resultsView || focusAfterListTab == closeButton);

        runOnFxThread(() -> {
            queryField.requestFocus();
            return null;
        });

        runOnFxThread(() -> {
            javafx.event.Event.fireEvent(queryField, tabKey(true));
            return null;
        });
        runOnFxThread(() -> null);
        assertEquals(closeButton, runOnFxThread(() -> activeDialogFocusOwner(paletteDialog)));
    }

    @Test
    void ctrlKShortcutOpensPaletteAndUpdatesOverlayIndicator() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        CommandPaletteDialog paletteDialog = getPrivateField(view, "commandPaletteDialog", CommandPaletteDialog.class);
        Label paletteIndicator = getPrivateField(view, "commandPaletteOverlayStateLabel", Label.class);
        OverlayDialogManager manager = getPrivateField(view, "overlayDialogManager", OverlayDialogManager.class);

        runOnFxThread(() -> {
            invokeGlobalShortcut(view, ctrlKey(KeyCode.K, "k"));
            view.applyCss();
            view.layout();
            return null;
        });

        assertTrue(runOnFxThread(paletteDialog::isOpen), "Ctrl+K should open command palette");
        assertTrue(
            runOnFxThread(() -> manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE)),
            "Ctrl+K should mark command palette overlay as open"
        );
        if (paletteIndicator != null) {
            assertTrue(paletteIndicator.getText().contains("OPEN"), "Palette indicator should reflect open overlay state");
        }

        runOnFxThread(() -> {
            paletteDialog.close();
            view.applyCss();
            view.layout();
            return null;
        });
        assertFalse(runOnFxThread(paletteDialog::isOpen));
        assertFalse(runOnFxThread(() -> manager.isOpen(OverlayDialogManager.OverlayId.COMMAND_PALETTE)));
        if (paletteIndicator != null) {
            assertTrue(paletteIndicator.getText().contains("CLOSED"));
        }
    }

    @Test
    void sidebarActionExecutionIsVisibleInPaletteRecentResults() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        @SuppressWarnings("unchecked")
        List<Button> sidebarButtons = getPrivateField(view, "sidebarButtons", List.class);
        @SuppressWarnings("unchecked")
        List<Button> contextButtons = getPrivateField(view, "contextSidebarDomainButtons", List.class);
        CommandPaletteController controller = getPrivateField(view, "commandPaletteController", CommandPaletteController.class);

        Button settingsButton = runOnFxThread(() -> {
            Button found = findSidebarButton(sidebarButtons, "main.system.settings");
            if (found != null) {
                return found;
            }
            found = findSidebarButton(contextButtons, "main.system.settings");
            if (found != null) {
                return found;
            }
            invokeShowActionInSidebar(view, "main.system.settings");
            found = findSidebarButton(sidebarButtons, "main.system.settings");
            if (found != null) {
                return found;
            }
            return findSidebarButton(contextButtons, "main.system.settings");
        });
        assertTrue(settingsButton != null && settingsButton.getOnAction() != null, "Sidebar settings action must be present");

        runOnFxThread(() -> {
            settingsButton.fire();
            return null;
        });

        List<CommandPaletteItem> items = controller.search("", 12);
        CommandPaletteItem settings = items.stream()
            .filter(item -> "main.system.settings".equals(item.commandId()))
            .findFirst()
            .orElseThrow();

        assertTrue(settings.recent(), "Sidebar execution should sync into palette recent history");
    }

    @Test
    void contextSidebarRowExecutionShowsTwoTierBridgeTooltipAndSyncsPaletteRecent() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1729, 900);
        CommandPaletteController controller = getPrivateField(view, "commandPaletteController", CommandPaletteController.class);
        @SuppressWarnings("unchecked")
        Map<Object, ToggleButton> railButtons = (Map<Object, ToggleButton>) getPrivateField(view, "navigationRailButtons", Map.class);

        runOnFxThread(() -> {
            ToggleButton systemRail = railButtons.values().stream()
                .filter(btn -> {
                    Object raw = btn.getProperties().get("railDomain");
                    return raw != null && raw.toString().contains("SYSTEM");
                })
                .findFirst()
                .orElseThrow();
            systemRail.fire();
            view.applyCss();
            view.layout();
            return null;
        });

        @SuppressWarnings("unchecked")
        List<Button> contextButtons = getPrivateField(view, "contextSidebarDomainButtons", List.class);
        Button settingsButton = runOnFxThread(() -> findSidebarButton(contextButtons, "main.system.settings"));
        assertTrue(settingsButton != null && settingsButton.getOnAction() != null, "System domain context row must be present");

        Tooltip tooltip = settingsButton.getTooltip();
        assertTrue(tooltip != null && tooltip.getText().contains("rail:"), "Context row tooltip should include rail-domain hint");
        assertTrue(tooltip.getText().contains("ПКМ"), "Context row tooltip should explain sidebar/palette bridge");

        runOnFxThread(() -> {
            settingsButton.fire();
            return null;
        });

        List<CommandPaletteItem> items = controller.search("", 12);
        CommandPaletteItem settings = items.stream()
            .filter(item -> "main.system.settings".equals(item.commandId()))
            .findFirst()
            .orElseThrow();
        assertTrue(settings.recent(), "Context sidebar execution should sync into palette recent history");
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

    private static void invokeOpenPalette(LegacyMainView view, String query, boolean toggle) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod(
                "openCommandPaletteWithQuery",
                String.class,
                boolean.class
            );
            method.setAccessible(true);
            method.invoke(view, query, toggle);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot invoke openCommandPaletteWithQuery", ex);
        }
    }

    private static void invokeGlobalShortcut(LegacyMainView view, KeyEvent event) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod("handleGlobalShortcutKeyPressed", KeyEvent.class);
            method.setAccessible(true);
            method.invoke(view, event);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot invoke handleGlobalShortcutKeyPressed", ex);
        }
    }

    private static Node activeDialogFocusOwner(CommandPaletteDialog paletteDialog) {
        javafx.scene.control.Dialog<?> dialog = getPrivateField(paletteDialog, "activeDialog", javafx.scene.control.Dialog.class);
        if (dialog == null || dialog.getDialogPane() == null || dialog.getDialogPane().getScene() == null) {
            throw new IllegalStateException("Palette dialog scene is unavailable");
        }
        return dialog.getDialogPane().getScene().getFocusOwner();
    }

    private static KeyEvent tabKey(boolean shift) {
        return new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "\t",
            "\t",
            KeyCode.TAB,
            shift,
            false,
            false,
            false
        );
    }

    private static KeyEvent ctrlKey(KeyCode code, String text) {
        return new KeyEvent(
            KeyEvent.KEY_PRESSED,
            text == null ? "" : text,
            text == null ? "" : text,
            code,
            false,
            true,
            false,
            false
        );
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

    private static final class StubOverlayHandle implements OverlayDialogManager.OverlayHandle {
        private final AtomicInteger openCalls = new AtomicInteger();
        private final AtomicInteger closeCalls = new AtomicInteger();
        private boolean opened;
        private OverlayDialogManager.OverlayRequest lastRequest = OverlayDialogManager.OverlayRequest.empty();

        @Override
        public void open(javafx.stage.Window owner, OverlayDialogManager.OverlayRequest request) {
            opened = true;
            openCalls.incrementAndGet();
            lastRequest = request == null ? OverlayDialogManager.OverlayRequest.empty() : request;
        }

        @Override
        public boolean isOpen() {
            return opened;
        }

        @Override
        public void close() {
            opened = false;
            closeCalls.incrementAndGet();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
