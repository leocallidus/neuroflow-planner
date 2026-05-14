package com.example.neuroflowplanner.service.planningquality;

public record PlanningQualityRecommendation(
        String title,
        String detail,
        String action,
        PlanningQualitySummarySource source) {

    public PlanningQualityRecommendation {
        title = title == null ? "" : title.trim();
        detail = detail == null ? "" : detail.trim();
        action = action == null ? "" : action.trim();
        source = source == null ? PlanningQualitySummarySource.UNAVAILABLE : source;
    }

    public static PlanningQualityRecommendation unavailable() {
        return new PlanningQualityRecommendation(
                "",
                "",
                "",
                PlanningQualitySummarySource.UNAVAILABLE
        );
    }

    public boolean available() {
        return source != PlanningQualitySummarySource.UNAVAILABLE
                && (!title.isBlank() || !detail.isBlank() || !action.isBlank());
    }
}
