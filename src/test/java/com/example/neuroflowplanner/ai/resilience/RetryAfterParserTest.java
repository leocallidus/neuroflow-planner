package com.example.neuroflowplanner.ai.resilience;

import org.junit.jupiter.api.Test;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RetryAfterParserTest {

    @Test
    void testParseSeconds() {
        assertEquals(Duration.ofSeconds(10), RetryAfterParser.parse("10"));
        assertEquals(Duration.ofSeconds(120), RetryAfterParser.parse("  120  "));
    }

    @Test
    void testParseInvalidOrUnrecognizedFormat() {
        assertNull(RetryAfterParser.parse(""));
        assertNull(RetryAfterParser.parse("   "));
        assertNull(RetryAfterParser.parse(null));
        assertNull(RetryAfterParser.parse("Wed, 21 Oct 2015 07:28:00 GMT"));
        assertNull(RetryAfterParser.parse("invalid"));
    }
}
