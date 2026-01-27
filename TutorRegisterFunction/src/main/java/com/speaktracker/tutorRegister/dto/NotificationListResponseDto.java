package com.speaktracker.tutorRegister.dto;

import java.util.List;

/**
 * 알림 목록 응답 DTO
 */
public class NotificationListResponseDto {
    private List<NotificationDto> notifications;
    private Integer unreadCount;

    // Constructors
    public NotificationListResponseDto() {}

    public NotificationListResponseDto(List<NotificationDto> notifications, Integer unreadCount) {
        this.notifications = notifications;
        this.unreadCount = unreadCount;
    }

    // Getters and Setters
    public List<NotificationDto> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<NotificationDto> notifications) {
        this.notifications = notifications;
    }

    public Integer getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }
}
