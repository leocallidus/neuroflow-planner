package com.example.neuroflowplanner.ui.navigation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Sidebar navigation item metadata used for sorting/filtering/rendering.
 */
public record SidebarNavItem(
    String id,
    String label,
    String sectionId,
    int priority,
    String actionId,
    String icon,
    List<String> tags,
    SidebarActionTaxonomy taxonomy,
    String shortDescription,
    List<String> aliases,
    SidebarUsagePriority usagePriority,
    SidebarSurfaceHint surfaceHint
) {
    public SidebarNavItem {
        id = normalize(id, "unknown.item");
        label = normalize(label, id);
        sectionId = normalize(sectionId, "unknown");
        priority = Math.max(0, priority);
        actionId = normalize(actionId, id);
        icon = normalize(icon, "");
        tags = normalizeTags(tags);
        taxonomy = taxonomy == null ? SidebarActionTaxonomy.ADVANCED : taxonomy;
        shortDescription = normalize(shortDescription, label);
        aliases = normalizeTags(aliases);
        usagePriority = usagePriority == null ? SidebarUsagePriority.MEDIUM : usagePriority;
        surfaceHint = surfaceHint == null ? SidebarSurfaceHint.BOTH : surfaceHint;
    }

    public SidebarNavItem(
        String id,
        String label,
        String sectionId,
        int priority,
        String actionId,
        String icon,
        List<String> tags
    ) {
        this(
            id,
            label,
            sectionId,
            priority,
            actionId,
            icon,
            tags,
            SidebarActionTaxonomy.ADVANCED,
            label,
            List.of(),
            SidebarUsagePriority.MEDIUM,
            SidebarSurfaceHint.BOTH
        );
    }

    public boolean matchesQuery(String query) {
        return matchesQuery(query, null);
    }

    public boolean matchesQuery(String query, String categoryText) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = searchableText(categoryText);
        String[] tokens = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            if (!haystack.contains(token.trim().toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private String searchableText(String categoryText) {
        StringBuilder out = new StringBuilder();
        out.append(id.toLowerCase(Locale.ROOT)).append(' ')
            .append(label.toLowerCase(Locale.ROOT)).append(' ')
            .append(sectionId.toLowerCase(Locale.ROOT)).append(' ')
            .append(actionId.toLowerCase(Locale.ROOT)).append(' ')
            .append(icon.toLowerCase(Locale.ROOT)).append(' ')
            .append(taxonomy.name().toLowerCase(Locale.ROOT)).append(' ')
            .append(shortDescription.toLowerCase(Locale.ROOT)).append(' ')
            .append(usagePriority.name().toLowerCase(Locale.ROOT)).append(' ')
            .append(surfaceHint.name().toLowerCase(Locale.ROOT));
        if (categoryText != null && !categoryText.isBlank()) {
            out.append(' ').append(categoryText.toLowerCase(Locale.ROOT));
        }
        for (String tag : tags) {
            out.append(' ').append(tag.toLowerCase(Locale.ROOT));
        }
        for (String alias : aliases) {
            out.append(' ').append(alias.toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String normalize(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static List<String> normalizeTags(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String tag : source) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            deduped.add(tag.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(new ArrayList<>(deduped));
    }
}
