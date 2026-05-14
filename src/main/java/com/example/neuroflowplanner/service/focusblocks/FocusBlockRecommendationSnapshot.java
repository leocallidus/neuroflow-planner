package com.example.neuroflowplanner.service.focusblocks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FocusBlockRecommendationSnapshot(
        LocalDate reviewDate,
        Instant generatedAt,
        FocusBlockExplanation explanation,
        FocusProductivityProfile productivityProfile,
        List<FocusBlockCandidate> candidateWindows,
        List<FocusBlockRecommendation> focusWindows,
        List<FocusBlockRecommendation> shortWindows,
        FocusBlockRecommendation nextRecommendedBlock,
        List<FocusBlockRisk> risks,
        boolean limitedHistory) {

    public FocusBlockRecommendationSnapshot {
        reviewDate = reviewDate == null ? LocalDate.now() : reviewDate;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        explanation = explanation == null ? FocusBlockExplanation.unavailable() : explanation;
        productivityProfile = productivityProfile == null ? FocusProductivityProfile.unavailable() : productivityProfile;
        candidateWindows = candidateWindows == null ? List.of() : List.copyOf(candidateWindows);
        focusWindows = focusWindows == null ? List.of() : List.copyOf(focusWindows);
        shortWindows = shortWindows == null ? List.of() : List.copyOf(shortWindows);
        nextRecommendedBlock = nextRecommendedBlock == null
                ? FocusBlockRecommendation.unavailable()
                : nextRecommendedBlock;
        risks = risks == null ? List.of() : List.copyOf(risks);
    }

    public boolean hasCandidateWindows() {
        return !candidateWindows.isEmpty();
    }

    public boolean hasFocusWindows() {
        return !focusWindows.isEmpty();
    }

    public boolean hasShortWindows() {
        return !shortWindows.isEmpty();
    }

    public boolean hasRisks() {
        return !risks.isEmpty();
    }
}
