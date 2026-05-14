package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.ui.interaction.UiActionRegistry;
import com.example.neuroflowplanner.ui.interaction.UndoRedoManager;
import com.example.neuroflowplanner.ui.layout.leftpanel.CommandPaletteDisplayPolicy;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class CommandPaletteController {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(CommandPaletteController.class);
    private static final int DEFAULT_LIMIT = 15;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_SUGGESTION_LIMIT = 4;
    private static final List<String> DEFAULT_EXAMPLE_QUERIES = List.of(
        "добавить задачу",
        "настройки",
        "ии анализ",
        "архив"
    );

    private static final Comparator<CommandPaletteItem> ORDER = Comparator
        .comparingDouble(CommandPaletteItem::score)
        .reversed()
        .thenComparingInt(item -> typePriority(item.type()))
        .thenComparing(CommandPaletteItem::title, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(CommandPaletteItem::key, String.CASE_INSENSITIVE_ORDER);

    private final String contextName;
    private final UiActionRegistry actionRegistry;
    private final BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider;
    private final Function<GlobalSearchResult, Boolean> globalSearchNavigator;
    private final Function<String, Boolean> sidebarRevealHandler;
    private final Function<String, String> actionShortcutHintResolver;
    private final CommandPaletteHistory history;
    private Function<String, String> sidebarRevealTargetHintResolver;

    public CommandPaletteController(
        String contextName,
        UiActionRegistry actionRegistry,
        BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider,
        Function<GlobalSearchResult, Boolean> globalSearchNavigator
    ) {
        this(
            contextName,
            actionRegistry,
            globalSearchProvider,
            globalSearchNavigator,
            null,
            null,
            new CommandPaletteHistory()
        );
    }

    public CommandPaletteController(
        String contextName,
        UiActionRegistry actionRegistry,
        BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider,
        Function<GlobalSearchResult, Boolean> globalSearchNavigator,
        Function<String, Boolean> sidebarRevealHandler
    ) {
        this(
            contextName,
            actionRegistry,
            globalSearchProvider,
            globalSearchNavigator,
            sidebarRevealHandler,
            null,
            new CommandPaletteHistory()
        );
    }

    public CommandPaletteController(
        String contextName,
        UiActionRegistry actionRegistry,
        BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider,
        Function<GlobalSearchResult, Boolean> globalSearchNavigator,
        CommandPaletteHistory history
    ) {
        this(contextName, actionRegistry, globalSearchProvider, globalSearchNavigator, null, null, history);
    }

    public CommandPaletteController(
        String contextName,
        UiActionRegistry actionRegistry,
        BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider,
        Function<GlobalSearchResult, Boolean> globalSearchNavigator,
        Function<String, Boolean> sidebarRevealHandler,
        CommandPaletteHistory history
    ) {
        this(
            contextName,
            actionRegistry,
            globalSearchProvider,
            globalSearchNavigator,
            sidebarRevealHandler,
            null,
            history
        );
    }

    public CommandPaletteController(
        String contextName,
        UiActionRegistry actionRegistry,
        BiFunction<String, Integer, List<GlobalSearchResult>> globalSearchProvider,
        Function<GlobalSearchResult, Boolean> globalSearchNavigator,
        Function<String, Boolean> sidebarRevealHandler,
        Function<String, String> actionShortcutHintResolver,
        CommandPaletteHistory history
    ) {
        this.contextName = normalize(contextName).isBlank() ? "unknown" : normalize(contextName);
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry");
        this.globalSearchProvider = globalSearchProvider;
        this.globalSearchNavigator = globalSearchNavigator;
        this.sidebarRevealHandler = sidebarRevealHandler;
        this.actionShortcutHintResolver = actionShortcutHintResolver;
        this.history = history == null ? new CommandPaletteHistory() : history;
    }

    public List<CommandPaletteItem> search(String query, int limit) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = normalizeLimit(limit);
        Map<String, CommandPaletteItem> candidates = new LinkedHashMap<>();

        int actionCandidates = collectActionCandidates(normalizedQuery, candidates);
        int globalCandidates = collectGlobalSearchCandidates(normalizedQuery, safeLimit, candidates);

        List<CommandPaletteItem> sorted = new ArrayList<>(candidates.values());
        sorted.sort(ORDER);

        List<CommandPaletteItem> out = new ArrayList<>(Math.min(sorted.size(), safeLimit));
        for (CommandPaletteItem item : sorted) {
            out.add(item);
            if (out.size() >= safeLimit) {
                break;
            }
        }

        LOG.info(
            "ux.commandPalette.query",
            "context", contextName,
            "queryLength", normalizedQuery.length(),
            "limit", safeLimit,
            "actionCandidates", actionCandidates,
            "globalCandidates", globalCandidates,
            "returned", out.size()
        );
        return List.copyOf(out);
    }

    public CommandPaletteViewModel buildViewModel(String query, CommandPaletteDisplayPolicy policy) {
        int maxResults = policy == null ? DEFAULT_LIMIT : Math.max(5, policy.maxResults());
        String normalizedQuery = normalizeQuery(query);
        List<CommandPaletteItem> ranked = search(normalizedQuery, maxResults);
        List<CommandPaletteResultSection> sections = buildSections(ranked, normalizedQuery, policy);
        List<CommandPaletteItem> flatItems = flattenSections(sections);
        List<String> examples = buildExampleQueries(normalizedQuery, policy);
        boolean hasResults = !flatItems.isEmpty();
        boolean showGuidedEmpty = !hasResults && (policy == null || policy.showGuidedEmptyState());
        String emptyTitle = resolveEmptyTitle(normalizedQuery, showGuidedEmpty);
        String emptyBody = resolveEmptyBody(normalizedQuery, policy, showGuidedEmpty);
        return new CommandPaletteViewModel(
            normalizedQuery,
            sections,
            flatItems,
            examples,
            showGuidedEmpty,
            emptyTitle,
            emptyBody
        );
    }

    public ExecutionResult execute(CommandPaletteItem item, String query, int selectedRank) {
        if (item == null) {
            return ExecutionResult.failure("Команда не выбрана");
        }

        int normalizedRank = Math.max(1, selectedRank);
        String safeQuery = normalize(query);
        LOG.info(
            "ux.commandPalette.execute.started",
            "context", contextName,
            "itemKey", item.key(),
            "itemType", item.type().name(),
            "itemId", item.commandId(),
            "selectedRank", normalizedRank,
            "queryLength", safeQuery.length()
        );

        ExecutionResult result;
        try {
            result = switch (item.type()) {
                case ACTION -> executeAction(item);
                case TASK, NOTE -> executeNavigation(item);
            };
        } catch (RuntimeException ex) {
            LOG.error(
                "ux.commandPalette.execute.failed",
                ex,
                "context", contextName,
                "itemKey", item.key(),
                "itemType", item.type().name(),
                "itemId", item.commandId(),
                "selectedRank", normalizedRank
            );
            return ExecutionResult.failure("Ошибка выполнения команды: " + ex.getClass().getSimpleName());
        }

        if (result.successful()) {
            history.record(item.key());
        }
        LOG.info(
            "ux.commandPalette.execute.completed",
            "context", contextName,
            "itemKey", item.key(),
            "itemType", item.type().name(),
            "itemId", item.commandId(),
            "selectedRank", normalizedRank,
            "successful", result.successful(),
            "message", result.message()
        );
        return result;
    }

    public List<String> recentKeys(int limit) {
        return history.takeRecent(limit);
    }

    public boolean recordExternalActionExecution(String actionId) {
        String normalized = normalize(actionId);
        if (normalized.isBlank()) {
            return false;
        }
        if (!actionRegistry.isRegistered(normalized)) {
            return false;
        }
        history.record(actionKey(normalized));
        return true;
    }

    public void setSidebarRevealTargetHintResolver(Function<String, String> resolver) {
        this.sidebarRevealTargetHintResolver = resolver;
    }

    public String sidebarRevealTargetHint(String actionId) {
        String normalized = normalize(actionId);
        if (normalized.isBlank()) {
            return "rail/context sidebar";
        }
        if (sidebarRevealTargetHintResolver != null) {
            try {
                String resolved = sidebarRevealTargetHintResolver.apply(normalized);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved.trim();
                }
            } catch (RuntimeException ignored) {
                // Fallback to generic two-tier navigation surface.
            }
        }
        return "rail/context sidebar";
    }

    public ExecutionResult revealInSidebar(CommandPaletteItem item) {
        if (item == null || item.type() != CommandPaletteItemType.ACTION) {
            return ExecutionResult.failure("Показ в панели доступен только для команд");
        }
        if (sidebarRevealHandler == null) {
            return ExecutionResult.failure("Bridge показа в панели недоступен");
        }
        String actionId = normalize(item.commandId());
        if (actionId.isBlank()) {
            return ExecutionResult.failure("Не удалось определить actionId");
        }

        boolean revealed;
        try {
            revealed = sidebarRevealHandler.apply(actionId);
        } catch (RuntimeException ex) {
            LOG.error(
                "ux.commandPalette.sidebarReveal.failed",
                ex,
                "context", contextName,
                "actionId", actionId,
                "itemKey", item.key()
            );
            return ExecutionResult.failure("Ошибка показа в панели: " + ex.getClass().getSimpleName());
        }

        if (!revealed) {
            return ExecutionResult.failure("Действие не найдено в панели");
        }
        String revealTarget = sidebarRevealTargetHint(actionId);
        LOG.info(
            "ux.commandPalette.sidebarReveal.completed",
            "context", contextName,
            "actionId", actionId,
            "itemKey", item.key(),
            "target", revealTarget
        );
        return ExecutionResult.success("Показано в " + revealTarget + ": " + item.displayTitle());
    }

    private ExecutionResult executeAction(CommandPaletteItem item) {
        if (!item.available()) {
            String message = item.unavailableReason().isBlank()
                ? "Команда недоступна"
                : item.unavailableReason();
            return ExecutionResult.failure(message);
        }
        UndoRedoManager.CommandResult commandResult = actionRegistry.execute(item.commandId());
        if (commandResult.successful()) {
            return ExecutionResult.success(commandResult.message());
        }
        return ExecutionResult.failure(commandResult.message());
    }

    private ExecutionResult executeNavigation(CommandPaletteItem item) {
        if (globalSearchNavigator == null) {
            return ExecutionResult.failure("Навигация не настроена");
        }
        if (item.globalSearchResult() == null) {
            return ExecutionResult.failure("Не удалось подготовить результат навигации");
        }
        boolean opened = globalSearchNavigator.apply(item.globalSearchResult());
        if (opened) {
            return ExecutionResult.success("Открыто: " + item.displayTitle());
        }
        return ExecutionResult.failure("Не удалось открыть: " + item.displayTitle());
    }

    private int collectActionCandidates(String query, Map<String, CommandPaletteItem> sink) {
        int candidates = 0;
        for (UiActionRegistry.RegisteredAction action : actionRegistry.listActions()) {
            if (action == null) {
                continue;
            }

            ActionMatch match = scoreActionMatch(action, query);
            if (!query.isBlank() && match.score() <= 0) {
                continue;
            }

            boolean available = safeAvailability(action);
            String unavailableReason = available ? "" : safeUnavailableReason(action);
            String subtitle = friendlyCategoryLabel(action.category()) + " • " + buildActionNoviceDescription(action);
            if (!available && !unavailableReason.isBlank()) {
                subtitle = subtitle + " • " + unavailableReason;
            }

            double score = match.score();
            if (query.isBlank()) {
                score = 70;
            }
            if (!available) {
                score -= 20;
            }

            CommandPaletteItem item = new CommandPaletteItem(
                actionKey(action.actionId()),
                CommandPaletteItemType.ACTION,
                action.actionId(),
                action.label(),
                subtitle,
                resolveActionShortcutHint(action),
                available,
                unavailableReason,
                score,
                false,
                null
            );
            item = applyUsageBoost(item);
            mergeBest(sink, item);
            candidates++;
        }
        return candidates;
    }

    private int collectGlobalSearchCandidates(String query, int limit, Map<String, CommandPaletteItem> sink) {
        if (query.isBlank() || globalSearchProvider == null) {
            return 0;
        }
        List<GlobalSearchResult> globalResults;
        try {
            globalResults = globalSearchProvider.apply(query, Math.min(limit * 2, MAX_LIMIT));
        } catch (RuntimeException ex) {
            LOG.warning(
                "ux.commandPalette.globalSearch.failed",
                "context", contextName,
                "queryLength", query.length(),
                "error", ex.getClass().getSimpleName()
            );
            return 0;
        }
        if (globalResults == null || globalResults.isEmpty()) {
            return 0;
        }

        int candidates = 0;
        for (GlobalSearchResult result : globalResults) {
            if (result == null || result.navigationTarget() == null || result.navigationTarget().isEmpty()) {
                continue;
            }

            CommandPaletteItemType type = result.type() == GlobalSearchResultType.TASK
                ? CommandPaletteItemType.TASK
                : CommandPaletteItemType.NOTE;
            String commandId = normalize(result.navigationTarget().targetId());
            if (commandId.isBlank()) {
                continue;
            }

            double base = type == CommandPaletteItemType.TASK ? 140 : 130;
            double score = base + Math.min(result.score(), 240);
            CommandPaletteItem item = new CommandPaletteItem(
                resultKey(type, commandId),
                type,
                commandId,
                result.title(),
                result.snippet(),
                "",
                true,
                "",
                score,
                false,
                result
            );
            item = applyUsageBoost(item);
            mergeBest(sink, item);
            candidates++;
        }
        return candidates;
    }

    private CommandPaletteItem applyUsageBoost(CommandPaletteItem item) {
        int rank = history.rankOf(item.key());
        int frequency = history.countOf(item.key());
        double score = item.score();
        if (rank < 0) {
            if (frequency > 0) {
                score += Math.min(18, frequency * 2.5d);
            }
            return item.withScore(score).withRecent(false);
        }
        double recentBoost = Math.max(5, 28 - (rank * 2));
        double frequentBoost = Math.min(22, Math.max(0, frequency - 1) * 2.75d);
        return item.withScore(score + recentBoost + frequentBoost).withRecent(true);
    }

    private String resolveActionShortcutHint(UiActionRegistry.RegisteredAction action) {
        if (action == null) {
            return "";
        }
        if (actionShortcutHintResolver != null) {
            try {
                String resolved = actionShortcutHintResolver.apply(action.actionId());
                if (resolved != null && !resolved.isBlank()) {
                    return resolved.trim();
                }
            } catch (RuntimeException ignored) {
                // Fallback to registry-provided default shortcut.
            }
        }
        return action.defaultShortcut();
    }

    private ActionMatch scoreActionMatch(UiActionRegistry.RegisteredAction action, String query) {
        if (query.isBlank()) {
            return new ActionMatch(70, Integer.MAX_VALUE, MatchKind.BLANK);
        }

        String id = normalizeQuery(action.actionId());
        String label = normalizeQuery(action.label());
        String category = normalizeQuery(action.category());
        List<String> aliases = buildActionAliases(action);

        double score = 0;
        int firstIndex = Integer.MAX_VALUE;
        MatchKind kind = MatchKind.NONE;
        if (label.equals(query) || id.equals(query)) {
            score = 220;
            firstIndex = 0;
            kind = MatchKind.EXACT;
        } else if (label.startsWith(query) || id.startsWith(query)) {
            score = 180;
            firstIndex = 0;
            kind = MatchKind.PREFIX;
        } else {
            for (String alias : aliases) {
                if (alias.equals(query)) {
                    score = Math.max(score, 170);
                    firstIndex = 0;
                    kind = kind.rank() < MatchKind.ALIAS_EXACT.rank() ? MatchKind.ALIAS_EXACT : kind;
                } else if (alias.startsWith(query)) {
                    score = Math.max(score, 160);
                    firstIndex = 0;
                    kind = kind.rank() < MatchKind.ALIAS_PREFIX.rank() ? MatchKind.ALIAS_PREFIX : kind;
                } else {
                    int aliasIndex = alias.indexOf(query);
                    if (aliasIndex >= 0) {
                        score = Math.max(score, 132);
                        firstIndex = Math.min(firstIndex, aliasIndex);
                        kind = kind.rank() < MatchKind.ALIAS_CONTAINS.rank() ? MatchKind.ALIAS_CONTAINS : kind;
                    }
                }
            }
            int labelIndex = label.indexOf(query);
            int idIndex = id.indexOf(query);
            int categoryIndex = category.indexOf(query);
            if (labelIndex >= 0) {
                score = Math.max(score, 150);
                firstIndex = Math.min(firstIndex, labelIndex);
                if (kind.rank() < MatchKind.CONTAINS.rank()) {
                    kind = MatchKind.CONTAINS;
                }
            }
            if (idIndex >= 0) {
                score = Math.max(score, 140);
                firstIndex = Math.min(firstIndex, idIndex);
                if (kind.rank() < MatchKind.CONTAINS.rank()) {
                    kind = MatchKind.CONTAINS;
                }
            }
            if (categoryIndex >= 0) {
                score = Math.max(score, 120);
                firstIndex = Math.min(firstIndex, categoryIndex);
                if (kind.rank() < MatchKind.CATEGORY.rank()) {
                    kind = MatchKind.CATEGORY;
                }
            }

            if (score <= 0 && fuzzyMatch(label, query)) {
                score = 108;
                firstIndex = Math.min(firstIndex, fuzzyFirstIndex(label, query));
                kind = MatchKind.FUZZY;
            } else if (score <= 0 && fuzzyMatch(id, query)) {
                score = 102;
                firstIndex = Math.min(firstIndex, fuzzyFirstIndex(id, query));
                kind = MatchKind.FUZZY;
            }
        }

        if (score > 0 && firstIndex != Integer.MAX_VALUE) {
            score += Math.max(0, 30 - Math.min(firstIndex, 30)) * 0.2d;
        }
        if (score > 0) {
            score += switch (kind) {
                case EXACT -> 18;
                case PREFIX -> 10;
                case ALIAS_EXACT -> 9;
                case ALIAS_PREFIX -> 6;
                case ALIAS_CONTAINS -> 3;
                case CONTAINS, CATEGORY, FUZZY, BLANK, NONE -> 0;
            };
        }
        return new ActionMatch(score, firstIndex, kind);
    }

    private boolean safeAvailability(UiActionRegistry.RegisteredAction action) {
        try {
            return action.availability().getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String safeUnavailableReason(UiActionRegistry.RegisteredAction action) {
        try {
            String reason = action.unavailableReason().get();
            return reason == null ? "" : reason.trim();
        } catch (RuntimeException ex) {
            return "Команда недоступна";
        }
    }

    private void mergeBest(Map<String, CommandPaletteItem> sink, CommandPaletteItem candidate) {
        CommandPaletteItem existing = sink.get(candidate.key());
        if (existing == null || candidate.score() > existing.score()) {
            sink.put(candidate.key(), candidate);
        }
    }

    private List<CommandPaletteResultSection> buildSections(
        List<CommandPaletteItem> ranked,
        String normalizedQuery,
        CommandPaletteDisplayPolicy policy
    ) {
        List<CommandPaletteItem> rankedItems = ranked == null ? List.of() : ranked;
        CommandPaletteDisplayPolicy safePolicy = policy;
        int maxPerSection = safePolicy == null ? DEFAULT_LIMIT : Math.max(5, safePolicy.maxResults());
        boolean showRecent = safePolicy == null || safePolicy.showRecentSection();
        boolean showSuggested = safePolicy == null || safePolicy.showFrequentSection() || safePolicy.showContextHints();
        LinkedHashSet<String> used = new LinkedHashSet<>();
        List<CommandPaletteResultSection> sections = new ArrayList<>();

        if (showRecent) {
            List<CommandPaletteItem> recent = takeSectionItems(
                rankedItems,
                item -> item.recent(),
                used,
                normalizedQuery.isBlank() ? 4 : 2
            );
            if (!recent.isEmpty()) {
                sections.add(new CommandPaletteResultSection(CommandPaletteResultGroup.RECENT, "Recent", recent));
            }
        }

        if (showSuggested) {
            List<CommandPaletteItem> suggested = buildSuggestedItems(rankedItems, normalizedQuery, safePolicy, used);
            if (!suggested.isEmpty()) {
                sections.add(new CommandPaletteResultSection(CommandPaletteResultGroup.SUGGESTED, "Suggested", suggested));
            }
        }

        List<CommandPaletteItem> actions = takeSectionItems(
            rankedItems,
            item -> item.type() == CommandPaletteItemType.ACTION,
            used,
            Math.max(3, Math.min(maxPerSection, normalizedQuery.isBlank() ? 8 : maxPerSection))
        );
        if (!actions.isEmpty()) {
            sections.add(new CommandPaletteResultSection(CommandPaletteResultGroup.ACTIONS, "Actions", actions));
        }

        List<CommandPaletteItem> entities = takeSectionItems(
            rankedItems,
            item -> item.type() == CommandPaletteItemType.TASK || item.type() == CommandPaletteItemType.NOTE,
            used,
            Math.max(2, Math.min(maxPerSection, normalizedQuery.isBlank() ? 6 : maxPerSection))
        );
        if (!entities.isEmpty()) {
            sections.add(new CommandPaletteResultSection(CommandPaletteResultGroup.ENTITIES, "Entities", entities));
        }

        if (sections.isEmpty() && !rankedItems.isEmpty()) {
            sections.add(new CommandPaletteResultSection(CommandPaletteResultGroup.ACTIONS, "Results", rankedItems));
        }
        return List.copyOf(sections);
    }

    private List<CommandPaletteItem> buildSuggestedItems(
        List<CommandPaletteItem> rankedItems,
        String query,
        CommandPaletteDisplayPolicy policy,
        Set<String> used
    ) {
        List<CommandPaletteItem> out = new ArrayList<>();
        int limit = DEFAULT_SUGGESTION_LIMIT;
        if (policy != null && policy.heightBand().isVeryLowHeight()) {
            limit = 2;
        } else if (policy != null && policy.heightBand().isLowHeight()) {
            limit = 3;
        }

        // Frequent actions first (even if not in current top results).
        for (String key : history.topFrequent(limit * 2)) {
            if (used.contains(key)) {
                continue;
            }
            CommandPaletteItem matched = findByKey(rankedItems, key);
            if (matched != null && matched.type() == CommandPaletteItemType.ACTION) {
                out.add(matched);
                used.add(matched.key());
            }
            if (out.size() >= limit) {
                return List.copyOf(out);
            }
        }

        List<String> contextTokens = inferContextTokens(query);
        for (CommandPaletteItem item : rankedItems) {
            if (out.size() >= limit) {
                break;
            }
            if (used.contains(item.key()) || item.type() != CommandPaletteItemType.ACTION) {
                continue;
            }
            if (!contextTokens.isEmpty() && !matchesAnyToken(item, contextTokens)) {
                continue;
            }
            if (query.isBlank() && !item.available()) {
                continue;
            }
            out.add(item);
            used.add(item.key());
        }
        return List.copyOf(out);
    }

    private List<CommandPaletteItem> takeSectionItems(
        List<CommandPaletteItem> rankedItems,
        java.util.function.Predicate<CommandPaletteItem> predicate,
        Set<String> used,
        int limit
    ) {
        int safeLimit = Math.max(1, limit);
        List<CommandPaletteItem> out = new ArrayList<>(safeLimit);
        for (CommandPaletteItem item : rankedItems) {
            if (item == null || used.contains(item.key())) {
                continue;
            }
            if (!predicate.test(item)) {
                continue;
            }
            out.add(item);
            used.add(item.key());
            if (out.size() >= safeLimit) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private List<CommandPaletteItem> flattenSections(List<CommandPaletteResultSection> sections) {
        List<CommandPaletteItem> out = new ArrayList<>();
        if (sections == null) {
            return List.of();
        }
        for (CommandPaletteResultSection section : sections) {
            if (section == null || section.items() == null || section.items().isEmpty()) {
                continue;
            }
            out.addAll(section.items());
        }
        return List.copyOf(out);
    }

    private List<String> buildExampleQueries(String query, CommandPaletteDisplayPolicy policy) {
        if (query != null && !query.isBlank()) {
            return List.of();
        }
        int count = policy == null ? 3 : Math.max(0, policy.exampleQueryCount());
        if (count <= 0) {
            return List.of();
        }
        List<String> examples = new ArrayList<>(count);
        for (String example : DEFAULT_EXAMPLE_QUERIES) {
            if (examples.size() >= count) {
                break;
            }
            examples.add(example);
        }
        return List.copyOf(examples);
    }

    private String resolveEmptyTitle(String query, boolean showGuidedEmpty) {
        if (query == null || query.isBlank()) {
            return showGuidedEmpty ? "Что можно сделать" : "Начните ввод";
        }
        return "Ничего не найдено";
    }

    private String resolveEmptyBody(String query, CommandPaletteDisplayPolicy policy, boolean showGuidedEmpty) {
        if (query == null || query.isBlank()) {
            if (!showGuidedEmpty) {
                return "Введите название действия, задачи или заметки.";
            }
            String suffix = policy != null && policy.showContextHints()
                ? " Частые и контекстные команды появятся в разделе Suggested."
                : "";
            return "Палитра ищет действия, задачи и заметки. Для редких сценариев используйте поиск по словам (например: архив, настройки, ИИ)." + suffix;
        }
        return "Проверьте формулировку, попробуйте синоним или более короткий запрос.";
    }

    private List<String> inferContextTokens(String query) {
        String q = normalizeQuery(query);
        if (q.isBlank()) {
            return switch (contextName) {
                case "mainview" -> List.of("задач", "панель", "добав", "ai", "ии", "настро");
                default -> List.of();
            };
        }
        List<String> tokens = new ArrayList<>();
        if (q.contains("ai") || q.contains("ии")) {
            tokens.add("ai");
            tokens.add("ии");
        }
        if (q.contains("зада") || q.contains("task")) {
            tokens.add("зада");
            tokens.add("task");
        }
        if (q.contains("настр") || q.contains("settings")) {
            tokens.add("настр");
            tokens.add("setting");
            tokens.add("system");
        }
        if (q.contains("архив") || q.contains("archive")) {
            tokens.add("архив");
            tokens.add("archive");
            tokens.add("bulk");
        }
        return List.copyOf(tokens);
    }

    private boolean matchesAnyToken(CommandPaletteItem item, List<String> tokens) {
        if (item == null || tokens == null || tokens.isEmpty()) {
            return false;
        }
        String haystack = normalizeQuery(item.displayTitle() + " " + item.subtitle() + " " + item.commandId());
        for (String token : tokens) {
            if (token != null && !token.isBlank() && haystack.contains(normalizeQuery(token))) {
                return true;
            }
        }
        return false;
    }

    private CommandPaletteItem findByKey(List<CommandPaletteItem> items, String key) {
        if (key == null || key.isBlank() || items == null || items.isEmpty()) {
            return null;
        }
        for (CommandPaletteItem item : items) {
            if (item != null && key.equals(item.key())) {
                return item;
            }
        }
        return null;
    }

    private List<String> buildActionAliases(UiActionRegistry.RegisteredAction action) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAliasTokens(aliases, action.actionId());
        addAliasTokens(aliases, action.label());
        addAliasTokens(aliases, action.category());
        String actionId = normalizeQuery(action.actionId());
        if (actionId.contains("commandpalette")) {
            aliases.add("палитра");
            aliases.add("командная палитра");
            aliases.add("palette");
        }
        if (actionId.contains("settings")) {
            aliases.add("параметры");
            aliases.add("настройки");
        }
        if (actionId.contains(".ai.") || actionId.contains("ai.")) {
            aliases.add("ai");
            aliases.add("ии");
        }
        if (actionId.contains("help") || actionId.contains("shortcuts")) {
            aliases.add("справка");
            aliases.add("горячие клавиши");
        }
        return List.copyOf(aliases);
    }

    private void addAliasTokens(Set<String> aliases, String raw) {
        String normalized = normalizeQuery(raw)
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ');
        if (normalized.isBlank()) {
            return;
        }
        aliases.add(normalized);
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank() || token.length() < 2) {
                continue;
            }
            aliases.add(token);
        }
    }

    private boolean fuzzyMatch(String haystack, String query) {
        if (haystack == null || query == null || haystack.isBlank() || query.isBlank()) {
            return false;
        }
        int q = 0;
        String h = normalizeQuery(haystack);
        String n = normalizeQuery(query);
        for (int i = 0; i < h.length() && q < n.length(); i++) {
            if (h.charAt(i) == n.charAt(q)) {
                q++;
            }
        }
        return q == n.length();
    }

    private int fuzzyFirstIndex(String haystack, String query) {
        if (haystack == null || query == null) {
            return Integer.MAX_VALUE;
        }
        String h = normalizeQuery(haystack);
        String q = normalizeQuery(query);
        if (q.isBlank()) {
            return 0;
        }
        int first = Integer.MAX_VALUE;
        int qi = 0;
        for (int i = 0; i < h.length() && qi < q.length(); i++) {
            if (h.charAt(i) == q.charAt(qi)) {
                if (first == Integer.MAX_VALUE) {
                    first = i;
                }
                qi++;
            }
        }
        return qi == q.length() ? first : Integer.MAX_VALUE;
    }

    private String friendlyCategoryLabel(String rawCategory) {
        String category = normalizeQuery(rawCategory);
        return switch (category) {
            case "system" -> "Система";
            case "tasks", "task", "main" -> "Задачи";
            case "tools" -> "Инструменты";
            case "ai" -> "AI";
            case "analysis" -> "Аналитика";
            case "bulk" -> "Массовые действия";
            case "history" -> "История";
            default -> normalize(rawCategory).isBlank() ? "Команда" : normalize(rawCategory);
        };
    }

    private String buildActionNoviceDescription(UiActionRegistry.RegisteredAction action) {
        String actionId = normalizeQuery(action.actionId());
        String label = normalize(action.label());
        if (actionId.contains("commandpalette")) {
            return "Быстрый поиск команд, задач и заметок";
        }
        if (actionId.contains("globalsearch")) {
            return "Фокус на строке поиска";
        }
        if (actionId.contains(".settings")) {
            return "Открыть параметры приложения";
        }
        if (actionId.contains(".help") || actionId.contains("shortcuts")) {
            return "Подсказка по возможностям и горячим клавишам";
        }
        if (actionId.contains(".ai.")) {
            return "Интеллектуальный сценарий для задач";
        }
        if (actionId.contains(".bulk.")) {
            return "Операция сразу для нескольких задач";
        }
        if (actionId.contains(".archive")) {
            return "Работа с архивом задач";
        }
        if (label.toLowerCase(Locale.ROOT).startsWith("открыть ")) {
            return "Переход к экрану или инструменту";
        }
        if (label.toLowerCase(Locale.ROOT).startsWith("добав")) {
            return "Создание нового элемента";
        }
        return "Выполнить действие";
    }

    private static int typePriority(CommandPaletteItemType type) {
        return switch (type) {
            case ACTION -> 0;
            case TASK -> 1;
            case NOTE -> 2;
        };
    }

    private static String actionKey(String actionId) {
        return "action:" + normalize(actionId);
    }

    private static String resultKey(CommandPaletteItemType type, String id) {
        String prefix = type == CommandPaletteItemType.TASK ? "task:" : "note:";
        return prefix + normalize(id);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static String normalizeQuery(String query) {
        return normalize(query).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    public record ExecutionResult(boolean successful, String message) {
        public static ExecutionResult success(String message) {
            return new ExecutionResult(true, normalize(message));
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(false, normalize(message));
        }
    }

    private record ActionMatch(double score, int firstIndex, MatchKind kind) {
    }

    private enum MatchKind {
        NONE(0),
        BLANK(1),
        CATEGORY(2),
        FUZZY(3),
        CONTAINS(4),
        ALIAS_CONTAINS(5),
        ALIAS_PREFIX(6),
        ALIAS_EXACT(7),
        PREFIX(8),
        EXACT(9);

        private final int rank;

        MatchKind(int rank) {
            this.rank = rank;
        }

        int rank() {
            return rank;
        }
    }
}
