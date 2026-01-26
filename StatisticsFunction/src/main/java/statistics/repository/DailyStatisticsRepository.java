package statistics.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import statistics.model.DailyStatistics;
import statistics.model.ResponseQuality;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DailyStatisticsRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public DailyStatisticsRepository(String tableName) {
        this.dynamoDbClient = DynamoDbClient.create();
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 오늘 통계 조회
     */
    public DailyStatistics getToday(String studentEmail) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return getByDate(studentEmail, today);
    }

    /**
     * 특정 날짜 통계 조회
     */
    public DailyStatistics getByDate(String studentEmail, String date) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("student_email", AttributeValue.builder().s(studentEmail).build());
        key.put("date", AttributeValue.builder().s(date).build());

        GetItemRequest request = GetItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .build();

        GetItemResponse response = dynamoDbClient.getItem(request);

        if (!response.hasItem()) {
            return null;
        }

        return parseStatistics(response.item());
    }

    /**
     * 일별 통계 저장 (Upsert - merge 방식)
     * - 요청에 포함된 필드만 저장(SET)하고, 없는 필드는 기존 값을 유지합니다.
     * - 프론트가 메모리/전송량 때문에 리스트(pace_ratios/response_latencies 등)를 생략할 수 있으므로
     *   PutItem(덮어쓰기) 대신 UpdateItem(merge upsert)을 사용합니다.
     */
    public void saveDailyStatistics(DailyStatistics stats) {
        try {
            // 필수 키
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("student_email", AttributeValue.builder().s(stats.getStudentEmail()).build());
            key.put("date", AttributeValue.builder().s(stats.getDate()).build());

            // TTL 설정 (30일 후 자동 삭제)
            long ttl = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond();
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

            StringBuilder updateExpression = new StringBuilder("SET ttl = :ttl");

            // 기본 통계
            if (stats.getTotalRecordingTime() != null) {
                updateExpression.append(", total_recording_time = :trt");
                expressionValues.put(":trt", AttributeValue.builder().n(String.valueOf(stats.getTotalRecordingTime())).build());
            }
            if (stats.getTotalSpeakingTime() != null) {
                updateExpression.append(", total_speaking_time = :tst");
                expressionValues.put(":tst", AttributeValue.builder().n(String.valueOf(stats.getTotalSpeakingTime())).build());
            }
            if (stats.getSessionsCount() != null) {
                updateExpression.append(", sessions_count = :sc");
                expressionValues.put(":sc", AttributeValue.builder().n(String.valueOf(stats.getSessionsCount())).build());
            }
            if (stats.getPracticeCount() != null) {
                updateExpression.append(", practice_count = :pc");
                expressionValues.put(":pc", AttributeValue.builder().n(String.valueOf(stats.getPracticeCount())).build());
            }
            if (stats.getChatTurnsCount() != null) {
                updateExpression.append(", chat_turns_count = :ctc");
                expressionValues.put(":ctc", AttributeValue.builder().n(String.valueOf(stats.getChatTurnsCount())).build());
            }

            // 3대 지표 평균
            if (stats.getAvgPaceRatio() != null) {
                updateExpression.append(", avg_pace_ratio = :apr");
                expressionValues.put(":apr", AttributeValue.builder().n(String.valueOf(stats.getAvgPaceRatio())).build());
            }
            if (stats.getAvgResponseLatency() != null) {
                updateExpression.append(", avg_response_latency = :arl");
                expressionValues.put(":arl", AttributeValue.builder().n(String.valueOf(stats.getAvgResponseLatency())).build());
            }
            if (stats.getAvgNetSpeakingDensity() != null) {
                updateExpression.append(", avg_net_speaking_density = :ansd");
                expressionValues.put(":ansd", AttributeValue.builder().n(String.valueOf(stats.getAvgNetSpeakingDensity())).build());
            }

            // 새 필드: avg_response_quality
            if (stats.getAvgResponseQuality() != null) {
                updateExpression.append(", avg_response_quality = :arq");
                expressionValues.put(":arq", AttributeValue.builder().n(String.valueOf(stats.getAvgResponseQuality())).build());
            }

            // 상세 기록 (JSON으로 저장)
            if (stats.getPaceRatios() != null && !stats.getPaceRatios().isEmpty()) {
                updateExpression.append(", pace_ratios = :prs");
                expressionValues.put(":prs", AttributeValue.builder().s(objectMapper.writeValueAsString(stats.getPaceRatios())).build());
            }
            if (stats.getResponseLatencies() != null && !stats.getResponseLatencies().isEmpty()) {
                updateExpression.append(", response_latencies = :rls");
                expressionValues.put(":rls", AttributeValue.builder().s(objectMapper.writeValueAsString(stats.getResponseLatencies())).build());
            }

            // 새 필드: response_qualities (JSON으로 저장)
            if (stats.getResponseQualities() != null && !stats.getResponseQualities().isEmpty()) {
                updateExpression.append(", response_qualities = :rqs");
                expressionValues.put(":rqs", AttributeValue.builder().s(objectMapper.writeValueAsString(stats.getResponseQualities())).build());
            }

            // count 필드 (리스트 생략 시에도 주간 가중평균 계산용)
            if (stats.getPaceRatioCount() != null) {
                updateExpression.append(", pace_ratio_count = :prc");
                expressionValues.put(":prc", AttributeValue.builder().n(String.valueOf(stats.getPaceRatioCount())).build());
            }
            if (stats.getResponseLatencyCount() != null) {
                updateExpression.append(", response_latency_count = :rlc");
                expressionValues.put(":rlc", AttributeValue.builder().n(String.valueOf(stats.getResponseLatencyCount())).build());
            }
            if (stats.getResponseQualityCount() != null) {
                updateExpression.append(", response_quality_count = :rqc");
                expressionValues.put(":rqc", AttributeValue.builder().n(String.valueOf(stats.getResponseQualityCount())).build());
            }

            UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(updateExpression.toString())
                .expressionAttributeValues(expressionValues)
                .build();

            dynamoDbClient.updateItem(request);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize statistics data", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save daily statistics", e);
        }
    }

    /**
     * 주간 통계 조회 (최근 7일)
     */
    public List<DailyStatistics> getWeekly(String studentEmail) {
        LocalDate today = LocalDate.now();
        List<DailyStatistics> weeklyStats = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            DailyStatistics stats = getByDate(studentEmail, date);

            // 데이터가 없는 날도 빈 객체로 포함
            if (stats == null) {
                stats = createEmptyStatistics(studentEmail, date);
            }
            weeklyStats.add(stats);
        }

        return weeklyStats;
    }

    /**
     * 통계 초기화 또는 업데이트
     */
    public void upsertStatistics(String studentEmail, String date,
                                 Long recordingDuration, Long speakingDuration,
                                 String sessionType, Integer chatTurnsCount,
                                 List<Double> paceRatios, List<Long> responseLatencies) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("student_email", AttributeValue.builder().s(studentEmail).build());
            key.put("date", AttributeValue.builder().s(date).build());

            // 먼저 기존 데이터 조회
            DailyStatistics existing = getByDate(studentEmail, date);

            if (existing == null) {
                // 새로 생성
                createNewStatistics(studentEmail, date, recordingDuration, speakingDuration,
                                  sessionType, chatTurnsCount, paceRatios, responseLatencies);
            } else {
                // 기존 데이터 업데이트
                updateExistingStatistics(studentEmail, date, existing, recordingDuration, speakingDuration,
                                        sessionType, chatTurnsCount, paceRatios, responseLatencies);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to upsert daily statistics", e);
        }
    }

    private void createNewStatistics(String studentEmail, String date,
                                     Long recordingDuration, Long speakingDuration,
                                     String sessionType, Integer chatTurnsCount,
                                     List<Double> paceRatios, List<Long> responseLatencies) throws JsonProcessingException {

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("student_email", AttributeValue.builder().s(studentEmail).build());
        item.put("date", AttributeValue.builder().s(date).build());
        item.put("total_recording_time", AttributeValue.builder().n(String.valueOf(recordingDuration)).build());
        item.put("total_speaking_time", AttributeValue.builder().n(String.valueOf(speakingDuration)).build());
        item.put("sessions_count", AttributeValue.builder().n("1").build());

        int practiceCount = "sentence".equals(sessionType) ? 1 : 0;
        item.put("practice_count", AttributeValue.builder().n(String.valueOf(practiceCount)).build());
        item.put("chat_turns_count", AttributeValue.builder().n(String.valueOf(chatTurnsCount != null ? chatTurnsCount : 0)).build());

        // 평균 계산
        if (paceRatios != null && !paceRatios.isEmpty()) {
            double avgPaceRatio = paceRatios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            item.put("avg_pace_ratio", AttributeValue.builder().n(String.valueOf(avgPaceRatio)).build());
            item.put("pace_ratios", AttributeValue.builder().s(objectMapper.writeValueAsString(paceRatios)).build());
        }

        if (responseLatencies != null && !responseLatencies.isEmpty()) {
            double avgResponseLatency = responseLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            item.put("avg_response_latency", AttributeValue.builder().n(String.valueOf(avgResponseLatency)).build());
            item.put("response_latencies", AttributeValue.builder().s(objectMapper.writeValueAsString(responseLatencies)).build());
        }

        double avgNetSpeakingDensity = recordingDuration > 0 ? (double) speakingDuration / recordingDuration * 100.0 : 0.0;
        item.put("avg_net_speaking_density", AttributeValue.builder().n(String.valueOf(avgNetSpeakingDensity)).build());

        PutItemRequest request = PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build();

        dynamoDbClient.putItem(request);
    }

    private void updateExistingStatistics(String studentEmail, String date, DailyStatistics existing,
                                         Long recordingDuration, Long speakingDuration,
                                         String sessionType, Integer chatTurnsCount,
                                         List<Double> paceRatios, List<Long> responseLatencies) throws JsonProcessingException {

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("student_email", AttributeValue.builder().s(studentEmail).build());
        key.put("date", AttributeValue.builder().s(date).build());

        // 누적 계산
        long newTotalRecording = existing.getTotalRecordingTime() + recordingDuration;
        long newTotalSpeaking = existing.getTotalSpeakingTime() + speakingDuration;
        int newSessionsCount = existing.getSessionsCount() + 1;
        int newPracticeCount = existing.getPracticeCount() + ("sentence".equals(sessionType) ? 1 : 0);
        int newChatTurns = existing.getChatTurnsCount() + (chatTurnsCount != null ? chatTurnsCount : 0);

        // 리스트 병합
        List<Double> allPaceRatios = new ArrayList<>(existing.getPaceRatios() != null ? existing.getPaceRatios() : new ArrayList<>());
        if (paceRatios != null) {
            allPaceRatios.addAll(paceRatios);
        }

        List<Long> allResponseLatencies = new ArrayList<>(existing.getResponseLatencies() != null ? existing.getResponseLatencies() : new ArrayList<>());
        if (responseLatencies != null) {
            allResponseLatencies.addAll(responseLatencies);
        }

        // 평균 재계산
        Double newAvgPaceRatio = null;
        if (!allPaceRatios.isEmpty()) {
            newAvgPaceRatio = allPaceRatios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        }

        Double newAvgResponseLatency = null;
        if (!allResponseLatencies.isEmpty()) {
            newAvgResponseLatency = allResponseLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        double newAvgNetSpeakingDensity = newTotalRecording > 0 ? (double) newTotalSpeaking / newTotalRecording * 100.0 : 0.0;

        // UpdateExpression 구성
        StringBuilder updateExpression = new StringBuilder("SET ");
        updateExpression.append("total_recording_time = :trt, ");
        updateExpression.append("total_speaking_time = :tst, ");
        updateExpression.append("sessions_count = :sc, ");
        updateExpression.append("practice_count = :pc, ");
        updateExpression.append("chat_turns_count = :ctc, ");
        updateExpression.append("avg_net_speaking_density = :ansd");

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":trt", AttributeValue.builder().n(String.valueOf(newTotalRecording)).build());
        expressionValues.put(":tst", AttributeValue.builder().n(String.valueOf(newTotalSpeaking)).build());
        expressionValues.put(":sc", AttributeValue.builder().n(String.valueOf(newSessionsCount)).build());
        expressionValues.put(":pc", AttributeValue.builder().n(String.valueOf(newPracticeCount)).build());
        expressionValues.put(":ctc", AttributeValue.builder().n(String.valueOf(newChatTurns)).build());
        expressionValues.put(":ansd", AttributeValue.builder().n(String.valueOf(newAvgNetSpeakingDensity)).build());

        if (newAvgPaceRatio != null) {
            updateExpression.append(", avg_pace_ratio = :apr, pace_ratios = :prs");
            expressionValues.put(":apr", AttributeValue.builder().n(String.valueOf(newAvgPaceRatio)).build());
            expressionValues.put(":prs", AttributeValue.builder().s(objectMapper.writeValueAsString(allPaceRatios)).build());
        }

        if (newAvgResponseLatency != null) {
            updateExpression.append(", avg_response_latency = :arl, response_latencies = :rls");
            expressionValues.put(":arl", AttributeValue.builder().n(String.valueOf(newAvgResponseLatency)).build());
            expressionValues.put(":rls", AttributeValue.builder().s(objectMapper.writeValueAsString(allResponseLatencies)).build());
        }

        UpdateItemRequest request = UpdateItemRequest.builder()
            .tableName(tableName)
            .key(key)
            .updateExpression(updateExpression.toString())
            .expressionAttributeValues(expressionValues)
            .build();

        dynamoDbClient.updateItem(request);
    }

    private DailyStatistics parseStatistics(Map<String, AttributeValue> item) {
        DailyStatistics stats = new DailyStatistics();

        stats.setStudentEmail(getStringValue(item, "student_email"));
        stats.setDate(getStringValue(item, "date"));

        if (item.containsKey("total_recording_time")) {
            stats.setTotalRecordingTime(Long.parseLong(item.get("total_recording_time").n()));
        }
        if (item.containsKey("total_speaking_time")) {
            stats.setTotalSpeakingTime(Long.parseLong(item.get("total_speaking_time").n()));
        }
        if (item.containsKey("sessions_count")) {
            stats.setSessionsCount(Integer.parseInt(item.get("sessions_count").n()));
        }
        if (item.containsKey("practice_count")) {
            stats.setPracticeCount(Integer.parseInt(item.get("practice_count").n()));
        }
        if (item.containsKey("chat_turns_count")) {
            stats.setChatTurnsCount(Integer.parseInt(item.get("chat_turns_count").n()));
        }
        if (item.containsKey("avg_pace_ratio")) {
            stats.setAvgPaceRatio(Double.parseDouble(item.get("avg_pace_ratio").n()));
        }
        if (item.containsKey("avg_response_latency")) {
            stats.setAvgResponseLatency(Double.parseDouble(item.get("avg_response_latency").n()));
        }
        if (item.containsKey("avg_net_speaking_density")) {
            stats.setAvgNetSpeakingDensity(Double.parseDouble(item.get("avg_net_speaking_density").n()));
        }

        // JSON 리스트 파싱
        if (item.containsKey("pace_ratios")) {
            try {
                String json = item.get("pace_ratios").s();
                List<Double> ratios = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
                stats.setPaceRatios(ratios);
            } catch (JsonProcessingException e) {
                stats.setPaceRatios(new ArrayList<>());
            }
        }

        if (item.containsKey("response_latencies")) {
            try {
                String json = item.get("response_latencies").s();
                List<Long> latencies = objectMapper.readValue(json, new TypeReference<List<Long>>() {});
                stats.setResponseLatencies(latencies);
            } catch (JsonProcessingException e) {
                stats.setResponseLatencies(new ArrayList<>());
            }
        }

        // 새 필드: avg_response_quality
        if (item.containsKey("avg_response_quality")) {
            stats.setAvgResponseQuality(Double.parseDouble(item.get("avg_response_quality").n()));
        }

        // count 필드
        if (item.containsKey("pace_ratio_count")) {
            stats.setPaceRatioCount(Integer.parseInt(item.get("pace_ratio_count").n()));
        }
        if (item.containsKey("response_latency_count")) {
            stats.setResponseLatencyCount(Integer.parseInt(item.get("response_latency_count").n()));
        }
        if (item.containsKey("response_quality_count")) {
            stats.setResponseQualityCount(Integer.parseInt(item.get("response_quality_count").n()));
        }

        // 새 필드: response_qualities (JSON 리스트)
        if (item.containsKey("response_qualities")) {
            try {
                String json = item.get("response_qualities").s();
                List<ResponseQuality> qualities = objectMapper.readValue(json,
                    new TypeReference<List<ResponseQuality>>() {});
                stats.setResponseQualities(qualities);
            } catch (JsonProcessingException e) {
                stats.setResponseQualities(new ArrayList<>());
            }
        }

        return stats;
    }

    private DailyStatistics createEmptyStatistics(String studentEmail, String date) {
        DailyStatistics stats = new DailyStatistics();
        stats.setStudentEmail(studentEmail);
        stats.setDate(date);
        stats.setTotalRecordingTime(0L);
        stats.setTotalSpeakingTime(0L);
        stats.setSessionsCount(0);
        stats.setPracticeCount(0);
        stats.setChatTurnsCount(0);
        stats.setAvgPaceRatio(0.0);
        stats.setAvgResponseLatency(0.0);
        stats.setAvgNetSpeakingDensity(0.0);
        stats.setAvgResponseQuality(0.0);
        stats.setPaceRatios(new ArrayList<>());
        stats.setResponseLatencies(new ArrayList<>());
        stats.setResponseQualities(new ArrayList<>());
        stats.setPaceRatioCount(0);
        stats.setResponseLatencyCount(0);
        stats.setResponseQualityCount(0);
        return stats;
    }

    private String getStringValue(Map<String, AttributeValue> item, String key) {
        if (item.containsKey(key) && item.get(key).s() != null) {
            return item.get(key).s();
        }
        return null;
    }
}
