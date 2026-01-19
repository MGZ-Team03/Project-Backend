package com.speaktracker;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 피드백 메시지 모델
 */
public class FeedbackMessage {
    
    @JsonProperty("feedback_id")
    private String feedbackId;
    
    @JsonProperty("tutor_email")
    private String tutorEmail;
    
    @JsonProperty("student_email")
    private String studentEmail;
    
    private String message;
    
    @JsonProperty("message_type")
    private String messageType;
    
    @JsonProperty("session_id")
    private String sessionId;
    
    private String timestamp;
    
    @JsonProperty("websocket_sent")
    private boolean websocketSent;
    
    @JsonProperty("audio_url")
    private String audioUrl;
    
    // Default constructor for Jackson
    public FeedbackMessage() {}
    
    // Getters
    public String getFeedbackId() {
        return feedbackId;
    }
    
    public String getTutorEmail() {
        return tutorEmail;
    }
    
    public String getStudentEmail() {
        return studentEmail;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public boolean isWebsocketSent() {
        return websocketSent;
    }
    
    public String getAudioUrl() {
        return audioUrl;
    }
    
    // Setters
    public void setFeedbackId(String feedbackId) {
        this.feedbackId = feedbackId;
    }
    
    public void setTutorEmail(String tutorEmail) {
        this.tutorEmail = tutorEmail;
    }
    
    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public void setWebsocketSent(boolean websocketSent) {
        this.websocketSent = websocketSent;
    }
    
    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }
    
    @Override
    public String toString() {
        return "FeedbackMessage{" +
                "feedbackId='" + feedbackId + '\'' +
                ", tutorEmail='" + tutorEmail + '\'' +
                ", studentEmail='" + studentEmail + '\'' +
                ", message='" + message + '\'' +
                ", messageType='" + messageType + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", websocketSent=" + websocketSent +
                ", audioUrl='" + audioUrl + '\'' +
                '}';
    }
}
