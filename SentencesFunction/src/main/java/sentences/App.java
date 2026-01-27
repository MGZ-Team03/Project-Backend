package sentences;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import sentences.model.*;
import sentences.service.ClaudeApiKeyProvider;
import sentences.service.ClaudeApiService;
import sentences.service.ConversationRepository;
import sentences.service.SituationGenerator;
import sentences.service.TopicScenariosProvider;
import sentences.service.SQSService;
import sentences.service.JobStatusService;
import sentences.service.SentenceAudioService;
import sentences.service.TtsSqsService;
import sentences.util.TextHashUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationRepository conversationRepository;
    private final ClaudeApiKeyProvider claudeApiKeyProvider;
    private final String claudeApiKeySecretId;
    private final SQSService sqsService;
    private final JobStatusService jobStatusService;
    private final TtsSqsService ttsSqsService;
    private final SentenceAudioService sentenceAudioService;
    private final String ttsBucket;
    private final int presignedUrlExpiration;
    private final S3Presigner s3Presigner;

    // 레벨 평가 설정 (요구사항: 최근 10개 conversation)
    private static final int LEVEL_EVAL_CONVERSATION_LIMIT = 10;
    private static final int LEVEL_EVAL_MAX_USER_MSG_PER_CONV = 10;
    private static final int LEVEL_EVAL_MAX_CHARS_PER_MSG = 300;

    // Lazy-init (cold start 최적화): Secrets Manager 호출은 첫 요청 시점에만 수행
    private volatile ClaudeApiService claudeApiService;
    private final Object claudeInitLock = new Object();

    public App() {
        this.claudeApiKeyProvider = new ClaudeApiKeyProvider();
        this.claudeApiKeySecretId = System.getenv("CLAUDE_API_KEY_SECRET_ID");

        String conversationsTable = System.getenv("AI_CONVERSATIONS_TABLE");
        this.conversationRepository = new ConversationRepository(conversationsTable);

        // SQS, DynamoDB, S3(Presign) 클라이언트 초기화
        String aiQueueUrl = System.getenv("AI_CONVERSATION_QUEUE_URL");
        String jobStatusTable = System.getenv("JOB_STATUS_TABLE");

        String ttsQueueUrl = System.getenv("TTS_QUEUE_URL");
        String sentenceAudioTable = System.getenv("SENTENCE_AUDIO_TABLE");
        this.ttsBucket = System.getenv("TTS_BUCKET");
        this.presignedUrlExpiration = Integer.parseInt(
            System.getenv().getOrDefault("PRESIGNED_URL_EXPIRATION", "3600")
        );

        SqsClient sqsClient = null;
        DynamoDbClient dynamoDbClient = null;
        S3Presigner presigner = null;

        if (aiQueueUrl != null || ttsQueueUrl != null) {
            sqsClient = SqsClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        }
        if (jobStatusTable != null || sentenceAudioTable != null) {
            dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        }
        if (this.ttsBucket != null) {
            // S3Client는 presign에는 필요 없지만, region 설정 명시용으로 함께 생성(향후 확장 대비)
            S3Client.builder().region(Region.AP_NORTHEAST_2).build();
            presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();
        }

        this.sqsService = (aiQueueUrl != null && sqsClient != null) ? new SQSService(sqsClient, aiQueueUrl) : null;
        this.jobStatusService = (jobStatusTable != null && dynamoDbClient != null) ? new JobStatusService(dynamoDbClient, jobStatusTable) : null;
        this.ttsSqsService = (ttsQueueUrl != null && sqsClient != null) ? new TtsSqsService(sqsClient, ttsQueueUrl) : null;
        this.sentenceAudioService = (sentenceAudioTable != null && dynamoDbClient != null) ? new SentenceAudioService(dynamoDbClient, sentenceAudioTable) : null;
        this.s3Presigner = presigner;
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
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        String httpMethod = input.getHttpMethod();
        String path = input.getPath();

        context.getLogger().log("Sentences Request - Method: " + httpMethod + ", Path: " + path);

        try {
            // POST /api/sentences/generate - 문장 생성
            if ("POST".equals(httpMethod) && path.endsWith("/generate")) {
                return handleGenerateSentences(input, context);
            }

            // POST /api/sentences/recommend - 추천 문장 (인증 필요)
            if ("POST".equals(httpMethod) && path.endsWith("/recommend")) {
                return handleRecommendSentences(input, context);
            }

            // POST /api/sentences/feedback - 문장 피드백 (인증 필요)
            if ("POST".equals(httpMethod) && path.endsWith("/feedback")) {
                return handleSentenceFeedback(input, context);
            }

            // POST /api/ai/chat/start - AI 대화 시작
            if ("POST".equals(httpMethod) && path.endsWith("/chat/start")) {
                return handleChatStart(input, context);
            }

            // POST /api/ai/chat/message - AI 대화 메시지
            if ("POST".equals(httpMethod) && path.endsWith("/chat/message")) {
                return handleChatMessage(input, context);
            }

            // GET /api/ai/chat/status/{requestId} - 작업 상태 조회
            if ("GET".equals(httpMethod) && path.contains("/chat/status/")) {
                return handleChatStatus(input, context);
            }

            // GET /api/ai/conversations - 대화 이력 목록 조회
            if ("GET".equals(httpMethod) && path.equals("/api/ai/conversations")) {
                return handleConversationList(input, context);
            }

            // GET /api/ai/conversations/{conversationId} - 대화 상세 조회
            if ("GET".equals(httpMethod) && path.startsWith("/api/ai/conversations/")) {
                return handleConversationDetail(input, context);
            }

            // GET /api/ai/level - 최근 대화 기반 레벨 평가(상/중/하)
            if ("GET".equals(httpMethod) && path.equals("/api/ai/level")) {
                return handleAiLevel(input, context);
            }

            // GET /api/sentences/audio/{sessionId} - 문장 연습 오디오 상태/통계 조회
            if ("GET".equals(httpMethod) && path.contains("/api/sentences/audio/")) {
                return handleSentenceAudioSession(input, context);
            }

            return createResponse(404, Map.of("error", "Not Found"));

        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, Map.of("success", false, "error", "Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleGenerateSentences(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱
            SentenceGenerateRequest request = objectMapper.readValue(
                input.getBody(), SentenceGenerateRequest.class);
            request.validate();

            String topic = request.getTopic();
            String difficulty = request.getDifficulty();

            context.getLogger().log("Generating sentences - Topic: " + topic + ", Difficulty: " + difficulty);

            // 프롬프트 생성
            String systemPrompt = buildSystemPrompt(topic, difficulty);
            // 요청마다 seed를 넣어 다양성/랜덤성을 높임 (저장/히스토리 없이 프롬프트만 강화)
            String diversitySeed = UUID.randomUUID().toString();
            String userPrompt = """
                Generate exactly 5 sentences now with maximum diversity.
                Seed: %s
                Requirements:
                - Keep the difficulty level exactly as specified.
                - Treat each of the 10 as a different micro-scenario.
                - Each sentence must have 2+ concrete details (numbers, times, names, item/model, seat/gate, address-like detail, etc.).
                - Avoid repeating the same opening words or template phrases across the 10 sentences.
                - Output JSON array only (no extra text).
                """.formatted(diversitySeed);

            // Claude API 호출
            String claudeResponse = getClaudeApiService().callClaudeApi(systemPrompt, userPrompt);
            context.getLogger().log("Claude API response received");

            // JSON 파싱 (Claude가 반환한 문장 배열)
            List<Sentence> sentences;
            try {
                sentences = objectMapper.readValue(
                    claudeResponse, new TypeReference<List<Sentence>>() {});
            } catch (Exception parseError) {
                // Claude가 간혹 JSON 외 텍스트를 섞어 반환하는 경우를 대비해 JSON 배열만 추출 시도
                String extracted = extractJsonArray(claudeResponse);
                context.getLogger().log("Claude response JSON parse failed; retrying with extracted JSON array. "
                    + "originalLen=" + (claudeResponse == null ? 0 : claudeResponse.length())
                    + ", extractedLen=" + (extracted == null ? 0 : extracted.length())
                    + ", error=" + parseError.getMessage());
                sentences = objectMapper.readValue(
                    extracted, new TypeReference<List<Sentence>>() {});
            }

            // 응답 생성
            SentenceGenerateResponse response = SentenceGenerateResponse.success(sentences, topic, difficulty);
            String sessionId = UUID.randomUUID().toString();
            response.setSessionId(sessionId);

            // 문장 생성 직후 오디오 세션 선생성 + TTS 큐잉 (비동기)
            try {
                enqueueSentenceTtsSession(sessionId, sentences, "Joanna", context);
            } catch (Exception ttsErr) {
                context.getLogger().log("Failed to enqueue sentence TTS session: " + ttsErr.getMessage());
            }
            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, SentenceGenerateResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Sentence generation error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, SentenceGenerateResponse.error("Failed to generate sentences: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleRecommendSentences(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Cognito Authorizer claims에서 studentEmail 추출 (권한/로깅 용도)
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            SentenceRecommendRequest request = objectMapper.readValue(
                input.getBody(), SentenceRecommendRequest.class);
            request.validate();

            String topic = request.getTopic();
            String difficulty = request.getDifficulty();
            int count = request.getCountOrDefault();
            String conversationId = request.getConversationId();

            context.getLogger().log("Recommending sentences - student=" + studentEmail
                + ", topic=" + topic + ", difficulty=" + difficulty + ", count=" + count
                + ", conversationId=" + conversationId);

            // conversationId가 있으면 턴 차감 (더미 메시지 2개 추가)
            ConversationRepository.ConversationData conversation = null;
            int remainingTurns = -1;  // -1 = 대화 없음

            if (conversationId != null && !conversationId.trim().isEmpty()) {
                conversation = conversationRepository.getConversation(conversationId);

                if (conversation != null && studentEmail.equals(conversation.getStudentEmail())) {
                    List<ConversationMessage> messages = new ArrayList<>(conversation.getMessages());

                    // 추천 요청으로 턴 2개 차감 (user + assistant 역할의 더미 메시지)
                    messages.add(new ConversationMessage("user", "[추천 문장 요청]"));
                    messages.add(new ConversationMessage("assistant", "[추천 문장 제공]"));

                    conversationRepository.saveConversation(
                        conversation.getStudentEmail(), conversationId,
                        conversation.getTopic(), conversation.getDifficulty(),
                        conversation.getSituation(), conversation.getRole(),
                        messages, conversation.getTimestamp());

                    remainingTurns = 15 - messages.size();
                    context.getLogger().log("Turn deducted for recommend. Current turn count: "
                        + messages.size() + ", remaining: " + remainingTurns);
                }
            }

            // conversation 객체를 프롬프트에 전달
            String systemPrompt = buildRecommendSystemPrompt(topic, difficulty, conversation);
            String seed = UUID.randomUUID().toString();
            String userPrompt = buildRecommendUserPrompt(count, seed, conversation != null);

            String claudeResponse = getClaudeApiService().callClaudeApi(systemPrompt, userPrompt);

            List<RecommendedSentence> sentences;
            try {
                sentences = objectMapper.readValue(
                    claudeResponse, new TypeReference<List<RecommendedSentence>>() {});
            } catch (Exception parseError) {
                String extracted = extractJsonArray(claudeResponse);
                context.getLogger().log("Recommend JSON parse failed; retrying with extracted JSON array. "
                    + "originalLen=" + (claudeResponse == null ? 0 : claudeResponse.length())
                    + ", extractedLen=" + (extracted == null ? 0 : extracted.length())
                    + ", error=" + parseError.getMessage());
                sentences = objectMapper.readValue(
                    extracted, new TypeReference<List<RecommendedSentence>>() {});
            }

            // id 정규화(없거나 0이면 1..N 부여)
            if (sentences != null) {
                for (int i = 0; i < sentences.size(); i++) {
                    RecommendedSentence s = sentences.get(i);
                    if (s != null && s.getId() <= 0) {
                        s.setId(i + 1);
                    }
                }
            }

            SentenceRecommendResponse response = SentenceRecommendResponse.success(sentences, topic, difficulty);
            response.setRemainingTurns(remainingTurns);
            String sessionId = UUID.randomUUID().toString();
            response.setSessionId(sessionId);

            // 추천 문장도 오디오 선처리 (영어만)
            try {
                enqueueRecommendSentenceTtsSession(sessionId, sentences, "Joanna", context);
            } catch (Exception ttsErr) {
                context.getLogger().log("Failed to enqueue recommend sentence TTS session: " + ttsErr.getMessage());
            }
            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, SentenceRecommendResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Sentence recommend error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, SentenceRecommendResponse.error("Failed to recommend sentences: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleSentenceFeedback(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Cognito Authorizer claims에서 studentEmail 추출 (권한/로깅 용도)
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            SentenceFeedbackRequest request = objectMapper.readValue(
                input.getBody(), SentenceFeedbackRequest.class);
            request.validate();

            String difficulty = request.getDifficultyOrDefault();
            context.getLogger().log("Sentence feedback - student=" + studentEmail
                + ", difficulty=" + difficulty);

            String systemPrompt = buildFeedbackSystemPrompt(difficulty);
            String userPrompt = buildFeedbackUserPrompt(request.getOriginalText(), request.getUserText());

            String claudeResponse = getClaudeApiService().callClaudeApi(systemPrompt, userPrompt);

            Map<String, Object> payload;
            try {
                payload = objectMapper.readValue(claudeResponse, new TypeReference<Map<String, Object>>() {});
            } catch (Exception parseError) {
                String extracted = extractJsonObject(claudeResponse);
                context.getLogger().log("Feedback JSON parse failed; retrying with extracted JSON object. "
                    + "originalLen=" + (claudeResponse == null ? 0 : claudeResponse.length())
                    + ", extractedLen=" + (extracted == null ? 0 : extracted.length())
                    + ", error=" + parseError.getMessage());
                payload = objectMapper.readValue(extracted, new TypeReference<Map<String, Object>>() {});
            }

            String correctedUserText = asString(payload.get("correctedUserText"));
            List<String> feedback = asStringList(payload.get("feedback"));
            List<String> suggestions = asStringList(payload.get("suggestions"));
            String encouragement = asString(payload.get("encouragement"));

            return createResponse(200, SentenceFeedbackResponse.success(
                correctedUserText, feedback, suggestions, encouragement
            ));

        } catch (IllegalArgumentException e) {
            return createResponse(400, SentenceFeedbackResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Sentence feedback error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, SentenceFeedbackResponse.error("Failed to generate feedback: " + e.getMessage()));
        }
    }

    /**
     * Claude 응답에서 JSON 배열([ ... ]) 부분만 추출합니다.
     * JSON 외 텍스트/마크다운이 섞여 있어도 파싱 가능하도록 폴백합니다.
     */
    private String extractJsonArray(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Claude response is null");
        }
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("Claude response does not contain a JSON array");
        }
        return text.substring(start, end + 1).trim();
    }

    /**
     * Claude 응답에서 JSON 객체({ ... }) 부분만 추출합니다.
     */
    private String extractJsonObject(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Claude response is null");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("Claude response does not contain a JSON object");
        }
        return text.substring(start, end + 1).trim();
    }

    private String asString(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List) {
            List<Object> raw = (List<Object>) v;
            List<String> out = new ArrayList<>();
            for (Object o : raw) {
                String s = asString(o);
                if (s != null) out.add(s);
            }
            return out;
        }
        String s = asString(v);
        return s == null ? List.of() : List.of(s);
    }

    private APIGatewayProxyResponseEvent handleChatStart(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱
            ChatStartRequest request = objectMapper.readValue(
                input.getBody(), ChatStartRequest.class);
            request.validate();

            String topic = request.getTopic();
            String difficulty = request.getDifficulty();

            context.getLogger().log("Starting AI chat - Topic: " + topic + ", Difficulty: " + difficulty);

            // Cognito Authorizer claims에서 studentEmail 추출
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            // 랜덤 상황 생성
            SituationGenerator.SituationTemplate situation =
                SituationGenerator.generateRandomSituation(topic);

            // Conversation ID 생성
            String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);

            // 시스템 프롬프트 생성
            String systemPrompt = buildChatSystemPrompt(
                topic, difficulty, situation.getSituation(), situation.getRole(), situation.getGoal());

            // Claude API 호출 (첫 메시지 생성)
            String initialMessage = "Start the conversation as " + situation.getRole() + ".";
            String aiResponse = getClaudeApiService().callClaudeApi(systemPrompt, initialMessage);

            context.getLogger().log("AI initial message generated");

            // 대화 히스토리 저장
            List<ConversationMessage> messages = new ArrayList<>();
            messages.add(new ConversationMessage("assistant", aiResponse));

            conversationRepository.saveConversation(
                studentEmail, conversationId, topic, difficulty,
                situation.getSituation(), situation.getRole(), messages);

            // 응답 생성
            ChatStartResponse response = ChatStartResponse.success(
                conversationId, situation.getSituation(), aiResponse,
                situation.getRole(), situation.getGoal());
            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, ChatStartResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Chat start error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, ChatStartResponse.error("Failed to start chat: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleChatMessage(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱
            ChatMessageRequest request = objectMapper.readValue(
                input.getBody(), ChatMessageRequest.class);
            request.validate();

            String conversationId = request.getConversationId();
            String userMessage = request.getUserMessage();

            context.getLogger().log("Chat message - ConversationId: " + conversationId);

            // Cognito Authorizer claims에서 studentEmail 추출
            String requesterEmail = extractStudentEmailFromAuthorizerClaims(input);

            // 기존 대화 조회
            ConversationRepository.ConversationData conversation =
                conversationRepository.getConversation(conversationId);

            if (conversation == null) {
                return createResponse(404, ChatMessageResponse.error("Conversation not found"));
            }

            // 다른 사용자 대화 접근 방지
            if (!requesterEmail.equals(conversation.getStudentEmail())) {
                return createResponse(403, Map.of("success", false, "error", "Forbidden"));
            }

            // ===== 15턴 제한 (assistant 포함 총 15 메시지) =====
            // ConversationRepository는 turn_count = messages.size()로 저장하고 있으므로 메시지 개수로 판단한다.
            int currentCount = conversation.getMessages() == null ? 0 : conversation.getMessages().size();
            if (currentCount >= 15) {
                String wrapUp = buildTurnLimitWrapUpMessage(conversation);
                return createResponse(200, ChatMessageResponse.ended(conversationId, wrapUp, currentCount, "TURN_LIMIT"));
            }
            // 15번째(마지막) 응답은 랩업으로 종료: 현재가 14 이상이면, 이번 요청에서는 Claude 호출 대신 랩업만 반환한다.
            // (이 경우 유저의 마지막 메시지는 히스토리에 저장하지 않고 종료 처리한다.)
            if (currentCount >= 14) {
                List<ConversationMessage> messages = new ArrayList<>(conversation.getMessages());
                String wrapUp = buildTurnLimitWrapUpMessage(conversation);
                messages.add(new ConversationMessage("assistant", wrapUp));

                // 랩업 메시지까지 저장해서 대화 종료 상태를 남김
                conversationRepository.saveConversation(
                    conversation.getStudentEmail(), conversationId,
                    conversation.getTopic(), conversation.getDifficulty(),
                    conversation.getSituation(), conversation.getRole(),
                    messages, conversation.getTimestamp()
                );

                return createResponse(200, ChatMessageResponse.ended(conversationId, wrapUp, messages.size(), "TURN_LIMIT"));
            }

            // 메시지 히스토리에 사용자 메시지 추가
            List<ConversationMessage> messages = new ArrayList<>(conversation.getMessages());
            messages.add(new ConversationMessage("user", userMessage));

            // 먼저 사용자 메시지만 저장
            conversationRepository.saveConversation(
                conversation.getStudentEmail(), conversationId,
                conversation.getTopic(), conversation.getDifficulty(),
                conversation.getSituation(), conversation.getRole(), messages,
                conversation.getTimestamp());

            // SQS 비동기 처리
            if (sqsService != null && jobStatusService != null) {
                // 최근 20턴만 유지 (토큰 절약)
                List<ConversationMessage> recentMessages = messages.size() > 20
                    ? messages.subList(messages.size() - 20, messages.size())
                    : messages;

                // 시스템 프롬프트 생성
                String systemPrompt = buildChatSystemPrompt(
                    conversation.getTopic(), conversation.getDifficulty(),
                    conversation.getSituation(), conversation.getRole(), "");

                // SQS에 메시지 전송
                String requestId = UUID.randomUUID().toString();
                AsyncChatMessage asyncMessage = new AsyncChatMessage(
                    requestId, conversationId, conversation.getStudentEmail(),
                    systemPrompt, recentMessages);

                sqsService.sendChatMessage(asyncMessage);
                jobStatusService.createJob(requestId, "PROCESSING");

                context.getLogger().log("Chat request sent to SQS: " + requestId);

                // 202 Accepted 응답
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("status", "PROCESSING");
                response.put("requestId", requestId);
                response.put("conversationId", conversationId);
                response.put("statusUrl", "/api/ai/chat/status/" + requestId);

                return createResponse(202, response);
            } else {
                // 동기 처리 (폴백)
                List<ConversationMessage> recentMessages = messages.size() > 20
                    ? messages.subList(messages.size() - 20, messages.size())
                    : messages;

                String systemPrompt = buildChatSystemPrompt(
                    conversation.getTopic(), conversation.getDifficulty(),
                    conversation.getSituation(), conversation.getRole(), "");

                String aiResponse = getClaudeApiService().callClaudeApiWithHistory(
                    systemPrompt, recentMessages);

                messages.add(new ConversationMessage("assistant", aiResponse));

                conversationRepository.saveConversation(
                    conversation.getStudentEmail(), conversationId,
                    conversation.getTopic(), conversation.getDifficulty(),
                    conversation.getSituation(), conversation.getRole(), messages,
                    conversation.getTimestamp());

                ChatMessageResponse response = ChatMessageResponse.success(
                    conversationId, aiResponse, messages.size());
                return createResponse(200, response);
            }

        } catch (IllegalArgumentException e) {
            return createResponse(400, ChatMessageResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Chat message error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, ChatMessageResponse.error("Failed to process message: " + e.getMessage()));
        }
    }

    private String buildTurnLimitWrapUpMessage(ConversationRepository.ConversationData conversation) {
        // 1~2문장 랩업 + 다음 액션 제안. 역할/상황을 깨지 않도록 너무 메타하게 말하지 않는다.
        String role = conversation == null ? "" : conversation.getRole();
        if (role == null) role = "";
        // 영어 대화 파트너 톤 유지(간단)
        return "That was great practice—let’s wrap up here for now. If you’re ready, start a new conversation and we’ll continue with a fresh scenario.";
    }

    private APIGatewayProxyResponseEvent handleChatStatus(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            if (jobStatusService == null) {
                return createResponse(503, Map.of("error", "Service not available"));
            }

            // URL에서 requestId 추출
            String path = input.getPath();
            String requestId = path.substring(path.lastIndexOf('/') + 1);

            context.getLogger().log("Status check for request: " + requestId);

            // DynamoDB에서 작업 상태 조회
            Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> jobStatus =
                jobStatusService.getJobStatus(requestId);

            if (jobStatus == null) {
                return createResponse(404, Map.of("error", "Request not found"));
            }

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", jobStatus.get("status").s());
            response.put("requestId", requestId);

            if ("COMPLETED".equals(jobStatus.get("status").s())) {
                response.put("conversationId", jobStatus.get("conversation_id").s());
                response.put("aiResponse", jobStatus.get("ai_response").s());
                response.put("turnCount", Integer.parseInt(jobStatus.get("turn_count").n()));
            } else if ("FAILED".equals(jobStatus.get("status").s())) {
                if (jobStatus.containsKey("error")) {
                    response.put("error", jobStatus.get("error").s());
                }
            }

            return createResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Status check error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("error", "Failed to check status: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleConversationList(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Cognito Authorizer claims에서 studentEmail 추출
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            // Query parameter로 limit 받기 (기본값: 20)
            int limit = 20;
            if (input.getQueryStringParameters() != null
                && input.getQueryStringParameters().containsKey("limit")) {
                try {
                    limit = Integer.parseInt(input.getQueryStringParameters().get("limit"));
                    if (limit <= 0 || limit > 100) {
                        limit = 20;
                    }
                } catch (NumberFormatException e) {
                    limit = 20;
                }
            }

            // 대화 이력 조회
            List<ConversationSummary> conversations =
                conversationRepository.getConversationsByStudent(studentEmail, limit);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", conversations);

            return createResponse(200, response);

        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Conversation list error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("success", false, "error", "Failed to get conversation list: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleConversationDetail(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // Cognito Authorizer claims에서 studentEmail 추출
            String requesterEmail = extractStudentEmailFromAuthorizerClaims(input);

            // URL에서 conversationId 추출
            String path = input.getPath();
            String conversationId = path.substring(path.lastIndexOf('/') + 1);

            context.getLogger().log("Conversation detail - conversationId: " + conversationId);

            // 대화 조회
            ConversationRepository.ConversationData conversation =
                conversationRepository.getConversation(conversationId);

            if (conversation == null) {
                return createResponse(404, Map.of("success", false, "error", "Conversation not found"));
            }

            // 다른 사용자 대화 접근 방지
            if (!requesterEmail.equals(conversation.getStudentEmail())) {
                return createResponse(403, Map.of("success", false, "error", "Forbidden"));
            }

            // 응답 생성
            Map<String, Object> conversationDto = new HashMap<>();
            conversationDto.put("conversationId", conversation.getConversationId());
            conversationDto.put("topic", conversation.getTopic());
            conversationDto.put("difficulty", conversation.getDifficulty());
            conversationDto.put("situation", conversation.getSituation());
            conversationDto.put("role", conversation.getRole());
            conversationDto.put("messages", conversation.getMessages());
            conversationDto.put("timestamp", conversation.getTimestamp());
            conversationDto.put("turnCount", conversation.getMessages().size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", conversationDto);

            return createResponse(200, response);

        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Conversation detail error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("success", false, "error", "Failed to get conversation detail: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleAiLevel(
        APIGatewayProxyRequestEvent input, Context context
    ) {
        try {
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            // 최근 10개 conversation(messages 포함) 조회
            List<ConversationRepository.ConversationData> conversations =
                conversationRepository.getRecentConversationsWithMessages(studentEmail, LEVEL_EVAL_CONVERSATION_LIMIT);

            if (conversations == null || conversations.isEmpty()) {
                return createResponse(400, Map.of("success", false, "error", "not_enough_data"));
            }

            String userPrompt = buildLevelEvalUserPrompt(conversations);
            if (userPrompt.isBlank()) {
                return createResponse(400, Map.of("success", false, "error", "not_enough_data"));
            }

            String systemPrompt = """
                너는 영어 학습자의 회화 레벨을 평가하는 채점자다.
                아래는 사용자의 최근 대화 발화(user role)들이다.
                
                규칙:
                - 반드시 다음 3개 중 하나만 출력: 상, 중, 하
                - 다른 글자/설명/공백/줄바꿈/마침표/따옴표/JSON 금지
                """;

            String claudeRaw = getClaudeApiService().callClaudeApi(systemPrompt, userPrompt);
            String level = parseLevelOnly(claudeRaw);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", Map.of("level", level));
            return createResponse(200, response);

        } catch (SecurityException e) {
            return createResponse(401, Map.of("success", false, "error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return createResponse(400, Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("AI level error: " + e.getMessage());
            return createResponse(500, Map.of("success", false, "error", "Failed to evaluate level: " + e.getMessage()));
        }
    }

    private String buildLevelEvalUserPrompt(List<ConversationRepository.ConversationData> conversations) {
        StringBuilder sb = new StringBuilder();
        sb.append("최근 대화 10개에서 사용자의 발화만 모았습니다. 아래 발화를 보고 상/중/하 중 하나로만 답하세요.\n\n");

        int appended = 0;
        for (int i = 0; i < conversations.size(); i++) {
            ConversationRepository.ConversationData conv = conversations.get(i);
            if (conv == null || conv.getMessages() == null || conv.getMessages().isEmpty()) {
                continue;
            }

            // conversation 단위 구분자
            sb.append("=== conversation ").append(i + 1).append(" (topic=").append(safe(conv.getTopic()))
                .append(", difficulty=").append(safe(conv.getDifficulty())).append(") ===\n");

            // 최근 user 메시지 최대 N개만
            int convUserCount = 0;
            List<ConversationMessage> msgs = conv.getMessages();
            for (int j = msgs.size() - 1; j >= 0; j--) {
                ConversationMessage m = msgs.get(j);
                if (m == null) continue;
                if (!"user".equalsIgnoreCase(m.getRole())) continue;
                String content = m.getContent() == null ? "" : m.getContent().trim();
                if (content.isEmpty()) continue;

                if (content.length() > LEVEL_EVAL_MAX_CHARS_PER_MSG) {
                    content = content.substring(0, LEVEL_EVAL_MAX_CHARS_PER_MSG);
                }

                sb.append("- ").append(content.replace('\n', ' ')).append("\n");
                appended++;
                convUserCount++;
                if (convUserCount >= LEVEL_EVAL_MAX_USER_MSG_PER_CONV) break;
            }
            sb.append("\n");
        }

        return appended == 0 ? "" : sb.toString();
    }

    private String parseLevelOnly(String claudeRaw) {
        if (claudeRaw == null) return "중";
        String trimmed = claudeRaw.trim();
        if (trimmed.isEmpty()) return "중";

        // 가장 먼저 등장하는 상/중/하 1글자만 채택
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '상' || c == '중' || c == '하') {
                return String.valueOf(c);
            }
        }
        return "중";
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private APIGatewayProxyResponseEvent handleSentenceAudioSession(
        APIGatewayProxyRequestEvent input, Context context
    ) {
        try {
            if (sentenceAudioService == null || s3Presigner == null || ttsBucket == null) {
                return createResponse(503, Map.of("success", false, "error", "Sentence audio service not available"));
            }

            String path = input.getPath();
            String sessionId = path.substring(path.lastIndexOf('/') + 1);

            List<Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue>> items =
                sentenceAudioService.queryBySessionId(sessionId);

            if (items == null || items.isEmpty()) {
                return createResponse(404, Map.of("success", false, "error", "Session not found"));
            }

            int totalCount = items.size();
            int completedCount = 0;
            int failedCount = 0;
            int pendingCount = 0;
            long totalDurationMs = 0;
            int durationCompleteCount = 0;

            List<Map<String, Object>> sentences = new ArrayList<>();

            for (Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item : items) {
                int index = Integer.parseInt(item.get("sentenceIndex").n());
                String status = item.containsKey("status") ? item.get("status").s() : "PENDING";

                String english = item.containsKey("english") ? item.get("english").s() : null;
                String korean = item.containsKey("korean") ? item.get("korean").s() : null;
                String voiceId = item.containsKey("voiceId") ? item.get("voiceId").s() : null;

                Long durationMs = null;
                if (item.containsKey("durationMs")) {
                    try {
                        durationMs = Long.parseLong(item.get("durationMs").n());
                    } catch (Exception ignore) {
                        durationMs = null;
                    }
                }

                String audioUrl = null;
                if ("COMPLETED".equals(status) && item.containsKey("s3Key")) {
                    String s3Key = item.get("s3Key").s();
                    if (s3Key != null && !s3Key.isEmpty()) {
                        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(ttsBucket)
                            .key(s3Key)
                            .build();
                        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                            .signatureDuration(Duration.ofSeconds(presignedUrlExpiration))
                            .getObjectRequest(getObjectRequest)
                            .build();
                        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
                        audioUrl = presigned.url().toString();
                    }
                }

                if ("COMPLETED".equals(status)) completedCount++;
                else if ("FAILED".equals(status)) failedCount++;
                else pendingCount++;

                if ("COMPLETED".equals(status) && durationMs != null) {
                    totalDurationMs += durationMs;
                    durationCompleteCount++;
                }

                Map<String, Object> dto = new HashMap<>();
                dto.put("index", index);
                dto.put("english", english);
                dto.put("korean", korean);
                dto.put("status", status);
                dto.put("audioUrl", audioUrl);
                dto.put("durationMs", durationMs);
                dto.put("voiceId", voiceId);
                if ("FAILED".equals(status)) {
                    if (item.containsKey("errorCode")) dto.put("errorCode", item.get("errorCode").s());
                    if (item.containsKey("errorMessage")) dto.put("errorMessage", item.get("errorMessage").s());
                }
                sentences.add(dto);
            }

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalCount", totalCount);
            summary.put("completedCount", completedCount);
            summary.put("failedCount", failedCount);
            summary.put("pendingCount", pendingCount);
            summary.put("totalDurationMs", totalDurationMs);
            summary.put("durationCompleteCount", durationCompleteCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", sessionId);
            response.put("sentences", sentences);
            response.put("summary", summary);

            return createResponse(200, response);
        } catch (Exception e) {
            context.getLogger().log("Sentence audio session error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("success", false, "error", "Failed to get sentence audio session: " + e.getMessage()));
        }
    }

    private void enqueueSentenceTtsSession(String sessionId, List<Sentence> sentences, String voiceId, Context context) {
        if (sentenceAudioService == null || ttsSqsService == null) {
            context.getLogger().log("Sentence audio services are not initialized; skipping TTS enqueue.");
            return;
        }
        if (sentences == null || sentences.isEmpty()) {
            return;
        }

        long ttl = Instant.now().plusSeconds(30L * 24 * 60 * 60).getEpochSecond();
        List<TTSJobMessagePayload> payloads = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            Sentence s = sentences.get(i);
            String english = s == null ? null : s.getText();
            String korean = s == null ? null : s.getTranslation();
            if (english == null) english = "";

            String jobId = UUID.randomUUID().toString();
            String textHash = TextHashUtil.generateHash(english, voiceId);
            String s3Key = TextHashUtil.generateS3Key(voiceId, textHash);

            // PENDING 선생성
            sentenceAudioService.putPending(sessionId, i, english, korean, voiceId, jobId, ttl);

            // SQS 메시지
            TTSJobMessagePayload p = new TTSJobMessagePayload();
            p.setJobId(jobId);
            p.setText(english);
            p.setVoiceId(voiceId);
            p.setS3Key(s3Key);
            p.setSessionId(sessionId);
            p.setSentenceIndex(i);
            p.setTrackDuration(true);
            payloads.add(p);
        }

        ttsSqsService.sendTtsJobsBatch(payloads);
    }

    private void enqueueRecommendSentenceTtsSession(String sessionId, List<RecommendedSentence> sentences, String voiceId, Context context) {
        if (sentenceAudioService == null || ttsSqsService == null) {
            context.getLogger().log("Sentence audio services are not initialized; skipping recommend TTS enqueue.");
            return;
        }
        if (sentences == null || sentences.isEmpty()) {
            return;
        }

        long ttl = Instant.now().plusSeconds(30L * 24 * 60 * 60).getEpochSecond();
        List<TTSJobMessagePayload> payloads = new ArrayList<>();

        for (int i = 0; i < sentences.size(); i++) {
            RecommendedSentence s = sentences.get(i);
            String english = s == null ? null : s.getText();
            if (english == null) english = "";

            String jobId = UUID.randomUUID().toString();
            String textHash = TextHashUtil.generateHash(english, voiceId);
            String s3Key = TextHashUtil.generateS3Key(voiceId, textHash);

            // PENDING 선생성 (추천 문장은 한국어 번역이 없을 수 있음)
            sentenceAudioService.putPending(sessionId, i, english, null, voiceId, jobId, ttl);

            TTSJobMessagePayload p = new TTSJobMessagePayload();
            p.setJobId(jobId);
            p.setText(english);
            p.setVoiceId(voiceId);
            p.setS3Key(s3Key);
            p.setSessionId(sessionId);
            p.setSentenceIndex(i);
            p.setTrackDuration(true);
            payloads.add(p);
        }

        ttsSqsService.sendTtsJobsBatch(payloads);
    }

    /**
     * Cognito Authorizer claims에서 이메일을 추출합니다.
     * 지원하는 claim 키: email, cognito:username, username, sub
     */
    @SuppressWarnings("unchecked")
    private String extractStudentEmailFromAuthorizerClaims(APIGatewayProxyRequestEvent input) {
        if (input == null || input.getRequestContext() == null) {
            throw new SecurityException("Unauthorized: missing requestContext");
        }
        Map<String, Object> authorizer = input.getRequestContext().getAuthorizer();
        if (authorizer == null) {
            throw new SecurityException("Unauthorized: missing authorizer");
        }
        Object claimsObj = authorizer.get("claims");
        if (!(claimsObj instanceof Map)) {
            throw new SecurityException("Unauthorized: missing authorizer claims");
        }
        Map<String, Object> claims = (Map<String, Object>) claimsObj;

        // 여러 가능한 email claim 키 시도
        String[] emailKeys = {"email", "cognito:username", "username", "sub"};
        String email = null;

        for (String key : emailKeys) {
            Object value = claims.get(key);
            if (value != null) {
                String candidate = String.valueOf(value).trim();
                // email 형식이거나 sub(UUID)가 아닌 경우 사용
                if (!candidate.isEmpty() && (candidate.contains("@") || key.equals("email"))) {
                    email = candidate;
                    break;
                }
                // cognito:username은 email일 수 있음
                if ("cognito:username".equals(key) && !candidate.isEmpty()) {
                    email = candidate;
                    break;
                }
            }
        }

        if (email == null || email.isEmpty()) {
            throw new SecurityException("Unauthorized: missing email claim. Available claims: " + claims.keySet());
        }

        // 이메일 유효성 정규표현식 검사
        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+[a-zA-Z]{2,}$")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email;
    }

    private String buildChatSystemPrompt(String topic, String difficulty,
                                         String situation, String role, String goal) {
        String difficultyGuidelines;
        if ("easy".equals(difficulty)) {
            difficultyGuidelines = "Use simple vocabulary, speak slowly, ask yes/no questions, be very patient";
        } else if ("hard".equals(difficulty)) {
            difficultyGuidelines = "Use complex vocabulary, speak naturally with contractions, include nuanced responses, occasionally create minor complications";
        } else {
            difficultyGuidelines = "Use natural speech, include some idioms, vary question types";
        }

        return String.format("""
            You are an English conversation partner for Korean language learners.

            ## Your Role
            %s

            ## Current Situation
            %s

            ## Conversation Goal for User
            %s

            ## Difficulty Level: %s
            %s

            ## Critical Rules
            1. Stay completely in character as %s
            2. Keep responses to 1-3 sentences (natural conversation length)
            3. React realistically to what the user says
            4. If user makes grammar mistakes, respond naturally (don't correct)
            5. Gradually progress the conversation toward resolution
            6. Add realistic complications based on difficulty level
            7. Never break character or mention you're an AI

            Respond as %s now.
            """, role, situation, goal, difficulty, difficultyGuidelines, role, role);
    }

    private String buildSystemPrompt(String topic, String difficulty) {
        String topicDescription = TopicScenariosProvider.getTopicDescription(topic);
        String topicScenarios = TopicScenariosProvider.getTopicScenarios(topic);

        return String.format("""
            You are an expert English sentence generator for Korean language learners.

            ## Task
            Generate exactly 10 practical English sentences for the topic: %s
            Difficulty level: %s

            ## Topic Context: %s

            ## Difficulty Guidelines
            - general: Aim for maximum diversity. Do NOT reuse common textbook templates.
            - easy: 5-10 words, present tense, basic vocabulary, simple sentence structure
            - medium: 10-15 words, various tenses, compound sentences, common idioms
            - hard: 15-25 words, complex grammar, subjunctive mood, nuanced expressions, formal/polite registers

            ## Critical Rules
            1. NO generic greetings (Hello, Hi, How are you, Thank you, Nice to meet you)
            2. NO clichéd phrases (Have a nice day, See you later)
            3. Each sentence must be practically useful in real situations
            4. Each sentence must include at least 2 concrete details (numbers, times, names, brands, locations, seat/gate, etc.)
            5. Make the 10 sentences maximally different from each other:
               - Different micro-scenario per sentence (place, goal, relationship, emotion, constraint)
               - Different intent per sentence (question, request, confirmation, complaint, negotiation, apology, refusal, clarification, suggestion, correction)
               - Different structure: do NOT repeat the same opening pattern
            6. Include questions, statements, and requests in mix
            7. Reflect realistic scenarios a Korean traveler/learner would encounter
            8. Avoid overusing these common templates across the set (at most once each):
               - \"I'd like...\"
               - \"Can I get...\"
               - \"Could you please...\"

            ## Topic-Specific Scenarios for %s
            %s

            ## Output Format (JSON only, no markdown)
            Return a JSON array exactly like this:
            [
              {
                "id": 1,
                "text": "English sentence here",
                "translation": "자연스러운 한국어 번역",
                "situation": "Brief context when this would be used"
              }
            ]

            IMPORTANT: Return ONLY the JSON array, no markdown code blocks, no explanations.
            """, topic, difficulty, topicDescription, topic, topicScenarios);
    }

    private String buildRecommendSystemPrompt(String topic, String difficulty, ConversationRepository.ConversationData conversation) {
        // conversationId 없으면 기존 방식 (일반 토픽 기반 추천)
        if (conversation == null) {
            String topicDescription = TopicScenariosProvider.getTopicDescription(topic);
            String topicScenarios = TopicScenariosProvider.getTopicScenarios(topic);

            return String.format("""
                You are an expert English sentence recommender for Korean learners.

                ## Task
                Recommend English sentences for the topic: %s
                Difficulty level: %s

                ## Topic Context: %s

                ## Difficulty Guidelines
                - easy: 5-10 words, present tense, basic vocabulary, simple sentence structure
                - medium: 10-15 words, various tenses, compound sentences, common idioms
                - hard: 15-25 words, complex grammar, nuanced expressions, formal/polite registers

                ## Diversity Rules (critical)
                1. Every sentence must be practical in real life.
                2. Across the set, avoid repeating the same opening words and template phrases.
                3. Across the set, vary intent and structure (question/request/confirmation/complaint/negotiation/apology/refusal/clarification).
                4. Avoid greetings and clichés.

                ## Topic-Specific Scenarios for %s
                %s

                ## Output Format (JSON only, no markdown)
                Return ONLY a JSON array with objects exactly like:
                [
                  { "id": 1, "text": "..." }
                ]

                IMPORTANT:
                - English text only. Do NOT include Korean translation.
                - Return ONLY the JSON array, no explanations.
                """, topic, difficulty, topicDescription, topic, topicScenarios);
        }

        // conversationId 있으면 대화 컨텍스트 기반 추천
        // 최근 3턴 메시지 추출 (6개 메시지 = user + assistant 3턴)
        List<ConversationMessage> recentMessages = conversation.getMessages();
        int startIndex = Math.max(0, recentMessages.size() - 6);
        List<ConversationMessage> last3Turns = recentMessages.subList(startIndex, recentMessages.size());

        // 메시지 히스토리 텍스트 생성
        StringBuilder historyBuilder = new StringBuilder();
        for (ConversationMessage msg : last3Turns) {
            historyBuilder.append(msg.getRole().equals("user") ? "User: " : "Assistant: ");
            historyBuilder.append(msg.getContent()).append("\n");
        }
        String conversationHistory = historyBuilder.toString().trim();

        // AI의 마지막 응답 추출
        String lastAssistantMessage = "";
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            if (recentMessages.get(i).getRole().equals("assistant")) {
                lastAssistantMessage = recentMessages.get(i).getContent();
                break;
            }
        }

        return String.format("""
            You are an expert English sentence recommender for Korean learners.

            ## Conversation Context
            - Topic: %s
            - Situation: %s
            - Your Role (as AI): %s

            ## Recent Conversation
            %s

            ## AI's Last Response (MOST IMPORTANT)
            "%s"

            ## Task
            Recommend sentences that DIRECTLY RESPOND to the AI's last message above.

            Consider what the AI said:
            - If AI asked a question → recommend answers to that question
            - If AI provided information → recommend follow-up questions or acknowledgments
            - If AI asked for confirmation → recommend confirmations or clarifications
            - If AI offered help → recommend accepting/declining or specifying needs

            ## Requirements
            1. Each sentence must be a DIRECT and APPROPRIATE response to the AI's last message
            2. Match the difficulty level (%s)
            3. Provide variety in response types (but all must fit the context)

            ## Difficulty Guidelines
            - easy: 5-10 words, present tense, basic vocabulary
            - medium: 10-15 words, various tenses, compound sentences
            - hard: 15-25 words, complex grammar, nuanced expressions

            ## Output Format (JSON only)
            [
              { "id": 1, "text": "..." },
              { "id": 2, "text": "..." },
              { "id": 3, "text": "..." }
            ]

            CRITICAL: Every sentence must make sense as a direct reply to: "%s"
            """,
            conversation.getTopic(),
            conversation.getSituation(),
            conversation.getRole(),
            conversationHistory,
            lastAssistantMessage,
            difficulty,
            lastAssistantMessage
        );
    }

    private String buildRecommendUserPrompt(int count, String seed, boolean hasConversation) {
        if (hasConversation) {
            // 대화 컨텍스트가 있는 경우
            return """
                Generate exactly %d items now.
                Seed: %s
                - Keep difficulty strictly.
                - All sentences must be relevant to the conversation above.
                - Provide natural follow-ups that move the dialogue forward.
                - Vary the type: questions, statements, requests.
                - Output JSON array only.
                """.formatted(count, seed);
        } else {
            // 기존 프롬프트 유지 (일반 토픽 기반)
            return """
                Generate exactly %d items now.
                Seed: %s
                - Keep difficulty strictly.
                - Maximize diversity across the %d items.
                - Each sentence must include at least 2 concrete details (numbers, times, names, brands, places, seat/gate, address-like detail, etc.).
                - Output JSON array only.
                """.formatted(count, seed, count);
        }
    }

    private String buildFeedbackSystemPrompt(String difficulty) {
        return """
            You are an English tutor for Korean learners.

            ## Task
            Given an original English sentence and the user's spoken text (STT), provide concise feedback.
            Difficulty level: %s

            ## Rules
            1. Be encouraging and constructive.
            2. If the user's text is close enough, acknowledge meaning first.
            3. Provide a corrected version of the user's sentence (natural English).
            4. Provide 2-4 short feedback bullet points (Korean).
            5. Provide 1-2 alternative natural expressions (English).

            ## Output Format (JSON only, no markdown)
            Return ONLY a JSON object exactly like:
            {
              "correctedUserText": "....",
              "feedback": ["...","..."],
              "suggestions": ["...","..."],
              "encouragement": "..."
            }

            IMPORTANT: Return ONLY the JSON object, no explanations.
            """.formatted(difficulty);
    }

    private String buildFeedbackUserPrompt(String originalText, String userText) {
        return """
            Original: %s
            User: %s
            """.formatted(originalText, userText);
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type,Authorization");

        try {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withHeaders(headers)
                .withBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
