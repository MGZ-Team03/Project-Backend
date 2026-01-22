package websocket.utils;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import websocket.dto.dashboard.DashboardUpdateDto;
import websocket.dto.dashboard.StudentStatusDto;

import javax.management.Query;
import java.time.Instant;
import java.util.*;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class StudentStatusCollector {

    private final DynamoDbClient dynamoDbClient;
    private final String tutorStudentTable;
    private final String usersTable;
    private final String sessionsTable;

    public DashboardUpdateDto collectAllStudents() {
        getLogger().log("========================================");
        getLogger().log("  전체 학생 상태 수집");
        getLogger().log("========================================");

        List<Map<String, AttributeValue>> studentRecords = getAllStudents();

        getLogger().log("✅ 전체학생수: " + studentRecords.size());
        getLogger().log("studentRecords: " + studentRecords);
        return processStudentRecords(studentRecords);
    }

    /**
     * 특정 튜터의 학생 상태 수집 (즉시 전송용)
     */
    public DashboardUpdateDto collectByTutor(String tutorEmail) {
        getLogger().log("========================================");
        getLogger().log("  튜터별 학생 상태 수집: " + tutorEmail);
        getLogger().log("========================================");

        List<Map<String, AttributeValue>> studentRecords = getStudentsByTutor(tutorEmail);
        getLogger().log("학생 수: " + studentRecords.size());

        return processStudentRecords(studentRecords);
    }

    /**
     * 학생 레코드 처리 (공통 로직)
     */
    private DashboardUpdateDto processStudentRecords(List<Map<String, AttributeValue>> studentRecords){
        List<StudentStatusDto> studentStatuses = new ArrayList<>();

        // 각 학생 상태 수집
        for (Map<String, AttributeValue> record : studentRecords) {
            try {
                // ✅ null 체크 추가
                if (!record.containsKey("student_email") || record.get("student_email") == null) {
                    getLogger().log("⚠️ student_email 없음, 레코드 스킵: " + record.keySet());
                    continue;
                }

                if (!record.containsKey("tutor_email") || record.get("tutor_email") == null) {
                    getLogger().log("⚠️ tutor_email 없음, 레코드 스킵: " + record.keySet());
                    continue;
                }

                String studentEmail = record.get("student_email").s();
                String tutorEmail = record.get("tutor_email").s();

                // ✅ 튜터 자신의 연결인지 체크 (튜터 대시보드 연결은 제외)
                if (studentEmail.equals(tutorEmail)) {
                    getLogger().log("⚠️ 튜터 자신의 연결, 스킵: " + tutorEmail);
                    continue;
                }

                String room = record.containsKey("room") && record.get("room") != null && !"no room".equals(record.get("room").s())
                        ? record.get("room").s()
                        : "idle";

                String connectionId = record.containsKey("connectionId") && record.get("connectionId") != null
                        ? record.get("connectionId").s()
                        : null;

                StudentStatusDto status = collectStudentStatus(studentEmail, tutorEmail, room, connectionId);
                studentStatuses.add(status);

            } catch (Exception e) {
                getLogger().log("❌ 레코드 처리 실패: " + e.getMessage());
                getLogger().log("레코드 내용: " + record);
                // 이 레코드는 스킵하고 다음으로
            }
        }

        getLogger().log("상태 수집 완료: " + studentStatuses.size() + "명");

        // 통계 계산
        int activeCount = (int) studentStatuses.stream()
                .filter(s -> !"inactive".equals(s.getStatus()))
                .count();

        int speakingCount = (int) studentStatuses.stream()
                .filter(s -> "speaking".equals(s.getStatus()))
                .count();

        int warningCount = (int) studentStatuses.stream()
                .filter(s -> Boolean.TRUE.equals(s.getWarning()) || Boolean.TRUE.equals(s.getAlert()))
                .count();

        getLogger().log("통계 - 전체: " + studentStatuses.size() + ", 활동: " + activeCount +
                ", 발음: " + speakingCount + ", 주의: " + warningCount);

        return DashboardUpdateDto.builder()
                .type("dashboard_update")
                .timestamp(System.currentTimeMillis())
                .students(studentStatuses)
                .summary(Map.of(
                        "total", studentStatuses.size(),
                        "active", activeCount,
                        "speaking", speakingCount,
                        "warning", warningCount
                ))
                .build();
    }

    private List<Map<String, AttributeValue>> getAllStudents() {
        try {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName(tutorStudentTable)
                    .build();

            ScanResponse response = dynamoDbClient.scan(scanRequest);
            getLogger().log("전체 학생: " + response.items().size());
            return response.items();

        } catch (Exception e) {
            getLogger().log("⚠️ 전체 학생 조회 실패: " + e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, AttributeValue>> getStudentsByTutor(String tutorEmail) {
        try {
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":tutorEmail", AttributeValue.builder().s(tutorEmail).build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(tutorStudentTable)
                    .keyConditionExpression("tutor_email = :tutorEmail")
                    .expressionAttributeValues(expressionValues)
                    .build();

            getLogger().log("getStudentByTutor.qeury result : " + dynamoDbClient.query(request).items());
            return dynamoDbClient.query(request).items();
        } catch (Exception e) {
            getLogger().log("⚠️ 튜터별 학생 조회 실패: " + e.getMessage());
            return List.of();
        }
    }



    private StudentStatusDto collectStudentStatus(String studentEmail,
                                                  String tutorEmail,
                                                  String room,
                                                  String connectionId) {

        try {
            getLogger().log("=== 학생 상태 수집 ===");
            getLogger().log("학생: " + studentEmail);
            getLogger().log("튜터: " + tutorEmail);
            getLogger().log("방: " + room);
            getLogger().log("연결ID: " + connectionId);

            String studentName = getStudentName(studentEmail);

            // ✅ 버그 수정: isEmpty() → !isEmpty()
            boolean isConnected = connectionId != null && !connectionId.isEmpty();
            getLogger().log("연결 상태: " + (isConnected ? "로그인" : "로그아웃"));

            // 최근 5분 이내 세션 조회 (연결된 경우만)
            Map<String, Object> recentSession = isConnected ? getRecentSession(studentEmail) : null;

            // 기본값 설정
            String status = "inactive";
            String activity = null;
            int speakingRatio = 0;
            int duration = 0;
            boolean warning = false;
            boolean alert = false;
            String lastActive = null;

            // 📊 상태 결정 로직
            if (isConnected && !Objects.equals(room, "no room") && !room.isEmpty()) {
                getLogger().log("✅ 유효한 방에 입장: " + room);
                activity = room;  // "sentence" or "ai"

                if (recentSession != null) {
                    // 최근 활동 있음
                    speakingRatio = (Integer) recentSession.getOrDefault("speaking_ratio", 0);
                    duration = (Integer) recentSession.getOrDefault("duration", 0);

                    if (speakingRatio > 0) {
                        status = "speaking";  // 🎤 발음 중
                        getLogger().log("🎤 발음 중 (비율: " + speakingRatio + "%)");
                    } else {
                        status = "listening";  // 👂 듣기만
                        getLogger().log("👂 듣기만 (발음 없음)");
                    }

                    // ⚠️ 경고: 발음 중인데 비율이 50% 미만
                    if ("speaking".equals(status) && speakingRatio < 50) {
                        warning = true;
                        getLogger().log("⚠️ 경고: 발음 비율 낮음");
                    }
                } else {
                    // 방에는 있지만 최근 5분 이내 활동 없음
                    status = "idle";  // 💤 대기 중
                    alert = true;
                    getLogger().log("💤 방에는 있지만 활동 없음");
                }
            } else if(isConnected){
                // ✅ 연결은 되어 있지만 유효한 방이 없음
                status = "idle";      // 🔴
                alert = true;         // 개입 필요!
                getLogger().log("🔴 연결되어 있지만 방 없음 (idle)");
            } else {
                // ✅ 로그아웃 상태
                status = "inactive";  // ⚪
                lastActive = "5분 전";
                alert = false;
                getLogger().log("⚪ 오프라인 (inactive)");
            }
            getLogger().log("activity: " + activity);
            getLogger().log("최종 상태: " + status);
            getLogger().log("========================================");

            return StudentStatusDto.builder()
                    .email(studentEmail)
                    .name(studentName)
                    .tutorEmail(tutorEmail)
                    .activity(activity)  // "sentence", "ai", or null
                    .status(status)  // "speaking", "listening", "idle", "inactive"
                    .speakingRatio(speakingRatio)
                    .duration(duration)
                    .warning(warning)
                    .alert(alert)
                    .lastActive(lastActive)
                    .build();

        } catch (Exception e) {
            getLogger().log("❌ 학생 상태 수집 실패 [" + studentEmail + "]: " + e.getMessage());
            e.printStackTrace();

            // 실패 시 기본값 (비활성 상태)
            return StudentStatusDto.builder()
                    .email(studentEmail)
                    .name(studentEmail.split("@")[0])
                    .tutorEmail(tutorEmail)
                    .status("inactive")
                    .speakingRatio(0)
                    .duration(0)
                    .alert(true)
                    .build();
        }
    }

    private Map<String, Object> getRecentSession(String studentEmail) {
        try {
            getLogger().log("getRecentSession start!!");
            getLogger().log("studentEmail: "+ studentEmail);


            // 5분 전 타임스탬프
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            // ISO 형식으로 변경
            Instant fiveMinutesAgoInstant = Instant.ofEpochMilli(fiveMinutesAgo);
            String fiveMinutesAgoStr = fiveMinutesAgoInstant.toString();  // "2026-01-21T06:27:23.456Z"

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":student_email",AttributeValue.builder()
                    .s(studentEmail).build());
            expressionValues.put(":timestamp",AttributeValue.builder()
                    .s(fiveMinutesAgoStr).build());

            Map<String, String> expressionNames = new HashMap<>();
            expressionNames.put("#ts", "timestamp");  // #ts → timestamp

            getLogger().log("sessionTable : "+sessionsTable);


            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(sessionsTable)
                    .keyConditionExpression("student_email = :student_email AND :timestamp > #ts")
                    .expressionAttributeValues(expressionValues)
                    .expressionAttributeNames(expressionNames)  // ✅ 추가!
                    .scanIndexForward(false)
                    .limit(1)
                    .build();


            QueryResponse response = dynamoDbClient.query(queryRequest);
            getLogger().log("조회된 항목 수: " + response.items().size());

            if (response.items().isEmpty()) {
                getLogger().log("❌ 5분 이내 세션 없음");
                getLogger().log("========================================");
                return null;
            }
            Map<String, AttributeValue> item = response.items().getFirst();
            getLogger().log("✅ 세션 발견!");


            Map<String, Object> session = new HashMap<>();

            session.put("speaking_ratio", item.containsKey("speaking_ratio")
                    ? Integer.parseInt(item.get("speaking_ratio").n()) : 0);
            session.put("duration", item.containsKey("duration")
                    ? Integer.parseInt(item.get("duration").n()) / 60 : 0);

            getLogger().log("speaking_ratio: " + session.get("speaking_ratio"));
            getLogger().log("duration: " + session.get("duration") + "분");
            getLogger().log("========================================");

            return session;


        } catch (Exception e){
            getLogger().log("⚠️ getRecentSession 실패: " + e.getMessage());
            return null;
        }
    }

    private String getStudentName(String studentEmail) {

        try {
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(usersTable)
                    .key(Map.of("email", AttributeValue.builder().s(studentEmail).build()))
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);

            if (response.hasItem() && response.item().containsKey("name")) {
                return response.item().get("name").s();
            }

            return studentEmail.split("@")[0];

        } catch (Exception e) {
            getLogger().log("⚠️ getStudentName 실패: " + e.getMessage());
            return studentEmail.split("@")[0];
        }
    }

}
