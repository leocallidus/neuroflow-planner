package com.example.neuroflowplanner.ui.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persistable runtime state for sidebar navigation UX.
 */
public final class SidebarNavState {
    private final Set<String> expandedSectionIds;
    private final Set<String> favoriteActionIds;
    private final List<String> recentActionIds;

    public SidebarNavState(
        Set<String> expandedSectionIds,
        Set<String> favoriteActionIds,
        List<String> recentActionIds
    ) {
        this.expandedSectionIds = normalizeSet(expandedSectionIds);
        this.favoriteActionIds = normalizeSet(favoriteActionIds);
        this.recentActionIds = normalizeRecent(recentActionIds);
    }

    public static SidebarNavState empty() {
        return new SidebarNavState(Set.of(), Set.of(), List.of());
    }

    public Set<String> expandedSectionIds() {
        return expandedSectionIds;
    }

    public Set<String> favoriteActionIds() {
        return favoriteActionIds;
    }

    public List<String> recentActionIds() {
        return recentActionIds;
    }

    public boolean isSectionExpanded(String sectionId) {
        String normalized = normalizeToken(sectionId);
        return normalized != null && expandedSectionIds.contains(normalized);
    }

    public boolean isFavoriteAction(String actionId) {
        String normalized = normalizeToken(actionId);
        return normalized != null && favoriteActionIds.contains(normalized);
    }

    public SidebarNavState withSectionExpanded(String sectionId, boolean expanded) {
        String normalized = normalizeToken(sectionId);
        if (normalized == null) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(expandedSectionIds);
        if (expanded) {
            updated.add(normalized);
        } else {
            updated.remove(normalized);
        }
        return new SidebarNavState(updated, favoriteActionIds, recentActionIds);
    }

    public SidebarNavState withFavoriteAction(String actionId, boolean favorite) {
        return withFavoriteAction(actionId, favorite, Integer.MAX_VALUE);
    }

    public SidebarNavState withFavoriteAction(String actionId, boolean favorite, int maxFavorites) {
        String normalized = normalizeToken(actionId);
        if (normalized == null) {
            return this;
        }
        int safeMaxFavorites = Math.max(1, maxFavorites);
        LinkedHashSet<String> updated = new LinkedHashSet<>(favoriteActionIds);
        if (favorite) {
            updated.remove(normalized);
            updated.add(normalized);
            while (updated.size() > safeMaxFavorites) {
                String oldest = updated.iterator().next();
                updated.remove(oldest);
            }
        } else {
            updated.remove(normalized);
        }
        return new SidebarNavState(expandedSectionIds, updated, recentActionIds);
    }

    public SidebarNavState withRecordedRecentAction(String actionId, int maxRecent) {
        String normalized = normalizeToken(actionId);
        if (normalized == null) {
            return this;
        }
        int safeMaxRecent = Math.max(1, maxRecent);
        List<String> updatedRecent = new ArrayList<>();
        updatedRecent.add(normalized);
        for (String existing : recentActionIds) {
            if (normalized.equals(existing)) {
                continue;
            }
            updatedRecent.add(existing);
            if (updatedRecent.size() >= safeMaxRecent) {
                break;
            }
        }
        return new SidebarNavState(expandedSectionIds, favoriteActionIds, updatedRecent);
    }

    private static Set<String> normalizeSet(Set<String> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : source) {
            String normalized = normalizeToken(raw);
            if (normalized != null) {
                out.add(normalized);
            }
        }
        if (out.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(out);
    }

    private static List<String> normalizeRecent(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String raw : source) {
            String normalized = normalizeToken(raw);
            if (normalized != null) {
                deduped.add(normalized);
            }
        }
        if (deduped.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(deduped));
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
