package com.example.neuroflowplanner.ai.resilience;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AiRetryDelayStrategyTest {

    @Test
    void testExponentialBackoffWithoutJitter() {
        AiRetryDelayStrategy strategy = new AiRetryDelayStrategy(Duration.ofMillis(100), Duration.ofMillis(1000), 0.0);

        assertEquals(100, strategy.calculateDelay(1).toMillis());
        assertEquals(200, strategy.calculateDelay(2).toMillis());
        assertEquals(400, strategy.calculateDelay(3).toMillis());
        assertEquals(800, strategy.calculateDelay(4).toMillis());
    }

    @Test
    void testMaxDelayCap() {
        AiRetryDelayStrategy strategy = new AiRetryDelayStrategy(Duration.ofMillis(100), Duration.ofMillis(300), 0.0);

        assertEquals(100, strategy.calculateDelay(1).toMillis());
        assertEquals(200, strategy.calculateDelay(2).toMillis());
        assertEquals(300, strategy.calculateDelay(3).toMillis());
        assertEquals(300, strategy.calculateDelay(4).toMillis());
    }

    @Test
    void testJitterApplied() {
        AiRetryDelayStrategy strategy = new AiRetryDelayStrategy(Duration.ofMillis(100), Duration.ofMillis(1000), 0.2);

        long delay1 = strategy.calculateDelay(1).toMillis();
        assertTrue(delay1 >= 80 && delay1 <= 120, "Delay should be within 100 +/- 20%");

        long delay2 = strategy.calculateDelay(2).toMillis();
        assertTrue(delay2 >= 160 && delay2 <= 240, "Delay should be within 200 +/- 20%");
    }
}
