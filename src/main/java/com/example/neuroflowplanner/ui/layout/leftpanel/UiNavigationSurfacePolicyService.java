package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;
import com.example.neuroflowplanner.ui.navigation.SidebarNavZone;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.UxConfigDefaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Policy resolver for unified navigation surfaces (left sidebar + command palette).
 * Keeps width/height adaptive decisions and persisted UX state outside UI rendering code.
 */
public final class UiNavigationSurfacePolicyService {
    public static final String HELPER_HINT_SIDEBAR_GUIDED = "sidebar.guided";
    public static final String HELPER_HINT_PALETTE_EMPTY_STATE = "palette.empty.state";

    public LeftPanelLayoutState defaultState() {
        return new LeftPanelLayoutState(
            parseZonesCsv(UxConfigDefaults.UX_NAV_SURFACES_STATE_COMPACTED_ZONES_DEFAULT),
            CommandPaletteViewMode.resolve(UxConfigDefaults.UX_COMMAND_PALETTE_STATE_LAST_VIEW_MODE_DEFAULT),
            parseTokenSetCsv(UxConfigDefaults.UX_NAV_SURFACES_STATE_DISMISSED_HINTS_DEFAULT),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public LeftPanelLayoutState loadState() {
        return new LeftPanelLayoutState(
            parseZones(ConfigManager.getUxNavSurfaceCompactedZones()),
            CommandPaletteViewMode.resolve(ConfigManager.getUxCommandPaletteLastViewMode()),
            ConfigManager.getUxNavSurfaceDismissedHelperHintIds(),
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode())
        );
    }

    public void saveState(LeftPanelLayoutState state) {
        if (state == null) {
            return;
        }
        ConfigManager.setUxNavSurfaceCompactedZones(encodeZones(state.compactedZones()));
        ConfigManager.setUxCommandPaletteLastViewMode(state.lastPaletteViewMode().configValue());
        ConfigManager.setUxNavSurfaceDismissedHelperHintIds(state.dismissedHelperHintIds());
    }

    public LeftPanelLayoutState updateCompactedZone(
        LeftPanelLayoutState state,
        SidebarNavZone zone,
        boolean compacted
    ) {
        LeftPanelLayoutState safeState = state == null ? loadState() : state;
        LeftPanelLayoutState updated = safeState.withCompactedZone(zone, compacted);
        saveState(updated);
        return updated;
    }

    public LeftPanelLayoutState updateLastPaletteViewMode(
        LeftPanelLayoutState state,
        CommandPaletteViewMode viewMode
    ) {
        LeftPanelLayoutState safeState = state == null ? loadState() : state;
        LeftPanelLayoutState updated = safeState.withLastPaletteViewMode(viewMode);
        saveState(updated);
        return updated;
    }

    public LeftPanelLayoutState updateDismissedHelperHint(
        LeftPanelLayoutState state,
        String hintId,
        boolean dismissed
    ) {
        LeftPanelLayoutState safeState = state == null ? loadState() : state;
        LeftPanelLayoutState updated = safeState.withHelperHintDismissed(hintId, dismissed);
        saveState(updated);
        return updated;
    }

    public NavSurfaceHeightBand resolveHeightBand(double availableHeightPx) {
        return NavSurfaceHeightBand.fromHeight(availableHeightPx);
    }

    public LeftPanelSidebarMode resolveSidebarMode(
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        return switch (safeBreakpoint) {
            case COMPACT -> LeftPanelSidebarMode.OVERLAY;
            case NORMAL -> safeHeightBand.isVeryLowHeight()
                ? LeftPanelSidebarMode.OVERLAY
                : LeftPanelSidebarMode.COLLAPSIBLE;
            case WIDE -> safeHeightBand.isVeryLowHeight()
                ? LeftPanelSidebarMode.COLLAPSIBLE
                : LeftPanelSidebarMode.PINNED;
        };
    }

    public List<SidebarNavZone> resolveSidebarVisibleZones(
        LeftPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;

        List<SidebarNavZone> zones = new ArrayList<>();
        zones.add(SidebarNavZone.QUICK);
        zones.add(SidebarNavZone.CORE);

        boolean includeAdvanced = switch (safeBreakpoint) {
            case COMPACT -> false;
            case NORMAL -> safeHeightBand == NavSurfaceHeightBand.TALL;
            case WIDE -> !safeHeightBand.isVeryLowHeight();
        };
        if (includeAdvanced) {
            zones.add(SidebarNavZone.ADVANCED);
        }
        return List.copyOf(zones);
    }

    public CommandPaletteDisplayPolicy resolvePaletteLayout(
        LeftPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        LeftPanelLayoutState safeState = effectiveState(state);
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;

        boolean compactRows = safeBreakpoint == UiLayoutBreakpoint.COMPACT || safeHeightBand.isLowHeight();
        boolean veryLow = safeHeightBand.isVeryLowHeight();
        boolean showDescriptions = !(compactRows && (safeBreakpoint != UiLayoutBreakpoint.WIDE || veryLow));
        boolean showGuidedEmptyState = !safeState.isHelperHintDismissed(HELPER_HINT_PALETTE_EMPTY_STATE);
        boolean guidedLauncher = safeState.lastPaletteViewMode() == CommandPaletteViewMode.GUIDED || showGuidedEmptyState;

        int maxResults = switch (safeHeightBand) {
            case TALL -> safeBreakpoint == UiLayoutBreakpoint.WIDE ? 18 : 16;
            case LOW_HEIGHT -> safeBreakpoint == UiLayoutBreakpoint.COMPACT ? 12 : 14;
            case VERY_LOW_HEIGHT -> 10;
        };
        int exampleQueryCount = showGuidedEmptyState
            ? (veryLow ? 2 : (safeHeightBand.isLowHeight() ? 3 : 4))
            : 0;

        return new CommandPaletteDisplayPolicy(
            safeBreakpoint,
            safeHeightBand,
            safeState.densityMode(),
            safeState.lastPaletteViewMode(),
            guidedLauncher,
            showGuidedEmptyState,
            true,
            !veryLow,
            !safeState.isHelperHintDismissed(HELPER_HINT_SIDEBAR_GUIDED),
            compactRows,
            showDescriptions,
            maxResults,
            exampleQueryCount
        );
    }

    public LeftPanelDisplayPolicy resolveHeightCompactionPlan(
        LeftPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand
    ) {
        LeftPanelLayoutState safeState = effectiveState(state);
        UiLayoutBreakpoint safeBreakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        NavSurfaceHeightBand safeHeightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;

        LeftPanelSidebarMode sidebarMode = resolveSidebarMode(safeBreakpoint, safeHeightBand);
        List<SidebarNavZone> visibleZones = resolveSidebarVisibleZones(safeState, safeBreakpoint, safeHeightBand);
        Set<SidebarNavZone> compactedZones = resolveEffectiveCompactedZones(safeState, safeBreakpoint, safeHeightBand, visibleZones);
        CommandPaletteDisplayPolicy palettePolicy = resolvePaletteLayout(safeState, safeBreakpoint, safeHeightBand);

        boolean aggressive = safeHeightBand.isVeryLowHeight();
        boolean heightCompactionApplied = safeHeightBand.isLowHeight() || !compactedZones.isEmpty() || visibleZones.size() < 3;
        boolean showInlineGuidance = !safeState.isHelperHintDismissed(HELPER_HINT_SIDEBAR_GUIDED)
            && safeBreakpoint != UiLayoutBreakpoint.COMPACT
            && safeHeightBand == NavSurfaceHeightBand.TALL
            && !aggressive;
        int quickActionLimit = switch (safeHeightBand) {
            case TALL -> 4;
            case LOW_HEIGHT -> 4;
            case VERY_LOW_HEIGHT -> 3;
        };

        return new LeftPanelDisplayPolicy(
            safeBreakpoint,
            safeHeightBand,
            safeState.densityMode(),
            sidebarMode,
            visibleZones,
            compactedZones,
            heightCompactionApplied,
            aggressive,
            true,
            true,
            showInlineGuidance,
            !visibleZones.contains(SidebarNavZone.ADVANCED),
            quickActionLimit,
            palettePolicy
        );
    }

    private LeftPanelLayoutState effectiveState(LeftPanelLayoutState state) {
        LeftPanelLayoutState base = state == null ? loadState() : state;
        return base.withDensityMode(UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode()));
    }

