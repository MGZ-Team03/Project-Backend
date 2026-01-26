package com.speaktracker.studentstatus.repository;

import com.speaktracker.studentstatus.dto.StudentStatusRequest;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class StudentStatusRepository {
    private final DynamoDbClient dynamoDbClient;
    String tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");


    /**
     * 학생 상태 저장
     */
    public void saveStudentStatus(StudentStatusRequest studentStatusRequest) {
        String tutorEmail;
        String studentEmail;

        getLogger().log(
                "===✅ Repository 실행 | 학생: " + studentStatusRequest.getStudentEmail()
                        + " | 상태: " + studentStatusRequest.getStatus()
                        + " | 방: " + studentStatusRequest.getRoom()
                        + " ==="
        );


        Map<String, AttributeValue> emails = findByStudentEmail(studentStatusRequest.getStudentEmail());
        if (emails == null) {
            getLogger().log("⚠️ 등록되지 않은 학생입니다. 새로 저장합니다.");
            tutorEmail = "undefined";  // "undfined" 오타 수정
            studentEmail = studentStatusRequest.getStudentEmail();
        }
        else {
            getLogger().log("⚠️ 등록된 학생입니다");
            tutorEmail = emails.get("tutor_email") != null && !emails.get("tutor_email").s().equals("undefined")
                    ? emails.get("tutor_email").s()
                    : "undefined";
            studentEmail = emails.get("student_email").s();
        }

        getLogger().log("📌tutorEmail: " + tutorEmail + " studentEmail: " + studentEmail);

        Map<String, AttributeValue> item = buildItem(tutorEmail, studentEmail, studentStatusRequest);

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
    private Map<String, AttributeValue> buildItem(String tutorEmail, String studentEmail,StudentStatusRequest request) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put(
                "tutor_email",
                AttributeValue.builder()
                        .s(tutorEmail)
                        .build()
        );

        item.put("student_email", AttributeValue.builder()
                .s(studentEmail)
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

    private Map<String, AttributeValue> findByStudentEmail(String studentEmail) {
        getLogger().log("start.findByStudentEmail: " + studentEmail);
        QueryRequest request = QueryRequest.builder()
                .tableName(tutorStudentsTable)
                .indexName("student_email-index")
                .keyConditionExpression("student_email = :email")
                .expressionAttributeValues(Map.of(
                        ":email", AttributeValue.builder().s(studentEmail).build()
                ))
                .limit(1)
                .build();

        QueryResponse response = dynamoDbClient.query(request);
        if (response.count() == 0) {
            getLogger().log("❌ student not found: " + studentEmail);
            return null;
        }

        getLogger().log("✅ student found by GSI");
        return response.items().getFirst();
    }
}