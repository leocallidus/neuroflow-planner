package com.example.neuroflowplanner.ai.resilience;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class AiConcurrencyLimiter {
    private final Semaphore semaphore;
    private final Duration acquireTimeout;

    public AiConcurrencyLimiter(int maxInFlight, Duration acquireTimeout) {
        this.semaphore = new Semaphore(Math.max(1, maxInFlight), true);
        this.acquireTimeout = acquireTimeout;
    }

    public void acquire() throws InterruptedException, TimeoutException {
        boolean acquired = semaphore.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new TimeoutException(
                    "Failed to acquire AI concurrency permit within " + acquireTimeout.toMillis() + " ms");
        }
    }

    public void release() {
        semaphore.release();
    }

    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }
}
