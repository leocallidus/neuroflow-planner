package com.example.neuroflowplanner.service.planningquality;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PlanningQualityContentFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm");

    private PlanningQualityContentFormatter() {
    }

    public static String buildExportTitle(PlanningQualityResult result) {
        PlanningQualityResult safeResult = result == null
                ? new PlanningQualityResult(null, null, "", false, false)
                : result;
        return "Качество планирования — "
                + DATE_FORMAT.format(safeResult.periodStart())
                + " - "
                + DATE_FORMAT.format(safeResult.periodEnd());
    }

    public static String toMarkdown(PlanningQualityResult result) {
        PlanningQualityResult safeResult = result == null
                ? new PlanningQualityResult(null, null, "", false, false)
                : result;
        PlanningQualitySnapshot snapshot = safeResult.snapshot();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(buildExportTitle(safeResult)).append("\n\n");
        markdown.append("*Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()))
                .append("*\n\n");

        PlanningQualitySummary summary = snapshot.summary();
        markdown.append("## Сводка качества планирования\n\n");
        if (!summary.headline().isBlank()) {
            markdown.append("**").append(escapeInline(summary.headline())).append("**\n\n");
        }
        if (!summary.summary().isBlank()) {
            markdown.append(summary.summary()).append("\n\n");
        } else {
            markdown.append("- Сводка качества планирования пока недоступна.\n\n");
        }
        if (!summary.nextAction().isBlank()) {
            markdown.append("**Следующий шаг:** ").append(escapeInline(summary.nextAction())).append("\n");
        }
        if (!summary.limitations().isBlank()) {
            markdown.append("\n**Ограничения:** ").append(escapeInline(summary.limitations())).append("\n");
        }

        markdown.append("\n## Метрики\n\n");
        appendMetric(markdown, "Точность оценки времени", formatAccuracy(snapshot.accuracyMetric()));
        appendMetric(markdown, "Доля переносов", formatReschedule(snapshot.rescheduleMetric()));
        appendMetric(markdown, "Стабильность ритма", formatRhythm(snapshot.rhythmMetric()));

        markdown.append("\n## Проблемные паттерны\n\n");
        if (snapshot.risks().isEmpty()) {
            markdown.append("- Явных проблемных паттернов не найдено.\n");
        } else {
            for (PlanningQualityRisk risk : snapshot.risks()) {
                markdown.append("- **").append(escapeInline(risk.title())).append("**");
                if (!risk.detail().isBlank()) {
                    markdown.append(" — ").append(escapeInline(risk.detail()));
                }
                markdown.append(" • ").append(risk.severity().name().toLowerCase()).append("\n");
            }
        }

        markdown.append("\n## Что улучшить\n\n");
        if (snapshot.recommendations().isEmpty()) {
            markdown.append("- Явных улучшений пока не найдено.\n");
        } else {
            for (PlanningQualityRecommendation recommendation : snapshot.recommendations()) {
                markdown.append("- **").append(escapeInline(recommendation.title())).append("**");
                if (!recommendation.detail().isBlank()) {
                    markdown.append(" — ").append(escapeInline(recommendation.detail()));
                }
                if (!recommendation.action().isBlank()) {
                    markdown.append("\n  - ").append(escapeInline(recommendation.action()));
                }
                markdown.append("\n");
            }
        }

        markdown.append("\n## Сигналы периода\n\n");
        markdown.append("- Активных задач: ").append(snapshot.activeTaskCount()).append("\n");
        markdown.append("- Завершённых задач: ").append(snapshot.completedTaskCount()).append("\n");
        markdown.append("- Задач с оценкой: ").append(snapshot.estimatedTaskCount()).append("\n");
        markdown.append("- Задач с расписанием: ").append(snapshot.scheduledTaskCount()).append("\n");
        markdown.append("- Задач с трекингом: ").append(snapshot.trackedTaskCount()).append("\n");
        markdown.append("- Сессий трекинга: ").append(snapshot.trackedSessionCount()).append("\n");

        return markdown.toString().trim() + "\n";
    }

    public static String toChatSeedPrompt(PlanningQualityResult result) {
        PlanningQualityResult safeResult = result == null
                ? new PlanningQualityResult(null, null, "", false, false)
                : result;
        StringBuilder prompt = new StringBuilder();
        prompt.append("Используй этот дашборд качества планирования как стартовый контекст.\n");
        prompt.append("Помоги интерпретировать слабые места планирования и предложить ближайшие улучшения.\n\n");
        prompt.append("Период анализа: ")
                .append(DATE_FORMAT.format(safeResult.periodStart()))
                .append(" - ")
                .append(DATE_FORMAT.format(safeResult.periodEnd()))
                .append("\n");
        prompt.append("Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()))
                .append("\n\n");
        prompt.append(toMarkdown(safeResult));
        prompt.append("\nС учётом этого dashboard:\n");
        prompt.append("1. Коротко оцени общее качество планирования.\n");
        prompt.append("2. Назови самую слабую метрику и почему она проседает.\n");
        prompt.append("3. Предложи 1-2 конкретных изменения в планировании на ближайшие дни.\n");
        if (safeResult.snapshot().limitedData()) {
            prompt.append("4. Учти, что часть выводов опирается на ограниченные данные.\n");
        }
        return prompt.toString().trim();
    }

    private static void appendMetric(StringBuilder markdown, String title, List<String> lines) {
        markdown.append("### ").append(title).append("\n\n");
        for (String line : lines) {
            markdown.append("- ").append(line).append("\n");
        }
        markdown.append("\n");
    }

    private static List<String> formatAccuracy(TimeEstimateAccuracyMetric metric) {
        if (metric == null || !metric.available()) {
            return List.of("Недостаточно данных для расчёта accuracy.");
        }
        return List.of(
                "Попадание в диапазон: " + formatPercent(metric.hitRate()),
                "Средняя ошибка: " + formatPercent(metric.averageErrorRatio()),
                "Недооценка: " + formatPercent(metric.underestimationBias()),
                "Переоценка: " + formatPercent(metric.overestimationBias()),
                "Coverage: " + metric.comparableTaskCount() + " из " + metric.estimatedTaskCount() + " задач"
        );
    }

    private static List<String> formatReschedule(RescheduleRateMetric metric) {
        if (metric == null || !metric.available()) {
            return List.of("Недостаточно данных для расчёта переносов.");
        }
        return List.of(
                "Доля задач с переносами: " + formatPercent(metric.rescheduleRate()),
                "Без переносов: " + metric.untouchedTaskCount(),
                "Повторные переносы: " + metric.multipleRescheduleCount(),
                "Поздние переносы: " + metric.lateRescheduleCount(),
                "Проанализировано: " + metric.analyzedTaskCount()
        );
    }

    private static List<String> formatRhythm(RhythmStabilityMetric metric) {
        if (metric == null || !metric.available()) {
            return List.of("Недостаточно данных для расчёта ритма.");
        }
        return List.of(
                "Уровень: " + localizeRhythmBand(metric.band()),
                "Score: " + formatPercent(metric.score()),
                "Продуктивных дней: " + metric.productiveDayCount() + " из " + metric.analyzedDayCount(),
                "Разброс старта: " + metric.startTimeVariabilityMinutes() + " мин",
                "Разброс фокуса: " + formatPercent(metric.focusMinutesVariability())
        );
    }

    private static String localizeRhythmBand(RhythmStabilityBand band) {
        if (band == null) {
            return "Нет данных";
        }
        return switch (band) {
            case STABLE -> "Стабильный";
            case MODERATE -> "Умеренный";
            case CHAOTIC -> "Хаотичный";
            case UNAVAILABLE -> "Нет данных";
        };
    }

    private static String formatPercent(double value) {
        return Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0) + "%";
    }

    private static String escapeInline(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("\r", " ").replace("\n", " ").trim();
    }
}
