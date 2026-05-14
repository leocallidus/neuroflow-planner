package com.example.neuroflowplanner.ui.navigation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarNavStateTest {

    @Test
    void favoritesFollowLruPolicyWithMaxLimit() {
        SidebarNavState state = SidebarNavState.empty()
            .withFavoriteAction("main.task.panel", true, 2)
            .withFavoriteAction("main.system.export", true, 2)
            .withFavoriteAction("main.task.panel", true, 2)
            .withFavoriteAction("main.tools.notes.open", true, 2);

        assertEquals(
            Set.of("main.task.panel", "main.tools.notes.open"),
            state.favoriteActionIds()
        );
    }

    @Test
    void recentActionsKeepMostRecentUniqueEntries() {
        SidebarNavState state = SidebarNavState.empty()
            .withRecordedRecentAction("MAIN.TASK.PANEL", 3)
            .withRecordedRecentAction("main.system.export", 3)
            .withRecordedRecentAction(" main.task.panel ", 3)
            .withRecordedRecentAction("main.tools.notes.open", 3);

        assertEquals(
            List.of("main.tools.notes.open", "main.task.panel", "main.system.export"),
            state.recentActionIds()
        );
    }

    @Test
    void constructorNormalizesAndDeduplicatesAllCollections() {
        SidebarNavState state = new SidebarNavState(
            Set.of(" MAIN ", "main", "tools"),
            Set.of(" MAIN.SYSTEM.EXPORT ", "main.system.export", "main.task.panel"),
            List.of("main.task.panel", "main.task.panel", "main.system.export")
        );

        assertTrue(state.isSectionExpanded("main"));
        assertTrue(state.isSectionExpanded("tools"));
        assertEquals(2, state.expandedSectionIds().size());
        assertTrue(state.isFavoriteAction("main.system.export"));
        assertTrue(state.isFavoriteAction("main.task.panel"));
        assertEquals(2, state.favoriteActionIds().size());
        assertEquals(
            List.of("main.task.panel", "main.system.export"),
            state.recentActionIds()
        );
    }
}
