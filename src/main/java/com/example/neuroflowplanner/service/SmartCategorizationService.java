package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.ai.AiClient;
import com.example.neuroflowplanner.ai.AiClientFactory;
import com.example.neuroflowplanner.ai.AiMode;
import com.example.neuroflowplanner.ai.AiRequestOptions;
import com.example.neuroflowplanner.ai.AiResponse;
import com.example.neuroflowplanner.ai.json.AiObjectMapperFactory;
import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.service.task.DefaultTaskApplicationService;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import com.example.neuroflowplanner.util.AsyncContext;
import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SmartCategorizationService {

    private static final StructuredLogger LOG = StructuredLogger.getLogger(SmartCategorizationService.class);
    private static final String FALLBACK_TAG = "без категории";
    private static final int AI_CONCURRENCY = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final int AI_BATCH_SIZE = 10;
    private static final int MAX_IN_FLIGHT_AI_BATCHES_DEFAULT = Math.max(2, AI_CONCURRENCY * 2);
    private static final ExecutorService CATEGORIZATION_EXECUTOR =
        Executors.newSingleThreadExecutor(AsyncContext.namedThreadFactory("smart-categorization", true));
    private static final ExecutorService AI_REQUEST_EXECUTOR =
        Executors.newFixedThreadPool(AI_CONCURRENCY, AsyncContext.namedThreadFactory("smart-categorization-ai", true));
    private static final Object DB_SAVE_LOCK = new Object();
    private static final Object JOB_LOCK = new Object();
    private static CategorizationJob currentJob;

    private final TaskApplicationService taskApplicationService;
    private final int writeChunkSize;
    private final int maxInFlightAiBatches;
    private volatile Boolean aiAvailable = null;

    public SmartCategorizationService() {
        this(null);
    }

    SmartCategorizationService(TaskApplicationService taskApplicationService) {
        this(taskApplicationService, ConfigManager.getDbBulkBatchSize(), MAX_IN_FLIGHT_AI_BATCHES_DEFAULT);
    }

    SmartCategorizationService(TaskApplicationService taskApplicationService, int writeChunkSize, int maxInFlightAiBatches) {
        this.taskApplicationService = taskApplicationService;
        this.writeChunkSize = Math.max(1, writeChunkSize);
        this.maxInFlightAiBatches = Math.max(1, maxInFlightAiBatches);
    }

    private TaskApplicationService taskApplicationService() {
        if (taskApplicationService != null) {
            return taskApplicationService;
        }
        return TaskApplicationServiceHolder.INSTANCE;
    }

    private static final class TaskApplicationServiceHolder {
        private static final TaskApplicationService INSTANCE = new DefaultTaskApplicationService();
    }

    public record Category(String name, String icon, String color, List<String> keywords) {}

    private static final List<Category> CATEGORIES = List.of(
        new Category("Работа", "💼", "blue", List.of(
            "работа", "проект", "клиент", "отчет", "презентация", "митинг", "встреча", "совещание",
            "код", "разработка", "тест", "релиз", "деплой", "баг", "фикс", "review", "pr", "merge",
            "sprint", "задача", "jira", "task", "git", "api", "server", "frontend", "backend", "sql",
            "db", "database", "dev", "bug", "feature", "deadline", "agile", "scrum", "kanban",
            "документация", "аналитика", "метрики", "kpi", "okr", "найм", "резюме", "интервью",
            "зарплата", "бонус", "оффер", "контракт", "договор", "nda", "партнер", "коллега"
        )),
        new Category("Учёба", "📚", "mauve", List.of(
            "учеба", "курс", "лекция", "экзамен", "домашка", "homework", "study", "книга", "читать",
            "конспект", "семинар", "диплом", "курсовая", "реферат", "зачет", "лабораторная", "тест",
            "вебинар", "мастер-класс", "тренинг", "сертификат", "английский", "язык", "урок",
            "преподаватель", "учитель", "студент", "универ", "школа", "колледж", "наука", "статья"
        )),
        new Category("Личное", "🏠", "green", List.of(
            "дом", "квартира", "уборка", "стирка", "ремонт", "интерьер", "мебель", "семья", "родители",
            "дети", "ребенок", "жена", "муж", "друг", "подруга", "свидание", "праздник", "день рождения",
            "подарок", "отдых", "хобби", "фильм", "кино", "сериал", "игра", "музыка", "концерт",
            "театр", "выставка", "путешествие", "отпуск", "билет", "отель", "виза", "паспорт"
        )),
        new Category("Здоровье", "❤️", "red", List.of(
            "врач", "доктор", "больница", "поликлиника", "аптека", "лекарство", "таблетки", "витамины",
            "анализы", "обследование", "зубной", "стоматолог", "терапевт", "здоровье", "спорт",
            "тренировка", "зал", "фитнес", "бег", "йога", "бассейн", "диета", "питание", "сон",
            "режим", "психолог", "медитация"
        )),
        new Category("Покупки", "🛒", "peach", List.of(
            "купить", "заказать", "доставка", "магазин", "супермаркет", "продукты", "еда", "одежда",
            "обувь", "техника", "покупки", "шопинг", "wb", "ozon", "amazon", "aliexpress", "рынок",
            "список покупок"
        )),
        new Category("Финансы", "💰", "yellow", List.of(
            "оплата", "счет", "налог", "банк", "деньги", "бюджет", "инвестиции", "кредит", "долг",
            "ипотека", "коммуналка", "аренда", "перевод", "карта", "кешбэк", "вклад", "акции",
            "крипта", "расходы", "доходы", "финансы", "платеж", "страховка"
        )),
        new Category("Срочное", "🔥", "red", List.of(
            "срочно", "asap", "важно", "критично", "дедлайн", "горит", "немедленно", "urgent",
            "critical", "сейчас", "быстро", "внимание"
        )),
        new Category("Идеи", "💡", "peach", List.of(
            "идея", "план", "подумать", "исследовать", "изучить", "попробовать", "эксперимент",
            "прототип", "концепт", "мысли", "заметка", "инсайт"
        )),
        new Category("Коммуникации", "📧", "sapphire", List.of(
            "письмо", "email", "звонок", "позвонить", "написать", "ответить", "связаться",
            "сообщение", "чат", "смс", "telegram", "whatsapp", "zoom", "skype", "почта",
            "рассылка", "уведомление"
        ))
    );

    public record CategorizedTask(Task task, Category category, double confidence) {}
    public record CategorizeProgress(int processed, int total, int updated, long elapsedMillis, long etaMillis) {}
    public static final class CategorizationJob {
        private final CopyOnWriteArrayList<Consumer<CategorizeProgress>> listeners = new CopyOnWriteArrayList<>();
        private final AtomicReference<CategorizeProgress> lastProgress = new AtomicReference<>();
        private CompletableFuture<CategorizeResult> future;

        private void report(CategorizeProgress progress) {
            lastProgress.set(progress);
            for (Consumer<CategorizeProgress> listener : listeners) {
                try {
                    listener.accept(progress);
                } catch (Exception ignored) {
                }
            }
        }

        public void addListener(Consumer<CategorizeProgress> listener) {
            if (listener == null) {
                return;
            }
            listeners.add(listener);
            CategorizeProgress progress = lastProgress.get();
            if (progress != null) {
                listener.accept(progress);
            }
        }

        public void removeListener(Consumer<CategorizeProgress> listener) {
            if (listener == null) {
                return;
            }
            listeners.remove(listener);
        }

        public CategorizeProgress getLastProgress() {
            return lastProgress.get();
        }

        public CompletableFuture<CategorizeResult> future() {
            return future;
        }

        public boolean isRunning() {
            return future != null && !future.isDone();
        }

        private void clearListeners() {
            listeners.clear();
        }
    }

    /** Динамическая категоризация на основе реальных тегов задач. */
    public Map<String, List<CategorizedTask>> categorize(List<Task> tasks) {
        Map<String, List<CategorizedTask>> result = new LinkedHashMap<>();
        
        for (Task task : tasks) {
            String tags = task.getTags();
            if (tags == null || tags.isBlank()) {
                // Попытка быстрой автоматической категоризации для задач без тегов
                CategorizedTask predicted = detectCategory(task);
                String catName = predicted.category().name();
                result.computeIfAbsent(catName, k -> new ArrayList<>())
                      .add(predicted);
            } else {
                // Берём первый тег как основную категорию
                String[] tagArray = tags.split(",");
                String primaryTag = tagArray[0].trim();
                String catName = capitalizeFirst(primaryTag);
                Category cat = getCategoryForTag(primaryTag);
                
                result.computeIfAbsent(catName, k -> new ArrayList<>())
                      .add(new CategorizedTask(task, cat, 1.0));
            }
        }

        // Сортируем: сначала категории с большим количеством задач, "Без категории" в конце
        Map<String, List<CategorizedTask>> sorted = new LinkedHashMap<>();
        result.entrySet().stream()
            .sorted((a, b) -> {
                if (a.getKey().equals("Без категории")) return 1;
                if (b.getKey().equals("Без категории")) return -1;
                return Integer.compare(b.getValue().size(), a.getValue().size());
            })
            .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        
        return sorted;
    }

    /** Получить категорию с иконкой и цветом для тега. */
    private Category getCategoryForTag(String tag) {
        String lower = tag.toLowerCase();
        
        // Проверяем предопределённые категории
        for (Category cat : CATEGORIES) {
            if (cat.name().toLowerCase().equals(lower)) {
                return cat;
            }
            for (String kw : cat.keywords()) {
                if (lower.contains(kw) || kw.contains(lower)) {
                    return new Category(capitalizeFirst(tag), cat.icon(), cat.color(), List.of(tag));
                }
            }
        }
        
        // Динамические иконки по ключевым словам
        String icon = "🏷️";
        String color = "lavender";
        
        if (lower.contains("работ") || lower.contains("проект") || lower.contains("task")) {
            icon = "💼"; color = "blue";
        } else if (lower.contains("учёб") || lower.contains("учеб") || lower.contains("курс")) {
            icon = "📚"; color = "mauve";
        } else if (lower.contains("личн") || lower.contains("дом") || lower.contains("семь")) {
            icon = "🏠"; color = "green";
        } else if (lower.contains("финанс") || lower.contains("деньг") || lower.contains("оплат")) {
            icon = "💰"; color = "yellow";
        } else if (lower.contains("срочн") || lower.contains("важн") || lower.contains("критич")) {
            icon = "🔥"; color = "red";
        } else if (lower.contains("идея") || lower.contains("план")) {
            icon = "💡"; color = "peach";
        } else if (lower.contains("звон") || lower.contains("письм") || lower.contains("связ")) {
            icon = "📧"; color = "sapphire";
        } else if (lower.contains("здоров") || lower.contains("спорт") || lower.contains("тренир")) {
            icon = "❤️"; color = "red";
        } else if (lower.contains("покуп") || lower.contains("магаз")) {
            icon = "🛒"; color = "peach";
        } else if (lower.contains("встреч") || lower.contains("митинг")) {
            icon = "🤝"; color = "sapphire";
        } else if (lower.contains("код") || lower.contains("разработ") || lower.contains("программ")) {
            icon = "💻"; color = "blue";
        } else if (lower.contains("тест") || lower.contains("баг") || lower.contains("фикс")) {
            icon = "🐛"; color = "red";
        } else if (lower.contains("документ") || lower.contains("отчёт") || lower.contains("отчет")) {
            icon = "📄"; color = "overlay1";
        }
        
        return new Category(capitalizeFirst(tag), icon, color, List.of(tag));
    }

    /** Первая буква заглавная. */
    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    /** Старый метод для совместимости — определяет категорию по ключевым словам. */
    public CategorizedTask detectCategory(Task task) {
        String title = task.getTitle() != null ? task.getTitle() : "";
        String desc = task.getDescription() != null ? task.getDescription() : "";
        String tags = task.getTags() != null ? task.getTags() : "";
        String text = (title + " " + desc + " " + tags).toLowerCase();
        
        Category best = null;
        int maxMatches = 0;

        for (Category cat : CATEGORIES) {
            int matches = 0;
            for (String kw : cat.keywords()) {
                // Используем более гибкий поиск: содержит слово целиком или как часть, если слово длинное
                if (text.contains(kw)) {
                    matches++;
                }
            }
            if (matches > maxMatches) {
                maxMatches = matches;
                best = cat;
            }
        }

        if (best == null || maxMatches == 0) {
            best = new Category("Без категории", "📋", "overlay1", List.of());
            return new CategorizedTask(task, best, 0.0);
        }

        // Если есть совпадения, считаем уверенность.
        // Одно точное совпадение специфичного ключевого слова уже неплохо.
        double confidence = Math.min(1.0, 0.4 + (maxMatches * 0.2)); 
        return new CategorizedTask(task, best, confidence);
    }

    public String suggestTag(Task task) {
        CategorizedTask ct = detectCategory(task);
        if (ct.confidence() > 0.5) { // Чуть строже порог для авто-тега
            return ct.category().name().toLowerCase();
        }
        return null;
    }

    public void autoTagTask(Task task) {
        String suggested = suggestTag(task);
        String tags = task.getTags() != null ? task.getTags() : "";
        String normalized = tags.toLowerCase();
        if (suggested != null && !normalized.contains(suggested)) {
            task.setTags(tags.isEmpty() ? suggested : tags + ", " + suggested);
            return;
        }
        if (tags.isBlank()) {
            task.setTags(FALLBACK_TAG);
        }
    }

    public List<Category> getAllCategories() {
        return CATEGORIES;
    }

    /** Проверить доступность ИИ API (с кэшированием результата). */
    public boolean isAIAvailable() {
        if (aiAvailable != null) return aiAvailable;
        return checkAIAvailability();
    }

    /** Принудительно проверить доступность ИИ API. */
    public boolean checkAIAvailability() {
        try {
            AiClient client = AiClientFactory.getInstance().getActiveClient();
            // В офлайн-режиме AI недоступен для серверных запросов
            if (client.getMode() == AiMode.OFFLINE) {
                aiAvailable = false;
                return false;
            }
            // Синхронная проверка через testConnection
            var result = client.testConnection().join();
            aiAvailable = result.success();
        } catch (Exception e) {
            aiAvailable = false;
        }
        return aiAvailable;
    }

    /** Асинхронная проверка доступности ИИ с таймаутом. */
    public CompletableFuture<Boolean> checkAIAvailabilityAsync() {
        AiClient client = AiClientFactory.getInstance().getActiveClient();
        if (client.getMode() == AiMode.OFFLINE) {
            aiAvailable = false;
            return CompletableFuture.completedFuture(false);
        }
        return client.testConnection()
            .thenApply(result -> {
                aiAvailable = result.success();
                return aiAvailable;
            })
            .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                aiAvailable = false;
                LOG.warning(
                    "smart.categorization.ai.availability.failed",
                    ErrorCode.AI_REQUEST_FAILED,
                    "operation", "checkAIAvailabilityAsync",
                    "model", client.getDefaultModel()
                );
                return false;
            });
    }

    /** Автоматически распределить все задачи по тегам/категориям и сохранить (локальная логика). */
    public void categorizeAndSave() {
        List<Task> all = com.example.neuroflowplanner.db.DatabaseManager.getInstance().loadAllTasks();
        categorizeAndSave(flattenTasks(all));
    }

    void categorizeAndSave(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        List<Task> pendingUpdates = new ArrayList<>(writeChunkSize);
        for (Task task : tasks) {
            if (task == null) {
                continue;
            }
            String before = normalizeTagsSnapshot(task.getTags());
            autoTagTask(task);
            String after = normalizeTagsSnapshot(task.getTags());
            if (!Objects.equals(before, after)) {
                pendingUpdates.add(task);
                flushPendingTagUpdatesIfNeeded(pendingUpdates, "categorizeAndSave");
            }
        }
        flushPendingTagUpdates(pendingUpdates, "categorizeAndSave.final");
    }

    /** ИИ-распределение всех задач по категориям и тегам с сохранением в БД. */
    public CompletableFuture<CategorizeResult> categorizeAllWithAI() {
        return startCategorizationJob(null).future();
    }

    public CompletableFuture<CategorizeResult> categorizeAllWithAI(Consumer<CategorizeProgress> progressCallback) {
        return startCategorizationJob(progressCallback).future();
    }

    public CategorizationJob getActiveCategorizationJob() {
        CategorizationJob job = currentJob;
        if (job != null && job.isRunning()) {
            return job;
        }
        return null;
    }

    public CategorizationJob startCategorizationWithAI(Consumer<CategorizeProgress> progressCallback) {
        return startCategorizationJob(progressCallback);
    }

    private CategorizationJob startCategorizationJob(Consumer<CategorizeProgress> progressCallback) {
        synchronized (JOB_LOCK) {
            if (currentJob != null && currentJob.isRunning()) {
                if (progressCallback != null) {
                    currentJob.addListener(progressCallback);
                }
                return currentJob;
            }
            CategorizationJob job = new CategorizationJob();
            if (progressCallback != null) {
                job.addListener(progressCallback);
            }
            job.future = runCategorization(job::report);
            job.future.whenComplete((result, ex) -> job.clearListeners());
            currentJob = job;
            return job;
        }
    }

    private CompletableFuture<CategorizeResult> runCategorization(Consumer<CategorizeProgress> progressCallback) {
        return AsyncContext.supplyAsync(() -> {
            List<Task> tasks = com.example.neuroflowplanner.db.DatabaseManager.getInstance().loadAllTasks();
            List<Task> tasksToProcess = new ArrayList<>();
            for (Task task : flattenTasks(tasks)) {
                if (needsAutoTagging(task)) {
                    tasksToProcess.add(task);
                }
            }
            int total = tasksToProcess.size();
            if (total == 0) {
                if (progressCallback != null) {
                    progressCallback.accept(new CategorizeProgress(0, 0, 0, 0, -1));
                }
                return new CategorizeResult(0, 0, "Нет задач без тегов для обработки.");
            }

            if (progressCallback != null) {
                progressCallback.accept(new CategorizeProgress(0, total, 0, 0, -1));
            }

            long startNs = System.nanoTime();
            java.util.concurrent.atomic.AtomicInteger processed = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger updated = new java.util.concurrent.atomic.AtomicInteger();
            List<Task> localTagUpdates = new ArrayList<>(writeChunkSize);
            List<Task> aiBatchBuffer = new ArrayList<>(AI_BATCH_SIZE);
            List<CompletableFuture<Void>> inFlight = new ArrayList<>();

            // 1. Попытка быстрой локальной категоризации
            for (Task task : tasksToProcess) {
                if (applyLocalTagIfConfident(task)) {
                    localTagUpdates.add(task);
                    updated.addAndGet(flushPendingTagUpdatesIfNeeded(localTagUpdates, "categorize.local"));
                    int done = processed.incrementAndGet();
                    if (progressCallback != null) {
                        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                        long etaMs = estimateEtaMillis(elapsedMs, done, total);
                        progressCallback.accept(new CategorizeProgress(done, total, updated.get(), elapsedMs, etaMs));
                    }
                } else {
                    aiBatchBuffer.add(task);
                    if (aiBatchBuffer.size() >= AI_BATCH_SIZE) {
                        submitAiBatch(aiBatchBuffer, inFlight, processed, updated, total, startNs, progressCallback);
                    }
                }
            }
            updated.addAndGet(flushPendingTagUpdates(localTagUpdates, "categorize.local.final"));

            submitAiBatch(aiBatchBuffer, inFlight, processed, updated, total, startNs, progressCallback);
            awaitAllBatches(inFlight);

            int processedCount = processed.get();
            int updatedCount = updated.get();
            return new CategorizeResult(processedCount, updatedCount,
                String.format("Обработано: %d задач, обновлено: %d", processedCount, updatedCount));
        }, CATEGORIZATION_EXECUTOR);
    }

    private void submitAiBatch(
        List<Task> aiBatchBuffer,
        List<CompletableFuture<Void>> inFlight,
        java.util.concurrent.atomic.AtomicInteger processed,
        java.util.concurrent.atomic.AtomicInteger updated,
        int total,
        long startNs,
        Consumer<CategorizeProgress> progressCallback
    ) {
        if (aiBatchBuffer == null || aiBatchBuffer.isEmpty()) {
            return;
        }
        List<Task> batch = List.copyOf(aiBatchBuffer);
        aiBatchBuffer.clear();
        inFlight.add(AsyncContext.runAsync(() -> processAiBatch(batch, processed, updated, total, startNs, progressCallback), AI_REQUEST_EXECUTOR));
        if (inFlight.size() >= maxInFlightAiBatches) {
            awaitAnyBatchCompletion(inFlight);
        }
    }

    private void processAiBatch(
        List<Task> batch,
        java.util.concurrent.atomic.AtomicInteger processed,
        java.util.concurrent.atomic.AtomicInteger updated,
        int total,
        long startNs,
        Consumer<CategorizeProgress> progressCallback
    ) {
        try {
            Map<String, String> results = requestAIBatchCategorization(batch);
            List<Task> updatedTasks = new ArrayList<>(batch.size());
            for (Task task : batch) {
                String tags = results.get(task.getId());
                if (tags == null || tags.isBlank()) {
                    continue;
                }
                String normalized = normalizeAiTags(tags);
                if (normalized == null || normalized.isBlank()) {
                    continue;
                }
                String existingTags = task.getTags() != null ? task.getTags() : "";
                String merged = mergeTags(existingTags, normalized);
                if (merged.isBlank() || merged.equals(existingTags)) {
                    continue;
                }
                task.setTags(merged);
                updatedTasks.add(task);
            }
            if (!updatedTasks.isEmpty()) {
                updated.addAndGet(persistTagUpdates(updatedTasks, "categorize.ai.batch"));
            }
        } catch (Throwable t) {
            LOG.error(
                "smart.categorization.ai.batch.failed",
                ErrorCode.AI_REQUEST_FAILED,
                t,
                "operation", "requestAIBatchCategorization",
                "batchSize", batch.size(),
                "firstTaskId", batch.isEmpty() ? "" : batch.get(0).getId(),
                "model", resolveActiveModel()
            );
        } finally {
            int done = processed.addAndGet(batch.size());
            if (progressCallback != null) {
                long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
                long etaMs = estimateEtaMillis(elapsedMs, done, total);
                progressCallback.accept(new CategorizeProgress(done, total, updated.get(), elapsedMs, etaMs));
            }
        }
    }

    private void awaitAnyBatchCompletion(List<CompletableFuture<Void>> inFlight) {
        if (inFlight.isEmpty()) {
            return;
        }
        CompletableFuture.anyOf(inFlight.toArray(new CompletableFuture[0])).join();
        inFlight.removeIf(CompletableFuture::isDone);
    }

    private void awaitAllBatches(List<CompletableFuture<Void>> inFlight) {
        if (inFlight.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(inFlight.toArray(new CompletableFuture[0])).join();
        inFlight.clear();
    }

    private List<Task> flattenTasks(List<Task> tasks) {
        List<Task> all = new ArrayList<>();
        for (Task task : tasks) {
            all.add(task);
            if (task.hasSubtasks()) {
                all.addAll(flattenTasks(task.getSubtasks()));
            }
        }
        return all;
    }
    
    /** Запрос к ИИ для категоризации пачки задач. */
    private Map<String, String> requestAIBatchCategorization(List<Task> tasks) {
        if (tasks.isEmpty()) return Map.of();

        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return Map.of(); // В офлайн-режиме AI недоступен
        }

        StringBuilder tasksJson = new StringBuilder("[\n");
        for (Task t : tasks) {
            tasksJson.append(String.format("  {\"id\": \"%s\", \"title\": \"%s\", \"desc\": \"%s\"},\n",
                t.getId(),
                escapeJson(t.getTitle()),
                t.getDescription() != null ? escapeJson(t.getDescription()) : ""));
        }
        if (tasksJson.length() > 2) tasksJson.setLength(tasksJson.length() - 2); // remove last comma
        tasksJson.append("\n]");

        String prompt = """
            Проанализируй список задач и предложи теги для каждой.
            Задачи:
            %s
            
            Выбирай теги из: Работа, Учёба, Личное, Финансы, Срочное, Идеи, Коммуникации, Здоровье, Покупки.
            Или предложи свой короткий тег.
            
            Верни ТОЛЬКО валидный JSON объект (Map), где ключ - id задачи, значение - теги через запятую.
            Пример:
            {
              "uuid-1": "работа, проект",
              "uuid-2": "личное, покупки"
            }
            """.formatted(tasksJson.toString());

        String systemPrompt = "Ты API классификатор задач. Ты возвращаешь ТОЛЬКО JSON объект с тегами. Никакого текста до или после JSON.";

        try {
            AiRequestOptions options = AiRequestOptions.builder()
                .model(aiClient.getDefaultModel())
                .systemPrompt(systemPrompt)
                .temperature(0.3) // Более детерминированные ответы для классификации
                .build();

            AiResponse response = aiClient.sendChatMessage(prompt, options).join();
            if (response.success() && response.content() != null) {
                return parseJsonMap(response.content());
            } else {
                LOG.warning(
                    "smart.categorization.ai.response.error",
                    ErrorCode.AI_REQUEST_FAILED,
                    "operation", "sendChatMessage",
                    "model", aiClient.getDefaultModel(),
                    "batchSize", tasks.size(),
                    "aiErrorCode", response.errorCode(),
                    "aiErrorMessage", response.errorMessage()
                );
            }
        } catch (Exception e) {
            LOG.error(
                "smart.categorization.ai.request.exception",
                ErrorCode.AI_REQUEST_FAILED,
                e,
                "operation", "sendChatMessage",
                "model", aiClient.getDefaultModel(),
                "batchSize", tasks.size()
            );
        }
        return Map.of();
    }
    
    /** Parse AI JSON map response to taskId->tags mapping. */
    private Map<String, String> parseJsonMap(String json) {
        Map<String, String> result = new HashMap<>();
        if (json == null || json.isBlank()) {
            return result;
        }

        String cleaned = stripMarkdownJsonFences(json);
        ObjectMapper mapper = AiObjectMapperFactory.providerResponseMapper();
        try {
            JsonNode root = mapper.readTree(cleaned);
            if (!root.isObject()) {
                return result;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                if (key == null || key.isBlank() || value == null || value.isNull()) {
                    continue;
                }
                result.put(key, value.isTextual() ? value.asText() : value.toString());
            }
        } catch (Exception e) {
            LOG.warning(
                "smart.categorization.ai.response.parse.failed",
                ErrorCode.AI_RESPONSE_INVALID,
                "operation", "parseJsonMap",
                "responseLength", cleaned.length()
            );
        }

        return result;
    }

    private String stripMarkdownJsonFences(String payload) {
        if (payload == null) {
            return null;
        }
        String cleaned = payload.trim();
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }
        return cleaned.trim();
    }

    /** Синхронный запрос к ИИ для категоризации одной задачи (Legacy/Fallback). */
    private String requestAICategorizationSync(Task task) {
        AiClient aiClient = AiClientFactory.getInstance().getActiveClient();
        if (aiClient.getMode() == AiMode.OFFLINE) {
            return null; // В офлайн-режиме AI недоступен
        }

        String prompt = """
            Проанализируй задачу и предложи 1-3 подходящих тега/категории на русском языке.
            Выбирай из: работа, учёба, личное, финансы, срочное, идеи, коммуникации, здоровье, дом, проект.
            Или предложи свой короткий тег если ничего не подходит.
            
            Задача: %s
            Описание: %s
            
            Ответь ТОЛЬКО тегами через запятую, без пояснений. Пример: работа, проект
            """.formatted(
                task.getTitle(),
                task.getDescription() != null && !task.getDescription().isEmpty() 
                    ? task.getDescription() : "нет описания"
            );

        String systemPrompt = "Ты помощник по категоризации задач. Отвечай только тегами через запятую.";

        try {
            AiRequestOptions options = AiRequestOptions.builder()
                .model(aiClient.getDefaultModel())
                .systemPrompt(systemPrompt)
                .temperature(0.3)
                .build();

            AiResponse response = aiClient.sendChatMessage(prompt, options).join();
            if (response.success() && response.content() != null) {
                return response.content().replace("\n", " ").trim();
            }
        } catch (Exception e) {
            // Вернём null, чтобы использовать fallback
        }
        return null;
    }

    /** Объединить существующие и новые теги без дубликатов. */
    private String mergeTags(String existing, String newTags) {
        Set<String> tags = new LinkedHashSet<>();
        addTags(tags, existing);
        addTags(tags, newTags);
        if (tags.size() > 1) {
            tags.remove(FALLBACK_TAG);
        }
        return String.join(", ", tags);
    }

    private boolean needsAutoTagging(Task task) {
        String tags = task.getTags();
        if (tags == null || tags.isBlank()) {
            return true;
        }
        return isFallbackOnly(tags);
    }

    private boolean isFallbackOnly(String tags) {
        Set<String> parsed = new LinkedHashSet<>();
        addTags(parsed, tags);
        return !parsed.isEmpty() && parsed.size() == 1 && parsed.contains(FALLBACK_TAG);
    }

    private boolean applyLocalTagIfConfident(Task task) {
        String suggested = suggestTag(task);
        if (suggested == null || suggested.isBlank()) {
            return false;
        }
        String existingTags = task.getTags() != null ? task.getTags() : "";
        String merged = mergeTags(existingTags, suggested);
        if (merged.isBlank() || merged.equals(existingTags)) {
            return false;
        }
        task.setTags(merged);
        return true;
    }

    private boolean applyAiTags(Task task) {
        String aiTags = normalizeAiTags(requestAICategorizationSync(task));
        if (aiTags == null || aiTags.isEmpty()) {
            return false;
        }
        String existingTags = task.getTags() != null ? task.getTags() : "";
        String merged = mergeTags(existingTags, aiTags);
        if (merged.isBlank() || merged.equals(existingTags)) {
            return false;
        }
        task.setTags(merged);
        persistTagUpdates(List.of(task), "categorize.ai.single");
        return true;
    }

    private int flushPendingTagUpdatesIfNeeded(List<Task> pendingUpdates, String operation) {
        if (pendingUpdates == null || pendingUpdates.size() < writeChunkSize) {
            return 0;
        }
        return flushPendingTagUpdates(pendingUpdates, operation);
    }

    private int flushPendingTagUpdates(List<Task> pendingUpdates, String operation) {
        if (pendingUpdates == null || pendingUpdates.isEmpty()) {
            return 0;
        }
        int persisted = persistTagUpdates(pendingUpdates, operation);
        pendingUpdates.clear();
        return persisted;
    }

    private int persistTagUpdates(List<Task> updatedTasks, String operation) {
        if (updatedTasks == null || updatedTasks.isEmpty()) {
            return 0;
        }
        Map<String, String> tagsByTaskId = new LinkedHashMap<>();
        for (Task task : updatedTasks) {
            if (task == null || task.getId() == null || task.getId().isBlank()) {
                continue;
            }
            tagsByTaskId.put(task.getId(), normalizeTagsSnapshot(task.getTags()));
        }
        if (tagsByTaskId.isEmpty()) {
            return 0;
        }

        synchronized (DB_SAVE_LOCK) {
            TaskBulkOperationResult result = taskApplicationService().updateTaskTagsBulk(tagsByTaskId);
            if (!result.isSuccessful()) {
                throw new IllegalStateException("Task tag bulk update failed for operation " + operation);
            }
            LOG.info(
                "smart.categorization.bulk.flush.completed",
                "operation", operation,
                "chunkSize", tagsByTaskId.size(),
                "updatedCount", result.updatedCount(),
                "batchCount", result.batchCount()
            );
            return result.updatedCount();
        }
    }

    private String normalizeTagsSnapshot(String tags) {
        return tags == null ? "" : tags.trim();
    }

    private String normalizeAiTags(String aiTags) {
        if (aiTags == null || aiTags.isBlank()) {
            return null;
        }
        Set<String> tags = new LinkedHashSet<>();
        addTags(tags, aiTags);
        if (tags.isEmpty()) {
            addTagsFromText(tags, aiTags);
        }
        if (tags.isEmpty()) {
            return null;
        }
        if (tags.size() == 1 && tags.contains(FALLBACK_TAG)) {
            return null;
        }
        if (tags.size() > 1) {
            tags.remove(FALLBACK_TAG);
        }
        return String.join(", ", tags);
    }

    private void addTagsFromText(Set<String> tags, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String lower = text.toLowerCase();
        for (Category cat : CATEGORIES) {
            String catName = cat.name().toLowerCase();
            if (lower.contains(catName)) {
                tags.add(catName);
                continue;
            }
            for (String kw : cat.keywords()) {
                if (lower.contains(kw)) {
                    tags.add(catName);
                    break;
                }
            }
        }
    }

    private void addTags(Set<String> tags, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String cleaned = raw.replace("```", "");
        cleaned = cleaned.replace("\r", "\n");
        cleaned = cleaned.replaceAll("(?i)теги\\s*:", " ");
        cleaned = cleaned.replaceAll("(?i)категории\\s*:", " ");
        cleaned = cleaned.replaceAll("[\\t ]+", " ").trim();

        String[] parts = cleaned.split("[,;\\n]+");
        for (String part : parts) {
            String tag = part.trim().toLowerCase();
            tag = tag.replaceAll("^[\\-•\\d).\\s]+", "");
            tag = tag.replace("\"", "");
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() >= 30) { // Защита от мусора
                continue;
            }
            if ("нет".equals(tag) || "нет тегов".equals(tag) || "нет тега".equals(tag)) {
                continue;
            }
            tags.add(tag);
        }
    }

    private String resolveActiveModel() {
        try {
            return AiClientFactory.getInstance().getActiveClient().getDefaultModel();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private long estimateEtaMillis(long elapsedMs, int processed, int total) {
        if (processed <= 0 || total <= 0) {
            return -1;
        }
        int remaining = total - processed;
        if (remaining <= 0) {
            return 0;
        }
        double perItem = (double) elapsedMs / processed;
        return Math.round(perItem * remaining);
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Вернуть свежий список всех задач (для перерендера после авто-распределения). */
    public List<Task> getAllTasks() {
        return com.example.neuroflowplanner.db.DatabaseManager.getInstance().loadAllTasks();
    }

    /** Результат ИИ-категоризации. */
    public record CategorizeResult(int processed, int updated, String message) {}
}
