package dashboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import dashboard.config.DynamoDbConfig;

import dashboard.controller.DashboardController;
import dashboard.controller.SQSController;
import dashboard.repository.DashboardRepository;
import dashboard.service.DashboardService;
import dashboard.utils.StudentStatusCollector;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;


public class DashboardSQSHandler implements RequestHandler<SQSEvent, Void> {

    private final SQSController controller;


    public DashboardSQSHandler() {
        String tutorStudentsTableName = System.getenv("TUTOR_STUDENTS_TABLE");
        String usersTable = System.getenv("USERS_TABLE");
        String sessionsTable = System.getenv("SESSIONS_TABLE");

        getLogger().log(
                "=== DashboardSQSHandler 초기화"
                        + " | 테이블: " + tutorStudentsTableName
                        + " | " + usersTable
                        + " | " + sessionsTable
                        + " ==="
        );

        StudentStatusCollector collector = new StudentStatusCollector(
                DynamoDbConfig.connectDynamoDb(),
                tutorStudentsTableName,
                usersTable,
                sessionsTable
        );

        DashboardRepository repository = new DashboardRepository(
                DynamoDbConfig.connectDynamoDb(),
                tutorStudentsTableName
        );

        DashboardService service = new DashboardService(repository, collector);

        this.controller = new SQSController(
                service,
                new Gson()
        );

    }
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log(
                "=== SQS 이벤트 수신"
                        + " | 메시지 수: " + event.getRecords().size()
                        + " | 실행 시간: " + java.time.LocalDateTime.now()
                        + " ==="
        );

        return controller.handleSQSMessages(event);
    }
}
