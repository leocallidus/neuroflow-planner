package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.service.imageflow.ImageRequestProgress;
import com.example.neuroflowplanner.service.imageflow.ImageRequestState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageRequestLifecycleStateMachineTest {

    private static final Map<ImageRequestState, Set<ImageRequestState>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    @Test
    void successfulLifecyclePathIsValid() {
        assertPathIsValid(List.of(
            ImageRequestState.QUEUED,
            ImageRequestState.SENDING,
            ImageRequestState.PROVIDER_ACCEPTED,
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.SAVING,
            ImageRequestState.DONE
        ));
    }

    @Test
    void retryFallbackAndResumeLifecyclePathIsValid() {
        assertPathIsValid(List.of(
            ImageRequestState.QUEUED,
            ImageRequestState.SENDING,
            ImageRequestState.PROVIDER_ACCEPTED,
            ImageRequestState.POLLING,
            ImageRequestState.RETRYING,
            ImageRequestState.RESUMING,
            ImageRequestState.POLLING,
            ImageRequestState.RETRYING,
            ImageRequestState.FALLBACK_MODEL,
            ImageRequestState.SENDING,
            ImageRequestState.PROVIDER_ACCEPTED,
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.SAVING,
            ImageRequestState.DONE
        ));
    }

    @Test
    void pausedAndTerminalStatesHaveNoUnexpectedOutgoingTransitions() {
        assertFalse(ImageRequestState.PAUSED.isTerminal());
        for (ImageRequestState state : List.of(
            ImageRequestState.DONE,
            ImageRequestState.FAILED,
            ImageRequestState.CANCELLED
        )) {
            assertTrue(state.isTerminal(), "Expected terminal state: " + state);
            assertTrue(ALLOWED_TRANSITIONS.get(state).isEmpty(), "Terminal state must not have outgoing transitions: " + state);
        }
    }

    @Test
    void progressNormalizesInvalidValues() {
        ImageRequestProgress progress = new ImageRequestProgress(-150L, 0, 0, true);
        assertEquals(0L, progress.elapsedMs());
        assertEquals(1, progress.attempt());
        assertEquals(1, progress.maxAttempts());
        assertTrue(progress.terminal());
    }

    private static void assertPathIsValid(List<ImageRequestState> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            ImageRequestState from = path.get(i);
            ImageRequestState to = path.get(i + 1);
            assertTrue(ALLOWED_TRANSITIONS.get(from).contains(to), "Invalid transition: " + from + " -> " + to);
        }
    }

    private static Map<ImageRequestState, Set<ImageRequestState>> buildAllowedTransitions() {
        EnumMap<ImageRequestState, Set<ImageRequestState>> transitions = new EnumMap<>(ImageRequestState.class);
        transitions.put(ImageRequestState.QUEUED, Set.of(ImageRequestState.SENDING, ImageRequestState.CANCELLED, ImageRequestState.FAILED));
        transitions.put(ImageRequestState.SENDING, Set.of(
            ImageRequestState.PROVIDER_ACCEPTED,
            ImageRequestState.RETRYING,
            ImageRequestState.FALLBACK_MODEL,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.PROVIDER_ACCEPTED, Set.of(
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.RETRYING,
            ImageRequestState.RESUMING,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.POLLING, Set.of(
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.RETRYING,
            ImageRequestState.RESUMING,
            ImageRequestState.PAUSED,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.DOWNLOADING, Set.of(
            ImageRequestState.SAVING,
            ImageRequestState.RETRYING,
            ImageRequestState.RESUMING,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.SAVING, Set.of(
            ImageRequestState.DONE,
            ImageRequestState.RETRYING,
            ImageRequestState.RESUMING,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.RETRYING, Set.of(
            ImageRequestState.SENDING,
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.RESUMING,
            ImageRequestState.FALLBACK_MODEL,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.FALLBACK_MODEL, Set.of(
            ImageRequestState.SENDING,
            ImageRequestState.PROVIDER_ACCEPTED,
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.RESUMING, Set.of(
            ImageRequestState.POLLING,
            ImageRequestState.DOWNLOADING,
            ImageRequestState.SAVING,
            ImageRequestState.CANCELLED,
            ImageRequestState.FAILED
        ));
        transitions.put(ImageRequestState.PAUSED, Set.of(ImageRequestState.RESUMING, ImageRequestState.CANCELLED));
        transitions.put(ImageRequestState.DONE, Set.of());
        transitions.put(ImageRequestState.FAILED, Set.of());
        transitions.put(ImageRequestState.CANCELLED, Set.of());
        return Map.copyOf(transitions);
    }
}
