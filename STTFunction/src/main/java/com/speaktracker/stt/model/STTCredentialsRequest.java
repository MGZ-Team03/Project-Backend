package com.speaktracker.stt.model;

public class STTCredentialsRequest {
    private String languageCode;
    private Integer sampleRate;

    public STTCredentialsRequest() {
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

    public String getLanguageCodeOrDefault() {
        return (languageCode == null || languageCode.trim().isEmpty()) ? "en-US" : languageCode;
    }

    public int getSampleRateOrDefault() {
        return (sampleRate == null) ? 16000 : sampleRate;
    }
}
