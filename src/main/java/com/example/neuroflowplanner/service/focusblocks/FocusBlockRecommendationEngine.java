package com.example.neuroflowplanner.service.focusblocks;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FocusBlockRecommendationEngine {

    private static final int MAX_FOCUS_WINDOWS = 3;
    private static final int MAX_SHORT_WINDOWS = 3;

    public FocusBlockRecommendationEngineResult recommend(FocusBlockRecommendationEngineInput input) {
        FocusBlockRecommendationEngineInput safeInput = input == null
                ? new FocusBlockRecommendationEngineInput(null, null, List.of(), 0, 0, 0)
                : input;
        List<FocusBlockCandidate> candidates = safeInput.candidateWindows();
        List<FocusBlockRisk> risks = buildRisks(safeInput, candidates);
        if (candidates.isEmpty()) {
            if (risks.isEmpty()) {
                risks = List.of(new FocusBlockRisk(
                        FocusBlockRiskLevel.WARNING,
                        "Нет доступных окон",
                        "На сегодня не найдено пригодных фокус-блоков."
                ));
            }
            return new FocusBlockRecommendationEngineResult(List.of(), List.of(), FocusBlockRecommendation.unavailable(), risks);
        }

        List<RankedRecommendation> ranked = candidates.stream()
                .map(candidate -> rankCandidate(candidate, safeInput))
                .sorted(Comparator
                        .comparingDouble(RankedRecommendation::score).reversed()
                        .thenComparing(rankedRecommendation -> rankedRecommendation.recommendation().startAt(),
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<FocusBlockRecommendation> focusWindows = ranked.stream()
                .map(RankedRecommendation::recommendation)
                .filter(this::isFocusWindow)
                .limit(MAX_FOCUS_WINDOWS)
                .toList();
        List<FocusBlockRecommendation> shortWindows = ranked.stream()
                .map(RankedRecommendation::recommendation)
                .filter(this::isShortWindow)
                .limit(MAX_SHORT_WINDOWS)
                .toList();
        FocusBlockRecommendation nextRecommendedBlock = !focusWindows.isEmpty()
                ? focusWindows.getFirst()
                : ranked.getFirst().recommendation();

        return new FocusBlockRecommendationEngineResult(
                focusWindows,
                shortWindows,
                nextRecommendedBlock,
                risks
        );
    }

    private RankedRecommendation rankCandidate(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input) {
        double hourScore = averagedHourScore(candidate, input.productivityProfile());
        double dayScore = dayScore(input.reviewDate().getDayOfWeek(), input.productivityProfile());
        double durationScore = durationScore(candidate);
        double urgencyScore = urgencyScore(candidate, input);
        double overloadPenalty = overloadPenalty(candidate, input);
        double confidence = (candidate.confidence() * 0.55) + (input.productivityProfile().confidence() * 0.45);

        double score = clamp(
                (candidate.suitabilityScore() * 0.33)
                        + (hourScore * 0.28)
                        + (durationScore * 0.17)
                        + (dayScore * 0.08)
                        + (confidence * 0.10)
                        + urgencyScore
                        - overloadPenalty
        );

        List<FocusBlockReason> reasons = new ArrayList<>(candidate.reasons());
        if (hourScore >= 0.7) {
            reasons.add(new FocusBlockReason(
                    "Исторически сильный час",
                    "В это время у вас выше накопленная продуктивность по данным трекинга."
            ));
        }
        if (durationScore >= 0.8 && candidate.durationMinutes() >= 90) {
            reasons.add(new FocusBlockReason(
                    "Достаточная длина окна",
                    "Окно " + candidate.durationMinutes() + " мин подходит для глубокой непрерывной работы."
            ));
        }
        if (urgencyScore > 0.01) {
            reasons.add(new FocusBlockReason(
                    "Подходит под срочность дня",
                    "Это окно достаточно раннее и помогает разгрузить просрочки или близкие дедлайны."
            ));
        }
        if (input.productivityProfile().limitedHistory()) {
            reasons.add(new FocusBlockReason(
                    "Ограниченная история",
                    "Рекомендация основана на неполной истории трекинга и может быть менее стабильной."
            ));
        }

        String title = buildTitle(candidate, input);
        String rationale = buildRationale(candidate, input, hourScore, urgencyScore);
        String nextStep = buildNextStep(candidate, input);
        FocusBlockRecommendation recommendation = new FocusBlockRecommendation(
                title,
                rationale,
                nextStep,
                candidate.startAt(),
                candidate.endAt(),
                candidate.durationMinutes(),
                candidate.type(),
                score,
                clamp((confidence * 0.75) + (score * 0.25)),
                false,
                reasons
        );
        return new RankedRecommendation(recommendation, score);
    }

    private List<FocusBlockRisk> buildRisks(FocusBlockRecommendationEngineInput input, List<FocusBlockCandidate> candidates) {
        List<FocusBlockRisk> risks = new ArrayList<>();
        if (candidates.isEmpty()) {
            risks.add(new FocusBlockRisk(
                    FocusBlockRiskLevel.CRITICAL,
                    "Свободных окон почти нет",
                    "На сегодня не найдено кандидатов для фокус-блока."
            ));
        }
        if (input.overdueTaskCount() > 0) {
            risks.add(new FocusBlockRisk(
                    input.overdueTaskCount() >= 3 ? FocusBlockRiskLevel.CRITICAL : FocusBlockRiskLevel.WARNING,
                    "Есть просрочки",
                    "Просроченных задач: " + input.overdueTaskCount() + ". Лучше использовать раннее сильное окно для разгрузки."
            ));
        }
        if (input.productivityProfile().limitedHistory()) {
            risks.add(new FocusBlockRisk(
                    FocusBlockRiskLevel.WARNING,
                    "История трекинга ограничена",
                    "Рекомендации построены по ограниченному объёму данных."
            ));
        }
        if (input.productivityProfile().switchDensityScore() >= 0.7) {
            risks.add(new FocusBlockRisk(
                    FocusBlockRiskLevel.WARNING,
                    "Высокая переключаемость",
                    "История показывает много коротких сессий. Длинные блоки стоит защищать от отвлечений."
            ));
        }
        if (candidates.stream().noneMatch(this::isDeepCandidate)) {
            risks.add(new FocusBlockRisk(
                    FocusBlockRiskLevel.INFO,
                    "Длинных окон почти нет",
                    "Сегодня мало чистых длинных слотов, поэтому фокус лучше строить короткими сериями."
            ));
        }
        return List.copyOf(risks);
    }

    private boolean isFocusWindow(FocusBlockRecommendation recommendation) {
        return recommendation != null
                && recommendation.available()
                && (recommendation.type() == FocusBlockType.DEEP_FOCUS || recommendation.durationMinutes() >= 45);
    }

    private boolean isShortWindow(FocusBlockRecommendation recommendation) {
        return recommendation != null
                && recommendation.available()
                && !isFocusWindow(recommendation);
    }

    private boolean isDeepCandidate(FocusBlockCandidate candidate) {
        return candidate != null
                && candidate.available()
                && (candidate.type() == FocusBlockType.DEEP_FOCUS || candidate.durationMinutes() >= 90);
    }

    private double averagedHourScore(FocusBlockCandidate candidate, FocusProductivityProfile profile) {
        if (candidate == null || candidate.startAt() == null || candidate.endAt() == null || !profile.available()) {
            return 0.0;
        }
        int startHour = candidate.startAt().getHour();
        int endHour = candidate.endAt().minusMinutes(1).getHour();
        double sum = 0.0;
        int count = 0;
        for (int hour = startHour; hour <= endHour; hour++) {
            int normalizedHour = Math.floorMod(hour, 24);
            double hourScore = profile.hourScores().stream()
                    .filter(score -> score.hourOfDay() == normalizedHour)
                    .mapToDouble(FocusHourScore::productivityScore)
                    .findFirst()
                    .orElse(0.0);
            sum += hourScore;
            count++;
        }
        return count <= 0 ? 0.0 : sum / count;
    }

    private double dayScore(DayOfWeek dayOfWeek, FocusProductivityProfile profile) {
        if (dayOfWeek == null || !profile.available()) {
            return 0.0;
        }
        return profile.dayScores().stream()
                .filter(score -> score.dayOfWeek() == dayOfWeek)
                .mapToDouble(FocusDayScore::productivityScore)
                .findFirst()
                .orElse(0.0);
    }

    private double durationScore(FocusBlockCandidate candidate) {
        if (candidate == null) {
            return 0.0;
        }
        if (candidate.durationMinutes() >= 120) {
            return 1.0;
        }
        if (candidate.durationMinutes() >= 90) {
            return 0.9;
        }
        if (candidate.durationMinutes() >= 60) {
            return 0.72;
        }
        if (candidate.durationMinutes() >= 45) {
            return 0.55;
        }
        if (candidate.durationMinutes() >= 25) {
            return 0.34;
        }
        return 0.20;
    }

    private double urgencyScore(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input) {
        if (candidate == null || candidate.startAt() == null) {
            return 0.0;
        }
        double urgencyPressure = Math.min(0.12, (input.overdueTaskCount() * 0.04) + (input.upcomingTaskCount() * 0.02));
        if (urgencyPressure <= 0.0) {
            return 0.0;
        }
        int hour = candidate.startAt().getHour();
        double earlyBias;
        if (hour <= 11) {
            earlyBias = 1.0;
        } else if (hour <= 14) {
            earlyBias = 0.65;
        } else if (hour <= 17) {
            earlyBias = 0.35;
        } else {
            earlyBias = 0.15;
        }
        return urgencyPressure * earlyBias;
    }

    private double overloadPenalty(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input) {
        if (candidate == null) {
            return 0.0;
        }
        double penalty = 0.0;
        if (candidate.approximate()) {
            penalty += 0.04;
        }
        if (input.activeTaskCount() >= 12 && candidate.durationMinutes() < 45) {
            penalty += 0.03;
        }
        if (input.productivityProfile().switchDensityScore() >= 0.75 && candidate.durationMinutes() >= 90) {
            penalty += 0.025;
        }
        return penalty;
    }

    private String buildTitle(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input) {
        String label = candidate.label().isBlank() && candidate.startAt() != null && candidate.endAt() != null
                ? formatRange(candidate.startAt(), candidate.endAt())
                : candidate.label();
        return switch (candidate.type()) {
            case DEEP_FOCUS -> "Фокус-блок " + label;
            case LIGHT_FOCUS -> "Рабочий блок " + label;
            case ADMIN -> "Короткий блок " + label;
            case RECOVERY -> "Восстановительный блок " + label;
        };
    }

    private String buildRationale(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input, double hourScore, double urgencyScore) {
        List<String> parts = new ArrayList<>();
        if (hourScore >= 0.65) {
            parts.add("это время исторически сильнее по трекингу");
        }
        if (candidate.durationMinutes() >= 90) {
            parts.add("окно достаточно длинное для глубокой работы");
        } else if (candidate.durationMinutes() >= 45) {
            parts.add("окно подходит для сфокусированной короткой сессии");
        } else {
            parts.add("окно лучше использовать для лёгких задач");
        }
        if (urgencyScore > 0.01) {
            parts.add("оно достаточно раннее для разгрузки срочных задач");
        }
        if (candidate.approximate()) {
            parts.add("оценка частично приблизительная");
        }
        return parts.isEmpty()
                ? "Окно выглядит пригодным по текущим данным."
                : String.join(", ", parts) + ".";
    }

    private String buildNextStep(FocusBlockCandidate candidate, FocusBlockRecommendationEngineInput input) {
        if (input.overdueTaskCount() > 0) {
            return "Используйте это окно для самой важной просроченной или блокирующей задачи.";
        }
        if (candidate.type() == FocusBlockType.DEEP_FOCUS) {
            return "Заблокируйте уведомления и заранее выберите одну главную сложную задачу.";
        }
        if (candidate.type() == FocusBlockType.ADMIN) {
            return "Закройте административные или мелкие задачи без контекстных переключений.";
        }
        return "Подготовьте одну конкретную цель на это окно и начните без разгона.";
    }

    private String formatRange(LocalDateTime start, LocalDateTime end) {
        return String.format("%02d:%02d-%02d:%02d",
                start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record RankedRecommendation(FocusBlockRecommendation recommendation, double score) {
    }
}
