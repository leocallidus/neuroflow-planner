package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.UxConfigDefaults;

/**
 * Persistable and immutable adaptive layout state.
 */
public record UiLayoutState(
    UiLayoutBreakpoint breakpoint,
    UiLayoutMode densityMode,
    boolean leftPanelCollapsed,
    boolean rightPanelCollapsed,
    double leftPanelWidth,
    double rightPanelWidth
) {
    public UiLayoutState {
        breakpoint = breakpoint == null ? UiLayoutBreakpoint.NORMAL : breakpoint;
        densityMode = densityMode == null ? UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT) : densityMode;
        leftPanelWidth = normalizeWidth(
            leftPanelWidth,
            UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT,
            UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MIN,
            UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_MAX
        );
        rightPanelWidth = normalizeWidth(
            rightPanelWidth,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MIN,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_MAX
        );
    }

    public static UiLayoutState defaults() {
        return new UiLayoutState(
            UiLayoutBreakpoint.NORMAL,
            UiLayoutMode.resolve(UxConfigDefaults.UX_LAYOUT_DENSITY_MODE_DEFAULT),
            UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_COLLAPSED_DEFAULT,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_COLLAPSED_DEFAULT,
            UxConfigDefaults.UX_LAYOUT_STATE_LEFT_PANEL_WIDTH_DEFAULT,
            UxConfigDefaults.UX_LAYOUT_STATE_RIGHT_PANEL_WIDTH_DEFAULT
        );
    }

    public UiLayoutState withBreakpoint(UiLayoutBreakpoint nextBreakpoint) {
        return new UiLayoutState(
            nextBreakpoint == null ? breakpoint : nextBreakpoint,
            densityMode,
            leftPanelCollapsed,
            rightPanelCollapsed,
            leftPanelWidth,
            rightPanelWidth
        );
    }

    public UiLayoutState withDensityMode(UiLayoutMode nextMode) {
        return new UiLayoutState(
            breakpoint,
            nextMode == null ? densityMode : nextMode,
            leftPanelCollapsed,
            rightPanelCollapsed,
            leftPanelWidth,
            rightPanelWidth
        );
    }

    public UiLayoutState withLeftPanelCollapsed(boolean collapsed) {
        return new UiLayoutState(
            breakpoint,
            densityMode,
            collapsed,
            rightPanelCollapsed,
            leftPanelWidth,
            rightPanelWidth
        );
    }

    public UiLayoutState withRightPanelCollapsed(boolean collapsed) {
        return new UiLayoutState(
            breakpoint,
            densityMode,
            leftPanelCollapsed,
            collapsed,
            leftPanelWidth,
            rightPanelWidth
        );
    }

    public UiLayoutState withPanelWidths(double nextLeftPanelWidth, double nextRightPanelWidth) {
        return new UiLayoutState(
            breakpoint,
            densityMode,
            leftPanelCollapsed,
            rightPanelCollapsed,
            nextLeftPanelWidth,
            nextRightPanelWidth
        );
    }

    private static double normalizeWidth(double value, double fallback, double min, double max) {
        if (!Double.isFinite(value)) {
            return fallback;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
