//package dashboard.handler;
//
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.SQSEvent;
//import com.google.gson.Gson;
//import dashboard.config.DynamoDbConfig;
//
//import dashboard.controller.DashboardController;
//import dashboard.repository.DashboardRepository;
//
//import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
//
//
//public class DashboardSQSHandler implements RequestHandler<SQSEvent, Void> {
//
//    private final DashboardController controller;
//
//
//    public DashboardSQSHandler() {
//        String tutorStudentsTableName = System.getenv("TUTOR_STUDENTS_TABLE");
//        String wsEndpoint = System.getenv("WS_ENDPOINT");
//
//        getLogger().log(
//                "=== DashboardSQSHandler 초기화"
//                        + " | 테이블: " + tutorStudentsTableName
//                        + " | WebSocket: " + wsEndpoint
//                        + " ==="
//        );
//        SocketRepository socketRepository = new SocketRepository(
//                DynamoDbConfig.connectDynamoDb(),
//                tutorStudentsTableName
//        );
//
//        this.controller = new DashboardController(
//                socketRepository,
//                wsEndpoint,
//                new Gson()
//        );
//
//    }
//    @Override
//    public Void handleRequest(SQSEvent event, Context context) {
//        context.getLogger().log(
//                "=== SQS 이벤트 수신"
//                        + " | 메시지 수: " + event.getRecords().size()
//                        + " | 실행 시간: " + java.time.LocalDateTime.now()
//                        + " ==="
//        );
//
//        return controller.handleSQSMessages(event, context);
//    }
//}
