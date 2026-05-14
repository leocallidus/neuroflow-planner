package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteViewMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.ContextSidebarDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelLayoutState;
import com.example.neuroflowplanner.ui.layout.leftpanel.LeftPanelSidebarMode;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavSurfaceHeightBand;
import com.example.neuroflowplanner.ui.layout.leftpanel.NavigationRailState;
import com.example.neuroflowplanner.ui.layout.leftpanel.TwoTierSidebarDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.leftpanel.TwoTierSidebarPolicyService;
import com.example.neuroflowplanner.ui.layout.leftpanel.UiNavigationSurfacePolicyService;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorDisplayPolicy;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorState;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorTab;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelLayoutService;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelLayoutState;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTab;
import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelTabHeightBand;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;

/**
 * Coordinates adaptive state transitions for the main shell.
 * Keeps layout state as a single source of truth outside UI rendering code.
 */
public final class MainLayoutCoordinator {
    private final AdaptiveLayoutService adaptiveLayoutService;
    private final RightPanelLayoutService rightPanelLayoutService;
    private final UiNavigationSurfacePolicyService navSurfacePolicyService;
    private final TwoTierSidebarPolicyService twoTierSidebarPolicyService;
    private UiLayoutState state;
    private RightPanelLayoutState rightPanelState;
    private RightPanelInspectorState rightPanelInspectorState;
    private LeftPanelLayoutState leftPanelState;
    private NavigationRailState twoTierSidebarState;
    private NavSurfaceHeightBand navSurfaceHeightBand = NavSurfaceHeightBand.LOW_HEIGHT;
    private RightPanelInspectorDisplayPolicy rightPanelInspectorDisplayPolicy;
    private double rightPanelInspectorAvailableHeightPx = Double.NaN;
    private LeftPanelDisplayPolicy leftPanelDisplayPolicy;
    private TwoTierSidebarDisplayPolicy twoTierSidebarDisplayPolicy;
    private boolean commandPaletteOverlayOpen;

    public MainLayoutCoordinator(AdaptiveLayoutService adaptiveLayoutService) {
        this(adaptiveLayoutService, null, null);
    }

    public MainLayoutCoordinator(
        AdaptiveLayoutService adaptiveLayoutService,
        RightPanelLayoutService rightPanelLayoutService
    ) {
        this(adaptiveLayoutService, rightPanelLayoutService, null);
    }

    public MainLayoutCoordinator(
        AdaptiveLayoutService adaptiveLayoutService,
        RightPanelLayoutService rightPanelLayoutService,
        UiNavigationSurfacePolicyService navSurfacePolicyService
    ) {
        this(adaptiveLayoutService, rightPanelLayoutService, navSurfacePolicyService, null);
    }

    public MainLayoutCoordinator(
        AdaptiveLayoutService adaptiveLayoutService,
        RightPanelLayoutService rightPanelLayoutService,
        UiNavigationSurfacePolicyService navSurfacePolicyService,
        TwoTierSidebarPolicyService twoTierSidebarPolicyService
    ) {
        this.adaptiveLayoutService = adaptiveLayoutService == null
            ? new AdaptiveLayoutService()
            : adaptiveLayoutService;
        this.rightPanelLayoutService = rightPanelLayoutService == null
            ? new RightPanelLayoutService()
            : rightPanelLayoutService;
        this.navSurfacePolicyService = navSurfacePolicyService == null
            ? new UiNavigationSurfacePolicyService()
            : navSurfacePolicyService;
        this.twoTierSidebarPolicyService = twoTierSidebarPolicyService == null
            ? new TwoTierSidebarPolicyService(this.navSurfacePolicyService, null)
            : twoTierSidebarPolicyService;
        this.state = this.adaptiveLayoutService.loadState();
        this.rightPanelState = this.rightPanelLayoutService.loadState();
        this.rightPanelInspectorState = this.rightPanelLayoutService.loadInspectorState();
        this.leftPanelState = this.navSurfacePolicyService.loadState();
        this.twoTierSidebarState = this.twoTierSidebarPolicyService.loadState();
        refreshLeftPanelDisplayPolicy();
        refreshTwoTierSidebarDisplayPolicy();
        refreshRightPanelInspectorDisplayPolicy();
    }

