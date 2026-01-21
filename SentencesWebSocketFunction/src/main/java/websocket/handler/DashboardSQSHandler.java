package websocket.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import websocket.config.DynamoDbConfig;
import websocket.controller.DashBoardController;
import websocket.repository.SocketRepository;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;


public class DashboardSQSHandler implements RequestHandler<SQSEvent, Void> {

    private final DashBoardController controller;


    public DashboardSQSHandler() {
        String tutorStudentsTableName = System.getenv("TUTOR_STUDENTS_TABLE");
        String wsEndpoint = System.getenv("WS_ENDPOINT");

        getLogger().log("========================================");
        getLogger().log("  DashboardSQSHandler 초기화");
        getLogger().log("========================================");
        getLogger().log("테이블: " + tutorStudentsTableName);
        getLogger().log("WebSocket: " + wsEndpoint);

        SocketRepository socketRepository = new SocketRepository(
                DynamoDbConfig.connectDynamoDb(),
                tutorStudentsTableName
        );

        this.controller = new DashBoardController(
                socketRepository,
                wsEndpoint,
                new Gson()
        );

    }
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("========================================");
        context.getLogger().log("  SQS 이벤트 수신");
        context.getLogger().log("========================================");
        context.getLogger().log("메시지 수: " + event.getRecords().size());
        context.getLogger().log("실행 시간: " + java.time.LocalDateTime.now());

        return controller.handleSQSMessages(event, context);
    }
}
