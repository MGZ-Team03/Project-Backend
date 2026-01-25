package com.speaktracker.tutorRegister;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.tutorRegister.models.ApiResponse;
import com.speaktracker.tutorRegister.services.TutorRegisterService;
import com.speaktracker.tutorRegister.dto.*;
import static com.speaktracker.tutorRegister.exceptions.TutorRegisterExceptions.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 튜터 등록 기능 Lambda Handler
 */
public class TutorRegisterHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TutorRegisterService tutorRegisterService;
    private final ObjectMapper objectMapper;

    public TutorRegisterHandler() {
        this.tutorRegisterService = new TutorRegisterService();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String path = input.getPath();
        String method = input.getHttpMethod();
        
        context.getLogger().log("Request: " + method + " " + path);

        try {
            // Cognito에서 사용자 이메일 추출
            String userEmail = getUserEmailFromCognito(input, context);
            context.getLogger().log("User email: " + userEmail);
            
            // DynamoDB에서 role 조회
            String userRole = getUserRole(userEmail, context);
            context.getLogger().log("User role: " + userRole);
            
            // 라우팅
            if (path.equals("/api/tutors") && method.equals("GET")) {
                requireStudent(userRole);
                return handleGetTutors(userEmail);
            } 
            else if (path.matches("/api/tutors/.+/request") && method.equals("POST")) {
                requireStudent(userRole);
                String tutorEmail = extractTutorEmail(path);
                return handleCreateRequest(userEmail, tutorEmail, input.getBody());
            }
            else if (path.equals("/api/my/tutor-requests") && method.equals("GET")) {
                requireStudent(userRole);
                String status = input.getQueryStringParameters() != null 
                        ? input.getQueryStringParameters().getOrDefault("status", "all")
                        : "all";
                return handleGetMyRequests(userEmail, status);
            }
            else if (path.matches("/api/my/tutor-requests/.+") && method.equals("DELETE")) {
                requireStudent(userRole);
                String requestId = extractRequestId(path);
                return handleCancelRequest(userEmail, requestId);
            }
            else if (path.equals("/api/tutor/requests") && method.equals("GET")) {
                requireTutor(userRole);
                String status = input.getQueryStringParameters() != null 
                        ? input.getQueryStringParameters().getOrDefault("status", "pending")
                        : "pending";
                return handleGetTutorRequests(userEmail, status);
            }
            else if (path.matches("/api/tutors/requests/.+/approve") && method.equals("POST")) {
                requireTutor(userRole);
                String requestId = extractRequestIdFromApprove(path);
                return handleApproveRequest(userEmail, requestId);
            }
            else if (path.matches("/api/tutors/requests/.+/reject") && method.equals("POST")) {
                requireTutor(userRole);
                String requestId = extractRequestIdFromReject(path);
                return handleRejectRequest(userEmail, requestId, input.getBody());
            }
            // 알림 관련 엔드포인트
            else if (path.equals("/api/notifications") && method.equals("GET")) {
                // 학생과 튜터 모두 접근 가능
                Boolean isReadFilter = null;
                if (input.getQueryStringParameters() != null && input.getQueryStringParameters().containsKey("is_read")) {
                    isReadFilter = Boolean.parseBoolean(input.getQueryStringParameters().get("is_read"));
                }
                return handleGetNotifications(userEmail, isReadFilter);
            }
            else if (path.matches("/api/notifications/.+") && method.equals("PUT")) {
                // 학생과 튜터 모두 접근 가능
                String notificationIdTimestamp = extractNotificationId(path);
                return handleMarkNotificationAsRead(userEmail, notificationIdTimestamp);
            }
            else {
                return createResponse(404, ApiResponse.error("NOT_FOUND", "Endpoint not found"));
            }

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return handleError(e);
        }
    }

    // ===== API Handlers =====

    private APIGatewayProxyResponseEvent handleGetTutors(String studentEmail) {
        TutorListResponseDto tutors = tutorRegisterService.getTutors(studentEmail);
        return createResponse(200, ApiResponse.success(tutors));
    }

    private APIGatewayProxyResponseEvent handleCreateRequest(String studentEmail, String tutorEmail, String body) {
        try {
            Map<String, String> requestBody = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
            String message = requestBody != null ? requestBody.get("message") : null;
            
            RequestResponseDto result = tutorRegisterService.createTutorRequest(studentEmail, tutorEmail, message);
            return createResponse(201, ApiResponse.success(result));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse request body", e);
        }
    }

    private APIGatewayProxyResponseEvent handleGetMyRequests(String studentEmail, String status) {
        StudentRequestListResponseDto requests = tutorRegisterService.getMyRequests(studentEmail, status);
        return createResponse(200, ApiResponse.success(requests));
    }

    private APIGatewayProxyResponseEvent handleCancelRequest(String studentEmail, String requestId) {
        RequestResponseDto result = tutorRegisterService.cancelRequest(studentEmail, requestId);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleGetTutorRequests(String tutorEmail, String status) {
        TutorRequestListResponseDto result = tutorRegisterService.getTutorRequests(tutorEmail, status);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleApproveRequest(String tutorEmail, String requestId) {
        RequestResponseDto result = tutorRegisterService.approveRequest(tutorEmail, requestId);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleRejectRequest(String tutorEmail, String requestId, String body) {
        try {
            Map<String, String> requestBody = objectMapper.readValue(body, new TypeReference<Map<String, String>>() {});
            String reason = requestBody != null ? requestBody.get("reason") : null;
            
            RequestResponseDto result = tutorRegisterService.rejectRequest(tutorEmail, requestId, reason);
            return createResponse(200, ApiResponse.success(result));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse request body", e);
        }
    }

    private APIGatewayProxyResponseEvent handleGetNotifications(String userEmail, Boolean isReadFilter) {
        NotificationListResponseDto result = tutorRegisterService.getNotifications(userEmail, isReadFilter);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleMarkNotificationAsRead(String userEmail, String notificationIdTimestamp) {
        tutorRegisterService.markNotificationAsRead(userEmail, notificationIdTimestamp);
        Map<String, String> response = new HashMap<>();
        response.put("message", "알림이 읽음 처리되었습니다.");
        return createResponse(200, ApiResponse.success(response));
    }

    // ===== Helper Methods =====

    private String getUserEmailFromCognito(APIGatewayProxyRequestEvent input, Context context) {
        // Cognito Authorizer에서 사용자 정보 추출
        try {
            if (input == null || input.getRequestContext() == null) {
                context.getLogger().log("Missing requestContext");
                return "test@example.com";
            }

            Map<String, Object> authorizer = input.getRequestContext().getAuthorizer();
            if (authorizer == null) {
                context.getLogger().log("Missing authorizer");
                return "test@example.com";
            }

            Object claimsObj = authorizer.get("claims");
            if (!(claimsObj instanceof Map)) {
                context.getLogger().log("Missing claims in authorizer");
                return "test@example.com";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) claimsObj;
            
            Object emailObj = claims.get("email");
            if (emailObj != null) {
                return emailObj.toString();
            }
            
            context.getLogger().log("Email not found in claims");
        } catch (Exception e) {
            context.getLogger().log("Error extracting email from Cognito: " + e.getMessage());
        }
        
        // 테스트를 위한 기본값
        context.getLogger().log("Using default test email");
        return "test@example.com";
    }

    private String getUserRole(String userEmail, Context context) {
        // DynamoDB users 테이블에서 role 조회
        try {
            com.speaktracker.tutorRegister.models.User user = tutorRegisterService.getDynamoDBHelper().getUserByEmail(userEmail);
            if (user != null && user.getRole() != null) {
                return user.getRole();
            }
            context.getLogger().log("User not found or role is null for email: " + userEmail);
        } catch (Exception e) {
            context.getLogger().log("Error getting user role: " + e.getMessage());
            e.printStackTrace();
        }
        // 기본값: student
        return "student";
    }

    private void requireStudent(String role) {
        if (!"student".equals(role)) {
            throw new RuntimeException("FORBIDDEN_STUDENT_ONLY");
        }
    }

    private void requireTutor(String role) {
        if (!"tutor".equals(role)) {
            throw new RuntimeException("FORBIDDEN_TUTOR_ONLY");
        }
    }

    private String extractTutorEmail(String path) {
        // /api/tutors/{email}/request -> email 추출
        String[] parts = path.split("/");
        return parts.length > 3 ? parts[3] : null;
    }

    private String extractRequestId(String path) {
        // /api/my/tutor-requests/{id} -> id 추출
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : null;
    }

    private String extractRequestIdFromApprove(String path) {
        // /api/tutors/requests/{id}/approve -> id 추출
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : null;
    }

    private String extractRequestIdFromReject(String path) {
        // /api/tutors/requests/{id}/reject -> id 추출
        String[] parts = path.split("/");
        return parts.length > 4 ? parts[4] : null;
    }

    private String extractNotificationId(String path) {
        // /api/notifications/{id} -> id 추출
        String[] parts = path.split("/");
        String id = parts.length > 3 ? parts[3] : null;
        // URL 디코딩 (예: %23 -> #)
        if (id != null) {
            id = URLDecoder.decode(id, StandardCharsets.UTF_8);
        }
        return id;
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, ApiResponse<?> body) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
            headers.put("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    private APIGatewayProxyResponseEvent handleError(Exception e) {
        int statusCode;
        String errorCode;
        String message;

        // 커스텀 예외 처리
        if (e instanceof TutorRegisterException) {
            TutorRegisterException tre = (TutorRegisterException) e;
            errorCode = tre.getErrorCode();
            message = tre.getMessage();

            // HTTP 상태 코드 매핑
            switch (errorCode) {
                case "TUTOR_NOT_FOUND":
                case "REQUEST_NOT_FOUND":
                    statusCode = 404;
                    break;
                case "UNAUTHORIZED":
                    statusCode = 403;
                    break;
                case "DUPLICATE_REQUEST":
                case "CAPACITY_FULL":
                case "TUTOR_NOT_ACCEPTING":
                case "ALREADY_REGISTERED":
                case "REQUEST_ALREADY_PROCESSED":
                case "CANNOT_CANCEL":
                    statusCode = 400;
                    break;
                default:
                    statusCode = 500;
                    break;
            }
        }
        // RuntimeException (FORBIDDEN 처리)
        else if (e instanceof RuntimeException) {
            String runtimeMessage = e.getMessage();
            if ("FORBIDDEN_STUDENT_ONLY".equals(runtimeMessage)) {
                statusCode = 403;
                errorCode = "FORBIDDEN_STUDENT_ONLY";
                message = "학생만 접근할 수 있는 API입니다.";
            } else if ("FORBIDDEN_TUTOR_ONLY".equals(runtimeMessage)) {
                statusCode = 403;
                errorCode = "FORBIDDEN_TUTOR_ONLY";
                message = "튜터만 접근할 수 있는 API입니다.";
            } else {
                statusCode = 500;
                errorCode = "INTERNAL_ERROR";
                message = "서버 오류가 발생했습니다: " + e.getMessage();
            }
        }
        // 기타 예외
        else {
            statusCode = 500;
            errorCode = "INTERNAL_ERROR";
            message = "서버 오류가 발생했습니다: " + e.getMessage();
        }

        return createResponse(statusCode, ApiResponse.error(errorCode, message));
    }
}
