package com.example.neuroflowplanner.ai.resilience;

import com.example.neuroflowplanner.ai.json.AiParsingException;

import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class AiHttpErrorClassifier {

    public enum ErrorCategory {
        TRANSIENT_RETRYABLE,
        DETERMINISTIC_FAIL_FAST,
        UNKNOWN
    }

    public static ErrorCategory classifyHttpStatus(int statusCode) {
        if (statusCode == 429 || statusCode == 408 || statusCode == 425) {
            return ErrorCategory.TRANSIENT_RETRYABLE;
        }
        if (statusCode >= 500 && statusCode < 600) {
            return ErrorCategory.TRANSIENT_RETRYABLE;
        }
        if (statusCode >= 400 && statusCode < 500) { // 400..404, 422, etc (except 408, 425, 429)
            return ErrorCategory.DETERMINISTIC_FAIL_FAST;
        }
        return ErrorCategory.UNKNOWN;
    }

    public static ErrorCategory classifyException(Throwable ex) {
        Throwable root = unwrap(ex);
        if (root instanceof AiParsingException) {
            return ErrorCategory.DETERMINISTIC_FAIL_FAST;
        }
        if (root instanceof HttpTimeoutException) {
            return ErrorCategory.TRANSIENT_RETRYABLE;
        }
        if (root instanceof IOException) {
            return ErrorCategory.TRANSIENT_RETRYABLE;
        }
        return ErrorCategory.UNKNOWN;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException || current instanceof ExecutionException) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }
        return current;
    }
}
