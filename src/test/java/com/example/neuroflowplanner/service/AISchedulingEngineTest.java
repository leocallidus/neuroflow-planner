package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.model.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для AISchedulingEngine.
 * UT-008
 */
@DisplayName("AISchedulingEngine Tests")
class AISchedulingEngineTest {

    private AISchedulingEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AISchedulingEngine();
    }

    // UT-008: calculatePriority
    @Test
    @DisplayName("UT-008: Расчёт приоритета для срочной задачи")
    void testCalculatePriorityUrgent() {
        Task urgentTask = new Task(
            "urgent-task",
            "Срочная задача",
            "Описание",
            LocalDate.now().plusDays(1), // Завтра
            8 // Высокая сложность
        );

        engine.calculatePriority(urgentTask);

        assertTrue(urgentTask.getSmartPriority() > 0, "Приоритет должен быть рассчитан");
        assertTrue(urgentTask.getSmartPriority() >= 5, "Срочная задача должна иметь высокий приоритет");
    }

    @Test
    @DisplayName("UT-008: Расчёт приоритета для несрочной задачи")
    void testCalculatePriorityNotUrgent() {
        Task notUrgentTask = new Task(
            "not-urgent-task",
            "Несрочная задача",
            "Описание",
            LocalDate.now().plusDays(30), // Через месяц
            2 // Низкая сложность
        );

        engine.calculatePriority(notUrgentTask);

        assertTrue(notUrgentTask.getSmartPriority() > 0, "Приоритет должен быть рассчитан");
        assertTrue(notUrgentTask.getSmartPriority() < 5, "Несрочная простая задача должна иметь низкий приоритет");
    }

    @Test
    @DisplayName("UT-008: Расчёт приоритета для просроченной задачи")
    void testCalculatePriorityOverdue() {
        Task overdueTask = new Task(
            "overdue-task",
            "Просроченная задача",
            "Описание",
            LocalDate.now().minusDays(1), // Вчера
            5
        );

        engine.calculatePriority(overdueTask);

        assertTrue(overdueTask.getSmartPriority() >= 8, "Просроченная задача должна иметь максимальный приоритет");
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1, 30",   // Низкая сложность, далёкий дедлайн
        "5, 5, 7",    // Средняя сложность, неделя
        "10, 10, 1",  // Высокая сложность, завтра
        "3, 7, 14"    // Разные комбинации
    })
    @DisplayName("UT-008: Параметризованный тест расчёта приоритета")
    void testCalculatePriorityParameterized(int complexity, int expectedMinPriority, int daysUntilDeadline) {
        Task task = new Task(
            "param-task",
            "Параметризованная задача",
            "Описание",
            LocalDate.now().plusDays(daysUntilDeadline),
            complexity
        );

        engine.calculatePriority(task);

        assertTrue(task.getSmartPriority() > 0, "Приоритет должен быть положительным");
        assertTrue(task.getSmartPriority() <= 10, "Приоритет не должен превышать 10");
    }

    @Test
    @DisplayName("Авто-планирование пустого списка")
    void testAutoScheduleEmptyList() {
        String result = engine.autoSchedule(new ArrayList<>(), 10);
        
        assertNotNull(result);
        assertTrue(result.contains("Нет задач"));
    }

    @Test
    @DisplayName("Авто-планирование списка задач")
    void testAutoScheduleWithTasks() {
        List<Task> tasks = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Task task = new Task(
                "task-" + i,
                "Задача " + i,
                "Описание " + i,
                LocalDate.now().plusDays(i * 2),
                i * 2
            );
            tasks.add(task);
        }

        String result = engine.autoSchedule(tasks, 10);

        assertNotNull(result);
        assertTrue(result.contains("Авто-планирование завершено"));
        assertTrue(result.contains("Распределено задач"));
    }

    @Test
    @DisplayName("Авто-планирование с учётом максимальной нагрузки")
    void testAutoScheduleWithMaxLoad() {
        List<Task> tasks = new ArrayList<>();
        
        // Создаём задачи с высокой сложностью
        for (int i = 1; i <= 3; i++) {
            Task task = new Task(
                "heavy-task-" + i,
                "Тяжёлая задача " + i,
                "Описание",
                LocalDate.now().plusDays(10),
                8 // Высокая сложность
            );
            tasks.add(task);
        }

        String result = engine.autoSchedule(tasks, 10);

        assertNotNull(result);
        // Задачи должны быть распределены на несколько дней
        assertTrue(result.contains("Задействовано дней"));
    }

    @Test
    @DisplayName("Авто-планирование игнорирует архивные задачи")
    void testAutoScheduleIgnoresArchived() {
        List<Task> tasks = new ArrayList<>();
        
        Task activeTask = new Task("active", "Активная", "Описание", LocalDate.now().plusDays(5), 5);
        Task archivedTask = new Task("archived", "Архивная", "Описание", LocalDate.now().plusDays(5), 5);
        archivedTask.setArchived(true);
        
        tasks.add(activeTask);
        tasks.add(archivedTask);

        engine.autoSchedule(tasks, 10);

        // Архивная задача не должна получить дату начала
        assertNull(archivedTask.getStartDate());
    }

    @Test
    @DisplayName("Проверка округления приоритета")
    void testPriorityRounding() {
        Task task = new Task(
            "rounding-task",
            "Задача для проверки округления",
            "Описание",
            LocalDate.now().plusDays(5),
            5
        );

        engine.calculatePriority(task);

        // Приоритет должен быть округлён до одного знака после запятой
        double priority = task.getSmartPriority();
        double rounded = Math.round(priority * 10.0) / 10.0;
        assertEquals(rounded, priority, 0.001);
    }

    @Test
    @DisplayName("Приоритет зависит от сложности")
    void testPriorityDependsOnComplexity() {
        LocalDate sameDeadline = LocalDate.now().plusDays(7);
        
        Task simpleTask = new Task("simple", "Простая", "Описание", sameDeadline, 2);
        Task complexTask = new Task("complex", "Сложная", "Описание", sameDeadline, 9);

        engine.calculatePriority(simpleTask);
        engine.calculatePriority(complexTask);

        assertTrue(complexTask.getSmartPriority() > simpleTask.getSmartPriority(),
            "Сложная задача должна иметь более высокий приоритет при одинаковом дедлайне");
    }

    @Test
    @DisplayName("Приоритет зависит от срочности")
    void testPriorityDependsOnUrgency() {
        int sameComplexity = 5;
        
        Task urgentTask = new Task("urgent", "Срочная", "Описание", LocalDate.now().plusDays(2), sameComplexity);
        Task notUrgentTask = new Task("not-urgent", "Несрочная", "Описание", LocalDate.now().plusDays(20), sameComplexity);

        engine.calculatePriority(urgentTask);
        engine.calculatePriority(notUrgentTask);

        assertTrue(urgentTask.getSmartPriority() > notUrgentTask.getSmartPriority(),
            "Срочная задача должна иметь более высокий приоритет при одинаковой сложности");
    }
}
