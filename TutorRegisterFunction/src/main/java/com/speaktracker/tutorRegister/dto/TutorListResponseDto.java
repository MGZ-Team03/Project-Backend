package com.speaktracker.tutorRegister.dto;

import java.util.List;

/**
 * 튜터 목록 응답 DTO
 */
public class TutorListResponseDto {
    private List<TutorInfoDto> tutors;

    // Constructors
    public TutorListResponseDto() {}

    public TutorListResponseDto(List<TutorInfoDto> tutors) {
        this.tutors = tutors;
    }

    // Getters and Setters
    public List<TutorInfoDto> getTutors() {
        return tutors;
    }

    public void setTutors(List<TutorInfoDto> tutors) {
        this.tutors = tutors;
    }
}
