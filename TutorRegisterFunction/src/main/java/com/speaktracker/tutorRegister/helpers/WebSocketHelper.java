package com.speaktracker.tutorRegister.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 사용자의 활성 WebSocket 연결 조회 (최신 연결만)
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

            if (response.items().isEmpty()) {
                return new ArrayList<>();
            }

            // connected_at 기준으로 최신 연결만 선택
            String latestConnectionId = response.items().stream()
                .filter(item -> item.containsKey("connected_at"))
                .max((a, b) -> {
                    String timeA = a.get("connected_at").s();
                    String timeB = b.get("connected_at").s();
                    return timeA.compareTo(timeB);
                })
                .map(item -> item.get("connection_id").s())
                .orElse(response.items().get(0).get("connection_id").s());
            
            return List.of(latestConnectionId);

        } catch (Exception e) {
            System.err.println("❌ Failed to get active connections for user: " + userEmail);
            e.printStackTrace();
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
                return false;
            }

            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.put("data", data);
            
            String messageJson = objectMapper.writeValueAsString(message);
            byte[] messageBytes = messageJson.getBytes();

            boolean sentToAtLeastOne = false;
            
            for (String connectionId : connectionIds) {
                try {
                    apiGatewayClient.postToConnection(PostToConnectionRequest.builder()
                            .connectionId(connectionId)
                            .data(SdkBytes.fromByteArray(messageBytes))
                            .build());
                    
                    sentToAtLeastOne = true;
                } catch (GoneException e) {
                    // 연결이 끊어진 경우 - 무시 (TTL이 자동으로 정리)
                } catch (Exception e) {
                    System.err.println("❌ Failed to send to connection " + connectionId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return sentToAtLeastOne;
        } catch (Exception e) {
            System.err.println("❌ Failed to send WebSocket notification: " + e.getMessage());
            e.printStackTrace();
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

    /**
     * 학생 추가 알림 (튜터에게) - 승인 후 대시보드 갱신용
     */
    public boolean sendStudentAddedNotification(String tutorEmail, String studentEmail, 
                                                 String studentName, String assignedAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("student_email", studentEmail);
        data.put("student_name", studentName);
        data.put("assigned_at", assignedAt);
        data.put("status", "active");

        return sendNotification(tutorEmail, "STUDENT_ADDED", data);
    }
}
