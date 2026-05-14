package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Persistable adaptive UX state for sidebar + command palette surfaces.
 */
public final class LeftPanelLayoutState {
    private final Set<SidebarNavZone> compactedZones;
    private final CommandPaletteViewMode lastPaletteViewMode;
    private final Set<String> dismissedHelperHintIds;
    private final UiLayoutMode densityMode;

    public LeftPanelLayoutState(
        Set<SidebarNavZone> compactedZones,
        CommandPaletteViewMode lastPaletteViewMode,
        Set<String> dismissedHelperHintIds,
        UiLayoutMode densityMode
    ) {
        this.compactedZones = normalizeZones(compactedZones);
        this.lastPaletteViewMode = lastPaletteViewMode == null ? CommandPaletteViewMode.GUIDED : lastPaletteViewMode;
        this.dismissedHelperHintIds = normalizeTokens(dismissedHelperHintIds);
        this.densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
    }

    public static LeftPanelLayoutState empty() {
        return new LeftPanelLayoutState(Set.of(), CommandPaletteViewMode.GUIDED, Set.of(), UiLayoutMode.COMFORTABLE);
    }

    public Set<SidebarNavZone> compactedZones() {
        return compactedZones;
    }

    public CommandPaletteViewMode lastPaletteViewMode() {
        return lastPaletteViewMode;
    }

    public Set<String> dismissedHelperHintIds() {
        return dismissedHelperHintIds;
    }

    public UiLayoutMode densityMode() {
        return densityMode;
    }

    public boolean isZoneCompacted(SidebarNavZone zone) {
        return zone != null && compactedZones.contains(zone);
    }

    public boolean isHelperHintDismissed(String hintId) {
        String normalized = normalizeToken(hintId);
        return normalized != null && dismissedHelperHintIds.contains(normalized);
    }

    public LeftPanelLayoutState withCompactedZone(SidebarNavZone zone, boolean compacted) {
        if (zone == null) {
            return this;
        }
        LinkedHashSet<SidebarNavZone> updated = new LinkedHashSet<>(compactedZones);
        if (compacted) {
            updated.add(zone);
        } else {
            updated.remove(zone);
        }
        return new LeftPanelLayoutState(updated, lastPaletteViewMode, dismissedHelperHintIds, densityMode);
    }

    public LeftPanelLayoutState withLastPaletteViewMode(CommandPaletteViewMode mode) {
        if (mode == null || mode == lastPaletteViewMode) {
            return this;
        }
        return new LeftPanelLayoutState(compactedZones, mode, dismissedHelperHintIds, densityMode);
    }

    public LeftPanelLayoutState withHelperHintDismissed(String hintId, boolean dismissed) {
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
        return new LeftPanelLayoutState(compactedZones, lastPaletteViewMode, updated, densityMode);
    }

    public LeftPanelLayoutState withDensityMode(UiLayoutMode densityMode) {
        UiLayoutMode safe = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        if (safe == this.densityMode) {
            return this;
        }
        return new LeftPanelLayoutState(compactedZones, lastPaletteViewMode, dismissedHelperHintIds, safe);
    }

    private static Set<SidebarNavZone> normalizeZones(Set<SidebarNavZone> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<SidebarNavZone> normalized = new LinkedHashSet<>();
        for (SidebarNavZone zone : source) {
            if (zone != null) {
                normalized.add(zone);
            }
        }
        if (normalized.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(normalized);
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
