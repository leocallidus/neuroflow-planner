package com.example.neuroflowplanner.ui.layout.rightpanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Per-tab content policy for tabbed inspector rendering and degradation decisions.
 */
public record RightPanelTabContentPolicy(
    RightPanelInspectorTab tab,
    String label,
    String compactLabel,
    List<RightPanelInspectorSectionMapping> sectionMappings,
    Set<String> expandedSubstateIds,
    boolean summaryFirst,
    boolean localScrollOnly,
    boolean lazyHeavyContent,
    boolean heightCompactionApplied,
    int maxHeavyBlocksVisible
) {
    public RightPanelTabContentPolicy {
        tab = tab == null ? RightPanelInspectorTab.PROPERTIES : tab;
        label = normalizeText(label, tab.label());
        compactLabel = normalizeText(compactLabel, tab.compactLabel());
        sectionMappings = normalizeMappings(sectionMappings);
        expandedSubstateIds = normalizeIds(expandedSubstateIds);
        maxHeavyBlocksVisible = Math.max(1, maxHeavyBlocksVisible);
    }

    public boolean isSubstateExpanded(String substateId) {
        String normalized = normalizeToken(substateId);
        return normalized != null && expandedSubstateIds.contains(normalized);
    }

    private static List<RightPanelInspectorSectionMapping> normalizeMappings(List<RightPanelInspectorSectionMapping> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<RightPanelInspectorSectionMapping> out = new ArrayList<>();
        for (RightPanelInspectorSectionMapping mapping : source) {
            if (mapping == null || !seen.add(mapping.sectionId())) {
                continue;
            }
            out.add(mapping);
        }
        return List.copyOf(out);
    }

    private static Set<String> normalizeIds(Set<String> source) {
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

    private static String normalizeText(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }

    private static String normalizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
