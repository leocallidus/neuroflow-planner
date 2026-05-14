package com.example.neuroflowplanner.service.focusblocks;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FocusBlockContentFormatter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM uuuu");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private FocusBlockContentFormatter() {
    }

    public static String buildExportTitle(FocusBlockRecommendationResult result) {
        LocalDate reviewDate = result == null ? LocalDate.now() : result.reviewDate();
        return "Рекомендации фокус-блоков — " + DATE_FORMAT.format(reviewDate);
    }

    public static String toMarkdown(FocusBlockRecommendationResult result) {
        FocusBlockRecommendationResult safeResult = result == null
                ? new FocusBlockRecommendationResult(null, null, "", false, false)
                : result;
        FocusBlockRecommendationSnapshot snapshot = safeResult.snapshot();
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(buildExportTitle(safeResult)).append("\n\n");
        markdown.append("*Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()))
                .append("*\n\n");

        markdown.append("## Следующий рекомендуемый блок\n\n");
        FocusBlockRecommendation next = snapshot.nextRecommendedBlock();
        if (!next.available()) {
            markdown.append("- Подходящий фокус-блок пока не найден.\n");
        } else {
            markdown.append("**").append(escapeInline(next.title())).append("**\n\n");
            markdown.append("- Время: ")
                    .append(TIME_FORMAT.format(next.startAt()))
                    .append("-")
                    .append(TIME_FORMAT.format(next.endAt()))
                    .append("\n");
            markdown.append("- Длительность: ").append(next.durationMinutes()).append(" мин\n");
            markdown.append("- Тип: ").append(next.type().name().replace('_', ' ').toLowerCase()).append("\n");
            markdown.append("- Уверенность: ").append(formatPercent(next.confidence())).append("\n");
            if (!next.rationale().isBlank()) {
                markdown.append("\n").append(next.rationale()).append("\n");
            }
            if (!next.nextStep().isBlank()) {
                markdown.append("\n**Следующий шаг:** ").append(escapeInline(next.nextStep())).append("\n");
            }
        }

        markdown.append("\n## Объяснение рекомендации\n\n");
        FocusBlockExplanation explanation = safeResult.explanation();
        if (!explanation.headline().isBlank()) {
            markdown.append("**").append(escapeInline(explanation.headline())).append("**\n\n");
        }
        if (!explanation.summary().isBlank()) {
            markdown.append(explanation.summary()).append("\n\n");
        } else {
            markdown.append("- Объяснение пока недоступно.\n");
        }
        if (!explanation.nextAction().isBlank()) {
            markdown.append("**Что сделать дальше:** ").append(escapeInline(explanation.nextAction())).append("\n");
        }
        if (!explanation.limitations().isBlank()) {
            markdown.append("\n**Ограничения:** ").append(escapeInline(explanation.limitations())).append("\n");
        }

        markdown.append("\n## Лучшие фокус-окна\n\n");
        appendRecommendations(markdown, snapshot.focusWindows(), "Сильных длинных окон пока не найдено.");

        markdown.append("\n## Короткие окна\n\n");
        appendRecommendations(markdown, snapshot.shortWindows(), "Коротких рабочих окон сейчас нет.");

        markdown.append("\n## Риски\n\n");
        if (snapshot.risks().isEmpty()) {
            markdown.append("- Явных рисков для фокусной работы не найдено.\n");
        } else {
            for (FocusBlockRisk risk : snapshot.risks()) {
                markdown.append("- **")
                        .append(escapeInline(risk.title().isBlank() ? "Риск дня" : risk.title()))
                        .append("**");
                if (!risk.detail().isBlank()) {
                    markdown.append(" — ").append(escapeInline(risk.detail()));
                }
                markdown.append(" • ").append(risk.level().name().toLowerCase()).append("\n");
            }
        }

        markdown.append("\n## Профиль продуктивности\n\n");
        markdown.append("- Средняя фокус-сессия: ").append(snapshot.productivityProfile().averageFocusMinutes()).append(" мин\n");
        markdown.append("- Стабильный фокус: ").append(snapshot.productivityProfile().stableFocusMinutes()).append(" мин\n");
        markdown.append("- Уверенность профиля: ").append(formatPercent(snapshot.productivityProfile().confidence())).append("\n");
        markdown.append("- История: ").append(snapshot.limitedHistory() ? "ограничена" : "достаточна").append("\n");

        return markdown.toString().trim() + "\n";
    }

    public static String toChatSeedPrompt(FocusBlockRecommendationResult result) {
        FocusBlockRecommendationResult safeResult = result == null
                ? new FocusBlockRecommendationResult(null, null, "", false, false)
                : result;
        FocusBlockRecommendationSnapshot snapshot = safeResult.snapshot();
        StringBuilder prompt = new StringBuilder();
        prompt.append("Используй эти рекомендации фокус-блоков как стартовый контекст для планирования ближайшей глубокой работы.\n");
        prompt.append("Помоги уточнить, какой блок лучше взять следующим и как подготовиться к нему.\n\n");
        prompt.append("Дата рекомендаций: ").append(DATE_FORMAT.format(safeResult.reviewDate())).append("\n");
        prompt.append("Обновлено: ")
                .append(DATE_TIME_FORMAT.format(safeResult.generatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()))
                .append("\n\n");
        prompt.append(toMarkdown(safeResult));
        prompt.append("\nС учётом этих рекомендаций:\n");
        prompt.append("1. Коротко оцени лучший следующий блок.\n");
        prompt.append("2. Назови 1 главную причину, почему он подходит сейчас.\n");
        prompt.append("3. Предложи конкретный следующий шаг на ближайшие 5-10 минут.\n");
        if (snapshot.limitedHistory()) {
            prompt.append("4. Учти, что история трекинга пока ограничена.\n");
        }
        return prompt.toString().trim();
    }

    private static void appendRecommendations(StringBuilder markdown, List<FocusBlockRecommendation> items, String emptyFallback) {
        if (items == null || items.isEmpty()) {
            markdown.append("- ").append(emptyFallback).append("\n");
            return;
        }
        for (FocusBlockRecommendation item : items) {
            markdown.append("- **").append(escapeInline(item.title())).append("**");
            if (item.available()) {
                markdown.append(" — ")
                        .append(TIME_FORMAT.format(item.startAt()))
                        .append("-")
                        .append(TIME_FORMAT.format(item.endAt()))
                        .append(" • ")
                        .append(item.durationMinutes())
                        .append(" мин");
            }
            markdown.append(" • уверенность ").append(formatPercent(item.confidence())).append("\n");
        }
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
