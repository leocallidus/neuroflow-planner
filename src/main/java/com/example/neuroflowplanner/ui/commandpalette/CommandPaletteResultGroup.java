package com.example.neuroflowplanner.ui.commandpalette;

/**
 * Visual grouping buckets for guided command palette launcher.
 */
public enum CommandPaletteResultGroup {
    RECENT("Recent"),
    SUGGESTED("Suggested"),
    ACTIONS("Actions"),
    ENTITIES("Entities");

    private final String title;

    CommandPaletteResultGroup(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
