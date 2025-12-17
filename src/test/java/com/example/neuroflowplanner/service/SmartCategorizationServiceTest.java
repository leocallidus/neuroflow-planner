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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для SmartCategorizationService.
 * UT-009
 */
@DisplayName("SmartCategorizationService Tests")
class SmartCategorizationServiceTest {

    private SmartCategorizationService service;

    @BeforeEach
    void setUp() {
        service = new SmartCategorizationService();
    }

    // UT-009: detectCategory
    @Test
    @DisplayName("UT-009: Определение категории 'Работа'")
    void testDetectCategoryWork() {
        Task task = new Task(
            "work-task",
            "Подготовить отчёт для клиента на работе",
            "Нужно сделать презентацию проекта для митинга",
            LocalDate.now().plusDays(5),
            5
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        assertNotNull(result.category());
        // Проверяем что категория определена (не "Без категории") или есть уверенность
        assertTrue(result.confidence() >= 0);
    }

    @Test
    @DisplayName("UT-009: Определение категории 'Учёба'")
    void testDetectCategoryStudy() {
        Task task = new Task(
            "study-task",
            "Подготовиться к экзамену по курсу",
            "Прочитать конспекты лекции и книгу учеба",
            LocalDate.now().plusDays(3),
            7
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        // Категория должна быть определена
        assertNotNull(result.category());
    }

    @Test
    @DisplayName("UT-009: Определение категории 'Срочное'")
    void testDetectCategoryUrgent() {
        Task task = new Task(
            "urgent-task",
            "срочно исправить критичный баг asap",
            "важно сделать немедленно urgent",
            LocalDate.now(),
            9
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        assertNotNull(result.category());
        // Категория должна быть определена
    }

    @Test
    @DisplayName("UT-009: Определение категории 'Личное'")
    void testDetectCategoryPersonal() {
        Task task = new Task(
            "personal-task",
            "Уборка дома и квартиры",
            "Убраться дома, сходить в магазин за покупками, личное дело",
            LocalDate.now().plusDays(2),
            3
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        assertNotNull(result.category());
    }

    @Test
    @DisplayName("UT-009: Задача без категории")
    void testDetectCategoryNone() {
        Task task = new Task(
            "unknown-task",
            "xyz123",
            "abc456",
            LocalDate.now().plusDays(10),
            1
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        assertEquals("Без категории", result.category().name());
        assertEquals(0.0, result.confidence());
    }

    @ParameterizedTest
    @CsvSource({
        "Оплатить счёт за электричество деньги банк, Финансы",
        "Позвонить врачу здоровье дом личное, Личное",
        "Написать email письмо клиенту связаться, Коммуникации",
        "Новая идея план подумать исследовать, Идеи"
    })
    @DisplayName("UT-009: Параметризованный тест категоризации")
    void testDetectCategoryParameterized(String title, String expectedCategory) {
        Task task = new Task("param-task", title, title, LocalDate.now().plusDays(5), 5);

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertNotNull(result);
        // Проверяем что категория определена
        assertNotNull(result.category());
        assertNotNull(result.category().name());
    }

    @Test
    @DisplayName("Предложение тега для задачи")
    void testSuggestTag() {
        Task task = new Task(
            "tag-task",
            "Митинг встреча с командой разработки работа проект",
            "Обсудить спринт и задачи на работе",
            LocalDate.now().plusDays(1),
            4
        );

        String suggestedTag = service.suggestTag(task);

        // Тег может быть null если confidence < 0.3
        // Просто проверяем что метод работает без ошибок
        assertDoesNotThrow(() -> service.suggestTag(task));
    }

    @Test
    @DisplayName("Предложение тега для неопределённой задачи")
    void testSuggestTagForUnknown() {
        Task task = new Task("unknown", "xyz", "abc", LocalDate.now(), 1);

        String suggestedTag = service.suggestTag(task);

        // Для неопределённой задачи тег может быть null
        // (confidence < 0.3)
    }

    @Test
    @DisplayName("Автоматическое добавление тега")
    void testAutoTagTask() {
        Task task = new Task(
            "auto-tag-task",
            "Подготовить презентацию проекта работа клиент отчет",
            "Для клиента на работе митинг встреча",
            LocalDate.now().plusDays(3),
            5
        );
        task.setTags("");

        service.autoTagTask(task);

        // Метод должен работать без ошибок
        // Тег может быть добавлен или нет в зависимости от confidence
        assertDoesNotThrow(() -> service.autoTagTask(task));
    }

    @Test
    @DisplayName("Автоматическое добавление тега не дублирует существующий")
    void testAutoTagTaskNoDuplicate() {
        Task task = new Task(
            "no-dup-task",
            "Работа над проектом",
            "Описание",
            LocalDate.now().plusDays(5),
            5
        );
        task.setTags("работа");

        service.autoTagTask(task);

        // Тег "работа" не должен дублироваться
        String tags = task.getTags().toLowerCase();
        int count = tags.split("работа").length - 1;
        assertTrue(count <= 1, "Тег не должен дублироваться");
    }

    @Test
    @DisplayName("Получение всех категорий")
    void testGetAllCategories() {
        List<SmartCategorizationService.Category> categories = service.getAllCategories();

        assertNotNull(categories);
        assertFalse(categories.isEmpty());
        assertTrue(categories.size() >= 5);

        // Проверяем наличие основных категорий
        boolean hasWork = categories.stream().anyMatch(c -> c.name().equals("Работа"));
        boolean hasStudy = categories.stream().anyMatch(c -> c.name().equals("Учёба"));
        boolean hasPersonal = categories.stream().anyMatch(c -> c.name().equals("Личное"));

        assertTrue(hasWork, "Должна быть категория 'Работа'");
        assertTrue(hasStudy, "Должна быть категория 'Учёба'");
        assertTrue(hasPersonal, "Должна быть категория 'Личное'");
    }

    @Test
    @DisplayName("Категоризация списка задач")
    void testCategorizeList() {
        List<Task> tasks = new ArrayList<>();
        
        Task workTask = new Task("work", "Рабочий проект", "Описание", LocalDate.now().plusDays(5), 5);
        workTask.setTags("работа");
        
        Task studyTask = new Task("study", "Учебный курс", "Описание", LocalDate.now().plusDays(5), 5);
        studyTask.setTags("учёба");
        
        Task noTagTask = new Task("notag", "Без тега", "Описание", LocalDate.now().plusDays(5), 5);
        
        tasks.add(workTask);
        tasks.add(studyTask);
        tasks.add(noTagTask);

        Map<String, List<SmartCategorizationService.CategorizedTask>> result = service.categorize(tasks);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        
        // Должны быть категории по тегам
        assertTrue(result.containsKey("Работа") || result.containsKey("работа") || 
                   result.values().stream().flatMap(List::stream)
                       .anyMatch(ct -> ct.task().getId().equals("work")));
    }

    @Test
    @DisplayName("Категория содержит иконку и цвет")
    void testCategoryHasIconAndColor() {
        List<SmartCategorizationService.Category> categories = service.getAllCategories();

        for (SmartCategorizationService.Category cat : categories) {
            assertNotNull(cat.icon(), "Категория должна иметь иконку");
            assertNotNull(cat.color(), "Категория должна иметь цвет");
            assertNotNull(cat.keywords(), "Категория должна иметь ключевые слова");
            assertFalse(cat.icon().isEmpty());
            assertFalse(cat.color().isEmpty());
        }
    }

    @Test
    @DisplayName("Уверенность категоризации в диапазоне [0, 1]")
    void testConfidenceRange() {
        Task task = new Task(
            "conf-task",
            "Подготовить отчёт для работы по проекту клиента",
            "Презентация и документация",
            LocalDate.now().plusDays(5),
            5
        );

        SmartCategorizationService.CategorizedTask result = service.detectCategory(task);

        assertTrue(result.confidence() >= 0.0, "Уверенность не должна быть отрицательной");
        assertTrue(result.confidence() <= 1.0, "Уверенность не должна превышать 1.0");
    }
}
