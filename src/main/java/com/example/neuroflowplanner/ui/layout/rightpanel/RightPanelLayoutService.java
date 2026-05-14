package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source of truth for right-panel section metadata and adaptive display policy.
 */
public final class RightPanelLayoutService {
    public static final String SECTION_DETAILS = "details";
    public static final String SECTION_DESCRIPTION = "description";
    public static final String SECTION_AI = "ai";
    public static final String SECTION_PATH = "path";

    private final List<RightPanelSection> sections;
    private final Map<String, RightPanelSection> sectionById;
    private final List<RightPanelInspectorSectionMapping> inspectorSectionMappings;
    private final Map<String, RightPanelInspectorSectionMapping> inspectorMappingBySectionId;
    private final RightPanelInspectorAnalyticsPolicy inspectorAnalyticsPolicy;

    public RightPanelLayoutService() {
        this(defaultSections());
    }

    public RightPanelLayoutService(List<RightPanelSection> sections) {
        List<RightPanelSection> normalized = normalizeSections(sections);
        this.sections = List.copyOf(normalized);
        Map<String, RightPanelSection> byId = new LinkedHashMap<>();
        for (RightPanelSection section : normalized) {
            byId.put(section.id(), section);
        }
        this.sectionById = Collections.unmodifiableMap(byId);
        List<RightPanelInspectorSectionMapping> mapping = buildInspectorSectionMappings(normalized);
        this.inspectorSectionMappings = List.copyOf(mapping);
        Map<String, RightPanelInspectorSectionMapping> mappingBySection = new LinkedHashMap<>();
        for (RightPanelInspectorSectionMapping entry : mapping) {
            mappingBySection.put(entry.sectionId(), entry);
        }
        this.inspectorMappingBySectionId = Collections.unmodifiableMap(mappingBySection);
        this.inspectorAnalyticsPolicy = RightPanelInspectorAnalyticsPolicy.defaults();
    }

    public List<RightPanelSection> sections() {
        return sections;
    }

    /**
     * Returns canonical IA mapping entries from section ids to inspector tabs with tab-local priority.
     */
    public List<RightPanelInspectorSectionMapping> inspectorSectionMappings() {
        return inspectorSectionMappings;
    }

    /**
     * Returns baseline tab order for the tabbed inspector shell.
     */
    public List<RightPanelInspectorTab> resolveInspectorTabOrder() {
        return RightPanelInspectorTab.baselineOrder();
    }

