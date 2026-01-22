package com.speaktracker.tutorRegister.helpers;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * Amazon SES 이메일 알림 헬퍼
 */
public class EmailHelper {
    private final SesClient sesClient;
    private final String senderEmail;
    private final String frontendUrl;

    public EmailHelper() {
        this.sesClient = SesClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        this.senderEmail = System.getenv("SES_SENDER_EMAIL");
        this.frontendUrl = System.getenv("FRONTEND_URL");
    }

    /**
     * 이메일 전송
     */
    private void sendEmail(String recipientEmail, String subject, String bodyHtml, String bodyText) {
        try {
            SendEmailRequest emailRequest = SendEmailRequest.builder()
                    .destination(Destination.builder()
                            .toAddresses(recipientEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder()
                                    .charset("UTF-8")
                                    .data(subject)
                                    .build())
                            .body(Body.builder()
                                    .html(Content.builder()
                                            .charset("UTF-8")
                                            .data(bodyHtml)
                                            .build())
                                    .text(Content.builder()
                                            .charset("UTF-8")
                                            .data(bodyText)
                                            .build())
                                    .build())
                            .build())
                    .source(senderEmail)
                    .build();

            sesClient.sendEmail(emailRequest);
            System.out.println("Email sent to: " + recipientEmail);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            // 이메일 전송 실패는 무시 (알림은 WebSocket으로도 전송됨)
        }
    }

    /**
     * 튜터에게 새 요청 알림 이메일
     */
    public void sendNewTutorRequestEmail(String tutorEmail, String tutorName, 
                                          String studentName, String message) {
        String subject = "[SpeakTracker] 새로운 학생 등록 요청";
        
        String bodyText = String.format(
                "안녕하세요, %s님\n\n" +
                "%s님이 튜터 등록 요청을 보냈습니다.\n\n" +
                "요청 메시지:\n\"%s\"\n\n" +
                "대시보드에서 요청을 확인하고 승인/거부할 수 있습니다.\n" +
                "→ %s/tutor/dashboard\n\n" +
                "감사합니다.\n" +
                "SpeakTracker 팀",
                tutorName, studentName, message != null ? message : "", frontendUrl
        );

        String bodyHtml = String.format(
                "<html><body>" +
                "<h2>안녕하세요, %s님</h2>" +
                "<p><strong>%s</strong>님이 튜터 등록 요청을 보냈습니다.</p>" +
                "<p><strong>요청 메시지:</strong></p>" +
                "<blockquote>%s</blockquote>" +
                "<p>대시보드에서 요청을 확인하고 승인/거부할 수 있습니다.</p>" +
                "<p><a href=\"%s/tutor/dashboard\" style=\"background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px;\">대시보드 확인하기</a></p>" +
                "<p>감사합니다.<br>SpeakTracker 팀</p>" +
                "</body></html>",
                tutorName, studentName, message != null ? message : "", frontendUrl
        );

        sendEmail(tutorEmail, subject, bodyHtml, bodyText);
    }

    /**
     * 학생에게 승인 알림 이메일
     */
    public void sendRequestApprovedEmail(String studentEmail, String studentName, String tutorName) {
        String subject = "[SpeakTracker] 튜터 등록 요청이 승인되었습니다";
        
        String bodyText = String.format(
                "안녕하세요, %s님\n\n" +
                "%s님이 등록 요청을 승인했습니다!\n\n" +
                "이제 %s님과 함께 학습을 시작할 수 있습니다.\n" +
                "→ %s/learning/start\n\n" +
                "감사합니다.\n" +
                "SpeakTracker 팀",
                studentName, tutorName, tutorName, frontendUrl
        );

        String bodyHtml = String.format(
                "<html><body>" +
                "<h2>안녕하세요, %s님</h2>" +
                "<p><strong>%s</strong>님이 등록 요청을 승인했습니다! 🎉</p>" +
                "<p>이제 %s님과 함께 학습을 시작할 수 있습니다.</p>" +
                "<p><a href=\"%s/learning/start\" style=\"background-color: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;\">학습 시작하기</a></p>" +
                "<p>감사합니다.<br>SpeakTracker 팀</p>" +
                "</body></html>",
                studentName, tutorName, tutorName, frontendUrl
        );

        sendEmail(studentEmail, subject, bodyHtml, bodyText);
    }

    /**
     * 학생에게 거부 알림 이메일
     */
    public void sendRequestRejectedEmail(String studentEmail, String studentName, 
                                          String tutorName, String rejectionReason) {
        String subject = "[SpeakTracker] 튜터 등록 요청이 거부되었습니다";
        
        String bodyText = String.format(
                "안녕하세요, %s님\n\n" +
                "%s님이 등록 요청을 거부했습니다.\n\n" +
                "거부 사유:\n\"%s\"\n\n" +
                "다른 튜터를 검색해보세요.\n" +
                "→ %s/tutors/search\n\n" +
                "감사합니다.\n" +
                "SpeakTracker 팀",
                studentName, tutorName, rejectionReason != null ? rejectionReason : "사유 없음", frontendUrl
        );

        String bodyHtml = String.format(
                "<html><body>" +
                "<h2>안녕하세요, %s님</h2>" +
                "<p><strong>%s</strong>님이 등록 요청을 거부했습니다.</p>" +
                "<p><strong>거부 사유:</strong></p>" +
                "<blockquote>%s</blockquote>" +
                "<p>다른 튜터를 검색해보세요.</p>" +
                "<p><a href=\"%s/tutors/search\" style=\"background-color: #2196F3; color: white; padding: 10px 20px; text-decoration: none; border-radius: 5px;\">튜터 검색하기</a></p>" +
                "<p>감사합니다.<br>SpeakTracker 팀</p>" +
                "</body></html>",
                studentName, tutorName, rejectionReason != null ? rejectionReason : "사유 없음", frontendUrl
        );

        sendEmail(studentEmail, subject, bodyHtml, bodyText);
    }
}
