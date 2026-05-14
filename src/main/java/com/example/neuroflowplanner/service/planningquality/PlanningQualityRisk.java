package com.example.neuroflowplanner.service.planningquality;

public record PlanningQualityRisk(
        PlanningQualityRiskSeverity severity,
        String title,
        String detail) {

    public PlanningQualityRisk {
        severity = severity == null ? PlanningQualityRiskSeverity.INFO : severity;
        title = title == null ? "" : title.trim();
        detail = detail == null ? "" : detail.trim();
    }
}
