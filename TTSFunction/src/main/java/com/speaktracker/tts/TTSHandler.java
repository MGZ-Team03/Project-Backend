package com.speaktracker.tts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Polly를 사용한 TTS Lambda Handler
 */
public class TTSHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    
    private final PollyClient pollyClient;
    private final S3Client s3Client;
    private final String bucketName;
    private final String audioPrefix;
    private final Gson gson;

    public TTSHandler() {
        Region region = Region.AP_NORTHEAST_2;
        this.pollyClient = PollyClient.builder().region(region).build();
        this.s3Client = S3Client.builder().region(region).build();
        this.bucketName = System.getenv("AUDIO_BUCKET");
        this.audioPrefix = System.getenv("AUDIO_PREFIX") != null ? System.getenv("AUDIO_PREFIX") : "tts-audio/";
        this.gson = new Gson();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // CORS preflight
        if ("OPTIONS".equals(request.getHttpMethod())) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody("");
        }

        try {
            // 요청 파싱
            Map<String, Object> body = gson.fromJson(request.getBody(), Map.class);
            String text = (String) body.get("text");
            String voiceId = body.get("voiceId") != null ? (String) body.get("voiceId") : "Joanna";

            if (text == null || text.trim().isEmpty()) {
                return createErrorResponse(headers, 400, "text is required");
            }

            // 텍스트 길이 제한 (Polly 제한: 3000자)
            if (text.length() > 3000) {
                return createErrorResponse(headers, 400, "text exceeds maximum length of 3000 characters");
            }

            context.getLogger().log("Generating TTS for text: " + text.substring(0, Math.min(50, text.length())) + "...");

            // Polly로 음성 생성
            SynthesizeSpeechRequest synthesizeRequest = SynthesizeSpeechRequest.builder()
                    .text(text)
                    .voiceId(VoiceId.fromValue(voiceId))
                    .outputFormat(OutputFormat.MP3)
                    .engine(Engine.NEURAL) // Neural TTS 사용 (고품질)
                    .build();

            ResponseInputStream<SynthesizeSpeechResponse> synthesizeResponse = 
                    pollyClient.synthesizeSpeech(synthesizeRequest);

            // AudioStream을 byte array로 변환
            byte[] audioBytes = readAllBytes(synthesizeResponse);
            context.getLogger().log("Audio generated, size: " + audioBytes.length + " bytes");

            // S3에 업로드
            String fileName = generateFileName(text);
            String s3Key = audioPrefix + fileName;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("audio/mpeg")
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(audioBytes));

            // S3 URL 생성
            String audioUrl = String.format("https://%s.s3.%s.amazonaws.com/%s", 
                    bucketName, Region.AP_NORTHEAST_2.id(), s3Key);

            context.getLogger().log("Audio uploaded to S3: " + audioUrl);

            // 응답 생성
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("audioUrl", audioUrl);
            responseBody.put("voiceId", voiceId);
            responseBody.put("cached", false);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(gson.toJson(responseBody));

        } catch (PollyException e) {
            context.getLogger().log("Polly error: " + e.getMessage());
            return createErrorResponse(headers, 500, "TTS generation failed: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createErrorResponse(headers, 500, "Internal server error: " + e.getMessage());
        }
    }

    private byte[] readAllBytes(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    private String generateFileName(String text) {
        try {
            // 텍스트 해시 생성 (동일 텍스트 캐싱용)
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(text.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return System.currentTimeMillis() + "-" + hexString.toString() + ".mp3";
        } catch (Exception e) {
            return System.currentTimeMillis() + "-" + Math.abs(text.hashCode()) + ".mp3";
        }
    }

    private APIGatewayProxyResponseEvent createErrorResponse(Map<String, String> headers, int statusCode, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withBody(gson.toJson(error));
    }
}
