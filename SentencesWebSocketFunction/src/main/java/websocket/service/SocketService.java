package websocket.service;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import websocket.repository.SocketRepository;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
import static websocket.controller.SocketController.createResponse;

/**
 * WebSocket 연결 관리 서비스 (피드백/알림 수신 전용)
 * - $connect: connection_id + user_email 저장
 * - $disconnect: connection_id 삭제
 */
public class SocketService {
    private final SocketRepository socketRepository;

    public SocketService(SocketRepository socketRepository) {
        this.socketRepository = socketRepository;
    }

    /**
     * $connect 시 connection_id와 user_email 저장
     */
    public void saveConnection(String connectionId, String userEmail) {
        getLogger().log("=== Service: Save Connection ===");
        getLogger().log("ConnectionId: " + connectionId + ", UserEmail: " + userEmail);
        socketRepository.saveConnectionWithUserEmail(connectionId, userEmail);
    }

    /**
     * $disconnect 시 connection_id 삭제
     */
    public APIGatewayV2WebSocketResponse handleDisconnect(String connectionId) {
        getLogger().log("=== Service: Handle Disconnect === ConnectionID: " + connectionId);

        boolean success = socketRepository.deleteConnection(connectionId);

        if (success) {
            getLogger().log("Disconnected successfully");
            return createResponse(200, "disconnected");
        } else {
            getLogger().log("Connection not found");
            return createResponse(200, "disconnected");
        }
    }
}

