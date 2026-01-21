package com.speaktracker.tutorRegister.dto;

/**
 * 요청 처리 응답 DTO (생성/승인/거부/취소)
 */
public class RequestResponseDto {
    private String requestId;
    private String tutorEmail;
    private String studentEmail;
    private String status; // "pending" | "approved" | "rejected" | "cancelled"
    private Long createdAt;
    private Long processedAt;
    private String rejectionReason;
    private String message;

    // Constructors
    public RequestResponseDto() {}

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Long processedAt) {
        this.processedAt = processedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
