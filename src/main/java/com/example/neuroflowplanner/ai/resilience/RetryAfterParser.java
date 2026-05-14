package com.example.neuroflowplanner.ai.resilience;

import java.time.Duration;

public class RetryAfterParser {

    /**
     * Parses the Retry-After header.
     * The value can be either an HTTP-date or a delay in seconds.
     * For simplicity, this implementation only supports delay in seconds.
     *
     * @param retryAfterHeader the header value
     * @return Duration parsed from seconds, or null if parsing fails
     */
    public static Duration parse(String retryAfterHeader) {
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return null;
        }

        try {
            long seconds = Long.parseLong(retryAfterHeader.trim());
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            // It might be an HTTP-date, which we don't support in this simple parser for
            // now.
            return null;
        }
    }
}
