package com.example.neuroflowplanner.error;

/**
 * Canonical error codes for P0 centralized logging and UI error handling.
 * <p>
 * Stage 1 introduces the catalog only; mapping and notifier wiring are added
 * in the next stages.
 */
public enum ErrorCode {
    // DB_*
    DB_CONNECTION_FAILED(Family.DB),
    DB_QUERY_FAILED(Family.DB),
    DB_MIGRATION_FAILED(Family.DB),
    DB_CONSTRAINT_VIOLATION(Family.DB),

    // AI_*
    AI_REQUEST_FAILED(Family.AI),
    AI_TIMEOUT(Family.AI),
    AI_UNAVAILABLE(Family.AI),
    AI_RATE_LIMITED(Family.AI),
    AI_PROVIDER_ERROR(Family.AI),
    AI_RETRY_EXHAUSTED(Family.AI),
    AI_RESPONSE_INVALID(Family.AI),

    // IO_*
    IO_READ_FAILED(Family.IO),
    IO_WRITE_FAILED(Family.IO),
    IO_DELETE_FAILED(Family.IO),

    // EXPORT_*
    EXPORT_EXCEL_FAILED(Family.EXPORT),
    EXPORT_PDF_FAILED(Family.EXPORT),
    EXPORT_CSV_FAILED(Family.EXPORT),
    EXPORT_DOCX_FAILED(Family.EXPORT),
    EXPORT_MARKDOWN_FAILED(Family.EXPORT),
    EXPORT_JSON_FAILED(Family.EXPORT),

    // VALIDATION_*
    VALIDATION_FAILED(Family.VALIDATION),
    VALIDATION_REQUIRED_FIELD(Family.VALIDATION),
    VALIDATION_INVALID_VALUE(Family.VALIDATION),

    // TASK_*
    TASK_DEPENDENCY_CYCLE(Family.TASK),
    TASK_DEPENDENCY_INVALID_REFERENCE(Family.TASK),

    // UNEXPECTED_*
    UNEXPECTED_ERROR(Family.UNEXPECTED);

    private final Family family;

    ErrorCode(Family family) {
        this.family = family;
    }

    public Family family() {
        return family;
    }

    public String familyTag() {
        return family.name();
    }

    public enum Family {
        DB,
        AI,
        IO,
        EXPORT,
        VALIDATION,
        TASK,
        UNEXPECTED
    }
}
