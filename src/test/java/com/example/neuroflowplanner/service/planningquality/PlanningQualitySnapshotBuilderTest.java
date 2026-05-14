package com.example.neuroflowplanner.service.planningquality;

import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningQualitySnapshotBuilderTest {

    @Test
    void buildsDeterministicSnapshotWithoutAi() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 7);

        Task activeEstimated = new Task("active-estimated", "Подготовить квартальный отчёт", "", LocalDate.of(2026, 3, 3), 7);
        activeEstimated.setStartDate(LocalDate.of(2026, 3, 3));
        activeEstimated.setStartTime(LocalTime.of(9, 0));
        activeEstimated.setDeadlineTime(LocalTime.of(11, 0));

        Task completedTracked = new Task("completed-tracked", "Закрыть sync action items", "", LocalDate.of(2026, 3, 4), 4);
        completedTracked.setStartDate(LocalDate.of(2026, 3, 4));
        completedTracked.setStartTime(LocalTime.of(14, 0));
        completedTracked.setDeadlineTime(LocalTime.of(15, 0));
        completedTracked.setCompleted(true);
        completedTracked.setCompletedDate(LocalDate.of(2026, 3, 4));

        Task activeScheduled = new Task("active-scheduled", "Подготовить интервью", "", LocalDate.of(2026, 3, 6), 5);
        activeScheduled.setStartDate(LocalDate.of(2026, 3, 6));
        activeScheduled.setStartTime(LocalTime.of(10, 30));

        Task archived = new Task("archived", "Старый архив", "", LocalDate.of(2026, 3, 5), 2);
        archived.setArchived(true);

        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "active-estimated", LocalDateTime.of(2026, 3, 3, 9, 15), 95),
                new TimeSession("s2", "completed-tracked", LocalDateTime.of(2026, 3, 4, 14, 5), 55),
                new TimeSession("s3", "completed-tracked", LocalDateTime.of(2026, 3, 5, 11, 0), 25),
                new TimeSession("s4", "other", LocalDateTime.of(2026, 3, 7, 16, 0), 30)
        );

        PlanningQualitySnapshotBuilder builder = new PlanningQualitySnapshotBuilder(
                new StubTaskService(List.of(activeEstimated, completedTracked, activeScheduled, archived)),
                () -> sessions
        );

        PlanningQualitySnapshot snapshot = builder.buildForPeriod(start, end);

        assertEquals(start, snapshot.periodStart());
        assertEquals(end, snapshot.periodEnd());
        assertEquals(2, snapshot.activeTaskCount());
        assertEquals(1, snapshot.completedTaskCount());
        assertEquals(3, snapshot.estimatedTaskCount());
        assertEquals(3, snapshot.scheduledTaskCount());
        assertEquals(3, snapshot.trackedTaskCount());
        assertEquals(4, snapshot.trackedSessionCount());
        assertEquals(PlanningQualitySummarySource.FALLBACK, snapshot.summary().source());
        assertTrue(snapshot.summary().available());
        assertTrue(snapshot.accuracyMetric().available());
        assertTrue(snapshot.rescheduleMetric().available());
        assertTrue(snapshot.rhythmMetric().available());
        assertTrue(snapshot.hasDeterministicMetrics());
        assertEquals(3, snapshot.accuracyMetric().estimatedTaskCount());
        assertEquals(2, snapshot.accuracyMetric().comparableTaskCount());
        assertEquals(3, snapshot.rescheduleMetric().analyzedTaskCount());
        assertEquals(1, snapshot.rescheduleMetric().rescheduledTaskCount());
        assertTrue(snapshot.rescheduleMetric().approximate());
        assertEquals(RhythmStabilityBand.MODERATE, snapshot.rhythmMetric().band());
        assertTrue(snapshot.rhythmMetric().score() > 0.0);
        assertTrue(snapshot.hasDayAggregates());
        assertEquals(7, snapshot.dayAggregates().size());
        assertEquals(LocalDate.of(2026, 3, 3), snapshot.dayAggregates().get(2).date());
        assertEquals(1, snapshot.dayAggregates().get(2).scheduledTaskCount());
        assertEquals(95L, snapshot.dayAggregates().get(2).trackedMinutes());
        assertFalse(snapshot.hasRecommendations());
        assertFalse(snapshot.limitedData());
    }

    @Test
    void marksSnapshotAsLimitedWhenHistoryIsSparse() {
        LocalDate end = LocalDate.of(2026, 3, 7);
        Task task = new Task("single", "Одна задача", "", LocalDate.of(2026, 3, 7), 3);

        PlanningQualitySnapshotBuilder builder = new PlanningQualitySnapshotBuilder(
                new StubTaskService(List.of(task)),
                List::of
        );

        PlanningQualitySnapshot snapshot = builder.buildForPeriod(end.minusDays(6), end);

        assertTrue(snapshot.limitedData());
        assertTrue(snapshot.hasRisks());
        assertTrue(snapshot.hasRecommendations());
        assertFalse(snapshot.accuracyMetric().available());
        assertTrue(snapshot.rescheduleMetric().available());
        assertTrue(snapshot.rescheduleMetric().approximate());
        assertFalse(snapshot.rhythmMetric().available());
        assertEquals(1, snapshot.rescheduleMetric().analyzedTaskCount());
        assertEquals(0, snapshot.rescheduleMetric().rescheduledTaskCount());
        assertEquals(0, snapshot.trackedSessionCount());
        assertEquals(0, snapshot.estimatedTaskCount());
    }

    private static final class StubTaskService implements TaskApplicationService {
        private final List<Task> tasks;

        private StubTaskService(List<Task> tasks) {
            this.tasks = tasks;
        }

        @Override
        public List<Task> loadTasks() {
            return tasks;
        }

        @Override
        public void saveTask(Task task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveTasks(List<Task> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskBulkOperationResult saveTasksBulk(List<Task> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTask(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskBulkOperationResult archiveTasksBulk(List<String> taskIds, boolean includeSubtasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskBulkOperationResult deleteTasksBulk(List<String> taskIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TaskBulkOperationResult updateTaskTagsBulk(Map<String, String> tagsByTaskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void linkDependency(String dependentTaskId, String blockerTaskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveDependencies(String taskId, List<String> blockerTaskIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> loadDependencies(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TaskDependencyEdge> loadAllDependencyEdges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDependenciesForTask(String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CriticalPathResult computeCriticalPathFullGraph() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<TaskTemplate> loadAllTemplates() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveTemplate(TaskTemplate template) {
            throw new UnsupportedOperationException();
        }
    }
}
