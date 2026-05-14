package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RightPanelLayoutServiceTest {
    private static final Field PROPERTIES_FIELD = resolvePropertiesField();
    private static final List<String> CONFIG_KEYS = List.of(
        UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB,
        UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES
    );

    private final Map<String, String> snapshot = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        snapshotConfig();
    }

    @AfterEach
    void tearDown() {
        restoreConfig();
    }

    @Test
    void resolveModeMatchesAdaptiveShellContract() {
        RightPanelLayoutService service = new RightPanelLayoutService();

        assertEquals(UiRightContextMode.OVERLAY, service.resolveMode(UiLayoutBreakpoint.COMPACT));
        assertEquals(UiRightContextMode.COLLAPSIBLE, service.resolveMode(UiLayoutBreakpoint.NORMAL));
        assertEquals(UiRightContextMode.PINNED, service.resolveMode(UiLayoutBreakpoint.WIDE));
    }

    @Test
    void compactPolicyUsesSegmentedOneHeavySectionLayout() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelLayoutState state = service.defaultState()
            .withActiveTab(RightPanelTab.AI)
            .withExpandedSection(RightPanelLayoutService.SECTION_AI, true)
            .withExpandedSection(RightPanelLayoutService.SECTION_DESCRIPTION, true);

        List<RightPanelSection> visible = service.resolveVisibleSections(state, UiLayoutBreakpoint.COMPACT);
        RightPanelDisplayPolicy policy = service.resolveCompactionPlan(state, UiLayoutBreakpoint.COMPACT);

        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_AI
        ), visible.stream().map(RightPanelSection::id).toList());

        assertEquals(UiRightContextMode.OVERLAY, policy.mode());
        assertTrue(policy.overlayOnDemand());
        assertTrue(policy.stickyHeader());
        assertTrue(policy.segmentedNavigation());
        assertEquals(1, policy.maxHeavySectionsVisible());
        assertTrue(policy.isSectionVisible(RightPanelLayoutService.SECTION_DETAILS));
        assertTrue(policy.isSectionVisible(RightPanelLayoutService.SECTION_AI));
        assertFalse(policy.isSectionVisible(RightPanelLayoutService.SECTION_DESCRIPTION));
        assertFalse(policy.isSectionVisible(RightPanelLayoutService.SECTION_PATH));
        assertTrue(policy.isSectionExpanded(RightPanelLayoutService.SECTION_DETAILS));
        assertTrue(policy.isSectionExpanded(RightPanelLayoutService.SECTION_AI));
        assertTrue(policy.demotedSectionIds().contains(RightPanelLayoutService.SECTION_DESCRIPTION));
        assertTrue(policy.demotedSectionIds().contains(RightPanelLayoutService.SECTION_PATH));
    }

    @Test
    void normalPolicyKeepsAllSectionsVisibleAndDemotesOnlyTertiaryContent() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelLayoutState state = service.defaultState()
            .withActiveTab(RightPanelTab.PATH)
            .withExpandedSection(RightPanelLayoutService.SECTION_DESCRIPTION, true)
            .withExpandedSection(RightPanelLayoutService.SECTION_AI, true)
            .withExpandedSection(RightPanelLayoutService.SECTION_PATH, false);

        RightPanelDisplayPolicy policy = service.resolveCompactionPlan(state, UiLayoutBreakpoint.NORMAL);

        assertEquals(UiRightContextMode.COLLAPSIBLE, policy.mode());
        assertFalse(policy.overlayOnDemand());
        assertFalse(policy.segmentedNavigation());
        assertEquals(Integer.MAX_VALUE, policy.maxHeavySectionsVisible());
        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_DESCRIPTION,
            RightPanelLayoutService.SECTION_AI,
            RightPanelLayoutService.SECTION_PATH
        ), policy.visibleSections().stream().map(RightPanelSection::id).toList());
        assertTrue(policy.isSectionExpanded(RightPanelLayoutService.SECTION_DETAILS));
        assertTrue(policy.isSectionExpanded(RightPanelLayoutService.SECTION_DESCRIPTION));
        assertTrue(policy.isSectionExpanded(RightPanelLayoutService.SECTION_AI));
        assertFalse(policy.isSectionExpanded(RightPanelLayoutService.SECTION_PATH));
        assertEquals(Set.of(
            RightPanelLayoutService.SECTION_AI,
            RightPanelLayoutService.SECTION_PATH
        ), policy.demotedSectionIds());
    }

    @Test
    void widePolicyRemovesDemotionAndUsesPinnedMode() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelDisplayPolicy policy = service.resolveCompactionPlan(service.defaultState(), UiLayoutBreakpoint.WIDE);

        assertEquals(UiRightContextMode.PINNED, policy.mode());
        assertFalse(policy.overlayOnDemand());
        assertFalse(policy.segmentedNavigation());
        assertFalse(policy.stickyHeader());
        assertEquals(Integer.MAX_VALUE, policy.maxHeavySectionsVisible());
        assertTrue(policy.demotedSectionIds().isEmpty());
        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_DESCRIPTION,
            RightPanelLayoutService.SECTION_AI,
            RightPanelLayoutService.SECTION_PATH
        ), policy.visibleSections().stream().map(RightPanelSection::id).toList());
    }

    @Test
    void compactVisibleSectionsFollowPrimaryFirstThenActiveTabPriority() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelLayoutState base = service.defaultState();

        List<String> detailsTab = service.resolveCompactionPlan(base.withActiveTab(RightPanelTab.DETAILS), UiLayoutBreakpoint.COMPACT)
            .visibleSections()
            .stream()
            .map(RightPanelSection::id)
            .toList();
        List<String> aiTab = service.resolveCompactionPlan(base.withActiveTab(RightPanelTab.AI), UiLayoutBreakpoint.COMPACT)
            .visibleSections()
            .stream()
            .map(RightPanelSection::id)
            .toList();
        List<String> pathTab = service.resolveCompactionPlan(base.withActiveTab(RightPanelTab.PATH), UiLayoutBreakpoint.COMPACT)
            .visibleSections()
            .stream()
            .map(RightPanelSection::id)
            .toList();

        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_DESCRIPTION
        ), detailsTab);
        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_AI
        ), aiTab);
        assertEquals(List.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_PATH
        ), pathTab);
    }

    @Test
    void resolveVisibleSectionsRespectsMinBreakpoint() {
        RightPanelLayoutService service = new RightPanelLayoutService(List.of(
            new RightPanelSection("details", RightPanelSectionPriority.PRIMARY, false, true, UiLayoutBreakpoint.COMPACT),
            new RightPanelSection("metrics", RightPanelSectionPriority.TERTIARY, true, false, UiLayoutBreakpoint.WIDE)
        ));

        List<String> normal = service.resolveVisibleSections(service.defaultState(), UiLayoutBreakpoint.NORMAL)
            .stream()
            .map(RightPanelSection::id)
            .toList();
        List<String> wide = service.resolveVisibleSections(service.defaultState(), UiLayoutBreakpoint.WIDE)
            .stream()
            .map(RightPanelSection::id)
            .toList();

        assertEquals(List.of("details"), normal);
        assertEquals(List.of("details", "metrics"), wide);
    }

    @Test
    void loadAndUpdateSectionStatePersistExpandedSections() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_LAYOUT_DENSITY_MODE, "compact");
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_STATE_EXPANDED_SECTIONS, "details,ai");
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelLayoutState loaded = service.loadState();
        assertTrue(loaded.isSectionExpanded(RightPanelLayoutService.SECTION_DETAILS));
        assertTrue(loaded.isSectionExpanded(RightPanelLayoutService.SECTION_AI));
        assertFalse(loaded.isSectionExpanded(RightPanelLayoutService.SECTION_DESCRIPTION));

        RightPanelLayoutState updated = service.updateSectionExpanded(loaded, RightPanelLayoutService.SECTION_DESCRIPTION, true);
        updated = service.updateSectionExpanded(updated, RightPanelLayoutService.SECTION_AI, false);

        RightPanelLayoutState reloaded = service.loadState();
        assertEquals(Set.of(
            RightPanelLayoutService.SECTION_DETAILS,
            RightPanelLayoutService.SECTION_DESCRIPTION
        ), reloaded.expandedSectionIds());
        assertFalse(reloaded.isSectionExpanded(RightPanelLayoutService.SECTION_AI));
    }

    @Test
    void inspectorMappingDefinesSingleReusableSectionToTabContract() {
        RightPanelLayoutService service = new RightPanelLayoutService();

        Map<String, RightPanelInspectorTab> mapping = service.resolveSectionToInspectorTabMapping();

        assertEquals(4, mapping.size());
        assertEquals(RightPanelInspectorTab.PROPERTIES, mapping.get(RightPanelLayoutService.SECTION_DETAILS));
        assertEquals(RightPanelInspectorTab.DESCRIPTION, mapping.get(RightPanelLayoutService.SECTION_DESCRIPTION));
        assertEquals(RightPanelInspectorTab.ANALYTICS, mapping.get(RightPanelLayoutService.SECTION_AI));
        assertEquals(RightPanelInspectorTab.ANALYTICS, mapping.get(RightPanelLayoutService.SECTION_PATH));
    }

    @Test
    void inspectorContentPriorityInsideAnalyticsTabKeepsAiPrimaryAndPathSecondary() {
        RightPanelLayoutService service = new RightPanelLayoutService();

        List<RightPanelInspectorSectionMapping> analytics = service.resolveInspectorContentPriority(RightPanelInspectorTab.ANALYTICS);

        assertEquals(List.of(
            RightPanelLayoutService.SECTION_AI,
            RightPanelLayoutService.SECTION_PATH
        ), analytics.stream().map(RightPanelInspectorSectionMapping::sectionId).toList());
        assertEquals(RightPanelSectionPriority.PRIMARY, analytics.getFirst().contentPriority());
        assertEquals(RightPanelSectionPriority.SECONDARY, analytics.get(1).contentPriority());
    }

    @Test
    void inspectorTabOrderAndCompactFallbackLabelsAreStable() {
        RightPanelLayoutService service = new RightPanelLayoutService();

        assertEquals(List.of(
            RightPanelInspectorTab.PROPERTIES,
            RightPanelInspectorTab.DESCRIPTION,
            RightPanelInspectorTab.ANALYTICS
        ), service.resolveInspectorTabOrder());

        assertEquals("Свойства", service.resolveInspectorTabLabel(RightPanelInspectorTab.PROPERTIES, UiLayoutBreakpoint.COMPACT));
        assertEquals("Описание", service.resolveInspectorTabLabel(RightPanelInspectorTab.DESCRIPTION, UiLayoutBreakpoint.COMPACT));
        assertEquals("ИИ+График", service.resolveInspectorTabLabel(RightPanelInspectorTab.ANALYTICS, UiLayoutBreakpoint.COMPACT));
        assertEquals("ИИ-Анализ & График", service.resolveInspectorTabLabel(RightPanelInspectorTab.ANALYTICS, UiLayoutBreakpoint.NORMAL));
    }

    @Test
    void analyticsMergePolicySuppressesDuplicateSummaryByDefault() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelInspectorAnalyticsPolicy policy = service.resolveInspectorAnalyticsPolicy();

        assertTrue(policy.aiSummaryPrimary());
        assertTrue(policy.pathSummarySecondary());
        assertTrue(policy.suppressDuplicateSummary());
        assertTrue(policy.pathMetricsStandaloneFallback());
    }

    @Test
    void inspectorStatePersistenceStoresActiveTabAndExpandedSubstates() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelInspectorState persisted = new RightPanelInspectorState(
            RightPanelInspectorTab.ANALYTICS,
            Set.of(
                RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL,
                RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL
            )
        );

        service.saveInspectorState(persisted);

        RightPanelInspectorState loaded = service.loadInspectorState();
        assertEquals(RightPanelInspectorTab.ANALYTICS, loaded.activeTab());
        assertTrue(loaded.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL));
        assertTrue(loaded.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL));
    }

    @Test
    void resolveActiveInspectorTabUsesInspectorPersistence() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, "description");
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelInspectorTab active = service.resolveActiveInspectorTab(service.defaultState(), UiLayoutBreakpoint.NORMAL);

        assertEquals(RightPanelInspectorTab.DESCRIPTION, active);
    }

    @Test
    void resolveInspectorTabsUsesStableBaselineOrderAcrossBreakpoints() {
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelLayoutState legacyAiState = service.defaultState().withActiveTab(RightPanelTab.AI);

        assertEquals(RightPanelInspectorTab.baselineOrder(), service.resolveInspectorTabs(legacyAiState, UiLayoutBreakpoint.COMPACT));
        assertEquals(RightPanelInspectorTab.baselineOrder(), service.resolveInspectorTabs(legacyAiState, UiLayoutBreakpoint.NORMAL));
        assertEquals(RightPanelInspectorTab.baselineOrder(), service.resolveInspectorTabs(legacyAiState, UiLayoutBreakpoint.WIDE));
    }

    @Test
    void resolveActiveInspectorTabUsesDeterministicDefaultWhenInspectorStateMissing() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, null);
        RightPanelLayoutService service = new RightPanelLayoutService();
        RightPanelLayoutState legacyPathState = service.defaultState().withActiveTab(RightPanelTab.PATH);

        RightPanelInspectorTab active = service.resolveActiveInspectorTab(legacyPathState, UiLayoutBreakpoint.NORMAL);

        assertEquals(RightPanelInspectorTab.PROPERTIES, active);
    }

    @Test
    void resolveInspectorContentPolicyUsesPerTabSubstateAndCompactSummaryFirstRules() {
        setRuntimeConfig(
            UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES,
            RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL + "," + RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL
        );
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelTabContentPolicy normalAnalytics = service.resolveInspectorContentPolicy(
            service.defaultState(),
            UiLayoutBreakpoint.NORMAL,
            RightPanelInspectorTab.ANALYTICS
        );
        RightPanelTabContentPolicy compactAnalytics = service.resolveInspectorContentPolicy(
            service.defaultState(),
            UiLayoutBreakpoint.COMPACT,
            RightPanelInspectorTab.ANALYTICS
        );

        assertFalse(normalAnalytics.summaryFirst());
        assertTrue(normalAnalytics.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL));
        assertTrue(normalAnalytics.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL));
        assertTrue(compactAnalytics.summaryFirst(), "Compact mode must force summary-first analytics tab");
        assertEquals(1, compactAnalytics.maxHeavyBlocksVisible());
    }

    @Test
    void resolveInspectorContentPolicyScopesExpandedSubstatesToCurrentTab() {
        setRuntimeConfig(
            UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_EXPANDED_SUBSTATES,
            RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL + ",description.full"
        );
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelTabContentPolicy analyticsPolicy = service.resolveInspectorContentPolicy(
            service.defaultState(),
            UiLayoutBreakpoint.NORMAL,
            RightPanelInspectorTab.ANALYTICS
        );
        RightPanelTabContentPolicy descriptionPolicy = service.resolveInspectorContentPolicy(
            service.defaultState(),
            UiLayoutBreakpoint.NORMAL,
            RightPanelInspectorTab.DESCRIPTION
        );

        assertTrue(analyticsPolicy.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL));
        assertFalse(analyticsPolicy.isSubstateExpanded("description.full"));
        assertTrue(descriptionPolicy.isSubstateExpanded("description.full"));
        assertFalse(descriptionPolicy.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL));
    }

    @Test
    void updateInspectorStateApiPersistsActiveTabAndExpandedSubstates() {
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelInspectorState state = service.updateInspectorActiveTab(
            service.defaultInspectorState(),
            RightPanelInspectorTab.DESCRIPTION
        );
        state = service.updateInspectorSubstateExpanded(state, "description.full", true);
        state = service.updateInspectorSubstateExpanded(state, RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL, true);
        state = service.updateInspectorSubstateExpanded(state, "description.full", false);

        RightPanelInspectorState loaded = service.loadInspectorState();
        assertEquals(RightPanelInspectorTab.DESCRIPTION, loaded.activeTab());
        assertFalse(loaded.isSubstateExpanded("description.full"));
        assertTrue(loaded.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL));
    }

    @Test
    void resolveTabHeightCompactionPlanBuildsIndependentTabbedInspectorPolicy() {
        setRuntimeConfig(UxConfigDefaults.CONFIG_UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB, "analytics");
        RightPanelLayoutService service = new RightPanelLayoutService();

        RightPanelInspectorDisplayPolicy lowHeightPolicy = service.resolveTabHeightCompactionPlan(
            service.defaultState(),
            UiLayoutBreakpoint.NORMAL,
            640.0
        );

        assertEquals(RightPanelInspectorTab.ANALYTICS, lowHeightPolicy.activeTab());
        assertEquals(RightPanelTabHeightBand.VERY_LOW_HEIGHT, lowHeightPolicy.heightBand());
        assertEquals(3, lowHeightPolicy.tabs().size());
        RightPanelTabContentPolicy analyticsPolicy = lowHeightPolicy.contentPolicyFor(RightPanelInspectorTab.ANALYTICS);
        assertTrue(analyticsPolicy.heightCompactionApplied());
        assertTrue(analyticsPolicy.localScrollOnly());
        assertTrue(analyticsPolicy.summaryFirst());
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
}
