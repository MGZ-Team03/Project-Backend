package com.speaktracker.stt.model;

import java.util.List;

public class PronunciationResult {
    private int overallScore;
    private int wordAccuracy;
    private int sequenceScore;
    private int completenessScore;
    private List<String> missedWords;
    private List<String> extraWords;
    private String feedback;
    private String grade;

    public PronunciationResult() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private PronunciationResult result = new PronunciationResult();

        public Builder overallScore(int overallScore) {
            result.overallScore = overallScore;
            return this;
        }

        public Builder wordAccuracy(int wordAccuracy) {
            result.wordAccuracy = wordAccuracy;
            return this;
        }

        public Builder sequenceScore(int sequenceScore) {
            result.sequenceScore = sequenceScore;
            return this;
        }

        public Builder completenessScore(int completenessScore) {
            result.completenessScore = completenessScore;
            return this;
        }

        public Builder missedWords(List<String> missedWords) {
            result.missedWords = missedWords;
            return this;
        }

        public Builder extraWords(List<String> extraWords) {
            result.extraWords = extraWords;
            return this;
        }

        public Builder feedback(String feedback) {
            result.feedback = feedback;
            return this;
        }

        public Builder grade(String grade) {
            result.grade = grade;
            return this;
        }

        public PronunciationResult build() {
            return result;
        }
    }

    // Getters and Setters
    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public int getWordAccuracy() {
        return wordAccuracy;
    }

    public void setWordAccuracy(int wordAccuracy) {
        this.wordAccuracy = wordAccuracy;
    }

    public int getSequenceScore() {
        return sequenceScore;
    }

    public void setSequenceScore(int sequenceScore) {
        this.sequenceScore = sequenceScore;
    }

    public int getCompletenessScore() {
        return completenessScore;
    }

    public void setCompletenessScore(int completenessScore) {
        this.completenessScore = completenessScore;
    }

    public List<String> getMissedWords() {
        return missedWords;
    }

    public void setMissedWords(List<String> missedWords) {
        this.missedWords = missedWords;
    }

    public List<String> getExtraWords() {
        return extraWords;
    }

    public void setExtraWords(List<String> extraWords) {
        this.extraWords = extraWords;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }
}
