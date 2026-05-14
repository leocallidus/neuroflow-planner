package com.example.neuroflowplanner.ui.mainview;

import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.layout.MainLayoutCoordinator;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarNavigationMainViewIntegrationTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS,
        UxConfigDefaults.CONFIG_UX_SIDEBAR_STATE_EXPANDED_SECTIONS,
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
    void quickActionsArePinnedAboveScrollAndRespectLimit() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_SIDEBAR_MAX_QUICK_ITEMS, "5");

        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1300, 860);
            created.applyCss();
            created.layout();
            return created;
        });

        VBox quickActionsBox = getPrivateField(view, "sidebarQuickActionsBox", VBox.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);

        assertNotNull(quickActionsBox);
        assertNotNull(scrollPane);
        assertNotNull(quickActionsBox.getParent());
        assertTrue(quickActionsBox.getParent().getParent() != scrollPane.getContent());

        long quickButtons = quickActionsBox.getChildren().stream()
            .filter(Button.class::isInstance)
            .count();
        assertEquals(5, quickButtons);
        assertEquals(0.0d, scrollPane.getVvalue(), 0.0001d);
    }

    @Test
    void activeRailDomainPersistenceSelectsContextSidebarDomainList() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "analytics");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1300, 860);
            created.applyCss();
            created.layout();
            return created;
        });

        Label domainHeader = getPrivateField(view, "contextSidebarDomainHeaderLabel", Label.class);
        VBox domainListBox = getPrivateField(view, "contextSidebarDomainListBox", VBox.class);
        assertNotNull(domainHeader);
        assertNotNull(domainListBox);
        assertTrue(domainHeader.getText().contains("Аналитика"));
        assertTrue(containsContextDomainButtonActionId(view, "main.analytics.dashboard"));
        assertFalse(containsContextDomainButtonActionId(view, "main.system.settings"));
    }

    @Test
    void filterKeyboardExecutesThroughUiActionRegistryAndEscClearsQuery() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1300, 860);
            created.applyCss();
            created.layout();
            return created;
        });

        UiActionRegistry actionRegistry = getPrivateField(view, "commandActionRegistry", UiActionRegistry.class);
        TextField filterField = getPrivateField(view, "sidebarFilterField", TextField.class);
        @SuppressWarnings("unchecked")
        List<Button> resultButtons = getPrivateField(view, "sidebarFilterResultButtons", List.class);
        AtomicReference<String> lastActionId = new AtomicReference<>();
        actionRegistry.addExecutionListener((actionId, result) -> lastActionId.set(actionId));

        runOnFxThread(() -> {
            filterField.setText("main.task.panel");
            return null;
        });
        assertFalse(resultButtons.isEmpty());
        runOnFxThread(() -> {
            invokeFilterKey(view, KeyCode.ENTER);
            return null;
        });
        assertEquals("main.task.panel", lastActionId.get());

        runOnFxThread(() -> {
            filterField.setText("main.task.panel");
            invokeFilterKey(view, KeyCode.ESCAPE);
            return null;
        });
        assertTrue(filterField.getText().isBlank());
    }

    @Test
    void navigationRailRendersDomainsAndArrowKeysSwitchSelection() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "work");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        @SuppressWarnings("unchecked")
        Map<SidebarRailDomain, ToggleButton> railButtons =
            getPrivateField(view, "navigationRailButtons", Map.class);
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);

        assertNotNull(railButtons);
        assertEquals(5, railButtons.size());
        assertTrue(railButtons.containsKey(SidebarRailDomain.WORK));
        assertTrue(railButtons.containsKey(SidebarRailDomain.RECENT));
        assertTrue(railButtons.containsKey(SidebarRailDomain.TOOLS));

        ToggleButton workButton = railButtons.get(SidebarRailDomain.WORK);
        ToggleButton toolsButton = railButtons.get(SidebarRailDomain.TOOLS);
        assertNotNull(workButton);
        assertNotNull(toolsButton);

        runOnFxThread(() -> {
            workButton.fireEvent(new KeyEvent(
                KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN, false, false, false, false
            ));
            return null;
        });

        assertEquals(SidebarRailDomain.RECENT, coordinator.navigationRailState().activeRailDomain());
        assertTrue(runOnFxThread(() -> railButtons.get(SidebarRailDomain.RECENT).isSelected()));
    }

    @Test
    void compactRailClickOpensContextSidebarAndRendersSelectedDomainList() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "work");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");

        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1280, 800); // COMPACT
            created.applyCss();
            created.layout();
            return created;
        });

        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        @SuppressWarnings("unchecked")
        Map<SidebarRailDomain, ToggleButton> railButtons =
            getPrivateField(view, "navigationRailButtons", Map.class);
        Label domainHeader = getPrivateField(view, "contextSidebarDomainHeaderLabel", Label.class);

        assertTrue(coordinator.state().leftPanelCollapsed(), "Compact mode should start with collapsed context sidebar");

        runOnFxThread(() -> {
            ToggleButton systemButton = railButtons.get(SidebarRailDomain.SYSTEM);
            assertNotNull(systemButton);
            systemButton.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        assertEquals(SidebarRailDomain.SYSTEM, coordinator.navigationRailState().activeRailDomain());
        assertFalse(coordinator.state().leftPanelCollapsed(), "User rail selection should open context sidebar on demand");
        assertTrue(domainHeader.getText().contains("Система"));
        assertTrue(containsContextDomainButtonActionId(view, "main.system.settings"));
        assertTrue(runOnFxThread(() -> railButtons.get(SidebarRailDomain.SYSTEM).isSelected()));
    }

    @Test
    void wideCollapsedRailClickReopensContextSidebarAndSwitchesDomain() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "work");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");

        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1729, 900); // WIDE
            created.applyCss();
            created.layout();
            return created;
        });

        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        @SuppressWarnings("unchecked")
        Map<SidebarRailDomain, ToggleButton> railButtons =
            getPrivateField(view, "navigationRailButtons", Map.class);
        Label domainHeader = getPrivateField(view, "contextSidebarDomainHeaderLabel", Label.class);

        runOnFxThread(() -> {
            invokePrivateNoArg(view, "toggleSidebar");
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(coordinator.state().leftPanelCollapsed(), "Sidebar should be collapsed after user toggle in WIDE");

        runOnFxThread(() -> {
            ToggleButton analyticsButton = railButtons.get(SidebarRailDomain.ANALYTICS);
            assertNotNull(analyticsButton);
            analyticsButton.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        assertFalse(coordinator.state().leftPanelCollapsed(), "Rail click should reopen collapsed context sidebar in WIDE");
        assertEquals(SidebarRailDomain.ANALYTICS, coordinator.navigationRailState().activeRailDomain());
        assertTrue(domainHeader.getText().contains("Аналитика"));
        assertTrue(containsContextDomainButtonActionId(view, "main.analytics.dashboard"));
        assertTrue(runOnFxThread(() -> railButtons.get(SidebarRailDomain.ANALYTICS).isSelected()));
    }

    @Test
    void activeRailDomainSurvivesResizeAndModeSwitchAndKeepsContextListConsistent() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "system");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "false");

        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1729, 900);
            created.applyCss();
            created.layout();
            return created;
        });
        runOnFxThread(() -> {
            view.applyCss();
            view.layout();
            return null;
        });

        assertActiveRailAndContextDomain(view, SidebarRailDomain.SYSTEM, "Система", "main.system.settings");

        resizeViewViaSceneReattach(view, 1280, 800);
        assertActiveRailAndContextDomain(view, SidebarRailDomain.SYSTEM, "Система", "main.system.settings");

        resizeViewViaSceneReattach(view, 1366, 768);
        assertActiveRailAndContextDomain(view, SidebarRailDomain.SYSTEM, "Система", "main.system.settings");

        resizeViewViaSceneReattach(view, 1729, 650);
        assertActiveRailAndContextDomain(view, SidebarRailDomain.SYSTEM, "Система", "main.system.settings");
    }

    @Test
    void twoTierSidebarTabCycleWrapsBetweenContextRowsAndRailControls() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        Button sidebarToggleBtn = getPrivateField(view, "sidebarToggleBtn", Button.class);
        @SuppressWarnings("unchecked")
        List<Button> contextButtons = getPrivateField(view, "contextSidebarDomainButtons", List.class);
        Button lastVisibleContextButton = contextButtons.stream()
            .filter(btn -> btn != null && btn.isVisible() && btn.isManaged())
            .reduce((a, b) -> b)
            .orElseThrow();

        runOnFxThread(() -> {
            lastVisibleContextButton.requestFocus();
            javafx.event.Event.fireEvent(lastVisibleContextButton, tabKey(false));
            return null;
        });
        runOnFxThread(() -> null);
        assertEquals(sidebarToggleBtn, runOnFxThread(() -> view.getScene().getFocusOwner()));

        runOnFxThread(() -> {
            sidebarToggleBtn.requestFocus();
            javafx.event.Event.fireEvent(sidebarToggleBtn, tabKey(true));
            return null;
        });
        runOnFxThread(() -> null);
        assertEquals(lastVisibleContextButton, runOnFxThread(() -> view.getScene().getFocusOwner()));
    }

    @Test
    void compactLowHeightKeepsLeftSidebarQuickActionsReadable() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1024, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        assertFalse(coordinator.leftPanelDisplayPolicy().aggressiveCompaction());
        assertFalse(coordinator.contextSidebarDisplayPolicy().aggressiveCompaction());

        Label quickTitle = getPrivateField(view, "sidebarQuickTitleLabel", Label.class);
        assertEquals("БЫСТРЫЙ ДОСТУП", quickTitle.getText());

        @SuppressWarnings("unchecked")
        List<Button> quickButtons = getPrivateField(view, "sidebarQuickActionButtons", List.class);
        runOnFxThread(() -> {
            long visibleCount = quickButtons.stream()
                .filter(button -> button != null && button.isVisible() && button.isManaged())
                .count();
            assertTrue(visibleCount > 0, "At least one quick action should remain visible at 1024x768");
            for (Button button : quickButtons) {
                if (button == null || !button.isVisible() || !button.isManaged()) {
                    continue;
                }
                assertEquals(ContentDisplay.LEFT, button.getContentDisplay());
                assertEquals(Pos.CENTER_LEFT, button.getAlignment());
            }
            return null;
        });
    }

    @Test
    void compactVeryLowRailOpenKeepsContextActionsVisibleAfterHeightRefresh() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN, "work");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED, "true");

        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1024, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        ScrollPane scrollPane = getPrivateField(view, "sidebarScrollPane", ScrollPane.class);
        Label quickTitle = getPrivateField(view, "sidebarQuickTitleLabel", Label.class);
        @SuppressWarnings("unchecked")
        List<Button> quickButtons = getPrivateField(view, "sidebarQuickActionButtons", List.class);
        @SuppressWarnings("unchecked")
        Map<SidebarRailDomain, ToggleButton> railButtons =
            getPrivateField(view, "navigationRailButtons", Map.class);

        assertTrue(coordinator.state().leftPanelCollapsed(), "Compact mode should start collapsed");

        runOnFxThread(() -> {
            ToggleButton systemButton = railButtons.get(SidebarRailDomain.SYSTEM);
            assertNotNull(systemButton);
            systemButton.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        assertFalse(coordinator.state().leftPanelCollapsed(), "Rail click should open compact context sidebar");
        assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Context scroll should stay visible after opening");
        assertTrue(containsContextDomainButtonActionId(view, "main.system.settings"));
        assertEquals("БЫСТРЫЙ ДОСТУП", quickTitle.getText());
        runOnFxThread(() -> {
            for (Button button : quickButtons) {
                if (button == null || !button.isVisible() || !button.isManaged()) {
                    continue;
                }
                assertEquals(ContentDisplay.LEFT, button.getContentDisplay());
                assertEquals(Pos.CENTER_LEFT, button.getAlignment());
                assertFalse(button.getText() == null || button.getText().isBlank());
            }
            return null;
        });

        runOnFxThread(() -> {
            invokePrivateNoArg(view, "scheduleAdaptiveHeightRefresh");
            return null;
        });
        runOnFxThread(() -> null);
        runOnFxThread(() -> null);

        assertFalse(coordinator.state().leftPanelCollapsed(), "Height refresh should not auto-close compact on-demand sidebar");
        assertTrue(scrollPane.isVisible() && scrollPane.isManaged(), "Context scroll should remain visible after height refresh");
        assertTrue(containsContextDomainButtonActionId(view, "main.system.settings"));
    }

    @Test
    void dailyReviewNavigationActionOpensInlineDialog() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        Runnable action = resolveSidebarAction(view, "main.analytics.dailyReview");
        assertNotNull(action);

        runOnFxThread(() -> {
            action.run();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        StackPane overlayContentHolder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        Node content = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));

        assertNotNull(content);
        assertTrue(content.getStyleClass().contains("daily-review-root"));
    }

    @Test
    void focusBlocksNavigationActionOpensInlineDialog() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        Runnable action = resolveSidebarAction(view, "main.analytics.focusBlocks");
        assertNotNull(action);

        runOnFxThread(() -> {
            action.run();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        StackPane overlayContentHolder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        Node content = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));

        assertNotNull(content);
        assertTrue(content.getStyleClass().contains("focus-blocks-root"));
    }

    @Test
    void planningQualityNavigationActionOpensInlineDialog() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        Runnable action = resolveSidebarAction(view, "main.analytics.planningQuality");
        assertNotNull(action);

        runOnFxThread(() -> {
            action.run();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        StackPane overlayContentHolder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        Node content = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));

        assertNotNull(content);
        assertTrue(content.getStyleClass().contains("planning-quality-root"));
    }

    @Test
    void focusBlocksDialogButtonsExposeAssistantAndOpenDailyReviewScenario() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        Runnable action = resolveSidebarAction(view, "main.analytics.focusBlocks");
        assertNotNull(action);

        runOnFxThread(() -> {
            action.run();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);

        StackPane overlayContentHolder = getPrivateField(view, "overlayContentHolder", StackPane.class);
        Node focusBlocksContent = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));
        assertNotNull(focusBlocksContent);
        Button dailyReviewButton = findButtonByText(focusBlocksContent, "Ежедневный обзор");
        Button assistantButton = findButtonByText(focusBlocksContent, "ИИ-Ассистент");
        assertNotNull(dailyReviewButton);
        assertNotNull(assistantButton);

        runOnFxThread(() -> {
            dailyReviewButton.fire();
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);
        Node dailyReviewContent = runOnFxThread(() -> overlayContentHolder.getChildren().isEmpty() ? null : overlayContentHolder.getChildren().get(0));
        assertNotNull(dailyReviewContent);
        assertTrue(dailyReviewContent.getStyleClass().contains("daily-review-root"));

        assertNotNull(assistantButton, "Focus blocks screen must expose a direct bridge to ИИ-Ассистент");
    }

    @Test
    void escCollapsesCollapsibleSidebarAndReopenRestoresPreviousFocus() throws Exception {
        Assumptions.assumeTrue(fxRuntimeReady, "JavaFX runtime unavailable in current environment");
        LegacyMainView view = runOnFxThread(() -> {
            LegacyMainView created = new LegacyMainView();
            new Scene(created, 1366, 768);
            created.applyCss();
            created.layout();
            return created;
        });

        @SuppressWarnings("unchecked")
        List<Button> contextButtons = getPrivateField(view, "contextSidebarDomainButtons", List.class);
        Button targetButton = contextButtons.stream()
            .filter(btn -> btn != null && btn.isVisible() && btn.isManaged())
            .findFirst()
            .orElseThrow();

        runOnFxThread(() -> {
            targetButton.requestFocus();
            javafx.event.Event.fireEvent(targetButton, escKey());
            view.applyCss();
            view.layout();
            return null;
        });
        assertTrue(getPrivateField(view, "isSidebarCollapsed", Boolean.class));

        runOnFxThread(() -> {
            invokePrivateNoArg(view, "toggleSidebar");
            view.applyCss();
            view.layout();
            return null;
        });
        runOnFxThread(() -> null);
        assertFalse(getPrivateField(view, "isSidebarCollapsed", Boolean.class));
        assertEquals(targetButton, runOnFxThread(() -> view.getScene().getFocusOwner()));
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

    private static boolean containsContextDomainButtonActionId(LegacyMainView view, String actionId) {
        String normalized = actionId == null ? null : actionId.trim().toLowerCase();
        if (normalized == null) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<Button> buttons = getPrivateField(view, "contextSidebarDomainButtons", List.class);
        for (Button button : buttons) {
            if (button == null || !button.isVisible() || !button.isManaged()) {
                continue;
            }
            Object raw = button.getProperties().get("sidebar.actionId");
            if (!(raw instanceof String candidate)) {
                continue;
            }
            if (normalized.equals(candidate.trim().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static Button findButtonByText(Node root, String text) {
        if (!(root instanceof Parent parent) || text == null) {
            return null;
        }
        return walk(parent).stream()
            .filter(Button.class::isInstance)
            .map(Button.class::cast)
            .filter(button -> text.equals(button.getText()))
            .findFirst()
            .orElse(null);
    }

    private static List<Node> walk(Parent root) {
        List<Node> out = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Node current = stack.pop();
            out.add(current);
            if (current instanceof Parent parent) {
                List<Node> children = parent.getChildrenUnmodifiable();
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return out;
    }

    private static void invokeFilterKey(LegacyMainView view, KeyCode code) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod("handleSidebarFilterKeyPressed", KeyEvent.class);
            method.setAccessible(true);
            KeyEvent event = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
            method.invoke(view, event);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot invoke handleSidebarFilterKeyPressed", ex);
        }
    }

    private static void invokePrivateNoArg(LegacyMainView view, String methodName) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(view);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot invoke method: " + methodName, ex);
        }
    }

    private static Runnable resolveSidebarAction(LegacyMainView view, String actionId) {
        try {
            Method method = LegacyMainView.class.getDeclaredMethod("resolveSidebarAction", String.class);
            method.setAccessible(true);
            return (Runnable) method.invoke(view, actionId);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot invoke resolveSidebarAction", ex);
        }
    }

    private static KeyEvent tabKey(boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "\t", "\t", KeyCode.TAB, shift, false, false, false);
    }

    private static KeyEvent escKey() {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ESCAPE, false, false, false, false);
    }

    private static void resizeViewViaSceneReattach(LegacyMainView view, double width, double height) throws Exception {
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

    private static void assertActiveRailAndContextDomain(
        LegacyMainView view,
        SidebarRailDomain expectedDomain,
        String expectedHeaderContains,
        String expectedActionId
    ) {
        MainLayoutCoordinator coordinator = getPrivateField(view, "mainLayoutCoordinator", MainLayoutCoordinator.class);
        Label domainHeader = getPrivateField(view, "contextSidebarDomainHeaderLabel", Label.class);
        @SuppressWarnings("unchecked")
        Map<SidebarRailDomain, ToggleButton> railButtons = getPrivateField(view, "navigationRailButtons", Map.class);

        assertEquals(expectedDomain, coordinator.navigationRailState().activeRailDomain());
        assertNotNull(domainHeader);
        assertTrue(domainHeader.getText().contains(expectedHeaderContains));
        assertTrue(containsContextDomainButtonActionId(view, expectedActionId));
        assertNotNull(railButtons.get(expectedDomain));
        assertTrue(railButtons.get(expectedDomain).isSelected(), "Active rail toggle must remain selected after resize");
    }

    private static <T> T getPrivateField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot access field: " + fieldName, ex);
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
