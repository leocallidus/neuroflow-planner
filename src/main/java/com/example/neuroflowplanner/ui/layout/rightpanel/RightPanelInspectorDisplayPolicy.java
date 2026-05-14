package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Resolved policy for tabbed inspector shell (tabs, active tab, per-tab content and height compaction).
 */
public record RightPanelInspectorDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    UiRightContextMode mode,
    UiLayoutMode density,
    RightPanelTabHeightBand heightBand,
    List<RightPanelInspectorTab> tabs,
    RightPanelInspectorTab activeTab,
    List<RightPanelTabContentPolicy> tabContentPolicies,
    boolean overlayOnDemand,
    boolean keyboardTabSwitchEnabled
) {
    public RightPanelInspectorDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        mode = mode == null ? UiRightContextMode.COLLAPSIBLE : mode;
        density = density == null ? UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT) : density;
        heightBand = heightBand == null ? RightPanelTabHeightBand.LOW_HEIGHT : heightBand;
        tabs = normalizeTabs(tabs);
        activeTab = activeTab == null ? RightPanelInspectorTab.PROPERTIES : activeTab;
        tabContentPolicies = normalizeContentPolicies(tabContentPolicies);
    }

    public RightPanelTabContentPolicy contentPolicyFor(RightPanelInspectorTab tab) {
        RightPanelInspectorTab safeTab = tab == null ? activeTab : tab;
        for (RightPanelTabContentPolicy policy : tabContentPolicies) {
            if (policy != null && policy.tab() == safeTab) {
                return policy;
            }
        }
        return null;
    }

    private static List<RightPanelInspectorTab> normalizeTabs(List<RightPanelInspectorTab> source) {
        if (source == null || source.isEmpty()) {
            return RightPanelInspectorTab.baselineOrder();
        }
        List<RightPanelInspectorTab> out = new ArrayList<>();
        for (RightPanelInspectorTab tab : source) {
            if (tab != null && !out.contains(tab)) {
                out.add(tab);
            }
        }
        return out.isEmpty() ? RightPanelInspectorTab.baselineOrder() : List.copyOf(out);
    }

    private static List<RightPanelTabContentPolicy> normalizeContentPolicies(List<RightPanelTabContentPolicy> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<RightPanelTabContentPolicy> out = new ArrayList<>();
        for (RightPanelTabContentPolicy policy : source) {
            if (policy == null) {
                continue;
            }
            boolean duplicate = out.stream().filter(Objects::nonNull).anyMatch(existing -> existing.tab() == policy.tab());
            if (!duplicate) {
                out.add(policy);
            }
        }
        return List.copyOf(out);
    }
}
