package dashboard;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import dashboard.controller.DashboardController;
import dashboard.repository.DashboardRepository;
import dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import dashboard.config.DynamoDbConfig;
import dashboard.utils.StudentStatusCollector;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */

@RequiredArgsConstructor
public class DashboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DashboardController controller;
    private final Gson gson;

    public DashboardHandler() {
        // 환경 변수에서 테이블 이름 가져오기

        String tutorStudentsTable = System.getenv("TUTOR_STUDENTS_TABLE");
        String usersTable = System.getenv("USERS_TABLE");
        String sessionsTable = System.getenv("SESSIONS_TABLE");

        DynamoDbClient dynamoDbClient = DynamoDbClient.create();

        StudentStatusCollector collector = new StudentStatusCollector(
                dynamoDbClient,
                tutorStudentsTable,
                usersTable,
                sessionsTable
        );

        DashboardRepository repository = new DashboardRepository(
                DynamoDbConfig.connectDynamoDb(),
                tutorStudentsTable
        );

        DashboardService service = new DashboardService(repository, collector);
        this.controller = new DashboardController(service, new Gson());
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("HTTP Method: " + request.getHttpMethod());
        context.getLogger().log("Path: " + request.getPath());

        try {
            return controller.handleRequest(request, context);
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");

        Map<String, Object> body = new HashMap<>();
        body.put("error", message);
        body.put("statusCode", statusCode);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(body));
    }
}
