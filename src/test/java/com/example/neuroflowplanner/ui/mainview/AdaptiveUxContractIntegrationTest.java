package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.layout.MainLayoutCoordinator;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelSidebarMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavSurfaceHeightBand;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveUxContractIntegrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN,
        UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED
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
    void normal1366x768KeepsTopActionsAccessibleWithoutFullscreen() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        VBox quickActionsBox = getPrivateField(view, "sidebarQuickActionsBox", VBox.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-normal"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-collapsible"));
        assertNotNull(quickActionsBox);
        assertNotNull(scrollPane);
        assertTrue(quickActionsBox.isVisible());
        assertTrue(quickActionsBox.getParent().getParent() != scrollPane.getContent());
        long quickButtons = quickActionsBox.getChildren().stream().filter(Button.class::isInstance).count();
        assertTrue(quickButtons >= 5, "Top actions should remain accessible without fullscreen");
        assertFalse(quickToggle.isVisible());
        assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Sidebar scroll must remain active at 1366x768");
        assertPanelFitsViewport(getPrivateField(view, "sidebarShellBox", VBox.class), view, "normal 1366x768 left sidebar");
        assertNoVerticalOverlap(resolveVisibleContextSidebarDomainRows(view), "normal 1366x768 context sidebar rows");
        assertCoreActionsAccessibleNearTop(view, "normal 1366x768");
    }

    @Test
    void compact1280x800UsesOverlayAndAutoCollapsedRightPanel() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1280, 800);
        VBox quickActionsBox = getPrivateField(view, "sidebarQuickActionsBox", VBox.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        VBox sidebarShell = getPrivateField(view, "sidebarShellBox", VBox.class);
        VBox quickZone = getPrivateField(view, "sidebarPinnedQuickZone", VBox.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-compact"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-overlay"));
        assertTrue(view.getStyleClass().contains("left-panel-mode-overlay"));
        assertTrue(quickToggle.isVisible(), "Compact mode should expose quick drawer toggle");
        assertFalse(overlayHost.isVisible(), "Compact mode starts with auto-collapsed right panel");
        assertTrue(quickActionsBox.isVisible());
        long quickButtons = quickActionsBox.getChildren().stream().filter(Button.class::isInstance).count();
        assertTrue(quickButtons >= 5, "Top actions must remain reachable in compact mode");
        assertTrue(quickZone.isVisible(), "Pinned quick zone must remain visible in compact mode");
        assertPanelFitsViewport(getPrivateField(view, "sidebarTwoTierRoot", javafx.scene.layout.HBox.class), view, "compact 1280x800 two-tier root");
        VBox navigationRail = getPrivateField(view, "navigationRailBox", VBox.class);
        assertTrue(navigationRail.isVisible() && navigationRail.isManaged(), "Compact mode must keep navigation rail visible");
    }

    @Test
    void wide1729x650KeepsSidebarInteractiveWithoutBottomClippingOrDomainListOverlap() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1729, 650);
        VBox sidebarShell = getPrivateField(view, "sidebarShellBox", VBox.class);
        VBox quickZone = getPrivateField(view, "sidebarPinnedQuickZone", VBox.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        Label domainHeader = getPrivateField(view, "contextSidebarDomainHeaderLabel", Label.class);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-wide"));
        assertTrue(view.getStyleClass().contains("left-panel-mode-collapsible"));
        assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Sidebar scroll must remain active at 1729x650");
        assertTrue(quickZone.isVisible(), "Pinned quick zone must stay visible at very low height");
        assertEquals(NavSurfaceHeightBand.VERY_LOW_HEIGHT, coordinator.navSurfaceHeightBand());
        assertTrue(domainHeader.getText().contains("Рабоч"));
        assertPanelFitsViewport(sidebarShell, view, "wide 1729x650 left sidebar");
        assertNoVerticalOverlap(resolveVisibleContextSidebarDomainRows(view), "wide 1729x650 context sidebar rows");
        assertCoreActionsAccessibleNearTop(view, "wide 1729x650");
        assertNoCompetingDomainRowsVisible(view, List.of("Дашборд", "Настройки"), "wide 1729x650");
    }

    @Test
    void wide1877x780AppliesHeightAwareCompactionWithoutBottomClipping() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1877, 780);
        VBox sidebarShell = getPrivateField(view, "sidebarShellBox", VBox.class);
        VBox quickZone = getPrivateField(view, "sidebarPinnedQuickZone", VBox.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-wide"));
        assertTrue(view.getStyleClass().contains("left-panel-mode-pinned"));
        assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Sidebar scroll must remain active at 1877x780");
        assertEquals(LeftPanelSidebarMode.PINNED, coordinator.leftPanelDisplayPolicy().sidebarMode());
        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, coordinator.navSurfaceHeightBand());
        assertTrue(quickZone.getStyleClass().contains("nav-surface-height-compact"));
        assertPanelFitsViewport(sidebarShell, view, "wide 1877x780 left sidebar");
        assertPanelFitsViewport(getPrivateField(view, "sidebarTwoTierRoot", javafx.scene.layout.HBox.class), view, "wide 1877x780 two-tier root");
        assertNoVerticalOverlap(resolveVisibleContextSidebarDomainRows(view), "wide 1877x780 context sidebar rows");
        assertNoVerticalOverlap(resolveVisibleSidebarShellBlocks(view), "wide 1877x780 sidebar shell blocks");
        assertCoreActionsAccessibleNearTop(view, "wide 1877x780");
    }

    @Test
    void liveHeightResizeReattachesSceneWithoutSidebarOverlapOrBottomClipping() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1877, 1200);
        resizeViewViaSceneReattach(view, 1877, 780);

        VBox sidebarShellAfterLow = getPrivateField(view, "sidebarShellBox", VBox.class);
        VBox quickZoneAfterLow = getPrivateField(view, "sidebarPinnedQuickZone", VBox.class);
        ScrollPane scrollAfterLow = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        MainLayoutCoordinator coordinatorAfterLow = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);

        assertEquals(NavSurfaceHeightBand.LOW_HEIGHT, coordinatorAfterLow.navSurfaceHeightBand());
        assertTrue(quickZoneAfterLow.getStyleClass().contains("nav-surface-height-compact"));
        assertTrue(scrollAfterLow.isVisible() && scrollAfterLow.isManaged(), "Sidebar scroll must stay usable after resize to 1877x780");
        assertPanelFitsViewport(sidebarShellAfterLow, view, "live resize 1877x780 sidebar shell");
        assertPanelFitsViewport(getPrivateField(view, "sidebarTwoTierRoot", javafx.scene.layout.HBox.class), view, "live resize 1877x780 two-tier root");
        assertNoVerticalOverlap(resolveVisibleSidebarShellBlocks(view), "live resize 1877x780 sidebar shell blocks");

        resizeViewViaSceneReattach(view, 1729, 650);

        VBox sidebarShellAfterVeryLow = getPrivateField(view, "sidebarShellBox", VBox.class);
        ScrollPane scrollAfterVeryLow = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        MainLayoutCoordinator coordinatorAfterVeryLow = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        Label headerMeta = getPrivateField(view, "contextSidebarDomainHeaderMetaLabel", Label.class);
        Label footerVersion = getPrivateField(view, "contextSidebarFooterVersionLabel", Label.class);
        VBox quickActionsBox = getPrivateField(view, "sidebarQuickActionsBox", VBox.class);

        assertEquals(NavSurfaceHeightBand.VERY_LOW_HEIGHT, coordinatorAfterVeryLow.navSurfaceHeightBand());
        assertTrue(scrollAfterVeryLow.isVisible() && scrollAfterVeryLow.isManaged(), "Sidebar scroll must stay usable after resize to 1729x650");
        long visibleQuickButtons = quickActionsBox.getChildren().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(btn -> btn.isVisible() && btn.isManaged())
            .count();
        assertTrue(visibleQuickButtons >= 3, "Very low height must keep at least 3 visible quick actions");
        assertFalse(headerMeta.isManaged(), "Very low height should collapse secondary domain meta line");
        assertFalse(footerVersion.isManaged(), "Very low height should hide secondary footer version");
        assertPanelFitsViewport(sidebarShellAfterVeryLow, view, "live resize 1729x650 sidebar shell");
        assertPanelFitsViewport(getPrivateField(view, "sidebarTwoTierRoot", javafx.scene.layout.HBox.class), view, "live resize 1729x650 two-tier root");
        assertNoVerticalOverlap(resolveVisibleSidebarShellBlocks(view), "live resize 1729x650 sidebar shell blocks");
        assertNoVerticalOverlap(resolveVisibleContextSidebarDomainRows(view), "live resize 1729x650 context sidebar rows");
    }

    private void configureAdaptiveDefaults() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS, "8");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "work");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");
    }

    private LegacyMainView createView(double width, double height) throws Exception {
        LegacyMainView created = runOnFxThread(() -> {
            LegacyMainView view = new LegacyMainView();
            new Scene(view, width, height);
            view.applyCss();
            view.layout();
            return view;
        });
        // Drain deferred resize/layout refresh scheduled via Platform.runLater(...) and apply final geometry.
        runOnFxThread(() -> {
            created.applyCss();
            created.layout();
            return null;
        });
        return created;
    }

    private void resizeViewViaSceneReattach(LegacyMainView view, double width, double height) throws Exception {
        runOnFxThread(() -> {
            Scene currentScene = view.getScene();
            if (currentScene != null && currentScene.getRoot() == view) {
                currentScene.setRoot(new StackPane());
            }
            new Scene(view, width, height);
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> {
            view.applyCss();
            view.layout();
            return null;
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

    private static List<Region> resolveVisibleContextSidebarDomainRows(LegacyMainView view) {
        VBox domainList = getPrivateField(view, "contextSidebarDomainListBox", VBox.class);
        return domainList.getChildren().stream()
            .filter(node -> node instanceof Region region && region.isVisible() && region.isManaged())
            .map(node -> (Region) node)
            .sorted((a, b) -> Double.compare(a.getBoundsInParent().getMinY(), b.getBoundsInParent().getMinY()))
            .toList();
    }

    private static List<Region> resolveVisibleSidebarShellBlocks(LegacyMainView view) {
        VBox shell = getPrivateField(view, "sidebarShellBox", VBox.class);
        return shell.getChildren().stream()
            .filter(node -> node instanceof Region region && region.isVisible() && region.isManaged())
            .map(node -> (Region) node)
            .sorted((a, b) -> Double.compare(a.getBoundsInParent().getMinY(), b.getBoundsInParent().getMinY()))
            .toList();
    }

    private static void assertCoreActionsAccessibleNearTop(LegacyMainView view, String context) {
        VBox quickActionsBox = getPrivateField(view, "sidebarQuickActionsBox", VBox.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        VBox domainList = getPrivateField(view, "contextSidebarDomainListBox", VBox.class);

        long quickButtons = quickActionsBox.getChildren().stream().filter(Button.class::isInstance).count();
        assertTrue(quickButtons >= 3, context + " must keep core quick actions visible");
        assertEquals(0.0d, scrollPane.getVvalue(), 0.0001d, context + " should not require initial scroll");
        assertNotNull(domainList, context + " must render context domain list");
        List<Region> rows = resolveVisibleContextSidebarDomainRows(view);
        assertFalse(rows.isEmpty(), context + " selected domain list must contain visible rows");

        double viewportHeight = scrollPane.getViewportBounds() == null ? 0.0 : scrollPane.getViewportBounds().getHeight();
        double firstRowOffset = rows.get(0).getBoundsInParent().getMinY();
        double visibilityThreshold = viewportHeight > 0.0
            ? Math.max(220.0, viewportHeight + 140.0)
            : 420.0;
        assertTrue(
            firstRowOffset <= visibilityThreshold,
            context + " first domain row should be reachable without long scroll"
                + " (offset=" + firstRowOffset + ", viewport=" + viewportHeight + ")"
        );
    }

    private static void assertNoCompetingDomainRowsVisible(LegacyMainView view, List<String> fragments, String context) {
        VBox domainList = getPrivateField(view, "contextSidebarDomainListBox", VBox.class);
        String joined = domainList.getChildren().stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .map(Button::getText)
            .reduce("", (a, b) -> a + "\n" + (b == null ? "" : b));
        for (String fragment : fragments) {
            assertFalse(joined.contains(fragment), context + " should not contain competing domain row: " + fragment);
        }
    }

    private static void assertNoVerticalOverlap(List<? extends Region> cards, String context) {
        for (int i = 0; i < cards.size() - 1; i++) {
            Region upper = cards.get(i);
            Region lower = cards.get(i + 1);
            Bounds upperBounds = upper.getBoundsInParent();
            Bounds lowerBounds = lower.getBoundsInParent();
            double gap = lowerBounds.getMinY() - upperBounds.getMaxY();
            assertTrue(
                gap >= -0.5d,
                context + " overlap detected between sidebar groups " + i + " and " + (i + 1)
                    + " (gap=" + gap + ", upper=" + upperBounds + ", lower=" + lowerBounds + ")"
            );
        }
    }

    private static void assertPanelFitsViewport(Region panel, Region viewport, String context) {
        Bounds panelBounds = panel.getBoundsInParent();
        Bounds viewportBounds = viewport.getBoundsInLocal();
        double panelHeight = panelBounds.getHeight();
        double viewportHeight = viewportBounds.getHeight();
        assertTrue(panelHeight > 0.0, context + " panel height must be positive");
        assertTrue(viewportHeight > 0.0, context + " viewport height must be positive");
        assertTrue(
            panelHeight <= viewportHeight + 1.0d,
            context + " panel height exceeds viewport (panel=" + panelHeight + ", viewport=" + viewportHeight + ")"
        );
        assertTrue(
            panelBounds.getMinY() >= -1.0d && panelBounds.getMaxY() <= viewportHeight + 1.0d,
            context + " panel bounds exceed viewport vertically (panel=" + panelBounds + ", viewportHeight=" + viewportHeight + ")"
        );
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