    public UiLayoutState state() {
        return state;
    }

    public RightPanelLayoutState rightPanelLayoutState() {
        return effectiveRightPanelState();
    }

    public LeftPanelLayoutState leftPanelLayoutState() {
        return effectiveLeftPanelState();
    }

    public NavigationRailState navigationRailState() {
        return effectiveTwoTierSidebarState();
    }

    public RightPanelDisplayPolicy rightPanelDisplayPolicy() {
        return rightPanelLayoutService.resolveCompactionPlan(effectiveRightPanelState(), state.breakpoint());
    }

    public RightPanelInspectorState rightPanelInspectorState() {
        if (rightPanelInspectorState == null) {
            rightPanelInspectorState = rightPanelLayoutService.loadInspectorState();
        }
        return rightPanelInspectorState;
    }

    public RightPanelInspectorDisplayPolicy rightPanelInspectorDisplayPolicy() {
        if (rightPanelInspectorDisplayPolicy == null) {
            refreshRightPanelInspectorDisplayPolicy();
        }
        return rightPanelInspectorDisplayPolicy;
    }

    public LeftPanelDisplayPolicy leftPanelDisplayPolicy() {
        if (leftPanelDisplayPolicy == null) {
            refreshLeftPanelDisplayPolicy();
        }
        return leftPanelDisplayPolicy;
    }

    public CommandPaletteDisplayPolicy commandPaletteDisplayPolicy() {
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy();
        return policy == null ? null : policy.palettePolicy();
    }

    public TwoTierSidebarDisplayPolicy twoTierSidebarDisplayPolicy() {
        if (twoTierSidebarDisplayPolicy == null) {
            refreshTwoTierSidebarDisplayPolicy();
        }
        return twoTierSidebarDisplayPolicy;
    }

    public ContextSidebarDisplayPolicy contextSidebarDisplayPolicy() {
        TwoTierSidebarDisplayPolicy policy = twoTierSidebarDisplayPolicy();
        return policy == null ? null : policy.contextSidebarPolicy();
    }

    public NavSurfaceHeightBand navSurfaceHeightBand() {
        return navSurfaceHeightBand;
    }

    public boolean isCommandPaletteOverlayOpen() {
        return commandPaletteOverlayOpen;
    }

    public MainLayoutSnapshot snapshot() {
        RightPanelInspectorDisplayPolicy inspectorPolicy = rightPanelInspectorDisplayPolicy();
        return new MainLayoutSnapshot(
            state,
            resolveRightContextMode(),
            inspectorPolicy == null ? RightPanelInspectorTab.PROPERTIES : inspectorPolicy.activeTab()
        );
    }

    public boolean applyWindowWidthPolicy(double windowWidthPx) {
        UiLayoutBreakpoint previousBreakpoint = state.breakpoint();
        state = adaptiveLayoutService.applyWindowWidthPolicy(state, windowWidthPx);
        boolean exitedCompact = previousBreakpoint == UiLayoutBreakpoint.COMPACT
            && state.breakpoint() != UiLayoutBreakpoint.COMPACT;
        if (state.breakpoint() == UiLayoutBreakpoint.COMPACT && !state.leftPanelCollapsed()) {
            state = state.withLeftPanelCollapsed(true);
        } else if (exitedCompact && state.leftPanelCollapsed()) {
            // Restore the full left navigation surface after compact overlay mode ends.
            state = state.withLeftPanelCollapsed(false);
        }
        refreshLeftPanelDisplayPolicy();
        enforceLeftPanelOverlayCollapse();
        syncTwoTierSidebarStateWithShell();
        refreshTwoTierSidebarDisplayPolicy();
        enforceTwoTierSidebarOverlayCollapse();
        refreshRightPanelInspectorDisplayPolicy();
        return previousBreakpoint != state.breakpoint();
    }

