package com.speaktracker.tts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.tts.model.TTSRequest;
import com.speaktracker.tts.model.TTSResponse;
import com.speaktracker.tts.service.PollyService;
import com.speaktracker.tts.service.S3Service;
import com.speaktracker.tts.util.TextHashUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.HashMap;
import java.util.Map;

public class TTSHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PollyService pollyService;
    private final S3Service s3Service;
    private final int presignedUrlExpiration;

    public TTSHandler() {
        // AWS 클라이언트 초기화
        PollyClient pollyClient = PollyClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        S3Client s3Client = S3Client.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        S3Presigner s3Presigner = S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        // 환경 변수
        String ttsBucket = System.getenv("TTS_BUCKET");
        this.presignedUrlExpiration = Integer.parseInt(
            System.getenv().getOrDefault("PRESIGNED_URL_EXPIRATION", "3600")
        );

        // 서비스 초기화
        this.pollyService = new PollyService(pollyClient);
        this.s3Service = new S3Service(s3Client, s3Presigner, ttsBucket);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        String httpMethod = input.getHttpMethod();
        String path = input.getPath();

        context.getLogger().log("TTS Request - Method: " + httpMethod + ", Path: " + path);

        try {
            // POST /api/tts - TTS 변환
            if ("POST".equals(httpMethod) && path.endsWith("/tts")) {
                return handleTTSRequest(input, context);
            }

            return createResponse(404, Map.of("error", "Not Found"));

        } catch (Exception e) {
            context.getLogger().log("TTS Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TTSResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleTTSRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱 및 유효성 검증
            TTSRequest ttsRequest = objectMapper.readValue(input.getBody(), TTSRequest.class);
            ttsRequest.validate();

            String text = ttsRequest.getText();
            String voiceId = ttsRequest.getVoiceIdOrDefault();

            context.getLogger().log(String.format(
                "TTS Request - Text length: %d, Voice: %s",
                text.length(), voiceId));

            // 텍스트 해시 및 S3 키 생성
            String textHash = TextHashUtil.generateHash(text, voiceId);
            String s3Key = TextHashUtil.generateS3Key(voiceId, textHash);

            context.getLogger().log("Generated S3 Key: " + s3Key);

            // 캐시 확인
            boolean cached = s3Service.exists(s3Key);

            if (!cached) {
                // Polly TTS 변환
                context.getLogger().log("Cache miss - calling Polly TTS");
                byte[] audioBytes = pollyService.synthesizeSpeech(text, voiceId);

                // S3 업로드
                s3Service.uploadAudio(s3Key, audioBytes);
                context.getLogger().log("Audio uploaded to S3: " + s3Key);
            } else {
                context.getLogger().log("Cache hit - returning existing audio");
            }

            // Presigned URL 생성
            String presignedUrl = s3Service.generatePresignedUrl(s3Key, presignedUrlExpiration);

            // 응답 반환
            TTSResponse response = TTSResponse.success(
                presignedUrl, presignedUrlExpiration, cached);

            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, TTSResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("TTS processing error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TTSResponse.error("Failed to process TTS: " + e.getMessage()));
        }
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
