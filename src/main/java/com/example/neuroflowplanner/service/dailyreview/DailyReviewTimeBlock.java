package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDateTime;

public record DailyReviewTimeBlock(
        String taskId,
        String title,
        LocalDateTime start,
        LocalDateTime end,
        int durationMinutes,
        String source,
        boolean approximate) {

    public DailyReviewTimeBlock {
        taskId = taskId == null ? "" : taskId.trim();
        title = title == null ? "" : title.trim();
        durationMinutes = Math.max(0, durationMinutes);
        source = source == null ? "" : source.trim();
    }
}
