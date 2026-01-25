package com.speaktracker.tutorRegister.models;

import java.util.List;
import java.util.Map;

/**
 * 알림 엔티티
 */
public class Notification {
    private String notificationId;
    private String userEmail;
    private String type; // NEW_TUTOR_REQUEST, TUTOR_REQUEST_APPROVED, TUTOR_REQUEST_REJECTED
    private String title;
    private String message;
    private Map<String, Object> data;
    private Boolean isRead;
    private List<String> sentVia; // websocket, email
    private Long createdAt;
    private Long ttl; // TTL (30일)

    // Getters and Setters
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public List<String> getSentVia() {
        return sentVia;
    }

    public void setSentVia(List<String> sentVia) {
        this.sentVia = sentVia;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getTtl() {
        return ttl;
    }

    public void setTtl(Long ttl) {
        this.ttl = ttl;
    }

    // DynamoDB용 복합 키
    public String getNotificationIdTimestamp() {
        return notificationId + "#" + createdAt;
    }

    public String getIsReadCreatedAt() {
        return isRead + "#" + createdAt;
    }
}
