package sentences.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.ConversationMessage;
import sentences.model.ConversationSummary;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

public class ConversationRepository {

    public static final long LONG_TTL_SECONDS = 30L * 24 * 60 * 60; // 30일

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ObjectMapper objectMapper;

    public ConversationRepository(String tableName) {
        this.dynamoDbClient = DynamoDbClient.create();
        this.tableName = tableName;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 대화 저장 (새 대화)
     */
    public String saveConversation(String studentEmail, String conversationId, String topic,
                                   String difficulty, String situation, String role,
                                   List<ConversationMessage> messages) {
        String timestamp = Instant.now().toString();
        saveConversation(studentEmail, conversationId, topic, difficulty, situation, role, messages, timestamp);
        return timestamp;
    }

    /**
     * 대화 저장 (새 대화) - TTL(epoch seconds) 지정
     */
    public String saveConversation(String studentEmail, String conversationId, String topic,
                                   String difficulty, String situation, String role,
                                   List<ConversationMessage> messages, long ttlEpochSeconds) {
        String timestamp = Instant.now().toString();
        saveConversation(studentEmail, conversationId, topic, difficulty, situation, role, messages, timestamp, ttlEpochSeconds);
        return timestamp;
    }

    /**
     * 대화 저장 (새 대화 또는 업데이트)
     */
    public void saveConversation(String studentEmail, String conversationId, String topic,
                                 String difficulty, String situation, String role,
                                 List<ConversationMessage> messages, String timestamp) {
        long ttlEpochSeconds = Instant.now().plusSeconds(LONG_TTL_SECONDS).getEpochSecond(); // 30일 후
        saveConversation(studentEmail, conversationId, topic, difficulty, situation, role, messages, timestamp, ttlEpochSeconds);
    }

    /**
     * 대화 저장 (새 대화 또는 업데이트) - TTL(epoch seconds) 지정
     */
    public void saveConversation(String studentEmail, String conversationId, String topic,
                                 String difficulty, String situation, String role,
                                 List<ConversationMessage> messages, String timestamp, long ttlEpochSeconds) {
        try {
            String messagesJson = objectMapper.writeValueAsString(messages);
            long ttl = ttlEpochSeconds > 0
                ? ttlEpochSeconds
                : Instant.now().plusSeconds(LONG_TTL_SECONDS).getEpochSecond();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("student_email", AttributeValue.builder().s(studentEmail).build());
            item.put("timestamp", AttributeValue.builder().s(timestamp).build());
            item.put("conversation_id", AttributeValue.builder().s(conversationId).build());
            item.put("topic", AttributeValue.builder().s(topic).build());
            item.put("difficulty", AttributeValue.builder().s(difficulty).build());
            item.put("situation", AttributeValue.builder().s(situation).build());
            item.put("role", AttributeValue.builder().s(role).build());
            item.put("messages", AttributeValue.builder().s(messagesJson).build());
            item.put("turn_count", AttributeValue.builder().n(String.valueOf(messages.size())).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

            PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

            dynamoDbClient.putItem(request);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize messages", e);
        }
    }

    /**
     * 학생별 대화 이력 조회 (최신순)
     */
    public List<ConversationSummary> getConversationsByStudent(String studentEmail, int limit) {
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":email", AttributeValue.builder().s(studentEmail).build());

        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("student_email = :email")
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false)  // 최신순 정렬 (timestamp DESC)
            .limit(limit)
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        List<ConversationSummary> summaries = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            if (isExpired(item)) {
                continue;
            }
            summaries.add(parseConversationSummary(item));
        }

