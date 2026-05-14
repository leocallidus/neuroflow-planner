package com.example.neuroflowplanner.service.focusblocks;

import java.util.List;

public record FocusBlockRecommendationEngineResult(
        List<FocusBlockRecommendation> focusWindows,
        List<FocusBlockRecommendation> shortWindows,
        FocusBlockRecommendation nextRecommendedBlock,
        List<FocusBlockRisk> risks) {

    public FocusBlockRecommendationEngineResult {
        focusWindows = focusWindows == null ? List.of() : List.copyOf(focusWindows);
        shortWindows = shortWindows == null ? List.of() : List.copyOf(shortWindows);
        nextRecommendedBlock = nextRecommendedBlock == null
                ? FocusBlockRecommendation.unavailable()
                : nextRecommendedBlock;
        risks = risks == null ? List.of() : List.copyOf(risks);
    }

    public static FocusBlockRecommendationEngineResult empty() {
        return new FocusBlockRecommendationEngineResult(
                List.of(),
                List.of(),
                FocusBlockRecommendation.unavailable(),
                List.of()
        );
    }
}
