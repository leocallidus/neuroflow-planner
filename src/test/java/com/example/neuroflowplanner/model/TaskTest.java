package com.example.neuroflowplanner.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для класса Task.
 * UT-001, UT-002
 */
@DisplayName("Task Model Tests")
class TaskTest {

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task("Тестовая задача", "Описание задачи", LocalDate.now().plusDays(7), 5);
    }

    // UT-001: Создание задачи
    @Test
    @DisplayName("UT-001: Создание задачи с базовыми параметрами")
    void testTaskCreation() {
        assertNotNull(task.getId(), "ID должен быть сгенерирован");
        assertEquals("Тестовая задача", task.getTitle());
        assertEquals("Описание задачи", task.getDescription());
        assertEquals(5, task.getComplexity());
        assertNotNull(task.getDeadline());
    }

    @Test
    @DisplayName("UT-001: Создание задачи с полными параметрами")
    void testTaskCreationWithAllParams() {
        Task fullTask = new Task(
            "test-id-123",
            "Полная задача",
            "Полное описание",
            LocalDate.of(2025, 12, 31),
            8,
            null,
            "работа, проект",
            "weekly"
        );

        assertEquals("test-id-123", fullTask.getId());
        assertEquals("Полная задача", fullTask.getTitle());
        assertEquals("Полное описание", fullTask.getDescription());
        assertEquals(LocalDate.of(2025, 12, 31), fullTask.getDeadline());
        assertEquals(8, fullTask.getComplexity());
        assertEquals("работа, проект", fullTask.getTags());
        assertEquals("weekly", fullTask.getRecurrence());
        assertTrue(fullTask.isRecurring());
    }

    @Test
    @DisplayName("UT-001: Задача без повторения")
    void testTaskWithoutRecurrence() {
        Task simpleTask = new Task("Простая", "Описание", LocalDate.now(), 3);
        assertFalse(simpleTask.isRecurring());
        assertEquals("", simpleTask.getRecurrence());
    }

    // UT-002: Добавление подзадачи
    @Test
    @DisplayName("UT-002: Добавление подзадачи")
    void testAddSubtask() {
        Task subtask = new Task(
            "subtask-id",
            "Подзадача",
            "Описание подзадачи",
            LocalDate.now().plusDays(3),
            3,
            task.getId()
        );

        task.getSubtasks().add(subtask);

        assertTrue(task.hasSubtasks());
        assertEquals(1, task.getSubtasks().size());
        assertEquals("Подзадача", task.getSubtasks().get(0).getTitle());
        assertTrue(subtask.isSubtask());
        assertEquals(task.getId(), subtask.getParentId());
    }

    @Test
    @DisplayName("UT-002: Множественные подзадачи")
    void testMultipleSubtasks() {
        for (int i = 1; i <= 3; i++) {
            Task subtask = new Task(
                "subtask-" + i,
                "Подзадача " + i,
                "Описание " + i,
                LocalDate.now().plusDays(i),
                i,
                task.getId()
            );
            task.getSubtasks().add(subtask);
        }

        assertEquals(3, task.getSubtasks().size());
        assertTrue(task.hasSubtasks());
    }

    @Test
    @DisplayName("Проверка свойств приоритета")
    void testSmartPriority() {
        assertEquals(0.0, task.getSmartPriority());
        task.setSmartPriority(7.5);
        assertEquals(7.5, task.getSmartPriority());
    }

    @Test
    @DisplayName("Проверка статуса выполнения")
    void testCompletionStatus() {
        assertFalse(task.isCompleted());
        assertNull(task.getCompletedDate());

        task.setCompleted(true);
        task.setCompletedDate(LocalDate.now());

        assertTrue(task.isCompleted());
        assertNotNull(task.getCompletedDate());
    }

    @Test
    @DisplayName("Проверка архивации")
    void testArchiveStatus() {
        assertFalse(task.isArchived());
        task.setArchived(true);
        assertTrue(task.isArchived());
    }

    @Test
    @DisplayName("Проверка трекинга времени")
    void testTimeTracking() {
        assertEquals(0, task.getTrackedMinutes());
        task.addTrackedMinutes(30);
        assertEquals(30, task.getTrackedMinutes());
        task.addTrackedMinutes(15);
        assertEquals(45, task.getTrackedMinutes());
    }

    @Test
    @DisplayName("Проверка даты начала")
    void testStartDate() {
        assertFalse(task.hasStartDate());
        assertTrue(task.isStarted()); // Без даты начала - уже стартовала

        task.setStartDate(LocalDate.now().plusDays(5));
        assertTrue(task.hasStartDate());
        assertFalse(task.isStarted()); // Дата в будущем - ещё не стартовала

        task.setStartDate(LocalDate.now().minusDays(1));
        assertTrue(task.isStarted()); // Дата в прошлом - уже стартовала
    }

    @Test
    @DisplayName("Проверка зависимостей")
    void testDependencies() {
        assertFalse(task.hasDependencies());
        assertEquals("", task.getDependsOn());

        task.setDependsOn("task-1, task-2");
        assertTrue(task.hasDependencies());
        assertEquals("task-1, task-2", task.getDependsOn());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10})
    @DisplayName("Проверка различных уровней сложности")
    void testComplexityLevels(int complexity) {
        Task t = new Task("Задача", "Описание", LocalDate.now(), complexity);
        assertEquals(complexity, t.getComplexity());
        assertTrue(complexity >= 1 && complexity <= 10);
    }

    @Test
    @DisplayName("Проверка AI Insight")
    void testAiInsight() {
        assertNull(task.getAiInsight());
        task.setAiInsight("Рекомендация от ИИ");
        assertEquals("Рекомендация от ИИ", task.getAiInsight());
    }

    @Test
    @DisplayName("Проверка тегов")
    void testTags() {
        assertEquals("", task.getTags());
        task.setTags("работа, важно");
        assertEquals("работа, важно", task.getTags());
        
        // Проверка null-safety
        task.setTags(null);
        assertEquals("", task.getTags());
    }
}
