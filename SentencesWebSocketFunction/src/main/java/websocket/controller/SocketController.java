package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import lombok.RequiredArgsConstructor;
import websocket.service.SocketService;

import java.util.Map;

@RequiredArgsConstructor
public class SocketController implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final SocketService socketService;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        String routeKey = event.getRequestContext().getRouteKey();
        context.getLogger().log("Route: " + routeKey);

        try {
            switch (routeKey) {
                case "$connect":
                    return handleConnect(event, context);

                case "$disconnect":
                    context.getLogger().log("Disconnect");
                    String connectionId = event.getRequestContext().getConnectionId();
                    return socketService.handleDisconnect(connectionId);

                case "$default":
                default:
                    return createResponse(200, "OK");
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("Client connected: " + connectionId);

        // 쿼리 파라미터에서 user_email 추출
        Map<String, String> queryParams = event.getQueryStringParameters();
        String userEmail = queryParams != null ? queryParams.get("user_email") : null;
        context.getLogger().log("User email from query: " + userEmail);

        if (userEmail != null && !userEmail.isEmpty()) {
            socketService.saveConnection(connectionId, userEmail);
        }

        return createResponse(200, "Connected");
    }

    public static APIGatewayV2WebSocketResponse createResponse(int statusCode, String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        response.setBody(message);
        return response;
    }
}