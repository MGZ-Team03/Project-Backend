package com.speaktracker.studentstatus.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentStatusRequest {
    private String studentEmail;
    private String tutorEmail;
    private String room;
    private String status;

}
