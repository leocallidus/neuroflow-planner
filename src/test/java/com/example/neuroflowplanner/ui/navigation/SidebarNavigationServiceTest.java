package com.example.neuroflowplanner.ui.navigation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarNavigationServiceTest {

    @Test
    void buildSectionsAndItemsReturnsSortedDeterministicStructure() {
        SidebarNavigationService service = new SidebarNavigationService();

        List<SidebarNavSection> sections = service.buildSections();
        List<SidebarNavItem> items = service.buildItems();
        Map<String, List<SidebarNavItem>> grouped = service.groupItemsBySection(items, sections);

        assertFalse(sections.isEmpty());
        assertFalse(items.isEmpty());
        assertEquals("history", sections.get(0).id());
        assertTrue(grouped.containsKey("history"));
        assertTrue(grouped.containsKey("main"));
        assertTrue(grouped.get("history").stream().anyMatch(item -> "main.system.commandPalette".equals(item.actionId())));
    }

    @Test
    void filterItemsMatchesByLabelAndTags() {
        SidebarNavigationService service = new SidebarNavigationService();
        List<SidebarNavSection> sections = service.buildSections();
        List<SidebarNavItem> items = service.buildItems();

        List<SidebarNavItem> byLabel = service.filterItems(items, "календарь");
        List<SidebarNavItem> byTag = service.filterItems(items, "bulk delete");
        List<SidebarNavItem> byCategoryAndTag = service.filterItems(items, sections, "управление dependency");

        assertFalse(byLabel.isEmpty());
        assertTrue(byLabel.stream().allMatch(item -> item.matchesQuery("календарь")));
        assertFalse(byTag.isEmpty());
        assertTrue(byTag.stream().anyMatch(item -> "main.task.bulk.delete".equals(item.actionId())));
        assertFalse(byCategoryAndTag.isEmpty());
        assertTrue(byCategoryAndTag.stream().anyMatch(item -> "main.task.dependency.link".equals(item.actionId())));
    }

    @Test
    void buildQuickAccessItemsReturnsTopNWithoutDuplicates() {
        SidebarNavigationService service = new SidebarNavigationService();

        List<SidebarNavItem> quickItems = service.buildQuickAccessItems(8);

        assertEquals(8, quickItems.size());
        assertEquals("main.history.undo", quickItems.get(0).actionId());
        assertEquals(
            quickItems.stream().map(SidebarNavItem::actionId).distinct().count(),
            quickItems.size()
        );
    }

    @Test
    void buildItemsAddsUnifiedUxMetadataAndTaxonomy() {
        SidebarNavigationService service = new SidebarNavigationService();
        Map<String, SidebarNavItem> byActionId = service.buildItems().stream()
            .collect(Collectors.toMap(SidebarNavItem::actionId, Function.identity()));

        SidebarNavItem palette = byActionId.get("main.system.commandPalette");
        SidebarNavItem bulkDelete = byActionId.get("main.task.bulk.delete");
        SidebarNavItem calendar = byActionId.get("main.view.calendar");

        assertNotNull(palette);
        assertEquals("Командная палитра", palette.label());
        assertEquals(SidebarActionTaxonomy.CORE, palette.taxonomy());
        assertEquals(SidebarUsagePriority.CRITICAL, palette.usagePriority());
        assertEquals(SidebarSurfaceHint.BOTH, palette.surfaceHint());
        assertTrue(palette.aliases().contains("ctrl k"));
        assertFalse(palette.shortDescription().isBlank());

        assertNotNull(bulkDelete);
        assertEquals(SidebarActionTaxonomy.CONTEXTUAL, bulkDelete.taxonomy());
        assertEquals(SidebarSurfaceHint.PALETTE, bulkDelete.surfaceHint());

        assertNotNull(calendar);
        assertEquals(SidebarSurfaceHint.SIDEBAR, calendar.surfaceHint());
    }

    @Test
    void filterItemsMatchesByAliasesAndShortDescription() {
        SidebarNavigationService service = new SidebarNavigationService();
        List<SidebarNavItem> items = service.buildItems();

        List<SidebarNavItem> byAlias = service.filterItems(items, "ctrl k");
        List<SidebarNavItem> byDescription = service.filterItems(items, "быстрый запуск действий");
        List<SidebarNavItem> byDailyReview = service.filterItems(items, "ежедневный обзор");
        List<SidebarNavItem> byFocusBlocks = service.filterItems(items, "фокус-блоки");
        List<SidebarNavItem> byPlanningQuality = service.filterItems(items, "качество планирования");

        assertTrue(byAlias.stream().anyMatch(item -> "main.system.commandPalette".equals(item.actionId())));
        assertTrue(byDescription.stream().anyMatch(item -> "main.system.commandPalette".equals(item.actionId())));
        assertTrue(byDailyReview.stream().anyMatch(item -> "main.analytics.dailyReview".equals(item.actionId())));
        assertTrue(byFocusBlocks.stream().anyMatch(item -> "main.analytics.focusBlocks".equals(item.actionId())));
        assertTrue(byPlanningQuality.stream().anyMatch(item -> "main.analytics.planningQuality".equals(item.actionId())));
    }

    @Test
    void buildsExplicitVisibleNowVsPaletteFirstMapping() {
        SidebarNavigationService service = new SidebarNavigationService();

        List<SidebarNavItem> visibleNow = service.buildSidebarVisibleByDefaultItems();
        List<SidebarNavItem> paletteFirst = service.buildPaletteFirstItems();

        assertTrue(visibleNow.stream().anyMatch(item -> "main.inbox.addTask".equals(item.actionId())));
        assertTrue(visibleNow.stream().anyMatch(item -> "main.system.commandPalette".equals(item.actionId())));
        assertTrue(visibleNow.stream().anyMatch(item -> "main.system.settings".equals(item.actionId())));

        assertTrue(paletteFirst.stream().anyMatch(item -> "main.task.bulk.delete".equals(item.actionId())));
        assertTrue(paletteFirst.stream().anyMatch(item -> "main.task.dependency.link".equals(item.actionId())));
        assertFalse(paletteFirst.stream().anyMatch(item -> "main.inbox.addTask".equals(item.actionId())));
    }

    @Test
    void buildsReusableRailDomainAndContextPlacementMapping() {
        SidebarNavigationService service = new SidebarNavigationService();

        Map<String, SidebarRailActionMapping> mappings = service.buildRailActionMappings();

        assertFalse(mappings.isEmpty());
        assertEquals(SidebarRailDomain.WORK, mappings.get("main.inbox.addTask").railDomain());
        assertEquals(SidebarContextPlacement.PINNED_TOP_ZONE, mappings.get("main.inbox.addTask").contextPlacement());
        assertEquals(SidebarRailDomain.SYSTEM, mappings.get("main.system.commandPalette").railDomain());
        assertEquals(SidebarContextPlacement.PINNED_TOP_ZONE, mappings.get("main.system.commandPalette").contextPlacement());
        assertEquals(SidebarRailDomain.SYSTEM, mappings.get("main.system.settings").railDomain());
        assertEquals(SidebarContextPlacement.DOMAIN_LIST, mappings.get("main.system.settings").contextPlacement());
    }

    @Test
    void buildsPinnedTopZoneBaselineAndDomainListsWithoutCrossDomainDuplication() {
        SidebarNavigationService service = new SidebarNavigationService();

        List<SidebarNavItem> pinned = service.buildPinnedTopZoneBaselineItems();
        List<SidebarNavItem> analytics = service.buildContextSidebarDomainItems(SidebarRailDomain.ANALYTICS);
        List<SidebarNavItem> tools = service.buildContextSidebarDomainItems(SidebarRailDomain.TOOLS);

        assertTrue(pinned.stream().anyMatch(item -> "main.inbox.addTask".equals(item.actionId())));
        assertTrue(pinned.stream().anyMatch(item -> "main.system.commandPalette".equals(item.actionId())));
        assertTrue(pinned.stream().anyMatch(item -> "main.system.globalSearchFocus".equals(item.actionId())));
        assertFalse(pinned.stream().anyMatch(item -> "main.task.bulk.delete".equals(item.actionId())));

        assertTrue(analytics.stream().anyMatch(item -> "main.analytics.dashboard".equals(item.actionId())));
        assertTrue(analytics.stream().anyMatch(item -> "main.analytics.dailyReview".equals(item.actionId())));
        assertTrue(analytics.stream().anyMatch(item -> "main.analytics.focusBlocks".equals(item.actionId())));
        assertTrue(analytics.stream().anyMatch(item -> "main.analytics.planningQuality".equals(item.actionId())));
        assertTrue(analytics.stream().anyMatch(item -> "main.ai.analyzeCenter".equals(item.actionId())));
        assertFalse(analytics.stream().anyMatch(item -> "main.ai.chat".equals(item.actionId())));

        assertTrue(tools.stream().anyMatch(item -> "main.ai.chat".equals(item.actionId())));
        assertFalse(tools.stream().anyMatch(item -> "main.analytics.dashboard".equals(item.actionId())));

        Set<String> pinnedActionIds = pinned.stream().map(SidebarNavItem::actionId).collect(Collectors.toSet());
        assertTrue(analytics.stream().noneMatch(item -> pinnedActionIds.contains(item.actionId())));
        assertTrue(tools.stream().noneMatch(item -> pinnedActionIds.contains(item.actionId())));
    }

    @Test
    void railDomainsProvidePlainLanguageLabelsForTooltipAndContextHeader() {
        SidebarNavigationService service = new SidebarNavigationService();

        List<SidebarRailDomain> domains = service.buildRailDomains();

        assertEquals(List.of(SidebarRailDomain.values()), domains);
        assertEquals("Рабочее", SidebarRailDomain.WORK.label());
        assertEquals("Недавние действия", SidebarRailDomain.RECENT.contextHeaderLabel());
        assertEquals("Рабочие сценарии", SidebarRailDomain.WORK.railTooltipLabel());
        assertEquals("Аналитика и ИИ", SidebarRailDomain.ANALYTICS.contextHeaderLabel());
    }

    @Test
    void navStateSupportsExpandedFavoritesAndRecentPolicies() {
        SidebarNavState state = SidebarNavState.empty()
            .withSectionExpanded("main", true)
            .withFavoriteAction("main.inbox.addTask", true)
            .withRecordedRecentAction("main.system.commandPalette", 3)
            .withRecordedRecentAction("main.inbox.addTask", 3)
            .withRecordedRecentAction("main.system.export", 3)
            .withRecordedRecentAction("main.system.commandPalette", 3);

        assertTrue(state.isSectionExpanded("main"));
        assertTrue(state.isFavoriteAction("main.inbox.addTask"));
        assertEquals(3, state.recentActionIds().size());
        assertEquals("main.system.commandpalette", state.recentActionIds().get(0));
        assertNotNull(state.expandedSectionIds());
        assertNotNull(state.favoriteActionIds());
    }

    @Test
    void buildFavoriteAndRecentItemsResolvesKnownActionsAndAppliesLimits() {
        SidebarNavigationService service = new SidebarNavigationService();
        SidebarNavState state = SidebarNavState.empty()
            .withFavoriteAction("main.inbox.addTask", true, 10)
            .withFavoriteAction("main.system.export", true, 10)
            .withFavoriteAction("missing.action", true, 10)
            .withRecordedRecentAction("main.system.export", 10)
            .withRecordedRecentAction("missing.action", 10)
            .withRecordedRecentAction("main.inbox.addTask", 10);

        List<SidebarNavItem> favorites = service.buildFavoriteItems(state, 2);
        List<SidebarNavItem> recent = service.buildRecentItems(state, 2);

        assertEquals(2, favorites.size());
        assertEquals("main.inbox.addTask", favorites.get(0).actionId());
        assertEquals("main.system.export", favorites.get(1).actionId());

        assertEquals(2, recent.size());
        assertEquals("main.inbox.addTask", recent.get(0).actionId());
        assertEquals("main.system.export", recent.get(1).actionId());
    }
}
