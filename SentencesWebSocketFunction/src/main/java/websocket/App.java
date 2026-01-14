package websocket;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/**
 * WebSocket 핸들러
 * 
 * 기능:
 * 1. WebSocket 연결/해제 관리
 * 2. 메시지 타입별 라우팅
 * 3. DynamoDB에 연결 정보 저장
 */
public class App implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private static final String CONNECTIONS_TABLE = System.getenv("CONNECTIONS_TABLE");
    
    private final Gson gson = new Gson();
    private final DynamoDbClient dynamoDbClient;

    public App() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(
            APIGatewayV2WebSocketEvent websocket,
            Context context) {

        String routeKey = websocket.getRequestContext().getRouteKey();
        String connectionId = websocket.getRequestContext().getConnectionId();

        context.getLogger().log("Route: " + routeKey + " | Connection: " + connectionId);

        switch (routeKey) {
            case "$connect":
                return handleConnect(websocket, context);
            case "$disconnect":
                return handleDisconnect(websocket, context);

            default:
                return handleMessage(websocket, context);
        }
    }

    // 연결 시 DynamoDB에 저장
    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        
        // 쿼리 파라미터에서 user_email 추출
        Map<String, String> queryParams = event.getQueryStringParameters();
        String userEmail = null;
        String tutorEmail = null;
        
        if (queryParams != null) {
            userEmail = queryParams.get("user_email");
            tutorEmail = queryParams.get("tutor_email");
        }
        
        context.getLogger().log("✅ Connecting: " + connectionId + " | User: " + userEmail);

        // DynamoDB에 연결 정보 저장
        if (userEmail != null && !userEmail.isEmpty()) {
            saveConnection(connectionId, userEmail, tutorEmail, context);
        }

        // 환영 메시지 전송
        Map<String, Object> welcomeMsg = new HashMap<>();
        welcomeMsg.put("type", "welcome");
        welcomeMsg.put("message", "WebSocket 연결되었습니다.");
        welcomeMsg.put("connectionId", connectionId);
        welcomeMsg.put("userEmail", userEmail);
        welcomeMsg.put("time", getCurrentTime());

        sendMessage(connectionId, welcomeMsg, event, context);

        return createResponse(200, "Connected");
    }

    /**
     * DynamoDB에 연결 정보 저장
     */
    private void saveConnection(String connectionId, String userEmail, String tutorEmail, Context context) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("connection_id", AttributeValue.builder().s(connectionId).build());
            item.put("user_email", AttributeValue.builder().s(userEmail).build());
            
            if (tutorEmail != null && !tutorEmail.isEmpty()) {
                item.put("tutor_email", AttributeValue.builder().s(tutorEmail).build());
            }
            
            // TTL: 24시간 후 자동 삭제
            long ttl = Instant.now().plusSeconds(24 * 60 * 60).getEpochSecond();
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());
            
            item.put("connected_at", AttributeValue.builder().s(Instant.now().toString()).build());

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(CONNECTIONS_TABLE)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            context.getLogger().log("✅ Connection saved: " + connectionId + " -> " + userEmail);

        } catch (Exception e) {
            context.getLogger().log("❌ Failed to save connection: " + e.getMessage());
        }
    }

    // 메시지 전송
    private void sendMessage(String connectionId, Map<String, Object> data,
                             APIGatewayV2WebSocketEvent event, Context context) {
        try {
            String domain = event.getRequestContext().getDomainName();
            String stage = event.getRequestContext().getStage();
            String endpoint = String.format("https://%s/%s", domain, stage);

            ApiGatewayManagementApiClient client = ApiGatewayManagementApiClient.builder()
                    .endpointOverride(URI.create(endpoint))
                    .build();

            String message = gson.toJson(data);

            PostToConnectionRequest request = PostToConnectionRequest.builder()
                    .connectionId(connectionId)
                    .data(SdkBytes.fromUtf8String(message))
                    .build();

            client.postToConnection(request);
            context.getLogger().log("✅ Sent: " + message);

        } catch (Exception e) {
            context.getLogger().log("❌ Error: " + e.getMessage());
        }
    }

    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
    private APIGatewayV2WebSocketResponse createResponse(int statusCode, String body) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        response.setBody(body);
        return response;

    }

    private APIGatewayV2WebSocketResponse handleDisconnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("❌ Disconnecting: " + connectionId);
        
        // DynamoDB에서 연결 정보 삭제
        deleteConnection(connectionId, context);
        
        return createResponse(200, "Disconnected");
    }

    /**
     * DynamoDB에서 연결 정보 삭제
     */
    private void deleteConnection(String connectionId, Context context) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("connection_id", AttributeValue.builder().s(connectionId).build());

            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(CONNECTIONS_TABLE)
                    .key(key)
                    .build();

            dynamoDbClient.deleteItem(request);
            context.getLogger().log("✅ Connection deleted: " + connectionId);

        } catch (Exception e) {
            context.getLogger().log("❌ Failed to delete connection: " + e.getMessage());
        }
    }

    private APIGatewayV2WebSocketResponse handleMessage(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        String body = event.getBody();

        context.getLogger().log("📩 Received: " + body);

        Map<String, Object> receivedData;
        try {
            receivedData = gson.fromJson(body, Map.class);
        } catch (Exception e) {
            receivedData = new HashMap<>();
            receivedData.put("raw", body);
        }

        // 메시지 타입별 처리
        String messageType = (String) receivedData.getOrDefault("type", "unknown");
        
        switch (messageType) {
            case "connection":
                return handleConnectionMessage(receivedData, connectionId, event, context);
            case "feedback":
                return handleFeedbackMessage(receivedData, connectionId, event, context);
            case "status":
                return handleStatusMessage(receivedData, connectionId, event, context);
            case "dashboard":
                return handleDashboardMessage(receivedData, connectionId, event, context);
            default:
                return handleDefaultMessage(receivedData, connectionId, event, context);
        }
    }

    /**
     * connection 타입 메시지 처리
     */
    private APIGatewayV2WebSocketResponse handleConnectionMessage(
            Map<String, Object> data, String connectionId, 
            APIGatewayV2WebSocketEvent event, Context context) {
        
        String userEmail = (String) data.get("user_email");
        String tutorEmail = (String) data.get("tutor_email");
        
        if (userEmail != null && !userEmail.isEmpty()) {
            saveConnection(connectionId, userEmail, tutorEmail, context);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", "connection_ack");
        response.put("message", "연결 정보가 저장되었습니다.");
        response.put("userEmail", userEmail);
        
        sendMessage(connectionId, response, event, context);
        return createResponse(200, "Connection message processed");
    }

    /**
     * feedback 타입 메시지 처리 (튜터가 직접 WebSocket으로 전송하는 경우)
     */
    private APIGatewayV2WebSocketResponse handleFeedbackMessage(
            Map<String, Object> data, String connectionId,
            APIGatewayV2WebSocketEvent event, Context context) {
        
        // 피드백은 일반적으로 REST API를 통해 처리되므로
        // 여기서는 간단한 에코만 수행
        Map<String, Object> response = new HashMap<>();
        response.put("type", "feedback_received");
        response.put("message", "피드백이 수신되었습니다.");
        response.put("data", data);
        
        sendMessage(connectionId, response, event, context);
        return createResponse(200, "Feedback message processed");
    }

    /**
     * status 타입 메시지 처리
     */
    private APIGatewayV2WebSocketResponse handleStatusMessage(
            Map<String, Object> data, String connectionId,
            APIGatewayV2WebSocketEvent event, Context context) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "status_ack");
        response.put("message", "상태가 업데이트되었습니다.");
        response.put("receivedStatus", data.get("status"));
        
        sendMessage(connectionId, response, event, context);
        return createResponse(200, "Status message processed");
    }

    /**
     * dashboard 타입 메시지 처리
     */
    private APIGatewayV2WebSocketResponse handleDashboardMessage(
            Map<String, Object> data, String connectionId,
            APIGatewayV2WebSocketEvent event, Context context) {
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "dashboard_ack");
        response.put("message", "대시보드 메시지가 처리되었습니다.");
        
        sendMessage(connectionId, response, event, context);
        return createResponse(200, "Dashboard message processed");
    }

    /**
     * 기본 메시지 처리
     */
    private APIGatewayV2WebSocketResponse handleDefaultMessage(
            Map<String, Object> receivedData, String connectionId,
            APIGatewayV2WebSocketEvent event, Context context) {

        Map<String, Object> response = new HashMap<>();
        response.put("type", "response");
        response.put("message", "메시지를 받았습니다!");
        response.put("receivedData", receivedData);
        response.put("time", getCurrentTime());
        response.put("connectionId", connectionId);

        sendMessage(connectionId, response, event, context);

        return createResponse(200, "Message processed");
    }

}
