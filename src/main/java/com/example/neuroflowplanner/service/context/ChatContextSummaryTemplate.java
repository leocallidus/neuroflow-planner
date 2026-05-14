package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.ai.AiRequestOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChatContextSummaryTemplate {

    public static final String TITLE = "Сводка предыдущего контекста";
    public static final String SECTION_DECISIONS = "## Ключевые решения";
    public static final String SECTION_GOALS = "## Цели пользователя";
    public static final String SECTION_FACTS = "## Важные факты";
    public static final String SECTION_OPEN_QUESTIONS = "## Незавершенные вопросы";
    public static final String SECTION_CONSTRAINTS = "## Ограничения";
    public static final String SECTION_ARTIFACTS = "## Вложения и артефакты";

    private static final List<String> REQUIRED_SECTIONS = List.of(
        SECTION_DECISIONS,
        SECTION_GOALS,
        SECTION_FACTS,
        SECTION_OPEN_QUESTIONS,
        SECTION_CONSTRAINTS,
        SECTION_ARTIFACTS
    );
    private static final int MAX_PROMPT_CHARS = 24_000;
    private static final int MAX_ENTRY_CHARS = 1_200;

    private static final List<String> GENERIC_PATTERNS = List.of(
        "обсуждались разные вопросы",
        "затрагивались разные темы",
        "в диалоге рассматривались различные аспекты",
        "были даны общие рекомендации",
        "контекст разговора сохранен"
    );

    private ChatContextSummaryTemplate() {
    }

    public static String buildSystemPrompt() {
        return """
            Ты сжимаешь историю переписки NeuroFlow в рабочую summary-память для следующего запроса модели.
            Твоя задача: сохранить только конкретный и полезный контекст, чтобы следующий ответ понимал беседу без исходного длинного лога.

            Обязательные правила:
            1. Не пиши общие формулировки и абстрактные фразы.
            2. Не добавляй факты, которых нет в переписке.
            3. Сохраняй только конкретные решения, цели, ограничения, артефакты и открытые вопросы.
            4. Если для раздела данных нет, пиши один bullet: "- Нет явных данных."
            5. Ответ должен быть на русском языке.
            6. Верни summary строго в этом формате и в этом порядке:

            Сводка предыдущего контекста
            ## Ключевые решения
            - ...
            ## Цели пользователя
            - ...
            ## Важные факты
            - ...
            ## Незавершенные вопросы
            - ...
            ## Ограничения
            - ...
            ## Вложения и артефакты
            - ...

            7. Не добавляй вступление, послесловие, markdown-блоки кода или пояснения вне формата.
            """;
    }

    public static String buildUserPrompt(ChatContextManager.ContextSummarizationInput input) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Собери качественную сводку контекста для продолжения этой переписки.\n\n");
        if (input == null) {
            prompt.append("Данных для summarize нет.");
            return prompt.toString();
        }
        prompt.append("Нужно покрыть сообщений истории: ").append(input.coveredMessages()).append('\n');
        prompt.append("Всего сообщений в переписке: ").append(input.totalHistoryMessages()).append("\n\n");

        if (input.existingSummary() != null && !input.existingSummary().isBlank()) {
            prompt.append("Текущая summary-память, которую нужно учесть:\n");
            prompt.append(input.existingSummary().trim()).append("\n\n");
        }

        if (input.pinnedFacts() != null && !input.pinnedFacts().isEmpty()) {
            prompt.append("Закреплённые факты:\n");
            for (String fact : input.pinnedFacts()) {
                if (fact == null || fact.isBlank()) {
                    continue;
                }
                prompt.append("- ").append(fact.trim()).append('\n');
            }
            prompt.append('\n');
        }

        prompt.append("Фрагмент переписки, который нужно сжать:\n");
        if (input.entriesToCover() == null || input.entriesToCover().isEmpty()) {
            prompt.append("- Нет данных.\n");
            return prompt.toString();
        }
        for (AiRequestOptions.ChatHistoryEntry entry : input.entriesToCover()) {
            if (entry == null || entry.content() == null || entry.content().isBlank()) {
                continue;
            }
            String role = "assistant".equalsIgnoreCase(entry.role()) ? "Ассистент" : "Пользователь";
            String content = entry.content().trim();
            if (content.length() > MAX_ENTRY_CHARS) {
                content = content.substring(0, MAX_ENTRY_CHARS).trim() + "...";
            }
            prompt.append('[')
                .append(role)
                .append("] ")
                .append(content)
                .append("\n");
            if (prompt.length() >= MAX_PROMPT_CHARS) {
                prompt.append("\n[TRUNCATED] История была обрезана до безопасного размера prompt.\n");
                break;
            }
        }
        return prompt.toString().trim();
    }

    public static String normalizeSummary(String rawSummary) {
        if (rawSummary == null) {
            return "";
        }
        return rawSummary
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[ \\t]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    public static boolean isAcceptable(String rawSummary) {
        String summary = normalizeSummary(rawSummary);
        if (summary.isBlank() || summary.length() < 180) {
            return false;
        }
        if (!summary.startsWith(TITLE)) {
            return false;
        }
        for (String section : REQUIRED_SECTIONS) {
            if (!summary.contains(section)) {
                return false;
            }
        }
        String normalizedLower = summary.toLowerCase(Locale.ROOT);
        for (String pattern : GENERIC_PATTERNS) {
            if (normalizedLower.contains(pattern)) {
                return false;
            }
        }
        return countMeaningfulBullets(summary) >= 4;
    }

    private static int countMeaningfulBullets(String summary) {
        int count = 0;
        for (String line : summary.split("\n")) {
            String normalized = line == null ? "" : line.trim();
            if (!normalized.startsWith("- ")) {
                continue;
            }
            String value = normalized.substring(2).trim().toLowerCase(Locale.ROOT);
            if (value.isBlank() || value.equals("нет явных данных.")) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static List<String> requiredSections() {
        return new ArrayList<>(REQUIRED_SECTIONS);
    }
}
