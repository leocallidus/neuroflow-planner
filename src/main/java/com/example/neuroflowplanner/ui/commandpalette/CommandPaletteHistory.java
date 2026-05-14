package com.example.neuroflowplanner.ui.commandpalette;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CommandPaletteHistory {
    public static final int DEFAULT_CAPACITY = 30;

    private final int capacity;
    private final Deque<String> recentKeys = new ArrayDeque<>();
    private final Map<String, Integer> usageCounts = new HashMap<>();

    public CommandPaletteHistory() {
        this(DEFAULT_CAPACITY);
    }

    public CommandPaletteHistory(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized void record(String rawKey) {
        String key = normalize(rawKey);
        if (key == null) {
            return;
        }
        recentKeys.remove(key);
        recentKeys.addFirst(key);
        usageCounts.merge(key, 1, Integer::sum);
        while (recentKeys.size() > capacity) {
            recentKeys.removeLast();
        }
    }

    public synchronized int rankOf(String rawKey) {
        String key = normalize(rawKey);
        if (key == null) {
            return -1;
        }
        int index = 0;
        for (String recentKey : recentKeys) {
            if (recentKey.equals(key)) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public synchronized List<String> recentKeys() {
        return List.copyOf(recentKeys);
    }

    public synchronized int countOf(String rawKey) {
        String key = normalize(rawKey);
        if (key == null) {
            return 0;
        }
        return Math.max(0, usageCounts.getOrDefault(key, 0));
    }

    public synchronized List<String> topFrequent(int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> keys = new ArrayList<>(usageCounts.keySet());
        keys.sort(
            Comparator
                .comparingInt((String key) -> usageCounts.getOrDefault(key, 0))
                .reversed()
                .thenComparingInt(this::rankOfRecentSafe)
                .thenComparing(String::compareToIgnoreCase)
        );
        if (keys.size() > safeLimit) {
            keys = new ArrayList<>(keys.subList(0, safeLimit));
        }
        return List.copyOf(keys);
    }

    public synchronized void clear() {
        recentKeys.clear();
        usageCounts.clear();
    }

    public synchronized List<String> takeRecent(int limit) {
        int safeLimit = Math.max(1, limit);
        List<String> out = new ArrayList<>(Math.min(safeLimit, recentKeys.size()));
        int count = 0;
        for (String key : recentKeys) {
            out.add(key);
            count++;
            if (count >= safeLimit) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private String normalize(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private int rankOfRecentSafe(String key) {
        int rank = rankOf(key);
        return rank < 0 ? Integer.MAX_VALUE : rank;
    }
}
