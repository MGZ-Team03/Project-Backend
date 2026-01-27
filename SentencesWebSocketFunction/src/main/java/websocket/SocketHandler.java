package websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import websocket.config.DynamoDbConfig;
import websocket.controller.SocketController;
import websocket.repository.SocketRepository;
import websocket.service.SocketService;

/**
 * WebSocket Lambda Handler (피드백/알림 수신 전용)
 * - $connect: connection_id + user_email 저장
 * - $disconnect: connection_id 삭제
 */
public class SocketHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final SocketController controller;

    public SocketHandler() {
        // 의존성 주입
        SocketRepository repository = new SocketRepository(DynamoDbConfig.connectDynamoDb());
        SocketService service = new SocketService(repository);
        this.controller = new SocketController(service);
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent event, Context context) {
        return controller.handleRequest(event, context);
    }
}
