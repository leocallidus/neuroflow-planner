package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.InlineView;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineOverlayAdaptiveIntegrationTest {
    private static final double EPS = 0.51;
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
    void inlineOverlayAt1366x768UsesCompactWidthAndLowHeightContract() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        verifyResolutionContract(1366, 768);
    }

    @Test
    void inlineOverlayAt1280x800UsesVeryCompactWidthContract() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        verifyResolutionContract(1280, 800);
    }

    @Test
    void inlineOverlayAt1729x650UsesVeryLowHeightContract() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        verifyResolutionContract(1729, 650);
    }

    @Test
    void inlineOverlayAt1877x780UsesLowHeightContract() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        verifyResolutionContract(1877, 780);
    }

    private void verifyResolutionContract(double width, double height) throws Exception {
        LegacyMainView view = createView(width, height);
        runOnFxThread(() -> {
            view.openOrActivateTab("global:inline.a", new FakeInlineView("A"), longTitle("AI Assistant"));
            view.openOrActivateTab("global:inline.b", new FakeInlineView("B"), longTitle("Kanban Board"));
            view.openOrActivateTab("global:inline.c", new FakeInlineView("C"), longTitle("Calendar Planner"));
            view.openOrActivateTab("global:inline.d", new FakeInlineView("D"), longTitle("Smart Notes Editor"));
            return null;
        });
        runOnFxThread(() -> {
            view.applyCss();
            view.layout();
            return null;
        });

        VBox overlayContainer = getPrivateField(view, "overlayContainer", VBox.class);
        ScrollPane tabStripScroll = getPrivateField(view, "overlayTabStripScroll", ScrollPane.class);
        HBox tabStrip = getPrivateField(view, "overlayTabStrip", HBox.class);
        StackPane overlayHost = getPrivateField(view, "overlayHost", StackPane.class);

        boolean expectCompactWidth = overlayHost.getWidth() > 0.0 && overlayHost.getWidth() < 1380.0;
        boolean expectVeryCompactWidth = overlayHost.getWidth() > 0.0 && overlayHost.getWidth() < 1320.0;
        boolean expectLowHeight = overlayHost.getHeight() > 0.0 && overlayHost.getHeight() < 800.0;
        boolean expectVeryLowHeight = overlayHost.getHeight() > 0.0 && overlayHost.getHeight() < 700.0;

        assertEquals(expectCompactWidth, overlayContainer.getStyleClass().contains("inline-overlay-width-compact"));
        assertEquals(expectVeryCompactWidth, overlayContainer.getStyleClass().contains("inline-overlay-width-very-compact"));
        assertEquals(expectLowHeight, overlayContainer.getStyleClass().contains("inline-overlay-height-low"));
        assertEquals(expectVeryLowHeight, overlayContainer.getStyleClass().contains("inline-overlay-height-very-low"));

        assertTrue(tabStripScroll.isVisible() && tabStripScroll.isManaged(), "Tab strip must stay visible with opened tabs");
        assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, tabStripScroll.getHbarPolicy());
        assertEquals(expectCompactWidth || expectLowHeight, tabStrip.getStyleClass().contains("inline-tabs-strip-compact"));
        assertEquals(
            expectVeryCompactWidth || expectVeryLowHeight,
            tabStrip.getStyleClass().contains("inline-tabs-strip-very-compact")
        );

        List<Button> tabButtons = runOnFxThread(() -> resolveInlineTabButtons(tabStrip));
        assertTrue(tabButtons.size() >= 4, "Expected at least four inline tab buttons");
        double expectedTabButtonMaxWidth = expectVeryCompactWidth
            ? 148.0
            : (expectCompactWidth ? 176.0 : 220.0);
        for (Button tabButton : tabButtons) {
            assertEquals(OverrunStyle.ELLIPSIS, tabButton.getTextOverrun());
            assertTrue(tabButton.isVisible() && tabButton.isManaged() && !tabButton.isDisable());
            assertTrue(
                tabButton.getMaxWidth() <= expectedTabButtonMaxWidth + EPS,
                "Tab button max width must adapt to viewport contract"
            );
        }

        runOnFxThread(() -> {
            BoundsAsserts.assertNodeFitsWithinHost(overlayContainer, overlayHost, "inline overlay container");
            return null;
        });
    }

    private String longTitle(String base) {
        return base + " • Contract Validation Tab";
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

    private static List<Button> resolveInlineTabButtons(HBox tabStrip) {
        List<Button> buttons = new ArrayList<>();
        if (tabStrip == null) {
            return buttons;
        }
        for (Node chip : tabStrip.getChildren()) {
            if (!(chip instanceof Region region)) {
                continue;
            }
            collectInlineTabButtons(region, buttons);
        }
        return buttons;
    }

    private static void collectInlineTabButtons(Node root, List<Button> target) {
        if (root == null || target == null) {
            return;
        }
        if (root instanceof Button button) {
            Object role = button.getProperties().get("inlineOverlayTabRole");
            if ("switch".equals(role)) {
                target.add(button);
            }
            return;
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectInlineTabButtons(child, target);
            }
        }
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

        private FakeInlineView(String title) {
            this.title = title;
        }

        @Override
        public Node getContent() {
            return root;
        }

        @Override
        public String getTitle() {
            return title;
        }
    }

    private static final class BoundsAsserts {
        private BoundsAsserts() {
        }

        private static void assertNodeFitsWithinHost(Region node, Region host, String context) {
            assertTrue(node != null && host != null, "Bounds assert requires non-null nodes: " + context);
            assertFalse(node.getBoundsInParent().isEmpty(), "Node bounds should be measurable: " + context);
            assertTrue(
                node.getBoundsInParent().getMaxX() <= host.getWidth() + EPS,
                "Node maxX exceeds host width: " + context
            );
            assertTrue(
                node.getBoundsInParent().getMaxY() <= host.getHeight() + EPS,
                "Node maxY exceeds host height: " + context
            );
        }
    }
}
