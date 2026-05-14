package com.example.neuroflowplanner.service.context;

import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiTextModelContextResolver;
import com.example.neuroflowplanner.ai.AiTextModelParameterResolver;
import com.example.neuroflowplanner.ai.dto.AiTextModelContextMetadata;
import com.example.neuroflowplanner.ai.dto.AiTextModelParameterMetadata;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetEstimator;
import com.example.neuroflowplanner.service.context.budget.ChatContextBudgetSnapshot;
import com.example.neuroflowplanner.util.ConfigManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory context manager with per-conversation state and deterministic
 * context building.
 */
public final class ChatContextManager {

    private static final String UNKNOWN_CONVERSATION_ID = "conversation:unknown";

    private static final int DEFAULT_RECENT_WINDOW_MESSAGES = 12;
    private static final int DEFAULT_SUMMARY_TRIGGER_MESSAGES = 30;
    private static final int DEFAULT_MAX_BUDGET_TOKENS = 12000;
    private static final int DEFAULT_MINIMAL_BUDGET_TOKENS = 2500;
    private static final int DEFAULT_MAX_PINNED_FACTS = 16;
    private static final int MAX_SUMMARY_LINES = 14;
    private static final int MAX_SUMMARY_CHARS = 4000;
    private static final int MAX_PINNED_FACT_LENGTH = 240;
    private static final int MAX_HISTORY_ITEM_CHARS = 8000;

    private final ConcurrentHashMap<String, ConversationContext> contexts = new ConcurrentHashMap<>();
    private final ChatContextBudgetEstimator budgetEstimator = new ChatContextBudgetEstimator();

    public void appendUserMessage(String conversationId, String text) {
        appendHistoryEntry(conversationId, AiRequestOptions.ChatHistoryEntry.user(normalizeMessage(text)));
    }

    public void appendAssistantMessage(String conversationId, String text) {
        appendHistoryEntry(conversationId, AiRequestOptions.ChatHistoryEntry.assistant(normalizeMessage(text)));
    }

