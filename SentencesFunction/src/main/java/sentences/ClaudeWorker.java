package sentences;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.AsyncChatMessage;
import sentences.model.ConversationMessage;
import sentences.service.ClaudeApiKeyProvider;
import sentences.service.ClaudeApiService;
import sentences.service.ConversationRepository;
import sentences.service.JobStatusService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

public class ClaudeWorker implements RequestHandler<SQSEvent, Void> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClaudeApiKeyProvider claudeApiKeyProvider;
    private final String claudeApiKeySecretId;
    private final ConversationRepository conversationRepository;
    private final JobStatusService jobStatusService;
    private final long seedConversationTtlSeconds;

    // Lazy-init
    private volatile ClaudeApiService claudeApiService;
    private final Object claudeInitLock = new Object();

    public ClaudeWorker() {
        this.claudeApiKeyProvider = new ClaudeApiKeyProvider();
        this.claudeApiKeySecretId = System.getenv("CLAUDE_API_KEY_SECRET_ID");

        String conversationsTable = System.getenv("AI_CONVERSATIONS_TABLE");
        this.conversationRepository = new ConversationRepository(conversationsTable);

        this.seedConversationTtlSeconds = parseLongEnv("AI_CONVERSATION_SEED_TTL_SECONDS", 10L * 60);

        String jobStatusTable = System.getenv("JOB_STATUS_TABLE");
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        this.jobStatusService = new JobStatusService(dynamoDbClient, jobStatusTable);
    }

    private ClaudeApiService getClaudeApiService() {
        ClaudeApiService service = claudeApiService;
        if (service != null) {
            return service;
        }
        synchronized (claudeInitLock) {
            if (claudeApiService == null) {
                String apiKey = claudeApiKeyProvider.getApiKey(claudeApiKeySecretId);
                claudeApiService = new ClaudeApiService(apiKey);
            }
            return claudeApiService;
        }
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                context.getLogger().log("Processing chat request from SQS: " + message.getMessageId());

                // SQS 메시지 파싱
                AsyncChatMessage asyncMessage = objectMapper.readValue(
                    message.getBody(), AsyncChatMessage.class);

                String requestId = asyncMessage.getRequestId();
                String conversationId = asyncMessage.getConversationId();
                String studentEmail = asyncMessage.getStudentEmail();
                String systemPrompt = asyncMessage.getSystemPrompt();
                List<ConversationMessage> messages = asyncMessage.getMessages();

                context.getLogger().log(String.format(
                    "Request ID: %s, Conversation ID: %s, Message count: %d",
                    requestId, conversationId, messages.size()));

                // Claude API 호출
                context.getLogger().log("Calling Claude API...");
                String aiResponse = getClaudeApiService().callClaudeApiWithHistory(
                    systemPrompt, messages);

                context.getLogger().log("AI response generated");

                // 기존 대화 조회 및 업데이트
                ConversationRepository.ConversationData conversation =
                    conversationRepository.getConversation(conversationId);

                if (conversation != null) {
                    List<ConversationMessage> updatedMessages = new ArrayList<>(conversation.getMessages());
                    updatedMessages.add(new ConversationMessage("assistant", aiResponse));

                    long ttlEpochSeconds = computeTtlEpochSeconds(updatedMessages);
                    conversationRepository.saveConversation(
                        studentEmail, conversationId,
                        conversation.getTopic(), conversation.getDifficulty(),
                        conversation.getSituation(), conversation.getRole(),
                        updatedMessages, conversation.getTimestamp(), ttlEpochSeconds);

                    // 작업 상태 업데이트 (COMPLETED)
                    jobStatusService.updateJobCompleted(
                        requestId, conversationId, aiResponse, updatedMessages.size());

                    context.getLogger().log("Chat request completed successfully: " + requestId);
                } else {
                    throw new Exception("Conversation not found: " + conversationId);
                }

            } catch (Exception e) {
                context.getLogger().log("Error processing chat request: " + e.getMessage());
                e.printStackTrace();

                // 실패 상태 업데이트
                try {
                    AsyncChatMessage asyncMessage = objectMapper.readValue(
                        message.getBody(), AsyncChatMessage.class);
                    jobStatusService.updateJobFailed(asyncMessage.getRequestId(), e.getMessage());
                } catch (Exception updateError) {
                    context.getLogger().log("Failed to update job status: " + updateError.getMessage());
                }

                // SQS DLQ로 전달되도록 예외 재발생
                throw new RuntimeException("Chat request processing failed", e);
            }
        }

        return null;
    }

    private long parseLongEnv(String key, long defaultValue) {
        try {
            String raw = System.getenv(key);
            if (raw == null || raw.isBlank()) return defaultValue;
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private long computeTtlEpochSeconds(List<ConversationMessage> messages) {
        // 실제 user 메시지가 한 번이라도 있으면 long TTL, 아니면 short TTL
        boolean hasUserMessage = false;
        if (messages != null) {
            for (ConversationMessage m : messages) {
                if (m == null) continue;
                String role = m.getRole();
                if (role != null && role.equalsIgnoreCase("user")) {
                    String content = m.getContent();
                    if (content != null && !content.trim().isEmpty()) {
                        hasUserMessage = true;
                        break;
                    }
                }
            }
        }
        long seconds = hasUserMessage
            ? ConversationRepository.LONG_TTL_SECONDS
            : seedConversationTtlSeconds; // seed TTL 기본값(Worker는 보통 seed에 안 닿지만 안전망)
        return Instant.now().plusSeconds(seconds).getEpochSecond();
    }
}
