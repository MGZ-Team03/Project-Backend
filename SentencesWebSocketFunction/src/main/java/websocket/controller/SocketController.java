package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import websocket.dto.StatusRequest;
import websocket.dto.WebSocketRequest;
import websocket.repository.SocketRepository;
import websocket.service.SocketService;

import java.lang.reflect.Type;

@RequiredArgsConstructor
public class SocketController implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final SocketService socketService;
    private final Gson gson;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        String routeKey = event.getRequestContext().getRouteKey();
        context.getLogger().log("Route!!: " + routeKey);


        try {
            switch (routeKey) {
                case "$connect":
                    return handleConnect(event,context);

                case "$disconnect":
                    context.getLogger().log("Disconnect!!");
                    String connectionId = event.getRequestContext().getConnectionId();
                    return socketService.handleDisconnect(connectionId);

                case "status":
                    Type type = new TypeToken<WebSocketRequest<StatusRequest>>(){}.getType();
                    WebSocketRequest<StatusRequest> request =
                            gson.fromJson(event.getBody(), type);

                    return socketService.handleStatus(event, request);

                case "$default":
                default:
                    return createResponse(400, "Unsupported route!: " + routeKey);
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("Client connected: " + connectionId);

        return createResponse(200, "Connected!!!");
    }


    public static APIGatewayV2WebSocketResponse createResponse(int statusCode, String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        response.setBody(message);
        return response;
    }
}