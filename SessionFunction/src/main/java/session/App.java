package session;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import session.model.*;
import session.repository.LearningSessionRepository;

import java.util.*;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final LearningSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public App() {
        String tableName = System.getenv("SESSIONS_TABLE");
        this.sessionRepository = new LearningSessionRepository(tableName);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        String path = input.getPath();
        String method = input.getHttpMethod();

        try {
            // POST /api/sessions/start
            if ("POST".equals(method) && path.equals("/api/sessions/start")) {
                return handleSessionStart(input, context);
            }

            // POST /api/sessions/end
            if ("POST".equals(method) && path.equals("/api/sessions/end")) {
                return handleSessionEnd(input, context);
            }

            // GET /api/sessions/history
            if ("GET".equals(method) && path.equals("/api/sessions/history")) {
                return handleSessionHistory(input, context);
            }

            return createResponse(404, Map.of("success", false, "error", "Not Found"));

        } catch (IllegalArgumentException e) {
            return createResponse(400, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, Map.of("success", false, "error", "Internal server error"));
        }
    }

    private APIGatewayProxyResponseEvent handleSessionStart(APIGatewayProxyRequestEvent input, Context context) throws Exception {
        // 1. 요청 파싱
        SessionStartRequest request = objectMapper.readValue(input.getBody(), SessionStartRequest.class);
        request.validate();

        // 2. 세션 생성
        String timestamp = sessionRepository.createSession(
            request.getStudentEmail(),
            request.getSessionType(),
            request.getTutorEmail()
        );

        context.getLogger().log("Session started for: " + request.getStudentEmail() + " at " + timestamp);

        // 3. 응답 (timestamp 반환 - 종료 시 사용)
        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("timestamp", timestamp);
        responseBody.put("message", "Session started");

        return createResponse(201, responseBody);
    }

    private APIGatewayProxyResponseEvent handleSessionEnd(APIGatewayProxyRequestEvent input, Context context) throws Exception {
        // 1. 요청 파싱
        SessionEndRequest request = objectMapper.readValue(input.getBody(), SessionEndRequest.class);
        request.validate();

        // 2. 통계 계산
        double netSpeakingDensity = calculateNetSpeakingDensity(request);
        Double avgPaceRatio = calculateAvgPaceRatio(request);
        Double avgResponseLatency = calculateAvgResponseLatency(request);

        // 3. 세션 업데이트
        sessionRepository.updateSession(request, netSpeakingDensity, avgPaceRatio, avgResponseLatency);

        context.getLogger().log("Session ended for: " + request.getStudentEmail() + " at " + request.getTimestamp());

        // 4. 응답
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("net_speaking_density", Math.round(netSpeakingDensity * 100.0) / 100.0);
        if (avgPaceRatio != null) {
            metrics.put("avg_pace_ratio", Math.round(avgPaceRatio * 100.0) / 100.0);
        }
        if (avgResponseLatency != null) {
            metrics.put("avg_response_latency", Math.round(avgResponseLatency * 100.0) / 100.0);
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("message", "Session ended");
        responseBody.put("metrics", metrics);

        return createResponse(200, responseBody);
    }

    private APIGatewayProxyResponseEvent handleSessionHistory(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> queryParams = input.getQueryStringParameters();

        if (queryParams == null || !queryParams.containsKey("student_email")) {
            throw new IllegalArgumentException("student_email query parameter is required");
        }

        String studentEmail = queryParams.get("student_email");
        int limit = 20;
        if (queryParams.containsKey("limit")) {
            try {
                limit = Integer.parseInt(queryParams.get("limit"));
                if (limit < 1 || limit > 100) {
                    limit = 20;
                }
            } catch (NumberFormatException e) {
                limit = 20;
            }
        }

        List<LearningSession> sessions = sessionRepository.getSessionsByStudent(studentEmail, limit);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("count", sessions.size());
        responseBody.put("sessions", sessions);

        return createResponse(200, responseBody);
    }

    /**
     * 순수 발화 밀도 계산: (speaking / recording) * 100
     */
    private double calculateNetSpeakingDensity(SessionEndRequest request) {
        if (request.getRecordingDuration() == null || request.getRecordingDuration() == 0) {
            return 0.0;
        }
        return (double) request.getSpeakingDuration() / request.getRecordingDuration() * 100.0;
    }

    /**
     * 평균 페이스 비율 계산 (문장 연습용)
     */
    private Double calculateAvgPaceRatio(SessionEndRequest request) {
        if (request.getPracticeRecords() == null || request.getPracticeRecords().isEmpty()) {
            return null;
        }

        double sum = 0.0;
        int count = 0;
        for (PracticeRecord record : request.getPracticeRecords()) {
            if (record.getPaceRatio() != null) {
                sum += record.getPaceRatio();
                count++;
            }
        }

        return count > 0 ? sum / count : null;
    }

    /**
     * 평균 응답 지연 계산 (AI 대화용)
     */
    private Double calculateAvgResponseLatency(SessionEndRequest request) {
        if (request.getResponseLatencies() == null || request.getResponseLatencies().isEmpty()) {
            return null;
        }

        long sum = 0;
        for (Long latency : request.getResponseLatencies()) {
            if (latency != null) {
                sum += latency;
            }
        }

        return (double) sum / request.getResponseLatencies().size();
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody("{\"success\":false,\"error\":\"JSON serialization error\"}");
        }
    }
}