    /**
     * Resolves localized tab label with compact fallback semantics.
     */
    public String resolveInspectorTabLabel(RightPanelInspectorTab tab, UiLayoutBreakpoint breakpoint) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        return safeTab.resolveLabel(breakpoint);
    }

    /**
     * Reusable section-to-tab mapping for policy/UI layers.
     */
    public Map<String, RightPanelInspectorTab> resolveSectionToInspectorTabMapping() {
        LinkedHashMap<String, RightPanelInspectorTab> mapping = new LinkedHashMap<>();
        for (RightPanelInspectorSectionMapping entry : inspectorSectionMappings) {
            mapping.put(entry.sectionId(), entry.inspectorTab());
        }
        return Collections.unmodifiableMap(mapping);
    }

    /**
     * Resolves canonical inspector tab for a legacy right-panel section.
     */
    public RightPanelInspectorTab resolveInspectorTabForSection(String sectionId) {
        String normalized = normalizeId(sectionId);
        if (normalized == null) {
            return RightPanelInspectorTab.PROPERTIES;
        }
        RightPanelInspectorSectionMapping mapped = inspectorMappingBySectionId.get(normalized);
        if (mapped != null) {
            return mapped.inspectorTab();
        }
        RightPanelSection section = sectionById.get(normalized);
        if (section == null) {
            return RightPanelInspectorTab.PROPERTIES;
        }
        if (section.priority().isPrimary()) {
            return RightPanelInspectorTab.PROPERTIES;
        }
        return section.priority().isTertiary()
            ? RightPanelInspectorTab.ANALYTICS
            : RightPanelInspectorTab.DESCRIPTION;
    }

    /**
     * Canonical mapping from legacy section stack tab to tabbed inspector tab.
     */
    public RightPanelInspectorTab resolveInspectorTabForLegacyTab(RightPanelTab tab) {
        return mapLegacyTabToInspectorTab(tab);
    }

    /**
     * Canonical reverse mapping to keep legacy right-panel state in sync while tabbed inspector rolls out.
     */
    public RightPanelTab resolveLegacyTabForInspectorTab(RightPanelInspectorTab tab) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        return switch (safeTab) {
            case PROPERTIES, DESCRIPTION -> RightPanelTab.DETAILS;
            case ANALYTICS -> RightPanelTab.AI;
        };
    }

    /**
     * Per-tab section ordering and priority for progressive disclosure within a tab.
     */
    public List<RightPanelInspectorSectionMapping> resolveInspectorContentPriority(RightPanelInspectorTab tab) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        List<RightPanelInspectorSectionMapping> out = new ArrayList<>();
        for (RightPanelInspectorSectionMapping entry : inspectorSectionMappings) {
            if (entry.inspectorTab() == safeTab) {
                out.add(entry);
            }
        }
        out.sort((left, right) -> {
            int byOrder = Integer.compare(left.order(), right.order());
            if (byOrder != 0) {
                return byOrder;
            }
            return Integer.compare(left.contentPriority().ordinal(), right.contentPriority().ordinal());
        });
        return List.copyOf(out);
    }

    /**
     * Composition rules for AI + critical-path content inside the analytics inspector tab.
     */
    public RightPanelInspectorAnalyticsPolicy resolveInspectorAnalyticsPolicy() {
        return inspectorAnalyticsPolicy;
    }

    public RightPanelInspectorState defaultInspectorState() {
        return new RightPanelInspectorState(
            RightPanelInspectorTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT),
            Set.of()
        );
    }

    public RightPanelInspectorState loadInspectorState() {
        return new RightPanelInspectorState(
            RightPanelInspectorTab.resolve(ConfigManager.getUxRightPanelInspectorActiveTab()),
            ConfigManager.getUxRightPanelInspectorExpandedSubstateIds()
        );
    }

    public void saveInspectorState(RightPanelInspectorState state) {
        if (state == null) {
            return;
        }
        ConfigManager.setUxRightPanelInspectorActiveTab(state.activeTab().id());
        ConfigManager.setUxRightPanelInspectorExpandedSubstateIds(state.expandedSubstateIds());
    }

    public RightPanelInspectorState updateInspectorActiveTab(RightPanelInspectorState state, RightPanelInspectorTab tab) {
        RightPanelInspectorState safeState = state == null ? loadInspectorState() : state;
        RightPanelInspectorState updated = safeState.withActiveTab(tab);
        saveInspectorState(updated);
        return updated;
    }

    public RightPanelInspectorState updateInspectorSubstateExpanded(
        RightPanelInspectorState state,
        String substateId,
        boolean expanded
    ) {
        RightPanelInspectorState safeState = state == null ? loadInspectorState() : state;
        RightPanelInspectorState updated = safeState.withSubstateExpanded(substateId, expanded);
        saveInspectorState(updated);
        return updated;
    }

    public List<RightPanelInspectorTab> resolveInspectorTabs(RightPanelLayoutState state, UiLayoutBreakpoint breakpoint) {
        return resolveInspectorTabOrder();
    }

    public RightPanelInspectorTab resolveActiveInspectorTab(RightPanelLayoutState state, UiLayoutBreakpoint breakpoint) {
        List<RightPanelInspectorTab> tabs = resolveInspectorTabs(state, breakpoint);
        RightPanelInspectorState inspectorState = loadInspectorState();
        RightPanelInspectorTab requested = inspectorState.activeTab();
        if (requested == null && state != null) {
            requested = mapLegacyTabToInspectorTab(state.activeTab());
        }
        if (requested == null || !tabs.contains(requested)) {
            return tabs.isEmpty() ? RightPanelInspectorTab.PROPERTIES : tabs.getFirst();
        }
        return requested;
    }

    public RightPanelTabContentPolicy resolveInspectorContentPolicy(
        RightPanelLayoutState state,
        UiLayoutBreakpoint breakpoint
    ) {
        return resolveInspectorContentPolicy(state, breakpoint, resolveActiveInspectorTab(state, breakpoint));
    }

    public RightPanelTabContentPolicy resolveInspectorContentPolicy(
        RightPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        RightPanelInspectorTab tab
    ) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        RightPanelInspectorState inspectorState = loadInspectorState();
        RightPanelInspectorTab safeTab = tab == null
            ? resolveActiveInspectorTab(state, safeBreakpoint)
            : tab;
        RightPanelTabHeightBand defaultHeightBand = safeBreakpoint == UiLayoutBreakpoint.COMPACT
            ? RightPanelTabHeightBand.LOW_HEIGHT
            : RightPanelTabHeightBand.TALL;
        return buildTabContentPolicy(safeTab, safeBreakpoint, defaultHeightBand, inspectorState);
    }

    public RightPanelInspectorDisplayPolicy resolveTabHeightCompactionPlan(
        RightPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        double availableHeightPx
    ) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        RightPanelLayoutState safeState = state == null ? defaultState() : state;
        UiRightContextMode mode = resolveMode(safeBreakpoint);
        RightPanelLayoutState effectiveState = safeState.withMode(mode);

        RightPanelInspectorState inspectorState = loadInspectorState();
        List<RightPanelInspectorTab> tabs = resolveInspectorTabs(effectiveState, safeBreakpoint);
        RightPanelInspectorTab activeTab = resolveActiveInspectorTab(effectiveState, safeBreakpoint);
        RightPanelTabHeightBand heightBand = RightPanelTabHeightBand.resolve(availableHeightPx);

        List<RightPanelTabContentPolicy> tabPolicies = new ArrayList<>();
        for (RightPanelInspectorTab tab : tabs) {
            tabPolicies.add(buildTabContentPolicy(tab, safeBreakpoint, heightBand, inspectorState));
        }

        return new RightPanelInspectorDisplayPolicy(
            safeBreakpoint,
            mode,
            effectiveState.density(),
            heightBand,
            tabs,
            activeTab,
            tabPolicies,
            mode == UiRightContextMode.OVERLAY,
            true
        );
    }

    public RightPanelLayoutState defaultState() {
        return new RightPanelLayoutState(
            UiRightContextMode.COLLAPSIBLE,
            defaultExpandedSectionIds(),
            RightPanelTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public RightPanelLayoutState loadState() {
        return new RightPanelLayoutState(
            UiRightContextMode.COLLAPSIBLE,
            ConfigManager.getUxRightPanelExpandedSectionIds(),
            RightPanelTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public void saveState(RightPanelLayoutState state) {
        if (state == null) {
            return;
        }
        ConfigManager.setUxRightPanelExpandedSectionIds(state.expandedSectionIds());
    }

    public RightPanelLayoutState updateSectionExpanded(RightPanelLayoutState state, String sectionId, boolean expanded) {
        RightPanelLayoutState safeState = state == null ? loadState() : state;
        String normalizedId = normalizeId(sectionId);
        if (normalizedId == null) {
            return safeState;
        }
        RightPanelSection section = sectionById.get(normalizedId);
        if (section == null) {
            return safeState;
        }
        if (!section.collapsible() && !expanded) {
            return safeState;
        }
        RightPanelLayoutState updated = safeState.withExpandedSection(normalizedId, expanded);
        saveState(updated);
        return updated;
    }

    public UiRightContextMode resolveMode(UiLayoutBreakpoint breakpoint) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        return switch (safeBreakpoint) {
            case WIDE -> UiRightContextMode.PINNED;
            case NORMAL -> UiRightContextMode.COLLAPSIBLE;
            case COMPACT -> UiRightContextMode.OVERLAY;
        };
    }

    public List<RightPanelSection> resolveVisibleSections(RightPanelLayoutState state, UiLayoutBreakpoint breakpoint) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        RightPanelLayoutState safeState = state == null ? defaultState() : state;

        List<RightPanelSection> supported = new ArrayList<>();
        for (RightPanelSection section : sections) {
            if (section.supportedAt(safeBreakpoint)) {
                supported.add(section);
            }
        }

        if (safeBreakpoint != UiLayoutBreakpoint.COMPACT) {
            return List.copyOf(supported);
        }

        Set<String> allowedIds = compactVisibleSectionIds(safeState.activeTab());
        List<RightPanelSection> compactVisible = new ArrayList<>();
        for (RightPanelSection section : supported) {
            if (allowedIds.contains(section.id())) {
                compactVisible.add(section);
            }
        }
        return List.copyOf(compactVisible);
    }

    public RightPanelDisplayPolicy resolveCompactionPlan(RightPanelLayoutState state, UiLayoutBreakpoint breakpoint) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        RightPanelLayoutState baseState = state == null ? defaultState() : state;
        UiRightContextMode mode = resolveMode(safeBreakpoint);
        RightPanelLayoutState effectiveState = baseState.withMode(mode);

        List<RightPanelSection> visibleSections = resolveVisibleSections(effectiveState, safeBreakpoint);
        Set<String> expandedSectionIds = resolveExpandedSectionIds(effectiveState, visibleSections, safeBreakpoint);
        Set<String> demotedSectionIds = resolveDemotedSectionIds(effectiveState, safeBreakpoint, visibleSections);

        boolean compact = safeBreakpoint == UiLayoutBreakpoint.COMPACT;
        return new RightPanelDisplayPolicy(
            safeBreakpoint,
            mode,
            effectiveState.density(),
            effectiveState.activeTab(),
            visibleSections,
            expandedSectionIds,
            demotedSectionIds,
            compact,
            compact,
            compact,
            compact ? 1 : Integer.MAX_VALUE
        );
    }

    private Set<String> resolveExpandedSectionIds(
        RightPanelLayoutState state,
        List<RightPanelSection> visibleSections,
        UiLayoutBreakpoint breakpoint
    ) {
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        Set<String> compactAllowed = breakpoint == UiLayoutBreakpoint.COMPACT
            ? compactExpandedSectionIds(state.activeTab())
            : Set.of();

        for (RightPanelSection section : visibleSections) {
            if (!section.collapsible()) {
                expanded.add(section.id());
                continue;
            }
            if (breakpoint == UiLayoutBreakpoint.COMPACT && !compactAllowed.contains(section.id())) {
                continue;
            }
            if (state.isSectionExpanded(section.id())) {
                expanded.add(section.id());
            }
        }
        return Collections.unmodifiableSet(expanded);
    }

    private Set<String> resolveDemotedSectionIds(
        RightPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        List<RightPanelSection> visibleSections
    ) {
        LinkedHashSet<String> demoted = new LinkedHashSet<>();
        LinkedHashSet<String> visibleIds = new LinkedHashSet<>();
        for (RightPanelSection section : visibleSections) {
            visibleIds.add(section.id());
            if (breakpoint != UiLayoutBreakpoint.WIDE && section.priority().isTertiary()) {
                demoted.add(section.id());
            }
        }

        if (breakpoint == UiLayoutBreakpoint.COMPACT) {
            for (RightPanelSection section : sections) {
                if (!section.supportedAt(breakpoint)) {
                    continue;
                }
                if (!visibleIds.contains(section.id()) && !section.priority().isPrimary()) {
                    demoted.add(section.id());
                }
            }
        }
        return Collections.unmodifiableSet(demoted);
    }

    private Set<String> compactVisibleSectionIds(RightPanelTab activeTab) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(SECTION_DETAILS);
        RightPanelTab safeTab = activeTab == null ? RightPanelTab.DETAILS : activeTab;
        switch (safeTab) {
            case DETAILS -> ids.add(SECTION_DESCRIPTION);
            case AI -> ids.add(SECTION_AI);
            case PATH -> ids.add(SECTION_PATH);
        }
        return Collections.unmodifiableSet(ids);
    }

    private Set<String> compactExpandedSectionIds(RightPanelTab activeTab) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(SECTION_DETAILS);
        RightPanelTab safeTab = activeTab == null ? RightPanelTab.DETAILS : activeTab;
        switch (safeTab) {
            case DETAILS -> ids.add(SECTION_DESCRIPTION);
            case AI -> ids.add(SECTION_AI);
            case PATH -> ids.add(SECTION_PATH);
        }
        return Collections.unmodifiableSet(ids);
    }

    private Set<String> defaultExpandedSectionIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (RightPanelSection section : sections) {
            if (!section.collapsible() || section.defaultExpanded()) {
                ids.add(section.id());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    private static List<RightPanelSection> normalizeSections(List<RightPanelSection> source) {
        List<RightPanelSection> safeSource = source == null ? List.of() : source;
        LinkedHashMap<String, RightPanelSection> deduped = new LinkedHashMap<>();
        for (RightPanelSection section : safeSource) {
            if (section == null) {
                continue;
            }
            deduped.putIfAbsent(section.id(), section);
        }
        if (deduped.isEmpty()) {
            return defaultSections();
        }
        return List.copyOf(deduped.values());
    }

    private RightPanelTabContentPolicy buildTabContentPolicy(
        RightPanelInspectorTab tab,
        UiLayoutBreakpoint breakpoint,
        RightPanelTabHeightBand heightBand,
        RightPanelInspectorState inspectorState
    ) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        RightPanelInspectorState safeInspectorState = inspectorState == null ? defaultInspectorState() : inspectorState;
        List<RightPanelInspectorSectionMapping> sectionMappings = resolveInspectorContentPriority(safeTab);
        Set<String> expandedSubstates = filterSubstatesForTab(safeInspectorState.expandedSubstateIds(), safeTab);

        boolean lowHeight = heightBand != null && heightBand.isLowHeight();
        boolean heightCompaction = lowHeight;
        boolean summaryFirst = switch (safeTab) {
            case PROPERTIES -> false;
            case DESCRIPTION -> lowHeight;
            case ANALYTICS -> {
                boolean aiFull = safeInspectorState.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_AI_FULL);
                boolean pathFull = safeInspectorState.isSubstateExpanded(RightPanelInspectorState.SUBSTATE_ANALYTICS_PATH_FULL);
                yield lowHeight || !(aiFull || pathFull);
            }
        };

        boolean lazyHeavyContent = safeTab == RightPanelInspectorTab.ANALYTICS
            || safeTab == RightPanelInspectorTab.DESCRIPTION;
        int maxHeavyBlocksVisible = safeTab == RightPanelInspectorTab.ANALYTICS && !lowHeight ? 2 : 1;

        return new RightPanelTabContentPolicy(
            safeTab,
            resolveInspectorTabLabel(safeTab, UiLayoutBreakpoint.NORMAL),
            resolveInspectorTabLabel(safeTab, UiLayoutBreakpoint.COMPACT),
            sectionMappings,
            expandedSubstates,
            summaryFirst,
            true,
            lazyHeavyContent,
            heightCompaction,
            maxHeavyBlocksVisible
        );
    }

    private Set<String> filterSubstatesForTab(Set<String> substateIds, RightPanelInspectorTab tab) {
        if (substateIds == null || substateIds.isEmpty()) {
            return Set.of();
        }
        String prefix = (tab == null ? RightPanelInspectorTab.PROPERTIES : tab).id() + ".";
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        for (String token : substateIds) {
            String normalized = normalizeId(token);
            if (normalized != null && normalized.startsWith(prefix)) {
                filtered.add(normalized);
            }
        }
        if (filtered.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(filtered);
    }

    private static RightPanelInspectorTab mapLegacyTabToInspectorTab(RightPanelTab tab) {
        RightPanelTab safeTab = tab == null ? RightPanelTab.DETAILS : tab;
        return switch (safeTab) {
            case DETAILS -> RightPanelInspectorTab.PROPERTIES;
            case AI, PATH -> RightPanelInspectorTab.ANALYTICS;
        };
    }

    private static List<RightPanelInspectorSectionMapping> buildInspectorSectionMappings(List<RightPanelSection> sourceSections) {
        List<RightPanelSection> safeSections = sourceSections == null ? List.of() : sourceSections;
        LinkedHashMap<String, RightPanelSection> byId = new LinkedHashMap<>();
        for (RightPanelSection section : safeSections) {
            byId.put(section.id(), section);
        }

        LinkedHashMap<String, RightPanelInspectorSectionMapping> mappings = new LinkedHashMap<>();
        addMappedSection(
            mappings,
            byId.get(SECTION_DETAILS),
            RightPanelInspectorTab.PROPERTIES,
            RightPanelSectionPriority.PRIMARY,
            0
        );
        addMappedSection(
            mappings,
            byId.get(SECTION_DESCRIPTION),
            RightPanelInspectorTab.DESCRIPTION,
            RightPanelSectionPriority.PRIMARY,
            0
        );
        addMappedSection(
            mappings,
            byId.get(SECTION_AI),
            RightPanelInspectorTab.ANALYTICS,
            RightPanelSectionPriority.PRIMARY,
            0
        );
        addMappedSection(
            mappings,
            byId.get(SECTION_PATH),
            RightPanelInspectorTab.ANALYTICS,
            RightPanelSectionPriority.SECONDARY,
            1
        );

        int fallbackOrder = 100;
        for (RightPanelSection section : safeSections) {
            if (section == null || mappings.containsKey(section.id())) {
                continue;
            }
            RightPanelInspectorTab fallbackTab = section.priority().isPrimary()
                ? RightPanelInspectorTab.PROPERTIES
                : (section.priority().isTertiary() ? RightPanelInspectorTab.ANALYTICS : RightPanelInspectorTab.DESCRIPTION);
            mappings.put(section.id(), new RightPanelInspectorSectionMapping(
                section.id(),
                fallbackTab,
                section.priority(),
                fallbackOrder++
            ));
        }

        return List.copyOf(mappings.values());
    }

    private static void addMappedSection(
        Map<String, RightPanelInspectorSectionMapping> mappings,
        RightPanelSection section,
        RightPanelInspectorTab inspectorTab,
        RightPanelSectionPriority contentPriority,
        int order
    ) {
        if (section == null || mappings.containsKey(section.id())) {
            return;
        }
        mappings.put(section.id(), new RightPanelInspectorSectionMapping(
            section.id(),
            inspectorTab,
            contentPriority,
            order
        ));
    }

    private static List<RightPanelSection> defaultSections() {
        return List.of(
            new RightPanelSection(
                SECTION_DETAILS,
                RightPanelSectionPriority.PRIMARY,
                false,
                true,
                UiLayoutBreakpoint.COMPACT
            ),
            new RightPanelSection(
                SECTION_DESCRIPTION,
                RightPanelSectionPriority.SECONDARY,
                true,
                true,
                UiLayoutBreakpoint.COMPACT
            ),
            new RightPanelSection(
                SECTION_AI,
                RightPanelSectionPriority.TERTIARY,
                true,
                false,
                UiLayoutBreakpoint.COMPACT
            ),
            new RightPanelSection(
                SECTION_PATH,
                RightPanelSectionPriority.TERTIARY,
                true,
                false,
                UiLayoutBreakpoint.COMPACT
            )
        );
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
