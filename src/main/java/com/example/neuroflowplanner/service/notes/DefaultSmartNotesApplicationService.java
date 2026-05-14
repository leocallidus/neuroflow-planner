package com.example.neuroflowplanner.service.notes;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.service.NotesService;
import com.example.neuroflowplanner.util.LinkParser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public class DefaultSmartNotesApplicationService implements SmartNotesApplicationService {
    private final NotesService notesService;

    public DefaultSmartNotesApplicationService() {
        this(NotesService.getInstance());
    }

    DefaultSmartNotesApplicationService(NotesService notesService) {
        this.notesService = notesService;
    }

    @Override
    public List<String> listTitles() {
        return new ArrayList<>(notesService.getAllNoteTitles());
    }

    @Override
    public List<String> searchTitles(String query) {
        List<String> allTitles = notesService.getAllNoteTitles();
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            return new ArrayList<>(allTitles);
        }

        List<String> filtered = new ArrayList<>();
        for (String title : allTitles) {
            if (title != null && title.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filtered.add(title);
                continue;
            }
            String content = notesService.loadNoteContent(title);
            if (content != null && content.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filtered.add(title);
            }
        }
        return filtered;
    }

    @Override
    public String loadContent(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String content = notesService.loadNoteContent(title);
        return content == null ? "" : content;
    }

    @Override
    public SaveResult saveCurrent(String currentTitle, String editedTitle, String content) {
        String normalizedContent = content == null ? "" : content;
        String candidateTitle = sanitizeTitle(editedTitle);
        if (candidateTitle.isBlank()) {
            candidateTitle = "Untitled";
        }

        if (currentTitle == null || currentTitle.isBlank()) {
            String created = ensureUniqueTitle(candidateTitle, null);
            notesService.saveNote(created, normalizedContent);
            return new SaveResult(created, normalizedContent, false);
        }

        String safeCurrentTitle = sanitizeTitle(currentTitle);
        if (safeCurrentTitle.isBlank()) {
            safeCurrentTitle = currentTitle.trim();
        }

        if (candidateTitle.equals(safeCurrentTitle)) {
            notesService.saveNote(safeCurrentTitle, normalizedContent);
            return new SaveResult(safeCurrentTitle, normalizedContent, false);
        }

        String renamedTitle = ensureUniqueTitle(candidateTitle, safeCurrentTitle);
        notesService.renameNote(safeCurrentTitle, renamedTitle);
        notesService.saveNote(renamedTitle, normalizedContent);
        return new SaveResult(renamedTitle, normalizedContent, true);
    }

    @Override
    public String createNewNote() {
        return createNoteWithTitle("Новая заметка");
    }

    @Override
    public String createNoteWithTitle(String title) {
        String safeTitle = ensureUniqueTitle(sanitizeTitle(title), null);
        notesService.saveNote(safeTitle, "");
        return safeTitle;
    }

    @Override
    public String createFromTemplate(String templateKey) {
        Template template = parseTemplate(templateKey);
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        String titleDate = date.replace('.', '-');

        String baseTitle = switch (template) {
            case DIARY -> "Дневник " + titleDate;
            case RETROSPECTIVE -> "Ретроспектива " + titleDate;
            case PLANS -> "Планы " + titleDate;
            case MEETING -> "Встреча " + titleDate;
            case ONE_ON_ONE -> "1-on-1 " + titleDate;
            case STATUS_REPORT -> "Отчет о статусе " + titleDate;
            case POSTMORTEM -> "Постмортем " + titleDate;
            case SPEC -> "Требования " + titleDate;
            case IDEAS -> "Идеи " + titleDate;
            case LEARNING_PLAN -> "Учебный план " + titleDate;
            case WEEKLY_REFLECTION -> "Рефлексия недели " + titleDate;
            case PROJECT_PLAN -> "План проекта " + titleDate;
            case SHOPPING -> "Покупки " + titleDate;
            case RESEARCH -> "Исследование " + titleDate;
            case HABITS -> "Трекер привычек " + titleDate;
            case REVIEW -> "Ревью " + titleDate;
            case TRAVEL -> "План путешествия " + titleDate;
            case OKR -> "OKR " + titleDate;
        };

        String title = ensureUniqueTitle(baseTitle, null);
        String templateContent = buildTemplateContent(template, date);
        notesService.saveNote(title, templateContent);
        return title;
    }

    @Override
    public void deleteNote(String title) {
        if (title == null || title.isBlank()) {
            return;
        }
        notesService.deleteNote(title);
    }

    @Override
    public String resolveExistingTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.trim();
        List<String> titles = notesService.getAllNoteTitles();
        for (String noteTitle : titles) {
            if (noteTitle.equalsIgnoreCase(trimmed)) {
                return noteTitle;
            }
        }

        String sanitized = sanitizeTitle(trimmed);
        if (!sanitized.equalsIgnoreCase(trimmed)) {
            for (String noteTitle : titles) {
                if (noteTitle.equalsIgnoreCase(sanitized)) {
                    return noteTitle;
                }
            }
        }

        return null;
    }

    @Override
    public List<LinkChip> outgoingLinks(String content, Function<String, Task> taskResolver) {
        List<LinkChip> links = new ArrayList<>();
        for (LinkParser.LinkTarget link : LinkParser.extractLinks(content)) {
            if (link.getType() == LinkParser.LinkType.NOTE) {
                String resolved = resolveExistingTitle(link.getTarget());
                boolean exists = resolved != null;
                String label = exists ? resolved : link.getTarget();
                String target = exists ? resolved : link.getTarget();
                links.add(new LinkChip(label, target, LinkType.NOTE, exists));
                continue;
            }

            Task task = resolveTask(taskResolver, link.getTarget());
            String label = task != null ? task.getTitle() : link.getTarget();
            String target = task != null ? task.getId() : link.getTarget();
            links.add(new LinkChip(label, target, LinkType.TASK, task != null));
        }
        return links;
    }

    @Override
    public List<LinkChip> incomingLinks(String noteTitle, Supplier<List<Task>> taskProvider) {
        List<LinkChip> incoming = new ArrayList<>();
        if (noteTitle == null || noteTitle.isBlank()) {
            return incoming;
        }

        String normalizedTarget = noteTitle.trim();
        Set<String> dedupe = new HashSet<>();

        for (String title : notesService.getAllNoteTitles()) {
            if (title.equalsIgnoreCase(normalizedTarget)) {
                continue;
            }
            String content = notesService.loadNoteContent(title);
            if (isNoteReferenced(content, normalizedTarget)) {
                if (dedupe.add("note:" + title)) {
                    incoming.add(new LinkChip(title, title, LinkType.NOTE, true));
                }
            }
        }

        for (Task task : flattenTasks(taskProvider)) {
            String description = task.getDescription();
            if (isNoteReferenced(description, normalizedTarget)) {
                String taskId = task.getId() == null ? "" : task.getId();
                if (dedupe.add("task:" + taskId)) {
                    incoming.add(new LinkChip(task.getTitle(), taskId, LinkType.TASK, true));
                }
            }
        }

        return incoming;
    }

    @Override
    public NotesSnapshot captureSnapshot() {
        List<NoteSnapshot> notes = new ArrayList<>();
        for (String title : listTitles()) {
            if (title == null || title.isBlank()) {
                continue;
            }
            notes.add(new NoteSnapshot(title, loadContent(title)));
        }
        return new NotesSnapshot(notes);
    }

    @Override
    public void restoreSnapshot(NotesSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        Map<String, String> targetByTitle = new LinkedHashMap<>();
        for (NoteSnapshot note : snapshot.notes()) {
            if (note == null || note.title() == null || note.title().isBlank()) {
                continue;
            }
            targetByTitle.put(note.title(), note.content() == null ? "" : note.content());
        }

        Set<String> existingTitles = new LinkedHashSet<>(listTitles());
        Set<String> targetTitles = targetByTitle.keySet();
        for (String existingTitle : existingTitles) {
            if (!targetTitles.contains(existingTitle)) {
                notesService.deleteNote(existingTitle);
            }
        }

        for (Map.Entry<String, String> entry : targetByTitle.entrySet()) {
            notesService.saveNote(entry.getKey(), entry.getValue());
        }
    }

    private Task resolveTask(Function<String, Task> resolver, String token) {
        if (resolver == null || token == null || token.isBlank()) {
            return null;
        }
        return resolver.apply(token.trim());
    }

    private List<Task> flattenTasks(Supplier<List<Task>> provider) {
        List<Task> result = new ArrayList<>();
        if (provider == null) {
            return result;
        }

        List<Task> topLevel = provider.get();
        if (topLevel == null) {
            return result;
        }

        for (Task task : topLevel) {
            collectTaskRecursive(task, result);
        }
        return result;
    }

    private void collectTaskRecursive(Task task, List<Task> sink) {
        if (task == null) {
            return;
        }
        sink.add(task);
        for (Task subtask : task.getSubtasks()) {
            collectTaskRecursive(subtask, sink);
        }
    }

    private boolean isNoteReferenced(String content, String noteTitle) {
        for (LinkParser.LinkTarget link : LinkParser.extractLinks(content)) {
            if (link.getType() == LinkParser.LinkType.NOTE && LinkParser.matchesNoteTarget(link.getTarget(), noteTitle)) {
                return true;
            }
        }
        return false;
    }

    private String ensureUniqueTitle(String baseName, String allowSameTitle) {
        String safeBase = sanitizeTitle(baseName);
        if (safeBase.isBlank()) {
            safeBase = "Untitled";
        }

        String current = safeBase;
        int index = 1;
        while (titleExists(current) && !current.equalsIgnoreCase(allowSameTitle == null ? "" : allowSameTitle)) {
            current = safeBase + " " + index++;
        }
        return current;
    }

    private boolean titleExists(String title) {
        for (String existing : notesService.getAllNoteTitles()) {
            if (existing.equalsIgnoreCase(title)) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Untitled";
        }
        String safe = title.replaceAll("[^a-zA-Z0-9а-яА-Я _-]", "").trim();
        return safe.isEmpty() ? "Untitled" : safe;
    }

    private String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private Template parseTemplate(String key) {
        if (key == null || key.isBlank()) {
            return Template.DIARY;
        }
        try {
            return Template.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Template.DIARY;
        }
    }

    private String buildTemplateContent(Template template, String date) {
        return switch (template) {
            case DIARY -> """
                # Дневник — %s

                ## Настроение
                - 

                ## Что сделал(а)
                - 

                ## Что было сложным
                - 

                ## Итог дня
                - 
                """.formatted(date);
            case RETROSPECTIVE -> """
                # Ретроспектива — %s

                ## Что прошло хорошо
                - 

                ## Что можно улучшить
                - 

                ## Что стоит попробовать дальше
                - 

                ## Благодарности
                - 
                """.formatted(date);
            case PLANS -> """
                # Планы — %s

                ## Цели
                - 

                ## Основные задачи
                - [ ] 

                ## Приоритеты
                1. 
                2. 

                ## Риски
                - 

                ## Следующие шаги
                - 
                """.formatted(date);
            case MEETING -> """
                # Встреча — %s

                ## Участники
                - 

                ## Повестка
                - 

                ## Ключевые решения
                - 

                ## Экшены
                - [ ] 

                ## Риски/блокеры
                - 
                """.formatted(date);
            case ONE_ON_ONE -> """
                # 1-on-1 — %s

                ## Повестка
                - 

                ## Прогресс
                - 

                ## Проблемы/препятствия
                - 

                ## Обратная связь
                - 

                ## Договоренности
                - 
                """.formatted(date);
            case STATUS_REPORT -> """
                # Отчет о статусе — %s

                ## Итоги периода
                - 

                ## Достижения
                - 

                ## Метрики
                - 

                ## Блокеры
                - 

                ## План на следующий период
                - 
                """.formatted(date);
            case POSTMORTEM -> """
                # Постмортем — %s

                ## Что произошло
                - 

                ## Влияние
                - 

                ## Причины
                - 

                ## Как исправили
                - 

                ## Что предотвратит повтор
                - 

                ## Action items
                - [ ] 
                """.formatted(date);
            case SPEC -> """
                # Требования — %s

                ## Цель
                - 

                ## Пользовательские сценарии
                - 

                ## Требования
                - 

                ## Ограничения
                - 

                ## Принятое решение
                - 
                """.formatted(date);
            case IDEAS -> """
                # Идеи — %s

                ## Тема
                - 

                ## Идеи
                - 

                ## Лучшие кандидаты
                - 

                ## Следующие шаги
                - 
                """.formatted(date);
            case LEARNING_PLAN -> """
                # Учебный план — %s

                ## Цель обучения
                - 

                ## Темы/модули
                - [ ] 

                ## Ресурсы
                - 

                ## Дедлайны
                - 

                ## Прогресс
                - 
                """.formatted(date);
            case WEEKLY_REFLECTION -> """
                # Рефлексия недели — %s

                ## Что порадовало
                - 

                ## Что было сложно
                - 

                ## Чему научился(ась)
                - 

                ## Что улучшить
                - 

                ## Маленькие победы
                - 
                """.formatted(date);
            case PROJECT_PLAN -> """
                # План проекта — %s

                ## Цели проекта
                - 

                ## Вехи
                - 

                ## Задачи по этапам
                - [ ] 

                ## Риски
                - 

                ## Ответственные
                - 
                """.formatted(date);
            case SHOPPING -> """
                # Покупки/поручения — %s

                ## Срочные
                - [ ] 

                ## На ближайшие дни
                - [ ] 

                ## Когда-нибудь
                - [ ] 
                """.formatted(date);
            case RESEARCH -> """
                # Исследование — %s

                ## Вопрос исследования
                - 

                ## Гипотезы
                - 

                ## Наблюдения
                - 

                ## Выводы
                - 

                ## Источники/ссылки
                - 
                """.formatted(date);
            case HABITS -> """
                # Трекер привычек — %s

                ## Цель
                - 

                ## Привычки
                - [ ] 

                ## Итоги недели
                - 

                ## Награда/мотивация
                - 
                """.formatted(date);
            case REVIEW -> """
                # Ревью книги/курса — %s

                ## Название
                - 

                ## Главные идеи
                - 

                ## Цитаты
                - 

                ## Практические выводы
                - 

                ## Что применю
                - 
                """.formatted(date);
            case TRAVEL -> """
                # План путешествия — %s

                ## Даты
                - 

                ## Маршрут
                - 

                ## Билеты/бронь
                - 

                ## Бюджет
                - 

                ## Чеклист вещей
                - [ ] 
                """.formatted(date);
            case OKR -> """
                # OKR — %s

                ## Objective
                - 

                ## Key Results
                - [ ] 

                ## Инициативы
                - 

                ## Прогресс
                - 
                """.formatted(date);
        };
    }

    private enum Template {
        DIARY,
        RETROSPECTIVE,
        PLANS,
        MEETING,
        ONE_ON_ONE,
        STATUS_REPORT,
        POSTMORTEM,
        SPEC,
        IDEAS,
        LEARNING_PLAN,
        WEEKLY_REFLECTION,
        PROJECT_PLAN,
        SHOPPING,
        RESEARCH,
        HABITS,
        REVIEW,
        TRAVEL,
        OKR
    }
}
