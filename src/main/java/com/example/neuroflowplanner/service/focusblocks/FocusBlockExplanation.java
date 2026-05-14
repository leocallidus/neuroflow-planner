package com.example.neuroflowplanner.service.focusblocks;

public record FocusBlockExplanation(
        FocusBlockSummarySource source,
        String headline,
        String summary,
        String nextAction,
        String limitations) {

    public FocusBlockExplanation {
        source = source == null ? FocusBlockSummarySource.UNAVAILABLE : source;
        headline = headline == null ? "" : headline.trim();
        summary = summary == null ? "" : summary.trim();
        nextAction = nextAction == null ? "" : nextAction.trim();
        limitations = limitations == null ? "" : limitations.trim();
    }

    public static FocusBlockExplanation unavailable() {
        return new FocusBlockExplanation(
                FocusBlockSummarySource.UNAVAILABLE,
                "",
                "",
                "",
                "Рекомендации фокус-блоков ещё не рассчитаны."
        );
    }

    public boolean available() {
        return source != FocusBlockSummarySource.UNAVAILABLE
                && (!headline.isBlank() || !summary.isBlank() || !nextAction.isBlank());
    }
}
