package websocket.repository;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import websocket.dto.StatusRequest;


import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;

    private final String tutorStudentsTableName;
    String connectionTable = System.getenv("CONNECTIONS_TABLE");
    private final String connectionsTableName;

    public void saveTutorStudent(StatusRequest request, APIGatewayV2WebSocketEvent event) {
        getLogger().log("=== Repository 실행 ===");

        Map<String, AttributeValue> item = save(request, event);


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

    private static HashMap<String, AttributeValue> save(StatusRequest request, APIGatewayV2WebSocketEvent event) {
        HashMap<String, AttributeValue> item = new HashMap<>();
        String connectionId = event.getRequestContext().getConnectionId();

        getLogger().log("save connectionId : " + connectionId);

        getLogger().log("save repo connectionId: " + connectionId);
        item.put("tutor_email", AttributeValue.fromS(request.getTutorEmail()));
        item.put("student_email", AttributeValue.fromS(request.getStudentEmail()));
        item.put("assigned_at",AttributeValue.fromS(DateTime.now().toString()));
        item.put("status", AttributeValue.fromS(request.getStatus()));
        item.put("connectionId", AttributeValue.fromS(connectionId));
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

    public void updateStatus(String connectionId,String room,String tutorEmail ,String studentEmail, String status) {
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
            attributeValues.put(":room", AttributeValue.fromS(room));
            attributeValues.put(":connectionId", AttributeValue.fromS(connectionId));


            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            UpdateItemRequest result = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :newStatus,connectionId = :connectionId,room = :room,  updated_at = :updatedAt")
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
                return null;
            }

            Map<String, AttributeValue> item = response.item();
            Map<String, String> result = new HashMap<>();

            result.put("status", item.containsKey("status") ? item.get("status").s() : null);
            result.put("room", item.containsKey("room") ? item.get("room").s() : null);

            return result;

        } catch (DynamoDbException e) {
            getLogger().log("❌ DynamoDB Error: " + e.getMessage());
            throw e;
        }
    }


    public void saveConnection(APIGatewayV2WebSocketEvent event,String tutorEmail, String studentEmail) {
        Map<String, AttributeValue> item = new HashMap<>();
        getLogger().log("=== Repository: Save Connection ===");
        String connectionId = event.getRequestContext().getConnectionId();
        item.put("connection_id", AttributeValue.builder().s(connectionId).build());
        item.put("student_email", AttributeValue.builder().s(studentEmail).build());
        item.put("tutor_email", AttributeValue.builder().s(tutorEmail).build());
        item.put("connected_at", AttributeValue.builder().s(Instant.now().toString()).build());
        // TTL: 한 달 후
        long ttl = (System.currentTimeMillis() / 1000) + 30L * 24 * 60 * 60;
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(connectionTable)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }

    public Optional<Map<String, AttributeValue>> getConnection(String connectionId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("connection_id", AttributeValue.builder().s(connectionId).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(tutorStudentsTableName)
                .key(key)
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        if (response.hasItem() && !response.item().isEmpty()) {
            return Optional.of(response.item());
        }
        return Optional.empty();
    }
    // connection 존재 여부
    public boolean existsByConnectionId(String connectionId) {
        getLogger().log("=== Repository: Check Connection Exists ===");

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("connection_id", AttributeValue.builder().s(connectionId).build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName(connectionTable)
                .key(key)
                .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        return response.hasItem();
    }


    public boolean handleDisConnect(String connectionId) {  // connectionId가 아니라 studentEmail
        getLogger().log("=== Repository: Handle Disconnect by ConnectionId ===");

        try {
            // 1단계: connectionId로 찾기 (Scan 또는 GSI 필요)
            Map<String, AttributeValue> eav = new HashMap<>();
            eav.put(":cid", AttributeValue.builder().s(connectionId).build());

            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .filterExpression("connectionId = :cid")
                    .expressionAttributeValues(eav)
                    .build();

            ScanResponse scanResponse = dynamoDbClient.scan(scanRequest);

            if (scanResponse.items().isEmpty()) {
                getLogger().log("⚠️ Connection not found");
                return false;
            }

            Map<String, AttributeValue> item = scanResponse.items().get(0);

            // 2단계: 실제 PK로 업데이트
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("tutor_email", item.get("tutor_email"));
            key.put("student_email", item.get("student_email"));

            // 3단계: status를 inactive로 + connectionId 제거 + room: no room
            //
            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":status", AttributeValue.builder().s("inactive").build());
            attributeValues.put(":room", AttributeValue.builder().s("no room").build());

            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");
            attributeNames.put("#room", "room");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :status, #room = :room REMOVE connectionId")
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build();

            dynamoDbClient.updateItem(request);
            getLogger().log("✅ Status updated to inactive, room set to 'no room', and connectionId removed");
            return true;

        } catch (Exception e) {
            getLogger().log("❌ Failed to update: " + e.getMessage());
            return false;
        }
    }

    public void handleRemoveConAtt(String connectionId) {
        getLogger().log("=== Repository: Handle RemoveConAtt ===");

        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("connectionId", AttributeValue.builder().s(connectionId).build());

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("REMOVE connectionId")
                    .build();

            dynamoDbClient.updateItem(request);
            getLogger().log("✅ connection_id removed for connectionId: " + connectionId);

        } catch (Exception e) {
            getLogger().log("❌ Failed to remove connection_id: " + e.getMessage());
            throw new RuntimeException("Failed to handle disconnect", e);
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
            getLogger().log("❌ Failed to save connection: " + e.getMessage());
            throw new RuntimeException("Failed to save connection", e);
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
