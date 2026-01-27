package com.speaktracker.auth.dto;

/**
 * 사용자 정보 응답 DTO
 */
public class UserResponse {
    
    private String email;
    private String name;
    private String role;
    private String createdAt;
    private String profileImage;
    private String learningLevel;
    
    public UserResponse() {
    }
    
    public UserResponse(String email, String name, String role, String createdAt) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    public UserResponse(String email, String name, String role, String createdAt, 
                        String profileImage, String learningLevel) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
        this.profileImage = profileImage;
        this.learningLevel = learningLevel;
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
    
    public String getProfileImage() {
        return profileImage;
    }
    
    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
    
    public String getLearningLevel() {
        return learningLevel;
    }
    
    public void setLearningLevel(String learningLevel) {
        this.learningLevel = learningLevel;
    }
}
