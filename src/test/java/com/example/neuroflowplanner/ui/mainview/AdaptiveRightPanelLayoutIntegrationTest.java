package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.layout.MainLayoutShell;
import com.example.neuroflowplanner.ui.layout.MainLayoutCoordinator;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorTab;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTabHeightBand;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveRightPanelLayoutIntegrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES
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
    void wideBreakpointUsesPinnedRightPanel() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1720, 900);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        MainLayoutShell shell = getPrivateField(view, "mainLayoutShell", MainLayoutShell.class);

        assertSame(rightPanelWrapper, shell.rightContextDrawer());
        assertFalse(overlayHost.isVisible());
        assertTrue(view.getStyleClass().contains("right-panel-mode-pinned"));
    }

    @Test
    void compactBreakpointUsesOverlayOnDemand() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1280, 760);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        Button panelToggle = getPrivateField(view, "rightPanelToggleBtn", Button.class);

        assertNull(view.getRight());
        assertFalse(overlayHost.isVisible());
        assertTrue(quickToggle.isVisible());
        assertTrue(view.getStyleClass().contains("right-panel-mode-overlay"));

        runOnFxThread(() -> {
            quickToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(overlayHost.isVisible());
        assertTrue(rightPanelWrapper.isVisible());
        assertTrue(view.getStyleClass().contains("right-panel-overlay-open"));

        runOnFxThread(() -> {
            panelToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertFalse(overlayHost.isVisible());
        assertFalse(view.getStyleClass().contains("right-panel-overlay-open"));
    }

    @Test
    void normal1366x768RendersTabbedInspectorInCollapsibleMode() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        javafx.scene.layout.HBox tabStrip = getPrivateField(view, "rightPanelInspectorTabStrip", javafx.scene.layout.HBox.class);
        StackPane tabContentHost = getPrivateField(view, "rightPanelInspectorContentHost", StackPane.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Node> sectionNodes = getPrivateField(view, "rightPanelSectionNodes", Map.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-normal"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-collapsible"));
        assertFalse(overlayHost.isVisible());
        assertFalse(quickToggle.isVisible());
        assertTrue(tabStrip.isVisible(), "Inspector tab strip must be visible");
        assertTrue(tabContentHost.isVisible(), "Inspector content host must be visible");
        assertEquals(RightPanelInspectorTab.PROPERTIES, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("details"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.PROPERTIES), sectionNodes));
        assertTrue(tabScrolls.get(RightPanelInspectorTab.PROPERTIES).getHeight() > 0.0, "Properties tab must have usable height");
        assertPanelFitsViewport(rightPanelWrapper, view, "normal 1366x768");
        assertNoVerticalOverlap(tabStacks.get(RightPanelInspectorTab.PROPERTIES), "normal 1366x768/properties");
    }

    @Test
    void compact1280x800SupportsOverlayOpenToggleAndScrimCloseWithTabbedInspector() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1280, 800);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        StackPane overlayScrim = getPrivateField(view, "rightPanelOverlayScrim", StackPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        Button panelToggle = getPrivateField(view, "rightPanelToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Node> sectionNodes = getPrivateField(view, "rightPanelSectionNodes", Map.class);

        assertTrue(view.getStyleClass().contains("layout-breakpoint-compact"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-overlay"));
        assertTrue(quickToggle.isVisible());
        assertFalse(overlayHost.isVisible());
        assertFalse(overlayScrim.isVisible());

        runOnFxThread(() -> {
            quickToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(overlayHost.isVisible(), "Overlay must open on quick toggle");
        assertTrue(overlayScrim.isVisible(), "Scrim must be visible when overlay is open");
        assertTrue(view.getStyleClass().contains("right-panel-overlay-open"));
        assertEquals(RightPanelInspectorTab.PROPERTIES, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("details"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.PROPERTIES), sectionNodes));
        assertTrue(tabScrolls.get(RightPanelInspectorTab.PROPERTIES).getHeight() > 0.0, "Overlay properties tab must have usable height");
        assertPanelFitsViewport(rightPanelWrapper, overlayHost, "compact 1280x800 overlay/details");
        assertNoVerticalOverlap(tabStacks.get(RightPanelInspectorTab.PROPERTIES), "compact 1280x800 overlay/properties");

        runOnFxThread(() -> {
            clickScrim(overlayScrim);
            view.applyCss();
            view.layout();
            return null;
        });
        assertFalse(overlayHost.isVisible(), "Scrim click must close overlay");
        assertFalse(overlayScrim.isVisible());
        assertFalse(view.getStyleClass().contains("right-panel-overlay-open"));

        runOnFxThread(() -> {
            quickToggle.fire();
            view.applyCss();
            view.layout();
            panelToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertFalse(overlayHost.isVisible(), "Header toggle should close overlay when open");
        assertFalse(view.getStyleClass().contains("right-panel-overlay-open"));
    }

    @Test
    void compactTabSwitchChangesActiveInspectorTabAndContent() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1280, 800);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        Map<String, Node> sectionNodes = getPrivateField(view, "rightPanelSectionNodes", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);

        runOnFxThread(() -> {
            quickToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.PROPERTIES, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("details"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.PROPERTIES), sectionNodes));

        Button descriptionTabBtn = tabButtons.get(RightPanelInspectorTab.DESCRIPTION);
        Button analyticsTabBtn = tabButtons.get(RightPanelInspectorTab.ANALYTICS);
        assertNotNull(descriptionTabBtn);
        assertNotNull(analyticsTabBtn);

        runOnFxThread(() -> {
            descriptionTabBtn.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("description"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.DESCRIPTION), sectionNodes));

        runOnFxThread(() -> {
            analyticsTabBtn.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals("analytics", ConfigManager.getUxRightPanelInspectorActiveTab());
        assertEquals(List.of("ai", "path"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.ANALYTICS), sectionNodes));
        assertPanelFitsViewport(
            getPrivateField(view, "rightPanelWrapper", BorderPane.class),
            getPrivateField(view, "rightPanelOverlayHost", StackPane.class),
            "compact 1280x800 overlay/analytics"
        );
        assertNoVerticalOverlap(tabStacks.get(RightPanelInspectorTab.ANALYTICS), "compact 1280x800 overlay/analytics");
    }

    @Test
    void normalModeTabsExposeMappedSectionsWithoutStackMixing() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Node> sectionNodes = getPrivateField(view, "rightPanelSectionNodes", Map.class);

        Map<RightPanelInspectorTab, List<String>> expected = Map.of(
            RightPanelInspectorTab.PROPERTIES, List.of("details"),
            RightPanelInspectorTab.DESCRIPTION, List.of("description"),
            RightPanelInspectorTab.ANALYTICS, List.of("ai", "path")
        );

        for (RightPanelInspectorTab tab : RightPanelInspectorTab.baselineOrder()) {
            runOnFxThread(() -> {
                tabButtons.get(tab).fire();
                view.applyCss();
                view.layout();
                return null;
            });
            assertEquals(tab, coordinator.snapshot().rightInspectorActiveTab());
            assertEquals(expected.get(tab), resolveVisibleSectionIds(tabStacks.get(tab), sectionNodes));
            assertNoVerticalOverlap(tabStacks.get(tab), "normal 1366x768/" + tab.id());
        }
    }

    @Test
    void inspectorTabStripSupportsArrowAndCtrlTabKeyboardNavigation() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        javafx.scene.layout.HBox tabStrip = getPrivateField(view, "rightPanelInspectorTabStrip", javafx.scene.layout.HBox.class);

        runOnFxThread(() -> {
            Button propertiesTab = tabButtons.get(RightPanelInspectorTab.PROPERTIES);
            propertiesTab.requestFocus();
            fireKey(propertiesTab, KeyCode.RIGHT, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());

        runOnFxThread(() -> {
            tabStrip.requestFocus();
            fireKey(tabStrip, KeyCode.TAB, true, false);
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());

        runOnFxThread(() -> {
            tabStrip.requestFocus();
            fireKey(tabStrip, KeyCode.TAB, true, true);
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());
    }

    @Test
    void inspectorTabStripSupportsEnterSpaceAndTabFocusContracts() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        StackPane contentHost = getPrivateField(view, "rightPanelInspectorContentHost", StackPane.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);

        runOnFxThread(() -> {
            Button descriptionButton = tabButtons.get(RightPanelInspectorTab.DESCRIPTION);
            descriptionButton.requestFocus();
            fireKey(descriptionButton, KeyCode.SPACE, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());

        runOnFxThread(() -> {
            Button descriptionButton = tabButtons.get(RightPanelInspectorTab.DESCRIPTION);
            descriptionButton.requestFocus();
            fireKey(descriptionButton, KeyCode.TAB, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        Node focusAfterTab = runOnFxThread(() -> view.getScene() == null ? null : view.getScene().getFocusOwner());
        assertTrue(isNodeInside(focusAfterTab, contentHost), "Tab from tab strip must move focus into active tab content");
        assertTrue(
            isNodeInside(focusAfterTab, tabScrolls.get(RightPanelInspectorTab.DESCRIPTION)),
            "Focus after Tab should remain inside active description scroll"
        );

        runOnFxThread(() -> {
            ScrollPane activeScroll = tabScrolls.get(RightPanelInspectorTab.DESCRIPTION);
            activeScroll.requestFocus();
            fireKey(activeScroll, KeyCode.TAB, false, true);
            view.applyCss();
            view.layout();
            return null;
        });
        Node focusAfterShiftTab = runOnFxThread(() -> view.getScene() == null ? null : view.getScene().getFocusOwner());
        assertTrue(
            isNodeInside(focusAfterShiftTab, tabButtons.get(RightPanelInspectorTab.DESCRIPTION)),
            "Shift+Tab should return focus to active tab button"
        );

        runOnFxThread(() -> {
            Button analyticsButton = tabButtons.get(RightPanelInspectorTab.ANALYTICS);
            analyticsButton.requestFocus();
            fireKey(analyticsButton, KeyCode.ENTER, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
    }

    @Test
    void overlayEscClosesInspectorAndRestoresPreviousFocus() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1280, 800);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);

        runOnFxThread(() -> {
            quickToggle.requestFocus();
            quickToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(overlayHost.isVisible(), "Overlay must open before Escape close contract");

        runOnFxThread(() -> {
            Button propertiesButton = tabButtons.get(RightPanelInspectorTab.PROPERTIES);
            propertiesButton.requestFocus();
            fireKey(propertiesButton, KeyCode.ESCAPE, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> {
            view.applyCss();
            view.layout();
            return null;
        });

        assertFalse(overlayHost.isVisible(), "Escape must close right-panel overlay");
        Node restoredFocus = runOnFxThread(() -> view.getScene() == null ? null : view.getScene().getFocusOwner());
        assertSame(quickToggle, restoredFocus, "Focus should restore to pre-overlay trigger");
    }

    @Test
    void wideLowHeightUsesHeightAwareCompactionInsteadOfBottomClipping() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1877, 780);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        assertTrue(view.getStyleClass().contains("layout-breakpoint-wide"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-pinned"));
        ScrollPane activeScroll = tabScrolls.get(coordinator.snapshot().rightInspectorActiveTab());
        VBox activeStack = tabStacks.get(coordinator.snapshot().rightInspectorActiveTab());
        assertNotNull(activeScroll);
        assertNotNull(activeStack);
        assertTrue(activeScroll.getHeight() > 0.0, "Inspector tab scroll must remain interactive in wide low-height mode");
        assertPanelFitsViewport(rightPanelWrapper, view, "wide 1877x780");
        assertNoVerticalOverlap(activeStack, "wide 1877x780 active-tab");
    }

    @Test
    void wideVeryLowHeight1729x650KeepsRightPanelScrollableAndWithinViewport() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1729, 650);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        assertTrue(view.getStyleClass().contains("layout-breakpoint-wide"));
        assertTrue(view.getStyleClass().contains("right-panel-mode-pinned"));
        ScrollPane activeScroll = tabScrolls.get(coordinator.snapshot().rightInspectorActiveTab());
        VBox activeStack = tabStacks.get(coordinator.snapshot().rightInspectorActiveTab());
        assertNotNull(activeScroll);
        assertNotNull(activeStack);
        assertTrue(activeScroll.getHeight() > 0.0, "Inspector tab scroll must have usable height in 1729x650");
        assertPanelFitsViewport(rightPanelWrapper, view, "wide 1729x650");
        assertNoVerticalOverlap(activeStack, "wide 1729x650 active-tab");
    }

    @Test
    void lowHeightDescriptionTabUsesSummaryFirstAndSupportsExpand() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1877, 780);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        VBox descriptionSummaryBox = getPrivateField(view, "descriptionSummaryBox", VBox.class);
        VBox descriptionFullContentBox = getPrivateField(view, "descriptionFullContentBox", VBox.class);
        Button descriptionExpandBtn = getPrivateField(view, "descriptionCompactExpandBtn", Button.class);

        runOnFxThread(() -> {
            tabButtons.get(RightPanelInspectorTab.DESCRIPTION).fire();
            view.applyCss();
            view.layout();
            return null;
        });

        assertEquals(RightPanelInspectorTab.DESCRIPTION, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(RightPanelTabHeightBand.LOW_HEIGHT, coordinator.rightPanelInspectorDisplayPolicy().heightBand());
        assertTrue(descriptionSummaryBox.isVisible() && descriptionSummaryBox.isManaged(), "Description summary must be visible in low-height mode");
        assertFalse(descriptionFullContentBox.isVisible(), "Description full content must start collapsed in summary-first mode");

        runOnFxThread(() -> {
            descriptionExpandBtn.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(descriptionFullContentBox.isVisible() && descriptionFullContentBox.isManaged(), "Description full content must expand on demand");
    }

    @Test
    void liveResizeBetweenLowAndVeryLowKeepsRightInspectorInteractive() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1877, 1200);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);

        resizeViewViaSceneReattach(view, 1877, 780);
        assertEquals(RightPanelTabHeightBand.LOW_HEIGHT, coordinator.rightPanelInspectorDisplayPolicy().heightBand());
        ScrollPane lowHeightScroll = tabScrolls.get(coordinator.snapshot().rightInspectorActiveTab());
        assertNotNull(lowHeightScroll);
        assertTrue(lowHeightScroll.getHeight() > 0.0, "Inspector tab scroll must remain usable after resize to 1877x780");
        assertPanelFitsViewport(rightPanelWrapper, view, "live resize 1877x780 right panel");

        resizeViewViaSceneReattach(view, 1729, 650);
        assertEquals(RightPanelTabHeightBand.VERY_LOW_HEIGHT, coordinator.rightPanelInspectorDisplayPolicy().heightBand());
        ScrollPane veryLowHeightScroll = tabScrolls.get(coordinator.snapshot().rightInspectorActiveTab());
        assertNotNull(veryLowHeightScroll);
        assertTrue(veryLowHeightScroll.getHeight() > 0.0, "Inspector tab scroll must remain usable after resize to 1729x650");
        assertPanelFitsViewport(rightPanelWrapper, view, "live resize 1729x650 right panel");
    }

    @Test
    void activeInspectorTabPersistsAcrossResizeAndModeSwitch() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        configureAdaptiveDefaults();

        LegacyMainView view = createView(1366, 768);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        BorderPane rightPanelWrapper = getPrivateField(view, "rightPanelWrapper", BorderPane.class);
        StackPane overlayHost = getPrivateField(view, "rightPanelOverlayHost", StackPane.class);
        StackPane overlayScrim = getPrivateField(view, "rightPanelOverlayScrim", StackPane.class);
        Button quickToggle = getPrivateField(view, "rightPanelQuickToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, Button> tabButtons = getPrivateField(view, "rightPanelInspectorTabButtons", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, VBox> tabStacks = getPrivateField(view, "rightPanelInspectorTabStacks", Map.class);
        @SuppressWarnings("unchecked")
        Map<RightPanelInspectorTab, ScrollPane> tabScrolls = getPrivateField(view, "rightPanelInspectorTabScrolls", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Node> sectionNodes = getPrivateField(view, "rightPanelSectionNodes", Map.class);

        runOnFxThread(() -> {
            tabButtons.get(RightPanelInspectorTab.ANALYTICS).fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("ai", "path"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.ANALYTICS), sectionNodes));

        resizeViewViaSceneReattach(view, 1280, 800);
        assertTrue(view.getStyleClass().contains("right-panel-mode-overlay"));
        assertFalse(overlayHost.isVisible(), "Compact mode should start with collapsed overlay");

        runOnFxThread(() -> {
            quickToggle.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(overlayHost.isVisible(), "Quick toggle should open compact overlay");
        assertTrue(overlayScrim.isVisible(), "Compact overlay should show scrim");
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("ai", "path"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.ANALYTICS), sectionNodes));
        assertTrue(tabScrolls.get(RightPanelInspectorTab.ANALYTICS).getHeight() > 0.0);
        assertPanelFitsViewport(rightPanelWrapper, overlayHost, "resize mode switch compact overlay/analytics");
        assertNoVerticalOverlap(tabStacks.get(RightPanelInspectorTab.ANALYTICS), "resize mode switch compact overlay/analytics");

        runOnFxThread(() -> {
            fireKey(tabButtons.get(RightPanelInspectorTab.ANALYTICS), KeyCode.ESCAPE, false, false);
            view.applyCss();
            view.layout();
            return null;
        });
        assertFalse(overlayHost.isVisible(), "Escape should close overlay during mode-switch regression scenario");

        resizeViewViaSceneReattach(view, 1729, 650);
        assertTrue(view.getStyleClass().contains("right-panel-mode-pinned"));
        assertEquals(RightPanelInspectorTab.ANALYTICS, coordinator.snapshot().rightInspectorActiveTab());
        assertEquals(List.of("ai", "path"), resolveVisibleSectionIds(tabStacks.get(RightPanelInspectorTab.ANALYTICS), sectionNodes));
        assertTrue(tabScrolls.get(RightPanelInspectorTab.ANALYTICS).getHeight() > 0.0);
        assertPanelFitsViewport(rightPanelWrapper, view, "resize mode switch wide 1729x650/analytics");
        assertNoVerticalOverlap(tabStacks.get(RightPanelInspectorTab.ANALYTICS), "resize mode switch wide 1729x650/analytics");
    }

    private void configureAdaptiveDefaults() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_COMFORTABLE);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED, "false");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS, "details,description,ai,path");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT);
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES, UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES_DEFAULT);
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

    private static void clickScrim(StackPane scrim) {
        MouseEvent click = new MouseEvent(
            MouseEvent.MOUSE_CLICKED,
            8, 8, 8, 8,
            MouseButton.PRIMARY,
            1,
            false, false, false, false,
            true, false, false,
            false, false, false,
            new PickResult(scrim, 8, 8)
        );
        scrim.fireEvent(click);
    }

    private static void fireKey(Node target, KeyCode code, boolean control, boolean shift) {
        KeyEvent keyPress = new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            code,
            shift,
            control,
            false,
            false
        );
        target.fireEvent(keyPress);
    }

    private static boolean isNodeInside(Node node, Node container) {
        if (node == null || container == null) {
            return false;
        }
        Node cursor = node;
        while (cursor != null) {
            if (cursor == container) {
                return true;
            }
            cursor = cursor.getParent();
        }
        return false;
    }

    private static List<String> resolveVisibleSectionIds(VBox sectionStack, Map<String, Node> sectionNodes) {
        Map<Node, String> idByNode = new LinkedHashMap<>();
        for (Map.Entry<String, Node> entry : sectionNodes.entrySet()) {
            idByNode.put(entry.getValue(), entry.getKey());
        }
        List<String> ids = new ArrayList<>();
        for (Node child : sectionStack.getChildren()) {
            if (!child.isVisible() || !child.isManaged()) {
                continue;
            }
            String id = idByNode.get(child);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static void assertNoVerticalOverlap(VBox stack, String context) {
        List<Node> visibleChildren = new ArrayList<>();
        for (Node child : stack.getChildren()) {
            if (child.isVisible() && child.isManaged()) {
                visibleChildren.add(child);
            }
        }
        for (int i = 0; i < visibleChildren.size() - 1; i++) {
            Node upper = visibleChildren.get(i);
            Node lower = visibleChildren.get(i + 1);
            Bounds upperBounds = upper.getBoundsInParent();
            Bounds lowerBounds = lower.getBoundsInParent();
            double gap = lowerBounds.getMinY() - upperBounds.getMaxY();
            assertTrue(
                gap >= -0.5d,
                context + " overlap detected between sections at indexes " + i + " and " + (i + 1)
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
