package dashboard.handler;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import dashboard.dto.dashboard.DashboardUpdateDto;
import dashboard.utils.StudentStatusCollector;

import java.util.*;
import java.util.stream.Collectors;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;


public class DataGeneratorHandler implements RequestHandler <Map<String,Object>, String>{

    private final SqsClient sqsClient;
    private final StudentStatusCollector collector;
    private final Gson gson;
    DynamoDbClient dynamoDbClient = DynamoDbClient.create();

    private static final String QUEUE_URL = System.getenv("QUEUE_URL");


    public DataGeneratorHandler() {
        this.sqsClient = SqsClient.create();
        this.gson = new Gson();
        DynamoDbClient dynamoDbClient = DynamoDbClient.create();
        this.collector = new StudentStatusCollector(
                dynamoDbClient,
                System.getenv("TUTOR_STUDENTS_TABLE"),
                System.getenv("USERS_TABLE"),
                System.getenv("SESSIONS_TABLE")
        );
    }

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
          context.getLogger().log("  학생 상태 수집 시작 (EventBridge), DataGeneratorHandler");
        try {
            // ✅ 모든 튜터 - 튜터별로 개별 메시지 전송
            context.getLogger().log("모든 튜터 처리");
            List<String> tutorEmails = getAllTutorEmails();
            context.getLogger().log("튜터 수: " + tutorEmails.size());

            for (String email : tutorEmails) {
                getLogger().log("email: "+ email);
                sendDashboardUpdate(email, context);
            }

            return "Success: " + tutorEmails.size() + " tutors processed";

        } catch (Exception e) {
            context.getLogger().log("❌ 에러: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to collect student statuses", e);
        }
    }


    private void sendDashboardUpdate(String tutorEmail, Context context) {
        try {
            context.getLogger().log("처리 중: " + tutorEmail);

            // 해당 튜터의 학생 상태 수집
            DashboardUpdateDto dashboardUpdate = collector.collectAllStudents(tutorEmail);

            // JSON 변환
            String messageBody = gson.toJson(dashboardUpdate);

            getLogger().log("sqs.messageBody: " + messageBody);
            getLogger().log("sqs.queueUrl: " + QUEUE_URL);

            // SQS 전송
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(messageBody)
                    .build());

            context.getLogger().log("✅ " + tutorEmail + " 전송 완료 (" +
                    dashboardUpdate.getStudents().size() + "명)");

        } catch (Exception e) {
            context.getLogger().log("⚠️ " + tutorEmail + " 전송 실패: " + e.getMessage());
        }
    }
    // ✅ Users 테이블에서 모든 튜터 조회
    private List<String> getAllTutorEmails() {
        try {

            getLogger().log("조회테이블 : " + System.getenv("USERS_TABLE"));
            getLogger().log("user_table: "+System.getenv("USERS_TABLE"));

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(System.getenv("USERS_TABLE"))
                    .keyConditionExpression("#role = :role")  // PK 조건
                    .expressionAttributeNames(Map.of("#role", "role"))
                    .expressionAttributeValues(Map.of(":role", AttributeValue.builder().s("tutor").build()))
                    .projectionExpression("email")
                    .build();

            QueryResponse response = dynamoDbClient.query(queryRequest);

            getLogger().log("튜터 조회 결과: " + response.items().size() + "명");

            return response.items().stream()
                    .map(item -> item.get("email").s())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            getLogger().log("⚠️ 튜터 조회 실패: " + e.getMessage());
            e.printStackTrace();
            return List.of();
        }
    }
}
