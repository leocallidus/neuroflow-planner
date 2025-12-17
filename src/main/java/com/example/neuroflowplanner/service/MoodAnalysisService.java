package com.example.neuroflowplanner.service;

import java.util.Arrays;
import java.util.List;

public class MoodAnalysisService {

    private static final List<String> POSITIVE_WORDS = Arrays.asList(
        "happy", "good", "great", "excellent", "excited", "productive", "calm", "joy", "success", "wonderful", "cool",
        "счастлив", "хорошо", "отлично", "радость", "продуктивно", "спокойно", "успех", "круто"
    );

    private static final List<String> NEGATIVE_WORDS = Arrays.asList(
        "sad", "bad", "tired", "stressed", "anxious", "angry", "poor", "depressed", "fail", "busy", "exhausted",
        "грустно", "плохо", "устал", "стресс", "тревога", "злость", "депрессия", "провал", "занят", "истощен"
    );

    public MoodAnalysisResult analyze(String text, int userScore) {
        if (text == null || text.trim().isEmpty()) {
            return new MoodAnalysisResult(userScore, getLabelForScore(userScore));
        }

        String lowerText = text.toLowerCase();
        int posCount = 0;
        int negCount = 0;

        for (String w : POSITIVE_WORDS) {
            if (lowerText.contains(w)) posCount++;
        }
        for (String w : NEGATIVE_WORDS) {
            if (lowerText.contains(w)) negCount++;
        }

        // Adjust score based on text if it significantly differs
        // This is a simple heuristic
        int calculatedScore = userScore;
        
        if (posCount > negCount) {
             // Bias towards positive
             if (calculatedScore < 5) calculatedScore += 1;
        } else if (negCount > posCount) {
            // Bias towards negative
            if (calculatedScore > 1) calculatedScore -= 1;
        }

        String analysisLabel = determineLabel(posCount, negCount, calculatedScore);

        return new MoodAnalysisResult(calculatedScore, analysisLabel);
    }

    private String getLabelForScore(int score) {
        if (score >= 8) return "Отличное";
        if (score >= 6) return "Хорошее";
        if (score >= 4) return "Нормальное";
        if (score >= 2) return "Так себе";
        return "Плохое";
    }

    private String determineLabel(int pos, int neg, int score) {
        if (pos > 0 && neg == 0) return "Позитивное";
        if (neg > 0 && pos == 0) return "Негативное";
        if (pos > 0 && neg > 0) return "Смешанное";
        return getLabelForScore(score); // Fallback to score-based
    }

    public static class MoodAnalysisResult {
        public final int adjustedScore;
        public final String label;

        public MoodAnalysisResult(int adjustedScore, String label) {
            this.adjustedScore = adjustedScore;
            this.label = label;
        }
    }
}
