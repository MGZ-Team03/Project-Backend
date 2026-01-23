package dashboard.controller;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class DashboardController implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DashboardService studentService;
    private final Gson gson;

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String path = request.getPath();
        String method = request.getHttpMethod();


        context.getLogger().log("Processing: " + method + " " + path);

        try {
            // 라우팅
            if (path.equals("/api/dashboard") && "GET".equals(method)) {
                return handleGetDashboard(request, context);
            }

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(404, Map.of("error", e.getMessage()));
        }
        return  createResponse(500, Map.of("error", "Not Found"));
    }

    private APIGatewayProxyResponseEvent handleGetStatus(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("=== Get Status ===");

        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams == null) {
            return createResponse(400, Map.of("error", "Missing query parameters"));
        }

        String tutorEmail = queryParams.get("tutorEmail");
        String studentEmail = queryParams.get("studentEmail");

        if (tutorEmail == null || studentEmail == null) {
            return createResponse(400, Map.of("error", "tutorEmail and studentEmail are required"));
        }

        Map<String, String> status = studentService.getStatus(tutorEmail, studentEmail);

        if (status == null) {
            return createResponse(404, Map.of("error", "Status not found"));
        }

        return createResponse(200, status);
    }

    /**
     * GET /dashboard?tutorEmail=...
     * 튜터의 전체 대시보드 데이터 조회
     */
    private APIGatewayProxyResponseEvent handleGetDashboard(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("=== Get Dashboard ===");

        Map<String, String> queryParams = request.getQueryStringParameters();

        if (queryParams == null || queryParams.get("tutorEmail") == null) {
            return createResponse(400, Map.of("error", "tutorEmail is required"));
        }

        String tutorEmail = queryParams.get("tutorEmail");
        context.getLogger().log("Tutor Email: " + tutorEmail);

        Object dashboardData = studentService.getDashboard(tutorEmail);

        return createResponse(200, dashboardData);
    }

    /**
     * GET /students?tutorEmail=...
     * 튜터의 전체 학생 목록 조회
     */
    private APIGatewayProxyResponseEvent handleGetStudents(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("=== Get Students ===");

        Map<String, String> queryParams = request.getQueryStringParameters();
        if (queryParams == null || queryParams.get("tutorEmail") == null) {
            return createResponse(400, Map.of("error", "tutorEmail is required"));
        }

        String tutorEmail = queryParams.get("tutorEmail");
        Object students = studentService.getAllStudents(tutorEmail);

        return createResponse(200, students);
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(body));
    }
}
