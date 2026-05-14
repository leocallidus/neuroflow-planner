package com.example.neuroflowplanner.service.dailyreview;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DailyReviewWindowCalculator {

    static final int MIN_FREE_WINDOW_MINUTES = 20;
    private static final int MAX_FREE_WINDOWS = 8;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public DailyReviewWindowCalculationResult calculate(
            List<DailyReviewWorkInterval> workIntervals,
            List<DailyReviewTimeBlock> knownTimeBlocks) {
        List<DailyReviewWorkInterval> intervals = workIntervals == null ? List.of() : List.copyOf(workIntervals);
        List<DailyReviewTimeBlock> blocks = knownTimeBlocks == null ? List.of() : List.copyOf(knownTimeBlocks);
        if (intervals.isEmpty()) {
            return new DailyReviewWindowCalculationResult(List.of(), true);
        }

        List<DailyReviewFreeWindow> windows = new ArrayList<>();
        boolean approximate = blocks.stream().anyMatch(DailyReviewTimeBlock::approximate);

        for (DailyReviewWorkInterval interval : intervals) {
            if (interval == null || interval.start() == null || interval.end() == null || !interval.end().isAfter(interval.start())) {
                continue;
            }
            List<LocalInterval> busy = blocks.stream()
                    .filter(block -> intersects(interval, block))
                    .map(block -> clamp(interval, block))
                    .filter(local -> local != null && local.end().isAfter(local.start()))
                    .sorted(Comparator.comparing(LocalInterval::start))
                    .toList();

            List<LocalInterval> mergedBusy = merge(busy);
            LocalDateTime cursor = interval.start();

            for (LocalInterval occupied : mergedBusy) {
                if (occupied.start().isAfter(cursor)) {
                    DailyReviewFreeWindow freeWindow = toFreeWindow(cursor, occupied.start(), approximate);
                    if (freeWindow != null) {
                        windows.add(freeWindow);
                    }
                }
                if (occupied.end().isAfter(cursor)) {
                    cursor = occupied.end();
                }
            }

            if (interval.end().isAfter(cursor)) {
                DailyReviewFreeWindow tail = toFreeWindow(cursor, interval.end(), approximate);
                if (tail != null) {
                    windows.add(tail);
                }
            }
        }

        return new DailyReviewWindowCalculationResult(
                windows.stream()
                        .sorted(Comparator.comparing(DailyReviewFreeWindow::start))
                        .limit(MAX_FREE_WINDOWS)
                        .toList(),
                approximate
        );
    }

    private boolean intersects(DailyReviewWorkInterval interval, DailyReviewTimeBlock block) {
        return block != null
                && block.start() != null
                && block.end() != null
                && block.end().isAfter(interval.start())
                && block.start().isBefore(interval.end());
    }

    private LocalInterval clamp(DailyReviewWorkInterval interval, DailyReviewTimeBlock block) {
        LocalDateTime start = block.start().isAfter(interval.start()) ? block.start() : interval.start();
        LocalDateTime end = block.end().isBefore(interval.end()) ? block.end() : interval.end();
        return end.isAfter(start) ? new LocalInterval(start, end) : null;
    }

    private List<LocalInterval> merge(List<LocalInterval> intervals) {
        if (intervals.isEmpty()) {
            return List.of();
        }
        List<LocalInterval> merged = new ArrayList<>();
        LocalInterval current = intervals.getFirst();
        for (int i = 1; i < intervals.size(); i++) {
            LocalInterval next = intervals.get(i);
            if (!next.start().isAfter(current.end())) {
                LocalDateTime maxEnd = next.end().isAfter(current.end()) ? next.end() : current.end();
                current = new LocalInterval(current.start(), maxEnd);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private DailyReviewFreeWindow toFreeWindow(LocalDateTime start, LocalDateTime end, boolean approximate) {
        if (start == null || end == null || !end.isAfter(start)) {
            return null;
        }
        int durationMinutes = Math.max(0, (int) Duration.between(start, end).toMinutes());
        if (durationMinutes < MIN_FREE_WINDOW_MINUTES) {
            return null;
        }
        return new DailyReviewFreeWindow(
                start,
                end,
                durationMinutes,
                classifySuitability(durationMinutes),
                approximate,
                start.format(TIME_FORMAT) + "-" + end.format(TIME_FORMAT)
        );
    }

    private DailyReviewWindowSuitability classifySuitability(int durationMinutes) {
        if (durationMinutes >= 90) {
            return DailyReviewWindowSuitability.DEEP_WORK;
        }
        if (durationMinutes >= 45) {
            return DailyReviewWindowSuitability.SHORT_WORK;
        }
        if (durationMinutes >= MIN_FREE_WINDOW_MINUTES) {
            return DailyReviewWindowSuitability.FLEXIBLE;
        }
        return DailyReviewWindowSuitability.UNKNOWN;
    }

    private record LocalInterval(LocalDateTime start, LocalDateTime end) {
    }
}
