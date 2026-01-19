package websocket.repository;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import lombok.RequiredArgsConstructor;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import websocket.dto.StatusRequest;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;

    private final String tutorStudentsTableName;
    String connectionTable = System.getenv("CONNECTIONS_TABLE");

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

            // 3단계: status를 inactive로 + connectionId 제거
            Map<String, AttributeValue> attributeValues = new HashMap<>();
            attributeValues.put(":status", AttributeValue.builder().s("inactive").build());

            Map<String, String> attributeNames = new HashMap<>();
            attributeNames.put("#status", "status");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .key(key)
                    .updateExpression("SET #status = :status REMOVE connectionId")
                    .expressionAttributeNames(attributeNames)
                    .expressionAttributeValues(attributeValues)
                    .build();

            dynamoDbClient.updateItem(request);
            getLogger().log("✅ Status updated to inactive and connectionId removed");
            return true;

        } catch (Exception e) {
            getLogger().log("❌ Failed to update: " + e.getMessage());
            return false;
        }
    }

    public List<String> getAllActiveConnections() {
        getLogger().log("=== Repository: GetAllActiveConnections ===");
        getLogger().log("=== Websokcete 연결 조회 ===");
        getLogger().log("=== 테이블 : " + tutorStudentsTableName);

        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .projectionExpression("connectionId")
                    .build();
            ScanResponse response = dynamoDbClient.scan(scanRequest);
            getLogger().log("=== socketRepository.getAllActiveConnectionId.result: " + response.toString());
            // 로그아웃이면 null
            List<String> connectionIds = response.items().stream()
                    .map(item -> item.get("connectionId"))
                    .filter(attr -> attr != null && attr.s() != null && !attr.s().isEmpty())
                    .map(AttributeValue::s)
                    .collect(Collectors.toList());

            getLogger().log("조회 완료: " + connectionIds.size() + "개 연결");
            getLogger().log("------------------------------------------");

            return connectionIds;
        } catch (Exception e) {
            getLogger().log("=== SocketRepository.GetAllActiveConnections: 연결 실패 ===");
            getLogger().log("에러: " + e.getMessage());
            return List.of();
        }
    }


        /**
         * 튜터 연결만 조회
         * student_email = "TUTOR_SELF"인 레코드의 connectionId 반환
         */
        public List<String> getTutorConnectionIds() {
            System.out.println("========================================");
            System.out.println("  튜터 연결 조회");
            System.out.println("========================================");
            System.out.println("테이블: " + tutorStudentsTableName);

            try {
                Map<String, AttributeValue> expressionValues = new HashMap<>();
                expressionValues.put(":self", AttributeValue.builder().s("TUTOR_SELF").build());

                ScanRequest scanRequest = ScanRequest.builder()
                        .tableName(tutorStudentsTableName)
                        .filterExpression("student_email = :self")
                        .expressionAttributeValues(expressionValues)
                        .projectionExpression("connectionId")
                        .build();

                ScanResponse response = dynamoDbClient.scan(scanRequest);

                List<String> connectionIds = response.items().stream()
                        .filter(item -> item.containsKey("connectionId"))
                        .map(item -> item.get("connectionId"))
                        .filter(attr -> attr != null && attr.s() != null && !attr.s().isEmpty())
                        .map(AttributeValue::s)
                        .collect(Collectors.toList());

                System.out.println("조회 완료: " + connectionIds.size() + "개 튜터 연결");
                System.out.println("========================================");

                return connectionIds;

            } catch (Exception e) {
                System.err.println("========================================");
                System.err.println("  ⚠️ 튜터 연결 조회 실패 (폴백: 모든 연결 반환)");
                System.err.println("========================================");
                System.err.println("에러: " + e.getMessage());
                e.printStackTrace();

                // 실패 시 모든 연결 반환
                return getAllActiveConnections();
            }
        }

    public List<String> getStudentConnectionIdsByTutor(String tutorEmail) {
        getLogger().log("=== 튜터별 학생 연결 조회: " + tutorEmail + " ===");

        try {
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":tutor", AttributeValue.builder().s(tutorEmail).build());
            expressionValues.put(":self", AttributeValue.builder().s("TUTOR_SELF").build());

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tutorStudentsTableName)
                    .keyConditionExpression("tutor_email = :tutor")
                    .filterExpression("student_email <> :self")
                    .expressionAttributeValues(expressionValues)
                    .projectionExpression("connectionId")
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            List<String> connectionIds = response.items().stream()
                    .filter(item -> item.containsKey("connectionId"))
                    .map(item -> item.get("connectionId"))
                    .filter(attr -> attr != null && attr.s() != null && !attr.s().isEmpty())
                    .map(AttributeValue::s)
                    .collect(Collectors.toList());

            getLogger().log("조회 완료: " + connectionIds.size() + "개 학생 연결");
            return connectionIds;

        } catch (Exception e) {
            System.err.println("에러: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }


}
