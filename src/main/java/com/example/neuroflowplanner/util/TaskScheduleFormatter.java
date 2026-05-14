package com.example.neuroflowplanner.util;

import com.example.neuroflowplanner.model.Task;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class TaskScheduleFormatter {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private TaskScheduleFormatter() {
    }

    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "—";
        }
        return date.format(DATE_FORMATTER);
    }

    public static String formatDateTime(LocalDate date, LocalTime time) {
        if (date == null) {
            return "—";
        }
        if (time == null) {
            return formatDate(date);
        }
        return formatDate(date) + " " + time.format(TIME_FORMATTER);
    }

    public static String formatDeadline(Task task) {
        return task == null ? "—" : formatDateTime(task.getDeadline(), task.getDeadlineTime());
    }

    public static String formatStart(Task task) {
        return task == null ? "—" : formatDateTime(task.getStartDate(), task.getStartTime());
    }

    public static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER);
    }
}
