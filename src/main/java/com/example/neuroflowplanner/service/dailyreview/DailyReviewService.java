package com.example.neuroflowplanner.service.dailyreview;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.util.ConfigManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class DailyReviewService {

    static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(20);
    private static final int AI_MAX_TOKENS = 450;
    private static final double AI_TEMPERATURE = 0.2;
    private static final int MAX_SUMMARY_BULLETS = 6;

    private final DailyReviewBuilder builder;
    private final Supplier<AiClient> aiClientSupplier;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ConcurrentMap<LocalDate, DailyReviewResult> cache = new ConcurrentHashMap<>();

    public DailyReviewService() {
        this(
                new DailyReviewBuilder(),
                () -> AiClientFactory.getInstance().getActiveClient(),
                Clock.systemDefaultZone(),
                DEFAULT_CACHE_TTL
        );
    }

    DailyReviewService(
            DailyReviewBuilder builder,
            Supplier<AiClient> aiClientSupplier,
            Clock clock,
            Duration cacheTtl) {
        this.builder = builder == null ? new DailyReviewBuilder() : builder;
        this.aiClientSupplier = aiClientSupplier == null ? () -> null : aiClientSupplier;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() || cacheTtl.isZero()
                ? DEFAULT_CACHE_TTL
                : cacheTtl;
        restorePersistedReview();
    }

    public CompletableFuture<DailyReviewResult> getTodayReview() {
        return getReview(LocalDate.now(clock), false);
    }

    public CompletableFuture<DailyReviewResult> refreshTodayReview() {
        return getReview(LocalDate.now(clock), true);
    }

    public CompletableFuture<DailyReviewResult> getReview(LocalDate reviewDate, boolean forceRefresh) {
        LocalDate effectiveDate = reviewDate == null ? LocalDate.now(clock) : reviewDate;
        DailyReviewSnapshot baseSnapshot = builder.buildForDate(effectiveDate);
        String snapshotFingerprint = buildSnapshotFingerprint(baseSnapshot);
        DailyReviewResult cached = cache.get(effectiveDate);
        if (!forceRefresh && isFresh(cached, effectiveDate, snapshotFingerprint)) {
            return CompletableFuture.completedFuture(cached.withFromCache(true));
        }
        DailyReviewAiPromptPayload promptPayload = DailyReviewPromptFactory.build(baseSnapshot);
        AiClient client = resolveAiClient();
        if (client == null) {
            DailyReviewResult fallback = buildFallbackResult(baseSnapshot, promptPayload, null, false);
            cacheAndPersist(fallback, snapshotFingerprint);
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
                    DailyReviewResult result = buildResult(baseSnapshot, promptPayload, modelId, response, throwable);
                    cacheAndPersist(result, snapshotFingerprint);
                    return result;
                });
    }

    private DailyReviewResult buildResult(
            DailyReviewSnapshot baseSnapshot,
            DailyReviewAiPromptPayload promptPayload,
            String modelId,
            AiResponse response,
            Throwable throwable) {
        ParsedDailyReviewAiOutput parsed = throwable == null ? parseAiOutput(response) : null;
        if (parsed != null) {
            Instant generatedAt = Instant.now(clock);
            DailyReviewSnapshot resolvedSnapshot = new DailyReviewSnapshot(
                    baseSnapshot.reviewDate(),
                    generatedAt,
                    baseSnapshot.activeTaskCount(),
                    baseSnapshot.overdueTaskCount(),
                    baseSnapshot.tasksDueTodayCount(),
                    baseSnapshot.upcomingTaskCount(),
                    baseSnapshot.trackedMinutesToday(),
                    baseSnapshot.approximateFreeWindows(),
                    parsed.summary(),
                    baseSnapshot.overdueItems(),
                    baseSnapshot.upcomingItems(),
                    baseSnapshot.workIntervals(),
                    baseSnapshot.knownTimeBlocks(),
                    baseSnapshot.freeWindows(),
                    parsed.focusRecommendation()
            );
            return new DailyReviewResult(resolvedSnapshot, generatedAt, modelId, true, false);
        }
        return buildFallbackResult(baseSnapshot, promptPayload, modelId, false);
    }

    private DailyReviewResult buildFallbackResult(
            DailyReviewSnapshot baseSnapshot,
            DailyReviewAiPromptPayload promptPayload,
            String modelId,
            boolean fromCache) {
        Instant generatedAt = Instant.now(clock);
        DailyReviewSnapshot fallbackSnapshot = new DailyReviewSnapshot(
                baseSnapshot.reviewDate(),
                generatedAt,
                baseSnapshot.activeTaskCount(),
                baseSnapshot.overdueTaskCount(),
                baseSnapshot.tasksDueTodayCount(),
                baseSnapshot.upcomingTaskCount(),
                baseSnapshot.trackedMinutesToday(),
                baseSnapshot.approximateFreeWindows(),
                promptPayload.fallbackSummary(),
                baseSnapshot.overdueItems(),
                baseSnapshot.upcomingItems(),
                baseSnapshot.workIntervals(),
                baseSnapshot.knownTimeBlocks(),
                baseSnapshot.freeWindows(),
                promptPayload.fallbackFocusRecommendation()
        );
        return new DailyReviewResult(fallbackSnapshot, generatedAt, modelId, false, fromCache);
    }

    private ParsedDailyReviewAiOutput parseAiOutput(AiResponse response) {
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
        List<String> overview = sections.getOrDefault("общая картина дня", List.of());
        List<String> risks = sections.getOrDefault("риски", List.of());
        List<String> priorities = sections.getOrDefault("приоритеты", List.of());
        List<String> focus = sections.getOrDefault("фокус-рекомендация", List.of());

        if (overview.isEmpty() || focus.isEmpty()) {
            return null;
        }

        List<String> summaryBullets = new ArrayList<>();
        summaryBullets.addAll(overview);
        summaryBullets.addAll(priorities);
        if (summaryBullets.isEmpty()) {
            return null;
        }
        if (summaryBullets.size() > MAX_SUMMARY_BULLETS) {
            summaryBullets = List.copyOf(summaryBullets.subList(0, MAX_SUMMARY_BULLETS));
        }

        String headline = firstNonBlank(overview, summaryBullets);
        String riskNote = String.join(" ", risks).trim();
        String nextStep = firstNonBlank(focus, List.of());
        if (headline.isBlank() || nextStep.isBlank()) {
            return null;
        }

        DailyReviewSummary summary = new DailyReviewSummary(
                DailyReviewSummarySource.AI,
                headline,
                summaryBullets,
                riskNote,
                nextStep,
                ""
        );
        DailyReviewFocusRecommendation focusRecommendation = new DailyReviewFocusRecommendation(
                firstNonBlank(priorities, List.of("Фокус дня")),
                !riskNote.isBlank() ? riskNote : headline,
                nextStep,
                DailyReviewSummarySource.AI
        );
        return new ParsedDailyReviewAiOutput(summary, focusRecommendation);
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
        String normalized = line.trim()
                .replaceFirst("^[-*•]+\\s*", "")
                .trim();
        return normalized;
    }

    private String firstNonBlank(List<String> primary, List<String> fallback) {
        for (String item : primary) {
            if (item != null && !item.isBlank()) {
                return item.trim();
            }
        }
        for (String item : fallback) {
            if (item != null && !item.isBlank()) {
                return item.trim();
            }
        }
        return "";
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

    private boolean isFresh(DailyReviewResult cached, LocalDate reviewDate) {
        return isFresh(cached, reviewDate, "");
    }

    private boolean isFresh(DailyReviewResult cached, LocalDate reviewDate, String fingerprint) {
        if (cached == null || cached.generatedAt() == null || !Objects.equals(cached.reviewDate(), reviewDate)) {
            return false;
        }
        if (!fingerprint.isBlank()) {
            DailyReviewPersistenceRecord persisted = ConfigManager.getPersistedDailyReview();
            if (persisted == null
                    || !Objects.equals(persisted.reviewDate(), reviewDate)
                    || !Objects.equals(persisted.snapshotFingerprint(), fingerprint)) {
                return false;
            }
        }
        Duration age = Duration.between(cached.generatedAt(), Instant.now(clock));
        return !age.isNegative() && age.compareTo(cacheTtl) < 0;
    }

    private void cacheAndPersist(DailyReviewResult result, String fingerprint) {
        if (result == null) {
            return;
        }
        cache.put(result.reviewDate(), result);
        ConfigManager.setPersistedDailyReview(toPersistenceRecord(result, fingerprint));
    }

    private void restorePersistedReview() {
        DailyReviewPersistenceRecord persisted = ConfigManager.getPersistedDailyReview();
        if (persisted == null) {
            return;
        }
        cache.put(persisted.reviewDate(), fromPersistenceRecord(persisted));
    }

    static String buildSnapshotFingerprint(DailyReviewSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(snapshot.reviewDate()).append('|')
                .append(snapshot.activeTaskCount()).append('|')
                .append(snapshot.overdueTaskCount()).append('|')
                .append(snapshot.tasksDueTodayCount()).append('|')
                .append(snapshot.upcomingTaskCount()).append('|')
                .append(snapshot.trackedMinutesToday()).append('|')
                .append(snapshot.approximateFreeWindows());
        for (DailyReviewOverdueItem item : snapshot.overdueItems()) {
            builder.append("|od:")
                    .append(item.taskId()).append(':')
                    .append(item.deadlineDate()).append(':')
                    .append(item.deadlineDateTime()).append(':')
                    .append(item.overdueDays());
        }
        for (DailyReviewUpcomingItem item : snapshot.upcomingItems()) {
            builder.append("|up:")
                    .append(item.taskId()).append(':')
                    .append(item.deadlineDate()).append(':')
                    .append(item.deadlineDateTime()).append(':')
                    .append(item.daysUntilDue()).append(':')
                    .append(item.urgent());
        }
        for (DailyReviewFreeWindow item : snapshot.freeWindows()) {
            builder.append("|fw:")
                    .append(item.start()).append(':')
                    .append(item.end()).append(':')
                    .append(item.durationMinutes()).append(':')
                    .append(item.suitability()).append(':')
                    .append(item.approximate());
        }
        return Integer.toHexString(builder.toString().hashCode());
    }

    private DailyReviewPersistenceRecord toPersistenceRecord(DailyReviewResult result, String fingerprint) {
        DailyReviewSnapshot snapshot = result.snapshot();
        return new DailyReviewPersistenceRecord(
                result.reviewDate(),
                result.generatedAt(),
                result.modelId(),
                result.aiUsed(),
                fingerprint,
                snapshot.activeTaskCount(),
                snapshot.overdueTaskCount(),
                snapshot.tasksDueTodayCount(),
                snapshot.upcomingTaskCount(),
                snapshot.trackedMinutesToday(),
                snapshot.approximateFreeWindows(),
                snapshot.summary(),
                snapshot.focusRecommendation(),
                snapshot.overdueItems(),
                snapshot.upcomingItems(),
                snapshot.freeWindows()
        );
    }

    private DailyReviewResult fromPersistenceRecord(DailyReviewPersistenceRecord persisted) {
        DailyReviewSnapshot snapshot = new DailyReviewSnapshot(
                persisted.reviewDate(),
                persisted.generatedAt(),
                persisted.activeTaskCount(),
                persisted.overdueTaskCount(),
                persisted.tasksDueTodayCount(),
                persisted.upcomingTaskCount(),
                persisted.trackedMinutesToday(),
                persisted.approximateFreeWindows(),
                persisted.summary(),
                persisted.overdueItems(),
                persisted.upcomingItems(),
                List.of(),
                List.of(),
                persisted.freeWindows(),
                persisted.focusRecommendation()
        );
        return new DailyReviewResult(snapshot, persisted.generatedAt(), persisted.modelId(), persisted.aiUsed(), false);
    }

    private record ParsedDailyReviewAiOutput(
            DailyReviewSummary summary,
            DailyReviewFocusRecommendation focusRecommendation) {
    }
}