    private Set<SidebarNavZone> resolveEffectiveCompactedZones(
        LeftPanelLayoutState state,
        UiLayoutBreakpoint breakpoint,
        NavSurfaceHeightBand heightBand,
        List<SidebarNavZone> visibleZones
    ) {
        LinkedHashSet<SidebarNavZone> compacted = new LinkedHashSet<>(state.compactedZones());
        if (heightBand.isLowHeight()) {
            compacted.add(SidebarNavZone.ADVANCED);
        }
        if (heightBand.isVeryLowHeight() || breakpoint == UiLayoutBreakpoint.COMPACT) {
            compacted.add(SidebarNavZone.CORE);
        }
        compacted.remove(SidebarNavZone.QUICK);
        compacted.retainAll(new LinkedHashSet<>(visibleZones));
        return Collections.unmodifiableSet(compacted);
    }

    private static Set<SidebarNavZone> parseZones(Set<String> rawZones) {
        if (rawZones == null || rawZones.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<SidebarNavZone> zones = new LinkedHashSet<>();
        for (String raw : rawZones) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                zones.add(SidebarNavZone.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // ignore unknown persisted values
            }
        }
        return Collections.unmodifiableSet(zones);
    }

    private static Set<SidebarNavZone> parseZonesCsv(String rawValue) {
        return parseZones(parseTokenSetCsv(rawValue));
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
        String[] parts = normalized.split("\\s*,\\s*");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            tokens.add(part.trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableSet(tokens);
    }

    private static Set<String> encodeZones(Set<SidebarNavZone> zones) {
        if (zones == null || zones.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> encoded = new LinkedHashSet<>();
        for (SidebarNavZone zone : zones) {
            if (zone != null) {
                encoded.add(zone.name().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(encoded);
    }
}
