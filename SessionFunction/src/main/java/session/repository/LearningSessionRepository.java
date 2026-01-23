package session.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import session.model.LearningSession;
import session.model.PracticeRecord;
import session.model.SessionEndRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

public class LearningSessionRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public LearningSessionRepository(String tableName) {
        this.dynamoDbClient = DynamoDbClient.create();
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 세션 생성 (시작)
     */
    public String createSession(String studentEmail, String sessionType, String tutorEmail) {
        String timestamp = Instant.now().toString();
        long ttl = Instant.now().plusSeconds(90 * 24 * 60 * 60).getEpochSecond(); // 90일 후

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("student_email", AttributeValue.builder().s(studentEmail).build());
        item.put("timestamp", AttributeValue.builder().s(timestamp).build());
        item.put("session_type", AttributeValue.builder().s(sessionType).build());
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        if (tutorEmail != null && !tutorEmail.trim().isEmpty()) {
            item.put("tutor_email", AttributeValue.builder().s(tutorEmail).build());
        }

        PutItemRequest request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build();

        dynamoDbClient.putItem(request);

        return timestamp;
    }

    /**
     * 세션 업데이트 (종료)
     */
    public void updateSession(SessionEndRequest request, double netSpeakingDensity,
                              Double avgPaceRatio, Double avgResponseLatency) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("student_email", AttributeValue.builder().s(request.getStudentEmail()).build());
            key.put("timestamp", AttributeValue.builder().s(request.getTimestamp()).build());

            Map<String, AttributeValue> expressionValues = new HashMap<>();
            Map<String, String> expressionNames = new HashMap<>();

            StringBuilder updateExpression = new StringBuilder("SET ");
            updateExpression.append("#rd = :rd, #sd = :sd, #nsd = :nsd");

            expressionNames.put("#rd", "recording_duration");
            expressionNames.put("#sd", "speaking_duration");
            expressionNames.put("#nsd", "net_speaking_density");

            expressionValues.put(":rd", AttributeValue.builder().n(String.valueOf(request.getRecordingDuration())).build());
            expressionValues.put(":sd", AttributeValue.builder().n(String.valueOf(request.getSpeakingDuration())).build());
            expressionValues.put(":nsd", AttributeValue.builder().n(String.valueOf(netSpeakingDensity)).build());

            // 문장 연습 전용 필드
            if (request.getPracticeRecords() != null && !request.getPracticeRecords().isEmpty()) {
                String practiceRecordsJson = objectMapper.writeValueAsString(request.getPracticeRecords());
                updateExpression.append(", #pr = :pr, #apr = :apr");
                expressionNames.put("#pr", "practice_records");
                expressionNames.put("#apr", "avg_pace_ratio");
                expressionValues.put(":pr", AttributeValue.builder().s(practiceRecordsJson).build());
                expressionValues.put(":apr", AttributeValue.builder().n(String.valueOf(avgPaceRatio)).build());
            }

            // AI 대화 전용 필드
            if (request.getResponseLatencies() != null && !request.getResponseLatencies().isEmpty()) {
                String latenciesJson = objectMapper.writeValueAsString(request.getResponseLatencies());
                updateExpression.append(", #rl = :rl, #arl = :arl");
                expressionNames.put("#rl", "response_latencies");
                expressionNames.put("#arl", "avg_response_latency");
                expressionValues.put(":rl", AttributeValue.builder().s(latenciesJson).build());
                expressionValues.put(":arl", AttributeValue.builder().n(String.valueOf(avgResponseLatency)).build());
            }

            if (request.getChatTurnsCount() != null) {
                updateExpression.append(", #ctc = :ctc");
                expressionNames.put("#ctc", "chat_turns_count");
                expressionValues.put(":ctc", AttributeValue.builder().n(String.valueOf(request.getChatTurnsCount())).build());
            }

            UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpression.toString())
                .expressionAttributeNames(expressionNames)
                .expressionAttributeValues(expressionValues)
                .build();

            dynamoDbClient.updateItem(updateRequest);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize session data", e);
        }
    }

    /**
     * 세션 이력 조회 (학생별)
     */
    public List<LearningSession> getSessionsByStudent(String studentEmail, int limit) {
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":email", AttributeValue.builder().s(studentEmail).build());

        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("student_email = :email")
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)  // 최신순 정렬
            .limit(limit)
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        List<LearningSession> sessions = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            sessions.add(parseSession(item));
        }

        return sessions;
    }

    /**
     * 세션 이력 조회 (튜터별 - GSI 사용)
     */
    public List<LearningSession> getSessionsByTutor(String tutorEmail, int limit) {
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":tutor", AttributeValue.builder().s(tutorEmail).build());

        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .indexName("tutor_email-timestamp-index")
            .keyConditionExpression("tutor_email = :tutor")
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)
            .limit(limit)
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        List<LearningSession> sessions = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            sessions.add(parseSession(item));
        }

        return sessions;
    }

    private LearningSession parseSession(Map<String, AttributeValue> item) {
        LearningSession session = new LearningSession();

        session.setStudentEmail(getStringValue(item, "student_email"));
        session.setTimestamp(getStringValue(item, "timestamp"));
        session.setSessionType(getStringValue(item, "session_type"));
        session.setTutorEmail(getStringValue(item, "tutor_email"));

        if (item.containsKey("recording_duration")) {
            session.setRecordingDuration(Long.parseLong(item.get("recording_duration").n()));
        }
        if (item.containsKey("speaking_duration")) {
            session.setSpeakingDuration(Long.parseLong(item.get("speaking_duration").n()));
        }
        if (item.containsKey("net_speaking_density")) {
            session.setNetSpeakingDensity(Double.parseDouble(item.get("net_speaking_density").n()));
        }
        if (item.containsKey("avg_pace_ratio")) {
            session.setAvgPaceRatio(Double.parseDouble(item.get("avg_pace_ratio").n()));
        }
        if (item.containsKey("avg_response_latency")) {
            session.setAvgResponseLatency(Double.parseDouble(item.get("avg_response_latency").n()));
        }
        if (item.containsKey("chat_turns_count")) {
            session.setChatTurnsCount(Integer.parseInt(item.get("chat_turns_count").n()));
        }

        // practice_records (JSON 문자열)
        if (item.containsKey("practice_records")) {
            try {
                String json = item.get("practice_records").s();
                List<PracticeRecord> records = objectMapper.readValue(json, new TypeReference<List<PracticeRecord>>() {});
                session.setPracticeRecords(records);
            } catch (JsonProcessingException e) {
                // 파싱 실패 시 빈 리스트
                session.setPracticeRecords(new ArrayList<>());
            }
        }

        // response_latencies (JSON 문자열)
        if (item.containsKey("response_latencies")) {
            try {
                String json = item.get("response_latencies").s();
                List<Long> latencies = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
                session.setResponseLatencies(latencies);
            } catch (JsonProcessingException e) {
                session.setResponseLatencies(new ArrayList<>());
            }
        }

        return session;
    }

    private String getStringValue(Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key) && item.get(key).s() != null) {
            return item.get(key).s();
        }
        return null;
    }
}
