package com.example.neuroflowplanner.model;

import java.time.LocalDateTime;

public class MoodEntry {
    private String id;
    private LocalDateTime timestamp;
    private int score; // 1-10 or 1-5
    private String note;
    private String analysis; // e.g., "Positive", "Stressed"

    public MoodEntry(String id, LocalDateTime timestamp, int score, String note, String analysis) {
        this.id = id;
        this.timestamp = timestamp;
        this.score = score;
        this.note = note;
        this.analysis = analysis;
    }

    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public int getScore() { return score; }
    public String getNote() { return note; }
    public String getAnalysis() { return analysis; }
}
