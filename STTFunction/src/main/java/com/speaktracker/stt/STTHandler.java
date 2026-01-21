package com.speaktracker.stt;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speaktracker.stt.model.*;
import com.speaktracker.stt.service.PronunciationEvaluationService;
import com.speaktracker.stt.service.PronunciationResultRepository;
import com.speaktracker.stt.service.STSCredentialsService;
import com.speaktracker.stt.service.TranscribeService;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class STTHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final STSCredentialsService stsCredentialsService;
    private final PronunciationEvaluationService evaluationService;
    private final PronunciationResultRepository repository;
    private final TranscribeService transcribeService;

    public STTHandler() {
        // AWS 클라이언트 초기화
        StsClient stsClient = StsClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();

        TranscribeClient transcribeClient = TranscribeClient.builder()
                .region(Region.AP_NORTHEAST_2)
                .build();

        // 환경 변수
        String transcribeClientRoleArn = System.getenv("TRANSCRIBE_CLIENT_ROLE_ARN");
        String pronunciationResultsTable = System.getenv("PRONUNCIATION_RESULTS_TABLE");
        String s3BucketName = System.getenv("TRANSCRIBE_BUCKET_NAME");
        int credentialExpiration = Integer.parseInt(
                System.getenv().getOrDefault("CREDENTIAL_EXPIRATION_SECONDS", "900")
        );

        // 서비스 초기화
        this.stsCredentialsService = new STSCredentialsService(
                stsClient, transcribeClientRoleArn, credentialExpiration);
        this.evaluationService = new PronunciationEvaluationService();
        this.repository = new PronunciationResultRepository(dynamoDbClient, pronunciationResultsTable);
        this.transcribeService = new TranscribeService(transcribeClient, s3Client, s3BucketName);
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent input, Context context) {

        String httpMethod = input.getHttpMethod();
        String path = input.getPath();

        context.getLogger().log("STT Request - Method: " + httpMethod + ", Path: " + path);

        try {
            // POST /api/stt/credentials - 임시 자격 증명 발급
            if ("POST".equals(httpMethod) && path.endsWith("/credentials")) {
                return handleCredentialsRequest(input, context);
            }

            // POST /api/stt/transcribe - 오디오 파일 변환
            if ("POST".equals(httpMethod) && path.endsWith("/transcribe")) {
                return handleTranscribeRequest(input, context);
            }

            // POST /api/stt/evaluate - 발음 평가
            if ("POST".equals(httpMethod) && path.endsWith("/evaluate")) {
                return handleEvaluationRequest(input, context);
            }

            // GET /api/stt/history - 평가 이력 조회 (향후 구현)
            if ("GET".equals(httpMethod) && path.endsWith("/history")) {
                return createResponse(200, Map.of("message", "History endpoint - coming soon"));
            }

            return createResponse(404, Map.of("error", "Not Found"));

        } catch (Exception e) {
            context.getLogger().log("STT Error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleTranscribeRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            context.getLogger().log("Transcribe request received");

            // Body에서 오디오 데이터 추출 (Base64 인코딩된 데이터)
            String body = input.getBody();
            if (body == null || body.isEmpty()) {
                return createResponse(400, TranscribeResponse.error("Missing request body"));
            }

            // isBase64Encoded 확인
            boolean isBase64Encoded = input.getIsBase64Encoded() != null && input.getIsBase64Encoded();

            // Base64 디코딩
            byte[] audioData;
            if (isBase64Encoded) {
                audioData = Base64.getDecoder().decode(body);
            } else {
                // Body가 JSON 형태일 수 있음 (languageCode 포함)
                try {
                    // JSON 파싱 시도
                    Map<String, Object> requestBody = objectMapper.readValue(body, Map.class);
                    String audioBase64 = (String) requestBody.get("audio");
                    String languageCode = (String) requestBody.getOrDefault("languageCode", "en-US");

                    if (audioBase64 == null || audioBase64.isEmpty()) {
                        return createResponse(400, TranscribeResponse.error("Missing audio data"));
                    }

                    audioData = Base64.getDecoder().decode(audioBase64);

                    // Transcribe 호출
                    context.getLogger().log("Starting transcription - Language: " + languageCode);
                    String transcript = transcribeService.transcribeAudio(audioData, languageCode);

                    context.getLogger().log("Transcription completed: " + transcript);
                    return createResponse(200, TranscribeResponse.success(transcript));

                } catch (Exception e) {
                    // JSON이 아니면 그냥 Base64 디코딩 시도
                    audioData = Base64.getDecoder().decode(body);
                }
            }

            // 기본 언어 코드로 Transcribe
            String languageCode = "en-US";
            context.getLogger().log("Starting transcription - Language: " + languageCode);
            String transcript = transcribeService.transcribeAudio(audioData, languageCode);

            context.getLogger().log("Transcription completed: " + transcript);
            return createResponse(200, TranscribeResponse.success(transcript));

        } catch (IllegalArgumentException e) {
            context.getLogger().log("Invalid request: " + e.getMessage());
            return createResponse(400, TranscribeResponse.error("Invalid request: " + e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Transcription error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, TranscribeResponse.error(
                    "Transcription failed: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleCredentialsRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 요청 파싱
            STTCredentialsRequest request = new STTCredentialsRequest();
            if (input.getBody() != null && !input.getBody().isEmpty()) {
                request = objectMapper.readValue(input.getBody(), STTCredentialsRequest.class);
            }

            String languageCode = request.getLanguageCodeOrDefault();
            int sampleRate = request.getSampleRateOrDefault();

            context.getLogger().log(String.format(
                    "Generating credentials - Language: %s, SampleRate: %d",
                    languageCode, sampleRate));

            // STS로 임시 자격 증명 발급
            STTCredentialsResponse response = stsCredentialsService.getTemporaryCredentials(
                    languageCode, sampleRate);

            if (!response.isSuccess()) {
                return createResponse(500, response);
            }

            return createResponse(200, response);

        } catch (Exception e) {
            context.getLogger().log("Credentials generation error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, STTCredentialsResponse.error(
                    "Failed to generate credentials: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handleEvaluationRequest(
            APIGatewayProxyRequestEvent input, Context context) {
        try {
            // 학생 이메일 추출 (Cognito Authorizer)
            String studentEmail = extractStudentEmailFromAuthorizerClaims(input);

            // 요청 파싱 및 유효성 검증
            PronunciationEvalRequest request = objectMapper.readValue(
                    input.getBody(), PronunciationEvalRequest.class);
            request.validate();

            context.getLogger().log(String.format(
                    "Evaluating pronunciation for student: %s, Sentence: %s",
                    studentEmail, request.getSentenceId()));

            // 발음 평가
            PronunciationResult result = evaluationService.evaluate(
                    request.getOriginalText(),
                    request.getTranscribedText()
            );

            // DynamoDB에 저장
            boolean saved = false;
            try {
                repository.save(
                        studentEmail,
                        request.getOriginalText(),
                        request.getTranscribedText(),
                        request.getSentenceId(),
                        request.getSessionId(),
                        request.getAudioDurationMs(),
                        result
                );
                saved = true;
                context.getLogger().log("Evaluation result saved to DynamoDB");
            } catch (Exception e) {
                context.getLogger().log("Failed to save to DynamoDB: " + e.getMessage());
                // 저장 실패해도 평가 결과는 반환
            }

            PronunciationEvalResponse response = PronunciationEvalResponse.success(result, saved);
            return createResponse(200, response);

        } catch (IllegalArgumentException e) {
            return createResponse(400, PronunciationEvalResponse.error(e.getMessage()));
        } catch (SecurityException e) {
            return createResponse(401, PronunciationEvalResponse.error(e.getMessage()));
        } catch (Exception e) {
            context.getLogger().log("Evaluation error: " + e.getMessage());
            e.printStackTrace();
            return createResponse(500, PronunciationEvalResponse.error(
                    "Failed to evaluate pronunciation: " + e.getMessage()));
        }
    }

    /**
     * Cognito Authorizer claims에서 이메일 추출
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
        String[] emailKeys = {"email", "cognito:username", "username"};
        String email = null;

        for (String key : emailKeys) {
            Object value = claims.get(key);
            if (value != null) {
                String candidate = String.valueOf(value).trim();
                if (!candidate.isEmpty() && (candidate.contains("@") || key.equals("email"))) {
                    email = candidate;
                    break;
                }
                if ("cognito:username".equals(key) && !candidate.isEmpty()) {
                    email = candidate;
                    break;
                }
            }
        }

        if (email == null || email.isEmpty()) {
            throw new SecurityException("Unauthorized: missing email claim");
        }

        return email;
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
