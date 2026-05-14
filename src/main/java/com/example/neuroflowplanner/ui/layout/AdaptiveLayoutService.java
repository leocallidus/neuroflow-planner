package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;

/**
 * Source of truth for adaptive layout breakpoint resolution and persisted layout state.
 */
public final class AdaptiveLayoutService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(AdaptiveLayoutService.class);

    public UiLayoutState loadState() {
        return new UiLayoutState(
            UiLayoutBreakpoint.NORMAL,
            UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode()),
            ConfigManager.isUxLayoutStateLeftPanelCollapsed(),
            ConfigManager.isUxLayoutStateRightPanelCollapsed(),
            ConfigManager.getUxLayoutStateLeftPanelWidth(),
            ConfigManager.getUxLayoutStateRightPanelWidth()
        );
    }

    public void saveState(UiLayoutState state) {
        UiLayoutState safeState = state == null ? UiLayoutState.defaults() : state;
        ConfigManager.setUxLayoutDensityMode(safeState.densityMode().configValue());
        ConfigManager.setUxLayoutStateLeftPanelCollapsed(safeState.leftPanelCollapsed());
        ConfigManager.setUxLayoutStateRightPanelCollapsed(safeState.rightPanelCollapsed());
        ConfigManager.setUxLayoutStateLeftPanelWidth(safeState.leftPanelWidth());
        ConfigManager.setUxLayoutStateRightPanelWidth(safeState.rightPanelWidth());
    }

    public UiLayoutBreakpoint resolveBreakpoint(double windowWidthPx) {
        return UiLayoutBreakpoint.fromWidth(windowWidthPx);
    }

    public UiLayoutState applyWindowWidthPolicy(UiLayoutState state, double windowWidthPx) {
        UiLayoutState safeState = state == null ? loadState() : state;
        UiLayoutBreakpoint previous = safeState.breakpoint();
        UiLayoutBreakpoint resolved = resolveBreakpoint(windowWidthPx);
        UiLayoutState updated = safeState.withBreakpoint(resolved);

        if (resolved.isCompact() && !updated.rightPanelCollapsed()) {
            updated = updated.withRightPanelCollapsed(true);
            LOG.info(
                "ux.layout.panel.toggled",
                "panel", "right",
                "collapsed", true,
                "reason", "compact_auto_policy",
                "breakpoint", resolved.name()
            );
        }

        if (previous != updated.breakpoint()) {
            LOG.info(
                "ux.layout.breakpoint.changed",
                "from", previous.name(),
                "to", updated.breakpoint().name(),
                "windowWidthPx", windowWidthPx
            );
        }
        return updated;
    }

    public UiLayoutState updateDensityMode(UiLayoutState state, UiLayoutMode mode) {
        UiLayoutState safeState = state == null ? loadState() : state;
        UiLayoutMode normalizedMode = mode == null ? UiLayoutMode.resolve(ConfigManager.getUxLayoutDensityMode()) : mode;
        UiLayoutState updated = safeState.withDensityMode(normalizedMode);
        if (safeState.densityMode() != updated.densityMode()) {
            LOG.info(
                "ux.layout.mode.changed",
                "from", safeState.densityMode().name(),
                "to", updated.densityMode().name()
            );
        }
        saveState(updated);
        return updated;
    }

    public UiLayoutState updatePanelCollapsedState(
        UiLayoutState state,
        boolean leftPanelCollapsed,
        boolean rightPanelCollapsed
    ) {
        UiLayoutState safeState = state == null ? loadState() : state;
        UiLayoutState updated = safeState
            .withLeftPanelCollapsed(leftPanelCollapsed)
            .withRightPanelCollapsed(rightPanelCollapsed);

        if (safeState.leftPanelCollapsed() != updated.leftPanelCollapsed()) {
            LOG.info(
                "ux.layout.panel.toggled",
                "panel", "left",
                "collapsed", updated.leftPanelCollapsed(),
                "reason", "user_toggle"
            );
        }
        if (safeState.rightPanelCollapsed() != updated.rightPanelCollapsed()) {
            LOG.info(
                "ux.layout.panel.toggled",
                "panel", "right",
                "collapsed", updated.rightPanelCollapsed(),
                "reason", "user_toggle"
            );
        }
        saveState(updated);
        return updated;
    }

    public UiLayoutState updatePanelWidths(UiLayoutState state, double leftPanelWidth, double rightPanelWidth) {
        UiLayoutState safeState = state == null ? loadState() : state;
        UiLayoutState updated = safeState.withPanelWidths(leftPanelWidth, rightPanelWidth);
        saveState(updated);
        return updated;
    }

}
