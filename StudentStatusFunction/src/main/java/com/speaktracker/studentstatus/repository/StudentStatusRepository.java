package com.speaktracker.studentstatus.repository;

import com.speaktracker.studentstatus.dto.StudentStatusRequest;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class StudentStatusRepository {
    private final DynamoDbClient dynamoDbClient;
    String tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");


    /**
     * 학생 상태 저장
     */
    public void saveStudentStatus(StudentStatusRequest studentStatusRequest) {
        getLogger().log(
                "=== Repository 실행 | 학생: " + studentStatusRequest.getStudentEmail()
                        + " | 상태: " + studentStatusRequest.getStatus()
                        + " | 방: " + studentStatusRequest.getRoom()
                        + " ==="
        );

        Map<String, AttributeValue> item = buildItem(studentStatusRequest);

        getLogger().log(tutorStudentsTable + ": " + item);

        try {
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tutorStudentsTable)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(putRequest);
            getLogger().log("✅ Item saved/updated successfully");

        } catch (Exception e) {
            getLogger().log("⚠️ Save failed: " + e.getMessage());
            throw new RuntimeException("Failed to save student status", e);
        }
    }

    /**
     * DynamoDB Item 생성
     */
    private Map<String, AttributeValue> buildItem(StudentStatusRequest request) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("tutor_email", AttributeValue.builder().s(request.getTutorEmail()).build());

        item.put("student_email", AttributeValue.builder()
                .s(request.getStudentEmail())
                .build());

        // status
        item.put("status", AttributeValue.builder()
                .s(getOrDefault(request.getStatus(), "active"))
                .build());

        // room
        item.put("room", AttributeValue.builder()
                .s(getOrDefault(request.getRoom(), "no room"))
                .build());

        // 타임스탬프
        item.put("assigned_at", AttributeValue.builder()
                .s(Instant.now().toString())
                .build());


        item.put("updated_at", AttributeValue.builder()
                .n(String.valueOf(Instant.now().toEpochMilli()))
                .build());

        return item;
    }

    /**
     * null 체크 및 기본값 반환
     */
    private String getOrDefault(String value, String defaultValue) {
        return (value != null && !value.trim().isEmpty()) ? value : defaultValue;
    }
}