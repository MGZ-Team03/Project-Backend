package com.speaktracker.notificationpersistence;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.*;

/**
 * 알림 조회 API Lambda 함수
 * GET /api/notifications
 */
public class NotificationQueryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient;
    private final String notificationsTable;
    private final Gson gson;

    public NotificationQueryHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        this.notificationsTable = System.getenv("NOTIFICATIONS_TABLE");
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Cognito에서 사용자 이메일 추출
            String userEmail = getUserEmailFromCognito(request);
            
            // Query Parameters에서 limit 가져오기
            int limit = 50; // 기본값
            if (request.getQueryStringParameters() != null && 
                request.getQueryStringParameters().containsKey("limit")) {
                try {
                    limit = Integer.parseInt(request.getQueryStringParameters().get("limit"));
                } catch (NumberFormatException e) {
                    limit = 50;
                }
            }

            // DynamoDB에서 알림 조회
            List<Map<String, Object>> notifications = getNotifications(userEmail, limit);

            // 응답 생성
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("notifications", notifications);
            responseBody.put("count", notifications.size());

            return createResponse(200, responseBody);

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createErrorResponse(500, "INTERNAL_ERROR", e.getMessage());
        }
    }

    /**
     * DynamoDB에서 사용자의 알림 조회
     */
    private List<Map<String, Object>> getNotifications(String userEmail, int limit) {
        QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                .tableName(notificationsTable)
                .keyConditionExpression("user_email = :email")
                .expressionAttributeValues(Map.of(
                        ":email", AttributeValue.builder().s(userEmail).build()
                ))
                .scanIndexForward(false) // 최신 순으로 정렬
                .limit(limit)
                .build());

        List<Map<String, Object>> notifications = new ArrayList<>();
        
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, Object> notification = new HashMap<>();
            
            notification.put("notification_id", item.get("notification_id").s());
            notification.put("type", item.get("type").s());
            notification.put("title", item.get("title").s());
            notification.put("message", item.get("message").s());
            notification.put("read", item.get("is_read").bool());
            notification.put("created_at", Long.parseLong(item.get("created_at").n()));

            // data 필드 변환
            if (item.containsKey("data")) {
                Map<String, AttributeValue> dataAttr = item.get("data").m();
                Map<String, Object> data = new HashMap<>();
                
                for (Map.Entry<String, AttributeValue> entry : dataAttr.entrySet()) {
                    if (entry.getValue().s() != null) {
                        data.put(entry.getKey(), entry.getValue().s());
                    } else if (entry.getValue().n() != null) {
                        try {
                            data.put(entry.getKey(), Long.parseLong(entry.getValue().n()));
                        } catch (NumberFormatException e) {
                            data.put(entry.getKey(), Double.parseDouble(entry.getValue().n()));
                        }
                    }
                }
                
                notification.put("data", data);
            }

            // sent_via 필드 변환
            if (item.containsKey("sent_via")) {
                notification.put("sent_via", item.get("sent_via").ss());
            }

            notifications.add(notification);
        }

        return notifications;
    }

    /**
     * Cognito Authorizer에서 사용자 이메일 추출
     */
    private String getUserEmailFromCognito(APIGatewayProxyRequestEvent request) {
        Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
        
        if (authorizer != null && authorizer.containsKey("claims")) {
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            return claims.get("email");
        }
        
        throw new RuntimeException("User email not found in Cognito claims");
    }

    /**
     * 성공 응답 생성
     */
    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(body));
    }

    /**
     * 에러 응답 생성
     */
    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String errorCode, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", errorCode);
        error.put("message", message);

        Map<String, Object> body = new HashMap<>();
        body.put("error", error);

        return createResponse(statusCode, body);
    }
}
