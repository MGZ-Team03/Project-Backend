package com.speaktracker.tutorRegister.models;

import java.util.List;

/**
 * 사용자(학생/튜터) 엔티티
 */
public class User {
    private String email;
    private String name;
    private String role; // student, tutor
    private String profileImage;
    private String bio;
    private List<String> specialties;
    private Integer maxStudents;
    private Boolean isAccepting;
    private Long createdAt;

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

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<String> getSpecialties() {
        return specialties;
    }

    public void setSpecialties(List<String> specialties) {
        this.specialties = specialties;
    }

    public Integer getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(Integer maxStudents) {
        this.maxStudents = maxStudents;
    }

    public Boolean getIsAccepting() {
        return isAccepting;
    }

    public void setIsAccepting(Boolean isAccepting) {
        this.isAccepting = isAccepting;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
