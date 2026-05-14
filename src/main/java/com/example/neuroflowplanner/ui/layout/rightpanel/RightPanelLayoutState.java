package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Persistable and immutable right-panel layout state independent from UI rendering code.
 */
public record RightPanelLayoutState(
    UiRightContextMode mode,
    Set<String> expandedSectionIds,
    RightPanelTab activeTab,
    UiLayoutMode density
) {
    public RightPanelLayoutState {
        mode = mode == null ? UiRightContextMode.COLLAPSIBLE : mode;
        expandedSectionIds = normalizeSectionIds(expandedSectionIds);
        activeTab = activeTab == null ? RightPanelTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT) : activeTab;
        density = density == null ? UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT) : density;
    }

    public static RightPanelLayoutState defaults() {
        return new RightPanelLayoutState(
            UiRightContextMode.COLLAPSIBLE,
            Set.of(),
            RightPanelTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT),
            UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT)
        );
    }

    public boolean isSectionExpanded(String sectionId) {
        String normalized = normalizeToken(sectionId);
        return normalized != null && expandedSectionIds.contains(normalized);
    }

    public RightPanelLayoutState withMode(UiRightContextMode nextMode) {
        return new RightPanelLayoutState(
            nextMode == null ? mode : nextMode,
            expandedSectionIds,
            activeTab,
            density
        );
    }

    public RightPanelLayoutState withExpandedSection(String sectionId, boolean expanded) {
        String normalized = normalizeToken(sectionId);
        if (normalized == null) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(expandedSectionIds);
        if (expanded) {
            updated.add(normalized);
        } else {
            updated.remove(normalized);
        }
        return new RightPanelLayoutState(mode, updated, activeTab, density);
    }

    public RightPanelLayoutState withExpandedSectionIds(Set<String> sectionIds) {
        return new RightPanelLayoutState(mode, sectionIds, activeTab, density);
    }

    public RightPanelLayoutState withActiveTab(RightPanelTab nextTab) {
        return new RightPanelLayoutState(mode, expandedSectionIds, nextTab == null ? activeTab : nextTab, density);
    }

    public RightPanelLayoutState withDensity(UiLayoutMode nextDensity) {
        return new RightPanelLayoutState(mode, expandedSectionIds, activeTab, nextDensity == null ? density : nextDensity);
    }

    private static Set<String> normalizeSectionIds(Set<String> rawIds) {
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
