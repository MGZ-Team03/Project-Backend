package sentences.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.TTSJobMessagePayload;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.List;

public class TtsSqsService {
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public TtsSqsService(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    public void sendTtsJob(TTSJobMessagePayload payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            SendMessageRequest req = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build();
            sqsClient.sendMessage(req);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send TTS SQS message", e);
        }
    }

    /**
     * 최대 10개 단위로 배치 전송.
     */
    public void sendTtsJobsBatch(List<TTSJobMessagePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }

        try {
            int i = 0;
            while (i < payloads.size()) {
                int end = Math.min(i + 10, payloads.size());
                List<TTSJobMessagePayload> chunk = payloads.subList(i, end);

                List<SendMessageBatchRequestEntry> entries = new ArrayList<>();
                for (int j = 0; j < chunk.size(); j++) {
                    TTSJobMessagePayload p = chunk.get(j);
                    String body = objectMapper.writeValueAsString(p);
                    // 배치 엔트리 id는 요청 내에서만 유니크하면 됨
                    entries.add(SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(j))
                        .messageBody(body)
                        .build());
                }

                SendMessageBatchRequest req = SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(entries)
                    .build();

                SendMessageBatchResponse resp = sqsClient.sendMessageBatch(req);
                if (resp.hasFailed() && !resp.failed().isEmpty()) {
                    throw new RuntimeException("Failed to send some TTS SQS messages: " + resp.failed());
                }

                i = end;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send TTS SQS batch messages", e);
        }
    }
}

