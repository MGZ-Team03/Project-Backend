package com.speaktracker.auth.model;

/**
 * 인증 자격 증명 모델
 */
public class AuthCredentials {
    
    private String email;
    private String password;
    
    public AuthCredentials() {
    }
    
    public AuthCredentials(String email, String password) {
        this.email = email;
        this.password = password;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
}
