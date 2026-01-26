package statistics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import statistics.model.DailyStatistics;
import statistics.repository.DailyStatisticsRepository;

/**
 * SQS에서 일일 통계 스냅샷 메시지를 소비하여 DynamoDB에 저장하는 Lambda Handler
 */
public class DailyStatsPersistenceHandler implements RequestHandler<SQSEvent, Void> {

    private final ObjectMapper objectMapper;
    private final DailyStatisticsRepository statisticsRepository;

    public DailyStatsPersistenceHandler() {
        this.objectMapper = new ObjectMapper();
        String tableName = System.getenv("STATISTICS_TABLE");
        this.statisticsRepository = new DailyStatisticsRepository(tableName);
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Processing " + event.getRecords().size() + " daily-stats messages from SQS");

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                DailyStatistics stats = objectMapper.readValue(message.getBody(), DailyStatistics.class);

                if (stats.getStudentEmail() == null || stats.getStudentEmail().isEmpty()
                    || stats.getDate() == null || stats.getDate().isEmpty()) {
                    throw new IllegalArgumentException("Invalid message payload: student_email/date is required");
                }

                statisticsRepository.saveDailyStatistics(stats);

                context.getLogger().log("Saved daily stats. messageId=" + message.getMessageId()
                    + ", student=" + stats.getStudentEmail() + ", date=" + stats.getDate());

            } catch (Exception e) {
                context.getLogger().log("ERROR: Failed to process messageId=" + message.getMessageId()
                    + ", error=" + e.getMessage());
                e.printStackTrace();
                // 실패 시 재시도 후 DLQ로 이동
                throw new RuntimeException("Failed to process SQS message", e);
            }
        }

        return null;
    }
}

