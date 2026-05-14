package com.example.neuroflowplanner.service.dailyreview;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.model.CriticalPathResult;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DailyReviewBuilderTest {

    @Test
    void buildsDeterministicSnapshotWithoutAi() {
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);

        Task overdue = new Task("overdue", "Fix billing export", "", reviewDate.minusDays(1), 8);
        overdue.setSmartPriority(0.9);
        overdue.setTags("finance, ops");

        Task dueToday = new Task("today", "Prepare team sync", "", reviewDate, 5);
        dueToday.setSmartPriority(0.7);
        dueToday.setStartDate(reviewDate);
        dueToday.setStartTime(LocalTime.of(10, 0));
        dueToday.setDeadlineTime(LocalTime.of(11, 30));
        dueToday.setTags("team");

        Task dueSoon = new Task("soon", "Renew SSL cert", "", reviewDate.plusDays(2), 6);
        dueSoon.setSmartPriority(0.6);

        TaskApplicationService taskService = new StubTaskService(List.of(overdue, dueToday, dueSoon));
        DailyReviewWorkHoursProvider workHoursProvider = date -> List.of(new DailyReviewWorkInterval(
                LocalDateTime.of(date, LocalTime.of(9, 0)),
                LocalDateTime.of(date, LocalTime.of(18, 0)),
                540,
                true,
                "09:00-18:00"
        ));
        List<TimeSession> sessions = List.of(
                new TimeSession("session-1", "today", LocalDateTime.of(reviewDate, LocalTime.of(14, 0)), 50)
        );

        DailyReviewBuilder builder = new DailyReviewBuilder(taskService, () -> sessions, workHoursProvider);
        DailyReviewSnapshot snapshot = builder.buildForDate(reviewDate);

        assertEquals(reviewDate, snapshot.reviewDate());
        assertEquals(3, snapshot.activeTaskCount());
        assertEquals(1, snapshot.overdueTaskCount());
        assertEquals(1, snapshot.tasksDueTodayCount());
        assertEquals(2, snapshot.upcomingTaskCount());
        assertEquals(50, snapshot.trackedMinutesToday());
        assertEquals(DailyReviewSummarySource.FALLBACK, snapshot.summary().source());
        assertEquals("Fix billing export", snapshot.overdueItems().getFirst().title());
        assertEquals("Prepare team sync", snapshot.upcomingItems().getFirst().title());
        assertEquals("09:00-18:00", snapshot.workIntervals().getFirst().label());
        assertEquals(2, snapshot.knownTimeBlocks().size());
        assertEquals(3, snapshot.freeWindows().size());
        assertEquals("09:00-10:00", snapshot.freeWindows().getFirst().label());
        assertEquals(DailyReviewWindowSuitability.DEEP_WORK, snapshot.freeWindows().get(1).suitability());
        assertTrue(snapshot.focusRecommendation().available());
        assertTrue(snapshot.hasFreeWindows());
        assertFalse(snapshot.approximateFreeWindows());
    }

    @Test
    void overdueItemsAreDetectedAndSortedByDeadlineThenPriority() {
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);

        Task oldest = new Task("oldest", "A oldest", "", reviewDate.minusDays(3), 2);
        oldest.setSmartPriority(0.2);

        Task sameDayLowerPriority = new Task("same-low", "C low", "", reviewDate.minusDays(1), 3);
        sameDayLowerPriority.setSmartPriority(0.4);

        Task sameDayHigherPriority = new Task("same-high", "B high", "", reviewDate.minusDays(1), 6);
        sameDayHigherPriority.setSmartPriority(0.9);

        DailyReviewBuilder builder = new DailyReviewBuilder(
                new StubTaskService(List.of(sameDayLowerPriority, sameDayHigherPriority, oldest)),
                List::of,
                date -> List.of()
        );

        DailyReviewSnapshot snapshot = builder.buildForDate(reviewDate);

        assertEquals(3, snapshot.overdueItems().size());
        assertEquals("A oldest", snapshot.overdueItems().get(0).title());
        assertEquals("B high", snapshot.overdueItems().get(1).title());
        assertEquals("C low", snapshot.overdueItems().get(2).title());
    }

    @Test
    void upcomingDeadlinesAreOrderedBySoonestThenPriority() {
        LocalDate reviewDate = LocalDate.of(2026, 3, 10);

        Task todayLater = new Task("today-late", "Today later", "", reviewDate, 3);
        todayLater.setDeadlineTime(LocalTime.of(16, 0));
        todayLater.setSmartPriority(0.4);

        Task todayEarlier = new Task("today-early", "Today early", "", reviewDate, 1);
        todayEarlier.setDeadlineTime(LocalTime.of(10, 0));
        todayEarlier.setSmartPriority(0.2);

        Task tomorrowHigh = new Task("tomorrow-high", "Tomorrow high", "", reviewDate.plusDays(1), 7);
        tomorrowHigh.setSmartPriority(0.9);

        Task tomorrowLow = new Task("tomorrow-low", "Tomorrow low", "", reviewDate.plusDays(1), 2);
        tomorrowLow.setSmartPriority(0.1);

        DailyReviewBuilder builder = new DailyReviewBuilder(
                new StubTaskService(List.of(tomorrowLow, todayLater, tomorrowHigh, todayEarlier)),
                List::of,
                date -> List.of()
        );

        DailyReviewSnapshot snapshot = builder.buildForDate(reviewDate);

        assertEquals(4, snapshot.upcomingItems().size());
        assertEquals("Today early", snapshot.upcomingItems().get(0).title());
        assertEquals("Today later", snapshot.upcomingItems().get(1).title());
        assertEquals("Tomorrow high", snapshot.upcomingItems().get(2).title());
        assertEquals("Tomorrow low", snapshot.upcomingItems().get(3).title());
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
