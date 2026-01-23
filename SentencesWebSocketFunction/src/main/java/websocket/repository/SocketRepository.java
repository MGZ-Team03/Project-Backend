package websocket.repository;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import websocket.dto.StatusRequest;


import java.util.*;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;

    private final String tutorStudentsTableName;
    String connectionTable = System.getenv("CONNECTIONS_TABLE");

    public void saveTutorStudent(StatusRequest request, APIGatewayV2WebSocketEvent event) {
        getLogger().log("=== Repository.saveTutorStudent 실행 ===");

        Map<String, AttributeValue> item = save(request, event);

        try {
            PutItemRequest build = PutItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .item(item)
                    // 조건 없이 항상 덮어쓰기 (upsert)
                    .build();

            dynamoDbClient.putItem(build);
            getLogger().log("✅ Item saved/updated successfully");

        } catch (Exception e) {
            getLogger().log("⚠️ Save failed: " + e.getMessage());
            throw new RuntimeException("Failed to save tutor-student mapping", e);
        }
    }

    private static HashMap<String, AttributeValue> save(StatusRequest request, APIGatewayV2WebSocketEvent event) {
        HashMap<String, AttributeValue> item = new HashMap<>();
        String connectionId = event.getRequestContext().getConnectionId();

        String tutorEmail = request.getTutorEmail();

        if ("undefined".equalsIgnoreCase(tutorEmail)
                || "unknown@example.com".equalsIgnoreCase(tutorEmail)) {

            String errorMsg = "❌ CRITICAL: 유효하지 않은 tutorEmail = " + tutorEmail
                    + " (프론트엔드에서 올바른 tutorEmail 전달 필요)";

            getLogger().log(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }


        // .fromS() 대신 .s() 사용
        item.put("tutor_email", AttributeValue.builder().s(request.getTutorEmail()).build());
        item.put("student_email", AttributeValue.builder().s(request.getStudentEmail()).build());
        item.put("assigned_at", AttributeValue.builder().s(Instant.now().toString()).build());
        item.put("status", AttributeValue.builder().s(request.getStatus()).build());
        item.put("connectionId", AttributeValue.builder().s(connectionId).build());
        item.put("room", AttributeValue.builder().s(request.getRoom()).build());

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

            // 3. 업데이트
            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":newStatus", AttributeValue.fromS(status));
            attributeValues.put(":updatedAt", AttributeValue.fromS(DateTime.now().toString()));
            attributeValues.put(":room", AttributeValue.fromS(room));
            attributeValues.put(":connectionId", AttributeValue.fromS(connectionId));


            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :newStatus,connectionId = :connectionId,room = :room,  updated_at = :updatedAt")
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build();


            dynamoDbClient.updateItem(request);
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
                getLogger().log("===Repository.getStatusAndRoom not found");
                return null;
            }

            Map<String, AttributeValue> item = response.item();
            Map<String, String> result = new HashMap<>();

            result.put("status", item.containsKey("status") ? item.get("status").s() : null);
            result.put("room", item.containsKey("room") ? item.get("room").s() : null);

            return result;

        } catch (DynamoDbException e) {
            getLogger().log("❌ get.getStatusAndRoom DynamoDB Error: " + e.getMessage());
            throw e;
        }
    }

    public void saveConnection(APIGatewayV2WebSocketEvent event,String tutorEmail) {
        Map<String, AttributeValue> item = new HashMap<>();
        getLogger().log("=== Repository: Save Connection ===");
        getLogger().log("Tutor: " + tutorEmail);

        deleteOldConnections(tutorEmail);

        String connectionId = event.getRequestContext().getConnectionId();
        item.put("connection_id", AttributeValue.builder().s(connectionId).build());
        item.put("tutor_email", AttributeValue.builder().s(tutorEmail).build());
        item.put("connected_at", AttributeValue.builder().s(Instant.now().toString()).build());
        // TTL: 한 달 후
        long ttl = (System.currentTimeMillis() / 1000) + 3L * 24 * 60 * 60;
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(connectionTable)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
        getLogger().log("✅ 새 연결 저장 완료: " + connectionId);

    }

    private void deleteOldConnections(String tutorEmail) {
        getLogger().log("=== Repository: deleteOldConnections ===");
        try {
            Map<String, AttributeValue> expressionValues = Map.of(
                    ":email", AttributeValue.builder().s(tutorEmail).build()
            );

            QueryRequest request = QueryRequest.builder()
                    .tableName(connectionTable)
                    .indexName("tutor_email-index")
                    .keyConditionExpression("tutor_email = :email")
                    .expressionAttributeValues(expressionValues)
                    .projectionExpression("connection_id")
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            // 모든 기존 연결 삭제
            for (Map<String, AttributeValue> item : response.items()) {
                DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                        .tableName(connectionTable)
                        .key(Map.of("connection_id", item.get("connection_id")))
                        .build();

                dynamoDbClient.deleteItem(deleteRequest);
            }

            getLogger().log("✅ 기존 연결 삭제 완료");

        } catch (Exception e){
            getLogger().log("⚠️ 기존 연결 삭제 실패: " + e.getMessage());

        }
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

    /**
     * 튜터 연결만 조회
     * student_email = "TUTOR_SELF"인 레코드의 connectionId 반환
     *
     * 튜터가 대시보드 연결 시 다음과 같이 저장:
     * {
     *   "id": "uuid",
     *   "tutor_email": "teacher@test.com",
     *   "student_email": "TUTOR_SELF",
     *   "connectionId": "conn-tutor-123",
     *   "room": "dashboard",
     *   "status": "active"
     * }
     */
    public String getTutorConnectionIds(String tutorEmail) {

       getLogger().log("  튜터 연결 조회 "+"테이블: " + connectionTable);

        try {
            Map<String, AttributeValue> expressionValues = Map.of(
                    ":email", AttributeValue.builder().s(tutorEmail).build()
            );


            QueryRequest request = QueryRequest.builder()
                    .tableName(connectionTable)
                    .indexName("tutor_email-index")
                    .keyConditionExpression("tutor_email = :email")
                    .expressionAttributeValues(expressionValues)
                    .projectionExpression("connection_id")
                    .limit(1)  // ✅ 이제 안전! (항상 1개만 있음)
                    .build();


            QueryResponse response = dynamoDbClient.query(request);

            String connectionId = response.items().stream()
                    .findFirst()
                    .map(item -> item.get("connection_id").s())
                    .orElse(null);

            if (connectionId != null) {
                getLogger().log("✅ ConnectionId 발견: " + connectionId);
            } else {
                getLogger().log("⚠️ 연결 없음");
            }

            return connectionId;

        } catch (Exception e) {
            System.err.println("========================================");
            System.err.println("  ⚠️ 튜터 연결 조회 실패 (폴백: 모든 연결 반환)");
            System.err.println("========================================");
            System.err.println("에러: " + e.getMessage());
            e.printStackTrace();

            return null;
        }
    }

    public boolean existsConnectionId(String connectionId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("connection_id", AttributeValue.builder().s(connectionId).build());

            GetItemRequest request = GetItemRequest.builder()
                    .tableName(connectionTable)
                    .key(key)
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);

            return response.hasItem();

        } catch (Exception e) {
            getLogger().log("❌ Error: " + e.getMessage());
            return false;
        }
    }

}
