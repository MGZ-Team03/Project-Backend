package sentences.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.ConversationMessage;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

public class ConversationRepository {

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
     * 대화 저장 (새 대화 또는 업데이트)
     */
    public void saveConversation(String studentEmail, String conversationId, String topic,
                                 String difficulty, String situation, String role,
                                 List<ConversationMessage> messages, String timestamp) {
        try {
            String messagesJson = objectMapper.writeValueAsString(messages);
            long ttl = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond(); // 30일 후

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
        return parseConversationData(item);
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
