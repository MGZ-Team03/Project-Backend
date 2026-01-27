package com.speaktracker.auth.model;

/**
 * 사용자 도메인 모델
 */
public class User {
    
    private String email;
    private String name;
    private String role;
    private String createdAt;
    private String userSub;
    private String profileImage;      // S3 URL
    private String learningLevel;     // beginner, intermediate, advanced (AI 분석 결과)
    private String lastLevelEvalDate; // 마지막 레벨 평가 날짜 (YYYY-MM-DD)
    
    public User() {
    }
    
    public User(String email, String name, String role, String createdAt, String userSub) {
        this.email = email;
        this.name = name;
        this.role = role;
        this.createdAt = createdAt;
        this.userSub = userSub;
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
    
    public String getUserSub() {
        return userSub;
    }
    
    public void setUserSub(String userSub) {
        this.userSub = userSub;
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

    public String getLastLevelEvalDate() {
        return lastLevelEvalDate;
    }

    public void setLastLevelEvalDate(String lastLevelEvalDate) {
        this.lastLevelEvalDate = lastLevelEvalDate;
    }
}
