package com.speaktracker.stt.model;

public class TranscribeRequest {
    private String languageCode;

    public TranscribeRequest() {
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getLanguageCodeOrDefault() {
        return (languageCode != null && !languageCode.isEmpty()) ? languageCode : "en-US";
    }
}
