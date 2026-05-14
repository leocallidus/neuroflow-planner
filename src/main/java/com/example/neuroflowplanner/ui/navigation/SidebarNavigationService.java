package com.example.neuroflowplanner.ui.navigation;

import com.example.neuroflowplanner.util.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Source of truth for sidebar navigation structure, sorting, filtering and persisted state.
 */
public final class SidebarNavigationService {
    private static final Comparator<SidebarNavSection> SECTION_ORDER = Comparator
        .comparingInt(SidebarNavSection::order)
        .thenComparing(SidebarNavSection::label, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(SidebarNavSection::id, String.CASE_INSENSITIVE_ORDER);

    private static final List<SidebarNavSection> DEFAULT_SECTIONS = List.of(
        section("history", "История", 10, SidebarNavZone.QUICK, false, true),
        section("main", "Главное", 20, SidebarNavZone.CORE, true, true),
        section("tools", "Инструменты", 30, SidebarNavZone.CORE, true, true),
        section("analysis", "Аналитика", 40, SidebarNavZone.ADVANCED, true, false),
        section("ai", "ИИ-функции", 50, SidebarNavZone.ADVANCED, true, false),
        section("manage", "Управление", 60, SidebarNavZone.ADVANCED, true, false),
        section("bulk", "Массовые", 70, SidebarNavZone.ADVANCED, true, false),
        section("system", "Система", 80, SidebarNavZone.ADVANCED, true, true)
    );

    private static final List<SidebarNavItem> DEFAULT_ITEMS = List.of(
        item("history.undo", "Undo", "history", 10, "main.history.undo", "mdi:undo", List.of("history", "undo")),
        item("history.redo", "Redo", "history", 20, "main.history.redo", "mdi:redo", List.of("history", "redo")),
        item("history.palette", "Команды", "history", 30, "main.system.commandPalette", "mdi:magnify", List.of("palette", "command")),
        item("history.search", "Поиск", "history", 40, "main.system.globalSearchFocus", "mdi:magnify", List.of("search", "global")),
        item("history.shortcuts", "Горячие клавиши", "history", 50, "main.system.shortcutsHelp", "mdi:help", List.of("shortcuts", "help")),

        item("main.tasks.panel", "Панель задач", "main", 10, "main.task.panel", "mdi:home", List.of("tasks", "panel")),
        item("main.tasks.add", "Добавить задачу", "main", 20, "main.inbox.addTask", "mdi:plus-circle-outline", List.of("tasks", "create")),
        item("main.tasks.addSubtask", "Добавить подзадачу", "main", 30, "main.task.addSubtask", "mdi:plus-box-outline", List.of("tasks", "subtask")),
        item("main.tasks.all", "Все задачи", "main", 40, "main.task.listAll", "mdi:view-list-outline", List.of("tasks", "all")),
        item("main.tasks.scheduled", "В планах", "main", 50, "main.task.filter.scheduled", "mdi:calendar-clock", List.of("tasks", "scheduled")),
        item("main.view.calendar", "Календарь", "main", 60, "main.view.calendar", "mdi:calendar-month", List.of("calendar")),
        item("main.view.kanban", "Канбан-доска", "main", 70, "main.view.kanban", "mdi:view-column", List.of("kanban")),
        item("main.view.gantt", "Диаграмма Ганта", "main", 80, "main.view.gantt", "mdi:chart-gantt", List.of("gantt")),

        item("tools.notes", "Умные заметки", "tools", 10, "main.tools.notes.open", "mdi:note-text-outline", List.of("notes")),
        item("tools.pomodoro", "Помодоро", "tools", 20, "main.tools.pomodoro", "mdi:timer-outline", List.of("pomodoro", "timer")),
        item("tools.timeTracker", "Трекинг времени", "tools", 30, "main.tools.timeTracker", "mdi:timer-sand", List.of("time", "tracking")),
        item("tools.workHours", "Рабочие часы", "tools", 40, "main.tools.workHours", "mdi:clock-time-eight-outline", List.of("hours")),
        item("tools.template.create", "Из шаблона", "tools", 50, "main.tools.template.create", "mdi:file-document-outline", List.of("template")),
        item("tools.template.save", "Сохранить шаблон", "tools", 60, "main.tools.template.save", "mdi:content-save-outline", List.of("template", "save")),
        item("tools.import", "Импорт задач", "tools", 70, "main.tools.importTasks", "mdi:file-import-outline", List.of("import")),

        item("analysis.dashboard", "Дашборд", "analysis", 10, "main.analytics.dashboard", "mdi:view-dashboard", List.of("analytics", "dashboard")),
        item("analysis.dailyReview", "Ежедневный обзор", "analysis", 15, "main.analytics.dailyReview", "mdi:weather-sunny", List.of("analytics", "daily", "review")),
        item("analysis.focusBlocks", "Фокус-блоки", "analysis", 18, "main.analytics.focusBlocks", "mdi:timeline-text-outline", List.of("analytics", "focus", "blocks")),
        item("analysis.planningQuality", "Качество планирования", "analysis", 19, "main.analytics.planningQuality", "mdi:gauge", List.of("analytics", "planning", "quality")),
        item("analysis.statistics", "Статистика", "analysis", 20, "main.analytics.statistics", "mdi:chart-bar", List.of("analytics", "stats")),
        item("analysis.insights", "Персональные инсайты", "analysis", 30, "main.analytics.personalInsights", "mdi:lightbulb-on", List.of("analytics", "insights")),
        item("analysis.goals", "Цели", "analysis", 40, "main.analytics.goals", "mdi:target", List.of("goals")),
        item("analysis.timeStats", "Оценка времени", "analysis", 50, "main.analytics.timeStats", "mdi:clock-outline", List.of("time", "analytics")),
        item("analysis.workload", "Загруженность", "analysis", 60, "main.analytics.workload", "mdi:chart-line", List.of("workload")),
        item("analysis.heatmap", "Тепловая карта", "analysis", 70, "main.analytics.heatmap", "mdi:grid", List.of("heatmap")),
        item("analysis.projectProgress", "Прогресс проектов", "analysis", 80, "main.analytics.projectProgress", "mdi:progress-check", List.of("projects")),

        item("ai.chat", "ИИ-Ассистент", "ai", 10, "main.ai.chat", "mdi:chat", List.of("ai", "chat")),
        item("ai.center", "Центр Анализа", "ai", 20, "main.ai.analyzeCenter", "mdi:brain", List.of("ai", "analysis")),
        item("ai.reminders", "Напоминания", "ai", 30, "main.ai.reminders", "mdi:bell-ring-outline", List.of("ai", "reminders")),
        item("ai.autoPrioritize", "Авто-приоритет", "ai", 40, "main.ai.autoPrioritize", "mdi:priority-high", List.of("ai", "priority")),
        item("ai.autoSchedule", "Авто-планирование", "ai", 50, "main.ai.autoSchedule", "mdi:calendar-sync", List.of("ai", "schedule")),
        item("ai.categorization", "Категоризация", "ai", 60, "main.ai.categorization", "mdi:shape-outline", List.of("ai", "categorization")),

        item("manage.urgent", "Срочные", "manage", 10, "main.task.filter.urgent", "mdi:alert-circle-outline", List.of("filter", "urgent")),
        item("manage.tag", "По тегу...", "manage", 20, "main.task.filter.tag", "mdi:tag-outline", List.of("filter", "tag")),
        item("manage.archive", "В архив", "manage", 30, "main.task.archive.selected", "mdi:archive-outline", List.of("archive")),
        item("manage.showArchive", "Показать архив", "manage", 40, "main.task.archive.show", "mdi:archive", List.of("archive", "view")),
        item("manage.linkDependency", "Добавить зависимость", "manage", 50, "main.task.dependency.link", "mdi:link-variant", List.of("dependency", "link")),
        item("manage.unlinkDependency", "Удалить зависимость", "manage", 60, "main.task.dependency.unlink", "mdi:delete-outline", List.of("dependency", "unlink")),
        item("manage.dependencyDetails", "Связи задачи", "manage", 70, "main.task.dependency.details", "mdi:view-list-outline", List.of("dependency", "details")),

        item("bulk.archive", "Архивировать", "bulk", 10, "main.task.bulk.archive", "mdi:archive-arrow-down", List.of("bulk", "archive")),
        item("bulk.delete", "Удалить", "bulk", 20, "main.task.bulk.delete", "mdi:delete-sweep", List.of("bulk", "delete")),
        item("bulk.tag", "Добавить тег", "bulk", 30, "main.task.bulk.tag", "mdi:tag-plus", List.of("bulk", "tag")),

        item("system.export", "Экспорт", "system", 10, "main.system.export", "mdi:file-export-outline", List.of("system", "export")),
        item("system.settings", "Настройки", "system", 20, "main.system.settings", "mdi:cog-outline", List.of("system", "settings")),
        item("system.help", "Справка", "system", 30, "main.system.help", "mdi:help-circle-outline", List.of("system", "help"))
    );

    public List<SidebarNavSection> buildSections() {
        return sortSections(DEFAULT_SECTIONS);
    }

    public List<SidebarNavItem> buildItems() {
        return sortItems(DEFAULT_ITEMS, DEFAULT_SECTIONS);
    }

    public List<SidebarNavItem> buildItems(String filterQuery) {
        return filterItems(buildItems(), filterQuery);
    }

    public List<SidebarNavItem> buildSidebarVisibleByDefaultItems() {
        List<SidebarNavSection> sections = buildSections();
        Map<String, SidebarNavSection> sectionById = new LinkedHashMap<>();
        for (SidebarNavSection section : sections) {
            sectionById.put(section.id(), section);
        }
        List<SidebarNavItem> visible = new ArrayList<>();
        for (SidebarNavItem item : buildItems()) {
            SidebarNavSection section = sectionById.get(item.sectionId());
            if (shouldBeVisibleByDefault(item, section)) {
                visible.add(item);
            }
        }
        return List.copyOf(visible);
    }

    public List<SidebarNavItem> buildPaletteFirstItems() {
        List<SidebarNavItem> paletteFirst = new ArrayList<>();
        for (SidebarNavItem item : buildItems()) {
            if (item.surfaceHint() == SidebarSurfaceHint.PALETTE) {
                paletteFirst.add(item);
            }
        }
        return List.copyOf(paletteFirst);
    }

    public List<SidebarRailDomain> buildRailDomains() {
        return List.of(SidebarRailDomain.values());
    }

    public Map<String, SidebarRailActionMapping> buildRailActionMappings() {
        Map<String, SidebarRailActionMapping> mappings = new LinkedHashMap<>();
        for (SidebarNavItem item : buildItems()) {
            SidebarRailActionMapping mapping = resolveRailActionMapping(item);
            mappings.put(item.actionId(), mapping);
        }
        return Collections.unmodifiableMap(mappings);
    }

    public SidebarRailActionMapping resolveRailActionMapping(SidebarNavItem item) {
        if (item == null) {
            return new SidebarRailActionMapping("", SidebarRailDomain.WORK, SidebarContextPlacement.DOMAIN_LIST);
        }
        SidebarRailDomain railDomain = resolveRailDomain(item.sectionId(), item.actionId());
        SidebarContextPlacement placement = resolveContextPlacement(item.actionId());
        return new SidebarRailActionMapping(item.actionId(), railDomain, placement);
    }

    public List<SidebarNavItem> buildPinnedTopZoneBaselineItems() {
        List<SidebarNavItem> output = new ArrayList<>();
        for (SidebarNavItem item : buildItems()) {
            SidebarRailActionMapping mapping = resolveRailActionMapping(item);
            if (mapping.pinnedTopZone()) {
                output.add(item);
            }
        }
        output.sort(Comparator
            .comparingInt((SidebarNavItem item) -> resolveRailActionMapping(item).railDomain().ordinal())
            .thenComparingInt(SidebarNavItem::priority)
            .thenComparing(SidebarNavItem::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(output);
    }

    public List<SidebarNavItem> buildContextSidebarDomainItems(SidebarRailDomain domain) {
        SidebarRailDomain safeDomain = domain == null ? SidebarRailDomain.WORK : domain;
        if (safeDomain == SidebarRailDomain.RECENT) {
            return buildRecentItems(loadState(), maxRecentItems());
        }
        List<SidebarNavItem> output = new ArrayList<>();
        for (SidebarNavItem item : buildItems()) {
            SidebarRailActionMapping mapping = resolveRailActionMapping(item);
            if (mapping.railDomain() != safeDomain) {
                continue;
            }
            if (mapping.contextPlacement() != SidebarContextPlacement.DOMAIN_LIST) {
                continue;
            }
            output.add(item);
        }
        return List.copyOf(output);
    }

    public List<SidebarNavItem> buildQuickAccessItems(int maxItems) {
        int safeMaxItems = Math.max(1, maxItems);
        List<SidebarNavSection> sections = buildSections();
        List<SidebarNavItem> items = buildItems();
        Map<String, SidebarNavSection> sectionById = new LinkedHashMap<>();
        for (SidebarNavSection section : sections) {
            sectionById.put(section.id(), section);
        }

        LinkedHashSet<String> addedActionIds = new LinkedHashSet<>();
        List<SidebarNavItem> quickItems = new ArrayList<>();

        appendZoneItems(
            quickItems,
            items,
            sectionById,
            SidebarNavZone.QUICK,
            safeMaxItems,
            addedActionIds
        );
        appendZoneItems(
            quickItems,
            items,
            sectionById,
            SidebarNavZone.CORE,
            safeMaxItems,
            addedActionIds
        );
        appendZoneItems(
            quickItems,
            items,
            sectionById,
            SidebarNavZone.ADVANCED,
            safeMaxItems,
            addedActionIds
        );
        return List.copyOf(quickItems);
    }

    public List<SidebarNavSection> sortSections(List<SidebarNavSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        List<SidebarNavSection> sorted = new ArrayList<>(sections);
        sorted.sort(SECTION_ORDER);
        return List.copyOf(sorted);
    }

    public List<SidebarNavItem> sortItems(List<SidebarNavItem> items, List<SidebarNavSection> sections) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> sectionOrder = new LinkedHashMap<>();
        for (SidebarNavSection section : sortSections(sections == null ? List.of() : sections)) {
            sectionOrder.put(section.id(), section.order());
        }
        List<SidebarNavItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator
            .comparingInt((SidebarNavItem item) -> sectionOrder.getOrDefault(item.sectionId(), Integer.MAX_VALUE))
            .thenComparingInt(SidebarNavItem::priority)
            .thenComparing(SidebarNavItem::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(SidebarNavItem::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(sorted);
    }

    public List<SidebarNavItem> filterItems(List<SidebarNavItem> items, String query) {
        return filterItems(items, null, query);
    }

    public List<SidebarNavItem> filterItems(
        List<SidebarNavItem> items,
        List<SidebarNavSection> sections,
        String query
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return List.copyOf(items);
        }
        Map<String, SidebarNavSection> sectionById = new LinkedHashMap<>();
        for (SidebarNavSection section : sortSections(sections == null ? buildSections() : sections)) {
            sectionById.put(section.id(), section);
        }
        List<SidebarNavItem> filtered = new ArrayList<>();
        for (SidebarNavItem item : items) {
            if (item == null) {
                continue;
            }
            SidebarNavSection section = sectionById.get(item.sectionId());
            String categoryText = section == null
                ? ""
                : section.label() + " " + section.zone().name().toLowerCase();
            if (item.matchesQuery(query, categoryText)) {
                filtered.add(item);
            }
        }
        return List.copyOf(filtered);
    }

    public Map<String, List<SidebarNavItem>> groupItemsBySection(
        List<SidebarNavItem> items,
        List<SidebarNavSection> sections
    ) {
        List<SidebarNavSection> sortedSections = sortSections(sections == null ? buildSections() : sections);
        Map<String, List<SidebarNavItem>> grouped = new LinkedHashMap<>();
        for (SidebarNavSection section : sortedSections) {
            grouped.put(section.id(), new ArrayList<>());
        }

        for (SidebarNavItem item : sortItems(items == null ? List.of() : items, sortedSections)) {
            grouped.computeIfAbsent(item.sectionId(), ignored -> new ArrayList<>()).add(item);
        }

        Map<String, List<SidebarNavItem>> immutable = new LinkedHashMap<>();
        for (Map.Entry<String, List<SidebarNavItem>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(immutable);
    }

    public SidebarNavState loadState() {
        return new SidebarNavState(
            ConfigManager.getUxSidebarExpandedSectionIds(),
            ConfigManager.getUxSidebarFavoriteActionIds(),
            ConfigManager.getUxSidebarRecentActionIds()
        );
    }

    public void saveState(SidebarNavState state) {
        SidebarNavState safeState = state == null ? SidebarNavState.empty() : state;
        ConfigManager.setUxSidebarExpandedSectionIds(safeState.expandedSectionIds());
        ConfigManager.setUxSidebarFavoriteActionIds(safeState.favoriteActionIds());
        ConfigManager.setUxSidebarRecentActionIds(safeState.recentActionIds());
    }

    public SidebarNavState updateSectionExpanded(SidebarNavState state, String sectionId, boolean expanded) {
        SidebarNavState safeState = state == null ? loadState() : state;
        SidebarNavState updated = safeState.withSectionExpanded(sectionId, expanded);
        saveState(updated);
        return updated;
    }

    public SidebarNavState updateFavoriteAction(SidebarNavState state, String actionId, boolean favorite) {
        SidebarNavState safeState = state == null ? loadState() : state;
        if (!isFavoritesEnabled()) {
            return safeState;
        }
        SidebarNavState updated = safeState.withFavoriteAction(actionId, favorite, maxFavoriteItems());
        saveState(updated);
        return updated;
    }

    public SidebarNavState recordRecentAction(SidebarNavState state, String actionId) {
        SidebarNavState safeState = state == null ? loadState() : state;
        if (!isRecentEnabled()) {
            return safeState;
        }
        SidebarNavState updated = safeState.withRecordedRecentAction(actionId, maxRecentItems());
        saveState(updated);
        return updated;
    }

    public List<SidebarNavItem> buildFavoriteItems(SidebarNavState state, int maxItems) {
        if (!isFavoritesEnabled()) {
            return List.of();
        }
        SidebarNavState safeState = state == null ? loadState() : state;
        int safeMaxItems = Math.max(1, Math.min(maxItems, maxFavoriteItems()));
        return resolveItemsByActionIds(safeState.favoriteActionIds(), safeMaxItems);
    }

    public List<SidebarNavItem> buildRecentItems(SidebarNavState state, int maxItems) {
        if (!isRecentEnabled()) {
            return List.of();
        }
        SidebarNavState safeState = state == null ? loadState() : state;
        int safeMaxItems = Math.max(1, Math.min(maxItems, maxRecentItems()));
        return resolveItemsByActionIds(safeState.recentActionIds(), safeMaxItems);
    }

    public int maxQuickItems() {
        return ConfigManager.getUxSidebarMaxQuickItems();
    }

    public int maxFavoriteItems() {
        return ConfigManager.getUxSidebarMaxFavorites();
    }

    public int maxRecentItems() {
        return ConfigManager.getUxSidebarMaxRecent();
    }

    public boolean isFilterEnabled() {
        return true;
    }

    public boolean isFavoritesEnabled() {
        return true;
    }

    public boolean isRecentEnabled() {
        return true;
    }

    private static SidebarNavSection section(
        String id,
        String label,
        int order,
        SidebarNavZone zone,
        boolean collapsible,
        boolean defaultExpanded
    ) {
        return new SidebarNavSection(id, label, order, zone, collapsible, defaultExpanded);
    }

    private static SidebarNavItem item(
        String id,
        String label,
        String sectionId,
        int priority,
        String actionId,
        String icon,
        List<String> tags
    ) {
        String canonicalLabel = canonicalLabel(actionId, label);
        SidebarActionTaxonomy taxonomy = resolveTaxonomy(sectionId, actionId);
        SidebarUsagePriority usagePriority = resolveUsagePriority(taxonomy, actionId);
        SidebarSurfaceHint surfaceHint = resolveSurfaceHint(taxonomy, usagePriority, actionId);
        return new SidebarNavItem(
            id,
            canonicalLabel,
            sectionId,
            priority,
            actionId,
            icon,
            tags,
            taxonomy,
            resolveShortDescription(canonicalLabel, taxonomy, actionId),
            resolveAliases(canonicalLabel, actionId, tags),
            usagePriority,
            surfaceHint
        );
    }

    private static SidebarRailDomain resolveRailDomain(String sectionId, String actionId) {
        if (actionId != null) {
            if (actionId.startsWith("main.system.")) {
                return SidebarRailDomain.SYSTEM;
            }
            if (actionId.startsWith("main.analytics.")) {
                return SidebarRailDomain.ANALYTICS;
            }
            if (actionId.startsWith("main.tools.")) {
                return SidebarRailDomain.TOOLS;
            }
            if (actionId.startsWith("main.ai.")) {
                return resolveAiRailDomain(actionId);
            }
            if (actionId.startsWith("main.history.")) {
                return resolveHistoryRailDomain(actionId);
            }
            if (actionId.startsWith("main.task.") || actionId.startsWith("main.view.") || actionId.startsWith("main.inbox.")) {
                return SidebarRailDomain.WORK;
            }
        }
        if (sectionId == null) {
            return SidebarRailDomain.WORK;
        }
        return switch (sectionId) {
            case "tools" -> SidebarRailDomain.TOOLS;
            case "analysis" -> SidebarRailDomain.ANALYTICS;
            case "ai" -> SidebarRailDomain.ANALYTICS;
            case "system" -> SidebarRailDomain.SYSTEM;
            default -> SidebarRailDomain.WORK;
        };
    }

    private static SidebarRailDomain resolveAiRailDomain(String actionId) {
        if (actionId == null) {
            return SidebarRailDomain.ANALYTICS;
        }
        return switch (actionId) {
            case "main.ai.analyzeCenter", "main.ai.analyze.selected" -> SidebarRailDomain.ANALYTICS;
            case "main.ai.chat",
                 "main.ai.reminders",
                 "main.ai.autoPrioritize",
                 "main.ai.autoSchedule",
                 "main.ai.categorization" -> SidebarRailDomain.TOOLS;
            default -> SidebarRailDomain.ANALYTICS;
        };
    }

    private static SidebarRailDomain resolveHistoryRailDomain(String actionId) {
        if (actionId == null) {
            return SidebarRailDomain.WORK;
        }
        return switch (actionId) {
            case "main.history.undo", "main.history.redo" -> SidebarRailDomain.WORK;
            case "main.history.shortcuts" -> SidebarRailDomain.SYSTEM;
            case "main.system.commandPalette", "main.system.globalSearchFocus" -> SidebarRailDomain.SYSTEM;
            default -> SidebarRailDomain.WORK;
        };
    }

    private static SidebarContextPlacement resolveContextPlacement(String actionId) {
        if (actionId == null) {
            return SidebarContextPlacement.DOMAIN_LIST;
        }
        return switch (actionId) {
            case "main.inbox.addTask",
                 "main.system.commandPalette",
                 "main.system.globalSearchFocus",
                 "main.history.undo",
                 "main.history.redo" -> SidebarContextPlacement.PINNED_TOP_ZONE;
            default -> SidebarContextPlacement.DOMAIN_LIST;
        };
    }

    private static boolean shouldBeVisibleByDefault(SidebarNavItem item, SidebarNavSection section) {
        if (item == null) {
            return false;
        }
        if (item.surfaceHint() == SidebarSurfaceHint.PALETTE) {
            return false;
        }
        if (item.usagePriority() == SidebarUsagePriority.CRITICAL) {
            return true;
        }
        if (section == null) {
            return item.surfaceHint() == SidebarSurfaceHint.SIDEBAR;
        }
        if (section.zone() == SidebarNavZone.QUICK || section.zone() == SidebarNavZone.CORE) {
            return item.usagePriority() != SidebarUsagePriority.LOW;
        }
        return item.surfaceHint() == SidebarSurfaceHint.SIDEBAR
            && item.usagePriority().ordinal() <= SidebarUsagePriority.HIGH.ordinal();
    }

    private static String canonicalLabel(String actionId, String rawLabel) {
        String safe = rawLabel == null ? "" : rawLabel.trim();
        if (safe.isEmpty()) {
            safe = actionId == null ? "Действие" : actionId;
        }
        if (actionId == null) {
            return safe;
        }
        return switch (actionId) {
            case "main.history.undo" -> "Отменить";
            case "main.history.redo" -> "Повторить";
            case "main.system.commandPalette" -> "Командная палитра";
            case "main.system.globalSearchFocus" -> "Поиск по приложению";
            case "main.system.shortcutsHelp" -> "Горячие клавиши";
            case "main.ai.analyzeCenter" -> "Центр анализа ИИ";
            case "main.tools.template.create" -> "Создать из шаблона";
            case "main.task.archive.selected" -> "Архивировать задачу";
            case "main.task.archive.show" -> "Открыть архив";
            default -> safe;
        };
    }

    private static SidebarActionTaxonomy resolveTaxonomy(String sectionId, String actionId) {
        if (actionId == null) {
            return SidebarActionTaxonomy.ADVANCED;
        }
        if (Set.of(
            "main.task.addSubtask",
            "main.task.archive.selected",
            "main.task.dependency.link",
            "main.task.dependency.unlink",
            "main.task.dependency.details",
            "main.task.bulk.archive",
            "main.task.bulk.delete",
            "main.task.bulk.tag"
        ).contains(actionId)) {
            return SidebarActionTaxonomy.CONTEXTUAL;
        }
        if (Set.of(
            "main.system.commandPalette",
            "main.system.globalSearchFocus",
            "main.inbox.addTask",
            "main.task.panel",
            "main.system.settings"
        ).contains(actionId)) {
            return SidebarActionTaxonomy.CORE;
        }
        if (actionId.startsWith("main.system.")) {
            return SidebarActionTaxonomy.SYSTEM;
        }
        if (sectionId != null && switch (sectionId) {
            case "analysis", "ai" -> true;
            default -> false;
        }) {
            return "main.ai.chat".equals(actionId)
                ? SidebarActionTaxonomy.FREQUENT
                : SidebarActionTaxonomy.ADVANCED;
        }
        if (sectionId != null && switch (sectionId) {
            case "history", "main", "tools" -> true;
            default -> false;
        }) {
            return SidebarActionTaxonomy.FREQUENT;
        }
        if ("manage".equals(sectionId)) {
            return actionId != null && (actionId.contains(".filter.") || actionId.endsWith(".show"))
                ? SidebarActionTaxonomy.FREQUENT
                : SidebarActionTaxonomy.CONTEXTUAL;
        }
        if ("system".equals(sectionId)) {
            return SidebarActionTaxonomy.SYSTEM;
        }
        return SidebarActionTaxonomy.ADVANCED;
    }

    private static SidebarUsagePriority resolveUsagePriority(
        SidebarActionTaxonomy taxonomy,
        String actionId
    ) {
        if (actionId != null && Set.of(
            "main.inbox.addTask",
            "main.system.commandPalette",
            "main.system.globalSearchFocus",
            "main.task.panel",
            "main.system.settings"
        ).contains(actionId)) {
            return SidebarUsagePriority.CRITICAL;
        }
        if (actionId != null && Set.of(
            "main.history.undo",
            "main.history.redo",
            "main.view.calendar",
            "main.view.kanban",
            "main.ai.chat",
            "main.system.help"
        ).contains(actionId)) {
            return SidebarUsagePriority.HIGH;
        }
        if (taxonomy == SidebarActionTaxonomy.CONTEXTUAL) {
            return actionId != null && actionId.startsWith("main.task.bulk.")
                ? SidebarUsagePriority.LOW
                : SidebarUsagePriority.MEDIUM;
        }
        return switch (taxonomy) {
            case CORE -> SidebarUsagePriority.CRITICAL;
            case FREQUENT -> SidebarUsagePriority.MEDIUM;
            case SYSTEM -> SidebarUsagePriority.MEDIUM;
            case ADVANCED -> SidebarUsagePriority.LOW;
            case CONTEXTUAL -> SidebarUsagePriority.MEDIUM;
        };
    }

    private static SidebarSurfaceHint resolveSurfaceHint(
        SidebarActionTaxonomy taxonomy,
        SidebarUsagePriority usagePriority,
        String actionId
    ) {
        if (actionId != null && Set.of(
            "main.task.bulk.archive",
            "main.task.bulk.delete",
            "main.task.bulk.tag",
            "main.task.dependency.unlink",
            "main.task.dependency.link",
            "main.task.dependency.details",
            "main.tools.template.save"
        ).contains(actionId)) {
            return SidebarSurfaceHint.PALETTE;
        }
        if (actionId != null && Set.of(
            "main.task.panel",
            "main.view.calendar",
            "main.view.kanban",
            "main.view.gantt"
        ).contains(actionId)) {
            return SidebarSurfaceHint.SIDEBAR;
        }
        if (usagePriority == SidebarUsagePriority.CRITICAL) {
            return SidebarSurfaceHint.BOTH;
        }
        if (taxonomy == SidebarActionTaxonomy.ADVANCED && usagePriority == SidebarUsagePriority.LOW) {
            return SidebarSurfaceHint.PALETTE;
        }
        if (taxonomy == SidebarActionTaxonomy.SYSTEM && "main.system.export".equals(actionId)) {
            return SidebarSurfaceHint.PALETTE;
        }
        return SidebarSurfaceHint.BOTH;
    }

    private static String resolveShortDescription(
        String label,
        SidebarActionTaxonomy taxonomy,
        String actionId
    ) {
        if (actionId == null) {
            return label;
        }
        return switch (actionId) {
            case "main.system.commandPalette" -> "Открыть быстрый запуск действий, переходов и поиска";
            case "main.system.globalSearchFocus" -> "Сфокусировать глобальный поиск по задачам и данным";
            case "main.inbox.addTask" -> "Создать новую задачу в рабочем списке";
            case "main.task.panel" -> "Вернуться к основной панели задач";
            case "main.ai.chat" -> "Открыть быстрый диалог с ИИ-помощником";
            case "main.system.settings" -> "Открыть настройки приложения";
            case "main.task.bulk.delete" -> "Удалить выбранные задачи одним действием";
            case "main.task.dependency.link" -> "Связать текущую задачу с другой задачей";
            default -> switch (taxonomy) {
                case CORE -> label + " (базовый сценарий)";
                case FREQUENT -> label + " (частый сценарий)";
                case ADVANCED -> label + " (расширенный сценарий)";
                case SYSTEM -> label + " (системное действие)";
                case CONTEXTUAL -> label + " (действие для текущего контекста)";
            };
        };
    }

    private static List<String> resolveAliases(String label, String actionId, List<String> tags) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (tags != null) {
            aliases.addAll(tags);
        }
        if (label != null && !label.isBlank()) {
            aliases.add(label.toLowerCase(Locale.ROOT));
        }
        if (actionId != null) {
            switch (actionId) {
                case "main.system.commandPalette" -> {
                    aliases.add("команды");
                    aliases.add("палитра");
                    aliases.add("ctrl k");
                    aliases.add("cmd k");
                    aliases.add("launcher");
                }
                case "main.system.globalSearchFocus" -> {
                    aliases.add("поиск");
                    aliases.add("найти");
                    aliases.add("search");
                    aliases.add("filter");
                }
                case "main.inbox.addTask" -> {
                    aliases.add("создать задачу");
                    aliases.add("новая задача");
                    aliases.add("add task");
                }
                case "main.system.settings" -> {
                    aliases.add("настройки");
                    aliases.add("параметры");
                    aliases.add("settings");
                }
                case "main.ai.chat" -> {
                    aliases.add("ии");
                    aliases.add("ai");
                    aliases.add("ассистент");
                    aliases.add("chatgpt");
                }
                case "main.view.kanban" -> {
                    aliases.add("доска");
                    aliases.add("kanban board");
                }
                case "main.view.gantt" -> aliases.add("gantt");
                case "main.task.bulk.delete" -> aliases.add("массовое удаление");
                default -> {
                    // tag-derived aliases are enough for most actions
                }
            }
        }
        return List.copyOf(aliases);
    }

    private List<SidebarNavItem> resolveItemsByActionIds(Iterable<String> actionIds, int maxItems) {
        if (actionIds == null || maxItems <= 0) {
            return List.of();
        }
        Map<String, SidebarNavItem> itemByActionId = new LinkedHashMap<>();
        for (SidebarNavItem item : buildItems()) {
            itemByActionId.put(item.actionId().toLowerCase(Locale.ROOT), item);
        }
        List<SidebarNavItem> resolved = new ArrayList<>();
        for (String actionId : actionIds) {
            if (actionId == null) {
                continue;
            }
            SidebarNavItem item = itemByActionId.get(actionId.trim().toLowerCase(Locale.ROOT));
            if (item != null) {
                resolved.add(item);
            }
            if (resolved.size() >= maxItems) {
                break;
            }
        }
        return List.copyOf(resolved);
    }

    private static void appendZoneItems(
        List<SidebarNavItem> output,
        List<SidebarNavItem> sortedItems,
        Map<String, SidebarNavSection> sectionById,
        SidebarNavZone zone,
        int maxItems,
        LinkedHashSet<String> addedActionIds
    ) {
        if (output.size() >= maxItems) {
            return;
        }
        for (SidebarNavItem item : sortedItems) {
            SidebarNavSection section = sectionById.get(item.sectionId());
            if (section == null || section.zone() != zone) {
                continue;
            }
            if (addedActionIds.add(item.actionId())) {
                output.add(item);
            }
            if (output.size() >= maxItems) {
                return;
            }
        }
    }
}
