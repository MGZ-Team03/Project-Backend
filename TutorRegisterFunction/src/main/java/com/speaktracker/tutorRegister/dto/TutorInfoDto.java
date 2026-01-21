package com.speaktracker.tutorRegister.dto;

import java.util.List;

/**
 * 튜터 정보 DTO (학생용 튜터 목록 조회)
 */
public class TutorInfoDto {
    private String email;
    private String name;
    private String bio;
    private List<String> specialties;
    private String profileImage;
    private int maxStudents;
    private int currentStudents;
    private Boolean isAccepting;
    private String myRequestStatus; // null | "pending" | "approved" | "rejected" | "registered"

    // Constructors
    public TutorInfoDto() {}

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

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public int getMaxStudents() {
        return maxStudents;
    }

    public void setMaxStudents(int maxStudents) {
        this.maxStudents = maxStudents;
    }

    public int getCurrentStudents() {
        return currentStudents;
    }

    public void setCurrentStudents(int currentStudents) {
        this.currentStudents = currentStudents;
    }

    public Boolean getIsAccepting() {
        return isAccepting;
    }

    public void setIsAccepting(Boolean isAccepting) {
        this.isAccepting = isAccepting;
    }

    public String getMyRequestStatus() {
        return myRequestStatus;
    }

    public void setMyRequestStatus(String myRequestStatus) {
        this.myRequestStatus = myRequestStatus;
    }
}
