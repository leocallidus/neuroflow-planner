package com.example.neuroflowplanner.error;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ErrorCode Catalog Tests")
class ErrorCodeTest {

    @Test
    @DisplayName("Catalog contains all P0 families")
    void containsAllP0Families() {
        Set<ErrorCode.Family> actualFamilies = Arrays.stream(ErrorCode.values())
            .map(ErrorCode::family)
            .collect(Collectors.toSet());

        assertEquals(EnumSet.allOf(ErrorCode.Family.class), actualFamilies);
    }

    @Test
    @DisplayName("Every error code name starts with family prefix")
    void namesMatchFamilyPrefix() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(
                code.name().startsWith(code.family().name() + "_"),
                () -> "Code name does not match family prefix: " + code.name()
            );
        }
    }

    @Test
    @DisplayName("Critical P0 error codes exist in catalog")
    void containsCriticalP0Codes() {
        Set<ErrorCode> expected = EnumSet.of(
            ErrorCode.DB_CONNECTION_FAILED,
            ErrorCode.AI_TIMEOUT,
            ErrorCode.AI_RATE_LIMITED,
            ErrorCode.IO_READ_FAILED,
            ErrorCode.EXPORT_PDF_FAILED,
            ErrorCode.VALIDATION_INVALID_VALUE,
            ErrorCode.UNEXPECTED_ERROR
        );

        Set<ErrorCode> actual = EnumSet.copyOf(Arrays.asList(ErrorCode.values()));
        assertTrue(actual.containsAll(expected));
    }
}
