package statistics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.annotation.JsonInclude;
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
        // 빈 배열/빈 값은 응답에서 생략 (프론트 정책: 빈 배열 미사용)
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
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
        List<Double> allResponseQualities = new ArrayList<>();

        double weightedPaceSum = 0.0;
        long weightedPaceCount = 0;
        double weightedLatencySum = 0.0;
        long weightedLatencyCount = 0;
        double weightedQualitySum = 0.0;
        long weightedQualityCount = 0;

        for (DailyStatistics stats : weeklyStats) {
            if (stats.getSessionsCount() != null && stats.getSessionsCount() > 0) {
                activeDays++;
                totalRecordingTime += stats.getTotalRecordingTime() != null ? stats.getTotalRecordingTime() : 0;
                totalSpeakingTime += stats.getTotalSpeakingTime() != null ? stats.getTotalSpeakingTime() : 0;
                totalSessions += stats.getSessionsCount();
                totalPracticeCount += stats.getPracticeCount() != null ? stats.getPracticeCount() : 0;
                totalChatTurns += stats.getChatTurnsCount() != null ? stats.getChatTurnsCount() : 0;

                if (stats.getPaceRatios() != null && !stats.getPaceRatios().isEmpty()) {
                    allPaceRatios.addAll(stats.getPaceRatios());
                } else if (stats.getAvgPaceRatio() != null
                    && stats.getPaceRatioCount() != null
                    && stats.getPaceRatioCount() > 0) {
                    weightedPaceSum += stats.getAvgPaceRatio() * stats.getPaceRatioCount();
                    weightedPaceCount += stats.getPaceRatioCount();
                }
                if (stats.getResponseLatencies() != null && !stats.getResponseLatencies().isEmpty()) {
                    allResponseLatencies.addAll(stats.getResponseLatencies());
                } else if (stats.getAvgResponseLatency() != null
                    && stats.getResponseLatencyCount() != null
                    && stats.getResponseLatencyCount() > 0) {
                    weightedLatencySum += stats.getAvgResponseLatency() * stats.getResponseLatencyCount();
                    weightedLatencyCount += stats.getResponseLatencyCount();
                }
                if (stats.getAvgNetSpeakingDensity() != null && stats.getAvgNetSpeakingDensity() > 0) {
                    allNetSpeakingDensities.add(stats.getAvgNetSpeakingDensity());
                }

                // 응답 품질: 리스트가 있으면 리스트 사용, 없으면 avg+count 가중평균
                if (stats.getResponseQualities() != null && !stats.getResponseQualities().isEmpty()) {
                    for (var q : stats.getResponseQualities()) {
                        if (q != null && q.getOverallScore() != null) {
                            allResponseQualities.add(q.getOverallScore());
                        }
                    }
                } else if (stats.getAvgResponseQuality() != null
                    && stats.getResponseQualityCount() != null
                    && stats.getResponseQualityCount() > 0) {
                    weightedQualitySum += stats.getAvgResponseQuality() * stats.getResponseQualityCount();
                    weightedQualityCount += stats.getResponseQualityCount();
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
        } else if (weightedPaceCount > 0) {
            double avgPaceRatio = weightedPaceSum / weightedPaceCount;
            summary.setAvgPaceRatio(Math.round(avgPaceRatio * 100.0) / 100.0);
        } else {
            summary.setAvgPaceRatio(0.0);
        }

        if (!allResponseLatencies.isEmpty()) {
            double avgResponseLatency = allResponseLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
            summary.setAvgResponseLatency(Math.round(avgResponseLatency * 100.0) / 100.0);
        } else if (weightedLatencyCount > 0) {
            double avgResponseLatency = weightedLatencySum / weightedLatencyCount;
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

        if (!allResponseQualities.isEmpty()) {
            double avgResponseQuality = allResponseQualities.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            summary.setAvgResponseQuality(Math.round(avgResponseQuality * 100.0) / 100.0);
        } else if (weightedQualityCount > 0) {
            double avgResponseQuality = weightedQualitySum / weightedQualityCount;
            summary.setAvgResponseQuality(Math.round(avgResponseQuality * 100.0) / 100.0);
        } else {
            summary.setAvgResponseQuality(0.0);
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
        stats.setPaceRatioCount(0);
        stats.setResponseLatencyCount(0);
        stats.setResponseQualityCount(0);
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
