package com.example.neuroflowplanner.service.task;

import com.example.neuroflowplanner.error.ErrorCode;
import com.example.neuroflowplanner.model.TaskDependencyEdge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Core graph algorithms for normalized task dependencies.
 * <p>
 * Edge semantics in {@link TaskDependencyEdge}: dependent -> blocker.
 * For DAG traversal and topological sorting we build adjacency as blocker -> dependents.
 */
public class TaskDependencyGraphService {

    public Map<String, List<String>> buildAdjacencyList(Collection<TaskDependencyEdge> edges) {
        Map<String, Set<String>> adjacency = new TreeMap<>();
        for (TaskDependencyEdge edge : sanitizeEdges(edges)) {
            adjacency.computeIfAbsent(edge.blockerTaskId(), key -> new TreeSet<>())
                    .add(edge.dependentTaskId());
            adjacency.computeIfAbsent(edge.dependentTaskId(), key -> new TreeSet<>());
        }

        Map<String, List<String>> result = new TreeMap<>();
        for (Map.Entry<String, Set<String>> entry : adjacency.entrySet()) {
            result.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    public boolean wouldCreateCycle(
            Collection<TaskDependencyEdge> existingEdges,
            String dependentTaskId,
            String blockerTaskId
    ) {
        String dependentId = normalizeId(dependentTaskId);
        String blockerId = normalizeId(blockerTaskId);
        if (dependentId == null || blockerId == null) {
            return false;
        }
        if (dependentId.equals(blockerId)) {
            return true;
        }

        Map<String, List<String>> adjacency = buildAdjacencyList(existingEdges);
        Deque<String> stack = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        stack.push(dependentId);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            if (blockerId.equals(current)) {
                return true;
            }
            for (String dependent : adjacency.getOrDefault(current, List.of())) {
                stack.push(dependent);
            }
        }
        return false;
    }

    public List<String> topologicalOrder(Collection<TaskDependencyEdge> edges) {
        return topologicalOrder(List.of(), edges);
    }

    public List<String> topologicalOrder(Collection<String> taskIds, Collection<TaskDependencyEdge> edges) {
        Set<String> nodeIds = new LinkedHashSet<>();
        if (taskIds != null) {
            for (String taskId : taskIds) {
                String normalized = normalizeId(taskId);
                if (normalized != null) {
                    nodeIds.add(normalized);
                }
            }
        }

        List<TaskDependencyEdge> normalizedEdges = sanitizeEdges(edges);
        for (TaskDependencyEdge edge : normalizedEdges) {
            nodeIds.add(edge.blockerTaskId());
            nodeIds.add(edge.dependentTaskId());
        }

        Map<String, Set<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodeIds) {
            adjacency.put(nodeId, new LinkedHashSet<>());
            inDegree.put(nodeId, 0);
        }

        for (TaskDependencyEdge edge : normalizedEdges) {
            Set<String> dependents = adjacency.computeIfAbsent(edge.blockerTaskId(), key -> new LinkedHashSet<>());
            if (dependents.add(edge.dependentTaskId())) {
                inDegree.put(edge.dependentTaskId(), inDegree.getOrDefault(edge.dependentTaskId(), 0) + 1);
                inDegree.putIfAbsent(edge.blockerTaskId(), inDegree.getOrDefault(edge.blockerTaskId(), 0));
            }
        }

        PriorityQueue<String> queue = new PriorityQueue<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        List<String> ordered = new ArrayList<>(inDegree.size());
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            ordered.add(nodeId);
            for (String dependentId : adjacency.getOrDefault(nodeId, Set.of())) {
                int nextInDegree = inDegree.computeIfPresent(dependentId, (id, degree) -> degree - 1);
                if (nextInDegree == 0) {
                    queue.offer(dependentId);
                }
            }
        }

        if (ordered.size() != inDegree.size()) {
            Set<String> unresolved = new TreeSet<>(inDegree.keySet());
            unresolved.removeAll(ordered);
            throw new TaskDependencyException(
                    ErrorCode.TASK_DEPENDENCY_CYCLE,
                    "Task dependency graph contains a cycle",
                    Map.of("unresolvedTaskIds", String.join(",", unresolved)));
        }
        return List.copyOf(ordered);
    }

    private List<TaskDependencyEdge> sanitizeEdges(Collection<TaskDependencyEdge> edges) {
        if (edges == null || edges.isEmpty()) {
            return List.of();
        }
        List<TaskDependencyEdge> normalized = new ArrayList<>(edges.size());
        for (TaskDependencyEdge edge : edges) {
            if (edge == null) {
                continue;
            }
            String dependentId = normalizeId(edge.dependentTaskId());
            String blockerId = normalizeId(edge.blockerTaskId());
            if (dependentId == null || blockerId == null) {
                continue;
            }
            normalized.add(new TaskDependencyEdge(dependentId, blockerId));
        }
        return normalized;
    }

    private String normalizeId(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
