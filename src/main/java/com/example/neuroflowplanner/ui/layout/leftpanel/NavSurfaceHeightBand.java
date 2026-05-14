package com.example.neuroflowplanner.ui.layout.leftpanel;

/**
 * Height buckets for navigation surfaces (sidebar + command palette).
 */
public enum NavSurfaceHeightBand {
    TALL,
    LOW_HEIGHT,
    VERY_LOW_HEIGHT;

    public static NavSurfaceHeightBand fromHeight(double availableHeightPx) {
        if (!Double.isFinite(availableHeightPx) || availableHeightPx <= 0.0) {
            return LOW_HEIGHT;
        }
        if (availableHeightPx < 700.0) {
            return VERY_LOW_HEIGHT;
        }
        if (availableHeightPx < 850.0) {
            return LOW_HEIGHT;
        }
        return TALL;
    }

    public boolean isLowHeight() {
        return this == LOW_HEIGHT || this == VERY_LOW_HEIGHT;
    }

    public boolean isVeryLowHeight() {
        return this == VERY_LOW_HEIGHT;
    }
}
