package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyReviewOverdueItem(
        String taskId,
        String title,
        LocalDate deadlineDate,
        LocalDateTime deadlineDateTime,
        int overdueDays,
        int complexity,
        double smartPriority,
        List<String> tags) {

    public DailyReviewOverdueItem {
        taskId = taskId == null ? "" : taskId.trim();
        title = title == null ? "" : title.trim();
        overdueDays = Math.max(0, overdueDays);
        complexity = Math.max(0, complexity);
        smartPriority = Math.max(0.0, smartPriority);
        tags = tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .toList();
    }
}
