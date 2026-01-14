package tutor;

import com.amazonaws.services.lambda.runtime.Context;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 튜터 피드백 핸들러 클래스
 * 
 * 주요 기능:
 * 1. 피드백 메시지 수신 및 검증
 * 2. DynamoDB에 피드백 저장
 * 3. WebSocket을 통해 학생에게 피드백 전송
 */
public class TutorFeedbackHandler {

    private static final String FEEDBACK_TABLE = System.getenv("FEEDBACK_MESSAGES_TABLE");
    private static final String WEBSOCKET_TABLE = System.getenv("WEBSOCKET_CONNECTIONS_TABLE");
    private static final String WEBSOCKET_ENDPOINT = System.getenv("WEBSOCKET_ENDPOINT");
    
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    public TutorFeedbackHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.gson = new Gson();
    }

    /**
     * 피드백 메시지 처리 메인 메서드
     */
    public Map<String, Object> processFeedback(Map<String, Object> requestBody, Context context) {
        try {
            // 1. 요청 검증
            validateRequest(requestBody);
            
            // 2. 피드백 데이터 추출
            String tutorEmail = (String) requestBody.get("tutor_email");
            String studentEmail = (String) requestBody.get("student_email");
            String sessionId = (String) requestBody.getOrDefault("session_id", "default");
            String messageText = (String) requestBody.get("message");
            String messageType = (String) requestBody.getOrDefault("message_type", "text");
            String audioUrl = (String) requestBody.get("audio_url");

            // 3. DynamoDB에 저장
            String timestamp = saveFeedbackToDB(
                tutorEmail, 
                studentEmail, 
                sessionId, 
                messageText, 
                messageType, 
                audioUrl,
                context
            );

            // 4. WebSocket을 통해 학생에게 전송
            boolean sent = sendToStudentViaWebSocket(
                studentEmail, 
                tutorEmail, 
                messageText, 
                messageType, 
                audioUrl, 
                timestamp,
                context
            );

            // 5. 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message_id", generateMessageId(tutorEmail, studentEmail, sessionId, timestamp));
            response.put("timestamp", timestamp);
            response.put("websocket_sent", sent);

            return response;

        } catch (IllegalArgumentException e) {
            context.getLogger().log("❌ Validation error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            context.getLogger().log("❌ Error processing feedback: " + e.getMessage());
            throw new RuntimeException("Failed to process feedback", e);
        }
    }

    /**
     * 요청 검증
     */
    private void validateRequest(Map<String, Object> request) {
        if (request.get("tutor_email") == null || request.get("tutor_email").toString().isEmpty()) {
            throw new IllegalArgumentException("tutor_email is required");
        }
        if (request.get("student_email") == null || request.get("student_email").toString().isEmpty()) {
            throw new IllegalArgumentException("student_email is required");
        }
        if (request.get("message") == null || request.get("message").toString().isEmpty()) {
            throw new IllegalArgumentException("message is required");
        }

        String messageType = (String) request.getOrDefault("message_type", "text");
        if (!"text".equals(messageType) && !"tts".equals(messageType)) {
            throw new IllegalArgumentException("message_type must be 'text' or 'tts'");
        }

        // TTS인 경우 audio_url 필수
        if ("tts".equals(messageType) && 
            (request.get("audio_url") == null || request.get("audio_url").toString().isEmpty())) {
            throw new IllegalArgumentException("audio_url is required for TTS messages");
        }
    }

    /**
     * DynamoDB에 피드백 저장
     */
    private String saveFeedbackToDB(
            String tutorEmail,
            String studentEmail,
            String sessionId,
            String messageText,
            String messageType,
            String audioUrl,
            Context context) {

        String timestamp = getCurrentTimestamp();
        String compositeKey = String.format("%s#%s#%s", tutorEmail, studentEmail, sessionId);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("composite_key", AttributeValue.builder().s(compositeKey).build());
        item.put("timestamp", AttributeValue.builder().s(timestamp).build());
        item.put("tutor_email", AttributeValue.builder().s(tutorEmail).build());
        item.put("student_email", AttributeValue.builder().s(studentEmail).build());
        item.put("message_text", AttributeValue.builder().s(messageText).build());
        item.put("message_type", AttributeValue.builder().s(messageType).build());
        
        if (audioUrl != null && !audioUrl.isEmpty()) {
            item.put("audio_url", AttributeValue.builder().s(audioUrl).build());
        }

        // TTL: 30일 후 자동 삭제
        long ttl = Instant.now().plusSeconds(30 * 24 * 60 * 60).getEpochSecond();
        item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl)).build());

        try {
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(FEEDBACK_TABLE)
                    .item(item)
                    .build();

            dynamoDbClient.putItem(request);
            context.getLogger().log("✅ Feedback saved to DynamoDB: " + compositeKey);
            
            return timestamp;

        } catch (Exception e) {
            context.getLogger().log("❌ DynamoDB save error: " + e.getMessage());
            throw new RuntimeException("Failed to save feedback to DynamoDB", e);
        }
    }

    /**
     * WebSocket을 통해 학생에게 피드백 전송
     */
    private boolean sendToStudentViaWebSocket(
            String studentEmail,
            String tutorEmail,
            String messageText,
            String messageType,
            String audioUrl,
            String timestamp,
            Context context) {

        try {
            // 1. WebSocketConnectionsTable에서 학생의 connection_id 조회
            String connectionId = getStudentConnectionId(studentEmail, context);
            
            if (connectionId == null) {
                context.getLogger().log("⚠️ Student is offline: " + studentEmail);
                return false;
            }

            // 2. 피드백 메시지 생성
            Map<String, Object> feedbackMessage = new HashMap<>();
            feedbackMessage.put("type", "feedback");
            feedbackMessage.put("from", tutorEmail);
            feedbackMessage.put("message", messageText);
            feedbackMessage.put("messageType", messageType);
            if (audioUrl != null) {
                feedbackMessage.put("audioUrl", audioUrl);
            }
            feedbackMessage.put("timestamp", timestamp);

            // 3. WebSocket으로 전송
            ApiGatewayManagementApiClient apiClient = ApiGatewayManagementApiClient.builder()
                    .endpointOverride(URI.create(WEBSOCKET_ENDPOINT))
                    .build();

            String message = gson.toJson(feedbackMessage);
            
            PostToConnectionRequest request = PostToConnectionRequest.builder()
                    .connectionId(connectionId)
                    .data(SdkBytes.fromUtf8String(message))
                    .build();

            apiClient.postToConnection(request);
            context.getLogger().log("✅ Feedback sent via WebSocket to: " + studentEmail);
            
            return true;

        } catch (Exception e) {
            context.getLogger().log("❌ WebSocket send error: " + e.getMessage());
            return false;
        }
    }

    /**
     * WebSocketConnectionsTable에서 학생의 connection_id 조회
     */
    private String getStudentConnectionId(String studentEmail, Context context) {
        try {
            Map<String, AttributeValue> keyCondition = new HashMap<>();
            keyCondition.put(":email", AttributeValue.builder().s(studentEmail).build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(WEBSOCKET_TABLE)
                    .indexName("user_email-index")
                    .keyConditionExpression("user_email = :email")
                    .expressionAttributeValues(keyCondition)
                    .limit(1)
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            if (response.items().isEmpty()) {
                return null;
            }

            return response.items().get(0).get("connection_id").s();

        } catch (Exception e) {
            context.getLogger().log("❌ Error querying connection: " + e.getMessage());
            return null;
        }
    }

    /**
     * 현재 타임스탬프 생성 (ISO 8601)
     */
    private String getCurrentTimestamp() {
        return Instant.now()
                .atZone(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * 메시지 ID 생성
     */
    private String generateMessageId(String tutorEmail, String studentEmail, String sessionId, String timestamp) {
        return String.format("%s#%s#%s#%s", tutorEmail, studentEmail, sessionId, timestamp);
    }
}
