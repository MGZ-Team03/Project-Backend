package com.speaktracker.tutorRegister.helpers;

import com.google.gson.Gson;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 알림 전송 헬퍼
 */
public class WebSocketHelper {
    private final ApiGatewayManagementApiClient apiGatewayClient;
    private final DynamoDbClient dynamoDbClient;
    private final String connectionsTable;
    private final Gson gson;

    public WebSocketHelper() {
        String websocketEndpoint = System.getenv("WEBSOCKET_API_ENDPOINT");
        
        this.apiGatewayClient = ApiGatewayManagementApiClient.builder()
                .endpointOverride(URI.create(websocketEndpoint))
                .region(Region.AP_NORTHEAST_2)
                .build();
        
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        
        this.connectionsTable = System.getenv("CONNECTIONS_TABLE");
        this.gson = new Gson();
    }

    /**
     * 사용자의 활성 WebSocket 연결 조회
     */
    private List<String> getActiveConnections(String userEmail) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(connectionsTable)
                    .indexName("user_email-index")
                    .keyConditionExpression("user_email = :email")
                    .expressionAttributeValues(Map.of(
                            ":email", AttributeValue.builder().s(userEmail).build()
                    ))
                    .build());

            List<String> connectionIds = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                connectionIds.add(item.get("connection_id").s());
            }

            return connectionIds;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get active connections for user: " + userEmail, e);
        }
    }

    /**
     * WebSocket으로 알림 전송
     */
    public boolean sendNotification(String userEmail, String type, Map<String, Object> data) {
        try {
            List<String> connectionIds = getActiveConnections(userEmail);
            
            if (connectionIds.isEmpty()) {
                System.out.println("No active WebSocket connections for user: " + userEmail);
                return false;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            
            String messageJson = gson.toJson(message);
            byte[] messageBytes = messageJson.getBytes();

            boolean sentToAtLeastOne = false;
            
            for (String connectionId : connectionIds) {
                try {
                    apiGatewayClient.postToConnection(PostToConnectionRequest.builder()
                            .connectionId(connectionId)
                            .data(SdkBytes.fromByteArray(messageBytes))
                            .build());
                    
                    System.out.println("Sent notification to connection: " + connectionId);
                    sentToAtLeastOne = true;
                } catch (GoneException e) {
                    // 연결이 끊어진 경우 - 무시 (TTL이 자동으로 정리)
                    System.out.println("Connection gone: " + connectionId);
                } catch (Exception e) {
                    System.err.println("Failed to send to connection " + connectionId + ": " + e.getMessage());
                }
            }

            return sentToAtLeastOne;
        } catch (Exception e) {
            System.err.println("Failed to send WebSocket notification: " + e.getMessage());
            return false;
        }
    }

    /**
     * 새 튜터 요청 알림 (튜터에게)
     */
    public boolean sendNewTutorRequestNotification(String tutorEmail, String requestId, 
                                                    String studentEmail, String studentName, 
                                                    String message, Long createdAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("student_email", studentEmail);
        data.put("student_name", studentName);
        data.put("message", message);
        data.put("created_at", createdAt);

        return sendNotification(tutorEmail, "NEW_TUTOR_REQUEST", data);
    }

    /**
     * 요청 승인 알림 (학생에게)
     */
    public boolean sendRequestApprovedNotification(String studentEmail, String requestId, 
                                                    String tutorEmail, String tutorName, 
                                                    Long processedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("tutor_email", tutorEmail);
        data.put("tutor_name", tutorName);
        data.put("processed_at", processedAt);

        return sendNotification(studentEmail, "TUTOR_REQUEST_APPROVED", data);
    }

    /**
     * 요청 거부 알림 (학생에게)
     */
    public boolean sendRequestRejectedNotification(String studentEmail, String requestId, 
                                                    String tutorEmail, String tutorName, 
                                                    String rejectionReason, Long processedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("tutor_email", tutorEmail);
        data.put("tutor_name", tutorName);
        data.put("rejection_reason", rejectionReason);
        data.put("processed_at", processedAt);

        return sendNotification(studentEmail, "TUTOR_REQUEST_REJECTED", data);
    }
}
