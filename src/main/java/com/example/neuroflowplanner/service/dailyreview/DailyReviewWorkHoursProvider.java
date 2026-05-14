package com.example.neuroflowplanner.service.dailyreview;

import com.example.neuroflowplanner.ui.WorkHoursDialog;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@FunctionalInterface
public interface DailyReviewWorkHoursProvider {

    List<DailyReviewWorkInterval> getWorkIntervals(LocalDate reviewDate);

    static DailyReviewWorkHoursProvider defaultProvider() {
        return reviewDate -> {
            if (reviewDate == null) {
                return List.of();
            }
            int dayOfWeek = reviewDate.getDayOfWeek().getValue();
            if (!WorkHoursDialog.isWorkDay(dayOfWeek)) {
                return List.of();
            }
            LocalTime startTime = LocalTime.of(WorkHoursDialog.getStartHour(dayOfWeek), 0);
            LocalTime endTime = LocalTime.of(WorkHoursDialog.getEndHour(dayOfWeek), 0);
            LocalDateTime start = LocalDateTime.of(reviewDate, startTime);
            LocalDateTime end = LocalDateTime.of(reviewDate, endTime);
            int durationMinutes = Math.max(0, (int) java.time.Duration.between(start, end).toMinutes());
            if (durationMinutes <= 0) {
                return List.of();
            }
            String label = startTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    + "-" + endTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            return List.of(new DailyReviewWorkInterval(start, end, durationMinutes, true, label));
        };
    }
}
