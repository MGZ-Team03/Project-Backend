package com.speaktracker.tutorRegister.dto;

import java.util.List;
import java.util.Map;

/**
 * 알림 정보 DTO
 */
public class NotificationDto {
    private String notificationId;
    private String notificationIdTimestamp;  // DynamoDB 키 (notification_id#timestamp)
    private String type;
    private String title;
    private String message;
    private Map<String, Object> data;
    private Boolean isRead;
    private List<String> sentVia;
    private Long createdAt;

    // Constructors
    public NotificationDto() {}

    // Getters and Setters
    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public String getNotificationIdTimestamp() {
        return notificationIdTimestamp;
    }

    public void setNotificationIdTimestamp(String notificationIdTimestamp) {
        this.notificationIdTimestamp = notificationIdTimestamp;
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
}
