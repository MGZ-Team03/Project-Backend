package com.speaktracker.studentstatus.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentStatusResponse {
    private String studentEmail;
    private String room;
    private String status;
    private String assigned_at;

    public static StudentStatusResponse save(String studentEmail, String room, String status, String assigned_at) {
        return StudentStatusResponse.builder()
                .studentEmail(studentEmail)
                .room(room)
                .status(status)
                .assigned_at(assigned_at)
                .build();

    }
}
