package com.speaktracker.tutorRegister.models;

/**
 * 튜터-학생 관계 엔티티
 */
public class TutorStudent {
    private String tutorEmail;
    private String studentEmail;
    private String assignedAt; // ISO 8601 DateTime
    private String status; // active, inactive
    private String requestId; // 참조용

    // Getters and Setters
    public String getTutorEmail() {
        return tutorEmail;
    }

    public void setTutorEmail(String tutorEmail) {
        this.tutorEmail = tutorEmail;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(String assignedAt) {
        this.assignedAt = assignedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
