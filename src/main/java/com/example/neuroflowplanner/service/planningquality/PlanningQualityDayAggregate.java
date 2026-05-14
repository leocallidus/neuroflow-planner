package com.example.neuroflowplanner.service.planningquality;

import java.time.LocalDate;

public record PlanningQualityDayAggregate(
        LocalDate date,
        int scheduledTaskCount,
        int completedTaskCount,
        int trackedSessionCount,
        long trackedMinutes,
        boolean overloaded,
        boolean emptyWorkday,
        boolean approximate) {

    public PlanningQualityDayAggregate {
        date = date == null ? LocalDate.now() : date;
        scheduledTaskCount = Math.max(0, scheduledTaskCount);
        completedTaskCount = Math.max(0, completedTaskCount);
        trackedSessionCount = Math.max(0, trackedSessionCount);
        trackedMinutes = Math.max(0L, trackedMinutes);
    }
}
