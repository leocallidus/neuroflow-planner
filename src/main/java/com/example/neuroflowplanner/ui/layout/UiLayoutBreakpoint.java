package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.util.UxConfigDefaults;

/**
 * Window-width buckets used by adaptive shell policy.
 */
public enum UiLayoutBreakpoint {
    COMPACT,
    NORMAL,
    WIDE;

    public static UiLayoutBreakpoint fromWidth(double windowWidthPx) {
        return fromWidth(
            windowWidthPx,
            UxConfigDefaults.UX_LAYOUT_BREAKPOINT_NORMAL_MIN_WIDTH,
            UxConfigDefaults.UX_LAYOUT_BREAKPOINT_WIDE_MIN_WIDTH
        );
    }

    public static UiLayoutBreakpoint fromWidth(double windowWidthPx, int normalMinWidth, int wideMinWidth) {
        if (!Double.isFinite(windowWidthPx) || windowWidthPx <= 0.0) {
            return NORMAL;
        }

        int safeNormalMin = Math.max(1, normalMinWidth);
        int safeWideMin = Math.max(safeNormalMin + 1, wideMinWidth);
        if (windowWidthPx >= safeWideMin) {
            return WIDE;
        }
        if (windowWidthPx >= safeNormalMin) {
            return NORMAL;
        }
        return COMPACT;
    }

    public boolean isCompact() {
        return this == COMPACT;
    }
}
