package com.example.neuroflowplanner.ui.layout.rightpanel;

/**
 * Content priority bucket for right-panel adaptive degradation policy.
 */
public enum RightPanelSectionPriority {
    PRIMARY,
    SECONDARY,
    TERTIARY;

    public boolean isPrimary() {
        return this == PRIMARY;
    }

    public boolean isTertiary() {
        return this == TERTIARY;
    }
}
