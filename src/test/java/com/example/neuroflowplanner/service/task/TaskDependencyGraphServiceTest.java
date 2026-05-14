package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskDependencyGraphService Tests")
class TaskDependencyGraphServiceTest {

    private final TaskDependencyGraphService graphService = new TaskDependencyGraphService();

    @Test
    @DisplayName("Builds blocker -> dependents adjacency list from normalized edges")
    void buildsAdjacencyList() {
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("task-A", "task-B"),
                new TaskDependencyEdge("task-A", "task-C"),
                new TaskDependencyEdge("task-D", "task-C")
        );

        Map<String, List<String>> adjacency = graphService.buildAdjacencyList(edges);

        assertEquals(List.of("task-A"), adjacency.get("task-B"));
        assertEquals(List.of("task-A", "task-D"), adjacency.get("task-C"));
        assertTrue(adjacency.containsKey("task-A"));
        assertTrue(adjacency.containsKey("task-D"));
    }

    @Test
    @DisplayName("wouldCreateCycle detects transitive cycles and self-loop")
    void wouldCreateCycleDetectsCycles() {
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("A", "B"), // B -> A
                new TaskDependencyEdge("B", "C")  // C -> B
        );

        assertTrue(graphService.wouldCreateCycle(edges, "C", "A"));
        assertTrue(graphService.wouldCreateCycle(edges, "A", "A"));
        assertFalse(graphService.wouldCreateCycle(edges, "C", "D"));
    }

    @Test
    @DisplayName("Topological order returns blockers before dependents")
    void topologicalOrderRespectsDependencies() {
        List<String> taskIds = List.of("A", "B", "C", "D");
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("A", "B"), // B before A
                new TaskDependencyEdge("B", "C"), // C before B
                new TaskDependencyEdge("D", "B")  // B before D
        );

        List<String> ordered = graphService.topologicalOrder(taskIds, edges);

        assertBefore(ordered, "C", "B");
        assertBefore(ordered, "B", "A");
        assertBefore(ordered, "B", "D");
    }

    @Test
    @DisplayName("Topological order throws domain error when graph has cycle")
    void topologicalOrderThrowsOnCycle() {
        List<TaskDependencyEdge> cyclic = List.of(
                new TaskDependencyEdge("A", "B"),
                new TaskDependencyEdge("B", "A")
        );

        TaskDependencyException exception = assertThrows(
                TaskDependencyException.class,
                () -> graphService.topologicalOrder(cyclic)
        );
        assertEquals(ErrorCode.TASK_DEPENDENCY_CYCLE, exception.errorCode());
    }

    private static void assertBefore(List<String> order, String first, String second) {
        int firstIndex = order.indexOf(first);
        int secondIndex = order.indexOf(second);
        assertTrue(firstIndex >= 0, "Missing node in order: " + first);
        assertTrue(secondIndex >= 0, "Missing node in order: " + second);
        assertTrue(firstIndex < secondIndex,
                () -> "Expected " + first + " before " + second + ", actual order: " + order);
    }
}
