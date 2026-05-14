package com.example.neuroflowplanner.ui.layout.rightpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.layout.UiRightContextMode;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolved display policy describing what the right panel should render for a breakpoint/state.
 */
public record RightPanelDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    UiRightContextMode mode,
    UiLayoutMode density,
    RightPanelTab activeTab,
    List<RightPanelSection> visibleSections,
    Set<String> expandedSectionIds,
    Set<String> demotedSectionIds,
    boolean overlayOnDemand,
    boolean stickyHeader,
    boolean segmentedNavigation,
    int maxHeavySectionsVisible
) {
    public RightPanelDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        mode = mode == null ? UiRightContextMode.COLLAPSIBLE : mode;
        density = density == null ? UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT) : density;
        activeTab = activeTab == null ? RightPanelTab.resolve(UxConfigDefaults.UX_RIGHT_PANEL_STATE_ACTIVE_TAB_DEFAULT) : activeTab;
        visibleSections = normalizeSections(visibleSections);
        expandedSectionIds = normalizeIds(expandedSectionIds);
        demotedSectionIds = normalizeIds(demotedSectionIds);
        maxHeavySectionsVisible = Math.max(1, maxHeavySectionsVisible);
    }

    public boolean isSectionVisible(String sectionId) {
        String normalized = normalizeId(sectionId);
        if (normalized == null) {
            return false;
        }
        for (RightPanelSection section : visibleSections) {
            if (section.id().equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSectionExpanded(String sectionId) {
        String normalized = normalizeId(sectionId);
        return normalized != null && expandedSectionIds.contains(normalized);
    }

    private static List<RightPanelSection> normalizeSections(List<RightPanelSection> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RightPanelSection> out = new ArrayList<>();
        for (RightPanelSection section : source) {
            if (section == null || !seen.add(section.id())) {
                continue;
            }
            out.add(section);
        }
        return List.copyOf(out);
    }

    private static Set<String> normalizeIds(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : source) {
            String normalized = normalizeId(raw);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        if (out.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(out);
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
