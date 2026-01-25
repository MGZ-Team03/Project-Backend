package statistics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import statistics.model.DailyStatistics;
import statistics.model.WeeklySummary;
import statistics.repository.DailyStatisticsRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DailyStatisticsRepository statisticsRepository;
    private final ObjectMapper objectMapper;

    public App() {
        String tableName = System.getenv("STATISTICS_TABLE");
        this.statisticsRepository = new DailyStatisticsRepository(tableName);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        String path = input.getPath();
        String method = input.getHttpMethod();

        try {
            // GET /api/statistics/today
            if ("GET".equals(method) && path.equals("/api/statistics/today")) {
                return handleTodayStatistics(input, context);
            }

            // GET /api/statistics/weekly
            if ("GET".equals(method) && path.equals("/api/statistics/weekly")) {
                return handleWeeklyStatistics(input, context);
            }

            // POST /api/stats/daily
            if ("POST".equals(method) && path.equals("/api/stats/daily")) {
                return handlePostDailyStatistics(input, context);
            }

            return createResponse(404, Map.of("success", false, "error", "Not Found"));

        } catch (IllegalArgumentException e) {
            return createResponse(400, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("success", false, "error", "Internal server error"));
        }
    }

    private APIGatewayProxyResponseEvent handleTodayStatistics(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> queryParams = input.getQueryStringParameters();

        if (queryParams == null || !queryParams.containsKey("student_email")) {
            throw new IllegalArgumentException("student_email query parameter is required");
        }

        String studentEmail = queryParams.get("student_email");
        DailyStatistics stats = statisticsRepository.getToday(studentEmail);

        if (stats == null) {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            stats = createEmptyStatistics(studentEmail, today);
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("data", stats);

        return createResponse(200, responseBody);
    }

    private APIGatewayProxyResponseEvent handleWeeklyStatistics(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> queryParams = input.getQueryStringParameters();

        if (queryParams == null || !queryParams.containsKey("student_email")) {
            throw new IllegalArgumentException("student_email query parameter is required");
        }

        String studentEmail = queryParams.get("student_email");
        List<DailyStatistics> weeklyStats = statisticsRepository.getWeekly(studentEmail);

        // 요약 통계 계산
        WeeklySummary summary = calculateWeeklySummary(weeklyStats);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("success", true);
        responseBody.put("data", Map.of(
            "daily", weeklyStats,
            "summary", summary
        ));

        return createResponse(200, responseBody);
    }

    /**
     * POST /api/stats/daily - 일별 통계 저장 (Upsert)
     */
    private APIGatewayProxyResponseEvent handlePostDailyStatistics(APIGatewayProxyRequestEvent input, Context context) {
        try {
            String body = input.getBody();
            if (body == null || body.isEmpty()) {
                throw new IllegalArgumentException("Request body is required");
            }

            // JSON 파싱
            DailyStatistics stats = objectMapper.readValue(body, DailyStatistics.class);

            // 필수 필드 검증
            if (stats.getStudentEmail() == null || stats.getStudentEmail().isEmpty()) {
                throw new IllegalArgumentException("student_email is required");
            }
            if (stats.getDate() == null || stats.getDate().isEmpty()) {
                throw new IllegalArgumentException("date is required");
            }

            // 날짜 형식 검증 (YYYY-MM-DD)
            if (!stats.getDate().matches("\\d{4}-\\d{2}-\\d{2}")) {
                throw new IllegalArgumentException("date must be in YYYY-MM-DD format");
            }

            // 저장 (Upsert - 덮어쓰기)
            statisticsRepository.saveDailyStatistics(stats);

            context.getLogger().log("Daily statistics saved for " + stats.getStudentEmail() + " on " + stats.getDate());

            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "Daily statistics saved successfully");
            responseBody.put("data", Map.of(
                "student_email", stats.getStudentEmail(),
                "date", stats.getDate()
            ));

            return createResponse(200, responseBody);

        } catch (IllegalArgumentException e) {
            return createResponse(400, Map.of("success", false, "error", e.getMessage()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            context.getLogger().log("JSON parsing error: " + e.getMessage());
            return createResponse(400, Map.of("success", false, "error", "Invalid JSON format"));
        } catch (Exception e) {
            context.getLogger().log("Error saving daily statistics: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("success", false, "error", "Failed to save daily statistics"));
        }
    }

    private WeeklySummary calculateWeeklySummary(List<DailyStatistics> weeklyStats) {
        WeeklySummary summary = new WeeklySummary();

        long totalRecordingTime = 0;
        long totalSpeakingTime = 0;
        int totalSessions = 0;
        int totalPracticeCount = 0;
        int totalChatTurns = 0;
        int activeDays = 0;

        List<Double> allPaceRatios = new ArrayList<>();
        List<Long> allResponseLatencies = new ArrayList<>();
        List<Double> allNetSpeakingDensities = new ArrayList<>();

        for (DailyStatistics stats : weeklyStats) {
            if (stats.getSessionsCount() != null && stats.getSessionsCount() > 0) {
                activeDays++;
                totalRecordingTime += stats.getTotalRecordingTime() != null ? stats.getTotalRecordingTime() : 0;
                totalSpeakingTime += stats.getTotalSpeakingTime() != null ? stats.getTotalSpeakingTime() : 0;
                totalSessions += stats.getSessionsCount();
                totalPracticeCount += stats.getPracticeCount() != null ? stats.getPracticeCount() : 0;
                totalChatTurns += stats.getChatTurnsCount() != null ? stats.getChatTurnsCount() : 0;

                if (stats.getPaceRatios() != null) {
                    allPaceRatios.addAll(stats.getPaceRatios());
                }
                if (stats.getResponseLatencies() != null) {
                    allResponseLatencies.addAll(stats.getResponseLatencies());
                }
                if (stats.getAvgNetSpeakingDensity() != null && stats.getAvgNetSpeakingDensity() > 0) {
                    allNetSpeakingDensities.add(stats.getAvgNetSpeakingDensity());
                }
            }
        }

        summary.setTotalRecordingTime(totalRecordingTime);
        summary.setTotalSpeakingTime(totalSpeakingTime);
        summary.setTotalSessions(totalSessions);
        summary.setTotalPracticeCount(totalPracticeCount);
        summary.setTotalChatTurns(totalChatTurns);
        summary.setActiveDays(activeDays);

        // 평균 계산
        if (!allPaceRatios.isEmpty()) {
            double avgPaceRatio = allPaceRatios.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.setAvgPaceRatio(Math.round(avgPaceRatio * 100.0) / 100.0);
        } else {
            summary.setAvgPaceRatio(0.0);
        }

        if (!allResponseLatencies.isEmpty()) {
            double avgResponseLatency = allResponseLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            summary.setAvgResponseLatency(Math.round(avgResponseLatency * 100.0) / 100.0);
        } else {
            summary.setAvgResponseLatency(0.0);
        }

        if (!allNetSpeakingDensities.isEmpty()) {
            double avgNetSpeakingDensity = allNetSpeakingDensities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.setAvgNetSpeakingDensity(Math.round(avgNetSpeakingDensity * 100.0) / 100.0);
        } else {
            summary.setAvgNetSpeakingDensity(0.0);
        }

        return summary;
    }

    private DailyStatistics createEmptyStatistics(String studentEmail, String date) {
        DailyStatistics stats = new DailyStatistics();
        stats.setStudentEmail(studentEmail);
        stats.setDate(date);
        stats.setTotalRecordingTime(0L);
        stats.setTotalSpeakingTime(0L);
        stats.setSessionsCount(0);
        stats.setPracticeCount(0);
        stats.setChatTurnsCount(0);
        stats.setAvgPaceRatio(0.0);
        stats.setAvgResponseLatency(0.0);
        stats.setAvgNetSpeakingDensity(0.0);
        stats.setAvgResponseQuality(0.0);
        stats.setPaceRatios(new ArrayList<>());
        stats.setResponseLatencies(new ArrayList<>());
        stats.setResponseQualities(new ArrayList<>());
        return stats;
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
