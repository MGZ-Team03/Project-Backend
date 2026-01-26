package com.speaktracker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * SQS에서 피드백 메시지를 읽어 DynamoDB에 저장하는 Lambda Handler
 */
public class FeedbackPersistenceHandler implements RequestHandler<SQSEvent, Void> {
    
    private final DynamoDbClient dynamoDB;
    private final ObjectMapper objectMapper;
    private final String FEEDBACK_TABLE;
    
    public FeedbackPersistenceHandler() {
        this.dynamoDB = DynamoDbClient.create();
        this.objectMapper = new ObjectMapper();
        this.FEEDBACK_TABLE = System.getenv("FEEDBACK_TABLE");
    }
    
    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Processing " + event.getRecords().size() + " messages from SQS");
        
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                // 메시지 파싱
                FeedbackMessage feedback = objectMapper.readValue(
                    message.getBody(), 
                    FeedbackMessage.class
                );
                
                context.getLogger().log("Processing feedback_id: " + feedback.getFeedbackId());
                
                // DynamoDB에 저장
                saveToDynamoDB(feedback, context);
                
                context.getLogger().log("Successfully saved feedback: " + feedback.getFeedbackId());
                
            } catch (Exception e) {
                context.getLogger().log("ERROR: Failed to process message: " + 
                    message.getMessageId() + ", Error: " + e.getMessage());
                e.printStackTrace();
                // SQS가 자동으로 재시도하거나 DLQ로 이동
                throw new RuntimeException("Failed to process SQS message", e);
            }
        }
        
        return null;
    }
    
    private void saveToDynamoDB(FeedbackMessage feedback, Context context) {
        Map<String, AttributeValue> item = new HashMap<>();
        
        // composite_key 생성: tutor_email#student_email (PK)
        String compositeKey = feedback.getTutorEmail() + "#" + feedback.getStudentEmail();
        
        item.put("composite_key", AttributeValue.builder().s(compositeKey).build());
        item.put("feedback_id", AttributeValue.builder().s(feedback.getFeedbackId()).build());
        item.put("tutor_email", AttributeValue.builder().s(feedback.getTutorEmail()).build());
        item.put("student_email", AttributeValue.builder().s(feedback.getStudentEmail()).build());
        item.put("message", AttributeValue.builder().s(feedback.getMessage()).build());
        item.put("message_type", AttributeValue.builder().s(feedback.getMessageType()).build());
        item.put("session_id", AttributeValue.builder().s(feedback.getSessionId()).build());
        item.put("timestamp", AttributeValue.builder().s(feedback.getTimestamp()).build());
        item.put("websocket_sent", AttributeValue.builder().bool(feedback.isWebsocketSent()).build());
        
        // audio_url은 선택사항
        if (feedback.getAudioUrl() != null && !feedback.getAudioUrl().isEmpty()) {
            item.put("audio_url", AttributeValue.builder().s(feedback.getAudioUrl()).build());
        }
        
        PutItemRequest request = PutItemRequest.builder()
            .tableName(FEEDBACK_TABLE)
            .item(item)
            .build();
        
        try {
            dynamoDB.putItem(request);
            context.getLogger().log("DynamoDB PutItem successful for: " + feedback.getFeedbackId());
        } catch (Exception e) {
            context.getLogger().log("ERROR: DynamoDB PutItem failed: " + e.getMessage());
            throw e;
        }
    }
}
