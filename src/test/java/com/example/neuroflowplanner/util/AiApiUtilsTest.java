package com.example.neuroflowplanner.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AiApiUtils Tests")
class AiApiUtilsTest {

    @Test
    @DisplayName("2xx статус-коды считаются успешными (в т.ч. 201)")
    void testIsSuccessfulStatus2xx() {
        assertTrue(AiApiUtils.isSuccessfulStatus(200));
        assertTrue(AiApiUtils.isSuccessfulStatus(201));
        assertTrue(AiApiUtils.isSuccessfulStatus(204));
        assertTrue(AiApiUtils.isSuccessfulStatus(299));
    }

    @Test
    @DisplayName("Не-2xx статус-коды не считаются успешными")
    void testIsSuccessfulStatusNon2xx() {
        assertFalse(AiApiUtils.isSuccessfulStatus(199));
        assertFalse(AiApiUtils.isSuccessfulStatus(300));
        assertFalse(AiApiUtils.isSuccessfulStatus(400));
        assertFalse(AiApiUtils.isSuccessfulStatus(500));
    }
}

