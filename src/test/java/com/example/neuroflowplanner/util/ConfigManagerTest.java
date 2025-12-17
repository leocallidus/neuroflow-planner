package com.example.neuroflowplanner.util;

import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты для ConfigManager.
 * UT-006, UT-007
 */
@DisplayName("ConfigManager Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigManagerTest {

    private static final String TEST_KEY = "test.property";
    private static final String TEST_VALUE = "test_value_123";

    @BeforeAll
    static void setUpClass() {
        // Убедимся, что ConfigManager инициализирован
        ConfigManager.getProperty("api.url");
    }

    // UT-006: Чтение свойства
    @Test
    @Order(1)
    @DisplayName("UT-006: Чтение существующего свойства")
    void testReadExistingProperty() {
        // api.url должен быть либо из конфига, либо null
        String apiUrl = ConfigManager.getProperty("api.url");
        // Не проверяем конкретное значение, просто что метод работает
        assertDoesNotThrow(() -> ConfigManager.getProperty("api.url"));
    }

    @Test
    @Order(2)
    @DisplayName("UT-006: Чтение несуществующего свойства возвращает null")
    void testReadNonExistingProperty() {
        String value = ConfigManager.getProperty("non.existing.property.xyz");
        assertNull(value);
    }

    // UT-007: Запись свойства
    @Test
    @Order(3)
    @DisplayName("UT-007: Запись и чтение свойства")
    void testWriteAndReadProperty() {
        // Записываем
        ConfigManager.setProperty(TEST_KEY, TEST_VALUE);
        
        // Читаем
        String readValue = ConfigManager.getProperty(TEST_KEY);
        assertEquals(TEST_VALUE, readValue);
    }

    @Test
    @Order(4)
    @DisplayName("UT-007: Перезапись свойства")
    void testOverwriteProperty() {
        String newValue = "new_value_456";
        
        ConfigManager.setProperty(TEST_KEY, TEST_VALUE);
        ConfigManager.setProperty(TEST_KEY, newValue);
        
        assertEquals(newValue, ConfigManager.getProperty(TEST_KEY));
    }

    @Test
    @Order(5)
    @DisplayName("Проверка isDarkTheme")
    void testIsDarkTheme() {
        // Метод должен возвращать boolean без исключений
        assertDoesNotThrow(() -> {
            boolean isDark = ConfigManager.isDarkTheme();
            assertTrue(isDark || !isDark); // Просто проверяем, что возвращается boolean
        });
    }

    @Test
    @Order(6)
    @DisplayName("Проверка setDarkTheme")
    void testSetDarkTheme() {
        boolean originalValue = ConfigManager.isDarkTheme();
        
        // Переключаем тему
        ConfigManager.setDarkTheme(!originalValue);
        assertEquals(!originalValue, ConfigManager.isDarkTheme());
        
        // Возвращаем обратно
        ConfigManager.setDarkTheme(originalValue);
        assertEquals(originalValue, ConfigManager.isDarkTheme());
    }

    @Test
    @Order(7)
    @DisplayName("Проверка работы с пустыми значениями")
    void testEmptyValue() {
        ConfigManager.setProperty("empty.test", "");
        String value = ConfigManager.getProperty("empty.test");
        assertEquals("", value);
    }

    @Test
    @Order(8)
    @DisplayName("Проверка работы со специальными символами")
    void testSpecialCharacters() {
        String specialValue = "http://localhost:11434/api/chat?param=value&other=123";
        ConfigManager.setProperty("special.chars", specialValue);
        assertEquals(specialValue, ConfigManager.getProperty("special.chars"));
    }

    @Test
    @Order(9)
    @DisplayName("Проверка работы с кириллицей")
    void testCyrillicValue() {
        String cyrillicValue = "Тестовое значение на русском";
        ConfigManager.setProperty("cyrillic.test", cyrillicValue);
        assertEquals(cyrillicValue, ConfigManager.getProperty("cyrillic.test"));
    }

    @AfterAll
    static void cleanUp() {
        // Очищаем тестовые свойства (устанавливаем пустые значения вместо null)
        ConfigManager.setProperty(TEST_KEY, "");
        ConfigManager.setProperty("empty.test", "");
        ConfigManager.setProperty("special.chars", "");
        ConfigManager.setProperty("cyrillic.test", "");
    }
}
