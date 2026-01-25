package com.speaktracker.tutorRegister.dto;

import java.util.List;

/**
 * 학생의 요청 목록 응답 DTO
 */
public class StudentRequestListResponseDto {
    private List<StudentRequestDto> requests;

    // Constructors
    public StudentRequestListResponseDto() {}

    public StudentRequestListResponseDto(List<StudentRequestDto> requests) {
        this.requests = requests;
    }

    // Getters and Setters
    public List<StudentRequestDto> getRequests() {
        return requests;
    }

    public void setRequests(List<StudentRequestDto> requests) {
        this.requests = requests;
    }
}
