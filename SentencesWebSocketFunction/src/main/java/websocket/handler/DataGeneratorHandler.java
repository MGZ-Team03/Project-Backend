package websocket.handler;


import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import websocket.dto.dashboard.DashboardUpdateDto;
import websocket.dto.dashboard.StudentStatusDto;
import websocket.utils.StudentStatusCollector;

import java.util.*;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;



public class DataGeneratorHandler implements RequestHandler <Object, String>{

    private final SqsClient sqsClient;
    private final StudentStatusCollector collector;
    private final Gson gson;

    private static final String QUEUE_URL = System.getenv("QUEUE_URL");
//    private static final String USERS_TABLE = System.getenv("USERS_TABLE");
//    private static final String TUTOR_STUDENTS_TABLE = System.getenv("TUTOR_STUDENTS_TABLE");
//    private static final String SESSIONS_TABLE = System.getenv("SESSIONS_TABLE");

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
    public String handleRequest(Object o, Context context) {
        context.getLogger().log("========================================");
        context.getLogger().log("  학생 상태 수집 시작 (EventBridge), DataGeneratorHandler");
        context.getLogger().log("========================================");
        try {
            DashboardUpdateDto dashboardUpdate = collector.collectAllStudents();

            //json 반환
            String messageBody = gson.toJson(dashboardUpdate);
            getLogger().log(" json.messagebody : " + messageBody.length() + " lenghtn");
            // SQS 전송
            SendMessageRequest sendRequest = SendMessageRequest.builder()
                    .queueUrl(QUEUE_URL)
                    .messageBody(messageBody)
                    .build();
            sqsClient.sendMessage(sendRequest);

            context.getLogger().log("✅ SQS 전송 완료!");
            context.getLogger().log("========================================");

            return "Success: " + dashboardUpdate.getStudents().size() + " students processed";
        } catch (Exception e) {
            context.getLogger().log("❌ 에러: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to collect student statuses", e);
        }
    }


//    @Override
//    public String handleRequest(Object o, Context context) {
//        context.getLogger().log("========================================");
//        context.getLogger().log("  학생 상태 수집 시작");
//        context.getLogger().log("========================================");
//
//        try {
//            // 1. 학생 목록 조회
//            getLogger().log("\n[1단계] 학생 목록 조회 중...");
//            List<Map<String, AttributeValue>> studentRecords = getAllStudents();
//            getLogger().log("   전체 학생 수: " + studentRecords.size());
//
//            // 2. 각 학생의 실시간 상태 수집
//            context.getLogger().log("\n[2단계] 각 학생 상태 수집 중...");
//            List<StudentStatusDto> studentStatuses = new ArrayList<>();
//
//            // ✅ 루프: 모든 학생 처리
//            for (Map<String, AttributeValue> record : studentRecords) {
//                String studentEmail = record.get("student_email").s();
//                String tutorEmail = record.get("tutor_email").s();
//
//                String room = record.containsKey("room") && !"no room".equals(record.get("room").s())
//                        ? record.get("room").s()
//                        : "idle";
//
//                String connectionId = record.containsKey("connectionId") && record.get("connectionId") != null
//                        ? record.get("connectionId").s()
//                        : null;
//
//                StudentStatusDto studentStatus = collectStudentStatus(studentEmail, tutorEmail, room, connectionId);
//                studentStatuses.add(studentStatus);
//            }
//            // ✅ 루프 종료
//
//            getLogger().log("   상태 수집 완료: " + studentStatuses.size() + "명");
//
//            // 3. 통계 계산 (루프 밖에서!)
//            context.getLogger().log("\n[3단계] 통계 계산 중...");
//
//            int activeCount = (int) studentStatuses.stream()
//                    .filter(s -> !"inactive".equals(s.getStatus()))
//                    .count();
//
//            int speakingCount = (int) studentStatuses.stream()
//                    .filter(s -> "speaking".equals(s.getStatus()))
//                    .count();
//
//            int warningCount = (int) studentStatuses.stream()
//                    .filter(s -> Boolean.TRUE.equals(s.getWarning()) || Boolean.TRUE.equals(s.getAlert()))
//                    .count();
//
//            context.getLogger().log("   - 전체: " + studentStatuses.size() + "명");
//            context.getLogger().log("   - 활동 중: " + activeCount + "명");
//            context.getLogger().log("   - 발음 중: " + speakingCount + "명");
//            context.getLogger().log("   - 주의 필요: " + warningCount + "명");
//
//            // 4. 대시보드 업데이트 DTO 구성 (루프 밖에서!)
//            DashboardUpdateDto dashboardUpdate = DashboardUpdateDto.builder()
//                    .type("dashboard_update")
//                    .timestamp(System.currentTimeMillis())
//                    .students(studentStatuses)
//                    .summary(Map.of(
//                            "total", studentStatuses.size(),
//                            "active", activeCount,
//                            "speaking", speakingCount,
//                            "warning", warningCount
//                    ))
//                    .build();
//
//            // 5. JSON 변환
//            String messageBody = gson.toJson(dashboardUpdate);
//            context.getLogger().log("\n[4단계] JSON 변환 완료 (크기: " + messageBody.length() + " bytes)");
//
//            // 6. SQS 전송
//            getLogger().log("\n[5단계] SQS에 전송 중...");
//            SendMessageRequest sendRequest = SendMessageRequest.builder()
//                    .queueUrl(QUEUE_URL)
//                    .messageBody(messageBody)
//                    .build();
//
//            sqsClient.sendMessage(sendRequest);
//
//            context.getLogger().log("✅ SQS 전송 완료!");
//            context.getLogger().log("========================================");
//
//            return "Success: " + studentStatuses.size() + " students processed";
//
//
//
//        } catch (Exception e){
//            context.getLogger().log("========================================");
//            context.getLogger().log("  ❌ 에러 발생!");
//            context.getLogger().log("========================================");
//            context.getLogger().log("에러: " + e.getMessage());
//            e.printStackTrace();
//            throw new RuntimeException("Failed to collect student statuses", e);
//        }
//    }

