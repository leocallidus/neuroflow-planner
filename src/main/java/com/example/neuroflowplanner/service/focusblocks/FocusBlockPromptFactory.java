package com.example.neuroflowplanner.service.focusblocks;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class FocusBlockPromptFactory {

    private static final int MAX_CANDIDATES_PER_SECTION = 4;
    private static final int MAX_REASON_LENGTH = 150;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private FocusBlockPromptFactory() {
    }

    public static FocusBlockAiPromptPayload build(FocusBlockRecommendationSnapshot snapshot) {
        FocusBlockRecommendationSnapshot safeSnapshot = snapshot == null
                ? new FocusBlockRecommendationSnapshot(
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        true
                )
                : snapshot;
        return new FocusBlockAiPromptPayload(
                buildSystemPrompt(),
                buildUserPrompt(safeSnapshot),
                buildFallbackExplanation(safeSnapshot)
        );
    }

    public static String buildSystemPrompt() {
        return """
                Ты формируешь компактное объяснение рекомендаций фокус-блоков для NeuroFlow Planner.
                Основа решения уже посчитана детерминированно. Твоя задача не придумывать новые окна, а объяснить лучший блок и возможный fallback.

                Цель ответа:
                - коротко назвать главный рекомендованный блок;
                - объяснить, почему он подходит;
                - подсказать, что делать, если пользователь пропустит это окно.

                Обязательные правила:
                1. Ответ должен быть на русском языке.
                2. Не выдумывай слоты, дедлайны, продуктивные часы или причины, которых нет во входных данных.
                3. Не пересказывай сырую историю трекинга.
                4. Опирайся только на структурированный recommendation snapshot.
                5. Не используй общие фразы вроде "работайте в удобное время" или "важно сохранять баланс".
                6. Если история ограничена, честно отрази это.
                7. Не добавляй вступление или послесловие вне формата.

                Верни ответ строго в таком формате:
                ## Главный блок
                - 1 bullet в формате "Лучший блок сейчас: ..."
                ## Почему он подходит
                - 2 коротких bullet
                ## Если окно пропустить
                - 1 bullet с конкретным fallback-действием
                """;
    }

    public static String buildUserPrompt(FocusBlockRecommendationSnapshot snapshot) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Собери объяснение рекомендаций фокус-блоков на сегодня.\n\n");
        prompt.append("Дата: ").append(snapshot.reviewDate().format(DATE_FORMAT)).append('\n');
        prompt.append("История ограничена: ").append(snapshot.limitedHistory() ? "да" : "нет").append('\n');
        prompt.append("Доступен профиль продуктивности: ").append(snapshot.productivityProfile().available() ? "да" : "нет").append('\n');
        prompt.append("Уверенность профиля: ").append(formatScore(snapshot.productivityProfile().confidence())).append('\n');
        prompt.append("Переключаемость: ").append(formatScore(snapshot.productivityProfile().switchDensityScore())).append('\n');
        prompt.append("Всего tracked minutes: ").append(snapshot.productivityProfile().totalTrackedMinutes()).append('\n');
        prompt.append("Всего sessions: ").append(snapshot.productivityProfile().totalSessions()).append('\n');
        prompt.append("Средняя фокус-сессия: ").append(snapshot.productivityProfile().averageFocusMinutes()).append(" мин\n");
        prompt.append("Устойчивая длина фокуса: ").append(snapshot.productivityProfile().stableFocusMinutes()).append(" мин\n\n");

        appendNextBlock(prompt, snapshot.nextRecommendedBlock());
        appendRecommendationSection(prompt, "### Лучшие фокус-окна", snapshot.focusWindows());
        appendRecommendationSection(prompt, "### Короткие окна", snapshot.shortWindows());
        appendCandidateSection(prompt, snapshot.candidateWindows());
        appendRiskSection(prompt, snapshot.risks());
        appendProfileHints(prompt, snapshot.productivityProfile());

        return prompt.toString().trim();
    }

    public static FocusBlockExplanation buildFallbackExplanation(FocusBlockRecommendationSnapshot snapshot) {
        FocusBlockExplanation existing = snapshot.explanation();
        if (existing != null && existing.source() == FocusBlockSummarySource.FALLBACK && existing.available()) {
            return existing;
        }

        FocusBlockRecommendation next = snapshot.nextRecommendedBlock();
        List<String> rationaleParts = new ArrayList<>();
        if (next.available()) {
            if (next.type() == FocusBlockType.DEEP_FOCUS) {
                rationaleParts.add("окно достаточно длинное для глубокой работы");
            } else if (next.durationMinutes() >= 45) {
                rationaleParts.add("окно подходит для целевой короткой сессии");
            } else {
                rationaleParts.add("окно лучше использовать для лёгких задач");
            }
            if (next.confidence() >= 0.7) {
                rationaleParts.add("у рекомендации хорошая уверенность");
            }
            if (snapshot.productivityProfile().available() && snapshot.productivityProfile().confidence() >= 0.6) {
                rationaleParts.add("она поддержана историей трекинга");
            }
        }
        if (snapshot.limitedHistory()) {
            rationaleParts.add("история трекинга пока ограничена");
        }
        if (snapshot.hasRisks()) {
            rationaleParts.add("в дне есть риски, которые стоит разгрузить как можно раньше");
        }

        String headline;
        String summary;
        String nextAction;
        if (next.available()) {
            headline = "Лучшее рабочее окно уже найдено";
            summary = "Лучший блок сейчас: " + formatRecommendation(next) + ". "
                    + (rationaleParts.isEmpty() ? "" : String.join(", ", rationaleParts) + ".");
            FocusBlockRecommendation fallback = !snapshot.focusWindows().isEmpty()
                    && snapshot.focusWindows().size() > 1
                    ? snapshot.focusWindows().get(1)
                    : !snapshot.shortWindows().isEmpty()
                    ? snapshot.shortWindows().getFirst()
                    : null;
            nextAction = fallback != null && fallback.available()
                    ? "Если это окно пропустить, используйте следующий доступный блок " + formatRecommendation(fallback) + "."
                    : "Если это окно пропустить, пересчитайте рекомендации и выберите ближайший чистый слот длительностью не меньше 30 минут.";
        } else {
            headline = "Сильного фокус-блока пока нет";
            summary = snapshot.hasRisks()
                    ? "Сегодня нет надёжного длинного окна, поэтому день лучше вести короткими управляемыми сериями."
                    : "На сегодня пока не найдено уверенного фокус-блока по текущим данным.";
            nextAction = "Соберите больше истории трекинга и пересчитайте рекомендации позже.";
        }

        String limitations = snapshot.limitedHistory()
                ? "Рекомендация основана на ограниченной истории трекинга."
                : "";
        return new FocusBlockExplanation(
                FocusBlockSummarySource.FALLBACK,
                headline,
                summary,
                nextAction,
                limitations
        );
    }

    private static void appendNextBlock(StringBuilder prompt, FocusBlockRecommendation recommendation) {
        prompt.append("### Следующий рекомендуемый блок\n");
        if (recommendation == null || !recommendation.available()) {
            prompt.append("- Уверенного следующего блока нет.\n\n");
            return;
        }
        prompt.append("- ").append(formatRecommendation(recommendation))
                .append(" | тип: ").append(recommendation.type())
                .append(" | suitability: ").append(formatScore(recommendation.suitabilityScore()))
                .append(" | confidence: ").append(formatScore(recommendation.confidence()))
                .append('\n');
        if (!recommendation.rationale().isBlank()) {
            prompt.append("- rationale: ").append(limit(recommendation.rationale())).append('\n');
        }
        if (!recommendation.nextStep().isBlank()) {
            prompt.append("- next step: ").append(limit(recommendation.nextStep())).append('\n');
        }
        prompt.append('\n');
    }

    private static void appendRecommendationSection(StringBuilder prompt, String title, List<FocusBlockRecommendation> recommendations) {
        prompt.append(title).append('\n');
        if (recommendations == null || recommendations.isEmpty()) {
            prompt.append("- Нет данных.\n\n");
            return;
        }
        for (FocusBlockRecommendation recommendation : recommendations.stream().limit(MAX_CANDIDATES_PER_SECTION).toList()) {
            prompt.append("- ").append(formatRecommendation(recommendation))
                    .append(" | тип: ").append(recommendation.type())
                    .append(" | suitability: ").append(formatScore(recommendation.suitabilityScore()))
                    .append(" | confidence: ").append(formatScore(recommendation.confidence()))
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendCandidateSection(StringBuilder prompt, List<FocusBlockCandidate> candidates) {
        prompt.append("### Candidate windows\n");
        if (candidates == null || candidates.isEmpty()) {
            prompt.append("- Нет candidate windows.\n\n");
            return;
        }
        for (FocusBlockCandidate candidate : candidates.stream().limit(MAX_CANDIDATES_PER_SECTION).toList()) {
            prompt.append("- ").append(candidate.label().isBlank() ? formatRange(candidate.startAt(), candidate.endAt()) : candidate.label())
                    .append(" | тип: ").append(candidate.type())
                    .append(" | suitability: ").append(formatScore(candidate.suitabilityScore()))
                    .append(" | confidence: ").append(formatScore(candidate.confidence()))
                    .append(candidate.approximate() ? " | approximate" : "")
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendRiskSection(StringBuilder prompt, List<FocusBlockRisk> risks) {
        prompt.append("### Риски\n");
        if (risks == null || risks.isEmpty()) {
            prompt.append("- Явных рисков не найдено.\n\n");
            return;
        }
        for (FocusBlockRisk risk : risks.stream().limit(MAX_CANDIDATES_PER_SECTION).toList()) {
            prompt.append("- ").append(limit(risk.title()))
                    .append(" | ").append(risk.level())
                    .append(" | ").append(limit(risk.detail()))
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static void appendProfileHints(StringBuilder prompt, FocusProductivityProfile profile) {
        prompt.append("### Подсказки профиля\n");
        if (profile == null || !profile.available()) {
            prompt.append("- Профиль продуктивности пока недоступен.\n\n");
            return;
        }
        FocusHourScore bestHour = profile.hourScores().stream()
                .max(java.util.Comparator.comparingDouble(FocusHourScore::productivityScore))
                .orElse(null);
        FocusDayScore bestDay = profile.dayScores().stream()
                .max(java.util.Comparator.comparingDouble(FocusDayScore::productivityScore))
                .orElse(null);
        if (bestHour != null) {
            prompt.append("- Лучший час дня: ")
                    .append(String.format("%02d:00", bestHour.hourOfDay()))
                    .append(" | score: ").append(formatScore(bestHour.productivityScore()))
                    .append('\n');
        }
        if (bestDay != null) {
            prompt.append("- Лучший день недели: ").append(bestDay.dayOfWeek())
                    .append(" | score: ").append(formatScore(bestDay.productivityScore()))
                    .append('\n');
        }
        prompt.append('\n');
    }

    private static String formatRecommendation(FocusBlockRecommendation recommendation) {
        return formatRange(recommendation.startAt(), recommendation.endAt()) + " (" + recommendation.durationMinutes() + " мин)";
    }

    private static String formatRange(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        if (start == null || end == null) {
            return "неизвестное окно";
        }
        return start.format(DATE_TIME_FORMAT) + "-" + end.format(DATE_TIME_FORMAT);
    }

    private static String formatScore(double value) {
        return String.format(java.util.Locale.US, "%.2f", Math.max(0.0, Math.min(1.0, value)));
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_REASON_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_REASON_LENGTH - 1) + "…";
    }
}
