package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarRailDomain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Persistable two-tier sidebar UX state (rail + context sidebar).
 */
public final class NavigationRailState {
    private final SidebarRailDomain activeRailDomain;
    private final boolean contextSidebarCollapsed;
    private final Set<String> dismissedHelperHintIds;
    private final UiLayoutMode densityMode;

    public NavigationRailState(
        SidebarRailDomain activeRailDomain,
        boolean contextSidebarCollapsed,
        Set<String> dismissedHelperHintIds,
        UiLayoutMode densityMode
    ) {
        this.activeRailDomain = activeRailDomain == null ? SidebarRailDomain.WORK : activeRailDomain;
        this.contextSidebarCollapsed = contextSidebarCollapsed;
        this.dismissedHelperHintIds = normalizeTokens(dismissedHelperHintIds);
        this.densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
    }

    public static NavigationRailState empty() {
        return new NavigationRailState(SidebarRailDomain.WORK, false, Set.of(), UiLayoutMode.COMFORTABLE);
    }

    public SidebarRailDomain activeRailDomain() {
        return activeRailDomain;
    }

    public boolean contextSidebarCollapsed() {
        return contextSidebarCollapsed;
    }

    public Set<String> dismissedHelperHintIds() {
        return dismissedHelperHintIds;
    }

    public UiLayoutMode densityMode() {
        return densityMode;
    }

    public boolean isHelperHintDismissed(String hintId) {
        String normalized = normalizeToken(hintId);
        return normalized != null && dismissedHelperHintIds.contains(normalized);
    }

    public NavigationRailState withActiveRailDomain(SidebarRailDomain domain) {
        SidebarRailDomain safe = domain == null ? SidebarRailDomain.WORK : domain;
        if (safe == activeRailDomain) {
            return this;
        }
        return new NavigationRailState(safe, contextSidebarCollapsed, dismissedHelperHintIds, densityMode);
    }

    public NavigationRailState withContextSidebarCollapsed(boolean collapsed) {
        if (collapsed == contextSidebarCollapsed) {
            return this;
        }
        return new NavigationRailState(activeRailDomain, collapsed, dismissedHelperHintIds, densityMode);
    }

    public NavigationRailState withHelperHintDismissed(String hintId, boolean dismissed) {
        String normalized = normalizeToken(hintId);
        if (normalized == null) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(dismissedHelperHintIds);
        if (dismissed) {
            updated.add(normalized);
        } else {
            updated.remove(normalized);
        }
        return new NavigationRailState(activeRailDomain, contextSidebarCollapsed, updated, densityMode);
    }

    public NavigationRailState withDensityMode(UiLayoutMode mode) {
        UiLayoutMode safe = mode == null ? UiLayoutMode.COMFORTABLE : mode;
        if (safe == densityMode) {
            return this;
        }
        return new NavigationRailState(activeRailDomain, contextSidebarCollapsed, dismissedHelperHintIds, safe);
    }

    private static Set<String> normalizeTokens(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String token : source) {
            String safe = normalizeToken(token);
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

