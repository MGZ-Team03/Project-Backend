package com.speaktracker.auth.model;

/**
 * JWT 토큰 정보 모델
 */
public class TokenInfo {
    
    private String idToken;
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    
    public TokenInfo() {
    }
    
    public TokenInfo(String idToken, String accessToken, String refreshToken, Integer expiresIn) {
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }
    
    // Getters and Setters
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
