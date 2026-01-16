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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationRepository conversationRepository;
    private final ClaudeApiKeyProvider claudeApiKeyProvider;
    private final String claudeApiKeySecretId;

    // Lazy-init (cold start 최적화): Secrets Manager 호출은 첫 요청 시점에만 수행
    private volatile ClaudeApiService claudeApiService;
    private final Object claudeInitLock = new Object();

    public App() {
        this.claudeApiKeyProvider = new ClaudeApiKeyProvider();
        this.claudeApiKeySecretId = System.getenv("CLAUDE_API_KEY_SECRET_ID");

        String conversationsTable = System.getenv("AI_CONVERSATIONS_TABLE");
        this.conversationRepository = new ConversationRepository(conversationsTable);
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

            // POST /api/ai/chat/start - AI 대화 시작
            if ("POST".equals(httpMethod) && path.endsWith("/chat/start")) {
                return handleChatStart(input, context);
            }

            // POST /api/ai/chat/message - AI 대화 메시지
            if ("POST".equals(httpMethod) && path.endsWith("/chat/message")) {
                return handleChatMessage(input, context);
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
            String userPrompt = "Generate 10 unique, practical sentences now.";

            // Claude API 호출
            String claudeResponse = getClaudeApiService().callClaudeApi(systemPrompt, userPrompt);
            context.getLogger().log("Claude API response received");

            // JSON 파싱 (Claude가 반환한 문장 배열)
            List<Sentence> sentences = objectMapper.readValue(
                claudeResponse, new TypeReference<List<Sentence>>() {});

            // 응답 생성
            SentenceGenerateResponse response = SentenceGenerateResponse.success(sentences, topic, difficulty);
            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, SentenceGenerateResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Sentence generation error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, SentenceGenerateResponse.error("Failed to generate sentences: " + e.getMessage()));
        }
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

            // 메시지 히스토리에 사용자 메시지 추가
            List<ConversationMessage> messages = new ArrayList<>(conversation.getMessages());
            messages.add(new ConversationMessage("user", userMessage));

            // 최근 10턴만 유지 (토큰 절약)
            List<ConversationMessage> recentMessages = messages.size() > 20
                ? messages.subList(messages.size() - 20, messages.size())
                : messages;

            // 시스템 프롬프트 생성
            String systemPrompt = buildChatSystemPrompt(
                conversation.getTopic(), conversation.getDifficulty(),
                conversation.getSituation(), conversation.getRole(), "");

            // Claude API 호출 (메시지 히스토리 포함)
            String aiResponse = getClaudeApiService().callClaudeApiWithHistory(
                systemPrompt, recentMessages);

            context.getLogger().log("AI response generated");

            // AI 응답 추가
            messages.add(new ConversationMessage("assistant", aiResponse));

            // 대화 업데이트 (기존 timestamp 사용)
            conversationRepository.saveConversation(
                conversation.getStudentEmail(), conversationId,
                conversation.getTopic(), conversation.getDifficulty(),
                conversation.getSituation(), conversation.getRole(), messages,
                conversation.getTimestamp());

            // 응답 생성
            ChatMessageResponse response = ChatMessageResponse.success(
                conversationId, aiResponse, messages.size());
            return createResponse(200, response);

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
            - general : Random seed is current time.
            - easy: 5-10 words, present tense, basic vocabulary, simple sentence structure
            - medium: 10-15 words, various tenses, compound sentences, common idioms
            - hard: 15-25 words, complex grammar, subjunctive mood, nuanced expressions, formal/polite registers

            ## Critical Rules
            1. NO generic greetings (Hello, Hi, How are you, Thank you, Nice to meet you)
            2. NO clichéd phrases (Have a nice day, See you later)
            3. Each sentence must be practically useful in real situations
            4. Include specific details (numbers, names, concrete nouns)
            5. Vary sentence structures - don't repeat patterns
            6. Include questions, statements, and requests in mix
            7. Reflect realistic scenarios a Korean traveler/learner would encounter

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
