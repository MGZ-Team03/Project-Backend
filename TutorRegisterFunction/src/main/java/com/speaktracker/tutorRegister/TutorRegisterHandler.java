package com.speaktracker.tutorRegister;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.speaktracker.tutorRegister.models.ApiResponse;
import com.speaktracker.tutorRegister.services.TutorRegisterService;
import com.speaktracker.tutorRegister.dto.*;
import static com.speaktracker.tutorRegister.exceptions.TutorRegisterExceptions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 튜터 등록 기능 Lambda Handler
 */
public class TutorRegisterHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final TutorRegisterService tutorRegisterService;
    private final Gson gson;

    public TutorRegisterHandler() {
        this.tutorRegisterService = new TutorRegisterService();
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        String path = input.getPath();
        String method = input.getHttpMethod();
        
        context.getLogger().log("Request: " + method + " " + path);

        try {
            // Cognito에서 사용자 이메일 추출
            String userEmail = getUserEmailFromCognito(input);
            // DynamoDB에서 role 조회
            String userRole = getUserRole(userEmail);
            
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
            else {
                return createResponse(404, ApiResponse.error("NOT_FOUND", "Endpoint not found"));
            }

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return handleError(e);
        }
    }

    // ===== API Handlers =====

    private APIGatewayProxyResponseEvent handleGetTutors(String studentEmail) {
        TutorListResponseDto tutors = tutorRegisterService.getTutors(studentEmail);
        return createResponse(200, ApiResponse.success(tutors));
    }

    private APIGatewayProxyResponseEvent handleCreateRequest(String studentEmail, String tutorEmail, String body) {
        Map<String, String> requestBody = gson.fromJson(body, Map.class);
        String message = requestBody != null ? requestBody.get("message") : null;
        
        RequestResponseDto result = tutorRegisterService.createTutorRequest(studentEmail, tutorEmail, message);
        return createResponse(201, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleGetMyRequests(String studentEmail, String status) {
        StudentRequestListResponseDto requests = tutorRegisterService.getMyRequests(studentEmail, status);
        return createResponse(200, ApiResponse.success(requests));
    }

    private APIGatewayProxyResponseEvent handleCancelRequest(String studentEmail, String requestId) {
        // requestId에서 createdAt 추출 (실제로는 요청 본문이나 쿼리에서 받아야 함)
        // 간단한 구현을 위해 여기서는 임시로 처리
        RequestResponseDto result = tutorRegisterService.cancelRequest(studentEmail, requestId, 0L);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleGetTutorRequests(String tutorEmail, String status) {
        TutorRequestListResponseDto result = tutorRegisterService.getTutorRequests(tutorEmail, status);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleApproveRequest(String tutorEmail, String requestId) {
        // requestId에서 createdAt 추출 필요
        RequestResponseDto result = tutorRegisterService.approveRequest(tutorEmail, requestId, 0L);
        return createResponse(200, ApiResponse.success(result));
    }

    private APIGatewayProxyResponseEvent handleRejectRequest(String tutorEmail, String requestId, String body) {
        Map<String, String> requestBody = gson.fromJson(body, Map.class);
        String reason = requestBody != null ? requestBody.get("reason") : null;
        
        RequestResponseDto result = tutorRegisterService.rejectRequest(tutorEmail, requestId, 0L, reason);
        return createResponse(200, ApiResponse.success(result));
    }

    // ===== Helper Methods =====

    private String getUserEmailFromCognito(APIGatewayProxyRequestEvent input) {
        // Cognito Authorizer에서 사용자 정보 추출
        Map<String, Object> authorizer = input.getRequestContext().getAuthorizer();
        if (authorizer != null && authorizer.containsKey("claims")) {
            Map<String, String> claims = (Map<String, String>) authorizer.get("claims");
            return claims.get("email");
        }
        // 테스트를 위한 기본값
        return "test@example.com";
    }

    private String getUserRole(String userEmail) {
        // DynamoDB users 테이블에서 role 조회
        try {
            com.speaktracker.tutorRegister.models.User user = tutorRegisterService.getDynamoDBHelper().getUserByEmail(userEmail);
            if (user != null && user.getRole() != null) {
                return user.getRole();
            }
        } catch (Exception e) {
            // 조회 실패 시 기본값 반환
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

    private APIGatewayProxyResponseEvent createResponse(int statusCode, ApiResponse<?> body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.put("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(body));
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
