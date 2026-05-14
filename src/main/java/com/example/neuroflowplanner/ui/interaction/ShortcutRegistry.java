package com.example.neuroflowplanner.ui.interaction;

import com.example.neuroflowplanner.util.ConfigManager;
import com.example.neuroflowplanner.util.StructuredLogger;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for keyboard shortcuts with context-aware conflict policy.
 */
public final class ShortcutRegistry {
    private static final StructuredLogger LOG = StructuredLogger.getLogger(ShortcutRegistry.class);
    public static final String CONFIG_UX_SHORTCUT_PROFILE = "ux.shortcuts.profile";
    public static final String SHORTCUT_PROFILE_DEFAULT = "default";
    public static final String SHORTCUT_PROFILE_OBSIDIAN = "obsidian";
    private static final List<ShortcutAlias> OBSIDIAN_SHORTCUT_ALIASES = List.of(
        new ShortcutAlias("Ctrl/Cmd+P", "CTRL+P", "CTRL+K", "Открыть командную палитру"),
        new ShortcutAlias("Ctrl/Cmd+Shift+P", "CTRL+SHIFT+P", "CTRL+K", "Открыть командную палитру"),
        new ShortcutAlias("Ctrl/Cmd+O", "CTRL+O", "CTRL+F", "Фокус глобального поиска"),
        new ShortcutAlias("Ctrl/Cmd+Shift+F", "CTRL+SHIFT+F", "CTRL+F", "Фокус глобального поиска")
    );
    private static final Map<String, String> OBSIDIAN_SHORTCUT_ALIAS_LOOKUP = buildAliasLookup(OBSIDIAN_SHORTCUT_ALIASES);

    private final boolean enabled;
    private final boolean strictConflicts;
    private final Map<String, Map<ShortcutContext, ShortcutBinding>> bindingsByShortcut = new LinkedHashMap<>();

    public ShortcutRegistry(boolean enabled, boolean strictConflicts) {
        this.enabled = enabled;
        this.strictConflicts = strictConflicts;
    }

