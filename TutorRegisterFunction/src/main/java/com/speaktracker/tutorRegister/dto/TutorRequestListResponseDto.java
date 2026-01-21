package com.speaktracker.tutorRegister.dto;

import java.util.List;

/**
 * 튜터의 요청 목록 응답 DTO
 */
public class TutorRequestListResponseDto {
    private List<TutorRequestDto> requests;
    private int totalPending;

    // Constructors
    public TutorRequestListResponseDto() {}

    public TutorRequestListResponseDto(List<TutorRequestDto> requests, int totalPending) {
        this.requests = requests;
        this.totalPending = totalPending;
    }

    // Getters and Setters
    public List<TutorRequestDto> getRequests() {
        return requests;
    }

    public void setRequests(List<TutorRequestDto> requests) {
        this.requests = requests;
    }

    public int getTotalPending() {
        return totalPending;
    }

    public void setTotalPending(int totalPending) {
        this.totalPending = totalPending;
    }
}
