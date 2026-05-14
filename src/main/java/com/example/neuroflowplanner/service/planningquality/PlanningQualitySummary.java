package com.example.neuroflowplanner.service.planningquality;

public record PlanningQualitySummary(
        PlanningQualitySummarySource source,
        String headline,
        String summary,
        String nextAction,
        String limitations) {

    public PlanningQualitySummary {
        source = source == null ? PlanningQualitySummarySource.UNAVAILABLE : source;
        headline = headline == null ? "" : headline.trim();
        summary = summary == null ? "" : summary.trim();
        nextAction = nextAction == null ? "" : nextAction.trim();
        limitations = limitations == null ? "" : limitations.trim();
    }

    public static PlanningQualitySummary unavailable() {
        return new PlanningQualitySummary(
                PlanningQualitySummarySource.UNAVAILABLE,
                "",
                "",
                "",
                "Сводка качества планирования ещё не рассчитана."
        );
    }

    public boolean available() {
        return source != PlanningQualitySummarySource.UNAVAILABLE
                && (!headline.isBlank() || !summary.isBlank() || !nextAction.isBlank());
    }
}
