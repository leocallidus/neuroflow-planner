package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.util.ConfigManager;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmartCategorizationService {

    private final String API_URL;
    private final String MODEL;
    private final String API_KEY;
    private final HttpClient client;
    private volatile Boolean aiAvailable = null;

    public SmartCategorizationService() {
        String url = ConfigManager.getProperty("api.url");
        String model = ConfigManager.getProperty("api.model");
        String key = ConfigManager.getProperty("api.key");
        API_URL = url != null ? url : "http://localhost:11434/api/chat";
        MODEL = model != null ? model : "llama3";
        API_KEY = key;
        client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public record Category(String name, String icon, String color, List<String> keywords) {}

    private static final List<Category> CATEGORIES = List.of(
        new Category("Работа", "💼", "blue", List.of("работа", "проект", "клиент", "отчет", "презентация", "митинг", "встреча", "совещание", "код", "разработка", "тест", "релиз", "деплой", "баг", "фикс", "review", "pr", "merge", "sprint", "задача", "jira", "task")),
        new Category("Учёба", "📚", "mauve", List.of("учеба", "курс", "лекция", "экзамен", "домашка", "homework", "study", "книга", "читать", "конспект", "семинар", "диплом", "курсовая", "реферат", "зачет")),
        new Category("Личное", "🏠", "green", List.of("дом", "квартира", "уборка", "покупки", "магазин", "врач", "здоровье", "спорт", "тренировка", "отдых", "семья", "друзья", "хобби", "личное")),
        new Category("Финансы", "💰", "yellow", List.of("оплата", "счет", "налог", "банк", "деньги", "бюджет", "зарплата", "инвестиции", "кредит", "долг", "финансы", "платеж")),
        new Category("Срочное", "🔥", "red", List.of("срочно", "asap", "важно", "критично", "дедлайн", "горит", "немедленно", "urgent", "critical")),
        new Category("Идеи", "💡", "peach", List.of("идея", "план", "подумать", "исследовать", "изучить", "попробовать", "эксперимент", "прототип")),
        new Category("Коммуникации", "📧", "sapphire", List.of("письмо", "email", "звонок", "позвонить", "написать", "ответить", "связаться", "сообщение", "чат"))
    );

    public record CategorizedTask(Task task, Category category, double confidence) {}

    /** Динамическая категоризация на основе реальных тегов задач. */
    public Map<String, List<CategorizedTask>> categorize(List<Task> tasks) {
        Map<String, List<CategorizedTask>> result = new LinkedHashMap<>();
        
        for (Task task : tasks) {
            String tags = task.getTags();
            if (tags == null || tags.isBlank()) {
                // Задачи без тегов — в "Без категории"
                String catName = "Без категории";
                Category cat = new Category(catName, "📋", "overlay1", List.of());
                result.computeIfAbsent(catName, k -> new ArrayList<>())
                      .add(new CategorizedTask(task, cat, 0.0));
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
            icon = "💪"; color = "teal";
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
        String text = (task.getTitle() + " " + task.getDescription() + " " + task.getTags()).toLowerCase();
        
        Category best = null;
        int maxMatches = 0;

        for (Category cat : CATEGORIES) {
            int matches = 0;
            for (String kw : cat.keywords()) {
                if (Pattern.compile("\\b" + Pattern.quote(kw) + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find()) {
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

        double confidence = Math.min(1.0, maxMatches / 3.0);
        return new CategorizedTask(task, best, confidence);
    }

    public String suggestTag(Task task) {
        CategorizedTask ct = detectCategory(task);
        if (ct.confidence() > 0.3) {
            return ct.category().name().toLowerCase();
        }
        return null;
    }

    public void autoTagTask(Task task) {
        String suggested = suggestTag(task);
        if (suggested != null && !task.getTags().toLowerCase().contains(suggested)) {
            String tags = task.getTags();
            task.setTags(tags.isEmpty() ? suggested : tags + ", " + suggested);
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
            // Быстрая проверка - просто пингуем базовый URL
            String baseUrl = API_URL.replace("/api/chat", "");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            aiAvailable = response.statusCode() == 200;
        } catch (Exception e) {
            aiAvailable = false;
        }
        return aiAvailable;
    }

    /** Асинхронная проверка доступности ИИ с таймаутом. */
    public CompletableFuture<Boolean> checkAIAvailabilityAsync() {
        return CompletableFuture.supplyAsync(this::checkAIAvailability)
            .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .exceptionally(ex -> {
                aiAvailable = false;
                return false;
            });
    }

    /** Автоматически распределить все задачи по тегам/категориям и сохранить (локальная логика). */
    public void categorizeAndSave() {
        List<Task> all = com.example.neuroflowplanner.db.DatabaseManager.getInstance().loadAllTasks();
        for (Task task : all) {
            autoTagTask(task);
            com.example.neuroflowplanner.db.DatabaseManager.getInstance().saveTask(task);
        }
    }

    /** ИИ-распределение всех задач по категориям и тегам с сохранением в БД. */
    public CompletableFuture<CategorizeResult> categorizeAllWithAI() {
        return CompletableFuture.supplyAsync(() -> {
            List<Task> tasks = com.example.neuroflowplanner.db.DatabaseManager.getInstance().loadAllTasks();
            if (tasks.isEmpty()) {
                return new CategorizeResult(0, 0, "Нет задач для распределения.");
            }

            int processed = 0;
            int updated = 0;

            for (Task task : tasks) {
                try {
                    String aiTags = requestAICategorizationSync(task);
                    if (aiTags != null && !aiTags.isEmpty()) {
                        String existingTags = task.getTags() != null ? task.getTags() : "";
                        String newTags = mergeTags(existingTags, aiTags);
                        if (!newTags.equals(existingTags)) {
                            task.setTags(newTags);
                            com.example.neuroflowplanner.db.DatabaseManager.getInstance().saveTask(task);
                            updated++;
                        }
                    } else {
                        // Fallback на локальную логику
                        String oldTags = task.getTags();
                        autoTagTask(task);
                        if (!Objects.equals(oldTags, task.getTags())) {
                            com.example.neuroflowplanner.db.DatabaseManager.getInstance().saveTask(task);
                            updated++;
                        }
                    }
                    processed++;
                } catch (Exception e) {
                    // Fallback на локальную логику при ошибке
                    String oldTags = task.getTags();
                    autoTagTask(task);
                    if (!Objects.equals(oldTags, task.getTags())) {
                        com.example.neuroflowplanner.db.DatabaseManager.getInstance().saveTask(task);
                        updated++;
                    }
                    processed++;
                }
            }

            return new CategorizeResult(processed, updated, 
                String.format("Обработано: %d задач, обновлено: %d", processed, updated));
        });
    }

    /** Синхронный запрос к ИИ для категоризации одной задачи. */
    private String requestAICategorizationSync(Task task) {
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

        String json = """
            {
                "model": "%s",
                "messages": [
                    {"role": "system", "content": "Ты помощник по категоризации задач. Отвечай только тегами через запятую."},
                    {"role": "user", "content": "%s"}
                ],
                "stream": false
            }
            """.formatted(MODEL, escapeJson(prompt));

        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30));
            
            if (API_KEY != null && !API_KEY.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + API_KEY);
            }
            
            HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return extractContent(response.body());
            }
        } catch (Exception e) {
            // Вернём null, чтобы использовать fallback
        }
        return null;
    }

    /** Объединить существующие и новые теги без дубликатов. */
    private String mergeTags(String existing, String newTags) {
        Set<String> tags = new LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String t : existing.split(",")) {
                tags.add(t.trim().toLowerCase());
            }
        }
        if (newTags != null && !newTags.isBlank()) {
            for (String t : newTags.split(",")) {
                String tag = t.trim().toLowerCase();
                if (!tag.isEmpty() && tag.length() < 30) { // Защита от мусора
                    tags.add(tag);
                }
            }
        }
        return String.join(", ", tags);
    }

    private String extractContent(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1) return null;
        int start = json.indexOf("\"", idx + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
            end++;
        }
        if (start >= end) return null;
        return json.substring(start, end).replace("\\n", " ").replace("\\\"", "\"").trim();
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
