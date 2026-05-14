package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.InlineView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineOverlayLifecycleIntegrationTest {
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
    void closeTabHonorsCanCloseAndInvokesCallbacksOnlyOnRealClose() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView sample = new FakeInlineView("A");
        sample.setCanClose(false);

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", sample, "Test A");
            return null;
        });

        boolean blocked = runOnFxThread(() -> view.closeTab("global:test.a"));
        assertFalse(blocked);
        assertEquals(0, sample.onCloseCalls());
        assertEquals(0, sample.onDisposeCalls());
        assertEquals(1, inlineTabCount(view));

        sample.setCanClose(true);
        boolean closed = runOnFxThread(() -> view.closeTab("global:test.a"));
        assertTrue(closed);
        assertEquals(1, sample.onCloseCalls());
        assertEquals(1, sample.onDisposeCalls());
        assertEquals(0, inlineTabCount(view));
        assertFalse(runOnFxThread(() -> overlayHost(view).isVisible()));
    }

    @Test
    void canCloseApplicationChecksAllTabsAndFocusesBlockingTab() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");
        second.setCanClose(false);

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "First");
            view.openOrActivateTab("global:test.b", second, "Second");
            view.activateTab("global:test.a");
            return null;
        });

        boolean canCloseApp = runOnFxThread(view::canCloseApplication);
        assertFalse(canCloseApp);
        assertEquals("global:test.b", runOnFxThread(() -> activeInlineTabId(view)));
    }

    @Test
    void viewCloseActionClosesExactlyItsOwnTab() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "First");
            view.openOrActivateTab("global:test.b", second, "Second");
            return null;
        });

        runOnFxThread(() -> {
            first.invokeCloseAction();
            return null;
        });

        assertEquals(1, inlineTabCount(view));
        assertTrue(inlineTabs(view).containsKey("global:test.b"));
        assertFalse(inlineTabs(view).containsKey("global:test.a"));
        assertEquals(1, first.onCloseCalls());
        assertEquals(1, first.onDisposeCalls());
        assertEquals(0, second.onCloseCalls());
        assertEquals(0, second.onDisposeCalls());
    }

    @Test
    void openingMultipleTabsKeepsExistingTabsAndSwitchingPreservesContent() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "First");
            view.openOrActivateTab("global:test.b", second, "Second");
            return null;
        });

        assertEquals(2, inlineTabCount(view), "Opening second tab must not close first tab");
        assertTrue(inlineTabs(view).containsKey("global:test.a"));
        assertTrue(inlineTabs(view).containsKey("global:test.b"));

        runOnFxThread(() -> {
            view.activateTab("global:test.a");
            return null;
        });
        assertSame(first.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));

        runOnFxThread(() -> {
            view.activateTab("global:test.b");
            return null;
        });
        assertSame(second.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));

        runOnFxThread(() -> {
            view.activateTab("global:test.a");
            return null;
        });
        assertSame(first.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));
        assertEquals(0, first.onDisposeCalls());
        assertEquals(0, second.onDisposeCalls());
    }

    @Test
    void openingSameTabIdReusesExistingTabAndDisposesReplacementView() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView replacement = new FakeInlineView("A2");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.same", first, "First");
            return null;
        });
        assertEquals(1, inlineTabCount(view));
        assertSame(first.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.same", replacement, "Replacement");
            return null;
        });

        assertEquals(1, inlineTabCount(view), "Same tabId must reuse existing tab");
        assertSame(first.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));
        assertEquals(0, first.onDisposeCalls());
        assertEquals(1, replacement.onDisposeCalls(), "Replacement view must be disposed on reuse");
    }

    @Test
    void closeActiveTabActivatesNeighborByTabOrder() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");
        FakeInlineView third = new FakeInlineView("C");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "A");
            view.openOrActivateTab("global:test.b", second, "B");
            view.openOrActivateTab("global:test.c", third, "C");
            view.activateTab("global:test.b");
            return null;
        });

        boolean firstClose = runOnFxThread(view::closeActiveTab);
        assertTrue(firstClose);
        assertEquals("global:test.c", runOnFxThread(() -> activeInlineTabId(view)));
        assertEquals(2, inlineTabCount(view));
        assertTrue(inlineTabs(view).containsKey("global:test.a"));
        assertTrue(inlineTabs(view).containsKey("global:test.c"));

        boolean secondClose = runOnFxThread(view::closeActiveTab);
        assertTrue(secondClose);
        assertEquals("global:test.a", runOnFxThread(() -> activeInlineTabId(view)));
        assertEquals(1, inlineTabCount(view));
    }

    @Test
    void taskPanelDockKeepsInlineTabsAndReopensOverlayWithoutReset() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "First");
            view.openOrActivateTab("global:test.b", second, "Second");
            return null;
        });

        Button toggleButton = getPrivateField(view, "inlineTaskDockToggleButton", Button.class);
        assertNotNull(toggleButton);
        runOnFxThread(() -> {
            toggleButton.fire();
            return null;
        });

        assertFalse(runOnFxThread(() -> overlayHost(view).isVisible()));
        assertEquals(2, inlineTabCount(view), "Hiding overlay from task panel must not close inline tabs");

        HBox dockTabs = getPrivateField(view, "inlineTaskDockTabStrip", HBox.class);
        Button reopenButton = runOnFxThread(() -> findDockTabButton(dockTabs, "global:test.a"));
        assertNotNull(reopenButton);
        runOnFxThread(() -> {
            reopenButton.fire();
            return null;
        });

        assertTrue(runOnFxThread(() -> overlayHost(view).isVisible()));
        assertEquals("global:test.a", runOnFxThread(() -> activeInlineTabId(view)));
        assertSame(first.getContent(), runOnFxThread(() -> activeOverlayContentNode(view)));
        assertEquals(2, inlineTabCount(view));
    }

    private LegacyMainView createView(double width, double height) throws Exception {
        LegacyMainView created = runOnFxThread(() -> {
            LegacyMainView instance = new LegacyMainView();
            new Scene(instance, width, height);
            instance.applyCss();
            instance.layout();
            return instance;
        });
        runOnFxThread(() -> {
            created.applyCss();
            created.layout();
            return null;
        });
        return created;
    }

    private static StackPane overlayHost(LegacyMainView view) throws Exception {
        return getPrivateField(view, "overlayHost", StackPane.class);
    }

    private static String activeInlineTabId(LegacyMainView view) throws Exception {
        return getPrivateField(view, "activeInlineTabId", String.class);
    }

    private static int inlineTabCount(LegacyMainView view) throws Exception {
        return inlineTabs(view).size();
    }

    private static Node activeOverlayContentNode(LegacyMainView view) throws Exception {
        StackPane holder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        if (holder.getChildren().isEmpty()) {
            return null;
        }
        return holder.getChildren().get(0);
    }

    private static Button findDockTabButton(HBox dockStrip, String tabId) {
        if (dockStrip == null || tabId == null) {
            return null;
        }
        return findButtonByRole(dockStrip, tabId, "task-dock-switch");
    }

    private static Button findButtonByRole(Node root, String tabId, String role) {
        if (root == null) {
            return null;
        }
        if (root instanceof Button button) {
            Object buttonTabId = button.getProperties().get("inlineOverlayTabId");
            Object buttonRole = button.getProperties().get("inlineOverlayTabRole");
            if (tabId.equals(buttonTabId) && role.equals(buttonRole)) {
                return button;
            }
            return null;
        }
        if (!(root instanceof javafx.scene.Parent parent)) {
            return null;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            Button found = findButtonByRole(child, tabId, role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> inlineTabs(LegacyMainView view) throws Exception {
        return getPrivateField(view, "inlineOverlayTabs", LinkedHashMap.class);
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        return type.cast(value);
    }

    private static <T> T runOnFxThread(FxSupplier<T> supplier) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return supplier.get();
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }

    private static final class FakeInlineView implements InlineView {
        private final VBox root = new VBox();
        private final String title;
        private Runnable closeAction;
        private boolean canClose = true;
        private int onCloseCalls;
        private int onDisposeCalls;

        private FakeInlineView(String title) {
            this.title = title;
            root.getChildren().add(new Label(title));
        }

        @Override
        public Node getContent() {
            return root;
        }

        @Override
        public boolean canClose() {
            return canClose;
        }

        @Override
        public Runnable getOnClose() {
            return () -> onCloseCalls++;
        }

        @Override
        public void setCloseAction(Runnable closeAction) {
            this.closeAction = closeAction;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public void onDispose() {
            onDisposeCalls++;
        }

        private void setCanClose(boolean canClose) {
            this.canClose = canClose;
        }

        private void invokeCloseAction() {
            if (closeAction != null) {
                closeAction.run();
            }
        }

        private int onCloseCalls() {
            return onCloseCalls;
        }

        private int onDisposeCalls() {
            return onDisposeCalls;
        }
    }
}
