package com.example.neuroflowplanner.model.search;

public record GlobalSearchResult(
    GlobalSearchResultType type,
    String id,
    String title,
    String snippet,
    double score,
    GlobalSearchNavigationTarget navigationTarget
) {
    public GlobalSearchResult {
        type = type == null ? GlobalSearchResultType.NOTE : type;
        id = normalize(id);
        title = normalize(title);
        snippet = normalize(snippet);
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0) {
            score = 0;
        }
        navigationTarget = navigationTarget == null
            ? new GlobalSearchNavigationTarget(type, id, title)
            : navigationTarget;
    }

    public static GlobalSearchResult task(
        String taskId,
        String title,
        String snippet,
        double score
    ) {
        return new GlobalSearchResult(
            GlobalSearchResultType.TASK,
            taskId,
            title,
            snippet,
            score,
            new GlobalSearchNavigationTarget(GlobalSearchResultType.TASK, taskId, title)
        );
    }

    public static GlobalSearchResult note(
        String noteTitle,
        String snippet,
        double score
    ) {
        return new GlobalSearchResult(
            GlobalSearchResultType.NOTE,
            noteTitle,
            noteTitle,
            snippet,
            score,
            new GlobalSearchNavigationTarget(GlobalSearchResultType.NOTE, noteTitle, noteTitle)
        );
    }

    public boolean isTask() {
        return type == GlobalSearchResultType.TASK;
    }

    public boolean isNote() {
        return type == GlobalSearchResultType.NOTE;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
