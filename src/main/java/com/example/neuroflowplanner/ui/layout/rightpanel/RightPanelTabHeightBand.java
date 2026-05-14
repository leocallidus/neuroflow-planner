package com.example.neuroflowplanner.ui.layout.rightpanel;

/**
 * Height compaction bands for tabbed inspector content policy.
 */
public enum RightPanelTabHeightBand {
    TALL,
    LOW_HEIGHT,
    VERY_LOW_HEIGHT;

    public boolean isLowHeight() {
        return this == LOW_HEIGHT || this == VERY_LOW_HEIGHT;
    }

    public boolean isVeryLowHeight() {
        return this == VERY_LOW_HEIGHT;
    }

    public static RightPanelTabHeightBand resolve(double availableHeightPx) {
        if (!Double.isFinite(availableHeightPx) || availableHeightPx <= 0.0) {
            return LOW_HEIGHT;
        }
        if (availableHeightPx <= 700.0) {
            return VERY_LOW_HEIGHT;
        }
        if (availableHeightPx <= 860.0) {
            return LOW_HEIGHT;
        }
        return TALL;
    }
}
