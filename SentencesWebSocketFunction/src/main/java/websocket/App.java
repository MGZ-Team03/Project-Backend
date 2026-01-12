package websocket;

import java.net.URI;
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

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private Gson gson = new Gson();

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

    // 연결 시 자동으로 환영 메시지 전송
    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("✅ Connected: " + connectionId);

        // 환영 메시지 자동 전송
        Map<String, Object> welcomeMsg = new HashMap<>();
        welcomeMsg.put("type", "welcome");
        welcomeMsg.put("message", "환영합니다! WebSocket에 연결되었습니다.");
        welcomeMsg.put("connectionId", connectionId);
        welcomeMsg.put("time", getCurrentTime());

        sendMessage(connectionId, welcomeMsg, event, context);

        return createResponse(200, "Connected");
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
        context.getLogger().log("❌ Disconnected: " + event.getRequestContext().getConnectionId());
        return createResponse(200, "Disconnected");
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

        Map<String, Object> response = new HashMap<>();
        response.put("type", "response");
        response.put("message", "메시지를 받았습니다! cd 테스트중입니다!");
        response.put("receivedData", receivedData);
        response.put("time", getCurrentTime());
        response.put("connectionId", connectionId);

        sendMessage(connectionId, response, event, context);

        return createResponse(200, "Message processed");
    }

}
