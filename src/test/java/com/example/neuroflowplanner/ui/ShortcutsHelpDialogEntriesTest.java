package com.example.neuroflowplanner.ui;

import com.example.neuroflowplanner.ui.interaction.ShortcutRegistry;
import com.example.neuroflowplanner.util.ConfigManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutsHelpDialogEntriesTest {

    @Test
    void defaultMainEntriesIncludeObsidianAliasesWhenProfileEnabled() {
        String previousProfile = ConfigManager.getProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE);
        try {
            ConfigManager.setProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE, ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN);

            List<ShortcutsHelpDialog.ShortcutHelpEntry> entries = ShortcutsHelpDialog.defaultMainEntries();

            assertFalse(entries.isEmpty());
            assertTrue(entries.stream().anyMatch(entry -> "Ctrl/Cmd+Shift+L".equals(entry.shortcut())));
            assertTrue(entries.stream().anyMatch(entry -> "Ctrl/Cmd+P".equals(entry.shortcut())));
            assertTrue(entries.stream().anyMatch(entry -> "Ctrl/Cmd+O".equals(entry.shortcut())));
        } finally {
            ConfigManager.setProperty(
                ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE,
                previousProfile == null ? "" : previousProfile
            );
        }
    }
}
