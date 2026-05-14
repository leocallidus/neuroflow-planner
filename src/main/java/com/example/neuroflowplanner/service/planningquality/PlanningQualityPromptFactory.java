package com.example.neuroflowplanner.service.planningquality;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PlanningQualityPromptFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");

    private PlanningQualityPromptFactory() {
    }

    public static PlanningQualityAiPromptPayload build(PlanningQualitySnapshot snapshot) {
        PlanningQualitySnapshot safeSnapshot = snapshot == null
                ? new PlanningQualitySnapshot(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                true
        )
                : snapshot;
        return new PlanningQualityAiPromptPayload(
                buildSystemPrompt(),
                buildUserPrompt(safeSnapshot),
                buildFallbackSummary(safeSnapshot)
        );
    }

    public static String buildSystemPrompt() {
        return """
                Ты формируешь компактную сводку качества планирования для NeuroFlow Planner.
                Тебе нельзя пересказывать все задачи и сырые логи. Нужно опираться только на уже рассчитанный planning quality snapshot.

                Цель ответа:
                - дать общую картину качества планирования;
                - назвать главную слабую зону;
                - отметить 1-2 сильные стороны;
                - дать одно конкретное улучшение на ближайший цикл планирования.

                Обязательные правила:
                1. Ответ должен быть на русском языке.
                2. Не выдумывай факты, которых нет во входных данных.
                3. Не пересказывай сырые day aggregates и не перечисляй все задачи.
                4. Если метрика approximate или unavailable, честно отрази это.
                5. Не используй размытые советы вроде "планируйте лучше" или "сохраняйте баланс".
                6. Опирайся на accuracy, reschedule rate, rhythm stability, risks и recommendations.
                7. Не добавляй вступление или послесловие вне формата.

                Верни ответ строго в таком формате:
                ## Общая картина
                - 2 коротких bullet
                ## Слабая зона
                - 1 bullet
                ## Сильные стороны
                - 1-2 bullet
                ## Что улучшить прямо сейчас
                - 1 bullet с конкретным next step
                """;
    }

    public static String buildUserPrompt(PlanningQualitySnapshot snapshot) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Собери planning quality dashboard summary.\n\n");
        prompt.append("Период анализа: ")
                .append(snapshot.periodStart().format(DATE_FORMAT))
                .append(" - ")
                .append(snapshot.periodEnd().format(DATE_FORMAT))
                .append('\n');
        prompt.append("Данных ограничено: ").append(snapshot.limitedData() ? "да" : "нет").append('\n');
        prompt.append("Активных задач: ").append(snapshot.activeTaskCount()).append('\n');
        prompt.append("Завершённых задач: ").append(snapshot.completedTaskCount()).append('\n');
        prompt.append("Задач с оценкой/планом: ").append(snapshot.estimatedTaskCount()).append('\n');
        prompt.append("Задач с расписанием: ").append(snapshot.scheduledTaskCount()).append('\n');
        prompt.append("Задач с трекингом: ").append(snapshot.trackedTaskCount()).append('\n');
        prompt.append("Сессий трекинга: ").append(snapshot.trackedSessionCount()).append("\n\n");

        appendAccuracySection(prompt, snapshot.accuracyMetric());
        appendRescheduleSection(prompt, snapshot.rescheduleMetric());
        appendRhythmSection(prompt, snapshot.rhythmMetric());
        appendRiskSection(prompt, snapshot.risks());
        appendRecommendationsSection(prompt, snapshot.recommendations());
        appendDayAggregateHints(prompt, snapshot.dayAggregates());

        return prompt.toString().trim();
    }

    public static PlanningQualitySummary buildFallbackSummary(PlanningQualitySnapshot snapshot) {
        PlanningQualitySummary existing = snapshot.summary();
        if (existing != null && existing.source() == PlanningQualitySummarySource.FALLBACK && existing.available()) {
            return existing;
        }

        List<String> strongSides = new ArrayList<>();
        List<String> weakSides = new ArrayList<>();

        if (snapshot.accuracyMetric().available()) {
            if (snapshot.accuracyMetric().hitRate() >= 0.6) {
                strongSides.add("оценка времени относительно стабильна");
            }
            if (snapshot.accuracyMetric().underestimationBias() >= 0.55) {
                weakSides.add("системная недооценка времени");
            }
            if (snapshot.accuracyMetric().overestimationBias() >= 0.55) {
                weakSides.add("оценки часто избыточны");
            }
        }

        if (snapshot.rescheduleMetric().available()) {
            if (snapshot.rescheduleMetric().rescheduleRate() <= 0.2) {
                strongSides.add("план редко приходится пересобирать");
            } else if (snapshot.rescheduleMetric().rescheduleRate() >= 0.45) {
                weakSides.add("задачи часто съезжают по ходу периода");
            }
        }

        if (snapshot.rhythmMetric().available()) {
            if (snapshot.rhythmMetric().band() == RhythmStabilityBand.STABLE) {
                strongSides.add("рабочий ритм повторяется достаточно ровно");
            } else if (snapshot.rhythmMetric().band() == RhythmStabilityBand.CHAOTIC) {
                weakSides.add("ритм работы остаётся нестабильным");
            }
        }

        PlanningQualityRisk topRisk = snapshot.risks().stream()
                .max(Comparator.comparingInt(risk -> switch (risk.severity()) {
                    case CRITICAL -> 3;
                    case WARNING -> 2;
                    case INFO -> 1;
                }))
                .orElse(null);
        if (topRisk != null) {
            weakSides.add(topRisk.title().toLowerCase());
        }

        String headline;
        if (!weakSides.isEmpty()) {
            headline = "Качество планирования требует коррекции";
        } else if (!strongSides.isEmpty()) {
            headline = "Планирование выглядит достаточно устойчивым";
        } else {
            headline = "Базовая картина качества планирования собрана";
        }

        StringBuilder summary = new StringBuilder();
        if (!weakSides.isEmpty()) {
            summary.append("Главная слабая зона: ").append(weakSides.getFirst()).append(". ");
        }
        if (!strongSides.isEmpty()) {
            summary.append("Сильная сторона: ").append(strongSides.getFirst()).append(". ");
        }
        if (snapshot.limitedData()) {
            summary.append("Часть выводов приблизительна из-за ограниченной истории.");
        } else if (summary.isEmpty()) {
            summary.append("Метрики собраны, но выраженного доминирующего паттерна пока нет.");
        }

        String nextAction = snapshot.recommendations().stream()
                .filter(PlanningQualityRecommendation::available)
                .findFirst()
                .map(PlanningQualityRecommendation::action)
                .filter(action -> action != null && !action.isBlank())
                .orElseGet(() -> defaultNextAction(snapshot));

        String limitations = snapshot.limitedData()
                ? "Часть quality-метрик построена по ограниченной выборке и может быть approximate."
                : buildApproximationLimitations(snapshot);

        return new PlanningQualitySummary(
                PlanningQualitySummarySource.FALLBACK,
                headline,
                summary.toString().trim(),
                nextAction,
                limitations
        );
    }

    private static void appendAccuracySection(StringBuilder prompt, TimeEstimateAccuracyMetric metric) {
        prompt.append("### Accuracy\n");
        if (metric == null || !metric.available()) {
            prompt.append("- Метрика пока недоступна.\n\n");
            return;
        }
        prompt.append("- comparable tasks: ").append(metric.comparableTaskCount()).append('\n');
        prompt.append("- average error ratio: ").append(formatRatio(metric.averageErrorRatio())).append('\n');
        prompt.append("- hit rate: ").append(formatRatio(metric.hitRate())).append('\n');
        prompt.append("- underestimation bias: ").append(formatRatio(metric.underestimationBias())).append('\n');
        prompt.append("- overestimation bias: ").append(formatRatio(metric.overestimationBias())).append('\n');
        prompt.append("- approximate: ").append(metric.approximate() ? "да" : "нет").append("\n\n");
    }

    private static void appendRescheduleSection(StringBuilder prompt, RescheduleRateMetric metric) {
        prompt.append("### Reschedule rate\n");
        if (metric == null || !metric.available()) {
            prompt.append("- Метрика пока недоступна.\n\n");
            return;
        }
        prompt.append("- analyzed tasks: ").append(metric.analyzedTaskCount()).append('\n');
        prompt.append("- rescheduled tasks: ").append(metric.rescheduledTaskCount()).append('\n');
        prompt.append("- multiple reschedules: ").append(metric.multipleRescheduleCount()).append('\n');
        prompt.append("- late reschedules: ").append(metric.lateRescheduleCount()).append('\n');
        prompt.append("- reschedule rate: ").append(formatRatio(metric.rescheduleRate())).append('\n');
        prompt.append("- approximate: ").append(metric.approximate() ? "да" : "нет").append("\n\n");
    }

    private static void appendRhythmSection(StringBuilder prompt, RhythmStabilityMetric metric) {
        prompt.append("### Rhythm stability\n");
        if (metric == null || !metric.available()) {
            prompt.append("- Метрика пока недоступна.\n\n");
            return;
        }
        prompt.append("- band: ").append(metric.band()).append('\n');
        prompt.append("- score: ").append(formatRatio(metric.score())).append('\n');
        prompt.append("- analyzed days: ").append(metric.analyzedDayCount()).append('\n');
        prompt.append("- productive days: ").append(metric.productiveDayCount()).append('\n');
        prompt.append("- start time variability: ").append(metric.startTimeVariabilityMinutes()).append(" мин\n");
        prompt.append("- focus minutes variability: ").append(formatRatio(metric.focusMinutesVariability())).append('\n');
        prompt.append("- approximate: ").append(metric.approximate() ? "да" : "нет").append("\n\n");
    }

    private static void appendRiskSection(StringBuilder prompt, List<PlanningQualityRisk> risks) {
        prompt.append("### Risks\n");
        if (risks == null || risks.isEmpty()) {
            prompt.append("- Явных рисков не найдено.\n\n");
            return;
        }
        for (PlanningQualityRisk risk : risks.stream().limit(4).toList()) {
            prompt.append("- ")
                    .append(limit(risk.title(), 120))
                    .append(" | ")
                    .append(risk.severity())
                    .append(" | ")
                    .append(limit(risk.detail(), 180))
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendRecommendationsSection(StringBuilder prompt, List<PlanningQualityRecommendation> recommendations) {
        prompt.append("### Recommendations\n");
        if (recommendations == null || recommendations.isEmpty()) {
            prompt.append("- Явных рекомендаций нет.\n\n");
            return;
        }
        for (PlanningQualityRecommendation recommendation : recommendations.stream().limit(4).toList()) {
            prompt.append("- ")
                    .append(limit(recommendation.title(), 100))
                    .append(" | ")
                    .append(limit(recommendation.detail(), 150))
                    .append(" | action: ")
                    .append(limit(recommendation.action(), 150))
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendDayAggregateHints(StringBuilder prompt, List<PlanningQualityDayAggregate> dayAggregates) {
        prompt.append("### Day-level hints\n");
        if (dayAggregates == null || dayAggregates.isEmpty()) {
            prompt.append("- Day aggregates недоступны.\n\n");
            return;
        }
        long overloadedDays = dayAggregates.stream().filter(PlanningQualityDayAggregate::overloaded).count();
        long emptyDays = dayAggregates.stream().filter(PlanningQualityDayAggregate::emptyWorkday).count();
        long approximateDays = dayAggregates.stream().filter(PlanningQualityDayAggregate::approximate).count();
        long peakTrackedMinutes = dayAggregates.stream().mapToLong(PlanningQualityDayAggregate::trackedMinutes).max().orElse(0L);
        prompt.append("- overloaded days: ").append(overloadedDays).append('\n');
        prompt.append("- empty workdays: ").append(emptyDays).append('\n');
        prompt.append("- approximate days: ").append(approximateDays).append('\n');
        prompt.append("- peak tracked minutes in day: ").append(peakTrackedMinutes).append('\n').append('\n');
    }

    private static String defaultNextAction(PlanningQualitySnapshot snapshot) {
        if (snapshot.accuracyMetric().available() && snapshot.accuracyMetric().underestimationBias() >= 0.55) {
            return "Добавьте буфер к оценкам глубоких задач и не планируйте их вплотную к дедлайну.";
        }
        if (snapshot.rescheduleMetric().available() && snapshot.rescheduleMetric().rescheduleRate() >= 0.35) {
            return "Сократите число жёстко назначенных задач и оставьте буфер между стартом и дедлайном.";
        }
        if (snapshot.rhythmMetric().available() && snapshot.rhythmMetric().band() == RhythmStabilityBand.CHAOTIC) {
            return "Стабилизируйте первый рабочий блок дня и удерживайте похожую длину фокус-сессий хотя бы несколько дней подряд.";
        }
        return "Продолжайте трекать время и фиксировать плановые окна, чтобы следующий цикл качества планирования был точнее.";
    }

    private static String buildApproximationLimitations(PlanningQualitySnapshot snapshot) {
        List<String> parts = new ArrayList<>();
        if (snapshot.rescheduleMetric().available() && snapshot.rescheduleMetric().approximate()) {
            parts.add("метрика переносов рассчитана heuristic-способом");
        }
        if (snapshot.rhythmMetric().available() && snapshot.rhythmMetric().approximate()) {
            parts.add("стабильность ритма основана на ограниченной истории");
        }
        return String.join("; ", parts);
    }

    private static String formatRatio(double value) {
        return String.format("%.0f%%", Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "…";
    }
}
