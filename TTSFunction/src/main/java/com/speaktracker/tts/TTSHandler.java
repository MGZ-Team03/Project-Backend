
package com.speaktracker.tts;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.tts.model.TTSRequest;
import com.speaktracker.tts.model.TTSResponse;
import com.speaktracker.tts.model.TTSJobMessage;
import com.speaktracker.tts.model.JobStatus;
import com.speaktracker.tts.service.PollyService;
import com.speaktracker.tts.service.S3Service;
import com.speaktracker.tts.service.SQSService;
import com.speaktracker.tts.service.JobStatusService;
import com.speaktracker.tts.util.TextHashUtil;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TTSHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PollyService pollyService;
    private final S3Service s3Service;
    private final SQSService sqsService;
    private final JobStatusService jobStatusService;
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

        SqsClient sqsClient = SqsClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
            .region(Region.AP_NORTHEAST_2)
            .build();

        // 환경 변수
        String ttsBucket = System.getenv("TTS_BUCKET");
        String ttsQueueUrl = System.getenv("TTS_QUEUE_URL");
        String jobStatusTable = System.getenv("JOB_STATUS_TABLE");
        this.presignedUrlExpiration = Integer.parseInt(
            System.getenv().getOrDefault("PRESIGNED_URL_EXPIRATION", "3600")
        );

        // 서비스 초기화
        this.pollyService = new PollyService(pollyClient);
        this.s3Service = new S3Service(s3Client, s3Presigner, ttsBucket);
        this.sqsService = new SQSService(sqsClient, ttsQueueUrl);
        this.jobStatusService = new JobStatusService(dynamoDbClient, jobStatusTable);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        String httpMethod = input.getHttpMethod();
        String path = input.getPath();

        context.getLogger().log("TTS Request - Method: " + httpMethod + ", Path: " + path);

        try {
            // POST /api/tts/korean - 한국어 TTS 변환
            if ("POST".equals(httpMethod) && path.equals("/api/tts/korean")) {
                return handleKoreanTTSRequest(input, context);
            }

            // GET /api/tts/korean/status/{jobId} - 한국어 TTS 작업 상태 조회
            if ("GET".equals(httpMethod) && path.startsWith("/api/tts/korean/status/")) {
                return handleKoreanStatusRequest(input, context);
            }

            // POST /api/tts - 영어 TTS 변환
            if ("POST".equals(httpMethod) && path.endsWith("/tts")) {
                return handleTTSRequest(input, context);
            }

            // GET /api/tts/status/{jobId} - 영어 TTS 작업 상태 조회
            if ("GET".equals(httpMethod) && path.contains("/status/")) {
                return handleStatusRequest(input, context);
            }

            return createResponse(404, Map.of("error", "Not Found"));

        } catch (Exception e) {
            context.getLogger().log("TTS Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TTSResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleKoreanTTSRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱 및 유효성 검증
            TTSRequest ttsRequest = objectMapper.readValue(input.getBody(), TTSRequest.class);
            ttsRequest.validate();

            String text = ttsRequest.getText();
            String voiceId = "Seoyeon";  // 한국어 음성 고정

            context.getLogger().log(String.format(
                "Korean TTS Request - Text length: %d, Voice: %s",
                text.length(), voiceId));

            // 텍스트 해시 및 S3 키 생성
            String textHash = TextHashUtil.generateHash(text, voiceId);
            String s3Key = TextHashUtil.generateS3Key(voiceId, textHash);

            context.getLogger().log("Generated S3 Key: " + s3Key);

            // 캐시 확인
            boolean cached = s3Service.exists(s3Key);

            if (!cached) {
                // 캐시 미스 - SQS에 작업 전송 (비동기)
                context.getLogger().log("Cache miss - sending to SQS queue");

                String jobId = UUID.randomUUID().toString();
                TTSJobMessage jobMessage = new TTSJobMessage(jobId, text, voiceId, s3Key);

                // SQS에 메시지 전송
                sqsService.sendTTSJob(jobMessage);

                // DynamoDB에 작업 상태 저장
                jobStatusService.createJob(jobId, "PROCESSING");

                context.getLogger().log("Job created: " + jobId);

                // 202 Accepted 응답
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("status", "PROCESSING");
                response.put("jobId", jobId);
                response.put("statusUrl", "/api/tts/korean/status/" + jobId);

                return createResponse(202, response);
            } else {
                // 캐시 히트 - 즉시 응답
                context.getLogger().log("Cache hit - returning existing audio");

                String presignedUrl = s3Service.generatePresignedUrl(s3Key, presignedUrlExpiration);
                TTSResponse response = TTSResponse.success(
                    presignedUrl, presignedUrlExpiration, cached);

                return createResponse(200, response);
            }

        } catch (IllegalArgumentException e) {
            return createResponse(400, TTSResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Korean TTS processing error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TTSResponse.error("Failed to process Korean TTS: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleKoreanStatusRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // URL에서 jobId 추출
            String path = input.getPath();
            String jobId = path.substring(path.lastIndexOf('/') + 1);

            context.getLogger().log("Korean TTS status check for job: " + jobId);

            // DynamoDB에서 작업 상태 조회
            JobStatus jobStatus = jobStatusService.getJobStatus(jobId);

            if (jobStatus == null) {
                return createResponse(404, Map.of("error", "Job not found"));
            }

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", jobStatus.getStatus());
            response.put("jobId", jobStatus.getJobId());

            if ("COMPLETED".equals(jobStatus.getStatus())) {
                response.put("audioUrl", jobStatus.getAudioUrl());
                response.put("expiresIn", presignedUrlExpiration);
            } else if ("FAILED".equals(jobStatus.getStatus())) {
                response.put("error", jobStatus.getError());
            }

            return createResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Korean TTS status check error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("error", "Failed to check status: " + e.getMessage()));
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
                // 캐시 미스 - SQS에 작업 전송 (비동기)
                context.getLogger().log("Cache miss - sending to SQS queue");

                String jobId = UUID.randomUUID().toString();
                TTSJobMessage jobMessage = new TTSJobMessage(jobId, text, voiceId, s3Key);

                // SQS에 메시지 전송
                sqsService.sendTTSJob(jobMessage);

                // DynamoDB에 작업 상태 저장
                jobStatusService.createJob(jobId, "PROCESSING");

                context.getLogger().log("Job created: " + jobId);

                // 202 Accepted 응답
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("status", "PROCESSING");
                response.put("jobId", jobId);
                response.put("statusUrl", "/api/tts/status/" + jobId);

                return createResponse(202, response);
            } else {
                // 캐시 히트 - 즉시 응답
                context.getLogger().log("Cache hit - returning existing audio");

                String presignedUrl = s3Service.generatePresignedUrl(s3Key, presignedUrlExpiration);
                TTSResponse response = TTSResponse.success(
                    presignedUrl, presignedUrlExpiration, cached);

                return createResponse(200, response);
            }

        } catch (IllegalArgumentException e) {
            return createResponse(400, TTSResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("TTS processing error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TTSResponse.error("Failed to process TTS: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleStatusRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // URL에서 jobId 추출
            String path = input.getPath();
            String jobId = path.substring(path.lastIndexOf('/') + 1);

            context.getLogger().log("Status check for job: " + jobId);

            // DynamoDB에서 작업 상태 조회
            JobStatus jobStatus = jobStatusService.getJobStatus(jobId);

            if (jobStatus == null) {
                return createResponse(404, Map.of("error", "Job not found"));
            }

            // 응답 생성
            Map<String, Object> response = new HashMap<>();
            response.put("status", jobStatus.getStatus());
            response.put("jobId", jobStatus.getJobId());

            if ("COMPLETED".equals(jobStatus.getStatus())) {
                response.put("audioUrl", jobStatus.getAudioUrl());
                response.put("expiresIn", presignedUrlExpiration);
            } else if ("FAILED".equals(jobStatus.getStatus())) {
                response.put("error", jobStatus.getError());
            }

            return createResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Status check error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("error", "Failed to check status: " + e.getMessage()));
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
