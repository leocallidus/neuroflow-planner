package com.example.neuroflowplanner.service;

import com.example.neuroflowplanner.service.chatflow.ChatRequestProgress;
import com.example.neuroflowplanner.service.chatflow.ChatRequestState;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatRequestLifecycleStateMachineTest {

    private static final Map<ChatRequestState, Set<ChatRequestState>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

    @Test
    void successfulLifecyclePathIsValid() {
        assertPathIsValid(List.of(
            ChatRequestState.QUEUED,
            ChatRequestState.SENDING,
            ChatRequestState.WAITING_PROVIDER,
            ChatRequestState.GENERATING,
            ChatRequestState.POST_PROCESSING,
            ChatRequestState.DONE
        ));
    }

    @Test
    void retryAndFallbackLifecyclePathIsValid() {
        assertPathIsValid(List.of(
            ChatRequestState.QUEUED,
            ChatRequestState.SENDING,
            ChatRequestState.WAITING_PROVIDER,
            ChatRequestState.RETRYING,
            ChatRequestState.WAITING_PROVIDER,
            ChatRequestState.FALLBACK_MODEL,
            ChatRequestState.GENERATING,
            ChatRequestState.POST_PROCESSING,
            ChatRequestState.DONE
        ));
    }

    @Test
    void terminalStatesAreTerminalAndHaveNoOutgoingTransitions() {
        for (ChatRequestState state : List.of(
            ChatRequestState.DONE,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.FAILED,
            ChatRequestState.CANCELLED
        )) {
            assertTrue(state.isTerminal(), "Expected terminal state: " + state);
            assertTrue(ALLOWED_TRANSITIONS.get(state).isEmpty(), "Terminal state must not have outgoing transitions: " + state);
        }
        for (ChatRequestState state : ChatRequestState.values()) {
            if (!List.of(ChatRequestState.DONE, ChatRequestState.PARTIAL_DONE, ChatRequestState.FAILED, ChatRequestState.CANCELLED).contains(state)) {
                assertFalse(state.isTerminal(), "Expected non-terminal state: " + state);
            }
        }
    }

    @Test
    void progressNormalizesInvalidValues() {
        ChatRequestProgress progress = new ChatRequestProgress(-125L, 0, 0, false);
        assertEquals(0L, progress.elapsedMs());
        assertEquals(1, progress.attempt());
        assertEquals(1, progress.maxAttempts());
        assertFalse(progress.terminal());
    }

    private static void assertPathIsValid(List<ChatRequestState> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            ChatRequestState from = path.get(i);
            ChatRequestState to = path.get(i + 1);
            assertTrue(
                ALLOWED_TRANSITIONS.get(from).contains(to),
                "Invalid transition: " + from + " -> " + to
            );
        }
    }

    private static Map<ChatRequestState, Set<ChatRequestState>> buildAllowedTransitions() {
        EnumMap<ChatRequestState, Set<ChatRequestState>> transitions = new EnumMap<>(ChatRequestState.class);
        transitions.put(ChatRequestState.QUEUED, Set.of(ChatRequestState.SENDING, ChatRequestState.CANCELLED, ChatRequestState.FAILED));
        transitions.put(ChatRequestState.SENDING, Set.of(ChatRequestState.WAITING_PROVIDER, ChatRequestState.CANCELLED, ChatRequestState.FAILED));
        transitions.put(ChatRequestState.WAITING_PROVIDER, Set.of(
            ChatRequestState.GENERATING,
            ChatRequestState.RETRYING,
            ChatRequestState.FALLBACK_MODEL,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.CANCELLED,
            ChatRequestState.FAILED
        ));
        transitions.put(ChatRequestState.GENERATING, Set.of(
            ChatRequestState.POST_PROCESSING,
            ChatRequestState.RETRYING,
            ChatRequestState.FALLBACK_MODEL,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.CANCELLED,
            ChatRequestState.FAILED
        ));
        transitions.put(ChatRequestState.RETRYING, Set.of(
            ChatRequestState.WAITING_PROVIDER,
            ChatRequestState.GENERATING,
            ChatRequestState.FALLBACK_MODEL,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.CANCELLED,
            ChatRequestState.FAILED
        ));
        transitions.put(ChatRequestState.FALLBACK_MODEL, Set.of(
            ChatRequestState.WAITING_PROVIDER,
            ChatRequestState.GENERATING,
            ChatRequestState.POST_PROCESSING,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.CANCELLED,
            ChatRequestState.FAILED
        ));
        transitions.put(ChatRequestState.POST_PROCESSING, Set.of(
            ChatRequestState.DONE,
            ChatRequestState.PARTIAL_DONE,
            ChatRequestState.CANCELLED,
            ChatRequestState.FAILED
        ));
        transitions.put(ChatRequestState.DONE, Set.of());
        transitions.put(ChatRequestState.PARTIAL_DONE, Set.of());
        transitions.put(ChatRequestState.FAILED, Set.of());
        transitions.put(ChatRequestState.CANCELLED, Set.of());
        return Map.copyOf(transitions);
    }
}
