package com.speaktracker.tts.model;

public class JobStatus {
    private String jobId;
    private String status; // PROCESSING, COMPLETED, FAILED
    private String audioUrl;
    private String error;
    private Long ttl;

    public JobStatus() {}

    public JobStatus(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }

    public static JobStatus processing(String jobId) {
        return new JobStatus(jobId, "PROCESSING");
    }

    public static JobStatus completed(String jobId, String audioUrl) {
        JobStatus status = new JobStatus(jobId, "COMPLETED");
        status.setAudioUrl(audioUrl);
        return status;
    }

    public static JobStatus failed(String jobId, String error) {
        JobStatus status = new JobStatus(jobId, "FAILED");
        status.setError(error);
        return status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }
}
