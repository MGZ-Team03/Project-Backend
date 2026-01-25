//package dashboard.controller;
//
//import com.amazonaws.services.lambda.runtime.Context;
//import com.amazonaws.services.lambda.runtime.RequestHandler;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
//import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
//import com.amazonaws.services.lambda.runtime.events.SQSEvent;
//import com.google.gson.Gson;
//import dashboard.dto.dashboard.DashboardUpdateDto;
//import dashboard.service.DashboardService;
//import lombok.RequiredArgsConstructor;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.time.Duration;
//import java.util.HashMap;
//import java.util.Map;
//
//import static com.amazonaws.services.lambda.runtime.LambdaRuntime.getLogger;
//
//@RequiredArgsConstructor
//public class SQSController implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
//
//    private final DashboardService dashboardService;
//    private final Gson gson;
//
//    // HTTP 클라이언트 (재사용)
//    private static final HttpClient httpClient = HttpClient.newBuilder()
//            .version(HttpClient.Version.HTTP_1_1)
//            .connectTimeout(Duration.ofSeconds(10))
//            .build();
//
//
//    @Override
//    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request,Context context) {
//        String path = request.getPath();
//        String method = request.getHttpMethod();
//
//        getLogger().log("Processing: " + method + " " + path);
//
//        try {
//            if (path.equals("/api/dashboard") && "GET".equals(method)) {
//                return handleGetDashboard(request);
//            }
//        } catch (Exception e) {
//            getLogger().log("Error: " + e.getMessage());
//            return createResponse(500, Map.of("error", e.getMessage()));
//        }
//
//
//
//
//        return null;
//    }
//
//    public Void handleSQSMessages(SQSEvent event) {
//        getLogger().log("  SQS 메시지 처리 시작");
//        getLogger().log("  메시지 수: " + event.getRecords().size());
//
//        int successCount = 0;
//        int failCount = 0;
//
//        for (SQSEvent.SQSMessage message : event.getRecords()) {
//            try {
//                getLogger().log("메시지 ID: " + message.getMessageId());
//                getLogger().log("message getbody: " + message.getBody());
//
//
//                // JSON → DashboardUpdateDto 변환
//                DashboardUpdateDto dashboardUpdate = gson.fromJson(
//                        message.getBody(),
//                        DashboardUpdateDto.class
//                );
//
//                getLogger().log("📊 대시보드 업데이트:");
//                getLogger().log("  - Type: " + dashboardUpdate.getType());
//                getLogger().log("  - Timestamp: " + dashboardUpdate.getTimestamp());
//                getLogger().log("  - 학생 수: " + dashboardUpdate.getStudents().size());
//                getLogger().log("  - Summary: " + dashboardUpdate.getSummary());
//
//                // 🌐 외부 API로 전송
//                sendToAPI(dashboardUpdate) ;
//
//                successCount++;
//                getLogger().log("✅ 메시지 처리 완료");
//
//            } catch (Exception e) {
//                failCount++;
//                getLogger().log("❌ 메시지 처리 실패: " + e.getMessage());
//                e.printStackTrace();
//
//                // SQS 재시도를 위해 예외 던지기
//                throw new RuntimeException("Failed to process SQS message", e);
//            }
//        }
//
//        getLogger().log("  처리 완료 - 성공: " + successCount + ", 실패: " + failCount);
//
//        return null;
//    }
//
//    private void sendToAPI(DashboardUpdateDto dashboardUpdate)  {
//        try {
//            // 환경변수에서 API 엔드포인트 가져오기
//            String apiEndpoint = System.getenv("DASHBOARD_API_ENDPOINT");
//
//            if (apiEndpoint == null || apiEndpoint.isEmpty()) {
//                throw new IllegalStateException("❌ DASHBOARD_API_ENDPOINT 환경변수 없음");
//            }
//
//            getLogger().log("🌐 API Endpoint: " + apiEndpoint);
//
//            // 튜터 이메일 추출 (로깅용)
//            String tutorEmail = "unknown";
//            if (!dashboardUpdate.getStudents().isEmpty()) {
//                tutorEmail = dashboardUpdate.getStudents().get(0).getTutorEmail();
//                getLogger().log("👨‍🏫 Tutor: " + tutorEmail);
//            }
//
//            // DashboardUpdateDto → JSON 변환
//            String requestBody = gson.toJson(dashboardUpdate);
//            getLogger().log("📦 Request Body Size: " + requestBody.length() + " bytes" + "requstBody: " + requestBody);
//
//
//            // HTTP POST 요청 생성
//            HttpRequest request = HttpRequest.newBuilder()
//                    .uri(URI.create(apiEndpoint))
//                    .header("Content-Type", "application/json")
//                    .header("Accept", "application/json")
//                    .header("X-Tutor-Email", tutorEmail)
//                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                    .timeout(Duration.ofSeconds(15))
//                    .build();
//
//            getLogger().log("📤 Sending HTTP POST...");
//            getLogger().log("requset.sending: "+request);
//
//            // HTTP 요청 전송
//            long startTime = System.currentTimeMillis();
//            HttpResponse<String> response = httpClient.send(
//                    request,
//                    HttpResponse.BodyHandlers.ofString()
//            );
//            long duration = System.currentTimeMillis() - startTime;
//
//            // 응답 처리
//            int statusCode = response.statusCode();
//            getLogger().log("📥 Response:");
//            getLogger().log("  - Status: " + statusCode);
//            getLogger().log("  - Duration: " + duration + "ms");
//            getLogger().log("  - Body: " + response.body());
//
//            if (statusCode >= 200 && statusCode < 300) {
//                getLogger().log("✅ API 전송 성공 (HTTP " + statusCode + ")");
//            } else {
//                throw new RuntimeException("API 실패 - HTTP " + statusCode + ": " + response.body());
//            }
//
//        } catch (Exception e) {
//            getLogger().log("❌ API 전송 실패: " + e.getMessage());
//            e.printStackTrace();
//            throw new RuntimeException("Failed to send to API", e);
//        }
//    }
//
//    private APIGatewayProxyResponseEvent handleGetDashboard(APIGatewayProxyRequestEvent request) {
//        Map<String, String> queryParams = request.getQueryStringParameters();
//
//        if (queryParams == null || queryParams.get("tutorEmail") == null) {
//            return createResponse(400, Map.of("error", "tutorEmail is required"));
//        }
//
//        String tutorEmail = queryParams.get("tutorEmail");
//        Object dashboardData = dashboardService.getDashboard(tutorEmail);
//
//        return createResponse(200, dashboardData);
//    }
//
//    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
//        Map<String, String> headers = new HashMap<>();
//        headers.put("Content-Type", "application/json");
//        headers.put("Access-Control-Allow-Origin", "*");
//        headers.put("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
//        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
//
//        return new APIGatewayProxyResponseEvent()
//                .withStatusCode(statusCode)
//                .withHeaders(headers)
//                .withBody(gson.toJson(body));
//    }
//
//}
