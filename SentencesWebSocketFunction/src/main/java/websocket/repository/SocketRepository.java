package websocket.repository;

import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.amazonaws.services.lambda.runtime.Context;
import websocket.dto.StatusRequest;
import websocket.dto.TutorStudentDto;


import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;

    private final String tutorStudentsTableName;
    private final String connectionsTableName;

    public void saveTutorStudent(StatusRequest request) {
        getLogger().log("=== Repository 실행 ===");

        Map<String, AttributeValue> item = save(request);

        try {
            PutItemRequest build = PutItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .item(item)
                    // 조건: status가 기대하는 값이거나 아이템이 없을 때만 쓰기
                    .conditionExpression("attribute_not_exists(tutor_email) OR #status <> :newStatus")
                    .expressionAttributeNames(Map.of("#status", "status"))
                    .expressionAttributeValues(Map.of(":newStatus", AttributeValue.fromS(request.getStatus())))
                    .build();

            dynamoDbClient.putItem(build);
            getLogger().log("✅ Item saved successfully");

        } catch (ConditionalCheckFailedException e) {
            getLogger().log("⚠️ Condition failed - status already updated by another request");
            // 무시하거나 재시도
        }
    }

    private static HashMap<String, AttributeValue> save(StatusRequest request) {
        HashMap<String, AttributeValue> item = new HashMap<>();
        item.put("tutor_email", AttributeValue.fromS(request.getTutorEmail()));
        item.put("student_email", AttributeValue.fromS(request.getStudentEmail()));
        item.put("assigned_at",AttributeValue.fromS(DateTime.now().toString()));
        item.put("status", AttributeValue.fromS(request.getStatus()));
        item.put("room", AttributeValue.fromS(request.getRoom()));
        return item;
    }

    public boolean existsTutorStudent(String tutorEmail, String studentEmail) {
        try{
            getLogger().log("=== Repository: Check Exists ===");
            getLogger().log("Tutor: " + tutorEmail);
            getLogger().log("Student: " + studentEmail);

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
        }  catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Error: " + e.getMessage());
            // 에러 발생 시 false 반환 (존재하지 않는 것으로 처리)
            return false;
        }
    }

    public void updateStatus(String tutorEmail, String studentEmail, String status) {
        try {
            getLogger().log("=== Repository: Update Status ===");
            getLogger().log("Table: " + tutorStudentsTableName);
            getLogger().log("Tutor: " + tutorEmail);
            getLogger().log("Student: " + studentEmail);
            getLogger().log("New Status: " + status);

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            Map<String, AttributeValue> item = new HashMap<>();
            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":newStatus", AttributeValue.fromS(status));
            attributeValues.put(":updatedAt", AttributeValue.fromS(DateTime.now().toString()));


            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            UpdateItemRequest result = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :newStatus, updated_at = :updatedAt")
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build();

            dynamoDbClient.updateItem(result);
            getLogger().log("✅ Successfully updated status to: " + status);
        }catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Update Error: " + e.getMessage());
            throw new RuntimeException("Failed to update status: " + e.getMessage(), e);
        }
    }

    public String getStatus(String tutorEmail, String studentEmail) {
        try {
            getLogger().log("=== Repository: getStatus ===");

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", AttributeValue.fromS(tutorEmail));
            key.put("student_email", AttributeValue.fromS(studentEmail));

            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .build());

            return response.hasItem() && response.item().containsKey("status")
                    ? response.item().get("status").s()
                    : null;

        } catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Error: " + e.getMessage());
            throw e;
        }
    }

    /**
     * CONNECTIONS_TABLE에 connectionId 저장 (튜터 피드백용)
     */
    public void saveConnection(String connectionId, String userEmail) {
        try {
            getLogger().log("=== Repository: Save Connection ===");
            getLogger().log("ConnectionId: " + connectionId);
            getLogger().log("UserEmail: " + userEmail);
            getLogger().log("Table: " + connectionsTableName);

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("connection_id", AttributeValue.fromS(connectionId));
            item.put("user_email", AttributeValue.fromS(userEmail));
            item.put("connected_at", AttributeValue.fromS(DateTime.now().toString()));
            
            // TTL: 24시간 후 자동 삭제
            long ttl = System.currentTimeMillis() / 1000 + (24 * 60 * 60);
            item.put("ttl", AttributeValue.fromN(String.valueOf(ttl)));

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(connectionsTableName)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            getLogger().log("✅ Connection saved successfully");

        } catch (DynamoDbException e) {
            // 연결 저장 실패해도 WebSocket 연결은 유지 (연결 실패 방지)
            getLogger().log("❌ Failed to save connection: " + e.getMessage());
            getLogger().log("⚠️ Connection will proceed without persistence");
        }
    }

    /**
     * CONNECTIONS_TABLE에서 connectionId 삭제
     */
    public void deleteConnection(String connectionId) {
        try {
            getLogger().log("=== Repository: Delete Connection ===");
            getLogger().log("ConnectionId: " + connectionId);

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("connection_id", AttributeValue.fromS(connectionId));

            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(connectionsTableName)
                    .key(key)
                    .build();

            dynamoDbClient.deleteItem(request);
            getLogger().log("✅ Connection deleted successfully");

        } catch (DynamoDbException e) {
            getLogger().log("❌ Failed to delete connection: " + e.getMessage());
        }
    }

}
