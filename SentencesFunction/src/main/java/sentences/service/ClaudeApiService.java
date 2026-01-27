package sentences.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import sentences.model.ConversationMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaudeApiService {

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_CLAUDE_MODEL = "claude-sonnet-4-5-20250929";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeApiService(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Claude API key is missing/empty");
        }
        this.client = new OkHttpClient.Builder()
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey.trim();
        String envModel = System.getenv("CLAUDE_MODEL");
        this.model = (envModel == null || envModel.trim().isEmpty())
            ? DEFAULT_CLAUDE_MODEL
            : envModel.trim();
    }

    /**
     * Claude API 호출하여 JSON 응답 받기
     */
    public String callClaudeApi(String systemPrompt, String userPrompt) throws IOException {
        // 요청 본문 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 2000);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", List.of(
            Map.of("role", "user", "content", userPrompt)
        ));

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // HTTP 요청 생성
        Request request = new Request.Builder()
            .url(CLAUDE_API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(RequestBody.create(jsonBody, JSON))
            .build();

        // API 호출
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() == null ? "" : response.body().string();
                throw new IOException("Claude API call failed: " + response.code()
                    + " - " + response.message()
                    + (errBody.isEmpty() ? "" : " | body=" + truncate(errBody, 500)));
            }

            String responseBody = response.body().string();

            // 응답에서 content[0].text 추출
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                return contentArray.get(0).get("text").asText();
            }

            throw new IOException("Invalid response format from Claude API");
        }
    }

    /**
     * Claude API 호출 (대화 히스토리 포함)
     */
    public String callClaudeApiWithHistory(String systemPrompt, List<ConversationMessage> messages) throws IOException {
        // ConversationMessage를 Claude API 형식으로 변환
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (ConversationMessage msg : messages) {
            Map<String, String> apiMsg = new HashMap<>();
            apiMsg.put("role", msg.getRole());
            apiMsg.put("content", msg.getContent());
            apiMessages.add(apiMsg);
        }

        // 요청 본문 구성
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 300);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", apiMessages);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        // HTTP 요청 생성
        Request request = new Request.Builder()
            .url(CLAUDE_API_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(RequestBody.create(jsonBody, JSON))
            .build();

        // API 호출
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() == null ? "" : response.body().string();
                throw new IOException("Claude API call failed: " + response.code()
                    + " - " + response.message()
                    + (errBody.isEmpty() ? "" : " | body=" + truncate(errBody, 500)));
            }

            String responseBody = response.body().string();

            // 응답에서 content[0].text 추출
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentArray = root.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                return contentArray.get(0).get("text").asText();
            }

            throw new IOException("Invalid response format from Claude API");
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (maxLen <= 0) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...(truncated)";
    }
}
