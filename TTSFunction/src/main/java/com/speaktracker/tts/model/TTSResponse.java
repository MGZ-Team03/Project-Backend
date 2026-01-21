package com.speaktracker.tts.model;

public class TTSResponse {
    private boolean success;
    private String audioUrl;
    private int expiresIn;
    private boolean cached;
    private String error;

    public TTSResponse() {}

    public static TTSResponse success(String audioUrl, int expiresIn, boolean cached) {
        TTSResponse response = new TTSResponse();
        response.success = true;
        response.audioUrl = audioUrl;
        response.expiresIn = expiresIn;
        response.cached = cached;
        return response;
    }

    public static TTSResponse error(String errorMessage) {
        TTSResponse response = new TTSResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public boolean isCached() {
        return cached;
    }

    public void setCached(boolean cached) {
        this.cached = cached;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
