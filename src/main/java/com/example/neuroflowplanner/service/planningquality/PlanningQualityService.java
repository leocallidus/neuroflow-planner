package com.example.neuroflowplanner.service.planningquality;

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

public class PlanningQualityService {

    static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(20);
    static final int DEFAULT_LOOKBACK_DAYS = PlanningQualitySnapshotBuilder.DEFAULT_LOOKBACK_DAYS;
    private static final int AI_MAX_TOKENS = 320;
    private static final double AI_TEMPERATURE = 0.2;

    private final PlanningQualitySnapshotBuilder snapshotBuilder;
    private final Supplier<AiClient> aiClientSupplier;
    private final Clock clock;
    private final Duration cacheTtl;
    private final ConcurrentMap<PeriodKey, PlanningQualityResult> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<PeriodKey, String> cacheFingerprints = new ConcurrentHashMap<>();

    public PlanningQualityService() {
        this(
                new PlanningQualitySnapshotBuilder(),
                () -> AiClientFactory.getInstance().getActiveClient(),
                Clock.systemDefaultZone(),
                DEFAULT_CACHE_TTL
        );
    }

    PlanningQualityService(
            PlanningQualitySnapshotBuilder snapshotBuilder,
            Supplier<AiClient> aiClientSupplier,
            Clock clock,
            Duration cacheTtl) {
        this.snapshotBuilder = snapshotBuilder == null ? new PlanningQualitySnapshotBuilder() : snapshotBuilder;
        this.aiClientSupplier = aiClientSupplier == null ? () -> null : aiClientSupplier;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.cacheTtl = cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()
                ? DEFAULT_CACHE_TTL
                : cacheTtl;
        restorePersistedDashboard();
    }

    public CompletableFuture<PlanningQualityResult> getRecentDashboard() {
        LocalDate end = LocalDate.now(clock);
        return getDashboard(end.minusDays(DEFAULT_LOOKBACK_DAYS - 1L), end, false);
    }

    public CompletableFuture<PlanningQualityResult> refreshRecentDashboard() {
        LocalDate end = LocalDate.now(clock);
        return getDashboard(end.minusDays(DEFAULT_LOOKBACK_DAYS - 1L), end, true);
    }

