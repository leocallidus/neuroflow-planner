package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Persistable inspector state independent from concrete UI controls.
 */
public record RightPanelInspectorState(
    RightPanelInspectorTab activeTab,
    Set<String> expandedSubstateIds
) {
    public static final String SUBSTATE_ANALYTICS_AI_FULL = "analytics.ai.full";
    public static final String SUBSTATE_ANALYTICS_PATH_FULL = "analytics.path.full";

    public RightPanelInspectorState {
        activeTab = activeTab == null
            ? RightPanelInspectorTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT)
            : activeTab;
        expandedSubstateIds = normalizeSubstateIds(expandedSubstateIds);
    }

    public static RightPanelInspectorState defaults() {
        return new RightPanelInspectorState(
            RightPanelInspectorTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_INSPECTOR_STATE_ACTIVE_TAB_DEFAULT),
            Set.of()
        );
    }

    public boolean isSubstateExpanded(String substateId) {
        String normalized = normalizeToken(substateId);
        return normalized != null && expandedSubstateIds.contains(normalized);
    }

    public RightPanelInspectorState withActiveTab(RightPanelInspectorTab tab) {
        return new RightPanelInspectorState(tab == null ? activeTab : tab, expandedSubstateIds);
    }

    public RightPanelInspectorState withSubstateExpanded(String substateId, boolean expanded) {
        String normalized = normalizeToken(substateId);
        if (normalized == null) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(expandedSubstateIds);
        if (expanded) {
            updated.add(normalized);
        } else {
            updated.remove(normalized);
        }
        return new RightPanelInspectorState(activeTab, updated);
    }

    public RightPanelInspectorState withExpandedSubstateIds(Set<String> substateIds) {
        return new RightPanelInspectorState(activeTab, substateIds);
    }

    private static Set<String> normalizeSubstateIds(Set<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            String safe = normalizeToken(rawId);
            if (safe != null) {
                normalized.add(safe);
            }
        }
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
