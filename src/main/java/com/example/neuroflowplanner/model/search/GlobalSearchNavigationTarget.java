package com.example.neuroflowplanner.model.search;

public record GlobalSearchNavigationTarget(
    GlobalSearchResultType type,
    String targetId,
    String targetLabel
) {
    public GlobalSearchNavigationTarget {
        type = type == null ? GlobalSearchResultType.NOTE : type;
        targetId = normalize(targetId);
        targetLabel = normalize(targetLabel);
    }

    public boolean isEmpty() {
        return targetId.isBlank();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
