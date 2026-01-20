package websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import com.google.gson.Gson;
import websocket.config.DynamoDbConfig;
import websocket.controller.SocketController;
import websocket.repository.SocketRepository;
import websocket.service.SocketService;

/**
 * Handler for requests to Lambda function.
 */

public class SocketHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    private final SocketController controller;

    public SocketHandler() {
        // 환경 변수에서 테이블 이름 가져오기
        String tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");
        String connectionsTable = System.getenv("CONNECTIONS_TABLE");

        // 의존성 수동 주입
        SocketRepository repository = new SocketRepository(
                DynamoDbConfig.connectDynamoDb(),
                tutorStudentsTable,
                connectionsTable
        );
        SocketService service = new SocketService(repository);
        this.controller = new SocketController(service, new Gson());
    }

    @Override
    public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent apiGatewayV2WebSocketEvent, Context context) {
        return controller.handleRequest(apiGatewayV2WebSocketEvent, context);
    }
}
