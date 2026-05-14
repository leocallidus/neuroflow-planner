package com.example.neuroflowplanner.service.search;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.search.GlobalSearchResult;
import com.example.neuroflowplanner.model.search.GlobalSearchResultType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGlobalSearchServiceTest {

    @Test
    void searchAggregatesTasksAndNotesWithStableRanking() {
        Task deployTask = new Task("task-1", "Deploy API", "Release deploy api checklist", LocalDate.now().plusDays(1), 3);
        deployTask.setTags("backend,release");

        Task docsTask = new Task("task-2", "Write docs", "Update onboarding guide", LocalDate.now().plusDays(2), 2);
        Task deploySubtask = new Task("task-3", "Deploy API smoke", "Run post-deploy smoke tests", LocalDate.now().plusDays(1), 2, deployTask.getId());
        deployTask.getSubtasks().add(deploySubtask);

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("Deploy Runbook", "Runbook for deploy api production flow");
        notes.put("Random", "Nothing relevant here");

        DefaultGlobalSearchService service = new DefaultGlobalSearchService(
            () -> List.of(deployTask, docsTask),
            query -> searchNotesByQuery(notes, query),
            title -> notes.getOrDefault(title, "")
        );

        List<GlobalSearchResult> results = service.search("deploy api", 10);

        assertFalse(results.isEmpty());
        assertEquals(GlobalSearchResultType.TASK, results.get(0).type());
        assertEquals("task-1", results.get(0).id());
        assertTrue(results.stream().anyMatch(result -> "task-3".equals(result.id())));
        assertTrue(results.stream().anyMatch(
            result -> result.type() == GlobalSearchResultType.NOTE && "Deploy Runbook".equals(result.id())
        ));
    }

    @Test
    void searchUsesDeterministicTieBreakForEqualScores() {
        Task first = new Task("task-2", "Alpha", "needle in description", LocalDate.now().plusDays(1), 2);
        Task second = new Task("task-1", "Alpha", "needle in description", LocalDate.now().plusDays(1), 2);

        DefaultGlobalSearchService service = new DefaultGlobalSearchService(
            () -> List.of(first, second),
            query -> List.of(),
            title -> ""
        );

        List<GlobalSearchResult> results = service.search("needle", 10);

        assertEquals(2, results.size());
        assertEquals("task-1", results.get(0).id());
        assertEquals("task-2", results.get(1).id());
    }

    @Test
    void searchReturnsEmptyForBlankQueryAndRespectsLimit() {
        Task alpha = new Task("task-alpha", "Alpha task", "alpha body", LocalDate.now().plusDays(1), 1);
        Task beta = new Task("task-beta", "Alpha beta", "alpha words", LocalDate.now().plusDays(1), 1);

        DefaultGlobalSearchService service = new DefaultGlobalSearchService(
            () -> List.of(alpha, beta),
            query -> List.of(),
            title -> ""
        );

        assertTrue(service.search("   ", 10).isEmpty());
        assertEquals(1, service.search("alpha", 1).size());
    }

    @Test
    void searchIsStableAcrossRepeatedCallsForSameDataset() {
        Task first = new Task("task-1", "API rollout", "deploy flow with checklist", LocalDate.now().plusDays(1), 2);
        Task second = new Task("task-2", "API docs", "deploy docs for release", LocalDate.now().plusDays(2), 1);
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("API Runbook", "deploy flow and rollback");
        notes.put("Backlog", "misc");

        DefaultGlobalSearchService service = new DefaultGlobalSearchService(
            () -> List.of(first, second),
            query -> searchNotesByQuery(notes, query),
            title -> notes.getOrDefault(title, "")
        );

        List<String> firstRun = service.search("deploy", 10).stream()
            .map(result -> result.type() + ":" + result.id())
            .collect(Collectors.toList());
        List<String> secondRun = service.search("deploy", 10).stream()
            .map(result -> result.type() + ":" + result.id())
            .collect(Collectors.toList());
        List<String> thirdRun = service.search("deploy", 10).stream()
            .map(result -> result.type() + ":" + result.id())
            .collect(Collectors.toList());

        assertEquals(firstRun, secondRun);
        assertEquals(firstRun, thirdRun);
    }

    private static List<String> searchNotesByQuery(Map<String, String> notes, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return List.copyOf(notes.keySet());
        }
        List<String> matched = new ArrayList<>();
        for (Map.Entry<String, String> entry : notes.entrySet()) {
            String title = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            String content = entry.getValue() == null ? "" : entry.getValue().toLowerCase(Locale.ROOT);
            if (title.contains(normalized) || content.contains(normalized)) {
                matched.add(entry.getKey());
            }
        }
        return matched;
    }
}
