package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.model.Task;
import com.example.neuroflowplanner.model.TaskBulkOperationResult;
import com.example.neuroflowplanner.model.TaskDependencyEdge;
import com.example.neuroflowplanner.model.TaskTemplate;
import com.example.neuroflowplanner.model.TimeSession;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkHoursProvider;
import com.example.neuroflowplanner.service.dailyreview.DailyReviewWindowCalculator;
import com.example.neuroflowplanner.service.task.TaskApplicationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FocusBlockCandidateWindowCalculatorTest {

    @Test
    void calculatesCandidateWindowsForTypicalDay() {
        Task scheduled = new Task("task-1", "Deep work slot", "", LocalDate.of(2026, 3, 11), 5);
        scheduled.setStartDate(LocalDate.of(2026, 3, 11));
        scheduled.setStartTime(LocalTime.of(10, 0));
        scheduled.setDeadlineTime(LocalTime.of(11, 0));

        FocusBlockCandidateWindowCalculator calculator = new FocusBlockCandidateWindowCalculator(
                taskService(List.of(scheduled)),
                () -> List.of(new TimeSession("s-1", "task-2", LocalDateTime.of(2026, 3, 11, 13, 0), 60)),
                reviewDate -> List.of(new com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkInterval(
                        LocalDateTime.of(reviewDate, LocalTime.of(9, 0)),
                        LocalDateTime.of(reviewDate, LocalTime.of(18, 0)),
                        540,
                        false,
                        "09:00-18:00"
                )),
                new DailyReviewWindowCalculator()
        );

        List<FocusBlockCandidate> windows = calculator.calculateForDate(LocalDate.of(2026, 3, 11));

        assertEquals(3, windows.size());
        assertEquals("09:00-10:00", windows.get(0).label());
        assertEquals("11:00-13:00", windows.get(1).label());
        assertEquals("14:00-18:00", windows.get(2).label());
        assertEquals(FocusBlockType.DEEP_FOCUS, windows.get(1).type());
        assertFalse(windows.get(0).approximate());
    }

    @Test
    void fallsBackToApproximateWindowWhenWorkHoursAreMissing() {
        Task scheduled = new Task("task-1", "Call", "", LocalDate.of(2026, 3, 11), 2);
        scheduled.setStartDate(LocalDate.of(2026, 3, 11));
        scheduled.setStartTime(LocalTime.of(12, 0));
        scheduled.setDeadlineTime(LocalTime.of(13, 0));

        FocusBlockCandidateWindowCalculator calculator = new FocusBlockCandidateWindowCalculator(
                taskService(List.of(scheduled)),
                List::of,
                reviewDate -> List.of(),
                new DailyReviewWindowCalculator()
        );

        List<FocusBlockCandidate> windows = calculator.calculateForDate(LocalDate.of(2026, 3, 11));

        assertFalse(windows.isEmpty());
        assertTrue(windows.stream().allMatch(FocusBlockCandidate::approximate));
        assertEquals("11:00-12:00", windows.get(0).label());
        assertEquals("13:00-14:00", windows.get(1).label());
    }

    @Test
    void ignoresUnscheduledTasksForBusyIntervals() {
        Task unscheduled = new Task("task-1", "Backlog task", "", LocalDate.of(2026, 3, 15), 3);

        FocusBlockCandidateWindowCalculator calculator = new FocusBlockCandidateWindowCalculator(
                taskService(List.of(unscheduled)),
                List::of,
                workHours(9, 12),
                new DailyReviewWindowCalculator()
        );

        List<FocusBlockCandidate> windows = calculator.calculateForDate(LocalDate.of(2026, 3, 11));

        assertEquals(1, windows.size());
        assertEquals("09:00-12:00", windows.getFirst().label());
    }

    private TaskApplicationService taskService(List<Task> tasks) {
        return new TaskApplicationService() {
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
            public com.example.neuroflowplanner.model.CriticalPathResult computeCriticalPathFullGraph() {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.example.neuroflowplanner.model.CriticalPathResult computeCriticalPathForRootTask(String rootTaskId) {
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
        };
    }

    private DailyReviewWorkHoursProvider workHours(int startHour, int endHour) {
        return reviewDate -> List.of(new com.example.neuroflowplanner.service.dailyreview.DailyReviewWorkInterval(
                LocalDateTime.of(reviewDate, LocalTime.of(startHour, 0)),
                LocalDateTime.of(reviewDate, LocalTime.of(endHour, 0)),
                (endHour - startHour) * 60,
                false,
                String.format("%02d:00-%02d:00", startHour, endHour)
        ));
    }
}
