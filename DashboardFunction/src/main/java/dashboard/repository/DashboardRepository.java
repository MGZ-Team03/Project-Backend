package dashboard.repository;

import dashboard.dto.StatusRequest;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;


import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class DashboardRepository {
    private final DynamoDbClient dynamoDbClient;
    private final String tutorStudentsTableName;

    /**
     * 튜터-학생 관계 저장 (Upsert)
     */
    public void saveTutorStudent(StatusRequest request) {
        getLogger().log("=== Repository.saveTutorStudent 실행 ===");

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("tutor_email", AttributeValue.builder().s(request.getTutorEmail()).build());
        item.put("student_email", AttributeValue.builder().s(request.getStudentEmail()).build());
        item.put("assigned_at", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("status", AttributeValue.builder().s(request.getStatus()).build());
        item.put("room", AttributeValue.builder().s(request.getRoom()).build());
        item.put("updated_at", AttributeValue.builder().s(DateTime.now().toString()).build());

        try {
            PutItemRequest putRequest = PutItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(putRequest);
            getLogger().log("✅ Item saved/updated successfully");

        } catch (Exception e) {
            getLogger().log("⚠️ Save failed: " + e.getMessage());
            throw new RuntimeException("Failed to save tutor-student mapping", e);
        }
    }

    /**
     * 튜터-학생 관계 존재 여부 확인
     */
    public boolean existsTutorStudent(String tutorEmail, String studentEmail) {
        try {
            getLogger().log("=== Repository: Check Exists | Tutor: " + tutorEmail + " | Student: " + studentEmail + " ===");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);

            boolean exists = response.hasItem();
            getLogger().log("Exists: " + exists);
            return exists;

        } catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Error: " + e.getMessage());
            return false;
        }
    }

    /**
     * 상태 업데이트
     */
    public void updateStatus(String tutorEmail, String studentEmail, String status, String room) {
        try {
            getLogger().log("=== Repository: Update Status | Table: " + tutorStudentsTableName +
                    " | Tutor: " + tutorEmail + " | Student: " + studentEmail +
                    " | New Status: " + status + " | Room: " + room + " ===");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":newStatus", AttributeValue.fromS(status));
            attributeValues.put(":updatedAt", AttributeValue.fromS(DateTime.now().toString()));
            attributeValues.put(":room", AttributeValue.fromS(room));

            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :newStatus, room = :room, updated_at = :updatedAt")
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build();

            dynamoDbClient.updateItem(request);
            getLogger().log("✅ Successfully updated status to: " + status);

        } catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Update Error: " + e.getMessage());
            throw new RuntimeException("Failed to update status: " + e.getMessage(), e);
        }
    }

    /**
     * 상태와 방 정보 조회
     */
    public Map<String, String> getStatusAndRoom(String tutorEmail, String studentEmail) {
        try {
            getLogger().log("=== Repository: getStatusAndRoom ===");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .build());

            if (!response.hasItem()) {
                getLogger().log("=== Repository.getStatusAndRoom not found ===");
                return null;
            }

            Map<String, AttributeValue> item = response.item();
            Map<String, String> result = new HashMap<>();

            result.put("status", item.containsKey("status") ? item.get("status").s() : null);
            result.put("room", item.containsKey("room") ? item.get("room").s() : null);

            return result;

        } catch (DynamoDbException e) {
            getLogger().log("❌ getStatusAndRoom DynamoDB Error: " + e.getMessage());
            throw e;
        }
    }
}