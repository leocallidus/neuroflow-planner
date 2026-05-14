package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.util.ConfigManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class FocusBlockRecommendationService {

    static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(20);
    private static final int AI_MAX_TOKENS = 260;
    private static final double AI_TEMPERATURE = 0.2;

    private final TaskApplicationService taskApplicationService;
    private final FocusProductivityProfileBuilder profileBuilder;
    private final FocusBlockCandidateWindowCalculator candidateWindowCalculator;
    private final FocusBlockRecommendationEngine recommendationEngine;
    private final Supplier<AiClient> aiClientSupplier;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ConcurrentMap<LocalDate, FocusBlockRecommendationResult> cache = new ConcurrentHashMap<>();

    public FocusBlockRecommendationService() {
        this(
                new DefaultTaskApplicationService(),
                new FocusProductivityProfileBuilder(),
                new FocusBlockCandidateWindowCalculator(),
                new FocusBlockRecommendationEngine(),
                () -> AiClientFactory.getInstance().getActiveClient(),
                Clock.systemDefaultZone(),
                DEFAULT_CACHE_TTL
        );
    }

    FocusBlockRecommendationService(
            TaskApplicationService taskApplicationService,
            FocusProductivityProfileBuilder profileBuilder,
            FocusBlockCandidateWindowCalculator candidateWindowCalculator,
            FocusBlockRecommendationEngine recommendationEngine,
            Supplier<AiClient> aiClientSupplier,
            Clock clock,
            Duration cacheTtl) {
        this.taskApplicationService = taskApplicationService == null ? new DefaultTaskApplicationService() : taskApplicationService;
        this.profileBuilder = profileBuilder == null ? new FocusProductivityProfileBuilder() : profileBuilder;
        this.candidateWindowCalculator = candidateWindowCalculator == null
                ? new FocusBlockCandidateWindowCalculator()
                : candidateWindowCalculator;
        this.recommendationEngine = recommendationEngine == null
                ? new FocusBlockRecommendationEngine()
                : recommendationEngine;
        this.aiClientSupplier = aiClientSupplier == null ? () -> null : aiClientSupplier;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? DEFAULT_CACHE_TTL
                : cacheTtl;
        restorePersistedRecommendations();
    }

    public CompletableFuture<FocusBlockRecommendationResult> getTodayRecommendations() {
        return getRecommendations(LocalDate.now(clock), false);
    }

    public CompletableFuture<FocusBlockRecommendationResult> refreshTodayRecommendations() {
        return getRecommendations(LocalDate.now(clock), true);
    }

    public CompletableFuture<FocusBlockRecommendationResult> getRecommendations(LocalDate reviewDate, boolean forceRefresh) {
        LocalDate effectiveDate = reviewDate == null ? LocalDate.now(clock) : reviewDate;
        FocusBlockRecommendationSnapshot baseSnapshot = buildSnapshot(effectiveDate);
        FocusBlockRecommendationResult cached = cache.get(effectiveDate);
        if (!forceRefresh && isFresh(cached, effectiveDate, baseSnapshot)) {
            return CompletableFuture.completedFuture(cached.withFromCache(true));
        }

        FocusBlockAiPromptPayload promptPayload = FocusBlockPromptFactory.build(baseSnapshot);
        AiClient client = resolveAiClient();
        if (client == null || !shouldUseAi(baseSnapshot)) {
            FocusBlockRecommendationResult fallback = buildFallbackResult(baseSnapshot, promptPayload, "", false);
            cacheAndPersist(fallback);
            return CompletableFuture.completedFuture(fallback);
        }

        String modelId = resolveModelId(client);
        AiRequestOptions options = AiRequestOptions.builder()
                .model(modelId)
                .systemPrompt(promptPayload.systemPrompt())
                .temperature(AI_TEMPERATURE)
                .maxTokens(AI_MAX_TOKENS)
                .build();

        return client.sendChatMessage(promptPayload.userPrompt(), options)
                .handle((response, throwable) -> {
                    FocusBlockRecommendationResult result = buildResult(baseSnapshot, promptPayload, modelId, response, throwable);
                    cacheAndPersist(result);
                    return result;
                });
    }

    private FocusBlockRecommendationSnapshot buildSnapshot(LocalDate reviewDate) {
        FocusProductivityProfile profile = profileBuilder.buildProfile(reviewDate, FocusProductivityProfileBuilder.DEFAULT_LOOKBACK_DAYS);
        List<FocusBlockCandidate> candidateWindows = candidateWindowCalculator.calculateForDate(reviewDate);
        TaskCounts counts = buildTaskCounts(reviewDate);
        FocusBlockRecommendationEngineResult engineResult = recommendationEngine.recommend(
                new FocusBlockRecommendationEngineInput(
                        reviewDate,
                        profile,
                        candidateWindows,
                        counts.activeTaskCount(),
                        counts.overdueTaskCount(),
                        counts.upcomingTaskCount()
                )
        );

        return new FocusBlockRecommendationSnapshot(
                reviewDate,
                Instant.now(clock),
                FocusBlockExplanation.unavailable(),
                profile,
                candidateWindows,
                engineResult.focusWindows(),
                engineResult.shortWindows(),
                engineResult.nextRecommendedBlock(),
                engineResult.risks(),
                profile.limitedHistory()
        );
    }

    private TaskCounts buildTaskCounts(LocalDate reviewDate) {
        List<Task> activeTasks = flattenTasks(taskApplicationService.loadTasks()).stream()
                .filter(task -> task != null && !task.isArchived() && !task.isCompleted())
                .toList();
        int overdueTaskCount = (int) activeTasks.stream()
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(reviewDate))
                .count();
        int upcomingTaskCount = (int) activeTasks.stream()
                .filter(task -> task.getDeadline() != null
                        && !task.getDeadline().isBefore(reviewDate)
                        && !task.getDeadline().isAfter(reviewDate.plusDays(3)))
                .count();
        return new TaskCounts(activeTasks.size(), overdueTaskCount, upcomingTaskCount);
    }

    private FocusBlockRecommendationResult buildResult(
            FocusBlockRecommendationSnapshot baseSnapshot,
            FocusBlockAiPromptPayload promptPayload,
            String modelId,
            AiResponse response,
            Throwable throwable) {
        FocusBlockExplanation parsed = throwable == null ? parseAiExplanation(baseSnapshot, response) : null;
        if (parsed != null) {
            Instant generatedAt = Instant.now(clock);
            FocusBlockRecommendationSnapshot resolvedSnapshot = withExplanation(baseSnapshot, generatedAt, parsed);
            return new FocusBlockRecommendationResult(resolvedSnapshot, generatedAt, modelId, true, false);
        }
        return buildFallbackResult(baseSnapshot, promptPayload, modelId, false);
    }

    private FocusBlockRecommendationResult buildFallbackResult(
            FocusBlockRecommendationSnapshot baseSnapshot,
            FocusBlockAiPromptPayload promptPayload,
            String modelId,
            boolean fromCache) {
        Instant generatedAt = Instant.now(clock);
        FocusBlockRecommendationSnapshot resolvedSnapshot = withExplanation(
                baseSnapshot,
                generatedAt,
                promptPayload.fallbackExplanation()
        );
        return new FocusBlockRecommendationResult(resolvedSnapshot, generatedAt, modelId, false, fromCache);
    }

    private FocusBlockRecommendationSnapshot withExplanation(
            FocusBlockRecommendationSnapshot baseSnapshot,
            Instant generatedAt,
            FocusBlockExplanation explanation) {
        return new FocusBlockRecommendationSnapshot(
                baseSnapshot.reviewDate(),
                generatedAt,
                explanation,
                baseSnapshot.productivityProfile(),
                baseSnapshot.candidateWindows(),
                baseSnapshot.focusWindows(),
                baseSnapshot.shortWindows(),
                baseSnapshot.nextRecommendedBlock(),
                baseSnapshot.risks(),
                baseSnapshot.limitedHistory()
        );
    }

    private FocusBlockExplanation parseAiExplanation(FocusBlockRecommendationSnapshot snapshot, AiResponse response) {
        if (response == null || !response.success()) {
            return null;
        }
        String content = response.getContentOptional()
                .map(String::trim)
                .orElse("");
        if (content.isBlank()) {
            return null;
        }

        Map<String, List<String>> sections = extractSections(content);
        List<String> mainBlock = sections.getOrDefault("главный блок", List.of());
        List<String> reasons = sections.getOrDefault("почему он подходит", List.of());
        List<String> fallback = sections.getOrDefault("если окно пропустить", List.of());
        if (mainBlock.isEmpty() || reasons.isEmpty() || fallback.isEmpty()) {
            return null;
        }

        String headline = mainBlock.getFirst().trim();
        String summary = joinBullets(reasons);
        String nextAction = fallback.getFirst().trim();
        if (headline.isBlank() || summary.isBlank() || nextAction.isBlank()) {
            return null;
        }

        String limitations = snapshot.limitedHistory()
                ? "Объяснение основано на ограниченной истории трекинга."
                : "";
        return new FocusBlockExplanation(
                FocusBlockSummarySource.AI,
                headline,
                summary,
                nextAction,
                limitations
        );
    }

    private Map<String, List<String>> extractSections(String content) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String currentSection = null;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("##")) {
                currentSection = normalizeHeading(line.replaceFirst("^#+", "").trim());
                sections.computeIfAbsent(currentSection, ignored -> new ArrayList<>());
                continue;
            }
            if (currentSection == null) {
                continue;
            }
            String normalizedBullet = normalizeBullet(line);
            if (!normalizedBullet.isBlank()) {
                sections.computeIfAbsent(currentSection, ignored -> new ArrayList<>()).add(normalizedBullet);
            }
        }
        return sections;
    }

    private String normalizeHeading(String heading) {
        return heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBullet(String line) {
        if (line == null) {
            return "";
        }
        return line.trim()
                .replaceFirst("^[-*•]+\\s*", "")
                .trim();
    }

    private String joinBullets(List<String> bullets) {
        if (bullets == null || bullets.isEmpty()) {
            return "";
        }
        return bullets.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private boolean shouldUseAi(FocusBlockRecommendationSnapshot snapshot) {
        return snapshot != null
                && (snapshot.nextRecommendedBlock().available()
                || snapshot.hasFocusWindows()
                || snapshot.hasShortWindows()
                || snapshot.hasRisks());
    }

    private AiClient resolveAiClient() {
        AiClient client = aiClientSupplier.get();
        if (client == null || !client.isConfigured()) {
            return null;
        }
        return client;
    }

    private String resolveModelId(AiClient client) {
        if (client == null) {
            return "";
        }
        String model = client.getDefaultModel();
        return model == null ? "" : model.trim();
    }

    private boolean isFresh(
            FocusBlockRecommendationResult cached,
            LocalDate reviewDate,
            FocusBlockRecommendationSnapshot baseSnapshot) {
        if (cached == null || cached.generatedAt() == null || !Objects.equals(cached.reviewDate(), reviewDate)) {
            return false;
        }
        if (!Objects.equals(buildSnapshotFingerprint(cached.snapshot()), buildSnapshotFingerprint(baseSnapshot))) {
            return false;
        }
        return true;
    }

    static String buildSnapshotFingerprint(FocusBlockRecommendationSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.reviewDate()).append('|')
                .append(snapshot.limitedHistory()).append('|')
                .append(snapshot.productivityProfile().confidence()).append('|')
                .append(snapshot.productivityProfile().stableFocusMinutes()).append('|')
                .append(snapshot.productivityProfile().switchDensityScore());
        for (FocusBlockCandidate candidate : snapshot.candidateWindows()) {
            builder.append("|c:")
                    .append(candidate.startAt()).append(':')
                    .append(candidate.endAt()).append(':')
                    .append(candidate.type()).append(':')
                    .append(candidate.suitabilityScore()).append(':')
                    .append(candidate.approximate());
        }
        for (FocusBlockRisk risk : snapshot.risks()) {
            builder.append("|r:")
                    .append(risk.level()).append(':')
                    .append(risk.title()).append(':')
                    .append(risk.detail());
        }
        FocusBlockRecommendation next = snapshot.nextRecommendedBlock();
        builder.append("|n:")
                .append(next.startAt()).append(':')
                .append(next.endAt()).append(':')
                .append(next.type()).append(':')
                .append(next.suitabilityScore());
        return Integer.toHexString(builder.toString().hashCode());
    }

    private void cacheAndPersist(FocusBlockRecommendationResult result) {
        if (result == null) {
            return;
        }
        cache.put(result.reviewDate(), result);
        ConfigManager.setPersistedFocusBlockRecommendations(toPersistenceRecord(result));
    }

    private void restorePersistedRecommendations() {
        FocusBlockPersistenceRecord persisted = ConfigManager.getPersistedFocusBlockRecommendations();
        if (persisted == null) {
            return;
        }
        cache.put(persisted.reviewDate(), fromPersistenceRecord(persisted));
    }

    private FocusBlockPersistenceRecord toPersistenceRecord(FocusBlockRecommendationResult result) {
        FocusBlockRecommendationSnapshot snapshot = result.snapshot();
        FocusProductivityProfile profile = snapshot.productivityProfile();
        return new FocusBlockPersistenceRecord(
                result.reviewDate(),
                result.generatedAt(),
                result.modelId(),
                result.aiUsed(),
                buildSnapshotFingerprint(snapshot),
                snapshot.limitedHistory(),
                profile.confidence(),
                profile.switchDensityScore(),
                profile.averageFocusMinutes(),
                profile.stableFocusMinutes(),
                profile.totalTrackedMinutes(),
                profile.totalSessions(),
                snapshot.explanation(),
                snapshot.candidateWindows(),
                snapshot.focusWindows(),
                snapshot.shortWindows(),
                snapshot.nextRecommendedBlock(),
                snapshot.risks()
        );
    }

    private FocusBlockRecommendationResult fromPersistenceRecord(FocusBlockPersistenceRecord persisted) {
        FocusProductivityProfile profile = new FocusProductivityProfile(
                persisted.generatedAt(),
                persisted.profileConfidence(),
                persisted.switchDensityScore(),
                persisted.averageFocusMinutes(),
                persisted.stableFocusMinutes(),
                persisted.totalTrackedMinutes(),
                persisted.totalSessions(),
                persisted.limitedHistory(),
                List.of(),
                List.of()
        );
        FocusBlockRecommendationSnapshot snapshot = new FocusBlockRecommendationSnapshot(
                persisted.reviewDate(),
                persisted.generatedAt(),
                persisted.explanation(),
                profile,
                persisted.candidateWindows(),
                persisted.focusWindows(),
                persisted.shortWindows(),
                persisted.nextRecommendedBlock(),
                persisted.risks(),
                persisted.limitedHistory()
        );
        return new FocusBlockRecommendationResult(snapshot, persisted.generatedAt(), persisted.modelId(), persisted.aiUsed(), false);
    }

    private List<Task> flattenTasks(List<Task> roots) {
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<Task> all = new ArrayList<>();
        Deque<Task> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            Task task = queue.removeFirst();
            if (task == null) {
                continue;
            }
            all.add(task);
            if (task.getSubtasks() != null && !task.getSubtasks().isEmpty()) {
                queue.addAll(task.getSubtasks());
            }
        }
        return List.copyOf(all);
    }

    private record TaskCounts(int activeTaskCount, int overdueTaskCount, int upcomingTaskCount) {
    }
}