    public boolean applyWindowHeightPolicy(double viewportHeightPx) {
        NavSurfaceHeightBand previousBand = navSurfaceHeightBand;
        navSurfaceHeightBand = navSurfacePolicyService.resolveHeightBand(viewportHeightPx);
        LeftPanelSidebarMode previousMode = leftPanelDisplayPolicy == null ? null : leftPanelDisplayPolicy.sidebarMode();
        LeftPanelSidebarMode previousTwoTierMode = twoTierSidebarDisplayPolicy == null || twoTierSidebarDisplayPolicy.contextSidebarPolicy() == null
            ? null
            : twoTierSidebarDisplayPolicy.contextSidebarPolicy().sidebarMode();
        RightPanelTabHeightBand previousRightBand = rightPanelInspectorDisplayPolicy == null
            ? null
            : rightPanelInspectorDisplayPolicy.heightBand();
        refreshLeftPanelDisplayPolicy();
        enforceLeftPanelOverlayCollapse();
        syncTwoTierSidebarStateWithShell();
        refreshTwoTierSidebarDisplayPolicy();
        enforceTwoTierSidebarOverlayCollapse();
        refreshRightPanelInspectorDisplayPolicy();
        LeftPanelSidebarMode nextMode = leftPanelDisplayPolicy == null ? null : leftPanelDisplayPolicy.sidebarMode();
        LeftPanelSidebarMode nextTwoTierMode = twoTierSidebarDisplayPolicy == null || twoTierSidebarDisplayPolicy.contextSidebarPolicy() == null
            ? null
            : twoTierSidebarDisplayPolicy.contextSidebarPolicy().sidebarMode();
        RightPanelTabHeightBand nextRightBand = rightPanelInspectorDisplayPolicy == null
            ? null
            : rightPanelInspectorDisplayPolicy.heightBand();
        return previousBand != navSurfaceHeightBand
            || previousMode != nextMode
            || previousTwoTierMode != nextTwoTierMode
            || previousRightBand != nextRightBand;
    }

    public boolean applyRightPanelInspectorHeightPolicy(double availableHeightPx) {
        RightPanelInspectorDisplayPolicy previousPolicy = rightPanelInspectorDisplayPolicy();
        double normalizedHeight = normalizeHeight(availableHeightPx);
        if (Double.compare(rightPanelInspectorAvailableHeightPx, normalizedHeight) == 0 && previousPolicy != null) {
            return false;
        }
        rightPanelInspectorAvailableHeightPx = normalizedHeight;
        refreshRightPanelInspectorDisplayPolicy();
        RightPanelInspectorDisplayPolicy nextPolicy = rightPanelInspectorDisplayPolicy;
        if (previousPolicy == null || nextPolicy == null) {
            return true;
        }
        return previousPolicy.heightBand() != nextPolicy.heightBand()
            || previousPolicy.mode() != nextPolicy.mode()
            || previousPolicy.activeTab() != nextPolicy.activeTab();
    }

    public void toggleLeftPanelCollapsed() {
        if (leftPanelDisplayPolicy() != null
            && leftPanelDisplayPolicy.sidebarMode() == LeftPanelSidebarMode.PINNED
            && state.breakpoint() == UiLayoutBreakpoint.WIDE) {
            // Pinned sidebar mode in wide/tall remains user-toggleable for now; no-op contract will be finalized later.
        }
        state = adaptiveLayoutService.updatePanelCollapsedState(
            state,
            !state.leftPanelCollapsed(),
            state.rightPanelCollapsed()
        );
        syncTwoTierSidebarStateWithShell();
        refreshTwoTierSidebarDisplayPolicy();
        enforceTwoTierSidebarOverlayCollapse();
    }

