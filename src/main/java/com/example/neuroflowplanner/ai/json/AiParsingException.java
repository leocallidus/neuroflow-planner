package com.example.neuroflowplanner.ai.json;

import com.example.neuroflowplanner.error.ErrorCode;

public class AiParsingException extends RuntimeException {
    private final ErrorCode errorCode;

    public AiParsingException(String message) {
        this(ErrorCode.AI_RESPONSE_INVALID, message, null);
    }

    public AiParsingException(String message, Throwable cause) {
        this(ErrorCode.AI_RESPONSE_INVALID, message, cause);
    }

    public AiParsingException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public AiParsingException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? ErrorCode.AI_RESPONSE_INVALID : errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
