package com.example.neuroflowplanner.service.focusblocks;

import com.example.neuroflowplanner.model.TimeSession;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FocusProductivityProfileBuilderTest {

    @Test
    void returnsUnavailableProfileWhenHistoryIsEmpty() {
        FocusProductivityProfileBuilder builder = new FocusProductivityProfileBuilder(List::of);

        FocusProductivityProfile profile = builder.buildProfile(LocalDate.of(2026, 3, 11), 28);

        assertFalse(profile.available());
        assertEquals(0.0, profile.confidence());
        assertTrue(profile.limitedHistory());
        assertTrue(profile.hourScores().isEmpty());
        assertTrue(profile.dayScores().isEmpty());
    }

    @Test
    void aggregatesHistoryByHourAndDayAndComputesStableMetrics() {
        List<TimeSession> sessions = List.of(
                new TimeSession("s1", "t1", LocalDateTime.of(2026, 3, 10, 14, 0), 90),
                new TimeSession("s2", "t2", LocalDateTime.of(2026, 3, 10, 14, 30), 75),
                new TimeSession("s3", "t3", LocalDateTime.of(2026, 3, 9, 9, 0), 30),
                new TimeSession("s4", "t4", LocalDateTime.of(2026, 3, 8, 9, 30), 25),
                new TimeSession("s5", "t5", LocalDateTime.of(2026, 3, 7, 18, 0), 20),
                new TimeSession("s6", "t6", LocalDateTime.of(2026, 3, 10, 15, 45), 60)
        );
        FocusProductivityProfileBuilder builder = new FocusProductivityProfileBuilder(() -> sessions);

        FocusProductivityProfile profile = builder.buildProfile(LocalDate.of(2026, 3, 11), 28);

        assertTrue(profile.available());
        assertEquals(6, profile.totalSessions());
        assertEquals(300, profile.totalTrackedMinutes());
        assertEquals(50, profile.averageFocusMinutes());
        assertEquals(75, profile.stableFocusMinutes());
        assertFalse(profile.limitedHistory());
        assertEquals(24, profile.hourScores().size());
        assertEquals(7, profile.dayScores().size());

        FocusHourScore strongHour = profile.hourScores().stream()
                .filter(score -> score.hourOfDay() == 14)
                .findFirst()
                .orElseThrow();
        FocusHourScore weakHour = profile.hourScores().stream()
                .filter(score -> score.hourOfDay() == 18)
                .findFirst()
                .orElseThrow();
        assertEquals(2, strongHour.sessionCount());
        assertEquals(165, strongHour.trackedMinutes());
        assertTrue(strongHour.productivityScore() > weakHour.productivityScore());

        FocusDayScore monday = profile.dayScores().stream()
                .filter(score -> score.dayOfWeek() == DayOfWeek.TUESDAY)
                .findFirst()
                .orElseThrow();
        FocusDayScore saturday = profile.dayScores().stream()
                .filter(score -> score.dayOfWeek() == DayOfWeek.SATURDAY)
                .findFirst()
                .orElseThrow();
        assertTrue(monday.productivityScore() > saturday.productivityScore());
        assertTrue(profile.confidence() > 0.0);
        assertTrue(profile.switchDensityScore() > 0.0);
    }

    @Test
    void ignoresSessionsOutsideLookbackWindow() {
        List<TimeSession> sessions = List.of(
                new TimeSession("old", "t-old", LocalDateTime.of(2026, 1, 1, 10, 0), 120),
                new TimeSession("recent", "t-recent", LocalDateTime.of(2026, 3, 10, 10, 0), 60)
        );
        FocusProductivityProfileBuilder builder = new FocusProductivityProfileBuilder(() -> sessions);

        FocusProductivityProfile profile = builder.buildProfile(LocalDate.of(2026, 3, 11), 7);

        assertEquals(1, profile.totalSessions());
        assertEquals(60, profile.totalTrackedMinutes());
        FocusHourScore tenAm = profile.hourScores().stream()
                .filter(score -> score.hourOfDay() == 10)
                .findFirst()
                .orElseThrow();
        assertEquals(60, tenAm.trackedMinutes());
    }
}
