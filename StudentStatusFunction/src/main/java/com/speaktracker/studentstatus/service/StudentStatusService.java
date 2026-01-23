package com.speaktracker.studentstatus.service;

import com.speaktracker.studentstatus.dto.StudentStatusEventRequest;
import com.speaktracker.studentstatus.dto.StudentStatusRequest;
import com.speaktracker.studentstatus.dto.StudentStatusResponse;
import com.speaktracker.studentstatus.repository.StudentStatusRepository;
import lombok.RequiredArgsConstructor;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;


@RequiredArgsConstructor
public class StudentStatusService {
    private final StudentStatusRepository repository;

    /**
     * 학생 상태 저장 (저장만!)
     */
    public StudentStatusResponse saveStudentStatus(StudentStatusEventRequest request) {
        getLogger().log("getTutorEmail"+request.getData().getTutorEmail());
        getLogger().log("getStudentEmail"+request.getData().getStudentEmail());

        // Repository 호출 - 저장만 함
        repository.saveStudentStatus(request.getData());

        // 응답 생성
        return StudentStatusResponse.builder()
                .studentEmail(request.getData().getStudentEmail())
                .status(request.getData().getStatus())
                .room(request.getData().getRoom())
                .build();
    }


}