package websocket.repository;

import org.joda.time.Instant;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

/**
 * WebSocket 연결 저장소 (피드백/알림 수신 전용)
 * - connection_id + user_email 저장/삭제
 */
public class SocketRepository {
    private final DynamoDbClient dynamoDbClient;
    private final String connectionTable;

    public SocketRepository(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.connectionTable = System.getenv("CONNECTIONS_TABLE");
    }

    /**
     * $connect 시 user_email과 함께 connection 저장
     */
    public void saveConnectionWithUserEmail(String connectionId, String userEmail) {
        getLogger().log("=== Repository: Save Connection ===");
        getLogger().log("ConnectionId: " + connectionId + ", UserEmail: " + userEmail);

        // 기존 연결 삭제 (user_email 기준)
        deleteOldConnectionsByUserEmail(userEmail);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("connection_id", AttributeValue.builder().s(connectionId).build());
        item.put("user_email", AttributeValue.builder().s(userEmail).build());
        item.put("connected_at", AttributeValue.builder().s(Instant.now().toString()).build());
        // TTL: 3일 후
        long ttl = (System.currentTimeMillis() / 1000) + 3L * 24 * 60 * 60;
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(connectionTable)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
        getLogger().log("✅ 연결 저장 완료: " + connectionId);
    }

    /**
     * $disconnect 시 connection_id 삭제
     */
    public boolean deleteConnection(String connectionId) {
        getLogger().log("=== Repository: Delete Connection ===");
        getLogger().log("ConnectionId: " + connectionId);

        try {
            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(connectionTable)
                    .key(Map.of("connection_id", AttributeValue.builder().s(connectionId).build()))
                    .build();

            dynamoDbClient.deleteItem(request);
            getLogger().log("✅ 연결 삭제 완료: " + connectionId);
            return true;
        } catch (Exception e) {
            getLogger().log("⚠️ 연결 삭제 실패: " + e.getMessage());
            return false;
        }
    }

    /**
     * 기존 연결 삭제 (user_email 기준)
     */
    private void deleteOldConnectionsByUserEmail(String userEmail) {
        try {
            Map<String, AttributeValue> expressionValues = Map.of(
                    ":email", AttributeValue.builder().s(userEmail).build()
            );

            QueryRequest request = QueryRequest.builder()
                    .tableName(connectionTable)
                    .indexName("user_email-index")
                    .keyConditionExpression("user_email = :email")
                    .expressionAttributeValues(expressionValues)
                    .projectionExpression("connection_id")
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            for (Map<String, AttributeValue> item : response.items()) {
                DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                        .tableName(connectionTable)
                        .key(Map.of("connection_id", item.get("connection_id")))
                        .build();

                dynamoDbClient.deleteItem(deleteRequest);
            }

            getLogger().log("✅ 기존 연결 삭제 완료 (user_email): " + userEmail);
        } catch (Exception e) {
            getLogger().log("⚠️ 기존 연결 삭제 실패: " + e.getMessage());
        }
    }
}
