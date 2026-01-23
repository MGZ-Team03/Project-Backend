package com.speaktracker.studentstatus.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.speaktracker.studentstatus.dto.StudentStatusEventRequest;
import com.speaktracker.studentstatus.dto.StudentStatusRequest;
import com.speaktracker.studentstatus.dto.StudentStatusResponse;
import com.speaktracker.studentstatus.service.StudentStatusService;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;

@RequiredArgsConstructor
public class StudentStatusController {
    private final StudentStatusService service;
    private Gson gson = new Gson();

    /**
     * 메인 요청 핸들러 (POST 저장만!)
     */
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent event) {

        String httpMethod = event.getHttpMethod();
        String path = event.getPath();
        String body = event.getBody();

        getLogger().log("=== Controller 시작 ===");
        getLogger().log("📍 HTTP Method: " + httpMethod);
        getLogger().log("📍 Path: " + path);
        getLogger().log("📥 받은 데이터: " + body);

        // POST만 처리
        if ("POST".equals(httpMethod)) {
            return handlePostRequest(event);
        } else {
            return createMethodNotAllowedResponse();
        }
    }

    /**
     * POST 요청 처리 - 학생 상태 저장
     */
    private APIGatewayProxyResponseEvent handlePostRequest(
            APIGatewayProxyRequestEvent event) {

        getLogger().log("=== POST 요청 처리 (저장) ===");

        try {
            String body = event.getBody();

            if (body == null || body.isEmpty()) {
                return createErrorResponse(400, "Request body is empty");
            }

            // JSON → DTO 변환
            StudentStatusEventRequest request = gson.fromJson(body, StudentStatusEventRequest.class);

            // Service 호출 - 저장만!
            StudentStatusResponse result = service.saveStudentStatus(request);

            getLogger().log("✅ Controller: 저장 완료");

            // 성공 응답
            return createSuccessResponse(200, result);

        } catch (IllegalArgumentException e) {
            getLogger().log("⚠️ 유효성 검사 실패: " + e.getMessage());
            return createErrorResponse(400, e.getMessage());

        } catch (Exception e) {
            getLogger().log("❌ 서버 에러: " + e.getMessage());
            e.printStackTrace();
            return createErrorResponse(500, "Internal server error: " + e.getMessage());
        }
    }

    /**
     * OPTIONS 요청 처리 (CORS Preflight)
     */
    private APIGatewayProxyResponseEvent handleOptionsRequest() {
        Map<String, String> headers = createCorsHeaders();
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(headers)
                .withBody("");
    }

    /**
     * CORS 헤더 생성
     */
    private Map<String, String> createCorsHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type");
        return headers;
    }

    /**
     * 성공 응답 생성
     */
    private APIGatewayProxyResponseEvent createSuccessResponse(
            int statusCode,
            Object data) {

        Map<String, String> headers = createCorsHeaders();

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(data));
    }

    /**
     * 에러 응답 생성v
     */

    private APIGatewayProxyResponseEvent createErrorResponse(
            int statusCode,
            String message) {

        Map<String, String> headers = createCorsHeaders();
        Map<String, String> errorBody = new HashMap<>();
        errorBody.put("error", message);

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(errorBody));
    }

    private APIGatewayProxyResponseEvent createMethodNotAllowedResponse() {
        return createErrorResponse(405, "Method not allowed. Only POST is supported.");
    }

}