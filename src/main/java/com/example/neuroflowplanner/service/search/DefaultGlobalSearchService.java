package com.example.neuroflowplanner.service.search;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import com.example.neuroflowplanner.service.notes.DefaultSmartNotesApplicationService;
import com.example.neuroflowplanner.service.notes.SmartNotesApplicationService;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.util.StructuredLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DefaultGlobalSearchService implements GlobalSearchService {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(DefaultGlobalSearchService.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 200;
    private static final int SNIPPET_CONTEXT = 42;
    private static final int SNIPPET_MAX = 120;
    private static final int NO_MATCH_INDEX = Integer.MAX_VALUE;

    private static final Comparator<ScoredResult> SCORE_ORDER = Comparator
        .comparingDouble((ScoredResult value) -> value.result().score())
        .reversed()
        .thenComparingInt(ScoredResult::typePriority)
        .thenComparingInt(ScoredResult::firstMatchIndex)
        .thenComparing(ScoredResult::normalizedTitle)
        .thenComparing(ScoredResult::normalizedId);

    private final Supplier<List<Task>> taskLoader;
    private final Function<String, List<String>> noteTitleSearcher;
    private final Function<String, String> noteContentLoader;

    public DefaultGlobalSearchService() {
        this(new DefaultTaskApplicationService(), new DefaultSmartNotesApplicationService());
    }

    public DefaultGlobalSearchService(
        TaskApplicationService taskApplicationService,
        SmartNotesApplicationService notesApplicationService
    ) {
        this(
            () -> taskApplicationService == null ? List.of() : safeTaskList(taskApplicationService.loadTasks()),
            query -> notesApplicationService == null ? List.of() : safeStringList(notesApplicationService.searchTitles(query)),
            noteTitle -> notesApplicationService == null ? "" : normalizeText(notesApplicationService.loadContent(noteTitle))
        );
    }

    DefaultGlobalSearchService(
        Supplier<List<Task>> taskLoader,
        Function<String, List<String>> noteTitleSearcher,
        Function<String, String> noteContentLoader
    ) {
        this.taskLoader = Objects.requireNonNull(taskLoader, "taskLoader");
        this.noteTitleSearcher = Objects.requireNonNull(noteTitleSearcher, "noteTitleSearcher");
        this.noteContentLoader = Objects.requireNonNull(noteContentLoader, "noteContentLoader");
    }

    @Override
    public List<GlobalSearchResult> search(String query, int limit) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        int safeLimit = normalizeLimit(limit);
        List<String> queryTokens = tokenize(normalizedQuery);
        List<ScoredResult> scored = new ArrayList<>();

        collectTaskResults(normalizedQuery, queryTokens, scored);
        collectNoteResults(normalizedQuery, queryTokens, scored);
        scored.sort(SCORE_ORDER);

        List<GlobalSearchResult> results = new ArrayList<>(Math.min(scored.size(), safeLimit));
        for (int i = 0; i < scored.size() && results.size() < safeLimit; i++) {
            results.add(scored.get(i).result());
        }

        LOG.info(
            "ux.search.query",
            "component", "GlobalSearchService",
            "queryLength", normalizedQuery.length(),
            "limit", safeLimit,
            "candidates", scored.size(),
            "returned", results.size()
        );
        return List.copyOf(results);
    }

    private void collectTaskResults(String query, List<String> tokens, List<ScoredResult> sink) {
        List<Task> roots = safeTaskList(taskLoader.get());
        if (roots.isEmpty()) {
            return;
        }

        List<Task> allTasks = flattenTasks(roots);
        Set<String> seenTaskIds = new LinkedHashSet<>();
        for (Task task : allTasks) {
            if (task == null) {
                continue;
            }

            String taskId = normalizeText(task.getId());
            if (taskId.isBlank() || !seenTaskIds.add(taskId)) {
                continue;
            }

            String title = normalizeText(task.getTitle());
            String description = normalizeText(task.getDescription());
            String tags = normalizeText(task.getTags());

            MatchScore titleScore = matchField(title, query, tokens, 120, 90, 70, 36);
            MatchScore descriptionScore = matchField(description, query, tokens, 72, 50, 35, 22);
            MatchScore tagsScore = matchField(tags, query, tokens, 68, 48, 32, 20);

            double totalScore = titleScore.score() + descriptionScore.score() + tagsScore.score();
            if (totalScore <= 0) {
                continue;
            }

            int firstIndex = minIndex(titleScore.firstIndex(), descriptionScore.firstIndex(), tagsScore.firstIndex());
            totalScore += firstMatchBoost(firstIndex);
            String visibleTitle = title.isBlank() ? taskId : title;
            String snippet = buildTaskSnippet(task, query, descriptionScore.score() > 0, tagsScore.score() > 0);

            GlobalSearchResult result = GlobalSearchResult.task(taskId, visibleTitle, snippet, totalScore);
            sink.add(new ScoredResult(
                result,
                typePriority(result.type()),
                normalizeSortIndex(firstIndex),
                normalizeSortText(visibleTitle),
                normalizeSortText(taskId)
            ));
        }
    }

    private void collectNoteResults(String query, List<String> tokens, List<ScoredResult> sink) {
        List<String> candidateTitles = safeStringList(noteTitleSearcher.apply(query));
        if (candidateTitles.isEmpty()) {
            return;
        }

        Set<String> dedupeTitles = new LinkedHashSet<>();
        for (String rawTitle : candidateTitles) {
            String title = normalizeText(rawTitle);
            if (title.isBlank() || !dedupeTitles.add(title)) {
                continue;
            }

            String content = normalizeText(noteContentLoader.apply(title));
            MatchScore titleScore = matchField(title, query, tokens, 112, 86, 66, 34);
            MatchScore contentScore = matchField(content, query, tokens, 76, 56, 38, 24);
            double totalScore = titleScore.score() + contentScore.score();
            if (totalScore <= 0) {
                continue;
            }

            int firstIndex = minIndex(titleScore.firstIndex(), contentScore.firstIndex());
            totalScore += firstMatchBoost(firstIndex);
            String snippet = contentScore.score() > 0
                ? buildSnippet(content, query, tokens)
                : buildSnippet(title, query, tokens);

            GlobalSearchResult result = GlobalSearchResult.note(title, snippet, totalScore);
            sink.add(new ScoredResult(
                result,
                typePriority(result.type()),
                normalizeSortIndex(firstIndex),
                normalizeSortText(title),
                normalizeSortText(title)
            ));
        }
    }

    private MatchScore matchField(
        String rawFieldValue,
        String query,
        List<String> tokens,
        double exactScore,
        double prefixScore,
        double containsScore,
        double tokenScore
    ) {
        String normalizedField = normalizeSortText(rawFieldValue);
        if (normalizedField.isBlank()) {
            return MatchScore.none();
        }

        double total = 0;
        int firstIndex = NO_MATCH_INDEX;
        if (normalizedField.equals(query)) {
            total += exactScore;
            firstIndex = 0;
        } else if (normalizedField.startsWith(query)) {
            total += prefixScore;
            firstIndex = 0;
        } else {
            int directIndex = normalizedField.indexOf(query);
            if (directIndex >= 0) {
                total += containsScore;
                firstIndex = directIndex;
            }
        }

        if (!tokens.isEmpty()) {
            int tokenHits = 0;
            int firstTokenIndex = NO_MATCH_INDEX;
            for (String token : tokens) {
                int tokenIndex = normalizedField.indexOf(token);
                if (tokenIndex >= 0) {
                    tokenHits++;
                    firstTokenIndex = Math.min(firstTokenIndex, tokenIndex);
                }
            }
            if (tokenHits > 0) {
                double coverage = (double) tokenHits / (double) tokens.size();
                total += tokenScore * coverage;
                firstIndex = Math.min(firstIndex, firstTokenIndex);
            }
        }

        if (total <= 0) {
            return MatchScore.none();
        }
        return new MatchScore(total, firstIndex);
    }

    private String buildTaskSnippet(Task task, String query, boolean descriptionMatched, boolean tagsMatched) {
        String description = normalizeText(task.getDescription());
        if (descriptionMatched && !description.isBlank()) {
            return buildSnippet(description, query, tokenize(query));
        }

        String tags = normalizeText(task.getTags());
        if (tagsMatched && !tags.isBlank()) {
            return "Теги: " + buildSnippet(tags, query, tokenize(query));
        }

        String title = normalizeText(task.getTitle());
        return buildSnippet(title, query, tokenize(query));
    }

    private String buildSnippet(String rawText, String query, List<String> tokens) {
        String normalized = normalizeText(rawText);
        if (normalized.isBlank()) {
            return "";
        }

        String lowered = normalizeSortText(normalized);
        int index = lowered.indexOf(query);
        if (index < 0) {
            for (String token : tokens) {
                int tokenIndex = lowered.indexOf(token);
                if (tokenIndex >= 0) {
                    index = tokenIndex;
                    break;
                }
            }
        }
        if (index < 0) {
            return truncate(normalized, SNIPPET_MAX);
        }

        int endIndex = Math.min(normalized.length(), index + query.length());
        int from = Math.max(0, index - SNIPPET_CONTEXT);
        int to = Math.min(normalized.length(), endIndex + SNIPPET_CONTEXT);
        String fragment = normalized.substring(from, to).trim();
        if (from > 0) {
            fragment = "..." + fragment;
        }
        if (to < normalized.length()) {
            fragment = fragment + "...";
        }
        return fragment;
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private List<Task> flattenTasks(List<Task> roots) {
        List<Task> result = new ArrayList<>();
        for (Task root : roots) {
            collectRecursively(root, result);
        }
        return result;
    }

    private void collectRecursively(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            collectRecursively(subtask, sink);
        }
    }

    private List<String> tokenize(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return List.of();
        }
        String[] pieces = normalizedQuery.split("\\s+");
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String piece : pieces) {
            String token = normalizeSortText(piece);
            if (!token.isBlank()) {
                deduped.add(token);
            }
        }
        return List.copyOf(deduped);
    }

    private String normalizeQuery(String query) {
        return normalizeSortText(query);
    }

    private static String normalizeText(String value) {
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

    private static String normalizeSortText(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int minIndex(int... values) {
        int min = NO_MATCH_INDEX;
        if (values == null || values.length == 0) {
            return min;
        }
        for (int value : values) {
            if (value >= 0 && value < min) {
                min = value;
            }
        }
        return min;
    }

    private int normalizeSortIndex(int value) {
        if (value < 0 || value == NO_MATCH_INDEX) {
            return Integer.MAX_VALUE / 2;
        }
        return value;
    }

    private double firstMatchBoost(int firstIndex) {
        if (firstIndex < 0 || firstIndex == NO_MATCH_INDEX) {
            return 0;
        }
        int capped = Math.min(firstIndex, 40);
        return (40 - capped) * 0.25d;
    }

    private int typePriority(GlobalSearchResultType type) {
        return type == GlobalSearchResultType.TASK ? 0 : 1;
    }

    private static List<Task> safeTaskList(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        return List.copyOf(tasks);
    }

    private static List<String> safeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private record MatchScore(double score, int firstIndex) {
        private static MatchScore none() {
            return new MatchScore(0, NO_MATCH_INDEX);
        }
    }

    private record ScoredResult(
        GlobalSearchResult result,
        int typePriority,
        int firstMatchIndex,
        String normalizedTitle,
        String normalizedId
    ) {
    }
}
