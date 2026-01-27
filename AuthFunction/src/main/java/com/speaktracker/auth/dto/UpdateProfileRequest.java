package com.speaktracker.auth.dto;

/**
 * 프로필 업데이트 요청 DTO
 */
public class UpdateProfileRequest {
    
    private String name;
    private String profileImage;  // S3 URL (이미지 업로드 후 설정)
    
    public UpdateProfileRequest() {
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getProfileImage() {
        return profileImage;
    }
    
    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }
}
