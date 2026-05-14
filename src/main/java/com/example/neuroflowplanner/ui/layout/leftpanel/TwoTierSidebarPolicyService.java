package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarNavigationService;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Policy resolver for the two-tier left navigation (navigation rail + context sidebar).
 * Keeps adaptive and persisted UX decisions outside UI rendering code.
 */
public final class TwoTierSidebarPolicyService {
    public static final String HELPER_HINT_TWO_TIER_RAIL_NOVICE = "sidebar.rail.novice";

    private final UiNavigationSurfacePolicyService navSurfacePolicyService;
    private final SidebarNavigationService sidebarNavigationService;

    public TwoTierSidebarPolicyService() {
        this(new UiNavigationSurfacePolicyService(), new SidebarNavigationService());
    }

    public TwoTierSidebarPolicyService(
        UiNavigationSurfacePolicyService navSurfacePolicyService,
        SidebarNavigationService sidebarNavigationService
    ) {
        this.navSurfacePolicyService = navSurfacePolicyService == null
            ? new UiNavigationSurfacePolicyService()
            : navSurfacePolicyService;
        this.sidebarNavigationService = sidebarNavigationService == null
            ? new SidebarNavigationService()
            : sidebarNavigationService;
    }

    public NavigationRailState defaultState() {
        return new NavigationRailState(
            parseRailDomain(UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_ACTIVE_RAIL_DOMAIN_DEFAULT),
            UxConfigDefaults.UX_TWO_TIER_SIDEBAR_STATE_CONTEXT_COLLAPSED_DEFAULT,
            parseTokenSetCsv(UxConfigDefaults.UX_NAV_SURFACES_STATE_DISMISSED_HINTS_DEFAULT),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public NavigationRailState loadState() {
        return new NavigationRailState(
            parseRailDomain(ConfigManager.getUxTwoTierSidebarActiveRailDomain()),
            ConfigManager.isUxTwoTierSidebarContextCollapsed(),
            ConfigManager.getUxNavSurfaceDismissedHelperHintIds(),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public void saveState(NavigationRailState state) {
        if (state == null) {
            return;
        }
        ConfigManager.setUxTwoTierSidebarActiveRailDomain(state.activeRailDomain().id());
        ConfigManager.setUxTwoTierSidebarContextCollapsed(state.contextSidebarCollapsed());
        // Re-use shared dismissed hints storage across nav surfaces.
        ConfigManager.setUxNavSurfaceDismissedHelperHintIds(state.dismissedHelperHintIds());
    }

    public NavigationRailState updateActiveRailDomain(
        NavigationRailState state,
        SidebarRailDomain domain
    ) {
        NavigationRailState safe = state == null ? loadState() : state;
        NavigationRailState updated = safe.withActiveRailDomain(domain);
        saveState(updated);
        return updated;
    }

    public NavigationRailState updateContextSidebarCollapsed(
        NavigationRailState state,
        boolean collapsed
    ) {
        NavigationRailState safe = state == null ? loadState() : state;
        NavigationRailState updated = safe.withContextSidebarCollapsed(collapsed);
        saveState(updated);
        return updated;
    }

    public NavigationRailState updateDismissedHelperHint(
        NavigationRailState state,
        String hintId,
        boolean dismissed
    ) {
        NavigationRailState safe = state == null ? loadState() : state;
        NavigationRailState updated = safe.withHelperHintDismissed(hintId, dismissed);
        saveState(updated);
        return updated;
    }

    public NavSurfaceHeightBand resolveHeightBand(double availableHeightPx) {
        return navSurfacePolicyService.resolveHeightBand(availableHeightPx);
    }

    public List<NavigationRailSection> resolveRailVisibleSections(
        NavigationRailState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        // Rail remains visible across width/height modes; presentation compaction is handled in UI.
        List<SidebarRailDomain> domains = sidebarNavigationService.buildRailDomains();
        List<NavigationRailSection> sections = new ArrayList<>(domains.size());
        for (int i = 0; i < domains.size(); i++) {
            SidebarRailDomain domain = domains.get(i);
            sections.add(new NavigationRailSection(
                domain,
                i,
                domain.label(),
                domain.railTooltipLabel(),
                domain.contextHeaderLabel(),
                domain.icon()
            ));
        }
        return List.copyOf(sections);
    }

    public SidebarRailDomain resolveActiveContextPanel(
        NavigationRailState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        NavigationRailState safeState = effectiveState(state);
        List<NavigationRailSection> visibleSections = resolveRailVisibleSections(safeState, breakpoint, heightBand);
        SidebarRailDomain requested = safeState.activeRailDomain();
        if (requested != null && visibleSections.stream().anyMatch(section -> section.domain() == requested)) {
            return requested;
        }
        return visibleSections.isEmpty() ? SidebarRailDomain.WORK : visibleSections.get(0).domain();
    }

    public ContextSidebarDisplayPolicy resolveContextSidebarLayout(
        NavigationRailState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        NavigationRailState safeState = effectiveState(state);
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        LeftPanelSidebarMode sidebarMode = navSurfacePolicyService.resolveSidebarMode(safeBreakpoint, safeHeightBand);
        SidebarRailDomain activeDomain = resolveActiveContextPanel(safeState, safeBreakpoint, safeHeightBand);

        boolean aggressive = safeHeightBand.isVeryLowHeight();
        boolean compactPinnedZone = safeHeightBand.isLowHeight() || safeBreakpoint == UiLayoutBreakpoint.COMPACT;
        boolean overlayOnDemand = sidebarMode == LeftPanelSidebarMode.OVERLAY;
        boolean internalScroll = true;
        boolean showPinnedTopZone = true;
        boolean showFavorites = !safeHeightBand.isLowHeight() && safeBreakpoint != UiLayoutBreakpoint.COMPACT;
        boolean showRecent = !safeHeightBand.isLowHeight();
        boolean showInlineHelperHints = !safeState.isHelperHintDismissed(HELPER_HINT_TWO_TIER_RAIL_NOVICE)
            && !safeState.isHelperHintDismissed(UiNavigationSurfacePolicyService.HELPER_HINT_SIDEBAR_GUIDED)
            && safeBreakpoint != UiLayoutBreakpoint.COMPACT
            && safeHeightBand == NavSurfaceHeightBand.TALL;
        int quickActionLimit = switch (safeHeightBand) {
            case TALL -> 4;
            case LOW_HEIGHT -> 3;
            case VERY_LOW_HEIGHT -> 3;
        };
        int maxDomainListRowsBeforeScroll = switch (safeHeightBand) {
            case TALL -> safeBreakpoint == UiLayoutBreakpoint.WIDE ? 14 : 12;
            case LOW_HEIGHT -> safeBreakpoint == UiLayoutBreakpoint.COMPACT ? 7 : 8;
            case VERY_LOW_HEIGHT -> 6;
        };

        boolean collapsed = safeState.contextSidebarCollapsed();
        if (overlayOnDemand && safeHeightBand.isVeryLowHeight() && safeBreakpoint != UiLayoutBreakpoint.COMPACT) {
            // In non-compact overlays, keep workspace-first behavior for very low heights.
            collapsed = true;
        }

        return new ContextSidebarDisplayPolicy(
            safeBreakpoint,
            safeHeightBand,
            safeState.densityMode(),
            sidebarMode,
            activeDomain,
            collapsed,
            overlayOnDemand,
            internalScroll,
            showPinnedTopZone,
            compactPinnedZone,
            aggressive,
            showFavorites,
            showRecent,
            showInlineHelperHints,
            quickActionLimit,
            maxDomainListRowsBeforeScroll
        );
    }

    public TwoTierSidebarDisplayPolicy resolveHeightCompactionPlan(
        NavigationRailState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        NavigationRailState safeState = effectiveState(state);
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;

        List<NavigationRailSection> railSections = resolveRailVisibleSections(safeState, safeBreakpoint, safeHeightBand);
        SidebarRailDomain activeDomain = resolveActiveContextPanel(safeState, safeBreakpoint, safeHeightBand);
        ContextSidebarDisplayPolicy contextPolicy = resolveContextSidebarLayout(safeState, safeBreakpoint, safeHeightBand);
        boolean aggressive = contextPolicy.aggressiveCompaction();
        boolean heightCompactionApplied = contextPolicy.heightCompactionApplied();

        return new TwoTierSidebarDisplayPolicy(
            safeBreakpoint,
            safeHeightBand,
            safeState.densityMode(),
            contextPolicy.sidebarMode(),
            railSections,
            activeDomain,
            contextPolicy,
            heightCompactionApplied,
            aggressive
        );
    }

    private NavigationRailState effectiveState(NavigationRailState state) {
        NavigationRailState base = state == null ? loadState() : state;
        return base.withDensityMode(UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode()));
    }

    private static SidebarRailDomain parseRailDomain(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SidebarRailDomain.WORK;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (SidebarRailDomain domain : SidebarRailDomain.values()) {
            if (domain.id().equalsIgnoreCase(normalized) || domain.name().equalsIgnoreCase(normalized)) {
                return domain;
            }
        }
        return SidebarRailDomain.WORK;
    }

    private static Set<String> parseTokenSetCsv(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Set.of();
        }
        String normalized = rawValue.trim();
        if (UxConfigDefaults.UX_COLLECTION_NONE_MARKER.equalsIgnoreCase(normalized)) {
            return Set.of();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (String part : normalized.split("\\s*,\\s*")) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tokens.add(part.trim().toLowerCase(Locale.ROOT));
        }
        if (tokens.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(tokens);
    }
}
