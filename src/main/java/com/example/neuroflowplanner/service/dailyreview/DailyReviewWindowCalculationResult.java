package com.example.neuroflowplanner.service.dailyreview;

import java.util.List;

public record DailyReviewWindowCalculationResult(
        List<DailyReviewFreeWindow> freeWindows,
        boolean approximate) {

    public DailyReviewWindowCalculationResult {
        freeWindows = freeWindows == null ? List.of() : List.copyOf(freeWindows);
    }
}
