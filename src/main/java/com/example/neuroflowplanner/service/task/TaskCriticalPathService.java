package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.CriticalPathEdgeMetrics;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.CriticalPathScopeMode;
import com.example.neuroflowplanner.model.CriticalPathTaskMetrics;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskDependencyEdge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Critical-path analytics for task dependency DAGs.
 * <p>
 * Node weight v1 is task complexity.
 */
public class TaskCriticalPathService {
    private final TaskDependencyGraphService graphService;

    public TaskCriticalPathService() {
        this(new TaskDependencyGraphService());
    }

    TaskCriticalPathService(TaskDependencyGraphService graphService) {
        this.graphService = graphService;
    }

    public CriticalPathResult computeFullGraph(Collection<Task> tasks, Collection<TaskDependencyEdge> edges) {
        return compute(CriticalPathScopeMode.FULL_GRAPH, null, tasks, edges);
    }

    public CriticalPathResult computeForRootTask(String rootTaskId, Collection<Task> tasks, Collection<TaskDependencyEdge> edges) {
        String normalizedRoot = normalizeTaskId(rootTaskId);
        if (normalizedRoot == null) {
            throw new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE,
                    "Root task id is required for scoped critical-path calculation",
                    Map.of("rootTaskId", rootTaskId == null ? "" : rootTaskId));
        }
        return compute(CriticalPathScopeMode.ROOT_TASK, normalizedRoot, tasks, edges);
    }

    private CriticalPathResult compute(
            CriticalPathScopeMode scopeMode,
            String scopeRootTaskId,
            Collection<Task> tasks,
            Collection<TaskDependencyEdge> edges
    ) {
        Map<String, Task> tasksById = indexTasks(tasks);
        if (tasksById.isEmpty()) {
            return CriticalPathResult.empty(scopeMode, scopeRootTaskId);
        }

        Set<String> scopedTaskIds = switch (scopeMode) {
            case FULL_GRAPH -> new LinkedHashSet<>(tasksById.keySet());
            case ROOT_TASK -> resolveRootScopeTaskIds(scopeRootTaskId, tasksById);
        };
        if (scopedTaskIds.isEmpty()) {
            return CriticalPathResult.empty(scopeMode, scopeRootTaskId);
        }

        List<TaskDependencyEdge> normalizedEdges = normalizeAndValidateEdges(edges, tasksById);
        List<TaskDependencyEdge> scopedEdges = filterEdgesByScope(normalizedEdges, scopedTaskIds);
        List<String> topologicalOrder = graphService.topologicalOrder(scopedTaskIds, scopedEdges);
        if (topologicalOrder.isEmpty()) {
            return CriticalPathResult.empty(scopeMode, scopeRootTaskId);
        }

        Map<String, Set<String>> blockersByDependent = buildBlockersByDependent(topologicalOrder, scopedEdges);
        Map<String, Set<String>> dependentsByBlocker = buildDependentsByBlocker(topologicalOrder, scopedEdges);

        Map<String, Integer> durations = computeDurations(topologicalOrder, tasksById);
        Map<String, Integer> earliestStart = new HashMap<>();
        Map<String, Integer> earliestFinish = new HashMap<>();
        Map<String, String> chainPredecessor = new HashMap<>();
        forwardPass(topologicalOrder, blockersByDependent, durations, earliestStart, earliestFinish, chainPredecessor);

        int projectDuration = topologicalOrder.stream()
                .mapToInt(taskId -> earliestFinish.getOrDefault(taskId, 0))
                .max()
                .orElse(0);

        Map<String, Integer> latestStart = new HashMap<>();
        Map<String, Integer> latestFinish = new HashMap<>();
        backwardPass(topologicalOrder, dependentsByBlocker, durations, latestStart, latestFinish, projectDuration);

        List<CriticalPathTaskMetrics> taskMetrics = new ArrayList<>(topologicalOrder.size());
        int criticalTaskCount = 0;
        for (String taskId : topologicalOrder) {
            int es = earliestStart.getOrDefault(taskId, 0);
            int ef = earliestFinish.getOrDefault(taskId, durations.getOrDefault(taskId, 0));
            int ls = latestStart.getOrDefault(taskId, es);
            int lf = latestFinish.getOrDefault(taskId, ef);
            int slack = Math.max(0, ls - es);
            boolean critical = slack == 0;
            if (critical) {
                criticalTaskCount++;
            }
            taskMetrics.add(new CriticalPathTaskMetrics(
                    taskId,
                    durations.getOrDefault(taskId, 0),
                    es,
                    ef,
                    ls,
                    lf,
                    slack,
                    critical));
        }

        Map<String, CriticalPathTaskMetrics> taskMetricsById = new HashMap<>(taskMetrics.size());
        for (CriticalPathTaskMetrics metrics : taskMetrics) {
            taskMetricsById.put(metrics.taskId(), metrics);
        }

        List<TaskDependencyEdge> sortedEdges = new ArrayList<>(scopedEdges);
        sortedEdges.sort(Comparator.comparing(TaskDependencyEdge::blockerTaskId)
                .thenComparing(TaskDependencyEdge::dependentTaskId));
        List<CriticalPathEdgeMetrics> edgeMetrics = new ArrayList<>(sortedEdges.size());
        int criticalEdgeCount = 0;
        for (TaskDependencyEdge edge : sortedEdges) {
            CriticalPathTaskMetrics blockerMetrics = taskMetricsById.get(edge.blockerTaskId());
            CriticalPathTaskMetrics dependentMetrics = taskMetricsById.get(edge.dependentTaskId());
            if (blockerMetrics == null || dependentMetrics == null) {
                continue;
            }
            int slack = Math.max(0, dependentMetrics.latestStart() - blockerMetrics.earliestFinish());
            boolean critical = slack == 0;
            if (critical) {
                criticalEdgeCount++;
            }
            edgeMetrics.add(new CriticalPathEdgeMetrics(
                    edge.blockerTaskId(),
                    edge.dependentTaskId(),
                    slack,
                    critical));
        }

        List<String> criticalChainTaskIds = buildCriticalChain(topologicalOrder, earliestFinish, projectDuration, chainPredecessor);

        return new CriticalPathResult(
                scopeMode,
                scopeRootTaskId,
                projectDuration,
                topologicalOrder.size(),
                edgeMetrics.size(),
                criticalTaskCount,
                criticalEdgeCount,
                topologicalOrder,
                criticalChainTaskIds,
                taskMetrics,
                edgeMetrics);
    }

    private Map<String, Task> indexTasks(Collection<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Map.of();
        }
        Map<String, Task> byId = new LinkedHashMap<>();
        for (Task task : tasks) {
            if (task == null) {
                continue;
            }
            String taskId = normalizeTaskId(task.getId());
            if (taskId == null) {
                continue;
            }
            byId.put(taskId, task);
        }
        return Map.copyOf(byId);
    }

    private Set<String> resolveRootScopeTaskIds(String rootTaskId, Map<String, Task> tasksById) {
        if (!tasksById.containsKey(rootTaskId)) {
            throw new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE,
                    "Root task does not exist",
                    Map.of("rootTaskId", rootTaskId));
        }

        Map<String, Set<String>> childrenByParent = new HashMap<>();
        for (Task task : tasksById.values()) {
            String taskId = normalizeTaskId(task.getId());
            String parentId = normalizeTaskId(task.getParentId());
            if (taskId == null || parentId == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(parentId, key -> new TreeSet<>()).add(taskId);
        }

        Set<String> scopedTaskIds = new LinkedHashSet<>();
        Deque<String> stack = new LinkedList<>();
        stack.push(rootTaskId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!scopedTaskIds.add(current)) {
                continue;
            }
            List<String> children = new ArrayList<>(childrenByParent.getOrDefault(current, Set.of()));
            Collections.reverse(children);
            for (String child : children) {
                stack.push(child);
            }
        }
        return scopedTaskIds;
    }

    private List<TaskDependencyEdge> normalizeAndValidateEdges(Collection<TaskDependencyEdge> edges, Map<String, Task> tasksById) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<TaskDependencyEdge> normalized = new ArrayList<>();
        for (TaskDependencyEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String dependentId = normalizeTaskId(edge.dependentTaskId());
            String blockerId = normalizeTaskId(edge.blockerTaskId());
            if (dependentId == null || blockerId == null) {
                continue;
            }
            if (!tasksById.containsKey(dependentId) || !tasksById.containsKey(blockerId)) {
                throw new TaskDependencyException(
                        ErrorCode.TASK_DEPENDENCY_INVALID_REFERENCE,
                        "Dependency edge contains missing task reference",
                        Map.of(
                                "dependentTaskId", dependentId,
                                "blockerTaskId", blockerId));
            }
            normalized.add(new TaskDependencyEdge(dependentId, blockerId));
        }
        return normalized;
    }

    private List<TaskDependencyEdge> filterEdgesByScope(List<TaskDependencyEdge> edges, Set<String> scopedTaskIds) {
        if (edges == null || edges.isEmpty() || scopedTaskIds == null || scopedTaskIds.isEmpty()) {
            return List.of();
        }
        List<TaskDependencyEdge> scoped = new ArrayList<>();
        for (TaskDependencyEdge edge : edges) {
            if (scopedTaskIds.contains(edge.dependentTaskId()) && scopedTaskIds.contains(edge.blockerTaskId())) {
                scoped.add(edge);
            }
        }
        return scoped;
    }

    private Map<String, Set<String>> buildBlockersByDependent(List<String> orderedTaskIds, List<TaskDependencyEdge> edges) {
        Map<String, Set<String>> blockersByDependent = new HashMap<>();
        for (String taskId : orderedTaskIds) {
            blockersByDependent.put(taskId, new TreeSet<>());
        }
        for (TaskDependencyEdge edge : edges) {
            blockersByDependent.computeIfAbsent(edge.dependentTaskId(), key -> new TreeSet<>())
                    .add(edge.blockerTaskId());
        }
        return blockersByDependent;
    }

    private Map<String, Set<String>> buildDependentsByBlocker(List<String> orderedTaskIds, List<TaskDependencyEdge> edges) {
        Map<String, Set<String>> dependentsByBlocker = new HashMap<>();
        for (String taskId : orderedTaskIds) {
            dependentsByBlocker.put(taskId, new TreeSet<>());
        }
        for (TaskDependencyEdge edge : edges) {
            dependentsByBlocker.computeIfAbsent(edge.blockerTaskId(), key -> new TreeSet<>())
                    .add(edge.dependentTaskId());
        }
        return dependentsByBlocker;
    }

    private Map<String, Integer> computeDurations(List<String> orderedTaskIds, Map<String, Task> tasksById) {
        Map<String, Integer> durations = new HashMap<>();
        for (String taskId : orderedTaskIds) {
            Task task = tasksById.get(taskId);
            int duration = task == null ? 1 : Math.max(1, task.getComplexity());
            durations.put(taskId, duration);
        }
        return durations;
    }

    private void forwardPass(
            List<String> orderedTaskIds,
            Map<String, Set<String>> blockersByDependent,
            Map<String, Integer> durations,
            Map<String, Integer> earliestStart,
            Map<String, Integer> earliestFinish,
            Map<String, String> chainPredecessor
    ) {
        for (String taskId : orderedTaskIds) {
            int start = 0;
            String bestPredecessor = null;
            for (String blockerId : blockersByDependent.getOrDefault(taskId, Set.of())) {
                int blockerFinish = earliestFinish.getOrDefault(blockerId, 0);
                if (blockerFinish > start || (blockerFinish == start && shouldReplacePredecessor(blockerId, bestPredecessor))) {
                    start = blockerFinish;
                    bestPredecessor = blockerId;
                }
            }
            int finish = start + durations.getOrDefault(taskId, 1);
            earliestStart.put(taskId, start);
            earliestFinish.put(taskId, finish);
            chainPredecessor.put(taskId, bestPredecessor);
        }
    }

    private void backwardPass(
            List<String> orderedTaskIds,
            Map<String, Set<String>> dependentsByBlocker,
            Map<String, Integer> durations,
            Map<String, Integer> latestStart,
            Map<String, Integer> latestFinish,
            int projectDuration
    ) {
        for (int i = orderedTaskIds.size() - 1; i >= 0; i--) {
            String taskId = orderedTaskIds.get(i);
            Set<String> dependents = dependentsByBlocker.getOrDefault(taskId, Set.of());
            int finish = projectDuration;
            if (!dependents.isEmpty()) {
                finish = dependents.stream()
                        .mapToInt(depId -> latestStart.getOrDefault(depId, projectDuration))
                        .min()
                        .orElse(projectDuration);
            }
            int start = finish - durations.getOrDefault(taskId, 1);
            latestStart.put(taskId, start);
            latestFinish.put(taskId, finish);
        }
    }

    private List<String> buildCriticalChain(
            List<String> orderedTaskIds,
            Map<String, Integer> earliestFinish,
            int projectDuration,
            Map<String, String> chainPredecessor
    ) {
        if (orderedTaskIds.isEmpty()) {
            return List.of();
        }
        String endTaskId = orderedTaskIds.stream()
                .filter(taskId -> earliestFinish.getOrDefault(taskId, 0) == projectDuration)
                .min(String::compareTo)
                .orElse(orderedTaskIds.get(orderedTaskIds.size() - 1));

        LinkedList<String> chain = new LinkedList<>();
        String current = endTaskId;
        while (current != null) {
            chain.addFirst(current);
            current = chainPredecessor.get(current);
        }
        return List.copyOf(chain);
    }

    private boolean shouldReplacePredecessor(String candidate, String existing) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        return candidate.compareTo(existing) < 0;
    }

    private String normalizeTaskId(String rawTaskId) {
        if (rawTaskId == null) {
            return null;
        }
        String normalized = rawTaskId.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
