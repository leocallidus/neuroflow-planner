package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.InlineView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineOverlayTabOperationsTest {
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
    void nodeBasedOpenOrActivateTabReusesByTabIdAndKeepsOrderStable() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        StackPane firstContent = new StackPane(new Label("first"));
        StackPane replacementContent = new StackPane(new Label("replacement"));

        runOnFxThread(() -> {
            view.openOrActivateTab("global:node.same", firstContent, null, "Node", false);
            view.openOrActivateTab("global:node.same", replacementContent, null, "Node 2", false);
            return null;
        });

        assertEquals(1, inlineTabCount(view));
        assertEquals(List.of("global:node.same"), runOnFxThread(() -> inlineTabOrder(view)));
        assertEquals("global:node.same", runOnFxThread(() -> activeInlineTabId(view)));
        assertEquals(firstContent, runOnFxThread(() -> activeOverlayContentNode(view)));
    }

    @Test
    void activateTabUnknownIdReturnsFalseAndDoesNotChangeActiveTab() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "A");
            view.openOrActivateTab("global:test.b", second, "B");
            view.activateTab("global:test.b");
            return null;
        });

        assertEquals("global:test.b", runOnFxThread(() -> activeInlineTabId(view)));
        boolean activated = runOnFxThread(() -> view.activateTab("global:test.missing"));
        assertFalse(activated);
        assertEquals("global:test.b", runOnFxThread(() -> activeInlineTabId(view)));
    }

    @Test
    void closeTabByIdRemovesOnlyRequestedTabAndKeepsOthersIntact() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView second = new FakeInlineView("B");
        FakeInlineView third = new FakeInlineView("C");

        runOnFxThread(() -> {
            view.openOrActivateTab("global:test.a", first, "A");
            view.openOrActivateTab("global:test.b", second, "B");
            view.openOrActivateTab("global:test.c", third, "C");
            return null;
        });

        boolean closed = runOnFxThread(() -> view.closeTab("global:test.b"));
        assertTrue(closed);
        assertEquals(2, inlineTabCount(view));
        assertFalse(runOnFxThread(() -> inlineTabs(view).containsKey("global:test.b")));
        assertTrue(runOnFxThread(() -> inlineTabs(view).containsKey("global:test.a")));
        assertTrue(runOnFxThread(() -> inlineTabs(view).containsKey("global:test.c")));
    }

    @Test
    void blankTabIdFallsBackToDefaultViewClassKeyAndReusesTab() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = createView(1366, 768);
        FakeInlineView first = new FakeInlineView("A");
        FakeInlineView replacement = new FakeInlineView("A2");

        runOnFxThread(() -> {
            view.openOrActivateTab("", first, "A");
            view.openOrActivateTab(" ", replacement, "A2");
            return null;
        });

        String expectedTabId = "inline:view:" + FakeInlineView.class.getName();
        assertEquals(1, inlineTabCount(view));
        assertEquals(expectedTabId, runOnFxThread(() -> activeInlineTabId(view)));
        assertEquals(1, replacement.onDisposeCalls(), "Replacement instance should be disposed on fallback-key reuse");
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

    private static int inlineTabCount(LegacyMainView view) throws Exception {
        return inlineTabs(view).size();
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Object> inlineTabs(LegacyMainView view) throws Exception {
        return getPrivateField(view, "inlineOverlayTabs", LinkedHashMap.class);
    }

    private static List<String> inlineTabOrder(LegacyMainView view) throws Exception {
        return new ArrayList<>(inlineTabs(view).keySet());
    }

    private static String activeInlineTabId(LegacyMainView view) throws Exception {
        return getPrivateField(view, "activeInlineTabId", String.class);
    }

    private static Node activeOverlayContentNode(LegacyMainView view) throws Exception {
        StackPane holder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        return holder.getChildren().isEmpty() ? null : holder.getChildren().get(0);
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
        public String getTitle() {
            return title;
        }

        @Override
        public void onDispose() {
            onDisposeCalls++;
        }

        private int onDisposeCalls() {
            return onDisposeCalls;
        }
    }
}
