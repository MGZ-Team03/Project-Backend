package com.speaktracker.auth.dto;

/**
 * 인증 응답 DTO (로그인 후 토큰 반환)
 */
public class AuthResponse {
    
    private String message;
    private String idToken;
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    
    public AuthResponse() {
    }
    
    public AuthResponse(String message, String idToken, String accessToken, String refreshToken, Integer expiresIn) {
        this.message = message;
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }
    
    // Getters and Setters
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getIdToken() {
        return idToken;
    }
    
    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
    
    public String getAccessToken() {
        return accessToken;
    }
    
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
    
    public String getRefreshToken() {
        return refreshToken;
    }
    
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
    
    public Integer getExpiresIn() {
        return expiresIn;
    }
    
    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }
}
