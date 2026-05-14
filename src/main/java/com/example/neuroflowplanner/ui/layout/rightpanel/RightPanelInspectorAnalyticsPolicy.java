package com.example.neuroflowplanner.ui.layout.rightpanel;

/**
 * Rules for composing AI insight and critical-path content in a single analytics inspector tab.
 */
public record RightPanelInspectorAnalyticsPolicy(
    boolean aiSummaryPrimary,
    boolean pathSummarySecondary,
    boolean suppressDuplicateSummary,
    boolean pathMetricsStandaloneFallback
) {
    public static RightPanelInspectorAnalyticsPolicy defaults() {
        return new RightPanelInspectorAnalyticsPolicy(
            true,
            true,
            true,
            true
        );
    }
}
