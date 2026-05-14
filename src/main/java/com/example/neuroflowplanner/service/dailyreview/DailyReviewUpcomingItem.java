package com.example.neuroflowplanner.service.dailyreview;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DailyReviewUpcomingItem(
        String taskId,
        String title,
        LocalDate deadlineDate,
        LocalDateTime deadlineDateTime,
        int daysUntilDue,
        boolean dueToday,
        boolean urgent,
        int complexity,
        double smartPriority,
        List<String> tags) {

    public DailyReviewUpcomingItem {
        taskId = taskId == null ? "" : taskId.trim();
        title = title == null ? "" : title.trim();
        daysUntilDue = Math.max(0, daysUntilDue);
        complexity = Math.max(0, complexity);
        smartPriority = Math.max(0.0, smartPriority);
        tags = tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .toList();
    }
}
