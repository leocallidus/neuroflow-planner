package com.example.neuroflowplanner.service.dailyreview;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class DailyReviewBuilder {

    private static final int MAX_OVERDUE_ITEMS = 6;
    private static final int MAX_UPCOMING_ITEMS = 8;
    private static final int MAX_TIME_BLOCKS = 12;
    private static final int DEFAULT_SCHEDULE_BLOCK_MINUTES = 60;
    private static final DateTimeFormatter TIME_LABEL_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TaskApplicationService taskApplicationService;
    private final Supplier<List<TimeSession>> timeSessionsSupplier;
    private final DailyReviewWorkHoursProvider workHoursProvider;
    private final DailyReviewWindowCalculator windowCalculator;

    public DailyReviewBuilder() {
        this(
                new DefaultTaskApplicationService(),
                () -> DatabaseManager.getInstance().loadTimeSessions(),
                DailyReviewWorkHoursProvider.defaultProvider(),
                new DailyReviewWindowCalculator()
        );
    }

    DailyReviewBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            DailyReviewWorkHoursProvider workHoursProvider) {
        this(taskApplicationService, timeSessionsSupplier, workHoursProvider, new DailyReviewWindowCalculator());
    }

    DailyReviewBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            DailyReviewWorkHoursProvider workHoursProvider,
            DailyReviewWindowCalculator windowCalculator) {
        this.taskApplicationService = taskApplicationService;
        this.timeSessionsSupplier = timeSessionsSupplier;
        this.workHoursProvider = workHoursProvider;
        this.windowCalculator = windowCalculator == null ? new DailyReviewWindowCalculator() : windowCalculator;
    }

    public DailyReviewSnapshot buildForDate(LocalDate reviewDate) {
        LocalDate effectiveDate = reviewDate == null ? LocalDate.now() : reviewDate;
        List<Task> activeTasks = flattenTasks(taskApplicationService.loadTasks()).stream()
                .filter(task -> task != null && !task.isArchived() && !task.isCompleted())
                .toList();
        List<TimeSession> timeSessions = safeTimeSessions();
        Map<String, Task> tasksById = indexById(activeTasks);

        List<DailyReviewOverdueItem> overdueItems = buildOverdueItems(activeTasks, effectiveDate);
        List<DailyReviewUpcomingItem> upcomingItems = buildUpcomingItems(activeTasks, effectiveDate);
        List<DailyReviewWorkInterval> workIntervals = workHoursProvider == null
                ? List.of()
                : List.copyOf(workHoursProvider.getWorkIntervals(effectiveDate));
        List<DailyReviewTimeBlock> knownTimeBlocks = buildKnownTimeBlocks(activeTasks, timeSessions, tasksById, effectiveDate);
        DailyReviewWindowCalculationResult freeWindowResult = windowCalculator.calculate(workIntervals, knownTimeBlocks);
        long trackedMinutesToday = timeSessions.stream()
                .filter(session -> session != null && session.getStartedAt() != null
                        && effectiveDate.equals(session.getStartedAt().toLocalDate()))
                .mapToLong(TimeSession::getMinutes)
                .sum();

        DailyReviewSummary summary = buildFallbackSummary(
                activeTasks.size(),
                overdueItems,
                upcomingItems,
                workIntervals,
                knownTimeBlocks
        );
        DailyReviewFocusRecommendation focusRecommendation = buildFallbackFocusRecommendation(overdueItems, upcomingItems);

        return new DailyReviewSnapshot(
                effectiveDate,
                Instant.now(),
                activeTasks.size(),
                overdueItems.size(),
                (int) activeTasks.stream()
                        .filter(task -> effectiveDate.equals(task.getDeadline()))
                        .count(),
                upcomingItems.size(),
                trackedMinutesToday,
                freeWindowResult.approximate(),
                summary,
                overdueItems,
                upcomingItems,
                workIntervals,
                knownTimeBlocks,
                freeWindowResult.freeWindows(),
                focusRecommendation
        );
    }

    private List<TimeSession> safeTimeSessions() {
        List<TimeSession> loaded = timeSessionsSupplier == null ? List.of() : timeSessionsSupplier.get();
        return loaded == null ? List.of() : List.copyOf(loaded);
    }

    private List<DailyReviewOverdueItem> buildOverdueItems(List<Task> tasks, LocalDate reviewDate) {
        return tasks.stream()
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(reviewDate))
                .sorted(Comparator
                        .comparing((Task task) -> task.getDeadlineDateTime() == null
                                ? LocalDateTime.of(task.getDeadline(), java.time.LocalTime.MAX)
                                : task.getDeadlineDateTime())
                        .thenComparing(Comparator.comparingDouble(Task::getSmartPriority).reversed())
                        .thenComparing(Comparator.comparingInt(Task::getComplexity).reversed())
                        .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_OVERDUE_ITEMS)
                .map(task -> new DailyReviewOverdueItem(
                        task.getId(),
                        task.getTitle(),
                        task.getDeadline(),
                        task.getDeadlineDateTime(),
                        (int) Duration.between(task.getDeadline().atStartOfDay(), reviewDate.atStartOfDay()).toDays(),
                        task.getComplexity(),
                        task.getSmartPriority(),
                        parseTags(task)
                ))
                .toList();
    }

    private List<DailyReviewUpcomingItem> buildUpcomingItems(List<Task> tasks, LocalDate reviewDate) {
        LocalDate upperBound = reviewDate.plusDays(3);
        return tasks.stream()
                .filter(task -> task.getDeadline() != null
                        && !task.getDeadline().isBefore(reviewDate)
                        && !task.getDeadline().isAfter(upperBound))
                .sorted(Comparator
                        .comparing((Task task) -> task.getDeadlineDateTime() == null
                                ? LocalDateTime.of(task.getDeadline(), java.time.LocalTime.MAX)
                                : task.getDeadlineDateTime())
                        .thenComparing(Comparator.comparingDouble(Task::getSmartPriority).reversed())
                        .thenComparing(Comparator.comparingInt(Task::getComplexity).reversed())
                        .thenComparing(Task::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_UPCOMING_ITEMS)
                .map(task -> {
                    int daysUntilDue = (int) Duration.between(
                            reviewDate.atStartOfDay(),
                            task.getDeadline().atStartOfDay()
                    ).toDays();
                    return new DailyReviewUpcomingItem(
                            task.getId(),
                            task.getTitle(),
                            task.getDeadline(),
                            task.getDeadlineDateTime(),
                            daysUntilDue,
                            daysUntilDue == 0,
                            daysUntilDue <= 1,
                            task.getComplexity(),
                            task.getSmartPriority(),
                            parseTags(task)
                    );
                })
                .toList();
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
                    Math.max(0, (int) Duration.between(start, end).toMinutes()),
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
                .limit(MAX_TIME_BLOCKS)
                .toList();
    }

    private DailyReviewSummary buildFallbackSummary(
            int activeTaskCount,
            List<DailyReviewOverdueItem> overdueItems,
            List<DailyReviewUpcomingItem> upcomingItems,
            List<DailyReviewWorkInterval> workIntervals,
            List<DailyReviewTimeBlock> knownTimeBlocks) {
        String headline;
        if (!overdueItems.isEmpty()) {
            headline = "День под давлением дедлайнов";
        } else if (!upcomingItems.isEmpty()) {
            headline = "День с понятными приоритетами";
        } else if (activeTaskCount > 0) {
            headline = "Спокойный день для планового прогресса";
        } else {
            headline = "Активных задач на сегодня почти нет";
        }

        List<String> bullets = new ArrayList<>();
        if (!overdueItems.isEmpty()) {
            bullets.add("Просрочено задач: " + overdueItems.size() + ".");
        }
        if (!upcomingItems.isEmpty()) {
            long dueToday = upcomingItems.stream().filter(DailyReviewUpcomingItem::dueToday).count();
            if (dueToday > 0) {
                bullets.add("На сегодня запланировано дедлайнов: " + dueToday + ".");
            } else {
                bullets.add("В ближайшие дни есть " + upcomingItems.size() + " важных дедлайнов.");
            }
        }
        if (!workIntervals.isEmpty()) {
            bullets.add("Рабочее окно дня: " + workIntervals.getFirst().label() + ".");
        }
        if (!knownTimeBlocks.isEmpty()) {
            bullets.add("Уже известно временных блоков: " + knownTimeBlocks.size() + ".");
        }
        if (bullets.isEmpty()) {
            bullets.add("День выглядит свободным, можно заняться профилактическими задачами.");
        }

        String riskNote = overdueItems.isEmpty()
                ? ""
                : "Просроченные задачи повышают риск срыва следующих сроков.";
        String nextStep = !overdueItems.isEmpty()
                ? "Начните с самой старой просроченной задачи."
                : !upcomingItems.isEmpty()
                ? "Подготовьте ближайший дедлайн до начала глубокой работы."
                : "Используйте день для расчистки бэклога или планирования.";

        return new DailyReviewSummary(
                DailyReviewSummarySource.FALLBACK,
                headline,
                bullets,
                riskNote,
                nextStep,
                ""
        );
    }

    private DailyReviewFocusRecommendation buildFallbackFocusRecommendation(
            List<DailyReviewOverdueItem> overdueItems,
            List<DailyReviewUpcomingItem> upcomingItems) {
        if (!overdueItems.isEmpty()) {
            DailyReviewOverdueItem item = overdueItems.getFirst();
            return new DailyReviewFocusRecommendation(
                    item.title(),
                    "Задача уже просрочена и создаёт наибольшее давление на день.",
                    "Закройте или перепланируйте \"" + item.title() + "\" в первую очередь.",
                    DailyReviewSummarySource.FALLBACK
            );
        }
        if (!upcomingItems.isEmpty()) {
            DailyReviewUpcomingItem item = upcomingItems.getFirst();
            return new DailyReviewFocusRecommendation(
                    item.title(),
                    item.dueToday()
                            ? "У задачи дедлайн сегодня, поэтому она должна быть в фокусе."
                            : "Это ближайший дедлайн с наибольшим влиянием на остаток дня.",
                    "Подготовьте основной прогресс по \"" + item.title() + "\" в ближайшем рабочем окне.",
                    DailyReviewSummarySource.FALLBACK
            );
        }
        return new DailyReviewFocusRecommendation(
                "Свободный фокус",
                "Критических задач на день не найдено.",
                "Используйте день для расчистки мелких хвостов или стратегического планирования.",
                DailyReviewSummarySource.FALLBACK
        );
    }

    private Map<String, Task> indexById(List<Task> tasks) {
        Map<String, Task> indexed = new HashMap<>();
        for (Task task : tasks) {
            if (task != null && task.getId() != null && !task.getId().isBlank()) {
                indexed.put(task.getId(), task);
            }
        }
        return indexed;
    }

    private List<Task> flattenTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        List<Task> all = new ArrayList<>();
        Deque<Task> stack = new ArrayDeque<>(tasks);
        while (!stack.isEmpty()) {
            Task task = stack.pop();
            if (task == null) {
                continue;
            }
            all.add(task);
            if (task.hasSubtasks()) {
                for (Task subtask : task.getSubtasks()) {
                    stack.push(subtask);
                }
            }
        }
        return all;
    }

    private List<String> parseTags(Task task) {
        if (task == null || task.getTags() == null || task.getTags().isBlank()) {
            return List.of();
        }
        return List.of(task.getTags().split(",")).stream()
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .toList();
    }
}
