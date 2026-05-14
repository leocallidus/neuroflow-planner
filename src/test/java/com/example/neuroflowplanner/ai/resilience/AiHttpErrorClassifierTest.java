package com.example.neuroflowplanner.ai.resilience;

import com.example.neuroflowplanner.ai.json.AiParsingException;
import org.junit.jupiter.api.Test;
import java.net.http.HttpTimeoutException;
import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class AiHttpErrorClassifierTest {

    @Test
    void testHttpStatusClassification() {
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyHttpStatus(429));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyHttpStatus(408));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyHttpStatus(500));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyHttpStatus(503));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyHttpStatus(504));

        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyHttpStatus(400));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyHttpStatus(401));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyHttpStatus(403));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyHttpStatus(404));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyHttpStatus(422));

        assertEquals(AiHttpErrorClassifier.ErrorCategory.UNKNOWN, AiHttpErrorClassifier.classifyHttpStatus(200));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.UNKNOWN, AiHttpErrorClassifier.classifyHttpStatus(301));
    }

    @Test
    void testExceptionClassification() {
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyException(new HttpTimeoutException("timeout")));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.TRANSIENT_RETRYABLE,
                AiHttpErrorClassifier.classifyException(new IOException("connection reset")));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyException(new AiParsingException("invalid payload")));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.DETERMINISTIC_FAIL_FAST,
                AiHttpErrorClassifier.classifyException(
                        new CompletionException(new AiParsingException("schema mismatch"))));
        assertEquals(AiHttpErrorClassifier.ErrorCategory.UNKNOWN,
                AiHttpErrorClassifier.classifyException(new RuntimeException("unknown")));
    }
}
