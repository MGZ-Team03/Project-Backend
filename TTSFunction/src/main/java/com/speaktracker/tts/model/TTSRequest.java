package com.speaktracker.tts.model;

public class TTSRequest {
    private String text;
    private String voiceId;

    public TTSRequest() {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    public void validate() {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text is required");
        }
        if (text.length() > 3000) {
            throw new IllegalArgumentException("text exceeds maximum length of 3000 characters");
        }
    }

    // voiceId가 없으면 기본값 사용
    public String getVoiceIdOrDefault() {
        return (voiceId == null || voiceId.trim().isEmpty()) ? "Joanna" : voiceId;
    }
}
