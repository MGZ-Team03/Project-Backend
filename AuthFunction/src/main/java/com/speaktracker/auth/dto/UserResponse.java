package com.speaktracker.auth.dto;

/**
 * 사용자 정보 응답 DTO
 */
public class UserResponse {
    
    private String email;
    private String name;
    private String role;
    private String createdAt;
    
    public UserResponse() {
    }
    
    public UserResponse(String email, String name, String role, String createdAt) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    // Getters and Setters
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