    public void toggleRightPanelCollapsed() {
        if (resolveRightContextMode() == UiRightContextMode.PINNED) {
            return;
        }
        state = adaptiveLayoutService.updatePanelCollapsedState(
            state,
            state.leftPanelCollapsed(),
            !state.rightPanelCollapsed()
        );
    }

    public void saveState() {
        adaptiveLayoutService.saveState(state);
        rightPanelLayoutService.saveState(rightPanelState);
        rightPanelLayoutService.saveInspectorState(rightPanelInspectorState());
        navSurfacePolicyService.saveState(leftPanelState);
        twoTierSidebarPolicyService.saveState(twoTierSidebarState);
    }

    public void selectRightPanelTab(RightPanelTab tab) {
        RightPanelTab safeTab = tab == null ? RightPanelTab.DETAILS : tab;
        rightPanelState = rightPanelState.withActiveTab(safeTab);
        rightPanelLayoutService.saveState(rightPanelState);
        rightPanelInspectorState = rightPanelLayoutService.updateInspectorActiveTab(
            rightPanelInspectorState(),
            rightPanelLayoutService.resolveInspectorTabForLegacyTab(safeTab)
        );
        refreshRightPanelInspectorDisplayPolicy();
    }

    public void selectRightInspectorTab(RightPanelInspectorTab tab) {
        RightPanelInspectorTab safeTab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        rightPanelInspectorState = rightPanelLayoutService.updateInspectorActiveTab(rightPanelInspectorState(), safeTab);
        RightPanelLayoutState baseRightState = rightPanelState == null ? rightPanelLayoutService.loadState() : rightPanelState;
        rightPanelState = baseRightState.withActiveTab(rightPanelLayoutService.resolveLegacyTabForInspectorTab(safeTab));
        rightPanelLayoutService.saveState(rightPanelState);
        refreshRightPanelInspectorDisplayPolicy();
    }

    public void setRightPanelSectionExpanded(String sectionId, boolean expanded) {
        rightPanelState = rightPanelLayoutService.updateSectionExpanded(rightPanelState, sectionId, expanded);
    }

    public void setLeftPanelZoneCompacted(SidebarNavZone zone, boolean compacted) {
        leftPanelState = navSurfacePolicyService.updateCompactedZone(leftPanelState, zone, compacted);
        refreshLeftPanelDisplayPolicy();
        enforceLeftPanelOverlayCollapse();
    }

    public void setCommandPaletteViewMode(CommandPaletteViewMode viewMode) {
        leftPanelState = navSurfacePolicyService.updateLastPaletteViewMode(leftPanelState, viewMode);
        refreshLeftPanelDisplayPolicy();
    }

    public void setNavHelperHintDismissed(String hintId, boolean dismissed) {
        leftPanelState = navSurfacePolicyService.updateDismissedHelperHint(leftPanelState, hintId, dismissed);
        twoTierSidebarState = twoTierSidebarPolicyService.updateDismissedHelperHint(twoTierSidebarState, hintId, dismissed);
        refreshLeftPanelDisplayPolicy();
        refreshTwoTierSidebarDisplayPolicy();
    }

    public void selectNavigationRailDomain(SidebarRailDomain domain) {
        twoTierSidebarState = twoTierSidebarPolicyService.updateActiveRailDomain(twoTierSidebarState, domain);
        refreshTwoTierSidebarDisplayPolicy();
    }

    public void setContextSidebarCollapsed(boolean collapsed) {
        twoTierSidebarState = twoTierSidebarPolicyService.updateContextSidebarCollapsed(twoTierSidebarState, collapsed);
        if (state != null && state.leftPanelCollapsed() != collapsed) {
            state = state.withLeftPanelCollapsed(collapsed);
        }
        syncTwoTierSidebarStateWithShell();
        refreshTwoTierSidebarDisplayPolicy();
    }

    public void setCommandPaletteOverlayOpen(boolean open) {
        commandPaletteOverlayOpen = open;
    }

