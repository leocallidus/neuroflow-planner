package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.db.DatabaseManager;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class PlanningQualitySnapshotBuilder {

    static final int DEFAULT_LOOKBACK_DAYS = 14;
    private static final int MAX_DAY_AGGREGATES = 31;

    private final TaskApplicationService taskApplicationService;
    private final Supplier<List<TimeSession>> timeSessionsSupplier;
    private final TimeEstimateAccuracyCalculator timeEstimateAccuracyCalculator;
    private final RescheduleRateCalculator rescheduleRateCalculator;
    private final RhythmStabilityCalculator rhythmStabilityCalculator;

    public PlanningQualitySnapshotBuilder() {
        this(
                new DefaultTaskApplicationService(),
                () -> DatabaseManager.getInstance().loadTimeSessions(),
                new TimeEstimateAccuracyCalculator(),
                new RescheduleRateCalculator(),
                new RhythmStabilityCalculator()
        );
    }

    PlanningQualitySnapshotBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier) {
        this(
                taskApplicationService,
                timeSessionsSupplier,
                new TimeEstimateAccuracyCalculator(),
                new RescheduleRateCalculator(),
                new RhythmStabilityCalculator()
        );
    }

    PlanningQualitySnapshotBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            TimeEstimateAccuracyCalculator timeEstimateAccuracyCalculator) {
        this(
                taskApplicationService,
                timeSessionsSupplier,
                timeEstimateAccuracyCalculator,
                new RescheduleRateCalculator(),
                new RhythmStabilityCalculator()
        );
    }

    PlanningQualitySnapshotBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            TimeEstimateAccuracyCalculator timeEstimateAccuracyCalculator,
            RescheduleRateCalculator rescheduleRateCalculator) {
        this(
                taskApplicationService,
                timeSessionsSupplier,
                timeEstimateAccuracyCalculator,
                rescheduleRateCalculator,
                new RhythmStabilityCalculator()
        );
    }

    PlanningQualitySnapshotBuilder(
            TaskApplicationService taskApplicationService,
            Supplier<List<TimeSession>> timeSessionsSupplier,
            TimeEstimateAccuracyCalculator timeEstimateAccuracyCalculator,
            RescheduleRateCalculator rescheduleRateCalculator,
            RhythmStabilityCalculator rhythmStabilityCalculator) {
        this.taskApplicationService = taskApplicationService == null ? new DefaultTaskApplicationService() : taskApplicationService;
        this.timeSessionsSupplier = timeSessionsSupplier == null ? List::of : timeSessionsSupplier;
        this.timeEstimateAccuracyCalculator = timeEstimateAccuracyCalculator == null
                ? new TimeEstimateAccuracyCalculator()
                : timeEstimateAccuracyCalculator;
        this.rescheduleRateCalculator = rescheduleRateCalculator == null
                ? new RescheduleRateCalculator()
                : rescheduleRateCalculator;
        this.rhythmStabilityCalculator = rhythmStabilityCalculator == null
                ? new RhythmStabilityCalculator()
                : rhythmStabilityCalculator;
    }

    public PlanningQualitySnapshot buildForRecentPeriod() {
        LocalDate end = LocalDate.now();
        return buildForPeriod(end.minusDays(DEFAULT_LOOKBACK_DAYS - 1L), end);
    }

    public PlanningQualitySnapshot buildForPeriod(LocalDate periodStart, LocalDate periodEnd) {
        LocalDate safeEnd = periodEnd == null ? LocalDate.now() : periodEnd;
        LocalDate safeStart = periodStart == null ? safeEnd.minusDays(DEFAULT_LOOKBACK_DAYS - 1L) : periodStart;
        if (safeStart.isAfter(safeEnd)) {
            LocalDate originalStart = safeStart;
            safeStart = safeEnd;
            safeEnd = originalStart;
        }
        final LocalDate effectiveStart = safeStart;
        final LocalDate effectiveEnd = safeEnd;

        List<Task> allTasks = flattenTasks(taskApplicationService.loadTasks()).stream()
                .filter(task -> task != null && !task.isArchived())
                .toList();
        List<TimeSession> sessions = safeTimeSessions().stream()
                .filter(session -> session != null && session.getStartedAt() != null)
                .toList();

        List<Task> activeTasks = allTasks.stream()
                .filter(task -> !task.isCompleted())
                .toList();
        List<Task> completedTasks = allTasks.stream()
                .filter(Task::isCompleted)
                .filter(task -> task.getCompletedDate() == null || isWithinPeriod(task.getCompletedDate(), effectiveStart, effectiveEnd))
                .toList();
        List<Task> scheduledTasks = allTasks.stream()
                .filter(task -> touchesPeriod(task, effectiveStart, effectiveEnd))
                .toList();
        List<Task> estimatedTasks = allTasks.stream()
                .filter(this::hasComparablePlannedWindow)
                .filter(task -> touchesPeriod(task, effectiveStart, effectiveEnd))
                .toList();
        List<TimeSession> sessionsInPeriod = sessions.stream()
                .filter(session -> isWithinPeriod(session.getStartedAt().toLocalDate(), effectiveStart, effectiveEnd))
                .toList();

        Set<String> trackedTaskIds = new HashSet<>();
        for (TimeSession session : sessionsInPeriod) {
            if (session.getTaskId() != null && !session.getTaskId().isBlank()) {
                trackedTaskIds.add(session.getTaskId());
            }
        }

        List<PlanningQualityDayAggregate> dayAggregates = buildDayAggregates(
                safeStart,
                safeEnd,
                scheduledTasks,
                completedTasks,
                sessionsInPeriod
        );
        TimeEstimateAccuracyMetric accuracyMetric = timeEstimateAccuracyCalculator.calculate(
                allTasks,
                sessionsInPeriod,
                safeStart,
                safeEnd
        );
        RescheduleRateMetric rescheduleMetric = rescheduleRateCalculator.calculate(
                allTasks,
                sessionsInPeriod,
                safeStart,
                safeEnd
        );
        RhythmStabilityMetric rhythmMetric = rhythmStabilityCalculator.calculate(dayAggregates, sessionsInPeriod);

        int estimatedTaskCount = estimatedTasks.size();
        int trackedSessionCount = sessionsInPeriod.size();
        PlanningQualitySummary summary = buildFallbackSummary(
                activeTasks.size(),
                estimatedTaskCount,
                scheduledTasks.size(),
                trackedTaskIds.size(),
                trackedSessionCount,
                rescheduleMetric
        );
        List<PlanningQualityRisk> risks = buildFallbackRisks(
                estimatedTaskCount,
                trackedSessionCount,
                dayAggregates,
                rescheduleMetric
        );
        List<PlanningQualityRecommendation> recommendations = buildFallbackRecommendations(
                estimatedTaskCount,
                trackedSessionCount,
                rescheduleMetric
        );
        boolean limitedData = trackedSessionCount < 4 || estimatedTaskCount < 3;

        return new PlanningQualitySnapshot(
                safeStart,
                safeEnd,
                Instant.now(),
                summary,
                accuracyMetric,
                rescheduleMetric,
                rhythmMetric,
                risks,
                recommendations,
                dayAggregates,
                activeTasks.size(),
                completedTasks.size(),
                estimatedTaskCount,
                scheduledTasks.size(),
                trackedTaskIds.size(),
                trackedSessionCount,
                limitedData
        );
    }

    private List<TimeSession> safeTimeSessions() {
        List<TimeSession> loaded = timeSessionsSupplier.get();
        return loaded == null ? List.of() : List.copyOf(loaded);
    }

    private List<PlanningQualityDayAggregate> buildDayAggregates(
            LocalDate periodStart,
            LocalDate periodEnd,
            List<Task> scheduledTasks,
            List<Task> completedTasks,
            List<TimeSession> sessionsInPeriod) {
        Map<LocalDate, Integer> scheduledCounts = new LinkedHashMap<>();
        Map<LocalDate, Integer> completedCounts = new LinkedHashMap<>();
        Map<LocalDate, Integer> sessionCounts = new LinkedHashMap<>();
        Map<LocalDate, Long> trackedMinutes = new LinkedHashMap<>();

        for (Task task : scheduledTasks) {
            LocalDate bucket = resolveScheduleBucket(task, periodStart, periodEnd);
            if (bucket != null) {
                scheduledCounts.merge(bucket, 1, Integer::sum);
            }
        }
        for (Task task : completedTasks) {
            if (task.getCompletedDate() != null && isWithinPeriod(task.getCompletedDate(), periodStart, periodEnd)) {
                completedCounts.merge(task.getCompletedDate(), 1, Integer::sum);
            }
        }
        for (TimeSession session : sessionsInPeriod) {
            LocalDate date = session.getStartedAt().toLocalDate();
            sessionCounts.merge(date, 1, Integer::sum);
            trackedMinutes.merge(date, session.getMinutes(), Long::sum);
        }

        List<PlanningQualityDayAggregate> aggregates = new ArrayList<>();
        LocalDate cursor = periodStart;
        while (!cursor.isAfter(periodEnd) && aggregates.size() < MAX_DAY_AGGREGATES) {
            int dayScheduled = scheduledCounts.getOrDefault(cursor, 0);
            int dayCompleted = completedCounts.getOrDefault(cursor, 0);
            int daySessions = sessionCounts.getOrDefault(cursor, 0);
            long dayTrackedMinutes = trackedMinutes.getOrDefault(cursor, 0L);
            boolean overloaded = dayScheduled >= 6 || dayTrackedMinutes >= 360;
            boolean emptyWorkday = dayScheduled == 0 && daySessions == 0 && dayTrackedMinutes == 0L;
            boolean approximate = dayScheduled == 0 && dayCompleted == 0 && daySessions == 0;
            aggregates.add(new PlanningQualityDayAggregate(
                    cursor,
                    dayScheduled,
                    dayCompleted,
                    daySessions,
                    dayTrackedMinutes,
                    overloaded,
                    emptyWorkday,
                    approximate
            ));
            cursor = cursor.plusDays(1);
        }
        return aggregates;
    }

    private PlanningQualitySummary buildFallbackSummary(
            int activeTaskCount,
            int estimatedTaskCount,
            int scheduledTaskCount,
            int trackedTaskCount,
            int trackedSessionCount,
            RescheduleRateMetric rescheduleMetric) {
        if (trackedSessionCount < 2) {
            return new PlanningQualitySummary(
                    PlanningQualitySummarySource.FALLBACK,
                    "Пока мало данных для уверенной оценки",
                    "Дашборд собрал базовые сигналы по задачам и трекингу, но истории ещё недостаточно для надёжного вывода о качестве планирования.",
                    "Продолжайте трекать фокус-сессии и добавляйте время в задачи.",
                    ""
            );
        }
        String rescheduleSummary = rescheduleMetric != null && rescheduleMetric.available()
                ? " Из " + rescheduleMetric.analyzedTaskCount() + " проанализированных плановых задач "
                + rescheduleMetric.rescheduledTaskCount() + " выглядят как задачи с переносами."
                : "";
        return new PlanningQualitySummary(
                PlanningQualitySummarySource.FALLBACK,
                "Базовый снимок качества планирования готов",
                "В периоде найдено " + activeTaskCount + " активных задач, "
                        + scheduledTaskCount + " задач с плановыми окнами, "
                        + estimatedTaskCount + " задач с сопоставимой оценкой времени и "
                        + trackedTaskCount + " задач с фактическим трекингом."
                        + rescheduleSummary,
                "Следующий шаг - рассчитать точность оценки времени, долю переносов и стабильность ритма.",
                ""
        );
    }

    private List<PlanningQualityRisk> buildFallbackRisks(
            int estimatedTaskCount,
            int trackedSessionCount,
            List<PlanningQualityDayAggregate> dayAggregates,
            RescheduleRateMetric rescheduleMetric) {
        List<PlanningQualityRisk> risks = new ArrayList<>();
        if (trackedSessionCount < 4) {
            risks.add(new PlanningQualityRisk(
                    PlanningQualityRiskSeverity.INFO,
                    "Недостаточно истории трекинга",
                    "Без устойчивой истории сессий качество планирования будет оцениваться приблизительно."
            ));
        }
        if (estimatedTaskCount < 3) {
            risks.add(new PlanningQualityRisk(
                    PlanningQualityRiskSeverity.WARNING,
                    "Мало задач с оценкой времени",
                    "Метрика точности оценки времени будет неполной, пока задачи редко получают плановые окна."
            ));
        }
        long overloadedDays = dayAggregates.stream().filter(PlanningQualityDayAggregate::overloaded).count();
        if (overloadedDays >= 3) {
            risks.add(new PlanningQualityRisk(
                    PlanningQualityRiskSeverity.WARNING,
                    "Есть перегруженные дни",
                    "В периоде уже видно несколько дней с признаками переуплотнения."
            ));
        }
        if (rescheduleMetric != null && rescheduleMetric.available()) {
            if (rescheduleMetric.rescheduleRate() >= 0.5 || rescheduleMetric.multipleRescheduleCount() >= 3) {
                risks.add(new PlanningQualityRisk(
                        PlanningQualityRiskSeverity.WARNING,
                        "План часто пересобирается по ходу дела",
                        "Heuristic-анализ показывает много задач с переносами и повторным сдвигом плана."
                ));
            } else if (rescheduleMetric.lateRescheduleCount() >= 2) {
                risks.add(new PlanningQualityRisk(
                        PlanningQualityRiskSeverity.INFO,
                        "Есть поздние переносы перед дедлайном",
                        "Часть задач смещается слишком поздно, когда у плана уже мало пространства для манёвра."
                ));
            }
        }
        return risks;
    }

    private List<PlanningQualityRecommendation> buildFallbackRecommendations(
            int estimatedTaskCount,
            int trackedSessionCount,
            RescheduleRateMetric rescheduleMetric) {
        List<PlanningQualityRecommendation> recommendations = new ArrayList<>();
        if (estimatedTaskCount < 3) {
            recommendations.add(new PlanningQualityRecommendation(
                    "Добавляйте время в задачи",
                    "Задачи со start/deadline временем дадут базу для точности оценки.",
                    "Начните задавать время хотя бы для ключевых задач дня.",
                    PlanningQualitySummarySource.FALLBACK
            ));
        }
        if (trackedSessionCount < 4) {
            recommendations.add(new PlanningQualityRecommendation(
                    "Трекайте реальные рабочие блоки",
                    "Без фактических сессий сложно понять, насколько план совпадает с реальностью.",
                    "Запускайте трекинг хотя бы на основных фокус-сессиях.",
                    PlanningQualitySummarySource.FALLBACK
            ));
        }
        if (rescheduleMetric != null && rescheduleMetric.available() && rescheduleMetric.rescheduleRate() >= 0.35) {
            recommendations.add(new PlanningQualityRecommendation(
                    "Уменьшите плотность плана",
                    "Heuristic-метрика показывает, что заметная доля задач требует переноса уже в ходе недели.",
                    "Сократите число жёстко расписанных задач и оставляйте буфер перед дедлайнами.",
                    PlanningQualitySummarySource.FALLBACK
            ));
        }
        return recommendations;
    }

    private LocalDate resolveScheduleBucket(Task task, LocalDate periodStart, LocalDate periodEnd) {
        if (task.getStartDate() != null && isWithinPeriod(task.getStartDate(), periodStart, periodEnd)) {
            return task.getStartDate();
        }
        if (task.getDeadline() != null && isWithinPeriod(task.getDeadline(), periodStart, periodEnd)) {
            return task.getDeadline();
        }
        if (task.getCompletedDate() != null && isWithinPeriod(task.getCompletedDate(), periodStart, periodEnd)) {
            return task.getCompletedDate();
        }
        return null;
    }

    private boolean hasComparablePlannedWindow(Task task) {
        LocalDateTime start = task == null ? null : task.getStartDateTime();
        LocalDateTime end = task == null ? null : task.getDeadlineDateTime();
        return start != null && end != null && end.isAfter(start);
    }

    private boolean touchesPeriod(Task task, LocalDate periodStart, LocalDate periodEnd) {
        if (task == null) {
            return false;
        }
        return isWithinPeriod(task.getStartDate(), periodStart, periodEnd)
                || isWithinPeriod(task.getDeadline(), periodStart, periodEnd)
                || isWithinPeriod(task.getCompletedDate(), periodStart, periodEnd);
    }

    private boolean isWithinPeriod(LocalDate date, LocalDate periodStart, LocalDate periodEnd) {
        return date != null && !date.isBefore(periodStart) && !date.isAfter(periodEnd);
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
        return all.stream()
                .sorted(Comparator.comparing(Task::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