        return summaries;
    }

    /**
     * 학생별 최근 대화 조회 (최신순, messages 포함)
     * - PK(student_email) 기준 Query로 최신 limit개를 가져온 뒤, 각 아이템의 messages JSON을 파싱합니다.
     * - 레벨 평가 등 “최근 대화 내용”이 필요한 기능에서 사용합니다.
     */
    public List<ConversationData> getRecentConversationsWithMessages(String studentEmail, int limit) {
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":email", AttributeValue.builder().s(studentEmail).build());

        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("student_email = :email")
            .expressionAttributeValues(expressionValues)
            .scanIndexForward(false) // 최신순 (timestamp DESC)
            .limit(limit)
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        List<ConversationData> conversations = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            if (isExpired(item)) {
                continue;
            }
            conversations.add(parseConversationData(item));
        }
        return conversations;
    }

    private ConversationSummary parseConversationSummary(Map<String, AttributeValue> item) {
        String conversationId = item.get("conversation_id").s();
        String timestamp = item.get("timestamp").s();
        String topic = item.get("topic").s();
        String difficulty = item.get("difficulty").s();
        Integer turnCount = item.containsKey("turn_count")
            ? Integer.parseInt(item.get("turn_count").n())
            : 0;

        // 첫 AI 메시지에서 미리보기 추출 (50자)
        String preview = "";
        try {
            String messagesJson = item.get("messages").s();
            List<ConversationMessage> messages = objectMapper.readValue(
                messagesJson, new TypeReference<List<ConversationMessage>>() {});

            if (!messages.isEmpty()) {
                String firstMessage = messages.get(0).getContent();
                preview = firstMessage.length() > 50
                    ? firstMessage.substring(0, 50) + "..."
                    : firstMessage;
            }
        } catch (JsonProcessingException e) {
            preview = "";
        }

        return new ConversationSummary(conversationId, timestamp, topic, difficulty, turnCount, preview);
    }

    /**
     * conversationId로 대화 조회 (GSI 사용)
     */
    public ConversationData getConversation(String conversationId) {
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":convId", AttributeValue.builder().s(conversationId).build());

        QueryRequest queryRequest = QueryRequest.builder()
            .tableName(tableName)
            .indexName("conversation_id-index")
            .keyConditionExpression("conversation_id = :convId")
            .expressionAttributeValues(expressionValues)
            .limit(1)
            .build();

        QueryResponse response = dynamoDbClient.query(queryRequest);

        if (response.items().isEmpty()) {
            return null;
        }

        Map<String, AttributeValue> item = response.items().get(0);
        if (isExpired(item)) {
            return null;
        }
        return parseConversationData(item);
    }

    private boolean isExpired(Map<String, AttributeValue> item) {
        try {
            if (item == null || !item.containsKey("ttl")) {
                return false;
            }
            String ttlRaw = item.get("ttl").n();
            if (ttlRaw == null || ttlRaw.isBlank()) {
                return false;
            }
            long ttlEpochSeconds = Long.parseLong(ttlRaw);
            long now = Instant.now().getEpochSecond();
            return ttlEpochSeconds <= now;
        } catch (Exception ignore) {
            return false;
        }
    }

    private ConversationData parseConversationData(Map<String, AttributeValue> item) {
        try {
            String studentEmail = item.get("student_email").s();
            String timestamp = item.get("timestamp").s();
            String conversationId = item.get("conversation_id").s();
            String topic = item.get("topic").s();
            String difficulty = item.get("difficulty").s();
            String situation = item.get("situation").s();
            String role = item.get("role").s();
            String messagesJson = item.get("messages").s();

            List<ConversationMessage> messages = objectMapper.readValue(
                messagesJson, new TypeReference<List<ConversationMessage>>() {});

            return new ConversationData(studentEmail, timestamp, conversationId, topic, difficulty,
                                       situation, role, messages);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse conversation data", e);
        }
    }

    /**
     * 대화 데이터 클래스
     */
    public static class ConversationData {
        private final String studentEmail;
        private final String timestamp;
        private final String conversationId;
        private final String topic;
        private final String difficulty;
        private final String situation;
        private final String role;
        private final List<ConversationMessage> messages;

        public ConversationData(String studentEmail, String timestamp, String conversationId, String topic,
                               String difficulty, String situation, String role,
                               List<ConversationMessage> messages) {
            this.studentEmail = studentEmail;
            this.timestamp = timestamp;
            this.conversationId = conversationId;
            this.topic = topic;
            this.difficulty = difficulty;
            this.situation = situation;
            this.role = role;
            this.messages = messages;
        }

        public String getStudentEmail() {
            return studentEmail;
        }

        public String getTimestamp() {
            return timestamp;
        }

        public String getConversationId() {
            return conversationId;
        }

        public String getTopic() {
            return topic;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public String getSituation() {
            return situation;
        }

        public String getRole() {
            return role;
        }

        public List<ConversationMessage> getMessages() {
            return messages;
        }
    }
}
