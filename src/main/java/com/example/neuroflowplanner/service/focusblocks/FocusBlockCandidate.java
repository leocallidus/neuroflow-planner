package com.example.neuroflowplanner.service.focusblocks;

import java.time.LocalDateTime;
import java.util.List;

public record FocusBlockCandidate(
        String label,
        LocalDateTime startAt,
        LocalDateTime endAt,
        long durationMinutes,
        FocusBlockType type,
        double suitabilityScore,
        double confidence,
        boolean approximate,
        List<FocusBlockReason> reasons) {

    public FocusBlockCandidate {
        label = label == null ? "" : label.trim();
        durationMinutes = Math.max(0L, durationMinutes);
        type = type == null ? FocusBlockType.LIGHT_FOCUS : type;
        suitabilityScore = clamp(suitabilityScore);
        confidence = clamp(confidence);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean available() {
        return startAt != null && endAt != null && durationMinutes > 0;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
