package com.example.neuroflowplanner.service.focusblocks;

import java.time.LocalDate;
import java.util.List;

public record FocusBlockRecommendationEngineInput(
        LocalDate reviewDate,
        FocusProductivityProfile productivityProfile,
        List<FocusBlockCandidate> candidateWindows,
        int activeTaskCount,
        int overdueTaskCount,
        int upcomingTaskCount) {

    public FocusBlockRecommendationEngineInput {
        reviewDate = reviewDate == null ? LocalDate.now() : reviewDate;
        productivityProfile = productivityProfile == null
                ? FocusProductivityProfile.unavailable()
                : productivityProfile;
        candidateWindows = candidateWindows == null ? List.of() : List.copyOf(candidateWindows);
        activeTaskCount = Math.max(0, activeTaskCount);
        overdueTaskCount = Math.max(0, overdueTaskCount);
        upcomingTaskCount = Math.max(0, upcomingTaskCount);
    }
}
