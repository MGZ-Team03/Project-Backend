package sentences.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.AsyncChatMessage;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SQSService {
    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public SQSService(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    public void sendChatMessage(AsyncChatMessage chatMessage) {
        try {
            String messageBody = objectMapper.writeValueAsString(chatMessage);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageGroupId(chatMessage.getConversationId())  // FIFO 순서 보장
                    .messageDeduplicationId(chatMessage.getRequestId())  // 중복 방지
                    .build();

            sqsClient.sendMessage(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send SQS message", e);
        }
    }
}
