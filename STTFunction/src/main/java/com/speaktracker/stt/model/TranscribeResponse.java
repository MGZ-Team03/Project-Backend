package com.speaktracker.stt.model;

public class TranscribeResponse {
    private boolean success;
    private String transcript;
    private String error;

    public TranscribeResponse() {
    }

    public static TranscribeResponse success(String transcript) {
        TranscribeResponse response = new TranscribeResponse();
        response.success = true;
        response.transcript = transcript;
        return response;
    }

    public static TranscribeResponse error(String errorMessage) {
        TranscribeResponse response = new TranscribeResponse();
        response.success = false;
        response.error = errorMessage;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTranscript() {
        return transcript;
    }

    public void setTranscript(String transcript) {
        this.transcript = transcript;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
