package com.example.neuroflowplanner.service.planningquality;

public record RescheduleRateMetric(
        int analyzedTaskCount,
        int rescheduledTaskCount,
        int untouchedTaskCount,
        int multipleRescheduleCount,
        int lateRescheduleCount,
        double rescheduleRate,
        boolean approximate) {

    public RescheduleRateMetric {
        analyzedTaskCount = Math.max(0, analyzedTaskCount);
        rescheduledTaskCount = Math.max(0, rescheduledTaskCount);
        untouchedTaskCount = Math.max(0, untouchedTaskCount);
        multipleRescheduleCount = Math.max(0, multipleRescheduleCount);
        lateRescheduleCount = Math.max(0, lateRescheduleCount);
        rescheduleRate = Math.max(0.0, Math.min(1.0, Double.isFinite(rescheduleRate) ? rescheduleRate : 0.0));
    }

    public static RescheduleRateMetric unavailable() {
        return new RescheduleRateMetric(0, 0, 0, 0, 0, 0.0, true);
    }

    public boolean available() {
        return analyzedTaskCount > 0;
    }
}
