package com.speaktracker.auth.dto;

/**
 * 토큰 갱신 요청 DTO
 */
public class RefreshRequest {
    
    private String refreshToken;
    
    public RefreshRequest() {
    }
    
    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    // Getters and Setters
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
