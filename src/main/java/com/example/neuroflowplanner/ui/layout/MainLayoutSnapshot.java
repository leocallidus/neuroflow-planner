package com.example.neuroflowplanner.ui.layout;

import com.example.neuroflowplanner.ui.layout.rightpanel.RightPanelInspectorTab;

/**
 * Immutable view of current shell layout state.
 */
public record MainLayoutSnapshot(
    UiLayoutState state,
    UiRightContextMode rightContextMode,
    RightPanelInspectorTab rightInspectorActiveTab
) {
    public MainLayoutSnapshot {
        state = state == null ? UiLayoutState.defaults() : state;
        rightContextMode = rightContextMode == null ? UiRightContextMode.COLLAPSIBLE : rightContextMode;
        rightInspectorActiveTab = rightInspectorActiveTab == null ? RightPanelInspectorTab.PROPERTIES : rightInspectorActiveTab;
    }
}
