package com.speaktracker.tts.model;

public class TTSJobMessage {
    private String jobId;
    private String text;
    private String voiceId;
    private String s3Key;

    public TTSJobMessage() {}

    public TTSJobMessage(String jobId, String text, String voiceId, String s3Key) {
        this.jobId = jobId;
        this.text = text;
        this.voiceId = voiceId;
        this.s3Key = s3Key;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

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

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }
}
