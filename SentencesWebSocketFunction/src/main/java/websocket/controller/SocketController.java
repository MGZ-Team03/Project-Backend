package websocket.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import websocket.dto.TutorStudentDto;
import websocket.service.SocketService;

@RequiredArgsConstructor
public class SocketController implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final SocketService socketService;
    private final Gson gson;

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {

        String routeKey = event.getRequestContext().getRouteKey();
        context.getLogger().log("Route: " + routeKey);

        try {
            switch (routeKey) {
                case "$connect":
                    return handleConnect(event, context);

                case "$disconnect":
                    return handleDisconnect(event, context);

                case "$status":
                    return handleStatus(event, context);

                case "$default":
                default:
                    return createResponse(400, "Unsupported route: " + routeKey);
            }
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayV2WebSocketResponse handleConnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("Client connected: " + connectionId);

        return createResponse(200, "Connected");
    }

    private APIGatewayV2WebSocketResponse handleDisconnect(APIGatewayV2WebSocketEvent event, Context context) {
        String connectionId = event.getRequestContext().getConnectionId();
        context.getLogger().log("Client disconnected: " + connectionId);

        return createResponse(200, "Disconnected");
    }

    private APIGatewayV2WebSocketResponse handleStatus(APIGatewayV2WebSocketEvent event, Context context) {
        String body = event.getBody();

        if (body == null || body.isEmpty()) {
            return createResponse(400, "Request body is required");
        }

        try {
            TutorStudentDto tutorStudentDto = gson.fromJson(body, TutorStudentDto.class);

            // 유효성 검사
            if (tutorStudentDto.getTutorEmail() == null || tutorStudentDto.getStudentEmail() == null) {
                return createResponse(400, "tutorEmail and studentEmail are required");
            }

            socketService.save(tutorStudentDto);

            return createResponse(200, "Tutor-Student assignment saved successfully");

        } catch (Exception e) {
            context.getLogger().log("Failed to parse or save data: " + e.getMessage());
            return createResponse(400, "Invalid request format");
        }
    }

    private APIGatewayV2WebSocketResponse createResponse(int statusCode, String message) {
        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(statusCode);
        response.setBody(message);
        return response;
    }
}