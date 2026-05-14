package com.example.neuroflowplanner.ui.interaction;

import com.example.neuroflowplanner.util.ConfigManager;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutRegistryTest {

    @Test
    void toShortcutTokenReturnsNormalizedTokenForCtrlShift() {
        KeyEvent keyEvent = new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            KeyCode.Z,
            true,
            true,
            false,
            false
        );

        String token = ShortcutRegistry.toShortcutToken(keyEvent);

        assertEquals("CTRL+SHIFT+Z", token);
    }

    @Test
    void toShortcutTokenReturnsNullForModifierOnlyKey() {
        KeyEvent keyEvent = new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            KeyCode.SHIFT,
            true,
            false,
            false,
            false
        );

        String token = ShortcutRegistry.toShortcutToken(keyEvent);

        assertNull(token);
    }

    @Test
    void resolvePrefersFocusedBindingWhenOverrideIsSafe() {
        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        ShortcutRegistry.RegistrationResult globalResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+F",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "global.search",
                false,
                false
            )
        );
        ShortcutRegistry.RegistrationResult focusedResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+F",
                ShortcutRegistry.ShortcutContext.FOCUSED_PANE,
                "focused.search",
                true,
                false
            )
        );

        Optional<ShortcutRegistry.ShortcutBinding> resolved = registry.resolve(
            "CTRL+F",
            Set.of(ShortcutRegistry.ShortcutContext.GLOBAL, ShortcutRegistry.ShortcutContext.FOCUSED_PANE)
        );

        assertTrue(globalResult.accepted());
        assertTrue(focusedResult.accepted());
        assertTrue(resolved.isPresent());
        assertEquals("focused.search", resolved.get().actionId());
    }

    @Test
    void registerRejectsGlobalFocusedConflictWhenOverrideNotSafe() {
        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        ShortcutRegistry.RegistrationResult globalResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+N",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "task.create",
                false,
                false
            )
        );
        ShortcutRegistry.RegistrationResult focusedResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+N",
                ShortcutRegistry.ShortcutContext.FOCUSED_PANE,
                "notes.create",
                false,
                false
            )
        );

        assertTrue(globalResult.accepted());
        assertFalse(focusedResult.accepted());
        assertEquals("global_focused_conflict", focusedResult.reason());
    }

    @Test
    void runStartupConflictCheckReturnsEmptyWhenBindingsAreValid() {
        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        ShortcutRegistry.RegistrationResult globalResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+K",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "palette.open",
                false,
                false
            )
        );
        ShortcutRegistry.RegistrationResult focusedResult = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+K",
                ShortcutRegistry.ShortcutContext.FOCUSED_PANE,
                "palette.context.open",
                true,
                false
            )
        );

        var conflicts = registry.runStartupConflictCheck("test");

        assertTrue(globalResult.accepted());
        assertTrue(focusedResult.accepted());
        assertNotNull(conflicts);
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void sameContextCollisionIsDetectedAndRejected() {
        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        ShortcutRegistry.RegistrationResult first = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+K",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "palette.open",
                false,
                false
            )
        );
        ShortcutRegistry.RegistrationResult second = registry.register(
            new ShortcutRegistry.ShortcutBinding(
                "CTRL+K",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "palette.open.duplicate",
                false,
                false
            )
        );

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertEquals("same_context_conflict", second.reason());
    }

    @Test
    void findBindingByActionIdReturnsHighestPriorityBinding() {
        ShortcutRegistry registry = new ShortcutRegistry(true, true);
        registry.register(new ShortcutRegistry.ShortcutBinding(
            "CTRL+N",
            ShortcutRegistry.ShortcutContext.GLOBAL,
            "task.create",
            false,
            false
        ));
        registry.register(new ShortcutRegistry.ShortcutBinding(
            "CTRL+SHIFT+N",
            ShortcutRegistry.ShortcutContext.FOCUSED_PANE,
            "task.create",
            true,
            false
        ));

        Optional<ShortcutRegistry.ShortcutBinding> resolved = registry.findBindingByActionId("task.create");

        assertTrue(resolved.isPresent());
        assertEquals("CTRL+N", resolved.get().shortcut());
        assertEquals(ShortcutRegistry.ShortcutContext.GLOBAL, resolved.get().context());
    }

    @Test
    void resolveSupportsObsidianAliasForCommandPalette() {
        String previousProfile = ConfigManager.getProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE);
        try {
            ConfigManager.setProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE, ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN);

            ShortcutRegistry registry = new ShortcutRegistry(true, true);
            registry.register(new ShortcutRegistry.ShortcutBinding(
                "CTRL+K",
                ShortcutRegistry.ShortcutContext.GLOBAL,
                "palette.open",
                false,
                false
            ));

            Optional<ShortcutRegistry.ShortcutBinding> resolved = registry.resolve(
                "CTRL+P",
                Set.of(ShortcutRegistry.ShortcutContext.GLOBAL)
            );

            assertTrue(resolved.isPresent());
            assertEquals("palette.open", resolved.get().actionId());
        } finally {
            ConfigManager.setProperty(
                ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE,
                previousProfile == null ? "" : previousProfile
            );
        }
    }

    @Test
    void normalizeShortcutProfileRecognizesObsidianAliases() {
        assertEquals(
            ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN,
            ShortcutRegistry.normalizeShortcutProfile("obsidian-inspired")
        );
        assertEquals(
            ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN,
            ShortcutRegistry.normalizeShortcutProfile("obsidian")
        );
        assertEquals(
            ShortcutRegistry.SHORTCUT_PROFILE_DEFAULT,
            ShortcutRegistry.normalizeShortcutProfile("legacy")
        );
    }

    @Test
    void commandPaletteToggleShortcutRecognizesCtrlK() {
        KeyEvent keyEvent = new KeyEvent(
            KeyEvent.KEY_PRESSED,
            "",
            "",
            KeyCode.K,
            false,
            true,
            false,
            false
        );

        assertTrue(ShortcutRegistry.isCommandPaletteToggleShortcut(keyEvent));
    }

    @Test
    void commandPaletteToggleShortcutRecognizesObsidianAliasCtrlP() {
        String previousProfile = ConfigManager.getProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE);
        try {
            ConfigManager.setProperty(ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE, ShortcutRegistry.SHORTCUT_PROFILE_OBSIDIAN);
            KeyEvent keyEvent = new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                KeyCode.P,
                false,
                true,
                false,
                false
            );

            assertTrue(ShortcutRegistry.isCommandPaletteToggleShortcut(keyEvent));
        } finally {
            ConfigManager.setProperty(
                ShortcutRegistry.CONFIG_UX_SHORTCUT_PROFILE,
                previousProfile == null ? "" : previousProfile
            );
        }
    }
}
