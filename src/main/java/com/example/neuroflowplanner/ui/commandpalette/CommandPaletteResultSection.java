package com.example.neuroflowplanner.ui.commandpalette;

import java.util.List;

/**
 * One visual section in the guided launcher.
 */
public record CommandPaletteResultSection(
    CommandPaletteResultGroup group,
    String title,
    List<CommandPaletteItem> items
) {
    public CommandPaletteResultSection {
        group = group == null ? CommandPaletteResultGroup.ACTIONS : group;
        title = title == null || title.isBlank() ? group.title() : title.trim();
        items = items == null ? List.of() : List.copyOf(items);
    }
}