    private UiRightContextMode resolveRightContextMode() {
        return switch (state.breakpoint()) {
            case WIDE -> UiRightContextMode.PINNED;
            case NORMAL -> UiRightContextMode.COLLAPSIBLE;
            case COMPACT -> UiRightContextMode.OVERLAY;
        };
    }

    private RightPanelLayoutState effectiveRightPanelState() {
        return (rightPanelState == null ? rightPanelLayoutService.defaultState() : rightPanelState)
            .withMode(resolveRightContextMode())
            .withDensity(state.densityMode());
    }

    private LeftPanelLayoutState effectiveLeftPanelState() {
        LeftPanelLayoutState base = leftPanelState == null ? navSurfacePolicyService.defaultState() : leftPanelState;
        return base.withDensityMode(state.densityMode());
    }

    private NavigationRailState effectiveTwoTierSidebarState() {
        NavigationRailState base = twoTierSidebarState == null ? twoTierSidebarPolicyService.defaultState() : twoTierSidebarState;
        NavigationRailState withDensity = base.withDensityMode(state == null ? UiLayoutMode.COMFORTABLE : state.densityMode());
        if (state == null) {
            return withDensity;
        }
        return withDensity.withContextSidebarCollapsed(state.leftPanelCollapsed());
    }

    private void refreshLeftPanelDisplayPolicy() {
        leftPanelDisplayPolicy = navSurfacePolicyService.resolveHeightCompactionPlan(
            effectiveLeftPanelState(),
            state == null ? UiLayoutBreakpoint.NORMAL : state.breakpoint(),
            navSurfaceHeightBand
        );
    }

    private void refreshTwoTierSidebarDisplayPolicy() {
        twoTierSidebarDisplayPolicy = twoTierSidebarPolicyService.resolveHeightCompactionPlan(
            effectiveTwoTierSidebarState(),
            state == null ? UiLayoutBreakpoint.NORMAL : state.breakpoint(),
            navSurfaceHeightBand
        );
    }

    private void refreshRightPanelInspectorDisplayPolicy() {
        rightPanelInspectorDisplayPolicy = rightPanelLayoutService.resolveTabHeightCompactionPlan(
            effectiveRightPanelState(),
            state == null ? UiLayoutBreakpoint.NORMAL : state.breakpoint(),
            rightPanelInspectorAvailableHeightPx
        );
        if (rightPanelInspectorDisplayPolicy != null) {
            rightPanelInspectorState = rightPanelInspectorState().withActiveTab(rightPanelInspectorDisplayPolicy.activeTab());
        }
    }

    private void enforceLeftPanelOverlayCollapse() {
        LeftPanelDisplayPolicy policy = leftPanelDisplayPolicy();
        if (policy == null) {
            return;
        }
        if (state != null
            && state.breakpoint() != UiLayoutBreakpoint.COMPACT
            && policy.sidebarMode() == LeftPanelSidebarMode.OVERLAY
            && !state.leftPanelCollapsed()) {
            state = state.withLeftPanelCollapsed(true);
        }
    }

    private void enforceTwoTierSidebarOverlayCollapse() {
        ContextSidebarDisplayPolicy policy = contextSidebarDisplayPolicy();
        if (policy == null || state == null) {
            return;
        }
        if (state.breakpoint() != UiLayoutBreakpoint.COMPACT
            && policy.sidebarMode() == LeftPanelSidebarMode.OVERLAY
            && !state.leftPanelCollapsed()) {
            state = state.withLeftPanelCollapsed(true);
            syncTwoTierSidebarStateWithShell();
            refreshTwoTierSidebarDisplayPolicy();
        }
    }

    private void syncTwoTierSidebarStateWithShell() {
        if (twoTierSidebarState == null || state == null) {
            return;
        }
        if (twoTierSidebarState.contextSidebarCollapsed() != state.leftPanelCollapsed()) {
            twoTierSidebarState = twoTierSidebarState.withContextSidebarCollapsed(state.leftPanelCollapsed());
        }
    }

    private static double normalizeHeight(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : Double.NaN;
    }
}
