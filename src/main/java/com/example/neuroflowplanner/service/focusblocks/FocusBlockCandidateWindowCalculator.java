package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewFreeWindow;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewTimeBlock;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowCalculationResult;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowCalculator;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowSuitability;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkHoursProvider;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkInterval;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class FocusBlockCandidateWindowCalculator {

    private static final int MAX_CANDIDATE_WINDOWS = 8;
    private static final int DEFAULT_SCHEDULE_BLOCK_MINUTES = 60;
    private static final int FALLBACK_PADDING_MINUTES = 60;
    private static final LocalTime FALLBACK_DAY_START = LocalTime.of(9, 0);
    private static final LocalTime FALLBACK_DAY_END = LocalTime.of(18, 0);
    private static final LocalTime EARLIEST_APPROXIMATE_START = LocalTime.of(6, 0);
    private static final LocalTime LATEST_APPROXIMATE_END = LocalTime.of(23, 0);
    private static final DateTimeFormatter TIME_LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TaskApplicationService taskApplicationService;
    private final Supplier<List<TimeSession>> timeSessionsSupplier;
    private final DailyReviewWorkHoursProvider workHoursProvider;
    private final DailyReviewWindowCalculator windowCalculator;

    public FocusBlockCandidateWindowCalculator() {
        this(
                new DefaultTaskApplicationService(),
                () -> DatabaseManager.getInstance().loadTimeSessions(),
                DailyReviewWorkHoursProvider.defaultProvider(),
                new DailyReviewWindowCalculator()
        );
    }

    FocusBlockCandidateWindowCalculator(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            DailyReviewWorkHoursProvider workHoursProvider,
            DailyReviewWindowCalculator windowCalculator) {
        this.taskApplicationService = taskApplicationService;
        this.timeSessionsSupplier = timeSessionsSupplier;
        this.workHoursProvider = workHoursProvider;
        this.windowCalculator = windowCalculator == null ? new DailyReviewWindowCalculator() : windowCalculator;
    }

    public List<FocusBlockCandidate> calculateForDate(LocalDate reviewDate) {
        LocalDate effectiveDate = reviewDate == null ? LocalDate.now() : reviewDate;
        List<Task> activeTasks = flattenTasks(taskApplicationService.loadTasks()).stream()
                .filter(task -> task != null && !task.isArchived() && !task.isCompleted())
                .toList();
        List<TimeSession> timeSessions = safeTimeSessions();
        Map<String, Task> tasksById = indexById(activeTasks);
        List<DailyReviewTimeBlock> knownTimeBlocks = buildKnownTimeBlocks(activeTasks, timeSessions, tasksById, effectiveDate);
        ResolvedWorkIntervals resolvedWorkIntervals = resolveWorkIntervals(effectiveDate, knownTimeBlocks);
        List<DailyReviewWorkInterval> workIntervals = resolvedWorkIntervals.intervals();
        boolean approximateDataset = resolvedWorkIntervals.approximate();

        DailyReviewWindowCalculationResult result = windowCalculator.calculate(workIntervals, knownTimeBlocks);
        return result.freeWindows().stream()
                .map(window -> toCandidate(window, approximateDataset || result.approximate()))
                .filter(FocusBlockCandidate::available)
                .sorted(Comparator
                        .comparing(FocusBlockCandidate::startAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparingDouble(FocusBlockCandidate::suitabilityScore).reversed()))
                .limit(MAX_CANDIDATE_WINDOWS)
                .toList();
    }

    private List<TimeSession> safeTimeSessions() {
        List<TimeSession> sessions = timeSessionsSupplier == null ? List.of() : timeSessionsSupplier.get();
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    private ResolvedWorkIntervals resolveWorkIntervals(LocalDate reviewDate, List<DailyReviewTimeBlock> knownTimeBlocks) {
        List<DailyReviewWorkInterval> configured = workHoursProvider == null
                ? List.of()
                : List.copyOf(workHoursProvider.getWorkIntervals(reviewDate));
        if (!configured.isEmpty()) {
            return new ResolvedWorkIntervals(configured, false);
        }
        DailyReviewWorkInterval approximate = buildApproximateInterval(reviewDate, knownTimeBlocks);
        return approximate == null
                ? new ResolvedWorkIntervals(List.of(), true)
                : new ResolvedWorkIntervals(List.of(approximate), true);
    }

    private DailyReviewWorkInterval buildApproximateInterval(LocalDate reviewDate, List<DailyReviewTimeBlock> knownTimeBlocks) {
        if (reviewDate == null) {
            return null;
        }
        if (knownTimeBlocks == null || knownTimeBlocks.isEmpty()) {
            LocalDateTime start = LocalDateTime.of(reviewDate, FALLBACK_DAY_START);
            LocalDateTime end = LocalDateTime.of(reviewDate, FALLBACK_DAY_END);
            return new DailyReviewWorkInterval(
                    start,
                    end,
                    (int) java.time.Duration.between(start, end).toMinutes(),
                    true,
                    "Оценочно " + start.toLocalTime().format(TIME_LABEL_FORMAT) + "-" + end.toLocalTime().format(TIME_LABEL_FORMAT)
            );
        }

        LocalDateTime earliest = knownTimeBlocks.stream()
                .map(DailyReviewTimeBlock::start)
                .filter(value -> value != null && reviewDate.equals(value.toLocalDate()))
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.of(reviewDate, FALLBACK_DAY_START));
        LocalDateTime latest = knownTimeBlocks.stream()
                .map(DailyReviewTimeBlock::end)
                .filter(value -> value != null && reviewDate.equals(value.toLocalDate()))
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.of(reviewDate, FALLBACK_DAY_END));

        LocalDateTime start = earliest.minusMinutes(FALLBACK_PADDING_MINUTES);
        LocalDateTime end = latest.plusMinutes(FALLBACK_PADDING_MINUTES);
        LocalDateTime earliestAllowed = LocalDateTime.of(reviewDate, EARLIEST_APPROXIMATE_START);
        LocalDateTime latestAllowed = LocalDateTime.of(reviewDate, LATEST_APPROXIMATE_END);
        if (start.isBefore(earliestAllowed)) {
            start = earliestAllowed;
        }
        if (end.isAfter(latestAllowed)) {
            end = latestAllowed;
        }
        if (!end.isAfter(start)) {
            start = LocalDateTime.of(reviewDate, FALLBACK_DAY_START);
            end = LocalDateTime.of(reviewDate, FALLBACK_DAY_END);
        }
        return new DailyReviewWorkInterval(
                start,
                end,
                (int) java.time.Duration.between(start, end).toMinutes(),
                true,
                "Оценочно " + start.toLocalTime().format(TIME_LABEL_FORMAT) + "-" + end.toLocalTime().format(TIME_LABEL_FORMAT)
        );
    }

    private List<DailyReviewTimeBlock> buildKnownTimeBlocks(
            List<Task> activeTasks,
            List<TimeSession> timeSessions,
            Map<String, Task> tasksById,
            LocalDate reviewDate) {
        List<DailyReviewTimeBlock> blocks = new ArrayList<>();

        for (Task task : activeTasks) {
            LocalDateTime start = task.getStartDateTime();
            if (start == null || !reviewDate.equals(start.toLocalDate())) {
                continue;
            }
            LocalDateTime end = task.getDeadlineDateTime();
            boolean approximate = false;
            if (end == null || !reviewDate.equals(end.toLocalDate()) || !end.isAfter(start)) {
                end = start.plusMinutes(DEFAULT_SCHEDULE_BLOCK_MINUTES);
                approximate = true;
            }
            blocks.add(new DailyReviewTimeBlock(
                    task.getId(),
                    task.getTitle(),
                    start,
                    end,
                    Math.max(0, (int) java.time.Duration.between(start, end).toMinutes()),
                    "task_schedule",
                    approximate
            ));
        }

        for (TimeSession session : timeSessions) {
            if (session == null || session.getStartedAt() == null) {
                continue;
            }
            if (!reviewDate.equals(session.getStartedAt().toLocalDate())) {
                continue;
            }
            LocalDateTime start = session.getStartedAt();
            LocalDateTime end = start.plusMinutes(Math.max(0L, session.getMinutes()));
            Task task = tasksById.get(session.getTaskId());
            String title = task != null ? task.getTitle() : "Фокус-сессия";
            blocks.add(new DailyReviewTimeBlock(
                    session.getTaskId(),
                    title,
                    start,
                    end,
                    Math.max(0, (int) session.getMinutes()),
                    "time_session",
                    false
            ));
        }

        return blocks.stream()
                .sorted(Comparator
                        .comparing(DailyReviewTimeBlock::start, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DailyReviewTimeBlock::end, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DailyReviewTimeBlock::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private FocusBlockCandidate toCandidate(DailyReviewFreeWindow window, boolean approximateDataset) {
        FocusBlockType type = switch (window.suitability()) {
            case DEEP_WORK -> FocusBlockType.DEEP_FOCUS;
            case SHORT_WORK -> FocusBlockType.LIGHT_FOCUS;
            case FLEXIBLE -> FocusBlockType.ADMIN;
            case UNKNOWN -> FocusBlockType.LIGHT_FOCUS;
        };
        double suitabilityScore = switch (window.suitability()) {
            case DEEP_WORK -> 0.84;
            case SHORT_WORK -> 0.62;
            case FLEXIBLE -> 0.44;
            case UNKNOWN -> 0.30;
        };
        double confidence = approximateDataset || window.approximate() ? 0.48 : 0.68;
        List<FocusBlockReason> reasons = new ArrayList<>();
        reasons.add(new FocusBlockReason(
                type == FocusBlockType.DEEP_FOCUS ? "Подходит для фокуса" : "Доступное рабочее окно",
                buildSuitabilityDetail(window)
        ));
        if (approximateDataset || window.approximate()) {
            reasons.add(new FocusBlockReason(
                    "Ограниченные данные",
                    "Окно рассчитано приблизительно, потому что рабочие часы или занятые интервалы заданы не полностью."
            ));
        }
        return new FocusBlockCandidate(
                window.label(),
                window.start(),
                window.end(),
                window.durationMinutes(),
                type,
                suitabilityScore,
                confidence,
                approximateDataset || window.approximate(),
                reasons
        );
    }

    private String buildSuitabilityDetail(DailyReviewFreeWindow window) {
        return switch (window.suitability()) {
            case DEEP_WORK -> "Длинное чистое окно " + window.durationMinutes() + " мин, подходит для глубокой работы.";
            case SHORT_WORK -> "Окно " + window.durationMinutes() + " мин подходит для целевой короткой сессии.";
            case FLEXIBLE -> "Короткое окно " + window.durationMinutes() + " мин лучше использовать для лёгких задач.";
            case UNKNOWN -> "Окно доступно, но его пригодность пока оценена приблизительно.";
        };
    }

    private Map<String, Task> indexById(List<Task> tasks) {
        Map<String, Task> tasksById = new HashMap<>();
        for (Task task : tasks) {
            if (task != null && task.getId() != null && !task.getId().isBlank()) {
                tasksById.put(task.getId(), task);
            }
        }
        return tasksById;
    }

    private List<Task> flattenTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Task> flattened = new ArrayList<>();
        Deque<Task> stack = new ArrayDeque<>(tasks);
        while (!stack.isEmpty()) {
            Task task = stack.removeFirst();
            if (task == null) {
                continue;
            }
            flattened.add(task);
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                for (Task subtask : task.getSubtasks()) {
                    stack.addLast(subtask);
                }
            }
        }
        return flattened;
    }

    private record ResolvedWorkIntervals(List<DailyReviewWorkInterval> intervals, boolean approximate) {
    }
}
