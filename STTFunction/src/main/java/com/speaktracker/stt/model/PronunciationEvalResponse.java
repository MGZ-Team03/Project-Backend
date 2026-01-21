package com.speaktracker.stt.model;

public class PronunciationEvalResponse {
    private boolean success;
    private PronunciationResult evaluation;
    private boolean saved;
    private String error;

    public PronunciationEvalResponse() {
    }

    public static PronunciationEvalResponse success(PronunciationResult evaluation, boolean saved) {
        PronunciationEvalResponse response = new PronunciationEvalResponse();
        response.success = true;
        response.evaluation = evaluation;
        response.saved = saved;
        return response;
    }

    public static PronunciationEvalResponse error(String errorMessage) {
        PronunciationEvalResponse response = new PronunciationEvalResponse();
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

    public PronunciationResult getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(PronunciationResult evaluation) {
        this.evaluation = evaluation;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