    public static ShortcutRegistry withConfigDefaults() {
        return new ShortcutRegistry(ConfigManager.isUxShortcutsEnabled(), true);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isStrictConflicts() {
        return strictConflicts;
    }

    public synchronized RegistrationResult register(ShortcutBinding binding) {
        ShortcutBinding incoming = Objects.requireNonNull(binding, "binding").normalized();
        Map<ShortcutContext, ShortcutBinding> byContext = bindingsByShortcut.computeIfAbsent(
            incoming.shortcut(),
            ignored -> new LinkedHashMap<>()
        );

        ShortcutBinding existingSameContext = byContext.get(incoming.context());
        if (existingSameContext != null) {
            return rejectConflict(
                "same_context_conflict",
                existingSameContext,
                incoming,
                byContext
            );
        }

        ShortcutBinding existingGlobal = byContext.get(ShortcutContext.GLOBAL);
        ShortcutBinding existingFocused = byContext.get(ShortcutContext.FOCUSED_PANE);
        ShortcutBinding global = resolveGlobal(existingGlobal, incoming);
        ShortcutBinding focused = resolveFocused(existingFocused, incoming);
        if (global != null && focused != null && !isAllowedGlobalFocusedPair(global, focused)) {
            ShortcutBinding existing = incoming.context() == ShortcutContext.GLOBAL ? focused : global;
            return rejectConflict("global_focused_conflict", existing, incoming, byContext);
        }

        byContext.put(incoming.context(), incoming);
        LOG.info(
            "ux.shortcut.registered",
            "shortcut", incoming.shortcut(),
            "context", incoming.context().name(),
            "actionId", incoming.actionId(),
            "overrideSafe", incoming.overrideSafe(),
            "safetyCritical", incoming.safetyCritical(),
            "enabled", enabled
        );
        return RegistrationResult.accepted(incoming);
    }

    public synchronized Optional<ShortcutBinding> resolve(String shortcut, Set<ShortcutContext> activeContexts) {
        if (!enabled) {
            return Optional.empty();
        }
        String normalizedShortcut = normalizeShortcut(shortcut);
        if (normalizedShortcut == null) {
            return Optional.empty();
        }
        Map<ShortcutContext, ShortcutBinding> byContext = bindingsByShortcut.get(normalizedShortcut);
        if ((byContext == null || byContext.isEmpty())) {
            String mappedShortcut = resolveAliasTarget(normalizedShortcut);
            if (mappedShortcut != null) {
                byContext = bindingsByShortcut.get(mappedShortcut);
            }
        }
        if (byContext == null || byContext.isEmpty()) {
            return Optional.empty();
        }

        Set<ShortcutContext> contexts = activeContexts == null || activeContexts.isEmpty()
            ? Set.of(ShortcutContext.GLOBAL, ShortcutContext.FOCUSED_PANE, ShortcutContext.CONTROL_DEFAULT)
            : activeContexts;

        ShortcutBinding global = contexts.contains(ShortcutContext.GLOBAL)
            ? byContext.get(ShortcutContext.GLOBAL)
            : null;
        ShortcutBinding focused = contexts.contains(ShortcutContext.FOCUSED_PANE)
            ? byContext.get(ShortcutContext.FOCUSED_PANE)
            : null;
        ShortcutBinding controlDefault = contexts.contains(ShortcutContext.CONTROL_DEFAULT)
            ? byContext.get(ShortcutContext.CONTROL_DEFAULT)
            : null;

        if (global != null && focused != null && isAllowedGlobalFocusedPair(global, focused)) {
            return Optional.of(focused);
        }
        if (global != null) {
            return Optional.of(global);
        }
        if (focused != null) {
            return Optional.of(focused);
        }
        return Optional.ofNullable(controlDefault);
    }

    public synchronized List<ShortcutBinding> listBindings() {
        List<ShortcutBinding> out = new ArrayList<>();
        for (Map<ShortcutContext, ShortcutBinding> byContext : bindingsByShortcut.values()) {
            out.addAll(byContext.values());
        }
        out.sort(Comparator
            .comparing(ShortcutBinding::shortcut)
            .thenComparing(binding -> binding.context().priority(), Comparator.reverseOrder())
            .thenComparing(ShortcutBinding::actionId));
        return List.copyOf(out);
    }

    public synchronized Optional<ShortcutBinding> findBindingByActionId(String actionId) {
        String normalizedActionId = normalizeActionIdValue(actionId);
        if (normalizedActionId == null) {
            return Optional.empty();
        }
        ShortcutBinding best = null;
        for (Map<ShortcutContext, ShortcutBinding> byContext : bindingsByShortcut.values()) {
            for (ShortcutBinding binding : byContext.values()) {
                if (!normalizedActionId.equals(binding.actionId())) {
                    continue;
                }
                if (best == null || binding.context().priority() > best.context().priority()) {
                    best = binding;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public synchronized List<ShortcutConflict> detectConflicts() {
        List<ShortcutConflict> conflicts = new ArrayList<>();
        for (Map.Entry<String, Map<ShortcutContext, ShortcutBinding>> entry : bindingsByShortcut.entrySet()) {
            ShortcutBinding global = entry.getValue().get(ShortcutContext.GLOBAL);
            ShortcutBinding focused = entry.getValue().get(ShortcutContext.FOCUSED_PANE);
            if (global != null && focused != null && !isAllowedGlobalFocusedPair(global, focused)) {
                conflicts.add(new ShortcutConflict(
                    entry.getKey(),
                    global.context(),
                    global.actionId(),
                    focused.context(),
                    focused.actionId(),
                    "global_focused_conflict"
                ));
            }
        }
        return List.copyOf(conflicts);
    }

    public synchronized List<ShortcutConflict> runStartupConflictCheck(String scope) {
        String normalizedScope = scope == null || scope.isBlank() ? "unknown" : scope.trim();
        List<ShortcutConflict> conflicts = detectConflicts();
        if (conflicts.isEmpty()) {
            LOG.info(
                "ux.shortcut.startup.conflicts.none",
                "scope", normalizedScope,
                "bindings", listBindings().size(),
                "strict", strictConflicts
            );
            return conflicts;
        }
        for (ShortcutConflict conflict : conflicts) {
            LOG.warning(
                "ux.shortcut.startup.conflict",
                "scope", normalizedScope,
                "shortcut", conflict.shortcut(),
                "existingContext", conflict.existingContext().name(),
                "existingActionId", conflict.existingActionId(),
                "incomingContext", conflict.incomingContext().name(),
                "incomingActionId", conflict.incomingActionId(),
                "reason", conflict.reason()
            );
        }
        return conflicts;
    }

    public static String activeShortcutProfile() {
        String configuredProfile = ConfigManager.getProperty(CONFIG_UX_SHORTCUT_PROFILE);
        if (configuredProfile != null && !configuredProfile.isBlank()) {
            return normalizeShortcutProfile(configuredProfile);
        }
        String layoutPreset = ConfigManager.getProperty("ux.layout.preset");
        if (layoutPreset != null && "obsidian-inspired".equals(layoutPreset.trim().toLowerCase(Locale.ROOT))) {
            return SHORTCUT_PROFILE_OBSIDIAN;
        }
        return SHORTCUT_PROFILE_DEFAULT;
    }

    public static boolean isObsidianShortcutProfileEnabled() {
        return SHORTCUT_PROFILE_OBSIDIAN.equals(activeShortcutProfile());
    }

    public static List<ShortcutAlias> shortcutAliasesForProfile(String profile) {
        String normalizedProfile = normalizeShortcutProfile(profile);
        if (SHORTCUT_PROFILE_OBSIDIAN.equals(normalizedProfile)) {
            return OBSIDIAN_SHORTCUT_ALIASES;
        }
        return List.of();
    }

    public static List<ShortcutAlias> activeShortcutAliases() {
        return shortcutAliasesForProfile(activeShortcutProfile());
    }

    public static String toShortcutToken(KeyEvent event) {
        if (event == null) {
            return null;
        }
        KeyCode code = event.getCode();
        if (code == null
            || code.isModifierKey()
            || code == KeyCode.UNDEFINED
            || code == KeyCode.SHIFT
            || code == KeyCode.CONTROL
            || code == KeyCode.ALT
            || code == KeyCode.META
            || code == KeyCode.SHORTCUT) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        if (event.isShortcutDown() || event.isControlDown() || event.isMetaDown()) {
            builder.append("CTRL+");
        }
        if (event.isShiftDown()) {
            builder.append("SHIFT+");
        }
        if (event.isAltDown()) {
            builder.append("ALT+");
        }
        builder.append(normalizeKeyCodeName(code));
        return builder.toString();
    }

    public static boolean isCommandPaletteToggleShortcut(KeyEvent event) {
        String token = toShortcutToken(event);
        if (token == null || token.isBlank()) {
            return false;
        }
        if ("CTRL+K".equals(token)) {
            return true;
        }
        if (!SHORTCUT_PROFILE_OBSIDIAN.equals(activeShortcutProfile())) {
            return false;
        }
        String mappedToken = OBSIDIAN_SHORTCUT_ALIAS_LOOKUP.get(token);
        return "CTRL+K".equals(mappedToken);
    }

    public static String toDisplayShortcutHint(String rawShortcut) {
        if (rawShortcut == null || rawShortcut.isBlank()) {
            return "";
        }
        String normalized = rawShortcut.trim().replace('_', ' ');
        if (normalized.startsWith("CTRL+")) {
            return "Ctrl/Cmd+" + normalized.substring("CTRL+".length());
        }
        return normalized;
    }

    private static String normalizeKeyCodeName(KeyCode code) {
        String name = code.getName();
        if (name == null || name.isBlank()) {
            return code.name().toUpperCase(Locale.ROOT);
        }
        return name
            .trim()
            .toUpperCase(Locale.ROOT)
            .replace(' ', '_');
    }

    private RegistrationResult rejectConflict(
        String reason,
        ShortcutBinding existing,
        ShortcutBinding incoming,
        Map<ShortcutContext, ShortcutBinding> byContext
    ) {
        LOG.warning(
            "ux.shortcut.conflict.detected",
            "shortcut", incoming.shortcut(),
            "reason", reason,
            "existingContext", existing.context().name(),
            "existingActionId", existing.actionId(),
            "incomingContext", incoming.context().name(),
            "incomingActionId", incoming.actionId(),
            "strict", strictConflicts
        );

        if (!strictConflicts) {
            // In permissive mode keep whichever binding has higher context priority.
            if (incoming.context().priority() > existing.context().priority()) {
                byContext.remove(existing.context());
                byContext.put(incoming.context(), incoming);
                LOG.warning(
                    "ux.shortcut.conflict.resolved",
                    "shortcut", incoming.shortcut(),
                    "keptContext", incoming.context().name(),
                    "keptActionId", incoming.actionId(),
                    "droppedContext", existing.context().name(),
                    "droppedActionId", existing.actionId()
                );
                return RegistrationResult.accepted(incoming);
            }
        }
        return RegistrationResult.rejected(reason, existing);
    }

    private ShortcutBinding resolveGlobal(ShortcutBinding existingGlobal, ShortcutBinding incoming) {
        if (incoming.context() == ShortcutContext.GLOBAL) {
            return incoming;
        }
        return existingGlobal;
    }

    private ShortcutBinding resolveFocused(ShortcutBinding existingFocused, ShortcutBinding incoming) {
        if (incoming.context() == ShortcutContext.FOCUSED_PANE) {
            return incoming;
        }
        return existingFocused;
    }

    private boolean isAllowedGlobalFocusedPair(ShortcutBinding global, ShortcutBinding focused) {
        return focused.overrideSafe() && !global.safetyCritical();
    }

    private String resolveAliasTarget(String shortcut) {
        if (shortcut == null) {
            return null;
        }
        String profile = activeShortcutProfile();
        if (!SHORTCUT_PROFILE_OBSIDIAN.equals(profile)) {
            return null;
        }
        return OBSIDIAN_SHORTCUT_ALIAS_LOOKUP.get(shortcut);
    }

    private String normalizeShortcut(String shortcut) {
        if (shortcut == null) {
            return null;
        }
        String trimmed = shortcut.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    public static String normalizeShortcutProfile(String rawProfile) {
        if (rawProfile == null) {
            return SHORTCUT_PROFILE_DEFAULT;
        }
        String normalized = rawProfile.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return SHORTCUT_PROFILE_DEFAULT;
        }
        return switch (normalized) {
            case "obsidian", "obsidian-inspired" -> SHORTCUT_PROFILE_OBSIDIAN;
            default -> SHORTCUT_PROFILE_DEFAULT;
        };
    }

    private static String normalizeActionIdValue(String actionId) {
        if (actionId == null) {
            return null;
        }
        String trimmed = actionId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    public enum ShortcutContext {
        GLOBAL(3),
        FOCUSED_PANE(2),
        CONTROL_DEFAULT(1);

        private final int priority;

        ShortcutContext(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return priority;
        }
    }

    public record ShortcutBinding(
        String shortcut,
        ShortcutContext context,
        String actionId,
        boolean overrideSafe,
        boolean safetyCritical
    ) {
        public ShortcutBinding {
            shortcut = normalizeShortcutValue(shortcut);
            context = context == null ? ShortcutContext.GLOBAL : context;
            actionId = normalizeActionId(actionId);
        }

        public ShortcutBinding normalized() {
            return new ShortcutBinding(shortcut, context, actionId, overrideSafe, safetyCritical);
        }

        private static String normalizeShortcutValue(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("Shortcut cannot be null");
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Shortcut cannot be blank");
            }
            return trimmed.toUpperCase();
        }

        private static String normalizeActionId(String raw) {
            if (raw == null) {
                throw new IllegalArgumentException("Action id cannot be null");
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Action id cannot be blank");
            }
            return trimmed;
        }
    }

    public record RegistrationResult(
        boolean accepted,
        String reason,
        ShortcutBinding effectiveBinding
    ) {
        public static RegistrationResult accepted(ShortcutBinding binding) {
            return new RegistrationResult(true, "", binding);
        }

        public static RegistrationResult rejected(String reason, ShortcutBinding existingBinding) {
            return new RegistrationResult(false, reason == null ? "conflict" : reason, existingBinding);
        }
    }

    public record ShortcutConflict(
        String shortcut,
        ShortcutContext existingContext,
        String existingActionId,
        ShortcutContext incomingContext,
        String incomingActionId,
        String reason
    ) {
    }

    public record ShortcutAlias(
        String displayShortcut,
        String aliasToken,
        String canonicalToken,
        String actionLabel
    ) {
        public ShortcutAlias {
            displayShortcut = normalizeDisplay(displayShortcut);
            aliasToken = ShortcutBinding.normalizeShortcutValue(aliasToken);
            canonicalToken = ShortcutBinding.normalizeShortcutValue(canonicalToken);
            actionLabel = normalizeDisplay(actionLabel);
        }

        private static String normalizeDisplay(String value) {
            if (value == null) {
                return "";
            }
            return value.trim();
        }
    }

    private static Map<String, String> buildAliasLookup(List<ShortcutAlias> aliases) {
        Map<String, String> lookup = new LinkedHashMap<>();
        for (ShortcutAlias alias : aliases) {
            if (alias == null) {
                continue;
            }
            lookup.put(alias.aliasToken(), alias.canonicalToken());
        }
        return Map.copyOf(lookup);
    }
}
