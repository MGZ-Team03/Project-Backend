package com.speaktracker.tutorRegister.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.*;

/**
 * SQS 알림 큐잉 헬퍼
 */
public class SQSHelper {
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SQSHelper() {
        this.sqsClient = SqsClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        this.queueUrl = System.getenv("NOTIFICATION_QUEUE_URL");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * SQS에 알림 메시지 전송
     */
    public void sendNotificationMessage(String userEmail, String type, String title, 
                                         String message, Map<String, Object> data, 
                                         List<String> sentVia) {
        try {
            Map<String, Object> notificationMessage = new HashMap<>();
            notificationMessage.put("notification_id", UUID.randomUUID().toString());
            notificationMessage.put("user_email", userEmail);
            notificationMessage.put("type", type);
            notificationMessage.put("title", title);
            notificationMessage.put("message", message);
            notificationMessage.put("data", data);
            notificationMessage.put("is_read", false);
            notificationMessage.put("sent_via", sentVia);
            notificationMessage.put("created_at", System.currentTimeMillis());

            String messageBody = objectMapper.writeValueAsString(notificationMessage);

            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());

            System.out.println("Sent notification to SQS for user: " + userEmail);
        } catch (Exception e) {
            System.err.println("Failed to send SQS message: " + e.getMessage());
            // SQS 전송 실패는 무시 (알림 이력 저장 실패는 치명적이지 않음)
        }
    }

    /**
     * 새 튜터 요청 알림 메시지
     */
    public void queueNewTutorRequestNotification(String tutorEmail, String tutorName, 
                                                  String studentEmail, String studentName, 
                                                  String message, String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("student_email", studentEmail);
        data.put("student_name", studentName);
        data.put("message", message);

        sendNotificationMessage(
                tutorEmail,
                "NEW_TUTOR_REQUEST",
                "새로운 학생 등록 요청",
                studentName + "님이 튜터 등록 요청을 보냈습니다.",
                data,
                Arrays.asList("websocket", "email")
        );
    }

    /**
     * 요청 승인 알림 메시지
     */
    public void queueRequestApprovedNotification(String studentEmail, String tutorName, String requestId) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("tutor_name", tutorName);

        sendNotificationMessage(
                studentEmail,
                "TUTOR_REQUEST_APPROVED",
                "튜터 등록 요청이 승인되었습니다",
                tutorName + "님이 등록 요청을 승인했습니다!",
                data,
                Arrays.asList("websocket", "email")
        );
    }

    /**
     * 요청 거부 알림 메시지
     */
    public void queueRequestRejectedNotification(String studentEmail, String tutorName, 
                                                  String requestId, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("request_id", requestId);
        data.put("tutor_name", tutorName);
        data.put("rejection_reason", reason);

        sendNotificationMessage(
                studentEmail,
                "TUTOR_REQUEST_REJECTED",
                "튜터 등록 요청이 거부되었습니다",
                tutorName + "님이 등록 요청을 거부했습니다.",
                data,
                Arrays.asList("websocket", "email")
        );
    }
}
