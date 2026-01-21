package com.speaktracker.auth.dto;

/**
 * 이메일 인증 확인 요청 DTO
 */
public class ConfirmRequest {
    
    private String email;
    private String code;
    
    public ConfirmRequest() {
    }
    
    public ConfirmRequest(String email, String code) {
        this.email = email;
        this.code = code;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
}
