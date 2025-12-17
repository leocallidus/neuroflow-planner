package com.example.neuroflowplanner.db;

import com.example.neuroflowplanner.model.Task;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для DatabaseManager.
 * UT-003, UT-004, UT-005
 */
@DisplayName("DatabaseManager Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseManagerTest {

    private static DatabaseManager db;
    private static String testTaskId;

    @BeforeAll
    static void setUpClass() {
        db = DatabaseManager.getInstance();
        testTaskId = "test-" + UUID.randomUUID().toString();
    }

    // UT-003: Сохранение задачи
    @Test
    @Order(1)
    @DisplayName("UT-003: Сохранение новой задачи")
    void testSaveTask() {
        Task task = new Task(
            testTaskId,
            "Тестовая задача для БД",
            "Описание тестовой задачи",
            LocalDate.now().plusDays(7),
            5,
            null,
            "тест, junit",
            ""
        );
        task.setSmartPriority(6.5);

        assertDoesNotThrow(() -> db.saveTask(task));
    }

    // UT-004: Загрузка задач
    @Test
    @Order(2)
    @DisplayName("UT-004: Загрузка всех задач")
    void testLoadAllTasks() {
        List<Task> tasks = db.loadAllTasks();

        assertNotNull(tasks, "Список задач не должен быть null");
        // Список может быть пустым или содержать задачи
    }

    @Test
    @Order(3)
    @DisplayName("UT-004: Загрузка сохранённой задачи")
    void testLoadSavedTask() {
        List<Task> tasks = db.loadAllTasks();

        // Ищем нашу тестовую задачу
        Task found = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(found, "Сохранённая задача должна быть найдена");
        assertEquals("Тестовая задача для БД", found.getTitle());
        assertEquals("Описание тестовой задачи", found.getDescription());
        assertEquals(5, found.getComplexity());
        assertEquals("тест, junit", found.getTags());
    }

    @Test
    @Order(4)
    @DisplayName("UT-003: Обновление существующей задачи")
    void testUpdateTask() {
        List<Task> tasks = db.loadAllTasks();
        Task task = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(task);

        // Обновляем задачу
        task.setTags("тест, junit, обновлено");
        task.setSmartPriority(8.0);
        task.setCompleted(true);
        task.setCompletedDate(LocalDate.now());

        db.saveTask(task);

        // Перезагружаем и проверяем
        List<Task> reloaded = db.loadAllTasks();
        Task updated = reloaded.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNotNull(updated);
        assertEquals("тест, junit, обновлено", updated.getTags());
        assertTrue(updated.isCompleted());
        assertNotNull(updated.getCompletedDate());
    }

    @Test
    @Order(5)
    @DisplayName("Сохранение задачи с подзадачами")
    void testSaveTaskWithSubtasks() {
        String parentId = "parent-" + UUID.randomUUID().toString();
        String subtaskId = "subtask-" + UUID.randomUUID().toString();

        Task parent = new Task(
            parentId,
            "Родительская задача",
            "Описание",
            LocalDate.now().plusDays(10),
            7
        );

        Task subtask = new Task(
            subtaskId,
            "Подзадача",
            "Описание подзадачи",
            LocalDate.now().plusDays(5),
            3,
            parentId
        );

        db.saveTask(parent);
        db.saveTask(subtask);

        // Проверяем загрузку - подзадачи могут быть вложены в родительские задачи
        List<Task> tasks = db.loadAllTasks();
        
        // Ищем подзадачу либо в корневом списке, либо внутри родительской задачи
        Task loadedSubtask = tasks.stream()
            .filter(t -> t.getId().equals(subtaskId))
            .findFirst()
            .orElse(null);
        
        // Если не нашли в корне, ищем в подзадачах родителя
        if (loadedSubtask == null) {
            Task loadedParent = tasks.stream()
                .filter(t -> t.getId().equals(parentId))
                .findFirst()
                .orElse(null);
            if (loadedParent != null && loadedParent.hasSubtasks()) {
                loadedSubtask = loadedParent.getSubtasks().stream()
                    .filter(t -> t.getId().equals(subtaskId))
                    .findFirst()
                    .orElse(null);
            }
        }

        // Подзадача должна быть найдена где-то
        assertNotNull(loadedSubtask, "Подзадача должна быть сохранена и загружена");
        assertEquals(parentId, loadedSubtask.getParentId());
        assertTrue(loadedSubtask.isSubtask());

        // Очистка
        db.deleteTask(subtaskId);
        db.deleteTask(parentId);
    }

    @Test
    @Order(6)
    @DisplayName("Сохранение задачи с датой начала")
    void testSaveTaskWithStartDate() {
        String taskId = "start-date-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с датой начала",
            "Описание",
            LocalDate.now().plusDays(14),
            5
        );
        task.setStartDate(LocalDate.now().plusDays(7));

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertNotNull(loaded.getStartDate());
        assertEquals(LocalDate.now().plusDays(7), loaded.getStartDate());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @Order(7)
    @DisplayName("Сохранение архивной задачи")
    void testSaveArchivedTask() {
        String taskId = "archived-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Архивная задача",
            "Описание",
            LocalDate.now().plusDays(5),
            3
        );
        task.setArchived(true);

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertTrue(loaded.isArchived());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @Order(8)
    @DisplayName("Сохранение задачи с трекингом времени")
    void testSaveTaskWithTrackedTime() {
        String taskId = "tracked-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с трекингом",
            "Описание",
            LocalDate.now().plusDays(5),
            5
        );
        task.setTrackedMinutes(120); // 2 часа

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals(120, loaded.getTrackedMinutes());

        // Очистка
        db.deleteTask(taskId);
    }

    // UT-005: Удаление задачи
    @Test
    @Order(100) // Выполняется последним
    @DisplayName("UT-005: Удаление задачи")
    void testDeleteTask() {
        // Удаляем тестовую задачу
        db.deleteTask(testTaskId);

        // Проверяем, что задача удалена
        List<Task> tasks = db.loadAllTasks();
        Task found = tasks.stream()
            .filter(t -> t.getId().equals(testTaskId))
            .findFirst()
            .orElse(null);

        assertNull(found, "Удалённая задача не должна быть найдена");
    }

    @Test
    @DisplayName("Удаление несуществующей задачи не вызывает ошибку")
    void testDeleteNonExistingTask() {
        assertDoesNotThrow(() -> db.deleteTask("non-existing-task-id-xyz"));
    }

    @Test
    @DisplayName("Singleton паттерн DatabaseManager")
    void testSingletonPattern() {
        DatabaseManager instance1 = DatabaseManager.getInstance();
        DatabaseManager instance2 = DatabaseManager.getInstance();

        assertSame(instance1, instance2, "Должен возвращаться один и тот же экземпляр");
    }

    @Test
    @DisplayName("Сохранение задачи с повторением")
    void testSaveRecurringTask() {
        String taskId = "recurring-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Повторяющаяся задача",
            "Описание",
            LocalDate.now().plusDays(7),
            4,
            null,
            "повтор",
            "weekly"
        );

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals("weekly", loaded.getRecurrence());
        assertTrue(loaded.isRecurring());

        // Очистка
        db.deleteTask(taskId);
    }

    @Test
    @DisplayName("Сохранение задачи с зависимостями")
    void testSaveTaskWithDependencies() {
        String taskId = "deps-" + UUID.randomUUID().toString();
        
        Task task = new Task(
            taskId,
            "Задача с зависимостями",
            "Описание",
            LocalDate.now().plusDays(10),
            6
        );
        task.setDependsOn("task-1, task-2");

        db.saveTask(task);

        List<Task> tasks = db.loadAllTasks();
        Task loaded = tasks.stream()
            .filter(t -> t.getId().equals(taskId))
            .findFirst()
            .orElse(null);

        assertNotNull(loaded);
        assertEquals("task-1, task-2", loaded.getDependsOn());
        assertTrue(loaded.hasDependencies());

        // Очистка
        db.deleteTask(taskId);
    }
}
