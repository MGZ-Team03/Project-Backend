package com.speaktracker.tutor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 튜터 피드백 Lambda 핸들러
 * 
 * 주요 기능:
 * 1. 피드백 메시지 수신 및 검증
 * 2. DynamoDB에 피드백 저장
 * 3. WebSocket을 통해 학생에게 피드백 전송
 */
public class TutorFeedbackHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String FEEDBACK_TABLE = System.getenv("FEEDBACK_TABLE");
    private static final String CONNECTIONS_TABLE = System.getenv("CONNECTIONS_TABLE");
    private static final String TUTOR_STUDENTS_TABLE = System.getenv("TUTOR_STUDENTS_TABLE");
    private static final String USERS_TABLE = System.getenv("USERS_TABLE");
    private static final String WEBSOCKET_ENDPOINT = System.getenv("WEBSOCKET_ENDPOINT");
    private static final String FEEDBACK_QUEUE_URL = System.getenv("FEEDBACK_QUEUE_URL");
    
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final Gson gson;
    private final ObjectMapper objectMapper;

    public TutorFeedbackHandler() {
        this.dynamoDbClient = DynamoDbClient.builder().build();
        this.sqsClient = SqsClient.builder().build();
        this.gson = new Gson();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Lambda 핸들러 메인 메서드
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        context.getLogger().log("📩 Received request: " + input.getPath() + " " + input.getHttpMethod());

        try {
            String path = input.getPath();
            String method = input.getHttpMethod();
            
            // GET /api/tutor/students - 담당 학생 목록 조회
            if ("GET".equals(method) && path.equals("/api/tutor/students")) {
                return handleGetStudents(input, context);
            }
            
            // POST /api/tutor/feedback
            if ("POST".equals(method) && path.contains("/feedback")) {
                return handlePostFeedback(input, context);
            }

            // GET /api/tutor/feedback
            if ("GET".equals(method) && path.contains("/feedback")) {
                return handleGetFeedback(input, context);
            }

            return createResponse(404, createErrorResponse("Endpoint not found"));

        } catch (Exception e) {
            context.getLogger().log("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, createErrorResponse("Internal server error: " + e.getMessage()));
        }
    }

    /**
     * GET 담당 학생 목록 조회
     */
    private APIGatewayProxyResponseEvent handleGetStudents(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Cognito에서 튜터 이메일 추출
            String tutorEmail = extractUserEmail(input, context);
            if (tutorEmail == null) {
                return createResponse(401, createErrorResponse("Unauthorized"));
            }
            
            context.getLogger().log("📚 학생 목록 조회 - 튜터: " + tutorEmail);
            
            // tutor-students 테이블에서 해당 튜터의 학생 조회
            QueryResponse queryResponse = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(TUTOR_STUDENTS_TABLE)
                    .keyConditionExpression("tutor_email = :tutorEmail")
                    .expressionAttributeValues(Map.of(
                            ":tutorEmail", AttributeValue.builder().s(tutorEmail).build()
                    ))
                    .build());
            
            List<Map<String, Object>> students = new ArrayList<>();
            
            for (Map<String, AttributeValue> item : queryResponse.items()) {
                String studentEmail = item.get("student_email").s();
                String assignedAt = item.containsKey("assigned_at") ? item.get("assigned_at").s() : null;
                String status = item.containsKey("status") ? item.get("status").s() : "active";
                
                // 학생 정보 조회
                String studentName = getStudentName(studentEmail, context);
                
                Map<String, Object> student = new HashMap<>();
                student.put("studentEmail", studentEmail);
                student.put("studentName", studentName);
                student.put("assignedAt", assignedAt);
                student.put("status", status);
                
                students.add(student);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", Map.of("students", students));
            
            context.getLogger().log("✅ 학생 목록 조회 완료: " + students.size() + "명");
            return createResponse(200, gson.toJson(result));
            
        } catch (Exception e) {
            context.getLogger().log("❌ 학생 목록 조회 실패: " + e.getMessage());
            return createResponse(500, createErrorResponse("Failed to get students: " + e.getMessage()));
        }
    }
    
    /**
     * 학생 이름 조회
     */
    private String getStudentName(String studentEmail, Context context) {
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(USERS_TABLE)
                    .key(Map.of("email", AttributeValue.builder().s(studentEmail).build()))
                    .build());
            
            if (response.hasItem() && response.item().containsKey("name")) {
                return response.item().get("name").s();
            }
            return studentEmail.split("@")[0]; // 이름이 없으면 이메일 앞부분 사용
        } catch (Exception e) {
            context.getLogger().log("⚠️ 학생 이름 조회 실패: " + studentEmail);
            return studentEmail.split("@")[0];
        }
    }
    
    /**
     * Cognito에서 사용자 이메일 추출
     */
    private String extractUserEmail(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, Object> authorizer = input.getRequestContext().getAuthorizer();
            if (authorizer != null && authorizer.containsKey("claims")) {
                @SuppressWarnings("unchecked")
                Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
                return claims.get("email");
            }
        } catch (Exception e) {
            context.getLogger().log("⚠️ 사용자 이메일 추출 실패: " + e.getMessage());
        }
        return null;
    }

    /**
     * POST 피드백 전송 처리
     */
    private APIGatewayProxyResponseEvent handlePostFeedback(APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 본문 파싱
            Map<String, Object> requestBody = gson.fromJson(input.getBody(), Map.class);
            
            // 피드백 처리
            Map<String, Object> result = processFeedback(requestBody, context);
            
            return createResponse(200, gson.toJson(result));

        } catch (JsonSyntaxException e) {
            context.getLogger().log("❌ Invalid JSON: " + e.getMessage());
            return createResponse(400, createErrorResponse("Invalid JSON format"));
        } catch (IllegalArgumentException e) {
            context.getLogger().log("❌ Validation error: " + e.getMessage());
            return createResponse(400, createErrorResponse(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("❌ Processing error: " + e.getMessage());
            return createResponse(500, createErrorResponse("Failed to process feedback"));
        }
    }

    /**
     * GET 피드백 조회 처리
     */
    private APIGatewayProxyResponseEvent handleGetFeedback(APIGatewayProxyRequestEvent input, Context context) {
        try {
            Map<String, String> queryParams = input.getQueryStringParameters();
            if (queryParams == null) {
                return createResponse(400, createErrorResponse("Query parameters required"));
            }

            String studentEmail = queryParams.get("student_email");
            if (studentEmail == null || studentEmail.isEmpty()) {
                return createResponse(400, createErrorResponse("student_email parameter is required"));
            }

            int limit = 50;
            if (queryParams.containsKey("limit")) {
                try {
                    limit = Integer.parseInt(queryParams.get("limit"));
                } catch (NumberFormatException e) {
                    return createResponse(400, createErrorResponse("Invalid limit parameter"));
                }
            }

            Map<String, Object> result = getFeedbackHistory(studentEmail, limit, context);
            return createResponse(200, gson.toJson(result));

        } catch (Exception e) {
            context.getLogger().log("❌ Query error: " + e.getMessage());
            return createResponse(500, createErrorResponse("Failed to retrieve feedback"));
        }
    }

    /**
     * 피드백 메시지 처리
     */
    private Map<String, Object> processFeedback(Map<String, Object> requestBody, Context context) {
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
            String timestamp = getCurrentTimestamp();
            String feedbackId = UUID.randomUUID().toString();

            // 3. WebSocket을 통해 학생에게 즉시 전송 (실시간성 보장)
            boolean websocketSent = sendToStudentViaWebSocket(
                studentEmail, 
                tutorEmail, 
                messageText, 
                messageType, 
                audioUrl, 
                timestamp,
                context
            );

            // 4. SQS에 메시지 큐잉 (비동기 DB 저장)
            sendToSQS(
                feedbackId,
                tutorEmail,
                studentEmail,
                sessionId,
                messageText,
                messageType,
                audioUrl,
                timestamp,
                websocketSent,
                context
            );

            // 5. 즉시 응답 반환
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message_id", feedbackId);
            response.put("timestamp", timestamp);
            response.put("websocket_sent", websocketSent);

            context.getLogger().log("✅ Feedback processed - WebSocket: " + websocketSent + ", SQS queued: " + feedbackId);
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
     * SQS에 피드백 메시지 전송
     */
    private void sendToSQS(
            String feedbackId,
            String tutorEmail,
            String studentEmail,
            String sessionId,
            String messageText,
            String messageType,
            String audioUrl,
            String timestamp,
            boolean websocketSent,
            Context context) {

        try {
            // FeedbackMessage 객체 생성
            FeedbackMessage feedbackMessage = new FeedbackMessage();
            feedbackMessage.setFeedbackId(feedbackId);
            feedbackMessage.setTutorEmail(tutorEmail);
            feedbackMessage.setStudentEmail(studentEmail);
            feedbackMessage.setSessionId(sessionId);
            feedbackMessage.setMessage(messageText);
            feedbackMessage.setMessageType(messageType);
            feedbackMessage.setTimestamp(timestamp);
            feedbackMessage.setWebsocketSent(websocketSent);
            
            if (audioUrl != null && !audioUrl.isEmpty()) {
                feedbackMessage.setAudioUrl(audioUrl);
            }

            // JSON 직렬화
            String messageBody = objectMapper.writeValueAsString(feedbackMessage);

            // SQS로 전송
            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(FEEDBACK_QUEUE_URL)
                    .messageBody(messageBody)
                    .build();

            SendMessageResponse response = sqsClient.sendMessage(request);
            
            context.getLogger().log("✅ Message sent to SQS - MessageId: " + response.messageId() + ", FeedbackId: " + feedbackId);

        } catch (Exception e) {
            context.getLogger().log("❌ SQS send error: " + e.getMessage());
            // SQS 전송 실패는 치명적 오류로 처리하지 않음 (WebSocket은 이미 전송됨)
            // 필요시 재시도 로직 추가 가능
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
     * 피드백 히스토리 조회
     */
    private Map<String, Object> getFeedbackHistory(String studentEmail, int limit, Context context) {
        try {
            Map<String, AttributeValue> keyCondition = new HashMap<>();
            keyCondition.put(":email", AttributeValue.builder().s(studentEmail).build());

            QueryRequest request = QueryRequest.builder()
                    .tableName(FEEDBACK_TABLE)
                    .indexName("student_email-timestamp-index")
                    .keyConditionExpression("student_email = :email")
                    .expressionAttributeValues(keyCondition)
                    .limit(limit)
                    .scanIndexForward(false)  // 최신 순 정렬
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            Map<String, Object> result = new HashMap<>();
            result.put("messages", response.items());
            result.put("count", response.count());
            
            if (response.lastEvaluatedKey() != null && !response.lastEvaluatedKey().isEmpty()) {
                result.put("lastEvaluatedKey", response.lastEvaluatedKey());
            }

            context.getLogger().log("✅ Retrieved " + response.count() + " messages for: " + studentEmail);
            return result;

        } catch (Exception e) {
            context.getLogger().log("❌ Query error: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve feedback history", e);
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
                    .tableName(CONNECTIONS_TABLE)
                    .indexName("user_email-index")
                    .keyConditionExpression("user_email = :email")
                    .expressionAttributeValues(keyCondition)
                    .build();

            QueryResponse response = dynamoDbClient.query(request);

            if (response.items().isEmpty()) {
                return null;
            }

            // 여러 연결이 있을 경우 connected_at 기준으로 최신 연결 선택
            return response.items().stream()
                .filter(item -> item.containsKey("connected_at"))
                .max((a, b) -> {
                    String timeA = a.get("connected_at").s();
                    String timeB = b.get("connected_at").s();
                    return timeA.compareTo(timeB);
                })
                .map(item -> item.get("connection_id").s())
                .orElse(response.items().get(0).get("connection_id").s());

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

    /**
     * HTTP 응답 생성
     */
    private APIGatewayProxyResponseEvent createResponse(int statusCode, String body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(body);
    }

    /**
     * 에러 응답 생성
     */
    private String createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        return gson.toJson(error);
    }
}
