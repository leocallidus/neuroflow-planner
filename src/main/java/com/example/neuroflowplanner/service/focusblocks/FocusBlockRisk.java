package com.example.neuroflowplanner.service.focusblocks;

public record FocusBlockRisk(
        FocusBlockRiskLevel level,
        String title,
        String detail) {

    public FocusBlockRisk {
        level = level == null ? FocusBlockRiskLevel.INFO : level;
        title = title == null ? "" : title.trim();
        detail = detail == null ? "" : detail.trim();
    }

    public boolean available() {
        return !title.isBlank() || !detail.isBlank();
    }
}
