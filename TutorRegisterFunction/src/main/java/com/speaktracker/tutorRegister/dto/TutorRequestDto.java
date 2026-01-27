package com.speaktracker.tutorRegister.dto;

/**
 * 튜터의 요청 정보 DTO
 */
public class TutorRequestDto {
    private String requestId;
    private String studentEmail;
    private String studentName;
    private String status;
    private String message;
    private Long createdAt;
    private Long daysWaiting; // pending 요청인 경우만

    // Constructors
    public TutorRequestDto() {}

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getDaysWaiting() {
        return daysWaiting;
    }

    public void setDaysWaiting(Long daysWaiting) {
        this.daysWaiting = daysWaiting;
    }
}
