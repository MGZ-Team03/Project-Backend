package websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import websocket.dto.dashboard.DashboardDataResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class DataGeneratorHandler implements RequestHandler <Object, String>{

    private final SqsClient sqsClient;
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    private static final String QUEUE_URL = System.getenv("QUEUE_URL");
    private static final String SESSIONS_TABLE = System.getenv("SESSIONS_TABLE");
    private static final String STATISTICS_TABLE = System.getenv("STATISTICS_TABLE");
    private static final String TUTOR_STUDENTS_TABLE = System.getenv("TUTOR_STUDENTS_TABLE");

    public DataGeneratorHandler() {
        this.sqsClient = SqsClient.create();
        this.dynamoDbClient = DynamoDbClient.create();
        this.gson = new Gson();
    }


    @Override
    public String handleRequest(Object o, Context context) {

        context.getLogger().log("========================================");
        context.getLogger().log("  대시보드 데이터 수집 및 전송 시작");
        context.getLogger().log("========================================");
        context.getLogger().log("실행 시간: " + java.time.LocalDateTime.now());

        try {

            context.getLogger().log("\n[1단계] 활성 세션 조회 중...");
            int activeSessions = getActiveSessionsCount(context);

            // 2. 오늘 총 학습 시간 조회
            context.getLogger().log("\n[2단계] 오늘 학습 시간 조회 중...");
            int todayTotalMinutes = getTodayTotalLearningTime(context);

            // 3. 전체 학생 수 조회
            context.getLogger().log("\n[3단계] 전체 학생 수 조회 중...");
            int totalStudents = getTotalStudentsCount(context);

            context.getLogger().log("\n[4단계] 데이터 조합 중...");

            DashboardDataResponse response = DashboardDataResponse.builder()
                    .activeUsers(activeSessions)
                    .speakingDuration(todayTotalMinutes)
                    .orderCount(totalStudents)
                    .region("ap-northeast-2")
                    .build();

            String messageBody = gson.toJson(response);
            context.getLogger().log("📊 수집된 데이터:");
            context.getLogger().log("   - 활성 세션: " + activeSessions);
            context.getLogger().log("   - 학습 시간: " + todayTotalMinutes + "분");
            context.getLogger().log("   - 전체 학생: " + totalStudents + "명");
            context.getLogger().log("   - JSON: " + messageBody);

            // 5. SQS에 메시지 전송
            context.getLogger().log("\n[5단계] SQS에 전송 중...");
            context.getLogger().log("Queue URL: " + QUEUE_URL);

            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(sendRequest);


            context.getLogger().log("✅ SQS 전송 완료!");
            context.getLogger().log("========================================");
            context.getLogger().log("  데이터 수집 및 전송 성공");
            context.getLogger().log("========================================");

            return "Success: " + messageBody;

        } catch (Exception e) {
            context.getLogger().log("========================================");
            context.getLogger().log("  ❌ 에러 발생!");
            context.getLogger().log("========================================");
            context.getLogger().log("에러 타입: " + e.getClass().getName());
            context.getLogger().log("에러 메시지: " + e.getMessage());
            throw new RuntimeException("Failed to generate dashboard data", e);
        }

    }

    private int getActiveSessionsCount(Context context) {
        try {
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            String fiveMinutesAgoStr = String.valueOf(fiveMinutesAgo);

            context.getLogger().log("   검색 기준: " + fiveMinutesAgoStr + " 이후");

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":timestamp", AttributeValue.builder().s(fiveMinutesAgoStr).build());
            expressionValues.put(":status", AttributeValue.builder().s("ACTIVE").build());

            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(SESSIONS_TABLE)
                    .filterExpression("timestamp > :timestamp AND session_status = :status")
                    .expressionAttributeValues(expressionValues)
                    .build();

            ScanResponse response = dynamoDbClient.scan(scanRequest);
            int count = response.count();

            context.getLogger().log("   ✅ 활성 세션 수: " + count);
            return count;

        } catch (Exception e) {
            context.getLogger().log("   ⚠️ 활성 세션 조회 실패: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 오늘 총 학습 시간 조회 (분 단위)
     */
    private int getTodayTotalLearningTime(Context context) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            context.getLogger().log("   검색 날짜: " + today);

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":date", AttributeValue.builder().s(today).build());

            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(STATISTICS_TABLE)
                    .filterExpression("date_value = :date")
                    .expressionAttributeValues(expressionValues)
                    .build();

            ScanResponse response = dynamoDbClient.scan(scanRequest);

            int totalMinutes = 0;
            for (Map<String, AttributeValue> item : response.items()) {
                if (item.containsKey("total_learning_time_minutes")) {
                    totalMinutes += Integer.parseInt(
                            item.get("total_learning_time_minutes").n()
                    );
                }
            }

            context.getLogger().log("   ✅ 오늘 총 학습 시간: " + totalMinutes + "분");
            return totalMinutes;

        } catch (Exception e) {
            context.getLogger().log("   ⚠️ 학습 시간 조회 실패: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 전체 학생 수 조회
     */
    private int getTotalStudentsCount(Context context) {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(TUTOR_STUDENTS_TABLE)
                    .select(Select.COUNT)
                    .build();

            ScanResponse response = dynamoDbClient.scan(scanRequest);
            int count = response.count();

            context.getLogger().log("   ✅ 전체 학생 수: " + count);
            return count;

        } catch (Exception e) {
            context.getLogger().log("   ⚠️ 학생 수 조회 실패: " + e.getMessage());
            return 0;
        }
    }
}
