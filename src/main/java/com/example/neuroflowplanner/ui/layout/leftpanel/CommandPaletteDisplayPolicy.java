package com.example.neuroflowplanner.ui.layout.leftpanel;

import com.example.neuroflowplanner.ui.layout.UiLayoutBreakpoint;
import com.example.neuroflowplanner.ui.layout.UiLayoutMode;

/**
 * Adaptive display policy for command palette overlay/launcher UX.
 */
public record CommandPaletteDisplayPolicy(
    UiLayoutBreakpoint breakpoint,
    NavSurfaceHeightBand heightBand,
    UiLayoutMode densityMode,
    CommandPaletteViewMode preferredViewMode,
    boolean guidedLauncher,
    boolean showGuidedEmptyState,
    boolean showRecentSection,
    boolean showFrequentSection,
    boolean showContextHints,
    boolean compactRows,
    boolean showDescriptions,
    int maxResults,
    int exampleQueryCount
) {
    public CommandPaletteDisplayPolicy {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        heightBand = heightBand == null ? NavSurfaceHeightBand.LOW_HEIGHT : heightBand;
        densityMode = densityMode == null ? UiLayoutMode.COMFORTABLE : densityMode;
        preferredViewMode = preferredViewMode == null ? CommandPaletteViewMode.GUIDED : preferredViewMode;
        maxResults = Math.max(5, maxResults);
        exampleQueryCount = Math.max(0, exampleQueryCount);
    }

    public boolean heightCompactionApplied() {
        return heightBand.isLowHeight() || compactRows || !showDescriptions;
    }
}
