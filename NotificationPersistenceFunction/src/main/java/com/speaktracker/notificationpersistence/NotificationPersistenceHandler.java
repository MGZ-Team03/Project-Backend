package com.speaktracker.notificationpersistence;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * SQS에서 알림 메시지를 소비하여 DynamoDB에 저장하는 Lambda 함수
 */
public class NotificationPersistenceHandler implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient;
    private final String notificationsTable;
    private final Gson gson;

    public NotificationPersistenceHandler() {
        this.dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        this.notificationsTable = System.getenv("NOTIFICATIONS_TABLE");
        this.gson = new Gson();
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                context.getLogger().log("Processing SQS message: " + message.getMessageId());
                
                // 1. SQS 메시지 파싱
                Map<String, Object> notification = gson.fromJson(message.getBody(), Map.class);
                
                // 2. DynamoDB에 저장
                saveNotification(notification, context);
                
                context.getLogger().log("Successfully saved notification: " + notification.get("notification_id"));
            } catch (Exception e) {
                context.getLogger().log("Error processing message: " + e.getMessage());
                // 실패 시 SQS가 자동 재시도 또는 DLQ로 이동
                throw new RuntimeException("Failed to process SQS message", e);
            }
        }
        
        return null;
    }

    private void saveNotification(Map<String, Object> notification, Context context) {
        try {
            String notificationId = (String) notification.get("notification_id");
            String userEmail = (String) notification.get("user_email");
            String type = (String) notification.get("type");
            String title = (String) notification.get("title");
            String message = (String) notification.get("message");
            Boolean isRead = (Boolean) notification.get("is_read");
            
            // createdAt을 Double에서 Long으로 변환
            Object createdAtObj = notification.get("created_at");
            Long createdAt;
            if (createdAtObj instanceof Double) {
                createdAt = ((Double) createdAtObj).longValue();
            } else {
                createdAt = (Long) createdAtObj;
            }

            // TTL 설정 (30일 후 자동 삭제)
            long ttl = createdAt + TimeUnit.DAYS.toMillis(30);

            // DynamoDB 아이템 생성
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("user_email", AttributeValue.builder().s(userEmail).build());
            item.put("notification_id_timestamp", AttributeValue.builder()
                    .s(notificationId + "#" + createdAt).build());
            item.put("notification_id", AttributeValue.builder().s(notificationId).build());
            item.put("type", AttributeValue.builder().s(type).build());
            item.put("title", AttributeValue.builder().s(title).build());
            item.put("message", AttributeValue.builder().s(message).build());
            item.put("is_read", AttributeValue.builder().bool(isRead).build());
            item.put("is_read_created_at", AttributeValue.builder()
                    .s(isRead + "#" + createdAt).build());
            item.put("created_at", AttributeValue.builder().n(String.valueOf(createdAt)).build());
            item.put("ttl", AttributeValue.builder().n(String.valueOf(ttl / 1000)).build()); // TTL은 초 단위

            // data 필드 (Map)
            if (notification.containsKey("data")) {
                Map<String, Object> data = (Map<String, Object>) notification.get("data");
                Map<String, AttributeValue> dataAttributes = new HashMap<>();
                
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        dataAttributes.put(entry.getKey(), AttributeValue.builder().s((String) value).build());
                    } else if (value instanceof Number) {
                        dataAttributes.put(entry.getKey(), AttributeValue.builder().n(value.toString()).build());
                    }
                }
                
                if (!dataAttributes.isEmpty()) {
                    item.put("data", AttributeValue.builder().m(dataAttributes).build());
                }
            }

            // sent_via 필드 (List)
            if (notification.containsKey("sent_via")) {
                List<String> sentVia = (List<String>) notification.get("sent_via");
                item.put("sent_via", AttributeValue.builder().ss(sentVia).build());
            }

            // DynamoDB에 저장
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(notificationsTable)
                    .item(item)
                    .build());

        } catch (Exception e) {
            context.getLogger().log("Failed to save notification: " + e.getMessage());
            throw new RuntimeException("Failed to save notification to DynamoDB", e);
        }
    }
}
