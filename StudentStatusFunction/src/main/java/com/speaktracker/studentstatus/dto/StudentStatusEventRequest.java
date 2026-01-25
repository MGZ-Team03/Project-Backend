package com.speaktracker.studentstatus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StudentStatusEventRequest {
    private String action;
    private StudentStatusRequest data;
}