    /**
     * 모든 학생 정보 조회 (tutor_students 테이블)
     */
//    private List<Map<String, AttributeValue>> getAllStudents(String tutorEmail){
//        try {
//            Map<String, AttributeValue> expressionValues = new HashMap<>();
//            expressionValues.put(":tutorEmail", AttributeValue.builder().s(tutorEmail).build());
//
//            ScanRequest scanRequest = ScanRequest.builder()
//                    .tableName(TUTOR_STUDENTS_TABLE)
//                    .filterExpression("tutor_email = :tutorEmail")
//                    .expressionAttributeValues(expressionValues)
//                    .build();
//
//            ScanResponse response = dynamoDbClient.scan(scanRequest);
//            return response.items();
//
//        } catch (Exception e){
//            getLogger().log("   ⚠️ 학생 목록 조회 실패: " + e.getMessage());
//            return List.of();
//        }
//    }
//
//    // 개별 학생 실시간 상태 수집
//    private StudentStatusDto collectStudentStatus(
//            String studentEmail,
//            String tutorEmail,
//            String room,
//            String connectionId
//    ) {
//        try {
//            // 1. 학생 이름 조회 없으면 메일로 조회
//            String studentName = getStudentName(studentEmail);
//            // 2. 연결 상태 확인
//            boolean isConnected = connectionId != null && !connectionId.isEmpty();
//
//            // 3. 최근 세션 데이터 조회
//            Map<String, Object> recentSession = isConnected ? getRecentSession(studentEmail) : null;
//
//
//            // 5. 상태 계산
//            String status = "inactive";
//            String activity = null;
//            int speakingRatio = 0;
//            int duration = 0;
//            boolean warning = false;
//            boolean alert = false;
//            String lastActive = null;
//
//            if (isConnected && room != "no room" && !room.isEmpty()) {
//                activity = room;
//                if (recentSession != null) {
//                    // 최근 5분 이내 세션이 있는 경우
//                    speakingRatio = (Integer) recentSession.getOrDefault("speaking_ratio", 0);
//                    duration = (Integer) recentSession.getOrDefault("duration", 0);
//
//                    // 발음 중인지 판단
//                    if (speakingRatio > 0) {
//                        status = "speaking";
//                    } else {
//                        status = "listene";
//                    }
//
//                    // 경고 판단: speaking인데 비율이 낮으면
//                    if ("speaking".equals(status) && speakingRatio < 50) {
//                        warning = true;
//                    }
//                } else {
//                    // 방에는 입장했지만 활동이 없음
//                    status = "idle";
//                }
//            }
//             else {
//                // 연결되어 있지 않음
//                status = "inactive";
//                lastActive = "5분 전";
//                alert = true;
//            }
//            return StudentStatusDto.builder()
//                    .email(studentEmail)
//                    .name(studentName)
//                    .tutorEmail(tutorEmail)
//                    .room(activity)
//                    .status(status)
//                    .speakingRatio(speakingRatio)
//                    .duration(duration)
//                    .currentSentence(null)
//                    .currentTopic(null)
//                    .warning(warning)
//                    .alert(alert)
//                    .lastActive(lastActive)
//                    .build();
//        } catch (Exception e) {
//            getLogger().log("   ⚠️ 학생 상태 수집 실패 [" + studentEmail + "]: " + e.getMessage());
//
//            // 실패 시 기본값 반환
//            return StudentStatusDto.builder()
//                    .email(studentEmail)
//                    .name(getStudentName(studentEmail))
//                    .status("inactive")
//                    .speakingRatio(0)
//                    .duration(0)
//                    .alert(true)
//                    .build();
//        }
//
//    }
//
//
//    private Map<String, Object> getRecentSession(String studentEmail) {
//        try {
//            // 5분 전 타임스탬프
//            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
//            String fiveMinutesAgoStr = String.valueOf(fiveMinutesAgo);
//
//            Map<String, AttributeValue> expressionValues = new HashMap<>();
//            expressionValues.put(":student_email", AttributeValue.builder()
//                    .s(studentEmail).build());
//            expressionValues.put(":timestamp", AttributeValue.builder()
//                    .s(fiveMinutesAgoStr).build());
//
//            QueryRequest queryRequest = QueryRequest.builder()
//                    .tableName(SESSIONS_TABLE)
//                    .keyConditionExpression("student_email = :student_email AND timestamp > :timestamp")
//                    .expressionAttributeValues(expressionValues)
//                    .scanIndexForward(false)
//                    .limit(1)
//                    .build();
//
//            QueryResponse response = dynamoDbClient.query(queryRequest);
//            if (response.items().isEmpty()) {
//                getLogger().log("=== Session_Table is not Found");
//                return null;
//            }
//
//            Map<String, AttributeValue> item = response.items().get(0);
//
//            Map<String, Object> session = new HashMap<>();
//            session.put("speaking_ratio", item.containsKey("speaking_ratio")
//                    ? Integer.parseInt(item.get("speaking_ratio").n()) : 0);
//            session.put("duration", item.containsKey("duration")
//                    ? Integer.parseInt(item.get("duration").n()) / 60 : 0); // 초 -> 분
//
//            return session;
//        } catch (Exception e) {
//            getLogger().log("==== ❌ getRecentSession.Error=====");
//            return null;
//        }
//    }
//    private String getStudentName(String studentEmail) {
//        getLogger().log("==== get student name ====");
//        getLogger().log("Student Email: " + studentEmail);
//        getLogger().log("Table Name: " + USERS_TABLE);
//
//        try {
//            // ✅ projectionExpression 제거하고 전체 아이템 가져오기
//            GetItemRequest request = GetItemRequest.builder()
//                    .tableName(USERS_TABLE)
//                    .key(Map.of("email", AttributeValue.builder().s(studentEmail).build()))
//                    .build();
//
//            GetItemResponse response = dynamoDbClient.getItem(request);
//
//            getLogger().log("Has Item: " + response.hasItem());
//
//            // ✅ 전체 아이템 내용 확인
//            if (response.hasItem()) {
//                getLogger().log("전체 아이템: " + response.item());
//                getLogger().log("아이템 키들: " + response.item().keySet());
//
//                // name 필드 확인
//                if (response.item().containsKey("name")) {
//                    String name = response.item().get("name").s();
//                    getLogger().log("✅ Found name: " + name);
//                    return name;
//                } else {
//                    getLogger().log("❌ name 필드가 없습니다!");
//                    getLogger().log("사용 가능한 필드: " + response.item().keySet());
//                }
//            } else {
//                getLogger().log("❌ 아이템을 찾을 수 없습니다!");
//                getLogger().log("검색한 이메일: " + studentEmail);
//            }
//
//            return studentEmail.split("@")[0];
//
//        } catch (Exception e) {
//            getLogger().log("❌ Error: " + e.getMessage());
//            e.printStackTrace();
//            return studentEmail.split("@")[0];
//        }
//    }
//
//    private List<Map<String, AttributeValue>> getAllStudents(){
//        try {
//            ScanRequest scanRequest = ScanRequest.builder()
//                    .tableName(TUTOR_STUDENTS_TABLE)
//                    .build();
//
//            ScanResponse response = dynamoDbClient.scan(scanRequest);
//            getLogger().log("=== get AllStudents ==== : " +response.items().size());
//            getLogger().log("=== get AllStudents ==== : " +response.items());
//            return response.items();
//
//        } catch (Exception e){
//            getLogger().log("   ⚠️ 학생 목록 조회 실패: " + e.getMessage());
//            return List.of();
//        }
//    }

}
