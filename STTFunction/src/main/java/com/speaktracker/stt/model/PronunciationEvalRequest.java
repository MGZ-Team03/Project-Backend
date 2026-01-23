package com.speaktracker.stt.model;

public class PronunciationEvalRequest {
    private String originalText;
    private String transcribedText;
    private String sentenceId;
    private String sessionId;
    private Long audioDurationMs;

    public PronunciationEvalRequest() {
    }

    public void validate() {
        if (originalText == null || originalText.trim().isEmpty()) {
            throw new IllegalArgumentException("originalText is required");
        }
        if (transcribedText == null) {
            throw new IllegalArgumentException("transcribedText is required");
        }
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getTranscribedText() {
        return transcribedText;
    }

    public void setTranscribedText(String transcribedText) {
        this.transcribedText = transcribedText;
    }

    public String getSentenceId() {
        return sentenceId;
    }

    public void setSentenceId(String sentenceId) {
        this.sentenceId = sentenceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getAudioDurationMs() {
        return audioDurationMs;
    }

    public void setAudioDurationMs(Long audioDurationMs) {
        this.audioDurationMs = audioDurationMs;
    }
}
