package com.example.neuroflowplanner.ui.commandpalette;

import com.example.neuroflowplanner.model.search.GlobalSearchResult;

public record CommandPaletteItem(
    String key,
    CommandPaletteItemType type,
    String commandId,
    String title,
    String subtitle,
    String shortcutHint,
    boolean available,
    String unavailableReason,
    double score,
    boolean recent,
    GlobalSearchResult globalSearchResult
) {
    public CommandPaletteItem {
        key = normalize(key);
        type = type == null ? CommandPaletteItemType.ACTION : type;
        commandId = normalize(commandId);
        title = normalize(title);
        subtitle = normalize(subtitle);
        shortcutHint = normalize(shortcutHint);
        unavailableReason = normalize(unavailableReason);
        if (Double.isNaN(score) || Double.isInfinite(score) || score < 0) {
            score = 0;
        }
    }

    public CommandPaletteItem withScore(double nextScore) {
        return new CommandPaletteItem(
            key,
            type,
            commandId,
            title,
            subtitle,
            shortcutHint,
            available,
            unavailableReason,
            nextScore,
            recent,
            globalSearchResult
        );
    }

    public CommandPaletteItem withRecent(boolean isRecent) {
        return new CommandPaletteItem(
            key,
            type,
            commandId,
            title,
            subtitle,
            shortcutHint,
            available,
            unavailableReason,
            score,
            isRecent,
            globalSearchResult
        );
    }

    public String displayTitle() {
        if (!title.isBlank()) {
            return title;
        }
        return commandId;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
