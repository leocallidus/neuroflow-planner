package com.example.neuroflowplanner.ai.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AiConcurrencyLimiterTest {

    @Test
    void testAcquireWithinLimits() throws Exception {
        AiConcurrencyLimiter limiter = new AiConcurrencyLimiter(2, Duration.ofMillis(100));
        limiter.acquire();
        limiter.acquire();
        assertEquals(0, limiter.getAvailablePermits());

        limiter.release();
        assertEquals(1, limiter.getAvailablePermits());
    }

    @Test
    void testTimeoutWhenLimitsExceeded() throws Exception {
        AiConcurrencyLimiter limiter = new AiConcurrencyLimiter(1, Duration.ofMillis(50));
        limiter.acquire();

        assertThrows(TimeoutException.class, limiter::acquire);
    }
}
