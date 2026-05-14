package com.example.neuroflowplanner.service.focusblocks;

import java.time.LocalDateTime;
import java.util.List;

public record FocusBlockRecommendation(
        String title,
        String rationale,
        String nextStep,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long durationMinutes,
        FocusBlockType type,
        double suitabilityScore,
        double confidence,
        boolean primary,
        List<FocusBlockReason> reasons) {

    public FocusBlockRecommendation {
        title = title == null ? "" : title.trim();
        rationale = rationale == null ? "" : rationale.trim();
        nextStep = nextStep == null ? "" : nextStep.trim();
        durationMinutes = Math.max(0L, durationMinutes);
        type = type == null ? FocusBlockType.LIGHT_FOCUS : type;
        suitabilityScore = clamp(suitabilityScore);
        confidence = clamp(confidence);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static FocusBlockRecommendation unavailable() {
        return new FocusBlockRecommendation(
                "",
                "",
                "",
                null,
                null,
                0,
                FocusBlockType.LIGHT_FOCUS,
                0.0,
                0.0,
                true,
                List.of()
        );
    }

    public boolean available() {
        return startAt != null && endAt != null && durationMinutes > 0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
