package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.CriticalPathTaskMetrics;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskCriticalPathService Tests")
class TaskCriticalPathServiceTest {
    private final TaskCriticalPathService service = new TaskCriticalPathService();

    @Test
    @DisplayName("Computes deterministic critical path metrics for DAG")
    void computesCriticalPathMetricsForDag() {
        List<Task> tasks = List.of(
                task("A", 2, null),
                task("B", 3, null),
                task("C", 2, null),
                task("D", 4, null),
                task("E", 1, null)
        );
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("B", "A"), // A -> B
                new TaskDependencyEdge("C", "A"), // A -> C
                new TaskDependencyEdge("D", "B"), // B -> D
                new TaskDependencyEdge("D", "C"), // C -> D
                new TaskDependencyEdge("E", "C")  // C -> E
        );

        CriticalPathResult result = service.computeFullGraph(tasks, edges);
        Map<String, CriticalPathTaskMetrics> byTaskId = result.taskMetrics().stream()
                .collect(Collectors.toMap(CriticalPathTaskMetrics::taskId, metrics -> metrics));

        assertEquals(CriticalPathScopeMode.FULL_GRAPH, result.scopeMode());
        assertEquals(9, result.projectDuration());
        assertEquals(5, result.taskCount());
        assertEquals(5, result.edgeCount());
        assertEquals(3, result.criticalTaskCount());
        assertEquals(2, result.criticalEdgeCount());
        assertEquals(List.of("A", "B", "D"), result.criticalChainTaskIds());

        assertEquals(0, byTaskId.get("A").earliestStart());
        assertEquals(2, byTaskId.get("A").earliestFinish());
        assertEquals(0, byTaskId.get("A").totalSlack());

        assertEquals(2, byTaskId.get("B").earliestStart());
        assertEquals(5, byTaskId.get("B").earliestFinish());
        assertEquals(0, byTaskId.get("B").totalSlack());

        assertEquals(2, byTaskId.get("C").earliestStart());
        assertEquals(4, byTaskId.get("C").earliestFinish());
        assertEquals(1, byTaskId.get("C").totalSlack());

        assertEquals(5, byTaskId.get("D").earliestStart());
        assertEquals(9, byTaskId.get("D").earliestFinish());
        assertEquals(0, byTaskId.get("D").totalSlack());

        assertEquals(4, byTaskId.get("E").earliestStart());
        assertEquals(5, byTaskId.get("E").earliestFinish());
        assertEquals(4, byTaskId.get("E").totalSlack());
    }

    @Test
    @DisplayName("Respects root-task scope and filters out external dependencies")
    void computesCriticalPathForRootScope() {
        List<Task> tasks = List.of(
                task("ROOT", 1, null),
                task("A", 3, "ROOT"),
                task("B", 2, "ROOT"),
                task("X", 5, null)
        );
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("A", "B"), // inside ROOT scope
                new TaskDependencyEdge("B", "X")  // external blocker for B, excluded in ROOT scope
        );

        CriticalPathResult full = service.computeFullGraph(tasks, edges);
        CriticalPathResult scoped = service.computeForRootTask("ROOT", tasks, edges);

        assertEquals(List.of("X", "B", "A"), full.criticalChainTaskIds());
        assertEquals(10, full.projectDuration());

        assertEquals(CriticalPathScopeMode.ROOT_TASK, scoped.scopeMode());
        assertEquals("ROOT", scoped.scopeRootTaskId());
        assertEquals(3, scoped.taskCount());
        assertEquals(1, scoped.edgeCount());
        assertEquals(5, scoped.projectDuration());
        assertEquals(List.of("B", "A"), scoped.criticalChainTaskIds());
        assertTrue(scoped.topologicalOrderTaskIds().containsAll(List.of("ROOT", "A", "B")));
    }

    @Test
    @DisplayName("Uses deterministic predecessor tie-break when longest paths are equal")
    void deterministicCriticalChainOnEqualLongestPaths() {
        List<Task> tasks = List.of(
                task("A", 2, null),
                task("B", 2, null),
                task("C", 1, null)
        );
        List<TaskDependencyEdge> edges = List.of(
                new TaskDependencyEdge("C", "A"),
                new TaskDependencyEdge("C", "B")
        );

        CriticalPathResult result = service.computeFullGraph(tasks, edges);
        assertEquals(List.of("A", "C"), result.criticalChainTaskIds());
    }

    @Test
    @DisplayName("Throws TASK_DEPENDENCY_INVALID_REFERENCE for missing root scope task")
    void throwsForMissingRootScopeTask() {
        TaskDependencyException exception = assertThrows(
                TaskDependencyException.class,
                () -> service.computeForRootTask("missing-root", List.of(task("A", 1, null)), List.of())
        );
        assertEquals(ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE, exception.errorCode());
    }

    private static Task task(String id, int complexity, String parentId) {
        return new Task(
                id,
                "title-" + id,
                "",
                LocalDate.now().plusDays(5),
                complexity,
                parentId,
                "",
                ""
        );
    }
}