    public void rollbackLastMessage(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            if (!state.history.isEmpty()) {
                state.history.remove(state.history.size() - 1);
                if (state.summaryCoveredMessages > state.history.size()) {
                    state.summaryCoveredMessages = 0;
                    state.summary = "";
                }
            }
        }
    }

    public void replaceHistory(String conversationId, List<AiRequestOptions.ChatHistoryEntry> entries) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            state.history.clear();
            if (entries != null && !entries.isEmpty()) {
                for (AiRequestOptions.ChatHistoryEntry entry : entries) {
                    if (entry == null || entry.role() == null || entry.content() == null) {
                        continue;
                    }
                    String normalizedRole = entry.role().trim().toLowerCase();
                    if (normalizedRole.isBlank()) {
                        continue;
                    }
                    String normalizedContent = normalizeMessage(entry.content());
                    if (normalizedContent.isBlank()) {
                        continue;
                    }
                    state.history.add(new AiRequestOptions.ChatHistoryEntry(normalizedRole, normalizedContent));
                }
            }
            if (state.summaryCoveredMessages > state.history.size()) {
                state.summary = "";
                state.summaryCoveredMessages = 0;
            }
            autoRebuildSummaryIfNeeded(state);
        }
    }

    public ChatContextBuildResult buildContext(String conversationId, ChatContextMode mode) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            autoRebuildSummaryIfNeeded(state);
            ChatContextMode requested = mode == null ? state.preferredMode : mode;
            ChatContextBuildResult primary = buildContextLocked(state, requested, false);
            if (primary.estimatedTokens() <= maxBudgetTokens() || primary.effectiveMode() == ChatContextMode.MINIMAL) {
                return primary;
            }
            ChatContextBuildResult degraded = buildContextLocked(state, ChatContextMode.MINIMAL, true);
            return new ChatContextBuildResult(
                    degraded.entries(),
                    requested,
                    degraded.effectiveMode(),
                    degraded.estimatedTokens(),
                    degraded.selectedHistoryMessages(),
                    degraded.totalHistoryMessages(),
                    degraded.pinnedFactsCount(),
                    degraded.summaryIncluded(),
                    true,
                    true);
        }
    }

    public ChatContextBudgetSnapshot buildBudgetSnapshot(String conversationId, String modelId, ChatContextMode mode) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String safeModelId = modelId == null ? "" : modelId.trim();
        ChatContextBuildResult context = buildContext(normalizedConversationId, mode);
        AiTextModelContextMetadata contextMetadata = AiTextModelContextResolver.resolveForModel(safeModelId);
        AiTextModelParameterMetadata parameterMetadata = AiTextModelParameterResolver.resolveForModel(safeModelId);
        return budgetEstimator.estimate(
                normalizedConversationId,
                safeModelId,
                context,
                contextMetadata,
                parameterMetadata,
                ConfigManager.getAssistantTextMaxTokens());
    }

    public void pinContextItem(String conversationId, String item) {
        String normalized = normalizePinnedFact(item);
        if (normalized.isBlank()) {
            return;
        }
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            for (String current : state.pinnedFacts) {
                if (current.equalsIgnoreCase(normalized)) {
                    return;
                }
            }
            state.pinnedFacts.add(normalized);
            if (state.pinnedFacts.size() > maxPinnedFacts()) {
                state.pinnedFacts.remove(0);
            }
        }
    }

    public String rebuildSummary(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            int coverageTarget = coverageTargetForSummary(state);
            String summary = buildSummaryLocked(state, coverageTarget);
            applySummaryLocked(state, summary, coverageTarget);
            return summary;
        }
    }

    public ContextSummarizationInput buildSummarizationInput(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            int coverageTarget = coverageTargetForSummary(state);
            List<AiRequestOptions.ChatHistoryEntry> entriesToCover = coverageTarget <= 0
                ? List.of()
                : new ArrayList<>(state.history.subList(0, coverageTarget));
            return new ContextSummarizationInput(
                entriesToCover,
                List.copyOf(state.pinnedFacts),
                coverageTarget,
                state.history.size(),
                state.summary == null ? "" : state.summary,
                buildSummaryLocked(state, coverageTarget)
            );
        }
    }

    public String applySummary(String conversationId, String summary, int coveredMessages) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            applySummaryLocked(state, summary, coveredMessages);
            return state.summary;
        }
    }

    public void clearContext(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            contexts.clear();
            return;
        }
        contexts.remove(normalizeConversationId(conversationId));
    }

    public int getHistorySize(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            return state.history.size();
        }
    }

    public void setPreferredMode(String conversationId, ChatContextMode mode) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            state.preferredMode = mode == null ? ChatContextMode.AUTO : mode;
        }
    }

    public ChatContextMode getPreferredMode(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            return state.preferredMode == null ? ChatContextMode.AUTO : state.preferredMode;
        }
    }

    public ContextPersistentState exportPersistentState(String conversationId) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            return new ContextPersistentState(
                state.preferredMode == null ? ChatContextMode.AUTO : state.preferredMode,
                state.summary == null ? "" : state.summary,
                Math.max(0, state.summaryCoveredMessages),
                List.copyOf(state.pinnedFacts),
                Math.max(0, state.activeSummaryRevision),
                state.lastSummarizedAt == null ? "" : state.lastSummarizedAt
            );
        }
    }

    public void restorePersistentState(String conversationId, ContextPersistentState snapshot) {
        if (snapshot == null) {
            return;
        }
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            state.preferredMode = snapshot.preferredMode() == null ? ChatContextMode.AUTO : snapshot.preferredMode();
            state.summary = snapshot.summary() == null ? "" : snapshot.summary().trim();
            state.summaryCoveredMessages = Math.max(0, snapshot.summaryCoveredMessages());
            state.pinnedFacts.clear();
            state.activeSummaryRevision = Math.max(0, snapshot.activeSummaryRevision());
            state.lastSummarizedAt = snapshot.lastSummarizedAt() == null ? "" : snapshot.lastSummarizedAt().trim();
            if (snapshot.pinnedFacts() != null && !snapshot.pinnedFacts().isEmpty()) {
                for (String fact : snapshot.pinnedFacts()) {
                    String normalized = normalizePinnedFact(fact);
                    if (normalized.isBlank()) {
                        continue;
                    }
                    state.pinnedFacts.add(normalized);
                    if (state.pinnedFacts.size() >= maxPinnedFacts()) {
                        break;
                    }
                }
            }
            if (state.summaryCoveredMessages > state.history.size()) {
                state.summaryCoveredMessages = Math.max(0, state.history.size());
            }
        }
    }

    private void appendHistoryEntry(String conversationId, AiRequestOptions.ChatHistoryEntry entry) {
        ConversationContext state = getState(conversationId);
        synchronized (state) {
            state.history.add(entry);
            if (state.history.size() > summaryTriggerMessages() * 3) {
                autoRebuildSummaryIfNeeded(state);
            }
        }
    }

    private ChatContextBuildResult buildContextLocked(
            ConversationContext state,
            ChatContextMode mode,
            boolean overflowProtected) {
        ChatContextMode effectiveMode = mode == null ? ChatContextMode.AUTO : mode;
        List<AiRequestOptions.ChatHistoryEntry> selectedHistory = switch (effectiveMode) {
            case RECENT -> selectRecentHistory(state.history);
            case FULL -> new ArrayList<>(state.history);
            case MINIMAL -> selectMinimalHistory(state.history);
            case AUTO -> selectRecentHistory(state.history);
        };

        List<AiRequestOptions.ChatHistoryEntry> entries = new ArrayList<>();

        int pinnedCount = state.pinnedFacts.size();
        if (pinnedCount > 0) {
            entries.add(AiRequestOptions.ChatHistoryEntry.system(renderPinnedFacts(state.pinnedFacts)));
        }

        boolean includeSummary = shouldIncludeSummary(state, effectiveMode);
        if (includeSummary) {
            entries.add(AiRequestOptions.ChatHistoryEntry.system(state.summary));
        }

        entries.addAll(selectedHistory);

        int estimatedTokens = estimateTokens(entries);
        if (effectiveMode == ChatContextMode.MINIMAL && estimatedTokens > minimalBudgetTokens()) {
            List<AiRequestOptions.ChatHistoryEntry> strictMinimal = selectStrictMinimalHistory(state.history);
            entries.clear();
            if (pinnedCount > 0) {
                entries.add(AiRequestOptions.ChatHistoryEntry.system(renderPinnedFactsLimited(state.pinnedFacts, 6)));
            }
            entries.addAll(strictMinimal);
            estimatedTokens = estimateTokens(entries);
        }

        return new ChatContextBuildResult(
                entries,
                mode,
                effectiveMode,
                estimatedTokens,
                selectedHistory.size(),
                state.history.size(),
                pinnedCount,
                includeSummary,
                overflowProtected,
                overflowProtected && effectiveMode == ChatContextMode.MINIMAL);
    }

    private boolean shouldIncludeSummary(ConversationContext state, ChatContextMode mode) {
        if (state.summary == null || state.summary.isBlank()) {
            return false;
        }
        return mode == ChatContextMode.AUTO || mode == ChatContextMode.FULL;
    }

    private List<AiRequestOptions.ChatHistoryEntry> selectRecentHistory(
            List<AiRequestOptions.ChatHistoryEntry> history) {
        int size = history.size();
        if (size == 0) {
            return List.of();
        }
        int window = recentWindowMessages();
        int from = Math.max(0, size - window);
        return new ArrayList<>(history.subList(from, size));
    }

    private List<AiRequestOptions.ChatHistoryEntry> selectMinimalHistory(
            List<AiRequestOptions.ChatHistoryEntry> history) {
        if (history.isEmpty()) {
            return List.of();
        }
        int size = history.size();
        int from = Math.max(0, size - 2);
        return new ArrayList<>(history.subList(from, size));
    }

    private List<AiRequestOptions.ChatHistoryEntry> selectStrictMinimalHistory(
            List<AiRequestOptions.ChatHistoryEntry> history) {
        if (history.isEmpty()) {
            return List.of();
        }
        AiRequestOptions.ChatHistoryEntry last = history.get(history.size() - 1);
        if ("user".equalsIgnoreCase(last.role())) {
            return List.of(last);
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AiRequestOptions.ChatHistoryEntry entry = history.get(i);
            if ("user".equalsIgnoreCase(entry.role())) {
                return List.of(entry, last);
            }
        }
        return List.of(last);
    }

    private void autoRebuildSummaryIfNeeded(ConversationContext state) {
        if (state.history.size() < summaryTriggerMessages()) {
            return;
        }
        if (state.summary != null && !state.summary.isBlank()) {
            return;
        }
        int coverageTarget = Math.max(0, state.history.size() - recentWindowMessages());
        if (coverageTarget <= state.summaryCoveredMessages) {
            return;
        }
        applySummaryLocked(state, buildSummaryLocked(state, coverageTarget), coverageTarget);
    }

    private String buildSummaryLocked(ConversationContext state, int coveredMessages) {
        int oldCount = Math.max(0, Math.min(coveredMessages, state.history.size()));
        if (oldCount < 2) {
            return "";
        }
        List<String> decisions = collectSectionSnippets(state.history, 0, oldCount, false, false, 3);
        List<String> goals = collectSectionSnippets(state.history, 0, oldCount, true, false, 4);
        List<String> facts = collectSectionSnippets(state.history, 0, oldCount, false, false, 5);
        List<String> openQuestions = collectQuestionSnippets(state.history, oldCount, 4);
        List<String> constraints = collectKeywordSnippets(
            state.history,
            0,
            oldCount,
            List.of("срок", "дедлайн", "лимит", "огранич", "нельзя", "нужно", "бюджет", "формат", "требован"),
            4
        );
        List<String> artifacts = collectKeywordSnippets(
            state.history,
            0,
            oldCount,
            List.of("pdf", "docx", "txt", "файл", "влож", "изображ", "картин", "аудио", "mp3", "wav", "flac", "m4a", "ссылк"),
            4
        );
        return renderStructuredSummary(decisions, goals, facts, openQuestions, constraints, artifacts);
    }

    private void applySummaryLocked(ConversationContext state, String summary, int coveredMessages) {
        state.summary = summary == null ? "" : summary.trim();
        state.summaryCoveredMessages = Math.max(0, Math.min(coveredMessages, state.history.size()));
        state.activeSummaryRevision = Math.max(0, state.activeSummaryRevision) + 1;
        state.lastSummarizedAt = Instant.now().toString();
    }

    private int coverageTargetForSummary(ConversationContext state) {
        int coverage = Math.max(0, state.history.size() - recentWindowMessages());
        if (coverage > 0) {
            return coverage;
        }
        if (state.history.size() >= 4) {
            return state.history.size();
        }
        return 0;
    }

    private List<String> collectSectionSnippets(
            List<AiRequestOptions.ChatHistoryEntry> history,
            int fromInclusive,
            int toExclusive,
            boolean preferUser,
            boolean preferAssistant,
            int limit) {
        if (history == null || history.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int step = Math.max(1, Math.max(1, toExclusive - fromInclusive) / Math.max(1, MAX_SUMMARY_LINES));
        for (int i = fromInclusive; i < toExclusive && result.size() < limit; i += step) {
            AiRequestOptions.ChatHistoryEntry entry = history.get(i);
            if (entry == null) {
                continue;
            }
            boolean isUser = "user".equalsIgnoreCase(entry.role());
            boolean isAssistant = "assistant".equalsIgnoreCase(entry.role());
            if (preferUser && !isUser) {
                continue;
            }
            if (preferAssistant && !isAssistant) {
                continue;
            }
            String content = normalizeSummarySnippet(entry.content());
            if (content.isBlank()) {
                continue;
            }
            result.add(toBulletSnippet(content));
        }
        return deduplicateSnippets(result, limit);
    }

    private List<String> collectQuestionSnippets(
            List<AiRequestOptions.ChatHistoryEntry> history,
            int toExclusive,
            int limit) {
        if (history == null || history.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = Math.max(0, toExclusive - 1); i >= 0 && result.size() < limit; i--) {
            AiRequestOptions.ChatHistoryEntry entry = history.get(i);
            if (entry == null || !"user".equalsIgnoreCase(entry.role())) {
                continue;
            }
            String content = normalizeSummarySnippet(entry.content());
            if (content.isBlank()) {
                continue;
            }
            if (content.contains("?") || containsAny(content, List.of("как", "нужно", "почему", "что делать", "какой"))) {
                result.add(toBulletSnippet(content));
            }
        }
        return deduplicateSnippets(result, limit);
    }

    private List<String> collectKeywordSnippets(
            List<AiRequestOptions.ChatHistoryEntry> history,
            int fromInclusive,
            int toExclusive,
            List<String> keywords,
            int limit) {
        if (history == null || history.isEmpty() || keywords == null || keywords.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (int i = fromInclusive; i < toExclusive && result.size() < limit; i++) {
            AiRequestOptions.ChatHistoryEntry entry = history.get(i);
            if (entry == null) {
                continue;
            }
            String content = normalizeSummarySnippet(entry.content());
            if (content.isBlank() || !containsAny(content, keywords)) {
                continue;
            }
            result.add(toBulletSnippet(content));
        }
        return deduplicateSnippets(result, limit);
    }

    private List<String> deduplicateSnippets(List<String> snippets, int limit) {
        if (snippets == null || snippets.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String snippet : snippets) {
            if (snippet == null || snippet.isBlank()) {
                continue;
            }
            boolean exists = false;
            for (String current : result) {
                if (current.equalsIgnoreCase(snippet)) {
                    exists = true;
                    break;
                }
            }
            if (exists) {
                continue;
            }
            result.add(snippet);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean containsAny(String content, List<String> keywords) {
        String normalized = content == null ? "" : content.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && normalized.contains(keyword.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String toBulletSnippet(String content) {
        String snippet = content == null ? "" : content.trim();
        if (snippet.length() > 160) {
            snippet = snippet.substring(0, 160).trim() + "...";
        }
        return snippet;
    }

    private String renderStructuredSummary(
            List<String> decisions,
            List<String> goals,
            List<String> facts,
            List<String> openQuestions,
            List<String> constraints,
            List<String> artifacts) {
        StringBuilder summary = new StringBuilder();
        summary.append(ChatContextSummaryTemplate.TITLE).append('\n');
        appendSection(summary, ChatContextSummaryTemplate.SECTION_DECISIONS, decisions);
        appendSection(summary, ChatContextSummaryTemplate.SECTION_GOALS, goals);
        appendSection(summary, ChatContextSummaryTemplate.SECTION_FACTS, facts);
        appendSection(summary, ChatContextSummaryTemplate.SECTION_OPEN_QUESTIONS, openQuestions);
        appendSection(summary, ChatContextSummaryTemplate.SECTION_CONSTRAINTS, constraints);
        appendSection(summary, ChatContextSummaryTemplate.SECTION_ARTIFACTS, artifacts);
        String result = summary.toString().trim();
        if (result.length() <= MAX_SUMMARY_CHARS) {
            return result;
        }
        return result.substring(0, MAX_SUMMARY_CHARS).trim() + "...";
    }

    private void appendSection(StringBuilder summary, String title, List<String> items) {
        summary.append(title).append('\n');
        if (items == null || items.isEmpty()) {
            summary.append("- Нет явных данных.\n");
            return;
        }
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            summary.append("- ").append(item).append('\n');
        }
        if (summary.charAt(summary.length() - 1) != '\n') {
            summary.append('\n');
        }
    }

    private String renderPinnedFacts(List<String> facts) {
        return renderPinnedFactsLimited(facts, facts.size());
    }

    private String renderPinnedFactsLimited(List<String> facts, int limit) {
        if (facts == null || facts.isEmpty() || limit <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder("Закрепленные факты и заметки:\n");
        int from = Math.max(0, facts.size() - limit);
        for (int i = from; i < facts.size(); i++) {
            result.append("- ").append(facts.get(i)).append('\n');
        }
        return result.toString().trim();
    }

    private int estimateTokens(List<AiRequestOptions.ChatHistoryEntry> entries) {
        int total = 0;
        for (AiRequestOptions.ChatHistoryEntry entry : entries) {
            if (entry == null || entry.content() == null) {
                continue;
            }
            int chars = entry.content().length();
            total += 4 + (chars / 4);
        }
        return total;
    }

    private ConversationContext getState(String conversationId) {
        return contexts.computeIfAbsent(normalizeConversationId(conversationId), id -> new ConversationContext());
    }

    private String normalizeConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UNKNOWN_CONVERSATION_ID;
        }
        return conversationId.trim();
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (normalized.length() > MAX_HISTORY_ITEM_CHARS) {
            return normalized.substring(0, MAX_HISTORY_ITEM_CHARS) + "...";
        }
        return normalized;
    }

    private String normalizeSummarySnippet(String message) {
        if (message == null) {
            return "";
        }
        String normalized = message
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\n', ' ')
            .replaceAll("\\s{2,}", " ")
            .trim();
        if (normalized.length() > MAX_HISTORY_ITEM_CHARS) {
            return normalized.substring(0, MAX_HISTORY_ITEM_CHARS) + "...";
        }
        return normalized;
    }

    private String normalizePinnedFact(String item) {
        if (item == null) {
            return "";
        }
        String normalized = item.replace('\r', ' ').replace('\n', ' ').trim();
        if (normalized.length() > MAX_PINNED_FACT_LENGTH) {
            normalized = normalized.substring(0, MAX_PINNED_FACT_LENGTH) + "...";
        }
        return normalized;
    }

    private int recentWindowMessages() {
        return readIntProperty("ai.chat.context.recentWindowMessages", DEFAULT_RECENT_WINDOW_MESSAGES, 4, 200);
    }

    private int summaryTriggerMessages() {
        return readIntProperty("ai.chat.context.summaryTriggerMessages", DEFAULT_SUMMARY_TRIGGER_MESSAGES, 10, 500);
    }

    private int maxBudgetTokens() {
        return readIntProperty("ai.chat.context.maxBudgetTokens", DEFAULT_MAX_BUDGET_TOKENS, 1500, 50000);
    }

    private int minimalBudgetTokens() {
        return readIntProperty("ai.chat.context.minimalBudgetTokens", DEFAULT_MINIMAL_BUDGET_TOKENS, 500, 10000);
    }

    private int maxPinnedFacts() {
        return readIntProperty("ai.chat.context.maxPinnedFacts", DEFAULT_MAX_PINNED_FACTS, 1, 128);
    }

    private int readIntProperty(String key, int fallback, int min, int max) {
        String raw = ConfigManager.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed < min) {
                return min;
            }
            if (parsed > max) {
                return max;
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static final class ConversationContext {
        private final List<AiRequestOptions.ChatHistoryEntry> history = new ArrayList<>();
        private final List<String> pinnedFacts = new ArrayList<>();
        private ChatContextMode preferredMode = ChatContextMode.AUTO;
        private String summary = "";
        private int summaryCoveredMessages = 0;
        private int activeSummaryRevision = 0;
        private String lastSummarizedAt = "";
    }

    public record ContextPersistentState(
        ChatContextMode preferredMode,
        String summary,
        int summaryCoveredMessages,
        List<String> pinnedFacts,
        int activeSummaryRevision,
        String lastSummarizedAt
    ) {
    }

    public record ContextSummarizationInput(
        List<AiRequestOptions.ChatHistoryEntry> entriesToCover,
        List<String> pinnedFacts,
        int coveredMessages,
        int totalHistoryMessages,
        String existingSummary,
        String fallbackSummary
    ) {
    }
}
