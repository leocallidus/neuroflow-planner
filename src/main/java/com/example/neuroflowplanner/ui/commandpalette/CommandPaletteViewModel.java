package com.example.neuroflowplanner.ui.commandpalette;

import java.util.List;

/**
 * Presentation model for the command palette guided launcher.
 */
public record CommandPaletteViewModel(
    String query,
    List<CommandPaletteResultSection> sections,
    List<CommandPaletteItem> flatItems,
    List<String> exampleQueries,
    boolean showGuidedEmptyState,
    String emptyTitle,
    String emptyBody
) {
    public CommandPaletteViewModel {
        query = query == null ? "" : query.trim();
        sections = sections == null ? List.of() : List.copyOf(sections);
        flatItems = flatItems == null ? List.of() : List.copyOf(flatItems);
        exampleQueries = exampleQueries == null ? List.of() : List.copyOf(exampleQueries);
        emptyTitle = emptyTitle == null ? "" : emptyTitle.trim();
        emptyBody = emptyBody == null ? "" : emptyBody.trim();
    }

    public boolean hasResults() {
        return !flatItems.isEmpty();
    }
}