    public CompletableFuture<PlanningQualityResult> getDashboard(LocalDate periodStart, LocalDate periodEnd, boolean forceRefresh) {
        LocalDate safeEnd = periodEnd == null ? LocalDate.now(clock) : periodEnd;
        LocalDate safeStart = periodStart == null ? safeEnd.minusDays(DEFAULT_LOOKBACK_DAYS - 1L) : periodStart;
        if (safeStart.isAfter(safeEnd)) {
            LocalDate originalStart = safeStart;
            safeStart = safeEnd;
            safeEnd = originalStart;
        }

        PlanningQualitySnapshot baseSnapshot = snapshotBuilder.buildForPeriod(safeStart, safeEnd);
        PeriodKey cacheKey = new PeriodKey(baseSnapshot.periodStart(), baseSnapshot.periodEnd());
        String snapshotFingerprint = buildSnapshotFingerprint(baseSnapshot);
        PlanningQualityResult cached = cache.get(cacheKey);
        if (!forceRefresh && isFresh(cached, snapshotFingerprint)) {
            return CompletableFuture.completedFuture(cached.withFromCache(true));
        }

        PlanningQualityAiPromptPayload promptPayload = PlanningQualityPromptFactory.build(baseSnapshot);
        AiClient client = resolveAiClient();
        if (client == null || !shouldUseAi(baseSnapshot)) {
            PlanningQualityResult fallback = buildFallbackResult(baseSnapshot, promptPayload, "", false);
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
                    PlanningQualityResult result = buildResult(baseSnapshot, promptPayload, modelId, response, throwable);
                    cacheAndPersist(result, snapshotFingerprint);
                    return result;
                });
    }

    private PlanningQualityResult buildResult(
            PlanningQualitySnapshot baseSnapshot,
            PlanningQualityAiPromptPayload promptPayload,
            String modelId,
            AiResponse response,
            Throwable throwable) {
        PlanningQualitySummary parsed = throwable == null ? parseAiSummary(baseSnapshot, response) : null;
        if (parsed != null) {
            Instant generatedAt = Instant.now(clock);
            PlanningQualitySnapshot resolvedSnapshot = withSummary(baseSnapshot, generatedAt, parsed);
            return new PlanningQualityResult(resolvedSnapshot, generatedAt, modelId, true, false);
        }
        return buildFallbackResult(baseSnapshot, promptPayload, modelId, false);
    }

    private PlanningQualityResult buildFallbackResult(
            PlanningQualitySnapshot baseSnapshot,
            PlanningQualityAiPromptPayload promptPayload,
            String modelId,
            boolean fromCache) {
        Instant generatedAt = Instant.now(clock);
        PlanningQualitySnapshot resolvedSnapshot = withSummary(baseSnapshot, generatedAt, promptPayload.fallbackSummary());
        return new PlanningQualityResult(resolvedSnapshot, generatedAt, modelId, false, fromCache);
    }

    private PlanningQualitySnapshot withSummary(
            PlanningQualitySnapshot baseSnapshot,
            Instant generatedAt,
            PlanningQualitySummary summary) {
        return new PlanningQualitySnapshot(
                baseSnapshot.periodStart(),
                baseSnapshot.periodEnd(),
                generatedAt,
                summary,
                baseSnapshot.accuracyMetric(),
                baseSnapshot.rescheduleMetric(),
                baseSnapshot.rhythmMetric(),
                baseSnapshot.risks(),
                baseSnapshot.recommendations(),
                baseSnapshot.dayAggregates(),
                baseSnapshot.activeTaskCount(),
                baseSnapshot.completedTaskCount(),
                baseSnapshot.estimatedTaskCount(),
                baseSnapshot.scheduledTaskCount(),
                baseSnapshot.trackedTaskCount(),
                baseSnapshot.trackedSessionCount(),
                baseSnapshot.limitedData()
        );
    }

    private PlanningQualitySummary parseAiSummary(PlanningQualitySnapshot snapshot, AiResponse response) {
        if (response == null || !response.success()) {
            return null;
        }
        String content = response.getContentOptional().map(String::trim).orElse("");
        if (content.isBlank()) {
            return null;
        }

        Map<String, List<String>> sections = extractSections(content);
        List<String> overview = sections.getOrDefault("общая картина", List.of());
        List<String> weakZone = sections.getOrDefault("слабая зона", List.of());
        List<String> strengths = sections.getOrDefault("сильные стороны", List.of());
        List<String> nextStep = sections.getOrDefault("что улучшить прямо сейчас", List.of());

        if (overview.isEmpty() || nextStep.isEmpty()) {
            return null;
        }

        String headline = firstNonBlank(overview, List.of());
        List<String> summaryParts = new ArrayList<>();
        addAll(summaryParts, overview);
        addAll(summaryParts, weakZone);
        addAll(summaryParts, strengths);
        String summary = joinBullets(summaryParts);
        String action = firstNonBlank(nextStep, List.of());
        if (headline.isBlank() || summary.isBlank() || action.isBlank()) {
            return null;
        }

        String limitations = snapshot.limitedData()
                ? "AI-сводка опирается на ограниченную выборку planning quality signals."
                : buildLimitations(snapshot);
        return new PlanningQualitySummary(
                PlanningQualitySummarySource.AI,
                headline,
                summary,
                action,
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
            String normalized = normalizeBullet(line);
            if (!normalized.isBlank()) {
                sections.computeIfAbsent(currentSection, ignored -> new ArrayList<>()).add(normalized);
            }
        }
        return sections;
    }

    private void addAll(List<String> target, List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .forEach(target::add);
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

    private String buildLimitations(PlanningQualitySnapshot snapshot) {
        List<String> limitations = new ArrayList<>();
        if (snapshot.rescheduleMetric().available() && snapshot.rescheduleMetric().approximate()) {
            limitations.add("reschedule rate остаётся heuristic");
        }
        if (snapshot.rhythmMetric().available() && snapshot.rhythmMetric().approximate()) {
            limitations.add("rhythm stability опирается на неполную историю");
        }
        return String.join("; ", limitations);
    }

    private boolean shouldUseAi(PlanningQualitySnapshot snapshot) {
        return snapshot != null && (
                snapshot.hasDeterministicMetrics()
                        || snapshot.hasRisks()
                        || snapshot.hasRecommendations()
        );
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

    private boolean isFresh(PlanningQualityResult cached, String snapshotFingerprint) {
        if (cached == null || cached.snapshot() == null) {
            return false;
        }
        if (Duration.between(cached.generatedAt(), Instant.now(clock)).compareTo(cacheTtl) > 0) {
            return false;
        }
        PeriodKey cacheKey = new PeriodKey(cached.periodStart(), cached.periodEnd());
        String cachedFingerprint = cacheFingerprints.get(cacheKey);
        if ((cachedFingerprint == null || cachedFingerprint.isBlank())
                && !snapshotFingerprint.equals(buildSnapshotFingerprint(cached.snapshot()))) {
            return false;
        }
        if (cachedFingerprint != null && !cachedFingerprint.isBlank() && !snapshotFingerprint.equals(cachedFingerprint)) {
            return false;
        }
        PlanningQualityPersistenceRecord persisted = ConfigManager.getPersistedPlanningQuality();
        if (persisted == null) {
            return false;
        }
        return cached.periodStart().equals(persisted.periodStart())
                && cached.periodEnd().equals(persisted.periodEnd())
                && snapshotFingerprint.equals(persisted.snapshotFingerprint());
    }

    private String buildSnapshotFingerprint(PlanningQualitySnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        return snapshot.periodStart()
                + "|"
                + snapshot.periodEnd()
                + "|"
                + snapshot.activeTaskCount()
                + "|"
                + snapshot.completedTaskCount()
                + "|"
                + snapshot.estimatedTaskCount()
                + "|"
                + snapshot.scheduledTaskCount()
                + "|"
                + snapshot.trackedTaskCount()
                + "|"
                + snapshot.trackedSessionCount()
                + "|"
                + snapshot.accuracyMetric().comparableTaskCount()
                + "|"
                + snapshot.rescheduleMetric().rescheduledTaskCount()
                + "|"
                + snapshot.rhythmMetric().analyzedDayCount()
                + "|"
                + snapshot.rhythmMetric().productiveDayCount()
                + "|"
                + snapshot.hasRisks()
                + "|"
                + snapshot.hasRecommendations()
                + "|"
                + snapshot.dayAggregates().stream()
                .map(day -> day.date()
                        + ":"
                        + day.scheduledTaskCount()
                        + ":"
                        + day.completedTaskCount()
                        + ":"
                        + day.trackedSessionCount()
                        + ":"
                        + day.trackedMinutes())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private void cacheAndPersist(PlanningQualityResult result, String snapshotFingerprint) {
        if (result == null || result.snapshot() == null) {
            return;
        }
        PeriodKey cacheKey = new PeriodKey(result.periodStart(), result.periodEnd());
        cache.put(cacheKey, result);
        cacheFingerprints.put(cacheKey, snapshotFingerprint == null ? "" : snapshotFingerprint);
        ConfigManager.setPersistedPlanningQuality(toPersistenceRecord(result, snapshotFingerprint));
    }

    private void restorePersistedDashboard() {
        PlanningQualityPersistenceRecord persisted = ConfigManager.getPersistedPlanningQuality();
        if (persisted == null) {
            return;
        }
        PlanningQualityResult restored = fromPersistenceRecord(persisted);
        PeriodKey cacheKey = new PeriodKey(restored.periodStart(), restored.periodEnd());
        cache.put(cacheKey, restored);
        cacheFingerprints.put(cacheKey, persisted.snapshotFingerprint());
    }

    private PlanningQualityPersistenceRecord toPersistenceRecord(PlanningQualityResult result, String snapshotFingerprint) {
        PlanningQualitySnapshot snapshot = result == null ? null : result.snapshot();
        if (snapshot == null) {
            return null;
        }
        return new PlanningQualityPersistenceRecord(
                snapshot.periodStart(),
                snapshot.periodEnd(),
                result.generatedAt(),
                result.modelId(),
                result.aiUsed(),
                snapshotFingerprint,
                snapshot.activeTaskCount(),
                snapshot.completedTaskCount(),
                snapshot.estimatedTaskCount(),
                snapshot.scheduledTaskCount(),
                snapshot.trackedTaskCount(),
                snapshot.trackedSessionCount(),
                snapshot.limitedData(),
                snapshot.summary(),
                snapshot.accuracyMetric(),
                snapshot.rescheduleMetric(),
                snapshot.rhythmMetric(),
                snapshot.risks(),
                snapshot.recommendations()
        );
    }

    private PlanningQualityResult fromPersistenceRecord(PlanningQualityPersistenceRecord persisted) {
        PlanningQualitySnapshot snapshot = new PlanningQualitySnapshot(
                persisted.periodStart(),
                persisted.periodEnd(),
                persisted.generatedAt(),
                persisted.summary(),
                persisted.accuracyMetric(),
                persisted.rescheduleMetric(),
                persisted.rhythmMetric(),
                persisted.risks(),
                persisted.recommendations(),
                List.of(),
                persisted.activeTaskCount(),
                persisted.completedTaskCount(),
                persisted.estimatedTaskCount(),
                persisted.scheduledTaskCount(),
                persisted.trackedTaskCount(),
                persisted.trackedSessionCount(),
                persisted.limitedData()
        );
        return new PlanningQualityResult(snapshot, persisted.generatedAt(), persisted.modelId(), persisted.aiUsed(), false);
    }

    private record PeriodKey(LocalDate start, LocalDate end) {
    }
}
